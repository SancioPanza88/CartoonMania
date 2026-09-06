package com.cartoonmania.app

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.TextView
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

/** Le tue statistiche: riepilogo, barre 7 giorni, ciambella generi,
 *  barre per serie + lista dettaglio con copertine.
 *  Il tempo parte dalla versione che introduce il tracking (niente retroattivo).
 *  Riusa layout/righe delle categorie + viste custom senza dipendenze. */
class StatsActivity : Activity() {

    private data class Row(
        val slug: String,
        val title: String,
        val img: String?,
        val secs: Long,
        val done: Int
    )

    private val rows = ArrayList<Row>()
    private lateinit var adapter: StatsAdapter

    private lateinit var head: LinearLayout
    private lateinit var sumHours: TextView
    private lateinit var sumSeries: TextView
    private lateinit var sumEps: TextView
    private lateinit var weekBars: BarsView
    private lateinit var genreDonut: DonutView
    private lateinit var genreLegend: LinearLayout
    private lateinit var seriesBars: BarsView

    private val palette = listOf(
        0xFF7C5CFC.toInt(), 0xFF00BFA6.toInt(), 0xFFFF7043.toInt(),
        0xFF42A5F5.toInt(), 0xFFEC407A.toInt(), 0xFFFFAB00.toInt()
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_category)

        findViewById<TextView>(R.id.c_title).text = getString(R.string.stats_title)
        findViewById<View>(R.id.c_back).setOnClickListener { finish() }

        val list = findViewById<ListView>(R.id.c_list)
        head = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(4), dp(16), dp(4))
        }
        buildHead()
        // Header PRIMA dell'adapter (regola ListView)
        list.addHeaderView(head)
        adapter = StatsAdapter()
        list.adapter = adapter
        list.setOnItemClickListener { _, _, pos, _ ->
            // pos include l'header
            val r = rows.getOrNull(pos - list.headerViewsCount) ?: return@setOnItemClickListener
            if (CatalogRepo.titles.any { it.slug == r.slug }) {
                startActivity(Intent(this, DetailActivity::class.java).putExtra("slug", r.slug))
            }
        }

        refresh()
        if (CatalogRepo.titles.isEmpty()) {
            Thread {
                try { CatalogRepo.loadLocal(this) } catch (_: Exception) { }
                runOnUiThread { refresh() }
            }.start()
        }
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (keyCode == KeyEvent.KEYCODE_ESCAPE) {
            onBackPressed()
            return true
        }
        return super.onKeyDown(keyCode, event)
    }

    private fun sectionTitle(text: String): TextView =
        TextView(this).apply {
            this.text = text
            textSize = 15f
            setTextColor(0xFFFFFFFF.toInt())
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            setPadding(0, dp(18), 0, dp(6))
        }

    private fun buildHead() {
        // Riepilogo 3 numeri
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setBackgroundResource(R.drawable.bg_episode_item)
            setPadding(dp(8), dp(12), dp(8), dp(12))
        }
        sumHours = TextView(this)
        sumSeries = TextView(this)
        sumEps = TextView(this)
        for ((v, label) in listOf(
            sumHours to getString(R.string.stats_l_hours),
            sumSeries to getString(R.string.stats_l_series),
            sumEps to getString(R.string.stats_l_eps)
        )) {
            val col = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            }
            v.apply {
                textSize = 19f
                setTextColor(0xFFFFFFFF.toInt())
                setTypeface(typeface, android.graphics.Typeface.BOLD)
                gravity = Gravity.CENTER
            }
            col.addView(v)
            col.addView(TextView(this).apply {
                text = label
                textSize = 11f
                setTextColor(0xFFA0A4B8.toInt())
                gravity = Gravity.CENTER
            })
            card.addView(col)
        }
        head.addView(card)

        head.addView(sectionTitle(getString(R.string.stats_week)))
        weekBars = BarsView(this).apply { colors = listOf(palette[1]) }
        head.addView(weekBars)

        head.addView(sectionTitle(getString(R.string.stats_genres)))
        genreDonut = DonutView(this).apply { colors = palette }
        head.addView(genreDonut)
        genreLegend = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        head.addView(genreLegend)

        head.addView(sectionTitle(getString(R.string.stats_series)))
        seriesBars = BarsView(this).apply { colors = palette }
        head.addView(seriesBars)
    }

    private fun refresh() {
        rows.clear()
        var total = 0L
        var epsTotal = 0
        try {
            val me = Profiles.current(this)
            val bySlug = CatalogRepo.titles.associateBy { it.slug }

            // Righe per serie
            for ((slug, secs) in me.watch.entries.sortedByDescending { it.value }) {
                if (secs <= 0) continue
                val t = bySlug[slug]
                rows.add(Row(slug, t?.title ?: slug, t?.img, secs, me.done[slug] ?: 0))
            }
            total = rows.sumOf { it.secs }
            epsTotal = me.done.values.sum()

            // Ultimi 7 giorni (ore)
            val dayFmt = SimpleDateFormat("yyyy-MM-dd", Locale.US)
            val labFmt = SimpleDateFormat("EE", Locale.ITALIAN)
            val cal = Calendar.getInstance()
            val week = ArrayList<Pair<String, Float>>()
            for (back in 6 downTo 0) {
                cal.timeInMillis = System.currentTimeMillis()
                cal.add(Calendar.DAY_OF_YEAR, -back)
                val key = dayFmt.format(cal.time)
                val h = (me.watchDay[key] ?: 0L) / 3600f
                var lab = labFmt.format(cal.time)
                lab = lab.replace(".", "").take(3)
                week.add(lab to h)
            }
            weekBars.items = week

            // Generi (ore piene a ogni genere della serie, top 6)
            val byCat = LinkedHashMap<String, Long>()
            for (r in rows) {
                val cats = bySlug[r.slug]?.cats ?: emptyList()
                for (c in cats) {
                    if (c.equals("ITA", true) || c.equals("Sub-Ita", true)) continue
                    byCat[c] = (byCat[c] ?: 0L) + r.secs
                }
            }
            val topCats = byCat.entries.sortedByDescending { it.value }.take(6)
            genreDonut.items = topCats.map { it.key to it.value / 3600f }
            genreLegend.removeAllViews()
            val totH = total / 3600f
            topCats.forEachIndexed { i, (name, secs) ->
                val pct = if (totH > 0) (secs / 3600f / totH * 100).toInt() else 0
                val row = LinearLayout(this).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                    setPadding(0, dp(3), 0, dp(3))
                }
                row.addView(View(this).apply {
                    setBackgroundColor(palette[i % palette.size])
                    layoutParams = LinearLayout.LayoutParams(dp(12), dp(12))
                    Ui.round(this, 6)
                })
                row.addView(TextView(this).apply {
                    text = "  $name  $pct%"
                    textSize = 13f
                    setTextColor(0xFFFFFFFF.toInt())
                })
                genreLegend.addView(row)
            }

            // Barre per serie (top 8)
            seriesBars.items = rows.take(8).map { it.title to it.secs / 3600f }
        } catch (_: Exception) {
        }
        sumHours.text = fmt(total)
        sumSeries.text = "${rows.size}"
        sumEps.text = "$epsTotal"
        findViewById<TextView>(R.id.c_count).text =
            if (rows.isEmpty()) getString(R.string.stats_empty)
            else getString(R.string.stats_total, fmt(total), rows.size)
        adapter.notifyDataSetChanged()
    }

    private fun fmt(s: Long): String {
        val h = s / 3600
        val m = (s % 3600) / 60
        return if (h > 0) "${h}h ${m}m" else "${m}m"
    }

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

    private inner class StatsAdapter : BaseAdapter() {
        override fun getCount() = rows.size
        override fun getItem(position: Int) = rows[position]
        override fun getItemId(position: Int) = position.toLong()

        override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
            val v = convertView ?: layoutInflater.inflate(R.layout.item_title, parent, false)
            val title: TextView = v.findViewById(R.id.t_title)
            val sub: TextView = v.findViewById(R.id.t_sub)
            val poster: ImageView = v.findViewById(R.id.t_poster)
            val r = rows[position]
            title.text = r.title
            sub.text = if (r.done > 0) {
                "${fmt(r.secs)} · " + resources.getQuantityString(R.plurals.episodes_count, r.done, r.done)
            } else {
                fmt(r.secs)
            }
            Ui.round(poster, 8)
            ImageLoader.display(poster, r.img)
            return v
        }
    }
}
