package com.cartoonmania.app

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View

/** Barre verticali con etichetta sotto e valore sopra (ore statistiche).
 *  Niente dipendenze, solo Canvas. */
class BarsView @JvmOverloads constructor(
    ctx: Context,
    attrs: AttributeSet? = null,
    def: Int = 0
) : View(ctx, attrs, def) {

    var items: List<Pair<String, Float>> = emptyList()
        set(v) {
            field = v
            requestLayout()
            invalidate()
        }

    var colors: List<Int> = listOf(0xFF7C5CFC.toInt())
    var emptyText: String = ""

    private val barP = Paint(Paint.ANTI_ALIAS_FLAG)
    private val gridP = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFF262633.toInt()
        strokeWidth = 1f
    }
    private val txtP = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFFA0A4B8.toInt()
        textAlign = Paint.Align.CENTER
    }
    private val valP = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFFFFFFFF.toInt()
        textAlign = Paint.Align.CENTER
    }

    private fun dp(v: Float): Float = v * resources.displayMetrics.density

    /** Versione scura del colore per il gradiente delle barre. */
    private fun darker(c: Int): Int {
        val r = ((c shr 16) and 0xFF) * 55 / 100
        val g = ((c shr 8) and 0xFF) * 55 / 100
        val b = (c and 0xFF) * 55 / 100
        return -0x1000000 or (r shl 16) or (g shl 8) or b
    }

    override fun onMeasure(wSpec: Int, hSpec: Int) {
        // Altezza fissa 190dp: entra in header senza ricalcoli strani
        setMeasuredDimension(
            MeasureSpec.getSize(wSpec),
            (190 * resources.displayMetrics.density).toInt()
        )
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0 || h <= 0) return
        txtP.textSize = dp(11f)
        valP.textSize = dp(11f)
        if (items.isEmpty()) {
            canvas.drawText(emptyText, w / 2f, h / 2f, txtP)
            return
        }
        val max = (items.maxOfOrNull { it.second } ?: 0f).coerceAtLeast(0.01f)
        val topPad = dp(24f)
        val botPad = dp(24f)
        // Linea di base sottile
        canvas.drawLine(0f, h - botPad, w, h - botPad, gridP)
        val slot = w / items.size
        val barW = (slot * 0.52f).coerceAtMost(dp(46f))
        items.forEachIndexed { i, (label, value) ->
            val cx = slot * i + slot / 2f
            val frac = (value / max).coerceIn(0f, 1f)
            // Altezza minima visibile per valori piccoli ma non nulli
            var barH = (h - topPad - botPad) * frac
            if (value > 0f && barH < dp(6f)) barH = dp(6f)
            val left = cx - barW / 2f
            val top = h - botPad - barH
            val bottom = h - botPad
            val base = colors[i % colors.size]
            if (barH > 0) {
                barP.shader = android.graphics.LinearGradient(
                    0f, top, 0f, bottom, base, darker(base),
                    android.graphics.Shader.TileMode.CLAMP
                )
                canvas.drawRoundRect(left, top, cx + barW / 2f, bottom, dp(8f), dp(8f), barP)
                barP.shader = null
            }
            // Valore sopra solo se significativo (niente zeri a sporcare)
            if (value >= 1f / 60f) {
                canvas.drawText(shortDur(value), cx, top - dp(5f), valP)
            }
            canvas.drawText(
                if (label.length > 7) label.take(7) + "…" else label,
                cx, h - dp(5f), txtP
            )
        }
    }

    /** Ore in input: "2h05" compatto per stare sopra la barra. */
    private fun shortDur(hours: Float): String {
        val mins = (hours * 60).toInt()
        val hh = mins / 60
        val mm = mins % 60
        return if (hh > 0) "${hh}h${if (mm > 0) "%02d".format(mm) else ""}" else "${mm}m"
    }
}
