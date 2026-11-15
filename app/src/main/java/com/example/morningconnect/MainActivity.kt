package com.example.morningconnect

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import com.example.morningconnect.databinding.ActivityMainBinding

// 홈: 원형 메뉴
class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setScreen(binding.root)

        // 아이콘과 이름 모두 누를 수 있게 (디자인의 터치 영역)
        fun link(target: Class<out Activity>, vararg views: View) =
            views.forEach { it.setOnClickListener { startActivity(Intent(this, target)) } }

        with(binding) {
            link(LookActivity::class.java, look, lookLabel)
            link(TalkActivity::class.java, talk, talkLabel)
            link(GameActivity::class.java, game, gameLabel)
            link(NewsActivity::class.java, news, newsLabel)
            link(PhotoActivity::class.java, photo, photoLabel)
            link(SeatActivity::class.java, seat)
        }
    }
}
