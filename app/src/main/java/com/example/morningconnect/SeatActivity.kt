package com.example.morningconnect

import android.os.Bundle
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.text.bold
import androidx.core.text.buildSpannedString
import androidx.core.view.children
import com.example.morningconnect.databinding.ActivitySeatBinding
import com.google.android.material.button.MaterialButton

// 곧 내려요 1: 출입문 구간과 앉은 자리 고르기
class SeatActivity : AppCompatActivity() {
    private lateinit var binding: ActivitySeatBinding

    private val segLabels = listOf("문 1–2 사이", "문 2–3 사이", "문 3–4 사이")
    private var seg = 1
    private var side = "오른쪽 줄"
    private var idx = 2

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySeatBinding.inflate(layoutInflater)
        setScreen(binding.root)

        binding.header.set("곧 내려요", "사당까지 2정거장", "취소하고 홈으로") { goHome() }
        binding.steps.show(1)
        binding.notify.setOnClickListener { open<WaitActivity>() }

        segments().forEachIndexed { i, b -> b.setOnClickListener { seg = i; render() } }

        listOf(binding.leftSeats to "왼쪽 줄", binding.rightSeats to "오른쪽 줄").forEach { (column, sideName) ->
            repeat(7) { k ->
                column.addView(TextView(this).apply {
                    tag = sideName to k
                    contentDescription = "$sideName ${k + 1}번째 자리"
                    gravity = Gravity.CENTER
                    textSize = 18f
                    setTextColor(getColor(R.color.white))
                    setOnClickListener { side = sideName; idx = k; render() }
                }, LinearLayout.LayoutParams(72.dp, 40.dp).apply { if (k > 0) topMargin = 6.dp })
            }
        }
        render()
    }

    private fun segments() = binding.segments.children.map { it as MaterialButton }

    private fun render() {
        segments().forEachIndexed { i, b -> b.setOn(i == seg) }

        (binding.leftSeats.children + binding.rightSeats.children).forEach { v ->
            val on = v.tag == (side to idx)
            v.isSelected = on
            (v as TextView).text = if (on) "✓" else ""
            v.background = roundBox(
                getColor(if (on) R.color.green_dark else R.color.bg), 10,
                getColor(if (on) R.color.ink else R.color.gray), 2,
            )
        }

        binding.doorTop.text = "${seg + 1}번 문 쪽 ↑"
        binding.doorBottom.text = "${seg + 2}번 문 쪽 ↓ · 양 끝 노약자석은 제외"
        binding.picked.text = buildSpannedString {
            append("선택한 자리 ")
            bold { append("${segLabels[seg]} · $side ${idx + 1}번째") }
        }
    }
}
