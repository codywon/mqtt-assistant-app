package com.example.viewmodel

import android.app.Application
import android.content.Context
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
import com.example.util.MqttTopicUtil
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

class MqttAssistantViewModel(application: Application) : AndroidViewModel(application) {

    private val storage = MqttStorageRepository(application.applicationContext)

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
    private var packetSeqCounter = 0L

    val isForegroundKeepAliveRunning = MutableStateFlow(false)

    // --- Live Packet Log States ---
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
            val initialBroker = initialProfiles.find { it.id == initialActiveId } ?: initialProfiles.first()
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
                backgroundKeepAliveEnabled = storage.loadBackgroundKeepAlive(),
                wakeLockEnabled = storage.loadWakeLock()
            )
        }
    )
    val isPasswordVisible = MutableStateFlow(false)
    val isSavingSettings = MutableStateFlow(false)

    init {
        setupMqttCallbacks()
        connectToBroker()
        if (serverConfig.value.backgroundKeepAliveEnabled) {
            val brokerHost = "${serverConfig.value.host}:${serverConfig.value.port}"
            MqttBackgroundService.startKeepAlive(application, brokerHost)
            isForegroundKeepAliveRunning.value = true
        }
    }

    private fun setupMqttCallbacks() {
        MqttClientManager.onMessageReceived = { topic, qos, payloadBytes, retain ->
            // PC-Grade Topic Filtering: Exclude rules have highest priority!
            if (MqttTopicUtil.isTopicAllowed(topic, includeTopicFilters.value, excludeTopicFilters.value) && !isRecordingPaused.value) {
                val timeStr = SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault()).format(Date())
                val payloadString = try {
                    String(payloadBytes, Charsets.UTF_8)
                } catch (e: Exception) {
                    payloadBytes.joinToString(" ") { "%02X".format(it) }
                }
                packetSeqCounter++
                val seq = "#%04d".format(packetSeqCounter)

                val matchingSub = subscriptions.value.firstOrNull { sub ->
                    MqttTopicUtil.matchesMqttTopic(sub.topic, topic)
                }
                val dotColor = matchingSub?.dotColorHex ?: 0xFF10B981
                val cat = matchingSub?.name?.ifBlank { matchingSub.topic } ?: topic.substringBefore('/')

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

                livePackets.update { current ->
                    (listOf(packet) + current).take(serverConfig.value.bufferThreshold)
                }

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
            }
        }

        MqttClientManager.onConnectionStateChanged = { isConn, cause ->
            if (isConn) {
                connectionState.value = MqttConnectionState.CONNECTED
                serverConfig.update { it.copy(isConnected = true) }
                reconnectAttempt.value = 0
                reconnectCountdown.value = 0
                persistCurrentActiveBrokerProfile()
                // Auto-subscribe all enabled subscriptions on the connected broker
                viewModelScope.launch {
                    val activeSubs = subscriptions.value.filter { it.isEnabled }
                    activeSubs.forEach { sub ->
                        MqttClientManager.subscribe(sub.topic, sub.qos)
                    }
                }
                // Auto start foreground keepalive service for persistent background connection
                if (serverConfig.value.backgroundKeepAliveEnabled) {
                    val brokerHost = "${serverConfig.value.host}:${serverConfig.value.port}"
                    MqttBackgroundService.startKeepAlive(getApplication(), brokerHost)
                    isForegroundKeepAliveRunning.value = true
                }
            } else {
                connectionState.value = MqttConnectionState.DISCONNECTED
                serverConfig.update { it.copy(isConnected = false) }
                // Only trigger auto-reconnect if it's an unexpected connection loss (cause != null)
                if (cause != null && serverConfig.value.autoReconnect && reconnectJob?.isActive != true) {
                    startAutoReconnectLoop()
                }
            }
        }
    }

    fun connectToBroker() {
        reconnectJob?.cancel()
        reconnectCountdown.value = 0
        viewModelScope.launch {
            connectionState.value = MqttConnectionState.CONNECTING
            val result = MqttClientManager.connect(serverConfig.value)
            if (result.isSuccess) {
                connectionState.value = MqttConnectionState.CONNECTED
                serverConfig.update { it.copy(isConnected = true) }
                persistCurrentActiveBrokerProfile()
                val activeSubs = subscriptions.value.filter { it.isEnabled }
                activeSubs.forEach { sub ->
                    MqttClientManager.subscribe(sub.topic, sub.qos)
                }
                if (serverConfig.value.backgroundKeepAliveEnabled) {
                    val brokerHost = "${serverConfig.value.host}:${serverConfig.value.port}"
                    MqttBackgroundService.startKeepAlive(getApplication(), brokerHost)
                    isForegroundKeepAliveRunning.value = true
                }
                showToast("已连接至 ${serverConfig.value.host}:${serverConfig.value.port} (已激活 ${activeSubs.size} 个主题)")
            } else {
                connectionState.value = MqttConnectionState.DISCONNECTED
                serverConfig.update { it.copy(isConnected = false) }
                val errorMsg = MqttClientManager.getReadableErrorMessage(
                    result.exceptionOrNull() ?: Exception("连接失败"),
                    serverConfig.value.host,
                    serverConfig.value.port
                )
                showToast("连接异常: $errorMsg")
                if (serverConfig.value.autoReconnect) {
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

        // If this broker is currently active, sync serverConfig
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
        }
        showToast("已保存 Broker 节点: ${broker.name}")
    }

    fun deleteBroker(brokerId: String) {
        if (brokerProfiles.value.size <= 1) {
            showToast("至少需要保留一个 Broker 节点")
            return
        }
        val isDeletingActive = brokerId == activeBrokerId.value
        val remaining = brokerProfiles.value.filter { it.id != brokerId }
        brokerProfiles.value = remaining
        storage.saveBrokerProfiles(remaining)
        if (isDeletingActive) {
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

            packetSeqCounter++
            val packet = MqttLogPacket(
                id = UUID.randomUUID().toString(),
                topic = preset.topic,
                qos = preset.qos,
                packetSeq = "#%04d".format(packetSeqCounter),
                timestamp = SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault()).format(Date()),
                payload = preset.payload,
                devInfo = "PUB · ${payloadBytes.size}B" + if (result.isSuccess) " · 已送达" else " · 发送失败",
                sizeText = "${payloadBytes.size}B",
                category = preset.name.ifBlank { preset.topic.substringBefore('/') },
                dotColorHex = dotColor
            )
            livePackets.update { (listOf(packet) + it).take(serverConfig.value.bufferThreshold) }

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
            packetSeqCounter++
            val packet = MqttLogPacket(
                id = UUID.randomUUID().toString(),
                topic = topic,
                qos = publishQos.value,
                packetSeq = "#%04d".format(packetSeqCounter),
                timestamp = SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault()).format(Date()),
                payload = publishPayload.value,
                devInfo = "PUB · ${payloadBytes.size}B" + if (result.isSuccess) " · 已送达" else " · 发送失败",
                sizeText = "${payloadBytes.size}B",
                category = topic.substringBefore('/'),
                dotColorHex = dotColor
            )
            livePackets.update { (listOf(packet) + it).take(serverConfig.value.bufferThreshold) }

            if (result.isSuccess) {
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
                    showToast("已保存订阅: ${item.topic}，待网络连接后自动生效")
                }
            } else {
                MqttClientManager.unsubscribe(item.topic)
                showToast("已保存订阅: ${item.topic} (已设为暂停)")
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
        var topic = (customTopic ?: newSubTopic.value).trim()
        val targetQos = qos ?: newSubQos.value
        val isDefault = topic.isBlank()
        if (isDefault) {
            topic = "college/#"
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
            name = name.ifBlank { topic.substringBefore('/') },
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
        showToast(if (isRecordingPaused.value) "流已暂停" else "流已恢复")
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
        serverConfig.update { it.copy(autoReconnect = !it.autoReconnect) }
    }

    fun toggleTls() {
        serverConfig.update { it.copy(tlsEnabled = !it.tlsEnabled) }
        showToast(if (serverConfig.value.tlsEnabled) "已启用 TLS 加密" else "已停用 TLS 加密")
    }

    fun toggleAutoRotate() {
        serverConfig.update { it.copy(autoRotate = !it.autoRotate) }
    }

    fun updateBufferThreshold(thStr: String) {
        val th = thStr.toIntOrNull() ?: 10000
        serverConfig.update { it.copy(bufferThreshold = th) }
    }

    fun triggerManualReconnect() {
        reconnectJob?.cancel()
        reconnectAttempt.value = 0
        reconnectCountdown.value = 0
        reconnectJob = viewModelScope.launch {
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
                    serverConfig.value.port
                )
                showToast("连接失败: $errorMsg")
                if (serverConfig.value.autoReconnect) {
                    startAutoReconnectLoop()
                }
            }
        }
    }

    fun startAutoReconnectLoop() {
        if (!serverConfig.value.autoReconnect) return
        reconnectJob?.cancel()
        reconnectJob = viewModelScope.launch {
            connectionState.value = MqttConnectionState.RECONNECTING
            reconnectAttempt.value += 1
            // 工业级优雅退避重试：前 3 次使用用户设置间隔（默认 5s），后续按 10s 周期性重试，直到网络就绪连上
            val baseSec = serverConfig.value.reconnectIntervalSeconds.coerceAtLeast(3)
            val interval = if (reconnectAttempt.value <= 3) baseSec else 10
            for (sec in interval downTo 1) {
                reconnectCountdown.value = sec
                delay(1000)
            }
            reconnectCountdown.value = 0
            connectionState.value = MqttConnectionState.CONNECTING
            val result = MqttClientManager.connect(serverConfig.value)
            if (result.isSuccess) {
                connectionState.value = MqttConnectionState.CONNECTED
                serverConfig.update { it.copy(isConnected = true) }
                reconnectAttempt.value = 0
                persistCurrentActiveBrokerProfile()
                val activeSubs = subscriptions.value.filter { it.isEnabled }
                activeSubs.forEach { sub ->
                    MqttClientManager.subscribe(sub.topic, sub.qos)
                }
                showToast("网络恢复，已自动重连 Broker！已恢复 ${activeSubs.size} 个主题订阅")
            } else {
                connectionState.value = MqttConnectionState.DISCONNECTED
                serverConfig.update { it.copy(isConnected = false) }
                if (serverConfig.value.autoReconnect) {
                    startAutoReconnectLoop()
                }
            }
        }
    }

    fun disconnectBroker(isManual: Boolean = true) {
        reconnectJob?.cancel()
        viewModelScope.launch {
            MqttClientManager.disconnect()
        }
        connectionState.value = MqttConnectionState.DISCONNECTED
        serverConfig.update { it.copy(isConnected = false) }
        if (!isManual && serverConfig.value.autoReconnect) {
            startAutoReconnectLoop()
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

    fun clearAllData() {
        publishHistory.value = emptyList()
        livePackets.value = emptyList()
        serverConfig.update { it.copy(usedSpaceMb = 0.0, packetCount = 0) }
        showToast("本地历史日志与未发缓存已清空")
    }

    fun saveAndApplySettings() {
        viewModelScope.launch {
            isSavingSettings.value = true
            showToast("正在保存并重新连接 Broker...")

            // Update the active broker profile in storage with current form values
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

            MqttClientManager.disconnect()
            val result = MqttClientManager.connect(serverConfig.value)
            isSavingSettings.value = false
            if (result.isSuccess) {
                connectionState.value = MqttConnectionState.CONNECTED
                serverConfig.update { it.copy(isConnected = true) }
                val activeSubs = subscriptions.value.filter { it.isEnabled }
                activeSubs.forEach { sub ->
                    MqttClientManager.subscribe(sub.topic, sub.qos)
                }
                showToast("已成功保存并连接至 ${serverConfig.value.host}:${serverConfig.value.port} (已激活 ${activeSubs.size} 个订阅)")
            } else {
                connectionState.value = MqttConnectionState.DISCONNECTED
                serverConfig.update { it.copy(isConnected = false) }
                showToast("连接失败: ${result.exceptionOrNull()?.message}")
            }
        }
    }
}
