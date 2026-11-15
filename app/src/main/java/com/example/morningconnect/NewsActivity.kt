package com.example.morningconnect

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import com.example.morningconnect.databinding.ActivityNewsBinding

// ④ 뉴스: 내리기 2정거장 전에 '곧 내려요' 알림이 끼어듦
class NewsActivity : AppCompatActivity() {
    private lateinit var binding: ActivityNewsBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityNewsBinding.inflate(layoutInflater)
        setScreen(binding.root)

        binding.header.set("뉴스", "사당까지 2정거장") { finish() }
        binding.closeTip.setOnClickListener { binding.tip.isVisible = false }
        binding.seat.setOnClickListener { open<SeatActivity>() }
    }
}
