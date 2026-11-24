package com.example.morningconnect

import org.json.JSONObject

// 오늘의 룩 색상 12개 (디자인 순서). adj = 설명에 붙는 꾸밈말
class ClothColor(val adj: String, val argb: Int) {
    // 화면 읽기용 색 이름
    val name get() = when (adj) { "흰" -> "흰색"; "빨간" -> "빨강"; "노란" -> "노랑"; else -> adj }
}

val CLOTH_COLORS = listOf(
    ClothColor("검정", 0xFF1E2420.toInt()), ClothColor("흰", 0xFFF7F7F5.toInt()),
    ClothColor("회색", 0xFF9AA39E.toInt()), ClothColor("베이지", 0xFFC8A07A.toInt()),
    ClothColor("갈색", 0xFF7A4E2D.toInt()), ClothColor("남색", 0xFF24335C.toInt()),
    ClothColor("청색", 0xFF4A6FA5.toInt()), ClothColor("하늘색", 0xFF9DC6E8.toInt()),
    ClothColor("초록", 0xFF3FA34D.toInt()), ClothColor("카키", 0xFF6B6B3A.toInt()),
    ClothColor("빨간", 0xFFC8403A.toInt()), ClothColor("노란", 0xFFF2C84B.toInt()),
)

const val NONE = "없음"

object LookCatalog {
    val categories = listOf("상의", "하의", "신발", "가방", "모자")
    val kinds = mapOf(
        "상의" to listOf("패딩", "코트", "후드티", "셔츠", "니트", "티셔츠"),
        "하의" to listOf("청바지", "바지", "슬랙스", "반바지", "치마"),
        "신발" to listOf("운동화", "구두", "부츠", "샌들"),
        "가방" to listOf(NONE, "백팩", "숄더백", "에코백"),
        "모자" to listOf(NONE, "캡모자", "비니", "버킷햇"),
    )
}

data class Item(val color: Int, val kind: String) {
    val argb get() = CLOTH_COLORS[color].argb
    val worn get() = kind != NONE

    // "검정 패딩", 청색 청바지는 그냥 "청바지"
    fun label() = if (kind == "청바지" && CLOTH_COLORS[color].adj == "청색") kind else "${CLOTH_COLORS[color].adj} $kind"
}

data class Look(val items: Map<String, Item>) {
    operator fun get(cat: String) = items.getValue(cat)

    fun with(cat: String, item: Item) = Look(items + (cat to item))

    // 인상착의 한 줄: 상의 · 하의 · (모자) · (가방) · 신발
    fun summary(): String {
        val parts = mutableListOf(get("상의"), get("하의"))
        if (get("모자").worn) parts += get("모자")
        if (get("가방").worn) parts += get("가방")
        parts += get("신발")
        return parts.joinToString(" · ") { it.label() }
    }

    fun toJson() = JSONObject().apply {
        items.forEach { (cat, item) -> put(cat, JSONObject().put("c", item.color).put("k", item.kind)) }
    }

    companion object {
        val DEFAULT = Look(
            mapOf(
                "상의" to Item(0, "패딩"), "하의" to Item(6, "청바지"), "신발" to Item(1, "운동화"),
                "가방" to Item(0, NONE), "모자" to Item(0, NONE),
            )
        )

        fun fromJson(o: JSONObject?): Look {
            if (o == null) return DEFAULT
            val items = LookCatalog.categories.associateWith { cat ->
                val j = o.optJSONObject(cat) ?: return@associateWith DEFAULT[cat]
                val color = j.optInt("c").coerceIn(CLOTH_COLORS.indices)
                val kind = j.optString("k").takeIf { it in LookCatalog.kinds.getValue(cat) } ?: DEFAULT[cat].kind
                Item(color, kind)
            }
            return Look(items)
        }
    }
}
