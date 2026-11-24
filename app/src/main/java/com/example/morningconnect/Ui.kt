package com.example.morningconnect

import android.app.Activity
import android.content.Intent
import android.content.res.Resources
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import androidx.core.view.updatePadding
import com.example.morningconnect.databinding.ViewHeaderBinding
import java.text.NumberFormat
import java.util.Locale

// 화면을 띄우고, 상태바·내비게이션바·키보드에 가리지 않게 content 에 여백을 줌
fun AppCompatActivity.setScreen(root: View, content: View = root, dark: Boolean = false) {
    val style = if (dark) SystemBarStyle.dark(Color.TRANSPARENT) else SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
    enableEdgeToEdge(style, style)
    setContentView(root)
    val base = intArrayOf(content.paddingLeft, content.paddingTop, content.paddingRight, content.paddingBottom)
    ViewCompat.setOnApplyWindowInsetsListener(root) { _, insets ->
        val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.ime())
        content.setPadding(base[0] + bars.left, base[1] + bars.top, base[2] + bars.right, base[3] + bars.bottom)
        insets
    }
}

// 화면 아래에 떠 있는 버튼: 원래 아래 여백 + 내비게이션바 높이
fun View.keepAboveNavBar() {
    val base = (layoutParams as? android.view.ViewGroup.MarginLayoutParams)?.bottomMargin ?: 0
    ViewCompat.setOnApplyWindowInsetsListener(this) { view, insets ->
        (view.layoutParams as? android.view.ViewGroup.MarginLayoutParams)?.let {
            it.bottomMargin = base + insets.getInsets(WindowInsetsCompat.Type.systemBars()).bottom
            view.layoutParams = it
        }
        insets
    }
}

inline fun <reified T : Activity> Activity.open(build: Intent.() -> Unit = {}) =
    startActivity(Intent(this, T::class.java).apply(build))

// 이미 떠 있는 홈으로 돌아감 (위에 쌓인 화면은 닫힘)
fun Activity.goHome() = startActivity(
    Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
)

fun Activity.toast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()

// 공통 헤더: 뒤로 + 제목 + (오른쪽 안내)
fun ViewHeaderBinding.set(titleText: String, rightText: String? = null, onBack: () -> Unit) {
    title.text = titleText
    right.text = rightText
    right.isVisible = rightText != null
    back.setOnClickListener { onBack() }
}

// 알약 칩: 켜짐 = 검정 바탕 흰 굵은 글자, 꺼짐 = offBg 바탕 검정 글자
fun TextView.pillOn(on: Boolean, offBg: Int = R.drawable.pill) {
    isSelected = on
    setBackgroundResource(if (on) R.drawable.pill_on else offBg)
    setTextColor(if (on) Color.WHITE else context.getColor(R.color.ink))
    setTypeface(null, if (on) android.graphics.Typeface.BOLD else android.graphics.Typeface.NORMAL)
}

val Int.dp: Int get() = (this * Resources.getSystem().displayMetrics.density).toInt()

fun roundBox(fill: Int, radiusDp: Int, strokeColor: Int = 0, strokeDp: Int = 0) = GradientDrawable().apply {
    setColor(fill)
    cornerRadius = radiusDp.dp.toFloat()
    if (strokeDp > 0) setStroke(strokeDp.dp, strokeColor)
}

fun Int.won(): String = NumberFormat.getNumberInstance(Locale.KOREA).format(this)

// 포인트 표시: 1240 → "1,240P", 부호 붙이기 선택
fun Int.points(sign: Boolean = false) = (if (sign && this > 0) "+" else if (this < 0) "-" else "") + kotlin.math.abs(this).won() + "P"
