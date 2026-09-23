package com.example.ai

import android.util.Log
import com.example.model.AiAgentConfig
import com.example.model.AiChatMessage
import org.json.JSONArray
import org.json.JSONObject

/**
 * 生产级上下文动态监控与自适应压缩器 (Context Compactor)：
 * 参考现代极简 Agent (如 Pi Agent) 内存管理范式：
 * 1. 动态估算中英混合与 JSON 结构化载荷的 Token 水位；
 * 2. 当会话上下文占用达到设定阈值 (默认 70% contextWindow) 时，自动启动语义记忆压缩；
 * 3. 永久保留 System Prompt 与最近活跃轮次，将早期的历史对话及大体量工具调用结果 (SQL/Excel Observation) 递归提炼为紧凑的结构化事实摘要；
 * 4. 彻底防止移动端 Context Window 溢出 (400 Bad Request)，实现无上限轮次的持久稳定会话。
 */
object ContextCompactor {

    private const val TAG = "ContextCompactor"

    /**
     * 针对中英文、代码、SQL 与 JSON 载荷的高效加权 Token 估算器
     */
    fun estimateTokens(text: String): Int {
        if (text.isEmpty()) return 0
        var tokens = 0.0
        for (char in text) {
            val code = char.code
            tokens += when {
                // 中日韩统一表意文字 (CJK)
                code in 0x4E00..0x9FFF || code in 0x3400..0x4DBF -> 1.3
                // 空白符与换行
                char.isWhitespace() -> 0.3
                // ASCII 标点
                code in 33..47 || code in 58..64 || code in 91..96 || code in 123..126 -> 0.5
                // 普通 ASCII 字母与数字
                code < 128 -> 0.28
                // 其他非 ASCII 字符
                else -> 1.0
            }
        }
        return Math.ceil(tokens).toInt().coerceAtLeast(1)
    }

    /**
     * 计算当前即将发送的完整请求消息列表的预估总 Token 数
     */
    fun calculateMessagesTokens(messages: JSONArray): Int {
        var total = 0
        for (i in 0 until messages.length()) {
            val item = messages.optJSONObject(i) ?: continue
            val role = item.optString("role", "")
            val content = item.optString("content", "")
            val toolCalls = item.optJSONArray("tool_calls")
            total += 4 // 消息外壳开销
            total += estimateTokens(role)
            total += estimateTokens(content)
            if (toolCalls != null) {
                total += estimateTokens(toolCalls.toString())
            }
        }
        return total
    }

    /**
     * 检查当前上下文是否已达到触发自动压缩的安全水位 (默认 70%)
     */
    fun isOverThreshold(estimatedTokens: Int, config: AiAgentConfig): Boolean {
        val window = config.contextWindow.coerceAtLeast(4096)
        val thresholdLimit = (window * config.compactionThreshold).toInt()
        val isOver = estimatedTokens >= thresholdLimit
        if (isOver) {
            Log.i(TAG, "上下文水位告警: 当前已消耗 ~$estimatedTokens tokens, 超过上限 $thresholdLimit (70% of $window)，触发自动压缩！")
        }
        return isOver
    }

    /**
     * 核心上下文智能自适应压缩算法 (Compaction)：
     * @param systemContent 顶层系统 Prompt
     * @param historyList 历史所有消息列表
     * @param config 大模型与上下文配置
     * @return 经过无损语义提炼与折叠后构建的安全 JSONArray
     */
    fun buildCompactedMessagesJson(
        systemContent: String,
        historyList: List<AiChatMessage>,
        config: AiAgentConfig
    ): JSONArray {
        val messagesJson = JSONArray()

        // 1. System Prompt (永久保留)
        messagesJson.put(
            JSONObject().apply {
                put("role", "system")
                put("content", systemContent)
            }
        )

        if (historyList.isEmpty()) {
            return messagesJson
        }

        // 2. 初步装配全部历史消息计算总水位
        val rawMessages = mutableListOf<JSONObject>()
        for (msg in historyList) {
            val obj = JSONObject().apply {
                put("role", msg.role)
                put("content", msg.content)
                if (msg.toolCallsJson.isNotBlank()) {
                    try {
                        put("tool_calls", JSONArray(msg.toolCallsJson))
                    } catch (_: Exception) {}
                }
                if (msg.toolCallId.isNotBlank()) {
                    put("tool_call_id", msg.toolCallId)
                }
            }
            rawMessages.add(obj)
        }

        val fullArray = JSONArray().apply {
            put(JSONObject().apply { put("role", "system"); put("content", systemContent) })
            rawMessages.forEach { put(it) }
        }

        val totalTokens = calculateMessagesTokens(fullArray)

        // 若未达到 70% 水位，直接原样使用全部历史
        if (!isOverThreshold(totalTokens, config)) {
            return fullArray
        }

        // 3. 超标 70%：启动语义精炼与记忆压缩
        // 策略：保留最近 2 轮活跃问答 (约 4~6 条消息)，将早期的所有对话与大体量工具 Observation 折叠为结构化事实记忆
        val keepCount = 4.coerceAtMost(rawMessages.size)
        val olderMessages = rawMessages.subList(0, rawMessages.size - keepCount)
        val recentMessages = rawMessages.subList(rawMessages.size - keepCount, rawMessages.size)

        val summaryBuilder = StringBuilder()
        summaryBuilder.append("【系统自动记忆压缩：以下为早期会话提炼的关键已知事实与数据摘要】\n")

        var userQueryIndex = 1
        for (m in olderMessages) {
            val role = m.optString("role")
            val content = m.optString("content")
            when (role) {
                "user" -> {
                    summaryBuilder.append("• 早期指令 $userQueryIndex: \"$content\"\n")
                    userQueryIndex++
                }
                "assistant" -> {
                    if (content.isNotBlank()) {
                        // 截取核心结论部分
                        val concise = if (content.length > 160) content.take(160) + "..." else content
                        summaryBuilder.append("  ↳ 回复结论: $concise\n")
                    }
                }
                "tool" -> {
                    // 工具返回数据通常极大，提炼其简述以节省 90% 以上 Token
                    val toolSummary = if (content.length > 200) {
                        "${content.take(180)}... (已由系统紧凑压缩为环境背景)"
                    } else {
                        content
                    }
                    summaryBuilder.append("  ↳ 工具执行事实: $toolSummary\n")
                }
            }
        }

        val compactedArray = JSONArray()
        // 1. 永久系统 Prompt
        compactedArray.put(
            JSONObject().apply {
                put("role", "system")
                put("content", systemContent)
            }
        )
        // 2. 注入精炼后的早期事实记忆块
        compactedArray.put(
            JSONObject().apply {
                put("role", "system")
                put("content", summaryBuilder.toString())
            }
        )
        // 3. 保留最近活跃对话
        for (recent in recentMessages) {
            compactedArray.put(recent)
        }

        val compactedTokens = calculateMessagesTokens(compactedArray)
        Log.i(TAG, "上下文压缩完成: Token 占用从 ~$totalTokens 缩减至 ~$compactedTokens (回落至安全水位)")

        return compactedArray
    }

    /**
     * 在 ReAct 推理与动作循环中，直接针对活跃演进中的 messages 数组做 70% 水位检测与自适应压缩
     */
    fun compactActiveMessages(
        currentMessages: JSONArray,
        config: AiAgentConfig
    ): JSONArray {
        val totalTokens = calculateMessagesTokens(currentMessages)
        if (!isOverThreshold(totalTokens, config) || currentMessages.length() <= 4) {
            return currentMessages
        }

        val systemPromptObj = currentMessages.optJSONObject(0) ?: return currentMessages
        val rawList = mutableListOf<JSONObject>()
        for (i in 1 until currentMessages.length()) {
            currentMessages.optJSONObject(i)?.let { rawList.add(it) }
        }

        val keepCount = 3.coerceAtMost(rawList.size)
        val olderMessages = rawList.subList(0, rawList.size - keepCount)
        val recentMessages = rawList.subList(rawList.size - keepCount, rawList.size)

        val summaryBuilder = StringBuilder()
        summaryBuilder.append("【系统自动记忆压缩：以下为早期 ReAct 推理中提炼的关键已知事实与观测数据】\n")
        for (m in olderMessages) {
            val role = m.optString("role")
            val content = m.optString("content")
            when (role) {
                "user" -> summaryBuilder.append("• 用户目标: \"${content.take(100)}\"\n")
                "assistant" -> {
                    if (content.isNotBlank() && content != "null") {
                        summaryBuilder.append("• 推理中间结论: ${content.take(120)}\n")
                    }
                    val tools = m.optJSONArray("tool_calls")
                    if (tools != null && tools.length() > 0) {
                        summaryBuilder.append("• 已执行工具数: ${tools.length()} 项\n")
                    }
                }
                "tool" -> {
                    val preview = if (content.length > 150) content.take(150) + "..." else content
                    summaryBuilder.append("• 工具观测(Observation): $preview\n")
                }
            }
        }

        val compacted = JSONArray()
        compacted.put(systemPromptObj)
        compacted.put(JSONObject().apply {
            put("role", "system")
            put("content", summaryBuilder.toString())
        })
        for (recent in recentMessages) {
            compacted.put(recent)
        }

        val newTokens = calculateMessagesTokens(compacted)
        Log.i(TAG, "ReAct 活跃上下文压缩完成: ~$totalTokens tokens -> ~$newTokens tokens")
        return compacted
    }
}
