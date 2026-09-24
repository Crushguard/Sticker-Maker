package com.piptechnologies.stickermaker.feature.create

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.media.MediaMetadataRetriever
import android.net.Uri
import androidx.exifinterface.media.ExifInterface
import java.util.Locale
import kotlin.math.min

/**
 * Media intake for the Create flow: EXIF-honouring photo decode, camera-shot
 * normalisation and video probing/frame extraction. Everything here is
 * synchronous bitmap work — callers run it on a background dispatcher.
 */
object CreateMedia {

    /** Frames + timing decoded from a clip (capped per [CreateSpec]). */
    class VideoFrames(
        val frames: List<Bitmap>,
        val frameDurationMs: Int,
        val totalDurationMs: Int
    )

    private val bitmapPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)

    /**
     * Decodes [uri] into an EXIF-rotated, centre-cropped square of [size] px
     * (ARGB_8888). Returns null when the picture cannot be read.
     */
    fun loadSquareBitmap(context: Context, uri: Uri, size: Int = CreateSpec.CANVAS_SIZE): Bitmap? {
        return try {
            val resolver = context.contentResolver
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
                ?: return null
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

            val orientation = resolver.openInputStream(uri)?.use { stream ->
                try {
                    ExifInterface(stream).getAttributeInt(
                        ExifInterface.TAG_ORIENTATION,
                        ExifInterface.ORIENTATION_NORMAL
                    )
                } catch (ignored: Exception) {
                    ExifInterface.ORIENTATION_NORMAL
                }
            } ?: ExifInterface.ORIENTATION_NORMAL

            var sample = 1
            val minSide = min(bounds.outWidth, bounds.outHeight)
            while (minSide / (sample * 2) >= size) sample *= 2
            val options = BitmapFactory.Options().apply {
                inSampleSize = sample
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }
            val decoded = resolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, options)
            } ?: return null

            val rotated = applyOrientation(decoded, orientation)
            val square = centerCropSquare(rotated, size)
            if (rotated !== decoded) decoded.recycle()
            if (square !== rotated) rotated.recycle()
            square
        } catch (oom: OutOfMemoryError) {
            null
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Centre-crops [bitmap] into a [size] px ARGB square. Always returns a new
     * bitmap unless [bitmap] already matches exactly.
     */
    fun centerCropSquare(bitmap: Bitmap, size: Int): Bitmap {
        if (bitmap.width == size && bitmap.height == size && bitmap.config == Bitmap.Config.ARGB_8888) {
            return bitmap
        }
        val out = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)
        val scale = size.toFloat() / min(bitmap.width, bitmap.height)
        val dx = (size - bitmap.width * scale) / 2f
        val dy = (size - bitmap.height * scale) / 2f
        val matrix = Matrix().apply {
            setScale(scale, scale)
            postTranslate(dx, dy)
        }
        canvas.drawBitmap(bitmap, matrix, bitmapPaint)
        return out
    }

    /** Clip length in ms, or null when the container cannot be read. */
    fun videoDurationMs(context: Context, uri: Uri): Long? {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(context, uri)
            retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull()
        } catch (e: Exception) {
            null
        } finally {
            try {
                retriever.release()
            } catch (ignored: Exception) {
            }
        }
    }

    /** First frame of the clip as a small square preview, or null. */
    fun videoThumb(context: Context, uri: Uri, size: Int = 256): Bitmap? {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(context, uri)
            val frame = retriever.getFrameAtTime(0, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                ?: return null
            val square = centerCropSquare(frame, size)
            if (square !== frame) frame.recycle()
            square
        } catch (e: Exception) {
            null
        } finally {
            try {
                retriever.release()
            } catch (ignored: Exception) {
            }
        }
    }

    /**
     * Decodes up to [maxFrames] evenly spaced square frames from the first
     * [maxDurationMs] of the clip (the retriever applies rotation metadata
     * itself). Returns null when fewer than two frames decode.
     */
    fun decodeVideoFrames(
        context: Context,
        uri: Uri,
        maxFrames: Int = CreateSpec.VIDEO_MAX_FRAMES,
        maxDurationMs: Long = CreateSpec.VIDEO_MAX_DURATION_MS,
        size: Int = CreateSpec.CANVAS_SIZE
    ): VideoFrames? {
        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(context, uri)
            val declared = retriever
                .extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                ?.toLongOrNull()
                ?.takeIf { it > 0 }
                ?: maxDurationMs
            val total = min(declared, maxDurationMs)
            val frames = ArrayList<Bitmap>(maxFrames)
            try {
                for (i in 0 until maxFrames) {
                    // Sample at the centre of each slot so the last frame stays inside the clip.
                    val timeUs = total * 1000L * (2 * i + 1) / (2L * maxFrames)
                    val raw = retriever.getFrameAtTime(timeUs, MediaMetadataRetriever.OPTION_CLOSEST)
                        ?: continue
                    val square = centerCropSquare(raw, size)
                    if (square !== raw) raw.recycle()
                    frames.add(square)
                }
            } catch (oom: OutOfMemoryError) {
                frames.forEach { it.recycle() }
                return null
            }
            if (frames.size < 2) {
                frames.forEach { it.recycle() }
                return null
            }
            val perFrame = (total / frames.size).toInt().coerceAtLeast(40)
            return VideoFrames(frames, perFrame, perFrame * frames.size)
        } catch (e: Exception) {
            return null
        } finally {
            try {
                retriever.release()
            } catch (ignored: Exception) {
            }
        }
    }

    /** "▶ 0:03" — the picker-tile clip length label from the design. */
    fun formatDurationLabel(durationMs: Long): String {
        val totalSeconds = ((durationMs + 500) / 1000).coerceAtLeast(1)
        val minutes = totalSeconds / 60
        val seconds = totalSeconds % 60
        return String.format(Locale.ROOT, "▶ %d:%02d", minutes, seconds)
    }

    private fun applyOrientation(bitmap: Bitmap, orientation: Int): Bitmap {
        val matrix = Matrix()
        when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
            ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
            ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.postScale(-1f, 1f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.postScale(1f, -1f)
            ExifInterface.ORIENTATION_TRANSPOSE -> {
                matrix.postRotate(90f)
                matrix.postScale(-1f, 1f)
            }
            ExifInterface.ORIENTATION_TRANSVERSE -> {
                matrix.postRotate(270f)
                matrix.postScale(-1f, 1f)
            }
            else -> return bitmap
        }
        return try {
            Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
        } catch (oom: OutOfMemoryError) {
            bitmap
        }
    }
}
