package com.cartoonmania.app

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import android.widget.ImageView
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicLong

object ImageLoader {

    private val executor: ExecutorService = Executors.newFixedThreadPool(10)
    private val counter = AtomicLong(0)

    private val maxKb = (Runtime.getRuntime().maxMemory() / 1024L / 4L).toInt()

    private val cache = object : LruCache<String, Bitmap>(maxKb) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount / 1024
    }

    private const val TARGET_W = 320
    private const val MAX_DISK_MB = 96L

    fun display(iv: ImageView, url: String?) {
        iv.setImageBitmap(null)
        iv.tag = url
        if (url.isNullOrEmpty()) return
        synchronized(cache) { cache.get(url) }?.let {
            if (iv.tag == url) iv.setImageBitmap(it)
            return
        }
        val ctx = iv.context.applicationContext
        executor.execute {
            val bmp = try { fetch(url, ctx) } catch (_: Throwable) { null } ?: return@execute
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

    private fun cacheDir(ctx: Context): File = File(ctx.cacheDir, "posters").apply { mkdirs() }

    private fun fetch(url: String, ctx: Context): Bitmap? {
        val dir = cacheDir(ctx)
        val f = File(dir, md5(url) + ".img")
        val data: ByteArray? =
            if (f.exists() && f.length() > 0) {
                f.readBytes()
            } else {
                val c = URL(url).openConnection() as HttpURLConnection
                c.connectTimeout = 8000
                c.readTimeout = 20000
                try {
                    if (c.responseCode != 200) null
                    else {
                        c.inputStream.use { input ->
                            val tmp = File(dir, "tmp_${System.nanoTime()}.part")
                            tmp.outputStream().use { input.copyTo(it, 1 shl 15) }
                            val b = tmp.readBytes()
                            if (!tmp.renameTo(f)) tmp.delete()
                            b
                        }
                    }
                } catch (e: Throwable) {
                    try { c.disconnect() } catch (_: Throwable) { }
                    throw e
                }
            }
        if (counter.incrementAndGet() % 64 == 0L) prune(dir)
        return data?.let { decode(it) }
    }

    fun clearDiskCache(ctx: Context): Long {
        synchronized(cache) { cache.evictAll() }
        var freed = 0L
        val dir = cacheDir(ctx)
        dir.listFiles()?.forEach {
            val len = it.length()
            if (it.delete()) freed += len
        }
        return freed / (1024 * 1024)
    }

    private fun decode(bytes: ByteArray): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        var sample = 1
        while (bounds.outWidth / (sample * 2) >= TARGET_W) sample *= 2
        val opts = BitmapFactory.Options().apply {
            inSampleSize = sample
            inPreferredConfig = Bitmap.Config.RGB_565
        }
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts)
    }

    private fun prune(dir: File) {
        try {
            val files = dir.listFiles()?.filter { it.name.startsWith("t_").not() } ?: return
            var total = files.sumOf { it.length() }
            if (total <= MAX_DISK_MB * 1024 * 1024) return
            for (f in files.sortedBy { it.lastModified() }) {
                if (total <= MAX_DISK_MB * 1024 * 1024 * 3 / 4) break
                val len = f.length()
                if (f.delete()) total -= len
            }
        } catch (_: Throwable) { }
    }

    private fun md5(s: String): String =
        MessageDigest.getInstance("MD5").digest(s.toByteArray())
            .joinToString("") { "%02x".format(it) }
}
