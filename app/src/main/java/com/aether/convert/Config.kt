package com.aether.convert

import android.content.Context

object Config {
    private var app: Context? = null
    fun init(c: Context) { app = c.applicationContext }

    val url: String
        get() = app?.let { Prefs(it).cfgUrl }?.takeIf { it.isNotBlank() }
            ?: BuildConfig.SUPABASE_URL.trimEnd('/')
    val key: String
        get() = app?.let { Prefs(it).cfgKey }?.takeIf { it.isNotBlank() }
            ?: BuildConfig.SUPABASE_KEY

    const val TABLE = "fixes"
    const val HIGH_INTERVAL_MS = 4_000L
    const val HIGH_FASTEST_MS = 1_500L
    const val IDLE_INTERVAL_MS = 40_000L
    const val IDLE_FASTEST_MS = 20_000L
    const val MAX_ACCURACY_M = 45f
    const val STILL_ACCURACY_M = 30f
    const val STILL_SPEED_MS = 0.6f
    const val MIN_MOVE_M = 4.0
    const val BATCH = 120
    const val KEEP_DAYS = 21
    const val HEARTBEAT_MS = 12 * 60_000L
    const val WATCHDOG_MS = 60_000L
    const val WAKE_TIMEOUT_MS = 10 * 60_000L
}