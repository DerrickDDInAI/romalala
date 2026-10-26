package com.example.romalala

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Rect
import android.graphics.drawable.AdaptiveIconDrawable
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
class RomalalaDesignTest {
    private fun all(view: View): List<View> = listOf(view) + if (view is ViewGroup)
        (0 until view.childCount).flatMap { all(view.getChildAt(it)) } else emptyList()
    private fun layout(view: View, width: Int, height: Int) {
        view.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY))
        view.layout(0, 0, width, height)
    }
    private fun save(view: View, name: String) {
        val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        view.draw(Canvas(bitmap))
        save(bitmap, name)
    }
    private fun save(bitmap: Bitmap, name: String) {
        val output = File("build/reports/romalala-preview").apply { mkdirs() }
        FileOutputStream(File(output, "$name.png")).use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
    }

    @Test fun nativeMenuIsReadableAtSmallWidthsAndLargeFonts() {
        try {
            for ((name, width, height, scale) in listOf(
                Case("menu", 400, 900, 1f), Case("menu-narrow", 320, 740, 1f),
                Case("menu-large-font", 360, 800, 1.5f), Case("menu-largest-font", 320, 740, 2f), Case("menu-landscape", 720, 360, 1f)
            )) {
                RuntimeEnvironment.setFontScale(scale)
                val menu = RomalalaMenuView(RuntimeEnvironment.getApplication())
                menu.update(true, false, false)
                layout(menu, width, height)
                assertEquals("Open a world of songs", all(menu).filterIsInstance<TextView>().single {
                    it.isAccessibilityHeading
                }.text.toString())
                for (text in all(menu).filterIsInstance<TextView>().filter { it.visibility == View.VISIBLE }) {
                    val bounds = Rect(0, 0, text.width, text.height)
                    menu.offsetDescendantRectToMyCoords(text, bounds)
                    assertTrue("Text must fit horizontally: $name / ${text.text}", bounds.left >= 0 && bounds.right <= width)
                    text.layout?.let { l ->
                        for (line in 0 until l.lineCount) {
                            assertEquals("No clipped text: $name / ${text.text}", 0, l.getEllipsisCount(line))
                            assertTrue("Line fits: $name / ${text.text}", l.getLineWidth(line) <= l.width + 1)
                        }
                    }
                }
                // Start remains reachable in the scrollable menu on short windows and large fonts.
                val button = Rect(0, 0, menu.primary.width, menu.primary.height)
                menu.offsetDescendantRectToMyCoords(menu.primary, button)
                assertTrue(button.bottom <= menu.getChildAt(0).height)
                assertTrue(menu.primary.height >= 48)
                save(menu, name)
                if (name == "menu") {
                    menu.conversion.renderProgress(.48f); save(menu, "conversion-middle")
                    menu.conversion.finish()
                    menu.update(true, true, false); layout(menu, width, height); save(menu, "menu-active")
                }
            }
        } finally { RuntimeEnvironment.setFontScale(1f) }
    }

    @Test fun permissionAndRunningStatesExposeTheCorrectAction() {
        val menu = RomalalaMenuView(RuntimeEnvironment.getApplication())
        assertEquals("Allow lyric overlay", menu.primary.text)
        menu.update(true, false, false)
        assertEquals("Start singing", menu.primary.text)
        menu.update(true, false, true)
        assertFalse(menu.primary.isEnabled)
        menu.update(true, true, false)
        assertEquals("Stop overlay", menu.primary.text)
        assertEquals(View.VISIBLE, menu.spotify.visibility)
        menu.update(true, true, false, stopping = true)
        assertFalse(menu.primary.isEnabled)
        menu.update(true, false, false)
        assertTrue(menu.primary.isEnabled)
        assertEquals(View.GONE, menu.spotify.visibility)
    }

    @Test fun setupGuideIsAvailableWithoutClutteringTheMenu() {
        val context = RuntimeEnvironment.getApplication()
        val menu = RomalalaMenuView(context)
        val guide = all(menu).filterIsInstance<TextView>().single { it.text == context.getString(R.string.setup_instructions) }
        val toggle = all(menu).filterIsInstance<TextView>().single { it.text == context.getString(R.string.how_it_works) }
        assertEquals(View.GONE, guide.visibility)
        toggle.performClick()
        assertEquals(View.VISIBLE, guide.visibility)
        layout(menu, 320, 740)
        save(menu, "menu-guide")
        toggle.performClick()
        assertEquals(View.GONE, guide.visibility)
    }

    @Test fun cancellingConversionRestoresAllWordsAndReplay() {
        val demo = ConversionDemoView(RuntimeEnvironment.getApplication())
        demo.renderProgress(.35f)
        val romanized = all(demo).filterIsInstance<TextView>().filter { it.text.toString() in listOf("sekai", "no", "uta") }
        assertEquals(3, romanized.size)
        assertTrue(romanized.any { it.alpha < 1f })
        demo.finish()
        romanized.forEach { assertEquals(1f, it.alpha); assertEquals(0f, it.translationY) }
        assertTrue(demo.isClickable)
        assertTrue(demo.isFocusable)
    }

    @Test fun launcherAndCompactLumiRenderWithAlphaAndIdentity() {
        val context = RuntimeEnvironment.getApplication()
        assertEquals("Romalala", context.getString(R.string.app_name))
        val art = android.graphics.BitmapFactory.decodeResource(context.resources, R.drawable.lumi_singing)
        assertTrue("Lumi must have true alpha", art.hasAlpha())
        assertEquals(0, Color.alpha(art.getPixel(0, 0)))
        art.recycle()
        val icon = context.getDrawable(R.mipmap.ic_launcher)!!
        assertTrue(icon is AdaptiveIconDrawable)
        val bitmap = Bitmap.createBitmap(192, 192, Bitmap.Config.ARGB_8888)
        icon.setBounds(0, 0, 192, 192); icon.draw(Canvas(bitmap)); save(bitmap, "launcher-icon")
        for (singing in listOf(false, true)) {
            val compact = LyricBuddyView(context, singing)
            layout(compact, 64, 64); save(compact, if (singing) "lumi-singing" else "lumi-resting")
        }
    }
    private data class Case(val name: String, val width: Int, val height: Int, val scale: Float)
}
