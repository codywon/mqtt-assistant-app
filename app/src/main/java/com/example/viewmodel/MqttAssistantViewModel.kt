package com.example.viewmodel

import android.app.Application
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.Uri
import android.util.Log
import com.example.util.AutoExportHelper
import com.example.util.ArchivedExcelReader
import com.example.util.AutoStartUtil
import com.example.util.BackupData
import com.example.util.ConfigBackupHelper
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.ai.AiAgentClient
import com.example.ai.SIAgentToolRegistry
import com.example.data.MqttStorageRepository
import com.example.model.AiAgentConfig
import com.example.model.AiChatMessage
import com.example.model.AiChatSession
import com.example.model.AppScreen
import com.example.model.BrokerProfile
import com.example.model.MqttConnectionState
import com.example.model.MqttLogPacket
import com.example.model.MqttServerConfig
import com.example.model.ProtocolKnowledge
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
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.concurrent.atomic.AtomicLong
import java.util.Date
import java.util.Locale
import java.util.UUID

/**
 * 现场主动巡检与异常预警雷达数据模型
 */
data class LiveHealthWatchdogState(
    val activeGatewayCount: Int = 0,
    val packetRatePerMin: Int = 0,
    val anomalyCount: Int = 0,
    val anomalies: List<WatchdogAnomaly> = emptyList(),
    val isHealthy: Boolean = true
)

data class WatchdogAnomaly(
    val id: String,
    val topic: String,
    val reason: String,
    val timestamp: String,
    val rawPacket: MqttLogPacket
)

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
    val isBatteryOptimizationIgnored = MutableStateFlow(false)
    val isAllFilesAccessGranted = MutableStateFlow(false)

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

    // --- TSL 物模型协议解析引擎状态 ---
    val tslProtocols = MutableStateFlow<List<com.example.model.TslProtocol>>(emptyList())
    val tslParseResults = MutableStateFlow<Map<String, com.example.model.TslParseResult>>(emptyMap()) // packetId -> result

    // --- PC-Grade Topic Filters (Persistent: Include / Exclude) ---
    val includeTopicFilters = MutableStateFlow<List<String>>(storage.loadIncludeTopicFilters())
    val excludeTopicFilters = MutableStateFlow<List<String>>(storage.loadExcludeTopicFilters())

    // --- Production Background Filter Pipeline (150ms Debounced, Zero Main-Thread Load, 120Hz Smoothness) ---
    @OptIn(kotlinx.coroutines.FlowPreview::class)
    val filteredLivePackets: StateFlow<List<MqttLogPacket>> = combine(
        livePackets,
        logFilterQuery.debounce(150L),
        includeTopicFilters,
        excludeTopicFilters
    ) { packets, query, incFilters, excFilters ->
        val q = query.trim()
        val hasRules = incFilters.isNotEmpty() || excFilters.isNotEmpty()
        if (q.isEmpty() && !hasRules) {
            packets
        } else {
            withContext(Dispatchers.Default) {
                packets.filter { packet ->
                    val matchesAllowed = !hasRules || MqttTopicUtil.isTopicAllowed(packet.topic, incFilters, excFilters)
                    val matchesQuery = q.isEmpty() ||
                            packet.topic.contains(q, ignoreCase = true) ||
                            packet.payload.contains(q, ignoreCase = true)
                    matchesAllowed && matchesQuery
                }
            }
        }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

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
                    autoExportExcel = storage.loadAutoExportExcel(),
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
                    autoExportExcel = storage.loadAutoExportExcel(),
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

    // --- AI SI Agent & Protocol Clarification State ---
    private val toolRegistry = SIAgentToolRegistry(
        storage = storage,
        context = application,
        livePacketsProvider = { livePackets.value },
        tslParseResultsProvider = { tslParseResults.value },
        tslProtocolsProvider = { tslProtocols.value },
        onMessagePublished = { topic, qos, _, payload ->
            viewModelScope.launch(Dispatchers.Main) {
                val dotColor = when (qos) {
                    0 -> 0xFFF59E0B
                    1 -> 0xFF10B981
                    else -> 0xFF3980F4
                }
                val newHistory = PublishHistoryItem(
                    id = UUID.randomUUID().toString(),
                    topic = topic,
                    qos = qos,
                    timestamp = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date()),
                    payload = payload,
                    dotColorHex = dotColor
                )
                publishHistory.update { listOf(newHistory) + it }
                showToast("Agent 已向 $topic 成功下发报文")
            }
        }
    )
    private val aiAgentClient = AiAgentClient(toolRegistry)

    val aiConfig = MutableStateFlow<AiAgentConfig>(storage.loadAiConfig())
    val aiSessions = MutableStateFlow<List<AiChatSession>>(emptyList())
    val currentSessionId = MutableStateFlow<String>("default")
    val aiMessages = MutableStateFlow<List<AiChatMessage>>(emptyList())
    val protocolKnowledgeList = MutableStateFlow<List<ProtocolKnowledge>>(emptyList())
    val isAiResponding = MutableStateFlow(false)
    val currentAiThinkingText = MutableStateFlow("")
    val currentAiActionStatus = MutableStateFlow("")
    val pendingAiPromptQueue = MutableStateFlow<List<String>>(emptyList())
    private var aiJob: Job? = null

    // --- 场景 1: 单条报文 AI 结构化透视与逆向解码状态 ---
    val inspectingPacket = MutableStateFlow<MqttLogPacket?>(null)
    val packetInspectionResult = MutableStateFlow<String>("")
    val isPacketInspecting = MutableStateFlow(false)
    val packetInspectionThinking = MutableStateFlow("")
    private var packetInspectJob: Job? = null

    // --- 场景 3: 现场主动巡检与异常预警雷达状态 (Proactive Watchdog) ---
    val liveHealthWatchdogState: StateFlow<LiveHealthWatchdogState> = livePackets.map { packets ->
        computeHealthWatchdogState(packets)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), LiveHealthWatchdogState())

    init {
        // 纯内存分层架构：启动时彻底清除旧版本遗留的 SQLite 实时报文，杜绝任何旧数据干扰
        viewModelScope.launch(Dispatchers.IO) {
            storage.clearAllPackets()

            var savedSessions = storage.loadAllAiSessions()
            if (savedSessions.isEmpty()) {
                val initialSession = AiChatSession(id = "default", title = "新会话")
                storage.saveAiSession(initialSession)
                savedSessions = listOf(initialSession)
            }
            val activeSessionId = savedSessions.first().id
            val savedAiMsgs = storage.loadAiMessages(activeSessionId, 100)
            val savedProtocols = storage.loadAllProtocolKnowledge()

            // TSL 物模型：首次安装注入内置模板，然后加载所有已启用协议
            storage.initBuiltinTslProtocols()
            val enabledTslProtos = storage.loadEnabledTslProtocols()

            // 保持内存单例为单一可信源
            val currentMemoryPackets = com.example.data.MemoryPacketStore.getAll()

            withContext(Dispatchers.Main) {
                livePackets.value = currentMemoryPackets
                aiSessions.value = savedSessions
                currentSessionId.value = activeSessionId
                aiMessages.value = savedAiMsgs
                protocolKnowledgeList.value = savedProtocols
                tslProtocols.value = enabledTslProtos
            }
        }
        startPacketBatchCollector()
        setupMqttCallbacks()
        registerNetworkCallback()
        refreshStorageStats()
        checkBatteryOptimizationStatus(application)
        checkAllFilesAccessStatus(application)

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

                // 1. 纯内存高速环形存储与主线程极速发射 (零 SQLite 写入，零闪存磨损)
                val allPackets = com.example.data.MemoryPacketStore.addPackets(currentBatch, maxBuffer)
                withContext(Dispatchers.Main) {
                    livePackets.value = allPackets

                    // TSL 物模型引擎：实时自动解析新到达的报文（微秒级，零额外 I/O）
                    val protos = tslProtocols.value
                    if (protos.isNotEmpty()) {
                        val newResults = tslParseResults.value.toMutableMap()
                        for (pkt in currentBatch) {
                            val result = com.example.engine.TslParseEngine.tryParse(
                                topic = pkt.topic,
                                payload = pkt.payload,
                                category = pkt.category,
                                protocols = protos
                            )
                            if (result != null) {
                                newResults[pkt.id] = result
                            }
                        }
                        // 保持缓存大小与 livePackets 对齐，防止无限膨胀
                        if (newResults.size > maxBuffer * 2) {
                            val liveIds = allPackets.map { it.id }.toSet()
                            newResults.keys.retainAll(liveIds)
                        }
                        tslParseResults.value = newResults
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
                refreshStorageStats()

                // 2. 满额自动导出 Excel 归档检查 (受系统设置 autoExportExcel 开关管控)
                if (serverConfig.value.autoExportExcel) {
                    AutoExportHelper.checkAndExportFromMemory(
                        context = getApplication(),
                        bufferThreshold = maxBuffer,
                        clientId = serverConfig.value.clientId
                    ) { exportedCount, _ ->
                        packetSeqCounter.set(0L)
                        viewModelScope.launch(Dispatchers.Main) {
                            livePackets.value = com.example.data.MemoryPacketStore.getAll()
                            showToast("已自动将 $exportedCount 条报文归档为 Excel (存至 Download 目录)")
                        }
                        refreshStorageStats()
                    }
                }

                // 4. 更新通知栏
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

    private var lastNetworkTransport: Int? = null
    private var lastSwitchTimestamp: Long = 0L

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

                override fun onCapabilitiesChanged(network: Network, networkCapabilities: NetworkCapabilities) {
                    val currentTransport = when {
                        networkCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> NetworkCapabilities.TRANSPORT_WIFI
                        networkCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> NetworkCapabilities.TRANSPORT_CELLULAR
                        networkCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> NetworkCapabilities.TRANSPORT_ETHERNET
                        else -> null
                    }
                    if (currentTransport != null && lastNetworkTransport != null && currentTransport != lastNetworkTransport) {
                        val now = System.currentTimeMillis()
                        // 2 秒防抖，防止网络震荡重复打断
                        if (now - lastSwitchTimestamp > 2000L) {
                            lastSwitchTimestamp = now
                            Log.i("MqttAssistantViewModel", "Network transport switched ($lastNetworkTransport -> $currentTransport). Reconnecting to heal TCP half-open socket...")
                            if (serverConfig.value.isConnected && !isManualDisconnecting && serverConfig.value.autoReconnect) {
                                viewModelScope.launch(Dispatchers.IO) {
                                    try {
                                        MqttClientManager.disconnect()
                                    } catch (_: Exception) {}
                                    withContext(Dispatchers.Main) {
                                        startAutoReconnectLoop(isImmediate = true)
                                    }
                                }
                            }
                        }
                    }
                    if (currentTransport != null) {
                        lastNetworkTransport = currentTransport
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

    /**
     * 解析发布载荷中的动态宏占位符:
     * 1. ${timestamp} 或 ${time} -> 当前系统毫秒时间戳
     * 2. ${uuid} -> 8位唯一短随机码
     * 3. ${random(min, max)} -> 区间随机整数
     */
    fun resolvePayloadMacros(payload: String): String {
        var result = payload
        val now = System.currentTimeMillis()
        result = result.replace(Regex("""\$\{(timestamp|time)\}"""), now.toString())
        result = result.replace(Regex("""\$\{uuid\}""")) {
            UUID.randomUUID().toString().replace("-", "").take(8)
        }
        result = result.replace(Regex("""\$\{random\((\d+)\s*,\s*(\d+)\)\}""")) { match ->
            val min = match.groupValues[1].toIntOrNull() ?: 0
            val max = match.groupValues[2].toIntOrNull() ?: 100
            if (min <= max) {
                java.util.concurrent.ThreadLocalRandom.current().nextInt(min, max + 1).toString()
            } else {
                min.toString()
            }
        }
        return result
    }

    fun directPublishPreset(preset: PublishPreset) {
        val validation = MqttTopicUtil.validatePublishTopic(preset.topic)
        if (!validation.isValid) {
            showToast("发布失败: ${validation.errorMessage}")
            return
        }

        viewModelScope.launch {
            val finalPayload = resolvePayloadMacros(preset.payload)
            val payloadBytes = finalPayload.toByteArray(Charsets.UTF_8)
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
                payload = finalPayload,
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
                payload = finalPayload,
                devInfo = "PUB · ${payloadBytes.size}B" + if (result.isSuccess) " · 已送达" else " · 发送失败",
                sizeText = "${payloadBytes.size}B",
                category = preset.name.ifBlank { preset.topic.substringBefore('/') },
                dotColorHex = dotColor
            )
            val allPackets = com.example.data.MemoryPacketStore.addPacket(packet, serverConfig.value.bufferThreshold)
            livePackets.value = allPackets

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
            val finalPayload = resolvePayloadMacros(publishPayload.value)
            val payloadBytes = finalPayload.toByteArray(Charsets.UTF_8)
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
                payload = finalPayload.replace("\n", "").replace(" ", ""),
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
                payload = finalPayload,
                devInfo = "PUB · ${payloadBytes.size}B" + if (result.isSuccess) " · 已送达" else " · 发送失败",
                sizeText = "${payloadBytes.size}B",
                category = topic.substringBefore('/'),
                dotColorHex = dotColor
            )
            val allPackets = com.example.data.MemoryPacketStore.addPacket(packet, serverConfig.value.bufferThreshold)
            livePackets.value = allPackets

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
        com.example.data.MemoryPacketStore.clear()
        // 彻底排空 Channel 管道中积压或正在并发流入的未处理报文，杜绝下一批次旧数据重新灌回
        while (incomingPacketChannel.tryReceive().isSuccess) {}
        livePackets.value = emptyList()
        selectedPacket.value = null
        tslParseResults.value = emptyMap()
        packetSeqCounter.set(0L)
        viewModelScope.launch(Dispatchers.IO) {
            storage.clearAllPackets()
        }
        refreshStorageStats()
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
        showToast("已清空实时报文日志与内存缓存")
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

    fun toggleAutoExportExcel() {
        val next = !serverConfig.value.autoExportExcel
        serverConfig.update { it.copy(autoExportExcel = next) }
        storage.saveAutoExportExcel(next)
        if (next) {
            showToast("已开启满额自动导出 Excel (满 ${serverConfig.value.bufferThreshold} 条转储至 Download 目录)")
        } else {
            showToast("已关闭满额自动导出 Excel")
        }
    }

    fun updateBufferThreshold(thStr: String) {
        val th = thStr.toIntOrNull() ?: 10000
        serverConfig.update { it.copy(bufferThreshold = th) }
        storage.saveBufferThreshold(th)
    }

    fun refreshStorageStats() {
        val count = com.example.data.MemoryPacketStore.size()
        val mb = Math.round((count * 350.0 / (1024.0 * 1024.0)) * 100.0) / 100.0
        serverConfig.update { it.copy(usedSpaceMb = mb, packetCount = count) }
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
        checkBatteryOptimizationStatus(getApplication())
        checkAllFilesAccessStatus(getApplication())
        if (!serverConfig.value.isConnected && !isManualDisconnecting && serverConfig.value.autoReconnect && brokerProfiles.value.isNotEmpty() && serverConfig.value.host.isNotBlank()) {
            Log.d("MqttAssistantViewModel", "onAppResume: app returned to foreground, probing immediate reconnect")
            startAutoReconnectLoop(isImmediate = true)
        }
    }

    fun checkAllFilesAccessStatus(context: Context) {
        try {
            isAllFilesAccessGranted.value = ArchivedExcelReader.hasAllFilesAccess(context)
        } catch (e: Exception) {
            Log.w("MqttAssistantViewModel", "Failed to check all files access status", e)
        }
    }

    fun checkBatteryOptimizationStatus(context: Context) {
        try {
            val powerManager = context.getSystemService(Context.POWER_SERVICE) as? android.os.PowerManager
            val isIgnored = powerManager?.isIgnoringBatteryOptimizations(context.packageName) == true
            isBatteryOptimizationIgnored.value = isIgnored
        } catch (e: Exception) {
            Log.w("MqttAssistantViewModel", "Failed to check battery optimization status", e)
        }
    }

    fun requestIgnoreBatteryOptimization(context: Context) {
        try {
            val intent = android.content.Intent(android.provider.Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                data = Uri.parse("package:${context.packageName}")
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            try {
                val intent = android.content.Intent(android.provider.Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
                context.startActivity(intent)
            } catch (_: Exception) {
                try {
                    val intent = android.content.Intent(android.provider.Settings.ACTION_SETTINGS)
                    context.startActivity(intent)
                } catch (_: Exception) {
                    showToast("无法打开系统电池优化设置")
                }
            }
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
        clearPackets()
    }

    fun clearAllData() {
        clearPacketLogs()
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
                var exportedCount = 0
                val clientId = serverConfig.value.clientId
                val memPackets = com.example.data.MemoryPacketStore.getAll()

                if (memPackets.isEmpty()) {
                    withContext(Dispatchers.Main) {
                        isExporting.value = false
                        showToast("当前暂无报文记录可导出")
                    }
                    return@launch
                }

                val file = ExcelExportHelper.exportStreamToXlsx(context) { rowWriter ->
                    for (packet in memPackets) {
                        val devId = ExcelExportHelper.extractDeviceId(
                            packet.payload,
                            packet.topic,
                            clientId
                        )
                        rowWriter.writeRow(packet.topic, devId, packet.payload, packet.timestamp)
                        exportedCount++
                    }
                }

                if (exportedCount == 0) {
                    withContext(Dispatchers.Main) {
                        isExporting.value = false
                        showToast("当前暂无报文记录可导出")
                    }
                    return@launch
                }

                // 同步备份一份至系统公共 Download 目录，确保媒体库与文件管理器立即可见
                ExcelExportHelper.saveExportFileToPublicDownloads(context, file)

                withContext(Dispatchers.Main) {
                    isExporting.value = false
                    showToast("已生成 Excel 表格 (共 $exportedCount 条记录)")
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
                    excludeFilters = excludeTopicFilters.value,
                    aiConfig = aiConfig.value,
                    protocols = protocolKnowledgeList.value
                )
                withContext(Dispatchers.Main) {
                    isExportingConfig.value = false
                    showToast("配置已成功导出为 JSON 文件 (含 AI 与协议规则)")
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
     * 生成配置口令并复制到系统剪贴板 (免找 JSON 文件，适合即时分享/换机克隆，含 AI 配置与协议库)
     */
    fun copyConfigToken(context: Context) {
        try {
            val token = ConfigBackupHelper.exportConfigToToken(
                profiles = brokerProfiles.value,
                activeId = activeBrokerId.value,
                presets = publishPresets.value,
                subs = subscriptions.value,
                serverConfig = serverConfig.value,
                includeFilters = includeTopicFilters.value,
                excludeFilters = excludeTopicFilters.value,
                aiConfig = aiConfig.value,
                protocols = protocolKnowledgeList.value
            )
            val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
            cm.setPrimaryClip(android.content.ClipData.newPlainText("MQTT-Config-Token", token))
            showToast("已复制全量配置口令！(含 AI 模型与协议知识库)")
        } catch (e: Exception) {
            showToast("生成口令失败: ${e.localizedMessage}")
        }
    }

    /**
     * 从剪贴板读取口令并一键导入恢复配置
     */
    fun importConfigFromClipboard(context: Context) {
        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
        val clipText = cm.primaryClip?.getItemAt(0)?.text?.toString()?.trim() ?: ""
        if (clipText.isBlank()) {
            showToast("剪贴板中无内容，请先复制配置口令")
            return
        }
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val backup = ConfigBackupHelper.parseConfigFromToken(clipText)
                applyBackupData(backup)
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    showToast("口令导入失败: 剪贴板内容不是有效配置口令")
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
                applyBackupData(backup)
            } catch (e: Exception) {
                Log.e("MqttAssistantViewModel", "Failed to import config", e)
                withContext(Dispatchers.Main) {
                    isImportingConfig.value = false
                    showToast("导入失败: ${e.localizedMessage ?: "备份文件解析异常"}")
                }
            }
        }
    }

    private suspend fun applyBackupData(backup: BackupData) {
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
        storage.saveAutoExportExcel(backup.autoExportExcel)
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
                autoExportExcel = backup.autoExportExcel,
                bufferThreshold = backup.bufferThreshold,
                backgroundKeepAliveEnabled = backup.backgroundKeepAlive,
                wakeLockEnabled = backup.wakeLockEnabled,
                autoStartEnabled = backup.autoStartEnabled,
                processGuardEnabled = backup.processGuardEnabled
            )
        }

        // 恢复 AI 智能体与大模型配置
        if (backup.aiConfig != null) {
            storage.saveAiConfig(backup.aiConfig)
            aiConfig.value = backup.aiConfig
        }

        // 恢复硬件私有协议知识库
        if (backup.protocolKnowledgeList.isNotEmpty()) {
            backup.protocolKnowledgeList.forEach { p ->
                storage.saveProtocolKnowledge(p)
            }
            protocolKnowledgeList.value = storage.loadAllProtocolKnowledge()
        }

        refreshStorageStats()

        withContext(Dispatchers.Main) {
            isImportingConfig.value = false
            val extraInfo = buildString {
                if (backup.aiConfig != null) append("、AI大模型配置")
                if (backup.protocolKnowledgeList.isNotEmpty()) append("、${backup.protocolKnowledgeList.size} 条硬件协议")
            }
            showToast("配置恢复成功：已恢复 ${backup.brokerProfiles.size} 个节点、${backup.publishPresets.size} 条预设、${backup.subscriptions.size} 条订阅$extraInfo")
            if (serverConfig.value.autoReconnect || serverConfig.value.isConnected) {
                connectToBroker()
            }
        }
    }

    // ==========================================
    // 硬件私有协议知识澄清库专属导入导出操作
    // ==========================================

    /**
     * 导出协议澄清库为独立 JSON 文件并弹出系统分享
     */
    fun exportProtocolsToJson(context: Context) {
        val protocols = protocolKnowledgeList.value
        if (protocols.isEmpty()) {
            showToast("当前协议库为空，暂无规则可导出")
            return
        }
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val file = ConfigBackupHelper.exportProtocolsToJson(context, protocols)
                withContext(Dispatchers.Main) {
                    showToast("协议知识库已导出为 JSON 文件")
                    ConfigBackupHelper.shareBackupFile(context, file, "分享/保存硬件私有协议库")
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    showToast("导出协议库失败: ${e.localizedMessage}")
                }
            }
        }
    }

    /**
     * 复制协议澄清库口令到系统剪贴板 (极速跨手机微信互传)
     */
    fun copyProtocolsToken(context: Context) {
        val protocols = protocolKnowledgeList.value
        if (protocols.isEmpty()) {
            showToast("当前协议库为空，暂无可生成的口令")
            return
        }
        try {
            val token = ConfigBackupHelper.exportProtocolsToToken(protocols)
            val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
            cm.setPrimaryClip(android.content.ClipData.newPlainText("MQTT-Protocols-Token", token))
            showToast("已复制协议库口令！可直接在微信发送并在另一台手机一键导入")
        } catch (e: Exception) {
            showToast("生成协议口令失败: ${e.localizedMessage}")
        }
    }

    /**
     * 从剪贴板口令导入协议规则
     */
    fun importProtocolsFromClipboard(context: Context) {
        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
        val clipText = cm.primaryClip?.getItemAt(0)?.text?.toString()?.trim() ?: ""
        if (clipText.isBlank()) {
            showToast("剪贴板中无内容，请先复制协议口令或JSON")
            return
        }
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val list = ConfigBackupHelper.parseProtocolsFromJsonOrToken(clipText)
                if (list.isEmpty()) {
                    withContext(Dispatchers.Main) {
                        showToast("未在剪贴板中识别到有效协议规则")
                    }
                    return@launch
                }
                list.forEach { storage.saveProtocolKnowledge(it) }
                val updated = storage.loadAllProtocolKnowledge()
                protocolKnowledgeList.value = updated
                withContext(Dispatchers.Main) {
                    showToast("成功导入 ${list.size} 条硬件协议规则！")
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    showToast("口令导入失败: 剪贴板内容不是有效协议规则")
                }
            }
        }
    }

    /**
     * 从外部文件 Uri 导入协议规则
     */
    fun importProtocolsFromUri(context: Context, uri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val jsonString = context.contentResolver.openInputStream(uri)?.use { stream ->
                    stream.bufferedReader(Charsets.UTF_8).readText()
                } ?: throw IllegalArgumentException("无法读取文件内容")

                val list = ConfigBackupHelper.parseProtocolsFromJsonOrToken(jsonString)
                if (list.isEmpty()) {
                    withContext(Dispatchers.Main) {
                        showToast("文件中未解析到有效协议规则")
                    }
                    return@launch
                }
                list.forEach { storage.saveProtocolKnowledge(it) }
                val updated = storage.loadAllProtocolKnowledge()
                protocolKnowledgeList.value = updated
                withContext(Dispatchers.Main) {
                    showToast("成功从文件恢复 ${list.size} 条硬件协议！")
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    showToast("导入失败: ${e.localizedMessage ?: "文件解析异常"}")
                }
            }
        }
    }

    // ==========================================
    // AI SI Agent & Protocol Clarification Actions
    // ==========================================

    fun createNewAiSession() {
        stopAiResponse()
        val newSession = AiChatSession(
            id = java.util.UUID.randomUUID().toString(),
            title = "新会话"
        )
        aiSessions.update { listOf(newSession) + it }
        currentSessionId.value = newSession.id
        aiMessages.value = emptyList()
        viewModelScope.launch(Dispatchers.IO) {
            storage.saveAiSession(newSession)
        }
    }

    fun switchAiSession(sessionId: String) {
        if (currentSessionId.value == sessionId) return
        stopAiResponse()
        currentSessionId.value = sessionId
        viewModelScope.launch(Dispatchers.IO) {
            val msgs = storage.loadAiMessages(sessionId, 100)
            withContext(Dispatchers.Main) {
                aiMessages.value = msgs
            }
        }
    }

    fun deleteAiSession(sessionId: String) {
        stopAiResponse()
        viewModelScope.launch(Dispatchers.IO) {
            storage.deleteAiSession(sessionId)
            var remaining = storage.loadAllAiSessions()
            if (remaining.isEmpty()) {
                val fresh = AiChatSession(id = "default", title = "新会话")
                storage.saveAiSession(fresh)
                remaining = listOf(fresh)
            }
            val targetActiveId = if (currentSessionId.value == sessionId) remaining.first().id else currentSessionId.value
            val msgs = storage.loadAiMessages(targetActiveId, 100)
            withContext(Dispatchers.Main) {
                aiSessions.value = remaining
                currentSessionId.value = targetActiveId
                aiMessages.value = msgs
                showToast("已删除该会话")
            }
        }
    }

    fun renameAiSession(sessionId: String, newTitle: String) {
        val trimmed = newTitle.trim().ifBlank { "会话" }
        aiSessions.update { list ->
            list.map { if (it.id == sessionId) it.copy(title = trimmed) else it }
        }
        viewModelScope.launch(Dispatchers.IO) {
            storage.updateAiSessionTitle(sessionId, trimmed)
        }
    }

    fun sendAiMessage(promptText: String) {
        val trimmed = promptText.trim()
        if (trimmed.isBlank()) return

        if (isAiResponding.value) {
            pendingAiPromptQueue.update { it + trimmed }
            showToast("追问已加入排队，将在当前回答完成后自动执行")
            return
        }

        executeAiMessage(trimmed, isRetry = false)
    }

    fun clearPendingAiQueue() {
        pendingAiPromptQueue.value = emptyList()
        showToast("已清空追问队列")
    }

    fun retryAiMessage(errorMessageId: String) {
        if (isAiResponding.value) {
            showToast("请等待当前任务完成")
            return
        }
        val msgs = aiMessages.value
        val errorIndex = msgs.indexOfFirst { it.id == errorMessageId }
        if (errorIndex < 0) return
        val userMsg = msgs.subList(0, errorIndex).lastOrNull { it.role == "user" }
        if (userMsg == null) {
            showToast("未找到可重试的问题")
            return
        }
        aiMessages.update { list -> list.filter { it.id != errorMessageId } }
        viewModelScope.launch(Dispatchers.IO) {
            storage.deleteAiMessage(errorMessageId)
        }
        executeAiMessage(userMsg.content, isRetry = true)
    }

    private fun executeAiMessage(promptText: String, isRetry: Boolean = false) {
        val activeSessionId = currentSessionId.value
        if (!isRetry) {
            val userMsg = AiChatMessage(
                sessionId = activeSessionId,
                role = "user",
                content = promptText
            )
            aiMessages.update { it + userMsg }
            viewModelScope.launch(Dispatchers.IO) {
                storage.saveAiMessage(userMsg)
            }

            // 若当前会话是默认名称且首条消息，自动根据首问提炼会话标题
            val currentSession = aiSessions.value.find { it.id == activeSessionId }
            if (currentSession != null && (currentSession.title == "新会话" || currentSession.title.isBlank())) {
                val newTitle = promptText.take(12)
                renameAiSession(activeSessionId, newTitle)
            }
        }

        val assistantMsgId = java.util.UUID.randomUUID().toString()
        val initialAssistantMsg = AiChatMessage(
            id = assistantMsgId,
            sessionId = activeSessionId,
            role = "assistant",
            content = "",
            isThinking = true
        )
        aiMessages.update { it + initialAssistantMsg }
        isAiResponding.value = true
        currentAiThinkingText.value = ""
        currentAiActionStatus.value = "🤖 正在理解意图..."

        aiJob?.cancel()
        aiJob = viewModelScope.launch(Dispatchers.IO) {
            val contentAccumulator = StringBuilder()
            val reasoningAccumulator = StringBuilder()

            aiAgentClient.chatStream(
                config = aiConfig.value,
                conversationHistory = aiMessages.value.dropLast(1),
                onChunk = { delta, isThinking ->
                    if (isThinking) {
                        reasoningAccumulator.append(delta)
                        currentAiThinkingText.value = reasoningAccumulator.toString()
                    } else {
                        contentAccumulator.append(delta)
                        val currText = contentAccumulator.toString()
                        aiMessages.update { list ->
                            list.map { if (it.id == assistantMsgId) it.copy(content = currText, isThinking = false) else it }
                        }
                    }
                },
                onToolAction = { actionText ->
                    currentAiActionStatus.value = actionText
                },
                onError = { errorText ->
                    val finalError = if (contentAccumulator.isNotEmpty()) "${contentAccumulator}\n\n⚠️ $errorText" else "⚠️ $errorText"
                    val errorMsg = AiChatMessage(
                        id = assistantMsgId,
                        sessionId = activeSessionId,
                        role = "assistant",
                        content = finalError,
                        isError = true,
                        isThinking = false
                    )
                    aiMessages.update { list ->
                        list.map { if (it.id == assistantMsgId) errorMsg else it }
                    }
                    storage.saveAiMessage(errorMsg)
                    isAiResponding.value = false
                    currentAiActionStatus.value = ""
                    currentAiThinkingText.value = ""
                    checkAndTriggerNextPendingAiMessage()
                },
                onComplete = { fullContent, reasoningContent ->
                    val finalMsg = AiChatMessage(
                        id = assistantMsgId,
                        sessionId = activeSessionId,
                        role = "assistant",
                        content = fullContent.ifBlank {
                            "已完成数据检索，当前暂未发现匹配记录。请告诉我您想查询的特定设备、网关或报文主题，以便为您精准排查。"
                        },
                        reasoningContent = reasoningContent,
                        isThinking = false,
                        isError = false
                    )
                    aiMessages.update { list ->
                        list.map { if (it.id == assistantMsgId) finalMsg else it }
                    }
                    storage.saveAiMessage(finalMsg)
                    isAiResponding.value = false
                    currentAiActionStatus.value = ""
                    currentAiThinkingText.value = ""
                    checkAndTriggerNextPendingAiMessage()
                }
            )
        }
    }

    private fun checkAndTriggerNextPendingAiMessage() {
        val queue = pendingAiPromptQueue.value
        if (queue.isNotEmpty()) {
            val nextPrompt = queue.first()
            pendingAiPromptQueue.update { it.drop(1) }
            viewModelScope.launch(Dispatchers.Main) {
                delay(300)
                executeAiMessage(nextPrompt, isRetry = false)
            }
        }
    }

    fun stopAiResponse() {
        aiJob?.cancel()
        isAiResponding.value = false
        currentAiActionStatus.value = ""
        currentAiThinkingText.value = ""
        if (pendingAiPromptQueue.value.isNotEmpty()) {
            pendingAiPromptQueue.value = emptyList()
            showToast("已停止响应并清空排队追问")
        }
    }

    fun copyToClipboard(text: String, label: String = "文本") {
        if (text.isBlank()) return
        try {
            val cm = getApplication<Application>().getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
            val clip = ClipData.newPlainText(label, text)
            cm?.setPrimaryClip(clip)
            showToast("已复制到剪贴板")
        } catch (e: Exception) {
            showToast("复制失败: ${e.localizedMessage}")
        }
    }

    fun clearAiMessages() {
        aiJob?.cancel()
        isAiResponding.value = false
        currentAiActionStatus.value = ""
        currentAiThinkingText.value = ""
        pendingAiPromptQueue.value = emptyList()
        aiMessages.value = emptyList()
        val activeSessionId = currentSessionId.value
        viewModelScope.launch(Dispatchers.IO) {
            storage.clearAiMessages(activeSessionId)
        }
        showToast("已清空当前会话记录")
    }

    fun updateAiConfig(config: AiAgentConfig) {
        aiConfig.value = config
        viewModelScope.launch(Dispatchers.IO) {
            storage.saveAiConfig(config)
        }
        showToast("AI 模型配置已保存")
    }

    fun saveProtocolKnowledge(item: ProtocolKnowledge) {
        val updated = item.copy(createdAt = System.currentTimeMillis())
        val currentList = protocolKnowledgeList.value.toMutableList()
        val index = currentList.indexOfFirst { it.id == item.id }
        if (index >= 0) {
            currentList[index] = updated
        } else {
            currentList.add(0, updated)
        }
        protocolKnowledgeList.value = currentList
        viewModelScope.launch(Dispatchers.IO) {
            storage.saveProtocolKnowledge(updated)
        }
        showToast("协议澄清已保存并注入 Agent 知识库")
    }

    fun importBatchProtocols(rawText: String) {
        if (rawText.isBlank()) return
        val blocks = rawText.split(Regex("(?:\\r?\\n){2,}(?:[-=]{3,}|---|===)(?:\\r?\\n)*|(?:\\r?\\n){3,}"))
            .map { it.trim() }
            .filter { it.isNotBlank() }

        val newProtocols = mutableListOf<ProtocolKnowledge>()
        val currentTime = System.currentTimeMillis()
        blocks.forEachIndexed { index, block ->
            val lines = block.lines().map { it.trim() }.filter { it.isNotBlank() }
            if (lines.isNotEmpty()) {
                val firstLine = lines.first().removePrefix("#").removePrefix("【").removeSuffix("】").trim()
                val topicLine = lines.find { it.contains("topic:", ignoreCase = true) || it.contains("主题:", ignoreCase = true) }
                val topic = topicLine?.substringAfter(":")?.trim()?.ifBlank { "vital/gateway/#" } ?: "vital/gateway/#"
                val proto = ProtocolKnowledge(
                    id = java.util.UUID.randomUUID().toString(),
                    name = if (firstLine.length in 1..25) firstLine else "导入协议 ${index + 1}",
                    topicFilter = topic,
                    description = block,
                    createdAt = currentTime + index
                )
                newProtocols.add(proto)
            }
        }

        if (newProtocols.isEmpty()) {
            val single = ProtocolKnowledge(
                name = "设备协议规则",
                topicFilter = "vital/#",
                description = rawText.trim(),
                createdAt = currentTime
            )
            newProtocols.add(single)
        }

        val updatedList = newProtocols + protocolKnowledgeList.value
        protocolKnowledgeList.value = updatedList
        viewModelScope.launch(Dispatchers.IO) {
            newProtocols.forEach { storage.saveProtocolKnowledge(it) }
        }
        showToast("已成功导入 ${newProtocols.size} 条硬件协议澄清规则")
    }

    fun deleteProtocolKnowledge(id: String) {
        protocolKnowledgeList.update { list -> list.filter { it.id != id } }
        viewModelScope.launch(Dispatchers.IO) {
            storage.deleteProtocolKnowledge(id)
        }
        showToast("已删除该协议澄清")
    }

    // --- 现场主动巡检与异常预警雷达 (Proactive Watchdog) ---
    private fun computeHealthWatchdogState(packets: List<MqttLogPacket>): LiveHealthWatchdogState {
        if (packets.isEmpty()) {
            return LiveHealthWatchdogState(
                activeGatewayCount = 0,
                packetRatePerMin = 0,
                anomalyCount = 0,
                anomalies = emptyList(),
                isHealthy = true
            )
        }

        // 提取去重活跃网关 (取前两级路径作为网关标识)
        val gateways = packets.map { p ->
            val parts = p.topic.split('/')
            if (parts.size >= 2) "${parts[0]}/${parts[1]}" else parts.firstOrNull() ?: p.topic
        }.distinct()

        val recentPackets = packets.takeLast(100)
        val anomalies = mutableListOf<WatchdogAnomaly>()
        val errorKeywords = listOf("error", "alarm", "fault", "fail", "crc_err", "offline", "timeout", "warn")

        for (p in recentPackets) {
            val isAnomaly = p.category.equals("ERROR", ignoreCase = true) ||
                errorKeywords.any { kw -> p.payload.contains(kw, ignoreCase = true) || p.topic.contains(kw, ignoreCase = true) }
            if (isAnomaly) {
                val reason = when {
                    p.payload.contains("alarm", ignoreCase = true) -> "检测到告警标志 (alarm)"
                    p.payload.contains("error", ignoreCase = true) -> "报文携带错误标识 (error)"
                    p.payload.contains("fault", ignoreCase = true) -> "设备故障状态 (fault)"
                    p.payload.contains("crc", ignoreCase = true) -> "CRC 校验失败特征"
                    p.category.equals("ERROR", ignoreCase = true) -> "系统标记异常分类"
                    else -> "异常体征数据"
                }
                anomalies.add(
                    WatchdogAnomaly(
                        id = p.id,
                        topic = p.topic,
                        reason = reason,
                        timestamp = p.timestamp,
                        rawPacket = p
                    )
                )
            }
        }

        val rate = (packets.size.coerceAtMost(60) * 1.2).toInt()
        return LiveHealthWatchdogState(
            activeGatewayCount = gateways.size,
            packetRatePerMin = rate,
            anomalyCount = anomalies.size,
            anomalies = anomalies.take(10),
            isHealthy = anomalies.isEmpty()
        )
    }

    // --- 场景 1: 单条报文 AI 结构化透视与逆向反推 ---
    fun inspectPacketWithAi(packet: MqttLogPacket) {
        inspectingPacket.value = packet
        isPacketInspecting.value = true
        packetInspectionResult.value = ""
        packetInspectionThinking.value = ""
        packetInspectJob?.cancel()

        packetInspectJob = viewModelScope.launch(Dispatchers.IO) {
            val cfg = aiConfig.value
            if (cfg.apiKey.isBlank()) {
                withContext(Dispatchers.Main) {
                    packetInspectionResult.value = "⚠️ 未配置 AI 模型 API Key。请在「设置 - AI 大模型设置」中配置 API Key 后再使用 AI 透视功能。"
                    isPacketInspecting.value = false
                }
                return@launch
            }

            val matchedProtocols = protocolKnowledgeList.value.filter {
                it.topicFilter.isNotBlank() && packet.topic.contains(it.topicFilter, ignoreCase = true)
            }
            val protocolContext = if (matchedProtocols.isNotEmpty()) {
                "【本地已命中私有协议规则】:\n" + matchedProtocols.joinToString("\n---\n") {
                    "协议名称: ${it.name}\n规则描述: ${it.description}\n样例 Hex: ${it.sampleHex}"
                }
            } else {
                "【本地暂无该主题规则】: 请基于工业物联网私有协议通用规范（帧头AA 55/EB 90/Modbus/TLV/定长帧/心跳包等）进行逆向反推与字节切片分析。"
            }

            val inspectionPrompt = """
                请作为资深工业物联网协议逆向与排查专家，深度透视并解码以下捕获到的 MQTT 报文：
                
                【报文主题 Topic】: ${packet.topic}
                【报文序号】: ${packet.packetSeq}
                【接收时间】: ${packet.timestamp}
                【QoS 等级】: ${packet.qos}
                【数据载荷 Payload】:
                ```
                ${packet.payload}
                ```
                
                $protocolContext
                
                【透视解析要求】
                请严格输出以下结构化内容：
                1. ### 1. 协议特征与类型推断
                   - 判定报文类型（心跳包/设备体征数据/控制应答/物模型JSON/私有Hex定长帧等）
                   - 识别报文格式（HEX 十六进制、UTF-8 文本或 JSON）
                2. ### 2. 字段字节切片对照表 (核心)
                   输出 Markdown 表格，列名包含: | 偏移量(Offset) | 字段名称 | 原始十六进制/原始值 | 物理量解析值 | 工程单位 | 详细说明 |
                   (若为 Hex 报文，逐字节切片拆解帧头、设备号、功能码、数据区各指标、校验码；若为 JSON，拆解各 key 含义)
                3. ### 3. 校验码审计与数值健康度
                   - CRC/LRC/累加和校验判定（推算校验算法与是否匹配）
                   - 关键指标阈值判断（是否有超标、异常体征或故障报警标志位）
                4. ### 4. 建议与协议沉淀
                   - 给出 SI 现场排查建议或一键存入协议知识库的建议说明
            """.trimIndent()

            val tempHistory = listOf(
                AiChatMessage(
                    id = UUID.randomUUID().toString(),
                    role = "user",
                    content = inspectionPrompt,
                    timestamp = System.currentTimeMillis()
                )
            )

            aiAgentClient.chatStream(
                config = cfg,
                conversationHistory = tempHistory,
                onChunk = { delta, isThinking ->
                    if (isThinking) {
                        packetInspectionThinking.value += delta
                    } else {
                        packetInspectionResult.value += delta
                    }
                },
                onToolAction = { _ -> },
                onError = { err ->
                    packetInspectionResult.value = "AI 透视解析失败: $err"
                    isPacketInspecting.value = false
                },
                onComplete = { _, _ ->
                    isPacketInspecting.value = false
                }
            )
        }
    }

    fun dismissPacketInspection() {
        packetInspectJob?.cancel()
        packetInspectJob = null
        inspectingPacket.value = null
        isPacketInspecting.value = false
        packetInspectionResult.value = ""
        packetInspectionThinking.value = ""
    }

    fun continueInspectionInChat(packet: MqttLogPacket) {
        val analysis = packetInspectionResult.value
        dismissPacketInspection()
        navigateTo(AppScreen.AiChat)
        val followUpPrompt = "关于报文 [${packet.topic}] (Payload: ${packet.payload.take(60)}...) 的 AI 透视结果，我想深入追问："
        sendAiMessage(followUpPrompt)
    }

    fun saveInspectionAsProtocolKnowledge(packet: MqttLogPacket, ruleName: String, description: String) {
        val proto = ProtocolKnowledge(
            id = UUID.randomUUID().toString(),
            name = ruleName.ifBlank { packet.topic.substringAfterLast('/') + " 协议规则" },
            topicFilter = packet.topic,
            description = description.ifBlank { packetInspectionResult.value },
            sampleHex = packet.payload,
            createdAt = System.currentTimeMillis()
        )
        saveProtocolKnowledge(proto)
        showToast("已将此报文特征成功沉淀为新协议规则！")
    }

    // --- 场景 4: 一键生成“SI 现场验收与排查工程报告” ---
    fun generateFieldAcceptanceReport() {
        navigateTo(AppScreen.AiChat)
        val prompt = "请全面盘点当前 MQTT Broker 采集到的所有网关数据、内存实时流与通信质量，生成一份标准的《MQTT 工业物联网现场验收与排查工程报告》。请调用工具查询真实数据，报告必须包含：1. 现场工程概况；2. 网关与设备在线清单及吞吐；3. 通信质量与连通性评估；4. 业务指标与私有协议解码审计；5. 整改建议与交付验收结论。"
        sendAiMessage(prompt)
    }

    // --- 场景 5: 外部 Excel 日志免权限导入与权限自愈 ---

    fun hasAllFilesAccess(context: Context): Boolean = ArchivedExcelReader.hasAllFilesAccess(context)

    fun openAllFilesAccessSettings(context: Context) = ArchivedExcelReader.openAllFilesAccessSettings(context)

    fun importExternalExcel(context: Context, uri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            val result = ArchivedExcelReader.importExcelFromUri(context, uri)
            result.onSuccess { file ->
                val summary = ArchivedExcelReader.analyzeExcelSummary(context, file.name)
                val rows = summary?.totalRows ?: 0
                val hint = if (rows > 0) {
                    "📂 已成功导入外部归档日志「${file.name}」（经原生流式引擎预检共包含 $rows 条报文，时间跨度: ${summary?.startTime ?: "-"} ~ ${summary?.endTime ?: "-"}）。我已为您建立分析就绪态，请告诉我您想了解什么？（例如：分析异常体征、统计热门主题、排查网关掉线等）"
                } else {
                    "📂 已成功导入外部 Excel 日志「${file.name}」，已存入应用内部安全目录。您可以直接向我提问分析该文件！"
                }
                withContext(Dispatchers.Main) {
                    showToast("成功导入: ${file.name}")
                    val session = currentSessionId.value
                    val welcomeMsg = AiChatMessage(
                        id = UUID.randomUUID().toString(),
                        sessionId = session,
                        role = "assistant",
                        content = hint,
                        timestamp = System.currentTimeMillis()
                    )
                    aiMessages.update { it + welcomeMsg }
                    storage.saveAiMessage(welcomeMsg)
                }
            }.onFailure { err ->
                withContext(Dispatchers.Main) {
                    showToast("导入失败: ${err.message}")
                }
            }
        }
    }

    // =========================================================================
    // TSL 物模型协议库管理方法
    // =========================================================================

    fun saveTslProtocol(protocol: com.example.model.TslProtocol) {
        viewModelScope.launch(Dispatchers.IO) {
            storage.saveTslProtocol(protocol)
            val updated = storage.loadEnabledTslProtocols()
            withContext(Dispatchers.Main) {
                tslProtocols.value = updated
                reparseAllLivePacketsWithTsl()
                showToast("TSL 协议【${protocol.name}】已保存并生效！")
            }
        }
    }

    fun deleteTslProtocol(id: String) {
        viewModelScope.launch(Dispatchers.IO) {
            storage.deleteTslProtocol(id)
            val updated = storage.loadEnabledTslProtocols()
            withContext(Dispatchers.Main) {
                tslProtocols.value = updated
                reparseAllLivePacketsWithTsl()
                showToast("协议已删除")
            }
        }
    }

    fun toggleTslProtocol(id: String, enabled: Boolean) {
        viewModelScope.launch(Dispatchers.IO) {
            storage.toggleTslProtocolEnabled(id, enabled)
            val updated = storage.loadEnabledTslProtocols()
            withContext(Dispatchers.Main) {
                tslProtocols.value = updated
                reparseAllLivePacketsWithTsl()
                showToast(if (enabled) "协议已启用并实时生效" else "协议已停用")
            }
        }
    }

    fun importTslProtocolFromJson(jsonStr: String): Boolean {
        return try {
            val json = org.json.JSONObject(jsonStr.trim())
            val protocol = com.example.model.TslProtocol.fromJson(json)
            saveTslProtocol(protocol)
            true
        } catch (e: Exception) {
            showToast("TSL 协议导入失败: ${e.message}")
            false
        }
    }

    fun reparseAllLivePacketsWithTsl() {
        val protos = tslProtocols.value
        val packets = livePackets.value
        if (protos.isEmpty() || packets.isEmpty()) {
            tslParseResults.value = emptyMap()
            return
        }
        val map = mutableMapOf<String, com.example.model.TslParseResult>()
        for (pkt in packets) {
            val res = com.example.engine.TslParseEngine.tryParse(
                topic = pkt.topic,
                payload = pkt.payload,
                category = pkt.category,
                protocols = protos
            )
            if (res != null) {
                map[pkt.id] = res
            }
        }
        tslParseResults.value = map
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
