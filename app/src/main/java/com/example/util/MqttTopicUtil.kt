package com.example.util

/**
 * Utility class adhering strictly to MQTT 3.1.1 and MQTT 5.0 specifications for
 * topic formatting, wildcard syntax validation, and hierarchical matching.
 */
object MqttTopicUtil {

    data class ValidationResult(
        val isValid: Boolean,
        val errorMessage: String? = null
    )

    /**
     * Standard IoT palette of high-contrast, modern accent colors for subscription topics.
     */
    val IOT_DOT_COLORS = listOf(
        0xFF10B981 to "翡翠绿 (Emerald)",
        0xFF2563EB to "科技蓝 (Electric)",
        0xFF8B5CF6 to "极客紫 (Royal)",
        0xFFF59E0B to "琥珀橙 (Amber)",
        0xFF06B6D4 to "青碧色 (Cyan)",
        0xFFF43F5E to "蔷薇红 (Rose)",
        0xFF64748B to "冷工业灰 (Slate)",
        0xFFDC2626 to "告警红 (Crimson)"
    )

    /**
     * Validates an MQTT subscription topic filter.
     * Rules per MQTT Spec:
     * - Multi-level wildcard '#' MUST be the last level in the topic (e.g., "#" or "sport/tennis/#").
     *   "sport/tennis/#/ranking" is INVALID. "sport/tennis#" is INVALID.
     * - Single-level wildcard '+' must occupy an entire level (e.g., "+", "+/tennis", "sport/+/player").
     *   "sport+tennis" is INVALID.
     * - Topic cannot be empty or contain null characters.
     */
    fun validateSubscriptionTopic(topic: String): ValidationResult {
        val trimmed = topic.trim()
        if (trimmed.isEmpty()) {
            return ValidationResult(false, "主题不能为空")
        }
        if (trimmed.contains("\u0000")) {
            return ValidationResult(false, "主题不能包含空字符 (Null character)")
        }

        val levels = trimmed.split("/")

        for (i in levels.indices) {
            val level = levels[i]

            // Multi-level wildcard '#' check
            if (level.contains("#")) {
                if (level != "#") {
                    return ValidationResult(false, "多级通配符 '#' 必须独占一个层级 (如 'sport/#', 不能是 '$level')")
                }
                if (i != levels.size - 1) {
                    return ValidationResult(false, "多级通配符 '#' 只能置于主题的最末级 (不能位于中间层级)")
                }
            }

            // Single-level wildcard '+' check
            if (level.contains("+")) {
                if (level != "+") {
                    return ValidationResult(false, "单级通配符 '+' 必须独占一个层级 (如 'sport/+/player', 不能是 '$level')")
                }
            }
        }

        return ValidationResult(true)
    }

    /**
     * Validates an MQTT publish topic.
     * Publish topics MUST NOT contain any wildcards ('+' or '#').
     */
    fun validatePublishTopic(topic: String): ValidationResult {
        val trimmed = topic.trim()
        if (trimmed.isEmpty()) {
            return ValidationResult(false, "发布主题不能为空")
        }
        if (trimmed.contains("+") || trimmed.contains("#")) {
            return ValidationResult(false, "发布主题严禁包含 '+' 或 '#' 通配符 (MQTT 协议规范)")
        }
        return ValidationResult(true)
    }

    /**
     * Matches an actual published topic against a subscription filter pattern.
     * Complies with MQTT 3.1.1 section 4.7.
     */
    fun matchesMqttTopic(subscriptionFilter: String, actualTopic: String): Boolean {
        if (subscriptionFilter == actualTopic) return true
        if (subscriptionFilter.isEmpty() || actualTopic.isEmpty()) return false

        // MQTT 3.1.1 / 5.0 规范 [MQTT-4.7.2-1]：
        // 过滤器以通配符 ('#' 或 '+') 开头时，严禁匹配以 '$' 开头的系统主题 (如 $SYS/...)。
        // 只有当过滤器本身同样以 '$' 开头时，才允许进行系统主题匹配。
        if (actualTopic.startsWith("$") && !subscriptionFilter.startsWith("$")) {
            return false
        }

        val filterLevels = subscriptionFilter.split("/")
        val topicLevels = actualTopic.split("/")

        var fIndex = 0
        var tIndex = 0

        while (fIndex < filterLevels.size && tIndex < topicLevels.size) {
            val filterLevel = filterLevels[fIndex]
            val topicLevel = topicLevels[tIndex]

            if (filterLevel == "#") {
                // '#' matches any number of remaining levels
                return true
            }

            if (filterLevel != "+" && filterLevel != topicLevel) {
                return false
            }

            fIndex++
            tIndex++
        }

        // If filter ended with '#'
        if (fIndex < filterLevels.size && filterLevels[fIndex] == "#") {
            return true
        }

        // Matched only if both reached the end
        return fIndex == filterLevels.size && tIndex == topicLevels.size
    }

    /**
     * Determines whether an incoming topic should be accepted based on PC-grade Include and Exclude filter rules.
     * Rule priority:
     * 1. Exclude filters have the HIGHEST priority. If the topic matches ANY exclude filter, it is rejected (false).
     * 2. If include filters are defined (non-empty), the topic MUST match AT LEAST ONE include filter (true).
     *    If none match, it is rejected (false).
     * 3. If no include filters are defined, and no exclude filters matched, it is accepted (true).
     */
    fun isTopicAllowed(
        topic: String,
        includeFilters: List<String>,
        excludeFilters: List<String>
    ): Boolean {
        val cleanTopic = topic.trim()
        if (cleanTopic.isEmpty()) return false

        // 1. Exclude check (highest priority)
        for (pattern in excludeFilters) {
            val p = pattern.trim()
            if (p.isNotEmpty() && matchesMqttTopic(p, cleanTopic)) {
                return false
            }
        }

        // 2. Include check
        val activeIncludes = includeFilters.map { it.trim() }.filter { it.isNotEmpty() }
        if (activeIncludes.isEmpty()) {
            return true
        }

        for (pattern in activeIncludes) {
            if (matchesMqttTopic(pattern, cleanTopic)) {
                return true
            }
        }

        return false
    }
}
