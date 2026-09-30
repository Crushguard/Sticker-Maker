package com.piptechnologies.stickermaker.feature.namepack.engine

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Typeface
import androidx.core.content.res.ResourcesCompat
import com.piptechnologies.stickermaker.R
import java.util.concurrent.ConcurrentHashMap

/** A character's waiting art, outlined, and whose art it is (not every character has its own yet). */
class WaitArt(val character: Character, val bitmap: Bitmap)

/** Everything the name pack reads from the APK: Claude Design's templates and phrases, and the fonts. */
open class NamePackAssets(private val context: Context) {

    private val sets = ConcurrentHashMap<Character, TemplateSet>()
    private val faces = ConcurrentHashMap<LetteringFont, Typeface>()
    private val phraseBook by lazy { PhraseBook.parse(read(PhraseBook.ASSET_PATH)) }

    fun templates(character: Character): TemplateSet =
        sets.getOrPut(character) { TemplateSet.parse(character, read(TemplateSet.assetPath(character))) }

    fun phrases(): PhraseBook = phraseBook

    /** A template's 512 art, decoded fresh (the caller recycles it). */
    fun art(file: String): Bitmap = decode(TemplateSet.artPath(file))

    open fun typeface(font: LetteringFont): Typeface = faces.getOrPut(font) {
        val res = when (font) {
            LetteringFont.BALOO -> R.font.lettering_baloo2
            LetteringFont.BALOO_BHAIJAAN -> R.font.lettering_baloo_bhaijaan2
            LetteringFont.RUBIK -> R.font.lettering_rubik
            LetteringFont.SYSTEM_BOLD -> null
        }
        res?.let { ResourcesCompat.getFont(context, it) } ?: Typeface.DEFAULT_BOLD
    }

    /** The character's waiting art with a white die-cut outline: `<id>-wait.webp` when bundled, else Mango's. */
    fun waitArt(character: Character): WaitArt {
        val shown = if (exists("templates/${character.id}-wait.webp")) character else FALLBACK_WAIT
        val art = decode("templates/${shown.id}-wait.webp")
        return WaitArt(shown, StickerComposer.withOutline(art, WAIT_OUTLINE_PX).also { art.recycle() })
    }

    /** "Who says it?" tile art: the character's hug pose, scaled to 144 px. */
    fun tileArt(character: Character): Bitmap {
        val art = art("${character.id}-6.webp")
        return Bitmap.createScaledBitmap(art, TILE_PX, TILE_PX, true).also { if (it !== art) art.recycle() }
    }

    private fun exists(path: String): Boolean = runCatching { context.assets.open(path).close() }.isSuccess

    private fun decode(path: String): Bitmap =
        context.assets.open(path).use { BitmapFactory.decodeStream(it) } ?: error("Undecodable $path")

    private fun read(path: String): String = context.assets.open(path).bufferedReader().use { it.readText() }

    companion object {
        private val FALLBACK_WAIT = Character.MANGO
        private const val WAIT_OUTLINE_PX = 7f
        private const val TILE_PX = 144
    }
}
