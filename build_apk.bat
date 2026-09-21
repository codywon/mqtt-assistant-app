@echo off
chcp 65001 > nul
echo ========================================================
echo        MQTT Assistant Android APK 一键编译脚本
echo ========================================================
echo.

where java >nul 2>nul
if %errorlevel% neq 0 (
    echo [提示] 检测到当前环境变量中未找到 'java' 命令。
    echo.
    echo 编译 Android APK 需要两项基础工具链：
    echo 1. JDK 17 或 JDK 21 (配置 JAVA_HOME)
    echo 2. Android SDK (或直接使用 Android Studio)
    echo.
    echo [推荐打包方案]：
    echo   方案一：在已安装 Android Studio 的电脑上，直接通过 "Open" 打开本项目目录：
    echo           d:\myscript\mqtt-assistant-app
    echo           点击顶部菜单栏 "Build" -^> "Build APK(s)"，即可在 1 分钟内自动生成 APK！
    echo.
    echo   方案二：若您安装了 JDK，请设置 JAVA_HOME 路径后运行：
    echo           set JAVA_HOME=C:\Path\To\Your\JDK
    echo           gradlew.bat assembleDebug
    echo.
    pause
    exit /b 1
)

echo [1/2] 检测到 Java 环境，准备调用 Gradle 构建...
call gradlew.bat assembleDebug

if %errorlevel% equ 0 (
    echo.
    echo [2/2] 构建成功！APK 生成路径如下：
    echo app\build\outputs\apk\debug\app-debug.apk
    echo ========================================================
) else (
    echo.
    echo [错误] 构建未完成，请检查 Android SDK 配置或在 Android Studio 中打开项目。
)
pause
