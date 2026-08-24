package com.cartoonmania.app

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast

class HomeActivity : Activity() {

    private lateinit var container: LinearLayout
    private lateinit var status: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        CrashGuard.install(applicationContext)
        setContentView(R.layout.activity_home)

        container = findViewById(R.id.home_container)
        status = findViewById(R.id.home_status)
        findViewById<TextView>(R.id.btn_tab_search).setOnClickListener {
            startActivity(Intent(this, SearchActivity::class.java))
        }

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
                                buildSections()
                            } else {
                                status.text = "Scaricamento fallito: riavvia l'app con la connessione attiva"
                            }
                        }
                    }.start()
                    return@runOnUiThread
                }
                status.visibility = View.GONE
                buildSections()
                Thread {
                    val msg = try { CatalogRepo.refresh(this) } catch (e: Exception) { "Aggiornamento fallito" }
                    runOnUiThread {
                        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
                    }
                }.start()
            }
        }.start()
    }

    private fun buildSections() {
        val all = CatalogRepo.titles
        if (all.isEmpty()) {
            status.visibility = View.VISIBLE
            status.text = getString(R.string.no_data)
            return
        }

        addRow("Popolari ora", all.shuffled().take(25))
        addRow("Aggiunti di recente", all.sortedByDescending { it.modified }.take(25))

        val preferred = listOf(
            "Anime", "Film Animazione", "Serie Tv", "Shonen", "Azione",
            "Avventura", "Commedia", "Fantascienza", "Drammatico",
            "Fantasy", "Bambini", "Sub-Ita", "Sentimentale", "Mecha",
            "Soprannaturale", "Shojo"
        )
        for (cat in preferred) {
            val items = all.filter { it.cats.contains(cat) }
            if (items.size >= 8) addRow(cat, items.shuffled().take(30))
        }
    }

    private fun addRow(sectionTitle: String, items: List<CatalogRepo.Title>) {
        if (items.isEmpty()) return

        val ripple = android.util.TypedValue().apply {
            theme.resolveAttribute(android.R.attr.selectableItemBackground, this, true)
        }

        val header = TextView(this).apply {
            text = sectionTitle
            textSize = 17f
            setTextColor(0xFFFFFFFF.toInt())
            setPadding(dp(20), dp(24), dp(20), dp(12))
            setTypeface(typeface, android.graphics.Typeface.BOLD)
        }
        container.addView(header)

        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(dp(14), 0, dp(14), dp(8))
        }

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
        }

        val scroll = HorizontalScrollView(this).apply {
            isHorizontalScrollBarEnabled = false
            addView(row)
        }
        container.addView(scroll)
    }

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()
}
