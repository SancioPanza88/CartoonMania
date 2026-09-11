package com.cartoonmania.app

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View

/** Ciambella quote per genere (ore statistiche). La legenda la costruisce
 *  StatsActivity con TextView: qui solo gli archi. */
class DonutView @JvmOverloads constructor(
    ctx: Context,
    attrs: AttributeSet? = null,
    def: Int = 0
) : View(ctx, attrs, def) {

    var items: List<Pair<String, Float>> = emptyList()
        set(v) {
            field = v
            invalidate()
        }

    var colors: List<Int> = listOf(
        0xFF7C5CFC.toInt(), 0xFF00BFA6.toInt(), 0xFFFF7043.toInt(),
        0xFF42A5F5.toInt(), 0xFFEC407A.toInt(), 0xFFFFAB00.toInt()
    )
    var emptyText: String = ""
    var centerText: String = ""
    var trackColor: Int = 0xFF262633.toInt()

    private val arcP = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }
    private val trackP = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }
    private val txtP = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFFA0A4B8.toInt()
        textAlign = Paint.Align.CENTER
        textSize = 13f
    }

    private val bigP = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFFFFFFFF.toInt()
        textAlign = Paint.Align.CENTER
        isFakeBoldText = true
    }

    private fun dp(v: Float): Float = v * resources.displayMetrics.density
    private val oval = RectF()

    override fun onMeasure(wSpec: Int, hSpec: Int) {
        val s = (200 * resources.displayMetrics.density).toInt()
        val w = MeasureSpec.getSize(wSpec)
        // Quadrata, centrata dal parent
        val side = s.coerceAtMost(w)
        setMeasuredDimension(w, side)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0 || h <= 0) return
        val total = items.sumOf { it.second.toDouble() }.toFloat()
        if (total <= 0f) {
            canvas.drawText(emptyText, w / 2f, h / 2f, txtP)
            return
        }
        val stroke = dp(30f)
        arcP.strokeWidth = stroke
        trackP.strokeWidth = stroke
        trackP.color = trackColor
        val pad = stroke / 2f + dp(8f)
        oval.set(pad, pad, w - pad, h - pad)
        // Anello di fondo + fette con estremita' arrotondate
        canvas.drawArc(oval, 0f, 360f, false, trackP)
        var start = -90f
        items.forEachIndexed { i, (_, value) ->
            if (value <= 0f) return@forEachIndexed
            val sweep = value / total * 360f
            arcP.color = colors[i % colors.size]
            // Gap tra fette (niente gap su fetta unica)
            val gap = if (items.count { it.second > 0f } > 1) 3f else 0f
            canvas.drawArc(oval, start + gap / 2f, (sweep - gap).coerceAtLeast(0.5f), false, arcP)
            start += sweep
        }
        // Totale al centro
        if (centerText.isNotEmpty()) {
            bigP.textSize = dp(22f)
            canvas.drawText(centerText, w / 2f, h / 2f + dp(3f), bigP)
        }
    }
}
