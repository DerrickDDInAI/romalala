package com.example.romalala

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import java.io.File
import java.io.FileOutputStream
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29], qualifiers = "w400dp-h900dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class OverlayCardPreviewTest {
    @Test fun rendersActualCardForVisualReview() {
        val context = RuntimeEnvironment.getApplication()
        val card = OverlayCardView(context)
        val current = OverlayContent(listOf(
            OverlaySentence("hakanaki hibi to kono tragedy night", SentenceRole.CURRENT),
            OverlaySentence("sekai ga owaru made wa", SentenceRole.NEXT),
            OverlaySentence("hanareru koto mo nai", SentenceRole.NEXT)
        ), 0.78f)
        for ((name, content, width) in listOf(
            Triple("current", current, 368),
            Triple("context", current.copy(sentences = current.sentences.map { it.copy(role = SentenceRole.CONTEXT) }), 368),
            Triple("narrow", current, 292),
            Triple("long", current.copy(sentences = listOf(
                OverlaySentence("hakanaki hibi to kono tragedy night soshite sekai ga owaru made wa", SentenceRole.CURRENT)
            )), 368),
            Triple("large-font", current, 368),
            Triple("short-window", current, 368)
        )) {
            RuntimeEnvironment.setFontScale(if (name == "large-font") 1.4f else 1f)
            card.render(content)
            card.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(if (name == "short-window") 155 else 280, View.MeasureSpec.AT_MOST))
            card.layout(0, 0, card.measuredWidth, card.measuredHeight)
            assertTrue(card.measuredHeight in 1..280)
            fun descendants(view: View): List<View> = listOf(view) +
                if (view is ViewGroup) (0 until view.childCount).flatMap { descendants(view.getChildAt(it)) } else emptyList()
            val views = descendants(card)
            assertFalse(views.filterIsInstance<TextView>().any { it.text == "UP NEXT" || it.text == "PREVIOUS" })
            val upcoming = views.filterIsInstance<TextView>().filter { view ->
                content.sentences.drop(1).any { it.text == view.text }
            }
            val visibleUpcoming = upcoming.count { (it.parent as View).visibility == View.VISIBLE }
            if (name == "current") assertEquals(2, visibleUpcoming)
            if (name == "short-window") assertTrue(visibleUpcoming < 2)
            val buddy = views.filterIsInstance<LyricBuddyView>().single { it.visibility == View.VISIBLE }
            val lyric = views.filterIsInstance<TextView>().first { it.text == content.sentences.first().text }
            val buddyBounds = android.graphics.Rect(0, 0, buddy.width, buddy.height)
            val lyricBounds = android.graphics.Rect(0, 0, lyric.width, lyric.height)
            card.offsetDescendantRectToMyCoords(buddy, buddyBounds)
            card.offsetDescendantRectToMyCoords(lyric, lyricBounds)
            assertFalse("Avatar must not overlap lyric: $name", android.graphics.Rect.intersects(buddyBounds, lyricBounds))
            if (name == "narrow" || name == "large-font") assertTrue(buddyBounds.bottom <= lyricBounds.top)
            assertTrue("Primary lyric must fit: $name", lyricBounds.bottom <= card.height - card.paddingBottom)
            assertEquals("Primary lyric must remain complete: $name", 0, lyric.layout.getEllipsisCount(lyric.lineCount - 1))
            val bitmap = Bitmap.createBitmap(width + 32, card.measuredHeight + 32, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            canvas.drawColor(Color.rgb(23, 74, 34))
            canvas.translate(16f, 16f)
            canvas.saveLayerAlpha(0f, 0f, width.toFloat(), card.measuredHeight.toFloat(), 199)
            card.draw(canvas)
            canvas.restore()
            val directory = File("build/reports/overlay-preview").apply { mkdirs() }
            FileOutputStream(File(directory, "$name.png")).use {
                assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it))
            }
            bitmap.recycle()
        }
        RuntimeEnvironment.setFontScale(1f)
    }
}
