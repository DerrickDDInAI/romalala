package com.example.romalala

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Rect
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29])
class LyricAppearanceTest {
    @Test fun distinguishesWhiteHighlightFromMutedBlueAndGreenLyrics() {
        for (muted in listOf(Color.rgb(166, 210, 240), Color.rgb(172, 210, 149))) {
            val bitmap = Bitmap.createBitmap(30, 10, Bitmap.Config.ARGB_8888)
            bitmap.eraseColor(muted)
            for (y in 0 until 10) for (x in 10 until 20) bitmap.setPixel(x, y, Color.WHITE)
            assertEquals(0f, LyricAppearance.whiteFraction(bitmap, listOf(Rect(0, 0, 10, 10))), 0.01f)
            assertEquals(1f, LyricAppearance.whiteFraction(bitmap, listOf(Rect(10, 0, 20, 10))), 0.01f)
            bitmap.recycle()
        }
    }

    @Test fun glyphBoxesAreClippedToBitmapAndEmptyBoxesHaveNoHighlight() {
        val bitmap = Bitmap.createBitmap(10, 10, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(Color.WHITE)
        assertEquals(1f, LyricAppearance.whiteFraction(bitmap, listOf(Rect(-5, -5, 20, 20))), 0.01f)
        assertEquals(0f, LyricAppearance.whiteFraction(bitmap, listOf(Rect(20, 20, 30, 30))), 0.01f)
        assertEquals(0f, LyricAppearance.whiteFraction(bitmap, emptyList()), 0.01f)
        bitmap.recycle()
    }
}
