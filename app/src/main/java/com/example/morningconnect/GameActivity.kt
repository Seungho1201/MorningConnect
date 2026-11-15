package com.example.morningconnect

import android.os.Bundle
import android.view.View
import android.widget.LinearLayout
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import com.example.morningconnect.databinding.ActivityGameBinding

// ③ 미니게임: 게임 중에 자리 알림 시트가 끼어듦
class GameActivity : AppCompatActivity() {
    private lateinit var binding: ActivityGameBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityGameBinding.inflate(layoutInflater)
        setScreen(binding.root, binding.content)
        binding.sheet.keepAboveNavBar()

        binding.header.set("미니게임", "사당까지 4정거장") { finish() }

        // 좌석 위치: 7칸 x 2줄, 오른쪽 줄 3번째 강조
        listOf(binding.mapLeft, binding.mapRight).forEach { row ->
            repeat(7) { i ->
                val hit = row == binding.mapRight && i == 2
                row.addView(View(this).apply {
                    background = if (hit) roundBox(getColor(R.color.green_dark), 4, getColor(R.color.ink), 2)
                    else roundBox(getColor(R.color.gray), 4)
                }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f).apply {
                    marginStart = 2.dp
                    marginEnd = 2.dp
                })
            }
        }

        binding.go.setOnClickListener {
            binding.alert.isVisible = false
            binding.going.isVisible = true
        }
        binding.dismiss.setOnClickListener { closeSheet() }
        binding.myCert.setOnClickListener { closeSheet() }
    }

    private fun closeSheet() {
        binding.scrim.isVisible = false
        binding.sheet.isVisible = false
    }
}
