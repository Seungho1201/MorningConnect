package com.example.morningconnect

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.example.morningconnect.databinding.ActivityTalkBinding

// ② 오픈톡: 같은 칸 대화방
class TalkActivity : AppCompatActivity() {
    private lateinit var binding: ActivityTalkBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityTalkBinding.inflate(layoutInflater)
        setScreen(binding.root)

        binding.header.set("오픈톡", "사당까지 4정거장") { finish() }
    }
}
