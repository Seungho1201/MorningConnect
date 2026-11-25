package com.example.morningconnect

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat

/**
 * 앱을 켜 두거나 백그라운드에 있어도 같은 칸 사람을 찾는 포그라운드 서비스.
 * 켜져 있는 동안 [SeatNetwork.instance] 가 [BleSeatNetwork] 이고, 새 자리 알림이 오면 알림을 띄운다.
 * 알림의 '끄기'로 멈추고, 앱을 다시 열면 다시 켜진다.
 */
class SeatService : Service() {
    override fun onBind(intent: Intent?) = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopSelf()
            return START_NOT_STICKY
        }
        try {
            if (Build.VERSION.SDK_INT >= 29) {
                startForeground(ONGOING_ID, ongoing(), ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE)
            } else {
                startForeground(ONGOING_ID, ongoing())
            }
        } catch (e: RuntimeException) {
            // 권한을 그사이 거뒀거나 백그라운드에서 시작이 막힌 경우
            stopSelf()
            return START_NOT_STICKY
        }
        val net = SeatNetwork.instance as? BleSeatNetwork ?: BleSeatNetwork(applicationContext)
        if (!net.start()) {
            net.stop()
            stopSelf()
            return START_NOT_STICKY
        }
        net.onOffer = ::notifyOffer
        net.onTaken = { id -> getSystemService(NotificationManager::class.java).cancel(TAG, id.hashCode()) }
        SeatNetwork.instance = net
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        (SeatNetwork.instance as? BleSeatNetwork)?.stop()
        SeatNetwork.instance = SimulatedSeatNetwork()
        super.onDestroy()
    }

    private fun ongoing() = NotificationCompat.Builder(this, channel(this, BG_CHANNEL))
        .setSmallIcon(R.drawable.ic_bluetooth)
        .setContentTitle("같은 칸 자리 알림을 받고 있어요")
        .setContentText("블루투스로 가까운 사람을 찾는 중")
        .setOngoing(true)
        .setContentIntent(activity(this, 10, Intent(this, MainActivity::class.java)))
        .addAction(
            R.drawable.ic_stop, "끄기",
            PendingIntent.getService(this, 11, Intent(this, SeatService::class.java).setAction(ACTION_STOP), PendingIntent.FLAG_IMMUTABLE),
        )
        .build()

    // 받는 사람: 같은 칸 사람이 '곧 내려요'를 알렸을 때
    private fun notifyOffer(h: Handoff) {
        val store = store
        if (store.handoff != null) return  // 이미 자리를 주고받는 중
        val open = Intent(this, ReceiveActivity::class.java).putExtra(ReceiveActivity.EXTRA_HANDOFF, h.toJson().toString())
        post(
            this, h.id.hashCode(), "${h.seat.doorLabel(h.car)} 자리가 곧 비어요",
            "${Line2.at(h.destStation).name}에서 내릴 예정이에요 · 눌러서 확인",
            activity(this, h.id.hashCode(), open),
        )
    }

    companion object {
        private const val TAG = "seat"
        private const val CHANNEL = "seat"
        private const val BG_CHANNEL = "seat_bg"
        private const val ONGOING_ID = 2
        private const val ACTION_STOP = "com.example.morningconnect.SEAT_STOP"

        val permissions: List<String>
            get() = buildList {
                if (Build.VERSION.SDK_INT >= 31) {
                    add(Manifest.permission.BLUETOOTH_SCAN)
                    add(Manifest.permission.BLUETOOTH_ADVERTISE)
                    add(Manifest.permission.BLUETOOTH_CONNECT)  // 블루투스 켜기 요청에 필요
                } else {
                    add(Manifest.permission.ACCESS_FINE_LOCATION)  // Android 11 이하는 BLE 스캔에 위치 권한
                }
                if (Build.VERSION.SDK_INT >= 33) add(Manifest.permission.POST_NOTIFICATIONS)
            }

        fun missingPermissions(context: Context) = permissions.filter {
            // 알림 권한은 없어도 찾기는 됨
            it != Manifest.permission.POST_NOTIFICATIONS &&
                ContextCompat.checkSelfPermission(context, it) != PackageManager.PERMISSION_GRANTED
        }

        fun bluetoothOn(context: Context) =
            context.getSystemService(BluetoothManager::class.java)?.adapter?.isEnabled == true

        fun hasBle(context: Context) = context.packageManager.hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)

        // 권한이 있고 블루투스가 켜져 있으면 시작 (이미 켜져 있으면 그대로)
        fun start(context: Context): Boolean {
            if (!hasBle(context) || missingPermissions(context).isNotEmpty() || !bluetoothOn(context)) return false
            ContextCompat.startForegroundService(context, Intent(context, SeatService::class.java))
            return true
        }

        val running get() = SeatNetwork.instance is BleSeatNetwork

        // 넘겨주는 사람: 앱이 뒤에 있을 때 누가 '갈게요'를 눌렀다고 알림
        fun notifyMatched(context: Context) = post(
            context, ONGOING_ID + 1, "연결됐어요", "상대가 자리 앞으로 오고 있어요 · 눌러서 서로 찾기",
            activity(context, 12, Intent(context, MatchActivity::class.java)),
        )

        private fun post(context: Context, id: Int, title: String, text: String, open: PendingIntent) {
            if (Build.VERSION.SDK_INT >= 33 &&
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
            ) return
            val n = NotificationCompat.Builder(context, channel(context, CHANNEL))
                .setSmallIcon(R.drawable.ic_bluetooth)
                .setContentTitle(title)
                .setContentText(text)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setCategory(NotificationCompat.CATEGORY_MESSAGE)
                .setContentIntent(open)
                .setAutoCancel(true)
                .build()
            context.getSystemService(NotificationManager::class.java).notify(TAG, id, n)
        }

        private fun activity(context: Context, code: Int, intent: Intent) = PendingIntent.getActivity(
            context, code, intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        private fun channel(context: Context, id: String): String {
            if (Build.VERSION.SDK_INT >= 26) {
                val (name, importance) = if (id == CHANNEL) "같은 칸 자리 알림" to NotificationManager.IMPORTANCE_HIGH
                else "같은 칸 찾기 (켜져 있는 동안)" to NotificationManager.IMPORTANCE_LOW
                context.getSystemService(NotificationManager::class.java)
                    .createNotificationChannel(NotificationChannel(id, name, importance))
            }
            return id
        }
    }
}
