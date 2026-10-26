package com.example.romalala

import android.icu.text.Transliterator
import com.atilika.kuromoji.ipadic.Tokenizer
import java.text.Normalizer

/** Dictionary readings followed by ICU romanization. Confined to the OCR worker. */
class RomajiConverter {
    private val tokenizer = Tokenizer()
    private val kanaToLatin = Transliterator.getInstance("Hiragana-Latin; Katakana-Latin")

    private val cache = object : LinkedHashMap<String, String>(128, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, String>?): Boolean = size > 128
    }
    private val whitespace = Regex("\\s+")
    private val beforePunctuation = Regex("\\s+([、。！？!?.,」』）)])")
    private val afterOpening = Regex("([「『（(])\\s+")

    fun convert(japanese: String): String = japanese.lineSequence().joinToString("\n") { line ->
        val normalized = Normalizer.normalize(line, Normalizer.Form.NFKC)
            .replace(whitespace, " ").trim()
        if (normalized.isEmpty()) "" else cache.getOrPut(normalized) { convertLine(normalized) }
    }

    private fun convertLine(japanese: String): String = tokenizer.tokenize(japanese)
        .filter { it.surface.isNotBlank() }.joinToString(" ") { token ->
        val surface = token.surface
        when {
            token.partOfSpeechLevel1 == "助詞" && surface == "は" -> "wa"
            token.partOfSpeechLevel1 == "助詞" && surface == "へ" -> "e"
            token.partOfSpeechLevel1 == "助詞" && surface == "を" -> "o"
            else -> {
                val kana = token.reading.takeUnless { it.isNullOrEmpty() || it == "*" } ?: surface
                kanaToLatin.transliterate(kana)
            }
        }
    }.replace(beforePunctuation, "$1")
        .replace(afterOpening, "$1")
        .trim()
}
