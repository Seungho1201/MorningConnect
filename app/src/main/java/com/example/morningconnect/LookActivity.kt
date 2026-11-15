package com.example.morningconnect

import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.InsetDrawable
import android.graphics.drawable.LayerDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.text.bold
import androidx.core.text.buildSpannedString
import androidx.core.text.color
import androidx.core.view.children
import com.example.morningconnect.databinding.ActivityLookBinding
import com.google.android.material.button.MaterialButton

// ① 오늘의 룩: 옷 종류·색을 고르면 캐릭터와 설명이 바뀜
class LookActivity : AppCompatActivity() {
    private lateinit var binding: ActivityLookBinding

    private val parts = linkedMapOf(
        "상의" to Part("초록", "니트"),
        "하의" to Part("검정", "바지"),
        "겉옷" to Part("남색", "코트"),
        "가방" to Part("회색", "백팩"),
    )
    private val styles = mapOf(
        "상의" to listOf("티셔츠", "니트", "셔츠", "후드티"),
        "하의" to listOf("바지", "청바지", "치마", "반바지"),
        "겉옷" to listOf("없음", "코트", "패딩", "재킷"),
        "가방" to listOf("없음", "백팩", "숄더백", "에코백"),
    )
    private var tab = "상의"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityLookBinding.inflate(layoutInflater)
        setScreen(binding.root)

        binding.header.set("오늘의 룩", "사당까지 4정거장") { finish() }
        binding.save.setOnClickListener { goHome() }

        tabButtons().forEach { b -> b.setOnClickListener { tab = b.text.toString(); render() } }
        styleButtons().forEach { b -> b.setOnClickListener { parts.getValue(tab).style = b.text.toString(); render() } }

        // 색 버튼: 원 + 이름
        CLOTH_COLORS.keys.forEach { name ->
            binding.swatches.addView(LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER_HORIZONTAL
                minimumHeight = 60.dp
                contentDescription = name
                tag = name
                addView(View(context), LinearLayout.LayoutParams(46.dp, 46.dp))
                addView(TextView(context).apply { text = name; textSize = 11f; importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO })
                setOnClickListener { parts.getValue(tab).color = name; render() }
            }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        }
        render()
    }

    private fun tabButtons() = binding.tabs.children.map { it as MaterialButton }
    private fun styleButtons() = binding.styles.children.map { it as MaterialButton }

    private fun render() {
        val cur = parts.getValue(tab)
        val ink = getColor(R.color.ink)

        tabButtons().forEach { it.setOn(it.text == tab) }
        styleButtons().forEachIndexed { i, b ->
            b.text = styles.getValue(tab)[i]
            b.setOn(b.text == cur.style)
        }
        binding.kindLabel.text = "$tab 종류"
        binding.colorLabel.text = "$tab 색"

        binding.swatches.children.forEach { sw ->
            val name = sw.tag as String
            val on = name == cur.color
            sw.isSelected = on
            // 선택된 색은 바깥에 검정 고리
            val dot = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(CLOTH_COLORS.getValue(name))
                setStroke(2.dp, ink)
            }
            val ring = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setStroke(2.dp, if (on) ink else 0)
            }
            (sw as LinearLayout).getChildAt(0).background = LayerDrawable(arrayOf(ring, InsetDrawable(dot, 5.dp)))
        }

        val top = parts.getValue("상의")
        val outer = parts.getValue("겉옷")
        val bag = parts.getValue("가방")
        val hasOuter = outer.style != "없음"
        val list = mutableListOf(top, parts.getValue("하의"))
        if (hasOuter) list += outer
        if (bag.style != "없음") list += bag
        binding.desc.text = list.joinToString(" · ") { "${it.color} ${it.style}" }

        val nick = if (hasOuter) outer else top
        binding.nick.text = buildSpannedString {
            append("오픈톡·미니게임 이름: ")
            bold { color(ink) { append("${nick.color} ${nick.style}") } }
        }
        binding.character.parts = parts
    }
}
