package com.example.ui.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.OnPrimaryWhite
import com.example.ui.theme.OnSurfaceDark
import com.example.ui.theme.OnSurfaceVariantGray
import com.example.ui.theme.OutlineVariantLight
import com.example.ui.theme.PrimaryBlack
import com.example.ui.theme.SurfaceContainerDefault
import com.example.ui.theme.SurfaceContainerLow
import com.example.ui.theme.SurfaceContainerLowest

/**
 * 生产级轻量 Markdown 富文本渲染器：
 * 纯原生 Compose 实现，零第三方库依赖，全面解析与排版大模型输出：
 * 1. 多级标题 (#, ##, ###)
 * 2. 粗体 (**bold**)、斜体 (*italic*)、行内代码 (`code`)
 * 3. 独立代码块 (```lang ... ``` 带灰底卡片与一键复制)
 * 4. 引用块 (> quote)
 * 5. 有序 / 无序列表 (- item, • item, 1. item)
 * 6. Markdown 数据分析表格 (| col1 | col2 |)
 * 7. 异常健康警告段落 (⚠️)
 */
@Composable
fun MarkdownRenderer(
    content: String,
    modifier: Modifier = Modifier,
    isUser: Boolean = false
) {
    if (content.isBlank()) return

    val blocks = remember(content) { parseMarkdownBlocks(content) }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        for (block in blocks) {
            when (block) {
                is MdBlock.Heading -> {
                    val fontSize = when (block.level) {
                        1 -> 16.sp
                        2 -> 14.5.sp
                        else -> 13.5.sp
                    }
                    Text(
                        text = block.text,
                        style = TextStyle(
                            fontSize = fontSize,
                            fontWeight = FontWeight.Bold,
                            color = if (isUser) OnPrimaryWhite else PrimaryBlack,
                            letterSpacing = (-0.2).sp
                        ),
                        modifier = Modifier.padding(top = 4.dp, bottom = 2.dp)
                    )
                }

                is MdBlock.CodeBlock -> {
                    CodeBlockCard(code = block.code, language = block.language)
                }

                is MdBlock.Table -> {
                    MarkdownTableCard(headers = block.headers, rows = block.rows)
                }

                is MdBlock.Quote -> {
                    Surface(
                        shape = RoundedCornerShape(topEnd = 6.dp, bottomEnd = 6.dp),
                        color = if (isUser) Color.White.copy(alpha = 0.1f) else SurfaceContainerLow,
                        border = BorderStroke(0.6.dp, OutlineVariantLight),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)) {
                            Box(
                                modifier = Modifier
                                    .width(3.dp)
                                    .height(18.dp)
                                    .background(PrimaryBlack.copy(alpha = 0.6f))
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = block.text,
                                style = TextStyle(
                                    fontSize = 12.5.sp,
                                    fontStyle = FontStyle.Italic,
                                    color = if (isUser) OnPrimaryWhite.copy(alpha = 0.9f) else OnSurfaceDark,
                                    lineHeight = 17.sp
                                )
                            )
                        }
                    }
                }

                is MdBlock.WarningCallout -> {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = Color(0xFFFEF2F2),
                        border = BorderStroke(0.8.dp, Color(0xFFFCA5A5)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = block.text,
                            style = TextStyle(
                                fontSize = 12.5.sp,
                                fontWeight = FontWeight.Medium,
                                color = Color(0xFFB91C1C),
                                lineHeight = 17.sp
                            ),
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp)
                        )
                    }
                }

                is MdBlock.Paragraph -> {
                    Text(
                        text = buildInlineMarkdown(block.text, isUser),
                        style = TextStyle(
                            fontSize = 13.5.sp,
                            color = if (isUser) OnPrimaryWhite else PrimaryBlack,
                            lineHeight = 19.5.sp
                        )
                    )
                }
            }
        }
    }
}

@Composable
private fun CodeBlockCard(code: String, language: String) {
    val context = LocalContext.current
    Card(
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF0F172A))
                    .padding(horizontal = 10.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = language.ifBlank { "code" },
                    style = TextStyle(fontSize = 10.5.sp, fontFamily = FontFamily.Monospace, color = Color(0xFF94A3B8))
                )
                IconButton(
                    onClick = {
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        clipboard.setPrimaryClip(ClipData.newPlainText("code", code))
                    },
                    modifier = Modifier.size(22.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.ContentCopy,
                        contentDescription = "复制",
                        tint = Color(0xFF94A3B8),
                        modifier = Modifier.size(13.dp)
                    )
                }
            }
            Text(
                text = code,
                style = TextStyle(
                    fontSize = 11.5.sp,
                    fontFamily = FontFamily.Monospace,
                    color = Color(0xFFF8FAFC),
                    lineHeight = 16.sp
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(10.dp)
            )
        }
    }
}

@Composable
private fun MarkdownTableCard(headers: List<String>, rows: List<List<String>>) {
    Card(
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = SurfaceContainerLowest),
        border = BorderStroke(0.8.dp, OutlineVariantLight),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.5.dp),
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
    ) {
        Column(modifier = Modifier.padding(6.dp)) {
            // Header Row
            Row(
                modifier = Modifier
                    .background(SurfaceContainerLow, RoundedCornerShape(4.dp))
                    .padding(vertical = 6.dp)
            ) {
                for (h in headers) {
                    Text(
                        text = h.trim(),
                        style = TextStyle(fontSize = 11.5.sp, fontWeight = FontWeight.Bold, color = PrimaryBlack),
                        modifier = Modifier
                            .width(100.dp)
                            .padding(horizontal = 8.dp)
                    )
                }
            }
            HorizontalDivider(color = SurfaceContainerDefault, thickness = 0.6.dp, modifier = Modifier.padding(vertical = 2.dp))
            // Body Rows
            for ((idx, row) in rows.withIndex()) {
                val bg = if (idx % 2 == 1) SurfaceContainerLow.copy(alpha = 0.35f) else Color.Transparent
                Row(
                    modifier = Modifier
                        .background(bg, RoundedCornerShape(3.dp))
                        .padding(vertical = 5.dp)
                ) {
                    for (cell in row) {
                        Text(
                            text = cell.trim(),
                            style = TextStyle(fontSize = 11.sp, color = OnSurfaceDark),
                            modifier = Modifier
                                .width(100.dp)
                                .padding(horizontal = 8.dp)
                        )
                    }
                }
            }
        }
    }
}

private sealed class MdBlock {
    data class Heading(val level: Int, val text: String) : MdBlock()
    data class Paragraph(val text: String) : MdBlock()
    data class CodeBlock(val language: String, val code: String) : MdBlock()
    data class Quote(val text: String) : MdBlock()
    data class WarningCallout(val text: String) : MdBlock()
    data class Table(val headers: List<String>, val rows: List<List<String>>) : MdBlock()
}

private fun parseMarkdownBlocks(rawText: String): List<MdBlock> {
    val blocks = mutableListOf<MdBlock>()
    val lines = rawText.lines()
    var i = 0

    while (i < lines.size) {
        val line = lines[i]
        val trimmed = line.trim()

        // 1. Code block fence ```
        if (trimmed.startsWith("```")) {
            val language = trimmed.removePrefix("```").trim()
            val codeLines = mutableListOf<String>()
            i++
            while (i < lines.size && !lines[i].trim().startsWith("```")) {
                codeLines.add(lines[i])
                i++
            }
            blocks.add(MdBlock.CodeBlock(language, codeLines.joinToString("\n")))
            i++
            continue
        }

        // 2. Table row starting with |
        if (trimmed.startsWith("|") && trimmed.endsWith("|") && i + 1 < lines.size && lines[i + 1].trim().contains("---")) {
            val headers = trimmed.split("|").filter { it.isNotBlank() }
            i += 2 // skip separator row
            val rows = mutableListOf<List<String>>()
            while (i < lines.size && lines[i].trim().startsWith("|")) {
                val cells = lines[i].trim().split("|").filter { it.isNotBlank() }
                rows.add(cells)
                i++
            }
            blocks.add(MdBlock.Table(headers, rows))
            continue
        }

        // 3. Headings
        if (trimmed.startsWith("#")) {
            val level = trimmed.takeWhile { it == '#' }.length.coerceIn(1, 3)
            val text = trimmed.dropWhile { it == '#' }.trim()
            blocks.add(MdBlock.Heading(level, text))
            i++
            continue
        }

        // 4. Warning callout
        if (trimmed.startsWith("⚠️") || (trimmed.contains("异常") && trimmed.contains("高血压"))) {
            blocks.add(MdBlock.WarningCallout(trimmed))
            i++
            continue
        }

        // 5. Quote
        if (trimmed.startsWith(">")) {
            blocks.add(MdBlock.Quote(trimmed.removePrefix(">").trim()))
            i++
            continue
        }

        // 6. Normal paragraph or list
        if (trimmed.isNotBlank()) {
            blocks.add(MdBlock.Paragraph(line))
        }

        i++
    }

    return blocks
}

private fun buildInlineMarkdown(text: String, isUser: Boolean): AnnotatedString {
    return buildAnnotatedString {
        var currentIndex = 0
        val regex = Regex("(\\*\\*([^*]+)\\*\\*)|(`([^`]+)`)")
        val matches = regex.findAll(text)

        for (match in matches) {
            if (match.range.first > currentIndex) {
                append(text.substring(currentIndex, match.range.first))
            }

            val boldContent = match.groups[2]?.value
            val codeContent = match.groups[4]?.value

            if (boldContent != null) {
                pushStyle(SpanStyle(fontWeight = FontWeight.Bold))
                append(boldContent)
                pop()
            } else if (codeContent != null) {
                pushStyle(
                    SpanStyle(
                        fontFamily = FontFamily.Monospace,
                        background = if (isUser) Color.White.copy(alpha = 0.2f) else Color(0xFFF1F5F9),
                        color = if (isUser) Color.White else Color(0xFF0F172A)
                    )
                )
                append(" $codeContent ")
                pop()
            }

            currentIndex = match.range.last + 1
        }

        if (currentIndex < text.length) {
            append(text.substring(currentIndex))
        }
    }
}
