package com.aether.convert

import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.location.Location
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.os.SystemClock
import androidx.core.app.ServiceCompat
import com.aether.convert.net.Sync
import com.aether.convert.recv.Watchdog
import kotlinx.coroutines.*

class TrackerService : Service() {
    companion object {
        const val ACTION_START = "start"
        const val ACTION_BURST = "burst"
        const val ACTION_STOP = "stop"
        const val ID = 0x41
    }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private lateinit var prefs: Prefs
    private var engine: LocationEngine? = null
    private var wake: PowerManager.WakeLock? = null
    private var noteAt = 0L

    override fun onCreate() {
        super.onCreate()
        prefs = Prefs(this)
        Notify.channel(this)
        fg("running"); hold()
        engine = LocationEngine(this, prefs) { l, s -> onFix(l, s) }
        engine?.start(prefs.moving)
        scope.launch { ticker() }
        Watchdog.schedule(this, Config.WATCHDOG_MS)
        Sync.periodic(this)
    }

    override fun onStartCommand(i: Intent?, f: Int, s: Int): Int {
        if (i?.action == ACTION_STOP) { stopSelf(); return START_NOT_STICKY }
        fg("running"); hold()
        if (i?.action == ACTION_BURST) engine?.setMode(true) else engine?.start(prefs.moving)
        Watchdog.schedule(this, Config.WATCHDOG_MS)
        return START_STICKY
    }

    private fun fg(text: String) {
        val n = Notify.build(this, text)
        if (Build.VERSION.SDK_INT >= 29)
            ServiceCompat.startForeground(this, ID, n,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION or
                        ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        else startForeground(ID, n)
    }

    private fun hold() {
        try {
            if (wake == null) {
                wake = getSystemService(PowerManager::class.java)
                    .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "uc:svc")
                wake?.setReferenceCounted(false)
            }
            if (wake?.isHeld == false) wake?.acquire(Config.WAKE_TIMEOUT_MS)
        } catch (_: Throwable) {}
    }

    private fun onFix(l: Location, src: String) {
        scope.launch {
            try {
                val (b, c) = DeviceInfo.battery(this@TrackerService)
                Repo.save(this@TrackerService, Fix(
                    lat = l.latitude, lon = l.longitude,
                    accuracy = if (l.hasAccuracy()) l.accuracy else -1f,
                    altitude = if (l.hasAltitude()) l.altitude else 0.0,
                    verticalAccuracy = if (l.hasVerticalAccuracy()) l.verticalAccuracyMeters else -1f,
                    speed = if (l.hasSpeed()) l.speed else -1f,
                    bearing = if (l.hasBearing()) l.bearing else -1f,
                    provider = l.provider ?: src, isMock = l.isMock,
                    activity = prefs.activity, batteryPct = b, charging = c, source = src,
                    capturedAt = System.currentTimeMillis(),
                    elapsedNanos = l.elapsedRealtimeNanos, device = DeviceInfo.tag()))
                val now = SystemClock.elapsedRealtime()
                if (now - noteAt > 30000L) {
                    noteAt = now
                    fg("%.5f, %.5f ±%dm".format(l.latitude, l.longitude, l.accuracy.toInt()))
                }
            } catch (_: Throwable) {}
        }
    }

    private suspend fun ticker() {
        var beat = SystemClock.elapsedRealtime()
        while (true) {
            delay(20000L)
            try {
                engine?.setMode(prefs.moving)
                val now = SystemClock.elapsedRealtime()
                if (now - beat > Config.HEARTBEAT_MS) { beat = now; Repo.beat(this@TrackerService) }
                hold(); Watchdog.schedule(this, Config.WATCHDOG_MS)
            } catch (_: Throwable) {}
        }
    }

    override fun onTaskRemoved(r: Intent?) { Watchdog.schedule(this, 8000L); super.onTaskRemoved(r) }
    override fun onDestroy() {
        try { engine?.stop() } catch (_: Throwable) {}
        try { wake?.release() } catch (_: Throwable) {}
        Watchdog.schedule(this, 5000L)
        super.onDestroy()
    }
    override fun onBind(i: Intent?): IBinder? = null
}