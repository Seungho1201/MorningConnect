package com.example.morningconnect

import android.os.Handler
import android.os.Looper
import kotlin.random.Random

/**
 * 같은 칸 사람들과 주고받는 통신 층.
 *
 * 실서비스에서는 블루투스(BLE)로 같은 칸 사람을 찾고 알림·수락·인증 결과를 주고받는다.
 * "주변 유저 찾기"는 이번 구현 범위에서 제외되어, 지금은 [SimulatedSeatNetwork] 가
 * 상대방 역할을 대신한다. BLE 구현체를 만들면 [SeatNetwork.instance] 만 바꾸면 된다.
 */
interface SeatNetwork {
    // 같은 칸에 있는 사람 수
    fun nearbyCount(): Int

    // 넘겨주는 사람: 같은 칸에 알리고, 가장 먼저 '갈게요'를 누른 사람이 정해지면 onAccepted
    fun broadcast(handoff: Handoff, onAccepted: (Peer) -> Unit): Cancel

    // 넘겨주는 사람: 인증서를 보여 준 뒤 상대가 스캔해서 확인하면 onScanned
    fun awaitScan(handoff: Handoff, onScanned: () -> Unit): Cancel

    // 받는 사람: 넘겨주는 사람이 하차 재확인을 마치면 onConfirmed
    fun awaitGiverConfirm(handoff: Handoff, onConfirmed: () -> Unit): Cancel

    // 받는 사람: '갈게요' 결과 (먼저 누른 사람이 있으면 false)
    fun accept(handoff: Handoff, onResult: (Boolean) -> Unit): Cancel

    // 받는 사람 쪽에 들어오는 새 자리 알림
    fun incoming(current: Int, dir: Int, car: Int): Pair<Handoff, Peer>

    fun interface Cancel { fun cancel() }

    companion object {
        var instance: SeatNetwork = SimulatedSeatNetwork()
    }
}

class SimulatedSeatNetwork(private val random: Random = Random.Default) : SeatNetwork {
    private val main = Handler(Looper.getMainLooper())

    private fun later(minMs: Long, maxMs: Long, block: () -> Unit): SeatNetwork.Cancel {
        val r = Runnable(block)
        main.postDelayed(r, random.nextLong(minMs, maxMs))
        return SeatNetwork.Cancel { main.removeCallbacks(r) }
    }

    override fun nearbyCount() = 7

    override fun broadcast(handoff: Handoff, onAccepted: (Peer) -> Unit) =
        later(3_000, 6_000) { onAccepted(randomPeer()) }

    override fun awaitScan(handoff: Handoff, onScanned: () -> Unit) = later(3_000, 5_000, onScanned)

    override fun awaitGiverConfirm(handoff: Handoff, onConfirmed: () -> Unit) = later(4_000, 7_000, onConfirmed)

    override fun accept(handoff: Handoff, onResult: (Boolean) -> Unit) = later(600, 1_200) { onResult(true) }

    override fun incoming(current: Int, dir: Int, car: Int): Pair<Handoff, Peer> {
        val seat = Seat(random.nextInt(2), random.nextInt(3), random.nextInt(Seat.POSITIONS.size))
        val dest = Line2.step(current, 2, dir)
        return Handoff.create(Role.RECEIVER, seat, car, dest, false) to randomPeer()
    }

    private fun randomPeer(): Peer {
        val look = Look(LookCatalog.categories.associateWith { cat ->
            val kinds = LookCatalog.kinds.getValue(cat)
            Item(random.nextInt(CLOTH_COLORS.size), kinds[random.nextInt(kinds.size)])
        })
        val total = random.nextInt(5, 60)
        return Peer(look, total - random.nextInt(0, 3).coerceAtMost(total), total)
    }
}
