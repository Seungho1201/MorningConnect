package com.example.morningconnect

import android.app.Activity
import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Looper
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

// 여러 화면 크기·글자 크기에서 그려 보기 (build/sizes)
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
class SizeTest {
    private val out = File("build/sizes").apply { mkdirs() }

    @Before
    fun setUp() {
        Store.resetForTest()
        val store = Store.get(ApplicationProvider.getApplicationContext())
        val now = System.currentTimeMillis()
        store.clock = TrainClock(Line2.indexOf("강남"), now - 10_000, Line2.OUTER)
        store.destination = Destination(Line2.indexOf("사당"), 2, store.clock.arrivalAt(Line2.indexOf("사당"), now))
        store.handoff = Handoff.create(Role.GIVER, Seat(0, 1, 1), 3, Line2.indexOf("사당"), true).apply {
            stage = Stage.MATCHED; partner = Peer(Look.DEFAULT, 42, 43)
        }.toJson()
    }

    private fun all(tag: String) {
        shot<MainActivity>("$tag-main"); shot<SendDownActivity>("$tag-send"); shot<ReceiveActivity>("$tag-receive")
        shot<MatchActivity>("$tag-match"); shot<DestinationActivity>("$tag-dest"); shot<LookActivity>("$tag-look")
        shot<MyActivity>("$tag-my"); shot<DoneActivity>("$tag-done"); shot<NewsActivity>("$tag-news"); shot<PhotoActivity>("$tag-photo")
    }

    private inline fun <reified T : Activity> shot(name: String) {
        val s = ActivityScenario.launch(T::class.java)
        s.onActivity { a ->
            repeat(3) { shadowOf(Looper.getMainLooper()).idleFor(java.time.Duration.ofMillis(50)) }
            val v = a.window.decorView
            val bmp = Bitmap.createBitmap(v.width, v.height, Bitmap.Config.ARGB_8888)
            v.draw(Canvas(bmp))
            File(out, "$name.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
        }
        s.close()
    }

    @Test @Config(qualifiers = "w320dp-h568dp-xhdpi") fun small() = all("a_320x568")
    @Test @Config(qualifiers = "w360dp-h640dp-xhdpi") fun compact() = all("b_360x640")
    @Test @Config(qualifiers = "w412dp-h915dp-xhdpi") fun large() = all("c_412x915")
    @Test @Config(qualifiers = "w360dp-h780dp-xhdpi") fun bigFont() { RuntimeEnvironment.setFontScale(1.3f); all("d_360_font130") }
}
