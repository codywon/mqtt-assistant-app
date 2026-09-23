package com.example.util

import android.os.Environment
import android.util.Log
import java.io.File
import java.io.InputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory

/**
 * 针对已归档 Excel (.xlsx) 的轻量流式穿透分析工具：
 * 1. 专门用于穿透扫描和读取由本应用满额自动转储到系统 Download 目录的 MQTT_Packets_*.xlsx 文件；
 * 2. 纯原生标准 OpenXML Zip 解包，零第三方臃肿库（内存 < 2MB）；
 * 3. 供 SI Agent 自由调阅历史归档数据，实现跨越在库数据与离线文件的全生命周期分析。
 */
object ArchivedExcelReader {

    private const val TAG = "ArchivedExcelReader"

    data class ArchivedFileInfo(
        val fileName: String,
        val filePath: String,
        val fileSizeBytes: Long,
        val lastModifiedTime: Long
    )

    data class ExcelPacketRow(
        val seq: String,
        val topic: String,
        val deviceId: String,
        val payload: String,
        val time: String
    )

    data class ExcelSummaryResult(
        val fileName: String,
        val totalRows: Int,
        val startTime: String,
        val endTime: String,
        val topTopics: Map<String, Int>,
        val uniqueDevices: List<String>
    )

    /**
     * 扫描系统 Download 目录中的 MQTT 报文归档 Excel 文件列表
     * 支持大小写不敏感与常见包含 mqtt / packet 或所有 .xlsx 文件
     */
    fun listArchivedExcels(): List<ArchivedFileInfo> {
        val foundMap = mutableMapOf<String, ArchivedFileInfo>()
        val candidateDirs = mutableListOf<File>()
        try {
            val downloadDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            if (downloadDir != null && downloadDir.exists() && downloadDir.isDirectory) {
                candidateDirs.add(downloadDir)
            }
        } catch (_: Exception) {}

        for (dir in candidateDirs) {
            try {
                // 放宽过滤条件：大小写不敏感匹配所有 .xlsx 格式文件，无论命名是 mqtt_packets_ 还是其他
                val files = dir.listFiles { file ->
                    file.isFile && file.name.endsWith(".xlsx", ignoreCase = true)
                }
                files?.forEach { f ->
                    if (!foundMap.containsKey(f.name)) {
                        foundMap[f.name] = ArchivedFileInfo(
                            fileName = f.name,
                            filePath = f.absolutePath,
                            fileSizeBytes = f.length(),
                            lastModifiedTime = f.lastModified()
                        )
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "扫描目录 ${dir.absolutePath} 失败", e)
            }
        }
        return foundMap.values.sortedByDescending { it.lastModifiedTime }
    }

    /**
     * 极速流式宏观概况分析 (毫秒级穿透 10,000 行，提取总量、起止时间与主题占比，零 Token 浪费防上下文爆炸)
     */
    fun analyzeExcelSummary(filePathOrName: String): ExcelSummaryResult? {
        val targetFile = resolveTargetFile(filePathOrName) ?: return null
        val sharedStrings = readSharedStrings(targetFile)

        var total = 0
        var firstTime = ""
        var lastTime = ""
        val topicCountMap = mutableMapOf<String, Int>()
        val deviceSet = mutableSetOf<String>()

        try {
            targetFile.inputStream().use { fileIn ->
                ZipInputStream(fileIn).use { zipIn ->
                    var entry: ZipEntry? = zipIn.nextEntry
                    while (entry != null) {
                        if (entry.name == "xl/worksheets/sheet1.xml") {
                            val factory = XmlPullParserFactory.newInstance()
                            val parser = factory.newPullParser()
                            parser.setInput(zipIn, "UTF-8")

                            var eventType = parser.eventType
                            var isHeader = true
                            var currentCellType: String? = null
                            var currentCellValue: StringBuilder? = null
                            val currentRowCells = mutableListOf<String>()

                            while (eventType != XmlPullParser.END_DOCUMENT) {
                                when (eventType) {
                                    XmlPullParser.START_TAG -> {
                                        when (parser.name) {
                                            "row" -> currentRowCells.clear()
                                            "c" -> {
                                                currentCellType = parser.getAttributeValue(null, "t")
                                                currentCellValue = StringBuilder()
                                            }
                                        }
                                    }
                                    XmlPullParser.TEXT -> {
                                        currentCellValue?.append(parser.text)
                                    }
                                    XmlPullParser.END_TAG -> {
                                        when (parser.name) {
                                            "c" -> {
                                                val raw = currentCellValue?.toString()?.trim() ?: ""
                                                val cellText = if (currentCellType == "s") {
                                                    val idx = raw.toIntOrNull()
                                                    if (idx != null && idx in sharedStrings.indices) sharedStrings[idx] else raw
                                                } else {
                                                    raw
                                                }
                                                currentRowCells.add(cellText)
                                                currentCellValue = null
                                                currentCellType = null
                                            }
                                            "row" -> {
                                                if (isHeader) {
                                                    isHeader = false
                                                } else if (currentRowCells.isNotEmpty()) {
                                                    total++
                                                    val topic = currentRowCells.getOrNull(1) ?: ""
                                                    val devId = currentRowCells.getOrNull(2) ?: ""
                                                    val time = currentRowCells.getOrNull(4) ?: ""

                                                    if (firstTime.isEmpty() && time.isNotEmpty()) firstTime = time
                                                    if (time.isNotEmpty()) lastTime = time
                                                    if (topic.isNotEmpty()) {
                                                        topicCountMap[topic] = (topicCountMap[topic] ?: 0) + 1
                                                    }
                                                    if (devId.isNotEmpty() && deviceSet.size < 30) {
                                                        deviceSet.add(devId)
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                                eventType = parser.next()
                            }
                            break
                        }
                        zipIn.closeEntry()
                        entry = zipIn.nextEntry
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "解析 Excel Summary 异常: ${e.message}", e)
            return null
        }

        // 取前 10 大热门主题
        val top10Topics = topicCountMap.entries
            .sortedByDescending { it.value }
            .take(10)
            .associate { it.key to it.value }

        return ExcelSummaryResult(
            fileName = targetFile.name,
            totalRows = total,
            startTime = firstTime,
            endTime = lastTime,
            topTopics = top10Topics,
            uniqueDevices = deviceSet.toList()
        )
    }

    private fun resolveTargetFile(filePathOrName: String): File? {
        val direct = File(filePathOrName)
        if (direct.isAbsolute && direct.exists()) return direct

        val downloadDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        val inDownload = File(downloadDir, filePathOrName)
        if (inDownload.exists()) return inDownload

        // 模糊搜索不区分大小写匹配
        val fuzzy = downloadDir.listFiles()?.firstOrNull {
            it.name.equals(filePathOrName, ignoreCase = true) || it.name.contains(filePathOrName, ignoreCase = true)
        }
        if (fuzzy != null && fuzzy.exists()) return fuzzy

        return null
    }

    /**
     * 流式读取指定 Excel 文件中的前 N 条报文行数据（支持关键字过滤与安全按需分页，杜绝爆上下文）
     */
    fun readExcelRows(
        filePathOrName: String,
        keyword: String = "",
        maxRows: Int = 30,
        offset: Int = 0
    ): List<ExcelPacketRow> {
        val result = mutableListOf<ExcelPacketRow>()
        val targetFile = resolveTargetFile(filePathOrName)

        if (targetFile == null || !targetFile.exists()) {
            Log.w(TAG, "目标 Excel 文件不存在: $filePathOrName")
            return emptyList()
        }

        try {
            // 第一步：先读取 sharedStrings.xml 获取共享字符串映射
            val sharedStrings = readSharedStrings(targetFile)

            // 第二步：流式解析 sheet1.xml
            targetFile.inputStream().use { fileIn ->
                ZipInputStream(fileIn).use { zipIn ->
                    var entry: ZipEntry? = zipIn.nextEntry
                    while (entry != null) {
                        if (entry.name == "xl/worksheets/sheet1.xml") {
                            parseSheetXml(zipIn, sharedStrings, keyword, maxRows, offset, result)
                            break
                        }
                        zipIn.closeEntry()
                        entry = zipIn.nextEntry
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "解析 Excel 文件行异常: ${e.message}", e)
        }

        return result
    }

    private fun readSharedStrings(file: File): List<String> {
        val strings = mutableListOf<String>()
        try {
            file.inputStream().use { fileIn ->
                ZipInputStream(fileIn).use { zipIn ->
                    var entry: ZipEntry? = zipIn.nextEntry
                    while (entry != null) {
                        if (entry.name == "xl/sharedStrings.xml") {
                            val factory = XmlPullParserFactory.newInstance()
                            val parser = factory.newPullParser()
                            parser.setInput(zipIn, "UTF-8")

                            var eventType = parser.eventType
                            var currentText: StringBuilder? = null
                            while (eventType != XmlPullParser.END_DOCUMENT) {
                                when (eventType) {
                                    XmlPullParser.START_TAG -> {
                                        if (parser.name == "t") {
                                            currentText = StringBuilder()
                                        }
                                    }
                                    XmlPullParser.TEXT -> {
                                        currentText?.append(parser.text)
                                    }
                                    XmlPullParser.END_TAG -> {
                                        if (parser.name == "t" && currentText != null) {
                                            strings.add(currentText.toString())
                                            currentText = null
                                        }
                                    }
                                }
                                eventType = parser.next()
                            }
                            break
                        }
                        zipIn.closeEntry()
                        entry = zipIn.nextEntry
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "读取 sharedStrings 异常", e)
        }
        return strings
    }

    private fun parseSheetXml(
        stream: InputStream,
        sharedStrings: List<String>,
        keyword: String,
        maxRows: Int,
        offset: Int,
        output: MutableList<ExcelPacketRow>
    ) {
        val factory = XmlPullParserFactory.newInstance()
        val parser = factory.newPullParser()
        parser.setInput(stream, "UTF-8")

        var eventType = parser.eventType
        var isHeader = true
        var currentCellType: String? = null
        var currentCellValue: StringBuilder? = null
        val currentRowCells = mutableListOf<String>()
        var matchCount = 0

        while (eventType != XmlPullParser.END_DOCUMENT && output.size < maxRows) {
            when (eventType) {
                XmlPullParser.START_TAG -> {
                    when (parser.name) {
                        "row" -> {
                            currentRowCells.clear()
                        }
                        "c" -> {
                            currentCellType = parser.getAttributeValue(null, "t")
                            currentCellValue = StringBuilder()
                        }
                    }
                }
                XmlPullParser.TEXT -> {
                    currentCellValue?.append(parser.text)
                }
                XmlPullParser.END_TAG -> {
                    when (parser.name) {
                        "c" -> {
                            val raw = currentCellValue?.toString()?.trim() ?: ""
                            val cellText = if (currentCellType == "s") {
                                val idx = raw.toIntOrNull()
                                if (idx != null && idx in sharedStrings.indices) sharedStrings[idx] else raw
                            } else {
                                raw
                            }
                            currentRowCells.add(cellText)
                            currentCellValue = null
                            currentCellType = null
                        }
                        "row" -> {
                            if (isHeader) {
                                isHeader = false // 跳过表头行 (序号, 主题, 设备ID, 报文内容, 接收时间)
                            } else if (currentRowCells.isNotEmpty()) {
                                val seq = currentRowCells.getOrNull(0) ?: ""
                                val topic = currentRowCells.getOrNull(1) ?: ""
                                val devId = currentRowCells.getOrNull(2) ?: ""
                                val payload = currentRowCells.getOrNull(3) ?: ""
                                val time = currentRowCells.getOrNull(4) ?: ""

                                if (keyword.isBlank() || topic.contains(keyword, ignoreCase = true) || payload.contains(keyword, ignoreCase = true) || devId.contains(keyword, ignoreCase = true)) {
                                    if (matchCount >= offset) {
                                        val row = ExcelPacketRow(seq, topic, devId, payload, time)
                                        output.add(row)
                                    }
                                    matchCount++
                                }
                            }
                        }
                    }
                }
            }
            eventType = parser.next()
        }
    }
}
