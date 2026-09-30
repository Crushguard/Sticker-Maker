package com.piptechnologies.stickermaker.feature.namepack

import java.io.File
import java.nio.ByteBuffer

/**
 * The code points a TrueType font maps to a real glyph, read from its `cmap` table (the
 * format 12 subtable when present, else format 4). Big-endian, as the format is.
 */
internal class CmapCoverage private constructor(private val glyphOf: (Int) -> Int) {

    fun covers(codePoint: Int): Boolean = glyphOf(codePoint) != 0

    companion object {
        fun of(file: File): CmapCoverage {
            val b = ByteBuffer.wrap(file.readBytes())
            val cmap = (0 until u16(b, 4)).map { 12 + it * 16 }
                .firstOrNull { tag(b, it) == "cmap" }
                ?.let { b.getInt(it + 8) }
                ?: error("${file.name} has no cmap table")
            val unicode = (0 until u16(b, cmap + 2)).map { cmap + 4 + it * 8 }
                .filter { record ->
                    val platform = u16(b, record)
                    val encoding = u16(b, record + 2)
                    platform == 0 || (platform == 3 && (encoding == 1 || encoding == 10))
                }
                .map { record -> cmap + b.getInt(record + 4) }
            unicode.firstOrNull { u16(b, it) == 12 }?.let { return CmapCoverage(format12(b, it)) }
            unicode.firstOrNull { u16(b, it) == 4 }?.let { return CmapCoverage(format4(b, it)) }
            error("${file.name} has no Unicode cmap subtable")
        }

        /** Groups of (first code point, last code point, first glyph). */
        private fun format12(b: ByteBuffer, at: Int): (Int) -> Int {
            val groups = (0 until b.getInt(at + 12)).map { at + 16 + it * 12 }
            return { cp ->
                groups.firstOrNull { g -> cp >= b.getInt(g) && cp <= b.getInt(g + 4) }
                    ?.let { g -> b.getInt(g + 8) + (cp - b.getInt(g)) } ?: 0
            }
        }

        /** Segments of the Basic Multilingual Plane: end codes, start codes, deltas, range offsets. */
        private fun format4(b: ByteBuffer, at: Int): (Int) -> Int {
            val segments = u16(b, at + 6) / 2
            val ends = at + 14
            val starts = ends + segments * 2 + 2
            val deltas = starts + segments * 2
            val rangeOffsets = deltas + segments * 2
            return glyph@{ cp ->
                if (cp > 0xFFFF) return@glyph 0
                val i = (0 until segments).firstOrNull { u16(b, ends + it * 2) >= cp } ?: return@glyph 0
                val start = u16(b, starts + i * 2)
                if (cp < start) return@glyph 0
                val delta = b.getShort(deltas + i * 2).toInt()
                val rangeOffsetAt = rangeOffsets + i * 2
                val rangeOffset = u16(b, rangeOffsetAt)
                if (rangeOffset == 0) return@glyph (cp + delta) and 0xFFFF
                val g = u16(b, rangeOffsetAt + rangeOffset + (cp - start) * 2)
                if (g == 0) 0 else (g + delta) and 0xFFFF
            }
        }

        private fun u16(b: ByteBuffer, at: Int): Int = b.getShort(at).toInt() and 0xFFFF

        private fun tag(b: ByteBuffer, at: Int): String = String(ByteArray(4) { b.get(at + it) }, Charsets.US_ASCII)
    }
}
