package com.example.morningconnect

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.InputType
import android.view.WindowManager
import android.widget.EditText
import android.widget.FrameLayout
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import com.example.morningconnect.databinding.ActivityMatchBinding
import com.example.morningconnect.databinding.DialogCertBinding
import com.example.morningconnect.databinding.ItemStepBinding
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.zxing.BarcodeFormat
import com.journeyapps.barcodescanner.BarcodeEncoder
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions

/**
 * 연결됐어요: 두 폰에 같은 색·번호를 띄워 서로 찾고,
 * 넘겨주는 사람은 하차 재확인 → 인증서(QR) 보여주기, 받는 사람은 인증서 스캔으로 인계를 확정한다.
 */
class MatchActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMatchBinding
    private lateinit var handoff: Handoff
    private val waits = mutableListOf<SeatNetwork.Cancel>()
    private var certDialog: AlertDialog? = null
    private var confirmAsked = false
    private val ticker = Handler(Looper.getMainLooper())
    private val tick = object : Runnable {
        override fun run() {
            checkArrival()
            ticker.postDelayed(this, 5_000)
        }
    }

    private val scan = registerForActivityResult(ScanContract()) { result ->
        val text = result.contents ?: return@registerForActivityResult
        if (handoff.verify(text)) complete() else toast("이 연결의 인증서가 아니에요. 번호와 색을 확인해 주세요")
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        handoff = Handoff.fromJson(store.handoff)?.takeIf { it.partner != null && it.stage in ACTIVE } ?: run {
            finish(); return
        }
        binding = ActivityMatchBinding.inflate(layoutInflater)
        setScreen(binding.root)
        // 서로 찾는 동안 화면이 꺼지지 않게
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        confirmAsked = savedInstanceState?.getBoolean("asked") ?: false

        binding.root.setBackgroundColor(handoff.color)
        binding.number.text = handoff.number.toString()
        binding.number.contentDescription = "번호 ${handoff.number}"
        val partner = handoff.partner!!
        binding.avatar.look = partner.look
        binding.partnerLook.text = partner.look.summary()
        binding.partnerSeat.text = if (handoff.role == Role.RECEIVER) {
            "${handoff.seat.doorLabel(handoff.car)} · ${handoff.seat.posLabel()}"
        } else "신뢰도 ${partner.trust} · 약속 이행 ${partner.kept}/${partner.total}회"
        binding.partnerRow.contentDescription = "상대방 인상착의: ${binding.partnerLook.text}. ${binding.partnerSeat.text}"

        binding.cancel.setOnClickListener { askCancel() }
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() = askCancel()
        })
        binding.cert.setOnClickListener { if (handoff.role == Role.GIVER) showCertificate() else startScan() }
        binding.certHint.setOnClickListener { if (handoff.role == Role.RECEIVER && handoff.stage == Stage.CONFIRMED) askNumber() }
        binding.step2.root.setOnClickListener { if (handoff.role == Role.GIVER && handoff.stage == Stage.MATCHED) askGetOff() }
        render()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putBoolean("asked", confirmAsked)
    }

    override fun onResume() {
        super.onResume()
        // 받는 사람: 넘겨주는 사람의 하차 재확인을 기다림
        if (handoff.role == Role.RECEIVER && handoff.stage == Stage.MATCHED) {
            waits += SeatNetwork.instance.awaitGiverConfirm(handoff) { setStage(Stage.CONFIRMED) }
        }
        if (handoff.role == Role.GIVER) tick.run()
    }

    override fun onPause() {
        waits.forEach { it.cancel() }; waits.clear()
        ticker.removeCallbacks(tick)
        certDialog?.dismiss()
        super.onPause()
    }

    private fun setStage(stage: Stage) {
        handoff.stage = stage
        store.handoff = handoff.toJson()
        render()
    }

    private fun render() {
        val giver = handoff.role == Role.GIVER
        val dest = Line2.at(handoff.destStation).name
        val confirmed = handoff.stage == Stage.CONFIRMED
        if (giver) {
            step(binding.step1, 1, "상대가 자리 앞으로 오는 중", done = true, now = false)
            step(binding.step2, 2, "$dest 도착 직전 하차 재확인", done = confirmed, now = !confirmed,
                action = if (!confirmed) "지금 확인" else null)
            step(binding.step3, 3, "일어나며 인증서 보여주기 → 포인트 이동", done = false, now = confirmed)
            binding.cert.text = "인증서 보여주기"
            binding.certHint.text = if (confirmed) "상대가 인증서를 스캔하면 인계가 확정돼요" else "하차 재확인을 하면 인증서를 보여줄 수 있어요"
        } else {
            step(binding.step1, 1, "${handoff.seat.doorLabel(handoff.car)} 자리 앞으로 가기", done = true, now = false)
            step(binding.step2, 2, if (confirmed) "상대가 $dest 하차를 확인했어요" else "상대의 하차 재확인을 기다리는 중",
                done = confirmed, now = !confirmed)
            step(binding.step3, 3, "인증서 스캔하고 앉기 → ${Store.TRANSFER}P 이동", done = false, now = confirmed)
            binding.cert.text = "인증서 스캔하기"
            binding.certHint.text = if (confirmed) "스캔이 안 되나요? 번호로 확인하기" else "상대가 하차를 확인하면 스캔할 수 있어요"
        }
        binding.cert.isEnabled = confirmed
    }

    private fun step(b: ItemStepBinding, n: Int, label: String, done: Boolean, now: Boolean, action: String? = null) {
        b.num.text = n.toString()
        b.num.setBackgroundResource(if (now) R.drawable.step_now else R.drawable.step_todo)
        b.num.setTextColor(getColor(if (now) R.color.ink else R.color.hint))
        b.check.isVisible = done
        b.text.text = label
        b.text.setTextColor(getColor(if (done || now) R.color.ink else R.color.sub))
        b.action.text = action
        b.action.isVisible = action != null
        b.root.isClickable = action != null
        b.root.contentDescription = "${n}단계, $label, " + when { done -> "완료"; now -> "진행 중"; else -> "대기" } +
            (action?.let { ", 눌러서 $it" } ?: "")
    }

    // 넘겨주는 사람: 하차역 1정거장 전에 자동으로 하차 재확인
    private fun checkArrival() {
        if (handoff.stage != Stage.MATCHED || confirmAsked) return
        val left = store.stopsLeft() ?: return
        if (left <= 1) askGetOff()
    }

    private fun askGetOff() {
        confirmAsked = true
        val dest = Line2.at(handoff.destStation).name
        MaterialAlertDialogBuilder(this)
            .setTitle("곧 ${dest.josa("이에요", "예요")}")
            .setMessage("${dest}에서 내리시나요? 확인하면 상대에게 알려요.")
            .setPositiveButton("네, 내려요") { _, _ -> setStage(Stage.CONFIRMED) }
            .setNegativeButton("아니요") { _, _ -> askCancel() }
            .show()
    }

    private fun showCertificate() {
        val d = DialogCertBinding.inflate(layoutInflater)
        d.sub.text = "번호 ${handoff.number} · ${handoff.seat.doorLabel(handoff.car)}"
        d.qr.setImageBitmap(BarcodeEncoder().encodeBitmap(handoff.certificate(), BarcodeFormat.QR_CODE, 720, 720))
        // 스캔이 잘 되게 화면을 가장 밝게
        val lp = window.attributes
        val oldBrightness = lp.screenBrightness
        window.attributes = lp.apply { screenBrightness = 1f }
        var wait: SeatNetwork.Cancel? = null
        certDialog = MaterialAlertDialogBuilder(this)
            .setView(d.root)
            .setNegativeButton("닫기", null)
            .setOnDismissListener {
                wait?.cancel()
                window.attributes = window.attributes.apply { screenBrightness = oldBrightness }
            }
            .show()
        wait = SeatNetwork.instance.awaitScan(handoff) {
            certDialog?.setOnDismissListener(null)
            window.attributes = window.attributes.apply { screenBrightness = oldBrightness }
            certDialog?.dismiss()
            complete()
        }
    }

    private fun startScan() {
        scan.launch(
            ScanOptions()
                .setDesiredBarcodeFormats(ScanOptions.QR_CODE)
                .setPrompt("상대 폰의 인증서 QR을 비춰 주세요")
                .setBeepEnabled(false)
                .setOrientationLocked(false)
        )
    }

    // 카메라를 못 쓸 때: 상대 화면의 번호로 확인
    private fun askNumber() {
        val input = EditText(this).apply {
            inputType = InputType.TYPE_CLASS_NUMBER
            hint = "상대 화면의 번호"
            contentDescription = "상대 화면의 번호"
        }
        val box = FrameLayout(this).apply { setPadding(24.dp, 8.dp, 24.dp, 0); addView(input) }
        MaterialAlertDialogBuilder(this)
            .setTitle("번호로 확인")
            .setMessage("상대 폰에 크게 떠 있는 번호를 입력해 주세요")
            .setView(box)
            .setPositiveButton("확인") { _, _ ->
                if (handoff.verifyNumber(input.text.toString())) complete() else toast("번호가 달라요. 같은 색 화면인지 확인해 주세요")
            }
            .setNegativeButton("취소", null)
            .show()
    }

    private fun askCancel() {
        MaterialAlertDialogBuilder(this)
            .setTitle("연결을 취소할까요?")
            .setMessage("약속을 지키지 않은 것으로 기록되어 신뢰도가 내려가요.")
            .setPositiveButton("취소하기") { _, _ ->
                store.breakPromise()
                handoff.stage = Stage.CANCELLED
                store.handoff = null
                toast("연결을 취소했어요")
                goHome()
                finish()
            }
            .setNegativeButton("계속하기", null)
            .show()
    }

    // 인계 확정: 포인트 이동 + 약속 이행 기록
    private fun complete() {
        if (handoff.stage == Stage.DONE) return
        val store = store
        if (handoff.role == Role.GIVER) {
            store.addPoints(Store.BASE_REWARD, "자리 넘겨주기 · 기본 지급")
            store.addPoints(Store.TRANSFER, "자리 넘겨주기 · 상대에게서 이동")
            store.keepPromise(gave = true)
        } else {
            store.addPoints(-Store.TRANSFER, "자리 받기 · 상대에게 이동")
            store.keepPromise(gave = false)
        }
        handoff.stage = Stage.DONE
        store.handoff = null
        open<DoneActivity> {
            putExtra(DoneActivity.EXTRA_ROLE, handoff.role.name)
            putExtra(DoneActivity.EXTRA_STATION, handoff.destStation)
        }
        finish()
    }

    companion object {
        val ACTIVE = listOf(Stage.MATCHED, Stage.CONFIRMED)
    }
}
