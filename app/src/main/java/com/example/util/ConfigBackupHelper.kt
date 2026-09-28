package com.example.util

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import com.example.model.AiAgentConfig
import com.example.model.BrokerProfile
import com.example.model.MqttServerConfig
import com.example.model.ProtocolKnowledge
import com.example.model.PublishPreset
import com.example.model.SubscriptionItem
import com.example.model.TslProtocol
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
    val autoExportExcel: Boolean = false,
    val bufferThreshold: Int,
    val backgroundKeepAlive: Boolean,
    val wakeLockEnabled: Boolean,
    val autoStartEnabled: Boolean = false,
    val processGuardEnabled: Boolean = true,
    val includeFilters: List<String>,
    val excludeFilters: List<String>,
    val aiConfig: AiAgentConfig? = null,
    val protocolKnowledgeList: List<ProtocolKnowledge> = emptyList()
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
        excludeFilters: List<String>,
        aiConfig: AiAgentConfig? = null,
        protocols: List<ProtocolKnowledge> = emptyList()
    ): JSONObject {
        val root = JSONObject()
        root.put("version", 2)
        root.put("app", "MQTT-Assistant")
        root.put("exportedAt", SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date()))
        root.put("activeBrokerId", activeId)

        // 1. Global & Engine Settings
        val configObj = JSONObject().apply {
            put("autoReconnect", serverConfig.autoReconnect)
            put("reconnectIntervalSeconds", serverConfig.reconnectIntervalSeconds)
            put("maxReconnectAttempts", serverConfig.maxReconnectAttempts)
            put("autoRotate", serverConfig.autoRotate)
            put("autoExportExcel", serverConfig.autoExportExcel)
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

        // 5. AI Agent & LLM Configuration
        if (aiConfig != null) {
            val aiObj = JSONObject().apply {
                put("apiKey", aiConfig.apiKey)
                put("baseUrl", aiConfig.baseUrl)
                put("modelName", aiConfig.modelName)
                put("customPrompt", aiConfig.customPrompt)
                put("temperature", aiConfig.temperature)
                put("maxTokens", aiConfig.maxTokens)
                put("contextWindow", aiConfig.contextWindow)
                put("compactionThreshold", aiConfig.compactionThreshold)
            }
            root.put("aiConfig", aiObj)
        }

        // 6. Protocol Knowledge Clarification Workbench
        if (protocols.isNotEmpty()) {
            val protoArr = JSONArray()
            protocols.forEach { p ->
                protoArr.put(
                    JSONObject().apply {
                        put("id", p.id)
                        put("name", p.name)
                        put("topicFilter", p.topicFilter)
                        put("description", p.description)
                        put("sampleHex", p.sampleHex)
                        put("createdAt", p.createdAt)
                    }
                )
            }
            root.put("protocolKnowledgeList", protoArr)
        }

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
        excludeFilters: List<String>,
        aiConfig: AiAgentConfig? = null,
        protocols: List<ProtocolKnowledge> = emptyList()
    ): File {
        val root = buildBackupJsonObject(
            profiles, activeId, presets, subs, serverConfig, includeFilters, excludeFilters, aiConfig, protocols
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
        excludeFilters: List<String>,
        aiConfig: AiAgentConfig? = null,
        protocols: List<ProtocolKnowledge> = emptyList()
    ): String {
        val root = buildBackupJsonObject(
            profiles, activeId, presets, subs, serverConfig, includeFilters, excludeFilters, aiConfig, protocols
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
                        retainHandling = obj.optInt("retainHandling", 2)
                    )
                )
            }
        }

        // 4. Global & Engine Settings
        var autoReconnect = true
        var reconnectIntervalSeconds = 5
        var maxReconnectAttempts = 3
        var autoRotate = true
        var autoExportExcel = false
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
            autoExportExcel = cfg.optBoolean("autoExportExcel", false)
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

        // 5. AI Agent & LLM Configuration
        var aiConfig: AiAgentConfig? = null
        if (root.has("aiConfig")) {
            val a = root.getJSONObject("aiConfig")
            aiConfig = AiAgentConfig(
                apiKey = a.optString("apiKey", ""),
                baseUrl = a.optString("baseUrl", "https://api.deepseek.com"),
                modelName = a.optString("modelName", "deepseek-v4-flash"),
                customPrompt = a.optString("customPrompt", ""),
                temperature = a.optDouble("temperature", 0.3),
                maxTokens = a.optInt("maxTokens", 2048),
                contextWindow = a.optInt("contextWindow", 1048576),
                compactionThreshold = a.optDouble("compactionThreshold", 0.7)
            )
        }

        // 6. Protocol Knowledge Clarification Workbench
        val protocolList = mutableListOf<ProtocolKnowledge>()
        if (root.has("protocolKnowledgeList")) {
            val arr = root.getJSONArray("protocolKnowledgeList")
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                protocolList.add(
                    ProtocolKnowledge(
                        id = obj.optString("id", UUID.randomUUID().toString()),
                        name = obj.optString("name", "未命名协议"),
                        topicFilter = obj.optString("topicFilter", ""),
                        description = obj.optString("description", ""),
                        sampleHex = obj.optString("sampleHex", ""),
                        createdAt = obj.optLong("createdAt", System.currentTimeMillis())
                    )
                )
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
            autoExportExcel = autoExportExcel,
            bufferThreshold = bufferThreshold,
            backgroundKeepAlive = backgroundKeepAlive,
            wakeLockEnabled = wakeLockEnabled,
            autoStartEnabled = if (root.has("globalSettings")) root.getJSONObject("globalSettings").optBoolean("autoStartEnabled", false) else false,
            processGuardEnabled = if (root.has("globalSettings")) root.getJSONObject("globalSettings").optBoolean("processGuardEnabled", true) else true,
            includeFilters = includeFilters,
            excludeFilters = excludeFilters,
            aiConfig = aiConfig,
            protocolKnowledgeList = protocolList
        )
    }

    // ==============================================================
    // 硬件私有协议澄清库与 TSL 物模型专属导入导出 (支持独立 JSON 与紧凑口令)
    // ==============================================================
    const val PROTOCOL_TOKEN_PREFIX = "#MQTT-PROTO#:"

    data class ParsedProtocolImportResult(
        val protocols: List<ProtocolKnowledge> = emptyList(),
        val tslProtocols: List<TslProtocol> = emptyList()
    )

    /**
     * 将协议库与 TSL 物模型导出为独立格式化 JSON 文件
     */
    fun exportProtocolsToJson(
        context: Context,
        protocols: List<ProtocolKnowledge>,
        tslProtocols: List<TslProtocol> = emptyList()
    ): File {
        val root = JSONObject().apply {
            put("type", "MQTT_PROTOCOL_KNOWLEDGE")
            put("version", 2)
            put("exportedAt", SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date()))
            val arr = JSONArray()
            protocols.forEach { p ->
                arr.put(
                    JSONObject().apply {
                        put("id", p.id)
                        put("name", p.name)
                        put("topicFilter", p.topicFilter)
                        put("description", p.description)
                        put("sampleHex", p.sampleHex)
                        put("createdAt", p.createdAt)
                    }
                )
            }
            put("protocols", arr)
            if (tslProtocols.isNotEmpty()) {
                val tslArr = JSONArray()
                tslProtocols.forEach { tsl ->
                    tslArr.put(tsl.toJson())
                }
                put("tslProtocols", tslArr)
            }
        }
        val exportDir = File(context.cacheDir, "exports").apply { if (!exists()) mkdirs() }
        val timeTag = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val file = File(exportDir, "mqtt_protocols_backup_$timeTag.json")
        file.writeText(root.toString(2), Charsets.UTF_8)
        return file
    }

    /**
     * 将协议库与 TSL 物模型导出为 GZIP+Base64 极速分享口令
     */
    fun exportProtocolsToToken(
        protocols: List<ProtocolKnowledge>,
        tslProtocols: List<TslProtocol> = emptyList()
    ): String {
        val root = JSONObject().apply {
            put("type", "MQTT_PROTOCOL_KNOWLEDGE")
            val arr = JSONArray()
            protocols.forEach { p ->
                arr.put(
                    JSONObject().apply {
                        put("name", p.name)
                        put("topicFilter", p.topicFilter)
                        put("description", p.description)
                        put("sampleHex", p.sampleHex)
                    }
                )
            }
            put("protocols", arr)
            if (tslProtocols.isNotEmpty()) {
                val tslArr = JSONArray()
                tslProtocols.forEach { tsl ->
                    tslArr.put(tsl.toJson())
                }
                put("tslProtocols", tslArr)
            }
        }
        val byteStream = java.io.ByteArrayOutputStream()
        java.util.zip.GZIPOutputStream(byteStream).use { gzip ->
            gzip.write(root.toString().toByteArray(Charsets.UTF_8))
        }
        val base64 = android.util.Base64.encodeToString(byteStream.toByteArray(), android.util.Base64.NO_WRAP)
        return "$PROTOCOL_TOKEN_PREFIX$base64"
    }

    /**
     * 从独立 JSON 字符串（包含文件内容/文本粘贴）或协议口令解析出协议澄清规则与 TSL 物模型
     * 具备极高容错性：兼容 JSONObject、JSONArray、口令 Base64、混合数组、带 Markdown 格式的代码块
     */
    fun parseProtocolsAndTslFromJsonOrToken(text: String): ParsedProtocolImportResult {
        var trimmed = text.trim()
        if (trimmed.isBlank()) return ParsedProtocolImportResult()

        // 剥离可能包含的 Markdown 代码块标签
        if (trimmed.startsWith("```")) {
            trimmed = trimmed.substringAfter("\n")
            if (trimmed.endsWith("```")) {
                trimmed = trimmed.substringBeforeLast("```").trim()
            }
        }

        val jsonStr = if (trimmed.startsWith(PROTOCOL_TOKEN_PREFIX)) {
            try {
                val base64Part = trimmed.removePrefix(PROTOCOL_TOKEN_PREFIX).trim()
                val compressedBytes = android.util.Base64.decode(base64Part, android.util.Base64.DEFAULT)
                val byteIn = java.io.ByteArrayInputStream(compressedBytes)
                java.util.zip.GZIPInputStream(byteIn).bufferedReader(Charsets.UTF_8).use { it.readText() }
            } catch (e: Exception) {
                return ParsedProtocolImportResult()
            }
        } else {
            trimmed
        }

        val protocols = mutableListOf<ProtocolKnowledge>()
        val tslProtocols = mutableListOf<TslProtocol>()

        fun parseSingleObject(obj: JSONObject) {
            var extractedTsl: TslProtocol? = null

            // 1. 若对象本身带有 fields 数组，直接解析为 TslProtocol
            if (obj.has("fields")) {
                try {
                    extractedTsl = TslProtocol.fromJson(obj)
                } catch (_: Exception) {}
            } else {
                // 2. 若对象本身没有 fields，但 description 字段是包含 fields 的合法 TSL JSON 字符串，深度提取！
                val descStr = obj.optString("description", "").trim()
                if (descStr.startsWith("{") && (descStr.contains("\"fields\"") || descStr.contains("'fields'"))) {
                    try {
                        val descJson = JSONObject(descStr)
                        if (descJson.has("fields")) {
                            val parsed = TslProtocol.fromJson(descJson)
                            // 优先采用外层用户明确指定或修正过的 id, name, topicFilter
                            val finalTopic = obj.optString("topicFilter", obj.optString("matchTopic", parsed.matchTopic)).trim()
                            val finalName = obj.optString("name", parsed.name).trim()
                            val finalId = obj.optString("id", parsed.id).trim()
                            extractedTsl = parsed.copy(
                                id = finalId.ifBlank { parsed.id },
                                name = finalName.ifBlank { parsed.name },
                                matchTopic = finalTopic.ifBlank { parsed.matchTopic }
                            )
                        }
                    } catch (_: Exception) {}
                }
            }

            if (extractedTsl != null) {
                tslProtocols.add(extractedTsl)
            }

            // 同时记录为 ProtocolKnowledge，确保工作台前台可见并可查可改
            val desc = obj.optString("description", "").trim()
            val name = obj.optString("name", extractedTsl?.name ?: "").trim()
            val topic = obj.optString("topicFilter", obj.optString("matchTopic", extractedTsl?.matchTopic ?: "")).trim()
            if (desc.isNotBlank() || name.isNotBlank() || topic.isNotBlank() || extractedTsl != null) {
                protocols.add(
                    ProtocolKnowledge(
                        id = obj.optString("id", extractedTsl?.id ?: UUID.randomUUID().toString()),
                        name = name.ifBlank { extractedTsl?.name ?: "未命名协议" },
                        topicFilter = topic.ifBlank { extractedTsl?.matchTopic ?: "+/+#" },
                        description = if (desc.isNotBlank()) desc else (extractedTsl?.toJson()?.toString(2) ?: name),
                        sampleHex = obj.optString("sampleHex", "").trim(),
                        createdAt = obj.optLong("createdAt", System.currentTimeMillis())
                    )
                )
            }
        }

        try {
            if (jsonStr.startsWith("[")) {
                val arr = JSONArray(jsonStr)
                for (i in 0 until arr.length()) {
                    val item = arr.optJSONObject(i) ?: continue
                    parseSingleObject(item)
                }
            } else if (jsonStr.startsWith("{")) {
                val root = JSONObject(jsonStr)

                // 提取 tslProtocols 数组
                if (root.has("tslProtocols")) {
                    val tslArr = root.optJSONArray("tslProtocols")
                    if (tslArr != null) {
                        for (i in 0 until tslArr.length()) {
                            val item = tslArr.optJSONObject(i) ?: continue
                            try {
                                tslProtocols.add(TslProtocol.fromJson(item))
                            } catch (_: Exception) {}
                        }
                    }
                }

                // 提取 protocols 数组
                val protoArr = root.optJSONArray("protocols") ?: root.optJSONArray("protocolKnowledgeList")
                if (protoArr != null) {
                    for (i in 0 until protoArr.length()) {
                        val item = protoArr.optJSONObject(i) ?: continue
                        parseSingleObject(item)
                    }
                }

                // 如果既无 tslProtocols 也无 protocols/protocolKnowledgeList，则 root 本身可能就是单个对象
                if (!root.has("tslProtocols") && !root.has("protocols") && !root.has("protocolKnowledgeList")) {
                    parseSingleObject(root)
                }
            }
        } catch (_: Exception) {
            // 非合法 JSON，返回空列表供调用方走文本回退解析
        }

        return ParsedProtocolImportResult(protocols, tslProtocols)
    }

    /**
     * 从独立 JSON 字符串或协议口令解析出协议规则列表（兼容旧接口）
     */
    fun parseProtocolsFromJsonOrToken(text: String): List<ProtocolKnowledge> {
        return parseProtocolsAndTslFromJsonOrToken(text).protocols
    }
}
