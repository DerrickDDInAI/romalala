package com.example.romalala

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Rect
import kotlin.math.max
import kotlin.math.min

/** Spotify's active words are white in the supplied blue and green themes. */
internal object LyricAppearance {
    fun whiteFraction(bitmap: Bitmap, boxes: List<Rect>): Float {
        var white = 0
        var sampled = 0
        for (box in boxes) {
            val left = max(0, box.left)
            val top = max(0, box.top)
            val right = min(bitmap.width, box.right)
            val bottom = min(bitmap.height, box.bottom)
            val step = max(1, (bottom - top) / 24)
            for (y in top until bottom step step) for (x in left until right step step) {
                val color = bitmap.getPixel(x, y)
                val r = Color.red(color)
                val g = Color.green(color)
                val b = Color.blue(color)
                if (min(r, min(g, b)) >= 210 && max(r, max(g, b)) - min(r, min(g, b)) <= 35) white++
                sampled++
            }
        }
        return if (sampled == 0) 0f else white.toFloat() / sampled
    }
}
