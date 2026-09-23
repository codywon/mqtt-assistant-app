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
 * 生产级极简 ReAct Agent 执行引擎 & OpenAI 兼容流式客户端：
 * 设计哲学与架构深度参考著名的「Pi Agent」400 行极简核心范式：
 * 
 * 1. 【纯粹 ReAct 循环】：Thought(推理思考) -> Action(工具调用) -> Observation(环境观测) -> Thought -> Final Answer；
 * 2. 【70% 上下文自动压缩】：动态感知 Token 水位，超过 70% 阈值时自动触发无损记忆提炼，实现无上限安全会话；
 * 3. 【四大原子工具集成】：查库(SQL)、读文件(Excel)、查协议(Protocol)、搜归档(Find)；
 * 4. 【零外部库纯原生实现】：纯 HttpURLConnection + SSE 流式解析，完美兼容 DeepSeek-R1 思考链、通义千问、OpenAI、Ollama。
 */
class AiAgentClient(
    private val toolRegistry: SIAgentToolRegistry
) {

    companion object {
        private const val TAG = "AiAgentClient"

        val DEFAULT_SYSTEM_PROMPT = """
            你是一个内嵌在移动端「MQTT 助手」中的专业系统集成与数据分析智能体 (SI Data Analysis Agent)。
            
            【核心行为模式 (ReAct)】
            你遵循严谨的「思考(Thought) -> 行动(Action) -> 观测(Observation) -> 终答(Final Answer)」循环：
            1. 当用户提出数据统计、网关分析、体征筛查或报文解析时，请先思考需要调用的工具；
            2. 发起对应的工具调用；拿到工具返回的数据后，仔细观察分析，若数据不足可继续发起下一步工具调用；
            3. 数据齐全后，给出专业、亲切、结构化 (Markdown 样式) 的分析报告与健康/业务建议。
            
            【可用工具箱】
            - execute_sqlite_query: 执行只读 SQL 语句查询当前 SQLite 数据库 (tbl_mqtt_packets)，分析实时/离线报文；
            - get_protocol_clarification: 查询用户录入的硬件私有协议 (如血压计、体征网关的 Hex 各字节含义)；
            - list_archived_excels: 扫描检索已转储到系统 Download 目录的 Excel 历史分卷列表；
            - query_excel_data: 穿透读取指定归档 Excel 内部的历史明细行。
            
            【分析准则】
            - 切勿凭空捏造数据，必须基于真实的工具查询结果进行归纳；
            - 若发现异常体征指标 (例如收缩压 ≥ 140mmHg、舒张压 ≥ 90mmHg、心率过速)，请在结论中显著以 ⚠️ 标出。
        """.trimIndent()
    }

    /**
     * 发起 ReAct Agent 智能体心跳循环
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

        val systemContent = if (config.customPrompt.isNotBlank()) {
            "${config.customPrompt}\n\n$DEFAULT_SYSTEM_PROMPT"
        } else {
            DEFAULT_SYSTEM_PROMPT
        }

        // ==========================================
        // 1. 初始化上下文（结合 70% 水位动态自适应压缩）
        // ==========================================
        var messagesArray = ContextCompactor.buildCompactedMessagesJson(
            systemContent = systemContent,
            historyList = conversationHistory,
            config = config
        )

        val toolsJson = SIAgentToolRegistry.getToolDefinitionsJson()

        var step = 0
        val maxSteps = 8 // 遵循 Pi Agent 范式，支持多达 8 步推理与动作链
        val fullAccumulatedContent = StringBuilder()
        val fullAccumulatedReasoning = StringBuilder()

        // ==========================================
        // 2. The ReAct Loop (Thought -> Action -> Observation)
        // ==========================================
        while (step < maxSteps) {
            step++

            // 每一步动作前，再次检查当前活跃上下文水位；若工具返回过大导致超 70%，自动压缩！
            messagesArray = ContextCompactor.compactActiveMessages(messagesArray, config)

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
            val toolCallsDetected = mutableListOf<JSONObject>()
            val currentStepContent = StringBuilder()
            val currentStepReasoning = StringBuilder()

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
                    Log.e(TAG, "API 请求返回错误 ($responseCode): $errorBody")
                    onError("大模型服务返回异常 ($responseCode): $errorBody")
                    return@withContext
                }

                val reader = BufferedReader(InputStreamReader(conn.inputStream, Charsets.UTF_8))
                var line: String? = reader.readLine()

                // 解析聚合 SSE 中流式返回的 tool_calls
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
                                    // 1. Thought / 思考链 (DeepSeek-R1 / QwQ 等)
                                    val reasoningDelta = delta.optString("reasoning_content", "")
                                    if (reasoningDelta.isNotEmpty()) {
                                        currentStepReasoning.append(reasoningDelta)
                                        fullAccumulatedReasoning.append(reasoningDelta)
                                        onChunk(reasoningDelta, true)
                                    }

                                    // 2. 正文打字机增量
                                    val contentDelta = delta.optString("content", "")
                                    if (contentDelta.isNotEmpty()) {
                                        currentStepContent.append(contentDelta)
                                        fullAccumulatedContent.append(contentDelta)
                                        onChunk(contentDelta, false)
                                    }

                                    // 3. Action 动作增量 (tool_calls)
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

                // 汇总当前步生成的 Action
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
                Log.e(TAG, "ReAct 会话通信异常", e)
                onError("网络通信失败: ${e.message}")
                return@withContext
            } finally {
                conn?.disconnect()
            }

            // ==========================================
            // 3. 终答判定 (Final Answer)
            // ==========================================
            if (toolCallsDetected.isEmpty()) {
                // 模型无需再采取 Action，已得出最终结论
                onComplete(fullAccumulatedContent.toString(), fullAccumulatedReasoning.toString())
                return@withContext
            }

            // ==========================================
            // 4. 执行 Action 并获取 Observation
            // ==========================================
            val assistantMsg = JSONObject().apply {
                put("role", "assistant")
                if (currentStepContent.isNotEmpty()) {
                    put("content", currentStepContent.toString())
                } else {
                    put("content", JSONObject.NULL)
                }
                val callsArray = JSONArray()
                for (t in toolCallsDetected) callsArray.put(t)
                put("tool_calls", callsArray)
            }
            messagesArray.put(assistantMsg)

            // 依次执行每个 Action 工具
            for (toolObj in toolCallsDetected) {
                val callId = toolObj.getString("id")
                val funcObj = toolObj.getJSONObject("function")
                val funcName = funcObj.getString("name")
                val funcArgs = funcObj.getString("arguments")

                val statusText = when (funcName) {
                    "execute_sqlite_query" -> "🔍 [Action] 正在执行只读 SQL 查询..."
                    "get_protocol_clarification" -> "📖 [Action] 正在检索硬件私有协议知识..."
                    "list_archived_excels" -> "📁 [Action] 正在扫描已转储 Excel 历史分卷..."
                    "query_excel_data" -> "📊 [Action] 正在流式提取已归档 Excel 数据行..."
                    else -> "⚙️ [Action] 正在调用工具: $funcName..."
                }
                onToolAction(statusText)

                // 产生 Observation
                val observation = toolRegistry.executeTool(funcName, funcArgs)

                // 将 Observation 回填进入上下文，作为下一次 Thought 的决策输入
                messagesArray.put(
                    JSONObject().apply {
                        put("role", "tool")
                        put("tool_call_id", callId)
                        put("name", funcName)
                        put("content", observation)
                    }
                )
            }
        }

        // 达到最大步数安全上限时完结
        onComplete(fullAccumulatedContent.toString(), fullAccumulatedReasoning.toString())
    }
}
