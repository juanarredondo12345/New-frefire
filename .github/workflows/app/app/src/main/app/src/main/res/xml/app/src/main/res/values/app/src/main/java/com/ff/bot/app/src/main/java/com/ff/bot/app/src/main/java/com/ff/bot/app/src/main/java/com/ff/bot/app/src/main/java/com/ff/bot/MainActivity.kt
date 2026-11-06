package com.ff.bot

import android.content.Intent
import android.graphics.Color
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts

class MainActivity : ComponentActivity() {

    private lateinit var launch: androidx.activity.result.ActivityResultLauncher<Intent>

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)

        launch = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { r ->
            if (r.resultCode == RESULT_OK && r.data != null) {
                startForegroundService(Intent(this, OverlayService::class.java)
                    .putExtra("data", r.data).putExtra("code", r.resultCode))
                moveTaskToBack(true)
            }
        }

        if (!Settings.canDrawOverlays(this)) {
            startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:$packageName")))
        }

        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setBackgroundColor(Color.parseColor("#101014"))
        }
        col.addView(TextView(this).apply {
            text = "FFBot"
            setTextColor(Color.WHITE)
            textSize = 22f
        })
        col.addView(Button(this).apply {
            text = "1. Dar acceso de accesibilidad"
            setOnClickListener { startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }
        })
        col.addView(Button(this).apply {
            text = "2. Arrancar"
            setOnClickListener {
                launch.launch((getSystemService(MEDIA_PROJECTION_SERVICE)
                    as MediaProjectionManager).createScreenCaptureIntent())
            }
        })
        setContentView(col)
    }
}
