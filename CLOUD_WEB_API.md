# Cloud / Web API Integration Guide

本文档基于当前安卓端实现整理，面向：

- Web 端开发者
- 后端开发者
- 需要与安卓端联调数据契约的同学

当前版本口径：`v0.2.0`

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
<AI4RESEARCH_NOCO_BASE_URL>
```

### 1.2 SiliconFlow

用途：

- 文本结构化解析
- 链接解析
- 视觉识别 / OCR
- 语音转写

当前 Base URL：

```text
<AI4RESEARCH_SILICONFLOW_BASE_URL>
```

---

## 2. 当前模型与 provider

当前安卓端默认模型配置如下：

| 能力 | Provider | 当前模型 |
|---|---|---|
| 文本结构化解析 | SiliconFlow | `Qwen/Qwen3.5-397B-A17B` |
| 视觉识别 / OCR | SiliconFlow | `Pro/moonshotai/Kimi-K2.5` |
| 语音转写 | SiliconFlow | `FunAudioLLM/SenseVoiceSmall` |

说明：

- 当前项目不是直连 Qwen 官方 SDK
- 当前项目也不是直连 Moonshot / Kimi 官方 SDK
- 所有模型请求仍通过 SiliconFlow API
- `v0.2.0` 已完成安卓端 OCR 截图链路稳定化，当前视觉识别调用可作为资料模块 OCR 兜底的基础能力继续扩展

---

## 3. NocoDB 认证方式

安卓端当前对所有 NocoDB 请求统一附带：

```http
xc-token: <NOCO_TOKEN>
Accept: application/json
Content-Type: application/json
```

说明：

- 当前 token 仍在客户端配置中
- 这适合 demo / 比赛阶段，不适合作为正式生产方案

---

## 4. 表与路径映射

当前安卓端固定依赖以下三张表：

| 业务对象 | NocoDB 路径 |
|---|---|
| items | `/mez4qicxcudfwnc` |
| projects | `/m14rejhia8w9cf7` |
| users | `/m1j18kc9fkjhcio` |

---

## 5. `items` 字段契约

安卓端当前依赖以下字段：

| 字段 | 类型 | 说明 |
|---|---|---|
| `Id` | Int | 主键 |
| `ownerId` | String | 条目所属用户 |
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

### 5.1 `type` 枚举

当前识别：

```text
paper
article
competition
insight
voice
```

---

## 6. `meta_json` 契约

这是当前 `v0.1.5` 最重要的字段。

### 6.1 Paper

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
  "summary_zh": "中文摘要",
  "summary_en": "英文摘要",
  "tags": ["transformer", "attention"],
  "note": "用户备注"
}
```

### 6.2 Article

```json
{
  "platform": "wechat|xiaohongshu|douyin|web",
  "account_name": "string|null",
  "author": "string|null",
  "publish_date": "string|null",
  "summary_short": "string|null",
  "keywords": ["string"],
  "topic_tags": ["string"],
  "core_points": ["string"],
  "referenced_links": ["string"],
  "paper_candidates": [
    {
      "url": "string",
      "label": "string|null",
      "kind": "arxiv|doi|paper_page|pdf|unknown"
    }
  ],
  "note": "string|null"
}
```

### 6.3 Competition

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

### 6.4 Insight

```json
{
  "source": "灵感",
  "tags": ["idea", "product"],
  "note": "用户备注"
}
```

### 6.5 Voice

```json
{
  "duration": 90,
  "transcription": "语音转写文本",
  "note": "用户备注"
}
```

---

## 7. `meta_json` 兼容规则

安卓端当前兼容以下两种返回形式：

### 7.1 对象形式

```json
"meta_json": {
  "identifier": "1706.03762",
  "year": "2017"
}
```

### 7.2 字符串形式

```json
"meta_json": "{\"identifier\":\"1706.03762\",\"year\":\"2017\"}"
```

安卓端都会尝试正常解析。

---

## 8. 安卓端当前展示策略

### 8.1 论文

列表页优先展示：

- `title`
- 作者
- `conference/source + year`
- `identifier`
- `summary_short`
- `note`

详情页优先展示：

- 论文索引卡
- `summary_short`
- `summary_zh / summary_en`
- `note`
- 正文内容

### 8.2 资料（article）

当前方向已进入开发：

- 新增独立 `资料` 入口
- article 列表页基础结构已开始落地
- article 详情页基础信息卡已开始接入
- `paper_candidates` 识别已开始接入

当前仍处于“骨架搭建 + 字段贯通”阶段。

---

## 9. Web 端接入建议

### 9.1 不要只消费 `summary`

如果 Web 端只读：

- `title`
- `summary`

会丢掉本次版本最重要的结构化能力。

建议优先消费：

#### Paper

- `meta_json.identifier`
- `meta_json.conference`
- `meta_json.year`
- `meta_json.domain_tags`
- `meta_json.keywords`
- `meta_json.method_tags`
- `meta_json.dedup_key`
- `meta_json.summary_short`
- `meta_json.summary_zh`
- `meta_json.summary_en`
- `meta_json.note`

#### Article

- `meta_json.platform`
- `meta_json.account_name`
- `meta_json.author`
- `meta_json.publish_date`
- `meta_json.summary_short`
- `meta_json.keywords`
- `meta_json.topic_tags`
- `meta_json.core_points`
- `meta_json.referenced_links`
- `meta_json.paper_candidates`
- `meta_json.note`

### 9.2 机器字段与用户字段分离

语义上必须区分：

- 机器字段：索引、标签、候选链接、短摘要
- 用户字段：`note`

不要把 `note` 当成摘要，也不要被后续自动补全覆盖。

---

## 10. 当前已知边界

### 10.1 `year / keywords / method_tags` 不是强确定字段

这些字段仍可能缺失，原因包括：

- 原网页未明确提供
- 抓取内容不完整
- AI 只能推断而不是纯抽取

### 10.2 资料入口仍在开发中

当前 `article` 方向已经有：

- 类型
- 元数据结构
- 列表页骨架
- 详情页骨架
- 候选论文字段

但还没有完全收尾：

- 首页“最近资料”区块
- 候选论文交互（复制 / 打开）
- 一键导入论文

---

## 11. 关键代码位置

### 远端 DTO
- [NocoItemDto.kt](D:/Android-mobile-terminal/app/src/main/java/com/example/ai4research/data/remote/dto/NocoItemDto.kt)

### AI / 结构化解析
- [AIService.kt](D:/Android-mobile-terminal/app/src/main/java/com/example/ai4research/service/AIService.kt)

### 数据映射
- [ItemMapper.kt](D:/Android-mobile-terminal/app/src/main/java/com/example/ai4research/data/mapper/ItemMapper.kt)

### 数据持久化
- [ItemRepositoryImpl.kt](D:/Android-mobile-terminal/app/src/main/java/com/example/ai4research/data/repository/ItemRepositoryImpl.kt)

### 主页面桥接
- [MainViewModel.kt](D:/Android-mobile-terminal/app/src/main/java/com/example/ai4research/ui/main/MainViewModel.kt)
- [MainScreen.kt](D:/Android-mobile-terminal/app/src/main/java/com/example/ai4research/ui/main/MainScreen.kt)
- [main_ui.html](D:/Android-mobile-terminal/app/src/main/assets/main_ui.html)

### 详情页展示
- [DetailScreen.kt](D:/Android-mobile-terminal/app/src/main/java/com/example/ai4research/ui/detail/DetailScreen.kt)
- [DetailViewModel.kt](D:/Android-mobile-terminal/app/src/main/java/com/example/ai4research/ui/detail/DetailViewModel.kt)

### 跳转映射
- [MainActivity.kt](D:/Android-mobile-terminal/app/src/main/java/com/example/ai4research/MainActivity.kt)

---

## 12. 结论

`v0.1.5` 后，当前安卓端已经具备两个清晰方向：

1. 论文索引增强与双语摘要能力已经可用  
2. 资料入口（article）方向已经进入实际开发

对于 Web / 后端开发者来说，当前最重要的事情不是复刻安卓 UI，而是：

- 对齐 `meta_json` 的 paper / article 字段结构
- 接受“article 方向已开始接入但尚未全部收尾”的现状
- 以后续资料整理与论文候选联动为目标继续扩展
