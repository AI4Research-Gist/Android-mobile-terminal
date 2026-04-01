# Gist

<p align="center">
  <img src="app/src/main/assets/logo.jpg" alt="Gist Logo" width="120"/>
</p>

<p align="center">
  <b>Gist （Get the Gist） · 你的个人 AI 研究助手（Android）  </b>
</p>

<p align="center">
  <b>AIS · 研智小组（Research Intelligence Group）@ Advanced Informatics Scholar  </b>
   <img src="app/src/main/assets/Team-logo.png" alt="Team Logo" width="100"/>
</p>


<p align="center">
  <img src="https://img.shields.io/badge/Platform-Android-green.svg"/>
  <img src="https://img.shields.io/badge/Language-Kotlin-blue.svg"/>
  <img src="https://img.shields.io/badge/UI-Hybrid%20(WebView%20%2B%20Compose)-orange.svg"/>
  <img src="https://img.shields.io/badge/Architecture-MVVM%20%2B%20Clean-purple.svg"/>
  <img src="https://img.shields.io/badge/Version-v0.6.0-red.svg"/>
  <img src="https://img.shields.io/badge/Min%20SDK-26-gray.svg"/>
</p>

---

## 项目简介

**Gist （Get the Gist）** 是面向科研/学习场景的信息采集与分析助手：
- **移动端负责入口与轻处理**，提供快速采集、浏览、编辑与管理。
- **云端负责结构化与 AI 解析**（NocoDB + SiliconFlow）。
- **本地负责流畅展示与离线缓存**（Room SSOT）。

应用采用 Hybrid UI：**主列表与交互使用 WebView 内的 React/Tailwind 页面**，详情页与系统能力使用 **原生 Compose**。

## 当前功能（以代码实现为准）

- **混合 UI**：`assets/main_ui.html` + `assets/login.html` 作为主/登录界面，详情页为 Compose。
- **研究卡片管理**：paper / article / competition / insight / voice 统一管理，支持搜索、过滤、项目归属、星标、阅读状态。
- **灵感页**：底部导航已将首页收敛为 `灵感`，支持页面内新建、搜索、显示已读、隐藏已读。
- **灵感录入**：单条灵感支持标题、正文、图片、原始语音四模块组合；标题必填，其余选填。
- **详情页**：Markdown 渲染、编辑保存、标记已读/星标/删除、项目归属同步；灵感详情支持图片预览与原始语音播放。
- **结构化阅读卡**：paper / article 支持“研究问题 / 方法 / 数据集 / 核心发现 / 局限性 / 可复用点 / 我的笔记”。
- **详情页 AI 对话**：支持围绕当前条目进行问答，并优先利用 OCR 原文与中等摘要构造上下文。
- **账号系统**：NocoDB 用户表注册/登录；本地 EncryptedSharedPreferences 缓存 token。
- **比赛版用户分离**：按当前登录用户隔离 `items/projects`；新账号默认空数据；历史数据统一归档到测试账号。
- **悬浮窗助手**：全局悬浮球，支持全屏/区域截图、剪贴板链接检测、手动输入链接；AI 解析入库。
- **语音采集**：保留独立语音录制页（录音 + ASR + AI 优化）能力；同时灵感页支持原始语音作为附件手动保存。
- **AI 能力**：SiliconFlow（Qwen2.5 文本/视觉）用于链接解析、OCR、摘要、语音优化。
- **OCR 稳定化**：截图授权、投影会话、图片解码与 OCR 主链路已完成第一轮稳定性修复。
- **知识连接**：支持 `paper` 去重分组、`article -> paper` 自动关联、`insight` 手动关联已有条目。
- **项目总览**：新增项目概况、最近新增、重点论文、灵感汇总与关系统计页面。
- **项目研究背景**：项目总览页支持上传 Markdown 研究背景文档，并生成摘要与关键词。
- **AI 项目总结**：项目总览页支持生成项目主题、最近进展、关键文献、待补问题与下一步建议。
- **灵感反查**：灵感详情页支持反查相关论文 / 资料，返回推荐理由并可一键采纳为关联。
- **项目创建入口**：设置页现已支持手动创建项目，不再只能依赖历史项目。
- **结构化筛选**：论文页和资料页支持按项目、年份、来源/作者、关键词/标签细筛。
- **解析状态可视化**：链接现已支持先分类入库、后台解析、前端状态字条与解析耗时展示。
- **资料页本地优先显示**：资料条目现已支持先写本地占位、后异步同步远端，避免整栏空白。
- **OCR 摘要修正**：长 OCR 文本引入 `medium_summary`，显示层优先使用中等摘要，避免只展示短截断内容。
- **同步诊断**：设置页支持查看当前用户 ID、本地条目数量、未上云条目数、最近同步状态与前端接收数量。
- **数据库升级保护**：数据库版本升级改为正式 migration，不再默认使用破坏性迁移清空本地库。
- **启动优化**：WebView 预热与页面缓存，Splash 动画等待初始化完成。

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
| 语言 | Kotlin (2.0.21) |
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
changelog/                  # 版本更新日志（按版本号拆分）
├─ v0.0.1.md
└─ v0.0.3.md
app/src/main/
├─ assets/                 # Web 前端资源
│  ├─ main_ui.html         # 主界面（React）
│  └─ login.html           # 登录界面（React）
├─ java/com/example/ai4research/
│  ├─ core/                # 主题、网络拦截、工具、安全
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

## 当前版本

- 当前版本：`v0.6.0`
- 本版本重点：项目级 AI 总结、灵感反查相关资料、手动创建项目、未上云条目补传与同步诊断增强、图片 OCR 稳定性增强
- 更新日志入口：[`v0.6.0`](d:/Android-mobile-terminal/changelog/v0.6.0.md)
- 上一版本说明：[`v0.5.1`](d:/Android-mobile-terminal/changelog/v0.5.1.md)
- 更早版本说明：[`v0.5.0`](d:/Android-mobile-terminal/changelog/v0.5.0.md)
- 灵感模块补充说明：[`INSPIRATION_PAGE_V0.3.0_SPEC.md`](d:/Android-mobile-terminal/INSPIRATION_PAGE_V0.3.0_SPEC.md)
- 知识连接参考文档：[`V0_5_KNOWLEDGE_CONNECTION_REFERENCE.md`](d:/Android-mobile-terminal/V0_5_KNOWLEDGE_CONNECTION_REFERENCE.md)
- 研究助手 PRD：[`V0_6_RESEARCH_ASSISTANT_PRD.md`](d:/Android-mobile-terminal/V0_6_RESEARCH_ASSISTANT_PRD.md)
- 功能现状与后续路线图：[`CURRENT_FEATURES_AND_ROADMAP.md`](d:/Android-mobile-terminal/CURRENT_FEATURES_AND_ROADMAP.md)
- 数据库迁移策略：[`DATABASE_MIGRATION_POLICY.md`](d:/Android-mobile-terminal/DATABASE_MIGRATION_POLICY.md)
- 版本明细目录：[`changelog/`](d:/Android-mobile-terminal/changelog)

## 配置说明（开发环境）

- NocoDB：在根目录 `.env.local` 或系统环境变量中设置 `AI4RESEARCH_NOCO_BASE_URL` 与 `AI4RESEARCH_NOCO_TOKEN`。
- 比赛版用户归属字段：现网 NocoDB 当前使用物理字段 `ownerId`。
- SiliconFlow：在根目录 `.env.local` 或系统环境变量中设置 `AI4RESEARCH_SILICONFLOW_API_KEY`，可选设置 `AI4RESEARCH_SILICONFLOW_BASE_URL`。
- `app/src/main/res/xml/network_security_config.xml` 当前已按迁移后的 NocoDB IP 放行明文 HTTP。

### 比赛测试账号

- 用户名：`gist_demo_archive`
- 邮箱：`gist_demo_archive@example.com`
- 密码：`GistDemo@2026!`

说明：
- 按当前比赛版用户分离方案，历史测试数据统一归档到该账号。
- 新注册账号默认从空数据开始。
- 演示完整历史数据时，统一使用该账号登录。
- 当前已归档历史数据：
  - `items` 40 条
  - `projects` 3 条

> 项目现在默认从 `.env.local` 或系统环境变量读取敏感配置，`.env.local` 已被加入 `.gitignore`，提交代码时不会带上真实密钥。

## 当前说明

- 当前项目已完成比赛版“用户分离”最小可用方案。
- 测试账号可直接用于演示历史数据。
- 新账号创建后应默认看到空数据，再逐步生成自己的项目和条目。
- 当前现网 NocoDB 仍为直连模式，正式产品化建议后续升级认证体系与配置安全性。
- 当前 OCR 截图主链路已可稳定使用，下一步建议再接资料模块中的手动 OCR 兜底入口。
- 当前链接导入链路已改为“先分类、后后台解析”，用户可直接在列表中观察解析状态与已耗时长。
- 当前资料页已改为本地优先显示，占位条目无需等待远端 `article` 创建成功即可在资料栏中出现。
- 当前首页已正式切换为“灵感”页，不再承担旧的动态聚合职责。
- 当前灵感页已支持页面内新建 / 编辑 / 搜索 / 已读隐藏与详情查看。
- 当前灵感详情页已支持查看上传图片与播放原始语音。
- 当前 `paper / article` 已支持结构化阅读卡与详情页 AI 问答。
- 当前项目已支持基础知识连接：paper 去重关系、article 关联论文、insight 手动关联条目。
- 当前项目已支持项目总览页，用于查看项目概况、重点论文和灵感汇总。
- 当前项目已支持上传项目研究背景 Markdown，并在项目总览页显示摘要与关键词。
- 当前项目已支持在项目总览页生成 `AI 项目总结`。
- 当前灵感详情页已支持 `反查相关资料`，并可将推荐结果一键采纳为显式关联。
- 当前设置页已支持手动创建项目。
- 当前 OCR 链路已补充 `medium_summary`，长图摘要与问答的上下文完整性已有明显改善。
- 当前设置页已支持同步诊断，可直接排查“云端有数据但手机端不显示”的问题，并显示未上云条目情况。
- 当前本地未上云条目在刷新时会自动尝试补传。
- 当前图片 OCR 链路已增强超时、重试与缩图降级策略，弱网下更稳。
- 当前数据库升级已切换为正式 migration 策略，后续版本升级不再默认清空本地库。

## 权限说明

| 权限 | 用途 |
|------|------|
| `INTERNET` | 访问 NocoDB / AI 服务 / Web 依赖 |
| `RECORD_AUDIO` | 语音采集录音 |
| `SYSTEM_ALERT_WINDOW` | 悬浮窗显示 |
| `FOREGROUND_SERVICE` | 悬浮窗服务常驻 |
| `FOREGROUND_SERVICE_MEDIA_PROJECTION` | 截图能力 |
| `POST_NOTIFICATIONS` | 通知权限（Android 13+） |

---

欢迎提交 Issue 或 PR 改进项目。
