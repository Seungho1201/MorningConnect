package com.example.morningconnect

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import androidx.core.graphics.ColorUtils
import kotlin.math.min

private const val SKIN = 0xFFE9C9A8.toInt()
private const val HAIR = 0xFF3A2E2A.toInt()
private const val OUTLINE = 0xFFC9D0CB.toInt()

// 오늘의 룩 캐릭터 (머리 · 상의 · 하의 · 신발 · 가방 · 모자). 좌표는 190x320 기준
class CharacterView(context: Context, attrs: AttributeSet? = null) : View(context, attrs) {
    var look: Look = Look.DEFAULT
        set(value) { field = value; contentDescription = "캐릭터: ${value.summary()}"; invalidate() }

    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val line = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND }

    init { importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_YES }

    override fun onDraw(canvas: Canvas) {
        val w = (width - paddingLeft - paddingRight).toFloat()
        val h = (height - paddingTop - paddingBottom).toFloat()
        if (w <= 0 || h <= 0) return
        val s = min(w / W, h / H)
        canvas.translate(paddingLeft + (w - W * s) / 2, paddingTop + (h - H * s) / 2)
        canvas.scale(s, s)

        val top = look["상의"]; val bottom = look["하의"]; val shoes = look["신발"]
        val bag = look["가방"]; val hat = look["모자"]

        fun rect(l: Float, t: Float, r: Float, b: Float, color: Int, radii: FloatArray, stroke: Int = 0) {
            val p = Path().apply { addRoundRect(RectF(l, t, r, b), radii, Path.Direction.CW) }
            fill.color = color
            canvas.drawPath(p, fill)
            if (stroke != 0) { line.color = stroke; line.strokeWidth = 2.5f; canvas.drawPath(p, line) }
        }
        fun r(all: Float) = FloatArray(8) { all }
        fun r(top: Float, bottom: Float) = floatArrayOf(top, top, top, top, bottom, bottom, bottom, bottom)
        val darker = { c: Int -> ColorUtils.blendARGB(c, 0xFF000000.toInt(), 0.25f) }
        // 흰 옷은 바탕과 구분되게 테두리
        fun edge(c: Int) = if (ColorUtils.calculateLuminance(c) > 0.8) OUTLINE else 0

        // 백팩은 몸 뒤에
        if (bag.kind == "백팩") rect(CX - 62, 92f, CX + 62, 200f, darker(bag.argb), r(26f))

        // 다리 / 하의
        val legTop = 196f; val legBottom = 286f
        val lL = CX - 68; val lR = CX - 4; val rL = CX + 4; val rR = CX + 68
        when (bottom.kind) {
            "반바지" -> {
                rect(lL + 10, legTop, lR - 10, legBottom, SKIN, r(8f)); rect(rL + 10, legTop, rR - 10, legBottom, SKIN, r(8f))
                rect(lL, legTop, lR, legTop + 42, bottom.argb, r(0f, 10f), edge(bottom.argb))
                rect(rL, legTop, rR, legTop + 42, bottom.argb, r(0f, 10f), edge(bottom.argb))
            }
            "치마" -> {
                rect(lL + 10, legTop, lR - 10, legBottom, SKIN, r(8f)); rect(rL + 10, legTop, rR - 10, legBottom, SKIN, r(8f))
                val p = Path().apply { moveTo(CX - 66, legTop - 4); lineTo(CX + 66, legTop - 4); lineTo(CX + 82, 250f); lineTo(CX - 82, 250f); close() }
                fill.color = bottom.argb; canvas.drawPath(p, fill)
            }
            else -> {
                rect(lL, legTop, lR, legBottom, bottom.argb, r(0f, 8f), edge(bottom.argb))
                rect(rL, legTop, rR, legBottom, bottom.argb, r(0f, 8f), edge(bottom.argb))
                if (bottom.kind == "청바지") {
                    line.color = darker(bottom.argb); line.strokeWidth = 2f
                    canvas.drawLine(lR - 14, legTop + 8, lR - 14, legBottom - 6, line)
                    canvas.drawLine(rL + 14, legTop + 8, rL + 14, legBottom - 6, line)
                }
            }
        }

        // 신발
        when (shoes.kind) {
            "부츠" -> {
                rect(lL - 2, 252f, lR + 2, 312f, shoes.argb, r(10f, 12f), edge(shoes.argb))
                rect(rL - 2, 252f, rR + 2, 312f, shoes.argb, r(10f, 12f), edge(shoes.argb))
            }
            "구두" -> {
                rect(lL - 2, 290f, lR + 4, 310f, shoes.argb, r(10f, 6f), edge(shoes.argb))
                rect(rL - 4, 290f, rR + 2, 310f, shoes.argb, r(10f, 6f), edge(shoes.argb))
            }
            "샌들" -> {
                rect(lL - 2, 302f, lR + 2, 312f, shoes.argb, r(5f), edge(shoes.argb))
                rect(rL - 2, 302f, rR + 2, 312f, shoes.argb, r(5f), edge(shoes.argb))
                line.color = shoes.argb; line.strokeWidth = 5f
                canvas.drawLine(lL + 8, 292f, lR - 8, 300f, line); canvas.drawLine(rL + 8, 300f, rR - 8, 292f, line)
            }
            else -> {
                rect(lL - 3, 287f, lR + 3, 312f, shoes.argb, r(12f, 10f), OUTLINE)
                rect(rL - 3, 287f, rR + 3, 312f, shoes.argb, r(12f, 10f), OUTLINE)
            }
        }

        // 몸통 / 상의 (코트는 길게)
        val bodyBottom = if (top.kind == "코트") 236f else 200f
        if (top.kind == "후드티") rect(CX - 46, 70f, CX + 46, 110f, darker(top.argb), r(22f))
        rect(CX - 75, 84f, CX + 75, bodyBottom, top.argb, r(40f, 10f), edge(top.argb))
        line.color = darker(top.argb); line.strokeWidth = 2.5f
        when (top.kind) {
            "패딩" -> listOf(118f, 150f, 180f).forEach { y -> canvas.drawLine(CX - 70, y, CX + 70, y, line) }
            "코트" -> { canvas.drawLine(CX, 96f, CX, bodyBottom - 4, line); listOf(130f, 170f).forEach { canvas.drawCircle(CX + 12, it, 3.5f, line) } }
            "셔츠" -> {
                val p = Path().apply { moveTo(CX - 20, 86f); lineTo(CX, 110f); lineTo(CX + 20, 86f) }
                canvas.drawPath(p, line); canvas.drawLine(CX, 110f, CX, bodyBottom - 6, line)
            }
            "니트" -> listOf(186f, 193f).forEach { y -> canvas.drawLine(CX - 70, y, CX + 70, y, line) }
            "후드티" -> rect(CX - 36, 150f, CX + 36, 186f, darker(top.argb), r(10f))
        }

        // 가방 (앞)
        when (bag.kind) {
            "백팩" -> { line.color = darker(bag.argb); line.strokeWidth = 7f
                canvas.drawLine(CX - 44, 90f, CX - 44, 176f, line); canvas.drawLine(CX + 44, 90f, CX + 44, 176f, line) }
            "숄더백" -> { line.color = darker(bag.argb); line.strokeWidth = 6f
                canvas.drawLine(CX - 50, 90f, CX + 70, 196f, line)
                rect(CX + 52, 186f, CX + 94, 222f, bag.argb, r(8f), edge(bag.argb)) }
            "에코백" -> { line.color = bag.argb; line.strokeWidth = 5f
                canvas.drawLine(CX + 62, 96f, CX + 66, 196f, line)
                rect(CX + 46, 190f, CX + 92, 246f, bag.argb, r(4f), OUTLINE) }
        }

        // 머리
        fill.color = SKIN
        canvas.drawCircle(CX, 50f, 37f, fill)
        rect(CX - 42, 8f, CX + 42, 34f, HAIR, floatArrayOf(40f, 40f, 40f, 40f, 4f, 4f, 4f, 4f))

        // 모자
        when (hat.kind) {
            "캡모자" -> { rect(CX - 42, 2f, CX + 42, 32f, hat.argb, r(36f, 2f), edge(hat.argb)); rect(CX - 10, 24f, CX + 66, 34f, hat.argb, r(6f), edge(hat.argb)) }
            "비니" -> { rect(CX - 40, 0f, CX + 40, 34f, hat.argb, r(34f, 4f), edge(hat.argb)); rect(CX - 43, 24f, CX + 43, 38f, darker(hat.argb), r(6f)) }
            "버킷햇" -> { rect(CX - 34, 0f, CX + 34, 30f, hat.argb, r(20f, 2f), edge(hat.argb)); rect(CX - 56, 26f, CX + 56, 38f, hat.argb, r(6f), edge(hat.argb)) }
        }
    }

    companion object {
        private const val W = 190f
        private const val H = 320f
        private const val CX = W / 2
    }
}
