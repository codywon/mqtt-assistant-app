package com.example.model

enum class AppScreen(
    val route: String,
    val title: String,
    val navLabel: String,
    val testTag: String
) {
    LiveLogs(
        route = "live-logs",
        title = "消息 - MQTT Assistant",
        navLabel = "消息",
        testTag = "live-logs"
    ),
    Publish(
        route = "publish",
        title = "发布 - MQTT Assistant",
        navLabel = "发布",
        testTag = "publish"
    ),
    Subscribe(
        route = "subscribe",
        title = "订阅 - MQTT Assistant",
        navLabel = "订阅",
        testTag = "subscribe"
    ),
    Settings(
        route = "settings",
        title = "设置 - MQTT Assistant",
        navLabel = "设置",
        testTag = "settings"
    )
}

enum class MqttConnectionState(val label: String) {
    DISCONNECTED("未连接"),
    CONNECTING("连接中"),
    CONNECTED("已连接"),
    RECONNECTING("自动重连中"),
    ERROR("连接异常")
}

data class PublishPreset(
    val id: String,
    val name: String,
    val topic: String,
    val qos: Int,
    val retain: Boolean,
    val payload: String
)

data class PublishHistoryItem(
    val id: String,
    val topic: String,
    val qos: Int,
    val timestamp: String,
    val payload: String,
    val dotColorHex: Long
)

data class SubscriptionItem(
    val id: String,
    val topic: String,
    val qos: Int,
    val msgCount: Int,
    val lastTimeText: String,
    val isEnabled: Boolean,
    val dotColorHex: Long,
    val name: String = "",
    val retainHandling: Int = 0 // 0 = 始终发送保留消息, 1 = 仅首次订阅发送, 2 = 订阅时不发送保留消息
)

data class MqttLogPacket(
    val id: String,
    val topic: String,
    val qos: Int,
    val packetSeq: String,
    val timestamp: String,
    val payload: String,
    val devInfo: String,
    val sizeText: String,
    val category: String,
    val dotColorHex: Long
)

data class TopicFilterRule(
    val id: String,
    val pattern: String,
    val isExclude: Boolean = false, // true 为排除条件，false 为包含条件
    val enabled: Boolean = true
)

data class BrokerProfile(
    val id: String,
    val name: String,
    val host: String,
    val port: Int = 1883,
    val clientId: String = "",
    val username: String = "",
    val password: String = "",
    val protocol: String = "MQTT 3.1.1",
    val cleanSession: Boolean = true,
    val tlsEnabled: Boolean = false,
    val keepAlive: Int = 60
)

data class MqttServerConfig(
    val activeProfileId: String = "b1",
    val host: String = "broker.emqx.io",
    val port: Int = 1883,
    val keepAlive: Int = 60,
    val clientId: String = "",
    val username: String = "",
    val password: String = "",
    val protocol: String = "MQTT 3.1.1",
    val cleanSession: Boolean = true,
    val autoReconnect: Boolean = true,
    val reconnectIntervalSeconds: Int = 5,
    val maxReconnectAttempts: Int = 3,
    val backgroundKeepAliveEnabled: Boolean = true,
    val wakeLockEnabled: Boolean = true,
    val autoStartEnabled: Boolean = false,
    val processGuardEnabled: Boolean = true,
    val tlsEnabled: Boolean = false,
    val autoRotate: Boolean = true,
    val autoExportExcel: Boolean = false,
    val bufferThreshold: Int = 10000,
    val usedSpaceMb: Double = 1.2,
    val packetCount: Int = 0,
    val isConnected: Boolean = false
)
