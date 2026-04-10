# Gist·简研

移动端 AI 研究助手。核心目标是把链接、图片、截图、语音和灵感统一采集进系统，再整理成可持续管理的研究卡片。

## 当前保留的主入口

- `README.md`
  用于快速了解项目、运行方式和当前主要功能。

## 当前主要能力

- 统一管理 `paper / article / competition / insight / voice`
- 支持链接导入、图片导入、OCR 整理、悬浮窗采集
- 支持灵感录入，包含标题、正文、图片、原始语音
- 支持详情页 AI 问答、结构化阅读卡、灵感反查、双文献对比
- 支持项目背景、项目总览、AI 项目总结和 Markdown 导出
- Room 本地优先，NocoDB 远端同步，SiliconFlow 提供 AI 能力

## 技术栈

- Android: Kotlin + Jetpack Compose
- Hybrid UI: WebView + React/Tailwind
- Architecture: MVVM + Clean Architecture
- Local: Room + DataStore + EncryptedSharedPreferences
- Network: Retrofit + OkHttp
- AI: SiliconFlow

## 快速开始

### 环境要求

- Android Studio
- JDK 17
- Android SDK 34+

### 本地运行

1. 打开项目并等待 Gradle 同步完成。
2. 在根目录配置 `.env.local`。
3. 连接 Android 设备或启动模拟器。
4. 运行 `app` 模块。

## 配置项

在 `.env.local` 或系统环境变量中提供：

- `AI4RESEARCH_NOCO_BASE_URL`
- `AI4RESEARCH_NOCO_TOKEN`
- `AI4RESEARCH_SILICONFLOW_BASE_URL`
- `AI4RESEARCH_SILICONFLOW_API_KEY`

示例可参考 `.env.local.example`。

## 目录结构

```text
app/                       Android 主工程
gradle/                    Gradle Wrapper 与版本配置
build.gradle.kts           根构建文件
settings.gradle.kts        Gradle 模块入口
gradlew / gradlew.bat      构建脚本
.env.local.example         环境变量示例
```

## 权限说明

- `INTERNET`：访问远端接口与 AI 服务
- `RECORD_AUDIO`：语音采集
- `SYSTEM_ALERT_WINDOW`：悬浮窗助手
- `FOREGROUND_SERVICE`：悬浮窗与后台任务
- `FOREGROUND_SERVICE_MEDIA_PROJECTION`：截图采集
- `POST_NOTIFICATIONS`：通知
