package com.example.viewmodel

import android.app.Application
import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.Uri
import android.util.Log
import com.example.util.AutoStartUtil
import com.example.util.ConfigBackupHelper
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.MqttStorageRepository
import com.example.model.AppScreen
import com.example.model.BrokerProfile
import com.example.model.MqttConnectionState
import com.example.model.MqttLogPacket
import com.example.model.MqttServerConfig
import com.example.model.PublishHistoryItem
import com.example.model.PublishPreset
import com.example.model.SubscriptionItem
import com.example.mqtt.MqttClientManager
import com.example.service.MqttBackgroundService
import com.example.util.ExcelExportHelper
import com.example.util.ExportPacketItem
import com.example.util.MqttTopicUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

class MqttAssistantViewModel(application: Application) : AndroidViewModel(application) {

    private val storage = MqttStorageRepository(application)

    private val _currentScreen = MutableStateFlow(AppScreen.LiveLogs)
    val currentScreen: StateFlow<AppScreen> = _currentScreen.asStateFlow()

    private val _toastEvent = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val toastEvent: SharedFlow<String> = _toastEvent.asSharedFlow()

    // --- Multi-Broker Cluster Management ---
    val brokerProfiles = MutableStateFlow<List<BrokerProfile>>(storage.loadBrokerProfiles())
    val activeBrokerId = MutableStateFlow<String>(storage.loadActiveBrokerId())

    // --- Production Connection State & Auto-Reconnect ---
    val connectionState = MutableStateFlow(MqttConnectionState.DISCONNECTED)
    val reconnectAttempt = MutableStateFlow(0)
    val reconnectCountdown = MutableStateFlow(0)
    private var reconnectJob: Job? = null
    private val packetSeqCounter = AtomicLong(0L)
    private var networkCallback: ConnectivityManager.NetworkCallback? = null

    val isForegroundKeepAliveRunning = MutableStateFlow(false)

    // --- Live Packet Log States (Backed by SQLite Database, Chronological Order: Newest at Bottom) ---
    val livePackets = MutableStateFlow<List<MqttLogPacket>>(emptyList())
    val selectedTopicFilter = MutableStateFlow("全部主题")
    val selectedPacket = MutableStateFlow<MqttLogPacket?>(null)
    val searchQuery = MutableStateFlow("")
    val filterQos = MutableStateFlow<Int?>(null)
    val isRecordingPaused = MutableStateFlow(false)
    val payloadViewMode = MutableStateFlow("TEXT") // TEXT, HEX, JSON
    val logFilterQuery = MutableStateFlow("")
    val logSelectedCategory = MutableStateFlow("全部")
    val isJsonPrettyFormat = MutableStateFlow(true)
    val filterJsonOnly = MutableStateFlow(false)

    // --- PC-Grade Topic Filters (Persistent: Include / Exclude) ---
    val includeTopicFilters = MutableStateFlow<List<String>>(storage.loadIncludeTopicFilters())
    val excludeTopicFilters = MutableStateFlow<List<String>>(storage.loadExcludeTopicFilters())

    // --- Subscriptions State (Persistent) ---
    val subscriptions = MutableStateFlow<List<SubscriptionItem>>(storage.loadSubscriptions())
    val newSubTopic = MutableStateFlow("")
    val newSubQos = MutableStateFlow(0)
    val subSearchQuery = MutableStateFlow("")

    // --- Publish State (Persistent) ---
    val publishPresets = MutableStateFlow<List<PublishPreset>>(storage.loadPublishPresets())
    val activePresetId = MutableStateFlow<String?>("p0")
    val presetSearchQuery = MutableStateFlow("")

    val publishTopic = MutableStateFlow("college/breaker/control/THFC012CCCBDDC")
    val publishQos = MutableStateFlow(0)
    val publishRetain = MutableStateFlow(false)
    val publishFormat = MutableStateFlow("TEXT") // TEXT or HEX
    val publishPayload = MutableStateFlow("{\n  \"schema_version\": 1,\n  \"action\": \"set_config\",\n  \"config_reset\": false\n}")
    val isPublishing = MutableStateFlow(false)
    val publishFeedback = MutableStateFlow<String?>(null)
    val publishHistory = MutableStateFlow<List<PublishHistoryItem>>(emptyList())

    // --- Server & Node Configuration ---
    val serverConfig = MutableStateFlow(
        run {
            val initialActiveId = storage.loadActiveBrokerId()
            val initialProfiles = storage.loadBrokerProfiles()
            val initialBroker = initialProfiles.find { it.id == initialActiveId } ?: initialProfiles.firstOrNull()
            if (initialBroker != null) {
                MqttServerConfig(
                    activeProfileId = initialBroker.id,
                    host = initialBroker.host,
                    port = initialBroker.port,
                    clientId = initialBroker.clientId,
                    username = initialBroker.username,
                    password = initialBroker.password,
                    protocol = initialBroker.protocol,
                    cleanSession = initialBroker.cleanSession,
                    tlsEnabled = initialBroker.tlsEnabled,
                    keepAlive = initialBroker.keepAlive,
                    autoReconnect = storage.loadAutoReconnect(),
                    reconnectIntervalSeconds = storage.loadReconnectInterval(),
                    maxReconnectAttempts = storage.loadMaxReconnectAttempts(),
                    autoRotate = storage.loadAutoRotate(),
                    bufferThreshold = storage.loadBufferThreshold(),
                    backgroundKeepAliveEnabled = storage.loadBackgroundKeepAlive(),
                    wakeLockEnabled = storage.loadWakeLock(),
                    autoStartEnabled = storage.loadAutoStartEnabled(),
                    processGuardEnabled = storage.loadProcessGuardEnabled()
                )
            } else {
                MqttServerConfig(
                    activeProfileId = "",
                    host = "",
                    port = 1883,
                    clientId = "android_client_" + (1000..9999).random(),
                    username = "",
                    password = "",
                    protocol = "MQTT 3.1.1",
                    cleanSession = true,
                    tlsEnabled = false,
                    keepAlive = 60,
                    autoReconnect = storage.loadAutoReconnect(),
                    reconnectIntervalSeconds = storage.loadReconnectInterval(),
                    maxReconnectAttempts = storage.loadMaxReconnectAttempts(),
                    autoRotate = storage.loadAutoRotate(),
                    bufferThreshold = storage.loadBufferThreshold(),
                    backgroundKeepAliveEnabled = storage.loadBackgroundKeepAlive(),
                    wakeLockEnabled = storage.loadWakeLock(),
                    autoStartEnabled = storage.loadAutoStartEnabled(),
                    processGuardEnabled = storage.loadProcessGuardEnabled()
                )
            }
        }
    )
    val isPasswordVisible = MutableStateFlow(false)
    val isSavingSettings = MutableStateFlow(false)
    private var isManualDisconnecting = false
    private val incomingPacketChannel = Channel<MqttLogPacket>(capacity = Channel.UNLIMITED)

    init {
        // 异步从 SQLite 加载历史最近报文，消除类构造期间主线程磁盘 I/O 阻塞
        viewModelScope.launch(Dispatchers.IO) {
            val cached = storage.loadRecentPackets(300).reversed()
            withContext(Dispatchers.Main) {
                livePackets.value = cached
            }
        }
        startPacketBatchCollector()
        setupMqttCallbacks()
        registerNetworkCallback()
        refreshStorageStats()
        // 严密校验：若 Broker 节点为 0 或主机为空，绝不发起连接和无限重连循环
        if (brokerProfiles.value.isNotEmpty() && serverConfig.value.host.isNotBlank()) {
            connectToBroker()
            if (serverConfig.value.backgroundKeepAliveEnabled) {
                val brokerHost = "${serverConfig.value.host}:${serverConfig.value.port}"
                MqttBackgroundService.startKeepAlive(application, brokerHost)
                isForegroundKeepAliveRunning.value = true
            }
        } else {
            connectionState.value = MqttConnectionState.DISCONNECTED
            serverConfig.update { it.copy(isConnected = false) }
        }
    }

    /**
     * 高性能报文聚合通道（40ms 窗口批处理）：
     * 无论瞬间并发冲刷多少条报文，聚合在 40ms 窗口（对应手机 60Hz/120Hz 丝滑刷新率）内合并更新：
     * 1. 批量触发一次 StateFlow 发射，彻底消除 Compose 重组雪崩和滚动条剧烈打断闪屏；
     * 2. 单次事务批量写入 SQLite 数据库，消除磁盘锁竞争与卡顿；
     * 3. 批量聚合订阅主题消息计数。
     */
    private fun startPacketBatchCollector() {
        viewModelScope.launch(Dispatchers.Default) {
            val batch = mutableListOf<MqttLogPacket>()
            var lastEmitTime = 0L
            while (isActive) {
                val firstPacket = incomingPacketChannel.receiveCatching().getOrNull() ?: break
                batch.add(firstPacket)

                // 抽干当前通道中已排队的消息
                while (batch.size < 200) {
                    val next = incomingPacketChannel.tryReceive().getOrNull() ?: break
                    batch.add(next)
                }

                // 60Hz 帧率边界平滑对齐：
                // 若空闲已久（距上次发射 >= 16ms），立即 0ms 发射，毫无迟滞感；
                // 若处于高并发密集冲刷期（距上次发射 < 16ms），微让步对齐单帧渲染节拍并吸收新消息，
                // 彻底杜绝主线程每秒上百次重组雪崩与滚动掉帧闪屏！
                val now = System.currentTimeMillis()
                val elapsed = now - lastEmitTime
                if (elapsed < 16) {
                    val waitMs = 16 - elapsed
                    delay(waitMs)
                    while (batch.size < 200) {
                        val next = incomingPacketChannel.tryReceive().getOrNull() ?: break
                        batch.add(next)
                    }
                }
                lastEmitTime = System.currentTimeMillis()

                val currentBatch = batch.toList()
                batch.clear()
                val maxBuffer = serverConfig.value.bufferThreshold

                // 1. 批量更新 livePackets 与订阅条目计数 (主线程一次性发射)
                withContext(Dispatchers.Main) {
                    livePackets.update { current ->
                        (current + currentBatch).takeLast(maxBuffer)
                    }

                    val topicCounts = currentBatch.groupBy { it.topic }
                    subscriptions.update { list ->
                        list.map { sub ->
                            if (sub.isEnabled) {
                                val matchedCount = topicCounts.entries.sumOf { (topic, packets) ->
                                    if (MqttTopicUtil.matchesMqttTopic(sub.topic, topic)) packets.size else 0
                                }
                                if (matchedCount > 0) {
                                    sub.copy(
                                        msgCount = sub.msgCount + matchedCount,
                                        lastTimeText = "刚刚"
                                    )
                                } else sub
                            } else sub
                        }
                    }
                }

                // 2. 异步批量单事务入库 SQLite
                withContext(Dispatchers.IO) {
                    storage.savePackets(currentBatch, maxBuffer)
                }
                refreshStorageStats()

                // 3. 更新通知栏
                if (MqttBackgroundService.isRunning) {
                    val lastPacket = currentBatch.lastOrNull()
                    if (lastPacket != null) {
                        val host = serverConfig.value.host
                        val brokerLabel = if (host.isNotBlank()) "${host}:${serverConfig.value.port}" else ""
                        MqttBackgroundService.updateNotification(
                            context = getApplication(),
                            brokerHost = brokerLabel,
                            count = packetSeqCounter.get(),
                            latestTopic = lastPacket.topic
                        )
                    }
                }
            }
        }
    }

    private fun registerNetworkCallback() {
        try {
            val cm = getApplication<Application>().getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            val request = NetworkRequest.Builder()
                .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                .build()
            val cb = object : ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: Network) {
                    Log.d("MqttAssistantViewModel", "NetworkCallback: Internet restored, checking connection...")
                    if (!serverConfig.value.isConnected && !isManualDisconnecting && serverConfig.value.autoReconnect) {
                        viewModelScope.launch(Dispatchers.Main) {
                            startAutoReconnectLoop(isImmediate = true)
                        }
                    }
                }
            }
            networkCallback = cb
            cm?.registerNetworkCallback(request, cb)
        } catch (e: Exception) {
            Log.w("MqttAssistantViewModel", "Failed to register NetworkCallback", e)
        }
    }

    private fun setupMqttCallbacks() {
        MqttClientManager.onMessageReceived = { topic, qos, payloadBytes, retain ->
            val timeStr = SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault()).format(Date())
            val payloadString = try {
                String(payloadBytes, Charsets.UTF_8)
            } catch (e: Exception) {
                payloadBytes.joinToString(" ") { "%02X".format(it) }
            }
            val seqNumber = packetSeqCounter.incrementAndGet()
            val seq = "#%04d".format(seqNumber)

            val matchingSub = subscriptions.value.firstOrNull { sub ->
                MqttTopicUtil.matchesMqttTopic(sub.topic, topic)
            }
            val dotColor = matchingSub?.dotColorHex ?: 0xFF10B981
            val cat = matchingSub?.name?.ifBlank { null } ?: topic.substringBefore('/')

            val packet = MqttLogPacket(
                id = UUID.randomUUID().toString(),
                topic = topic,
                qos = qos,
                packetSeq = seq,
                timestamp = timeStr,
                payload = payloadString,
                devInfo = "SUB · ${payloadBytes.size}B" + if (retain) " · Retain" else "",
                sizeText = "${payloadBytes.size}B",
                category = cat,
                dotColorHex = dotColor
            )

            // 发送到高性能聚合管道，无阻塞极速返回
            incomingPacketChannel.trySend(packet)
        }

        MqttClientManager.onConnectionStateChanged = { isConn, cause ->
            if (isConn) {
                isManualDisconnecting = false
                connectionState.value = MqttConnectionState.CONNECTED
                serverConfig.update { it.copy(isConnected = true) }
                reconnectAttempt.value = 0
                reconnectCountdown.value = 0
                persistCurrentActiveBrokerProfile()
                // Auto-subscribe all enabled subscriptions on the connected broker using batch API
                viewModelScope.launch {
                    val activeSubs = subscriptions.value.filter { it.isEnabled }
                    MqttClientManager.subscribeBatch(activeSubs.map { it.topic to it.qos })
                }
                // Auto start foreground keepalive service for persistent background connection
                if (serverConfig.value.backgroundKeepAliveEnabled) {
                    val brokerHost = "${serverConfig.value.host}:${serverConfig.value.port}"
                    MqttBackgroundService.startKeepAlive(getApplication(), brokerHost)
                    MqttBackgroundService.updateNotification(
                        context = getApplication(),
                        brokerHost = brokerHost,
                        count = packetSeqCounter.get(),
                        latestTopic = null
                    )
                    isForegroundKeepAliveRunning.value = true
                }
            } else {
                connectionState.value = MqttConnectionState.DISCONNECTED
                serverConfig.update { it.copy(isConnected = false) }
                // 只要非用户主动断开且开启了自动重连，立即以 isImmediate = true 毫秒级自愈发起重连！
                if (!isManualDisconnecting && serverConfig.value.autoReconnect) {
                    startAutoReconnectLoop(isImmediate = true)
                }
            }
        }
    }

    fun connectToBroker() {
        isManualDisconnecting = false
        if (brokerProfiles.value.isEmpty() || serverConfig.value.host.isBlank()) {
            reconnectJob?.cancel()
            reconnectCountdown.value = 0
            connectionState.value = MqttConnectionState.DISCONNECTED
            serverConfig.update { it.copy(isConnected = false) }
            return
        }
        reconnectJob?.cancel()
        reconnectCountdown.value = 0
        viewModelScope.launch {
            connectionState.value = MqttConnectionState.CONNECTING
            val result = MqttClientManager.connect(serverConfig.value)
            if (result.isSuccess) {
                val activeCount = subscriptions.value.count { it.isEnabled }
                showToast("已连接至 ${serverConfig.value.host}:${serverConfig.value.port} (已激活 $activeCount 个主题)")
            } else {
                connectionState.value = MqttConnectionState.DISCONNECTED
                serverConfig.update { it.copy(isConnected = false) }
                val errorMsg = MqttClientManager.getReadableErrorMessage(
                    result.exceptionOrNull() ?: Exception("连接失败"),
                    serverConfig.value.host,
                    serverConfig.value.port,
                    isTls = serverConfig.value.tlsEnabled
                )
                showToast("连接异常: $errorMsg")
                if (serverConfig.value.autoReconnect && brokerProfiles.value.isNotEmpty() && serverConfig.value.host.isNotBlank()) {
                    startAutoReconnectLoop()
                }
            }
        }
    }

    // Navigation
    fun navigateTo(screen: AppScreen) {
        _currentScreen.value = screen
    }

    fun showToast(message: String) {
        _toastEvent.tryEmit(message)
    }

    // ==========================================
    // Multi-Broker Management
    // ==========================================

    fun selectBroker(brokerId: String) {
        val broker = brokerProfiles.value.find { it.id == brokerId } ?: return
        activeBrokerId.value = broker.id
        storage.saveActiveBrokerId(broker.id)
        serverConfig.update {
            it.copy(
                activeProfileId = broker.id,
                host = broker.host,
                port = broker.port,
                clientId = broker.clientId,
                username = broker.username,
                password = broker.password,
                protocol = broker.protocol,
                cleanSession = broker.cleanSession,
                tlsEnabled = broker.tlsEnabled,
                keepAlive = broker.keepAlive
            )
        }
        showToast("已切换节点: ${broker.name} (${broker.host}:${broker.port})，正在连接...")
        reconnectJob?.cancel()
        reconnectAttempt.value = 0
        reconnectCountdown.value = 0
        connectToBroker()
    }

    fun switchToNextBroker() {
        val profiles = brokerProfiles.value
        if (profiles.isEmpty()) return
        if (profiles.size == 1) {
            showToast("当前节点: ${profiles.first().name} (${profiles.first().host})")
            return
        }
        val currentIndex = profiles.indexOfFirst { it.id == activeBrokerId.value }
        val nextIndex = (currentIndex + 1).coerceAtLeast(0) % profiles.size
        selectBroker(profiles[nextIndex].id)
    }

    fun persistCurrentActiveBrokerProfile() {
        val activeId = activeBrokerId.value
        storage.saveActiveBrokerId(activeId)
        val currentProfiles = brokerProfiles.value.toMutableList()
        val activeIndex = currentProfiles.indexOfFirst { it.id == activeId }
        if (activeIndex >= 0) {
            currentProfiles[activeIndex] = currentProfiles[activeIndex].copy(
                host = serverConfig.value.host,
                port = serverConfig.value.port,
                clientId = serverConfig.value.clientId,
                username = serverConfig.value.username,
                password = serverConfig.value.password,
                protocol = serverConfig.value.protocol,
                cleanSession = serverConfig.value.cleanSession,
                tlsEnabled = serverConfig.value.tlsEnabled,
                keepAlive = serverConfig.value.keepAlive
            )
            brokerProfiles.value = currentProfiles
            storage.saveBrokerProfiles(currentProfiles)
        }
    }

    fun saveOrUpdateBroker(broker: BrokerProfile) {
        brokerProfiles.update { currentList ->
            val index = currentList.indexOfFirst { it.id == broker.id }
            if (index >= 0) {
                currentList.toMutableList().apply { set(index, broker) }
            } else {
                currentList + broker
            }
        }
        storage.saveBrokerProfiles(brokerProfiles.value)

        // If this broker is currently active, sync serverConfig and reconnect
        if (broker.id == activeBrokerId.value) {
            serverConfig.update {
                it.copy(
                    host = broker.host,
                    port = broker.port,
                    clientId = broker.clientId,
                    username = broker.username,
                    password = broker.password,
                    protocol = broker.protocol,
                    cleanSession = broker.cleanSession,
                    tlsEnabled = broker.tlsEnabled,
                    keepAlive = broker.keepAlive
                )
            }
            triggerManualReconnect()
        }
        showToast("已保存 Broker 节点: ${broker.name}")
    }

    fun deleteBroker(brokerId: String) {
        val isDeletingActive = brokerId == activeBrokerId.value
        val remaining = brokerProfiles.value.filter { it.id != brokerId }
        brokerProfiles.value = remaining
        storage.saveBrokerProfiles(remaining)
        if (remaining.isEmpty()) {
            activeBrokerId.value = ""
            storage.saveActiveBrokerId("")
            serverConfig.update { it.copy(activeProfileId = "", host = "", isConnected = false) }
            reconnectJob?.cancel()
            reconnectCountdown.value = 0
            connectionState.value = MqttConnectionState.DISCONNECTED
            viewModelScope.launch {
                MqttClientManager.disconnect()
            }
            showToast("已清空所有 Broker 节点，连接引擎已停止")
        } else if (isDeletingActive) {
            selectBroker(remaining.first().id)
        } else {
            showToast("已删除 Broker 节点")
        }
    }

    // ==========================================
    // Publish Console & Presets Management
    // ==========================================

    fun selectPreset(preset: PublishPreset) {
        activePresetId.value = preset.id
        publishTopic.value = preset.topic
        publishQos.value = preset.qos
        publishRetain.value = preset.retain
        publishPayload.value = preset.payload
        showToast("已载入预设: ${preset.name}")
    }

    fun directPublishPreset(preset: PublishPreset) {
        val validation = MqttTopicUtil.validatePublishTopic(preset.topic)
        if (!validation.isValid) {
            showToast("发布失败: ${validation.errorMessage}")
            return
        }

        viewModelScope.launch {
            val payloadBytes = preset.payload.toByteArray(Charsets.UTF_8)
            val result = MqttClientManager.publish(
                topic = preset.topic,
                payload = payloadBytes,
                qos = preset.qos,
                retain = preset.retain
            )

            val timeStr = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
            val dotColor = when (preset.qos) {
                0 -> 0xFFF59E0B
                1 -> 0xFF10B981
                else -> 0xFF3980F4
            }
            val newHistory = PublishHistoryItem(
                id = UUID.randomUUID().toString(),
                topic = preset.topic,
                qos = preset.qos,
                timestamp = timeStr,
                payload = preset.payload,
                dotColorHex = dotColor
            )
            publishHistory.update { listOf(newHistory) + it }

            val seqNumber = packetSeqCounter.incrementAndGet()
            val packet = MqttLogPacket(
                id = UUID.randomUUID().toString(),
                topic = preset.topic,
                qos = preset.qos,
                packetSeq = "#%04d".format(seqNumber),
                timestamp = SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault()).format(Date()),
                payload = preset.payload,
                devInfo = "PUB · ${payloadBytes.size}B" + if (result.isSuccess) " · 已送达" else " · 发送失败",
                sizeText = "${payloadBytes.size}B",
                category = preset.name.ifBlank { preset.topic.substringBefore('/') },
                dotColorHex = dotColor
            )
            livePackets.update { (it + packet).takeLast(serverConfig.value.bufferThreshold) }
            storage.savePacket(packet, serverConfig.value.bufferThreshold)

            // Match against subscriptions
            subscriptions.update { list ->
                list.map { sub ->
                    if (sub.isEnabled && MqttTopicUtil.matchesMqttTopic(sub.topic, preset.topic)) {
                        sub.copy(
                            msgCount = sub.msgCount + 1,
                            lastTimeText = "刚刚"
                        )
                    } else sub
                }
            }

            if (result.isSuccess) {
                showToast("已发送: ${preset.name}")
            } else {
                showToast("发送异常: ${result.exceptionOrNull()?.message ?: "未连接 Broker"}")
            }
        }
    }

    fun saveOrUpdatePreset(preset: PublishPreset) {
        val validation = MqttTopicUtil.validatePublishTopic(preset.topic)
        if (!validation.isValid) {
            showToast("发布配置错误: ${validation.errorMessage}")
            return
        }
        publishPresets.update { currentList ->
            val existingIndex = currentList.indexOfFirst { it.id == preset.id }
            if (existingIndex >= 0) {
                currentList.toMutableList().apply { set(existingIndex, preset) }
            } else {
                listOf(preset) + currentList
            }
        }
        storage.savePublishPresets(publishPresets.value)
        showToast("已持久化保存配置: ${preset.name}")
    }

    fun deletePreset(presetId: String) {
        publishPresets.update { it.filter { p -> p.id != presetId } }
        storage.savePublishPresets(publishPresets.value)
        showToast("已删除发布配置")
    }

    fun addPreset(name: String) {
        val newPreset = PublishPreset(
            id = UUID.randomUUID().toString(),
            name = name.ifBlank { "预设_${System.currentTimeMillis() % 1000}" },
            topic = publishTopic.value,
            qos = publishQos.value,
            retain = publishRetain.value,
            payload = publishPayload.value
        )
        publishPresets.update { listOf(newPreset) + it }
        storage.savePublishPresets(publishPresets.value)
        activePresetId.value = newPreset.id
        showToast("已保存为新预设: ${newPreset.name}")
    }

    fun formatJson() {
        val current = publishPayload.value.trim()
        try {
            if (current.startsWith("{") && current.endsWith("}")) {
                val obj = JSONObject(current)
                publishPayload.value = obj.toString(2)
                showToast("JSON 格式化成功")
            } else if (current.startsWith("[") && current.endsWith("]")) {
                val array = JSONArray(current)
                publishPayload.value = array.toString(2)
                showToast("JSON 格式化成功")
            } else {
                showToast("当前载荷非 JSON 格式")
            }
        } catch (e: Exception) {
            showToast("JSON 格式错误，请检查语法")
        }
    }

    fun publishMessage() {
        val topic = publishTopic.value.trim()
        if (topic.isBlank()) {
            showToast("请输入有效发布主题")
            return
        }
        val validation = MqttTopicUtil.validatePublishTopic(topic)
        if (!validation.isValid) {
            showToast("发布主题格式错误: ${validation.errorMessage}")
            return
        }

        viewModelScope.launch {
            isPublishing.value = true
            publishFeedback.value = "正在发送报文..."
            val payloadBytes = publishPayload.value.toByteArray(Charsets.UTF_8)
            val result = MqttClientManager.publish(
                topic = topic,
                payload = payloadBytes,
                qos = publishQos.value,
                retain = publishRetain.value
            )

            // Add to history
            val timeStr = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
            val dotColor = when (publishQos.value) {
                0 -> 0xFFF59E0B
                1 -> 0xFF10B981
                else -> 0xFF3980F4
            }
            val newHistory = PublishHistoryItem(
                id = UUID.randomUUID().toString(),
                topic = topic,
                qos = publishQos.value,
                timestamp = timeStr,
                payload = publishPayload.value.replace("\n", "").replace(" ", ""),
                dotColorHex = dotColor
            )
            publishHistory.update { listOf(newHistory) + it }

            // Also add to live logs
            val seqNumber = packetSeqCounter.incrementAndGet()
            val packet = MqttLogPacket(
                id = UUID.randomUUID().toString(),
                topic = topic,
                qos = publishQos.value,
                packetSeq = "#%04d".format(seqNumber),
                timestamp = SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault()).format(Date()),
                payload = publishPayload.value,
                devInfo = "PUB · ${payloadBytes.size}B" + if (result.isSuccess) " · 已送达" else " · 发送失败",
                sizeText = "${payloadBytes.size}B",
                category = topic.substringBefore('/'),
                dotColorHex = dotColor
            )
            livePackets.update { (it + packet).takeLast(serverConfig.value.bufferThreshold) }
            storage.savePacket(packet, serverConfig.value.bufferThreshold)

            if (result.isSuccess) {
                // If any enabled subscription matches the published topic, update stats immediately
                subscriptions.update { list ->
                    list.map { sub ->
                        if (sub.isEnabled && MqttTopicUtil.matchesMqttTopic(sub.topic, topic)) {
                            sub.copy(
                                msgCount = sub.msgCount + 1,
                                lastTimeText = "刚刚"
                            )
                        } else sub
                    }
                }
                publishFeedback.value = "已送达 Broker"
                showToast("发布成功: $topic")
            } else {
                publishFeedback.value = "发送失败"
                showToast("发布异常: ${result.exceptionOrNull()?.message ?: "连接未建立"}")
            }

            delay(1000)
            isPublishing.value = false
            publishFeedback.value = null
        }
    }

    fun clearPublishHistory() {
        publishHistory.value = emptyList()
        showToast("已清空近期发布记录")
    }

    fun resendFromHistory(item: PublishHistoryItem) {
        publishTopic.value = item.topic
        publishQos.value = item.qos
        publishPayload.value = item.payload
        showToast("已载入并准备重发: ${item.topic}")
    }

    // ==========================================
    // Subscriptions Management (Persistent & Real)
    // ==========================================

    fun toggleSubscription(id: String) {
        subscriptions.update { list ->
            list.map {
                if (it.id == id) {
                    val newState = !it.isEnabled
                    viewModelScope.launch {
                        if (newState) {
                            MqttClientManager.subscribe(it.topic, it.qos)
                        } else {
                            MqttClientManager.unsubscribe(it.topic)
                        }
                    }
                    showToast(if (newState) "已激活订阅: ${it.topic}" else "已暂停订阅: ${it.topic}")
                    it.copy(isEnabled = newState)
                } else it
            }
        }
        storage.saveSubscriptions(subscriptions.value)
    }

    fun removeSubscription(id: String) {
        val item = subscriptions.value.find { it.id == id }
        if (item != null) {
            viewModelScope.launch {
                MqttClientManager.unsubscribe(item.topic)
            }
            showToast("已删除订阅: ${item.topic}")
        }
        subscriptions.update { it.filter { sub -> sub.id != id } }
        storage.saveSubscriptions(subscriptions.value)
    }

    fun saveOrUpdateSubscription(item: SubscriptionItem, oldTopic: String? = null) {
        val validation = MqttTopicUtil.validateSubscriptionTopic(item.topic)
        if (!validation.isValid) {
            showToast("订阅配置错误: ${validation.errorMessage}")
            return
        }

        if (oldTopic != null && oldTopic != item.topic) {
            viewModelScope.launch {
                MqttClientManager.unsubscribe(oldTopic)
            }
        }

        subscriptions.update { currentList ->
            val existingIndex = currentList.indexOfFirst { it.id == item.id }
            if (existingIndex >= 0) {
                currentList.toMutableList().apply { set(existingIndex, item) }
            } else {
                listOf(item) + currentList
            }
        }
        storage.saveSubscriptions(subscriptions.value)

        viewModelScope.launch {
            if (item.isEnabled) {
                val res = MqttClientManager.subscribe(item.topic, item.qos)
                if (res.isSuccess) {
                    showToast("已持久化保存并在 Broker 激活订阅: ${item.topic} (QoS ${item.qos})")
                } else {
                    showToast("订阅失败: ${res.exceptionOrNull()?.message}")
                }
            } else {
                MqttClientManager.unsubscribe(item.topic)
                showToast("已保存订阅配置 (已暂停接收并退订)")
            }
        }
    }

    fun testPublishLoopback(targetSub: SubscriptionItem? = null) {
        if (!serverConfig.value.isConnected) {
            showToast("请先等待或点击顶部连接 Broker 再进行自测")
            return
        }
        val target = targetSub ?: subscriptions.value.firstOrNull { it.isEnabled }
        val targetTopic = if (target != null) {
            val raw = target.topic.trim()
            when {
                raw == "#" -> "testtopic/probe"
                raw.endsWith("/#") -> raw.removeSuffix("/#") + "/probe"
                raw.contains("/+/") -> raw.replace("/+/", "/probe/")
                raw.endsWith("/+") -> raw.removeSuffix("/+") + "/probe"
                else -> raw
            }
        } else {
            "testtopic/probe"
        }
        val timeNow = SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault()).format(Date())
        val testPayload = """{"event":"probe_test","target_topic":"$targetTopic","time":"$timeNow"}"""
        viewModelScope.launch {
            val result = MqttClientManager.publish(
                topic = targetTopic,
                payload = testPayload.toByteArray(Charsets.UTF_8),
                qos = target?.qos ?: 0,
                retain = false
            )
            if (result.isSuccess) {
                // Instantly register stats feedback on matching subscription
                subscriptions.update { list ->
                    list.map { sub ->
                        if (sub.isEnabled && MqttTopicUtil.matchesMqttTopic(sub.topic, targetTopic)) {
                            sub.copy(
                                msgCount = sub.msgCount + 1,
                                lastTimeText = "刚刚"
                            )
                        } else sub
                    }
                }
                showToast("自测报文已发送至 $targetTopic")
            } else {
                showToast("自测发送异常: ${result.exceptionOrNull()?.message}")
            }
        }
    }

    fun addSubscription(
        customTopic: String? = null,
        qos: Int? = null,
        dotColor: Long? = null,
        name: String = "",
        retainHandling: Int = 0
    ) {
        val topic = (customTopic ?: newSubTopic.value).trim()
        val targetQos = qos ?: newSubQos.value
        if (topic.isBlank()) {
            showToast("请输入订阅主题 (例如 testtopic/#)")
            return
        }
        val validation = MqttTopicUtil.validateSubscriptionTopic(topic)
        if (!validation.isValid) {
            showToast("订阅格式错误: ${validation.errorMessage}")
            return
        }
        val defaultColors = listOf(0xFF10B981, 0xFF2563EB, 0xFF8B5CF6, 0xFFF59E0B, 0xFF06B6D4)
        val color = dotColor ?: defaultColors[subscriptions.value.size % defaultColors.size]

        val newItem = SubscriptionItem(
            id = UUID.randomUUID().toString(),
            topic = topic,
            qos = targetQos,
            msgCount = 0,
            lastTimeText = "等待数据",
            isEnabled = true,
            dotColorHex = color,
            name = name.trim(),
            retainHandling = retainHandling
        )
        subscriptions.update { listOf(newItem) + it }
        storage.saveSubscriptions(subscriptions.value)
        newSubTopic.value = ""

        viewModelScope.launch {
            val res = MqttClientManager.subscribe(newItem.topic, newItem.qos)
            if (res.isSuccess) {
                showToast("已添加并在 Broker 激活订阅: $topic (QoS $targetQos)")
            } else {
                showToast("已保存订阅: $topic，待网络连接后自动生效")
            }
        }
    }

    // ==========================================
    // PC-Grade Include / Exclude Filter Rules
    // ==========================================

    fun addIncludeTopicFilter(pattern: String) {
        val trimmed = pattern.trim()
        if (trimmed.isBlank()) return
        val valid = MqttTopicUtil.validateSubscriptionTopic(trimmed)
        if (!valid.isValid) {
            showToast("包含主题格式错误: ${valid.errorMessage}")
            return
        }
        if (!includeTopicFilters.value.contains(trimmed)) {
            val next = includeTopicFilters.value + trimmed
            includeTopicFilters.value = next
            storage.saveIncludeTopicFilters(next)
            showToast("已添加包含条件: $trimmed")
        }
    }

    fun removeIncludeTopicFilter(index: Int) {
        val list = includeTopicFilters.value.toMutableList()
        if (index in list.indices) {
            val removed = list.removeAt(index)
            includeTopicFilters.value = list
            storage.saveIncludeTopicFilters(list)
            showToast("已移除包含条件: $removed")
        }
    }

    fun clearIncludeTopicFilters() {
        includeTopicFilters.value = emptyList()
        storage.saveIncludeTopicFilters(emptyList())
        showToast("已清空所有包含条件")
    }

    fun addExcludeTopicFilter(pattern: String) {
        val trimmed = pattern.trim()
        if (trimmed.isBlank()) return
        val valid = MqttTopicUtil.validateSubscriptionTopic(trimmed)
        if (!valid.isValid) {
            showToast("排除主题格式错误: ${valid.errorMessage}")
            return
        }
        if (!excludeTopicFilters.value.contains(trimmed)) {
            val next = excludeTopicFilters.value + trimmed
            excludeTopicFilters.value = next
            storage.saveExcludeTopicFilters(next)
            showToast("已添加排除条件: $trimmed")
        }
    }

    fun removeExcludeTopicFilter(index: Int) {
        val list = excludeTopicFilters.value.toMutableList()
        if (index in list.indices) {
            val removed = list.removeAt(index)
            excludeTopicFilters.value = list
            storage.saveExcludeTopicFilters(list)
            showToast("已移除排除条件: $removed")
        }
    }

    fun clearExcludeTopicFilters() {
        excludeTopicFilters.value = emptyList()
        storage.saveExcludeTopicFilters(emptyList())
        showToast("已清空所有排除条件")
    }

    // ==========================================
    // Live Packets Log Management
    // ==========================================

    fun selectTopicFilter(topic: String) {
        selectedTopicFilter.value = topic
    }

    fun selectPacket(packet: MqttLogPacket) {
        selectedPacket.value = packet
    }

    fun closePacketDetail() {
        selectedPacket.value = null
    }

    fun clearPackets() {
        livePackets.value = emptyList()
        selectedPacket.value = null
        showToast("已清空实时报文日志")
    }

    fun togglePauseRecording() {
        isRecordingPaused.value = !isRecordingPaused.value
        showToast(if (isRecordingPaused.value) "自动滚动已暂停 (可自由滑动浏览，后台正常接收)" else "已恢复自动向下滚动 (吸附最新)")
    }

    fun toggleStreamPause() {
        togglePauseRecording()
    }

    fun clearLogStream() {
        clearPackets()
    }

    fun toggleJsonPretty() {
        isJsonPrettyFormat.value = !isJsonPrettyFormat.value
    }

    fun toggleFilterJsonOnly() {
        filterJsonOnly.value = !filterJsonOnly.value
    }

    fun resendLogPacket(packet: MqttLogPacket) {
        publishTopic.value = packet.topic
        publishQos.value = packet.qos
        publishPayload.value = packet.payload
        _currentScreen.value = AppScreen.Publish
        showToast("已带入发布台并准备重发: ${packet.topic}")
    }

    fun togglePauseAllSubscriptions() {
        val anyActive = subscriptions.value.any { it.isEnabled }
        val targetState = !anyActive
        subscriptions.update { list ->
            list.map { sub ->
                viewModelScope.launch {
                    if (targetState) {
                        MqttClientManager.subscribe(sub.topic, sub.qos)
                    } else {
                        MqttClientManager.unsubscribe(sub.topic)
                    }
                }
                sub.copy(isEnabled = targetState)
            }
        }
        storage.saveSubscriptions(subscriptions.value)
        showToast(if (targetState) "已恢复全部订阅" else "已暂停全部订阅")
    }

    fun resetSubscriptionCounts() {
        subscriptions.update { list ->
            list.map { it.copy(msgCount = 0) }
        }
        storage.saveSubscriptions(subscriptions.value)
        showToast("已重置所有主题接收计数")
    }

    fun setFilterQos(qos: Int?) {
        filterQos.value = qos
    }

    fun setSearchQuery(query: String) {
        searchQuery.value = query
    }

    fun setPayloadViewMode(mode: String) {
        payloadViewMode.value = mode
    }

    // ==========================================
    // Server Config Controls
    // ==========================================

    fun updateHost(host: String) {
        serverConfig.update { it.copy(host = host) }
    }

    fun updatePort(portStr: String) {
        val p = portStr.toIntOrNull() ?: 1883
        serverConfig.update { it.copy(port = p) }
    }

    fun updateKeepAlive(kaStr: String) {
        val ka = kaStr.toIntOrNull() ?: 60
        serverConfig.update { it.copy(keepAlive = ka) }
    }

    fun updateClientId(id: String) {
        serverConfig.update { it.copy(clientId = id) }
    }

    fun generateRandomClientId() {
        val randomId = "client_mobile_" + UUID.randomUUID().toString().take(6)
        serverConfig.update { it.copy(clientId = randomId) }
        showToast("已生成客户端标识: $randomId")
    }

    fun updateUsername(u: String) {
        serverConfig.update { it.copy(username = u) }
    }

    fun updatePassword(p: String) {
        serverConfig.update { it.copy(password = p) }
    }

    fun updateProtocol(proto: String) {
        serverConfig.update { it.copy(protocol = proto) }
    }

    fun toggleCleanSession() {
        serverConfig.update { it.copy(cleanSession = !it.cleanSession) }
    }

    fun toggleAutoReconnect() {
        val next = !serverConfig.value.autoReconnect
        serverConfig.update { it.copy(autoReconnect = next) }
        storage.saveAutoReconnect(next)
    }

    fun toggleTls() {
        val next = !serverConfig.value.tlsEnabled
        val currentPort = serverConfig.value.port
        val newPort = if (next && currentPort == 1883) {
            8883
        } else if (!next && currentPort == 8883) {
            1883
        } else {
            currentPort
        }
        serverConfig.update { it.copy(tlsEnabled = next, port = newPort) }
        persistCurrentActiveBrokerProfile()
        if (next) {
            showToast("已启用 TLS 加密传输 (已智能联动安全端口 $newPort)")
        } else {
            showToast("已停用 TLS 加密传输 (已恢复普通端口 $newPort)")
        }
    }

    fun toggleAutoRotate() {
        val next = !serverConfig.value.autoRotate
        serverConfig.update { it.copy(autoRotate = next) }
        storage.saveAutoRotate(next)
    }

    fun updateBufferThreshold(thStr: String) {
        val th = thStr.toIntOrNull() ?: 10000
        serverConfig.update { it.copy(bufferThreshold = th) }
        storage.saveBufferThreshold(th)
    }

    fun refreshStorageStats() {
        viewModelScope.launch(Dispatchers.IO) {
            val count = storage.getPacketCount().toInt()
            val bytes = storage.getDatabaseSizeBytes(getApplication())
            val mb = bytes.toDouble() / (1024.0 * 1024.0)
            val formattedMb = Math.round(mb * 100.0) / 100.0
            withContext(Dispatchers.Main) {
                serverConfig.update { it.copy(usedSpaceMb = formattedMb, packetCount = count) }
            }
        }
    }

    fun triggerManualReconnect() {
        isManualDisconnecting = false
        reconnectJob?.cancel()
        reconnectAttempt.value = 0
        reconnectCountdown.value = 0
        if (brokerProfiles.value.isEmpty() || serverConfig.value.host.isBlank()) {
            showToast("暂无可用的 Broker 节点，请先添加节点")
            connectionState.value = MqttConnectionState.DISCONNECTED
            serverConfig.update { it.copy(isConnected = false) }
            return
        }
        viewModelScope.launch {
            connectionState.value = MqttConnectionState.CONNECTING
            showToast("正在连接至 ${serverConfig.value.host}:${serverConfig.value.port}...")
            val result = MqttClientManager.connect(serverConfig.value)
            if (result.isSuccess) {
                connectionState.value = MqttConnectionState.CONNECTED
                serverConfig.update { it.copy(isConnected = true) }
                reconnectAttempt.value = 0
                reconnectCountdown.value = 0
                val activeSubs = subscriptions.value.filter { it.isEnabled }
                activeSubs.forEach { sub ->
                    MqttClientManager.subscribe(sub.topic, sub.qos)
                }
                showToast("已连接 Broker，已同步恢复 ${activeSubs.size} 个主题订阅")
            } else {
                connectionState.value = MqttConnectionState.DISCONNECTED
                serverConfig.update { it.copy(isConnected = false) }
                val errorMsg = MqttClientManager.getReadableErrorMessage(
                    result.exceptionOrNull() ?: Exception("连接失败"),
                    serverConfig.value.host,
                    serverConfig.value.port,
                    isTls = serverConfig.value.tlsEnabled
                )
                showToast("连接失败: $errorMsg")
                if (serverConfig.value.autoReconnect && !isManualDisconnecting && brokerProfiles.value.isNotEmpty() && serverConfig.value.host.isNotBlank()) {
                    startAutoReconnectLoop(isImmediate = false)
                }
            }
        }
    }

    /**
     * 极速秒级自动重连自愈引擎：
     * 1. 首次掉线或外部网络恢复/切回前台时，0秒等待立即发起重连！毫秒级恢复长连接；
     * 2. 后续重试阶梯退避：第2次等1秒，第3次等2秒，最大封顶仅3秒（彻底废除过去 5s/10s 漫长无谓等待！）。
     */
    fun startAutoReconnectLoop(isImmediate: Boolean = false) {
        if (!serverConfig.value.autoReconnect || isManualDisconnecting || brokerProfiles.value.isEmpty() || serverConfig.value.host.isBlank()) {
            reconnectJob?.cancel()
            reconnectCountdown.value = 0
            connectionState.value = MqttConnectionState.DISCONNECTED
            return
        }
        reconnectJob?.cancel()
        reconnectJob = viewModelScope.launch {
            connectionState.value = MqttConnectionState.RECONNECTING
            reconnectAttempt.value += 1

            val waitSec = if (isImmediate || reconnectAttempt.value <= 1) {
                0
            } else when (reconnectAttempt.value) {
                2 -> 1
                3 -> 2
                else -> 3
            }

            if (waitSec > 0) {
                for (sec in waitSec downTo 1) {
                    reconnectCountdown.value = sec
                    delay(1000)
                }
            }
            reconnectCountdown.value = 0
            connectionState.value = MqttConnectionState.CONNECTING
            val result = MqttClientManager.connect(serverConfig.value)
            if (result.isSuccess) {
                val activeCount = subscriptions.value.count { it.isEnabled }
                showToast("连接已恢复，已同步 $activeCount 个主题订阅")
            } else {
                connectionState.value = MqttConnectionState.DISCONNECTED
                serverConfig.update { it.copy(isConnected = false) }
                if (serverConfig.value.autoReconnect && !isManualDisconnecting && brokerProfiles.value.isNotEmpty() && serverConfig.value.host.isNotBlank()) {
                    startAutoReconnectLoop(isImmediate = false)
                }
            }
        }
    }

    /**
     * 应用切回前台时即刻探活自愈：
     * 解决“最小化打开其他程序再回来每次都断开/重连”的问题，只要发现未连接瞬间发起重连，不让用户等待。
     */
    fun onAppResume() {
        if (!serverConfig.value.isConnected && !isManualDisconnecting && serverConfig.value.autoReconnect && brokerProfiles.value.isNotEmpty() && serverConfig.value.host.isNotBlank()) {
            Log.d("MqttAssistantViewModel", "onAppResume: app returned to foreground, probing immediate reconnect")
            startAutoReconnectLoop(isImmediate = true)
        }
    }

    fun toggleAutoStart(context: Context) {
        val next = !serverConfig.value.autoStartEnabled
        serverConfig.update { it.copy(autoStartEnabled = next) }
        storage.saveAutoStartEnabled(next)
        if (next) {
            showToast("已开启开机自启动")
            AutoStartUtil.openAutoStartSettings(context)
        } else {
            showToast("已关闭开机自启动")
        }
    }

    fun toggleProcessGuard() {
        val next = !serverConfig.value.processGuardEnabled
        serverConfig.update { it.copy(processGuardEnabled = next) }
        storage.saveProcessGuardEnabled(next)
        if (next) {
            showToast("已开启进程守护 (防杀自愈恢复)")
        } else {
            showToast("已关闭进程守护")
        }
    }

    fun disconnectBroker(isManual: Boolean = true) {
        if (isManual) {
            isManualDisconnecting = true
        }
        reconnectJob?.cancel()
        reconnectCountdown.value = 0
        viewModelScope.launch {
            MqttClientManager.disconnect()
        }
        connectionState.value = MqttConnectionState.DISCONNECTED
        serverConfig.update { it.copy(isConnected = false) }
        if (!isManual && serverConfig.value.autoReconnect) {
            startAutoReconnectLoop(isImmediate = true)
        } else {
            MqttBackgroundService.stopKeepAlive(getApplication())
            isForegroundKeepAliveRunning.value = false
            showToast("已断开与 Broker 的连接")
        }
    }

    fun toggleConnection() {
        if (connectionState.value == MqttConnectionState.CONNECTED) {
            disconnectBroker(isManual = true)
        } else {
            triggerManualReconnect()
        }
    }

    fun updateReconnectInterval(secStr: String) {
        val s = secStr.toIntOrNull()?.coerceIn(1, 60) ?: 5
        serverConfig.update { it.copy(reconnectIntervalSeconds = s) }
    }

    fun toggleBackgroundKeepAlive(context: Context) {
        val next = !serverConfig.value.backgroundKeepAliveEnabled
        serverConfig.update { it.copy(backgroundKeepAliveEnabled = next) }
        storage.saveBackgroundKeepAlive(next)
        if (next) {
            val brokerHost = "${serverConfig.value.host}:${serverConfig.value.port}"
            MqttBackgroundService.startKeepAlive(context, brokerHost)
            isForegroundKeepAliveRunning.value = true
            showToast("已启用后台常驻保活服务 (Foreground Service)")
        } else {
            MqttBackgroundService.stopKeepAlive(context)
            isForegroundKeepAliveRunning.value = false
            showToast("已停用后台常驻保活服务")
        }
    }

    fun toggleWakeLock() {
        val next = !serverConfig.value.wakeLockEnabled
        serverConfig.update { it.copy(wakeLockEnabled = next) }
        storage.saveWakeLock(next)
        showToast(if (next) "已启用 CPU 唤醒锁 (WakeLock)" else "已停用 CPU 唤醒锁")
    }

    fun clearPacketLogs() {
        livePackets.value = emptyList()
        selectedPacket.value = null
        packetSeqCounter.set(0L)
        viewModelScope.launch(Dispatchers.IO) {
            storage.clearAllPackets()
            refreshStorageStats()
        }
        if (MqttBackgroundService.isRunning) {
            val host = serverConfig.value.host
            val brokerLabel = if (host.isNotBlank()) "${host}:${serverConfig.value.port}" else ""
            MqttBackgroundService.updateNotification(
                context = getApplication(),
                brokerHost = brokerLabel,
                count = 0L,
                latestTopic = null
            )
        }
        showToast("本地历史报文已清空，存储空间已物理收缩")
    }

    val isExporting = MutableStateFlow(false)

    /**
     * 导出全量报文为 Excel (.xlsx) 表格并调用系统能力打开/分享
     * 格式：序号、主题、设备ID、消息内容、时间 (yyyy-M-d HH:mm:ss)
     */
    fun exportPacketsToExcel(context: Context) {
        if (isExporting.value) return
        isExporting.value = true
        showToast("正在导出 Excel 报文，请稍候...")

        viewModelScope.launch(Dispatchers.IO) {
            try {
                val limit = serverConfig.value.bufferThreshold.coerceAtLeast(1000)
                val rawPairs = storage.loadAllPacketsForExport(limit)

                val exportItems = if (rawPairs.isNotEmpty()) {
                    rawPairs.mapIndexed { index, pair ->
                        val packet = pair.first
                        val createdAt = pair.second
                        val timeStr = ExcelExportHelper.formatTimestamp(createdAt)
                        val devId = ExcelExportHelper.extractDeviceId(
                            packet.payload,
                            packet.topic,
                            serverConfig.value.clientId
                        )
                        ExportPacketItem(
                            seqNumber = index + 1,
                            topic = packet.topic,
                            deviceId = devId,
                            payload = packet.payload,
                            timeFormatted = timeStr
                        )
                    }
                } else {
                    val currentMem = livePackets.value
                    currentMem.mapIndexed { index, packet ->
                        val devId = ExcelExportHelper.extractDeviceId(
                            packet.payload,
                            packet.topic,
                            serverConfig.value.clientId
                        )
                        ExportPacketItem(
                            seqNumber = index + 1,
                            topic = packet.topic,
                            deviceId = devId,
                            payload = packet.payload,
                            timeFormatted = ExcelExportHelper.formatTimestamp(System.currentTimeMillis())
                        )
                    }
                }

                if (exportItems.isEmpty()) {
                    withContext(Dispatchers.Main) {
                        isExporting.value = false
                        showToast("当前暂无报文记录可导出")
                    }
                    return@launch
                }

                val file = ExcelExportHelper.exportToXlsx(context, exportItems)

                withContext(Dispatchers.Main) {
                    isExporting.value = false
                    showToast("已生成 Excel 表格 (共 ${exportItems.size} 条记录)")
                    ExcelExportHelper.shareExportedFile(context, file)
                }
            } catch (e: Exception) {
                e.printStackTrace()
                withContext(Dispatchers.Main) {
                    isExporting.value = false
                    showToast("导出失败: ${e.localizedMessage}")
                }
            }
        }
    }

    fun saveAndApplySettings() {
        viewModelScope.launch {
            try {
                isSavingSettings.value = true

                // 1. 本地数据库瞬间持久化落盘 (SQLite)
                val activeId = activeBrokerId.value
                val currentProfiles = brokerProfiles.value.toMutableList()
                val activeIndex = currentProfiles.indexOfFirst { it.id == activeId }
                if (activeIndex >= 0) {
                    val current = currentProfiles[activeIndex]
                    currentProfiles[activeIndex] = current.copy(
                        host = serverConfig.value.host,
                        port = serverConfig.value.port,
                        clientId = serverConfig.value.clientId,
                        username = serverConfig.value.username,
                        password = serverConfig.value.password,
                        protocol = serverConfig.value.protocol,
                        cleanSession = serverConfig.value.cleanSession,
                        tlsEnabled = serverConfig.value.tlsEnabled,
                        keepAlive = serverConfig.value.keepAlive
                    )
                    brokerProfiles.value = currentProfiles
                    storage.saveBrokerProfiles(currentProfiles)
                }

                // 2. 本地保存完成，立即解除按钮 loading 状态，绝不卡住界面
                isSavingSettings.value = false
                showToast("配置已成功保存并立即生效")

                // 3. 异步应用连接：平滑重启长连接，不阻碍主界面交互
                isManualDisconnecting = false
                reconnectJob?.cancel()
                reconnectCountdown.value = 0
                connectionState.value = MqttConnectionState.CONNECTING

                val result = withContext(Dispatchers.IO) {
                    MqttClientManager.disconnect()
                    MqttClientManager.connect(serverConfig.value)
                }

                if (result.isSuccess) {
                    connectionState.value = MqttConnectionState.CONNECTED
                    serverConfig.update { it.copy(isConnected = true) }
                    val activeSubs = subscriptions.value.filter { it.isEnabled }
                    activeSubs.forEach { sub ->
                        MqttClientManager.subscribe(sub.topic, sub.qos)
                    }
                    showToast("已连接 Broker: ${serverConfig.value.host}:${serverConfig.value.port}")
                } else {
                    connectionState.value = MqttConnectionState.DISCONNECTED
                    serverConfig.update { it.copy(isConnected = false) }
                    val errorMsg = MqttClientManager.getReadableErrorMessage(
                        result.exceptionOrNull() ?: Exception("连接失败"),
                        serverConfig.value.host,
                        serverConfig.value.port,
                        isTls = serverConfig.value.tlsEnabled
                    )
                    showToast("连接未成功: $errorMsg")
                    if (serverConfig.value.autoReconnect) {
                        startAutoReconnectLoop(isImmediate = false)
                    }
                }
            } catch (e: Exception) {
                Log.e("MqttAssistantViewModel", "Error saving settings", e)
                showToast("保存异常: ${e.localizedMessage}")
            } finally {
                isSavingSettings.value = false
            }
        }
    }

    val isExportingConfig = MutableStateFlow(false)
    val isImportingConfig = MutableStateFlow(false)

    /**
     * 全量导出配置为标准 JSON 格式并调起系统分享 / 另存为
     * 包含：全部 Broker 节点、发布预设、订阅规则、高级参数及主题过滤规则
     */
    fun exportConfiguration(context: Context) {
        if (isExportingConfig.value) return
        isExportingConfig.value = true
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val file = ConfigBackupHelper.exportConfigToJson(
                    context = context,
                    profiles = brokerProfiles.value,
                    activeId = activeBrokerId.value,
                    presets = publishPresets.value,
                    subs = subscriptions.value,
                    serverConfig = serverConfig.value,
                    includeFilters = includeTopicFilters.value,
                    excludeFilters = excludeTopicFilters.value
                )
                withContext(Dispatchers.Main) {
                    isExportingConfig.value = false
                    showToast("配置已成功导出为 JSON 文件")
                    ConfigBackupHelper.shareBackupFile(context, file)
                }
            } catch (e: Exception) {
                Log.e("MqttAssistantViewModel", "Failed to export config", e)
                withContext(Dispatchers.Main) {
                    isExportingConfig.value = false
                    showToast("导出配置失败: ${e.localizedMessage}")
                }
            }
        }
    }

    /**
     * 从外部选择的 JSON 文件中全量解析并恢复配置
     */
    fun importConfiguration(context: Context, uri: Uri) {
        if (isImportingConfig.value) return
        isImportingConfig.value = true
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val jsonString = context.contentResolver.openInputStream(uri)?.use { stream ->
                    stream.bufferedReader(Charsets.UTF_8).readText()
                } ?: throw IllegalArgumentException("无法读取文件内容")

                val backup = ConfigBackupHelper.parseBackupJson(jsonString)

                // 1. 恢复 Broker 节点
                if (backup.brokerProfiles.isNotEmpty()) {
                    storage.saveBrokerProfiles(backup.brokerProfiles)
                    brokerProfiles.value = backup.brokerProfiles
                }

                // 2. 恢复激活 Broker
                val activeId = if (backup.activeBrokerId.isNotBlank() && backup.brokerProfiles.any { it.id == backup.activeBrokerId }) {
                    backup.activeBrokerId
                } else {
                    backup.brokerProfiles.firstOrNull()?.id ?: ""
                }
                if (activeId.isNotBlank()) {
                    storage.saveActiveBrokerId(activeId)
                    activeBrokerId.value = activeId
                    val activeBroker = backup.brokerProfiles.find { it.id == activeId }
                    if (activeBroker != null) {
                        serverConfig.update {
                            it.copy(
                                activeProfileId = activeBroker.id,
                                host = activeBroker.host,
                                port = activeBroker.port,
                                clientId = activeBroker.clientId,
                                username = activeBroker.username,
                                password = activeBroker.password,
                                protocol = activeBroker.protocol,
                                cleanSession = activeBroker.cleanSession,
                                tlsEnabled = activeBroker.tlsEnabled,
                                keepAlive = activeBroker.keepAlive
                            )
                        }
                    }
                }

                // 3. 恢复发布预设
                if (backup.publishPresets.isNotEmpty()) {
                    storage.savePublishPresets(backup.publishPresets)
                    publishPresets.value = backup.publishPresets
                }

                // 4. 恢复订阅条目
                if (backup.subscriptions.isNotEmpty()) {
                    storage.saveSubscriptions(backup.subscriptions)
                    subscriptions.value = backup.subscriptions
                }

                // 5. 恢复全局设置与过滤规则
                storage.saveAutoReconnect(backup.autoReconnect)
                storage.saveReconnectInterval(backup.reconnectIntervalSeconds)
                storage.saveMaxReconnectAttempts(backup.maxReconnectAttempts)
                storage.saveAutoRotate(backup.autoRotate)
                storage.saveBufferThreshold(backup.bufferThreshold)
                storage.saveBackgroundKeepAlive(backup.backgroundKeepAlive)
                storage.saveWakeLock(backup.wakeLockEnabled)
                storage.saveAutoStartEnabled(backup.autoStartEnabled)
                storage.saveProcessGuardEnabled(backup.processGuardEnabled)
                storage.saveIncludeTopicFilters(backup.includeFilters)
                storage.saveExcludeTopicFilters(backup.excludeFilters)
                includeTopicFilters.value = backup.includeFilters
                excludeTopicFilters.value = backup.excludeFilters

                serverConfig.update {
                    it.copy(
                        autoReconnect = backup.autoReconnect,
                        reconnectIntervalSeconds = backup.reconnectIntervalSeconds,
                        maxReconnectAttempts = backup.maxReconnectAttempts,
                        autoRotate = backup.autoRotate,
                        bufferThreshold = backup.bufferThreshold,
                        backgroundKeepAliveEnabled = backup.backgroundKeepAlive,
                        wakeLockEnabled = backup.wakeLockEnabled,
                        autoStartEnabled = backup.autoStartEnabled,
                        processGuardEnabled = backup.processGuardEnabled
                    )
                }

                refreshStorageStats()

                withContext(Dispatchers.Main) {
                    isImportingConfig.value = false
                    showToast("配置导入成功：恢复 ${backup.brokerProfiles.size} 个节点、${backup.publishPresets.size} 条预设、${backup.subscriptions.size} 条订阅")
                    if (serverConfig.value.autoReconnect || serverConfig.value.isConnected) {
                        connectToBroker()
                    }
                }
            } catch (e: Exception) {
                Log.e("MqttAssistantViewModel", "Failed to import config", e)
                withContext(Dispatchers.Main) {
                    isImportingConfig.value = false
                    showToast("导入失败: ${e.localizedMessage ?: "备份文件解析异常"}")
                }
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        try {
            val cm = getApplication<Application>().getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            networkCallback?.let { cm?.unregisterNetworkCallback(it) }
        } catch (_: Exception) {}
        MqttClientManager.onMessageReceived = null
        MqttClientManager.onConnectionStateChanged = null
        reconnectJob?.cancel()
    }
}
