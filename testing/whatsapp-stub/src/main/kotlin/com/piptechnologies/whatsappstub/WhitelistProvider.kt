package com.piptechnologies.whatsappstub

import android.content.ContentProvider
import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri

/**
 * WhatsApp's whitelist check: content://com.whatsapp.provider.sticker_whitelist_check/
 * is_whitelisted?authority=…&identifier=… answers one row with a `result`
 * column, 1 once the pack was added through [AddStickerPackActivity].
 */
class WhitelistProvider : ContentProvider() {

    override fun onCreate(): Boolean = true

    override fun query(
        uri: Uri,
        projection: Array<String>?,
        selection: String?,
        selectionArgs: Array<String>?,
        sortOrder: String?
    ): Cursor? {
        if (uri.lastPathSegment != "is_whitelisted") return null
        val context = context ?: return null
        val added = Whitelist.contains(
            context,
            uri.getQueryParameter("authority").orEmpty(),
            uri.getQueryParameter("identifier").orEmpty()
        )
        return MatrixCursor(arrayOf("result")).apply { addRow(arrayOf<Any?>(if (added) 1 else 0)) }
    }

    override fun getType(uri: Uri): String? = null

    override fun insert(uri: Uri, values: ContentValues?): Uri? = null

    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<String>?): Int = 0

    override fun update(
        uri: Uri,
        values: ContentValues?,
        selection: String?,
        selectionArgs: Array<String>?
    ): Int = 0
}

/** Packs "added to WhatsApp", keyed by provider authority and pack identifier. */
object Whitelist {
    private const val PREFS = "whitelist"
    private const val KEY_PACKS = "packs"

    fun add(context: Context, authority: String, identifier: String) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val packs = prefs.getStringSet(KEY_PACKS, emptySet()).orEmpty() + key(authority, identifier)
        prefs.edit().putStringSet(KEY_PACKS, packs).commit()
    }

    fun contains(context: Context, authority: String, identifier: String): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getStringSet(KEY_PACKS, emptySet())
            .orEmpty()
            .contains(key(authority, identifier))

    private fun key(authority: String, identifier: String) = "$authority/$identifier"
}
