package com.example.morningconnect

import android.os.Bundle
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import androidx.lifecycle.Lifecycle
import com.example.morningconnect.databinding.ActivitySendDownBinding
import com.google.android.material.dialog.MaterialAlertDialogBuilder

// 곧 내려요 알림: 내 자리를 골라 같은 칸에 알리고, 누가 '갈게요'를 누르면 연결 화면으로
class SendDownActivity : AppCompatActivity() {
    private lateinit var binding: ActivitySendDownBinding
    private var seat = Seat(0, 1, 1)
    private var waiting: Handoff? = null
    private var pendingAccept: SeatNetwork.Cancel? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySendDownBinding.inflate(layoutInflater)
        setScreen(binding.root)

        savedInstanceState?.let { seat = Seat(it.getInt("row"), it.getInt("bench"), it.getInt("pos")) }

        binding.header.set("곧 내려요 알림") { onBackPressedDispatcher.onBackPressed() }
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() { if (waiting != null) stopWaiting() else finish() }
        })

        binding.seatMap.onPick = { row, bench -> seat = seat.copy(row = row, bench = bench); render() }
        binding.door.setOnClickListener { pickBench() }
        binding.pos.setOnClickListener { pickPosition() }
        binding.train.setOnClickListener { pickCar() }
        binding.lookCard.setOnClickListener { open<LookActivity>() }
        binding.pregnant.isChecked = store.pregnantPriority
        binding.pregnant.setOnCheckedChangeListener { _, on -> store.pregnantPriority = on }
        binding.notify.setOnClickListener { if (waiting == null) startWaiting() else stopWaiting() }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putInt("row", seat.row); outState.putInt("bench", seat.bench); outState.putInt("pos", seat.pos)
    }

    override fun onResume() {
        super.onResume()
        render()
    }

    override fun onDestroy() {
        // 화면을 떠나면 알림도 거둠 (아직 아무도 수락하지 않았으니 신뢰도 영향 없음)
        pendingAccept?.cancel()
        if (waiting != null) store.handoff = null
        super.onDestroy()
    }

    private fun render() {
        val store = store
        val left = store.stopsLeft()
        val d = store.destination
        if (left == null || d == null) {
            toast("하차역이 지났어요. 다시 정해 주세요")
            finish()
            return
        }
        val dest = Line2.at(d.station).name
        binding.arriveLabel.text = "$dest 도착까지"
        binding.arriveIn.text = if (left == 0) "지금 도착" else "${left}정거장 · 약 ${TrainClock.minutesFor(left)}분"
        binding.train.text = "2호선 ${Line2.dirName(store.clock.dir)} · ${store.car}번째 칸"
        binding.train.contentDescription = "${binding.train.text}, 눌러서 칸 바꾸기"

        binding.seatMap.select(seat.row, seat.bench)
        binding.seatMap.contentDescription = "좌석 배치도. 선택한 자리 ${seat.doorLabel(store.car)}"
        binding.door.text = seat.doorLabel(store.car)
        binding.pos.text = seat.posLabel()

        val look = store.look
        binding.avatar.look = look
        binding.lookText.text = look.summary()
        binding.lookCard.contentDescription = "인상착의로 전달돼요. ${look.summary()}. 수정"

        val count = SeatNetwork.instance.nearbyCount()
        binding.notify.text = if (waiting == null) "같은 칸 ${count}명에게 알리기" else "알림 취소"
        binding.waiting.isVisible = waiting != null
    }

    private fun startWaiting() {
        val store = store
        val d = store.destination ?: return
        val h = Handoff.create(Role.GIVER, seat, store.car, d.station, binding.pregnant.isChecked)
        waiting = h
        store.handoff = h.toJson()
        pendingAccept = SeatNetwork.instance.broadcast(h) { peer ->
            // 가장 먼저 '갈게요'를 누른 사람과 연결
            h.partner = peer
            h.stage = Stage.MATCHED
            store.handoff = h.toJson()
            waiting = null
            // 앱이 뒤에 있으면 화면을 띄울 수 없어서 알림으로
            if (lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) open<MatchActivity>()
            else SeatService.notifyMatched(this)
            finish()
        }
        render()
    }

    private fun stopWaiting() {
        pendingAccept?.cancel()
        pendingAccept = null
        waiting = null
        store.handoff = null
        toast("알림을 취소했어요")
        render()
    }

    // 좌석 배치도를 못 누르는 경우(화면 읽기 등)를 위한 목록 선택
    private fun pickBench() {
        val options = (0..1).flatMap { row -> (0..2).map { Seat(row, it, seat.pos) } }
        val labels = options.map { it.doorLabel(store.car) + if (it.row == 0) " (위쪽 줄)" else " (아래쪽 줄)" }.toTypedArray()
        val checked = options.indexOfFirst { it.row == seat.row && it.bench == seat.bench }
        MaterialAlertDialogBuilder(this)
            .setTitle("어느 쪽 의자인가요?")
            .setSingleChoiceItems(labels, checked) { dialog, which ->
                seat = options[which]; render(); dialog.dismiss()
            }
            .show()
    }

    private fun pickPosition() {
        MaterialAlertDialogBuilder(this)
            .setTitle("의자에서 몇 번째 자리인가요?")
            .setSingleChoiceItems(Seat.POSITIONS.toTypedArray(), seat.pos) { dialog, which ->
                seat = seat.copy(pos = which); render(); dialog.dismiss()
            }
            .show()
    }

    private fun pickCar() {
        val cars = (1..10).map { "${it}번째 칸" }.toTypedArray()
        MaterialAlertDialogBuilder(this)
            .setTitle("몇 번째 칸에 타고 있나요?")
            .setSingleChoiceItems(cars, store.car - 1) { dialog, which ->
                store.car = which + 1; render(); dialog.dismiss()
            }
            .show()
    }
}
