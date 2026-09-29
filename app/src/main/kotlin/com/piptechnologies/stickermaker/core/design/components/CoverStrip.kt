package com.piptechnologies.stickermaker.core.design.components

import android.graphics.drawable.BitmapDrawable
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import coil.imageLoader
import coil.request.ImageRequest
import kotlin.math.roundToInt

/**
 * A pack card's cover strip (its cover stickers side by side, one image per card instead of six), loaded
 * once through Coil's caches: the small strip up to 2x screens, the large one above. Null while loading or
 * when there is none.
 */
@Composable
fun rememberCoverStrip(smallUrl: String?, largeUrl: String?): ImageBitmap? {
    val density = LocalDensity.current.density
    val url = if (density <= SMALL_STRIP_MAX_DENSITY) smallUrl ?: largeUrl else largeUrl ?: smallUrl
    val context = LocalContext.current
    var strip by remember(url) { mutableStateOf<ImageBitmap?>(null) }
    LaunchedEffect(url) {
        if (url == null) return@LaunchedEffect
        val request = ImageRequest.Builder(context).data(url).allowHardware(false).build()
        val drawable = context.imageLoader.execute(request).drawable
        strip = (drawable as? BitmapDrawable)?.bitmap?.asImageBitmap()
    }
    return strip
}

/** Tile [index] of a [tiles]-wide cover strip, scaled to fill this composable. */
@Composable
fun CoverTile(strip: ImageBitmap, index: Int, tiles: Int, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val tileWidth = strip.width / tiles.coerceAtLeast(1)
        drawImage(
            image = strip,
            srcOffset = IntOffset(index * tileWidth, 0),
            srcSize = IntSize(tileWidth, strip.height),
            dstSize = IntSize(size.width.roundToInt(), size.height.roundToInt()),
            filterQuality = FilterQuality.High,
        )
    }
}

/** A catalog pack's cover strips and how many tiles they hold. */
data class PackCover(val smallUrl: String?, val largeUrl: String?, val tiles: Int)

private const val SMALL_STRIP_MAX_DENSITY = 2f
