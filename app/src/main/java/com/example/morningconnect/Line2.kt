package com.example.morningconnect

import kotlin.math.max

class Station(val name: String, val roman: String)

// 2호선 본선 43개 역. 외선순환(시계 방향) 순서: 시청 → 을지로입구 → … → 역삼 → 강남 → 교대 → …
object Line2 {
    val stations = listOf(
        Station("시청", "City Hall"), Station("을지로입구", "Euljiro 1-ga"), Station("을지로3가", "Euljiro 3-ga"),
        Station("을지로4가", "Euljiro 4-ga"), Station("동대문역사문화공원", "DDP"), Station("신당", "Sindang"),
        Station("상왕십리", "Sangwangsimni"), Station("왕십리", "Wangsimni"), Station("한양대", "Hanyang Univ."),
        Station("뚝섬", "Ttukseom"), Station("성수", "Seongsu"), Station("건대입구", "Konkuk Univ."),
        Station("구의", "Guui"), Station("강변", "Gangbyeon"), Station("잠실나루", "Jamsillaru"),
        Station("잠실", "Jamsil"), Station("잠실새내", "Jamsilsaenae"), Station("종합운동장", "Sports Complex"),
        Station("삼성", "Samseong"), Station("선릉", "Seolleung"), Station("역삼", "Yeoksam"),
        Station("강남", "Gangnam"), Station("교대", "Gyodae"), Station("서초", "Seocho"),
        Station("방배", "Bangbae"), Station("사당", "Sadang"), Station("낙성대", "Nakseongdae"),
        Station("서울대입구", "Seoul Nat'l Univ."), Station("봉천", "Bongcheon"), Station("신림", "Sillim"),
        Station("신대방", "Sindaebang"), Station("구로디지털단지", "Guro Digital Complex"), Station("대림", "Daerim"),
        Station("신도림", "Sindorim"), Station("문래", "Mullae"), Station("영등포구청", "Yeongdeungpo-gu Office"),
        Station("당산", "Dangsan"), Station("합정", "Hapjeong"), Station("홍대입구", "Hongik Univ."),
        Station("신촌", "Sinchon"), Station("이대", "Ewha Womans Univ."), Station("아현", "Ahyeon"),
        Station("충정로", "Chungjeongno"),
    )
    val size get() = stations.size

    const val OUTER = 1   // 외선순환
    const val INNER = -1  // 내선순환

    fun dirName(dir: Int) = if (dir == OUTER) "외선순환" else "내선순환"

    fun at(index: Int) = stations[Math.floorMod(index, size)]

    // from 에서 dir 방향으로 몇 정거장 가야 to 인지 (0 = 같은 역)
    fun stopsBetween(from: Int, to: Int, dir: Int) = Math.floorMod((to - from) * dir, size)

    fun step(from: Int, stops: Int, dir: Int) = Math.floorMod(from + stops * dir, size)

    fun indexOf(name: String) = stations.indexOfFirst { it.name == name }
}

/**
 * 지금 열차 위치.
 *
 * 지하에서 GPS가 잡히지 않아 실제 서비스에서는 역사 비콘·기지국 정보로 위치를 받아야 하는데,
 * 그 부분(주변 탐지)은 이번 구현 범위에서 빠져 있어서 "출발역 + 출발 시각 + 역당 평균 소요 시간"으로 계산한다.
 * 출발역은 하차역 화면에서 역을 길게 눌러 다시 맞출 수 있다.
 */
class TrainClock(val origin: Int, val startedAt: Long, val dir: Int) {
    fun stopsTravelled(now: Long = System.currentTimeMillis()) = (max(0L, now - startedAt) / MS_PER_STOP).toInt()

    fun current(now: Long = System.currentTimeMillis()) = Line2.step(origin, stopsTravelled(now), dir)

    // 지금 역을 떠나 다음 역까지 간 정도 (0~1)
    fun progress(now: Long = System.currentTimeMillis()) = (max(0L, now - startedAt) % MS_PER_STOP) / MS_PER_STOP.toFloat()

    // target 역에 닿는 시각. 이미 지났거나 지금 역이면 다음 바퀴가 아니라 지금으로 본다
    fun arrivalAt(target: Int, now: Long = System.currentTimeMillis()): Long {
        val travelled = stopsTravelled(now)
        val ahead = Line2.stopsBetween(current(now), target, dir)
        return startedAt + (travelled + ahead) * MS_PER_STOP
    }

    companion object {
        const val MS_PER_STOP = 2 * 60 * 1000L  // 2호선 역간 평균 약 2분
        fun minutesFor(stops: Int) = stops * (MS_PER_STOP / 60_000).toInt()
    }
}

// 받침 유무에 따라 조사 고르기: 사당 → "사당으로", 교대 → "교대로"
fun String.josa(withBatchim: String, without: String): String {
    val c = lastOrNull() ?: return this + without
    if (c !in '가'..'힣') return this + without
    val jong = (c - '가') % 28
    // '으로/로'는 ㄹ 받침(8)도 '로'
    if (withBatchim == "으로" && jong == 8) return this + without
    return this + if (jong != 0) withBatchim else without
}
