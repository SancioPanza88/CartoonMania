package com.cartoonmania.app

import android.app.Activity
import android.graphics.BitmapFactory
import android.util.LruCache
import android.widget.ImageView
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

object ImageLoader {

    private val executor: ExecutorService = Executors.newFixedThreadPool(3)

    private val cache = object : LruCache<String, android.graphics.Bitmap>(30) {
        override fun sizeOf(key: String, value: android.graphics.Bitmap) = 1
    }

    fun display(iv: ImageView, url: String?) {
        iv.setImageBitmap(null)
        iv.tag = url
        if (url.isNullOrEmpty()) return
        cache.get(url)?.let {
            if (iv.tag == url) iv.setImageBitmap(it)
            return
        }
        executor.execute {
            val bmp = fetch(url) ?: return@execute
            cache.put(url, bmp)
            iv.post {
                if (iv.tag == url) iv.setImageBitmap(bmp)
            }
        }
    }

    private fun fetch(url: String): android.graphics.Bitmap? = try {
        val c = URL(url).openConnection() as HttpURLConnection
        c.connectTimeout = 10000
        c.readTimeout = 20000
        try {
            if (c.responseCode != 200) null
            else BitmapFactory.decodeStream(c.inputStream)
        } finally {
            c.disconnect()
        }
    } catch (_: Exception) {
        null
    }

    fun preload(activity: Activity) {}
}
