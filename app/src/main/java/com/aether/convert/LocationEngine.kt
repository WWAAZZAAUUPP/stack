package com.aether.convert

import android.Manifest
import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.Looper
import androidx.core.content.ContextCompat
import com.aether.convert.recv.ActivityReceiver
import com.aether.convert.recv.GeofenceReceiver
import com.google.android.gms.location.*
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

class LocationEngine(
    private val ctx: Context, private val prefs: Prefs,
    private val emit: (Location, String) -> Unit
) {
    private val fused = LocationServices.getFusedLocationProviderClient(ctx)
    private val geo = LocationServices.getGeofencingClient(ctx)
    private val lm = ctx.getSystemService(LocationManager::class.java)
    private var running = false
    private var high = true
    private var raw = false
    private var lastLat = Double.NaN
    private var lastLon = Double.NaN
    private var lastAt = 0L
    private var lastAcc = Float.MAX_VALUE

    private val cb = object : LocationCallback() {
        override fun onLocationResult(r: LocationResult) { for (l in r.locations) accept(l, "fused") }
    }
    private val rawListener = object : LocationListener {
        override fun onLocationChanged(l: Location) = accept(l, "gnss")
        override fun onProviderEnabled(p: String) {}
        override fun onProviderDisabled(p: String) {}
        @Deprecated("x") override fun onStatusChanged(p: String?, s: Int, e: android.os.Bundle?) {}
    }

    private fun allowed() =
        ContextCompat.checkSelfPermission(ctx, Manifest.permission.ACCESS_FINE_LOCATION) ==
                PackageManager.PERMISSION_GRANTED

    @SuppressLint("MissingPermission")
    fun start(moving: Boolean) {
        if (!allowed()) return
        if (!running) { running = true; attachActivity() }
        high = moving; apply()
    }

    @SuppressLint("MissingPermission")
    fun setMode(moving: Boolean) { if (!running || moving == high) return; high = moving; apply() }

    @SuppressLint("MissingPermission")
    private fun apply() {
        if (!allowed()) return
        val req = LocationRequest.Builder(
            if (high) Priority.PRIORITY_HIGH_ACCURACY else Priority.PRIORITY_BALANCED_POWER_ACCURACY,
            if (high) Config.HIGH_INTERVAL_MS else Config.IDLE_INTERVAL_MS)
            .setMinUpdateIntervalMillis(if (high) Config.HIGH_FASTEST_MS else Config.IDLE_FASTEST_MS)
            .setMinUpdateDistanceMeters(0f)
            .setWaitForAccurateLocation(high).build()
        try { fused.requestLocationUpdates(req, cb, Looper.getMainLooper()) } catch (_: Throwable) {}
        try { fused.getLastLocation().addOnSuccessListener { it?.let { l -> accept(l, "last") } } }
        catch (_: Throwable) {}
        rawGps(high)
    }

    @SuppressLint("MissingPermission")
    private fun rawGps(on: Boolean) {
        if (on == raw || !allowed()) return
        raw = on
        try {
            if (on) {
                if (lm.isProviderEnabled(LocationManager.GPS_PROVIDER))
                    lm.requestLocationUpdates(LocationManager.GPS_PROVIDER,
                        Config.HIGH_FASTEST_MS, 0f, rawListener, Looper.getMainLooper())
                if (lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER))
                    lm.requestLocationUpdates(LocationManager.NETWORK_PROVIDER,
                        30000L, 0f, rawListener, Looper.getMainLooper())
            } else lm.removeUpdates(rawListener)
        } catch (_: Throwable) {}
    }

    private fun accept(l: Location, src: String) {
        if (l.latitude.isNaN() || l.longitude.isNaN()) return
        if (abs(l.latitude) < 0.0001 && abs(l.longitude) < 0.0001) return
        val acc = if (l.hasAccuracy()) l.accuracy else 999f
        if (acc > Config.MAX_ACCURACY_M) return
        val now = System.currentTimeMillis()
        if (now - lastAt < 900L) return
        if (!lastLat.isNaN()) {
            val d = dist(lastLat, lastLon, l.latitude, l.longitude)
            if (d < Config.MIN_MOVE_M && acc >= lastAcc - 2f) return
            if (!prefs.moving && acc > Config.STILL_ACCURACY_M && d < 12.0) return
            if (!prefs.moving && l.hasSpeed() && l.speed < Config.STILL_SPEED_MS &&
                acc > Config.STILL_ACCURACY_M) return
        }
        lastLat = l.latitude; lastLon = l.longitude; lastAt = now; lastAcc = acc
        emit(l, src)
        if (!prefs.moving) fence(l.latitude, l.longitude)
    }

    private fun dist(a1: Double, o1: Double, a2: Double, o2: Double): Double {
        val r = 6371000.0
        val dLat = Math.toRadians(a2 - a1); val dLon = Math.toRadians(o2 - o1)
        val s = sin(dLat/2)*sin(dLat/2) + cos(Math.toRadians(a1))*cos(Math.toRadians(a2))*sin(dLon/2)*sin(dLon/2)
        return 2 * r * atan2(sqrt(s), sqrt(1 - s))
    }

    private fun attachActivity() {
        if (Build.VERSION.SDK_INT >= 29 &&
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.ACTIVITY_RECOGNITION) !=
            PackageManager.PERMISSION_GRANTED) return
        try {
            ActivityRecognition.getClient(ctx).requestActivityUpdates(6000L,
                PendingIntent.getBroadcast(ctx, 0x31, Intent(ctx, ActivityReceiver::class.java),
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE))
        } catch (_: Throwable) {}
    }

    @SuppressLint("MissingPermission")
    private fun fence(lat: Double, lon: Double) {
        if (!allowed()) return
        try {
            val f = Geofence.Builder().setRequestId("s")
                .setCircularRegion(lat, lon, 120f)
                .setExpirationDuration(Geofence.NEVER_EXPIRE)
                .setTransitionTypes(Geofence.GEOFENCE_TRANSITION_EXIT).build()
            val pi = PendingIntent.getBroadcast(ctx, 0x32, Intent(ctx, GeofenceReceiver::class.java),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE)
            geo.removeGeofences(pi)
            geo.addGeofences(GeofencingRequest.Builder()
                .setInitialTrigger(GeofencingRequest.INITIAL_TRIGGER_EXIT)
                .addGeofence(f).build(), pi)
        } catch (_: Throwable) {}
    }

    fun stop() {
        running = false
        try { fused.removeLocationUpdates(cb) } catch (_: Throwable) {}
        try { lm.removeUpdates(rawListener) } catch (_: Throwable) {}
    }
}