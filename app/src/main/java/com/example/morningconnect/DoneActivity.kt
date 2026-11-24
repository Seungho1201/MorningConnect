package com.example.morningconnect

import android.content.res.ColorStateList
import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.example.morningconnect.databinding.ActivityDoneBinding
import com.google.android.material.chip.Chip

// 인계 완료: 포인트 정산 결과 + 상대 평가
class DoneActivity : AppCompatActivity() {
    private lateinit var binding: ActivityDoneBinding
    private var rating: Boolean? = null
    private val picked = mutableSetOf<String>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityDoneBinding.inflate(layoutInflater)
        setScreen(binding.root)

        val giver = intent.getStringExtra(EXTRA_ROLE) != Role.RECEIVER.name
        val station = Line2.at(intent.getIntExtra(EXTRA_STATION, 0)).name
        savedInstanceState?.let {
            rating = if (it.containsKey("rating")) it.getBoolean("rating") else null
            picked += it.getStringArrayList("tags").orEmpty()
        }

        with(binding) {
            if (giver) {
                title.text = "자리를 넘겨줬어요"
                sub.text = "$station 하차 확인 · 인증서 스캔 완료"
                totalLabel.text = "받은 포인트"
                total.text = (Store.BASE_REWARD + Store.TRANSFER).points(sign = true)
                line("기본 지급", Store.BASE_REWARD.points())
                line("상대에게서 이동", Store.TRANSFER.points())
                question.text = "자리를 받은 분은 어땠나요?"
            } else {
                title.text = "자리를 받았어요"
                sub.text = "인증서 확인 완료 · 편하게 앉으세요"
                totalLabel.text = "쓴 포인트"
                total.text = (-Store.TRANSFER).points()
                total.setTextColor(getColor(R.color.ink))
                line("넘겨준 분에게 이동", Store.TRANSFER.points())
                question.text = "자리를 넘겨준 분은 어땠나요?"
            }
            balance.text = store.points.points()

            val tagNames = if (giver) listOf("빨리 와 줬어요", "예의 발랐어요", "찾기 쉬웠어요")
            else listOf("자리를 잘 알려 줬어요", "친절했어요", "찾기 쉬웠어요")
            tagNames.forEach { name ->
                tags.addView(Chip(this@DoneActivity).apply {
                    text = name
                    isCheckable = true
                    isCheckedIconVisible = false
                    isChecked = name in picked
                    chipStrokeWidth = 0f
                    textSize = 15f
                    chipMinHeight = 44.dp.toFloat()
                    shapeAppearanceModel = shapeAppearanceModel.withCornerSize(22.dp.toFloat())
                    chipBackgroundColor = ColorStateList(
                        arrayOf(intArrayOf(android.R.attr.state_checked), intArrayOf()),
                        intArrayOf(getColor(R.color.ink), getColor(R.color.chip)),
                    )
                    setTextColor(ColorStateList(
                        arrayOf(intArrayOf(android.R.attr.state_checked), intArrayOf()),
                        intArrayOf(Color.WHITE, getColor(R.color.ink)),
                    ))
                    setOnCheckedChangeListener { _, on -> if (on) picked += name else picked -= name }
                })
            }

            good.setOnClickListener { rating = true; renderRating() }
            bad.setOnClickListener { rating = false; renderRating() }
            ok.setOnClickListener { submit(); goHome(); finish() }
            exchange.setOnClickListener { submit(); goHome(); open<MyActivity>(); finish() }
        }
        renderRating()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        rating?.let { outState.putBoolean("rating", it) }
        outState.putStringArrayList("tags", ArrayList(picked))
    }

    private fun line(label: String, value: String) {
        binding.lines.addView(LinearLayout(this).apply {
            setPadding(0, 6.dp, 0, 6.dp)
            addView(TextView(context).apply { text = label; textSize = 15f; setTextColor(getColor(R.color.sub)) },
                LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
            addView(TextView(context).apply { text = value; textSize = 15f; gravity = Gravity.END; setTypeface(null, android.graphics.Typeface.BOLD) })
        })
    }

    private fun renderRating() = with(binding) {
        listOf(good to true, bad to false).forEach { (v, value) ->
            val on = rating == value
            v.isSelected = on
            v.setBackgroundResource(if (on) R.drawable.choice_on else R.drawable.choice_off)
            val color = getColor(if (on) R.color.green_text else R.color.ink)
            v.setTextColor(color)
            v.setTypeface(null, if (on) android.graphics.Typeface.BOLD else android.graphics.Typeface.NORMAL)
            v.compoundDrawablesRelative[0]?.mutate()?.setTint(color)
        }
    }

    // 평가는 상대에게 전달할 값 (통신 층이 붙으면 함께 보냄). 지금은 고른 것만 확인
    private fun submit() {
        val r = rating ?: return
        toast(if (r) "좋은 평가를 보냈어요" else "의견을 보냈어요")
    }

    companion object {
        const val EXTRA_ROLE = "role"
        const val EXTRA_STATION = "station"
    }
}
