# Cloud / Web API Integration Guide

本文档基于当前安卓端实现整理，面向：

- Web 端开发者
- 后端开发者
- 需要与安卓端联调数据结构的同学

文档目标：

- 说明安卓端当前实际依赖的远端接口
- 说明安卓端当前使用的数据字段
- 说明本次 `v0.1.0` 新增的论文索引字段契约
- 说明 `meta_json` 的兼容规则与消费建议

本文档重点关注“数据契约”和“联调约定”，不展开安卓本地 Room 细节。

---

## 1. 当前服务概览

当前安卓端依赖两类远端服务：

### 1.1 NocoDB

用途：

- `items` 数据增删改查
- `projects` 数据增删改查
- `users` 数据注册、登录、更新

当前 Base URL：

```text
http://8.152.222.163:8080/api/v1/db/data/v1/p8bhzq1ltutm8zr/
```

来源代码：

- [Constants.kt](D:/Android-mobile-terminal/app/src/main/java/com/example/ai4research/core/util/Constants.kt)

### 1.2 SiliconFlow

用途：

- 文本结构化解析
- 链接解析
- 视觉识别 / OCR
- 语音转写

当前 Base URL：

```text
https://api.siliconflow.cn/v1/
```

来源代码：

- [SiliconFlowApiService.kt](D:/Android-mobile-terminal/app/src/main/java/com/example/ai4research/data/remote/api/SiliconFlowApiService.kt)

---

## 2. NocoDB 认证方式

安卓端当前对所有 NocoDB 请求统一附带：

```http
xc-token: <NOCO_TOKEN>
Accept: application/json
Content-Type: application/json
```

说明：

- 当前 token 仍在客户端配置中
- 这适合比赛/demo 阶段，不适合作为正式生产方案
- 后续建议迁移为后端代理或正式认证体系

来源代码：

- [NocoAuthInterceptor.kt](D:/Android-mobile-terminal/app/src/main/java/com/example/ai4research/core/network/NocoAuthInterceptor.kt)

---

## 3. 表与路径映射

当前安卓端固定依赖以下三张表：

| 业务对象 | NocoDB 路径 |
|---|---|
| items | `/mez4qicxcudfwnc` |
| projects | `/m14rejhia8w9cf7` |
| users | `/m1j18kc9fkjhcio` |

来源代码：

- [NocoApiService.kt](D:/Android-mobile-terminal/app/src/main/java/com/example/ai4research/data/remote/api/NocoApiService.kt)

---

## 4. Items 接口

## 4.1 获取全部 Items

```http
GET /mez4qicxcudfwnc?limit=100&offset=0&sort=-CreatedAt
```

安卓端用途：

- 刷新本地条目缓存

返回结构示例：

```json
{
  "list": [],
  "pageInfo": {
    "totalRows": 0,
    "page": 1,
    "pageSize": 100,
    "isFirstPage": true,
    "isLastPage": true
  }
}
```

安卓端当前会过滤掉：

- `title` 为空的记录
- `type` 为空的记录

## 4.2 获取单个 Item

```http
GET /mez4qicxcudfwnc/{id}
```

## 4.3 创建 Item

```http
POST /mez4qicxcudfwnc
Content-Type: application/json
```

安卓端常见请求字段：

```json
{
  "title": "示例标题",
  "type": "paper",
  "summary": "主摘要",
  "content_md": "Markdown 正文",
  "origin_url": "https://example.com",
  "audio_url": null,
  "status": "done (完成)",
  "read_status": "unread (未读)",
  "tags": "tag1,tag2",
  "project_id": 1,
  "meta_json": {
    "authors": ["A", "B"],
    "conference": "ACL",
    "year": "2025",
    "identifier": "10.1000/demo",
    "domain_tags": ["LLM"],
    "keywords": ["retrieval"],
    "method_tags": ["RAG"],
    "dedup_key": "10.1000/demo",
    "summary_short": "两句以内的极短摘要",
    "note": "用户备注"
  }
}
```

安卓端注意事项：

- `meta_json` 当前是重点字段
- `note` 当前也放在 `meta_json` 中
- `tags` 仍然使用逗号分隔字符串，尚未改成数组字段

## 4.4 更新 Item

```http
PATCH /mez4qicxcudfwnc/{id}
Content-Type: application/json
```

安卓端当前更新策略：

- 往往不是只 patch 单字段
- 通常会带主要字段一起提交
- 安卓端本地已做“远端响应缺字段时回退本地请求体”的兼容，避免 `meta_json` 丢失

建议后端保持：

- PATCH 支持部分字段更新
- 不要求客户端严格传全字段

## 4.5 删除 Item

```http
DELETE /mez4qicxcudfwnc/{id}
```

安卓端当前行为：

- 先删本地
- 再 best-effort 删除远端

这意味着：

- 如果远端删除失败，本地可能已经看不到这条记录

---

## 5. Projects 接口

## 5.1 获取全部 Projects

```http
GET /m14rejhia8w9cf7?limit=100
```

用途：

- 安卓端刷新项目列表
- 用于把 `project_id` 映射成 `project_name`

## 5.2 获取单个 Project

```http
GET /m14rejhia8w9cf7/{id}
```

## 5.3 创建 Project

```http
POST /m14rejhia8w9cf7
Content-Type: application/json
```

安卓端当前请求示例：

```json
{
  "Title": "项目名",
  "name": "项目名",
  "description": "项目描述"
}
```

安卓端读取规则：

- 优先使用 `name`
- `name` 为空时回退到 `Title`

## 5.4 删除 Project

```http
DELETE /m14rejhia8w9cf7/{id}
```

---

## 6. Users 接口

当前用户体系本质上仍是直接操作 `users` 表，不是正式认证服务。

这意味着：

- 注册：直接插表
- 登录：直接按 where 条件查表
- 本地保存的是 `user.id`，不是标准 token

这套方式适合 demo 阶段，不适合正式生产。

## 6.1 注册

```http
POST /m1j18kc9fkjhcio
Content-Type: application/json
```

安卓端当前请求示例：

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

## 6.2 登录

```http
GET /m1j18kc9fkjhcio?where=...
```

安卓端支持三种标识登录：

- 邮箱
- 用户名
- 手机号

---

## 7. `items` 表字段契约

安卓端当前依赖以下字段：

| 字段 | 类型 | 说明 |
|---|---|---|
| `Id` | Int | 主键 |
| `ownerId` | String | 当前条目所属用户 |
| `title` | String | 标题 |
| `type` | String | 条目类型 |
| `summary` | String | 主摘要 |
| `content_md` | String | Markdown 正文 |
| `origin_url` | String? | 原始链接 |
| `audio_url` | String? | 音频地址 |
| `status` | String | 条目状态 |
| `read_status` | String | 阅读状态 |
| `tags` | String? | 逗号分隔字符串 |
| `project_id` | Int? | 项目外键 |
| `meta_json` | JSON / String | 结构化扩展字段 |
| `CreatedAt` | String | 创建时间 |
| `UpdatedAt` | String | 更新时间 |

### 7.1 `type` 枚举

安卓端当前识别：

```text
paper
competition
insight
voice
```

未知值会回退成：

```text
insight
```

### 7.2 `status` 枚举

安卓端当前实际写入通常为：

```text
processing (解析中)
done (完成)
failed (失败)
```

但客户端解析只依赖英文前缀，因此服务端只要前缀稳定即可：

```text
processing
done
failed
```

### 7.3 `read_status` 枚举

安卓端当前实际写入通常为：

```text
unread (未读)
reading (在读)
read (已读)
```

客户端同样只依赖英文前缀：

```text
unread
reading
read
```

---

## 8. `meta_json` 契约

这是当前 `v0.1.0` 最重要的字段。

### 8.1 Paper

推荐结构：

```json
{
  "authors": ["Ashish Vaswani", "Noam Shazeer"],
  "conference": "NeurIPS",
  "year": "2017",
  "source": "arxiv",
  "identifier": "1706.03762",
  "domain_tags": ["LLM", "NLP"],
  "keywords": ["transformer", "attention"],
  "method_tags": ["Transformer"],
  "dedup_key": "1706.03762",
  "summary_short": "两句以内的极短摘要",
  "tags": ["transformer", "attention"],
  "note": "用户备注"
}
```

### 8.2 Competition

```json
{
  "organizer": "Kaggle",
  "prizePool": "10000 USD",
  "deadline": "2026-12-31",
  "theme": "AI for Science",
  "competitionType": "algorithm",
  "website": "https://example.com",
  "registrationUrl": "https://example.com/register",
  "timeline": [
    {
      "name": "报名截止",
      "date": "2026-12-31",
      "isPassed": false
    }
  ]
}
```

### 8.3 Insight

```json
{
  "source": "灵感",
  "tags": ["idea", "product"],
  "note": "用户备注"
}
```

### 8.4 Voice

```json
{
  "duration": 90,
  "transcription": "语音转写文本",
  "note": "用户备注"
}
```

---

## 9. `meta_json` 兼容规则

安卓端当前兼容以下两种返回形式：

### 9.1 对象形式

```json
"meta_json": {
  "identifier": "1706.03762",
  "year": "2017"
}
```

### 9.2 字符串形式

```json
"meta_json": "{\"identifier\":\"1706.03762\",\"year\":\"2017\"}"
```

安卓端都会尝试正常解析。

对 Web / 后端的建议：

- 最好统一返回对象形式
- 但当前如暂时无法统一，字符串 JSON 也可以被安卓兼容

---

## 10. 安卓端当前展示策略

### 10.1 列表页

论文列表卡片当前优先展示：

- `title`
- 作者
- `conference/source + year`
- `identifier`
- `summary_short`
- `note`
- 标签

### 10.2 详情页

论文详情页当前优先展示：

- 论文索引卡
- `summary_short`
- `note`
- 原始 Markdown / 正文内容

### 10.3 类型自动修正

当解析结果带有明显学术特征时，即使模型返回：

- `article`
- `insight`

安卓端也可能把它归到：

- `paper`

依据包括：

- `identifier`
- `conference`
- `year`
- `arxiv`
- `doi`

---

## 11. SiliconFlow / 模型说明

当前安卓端通过 SiliconFlow 调用模型。

相关代码：

- [AIService.kt](D:/Android-mobile-terminal/app/src/main/java/com/example/ai4research/service/AIService.kt)
- [SiliconFlowApiService.kt](D:/Android-mobile-terminal/app/src/main/java/com/example/ai4research/data/remote/api/SiliconFlowApiService.kt)

### 当前实际生效配置

| 能力 | Provider | 当前模型 |
|---|---|---|
| 文本结构化解析 | SiliconFlow | `Qwen/Qwen3.5-397B-A17B` |
| 视觉识别 / OCR | SiliconFlow | `Pro/moonshotai/Kimi-K2.5` |
| 语音转写 | SiliconFlow | `FunAudioLLM/SenseVoiceSmall` |

### 说明

- 当前项目仍然通过 **SiliconFlow API** 调用模型
- 即使模型名是 Qwen 或 Kimi，也不是直连对应官方 SDK
- 如果需要查看调用日志、计费或用量，应优先查看 SiliconFlow 控制台
- 模型切换不会改变 Web 端字段名，但可能改变字段完整率与摘要质量

### 配置位置

- API Key：
  - [AIService.kt](D:/Android-mobile-terminal/app/src/main/java/com/example/ai4research/service/AIService.kt)
- 文本模型 / 视觉模型 / ASR 模型：
  - [SiliconFlowApiService.kt](D:/Android-mobile-terminal/app/src/main/java/com/example/ai4research/data/remote/api/SiliconFlowApiService.kt)

---

## 12. Web 端接入建议

### 12.1 不要只消费 `summary`

如果 Web 端只读：

- `title`
- `summary`

会丢掉本次版本最重要的结构化索引能力。

建议优先消费：

- `meta_json.identifier`
- `meta_json.conference`
- `meta_json.year`
- `meta_json.domain_tags`
- `meta_json.keywords`
- `meta_json.method_tags`
- `meta_json.dedup_key`
- `meta_json.summary_short`
- `meta_json.note`

### 12.2 机器字段与用户字段分离

语义上必须区分：

- 机器字段：索引、标签、去重键、短摘要
- 用户字段：`note`

不要把 `note` 当成摘要字段，也不要让后续自动补全覆盖它。

### 12.3 去重逻辑建议

Web / 后端建议优先使用：

1. `identifier`
2. `dedup_key`
3. 规范化 `title + year`

安卓端当前只是提供辅助字段，不建议把安卓端结果当成唯一真相来源。

---

## 13. 当前已知边界

### 13.1 `year / keywords / method_tags` 不是强确定字段

这些字段当前仍可能缺失，原因包括：

- 原网页没有明确提供
- 抓取内容不完整
- AI 只能做推断而不是纯抽取

### 13.2 `summary_short` 更适合入口展示

它适合：

- 列表页
- 卡片页
- 手机端快速浏览

它不适合替代：

- 原始英文摘要
- 全部正文内容

### 13.3 当前仍是安卓端优先

本期还没有完成：

- Web UI
- 桌面整理工作台
- 旧记录批量重建索引

---

## 14. 关键代码位置

### 远端 DTO
- [NocoItemDto.kt](D:/Android-mobile-terminal/app/src/main/java/com/example/ai4research/data/remote/dto/NocoItemDto.kt)

### NocoDB 接口
- [NocoApiService.kt](D:/Android-mobile-terminal/app/src/main/java/com/example/ai4research/data/remote/api/NocoApiService.kt)

### AI / 结构化解析
- [AIService.kt](D:/Android-mobile-terminal/app/src/main/java/com/example/ai4research/service/AIService.kt)

### 采集保存入口
- [FloatingWindowService.kt](D:/Android-mobile-terminal/app/src/main/java/com/example/ai4research/service/FloatingWindowService.kt)

### 数据映射
- [ItemMapper.kt](D:/Android-mobile-terminal/app/src/main/java/com/example/ai4research/data/mapper/ItemMapper.kt)

### 数据持久化
- [ItemRepositoryImpl.kt](D:/Android-mobile-terminal/app/src/main/java/com/example/ai4research/data/repository/ItemRepositoryImpl.kt)

### 列表桥接
- [MainViewModel.kt](D:/Android-mobile-terminal/app/src/main/java/com/example/ai4research/ui/main/MainViewModel.kt)

### 详情页展示
- [DetailScreen.kt](D:/Android-mobile-terminal/app/src/main/java/com/example/ai4research/ui/detail/DetailScreen.kt)

---

## 15. 结论

`v0.1.0` 后，安卓端已经不再只是“标题 + 摘要”的弱结构化采集，而是开始稳定产出论文索引字段。

对于 Web / 后端开发者来说，当前最重要的事情不是复刻安卓 UI，而是：

- 对齐 `meta_json` 字段结构
- 对齐 `summary_short / note / identifier / dedup_key` 语义
- 按当前契约继续扩展整理、过滤、去重和深处理能力
