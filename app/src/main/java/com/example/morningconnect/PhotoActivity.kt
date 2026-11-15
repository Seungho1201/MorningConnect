package com.example.morningconnect

import android.graphics.Color
import android.os.Bundle
import android.text.style.AbsoluteSizeSpan
import androidx.appcompat.app.AppCompatActivity
import androidx.core.text.buildSpannedString
import androidx.core.text.inSpans
import androidx.core.view.children
import com.example.morningconnect.databinding.ActivityPhotoBinding
import com.google.android.material.button.MaterialButton

// ⑤ 이곳 사진: 지금 역 / 다음 역 / 내릴 역 주변 사진
class PhotoActivity : AppCompatActivity() {
    private lateinit var binding: ActivityPhotoBinding

    private val stations = listOf("지금 역" to "강남", "다음 역" to "교대", "내릴 역" to "사당")
    private var station = "강남"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPhotoBinding.inflate(layoutInflater)
        setScreen(binding.root)

        binding.header.set("이곳 사진", "사당까지 4정거장") { finish() }

        tabs().forEachIndexed { i, b ->
            val (kind, name) = stations[i]
            b.text = buildSpannedString {
                inSpans(AbsoluteSizeSpan(11, true)) { append(kind) }
                append("\n")
                inSpans(AbsoluteSizeSpan(16, true)) { append(name) }
            }
            b.setOnClickListener { station = name; render() }
        }
        render()
    }

    private fun tabs() = binding.tabs.children.map { it as MaterialButton }

    private fun render() {
        tabs().forEachIndexed { i, b -> b.setOn(stations[i].second == station, Color.TRANSPARENT) }
        binding.stationTitle.text = "${station}역 주변"
        binding.upload.text = "${station}역 사진 올리기"
    }
}
