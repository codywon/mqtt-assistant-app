package com.example.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.net.wifi.WifiManager
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.MainActivity
import com.example.data.MqttStorageRepository
import com.example.model.MqttLogPacket
import com.example.model.MqttServerConfig
import com.example.mqtt.MqttClientManager
import com.example.receiver.AlarmPulseReceiver
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

/**
 * 生产级 Android MQTT 前台保活服务：
 * 1. 启动为 Foreground Service (通知栏常驻)，防止系统在应用最小化或息屏时挂起进程；
 * 2. 组合持有 CPU WakeLock 与 Wi-Fi Lock，防止 Wi-Fi 芯片和 Socket 进入省电休眠；
 * 3. 动态监听息屏/亮屏事件，熄屏时触发预保活 Ping，亮屏时 0ms 探活自愈；
 * 4. 协同 AlarmPulseReceiver 穿透 Doze 低功耗模式进行定时精准脉冲唤醒。
 */
class MqttBackgroundService : Service() {

    private val serviceScope = CoroutineScope(Dispatchers.IO + Job())
    private var wakeLock: PowerManager.WakeLock? = null
    private var wifiLock: WifiManager.WifiLock? = null
    private var heartbeatJob: Job? = null
    private var screenReceiver: BroadcastReceiver? = null

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

        /**
         * 供外部（如 AlarmPulseReceiver、系统开机广播等）调用的静默重连与保活自愈方法
         */
        suspend fun ensureMqttConnected(context: Context): Result<Unit> {
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
                    val storage = MqttStorageRepository(context)
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
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        MqttClientManager.addMessageListener(backgroundMessageListener)
        registerScreenStateReceiver()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                AlarmPulseReceiver.cancelPulse(this)
                stopSelf()
                return START_NOT_STICKY
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
            ensureMqttConnected(applicationContext)
        }
        startHeartbeatLoop()
        AlarmPulseReceiver.scheduleNextPulse(this)

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
                        val result = ensureMqttConnected(applicationContext)
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

    /**
     * 动态监听屏幕状态变化：
     * 1. 息屏瞬间：立即主动发送探活 PINGREQ，并排期 15 秒精准心跳，确保进入休眠时链路表项处于最新活跃状态；
     * 2. 亮屏/解锁瞬间：0ms 探活并自愈重连，保障用户点亮手机瞬间即可收发消息。
     */
    private fun registerScreenStateReceiver() {
        if (screenReceiver != null) return
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_USER_PRESENT)
        }
        screenReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent?) {
                when (intent?.action) {
                    Intent.ACTION_SCREEN_OFF -> {
                        Log.d(TAG, "Screen OFF: proactive MQTT ping and scheduling pulse")
                        serviceScope.launch {
                            try {
                                if (MqttClientManager.isConnected) {
                                    MqttClientManager.pingOrKeepAlive()
                                }
                            } catch (e: Exception) {
                                Log.w(TAG, "Screen OFF ping error", e)
                            }
                        }
                        AlarmPulseReceiver.scheduleNextPulse(this@MqttBackgroundService, 15_000L)
                    }
                    Intent.ACTION_SCREEN_ON, Intent.ACTION_USER_PRESENT -> {
                        Log.d(TAG, "Screen ON / USER PRESENT: zero-delay active check and healing")
                        serviceScope.launch {
                            try {
                                if (!MqttClientManager.isConnected && !MqttClientManager.isConnecting) {
                                    ensureMqttConnected(applicationContext)
                                } else if (MqttClientManager.isConnected) {
                                    MqttClientManager.pingOrKeepAlive()
                                }
                            } catch (e: Exception) {
                                Log.w(TAG, "Screen ON check error", e)
                            }
                        }
                    }
                }
            }
        }
        try {
            registerReceiver(screenReceiver, filter)
            Log.d(TAG, "Registered screen state receiver")
        } catch (e: Exception) {
            Log.w(TAG, "Failed to register screenReceiver", e)
        }
    }

    private fun unregisterScreenStateReceiver() {
        screenReceiver?.let {
            try {
                unregisterReceiver(it)
                Log.d(TAG, "Unregistered screen state receiver")
            } catch (e: Exception) {
                Log.w(TAG, "Failed to unregister screenReceiver", e)
            }
            screenReceiver = null
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        isRunning = false
        AlarmPulseReceiver.cancelPulse(this)
        unregisterScreenStateReceiver()
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
