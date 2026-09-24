package com.piptechnologies.stickermaker.whatsapp

import java.net.MalformedURLException
import java.net.URL

/**
 * One sticker to validate: the file bytes plus its emoji list.
 * Decoupled from Room and from the ContentProvider so both the app and plain JVM tests can
 * build one from any source (downloaded files, the create flow, the packs/ fixtures).
 */
data class ValidatableSticker(
    val fileName: String,
    val bytes: ByteArray,
    val emojis: List<String>,
    val accessibilityText: String? = null,
)

/** One sticker pack to validate; mirrors the fields the WhatsApp/stickers sample validates. */
data class ValidatablePack(
    val identifier: String,
    val name: String,
    val publisher: String,
    val trayImageFile: String,
    val trayBytes: ByteArray,
    val animatedStickerPack: Boolean,
    val stickers: List<ValidatableSticker>,
    val publisherEmail: String = "",
    val publisherWebsite: String = "",
    val privacyPolicyWebsite: String = "",
    val licenseAgreementWebsite: String = "",
    val androidPlayStoreLink: String = "",
    val iosAppStoreLink: String = "",
)

/**
 * Port of the WhatsApp/stickers Android sample `StickerPackValidator` (revision 06144a1).
 * Every check and constant of the sample is kept; the Fresco `WebPImage` decode is replaced
 * with the pure-Kotlin [WebpInfo] header parser (and a small PNG header read for tray icons,
 * which may be PNG in this app), so validation runs identically on device and on the JVM.
 */
object StickerPackValidator {

    const val EMOJI_MAX_LIMIT = 3
    const val MAX_STATIC_STICKER_A11Y_TEXT_CHAR_LIMIT = 125
    const val MAX_ANIMATED_STICKER_A11Y_TEXT_CHAR_LIMIT = 255

    private const val STATIC_STICKER_FILE_LIMIT_KB = 100
    private const val ANIMATED_STICKER_FILE_LIMIT_KB = 500
    private const val EMOJI_MIN_LIMIT = 1
    private const val IMAGE_HEIGHT = 512
    private const val IMAGE_WIDTH = 512
    private const val STICKER_SIZE_MIN = 3
    private const val STICKER_SIZE_MAX = 30
    private const val CHAR_COUNT_MAX = 128
    private const val KB_IN_BYTES = 1024L
    private const val TRAY_IMAGE_FILE_SIZE_MAX_KB = 50
    private const val TRAY_IMAGE_DIMENSION_MIN = 24
    private const val TRAY_IMAGE_DIMENSION_MAX = 512
    private const val ANIMATED_STICKER_FRAME_DURATION_MIN = 8
    private const val ANIMATED_STICKER_TOTAL_DURATION_MAX = 10 * 1000 // ms
    private const val PLAY_STORE_DOMAIN = "play.google.com"
    private const val APPLE_STORE_DOMAIN = "itunes.apple.com"

    /** Same pattern as AOSP's `Patterns.EMAIL_ADDRESS`, so the check works off-device too. */
    private val EMAIL_ADDRESS = Regex(
        "[a-zA-Z0-9\\+\\.\\_\\%\\-\\+]{1,256}" +
            "\\@" +
            "[a-zA-Z0-9][a-zA-Z0-9\\-]{0,64}" +
            "(" +
            "\\." +
            "[a-zA-Z0-9][a-zA-Z0-9\\-]{0,25}" +
            ")+"
    )

    private val PNG_SIGNATURE =
        byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A)

    /**
     * Checks whether a sticker pack contains valid data.
     *
     * @throws IllegalStateException naming the first failed check, like the sample.
     */
    @JvmStatic
    @Throws(IllegalStateException::class)
    fun verifyStickerPackValidity(stickerPack: ValidatablePack) {
        if (stickerPack.identifier.isEmpty()) {
            throw IllegalStateException("sticker pack identifier is empty")
        }
        if (stickerPack.identifier.length > CHAR_COUNT_MAX) {
            throw IllegalStateException("sticker pack identifier cannot exceed $CHAR_COUNT_MAX characters")
        }
        checkStringValidity(stickerPack.identifier)
        if (stickerPack.publisher.isEmpty()) {
            throw IllegalStateException("sticker pack publisher is empty, sticker pack identifier: ${stickerPack.identifier}")
        }
        if (stickerPack.publisher.length > CHAR_COUNT_MAX) {
            throw IllegalStateException("sticker pack publisher cannot exceed $CHAR_COUNT_MAX characters, sticker pack identifier: ${stickerPack.identifier}")
        }
        if (stickerPack.name.isEmpty()) {
            throw IllegalStateException("sticker pack name is empty, sticker pack identifier: ${stickerPack.identifier}")
        }
        if (stickerPack.name.length > CHAR_COUNT_MAX) {
            throw IllegalStateException("sticker pack name cannot exceed $CHAR_COUNT_MAX characters, sticker pack identifier: ${stickerPack.identifier}")
        }
        if (stickerPack.trayImageFile.isEmpty()) {
            throw IllegalStateException("sticker pack tray id is empty, sticker pack identifier:${stickerPack.identifier}")
        }
        if (stickerPack.androidPlayStoreLink.isNotEmpty() && !isValidWebsiteUrl(stickerPack.androidPlayStoreLink)) {
            throw IllegalStateException("Make sure to include http or https in url links, android play store link is not a valid url: ${stickerPack.androidPlayStoreLink}")
        }
        if (stickerPack.androidPlayStoreLink.isNotEmpty() && !isURLInCorrectDomain(stickerPack.androidPlayStoreLink, PLAY_STORE_DOMAIN)) {
            throw IllegalStateException("android play store link should use play store domain: $PLAY_STORE_DOMAIN")
        }
        if (stickerPack.iosAppStoreLink.isNotEmpty() && !isValidWebsiteUrl(stickerPack.iosAppStoreLink)) {
            throw IllegalStateException("Make sure to include http or https in url links, ios app store link is not a valid url: ${stickerPack.iosAppStoreLink}")
        }
        if (stickerPack.iosAppStoreLink.isNotEmpty() && !isURLInCorrectDomain(stickerPack.iosAppStoreLink, APPLE_STORE_DOMAIN)) {
            throw IllegalStateException("iOS app store link should use app store domain: $APPLE_STORE_DOMAIN")
        }
        if (stickerPack.licenseAgreementWebsite.isNotEmpty() && !isValidWebsiteUrl(stickerPack.licenseAgreementWebsite)) {
            throw IllegalStateException("Make sure to include http or https in url links, license agreement link is not a valid url: ${stickerPack.licenseAgreementWebsite}")
        }
        if (stickerPack.privacyPolicyWebsite.isNotEmpty() && !isValidWebsiteUrl(stickerPack.privacyPolicyWebsite)) {
            throw IllegalStateException("Make sure to include http or https in url links, privacy policy link is not a valid url: ${stickerPack.privacyPolicyWebsite}")
        }
        if (stickerPack.publisherWebsite.isNotEmpty() && !isValidWebsiteUrl(stickerPack.publisherWebsite)) {
            throw IllegalStateException("Make sure to include http or https in url links, publisher website link is not a valid url: ${stickerPack.publisherWebsite}")
        }
        if (stickerPack.publisherEmail.isNotEmpty() && !EMAIL_ADDRESS.matches(stickerPack.publisherEmail)) {
            throw IllegalStateException("publisher email does not seem valid, email is: ${stickerPack.publisherEmail}")
        }
        if (stickerPack.trayBytes.size > TRAY_IMAGE_FILE_SIZE_MAX_KB * KB_IN_BYTES) {
            throw IllegalStateException("tray image should be less than $TRAY_IMAGE_FILE_SIZE_MAX_KB KB, tray image file: ${stickerPack.trayImageFile}")
        }
        val (trayWidth, trayHeight) = try {
            trayDimensions(stickerPack.trayBytes)
        } catch (e: IllegalArgumentException) {
            throw IllegalStateException("Cannot open tray image, ${stickerPack.trayImageFile}", e)
        }
        if (trayHeight > TRAY_IMAGE_DIMENSION_MAX || trayHeight < TRAY_IMAGE_DIMENSION_MIN) {
            throw IllegalStateException("tray image height should between $TRAY_IMAGE_DIMENSION_MIN and $TRAY_IMAGE_DIMENSION_MAX pixels, current tray image height is $trayHeight, tray image file: ${stickerPack.trayImageFile}")
        }
        if (trayWidth > TRAY_IMAGE_DIMENSION_MAX || trayWidth < TRAY_IMAGE_DIMENSION_MIN) {
            throw IllegalStateException("tray image width should be between $TRAY_IMAGE_DIMENSION_MIN and $TRAY_IMAGE_DIMENSION_MAX pixels, current tray image width is $trayWidth, tray image file: ${stickerPack.trayImageFile}")
        }
        val stickers = stickerPack.stickers
        if (stickers.size < STICKER_SIZE_MIN || stickers.size > STICKER_SIZE_MAX) {
            throw IllegalStateException("sticker pack sticker count should be between 3 to 30 inclusive, it currently has ${stickers.size}, sticker pack identifier: ${stickerPack.identifier}")
        }
        for (sticker in stickers) {
            validateSticker(stickerPack.identifier, sticker, stickerPack.animatedStickerPack)
        }
    }

    @Throws(IllegalStateException::class)
    private fun validateSticker(identifier: String, sticker: ValidatableSticker, animatedStickerPack: Boolean) {
        if (sticker.emojis.size > EMOJI_MAX_LIMIT) {
            throw IllegalStateException("emoji count exceed limit, sticker pack identifier: $identifier, filename: ${sticker.fileName}")
        }
        if (sticker.emojis.size < EMOJI_MIN_LIMIT) {
            throw IllegalStateException("To provide best user experience, please associate at least 1 emoji to this sticker, sticker pack identifier: $identifier, filename: ${sticker.fileName}")
        }
        if (sticker.fileName.isEmpty()) {
            throw IllegalStateException("no file path for sticker, sticker pack identifier:$identifier")
        }
        if (isInvalidAccessibilityText(sticker.accessibilityText, animatedStickerPack)) {
            throw IllegalStateException("accessibility text length exceed limit, sticker pack identifier: $identifier, filename: ${sticker.fileName}")
        }
        validateStickerFile(identifier, sticker.fileName, sticker.bytes, animatedStickerPack)
    }

    private fun isInvalidAccessibilityText(accessibilityText: String?, isAnimatedStickerPack: Boolean): Boolean {
        if (accessibilityText == null) {
            return false
        }
        val length = accessibilityText.length
        return isAnimatedStickerPack && length > MAX_ANIMATED_STICKER_A11Y_TEXT_CHAR_LIMIT ||
            !isAnimatedStickerPack && length > MAX_STATIC_STICKER_A11Y_TEXT_CHAR_LIMIT
    }

    @Throws(IllegalStateException::class)
    private fun validateStickerFile(identifier: String, fileName: String, stickerInBytes: ByteArray, animatedStickerPack: Boolean) {
        if (!animatedStickerPack && stickerInBytes.size > STATIC_STICKER_FILE_LIMIT_KB * KB_IN_BYTES) {
            throw IllegalStateException("static sticker should be less than ${STATIC_STICKER_FILE_LIMIT_KB}KB, current file is ${stickerInBytes.size / KB_IN_BYTES} KB, sticker pack identifier: $identifier, filename: $fileName")
        }
        if (animatedStickerPack && stickerInBytes.size > ANIMATED_STICKER_FILE_LIMIT_KB * KB_IN_BYTES) {
            throw IllegalStateException("animated sticker should be less than ${ANIMATED_STICKER_FILE_LIMIT_KB}KB, current file is ${stickerInBytes.size / KB_IN_BYTES} KB, sticker pack identifier: $identifier, filename: $fileName")
        }
        val webpInfo = try {
            WebpInfo.parse(stickerInBytes)
        } catch (e: IllegalArgumentException) {
            throw IllegalStateException("Error parsing webp image, sticker pack identifier: $identifier, filename: $fileName", e)
        }
        if (webpInfo.height != IMAGE_HEIGHT) {
            throw IllegalStateException("sticker height should be $IMAGE_HEIGHT, current height is ${webpInfo.height}, sticker pack identifier: $identifier, filename: $fileName")
        }
        if (webpInfo.width != IMAGE_WIDTH) {
            throw IllegalStateException("sticker width should be $IMAGE_WIDTH, current width is ${webpInfo.width}, sticker pack identifier: $identifier, filename: $fileName")
        }
        if (animatedStickerPack) {
            if (webpInfo.frameCount <= 1) {
                throw IllegalStateException("this pack is marked as animated sticker pack, all stickers should animate, sticker pack identifier: $identifier, filename: $fileName")
            }
            if (webpInfo.minFrameDurationMs < ANIMATED_STICKER_FRAME_DURATION_MIN) {
                throw IllegalStateException("animated sticker frame duration limit is $ANIMATED_STICKER_FRAME_DURATION_MIN, sticker pack identifier: $identifier, filename: $fileName")
            }
            if (webpInfo.totalDurationMs > ANIMATED_STICKER_TOTAL_DURATION_MAX) {
                throw IllegalStateException("sticker animation max duration is: $ANIMATED_STICKER_TOTAL_DURATION_MAX ms, current duration is: ${webpInfo.totalDurationMs} ms, sticker pack identifier: $identifier, filename: $fileName")
            }
        } else if (webpInfo.frameCount > 1) {
            throw IllegalStateException("this pack is not marked as animated sticker pack, all stickers should be static stickers, sticker pack identifier: $identifier, filename: $fileName")
        }
    }

    @Throws(IllegalStateException::class)
    private fun checkStringValidity(string: String) {
        val pattern = "[\\w-.,'\\s]+" // [a-zA-Z0-9_-.' ]
        if (!string.matches(Regex(pattern))) {
            throw IllegalStateException("$string contains invalid characters, allowed characters are a to z, A to Z, _ , ' - . and space character")
        }
        if (string.contains("..")) {
            throw IllegalStateException("$string cannot contain ..")
        }
    }

    @Throws(IllegalStateException::class)
    private fun isValidWebsiteUrl(websiteUrl: String): Boolean {
        val url = try {
            URL(websiteUrl)
        } catch (e: MalformedURLException) {
            throw IllegalStateException("url: $websiteUrl is malformed", e)
        }
        // The sample uses URLUtil.isHttpUrl/isHttpsUrl; for a well-formed URL that is a
        // protocol check, done here without android imports.
        return url.protocol == "http" || url.protocol == "https"
    }

    @Throws(IllegalStateException::class)
    private fun isURLInCorrectDomain(urlString: String, domain: String): Boolean {
        try {
            val url = URL(urlString)
            if (domain == url.host) {
                return true
            }
        } catch (e: MalformedURLException) {
            throw IllegalStateException("url: $urlString is malformed", e)
        }
        return false
    }

    // -- image header helpers ------------------------------------------------------------------

    /** True when [bytes] starts with the 8-byte PNG signature. */
    @JvmStatic
    fun isPng(bytes: ByteArray): Boolean {
        if (bytes.size < PNG_SIGNATURE.size) {
            return false
        }
        for (i in PNG_SIGNATURE.indices) {
            if (bytes[i] != PNG_SIGNATURE[i]) {
                return false
            }
        }
        return true
    }

    /**
     * Reads (width, height) from a PNG's IHDR chunk (big-endian, bytes 16..23).
     *
     * @throws IllegalArgumentException when [bytes] is not a PNG with a leading IHDR chunk.
     */
    @JvmStatic
    fun pngDimensions(bytes: ByteArray): Pair<Int, Int> {
        require(isPng(bytes)) { "Not a PNG file" }
        require(bytes.size >= 24 && bytes[12].toInt() == 'I'.code && bytes[13].toInt() == 'H'.code &&
            bytes[14].toInt() == 'D'.code && bytes[15].toInt() == 'R'.code) { "PNG IHDR chunk not found" }
        val width = be32(bytes, 16)
        val height = be32(bytes, 20)
        return width to height
    }

    /** Tray icons may be PNG (this app's packs) or WebP (the sample's). */
    private fun trayDimensions(bytes: ByteArray): Pair<Int, Int> =
        if (isPng(bytes)) {
            pngDimensions(bytes)
        } else {
            val info = WebpInfo.parse(bytes)
            info.width to info.height
        }

    private fun be32(bytes: ByteArray, offset: Int): Int =
        ((bytes[offset].toInt() and 0xFF) shl 24) or
            ((bytes[offset + 1].toInt() and 0xFF) shl 16) or
            ((bytes[offset + 2].toInt() and 0xFF) shl 8) or
            (bytes[offset + 3].toInt() and 0xFF)
}
