package com.example.morningconnect

import android.content.Context
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import java.util.Locale

/**
 * 음성 안내(TTS). 하차역 알림과 뉴스 헤드라인 읽기가 함께 쓴다.
 * 새로 말하면 이전 문장은 끊기므로, 하차역 알림이 오면 뉴스는 자동으로 멈춘다.
 */
object Speaker {
    private var tts: TextToSpeech? = null
    private var ready = false
    private var pending: (() -> Unit)? = null
    private val main = Handler(Looper.getMainLooper())

    private var currentTag: String? = null
    private var lastId: String? = null
    private var onDone: (() -> Unit)? = null
    private var onStopped: (() -> Unit)? = null

    fun isSpeaking(tag: String) = currentTag == tag

    // texts 를 차례로 읽는다. 다 읽으면 onDone, 다른 소리에 끊기거나 stop() 되면 onStopped
    fun say(context: Context, texts: List<String>, tag: String, onDone: (() -> Unit)? = null, onStopped: (() -> Unit)? = null) {
        interrupt()
        currentTag = tag
        this.onDone = onDone
        this.onStopped = onStopped
        val run = {
            val engine = tts!!
            texts.forEachIndexed { i, t ->
                val id = "$tag#$i#${System.nanoTime()}"
                lastId = id
                engine.speak(t, if (i == 0) TextToSpeech.QUEUE_FLUSH else TextToSpeech.QUEUE_ADD, null, id)
            }
        }
        if (ready) run() else {
            pending = run
            init(context)
        }
    }

    fun stop(tag: String? = null) {
        if (tag != null && currentTag != tag) return
        interrupt()
        tts?.stop()
    }

    private fun interrupt() {
        val stopped = onStopped
        currentTag = null; onDone = null; onStopped = null; lastId = null
        pending = null
        stopped?.let { main.post(it) }
    }

    private fun init(context: Context) {
        if (tts != null) return
        tts = TextToSpeech(context.applicationContext) { status ->
            main.post {
                if (status == TextToSpeech.SUCCESS) {
                    tts?.language = Locale.KOREAN
                    tts?.setOnUtteranceProgressListener(listener)
                    ready = true
                    pending?.invoke()
                } else {
                    tts = null
                    interrupt()  // 음성 엔진이 없으면 onStopped 로 알림
                }
                pending = null
            }
        }
    }

    private val listener = object : UtteranceProgressListener() {
        override fun onStart(utteranceId: String?) {}
        override fun onDone(utteranceId: String?) {
            main.post {
                if (utteranceId != null && utteranceId == lastId) {
                    val done = onDone
                    currentTag = null; onDone = null; onStopped = null; lastId = null
                    done?.invoke()
                }
            }
        }
        @Deprecated("Deprecated in Java")
        override fun onError(utteranceId: String?) = onDone(utteranceId)
    }

    // 이어폰(유선·블루투스·USB)이 연결되어 있나
    fun headsetConnected(context: Context): Boolean {
        val am = context.getSystemService(AudioManager::class.java) ?: return false
        val types = mutableSetOf(
            AudioDeviceInfo.TYPE_WIRED_HEADSET, AudioDeviceInfo.TYPE_WIRED_HEADPHONES,
            AudioDeviceInfo.TYPE_BLUETOOTH_A2DP, AudioDeviceInfo.TYPE_BLUETOOTH_SCO,
        )
        if (Build.VERSION.SDK_INT >= 26) types += AudioDeviceInfo.TYPE_USB_HEADSET
        if (Build.VERSION.SDK_INT >= 31) types += AudioDeviceInfo.TYPE_BLE_HEADSET
        return am.getDevices(AudioManager.GET_DEVICES_OUTPUTS).any { it.type in types }
    }
}
