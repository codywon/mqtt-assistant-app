package com.example.ai

import android.util.Log
import com.example.model.AiAgentConfig
import com.example.model.AiChatMessage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL

/**
 * 生产级轻量原生 OpenAI 兼容流式大模型客户端 & ReAct Agent 执行引擎：
 * 1. 纯原生 HttpURLConnection 实现，零第三方库依赖，APK 增加 0KB；
 * 2. 完美适配 DeepSeek-V3 / DeepSeek-R1 (包含思考链 reasoning_content) / 通义千问 / OpenAI / 本地 Ollama；
 * 3. 完整支持 Tool Calling 循环调用 (ReAct)，使 Agent 能自主调用数据库与 Excel 分析工具并闭环输出。
 */
class AiAgentClient(
    private val toolRegistry: SIAgentToolRegistry
) {

    companion object {
        private const val TAG = "AiAgentClient"

        val DEFAULT_SYSTEM_PROMPT = """
            你是一个内嵌在移动端「MQTT 助手」中的专业系统集成与数据分析智能体 (SI Data Analysis Agent)。
            
            【核心能力与工具】
            1. 你可以随时调用 execute_sqlite_query 工具，针对当前 SQLite 本地数据库 (mqtt_assistant.db) 自由编写只读 SELECT SQL 语句，查询实时收到的报文表 tbl_mqtt_packets (字段包含 topic, qos, payload, timestamp, created_at 等)；
            2. 你可以调用 get_protocol_clarification 工具，获取用户录入的私有硬件设备协议说明。对于 16 进制 Hex 报文 (如体征网关、血压计、心电仪)，依据协议规则解码具体字段 (如高压、低压、心率、温度)；
            3. 当数据库报文满额分卷转储为 Excel 后，你可以调用 list_archived_excels 查看已归档文件，并调用 query_excel_data 读取 Excel 内的历史数据进行跨卷趋势对比；
            
            【行为准则】
            - 风格专业、精准、亲切，输出格式采用结构优雅的 Markdown (支持表格、粗体、列表、高亮卡片)；
            - 当用户问及具体网关或设备数据时，必须先调用工具查阅真实数据，切勿凭空编造；
            - 若发现体征数据异常 (如高血压、心动过速、丢包严重)，请在结论中显著以 ⚠️ 标注并附上温馨的健康/排查建议。
        """.trimIndent()
    }

    /**
     * 发起 Agent 会话循环（支持流式输出与 Tool Calling 闭环）
     */
    suspend fun chatStream(
        config: AiAgentConfig,
        conversationHistory: List<AiChatMessage>,
        onChunk: (delta: String, isThinking: Boolean) -> Unit,
        onToolAction: (actionText: String) -> Unit,
        onError: (errorText: String) -> Unit,
        onComplete: (fullContent: String, reasoningContent: String) -> Unit
    ) = withContext(Dispatchers.IO) {
        if (config.apiKey.isBlank()) {
            onError("未配置 API Key。请点击右上角设置图标填入大模型 API Key（推荐使用 DeepSeek 或通义千问）")
            return@withContext
        }

        val messagesArray = JSONArray()

        // 1. System Prompt
        val systemContent = if (config.customPrompt.isNotBlank()) {
            "${config.customPrompt}\n\n$DEFAULT_SYSTEM_PROMPT"
        } else {
            DEFAULT_SYSTEM_PROMPT
        }
        messagesArray.put(
            JSONObject().apply {
                put("role", "system")
                put("content", systemContent)
            }
        )

        // 2. 注入历史会话 (取最近 15 轮避免超过上限)
        val recentHistory = conversationHistory.takeLast(15)
        for (msg in recentHistory) {
            if (msg.role == "user" || msg.role == "assistant") {
                messagesArray.put(
                    JSONObject().apply {
                        put("role", msg.role)
                        put("content", msg.content)
                    }
                )
            }
        }

        val toolsJson = SIAgentToolRegistry.getToolDefinitionsJson()

        var loopCount = 0
        val maxLoops = 5 // 最多允许 5 轮工具链式调用，防止死循环
        val fullAccumulatedContent = StringBuilder()
        val fullAccumulatedReasoning = StringBuilder()

        while (loopCount < maxLoops) {
            loopCount++
            val requestBody = JSONObject().apply {
                put("model", config.modelName)
                put("messages", messagesArray)
                put("tools", toolsJson)
                put("tool_choice", "auto")
                put("temperature", config.temperature)
                put("max_tokens", config.maxTokens)
                put("stream", true)
            }

            var conn: HttpURLConnection? = null
            var toolCallsDetected = mutableListOf<JSONObject>()
            var currentIterationContent = StringBuilder()
            var currentIterationReasoning = StringBuilder()

            try {
                val baseUrl = config.baseUrl.trim().trimEnd('/')
                val endpoint = if (baseUrl.endsWith("/v1")) "$baseUrl/chat/completions" else "$baseUrl/v1/chat/completions"
                val url = URL(endpoint)

                conn = (url.openConnection() as HttpURLConnection).apply {
                    requestMethod = "POST"
                    connectTimeout = 15_000
                    readTimeout = 60_000
                    doOutput = true
                    doInput = true
                    setRequestProperty("Authorization", "Bearer ${config.apiKey.trim()}")
                    setRequestProperty("Content-Type", "application/json; charset=UTF-8")
                    setRequestProperty("Accept", "text/event-stream")
                }

                conn.outputStream.use { os ->
                    os.write(requestBody.toString().toByteArray(Charsets.UTF_8))
                    os.flush()
                }

                val responseCode = conn.responseCode
                if (responseCode !in 200..299) {
                    val errorBody = conn.errorStream?.bufferedReader()?.use { it.readText() } ?: "HTTP $responseCode"
                    Log.e(TAG, "API 请求失败: $errorBody")
                    onError("大模型 API 返回异常 ($responseCode): $errorBody")
                    return@withContext
                }

                val reader = BufferedReader(InputStreamReader(conn.inputStream, Charsets.UTF_8))
                var line: String? = reader.readLine()

                // 解析聚合 tool_calls 的 Map: index -> (id, name, argsBuilder)
                val toolCallMap = mutableMapOf<Int, Triple<StringBuilder, StringBuilder, StringBuilder>>()

                while (line != null) {
                    val trimmed = line.trim()
                    if (trimmed.startsWith("data:")) {
                        val payload = trimmed.substring(5).trim()
                        if (payload == "[DONE]") {
                            break
                        }
                        try {
                            val json = JSONObject(payload)
                            val choices = json.optJSONArray("choices")
                            if (choices != null && choices.length() > 0) {
                                val choice = choices.getJSONObject(0)
                                val delta = choice.optJSONObject("delta")
                                if (delta != null) {
                                    // 思考链 delta (DeepSeek-R1 / Qwen 等)
                                    val reasoningDelta = delta.optString("reasoning_content", "")
                                    if (reasoningDelta.isNotEmpty()) {
                                        currentIterationReasoning.append(reasoningDelta)
                                        fullAccumulatedReasoning.append(reasoningDelta)
                                        onChunk(reasoningDelta, true)
                                    }

                                    // 普通文本 delta
                                    val contentDelta = delta.optString("content", "")
                                    if (contentDelta.isNotEmpty()) {
                                        currentIterationContent.append(contentDelta)
                                        fullAccumulatedContent.append(contentDelta)
                                        onChunk(contentDelta, false)
                                    }

                                    // 工具调用 delta
                                    val deltaTools = delta.optJSONArray("tool_calls")
                                    if (deltaTools != null) {
                                        for (i in 0 until deltaTools.length()) {
                                            val t = deltaTools.getJSONObject(i)
                                            val idx = t.optInt("index", 0)
                                            val id = t.optString("id", "")
                                            val func = t.optJSONObject("function")
                                            val name = func?.optString("name", "") ?: ""
                                            val argsPart = func?.optString("arguments", "") ?: ""

                                            val triple = toolCallMap.getOrPut(idx) {
                                                Triple(StringBuilder(), StringBuilder(), StringBuilder())
                                            }
                                            if (id.isNotEmpty()) triple.first.append(id)
                                            if (name.isNotEmpty()) triple.second.append(name)
                                            if (argsPart.isNotEmpty()) triple.third.append(argsPart)
                                        }
                                    }
                                }
                            }
                        } catch (_: Exception) {}
                    }
                    line = reader.readLine()
                }

                // 收集当前轮次模型发起的 tool_calls
                for ((_, triple) in toolCallMap) {
                    val id = triple.first.toString()
                    val name = triple.second.toString()
                    val args = triple.third.toString()
                    if (name.isNotEmpty()) {
                        toolCallsDetected.add(
                            JSONObject().apply {
                                put("id", if (id.isNotEmpty()) id else "call_${System.currentTimeMillis()}")
                                put("type", "function")
                                put(
                                    "function",
                                    JSONObject().apply {
                                        put("name", name)
                                        put("arguments", args)
                                    }
                                )
                            }
                        )
                    }
                }

            } catch (e: Exception) {
                Log.e(TAG, "Agent 会话发生网络异常", e)
                onError("网络通信失败: ${e.message}")
                return@withContext
            } finally {
                conn?.disconnect()
            }

            // 如果本轮次大模型没有调用工具，说明已产生最终解答，结束 ReAct 循环！
            if (toolCallsDetected.isEmpty()) {
                onComplete(fullAccumulatedContent.toString(), fullAccumulatedReasoning.toString())
                return@withContext
            }

            // 否则：大模型发起了工具调用，将大模型的调用意图与本地工具执行结果压入 messagesArray，进入下一轮
            val assistantMsgWithTools = JSONObject().apply {
                put("role", "assistant")
                if (currentIterationContent.isNotEmpty()) {
                    put("content", currentIterationContent.toString())
                } else {
                    put("content", JSONObject.NULL)
                }
                val callsArray = JSONArray()
                for (t in toolCallsDetected) callsArray.put(t)
                put("tool_calls", callsArray)
            }
            messagesArray.put(assistantMsgWithTools)

            // 本地依次执行工具
            for (toolObj in toolCallsDetected) {
                val callId = toolObj.getString("id")
                val funcObj = toolObj.getJSONObject("function")
                val funcName = funcObj.getString("name")
                val funcArgs = funcObj.getString("arguments")

                val friendlyAction = when (funcName) {
                    "execute_sqlite_query" -> "🔍 正在执行 SQLite 数据检索..."
                    "get_protocol_clarification" -> "📖 正在获取私有协议澄清说明..."
                    "list_archived_excels" -> "📁 正在检索已转储的 Excel 历史分卷..."
                    "query_excel_data" -> "📊 正在流式读取已归档 Excel 报文行..."
                    else -> "⚙️ 正在执行工具: $funcName..."
                }
                onToolAction(friendlyAction)

                val toolResult = toolRegistry.executeTool(funcName, funcArgs)

                // 将工具结果注入上下文
                messagesArray.put(
                    JSONObject().apply {
                        put("role", "tool")
                        put("tool_call_id", callId)
                        put("name", funcName)
                        put("content", toolResult)
                    }
                )
            }
        }

        // 达到最大轮次保护时直接完结
        onComplete(fullAccumulatedContent.toString(), fullAccumulatedReasoning.toString())
    }
}
