package com.cartoonmania.app

import android.app.Activity
import android.os.Bundle
import android.view.KeyEvent
import android.view.View
import android.widget.TextView

class SettingsActivity : Activity() {

    private var refreshing = false
    private lateinit var status: TextView
    private lateinit var infoCatalog: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        status = findViewById(R.id.s_status)
        infoCatalog = findViewById(R.id.s_info_catalog)
        findViewById<View>(R.id.s_back).setOnClickListener { finish() }
        findViewById<View>(R.id.btn_update).setOnClickListener { manualUpdate() }
        findViewById<View>(R.id.btn_clear_cache).setOnClickListener { clearCache() }
        findViewById<View>(R.id.btn_tab_home).setOnClickListener { finish() }
        findViewById<View>(R.id.btn_tab_search).setOnClickListener {
            startActivity(android.content.Intent(this, SearchActivity::class.java))
        }
        Ui.tvFocus(findViewById(R.id.btn_tab_home))
        Ui.tvFocus(findViewById(R.id.btn_tab_search))
        Ui.tvFocus(findViewById(R.id.btn_update))
        Ui.tvFocus(findViewById(R.id.btn_clear_cache))
        // Sulla TV il ripple non evidenzia il focus: sfondo bordato
        if (Ui.isTv(this)) {
            findViewById<View>(R.id.btn_update).setBackgroundResource(R.drawable.bg_episode_focus)
            findViewById<View>(R.id.btn_clear_cache).setBackgroundResource(R.drawable.bg_episode_focus)
        }

        val appVer = try {
            packageManager.getPackageInfo(packageName, 0).versionName ?: "?"
        } catch (_: Exception) { "?" }

        findViewById<TextView>(R.id.s_info_app).text =
            "CartoonMania v$appVer\nContenuti: toonitalia.xyz + loonex.eu"

        refreshInfo()
    }

    override fun onResume() {
        super.onResume()
        refreshInfo()
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (keyCode == KeyEvent.KEYCODE_ESCAPE) {
            onBackPressed()
            return true
        }
        return super.onKeyDown(keyCode, event)
    }

    private fun refreshInfo() {
        infoCatalog.text =
            "${CatalogRepo.titles.size} titoli caricati · catalogo v${CatalogRepo.currentVersion(this)}"
    }

    private fun manualUpdate() {
        if (refreshing) return
        refreshing = true
        status.visibility = View.VISIBLE
        status.text = getString(R.string.checking_updates)
        Thread {
            val msg = try { CatalogRepo.refresh(this) } catch (e: Exception) { "Errore: ${e.message}" }
            runOnUiThread {
                refreshing = false
                refreshInfo()
                status.text = msg
                status.visibility = View.VISIBLE
                status.postDelayed({ status.visibility = View.GONE }, 3500)
            }
        }.start()
    }

    private fun clearCache() {
        val freed = ImageLoader.clearDiskCache(this)
        status.visibility = View.VISIBLE
        status.text = "Cache svuotata ($freed MB liberati)"
        status.postDelayed({ status.visibility = View.GONE }, 3000)
    }
}
