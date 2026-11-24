package com.example.morningconnect

import org.json.JSONObject
import java.security.SecureRandom
import java.util.UUID
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

enum class Role { GIVER, RECEIVER }

// WAITING: 같은 칸에 알리고 기다리는 중 → MATCHED: 연결됨 → CONFIRMED: 하차 재확인 끝 → DONE / CANCELLED
enum class Stage { WAITING, MATCHED, CONFIRMED, DONE, CANCELLED }

/**
 * 좌석 위치. 한 칸의 한쪽 면 = 문 4개 사이에 긴 의자 3개.
 * row 0 = 위쪽 줄, 1 = 아래쪽 줄 / bench 0..2 = 왼쪽(진행 반대)부터 / pos = 문에서 몇 번째 자리
 */
data class Seat(val row: Int, val bench: Int, val pos: Int) {
    fun doorLabel(car: Int) = "$car-${bench + 1} 문 " + if (row == 0) "오른쪽" else "건너편"
    fun posLabel() = POSITIONS[pos]

    companion object {
        val POSITIONS = listOf("문 바로 옆 자리", "끝에서 두 번째 자리", "끝에서 세 번째 자리", "가운데 자리")
    }
}

// 상대방 정보 (실서비스에서는 블루투스로 받는 값)
class Peer(val look: Look, val kept: Int, val total: Int) {
    val trust get() = Store.trustOf(kept, total)
}

class Handoff(
    val id: String,
    val role: Role,
    val secret: String,
    val seat: Seat,
    val car: Int,
    val destStation: Int,        // 넘겨주는 사람이 내릴 역
    val pregnantPriority: Boolean,
    var stage: Stage = Stage.WAITING,
    var partner: Peer? = null,
) {
    // 두 폰에 같은 색과 번호: 둘 다 같은 세션 id 에서 계산
    private val hash get() = id.hashCode().toLong() and 0x7fffffff
    val number get() = (10 + hash % 90).toInt()
    val color get() = MATCH_COLORS[((hash / 90) % MATCH_COLORS.size).toInt()]

    // 인증서 QR 내용: 세션 id + 번호 + 서명(세션 비밀값으로 만든 HMAC)
    fun certificate() = "MC1:$id:$number:${sign("$id:$number")}"

    fun verify(scanned: String): Boolean {
        val p = scanned.trim().split(":")
        if (p.size != 4 || p[0] != "MC1") return false
        return p[1] == id && p[2] == number.toString() && p[3] == sign("${p[1]}:${p[2]}")
    }

    fun verifyNumber(input: String) = input.trim() == number.toString()

    private fun sign(msg: String): String {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(secret.toByteArray(), "HmacSHA256"))
        return mac.doFinal(msg.toByteArray()).take(8).joinToString("") { "%02x".format(it) }
    }

    fun toJson(): JSONObject = JSONObject()
        .put("id", id).put("role", role.name).put("secret", secret)
        .put("row", seat.row).put("bench", seat.bench).put("pos", seat.pos)
        .put("car", car).put("dest", destStation).put("preg", pregnantPriority).put("stage", stage.name)
        .apply {
            partner?.let { put("pLook", it.look.toJson()).put("pKept", it.kept).put("pTotal", it.total) }
        }

    companion object {
        val MATCH_COLORS = listOf(
            0xFFF5A04C.toInt(), 0xFF7CC3F0.toInt(), 0xFFF29BB8.toInt(),
            0xFFB9DB62.toInt(), 0xFFB9A3F0.toInt(), 0xFF6FD3B5.toInt(),
        )

        fun newSecret(): String {
            val b = ByteArray(16).also { SecureRandom().nextBytes(it) }
            return b.joinToString("") { "%02x".format(it) }
        }

        fun create(role: Role, seat: Seat, car: Int, dest: Int, pregnantPriority: Boolean) =
            Handoff(UUID.randomUUID().toString(), role, newSecret(), seat, car, dest, pregnantPriority)

        fun fromJson(o: JSONObject?): Handoff? {
            o ?: return null
            return runCatching {
                Handoff(
                    o.getString("id"), Role.valueOf(o.getString("role")), o.getString("secret"),
                    Seat(o.getInt("row"), o.getInt("bench"), o.getInt("pos")),
                    o.getInt("car"), o.getInt("dest"), o.getBoolean("preg"), Stage.valueOf(o.getString("stage")),
                    o.optJSONObject("pLook")?.let { Peer(Look.fromJson(it), o.getInt("pKept"), o.getInt("pTotal")) },
                )
            }.getOrNull()
        }
    }
}
