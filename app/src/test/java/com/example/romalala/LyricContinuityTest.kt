package com.example.romalala

import org.junit.Assert.*
import org.junit.Test

class LyricContinuityTest {
    private val tracker = LyricContinuity()
    private fun row(text: String, top: Int, white: Float = 0f) =
        RecognizedLine(text, 36, top, 780, top + 44, white)
    private fun panel(active: String? = null, offset: Int = 0, note: Boolean = false) =
        listOf("前の歌詞", "古い歌詞", "今の歌詞", "次の歌詞", "その次の歌詞", "最後の歌詞")
            .mapIndexed { index, text -> row(text, 300 + index * 200 + offset,
                if (text == active) 0.3f else 0f) } +
            if (note) listOf(row("♫", 800 + offset, 0.5f)) else emptyList()
    private fun content(rows: List<RecognizedLine>) = OverlayContent.from(
        tracker.resolve(LyricSelector.detect(rows, 877, 2048))!!) { it }!!

    @Test fun highlightLossPromotesPreviouslyTranslatedNextSentence() {
        val first = content(panel("今の歌詞"))
        assertEquals(listOf("今の歌詞", "次の歌詞", "その次の歌詞"), first.sentences.map { it.text })
        val second = content(panel())
        assertEquals(listOf("次の歌詞", "その次の歌詞", "最後の歌詞"), second.sentences.map { it.text })
        assertEquals(listOf(SentenceRole.CURRENT, SentenceRole.NEXT, SentenceRole.NEXT),
            second.sentences.map { it.role })
    }

    @Test fun highlightedMusicNoteAdvancesToNextLyricInsteadOfBecomingCurrent() {
        content(panel("今の歌詞"))
        assertEquals("次の歌詞", content(panel(note = true)).sentences.first().text)
    }

    @Test fun repeatedFramesWithoutHighlightDoNotKeepAdvancing() {
        content(panel("今の歌詞"))
        repeat(10) { assertEquals("次の歌詞", content(panel(note = it % 2 == 0)).sentences.first().text) }
    }

    @Test fun scrollDoesNotChangeSentenceIdentity() {
        content(panel("今の歌詞"))
        assertEquals("次の歌詞", content(panel(offset = -100)).sentences.first().text)
    }

    @Test fun nextSentenceCanBeOutsideGeometricFallbackAndWrapDifferently() {
        val rows = (0 until 10).map { row("歌詞$it", 260 + it * 150,
            if (it == 6) 0.3f else 0f) }
        content(rows)
        val noHighlight = rows.flatMap {
            if (it.text == "歌詞7") listOf(row("歌", it.top), row("詞7", it.top + 58))
            else listOf(it.copy(whiteFraction = 0f))
        }
        assertEquals("歌詞7", content(noHighlight).sentences.first().text)
    }

    @Test fun actualHighlightOverridesInferredCurrentIncludingSeekingBack() {
        content(panel("今の歌詞"))
        content(panel())
        assertEquals("その次の歌詞", content(panel("その次の歌詞")).sentences.first().text)
        assertEquals("最後の歌詞", content(panel()).sentences.first().text)
        assertEquals("今の歌詞", content(panel("今の歌詞")).sentences.first().text)
        assertEquals("次の歌詞", content(panel()).sentences.first().text)
    }

    @Test fun noEarlierHighlightKeepsNeutralContext() {
        assertTrue(content(panel()).sentences.all { it.role == SentenceRole.CONTEXT })
    }

    @Test fun emptyDetectionAndExplicitResetForgetEarlierSong() {
        content(panel("今の歌詞"))
        assertNull(tracker.resolve(null))
        assertTrue(content(panel()).sentences.all { it.role == SentenceRole.CONTEXT })
        content(panel("今の歌詞"))
        tracker.reset()
        assertTrue(content(panel()).sentences.all { it.role == SentenceRole.CONTEXT })
    }

    @Test fun differentLyricsCannotInheritOldNextSentence() {
        content(panel("今の歌詞"))
        assertTrue(content(panel().map { it.copy(text = "別の曲${it.text}") })
            .sentences.all { it.role == SentenceRole.CONTEXT })
        assertTrue(content(panel()).sentences.all { it.role == SentenceRole.CONTEXT })
    }

    @Test fun endOfSongDoesNotInventNextSentence() {
        assertEquals(1, content(panel("最後の歌詞")).sentences.size)
        assertTrue(content(panel()).sentences.all { it.role == SentenceRole.CONTEXT })
    }

    @Test fun repeatedLyricsPreferKnownFollowingContext() {
        val rows = listOf(row("同じ言葉", 300), row("違う続き", 500),
            row("今の歌詞", 700, 0.3f), row("同じ言葉", 900), row("正しい続き", 1100))
        content(rows)
        assertEquals(listOf("同じ言葉", "正しい続き"),
            content(rows.map { it.copy(whiteFraction = 0f) }).sentences.map { it.text })
    }
}
