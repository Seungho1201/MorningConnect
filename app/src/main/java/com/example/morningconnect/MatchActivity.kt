package com.example.morningconnect

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import com.example.morningconnect.databinding.ActivityMatchBinding

// 곧 내려요 3: 같은 색 화면으로 서로 찾기 + 내리기 직전 재확인
class MatchActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMatchBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMatchBinding.inflate(layoutInflater)
        setScreen(binding.root, binding.content)
        binding.sheet.keepAboveNavBar()
        binding.cancelled.keepAboveNavBar()

        binding.header.set("연결됐어요", "사당까지 1정거장", "뒤로") { finish() }
        binding.header.title.textSize = 15f

        // 연결된 분의 오늘의 룩
        binding.partner.line = 3f
        binding.partner.parts = mapOf(
            "상의" to Part("흰색", "티셔츠"), "하의" to Part("베이지", "바지"),
            "겉옷" to Part("남색", "코트"), "가방" to Part("회색", "백팩"),
        )

        binding.no.setOnClickListener { ask(false) }
        binding.undo.setOnClickListener { ask(true) }
        binding.yes.setOnClickListener { open<DoneActivity>() }
    }

    private fun ask(show: Boolean) {
        binding.scrim.isVisible = show
        binding.sheet.isVisible = show
        binding.cancelled.isVisible = !show
    }
}
