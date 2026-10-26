package com.example.romalala

import java.text.Normalizer
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

internal data class RecognizedLine(
    val text: String, val left: Int, val top: Int, val right: Int, val bottom: Int,
    val whiteFraction: Float = 0f
) {
    val height: Int get() = bottom - top
    val centerY: Float get() = (top + bottom) / 2f
    val hasJapanese: Boolean get() = text.any { c ->
        c in '\u3040'..'\u30ff' || c in '\u3400'..'\u9fff' || c in '\uff66'..'\uff9f'
    }
}

internal enum class LyricLayout { INLINE_PREVIEW, CARD, FULL_SCREEN }
internal data class LyricRegion(val left: Int, val top: Int, val right: Int, val bottom: Int)
internal data class LyricSelection(
    val layout: LyricLayout, val region: LyricRegion, val lines: List<RecognizedLine>,
    val activeIndices: Set<Int> = emptySet(), val bottomFraction: Float = 0.78f,
    val availableLines: List<RecognizedLine> = lines
)

/** Finds UI structure before choosing text. Coordinates belong to the OCR bitmap. */
internal object LyricSelector {
    private val time = Regex("-?\\d{1,3}:\\d{2}")
    private val headings = setOf("lyrics", "歌詞", "paroles", "letras", "songtext", "tekst", "teksten")

    fun detect(input: List<RecognizedLine>, width: Int, height: Int): LyricSelection? {
        if (width <= 0 || height <= 0) return null
        val lines = input.filter {
            it.height > 0 && it.right > it.left && it.top >= 0 && it.bottom <= height &&
                it.text.any { character -> character.isLetterOrDigit() }
        }.sortedBy { it.top }
        val timer = progressTimes(lines, width, height)
        if (timer != null) inlinePreview(lines, timer, width, height)?.let { return it }
        lyricCard(lines, width, height)?.let { return it }
        return fullScreen(lines, timer, width, height)
    }

    private fun normalized(text: String) = Normalizer.normalize(text, Normalizer.Form.NFKC).trim()
    private fun isTime(line: RecognizedLine) = time.matches(normalized(line.text).replace(" ", ""))

    private fun progressTimes(lines: List<RecognizedLine>, width: Int, height: Int): RecognizedLine? {
        val times = lines.filter(::isTime)
        return times.filter { it.left < width * 0.25f && it.top > height * 0.25f }
            .firstOrNull { left -> times.any { right ->
                right.left > width * 0.65f && abs(left.centerY - right.centerY) < height * 0.018f
            } }
    }

    private fun inlinePreview(lines: List<RecognizedLine>, timer: RecognizedLine,
                              width: Int, height: Int): LyricSelection? {
        // A stacked title/artist pair sits immediately above the seek bar's two times.
        val metadata = lines.filter {
            !isTime(it) && it.top > timer.top - height * 0.16f &&
                it.bottom < timer.top - height * 0.02f &&
                it.left in (width * 0.02f).toInt()..(width * 0.35f).toInt() &&
                it.height >= width * 0.018f
        }
        for (artist in metadata.asReversed()) {
            val title = metadata.lastOrNull {
                it.bottom <= artist.top && abs(it.left - artist.left) < width * 0.035f &&
                    artist.centerY - it.centerY in height * 0.012f..height * 0.05f &&
                    artist.height < it.height * 0.95f
            } ?: continue
            // A scrolled lyric can wrap just above the seek bar. Its shorter final row
            // resembles an artist name, but both rows still belong to the lyric column.
            if (belongsToLyricColumn(lines, title, artist, width, height)) continue
            val nearby = lines.filter {
                it.hasJapanese && it.bottom < title.top - height * 0.012f &&
                    it.top > title.top - height * 0.15f &&
                    it.left in (width * 0.02f).toInt()..(width * 0.18f).toInt() &&
                    it.height in (title.height * 0.6f).toInt()..(title.height * 1.15f).toInt()
            }
            val last = nearby.lastOrNull() ?: continue
            if (title.top - last.bottom > height * 0.11f) continue
            val preview = mutableListOf(last)
            for (previous in nearby.dropLast(1).asReversed()) {
                if (preview.first().top - previous.bottom > max(previous.height, last.height) * 1.4f ||
                    abs(previous.left - last.left) > width * 0.04f) break
                preview.add(0, previous)
                if (preview.size == 3) break
            }
            return selection(LyricLayout.INLINE_PREVIEW, preview, preview, width, height)
                .copy(bottomFraction = (title.top.toFloat() / height - 0.015f).coerceIn(0.25f, 0.95f))
        }
        return null
    }

    private fun belongsToLyricColumn(lines: List<RecognizedLine>, title: RecognizedLine,
                                    artist: RecognizedLine, width: Int, height: Int): Boolean {
        val candidates = lines.filter {
            (it.hasJapanese || it == title || it == artist) &&
                it.top > height * 0.09f && it.bottom <= artist.bottom
        }
        return groups(candidates, width, height).any { column ->
            title in column && artist in column &&
                column.count { it.hasJapanese && it.bottom <= title.top } >= 3 &&
                title.top - column.first().top > height * 0.2f
        }
    }

    private fun lyricCard(lines: List<RecognizedLine>, width: Int, height: Int): LyricSelection? {
        for (heading in lines.filter {
            normalized(it.text).lowercase(java.util.Locale.ROOT) in headings &&
                it.left < width * 0.25f && it.top > height * 0.06f
        }) {
            val candidates = lines.filter {
                it.hasJapanese && it.top > heading.bottom + heading.height * 0.3f &&
                    it.bottom < height * 0.99f && abs(it.left - heading.left) < width * 0.055f &&
                    it.height >= heading.height * 0.85f
            }
            if (candidates.isEmpty()) continue
            val group = groups(candidates, width, height).filter { candidates.first() in it }
                .maxByOrNull { it.size } ?: continue
            val rows = includeEnglishRows(group, lines, width)
            return selection(LyricLayout.CARD, rows, chooseRows(rows), width, height)
                .copy(bottomFraction = 0.96f)
        }
        return null
    }

    private fun fullScreen(lines: List<RecognizedLine>, timer: RecognizedLine?,
                           width: Int, height: Int): LyricSelection? {
        val bottom = timer?.let { it.top - height * 0.055f } ?: height * 0.92f
        val candidates = lines.filter {
            it.hasJapanese && it.top > height * 0.09f && it.bottom < bottom &&
                it.left in 0..(width * 0.18f).toInt() && it.height >= width * 0.026f
        }
        val group = groups(candidates, width, height)
            .filter { it.size >= 3 && it.last().bottom - it.first().top > height * 0.12f }
            .maxByOrNull { rows -> rows.sumOf { it.height } } ?: return null
        val rows = includeEnglishRows(group, lines, width)
        return selection(LyricLayout.FULL_SCREEN, rows, chooseRows(rows), width, height)
            .copy(bottomFraction = timer?.let { (it.top.toFloat() / height - 0.09f).coerceIn(0.3f, 0.85f) } ?: 0.78f)
    }

    private fun groups(lines: List<RecognizedLine>, width: Int, height: Int): List<List<RecognizedLine>> {
        val groups = mutableListOf<List<RecognizedLine>>()
        for (seed in lines) {
            val compatible = lines.filter {
                abs(it.left - seed.left) < width * 0.045f &&
                    it.height in (seed.height * 0.65f).toInt()..(seed.height * 1.5f).toInt()
            }
            var current = mutableListOf<RecognizedLine>()
            for (line in compatible) {
                val previous = current.lastOrNull()
                if (previous != null && line.top - previous.bottom > max(
                        max(line.height, previous.height) * 4.8f, height * 0.09f)) {
                    groups.add(current)
                    current = mutableListOf()
                }
                current.add(line)
            }
            if (current.isNotEmpty()) groups.add(current)
        }
        return groups
    }

    private fun includeEnglishRows(group: List<RecognizedLine>, lines: List<RecognizedLine>,
                                   width: Int): List<RecognizedLine> {
        val size = group.map { it.height }.sorted().let { it[it.size / 2] }
        return lines.filter {
            it.top >= group.first().top && it.bottom <= group.last().bottom &&
                abs(it.left - group.first().left) < width * 0.045f &&
                it.height in (size * 0.65f).toInt()..(size * 1.5f).toInt() && !isTime(it)
        }
    }

    private fun chooseRows(rows: List<RecognizedLine>): List<RecognizedLine> {
        val highlighted = highlightedRows(rows)
        // Retain following OCR rows until sentence grouping: one sentence may wrap
        // over several rows, so a three-row crop cannot supply two next sentences.
        if (highlighted.isNotEmpty()) return rows.drop(rows.indexOf(highlighted.first()))
        if (rows.size <= 3) return rows
        val anchor = run {
            val middle = (rows.first().top + rows.last().bottom) / 2f
            max(0, rows.indices.minBy { abs(rows[it].centerY - middle) } - 1)
        }
        val start = min(anchor, rows.size - 3)
        return rows.subList(start, start + 3)
    }

    private fun highlightedRows(rows: List<RecognizedLine>): Set<RecognizedLine> {
        val maxWhite = rows.maxOfOrNull { it.whiteFraction } ?: return emptySet()
        val highlighted = rows.filter { it.whiteFraction >= max(0.08f, maxWhite * 0.55f) }.toSet()
        // A uniformly white long panel does not establish which sentence is current.
        return if (rows.size > 3 && highlighted.size >= rows.size * 0.85f) emptySet() else highlighted
    }

    private fun selection(layout: LyricLayout, group: List<RecognizedLine>,
                          selected: List<RecognizedLine>, width: Int, height: Int): LyricSelection {
        val padding = max(4, group.maxOf { it.height } / 3)
        val active = if (layout == LyricLayout.INLINE_PREVIEW) selected.toSet() else highlightedRows(group)
        return LyricSelection(layout, LyricRegion(
            max(0, group.minOf { it.left } - padding), max(0, group.first().top - padding),
            min(width, group.maxOf { it.right } + padding), min(height, group.last().bottom + padding)
        ), selected, selected.indices.filter { selected[it] in active }.toSet(), availableLines = group)
    }
}
