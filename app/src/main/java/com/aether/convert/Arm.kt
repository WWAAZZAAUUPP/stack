package com.aether.convert

import android.annotation.SuppressLint
import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.SystemClock
import androidx.core.content.ContextCompat
import com.aether.convert.net.Sync
import com.aether.convert.recv.HeartReceiver
import com.aether.convert.recv.LocReceiver
import com.aether.convert.recv.WatchReceiver
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority

object Arm {

    private const val RC_LOC = 0x60
    private const val RC_WATCH = 0x61
    private const val RC_HEART = 0x62

    private fun locPi(c: Context): PendingIntent =
        PendingIntent.getBroadcast(c, RC_LOC, Intent(c, LocReceiver::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE)
    fun arm(c: Context, moving: Boolean) {
        val p = Prefs(c)
        p.moving = moving
        if (moving) engage(c) else park(c)
        watch(c)
        heart(c)
        Sync.periodic(c)
    }

    @SuppressLint("MissingPermission")
    private fun engage(c: Context) {
        try {
            LocationServices.getFusedLocationProviderClient(c).removeLocationUpdates(locPi(c))
        } catch (_: Throwable) {}
        try {
            ContextCompat.startForegroundService(c,
                Intent(c, TrackerService::class.java).setAction(TrackerService.ACTION_START))
        } catch (_: Throwable) {}
    }

    @SuppressLint("MissingPermission")
    private fun park(c: Context) {
        try { c.stopService(Intent(c, TrackerService::class.java)) } catch (_: Throwable) {}
        register(c, false)
    }

    @SuppressLint("MissingPermission")
    fun register(c: Context, moving: Boolean) {
        val interval = if (moving) 20_000L else 60_000L
        val min = if (moving) 15_000L else 45_000L
        val age = if (moving) 10_000L else 60_000L
        val req = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, interval)
            .setMinUpdateIntervalMillis(min)
            .setMaxUpdateAgeMillis(age)
            .setMinUpdateDistanceMeters(0f)
            .build()
        try {
            LocationServices.getFusedLocationProviderClient(c)
                .requestLocationUpdates(req, locPi(c))
        } catch (_: Throwable) {}
    }
    fun watch(c: Context) {
        try {
            c.getSystemService(AlarmManager::class.java)?.setAndAllowWhileIdle(
                AlarmManager.ELAPSED_REALTIME_WAKEUP,
                SystemClock.elapsedRealtime() + 60_000L,
                PendingIntent.getBroadcast(c, RC_WATCH, Intent(c, WatchReceiver::class.java),
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE))
        } catch (_: Throwable) {}
    }

    fun heart(c: Context) {
        try {
            c.getSystemService(AlarmManager::class.java)?.setAndAllowWhileIdle(
                AlarmManager.ELAPSED_REALTIME_WAKEUP,
                SystemClock.elapsedRealtime() + Config.HEARTBEAT_MS,
                PendingIntent.getBroadcast(c, RC_HEART, Intent(c, HeartReceiver::class.java),
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE))
        } catch (_: Throwable) {}
    }
}