package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.json.JSONArray
import org.json.JSONObject

/**
 * VS Code / GitHub 风格的 JSON 词法高亮配色标准 (Light/Clean 风格)
 */
object JsonSyntaxTheme {
    val Background = Color(0xFFF8FAFC) // 柔和现代极简代码块浅灰底
    val BorderColor = Color(0xFFE2E8F0) // 细线边框
    val LineNumberColor = Color(0xFF94A3B8) // 弱化行号颜色

    // GitHub / VS Code 语法着色
    val KeyColor = Color(0xFF0969DA) // 键名：VS Code 经典亮蓝
    val StringColor = Color(0xFF116329) // 字符串值：墨绿色
    val NumberColor = Color(0xFF953800) // 数值：橙棕/琥珀色
    val BooleanColor = Color(0xFF8250DF) // 布尔值：紫色
    val NullColor = Color(0xFFCF222E) // Null 值：柔红
    val PunctuationColor = Color(0xFF475569) // 符号括号：中灰
    val PlainTextColor = Color(0xFF1E293B) // 普通文本
}

/**
 * 将原始 JSON 文本美化并赋予 VS Code / GitHub 风格的语法高亮
 */
fun highlightJsonText(rawJson: String): Pair<List<String>, List<AnnotatedString>> {
    val trimmed = rawJson.trim()
    val formattedJson = try {
        if (trimmed.startsWith("{") && trimmed.endsWith("}")) {
            JSONObject(trimmed).toString(2)
        } else if (trimmed.startsWith("[") && trimmed.endsWith("]")) {
            JSONArray(trimmed).toString(2)
        } else {
            trimmed
        }
    } catch (_: Exception) {
        trimmed
    }

    val lines = formattedJson.lines()
    val annotatedLines = lines.map { line ->
        highlightSingleJsonLine(line)
    }
    return lines to annotatedLines
}

/**
 * 对单行 JSON 进行高亮分词着色
 */
private fun highlightSingleJsonLine(line: String): AnnotatedString {
    return buildAnnotatedString {
        var i = 0
        val len = line.length

        while (i < len) {
            val c = line[i]

            // 1. 处理字符串（键 或 值）
            if (c == '"') {
                val start = i
                i++
                var escaped = false
                while (i < len) {
                    val sc = line[i]
                    if (sc == '\\' && !escaped) {
                        escaped = true
                    } else if (sc == '"' && !escaped) {
                        i++
                        break
                    } else {
                        escaped = false
                    }
                    i++
                }
                val token = line.substring(start, i)

                // 检查后面紧跟着的是否是冒号 ':'（代表这是 Key）
                var lookAhead = i
                while (lookAhead < len && line[lookAhead].isWhitespace()) {
                    lookAhead++
                }
                val isKey = lookAhead < len && line[lookAhead] == ':'

                if (isKey) {
                    pushStyle(SpanStyle(color = JsonSyntaxTheme.KeyColor, fontWeight = FontWeight.SemiBold))
                } else {
                    pushStyle(SpanStyle(color = JsonSyntaxTheme.StringColor, fontWeight = FontWeight.Normal))
                }
                append(token)
                pop()
                continue
            }

            // 2. 处理数字、布尔值、null
            if (c.isDigit() || c == '-' || c.isLetter()) {
                val start = i
                while (i < len && (line[i].isLetterOrDigit() || line[i] == '.' || line[i] == '-')) {
                    i++
                }
                val word = line.substring(start, i)

                when (word) {
                    "true", "false" -> {
                        pushStyle(SpanStyle(color = JsonSyntaxTheme.BooleanColor, fontWeight = FontWeight.Bold))
                        append(word)
                        pop()
                    }
                    "null" -> {
                        pushStyle(SpanStyle(color = JsonSyntaxTheme.NullColor, fontWeight = FontWeight.Bold))
                        append(word)
                        pop()
                    }
                    else -> {
                        // 尝试作为数字
                        if (word.toDoubleOrNull() != null) {
                            pushStyle(SpanStyle(color = JsonSyntaxTheme.NumberColor, fontWeight = FontWeight.Medium))
                        } else {
                            pushStyle(SpanStyle(color = JsonSyntaxTheme.PlainTextColor))
                        }
                        append(word)
                        pop()
                    }
                }
                continue
            }

            // 3. 标点符号与结构符
            if (c in "{}[],:") {
                pushStyle(SpanStyle(color = JsonSyntaxTheme.PunctuationColor, fontWeight = FontWeight.Medium))
                append(c)
                pop()
                i++
                continue
            }

            // 4. 空白与其它普通字符
            append(c)
            i++
        }
    }
}

/**
 * GitHub / VS Code 风格的高级 JSON 代码排版与高亮渲染组件
 */
@Composable
fun JsonCodeBlockView(
    rawText: String,
    isJsonPretty: Boolean = true,
    showLineNumbers: Boolean = true,
    maxLines: Int = Int.MAX_VALUE,
    modifier: Modifier = Modifier
) {
    val trimmed = rawText.trim()
    val isPotentialJson = (trimmed.startsWith("{") && trimmed.endsWith("}")) ||
            (trimmed.startsWith("[") && trimmed.endsWith("]"))

    if (isJsonPretty && isPotentialJson) {
        val (lines, annotatedLines) = remember(rawText) {
            highlightJsonText(rawText)
        }

        val displayLines = if (maxLines < lines.size) annotatedLines.take(maxLines) else annotatedLines
        val totalDisplayCount = displayLines.size

        Box(
            modifier = modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(JsonSyntaxTheme.Background)
                .border(0.8.dp, JsonSyntaxTheme.BorderColor, RoundedCornerShape(8.dp))
                .padding(vertical = 8.dp, horizontal = 10.dp)
        ) {
            val rowModifier = if (maxLines == Int.MAX_VALUE) {
                Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
            } else {
                Modifier.fillMaxWidth()
            }
            Row(modifier = rowModifier) {
                // 行号区（VS Code 经典灰色）
                if (showLineNumbers && totalDisplayCount > 1) {
                    Column {
                        for (lineIndex in 1..totalDisplayCount) {
                            Text(
                                text = lineIndex.toString().padStart(2, ' '),
                                style = TextStyle(
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 11.5.sp,
                                    lineHeight = 17.sp,
                                    color = JsonSyntaxTheme.LineNumberColor
                                )
                            )
                        }
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                }

                // 高亮代码行区
                Column {
                    displayLines.forEach { annotatedLine ->
                        Text(
                            text = annotatedLine,
                            style = TextStyle(
                                fontFamily = FontFamily.Monospace,
                                fontSize = 11.5.sp,
                                lineHeight = 17.sp,
                                color = JsonSyntaxTheme.PlainTextColor
                            ),
                            softWrap = false
                        )
                    }
                    if (maxLines < lines.size) {
                        Text(
                            text = "... (还有 ${lines.size - maxLines} 行，轻触卡片查看全部)",
                            style = TextStyle(
                                fontFamily = FontFamily.Monospace,
                                fontSize = 10.5.sp,
                                color = JsonSyntaxTheme.LineNumberColor
                            )
                        )
                    }
                }
            }
        }
    } else {
        // 普通文本等宽展示
        Box(
            modifier = modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(JsonSyntaxTheme.Background)
                .border(0.8.dp, JsonSyntaxTheme.BorderColor, RoundedCornerShape(8.dp))
                .horizontalScroll(rememberScrollState())
                .padding(10.dp)
        ) {
            Text(
                text = rawText,
                style = TextStyle(
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.5.sp,
                    lineHeight = 17.sp,
                    color = JsonSyntaxTheme.PlainTextColor
                ),
                softWrap = false
            )
        }
    }
}
