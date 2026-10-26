package com.example.romalala

import org.junit.Assert.*
import org.junit.Test

class LyricSelectorTest {
    private fun row(text: String, top: Int, height: Int = 44, left: Int = 54,
                    right: Int = 780, white: Float = 0f) =
        RecognizedLine(text, left, top, right, top + height, white)

    private fun player(titleTop: Int, timeTop: Int, thumbnail: Boolean = false) = listOf(
        row("Track title", titleTop, 44, if (thumbnail) 188 else 54),
        row("Artist", titleTop + 54, 32, if (thumbnail) 188 else 54),
        row("0:09", timeTop, 24, 54, 115), row("7:27", timeTop, 24, 766, 824)
    )

    @Test fun previewAbovePlayerWorksWithAlbumArtAndAlmostHiddenCard() {
        // Screenshot 17.59.57 (1): card heading is below the old 88% crop.
        val preview = row("君と歩く", 1230, 34)
        val input = player(1380, 1550) + preview + row("Lyrics", 1980, 32, 70)
        val result = LyricSelector.detect(input, 877, 2048)!!
        assertEquals(LyricLayout.INLINE_PREVIEW, result.layout)
        assertEquals(listOf(preview), result.lines)
    }

    @Test fun previewWinsOverDuplicateLyricsInScrolledCard() {
        // Screenshot 17.59.57: one preview plus two wrapped active rows in the card.
        val preview = row("君と歩く", 670, 34)
        val input = player(820, 995) + preview + listOf(
            row("Lyrics", 1430, 32, 70), row("前の行", 1540, 44, 70),
            row("君と歩く", 1780, 52, 70, white = 0.3f),
            row("続く言葉", 1865, 52, 70, white = 0.3f)
        )
        val result = LyricSelector.detect(input, 877, 2048)!!
        assertEquals(LyricLayout.INLINE_PREVIEW, result.layout)
        assertEquals(listOf(preview), result.lines)
    }

    @Test fun previewWorksOverCanvasWithAndWithoutCardText() {
        for ((titleTop, timeTop, previewTop, cardTop) in listOf(
            listOf(1340, 1520, 1200, 1950), listOf(940, 1110, 790, 1560)
        )) {
            val preview = row("夢を見ている", previewTop, 34)
            val input = player(titleTop, timeTop, thumbnail = true) + preview +
                row("Lyrics", cardTop, 32, 70) + row("別の歌詞", cardTop + 125, 48, 70)
            val result = LyricSelector.detect(input.filter { it.bottom <= 2048 }, 877, 2048)!!
            assertEquals(LyricLayout.INLINE_PREVIEW, result.layout)
            assertEquals(listOf(preview), result.lines)
        }
    }

    @Test fun cardIncludesWrappedLineBelowOldCropWhenPreviewIsAbsent() {
        val input = player(820, 995) + listOf(
            row("Lyrics", 1430, 32, 70), row("前の行", 1540, 44, 70),
            row("君と歩く", 1780, 52, 70, white = 0.3f),
            row("続く言葉", 1865, 52, 70, white = 0.3f)
        )
        val result = LyricSelector.detect(input, 877, 2048)!!
        assertEquals(LyricLayout.CARD, result.layout)
        assertTrue(result.lines.any { it.top > 2048 * 0.88f })
        assertEquals(1865 + 52, result.lines.last().bottom)
    }

    @Test fun fullScreenUsesHighlightAndKeepsWrappedActiveRows() {
        val active = listOf(row("君と歩く", 840, 48, 36, white = 0.3f),
            row("続く言葉", 920, 48, 36, white = 0.28f))
        val input = listOf(
            row("日本語の曲名", 130, 24, 300), row("アーティスト", 170, 24, 300),
            row("前の歌詞", 460, 44, 36), row("その続き", 530, 44, 36),
            row("別の行", 630, 44, 36), row("続く", 700, 44, 36)
        ) + active + listOf(row("次の歌詞", 1070, 44, 36),
            row("その後", 1230, 44, 36), row("最後", 1430, 44, 36),
            row("2:19", 1790, 24, 54, 115), row("8:57", 1790, 24, 766, 824))
        val result = LyricSelector.detect(input, 877, 2048)!!
        assertEquals(LyricLayout.FULL_SCREEN, result.layout)
        assertEquals(active, result.lines.take(2))
        assertFalse(result.lines.any { it.text == "日本語の曲名" })
    }

    @Test fun unhighlightedFullScreenHasStableGeometricFallback() {
        val rows = (0 until 12).map { row("歌詞$it", 260 + it * 105, 44, 36) }
        val result = LyricSelector.detect(rows, 877, 2048)!!
        assertEquals(LyricLayout.FULL_SCREEN, result.layout)
        assertEquals(listOf("歌詞4", "歌詞5", "歌詞6"), result.lines.map { it.text })
    }

    @Test fun JapaneseTitleAndArtworkAloneAreNotLyrics() {
        val input = player(1380, 1550).map {
            if (it.text == "Track title") it.copy(text = "日本語の曲名") else it
        } + row("アルバム", 800, 38, 300)
        assertNull(LyricSelector.detect(input, 877, 2048))
        assertNull(LyricSelector.detect(listOf(row("日本語の曲名", 130, 28)), 877, 2048))
    }

    @Test fun translatedRowsWithinLyricsBlockArePreserved() {
        val input = listOf(row("夢", 400, 44, 36), row("Sing with me", 500, 44, 36),
            row("ｷﾐ", 600, 44, 36), row("夜空", 800, 44, 36))
        val result = LyricSelector.detect(input, 877, 2048)!!
        assertTrue(result.lines.any { it.text == "Sing with me" })
    }

    @Test fun layoutDetectionScalesWithCaptureSize() {
        val input = player(1380, 1550) + row("君と歩く", 1230, 34)
        for (scale in listOf(0.6f, 1.25f)) {
            val scaled = input.map { it.copy(left = (it.left * scale).toInt(),
                top = (it.top * scale).toInt(), right = (it.right * scale).toInt(),
                bottom = (it.bottom * scale).toInt()) }
            assertEquals(LyricLayout.INLINE_PREVIEW,
                LyricSelector.detect(scaled, (877 * scale).toInt(), (2048 * scale).toInt())?.layout)
        }
    }

    @Test fun wrappedPreviewIsKeptInReadingOrder() {
        val preview = listOf(row("君と歩く", 1200, 34), row("夜空の下", 1245, 34))
        val result = LyricSelector.detect(player(1380, 1550) + preview, 877, 2048)!!
        assertEquals(preview, result.lines)
    }

    @Test fun emptyInvalidAndNonJapaneseScreensClearSelection() {
        assertNull(LyricSelector.detect(emptyList(), 877, 2048))
        assertNull(LyricSelector.detect(player(1380, 1550), 877, 2048))
        assertNull(LyricSelector.detect(listOf(row("夢", 500)), 0, 0))
    }
}
