package com.example.engine

import android.util.Log
import com.example.model.*
import org.json.JSONArray
import org.json.JSONObject
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * TSL 声明式物模型解析引擎（纯 Kotlin 原生实现）
 *
 * 设计准则：
 * 1. 零脚本、零正则、零反射：纯字节偏移切片 + 类型映射，微秒级解析
 * 2. 支持 HEX 二进制与 JSON 双格式自适应
 * 3. MQTT Topic 通配符匹配（支持 + 单层和 # 多层通配符）
 * 4. 线程安全：引擎本身无状态，协议列表由外部注入
 * 5. 容错优先：单字段解析失败不影响其他字段，确保工业现场鲁棒性
 */
object TslParseEngine {

    private const val TAG = "TslParseEngine"

    // =========================================================================
    // 核心入口：自动匹配协议并解析报文
    // =========================================================================

    /**
     * 尝试用所有已加载的 TSL 协议匹配并解析一条 MQTT 报文
     * @param topic MQTT 消息主题
     * @param payload 报文内容（Hex 字符串或 JSON 文本）
     * @param category 报文分类标记（"HEX", "JSON", "TEXT"）
     * @param protocols 当前已加载的所有 TSL 协议列表
     * @return 匹配成功返回解析结果，未匹配返回 null
     */
    fun tryParse(
        topic: String,
        payload: String,
        category: String,
        protocols: List<TslProtocol>
    ): TslParseResult? {
        if (protocols.isEmpty() || payload.isBlank()) return null

        // 遍历所有启用的协议，找到第一个匹配 topic 的
        for (protocol in protocols) {
            if (!protocol.enabled) continue
            if (!matchTopic(topic, protocol.matchTopic)) continue

            return try {
                parseWithProtocol(payload, category, protocol)
            } catch (e: Exception) {
                Log.w(TAG, "协议 [${protocol.name}] 解析报文异常: ${e.message}")
                null
            }
        }
        return null
    }

    /**
     * 使用指定协议强制解析报文（不做 topic 匹配，用于手动指定协议场景）
     */
    fun parseWithProtocol(
        payload: String,
        category: String,
        protocol: TslProtocol
    ): TslParseResult? {
        val values = when (protocol.format) {
            TslFormat.HEX -> parseHexPayload(payload, protocol.fields)
            TslFormat.JSON -> parseJsonPayload(payload, protocol.fields)
        }

        if (values.isEmpty()) return null

        val warnings = values.filter { it.isWarning }
        return TslParseResult(
            protocolId = protocol.id,
            protocolName = protocol.name,
            values = values,
            hasWarnings = warnings.isNotEmpty(),
            warningCount = warnings.size
        )
    }

    // =========================================================================
    // HEX 二进制报文解析
    // =========================================================================

    /**
     * 将 Hex 字符串解析为字节数组，然后逐字段按偏移量切片、按类型解包
     * 具备智能网关解包能力：若网关将 Hex 封装在 JSON 载荷中 (如 {"data_value": "550e03..."})，自动提取内部真实 Hex
     */
    private fun parseHexPayload(payload: String, fields: List<TslField>): List<TslParsedValue> {
        val rawHex = extractHexFromPayload(payload)
        val bytes = hexStringToBytes(rawHex) ?: return emptyList()
        if (bytes.isEmpty()) return emptyList()

        return fields.mapNotNull { field ->
            try {
                parseHexField(bytes, field)
            } catch (e: Exception) {
                Log.w(TAG, "字段 [${field.name}] 解析失败 (offset=${field.offset}, len=${field.length}): ${e.message}")
                null
            }
        }
    }

    /**
     * 智能网关载荷解包 (Gateway Wrapped Hex Unpacker):
     * 工业/医疗现场中，很多物联网网关收到蓝牙/串口 Hex 报文后，会包装成 JSON 格式上报 MQTT Broker。
     * 例如：{ "data_value": "550e03180c050800008100514db8", "bat_voltage": 3784, ... }
     * 本方法自动识别并提取内部包裹的真实 Hex 字符串。若本身就是纯 Hex 则直接返回。
     */
    fun extractHexFromPayload(payload: String): String {
        val trimmed = payload.trim()
        if (trimmed.startsWith("{") && trimmed.endsWith("}")) {
            try {
                val json = JSONObject(trimmed)
                // 常见网关封装 Hex 的键名优先级列表 (现场以 data_value 最为典型)
                val candidateKeys = listOf(
                    "data_value", "dataValue", "data", "raw_data", "rawData",
                    "raw", "payload", "hex", "hexData", "hex_data", "value", "msg", "content", "stream"
                )
                for (key in candidateKeys) {
                    if (json.has(key)) {
                        val v = json.optString(key, "").trim()
                        if (isLikelyHexString(v)) return v
                    }
                }
                // 若预设键未命中，自动扫描 JSON 顶级字段寻找最长的有效 Hex 字符串
                var longestHex = ""
                val keys = json.keys()
                while (keys.hasNext()) {
                    val k = keys.next()
                    val v = json.optString(k, "").trim()
                    if (isLikelyHexString(v) && v.length > longestHex.length) {
                        longestHex = v
                    }
                }
                if (longestHex.isNotBlank()) return longestHex
            } catch (_: Exception) {}
        }
        return trimmed
    }

    private fun isLikelyHexString(str: String): Boolean {
        val clean = str.trim().replace(" ", "").replace("0x", "").replace("0X", "").replace("-", "").replace("\"", "")
        if (clean.length < 4 || clean.length % 2 != 0) return false
        return clean.all { it in '0'..'9' || it in 'a'..'f' || it in 'A'..'F' }
    }

    /**
     * 解析单个 HEX 字段
     */
    private fun parseHexField(bytes: ByteArray, field: TslField): TslParsedValue? {
        // 边界检查
        if (field.type == TslFieldType.BOOL_BIT) {
            if (field.offset >= bytes.size) return null
        } else if (field.type == TslFieldType.ASCII) {
            if (field.offset + field.length > bytes.size) return null
        } else {
            val requiredLen = when (field.type) {
                TslFieldType.UINT8, TslFieldType.INT8 -> 1
                TslFieldType.UINT16_BE, TslFieldType.UINT16_LE,
                TslFieldType.INT16_BE, TslFieldType.INT16_LE -> 2
                TslFieldType.UINT32_BE, TslFieldType.UINT32_LE,
                TslFieldType.INT32_BE, TslFieldType.INT32_LE,
                TslFieldType.FLOAT32_BE, TslFieldType.FLOAT32_LE -> 4
                TslFieldType.FLOAT64_BE, TslFieldType.FLOAT64_LE -> 8
                TslFieldType.BCD -> field.length
                else -> field.length
            }
            if (field.offset + requiredLen > bytes.size) return null
        }

        return when (field.type) {
            // 8 位整数
            TslFieldType.UINT8 -> {
                val raw = bytes[field.offset].toInt() and 0xFF
                buildNumericValue(field, raw.toDouble())
            }
            TslFieldType.INT8 -> {
                val raw = bytes[field.offset].toInt()
                buildNumericValue(field, raw.toDouble())
            }

            // 16 位整数
            TslFieldType.UINT16_BE -> {
                val raw = ((bytes[field.offset].toInt() and 0xFF) shl 8) or
                          (bytes[field.offset + 1].toInt() and 0xFF)
                buildNumericValue(field, raw.toDouble())
            }
            TslFieldType.UINT16_LE -> {
                val raw = (bytes[field.offset].toInt() and 0xFF) or
                          ((bytes[field.offset + 1].toInt() and 0xFF) shl 8)
                buildNumericValue(field, raw.toDouble())
            }
            TslFieldType.INT16_BE -> {
                val raw = ((bytes[field.offset].toInt() and 0xFF) shl 8) or
                          (bytes[field.offset + 1].toInt() and 0xFF)
                val signed = if (raw > 0x7FFF) raw - 0x10000 else raw
                buildNumericValue(field, signed.toDouble())
            }
            TslFieldType.INT16_LE -> {
                val raw = (bytes[field.offset].toInt() and 0xFF) or
                          ((bytes[field.offset + 1].toInt() and 0xFF) shl 8)
                val signed = if (raw > 0x7FFF) raw - 0x10000 else raw
                buildNumericValue(field, signed.toDouble())
            }

            // 32 位整数
            TslFieldType.UINT32_BE -> {
                val raw = readUint32(bytes, field.offset, ByteOrder.BIG_ENDIAN)
                buildNumericValue(field, raw.toDouble())
            }
            TslFieldType.UINT32_LE -> {
                val raw = readUint32(bytes, field.offset, ByteOrder.LITTLE_ENDIAN)
                buildNumericValue(field, raw.toDouble())
            }
            TslFieldType.INT32_BE -> {
                val raw = readInt32(bytes, field.offset, ByteOrder.BIG_ENDIAN)
                buildNumericValue(field, raw.toDouble())
            }
            TslFieldType.INT32_LE -> {
                val raw = readInt32(bytes, field.offset, ByteOrder.LITTLE_ENDIAN)
                buildNumericValue(field, raw.toDouble())
            }

            // IEEE 754 浮点
            TslFieldType.FLOAT32_BE -> {
                val raw = readFloat32(bytes, field.offset, ByteOrder.BIG_ENDIAN)
                buildNumericValue(field, raw.toDouble())
            }
            TslFieldType.FLOAT32_LE -> {
                val raw = readFloat32(bytes, field.offset, ByteOrder.LITTLE_ENDIAN)
                buildNumericValue(field, raw.toDouble())
            }
            TslFieldType.FLOAT64_BE -> {
                val raw = readFloat64(bytes, field.offset, ByteOrder.BIG_ENDIAN)
                buildNumericValue(field, raw)
            }
            TslFieldType.FLOAT64_LE -> {
                val raw = readFloat64(bytes, field.offset, ByteOrder.LITTLE_ENDIAN)
                buildNumericValue(field, raw)
            }

            // ASCII 字符串
            TslFieldType.ASCII -> {
                val strBytes = bytes.copyOfRange(field.offset, field.offset + field.length)
                val str = String(strBytes, Charsets.US_ASCII).trimEnd('\u0000')
                TslParsedValue(
                    identifier = field.identifier,
                    name = field.name,
                    rawValue = 0.0,
                    displayValue = str,
                    unit = field.unit,
                    isWarning = false,
                    stringValue = str,
                    isKeyIndicator = field.isKeyIndicator
                )
            }

            // BCD 编码（常见于国标电表 DL/T 645）
            TslFieldType.BCD -> {
                val bcdBytes = bytes.copyOfRange(field.offset, field.offset + field.length)
                val bcdStr = bcdBytes.joinToString("") { b ->
                    String.format("%02X", b.toInt() and 0xFF)
                }
                val numVal = bcdStr.toDoubleOrNull() ?: 0.0
                buildNumericValue(field, numVal)
            }

            // 单比特布尔值（offset = 字节索引，length = 位索引 0~7）
            TslFieldType.BOOL_BIT -> {
                val byteVal = bytes[field.offset].toInt() and 0xFF
                val bitIndex = field.length.coerceIn(0, 7)
                val bitVal = (byteVal shr bitIndex) and 1
                val isTrue = (bitVal == 1)
                val displayStr = formatBoolDisplay(field, isTrue)
                TslParsedValue(
                    identifier = field.identifier,
                    name = field.name,
                    rawValue = bitVal.toDouble(),
                    displayValue = displayStr,
                    unit = field.unit,
                    isWarning = false,
                    stringValue = if (isTrue) "true" else "false",
                    isKeyIndicator = field.isKeyIndicator
                )
            }

            // JSON 类型在 HEX 模式下不适用
            TslFieldType.JSON_NUMBER, TslFieldType.JSON_STRING, TslFieldType.JSON_BOOL -> null
        }
    }

    // =========================================================================
    // JSON 报文解析
    // =========================================================================

    /**
     * 解析 JSON 格式报文：按 jsonPath 路径提取值
     */
    private fun parseJsonPayload(payload: String, fields: List<TslField>): List<TslParsedValue> {
        val json = try {
            JSONObject(payload.trim())
        } catch (_: Exception) {
            return emptyList()
        }

        return fields.mapNotNull { field ->
            try {
                parseJsonField(json, field)
            } catch (e: Exception) {
                Log.w(TAG, "JSON 字段 [${field.name}] 解析失败 (path=${field.jsonPath}): ${e.message}")
                null
            }
        }
    }

    /**
     * 按 jsonPath 从 JSON 对象中提取单个字段值
     * 支持嵌套路径如 "data.sensors.temperature"
     */
    private fun parseJsonField(json: JSONObject, field: TslField): TslParsedValue? {
        val path = field.jsonPath.ifBlank { field.identifier }
        val value = resolveJsonPath(json, path) ?: return null

        return when (field.type) {
            TslFieldType.JSON_NUMBER -> {
                val numVal = when (value) {
                    is Number -> value.toDouble()
                    is String -> value.toDoubleOrNull() ?: return null
                    else -> return null
                }
                buildNumericValue(field, numVal)
            }
            TslFieldType.JSON_STRING -> {
                val strVal = value.toString().trim()
                val displayStr = formatStateDisplay(field, strVal)
                TslParsedValue(
                    identifier = field.identifier,
                    name = field.name,
                    rawValue = 0.0,
                    displayValue = displayStr,
                    unit = field.unit,
                    isWarning = false,
                    stringValue = strVal,
                    isKeyIndicator = field.isKeyIndicator
                )
            }
            TslFieldType.JSON_BOOL -> {
                val boolVal = when (value) {
                    is Boolean -> value
                    is String -> value.equals("true", ignoreCase = true)
                    is Number -> value.toInt() != 0
                    else -> return null
                }
                val displayStr = formatBoolDisplay(field, boolVal)
                TslParsedValue(
                    identifier = field.identifier,
                    name = field.name,
                    rawValue = if (boolVal) 1.0 else 0.0,
                    displayValue = displayStr,
                    unit = field.unit,
                    isWarning = false,
                    stringValue = boolVal.toString(),
                    isKeyIndicator = field.isKeyIndicator
                )
            }
            // 对于 HEX 类型的字段也尝试以数值方式提取（兼容混合定义）
            else -> {
                val numVal = when (value) {
                    is Number -> value.toDouble()
                    is String -> value.toDoubleOrNull() ?: return null
                    else -> return null
                }
                buildNumericValue(field, numVal)
            }
        }
    }

    private fun resolveJsonPath(json: JSONObject, path: String): Any? {
        if (path.isBlank()) return null

        // 1. 将 a[0].b 统一转换为 a.0.b
        val normalized = path.replace("[", ".").replace("]", "")
        val parts = normalized.split(".").filter { it.isNotEmpty() }
        if (parts.isEmpty()) return null

        var current: Any = json
        for (part in parts) {
            current = when (current) {
                is JSONObject -> {
                    if (current.has(part)) {
                        current.get(part)
                    } else {
                        // 友好回退：若直接查找断路器属性（如 "voltage"）而未写全前缀，
                        // 且顶层存在 "breakers" / "data" / "items" 数组，尝试从首个元素读取
                        val fallbackArray = current.optJSONArray("breakers")
                            ?: current.optJSONArray("data")
                            ?: current.optJSONArray("items")
                        if (fallbackArray != null && fallbackArray.length() > 0) {
                            val firstObj = fallbackArray.optJSONObject(0)
                            if (firstObj != null && firstObj.has(part)) {
                                firstObj.get(part)
                            } else return null
                        } else return null
                    }
                }
                is JSONArray -> {
                    val index = part.toIntOrNull() ?: return null
                    if (index in 0 until current.length()) {
                        current.get(index)
                    } else return null
                }
                else -> return null
            }
        }
        return if (current == JSONObject.NULL) null else current
    }

    // =========================================================================
    // MQTT Topic 通配符匹配
    // =========================================================================

    /**
     * MQTT Topic 通配符匹配
     * 支持：+ (单层通配) 和 # (多层通配)
     * 具备工业现场高容错能力：
     * 1. 自动清洗反斜杠转义 (如 JSON 导出的 "\/" 统一还原为 "/")
     * 2. 支持多 Topic 过滤表达式 (以逗号或分号分隔，命中任意一个即为匹配成功)
     * 例如：
     *   matchTopic("hospital/gateway/gw01/vital", "hospital/gateway/+/vital") -> true
     *   matchTopic("factory/line1/sensor/temp", "factory/#") -> true
     *   matchTopic("Collect/BP_Report/01", "Collect/BP_Report/#, medical/+/blood_pressure/#") -> true
     */
    fun matchTopic(topic: String, pattern: String): Boolean {
        if (pattern.isBlank()) return false
        val cleanTopic = topic.trim().replace("\\/", "/").replace("\\", "")
        val patterns = pattern.split(",", ";")
            .map { it.trim().replace("\\/", "/").replace("\\", "") }
            .filter { it.isNotBlank() }
        if (patterns.isEmpty()) return false
        return patterns.any { p -> matchSingleTopic(cleanTopic, p) }
    }

    private fun matchSingleTopic(topic: String, pattern: String): Boolean {
        if (pattern == "#") return true
        if (pattern == topic) return true

        val topicParts = topic.split("/")
        val patternParts = pattern.split("/")

        var ti = 0
        var pi = 0
        while (pi < patternParts.size) {
            val pp = patternParts[pi]
            if (pp == "#") return true  // # 匹配剩余所有层级
            if (ti >= topicParts.size) return false
            if (pp != "+" && pp != topicParts[ti]) return false
            ti++
            pi++
        }
        return ti == topicParts.size
    }

    // =========================================================================
    // 行业状态语义字典与中文化呈现
    // =========================================================================

    private val STATE_TRANSLATION_MAP = mapOf(
        // 雷达活动状态
        "still" to "微动",
        "motion" to "运动",
        "moving" to "运动",
        "body_motion" to "运动",
        "major_motion" to "大幅运动",
        "micro_motion" to "微动",
        "minor_motion" to "微动",
        "slight_motion" to "微动",
        "static" to "静止",
        "rest" to "静止",
        "resting" to "静止",
        "fall" to "跌倒告警",
        "fallen" to "跌倒告警",
        "falling" to "跌倒告警",
        "fall_detected" to "检测到跌倒",
        "sitting" to "坐姿",
        "seated" to "坐姿",
        "standing" to "站立",
        "walking" to "走动",

        // 在场/在位状态
        "present" to "在场",
        "room_present" to "有人在场",
        "occupied" to "有人",
        "exist" to "在场",
        "absent" to "无人",
        "room_absent" to "无人",
        "empty" to "无人",
        "vacant" to "无人",
        "nobody" to "无人",
        "leave" to "离场",
        "leaving" to "离场中",
        "enter" to "进入",
        "entering" to "进入中",

        // 睡眠/在床状态
        "bed_rest_observed" to "在床休息",
        "in_bed" to "在床",
        "bed_present" to "在床",
        "off_bed_present" to "离床在场",
        "off_bed" to "离床",
        "out_of_bed" to "离床",
        "out_bed" to "离床",
        "sleep" to "睡眠",
        "sleeping" to "睡眠中",
        "asleep" to "入睡",
        "deep_sleep" to "深睡",
        "light_sleep" to "浅睡",
        "rem" to "快速眼动",
        "awake" to "清醒",
        "turn_over" to "翻身",
        "breath_pause" to "呼吸暂停告警",
        "apnea" to "呼吸暂停告警",

        // 开关/通用工控运行状态
        "on" to "合闸/开启",
        "off" to "分闸/关闭",
        "open" to "开启",
        "close" to "关闭",
        "closed" to "关闭",
        "normal" to "正常",
        "alarm" to "告警",
        "fault" to "故障",
        "trip" to "跳闸",
        "tripped" to "已跳闸",
        "online" to "在线",
        "offline" to "离线",
        "connected" to "已连接",
        "disconnected" to "已断开",
        "running" to "运行中",
        "stopped" to "已停机",
        "standby" to "待机"
    )

    /**
     * 智能翻译状态字符串（优先使用 TslField 的显式映射表 valueMap，兜底查内建行业语义词典）
     */
    fun formatStateDisplay(field: TslField, rawStr: String): String {
        val clean = rawStr.trim()
        if (clean.isEmpty()) return clean

        // 1. 优先查字段级显式配置的 valueMap (支持大小写无关)
        field.valueMap?.let { map ->
            val explicitMatch = map.entries.firstOrNull { it.key.equals(clean, ignoreCase = true) }
            if (explicitMatch != null && explicitMatch.value.isNotBlank()) {
                return explicitMatch.value
            }
        }

        // 2. 查内建雷达与工控通用状态词典
        val key = clean.lowercase()
        STATE_TRANSLATION_MAP[key]?.let { return it }

        // 3. 未匹配时保持原始字符串
        return clean
    }

    /**
     * 智能翻译布尔值（优先使用 TslField 的显式映射表，兜底结合字段领域语义推断）
     */
    fun formatBoolDisplay(field: TslField, boolVal: Boolean): String {
        val boolKey = if (boolVal) "true" else "false"
        val numKey = if (boolVal) "1" else "0"

        // 1. 优先查显式 valueMap
        field.valueMap?.let { map ->
            val match = map.entries.firstOrNull {
                it.key.equals(boolKey, ignoreCase = true) || it.key == numKey
            }
            if (match != null && match.value.isNotBlank()) {
                return match.value
            }
        }

        // 2. 结合字段领域语义自动推断
        val lowerId = field.identifier.lowercase()
        val lowerName = field.name.lowercase()

        // 卧床/在床检测
        if (lowerId.contains("bed") || lowerName.contains("床") || lowerName.contains("卧")) {
            return if (boolVal) "在床" else "离床"
        }

        // 人员在场/在位检测
        if (lowerId.contains("presence") || lowerId.contains("occup") ||
            lowerName.contains("在位") || lowerName.contains("在场") ||
            lowerName.contains("有人") || lowerName.contains("人数")) {
            return if (boolVal) "有人" else "无人"
        }

        // 跌倒/告警/故障
        if (lowerId.contains("fall") || lowerId.contains("alarm") || lowerId.contains("warn") ||
            lowerName.contains("跌倒") || lowerName.contains("告警") || lowerName.contains("报警")) {
            return if (boolVal) "告警" else "正常"
        }

        // 开关/断路器/继电器
        if (lowerId.contains("breaker") || lowerId.contains("switch") || lowerId.contains("relay") ||
            lowerName.contains("开关") || lowerName.contains("断路器") || lowerName.contains("闸") || lowerName.contains("继电器")) {
            return if (boolVal) "合闸" else "分闸"
        }

        // 默认工业标识
        return if (boolVal) "ON" else "OFF"
    }

    // =========================================================================
    // 工具方法
    // =========================================================================

    /**
     * 构建带缩放、格式化和告警判定的数值型解析结果
     */
    private fun buildNumericValue(field: TslField, rawValue: Double): TslParsedValue {
        val scaledValue = rawValue * field.scale
        val formatted = formatNumber(scaledValue, field.precision)
        val displayText = if (field.unit.isNotBlank()) "$formatted ${field.unit}" else formatted

        var isWarning = false
        var warningMsg: String? = null

        field.warnMax?.let { max ->
            if (scaledValue > max) {
                isWarning = true
                warningMsg = "${field.name} $formatted 超过上限 ${formatNumber(max, field.precision)} ${field.unit}"
            }
        }
        field.warnMin?.let { min ->
            if (scaledValue < min) {
                isWarning = true
                warningMsg = "${field.name} $formatted 低于下限 ${formatNumber(min, field.precision)} ${field.unit}"
            }
        }
        // 2. 状态位掩码告警 (Bitmask Alert: 当对应故障/跳闸位为 1 时触发告警)
        field.alarmBitmask?.let { mask ->
            val rawLong = rawValue.toLong()
            if ((rawLong and mask) != 0L) {
                isWarning = true
                val hexMask = "0x" + mask.toString(16).uppercase()
                val hexRaw = "0x" + rawLong.toString(16).uppercase()
                warningMsg = "${field.name} 触发状态位告警 ($hexRaw & $hexMask != 0)"
            }
        }

        return TslParsedValue(
            identifier = field.identifier,
            name = field.name,
            rawValue = scaledValue,
            displayValue = displayText,
            unit = field.unit,
            isWarning = isWarning,
            warningMessage = warningMsg,
            isKeyIndicator = field.isKeyIndicator
        )
    }

    /**
     * 数值格式化：整数不带小数点，浮点数按精度截断
     */
    private fun formatNumber(value: Double, precision: Int): String {
        return if (value == value.toLong().toDouble() && precision == 0) {
            value.toLong().toString()
        } else {
            String.format(java.util.Locale.US, "%.${precision.coerceIn(0, 6)}f", value)
        }
    }

    /**
     * 将 Hex 字符串转换为字节数组
     * 支持 "AA BB CC" / "AABBCC" / "0xAA 0xBB" / "AA-BB-CC" 等多种常见格式
     */
    fun hexStringToBytes(hex: String): ByteArray? {
        val cleaned = hex.trim()
            .replace("\"", "").replace("'", "")
            .replace("0x", "").replace("0X", "")
            .replace("-", " ")
            .replace(",", " ")
            .replace("  ", " ")
            .trim()

        return try {
            if (cleaned.contains(" ")) {
                // 空格分隔格式
                cleaned.split(" ")
                    .filter { it.isNotBlank() }
                    .map { it.toInt(16).toByte() }
                    .toByteArray()
            } else {
                // 连续 Hex 格式
                if (cleaned.length % 2 != 0) return null
                ByteArray(cleaned.length / 2) { i ->
                    cleaned.substring(i * 2, i * 2 + 2).toInt(16).toByte()
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Hex 字符串解析失败: $hex -> ${e.message}")
            null
        }
    }

    // 字节序读取辅助
    private fun readUint32(bytes: ByteArray, offset: Int, order: ByteOrder): Long {
        val buf = ByteBuffer.wrap(bytes, offset, 4).order(order)
        return buf.int.toLong() and 0xFFFFFFFFL
    }

    private fun readInt32(bytes: ByteArray, offset: Int, order: ByteOrder): Int {
        return ByteBuffer.wrap(bytes, offset, 4).order(order).int
    }

    private fun readFloat32(bytes: ByteArray, offset: Int, order: ByteOrder): Float {
        return ByteBuffer.wrap(bytes, offset, 4).order(order).float
    }

    private fun readFloat64(bytes: ByteArray, offset: Int, order: ByteOrder): Double {
        return ByteBuffer.wrap(bytes, offset, 8).order(order).double
    }

    // =========================================================================
    // AI 动态雷达哨兵与条件拦截判定
    // =========================================================================

    /**
     * 评估单包报文是否满足 AI 雷达哨兵 (DynamicRadarTrap) 的布控拦截条件
     */
    fun evaluateTrap(
        packetTopic: String,
        rawPayload: String,
        tslResult: TslParseResult?,
        trap: DynamicRadarTrap
    ): Boolean {
        // 1. Topic 通配符匹配
        if (!matchTopic(packetTopic.trim(), trap.topicPattern.trim())) {
            return false
        }

        // 若无字段级额外条件，命中 Topic 即为拦截成功
        if (trap.conditions.isEmpty()) return true

        val results = trap.conditions.map { cond ->
            evaluateFieldCondition(rawPayload, tslResult, cond)
        }

        return if (trap.matchLogic.equals("OR", ignoreCase = true)) {
            results.any { it }
        } else {
            results.all { it }
        }
    }

    /**
     * 单个字段条件的动态求值
     */
    private fun evaluateFieldCondition(
        rawPayload: String,
        tslResult: TslParseResult?,
        cond: FieldCondition
    ): Boolean {
        val targetField = cond.field.trim()
        val op = cond.operator.trim()
        val targetValStr = cond.targetValue.trim()

        var actualValDouble: Double? = null
        var actualValStr: String? = null

        // 1. 优先从 TslParseResult 的已解析物理量中查找
        val matchedTslValue = tslResult?.values?.firstOrNull {
            it.identifier.equals(targetField, ignoreCase = true) ||
                it.name.contains(targetField, ignoreCase = true)
        }

        if (matchedTslValue != null) {
            actualValDouble = matchedTslValue.rawValue
            actualValStr = matchedTslValue.stringValue ?: matchedTslValue.displayValue
        } else {
            // 2. 若物模型未解析该字段，尝试从原始 JSON 中提取（兼容未经 TSL 映射的属性）
            try {
                val clean = rawPayload.trim()
                if (clean.startsWith("{")) {
                    val json = JSONObject(clean)
                    if (json.has(targetField)) {
                        actualValDouble = json.optDouble(targetField, Double.NaN).takeIf { !it.isNaN() }
                        actualValStr = json.optString(targetField, "")
                    } else {
                        val keys = json.keys()
                        while (keys.hasNext()) {
                            val k = keys.next()
                            if (k.equals(targetField, ignoreCase = true)) {
                                actualValDouble = json.optDouble(k, Double.NaN).takeIf { !it.isNaN() }
                                actualValStr = json.optString(k, "")
                                break
                            }
                        }
                    }
                }
            } catch (_: Exception) {}
        }

        // 3. 执行操作符求值
        return when (op) {
            ">" -> {
                val t = targetValStr.toDoubleOrNull() ?: return false
                val a = actualValDouble ?: return false
                a > t
            }
            "<" -> {
                val t = targetValStr.toDoubleOrNull() ?: return false
                val a = actualValDouble ?: return false
                a < t
            }
            ">=" -> {
                val t = targetValStr.toDoubleOrNull() ?: return false
                val a = actualValDouble ?: return false
                a >= t
            }
            "<=" -> {
                val t = targetValStr.toDoubleOrNull() ?: return false
                val a = actualValDouble ?: return false
                a <= t
            }
            "==", "=" -> {
                val tNum = targetValStr.toDoubleOrNull()
                if (tNum != null && actualValDouble != null) {
                    Math.abs(actualValDouble - tNum) < 0.0001
                } else {
                    val aStr = actualValStr ?: actualValDouble?.toString() ?: ""
                    val dStr = matchedTslValue?.displayValue ?: ""
                    aStr.equals(targetValStr, ignoreCase = true) || dStr.equals(targetValStr, ignoreCase = true)
                }
            }
            "!=" -> {
                val tNum = targetValStr.toDoubleOrNull()
                if (tNum != null && actualValDouble != null) {
                    Math.abs(actualValDouble - tNum) >= 0.0001
                } else {
                    val aStr = actualValStr ?: actualValDouble?.toString() ?: ""
                    val dStr = matchedTslValue?.displayValue ?: ""
                    !aStr.equals(targetValStr, ignoreCase = true) && !dStr.equals(targetValStr, ignoreCase = true)
                }
            }
            "contains" -> {
                val aStr = actualValStr ?: actualValDouble?.toString() ?: rawPayload
                val dStr = matchedTslValue?.displayValue ?: ""
                aStr.contains(targetValStr, ignoreCase = true) || dStr.contains(targetValStr, ignoreCase = true)
            }
            "bitmask_and", "&" -> {
                val mask = parseLongSafely(targetValStr) ?: return false
                val rawLong = actualValDouble?.toLong() ?: parseLongSafely(actualValStr ?: "") ?: return false
                (rawLong and mask) != 0L
            }
            else -> false
        }
    }

    private fun parseLongSafely(str: String): Long? {
        val clean = str.trim()
        return try {
            if (clean.startsWith("0x", ignoreCase = true)) {
                clean.substring(2).toLong(16)
            } else {
                clean.toLong()
            }
        } catch (_: Exception) {
            null
        }
    }
}
