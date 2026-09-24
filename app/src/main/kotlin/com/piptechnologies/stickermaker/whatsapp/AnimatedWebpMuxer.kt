package com.piptechnologies.stickermaker.whatsapp

import java.io.ByteArrayOutputStream

/**
 * Pure-Kotlin animated WebP writer for the sticker create flow.
 *
 * Each input frame is a complete static WebP file (exactly what `Bitmap.compress(WEBP*)`
 * produces): simple "VP8 "/"VP8L", or extended "VP8X" with an optional ALPH chunk. The muxer
 * extracts each frame's ALPH (if any) plus VP8/VP8L payload and repacks them as
 *
 *   RIFF/WEBP -> VP8X (ANIMATION|ALPHA, canvas from the first frame)
 *             -> ANIM (background 0x00000000, loop count)
 *             -> one ANMF per frame (x=0, y=0, frame dims, duration, no-blend, dispose-none)
 *
 * with little-endian sizes and even-size chunk padding throughout.
 *
 * No android imports; unit-testable on the plain JVM.
 */
object AnimatedWebpMuxer {

    private const val RIFF_HEADER_SIZE = 12
    private const val CHUNK_HEADER_SIZE = 8

    private const val VP8X_FLAG_ANIMATION = 0x02
    private const val VP8X_FLAG_ALPHA = 0x10

    /** ANMF flags byte: bit 1 = blending (1 = do not blend), bit 0 = disposal (0 = none). */
    private const val ANMF_FLAGS_NO_BLEND_DISPOSE_NONE = 0x02

    private const val ANIM_BACKGROUND_COLOR = 0x00000000

    /**
     * Packs [frames] into one animated WebP.
     *
     * @param frames complete static WebP files, one per animation frame.
     * @param durationsMs display duration of each frame, same size as [frames].
     * @param loopCount 0 means loop forever (WhatsApp stickers loop).
     * @throws IllegalArgumentException on empty/mismatched input or a malformed frame.
     */
    fun mux(frames: List<ByteArray>, durationsMs: List<Int>, loopCount: Int = 0): ByteArray {
        require(frames.isNotEmpty()) { "At least one frame is required" }
        require(frames.size == durationsMs.size) {
            "frames (${frames.size}) and durationsMs (${durationsMs.size}) must have the same size"
        }
        require(loopCount in 0..0xFFFF) { "loopCount must fit in 16 bits, was $loopCount" }
        durationsMs.forEach { durationMs ->
            require(durationMs in 0..0xFFFFFF) { "Frame duration must fit in 24 bits, was $durationMs" }
        }

        val payloads = frames.map { FramePayload.from(it) }
        val canvasWidth = payloads.first().width
        val canvasHeight = payloads.first().height

        val chunks = ByteArrayOutputStream()
        chunks.writeVp8x(canvasWidth, canvasHeight)
        chunks.writeAnim(loopCount)
        payloads.forEachIndexed { index, frame -> chunks.writeAnmf(frame, durationsMs[index]) }
        val chunkBytes = chunks.toByteArray()

        val out = ByteArrayOutputStream(RIFF_HEADER_SIZE + chunkBytes.size)
        out.writeFourCc("RIFF")
        // RIFF size counts "WEBP" plus every chunk, headers and pad bytes included.
        out.writeLe32(4 + chunkBytes.size)
        out.writeFourCc("WEBP")
        out.write(chunkBytes, 0, chunkBytes.size)
        return out.toByteArray()
    }

    private fun ByteArrayOutputStream.writeVp8x(canvasWidth: Int, canvasHeight: Int) {
        val payload = ByteArrayOutputStream(10)
        payload.write(VP8X_FLAG_ANIMATION or VP8X_FLAG_ALPHA)
        payload.writeLe24(0) // reserved
        payload.writeLe24(canvasWidth - 1)
        payload.writeLe24(canvasHeight - 1)
        writeChunk("VP8X", payload.toByteArray())
    }

    private fun ByteArrayOutputStream.writeAnim(loopCount: Int) {
        val payload = ByteArrayOutputStream(6)
        payload.writeLe32(ANIM_BACKGROUND_COLOR)
        payload.writeLe16(loopCount)
        writeChunk("ANIM", payload.toByteArray())
    }

    private fun ByteArrayOutputStream.writeAnmf(frame: FramePayload, durationMs: Int) {
        val payload = ByteArrayOutputStream()
        payload.writeLe24(0) // frame X / 2
        payload.writeLe24(0) // frame Y / 2
        payload.writeLe24(frame.width - 1)
        payload.writeLe24(frame.height - 1)
        payload.writeLe24(durationMs)
        payload.write(ANMF_FLAGS_NO_BLEND_DISPOSE_NONE)
        frame.alphPayload?.let { payload.writeChunk("ALPH", it) }
        payload.writeChunk(frame.imageFourCc, frame.imagePayload)
        writeChunk("ANMF", payload.toByteArray())
    }

    /** The reusable pieces of one input frame. */
    private class FramePayload(
        val width: Int,
        val height: Int,
        val alphPayload: ByteArray?,
        val imageFourCc: String,
        val imagePayload: ByteArray,
    ) {
        companion object {

            private fun ByteArray.le32(offset: Int): Int =
                (this[offset].toInt() and 0xFF) or
                    ((this[offset + 1].toInt() and 0xFF) shl 8) or
                    ((this[offset + 2].toInt() and 0xFF) shl 16) or
                    ((this[offset + 3].toInt() and 0xFF) shl 24)

            fun from(webp: ByteArray): FramePayload {
                val info = WebpInfo.parse(webp)
                require(!info.isAnimated) { "mux() takes static WebP frames, got an animated one" }
                var alph: ByteArray? = null
                var foundFourCc: String? = null
                var foundImage: ByteArray? = null
                var offset = RIFF_HEADER_SIZE
                while (offset + CHUNK_HEADER_SIZE <= webp.size) {
                    val fourCc = String(webp, offset, 4, Charsets.US_ASCII)
                    val chunkSize = webp.le32(offset + 4)
                    val payloadStart = offset + CHUNK_HEADER_SIZE
                    require(chunkSize >= 0 && payloadStart.toLong() + chunkSize <= webp.size) {
                        "Truncated WebP chunk: $fourCc"
                    }
                    when (fourCc) {
                        "ALPH" ->
                            if (alph == null) {
                                alph = webp.copyOfRange(payloadStart, payloadStart + chunkSize)
                            }
                        "VP8 ", "VP8L" ->
                            if (foundImage == null) {
                                foundFourCc = fourCc
                                foundImage = webp.copyOfRange(payloadStart, payloadStart + chunkSize)
                            }
                        // VP8X/ICCP/EXIF/XMP carry no pixels; dropped on purpose.
                    }
                    offset = payloadStart + chunkSize + (chunkSize and 1)
                }
                val imageFourCc = foundFourCc
                val imagePayload = foundImage
                require(imageFourCc != null && imagePayload != null) {
                    "No VP8/VP8L image data found in frame"
                }
                return FramePayload(info.width, info.height, alph, imageFourCc, imagePayload)
            }
        }
    }

    // -- little-endian byte writers -----------------------------------------------------------

    private fun ByteArrayOutputStream.writeFourCc(fourCc: String) {
        require(fourCc.length == 4) { "FourCC must be 4 characters: $fourCc" }
        for (ch in fourCc) {
            write(ch.code and 0xFF)
        }
    }

    private fun ByteArrayOutputStream.writeLe16(value: Int) {
        write(value and 0xFF)
        write((value ushr 8) and 0xFF)
    }

    private fun ByteArrayOutputStream.writeLe24(value: Int) {
        write(value and 0xFF)
        write((value ushr 8) and 0xFF)
        write((value ushr 16) and 0xFF)
    }

    private fun ByteArrayOutputStream.writeLe32(value: Int) {
        write(value and 0xFF)
        write((value ushr 8) and 0xFF)
        write((value ushr 16) and 0xFF)
        write((value ushr 24) and 0xFF)
    }

    /** Writes a full chunk: fourCC, little-endian size, payload, plus a pad byte when odd. */
    private fun ByteArrayOutputStream.writeChunk(fourCc: String, payload: ByteArray) {
        writeFourCc(fourCc)
        writeLe32(payload.size)
        write(payload, 0, payload.size)
        if (payload.size % 2 == 1) {
            write(0)
        }
    }
}
