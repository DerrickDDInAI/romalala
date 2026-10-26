package com.example.romalala

internal enum class SentenceRole { CURRENT, NEXT, CONTEXT }
internal data class OverlaySentence(val text: String, val role: SentenceRole)
internal data class OverlayContent(val sentences: List<OverlaySentence>, val bottomFraction: Float) {
    companion object {
        /** Preserve highlight identity and restore sentences split by visual wrapping. */
        fun from(selection: LyricSelection, convert: (String) -> String): OverlayContent? {
            if (selection.lines.isEmpty()) return null
            val groups = sentenceGroups(selection.lines, selection.activeIndices)
            fun sentence(index: Int, role: SentenceRole): OverlaySentence {
                val source = groups[index].map { selection.lines[it].text.trim() }
                    .reduce { text, next ->
                        val join = if (isJapanese(text.lastOrNull()) && isJapanese(next.firstOrNull())) "" else " "
                        text + join + next
                    }
                return OverlaySentence(convert(source), role)
            }
            val current = groups.indexOfFirst { group -> group.any { it in selection.activeIndices } }
            val sentences = if (current >= 0) {
                buildList {
                    add(sentence(current, SentenceRole.CURRENT))
                    for (next in (current + 1 until groups.size).take(2)) {
                        add(sentence(next, SentenceRole.NEXT))
                    }
                }
            } else {
                groups.indices.take(2).map { sentence(it, SentenceRole.CONTEXT) }
            }.filter { it.text.isNotBlank() }
            return if (sentences.isEmpty()) null else OverlayContent(sentences, selection.bottomFraction)
        }

        internal fun sentenceGroups(lines: List<RecognizedLine>, activeIndices: Set<Int>): List<List<Int>> {
            val groups = mutableListOf<MutableList<Int>>()
            for (index in lines.indices) {
                val previous = groups.lastOrNull()?.lastOrNull()
                val line = lines[index]
                val sameSentence = previous != null &&
                    (index in activeIndices) == (previous in activeIndices) &&
                    (index in activeIndices ||
                        line.top - lines[previous].bottom <=
                        maxOf(line.height, lines[previous].height) * 0.9f)
                if (sameSentence) groups.last().add(index) else groups.add(mutableListOf(index))
            }
            return groups
        }

        private fun isJapanese(c: Char?) = c != null &&
            (c in '\u3040'..'\u30ff' || c in '\u3400'..'\u9fff' || c in '\uff66'..'\uff9f')
    }
}
