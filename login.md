这是为您生成的**身份验证模块（登录/注册 + 指纹）**专属开发文档与 AI 提示词。

这份文档专注于**由 NocoDB 支撑后端，Android 本地进行加密存储与生物识别**的完整闭环。

---

#第一部分：开发规格说明书 (登录/注册模块)##1. 模块概览* **功能**: 用户注册、账号密码登录、**生物识别（指纹/面容）快捷登录**。
* **设计风格**: iOS 风格（大标题、毛玻璃背景、圆角输入框、蓝色主色调）。
* **安全策略**:
* **通讯**: HTTPS。
* **Token**: 使用 `EncryptedSharedPreferences` 存储。
* **指纹**: 使用 `androidx.biometric` 库，仅在本地校验通过后解密 Token。



##2. 数据存储架构 (严格分层)###A. 云端 (NocoDB) - "账号档案库"我们需要在 NocoDB 中建立 `users` 表。

* **Table**: `users`
* **Columns**:
* `email` (SingleLineText, Unique, Required) - 作为账号。
* `password_hash` (SingleLineText) - **注意**：App 端应先进行 SHA-256 或 Argon2 哈希后再上传，或者由后端中间层处理。为简化直连开发，建议 App 端加盐哈希。
* `username` (SingleLineText) - 昵称。
* `avatar_url` (URL) - 头像。
* `biometric_enabled` (Checkbox/Boolean) - 标记该用户是否开启了指纹。



###B. 本地 (Android) - "凭证保险箱"1. **EncryptedSharedPreferences (ESP)**
* 用于存储敏感信息，手机 root 也无法轻易读取。
* **Key**: `auth_token` (登录成功后的 Session/JWT)。
* **Key**: `biometric_token` (用于指纹登录的加密凭证，如密码的加密版或长效 Token)。


2. **Room Database (`current_user` 表)**
* 用于存储非敏感的用户展示信息。
* 字段: `uid`, `username`, `email`, `avatar_local_path`。



##3. 生物识别逻辑 (Biometric Logic)* **注册页**: 注册成功后，弹窗询问 "是否启用指纹登录？"。若同意，调起指纹录入，将加密后的凭证存入 ESP。
* **登录页**:
* 检查 ESP 中是否有 `biometric_token`。
* 如果有，在输入框旁显示“指纹图标”。
* 点击图标或自动弹出系统指纹框 -> 验证通过 -> 自动填入凭证/Token -> 登录成功。



##4. UI 设计规范 (iOS Style)* **输入框**: 高度 50dp，背景色 `#F2F2F7` (浅灰)，圆角 12dp，无边框，光标蓝色。
* **按钮**: 高度 50dp，背景色 `#007AFF` (iOS 蓝)，圆角 12dp，字体加粗。
* **转场**: 登录与注册页面切换使用 `Slide` 动画（类似 iOS 的 Push/Pop）。

---

#第二部分：Cursor 提示词 (AI Prompts)请按顺序将以下提示词发送给 Cursor。

###🚀 Prompt 1: 基础设施搭建 (依赖与数据层)```markdown
# Role
Android Expert specializing in Security and Jetpack Compose.

# Task
Setup the infrastructure for the Authentication Module (Login/Register) for the "AI4Research" app.

# Tech Requirements
1.  **Dependencies**: Add `androidx.biometric:biometric`, `androidx.security:security-crypto`, and `androidx.room` to `build.gradle`.
2.  **Local Security**:
    - Create a `TokenManager` class using `EncryptedSharedPreferences` to securely store/retrieve `auth_token` and `password_hash`.
    - Implement methods: `saveToken`, `getToken`, `clearToken`.
3.  **Local Cache (Room)**:
    - Create a `UserEntity` (id, name, email, avatar).
    - Create `UserDao` with `insertUser` and `getCurrentUser`.
4.  **Backend (NocoDB)**:
    - Create a `AuthService` (Retrofit) interface.
    - Endpoints:
        - `GET /api/v1/db/data/v1/p8bhzq1ltutm8zr/users` (Use query params to filter by email for Login).
        - `POST /api/v1/db/data/v1/p8bhzq1ltutm8zr/users` (For Registration).
    - **Header**: Remember to include `xc-token: lBVvkotCNwFCXz-j1-s3XcE5tXRCp7MzKECOfY2e`.

# Output
Generate the Data Layer code (Entity, DAO, Repository, TokenManager, Retrofit Service).

```

###🚀 Prompt 2: 生物识别管理器 (Biometric Helper)```markdown
# Role
Android System Expert.

# Task
Create a reusable `BiometricHelper` class to handle Fingerprint/FaceID logic.

# Requirements
1.  **Check Capability**: Implement `canAuthenticate()` to check if the hardware exists and fingerprints are enrolled.
2.  **Show Prompt**: Implement `showBiometricPrompt(activity: FragmentActivity, onSuccess: () -> Unit, onError: (String) -> Unit)`.
    - Use `BiometricPrompt` API.
    - Title: "Log in to AI4Research".
    - Negative Button: "Use Password".
3.  **Crypto (Simplified)**: For this MVP, we will treat a successful biometric authentication as a trigger to auto-fill the saved password/token from `EncryptedSharedPreferences`.
4.  **UI Integration**: This helper should be callable from a Composable via a SideEffect or a LaunchedEffect.

# Output
Generate the `BiometricHelper` class and a Composable wrapper if necessary.

```

###🚀 Prompt 3: 登录注册 UI 开发 (iOS 风格)```markdown
# Role
UI/UX Designer (iOS Expert) & Jetpack Compose Developer.

# Task
Build the `LoginScreen` and `RegisterScreen` with strict iOS aesthetics.

# Design System (iOS Style)
- **Background**: White (#FFFFFF).
- **Inputs**:
    - Background: Light Grey (#F2F2F7).
    - Shape: RoundedCornerShape(12.dp).
    - No border.
    - Text: San Francisco style (use System Default, dark grey).
- **Buttons**:
    - Primary Color: iOS Blue (#007AFF).
    - Full width, 50.dp height, RoundedCornerShape(12.dp).
- **Typography**: Large, bold titles ("Log In", "Sign Up") aligned left.

# Features to Implement
1.  **LoginScreen**:
    - Fields: Email, Password (with eye icon to toggle visibility).
    - **Biometric Icon**: If `BiometricHelper.canAuthenticate()` is true, show a FaceID/Fingerprint icon next to the "Log In" button. Clicking it triggers the system prompt.
    - Links: "Forgot Password?" and "Create Account" (Navigates to Register).
2.  **RegisterScreen**:
    - Fields: Username, Email, Password, Confirm Password.
    - Action: "Sign Up".
    - Logic: On success, save user to NocoDB, then prompt: "Enable Fingerprint for future logins?"
3.  **State Management**: Use `AuthViewModel` to handle loading states (CircularProgressIndicator) and error toasts.

# Output
Generate the Composables for Login and Register screens, and the `AuthViewModel` handling the logic.

```

---

###开发中的注意事项 (给您的备忘录)1. **NocoDB 的局限性**:
* NocoDB 本身是一个数据库管理工具，不是专门的 Auth 服务（像 Firebase Auth 那样）。
* **安全提示**: 直接在 App 端调用 NocoDB API 写入 `users` 表意味着你需要把 Token 硬编码在 App 里。在生产环境中，应该有一个 Python 后端来做中转和鉴权。
* **当前方案**: 为了快速开发，我们直接连 NocoDB。**请务必让 Cursor 在代码中实现一个简单的哈希函数（如 SHA-256）**，不要把密码明文上传到 NocoDB。


2. **指纹登录的逻辑**:
* 由于我们没有后端颁发的 OAuth Token 刷新机制，最简单的指纹登录实现方式是：
* 登录成功 -> 把**密码**（或哈希后的密码）加密存入 `EncryptedSharedPreferences`。
* 下次指纹验证通过 -> 从 `EncryptedSharedPreferences` 取出密码 -> 自动调用后台登录接口。




3. **Activity 继承**:
* 使用生物识别时，承载 Compose 的 `MainActivity` 必须继承自 `FragmentActivity` (即 `ComponentActivity` 的子类通常没问题，但要确保支持 `androidx.biometric`)。


4. **权限**:
* 不需要特殊的运行时权限申请，但需要在 `AndroidManifest.xml` 中声明：
```xml
<uses-permission android:name="android.permission.USE_BIOMETRIC" />

```

