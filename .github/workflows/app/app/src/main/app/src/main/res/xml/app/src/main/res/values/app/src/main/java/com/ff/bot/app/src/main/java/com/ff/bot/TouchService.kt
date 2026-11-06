package com.ff.bot

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.Intent
import android.graphics.Path
import android.os.Handler
import android.os.Looper
import android.view.accessibility.AccessibilityEvent

class TouchService : AccessibilityService() {

    companion object {
        @Volatile var instance: TouchService? = null
        private val main = Handler(Looper.getMainLooper())
    }

    override fun onServiceConnected() { instance = this }
    override fun onUnbind(i: Intent?): Boolean { instance = null; return super.onUnbind(i) }
    override fun onAccessibilityEvent(e: AccessibilityEvent?) {}
    override fun onInterrupt() {}

    fun drag(dx: Float, dy: Float) {
        val dm = resources.displayMetrics
        val cx = dm.widthPixels / 2f
        val cy = dm.heightPixels / 2f
        val ex = (cx + dx).coerceIn(1f, dm.widthPixels - 2f)
        val ey = (cy + dy).coerceIn(1f, dm.heightPixels - 2f)
        val p = Path().apply { moveTo(cx, cy); lineTo(ex, ey) }
        val g = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(p, 0, 22)).build()
        main.post { dispatchGesture(g, null, null) }
    }

    fun tap(x: Float, y: Float) {
        val p = Path().apply { moveTo(x, y) }
        val g = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(p, 0, 30)).build()
        main.post { dispatchGesture(g, null, null) }
    }
}
