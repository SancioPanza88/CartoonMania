package com.cartoonmania.app

import android.app.Activity
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.ImageView
import android.widget.ListView
import android.widget.TextView

class CategoryActivity : Activity() {

    private lateinit var adapter: CatAdapter
    private val shown = ArrayList<CatalogRepo.Title>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_category)

        val cat = intent.getStringExtra("cat").orEmpty()
        findViewById<TextView>(R.id.c_title).text = cat.ifEmpty { getString(R.string.no_data) }
        findViewById<View>(R.id.c_back).setOnClickListener { finish() }

        val list = findViewById<ListView>(R.id.c_list)
        adapter = CatAdapter()
        list.adapter = adapter

        refresh()
        if (CatalogRepo.titles.isEmpty()) {
            Thread {
                try { CatalogRepo.loadLocal(this) } catch (_: Exception) { }
                runOnUiThread { refresh() }
            }.start()
        }
    }

    private fun refresh() {
        val cat = intent.getStringExtra("cat").orEmpty()
        shown.clear()
        for (t in CatalogRepo.titles) if (t.cats.contains(cat)) shown.add(t)
        shown.sortByDescending { it.modified }
        findViewById<TextView>(R.id.c_count).text =
            resources.getQuantityString(R.plurals.titles_count, shown.size, shown.size)
        adapter.notifyDataSetChanged()
    }

    private inner class CatAdapter : BaseAdapter() {
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
