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
    private fun binFile(ctx: Context) = File(ctx.filesDir, "titles.bin")

    /**
     * Carica il catalogo dagli asset integrati.
     * Prova piu' nomi perche' il packaging Android puo' rinominare gli asset .gz.
     * Rileva automaticamente se il contenuto e' gzip o json plain.
     */
    @Synchronized
    fun loadLocal(ctx: Context): List<Title> {
        val cached = cacheFile(ctx)
        val useCache = cached.exists() && cached.length() > 0
        val key = sourceKey(ctx, cached)
        // Via veloce: lettura binaria dell'ultimo parse valido (millisecondi
        // invece di secondi sulle CPU scarse delle TV)
        try {
            val t = loadBin(ctx, key)
            if (t.isNotEmpty()) { titles = t; return titles }
        } catch (_: Exception) {
        }
        // Via lenta: gzip + JSON, poi salva il bin per le prossime volte
        if (useCache) {
            try {
                val t = parseAuto(cached.inputStream())
                if (t.isNotEmpty()) {
                    titles = t
                    try { writeBin(ctx, key, t) } catch (_: Exception) { }
                    return titles
                }
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
                    if (t.isNotEmpty()) {
                        titles = t
                        try { writeBin(ctx, key, t) } catch (_: Exception) { }
                        return titles
                    }
                }
            } catch (e: Exception) {
                lastErr = e
            }
        }
        throw IllegalStateException("Asset catalogo non trovato nell'APK", lastErr)
    }

    /** Chiave della sorgente dati: se cambia, il bin e' scaduto e si riparsa. */
    @Suppress("DEPRECATION")
    private fun sourceKey(ctx: Context, cached: File): String {
        return if (cached.exists() && cached.length() > 0) {
            "file:${cached.length()}:${cached.lastModified()}"
        } else {
            val appVer = try {
                ctx.packageManager.getPackageInfo(ctx.packageName, 0)?.versionName ?: "?"
            } catch (_: Exception) {
                "?"
            }
            "asset:$appVer"
        }
    }

    private fun writeBin(ctx: Context, key: String, list: List<Title>) {
        val tmp = File(ctx.filesDir, "titles.tmp.bin")
        java.io.DataOutputStream(tmp.outputStream().buffered(1 shl 16)).use { out ->
            out.writeUTF(key)
            out.writeInt(list.size)
            for (t in list) {
                out.writeUTF(t.slug)
                out.writeUTF(t.title)
                out.writeUTF(t.img ?: "")
                out.writeUTF(t.modified)
                out.writeInt(t.cats.size)
                for (c in t.cats) out.writeUTF(c)
                out.writeInt(t.episodes.size)
                for (e in t.episodes) {
                    out.writeUTF(e.label)
                    out.writeInt(e.players.size)
                    for (p in e.players) {
                        out.writeUTF(p.name)
                        out.writeUTF(p.url)
                    }
                }
            }
        }
        val dest = binFile(ctx)
        dest.delete()
        if (!tmp.renameTo(dest)) {
            tmp.copyTo(dest, overwrite = true)
            tmp.delete()
        }
    }

    private fun loadBin(ctx: Context, key: String): List<Title> {
        val f = binFile(ctx)
        if (!f.exists() || f.length() <= 0) return emptyList()
        java.io.DataInputStream(f.inputStream().buffered(1 shl 16)).use { inp ->
            if (inp.readUTF() != key) return emptyList()
            val n = inp.readInt()
            if (n <= 0 || n > 20000) return emptyList()
            val list = ArrayList<Title>(n)
            repeat(n) {
                val slug = inp.readUTF()
                val title = inp.readUTF()
                val imgRaw = inp.readUTF()
                val modified = inp.readUTF()
                val nc = inp.readInt()
                if (nc < 0 || nc > 100) throw IllegalStateException("bin corrotto")
                val cats = ArrayList<String>(nc)
                repeat(nc) { cats.add(inp.readUTF().intern()) }
                val ne = inp.readInt()
                if (ne < 0 || ne > 2000) throw IllegalStateException("bin corrotto")
                val eps = ArrayList<Episode>(ne)
                repeat(ne) {
                    val label = inp.readUTF()
                    val np = inp.readInt()
                    if (np < 0 || np > 100) throw IllegalStateException("bin corrotto")
                    val players = ArrayList<Player>(np)
                    repeat(np) { players.add(Player(inp.readUTF(), inp.readUTF())) }
                    eps.add(Episode(label.intern(), players))
                }
                list.add(Title(slug, title, imgRaw.ifEmpty { null }, cats, modified, eps))
            }
            return list
        }
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
            try { writeBin(ctx, sourceKey(ctx, cacheFile(ctx)), newTitles) } catch (_: Exception) { }
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
