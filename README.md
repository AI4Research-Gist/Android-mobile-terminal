# AI4Research

<p align="center">
  <img src="app/src/main/assets/logo.png" alt="AI4Research Logo" width="120"/>
</p>

<p align="center">
  <b>Photon Lab · 你的个人 AI 研究助手（Android）</b>
</p>

<p align="center">
  <img src="https://img.shields.io/badge/Platform-Android-green.svg"/>
  <img src="https://img.shields.io/badge/Language-Kotlin-blue.svg"/>
  <img src="https://img.shields.io/badge/UI-Hybrid%20(WebView%20%2B%20Compose)-orange.svg"/>
  <img src="https://img.shields.io/badge/Architecture-MVVM%20%2B%20Clean-purple.svg"/>
  <img src="https://img.shields.io/badge/Min%20SDK-26-gray.svg"/>
</p>

---

## 项目简介

AI4Research 是面向科研/学习场景的信息采集与分析助手。移动端负责“入口与轻处理”，云端负责“结构化与 AI 解析”，本地负责“流畅展示与离线缓存”。应用采用 Hybrid UI：主列表与交互使用 WebView 内的 React/Tailwind 页面，详情页与系统能力使用原生 Compose。

## 当前功能（以代码实现为准）

- 混合 UI：`assets/main_ui.html` + `assets/login.html` 作为主/登录界面，详情页为 Compose，统一导航过渡。
- 研究卡片管理：paper / competition / insight / voice 统一列表，支持搜索、过滤、项目归属、星标、阅读状态。
- 详情页：Markdown 渲染、编辑保存、标记已读/星标/删除、项目归属同步。
- 账号系统：NocoDB 用户表注册/登录；本地使用 EncryptedSharedPreferences 缓存 token。
- 悬浮窗助手：全局悬浮球，支持全屏/区域截图、剪贴板链接检测、手动输入链接；调用 AI 解析并入库。
- AI 能力：SiliconFlow（Qwen2.5 文本/视觉）用于链接解析、OCR、摘要。
- 启动优化：WebView 预热与页面缓存，Splash 动画等待初始化完成。

> 注意：Web UI 依赖 CDN/ESM（React、Tailwind、Framer Motion、Lucide），运行时需要网络。

## 架构概览

- UI：Compose + WebView Hybrid
- 状态：MVVM（ViewModel + StateFlow）
- 数据：Room 作为 SSOT；NocoDB 作为远端数据源
- 依赖注入：Hilt
- 网络：Retrofit + OkHttp + Kotlinx Serialization
- 本地存储：Room + DataStore + EncryptedSharedPreferences

数据流（简化）：

```
Web UI (React) <-> JS Bridge <-> ViewModel <-> Repository
                                       |-> Room (SSOT)
                                       |-> NocoDB API
                                       |-> AIService (SiliconFlow)

FloatingWindowService -> AIService -> Repository -> Room/NocoDB
```

## 技术栈

### Android Native
| 模块 | 技术选型 |
|------|----------|
| 语言 | Kotlin |
| UI | Jetpack Compose (Material 3, iOS 风格主题) |
| 架构 | MVVM + Clean Architecture |
| 依赖注入 | Hilt |
| 网络 | Retrofit + OkHttp + Kotlinx Serialization |
| 本地存储 | Room + DataStore |
| 安全存储 | EncryptedSharedPreferences |
| Markdown | compose-markdown |

### Hybrid Web Frontend
| 模块 | 技术选型 |
|------|----------|
| 核心 | React 18（ESM 运行时导入） |
| 样式 | Tailwind CSS（CDN） |
| 动画 | Framer Motion |
| 图标 | Lucide React |

### AI & Backend
| 模块 | 技术选型 |
|------|----------|
| Headless CMS | NocoDB（自建） |
| AI 服务 | SiliconFlow API（Qwen2.5） |

## 目录结构

```
app/src/main/
├─ assets/                 # Web 前端资源
│  ├─ main_ui.html         # 主界面（React）
│  └─ login.html           # 登录界面（React）
├─ java/com/example/ai4research/
│  ├─ core/                # 主题、网络拦截、工具
│  ├─ data/                # 数据层（Room + Retrofit）
│  ├─ domain/              # 领域模型与仓库接口
│  ├─ ui/                  # Compose 界面
│  ├─ service/             # 悬浮窗、截图、AI 服务
│  ├─ navigation/          # 导航图
│  └─ di/                  # Hilt 模块
└─ res/                    # Android 资源
```

## 快速开始

### 环境要求
- Android Studio Koala 或更高版本
- JDK 17（AGP 8.x 需要）
- Android SDK 34（Target），Min SDK 26

### 运行步骤
1. 克隆仓库并打开项目。
2. 等待 Gradle 同步完成。
3. 连接设备或启动模拟器。
4. 运行 `app`。

## 配置说明（开发环境）

- NocoDB：在 `app/src/main/java/com/example/ai4research/core/util/Constants.kt` 设置 `NOCO_BASE_URL` 与 `NOCO_TOKEN`。
- SiliconFlow：在 `app/src/main/java/com/example/ai4research/service/AIService.kt` 设置 `API_KEY`。

> 生产环境建议将密钥移至安全配置（例如 `local.properties` + CI 注入），避免硬编码。

## 权限说明

| 权限 | 用途 |
|------|------|
| `INTERNET` | 访问 NocoDB / AI 服务 / Web 依赖 |
| `SYSTEM_ALERT_WINDOW` | 悬浮窗显示 |
| `FOREGROUND_SERVICE` | 悬浮窗服务常驻 |
| `FOREGROUND_SERVICE_MEDIA_PROJECTION` | 截图能力 |
| `POST_NOTIFICATIONS` | 通知权限（Android 13+） |

---

欢迎提交 Issue 或 PR 改进项目。
