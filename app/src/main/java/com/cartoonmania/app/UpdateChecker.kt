package com.cartoonmania.app

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.widget.Toast
import androidx.core.content.FileProvider
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import org.json.JSONObject

/**
 * Controlla le release GitHub a ogni avvio: se esiste una versione piu' nuova
 * mostra un popup (ignorabile con "Piu' tardi", ricompare al prossimo avvio)
 * che propone di scaricarla e installarla. Silenzioso se offline o in errore.
 */
object UpdateChecker {

    private const val LATEST_URL =
        "https://api.github.com/repos/SancioPanza88/CartoonMania/releases/latest"
    private const val UA = "CartoonMania-App"

    fun check(activity: Activity) {
        Thread {
            try {
                val info = fetchLatest() ?: return@Thread
                val current = currentVersion(activity) ?: return@Thread
                if (!isNewer(info.tag, current)) return@Thread
                activity.runOnUiThread {
                    if (!activity.isFinishing && !activity.isDestroyed) {
                        showDialog(activity, info, current)
                    }
                }
            } catch (_: Exception) {
            }
        }.start()
    }

    private data class Release(val tag: String, val apkUrl: String)

    private fun fetchLatest(): Release? {
        val c = (URL(LATEST_URL).openConnection() as HttpURLConnection).apply {
            connectTimeout = 10000
            readTimeout = 15000
            setRequestProperty("User-Agent", UA)
            setRequestProperty("Accept", "application/vnd.github+json")
        }
        try {
            if (c.responseCode != 200) return null
            val json = JSONObject(c.inputStream.bufferedReader().use { it.readText() })
            val tag = json.optString("tag_name").trim()
            if (tag.isEmpty()) return null
            val assets = json.optJSONArray("assets") ?: return null
            for (i in 0 until assets.length()) {
                val a = assets.optJSONObject(i) ?: continue
                val url = a.optString("browser_download_url")
                if (url.endsWith(".apk", ignoreCase = true)) return Release(tag, url)
            }
            return null
        } finally {
            c.disconnect()
        }
    }

    @Suppress("DEPRECATION")
    private fun currentVersion(activity: Activity): String? {
        return try {
            activity.packageManager.getPackageInfo(activity.packageName, 0)?.versionName
        } catch (_: Exception) {
            null
        }
    }

    /** "v1.8.0" e' piu' nuovo di "1.7": le parti mancanti valgono 0. */
    internal fun isNewer(tag: String, current: String): Boolean {
        fun parts(v: String) = v.trim().trimStart('v', 'V')
            .split('.').map { it.filter(Char::isDigit).toIntOrNull() ?: 0 }
        val t = parts(tag)
        val c = parts(current)
        for (i in 0 until maxOf(t.size, c.size)) {
            val a = t.getOrNull(i) ?: 0
            val b = c.getOrNull(i) ?: 0
            if (a != b) return a > b
        }
        return false
    }

    private fun showDialog(activity: Activity, info: Release, current: String) {
        AlertDialog.Builder(activity)
            .setTitle(activity.getString(R.string.update_title))
            .setMessage(activity.getString(R.string.update_msg, info.tag, current))
            .setPositiveButton(R.string.update_now) { _, _ -> downloadAndInstall(activity, info) }
            .setNegativeButton(R.string.update_later, null)
            .show()
    }

    private fun downloadAndInstall(activity: Activity, info: Release) {
        Toast.makeText(activity, R.string.update_downloading, Toast.LENGTH_SHORT).show()
        Thread {
            try {
                val dir = File(activity.filesDir, "updates").apply { mkdirs() }
                val out = File(dir, "CartoonMania-update.apk")
                if (out.exists()) out.delete()
                val c = (URL(info.apkUrl).openConnection() as HttpURLConnection).apply {
                    connectTimeout = 15000
                    readTimeout = 60000
                    instanceFollowRedirects = true
                    setRequestProperty("User-Agent", UA)
                }
                try {
                    if (c.responseCode != 200) throw IllegalStateException("HTTP ${c.responseCode}")
                    c.inputStream.use { input ->
                        out.outputStream().use { output -> input.copyTo(output, 1 shl 16) }
                    }
                } finally {
                    c.disconnect()
                }
                activity.runOnUiThread { installApk(activity, out) }
            } catch (_: Exception) {
                activity.runOnUiThread {
                    Toast.makeText(activity, R.string.update_failed, Toast.LENGTH_LONG).show()
                }
            }
        }.start()
    }

    private fun installApk(activity: Activity, apk: File) {
        try {
            val uri = FileProvider.getUriForFile(
                activity, "${activity.packageName}.provider", apk
            )
            val i = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            activity.startActivity(i)
        } catch (e: Exception) {
            Toast.makeText(
                activity,
                "${activity.getString(R.string.update_failed)}: ${e.message}",
                Toast.LENGTH_LONG
            ).show()
        }
    }
}
