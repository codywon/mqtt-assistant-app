# MQTT Assistant 工程避坑与架构复用指南 (Post-Mortem & Best Practices)

> **版本**：v1.0  
> **适用范围**：移动端边缘计算应用、AI 客户端渲染引擎、高频物联网数据采集、Android 架构工程  
> **核心原则**：绝不头痛医头、杜绝零散打补丁、构建高容错体系化工程架构。

---

## 目录
1. [AI 流式 Markdown 渲染与 CJK 排版工程](#一ai-流式-markdown-渲染与-cjk-排版工程)
2. [移动端高频 MQTT 报文流水线与性能优化](#二移动端高频-mqtt-报文流水线与性能优化)
3. [Android 系统权限、保活与组件生命周期](#三android-系统权限保活与组件生命周期)
4. [CI/CD 自动化构建与终身持久化签名体系](#四cicd-自动化构建与终身持久化签名体系)
5. [工业物联网物模型 (TSL) 与协议解析引擎](#五工业物联网物模型-tsl-与协议解析引擎)
6. [移动端生命周期裂痕、半开僵尸 Socket 与四重自愈体系](#六移动端生命周期裂痕半开僵尸-socket-与四重自愈体系)

---

## 一、AI 流式 Markdown 渲染与 CJK 排版工程

### 踩坑记录 (Pitfalls)
1. **CommonMark 国际规范水土不服**：
   - *现象*：输入 `控制的**“发动机芯片”**` 或 `**（高频）**` 时，`**` 原样暴露，粗体失效。
   - *根因*：CommonMark 规范 6.2 节对强调定界符判定以西文词间空格为基准（Left/Right-flanking 规则）。中文汉字紧贴标点无空格，解析器判定为“词内字符”而拒绝开启或闭合强调。
2. **大模型（LLM）生成文本的不规范性**：
   - *现象*：大模型在粗体句尾多加了空格（如 `零冗余 **。`）或开头带空格（`** 加粗`），以及用 Unicode `• ` 代替 Markdown `- `。
   - *根因*：LLM 按 Token 概率生成，不具备编译器级语法自律；而 CommonMark 规定定界符前若有空白则绝不可作为闭合符，导致单侧无法闭合而退化为全句纯文本。
3. **“打补丁式”局部正则的副作用反噬**：
   - *现象*：早期版本为了解决标点，写了宽泛的“汉字+标点插空格”正则，结果把句号 `。` 也当成开启标点，在 `加粗**。` 中间强行塞入空格变成 `加粗 **。`，亲手把原本能闭合的语法破坏掉！
4. **AST 节点割裂导致行内替换失效**：
   - *现象*：段落内包含软换行时，CommonMark AST 将段落拆分为 `MdText` -> `SoftLineBreak` -> `MdText`。若在单个 `MdText` 节点内做正则替换，因两端成对的 `**` 分布在不同节点内，单节点正则永远匹配失败。

### 工业级最佳实践 (Best Practices)
- **三层容错渲染流水线（Tolerant Pipeline）**：
  ```
  大模型脏文本 ──> [第1层：容错清洗] ──> [第2层：官方AST标准树] ──> [第3层：原生语义渲染+块级兜底]
  ```
- **关键实施细则**：
  1. **代码块绝对隔离**：先占位提取行内代码与代码块，防止排版规则污染技术脚本；
  2. **成对定界符内侧空白剥除**：利用 `\*\*[\t ]*([^*]+?)[\t ]*\*\*`，将内部多余空格规范剥离，从源头满足 CommonMark 闭合标准；
  3. **区分开启标点与闭合标点**：开启标点（`“‘（【《`）前置补空白以开启，闭合标点（`”’）】》。！？`）后置补空白以闭合，严禁在闭合标点前插空格；
  4. **伪列表符号归一**：行首 `• / ◦ / ▪` 自动归一化为 `- `，激活官方 AST 挂起缩进微圆点；
  5. **块级后处理（Block-level Sweeper）**：在 Compose `AnnotatedString` 产出最终端执行跨节点成对扫描，作为兜底物理消除残留符号。

---

## 二、移动端高频 MQTT 报文流水线与性能优化

### 踩坑记录 (Pitfalls)
1. **主线程直接解析导致 UI 卡死与掉帧**：
   - *现象*：当工业网关每秒吐入数十条包含复杂 JSON/Hex 报文时，列表上下滑动发生严重卡顿（掉出 60 FPS），甚至触发 ANR。
   - *根因*：在 MQTT 客户端回调线程或主线程直接执行字符串截取、十六进制转换、JSON 反序列化及 SQLite 插入，阻塞了 Choreographer 编排帧率。
2. **前后台切换引发连接震荡（重连风暴）**：
   - *现象*：手机锁屏或切换到微信再切回应用时，日志频繁出现 `Connection Lost`、`Reconnecting...`，发热剧烈。
   - *根因*：Activity 生命周期（`onStop` / `onStart`）直接关联 MQTT 连接启停，导致前后台切换频繁触发 TCP 握手与 TLS 重协商。

### 工业级最佳实践 (Best Practices)
- **四级生产消费异步流水线**：
  ```
  MQTT Client ──> [无锁内存环形队列] ──> [Dispatchers.Default 批量解析] ──> [Room 协程事务批量入库] ──> [StateFlow 差量局部重组]
  ```
- **关键实施细则**：
  1. **连接与 UI 生命周期解耦**：将 MQTT 客户端常驻于前台服务（Foreground Service），Activity 仅通过 Binder/Flow 观察状态，前后台切换零重连开销；
  2. **批处理与背压缓冲（Backpressure Buffer）**：入库操作不走单条写入，采用 `Flow.chunked(50)` 或 `tickerChannel(200ms)` 执行批量 SQLite 事务，IO 开销降低 90%；
  3. **Compose 重组优化**：状态列表暴露不可变集合（`ImmutableList`），消息项采用独立 `key` 绑定报文 ID，确保新消息涌入时仅重组新增 Item，既有列表零重组。

---

## 三、Android 系统权限、保活与组件生命周期

### 踩坑记录 (Pitfalls)
1. **系统权限与应用状态割裂（伪同步）**：
   - *现象*：用户在系统设置里授予了“忽略电池优化”或“所有文件访问权限”，返回应用后，设置界面按钮依然显示“未授权”，需重启应用才刷新。
   - *根因*：UI 状态依赖了本地 SharedPreferences 缓存，而不是直接穿透读取系统底层真实接口。
2. **跨厂商 Android 11+ 存储权限兼容深渊**：
   - *现象*：传统 `WRITE_EXTERNAL_STORAGE` 在 Android 11+ 上失效，静默拒绝；部分国产定制 ROM 对 `ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION` 做了二次权限拦截。

### 工业级最佳实践 (Best Practices)
- **单一真实源（Single Source of Truth, SSOT）**：
  ```kotlin
  // 严禁依赖本地缓存标记，每次 onResume 直接向系统服务查询实时状态
  val isBatteryOptimized = remember(lifecycleOwner.lifecycle.currentState) {
      val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
      pm.isIgnoringBatteryOptimizations(context.packageName)
  }
  ```
- **关键实施细则**：
  1. **统一胶囊按钮与视觉语言**：权限授权后统一使用统一的成功色（如 Emerald/Slate 稳定配色），图标由动态操作态变为“已启用/对勾”静态展示态；
  2. **渐进式授权引导**：先通过友好 Dialog 解释为何需要前台保活与日志导出权限，再通过系统 Intent 引导至特定页面，拒绝时保留降级功能（如仅在应用私有目录存盘）。

---

## 四、CI/CD 自动化构建与终身持久化签名体系

### 踩坑记录 (Pitfalls)
1. **云端签名丢失导致覆盖安装失败**：
   - *现象*：GitHub Actions 打出的 v2.0.2 安装后，发布 v2.0.3 时手机提示“签名不一致，安装包冲突，请先卸载旧版本”。
   - *根因*：CI 脚本在构建时每次都重新调用 `keytool` 动态生成临时的 keystore，每次构建生成的公私钥对完全不同，彻底破坏了 Android 系统的应用升级安全链。
2. **Release 资产上传命名歧义**：
   - *现象*：编译机默认输出 `app-release-unsigned.apk`，用户下载后无法在非 root 手机上直接运行安装。

### 工业级最佳实践 (Best Practices)
- **终身唯一持久化正式签名机制**：
  ```
  本地生成官方正式 Keystore ──> Base64 编码 ──> 注入 GitHub Secrets (RELEASE_KEYSTORE_BASE64)
                                                             │
  GitHub Actions CI 运行时 <── 动态恢复至临时隔离目录 <────────┘
  ```
- **关键实施细则**：
  1. **云端无痕注入**：构建前动态由 Secret 恢复为 `.jks`，构建后自动安全销毁；
  2. **Release 与 Debug 双产物交付**：统一重命名为 `MQTT-Assistant-release.apk` 与 `MQTT-Assistant-debug.apk`，并通过 `softprops/action-gh-release` 挂载到 GitHub Tag 发布页面；
  3. **严格语义化 Tag 触发**：CI 仅监听 `v*` 标签 push 事件，自动执行单元测试、签名打包、生成 Changelog。

---

## 五、工业物联网物模型 (TSL) 与协议解析引擎

### 踩坑记录 (Pitfalls)
1. **硬编码偏移与协议混发的“数值灾难”**：
   - *现象*：血氧仪报文解析异常，消息卡片显示“血氧 1%，心率 24579”。
   - *根因*：设备端引入了“点测（Spot-check）”和“连续监测（Continuous）”两种协议，两者头部相同但数据有效载荷结构与字节序截然不同。解析层由于未校验协议功能码，把点测报文当作连续报文强行切片，导致高低位颠倒解析出离谱数值。
2. **物模型结构体与业务说明大文本耦合**：
   - *现象*：内存常驻物模型结构体由于包含了大段故障排查、业务背景自然语言，手机内存消耗翻倍，GC 频繁。

### 工业级最佳实践 (Best Practices)
- **执行态（TSL 物模型）与认知态（AI 知识库）严格解耦**：
  ```
  【执行态轻量化物模型 (Native C++/Kotlin)】
    · 字段ID、物理量标识 (identifier)、数据类型 (uint16_be)、缩放系数 (scale)、计算公式
    · 特征码/功能码路由表 (OpCode Routing Table)
    · 极度精炼、常驻内存，每秒处理万级报文，微秒级吞吐
  
  【认知态大语言模型知识库 (SQLite/Vector Store)】
    · 协议历史背景、故障排查手册、排错建议
    · 按需索引，当且仅当用户提问时调取局部片段，极度节省 AI 上下文与 Token
  ```
- **关键实施细则**：
  1. **协议特征码严格前置校验**：所有报文切片前必须经过 `(Header == 0xAA55) && (Length == Expected) && (CRC/Checksum Valid)` 三重门禁；
  2. **物模型元数据驱动（Data-Driven Parsing）**：禁止在代码中写死 `bytes[4].toInt() shl 8`，统一由 JSON 物模型定义声明式字段与大端/小端（Endianness），避免新增协议修改核心解析逻辑。

---

## 六、移动端生命周期裂痕、半开僵尸 Socket 与四重自愈体系

### 踩坑记录 (Pitfalls)
1. **TCP 半开死锁与连接假成功 (Half-Open Zombie Socket)**：
   - *现象*：手机锁屏或切换到微信一段时间后切回应用，消息流彻底停止接收。用户手动在顶部或设置中点击“重新连接”，界面立刻弹出“已成功连接 Broker”，但实际一条新消息都收不到。
   - *根因*：移动蜂窝/Wi-Fi 切换或息屏省电时，基站或 NAT 网关单向丢弃了映射（静默断链），TCP 没有收到 FIN/RST 报文。Paho MQTT 客户端底层的 Socket 实际已死，但应用层对象 `client.isConnected` 依然滞留在 `true`。当调用 `connect()` 时，逻辑误判为“相同配置且已连接”，直接命中缓存短路返回 `Result.success(Unit)`，导致建立了长达数小时的“虚假存活态”。
2. **内存分层恢复时状态脱节导致胶囊全灭 (Capsule Loss on State Restoration)**：
   - *现象*：切回应用或界面恢复时，历史报文卡片依然在列表中正常呈现，但所有原本能够成功解析出 TSL 物模型核心指标的“小药丸胶囊”全部瞬间消失，变成空白卡片。
   - *根因*：报文列表 `livePackets` 从单例 `MemoryPacketStore` 中恢复，但 `tslParseResults: Map<String, TslParseResult>` 仅保存在 ViewModel 内存状态流中且初始值为 `emptyMap()`。ViewModel `init` 异步读取完协议库后，**遗漏了对内存既有报文触发 `reparseAllLivePacketsWithTsl()`**，导致历史报文的所有胶囊索引全为 `null`。
3. **前台服务单方面注销且前台无拉起能力 (Orphaned Service Death)**：
   - *现象*：用户发现系统通知栏的常驻保活通知莫名消失，应用退到后台后直接被系统挂起。
   - *根因*：厂商定制省电策略在后台强制杀死了 `MqttBackgroundService` 组件，但并未杀死整个应用进程。当 Activity 切回前台时，`onAppResume()` 仅同步了网络连接，**完全没有检查 `MqttBackgroundService.isRunning` 状态**，导致通知栏与后台常驻能力彻底丢失。
4. **高频报文批处理管道裸奔崩溃 (Silent Pipeline Death)**：
   - *现象*：收到某条包含特殊畸形载荷的报文后，整个日志页面瞬间停止吞吐，无论如何重连都无法接收新消息。
   - *根因*：`startPacketBatchCollector()` 协程负责从 `Channel` 批量抽干报文并分发到 UI。该协程循环体内部包含了 TSL 解析、雷达匹配、Excel 自动转储、通知刷新等多道复杂流程，但未配置异常保护。一旦单个报文抛出异常，整个协程静默崩溃退出，导致 Channel 通道彻底积压瘫痪。

### 工业级最佳实践 (Best Practices)
- **四重立体自愈工程模式 (Quad-Layer Self-Healing)**：
  ```
  [前台切回 / 周期探活 onAppResume]
            │
            ├─► 1. 管道自愈: 检查 collectorJob.isActive，死亡时 0ms 瞬间重启消费通道
            ├─► 2. 胶囊自愈: 发现有报文但 tslParseResults 为空时，立即后台重算点亮小药丸
            ├─► 3. 通知栏自愈: 发现开启后台保活但 Service 掉线时，自动重新拉起通知栏
            └─► 4. 长连接自愈: 通过 checkPing 反射物理探测帧识破僵尸 Socket，假死时彻底重连
  ```
- **关键实施细则**：
  1. **物理探测帧识破僵尸连接**：在跳过重连前，必须调用 `checkSocketAlive()` 向底层 Paho 客户端反射调用 `checkPing` 发送实际物理探测帧。仅当探测确认存活时才可复用连接，否则必须无条件执行物理重连；
  2. **手动重连强制切断 (forceReconnect)**：用户主动触发的“重连”操作代表最高仲裁权，必须无条件传入 `forceReconnect = true`，彻底丢弃旧 Socket 对象，重新完成完整的 TCP 三次握手与 MQTT CONNECT 交互；
  3. **重连原子批量订阅 (subscribeBatch)**：无论通过何种路径完成连接，必须紧随其后以原子批处理 API `subscribeBatch(topics)` 恢复所有已启用主题订阅，严禁在异步回调中丢失订阅时序；
  4. **全管道异常吸收与隔离 (Fault-Tolerant Pipeline)**：报文批处理协程必须使用 `try-catch(Throwable)` 全局兜底，并在单项（如 TSL 解析、Excel 转储）建立局部沙箱，确保单个异常报文绝不能中断数据流。

---

## 总结：架构设计的核心军规
1. **面对外部输入（大模型输出、现场硬件报文）保持“最大宽容”**：永远假设输入数据是不标准、有噪音、带错位的，必须设置前置容错净化管道；
2. **面对内部架构（数据流转、状态源）保持“绝对纯粹”**：单一真实源、单向数据流、执行态与认知态彻底解耦；
3. **排查问题时杜绝打补丁思维**：定位到单点异常后，先往上推演三个层级——是规范冲突？是边界定义缺失？还是架构分层混乱？在根因层构建体系化防御；
4. **面对移动端生命周期保持“永久自愈”**：永远不要假设后台服务、TCP 连接、协程管道会永久存活；在每一次 `onResume` 前台唤醒点建立完备的闭环探活与秒级自愈链条。

