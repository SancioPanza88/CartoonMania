package com.cartoonmania.app

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.EditText
import android.widget.ImageView
import android.widget.ListView
import android.widget.TextView

class SearchActivity : Activity() {

    private lateinit var adapter: TitleAdapter
    private val shown = ArrayList<CatalogRepo.Title>()
    private lateinit var status: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_search)

        val search = findViewById<EditText>(R.id.search)
        val list = findViewById<ListView>(R.id.list)
        status = findViewById(R.id.status)
        findViewById<View>(R.id.btn_tab_home).setOnClickListener { finish() }
        adapter = TitleAdapter()
        list.adapter = adapter
        list.setOnItemClickListener { _, _, pos, _ ->
            val t = shown.getOrNull(pos) ?: return@setOnItemClickListener
            startActivity(Intent(this, DetailActivity::class.java).putExtra("slug", t.slug))
        }
        search.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) = refilter(s?.toString().orEmpty())
            override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
        })

        refilter("")
        if (CatalogRepo.titles.isEmpty()) {
            Thread {
                try { CatalogRepo.loadLocal(this) } catch (_: Exception) { }
                runOnUiThread {
                    if (CatalogRepo.titles.isNotEmpty()) refilter(search.text?.toString().orEmpty())
                }
            }.start()
        }
    }

    private fun refilter(qRaw: String) {
        val q = qRaw.trim().lowercase()
        shown.clear()
        val all = CatalogRepo.titles
        if (q.isEmpty()) {
            shown.addAll(all.take(300))
            status.text = "${all.size} titoli - digita per cercare"
        } else {
            for (t in all) {
                if (t.title.lowercase().contains(q)) {
                    shown.add(t)
                    if (shown.size >= 300) break
                }
            }
            status.text = "${shown.size} risultati per \"$qRaw\""
        }
        adapter.notifyDataSetChanged()
    }

    private inner class TitleAdapter : BaseAdapter() {
        override fun getCount() = shown.size
        override fun getItem(position: Int) = shown[position]
        override fun getItemId(position: Int) = position.toLong()

        override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
            val v = convertView ?: layoutInflater.inflate(R.layout.item_title, parent, false)
            val title: TextView = v.findViewById(R.id.t_title)
            val sub: TextView = v.findViewById(R.id.t_sub)
            val poster: ImageView = v.findViewById(R.id.t_poster)
            val t = shown[position]
            title.text = t.title
            sub.text = when {
                t.episodes.isEmpty() -> t.cats.joinToString(" · ")
                else -> "${t.episodes.size} episodi · ${t.cats.take(3).joinToString(" · ")}"
            }
            Ui.round(poster, 8)
            ImageLoader.display(poster, t.img)
            return v
        }
    }
}
