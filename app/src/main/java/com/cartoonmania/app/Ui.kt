package com.cartoonmania.app

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Outline
import android.view.MotionEvent
import android.view.View
import android.view.ViewOutlineProvider
import android.view.animation.AnimationUtils
import android.widget.ListView
import android.widget.TextView

object Ui {

    fun isTv(ctx: Context): Boolean {
        return try {
            val um = ctx.getSystemService(Context.UI_MODE_SERVICE) as android.app.UiModeManager
            um.currentModeType == android.content.res.Configuration.UI_MODE_TYPE_TELEVISION
        } catch (_: Exception) {
            false
        }
    }

    fun round(view: View, radiusDp: Int) {
        val r = radiusDp * view.resources.displayMetrics.density
        view.clipToOutline = true
        view.outlineProvider = object : ViewOutlineProvider() {
            override fun getOutline(view: View, outline: Outline) {
                outline.setRoundRect(0, 0, view.width, view.height, r)
            }
        }
    }

    fun tvFocus(v: View) {
        v.isFocusable = true
        v.isClickable = true
        // Sulla TV lo zoom deve vedersi da 3 metri
        val zoom = if (isTv(v.context)) 1.12f else 1.07f
        v.onFocusChangeListener = View.OnFocusChangeListener { view, has ->
            val s = if (has) zoom else 1f
            view.animate().scaleX(s).scaleY(s).setDuration(120).start()
            view.translationZ = if (has) 10f else 0f
        }
    }

    fun chip(parent: Context, text: String, onClick: ((Context) -> Unit)? = null): TextView {
        val c = TextView(parent).apply {
            this.text = text
            textSize = 12f
            setTextColor(0xFFF2F2F7.toInt())
            setBackgroundResource(R.drawable.bg_chip)
            setPadding(dp(parent, 14), dp(parent, 6), dp(parent, 14), dp(parent, 6))
            if (onClick != null) {
                isFocusable = true
                isClickable = true
                setOnClickListener { onClick(it.context) }
                val zoom = if (isTv(parent)) 1.12f else 1.08f
                onFocusChangeListener = View.OnFocusChangeListener { view, has ->
                    view.animate().scaleX(if (has) zoom else 1f)
                        .scaleY(if (has) zoom else 1f)
                        .setDuration(120).start()
                }
            }
        }
        return c
    }

    /** Blocca il focus ai bordi di una riga orizzontale: a fine riga la freccia
     *  resta dov'e' invece di cadere sulla riga sotto. */
    fun clampHorizontalRow(views: List<View>) {
        if (views.isEmpty()) return
        for (v in views) if (v.id == View.NO_ID) v.id = View.generateViewId()
        views.first().nextFocusLeftId = views.first().id
        views.last().nextFocusRightId = views.last().id
    }

    /** Ingresso scaglionato delle righe home: dissolvenza ovunque, scorrimento
     *  verticale solo su telefono (sulla TV la GPU scarsa scatta). */
    fun rowEnter(v: View, index: Int) {
        try {
            val tv = isTv(v.context)
            val density = v.resources.displayMetrics.density
            v.alpha = 0f
            if (!tv) v.translationY = 20f * density
            v.animate().alpha(1f).translationY(0f)
                .setStartDelay(minOf(index * 45, 420).toLong())
                .setDuration(if (tv) 150 else 260).start()
        } catch (_: Exception) {
            try { v.alpha = 1f; v.translationY = 0f } catch (_: Exception) { }
        }
    }

    /** Feedback pressione touch: rimbalzo leggero. Non consuma l'evento
     *  (ritorna false) cosi' click e long-press restano intatti. */
    fun pressPop(v: View) {
        try {
            v.setOnTouchListener { view, e ->
                try {
                    when (e.action) {
                        MotionEvent.ACTION_DOWN ->
                            view.animate().scaleX(0.95f).scaleY(0.95f).setDuration(80).start()
                        MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                            val s = if (view.isFocused) {
                                if (isTv(view.context)) 1.12f else 1.07f
                            } else 1f
                            view.animate().scaleX(s).scaleY(s).setDuration(120).start()
                        }
                    }
                } catch (_: Exception) {
                }
                false
            }
        } catch (_: Exception) {
        }
    }

    /** Dissolvenza a cascata degli elementi di una lista (solo alpha: sicura
     *  anche sulle TV). Da chiamare una volta, non a ogni refilter. */
    fun fadeList(lv: ListView) {
        try {
            lv.layoutAnimation =
                AnimationUtils.loadLayoutAnimation(lv.context, R.anim.cm_list_layout)
        } catch (_: Exception) {
        }
    }

    /** Apre i dettagli con transizione di entrata (l'uscita e' gestita dal
     *  tema + finish() delle activity). */
    fun openDetail(ctx: Context, slug: String) {
        try {
            ctx.startActivity(Intent(ctx, DetailActivity::class.java).putExtra("slug", slug))
            if (ctx is Activity) {
                ctx.overridePendingTransition(R.anim.cm_open_enter, R.anim.cm_open_exit)
            }
        } catch (_: Exception) {
        }
    }

    /** Come openDetail ma con intent gia' pronto (search, settings, ...). */
    fun openScreen(ctx: Context, intent: Intent) {
        try {
            ctx.startActivity(intent)
            if (ctx is Activity) {
                ctx.overridePendingTransition(R.anim.cm_open_enter, R.anim.cm_open_exit)
            }
        } catch (_: Exception) {
        }
    }

    /** Transizione di chiusura da usare in finish() delle activity. */
    fun applyCloseTransition(a: Activity) {
        try {
            a.overridePendingTransition(R.anim.cm_close_enter, R.anim.cm_close_exit)
        } catch (_: Exception) {
        }
    }

    fun dp(ctx: Context, v: Int): Int =
        (v * ctx.resources.displayMetrics.density).toInt()
}
