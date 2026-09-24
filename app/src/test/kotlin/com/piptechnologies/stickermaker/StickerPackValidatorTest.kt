package com.piptechnologies.stickermaker

import com.piptechnologies.stickermaker.whatsapp.StickerPackValidator
import com.piptechnologies.stickermaker.whatsapp.ValidatablePack
import com.piptechnologies.stickermaker.whatsapp.ValidatableSticker
import com.piptechnologies.stickermaker.whatsapp.WebpInfo
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

/**
 * The CI gate for the seeded catalog: every pack under packs/ must satisfy every WhatsApp
 * sticker rule, checked with the same ported validator the app runs before serving a pack.
 *
 * Robolectric only supplies android's org.json for parsing the pack.json fixtures; a plain
 * [android.app.Application] is configured so the Hilt application class stays out of the test.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
class StickerPackValidatorTest {

    @Test
    fun allFourteenSeededPacksArePresent() {
        assertEquals(
            "expected the 14 seeded packs under ${PackFixtures.packsDir}",
            14,
            PackFixtures.packDirs.size,
        )
    }

    @Test
    fun everySeededPackPassesEveryWhatsAppCheck() {
        for (dir in PackFixtures.packDirs) {
            val pack = loadPack(dir)
            val id = pack.identifier

            // Identity strings.
            assertTrue("$id: identifier is empty", pack.identifier.isNotEmpty())
            assertTrue("$id: name is empty", pack.name.isNotEmpty())
            assertTrue("$id: publisher is empty", pack.publisher.isNotEmpty())

            // Sticker count.
            assertTrue(
                "$id: sticker count ${pack.stickers.size} outside 3..30",
                pack.stickers.size in 3..30,
            )

            // Tray icon: exists, PNG, 96x96, at most 50 KB.
            val trayFile = File(dir, pack.trayImageFile)
            assertTrue("$id: tray image ${trayFile.path} missing", trayFile.isFile)
            assertTrue(
                "$id: tray image is ${pack.trayBytes.size} bytes, over 50 KB",
                pack.trayBytes.size <= 50 * 1024,
            )
            assertTrue("$id: tray image is not a PNG", StickerPackValidator.isPng(pack.trayBytes))
            val (trayWidth, trayHeight) = StickerPackValidator.pngDimensions(pack.trayBytes)
            assertEquals("$id: tray width", 96, trayWidth)
            assertEquals("$id: tray height", 96, trayHeight)

            for (sticker in pack.stickers) {
                val name = "$id/${sticker.fileName}"
                assertTrue("$name: file missing", File(dir, sticker.fileName).isFile)
                assertTrue(
                    "$name: emoji count ${sticker.emojis.size} outside 1..3",
                    sticker.emojis.size in 1..3,
                )

                val info = WebpInfo.parse(sticker.bytes)
                assertEquals("$name: width", 512, info.width)
                assertEquals("$name: height", 512, info.height)
                assertEquals(
                    "$name: animated_sticker_pack=${pack.animatedStickerPack} but file isAnimated=${info.isAnimated}",
                    pack.animatedStickerPack,
                    info.isAnimated,
                )

                val sizeLimit = if (pack.animatedStickerPack) 500 * 1024 else 100 * 1024
                assertTrue(
                    "$name: ${sticker.bytes.size} bytes over the ${sizeLimit / 1024} KB limit",
                    sticker.bytes.size <= sizeLimit,
                )

                if (pack.animatedStickerPack) {
                    assertTrue("$name: needs more than one frame", info.frameCount > 1)
                    assertTrue(
                        "$name: min frame duration ${info.minFrameDurationMs} ms under 8 ms",
                        info.minFrameDurationMs >= 8,
                    )
                    assertTrue(
                        "$name: total duration ${info.totalDurationMs} ms over 10 s",
                        info.totalDurationMs <= 10_000,
                    )
                }
            }

            // And the full ported validator: identifier character rules, URL/email formats,
            // tray dimension bounds, per-file limits - everything the sample checks.
            StickerPackValidator.verifyStickerPackValidity(pack)
        }
    }

    private fun loadPack(dir: File): ValidatablePack {
        val json = JSONObject(File(dir, "pack.json").readText())
        val stickers = mutableListOf<ValidatableSticker>()
        val stickersJson = json.getJSONArray("stickers")
        for (i in 0 until stickersJson.length()) {
            val stickerJson = stickersJson.getJSONObject(i)
            val emojis = mutableListOf<String>()
            val emojisJson = stickerJson.getJSONArray("emojis")
            for (j in 0 until emojisJson.length()) {
                emojis.add(emojisJson.getString(j))
            }
            val fileName = stickerJson.getString("image_file")
            stickers.add(
                ValidatableSticker(
                    fileName = fileName,
                    bytes = File(dir, fileName).readBytes(),
                    emojis = emojis,
                )
            )
        }
        val trayImageFile = json.getString("tray_image_file")
        return ValidatablePack(
            identifier = json.getString("identifier"),
            name = json.getString("name"),
            publisher = json.getString("publisher"),
            trayImageFile = trayImageFile,
            trayBytes = File(dir, trayImageFile).readBytes(),
            animatedStickerPack = json.getBoolean("animated_sticker_pack"),
            stickers = stickers,
            publisherEmail = json.optString("publisher_email"),
            publisherWebsite = json.optString("publisher_website"),
            privacyPolicyWebsite = json.optString("privacy_policy_website"),
            licenseAgreementWebsite = json.optString("license_agreement_website"),
        )
    }
}
