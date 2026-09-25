package com.example.engine

import com.example.model.TslField
import com.example.model.TslFieldType
import com.example.model.TslFormat
import com.example.model.TslProtocol

/**
 * 内置工业行业协议模板库（开箱即用，零配置）
 *
 * 三大核心行业模板：
 * 1. 多参数健康体征采集协议 — 覆盖医疗/康养场景
 * 2. 标准 Modbus RTU 多寄存器采集协议 — 覆盖工控 PLC/变频器/传感器
 * 3. 通用 JSON 遥测上报协议 — 覆盖 WiFi/4G 智能硬件 JSON 类设备
 */
object TslBuiltinTemplates {

    /**
     * 获取所有内置协议模板（首次安装时自动注入）
     */
    fun getAll(): List<TslProtocol> = listOf(
        vitalSignsSensor(),
        modbusRtuGeneric(),
        jsonTelemetryGeneric()
    )

    // =========================================================================
    // 1. 多参数健康体征采集协议
    // =========================================================================

    /**
     * 典型医疗/康养网关 Hex 报文协议
     * 帧格式示例：AA 55 [LEN] [心率] [高压] [低压] [体温高] [体温低] [血氧] [CRC]
     * 帧头 AA 55 固定标识，第 3 字节为数据长度，第 4 字节起为有效载荷
     */
    private fun vitalSignsSensor(): TslProtocol = TslProtocol(
        id = "builtin_vital_signs_v1",
        name = "多参数健康体征网关协议",
        format = TslFormat.HEX,
        matchTopic = "+/+/vital",
        builtin = true,
        enabled = true,
        fields = listOf(
            TslField(
                identifier = "heart_rate",
                name = "心率",
                offset = 3,
                length = 1,
                type = TslFieldType.UINT8,
                unit = "bpm",
                precision = 0,
                warnMin = 50.0,
                warnMax = 120.0
            ),
            TslField(
                identifier = "systolic",
                name = "收缩压(高压)",
                offset = 4,
                length = 1,
                type = TslFieldType.UINT8,
                unit = "mmHg",
                precision = 0,
                warnMax = 140.0
            ),
            TslField(
                identifier = "diastolic",
                name = "舒张压(低压)",
                offset = 5,
                length = 1,
                type = TslFieldType.UINT8,
                unit = "mmHg",
                precision = 0,
                warnMax = 90.0
            ),
            TslField(
                identifier = "temperature",
                name = "体温",
                offset = 6,
                length = 2,
                type = TslFieldType.UINT16_BE,
                scale = 0.1,
                unit = "℃",
                precision = 1,
                warnMax = 37.3
            ),
            TslField(
                identifier = "spo2",
                name = "血氧饱和度",
                offset = 8,
                length = 1,
                type = TslFieldType.UINT8,
                unit = "%",
                precision = 0,
                warnMin = 90.0
            )
        )
    )

    // =========================================================================
    // 2. 标准 Modbus RTU 多寄存器读取响应
    // =========================================================================

    /**
     * Modbus RTU Function Code 03/04（读保持/输入寄存器）通用响应帧
     * 帧格式：[设备地址 1B] [功能码 1B] [数据长度 1B] [寄存器1-高] [寄存器1-低] [寄存器2-高] [寄存器2-低] ... [CRC 2B]
     *
     * 此模板假设 4 个连续 16 位寄存器（适配温湿度/电流电压等常见传感器）
     * 用户可按实际设备修改 offset、scale 和 unit
     */
    private fun modbusRtuGeneric(): TslProtocol = TslProtocol(
        id = "builtin_modbus_rtu_v1",
        name = "Modbus RTU 四寄存器通用采集",
        format = TslFormat.HEX,
        matchTopic = "+/modbus/+",
        builtin = true,
        enabled = true,
        fields = listOf(
            TslField(
                identifier = "slave_addr",
                name = "从站地址",
                offset = 0,
                length = 1,
                type = TslFieldType.UINT8,
                precision = 0
            ),
            TslField(
                identifier = "func_code",
                name = "功能码",
                offset = 1,
                length = 1,
                type = TslFieldType.UINT8,
                precision = 0
            ),
            TslField(
                identifier = "register_1",
                name = "寄存器1(温度)",
                offset = 3,
                length = 2,
                type = TslFieldType.INT16_BE,
                scale = 0.1,
                unit = "℃",
                precision = 1,
                warnMax = 85.0
            ),
            TslField(
                identifier = "register_2",
                name = "寄存器2(湿度)",
                offset = 5,
                length = 2,
                type = TslFieldType.UINT16_BE,
                scale = 0.1,
                unit = "%RH",
                precision = 1,
                warnMax = 95.0
            ),
            TslField(
                identifier = "register_3",
                name = "寄存器3(电压)",
                offset = 7,
                length = 2,
                type = TslFieldType.UINT16_BE,
                scale = 0.1,
                unit = "V",
                precision = 1
            ),
            TslField(
                identifier = "register_4",
                name = "寄存器4(电流)",
                offset = 9,
                length = 2,
                type = TslFieldType.UINT16_BE,
                scale = 0.01,
                unit = "A",
                precision = 2,
                warnMax = 50.0
            )
        )
    )

    // =========================================================================
    // 3. 通用 JSON 遥测上报协议
    // =========================================================================

    /**
     * 标准 JSON 遥测上报（适配大量 WiFi/4G 模组类智能硬件）
     * 报文示例：{"temperature": 25.4, "humidity": 60, "voltage": 3.72, "status": "normal"}
     */
    private fun jsonTelemetryGeneric(): TslProtocol = TslProtocol(
        id = "builtin_json_telemetry_v1",
        name = "通用 JSON 遥测上报协议",
        format = TslFormat.JSON,
        matchTopic = "+/+/telemetry",
        builtin = true,
        enabled = true,
        fields = listOf(
            TslField(
                identifier = "temperature",
                name = "温度",
                type = TslFieldType.JSON_NUMBER,
                jsonPath = "temperature",
                unit = "℃",
                precision = 1,
                warnMax = 60.0
            ),
            TslField(
                identifier = "humidity",
                name = "湿度",
                type = TslFieldType.JSON_NUMBER,
                jsonPath = "humidity",
                unit = "%RH",
                precision = 1,
                warnMax = 95.0
            ),
            TslField(
                identifier = "voltage",
                name = "电压",
                type = TslFieldType.JSON_NUMBER,
                jsonPath = "voltage",
                unit = "V",
                precision = 2
            ),
            TslField(
                identifier = "status",
                name = "设备状态",
                type = TslFieldType.JSON_STRING,
                jsonPath = "status"
            )
        )
    )
}
