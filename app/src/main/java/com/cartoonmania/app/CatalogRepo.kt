package com.cartoonmania.app

import android.content.Context
import android.util.JsonReader
import android.util.JsonToken
import java.io.File
import java.io.InputStream
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.util.zip.GZIPInputStream

object CatalogRepo {

    const val RAW_BASE = "https://raw.githubusercontent.com/SancioPanza88/CartoonMania/main"
    const val VERSION_URL = "$RAW_BASE/data/catalog.version.txt"
    const val CATALOG_URL = "$RAW_BASE/data/catalog.json.gz"

    data class Player(val name: String, val url: String)
    data class Episode(val label: String, val players: List<Player>)
    data class Title(
        val slug: String,
        val title: String,
        val img: String?,
        val cats: List<String>,
        val modified: String,
        val episodes: List<Episode>
    )

    @Volatile
    var titles: List<Title> = emptyList()
        private set

    private fun cacheFile(ctx: Context) = File(ctx.filesDir, "catalog.json.gz")
    private fun versionFile(ctx: Context) = File(ctx.filesDir, "catalog.version")

    /**
     * Carica il catalogo dagli asset integrati.
     * Prova piu' nomi perche' il packaging Android puo' rinominare gli asset .gz.
     * Rileva automaticamente se il contenuto e' gzip o json plain.
     */
    @Synchronized
    fun loadLocal(ctx: Context): List<Title> {
        val cached = cacheFile(ctx)
        if (cached.exists() && cached.length() > 0) {
            try {
                val t = parseAuto(cached.inputStream())
                if (t.isNotEmpty()) { titles = t; return titles }
            } catch (_: Exception) {
            }
            cached.delete()
        }
        val candidates = listOf("catalog.cm", "catalog.json.gz", "catalog.json", "catalog.cm.gz")
        var lastErr: Exception? = null
        for (name in candidates) {
            try {
                ctx.assets.open(name).use { raw ->
                    val t = parseAuto(raw)
                    if (t.isNotEmpty()) { titles = t; return titles }
                }
            } catch (e: Exception) {
                lastErr = e
            }
        }
        throw IllegalStateException("Asset catalogo non trovato nell'APK", lastErr)
    }

    /** Rileva la magia gzip 1F 8B e decodifica di conseguenza. */
    private fun parseAuto(input: InputStream): List<Title> {
        val pb = java.io.PushbackInputStream(input.buffered(1 shl 16), 2)
        val b1 = pb.read()
        val b2 = pb.read()
        pb.unread(byteArrayOf(b1.toByte(), b2.toByte()))
        val stream: InputStream =
            if (b1 == 0x1f && b2 == 0x8b) GZIPInputStream(pb, 1 shl 16) else pb
        InputStreamReader(stream, Charsets.UTF_8).use { isr ->
            JsonReader(isr).use { reader -> return parseStream(reader) }
        }
    }

    fun currentVersion(ctx: Context): String {
        val f = versionFile(ctx)
        return if (f.exists()) f.readText().trim() else "0"
    }

    @Synchronized
    fun refresh(ctx: Context): String {
        val remoteVer = httpGet(VERSION_URL)?.trim() ?: return "Rete non disponibile"
        if (remoteVer.isEmpty()) return "Nessun aggiornamento"
        val cacheOk = cacheFile(ctx).exists() && cacheFile(ctx).length() > 0
        if (cacheOk && titles.isNotEmpty() && remoteVer == currentVersion(ctx)) {
            return "Catalogo gia' aggiornato"
        }
        val tmp = File(ctx.filesDir, "catalog.tmp.gz")
        try {
            downloadTo(CATALOG_URL, tmp)
            if (!isGzip(tmp)) return "File remoto non valido"
            val newTitles = parseAuto(tmp.inputStream())
            if (newTitles.isEmpty()) return "Catalogo remoto vuoto"
            cacheFile(ctx).delete()
            if (!tmp.renameTo(cacheFile(ctx))) {
                tmp.copyTo(cacheFile(ctx), overwrite = true)
                tmp.delete()
            }
            versionFile(ctx).writeText(remoteVer)
            titles = newTitles
            return "Aggiornato: ${titles.size} titoli"
        } finally {
            if (tmp.exists()) tmp.delete()
        }
    }

    internal fun parseStream(reader: JsonReader): List<Title> {
        val list = ArrayList<Title>(3200)
        reader.beginObject()
        while (reader.hasNext()) {
            when (reader.nextName()) {
                "s" -> {
                    reader.beginArray()
                    while (reader.hasNext()) list.add(readTitle(reader))
                    reader.endArray()
                }
                else -> reader.skipValue()
            }
        }
        reader.endObject()
        return list
    }

    private fun readTitle(r: JsonReader): Title {
        var slug = ""
        var title = ""
        var img: String? = null
        var cats: List<String> = emptyList()
        var modified = ""
        var eps: List<Episode> = emptyList()
        r.beginObject()
        while (r.hasNext()) {
            when (r.nextName()) {
                "u" -> slug = r.nextString()
                "t" -> title = r.nextString()
                "i" -> img = if (r.peek() == JsonToken.NULL) { r.nextNull(); null } else r.nextString()
                "m" -> modified = r.nextString()
                "c" -> cats = when (r.peek()) {
                    JsonToken.NULL -> { r.nextNull(); emptyList() }
                    JsonToken.STRING -> listOf(r.nextString().intern())
                    else -> readStringArray(r)
                }
                "e" -> {
                    val list = ArrayList<Episode>()
                    r.beginArray()
                    while (r.hasNext()) list.add(readEpisode(r))
                    r.endArray()
                    eps = list
                }
                else -> r.skipValue()
            }
        }
        r.endObject()
        return Title(slug, title, img, cats, modified, eps)
    }

    private fun readStringArray(r: JsonReader): List<String> {
        val out = ArrayList<String>(4)
        r.beginArray()
        while (r.hasNext()) out.add(r.nextString().intern())
        r.endArray()
        return out
    }

    private fun readEpisode(r: JsonReader): Episode {
        var label = ""
        var players: List<Player> = emptyList()
        r.beginObject()
        while (r.hasNext()) {
            when (r.nextName()) {
                "l" -> label = r.nextString()
                "p" -> {
                    val list = ArrayList<Player>()
                    r.beginArray()
                    while (r.hasNext()) list.add(readPlayer(r))
                    r.endArray()
                    players = list
                }
                else -> r.skipValue()
            }
        }
        r.endObject()
        return Episode(label.intern(), players)
    }

    private fun readPlayer(r: JsonReader): Player {
        var name = "PLAYER"
        var url = ""
        r.beginObject()
        while (r.hasNext()) {
            when (r.nextName()) {
                "n" -> name = r.nextString().intern()
                "u" -> url = r.nextString()
                else -> r.skipValue()
            }
        }
        r.endObject()
        return Player(name, url)
    }

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
