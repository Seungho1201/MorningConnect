package com.example.morningconnect

import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.InsetDrawable
import android.graphics.drawable.LayerDrawable
import android.os.Bundle
import android.view.View
import android.widget.GridLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.children
import com.example.morningconnect.databinding.ActivityLookBinding
import com.google.android.material.chip.Chip
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import org.json.JSONObject

// 오늘의 룩: 같은 칸 사람에게 인상착의로 전달되는 옷차림
class LookActivity : AppCompatActivity() {
    private lateinit var binding: ActivityLookBinding
    private lateinit var saved: Look
    private lateinit var look: Look
    private var tab = LookCatalog.categories.first()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityLookBinding.inflate(layoutInflater)
        setScreen(binding.root)

        saved = store.look
        look = savedInstanceState?.getString("look")?.let { Look.fromJson(JSONObject(it)) } ?: saved
        tab = savedInstanceState?.getString("tab") ?: tab

        binding.header.set("오늘의 룩", "같은 칸 사람만\n볼 수 있어요") { onBackPressedDispatcher.onBackPressed() }
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() = leave()
        })
        binding.save.setOnClickListener {
            store.look = look
            toast("오늘의 룩을 저장했어요")
            finish()
        }

        LookCatalog.categories.forEach { cat ->
            binding.tabs.addView(TextView(this, null, 0, R.style.Pill).apply {
                text = cat
                minWidth = 64.dp
                textSize = 16f
                setOnClickListener { tab = cat; render() }
            }, LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, 44.dp).apply { marginEnd = 8.dp })
        }

        CLOTH_COLORS.forEachIndexed { i, c ->
            binding.swatches.addView(View(this).apply {
                tag = i
                contentDescription = c.name
                isClickable = true
                isFocusable = true
                setOnClickListener { update { it.copy(color = i) } }
            }, GridLayout.LayoutParams(GridLayout.spec(i / 6), GridLayout.spec(i % 6, 1f)).apply {
                width = 0
                height = 50.dp
                setMargins(3.dp, 4.dp, 3.dp, 4.dp)
            })
        }
        render()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putString("look", look.toJson().toString())
        outState.putString("tab", tab)
    }

    private fun update(change: (Item) -> Item) {
        look = look.with(tab, change(look[tab]))
        render()
    }

    private fun render() {
        val cur = look[tab]
        binding.character.look = look
        binding.summary.text = look.summary()

        binding.tabs.children.forEach { (it as TextView).pillOn(it.text == tab) }
        binding.colorLabel.text = "$tab 색상"

        binding.swatches.children.forEach { sw ->
            val i = sw.tag as Int
            val on = i == cur.color
            sw.isSelected = on
            val dot = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(CLOTH_COLORS[i].argb)
                setStroke(1.dp, getColor(R.color.gray))
            }
            val ring = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setStroke(3.dp, if (on) getColor(R.color.green_dark) else Color.TRANSPARENT)
            }
            sw.background = LayerDrawable(arrayOf(ring, InsetDrawable(dot, if (on) 6.dp else 2.dp)))
        }

        // 종류 칩 (가방·모자는 '없음' 포함)
        val kinds = LookCatalog.kinds.getValue(tab)
        if (binding.kinds.tag != tab) {
            binding.kinds.tag = tab
            binding.kinds.removeAllViews()
            kinds.forEach { k ->
                binding.kinds.addView(Chip(this).apply {
                    text = k
                    isCheckable = true
                    isCheckedIconVisible = false
                    chipStrokeWidth = 0f
                    textSize = 15f
                    chipMinHeight = 44.dp.toFloat()
                    shapeAppearanceModel = shapeAppearanceModel.withCornerSize(22.dp.toFloat())
                    chipStartPadding = 10.dp.toFloat(); chipEndPadding = 10.dp.toFloat()
                    chipBackgroundColor = ColorStateList(
                        arrayOf(intArrayOf(android.R.attr.state_checked), intArrayOf()),
                        intArrayOf(getColor(R.color.ink), getColor(R.color.chip)),
                    )
                    setTextColor(ColorStateList(
                        arrayOf(intArrayOf(android.R.attr.state_checked), intArrayOf()),
                        intArrayOf(Color.WHITE, getColor(R.color.ink)),
                    ))
                    setOnClickListener { update { it.copy(kind = k) } }
                })
            }
        }
        binding.kinds.children.forEach { (it as Chip).isChecked = it.text == cur.kind }
    }

    private fun leave() {
        if (look == saved) { finish(); return }
        MaterialAlertDialogBuilder(this)
            .setTitle("바꾼 룩을 저장할까요?")
            .setPositiveButton("저장") { _, _ -> store.look = look; finish() }
            .setNegativeButton("저장 안 함") { _, _ -> finish() }
            .setNeutralButton("계속 고르기", null)
            .show()
    }
}
