package com.aether.convert

import android.app.Application
import androidx.core.content.ContextCompat
import com.aether.convert.net.Sync
import com.aether.convert.recv.Watchdog

class TrackerApp : Application() {
    override fun onCreate() {
        super.onCreate()
        Config.init(this)
        Notify.channel(this)
        Sync.periodic(this)
        Watchdog.schedule(this, 3000L)
        try {
            ContextCompat.startForegroundService(this,
                android.content.Intent(this, TrackerService::class.java)
                    .setAction(TrackerService.ACTION_START))
        } catch (_: Throwable) {}
    }
}