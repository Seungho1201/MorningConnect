package com.example.morningconnect

import android.app.Activity
import android.content.Intent
import android.content.res.ColorStateList
import android.content.res.Resources
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.View
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import com.example.morningconnect.databinding.ViewHeaderBinding
import com.example.morningconnect.databinding.ViewStepsBinding
import com.google.android.material.button.MaterialButton

// 화면을 띄우고, 상태바·내비게이션바·키보드에 가리지 않게 content에 여백을 줌
// (시트가 있는 화면은 content만 여백을 받고, 어두운 막과 시트는 화면 끝까지 덮음)
fun AppCompatActivity.setScreen(root: View, content: View = root) {
    val light = SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
    enableEdgeToEdge(light, light)
    setContentView(root)
    ViewCompat.setOnApplyWindowInsetsListener(root) { _, insets ->
        val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.ime())
        content.setPadding(bars.left, bars.top, bars.right, bars.bottom)
        insets
    }
}

// 화면 아래에 붙는 시트: 원래 아래 여백 + 내비게이션바 높이
fun View.keepAboveNavBar() {
    val base = paddingBottom
    ViewCompat.setOnApplyWindowInsetsListener(this) { view, insets ->
        view.updatePadding(bottom = base + insets.getInsets(WindowInsetsCompat.Type.systemBars()).bottom)
        insets
    }
}

inline fun <reified T : Activity> Activity.open() = startActivity(Intent(this, T::class.java))

// 이미 떠 있는 홈으로 돌아감 (위에 쌓인 화면은 닫힘)
fun Activity.goHome() = startActivity(
    Intent(this, MainActivity::class.java)
        .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
)

// 공통 헤더: 원형 뒤로 버튼 + 제목 + 남은 정거장
fun ViewHeaderBinding.set(titleText: String, statusText: String, backLabel: String = "홈으로", onBack: () -> Unit) {
    title.text = titleText
    status.text = statusText
    back.contentDescription = backLabel
    back.setOnClickListener { onBack() }
}

// 곧 내려요 진행 단계 (current: 1~3)
fun ViewStepsBinding.show(current: Int) {
    val ctx = root.context
    listOf(bar1, bar2, bar3).forEachIndexed { i, bar ->
        bar.backgroundTintList = ColorStateList.valueOf(ctx.getColor(if (i < current) R.color.green_dark else R.color.gray))
    }
    listOf(step1, step2, step3).forEachIndexed { i, step ->
        step.setTextColor(ctx.getColor(if (i == current - 1) R.color.ink else R.color.sub))
    }
}

// 선택 버튼: 켜짐 = 검정 바탕 흰 글자, 꺼짐 = offColor 바탕 검정 글자
fun MaterialButton.setOn(on: Boolean, offColor: Int = Color.WHITE) {
    isSelected = on
    backgroundTintList = ColorStateList.valueOf(if (on) context.getColor(R.color.ink) else offColor)
    setTextColor(if (on) Color.WHITE else context.getColor(R.color.ink))
}

val Int.dp: Int get() = (this * Resources.getSystem().displayMetrics.density).toInt()

fun roundBox(fill: Int, radiusDp: Int, strokeColor: Int = 0, strokeDp: Int = 0) = GradientDrawable().apply {
    setColor(fill)
    cornerRadius = radiusDp.dp.toFloat()
    if (strokeDp > 0) setStroke(strokeDp.dp, strokeColor)
}
