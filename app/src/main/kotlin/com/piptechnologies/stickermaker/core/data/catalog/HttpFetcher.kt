package com.piptechnologies.stickermaker.core.data.catalog

import java.io.IOException
import java.io.InputStream
import okhttp3.OkHttpClient
import okhttp3.Request

/** Plain HTTPS downloads of public catalog files (no Firebase SDK: the files may move to a CDN). */
interface HttpFetcher {

    /** Opens [url] and hands its body to [read]. Blocking; call from an IO dispatcher. Throws on HTTP errors. */
    suspend fun <T> open(url: String, read: (InputStream) -> T): T
}

class OkHttpFetcher(private val client: OkHttpClient) : HttpFetcher {

    override suspend fun <T> open(url: String, read: (InputStream) -> T): T {
        val request = Request.Builder().url(url).build()
        return client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IOException("HTTP ${response.code} for $url")
            val body = response.body ?: throw IOException("Empty body for $url")
            read(body.byteStream())
        }
    }
}
