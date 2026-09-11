package com.cartoonmania.app

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.ImageView
import android.widget.ListView
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast

/** TV in diretta: un canale per riga con poster, titolo in onda, orario
 *  inizio-fine, avanzamento e "a seguire". Click = sintonia. */
class TvActivity : Activity() {

    private data class Row(
        val channelId: String,
        val channelName: String,
        val airing: TvSchedule.Airing?,
        val nextTitle: String,
        val nextStart: String
    )

    private lateinit var adapter: TvAdapter
    private val rows = ArrayList<Row>()
    private val tick = Handler(Looper.getMainLooper())
    private var ticking = false
    private val tickRun = object : Runnable {
        override fun run() {
            if (isFinishing || isDestroyed) return
            refresh()
            tick.postDelayed(this, 30_000L)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_tv)

        val backBtn = findViewById<View>(R.id.tv_back)
        backBtn.setOnClickListener { finish() }
        Ui.tvFocus(backBtn)
        Ui.pressPop(backBtn)

        wireTab(R.id.btn_tab_home, HomeActivity::class.java)
        wireTab(R.id.btn_tab_search, SearchActivity::class.java)
        wireTab(R.id.btn_tab_settings, SettingsActivity::class.java)
        findViewById<View>(R.id.btn_tab_tv).setOnClickListener { refresh() }
        Ui.tvFocus(findViewById(R.id.btn_tab_tv))

        val list = findViewById<ListView>(R.id.tv_list)
        adapter = TvAdapter()
        list.adapter = adapter
        list.setOnItemClickListener { _, _, pos, _ ->
            rows.getOrNull(pos)?.airing?.let { tune(it) }
        }

        refresh()
        if (CatalogRepo.titles.isEmpty()) {
            Thread {
                try {
                    CatalogRepo.loadLocal(this)
                } catch (_: Exception) {
                }
                runOnUiThread { refresh() }
            }.start()
        }
    }

    override fun onResume() {
        super.onResume()
        refresh()
        if (!ticking) {
            ticking = true
            tick.postDelayed(tickRun, 30_000L)
        }
    }

    override fun onPause() {
        ticking = false
        try {
            tick.removeCallbacks(tickRun)
        } catch (_: Exception) {
        }
        super.onPause()
    }

    override fun onDestroy() {
        try {
            tick.removeCallbacks(tickRun)
        } catch (_: Exception) {
        }
        super.onDestroy()
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (keyCode == KeyEvent.KEYCODE_ESCAPE) {
            onBackPressed()
            return true
        }
        return super.onKeyDown(keyCode, event)
    }

    override fun finish() {
        super.finish()
        Ui.applyCloseTransition(this)
    }

    private fun wireTab(id: Int, cls: Class<*>) {
        try {
            val v = findViewById<View>(id)
            v.setOnClickListener { Ui.openScreen(this, Intent(this, cls)) }
            Ui.tvFocus(v)
            Ui.pressPop(v)
        } catch (_: Exception) {
        }
    }

    private fun refresh() {
        try {
            rows.clear()
            val all = CatalogRepo.titles
            val now = System.currentTimeMillis()
            for (ch in TvSchedule.channels) {
                val a = try {
                    TvSchedule.current(ch.id, all, now)
                } catch (_: Exception) {
                    null
                } ?: continue
                val n = try {
                    TvSchedule.next(ch.id, all, now)
                } catch (_: Exception) {
                    null
                }
                rows.add(
                    Row(
                        ch.id, ch.name, a,
                        n?.let { it.title.title + epSuffix(it) } ?: "",
                        n?.let { TvSchedule.fmtTime(it.slotStartMs) } ?: ""
                    )
                )
            }
            adapter.notifyDataSetChanged()
            try {
                val st = findViewById<TextView>(R.id.tv_status)
                val list = findViewById<ListView>(R.id.tv_list)
                if (rows.isEmpty()) {
                    list.visibility = View.GONE
                    st.visibility = View.VISIBLE
                    st.text = if (CatalogRepo.titles.isEmpty()) getString(R.string.checking_updates)
                    else getString(R.string.tv_empty)
                } else {
                    list.visibility = View.VISIBLE
                    st.visibility = View.GONE
                }
            } catch (_: Exception) {
            }
        } catch (_: Exception) {
        }
    }

    private fun epSuffix(a: TvSchedule.Airing): String {
        val l = a.title.episodes.getOrNull(a.epIndex)?.label.orEmpty()
        return if (l.isEmpty()) "" else " — $l"
    }

    /** Sintonia: diretti dal punto in onda, embed dall'inizio. */
    private fun tune(a: TvSchedule.Airing) {
        if (TvSchedule.playerFor(a.title, a.epIndex) == null) {
            try {
                Toast.makeText(this, getString(R.string.no_episodes), Toast.LENGTH_SHORT).show()
            } catch (_: Exception) {
            }
            return
        }
        TvSchedule.tuneTo(this, a)
    }

    private inner class TvAdapter : BaseAdapter() {
        override fun getCount() = rows.size
        override fun getItem(position: Int) = rows[position]
        override fun getItemId(position: Int) = position.toLong()

        override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
            val v = convertView ?: layoutInflater.inflate(R.layout.item_tv_channel, parent, false)
            val r = rows[position]
            val a = r.airing
            (v.findViewById<TextView>(R.id.v_channel)).text = r.channelName
            val poster = v.findViewById<ImageView>(R.id.v_poster)
            val nowTv = v.findViewById<TextView>(R.id.v_now)
            val timeTv = v.findViewById<TextView>(R.id.v_time)
            val bar = v.findViewById<ProgressBar>(R.id.v_progress)
            val nextTv = v.findViewById<TextView>(R.id.v_next)
            if (a == null) {
                Ui.round(poster, 10)
                ImageLoader.display(poster, null)
                nowTv.text = getString(R.string.tv_empty)
                timeTv.text = ""
                bar.progress = 0
                nextTv.text = ""
            } else {
                val epLabel = a.title.episodes.getOrNull(a.epIndex)?.label.orEmpty()
                Ui.round(poster, 10)
                ImageLoader.display(poster, a.title.img)
                nowTv.text = a.title.title + if (epLabel.isEmpty()) "" else " — $epLabel"
                timeTv.text = TvSchedule.fmtTime(a.slotStartMs) + " – " + TvSchedule.fmtTime(a.slotEndMs)
                val frac = ((System.currentTimeMillis() - a.slotStartMs).toFloat() / TvSchedule.SLOT_MS.toFloat())
                    .coerceIn(0f, 1f)
                bar.progress = (frac * 100).toInt()
                nextTv.text = if (r.nextTitle.isEmpty()) "" else getString(R.string.tv_next) + ": " + r.nextTitle +
                    if (r.nextStart.isEmpty()) "" else " (" + r.nextStart + ")"
            }
            // Click diretto sulla riga (touch E telecomando): il focus D-pad
            // dentro la riga mangiava il click della ListView. Niente tvFocus
            // qui: l'evidenziazione la fa il listSelector come in Cerca.
            v.setOnClickListener { rows.getOrNull(position)?.airing?.let { tune(it) } }
            v.isFocusable = true
            v.isClickable = true
            return v
        }
    }
}
