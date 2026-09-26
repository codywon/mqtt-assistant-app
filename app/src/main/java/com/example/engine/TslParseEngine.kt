package com.example.engine

import android.util.Log
import com.example.model.*
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
     */
    private fun parseHexPayload(payload: String, fields: List<TslField>): List<TslParsedValue> {
        val bytes = hexStringToBytes(payload) ?: return emptyList()
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
                    stringValue = str
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
                TslParsedValue(
                    identifier = field.identifier,
                    name = field.name,
                    rawValue = bitVal.toDouble(),
                    displayValue = if (bitVal == 1) "ON" else "OFF",
                    unit = field.unit,
                    isWarning = false
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
                val strVal = value.toString()
                TslParsedValue(
                    identifier = field.identifier,
                    name = field.name,
                    rawValue = 0.0,
                    displayValue = strVal,
                    unit = field.unit,
                    isWarning = false,
                    stringValue = strVal
                )
            }
            TslFieldType.JSON_BOOL -> {
                val boolVal = when (value) {
                    is Boolean -> value
                    is String -> value.equals("true", ignoreCase = true)
                    is Number -> value.toInt() != 0
                    else -> return null
                }
                TslParsedValue(
                    identifier = field.identifier,
                    name = field.name,
                    rawValue = if (boolVal) 1.0 else 0.0,
                    displayValue = if (boolVal) "ON" else "OFF",
                    unit = field.unit,
                    isWarning = false
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

    /**
     * 按点分路径递归解析嵌套 JSON 值
     * 如 "data.sensors.temperature" -> json["data"]["sensors"]["temperature"]
     */
    private fun resolveJsonPath(json: JSONObject, path: String): Any? {
        val parts = path.split(".")
        var current: Any = json
        for (part in parts) {
            current = when (current) {
                is JSONObject -> {
                    if (!current.has(part)) return null
                    current.get(part)
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
     * 例如：
     *   matchTopic("hospital/gateway/gw01/vital", "hospital/gateway/+/vital") -> true
     *   matchTopic("factory/line1/sensor/temp", "factory/#") -> true
     */
    fun matchTopic(topic: String, pattern: String): Boolean {
        if (pattern.isBlank()) return false
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
            warningMessage = warningMsg
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
}
