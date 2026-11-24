package com.example.morningconnect

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.example.morningconnect.databinding.ActivityReceiveBinding
import org.json.JSONObject

// 받는 쪽: 같은 칸 사람이 보낸 "곧 비어요" 알림. '갈게요'를 누르면 연결 화면으로
class ReceiveActivity : AppCompatActivity() {
    private lateinit var binding: ActivityReceiveBinding
    private lateinit var handoff: Handoff
    private lateinit var giver: Peer
    private var pending: SeatNetwork.Cancel? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityReceiveBinding.inflate(layoutInflater)
        setScreen(binding.root, dark = true)

        val saved = savedInstanceState?.getString("handoff")?.let { Handoff.fromJson(JSONObject(it)) }
        if (saved?.partner != null) {
            handoff = saved; giver = saved.partner!!
        } else {
            val clock = store.clock
            val (h, p) = SeatNetwork.instance.incoming(clock.current(), clock.dir, store.car)
            handoff = h.apply { partner = p }; giver = p
        }

        binding.no.setOnClickListener { finish() }
        binding.go.setOnClickListener { accept() }
        render()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putString("handoff", handoff.toJson().toString())
    }

    override fun onDestroy() {
        pending?.cancel()
        super.onDestroy()
    }

    private fun render() {
        val store = store
        val clock = store.clock
        val cur = clock.current()
        binding.route.text = "${Line2.at(cur).name} → ${Line2.at(Line2.step(cur, 1, clock.dir)).name}"
        binding.carCount.text = "같은 칸 ${SeatNetwork.instance.nearbyCount()}명"

        val door = handoff.seat.doorLabel(handoff.car)
        binding.title.text = "$door 자리가\n곧 비어요"
        val stops = Line2.stopsBetween(cur, handoff.destStation, clock.dir)
        binding.`when`.text = "${stops}정거장 뒤 ${Line2.at(handoff.destStation).name}에서 내릴 예정이에요"

        binding.seatMap.highlight = getColor(R.color.orange)
        binding.seatMap.label = null
        binding.seatMap.dashedAisle = false
        binding.seatMap.aisleText = handoff.seat.posLabel()
        binding.seatMap.select(handoff.seat.row, handoff.seat.bench)
        binding.seatMap.contentDescription = "좌석 배치도. $door, ${handoff.seat.posLabel()}"

        binding.avatar.look = giver.look
        binding.lookText.text = giver.look.summary()
        binding.trust.text = "신뢰도 ${giver.trust} · 약속 이행 ${giver.kept}/${giver.total}회"
        binding.giver.contentDescription = "넘겨주는 분: ${binding.lookText.text}, ${binding.trust.text}"

        binding.rule.text = "가장 먼저 '갈게요'를 누른 한 명만 연결돼요\n자리를 받으면 ${Store.TRANSFER}P가 상대에게 이동해요"
        val enough = store.points >= Store.TRANSFER
        binding.go.isEnabled = enough
        binding.go.text = if (enough) "갈게요" else "포인트가 부족해요 (${Store.TRANSFER}P 필요)"
    }

    private fun accept() {
        binding.go.isEnabled = false
        binding.go.text = "연결하는 중…"
        pending = SeatNetwork.instance.accept(handoff) { ok ->
            if (!ok) {
                toast("다른 분이 먼저 '갈게요'를 눌렀어요")
                finish()
                return@accept
            }
            handoff.stage = Stage.MATCHED
            store.handoff = handoff.toJson()
            open<MatchActivity>()
            finish()
        }
    }
}
