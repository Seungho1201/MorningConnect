package com.example.morningconnect

import android.os.Handler
import android.os.Looper
import kotlin.random.Random

/**
 * 같은 칸 사람들과 주고받는 통신 층.
 *
 * 블루투스(BLE)를 쓸 수 있으면 [SeatService] 가 [BleSeatNetwork] 로 바꿔 끼운다.
 * 권한이 없거나 블루투스가 꺼져 있으면 [SimulatedSeatNetwork] 가 상대방 역할을 대신한다 (체험·테스트용).
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

    // 넘겨주는 사람: 하차 재확인을 상대에게 알림
    fun confirmGetOff(handoff: Handoff) {}

    // 받는 사람: 인증서(QR·번호)를 확인했다고 상대에게 알림
    fun reportScanned(handoff: Handoff) {}

    // 인계가 끝나거나 취소되면 알리던 것을 거둠
    fun end(handoff: Handoff) {}

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

    // 받는 사람 쪽에 들어오는 새 자리 알림 (체험용)
    fun incoming(current: Int, dir: Int, car: Int): Pair<Handoff, Peer> {
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
