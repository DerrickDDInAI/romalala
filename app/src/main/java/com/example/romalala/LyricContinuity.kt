package com.example.romalala

import java.text.Normalizer
import kotlin.math.abs

/** Worker-thread state: a missing highlight advances to the known next sentence only once. */
internal class LyricContinuity {
    private data class NextSentence(val text: String, val following: String, val top: Int)
    private var next: NextSentence? = null

    fun reset() { next = null }

    fun resolve(selection: LyricSelection?): LyricSelection? {
        if (selection == null) {
            reset()
            return null
        }
        if (selection.activeIndices.isNotEmpty()) {
            val groups = OverlayContent.sentenceGroups(selection.lines, selection.activeIndices)
            val current = groups.indexOfFirst { group -> group.any { it in selection.activeIndices } }
            next = groups.getOrNull(current + 1)?.let { indices ->
                NextSentence(normalized(indices.joinToString("") { selection.lines[it].text }),
                    normalized(selection.lines.drop(indices.last() + 1).joinToString("") { it.text }),
                    selection.lines[indices.first()].top)
            }
            return selection
        }

        val expected = next ?: return selection
        val rows = selection.availableLines
        // Match source text, not screen position; scrolling and wrapping can change between frames.
        val matches = rows.indices.mapNotNull { start ->
            var text = ""
            for (end in start until rows.size) {
                text += normalized(rows[end].text)
                if (text == expected.text) return@mapNotNull start..end
                if (!expected.text.startsWith(text)) break
            }
            null
        }
        val match = matches.maxWithOrNull(compareBy<IntRange> { range ->
            val following = normalized(rows.drop(range.last + 1).joinToString("") { it.text })
            following.commonPrefixWith(expected.following).length
        }.thenBy { -abs(rows[it.first].top - expected.top) })
        if (match == null) {
            // A new song, seek, or unrelated screen must not inherit the old sentence.
            reset()
            return selection
        }
        // Keep the remembered anchor until an actual highlight returns. Repeated blank-highlight
        // frames must not walk through every upcoming sentence.
        return selection.copy(lines = rows.drop(match.first), activeIndices = (0 until match.count()).toSet())
    }

    private fun normalized(text: String) = Normalizer.normalize(text, Normalizer.Form.NFKC)
        .filterNot { it.isWhitespace() }
}
