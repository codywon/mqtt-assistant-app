package com.example.util

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import com.example.model.BrokerProfile
import com.example.model.MqttServerConfig
import com.example.model.PublishPreset
import com.example.model.SubscriptionItem
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

data class BackupData(
    val version: Int,
    val exportedAt: String,
    val activeBrokerId: String,
    val brokerProfiles: List<BrokerProfile>,
    val publishPresets: List<PublishPreset>,
    val subscriptions: List<SubscriptionItem>,
    val autoReconnect: Boolean,
    val reconnectIntervalSeconds: Int,
    val maxReconnectAttempts: Int,
    val autoRotate: Boolean,
    val bufferThreshold: Int,
    val backgroundKeepAlive: Boolean,
    val wakeLockEnabled: Boolean,
    val autoStartEnabled: Boolean = false,
    val processGuardEnabled: Boolean = true,
    val includeFilters: List<String>,
    val excludeFilters: List<String>
)

object ConfigBackupHelper {

    const val TOKEN_PREFIX = "#MQTT-CFG#:"

    fun buildBackupJsonObject(
        profiles: List<BrokerProfile>,
        activeId: String,
        presets: List<PublishPreset>,
        subs: List<SubscriptionItem>,
        serverConfig: MqttServerConfig,
        includeFilters: List<String>,
        excludeFilters: List<String>
    ): JSONObject {
        val root = JSONObject()
        root.put("version", 1)
        root.put("app", "MQTT-Assistant")
        root.put("exportedAt", SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date()))
        root.put("activeBrokerId", activeId)

        // 1. Global & Engine Settings
        val configObj = JSONObject().apply {
            put("autoReconnect", serverConfig.autoReconnect)
            put("reconnectIntervalSeconds", serverConfig.reconnectIntervalSeconds)
            put("maxReconnectAttempts", serverConfig.maxReconnectAttempts)
            put("autoRotate", serverConfig.autoRotate)
            put("bufferThreshold", serverConfig.bufferThreshold)
            put("backgroundKeepAlive", serverConfig.backgroundKeepAliveEnabled)
            put("wakeLockEnabled", serverConfig.wakeLockEnabled)
            put("autoStartEnabled", serverConfig.autoStartEnabled)
            put("processGuardEnabled", serverConfig.processGuardEnabled)
            val incArray = JSONArray()
            includeFilters.forEach { incArray.put(it) }
            put("includeFilters", incArray)
            val excArray = JSONArray()
            excludeFilters.forEach { excArray.put(it) }
            put("excludeFilters", excArray)
        }
        root.put("globalSettings", configObj)

        // 2. Broker Profiles
        val brokerArray = JSONArray()
        profiles.forEach { b ->
            val bObj = JSONObject().apply {
                put("id", b.id)
                put("name", b.name)
                put("host", b.host)
                put("port", b.port)
                put("clientId", b.clientId)
                put("username", b.username)
                put("password", b.password)
                put("protocol", b.protocol)
                put("cleanSession", b.cleanSession)
                put("tlsEnabled", b.tlsEnabled)
                put("keepAlive", b.keepAlive)
            }
            brokerArray.put(bObj)
        }
        root.put("brokerProfiles", brokerArray)

        // 3. Publish Presets
        val presetArray = JSONArray()
        presets.forEach { p ->
            val pObj = JSONObject().apply {
                put("id", p.id)
                put("name", p.name)
                put("topic", p.topic)
                put("qos", p.qos)
                put("retain", p.retain)
                put("payload", p.payload)
            }
            presetArray.put(pObj)
        }
        root.put("publishPresets", presetArray)

        // 4. Subscriptions
        val subArray = JSONArray()
        subs.forEach { s ->
            val sObj = JSONObject().apply {
                put("id", s.id)
                put("name", s.name)
                put("topic", s.topic)
                put("qos", s.qos)
                put("isEnabled", s.isEnabled)
                put("dotColorHex", s.dotColorHex)
                put("retainHandling", s.retainHandling)
            }
            subArray.put(sObj)
        }
        root.put("subscriptions", subArray)

        return root
    }

    fun exportConfigToJson(
        context: Context,
        profiles: List<BrokerProfile>,
        activeId: String,
        presets: List<PublishPreset>,
        subs: List<SubscriptionItem>,
        serverConfig: MqttServerConfig,
        includeFilters: List<String>,
        excludeFilters: List<String>
    ): File {
        val root = buildBackupJsonObject(
            profiles, activeId, presets, subs, serverConfig, includeFilters, excludeFilters
        )
        // Write to cache exports directory
        val exportDir = File(context.cacheDir, "exports").apply { if (!exists()) mkdirs() }
        val timeTag = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val file = File(exportDir, "mqtt_assistant_backup_$timeTag.json")
        file.writeText(root.toString(2), Charsets.UTF_8)
        return file
    }

    /**
     * 生成超紧凑的 GZIP+Base64 口令字符串，方便用户在聊天软件中一键复制互传
     */
    fun exportConfigToToken(
        profiles: List<BrokerProfile>,
        activeId: String,
        presets: List<PublishPreset>,
        subs: List<SubscriptionItem>,
        serverConfig: MqttServerConfig,
        includeFilters: List<String>,
        excludeFilters: List<String>
    ): String {
        val root = buildBackupJsonObject(
            profiles, activeId, presets, subs, serverConfig, includeFilters, excludeFilters
        )
        val jsonStr = root.toString()
        val byteStream = java.io.ByteArrayOutputStream()
        java.util.zip.GZIPOutputStream(byteStream).use { gzip ->
            gzip.write(jsonStr.toByteArray(Charsets.UTF_8))
        }
        val base64 = android.util.Base64.encodeToString(byteStream.toByteArray(), android.util.Base64.NO_WRAP)
        return "$TOKEN_PREFIX$base64"
    }

    /**
     * 从口令字符串或原生 JSON 还原解析配置对象
     */
    fun parseConfigFromToken(tokenText: String): BackupData {
        val trimmed = tokenText.trim()
        val jsonString = if (trimmed.startsWith(TOKEN_PREFIX)) {
            val base64Part = trimmed.removePrefix(TOKEN_PREFIX).trim()
            val compressedBytes = android.util.Base64.decode(base64Part, android.util.Base64.DEFAULT)
            val byteIn = java.io.ByteArrayInputStream(compressedBytes)
            java.util.zip.GZIPInputStream(byteIn).bufferedReader(Charsets.UTF_8).use { it.readText() }
        } else if (trimmed.startsWith("{") && trimmed.endsWith("}")) {
            trimmed
        } else {
            throw IllegalArgumentException("剪贴板内容不是有效的 MQTT 助手配置口令")
        }
        return parseBackupJson(jsonString)
    }

    fun shareBackupFile(context: Context, file: File, chooserTitle: String = "备份配置导出 (保存到本地或分享)") {
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )

        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "application/json"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, "MQTT-Assistant 配置备份")
            putExtra(Intent.EXTRA_TEXT, "已生成 MQTT 助手完整配置备份文件：${file.name}")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }

        val chooser = Intent.createChooser(intent, chooserTitle).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(chooser)
    }

    fun parseBackupJson(jsonString: String): BackupData {
        val root = JSONObject(jsonString)

        val version = root.optInt("version", 1)
        val exportedAt = root.optString("exportedAt", "")
        val activeBrokerId = root.optString("activeBrokerId", "")

        // 1. Broker profiles
        val profiles = mutableListOf<BrokerProfile>()
        if (root.has("brokerProfiles")) {
            val arr = root.getJSONArray("brokerProfiles")
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                profiles.add(
                    BrokerProfile(
                        id = obj.optString("id", UUID.randomUUID().toString()),
                        name = obj.optString("name", "Broker $i"),
                        host = obj.optString("host", ""),
                        port = obj.optInt("port", 1883),
                        clientId = obj.optString("clientId", ""),
                        username = obj.optString("username", ""),
                        password = obj.optString("password", ""),
                        protocol = obj.optString("protocol", "MQTT 3.1.1"),
                        cleanSession = obj.optBoolean("cleanSession", true),
                        tlsEnabled = obj.optBoolean("tlsEnabled", false),
                        keepAlive = obj.optInt("keepAlive", 60)
                    )
                )
            }
        }

        // 2. Publish Presets
        val presets = mutableListOf<PublishPreset>()
        if (root.has("publishPresets")) {
            val arr = root.getJSONArray("publishPresets")
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                presets.add(
                    PublishPreset(
                        id = obj.optString("id", UUID.randomUUID().toString()),
                        name = obj.optString("name", "预设 $i"),
                        topic = obj.optString("topic", ""),
                        qos = obj.optInt("qos", 0),
                        retain = obj.optBoolean("retain", false),
                        payload = obj.optString("payload", "{}")
                    )
                )
            }
        }

        // 3. Subscriptions
        val subs = mutableListOf<SubscriptionItem>()
        if (root.has("subscriptions")) {
            val arr = root.getJSONArray("subscriptions")
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                subs.add(
                    SubscriptionItem(
                        id = obj.optString("id", UUID.randomUUID().toString()),
                        topic = obj.optString("topic", ""),
                        qos = obj.optInt("qos", 0),
                        msgCount = 0,
                        lastTimeText = "刚刚恢复",
                        isEnabled = obj.optBoolean("isEnabled", true),
                        dotColorHex = obj.optLong("dotColorHex", 0xFF10B981),
                        name = obj.optString("name", ""),
                        retainHandling = obj.optInt("retainHandling", 0)
                    )
                )
            }
        }

        // 4. Global & Engine Settings
        var autoReconnect = true
        var reconnectIntervalSeconds = 5
        var maxReconnectAttempts = 3
        var autoRotate = true
        var bufferThreshold = 10000
        var backgroundKeepAlive = true
        var wakeLockEnabled = true
        val includeFilters = mutableListOf<String>()
        val excludeFilters = mutableListOf<String>()

        if (root.has("globalSettings")) {
            val cfg = root.getJSONObject("globalSettings")
            autoReconnect = cfg.optBoolean("autoReconnect", true)
            reconnectIntervalSeconds = cfg.optInt("reconnectIntervalSeconds", 5)
            maxReconnectAttempts = cfg.optInt("maxReconnectAttempts", 3)
            autoRotate = cfg.optBoolean("autoRotate", true)
            bufferThreshold = cfg.optInt("bufferThreshold", 10000)
            backgroundKeepAlive = cfg.optBoolean("backgroundKeepAlive", true)
            wakeLockEnabled = cfg.optBoolean("wakeLockEnabled", true)
            val autoStart = cfg.optBoolean("autoStartEnabled", false)

            if (cfg.has("includeFilters")) {
                val arr = cfg.getJSONArray("includeFilters")
                for (i in 0 until arr.length()) includeFilters.add(arr.getString(i))
            }
            if (cfg.has("excludeFilters")) {
                val arr = cfg.getJSONArray("excludeFilters")
                for (i in 0 until arr.length()) excludeFilters.add(arr.getString(i))
            }
        }

        return BackupData(
            version = version,
            exportedAt = exportedAt,
            activeBrokerId = activeBrokerId,
            brokerProfiles = profiles,
            publishPresets = presets,
            subscriptions = subs,
            autoReconnect = autoReconnect,
            reconnectIntervalSeconds = reconnectIntervalSeconds,
            maxReconnectAttempts = maxReconnectAttempts,
            autoRotate = autoRotate,
            bufferThreshold = bufferThreshold,
            backgroundKeepAlive = backgroundKeepAlive,
            wakeLockEnabled = wakeLockEnabled,
            autoStartEnabled = if (root.has("globalSettings")) root.getJSONObject("globalSettings").optBoolean("autoStartEnabled", false) else false,
            processGuardEnabled = if (root.has("globalSettings")) root.getJSONObject("globalSettings").optBoolean("processGuardEnabled", true) else true,
            includeFilters = includeFilters,
            excludeFilters = excludeFilters
        )
    }
}
