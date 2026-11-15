package com.example.morningconnect

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.util.AttributeSet
import android.view.View
import kotlin.math.min

class Part(var color: String, var style: String)

val CLOTH_COLORS = linkedMapOf(
    "검정" to 0xFF2A2A2A.toInt(), "흰색" to 0xFFF7F7F2.toInt(), "회색" to 0xFF9AA0A3.toInt(), "남색" to 0xFF2C3E6B.toInt(),
    "파랑" to 0xFF4A86D9.toInt(), "초록" to 0xFF4CA555.toInt(), "빨강" to 0xFFD9483B.toInt(), "베이지" to 0xFFD9C3A0.toInt(),
)

private const val INK = 0xFF1E2420.toInt()
private const val SKIN = 0xFFF1D2B6.toInt()

// 오늘의 룩 캐릭터. 좌표는 디자인 SVG(viewBox 160x210) 그대로
class CharacterView(context: Context, attrs: AttributeSet? = null) : View(context, attrs) {
    // 키: 상의, 하의, 겉옷, 가방
    var parts: Map<String, Part> = mapOf(
        "상의" to Part("초록", "니트"), "하의" to Part("검정", "바지"),
        "겉옷" to Part("남색", "코트"), "가방" to Part("회색", "백팩"),
    )
        set(value) { field = value; invalidate() }

    var line = 2.5f // 외곽선 굵기 (viewBox 단위)
        set(value) { field = value; invalidate() }

    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeJoin = Paint.Join.ROUND
        color = INK
    }

    override fun onDraw(canvas: Canvas) {
        val s = min(width / 160f, height / 210f)
        canvas.translate((width - 160 * s) / 2, (height - 210 * s) / 2)
        canvas.scale(s, s)
        stroke.strokeWidth = line

        val top = parts.getValue("상의")
        val bottom = parts.getValue("하의")
        val outer = parts.getValue("겉옷")
        val bag = parts.getValue("가방")
        val hasOuter = outer.style != "없음"
        fun hex(p: Part) = CLOTH_COLORS[p.color] ?: CLOTH_COLORS.getValue("회색")

        fun draw(path: Path, color: Int) {
            fill.color = color
            canvas.drawPath(path, fill)
            canvas.drawPath(path, stroke)
        }
        fun rect(x: Int, y: Int, w: Int, h: Int, r: Int, color: Int) = draw(Path().apply {
            addRoundRect(x.toFloat(), y.toFloat(), (x + w).toFloat(), (y + h).toFloat(), r.toFloat(), r.toFloat(), Path.Direction.CW)
        }, color)
        fun oval(cx: Int, cy: Int, rx: Int, ry: Int, color: Int) = draw(Path().apply {
            addOval((cx - rx).toFloat(), (cy - ry).toFloat(), (cx + rx).toFloat(), (cy + ry).toFloat(), Path.Direction.CW)
        }, color)

        if (bag.style == "백팩") rect(104, 68, 24, 50, 8, hex(bag))
        rect(60, 140, 14, 52, 5, SKIN)
        rect(86, 140, 14, 52, 5, SKIN)
        when (bottom.style) {
            "바지", "청바지" -> { rect(56, 120, 22, 72, 6, hex(bottom)); rect(82, 120, 22, 72, 6, hex(bottom)) }
            "반바지" -> { rect(56, 120, 22, 36, 6, hex(bottom)); rect(82, 120, 22, 36, 6, hex(bottom)) }
            "치마" -> draw(Path().apply {
                moveTo(52f, 118f); lineTo(108f, 118f); lineTo(118f, 166f); lineTo(42f, 166f); close()
            }, hex(bottom))
        }
        oval(66, 196, 13, 6, INK)
        oval(94, 196, 13, 6, INK)
        val arm = if (hasOuter) hex(outer) else hex(top)
        rect(32, 68, 16, 56, 8, arm)
        rect(112, 68, 16, 56, 8, arm)
        oval(40, 128, 7, 7, SKIN)
        oval(120, 128, 7, 7, SKIN)
        rect(72, 50, 16, 16, 0, SKIN)
        rect(46, 62, 68, 64, 16, hex(top))
        if (hasOuter) { rect(44, 60, 28, 72, 12, hex(outer)); rect(88, 60, 28, 72, 12, hex(outer)) }
        oval(80, 36, 24, 24, SKIN)
        draw(Path().apply {
            moveTo(56f, 36f); cubicTo(54f, 12f, 106f, 12f, 104f, 36f); cubicTo(96f, 24f, 68f, 22f, 56f, 36f); close()
        }, INK)
        when (bag.style) {
            "숄더백" -> {
                canvas.drawLine(58f, 64f, 116f, 128f, Paint(stroke).apply { color = hex(bag); strokeWidth = 6f })
                rect(104, 118, 28, 22, 5, hex(bag))
            }
            "에코백" -> {
                canvas.drawPath(Path().apply { moveTo(114f, 128f); cubicTo(114f, 116f, 132f, 116f, 132f, 128f) }, stroke)
                rect(108, 126, 30, 34, 3, hex(bag))
            }
        }
    }
}
