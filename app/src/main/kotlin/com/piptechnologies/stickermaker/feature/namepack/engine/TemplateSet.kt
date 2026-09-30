package com.piptechnologies.stickermaker.feature.namepack.engine

import org.json.JSONObject

/** Where a phrase goes on its template, in the 512 canvas: centre, box, and rotation in degrees, clockwise-positive (as Canvas.rotate applies it). */
data class Zone(val cx: Float, val cy: Float, val w: Float, val h: Float, val rotate: Float)

/**
 * One text-free pose from Claude Design (`assets/templates/<character>.json`): its art,
 * phrase slot, lettering zone, WhatsApp emojis, ink and optional white stroke.
 */
data class TemplateSticker(
    val file: String,
    val slot: Slot,
    val zone: Zone,
    val emojis: List<String>,
    val ink: Int,
    val stroke: Float,
    val strokeColor: Int,
)

/** A character's 12 templates, one per [Slot], in slot order. */
data class TemplateSet(val character: Character, val stickers: List<TemplateSticker>) {

    fun sticker(slot: Slot): TemplateSticker = stickers.first { it.slot == slot }

    companion object {
        const val CANVAS = 512

        fun assetPath(character: Character): String = "templates/${character.id}.json"

        fun artPath(file: String): String = "templates/$file"

        /** Parses Claude Design's JSON and fails loudly when a file breaks the template rules. */
        fun parse(character: Character, json: String): TemplateSet {
            val root = JSONObject(json)
            require(root.optInt("canvas") == CANVAS) { "${character.id}: canvas must be $CANVAS" }
            val array = root.getJSONArray("stickers")
            val stickers = (0 until array.length()).map { index ->
                val o = array.getJSONObject(index)
                val z = o.getJSONObject("zone")
                val emojiArray = o.getJSONArray("emojis")
                val slotKey = o.getString("slot")
                TemplateSticker(
                    file = o.getString("file"),
                    slot = requireNotNull(Slot.byKey(slotKey)) { "${character.id}: unknown slot $slotKey" },
                    zone = Zone(
                        cx = z.getDouble("cx").toFloat(),
                        cy = z.getDouble("cy").toFloat(),
                        w = z.getDouble("w").toFloat(),
                        h = z.getDouble("h").toFloat(),
                        rotate = z.optDouble("rotate", 0.0).toFloat()
                    ),
                    emojis = (0 until emojiArray.length()).map { emojiArray.getString(it) },
                    ink = parseColor(o.getString("ink")),
                    stroke = o.optDouble("stroke", 0.0).toFloat(),
                    strokeColor = parseColor(o.optString("strokeColor", "#FFFFFF"))
                )
            }
            val slots = stickers.map { it.slot }
            require(slots.size == Slot.entries.size && slots.toSet() == Slot.entries.toSet()) {
                "${character.id}: needs each of the 12 slots once, got $slots"
            }
            return TemplateSet(character, Slot.entries.map { slot -> stickers.first { it.slot == slot } })
        }

        /** "#RRGGBB", "RRGGBB" or "#RGB" as an opaque ARGB int. */
        fun parseColor(hex: String): Int {
            val raw = hex.removePrefix("#")
            val digits = if (raw.length == 3) raw.map { "$it$it" }.joinToString("") else raw
            require(digits.length == 6) { "Bad colour $hex" }
            return (0xFF000000L or digits.toLong(16)).toInt()
        }
    }
}
