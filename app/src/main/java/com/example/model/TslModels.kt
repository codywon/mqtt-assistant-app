package com.example.model

import org.json.JSONArray
import org.json.JSONObject

/**
 * TSL (Thing Specification Language) 声明式物模型定义
 * 专为工业物联网高频二进制/JSON 报文设计的轻量级协议描述标准
 *
 * 设计准则：
 * 1. 纯声明式 JSON，零代码零脚本，手机端 Kotlin 原生微秒级解析
 * 2. 支持 HEX 二进制字节偏移切片 + JSON Path 键值提取 双模式
 * 3. 内建阈值告警系统（warnMin / warnMax），报文一到达即可实时越限判定
 * 4. 兼容 AI Agent 工具调用与 Excel 导出（自动追加物理量列）
 */

// =========================================================================
// 协议定义
// =========================================================================

/**
 * 一个完整的 TSL 物模型协议定义
 */
data class TslProtocol(
    val id: String = java.util.UUID.randomUUID().toString(),
    val name: String,                   // 协议名称（如 "多参数健康体征网关协议"）
    val format: TslFormat,              // 报文格式：HEX（二进制）或 JSON
    val matchTopic: String,             // MQTT Topic 匹配模式（支持 + 和 # 通配符）
    val packetFilter: String = "",      // 报文特征过滤（如 "4==0x21" 或 "len:10" 或 "aa55..21"）
    val fields: List<TslField>,         // 解析字段列表
    val builtin: Boolean = false,       // 是否为系统预置模板
    val enabled: Boolean = true,        // 是否启用
    val createdAt: Long = System.currentTimeMillis()
) {
    /**
     * 序列化为标准 JSON（用于导出、扫码传输、SQLite 存储）
     */
    fun toJson(): JSONObject = JSONObject().apply {
        put("protocolId", id)
        put("name", name)
        put("format", format.name)
        put("matchTopic", matchTopic)
        put("packetFilter", packetFilter)
        put("builtin", builtin)
        put("enabled", enabled)
        put("createdAt", createdAt)
        put("fields", JSONArray().apply {
            for (f in fields) put(f.toJson())
        })
    }

    companion object {
        /**
         * 从标准 JSON 反序列化
         */
        fun fromJson(json: JSONObject): TslProtocol {
            val fieldsArray = json.optJSONArray("fields") ?: JSONArray()
            val fields = (0 until fieldsArray.length()).map { i ->
                TslField.fromJson(fieldsArray.getJSONObject(i))
            }
            return TslProtocol(
                id = json.optString("protocolId", json.optString("id", java.util.UUID.randomUUID().toString())),
                name = json.optString("name", "未命名协议"),
                format = try {
                    TslFormat.valueOf(json.optString("format", "HEX").uppercase())
                } catch (_: Exception) { TslFormat.HEX },
                matchTopic = json.optString("matchTopic", "").trim().replace("\\/", "/"),
                packetFilter = json.optString("packetFilter", "").trim(),
                fields = fields,
                builtin = json.optBoolean("builtin", false),
                enabled = json.optBoolean("enabled", true),
                createdAt = json.optLong("createdAt", System.currentTimeMillis())
            )
        }
    }
}

/**
 * 报文格式枚举
 */
enum class TslFormat {
    HEX,    // 二进制 Hex 字节流（如 "AA 55 01 02 78 8C 5A 01 6D"）
    JSON    // JSON 结构化文本（如 {"temperature": 25.4, "humidity": 60}）
}

// =========================================================================
// 字段定义
// =========================================================================

/**
 * 单个 TSL 字段（物模型属性）的定义
 */
data class TslField(
    val identifier: String,             // 程序化标识符（如 "heart_rate"）
    val name: String,                   // 人类可读名称（如 "心率"）
    val offset: Int = 0,                // HEX 模式下的字节偏移量（从 0 开始）
    val length: Int = 1,                // HEX 模式下的字节长度
    val type: TslFieldType = TslFieldType.UINT8,  // 数据类型
    val scale: Double = 1.0,            // 缩放系数（如 0.1 表示实际值 = 原始值 * 0.1）
    val precision: Int = 2,             // 小数位精度
    val unit: String = "",              // 物理单位（如 "℃", "mmHg", "bpm"）
    val jsonPath: String = "",          // JSON 模式下的路径（如 "data.temperature"）
    val warnMin: Double? = null,        // 下限告警阈值（低于此值触发告警）
    val warnMax: Double? = null,        // 上限告警阈值（高于此值触发告警）
    val alarmBitmask: Long? = null,     // 状态位掩码告警（按位与非 0 触发告警，如 0x0002 代表跳闸报警）
    val isKeyIndicator: Boolean = false, // 是否显式声明为该协议的核心指标（微型药丸最高权重展现）
    val valueMap: Map<String, String>? = null // 状态/枚举值显式中文化映射字典（如 {"still": "微动", "motion": "运动"}）
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("identifier", identifier)
        put("name", name)
        put("offset", offset)
        put("length", length)
        put("type", type.name.lowercase())
        put("scale", scale)
        put("precision", precision)
        put("unit", unit)
        if (jsonPath.isNotBlank()) put("jsonPath", jsonPath)
        if (warnMin != null) put("warnMin", warnMin)
        if (warnMax != null) put("warnMax", warnMax)
        if (alarmBitmask != null) put("alarmBitmask", alarmBitmask)
        if (isKeyIndicator) put("isKeyIndicator", true)
        if (valueMap != null && valueMap.isNotEmpty()) {
            put("valueMap", JSONObject(valueMap))
        }
    }

    companion object {
        fun fromJson(json: JSONObject): TslField {
            val mapObj = json.optJSONObject("valueMap") ?: json.optJSONObject("value_map")
            val parsedValueMap: Map<String, String>? = if (mapObj != null && mapObj.length() > 0) {
                val map = mutableMapOf<String, String>()
                val keys = mapObj.keys()
                while (keys.hasNext()) {
                    val k = keys.next()
                    map[k] = mapObj.optString(k, "")
                }
                map
            } else null

            return TslField(
                identifier = json.optString("identifier", json.optString("id", "unknown")),
                name = json.optString("name", "未知字段"),
                offset = json.optInt("offset", 0),
                length = json.optInt("length", 1),
                type = try {
                    TslFieldType.valueOf(json.optString("type", "uint8").uppercase())
                } catch (_: Exception) { TslFieldType.UINT8 },
                scale = json.optDouble("scale", 1.0),
                precision = json.optInt("precision", 2),
                unit = json.optString("unit", ""),
                jsonPath = json.optString("jsonPath", ""),
                warnMin = if (json.has("warnMin") || json.has("warn_min"))
                    json.optDouble("warnMin", json.optDouble("warn_min", Double.NaN)).takeIf { !it.isNaN() }
                else null,
                warnMax = if (json.has("warnMax") || json.has("warn_max"))
                    json.optDouble("warnMax", json.optDouble("warn_max", Double.NaN)).takeIf { !it.isNaN() }
                else null,
                alarmBitmask = if (json.has("alarmBitmask") || json.has("alarm_bitmask"))
                    json.optLong("alarmBitmask", json.optLong("alarm_bitmask", -1L)).takeIf { it >= 0 }
                else null,
                isKeyIndicator = json.optBoolean("isKeyIndicator", json.optBoolean("is_key_indicator", false)),
                valueMap = parsedValueMap
            )
        }
    }
}

/**
 * 支持的数据类型枚举
 * 命名规范：{有无符号}{位宽}_{字节序}
 */
enum class TslFieldType {
    UINT8,          // 无符号 8 位整数
    INT8,           // 有符号 8 位整数
    UINT16_BE,      // 无符号 16 位整数（大端）
    UINT16_LE,      // 无符号 16 位整数（小端）
    INT16_BE,       // 有符号 16 位整数（大端）
    INT16_LE,       // 有符号 16 位整数（小端）
    UINT32_BE,      // 无符号 32 位整数（大端）
    UINT32_LE,      // 无符号 32 位整数（小端）
    INT32_BE,       // 有符号 32 位整数（大端）
    INT32_LE,       // 有符号 32 位整数（小端）
    FLOAT32_BE,     // IEEE 754 单精度浮点（大端）
    FLOAT32_LE,     // IEEE 754 单精度浮点（小端）
    FLOAT64_BE,     // IEEE 754 双精度浮点（大端）
    FLOAT64_LE,     // IEEE 754 双精度浮点（小端）
    ASCII,          // ASCII 字符串
    BCD,            // BCD 编码（常见于电表协议）
    BOOL_BIT,       // 单比特布尔值（offset 为字节索引，length 为位索引 0~7）
    JSON_NUMBER,    // JSON 模式：数值提取
    JSON_STRING,    // JSON 模式：字符串提取
    JSON_BOOL       // JSON 模式：布尔值提取
}

// =========================================================================
// 解析结果
// =========================================================================

/**
 * 单条报文经 TSL 引擎解析后的完整结果
 */
data class TslParseResult(
    val protocolId: String,             // 命中的协议 ID
    val protocolName: String,           // 命中的协议名称
    val values: List<TslParsedValue>,   // 解析出的所有物理量
    val hasWarnings: Boolean,           // 是否存在越限告警
    val warningCount: Int               // 告警数量
) {
    /**
     * 体系化提取核心关键指标（微型药丸专用）
     * 遵循工业级三层决策仲裁，绝无业务级硬编码：
     * 1. 【告警优先仲裁 (Alarm Override)】: 只要有指标越限或报警，100% 优先透出首个告警指标；
     * 2. 【显式声明仲裁 (Explicit Declaration)】: 显式标记 isKeyIndicator 的字段优先；
     * 3. 【语义权重仲裁 (Semantic Weighting)】: 依据工业物理量通用特征词根打分：
     *    - 权重 40: 存在/安防/计数/状态 (count, target, person, status, state, presence, alarm, fall, motion)
     *    - 权重 30: 能耗/累计做功/核心体征 (energy, kwh, heart, breath, vital, temp)
     *    - 权重 20: 主动物理测量量 (power, voltage, current, humidity, speed, pressure, lux)
     *    - 权重 5:  辅助诊断与次要参数 (rssi, seq, version, bat, snr, timestamp)
     * 4. 【首项兜底 (Fallback)】: 相同权重或无法区分时按物模型定义的第 1 个指标呈现。
     */
    /**
     * 核心关键指标（微型药丸专用）- 懒加载计算并常驻缓存，规避列表滚动高频重组反复打分
     */
    val keyIndicator: TslParsedValue? by lazy {
        computeKeyIndicator()
    }

    fun findKeyIndicator(): TslParsedValue? = keyIndicator

    private fun computeKeyIndicator(): TslParsedValue? {
        if (values.isEmpty()) return null

        // 1. 告警最高优先级（任何异常都无条件优先在药丸呈现）
        val alarmValue = values.firstOrNull { it.isWarning }
        if (alarmValue != null) return alarmValue

        // 2. 显式标记为核心关键指标的字段优先
        val explicitKey = values.firstOrNull { it.isKeyIndicator }
        if (explicitKey != null) return explicitKey

        // 3. 通用工业语义特征自适应打分
        fun scoreValue(v: TslParsedValue): Int {
            val key = "${v.identifier} ${v.name}".lowercase()
            return when {
                key.containsAnyKeywords("target", "count", "person", "人数", "目标", "alarm", "告警", "fall", "跌倒", "motion") -> 40
                key.containsAnyKeywords("energy", "kwh", "电能", "电量", "用电", "heart", "心率", "breath", "呼吸", "vital", "体征", "temp", "体温", "温度") -> 35
                key.containsAnyKeywords("power", "功率", "voltage", "电压", "current", "电流", "humidity", "湿度", "lux", "光照", "speed", "速度", "pressure", "压力") -> 25
                key.containsAnyKeywords("status", "state", "状态", "presence", "存在", "mode", "模式") -> 15
                key.containsAnyKeywords("rssi", "seq", "version", "版本", "bat", "电量百分比", "snr", "time") -> 5
                else -> 10
            }
        }

        return values.maxByOrNull { scoreValue(it) } ?: values.first()
    }

    /**
     * 生成简洁的物理量摘要文本（如 "心率:78bpm 高压:125mmHg ⚠ 体温:38.2℃"）
     */
    fun toSummaryText(): String = values.joinToString("  ") { v ->
        val prefix = if (v.isWarning) "⚠" else ""
        "$prefix${v.name}:${v.displayValue}"
    }

    /**
     * 序列化为 JSON（供 Agent 工具调用与 Excel 导出）
     */
    fun toJson(): JSONObject = JSONObject().apply {
        put("protocolName", protocolName)
        put("hasWarnings", hasWarnings)
        put("warningCount", warningCount)
        put("values", JSONArray().apply {
            for (v in values) put(v.toJson())
        })
    }
}

private fun String.containsAnyKeywords(vararg keywords: String): Boolean = keywords.any { this.contains(it) }

/**
 * 单个解析出的物理量值
 */
data class TslParsedValue(
    val identifier: String,             // 字段标识符
    val name: String,                   // 人类可读名称
    val rawValue: Double,               // 原始数值（缩放后）
    val displayValue: String,           // 格式化后的显示文本（含单位，如 "78 bpm"）
    val unit: String,                   // 物理单位
    val isWarning: Boolean,             // 是否越限告警
    val warningMessage: String? = null, // 告警详情（如 "收缩压 158 超过上限 140 mmHg"）
    val stringValue: String? = null,    // 字符串类型字段的原始文本（ASCII / JSON_STRING）
    val isKeyIndicator: Boolean = false // 是否显式声明为该协议的核心指标
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("id", identifier)
        put("name", name)
        put("value", rawValue)
        put("display", displayValue)
        put("unit", unit)
        put("warning", isWarning)
        if (warningMessage != null) put("warningMsg", warningMessage)
        if (stringValue != null) put("stringValue", stringValue)
        if (isKeyIndicator) put("isKeyIndicator", true)
    }
}

// =========================================================================
// AI 雷达哨兵与动态拦截模型
// =========================================================================

/**
 * 字段级动态匹配条件（由 Agent 根据自然语言意图编译生成）
 */
data class FieldCondition(
    val field: String,              // 字段标识符或中文名（如 "ia", "A相电流", "temp", "status"）
    val operator: String,           // 操作符: ">", "<", ">=", "<=", "==", "!=", "contains", "bitmask_and"
    val targetValue: String         // 目标比较值（如 "15.0", "online", "0x0002"）
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("field", field)
        put("operator", operator)
        put("targetValue", targetValue)
    }

    companion object {
        fun fromJson(json: JSONObject): FieldCondition = FieldCondition(
            field = json.optString("field", ""),
            operator = json.optString("operator", "=="),
            targetValue = json.optString("targetValue", "")
        )
    }
}

/**
 * 雷达拦截动作枚举
 */
enum class RadarTrapAction {
    FILTER_AND_HIGHLIGHT,   // 列表高亮并过滤展示
    CAPTURE_ALERT,          // 抓取并触发声光震动提示
    SNIFFER_TRAP            // 定量嗅探捕获（达标后自动汇总交卷）
}

/**
 * AI 雷达动态哨兵布控对象
 */
data class DynamicRadarTrap(
    val id: String = java.util.UUID.randomUUID().toString(),
    val topicPattern: String,                  // 目标主题通配符（如 "college/breaker/#"）
    val conditionDesc: String,                 // 人类可读意图描述（如 "断路器 A相电流 > 15A 或温度 > 65℃"）
    val conditions: List<FieldCondition>,      // 结构化匹配条件
    val matchLogic: String = "AND",            // 组合逻辑: "AND" 或 "OR"
    val action: RadarTrapAction = RadarTrapAction.FILTER_AND_HIGHLIGHT,
    val maxCaptureCount: Int = 10,             // 最大捕获容量
    val capturedCount: Int = 0,                // 当前已捕获条数
    val createdAt: Long = System.currentTimeMillis()
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("topicPattern", topicPattern)
        put("conditionDesc", conditionDesc)
        put("matchLogic", matchLogic)
        put("action", action.name)
        put("maxCaptureCount", maxCaptureCount)
        put("capturedCount", capturedCount)
        put("createdAt", createdAt)
        put("conditions", JSONArray().apply {
            for (c in conditions) put(c.toJson())
        })
    }

    companion object {
        fun fromJson(json: JSONObject): DynamicRadarTrap {
            val condsArray = json.optJSONArray("conditions") ?: JSONArray()
            val conds = (0 until condsArray.length()).map { i ->
                FieldCondition.fromJson(condsArray.getJSONObject(i))
            }
            return DynamicRadarTrap(
                id = json.optString("id", java.util.UUID.randomUUID().toString()),
                topicPattern = json.optString("topicPattern", "#"),
                conditionDesc = json.optString("conditionDesc", "未命名的雷达拦截规则"),
                conditions = conds,
                matchLogic = json.optString("matchLogic", "AND").uppercase(),
                action = try {
                    RadarTrapAction.valueOf(json.optString("action", "FILTER_AND_HIGHLIGHT").uppercase())
                } catch (_: Exception) { RadarTrapAction.FILTER_AND_HIGHLIGHT },
                maxCaptureCount = json.optInt("maxCaptureCount", 10),
                capturedCount = json.optInt("capturedCount", 0),
                createdAt = json.optLong("createdAt", System.currentTimeMillis())
            )
        }
    }
}
