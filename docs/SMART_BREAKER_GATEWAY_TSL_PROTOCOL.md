# 智能断路器/微断网关遥测协议 TSL 物模型包

本文档依据智能微型断路器 (Smart MCB) / 导轨式电能采集网关上报的 JSON 遥测报文编制。
涵盖**电压、电流、有功功率、累计电量、功率因数、电网频率、触头温度、合分闸状态**等核心电气指标。

已适配 **MQTT Assistant APP**（内置支持 `breakers[0].xxx` 数组索引与自动回退解析）及 **TSL Web Studio**。

---

## 目录
1. [上报报文结构分析](#1-上报报文结构分析)
2. [可直接复制粘贴的 TSL 物模型 JSON](#2-可直接复制粘贴的-tsl-物模型-json)
3. [真实报文提取验证](#3-真实报文提取验证)
4. [电气安全与越限告警配置说明](#4-电气安全与越限告警配置说明)

---

## 1. 上报报文结构分析

现场真实上报报文示例：
```json
{
  "schema_version": 1,
  "soft_ver": "v1.0.3",
  "device_id": "THE4B063BB13E8",
  "wall_clock_valid": true,
  "timestamp": "2026-09-27 21:07:20.338",
  "poll_cycle_ms": 10000,
  "wifi_rssi": -74,
  "mqtt_connected": true,
  "chunk_index": 1,
  "chunk_total": 1,
  "breakers": [
    {
      "breaker_id": "BR01",
      "modbus_address": 1,
      "voltage": 235.8,
      "current": 0,
      "power": 3,
      "energy": 81.44,
      "power_factor": 1,
      "frequency": 50,
      "status": "on",
      "temperature": 32.8,
      "alarm_flags": 0,
      "is_valid": true,
      "error_count": 0
    }
  ]
}
```

### 字段层级说明
- **顶层系统状态**：`device_id`（网关设备号）、`wifi_rssi`（信号强度）、`timestamp`（时间戳）；
- **断路器阵列 (`breakers`)**：挂载在网关下辖的 Modbus/RS485 微断列表。第 0 路断路器（BR01）包含实时电气测量值。

---

## 2. 可直接复制粘贴的 TSL 物模型 JSON

在 **MQTT Assistant APP -> 设置 -> TSL 物模型管理 -> 导入/新建**，或在 **TSL Web Studio** 中直接粘贴以下内容：

```json
{
  "protocolId": "smart_breaker_gateway_v1",
  "name": "智能断路器网关遥测协议",
  "format": "JSON",
  "matchTopic": "college/breaker/#",
  "builtin": false,
  "enabled": true,
  "fields": [
    {
      "identifier": "voltage",
      "name": "线路电压",
      "jsonPath": "breakers[0].voltage",
      "type": "json_number",
      "scale": 1.0,
      "precision": 1,
      "unit": "V",
      "warnMin": 198.0,
      "warnMax": 253.0
    },
    {
      "identifier": "current",
      "name": "相电流",
      "jsonPath": "breakers[0].current",
      "type": "json_number",
      "scale": 1.0,
      "precision": 2,
      "unit": "A",
      "warnMax": 32.0
    },
    {
      "identifier": "power",
      "name": "有功功率",
      "jsonPath": "breakers[0].power",
      "type": "json_number",
      "scale": 1.0,
      "precision": 0,
      "unit": "W",
      "warnMax": 7000.0
    },
    {
      "identifier": "energy",
      "name": "累计电量",
      "jsonPath": "breakers[0].energy",
      "type": "json_number",
      "scale": 1.0,
      "precision": 2,
      "unit": "kWh"
    },
    {
      "identifier": "temperature",
      "name": "触头温度",
      "jsonPath": "breakers[0].temperature",
      "type": "json_number",
      "scale": 1.0,
      "precision": 1,
      "unit": "℃",
      "warnMax": 70.0
    },
    {
      "identifier": "frequency",
      "name": "电网频率",
      "jsonPath": "breakers[0].frequency",
      "type": "json_number",
      "scale": 1.0,
      "precision": 1,
      "unit": "Hz"
    },
    {
      "identifier": "power_factor",
      "name": "功率因数",
      "jsonPath": "breakers[0].power_factor",
      "type": "json_number",
      "scale": 1.0,
      "precision": 2,
      "unit": ""
    },
    {
      "identifier": "status",
      "name": "开关状态",
      "jsonPath": "breakers[0].status",
      "type": "json_string",
      "unit": ""
    },
    {
      "identifier": "wifi_rssi",
      "name": "网关WiFi信号",
      "jsonPath": "wifi_rssi",
      "type": "json_number",
      "scale": 1.0,
      "precision": 0,
      "unit": "dBm"
    }
  ]
}
```

> **💡 引擎特性支持**：
> - APP 的 TSL 解析引擎原生支持 `breakers[0].xxx` 标准 JSON 路径写法；
> - 亦支持点分路径 `breakers.0.xxx`；
> - 若简写为 `voltage`，引擎会自动探测并匹配首个微断的电气值！

---

## 3. 真实报文提取验证

| 字段名称 | jsonPath | 原始值 | 提取结果 | 业务含义 |
| :--- | :--- | :--- | :--- | :--- |
| **线路电压** | `breakers[0].voltage` | `235.8` | **235.8 V** | 处于 220V±10%（198V~253V）正常市电区间 |
| **相电流** | `breakers[0].current` | `0` | **0.00 A** | 当前回路无明显负载电流 |
| **有功功率** | `breakers[0].power` | `3` | **3 W** | 待机微弱功耗 |
| **累计电量** | `breakers[0].energy` | `81.44` | **81.44 kWh** | 当前回路累计用电量（度） |
| **触头温度** | `breakers[0].temperature` | `32.8` | **32.8 ℃** | 触头温升正常（< 70℃） |
| **电网频率** | `breakers[0].frequency` | `50` | **50.0 Hz** | 50Hz 工频稳定 |
| **功率因数** | `breakers[0].power_factor`| `1` | **1.00** | 纯阻性/待机状态 |
| **开关状态** | `breakers[0].status` | `"on"` | **ON** (合闸通电) | 回路处于合闸工作状态 |
| **WiFi信号** | `wifi_rssi` | `-74` | **-74 dBm** | 网关无线连接正常 |

---

## 4. 电气安全与越限告警配置说明

1. **过欠压告警 (`warnMin = 198.0, warnMax = 253.0`)**：
   - 依照国家电网供电电压偏差标准（220V +7%/-10%），低于 198V 或高于 253V 将在 APP 消息流中立即标红告警。
2. **过载告警 (`warnMax = 32.0`)**：
   - 可根据微断额定规格（如 16A / 20A / 32A / 63A）灵活调整；达到上限时提醒可能发生过载跳闸。
3. **触头温升告警 (`warnMax = 70.0`)**：
   - 导线接线端子虚接、松动或接触氧化会导致温度急剧升高，超过 70℃ 时立即告警，有效防范电气火灾。
