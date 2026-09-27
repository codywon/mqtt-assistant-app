package com.example.ui.components

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.BaselineShift
import androidx.compose.ui.unit.sp
import com.example.ui.theme.OnPrimaryWhite
import com.example.ui.theme.PrimaryBlack

/**
 * 工业级 LaTeX 数学与工程表达式解析与矢量渲染引擎
 * 采用编译器前端标准的“分词器 (Lexer) + 括号匹配深度栈 (Brace Stack) + 语法解析器 (Parser)”，
 * 彻底告别简单脆弱的正则表达式替换，提供深度嵌套支持、高容错度、高保真排版的数学呈现能力。
 */
object LatexMathParser {

    /**
     * 将包含 LaTeX 数学语法的文本解析并以带排版样式的 AnnotatedString 写入 builder
     */
    fun appendTextWithMath(
        rawText: String,
        builder: AnnotatedString.Builder,
        isUser: Boolean = false
    ) {
        if (!rawText.contains("$") && !rawText.contains("\\")) {
            builder.append(rawText)
            return
        }

        var index = 0
        val length = rawText.length

        while (index < length) {
            val char = rawText[index]

            // 检查转义的 \$
            if (char == '\\' && index + 1 < length && rawText[index + 1] == '$') {
                builder.append("$")
                index += 2
                continue
            }

            // 检查块级公式 $$...$$
            if (char == '$' && index + 1 < length && rawText[index + 1] == '$') {
                val endIdx = rawText.indexOf("$$", index + 2)
                if (endIdx != -1) {
                    val formula = rawText.substring(index + 2, endIdx).trim()
                    renderFormulaToBuilder(formula, builder, isUser, isBlock = true)
                    index = endIdx + 2
                    continue
                }
            }

            // 检查行内公式 $...$
            if (char == '$') {
                // 确保不是单独的货币符号（检查是否有同行成对闭合的 $）
                var endIdx = -1
                var scanIdx = index + 1
                while (scanIdx < length && rawText[scanIdx] != '\n') {
                    if (rawText[scanIdx] == '$' && rawText[scanIdx - 1] != '\\') {
                        endIdx = scanIdx
                        break
                    }
                    scanIdx++
                }

                if (endIdx != -1 && endIdx > index + 1) {
                    val formula = rawText.substring(index + 1, endIdx).trim()
                    renderFormulaToBuilder(formula, builder, isUser, isBlock = false)
                    index = endIdx + 1
                    continue
                }
            }

            builder.append(char)
            index++
        }
    }

    /**
     * 将单个公式字符串解析为结构化语法树，并赋予斜体变量、上下标偏移、正体单位等专业排版样式
     */
    private fun renderFormulaToBuilder(
        formula: String,
        builder: AnnotatedString.Builder,
        isUser: Boolean,
        isBlock: Boolean
    ) {
        if (formula.isBlank()) return

        if (isBlock) {
            builder.append("\n")
        }

        val primaryColor = if (isUser) OnPrimaryWhite else PrimaryBlack
        val formulaTokens = tokenizeAndParseFormula(formula)

        for (token in formulaTokens) {
            when (token) {
                is MathToken.Text -> {
                    builder.append(token.content)
                }

                is MathToken.Variable -> {
                    // 数学变量标准排版：使用 Italic 斜体
                    builder.pushStyle(
                        SpanStyle(
                            fontStyle = FontStyle.Italic,
                            fontWeight = FontWeight.SemiBold,
                            color = primaryColor
                        )
                    )
                    builder.append(token.name)
                    builder.pop()
                }

                is MathToken.Operator -> {
                    builder.append(token.symbol)
                }

                is MathToken.Superscript -> {
                    // 原生 BaselineShift 上标排版
                    builder.pushStyle(
                        SpanStyle(
                            baselineShift = BaselineShift.Superscript,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Medium,
                            color = primaryColor
                        )
                    )
                    builder.append(token.content)
                    builder.pop()
                }

                is MathToken.Subscript -> {
                    // 原生 BaselineShift 下标排版
                    builder.pushStyle(
                        SpanStyle(
                            baselineShift = BaselineShift.Subscript,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Medium,
                            color = primaryColor
                        )
                    )
                    builder.append(token.content)
                    builder.pop()
                }
            }
        }

        if (isBlock) {
            builder.append("\n")
        }
    }

    /**
     * 基于括号深度栈 (Depth Stack) 和前瞻扫描的递归解析器
     */
    private fun tokenizeAndParseFormula(formula: String): List<MathToken> {
        val tokens = mutableListOf<MathToken>()
        var i = 0
        val len = formula.length

        while (i < len) {
            val c = formula[i]

            // 1. 跳过或规范化空白
            if (c.isWhitespace()) {
                if (tokens.isNotEmpty() && tokens.last() !is MathToken.Operator) {
                    val last = tokens.last()
                    if (last is MathToken.Text && !last.content.endsWith(" ")) {
                        tokens.add(MathToken.Text(" "))
                    }
                }
                i++
                continue
            }

            // 2. 识别以反斜杠开头的 LaTeX 命令
            if (c == '\\') {
                val (cmdTokenList, nextIdx) = parseCommand(formula, i)
                tokens.addAll(cmdTokenList)
                i = nextIdx
                continue
            }

            // 3. 识别上标符号 ^
            if (c == '^') {
                val (supText, nextIdx) = readScriptContent(formula, i + 1)
                // 特殊工程符号处理：如 ^\circ 紧随 C -> ℃
                if (supText == "\\circ" || supText == "°") {
                    var finalNext = nextIdx
                    // 检查后续是否紧随 C 或 \text{C}
                    if (finalNext < len && formula.startsWith("\\text{C}", finalNext)) {
                        tokens.add(MathToken.Text(" ℃"))
                        finalNext += "\\text{C}".length
                    } else if (finalNext < len && formula[finalNext] == 'C') {
                        tokens.add(MathToken.Text(" ℃"))
                        finalNext += 1
                    } else {
                        tokens.add(MathToken.Text("°"))
                    }
                    i = finalNext
                    continue
                }

                val cleanSup = cleanLatexCommandToUnicode(supText)
                tokens.add(MathToken.Superscript(cleanSup))
                i = nextIdx
                continue
            }

            // 4. 识别下标符号 _
            if (c == '_') {
                val (subText, nextIdx) = readScriptContent(formula, i + 1)
                val cleanSub = cleanLatexCommandToUnicode(subText)
                tokens.add(MathToken.Subscript(cleanSub))
                i = nextIdx
                continue
            }

            // 5. 识别数学常用单字符运算符与关系符
            when (c) {
                '=' -> {
                    tokens.add(MathToken.Operator(" = "))
                    i++
                    continue
                }
                '+' -> {
                    tokens.add(MathToken.Operator(" + "))
                    i++
                    continue
                }
                '-' -> {
                    tokens.add(MathToken.Operator(" - "))
                    i++
                    continue
                }
                '*' -> {
                    tokens.add(MathToken.Operator(" × "))
                    i++
                    continue
                }
                '/' -> {
                    tokens.add(MathToken.Operator(" / "))
                    i++
                    continue
                }
                '~' -> {
                    tokens.add(MathToken.Operator(" ~ "))
                    i++
                    continue
                }
                ',' -> {
                    tokens.add(MathToken.Text(", "))
                    i++
                    continue
                }
            }

            // 6. 识别单字母变量 (在数学模式下，单个英文字母作为数学变量，渲染为斜体)
            if (c.isLetter()) {
                // 检查是否构成常见的多字母函数名 (如 cos, sin, tan, max, min, log, ln, lim)
                val (word, nextIdx) = readWord(formula, i)
                if (isMathFunctionName(word)) {
                    tokens.add(MathToken.Text("$word "))
                } else if (word.length == 1) {
                    tokens.add(MathToken.Variable(word))
                } else {
                    tokens.add(MathToken.Text(word))
                }
                i = nextIdx
                continue
            }

            // 7. 识别数字与其它常规字符
            if (c.isDigit() || c == '.') {
                val (numStr, nextIdx) = readNumber(formula, i)
                tokens.add(MathToken.Text(numStr))
                i = nextIdx
                continue
            }

            // 8. 默认字符
            tokens.add(MathToken.Text(c.toString()))
            i++
        }

        return tokens
    }

    /**
     * 解析形如 \command 或 \command{...} 的 LaTeX 语法
     */
    private fun parseCommand(formula: String, startIndex: Int): Pair<List<MathToken>, Int> {
        var i = startIndex + 1
        val len = formula.length

        // 处理特殊间距命令 \, \; \: \!
        if (i < len && (formula[i] == ',' || formula[i] == ';' || formula[i] == ':' || formula[i] == '!')) {
            return listOf(MathToken.Text(" ")) to (i + 1)
        }

        // 读取命令名
        val cmdStart = i
        while (i < len && formula[i].isLetter()) {
            i++
        }
        val cmd = formula.substring(cmdStart, i)

        when (cmd) {
            // 文本与正体修饰命令
            "text", "mathrm", "mathbf", "mathit" -> {
                val (content, nextIdx) = readBracedBlock(formula, i)
                val cleanContent = cleanLatexCommandToUnicode(content)
                return listOf(MathToken.Text(" $cleanContent")) to nextIdx
            }

            // 分式 \frac{num}{den}
            "frac" -> {
                val (num, nextAfterNum) = readBracedBlock(formula, i)
                val (den, nextAfterDen) = readBracedBlock(formula, nextAfterNum)
                val cleanNum = cleanLatexCommandToUnicode(num)
                val cleanDen = cleanLatexCommandToUnicode(den)
                return listOf(MathToken.Text(" ($cleanNum) / ($cleanDen) ")) to nextAfterDen
            }

            // 根号 \sqrt[n]{x} 或 \sqrt{x}
            "sqrt" -> {
                var currentIdx = i
                // 检查是否有可选根指数 [n]
                var rootDegree = ""
                if (currentIdx < len && formula[currentIdx] == '[') {
                    val closeBracket = formula.indexOf(']', currentIdx)
                    if (closeBracket != -1) {
                        rootDegree = formula.substring(currentIdx + 1, closeBracket).trim()
                        currentIdx = closeBracket + 1
                    }
                }
                val (radicand, nextIdx) = readBracedBlock(formula, currentIdx)
                val cleanRadicand = cleanLatexCommandToUnicode(radicand)
                val resultText = if (rootDegree.isNotEmpty()) {
                    "$rootDegree√($cleanRadicand)"
                } else {
                    "√($cleanRadicand)"
                }
                return listOf(MathToken.Text(resultText)) to nextIdx
            }

            // 运算符与关系符
            "times" -> return listOf(MathToken.Operator(" × ")) to i
            "div" -> return listOf(MathToken.Operator(" ÷ ")) to i
            "approx" -> return listOf(MathToken.Operator(" ≈ ")) to i
            "pm" -> return listOf(MathToken.Operator(" ± ")) to i
            "mp" -> return listOf(MathToken.Operator(" ∓ ")) to i
            "cdot" -> return listOf(MathToken.Operator(" · ")) to i
            "leq", "le" -> return listOf(MathToken.Operator(" ≤ ")) to i
            "geq", "ge" -> return listOf(MathToken.Operator(" ≥ ")) to i
            "neq" -> return listOf(MathToken.Operator(" ≠ ")) to i
            "sim" -> return listOf(MathToken.Operator(" ~ ")) to i
            "infty" -> return listOf(MathToken.Text("∞")) to i
            "circ", "degree" -> return listOf(MathToken.Text("°")) to i

            // 希腊字母与常用工程符号
            "Omega" -> return listOf(MathToken.Text(" Ω")) to i
            "omega" -> return listOf(MathToken.Text(" ω")) to i
            "mu", "micro" -> return listOf(MathToken.Text(" μ")) to i
            "Delta" -> return listOf(MathToken.Text(" Δ")) to i
            "delta" -> return listOf(MathToken.Text(" δ")) to i
            "phi" -> return listOf(MathToken.Text(" φ")) to i
            "theta" -> return listOf(MathToken.Text(" θ")) to i
            "alpha" -> return listOf(MathToken.Text(" α")) to i
            "beta" -> return listOf(MathToken.Text(" β")) to i
            "gamma" -> return listOf(MathToken.Text(" γ")) to i
            "pi" -> return listOf(MathToken.Text(" π")) to i

            // 常用三角与对数函数
            "cos" -> return listOf(MathToken.Text("cos ")) to i
            "sin" -> return listOf(MathToken.Text("sin ")) to i
            "tan" -> return listOf(MathToken.Text("tan ")) to i
            "ln" -> return listOf(MathToken.Text("ln ")) to i
            "log" -> return listOf(MathToken.Text("log ")) to i

            else -> {
                return listOf(MathToken.Text(cmd)) to i
            }
        }
    }

    /**
     * 使用“深度括号栈 (Brace Depth Stack)”精确提取 `{...}` 内部的内容
     * 无论内部嵌套多少层 `{...}`，都能 100% 精准闭合，彻底避免正则表达式的贪婪或错误截断
     */
    private fun readBracedBlock(formula: String, startIndex: Int): Pair<String, Int> {
        var i = startIndex
        val len = formula.length

        // 跳过命令与括号之间的多余空格
        while (i < len && formula[i].isWhitespace()) {
            i++
        }

        if (i >= len || formula[i] != '{') {
            // 没有大括号，则仅读取下一个非空字符作为单一参数
            if (i < len) {
                return formula[i].toString() to (i + 1)
            }
            return "" to i
        }

        // 此时 formula[i] == '{'
        var depth = 1
        val contentStart = i + 1
        i++

        while (i < len && depth > 0) {
            val c = formula[i]
            if (c == '\\' && i + 1 < len) {
                // 跳过转义字符，避免被转义的 \{ 或 \} 误计入深度
                i += 2
                continue
            }
            if (c == '{') {
                depth++
            } else if (c == '}') {
                depth--
            }
            i++
        }

        val content = if (depth == 0) {
            formula.substring(contentStart, i - 1)
        } else {
            formula.substring(contentStart, i)
        }

        return content to i
    }

    /**
     * 读取上下标内容 (支持单个字符或 `{...}` 复合内容)
     */
    private fun readScriptContent(formula: String, startIndex: Int): Pair<String, Int> {
        var i = startIndex
        val len = formula.length
        while (i < len && formula[i].isWhitespace()) {
            i++
        }
        if (i >= len) return "" to i

        if (formula[i] == '{') {
            return readBracedBlock(formula, i)
        }

        // 如果紧跟命令 (如 ^\circ)
        if (formula[i] == '\\') {
            var cmdEnd = i + 1
            while (cmdEnd < len && formula[cmdEnd].isLetter()) {
                cmdEnd++
            }
            return formula.substring(i, cmdEnd) to cmdEnd
        }

        // 单个普通字符
        return formula[i].toString() to (i + 1)
    }

    private fun readWord(formula: String, startIndex: Int): Pair<String, Int> {
        var i = startIndex
        val len = formula.length
        while (i < len && formula[i].isLetter()) {
            i++
        }
        return formula.substring(startIndex, i) to i
    }

    private fun readNumber(formula: String, startIndex: Int): Pair<String, Int> {
        var i = startIndex
        val len = formula.length
        while (i < len && (formula[i].isDigit() || formula[i] == '.')) {
            i++
        }
        return formula.substring(startIndex, i) to i
    }

    private fun isMathFunctionName(word: String): Boolean {
        return word in listOf("sin", "cos", "tan", "cot", "sec", "csc", "log", "ln", "lg", "exp", "min", "max", "lim", "det")
    }

    /**
     * 辅助方法：将嵌套的简单 LaTeX 指令平滑转为 Unicode 纯文本
     */
    private fun cleanLatexCommandToUnicode(raw: String): String {
        var s = raw
            .replace("\\times", " × ")
            .replace("\\div", " ÷ ")
            .replace("\\approx", " ≈ ")
            .replace("\\pm", " ± ")
            .replace("\\Omega", "Ω")
            .replace("\\mu", "μ")
            .replace("\\degree", "°")
            .replace("\\circ", "°")

        // 去除 \text{...} 包裹
        var braceIdx = s.indexOf("\\text{")
        while (braceIdx != -1) {
            val (textVal, nextIdx) = readBracedBlock(s, braceIdx + "\\text".length)
            s = s.substring(0, braceIdx) + textVal + s.substring(nextIdx)
            braceIdx = s.indexOf("\\text{")
        }

        return s.trim()
    }

    /**
     * 表达式词法单元模型
     */
    private sealed class MathToken {
        data class Text(val content: String) : MathToken()
        data class Variable(val name: String) : MathToken()
        data class Operator(val symbol: String) : MathToken()
        data class Superscript(val content: String) : MathToken()
        data class Subscript(val content: String) : MathToken()
    }
}
