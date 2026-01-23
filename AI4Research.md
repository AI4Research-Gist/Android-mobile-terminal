# AI4Research 技术与产品文档

## 1. 项目概览

**AI4Research** 是面向科研人群的信息采集与分析工具，核心目标是把分散的科研素材（论文/比赛/灵感/语音）统一进入云端，再由大模型完成结构化分析，最终在移动端以卡片化方式展示并支持项目归属管理。

### 1.1 产品定位
> 面向科研工作者的「三端一体化个人科研中枢」：
> - **手机端**：负责信息入口、灵感捕捉和轻量查看（当前重点）。
> - **云端**：负责 AI/LLM/Agent 重处理和数据存储。
> - **PC 端**：负责深度整合、写作与和现有科研工具（如 Zotero）协同（未来规划）。

### 1.2 目标用户
- 硕/博研究生（信息/计算机/自动化等方向）。
- 有多个科研项目并行（论文方向 + 比赛/工程项目）。
- 痛点：信息入口分散（微信/浏览器/截图/录音），文献库与具体项目脱节，碎片时间利用率低。

---

## 2. 核心功能 (User Stories)

### Epic 1：信息采集 & 一键上云
- **Story 1 (链接剪藏)**：从微信/浏览器分享链接到 App，云端自动解析论文/博客/推文。
- **Story 2 (图片/截图)**：分享截图或拍照，OCR 识别论文标题、公式或架构图。
- **Story 8 (语音灵感)**：录制语音，本地/云端转写并整理成 Bullet Points。

### Epic 2：云端 AI 处理与展示
- **Story 3 (文献卡片)**：查看云端生成的结构化卡片（标题、作者、年份、AI 一句话摘要）。
- **Story 4 (五点法分析)**：论文详情页展示研究问题、方法、创新点、局限性、启发。
- **Story 5 (启发精华)**：非论文长文提炼为核心观点 + 可执行 Checklist。
- **Story 6 (项目归属)**：AI 推荐项目归属，支持手动调整精读/略读状态。

### Epic 3：端侧辅助
- **Story 7 (划词解释)**：选中文本，本地/云端模型即时解释或翻译。

### Epic 4：会议与日程
- **Story 9 (会议纪要)**：组会录音转会议纪要 + TODO 列表。
- **Story 11 (比赛日历)**：自动提取比赛通知的 DDL（报名/提交/答辩），形成时间轴。

### Epic 5：对话交互
- **Story 10 (AI 问答)**：在详情页针对当前文献进行提问（对比、解释、启发）。

---

## 3. 系统架构

### 3.1 移动端 (Android)
采用 **Hybrid 混合架构**，结合 Jetpack Compose 原生体验与 React Web 动态灵活性。

- **UI 层**：
  - **WebView (React)**：主界面 (`main_ui.html`)，负责复杂的列表流、卡片展示、动画效果。
  - **Compose**：详情页、悬浮窗、认证页、设置页，负责高性能交互与系统级能力。
- **ViewModel**：驱动数据流转，处理 `JavascriptInterface` 调用。
- **数据层**：
  - **Room**：本地数据库，作为 UI 的单一事实源（Single Source of Truth），支持离线访问。
  - **NocoDB API**：远端权威数据源，负责同步。

### 3.2 云端 (Backend)
- **NocoDB**：作为 Headless CMS，统一存储结构化数据 (items/projects/users)。
- **LLM/Agent 分析层**：
  - 接收采集的原始数据 (URL/Image/Audio)。
  - 调用 LLM (如 SiliconFlow/OpenAI) 进行解析、摘要、结构化提取。
  - 回写 NocoDB，更新状态。

### 3.3 关键数据流 (End-to-End)
1.  **采集入口**：手机端分享链接/图片/语音 → 调用 Capture API / 写入本地待处理。
2.  **云端接入**：统一落库为 `items`，初始状态 `processing`。
3.  **智能分析**：云端 Agent 识别类型 (paper/insight/competition)，生成 `summary` / `content_md` / `meta_json`。
4.  **结果回写**：更新数据库字段，状态变更为 `done`。
5.  **多端同步**：手机端拉取最新数据存入 Room，UI 响应式更新。

---

## 4. 数据模型 (Schema)

### 4.1 NocoDB (Remote)
> Base: `p8bhzq1ltutm8zr`

#### **items 表** (`mez4qicxcudfwnc`)
| 字段 | 类型 | 说明 |
|---|---|---|
| `Id` | String | 主键 |
| `title` | String | 标题 |
| `type` | String | `paper` / `competition` / `insight` / `voice` |
| `summary` | String | AI 生成的一句话摘要 |
| `content_md` | Text | 结构化 Markdown 内容 |
| `origin_url` | URL | 来源链接 |
| `status` | String | `processing` / `done` / `failed` |
| `read_status` | String | `unread` / `reading` / `read` |
| `meta_json` | JSON | 扩展元数据 (作者/会议/时间线) |
| `project_id` | Int | 外键，关联 `projects` 表 |

#### **projects 表** (`m14rejhia8w9cf7`)
| 字段 | 类型 | 说明 |
|---|---|---|
| `Id` | Int | 主键 |
| `Title` | String | 项目名称 |
| `description` | String | 描述 |

#### **users 表** (`m1j18kc9fkjhcio`)
| 字段 | 类型 | 说明 |
|---|---|---|
| `Id` | Int | 主键 |
| `username` | String | 用户名 |
| `email` | String | 邮箱 |
| `password` | String | 密码 |
| `avatar_url` | String | 头像 |

### 4.2 Room (Local)
- **ItemEntity**: 映射 `items` 表，包含本地缓存字段。
- **ProjectEntity**: 映射 `projects` 表。
- **UserEntity**: 缓存当前登录用户信息。

---

## 5. 开发规划 (Roadmap)

### Milestone 1: 闭环 MVP (已完成)
- [x] 手机端混合架构搭建 (WebView + Compose)。
- [x] 登录/注册流程 (NocoDB Auth)。
- [x] 基础采集：URL 分享解析。
- [x] 列表展示：WebView 渲染 React 卡片。

### Milestone 2: 核心增强 (进行中)
- [x] **悬浮窗工具**：全局截屏、区域选择、链接快速采集 (Floating Window)。
- [ ] **文献五点法**：详情页 Markdown 渲染与展示。
- [ ] **项目归属**：Item 与 Project 的关联管理 UI。

### Milestone 3: 多模态与扩展
- [ ] **比赛卡片**：时间轴可视化。
- [ ] **语音记录**：录音机与 ASR 对接。
- [ ] **启发清单**：Insight 类型卡片的 Checklist 展示。

### Milestone 4: PC 端与生态
- [ ] Web 控制台。
- [ ] Zotero 插件集成。

---

## 6. 关键模块代码路径
- **WebView 交互**: `ui/auth/WebAppInterface.kt`, `assets/main_ui.html`
- **悬浮窗服务**: `service/FloatingWindowService.kt`
- **数据仓库**: `data/repository/ItemRepositoryImpl.kt`
- **API 定义**: `data/remote/api/NocoApiService.kt`
- **AI 服务**: `service/AIService.kt`
