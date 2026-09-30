package com.piptechnologies.stickermaker.feature.namepack.engine

import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min

/** How a phrase fits its zone: text size (px at 512), one or two lines, and whether it fit above the floor. */
data class Fit(val size: Float, val lines: List<String>, val ok: Boolean) {
    val lineHeight: Float get() = size * LetteringFit.LINE_HEIGHT
}

/**
 * The design's fit (spec, Rendering): start at floor(min(zone.h × 0.82, 128)) and step −2 to 20.
 * At each size one line wins if it fits the zone's width; otherwise, when two lines fit the
 * height, the split on a space whose longer line is narrowest. Never three lines. At the floor:
 * size 20, one line, overflow allowed, [Fit.ok] false.
 */
object LetteringFit {

    const val MAX_SIZE = 128f
    const val MIN_SIZE = 20f
    const val STEP = 2f
    const val HEIGHT_RATIO = 0.82f
    const val LINE_HEIGHT = 1.08f

    /** [measure] returns the advance width of a line at a text size. */
    fun fit(text: String, zoneW: Float, zoneH: Float, measure: (line: String, size: Float) -> Float): Fit {
        val spaces = text.indices.filter { text[it] == ' ' }

        /** One line if it fits, else the two-line split whose longer line is narrowest; null if neither fits. */
        fun at(size: Float): Fit? {
            if (measure(text, size) <= zoneW) return Fit(size, listOf(text), true)
            if (spaces.isEmpty() || 2 * size * LINE_HEIGHT > zoneH) return null
            var best: List<String>? = null
            var bestWidth = Float.MAX_VALUE
            for (i in spaces) {
                val first = text.substring(0, i).trimEnd()
                val second = text.substring(i + 1).trimStart()
                if (first.isEmpty() || second.isEmpty()) continue
                val width = max(measure(first, size), measure(second, size))
                if (width <= zoneW && width < bestWidth) {
                    bestWidth = width
                    best = listOf(first, second)
                }
            }
            return best?.let { Fit(size, it, true) }
        }

        val start = floor(min(zoneH * HEIGHT_RATIO, MAX_SIZE))
        var size = start
        while (size > MIN_SIZE) {
            at(size)?.let { return it }
            size -= STEP
        }
        // From an odd start the steps go 21 → 19, past the floor, so the floor itself is always tried.
        if (start >= MIN_SIZE) at(MIN_SIZE)?.let { return it }
        return Fit(MIN_SIZE, listOf(text), false)
    }
}
