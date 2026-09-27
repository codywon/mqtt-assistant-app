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
        jsonTelemetryGeneric(),
        biolandBloodPressure(),
        biolandBloodGlucose(),
        biolandThermometer(),
        biolandBodyFatScale(),
        lepuPc60Continuous(),
        lepuPc60SpotCheck(),
        smartBreakerGateway()
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

    // =========================================================================
    // 4. 爱奥乐蓝牙血压计 V3.0 (Bioland BP-V3)
    // =========================================================================

    /**
     * 测量结果帧示例：55 0E 03 0E 0B 08 0C 12 00 20 01 58 46 65
     * 对应：2014-11-08 12:18, SYS=0x0120(288 mmHg), DIA=0x58(88 mmHg), PULSE=0x46(70 bpm)
     */
    private fun biolandBloodPressure(): TslProtocol = TslProtocol(
        id = "builtin_bioland_bp_v3",
        name = "爱奥乐蓝牙血压计 V3.0",
        format = TslFormat.HEX,
        matchTopic = "medical/+/blood_pressure/#",
        builtin = true,
        enabled = true,
        fields = listOf(
            TslField(
                identifier = "systolic",
                name = "收缩压(高压)",
                offset = 9,
                length = 2,
                type = TslFieldType.UINT16_LE,
                unit = "mmHg",
                precision = 0,
                warnMin = 90.0,
                warnMax = 140.0
            ),
            TslField(
                identifier = "diastolic",
                name = "舒张压(低压)",
                offset = 11,
                length = 1,
                type = TslFieldType.UINT8,
                unit = "mmHg",
                precision = 0,
                warnMin = 60.0,
                warnMax = 90.0
            ),
            TslField(
                identifier = "pulse",
                name = "脉搏心率",
                offset = 12,
                length = 1,
                type = TslFieldType.UINT8,
                unit = "bpm",
                precision = 0,
                warnMin = 50.0,
                warnMax = 100.0
            ),
            TslField(
                identifier = "meas_year",
                name = "测量年份",
                offset = 3,
                length = 1,
                type = TslFieldType.UINT8,
                unit = "年",
                precision = 0
            ),
            TslField(
                identifier = "meas_month",
                name = "测量月份",
                offset = 4,
                length = 1,
                type = TslFieldType.UINT8,
                unit = "月",
                precision = 0
            ),
            TslField(
                identifier = "meas_day",
                name = "测量日",
                offset = 5,
                length = 1,
                type = TslFieldType.UINT8,
                unit = "日",
                precision = 0
            )
        )
    )

    // =========================================================================
    // 5. 爱奥乐蓝牙血糖仪 V3.0 (Bioland Glucose-V3)
    // =========================================================================

    /**
     * 测量结果帧示例：55 0C 03 0E 01 01 05 19 00 C3 02 57
     * 对应：02C3 = 707 mg/dL = 39.3 mmol/L (scale=0.0556 即除以 18)
     */
    private fun biolandBloodGlucose(): TslProtocol = TslProtocol(
        id = "builtin_bioland_glucose_v3",
        name = "爱奥乐蓝牙血糖仪 V3.0",
        format = TslFormat.HEX,
        matchTopic = "medical/+/glucose/#",
        builtin = true,
        enabled = true,
        fields = listOf(
            TslField(
                identifier = "glucose_mmol",
                name = "血糖值(mmol/L)",
                offset = 9,
                length = 2,
                type = TslFieldType.UINT16_LE,
                scale = 0.0556,
                unit = "mmol/L",
                precision = 1,
                warnMin = 3.9,
                warnMax = 6.1
            ),
            TslField(
                identifier = "glucose_mgdl",
                name = "血糖值(mg/dL)",
                offset = 9,
                length = 2,
                type = TslFieldType.UINT16_LE,
                scale = 1.0,
                unit = "mg/dL",
                precision = 0,
                warnMin = 70.0,
                warnMax = 110.0
            ),
            TslField(
                identifier = "meas_year",
                name = "测量年份",
                offset = 3,
                length = 1,
                type = TslFieldType.UINT8,
                unit = "年",
                precision = 0
            ),
            TslField(
                identifier = "meas_month",
                name = "测量月份",
                offset = 4,
                length = 1,
                type = TslFieldType.UINT8,
                unit = "月",
                precision = 0
            ),
            TslField(
                identifier = "meas_day",
                name = "测量日",
                offset = 5,
                length = 1,
                type = TslFieldType.UINT8,
                unit = "日",
                precision = 0
            )
        )
    )

    // =========================================================================
    // 6. 爱奥乐蓝牙红外额温枪 V3.0 (Bioland Thermometer-V3)
    // =========================================================================

    /**
     * 测量结果帧示例：55 0C 03 0E 01 01 05 19 00 70 01 57
     * 对应：0170 = 368 -> 36.8 ℃ (scale=0.1)
     */
    private fun biolandThermometer(): TslProtocol = TslProtocol(
        id = "builtin_bioland_thermometer_v3",
        name = "爱奥乐红外额温枪 V3.0",
        format = TslFormat.HEX,
        matchTopic = "medical/+/thermometer/#",
        builtin = true,
        enabled = true,
        fields = listOf(
            TslField(
                identifier = "temperature",
                name = "体温",
                offset = 9,
                length = 2,
                type = TslFieldType.UINT16_LE,
                scale = 0.1,
                unit = "℃",
                precision = 1,
                warnMin = 36.0,
                warnMax = 37.3
            ),
            TslField(
                identifier = "meas_year",
                name = "测量年份",
                offset = 3,
                length = 1,
                type = TslFieldType.UINT8,
                unit = "年",
                precision = 0
            ),
            TslField(
                identifier = "meas_month",
                name = "测量月份",
                offset = 4,
                length = 1,
                type = TslFieldType.UINT8,
                unit = "月",
                precision = 0
            ),
            TslField(
                identifier = "meas_day",
                name = "测量日",
                offset = 5,
                length = 1,
                type = TslFieldType.UINT8,
                unit = "日",
                precision = 0
            )
        )
    )

    // =========================================================================
    // 7. 蓝牙智能体脂秤广播协议 (Body Fat Scale BLE Broadcast)
    // =========================================================================

    /**
     * 蓝牙体脂秤厂商广播帧 (15 字节，以 0xC0 开头)
     * 官方示例：C0 CB 00 AD 13 7D 00 02 21 00 11 22 33 44 55
     *  - C0: 识别包头
     *  - CB: 流水号 (1~255)
     *  - 00 AD: 体重 173 -> 17.3 kg (scale=0.1, 大端 BE)
     *  - 13 7D: 电阻 4989 -> 498.9 Ω (scale=0.1, 大端 BE)
     *  - 00 02: 产品 ID
     *  - 21: 属性状态 (Bit0=1 锁定稳定数据, Bit5=1 体脂秤, Bit4-3=00 kg)
     */
    private fun biolandBodyFatScale(): TslProtocol = TslProtocol(
        id = "builtin_body_fat_scale_v1",
        name = "蓝牙智能体脂秤广播协议",
        format = TslFormat.HEX,
        matchTopic = "medical/+/body_scale/#",
        builtin = true,
        enabled = true,
        fields = listOf(
            TslField(
                identifier = "weight",
                name = "体重",
                offset = 2,
                length = 2,
                type = TslFieldType.UINT16_BE,
                scale = 0.1,
                unit = "kg",
                precision = 1,
                warnMin = 20.0,
                warnMax = 150.0
            ),
            TslField(
                identifier = "impedance",
                name = "人体阻抗",
                offset = 4,
                length = 2,
                type = TslFieldType.UINT16_BE,
                scale = 0.1,
                unit = "Ω",
                precision = 1,
                warnMin = 200.0,
                warnMax = 1200.0
            ),
            TslField(
                identifier = "is_locked",
                name = "锁定标志(稳定完成)",
                offset = 8,
                length = 1,
                type = TslFieldType.UINT8,
                scale = 1.0,
                precision = 0,
                alarmBitmask = 0x01L
            ),
            TslField(
                identifier = "seq",
                name = "流水号",
                offset = 1,
                length = 1,
                type = TslFieldType.UINT8,
                scale = 1.0,
                precision = 0
            ),
            TslField(
                identifier = "product_id",
                name = "产品ID",
                offset = 6,
                length = 2,
                type = TslFieldType.UINT16_BE,
                scale = 1.0,
                precision = 0
            )
        )
    )

    // =========================================================================
    // 8. 乐普 PC-60 连续实时监测协议 (Continuous Pulse Oximeter)
    // =========================================================================

    /**
     * 连续实时参数包 (13 字节)
     * 示例：AA 55 0F 08 01 63 58 00 5C 00 C0 67
     *  - AA 55: 帧头
     *  - 0F 08 01: 实时参数类型
     *  - 63: SpO2 = 99 %
     *  - 58 00: PR = 88 bpm (小端序 UINT16_LE)
     *  - 5C 00: PI = 92 -> 9.2 % (scale=0.1, 小端序 UINT16_LE)
     *  - 00: Status (Bit1 探头脱落告警, Bit2 寻脉状态)
     *  - C0: Battery (Bit7~6 电量等级)
     */
    private fun lepuPc60Continuous(): TslProtocol = TslProtocol(
        id = "builtin_lepu_pc60_continuous_v1",
        name = "乐普血氧仪 PC-60 连续实时监测协议",
        format = TslFormat.HEX,
        matchTopic = "medical/+/oximeter/continuous/#",
        builtin = true,
        enabled = true,
        fields = listOf(
            TslField(
                identifier = "spo2",
                name = "血氧饱和度",
                offset = 5,
                length = 1,
                type = TslFieldType.UINT8,
                scale = 1.0,
                precision = 0,
                unit = "%",
                warnMin = 90.0,
                warnMax = 100.0
            ),
            TslField(
                identifier = "pr",
                name = "脉率心率",
                offset = 6,
                length = 2,
                type = TslFieldType.UINT16_LE,
                scale = 1.0,
                precision = 0,
                unit = "bpm",
                warnMin = 50.0,
                warnMax = 120.0
            ),
            TslField(
                identifier = "pi",
                name = "血流灌注指数(PI)",
                offset = 8,
                length = 2,
                type = TslFieldType.UINT16_LE,
                scale = 0.1,
                precision = 1,
                unit = "%",
                warnMin = 0.5,
                warnMax = 20.0
            ),
            TslField(
                identifier = "probe_off",
                name = "探头脱落告警",
                offset = 10,
                length = 1,
                type = TslFieldType.UINT8,
                scale = 1.0,
                precision = 0,
                alarmBitmask = 0x02L
            ),
            TslField(
                identifier = "battery_raw",
                name = "电池电量字节",
                offset = 11,
                length = 1,
                type = TslFieldType.UINT8,
                scale = 1.0,
                precision = 0
            )
        )
    )

    // =========================================================================
    // 9. 乐普 PC-60 单次点测结果协议 (Spot-Check Result)
    // =========================================================================

    /**
     * 点测结果播报包 (10 字节)
     * 示例：AA 55 0F 06 21 01 03 62 58 FB
     *  - AA 55: 帧头
     *  - 21: 点测状态包
     *  - 03: 步骤 3 测量完成播报血氧
     *  - 62: SpO2 = 98 %
     *  - 58: PR = 88 bpm
     */
    private fun lepuPc60SpotCheck(): TslProtocol = TslProtocol(
        id = "builtin_lepu_pc60_spot_check_v1",
        name = "乐普血氧仪 PC-60 单次点测结果协议",
        format = TslFormat.HEX,
        matchTopic = "medical/+/oximeter/spot/#",
        builtin = true,
        enabled = true,
        fields = listOf(
            TslField(
                identifier = "spo2",
                name = "血氧饱和度",
                offset = 7,
                length = 1,
                type = TslFieldType.UINT8,
                scale = 1.0,
                precision = 0,
                unit = "%",
                warnMin = 90.0,
                warnMax = 100.0
            ),
            TslField(
                identifier = "pr",
                name = "脉率心率",
                offset = 8,
                length = 1,
                type = TslFieldType.UINT8,
                scale = 1.0,
                precision = 0,
                unit = "bpm",
                warnMin = 50.0,
                warnMax = 120.0
            ),
            TslField(
                identifier = "step",
                name = "点测步骤",
                offset = 6,
                length = 1,
                type = TslFieldType.UINT8,
                scale = 1.0,
                precision = 0
            )
        )
    )

    // =========================================================================
    // 10. 智能微断/断路器网关遥测协议 (Smart Breaker Gateway JSON)
    // =========================================================================

    /**
     * 智能断路器多功能网关遥测协议 (JSON 格式)
     * 支持上报：电压 (V)、电流 (A)、有功功率 (W)、累计电能 (kWh)、频率 (Hz)、功率因数、触头温度 (℃)、合分闸状态等
     * 匹配 Topic：college/breaker/# (或 power/+/breaker/#)
     */
    private fun smartBreakerGateway(): TslProtocol = TslProtocol(
        id = "builtin_smart_breaker_gateway_v1",
        name = "智能微型断路器遥测协议",
        format = TslFormat.JSON,
        matchTopic = "college/breaker/#",
        builtin = true,
        enabled = true,
        fields = listOf(
            TslField(
                identifier = "voltage",
                name = "线路电压",
                jsonPath = "breakers[0].voltage",
                type = TslFieldType.JSON_NUMBER,
                scale = 1.0,
                precision = 1,
                unit = "V",
                warnMin = 198.0,
                warnMax = 253.0
            ),
            TslField(
                identifier = "current",
                name = "负载电流",
                jsonPath = "breakers[0].current",
                type = TslFieldType.JSON_NUMBER,
                scale = 1.0,
                precision = 2,
                unit = "A",
                warnMax = 32.0
            ),
            TslField(
                identifier = "power",
                name = "有功功率",
                jsonPath = "breakers[0].power",
                type = TslFieldType.JSON_NUMBER,
                scale = 1.0,
                precision = 0,
                unit = "W",
                warnMax = 7000.0
            ),
            TslField(
                identifier = "energy",
                name = "累计电量",
                jsonPath = "breakers[0].energy",
                type = TslFieldType.JSON_NUMBER,
                scale = 1.0,
                precision = 2,
                unit = "kWh",
                isKeyIndicator = true
            ),
            TslField(
                identifier = "temperature",
                name = "触头温度",
                jsonPath = "breakers[0].temperature",
                type = TslFieldType.JSON_NUMBER,
                scale = 1.0,
                precision = 1,
                unit = "℃",
                warnMax = 70.0
            ),
            TslField(
                identifier = "frequency",
                name = "电网频率",
                jsonPath = "breakers[0].frequency",
                type = TslFieldType.JSON_NUMBER,
                scale = 1.0,
                precision = 1,
                unit = "Hz"
            ),
            TslField(
                identifier = "power_factor",
                name = "功率因数",
                jsonPath = "breakers[0].power_factor",
                type = TslFieldType.JSON_NUMBER,
                scale = 1.0,
                precision = 2,
                unit = ""
            ),
            TslField(
                identifier = "status",
                name = "开关状态",
                jsonPath = "breakers[0].status",
                type = TslFieldType.JSON_STRING,
                unit = ""
            ),
            TslField(
                identifier = "wifi_rssi",
                name = "WiFi信号",
                jsonPath = "wifi_rssi",
                type = TslFieldType.JSON_NUMBER,
                scale = 1.0,
                precision = 0,
                unit = "dBm"
            )
        )
    )
}
