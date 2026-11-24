package com.example.morningconnect

import android.content.Context
import android.util.Xml
import androidx.core.content.edit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import org.xmlpull.v1.XmlPullParser
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.text.SimpleDateFormat
import java.util.Locale

class Headline(val title: String, val source: String, val link: String, val publishedAt: Long)

/**
 * 헤드라인 뉴스: 구글 뉴스 RSS(한국어)에서 분야별 주요 기사를 받는다.
 * 마지막으로 받은 목록은 저장해 두어서 지하철에서 신호가 끊겨도 보여 준다.
 */
class NewsRepo(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("news", Context.MODE_PRIVATE)

    fun cached(category: String): List<Headline> = runCatching { decode(prefs.getString(category, null)) }.getOrDefault(emptyList())

    suspend fun fetch(category: String): List<Headline> = withContext(Dispatchers.IO) {
        val conn = URL(urlFor(category)).openConnection() as HttpURLConnection
        conn.connectTimeout = 8_000
        conn.readTimeout = 8_000
        conn.setRequestProperty("User-Agent", "MorningConnect/1.0 (Android)")
        try {
            if (conn.responseCode !in 200..299) error("HTTP ${conn.responseCode}")
            val list = conn.inputStream.use { parse(it) }.take(MAX)
            prefs.edit { putString(category, encode(list)) }
            list
        } finally {
            conn.disconnect()
        }
    }

    companion object {
        val CATEGORIES = listOf("주요", "경제", "IT", "사회", "날씨")
        private const val MAX = 20
        private const val TAIL = "hl=ko&gl=KR&ceid=KR:ko"

        fun urlFor(category: String) = when (category) {
            "경제" -> "https://news.google.com/rss/headlines/section/topic/BUSINESS?$TAIL"
            "IT" -> "https://news.google.com/rss/headlines/section/topic/TECHNOLOGY?$TAIL"
            "사회" -> "https://news.google.com/rss/headlines/section/topic/NATION?$TAIL"
            "날씨" -> "https://news.google.com/rss/search?q=${URLEncoder.encode("날씨", "UTF-8")}&$TAIL"
            else -> "https://news.google.com/rss?$TAIL"
        }

        private val RFC822 get() = SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss zzz", Locale.US)

        // RSS 2.0 <item> 들을 읽는다. 제목 끝의 " - 언론사" 는 떼어 낸다
        fun parse(input: InputStream): List<Headline> {
            val p = Xml.newPullParser()
            p.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
            p.setInput(input, null)
            val out = mutableListOf<Headline>()
            var inItem = false
            var title = ""; var link = ""; var source = ""; var date = ""
            while (p.next() != XmlPullParser.END_DOCUMENT) {
                when (p.eventType) {
                    XmlPullParser.START_TAG -> when (p.name) {
                        "item" -> { inItem = true; title = ""; link = ""; source = ""; date = "" }
                        "title" -> if (inItem) title = p.nextText()
                        "link" -> if (inItem) link = p.nextText()
                        "source" -> if (inItem) source = p.nextText()
                        "pubDate" -> if (inItem) date = p.nextText()
                    }
                    XmlPullParser.END_TAG -> if (p.name == "item") {
                        inItem = false
                        var t = title.trim()
                        if (source.isNotBlank() && t.endsWith(" - $source")) t = t.removeSuffix(" - $source").trim()
                        val at = runCatching { RFC822.parse(date.trim())!!.time }.getOrDefault(0L)
                        if (t.isNotEmpty()) out += Headline(t, source.trim(), link.trim(), at)
                    }
                }
            }
            return out
        }

        private fun encode(list: List<Headline>) = JSONArray().apply {
            list.forEach { put(JSONObject().put("t", it.title).put("s", it.source).put("l", it.link).put("d", it.publishedAt)) }
        }.toString()

        private fun decode(s: String?): List<Headline> {
            s ?: return emptyList()
            val a = JSONArray(s)
            return (0 until a.length()).map {
                val o = a.getJSONObject(it)
                Headline(o.getString("t"), o.getString("s"), o.getString("l"), o.getLong("d"))
            }
        }

        // "12분 전", "3시간 전"
        fun ago(at: Long, now: Long = System.currentTimeMillis()): String {
            if (at <= 0) return ""
            val min = (now - at) / 60_000
            return when {
                min < 1 -> "방금"
                min < 60 -> "${min}분 전"
                min < 60 * 24 -> "${min / 60}시간 전"
                else -> "${min / (60 * 24)}일 전"
            }
        }
    }
}
