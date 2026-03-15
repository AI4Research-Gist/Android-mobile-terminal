# Gist 云端 / Web 对接接口说明

本文档基于当前 Android 代码实现整理，目标读者是云端与 Web 开发同学。

重点说明：
- Android 当前实际调用了哪些服务端接口
- 每个接口的路径、请求方式、主要字段和调用习惯
- NocoDB 表结构与字段契约
- 客户端对字段值的实际解析规则

不展开 Android 本地 Room 细节，只保留与云端契约有关的同步说明。

## 1. 当前服务概览

当前 Android 端依赖两类远端服务：

1. NocoDB
   - 用途：研究条目、项目、用户数据的增删改查
   - Base URL:
     `http://8.152.222.163:8080/api/v1/db/data/v1/p8bhzq1ltutm8zr/`

2. SiliconFlow
   - 用途：文本总结、视觉识别、语音转写
   - Base URL:
     `https://api.siliconflow.cn/v1/`

## 2. NocoDB 认证方式

Android 对所有 NocoDB 请求统一附带以下 Header：

```http
xc-token: <NOCO_TOKEN>
Accept: application/json
Content-Type: application/json
```

说明：
- Token 当前由客户端硬编码在 `Constants.NOCO_TOKEN` 中。
- 不建议继续沿用“客户端硬编码 Token”方案到正式环境。
- 若后续云端做统一网关，建议改成应用后端签发临时凭证，或至少把静态 Token 移出客户端代码。

## 3. NocoDB 表与路径映射

当前 Android 代码固定依赖以下三张表：

| 业务对象 | NocoDB 表 ID | 路径 |
|---|---|---|
| items | `mez4qicxcudfwnc` | `/mez4qicxcudfwnc` |
| projects | `m14rejhia8w9cf7` | `/m14rejhia8w9cf7` |
| users | `m1j18kc9fkjhcio` | `/m1j18kc9fkjhcio` |

## 4. Items 接口

### 4.1 获取全部 Items

```http
GET /mez4qicxcudfwnc?limit=100&offset=0&sort=-CreatedAt
```

用途：
- Android 启动刷新时拉全量条目

返回结构：

```json
{
  "list": [ ... ],
  "pageInfo": {
    "totalRows": 0,
    "page": 1,
    "pageSize": 100,
    "isFirstPage": true,
    "isLastPage": true
  }
}
```

客户端注意：
- Android 会过滤掉 `title` 为空或 `type` 为空的记录
- 排序默认按 `CreatedAt` 倒序

### 4.2 按类型查询 Items

```http
GET /mez4qicxcudfwnc?where=(type,eq,paper)&limit=100
```

用途：
- 代码里已定义，但当前主刷新流程主要还是全量拉取后本地筛选

### 4.3 获取单个 Item

```http
GET /mez4qicxcudfwnc/{id}
```

### 4.4 创建 Item

```http
POST /mez4qicxcudfwnc
Content-Type: application/json
```

Android 当前创建时常用字段：

```json
{
  "title": "示例标题",
  "type": "paper",
  "summary": "摘要",
  "content_md": "Markdown 正文",
  "origin_url": "https://example.com",
  "audio_url": null,
  "status": "processing (解析中)",
  "read_status": "unread (未读)",
  "tags": "tag1,tag2",
  "project_id": 1,
  "meta_json": {
    "authors": ["A", "B"],
    "conference": "ACL",
    "year": 2025
  }
}
```

客户端注意：
- Android 发送 JSON 时不会主动带 `Id: null`
- `meta_json` 是 JSON 对象，不是字符串
- `tags` 当前走逗号拼接字符串，不是数组

### 4.5 更新 Item

```http
PATCH /mez4qicxcudfwnc/{id}
Content-Type: application/json
```

Android 当前更新策略：
- 大多数更新是“带完整主要字段一起 PATCH”，不是只发改单字段
- 以下场景会调用：
  - 修改条目类型
  - 修改阅读状态
  - 修改标题 / 摘要 / 正文 / tags / meta_json
  - 修改项目归属

建议服务端兼容：
- PATCH 支持部分字段更新
- 即使客户端传的是“接近全量”的对象，也不要要求字段顺序或严格全字段

### 4.6 删除 Item

```http
DELETE /mez4qicxcudfwnc/{id}
```

客户端注意：
- Android 当前是“先删本地，再尽力删远端”
- 也就是说，远端删除失败时，客户端本地可能已经看不到这条数据

## 5. Projects 接口

### 5.1 获取全部 Projects

```http
GET /m14rejhia8w9cf7?limit=100
```

用途：
- Android 在刷新 items 前，会先刷新 projects，用于把 `project_id` 映射成 `project_name`

### 5.2 获取单个 Project

```http
GET /m14rejhia8w9cf7/{id}
```

### 5.3 创建 Project

```http
POST /m14rejhia8w9cf7
Content-Type: application/json
```

Android 当前请求体：

```json
{
  "Title": "项目名",
  "name": "项目名",
  "description": "项目描述"
}
```

客户端注意：
- Android 同时写 `Title` 和 `name`
- 客户端读取时优先用 `name`，如果 `name` 为空则回退到 `Title`

### 5.4 删除 Project

```http
DELETE /m14rejhia8w9cf7/{id}
```

## 6. Users 接口

当前用户体系本质上不是独立认证服务，而是“直接查 NocoDB users 表”。

这意味着：
- 注册是直接往表里插入一行
- 登录是通过 `where` 条件查询用户记录
- 密码当前是按明文查询

这套方式能跑通，但安全性较弱，正式环境建议后续替换。

### 6.1 注册

```http
POST /m1j18kc9fkjhcio
Content-Type: application/json
```

Android 当前请求体：

```json
{
  "Phonenumber": "13800000000",
  "email": "user@example.com",
  "password": "plain_password",
  "username": "tester",
  "avatar_url": null,
  "biometric_enabled": false
}
```

### 6.2 登录

```http
GET /m1j18kc9fkjhcio?where=...
```

Android 当前支持三种登录标识：

1. 邮箱登录

```text
(email,eq,user@example.com)~and(password,eq,plain_password)
```

2. 用户名登录

```text
(username,eq,tester)~and(password,eq,plain_password)
```

3. 手机号登录

```text
(Phonenumber,eq,13800000000)~and(password,eq,plain_password)
```

客户端注意：
- Android 登录成功后，把 `user.id` 当作本地 token 保存
- 当前不是 JWT，也不是 session token，只是用户 ID

### 6.3 检查用户名是否存在

```http
GET /m1j18kc9fkjhcio?where=(username,eq,tester)
```

### 6.4 获取用户详情

```http
GET /m1j18kc9fkjhcio/{id}
```

### 6.5 更新用户

```http
PATCH /m1j18kc9fkjhcio/{id}
Content-Type: application/json
```

Android 当前主要用在：
- 更新 `biometric_enabled`

## 7. Items 表字段契约

Android 当前对 `items` 表的字段依赖如下：

| 字段名 | 类型 | 说明 |
|---|---|---|
| `Id` | Int | NocoDB 主键 |
| `title` | String | 标题，不能为空，否则 Android 会过滤 |
| `type` | String | 条目类型，不能为空，否则 Android 会过滤 |
| `summary` | String | 摘要 |
| `content_md` | String | Markdown 正文 |
| `origin_url` | String? | 原始链接或图片地址 |
| `audio_url` | String? | 语音文件地址 |
| `status` | String | 条目状态 |
| `read_status` | String | 阅读状态 |
| `tags` | String? | 逗号分隔字符串 |
| `project_id` | Int? | 项目外键 |
| `meta_json` | JSON? | 结构化扩展数据 |
| `CreatedAt` | String | 创建时间 |
| `UpdatedAt` | String | 更新时间 |

### 7.1 `type` 枚举

客户端识别以下值：

```text
paper
competition
insight
voice
```

未知值会被 Android 回退成 `insight` 处理。

### 7.2 `status` 枚举

Android 当前写入的完整值通常是：

```text
processing (解析中)
done (完成)
failed (失败)
```

但客户端解析逻辑只看英文前缀，所以服务端至少需要保证前缀稳定：

```text
processing
done
failed
```

### 7.3 `read_status` 枚举

Android 当前写入的完整值通常是：

```text
unread (未读)
reading (在读)
read (已读)
```

客户端解析时同样只看英文前缀：

```text
unread
reading
read
```

### 7.4 `meta_json` 约定

#### paper

```json
{
  "authors": ["A", "B"],
  "conference": "ACL",
  "year": 2025,
  "tags": ["nlp", "llm"]
}
```

#### competition

```json
{
  "timeline": [],
  "prizePool": "10000 USD",
  "organizer": "xxx",
  "deadline": "2026-12-31",
  "theme": "AI for Science",
  "competitionType": "algorithm",
  "website": "https://example.com",
  "registrationUrl": "https://example.com/register"
}
```

#### insight

```json
{
  "tags": ["idea", "product"]
}
```

#### voice

```json
{
  "duration": 90,
  "transcription": "语音转写文本"
}
```

客户端兼容性说明：
- `authors` / `tags` 既兼容数组，也兼容逗号字符串
- `year` / `duration` 既兼容数字，也兼容数字字符串

## 8. Projects 表字段契约

| 字段名 | 类型 | 说明 |
|---|---|---|
| `Id` | Int | 主键 |
| `Title` | String? | 项目标题 |
| `name` | String? | 项目名 |
| `description` | String? | 描述 |
| `CreatedAt` | String | 创建时间 |

客户端读取规则：
- 优先使用 `name`
- 若 `name` 为空，则回退到 `Title`
- 若两者都为空，客户端会把项目名兜底成“未命名项目”

## 9. Users 表字段契约

| 字段名 | 类型 | 说明 |
|---|---|---|
| `Id` | Int | 主键 |
| `Phonenumber` | String? | 手机号 |
| `email` | String | 邮箱 |
| `password` | String | 当前客户端按明文使用 |
| `username` | String | 用户名 |
| `avatar_url` | String? | 头像 |
| `biometric_enabled` | Boolean? | 是否启用生物识别 |
| `CreatedAt` | String | 创建时间 |
| `UpdatedAt` | String | 更新时间 |

## 10. 时间字段格式

Android 目前能解析以下时间格式：

```text
yyyy-MM-dd HH:mm:ssXXX
yyyy-MM-dd HH:mm:ss
yyyy-MM-dd'T'HH:mm:ss.SSS'Z'
yyyy-MM-dd'T'HH:mm:ss'Z'
```

建议云端统一输出：

```text
yyyy-MM-dd HH:mm:ss+00:00
```

## 11. Android 当前实际同步行为

给云端同学最关键的几点：

1. 首次刷新时，Android 会先拉 `projects`，再拉 `items`
2. Android UI 主要消费的是本地缓存，不是每次现查远端
3. `updateStarred` 当前只改本地，不回写远端
4. `deleteItem` 是本地先删，远端 best-effort
5. 登录成功后本地保存的是 `user.id`，不是标准 token

如果你们要做云端 Web 管理台，建议优先统一以下行为：
- 让 `starred` 也有远端字段
- 把登录从“查 users 表”升级成真正认证接口
- 保持 `status` / `read_status` 的英文前缀稳定

## 12. SiliconFlow 接口

这部分不是 NocoDB，但 Android 当前也依赖它。

### 12.1 文本对话

```http
POST /chat/completions
Authorization: Bearer <API_KEY>
```

用途：
- 文本总结
- 链接解析

模型：

```text
Qwen/Qwen2.5-14B-Instruct
```

### 12.2 视觉对话

```http
POST /chat/completions
Authorization: Bearer <API_KEY>
```

用途：
- 截图 OCR / 图片理解

模型：

```text
Qwen/Qwen2.5-VL-32B-Instruct
```

### 12.3 音频转写

```http
POST /audio/transcriptions
Authorization: Bearer <API_KEY>
Content-Type: multipart/form-data
```

用途：
- 语音录制转文本

模型：

```text
FunAudioLLM/SenseVoiceSmall
```

## 13. 对云端 / Web 开发的直接建议

### 建议优先保持兼容的部分

1. 不要改动三张 NocoDB 表的路径 ID
2. 保持 `items.type` 的四个值不变：
   - `paper`
   - `competition`
   - `insight`
   - `voice`
3. 保持 `status` / `read_status` 的英文前缀不变
4. `meta_json` 保持 JSON 对象，不要强制改成纯字符串
5. `projects` 表继续兼容 `Title` 和 `name`

### 建议后续升级的部分

1. 把 users 登录改成正式认证接口，不再直接查表
2. 不要让客户端继续持有 NocoDB 静态 token
3. 增加服务端统一业务 API，逐步把客户端从“直连 NocoDB”迁移出去
4. 给 `items` 增加真正的远端 `is_starred` 字段，和 Android 行为对齐

## 14. 代码来源

本文档对应当前仓库中的这些实现：

- `app/src/main/java/com/example/ai4research/core/util/Constants.kt`
- `app/src/main/java/com/example/ai4research/core/network/NocoAuthInterceptor.kt`
- `app/src/main/java/com/example/ai4research/data/remote/api/NocoApiService.kt`
- `app/src/main/java/com/example/ai4research/data/remote/api/SiliconFlowApiService.kt`
- `app/src/main/java/com/example/ai4research/data/remote/dto/NocoItemDto.kt`
- `app/src/main/java/com/example/ai4research/data/remote/dto/NocoUserDto.kt`
- `app/src/main/java/com/example/ai4research/data/repository/AuthRepository.kt`
- `app/src/main/java/com/example/ai4research/data/repository/ItemRepositoryImpl.kt`
- `app/src/main/java/com/example/ai4research/data/repository/ProjectRepositoryImpl.kt`
- `app/src/main/java/com/example/ai4research/domain/model/ResearchItem.kt`

