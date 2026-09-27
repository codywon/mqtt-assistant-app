# 《爱奥乐》蓝牙医疗体征设备 (V3.0) TSL 物模型协议包

本文档依据《爱奥乐》血压/血糖/体温 蓝牙通信协议 (V3.0) 官方规约编制，提取了现场采集网关最核心的**测量结果上报包 (0x03 结果包)**。

支持直接在 **MQTT Assistant APP** 或 **TSL Web Studio** 中复制粘贴导入，亦可在 APP 内置模板库中一键添加！

---

## 目录
1. [爱奥乐蓝牙血压计 V3.0 (Bioland BP-V3)](#1-爱奥乐蓝牙血压计-v30-bioland-bp-v3)
2. [爱奥乐蓝牙血糖仪 V3.0 (Bioland Glucose-V3)](#2-爱奥乐蓝牙血糖仪-v30-bioland-glucose-v3)
3. [爱奥乐蓝牙红外额温枪 V3.0 (Bioland Thermometer-V3)](#3-爱奥乐蓝牙红外额温枪-v30-bioland-thermometer-v3)
4. [真实报文 Hex 验证样例](#4-真实报文-hex-验证样例)

---

## 1. 爱奥乐蓝牙血压计 V3.0 (Bioland BP-V3)

### 📌 规约帧结构
- **帧头**：固定 `0x55`
- **长度**：`0x0E`（14 字节）
- **类型**：`0x03`（测量结果包）
- **时间**：年 (offset 3), 月 (offset 4), 日 (offset 5), 时 (offset 6), 分 (offset 7)
- **物理量**：
  - `收缩压 (SYS / 高压)`：Offset 9~10，`UINT16_LE`（小端序），单位 `mmHg`，正常范围 `90 ~ 140`
  - `舒张压 (DIA / 低压)`：Offset 11，`UINT8`，单位 `mmHg`，正常范围 `60 ~ 90`
  - `脉搏心率 (PULSE)`：Offset 12，`UINT8`，单位 `bpm`，正常范围 `50 ~ 100`

### 📋 可直接复制的 TSL JSON
```json
{
  "protocolId": "bioland_bp_v3",
  "name": "爱奥乐蓝牙血压计 V3.0",
  "format": "HEX",
  "matchTopic": "medical/+/blood_pressure/#",
  "builtin": false,
  "enabled": true,
  "fields": [
    {
      "identifier": "systolic",
      "name": "收缩压(高压)",
      "offset": 9,
      "length": 2,
      "type": "uint16_le",
      "scale": 1.0,
      "precision": 0,
      "unit": "mmHg",
      "warnMin": 90.0,
      "warnMax": 140.0
    },
    {
      "identifier": "diastolic",
      "name": "舒张压(低压)",
      "offset": 11,
      "length": 1,
      "type": "uint8",
      "scale": 1.0,
      "precision": 0,
      "unit": "mmHg",
      "warnMin": 60.0,
      "warnMax": 90.0
    },
    {
      "identifier": "pulse",
      "name": "脉搏心率",
      "offset": 12,
      "length": 1,
      "type": "uint8",
      "scale": 1.0,
      "precision": 0,
      "unit": "bpm",
      "warnMin": 50.0,
      "warnMax": 100.0
    },
    {
      "identifier": "meas_year",
      "name": "测量年份",
      "offset": 3,
      "length": 1,
      "type": "uint8",
      "scale": 1.0,
      "precision": 0,
      "unit": "年"
    },
    {
      "identifier": "meas_month",
      "name": "测量月份",
      "offset": 4,
      "length": 1,
      "type": "uint8",
      "scale": 1.0,
      "precision": 0,
      "unit": "月"
    },
    {
      "identifier": "meas_day",
      "name": "测量日",
      "offset": 5,
      "length": 1,
      "type": "uint8",
      "scale": 1.0,
      "precision": 0,
      "unit": "日"
    }
  ]
}
```

---

## 2. 爱奥乐蓝牙血糖仪 V3.0 (Bioland Glucose-V3)

### 📌 规约帧结构
- **帧头**：固定 `0x55`
- **长度**：`0x0C`（12 字节）
- **类型**：`0x03`（测量结果包）
- **时间**：年 (offset 3), 月 (offset 4), 日 (offset 5), 时 (offset 6), 分 (offset 7)
- **物理量**：
  - `血糖值 (mmol/L)`：Offset 9~10，`UINT16_LE`（小端序），缩放系数 `0.0556`（即原始 `mg/dL` 除以 18），精度 1 位小数，正常参考值 `3.9 ~ 6.1 mmol/L`
  - `原始血糖值 (mg/dL)`：Offset 9~10，`UINT16_LE`（小端序），缩放系数 `1.0`，正常参考值 `70 ~ 110 mg/dL`

### 📋 可直接复制的 TSL JSON
```json
{
  "protocolId": "bioland_glucose_v3",
  "name": "爱奥乐蓝牙血糖仪 V3.0",
  "format": "HEX",
  "matchTopic": "medical/+/glucose/#",
  "builtin": false,
  "enabled": true,
  "fields": [
    {
      "identifier": "glucose_mmol",
      "name": "血糖值(mmol/L)",
      "offset": 9,
      "length": 2,
      "type": "uint16_le",
      "scale": 0.0556,
      "precision": 1,
      "unit": "mmol/L",
      "warnMin": 3.9,
      "warnMax": 6.1
    },
    {
      "identifier": "glucose_mgdl",
      "name": "血糖值(mg/dL)",
      "offset": 9,
      "length": 2,
      "type": "uint16_le",
      "scale": 1.0,
      "precision": 0,
      "unit": "mg/dL",
      "warnMin": 70.0,
      "warnMax": 110.0
    },
    {
      "identifier": "meas_year",
      "name": "测量年份",
      "offset": 3,
      "length": 1,
      "type": "uint8",
      "scale": 1.0,
      "precision": 0,
      "unit": "年"
    },
    {
      "identifier": "meas_month",
      "name": "测量月份",
      "offset": 4,
      "length": 1,
      "type": "uint8",
      "scale": 1.0,
      "precision": 0,
      "unit": "月"
    },
    {
      "identifier": "meas_day",
      "name": "测量日",
      "offset": 5,
      "length": 1,
      "type": "uint8",
      "scale": 1.0,
      "precision": 0,
      "unit": "日"
    }
  ]
}
```

---

## 3. 爱奥乐蓝牙红外额温枪 V3.0 (Bioland Thermometer-V3)

### 📌 规约帧结构
- **帧头**：固定 `0x55`
- **长度**：`0x0C`（12 字节）
- **类型**：`0x03`（测量结果包）
- **时间**：年 (offset 3), 月 (offset 4), 日 (offset 5), 时 (offset 6), 分 (offset 7)
- **物理量**：
  - `体温/额温`：Offset 9~10，`UINT16_LE`（小端序），缩放系数 `0.1`（如 368 代表 36.8 ℃），精度 1 位小数，单位 `℃`，正常参考值 `36.0 ~ 37.3 ℃`

### 📋 可直接复制的 TSL JSON
```json
{
  "protocolId": "bioland_thermometer_v3",
  "name": "爱奥乐红外额温枪 V3.0",
  "format": "HEX",
  "matchTopic": "medical/+/thermometer/#",
  "builtin": false,
  "enabled": true,
  "fields": [
    {
      "identifier": "temperature",
      "name": "体温",
      "offset": 9,
      "length": 2,
      "type": "uint16_le",
      "scale": 0.1,
      "precision": 1,
      "unit": "℃",
      "warnMin": 36.0,
      "warnMax": 37.3
    },
    {
      "identifier": "meas_year",
      "name": "测量年份",
      "offset": 3,
      "length": 1,
      "type": "uint8",
      "scale": 1.0,
      "precision": 0,
      "unit": "年"
    },
    {
      "identifier": "meas_month",
      "name": "测量月份",
      "offset": 4,
      "length": 1,
      "type": "uint8",
      "scale": 1.0,
      "precision": 0,
      "unit": "月"
    },
    {
      "identifier": "meas_day",
      "name": "测量日",
      "offset": 5,
      "length": 1,
      "type": "uint8",
      "scale": 1.0,
      "precision": 0,
      "unit": "日"
    }
  ]
}
```

---

## 4. 真实报文 Hex 验证样例

### 血压计真实 Hex 样例：
- **报文**：`55 0E 03 0E 0B 08 0C 12 00 20 01 58 46 65`
- **解析结果**：
  - 时间：2014年 11月 8日 12时 18分
  - 收缩压 (高压)：`0x0120` = 288 mmHg (⚠️ 超过 140 高压告警)
  - 舒张压 (低压)：`0x58` = 88 mmHg
  - 脉搏心率：`0x46` = 70 bpm

### 血糖仪真实 Hex 样例：
- **报文**：`55 0C 03 0E 01 01 05 19 00 C3 02 57`
- **解析结果**：
  - 时间：2014年 1月 1日 05时 25分
  - 原始血糖：`0x02C3` = 707 mg/dL
  - 血糖值：`707 / 18` = **39.3 mmol/L** (⚠️ 严重超标告警)

### 红外额温枪真实 Hex 样例：
- **报文**：`55 0C 03 0E 01 01 05 19 00 70 01 57`
- **解析结果**：
  - 时间：2014年 1月 1日 05时 25分
  - 体温值：`0x0170` = 368 ➜ **36.8 ℃** (正常绿标)
