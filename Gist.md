# Gist 技术与产品说明（代码级）

## 1. 文档目标
本文档以当前代码库为唯一依据，完整说明 **Gist**（原名 AI4Research）的产品定位、架构设计、核心流程、数据模型、服务能力与技术细节，便于后续维护、迭代与交接。

## 2. 命名与范围说明（重要）
- **产品对外名称已改为：Gist**（`app_name` 已为 Gist）。
- **代码与包名仍大量保留 AI4Research 历史命名**：
  - 包名：`com.example.ai4research`
  - Application：`AI4ResearchApp`
  - 数据库名：`ai4research_db`
  - rootProject.name：`AI4Research`
  - 主题名：`Theme.AI4Research`
- 本文档覆盖范围：
  - Android App（`app/`）
  - 附带的 **UI/UX Pro Max skill** 与 CLI（`skill/`）
  - 构建与配置文件（Gradle/Manifest/Resources）
- `app/build/` 等编译产物不在说明范围。

## 3. 产品定位与场景
Gist 面向科研与学习场景，核心目标是 **把分散的入口（链接、截图、语音灵感）统一归档为结构化“研究卡片”**，并通过 AI 进行解析与总结，最终在移动端以卡片列表 + 详情页的方式管理。

典型场景：
- 浏览器/微信复制论文链接 → 悬浮窗识别 → AI 解析 → 入库。
- 截图/区域截图 → OCR/解析 → 入库为“图片采集/灵感”条目。
- App 内浏览 → 详情页阅读/编辑 → 项目归属/星标/状态同步。

## 4. 架构总览
### 4.1 Hybrid UI 架构
- **WebView（React + Tailwind）**：主列表、筛选、搜索、设置、项目过滤。
- **Jetpack Compose**：详情页、系统级页面（登录容器、启动动画、截图/悬浮窗交互）。
- **JS Bridge**：`AndroidInterface` 实现双向通信（数据推送、导航、操作）。

### 4.2 分层结构（Clean + MVVM）
- **Presentation**：Compose + ViewModel（StateFlow）
- **Domain**：业务模型 + Repository 接口
- **Data**：Repository 实现、Room（本地 SSOT）+ NocoDB（远端）

### 4.3 数据与服务依赖
- **本地**：Room + DataStore + EncryptedSharedPreferences
- **远端**：NocoDB API
- **AI 服务**：SiliconFlow API（Qwen2.5 文本/视觉）
- **网页抓取**：OkHttp + Jsoup + arXiv API + CrossRef

### 4.4 关键数据流（简化）
```
Web UI (React) <-> JS Bridge <-> ViewModel <-> Repository
                                       |-> Room (SSOT)
                                       |-> NocoDB API
                                       |-> AIService (SiliconFlow)

FloatingWindowService -> AIService -> Repository -> Room/NocoDB
```

## 5. 功能清单（已实现）
- **混合 UI**：`assets/main_ui.html`（主界面）+ `assets/login.html`（登录界面）+ Compose 详情页。
- **研究卡片管理**：paper / competition / insight / voice 统一管理，支持搜索、过滤、项目归属、星标、阅读状态。
- **详情页**：Markdown 渲染、编辑保存、标记已读、星标、删除、项目归属同步。
- **账号系统**：NocoDB 用户表注册/登录；本地使用 EncryptedSharedPreferences 缓存 token。
- **悬浮窗助手**：全局悬浮球，支持全屏/区域截图、剪贴板链接检测、手动输入链接。
- **语音采集**：语音录制 → SpeechRecognizer 本地识别 → AI 优化润色 → 保存为语音卡片。
- **AI 解析**：SiliconFlow（Qwen2.5 文本/视觉）用于链接解析、OCR、摘要、语音优化。
- **启动优化**：WebView 预热与页面缓存，Splash 动画等待初始化完成。

> Web UI 使用 CDN/ESM（React、Tailwind、Framer Motion、Lucide），**运行时需要网络**。

## 5.1 语音采集功能详情
### 功能入口
主页快速采集窗口（点击"+"号）→ 选择"语音"按钮 → 进入 Compose 录音页面。

### 技术实现
| 组件 | 文件路径 | 说明 |
|------|----------|------|
| 录音状态 | `service/VoiceRecordingState.kt` | Idle/Recording/Processing/Completed/Error 五种状态 |
| 录音工具 | `service/AudioRecorderHelper.kt` | MediaRecorder 封装，支持获取振幅、时长 |
| 录音界面 | `ui/voice/VoiceRecordingScreen.kt` | iOS 风格 Compose UI，脉冲动画、实时计时 |
| 状态管理 | `ui/voice/VoiceRecordingViewModel.kt` | 整合 SpeechRecognizer + AI 优化 + 保存逻辑 |
| AI 优化 | `service/AIService.kt#enhanceTranscription` | 使用 Qwen2.5 润色语音识别结果 |
| 路由定义 | `navigation/GistNavigation.kt` | `Screen.VoiceRecording` 路由 |
| JS Bridge | `ui/main/MainScreen.kt#startVoiceRecording` | WebView 调用入口 |

### 数据流程
```
用户点击语音按钮 → JS Bridge.startVoiceRecording()
    → NavigationGraph 导航到 VoiceRecordingScreen
    → 用户开始录音
    → AudioRecorderHelper 录制音频 + SpeechRecognizer 识别
    → AIService.enhanceTranscription() 优化文本
    → 用户编辑确认 → ItemRepository.createVoiceItem() 保存
    → 返回主页面
```

### 权限要求
- `RECORD_AUDIO`：麦克风录音权限

## 6. 运行流程（关键路径）
### 6.1 启动与路由
- `MainActivity` 使用 `SplashScreenContent` 播放公式描边动画。
- `WebViewCache` 预热 WebView，并提前预加载登录页。
- `AuthRepository.isLoggedInFast()` 仅基于 token 判断是否进入主页面。
- 已登录则预加载 `main_ui.html`。

### 6.2 WebView 数据同步
- `MainScreen` 将 Room Flow 转换为 JSON 并通过 JS 注入：
  - `window.receivePapers` / `receiveCompetitions` / `receiveInsights` / `receiveProjects`
- 页面准备完成后（`isPageReady`）推送首屏数据，再触发一次刷新。
- 悬浮窗触发入库时，通过广播通知主页面刷新。

### 6.3 详情页流程
- 进入 `DetailScreen` 时自动 `load()`。
- 若 `ReadStatus == UNREAD`，自动标记为已读。
- 支持编辑 Markdown，保存后同步远端再写回本地。
- 支持项目绑定/解绑/新建项目。

### 6.4 悬浮窗采集流程
- 剪贴板检测 URL/DOI/arXiv → 悬浮球徽标提示。
- 点击悬浮球 → 选择截图/区域/链接 → AI 解析。
- 解析结果展示分类选择（paper/competition/insight）与来源选择（wechat/zhihu/web/custom）。
- 保存成功后：
  1) 广播 `ACTION_ITEM_ADDED`
  2) 跳转主页面指定 Tab
  3) 顶部 Toast 提示

## 7. UI 与 Web 前端细节
### 7.1 `assets/main_ui.html`
- 技术栈：React 18（ESM）、Tailwind CDN、Framer Motion、Lucide。
- 字体：Space Grotesk + Inter（Google Fonts）。
- 主题：Light/Dark 切换（Web 内部状态）。
- 页面结构：Home / Papers / Competitions / Settings + FAB Capture Modal。
- UI 特性：玻璃拟态（liquid glass）、动态背景、Aurora/网格动画。
- 数据解析：
  - `meta_json` 字符串解析 JSON。
  - `read_status`、`status` 取空格前缀（如 `unread (未读)` → `unread`）。
  - `UpdatedAt` 用于显示时间。

### 7.2 `assets/login.html`
- 登录/注册 3D 翻转卡片（React + Tailwind + Babel）。
- 支持用户名/手机号/邮箱登录。
- 通过 `window.AndroidInterface.login/register` 调用原生。
- `window.showError` 用于回传错误提示。

### 7.3 Compose UI
- **DetailScreen**：iOS 风格液态玻璃卡片、Markdown 渲染（compose-markdown）。
- **SplashScreenContent**：使用 PathMeasure 绘制公式路径动画。

## 8. JS Bridge（前后端接口）
### 8.1 Android → Web（注入到 window）
- `receivePapers(json)`
- `receiveCompetitions(json)`
- `receiveInsights(json)`
- `receiveProjects(json)`
- `setActiveTab(tabId)`

### 8.2 Web → Android（AndroidInterface）
#### 登录页（`WebAppInterface`）
- `login(identifier, password)`
- `register(username, email, password, phone)`
- `socialLogin(type)`

#### 主页面（`MainAppInterface`）
- `logout()`
- `navigateToDetail(itemId)`
- `requestData()`
- `search(query)`
- `applyFilter(filterType, projectId)`
- `deleteItem(itemId)`
- `getProjects()`
- `checkFloatingWindowStatus()`
- `requestFloatingWindowPermission()`
- `setFloatingWindowEnabled(enabled)`
- `startVoiceRecording()` - 启动语音录制页面

## 9. 数据模型
### 9.1 Domain Model
`ResearchItem`:
- id, type, title, summary, contentMarkdown, originUrl, audioUrl
- status (`processing/done/failed`)
- readStatus (`unread/reading/read`)
- isStarred
- projectId / projectName
- metaData (Paper/Competition/Insight/Voice)

`ItemType`:
- PAPER / COMPETITION / INSIGHT / VOICE

`ItemStatus` / `ReadStatus`:
- 存储形式包含中文说明（如 `processing (解析中)`）。

### 9.2 Room 实体
- **ItemEntity**（表名 `items`）
  - `id` String（远端 Int 转 String）
  - `type` / `title` / `summary` / `content_md`
  - `origin_url` / `audio_url`
  - `status` / `read_status`
  - `project_id` / `project_name`
  - `is_starred`
  - `meta_json`
  - `created_at` / `synced_at`
- **ProjectEntity**（表名 `projects`）
  - `id`, `name`, `description`, `created_at`
- **UserEntity**（表名 `users`）
  - `id`, `email`, `username`, `phone`, `avatarUrl`, `biometricEnabled`, `updatedAt`

### 9.3 Room 数据库
- `AI4ResearchDatabase`，版本 3，`fallbackToDestructiveMigration()`。
- DB 名称：`ai4research_db`。

### 9.4 NocoDB 数据结构
Base ID：`p8bhzq1ltutm8zr`

**items 表（`mez4qicxcudfwnc`）**
- `Id`（Int 主键）
- `title`, `type`, `summary`, `content_md`
- `origin_url`, `audio_url`
- `status`, `read_status`
- `project_id`
- `meta_json`
- `CreatedAt`, `UpdatedAt`

**projects 表（`m14rejhia8w9cf7`）**
- `Id`, `Title`/`name`, `description`, `CreatedAt`

**users 表（`m1j18kc9fkjhcio`）**
- `Id`, `email`, `username`, `Phonenumber`, `password`, `avatar_url`, `biometric_enabled`

### 9.5 meta_json 约定
- Paper：`authors[]`, `conference`, `year`, `tags[]`
- Competition：`organizer`, `deadline`, `theme`, `competitionType`, `prizePool`
- Insight：`tags[]`
- Voice：`duration`, `transcription`
- AI 解析扩展：`source`, `identifier`, `summary_en`, `summary_zh`, `platform`

## 10. 数据同步策略
- **Room 为单一事实来源（SSOT）**。
- `refreshItems()`：
  1) 同步 Projects → 本地（用于 project_name 映射）
  2) 同步 Items → 本地（过滤空 title/type）
- 读取路径：UI 仅订阅 Room Flow。
- 更新路径：
  - `updateReadStatus` 先本地，再远端 best-effort。
  - `updateStarred` 仅本地。
  - `deleteItem` 先本地，远端 best-effort。

## 11. AI 服务与内容解析
### 11.1 SiliconFlow API
- Base URL：`https://api.siliconflow.cn/v1/`
- 模型：
  - 文本：`Qwen/Qwen2.5-14B-Instruct`
  - 视觉：`Qwen/Qwen2.5-VL-32B-Instruct`
- 主要能力：
  - 文本摘要、链接解析、OCR

### 11.2 AIService 关键能力
- `summarizeText`：论文摘要 JSON 输出。
- `parseLinkStructured`：链接类型识别（arxiv/doi/webpage）。
- `recognizeImageStructured`：图片 OCR 解析。
- `parseFullLink`：完整链接解析（抓取网页 → AI 结构化）。
- 生成 Markdown 内容 + meta_json + 双语摘要。

### 11.3 WebContentFetcher
- **arXiv**：使用 `export.arxiv.org/api/query` 获取标题/摘要/作者/分类。
- **DOI**：使用 CrossRef API 获取元信息。
- **WeChat**：解析 `#js_content` 获取正文。
- **Generic Web**：提取 `article/main/content`，移除脚本、广告等。
- 最长内容：`MAX_CONTENT_LENGTH = 8000`。

## 12. 悬浮窗体系
- `FloatingWindowManager`：
  - DataStore 保存开关 (`floating_window_settings`)。
  - 检查 overlay 权限。
- `FloatingWindowService`：
  - 悬浮球 UI（`FloatingBallView`）+ 菜单 UI（`FloatingMenuView`）。
  - 剪贴板检测 URL/DOI/arXiv（Regex）。
  - 自定义来源输入弹窗。
  - 分类选择弹窗（paper/competition/insight） + 来源选择（wechat/zhihu/web/custom）。
  - 入库成功后广播并跳转主页面。

## 13. 截图与 MediaProjection
- `ScreenCaptureActivity`：
  - 获取 MediaProjection 权限。
  - 支持全屏/区域截图。
  - 截图保存到 `cache/screenshots/`。
  - 广播 `ACTION_CAPTURE_COMPLETED`。
- `MediaProjectionStore`：
  - 内存级缓存授权，避免重复弹窗。
- `FloatingWindowService` 也提供区域截图 overlay 方案（不走 Activity）。

## 14. 主题与视觉系统
- `AI4ResearchTheme`：iOS 风格 Material3 主题。
- `IOSColor / IOSShapes / IOSTypography`：模拟 Apple HIG。
- `ThemeManager` 使用 DataStore 保存主题模式（Light/Dark/System）。

## 15. 网络与安全
- `NocoAuthInterceptor` 自动添加 `xc-token`。
- `network_security_config.xml` 允许对 `47.109.158.254` 明文通信。
- **硬编码密钥/Token**：
  - `Constants.kt`：`NOCO_BASE_URL`, `NOCO_TOKEN`
  - `AIService.kt`：`API_KEY`
  - 建议：移至 `local.properties` + CI 注入。
- 本地敏感信息存储：EncryptedSharedPreferences。

## 16. 构建与依赖版本
| 组件 | 版本/配置 |
|------|----------|
| AGP | 8.12.3 |
| Kotlin | 2.0.21 |
| Compile SDK | 36 |
| Target SDK | 34 |
| Min SDK | 26 |
| Compose BOM | 2024.09.00 |
| Hilt | 2.51.1 |
| Retrofit | 2.11.0 |
| OkHttp | 4.12.0 |
| Room | 2.6.1 |
| Kotlinx Serialization | 1.6.3 |
| DataStore | 1.1.1 |
| Security Crypto | 1.1.0-alpha06 |
| Biometric | 1.2.0-alpha05 |
| Compose Markdown | 0.5.4 |
| Jsoup | 1.17.2 |
| Gson | 2.11.0 |

## 17. 权限与系统声明
Manifest 声明：
- `INTERNET`
- `RECORD_AUDIO` - 语音采集录音
- `SYSTEM_ALERT_WINDOW`
- `FOREGROUND_SERVICE`
- `FOREGROUND_SERVICE_MEDIA_PROJECTION`
- `POST_NOTIFICATIONS`

Service：`FloatingWindowService`（foregroundServiceType=`specialUse`）

## 18. 目录结构（核心部分）
```
app/src/main/
├─ assets/
│  ├─ main_ui.html
│  └─ login.html
├─ java/com/example/ai4research/
│  ├─ core/            # theme/network/util/security
│  ├─ data/            # Room + Retrofit + Mapper
│  ├─ domain/          # Model + Repository 接口
│  ├─ ui/              # Compose 界面
│  ├─ service/         # AI/悬浮窗/截图
│  ├─ navigation/      # NavGraph
│  └─ di/              # Hilt Modules
└─ res/
```

## 19. 附带工具：UI/UX Pro Max Skill（`skill/`）
该目录为 **独立 UI/UX 设计智能工具**，与 Android App 运行时无直接耦合，包含：
- **技能包**：`skill/skills/ui-ux-pro-max/`
  - Python 脚本（BM25 搜索 + 设计系统生成）
  - CSV 数据库（样式/配色/排版/UX/图表/多栈指南）
- **CLI**：`skill/cli/`（TypeScript + Bun）
  - `uipro` 命令安装技能到不同 AI Assistant 环境
  - 支持 GitHub Release 自动下载与离线安装

## 20. 注意事项 / 风险点
- **密钥硬编码**：NocoDB Token、SiliconFlow API Key 直接写在代码中。
- **明文 HTTP**：允许对固定 IP 明文访问（`network_security_config.xml`）。
- **Web UI 依赖网络**：CDN/ESM 加载 React/Tailwind/Framer Motion。
- **编码问题**：部分注释出现乱码（编码非 UTF-8）。
- **Proguard 规则**：保留类名中存在 `AI4ResearchApplication` 但实际为 `AI4ResearchApp`（需校正）。
- **WebView Debug**：`WebView.setWebContentsDebuggingEnabled(true)` 建议仅开发环境。

---
如需增加更多运行时数据、埋点、测试或部署说明，可继续补充。
