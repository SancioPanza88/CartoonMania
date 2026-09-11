package com.cartoonmania.app

import java.net.HttpURLConnection
import java.net.URL

/** Risoluzione nativa degli embed OK.ru (usati da Loonex quando il video
 *  non sta sul videoserver): dalla pagina guarda si ricava l'embed ID,
 *  dalla pagina embed i metadati con l'HLS fresco (scade in ore ed e'
 *  legato all'IP, quindi va risolto sul dispositivo a ogni visione, mai
 *  immagazzinato). Nativo = niente player OK.ru = niente muri anti-adblock.
 *  Qualunque fallimento -> null e l'app usa il fallback WebView esistente. */
object OkRu {

    const val HLS_MIME = "application/x-mpegURL"

    private const val UA =
        "Mozilla/5.0 (Linux; Android 13; Pixel 7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Mobile Safari/537.36"

    fun resolveHls(guardaUrl: String): String? {
        try {
            val page = httpGet(guardaUrl, null) ?: return null
            var embedId = firstGroup(page, """guardaOkruEmbedId\s*=\s*"([^"]+)"""")
            if (embedId.isNullOrEmpty()) {
                embedId = firstGroup(page, """currentVideoId\s*=\s*"([^"]+)"""")
            }
            if (embedId.isNullOrEmpty()) return null
            val embed = httpGet("https://ok.ru/videoembed/$embedId", guardaUrl) ?: return null
            val m = Regex("""(https?://[^"\\\s]*?/expires/[^"\\\s]*)""").find(embed) ?: return null
            var u = m.groupValues[1]
                .replace("\\/", "/")
                .replace("\\u0026", "&")
                .replace("\\u002F", "/")
                .replace("&amp;", "&")
            if (!u.startsWith("http")) return null
            if (!u.contains("okcdn.ru") && !u.contains("ok.ru")) return null
            return u
        } catch (_: Exception) {
            return null
        }
    }

    private fun firstGroup(s: String, pat: String): String? = try {
        Regex(pat).find(s)?.groupValues?.getOrNull(1)
    } catch (_: Exception) {
        null
    }

    private fun httpGet(url: String, referer: String?): String? {
        var conn: HttpURLConnection? = null
        try {
            conn = URL(url).openConnection() as HttpURLConnection
            conn.connectTimeout = 15000
            conn.readTimeout = 15000
            conn.instanceFollowRedirects = true
            conn.setRequestProperty("User-Agent", UA)
            if (!referer.isNullOrEmpty()) conn.setRequestProperty("Referer", referer)
            if (conn.responseCode != 200) return null
            return conn.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
        } catch (_: Exception) {
            return null
        } finally {
            try {
                conn?.disconnect()
            } catch (_: Exception) {
            }
        }
    }
}
