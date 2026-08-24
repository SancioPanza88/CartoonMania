package com.cartoonmania.app

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.ImageView
import android.widget.ListView
import android.widget.TextView

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

        findViewById<TextView>(R.id.d_title).text = t.title
        findViewById<TextView>(R.id.d_count).text =
            if (t.episodes.isEmpty()) getString(R.string.no_episodes)
            else resources.getQuantityString(R.plurals.episodes_count, t.episodes.size, t.episodes.size)
        ImageLoader.display(findViewById(R.id.d_poster), t.img)

        val list = findViewById<ListView>(R.id.d_list)
        adapter = EpisodeAdapter()
        list.adapter = adapter
        list.setOnItemClickListener { _, _, pos, _ ->
            val ep = t.episodes.getOrNull(pos) ?: return@setOnItemClickListener
            if (ep.players.size == 1) {
                openPlayer(ep.label, ep.players[0])
            } else {
                val names = ep.players.map { "${it.name} (${it.url.substringAfter("//").substringBefore("/")})" }
                AlertDialog.Builder(this)
                    .setTitle(ep.label.ifEmpty { getString(R.string.choose_player) })
                    .setItems(names.toTypedArray()) { _, which -> openPlayer(ep.label, ep.players[which]) }
                    .setNegativeButton(android.R.string.cancel, null)
                    .show()
            }
        }
    }

    private fun openPlayer(label: String, p: CatalogRepo.Player) {
        startActivity(
            Intent(this, PlayerActivity::class.java)
                .putExtra("url", p.url)
                .putExtra("label", label)
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
