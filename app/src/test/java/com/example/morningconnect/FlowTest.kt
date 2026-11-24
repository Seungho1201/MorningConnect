package com.example.morningconnect

import android.content.Context
import android.content.DialogInterface
import android.os.Looper
import android.widget.EditText
import android.widget.FrameLayout
import androidx.appcompat.app.AlertDialog
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowDialog
import java.time.Duration

// 자리 넘기기 전체 흐름 (상대방은 SimulatedSeatNetwork)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class FlowTest {
    private lateinit var context: Context
    private lateinit var store: Store
    private val sadang = Line2.indexOf("사당")

    @Before
    fun setUp() {
        Store.resetForTest()
        context = ApplicationProvider.getApplicationContext()
        store = Store.get(context)
        val now = System.currentTimeMillis()
        store.clock = TrainClock(Line2.indexOf("강남"), now, Line2.OUTER)
        store.destination = Destination(sadang, 2, store.clock.arrivalAt(sadang, now))
    }

    private fun idle(seconds: Long) = shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(seconds))
    private fun latestDialog() = ShadowDialog.getLatestDialog() as AlertDialog

    @Test
    fun giver_notifyMatchConfirmShowCertificate_earns100() {
        ActivityScenario.launch(SendDownActivity::class.java).onActivity { a ->
            a.findViewById<android.view.View>(R.id.notify).performClick()
            assertEquals(Stage.WAITING, Handoff.fromJson(store.handoff)!!.stage)
            idle(7)
            assertEquals(MatchActivity::class.java.name, shadowOf(a).nextStartedActivity.component!!.className)
        }
        val h = Handoff.fromJson(store.handoff)!!
        assertEquals(Stage.MATCHED, h.stage)
        assertTrue(h.partner != null)

        ActivityScenario.launch(MatchActivity::class.java).onActivity { a ->
            assertTrue(!a.findViewById<android.view.View>(R.id.cert).isEnabled)
            a.findViewById<android.view.View>(R.id.step2).performClick()
            latestDialog().getButton(DialogInterface.BUTTON_POSITIVE).performClick()
            idle(1)
            assertEquals(Stage.CONFIRMED, Handoff.fromJson(store.handoff)!!.stage)

            a.findViewById<android.view.View>(R.id.cert).performClick()
            idle(6)
            val next = shadowOf(a).nextStartedActivity
            assertEquals(DoneActivity::class.java.name, next.component!!.className)
            assertEquals(Role.GIVER.name, next.getStringExtra(DoneActivity.EXTRA_ROLE))
        }
        assertEquals(Store.WELCOME_POINTS + 100, store.points)
        assertEquals(1, store.seatsGiven)
        assertEquals(100, store.trust)
        assertNull(store.handoff)
    }

    @Test
    fun receiver_acceptWaitConfirmByNumber_pays50() {
        ActivityScenario.launch(ReceiveActivity::class.java).onActivity { a ->
            a.findViewById<android.view.View>(R.id.go).performClick()
            idle(2)
            assertEquals(MatchActivity::class.java.name, shadowOf(a).nextStartedActivity.component!!.className)
        }
        val h = Handoff.fromJson(store.handoff)!!
        assertEquals(Role.RECEIVER, h.role)

        ActivityScenario.launch(MatchActivity::class.java).onActivity { a ->
            idle(8) // 넘겨주는 사람의 하차 재확인
            assertEquals(Stage.CONFIRMED, Handoff.fromJson(store.handoff)!!.stage)
            assertTrue(a.findViewById<android.view.View>(R.id.cert).isEnabled)

            // 카메라 대신 번호로 확인: 틀린 번호는 거절
            a.findViewById<android.view.View>(R.id.certHint).performClick()
            var d = latestDialog()
            d.findEditText().setText("${(h.number % 89) + 10}")
            d.getButton(DialogInterface.BUTTON_POSITIVE).performClick()
            idle(1)
            assertEquals(Store.WELCOME_POINTS, store.points)

            a.findViewById<android.view.View>(R.id.certHint).performClick()
            d = latestDialog()
            d.findEditText().setText(h.number.toString())
            d.getButton(DialogInterface.BUTTON_POSITIVE).performClick()
            idle(1)
            assertEquals(DoneActivity::class.java.name, shadowOf(a).nextStartedActivity.component!!.className)
        }
        assertEquals(Store.WELCOME_POINTS - Store.TRANSFER, store.points)
        assertEquals(1, store.seatsReceived)
    }

    @Test
    fun cancelAfterMatch_lowersTrust() {
        store.handoff = Handoff.create(Role.GIVER, Seat(0, 1, 1), 3, sadang, false).apply {
            stage = Stage.MATCHED
            partner = Peer(Look.DEFAULT, 10, 10)
        }.toJson()
        ActivityScenario.launch(MatchActivity::class.java).onActivity { a ->
            a.findViewById<android.view.View>(R.id.cancel).performClick()
            latestDialog().getButton(DialogInterface.BUTTON_POSITIVE).performClick()
            idle(1)
        }
        assertEquals(0, store.trust) // 0/1
        assertEquals(1, store.promisesTotal)
        assertNull(store.handoff)
    }

    @Test
    fun cancelWhileWaiting_keepsTrust() {
        ActivityScenario.launch(SendDownActivity::class.java).onActivity { a ->
            a.findViewById<android.view.View>(R.id.notify).performClick()
            a.findViewById<android.view.View>(R.id.notify).performClick() // 알림 취소
            idle(7)
            assertNull(shadowOf(a).nextStartedActivity)
        }
        assertNull(store.handoff)
        assertEquals(0, store.promisesTotal)
    }

    @Test
    fun home_withoutDestination_opensDestination() {
        store.destination = null
        ActivityScenario.launch(MainActivity::class.java).onActivity { a ->
            a.findViewById<android.view.View>(R.id.center).performClick()
            assertEquals(DestinationActivity::class.java.name, shadowOf(a).nextStartedActivity.component!!.className)
        }
    }

    @Test
    fun home_menuButtonOpensMy() {
        ActivityScenario.launch(MainActivity::class.java).onActivity { a ->
            a.findViewById<android.view.View>(R.id.my).performClick()
            assertEquals(MyActivity::class.java.name, shadowOf(a).nextStartedActivity.component!!.className)
        }
    }

    @Test
    fun home_dragRotatesMenuAndSnaps_tapStillWorks() {
        ActivityScenario.launch(MainActivity::class.java).onActivity { a ->
            idle(1)
            val menu = a.findViewById<OrbitMenu>(R.id.menu)
            val cx = menu.width / 2f; val cy = menu.height / 2f; val r = 140 * a.resources.displayMetrics.density
            fun ev(action: Int, deg: Double, t: Long): android.view.MotionEvent {
                val rad = Math.toRadians(deg)
                return android.view.MotionEvent.obtain(0, t, action, (cx + r * Math.sin(rad)).toFloat(), (cy - r * Math.cos(rad)).toFloat(), 0)
            }
            // 오른쪽 아래 빈 원 위에서 시계 방향으로 약 60° 끌기
            menu.dispatchTouchEvent(ev(android.view.MotionEvent.ACTION_DOWN, 108.0, 0))
            for (i in 1..12) menu.dispatchTouchEvent(ev(android.view.MotionEvent.ACTION_MOVE, 108.0 + i * 5, i * 16L))
            assertTrue(menu.offset > 40f)
            menu.dispatchTouchEvent(ev(android.view.MotionEvent.ACTION_UP, 168.0, 13 * 16L))
            idle(1)
            assertEquals(0f, menu.offset % 72f, 0.01f) // 가까운 자리에 맞춰 멈춤
            assertTrue(menu.offset != 0f)

            // 돌린 뒤에도 버튼을 누르면 해당 화면으로
            a.findViewById<android.view.View>(R.id.look).performClick()
            assertEquals(LookActivity::class.java.name, shadowOf(a).nextStartedActivity.component!!.className)
        }
    }

    private fun AlertDialog.findEditText(): EditText {
        fun find(v: android.view.View): EditText? = when (v) {
            is EditText -> v
            is android.view.ViewGroup -> (0 until v.childCount).firstNotNullOfOrNull { find(v.getChildAt(it)) }
            else -> null
        }
        return find(window!!.decorView)!!
    }
}
