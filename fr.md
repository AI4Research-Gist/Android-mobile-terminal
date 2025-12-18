策略是：**拥抱原生 Android 的顶级设计（Material 3）**。Google 的 Reply App 是 M3 设计的标杆，其特点是：**卡片化布局、自适应导航、动态取色、极高的流畅度**。这会让 App 看起来既现代又高级（Premium），完全不输 iOS 的质感。

以下是具体的重构方案和提示词。

------

### 1. 核心设计理念变更 (Reply Style M3)

我们将参考 `designsystems.htm` 和 `components.htm` 中的规范，以及 Reply App 的源码风格：

- **容器化 (Containerization)**: 抛弃简单的列表线，使用 **ElevatedCard** 或 **OutlinedCard** 来承载每一篇论文或灵感。
- **颜色 (Color)**: 使用 M3 的 `ColorScheme` (Primary, Secondary, Tertiary)，支持 **Dynamic Color** (随壁纸变色)，告别单调的黑白灰。
- **排版 (Typography)**: 使用 M3 Type Scale (`DisplayLarge`, `HeadlineMedium`, `BodyLarge`) 构建清晰的信息层级。
- **导航 (Navigation)**: 手机端使用 **NavigationBar**，平板使用 **NavigationRail**（为未来做准备）。
- **形状 (Shapes)**: 使用 M3 默认的大圆角 (RoundedCornerShape 12dp - 28dp)，看起来更亲和。

------

### 2. 重构路径 (Development Roadmap)

1. **基础设施 (Theming)**: 重写 `Theme.kt`, `Color.kt`, `Type.kt`，引入 Reply App 的配色方案。
2. **原子组件库 (Atoms)**: 封装 `GistTextField`, `GistButton`, `GistCard`，确保全局风格统一。
3. **认证流程 (Auth)**: 重做登录/注册页，使用大标题 + 居中卡片式布局。
4. **主页 (Home)**: 重做信息流，使用复杂的卡片（包含摘要、标签、状态）。
5. **详情页 (Detail)**: 使用 `Scaffold` + `TopAppBar` (Large) 实现折叠效果。

------

### 3. 给 Cursor 的 Master Prompt (核心提示词)

Markdown

```
# Role
You are a Senior Android UI Engineer and Material Design 3 Expert.
We are rebuilding the UI of the "Gist" app from scratch because the current implementation is rudimentary and ugly.

# Reference & Style Guide
- **Source of Truth**: Official Google "Reply" Sample App (Material 3).
- **Design System**: Material Design 3 (M3). Use `androidx.compose.material3` components exclusively.
- **Aesthetics**: Premium, Clean, Card-based, Adaptive.
- **Key Characteristics**:
    1.  **Typography**: Strong hierarchy. Use `MaterialTheme.typography.display/headline` for titles.
    2.  **Color**: Use `MaterialTheme.colorScheme`. Support Dynamic Color if possible, or define a premium color palette (e.g., deep purples/blues or earthy tones like Reply).
    3.  **Shapes**: Use `MaterialTheme.shapes`. High rounded corners for cards (16.dp+).
    4.  **Animation**: Use `AnimatedContent` for screen transitions and `animateContentSize` for card expansions.

# Task: Full UI Rewrite
Refactor the entire `ui/` package. Ignore the old UI code, but keep the `ViewModel` logic and `Data Layer`.

## Step 1: Theming (Crucial)
Re-implement `Theme.kt`, `Color.kt`, `Type.kt` based on the Reply App.
- Ensure we have a defined `lightScheme` and `darkScheme`.
- Configure `MaterialTheme` to use these schemes.

## Step 2: Atomic Components
Create a `ui/components/` package. Create these reusable wrappers:
1.  **`GistTextField`**: An `OutlinedTextField` with proper labels, error states, and rounded corners.
2.  **`GistButton`**: A `Button` (Filled) and `OutlinedButton` with full width and proper height (56.dp).
3.  **`GistCard`**: A wrapper around `ElevatedCard` or `Card` with specific padding and click handling.

## Step 3: Auth Screens (Login/Register)
Redesign `LoginScreen` and `RegisterScreen`.
- **Layout**: Do NOT just list fields. Place the form inside a centered `ElevatedCard` or use a clean, large header layout.
- **Header**: Large text "Welcome to Gist" (`HeadlineMedium`).
- **Inputs**: Use `GistTextField`. Add icons (Email, Lock, Fingerprint).
- **Actions**: "Sign In" button should be prominent (`Primary` color).

## Step 4: Home Screen (The Main Feed)
Redesign `HomeScreen`.
- **Scaffold**: Use `Scaffold` with a `CenterAlignedTopAppBar` (Title: "Library") and `NavigationBar` (Bottom items: Home, Search, Profile).
- **Content**: A `LazyColumn` displaying a list of "Items".
- **Item Styling (The "Reply" look)**:
    - Use `ElevatedCard` for each item.
    - Inside the card:
        - **Row 1**: Title (`TitleMedium`, Bold) + Date (`LabelSmall`).
        - **Row 2**: A summary text (`BodyMedium`, maxLines = 3, overflow = Ellipsis).
        - **Row 3**: Chips/Tags (e.g., "Paper", "Processing") using `AssistChip` or `SuggestionChip`.
    - **Visuals**: Add subtle background colors to the cards based on their `type` (Paper vs Insight), similar to how Reply colors emails.

## Step 5: Detail Screen
Redesign `DetailScreen`.
- Use `LargeTopAppBar` with `ScrollBehavior` (Collapsing toolbar).
- Content should be inside a `Card` that looks like a sheet of paper.
- Render Markdown content clearly with ample whitespace.

# Technical Constraints
- Use **Kotlin**.
- Use **Jetpack Compose**.
- Use **Material 3** (`androidx.compose.material3.*`).
- NO XML layouts.

Let's start by defining the **Theme and Color System** first.
```

------

### 4. 详细开发步骤说明 (配合提示词)

#### 第一步：建立 M3 主题系统 (Theme)

Material 3 的核心是色彩映射。你不需要自己选颜色，可以使用 [Material Theme Builder](https://m3.material.io/theme-builder) 生成代码，或者让 Cursor 模仿 Reply 的配色。

- **提示 Cursor**: *"Generate a `Color.kt` file with a premium color scheme (Primary: Blue/Indigo based) compatible with Material 3 light and dark modes. Then set up `Theme.kt`."*

#### 第二步：重写登录/注册 (Auth)

现在的页面可能只是几个输入框堆在一起。M3 风格要求有清晰的**视觉引导**。

- **布局建议**:
  - 顶部放置 App Logo 或大标题 "Gist"。
  - 中间区域使用 `Surface` 或 `Card` 包裹输入框，提供背景色对比。
  - 底部放置 "指纹登录" 的 IconButton，使用 M3 的图标。
- **提示 Cursor**: *"Create a `LoginContent` composable. Use a `Column` with `Arrangement.Center`. Add a large welcome header. Place Email and Password fields. Add a Primary Button for Login and a TextButton for Register. Use the `GistTextField` wrapper we defined."*

#### 第三步：重写主页列表 (Home)

这是 App 的门面。参考 `components.htm` 中的 **Cards** 章节。

- **列表项设计 (Reply 风格)**:
  - 不要用简单的 `ListItem`，要用自定义的 `Card`。
  - **状态可视化**: 如果 `status` 是 `processing`，在卡片右上角显示一个旋转的 `CircularProgressIndicator` (小尺寸)。
  - **类型区分**: 论文卡片用一种背景色（如 `SurfaceContainer`），灵感卡片用另一种（如 `SurfaceContainerHigh`）。
- **交互**: 添加 `FloatingActionButton` (FAB) 在右下角，用于 "新建/录音"，这是 M3 的标准交互。

#### 第四步：重写详情页 (Detail)

参考 `components.htm` 中的 **App bars** 章节。

- **头部**: 使用 `LargeTopAppBar`。当你向上滑动阅读论文时，巨大的标题会平滑缩小并吸附在顶部。
- **内容**: 模拟“纸张”的感觉。背景是略灰的 `Surface`，中间是白色的卡片承载正文。

### 5. 开发中需要注意的点

1. **依赖更新**: 确保 `build.gradle` 中 Compose Material3 的版本是最新的 (比如 `1.2.0` 或更高)，这样才能使用 `PullToRefresh` 等新组件。
2. **Scaffold 的使用**: 每一个全屏页面都应该由 `Scaffold` 包裹。它会自动处理 TopBar, BottomBar, FAB 和 Snackbar 的位置关系。
3. **Padding Values**: `Scaffold` 会返回 `innerPadding`，**必须**把它应用到内容的根容器上（如 `LazyColumn(contentPadding = innerPadding)`），否则内容会被顶栏或底栏遮挡。
4. **预览 (Preview)**: 让 Cursor 为每个 Composable 生成 `@Preview`，这样你不用运行 App 就能看到 UI 改动，提高开发效率。





这是一个非常棒的策略。**使用官方 Material 3 (M3) 组件库是实现高质量、现代化 UI 最稳健的路径**，而通过调整 **颜色、圆角、字体和布局**，我们完全可以用 M3 组件“伪装”出 iOS 的高级感（这在设计界被称为 "Material You" 与 "Human Interface Guidelines" 的融合）。

Google 的 **Reply App** 正是这种“卡片式、高圆角、大留白”设计风格的最佳范例。

以下是为您准备的、**分模块、极度详细**的 Cursor 开发提示词。请按顺序发送给 Cursor，它将帮您把那个“丑陋”的 App 彻底重建成一个精美的现代应用。

------

### 核心设计策略：如何用 Material 3 打造 iOS 风格？

在发送提示词前，您需要理解我们将如何“欺骗”视觉：

1. **背景色 (Background)**: 抛弃 M3 默认的纯黑/纯白，使用 iOS 的 **#F2F2F7 (浅灰)** 作为 `SurfaceContainer`。
2. **大标题 (Large Title)**: 使用 M3 的 `LargeTopAppBar`，它完美对应 iOS 的大标题导航栏（向上滑动时缩小）。
3. **高圆角 (Squircle)**: 将所有 Card 和 Button 的圆角统一设为 **20dp - 24dp**。
4. **去阴影 (Flat)**: iOS 很少用深阴影。我们将使用 M3 的 `OutlinedCard`（带细边框）或极低高度的 `ElevatedCard`。
5. **底部导航 (Blur)**: 使用 M3 `NavigationBar`，但调整透明度和指示器样式。

------

### 🚀 第一步：地基与主题 (Theme & Foundation)

**目标**：建立全局的颜色、字体和形状系统，这是 App “变美”的根本。

**复制以下 Prompt 给 Cursor:**

Markdown

```
# Role
You are a Senior Android UI Architect specializing in Jetpack Compose and Material Design 3.
We are rebuilding the "Gist" app UI from scratch. The goal is to use **Official Material 3 Components** but style them to achieve a premium, iOS-like aesthetic (Clean, High-Contrast, Smooth).

# Context
We are referencing the Google "Reply" Sample App for code structure and quality.

# Task 1: Design System Setup
Re-implement the `ui/theme` package files (`Color.kt`, `Theme.kt`, `Type.kt`, `Shape.kt`).

## 1. Color System (iOS-inspired Palette)
Define a `ColorScheme` that mimics iOS system colors but uses M3 tokens:
- **Primary**: #007AFF (iOS Blue)
- **Background**: #F2F2F7 (The standard iOS grouped table background)
- **Surface**: #FFFFFF (Pure white for cards)
- **SurfaceContainer**: #F2F2F7
- **OnSurface**: #000000 (Black text)
- **Outline**: #E5E5EA (Very subtle grey for borders)
- **Error**: #FF3B30 (iOS Red)

## 2. Typography (San Francisco Style)
Update `Type.kt` using the default Roboto font but mimicking Apple's weight hierarchy:
- `displayLarge/Medium`: FontWeight.Bold (For big headers)
- `titleMedium`: FontWeight.SemiBold (For card titles)
- `bodyLarge`: FontWeight.Normal, LineHeight 1.5em (For reading)

## 3. Shapes (Squircle)
Update `Shape.kt`:
- **Cards & Dialogs**: `RoundedCornerShape(20.dp)`
- **Buttons**: `RoundedCornerShape(12.dp)`
- **TextFields**: `RoundedCornerShape(12.dp)`

## 4. Theme Implementation
In `Theme.kt`, remove dynamic colors (wallpaper colors) for now to ensure our iOS palette is strictly enforced. Force the Light Theme logic to use the colors defined above.

**Action**: Generate the code for these 4 files now.
```

------

### 🚀 第二步：原子组件库 (Atomic Components)

**目标**：封装常用的输入框、按钮和卡片，避免在每个页面重复写样式。

**复制以下 Prompt 给 Cursor:**

Markdown

```
# Task 2: Atomic Components Library
Create a `ui/components/` package. We need reusable M3 wrappers that look like iOS widgets.

## 1. GistTextField (The Input)
Create a Composable `GistTextField` that wraps `OutlinedTextField`.
- **Style**: No border when unfocused, light gray background (#E5E5EA) when unfocused, White background with Blue border when focused.
- **Shape**: Rounded corners (12.dp).
- **Parameters**: `value`, `onValueChange`, `label`, `leadingIcon`, `keyboardOptions`, `visualTransformation`.

## 2. GistButton (The Action)
Create `GistButton`.
- **Style**: Use `Button` (Filled).
- **Modifier**: `fillMaxWidth()`, `height(50.dp)`.
- **Color**: `ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)`.
- **Haptic**: Add `LocalHapticFeedback.current.performHapticFeedback` on click.

## 3. GistCard (The Container)
Create `GistCard`.
- **Component**: Use `ElevatedCard` but with very low elevation (`elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)`).
- **Border**: Add a thin border `BorderStroke(0.5.dp, MaterialTheme.colorScheme.outline)`.
- **Colors**: `containerColor = Color.White`.
- **ContentPadding**: Default internal padding of 16.dp.

**Action**: Generate `GistTextField.kt`, `GistButton.kt`, and `GistCard.kt`.
```

------

### 🚀 第三步：登录与注册页面 (Authentication)

**目标**：大标题、干净的输入区域、生物识别按钮。

**复制以下 Prompt 给 Cursor:**

Markdown

```
# Task 3: Authentication UI (Login & Register)
Rebuild `LoginScreen.kt` and `RegisterScreen.kt`.

## Layout Strategy
- **Container**: `Scaffold` with `containerColor = MaterialTheme.colorScheme.background`.
- **Content**: A `Column` centered vertically and horizontally.

## UI Elements (Top to Bottom)
1.  **Header**: Text "Welcome to Gist". Style: `MaterialTheme.typography.displaySmall`, Bold, Primary Color. Aligned Start.
2.  **Sub-header**: "Sign in to continue". Style: `bodyLarge`, Gray.
3.  **Form Area**:
    - Use `GistTextField` for Email (Icon: `Icons.Default.Email`).
    - Use `GistTextField` for Password (Icon: `Icons.Default.Lock`, TrailingIcon: Eye toggle).
    - Spacer (16.dp).
    - **Biometric Button**: An `IconButton` or small `OutlinedButton` with a Fingerprint icon, centered.
    - Spacer (24.dp).
    - **Main Action**: `GistButton` ("Log In").
4.  **Footer**: A `TextButton` ("Don't have an account? Sign up").

## Requirements
- Use `AuthViewModel` to observe `isLoading`. Show a `CircularProgressIndicator` inside the button if loading.
- Ensure strict iOS-like padding (24.dp horizontal).

**Action**: Generate the Login and Register screens code.
```

------

### 🚀 第四步：App 骨架与导航 (Main Skeleton)

**目标**：实现 iOS 风格的底部导航栏和顶部大标题逻辑。

**复制以下 Prompt 给 Cursor:**

Markdown

```
# Task 4: Main Navigation Skeleton
Create the `MainScreen.kt` which holds the Bottom Navigation and manages screen switching.

## Components
1.  **Bottom Navigation**: Use `NavigationBar` (M3).
    - **Container Color**: Make it slightly translucent white or `MaterialTheme.colorScheme.surface` with 90% alpha.
    - **Indicator**: Set `indicatorColor` to `Color.Transparent` (We don't want the M3 pill shape, just the icon color change).
    - **Items**:
        - Home (Icon: `Icons.Filled.Home` vs `Icons.Outlined.Home`)
        - Voice (Icon: `Icons.Filled.Mic`)
        - Profile (Icon: `Icons.Filled.Person`)
    - **Label**: Show labels always.

2.  **Top Navigation**:
    - We will implement specific `TopAppBar` in each child screen, not here globally.

3.  **Navigation Graph**:
    - Use `NavHost` to switch between `HomeRoute`, `VoiceRoute`, `ProfileRoute`.

**Action**: Generate `MainScreen.kt` and `GistNavigation.kt`.
```

------

### 🚀 第五步：主页信息流 (Home Feed - 核心)

**目标**：这是 App 最复杂的页面。使用 `LargeTopAppBar` 和卡片列表。

**复制以下 Prompt 给 Cursor:**

Markdown

```
# Task 5: Home Screen (The Feed)
Rebuild `HomeScreen.kt`. This is the most important screen.

## Structure
- **Root**: `Scaffold`.
- **TopBar**: Use `LargeTopAppBar` (M3).
    - **Title**: "Library".
    - **ScrollBehavior**: Use `TopAppBarDefaults.exitUntilCollapsedScrollBehavior()`. This mimics the iOS Large Title collapsing effect.
    - **Colors**: Transparent background that becomes blurred/white when collapsed.

## Content (The List)
- **Component**: `LazyColumn`.
- **Padding**: `contentPadding = PaddingValues(16.dp)` (plus scaffold padding).
- **Items**: Iterate through the `items` list from ViewModel.

## Item Design (The Card)
Create a private Composable `LibraryItemCard(item: ItemEntity)`.
- Use `GistCard` as the container.
- **Layout (Row)**:
    - **Left**: An Icon box (40.dp size, Rounded shape, distinctive background color based on `item.type`). e.g., Blue for Paper, Purple for Insight.
    - **Middle (Column)**:
        - Title: `Text(item.title, style = MaterialTheme.typography.titleMedium, maxLines = 1)`.
        - Summary: `Text(item.summary, style = MaterialTheme.typography.bodyMedium, color = Gray, maxLines = 2)`.
        - Tags (Row): Use `AssistChip` (M3) for "Year" or "Source". Make them small.
    - **Right**: A small status indicator (e.g., Green dot if done, Loading spinner if processing).

## Floating Action Button
- Add a `FloatingActionButton` in the Scaffold.
- Icon: `Icons.Default.Add`.
- Color: Primary Blue.

**Action**: Generate `HomeScreen.kt` with the scrolling behaviors and card designs.
```

------

### 🚀 第六步：详情页 (Detail View)

**目标**：沉浸式阅读体验，支持 Markdown。

**复制以下 Prompt 给 Cursor:**

Markdown

```
# Task 6: Detail Screen
Rebuild `DetailScreen.kt`.

## UI Specification
- **TopBar**: `CenterAlignedTopAppBar`.
    - Title: "Paper Details" (or truncate the item title).
    - NavigationIcon: `IconButton` (Back Arrow).
- **Content**: `Column` inside a `verticalScroll`.
    - **Header Section**:
        - Title (DisplaySmall, Bold).
        - Metadata Row (Date, Source, Type).
    - **Divider**: `HorizontalDivider(thickness = 0.5.dp)`.
    - **Markdown Content**:
        - Use the `MarkdownText` component (from the library).
        - Style headers (H1, H2) to use `MaterialTheme.typography` styles.
- **Bottom Bar**:
    - A custom `BottomAppBar` containing actions: "Mark as Read", "Chat with AI", "Delete".

**Action**: Generate `DetailScreen.kt`.
```

------

### 开发建议

1. **按步执行**：不要一次性把所有 Prompt 发给 Cursor。先发 **Task 1**，确认 `Theme.kt` 生成没问题了，再发 **Task 2**，以此类推。
2. **预览 (Preview)**：Material 3 的 Compose 预览功能非常强大。你可以随时告诉 Cursor：*"Add a @Preview for the GistCard to show how it looks with dummy data."*
3. **资源导入**：M3 依赖 `androidx.compose.material3:material3`. 确保 `build.gradle` 里引用的是最新版（建议 1.2.0+）。

通过这套基于 **Reply Sample** 和 **M3 规范** 的开发流程，您的 App 将会从“简陋”瞬间变成符合 Google 设计奖标准的现代化应用。