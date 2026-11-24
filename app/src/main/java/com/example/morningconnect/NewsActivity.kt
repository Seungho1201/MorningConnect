package com.example.morningconnect

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.children
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import com.example.morningconnect.databinding.ActivityNewsBinding
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

// 헤드라인 뉴스: 분야별 주요 기사 + 이어폰으로 듣기 (하차역 알림이 오면 멈춤)
class NewsActivity : AppCompatActivity() {
    private lateinit var binding: ActivityNewsBinding
    private lateinit var repo: NewsRepo
    private var category = NewsRepo.CATEGORIES.first()
    private var items = listOf<Headline>()
    private var job: Job? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityNewsBinding.inflate(layoutInflater)
        setScreen(binding.root)
        repo = NewsRepo(this)
        category = savedInstanceState?.getString("category") ?: category

        binding.header.set("헤드라인 뉴스", "") { finish() }
        binding.header.right.apply {
            setBackgroundResource(R.drawable.pill)
            setPadding(12.dp, 8.dp, 12.dp, 8.dp)
            setTextColor(getColor(R.color.green_text))
            setTypeface(null, android.graphics.Typeface.BOLD)
            maxLines = 1
            textSize = 13f
            // 좁은 화면·큰 글씨에서는 알약 글자를 줄여 한 줄 유지
            androidx.core.widget.TextViewCompat.setAutoSizeTextTypeUniformWithConfiguration(
                this, 10, 13, 1, android.util.TypedValue.COMPLEX_UNIT_SP)
        }

        NewsRepo.CATEGORIES.forEach { cat ->
            binding.tabs.addView(TextView(this, null, 0, R.style.Pill).apply {
                text = cat
                textSize = 16f
                minWidth = 56.dp
                setOnClickListener { if (category != cat) { category = cat; load() } }
            }, LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, 44.dp).apply { marginEnd = 8.dp })
        }
        binding.refresh.setColorSchemeColors(getColor(R.color.green_dark))
        binding.refresh.setOnRefreshListener { load(force = true) }
        binding.listen.setOnClickListener { toggleListen() }
        binding.top.clipToOutline = true
        load()
    }

    override fun onResume() {
        super.onResume()
        renderTimer()
        renderListen()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putString("category", category)
    }

    private fun renderTimer() {
        val left = store.stopsLeft()
        val d = store.destination
        binding.header.right.isVisible = left != null && d != null
        if (left != null && d != null) {
            binding.header.right.text = "${Line2.at(d.station).name}까지 ${TrainClock.minutesFor(left)}분 분량"
        }
    }

    private fun load(force: Boolean = false) {
        binding.tabs.children.forEach { (it as TextView).pillOn(it.text == category) }
        val cached = repo.cached(category)
        if (!force) show(cached)
        job?.cancel()
        binding.refresh.isRefreshing = true
        val cat = category
        job = lifecycleScope.launch {
            val result = runCatching { repo.fetch(cat) }
            if (cat != category) return@launch
            binding.refresh.isRefreshing = false
            result.onSuccess { show(it) }.onFailure {
                if (items.isEmpty()) show(emptyList(), "뉴스를 불러오지 못했어요\n신호가 잡히면 아래로 당겨 다시 불러와 주세요")
                else toast("새 뉴스를 불러오지 못해 저장된 뉴스를 보여 줘요")
            }
        }
    }

    private fun show(list: List<Headline>, error: String? = null) {
        items = list
        binding.status.isVisible = list.isEmpty()
        binding.status.text = error ?: "불러오는 중…"
        binding.top.isVisible = list.isNotEmpty()
        binding.list.isVisible = list.size > 1
        binding.list.removeAllViews()
        val first = list.firstOrNull() ?: return

        binding.topImage.text = first.source
        binding.topTitle.text = first.title
        binding.topMeta.text = meta(first)
        binding.top.contentDescription = "1위, ${first.title}, ${meta(first)}"
        binding.top.setOnClickListener { openArticle(first) }

        list.drop(1).take(9).forEachIndexed { i, h ->
            if (i > 0) binding.list.addView(View(this).apply { setBackgroundColor(getColor(R.color.divider)) },
                LinearLayout.LayoutParams(-1, 1.dp))
            binding.list.addView(row(i + 2, h))
        }
    }

    private fun meta(h: Headline) = listOf(h.source, NewsRepo.ago(h.publishedAt)).filter { it.isNotBlank() }.joinToString(" · ")

    private fun row(rank: Int, h: Headline) = LinearLayout(this).apply {
        gravity = Gravity.CENTER_VERTICAL
        setPadding(0, 16.dp, 0, 16.dp)
        background = getDrawable(android.R.drawable.list_selector_background)
        contentDescription = "${rank}위, ${h.title}, ${meta(h)}"
        setOnClickListener { openArticle(h) }
        addView(TextView(context).apply {
            text = rank.toString(); textSize = 22f; setTypeface(null, android.graphics.Typeface.BOLD)
            setTextColor(getColor(R.color.green_dark))
        }, LinearLayout.LayoutParams(40.dp, -2))
        addView(LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            addView(TextView(context).apply { text = h.title; textSize = 16f; maxLines = 2; ellipsize = android.text.TextUtils.TruncateAt.END })
            addView(TextView(context).apply { text = meta(h); textSize = 13f; setTextColor(getColor(R.color.sub)); setPadding(0, 6.dp, 0, 0) })
        }, LinearLayout.LayoutParams(0, -2, 1f))
        // 기사 이미지가 없는 RSS 라서 언론사 이름 첫 글자로 썸네일
        addView(TextView(context).apply {
            text = h.source.take(2)
            gravity = Gravity.CENTER
            textSize = 15f
            setTypeface(null, android.graphics.Typeface.BOLD)
            setTextColor(getColor(R.color.sub))
            background = getDrawable(R.drawable.box_photo)
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        }, LinearLayout.LayoutParams(56.dp, 56.dp).apply { marginStart = 12.dp })
    }

    private fun openArticle(h: Headline) {
        runCatching { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(h.link))) }
            .onFailure { toast("기사를 열 앱이 없어요") }
    }

    // ---------- 듣기 ----------
    private fun toggleListen() {
        if (Speaker.isSpeaking(SPEAK_TAG)) { Speaker.stop(SPEAK_TAG); renderListen(); return }
        if (items.isEmpty()) { toast("들을 뉴스가 없어요"); return }
        if (Speaker.headsetConnected(this)) startListen()
        else MaterialAlertDialogBuilder(this)
            .setTitle("이어폰이 연결되어 있지 않아요")
            .setMessage("스피커로 들으면 주변 사람에게도 들려요.")
            .setPositiveButton("스피커로 듣기") { _, _ -> startListen() }
            .setNegativeButton("취소", null)
            .show()
    }

    private fun startListen() {
        // 하차역까지 남은 시간만큼만 (헤드라인 하나에 약 8초)
        val minutes = store.stopsLeft()?.let(TrainClock::minutesFor)
        val count = if (minutes == null) 10 else (minutes * 60 / 8).coerceIn(1, items.size)
        val texts = listOf("${category} 헤드라인입니다.") +
            items.take(count).mapIndexed { i, h -> "${i + 1}위. ${h.title}. ${h.source}." }
        Speaker.say(this, texts, SPEAK_TAG, onDone = ::renderListen, onStopped = ::renderListen)
        renderListen()
    }

    private fun renderListen() {
        if (isDestroyed) return
        val on = Speaker.isSpeaking(SPEAK_TAG)
        binding.play.setImageResource(if (on) R.drawable.ic_stop else R.drawable.ic_play)
        binding.listenTitle.text = if (on) "헤드라인 읽는 중 · 눌러서 멈추기" else "이어폰으로 헤드라인 듣기"
        binding.listen.contentDescription = binding.listenTitle.text
    }

    companion object {
        const val SPEAK_TAG = "news"
    }
}
