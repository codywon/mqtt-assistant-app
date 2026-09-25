package com.example.ai

import android.content.Context
import com.example.data.MqttStorageRepository
import com.example.model.MqttLogPacket
import com.example.model.ProtocolKnowledge
import com.example.util.ArchivedExcelReader
import com.example.util.WebSearchHelper
import org.json.JSONArray
import org.json.JSONObject

/**
 * SI 智能数据分析 Agent 工具注册中心与执行引擎：
 * 遵循 OpenAI Tool Calling (Function Calling) 规范，为大模型提供操作底层 SQLite、内存实时流与历史 Excel 文件的工具箱。
 */
class SIAgentToolRegistry(
    val storage: MqttStorageRepository,
    val context: Context? = null,
    val livePacketsProvider: (() -> List<MqttLogPacket>)? = null,
    val tslParseResultsProvider: (() -> Map<String, com.example.model.TslParseResult>)? = null,
    val tslProtocolsProvider: (() -> List<com.example.model.TslProtocol>)? = null,
    val onMessagePublished: ((topic: String, qos: Int, retain: Boolean, payload: String) -> Unit)? = null
) {

    companion object {
        /**
         * 获取提供给大模型的工具描述定义清单 (JSON Schema)
         */
        fun getToolDefinitionsJson(): JSONArray {
            val tools = JSONArray()

            // 工具 1: get_live_packets (直接多维分析内存热报文)
            tools.put(
                JSONObject().apply {
                    put("type", "function")
                    put(
                        "function",
                        JSONObject().apply {
                            put("name", "get_live_packets")
                            put(
                                "description",
                                "【内存实时热报文多维分析】从应用内存高速缓冲流中检索当前最新到达的 MQTT 报文（零 I/O 极速直读）。支持两种模式：1. mode='summary'（极速提取宏观统计：总条数、活跃主题分布 Top 10、各网关吞吐量、识别的设备 ID 清单、最新时间戳，仅消耗 ~80 tokens）；2. mode='sample'（按主题或内容关键词过滤精准抽样具体明细报文，单次上限 50 条）。分析当前在线网关通信质量、生成现场验收报告、排查实时告警时必须调用此工具。"
                            )
                            put(
                                "parameters",
                                JSONObject().apply {
                                    put("type", "object")
                                    put(
                                        "properties",
                                        JSONObject().apply {
                                            put(
                                                "mode",
                                                JSONObject().apply {
                                                    put("type", "string")
                                                    put("description", "分析模式: 'summary' (汇总全网各主题吞吐分布与在线设备画像，极其省 token) 或 'sample' (抽样读取具体报文明细，默认)")
                                                }
                                            )
                                            put(
                                                "topicFilter",
                                                JSONObject().apply {
                                                    put("type", "string")
                                                    put("description", "可选的主题过滤关键词或路径，例如 'gateway/gw_01' 或 'sensor'")
                                                }
                                            )
                                            put(
                                                "keyword",
                                                JSONObject().apply {
                                                    put("type", "string")
                                                    put("description", "可选的报文内容/Hex过滤关键词")
                                                }
                                            )
                                            put(
                                                "limit",
                                                JSONObject().apply {
                                                    put("type", "integer")
                                                    put("description", "采样数量 (仅在 mode='sample' 时生效)，默认 20 条，最大 50 条")
                                                }
                                            )
                                        }
                                    )
                                }
                            )
                        }
                    )
                }
            )

            // 工具 3: get_protocol_clarification
            tools.put(
                JSONObject().apply {
                    put("type", "function")
                    put(
                        "function",
                        JSONObject().apply {
                            put("name", "get_protocol_clarification")
                            put(
                                "description",
                                "按需查询硬件私有协议的详细解码规范与字节偏移定义。当遇到 Hex 16进制报文需要解析内部指标（如血压计、心率仪）时调用此工具按需加载规则。"
                            )
                            put(
                                "parameters",
                                JSONObject().apply {
                                    put("type", "object")
                                    put(
                                        "properties",
                                        JSONObject().apply {
                                            put(
                                                "query",
                                                JSONObject().apply {
                                                    put("type", "string")
                                                    put("description", "协议名称、ID或匹配的主题关键词，例如: '血压计' 或 'gateway/vital'")
                                                }
                                            )
                                        }
                                    )
                                }
                            )
                        }
                    )
                }
            )

            // 工具 3: list_archived_excels
            tools.put(
                JSONObject().apply {
                    put("type", "function")
                    put(
                        "function",
                        JSONObject().apply {
                            put("name", "list_archived_excels")
                            put(
                                "description",
                                "获取本地已自动分卷转储到 Download 目录的所有历史 MQTT 数据 Excel (.xlsx) 文件列表。当数据库满额（如1万条）自动转储后，可通过此工具发现历史归档。"
                            )
                            put(
                                "parameters",
                                JSONObject().apply {
                                    put("type", "object")
                                    put("properties", JSONObject())
                                }
                            )
                        }
                    )
                }
            )

            // 工具 4: get_excel_summary (极速宏观统计画像，防爆上下文)
            tools.put(
                JSONObject().apply {
                    put("type", "function")
                    put(
                        "function",
                        JSONObject().apply {
                            put("name", "get_excel_summary")
                            put(
                                "description",
                                "【防爆核心工具】极速流式分析指定的已归档 Excel (.xlsx) 文件的宏观画像（总报文行数、起止时间戳、Top 10 热门主题分布、涉及设备概况）。分析一个 10,000 行的 Excel 仅需几十毫秒且仅耗费 ~150 tokens。在分析任何历史 Excel 时，必须最先调用此工具获取概况，严禁盲目读取大量原始明细！"
                            )
                            put(
                                "parameters",
                                JSONObject().apply {
                                    put("type", "object")
                                    put(
                                        "properties",
                                        JSONObject().apply {
                                            put(
                                                "fileName",
                                                JSONObject().apply {
                                                    put("type", "string")
                                                    put("description", "Excel 文件名，例如: mqtt_packets_20260923_180000.xlsx")
                                                }
                                            )
                                        }
                                    )
                                    put("required", JSONArray().apply { put("fileName") })
                                }
                            )
                        }
                    )
                }
            )

            // 工具 5: query_excel_data (受控按需采样读取，单次上限 30 条)
            tools.put(
                JSONObject().apply {
                    put("type", "function")
                    put(
                        "function",
                        JSONObject().apply {
                            put("name", "query_excel_data")
                            put(
                                "description",
                                "流式读取指定 Excel (.xlsx) 文件内的具体报文行数据。为保护上下文防止模型崩溃，单次上限严格限制为 30 条。必须结合 keyword 关键字过滤或 offset 分页进行按需精准采样，严禁无差别全量拉取！"
                            )
                            put(
                                "parameters",
                                JSONObject().apply {
                                    put("type", "object")
                                    put(
                                        "properties",
                                        JSONObject().apply {
                                            put(
                                                "fileName",
                                                JSONObject().apply {
                                                    put("type", "string")
                                                    put("description", "Excel 文件名，例如: mqtt_packets_20260923_180000.xlsx")
                                                }
                                            )
                                            put(
                                                "keyword",
                                                JSONObject().apply {
                                                    put("type", "string")
                                                    put("description", "过滤关键词，如特定主题前缀、设备ID或 Hex 特征码，建议明确指定")
                                                }
                                            )
                                            put(
                                                "limit",
                                                JSONObject().apply {
                                                    put("type", "integer")
                                                    put("description", "单次最多读取行数，默认 20，最大允许 30")
                                                }
                                            )
                                            put(
                                                "offset",
                                                JSONObject().apply {
                                                    put("type", "integer")
                                                    put("description", "跳过的匹配行数（用于分页检索），默认为 0")
                                                }
                                            )
                                        }
                                    )
                                    put("required", JSONArray().apply { put("fileName") })
                                }
                            )
                        }
                    )
                }
            )

            // 工具 6: save_protocol_knowledge (对话即沉淀协议规则)
            tools.put(
                JSONObject().apply {
                    put("type", "function")
                    put(
                        "function",
                        JSONObject().apply {
                            put("name", "save_protocol_knowledge")
                            put(
                                "description",
                                "【协议沉淀】当用户在对话中说明、定义或解释某种硬件私有协议格式、报文规范（如血压计Hex格式、心跳包规范）时，调用此工具将协议规则即时持久化到应用知识库中，以便后续所有会话均可自动应用。"
                            )
                            put(
                                "parameters",
                                JSONObject().apply {
                                    put("type", "object")
                                    put(
                                        "properties",
                                        JSONObject().apply {
                                            put(
                                                "name",
                                                JSONObject().apply {
                                                    put("type", "string")
                                                    put("description", "协议简短名称，例如: '迈瑞血压计协议' 或 '网关心跳包'")
                                                }
                                            )
                                            put(
                                                "topicFilter",
                                                JSONObject().apply {
                                                    put("type", "string")
                                                    put("description", "关联的 MQTT 主题或关键字过滤条件，如 'vital/bp'，可为空")
                                                }
                                            )
                                            put(
                                                "description",
                                                JSONObject().apply {
                                                    put("type", "string")
                                                    put("description", "详细的协议字段解码规则、字节偏移、计算公式等说明")
                                                }
                                            )
                                            put(
                                                "sampleHex",
                                                JSONObject().apply {
                                                    put("type", "string")
                                                    put("description", "示例 Hex 串或报文样例，可为空")
                                                }
                                            )
                                        }
                                    )
                                    put("required", JSONArray().apply {
                                        put("name")
                                        put("description")
                                    })
                                }
                            )
                        }
                    )
                }
            )

            // 工具 7: publish_mqtt_message (自然语言双向发包与 Mock)
            tools.put(
                JSONObject().apply {
                    put("type", "function")
                    put(
                        "function",
                        JSONObject().apply {
                            put("name", "publish_mqtt_message")
                            put(
                                "description",
                                "【MQTT 报文下发与 Mock 发送】直接向 Broker 指定主题发布消息（支持纯文本/JSON，或以空格分隔的 Hex 16进制串如 'AA 55 01 02'）。当用户要求模拟发包、下发控制指令、发送心跳或调试设备时调用此工具。"
                            )
                            put(
                                "parameters",
                                JSONObject().apply {
                                    put("type", "object")
                                    put(
                                        "properties",
                                        JSONObject().apply {
                                            put(
                                                "topic",
                                                JSONObject().apply {
                                                    put("type", "string")
                                                    put("description", "发布的目标主题，例如: 'college/breaker/control/THFC012CCCBDDC' 或 'gateway/cmd'")
                                                }
                                            )
                                            put(
                                                "payload",
                                                JSONObject().apply {
                                                    put("type", "string")
                                                    put("description", "报文内容，可为 JSON 字符串、纯文本，或 Hex 字符串（如 'AA 55 01 04 00 00'）")
                                                }
                                            )
                                            put(
                                                "format",
                                                JSONObject().apply {
                                                    put("type", "string")
                                                    put("description", "报文编码格式: TEXT（纯文本/JSON，默认）或 HEX（十六进制字节流）")
                                                }
                                            )
                                            put(
                                                "qos",
                                                JSONObject().apply {
                                                    put("type", "integer")
                                                    put("description", "MQTT QoS 服务质量等级，可选 0, 1, 2，默认为 0")
                                                }
                                            )
                                            put(
                                                "retain",
                                                JSONObject().apply {
                                                    put("type", "boolean")
                                                    put("description", "是否保留消息 (Retain)，默认为 false")
                                                }
                                            )
                                        }
                                    )
                                    put("required", JSONArray().apply {
                                        put("topic")
                                        put("payload")
                                    })
                                }
                            )
                        }
                    )
                }
            )

            // 工具 8: web_search (实时工业与技术规约联网搜索)
            tools.put(
                JSONObject().apply {
                    put("type", "function")
                    put(
                        "function",
                        JSONObject().apply {
                            put("name", "web_search")
                            put(
                                "description",
                                "【实时工业与技术规约联网搜索】在面对未知私有硬件报文（如电表 DL/T 645、水气表 CJ/T 188、环保 HJ 212、车载车联网 JT/T 808、Modbus、BACnet 等规约）、特定硬件/PLC 报错代码（如西门子、汇川、三菱等）、或需要查阅最新工业技术标准与参考资料时调用此工具。通过双通道搜索引擎实时检索公网权威资料与技术规范。"
                            )
                            put(
                                "parameters",
                                JSONObject().apply {
                                    put("type", "object")
                                    put(
                                        "properties",
                                        JSONObject().apply {
                                            put(
                                                "query",
                                                JSONObject().apply {
                                                    put("type", "string")
                                                    put("description", "要检索的工业关键词、协议规约名称、帧格式或设备故障代码，例如: 'DL/T 645-2007 报文格式'、'HJ 212 报文校验算法' 或 '汇川变频器 E010 故障代码'")
                                                }
                                            )
                                        }
                                    )
                                    put("required", JSONArray().apply { put("query") })
                                }
                            )
                        }
                    )
                }
            )

            return tools
        }
    }

    /**
     * 执行由大模型发起的具体工具调用，返回纯文本/JSON结果
     */
    fun executeTool(functionName: String, argumentsJson: String): String {
        return try {
            val args = if (argumentsJson.isNotBlank()) JSONObject(argumentsJson) else JSONObject()
            when (functionName) {
                "execute_sqlite_query" -> {
                    // 架构升级兼容：提示大模型数据已全面在内存与 Excel 分级维护
                    val liveCount = livePacketsProvider?.invoke()?.size ?: 0
                    "【存储架构升级提示】实时报文已全面升级为零 I/O 纯内存环形缓冲区 (当前内存活跃 $liveCount 条) 与公共 Download 目录 Excel 自动分卷存储，不再向 SQLite 盲目落盘。请立即调用 get_live_packets(mode=\"summary\") 获取全网主题吞吐画像，或调用 list_archived_excels 分析已转储的历史 Excel 日志！"
                }

                "get_live_packets" -> {
                    val packets = livePacketsProvider?.invoke() ?: emptyList()
                    if (packets.isEmpty()) {
                        return "【内存状态】当前内存实时报文队列为空（暂未收到新报文，或刚启动未建立连接）。"
                    }
                    val mode = args.optString("mode", "sample").lowercase()
                    val topicFilter = args.optString("topicFilter", "").trim()
                    val keyword = args.optString("keyword", "").trim()
                    val limit = args.optInt("limit", 20).coerceIn(1, 50)

                    val filtered = packets.filter { p ->
                        (topicFilter.isBlank() || p.topic.contains(topicFilter, ignoreCase = true)) &&
                        (keyword.isBlank() || p.payload.contains(keyword, ignoreCase = true) || p.devInfo.contains(keyword, ignoreCase = true))
                    }

                    val tslResults = tslParseResultsProvider?.invoke() ?: emptyMap()

                    if (mode == "summary") {
                        val topicGroups = filtered.groupBy { it.topic }
                        val topTopics = topicGroups.mapValues { it.value.size }
                            .toList()
                            .sortedByDescending { it.second }
                            .take(10)
                            .toMap()

                        val sampleDevices = filtered.mapNotNull {
                            val dev = it.devInfo.substringBefore(" ·").trim()
                            if (dev.isNotBlank() && !dev.startsWith("QoS")) dev else null
                        }.distinct().take(15)

                        val firstTime = filtered.firstOrNull()?.timestamp ?: "-"
                        val lastTime = filtered.lastOrNull()?.timestamp ?: "-"

                        // 提取 TSL 物模型解析状态与告警画像
                        val tslMatchedPackets = filtered.mapNotNull { tslResults[it.id] }
                        val warningPackets = tslMatchedPackets.filter { it.hasWarnings }
                        val recentAlarms = warningPackets.takeLast(5).flatMap { r ->
                            r.values.filter { it.isWarning }.mapNotNull { it.warningMessage }
                        }

                        val summaryObj = JSONObject().apply {
                            put("status", "SUCCESS")
                            put("totalInMemory", packets.size)
                            put("matchedCount", filtered.size)
                            put("uniqueTopicsCount", topicGroups.size)
                            put("startTime", firstTime)
                            put("latestTime", lastTime)
                            put("topTopicsThroughput", JSONObject(topTopics))
                            put("sampleDevices", JSONArray(sampleDevices))
                            put("tslProtocolsActive", tslProtocolsProvider?.invoke()?.size ?: 0)
                            put("tslParsedCount", tslMatchedPackets.size)
                            put("alarmPacketsCount", warningPackets.size)
                            if (recentAlarms.isNotEmpty()) {
                                put("recentAlarms", JSONArray(recentAlarms))
                            }
                        }
                        summaryObj.toString()
                    } else {
                        if (filtered.isEmpty()) {
                            return "【内存状态】内存中共有 ${packets.size} 条报文，但未匹配到条件 (主题: '$topicFilter', 关键词: '$keyword') 的报文。"
                        }
                        val sampleList = filtered.takeLast(limit)
                        val array = JSONArray()
                        for (p in sampleList.reversed()) {
                            val itemObj = JSONObject().apply {
                                put("seq", p.packetSeq)
                                put("topic", p.topic)
                                put("time", p.timestamp)
                                put("payload", p.payload)
                                put("devInfo", p.devInfo)
                            }
                            val parseRes = tslResults[p.id]
                            if (parseRes != null) {
                                itemObj.put("tslDecoded", parseRes.toSummaryText())
                                if (parseRes.hasWarnings) {
                                    val warns = parseRes.values.filter { it.isWarning }.mapNotNull { it.warningMessage }
                                    itemObj.put("warnings", JSONArray(warns))
                                }
                            }
                            array.put(itemObj)
                        }
                        val res = JSONObject().apply {
                            put("totalInMemory", packets.size)
                            put("returnedCount", sampleList.size)
                            put("packets", array)
                        }
                        res.toString()
                    }
                }

                "get_protocol_clarification" -> {
                    var query = args.optString("query", "").trim()
                    if (query.isBlank()) query = args.optString("topic", "").trim()
                    if (query.isBlank()) query = args.optString("name", "").trim()
                    val allProtocols = storage.loadAllProtocolKnowledge()
                    if (allProtocols.isEmpty()) {
                        return "暂未录入任何私有协议澄清规则。用户可在「AI 设置 - 协议澄清」中直接粘贴录入。"
                    }
                    val matched = if (query.isNotBlank()) {
                        allProtocols.filter {
                            it.name.contains(query, ignoreCase = true) ||
                                it.topicFilter.contains(query, ignoreCase = true) ||
                                it.description.contains(query, ignoreCase = true)
                        }
                    } else {
                        allProtocols
                    }
                    if (matched.isEmpty()) {
                        val available = allProtocols.joinToString(", ") { it.name }
                        return "未找到匹配 '$query' 的硬件协议规则。当前已录入的协议有: $available"
                    }
                    val array = JSONArray()
                    for (p in matched) {
                        array.put(
                            JSONObject().apply {
                                put("name", p.name)
                                put("topicFilter", p.topicFilter)
                                put("description", p.description)
                                put("sampleHex", p.sampleHex)
                            }
                        )
                    }
                    array.toString()
                }

                "list_archived_excels" -> {
                    val hasFullAccess = if (context != null) ArchivedExcelReader.hasAllFilesAccess(context) else true
                    val files = ArchivedExcelReader.listArchivedExcels(context)
                    if (files.isEmpty()) {
                        val permissionHint = if (!hasFullAccess) {
                            "【⚠️ Android 分区存储限制排查】\n" +
                            "当前设备尚未开启「所有文件访问权限」，系统会静默隐藏由微信、QQ、电脑传输或第三方下载的公共 Excel 文件。\n" +
                            "【解决指引】：\n" +
                            "1. 【推荐】：前往「系统设置」中开启「所有文件访问权限」，开启后 AI 即可直接原地免拷贝直读 Download 目录；\n" +
                            "2. 【免权限即选即用】：点击右上角「更多(···)」菜单中的「📂 导入外部 Excel 日志」，手动点选该文件即可立即分析；\n" +
                            "3. 若需分析当前在线或实时收到的报文，请直接告诉我查询实时数据。"
                        } else {
                            "【检索结果】已全方位检索公共 Download 目录、微信/QQ下载目录与应用内部目录，暂未发现 .xlsx 归档文件。\n" +
                            "建议引导用户确认文件是否为 .xlsx 格式，或通过右上角「更多(···)」->「📂 导入外部 Excel 日志」手动点选。"
                        }
                        return permissionHint
                    }
                    val array = JSONArray()
                    for (f in files) {
                        val sizeKb = "%.1f KB".format(f.fileSizeBytes / 1024.0)
                        val timeStr = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault())
                            .format(java.util.Date(f.lastModifiedTime))
                        array.put(
                            JSONObject().apply {
                                put("fileName", f.fileName)
                                put("size", sizeKb)
                                put("location", f.locationDesc)
                                put("modifiedAt", timeStr)
                            }
                        )
                    }
                    val obj = JSONObject().apply {
                        put("fileCount", files.size)
                        put("hasFullFilesAccess", hasFullAccess)
                        put("files", array)
                        if (!hasFullAccess) {
                            put("notice", "注意：当前未开启全盘文件权限，若有通过微信/电脑拷贝的外部 Excel 未列出，请使用右上角「导入外部 Excel 日志」导入。")
                        }
                    }
                    obj.toString()
                }

                "get_excel_summary" -> {
                    val fileName = args.optString("fileName", "").trim()
                    if (fileName.isBlank()) return "错误: fileName 不能为空"
                    val summary = ArchivedExcelReader.analyzeExcelSummary(context, fileName)
                    if (summary == null) {
                        "无法分析文件 '$fileName'。若该文件由外部微信或电脑拷贝传入，请在右上角菜单点击「📂 导入外部 Excel 日志」导入后再分析，或确认文件名是否准确。"
                    } else {
                        val obj = JSONObject().apply {
                            put("fileName", summary.fileName)
                            put("totalRows", summary.totalRows)
                            put("startTime", summary.startTime)
                            put("endTime", summary.endTime)
                            put("topTopics", JSONObject(summary.topTopics))
                            put("uniqueDevicesCount", summary.uniqueDevices.size)
                            put("sampleDevices", JSONArray(summary.uniqueDevices.take(10)))
                        }
                        obj.toString()
                    }
                }

                "query_excel_data" -> {
                    val fileName = args.optString("fileName", "").trim()
                    val keyword = args.optString("keyword", "").trim()
                    val limit = args.optInt("limit", 20).coerceIn(1, 30)
                    val offset = args.optInt("offset", 0).coerceAtLeast(0)
                    val rows = ArchivedExcelReader.readExcelRows(context, fileName, keyword, limit, offset)
                    val array = JSONArray()
                    for (r in rows) {
                        array.put(
                            JSONObject().apply {
                                put("seq", r.seq)
                                put("topic", r.topic)
                                put("deviceId", r.deviceId)
                                put("payload", r.payload)
                                put("time", r.time)
                            }
                        )
                    }
                    val resultObj = JSONObject().apply {
                        put("fileName", fileName)
                        put("offset", offset)
                        put("returnedCount", rows.size)
                        put("hasMore", rows.size == limit)
                        put("rows", array)
                    }
                    resultObj.toString()
                }

                "save_protocol_knowledge" -> {
                    val name = args.optString("name", "").trim()
                    val desc = args.optString("description", "").trim()
                    val topic = args.optString("topicFilter", "").trim()
                    val hex = args.optString("sampleHex", "").trim()
                    if (name.isBlank() || desc.isBlank()) {
                        "保存失败: 协议名称(name)和协议解码描述(description)不能为空"
                    } else {
                        val item = ProtocolKnowledge(
                            name = name,
                            topicFilter = topic,
                            description = desc,
                            sampleHex = hex
                        )
                        storage.saveProtocolKnowledge(item)
                        "协议规则【$name】已成功沉淀并持久化至应用知识库！后续涉及相关主题或设备时将自动应用该解析规则。"
                    }
                }

                "publish_mqtt_message" -> {
                    val topic = args.optString("topic", "").trim()
                    val payloadStr = args.optString("payload", "")
                    val format = args.optString("format", "TEXT").uppercase()
                    val qos = args.optInt("qos", 0).coerceIn(0, 2)
                    val retain = args.optBoolean("retain", false)

                    if (topic.isBlank()) {
                        "发布失败: 目标主题 (topic) 不能为空"
                    } else {
                        try {
                            val payloadBytes = if (format == "HEX") {
                                val cleanHex = payloadStr.replace(" ", "").replace("\n", "").replace("0x", "").replace("0X", "")
                                if (cleanHex.length % 2 != 0) {
                                    return "发布失败: HEX 格式的报文十六进制字符总数必须为偶数，当前长度为 ${cleanHex.length}"
                                }
                                cleanHex.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
                            } else {
                                payloadStr.toByteArray(Charsets.UTF_8)
                            }

                            val result = kotlinx.coroutines.runBlocking {
                                com.example.mqtt.MqttClientManager.publish(topic, payloadBytes, qos, retain)
                            }
                            if (result.isSuccess) {
                                onMessagePublished?.invoke(topic, qos, retain, payloadStr)
                                JSONObject().apply {
                                    put("status", "SUCCESS")
                                    put("message", "报文已成功送达 Broker 并下发")
                                    put("topic", topic)
                                    put("qos", qos)
                                    put("retain", retain)
                                    put("bytesSent", payloadBytes.size)
                                    put("format", format)
                                }.toString()
                            } else {
                                val err = result.exceptionOrNull()?.message ?: "未知错误"
                                "发布失败: $err。请确认 MQTT Broker 当前是否已连接且有发布权限。"
                            }
                        } catch (e: Exception) {
                            "报文编码处理或发布异常: ${e.message}"
                        }
                    }
                }

                "web_search" -> {
                    val query = args.optString("query", "").trim()
                    if (query.isBlank()) {
                        "搜索失败: 检索关键词 (query) 不能为空"
                    } else {
                        val searchResult = kotlinx.coroutines.runBlocking {
                            WebSearchHelper.search(query, maxResults = 4)
                        }
                        if (searchResult.items.isEmpty()) {
                            "【联网搜索结果】未检索到与 '$query' 相关的资料（${searchResult.error ?: "无匹配结果"}）。建议更换更具针对性的工控关键词或规约标准名称重试。"
                        } else {
                            val array = JSONArray()
                            for (item in searchResult.items) {
                                array.put(
                                    JSONObject().apply {
                                        put("title", item.title)
                                        put("snippet", item.snippet)
                                        put("url", item.url)
                                    }
                                )
                            }
                            val resObj = JSONObject().apply {
                                put("query", searchResult.query)
                                put("engine", searchResult.source)
                                put("resultCount", searchResult.items.size)
                                put("results", array)
                            }
                            resObj.toString()
                        }
                    }
                }

                else -> "未知工具: $functionName"
            }
        } catch (e: Exception) {
            "执行工具 [$functionName] 异常: ${e.message}"
        }
    }
}
