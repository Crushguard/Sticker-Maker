package com.piptechnologies.stickermaker.core.data.catalog

import java.net.URLEncoder

/**
 * Turns a public object path ("public/packs/<id>/v3-1a2b3c4d/pack.zip") into a URL with the template catalog/meta
 * carries: `{path}` is the URL-encoded path (Firebase Storage), `{rawPath}` the path as is (a CDN).
 * Debug builds pointed at the emulators reach the host machine, not the device's own localhost.
 */
class CatalogUrls(private val template: String, private val emulatorHost: String?) {

    fun url(path: String): String {
        val encoded = URLEncoder.encode(path, "UTF-8").replace("+", "%20")
        val url = template.replace("{path}", encoded).replace("{rawPath}", path)
        return if (emulatorHost.isNullOrBlank()) url else url.replace(LOCAL_HOST, "://$emulatorHost")
    }

    private companion object {
        val LOCAL_HOST = Regex("://(127\\.0\\.0\\.1|localhost)(?=[:/])")
    }
}
