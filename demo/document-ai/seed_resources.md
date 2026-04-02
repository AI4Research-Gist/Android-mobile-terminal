# 多模态文档智能 Demo 资源清单

## 使用原则

这份清单按演示价值来组织：

- 先放适合导入成 `paper` 的论文
- 再放适合导入成 `article` 的资料与基准页面
- 再放适合导入成 `competition` 的比赛页面

如果你想快速把项目跑起来，建议至少先导入：

- 3 篇论文
- 2 条资料
- 1 个比赛
- 1 份项目背景 Markdown

---

## 一、推荐导入为 `paper` 的论文

### 1. LayoutLMv3: Pre-training for Document AI with Unified Text and Image Masking

- 类型：`paper`
- 作用：适合讲“文档 AI 的经典多模态预训练路线”
- 导入链接：https://arxiv.org/abs/2204.08387
- 推荐标签：`document-ai` `layout` `multimodal` `pretraining`
- 适合在详情页问 AI：
  - 它解决了哪些 Document AI 任务？
  - 它和传统 OCR 管线相比的价值是什么？

### 2. OCR-free Document Understanding Transformer (Donut)

- 类型：`paper`
- 作用：适合讲“为什么要减少对 OCR 的依赖”
- 导入链接：https://arxiv.org/abs/2111.15664
- 推荐标签：`document-ai` `ocr-free` `transformer`
- 适合在详情页问 AI：
  - Donut 为什么被称为 OCR-free？
  - 它适合哪些真实应用场景？

### 3. M-LongDoc: A Benchmark For Multimodal Super-Long Document Understanding And A Retrieval-Aware Tuning Framework

- 类型：`paper`
- 作用：适合讲“长文档、多页、跨图文理解”
- 导入链接：https://openreview.net/forum?id=5zjsZiYEnr
- 推荐标签：`long-document` `benchmark` `retrieval`
- 适合在详情页问 AI：
  - 长文档理解比单页理解多了哪些难点？
  - 文档检索为什么对问答性能重要？

### 4. UniDoc-Bench: A Unified Benchmark for Document-Centric Multimodal RAG

- 类型：`paper`
- 作用：适合讲“文档场景下的多模态 RAG”
- 导入链接：https://openreview.net/forum?id=iGDv1e4rFY
- 推荐标签：`multimodal-rag` `benchmark` `document-qa`
- 适合在详情页问 AI：
  - 为什么文档场景需要单独的 MM-RAG 基准？
  - 它对系统设计有什么启发？

### 5. VisR-Bench: An Empirical Study on Visual Retrieval-Augmented Generation for Multilingual Long Document Understanding

- 类型：`paper`
- 作用：适合讲“多语言 + 长文档 + 视觉检索”
- 导入链接：https://openreview.net/forum?id=7iFZ6uzILL
- 推荐标签：`multilingual` `visual-rag` `long-document`
- 适合在详情页问 AI：
  - 多语言文档检索最难的部分是什么？
  - 表格与低资源语言为什么更难？

### 6. PaddleOCR-VL-1.5: Towards a Multi-Task 0.9B VLM for Robust In-the-Wild Document Parsing

- 类型：`paper`
- 作用：适合讲“轻量级、可部署、面向真实复杂场景的文档解析模型”
- 导入链接：https://arxiv.org/abs/2601.21957
- 推荐标签：`paddleocr` `multilingual` `document-parsing`
- 适合在详情页问 AI：
  - 为什么轻量模型对落地重要？
  - 它对复杂真实场景下的文档解析有什么意义？

---

## 二、推荐导入为 `article` 的资料与基准页面

### 1. DocVQA 2026 数据集说明页

- 类型：`article`
- 作用：适合演示“比赛资料页 + 问答 + 项目总结”
- 导入链接：https://huggingface.co/datasets/VLR-CVC/DocVQA-2026
- 推荐标签：`docvqa` `benchmark` `document-vqa`

### 2. ICDAR 2026 DocVQA 官方任务页

- 类型：`article`
- 作用：适合演示“官方比赛规则页解析”
- 导入链接：https://rrc.cvc.uab.es/?ch=34&com=tasks
- 推荐标签：`competition-rule` `document-vqa`

### 3. PaddleOCR 官方仓库

- 类型：`article`
- 作用：适合演示“工程资料页解析”
- 导入链接：https://github.com/PaddlePaddle/PaddleOCR
- 推荐标签：`ocr` `toolchain` `engineering`

### 4. Hugging Face Donut 模型文档

- 类型：`article`
- 作用：适合演示“模型说明页解析”
- 导入链接：https://huggingface.co/docs/transformers/model_doc/donut
- 推荐标签：`donut` `model-doc` `document-understanding`

### 5. Hugging Face LayoutLMv3 模型文档

- 类型：`article`
- 作用：适合演示“模型说明页解析”
- 导入链接：https://huggingface.co/docs/transformers/model_doc/layoutlmv3
- 推荐标签：`layoutlmv3` `model-doc` `layout-understanding`

---

## 三、推荐导入为 `competition` 的比赛

### 1. DocVQA 2026

- 类型：`competition`
- 官方页面：https://rrc.cvc.uab.es/?ch=34&com=tasks
- 数据页：https://huggingface.co/datasets/VLR-CVC/DocVQA-2026
- 演示价值：
  - 能展示比赛信息管理
  - 能展示多模态文档问答任务
  - 能展示“把规则、数据和论文放在一个项目里统一管理”
- 日期说明：
  - 官方 RRC 任务页显示测试集方法提交截止为 **2026-04-03**
  - 官方 RRC 任务页显示报告提交截止为 **2026-04-10**
  - Hugging Face 数据页写的是报告提交 **2026-04-17**
  - 演示时建议以官方 RRC 页面为准

### 2. LAVA Challenge 2026

- 类型：`competition`
- 官方页面：https://lava-workshop.github.io/
- 演示价值：
  - 强调多语言 PDF 文档理解
  - 强调 evidence-grounded answering
  - 非常适合和“文档问答、证据追踪、项目总结”联动
- 已确认信息：
  - 挑战将与 ACM Multimedia 2026 同期举行
  - 时间为 **2026-11-10 至 2026-11-14**

### 3. COLIEE 2026

- 类型：`competition`
- 官方页面：https://coliee.org/COLIEE2026/overview
- 演示价值：
  - 强调长文档检索、信息抽取、蕴含判断
  - 适合扩展成“法律文档智能”子方向
- 已确认信息：
  - 官网显示训练数据分发注册于 **2026-01-15** 开放
  - 官网显示报名已于 **2026-02-15** 关闭
  - 官网显示 ICAIL 2026 相关 workshop 日期为 **2026-06-12**

---

## 四、建议你在系统里如何组织这些内容

### 项目名

`多模态文档智能与科研知识管理`

### 推荐条目结构

#### `paper`

- LayoutLMv3
- Donut
- M-LongDoc
- UniDoc-Bench
- VisR-Bench
- PaddleOCR-VL
- PaddleOCR-VL-1.5

#### `article`

- DocVQA 2026 数据页
- DocVQA 2026 官方任务页
- PaddleOCR 仓库
- Donut 文档页
- LayoutLMv3 文档页

#### `competition`

- DocVQA 2026
- LAVA Challenge 2026
- COLIEE 2026

#### `insight`

建议你手动补 3 到 5 条灵感，示例如下：

- OCR-free 路线是否更适合移动端场景？
- 长文档问答里，检索比生成更关键吗？
- 比赛规则页能否自动整理成任务卡片？
- 能否把项目背景、论文和比赛统一进一个研究图谱？

---

## 五、最适合演示的 AI 问题

### 针对单篇论文

- 这篇论文的核心贡献是什么？
- 它解决的是文档理解中的哪一类问题？
- 它适合做研究原型，还是适合做工程落地？

### 针对两篇论文对比

- LayoutLMv3 和 Donut 的核心路线差别是什么？
- UniDoc-Bench 和 M-LongDoc 分别评估什么能力？
- PaddleOCR-VL 和 Donut 在落地取舍上有什么不同？

### 针对项目级总结

- 当前这个方向最值得优先投入的是模型能力、检索能力还是系统整合能力？
- 如果以比赛为牵引，这个项目下一步应该补哪些能力？
- 这个项目适合先做科研助手，还是先做企业知识库助手？
