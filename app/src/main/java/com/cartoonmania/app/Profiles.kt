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
        val recent: ArrayList<String> = ArrayList()
    )

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
                out.add(p)
            }
        } catch (_: Exception) {
        }
        if (out.isEmpty()) {
            out.add(Profile("p1", "Famiglia", COLORS[0]))
        }
        return out
    }

    private fun persist(ctx: Context, list: List<Profile>) {
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
                arr.put(o)
            }
            prefs(ctx).edit().putString("list", arr.toString()).apply()
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
        } catch (_: Exception) {
        }
    }
}
