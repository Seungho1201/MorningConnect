package com.example.morningconnect

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.animation.DecelerateInterpolator
import androidx.constraintlayout.widget.ConstraintLayout
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * 원형 메뉴: 바깥 버튼들을 원을 따라 손가락으로 돌릴 수 있다.
 * 놓으면 관성으로 조금 더 돌다가 가장 가까운 자리(72° 간격)에 멈춘다. 버튼 누르기는 그대로 동작.
 */
class OrbitMenu(context: Context, attrs: AttributeSet? = null) : ConstraintLayout(context, attrs) {
    private class Slot(val item: View, val baseAngle: Float)

    private val slots = mutableListOf<Slot>()

    // 전체 회전 각도 (시계 방향, 도)
    var offset = 0f
        private set

    private val slop = ViewConfiguration.get(context).scaledTouchSlop
    private var downX = 0f
    private var downY = 0f
    private var lastAngle = 0f
    private var lastTime = 0L
    private var velocity = 0f   // 도/초
    private var dragging = false
    private var anim: ValueAnimator? = null

    // 블루투스로 찾은 근처 사람 (폰 번호). 가운데 버튼 둘레에 파란 점으로 고르게 띄움
    var nearby: List<Int> = emptyList()
        set(value) {
            if (field != value) { field = value; invalidate() }
        }
    private val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = context.getColor(R.color.nearby) }

    // 버튼(아이콘+이름이 든 원)을 원 위에 고르게 배치 (첫 번째가 맨 위)
    fun setItems(items: List<View>) {
        slots.clear()
        val step = 360f / items.size
        items.forEachIndexed { i, v -> slots += Slot(v, i * step) }
        apply()
    }

    private val step get() = if (slots.isEmpty()) 72f else 360f / slots.size

    private fun apply() {
        slots.forEach { s ->
            val angle = Math.floorMod((s.baseAngle + offset).roundToInt(), 360).toFloat()
            val lp = s.item.layoutParams as LayoutParams
            lp.circleAngle = angle
            s.item.layoutParams = lp
        }
    }

    // 가운데를 기준으로 한 터치 각도 (맨 위 0°, 시계 방향)
    private fun angleOf(x: Float, y: Float) =
        Math.toDegrees(atan2((x - width / 2f).toDouble(), -(y - height / 2f).toDouble())).toFloat()

    private fun onRing(x: Float, y: Float): Boolean {
        val r = hypot(x - width / 2f, y - height / 2f)
        return r > 84.dp   // 가운데 '곧 내려요' 버튼 위에서는 돌리지 않음
    }

    override fun onInterceptTouchEvent(ev: MotionEvent): Boolean {
        when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = ev.x; downY = ev.y
                lastAngle = angleOf(ev.x, ev.y); lastTime = ev.eventTime
                velocity = 0f
                dragging = false
                anim?.cancel()
            }
            MotionEvent.ACTION_MOVE -> if (!dragging && onRing(downX, downY) &&
                hypot(ev.x - downX, ev.y - downY) > slop
            ) {
                dragging = true
                parent?.requestDisallowInterceptTouchEvent(true)
                lastAngle = angleOf(ev.x, ev.y); lastTime = ev.eventTime
            }
        }
        return dragging
    }

    override fun onTouchEvent(ev: MotionEvent): Boolean {
        when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN -> return onRing(ev.x, ev.y).also {
                downX = ev.x; downY = ev.y; lastAngle = angleOf(ev.x, ev.y); lastTime = ev.eventTime
                anim?.cancel()
            }
            MotionEvent.ACTION_MOVE -> {
                if (!dragging && hypot(ev.x - downX, ev.y - downY) > slop) dragging = true
                if (!dragging) return true
                val a = angleOf(ev.x, ev.y)
                var delta = a - lastAngle
                if (delta > 180) delta -= 360
                if (delta < -180) delta += 360
                val dt = (ev.eventTime - lastTime).coerceAtLeast(1)
                velocity = 0.6f * velocity + 0.4f * (delta * 1000f / dt)
                offset += delta
                lastAngle = a; lastTime = ev.eventTime
                apply()
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (dragging) settle() else if (ev.actionMasked == MotionEvent.ACTION_UP) performClick()
                dragging = false
            }
        }
        return true
    }

    override fun performClick() = super.performClick()

    // 가운데 버튼(반지름 84dp)과 연한 띠(100dp) 사이라 바깥 버튼(142dp ± 42dp)과 겹치지 않음
    override fun dispatchDraw(canvas: Canvas) {
        super.dispatchDraw(canvas)
        val dots = nearby.take(MAX_DOTS)
        dots.forEachIndexed { i, _ ->
            // 36°부터: 멈춰 있을 때 바깥 버튼(72° 간격) 사이 빈 곳
            val a = Math.toRadians(36 + i * 360.0 / dots.size)
            canvas.drawCircle(
                width / 2f + DOT_ORBIT.dp * sin(a).toFloat(), height / 2f - DOT_ORBIT.dp * cos(a).toFloat(),
                DOT_RADIUS.dp.toFloat(), dotPaint,
            )
        }
    }

    // 관성 + 가장 가까운 자리로 맞춤
    private fun settle() {
        val target = Math.round((offset + velocity * 0.25f) / step) * step
        anim = ValueAnimator.ofFloat(offset, target).apply {
            duration = 450
            interpolator = DecelerateInterpolator(1.6f)
            addUpdateListener { offset = it.animatedValue as Float; apply() }
            start()
        }
    }

    // 버튼 한 칸씩 돌리기 (화면 읽기 등 드래그가 어려운 경우)
    fun rotateBy(slots: Int) {
        anim?.cancel()
        val target = Math.round(offset / step) * step + slots * step
        anim = ValueAnimator.ofFloat(offset, target).apply {
            duration = 350
            interpolator = DecelerateInterpolator()
            addUpdateListener { offset = it.animatedValue as Float; apply() }
            start()
        }
    }

    override fun onDetachedFromWindow() {
        anim?.cancel()
        super.onDetachedFromWindow()
    }

    companion object {
        private const val DOT_ORBIT = 92
        private const val DOT_RADIUS = 5
        private const val MAX_DOTS = 40  // 둘레에 겹치지 않고 들어가는 만큼
    }
}
