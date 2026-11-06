package com.ff.bot

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.PointF

object Engine {
    var aimEnabled = false
    var triggerEnabled = false
    var fov = 240f
    var smooth = 3f
    var gain = 1f
    var minHits = 12
    var hue = 0f
    var satMin = 0.55f
    var valMin = 0.45f
    var hueTol = 14f
    var found = 0

    fun detect(bmp: Bitmap, side: Int, ox: Int, oy: Int): PointF? {
        val px = IntArray(side * side)
        Bitmap.createBitmap(bmp, ox, oy, side, side).getPixels(px, 0, side, 0, 0, side, side)
        val cells = HashMap<Int, Int>()
        var y = 0
        while (y < side) {
            var x = 0
            while (x < side) {
                if (match(px[y * side + x])) {
                    val k = (y / 16) * 1000 + (x / 16)
                    cells[k] = (cells[k] ?: 0) + 1
                }
                x += 2
            }
            y += 2
        }
        val best = cells.maxByOrNull { it.value } ?: return null
        found = best.value
        if (best.value < minHits) return null

        var sx = 0f; var sy = 0f; var n = 0
        val x0 = (best.key % 1000) * 16
        val y0 = (best.key / 1000) * 16
        var j = y0
        while (j < y0 + 16) {
            var i = x0
            while (i < x0 + 16) {
                if (i < side && j < side && match(px[j * side + i])) { sx += i; sy += j; n++ }
                i += 2
            }
            j += 2
        }
        if (n == 0) return null
        val h = side / 2f
        return PointF((sx / n - h) / h, (sy / n - h) / h)
    }

    private val hsv = FloatArray(3)
    private fun match(c: Int): Boolean {
        Color.RGBToHSV((c shr 16) and 0xFF, (c shr 8) and 0xFF, c and 0xFF, hsv)
        if (hsv[1] < satMin || hsv[2] < valMin) return false
        var d = Math.abs(hsv[0] - hue) % 360f
        if (d > 180f) d = 360f - d
        return d <= hueTol
    }
}
