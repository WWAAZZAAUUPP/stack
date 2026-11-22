package com.aether.convert.recv

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.aether.convert.Arm
import com.aether.convert.Gate
import com.aether.convert.Prefs
import com.aether.convert.Repo
import com.google.android.gms.location.ActivityRecognitionResult
import com.google.android.gms.location.DetectedActivity
import com.google.android.gms.location.Geofence
import com.google.android.gms.location.GeofencingEvent
import com.google.android.gms.location.LocationResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

object Watchdog {
    fun schedule(c: Context, d: Long) { Arm.watch(c) }
}

class LocReceiver : BroadcastReceiver() {
    override fun onReceive(c: Context, i: Intent) {
        val r = LocationResult.extractResult(i) ?: return
        val app = c.applicationContext
        val pr = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                for (l in r.locations) Gate.handle(app, l, l.provider ?: "fused")
            } catch (_: Throwable) {
            } finally {
                pr.finish()
            }
        }
    }
}

class WatchReceiver : BroadcastReceiver() {
    override fun onReceive(c: Context, i: Intent) = Arm.arm(c, Prefs(c).moving)
}

class HeartReceiver : BroadcastReceiver() {
    override fun onReceive(c: Context, i: Intent) {
        val app = c.applicationContext
        val pr = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try { Repo.beat(app) } catch (_: Throwable) {} finally { pr.finish() }
        }
        Arm.heart(c)
        Arm.watch(c)
    }
}

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(c: Context, i: Intent) = Arm.arm(c, Prefs(c).moving)
}

class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(c: Context, i: Intent) = Arm.arm(c, Prefs(c).moving)
}

class PowerReceiver : BroadcastReceiver() {
    override fun onReceive(c: Context, i: Intent) = Arm.arm(c, true)
}

class ScreenReceiver : BroadcastReceiver() {
    override fun onReceive(c: Context, i: Intent) = Arm.arm(c, Prefs(c).moving)
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
            DetectedActivity.ON_BICYCLE -> "ON_BICYCLE"
            DetectedActivity.IN_VEHICLE -> "IN_VEHICLE"
            DetectedActivity.ON_FOOT -> "ON_FOOT"
            else -> "UNKNOWN"
        }
        val moving = t != DetectedActivity.STILL && t != DetectedActivity.UNKNOWN
        if (moving != p.moving) Arm.arm(c, moving)
    }
}

class GeofenceReceiver : BroadcastReceiver() {
    override fun onReceive(c: Context, i: Intent) {
        val ev = GeofencingEvent.fromIntent(i) ?: return
        if (ev.geofenceTransition == Geofence.GEOFENCE_TRANSITION_EXIT) Arm.arm(c, true)
    }
}