package com.piptechnologies.stickermaker.feature.create.decor

import android.graphics.Bitmap
import com.piptechnologies.stickermaker.feature.create.CreateSpec
import com.piptechnologies.stickermaker.feature.create.StickerRenderer
import com.piptechnologies.stickermaker.whatsapp.AnimatedWebpMuxer
import kotlin.math.roundToInt

/**
 * Encodes decorated stickers for WhatsApp (spec §5, §7), one sticker per call. Every frame is its scene
 * as [SceneRenderer] draws it on the live canvas, fitted into the 460 export square:
 * - a still in a static pack is one WebP of at most 100 KB;
 * - a motion preset, a clip and a still in an animated pack are looping animated WebPs of at most
 *   500 KB, every frame shown for 8 ms or more;
 * - the tray is a 96 px PNG of at most 50 KB.
 *
 * Quality steps down, then frames go, until the file fits; when nothing fits, the smallest attempt comes
 * back and the pack validator has the last word. A call recycles the bitmaps it makes and never the
 * scene's own, which the live canvas may be drawing. Bitmap work: call it off the main thread.
 */
class StickerExporter(private val renderer: SceneRenderer) {

    /** A still in a static pack: quality steps down from 95 until the WebP fits 100 KB. */
    fun staticSticker(scene: SceneRenderer.Scene): ByteArray =
        exportFrame(scene).recycleAfter { StickerRenderer.encodeStaticSticker(it) }

    /**
     * A sticker with motion [preset]: frame i is the scene at rest under the preset's transform at
     * i / frames, with its particles, so frame 0 is the rest pose; each frame lasts its share of the loop
     * ([MotionMath.frameDurations]). Quality steps down from 60 to 30; when that misses 500 KB, every
     * other frame goes ([everyOtherFrame]: the loop keeps its length) and the qualities run again, while
     * 4 frames or more are left (16, 8, 4; 12, 6). A preset without motion (`none`) exports as
     * [stillInAnimatedPack].
     */
    fun presetSticker(scene: SceneRenderer.Scene, preset: MotionPreset): ByteArray {
        if (preset.isNone || preset.frames < 2) return stillInAnimatedPack(scene)
        val frames = ArrayList<Bitmap>(preset.frames)
        try {
            renderer.renderStill(scene).recycleAfter { rest ->
                repeat(preset.frames) { i ->
                    frames += renderer.renderFrame(rest, preset, i).recycleAfter(StickerRenderer::renderExportCanvas)
                }
            }
            val takes = generateSequence(Take(frames, MotionMath.frameDurations(preset))) { take ->
                if (take.frames.size / 2 >= MIN_PRESET_FRAMES) take.everyOther() else null
            }
            return firstFit(attempts(takes, PRESET_QUALITIES))
        } finally {
            frames.forEach(Bitmap::recycle)
        }
    }

    /**
     * A still in an animated pack, where WhatsApp wants every sticker to animate: two identical frames of
     * 500 ms, quality stepping down from 80 to 30.
     */
    fun stillInAnimatedPack(scene: SceneRenderer.Scene): ByteArray = exportFrame(scene).recycleAfter { export ->
        firstFit(
            STILL_QUALITIES.asSequence().map { quality ->
                val frame = StickerRenderer.encodeWebp(export, quality)
                AnimatedWebpMuxer.mux(listOf(frame, frame), listOf(STILL_FRAME_MS, STILL_FRAME_MS))
            }
        )
    }

    /**
     * A clip: [frames] are the scenes of its decoded frames, in order, each drawn at rest and shown for an
     * even share of [totalDurationMs] (40 ms or more). All of them are tried, then 8, then 6 picked evenly,
     * each count at qualities 80 to 30. A single frame exports as [stillInAnimatedPack].
     */
    fun clipSticker(frames: List<SceneRenderer.Scene>, totalDurationMs: Long): ByteArray {
        require(frames.isNotEmpty()) { "a clip needs a frame" }
        if (frames.size == 1) return stillInAnimatedPack(frames.single())
        val exports = ArrayList<Bitmap>(frames.size)
        try {
            frames.forEach { exports += exportFrame(it) }
            val counts = (listOf(exports.size) + CLIP_FEWER_FRAMES).distinct().filter { it in 2..exports.size }
            val takes = counts.asSequence().map { count ->
                val shown = (totalDurationMs / count).coerceAtLeast(MIN_CLIP_FRAME_MS).toInt()
                Take(pickEvenly(exports, count), List(count) { shown })
            }
            return firstFit(attempts(takes, CLIP_QUALITIES))
        } finally {
            exports.forEach(Bitmap::recycle)
        }
    }

    /** The tray icon: the scene at rest, whole (not fitted into 460), as a 96 px PNG; smaller if 96 misses 50 KB. */
    fun tray(scene: SceneRenderer.Scene): ByteArray =
        renderer.renderStill(scene).recycleAfter(StickerRenderer::encodeTrayPng)

    /** Frames to encode together, each with its display time. */
    private class Take(val frames: List<Bitmap>, val durationsMs: List<Int>) {
        /** Every other frame, the loop keeping its length: see [everyOtherFrame]. */
        fun everyOther(): Take = everyOtherFrame(frames, durationsMs).let { (kept, times) -> Take(kept, times) }
    }

    /** [scene] at rest, fitted into the 460 export square, in a new 512 bitmap. */
    private fun exportFrame(scene: SceneRenderer.Scene): Bitmap =
        renderer.renderStill(scene).recycleAfter(StickerRenderer::renderExportCanvas)

    /** Each take at each of [qualities], in that order, as animated WebPs, each encoded only once asked for. */
    private fun attempts(takes: Sequence<Take>, qualities: IntArray): Sequence<ByteArray> =
        takes.flatMap { take ->
            qualities.asSequence().map { quality ->
                AnimatedWebpMuxer.mux(take.frames.map { StickerRenderer.encodeWebp(it, quality) }, take.durationsMs)
            }
        }

    /** The first of [attempts] that fits 500 KB (the later ones are never made), else the smallest of them. */
    private fun firstFit(attempts: Sequence<ByteArray>): ByteArray {
        var smallest: ByteArray? = null
        for (bytes in attempts) {
            if (bytes.size <= CreateSpec.ANIMATED_LIMIT_BYTES) return bytes
            if (smallest == null || bytes.size < smallest.size) smallest = bytes
        }
        return checkNotNull(smallest) { "nothing to encode" }
    }

    internal companion object {
        /** Preset qualities, from the first try down (spec §7). */
        private val PRESET_QUALITIES = intArrayOf(60, 50, 42, 35, 30)

        /** Qualities of a still in an animated pack. */
        private val STILL_QUALITIES = intArrayOf(80, 65, 50, 40, 30)

        /** Qualities of a clip, at each frame count. */
        private val CLIP_QUALITIES = intArrayOf(80, 65, 50, 40, 30)

        /** The frame counts a clip falls back to when all its frames miss 500 KB. */
        private val CLIP_FEWER_FRAMES = listOf(8, 6)

        /** Halving a preset stops before it would leave fewer frames than this. */
        private const val MIN_PRESET_FRAMES = 4

        /** Each of the two frames of a still in an animated pack. */
        private const val STILL_FRAME_MS = 500

        /** The shortest a clip frame is shown. */
        private const val MIN_CLIP_FRAME_MS = 40L

        /**
         * Every other frame: frames 0, 2, 4, … stay (frame 0 is the rest pose), each shown for its own time
         * plus the next frame's; a last, unpaired frame goes and its time joins the final pair. The loop
         * keeps its length and no frame gets shorter. [frames] and [durationsMs] match, 2 or more.
         */
        internal fun <T> everyOtherFrame(frames: List<T>, durationsMs: List<Int>): Pair<List<T>, List<Int>> {
            require(frames.size >= 2 && frames.size == durationsMs.size) {
                "${frames.size} frames, ${durationsMs.size} durations"
            }
            val pairs = frames.size / 2
            val times = MutableList(pairs) { durationsMs[2 * it] + durationsMs[2 * it + 1] }
            if (frames.size % 2 == 1) times[pairs - 1] += durationsMs.last()
            return List(pairs) { frames[2 * it] } to times
        }

        /** [count] items spread evenly over [list], its first and last included; all of it when it holds no more. */
        private fun <T> pickEvenly(list: List<T>, count: Int): List<T> {
            if (count >= list.size) return list
            return List(count) { i -> list[(i.toFloat() * (list.size - 1) / (count - 1)).roundToInt()] }
        }
    }
}

/** Runs [block] on this bitmap, then recycles it: only for a bitmap the export made. */
private inline fun <R> Bitmap.recycleAfter(block: (Bitmap) -> R): R {
    try {
        return block(this)
    } finally {
        recycle()
    }
}
