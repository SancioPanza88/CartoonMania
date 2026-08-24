package com.cartoonmania.app

import android.graphics.Outline
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

    fun chip(parent: android.content.Context, text: String): TextView {
        val c = TextView(parent).apply {
            text = text
            textSize = 12f
            setTextColor(0xFFF2F2F7.toInt())
            setBackgroundResource(R.drawable.bg_chip)
            setPadding(dp(parent, 14), dp(parent, 6), dp(parent, 14), dp(parent, 6))
        }
        return c
    }

    fun dp(ctx: android.content.Context, v: Int): Int =
        (v * ctx.resources.displayMetrics.density).toInt()
}
