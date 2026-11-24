package com.example.morningconnect

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import org.json.JSONArray
import org.json.JSONObject
import java.util.Calendar
import kotlin.math.roundToInt

class PointEntry(val at: Long, val amount: Int, val label: String)

class Destination(val station: Int, val alertBefore: Int, val arrivalAt: Long)

/**
 * 앱 데이터 저장소 (SharedPreferences).
 * 포인트·신뢰도·내역, 오늘의 룩, 하차역, 열차 위치, 임산부 인증, 쿠폰을 기기에 저장한다.
 */
class Store private constructor(private val prefs: SharedPreferences) {

    init {
        if (!prefs.getBoolean("joined", false)) {
            prefs.edit { putBoolean("joined", true) }
            addPoints(WELCOME_POINTS, "가입 축하 포인트")
        }
    }

    // ---------- 포인트 ----------
    val points get() = prefs.getInt("points", 0)

    fun history(): List<PointEntry> {
        val arr = JSONArray(prefs.getString("history", "[]"))
        return (0 until arr.length()).map {
            val o = arr.getJSONObject(it)
            PointEntry(o.getLong("t"), o.getInt("a"), o.getString("l"))
        }.sortedByDescending { it.at }
    }

    fun addPoints(amount: Int, label: String, at: Long = System.currentTimeMillis()) {
        val arr = JSONArray(prefs.getString("history", "[]"))
        arr.put(JSONObject().put("t", at).put("a", amount).put("l", label))
        prefs.edit {
            putInt("points", points + amount)
            putString("history", arr.toString())
        }
    }

    fun earnedThisMonth(now: Long = System.currentTimeMillis()): Int {
        val cal = Calendar.getInstance().apply {
            timeInMillis = now
            set(Calendar.DAY_OF_MONTH, 1); set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }
        return history().filter { it.at >= cal.timeInMillis && it.amount > 0 }.sumOf { it.amount }
    }

    // ---------- 신뢰도 ----------
    // 약속 = '알리기' 후 매칭됐거나 '갈게요'를 누른 것. 끝까지 지키면 kept 도 오른다
    val promisesKept get() = prefs.getInt("kept", 0)
    val promisesTotal get() = prefs.getInt("total", 0)
    val compliments get() = prefs.getInt("compliments", 0)
    val seatsGiven get() = prefs.getInt("given", 0)
    val seatsReceived get() = prefs.getInt("received", 0)

    val trust get() = trustOf(promisesKept, promisesTotal)

    fun keepPromise(gave: Boolean) = prefs.edit {
        putInt("kept", promisesKept + 1)
        putInt("total", promisesTotal + 1)
        if (gave) putInt("given", seatsGiven + 1) else putInt("received", seatsReceived + 1)
    }

    fun breakPromise() = prefs.edit { putInt("total", promisesTotal + 1) }

    // ---------- 오늘의 룩 ----------
    var look: Look
        get() = Look.fromJson(prefs.getString("look", null)?.let(::JSONObject))
        set(value) = prefs.edit { putString("look", value.toJson().toString()) }

    // ---------- 열차 위치 ----------
    var clock: TrainClock
        get() {
            if (!prefs.contains("startedAt")) prefs.edit { putLong("startedAt", System.currentTimeMillis()) }
            return TrainClock(
                prefs.getInt("origin", Line2.indexOf("강남")),
                prefs.getLong("startedAt", 0),
                prefs.getInt("dir", Line2.OUTER),
            )
        }
        set(value) = prefs.edit {
            putInt("origin", value.origin)
            putLong("startedAt", value.startedAt)
            putInt("dir", value.dir)
        }

    var car: Int
        get() = prefs.getInt("car", 3)
        set(value) = prefs.edit { putInt("car", value) }

    // ---------- 하차역 ----------
    var destination: Destination?
        get() {
            val s = prefs.getInt("dest", -1)
            if (s < 0) return null
            return Destination(s, prefs.getInt("alertBefore", 2), prefs.getLong("arrivalAt", 0))
        }
        set(value) = prefs.edit {
            putInt("dest", value?.station ?: -1)
            if (value != null) {
                putInt("alertBefore", value.alertBefore)
                putLong("arrivalAt", value.arrivalAt)
            }
        }

    // 하차역까지 남은 정거장. 하차역이 없거나 이미 도착 시각이 지났으면 null (지난 하차역은 지움)
    fun stopsLeft(now: Long = System.currentTimeMillis()): Int? {
        val d = destination ?: return null
        if (now >= d.arrivalAt + ARRIVED_GRACE_MS) {
            destination = null
            return null
        }
        val c = clock
        return Line2.stopsBetween(c.current(now), d.station, c.dir)
    }

    var alertBefore: Int
        get() = prefs.getInt("alertBefore", 2)
        set(value) = prefs.edit { putInt("alertBefore", value) }

    var voiceGuide: Boolean
        get() = prefs.getBoolean("voice", true)
        set(value) = prefs.edit { putBoolean("voice", value) }

    var autoSuggest: Boolean
        get() = prefs.getBoolean("autoSuggest", false)
        set(value) = prefs.edit { putBoolean("autoSuggest", value) }

    // ---------- 임산부 ----------
    var pregnantVerified: Boolean
        get() = prefs.getBoolean("pregnant", false)
        set(value) = prefs.edit { putBoolean("pregnant", value) }

    var pregnantPriority: Boolean
        get() = prefs.getBoolean("pregnantPriority", true)
        set(value) = prefs.edit { putBoolean("pregnantPriority", value) }

    // ---------- 쿠폰 ----------
    fun coupons(): List<JSONObject> {
        val arr = JSONArray(prefs.getString("coupons", "[]"))
        return (0 until arr.length()).map { arr.getJSONObject(it) }.reversed()
    }

    fun addCoupon(name: String, code: String) {
        val arr = JSONArray(prefs.getString("coupons", "[]"))
        arr.put(JSONObject().put("name", name).put("code", code).put("t", System.currentTimeMillis()))
        prefs.edit { putString("coupons", arr.toString()) }
    }

    // ---------- 자리 넘기기 진행 상태 ----------
    var handoff: JSONObject?
        get() = prefs.getString("handoff", null)?.let(::JSONObject)
        set(value) = prefs.edit { putString("handoff", value?.toString()) }

    companion object {
        const val WELCOME_POINTS = 500
        const val BASE_REWARD = 50      // 자리를 넘겨주면 앱이 주는 기본 지급
        const val TRANSFER = 50         // 자리를 받은 사람 → 넘겨준 사람
        const val ARRIVED_GRACE_MS = 60_000L  // 도착 후 1분은 '도착'으로 보여 주고 그다음 하차역을 지움

        // 기록이 없으면 100, 있으면 약속 이행률
        fun trustOf(kept: Int, total: Int) = if (total == 0) 100 else (kept * 100f / total).roundToInt()

        @Volatile private var instance: Store? = null

        // 테스트마다 새 저장소로
        @androidx.annotation.VisibleForTesting
        fun resetForTest() { instance = null }
        fun get(context: Context): Store = instance ?: synchronized(this) {
            instance ?: Store(context.applicationContext.getSharedPreferences("morning", Context.MODE_PRIVATE)).also { instance = it }
        }
    }
}

val Context.store get() = Store.get(this)
