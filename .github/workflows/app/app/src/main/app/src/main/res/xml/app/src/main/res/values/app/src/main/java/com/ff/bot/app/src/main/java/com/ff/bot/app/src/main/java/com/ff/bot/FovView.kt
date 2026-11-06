package com.ff.bot

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.view.View

class FovView(c: Context) : View(c) {
    private val p = Paint().apply {
        style = Paint.Style.STROKE
        strokeWidth = 3f
        color = Color.argb(200, 125, 211, 252)
        isAntiAlias = true
    }
    override fun onDraw(canvas: Canvas) {
        val r = Engine.fov
        canvas.drawCircle(width / 2f, height / 2f, r, p)
        invalidate()
    }
}
