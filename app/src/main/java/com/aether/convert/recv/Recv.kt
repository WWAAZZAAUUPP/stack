package com.aether.convert.recv

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.SystemClock
import androidx.core.content.ContextCompat
import com.aether.convert.Prefs
import com.aether.convert.TrackerService
import com.google.android.gms.location.*

object Watchdog {
    private fun pi(c: Context) = PendingIntent.getBroadcast(c, 0x51,
        Intent(c, AlarmReceiver::class.java),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    fun schedule(c: Context, d: Long) {
        try {
            c.getSystemService(AlarmManager::class.java)?.setAndAllowWhileIdle(
                AlarmManager.ELAPSED_REALTIME_WAKEUP, SystemClock.elapsedRealtime() + d, pi(c))
        } catch (_: Throwable) {}
    }
}

private fun boot(c: Context, a: String) {
    try { ContextCompat.startForegroundService(c, Intent(c, TrackerService::class.java).setAction(a)) }
    catch (_: Throwable) {}
}

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(c: Context, i: Intent) { boot(c, TrackerService.ACTION_START); Watchdog.schedule(c, 5000) }
}
class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(c: Context, i: Intent) { boot(c, TrackerService.ACTION_START); Watchdog.schedule(c, 60000) }
}
class PowerReceiver : BroadcastReceiver() {
    override fun onReceive(c: Context, i: Intent) = boot(c, TrackerService.ACTION_BURST)
}
class ScreenReceiver : BroadcastReceiver() {
    override fun onReceive(c: Context, i: Intent) = boot(c, TrackerService.ACTION_BURST)
}
class ActivityReceiver : BroadcastReceiver() {
    override fun onReceive(c: Context, i: Intent) {
        val r = ActivityRecognitionResult.extractResult(i) ?: return
        val t = r.mostProbableActivity?.type ?: return
        val p = Prefs(c)
        p.activity = when (t) {
            DetectedActivity.STILL -> "STILL"
            DetectedActivity.WALKING -> "WALKING"
            DetectedActivity.RUNNING -> "RUNNING"
            DetectedActivity.IN_VEHICLE -> "IN_VEHICLE"
            else -> "UNKNOWN"
        }
        p.moving = t != DetectedActivity.STILL && t != DetectedActivity.UNKNOWN
    }
}
class GeofenceReceiver : BroadcastReceiver() {
    override fun onReceive(c: Context, i: Intent) {
        val ev = GeofencingEvent.fromIntent(i) ?: return
        if (ev.geofenceTransition == Geofence.GEOFENCE_TRANSITION_EXIT) Prefs(c).moving = true
        boot(c, TrackerService.ACTION_BURST)
    }
}