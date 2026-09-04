package com.cartoonmania.app

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.graphics.Bitmap
import android.os.Bundle
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import java.io.File

class ProfileActivity : Activity() {

    private lateinit var list: LinearLayout
    private var photoSession: PhotoServer.Session? = null
    private var qrDialog: AlertDialog? = null
    private var galleryTarget: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_profile)

        list = findViewById(R.id.p_list)
        findViewById<View>(R.id.p_back).setOnClickListener { finish() }
        findViewById<View>(R.id.p_add).setOnClickListener { askName() }
        rebuild()
    }

    override fun onResume() {
        super.onResume()
        rebuild()
    }

    override fun onDestroy() {
        stopPhotoServer()
        super.onDestroy()
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (keyCode == KeyEvent.KEYCODE_ESCAPE) {
            onBackPressed()
            return true
        }
        return super.onKeyDown(keyCode, event)
    }

    private fun rebuild() {
        list.removeAllViews()
        val profiles = Profiles.all(this)
        val cur = Profiles.current(this)
        for (p in profiles) {
            list.addView(profileRow(p, p.id == cur.id, profiles.size > 1))
        }
    }

    private fun profileRow(p: Profiles.Profile, isCur: Boolean, canDelete: Boolean): View {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.CENTER_VERTICAL
            setBackgroundResource(R.drawable.bg_episode_focus)
            isFocusable = true
            isClickable = true
            setPadding(dp(14), dp(12), dp(14), dp(12))
            val lp = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
            )
            lp.bottomMargin = dp(10)
            layoutParams = lp
        }
        val avatar = ImageView(this).apply {
            layoutParams = LinearLayout.LayoutParams(dp(56), dp(56))
            contentDescription = getString(R.string.profile_avatar)
            isFocusable = true
            isClickable = true
            setOnClickListener { editAvatar(p) }
        }
        Profiles.renderInto(this, avatar, p)
        row.addView(avatar)

        val name = TextView(this).apply {
            text = (if (isCur) "✓ " else "") + p.name
            textSize = 17f
            setTextColor(0xFFFFFFFF.toInt())
            setPadding(dp(14), 0, dp(8), 0)
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }
        row.addView(name)

        if (canDelete) {
            val del = Button(this).apply {
                text = "✕"
                textSize = 16f
                setTextColor(0xFFFFFFFF.toInt())
                background = null
                isFocusable = true
                isClickable = true
                setOnClickListener {
                    if (Profiles.delete(this@ProfileActivity, p.id)) {
                        Toast.makeText(this@ProfileActivity, R.string.profile_deleted, Toast.LENGTH_SHORT).show()
                        rebuild()
                    }
                }
            }
            row.addView(del)
        }
        row.setOnClickListener {
            Profiles.switch(this, p.id)
            finish()
        }
        return row
    }

    private fun askName() {
        if (Profiles.all(this).size >= Profiles.MAX_PROFILES) return
        val input = EditText(this).apply {
            hint = getString(R.string.profile_name_hint)
            isFocusable = true
            isFocusableInTouchMode = true
        }
        AlertDialog.Builder(this)
            .setTitle(R.string.profile_new)
            .setView(input)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                Profiles.create(this, input.text.toString().trim())
                rebuild()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun editAvatar(p: Profiles.Profile) {
        if (Ui.isTv(this)) {
            showQrReceive(p)
            return
        }
        AlertDialog.Builder(this)
            .setTitle(R.string.profile_avatar)
            .setItems(
                arrayOf(
                    getString(R.string.profile_gallery),
                    getString(R.string.profile_color)
                )
            ) { _, which ->
                if (which == 0) {
                    pickFromGallery(p.id)
                } else {
                    val list = Profiles.all(this)
                    val cur = list.firstOrNull { it.id == p.id } ?: return@setItems
                    val idx = Profiles.COLORS.indexOf(cur.color)
                    cur.color = Profiles.COLORS[(idx + 1) % Profiles.COLORS.size]
                    Profiles.update(this, cur)
                    rebuild()
                }
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun pickFromGallery(id: String) {
        galleryTarget = id
        try {
            startActivityForResult(
                Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                    type = "image/*"
                    addCategory(Intent.CATEGORY_OPENABLE)
                },
                41
            )
        } catch (_: Exception) {
            Toast.makeText(this, R.string.update_failed, Toast.LENGTH_SHORT).show()
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == 41 && resultCode == RESULT_OK) {
            val id = galleryTarget ?: return
            try {
                val uri = data?.data ?: return
                contentResolver.openInputStream(uri)?.use { inp ->
                    val out = Profiles.avatarFile(this, id)
                    val buf = ByteArray(8192)
                    var total = 0
                    out.outputStream().use { o ->
                        while (true) {
                            val n = inp.read(buf)
                            if (n < 0) break
                            total += n
                            if (total > PhotoServer.MAX_BYTES) throw IllegalStateException("too big")
                            o.write(buf, 0, n)
                        }
                    }
                }
                rebuild()
            } catch (_: Exception) {
                Toast.makeText(this, R.string.update_failed, Toast.LENGTH_SHORT).show()
            }
        }
    }

    // ---- Ricezione foto dal telefono via QR (TV) ----

    private fun stopPhotoServer() {
        try {
            photoSession?.stop()
        } catch (_: Exception) {
        }
        photoSession = null
    }

    private fun showQrReceive(p: Profiles.Profile) {
        stopPhotoServer()
        val ip = PhotoServer.localIp()
        if (ip == null) {
            Toast.makeText(this, R.string.update_failed, Toast.LENGTH_LONG).show()
            return
        }
        val token = (100000 + (Math.random() * 900000).toInt()).toString()
        val inbox = File(File(filesDir, "avatars").apply { mkdirs() }, "inbox.tmp")
        val session = PhotoServer.open(inbox, token)
        if (session == null) {
            Toast.makeText(this, R.string.update_failed, Toast.LENGTH_LONG).show()
            return
        }
        photoSession = session
        val url = "http://$ip:${session.port}/?t=$token"
        val qr = qrBitmap(url)
        val others = PhotoServer.localIps().filter { it != ip }
            .joinToString(", ") { "http://$it:${session.port}/" }

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
            text = url
            textSize = 13f
            setTextColor(0xFFA0A4B8.toInt())
            setPadding(0, dp(12), 0, 0)
        })
        box.addView(TextView(this).apply {
            text = getString(R.string.profile_qr_hint)
            textSize = 14f
            setTextColor(0xFFFFFFFF.toInt())
            setPadding(0, dp(8), 0, 0)
        })
        box.addView(TextView(this).apply {
            text = getString(R.string.qr_net_hint)
            textSize = 13f
            setTextColor(0xFFA0A4B8.toInt())
            setPadding(0, dp(8), 0, 0)
        })
        if (others.isNotEmpty()) {
            box.addView(TextView(this).apply {
                text = "Altri IP: $others"
                textSize = 12f
                setTextColor(0xFFA0A4B8.toInt())
                setPadding(0, dp(8), 0, 0)
            })
        }

        qrDialog = AlertDialog.Builder(this)
            .setTitle(R.string.profile_receive)
            .setView(box)
            .setNegativeButton(android.R.string.cancel) { _, _ -> stopPhotoServer() }
            .setOnDismissListener { stopPhotoServer() }
            .show()

        Thread {
            val end = System.currentTimeMillis() + 5 * 60 * 1000L
            var ok = false
            while (System.currentTimeMillis() < end && photoSession === session) {
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
            val done = ok && photoSession === session
            runOnUiThread {
                if (done) {
                    try {
                        val dest = Profiles.avatarFile(this, p.id)
                        dest.delete()
                        inbox.renameTo(dest)
                    } catch (_: Exception) {
                    }
                    rebuild()
                } else if (photoSession === session) {
                    Toast.makeText(this, R.string.profile_timeout, Toast.LENGTH_LONG).show()
                }
                try {
                    qrDialog?.dismiss()
                } catch (_: Exception) {
                }
            }
        }.start()
    }

    private fun qrBitmap(text: String): Bitmap? {
        return try {
            val px = 512
            val m = com.google.zxing.qrcode.QRCodeWriter().encode(
                text, com.google.zxing.BarcodeFormat.QR_CODE, px, px
            )
            Bitmap.createBitmap(px, px, Bitmap.Config.ARGB_8888).apply {
                for (x in 0 until px) {
                    for (y in 0 until px) {
                        setPixel(x, y, if (m.get(x, y)) 0xFF000000.toInt() else 0xFFFFFFFF.toInt())
                    }
                }
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()
}
