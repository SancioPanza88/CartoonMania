package com.cartoonmania.app

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import android.widget.ImageView
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

object ImageLoader {

    private val executor: ExecutorService = Executors.newFixedThreadPool(3)

    private val maxKb = (Runtime.getRuntime().maxMemory() / 1024L / 6L).toInt()

    private val cache = object : LruCache<String, Bitmap>(maxKb) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount / 1024
    }

    fun display(iv: ImageView, url: String?) {
        iv.setImageBitmap(null)
        iv.tag = url
        if (url.isNullOrEmpty()) return
        synchronized(cache) { cache.get(url) }?.let {
            if (iv.tag == url) iv.setImageBitmap(it)
            return
        }
        executor.execute {
            val bmp = fetch(url) ?: return@execute
            synchronized(cache) { cache.put(url, bmp) }
            iv.post {
                if (iv.tag == url) {
                    iv.setImageBitmap(bmp)
                    iv.alpha = 0f
                    iv.animate().alpha(1f).setDuration(180).start()
                }
            }
        }
    }

    private fun fetch(url: String): Bitmap? = try {
        val c = URL(url).openConnection() as HttpURLConnection
        c.connectTimeout = 10000
        c.readTimeout = 20000
        try {
            if (c.responseCode != 200) null
            else {
                val opts = BitmapFactory.Options().apply {
                    inPreferredConfig = Bitmap.Config.RGB_565
                }
                BitmapFactory.decodeStream(c.inputStream, null, opts)
            }
        } finally {
            c.disconnect()
        }
    } catch (_: Exception) {
        null
    }
}
