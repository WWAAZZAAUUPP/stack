package com.aether.convert

import android.app.Application
import com.aether.convert.net.Sync

class TrackerApp : Application() {
    override fun onCreate() {
        super.onCreate()
        Config.init(this)
        Notify.channel(this)
        Sync.periodic(this)
        Arm.arm(this, Prefs(this).moving)
    }
}