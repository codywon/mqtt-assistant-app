package com.example.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.wifi.WifiManager
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.MainActivity
import com.example.mqtt.MqttClientManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * 生产级 Android MQTT 前台保活服务：
 * 1. 启动为 Foreground Service (通知栏常驻)，防止系统在应用最小化或息屏时挂起进程；
 * 2. 组合持有 CPU WakeLock 与 Wi-Fi Lock，防止 Wi-Fi 芯片和 Socket 进入省电休眠；
 * 3. 周期性 (15秒) 执行活跃心跳检查与状态刷新，确保长连接持续健康。
 */
class MqttBackgroundService : Service() {

    private val serviceScope = CoroutineScope(Dispatchers.IO + Job())
    private var wakeLock: PowerManager.WakeLock? = null
    private var wifiLock: WifiManager.WifiLock? = null
    private var heartbeatJob: Job? = null

    companion object {
        private const val TAG = "MqttBgService"
        const val CHANNEL_ID = "mqtt_keepalive_channel"
        const val NOTIFICATION_ID = 10086
        const val ACTION_START = "com.example.service.ACTION_START"
        const val ACTION_STOP = "com.example.service.ACTION_STOP"
        const val EXTRA_BROKER = "EXTRA_BROKER"

        var isRunning: Boolean = false
            private set

        fun startKeepAlive(context: Context, brokerHost: String = "MQTT Broker") {
            try {
                val intent = Intent(context, MqttBackgroundService::class.java).apply {
                    action = ACTION_START
                    putExtra(EXTRA_BROKER, brokerHost)
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(intent)
                } else {
                    context.startService(intent)
                }
                Log.d(TAG, "Requested startKeepAlive for $brokerHost")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to start keep alive service", e)
            }
        }

        fun stopKeepAlive(context: Context) {
            try {
                val intent = Intent(context, MqttBackgroundService::class.java).apply {
                    action = ACTION_STOP
                }
                context.stopService(intent)
                Log.d(TAG, "Requested stopKeepAlive")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to stop keep alive service", e)
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopSelf()
            return START_NOT_STICKY
        }

        isRunning = true
        acquireWakeAndWifiLocks()

        val brokerHost = intent?.getStringExtra(EXTRA_BROKER) ?: "MQTT Broker"
        val notification = buildForegroundNotification("已保持后台常驻连接 · $brokerHost")

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(
                    NOTIFICATION_ID,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
                )
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error starting foreground service", e)
        }

        startHeartbeatLoop()

        return START_STICKY
    }

    private fun acquireWakeAndWifiLocks() {
        try {
            if (wakeLock == null) {
                val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
                wakeLock = powerManager.newWakeLock(
                    PowerManager.PARTIAL_WAKE_LOCK,
                    "MqttAssistant:KeepAliveLock"
                ).apply {
                    setReferenceCounted(false)
                    acquire(24 * 60 * 60 * 1000L) // 24小时长期保活
                }
                Log.d(TAG, "Acquired PARTIAL_WAKE_LOCK")
            }

            if (wifiLock == null) {
                val wifiManager = applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
                wifiLock = wifiManager?.createWifiLock(
                    WifiManager.WIFI_MODE_FULL_HIGH_PERF,
                    "MqttAssistant:WifiKeepAliveLock"
                )?.apply {
                    setReferenceCounted(false)
                    acquire()
                }
                Log.d(TAG, "Acquired WIFI_MODE_FULL_HIGH_PERF Lock")
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error acquiring wake/wifi locks", e)
        }
    }

    private fun startHeartbeatLoop() {
        heartbeatJob?.cancel()
        heartbeatJob = serviceScope.launch {
            while (isActive) {
                delay(15_000L) // 每 15 秒检查并刷新一次活跃心跳
                try {
                    MqttClientManager.pingOrKeepAlive()

                    // 确保 WakeLock 与 WifiLock 在后台始终持有
                    wakeLock?.let {
                        if (!it.isHeld) it.acquire(60 * 1000L)
                    }
                    wifiLock?.let {
                        if (!it.isHeld) it.acquire()
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Heartbeat iteration failed", e)
                }
            }
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "MQTT 助手长连接常驻服务",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "确保应用最小化或息屏时维持 MQTT 长连接与实时接收"
                setShowBadge(false)
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(channel)
        }
    }

    private fun buildForegroundNotification(contentText: String): Notification {
        val launchIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            launchIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("MQTT 助手 · 正在后台保持连接")
            .setContentText(contentText)
            .setSmallIcon(android.R.drawable.stat_notify_sync)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .build()
    }

    override fun onDestroy() {
        super.onDestroy()
        isRunning = false
        heartbeatJob?.cancel()
        serviceScope.cancel()
        try {
            wakeLock?.let {
                if (it.isHeld) it.release()
            }
            wifiLock?.let {
                if (it.isHeld) it.release()
            }
            Log.d(TAG, "Released wake and wifi locks")
        } catch (e: Exception) {
            Log.w(TAG, "Error releasing locks", e)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
