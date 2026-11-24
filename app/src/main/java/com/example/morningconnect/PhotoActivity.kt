package com.example.morningconnect

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.util.LruCache
import android.view.Gravity
import android.widget.FrameLayout
import android.widget.GridLayout
import android.widget.ImageView
import android.widget.TextView
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.core.view.children
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import com.example.morningconnect.databinding.ActivityPhotoBinding
import com.example.morningconnect.databinding.DialogUploadBinding
import com.google.android.material.chip.Chip
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

// 이곳 사진: 지금 역(또는 다음 역) 주변 사진 보기 · 올리기 · 좋아요
class PhotoActivity : AppCompatActivity() {
    private lateinit var binding: ActivityPhotoBinding
    private lateinit var repo: PhotoRepo
    private var station = 0
    private var filter = PhotoRepo.ALL
    private var cameraUri: Uri? = null
    private val cache = LruCache<String, Bitmap>(24)

    private val pickPhoto = registerForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        uri?.let(::askDetails)
    }
    private val takePhoto = registerForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        val uri = cameraUri
        if (ok && uri != null) askDetails(uri)
    }
    private val cameraPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) launchCamera() else toast("카메라 권한이 있어야 바로 찍을 수 있어요")
    }

    private val current get() = store.clock.current()
    private val next get() = store.clock.let { Line2.step(it.current(), 1, it.dir) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPhotoBinding.inflate(layoutInflater)
        setScreen(binding.root, binding.content)
        binding.upload.keepAboveNavBar()
        repo = PhotoRepo(this)

        station = savedInstanceState?.getInt("station") ?: current
        filter = savedInstanceState?.getString("filter") ?: filter
        cameraUri = savedInstanceState?.getString("camera")?.let(Uri::parse)

        binding.back.setOnClickListener { finish() }
        binding.subtitle.text = "지나가는 역 주변 · 사용자들이 올린 사진"
        (listOf(PhotoRepo.ALL) + PhotoRepo.CATEGORIES).forEach { cat ->
            binding.filters.addView(TextView(this, null, 0, R.style.Pill).apply {
                text = cat
                textSize = 16f
                setOnClickListener { filter = cat; render() }
            }, android.widget.LinearLayout.LayoutParams(android.widget.LinearLayout.LayoutParams.WRAP_CONTENT, 44.dp).apply { marginEnd = 8.dp })
        }
        binding.other.setOnClickListener {
            station = if (station == current) next else current
            render()
        }
        binding.upload.setOnClickListener { chooseSource() }
        binding.featuredImage.clipToOutline = true
        binding.featured.clipToOutline = true
    }

    override fun onResume() {
        super.onResume()
        render()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putInt("station", station)
        outState.putString("filter", filter)
        cameraUri?.let { outState.putString("camera", it.toString()) }
    }

    private fun render() {
        val name = Line2.at(station).name
        val isCurrent = station == current
        binding.title.text = "${name}역 이곳 사진"
        binding.filters.children.forEach { (it as TextView).pillOn(it.text == filter) }

        val list = repo.forStation(station, filter)
        val top = repo.featured(list)
        binding.empty.isVisible = top == null
        binding.empty.text = if (filter == PhotoRepo.ALL) "아직 올라온 사진이 없어요\n${name}역 주변 첫 사진을 올려 주세요"
        else "'$filter' 사진이 아직 없어요"
        binding.featuredCaption.isVisible = top != null
        binding.like.isVisible = top != null
        binding.featuredImage.setImageDrawable(null)
        if (top != null) {
            binding.featuredCaption.text = caption(top)
            renderLike(top)
            binding.like.setOnClickListener { repo.toggleLike(top.id); render() }
            binding.featured.setOnClickListener { showPhoto(top) }
            binding.featured.contentDescription = "대표 사진, ${top.category}, ${caption(top)}"
            load(top, binding.featuredImage, 1080)
        } else {
            binding.featured.setOnClickListener { chooseSource() }
            binding.featured.contentDescription = binding.empty.text
        }

        binding.grid.removeAllViews()
        list.filter { it !== top }.forEachIndexed { i, p -> binding.grid.addView(cell(p), gridParams(i)) }

        val other = if (isCurrent) next else current
        val count = repo.forStation(other).size
        binding.otherTitle.text = if (isCurrent) "다음 역 ${Line2.at(other).name} 사진 미리 보기" else "지금 역 ${Line2.at(other).name} 사진 보기"
        binding.otherCount.text = "${count}장"
        binding.upload.contentDescription = "${name}역 사진 올리기"
    }

    private fun renderLike(p: Photo) {
        binding.like.text = p.likes.toString()
        binding.like.contentDescription = "좋아요 ${p.likes}개" + if (p.liked) ", 누름" else ""
        val color = getColor(if (p.liked) R.color.pink else R.color.ink)
        binding.like.setTextColor(color)
        binding.like.compoundDrawablesRelative[0]?.mutate()?.setTint(color)
    }

    private fun caption(p: Photo) = listOf(p.place.ifBlank { p.category }, NewsRepo.ago(p.createdAt)).filter { it.isNotBlank() }.joinToString(" · ")

    private fun gridParams(i: Int) = GridLayout.LayoutParams(GridLayout.spec(i / 3), GridLayout.spec(i % 3, 1f)).apply {
        width = 0
        height = 110.dp
        setMargins(3.dp, 3.dp, 3.dp, 3.dp)
    }

    private fun cell(p: Photo) = FrameLayout(this).apply {
        background = getDrawable(R.drawable.box_photo)
        clipToOutline = true
        foreground = getDrawable(android.R.drawable.list_selector_background)
        contentDescription = "${p.category}, ${caption(p)}"
        val img = ImageView(context).apply { scaleType = ImageView.ScaleType.CENTER_CROP; importantForAccessibility = ImageView.IMPORTANT_FOR_ACCESSIBILITY_NO }
        addView(img, FrameLayout.LayoutParams(-1, -1))
        addView(TextView(context).apply {
            text = p.place.ifBlank { p.category }
            textSize = 12f
            setTextColor(Color.WHITE)
            setShadowLayer(4f, 0f, 1f, 0x99000000.toInt())
            maxLines = 1
            setPadding(10.dp, 0, 10.dp, 8.dp)
            importantForAccessibility = TextView.IMPORTANT_FOR_ACCESSIBILITY_NO
        }, FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM))
        setOnClickListener { showPhoto(p) }
        load(p, img, 360)
    }

    private fun load(p: Photo, into: ImageView, px: Int) {
        val key = "${p.id}@$px"
        cache.get(key)?.let { into.setImageBitmap(it); return }
        into.tag = key
        lifecycleScope.launch {
            val bmp = withContext(Dispatchers.IO) { PhotoRepo.thumbnail(p.file(this@PhotoActivity), px) } ?: return@launch
            cache.put(key, bmp)
            if (into.tag == key) into.setImageBitmap(bmp)
        }
    }

    private fun showPhoto(p: Photo) {
        val img = ImageView(this).apply {
            adjustViewBounds = true
            scaleType = ImageView.ScaleType.FIT_CENTER
            contentDescription = "${p.category}, ${caption(p)}"
        }
        load(p, img, 1600)
        MaterialAlertDialogBuilder(this)
            .setTitle(caption(p))
            .setView(img)
            .setPositiveButton(if (p.liked) "좋아요 취소" else "좋아요") { _, _ -> repo.toggleLike(p.id); render() }
            .setNeutralButton("삭제") { _, _ -> confirmDelete(p) }
            .setNegativeButton("닫기", null)
            .show()
    }

    private fun confirmDelete(p: Photo) {
        MaterialAlertDialogBuilder(this)
            .setTitle("이 사진을 지울까요?")
            .setPositiveButton("지우기") { _, _ -> repo.delete(p.id); cache.evictAll(); render() }
            .setNegativeButton("취소", null)
            .show()
    }

    private fun chooseSource() {
        MaterialAlertDialogBuilder(this)
            .setTitle("${Line2.at(station).name}역 사진 올리기")
            .setItems(arrayOf("카메라로 찍기", "앨범에서 고르기")) { _, which ->
                if (which == 0) {
                    if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) launchCamera()
                    else cameraPermission.launch(Manifest.permission.CAMERA)
                } else {
                    pickPhoto.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                }
            }
            .show()
    }

    private fun launchCamera() {
        val file = File(File(cacheDir, "camera").apply { mkdirs() }, "shot_${System.currentTimeMillis()}.jpg")
        val uri = FileProvider.getUriForFile(this, "$packageName.files", file)
        cameraUri = uri
        takePhoto.launch(uri)
    }

    private fun askDetails(uri: Uri) {
        val d = DialogUploadBinding.inflate(layoutInflater)
        d.preview.setImageURI(uri)
        d.preview.clipToOutline = true
        PhotoRepo.CATEGORIES.forEachIndexed { i, cat ->
            d.category.addView(Chip(this).apply {
                id = i + 1
                text = cat
                isCheckable = true
                isChecked = cat == filter || (filter == PhotoRepo.ALL && i == 0)
                chipMinHeight = 44.dp.toFloat()
            })
        }
        val stationAt = station
        MaterialAlertDialogBuilder(this)
            .setTitle("${Line2.at(stationAt).name}역 사진")
            .setView(d.root)
            .setPositiveButton("올리기") { _, _ ->
                val category = PhotoRepo.CATEGORIES[(d.category.checkedChipId - 1).coerceIn(PhotoRepo.CATEGORIES.indices)]
                lifecycleScope.launch {
                    runCatching { repo.add(uri, stationAt, category, d.place.text.toString()) }
                        .onSuccess { toast("사진을 올렸어요"); filter = PhotoRepo.ALL; render() }
                        .onFailure { toast(it.message ?: "사진을 올리지 못했어요") }
                    cacheDir.resolve("camera").listFiles()?.forEach { it.delete() }
                }
            }
            .setNegativeButton("취소", null)
            .show()
    }
}
