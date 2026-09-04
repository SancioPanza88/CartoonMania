package com.cartoonmania.app

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.TextView
import android.widget.Toast

class DetailActivity : Activity() {

    private var current: CatalogRepo.Title? = null
    private lateinit var adapter: EpisodeAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_detail)

        val slug = intent.getStringExtra("slug").orEmpty()
        val t = CatalogRepo.titles.firstOrNull { it.slug == slug } ?: run {
            finish(); return
        }
        current = t
        Profiles.touchRecent(this, slug)

        val list = findViewById<ListView>(R.id.d_list)
        // Sulla TV testata e lista sono un'unica ListView (niente ScrollView
        // annidata: col D-pad il focus non entrerebbe mai negli episodi).
        // Sul telefono la testata resta dov'era.
        val header: View = if (Ui.isTv(this)) {
            layoutInflater.inflate(R.layout.detail_header, list, false).also {
                list.addHeaderView(it)
                list.addHeaderView(episodesLabel())
            }
        } else {
            findViewById(R.id.d_header)
        }

        header.findViewById<TextView>(R.id.d_title).text = t.title
        header.findViewById<TextView>(R.id.d_count).text =
            if (t.episodes.isEmpty()) t.cats.take(2).joinToString(" · ")
            else resources.getQuantityString(R.plurals.episodes_count, t.episodes.size, t.episodes.size)

        val poster = header.findViewById<ImageView>(R.id.d_poster)
        ImageLoader.display(poster, t.img)
        Ui.round(poster, 12)
        ImageLoader.display(header.findViewById(R.id.d_backdrop), t.img)
        header.findViewById<View>(R.id.d_back).setOnClickListener { finish() }

        val favBtn = header.findViewById<Button>(R.id.d_fav)
        fun refreshFav() {
            favBtn.text = if (Profiles.isFavorite(this, slug)) getString(R.string.fav_remove)
            else getString(R.string.fav_add)
        }
        refreshFav()
        favBtn.setOnClickListener {
            val now = Profiles.toggleFavorite(this, slug)
            refreshFav()
            Toast.makeText(
                this,
                if (now) getString(R.string.fav_added) else getString(R.string.fav_removed),
                Toast.LENGTH_SHORT
            ).show()
        }

        val chips = header.findViewById<LinearLayout>(R.id.d_chips)
        val chipViews = ArrayList<View>()
        for (c in t.cats) {
            val chip = Ui.chip(this, c) { ctx ->
                startActivity(Intent(ctx, CategoryActivity::class.java).putExtra("cat", c))
            }
            val lp = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT
            )
            lp.marginEnd = Ui.dp(this, 8)
            chips.addView(chip, lp)
            chipViews.add(chip)
        }
        Ui.clampHorizontalRow(chipViews)

        adapter = EpisodeAdapter()
        list.adapter = adapter
        list.setOnItemClickListener { _, _, pos, _ ->
            // pos include gli header della lista (solo TV ne ha)
            val epPos = pos - list.headerViewsCount
            val ep = t.episodes.getOrNull(epPos) ?: return@setOnItemClickListener
            if (ep.players.size == 1) {
                openPlayer(epPos, 0)
            } else {
                val names = ep.players.map { "${it.name} (${it.url.substringAfter("//").substringBefore("/")})" }
                AlertDialog.Builder(this)
                    .setTitle(ep.label.ifEmpty { getString(R.string.choose_player) })
                    .setItems(names.toTypedArray()) { _, which -> openPlayer(epPos, which) }
                    .setNegativeButton(android.R.string.cancel, null)
                    .show()
            }
        }
    }

    private fun episodesLabel(): TextView = TextView(this).apply {
        text = getString(R.string.episodes_header)
        textSize = 20f
        setTypeface(typeface, android.graphics.Typeface.BOLD)
        setTextColor(0xFFFFFFFF.toInt())
        val dp = Ui.dp(this@DetailActivity, 1)
        setPadding(48 * dp, 24 * dp, 48 * dp, 12 * dp)
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (keyCode == KeyEvent.KEYCODE_ESCAPE) {
            onBackPressed()
            return true
        }
        return super.onKeyDown(keyCode, event)
    }

    private fun openPlayer(pos: Int, playerIdx: Int) {
        val t = current ?: return
        val ep = t.episodes.getOrNull(pos) ?: return
        val p = ep.players.getOrNull(playerIdx) ?: ep.players.firstOrNull() ?: return
        startActivity(
            Intent(this, PlayerActivity::class.java)
                .putExtra("url", p.url)
                .putExtra("label", ep.label)
                .putExtra("slug", t.slug)
                .putExtra("ep", pos)
                .putExtra("pi", playerIdx)
        )
    }

    private inner class EpisodeAdapter : BaseAdapter() {
        override fun getCount() = current?.episodes?.size ?: 0
        override fun getItem(position: Int) = current?.episodes?.get(position)
        override fun getItemId(position: Int) = position.toLong()

        override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
            val v = convertView ?: layoutInflater.inflate(R.layout.item_episode, parent, false)
            val label: TextView = v.findViewById(R.id.e_label)
            val servers: TextView = v.findViewById(R.id.e_servers)
            val ep = current!!.episodes[position]
            label.text = ep.label.ifEmpty { getString(R.string.play) }
            servers.text = ep.players.joinToString(" · ") { it.name }
            return v
        }
    }
}
