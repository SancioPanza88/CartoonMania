package com.cartoonmania.app

import android.content.Context
import android.view.View
import android.view.ViewOutlineProvider
import android.widget.TextView

object Ui {

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
        v.onFocusChangeListener = View.OnFocusChangeListener { view, has ->
            val s = if (has) 1.07f else 1f
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
                onFocusChangeListener = View.OnFocusChangeListener { view, has ->
                    view.animate().scaleX(if (has) 1.08f else 1f)
                        .scaleY(if (has) 1.08f else 1f)
                        .setDuration(120).start()
                }
            }
        }
        return c
    }

    fun dp(ctx: Context, v: Int): Int =
        (v * ctx.resources.displayMetrics.density).toInt()
}
