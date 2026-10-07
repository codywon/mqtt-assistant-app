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
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 现场工具调用执行记录（用于 Observation-First 报告自愈与过程追溯）
 */
data class ExecutedToolRecord(
    val step: Int,
    val toolName: String,
    val argumentsJson: String,
    val observation: String,
    val timestamp: String = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
)

/**
 * 生产级工业物联 AI 智能体执行引擎 & 多轨自愈 OpenAI 兼容客户端
 *
 * 核心架构特性与工业级护城河设计（深度对齐 Pi-Agent 架构哲学）：
 * 1. 【三级全自动自愈容错状态机 (Multi-Tier Resilient Engine)】：
 *    - Tier 1: 原生流式 (stream=true, tools=true, tool_choice="auto")
 *    - Tier 2: 纯文本流式 (stream=true, tools=false) -> 自动注入纯文本 ReAct 工具调用引导
 *    - Tier 3: 稳定非流式兜底 (stream=false, tools=false, Accept: application/json) -> 彻底治愈 CLIProxyAPI 流式 Null Bug
 *    - 状态持久化继承：会话级记住可用模式，后续 Step 秒级复用，拒绝重复超时。
 * 2. 【全协议纯文本 ReAct 工具拦截 (4 大格式通吃)】：
 *    - 无论开源模型（DeepSeek-R1、Ollama、Qwen）是否支持 Function Calling，
 *      只要在正文中输出 ```tool:xxx```、```json```、<tool_call> 或 Action/Action Input，均可毫秒级捕获并执行。
 * 3. 【Gemini 原生工具调用智能 ID 回溯与多态参数提取】：
 *    - 解决 Gemini 原生 API 函数调用无独立 name 字段的致命缺陷，通过 ID 智能反推真实工具名；
 *    - 参数多态兼容 arguments, args, parameters, input (兼容 JSONObject, JSONArray, String)。
 * 4. 【免 tools 模式上下文平滑净化 (Context Sanitization)】：
 *    - 彻底消除在无 tools 请求时残留 role: "tool" 导致的 400 报错与断流；
 * 5. 【Observation-First 执行成果优先自愈机制】：
 *    - 全程追踪 executedToolRecords，若模型最后一步未吐文本终答，绝不误判报错抹杀现场成果，优先组织结构化交付报告；
 * 6. 【透明诊断采样镜像 (Diagnostic Mirror)】：
 *    - 放宽至 2000 字符抓取原始服务端响应采样，标注版本与步骤，排障彻底透明。
 */
class AiAgentClient(
    private val toolRegistry: SIAgentToolRegistry
) {

    companion object {
        private const val TAG = "AiAgentClient"

        /**
         * 客户端支持的合法工具名称集合（用于 Gemini ID 回溯与纯文本 ReAct 校验）
         */
        val VALID_TOOL_NAMES = setOf(
            "get_live_packets",
            "get_protocol_clarification",
            "list_archived_excels",
            "get_excel_summary",
            "query_excel_data",
            "save_protocol_knowledge",
            "save_tsl_protocol",
            "set_protocol_key_indicator",
            "publish_mqtt_message",
            "web_search",
            "deploy_radar_interceptor",
            "clear_radar_interceptor",
            "get_active_radar_trap",
            "execute_sqlite_query"
        )

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

            【物模型报文胶囊核心指标动态设定 (set_protocol_key_indicator)】
            消息列表每条报文均带有微型状态药丸（Micro-pill），默认根据工业通用权重自适应展示。
            当用户提出自定义核心指标展示偏好时（例如：“把断路器核心指标改为电量/用电量”、“胶囊显示电流”、“雷达胶囊显示人数/目标数”、“手环显示心率”等）：
            1. 准确识别目标协议（如断路器、雷达、血压计等）及目标物理量指标（如累计电量 energy、负载电流 current、目标人数 target_count、心率 heart_rate 等）；
            2. 立即调用 set_protocol_key_indicator 工具进行精准绑定；
            3. 设置成功后，向用户确认并说明现场卡片胶囊已即刻动态切换！

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
            - set_protocol_key_indicator: 【设定物模型胶囊核心展示指标】当用户要求在消息卡片的小药丸胶囊上展示某个特定指标（如“把断路器胶囊改成显示电量/电流”、“让雷达显示人数”、“手环显示体温”等）时调用此工具；
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

        val TEXT_REACT_PROMPT_ADDITION = """
            
            【纯文本工具调用格式特别说明 (Text ReAct Mode)】
            当原生 Function Calling 不可用时，若需要调用工具，请直接在正文中使用以下任一格式输出工具调用（系统将自动拦截并执行）：
            
            格式 1 (推荐)：
            ```tool:工具名称
            {"参数名": "参数值"}
            ```
            
            格式 2：
            ```json
            {"name": "工具名称", "arguments": {"参数名": "参数值"}}
            ```
            
            格式 3：
            <tool_call>{"name": "工具名称", "arguments": {"参数名": "参数值"}}</tool_call>
            
            格式 4：
            Action: 工具名称
            Action Input: {"参数名": "参数值"}
            
            【工具参数定义速查】
            - get_live_packets: mode ("summary"|"sample"), topicFilter, keyword, limit (int)
            - get_protocol_clarification: query (string)
            - list_archived_excels: {}
            - get_excel_summary: fileName (string)
            - query_excel_data: fileName (string), keyword (string), limit (int), offset (int)
            - save_protocol_knowledge: name, description, topicFilter, sampleHex
            - save_tsl_protocol: name, format ("HEX"|"JSON"), matchTopic, fields (array)
            - set_protocol_key_indicator: protocol, field
            - publish_mqtt_message: topic, payload, format ("TEXT"|"HEX"), qos (0|1|2), retain (bool)
            - web_search: query (string)
            - deploy_radar_interceptor: topicPattern, conditionDesc, action, maxCaptureCount
            - clear_radar_interceptor: {}
            - get_active_radar_trap: {}
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

        val baseSystemContent = (if (config.customPrompt.isNotBlank()) {
            "${config.customPrompt}\n\n$DEFAULT_SYSTEM_PROMPT"
        } else {
            DEFAULT_SYSTEM_PROMPT
        }) + protocolSummary

        // ==========================================
        // 1. 初始化上下文（结合 70% 水位动态自适应压缩）
        // ==========================================
        var messagesArray = ContextCompactor.buildCompactedMessagesJson(
            systemContent = baseSystemContent,
            historyList = conversationHistory,
            config = config
        )

        val toolsJson = SIAgentToolRegistry.getToolDefinitionsJson()

        var step = 0
        val maxSteps = 4 // 移动端优化为 4 步安全收敛循环
        val fullAccumulatedReasoning = StringBuilder()
        val fullAccumulatedContent = StringBuilder()

        // 现场运维与数据分析工具执行全程记录（用于 Observation-First 自愈交付）
        val executedToolRecords = mutableListOf<ExecutedToolRecord>()

        // 会话级持久化参数（一旦某一层判定成功，后续 Step 直接继承，避免每次重复等待超时）
        var useNativeTools = true
        var useStream = true

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

            val stepStartContentLength = fullAccumulatedContent.length
            val stepStartReasoningLength = fullAccumulatedReasoning.length

            val toolCallsDetected = mutableListOf<JSONObject>()
            val currentStepContent = StringBuilder()
            val currentStepReasoning = StringBuilder()
            var stepSucceeded = false
            var lastRawSnippet = ""
            var lastHttpCode = 200

            var tierAttempt = 0
            val maxTierAttempts = 3 // Tier 1 (原生流式) -> Tier 2 (纯文本流式) -> Tier 3 (稳定非流式兜底)

            while (tierAttempt < maxTierAttempts && !stepSucceeded) {
                tierAttempt++

                // 关键防护 1: 降级重试前精确回滚累积缓存，杜绝残缺 chunk 脏数据残留
                fullAccumulatedContent.setLength(stepStartContentLength)
                fullAccumulatedReasoning.setLength(stepStartReasoningLength)
                currentStepContent.setLength(0)
                currentStepReasoning.setLength(0)
                toolCallsDetected.clear()

                val currentNativeTools = useNativeTools
                val currentStream = useStream

                // 若处于纯文本工具模式，为 System Prompt 动态追加文本 ReAct 语法指引
                val effectiveMessages = if (!currentNativeTools || isFinalStep) {
                    sanitizeMessagesForTextMode(messagesArray, isFinalStep)
                } else {
                    messagesArray
                }

                val requestBody = JSONObject().apply {
                    put("model", config.modelName)
                    put("messages", effectiveMessages)
                    if (!isFinalStep && currentNativeTools) {
                        put("tools", toolsJson)
                        put("tool_choice", "auto")
                    }
                    put("temperature", config.temperature)
                    put("max_tokens", config.maxTokens)
                    put("stream", currentStream)
                }

                var conn: HttpURLConnection? = null
                var attemptRawSnippet = StringBuilder()

                try {
                    val baseUrl = config.baseUrl.trim().trimEnd('/')
                    val endpoint = if (baseUrl.endsWith("/v1")) "$baseUrl/chat/completions" else "$baseUrl/v1/chat/completions"
                    val url = URL(endpoint)

                    conn = (url.openConnection() as HttpURLConnection).apply {
                        requestMethod = "POST"
                        connectTimeout = 20_000
                        readTimeout = 120_000
                        doOutput = true
                        doInput = true
                        setRequestProperty("Authorization", "Bearer ${config.apiKey.trim()}")
                        setRequestProperty("Content-Type", "application/json; charset=UTF-8")
                        setRequestProperty("Accept", if (currentStream) "text/event-stream" else "application/json")
                    }

                    conn.outputStream.use { os ->
                        os.write(requestBody.toString().toByteArray(Charsets.UTF_8))
                        os.flush()
                    }

                    val responseCode = conn.responseCode
                    lastHttpCode = responseCode

                    if (responseCode !in 200..299) {
                        val errorBody = conn.errorStream?.bufferedReader()?.use { it.readText() } ?: "HTTP $responseCode"
                        lastRawSnippet = "HTTP $responseCode: $errorBody"
                        Log.w(TAG, "[Tier $tierAttempt 失败] HTTP $responseCode: $errorBody")

                        // 若为 400 且当前开启了 tools，极大概率是模型/代理不支持 tools 参数 -> 降级为纯文本模式
                        if ((responseCode == 400 || responseCode == 422) && currentNativeTools) {
                            Log.i(TAG, "检测到模型或代理不支持原生 tools 参数，原地自适应降级为纯文本 ReAct 模式 (Tier 2)")
                            useNativeTools = false
                            onToolAction("模型不支持原生工具，正在切换至自适应文本双轨模式...")
                            continue
                        }

                        // 服务端瞬时波动 502/503/504
                        if (responseCode in listOf(502, 503, 504) && tierAttempt < maxTierAttempts) {
                            onToolAction("网关波动 ($responseCode)，正在进行自愈重试...")
                            kotlinx.coroutines.delay(1000L)
                            continue
                        }

                        break
                    }

                    // ----------------------------------------------------
                    // 模式 A: 流式模式 (SSE text/event-stream)
                    // ----------------------------------------------------
                    if (currentStream) {
                        val reader = BufferedReader(InputStreamReader(conn.inputStream, Charsets.UTF_8))
                        var line: String? = reader.readLine()
                        val toolCallMap = mutableMapOf<Int, Triple<StringBuilder, StringBuilder, StringBuilder>>()

                        while (line != null) {
                            val trimmed = line.trim()
                            if (attemptRawSnippet.length < 2000 && trimmed.isNotEmpty()) {
                                attemptRawSnippet.append(trimmed).append("\n")
                            }

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
                                        val message = choice.optJSONObject("message")

                                        // 1. 思考链增量 (DeepSeek-R1 / QwQ / Claude Thinking / Gemini)
                                        val reasoningDelta = when {
                                            delta != null -> extractContentText(delta, "reasoning_content")
                                                .ifEmpty { extractContentText(delta, "reasoning") }
                                                .ifEmpty { extractContentText(delta, "thought") }
                                            message != null -> extractContentText(message, "reasoning_content")
                                                .ifEmpty { extractContentText(message, "reasoning") }
                                                .ifEmpty { extractContentText(message, "thought") }
                                            else -> extractContentText(choice, "reasoning_content")
                                                .ifEmpty { extractContentText(choice, "reasoning") }
                                                .ifEmpty { extractContentText(json, "reasoning_content") }
                                        }
                                        if (reasoningDelta.isNotEmpty() && reasoningDelta != "null") {
                                            currentStepReasoning.append(reasoningDelta)
                                            fullAccumulatedReasoning.append(reasoningDelta)
                                            onChunk(reasoningDelta, true)
                                        }

                                        // 2. 正文打字机增量 (递归解包 Content Block 数组，防 optString 吞噬)
                                        val contentDelta = when {
                                            delta != null -> extractContentText(delta, "content")
                                                .ifEmpty { extractContentText(delta, "text") }
                                            message != null -> extractContentText(message, "content")
                                                .ifEmpty { extractContentText(message, "text") }
                                            else -> extractContentText(choice, "text")
                                                .ifEmpty { extractContentText(choice, "content") }
                                                .ifEmpty { extractContentText(json, "text") }
                                                .ifEmpty { extractContentText(json, "response") }
                                        }
                                        if (contentDelta.isNotEmpty() && contentDelta != "null") {
                                            currentStepContent.append(contentDelta)
                                            fullAccumulatedContent.append(contentDelta)
                                            onChunk(contentDelta, false)
                                        }

                                        // 3. 原生 tool_calls 增量 (支持 Gemini 智能 ID 回溯与多态参数提取)
                                        val deltaTools = delta?.optJSONArray("tool_calls") ?: message?.optJSONArray("tool_calls")
                                        if (deltaTools != null) {
                                            for (i in 0 until deltaTools.length()) {
                                                val t = deltaTools.getJSONObject(i)
                                                val idx = t.optInt("index", i)
                                                val id = if (!t.isNull("id")) t.optString("id", "") else ""
                                                val func = t.optJSONObject("function")
                                                
                                                // 智能回溯识别工具名（修复 Gemini 原生 API 函数名编码在 id 中的情况）
                                                var name = if (func != null && !func.isNull("name")) func.optString("name", "") else ""
                                                if (name.isBlank() && id.isNotBlank()) {
                                                    name = extractToolNameFromId(id)
                                                }

                                                val argsPart = extractToolArgumentsString(func, t)

                                                val triple = toolCallMap.getOrPut(idx) {
                                                    Triple(StringBuilder(), StringBuilder(), StringBuilder())
                                                }
                                                if (id.isNotEmpty() && id != "null" && triple.first.isEmpty()) triple.first.append(id)
                                                if (name.isNotEmpty() && name != "null" && triple.second.isEmpty()) triple.second.append(name)
                                                if (argsPart.isNotEmpty()) triple.third.append(argsPart)
                                            }
                                        }
                                    }
                                } catch (_: Exception) {}
                            }
                            line = reader.readLine()
                        }

                        // 汇总流式收集到的原生 tool_calls
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
                    } else {
                        // ----------------------------------------------------
                        // 模式 B: 稳定非流式模式 (单次 POST，Accept: application/json)
                        // 彻底根除 CLIProxyAPI 等反代网关在流式模式下的 Null 缺陷与截断
                        // ----------------------------------------------------
                        val fullResponseText = conn.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
                        attemptRawSnippet.append(fullResponseText.take(2000))

                        val json = JSONObject(fullResponseText)
                        val choices = json.optJSONArray("choices")
                        if (choices != null && choices.length() > 0) {
                            val choice = choices.getJSONObject(0)
                            val message = choice.optJSONObject("message")

                            // 思考链
                            val reasoningText = when {
                                message != null -> extractContentText(message, "reasoning_content")
                                    .ifEmpty { extractContentText(message, "reasoning") }
                                    .ifEmpty { extractContentText(message, "thought") }
                                else -> extractContentText(choice, "reasoning_content")
                                    .ifEmpty { extractContentText(json, "reasoning_content") }
                            }
                            if (reasoningText.isNotBlank()) {
                                currentStepReasoning.append(reasoningText)
                                fullAccumulatedReasoning.append(reasoningText)
                                onChunk(reasoningText, true)
                            }

                            // 正文
                            val contentText = when {
                                message != null -> extractContentText(message, "content")
                                    .ifEmpty { extractContentText(message, "text") }
                                else -> extractContentText(choice, "text")
                                    .ifEmpty { extractContentText(choice, "content") }
                                    .ifEmpty { extractContentText(json, "response") }
                                    .ifEmpty { extractContentText(json, "text") }
                            }
                            if (contentText.isNotBlank()) {
                                currentStepContent.append(contentText)
                                fullAccumulatedContent.append(contentText)
                                onChunk(contentText, false)
                            }

                            // 原生 tool_calls
                            val rawToolCalls = message?.optJSONArray("tool_calls")
                            if (rawToolCalls != null) {
                                for (i in 0 until rawToolCalls.length()) {
                                    val t = rawToolCalls.getJSONObject(i)
                                    val id = t.optString("id", "call_${System.currentTimeMillis()}_$i")
                                    val func = t.optJSONObject("function")
                                    var name = if (func != null && !func.isNull("name")) func.optString("name", "") else ""
                                    if (name.isBlank() && id.isNotBlank()) {
                                        name = extractToolNameFromId(id)
                                    }
                                    val args = extractToolArgumentsString(func, t)

                                    if (name.isNotBlank()) {
                                        toolCallsDetected.add(
                                            JSONObject().apply {
                                                put("id", id)
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
                            }
                        }
                    }

                    // 4. 纯文本 ReAct 工具调用全协议拦截 (双轨兜底保护)
                    // 若原生 tool_calls 未检测到，深度扫描正文中的 4 大文本调用格式
                    if (toolCallsDetected.isEmpty() && currentStepContent.isNotBlank()) {
                        val textualCalls = extractTextualToolCalls(currentStepContent.toString())
                        if (textualCalls.isNotEmpty()) {
                            toolCallsDetected.addAll(textualCalls)
                            Log.i(TAG, "成功从文本内容中捕获 ${textualCalls.size} 个纯文本 ReAct 工具调用！")
                        }
                    }

                    lastRawSnippet = attemptRawSnippet.toString()

                    // 关键判定：严格使用 isNotBlank() 防御空白字符陷阱
                    val hasValidOutput = currentStepContent.isNotBlank() || toolCallsDetected.isNotEmpty()
                    if (hasValidOutput) {
                        stepSucceeded = true
                        // 持久化当前可用参数模式
                        useNativeTools = currentNativeTools
                        useStream = currentStream
                        break
                    } else {
                        Log.w(TAG, "[Tier $tierAttempt 警告] 服务端返回 200 但内容为空白 (0 字节或纯换行)")
                    }

                } catch (e: Exception) {
                    Log.w(TAG, "[Tier $tierAttempt 异常] ${e.message}", e)
                    lastRawSnippet = "异常: ${e.message}\n" + attemptRawSnippet.take(500)
                } finally {
                    conn?.disconnect()
                }

                // 推进状态机自愈降级链路：
                // Tier 1 (流式原生) 失败 -> 尝试 Tier 2 (流式文本无 tools)
                // Tier 2 (流式文本) 失败 -> 尝试 Tier 3 (稳定非流式单次 POST)
                if (!stepSucceeded && tierAttempt < maxTierAttempts) {
                    if (useNativeTools) {
                        Log.i(TAG, "自愈状态机流转: 原生流式失败 -> 降级为纯文本流式模式 (Tier 2)")
                        useNativeTools = false
                        onToolAction("正在尝试自适应文本流式模式...")
                    } else if (useStream) {
                        Log.i(TAG, "自愈状态机流转: 流式管道异常 (疑似 CLIProxyAPI 流式 Null Bug) -> 切换至稳定非流式模式 (Tier 3)")
                        useStream = false
                        onToolAction("流式管道无响应，正在切换至稳定单次请求模式...")
                    }
                    kotlinx.coroutines.delay(600L)
                }
            } // end while(tierAttempt)

            // 关键保护：若当前步三级自愈全部失败，优先检查是否已有历史执行成果
            if (!stepSucceeded) {
                if (executedToolRecords.isNotEmpty()) {
                    Log.i(TAG, "当前步无新响应，但已有 ${executedToolRecords.size} 条现场执行记录，触发 Observation-First 自愈报告！")
                    onToolAction("")
                    val report = buildExecutedToolsReport(executedToolRecords, lastUserPrompt)
                    onComplete(report, fullAccumulatedReasoning.toString())
                    return@withContext
                }

                // 没有任何执行记录，生成透明诊断采样镜像
                val rawSample = if (lastRawSnippet.isNotBlank()) {
                    "\n\n【服务端原始响应采样镜像】:\n```\n${lastRawSnippet.take(800)}\n```"
                } else {
                    "\n\n【响应诊断】: 服务端返回 HTTP $lastHttpCode，但有效数据体为空 (0 字节空响应)。"
                }
                val failMsg = "⚠️ 大模型服务本次未返回有效回答或工具调用。\n已自动尝试 [原生流式] -> [文本流式] -> [稳定非流式] 三级自愈链路。$rawSample\n\n💡 建议排查方向：\n1. 若使用 CLIProxyAPI 或中转网关，请检查控制台是否提示上游账号失效、限流或并发满额；\n2. 检查设置中的模型名称是否与后端网关完全匹配；\n3. 检查 API Key 额度与网络连接状态。"
                onError(failMsg)
                return@withContext
            }

            // ==========================================
            // 3. 终答判定 (Final Answer)
            // ==========================================
            if (toolCallsDetected.isEmpty() || isFinalStep) {
                // 模型无需再采取 Action，或已达到收敛终态，当前输出即为最终报告解答！
                onToolAction("")
                val rawAnswer = currentStepContent.toString().trim()
                val safeAnswer = if (rawAnswer.isNotBlank() && rawAnswer != "null") {
                    rawAnswer
                } else {
                    // Observation-First 运维成果优先自愈：绝不允许抛出错误卡片
                    if (executedToolRecords.isNotEmpty()) {
                        buildExecutedToolsReport(executedToolRecords, lastUserPrompt)
                    } else {
                        generateDynamicFallback(lastUserPrompt)
                    }
                }
                onComplete(safeAnswer, fullAccumulatedReasoning.toString())
                return@withContext
            }

            // ==========================================
            // 4. 执行 Action 并获取 Observation
            // ==========================================
            // 向上下文回传 assistant 消息
            val assistantMsg = JSONObject().apply {
                put("role", "assistant")
                val cleanContent = currentStepContent.toString().trim()
                put("content", if (cleanContent.isNotBlank() && cleanContent != "null") cleanContent else "")
                if (useNativeTools) {
                    val callsArray = JSONArray()
                    for (t in toolCallsDetected) callsArray.put(t)
                    put("tool_calls", callsArray)
                }
            }
            messagesArray.put(assistantMsg)

            // 依次执行每个 Action 工具并产出 Observation
            for (toolObj in toolCallsDetected) {
                val callId = toolObj.optString("id", "call_${System.currentTimeMillis()}")
                val funcObj = toolObj.optJSONObject("function")
                val funcName = funcObj?.optString("name", "") ?: toolObj.optString("name", "")
                val funcArgs = funcObj?.optString("arguments", "{}") ?: "{}"

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
                    "save_tsl_protocol" -> "🛠️ [Action] 正在创建并激活 TSL 物模型..."
                    "set_protocol_key_indicator" -> "🎯 [Action] 正在动态绑定胶囊核心展示指标..."
                    "deploy_radar_interceptor" -> "📡 [Action] 正在部署 AI 实时雷达哨兵..."
                    "clear_radar_interceptor" -> "🛑 [Action] 正在撤销雷达哨兵布控..."
                    "get_active_radar_trap" -> "🔍 [Action] 正在查询雷达哨兵布控状态..."
                    else -> "⚙️ [Action] 正在调用工具: $funcName..."
                }
                onToolAction(statusText)

                // 产生 Observation
                val observation = toolRegistry.executeTool(funcName, funcArgs)

                // 登记执行记录
                executedToolRecords.add(
                    ExecutedToolRecord(
                        step = step,
                        toolName = funcName,
                        argumentsJson = funcArgs,
                        observation = observation
                    )
                )

                // 将 Observation 回填进入上下文
                if (useNativeTools) {
                    messagesArray.put(
                        JSONObject().apply {
                            put("role", "tool")
                            put("tool_call_id", callId)
                            put("name", funcName)
                            put("content", observation)
                        }
                    )
                } else {
                    // 纯文本模式：使用标准 user 角色回传观测，彻底杜绝无 tools 时 400 报错
                    messagesArray.put(
                        JSONObject().apply {
                            put("role", "user")
                            put("content", "【工具执行观测结果 (Tool Observation for $funcName)】:\n$observation")
                        }
                    )
                }
            }

            // 工具执行完毕，在请求大模型生成最终报告期间展示数据分析动效
            onToolAction("📊 数据检索完毕，正在深入分析并生成报告...")
        }

        // 达到最大步数安全上限时完结
        onToolAction("")
        val rawAnswer = fullAccumulatedContent.toString().trim()
        val safeAnswer = if (rawAnswer.isNotBlank() && rawAnswer != "null") {
            rawAnswer
        } else {
            if (executedToolRecords.isNotEmpty()) {
                buildExecutedToolsReport(executedToolRecords, lastUserPrompt)
            } else {
                generateDynamicFallback(lastUserPrompt)
            }
        }
        onComplete(safeAnswer, fullAccumulatedReasoning.toString())
    }

    // =========================================================================
    // 工业级高可用辅助函数群 (Universal Extractors & ReAct Utilities)
    // =========================================================================

    /**
     * 智能从 Tool Call ID 中反推合法工具名（针对 Gemini 原生 API 未下发独立 name 的修复）
     */
    private fun extractToolNameFromId(id: String): String {
        for (validName in VALID_TOOL_NAMES) {
            if (id.startsWith(validName, ignoreCase = true) || id.contains(validName, ignoreCase = true)) {
                return validName
            }
        }
        return ""
    }

    /**
     * 多态参数提取器：兼容 arguments, args, parameters, input 等多种数据格式
     */
    private fun extractToolArgumentsString(funcObj: JSONObject?, rawToolObj: JSONObject): String {
        if (funcObj != null) {
            val rawArgs = funcObj.opt("arguments") ?: funcObj.opt("args") ?: funcObj.opt("parameters") ?: funcObj.opt("input")
            if (rawArgs != null) {
                return when (rawArgs) {
                    is JSONObject, is JSONArray -> rawArgs.toString()
                    is String -> rawArgs
                    else -> rawArgs.toString()
                }
            }
        }
        val topArgs = rawToolObj.opt("arguments") ?: rawToolObj.opt("args") ?: rawToolObj.opt("input")
        if (topArgs != null) {
            return when (topArgs) {
                is JSONObject, is JSONArray -> topArgs.toString()
                is String -> topArgs
                else -> topArgs.toString()
            }
        }
        return "{}"
    }

    /**
     * 通用 Content 递归解包：解决中转网关返回 Content Block 数组导致 JSONObject.optString 变空白的盲区
     */
    private fun extractContentText(jsonObj: JSONObject, key: String): String {
        if (jsonObj.isNull(key)) return ""
        val raw = jsonObj.opt(key) ?: return ""
        return when (raw) {
            is String -> raw
            is JSONArray -> {
                val sb = StringBuilder()
                for (i in 0 until raw.length()) {
                    val item = raw.optJSONObject(i)
                    if (item != null) {
                        val text = item.optString("text", "")
                        if (text.isNotEmpty()) sb.append(text)
                    } else {
                        sb.append(raw.optString(i, ""))
                    }
                }
                sb.toString()
            }
            else -> raw.toString()
        }
    }

    /**
     * 纯文本 ReAct 工具调用全协议拦截（4 大主流语法通吃）
     */
    private fun extractTextualToolCalls(content: String): List<JSONObject> {
        val results = mutableListOf<JSONObject>()

        // 格式 1: ```tool:get_live_packets\n{"mode": "summary"}\n```
        val directCodeBlockRegex = Regex("```(?:tool:)?([a-zA-Z0-9_]+)\\s*\\n([\\s\\S]*?)```")
        directCodeBlockRegex.findAll(content).forEach { match ->
            val toolName = match.groupValues[1].trim()
            val payload = match.groupValues[2].trim()
            if (VALID_TOOL_NAMES.contains(toolName)) {
                val argsJson = sanitizeToJsonString(payload)
                results.add(buildToolCallJson(toolName, argsJson))
            }
        }
        if (results.isNotEmpty()) return results

        // 格式 2: ```json\n{"name": "get_live_packets", "arguments": {...}}\n```
        val jsonBlockRegex = Regex("```(?:json)?\\s*\\n([\\s\\S]*?)```")
        jsonBlockRegex.findAll(content).forEach { match ->
            val jsonText = match.groupValues[1].trim()
            try {
                if (jsonText.startsWith("{") && jsonText.endsWith("}")) {
                    val obj = JSONObject(jsonText)
                    val name = obj.optString("name", "").ifEmpty { obj.optString("tool", "") }.ifEmpty { obj.optString("function", "") }
                    if (VALID_TOOL_NAMES.contains(name)) {
                        val args = obj.opt("arguments") ?: obj.opt("parameters") ?: obj.opt("params") ?: "{}"
                        val argsStr = if (args is JSONObject || args is JSONArray) args.toString() else args.toString()
                        results.add(buildToolCallJson(name, argsStr))
                    }
                }
            } catch (_: Exception) {}
        }
        if (results.isNotEmpty()) return results

        // 格式 3: <tool_call>{"name": "get_live_packets", "arguments": {...}}</tool_call>
        val tagRegex = Regex("<(?:tool_call|tool|action)>\\s*([\\s\\S]*?)\\s*</(?:tool_call|tool|action)>")
        tagRegex.findAll(content).forEach { match ->
            val raw = match.groupValues[1].trim()
            try {
                if (raw.startsWith("{") && raw.endsWith("}")) {
                    val obj = JSONObject(raw)
                    val name = obj.optString("name", "").ifEmpty { obj.optString("tool", "") }
                    if (VALID_TOOL_NAMES.contains(name)) {
                        val args = obj.opt("arguments") ?: obj.opt("parameters") ?: "{}"
                        results.add(buildToolCallJson(name, args.toString()))
                    }
                }
            } catch (_: Exception) {}
        }
        if (results.isNotEmpty()) return results

        // 格式 4: Action: get_live_packets\nAction Input: {"mode": "summary"}
        val reactRegex = Regex("Action:\\s*([a-zA-Z0-9_]+)\\s*\\n+Action Input:\\s*([\\s\\S]+?)(?:\\n*(?:Thought|Observation|Action:|$))")
        reactRegex.findAll(content).forEach { match ->
            val toolName = match.groupValues[1].trim()
            val inputPayload = match.groupValues[2].trim()
            if (VALID_TOOL_NAMES.contains(toolName)) {
                val argsJson = sanitizeToJsonString(inputPayload)
                results.add(buildToolCallJson(toolName, argsJson))
            }
        }

        return results
    }

    private fun sanitizeToJsonString(payload: String): String {
        val trimmed = payload.trim()
        if (trimmed.startsWith("{") && trimmed.endsWith("}")) return trimmed
        return try {
            JSONObject().apply { put("query", trimmed) }.toString()
        } catch (_: Exception) {
            "{}"
        }
    }

    private fun buildToolCallJson(toolName: String, argumentsJson: String): JSONObject {
        val safeId = "text_call_${System.currentTimeMillis()}_${(100..999).random()}"
        return JSONObject().apply {
            put("id", safeId)
            put("type", "function")
            put(
                "function",
                JSONObject().apply {
                    put("name", toolName)
                    put("arguments", if (argumentsJson.isNotBlank()) argumentsJson else "{}")
                }
            )
        }
    }

    /**
     * 免 tools 模式上下文净化：将前序步骤的 role: "tool" 转换为标准 user 消息，
     * 移除 assistant 中的 tool_calls 字段，杜绝 CLIProxyAPI / Gemini 报 400
     */
    private fun sanitizeMessagesForTextMode(originalArray: JSONArray, isFinalStep: Boolean): JSONArray {
        val cleanArray = JSONArray()
        for (i in 0 until originalArray.length()) {
            val item = originalArray.optJSONObject(i) ?: continue
            val role = item.optString("role", "")
            val content = item.optString("content", "")

            when (role) {
                "system" -> {
                    // 若处于纯文本模式，追加文本 ReAct 语法提示
                    val augmentedContent = if (!content.contains("Text ReAct Mode")) {
                        content + TEXT_REACT_PROMPT_ADDITION
                    } else {
                        content
                    }
                    cleanArray.put(JSONObject().apply {
                        put("role", "system")
                        put("content", augmentedContent)
                    })
                }
                "tool" -> {
                    val toolName = item.optString("name", "tool")
                    cleanArray.put(JSONObject().apply {
                        put("role", "user")
                        put("content", "【工具执行观测结果 (Tool Observation for $toolName)】:\n$content")
                    })
                }
                "assistant" -> {
                    val copy = JSONObject(item.toString())
                    copy.remove("tool_calls")
                    val existingContent = copy.optString("content", "")
                    if (existingContent.isBlank()) {
                        copy.put("content", "（正在分析并调度现场工具...）")
                    }
                    cleanArray.put(copy)
                }
                else -> {
                    cleanArray.put(item)
                }
            }
        }

        // 若处于终答收敛步，追加强制总结指令促使模型直接输出报告
        if (isFinalStep) {
            cleanArray.put(
                JSONObject().apply {
                    put("role", "user")
                    put("content", "【系统指令】所有现场排查与工具调用已执行完毕。请立即根据前序所有工具观测结果，输出结构完整的现场分析交付报告！")
                }
            )
        }

        return cleanArray
    }

    /**
     * Observation-First 运维成果优先自愈报告生成器
     * 当模型连续调用工具但最终因步数或反代截断未生成文本答复时，生成结构工整严密的 Markdown 现场分析与排查交付报告，
     * 彻底杜绝抹杀现场成果与虚假报错！
     */
    private fun buildExecutedToolsReport(records: List<ExecutedToolRecord>, lastUserPrompt: String): String {
        val sb = StringBuilder()
        sb.append("### 📊 MQTT 工业物联网现场智能排查与执行报告\n\n")

        if (lastUserPrompt.isNotBlank()) {
            sb.append("> **🎯 用户指令与排查目标**：${lastUserPrompt.trim()}\n\n")
        }

        sb.append("#### 一、现场工具调度与执行记录清单\n\n")
        sb.append("| 序号 | 时间 | 执行工具 | 目标参数 | 执行状态 |\n")
        sb.append("| :--- | :--- | :--- | :--- | :--- |\n")
        records.forEachIndexed { index, rec ->
            val compactArgs = rec.argumentsJson.replace("\n", " ").take(40)
            sb.append("| ${index + 1} | ${rec.timestamp} | `${rec.toolName}` | `$compactArgs` | ✅ 已完成 |\n")
        }
        sb.append("\n")

        sb.append("#### 二、现场关键业务观测数据与发现\n\n")
        records.forEachIndexed { index, rec ->
            val title = when (rec.toolName) {
                "get_live_packets" -> "内存实时报文流检索结果"
                "deploy_radar_interceptor" -> "AI 实时雷达哨兵布控状态"
                "publish_mqtt_message" -> "MQTT 双向报文下发结果"
                "save_tsl_protocol" -> "TSL 物模型自动注册与激活"
                "set_protocol_key_indicator" -> "物模型胶囊展示指标动态绑定"
                "get_excel_summary" -> "Excel 历史归档宏观画像"
                "query_excel_data" -> "Excel 归档历史明细采样"
                "web_search" -> "工业规约与技术资料联网检索"
                "get_protocol_clarification" -> "硬件私有协议解码规范"
                "save_protocol_knowledge" -> "私有协议沉淀入库"
                else -> "${rec.toolName} 执行结果"
            }
            sb.append("**【步骤 ${index + 1}】$title**：\n")
            val obsSnippet = rec.observation.trim()
            if (obsSnippet.startsWith("{") || obsSnippet.startsWith("[")) {
                sb.append("```json\n${obsSnippet.take(1200)}\n```\n\n")
            } else {
                sb.append("${obsSnippet.take(1200)}\n\n")
            }
        }

        sb.append("#### 三、现场工程建议与后续处置\n\n")
        sb.append("1. **状态确认**：底层各步骤操作与数据检索已成功执行完成，核心业务数据如上所示；\n")
        sb.append("2. **持续监控**：若布设了雷达哨兵，系统已在底层微秒级拦截盯防，可随时在消息页面查看高亮捕获项；\n")
        sb.append("3. **进一步分析**：如需深入探查特定网关或字段，可直接告诉我，例如：“分析网关 gw_01 的心跳间隔”或“调取刚才的异常明细”。\n")

        return sb.toString()
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
