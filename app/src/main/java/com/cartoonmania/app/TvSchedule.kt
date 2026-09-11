package com.cartoonmania.app

import android.content.Context
import android.content.Intent
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/** Palinsesto lineare deterministico: ogni canale trasmette una sequenza
 *  giornaliera di episodi pescati dal catalogo. Stessa data + stesso canale
 *  = stesso palinsesto su tutti i dispositivi, senza server, anche offline.
 *
 *  Giornata divisa in slot fissi da 30 minuti (le durate reali degli episodi
 *  non sono nel catalogo): "ora in onda", orari inizio/fine e "a seguire"
 *  sono sempre calcolabili. Il join a meta' episodio vale solo per gli
 *  stream diretti; gli embed partono dall'inizio (limite tecnico). */
object TvSchedule {

    const val SLOT_MINUTES = 30L
    const val SLOT_MS = SLOT_MINUTES * 60_000L

    /** Offset massimo di join: oltre i 20 min si parte da zero (evita seek
     *  oltre la fine negli episodi brevi, che manderebbe in loop il retune). */
    const val MAX_JOIN_OFFSET_MS = 20L * 60_000L

    data class Channel(
        val id: String,
        val name: String,
        val match: (CatalogRepo.Title) -> Boolean
    )

    val channels: List<Channel> = listOf(
        Channel("bambini", "Bambini TV") { it.cats.contains("Bambini") },
        Channel("anime", "Anime TV") { t ->
            t.cats.any { c -> c == "Anime" || c == "Shonen" || c == "Shojo" || c == "Seinen" || c == "Mecha" }
        },
        Channel("film", "Film TV") { it.cats.contains("Film Animazione") },
        Channel("mix", "CartoonMania TV") { true }
    )

    data class Airing(
        val channelId: String,
        val channelName: String,
        val title: CatalogRepo.Title,
        val epIndex: Int,
        val slotStartMs: Long,
        val slotEndMs: Long,
        /** Millisecondi da inizio slot: punto di join per gli stream diretti. */
        val offsetMs: Long
    )

    data class PlayerPick(val url: String, val direct: Boolean)

    /** Diretto = file multimediale vero (agganciabile a meta'), non pagina. */
    fun isDirectUrl(url: String): Boolean {
        val low = url.substringBefore('#').lowercase()
        if (low.contains("loonex.eu/guarda")) return false
        return low.contains(".m3u8") || ".mp4?" in low || low.endsWith(".mp4") ||
            low.endsWith(".mkv") || low.contains("/hls/")
    }

    /** Player preferito dell'episodio: diretto se c'e', altrimenti il primo. */
    fun playerFor(t: CatalogRepo.Title, epIndex: Int): PlayerPick? {
        val ep = t.episodes.getOrNull(epIndex) ?: return null
        ep.players.firstOrNull { isDirectUrl(it.url) }?.let { return PlayerPick(it.url, true) }
        ep.players.firstOrNull { it.url.isNotEmpty() }?.let { return PlayerPick(it.url, false) }
        return null
    }

    fun channel(id: String): Channel? = channels.firstOrNull { it.id == id }

    private fun dayKey(nowMs: Long): Long = try {
        SimpleDateFormat("yyyyMMdd", Locale.US).format(Date(nowMs)).toLong()
    } catch (_: Exception) {
        nowMs / 86_400_000L
    }

    private fun startOfDay(nowMs: Long): Long = try {
        val c = Calendar.getInstance()
        c.timeInMillis = nowMs
        c.set(Calendar.HOUR_OF_DAY, 0)
        c.set(Calendar.MINUTE, 0)
        c.set(Calendar.SECOND, 0)
        c.set(Calendar.MILLISECOND, 0)
        c.timeInMillis
    } catch (_: Exception) {
        nowMs - (nowMs % 86_400_000L)
    }

    /** Episodio in onda sul canale all'istante dato (null se palinsesto vuoto). */
    fun current(channelId: String, titles: List<CatalogRepo.Title>, nowMs: Long = System.currentTimeMillis()): Airing? {
        val ch = channel(channelId) ?: return null
        if (titles.isEmpty()) return null
        val pool = titles.filter { ch.match(it) && it.episodes.isNotEmpty() }.sortedBy { it.slug }
        if (pool.isEmpty()) return null
        val dayStart = startOfDay(nowMs)
        val slotIdx = ((nowMs - dayStart) / SLOT_MS).toInt().coerceAtLeast(0)
        val slotStart = dayStart + slotIdx * SLOT_MS
        var i = 0
        while (i < 12) {
            val rnd = kotlin.random.Random(dayKey(nowMs) * 31 + channelId.hashCode() * 17 + slotIdx * 101 + i)
            val t = pool[rnd.nextInt(pool.size)]
            val epIdx = rnd.nextInt(t.episodes.size)
            if (playerFor(t, epIdx) != null) {
                return Airing(ch.id, ch.name, t, epIdx, slotStart, slotStart + SLOT_MS, (nowMs - slotStart).coerceAtLeast(0))
            }
            i++
        }
        return null
    }

    /** Slot successivo: per la riga "A seguire". */
    fun next(channelId: String, titles: List<CatalogRepo.Title>, nowMs: Long = System.currentTimeMillis()): Airing? {
        val ch = channel(channelId) ?: return null
        val dayStart = startOfDay(nowMs)
        val slotIdx = ((nowMs - dayStart) / SLOT_MS).toInt().coerceAtLeast(0)
        return current(channelId, titles, dayStart + (slotIdx + 1) * SLOT_MS + 1_000L)
    }

    fun joinOffset(a: Airing): Long =
        if (a.offsetMs > MAX_JOIN_OFFSET_MS) 0L else a.offsetMs

    fun fmtTime(ms: Long): String = try {
        SimpleDateFormat("HH:mm", Locale.ITALY).format(Date(ms))
    } catch (_: Exception) {
        ""
    }

    /** Sintonia condivisa (TvActivity + riga "Riprendi"): diretti dal punto
     *  in onda, embed dall'inizio. Salva il canale per la continuita'. */
    fun tuneTo(ctx: Context, a: Airing) {
        try {
            val pick = playerFor(a.title, a.epIndex) ?: return
            val epLabel = a.title.episodes.getOrNull(a.epIndex)?.label.orEmpty()
            val pos = if (pick.direct) joinOffset(a) else 0L
            TvResume.save(ctx, a.channelId)
            Ui.openScreen(
                ctx,
                Intent(ctx, PlayerActivity::class.java)
                    .putExtra("url", pick.url)
                    .putExtra("label", a.title.title + if (epLabel.isEmpty()) "" else " — $epLabel")
                    .putExtra("tv", a.channelId)
                    .putExtra("pos", pos)
            )
        } catch (_: Exception) {
        }
    }
}

/** Memoria indipendente della diretta: solo il canale (la posizione si
 *  ricalcola dall'orologio al rientro). Scrittura sincrona: sopravvive
 *  alla chiusura forzata. Separata dai progressi episodi: le due
 *  continuita' non si cancellano a vicenda. */
object TvResume {

    private const val PREFS = "cm"
    private const val KEY = "tv_resume"

    fun save(ctx: Context, channelId: String) {
        try {
            ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                .putString(KEY, channelId).commit()
        } catch (_: Exception) {
        }
    }

    fun get(ctx: Context): String? = try {
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, null)
    } catch (_: Exception) {
        null
    }

    fun clear(ctx: Context) {
        try {
            ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                .remove(KEY).commit()
        } catch (_: Exception) {
        }
    }
}
