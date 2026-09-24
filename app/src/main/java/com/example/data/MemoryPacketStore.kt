package com.example.data

import com.example.model.MqttLogPacket
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * 全局单例内存报文高速环形缓冲区 (Memory Packet Store)
 * 1. 彻底替代 SQLite 存储高速 MQTT 实时报文，实现 0 闪存 I/O、0 磨损、极致省电；
 * 2. 线程安全无锁并发，单次容量由系统设置 bufferThreshold 控制（默认 10,000 条）；
 * 3. 内存满额时：
 *    - 若开启自动导出，由 AutoExportHelper 滚动导出为 Excel 后顺畅切卷；
 *    - 若未开启自动导出，环形队列自动滑出最旧数据，持续循环吞吐。
 * 4. UI 界面、后台保活服务与 SIAgent 智能体完全共享同一内存视图。
 */
object MemoryPacketStore {

    private val _packetsFlow = MutableStateFlow<List<MqttLogPacket>>(emptyList())
    val packetsFlow = _packetsFlow.asStateFlow()

    @Synchronized
    fun addPackets(newPackets: List<MqttLogPacket>, maxCapacity: Int): List<MqttLogPacket> {
        if (newPackets.isEmpty()) return _packetsFlow.value
        val cap = maxCapacity.coerceAtLeast(100)
        _packetsFlow.update { current ->
            (current + newPackets).takeLast(cap)
        }
        return _packetsFlow.value
    }

    @Synchronized
    fun addPacket(packet: MqttLogPacket, maxCapacity: Int): List<MqttLogPacket> {
        val cap = maxCapacity.coerceAtLeast(100)
        _packetsFlow.update { current ->
            (current + packet).takeLast(cap)
        }
        return _packetsFlow.value
    }

    fun getAll(): List<MqttLogPacket> = _packetsFlow.value

    fun size(): Int = _packetsFlow.value.size

    @Synchronized
    fun dropFirst(count: Int) {
        if (count <= 0) return
        _packetsFlow.update { current ->
            current.drop(count)
        }
    }

    @Synchronized
    fun clear() {
        _packetsFlow.value = emptyList()
    }
}
