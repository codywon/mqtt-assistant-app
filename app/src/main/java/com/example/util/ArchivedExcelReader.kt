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

    /**
     * 扫描系统 Download 目录中的 MQTT 报文归档 Excel 文件列表
     */
    fun listArchivedExcels(): List<ArchivedFileInfo> {
        val list = mutableListOf<ArchivedFileInfo>()
        try {
            val downloadDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            if (downloadDir != null && downloadDir.exists() && downloadDir.isDirectory) {
                val files = downloadDir.listFiles { file ->
                    file.isFile && file.name.startsWith("MQTT_Packets_") && file.name.endsWith(".xlsx")
                }
                files?.sortedByDescending { it.lastModified() }?.forEach { f ->
                    list.add(
                        ArchivedFileInfo(
                            fileName = f.name,
                            filePath = f.absolutePath,
                            fileSizeBytes = f.length(),
                            lastModifiedTime = f.lastModified()
                        )
                    )
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "扫描归档 Excel 文件失败", e)
        }
        return list
    }

    /**
     * 流式读取指定 Excel 文件中的前 N 条报文行数据（支持关键字过滤）
     */
    fun readExcelRows(
        filePathOrName: String,
        keyword: String = "",
        maxRows: Int = 100
    ): List<ExcelPacketRow> {
        val result = mutableListOf<ExcelPacketRow>()
        val targetFile = if (File(filePathOrName).isAbsolute) {
            File(filePathOrName)
        } else {
            val downloadDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            File(downloadDir, filePathOrName)
        }

        if (!targetFile.exists()) {
            Log.w(TAG, "目标 Excel 文件不存在: ${targetFile.absolutePath}")
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
                            parseSheetXml(zipIn, sharedStrings, keyword, maxRows, result)
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

                                val row = ExcelPacketRow(seq, topic, devId, payload, time)
                                if (keyword.isBlank() || topic.contains(keyword, ignoreCase = true) || payload.contains(keyword, ignoreCase = true) || devId.contains(keyword, ignoreCase = true)) {
                                    output.add(row)
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
