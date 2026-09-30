package com.piptechnologies.stickermaker.feature.namepack.engine

import android.graphics.Matrix
import android.graphics.Path

/**
 * The app-mark heart from Claude Design (viewBox 24:
 * `M12 21s-7.5-4.7-9.6-9.2C.7 8 3 4.5 6.6 4.5c2 0 3.6 1.1 4.4 2.6.8-1.5 2.4-2.6 4.4-2.6 3.6 0 5.9 3.5 4.2 7.3C19.5 16.3 12 21 12 21z`)
 * in absolute cubic form. Hand-drawn and slightly asymmetric: keep it as it is.
 */
object HeartPath {

    private fun unit(): Path = Path().apply {
        moveTo(12f, 21f)
        cubicTo(12f, 21f, 4.5f, 16.3f, 2.4f, 11.8f)
        cubicTo(0.7f, 8f, 3f, 4.5f, 6.6f, 4.5f)
        cubicTo(8.6f, 4.5f, 10.2f, 5.6f, 11f, 7.1f)
        cubicTo(11.8f, 5.6f, 13.4f, 4.5f, 15.4f, 4.5f)
        cubicTo(19f, 4.5f, 21.3f, 8f, 19.6f, 11.8f)
        cubicTo(19.5f, 16.3f, 12f, 21f, 12f, 21f)
        close()
    }

    /** The heart's 24-unit box scaled to [size] px with its top-left at ([left], [top]). */
    fun inBox(left: Float, top: Float, size: Float): Path = unit().apply {
        transform(Matrix().apply {
            setScale(size / 24f, size / 24f)
            postTranslate(left, top)
        })
    }
}
