package com.example.morningconnect

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat

/**
 * 하차역 알림: 하차역 N정거장 전에 도착하는 시각에 알람을 걸고,
 * 시간이 되면 알림을 띄우고 이어폰이 연결되어 있으면 "곧 ○○역이에요" 라고 읽어 준다.
 */
object ArrivalAlarm {
    private const val CHANNEL = "arrival"
    private const val NOTIFY_ID = 1
    const val ACTION_ALERT = "com.example.morningconnect.ARRIVAL_ALERT"

    // 하차역을 정하면 알림 시각 계산 후 예약. 이미 N정거장 안쪽이면 바로 알림
    fun schedule(context: Context) {
        val store = context.store
        val dest = store.destination ?: return cancel(context)
        val clock = store.clock
        val alertStation = Line2.step(dest.station, -dest.alertBefore, clock.dir)
        val left = Line2.stopsBetween(clock.current(), dest.station, clock.dir)
        val now = System.currentTimeMillis()
        val at = if (left <= dest.alertBefore) now + 1_000 else clock.arrivalAt(alertStation, now)

        val am = context.getSystemService(AlarmManager::class.java)
        val pi = pending(context)
        if (Build.VERSION.SDK_INT >= 31 && !am.canScheduleExactAlarms()) {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
        } else {
            am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
        }
    }

    fun cancel(context: Context) {
        context.getSystemService(AlarmManager::class.java).cancel(pending(context))
        NotificationManagerCompat.from(context).cancel(NOTIFY_ID)
    }

    private fun pending(context: Context) = PendingIntent.getBroadcast(
        context, 0, Intent(context, ArrivalReceiver::class.java).setAction(ACTION_ALERT),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    fun notify(context: Context, stationName: String, stopsLeft: Int, autoSuggest: Boolean) {
        val nm = context.getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= 26) {
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL, "하차역 알림", NotificationManager.IMPORTANCE_HIGH).apply {
                    description = "내릴 역이 가까워지면 알려요"
                }
            )
        }
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return

        val open = PendingIntent.getActivity(
            context, 1, Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE,
        )
        val text = if (stopsLeft == 0) "지금 내리세요" else "${stopsLeft}정거장 남았어요 · 약 ${TrainClock.minutesFor(stopsLeft)}분"
        val builder = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_bolt)
            .setContentTitle("곧 ${stationName}역이에요")
            .setContentText(text)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setContentIntent(open)
            .setAutoCancel(true)
        // '곧 내려요' 자동 제안: 알림에서 바로 같은 칸에 알리기 화면으로
        if (autoSuggest) {
            val send = PendingIntent.getActivity(
                context, 2, Intent(context, SendDownActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                PendingIntent.FLAG_IMMUTABLE,
            )
            builder.addAction(R.drawable.ic_bolt, "곧 내려요 · 같은 칸에 알리기", send)
        }
        nm.notify(NOTIFY_ID, builder.build())
    }
}

class ArrivalReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val store = context.store
        when (intent.action) {
            // 재부팅하면 예약된 알람이 사라져서 다시 건다
            Intent.ACTION_BOOT_COMPLETED -> { if (store.stopsLeft() != null) ArrivalAlarm.schedule(context); return }
            ArrivalAlarm.ACTION_ALERT -> Unit
            else -> return
        }
        val left = store.stopsLeft() ?: return
        val dest = store.destination ?: return
        val name = Line2.at(dest.station).name
        ArrivalAlarm.notify(context, name, left, store.autoSuggest)

        // 뉴스 듣기는 하차역 알림이 오면 멈춤 (말하기 시작하면 이전 문장은 끊김)
        Speaker.stop(NewsActivity.SPEAK_TAG)
        if (store.voiceGuide && Speaker.headsetConnected(context)) {
            val result = goAsync()
            var finished = false
            val finish = { if (!finished) { finished = true; result.finish() } }
            Speaker.say(context, listOf("곧 ${name}역이에요"), "arrival", onDone = finish, onStopped = finish)
            Handler(Looper.getMainLooper()).postDelayed(finish, 9_000)
        }
    }
}
