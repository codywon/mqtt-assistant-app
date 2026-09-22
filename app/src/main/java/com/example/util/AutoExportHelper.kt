package com.example.util

import android.content.Context
import android.util.Log
import com.example.data.MqttStorageRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean

/**
 * 满额自动导出 Excel 归档工具（支持长时间监测、无人值守滚动分卷转储）
 * 当报文缓存达到设定的阈值时，自动将批次报文通过游标流式生成 Excel 导出至公共 Download 目录，
 * 并在导出成功后自动清理已导出数据、执行 SQLite VACUUM 释放物理空间，开启下一轮归档。
 */
object AutoExportHelper {

    private val isAutoExporting = AtomicBoolean(false)

    /**
     * 检查当前存储报文是否达到阈值，若达到且未在导出中，立即触发自动归档
     */
    fun checkAndTrigger(
        context: Context,
        storage: MqttStorageRepository,
        bufferThreshold: Int,
        clientId: String,
        onExportSuccess: ((exportedCount: Int, fileName: String) -> Unit)? = null
    ) {
        if (bufferThreshold <= 0) return
        val currentCount = storage.getPacketCount()
        if (currentCount < bufferThreshold) return

        if (!isAutoExporting.compareAndSet(false, true)) return

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val cutoffTime = System.currentTimeMillis()
                var exportedCount = 0
                val fileName = ExcelExportHelper.exportStreamToPublicDownloads(context) { rowWriter ->
                    storage.exportPacketsStream(limit = bufferThreshold, maxCreatedAt = cutoffTime) { packet, createdAt ->
                        val timeStr = ExcelExportHelper.formatTimestamp(createdAt)
                        val devId = ExcelExportHelper.extractDeviceId(
                            packet.payload,
                            packet.topic,
                            clientId
                        )
                        rowWriter.writeRow(packet.topic, devId, packet.payload, timeStr)
                        exportedCount++
                    }
                }

                if (exportedCount > 0) {
                    // 导出成功后，精准删除已导出的历史报文并整理磁盘碎片，保留导出过程中可能新流入的数据
                    storage.deletePacketsBefore(cutoffTime)
                    storage.vacuumDatabase()
                    onExportSuccess?.invoke(exportedCount, fileName)
                }
            } catch (e: Exception) {
                Log.e("AutoExportHelper", "自动归档导出 Excel 异常: ${e.message}", e)
            } finally {
                isAutoExporting.set(false)
            }
        }
    }
}
