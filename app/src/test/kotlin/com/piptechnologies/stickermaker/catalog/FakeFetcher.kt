package com.piptechnologies.stickermaker.catalog

import com.piptechnologies.stickermaker.core.data.catalog.HttpFetcher
import java.io.ByteArrayInputStream
import java.io.IOException
import java.io.InputStream

/** Serves bytes by URL from memory and counts requests; unknown URLs fail like a network error. */
class FakeFetcher(private val bodies: MutableMap<String, ByteArray> = mutableMapOf()) : HttpFetcher {
    val requests = mutableListOf<String>()

    operator fun set(url: String, body: ByteArray) {
        bodies[url] = body
    }

    override suspend fun <T> open(url: String, read: (InputStream) -> T): T {
        requests += url
        val body = bodies[url] ?: throw IOException("HTTP 404 $url")
        return ByteArrayInputStream(body).use(read)
    }
}
