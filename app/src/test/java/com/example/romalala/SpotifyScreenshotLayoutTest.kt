package com.example.romalala

import com.google.gson.JsonParser
import org.junit.Assert.*
import org.junit.Test

/** Measured screenshot boxes from macOS Vision, with lyrics replaced by placeholders.
 * Tests the selector on real layout measurements; this does not test Android ML Kit OCR.
 */
class SpotifyScreenshotLayoutTest {
    @Test fun detectsAllSixSuppliedLayoutsAtDifferentCaptureScales() {
        val source = javaClass.getResourceAsStream("/spotify-layouts.json")!!.bufferedReader().use { it.readText() }
        for (element in JsonParser.parseString(source).asJsonArray) {
            val shot = element.asJsonObject
            val name = shot["name"].asString
            for (scale in listOf(1f, 0.6f, 1.25f)) {
                val lines = shot["lines"].asJsonArray.map {
                    val row = it.asJsonObject
                    fun coordinate(key: String) = (row[key].asInt * scale).toInt()
                    RecognizedLine(row["text"].asString, coordinate("left"), coordinate("top"),
                        coordinate("right"), coordinate("bottom"), row["whiteFraction"].asFloat)
                }
                val result = LyricSelector.detect(lines, (shot["width"].asInt * scale).toInt(),
                    (shot["height"].asInt * scale).toInt())
                assertEquals("$name at $scale", shot["layout"].asString, result?.layout?.name)
                assertTrue("$name selected text", result!!.lines.isNotEmpty())
                if (result.layout == LyricLayout.INLINE_PREVIEW) {
                    val previewTop = when {
                        "(1)" in name -> 1226
                        "(2)" in name -> 1190
                        "(3)" in name -> 785
                        else -> 666
                    }
                    assertEquals("$name picks only the current preview", listOf((previewTop * scale).toInt()),
                        result.lines.map { it.top })
                }
            }
        }
    }
}
