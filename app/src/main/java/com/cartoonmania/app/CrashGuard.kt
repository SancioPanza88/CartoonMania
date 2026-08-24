package com.cartoonmania.app

import android.content.Context
import android.os.Environment
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter

object CrashGuard {

    fun crashFile(ctx: Context) = File(ctx.getExternalFilesDir(null) ?: ctx.filesDir, "crash.txt")

    fun lastCrashReport(ctx: Context): String? {
        val f = crashFile(ctx)
        return if (f.exists()) f.readText().take(2000) else null
    }

    fun clear(ctx: Context) {
        crashFile(ctx).delete()
    }

    fun install(ctx: Context) {
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            try {
                val sw = StringWriter()
                throwable.printStackTrace(PrintWriter(sw))
                val extra = try {
                    "\n\nTITOLI CARICATI: ${CatalogRepo.titles.size}"
                } catch (_: Exception) {
                    ""
                }
                crashFile(ctx).writeText(
                    "Thread: ${thread.name}\n$sw$extra\n" +
                        "External storage: ${Environment.getExternalStorageState()}\n"
                )
            } catch (_: Exception) {
            }
            previous?.uncaughtException(thread, throwable)
        }
    }
}
