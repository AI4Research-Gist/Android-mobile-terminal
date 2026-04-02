# Gist 当前功能清单

## 文档说明

这份文档只整理 **当前已经实现的功能**，按用户理解和使用的自然顺序来写。

- 不把技术细节混进主清单
- 不把未来规划和已上线功能混在一起
- 以当前代码与现有页面入口为准

---

## 1. 这是一个什么产品

Gist 是一个面向科研与学习场景的移动端研究助手。

它解决的核心问题是：把分散出现的 **链接、截图、图片、语音、灵感笔记**，统一收进一个项目化、可检索、可追踪、可继续分析的研究空间里。

---

## 2. 用户最先接触到的功能

### 2.1 进入应用

- 启动页动画
- 注册
- 登录
- 支持用户名登录
- 支持邮箱登录
- 支持手机号登录
- 本地保存登录状态
- 不同账号之间数据隔离

<img src="C:\Users\Lenovo\Downloads/Screenshot_2026-04-02-00-02-46-210_com.example.ai.jpg" alt="Screenshot_2026-04-02-00-02-46-210_com.example.ai" style="zoom: 25%;" />

新人注册账号进行使用，演示账号：demo  密码：GistDemo123，体验尽量使用自己注册的账号，demo账号勿动

### 2.2 应用主结构

- 首页已经收敛为 `灵感` 页
- 底部导航包含：
  - 灵感
  - 论文
  - 资料
  - 比赛
  - 设置
- 支持从主页面进入条目详情页
- 支持从条目进入项目总览页

<img src="C:\Users\Lenovo\Downloads/Screenshot_2026-04-01-23-50-37-752_com.example.ai.jpg" alt="Screenshot_2026-04-01-23-50-37-752_com.example.ai" style="zoom: 25%;" />

### 2.3 当前统一管理的内容类型

- `insight`：灵感
- `paper`：论文
- `article`：资料
- `competition`：比赛
- `voice`：语音条目

---

## 3. 内容是怎么进入系统的

### 3.1 手动录入

- 手动新建灵感
- 灵感支持填写标题
- 灵感支持填写正文
- 灵感支持附加图片
- 灵感支持附加原始语音
- 灵感支持标题必填、其余选填的轻量录入方式

<img src="C:\Users\Lenovo\Downloads/Screenshot_2026-04-01-23-50-35-572_com.example.ai.jpg" alt="Screenshot_2026-04-01-23-50-35-572_com.example.ai" style="zoom:25%;" />

### 3.2 语音采集

- 独立语音录制入口
- 录音
- 录音时长显示
- 语音转文字
- AI 对转写结果进行润色
- 用户确认后保存为语音条目

### 3.3 链接采集

- 手动输入链接导入
- 剪贴板链接自动识别
- 支持识别常见论文/网页链接
- 链接可先入库再后台解析
- 前端可看到解析状态
- 前端可看到解析耗时

### 3.4 截图与图片采集

- 全屏截图采集
- 区域截图采集
- 图片 OCR 识别
- 截图内容可转成结构化条目
- 图片识别链路支持更稳的超时、重试、缩图降级

<img src="C:\Users\Lenovo\Downloads/Screenshot_2026-04-02-00-07-56-623_com.example.ai.jpg" alt="Screenshot_2026-04-02-00-07-56-623_com.example.ai" style="zoom:25%;" />

### 3.5 悬浮窗快捷采集

- 全局悬浮球
- 悬浮球快捷打开采集菜单
- 从悬浮窗发起截图
- 从悬浮窗发起区域截图
- 从悬浮窗发起链接导入
- 采集完成后跳回主应用对应栏目

<img src="C:\Users\Lenovo\AppData\Roaming\Typora\typora-user-images\image-20260402000918247.png" alt="image-20260402000918247" style="zoom: 33%;" />

---

## 4. 进入系统后，用户能怎么浏览和管理内容

### 4.1 统一内容管理

- 统一管理论文、资料、比赛、灵感、语音
- 搜索
- 按项目筛选
- 星标
- 已读/未读管理
- 删除条目

<img src="C:\Users\Lenovo\AppData\Roaming\Typora\typora-user-images\image-20260402001017814.png" alt="image-20260402001017814" style="zoom: 50%;" />

### 4.2 灵感页

- 页面内直接新建灵感
- 搜索灵感
- 按分类查看灵感
- 支持隐藏已读灵感
- 支持显示已读灵感
- 灵感按时间组织展示

**如上图**，用以记载瞬时想法，支持**语音**、**文字**、**图片**三种输入

### 4.3 论文页

- 查看论文卡片列表
- 论文搜索
- 按项目筛选
- 按年份筛选
- 按来源或会议筛选
- 按关键词或标签筛选
- 展示结构化摘要信息

<img src="D:\新建文件夹\Tencent Files\3146364920\nt_qq\nt_data\Pic\2026-04\Ori\0763e9173bf6337df281908a1cf43d2c.jpeg" alt="0763e9173bf6337df281908a1cf43d2c" style="zoom:25%;" />

用以储存解析的论文，并自动解析呈现**关键词**和**概要**、**来源**、**作者**等关键信息

### 4.4 资料页

- 查看资料卡片列表
- 资料搜索
- 按项目筛选
- 按年份筛选
- 按作者或来源筛选
- 按关键词或标签筛选
- 本地优先显示资料占位，减少空白等待

<img src="C:\Users\Lenovo\Downloads/Screenshot_2026-04-01-23-49-58-970_com.example.ai.jpg" alt="Screenshot_2026-04-01-23-49-58-970_com.example.ai" style="zoom: 25%;" />

可存储例如**数据集、小红书、微信公众号、知乎等碎片化信息**来源并整合**提取关键词**

### 4.5 比赛页

- 查看比赛卡片列表
- 展示比赛截止时间
- 展示时间线信息
- 按紧迫度排序查看

<img src="C:\Users\Lenovo\Downloads/Screenshot_2026-04-01-23-50-01-628_com.example.ai.jpg" alt="Screenshot_2026-04-01-23-50-01-628_com.example.ai" style="zoom:25%;" />

可根据原文解析**多种时间节点**，并补充了**手动输入渠道**，实时显示**比赛剩余时间动态**

---

## 5. 点进详情后，用户能做什么

### 5.1 通用详情能力

- Markdown 阅读
- 编辑摘要
- 编辑正文
- 编辑笔记
- 保存内容
- 标记已读
- 星标/取消星标
- 删除条目
- 修改项目归属

<img src="C:\Users\Lenovo\Downloads/Screenshot_2026-04-02-00-17-37-906_com.example.ai.jpg" alt="Screenshot_2026-04-02-00-17-37-906_com.example.ai" style="zoom:25%;" /><img src="C:\Users\Lenovo\Downloads/Screenshot_2026-04-02-00-17-42-829_com.example.ai.jpg" alt="Screenshot_2026-04-02-00-17-42-829_com.example.ai" style="zoom:25%;" />



### 5.2 灵感详情

- 查看灵感正文
- 查看上传图片
- 播放原始语音
- 编辑灵感关联关系

### 5.3 论文与资料详情

- 查看结构化阅读卡

<img src="D:\新建文件夹\Tencent Files\3146364920\nt_qq\nt_data\Pic\2026-04\Ori\212a18b01b49855755a8ea37da3c245f.jpeg" alt="212a18b01b49855755a8ea37da3c245f" style="zoom:25%;" />

- 生成结构化阅读卡草稿
- 重新生成双语摘要

<img src="C:\Users\Lenovo\AppData\Roaming\Typora\typora-user-images\image-20260402002113017.png" alt="image-20260402002113017" style="zoom: 50%;" /><img src="D:\新建文件夹\Tencent Files\3146364920\nt_qq\nt_data\Pic\2026-04\Ori\e23ab3fed94a9e23b86bad38bdf6d58c.jpeg" alt="e23ab3fed94a9e23b86bad38bdf6d58c" style="zoom: 25%;" /><img src="C:\Users\Lenovo\AppData\Roaming\Typora\typora-user-images\image-20260402002146355.png" alt="image-20260402002146355" style="zoom: 50%;" />

- 围绕当前条目发起 AI 问答

<img src="C:\Users\Lenovo\AppData\Roaming\Typora\typora-user-images\image-20260402002347237.png" alt="image-20260402002347237" style="zoom: 50%;" />

- 选择另一条资料做双文献对比

<img src="C:\Users\Lenovo\AppData\Roaming\Typora\typora-user-images\image-20260402002407812.png" alt="image-20260402002407812" style="zoom:50%;" /><img src="C:\Users\Lenovo\AppData\Roaming\Typora\typora-user-images\image-20260402002416425.png" alt="image-20260402002416425" style="zoom:50%;" />

### 5.4 OCR 与解析失败后的兜底

- OCR 条目重试识别
- 长 OCR 内容优先使用更完整摘要
- 详情问答优先利用 OCR 原文和中等摘要构造上下文

---

## 6. 这个应用里的 AI 到底做了什么

### 6.1 采集阶段的 AI

- 解析链接内容
- 提取网页核心内容
- 识别图片文字
- 识别并整理截图内容
- 语音转写
- 语音文本优化

### 6.2 阅读阶段的 AI

- 生成摘要
- 生成双语摘要
- 生成结构化阅读卡
- 围绕当前条目回答问题

### 6.3 研究推进阶段的 AI

- 为灵感反查相关论文/资料
- 给出推荐理由
- 支持一键采纳推荐结果为关联
- 对两篇文献做结构化对比
- 为项目生成 AI 总结
- 为项目研究背景生成摘要与关键词

---

## 7. 项目功能：系统不只是存东西，还会按项目组织

### 7.1 项目管理

- 手动创建项目
- 将条目归属到项目
- 从详情页调整项目归属

<img src="C:\Users\Lenovo\AppData\Roaming\Typora\typora-user-images\image-20260402002633155.png" alt="image-20260402002633155" style="zoom: 50%;" />

### 7.2 项目总览

- 查看项目概况
- 查看最近新增内容
- 查看重点论文
- 查看灵感汇总
- 查看关系统计

<img src="C:\Users\Lenovo\AppData\Roaming\Typora\typora-user-images\image-20260402002723998.png" alt="image-20260402002723998" style="zoom:33%;" />

### 7.3 项目背景

- 上传项目研究背景 Markdown
- 自动生成背景摘要
- 自动提取关键词

<img src="C:\Users\Lenovo\AppData\Roaming\Typora\typora-user-images\image-20260402002801340.png" alt="image-20260402002801340" style="zoom:33%;" />、

**注意：这里可以上传自己编译好格式的markdown作为背景！**

### 7.4 项目级 AI 总结

- 生成当前项目主题
- 生成最近进展总结
- 提取关键文献
- 识别待补问题
- 给出下一步建议

<img src="C:\Users\Lenovo\AppData\Roaming\Typora\typora-user-images\image-20260402002848309.png" alt="image-20260402002848309" style="zoom: 50%;" /><img src="C:\Users\Lenovo\AppData\Roaming\Typora\typora-user-images\image-20260402002906856.png" alt="image-20260402002906856" style="zoom: 50%;" />

---

## 8. 知识连接：内容之间不是孤立的

- `paper` 支持去重分组
- `article -> paper` 支持自动关联
- `insight` 支持手动关联已有条目
- 灵感支持反查相关资料后再建立关联
- 文献之间支持对比分析

---

## 9. 导出、同步和诊断能力

### 9.1 导出

- 项目总结导出为 Markdown
- 双文献对比结果导出为 Markdown

### 9.2 同步

- 本地数据库作为主要读取来源
- 本地与云端同步
- 未上云条目自动补传
- 新导入内容可先本地占位，再继续异步同步

### 9.3 诊断

- 查看当前用户信息
- 查看本地条目数量
- 查看未上云条目数量
- 查看最近同步状态
- 查看前端接收数据情况
- <img src="D:\新建文件夹\Tencent Files\3146364920\nt_qq\nt_data\Pic\2026-04\Ori\cf115e16946f7ce9507de3802fb5c4a0.jpeg" alt="cf115e16946f7ce9507de3802fb5c4a0" style="zoom: 25%;" />

### 9.4 研究回顾

- 查看本周新增
- 查看本周新增论文
- 查看本周新增灵感
- 查看未读积压
- 查看近期比赛截止提醒
- 查看最活跃项目

<img src="D:\新建文件夹\Tencent Files\3146364920\nt_qq\nt_data\Pic\2026-04\Ori\a6bd3a05af53e32ba8f486bd31e6b2ba.jpeg" alt="a6bd3a05af53e32ba8f486bd31e6b2ba" style="zoom: 25%;" />

---

## 10. 系统层支持能力

- 亮色/暗色主题切换
- WebView 预热与缓存
- Splash 等待初始化完成
- 数据库升级改为正式 migration
- 升级版本时不再默认清空本地数据库
- 悬浮窗权限管理
- 截图授权管理

---

## 11. 一句话总结

如果用最直接的话来概括，Gist 当前已经具备的是一套完整的研究工作流：

**先采集，再整理，再阅读，再关联，再总结，再回顾。**

它已经不只是“记笔记”，而是一个把研究资料、灵感、项目和 AI 分析串起来的移动端研究助手。
