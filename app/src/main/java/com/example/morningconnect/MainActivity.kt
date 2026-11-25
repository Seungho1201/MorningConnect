package com.example.morningconnect

import android.bluetooth.BluetoothAdapter
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import com.example.morningconnect.databinding.ActivityMainBinding
import com.google.android.material.dialog.MaterialAlertDialogBuilder

// 홈 (고정 화면): 왼쪽 위 메뉴(마이), 지금 역, 하차역, 돌릴 수 있는 원형 메뉴
class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding
    private val ticker = Handler(Looper.getMainLooper())
    private val tick = object : Runnable {
        override fun run() {
            render()
            ticker.postDelayed(this, 2_000)
        }
    }

    // 같은 칸 찾기: 권한 → 블루투스 켜기 → 서비스 시작
    private val blePermission = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        if (SeatService.missingPermissions(this).isEmpty()) startBle() else toast("블루투스 권한이 있어야 같은 칸 사람을 찾을 수 있어요")
    }
    private val enableBt = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        if (SeatService.start(this)) render()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setScreen(binding.root)

        with(binding) {
            dest.setOnClickListener { open<DestinationActivity>() }
            center.setOnClickListener { onSendDown() }
            look.setOnClickListener { open<LookActivity>() }
            photo.setOnClickListener { open<PhotoActivity>() }
            news.setOnClickListener { open<NewsActivity>() }
            // 오픈톡·미니게임은 이번 구현 범위에서 제외
            listOf(talk, game).forEach { it.setOnClickListener { toast("준비 중인 기능이에요") } }
            my.setOnClickListener { open<MyActivity>() }
            line.setOnClickListener { askDirection() }
            carCount.setOnClickListener { if (SeatService.running) toast("블루투스로 같은 칸 사람을 찾고 있어요") else askBle() }
            menu.setItems(listOf(look, talk, game, news, photo))
            // 드래그가 어려운 경우(화면 읽기 등)를 위한 돌리기 동작
            ViewCompat.addAccessibilityAction(menu, "메뉴를 시계 방향으로 돌리기") { _, _ -> menu.rotateBy(1); true }
            ViewCompat.addAccessibilityAction(menu, "메뉴를 반시계 방향으로 돌리기") { _, _ -> menu.rotateBy(-1); true }
            menuBox.addOnLayoutChangeListener { _, l, t, r, b, ol, ot, or, ob ->
                if (r - l != or - ol || b - t != ob - ot) fitMenu(r - l, b - t)
            }
        }
        if (savedInstanceState == null && SeatService.hasBle(this)) startBle()
    }

    // 원형 메뉴(372x372dp)를 남는 공간(폭·높이)에 맞게 줄임. 고정 화면이라 작은 폰에서도 스크롤 없이 다 보이게
    private fun fitMenu(width: Int, height: Int) {
        if (width <= 0 || height <= 0) return
        val scale = minOf(1f, (width - 4.dp).toFloat() / 372.dp, height.toFloat() / 372.dp)
        binding.menu.scaleX = scale
        binding.menu.scaleY = scale
    }

    override fun onResume() {
        super.onResume()
        SeatService.start(this)  // 알림에서 껐어도 앱을 다시 열면 켬 (권한·블루투스가 준비된 경우만)
        tick.run()
    }

    override fun onPause() {
        ticker.removeCallbacks(tick)
        super.onPause()
    }

    private fun render() {
        val store = store
        val clock = store.clock
        val now = System.currentTimeMillis()
        val cur = clock.current(now)
        with(binding) {
            lineName.text = "호선 ${Line2.dirName(clock.dir)}"
            val nearby = (SeatNetwork.instance as? BleSeatNetwork)?.nearby().orEmpty()
            carCount.text = if (SeatService.running) "같은 칸 ${nearby.size}명" else "블루투스 꺼짐"
            menu.nearby = nearby

            val prev = Line2.at(Line2.step(cur, -1, clock.dir))
            val here = Line2.at(cur)
            val next = Line2.at(Line2.step(cur, 1, clock.dir))
            prevName.text = prev.name; prevRoman.text = prev.roman
            nowName.text = here.name; nowRoman.text = here.roman
            nextName.text = next.name; nextRoman.text = next.roman

            // 다음 역까지 얼마나 갔는지 (지금 역 점과 다음 역 점 사이)
            val span = content.width * 0.3f
            train.translationX = 17.dp + (span - 34.dp) * clock.progress(now) - 5.dp
            train.visibility = if (content.width == 0) View.INVISIBLE else View.VISIBLE

            val left = store.stopsLeft(now)
            val d = store.destination
            if (left == null || d == null) {
                destTitle.text = "하차역을 정해 주세요"
                destLeft.text = "알림 받기"
            } else {
                destTitle.text = "${Line2.at(d.station).name}에서 내려요"
                destLeft.text = if (left == 0) "지금 내리세요" else "${left}정거장 남음"
            }
            dest.contentDescription = "${destTitle.text}, ${destLeft.text}"

            my.contentDescription = "메뉴, 마이 페이지. 내 포인트 ${store.points.points()}, 신뢰도 ${store.trust}"
        }
    }

    // 곧 내려요: 하차역이 있어야 같은 칸에 알릴 수 있음. 진행 중인 인계가 있으면 그 화면으로
    private fun onSendDown() {
        val h = Handoff.fromJson(store.handoff)
        if (h != null && h.stage in listOf(Stage.MATCHED, Stage.CONFIRMED)) {
            open<MatchActivity>()
            return
        }
        if (store.stopsLeft() == null) {
            toast("먼저 내릴 역을 정해 주세요")
            open<DestinationActivity>()
            return
        }
        open<SendDownActivity>()
    }

    private fun askDirection() {
        val clock = store.clock
        val other = -clock.dir
        MaterialAlertDialogBuilder(this)
            .setTitle("${Line2.dirName(other)}으로 바꿀까요?")
            .setMessage("타고 있는 열차 방향을 바꾸면 하차역 설정이 지워져요.")
            .setPositiveButton("바꾸기") { _, _ ->
                store.clock = TrainClock(clock.current(), System.currentTimeMillis(), other)
                store.destination = null
                ArrivalAlarm.cancel(this)
                render()
            }
            .setNegativeButton("취소", null)
            .show()
    }

    private fun startBle() {
        val missing = SeatService.missingPermissions(this)
        when {
            missing.isNotEmpty() -> blePermission.launch((missing + SeatService.permissions).distinct().toTypedArray())
            !SeatService.bluetoothOn(this) -> enableBt.launch(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE))
            else -> { SeatService.start(this); render() }
        }
    }

    // 블루투스를 못 쓰는 동안: 켜기, 또는 같은 칸 사람이 보낸 것처럼 체험
    private fun askBle() {
        val dialog = MaterialAlertDialogBuilder(this)
            .setTitle("같은 칸 사람 찾기")
            .setMessage("블루투스로 가까운 사람을 찾아 자리 알림을 주고받아요. 앱을 닫아도 알림을 받을 수 있어요.\n\n블루투스 없이 받는 쪽 화면을 체험해 볼 수도 있어요.")
            .setNeutralButton("체험해 보기") { _, _ -> open<ReceiveActivity>() }
            .setNegativeButton("닫기", null)
        if (SeatService.hasBle(this)) dialog.setPositiveButton("블루투스 켜기") { _, _ -> startBle() }
        dialog.show()
    }
}
