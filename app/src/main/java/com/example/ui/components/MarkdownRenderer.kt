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
import androidx.compose.foundation.layout.widthIn
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
import org.commonmark.ext.gfm.tables.TableBlock
import org.commonmark.ext.gfm.tables.TableBody
import org.commonmark.ext.gfm.tables.TableCell
import org.commonmark.ext.gfm.tables.TableHead
import org.commonmark.ext.gfm.tables.TableRow
import org.commonmark.ext.gfm.tables.TablesExtension
import org.commonmark.node.BlockQuote
import org.commonmark.node.BulletList
import org.commonmark.node.Code
import org.commonmark.node.Emphasis
import org.commonmark.node.FencedCodeBlock
import org.commonmark.node.HardLineBreak
import org.commonmark.node.Heading
import org.commonmark.node.IndentedCodeBlock
import org.commonmark.node.Link
import org.commonmark.node.ListItem
import org.commonmark.node.Node
import org.commonmark.node.OrderedList
import org.commonmark.node.Paragraph
import org.commonmark.node.SoftLineBreak
import org.commonmark.node.StrongEmphasis
import org.commonmark.node.Text as MdText
import org.commonmark.node.ThematicBreak
import org.commonmark.parser.Parser

/**
 * 生产级工业标准 CommonMark AST 富文本渲染器：
 * 采用 Google / GitHub 官方标准 CommonMark 规范与 GFM 表格扩展，
 * 将 AST 语法树递归解析为极简 Jetpack Compose 原生组件：
 * 
 * 1. 深度解析表格 (TableBlock) 内部嵌套的加粗、代码徽标、斜体；支持手机窄屏横向平滑滑动；
 * 2. 完美解析横向分割线 (ThematicBreak ---)；
 * 3. 独立代码块 (```lang ... ```) 配备深色卡片与一键剪贴板复制；
 * 4. 引用块 (> quote) 优雅竖线微晕卡片；
 * 5. 多级标题 (#, ##, ###)；
 * 6. 有序/无序列表 (• / 1.)；
 * 7. 异常健康警告段落 (⚠️) 自动识别为警示卡片。
 */
@Composable
fun MarkdownRenderer(
    content: String,
    modifier: Modifier = Modifier,
    isUser: Boolean = false
) {
    if (content.isBlank()) return

    val document = remember(content) {
        val extensions = listOf(TablesExtension.create())
        val parser = Parser.builder().extensions(extensions).build()
        parser.parse(content)
    }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        var childNode = document.firstChild
        while (childNode != null) {
            RenderAstBlockNode(node = childNode, isUser = isUser)
            childNode = childNode.next
        }
    }
}

@Composable
private fun RenderAstBlockNode(node: Node, isUser: Boolean) {
    when (node) {
        is Heading -> {
            val fontSize = when (node.level) {
                1 -> 16.sp
                2 -> 14.5.sp
                3 -> 13.5.sp
                else -> 12.5.sp
            }
            val annotated = buildInlineAnnotatedString(node, isUser)
            Text(
                text = annotated,
                style = TextStyle(
                    fontSize = fontSize,
                    fontWeight = FontWeight.Bold,
                    color = if (isUser) OnPrimaryWhite else PrimaryBlack,
                    letterSpacing = (-0.2).sp
                ),
                modifier = Modifier.padding(top = 4.dp, bottom = 2.dp)
            )
        }

        is Paragraph -> {
            val textContent = extractPlainNodeText(node).trim()
            if (textContent.startsWith("⚠️") || (textContent.contains("异常") && textContent.contains("高血压"))) {
                // 自动识别为健康告警卡片
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = Color(0xFFFEF2F2),
                    border = BorderStroke(0.8.dp, Color(0xFFFCA5A5)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = buildInlineAnnotatedString(node, isUser),
                        style = TextStyle(
                            fontSize = 12.5.sp,
                            fontWeight = FontWeight.Medium,
                            color = Color(0xFFB91C1C),
                            lineHeight = 17.5.sp
                        ),
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp)
                    )
                }
            } else {
                Text(
                    text = buildInlineAnnotatedString(node, isUser),
                    style = TextStyle(
                        fontSize = 13.5.sp,
                        color = if (isUser) OnPrimaryWhite else PrimaryBlack,
                        lineHeight = 19.5.sp
                    )
                )
            }
        }

        is FencedCodeBlock -> {
            CodeBlockCard(code = node.literal.trimEnd(), language = node.info ?: "")
        }

        is IndentedCodeBlock -> {
            CodeBlockCard(code = node.literal.trimEnd(), language = "")
        }

        is TableBlock -> {
            AstTableCard(tableNode = node, isUser = isUser)
        }

        is ThematicBreak -> {
            HorizontalDivider(
                color = SurfaceContainerDefault,
                thickness = 0.8.dp,
                modifier = Modifier.padding(vertical = 4.dp)
            )
        }

        is BlockQuote -> {
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
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        var quoteChild = node.firstChild
                        while (quoteChild != null) {
                            RenderAstBlockNode(node = quoteChild, isUser = isUser)
                            quoteChild = quoteChild.next
                        }
                    }
                }
            }
        }

        is BulletList -> {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                var item = node.firstChild
                while (item != null) {
                    if (item is ListItem) {
                        Row(modifier = Modifier.fillMaxWidth()) {
                            Text(
                                text = "• ",
                                style = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.Bold, color = if (isUser) OnPrimaryWhite else PrimaryBlack),
                                modifier = Modifier.padding(start = 4.dp, end = 2.dp)
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                var itemChild = item.firstChild
                                while (itemChild != null) {
                                    RenderAstBlockNode(node = itemChild, isUser = isUser)
                                    itemChild = itemChild.next
                                }
                            }
                        }
                    }
                    item = item.next
                }
            }
        }

        is OrderedList -> {
            var index = node.startNumber
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                var item = node.firstChild
                while (item != null) {
                    if (item is ListItem) {
                        Row(modifier = Modifier.fillMaxWidth()) {
                            Text(
                                text = "$index. ",
                                style = TextStyle(fontSize = 12.5.sp, fontWeight = FontWeight.Bold, color = if (isUser) OnPrimaryWhite else PrimaryBlack),
                                modifier = Modifier.padding(start = 4.dp, end = 2.dp)
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                var itemChild = item.firstChild
                                while (itemChild != null) {
                                    RenderAstBlockNode(node = itemChild, isUser = isUser)
                                    itemChild = itemChild.next
                                }
                            }
                        }
                        index++
                    }
                    item = item.next
                }
            }
        }
    }
}

/**
 * 递归构建包含加粗、行内代码、斜体和链接的 AnnotatedString
 */
private fun buildInlineAnnotatedString(parentNode: Node, isUser: Boolean): AnnotatedString {
    return buildAnnotatedString {
        appendInlineChildren(parentNode, this, isUser)
    }
}

private fun appendInlineChildren(parent: Node, builder: AnnotatedString.Builder, isUser: Boolean) {
    var child = parent.firstChild
    while (child != null) {
        when (child) {
            is MdText -> {
                builder.append(child.literal)
            }

            is StrongEmphasis -> {
                builder.pushStyle(SpanStyle(fontWeight = FontWeight.Bold))
                appendInlineChildren(child, builder, isUser)
                builder.pop()
            }

            is Emphasis -> {
                builder.pushStyle(SpanStyle(fontStyle = FontStyle.Italic))
                appendInlineChildren(child, builder, isUser)
                builder.pop()
            }

            is Code -> {
                builder.pushStyle(
                    SpanStyle(
                        fontFamily = FontFamily.Monospace,
                        fontSize = 12.sp,
                        background = if (isUser) Color.White.copy(alpha = 0.2f) else Color(0xFFF1F5F9),
                        color = if (isUser) Color.White else Color(0xFF0F172A)
                    )
                )
                builder.append(" ${child.literal} ")
                builder.pop()
            }

            is Link -> {
                builder.pushStyle(SpanStyle(color = if (isUser) Color(0xFF93C5FD) else Color(0xFF2563EB), fontWeight = FontWeight.Medium))
                appendInlineChildren(child, builder, isUser)
                builder.pop()
            }

            is SoftLineBreak -> {
                builder.append(" ")
            }

            is HardLineBreak -> {
                builder.append("\n")
            }

            else -> {
                appendInlineChildren(child, builder, isUser)
            }
        }
        child = child.next
    }
}

private fun extractPlainNodeText(node: Node): String {
    val sb = StringBuilder()
    var child = node.firstChild
    while (child != null) {
        when (child) {
            is MdText -> sb.append(child.literal)
            is Code -> sb.append(child.literal)
            else -> sb.append(extractPlainNodeText(child))
        }
        child = child.next
    }
    return sb.toString()
}

/**
 * CommonMark GFM 表格卡片：
 * 深度解析单元格内的富文本，支持在窄屏上自由横向滑动
 */
@Composable
private fun AstTableCard(tableNode: TableBlock, isUser: Boolean) {
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
            var tablePart = tableNode.firstChild
            while (tablePart != null) {
                when (tablePart) {
                    is TableHead -> {
                        var rowNode = tablePart.firstChild
                        while (rowNode != null) {
                            if (rowNode is TableRow) {
                                Row(
                                    modifier = Modifier
                                        .background(SurfaceContainerLow, RoundedCornerShape(4.dp))
                                        .padding(vertical = 6.dp)
                                ) {
                                    var cellNode = rowNode.firstChild
                                    while (cellNode != null) {
                                        if (cellNode is TableCell) {
                                            val cellAnnotated = buildInlineAnnotatedString(cellNode, isUser = false)
                                            Text(
                                                text = cellAnnotated,
                                                style = TextStyle(
                                                    fontSize = 11.5.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = PrimaryBlack
                                                ),
                                                modifier = Modifier
                                                    .widthIn(min = 90.dp, max = 220.dp)
                                                    .padding(horizontal = 8.dp)
                                            )
                                        }
                                        cellNode = cellNode.next
                                    }
                                }
                                HorizontalDivider(
                                    color = SurfaceContainerDefault,
                                    thickness = 0.6.dp,
                                    modifier = Modifier.padding(vertical = 2.dp)
                                )
                            }
                            rowNode = rowNode.next
                        }
                    }

                    is TableBody -> {
                        var rowIdx = 0
                        var rowNode = tablePart.firstChild
                        while (rowNode != null) {
                            if (rowNode is TableRow) {
                                val bg = if (rowIdx % 2 == 1) SurfaceContainerLow.copy(alpha = 0.35f) else Color.Transparent
                                Row(
                                    modifier = Modifier
                                        .background(bg, RoundedCornerShape(3.dp))
                                        .padding(vertical = 5.dp)
                                ) {
                                    var cellNode = rowNode.firstChild
                                    while (cellNode != null) {
                                        if (cellNode is TableCell) {
                                            val cellAnnotated = buildInlineAnnotatedString(cellNode, isUser = false)
                                            Text(
                                                text = cellAnnotated,
                                                style = TextStyle(
                                                    fontSize = 11.sp,
                                                    color = OnSurfaceDark
                                                ),
                                                modifier = Modifier
                                                    .widthIn(min = 90.dp, max = 220.dp)
                                                    .padding(horizontal = 8.dp)
                                            )
                                        }
                                        cellNode = cellNode.next
                                    }
                                }
                                rowIdx++
                            }
                            rowNode = rowNode.next
                        }
                    }
                }
                tablePart = tablePart.next
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
