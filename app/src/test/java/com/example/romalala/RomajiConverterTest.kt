package com.example.romalala

import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29])
class RomajiConverterTest {
    @Test fun particlesUseTheirSpokenReadings() {
        val result = RomajiConverter().convert("私は学校へ行く。水を飲む。")
        assertTrue(result, result.contains("watashi wa"))
        assertTrue(result, result.contains(" e iku"))
        assertTrue(result, result.contains("mizu o nomu"))
    }

    @Test fun normalizesHalfWidthKanaAndPreservesLines() {
        val converter = RomajiConverter()
        assertEquals(converter.convert("カタカナ"), converter.convert("ｶﾀｶﾅ"))
        assertEquals("kimi\nboku", converter.convert("君\n僕"))
        assertEquals("", converter.convert("   "))
    }

    @Test fun repeatedLinesAreStableAndPunctuationHasNoLeadingSpace() {
        val converter = RomajiConverter()
        val result = converter.convert("君、僕。")
        assertFalse(result, result.contains(" 、") || result.contains(" 。"))
        assertEquals(result, converter.convert("君、僕。"))
    }
}
