package com.example.morningconnect

import android.annotation.SuppressLint
import android.bluetooth.BluetoothManager
import android.bluetooth.le.AdvertiseCallback
import android.bluetooth.le.AdvertiseData
import android.bluetooth.le.AdvertiseSettings
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import java.nio.ByteBuffer
import java.security.SecureRandom

/**
 * 같은 칸 사람과 BLE 광고만으로 주고받는 메시지 (연결 없이).
 * 폰마다 지금 상태 하나를 계속 광고하고, 동시에 주변 광고를 스캔해서 상대 상태를 읽는다.
 *
 *   넘겨주는 사람: Presence → Offer(자리 알림) → Match(받는 사람 지정 + 인증 비밀값) → Match(confirmed)
 *   받는 사람:    Presence → Accept('갈게요') → Presence ……………………………………→ Scanned(인증서 확인)
 */
sealed class SeatMsg {
    abstract val from: Int   // 보낸 폰의 앱 안 번호 (블루투스 주소는 자꾸 바뀌어서 따로 둠)

    data class Presence(override val from: Int) : SeatMsg()

    class Offer(
        override val from: Int, val session: Int, val seat: Seat, val car: Int, val dest: Int,
        val preg: Boolean, val giver: Peer,
    ) : SeatMsg()

    class Accept(override val from: Int, val session: Int, val receiver: Peer) : SeatMsg()

    class Match(
        override val from: Int, val session: Int, val receiver: Int, val secret: ByteArray, val confirmed: Boolean,
    ) : SeatMsg()

    data class Scanned(override val from: Int, val session: Int) : SeatMsg()
}

/**
 * 광고에 담는 바이트 형식. 레거시 광고(31바이트)의 제조사 데이터에 들어가도록 최대 [MAX]바이트.
 * [MAGIC](2) + 종류(1) + 보낸 폰(4) + 종류별 내용
 */
object SeatCodec {
    // shortcut: 0xFFFF 는 블루투스 SIG 의 테스트용 회사 ID. 출시하려면 할당받은 회사 ID로 바꿔야 함
    const val COMPANY = 0xFFFF
    val MAGIC = byteArrayOf('M'.code.toByte(), 'C'.code.toByte())
    const val MAX = 24

    private const val PRESENCE: Byte = 0
    private const val OFFER: Byte = 1
    private const val ACCEPT: Byte = 2
    private const val MATCH: Byte = 3
    private const val SCANNED: Byte = 4

    fun encode(m: SeatMsg): ByteArray {
        val b = ByteBuffer.allocate(MAX).put(MAGIC)
        when (m) {
            is SeatMsg.Presence -> b.put(PRESENCE).putInt(m.from)
            is SeatMsg.Offer -> {
                b.put(OFFER).putInt(m.from).putInt(m.session)
                b.put(((m.seat.row shl 4) or (m.seat.bench shl 2) or m.seat.pos).toByte())
                b.put(m.car.toByte()).put(m.dest.toByte()).put(if (m.preg) 1 else 0)
                putPeer(b, m.giver)
            }
            is SeatMsg.Accept -> { b.put(ACCEPT).putInt(m.from).putInt(m.session); putPeer(b, m.receiver) }
            is SeatMsg.Match -> b.put(MATCH).putInt(m.from).putInt(m.session).putInt(m.receiver)
                .put(m.secret).put(if (m.confirmed) 1 else 0)
            is SeatMsg.Scanned -> b.put(SCANNED).putInt(m.from).putInt(m.session)
        }
        return b.array().copyOf(b.position())
    }

    // 모르는 형식이거나 값이 범위를 벗어나면 null
    fun decode(bytes: ByteArray): SeatMsg? = try {
        val b = ByteBuffer.wrap(bytes)
        if (b.get() != MAGIC[0] || b.get() != MAGIC[1]) null
        else {
            val type = b.get()
            val from = b.int
            when (type) {
                PRESENCE -> SeatMsg.Presence(from)
                OFFER -> {
                    val session = b.int
                    val s = b.get().toInt()
                    val seat = Seat((s shr 4) and 3, (s shr 2) and 3, s and 3)
                    require(seat.row in 0..1 && seat.bench in 0..2 && seat.pos in Seat.POSITIONS.indices)
                    val car = b.get().toInt()
                    val dest = b.get().toInt()
                    require(car in 1..10 && dest in 0 until Line2.size)
                    SeatMsg.Offer(from, session, seat, car, dest, b.get().toInt() == 1, getPeer(b, from))
                }
                ACCEPT -> SeatMsg.Accept(from, b.int, getPeer(b, from))
                MATCH -> SeatMsg.Match(from, b.int, b.int, ByteArray(8).also { b.get(it) }, b.get().toInt() == 1)
                SCANNED -> SeatMsg.Scanned(from, b.int)
                else -> null
            }
        }
    } catch (e: RuntimeException) {
        null  // 짧거나 잘못된 광고
    }

    // 룩: 종류마다 1바이트 (색 4비트 + 종류 3비트), 신뢰도: 약속 이행/전체 (각 1바이트)
    private fun putPeer(b: ByteBuffer, p: Peer) {
        LookCatalog.categories.forEach { cat ->
            val item = p.look[cat]
            val kind = LookCatalog.kinds.getValue(cat).indexOf(item.kind).coerceAtLeast(0)
            b.put(((item.color shl 3) or kind).toByte())
        }
        // 255번이 넘으면 비율을 지키며 줄임
        val total = p.total.coerceAtMost(255)
        val kept = if (p.total > 255) p.kept * 255 / p.total else p.kept
        b.put(kept.toByte()).put(total.toByte())
    }

    private fun getPeer(b: ByteBuffer, device: Int): Peer {
        val items = LookCatalog.categories.associateWith { cat ->
            val v = b.get().toInt() and 0xFF
            val kind = LookCatalog.kinds.getValue(cat).getOrNull(v and 7)
            val color = v ushr 3
            require(kind != null && color in CLOTH_COLORS.indices)
            Item(color, kind)
        }
        val kept = b.get().toInt() and 0xFF
        val total = b.get().toInt() and 0xFF
        require(kept <= total)
        return Peer(Look(items), kept, total, device)
    }

    fun session(h: Handoff) = h.id.toLong(16).toInt()
    fun hex(bytes: ByteArray) = bytes.joinToString("") { "%02x".format(it) }
    fun bytes(hex: String) = ByteArray(hex.length / 2) { hex.substring(it * 2, it * 2 + 2).toInt(16).toByte() }
}

/**
 * BLE 로 같은 칸 사람과 주고받는 [SeatNetwork]. [SeatService] 가 켜고 끈다.
 * 스캔·광고 콜백과 화면 쪽 호출이 모두 메인 스레드에서 돈다.
 */
@SuppressLint("MissingPermission")  // SeatService 가 권한을 확인한 뒤에만 start()
class BleSeatNetwork(private val context: Context) : SeatNetwork {
    val me = SecureRandom().nextInt()
    private val main = Handler(Looper.getMainLooper())
    private val adapter get() = context.getSystemService(BluetoothManager::class.java)?.adapter
    private val seen = HashMap<Int, Long>()            // 폰 번호 → 마지막으로 본 시각
    private val listeners = mutableListOf<(SeatMsg) -> Unit>()
    private val offered = HashSet<Int>()               // 이미 알림을 띄운 세션
    private val taken = HashSet<Int>()                 // 이미 누군가와 연결된 세션
    private var current: SeatMsg = SeatMsg.Presence(me)
    private var advCallback: AdvertiseCallback? = null
    private var scanning = false

    // 받는 사람 쪽: 새 자리 알림 / 그 자리가 다른 사람과 연결됨
    var onOffer: ((Handoff) -> Unit)? = null
    var onTaken: ((String) -> Unit)? = null

    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) = handle(result)
        override fun onBatchScanResults(results: MutableList<ScanResult>) = results.forEach(::handle)
        override fun onScanFailed(errorCode: Int) {
            scanning = false
            Log.w(TAG, "scan failed $errorCode")
        }
    }

    // 블루투스가 꺼져 있거나 BLE 광고를 못 하는 폰이면 false
    fun start(): Boolean {
        val a = adapter ?: return false
        if (!a.isEnabled) return false
        val scanner = a.bluetoothLeScanner ?: return false
        if (!scanning) {
            val filter = ScanFilter.Builder()
                .setManufacturerData(SeatCodec.COMPANY, SeatCodec.MAGIC, byteArrayOf(-1, -1))
                .build()
            // 화면이 꺼져도 스캔하려면 필터가 있어야 함. BALANCED: 4초마다 1초씩 (지연 최대 약 4초)
            val settings = ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_BALANCED).build()
            scanner.startScan(listOf(filter), settings, scanCallback)
            scanning = true
        }
        advertise(current)
        return a.bluetoothLeAdvertiser != null
    }

    fun stop() {
        runCatching { adapter?.bluetoothLeScanner?.stopScan(scanCallback) }
        advCallback?.let { cb -> runCatching { adapter?.bluetoothLeAdvertiser?.stopAdvertising(cb) } }
        advCallback = null
        scanning = false
        main.removeCallbacksAndMessages(null)
    }

    private fun advertise(msg: SeatMsg) {
        current = msg
        val advertiser = adapter?.bluetoothLeAdvertiser ?: return
        advCallback?.let { runCatching { advertiser.stopAdvertising(it) } }
        val settings = AdvertiseSettings.Builder()
            .setAdvertiseMode(
                if (msg is SeatMsg.Presence) AdvertiseSettings.ADVERTISE_MODE_LOW_POWER
                else AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY
            )
            .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_MEDIUM)
            .setConnectable(false)
            .build()
        val data = AdvertiseData.Builder()
            .addManufacturerData(SeatCodec.COMPANY, SeatCodec.encode(msg))
            .setIncludeDeviceName(false)
            .build()
        val cb = object : AdvertiseCallback() {
            override fun onStartFailure(errorCode: Int) { Log.w(TAG, "advertise failed $errorCode") }
        }
        advCallback = cb
        runCatching { advertiser.startAdvertising(settings, data, cb) }
            .onFailure { Log.w(TAG, "advertise failed", it) }
    }

    // 지금 광고가 이 세션 것이면 평소 상태로
    private fun idle(session: Int) {
        val c = current
        val mine = when (c) {
            is SeatMsg.Offer -> c.session; is SeatMsg.Accept -> c.session
            is SeatMsg.Match -> c.session; is SeatMsg.Scanned -> c.session
            is SeatMsg.Presence -> return
        }
        if (mine == session) advertise(SeatMsg.Presence(me))
    }

    private fun handle(r: ScanResult) {
        val bytes = r.scanRecord?.getManufacturerSpecificData(SeatCodec.COMPANY) ?: return
        val msg = SeatCodec.decode(bytes) ?: return
        if (msg.from == me) return
        seen[msg.from] = SystemClock.elapsedRealtime()
        listeners.toList().forEach { it(msg) }
        when (msg) {
            is SeatMsg.Offer -> maybeOffer(msg)
            is SeatMsg.Match -> if (taken.add(msg.session) && msg.receiver != me) onTaken?.invoke(Handoff.sessionId(msg.session))
            else -> Unit
        }
    }

    private fun maybeOffer(o: SeatMsg.Offer) {
        if (o.session in taken || !offered.add(o.session)) return
        // 임산부 우선: 인증된 임산부가 아니면 조금 늦게 알림
        val delay = if (o.preg && !context.store.pregnantVerified) PREGNANT_FIRST_MS else 0L
        main.postDelayed({
            if (o.session in taken) return@postDelayed
            onOffer?.invoke(
                Handoff(Handoff.sessionId(o.session), Role.RECEIVER, "", o.seat, o.car, o.dest, o.preg, partner = o.giver)
            )
        }, delay)
    }

    private fun listen(block: (SeatMsg) -> Unit): SeatNetwork.Cancel {
        listeners += block
        return SeatNetwork.Cancel { listeners -= block }
    }

    private fun myPeer(): Peer {
        val s = context.store
        return Peer(s.look, s.promisesKept, s.promisesTotal, me)
    }

    // 최근에 본 근처 폰 번호 (정렬해서 점 순서가 매번 같게)
    fun nearby(): List<Int> {
        val now = SystemClock.elapsedRealtime()
        seen.values.removeAll { now - it > SEEN_MS }
        return seen.keys.sorted()
    }

    override fun nearbyCount() = nearby().size

    override fun broadcast(handoff: Handoff, onAccepted: (Peer) -> Unit): SeatNetwork.Cancel {
        val session = SeatCodec.session(handoff)
        advertise(
            SeatMsg.Offer(me, session, handoff.seat, handoff.car, handoff.destStation, handoff.pregnantPriority, myPeer())
        )
        lateinit var stop: SeatNetwork.Cancel
        stop = listen { m ->
            if (m !is SeatMsg.Accept || m.session != session) return@listen
            // 가장 먼저 본 '갈게요' 한 명과 연결. 다른 사람들은 Match 광고를 보고 졌다는 걸 앎
            stop.cancel()
            advertise(SeatMsg.Match(me, session, m.from, SeatCodec.bytes(handoff.secret), false))
            onAccepted(m.receiver)
        }
        return SeatNetwork.Cancel { stop.cancel(); if (current is SeatMsg.Offer) idle(session) }
    }

    override fun accept(handoff: Handoff, onResult: (Boolean) -> Unit): SeatNetwork.Cancel {
        val session = SeatCodec.session(handoff)
        advertise(SeatMsg.Accept(me, session, myPeer()))
        lateinit var stop: SeatNetwork.Cancel
        val timeout = Runnable { stop.cancel(); idle(session); onResult(false) }
        stop = listen { m ->
            if (m !is SeatMsg.Match || m.session != session) return@listen
            stop.cancel()
            main.removeCallbacks(timeout)
            idle(session)
            val mine = m.receiver == me
            if (mine) handoff.secret = SeatCodec.hex(m.secret)
            onResult(mine)
        }
        // 넘겨주는 사람이 알림을 거뒀거나 멀어졌으면 응답이 안 옴
        main.postDelayed(timeout, ACCEPT_TIMEOUT_MS)
        return SeatNetwork.Cancel { stop.cancel(); main.removeCallbacks(timeout); idle(session) }
    }

    override fun awaitGiverConfirm(handoff: Handoff, onConfirmed: () -> Unit): SeatNetwork.Cancel {
        val session = SeatCodec.session(handoff)
        lateinit var stop: SeatNetwork.Cancel
        stop = listen { m ->
            if (m is SeatMsg.Match && m.session == session && m.receiver == me && m.confirmed) {
                stop.cancel(); onConfirmed()
            }
        }
        return stop
    }

    override fun confirmGetOff(handoff: Handoff) {
        val partner = handoff.partner ?: return
        advertise(SeatMsg.Match(me, SeatCodec.session(handoff), partner.device, SeatCodec.bytes(handoff.secret), true))
    }

    override fun awaitScan(handoff: Handoff, onScanned: () -> Unit): SeatNetwork.Cancel {
        val session = SeatCodec.session(handoff)
        val partner = handoff.partner?.device
        lateinit var stop: SeatNetwork.Cancel
        stop = listen { m ->
            if (m is SeatMsg.Scanned && m.session == session && m.from == partner) {
                stop.cancel(); onScanned()
            }
        }
        return stop
    }

    override fun reportScanned(handoff: Handoff) {
        val session = SeatCodec.session(handoff)
        advertise(SeatMsg.Scanned(me, session))
        // 상대가 볼 때까지 잠시 더 광고
        main.postDelayed({ idle(session) }, SCANNED_LINGER_MS)
    }

    override fun end(handoff: Handoff) {
        if (current !is SeatMsg.Scanned) idle(SeatCodec.session(handoff))
    }

    companion object {
        private const val TAG = "BleSeatNetwork"
        const val SEEN_MS = 30_000L
        const val ACCEPT_TIMEOUT_MS = 20_000L
        const val SCANNED_LINGER_MS = 60_000L
        const val PREGNANT_FIRST_MS = 10_000L
    }
}
