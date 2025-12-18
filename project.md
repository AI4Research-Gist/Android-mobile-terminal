# AI4Research Android 移动端开发规格说明书

## 1. 项目概览 (Project Overview)

- **应用名称**: AI4Research
- **平台**: Android (Min SDK 26 / Target SDK 34)
- **核心理念**: 碎片化采集（微信/语音/图） -> 云端 AI 重构 -> 本地结构化卡片。
- **开发模式**: 使用 Cursor 辅助开发，强调代码整洁度与模块化。
- **设计美学**: **"iOSify Material"**。使用 Android 原生 Jetpack Compose 技术栈，但通过自定义 Theme 和组件，复刻 iOS 的视觉体验（高斯模糊、大圆角、流畅动效、极简白灰配色）。

------

## 2. 技术栈与架构 (Tech Stack & Architecture)

### 2.1 核心技术栈

- **语言**: Kotlin (100%)
- **UI 框架**: Jetpack Compose (Material 3)
- **架构模式**: MVVM + Clean Architecture (Presentation / Domain / Data)
- **依赖注入**: Hilt
- **异步处理**: Coroutines + Flow
- **网络层**: Retrofit + OkHttp (支持 Multipart 上传)
- **本地存储**: Room (SQLite ORM)
- **图片加载**: Coil (支持缓存、SVG、GIF)
- **后台任务**: WorkManager (保证大文件上传不中断)
- **JSON 解析**: Gson (用于 Room TypeConverter)
- **Markdown渲染**: `com.github.jeziellago:compose-markdown` 或类似库 (支持 LaTeX)

### 2.2 后端与数据源

- **数据库**: PostgreSQL (部署在自建服务器)
- **后台管理**: **NocoDB** (连接 PostgreSQL，作为 Headless CMS 管理数据)
- **文件存储**: 本地磁盘 + Nginx 静态映射 (URL 存入 PG)
- **API 协议**: RESTful API (Python FastAPI)

### 2.3 架构分层规范

1. **UI Layer**: Composable 函数 -> ViewModel (持有 UIState)。
2. **Domain Layer (纯 Kotlin)**: UseCases (业务逻辑) -> Repository Interface。
3. **Data Layer**: Repository Impl -> RemoteDataSource (Retrofit) / LocalDataSource (Room)。
   - *原则*: **Single Source of Truth** 是 Room。UI 只观察 Room，网络请求负责更新 Room。

------

## 3. UI/UX 设计规范 (iOS on Android)

**Prompt for Cursor**: *"Design all UI components using Material 3 but override styles to match iOS Human Interface Guidelines. Use Squircle shapes, Blur effects, and avoid elevation shadows."*

### 3.1 视觉系统 (Design Tokens)

- **色彩 (Color Palette)**:
  - `Background`: `#F2F2F7` (iOS 默认浅灰背景)
  - `Surface`: `#FFFFFF` (纯白卡片)
  - `Primary`: `#007AFF` (iOS 蓝) 或 自定义品牌色
  - `TextPrimary`: `#000000` (几乎纯黑)
  - `TextSecondary`: `#8E8E93` (系统灰)
- **形状 (Shapes)**:
  - 统一使用 **20dp** 的圆角 (RoundedCornerShape)。
  - 卡片去除 `elevation` (阴影)，改为 `Border(1.dp, Color(0xFFE5E5EA))` (极浅边框) 或纯白背景块。
- **字体 (Typography)**:
  - 标题使用 Bold/Heavy 字重，正文使用 Regular。行高 (LineHeight) 设为 `1.4em`。

### 3.2 核心组件定制

1. **导航栏 (Blurry Top Bar)**:
   - 模仿 `UINavigationBar`。背景需实现磨砂玻璃效果（Android 上可用 `Haze` 库或半透明色模拟）。
   - 支持 "Large Title" (页面滚动时，大标题缩小移至顶部中间)。
2. **底部 Tab (Bottom Bar)**:
   - 背景半透明 + Blur。
   - **移除** Material 3 的胶囊状选中背景 (Indicator)。
   - 选中状态：图标变蓝；未选中：图标灰色。
3. **列表与转场**:
   - 列表项点击要有缩放反馈 (Scale down to 0.98)。
   - 页面切换使用 `SlideInHorizontally` (从右向左滑入)。

------

## 4. 数据库设计 (Schema)

此部分需同时在 **NocoDB** (Postgres) 和 **Android Room** 中对应。

### 4.1 核心表结构

#### Table: `items` (所有资源的主表)

| **字段名**    | **类型**    | **说明**                                         |
| ------------- | ----------- | ------------------------------------------------ |
| `id`          | UUID/String | 主键                                             |
| `type`        | String      | 枚举: `paper`, `competition`, `insight`, `voice` |
| `title`       | String      | 标题                                             |
| `summary`     | Text        | AI 生成的一句话摘要                              |
| `content_md`  | Text        | 详细内容/五点法 (Markdown 格式)                  |
| `origin_url`  | String      | 原始链接                                         |
| `audio_url`   | String      | 录音文件 URL (仅 voice 类型有)                   |
| `status`      | String      | `processing`, `done`, `failed`                   |
| `read_status` | String      | `unread`, `reading`, `read`                      |
| `project_id`  | String      | 外键关联 Projects                                |
| `meta_json`   | JSONB       | 存比赛时间线、作者列表等动态结构                 |
| `created_at`  | Timestamp   | 创建时间                                         |

#### Table: `projects`

| **字段名**    | **类型** | **说明**                      |
| ------------- | -------- | ----------------------------- |
| `id`          | UUID     | 主键                          |
| `name`        | String   | 项目名称 (如 "LoRA 容量研究") |
| `description` | String   | 备注                          |

### 4.2 Room 特殊处理

- **TypeConverters**:
  - Postgres 的 `JSONB` -> Android Room 的 `String`。
  - 在读取时，使用 Gson 将 String 转回 `data class`。
  - *示例*: `competition_timeline` 字段在 Room 中存为 String，UI 读取时转为 `List<TimelineEvent>`。

------

## 5. 功能模块开发指南 (Features)

### 模块一：全局采集 (Intent & Share)

**目标**: 无论在微信还是浏览器，点分享都能找到 "AI4Research"。

- **组件**: `ShareActivity` (不可见/透明主题)。

- **Intent Filter**:

  XML

  ```
  <intent-filter>
      <action android:name="android.intent.action.SEND" />
      <category android:name="android.intent.category.DEFAULT" />
      <data android:mimeType="text/plain" /> <data android:mimeType="image/*" />    </intent-filter>
  ```

- **UI 交互**:

  - 触发时，从屏幕底部弹出一个 **iOS 风格 ActionSheet**。
  - 显示抓取的内容预览 + 备注输入框 + 确认按钮。

- **后台逻辑**:

  - 点击确认 -> 启动 `WorkManager` (即使杀掉 App 也能传) -> 调用 API `/capture/url`。

### 模块二：首页信息流 (Main Feed)

**目标**: 清晰展示解析状态与结果。

- **UI 结构**: `Scaffold` -> `Column` -> `iOSStyleSearchBar` + `FilterTabs` + `LazyColumn`。
- **卡片设计**:
  - **解析中状态**: 显示 Shimmer (骨架屏闪烁动画) + "AI 正在阅读..."。
  - **论文卡片**: 标题 (Bold) + 胶囊标签 (Year/Conf) + 摘要 (浅灰背景块)。
  - **比赛卡片**: 标题 + 倒计时进度条 (Visual Progress)。
- **交互**: 下拉刷新 (Haptic Feedback) 触发 Sync。

### 模块三：沉浸式详情页 (Detail)

**目标**: 舒适的阅读体验。

- **排版**:
  - 利用 `compose-markdown` 渲染 Markdown。
  - 针对 LaTeX 公式，如果 Markdown 库支持不好，可嵌入轻量级 `WebView` 仅渲染公式部分，或使用 `CaTeX`。
- **项目归属**:
  - 标题下方显示 "所属项目: LoRA研究 >"。点击弹出 `ModalBottomSheet` (NocoDB 里的 Projects 列表) 进行切换。
- **Chat 悬浮窗**:
  - 右下角 FAB 按钮。点击展开全屏对话层。
  - 对话气泡模仿 iMessage (蓝色/灰色气泡)。

### 模块四：语音灵感 (Voice Memo)

**目标**: 极速记录。

- **录音机**:
  - 使用 `Android MediaRecorder` (API 31+ 推荐 `MediaRecorder` 或 `AudioRecord`)。
  - 格式: AAC / M4A (体积小，兼容性好)。
- **上传策略**:
  - 录音结束 -> 存入 `Context.cacheDir` -> 数据库插入一条 `Local Item` (状态: Uploading) -> WorkManager 后台上传文件 -> 成功后更新 URL。

------

## 6. 开发环境与目录结构 (Project Structure)

请在 Cursor 中创建文件时严格遵守此结构：

```
app/src/main/java/com/example/ai4research/
├── core/                  # 核心基础库
│   ├── network/           # Retrofit, Interceptors (Token)
│   ├── database/          # Room DB, TypeConverters
│   ├── theme/             # Color, Type, Shape (iOS Style)
│   └── util/              # Extensions, Constants
├── di/                    # Hilt Modules (AppModule, NetworkModule...)
├── domain/                # 业务逻辑层 (纯 Kotlin)
│   ├── model/             # Data Classes (Paper, Insight...)
│   ├── repository/        # Interfaces
│   └── usecase/           # CaptureUrlUseCase, GetItemsUseCase...
├── data/                  # 数据层
│   ├── remote/            # API DTOs, Retrofit Service
│   ├── local/             # Room Entities, DAOs
│   └── repository/        # Repository Implementations
├── ui/                    # 界面层 (Compose)
│   ├── components/        # 通用组件 (IOSAppBar, IOSCard, IOSButton)
│   ├── screens/
│   │   ├── home/          # 首页 ViewModel & Screen
│   │   ├── detail/        # 详情 ViewModel & Screen
│   │   ├── share/         # 分享弹窗 Activity & UI
│   │   └── voice/         # 录音相关 UI
│   └── navigation/        # AppNavigation (NavHost)
└── worker/                # WorkManager (UploadWorker)
```

------

## 7. 开发流程注意事项 (Checklist)

1. **NocoDB 先行**:
   - 在服务器部署好 NocoDB 和 Postgres。
   - 在 NocoDB 界面创建好 `items`, `projects` 表。
   - 获取 NocoDB 的 API Token 或让后端封装一层 API。
2. **Room 是核心**:
   - 不要在 UI 层直接调 API。所有数据必须先进 Room。
   - `Flow<List<Item>>` 从 DAO流向 UI，确保数据实时性。
3. **图片/文件处理**:
   - Postgres/Room 里只存 URL (如 `http://x.x.x.x/static/img.jpg`)。
   - Android 端使用 Coil 加载该 URL。
4. **iOS 风格细节**:
   - **不要使用** Material 自带的 `TopAppBar`，因为它有阴影和特定的高度限制。建议手写一个 `Row` 来实现 iOS 风格的导航栏。
   - **不要使用** `Ripple` (水波纹) 点击效果，改为透明度变化 (Opacity change)。
5. **权限管理**:
   - 录音需要 `RECORD_AUDIO` 权限。
   - Android 13+ 需要通知权限。
   - 使用 Accompanist Permissions 库优雅处理动态权限申请。

------

## 8. 给 Cursor 的提示词示例 (Prompts)

在开发过程中，你可以复制以下提示词给 Cursor：

- **生成 UI 时**: *"Create a Jetpack Compose card for a research paper. Style it like an iOS widget: white background, 20dp rounded corners, no shadow but a very thin grey border. Use San Francisco-style font weights. The title should be bold. Include a 'Processing' state with a shimmer effect."*
- **生成数据库代码时**: *"Create a Room Entity for the 'items' table. It needs to handle the 'tags' list and 'timeline' object using Gson TypeConverters. Make sure it matches the PostgreSQL schema provided in the doc."*
- **生成 ViewModel 时**: *"Create a ViewModel for the Home Screen. It should observe the Room database using Flow. Include a function to refresh data from the API. Handle strict error states and expose a Sealed Class UI State (Loading, Success, Error)."*





## 1. 项目概况与愿景

- **项目名称**: AI4Research Client
- **目标平台**: Android Native (Min SDK 26, Target SDK 34)
- **核心功能**:
  1. **全场景采集**: 通过分享菜单、语音、以及**全局悬浮球**，随时捕捉科研灵感与文献。
  2. **AI 赋能**: 云端解析内容，本地呈现结构化卡片（五点法、比赛时间线）。
  3. **极致体验**: 在 Android 上复刻 **iOS 系统级原生体验**（高斯模糊、流畅弹簧动画、极简设计）。
- **开发工具**: Cursor (AI 辅助编码) + Android Studio Koala/Ladybug。

------

## 2. 后端数据源配置 (NocoDB)

由于您的 NocoDB 尚未建立表格，请登录您提供的 Dashboard 并在 Cursor 开发前完成以下配置。

### 2.1 连接信息

- **Dashboard 地址**: `http://47.109.158.254:8080/dashboard/#/nc/p8bhzq1ltutm8zr`
- **API Token (xc-token)**: `lBVvkotCNwFCXz-j1-s3XcE5tXRCp7MzKECOfY2e`
- **Base URL (API调用地址)**: `http://47.109.158.254:8080/api/v1/db/data/v1/p8bhzq1ltutm8zr` (注：这是推测的标准 API 路径，请在 NocoDB "API Snippets" 中确认 Project ID `p8bhzq1ltutm8zr` 是否正确)

### 2.2 表结构初始化 (请在网页端手动创建)

请创建两个核心表：`items` 和 `projects`。

#### 表 1: `items` (核心资源表)

| **列名 (Column Name)** | **类型 (Type)**     | **说明**                                           |
| ---------------------- | ------------------- | -------------------------------------------------- |
| `title`                | SingleLineText      | 标题                                               |
| `type`                 | SingleLineText      | 枚举值: `paper`, `competition`, `insight`, `voice` |
| `summary`              | LongText            | AI 生成的简短摘要                                  |
| `content_md`           | LongText            | 详细内容/五点法 (Markdown)                         |
| `origin_url`           | URL                 | 原始文章链接                                       |
| `audio_url`            | URL                 | 录音文件下载地址                                   |
| `status`               | SingleLineText      | `processing`, `done`, `failed`                     |
| `read_status`          | SingleLineText      | `unread`, `reading`, `read`                        |
| `project_id`           | LinkToAnotherRecord | 关联 `projects` 表 (Many-to-One)                   |
| `meta_json`            | JSON                | 存储比赛时间线、Tags 等动态数据                    |

#### 表 2: `projects` (项目表)

| **列名 (Column Name)** | **类型 (Type)** | **说明**                 |
| ---------------------- | --------------- | ------------------------ |
| `name`                 | SingleLineText  | 项目名称 (如 "LoRA优化") |
| `description`          | LongText        | 项目备注                 |

------

## 3. 技术栈与架构 (Tech Stack)

### 3.1 核心框架 (Modern Android)

- **语言**: Kotlin (100%)
- **UI**: Jetpack Compose (Material 3) + **Custom iOS Modifiers**
- **架构**: MVVM + Clean Architecture (Presentation -> Domain -> Data)
- **依赖注入**: Hilt
- **异步**: Coroutines + Flow
- **网络**: Retrofit + OkHttp (拦截器注入 NocoDB Token)
- **本地库**: Room (Single Source of Truth)
- **悬浮窗**: Android `WindowManager` + `AccessibilityService` (用于读取浏览器链接)
- **图片**: Coil
- **序列化**: Kotlinx Serialization 或 Gson

------

## 4. UI 设计规范: "iOSify" (iOS 风格化)

所有 UI 组件必须**严格**遵循以下视觉语言，而非默认的 Material Design。

### 4.1 视觉 DNA

- **高斯模糊 (Blur)**: 导航栏、底部 Tab、悬浮球菜单背景必须使用“毛玻璃”效果。
  - *技术*: Android 12+ 使用 `RenderEffect.createBlurEffect`，低版本使用 `Toolkit.blur` 或半透明遮罩。
- **无阴影设计 (No Elevation)**: 抛弃 Material 的投影，使用**极细边框** (`0.5dp` 灰色描边) 来区分层级。
- **平滑圆角 (Squircle)**: 所有卡片圆角统一为 **20dp**。
- **弹簧动画 (Spring)**: 悬浮球展开、页面跳转使用 `spring(dampingRatio = 0.8f)`，拒绝生硬的线性动画。
- **触觉反馈 (Haptics)**: 按钮点击、下拉刷新需触发 `HapticFeedbackConstants.LIGHT_IMPACT`。

------

## 5. 详细功能模块开发

### 模块一：全局悬浮球 (Assistive Touch Style) [NEW]

这是一个后台 Service，即使 App 关闭也能运行。

- **UI 设计**:
  - **常驻态**: 一个圆角矩形小球 (类似 iOS 辅助触控)，半透明黑色背景 (Opacity 40%)，边缘吸附。
  - **展开态**: 点击后，背景变模糊，中心弹出一个 iOS 风格菜单 (Popover)，包含两个图标按钮：
    1. 🔗 **识别链接**
    2. 📷 **拍照识别**
- **技术实现**:
  - **权限**: 需要申请 `SYSTEM_ALERT_WINDOW` (悬浮窗权限) 和 `BIND_ACCESSIBILITY_SERVICE` (无障碍权限，用于读取浏览器 URL)。
  - **Service**: 创建 `FloatingBallService`。
  - **功能逻辑**:
    - **识别链接**: 利用 AccessibilityNodeInfo 遍历当前屏幕节点，查找 `id` 为 `url_bar` 或以 `http` 开头的文本 -> 自动弹窗提示 "检测到链接，是否采集？"
    - **拍照识别**: 点击后，启动一个**透明 Activity** (`Theme.Translucent.NoTitleBar`)，覆盖全屏相机界面，拍照后直接上传 `/capture/image` 接口。

### 模块二：数据存储分层 (Data Layering)

必须严格遵守 **Single Source of Truth (SSOT)** 原则。

#### 1. 实体定义 (Entity)

- **DTO (Remote)**: `NocoItemDto` (对应 NocoDB JSON 字段)。
- **Entity (Local)**: `ItemEntity` (Room 表结构)。
- **Domain Model**: `ResearchItem` (UI 使用的纯净对象)。

#### 2. 数据流向 (Repository Pattern)

- **读取**: UI <- ViewModel <- UseCase <- Repository <- **Room DAO** (Flow)。
  - *注意*: UI **永远不** 直接观察网络请求。UI 只观察数据库。
- **写入/同步**:
  1. WorkManager 定时或手动触发 `SyncWorker`。
  2. `SyncWorker` 调用 API 拉取 NocoDB 数据。
  3. 数据对比，写入/更新 Room 数据库。
  4. Room 发出通知，UI 自动刷新。

#### 3. Room 类型转换 (Converters)

NocoDB 的 `meta_json` 是字符串，在 Room 中需自动转为对象。

Kotlin

```
class RoomConverters {
    @TypeConverter
    fun fromTimestamp(value: Long?): Date? = value?.let { Date(it) }
    @TypeConverter
    fun jsonToTimeline(value: String): TimelineMeta? = Gson().fromJson(...)
}
```

### 模块三：首页与列表 (Home Feed)

- **iOS 风格导航栏**:
  - 不要使用 `TopAppBar`。
  - 使用 `Row` 自定义：左侧大标题 "Library"，右侧 "编辑"。
  - 滚动时：大标题缩小并渐变至顶部正中 (Large Title collapse)。
- **卡片设计**:
  - **论文**: 纯白背景 + 1dp 灰色边框。标题加粗 (San Francisco 字体替代品)。摘要部分使用浅灰色块背景 (`#F2F2F7`)。
  - **状态指示**: 列表右侧显示一个小蓝点 (未读)。

### 模块四：详情页与五点法 (Detail & Immersion)

- **Markdown 渲染**:
  - 使用 `com.github.jeziellago:compose-markdown`。
  - 针对五点法结构，设计专门的 CSS/Style，例如 "研究背景" 四个字自动加粗并变色。
- **全屏手势**:
  - 支持**边缘左滑返回** (Swipe Back)，配合 Compose 的 `EnterTransition.slideInHorizontally`。

### 模块五：采集与录音 (Capture)

- **录音**:
  - 界面模仿 iOS "语音备忘录"。
  - 黑色背景，红色波形 (Canvas 绘制振幅)。
  - 录音完成 -> 存文件 -> WorkManager 后台上传 -> NocoDB 获得 `audio_url`。

------

## 6. Cursor 提示词指南 (Prompts for Development)

在开发过程中，请复制以下提示词给 Cursor，以确保代码质量。

### 6.1 初始化项目结构

> "Create a rigorous Android project structure using MVVM and Clean Architecture.
>
> Root packages: core, feature, data, domain.
>
> Tech stack: Hilt, Room, Retrofit, Jetpack Compose.
>
> Constraint: Do not use standard Material Design elevations or shadows. Prepare a Theme.kt that mimics iOS design language (Colors: #F2F2F7 background, #007AFF primary)."

### 6.2 配置网络层 (NocoDB)

> "Setup Retrofit module using Hilt.
>
> Base URL: http://47.109.158.254:8080/api/v1/db/data/v1/p8bhzq1ltutm8zr/
>
> Add an OkHttp Interceptor that adds the header xc-token: lBVvkotCNwFCXz-j1-s3XcE5tXRCp7MzKECOfY2e to every request.
>
> Create a DTO NocoItemDto that maps to the NocoDB table columns: title, type, summary, content_md, meta_json."

### 6.3 实现悬浮球 Service

> "Implement an Android Service `FloatingBallService`.
>
> 1. Use `WindowManager` to draw a small, rounded, semi-transparent view over other apps.
>
> 2. When clicked, expand a menu with a spring animation (using Compose inside the WindowManager view).
>
> 3. The menu has two buttons: 'Link' and 'Camera'.
>
> 4. Ensure permissions SYSTEM_ALERT_WINDOW are handled gracefully.
>
>    Style: The expanded menu must have a background blur effect."

### 6.4 实现数据库缓存 (Room)

> "Create a Room Database.
>
> Entity: ItemEntity. Needs TypeConverters for the meta_json field to store it as String but use it as a Data Class in code.
>
> DAO: fun getItems(): Flow<List<ItemEntity>>.
>
> Repository: fun syncItems() which fetches from API and inserts/replaces into Room. Ensure Single Source of Truth pattern."

------

## 7. 开发注意事项 (Critical Checkpoints)

1. **NocoDB 坑点**: NocoDB 的 API 返回结构通常包裹在 `{ "list": [...], "pageInfo": ... }` 中，解析 JSON 时不要直接解析 List，要先剥离外层。
2. **悬浮窗权限**: 在小米/华为手机上，`SYSTEM_ALERT_WINDOW` 权限可能需要去“设置-应用管理-权限”中手动开启，代码里要写好引导跳转逻辑。
3. **无障碍服务 (Accessibility)**: 读取浏览器 URL 是敏感操作。Google Play 可能会审核。如果是个人项目，请在代码中注明仅用于辅助提取链接。
4. **图片加载**: NocoDB 里的图片如果是私有的，Coil 加载时可能也需要加 Header。如果你用的是上面提供的 NocoDB 公开 API，则不需要。
5. **主线程保护**: 悬浮球的点击事件不要阻塞主线程，OCR 识别或正则匹配 URL 必须在 `Dispatchers.IO` 中执行。

指令 1 (数据库与网络):

"Now implement the Data Layer. Create the ApiService interface for NocoDB, the ItemEntity for Room with TypeConverters for the meta_json field, and the ItemRepository that handles the sync logic (SSOT)."

指令 2 (UI 基础):

"Now let's build the Home Screen. Create a Scaffold with an iOS-style Bottom Bar (Blur effect). Implement the HomeViewModel to fetch data from the Repository flow. Create a sample ItemCard composable that looks like an iOS widget (White, 20dp corner, no shadow)."

指令 3 (悬浮球 - 难点):

"Now implement the Floating Ball feature. Create the FloatingBallService. It needs to draw a view over other apps using WindowManager. Implement the click interaction to expand a menu with 'Link' and 'Camera' buttons using Spring animations."

指令 4 (分享功能):

"Implement the Share Extension. Create a ShareActivity with Theme.Translucent.NoTitleBar. Update AndroidManifest to handle ACTION_SEND. When a URL is shared, show a bottom sheet dialog to confirm, then enqueue a WorkManager task to upload it."