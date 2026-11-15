package com.example.morningconnect

import android.animation.ObjectAnimator
import android.animation.PropertyValuesHolder
import android.animation.ValueAnimator
import android.os.Build
import android.os.Bundle
import android.view.View
import android.view.animation.PathInterpolator
import androidx.appcompat.app.AppCompatActivity
import com.example.morningconnect.databinding.ActivityWaitBinding

// 곧 내려요 2: 같은 칸에 알리고 '갈게요'를 기다림
class WaitActivity : AppCompatActivity() {
    private lateinit var binding: ActivityWaitBinding
    private val rings = mutableListOf<ObjectAnimator>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityWaitBinding.inflate(layoutInflater)
        setScreen(binding.root)

        binding.header.set("곧 내려요", "사당까지 2정거장", "자리 다시 고르기") { finish() }
        binding.steps.show(2)
        binding.demoNext.setOnClickListener { open<MatchActivity>() }
        binding.cancel.setOnClickListener { goHome() }

        // 퍼지는 고리: 0.6→1.25배, 투명도 0.7→0, 2.4초 반복, 두 번째는 1.2초 늦게
        val reduceMotion = Build.VERSION.SDK_INT >= 26 && !ValueAnimator.areAnimatorsEnabled()
        listOf(binding.ring1 to 0L, binding.ring2 to 1200L).forEach { (ring, delay) ->
            if (reduceMotion) {
                ring.alpha = 0.25f
                return@forEach
            }
            rings += ObjectAnimator.ofPropertyValuesHolder(
                ring,
                PropertyValuesHolder.ofFloat(View.SCALE_X, 0.6f, 1.25f),
                PropertyValuesHolder.ofFloat(View.SCALE_Y, 0.6f, 1.25f),
                PropertyValuesHolder.ofFloat(View.ALPHA, 0.7f, 0f),
            ).apply {
                duration = 2400
                startDelay = delay
                repeatCount = ValueAnimator.INFINITE
                interpolator = PathInterpolator(0f, 0f, 0.58f, 1f) // CSS ease-out
                start()
            }
        }
    }

    override fun onDestroy() {
        rings.forEach { it.cancel() }
        super.onDestroy()
    }
}
