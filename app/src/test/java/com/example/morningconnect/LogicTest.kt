package com.example.morningconnect

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class LogicTest {
    private lateinit var context: Context
    private val gangnam = Line2.indexOf("강남")
    private val sadang = Line2.indexOf("사당")

    @Before
    fun setUp() {
        Store.resetForTest()
        context = ApplicationProvider.getApplicationContext()
    }

    @Test
    fun line2_outerLoopGoesGangnamToSadangInFourStops() {
        assertEquals(43, Line2.size)
        assertEquals(4, Line2.stopsBetween(gangnam, sadang, Line2.OUTER))
        assertEquals("교대", Line2.at(Line2.step(gangnam, 1, Line2.OUTER)).name)
        assertEquals("역삼", Line2.at(Line2.step(gangnam, -1, Line2.OUTER)).name)
        // 내선은 반대 방향으로 한 바퀴 가까이 돈다
        assertEquals(39, Line2.stopsBetween(gangnam, sadang, Line2.INNER))
        // 순환선 끝(충정로)에서 시청으로 넘어감
        assertEquals("시청", Line2.at(Line2.step(Line2.indexOf("충정로"), 1, Line2.OUTER)).name)
    }

    @Test
    fun josa_picksParticleByFinalConsonant() {
        assertEquals("사당으로", "사당".josa("으로", "로"))
        assertEquals("교대로", "교대".josa("으로", "로"))
        assertEquals("신림으로", "신림".josa("으로", "로"))
        assertEquals("을지로4가로", "을지로4가".josa("으로", "로"))
        assertEquals("500P가", "500P".josa("이", "가"))
    }

    @Test
    fun trainClock_movesOneStationPerInterval() {
        val start = 1_000_000L
        val clock = TrainClock(gangnam, start, Line2.OUTER)
        assertEquals(gangnam, clock.current(start))
        assertEquals("교대", Line2.at(clock.current(start + TrainClock.MS_PER_STOP)).name)
        assertEquals(sadang, clock.current(start + 4 * TrainClock.MS_PER_STOP + 1))
        assertEquals(start + 4 * TrainClock.MS_PER_STOP, clock.arrivalAt(sadang, start))
        assertEquals(0.5f, clock.progress(start + TrainClock.MS_PER_STOP / 2), 0.001f)
        assertEquals(8, TrainClock.minutesFor(4))
    }

    @Test
    fun store_pointsTrustAndDestination() {
        val store = Store.get(context)
        assertEquals(Store.WELCOME_POINTS, store.points)
        assertEquals(100, store.trust)

        store.addPoints(100, "test")
        assertEquals(Store.WELCOME_POINTS + 100, store.points)
        assertEquals("test", store.history().first().label)

        repeat(42) { store.keepPromise(gave = true) }
        store.breakPromise()
        assertEquals(98, store.trust) // 42/43
        assertEquals(42, store.seatsGiven)

        val now = System.currentTimeMillis()
        store.clock = TrainClock(gangnam, now, Line2.OUTER)
        store.destination = Destination(sadang, 2, store.clock.arrivalAt(sadang, now))
        assertEquals(4, store.stopsLeft(now))
        // 도착 후 잠시 지나면 하차역은 지워진다
        assertNull(store.stopsLeft(now + 4 * TrainClock.MS_PER_STOP + Store.ARRIVED_GRACE_MS))
        assertNull(store.destination)
    }

    @Test
    fun look_summaryAndJsonRoundTrip() {
        assertEquals("검정 패딩 · 청바지 · 흰 운동화", Look.DEFAULT.summary())
        val look = Look.DEFAULT.with("가방", Item(3, "백팩")).with("상의", Item(3, "코트"))
        assertEquals("베이지 코트 · 청바지 · 베이지 백팩 · 흰 운동화", look.summary())
        assertEquals(look, Look.fromJson(look.toJson()))
    }

    @Test
    fun handoff_certificateVerifiesOnlyForSameSession() {
        val h = Handoff.create(Role.GIVER, Seat(0, 1, 1), 3, sadang, true)
        assertTrue(h.verify(h.certificate()))
        assertTrue(h.number in 10..99)
        assertTrue(h.verifyNumber(h.number.toString()))

        val other = Handoff.create(Role.GIVER, Seat(0, 1, 1), 3, sadang, true)
        assertFalse(h.verify(other.certificate()))
        // 번호를 바꾼 위조 QR
        val parts = h.certificate().split(":")
        assertFalse(h.verify("MC1:${parts[1]}:${(h.number % 89) + 10}:${parts[3]}"))
        assertFalse(h.verify("hello"))

        val restored = Handoff.fromJson(h.toJson())!!
        assertTrue(restored.verify(h.certificate()))
        assertEquals("3-2 문 오른쪽", h.seat.doorLabel(3))
        assertEquals("끝에서 두 번째 자리", h.seat.posLabel())
    }

    @Test
    fun news_parsesGoogleRss() {
        val rss = """
            <?xml version="1.0" encoding="UTF-8"?>
            <rss version="2.0"><channel><title>Google 뉴스</title>
              <item>
                <title>지하철 2호선 출근길 정상 운행 - 연합뉴스</title>
                <link>https://news.google.com/a</link>
                <pubDate>Tue, 24 Nov 2026 00:10:00 GMT</pubDate>
                <source url="https://www.yna.co.kr">연합뉴스</source>
              </item>
              <item>
                <title>오늘 날씨 맑음</title>
                <link>https://news.google.com/b</link>
                <pubDate>bad date</pubDate>
              </item>
            </channel></rss>
        """.trimIndent()
        val list = NewsRepo.parse(rss.byteInputStream())
        assertEquals(2, list.size)
        assertEquals("지하철 2호선 출근길 정상 운행", list[0].title)
        assertEquals("연합뉴스", list[0].source)
        assertTrue(list[0].publishedAt > 0)
        assertEquals(0L, list[1].publishedAt)
        assertEquals("12분 전", NewsRepo.ago(1_000_000L, 1_000_000L + 12 * 60_000))
    }
}
