package com.example.morningconnect

import android.Manifest
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.graphics.Typeface
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.children
import androidx.core.view.isVisible
import androidx.core.widget.doAfterTextChanged
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.morningconnect.databinding.ActivityDestinationBinding
import com.example.morningconnect.databinding.ItemStationBinding
import com.google.android.material.dialog.MaterialAlertDialogBuilder

// 하차역 알림: 내릴 역과 알림 시점을 정하면 그때 알림(+이어폰 음성)을 보냄
class DestinationActivity : AppCompatActivity() {
    private lateinit var binding: ActivityDestinationBinding
    private var selected = -1          // 고른 역 (Line2 index)
    private var alertBefore = 2
    private var rows = listOf<Int>()   // 화면에 보이는 역 (지금 역부터 진행 방향 순서)

    private val notifyPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (!granted) toast("알림 권한이 없으면 소리·진동 알림을 받을 수 없어요")
        save()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityDestinationBinding.inflate(layoutInflater)
        setScreen(binding.root)

        val store = store
        store.stopsLeft() // 지난 하차역 정리
        selected = savedInstanceState?.getInt("selected") ?: store.destination?.station ?: -1
        alertBefore = savedInstanceState?.getInt("alert") ?: store.alertBefore

        binding.header.set("하차역 알림", if (store.destination != null) "해제" else null) { finish() }
        binding.header.right.setOnClickListener { clearDestination() }
        binding.header.right.minHeight = 48.dp
        binding.header.right.gravity = android.view.Gravity.CENTER
        binding.header.right.setTextColor(getColor(R.color.green_text))

        binding.list.layoutManager = LinearLayoutManager(this)
        binding.list.adapter = adapter
        binding.list.clipToOutline = true
        // 목록은 화면에 남는 높이만큼 (낮은 화면에서도 최소 4줄은 보이게, 넘치면 화면 전체가 스크롤)
        binding.scroll.addOnLayoutChangeListener { _, _, top, _, bottom, _, oldTop, _, oldBottom ->
            if (bottom - top == oldBottom - oldTop) return@addOnLayoutChangeListener
            val others = binding.body.height - binding.list.height
            val target = maxOf(MIN_LIST_DP.dp, (bottom - top) - others)
            if (binding.list.layoutParams.height != target) {
                binding.list.post { binding.list.layoutParams = binding.list.layoutParams.apply { height = target } }
            }
        }
        binding.search.doAfterTextChanged { filter() }

        binding.`when`.children.forEachIndexed { i, v ->
            v.setOnClickListener { alertBefore = i + 1; render() }
        }
        binding.voice.isChecked = store.voiceGuide
        binding.auto.isChecked = store.autoSuggest
        binding.set.setOnClickListener { onSet() }

        filter()
        // 고른 역이 보이게
        rows.indexOf(selected).takeIf { it > 5 }?.let { (binding.list.layoutManager as LinearLayoutManager).scrollToPositionWithOffset(it - 2, 0) }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putInt("selected", selected)
        outState.putInt("alert", alertBefore)
    }

    private fun filter() {
        val clock = store.clock
        val cur = clock.current()
        val q = binding.search.text.toString().trim()
        rows = (0 until Line2.size).map { Line2.step(cur, it, clock.dir) }
            .filter { q.isEmpty() || Line2.at(it).name.contains(q) || Line2.at(it).roman.contains(q, ignoreCase = true) }
        adapter.notifyDataSetChanged()
        render()
    }

    private fun render() {
        binding.`when`.children.forEachIndexed { i, v ->
            val on = i + 1 == alertBefore
            v.isSelected = on
            v.setBackgroundResource(if (on) R.drawable.seg_on else 0)
            v.elevation = if (on) 2.dp.toFloat() else 0f
            (v as TextView).setTypeface(null, if (on) Typeface.BOLD else Typeface.NORMAL)
            v.setTextColor(getColor(if (on) R.color.ink else R.color.sub))
        }
        val name = if (selected >= 0) Line2.at(selected).name else "사당"
        binding.voiceSub.text = "이어폰으로 \"곧 ${name}역이에요\""
        binding.set.isEnabled = selected >= 0
        binding.set.text = if (selected >= 0) "${Line2.at(selected).name.josa("으로", "로")} 설정" else "내릴 역을 골라 주세요"
    }

    private fun onSet() {
        val clock = store.clock
        if (Line2.stopsBetween(clock.current(), selected, clock.dir) == 0) {
            toast("지금 있는 역이에요")
            return
        }
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            notifyPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else save()
    }

    private fun save() {
        val store = store
        val clock = store.clock
        store.alertBefore = alertBefore
        store.voiceGuide = binding.voice.isChecked
        store.autoSuggest = binding.auto.isChecked
        store.destination = Destination(selected, alertBefore, clock.arrivalAt(selected))
        ArrivalAlarm.schedule(this)
        toast("${Line2.at(selected).name}역 ${alertBefore}정거장 전에 알려 드릴게요")
        finish()
    }

    private fun clearDestination() {
        store.destination = null
        ArrivalAlarm.cancel(this)
        toast("하차역 알림을 껐어요")
        finish()
    }

    // 역을 길게 누르면 지금 위치를 그 역으로 맞춤 (주변 탐지 대신 직접 보정)
    private fun askSetCurrent(station: Int) {
        val name = Line2.at(station).name
        MaterialAlertDialogBuilder(this)
            .setTitle("지금 ${name}역인가요?")
            .setMessage("열차 위치를 ${name}역으로 맞출게요.")
            .setPositiveButton("맞추기") { _, _ ->
                val store = store
                store.clock = TrainClock(station, System.currentTimeMillis(), store.clock.dir)
                store.destination?.let { d ->
                    if (d.station == station) {
                        store.destination = null
                        ArrivalAlarm.cancel(this)
                    } else {
                        store.destination = Destination(d.station, d.alertBefore, store.clock.arrivalAt(d.station))
                        ArrivalAlarm.schedule(this)
                    }
                }
                if (selected == station) selected = -1
                filter()
            }
            .setNegativeButton("취소", null)
            .show()
    }

    companion object {
        private const val MIN_LIST_DP = 232
    }

    private val adapter = object : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
        override fun getItemCount() = rows.size

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
            object : RecyclerView.ViewHolder(ItemStationBinding.inflate(LayoutInflater.from(parent.context), parent, false).root) {}

        override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
            val b = ItemStationBinding.bind(holder.itemView)
            val station = rows[position]
            val clock = store.clock
            val stops = Line2.stopsBetween(clock.current(), station, clock.dir)
            val here = stops == 0
            val on = station == selected && !here
            val ctx = holder.itemView.context

            b.name.text = Line2.at(station).name
            b.name.setTypeface(null, if (here || on) Typeface.BOLD else Typeface.NORMAL)
            b.name.textSize = if (here || on) 19f else 17f
            b.row.setBackgroundResource(if (on) R.drawable.row_selected else 0)
            b.divider.isVisible = position < rows.size - 1 && !on && rows.getOrNull(position + 1) != selected

            when {
                here -> {
                    b.mark.setImageResource(R.drawable.ring_now); b.mark.background = null; b.mark.imageTintList = null
                    b.mark.setPadding(0, 0, 0, 0)
                    b.info.text = "지금 여기"
                    b.info.setBackgroundResource(R.drawable.pill_soft)
                    b.info.setPadding(12.dp, 6.dp, 12.dp, 6.dp)
                    b.info.setTextColor(ctx.getColor(R.color.green_text))
                    b.info.setTypeface(null, Typeface.BOLD)
                }
                on -> {
                    b.mark.setImageResource(R.drawable.ic_check); b.mark.setBackgroundResource(R.drawable.circle_green)
                    b.mark.imageTintList = ColorStateList.valueOf(ctx.getColor(R.color.white))
                    b.mark.setPadding(5.dp, 5.dp, 5.dp, 5.dp)
                    b.mark.scaleType = android.widget.ImageView.ScaleType.FIT_CENTER
                    b.info.text = "${stops}정거장 · 약 ${TrainClock.minutesFor(stops)}분"
                    b.info.background = null; b.info.setPadding(0, 0, 0, 0)
                    b.info.setTextColor(ctx.getColor(R.color.green_text))
                    b.info.setTypeface(null, Typeface.BOLD)
                }
                else -> {
                    b.mark.setImageResource(R.drawable.dot_green); b.mark.background = null; b.mark.imageTintList = null
                    b.mark.setPadding(0, 0, 0, 0)
                    b.mark.scaleType = android.widget.ImageView.ScaleType.CENTER
                    b.info.text = "${stops}정거장"
                    b.info.background = null; b.info.setPadding(0, 0, 0, 0)
                    b.info.setTextColor(ctx.getColor(R.color.sub))
                    b.info.setTypeface(null, Typeface.NORMAL)
                }
            }
            b.row.contentDescription = "${b.name.text}, ${b.info.text}" + if (on) ", 선택됨" else ""
            b.row.setOnClickListener {
                if (here) { toast("지금 있는 역이에요"); return@setOnClickListener }
                val old = rows.indexOf(selected)
                selected = station
                if (old >= 0) notifyItemChanged(old)
                if (old > 0) notifyItemChanged(old - 1)
                notifyItemChanged(position)
                if (position > 0) notifyItemChanged(position - 1)
                render()
            }
            b.row.setOnLongClickListener { askSetCurrent(station); true }
        }
    }
}
