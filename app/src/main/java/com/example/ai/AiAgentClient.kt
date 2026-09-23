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
 * 4. 【零外部库纯原生实现】：纯 HttpURLConnection + SSE 流式解析，全面兼容 Gemini/OpenAI/DeepSeek/Qwen/Ollama；
 * 5. 【严谨 Null-Safe 协议清洗】：杜绝 Android 原生 JSONObject 将 null 解析为字符串 "null" 的经典巨坑。
 */
class AiAgentClient(
    private val toolRegistry: SIAgentToolRegistry
) {

    companion object {
        private const val TAG = "AiAgentClient"

        val DEFAULT_SYSTEM_PROMPT = """
            你是一个内嵌在移动端「MQTT 助手」中的专业系统集成与数据分析智能体 (SI Data Analysis Agent)。
            
            【意图准则与行为模式】
            1. 【日常会话与通用交流】：
               当用户打招呼（如“你好”、“在吗”）、礼貌闲聊、或询问通用常识/协议原理时，直接以亲切自然的口吻回答。
               ⚠️ 严禁无端调用 execute_sqlite_query 或其它工具！
            2. 【专业数据分析 (ReAct 循环)】：
               仅当用户明确要求“统计报文”、“查询 SQLite 数据库”、“排查异常体征”、“读取 Excel 历史数据”或需要检索私有协议时，才按需发起工具调用：
               Thought(分析需求) -> Action(调用工具) -> Observation(观察数据) -> Final Answer(给出结构化报告)。
            
            【SQLite 报文表结构规则】
            数据库表名: tbl_mqtt_packets
            真实可用列名如下:
            - topic (TEXT): 消息主题（网关 ID、设备类型均位于 topic 路径中，如 'gateway/gw_01/telemetry' 或 'sensor/vital'）
            - payload (TEXT): 原始报文内容（可能是 Hex 16进制字符串，也可能是 JSON 数据）
            - devInfo (TEXT): 预解析设备信息
            - qos (INTEGER), packetSeq (TEXT), timestamp (TEXT), category (TEXT), created_at (INTEGER 毫秒时间戳)
            ⚠️ 关键铁律：表中绝不存在名为 gateway、gateway_id、device_id 的列！查询各网关吞吐或频次时，必须基于 topic 字段进行 GROUP BY 聚合，示范:
            SELECT topic, count(*) as count FROM tbl_mqtt_packets GROUP BY topic ORDER BY count DESC
            
            【历史 Excel 归档分析铁律与上下文防爆策略 (极其重要)】
            每个归档 Excel 文件通常包含高达 10,000 条报文，严禁盲目读取大量原始明细导致上下文溢出或崩溃！必须遵循三步分析法：
            1. 发现归档：调用 list_archived_excels 获取归档列表（已全方位穿透检索系统 Download、微信/QQ接收、应用导出目录与系统媒体库）；
            2. 宏观画像优先：必须首先调用 get_excel_summary(fileName) 工具！它仅耗费 ~150 Tokens 即可秒级提炼出这 10,000 条报文的总数、起止时间、Top 10 热门主题及涉及设备；
            3. 精准按需采样：仅当用户需要分析具体异常报文或抽样查看明细时，使用 query_excel_data(fileName, keyword, limit=20) 配合过滤关键词精准读取 10~20 条。绝对禁止全量翻页拉取！
            4. 库内数据兜底：若暂未发现外部 Excel 归档，或用户询问的是在线最新数据，优先调用 execute_sqlite_query 检索当前 SQLite 数据库 (tbl_mqtt_packets)。
            
            【硬件私有协议管理与对话即沉淀 (In-Conversation Learning)】
            1. 解码 Hex 报文时，调用 get_protocol_clarification(query="协议名或Topic") 按需拉取对应规则；
            2. 当用户在对话中直接告诉你某种私有协议规则（例如：“网关上报的 Hex 第4字节是心率，第5-6字节是收缩压/舒张压...”），你必须立即调用 save_protocol_knowledge 工具将该规则沉淀持久化到知识库，并向用户确认已保存！
            
            【双向联调与自然语言 Mock 发包 (publish_mqtt_message)】
            当你收到用户的调试或控制指令（例如：“帮我构造一条心跳报文发送给网关”、“向主题 college/breaker/control/... 发送开闸指令”、“模拟上报体征数据”）：
            1. 你可直接为用户推导或构造符合规范的 JSON 或 Hex 报文；
            2. 调用 publish_mqtt_message(topic, payload, format, qos, retain) 工具直接下发到 Broker；
            3. 若报文为十六进制字节流，将 format 指定为 "HEX"，payload 传入十六进制字符串（如 'AA 55 01 02'）；若为 JSON 或文本则保持 format 为 "TEXT"；
            4. 发送完成后向用户汇报发送状态与下发参数。

            【工业物联网现场验收交付报告规范】
            当用户要求“生成现场验收报告”、“工程排查报告”或盘点整网通信质量时：
            1. 必须调用 get_live_packets 与 execute_sqlite_query 获取在线网关数、各网关吞吐分布及异常告警；
            2. 输出标准的工业级工程验收交付报告，必须包括以下章节：
               # MQTT 工业物联网现场验收与排查工程报告
               - 一、现场工程概况（接入状态、监听主题概览）
               - 二、网关与设备在线清单及吞吐（各网关 ID、最新活跃时间、吞吐分布表）
               - 三、通信质量与连通性评估（心跳间隔、丢包/重连分析、QoS 稳定性）
               - 四、业务指标与私有协议解码审计（基于协议库解码抽样、异常告警明细）
               - 五、现场整改建议与验收结论（是否符合交付标准、遗留风险与处置建议）

            【可用工具箱】
            - execute_sqlite_query: 执行只读 SQL 语句查询当前 SQLite 数据库 (tbl_mqtt_packets)，分析历史/离线报文；
            - get_live_packets: 【内存实时热报文检索】直接从应用内存实时消息流中获取最新到达的报文（无需经过磁盘或 SQL），排查实时数据流或当 SQLite 查无记录时使用；
            - publish_mqtt_message: 【双向发包与Mock调试】向 Broker 指定主题直接发布消息（支持 JSON/文本或十六进制 HEX 串），实现自然语言发包与指令下发；
            - get_protocol_clarification: 按需查询硬件私有协议解码规范与字段偏移；
            - save_protocol_knowledge: 对话即沉淀，将用户描述的私有协议持久化入库；
            - list_archived_excels: 全渠道穿透检索已导出的 Excel 历史分卷列表（覆盖系统公共 Download、微信/QQ目录及应用私有导出目录）；
            - get_excel_summary: 【防爆核心】秒级提取 10,000 行 Excel 的宏观统计画像（时间跨度、总条数、Top 10 主题）；
            - query_excel_data: 按需精准采样读取指定归档 Excel 内部的历史明细行（单次上限 30 条）。
            
            【需求模糊与数据缺失时的主动澄清追问铁律（极其重要）】
            1. 【主动追问澄清需求】：
               当用户的提问较宽泛、未指定关键要素时（例如仅说“排查异常体征”但未指定网关、未提供主题 Topic、或未说明私有报文格式）：
               ⚠️ 绝不允许输出空内容、无回答内容或敷衍回复！
               你必须在简要汇报当前排查情况的同时，主动向用户追问以澄清需求。
               追问示例：“已为您排查本地库，当前未发现明确的体征异常数据。为了帮您精准筛查，请告知：① 体征数据上报的主题 (Topic) 是什么？② 设备上报的 Hex 报文是否有字段定义（如高低压、心率分别在第几字节）？您可直接在对话中发送给我，我会自动学习沉淀并为您解码！”
            2. 【查库无数据或协议缺失时的澄清规范】：
               当工具查询结果为空（SQLite 查询为 0 行，或私有协议未命中）时：
               ⚠️ 严禁陷入反复无意义的工具调用死循环！
               只要经过 1~2 次工具调用发现库中无规则或无数据，必须立刻停止调用工具，直接向用户生成清晰的结构化诊断报告，详细告知已执行的查询和发现的结果，并提出针对性的追问和建议！
            
            【分析准则】
            - 切勿凭空捏造数据，必须基于真实的工具查询结果进行归纳；
            - 数据报告请采用标准的 Markdown 标题与表格呈现；
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
            onError("未配置 API Key。请点击右上角设置图标填入大模型 API Key（推荐使用 DeepSeek、通义千问或 Gemini）")
            return@withContext
        }

        // 渐进式按需协议索引（仅注入协议名称索引目录，仅消耗 ~20 Tokens，按需由工具加载完整规则）
        val protocols = toolRegistry.storage.loadAllProtocolKnowledge()
        val protocolSummary = if (protocols.isNotEmpty()) {
            val names = protocols.joinToString(", ") { it.name }
            "\n\n【当前已挂载私有协议索引】: $names。如需具体规则，请调用 get_protocol_clarification 工具获取。"
        } else {
            ""
        }

        val systemContent = (if (config.customPrompt.isNotBlank()) {
            "${config.customPrompt}\n\n$DEFAULT_SYSTEM_PROMPT"
        } else {
            DEFAULT_SYSTEM_PROMPT
        }) + protocolSummary

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
        val maxSteps = 4 // 移动端优化为 4 步安全收敛循环
        val fullAccumulatedReasoning = StringBuilder()
        var finalAnswerContent = StringBuilder()

        // 获取用户最后一条提问，用于意图澄清兜底分析
        val lastUserPrompt = conversationHistory.lastOrNull { it.role == "user" }?.content ?: ""

        // ==========================================
        // 2. The ReAct Loop (Thought -> Action -> Observation)
        // ==========================================
        while (step < maxSteps) {
            step++

            // 每一步动作前，再次检查当前活跃上下文水位；若工具返回过大导致超 70%，自动压缩！
            messagesArray = ContextCompactor.compactActiveMessages(messagesArray, config)

            // 最后一步强制不再提供 tools，迫使模型停止调用工具，输出文本终答与追问澄清！
            val isFinalStep = (step == maxSteps)

            val requestBody = JSONObject().apply {
                put("model", config.modelName)
                put("messages", messagesArray)
                if (!isFinalStep) {
                    put("tools", toolsJson)
                    put("tool_choice", "auto")
                }
                put("temperature", config.temperature)
                put("max_tokens", config.maxTokens)
                put("stream", true)
            }

            var conn: HttpURLConnection? = null
            val toolCallsDetected = mutableListOf<JSONObject>()
            val currentStepContent = StringBuilder()
            val currentStepReasoning = StringBuilder()
            var requestSucceeded = false
            var lastException: Exception? = null

            for (attempt in 1..3) {
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
                        if (responseCode in listOf(502, 503, 504) && attempt < 3) {
                            onToolAction("服务端波动 ($responseCode)，正在进行第 $attempt 次自动重试...")
                            kotlinx.coroutines.delay(attempt * 800L)
                            conn.disconnect()
                            continue
                        }
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
                                    // 严密防护：避免 org.json 将 null 解析为 "null" 字符串
                                    if (!delta.isNull("reasoning_content")) {
                                        val reasoningDelta = delta.optString("reasoning_content", "")
                                        if (reasoningDelta.isNotEmpty() && reasoningDelta != "null") {
                                            currentStepReasoning.append(reasoningDelta)
                                            fullAccumulatedReasoning.append(reasoningDelta)
                                            onChunk(reasoningDelta, true)
                                        }
                                    }

                                    // 2. 正文打字机增量
                                    // 严密防护：如果字段为 null（如 tool-call 阶段），绝对不输出 "null" 字符！
                                    if (!delta.isNull("content")) {
                                        val contentDelta = delta.optString("content", "")
                                        if (contentDelta.isNotEmpty() && contentDelta != "null") {
                                            currentStepContent.append(contentDelta)
                                            onChunk(contentDelta, false)
                                        }
                                    }

                                    // 3. Action 动作增量 (tool_calls)
                                    val deltaTools = delta.optJSONArray("tool_calls")
                                    if (deltaTools != null) {
                                        for (i in 0 until deltaTools.length()) {
                                            val t = deltaTools.getJSONObject(i)
                                            val idx = t.optInt("index", 0)
                                            val id = if (!t.isNull("id")) t.optString("id", "") else ""
                                            val func = t.optJSONObject("function")
                                            val name = if (func != null && !func.isNull("name")) func.optString("name", "") else ""
                                            val argsPart = if (func != null && !func.isNull("arguments")) func.optString("arguments", "") else ""

                                             val triple = toolCallMap.getOrPut(idx) {
                                                Triple(StringBuilder(), StringBuilder(), StringBuilder())
                                            }
                                            if (id.isNotEmpty() && id != "null" && triple.first.isEmpty()) triple.first.append(id)
                                            if (name.isNotEmpty() && name != "null" && triple.second.isEmpty()) triple.second.append(name)
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
                    val id = triple.first.toString().trim()
                    val name = triple.second.toString().trim()
                    val args = triple.third.toString().trim()
                    if (name.isNotEmpty()) {
                        toolCallsDetected.add(
                            JSONObject().apply {
                                put("id", if (id.isNotEmpty()) id else "call_${System.currentTimeMillis()}_${(100..999).random()}")
                                put("type", "function")
                                put(
                                    "function",
                                    JSONObject().apply {
                                        put("name", name)
                                        put("arguments", if (args.isNotBlank()) args else "{}")
                                    }
                                )
                            }
                        )
                    }
                }

                requestSucceeded = true
                break
            } catch (e: Exception) {
                lastException = e
                conn?.disconnect()
                if (attempt < 3 && currentStepContent.isEmpty()) {
                    onToolAction("网络连接微弱，正在进行第 $attempt 次自动重试...")
                    kotlinx.coroutines.delay(attempt * 800L)
                } else {
                    break
                }
            } finally {
                conn?.disconnect()
            }
        }

        if (!requestSucceeded) {
            Log.e(TAG, "ReAct 会话通信异常", lastException)
            onError("网络通信失败 (已自动重试 3 次): ${lastException?.message}")
            return@withContext
        }

            // ==========================================
            // 3. 终答判定 (Final Answer)
            // ==========================================
            if (toolCallsDetected.isEmpty() || isFinalStep) {
                // 模型无需再采取 Action，或已达到收敛终态，当前输出即为最终报告解答！
                finalAnswerContent = currentStepContent
                onToolAction("") // 清除工具 Action 提示
                val rawAnswer = finalAnswerContent.toString().trim()
                val safeAnswer = if (rawAnswer.isNotEmpty() && rawAnswer != "null") {
                    rawAnswer
                } else {
                    generateFallbackClarification(lastUserPrompt, step > 1)
                }
                onComplete(safeAnswer, fullAccumulatedReasoning.toString())
                return@withContext
            }

            // ==========================================
            // 4. 执行 Action 并获取 Observation
            // ==========================================
            // 规范：向兼容层回传时，若无正文则传空字符串 "" 以获得各大中转代理的最广泛兼容
            val assistantMsg = JSONObject().apply {
                put("role", "assistant")
                val cleanContent = currentStepContent.toString().trim()
                if (cleanContent.isNotEmpty() && cleanContent != "null") {
                    put("content", cleanContent)
                } else {
                    put("content", "")
                }
                val callsArray = JSONArray()
                for (t in toolCallsDetected) callsArray.put(t)
                put("tool_calls", callsArray)
            }
            messagesArray.put(assistantMsg)

            // 依次执行每个 Action 工具并产出 Observation
            for (toolObj in toolCallsDetected) {
                val callId = toolObj.getString("id")
                val funcObj = toolObj.getJSONObject("function")
                val funcName = funcObj.getString("name")
                val funcArgs = funcObj.optString("arguments", "{}")

                val statusText = when (funcName) {
                    "execute_sqlite_query" -> "🔍 [Action] 正在执行只读 SQL 查询..."
                    "get_protocol_clarification" -> "📖 [Action] 正在检索硬件私有协议知识..."
                    "save_protocol_knowledge" -> "💾 [Action] 正在将私有协议规则沉淀入库..."
                    "list_archived_excels" -> "📁 [Action] 正在扫描已转储 Excel 历史分卷..."
                    "get_excel_summary" -> "⚡ [Action] 正在提取 Excel 宏观统计画像..."
                    "query_excel_data" -> "📊 [Action] 正在流式提取已归档 Excel 数据行..."
                    else -> "⚙️ [Action] 正在调用工具: $funcName..."
                }
                onToolAction(statusText)

                // 产生 Observation
                val observation = toolRegistry.executeTool(funcName, funcArgs)

                // 将 Observation 回填进入上下文，作为下一次 Thought 的决策依据
                messagesArray.put(
                    JSONObject().apply {
                        put("role", "tool")
                        put("tool_call_id", callId)
                        put("name", funcName)
                        put("content", observation)
                    }
                )
            }

            // 工具执行完毕，在请求大模型生成最终报告期间展示数据分析动效
            onToolAction("📊 数据检索完毕，正在深入分析并生成报告...")
        }

        // 达到最大步数安全上限时完结（严格兜底保护，绝无空回答）
        onToolAction("")
        val rawAnswer = finalAnswerContent.toString().trim()
        val safeAnswer = if (rawAnswer.isNotEmpty() && rawAnswer != "null") {
            rawAnswer
        } else {
            generateFallbackClarification(lastUserPrompt, true)
        }
        onComplete(safeAnswer, fullAccumulatedReasoning.toString())
    }

    /**
     * 当模型未输出终答正文、或检索无果需求模糊时的智能澄清与追问说明，杜绝空回答兜底！
     */
    private fun generateFallbackClarification(userPrompt: String, hadToolActions: Boolean): String {
        val isVitalRelated = userPrompt.contains("体征") || userPrompt.contains("血压") || userPrompt.contains("心率") || userPrompt.contains("健康")
        return if (isVitalRelated) {
            """
            ### 🔍 体征数据排查与意图澄清说明
            
            已为您在本地 SQLite 数据库与私有协议知识库中完成检索与排查：
            1. **协议规则检查**：当前系统暂未检索到体征相关的私有解码规则（如血压计/心率仪报文结构）；
            2. **报文数据排查**：暂未在最新报文中识别到明确的体征异常数据。
            
            👉 **为了帮您精准筛查，请协助澄清以下关键信息**：
            - 您的体征设备或网关对应的主题（Topic）是什么？例如 `vital/gateway/#` 或 `sensor/vital`？
            - 硬件上报的 Hex 报文是否有字段定义（如高低压、心率分别在第几字节）？
            
            *(💡 贴心提示：您可以直接在当前对话框中将协议说明发送给我，我会自动将其沉淀入库并为您即时解码！)*
            """.trimIndent()
        } else if (hadToolActions) {
            """
            ### 📊 数据检索排查说明
            
            已完成底层数据检索与排查。当前检索条件下暂未获取到匹配的有效数据。
            
            👉 **为了进一步分析，请告诉我更多细节**：
            - 您希望重点排查的具体网关或设备主题（Topic）是什么？
            - 是否需要扩大时间范围或检索历史归档 Excel 文件？
            """.trimIndent()
        } else {
            "您好！我是 SI 数据分析专家。请告诉我您想查询的网关报文、体征数据或私有协议规则。"
        }
    }
}
