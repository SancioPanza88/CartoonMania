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
import android.widget.ListView
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast

class MainActivity : Activity() {

    private lateinit var adapter: TitleAdapter
    private val shown = ArrayList<CatalogRepo.Title>()
    private lateinit var status: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val search = findViewById<EditText>(R.id.search)
        val list = findViewById<ListView>(R.id.list)
        status = findViewById(R.id.status)
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

        Thread {
            CatalogRepo.loadLocal(this)
            runOnUiThread {
                refilter(search.text?.toString().orEmpty())
                status.text = getString(R.string.checking_updates)
                Thread {
                    val msg = try { CatalogRepo.refresh(this) } catch (e: Exception) { "Aggiornamento fallito" }
                    runOnUiThread {
                        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
                        refilter(search.text?.toString().orEmpty())
                        if (CatalogRepo.titles.isEmpty()) status.text = getString(R.string.no_data)
                        else status.visibility = View.GONE
                    }
                }.start()
            }
        }.start()
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
        status.visibility = View.VISIBLE
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
            val t = shown[position]
            title.text = t.title
            sub.text = when {
                t.episodes.isEmpty() -> "Nessun episodio"
                else -> "${t.episodes.size} episodi"
            }
            return v
        }
    }
}
