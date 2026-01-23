# AI4Research

<p align="center">
  <img src="app/src/main/assets/logo.png" alt="AI4Research Logo" width="120"/>
</p>

<p align="center">
  <b>Photon Lab - 您的个人 AI 研究助手</b>
</p>

<p align="center">
  <img src="https://img.shields.io/badge/Platform-Android-green.svg"/>
  <img src="https://img.shields.io/badge/Language-Kotlin-blue.svg"/>
  <img src="https://img.shields.io/badge/UI-Hybrid%20(React%20%2B%20Compose)-orange.svg"/>
  <img src="https://img.shields.io/badge/Architecture-MVVM%20%2B%20Clean-purple.svg"/>
  <img src="https://img.shields.io/badge/Min%20SDK-26-gray.svg"/>
</p>

---

## 📖 项目简介

**AI4Research** 是一款专为 AI 研究人员设计的移动端助手应用，旨在帮助用户高效管理论文、追踪前沿竞赛并记录灵感。它通过 **"三端一体化"** 的理念（手机采集、云端分析、PC 整合），解决了科研信息入口分散、整理困难的痛点。

本项目采用 **"UI/UX Pro Max"** 设计理念，结合 **Hybrid 混合开发架构**，在 Android 原生应用中无缝集成了 React + Tailwind CSS 的现代化 Web 界面，实现了极致丝滑的视觉体验与原生性能的完美平衡。

---

## ✨ 核心特性

- **🌌 双模 UI 引擎 (Dual-Theme Engine)**
  - **Holographic Day**: 全息银白风格，清爽透亮，适合日间阅读。
  - **Cyber Void**: 午夜霓虹风格，深邃沉浸，科技感十足。
  - 支持系统自动切换与手动一键切换。

- **🚀 混合架构 (Hybrid Architecture)**
  - **主界面 (Main Stream)**: 采用 React + Tailwind + Framer Motion 构建，运行于高性能 WebView 中，实现复杂的玻璃拟态 (Glassmorphism) 和流体动画。
  - **详情页 (Native Detail)**: 采用 Jetpack Compose 原生开发，确保沉浸式阅读体验和高性能 Markdown 渲染。
  - **无缝交互**: 通过 `WebAppInterface` 桥接层，实现 Web 与 Native 的双向通信（跳转、状态同步、原生能力调用）。

- **🧠 智能科研助手**
  - **全能采集 (Capture)**:
    - **链接剪藏**: 从微信/浏览器一键分享，自动解析论文/博客。
    - **悬浮窗工具**: 全局悬浮球，支持**截屏识别**、**区域选择**和**快速链接输入**，不打断当前工作流。
    - **语音灵感**: 随时录制想法，AI 自动整理成 Bullet Points (规划中)。
  - **深度分析 (Analysis)**:
    - **五点法摘要**: 自动生成研究问题、方法、创新点、局限性与启发。
    - **竞赛日历**: 自动提取比赛 DDL 并生成时间轴。
  - **项目管理 (Projects)**: 智能推荐文献归属项目，支持精读/略读状态管理。

---

## 🛠 技术栈

### Android Native
| 模块 | 技术选型 |
|------|----------|
| **语言** | Kotlin |
| **UI 框架** | Jetpack Compose (Material 3) |
| **架构模式** | MVVM + Clean Architecture |
| **依赖注入** | Hilt |
| **网络层** | Retrofit + OkHttp + Kotlinx Serialization |
| **本地存储** | Room (SQLite) + DataStore |
| **安全存储** | EncryptedSharedPreferences |
| **Markdown** | compose-markdown |
| **AI 服务** | SiliconFlow API (Qwen2.5/VL) |

### Hybrid Web Frontend
| 模块 | 技术选型 |
|------|----------|
| **核心库** | React 18 (via ESM) |
| **样式** | Tailwind CSS (via CDN) |
| **动画** | Framer Motion |
| **图标** | Lucide React |
| **构建方式** | Runtime ESM Import (无需 Node.js 构建步骤) |

### Backend & Data
| 模块 | 技术选型 |
|------|----------|
| **Headless CMS** | NocoDB (Self-hosted) |
| **Database** | MySQL/PostgreSQL (via NocoDB) |
| **Auth** | NocoDB Auth API |

---

## 📂 项目结构

```
app/src/main/
├── assets/                    # Web 前端资源 (Hybrid UI)
│   ├── main_ui.html           # 主界面 (React应用入口)
│   ├── login.html             # 登录界面 (React应用入口)
│   └── logo.png               # 应用图标
├── java/com/example/ai4research/
│   ├── core/                  # 核心基础层
│   │   ├── security/          # 生物识别与加密
│   │   ├── network/           # 网络拦截器
│   │   └── theme/             # 原生主题配置
│   ├── data/                  # 数据层 (Data Layer)
│   │   ├── local/             # Room 数据库 & DAO
│   │   ├── remote/            # Retrofit API & DTO
│   │   └── repository/        # 仓库实现
│   ├── domain/                # 领域层 (Domain Layer)
│   │   ├── model/             # 业务实体
│   │   └── repository/        # 仓库接口
│   ├── ui/                    # 表现层 (Presentation Layer)
│   │   ├── main/              # 主界面容器 (WebView Wrapper)
│   │   ├── detail/            # 详情页 (Native Compose)
│   │   ├── auth/              # 认证页容器
│   │   └── floating/          # 悬浮窗 UI 组件
│   ├── service/               # 后台服务 (悬浮窗, AI服务)
│   ├── navigation/            # 导航图配置
│   ├── MainActivity.kt        # 应用入口
│   └── AI4ResearchApp.kt      # Application 类
└── res/                       # Android 资源文件
```

---

## 🚀 快速开始

### 环境要求
- Android Studio Koala (2024.1.1) 或更高版本
- JDK 17
- Android SDK 34 (UpsideDownCake)

### 构建步骤
1. 克隆仓库:
   ```bash
   git clone <your-repo-url>
   cd AI4Research
   ```
2. 打开 Android Studio，等待 Gradle 同步完成。
3. 连接 Android 设备或启动模拟器。
4. 点击 **Run 'app'** (Shift+F10)。

### 注意事项
- 由于 Web 界面使用了 ESM 模块加载 (`esm.sh`) 和 CDN 资源，**运行应用时需要保持网络连接**。
- 首次加载 WebView 可能需要几秒钟下载 React 依赖。
- 悬浮窗功能需要授予“显示在其他应用上层”权限。

---

## 📱 权限说明

| 权限 | 用途 |
|------|------|
| `INTERNET` | 加载 Web 资源、同步数据、AI 服务调用 |
| `ACCESS_NETWORK_STATE` | 检查网络连接状态 |
| `SYSTEM_ALERT_WINDOW` | 显示全局悬浮窗工具 |
| `FOREGROUND_SERVICE` | 维持悬浮窗服务运行 |
| `USE_BIOMETRIC` | 生物识别登录（可选） |

---

## 🤝 贡献与支持

欢迎提交 Issue 或 Pull Request 来改进本项目。

- **GitHub**: [AI4Research-Gist](https://github.com/AI4Research-Gist)
- **团队**: Photon Lab Team

---

<p align="center">
  Made with ❤️ by AI4Research Team
</p>
