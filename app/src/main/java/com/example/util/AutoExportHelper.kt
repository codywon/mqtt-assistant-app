package com.example.util

import android.content.Context
import android.util.Log
import com.example.data.MemoryPacketStore
import com.example.data.MqttStorageRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean

/**
 * 满额自动导出 Excel 归档工具（支持长时间监测、无人值守滚动分卷转储）
 * 专为纯内存高速时序流设计：当内存报文累积达到设定的阈值时，自动流式生成 Excel 导出至公共 Download 目录，
 * 并在导出成功后滑出已归档数据，开启下一轮分卷归档。彻底零 SQLite 依赖与零闪存磨损。
 */
object AutoExportHelper {

    private val isAutoExporting = AtomicBoolean(false)

    /**
     * 检查当前内存报文是否达到阈值，若达到且未在导出中，立即触发自动归档 (纯内存驱动，0 I/O 磨损)
     */
    fun checkAndExportFromMemory(
        context: Context,
        bufferThreshold: Int,
        clientId: String,
        onExportSuccess: ((exportedCount: Int, fileName: String) -> Unit)? = null
    ) {
        if (bufferThreshold <= 0) return
        val currentPackets = MemoryPacketStore.getAll()
        if (currentPackets.size < bufferThreshold) return

        if (!isAutoExporting.compareAndSet(false, true)) return

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val packetsToExport = currentPackets.take(bufferThreshold)
                var exportedCount = 0
                val fileName = ExcelExportHelper.exportStreamToPublicDownloads(context) { rowWriter ->
                    for (packet in packetsToExport) {
                        val devId = ExcelExportHelper.extractDeviceId(
                            packet.payload,
                            packet.topic,
                            clientId
                        )
                        rowWriter.writeRow(packet.topic, devId, packet.payload, packet.timestamp)
                        exportedCount++
                    }
                }

                if (exportedCount > 0) {
                    MemoryPacketStore.dropFirst(exportedCount)
                    onExportSuccess?.invoke(exportedCount, fileName)
                }
            } catch (e: Exception) {
                Log.e("AutoExportHelper", "内存满额自动导出 Excel 异常: ${e.message}", e)
            } finally {
                isAutoExporting.set(false)
            }
        }
    }

    /**
     * 兼容性适配层
     */
    fun checkAndTrigger(
        context: Context,
        storage: MqttStorageRepository?,
        bufferThreshold: Int,
        clientId: String,
        onExportSuccess: ((exportedCount: Int, fileName: String) -> Unit)? = null
    ) {
        checkAndExportFromMemory(context, bufferThreshold, clientId, onExportSuccess)
    }
}
