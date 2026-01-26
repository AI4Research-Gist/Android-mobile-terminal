# AI4Research 技术与产品说明

## 1. 文档目标

本文档用于说明当前代码库中的产品定位、架构设计与关键流程，内容以实际实现为准。

## 2. 产品定位与使用场景

AI4Research 面向科研与学习场景，核心目标是把分散的信息入口（链接、截图、语音灵感）统一进入“研究卡片”，并通过 AI 做结构化整理，最终以移动端卡片 + 详情页的方式呈现与管理。

典型场景：
- 浏览器/微信复制论文链接 -> 悬浮窗识别 -> 自动入库。
- 截图/区域截图 -> OCR/识别 -> 入库为“图片采集”条目。
- App 内列表浏览 -> 进入详情 -> 阅读/编辑/归类。

## 3. 系统架构

### 3.1 Hybrid UI
- WebView（React/Tailwind）：主列表、筛选、搜索、项目过滤、退出登录、悬浮窗开关。
- Compose：详情页、登录路由容器、系统级页面与服务交互。
- JS Bridge：`AndroidInterface` 提供双向通信（数据推送、导航、操作）。

### 3.2 Android 架构分层
- Presentation：Compose + ViewModel（StateFlow）
- Domain：纯业务模型 + Repository 接口
- Data：Repository 实现、Room（本地 SSOT）+ NocoDB（远端）

### 3.3 服务层
- `FloatingWindowService`：悬浮球/菜单、截图、区域选择、链接输入、剪贴板检测。
- `ScreenCaptureActivity`：MediaProjection 权限与截图逻辑。
- `AIService`：SiliconFlow API（Qwen2.5 文本/视觉）

简化数据流：

```
Web UI (React) <-> JS Bridge <-> ViewModel <-> Repository
                                       |-> Room (SSOT)
                                       |-> NocoDB API
                                       |-> AIService (SiliconFlow)

FloatingWindowService -> AIService -> Repository -> Room/NocoDB
```

## 4. 关键流程

### 4.1 启动与登录
- Splash 动画期间预热 WebView + 预加载登录页。
- `AuthRepository.isLoggedInFast()` 仅通过 token 判定是否进入主页面。

### 4.2 列表加载与同步
- 启动/手动刷新时，先拉取 Projects，再拉取 Items。
- 项目名通过本地 Project 表映射，写入 Item 的 `projectName`。
- Room 为单一事实来源（SSOT），UI 始终观察本地 Flow。

### 4.3 详情页编辑
- 详情页支持 Markdown 渲染与编辑保存。
- 保存后调用远端 `updateItem`，本地再插入更新后的实体。

### 4.4 悬浮窗采集
- 悬浮球支持：全屏截图 / 区域截图 / 手动输入链接。
- 剪贴板检测到 URL / DOI / arXiv 后提示添加。
- AI 解析后调用 `ItemRepository.createUrlItem/createImageItem` 入库。

## 5. 数据模型与表结构

### 5.1 NocoDB
Base: `p8bhzq1ltutm8zr`

#### items 表（`mez4qicxcudfwnc`）
核心字段：
- `Id`：主键（Int）
- `title`：标题
- `type`：paper / competition / insight / voice
- `summary`：摘要
- `content_md`：Markdown 正文
- `origin_url`：来源链接或图片路径
- `audio_url`：语音条目路径
- `status`：processing / done / failed（字符串带注释）
- `read_status`：unread / reading / read（字符串带注释）
- `project_id`：外键（Int）
- `meta_json`：扩展元数据（JSON）
- `CreatedAt` / `UpdatedAt`

#### projects 表（`m14rejhia8w9cf7`）
- `Id`：主键（Int）
- `Title` 或 `name`：项目名称
- `description`
- `CreatedAt`

#### users 表（`m1j18kc9fkjhcio`）
- `Id`：主键（Int）
- `email` / `username` / `Phonenumber`
- `password`
- `avatar_url`
- `biometric_enabled`

### 5.2 Room（本地）
- `ItemEntity`：将远端 `Id(Int)` 映射为本地 `id(String)`；保存 `projectName` 与 `isStarred`。
- `ProjectEntity`：存储项目名与描述。
- `UserEntity`：缓存登录用户信息。

## 6. 关键模块与路径

- App/启动：`app/src/main/java/com/example/ai4research/AI4ResearchApp.kt`
- 主入口：`app/src/main/java/com/example/ai4research/MainActivity.kt`
- Hybrid UI：`app/src/main/assets/main_ui.html`
- 登录页面：`app/src/main/assets/login.html`
- JS Bridge：`app/src/main/java/com/example/ai4research/ui/auth/WebAppInterface.kt`
- 悬浮窗：`app/src/main/java/com/example/ai4research/service/FloatingWindowService.kt`
- 截图：`app/src/main/java/com/example/ai4research/service/ScreenCaptureActivity.kt`
- AI 服务：`app/src/main/java/com/example/ai4research/service/AIService.kt`
- 数据仓库：`app/src/main/java/com/example/ai4research/data/repository/ItemRepositoryImpl.kt`

## 7. 配置与安全

- NocoDB URL 与 Token：`core/util/Constants.kt`
- SiliconFlow API Key：`service/AIService.kt`
- 网络安全策略：`res/xml/network_security_config.xml`（允许特定域名明文）

> 生产环境建议将密钥移出源码（例如 local.properties + CI 注入）。

## 8. 已实现 / 规划中

已实现：
- 混合 UI（WebView + Compose）
- NocoDB 登录/注册与本地 token 缓存
- 研究卡片列表、筛选、搜索、项目归属
- 详情页 Markdown 渲染与编辑保存
- 悬浮窗截图/区域选择/链接采集
- SiliconFlow OCR/链接解析/摘要

规划中（代码内尚未完整闭环）：
- 详情页 AI 对话与多轮问答
- 语音条目完整链路（ASR + 结构化）
- PC 端协作与插件集成

## 9. 构建环境

- Android Studio Koala 或更高版本
- JDK 17（AGP 8.x 需要）
- Min SDK 26 / Target 34 / Compile SDK 36

---

如果需要进一步补充流程或模块说明，请指出具体关注点。
