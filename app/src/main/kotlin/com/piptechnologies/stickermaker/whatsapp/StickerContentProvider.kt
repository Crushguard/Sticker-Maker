package com.piptechnologies.stickermaker.whatsapp

import android.content.ContentProvider
import android.content.ContentResolver
import android.content.ContentValues
import android.content.UriMatcher
import android.content.res.AssetFileDescriptor
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.util.Log
import com.piptechnologies.stickermaker.BuildConfig
import com.piptechnologies.stickermaker.core.data.db.InstalledPackEntity
import com.piptechnologies.stickermaker.core.data.db.LoveDb
import com.piptechnologies.stickermaker.core.data.db.OwnPackEntity
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import java.io.File
import java.io.FileNotFoundException

/**
 * Port of the WhatsApp/stickers sample `StickerContentProvider` (revision 06144a1).
 *
 * Differences from the sample, both required by this app's dynamic packs:
 *  - pack metadata comes from Room (installed + own packs) instead of assets/contents.json,
 *    so the asset URIs are matched with a wildcard instead of per-file registrations;
 *  - image files are served from each pack's directory on disk (tray + sticker files live
 *    directly in the pack's `dirPath`) instead of the APK's assets.
 *
 * All URI shapes, cursor column names and behaviors WhatsApp depends on are kept verbatim.
 */
class StickerContentProvider : ContentProvider() {

    /** Room access; Hilt is not ready during provider onCreate, so the handle is lazy. */
    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface StickerContentProviderEntryPoint {
        fun loveDb(): LoveDb
    }

    private val db: LoveDb by lazy {
        EntryPointAccessors.fromApplication(
            checkNotNull(context) { "StickerContentProvider used before attachInfo" }.applicationContext,
            StickerContentProviderEntryPoint::class.java,
        ).loveDb()
    }

    private val matcher = UriMatcher(UriMatcher.NO_MATCH)

    override fun onCreate(): Boolean {
        val authority = CONTENT_PROVIDER_AUTHORITY
        val context = checkNotNull(context)
        if (!authority.startsWith(context.packageName)) {
            throw IllegalStateException(
                "your authority ($authority) for the content provider should start with your package name: ${context.packageName}"
            )
        }

        // The call to get the metadata for the sticker packs.
        matcher.addURI(authority, METADATA, METADATA_CODE)

        // The call to get the metadata for a single sticker pack. * represents the identifier.
        matcher.addURI(authority, "$METADATA/*", METADATA_CODE_FOR_SINGLE_PACK)

        // Gets the list of stickers for a sticker pack. * represents the identifier.
        matcher.addURI(authority, "$STICKERS/*", STICKERS_CODE)

        // Tray icons and sticker files: {identifier}/{fileName}. The sample registers every
        // file here from contents.json; packs live in Room and change at runtime, so a
        // wildcard is registered instead and the file is checked against Room on each call.
        matcher.addURI(authority, "$STICKERS_ASSET/*/*", STICKERS_ASSET_CODE)

        return true
    }

    override fun query(
        uri: Uri,
        projection: Array<String>?,
        selection: String?,
        selectionArgs: Array<String>?,
        sortOrder: String?,
    ): Cursor {
        return when (matcher.match(uri)) {
            METADATA_CODE -> getPackForAllStickerPacks(uri)
            METADATA_CODE_FOR_SINGLE_PACK -> getCursorForSingleStickerPack(uri)
            STICKERS_CODE -> getStickersForAStickerPack(uri)
            else -> throw IllegalArgumentException("Unknown URI: $uri")
        }
    }

    override fun openAssetFile(uri: Uri, mode: String): AssetFileDescriptor? {
        val matchCode = matcher.match(uri)
        if (matchCode == STICKERS_ASSET_CODE) {
            return getImageAsset(uri)
        }
        return null
    }

    override fun getType(uri: Uri): String {
        return when (matcher.match(uri)) {
            METADATA_CODE -> "vnd.android.cursor.dir/vnd.$CONTENT_PROVIDER_AUTHORITY.$METADATA"
            METADATA_CODE_FOR_SINGLE_PACK -> "vnd.android.cursor.item/vnd.$CONTENT_PROVIDER_AUTHORITY.$METADATA"
            STICKERS_CODE -> "vnd.android.cursor.dir/vnd.$CONTENT_PROVIDER_AUTHORITY.$STICKERS"
            STICKERS_ASSET_CODE ->
                if (assetCode(uri) == STICKER_PACK_TRAY_ICON_CODE) "image/png" else "image/webp"
            else -> throw IllegalArgumentException("Unknown URI: $uri")
        }
    }

    // -- pack lookup (Room replaces the sample's contents.json) -------------------------------

    private fun getStickerPackList(): List<ProviderPack> =
        db.installedPackDao().getAllBlocking().map { it.toProviderPack() } +
            db.ownPackDao().getAllBlocking().map { it.toProviderPack() }

    private fun getPack(identifier: String): ProviderPack? =
        db.installedPackDao().getBlocking(identifier)?.toProviderPack()
            ?: db.ownPackDao().getBlocking(identifier)?.toProviderPack()

    private fun getStickers(identifier: String): List<ProviderSticker> =
        db.installedPackDao().stickersBlocking(identifier)
            .map { ProviderSticker(it.fileName, it.emojis) }
            .ifEmpty {
                db.ownPackDao().stickersBlocking(identifier)
                    .map { ProviderSticker(it.fileName, it.emojis) }
            }

    /** Resolves an asset URI to the tray-icon or sticker-asset code via Room. */
    private fun assetCode(uri: Uri): Int {
        val pathSegments = uri.pathSegments
        if (pathSegments.size != 3) {
            return UriMatcher.NO_MATCH
        }
        val pack = getPack(pathSegments[1]) ?: return UriMatcher.NO_MATCH
        return if (pathSegments[2] == pack.trayImageFile) STICKER_PACK_TRAY_ICON_CODE else STICKERS_ASSET_CODE
    }

    // -- cursors ------------------------------------------------------------------------------

    private fun getPackForAllStickerPacks(uri: Uri): Cursor =
        getStickerPackInfo(uri, getStickerPackList())

    private fun getCursorForSingleStickerPack(uri: Uri): Cursor {
        val identifier = uri.lastPathSegment.orEmpty()
        val pack = getPack(identifier)
        return getStickerPackInfo(uri, if (pack != null) listOf(pack) else emptyList())
    }

    private fun getStickerPackInfo(uri: Uri, stickerPackList: List<ProviderPack>): Cursor {
        val cursor = MatrixCursor(
            arrayOf(
                STICKER_PACK_IDENTIFIER_IN_QUERY,
                STICKER_PACK_NAME_IN_QUERY,
                STICKER_PACK_PUBLISHER_IN_QUERY,
                STICKER_PACK_ICON_IN_QUERY,
                ANDROID_APP_DOWNLOAD_LINK_IN_QUERY,
                IOS_APP_DOWNLOAD_LINK_IN_QUERY,
                PUBLISHER_EMAIL,
                PUBLISHER_WEBSITE,
                PRIVACY_POLICY_WEBSITE,
                LICENSE_AGREEMENT_WEBSITE,
                IMAGE_DATA_VERSION,
                AVOID_CACHE,
                ANIMATED_STICKER_PACK,
            )
        )
        for (stickerPack in stickerPackList) {
            cursor.newRow()
                .add(stickerPack.identifier)
                .add(stickerPack.name)
                .add(stickerPack.publisher)
                .add(stickerPack.trayImageFile)
                .add(ANDROID_PLAY_STORE_LINK)
                .add(IOS_APP_STORE_LINK)
                .add(PUBLISHER_EMAIL_VALUE)
                .add(PUBLISHER_WEBSITE_VALUE)
                .add(PRIVACY_POLICY_WEBSITE_VALUE)
                .add(LICENSE_AGREEMENT_WEBSITE_VALUE)
                .add(IMAGE_DATA_VERSION_VALUE)
                .add(if (AVOID_CACHE_VALUE) 1 else 0)
                .add(if (stickerPack.animatedStickerPack) 1 else 0)
        }
        cursor.setNotificationUri(checkNotNull(context).contentResolver, uri)
        return cursor
    }

    private fun getStickersForAStickerPack(uri: Uri): Cursor {
        val identifier = uri.lastPathSegment.orEmpty()
        val cursor = MatrixCursor(
            arrayOf(
                STICKER_FILE_NAME_IN_QUERY,
                STICKER_FILE_EMOJI_IN_QUERY,
                STICKER_FILE_ACCESSIBILITY_TEXT_IN_QUERY,
            )
        )
        for (sticker in getStickers(identifier)) {
            // Emojis are stored comma-joined, exactly the shape the sample builds with
            // TextUtils.join(",", emojis). No accessibility text is stored.
            cursor.addRow(arrayOf<Any?>(sticker.fileName, sticker.emojis, null))
        }
        cursor.setNotificationUri(checkNotNull(context).contentResolver, uri)
        return cursor
    }

    // -- files --------------------------------------------------------------------------------

    @Throws(IllegalArgumentException::class)
    private fun getImageAsset(uri: Uri): AssetFileDescriptor? {
        val pathSegments = uri.pathSegments
        if (pathSegments.size != 3) {
            throw IllegalArgumentException("path segments should be 3, uri is: $uri")
        }
        val fileName = pathSegments[pathSegments.size - 1]
        val identifier = pathSegments[pathSegments.size - 2]
        if (identifier.isEmpty()) {
            throw IllegalArgumentException("identifier is empty, uri: $uri")
        }
        if (fileName.isEmpty()) {
            throw IllegalArgumentException("file name is empty, uri: $uri")
        }
        // Making sure the file that is trying to be fetched is in the list of stickers.
        val stickerPack = getPack(identifier) ?: return null
        if (fileName == stickerPack.trayImageFile) {
            return fetchFile(uri, stickerPack.dirPath, fileName)
        }
        for (sticker in getStickers(identifier)) {
            if (fileName == sticker.fileName) {
                return fetchFile(uri, stickerPack.dirPath, fileName)
            }
        }
        return null
    }

    private fun fetchFile(uri: Uri, dirPath: String, fileName: String): AssetFileDescriptor? {
        val file = File(dirPath, fileName)
        return try {
            val pfd = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
            AssetFileDescriptor(pfd, 0, file.length())
        } catch (e: FileNotFoundException) {
            Log.e(TAG, "FileNotFoundException when getting asset file, uri:$uri", e)
            null
        }
    }

    // -- unsupported --------------------------------------------------------------------------

    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<String>?): Int {
        throw UnsupportedOperationException("Not supported")
    }

    override fun insert(uri: Uri, values: ContentValues?): Uri? {
        throw UnsupportedOperationException("Not supported")
    }

    override fun update(
        uri: Uri,
        values: ContentValues?,
        selection: String?,
        selectionArgs: Array<String>?,
    ): Int {
        throw UnsupportedOperationException("Not supported")
    }

    /** What the provider serves about one pack, whichever table it came from. */
    private data class ProviderPack(
        val identifier: String,
        val name: String,
        val publisher: String,
        val trayImageFile: String,
        val animatedStickerPack: Boolean,
        val dirPath: String,
    )

    private data class ProviderSticker(val fileName: String, val emojis: String)

    companion object {
        private const val TAG = "StickerContentProvider"

        /** Appended to the application id to form the provider authority. */
        const val CONTENT_PROVIDER_AUTHORITY_SUFFIX = ".stickercontentprovider"

        /** Must match android:authorities of the <provider> entry in the manifest. */
        const val CONTENT_PROVIDER_AUTHORITY: String =
            BuildConfig.APPLICATION_ID + CONTENT_PROVIDER_AUTHORITY_SUFFIX

        /**
         * Do not change the strings listed below, as these are used by WhatsApp. And changing
         * these will break the interface between sticker app and WhatsApp.
         */
        const val STICKER_PACK_IDENTIFIER_IN_QUERY = "sticker_pack_identifier"
        const val STICKER_PACK_NAME_IN_QUERY = "sticker_pack_name"
        const val STICKER_PACK_PUBLISHER_IN_QUERY = "sticker_pack_publisher"
        const val STICKER_PACK_ICON_IN_QUERY = "sticker_pack_icon"
        const val ANDROID_APP_DOWNLOAD_LINK_IN_QUERY = "android_play_store_link"
        const val IOS_APP_DOWNLOAD_LINK_IN_QUERY = "ios_app_download_link"
        const val PUBLISHER_EMAIL = "sticker_pack_publisher_email"
        const val PUBLISHER_WEBSITE = "sticker_pack_publisher_website"
        const val PRIVACY_POLICY_WEBSITE = "sticker_pack_privacy_policy_website"
        const val LICENSE_AGREEMENT_WEBSITE = "sticker_pack_license_agreement_website"
        const val IMAGE_DATA_VERSION = "image_data_version"
        const val AVOID_CACHE = "whatsapp_will_not_cache_stickers"
        const val ANIMATED_STICKER_PACK = "animated_sticker_pack"

        const val STICKER_FILE_NAME_IN_QUERY = "sticker_file_name"
        const val STICKER_FILE_EMOJI_IN_QUERY = "sticker_emoji"
        const val STICKER_FILE_ACCESSIBILITY_TEXT_IN_QUERY = "sticker_accessibility_text"

        /**
         * Values for the metadata columns Room does not store. All are optional for WhatsApp;
         * empty means absent. Files on disk never change under an identifier without the pack
         * being re-added, so the image data version is constant.
         */
        private const val ANDROID_PLAY_STORE_LINK = ""
        private const val IOS_APP_STORE_LINK = ""
        private const val PUBLISHER_EMAIL_VALUE = ""
        private const val PUBLISHER_WEBSITE_VALUE = ""
        private const val PRIVACY_POLICY_WEBSITE_VALUE = ""
        private const val LICENSE_AGREEMENT_WEBSITE_VALUE = ""
        private const val IMAGE_DATA_VERSION_VALUE = "1"
        private const val AVOID_CACHE_VALUE = false

        /**
         * Do not change the values in the UriMatcher because otherwise, WhatsApp will not be
         * able to fetch the stickers from the ContentProvider.
         */
        const val METADATA = "metadata"
        private const val METADATA_CODE = 1
        private const val METADATA_CODE_FOR_SINGLE_PACK = 2

        const val STICKERS = "stickers"
        private const val STICKERS_CODE = 3

        const val STICKERS_ASSET = "stickers_asset"
        private const val STICKERS_ASSET_CODE = 4

        private const val STICKER_PACK_TRAY_ICON_CODE = 5

        val AUTHORITY_URI: Uri = Uri.Builder()
            .scheme(ContentResolver.SCHEME_CONTENT)
            .authority(CONTENT_PROVIDER_AUTHORITY)
            .appendPath(METADATA)
            .build()

        private fun InstalledPackEntity.toProviderPack() = ProviderPack(
            identifier = id,
            name = name,
            publisher = publisher,
            trayImageFile = trayFile,
            animatedStickerPack = animated,
            dirPath = dirPath,
        )

        private fun OwnPackEntity.toProviderPack() = ProviderPack(
            identifier = id,
            name = name,
            publisher = publisher,
            trayImageFile = trayFile,
            animatedStickerPack = animated,
            dirPath = dirPath,
        )
    }
}
