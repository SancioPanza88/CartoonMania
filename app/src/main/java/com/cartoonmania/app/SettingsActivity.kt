package com.cartoonmania.app

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import java.io.File

class SettingsActivity : Activity() {

    private var refreshing = false
    private lateinit var status: TextView
    private lateinit var infoCatalog: TextView
    private var syncSession: SyncServer.Session? = null
    private var syncDialog: AlertDialog? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        status = findViewById(R.id.s_status)
        infoCatalog = findViewById(R.id.s_info_catalog)
        findViewById<View>(R.id.s_back).setOnClickListener { finish() }
        findViewById<View>(R.id.btn_update).setOnClickListener { manualUpdate() }
        findViewById<View>(R.id.btn_clear_cache).setOnClickListener { clearCache() }
        findViewById<View>(R.id.btn_sync).setOnClickListener { showSyncMenu() }
        findViewById<View>(R.id.btn_tab_home).setOnClickListener { finish() }
        findViewById<View>(R.id.btn_tab_search).setOnClickListener {
            startActivity(android.content.Intent(this, SearchActivity::class.java))
        }
        Ui.tvFocus(findViewById(R.id.btn_tab_home))
        Ui.tvFocus(findViewById(R.id.btn_tab_search))
        // Niente zoom sui pulsanti larghi: con lo ScrollView lo zoom li spinge
        // fuori schermo; l'evidenziazione la fa gia' lo sfondo sulla TV.
        // Sulla TV il ripple non evidenzia il focus: sfondo bordato
        if (Ui.isTv(this)) {
            findViewById<View>(R.id.btn_update).setBackgroundResource(R.drawable.bg_episode_focus)
            findViewById<View>(R.id.btn_clear_cache).setBackgroundResource(R.drawable.bg_episode_focus)
            findViewById<View>(R.id.btn_sync).setBackgroundResource(R.drawable.bg_episode_focus)
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

    override fun onDestroy() {
        stopSync()
        super.onDestroy()
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

    // ---- Sincronizzazione tra dispositivi (stesso Wi-Fi, nessuna nuvola) ----
    // Profilo corrente: preferiti + recenti uniti, progressi inviati vincono.
    // Ricevente = mostra QR (server locale). Mittente = digita IP/porta/codice
    // e invia o scarica. Importa file = via di fuga (es. file scaricato dal
    // browser dopo aver inquadrato il QR con la fotocamera).

    private fun stopSync() {
        try {
            syncSession?.stop()
        } catch (_: Exception) {
        }
        syncSession = null
    }

    /** Legge tutto lo stream fino a cap byte (API 21-safe, niente readBytes). */
    private fun readCapped(inp: java.io.InputStream, cap: Int): ByteArray? {
        return try {
            val out = java.io.ByteArrayOutputStream(8192)
            val buf = ByteArray(8192)
            var total = 0
            while (true) {
                val n = inp.read(buf)
                if (n < 0) break
                total += n
                if (total > cap) return null
                out.write(buf, 0, n)
            }
            out.toByteArray()
        } catch (_: Exception) {
            null
        }
    }

    private fun showSyncMenu() {
        val me = try { Profiles.current(this).name } catch (_: Exception) { "?" }
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.sync_title) + " · " + getString(R.string.sync_profile, me))
            .setItems(
                arrayOf(
                    getString(R.string.sync_receive),
                    getString(R.string.sync_send),
                    getString(R.string.sync_import)
                )
            ) { _, which ->
                when (which) {
                    0 -> showSyncReceive()
                    1 -> showSyncSend()
                    else -> pickSyncFile()
                }
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun showSyncReceive() {
        stopSync()
        val ip = PhotoServer.localIp()
        if (ip == null) {
            Toast.makeText(this, R.string.update_failed, Toast.LENGTH_LONG).show()
            return
        }
        val token = (100000 + (Math.random() * 900000).toInt()).toString()
        val inbox = File(File(filesDir, "sync").apply { mkdirs() }, "inbox.tmp")
        val export = try { Profiles.exportCurrent(this) } catch (_: Exception) { "" }
        if (export.isEmpty()) {
            Toast.makeText(this, R.string.update_failed, Toast.LENGTH_LONG).show()
            return
        }
        val session = SyncServer.open(inbox, token, export)
        if (session == null) {
            Toast.makeText(this, R.string.update_failed, Toast.LENGTH_LONG).show()
            return
        }
        syncSession = session
        val url = "http://$ip:${session.port}/?t=$token"
        val qr = Qr.bitmap(url)
        val others = PhotoServer.localIps().filter { it != ip }
            .joinToString(", ") { "http://$it:${session.port}/" }
        val dp = fun(v: Int): Int = (v * resources.displayMetrics.density).toInt()

        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(dp(24), dp(16), dp(24), dp(8))
        }
        if (qr != null) {
            box.addView(ImageView(this).apply {
                setImageBitmap(qr)
                layoutParams = LinearLayout.LayoutParams(dp(240), dp(240))
            })
        }
        box.addView(TextView(this).apply {
            text = "IP $ip · ${getString(R.string.sync_port_hint)} ${session.port} · ${getString(R.string.sync_token_hint, "")}: $token"
            textSize = 15f
            setTextColor(0xFFFFFFFF.toInt())
            setPadding(0, dp(12), 0, 0)
            gravity = Gravity.CENTER
        })
        box.addView(TextView(this).apply {
            text = url
            textSize = 12f
            setTextColor(0xFFA0A4B8.toInt())
            setPadding(0, dp(8), 0, 0)
            gravity = Gravity.CENTER
        })
        if (others.isNotEmpty()) {
            box.addView(TextView(this).apply {
                text = "Altri IP: $others"
                textSize = 12f
                setTextColor(0xFFA0A4B8.toInt())
                setPadding(0, dp(8), 0, 0)
                gravity = Gravity.CENTER
            })
        }
        box.addView(TextView(this).apply {
            text = getString(R.string.qr_net_hint)
            textSize = 13f
            setTextColor(0xFFA0A4B8.toInt())
            setPadding(0, dp(8), 0, 0)
            gravity = Gravity.CENTER
        })

        syncDialog = AlertDialog.Builder(this)
            .setTitle(R.string.sync_receive)
            .setView(box)
            .setNegativeButton(android.R.string.cancel) { _, _ -> stopSync() }
            .setOnDismissListener { stopSync() }
            .show()

        Thread {
            val end = System.currentTimeMillis() + 5 * 60 * 1000L
            var ok = false
            while (System.currentTimeMillis() < end && syncSession === session) {
                try {
                    if (inbox.exists() && inbox.length() > 0) {
                        ok = true
                        break
                    }
                    Thread.sleep(1500)
                } catch (_: Exception) {
                    break
                }
            }
            val done = ok && syncSession === session
            var summary: String? = null
            if (done) {
                summary = try {
                    Profiles.importMerge(this, inbox.readText(Charsets.UTF_8))
                } catch (_: Exception) {
                    null
                }
                try {
                    inbox.delete()
                } catch (_: Exception) {
                }
            }
            runOnUiThread {
                if (done) {
                    if (summary != null) {
                        Toast.makeText(this, getString(R.string.sync_received, summary), Toast.LENGTH_LONG).show()
                    } else {
                        Toast.makeText(this, R.string.sync_bad, Toast.LENGTH_LONG).show()
                    }
                } else if (syncSession === session) {
                    Toast.makeText(this, R.string.profile_timeout, Toast.LENGTH_LONG).show()
                }
                try {
                    syncDialog?.dismiss()
                } catch (_: Exception) {
                }
            }
        }.start()
    }

    private fun showSyncSend() {
        val dp = fun(v: Int): Int = (v * resources.displayMetrics.density).toInt()
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(8), dp(20), 0)
        }
        val ipIn = EditText(this).apply {
            hint = getString(R.string.sync_ip_hint)
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI
            isFocusable = true
            isFocusableInTouchMode = true
        }
        val portIn = EditText(this).apply {
            hint = getString(R.string.sync_port_hint)
            inputType = InputType.TYPE_CLASS_NUMBER
            isFocusable = true
            isFocusableInTouchMode = true
        }
        val tokenIn = EditText(this).apply {
            hint = getString(R.string.sync_token_hint)
            inputType = InputType.TYPE_CLASS_NUMBER
            isFocusable = true
            isFocusableInTouchMode = true
        }
        box.addView(ipIn)
        box.addView(portIn)
        box.addView(tokenIn)
        AlertDialog.Builder(this)
            .setTitle(R.string.sync_send)
            .setView(box)
            .setPositiveButton(R.string.sync_upload) { _, _ ->
                doSyncPush(
                    ipIn.text.toString().trim(),
                    portIn.text.toString().trim().toIntOrNull() ?: 0,
                    tokenIn.text.toString().trim()
                )
            }
            .setNeutralButton(R.string.sync_download) { _, _ ->
                doSyncPull(
                    ipIn.text.toString().trim(),
                    portIn.text.toString().trim().toIntOrNull() ?: 0,
                    tokenIn.text.toString().trim()
                )
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    /** Invia l'export del profilo corrente al ricevente (POST /up). */
    private fun doSyncPush(ip: String, port: Int, token: String) {
        if (ip.isEmpty() || port <= 0 || token.isEmpty()) {
            Toast.makeText(this, R.string.sync_failed, Toast.LENGTH_LONG).show()
            return
        }
        status.visibility = View.VISIBLE
        status.text = getString(R.string.checking_updates)
        Thread {
            var ok = false
            try {
                val body = Profiles.exportCurrent(this).toByteArray(Charsets.UTF_8)
                val c = java.net.URL("http://$ip:$port/up").openConnection() as java.net.HttpURLConnection
                c.connectTimeout = 15000
                c.readTimeout = 30000
                c.doOutput = true
                c.setFixedLengthStreamingMode(body.size)
                c.setRequestProperty("X-Token", token)
                c.setRequestProperty("Content-Type", "application/json")
                c.outputStream.use { it.write(body) }
                val code = c.responseCode
                val resp = try {
                    c.inputStream.bufferedReader().readText()
                } catch (_: Exception) { "" }
                ok = code == 200 && resp.trim() == "ok"
                c.disconnect()
            } catch (_: Exception) {
                ok = false
            }
            runOnUiThread {
                status.visibility = View.VISIBLE
                status.text = getString(if (ok) R.string.sync_sent else R.string.sync_failed)
                status.postDelayed({ status.visibility = View.GONE }, 3500)
            }
        }.start()
    }

    /** Scarica l'export dal ricevente (GET /down) e lo fonde nel profilo. */
    private fun doSyncPull(ip: String, port: Int, token: String) {
        if (ip.isEmpty() || port <= 0 || token.isEmpty()) {
            Toast.makeText(this, R.string.sync_failed, Toast.LENGTH_LONG).show()
            return
        }
        status.visibility = View.VISIBLE
        status.text = getString(R.string.checking_updates)
        Thread {
            var summary: String? = null
            var failed = false
            try {
                val c = java.net.URL("http://$ip:$port/down?t=$token").openConnection() as java.net.HttpURLConnection
                c.connectTimeout = 15000
                c.readTimeout = 30000
                val code = c.responseCode
                if (code == 200) {
                    val bytes = c.inputStream.use { readCapped(it, SyncServer.MAX_BYTES) }
                    c.disconnect()
                    if (bytes == null) {
                        failed = true
                    } else {
                        summary = Profiles.importMerge(this, String(bytes, Charsets.UTF_8))
                        if (summary == null) failed = true
                    }
                } else {
                    failed = true
                    try {
                        c.disconnect()
                    } catch (_: Exception) {
                    }
                }
            } catch (_: Exception) {
                failed = true
            }
            val done = summary
            runOnUiThread {
                status.visibility = View.VISIBLE
                status.text = when {
                    done != null -> getString(R.string.sync_received, done)
                    else -> getString(if (failed) R.string.sync_failed else R.string.sync_bad)
                }
                status.postDelayed({ status.visibility = View.GONE }, 3500)
            }
        }.start()
    }

    private fun pickSyncFile() {
        try {
            startActivityForResult(
                Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                    type = "application/json"
                    addCategory(Intent.CATEGORY_OPENABLE)
                },
                42
            )
        } catch (_: Exception) {
            Toast.makeText(this, R.string.update_failed, Toast.LENGTH_SHORT).show()
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == 42 && resultCode == RESULT_OK) {
            try {
                val uri = data?.data ?: return
                val bytes = contentResolver.openInputStream(uri)?.use { readCapped(it, SyncServer.MAX_BYTES) }
                if (bytes == null) {
                    Toast.makeText(this, R.string.sync_bad, Toast.LENGTH_LONG).show()
                    return
                }
                val summary = Profiles.importMerge(this, String(bytes, Charsets.UTF_8))
                if (summary != null) {
                    Toast.makeText(this, getString(R.string.sync_imported, summary), Toast.LENGTH_LONG).show()
                } else {
                    Toast.makeText(this, R.string.sync_bad, Toast.LENGTH_LONG).show()
                }
            } catch (_: Exception) {
                Toast.makeText(this, R.string.sync_bad, Toast.LENGTH_LONG).show()
            }
        }
    }
}
