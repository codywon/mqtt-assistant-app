package com.example.ai

import com.example.data.MqttStorageRepository
import com.example.util.ArchivedExcelReader
import org.json.JSONArray
import org.json.JSONObject

/**
 * SI 智能数据分析 Agent 工具注册中心与执行引擎：
 * 遵循 OpenAI Tool Calling (Function Calling) 规范，为大模型提供操作底层 SQLite 与历史 Excel 文件的工具箱。
 */
class SIAgentToolRegistry(private val storage: MqttStorageRepository) {

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
                                "执行只读 SQLite SQL 查询以分析 MQTT 实时数据。表 tbl_mqtt_packets 字段包含: topic(主题), qos, packetSeq, timestamp(HH:mm:ss.SSS), payload(报文内容/Hex串/JSON), devInfo, sizeText, category, dotColorHex, created_at(毫秒时间戳)。注意：只允许单条只读 SELECT 语句，禁止任何变更操作。"
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
                                "查询用户预先录入的私有硬件设备协议澄清说明。当遇到 Hex (16进制) 或自定义二进制报文（如血压计、体征网关）无法理解内部字节含义时，调用此工具获取对应的协议说明与字段偏移量。"
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
                                                    put("description", "报文所属的 MQTT Topic 或关键词，用于检索匹配的协议说明")
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

            // 工具 4: query_excel_data
            tools.put(
                JSONObject().apply {
                    put("type", "function")
                    put(
                        "function",
                        JSONObject().apply {
                            put("name", "query_excel_data")
                            put(
                                "description",
                                "流式穿透读取指定的已归档 Excel (.xlsx) 文件内的报文行数据。支持按主题或内容关键字检索前 N 条数据。"
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
                                                    put("description", "Excel 文件名，例如: MQTT_Packets_20260923_180000.xlsx")
                                                }
                                            )
                                            put(
                                                "keyword",
                                                JSONObject().apply {
                                                    put("type", "string")
                                                    put("description", "过滤关键词，如特定网关ID、主题前缀或 Hex 标记，为空则读取全部")
                                                }
                                            )
                                            put(
                                                "limit",
                                                JSONObject().apply {
                                                    put("type", "integer")
                                                    put("description", "最多读取行数，默认 50，最大 150")
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
                    val sql = args.optString("sql", "").trim()
                    if (sql.isBlank()) return "错误: SQL 语句不能为空"
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
                }

                "get_protocol_clarification" -> {
                    val topic = args.optString("topic", "").trim()
                    val allProtocols = storage.loadAllProtocolKnowledge()
                    if (allProtocols.isEmpty()) {
                        return "暂未录入任何私有协议澄清规则。用户可在「AI 设置 - 协议澄清」中添加该网关主题的 Hex 字节定义。"
                    }
                    val matched = if (topic.isNotBlank()) {
                        allProtocols.filter {
                            topic.contains(it.topicFilter, ignoreCase = true) ||
                                it.topicFilter.contains(topic, ignoreCase = true) ||
                                it.name.contains(topic, ignoreCase = true)
                        }
                    } else {
                        allProtocols
                    }
                    val targetList = if (matched.isNotEmpty()) matched else allProtocols
                    val array = JSONArray()
                    for (p in targetList) {
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

                "query_excel_data" -> {
                    val fileName = args.optString("fileName", "")
                    val keyword = args.optString("keyword", "")
                    val limit = args.optInt("limit", 50).coerceIn(1, 150)
                    val rows = ArchivedExcelReader.readExcelRows(fileName, keyword, limit)
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
                        put("matchedCount", rows.size)
                        put("rows", array)
                    }
                    resultObj.toString()
                }

                else -> "未知工具: $functionName"
            }
        } catch (e: Exception) {
            "执行工具 [$functionName] 异常: ${e.message}"
        }
    }
}
