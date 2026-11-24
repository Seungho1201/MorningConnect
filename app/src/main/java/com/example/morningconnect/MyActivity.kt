package com.example.morningconnect

import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.example.morningconnect.databinding.ActivityMyBinding
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.zxing.BarcodeFormat
import com.journeyapps.barcodescanner.BarcodeEncoder
import java.security.SecureRandom
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class Gift(val name: String, val price: Int, val icon: Int)

// 마이: 포인트·신뢰도, 기프티콘 교환, 임산부 인증, 오늘의 룩
class MyActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMyBinding

    private val pickProof = registerForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri == null) return@registerForActivityResult
        // 실서비스에서는 서류를 서버에서 확인한 뒤 인증 표시를 붙인다. 지금은 서류를 고르면 기기에 인증으로 기록
        store.pregnantVerified = true
        toast("임산부 인증을 마쳤어요")
        render()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMyBinding.inflate(layoutInflater)
        setScreen(binding.root)

        binding.header.set("마이") { finish() }
        binding.history.setOnClickListener { showHistory() }
        binding.allGifts.setOnClickListener { showAllGifts() }
        binding.pregnant.setOnClickListener { onPregnant() }
        binding.look.setOnClickListener { open<LookActivity>() }
        binding.coupons.setOnClickListener { showCoupons() }

        GIFTS.take(3).forEachIndexed { i, g ->
            binding.gifts.addView(giftView(g), LinearLayout.LayoutParams(0, -2, 1f).apply { if (i > 0) marginStart = 8.dp })
        }
    }

    override fun onResume() {
        super.onResume()
        render()
    }

    private fun render() {
        val s = store
        with(binding) {
            points.text = s.points.points()
            trust.text = "${s.trust} / 100"
            trustBar.setProgressCompat(s.trust, false)
            trustBar.contentDescription = "신뢰도 ${s.trust}점"
            promises.text = "약속 이행 ${s.promisesKept}/${s.promisesTotal}회 · 받은 칭찬 ${s.compliments}개"
            given.text = s.seatsGiven.toString()
            received.text = s.seatsReceived.toString()
            month.text = s.earnedThisMonth().points(sign = true)
            givenBox.contentDescription = "넘겨준 자리 ${s.seatsGiven}"
            receivedBox.contentDescription = "받은 자리 ${s.seatsReceived}"
            monthBox.contentDescription = "이번 달 ${month.text}"
            pregnantTitle.text = if (s.pregnantVerified) "임산부 인증 완료" else "임산부 인증"
            pregnantSub.text = if (s.pregnantVerified) "자리 알림을 먼저 받고 있어요" else "인증 시 자리 알림을 먼저 받아요"
            couponsTitle.text = "내 쿠폰함 ${s.coupons().size}"
        }
    }

    private fun giftView(g: Gift) = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        background = getDrawable(android.R.drawable.list_selector_background)
        contentDescription = "${g.name}, ${g.price.points()}, 교환하기"
        isClickable = true
        isFocusable = true
        setOnClickListener { exchange(g) }
        addView(ImageView(context).apply {
            setImageResource(g.icon)
            setColorFilter(getColor(R.color.sub))
            scaleType = ImageView.ScaleType.CENTER_INSIDE
            setPadding(30.dp, 30.dp, 30.dp, 30.dp)
            background = getDrawable(R.drawable.box_chip)
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        }, LinearLayout.LayoutParams(-1, 100.dp))
        addView(TextView(context).apply { text = g.name; textSize = 14f; maxLines = 1; setPadding(0, 8.dp, 0, 0) })
        addView(TextView(context).apply {
            text = g.price.points(); textSize = 14f; setTypeface(null, android.graphics.Typeface.BOLD)
            setTextColor(getColor(R.color.green_text)); setPadding(0, 2.dp, 0, 0)
        })
    }

    private fun exchange(g: Gift) {
        val have = store.points
        if (have < g.price) {
            MaterialAlertDialogBuilder(this)
                .setTitle("포인트가 부족해요")
                .setMessage("${g.name.josa("은", "는")} ${g.price.points().josa("이", "가")} 필요해요.\n${(g.price - have).points()} 더 모으면 교환할 수 있어요.")
                .setPositiveButton("확인", null)
                .show()
            return
        }
        MaterialAlertDialogBuilder(this)
            .setTitle("${g.name.josa("으로", "로")} 바꿀까요?")
            .setMessage("${g.price.points().josa("이", "가")} 차감되고, 남는 포인트는 ${(have - g.price).points()}예요.")
            .setPositiveButton("교환하기") { _, _ ->
                val code = newCode()
                store.addPoints(-g.price, "기프티콘 교환 · ${g.name}")
                store.addCoupon(g.name, code)
                render()
                showCoupon(g.name, code)
            }
            .setNegativeButton("취소", null)
            .show()
    }

    private fun newCode(): String {
        val r = SecureRandom()
        val chars = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
        return (0 until 3).joinToString("-") { (0 until 4).map { chars[r.nextInt(chars.length)] }.joinToString("") }
    }

    private fun showCoupon(name: String, code: String) {
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(24.dp, 8.dp, 24.dp, 0)
            addView(ImageView(context).apply {
                setImageBitmap(BarcodeEncoder().encodeBitmap(code, BarcodeFormat.CODE_128, 900, 240))
                contentDescription = "쿠폰 바코드"
            }, LinearLayout.LayoutParams(-1, 90.dp))
            addView(TextView(context).apply {
                text = code; textSize = 20f; gravity = Gravity.CENTER; setTypeface(android.graphics.Typeface.MONOSPACE, android.graphics.Typeface.BOLD)
                setPadding(0, 12.dp, 0, 0); setTextIsSelectable(true)
            })
        }
        MaterialAlertDialogBuilder(this)
            .setTitle(name)
            .setView(box)
            .setPositiveButton("확인", null)
            .show()
    }

    private fun showAllGifts() {
        val labels = GIFTS.map { "${it.name} · ${it.price.points()}" }.toTypedArray()
        MaterialAlertDialogBuilder(this)
            .setTitle("기프티콘 교환 (내 포인트 ${store.points.points()})")
            .setItems(labels) { _, which -> exchange(GIFTS[which]) }
            .setNegativeButton("닫기", null)
            .show()
    }

    private fun showCoupons() {
        val list = store.coupons()
        if (list.isEmpty()) { toast("아직 교환한 쿠폰이 없어요"); return }
        val labels = list.map { "${it.getString("name")}\n${it.getString("code")}" }.toTypedArray()
        MaterialAlertDialogBuilder(this)
            .setTitle("내 쿠폰함")
            .setItems(labels) { _, which -> showCoupon(list[which].getString("name"), list[which].getString("code")) }
            .setNegativeButton("닫기", null)
            .show()
    }

    private fun showHistory() {
        val fmt = SimpleDateFormat("M월 d일 HH:mm", Locale.KOREA)
        val rows = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(24.dp, 0, 24.dp, 0) }
        store.history().forEach { e ->
            rows.addView(LinearLayout(this).apply {
                gravity = Gravity.CENTER_VERTICAL
                setPadding(0, 10.dp, 0, 10.dp)
                isFocusable = true
                contentDescription = "${e.label}, ${fmt.format(Date(e.at))}, ${e.amount.points(sign = true)}"
                addView(LinearLayout(context).apply {
                    orientation = LinearLayout.VERTICAL
                    addView(TextView(context).apply { text = e.label; textSize = 15f })
                    addView(TextView(context).apply { text = fmt.format(Date(e.at)); textSize = 12f; setTextColor(getColor(R.color.sub)) })
                }, LinearLayout.LayoutParams(0, -2, 1f))
                addView(TextView(context).apply {
                    text = e.amount.points(sign = true); textSize = 15f; setTypeface(null, android.graphics.Typeface.BOLD)
                    setTextColor(getColor(if (e.amount > 0) R.color.green_text else R.color.ink))
                })
            })
        }
        MaterialAlertDialogBuilder(this)
            .setTitle("포인트 내역")
            .setView(ScrollView(this).apply { addView(rows) })
            .setPositiveButton("닫기", null)
            .show()
    }

    private fun onPregnant() {
        if (store.pregnantVerified) {
            MaterialAlertDialogBuilder(this)
                .setTitle("임산부 인증 완료")
                .setMessage("같은 칸에서 자리가 나면 먼저 알려 드려요.")
                .setPositiveButton("확인", null)
                .setNeutralButton("인증 해제") { _, _ -> store.pregnantVerified = false; render() }
                .show()
            return
        }
        MaterialAlertDialogBuilder(this)
            .setTitle("임산부 인증")
            .setMessage("임산부 배지나 산모수첩 사진을 올려 주세요.\n인증하면 같은 칸에서 자리가 날 때 먼저 알림을 받아요.")
            .setPositiveButton("사진 고르기") { _, _ ->
                pickProof.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
            }
            .setNegativeButton("취소", null)
            .show()
    }

    companion object {
        val GIFTS = listOf(
            Gift("커피 쿠폰", 500, R.drawable.ic_coffee),
            Gift("편의점 상품권", 1_000, R.drawable.ic_store),
            Gift("교통카드 충전", 3_000, R.drawable.ic_card),
            Gift("베이커리 교환권", 1_500, R.drawable.ic_cake),
            Gift("영화 관람권", 9_000, R.drawable.ic_ticket),
        )
    }
}
