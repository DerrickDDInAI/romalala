package com.example.romalala

import org.junit.Assert.*
import org.junit.Test

class OverlayContentTest {
    private fun row(text: String, top: Int) = RecognizedLine(text, 30, top, 700, top + 44)
    private fun selection(rows: List<RecognizedLine>, active: Set<Int> = emptySet()) = LyricSelection(
        LyricLayout.FULL_SCREEN, LyricRegion(20, 100, 800, 1600), rows, active, 0.78f)

    @Test fun wrappedHighlightBecomesOneSentenceAndNextLineIsSecondary() {
        val input = selection(listOf(row("君と tragedy", 400), row("night", 458), row("夜空", 600)), setOf(0, 1))
        val content = OverlayContent.from(input) { "romaji:$it" }!!
        assertEquals(listOf(OverlaySentence("romaji:君と tragedy night", SentenceRole.CURRENT),
            OverlaySentence("romaji:夜空", SentenceRole.NEXT)), content.sentences)
        assertEquals(0.78f, content.bottomFraction, 0.01f)
    }

    @Test fun JapaneseWordSplitByWrappingIsRestoredBeforeConversion() {
        val input = selection(listOf(row("届", 400), row("く", 458)), setOf(0, 1))
        assertEquals("届く", OverlayContent.from(input) { it }!!.sentences.single().text)
    }

    @Test fun endOfVisibleLyricsDoesNotFillNextSlotsWithPreviousText() {
        val input = selection(listOf(row("前の行", 400), row("今の行", 600)), setOf(1))
        val content = OverlayContent.from(input) { it }!!
        assertEquals(listOf(SentenceRole.CURRENT), content.sentences.map { it.role })
        assertEquals("今の行", content.sentences.first().text)
    }

    @Test fun wrappedCurrentAndTwoFollowingSentencesSurviveDetection() {
        val rows = listOf(row("前", 250), row("今", 400).copy(whiteFraction = 0.3f),
            row("の言葉", 458).copy(whiteFraction = 0.3f),
            row("次", 600), row("の言葉", 658), row("その次", 800), row("さらに先", 1000))
        val detected = LyricSelector.detect(rows, 877, 2048)!!
        val content = OverlayContent.from(detected) { it }!!
        assertEquals(listOf("今の言葉", "次の言葉", "その次"), content.sentences.map { it.text })
        assertEquals(listOf(SentenceRole.CURRENT, SentenceRole.NEXT, SentenceRole.NEXT),
            content.sentences.map { it.role })
    }

    @Test fun noHighlightDoesNotInventACurrentSentence() {
        val content = OverlayContent.from(selection(listOf(row("夢", 400), row("星", 600)))) { it }!!
        assertTrue(content.sentences.all { it.role == SentenceRole.CONTEXT })
    }

    @Test fun highlightChangeRerendersEvenWhenRomajiTextIsIdentical() {
        val neutral = selection(listOf(row("君", 400)))
        val active = neutral.copy(activeIndices = setOf(0))
        assertNotEquals(OverlayContent.from(neutral) { "kimi" }, OverlayContent.from(active) { "kimi" })
    }

    @Test fun inlinePreviewCarriesCurrentIdentityWithoutNeedingWhitePixelContrast() {
        val input = listOf(row("君と歩く", 1230).copy(bottom = 1264),
            row("Track title", 1380), row("Artist", 1434).copy(bottom = 1466),
            RecognizedLine("0:09", 50, 1550, 110, 1574),
            RecognizedLine("7:27", 760, 1550, 824, 1574))
        val detected = LyricSelector.detect(input, 877, 2048)!!
        assertEquals(setOf(0), detected.activeIndices)
        assertEquals(SentenceRole.CURRENT, OverlayContent.from(detected) { it }!!.sentences.first().role)
        assertTrue(detected.bottomFraction < 1380f / 2048)
    }

    @Test fun emptyConversionClearsContent() {
        assertNull(OverlayContent.from(selection(listOf(row("夢", 400)), setOf(0))) { "" })
        assertNull(OverlayContent.from(selection(emptyList())) { it })
    }
}
