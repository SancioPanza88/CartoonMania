package com.cartoonmania.app

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView

class HomeActivity : Activity() {

    private lateinit var container: LinearLayout
    private lateinit var status: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        CrashGuard.install(applicationContext)
        setContentView(R.layout.activity_home)

        val profBtn = findViewById<ImageView>(R.id.home_profile)
        Ui.tvFocus(profBtn)
        profBtn.setOnClickListener {
            startActivity(Intent(this, ProfileActivity::class.java))
        }
        renderProfile()
        // Popup di aggiornamento a ogni avvio se c'e' una release piu' nuova
        UpdateChecker.check(this)

        container = findViewById(R.id.home_container)
        status = findViewById(R.id.home_status)
        findViewById<View>(R.id.btn_tab_search).setOnClickListener {
            startActivity(Intent(this, SearchActivity::class.java))
        }
        Ui.tvFocus(findViewById(R.id.btn_tab_search))
        findViewById<View>(R.id.btn_tab_settings).setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }
        Ui.tvFocus(findViewById(R.id.btn_tab_settings))
        findViewById<View>(R.id.btn_tab_home).setOnClickListener {
            findViewById<android.widget.ScrollView>(R.id.home_scroll).smoothScrollTo(0, 0)
        }
        Ui.tvFocus(findViewById(R.id.btn_tab_home))

        Thread {
            val loadError = try {
                CatalogRepo.loadLocal(this)
                null
            } catch (e: Exception) {
                e
            }
            runOnUiThread {
                if (loadError != null) {
                    status.visibility = View.VISIBLE
                    status.text = "Asset non disponibile, scarico il catalogo…"
                    Thread {
                        var ok = false
                        for (attempt in 1..5) {
                            val msg = try { CatalogRepo.refresh(this@HomeActivity) } catch (e: Exception) { "Errore: ${e.message}" }
                            ok = CatalogRepo.titles.isNotEmpty()
                            if (ok) break
                            runOnUiThread { status.text = "$msg - riprovo ($attempt/5)" }
                            Thread.sleep(8000)
                        }
                        runOnUiThread {
                            if (ok) {
                                status.visibility = View.GONE
                                safeBuildSections()
                            } else {
                                status.text = "Scaricamento fallito: riavvia l'app con la connessione attiva"
                            }
                        }
                    }.start()
                    return@runOnUiThread
                }
                status.visibility = View.GONE
                safeBuildSections()
                backgroundRefresh(manual = false)
            }
        }.start()
    }

    override fun onResume() {
        super.onResume()
        renderProfile()
        val v = CatalogRepo.currentVersion(this)
        val pid = try { Profiles.current(this).id } catch (_: Exception) { "" }
        if (builtVer != null && (v != builtVer || pid != builtProfile) && CatalogRepo.titles.isNotEmpty()) {
            safeBuildSections()
        }
    }

    private fun renderProfile() {
        try {
            val p = Profiles.current(this)
            val v = findViewById<ImageView>(R.id.home_profile)
            Profiles.renderInto(this, v, p)
            v.contentDescription = p.name
        } catch (_: Exception) {
        }
    }

    private var refreshing = false

    /** Controllo rete al massimo ogni 6h: gli avvii ravvicinati usano i dati
     *  locali e partono subito (l'aggiornamento manuale resta sempre). */
    private fun shouldAutoRefresh(): Boolean {
        return try {
            val p = getSharedPreferences("cm", MODE_PRIVATE)
            val last = p.getLong("last_refresh", 0)
            if (System.currentTimeMillis() - last < 6 * 60 * 60 * 1000L) return false
            p.edit().putLong("last_refresh", System.currentTimeMillis()).apply()
            true
        } catch (_: Exception) {
            true
        }
    }

    private fun backgroundRefresh(manual: Boolean) {
        if (!manual && !shouldAutoRefresh()) return
        Thread {
            val msg = try { CatalogRepo.refresh(this) } catch (e: Exception) { "Errore: ${e.message}" }
            runOnUiThread {
                refreshing = false
                val updated = msg.startsWith("Aggiornato")
                if (updated) {
                    container.removeAllViews()
                    safeBuildSections()
                }
                val silent = !manual && !updated &&
                    msg.startsWith("Catalogo gia'") && CatalogRepo.titles.isNotEmpty()
                if (silent) {
                    status.visibility = View.GONE
                } else {
                    status.text = msg
                    status.visibility = View.VISIBLE
                    status.postDelayed({ status.visibility = View.GONE }, 3000)
                }
            }
        }.start()
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (keyCode == KeyEvent.KEYCODE_ESCAPE) {
            onBackPressed()
            return true
        }
        return super.onKeyDown(keyCode, event)
    }

    private var buildGen = 0

    private fun safeBuildSections() {
        try {
            buildGen++
            val g = buildGen
            container.removeAllViews()
            val all = CatalogRepo.titles
            if (all.isEmpty()) {
                status.visibility = View.VISIBLE
                status.text = getString(R.string.no_data)
                return
            }
            buildCategories(all)
            builtVer = CatalogRepo.currentVersion(this)
            builtProfile = try { Profiles.current(this).id } catch (_: Exception) { null }
            postRows(planRows(all), 0, g)
        } catch (e: Throwable) {
            status.visibility = View.VISIBLE
            status.text = "Errore: ${e.javaClass.simpleName}: ${e.message}"
        }
    }

    private var builtVer: String? = null
    private var builtProfile: String? = null

    private fun planRows(all: List<CatalogRepo.Title>): List<Pair<String, List<CatalogRepo.Title>>> {
        val out = ArrayList<Pair<String, List<CatalogRepo.Title>>>()
        val tv = Ui.isTv(this)
        val popN = if (tv) 20 else 25
        val rowN = if (tv) 20 else 30
        try {
            val bySlug = all.associateBy { it.slug }
            val me = Profiles.current(this)
            val favs = me.favorites.mapNotNull { bySlug[it] }
            if (favs.isNotEmpty()) out.add(getString(R.string.favorites_row) to favs)
            val rec = me.recent.mapNotNull { bySlug[it] }
            if (rec.isNotEmpty()) out.add(getString(R.string.recent_row) to rec)
        } catch (_: Exception) {
        }
        out.add("Popolari ora" to all.shuffled().take(popN))
        out.add("Aggiunti di recente" to all.sortedByDescending { it.modified }.take(popN))

        val preferred = listOf(
            "Anime", "Film Animazione", "Serie Tv", "Shonen", "Azione",
            "Avventura", "Commedia", "Fantascienza", "Drammatico",
            "Fantasy", "Bambini", "Sub-Ita", "Sentimentale", "Mecha",
            "Soprannaturale", "Shojo"
        )
        for (cat in preferred) {
            val items = all.filter { it.cats.contains(cat) }
            if (items.size >= 8) out.add(cat to items.shuffled().take(rowN))
        }
        return out
    }

    /** Aggiunge le righe a blocchi: la prima si vede subito, il resto segue. */
    private fun postRows(rows: List<Pair<String, List<CatalogRepo.Title>>>, i: Int, g: Int) {
        if (g != buildGen || isFinishing || isDestroyed) return
        if (i >= rows.size) return
        try {
            addRow(rows[i].first, rows[i].second)
        } catch (_: Exception) {
        }
        container.post { postRows(rows, i + 1, g) }
    }

    private fun buildCategories(all: List<CatalogRepo.Title>) {
        val counts = LinkedHashMap<String, Int>()
        for (t in all) for (c in t.cats) counts[c] = (counts[c] ?: 0) + 1
        val cats = counts.entries.filter { it.value >= 8 }.sortedByDescending { it.value }.take(30)
        if (cats.isEmpty()) return

        container.addView(sectionHeader(getString(R.string.categories)), 0)

        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(dp(14), 0, dp(14), dp(4))
        }
        val chipViews = ArrayList<View>()
        for ((cat, count) in cats) {
            val chip = Ui.chip(this, "$cat  $count") { ctx ->
                startActivity(Intent(ctx, CategoryActivity::class.java).putExtra("cat", cat))
            }
            val lp = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT
            )
            lp.marginEnd = dp(8)
            row.addView(chip, lp)
            chipViews.add(chip)
        }
        Ui.clampHorizontalRow(chipViews)
        container.addView(
            wheelScroll(HorizontalScrollView(this).apply { addView(row) }),
            1
        )
    }

    private fun sectionHeader(text: String): TextView =
        TextView(this).apply {
            this.text = text
            textSize = 17f
            setTextColor(0xFFFFFFFF.toInt())
            setPadding(dp(20), dp(24), dp(20), dp(12))
            setTypeface(typeface, android.graphics.Typeface.BOLD)
        }

    private fun wheelScroll(scroll: HorizontalScrollView): HorizontalScrollView {
        scroll.isHorizontalScrollBarEnabled = false
        scroll.setOnGenericMotionListener { v, e ->
            if (e.action == MotionEvent.ACTION_SCROLL) {
                val dx = e.getAxisValue(MotionEvent.AXIS_HSCROLL) +
                    e.getAxisValue(MotionEvent.AXIS_VSCROLL)
                (v as HorizontalScrollView).smoothScrollBy((dx * dp(64)).toInt(), 0)
                true
            } else false
        }
        return scroll
    }

    private fun addRow(sectionTitle: String, items: List<CatalogRepo.Title>) {
        if (items.isEmpty()) return

        val ripple = android.util.TypedValue().apply {
            theme.resolveAttribute(android.R.attr.selectableItemBackground, this, true)
        }

        container.addView(sectionHeader(sectionTitle))

        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(dp(14), 0, dp(14), dp(8))
        }

        val cards = ArrayList<View>()
        for (t in items) {
            val card = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(5), 0, dp(5), 0)
                isClickable = true
                isFocusable = true
                setBackgroundResource(ripple.resourceId)
                setOnClickListener {
                    startActivity(
                        Intent(context, DetailActivity::class.java).putExtra("slug", t.slug)
                    )
                }
            }
            Ui.tvFocus(card)

            val poster = ImageView(this).apply {
                layoutParams = LinearLayout.LayoutParams(dp(102), dp(152))
                scaleType = ImageView.ScaleType.CENTER_CROP
                setBackgroundColor(0xFF1F1F2B.toInt())
                clipToOutline = true
                outlineProvider = object : android.view.ViewOutlineProvider() {
                    override fun getOutline(view: View, outline: android.graphics.Outline) {
                        outline.setRoundRect(0, 0, view.width, view.height, dp(10).toFloat())
                    }
                }
            }
            ImageLoader.display(poster, t.img)

            val label = TextView(this).apply {
                text = t.title
                textSize = 12f
                setTextColor(0xFFA0A4B8.toInt())
                maxLines = 2
                ellipsize = android.text.TextUtils.TruncateAt.END
                setPadding(dp(2), dp(7), dp(2), dp(4))
            }

            card.addView(poster)
            card.addView(label)
            row.addView(card)
            cards.add(card)
        }
        Ui.clampHorizontalRow(cards)

        container.addView(wheelScroll(HorizontalScrollView(this).apply { addView(row) }))
    }

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()
}
