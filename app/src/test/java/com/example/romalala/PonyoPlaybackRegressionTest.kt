package com.example.romalala

import com.google.gson.JsonParser
import org.junit.Assert.*
import org.junit.Test

/** Measured boxes from the two reported frames; macOS Vision is not Android ML Kit. */
class PonyoPlaybackRegressionTest {
    private fun frames(): List<List<RecognizedLine>> {
        val source = javaClass.getResourceAsStream("/spotify-ponyo-playback.json")!!
            .bufferedReader().use { it.readText() }
        return JsonParser.parseString(source).asJsonArray.map { shot ->
            shot.asJsonObject["lines"].asJsonArray.map { element ->
                val row = element.asJsonObject
                RecognizedLine(row["text"].asString, row["left"].asInt, row["top"].asInt,
                    row["right"].asInt, row["bottom"].asInt, row["whiteFraction"].asFloat)
            }
        }
    }

    @Test fun reportedScrollKeepsActualHighlightedSentence() {
        val tracker = LyricContinuity()
        val selections = frames().map { tracker.resolve(LyricSelector.detect(it, 877, 2048))!! }
        assertTrue(selections.all { it.layout == LyricLayout.FULL_SCREEN })
        assertEquals(listOf("足っていいなかけちゃお！", "ニーギニギブーンブン"),
            selections.map { OverlayContent.from(it) { text -> text }!!.sentences.first().text })
    }

    @Test fun unobscuredLowerLyricIsNotAnInlinePreview() {
        val second = frames()[1].map { row ->
            // ML Kit may separate the Japanese row from the romaji drawn over it.
            if (row.text == "あの子kakkanoまっかっかの")
                row.copy(text = "あの子が大好きまっかっかの", top = 1407, bottom = 1452)
            else row
        }
        for (scale in listOf(0.6f, 1f, 1.25f)) {
            val rows = second.map { it.copy(left = (it.left * scale).toInt(), top = (it.top * scale).toInt(),
                right = (it.right * scale).toInt(), bottom = (it.bottom * scale).toInt()) }
            val selection = LyricSelector.detect(rows, (877 * scale).toInt(), (2048 * scale).toInt())!!
            assertEquals("layout at $scale", LyricLayout.FULL_SCREEN, selection.layout)
            assertEquals("ニーギニギブーンブン", OverlayContent.from(selection) { it }!!.sentences.first().text)
        }
    }

    @Test fun realJapaneseTrackMetadataAndWrappedPreviewStillWork() {
        val preview = listOf(
            RecognizedLine("見えている", 54, 1160, 700, 1194),
            RecognizedLine("歌詞の", 54, 1205, 700, 1239),
            RecognizedLine("続き", 54, 1250, 700, 1284))
        val player = listOf(
            RecognizedLine("日本語の曲名", 54, 1380, 700, 1424),
            RecognizedLine("歌手の名前", 54, 1434, 700, 1466),
            RecognizedLine("0:35", 50, 1550, 112, 1578),
            RecognizedLine("2:46", 760, 1550, 823, 1578))
        val selection = LyricSelector.detect(preview + player, 877, 2048)!!
        assertEquals(LyricLayout.INLINE_PREVIEW, selection.layout)
        assertEquals(preview, selection.lines)
    }
}
