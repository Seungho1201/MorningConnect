package com.example.morningconnect

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import androidx.exifinterface.media.ExifInterface
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID
import kotlin.math.max

class Photo(
    val id: String,
    val station: Int,
    val category: String,
    val place: String,
    val createdAt: Long,
    var likes: Int,
    var liked: Boolean,
) {
    fun file(context: Context) = File(PhotoRepo.dir(context), "$id.jpg")
}

/**
 * 이곳 사진: 역별로 올린 사진을 앱 저장소에 보관한다 (사진 파일 + photos.json 목록).
 * 서버에 올려 다른 사용자와 나누는 부분은 같은 칸 통신과 함께 붙일 자리다.
 */
class PhotoRepo(private val context: Context) {
    private val index get() = File(context.filesDir, "photos.json")

    fun all(): List<Photo> = runCatching {
        val a = JSONArray(index.readText())
        (0 until a.length()).map { i ->
            val o = a.getJSONObject(i)
            Photo(o.getString("id"), o.getInt("st"), o.getString("cat"), o.getString("place"),
                o.getLong("t"), o.getInt("likes"), o.getBoolean("liked"))
        }
    }.getOrDefault(emptyList())

    fun forStation(station: Int, category: String = ALL) =
        all().filter { it.station == station && (category == ALL || it.category == category) }
            .sortedByDescending { it.createdAt }

    // 대표 사진: 좋아요가 가장 많은 사진 (같으면 최신)
    fun featured(list: List<Photo>) = list.maxWithOrNull(compareBy<Photo> { it.likes }.thenBy { it.createdAt })

    private fun save(list: List<Photo>) {
        val a = JSONArray()
        list.forEach {
            a.put(JSONObject().put("id", it.id).put("st", it.station).put("cat", it.category).put("place", it.place)
                .put("t", it.createdAt).put("likes", it.likes).put("liked", it.liked))
        }
        index.writeText(a.toString())
    }

    // 고른 사진을 긴 변 1600px JPEG 로 줄이고 방향을 바로 잡아서 저장
    suspend fun add(source: Uri, station: Int, category: String, place: String): Photo = withContext(Dispatchers.IO) {
        val photo = Photo(UUID.randomUUID().toString(), station, category, place.trim(), System.currentTimeMillis(), 0, false)
        val bitmap = decode(source, 1600) ?: error("사진을 열 수 없어요")
        photo.file(context).outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 85, it) }
        bitmap.recycle()
        save(all() + photo)
        photo
    }

    fun toggleLike(id: String): Photo? {
        val list = all()
        val p = list.find { it.id == id } ?: return null
        p.liked = !p.liked
        p.likes = max(0, p.likes + if (p.liked) 1 else -1)
        save(list)
        return p
    }

    fun delete(id: String) {
        val list = all()
        list.find { it.id == id }?.file(context)?.delete()
        save(list.filter { it.id != id })
    }

    private fun decode(uri: Uri, maxSide: Int): Bitmap? {
        val resolver = context.contentResolver
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        if (bounds.outWidth <= 0) return null
        var sample = 1
        while (max(bounds.outWidth, bounds.outHeight) / (sample * 2) >= maxSide) sample *= 2
        val raw = resolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
        } ?: return null
        val rotation = resolver.openInputStream(uri)?.use { ExifInterface(it).rotationDegrees } ?: 0
        val scale = maxSide.toFloat() / max(raw.width, raw.height)
        val m = Matrix().apply {
            if (scale < 1f) postScale(scale, scale)
            if (rotation != 0) postRotate(rotation.toFloat())
        }
        if (m.isIdentity) return raw
        return Bitmap.createBitmap(raw, 0, 0, raw.width, raw.height, m, true).also { if (it !== raw) raw.recycle() }
    }

    companion object {
        const val ALL = "전체"
        val CATEGORIES = listOf("출구 풍경", "맛집", "혼잡도")
        fun dir(context: Context) = File(context.filesDir, "photos").apply { mkdirs() }

        // 썸네일: 화면 크기에 맞게 줄여서 읽기
        fun thumbnail(file: File, targetPx: Int): Bitmap? {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(file.path, bounds)
            if (bounds.outWidth <= 0) return null
            var sample = 1
            while (max(bounds.outWidth, bounds.outHeight) / (sample * 2) >= targetPx) sample *= 2
            return BitmapFactory.decodeFile(file.path, BitmapFactory.Options().apply { inSampleSize = sample })
        }
    }
}
