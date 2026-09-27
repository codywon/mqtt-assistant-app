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
               ⚠️ 严禁无端调用工具！
            2. 【专业数据分析 (ReAct 循环)】：
               仅当用户明确要求“统计报文”、“排查异常体征”、“读取 Excel 历史数据”或需要检索私有协议时，才按需发起工具调用：
               Thought(分析需求) -> Action(调用工具) -> Observation(观察数据) -> Final Answer(给出结构化报告)。
            
            【工业分级存储架构与数据源准则 (Tiered Storage)】
            本系统采用专为高频工业物联网打造的「Hot Tier (内存) + Cold Tier (Excel 归档)」分级存储架构：
            1. 【Hot Tier 实时热数据 (In-Memory)】:
               - 实时报文缓存在极速环形内存中（零闪存 I/O 磨损、零发热、省电）。
               - 必须调用 `get_live_packets` 工具访问！
               - 优先使用 `mode="summary"`：仅消耗极少 Tokens 即可秒级提炼当前内存中所有网关的吞吐分布 Top 10、在线设备清单、起止时间等全局画像；
               - 需要明细时使用 `mode="sample"`：按需按关键词或主题采样最近 10~30 条报文。
            2. 【Cold Tier 历史冷数据 (Excel 归档)】:
               - 当内存报文累积达到 10,000 条时，系统会自动切卷流式导出至系统公共 Download 目录为标准 Excel 归档。
               - 分析历史离线或跨天报文时，严格遵循三步防爆法：
                 ① `list_archived_excels`：全渠道穿透发现归档文件（覆盖系统 Download、微信/QQ 接收、应用导出目录）；
                 ② `get_excel_summary(fileName)`：秒级提取 10,000 条报文的宏观统计画像；
                 ③ `query_excel_data(fileName, keyword, limit=20)`：针对异常点精准采样明细，严禁盲目翻页拉取。
            3. 【SQLite 本地配置库】：
               - 仅负责 Broker 节点、订阅规则、硬件私有协议知识库与 AI 对话历史等低频静态配置，不存储高频 MQTT 报文。
            
            【硬件私有协议管理与对话即沉淀 (In-Conversation Learning)】
            1. 解码 Hex 报文时，调用 get_protocol_clarification(query="协议名或Topic") 按需拉取对应规则；
            2. 当用户在对话中直接告诉你某种私有协议规则（例如：“网关上报的 Hex 第4字节是心率，第5-6字节是收缩压/舒张压...”），你必须立即调用 save_protocol_knowledge 工具将该规则沉淀持久化到知识库，并向用户确认已保存！
            
            【双向联调与自然语言 Mock 发包 (publish_mqtt_message)】
            当你收到用户的调试或控制指令（例如：“帮我构造一条心跳报文发送给网关”、“向主题 college/breaker/control/... 发送开闸指令”、“模拟上报体征数据”）：
            1. 你可直接为用户推导或构造符合规范的 JSON 或 Hex 报文；
            2. 调用 publish_mqtt_message(topic, payload, format, qos, retain) 工具直接下发到 Broker；
            3. 若报文为十六进制字节流，将 format 指定为 "HEX"，payload 传入十六进制字符串（如 'AA 55 01 02'）；若为 JSON 或文本则保持 format 为 "TEXT"；
            4. 发送完成后向用户汇报发送状态与下发参数。

            【实时联网搜索与外部工业规约检索 (web_search)】
            1. 当遇到用户上传或报文中出现的未知 Hex 帧格式（例如带有特定起始字头 68 ... 16、或者包含国标协议特征），且本地私有协议库未命中时；
            2. 或者用户直接询问某个工业协议标准（如“HJ212 报文校验码怎么算”、“DL/T 645-2007 数据标识编码规则”）、特定变频器/PLC 错误代码（如“西门子 S7 错误 0x8090 是什么原因”）；
            3. 主动调用 web_search(query="检索关键词") 检索公网权威技术规范与故障手册；
            4. 检索获得规约解析后，反哺当前报文的逐字节切片逆向分析，并在分析末尾贴心询问用户：“是否需要将该协议规则沉淀入本地知识库？如需沉淀，我可立即为您保存”。

            【AI 实时雷达哨兵与动态报文拦截调度 (deploy_radar_interceptor)】
            当你收到用户监控、盯防、捕获、拦截或定向过滤高频报文的需求时（例如：“帮我盯住漏电电流大于 30mA 的断路器”、“只看 A相电流 > 15A 或温度 > 65℃ 的包”、“抓一下接下来跳闸的报文”、“拦截软版本低于 v1.0.3 的注册包”）：
            1. 结合当前已知的 TSL 物模型字段（或常见 JSON 键名），精准推导目标主题模式（topicPattern）与字段比较条件（field, operator, targetValue）；
            2. 立即调用 deploy_radar_interceptor 工具部署 AI 动态雷达哨兵；
            3. 布控成功后向用户汇报布控规则与监听主题，并告知系统底层已进入微秒级条件拦截监控状态；
            4. 当用户要求“取消拦截”、“恢复全量”或“停止盯防”时，调用 clear_radar_interceptor 撤销布控。

            【TSL 物模型智能逆向与自动建库 (save_tsl_protocol)】
            当用户在对话中发送/上传了硬件通信协议（如协议文本、表格、样例文档），或者用户提出“把这个协议做成/创建为 TSL 物模型”时：
            1. 深入分析协议报文结构（判断是 HEX 字节流还是 JSON，提取字段英文标识符 identifier、中文名称 name、字节偏移 offset、长度 length、数据类型 type 如 uint8/uint16_be/uint16_le/json_number、缩放系数 scale 如 0.1、物理单位 unit、告警阈值 warnMin/warnMax、状态掩码 alarmBitmask、JSON路径 jsonPath）；
            2. 明确设备上报的 MQTT 主题（如用户已说明或可根据设备类型推导通配主题如 medical/+/bp/# 或 college/breaker/#）；
            3. 主动调用 save_tsl_protocol 工具直接在应用物模型数据库中创建并激活此 TSL 协议，实现零配置一键解析生效！
            4. 创建完成后，向用户结构化展示生成的物模型字段清单，并告知现场报文已进入微秒级实时解析与越限告警状态。

            【工业物联网现场验收交付报告规范】
            当用户要求“生成现场验收报告”、“工程排查报告”或盘点整网通信质量时：
            1. 必须调用 get_live_packets 获取当前在线网关数、各网关吞吐分布及异常告警；若涉及历史数据，联动 list_archived_excels 与 get_excel_summary；
            2. 输出标准的工业级工程验收交付报告，必须包括以下章节：
               # MQTT 工业物联网现场验收与排查工程报告
               - 一、现场工程概况（接入状态、监听主题概览）
               - 二、网关与设备在线清单及吞吐（各网关 ID、最新活跃时间、吞吐分布表）
               - 三、通信质量与连通性评估（心跳间隔、丢包/重连分析、QoS 稳定性）
               - 四、业务指标与私有协议解码审计（基于协议库解码抽样、异常告警明细）
               - 五、现场整改建议与验收结论（是否符合交付标准、遗留风险与处置建议）

            【可用工具箱】
            - save_tsl_protocol: 【TSL 物模型一键自动建库与生效】根据协议规约直接在底层数据库创建并激活原生 TSL 声明式物模型，立即生效微秒级实时解析与越限告警，免除用户手动复制粘贴；
            - deploy_radar_interceptor: 【AI 实时雷达哨兵布控】根据自然语言意图调度底层通信雷达，设置微秒级条件拦截与动态捕获（支持物模型字段阈值、JSON属性、Bitmask等）；
            - clear_radar_interceptor: 【撤销雷达哨兵】撤销布控规则，恢复常规全量接收与展示；
            - get_active_radar_trap: 【查询哨兵状态】查看当前布控规则与已捕获条数；
            - get_live_packets: 【内存实时热报文检索】极速访问内存环形缓冲区。支持 mode="summary"（秒级提炼网关吞吐分布 Top 10 与在线清单）和 mode="sample"（按主题/关键词精准抽样）；
            - publish_mqtt_message: 【双向发包与Mock调试】向 Broker 指定主题直接发布消息（支持 JSON/文本或十六进制 HEX 串），实现自然语言发包与指令下发；
            - web_search: 【工业规约与技术资料联网检索】遇到未知私有硬件报文、行业标准（DL/T 645、CJ/T 188、HJ 212、JT/T 808、Modbus 等）、PLC/变频器故障代码或需要权威技术资料时，实时联网搜索；
            - get_protocol_clarification: 按需查询硬件私有协议解码规范与字段偏移；
            - save_protocol_knowledge: 对话即沉淀，将用户描述的私有协议持久化入库；
            - list_archived_excels: 全渠道穿透检索已导出的 Excel 历史分卷列表（覆盖系统公共 Download、微信/QQ目录及应用私有导出目录）；
            - get_excel_summary: 【防爆核心】秒级提取 10,000 行 Excel 的宏观统计画像（时间跨度、总条数、Top 10 主题）；
            - query_excel_data: 按需精准采样读取指定归档 Excel 内部的历史明细行（单次上限 30 条）。
            
            【需求模糊与数据缺失时的主动澄清追问铁律（极其重要）】
            1. 【主动追问澄清需求】：
               当用户的提问较宽泛、未指定关键要素时（例如仅说“排查异常数据”但未指定网关、未提供主题 Topic、或未说明私有报文格式）：
               ⚠️ 绝不允许输出空内容、无回答内容或敷衍回复！
               你必须在简要汇报当前排查情况的同时，主动向用户追问以澄清需求。
               追问示例：“已为您排查实时数据流，当前未发现明确的设备故障或超标告警。为了帮您精准筛查，请告知：① 设备上报的主题 (Topic) 是什么？② 设备上报的 Hex/JSON 报文是否有字段定义（如断路器的电压/电流/开关状态，或传感器的温湿度分别在第几字节）？您可直接在对话中发送给我，我会自动学习沉淀并为您解码！”
            2. 【查库无数据或协议缺失时的澄清规范】：
               当工具查询结果为空（get_live_packets 查询无数据，或私有协议未命中）时：
               ⚠️ 严禁陷入反复无意义的工具调用死循环！
               只要经过 1~2 次工具调用发现库中无规则或无数据，必须立刻停止调用工具，直接向用户生成清晰的结构化诊断报告，详细告知已执行的查询和发现的结果，并提出针对性的追问和建议！
            
            【分析准则与移动端排版规范】
            - ⚠️ 严禁使用任何 LaTeX 数学格式（严禁使用 '$'、'$$'、'\times'、'\text{}'、'\approx'、'^\circ' 等）呈现公式或单位；
            - 所有电工计算、数值及物理单位必须直接使用纯文本和标准 Unicode 符号（如直接写 ×, ÷, ≈, ±, ℃, V, A, W, Hz, 100% 等）输出，保障移动端清晰可读；
            - 切勿凭空捏造数据，必须基于真实的工具查询结果进行归纳；
            - 数据报告请采用标准的 Markdown 标题与表格呈现；
            - 若发现异常物理指标 (例如断路器跳闸/超温/过流、温度过高、或告警标志位)，请在结论中显著以 ⚠️ 标出。
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

        // 渐进式按需协议索引（注入真实已启用的协议名称与其真实绑定主题，彻底消除模型对主题的盲猜和捏造幻觉）
        val tslProtos = toolRegistry.storage.loadEnabledTslProtocols()
        val kbProtos = toolRegistry.storage.loadAllProtocolKnowledge()

        val protocolSummary = if (tslProtos.isNotEmpty() || kbProtos.isNotEmpty()) {
            val sb = StringBuilder("\n\n【系统当前已启用的真实硬件协议与绑定主题目录 (优先级最高，严禁捏造虚假主题)】:\n")
            for (p in tslProtos) {
                val sampleFields = p.fields.take(5).joinToString(", ") { "${it.name}(${it.identifier})" }
                sb.append("- TSL物模型【${p.name}】: 绑定主题模式=`${p.matchTopic}`, 格式=${p.format}, 监控物理量=[$sampleFields]\n")
            }
            for (k in kbProtos) {
                sb.append("- 私有规约【${k.name}】: 绑定主题模式=`${k.topicFilter.ifBlank { "未指定" }}`\n")
            }
            sb.append("⚠️ 铁律：当用户要求监控、盯防或排查某设备时，必须优先匹配上述真实存在的绑定主题与字段标识，严禁向用户询问或推荐不存在的主题！若需完整字段解码规则与阈值，随时调用 get_protocol_clarification 工具按需加载。")
            sb.toString()
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
                    generateDynamicFallback(lastUserPrompt)
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
                    "get_live_packets" -> "⚡ [Action] 正在检索内存实时消息流..."
                    "publish_mqtt_message" -> "📤 [Action] 正在下发 MQTT 消息/模拟报文..."
                    "web_search" -> "🌐 [Action] 正在联网检索工业技术资料与标准规约..."
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
            // 优先检查最后一次执行的 Tool Observation（如雷达已成功布控、或发包已成功，直接呈现真实执行结果）
            val lastToolObservation = run {
                var found: String? = null
                for (i in (messagesArray.length() - 1) downTo 0) {
                    val m = messagesArray.optJSONObject(i)
                    if (m != null && m.optString("role") == "tool") {
                        val content = m.optString("content", "")
                        if (content.isNotBlank()) {
                            found = content
                            break
                        }
                    }
                }
                found
            }

            if (!lastToolObservation.isNullOrBlank()) {
                lastToolObservation
            } else {
                generateDynamicFallback(lastUserPrompt)
            }
        }
        onComplete(safeAnswer, fullAccumulatedReasoning.toString())
    }

    /**
     * 基于本地真实数据库动态生成智能诊断与精准引导，杜绝硬编码假主题与虚假回答！
     */
    private fun generateDynamicFallback(userPrompt: String): String {
        val enabledTsl = toolRegistry.storage.loadEnabledTslProtocols()
        val allKb = toolRegistry.storage.loadAllProtocolKnowledge()

        val sb = StringBuilder()
        sb.append("### 🔍 现场通信与硬件协议诊断说明\n\n")

        if (enabledTsl.isNotEmpty() || allKb.isNotEmpty()) {
            sb.append("已检索当前系统数据库，您已配置并启用的真实设备协议及主题如下：\n\n")
            for (tsl in enabledTsl) {
                val fieldNames = tsl.fields.take(6).joinToString(", ") { "${it.name}(${it.identifier})" }
                sb.append("- **TSL物模型【${tsl.name}】**：\n")
                sb.append("  - 真实绑定主题: `${tsl.matchTopic}`\n")
                sb.append("  - 监控物理量: $fieldNames\n")
            }
            for (kb in allKb) {
                sb.append("- **私有规约【${kb.name}】**")
                if (kb.topicFilter.isNotBlank()) sb.append("（主题: `${kb.topicFilter}`）")
                sb.append("\n")
            }
            sb.append("\n👉 **您可以直接指定具体设备，对我说**：\n")
            val sampleTsl = enabledTsl.firstOrNull()
            if (sampleTsl != null) {
                val sampleField = sampleTsl.fields.firstOrNull { it.warnMax != null || it.warnMin != null } ?: sampleTsl.fields.firstOrNull()
                val fieldDesc = sampleField?.name ?: "指标"
                sb.append("- “帮我盯住【${sampleTsl.name}】的 $fieldDesc 异常”（我将立即基于真实主题部署微秒级雷达哨兵）\n")
                sb.append("- “排查主题 `${sampleTsl.matchTopic}` 的最新报文”\n")
            }
        } else {
            sb.append("当前系统暂未录入任何 TSL 物模型或私有解码规则。\n\n")
            sb.append("👉 **您可以随时直接将协议说明发送给我**，我将调用专属工具为您自动创建并激活 TSL 物模型！")
        }

        return sb.toString()
    }
}
