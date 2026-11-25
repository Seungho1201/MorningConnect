package com.example.morningconnect

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

// BLE 광고에 담는 메시지: 되돌렸을 때 같은 값, 광고 한 개(24바이트)에 들어감, 잘못된 바이트는 무시
class SeatCodecTest {
    private val look = Look.DEFAULT.with("상의", Item(11, "티셔츠")).with("가방", Item(5, "에코백")).with("모자", Item(3, "버킷햇"))
    private val peer = Peer(look, 41, 43, device = 7)

    private fun roundTrip(m: SeatMsg): SeatMsg {
        val bytes = SeatCodec.encode(m)
        assertTrue("${bytes.size} bytes", bytes.size <= SeatCodec.MAX)
        return SeatCodec.decode(bytes)!!
    }

    @Test
    fun offer_roundTrips() {
        val m = roundTrip(SeatMsg.Offer(-5, 0x7abc0123, Seat(1, 2, 3), 10, Line2.size - 1, true, peer)) as SeatMsg.Offer
        assertEquals(-5, m.from)
        assertEquals(0x7abc0123, m.session)
        assertEquals(Seat(1, 2, 3), m.seat)
        assertEquals(10, m.car)
        assertEquals(Line2.size - 1, m.dest)
        assertTrue(m.preg)
        assertEquals(look, m.giver.look)
        assertEquals(41, m.giver.kept)
        assertEquals(43, m.giver.total)
        assertEquals(-5, m.giver.device)  // 상대 폰 번호 = 보낸 폰
    }

    @Test
    fun acceptMatchScannedPresence_roundTrip() {
        val a = roundTrip(SeatMsg.Accept(1, 2, peer)) as SeatMsg.Accept
        assertEquals(2, a.session); assertEquals(look, a.receiver.look)

        val secret = SeatCodec.bytes(Handoff.newSecret())
        val m = roundTrip(SeatMsg.Match(1, 2, 3, secret, true)) as SeatMsg.Match
        assertEquals(3, m.receiver); assertArrayEquals(secret, m.secret); assertTrue(m.confirmed)

        assertEquals(SeatMsg.Scanned(1, 2), roundTrip(SeatMsg.Scanned(1, 2)))
        assertEquals(SeatMsg.Presence(9), roundTrip(SeatMsg.Presence(9)))
    }

    @Test
    fun bigTrustCounts_keepRatio() {
        val m = roundTrip(SeatMsg.Accept(1, 2, Peer(look, 500, 1000))) as SeatMsg.Accept
        assertEquals(255, m.receiver.total)
        assertEquals(127, m.receiver.kept)
    }

    @Test
    fun sessionAndSecret_matchOnBothPhones() {
        // 넘겨주는 사람의 세션 id·비밀값을 BLE로 받은 쪽이 똑같이 만들어야 같은 색·번호·인증서
        val h = Handoff.create(Role.GIVER, Seat(0, 1, 1), 3, 25, false)
        val other = Handoff(Handoff.sessionId(SeatCodec.session(h)), Role.RECEIVER, SeatCodec.hex(SeatCodec.bytes(h.secret)), h.seat, 3, 25, false)
        assertEquals(h.id, other.id)
        assertEquals(h.number, other.number)
        assertEquals(h.color, other.color)
        assertTrue(other.verify(h.certificate()))
    }

    @Test
    fun garbage_isIgnored() {
        assertNull(SeatCodec.decode(byteArrayOf()))
        assertNull(SeatCodec.decode(byteArrayOf('X'.code.toByte(), 'Y'.code.toByte(), 0, 0, 0, 0, 1)))
        // 잘린 Offer
        val offer = SeatCodec.encode(SeatMsg.Offer(1, 2, Seat(0, 0, 0), 3, 4, false, peer))
        assertNull(SeatCodec.decode(offer.copyOf(offer.size - 3)))
        // 범위를 벗어난 칸 번호
        assertNull(SeatCodec.decode(offer.copyOf().also { it[12] = 99 }))
        // 모르는 종류
        assertNull(SeatCodec.decode(SeatCodec.MAGIC + byteArrayOf(42, 0, 0, 0, 1)))
    }
}
