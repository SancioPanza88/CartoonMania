package com.cartoonmania.app

import android.content.Context
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.zip.GZIPInputStream

object CatalogRepo {

    const val RAW_BASE = "https://raw.githubusercontent.com/SancioPanza88/CartoonMania/main"
    const val VERSION_URL = "$RAW_BASE/data/catalog.version.txt"
    const val CATALOG_URL = "$RAW_BASE/data/catalog.json.gz"

    data class Player(val name: String, val url: String)
    data class Episode(val label: String, val players: List<Player>)
    data class Title(val slug: String, val title: String, val img: String?, val episodes: List<Episode>)

    @Volatile
    var titles: List<Title> = emptyList()
        private set

    private fun cacheFile(ctx: Context) = File(ctx.filesDir, "catalog.json.gz")
    private fun versionFile(ctx: Context) = File(ctx.filesDir, "catalog.version")

    @Synchronized
    fun loadLocal(ctx: Context): List<Title> {
        val f = cacheFile(ctx)
        val source: GZIPInputStream = if (f.exists() && f.length() > 0) {
            GZIPInputStream(f.inputStream())
        } else {
            GZIPInputStream(ctx.assets.open("catalog.json.gz"))
        }
        val text = source.use { readAll(it) }
        titles = parse(text)
        return titles
    }

    fun currentVersion(ctx: Context): String {
        val f = versionFile(ctx)
        return if (f.exists()) f.readText().trim() else "0"
    }

    /**
     * Scarica il catalogo remoto se la versione e' piu' recente.
     * Ritorna messaggio di esito.
     */
    @Synchronized
    fun refresh(ctx: Context): String {
        val remoteVer = httpGet(VERSION_URL)?.trim()
            ?: return "Rete non disponibile"
        if (remoteVer.isEmpty()) return "Nessun aggiornamento"
        if (cacheFile(ctx).exists() && remoteVer == currentVersion(ctx)) {
            return "Catalogo gia' aggiornato (v$remoteVer)"
        }
        val tmp = File(ctx.filesDir, "catalog.tmp.gz")
        downloadTo(CATALOG_URL, tmp)
        if (!isGzip(tmp)) {
            tmp.delete()
            return "File remoto non valido"
        }
        // valida il parsing prima di sostituire
        val newText = GZIPInputStream(tmp.inputStream()).use { readAll(it) }
        val newTitles = parse(newText)
        cacheFile(ctx).outputStream().use { out -> tmp.inputStream().use { it.copyTo(out) } }
        tmp.delete()
        versionFile(ctx).writeText(remoteVer)
        titles = newTitles
        return "Aggiornato: ${titles.size} titoli (v$remoteVer)"
    }

    internal fun parse(jsonText: String): List<Title> {
        val root = JSONObject(jsonText)
        val arr = root.getJSONArray("s")
        val list = ArrayList<Title>(arr.length())
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            val epsArr = o.getJSONArray("e")
            val eps = ArrayList<Episode>(epsArr.length())
            for (j in 0 until epsArr.length()) {
                val eo = epsArr.getJSONObject(j)
                val pArr = eo.getJSONArray("p")
                val players = ArrayList<Player>(pArr.length())
                for (k in 0 until pArr.length()) {
                    val po = pArr.getJSONObject(k)
                    players.add(Player(po.optString("n", "PLAYER"), po.getString("u")))
                }
                eps.add(Episode(eo.getString("l"), players))
            }
            list.add(
                Title(
                    slug = o.getString("u"),
                    title = o.getString("t"),
                    img = if (o.isNull("i")) null else o.optString("i"),
                    episodes = eps
                )
            )
        }
        return list
    }

    private fun readAll(input: java.io.InputStream): String =
        ByteArrayOutputStream().also { input.copyTo(it, 1 shl 16) }.toString("UTF-8")

    private fun isGzip(f: File): Boolean =
        f.length() > 2 && f.inputStream().use { s ->
            val h = ByteArray(2)
            s.read(h) == 2 && h[0] == 0x1f.toByte() && h[1] == 0x8b.toByte()
        }

    private fun open(url: String): HttpURLConnection {
        val c = URL(url).openConnection() as HttpURLConnection
        c.connectTimeout = 15000
        c.readTimeout = 30000
        c.instanceFollowRedirects = true
        return c
    }

    private fun httpGet(url: String): String? = try {
        open(url).apply { setRequestProperty("User-Agent", "CartoonMania/1.0") }.let { c ->
            try { c.inputStream.bufferedReader().use { it.readText() } } finally { c.disconnect() }
        }
    } catch (_: Exception) {
        null
    }

    private fun downloadTo(url: String, dest: File) {
        val c = open(url)
        try {
            c.inputStream.use { input ->
                dest.outputStream().use { output -> input.copyTo(output, 1 shl 16) }
            }
        } finally {
            c.disconnect()
        }
    }
}
