package com.cartoonmania.app

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.ImageView
import android.widget.ListView
import android.widget.TextView

/** Le tue statistiche: ore per serie del profilo corrente, con copertine.
 *  Il tempo parte dalla versione che introduce il tracking (niente retroattivo).
 *  Riusa layout/righe delle categorie: niente nuovo XML. */
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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_category)

        findViewById<TextView>(R.id.c_title).text = getString(R.string.stats_title)
        findViewById<View>(R.id.c_back).setOnClickListener { finish() }

        val list = findViewById<ListView>(R.id.c_list)
        adapter = StatsAdapter()
        list.adapter = adapter
        list.setOnItemClickListener { _, _, pos, _ ->
            val r = rows.getOrNull(pos) ?: return@setOnItemClickListener
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

    private fun refresh() {
        rows.clear()
        try {
            val me = Profiles.current(this)
            val bySlug = CatalogRepo.titles.associateBy { it.slug }
            val sorted = me.watch.entries.sortedByDescending { it.value }
            for ((slug, secs) in sorted) {
                if (secs <= 0) continue
                val t = bySlug[slug]
                rows.add(
                    Row(
                        slug,
                        t?.title ?: slug,
                        t?.img,
                        secs,
                        me.done[slug] ?: 0
                    )
                )
            }
        } catch (_: Exception) {
        }
        val total = rows.sumOf { it.secs }
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
