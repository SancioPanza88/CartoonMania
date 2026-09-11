package com.cartoonmania.app

import android.app.Activity
import android.content.Context
import android.view.View

/** Modalita' tubo catodico per Firestick su vecchie TV: niente effetti
 *  (le scanline le fa gia' il tubo), solo adattamenti pratici —
 *  bordi sicuri anti-overscan, aspetto video, testi anti-sfarfallio.
 *  Preferenze nello stesso file "cm" del resto dell'app. */
object CrtMode {

    private const val PREFS = "cm"
    private const val KEY_ENABLED = "crt_enabled"
    private const val KEY_OVERSCAN = "crt_overscan_dp"
    private const val KEY_ASPECT = "crt_aspect"

    const val OVERSCAN_STEP = 8
    const val OVERSCAN_MAX = 64
    const val OVERSCAN_DEFAULT = 28

    /** 0 = FIT (adatta), 1 = FILL (riempi), 2 = ZOOM (ritaglia). */
    const val ASPECT_FIT = 0
    const val ASPECT_FILL = 1
    const val ASPECT_ZOOM = 2

    fun isEnabled(ctx: Context): Boolean = try {
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY_ENABLED, false)
    } catch (_: Exception) {
        false
    }

    fun overscanDp(ctx: Context): Int = try {
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getInt(KEY_OVERSCAN, OVERSCAN_DEFAULT)
    } catch (_: Exception) {
        OVERSCAN_DEFAULT
    }

    fun aspect(ctx: Context): Int = try {
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getInt(KEY_ASPECT, ASPECT_FIT)
    } catch (_: Exception) {
        ASPECT_FIT
    }

    fun setAspect(ctx: Context, v: Int) {
        try {
            ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                .putInt(KEY_ASPECT, v.coerceIn(ASPECT_FIT, ASPECT_ZOOM)).apply()
        } catch (_: Exception) {
        }
    }

    fun aspectLabel(v: Int): String = when (v) {
        ASPECT_FILL -> "FILL"
        ASPECT_ZOOM -> "ZOOM"
        else -> "FIT"
    }

    /** Accende/spegne e ricarica l'activity per applicare subito. */
    fun setEnabled(a: Activity, v: Boolean) {
        try {
            a.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                .putBoolean(KEY_ENABLED, v).apply()
        } catch (_: Exception) {
        }
        try {
            a.recreate()
        } catch (_: Exception) {
        }
    }

    fun addOverscan(ctx: Context, deltaSteps: Int): Int {
        return try {
            val cur = overscanDp(ctx)
            val next = (cur + deltaSteps * OVERSCAN_STEP).coerceIn(0, OVERSCAN_MAX)
            ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                .putInt(KEY_OVERSCAN, next).apply()
            next
        } catch (_: Exception) {
            overscanDp(ctx)
        }
    }

    /** Padding anti-overscan sulla radice: con sfondi a tinta unita sposta
     *  solo i contenuti nella safe-area, video incluso. */
    fun applyTo(a: Activity) {
        if (!isEnabled(a)) return
        try {
            val px = (overscanDp(a) * a.resources.displayMetrics.density).toInt()
            if (px <= 0) return
            val root = a.findViewById<View>(android.R.id.content) ?: return
            root.setPadding(px, px, px, px)
        } catch (_: Exception) {
        }
    }

    /** Testi un filo piu' grandi contro lo sfarfallio interlacciato. */
    fun bigText(ctx: Context, baseSp: Float): Float =
        if (isEnabled(ctx)) baseSp + 2f else baseSp
}
