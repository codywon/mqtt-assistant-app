package com.example.ai

import com.example.data.MqttStorageRepository
import com.example.model.ProtocolKnowledge
import com.example.util.ArchivedExcelReader
import org.json.JSONArray
import org.json.JSONObject

/**
 * SI 智能数据分析 Agent 工具注册中心与执行引擎：
 * 遵循 OpenAI Tool Calling (Function Calling) 规范，为大模型提供操作底层 SQLite 与历史 Excel 文件的工具箱。
 */
class SIAgentToolRegistry(val storage: MqttStorageRepository) {

    companion object {
        /**
         * 获取提供给大模型的工具描述定义清单 (JSON Schema)
         */
        fun getToolDefinitionsJson(): JSONArray {
            val tools = JSONArray()

            // 工具 1: execute_sqlite_query
            tools.put(
                JSONObject().apply {
                    put("type", "function")
                    put(
                        "function",
                        JSONObject().apply {
                            put("name", "execute_sqlite_query")
                            put(
                                "description",
                                "执行只读 SQLite SQL 查询以分析 MQTT 实时数据。表 tbl_mqtt_packets 真实可用列仅为: topic(主题/网关路径), qos, packetSeq, timestamp(HH:mm:ss.SSS), payload(报文内容/Hex串/JSON), devInfo(设备信息), sizeText, category, created_at(毫秒时间戳)。⚠️重要提醒：绝对没有名为 gateway、gateway_id、device_id 的列！网关名称通常位于 topic 中，如查询各网关报文请写: SELECT topic, count(*) as count FROM tbl_mqtt_packets GROUP BY topic ORDER BY count DESC。"
                            )
                            put(
                                "parameters",
                                JSONObject().apply {
                                    put("type", "object")
                                    put(
                                        "properties",
                                        JSONObject().apply {
                                            put(
                                                "sql",
                                                JSONObject().apply {
                                                    put("type", "string")
                                                    put(
                                                        "description",
                                                        "标准只读 SELECT SQL 查询语句，例如: SELECT topic, count(*) as cnt FROM tbl_mqtt_packets GROUP BY topic"
                                                    )
                                                }
                                            )
                                        }
                                    )
                                    put("required", JSONArray().apply { put("sql") })
                                }
                            )
                        }
                    )
                }
            )

            // 工具 2: get_protocol_clarification
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
                    var sql = args.optString("sql", "").trim()
                    if (sql.isBlank()) sql = args.optString("query", "").trim()
                    if (sql.isBlank()) sql = args.optString("statement", "").trim()
                    if (sql.isBlank()) return "错误: SQL 语句不能为空"
                    try {
                        val rows = storage.executeReadOnlyQuery(sql)
                        val array = JSONArray()
                        for (row in rows) {
                            array.put(JSONObject(row))
                        }
                        val resultObj = JSONObject().apply {
                            put("rowCount", rows.size)
                            put("rows", array)
                        }
                        resultObj.toString()
                    } catch (e: Exception) {
                        "SQL 执行失败: ${e.message}。特别提醒: 表 tbl_mqtt_packets 真实可用列名仅有 (id, topic, qos, packetSeq, timestamp, payload, devInfo, sizeText, category, created_at)。绝不存在名为 gateway 或 gateway_id 的列！查询各网关吞吐或设备，请以 topic 字段进行分组聚合，例如: SELECT topic, count(*) as count FROM tbl_mqtt_packets GROUP BY topic ORDER BY count DESC。请修正 SQL 后重新执行。"
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
                    val files = ArchivedExcelReader.listArchivedExcels()
                    if (files.isEmpty()) {
                        return "本地 Download 目录下暂无 MQTT 归档 Excel 文件。"
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
                                put("modifiedAt", timeStr)
                            }
                        )
                    }
                    array.toString()
                }

                "get_excel_summary" -> {
                    val fileName = args.optString("fileName", "").trim()
                    if (fileName.isBlank()) return "错误: fileName 不能为空"
                    val summary = ArchivedExcelReader.analyzeExcelSummary(fileName)
                    if (summary == null) {
                        "无法分析文件 '$fileName'，请检查文件是否存在且格式有效。"
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
                    val rows = ArchivedExcelReader.readExcelRows(fileName, keyword, limit, offset)
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

                else -> "未知工具: $functionName"
            }
        } catch (e: Exception) {
            "执行工具 [$functionName] 异常: ${e.message}"
        }
    }
}
