package com.cartoonmania.app

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.widget.ImageView
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * Profili stile Netflix: ognuno ha nome, colore/avatar, preferiti e recenti.
 * Tutto in locale (SharedPreferences + file), nessun account.
 */
object Profiles {

    data class Profile(
        val id: String,
        var name: String,
        var color: Int,
        val favorites: LinkedHashSet<String> = LinkedHashSet(),
        val recent: ArrayList<String> = ArrayList(),
        val progress: LinkedHashMap<String, Prog> = LinkedHashMap(),
        val watch: LinkedHashMap<String, Long> = LinkedHashMap(),
        val done: LinkedHashMap<String, Int> = LinkedHashMap(),
        val watchDay: LinkedHashMap<String, Long> = LinkedHashMap()
    )

    data class Prog(val ep: Int, val pi: Int, val pos: Long, val label: String)

    val COLORS = intArrayOf(
        0xFF7C5CFC.toInt(), 0xFF00BFA6.toInt(), 0xFFFF7043.toInt(),
        0xFF42A5F5.toInt(), 0xFFEC407A.toInt(), 0xFFFFAB00.toInt()
    )

    const val MAX_PROFILES = 6
    private const val MAX_RECENT = 20

    private fun prefs(ctx: Context) =
        ctx.getSharedPreferences("profiles", Context.MODE_PRIVATE)

    fun all(ctx: Context): MutableList<Profile> {
        val out = ArrayList<Profile>()
        try {
            val arr = JSONArray(prefs(ctx).getString("list", "[]"))
            for (i in 0 until arr.length()) {
                val o = arr.optJSONObject(i) ?: continue
                val id = o.optString("id")
                if (id.isEmpty()) continue
                val p = Profile(
                    id = id,
                    name = o.optString("name", "?"),
                    color = o.optInt("color", COLORS[0])
                )
                val f = o.optJSONArray("fav")
                if (f != null) for (j in 0 until f.length()) p.favorites.add(f.optString(j))
                val r = o.optJSONArray("recent")
                if (r != null) for (j in 0 until r.length()) {
                    val s = r.optString(j)
                    if (s.isNotEmpty()) p.recent.add(s)
                }
                val g = o.optJSONObject("prog")
                if (g != null) {
                    val keys = g.keys()
                    while (keys.hasNext()) {
                        val s = keys.next()
                        val e = g.optJSONObject(s) ?: continue
                        p.progress[s] = Prog(
                            e.optInt("e", 0), e.optInt("pi", 0),
                            e.optLong("pos", 0), e.optString("l", "")
                        )
                    }
                }
                val w = o.optJSONObject("watch")
                if (w != null) {
                    val keys = w.keys()
                    while (keys.hasNext()) {
                        val s = keys.next()
                        val sec = w.optLong(s, 0)
                        if (s.isNotEmpty() && sec > 0) p.watch[s] = sec
                    }
                }
                val d = o.optJSONObject("done")
                if (d != null) {
                    val keys = d.keys()
                    while (keys.hasNext()) {
                        val s = keys.next()
                        val n = d.optInt(s, 0)
                        if (s.isNotEmpty() && n > 0) p.done[s] = n
                    }
                }
                val wd = o.optJSONObject("wd")
                if (wd != null) {
                    val keys = wd.keys()
                    while (keys.hasNext()) {
                        val s = keys.next()
                        val sec = wd.optLong(s, 0)
                        if (s.isNotEmpty() && sec > 0) p.watchDay[s] = sec
                    }
                }
                out.add(p)
            }
        } catch (_: Exception) {
        }
        if (out.isEmpty()) {
            out.add(Profile("p1", "Famiglia", COLORS[0]))
        }
        return out
    }

    private fun persist(ctx: Context, list: List<Profile>, sync: Boolean = false) {
        try {
            val arr = JSONArray()
            for (p in list) {
                val o = JSONObject()
                o.put("id", p.id)
                o.put("name", p.name)
                o.put("color", p.color)
                val f = JSONArray()
                for (s in p.favorites) f.put(s)
                o.put("fav", f)
                val r = JSONArray()
                for (s in p.recent) r.put(s)
                o.put("recent", r)
                val g = JSONObject()
                for ((s, pr) in p.progress) {
                    val e = JSONObject()
                    e.put("e", pr.ep)
                    e.put("pi", pr.pi)
                    e.put("pos", pr.pos)
                    e.put("l", pr.label)
                    g.put(s, e)
                }
                o.put("prog", g)
                val w = JSONObject()
                for ((s, sec) in p.watch) w.put(s, sec)
                o.put("watch", w)
                val d = JSONObject()
                for ((s, n) in p.done) d.put(s, n)
                o.put("done", d)
                val wd = JSONObject()
                for ((s, sec) in p.watchDay) wd.put(s, sec)
                o.put("wd", wd)
                arr.put(o)
            }
            val ed = prefs(ctx).edit().putString("list", arr.toString())
            if (sync) ed.commit() else ed.apply()
        } catch (_: Exception) {
        }
    }

    /** Scrittura sincrona: lo stato sopravvive anche se il processo muore
     *  subito dopo (chiusura forzata mentre guardi qualcosa). */
    fun flushNow(ctx: Context) {
        try {
            persist(ctx, all(ctx), sync = true)
        } catch (_: Exception) {
        }
    }

    fun current(ctx: Context): Profile {
        val list = all(ctx)
        val id = prefs(ctx).getString("current", null)
        return list.firstOrNull { it.id == id } ?: list[0]
    }

    fun switch(ctx: Context, id: String) {
        prefs(ctx).edit().putString("current", id).apply()
    }

    fun create(ctx: Context, name: String): Profile? {
        val list = all(ctx)
        if (list.size >= MAX_PROFILES) return null
        val p = Profile(
            id = "p${System.currentTimeMillis()}",
            name = name.ifBlank { "Profilo ${list.size + 1}" },
            color = COLORS[list.size % COLORS.size]
        )
        list.add(p)
        persist(ctx, list)
        return p
    }

    fun delete(ctx: Context, id: String): Boolean {
        val list = all(ctx)
        if (list.size <= 1) return false
        val p = list.firstOrNull { it.id == id } ?: return false
        list.remove(p)
        try {
            if (prefs(ctx).getString("current", null) == id) {
                prefs(ctx).edit().putString("current", list[0].id).apply()
            }
        } catch (_: Exception) {
        }
        persist(ctx, list)
        try {
            avatarFile(ctx, id).delete()
        } catch (_: Exception) {
        }
        return true
    }

    fun update(ctx: Context, p: Profile) {
        val list = all(ctx)
        val i = list.indexOfFirst { it.id == p.id }
        if (i >= 0) {
            list[i] = p
            persist(ctx, list)
        }
    }

    fun isFavorite(ctx: Context, slug: String): Boolean =
        current(ctx).favorites.contains(slug)

    /** Ritorna true se ora e' tra i preferiti. */
    fun toggleFavorite(ctx: Context, slug: String): Boolean {
        val list = all(ctx)
        val cur = list.firstOrNull { it.id == prefs(ctx).getString("current", null) } ?: list[0]
        val now = if (cur.favorites.contains(slug)) {
            cur.favorites.remove(slug)
            false
        } else {
            cur.favorites.add(slug)
            true
        }
        persist(ctx, list)
        return now
    }

    fun touchRecent(ctx: Context, slug: String) {
        try {
            val list = all(ctx)
            val cur = list.firstOrNull { it.id == prefs(ctx).getString("current", null) } ?: list[0]
            cur.recent.remove(slug)
            cur.recent.add(0, slug)
            while (cur.recent.size > MAX_RECENT) cur.recent.removeAt(cur.recent.size - 1)
            persist(ctx, list)
        } catch (_: Exception) {
        }
    }

    /** Salva dove sei arrivato (riprende in testa alla lista). Max 50 show. */
    fun saveProgress(ctx: Context, slug: String, ep: Int, pi: Int, pos: Long, label: String) {
        try {
            val list = all(ctx)
            val cur = list.firstOrNull { it.id == prefs(ctx).getString("current", null) } ?: list[0]
            cur.progress.remove(slug)
            cur.progress[slug] = Prog(ep, pi, pos, label)
            while (cur.progress.size > 50) {
                cur.progress.entries.iterator().let {
                    if (it.hasNext()) {
                        it.next()
                        it.remove()
                    }
                }
            }
            persist(ctx, list)
        } catch (_: Exception) {
        }
    }

    fun clearProgress(ctx: Context, slug: String) {
        try {
            val list = all(ctx)
            val cur = list.firstOrNull { it.id == prefs(ctx).getString("current", null) } ?: list[0]
            if (cur.progress.remove(slug) != null) persist(ctx, list)
        } catch (_: Exception) {
        }
    }

    /** Togli dagli ultimamente visti (es. show finito e segnato come visto). */
    fun removeRecent(ctx: Context, slug: String) {
        try {
            val list = all(ctx)
            val cur = list.firstOrNull { it.id == prefs(ctx).getString("current", null) } ?: list[0]
            if (cur.recent.remove(slug)) persist(ctx, list)
        } catch (_: Exception) {
        }
    }

    fun progressOf(ctx: Context, slug: String): Prog? {
        return try {
            current(ctx).progress[slug]
        } catch (_: Exception) {
            null
        }
    }

    /** Svuota in blocco le liste del profilo corrente. Ritorna quante voci tolte. */
    fun clearLists(ctx: Context, fav: Boolean, rec: Boolean, prog: Boolean): Int {
        return try {
            val list = all(ctx)
            val cur = list.firstOrNull { it.id == prefs(ctx).getString("current", null) } ?: list[0]
            var n = 0
            if (fav) {
                n += cur.favorites.size
                cur.favorites.clear()
            }
            if (rec) {
                n += cur.recent.size
                cur.recent.clear()
            }
            if (prog) {
                n += cur.progress.size
                cur.progress.clear()
            }
            if (n > 0) persist(ctx, list)
            n
        } catch (_: Exception) {
            0
        }
    }
    /** Accumula secondi di visione per le statistiche (max 500 serie). */
    fun addWatch(ctx: Context, slug: String, secs: Long) {
        if (secs <= 0) return
        try {
            val list = all(ctx)
            val cur = list.firstOrNull { it.id == prefs(ctx).getString("current", null) } ?: list[0]
            cur.watch[slug] = (cur.watch[slug] ?: 0L) + secs
            while (cur.watch.size > 500) {
                cur.watch.entries.iterator().let {
                    if (it.hasNext()) {
                        it.next()
                        it.remove()
                    }
                }
            }
            try {
                val day = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US)
                    .format(java.util.Date())
                cur.watchDay[day] = (cur.watchDay[day] ?: 0L) + secs
                while (cur.watchDay.size > 120) {
                    cur.watchDay.entries.iterator().let {
                        if (it.hasNext()) {
                            it.next()
                            it.remove()
                        }
                    }
                }
            } catch (_: Exception) {
            }
            persist(ctx, list)
        } catch (_: Exception) {
        }
    }

    /** Un episodio visto fino alla fine (statistiche). */
    fun addEpDone(ctx: Context, slug: String) {
        try {
            val list = all(ctx)
            val cur = list.firstOrNull { it.id == prefs(ctx).getString("current", null) } ?: list[0]
            cur.done[slug] = (cur.done[slug] ?: 0) + 1
            persist(ctx, list)
        } catch (_: Exception) {
        }
    }

    /** Export del profilo corrente per la sincronizzazione tra dispositivi
     *  (QR + Wi-Fi locale, nessuna nuvola). */
    fun exportCurrent(ctx: Context): String {
        val p = current(ctx)
        val o = JSONObject()
        o.put("v", 1)
        o.put("fav", JSONArray(p.favorites.toList()))
        o.put("rec", JSONArray(p.recent.toList()))
        val g = JSONObject()
        for ((s, pr) in p.progress) {
            val e = JSONObject()
            e.put("e", pr.ep)
            e.put("pi", pr.pi)
            e.put("pos", pr.pos)
            e.put("l", pr.label)
            g.put(s, e)
        }
        o.put("prog", g)
        return o.toString()
    }

    /** Import con fusione nel profilo corrente: preferiti e recenti uniti,
     *  i progressi inviati sovrascrivono quelli locali. Ritorna un riepilogo
     *  ("2 preferiti, 3 recenti, 1 progressi") o null se il JSON non e' valido. */
    fun importMerge(ctx: Context, json: String): String? {
        return try {
            val o = JSONObject(json)
            if (o.optInt("v", 0) != 1) return null
            val list = all(ctx)
            val cur = list.firstOrNull { it.id == prefs(ctx).getString("current", null) } ?: list[0]
            var fav = 0
            var rec = 0
            var prog = 0
            val f = o.optJSONArray("fav")
            if (f != null) for (i in 0 until f.length()) {
                val s = f.optString(i)
                if (s.isNotEmpty() && cur.favorites.add(s)) fav++
            }
            val r = o.optJSONArray("rec")
            if (r != null) {
                val merged = ArrayList<String>()
                for (i in 0 until r.length()) {
                    val s = r.optString(i)
                    if (s.isNotEmpty() && s !in merged) {
                        merged.add(s)
                        if (s !in cur.recent) rec++
                    }
                }
                for (s in cur.recent) if (s !in merged) merged.add(s)
                cur.recent.clear()
                cur.recent.addAll(merged.take(MAX_RECENT))
            }
            val g = o.optJSONObject("prog")
            if (g != null) {
                val keys = g.keys()
                while (keys.hasNext()) {
                    val s = keys.next()
                    val e = g.optJSONObject(s) ?: continue
                    val isNew = s !in cur.progress
                    cur.progress.remove(s)
                    cur.progress[s] = Prog(
                        e.optInt("e", 0), e.optInt("pi", 0),
                        e.optLong("pos", 0), e.optString("l", "")
                    )
                    if (isNew) prog++
                }
                while (cur.progress.size > 50) {
                    cur.progress.entries.iterator().let {
                        if (it.hasNext()) {
                            it.next()
                            it.remove()
                        }
                    }
                }
            }
            persist(ctx, list)
            "$fav preferiti, $rec recenti, $prog progressi"
        } catch (_: Exception) {
            null
        }
    }

    fun avatarFile(ctx: Context, id: String): File =
        File(ctx.filesDir, "avatars").apply { mkdirs() }.let { File(it, "$id.jpg") }

    /** Foto scattata/caricata oppure cerchio colorato con iniziale. */
    fun avatarBitmap(ctx: Context, p: Profile, px: Int): Bitmap {
        avatarFile(ctx, p.id).takeIf { it.exists() && it.length() > 0 }?.let { f ->
            try {
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeFile(f.absolutePath, bounds)
                var sample = 1
                while (bounds.outWidth / (sample * 2) >= px && bounds.outHeight / (sample * 2) >= px) sample *= 2
                val opts = BitmapFactory.Options().apply { inSampleSize = sample }
                BitmapFactory.decodeFile(f.absolutePath, opts)?.let { return it }
            } catch (_: Exception) {
            }
        }
        val b = Bitmap.createBitmap(px, px, Bitmap.Config.ARGB_8888)
        val c = Canvas(b)
        val bg = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = p.color }
        c.drawCircle(px / 2f, px / 2f, px / 2f, bg)
        val letter = p.name.trim().firstOrNull()?.uppercase() ?: "?"
        val tp = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFFFFFFFF.toInt()
            textSize = px * 0.45f
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            textAlign = Paint.Align.CENTER
        }
        val fm = tp.fontMetrics
        c.drawText(letter, px / 2f, px / 2f - (fm.ascent + fm.descent) / 2f, tp)
        return b
    }

    fun renderInto(ctx: Context, iv: ImageView, p: Profile) {
        try {
            val px = (96 * ctx.resources.displayMetrics.density).toInt().coerceAtLeast(96)
            iv.setImageBitmap(avatarBitmap(ctx, p, px))
            // Avatar sempre tondi, come la guida della pagina di ritaglio
            Ui.round(iv, 100)
        } catch (_: Exception) {
        }
    }
}
