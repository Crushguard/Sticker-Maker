package com.piptechnologies.stickermaker.feature.namepack

import com.piptechnologies.stickermaker.feature.namepack.engine.Character
import com.piptechnologies.stickermaker.feature.namepack.engine.Slot
import com.piptechnologies.stickermaker.feature.namepack.engine.TemplateSet
import com.piptechnologies.stickermaker.feature.namepack.engine.Zone
import com.piptechnologies.stickermaker.whatsapp.WebpInfo
import java.io.File
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** org.json is Android's, so this runs under Robolectric. Files are read from the source tree. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
class TemplateSetTest {

    private val dir = File("src/main/assets/templates")

    @Test
    fun everyCharacterHasTwelveValidTemplates() {
        Character.entries.forEach { character ->
            val set = TemplateSet.parse(character, File(dir, "${character.id}.json").readText())
            assertEquals(Slot.entries, set.stickers.map { it.slot })
            // The be_mine sign board tilts clockwise, so its lettering must too (Claude Design's files had the sign flipped).
            assertTrue("${character.id} be_mine tilts with its board", set.sticker(Slot.BE_MINE).zone.rotate > 0f)
            set.stickers.forEach { s ->
                assertTrue("${s.file}: 1–3 emojis", s.emojis.size in 1..3)
                val (left, top, right, bottom) = bounds(s.zone)
                assertTrue("${s.file}: zone inside the canvas", left >= 0 && top >= 0 && right <= 512 && bottom <= 512)
                assertTrue("${s.file}: zone big enough to letter", s.zone.w >= 40f && s.zone.h >= 30f)
                val info = WebpInfo.parse(File(dir, s.file).readBytes())
                assertEquals(s.file, 512, info.width)
                assertEquals(s.file, 512, info.height)
                assertFalse(s.file, info.isAnimated)
                assertTrue(s.file, info.hasAlpha)
            }
        }
    }

    @Test
    fun theWaitingStillIsBundled() {
        val info = WebpInfo.parse(File(dir, "mango-wait.webp").readBytes())
        assertEquals(512, info.width)
        assertFalse(info.isAnimated)
    }

    @Test
    fun aBrokenFileFailsLoudly() {
        assertThrows(IllegalArgumentException::class.java) {
            TemplateSet.parse(Character.MANGO, """{"canvas":512,"stickers":[]}""")
        }
        assertThrows(IllegalArgumentException::class.java) {
            TemplateSet.parse(Character.MANGO, """{"canvas":256,"stickers":[]}""")
        }
    }

    @Test
    fun parsesColours() {
        assertEquals(0xFFC23359.toInt(), TemplateSet.parseColor("#C23359"))
        assertEquals(0xFFFFFFFF.toInt(), TemplateSet.parseColor("#fff"))
        assertEquals(0xFF3B2114.toInt(), TemplateSet.parseColor("3B2114"))
    }

    /** The axis-aligned box of the rotated zone. */
    private fun bounds(z: Zone): List<Float> {
        val a = Math.toRadians(z.rotate.toDouble())
        val hw = (abs(z.w / 2 * cos(a)) + abs(z.h / 2 * sin(a))).toFloat()
        val hh = (abs(z.w / 2 * sin(a)) + abs(z.h / 2 * cos(a))).toFloat()
        return listOf(z.cx - hw, z.cy - hh, z.cx + hw, z.cy + hh)
    }
}
