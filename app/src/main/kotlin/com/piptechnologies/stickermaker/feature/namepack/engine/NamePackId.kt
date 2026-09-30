package com.piptechnologies.stickermaker.feature.namepack.engine

import java.security.MessageDigest

/**
 * The WhatsApp identifier of a name pack: `own-np-` + 12 hex of SHA-256 over the language and
 * both normalized names. The same names and language re-letter the same pack (tone, character
 * and relation do not change it); the `own-` prefix keeps analytics reporting it as "own".
 */
object NamePackId {

    const val PREFIX = "own-np-"

    fun of(lang: String, you: String, love: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
            .digest("$lang\u0000$you\u0000$love".toByteArray(Charsets.UTF_8))
        return PREFIX + digest.take(6).joinToString("") { "%02x".format(it) }
    }
}
