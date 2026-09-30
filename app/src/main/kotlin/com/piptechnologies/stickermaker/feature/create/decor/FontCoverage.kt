package com.piptechnologies.stickermaker.feature.create.decor

import java.nio.ByteBuffer

/**
 * The code points a font file maps to a glyph of its own, read from its `cmap` table (the format 12
 * subtable when present, else format 4). `Paint.hasGlyph` can't tell this: a bundled typeface always
 * falls back to the system fonts, so it reports every letter any system font can draw.
 */
class FontCoverage private constructor(private val ranges: IntArray) {

    /** Whether the font has a glyph for [codePoint]. */
    fun covers(codePoint: Int): Boolean {
        var lo = 0
        var hi = ranges.size / 2 - 1
        while (lo <= hi) {
            val mid = (lo + hi) ushr 1
            when {
                codePoint < ranges[2 * mid] -> hi = mid - 1
                codePoint > ranges[2 * mid + 1] -> lo = mid + 1
                else -> return true
            }
        }
        return false
    }

    companion object {
        private const val CMAP = 0x636D6170 // "cmap"

        /** The coverage of a TrueType or OpenType font file; null when it has no Unicode `cmap` that reads cleanly. */
        fun read(font: ByteArray): FontCoverage? = runCatching { parse(ByteBuffer.wrap(font)) }.getOrNull()

        private fun parse(b: ByteBuffer): FontCoverage? {
            val cmap = (0 until u16(b, 4)).map { 12 + it * 16 }
                .firstOrNull { b.getInt(it) == CMAP }
                ?.let { b.getInt(it + 8) } ?: return null
            val unicode = (0 until u16(b, cmap + 2)).map { cmap + 4 + it * 8 }
                .filter { record ->
                    val platform = u16(b, record)
                    platform == 0 || (platform == 3 && u16(b, record + 2).let { it == 1 || it == 10 })
                }
                .map { record -> cmap + b.getInt(record + 4) }
            val ranges = unicode.firstOrNull { u16(b, it) == 12 }?.let { format12(b, it) }
                ?: unicode.firstOrNull { u16(b, it) == 4 }?.let { format4(b, it) }
                ?: return null
            return FontCoverage(merged(ranges))
        }

        /** Groups of (first code point, last code point, first glyph); glyph 0 means missing. */
        private fun format12(b: ByteBuffer, at: Int): List<IntRange> = (0 until b.getInt(at + 12)).mapNotNull { i ->
            val group = at + 16 + i * 12
            val first = b.getInt(group)
            val last = b.getInt(group + 4)
            val from = if (b.getInt(group + 8) == 0) first + 1 else first
            if (from <= last) from..last else null
        }

        /**
         * Segments of the Basic Multilingual Plane; null when they overlap or run out of order (a broken
         * table), which also bounds the walk to 65,536 code points.
         */
        private fun format4(b: ByteBuffer, at: Int): List<IntRange>? {
            val segments = u16(b, at + 6) / 2
            val ends = at + 14
            val starts = ends + segments * 2 + 2
            val deltas = starts + segments * 2
            val offsets = deltas + segments * 2
            val out = ArrayList<IntRange>()
            var previousEnd = -1
            for (i in 0 until segments) {
                val start = u16(b, starts + i * 2)
                val end = u16(b, ends + i * 2)
                if (start > end || start <= previousEnd) return null
                previousEnd = end
                val delta = b.getShort(deltas + i * 2).toInt()
                val offsetAt = offsets + i * 2
                val offset = u16(b, offsetAt)
                var run = -1
                for (cp in start..end) {
                    val glyph = if (offset == 0) {
                        (cp + delta) and 0xFFFF
                    } else {
                        u16(b, offsetAt + offset + (cp - start) * 2).let { if (it == 0) 0 else (it + delta) and 0xFFFF }
                    }
                    if (glyph != 0 && run < 0) run = cp
                    if (glyph == 0 && run >= 0) {
                        out += run until cp
                        run = -1
                    }
                }
                if (run >= 0) out += run..end
            }
            return out
        }

        /** Sorted, merged `[first, last]` pairs for a binary search. */
        private fun merged(ranges: List<IntRange>): IntArray {
            val out = ArrayList<Int>()
            ranges.sortedBy { it.first }.forEach { r ->
                if (out.isNotEmpty() && r.first <= out.last() + 1) {
                    out[out.size - 1] = maxOf(out.last(), r.last)
                } else {
                    out += r.first
                    out += r.last
                }
            }
            return out.toIntArray()
        }

        private fun u16(b: ByteBuffer, at: Int): Int = b.getShort(at).toInt() and 0xFFFF
    }
}
