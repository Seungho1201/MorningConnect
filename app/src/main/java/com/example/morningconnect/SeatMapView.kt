package com.example.morningconnect

import android.content.Context
import android.graphics.Canvas
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.util.AttributeSet
import android.util.TypedValue
import android.view.MotionEvent
import android.view.View

/**
 * 객차 좌석 배치: 위·아래 줄마다 [문][의자][문][의자][문][의자][문].
 * 노란 칸 = 문, 회색 칸 = 긴 의자. 선택한 의자는 강조색으로 칠한다.
 */
class SeatMapView(context: Context, attrs: AttributeSet? = null) : View(context, attrs) {
    var selectedRow = 0
    var selectedBench = 1
    var highlight = context.getColor(R.color.green_dark)
    var label: String? = "내 자리"
    var aisleText = "통로 · 진행 방향 →"
    var dashedAisle = true
    var onPick: ((row: Int, bench: Int) -> Unit)? = null
        set(value) { field = value; isClickable = value != null }

    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val dash = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 1.5f.dpf
        color = 0xFFAEB6B0.toInt()
        pathEffect = DashPathEffect(floatArrayOf(5f.dpf, 4f.dpf), 0f)
    }
    private val text = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER }

    private val rowH get() = 34f.dpf
    private val aisleH get() = 32f.dpf
    private val gap get() = 6f.dpf

    fun select(row: Int, bench: Int) {
        selectedRow = row; selectedBench = bench
        invalidate()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val w = MeasureSpec.getSize(widthMeasureSpec)
        setMeasuredDimension(w, (rowH * 2 + aisleH + gap * 2).toInt() + paddingTop + paddingBottom)
    }

    // 한 줄의 칸 위치: 문 4개(폭 1) + 의자 3개(폭 3.2), 사이 간격
    private fun cells(): List<Pair<Float, Float>> {
        val total = width - paddingLeft - paddingRight - gap * 6
        val unit = total / (4 * 1f + 3 * 3.2f)
        var x = paddingLeft.toFloat()
        return (0 until 7).map { i ->
            val w = if (i % 2 == 0) unit else unit * 3.2f
            val cell = x to x + w
            x += w + gap
            cell
        }
    }

    private fun rowTop(row: Int) = paddingTop + if (row == 0) 0f else rowH + gap * 2 + aisleH

    override fun onDraw(canvas: Canvas) {
        val cells = cells()
        val radius = 6f.dpf
        for (row in 0..1) {
            val t = rowTop(row)
            cells.forEachIndexed { i, (l, r) ->
                val bench = i / 2
                val on = i % 2 == 1 && row == selectedRow && bench == selectedBench
                fill.color = when {
                    i % 2 == 0 -> context.getColor(R.color.door)
                    on -> highlight
                    else -> context.getColor(R.color.seat)
                }
                val rect = RectF(l, t, r, t + rowH)
                canvas.drawRoundRect(rect, radius, radius, fill)
                if (on && label != null) {
                    text.color = context.getColor(R.color.white)
                    text.textSize = 14f.spf
                    text.typeface = Typeface.DEFAULT_BOLD
                    canvas.drawText(label!!, rect.centerX(), rect.centerY() - (text.descent() + text.ascent()) / 2, text)
                }
            }
        }
        val aisle = RectF(paddingLeft.toFloat(), rowH + gap + paddingTop, (width - paddingRight).toFloat(), rowH + gap + aisleH + paddingTop)
        if (dashedAisle) canvas.drawRoundRect(aisle, radius, radius, dash)
        text.color = context.getColor(R.color.sub)
        text.textSize = 13f.spf
        text.typeface = Typeface.DEFAULT
        canvas.drawText(aisleText, aisle.centerX(), aisle.centerY() - (text.descent() + text.ascent()) / 2, text)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        val pick = onPick ?: return super.onTouchEvent(event)
        if (event.action == MotionEvent.ACTION_DOWN) return true
        if (event.action != MotionEvent.ACTION_UP) return super.onTouchEvent(event)
        val row = when {
            event.y in rowTop(0)..(rowTop(0) + rowH + gap) -> 0
            event.y >= rowTop(1) - gap -> 1
            else -> return true
        }
        val i = cells().indexOfFirst { (l, r) -> event.x in (l - gap / 2)..(r + gap / 2) }
        if (i >= 0 && i % 2 == 1) {
            select(row, i / 2)
            pick(row, i / 2)
            performClick()
        }
        return true
    }

    override fun performClick(): Boolean = super.performClick()

    private val Float.dpf get() = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, this, resources.displayMetrics)
    private val Float.spf get() = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, this, resources.displayMetrics)
}
