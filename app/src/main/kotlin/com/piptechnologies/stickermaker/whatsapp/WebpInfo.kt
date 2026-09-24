package com.piptechnologies.stickermaker.whatsapp

/**
 * Pure-Kotlin WebP container header parser. It replaces the Fresco `WebPImage` decode that the
 * WhatsApp/stickers sample validator uses, so the same checks can run on the plain JVM (unit
 * tests) and on device without an image pipeline dependency.
 *
 * Understands the three container layouts of the WebP spec:
 *  - "VP8 " simple lossy: dimensions from the key frame header (14-bit little-endian fields),
 *  - "VP8L" simple lossless: 5-byte bitstream header (14-bit fields, stored minus one),
 *  - "VP8X" extended: 24-bit little-endian canvas dimensions (stored minus one), feature flags,
 *    and, when the ANIM flag is set, one ANMF chunk per animation frame with a 24-bit duration.
 *
 * No android imports; safe to use from JVM unit tests.
 *
 * @property totalDurationMs sum of all ANMF frame durations (0 for static images). This field is
 * additive over the sample's needs: the validator ports the sample's `getDuration()` check with it.
 */
data class WebpInfo(
    val width: Int,
    val height: Int,
    val isAnimated: Boolean,
    val frameCount: Int,
    val minFrameDurationMs: Int,
    val hasAlpha: Boolean,
    val totalDurationMs: Int = 0,
) {

    companion object {
        private const val RIFF_HEADER_SIZE = 12
        private const val CHUNK_HEADER_SIZE = 8

        /** VP8X feature flag bits (first payload byte): ...I L E X A R -> alpha 0x10, anim 0x02. */
        private const val VP8X_FLAG_ANIMATION = 0x02
        private const val VP8X_FLAG_ALPHA = 0x10
        private const val VP8L_SIGNATURE = 0x2F

        /**
         * Parses the header of [bytes].
         *
         * @throws IllegalArgumentException when [bytes] is not a well-formed WebP file.
         */
        @JvmStatic
        fun parse(bytes: ByteArray): WebpInfo {
            require(bytes.size >= RIFF_HEADER_SIZE + CHUNK_HEADER_SIZE) {
                "Too short to be a WebP file: ${bytes.size} bytes"
            }
            require(bytes.fourCc(0) == "RIFF" && bytes.fourCc(8) == "WEBP") {
                "Not a WebP file: missing RIFF/WEBP magic"
            }
            return when (val firstChunk = bytes.fourCc(RIFF_HEADER_SIZE)) {
                "VP8 " -> parseSimpleLossy(bytes)
                "VP8L" -> parseSimpleLossless(bytes)
                "VP8X" -> parseExtended(bytes)
                else -> throw IllegalArgumentException("Unsupported first WebP chunk: $firstChunk")
            }
        }

        /** Simple lossy: key frame header sits at the start of the "VP8 " chunk payload. */
        private fun parseSimpleLossy(bytes: ByteArray): WebpInfo {
            // Payload offset 20: 3 bytes frame tag, 3 bytes start code, then 14-bit dimensions.
            require(bytes.size >= 30) { "Truncated VP8 bitstream" }
            require(bytes.u8(23) == 0x9D && bytes.u8(24) == 0x01 && bytes.u8(25) == 0x2A) {
                "Bad VP8 key frame start code"
            }
            val width = bytes.le16(26) and 0x3FFF
            val height = bytes.le16(28) and 0x3FFF
            return WebpInfo(
                width = width,
                height = height,
                isAnimated = false,
                frameCount = 1,
                minFrameDurationMs = 0,
                // A simple lossy file has no alpha plane (that needs the extended format).
                hasAlpha = false,
                totalDurationMs = 0,
            )
        }

        /** Simple lossless: 5-byte VP8L bitstream header, fields are stored minus one. */
        private fun parseSimpleLossless(bytes: ByteArray): WebpInfo {
            require(bytes.size >= 25) { "Truncated VP8L bitstream" }
            require(bytes.u8(20) == VP8L_SIGNATURE) { "Bad VP8L signature" }
            val b1 = bytes.u8(21)
            val b2 = bytes.u8(22)
            val b3 = bytes.u8(23)
            val b4 = bytes.u8(24)
            val width = (((b2 and 0x3F) shl 8) or b1) + 1
            val height = (((b4 and 0x0F) shl 10) or (b3 shl 2) or (b2 ushr 6)) + 1
            val hasAlpha = (b4 ushr 4) and 0x01 == 1
            return WebpInfo(
                width = width,
                height = height,
                isAnimated = false,
                frameCount = 1,
                minFrameDurationMs = 0,
                hasAlpha = hasAlpha,
                totalDurationMs = 0,
            )
        }

        /** Extended: VP8X flags + 24-bit canvas dimensions, then a flat walk over the chunks. */
        private fun parseExtended(bytes: ByteArray): WebpInfo {
            require(bytes.size >= 30) { "Truncated VP8X chunk" }
            val vp8xSize = bytes.le32(16)
            require(vp8xSize >= 10) { "VP8X chunk too small: $vp8xSize bytes" }
            val flags = bytes.u8(20)
            val isAnimated = flags and VP8X_FLAG_ANIMATION != 0
            var hasAlpha = flags and VP8X_FLAG_ALPHA != 0
            val width = bytes.le24(24) + 1
            val height = bytes.le24(27) + 1

            var frameCount = 0
            var minFrameDurationMs = Int.MAX_VALUE
            var totalDurationMs = 0
            // Chunks are padded to even sizes; the pad byte is not counted in the chunk size.
            var offset = RIFF_HEADER_SIZE + CHUNK_HEADER_SIZE + vp8xSize + (vp8xSize and 1)
            while (offset + CHUNK_HEADER_SIZE <= bytes.size) {
                val fourCc = bytes.fourCc(offset)
                val chunkSize = bytes.le32(offset + 4)
                val payloadStart = offset + CHUNK_HEADER_SIZE
                require(chunkSize >= 0 && payloadStart.toLong() + chunkSize <= bytes.size) {
                    "Truncated WebP chunk: $fourCc"
                }
                when (fourCc) {
                    "ANMF" -> {
                        require(chunkSize >= 16) { "ANMF chunk too small: $chunkSize bytes" }
                        frameCount++
                        val durationMs = bytes.le24(payloadStart + 12)
                        minFrameDurationMs = minOf(minFrameDurationMs, durationMs)
                        totalDurationMs += durationMs
                    }
                    "ALPH" -> hasAlpha = true
                }
                offset = payloadStart + chunkSize + (chunkSize and 1)
            }

            return if (isAnimated) {
                WebpInfo(
                    width = width,
                    height = height,
                    isAnimated = true,
                    frameCount = frameCount,
                    minFrameDurationMs = if (frameCount == 0) 0 else minFrameDurationMs,
                    hasAlpha = hasAlpha,
                    totalDurationMs = totalDurationMs,
                )
            } else {
                WebpInfo(
                    width = width,
                    height = height,
                    isAnimated = false,
                    frameCount = 1,
                    minFrameDurationMs = 0,
                    hasAlpha = hasAlpha,
                    totalDurationMs = 0,
                )
            }
        }

        // -- little-endian byte readers -------------------------------------------------------

        private fun ByteArray.u8(offset: Int): Int = this[offset].toInt() and 0xFF

        private fun ByteArray.le16(offset: Int): Int = u8(offset) or (u8(offset + 1) shl 8)

        private fun ByteArray.le24(offset: Int): Int =
            u8(offset) or (u8(offset + 1) shl 8) or (u8(offset + 2) shl 16)

        private fun ByteArray.le32(offset: Int): Int =
            u8(offset) or (u8(offset + 1) shl 8) or (u8(offset + 2) shl 16) or (u8(offset + 3) shl 24)

        private fun ByteArray.fourCc(offset: Int): String {
            require(offset + 4 <= size) { "Truncated WebP file" }
            return String(this, offset, 4, Charsets.US_ASCII)
        }
    }
}
