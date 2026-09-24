package com.example.util

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.regex.Pattern

data class WebSearchItem(
    val title: String,
    val snippet: String,
    val url: String
)

data class WebSearchResult(
    val query: String,
    val count: Int,
    val items: List<WebSearchItem>,
    val source: String,
    val error: String? = null
)

/**
 * 工业级双通道轻量联网检索引擎：
 * 1. 专为工业物联网现场调试与 SI 工程师打造，免去第三方昂贵搜索 API Key；
 * 2. 双轨搜索引擎自愈架构：默认直连微软必应 (Bing CN)，自动回退 DuckDuckGo HTML；
 * 3. 毫秒级提取规约标准（Modbus、DL/T 645、CJ/T 188、HJ 212、JT/T 808 等）、PLC 报警代码及设备参考手册；
 * 4. 纯原生 HttpURLConnection + 正则流式提取，零额外重量级依赖，支持极速网络穿透。
 */
object WebSearchHelper {

    private const val TAG = "WebSearchHelper"
    private const val USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"

    /**
     * 实时检索工业技术资料、国标规约、设备说明与故障代码（优先 Bing CN，回退 DuckDuckGo）
     */
    suspend fun search(query: String, maxResults: Int = 4): WebSearchResult = withContext(Dispatchers.IO) {
        val trimmedQuery = query.trim()
        if (trimmedQuery.isBlank()) {
            return@withContext WebSearchResult(trimmedQuery, 0, emptyList(), "None", "搜索关键词不能为空")
        }

        // 1. 优先尝试微软必应 (国内直连极速，中文工控资料与国标规范覆盖全面)
        try {
            val bingResult = searchBingCn(trimmedQuery, maxResults)
            if (bingResult.items.isNotEmpty()) {
                return@withContext bingResult
            }
        } catch (e: Exception) {
            Log.w(TAG, "Bing 搜索失败，尝试回退备用搜索引擎: ${e.message}")
        }

        // 2. 备用通道：DuckDuckGo HTML 版 (通用/海外开源规约)
        try {
            val ddgResult = searchDuckDuckGo(trimmedQuery, maxResults)
            if (ddgResult.items.isNotEmpty()) {
                return@withContext ddgResult
            }
        } catch (e: Exception) {
            Log.w(TAG, "DuckDuckGo 搜索失败: ${e.message}")
        }

        WebSearchResult(
            query = trimmedQuery,
            count = 0,
            items = emptyList(),
            source = "None",
            error = "未能获取到搜索结果，网络可能不稳定或未找到匹配资料"
        )
    }

    private fun searchBingCn(query: String, maxResults: Int): WebSearchResult {
        val encoded = URLEncoder.encode(query, "UTF-8")
        val urlStr = "https://cn.bing.com/search?q=$encoded"
        val html = fetchHtml(urlStr, mapOf("Accept-Language" to "zh-CN,zh;q=0.9,en;q=0.8"))

        val items = mutableListOf<WebSearchItem>()
        val algoPattern = Pattern.compile("<li class=\"b_algo\"[\\s\\S]*?</li>", Pattern.CASE_INSENSITIVE)
        val matcher = algoPattern.matcher(html)

        val titleUrlPattern = Pattern.compile("<h2><a[^>]*href=\"([^\"]+)\"[^>]*>(.*?)</a></h2>", Pattern.CASE_INSENSITIVE)
        val snippetPattern = Pattern.compile("<p[^>]*>(.*?)</p>", Pattern.CASE_INSENSITIVE)

        while (matcher.find() && items.size < maxResults) {
            val block = matcher.group()
            val tuMatcher = titleUrlPattern.matcher(block)
            if (tuMatcher.find()) {
                val rawUrl = tuMatcher.group(1) ?: ""
                val rawTitle = tuMatcher.group(2) ?: ""

                var rawSnippet = ""
                val snipMatcher = snippetPattern.matcher(block)
                if (snipMatcher.find()) {
                    rawSnippet = snipMatcher.group(1) ?: ""
                }

                val cleanTitle = cleanHtml(rawTitle)
                val cleanSnippet = cleanHtml(rawSnippet)

                if (cleanTitle.isNotBlank() && rawUrl.startsWith("http")) {
                    items.add(
                        WebSearchItem(
                            title = cleanTitle,
                            snippet = cleanSnippet.ifBlank { cleanTitle },
                            url = rawUrl
                        )
                    )
                }
            }
        }

        return WebSearchResult(
            query = query,
            count = items.size,
            items = items,
            source = "Bing"
        )
    }

    private fun searchDuckDuckGo(query: String, maxResults: Int): WebSearchResult {
        val encoded = URLEncoder.encode(query, "UTF-8")
        val urlStr = "https://html.duckduckgo.com/html/?q=$encoded"
        val html = fetchHtml(urlStr)

        val items = mutableListOf<WebSearchItem>()
        val blockPattern = Pattern.compile("<div class=\"result results_links[\\s\\S]*?</div>\\s*</div>", Pattern.CASE_INSENSITIVE)
        val matcher = blockPattern.matcher(html)

        val titlePattern = Pattern.compile("<a[^>]*class=\"result__a\"[^>]*href=\"([^\"]+)\"[^>]*>(.*?)</a>", Pattern.CASE_INSENSITIVE)
        val snippetPattern = Pattern.compile("<a[^>]*class=\"result__snippet\"[^>]*>(.*?)</a>", Pattern.CASE_INSENSITIVE)

        while (matcher.find() && items.size < maxResults) {
            val block = matcher.group()
            val tMatcher = titlePattern.matcher(block)
            if (tMatcher.find()) {
                val rawUrl = tMatcher.group(1) ?: ""
                val rawTitle = tMatcher.group(2) ?: ""
                var rawSnippet = ""

                val sMatcher = snippetPattern.matcher(block)
                if (sMatcher.find()) {
                    rawSnippet = sMatcher.group(1) ?: ""
                }

                var cleanUrl = rawUrl
                if (cleanUrl.contains("uddg=")) {
                    val encodedReal = cleanUrl.substringAfter("uddg=").substringBefore("&")
                    try {
                        cleanUrl = java.net.URLDecoder.decode(encodedReal, "UTF-8")
                    } catch (_: Exception) {}
                }
                if (cleanUrl.startsWith("//")) cleanUrl = "https:$cleanUrl"

                val cleanTitle = cleanHtml(rawTitle)
                val cleanSnippet = cleanHtml(rawSnippet)

                if (cleanTitle.isNotBlank()) {
                    items.add(
                        WebSearchItem(
                            title = cleanTitle,
                            snippet = cleanSnippet.ifBlank { cleanTitle },
                            url = cleanUrl
                        )
                    )
                }
            }
        }

        return WebSearchResult(
            query = query,
            count = items.size,
            items = items,
            source = "DuckDuckGo"
        )
    }

    private fun fetchHtml(urlStr: String, extraHeaders: Map<String, String> = emptyMap()): String {
        val url = URL(urlStr)
        val conn = url.openConnection() as HttpURLConnection
        conn.requestMethod = "GET"
        conn.connectTimeout = 7000
        conn.readTimeout = 7000
        conn.setRequestProperty("User-Agent", USER_AGENT)
        conn.setRequestProperty("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
        extraHeaders.forEach { (k, v) -> conn.setRequestProperty(k, v) }

        val code = conn.responseCode
        if (code !in 200..299) {
            throw IllegalStateException("HTTP 响应码异常: $code")
        }

        val reader = BufferedReader(InputStreamReader(conn.inputStream, Charsets.UTF_8))
        val sb = java.lang.StringBuilder()
        var line: String?
        while (reader.readLine().also { line = it } != null) {
            sb.append(line).append("\n")
        }
        reader.close()
        return sb.toString()
    }

    private fun cleanHtml(html: String): String {
        return html
            .replace(Regex("<[^>]*>"), "")
            .replace("&nbsp;", " ")
            .replace("&amp;", "&")
            .replace("&quot;", "\"")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&#39;", "'")
            .replace("&#0183;", "·")
            .replace("&middot;", "·")
            .replace("\\s+".toRegex(), " ")
            .trim()
    }
}
