# AI4Research 功能完善与 NocoDB 集成实施计划

根据您的要求，我已整合了所有开发任务，将重点放在 UI 汉化、NocoDB 深度集成以及“整理”视图的实现上。鉴于我们已经建立了 Android 端的网络层，我们将采用 **Android 代理模式** 来处理数据，这样比在 HTML 中直接暴露 Token 更安全，且能复用已有的代码。

## 1. Web 前端改造 (`main_ui.html`)

*   **UI 全面汉化**:
    *   将所有界面文本替换为中文（例如："Today" → "今日动态", "Settings" → "设置"）。
    *   日期格式化调整为中文格式。
*   **新增 "整理 (Inbox)" 视图**:
    *   在底部导航栏添加 **"整理"** Tab。
    *   实现 `SortingView` 组件，用于展示待处理、未分类的条目（对应 NocoDB 中 `status='inbox'` 或 `type='link'` 的数据）。
*   **采集悬浮窗 (Capture Modal) 功能实现**:
    *   **链接 (Link)**: 实现 URL 输入界面，支持自动填充剪贴板内容。
    *   **扫描 (Scan)** & **语音 (Voice)**: 绑定按钮事件，调用 Android 原生接口进行处理。

## 2. Android 端逻辑与数据桥接

*   **升级 `MainViewModel` & `NocoApiService`**:
    *   **数据获取**: 增加对 "整理/Inbox" 数据的获取逻辑。
    *   **数据写入**: 实现 `createItem` 接口，支持将用户采集的链接、笔记直接写入 NocoDB。
*   **完善 `MainAppInterface` (JS 桥接)**:
    *   `createItem(data)`: 接收前端采集的数据并上传。
    *   `triggerScan()`: 模拟或启动扫描功能。
    *   `triggerVoice()`: 模拟或启动语音功能。

## 3. NocoDB 集成配置

*   **Token 配置**: 确认使用您提供的 Token: `lBVvkotCNwFCXz-j1-s3XcE5tXRCp7MzKECOfY2e`。
*   **Base URL**: `http://47.109.158.254:8080`。

## 4. 验证计划

1.  **UI 检查**: 确认所有界面均为中文，布局无错乱。
2.  **数据流测试**:
    *   点击悬浮窗 "+" -> 输入链接 -> 保存。
    *   刷新 "整理" 视图，确认新条目出现。
    *   在 NocoDB 后台确认数据已写入。
3.  **系统悬浮窗**: 确认后台剪贴板监听功能正常。
