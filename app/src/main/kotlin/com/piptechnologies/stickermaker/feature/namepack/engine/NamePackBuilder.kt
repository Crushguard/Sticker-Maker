package com.piptechnologies.stickermaker.feature.namepack.engine

import android.graphics.Bitmap
import java.util.Locale
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

/** What to letter: normalized names, the phrase language, relation, tone and character. */
data class NamePackRequest(
    val lang: String,
    val rtl: Boolean,
    val you: String,
    val love: String,
    val relation: Relation,
    val tone: Tone,
    val character: Character,
) {
    /** Family relations are always Sweet. */
    val effectiveTone: Tone get() = if (relation.family) Tone.SWEET else tone

    /** WhatsApp identity: names and language only, so tone and re-casts re-letter the same pack. */
    val packId: String get() = NamePackId.of(lang, you, love)

    /** What the pixels depend on (spec, Cache): everything except the relation beyond its family flag. */
    val contentKey: String
        get() = listOf(character.id, effectiveTone.id, relation.family, lang, you, love).joinToString("\u0000")

    fun cacheKey(slot: Slot): String = contentKey + "\u0000" + slot.key
}

/** One lettered sticker: [preview] (320 px) for the grid, [webp] for WhatsApp. */
class LetteredSticker(
    val slot: Slot,
    val text: String,
    val preview: Bitmap,
    val webp: ByteArray,
    val emojis: List<String>,
)

class TrayImage(val bitmap: Bitmap, val png: ByteArray)

/**
 * Letters name packs: 12 stickers three at a time on [Dispatchers.Default], cached per content,
 * so tone and character switches back and forth are instant.
 */
class NamePackBuilder(private val assets: NamePackAssets) {

    private val lettering = Lettering(assets::typeface)
    private val composer = StickerComposer(lettering)
    private val trays = TrayRenderer(assets::typeface)
    private val cache = object : LinkedHashMap<String, LetteredSticker>(64, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, LetteredSticker>) = size > CACHE_SIZE
    }

    fun text(request: NamePackRequest, slot: Slot): String =
        assets.phrases().text(request.lang, request.effectiveTone, request.relation, slot, request.you, request.love)

    /** A full-size sticker for the live preview: [text] lettered on [slot]'s template. Not cached. */
    fun preview(request: NamePackRequest, slot: Slot, text: String): Bitmap {
        val sticker = assets.templates(request.character).sticker(slot)
        val art = assets.art(sticker.file)
        return try {
            composer.compose(art, sticker, text, request.rtl)
        } finally {
            art.recycle()
        }
    }

    /** The 12 stickers in [Slot] order; [onProgress] receives 1..12 as each finishes (any thread). */
    suspend fun build(request: NamePackRequest, onProgress: (done: Int) -> Unit = {}): List<LetteredSticker> =
        coroutineScope {
            val set = assets.templates(request.character)
            val done = AtomicInteger(0)
            val gate = Semaphore(PARALLELISM)
            Slot.entries.map { slot ->
                async(Dispatchers.Default) {
                    gate.withPermit {
                        val key = request.cacheKey(slot)
                        val sticker = synchronized(cache) { cache[key] }
                            ?: render(request, set.sticker(slot)).also { synchronized(cache) { cache[key] = it } }
                        onProgress(done.incrementAndGet())
                        sticker
                    }
                }
            }.awaitAll()
        }

    /** The 96 px tray: the heart with your initial, then theirs. */
    fun tray(request: NamePackRequest): TrayImage {
        val locale = Locale.forLanguageTag(request.lang)
        val bitmap = trays.render(NameInput.initial(request.you, locale) + NameInput.initial(request.love, locale))
        return TrayImage(bitmap, TrayRenderer.encode(bitmap))
    }

    private fun render(request: NamePackRequest, sticker: TemplateSticker): LetteredSticker {
        val text = text(request, sticker.slot)
        val art = assets.art(sticker.file)
        val full = try {
            composer.compose(art, sticker, text, request.rtl)
        } finally {
            art.recycle()
        }
        val webp = StickerComposer.encode(full)
        val preview = Bitmap.createScaledBitmap(full, PREVIEW_PX, PREVIEW_PX, true)
        if (preview !== full) full.recycle()
        return LetteredSticker(sticker.slot, text, preview, webp, sticker.emojis)
    }

    companion object {
        const val PREVIEW_PX = 320
        private const val PARALLELISM = 3
        private const val CACHE_SIZE = 60
    }
}
