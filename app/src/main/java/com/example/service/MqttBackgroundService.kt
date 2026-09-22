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
import android.app.AlarmManager
import android.os.SystemClock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

import com.example.data.MqttStorageRepository
import com.example.model.MqttLogPacket
import com.example.model.MqttServerConfig
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

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

    private val backgroundMessageListener: (String, Int, ByteArray, Boolean) -> Unit = { topic, qos, payloadBytes, retain ->
        serviceScope.launch(Dispatchers.IO) {
            try {
                totalPacketCount++
                latestMessageTopic = topic
                refreshNotification()

                // 后台无 UI 独立运行时，由服务自动将报文持久化入库 SQLite
                val timeStr = SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault()).format(Date())
                val payloadString = try {
                    String(payloadBytes, Charsets.UTF_8)
                } catch (e: Exception) {
                    payloadBytes.joinToString(" ") { "%02X".format(it) }
                }
                val packet = MqttLogPacket(
                    id = UUID.randomUUID().toString(),
                    topic = topic,
                    qos = qos,
                    packetSeq = "#%04d".format(totalPacketCount),
                    timestamp = timeStr,
                    payload = payloadString,
                    devInfo = if (retain) "QoS$qos · Retain" else "QoS$qos",
                    sizeText = "${payloadBytes.size} B",
                    category = topic.substringBefore('/'),
                    dotColorHex = 0xFF10B981
                )
                val storage = MqttStorageRepository(applicationContext)
                storage.savePackets(listOf(packet), storage.loadBufferThreshold())
            } catch (e: Exception) {
                Log.w(TAG, "Failed to persist background message", e)
            }
        }
    }

    companion object {
        private const val TAG = "MqttBgService"
        const val CHANNEL_ID = "mqtt_keepalive_channel"
        const val NOTIFICATION_ID = 10086
        const val ACTION_START = "com.example.service.ACTION_START"
        const val ACTION_STOP = "com.example.service.ACTION_STOP"
        const val ACTION_UPDATE_STATS = "com.example.service.ACTION_UPDATE_STATS"
        const val ACTION_PULSE_HEARTBEAT = "com.example.service.ACTION_PULSE_HEARTBEAT"
        const val EXTRA_BROKER = "EXTRA_BROKER"
        const val EXTRA_COUNT = "EXTRA_COUNT"
        const val EXTRA_TOPIC = "EXTRA_TOPIC"

        var isRunning: Boolean = false
            private set

        private var currentBrokerHost: String = ""
        private var totalPacketCount: Long = 0L
        private var latestMessageTopic: String? = null

        fun startKeepAlive(context: Context, brokerHost: String = "MQTT Broker") {
            try {
                currentBrokerHost = brokerHost
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

        fun updateNotification(
            context: Context,
            brokerHost: String? = null,
            count: Long? = null,
            latestTopic: String? = null
        ) {
            brokerHost?.let { currentBrokerHost = it }
            count?.let { totalPacketCount = it }
            latestTopic?.let { latestMessageTopic = it }

            if (!isRunning) return

            try {
                val intent = Intent(context, MqttBackgroundService::class.java).apply {
                    action = ACTION_UPDATE_STATS
                    putExtra(EXTRA_BROKER, currentBrokerHost)
                    putExtra(EXTRA_COUNT, totalPacketCount)
                    putExtra(EXTRA_TOPIC, latestMessageTopic)
                }
                context.startService(intent)
            } catch (e: Exception) {
                Log.w(TAG, "Failed to update notification stats", e)
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
        MqttClientManager.addMessageListener(backgroundMessageListener)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                cancelAlarmPulse()
                stopSelf()
                return START_NOT_STICKY
            }
            ACTION_PULSE_HEARTBEAT -> {
                handlePulseHeartbeat()
                return START_STICKY
            }
            ACTION_UPDATE_STATS -> {
                val host = intent.getStringExtra(EXTRA_BROKER)
                val count = intent.getLongExtra(EXTRA_COUNT, -1L)
                val topic = intent.getStringExtra(EXTRA_TOPIC)
                if (!host.isNullOrBlank()) currentBrokerHost = host
                if (count >= 0L) totalPacketCount = count
                if (topic != null) latestMessageTopic = topic

                refreshNotification()
                return START_STICKY
            }
        }

        isRunning = true
        acquireWakeAndWifiLocks()

        val brokerHost = intent?.getStringExtra(EXTRA_BROKER) ?: currentBrokerHost
        if (brokerHost.isNotBlank()) currentBrokerHost = brokerHost
        val notification = buildForegroundNotification()

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

        serviceScope.launch {
            ensureMqttConnected()
        }
        startHeartbeatLoop()
        scheduleNextAlarmPulse()

        return START_STICKY
    }

    private suspend fun ensureMqttConnected(): Result<Unit> {
        if (MqttClientManager.isConnected || MqttClientManager.isConnecting) {
            return Result.success(Unit)
        }

        // 1. 若内存中已有上一次连接配置，优先快速复用
        if (MqttClientManager.lastConfig != null) {
            return MqttClientManager.reconnectSilently()
        }

        // 2. 冷启动无 lastConfig（如系统开机或应用被杀自启动）：直接从本地存储加载激活 Broker 独立建连
        return withContext(Dispatchers.IO) {
            try {
                val storage = MqttStorageRepository(applicationContext)
                val profiles = storage.loadBrokerProfiles()
                val activeId = storage.loadActiveBrokerId()
                val activeBroker = profiles.find { it.id == activeId } ?: profiles.firstOrNull()

                if (activeBroker == null || activeBroker.host.isBlank()) {
                    Log.w(TAG, "No valid Broker profile configured in storage for background wake")
                    return@withContext Result.failure(IllegalStateException("未配置有效的 Broker 节点"))
                }

                val brokerLabel = "${activeBroker.host}:${activeBroker.port}"
                currentBrokerHost = brokerLabel

                val serverConfig = MqttServerConfig(
                    activeProfileId = activeBroker.id,
                    host = activeBroker.host,
                    port = activeBroker.port,
                    clientId = activeBroker.clientId,
                    username = activeBroker.username,
                    password = activeBroker.password,
                    protocol = activeBroker.protocol,
                    cleanSession = activeBroker.cleanSession,
                    tlsEnabled = activeBroker.tlsEnabled,
                    keepAlive = activeBroker.keepAlive,
                    autoReconnect = storage.loadAutoReconnect(),
                    bufferThreshold = storage.loadBufferThreshold(),
                    backgroundKeepAliveEnabled = true
                )

                Log.i(TAG, "Background Guardian: connecting independently to $brokerLabel...")
                val connRes = MqttClientManager.connect(serverConfig)
                if (connRes.isSuccess) {
                    Log.i(TAG, "Background Guardian: successfully connected! Restoring subscriptions...")
                    val subs = storage.loadSubscriptions().filter { it.isEnabled }
                    if (subs.isNotEmpty()) {
                        MqttClientManager.subscribeBatch(subs.map { it.topic to it.qos })
                    }
                }
                connRes
            } catch (e: Exception) {
                Log.e(TAG, "Failed to connect to Broker in background", e)
                Result.failure(e)
            }
        }
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
                    // 1. 确保 WakeLock 与 WifiLock 在后台始终生效
                    wakeLock?.let {
                        if (!it.isHeld) it.acquire(60 * 1000L)
                    }
                    wifiLock?.let {
                        if (!it.isHeld) it.acquire()
                    }

                    // 2. 探活底层 Socket 心跳
                    MqttClientManager.pingOrKeepAlive()

                    // 3. 后台守护机制：若长连接在后台意外断开，立即独立自动拉起！
                    if (!MqttClientManager.isConnected && !MqttClientManager.isConnecting) {
                        Log.d(TAG, "Background Guardian: detected MQTT disconnected, attempting reconnect...")
                        val result = ensureMqttConnected()
                        if (result.isSuccess) {
                            Log.d(TAG, "Background Guardian: Reconnect succeeded!")
                        } else {
                            Log.w(TAG, "Background Guardian: Reconnect failed: ${result.exceptionOrNull()?.message}")
                        }
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

    private fun buildForegroundNotification(): Notification {
        val launchIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            launchIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val title = if (currentBrokerHost.isNotBlank()) {
            "MQTT 助手 · $currentBrokerHost"
        } else {
            "MQTT 助手"
        }

        val contentText = if (!latestMessageTopic.isNullOrBlank()) {
            "已接收 $totalPacketCount 条报文 · 最新: $latestMessageTopic"
        } else if (totalPacketCount > 0) {
            "● 已接收 $totalPacketCount 条报文 · 运行正常"
        } else {
            "● 连接正常 · 等待报文推送"
        }

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(contentText)
            .setSmallIcon(android.R.drawable.stat_notify_sync)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .build()
    }

    private fun refreshNotification() {
        try {
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            notificationManager?.notify(NOTIFICATION_ID, buildForegroundNotification())
        } catch (e: Exception) {
            Log.w(TAG, "Failed to refresh notification", e)
        }
    }

    private fun scheduleNextAlarmPulse() {
        if (!isRunning) return
        try {
            val alarmManager = getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
            val intent = Intent(this, MqttBackgroundService::class.java).apply {
                action = ACTION_PULSE_HEARTBEAT
            }
            val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            } else {
                PendingIntent.FLAG_UPDATE_CURRENT
            }
            val pendingIntent = PendingIntent.getService(this, 2001, intent, flags)
            val triggerAtMillis = SystemClock.elapsedRealtime() + 25_000L // 25秒一次精准脉冲，穿透 Doze 并保活 NAT

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.ELAPSED_REALTIME_WAKEUP,
                    triggerAtMillis,
                    pendingIntent
                )
            } else {
                alarmManager.setExact(
                    AlarmManager.ELAPSED_REALTIME_WAKEUP,
                    triggerAtMillis,
                    pendingIntent
                )
            }
            Log.d(TAG, "Scheduled next 25s Doze-piercing pulse alarm")
        } catch (e: Exception) {
            Log.w(TAG, "Failed to schedule pulse alarm", e)
        }
    }

    private fun cancelAlarmPulse() {
        try {
            val alarmManager = getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
            val intent = Intent(this, MqttBackgroundService::class.java).apply {
                action = ACTION_PULSE_HEARTBEAT
            }
            val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
            } else {
                PendingIntent.FLAG_NO_CREATE
            }
            val pi = PendingIntent.getService(this, 2001, intent, flags)
            if (pi != null) {
                alarmManager.cancel(pi)
                pi.cancel()
            }
            Log.d(TAG, "Cancelled pulse alarm")
        } catch (e: Exception) {
            Log.w(TAG, "Error cancelling pulse alarm", e)
        }
    }

    private fun handlePulseHeartbeat() {
        if (!isRunning) return

        // 临时唤醒锁：确保在低电耗模式唤醒后 CPU 至少维持 5 秒发完心跳或重连
        try {
            val powerManager = getSystemService(Context.POWER_SERVICE) as? PowerManager
            val tempLock = powerManager?.newWakeLock(
                PowerManager.PARTIAL_WAKE_LOCK,
                "MqttAssistant:PulseTempLock"
            )
            tempLock?.acquire(5000L)
        } catch (_: Exception) {}

        serviceScope.launch {
            try {
                if (MqttClientManager.isConnected) {
                    Log.d(TAG, "Pulse wake: Dispatching active MQTT ping...")
                    MqttClientManager.pingOrKeepAlive()
                } else if (!MqttClientManager.isConnecting) {
                    Log.d(TAG, "Pulse wake: Detected connection lost in sleep, restoring...")
                    val res = ensureMqttConnected()
                    if (res.isSuccess) {
                        Log.d(TAG, "Pulse wake: Reconnected successfully in sleep")
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Error during pulse heartbeat", e)
            } finally {
                // 排期下一个脉冲
                if (isRunning) {
                    scheduleNextAlarmPulse()
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        isRunning = false
        cancelAlarmPulse()
        heartbeatJob?.cancel()
        serviceScope.cancel()
        MqttClientManager.removeMessageListener(backgroundMessageListener)
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
