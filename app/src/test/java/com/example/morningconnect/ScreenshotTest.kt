package com.example.morningconnect

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Looper
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/**
 * 화면을 실제 픽셀로 그려 build/screens 에 PNG 로 저장 (디자인 시안과 같은 390x844dp @2x).
 * 에뮬레이터 없이 레이아웃이 깨지지 않는지 확인하는 용도.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w390dp-h844dp-xhdpi")
class ScreenshotTest {
    private lateinit var context: Context
    private val out = File("build/screens").apply { mkdirs() }

    @Before
    fun setUp() {
        Store.resetForTest()
        context = ApplicationProvider.getApplicationContext()
        val store = Store.get(context)
        val now = System.currentTimeMillis()
        store.clock = TrainClock(Line2.indexOf("강남"), now - 10_000, Line2.OUTER)
        store.destination = Destination(Line2.indexOf("사당"), 2, store.clock.arrivalAt(Line2.indexOf("사당"), now))
    }

    private inline fun <reified T : Activity> shot(name: String, intent: Intent? = null, crossinline prepare: (T) -> Unit = {}) {
        val scenario = if (intent != null) ActivityScenario.launch<T>(intent) else ActivityScenario.launch(T::class.java)
        scenario.onActivity { a ->
            prepare(a)
            repeat(12) { shadowOf(Looper.getMainLooper()).idleFor(java.time.Duration.ofMillis(50)) }
            val v = a.window.decorView
            val bmp = Bitmap.createBitmap(v.width, v.height, Bitmap.Config.ARGB_8888)
            v.draw(Canvas(bmp))
            File(out, "$name.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
        }
        scenario.close()
    }

    @Test fun main() = shot<MainActivity>("01_main")
    @Test fun mainRotated() = shot<MainActivity>("01b_main_rotated") { it.findViewById<OrbitMenu>(R.id.menu).rotateBy(1) }
    @Test fun sendDown() = shot<SendDownActivity>("02_send_down")
    @Test fun receive() = shot<ReceiveActivity>("03_receive")

    @Test fun match() {
        val h = Handoff.create(Role.GIVER, Seat(0, 1, 1), 3, Line2.indexOf("사당"), true).apply {
            stage = Stage.MATCHED
            partner = Peer(Look.DEFAULT.with("상의", Item(3, "코트")).with("하의", Item(0, "바지")).with("가방", Item(0, "백팩")), 42, 43)
        }
        Store.get(context).handoff = h.toJson()
        shot<MatchActivity>("04_match")
    }

    @Test fun done() = shot<DoneActivity>("05_done")
    @Test fun destination() = shot<DestinationActivity>("06_destination")
    @Test fun look() = shot<LookActivity>("07_look")
    @Test fun photo() = shot<PhotoActivity>("09_photo")

    @Test fun news() {
        context.getSharedPreferences("news", Context.MODE_PRIVATE).edit().putString(
            "주요",
            org.json.JSONArray().apply {
                (1..5).forEach { put(org.json.JSONObject().put("t", "헤드라인 제목 $it — 출근길 지하철 2호선 소식이 두 줄까지 들어갑니다").put("s", "언론사").put("l", "https://example.com").put("d", System.currentTimeMillis() - it * 600_000L)) }
            }.toString()
        ).commit()
        shot<NewsActivity>("10_news")
    }

    @Test fun my() = shot<MyActivity>("12_my")
}
