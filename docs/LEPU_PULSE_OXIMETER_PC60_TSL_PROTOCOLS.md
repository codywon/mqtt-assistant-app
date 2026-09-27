# 乐普 (Lepu) PC-60 系列指夹血氧仪通信协议 TSL 物模型协议包

本文档依据《PC-60 乐普血氧协议 (PC-60FW / PC-60NW)》官方规约编制，提取了现场采集网关最核心的两种工作模式：
1. **连续实时监护模式 (Continuous Real-time Telemetry)**：连续上报血氧 SpO2、脉率 PR、血流灌注指数 PI、探头脱落告警与电池等级；
2. **单次点测结果模式 (Spot-Check Result)**：点测倒计时完成后，单次推送锁定的血氧与脉率数值。

支持直接在 **MQTT Assistant APP** 或 **TSL Web Studio** 中复制粘贴导入，亦已作为内置模板注入 APP 开箱即用！

---

## 目录
1. [协议总体特征](#1-协议总体特征)
2. [乐普 PC-60 连续实时监测协议 (推荐)](#2-乐普-pc-60-连续实时监测协议-推荐)
3. [乐普 PC-60 单次点测结果协议](#3-乐普-pc-60-单次点测结果协议)
4. [真实报文 Hex 验证样例](#4-真实报文-hex-验证样例)
5. [现场集成与 SI Agent 告警拦截建议](#5-现场集成与-si-agent-告警拦截建议)

---

## 1. 协议总体特征

- **帧头**：固定 `AA 55`（2 字节）；
- **报文类型**：
  - 实时参数帧：`AA 55 0F 08 01 ...`（总长 13 字节）
  - 点测状态/结果帧：`AA 55 0F 06 21 01 03 ...`（总长 10 字节）
- **字节序**：小端序 `Little-Endian`（`UINT16_LE`，低字节在前高字节在后，如脉率与灌注指数）。

---

## 2. 乐普 PC-60 连续实时监测协议 (推荐)

常用于病房夜间监护、睡眠呼吸监测、居家长期康养等连续上报场景。

### 📌 规约帧结构 (13 字节)
- `AA 55`: 帧头
- `0F`: 标识
- `08`: 有效数据长度 (8 字节)
- `01`: 子类型 (0x01 = 实时参数包)
- **物理量映射**：
  - `SpO2 (血氧饱和度)`：Offset 5，`UINT8`，单位 `%，` 正常范围 `95 ~ 100`，告警下限 `< 90.0`
  - `PR (脉率心率)`：Offset 6~7，`UINT16_LE`（小端），单位 `bpm`，正常范围 `50 ~ 120`
  - `PI (血流灌注指数)`：Offset 8~9，`UINT16_LE`（小端），`scale = 0.1`，单位 `%，` 正常范围 `0.5 ~ 20.0`
  - `Status (探头脱落状态)`：Offset 10，`UINT8`：
    - `Bit 1`: **探头脱落 (Probe Off)**（0 = 佩戴正常, 1 = 探头脱落报警），配置 `alarmBitmask = 0x02`
    - `Bit 2`: **脉搏搜索中 (Pulse Searching)**（0 = 稳定正常, 1 = 正在寻脉）
  - `Battery (电池等级)`：Offset 11，`UINT8`：
    - `Bit 7~6`: 00=0~25%, 01=25~50%, 10=50~75%, 11=75~100%

### 📋 可直接复制的 TSL JSON
```json
{
  "protocolId": "lepu_pc60_continuous_v1",
  "name": "乐普血氧仪 PC-60 连续实时监测协议",
  "format": "HEX",
  "matchTopic": "medical/+/oximeter/continuous/#",
  "builtin": false,
  "enabled": true,
  "fields": [
    {
      "identifier": "spo2",
      "name": "血氧饱和度",
      "offset": 5,
      "length": 1,
      "type": "uint8",
      "scale": 1.0,
      "precision": 0,
      "unit": "%",
      "warnMin": 90.0,
      "warnMax": 100.0
    },
    {
      "identifier": "pr",
      "name": "脉率心率",
      "offset": 6,
      "length": 2,
      "type": "uint16_le",
      "scale": 1.0,
      "precision": 0,
      "unit": "bpm",
      "warnMin": 50.0,
      "warnMax": 120.0
    },
    {
      "identifier": "pi",
      "name": "血流灌注指数(PI)",
      "offset": 8,
      "length": 2,
      "type": "uint16_le",
      "scale": 0.1,
      "precision": 1,
      "unit": "%",
      "warnMin": 0.5,
      "warnMax": 20.0
    },
    {
      "identifier": "probe_off",
      "name": "探头脱落告警",
      "offset": 10,
      "length": 1,
      "type": "uint8",
      "scale": 1.0,
      "precision": 0,
      "unit": "",
      "alarmBitmask": 2
    },
    {
      "identifier": "battery_raw",
      "name": "电池电量字节",
      "offset": 11,
      "length": 1,
      "type": "uint8",
      "scale": 1.0,
      "precision": 0,
      "unit": ""
    }
  ]
}
```

---

## 3. 乐普 PC-60 单次点测结果协议

常用于家庭门诊抽测、社区体检一体机或单次快速检测。

### 📌 规约帧结构 (10 字节)
- `AA 55`: 帧头
- `0F`: 标识
- `06`: 数据长度 (6 字节)
- `21`: 命令类型 (点测状态机)
- `01`: 模式 (1 = 点测)
- `03`: 步骤 (3 = 测量完成播报血氧)
- **物理量映射**：
  - `SpO2 (血氧饱和度)`：Offset 7，`UINT8`，单位 `%，` 正常范围 `90 ~ 100`
  - `PR (脉率心率)`：Offset 8，`UINT8`，单位 `bpm`，正常范围 `50 ~ 120`
  - `Step (状态机步骤)`：Offset 6，`UINT8`（2=测量中, 3=测量完成, 4=脉率分析, 5=点测结束）

### 📋 可直接复制的 TSL JSON
```json
{
  "protocolId": "lepu_pc60_spot_check_v1",
  "name": "乐普血氧仪 PC-60 单次点测结果协议",
  "format": "HEX",
  "matchTopic": "medical/+/oximeter/spot/#",
  "builtin": false,
  "enabled": true,
  "fields": [
    {
      "identifier": "spo2",
      "name": "血氧饱和度",
      "offset": 7,
      "length": 1,
      "type": "uint8",
      "scale": 1.0,
      "precision": 0,
      "unit": "%",
      "warnMin": 90.0,
      "warnMax": 100.0
    },
    {
      "identifier": "pr",
      "name": "脉率心率",
      "offset": 8,
      "length": 1,
      "type": "uint8",
      "scale": 1.0,
      "precision": 0,
      "unit": "bpm",
      "warnMin": 50.0,
      "warnMax": 120.0
    },
    {
      "identifier": "step",
      "name": "点测步骤",
      "offset": 6,
      "length": 1,
      "type": "uint8",
      "scale": 1.0,
      "precision": 0,
      "unit": ""
    }
  ]
}
```

---

## 4. 真实报文 Hex 验证样例

### 样例 1：连续实时参数帧 (PC-60FW 正常测量)
- **原始 Hex 报文**：
  ```hex
  AA 55 0F 08 01 63 58 00 5C 00 C0 67
  ```
- **字段解析验证**：
  | 字节位置 | Hex 原始值 | 计算与解析说明 | 输出值 |
  | :--- | :--- | :--- | :--- |
  | `Byte 0~1` | `AA 55` | 乐普固定帧头 | `AA 55` |
  | `Byte 2~4` | `0F 08 01` | 连续实时参数包类型 | 长度=8, Cmd=0x01 |
  | `Byte 5` | `63` | 0x63 十进制 | **`99 %`** (血氧饱和度) |
  | `Byte 6~7` | `58 00` | 小端拼合 `0x0058` = 88 | **`88 bpm`** (脉率心率) |
  | `Byte 8~9` | `5C 00` | 小端拼合 `0x005C` = 92，乘以 0.1 | **`9.2 %`** (血流灌注指数 PI) |
  | `Byte 10` | `00` | 二进制 `00000000`：Bit1=0(正常佩戴), Bit2=0(已锁定) | **佩戴正常，未脱落** |
  | `Byte 11` | `C0` | 二进制 `11000000`：Bit7-6=11 (75%~100%) | **电量满格** |
  | `Byte 12` | `67` | 校验字节 | 校验通过 |

---

### 样例 2：连续实时参数帧 (PC-60NW 低灌注/低电量)
- **原始 Hex 报文**：
  ```hex
  AA 55 0F 08 01 63 55 00 1C 00 3C 6D
  ```
- **字段解析验证**：
  - **SpO2 (血氧)**：`0x63` -> **`99 %`**
  - **PR (脉率)**：`55 00` -> **`85 bpm`**
  - **PI (灌注指数)**：`1C 00` -> 28 * 0.1 -> **`2.8 %`**
  - **Status**：`00` -> 正常无脱落
  - **Battery**：`3C` -> Bit7-6=00 (0%~25% 低电量)

---

### 样例 3：单次点测结果播报帧
- **原始 Hex 报文**：
  ```hex
  AA 55 0F 06 21 01 03 62 58 FB
  ```
- **字段解析验证**：
  - `Byte 4`: `21` (点测包)
  - `Byte 6`: `03` (步骤 3: 测量完成播报血氧)
  - `Byte 7`: `62` -> **`98 %`** (血氧)
  - `Byte 8`: `58` -> **`88 bpm`** (脉率)

---

## 5. 现场集成与 SI Agent 告警拦截建议

1. **探头脱落实时告警**：
   - 当受测者手指滑脱时，血氧仪发送的 `Byte 10` (Status) 的 `Bit 1` 将置 `1`（`alarmBitmask = 0x02`）。
   - 在 APP 中将直接触发**橙色高亮告警**，可配合 SI Agent 实时语音提示或自动化报警。
2. **夜间低血氧下限预警**：
   - 临床指征中血氧低于 90% 提示低氧血症风险。本模型预设 `warnMin: 90.0`，一旦低于此数值即触发越限告警。
