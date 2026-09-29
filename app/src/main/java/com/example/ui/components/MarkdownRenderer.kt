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
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.text.style.TextDecoration
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
import org.commonmark.ext.autolink.AutolinkExtension
import org.commonmark.ext.gfm.strikethrough.Strikethrough
import org.commonmark.ext.gfm.strikethrough.StrikethroughExtension
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
 * 大模型友好型 CJK (中日韩) Markdown 规整引擎：
 * 
 * 1. 自动转换伪列表符号 (•, ◦, ▪ 等) 为标准 Markdown 列表符号 (- )，激活官方 AST 挂起缩进列表；
 * 2. 彻底清除粗体定界符内侧的冗余空白 (如 "** 文本 **" 或 "零冗余 **。")，严格满足 CommonMark 6.2 闭合规范；
 * 3. 精准区分 CJK 开启标点与闭合标点，杜绝误插空格造成粗体无法闭合；
 * 4. 自动保护所有行内代码与代码块，严禁误伤代码内容。
 */
object CjkMarkdownNormalizer {
    private val CODE_BLOCK_PATTERN = Regex("""(```[\s\S]*?```|`[^`\n]+`)""")
    
    // 伪列表符号（大模型经常用 Unicode 圆点代替 Markdown 连字符）
    private val PSEUDO_LIST_REGEX = Regex("""(?m)^([\t ]*)[•◦▪◆]\s+""")

    // 成对粗体定界符内侧空白清除（如 "** 文本 **" -> "**文本**"，"零冗余 **。" -> "零冗余**。"）
    private val INNER_BOLD_WHITESPACE_ASTERISK = Regex("""\*\*[\t ]*([^*]+?)[\t ]*\*\*""")
    private val INNER_BOLD_WHITESPACE_UNDERSCORE = Regex("""__[\t ]*([^_]+?)[\t ]*__""")

    // CJK 开启标点与闭合标点
    private const val OPEN_PUNC = """[“‘（【《〈〔\[\(\{]"""
    private const val CLOSE_PUNC = """[”’）】》〉〕\]\)\}。，！？；：]"""

    // 汉字/字母紧贴 ** + 开启标点 (如 汽车的**“发动机 -> 汽车的 **“发动机)
    private val OPEN_BOLD_REGEX = Regex("""([\u4e00-\u9fa5\w])(\*\*)($OPEN_PUNC)""")
    // 闭合标点 + ** + 汉字/字母 (如 发动机”**非常好 -> 发动机”** 非常好)
    private val CLOSE_BOLD_REGEX = Regex("""($CLOSE_PUNC)(\*\*)([\u4e00-\u9fa5\w])""")

    fun normalize(rawMarkdown: String): String {
        if (rawMarkdown.isEmpty() || (!rawMarkdown.contains("*") && !rawMarkdown.contains("_") && !rawMarkdown.contains("•"))) {
            return rawMarkdown
        }

        // 保护代码块与行内代码，仅对非代码文本段落进行排版归一化
        val matches = CODE_BLOCK_PATTERN.findAll(rawMarkdown).toList()
        if (matches.isEmpty()) {
            return fixSegment(rawMarkdown)
        }

        val sb = StringBuilder()
        var lastEnd = 0
        for (m in matches) {
            val start = m.range.first
            val end = m.range.last + 1
            if (start > lastEnd) {
                sb.append(fixSegment(rawMarkdown.substring(lastEnd, start)))
            }
            sb.append(m.value)
            lastEnd = end
        }
        if (lastEnd < rawMarkdown.length) {
            sb.append(fixSegment(rawMarkdown.substring(lastEnd)))
        }
        return sb.toString()
    }

    private fun fixSegment(text: String): String {
        // 1. 规范化伪列表符号
        var s = PSEUDO_LIST_REGEX.replace(text) { "${it.groupValues[1]}- " }

        // 2. 剥除成对加粗内侧的空格，让其完美满足 CommonMark Left/Right-flanking 规范
        s = INNER_BOLD_WHITESPACE_ASTERISK.replace(s) { "**${it.groupValues[1]}**" }
        s = INNER_BOLD_WHITESPACE_UNDERSCORE.replace(s) { "__${it.groupValues[1]}__" }

        // 3. 精准修复 CJK 标点分界
        s = OPEN_BOLD_REGEX.replace(s) { m ->
            "${m.groupValues[1]} ${m.groupValues[2]}${m.groupValues[3]}"
        }
        s = CLOSE_BOLD_REGEX.replace(s) { m ->
            "${m.groupValues[1]}${m.groupValues[2]} ${m.groupValues[3]}"
        }
        return s
    }
}

/**
 * 工业级成熟标准 CommonMark AST 现代富文本渲染器：
 * 采用 Google / GitHub 官方推荐的 CommonMark + GFM 扩展核心，
 * 搭载 CJK 标点粗体自适应归一化 + AST 文本行内兜底解析双重保险，
 * 彻底消灭原生 ** 字符泄露与自造轮子的排版粗糙感：
 * 
 * 1. 深度解析表格 (GFM Table) 自动适配窄屏水平滑动与垂向对齐；
 * 2. 国际标准列表 (BulletList / OrderedList) 挂起缩进排版，彻底告别糙汉 `· `；
 * 3. 独立代码块 (```lang ... ```) 提供深色现代终端风格与一键复制；
 * 4. 引用块 (> quote) 沉浸式左侧 Accent 强调条与柔和卡片；
 * 5. 多级标题与删除线 (~~strikethrough~~)；
 * 6. 支持内嵌 LaTeX 数学与工程公式 ($E=mc^2$)。
 */
@Composable
fun MarkdownRenderer(
    content: String,
    modifier: Modifier = Modifier,
    isUser: Boolean = false
) {
    if (content.isBlank()) return

    val document = remember(content) {
        val normalized = CjkMarkdownNormalizer.normalize(content)
        val extensions = listOf(
            TablesExtension.create(),
            StrikethroughExtension.create(),
            AutolinkExtension.create()
        )
        val parser = Parser.builder().extensions(extensions).build()
        parser.parse(normalized)
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
                1 -> 16.5.sp
                2 -> 15.sp
                3 -> 14.sp
                else -> 13.sp
            }
            val annotated = buildInlineAnnotatedString(node, isUser)
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 6.dp, bottom = 2.dp)
            ) {
                Text(
                    text = annotated,
                    style = TextStyle(
                        fontSize = fontSize,
                        fontWeight = FontWeight.Bold,
                        color = if (isUser) OnPrimaryWhite else Color(0xFF0F172A),
                        letterSpacing = (-0.2).sp,
                        lineHeight = (fontSize.value * 1.35f).sp
                    )
                )
                if (node.level <= 2) {
                    Spacer(modifier = Modifier.height(4.dp))
                    HorizontalDivider(
                        color = SurfaceContainerDefault.copy(alpha = 0.7f),
                        thickness = 0.6.dp
                    )
                }
            }
        }

        is Paragraph -> {
            val textContent = extractPlainNodeText(node).trim()
            if (textContent.startsWith("⚠️") || (textContent.contains("异常") && textContent.contains("高血压"))) {
                // 工业现场异常告警卡片
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
                            lineHeight = 18.sp
                        ),
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp)
                    )
                }
            } else {
                Text(
                    text = buildInlineAnnotatedString(node, isUser),
                    style = TextStyle(
                        fontSize = 13.5.sp,
                        color = if (isUser) OnPrimaryWhite else Color(0xFF1E293B),
                        lineHeight = 20.sp,
                        letterSpacing = 0.1.sp
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
                modifier = Modifier.padding(vertical = 6.dp)
            )
        }

        is BlockQuote -> {
            Surface(
                shape = RoundedCornerShape(topEnd = 8.dp, bottomEnd = 8.dp),
                color = if (isUser) Color.White.copy(alpha = 0.1f) else SurfaceContainerLow.copy(alpha = 0.6f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    Box(
                        modifier = Modifier
                            .width(3.5.dp)
                            .height(20.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(if (isUser) Color.White.copy(alpha = 0.7f) else Color(0xFF0F172A).copy(alpha = 0.6f))
                    )
                    Spacer(modifier = Modifier.width(10.dp))
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
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                var item = node.firstChild
                while (item != null) {
                    if (item is ListItem) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 1.5.dp),
                            verticalAlignment = Alignment.Top
                        ) {
                            // 国际标准设计语言微圆点 Bullet 徽标（严格对齐第一行文本垂直居中，彻底替代字符 '· '）
                            Box(
                                modifier = Modifier
                                    .padding(top = 7.5.dp, start = 4.dp, end = 9.dp)
                                    .size(5.dp)
                                    .clip(CircleShape)
                                    .background(if (isUser) OnPrimaryWhite else Color(0xFF475569))
                            )
                            Column(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(3.dp)
                            ) {
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
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                var item = node.firstChild
                while (item != null) {
                    if (item is ListItem) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 1.5.dp),
                            verticalAlignment = Alignment.Top
                        ) {
                            Text(
                                text = "$index.",
                                style = TextStyle(
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isUser) OnPrimaryWhite else Color(0xFF64748B)
                                ),
                                modifier = Modifier
                                    .widthIn(min = 20.dp)
                                    .padding(top = 1.dp, end = 6.dp)
                            )
                            Column(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(3.dp)
                            ) {
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
 * 递归构建包含加粗、行内代码、斜体、删除线和链接的 AnnotatedString，
 * 并在块级别进行成对粗体兜底清理，确保跨节点换行或复杂嵌套也绝不裸露 ** 符号。
 */
private fun buildInlineAnnotatedString(parentNode: Node, isUser: Boolean): AnnotatedString {
    val initial = buildAnnotatedString {
        appendInlineChildren(parentNode, this, isUser)
    }
    return fixUnrenderedBoldInAnnotatedString(initial)
}

// 行内与全局粗体兜底正则（容忍空白与跨节点漏网）
private val GLOBAL_FALLBACK_BOLD_REGEX = Regex("""\*\*[\t ]*([^*]+?)[\t ]*\*\*""")

private fun fixUnrenderedBoldInAnnotatedString(source: AnnotatedString): AnnotatedString {
    val rawText = source.text
    if (!rawText.contains("**")) return source
    if (!GLOBAL_FALLBACK_BOLD_REGEX.containsMatchIn(rawText)) return source

    return buildAnnotatedString {
        var cursor = 0
        GLOBAL_FALLBACK_BOLD_REGEX.findAll(rawText).forEach { match ->
            val matchStart = match.range.first
            val matchEnd = match.range.last + 1
            val innerBoldContent = match.groupValues[1]

            if (matchStart > cursor) {
                append(source.subSequence(cursor, matchStart))
            }

            pushStyle(SpanStyle(fontWeight = FontWeight.Bold))
            append(innerBoldContent)
            pop()

            cursor = matchEnd
        }
        if (cursor < rawText.length) {
            append(source.subSequence(cursor, rawText.length))
        }
    }
}

private fun appendInlineChildren(parent: Node, builder: AnnotatedString.Builder, isUser: Boolean) {
    var child = parent.firstChild
    while (child != null) {
        when (child) {
            is MdText -> {
                appendMdTextWithFallbacks(child.literal, builder, isUser)
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

            is Strikethrough -> {
                builder.pushStyle(SpanStyle(textDecoration = TextDecoration.LineThrough))
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
                builder.pushStyle(
                    SpanStyle(
                        color = if (isUser) Color(0xFF93C5FD) else Color(0xFF2563EB),
                        fontWeight = FontWeight.Medium,
                        textDecoration = TextDecoration.Underline
                    )
                )
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

/**
 * 文本节点行内兜底解析：
 * 融合 LaTeX 公式渲染与未捕获的粗体兜底，双重保险杜绝界面露底 raw **
 */
private fun appendMdTextWithFallbacks(
    literal: String,
    builder: AnnotatedString.Builder,
    isUser: Boolean
) {
    if (!literal.contains("**")) {
        LatexMathParser.appendTextWithMath(literal, builder, isUser)
        return
    }

    var lastIndex = 0
    GLOBAL_FALLBACK_BOLD_REGEX.findAll(literal).forEach { match ->
        val start = match.range.first
        val end = match.range.last + 1
        if (start > lastIndex) {
            val plain = literal.substring(lastIndex, start)
            LatexMathParser.appendTextWithMath(plain, builder, isUser)
        }
        val boldContent = match.groupValues[1]
        builder.pushStyle(SpanStyle(fontWeight = FontWeight.Bold))
        LatexMathParser.appendTextWithMath(boldContent, builder, isUser)
        builder.pop()
        lastIndex = end
    }
    if (lastIndex < literal.length) {
        val plain = literal.substring(lastIndex)
        LatexMathParser.appendTextWithMath(plain, builder, isUser)
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
 * 预计算每一列的最大内容宽度，确保表头与各数据行垂线严格对齐，支持手机横向平滑滑动
 */
@Composable
private fun AstTableCard(tableNode: TableBlock, isUser: Boolean) {
    // 1. 遍历 AST 提取表头与各数据行
    val headerCells = mutableListOf<AnnotatedString>()
    val bodyRows = mutableListOf<List<AnnotatedString>>()

    var tablePart = tableNode.firstChild
    while (tablePart != null) {
        when (tablePart) {
            is TableHead -> {
                var rowNode = tablePart.firstChild
                while (rowNode != null) {
                    if (rowNode is TableRow) {
                        var cellNode = rowNode.firstChild
                        while (cellNode != null) {
                            if (cellNode is TableCell) {
                                headerCells.add(buildInlineAnnotatedString(cellNode, isUser = false))
                            }
                            cellNode = cellNode.next
                        }
                    }
                    rowNode = rowNode.next
                }
            }

            is TableBody -> {
                var rowNode = tablePart.firstChild
                while (rowNode != null) {
                    if (rowNode is TableRow) {
                        val rowCells = mutableListOf<AnnotatedString>()
                        var cellNode = rowNode.firstChild
                        while (cellNode != null) {
                            if (cellNode is TableCell) {
                                rowCells.add(buildInlineAnnotatedString(cellNode, isUser = false))
                            }
                            cellNode = cellNode.next
                        }
                        bodyRows.add(rowCells)
                    }
                    rowNode = rowNode.next
                }
            }
        }
        tablePart = tablePart.next
    }

    // 2. 统计各列最大字符数，计算整张表每一列的统一固定宽度
    val totalCols = kotlin.math.max(headerCells.size, bodyRows.maxOfOrNull { it.size } ?: 0)
    if (totalCols == 0) return

    val colWidths = (0 until totalCols).map { colIdx ->
        val headerLen = headerCells.getOrNull(colIdx)?.text?.length ?: 0
        val maxBodyLen = bodyRows.maxOfOrNull { it.getOrNull(colIdx)?.text?.length ?: 0 } ?: 0
        val maxLen = kotlin.math.max(headerLen, maxBodyLen)
        ((maxLen * 10f) + 24f).coerceIn(90f, 230f).dp
    }

    // 3. 渲染结构化严格对齐表格卡片
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
            // 表头行 (严格按计算列宽对齐)
            if (headerCells.isNotEmpty()) {
                Row(
                    modifier = Modifier
                        .background(SurfaceContainerLow, RoundedCornerShape(4.dp))
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    for (c in 0 until totalCols) {
                        val cellText = headerCells.getOrNull(c) ?: AnnotatedString("")
                        val colW = colWidths[c]
                        Box(
                            modifier = Modifier
                                .width(colW)
                                .padding(horizontal = 8.dp, vertical = 4.dp),
                            contentAlignment = Alignment.CenterStart
                        ) {
                            Text(
                                text = cellText,
                                style = TextStyle(
                                    fontSize = 11.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = PrimaryBlack
                                )
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(3.dp))
            }

            // 数据行
            bodyRows.forEachIndexed { rIdx, rowCells ->
                val rowBg = if (rIdx % 2 == 1) SurfaceContainerLow.copy(alpha = 0.4f) else Color.Transparent
                Row(
                    modifier = Modifier
                        .background(rowBg, RoundedCornerShape(3.dp))
                        .padding(vertical = 3.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    for (c in 0 until totalCols) {
                        val cellText = rowCells.getOrNull(c) ?: AnnotatedString("")
                        val colW = colWidths[c]
                        Box(
                            modifier = Modifier
                                .width(colW)
                                .padding(horizontal = 8.dp, vertical = 4.dp),
                            contentAlignment = Alignment.CenterStart
                        ) {
                            Text(
                                text = cellText,
                                style = TextStyle(
                                    fontSize = 11.sp,
                                    color = OnSurfaceDark,
                                    lineHeight = 15.sp
                                )
                            )
                        }
                    }
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
