package com.example.morningconnect

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import com.example.morningconnect.databinding.ActivityDoneBinding

// 곧 내려요 4: 인증서 QR 확인 → 자리 인계 완료
class DoneActivity : AppCompatActivity() {
    private lateinit var binding: ActivityDoneBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityDoneBinding.inflate(layoutInflater)
        setScreen(binding.root)

        binding.header.set("자리 인계 확인", "곧 사당 도착", "뒤로") { finish() }
        binding.demoScan.setOnClickListener {
            binding.scanning.isVisible = false
            binding.done.isVisible = true
        }
        binding.ok.setOnClickListener { goHome() }
    }
}
