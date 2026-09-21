# MQTT Assistant (Android)

一款基于 Jetpack Compose 与 Material 3 构建的高性能、极简黑白风格 MQTT 调试与助手工具。

## ✨ 核心特性

- **黑白极简美学**：全局采用纯黑白灰色彩系统，界面纯粹干练；仅保留主题小圆点自定义彩色标识，彻底去除杂色与刺眼底色。
- **稳健的 MQTT 连接引擎**：
  - 内部互斥锁并发管控，彻底根除自动重连与断开时的协程争抢与闪断死锁；
  - 智能 Broker URL 归一化（自动剔除协议前缀冗余及重复端口）；
  - 支持 SSL/TLS 证书信任扩展，兼容各类自签证书与私有 Broker。
- **高级主题过滤系统（参考 PC 端设计）**：
  - 支持 **包含（Include）** 与 **排除（Exclude）** 规则；
  - 支持 MQTT 标准通配符：单层通配符 `+` 与多层通配符 `#`；
  - 排除规则具有最高优先级；提供快捷标签管理模态窗与实时拦截指示。
- **实时日志与报文管理**：
  - 支持按包含/排除规则实时过滤报文；
  - 报文统计与快速清空。

---

## 🚀 GitHub Actions 自动云端打包与 APK 下载

本项目已完整配置 GitHub Actions 自动化打包工作流（位于 [`.github/workflows/build-apk.yml`](.github/workflows/build-apk.yml)）。无需在本地安装复杂的 Android Studio、JDK 与 Android SDK 环境，代码推送到 GitHub 即可自动打包生成 APK。

### 方式一：每次 Push 或手动触发下载 Artifacts
1. 将代码推送到 GitHub 仓库（`main` 或 `master` 分支）。
2. 在仓库页面点击 **Actions** 标签页。
3. 选择 **Build Android APK** 工作流（或在右侧点击 **Run workflow** 手动触发）。
4. 等待构建完成（绿色对勾 ✅），点击进入构建详情页。
5. 在页面底部的 **Artifacts** 区域，直接点击 **MQTT-Assistant-APK** 即可下载生成的安装包（内含 `MQTT-Assistant-debug.apk`，开箱即用，直接可在手机上安装）。

### 方式二：发布 Release 版本
推送版本 Tag（如 `v1.0.0`）时，GitHub Actions 会自动在仓库的 **Releases** 页面发布新版本并将 APK 挂载为附件：
```bash
git tag v1.0.0
git push origin v1.0.0
```

---

## 🛠️ 本地运行（可选）

如需本地调试：
1. 确保安装 JDK 17 或 JDK 21。
2. 运行 `./gradlew assembleDebug`（Linux/macOS）或 `build_apk.bat` / `gradlew.bat assembleDebug`（Windows）。
3. 生成的 APK 位于 `app/build/outputs/apk/debug/app-debug.apk`。
