package com.aether.convert

import android.content.Context
import android.location.Location
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

object Gate {

    private fun sp(c: Context) = c.getSharedPreferences("uc", Context.MODE_PRIVATE)

    suspend fun handle(ctx: Context, l: Location, src: String) {
        if (l.latitude.isNaN() || l.longitude.isNaN()) return
        if (abs(l.latitude) < 0.0001 && abs(l.longitude) < 0.0001) return

        val acc = if (l.hasAccuracy()) l.accuracy else 999f
        if (acc > Config.MAX_ACCURACY_M) return

        val p = Prefs(ctx)
        val s = sp(ctx)
        val now = System.currentTimeMillis()
        if (now - p.lastAt < 900L) return

        if (p.lastAt > 0L) {
            val d = dist(p.lastLat, p.lastLon, l.latitude, l.longitude)
            val lastAcc = s.getFloat("lacc", Float.MAX_VALUE)
            if (d < Config.MIN_MOVE_M && acc >= lastAcc - 2f) return
            if (!p.moving && acc > Config.STILL_ACCURACY_M && d < 12.0) return
            if (!p.moving && l.hasSpeed() && l.speed < Config.STILL_SPEED_MS &&
                acc > Config.STILL_ACCURACY_M) return
        }

        s.edit().putFloat("lacc", acc).apply()

        val (b, c) = DeviceInfo.battery(ctx)
        Repo.save(ctx, Fix(
            lat = l.latitude, lon = l.longitude, accuracy = acc,
            altitude = if (l.hasAltitude()) l.altitude else 0.0,
            verticalAccuracy = if (l.hasVerticalAccuracy()) l.verticalAccuracyMeters else -1f,
            speed = if (l.hasSpeed()) l.speed else -1f,
            bearing = if (l.hasBearing()) l.bearing else -1f,
            provider = l.provider ?: src, isMock = l.isMock,
            activity = p.activity, batteryPct = b, charging = c, source = src,
            capturedAt = now, elapsedNanos = l.elapsedRealtimeNanos,
            device = DeviceInfo.tag()))
    }

    fun dist(a1: Double, o1: Double, a2: Double, o2: Double): Double {
        val r = 6371000.0
        val dLat = Math.toRadians(a2 - a1)
        val dLon = Math.toRadians(o2 - o1)
        val x = sin(dLat / 2) * sin(dLat / 2) +
                cos(Math.toRadians(a1)) * cos(Math.toRadians(a2)) *
                sin(dLon / 2) * sin(dLon / 2)
        return 2 * r * atan2(sqrt(x), sqrt(1 - x))
    }
}