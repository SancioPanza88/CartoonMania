package com.cartoonmania.app

import android.content.Context
import android.graphics.Outline
import android.view.View
import android.view.ViewOutlineProvider
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

    fun dp(ctx: Context, v: Int): Int =
        (v * ctx.resources.displayMetrics.density).toInt()
}
