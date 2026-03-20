# Article Entry PRD

状态：当前开发版 PRD（v0.1.5 基线）  
适用范围：本轮及下一轮安卓端开发直接按本文档执行  
本轮目标：把“资料”从首页动态中解放出来，做成独立入口，并补上文章中的论文链接识别能力

---

## 1. 本轮要解决的问题

当前来自以下平台的内容：

- 公众号
- 小红书
- 抖音
- 普通网页文章

在安卓端解析后，主要被放进首页动态流。

这会带来几个问题：

- 内容进入动态流后很快沉底
- 这类内容本质上是“资料”，不是“瞬时动态”
- 后续不方便检索、回看和整理
- 和论文、灵感、语音混在一起，信息结构不清楚
- 文章中的论文链接没有被结构化利用

因此本轮真正要解决的是：

1. 给资料一个独立入口  
2. 把文章中的论文线索识别出来

---

## 2. 本轮产品结论

本轮结论非常明确：

1. 新增一个独立入口：`资料`
2. 来自公众号 / 小红书 / 抖音 / 网页文章的内容，统一归入 `article / 资料`
3. 首页继续保留，但只做“总览”，不再承担资料主入口职责
4. 本轮在 `article` 中补上论文链接识别
5. 本轮不改首页成灵感页
6. 本轮不做“一键导入论文”

一句话概括：

`首页保留总览，资料单独成页，文章里先把论文线索识别出来。`

---

## 3. 本轮不做什么

本轮明确不做：

- 不重构首页为灵感主页
- 不重做整套信息架构
- 不把灵感拆成独立底部入口
- 不把语音拆成独立底部入口
- 不做 `paper_candidate -> paper` 一键导入
- 不做候选去重
- 不做 article 与 paper 正式关联

---

## 4. 导航结构

本轮建议底部导航调整为：

- 首页
- 论文
- 资料
- 比赛
- 设置

### 首页定位

首页继续保留，作为：

- 最近内容总览页
- Dashboard / Overview

首页只负责展示：

- 最近论文
- 最近资料
- 最近灵感
- 最近语音
- 比赛提醒

首页不再承担资料主管理职责。

### 资料页定位

新增 `资料` 页，作为以下内容的主入口：

- 公众号文章
- 小红书图文
- 抖音内容说明
- 普通网页文章

它是“资料内容层”的正式入口。

---

## 5. 类型设计

本轮新增一个正式类型：

- `article`

用于承接：

- 公众号内容
- 小红书内容
- 抖音内容说明
- 普通网页文章

### 类型边界

#### `paper`
- 正式论文对象
- DOI / arXiv / venue 更明确的学术内容

#### `article`
- 来源明确的资料型内容
- 有正文、有来源、有后续整理价值

#### `insight`
- 碎片灵感
- 一句话想法
- 没有明确来源的个人内容

#### `voice`
- 语音输入内容

---

## 6. 本轮数据结构

建议 `article` 当前结构如下：

```json
{
  "id": "string",
  "type": "article",
  "title": "string|null",
  "summary": "string|null",
  "content_md": "string|null",
  "origin_url": "string",
  "status": "processing|done|failed",
  "read_status": "unread|reading|read",
  "project_id": "string|null",
  "project_name": "string|null",
  "meta_json": {
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
}
```

---

## 7. 本轮必须产出的字段

### P0 必须有

- `title`
- `platform`
- `origin_url`

### P1 尽量有

- `account_name`
- `author`
- `publish_date`
- `summary_short`
- `keywords`
- `topic_tags`
- `core_points`
- `referenced_links`
- `paper_candidates`

### 用户字段

- `note`

### 本轮不要求强落地

- 一键送入论文解析通道
- 候选去重
- article 与 paper 正式关联

---

## 8. 安卓端展示要求

### 8.1 首页

首页继续保留，但新增：

- “最近资料”区块

### 8.2 资料页

资料页建议展示：

- 标题
- 平台
- 账号 / 作者
- 发布时间
- 极短摘要
- 关键词
- 若含论文候选，显示“含论文线索”

### 8.3 资料详情页

详情页建议展示：

1. 基本信息
- 标题
- 平台
- 账号 / 作者
- 发布时间
- 原始链接

2. 内容摘要
- `summary_short`
- `core_points`

3. 标签区
- `keywords`
- `topic_tags`

4. 论文线索区
- `paper_candidates`
- 候选论文链接展示
- 链接类型展示

5. 备注区
- `note`

6. 正文区
- `content_md`

---

## 9. 解析策略

本轮解析策略服务于两件事：

1. 把资料变成资料卡片  
2. 把文章中的论文线索识别出来

建议统一解析输出：

- `title`
- `platform`
- `account_name`
- `author`
- `publish_date`
- `summary_short`
- `keywords`
- `topic_tags`
- `core_points`
- `referenced_links`
- `paper_candidates`

### 平台处理原则

本轮不为每个平台做重型独立链路。

统一思路：

- 平台识别
- 抓取正文
- 结构化为 `article`

### 论文线索识别规则

本轮要求做到：

- 提取 `referenced_links`
- 识别 `paper_candidates`
- 在详情页展示候选论文链接

符合以下任一条件即可进入候选：

- 链接包含 `arxiv.org`
- 链接包含 `doi.org`
- 链接为 PDF 且上下文像论文
- 链接域名属于常见论文站点：
  - `openreview.net`
  - `aclanthology.org`
  - `ieeexplore.ieee.org`
  - `dl.acm.org`
  - `springer.com`
  - `nature.com`

候选结构建议：

```json
{
  "url": "https://arxiv.org/abs/2501.12345",
  "label": "文章里出现的上下文标题或说明",
  "kind": "arxiv"
}
```

### 本轮不要求的后续能力

- 自动导入论文
- 候选去重
- article -> paper 自动关联

---

## 10. 本轮开发任务拆解

### 任务 1：新增 `article` 类型支持

- 数据层支持 `article`
- UI 层识别并展示 `article`
- 桥接层输出 `article`

### 任务 2：新增底部导航 `资料`

- 导航入口
- 页面容器
- 页面数据源

### 任务 3：首页保留总览，但增加“最近资料”

- 首页新增资料区块
- 与灵感、论文、语音区分清楚

### 任务 4：资料列表页

- 文章卡片样式
- 平台 / 时间 / 摘要 / 标签展示
- 论文线索标识

### 任务 5：资料详情页

- 基础信息区
- 摘要区
- 关键词区
- 论文线索区
- 备注区
- 正文区

### 任务 6：论文线索识别

- 提取 `referenced_links`
- 识别 `paper_candidates`
- 在详情页展示候选链接

---

## 11. 本轮验收标准

满足以下条件即可认为本轮达标：

1. 公众号 / 小红书 / 抖音 / 网页文章解析后不再只进首页动态
2. 资料有独立入口
3. 首页仍然保留总览，不被重构成灵感主页
4. 资料页可查看文章列表
5. 资料详情页可展示结构化内容
6. 资料详情页能展示论文候选链接
7. 备注仍然保留

---

## 12. 暂缓事项

以下内容明确留到后续版本：

- 一键导入论文
- `paper_candidate` 去重
- article 与 paper 的关联关系
- 首页是否最终取消或改成灵感主页

---

## 13. 最终结论

本轮不做大架构重构，不做首页重做，不做论文候选一键导入。

本轮做两件强相关但仍然收敛的事：

1. 新增“资料”独立入口，把资料从首页动态里解放出来  
2. 在资料详情里识别并展示论文线索

这就是当前版本应该执行的 PRD。
