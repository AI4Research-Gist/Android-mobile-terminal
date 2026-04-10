# 主项目接入说明

这份说明对应主项目里已经预留好的端侧模型接入骨架。

## 当前主项目已经做好的部分

主项目已经新增了这些能力：

- 端侧模型设置存储
- 端侧模型安装/加载状态
- 轻任务路由骨架
- 本地模型管理器
- App 启动时自动扫描本地模型目录

相关代码位置：

- `app/src/main/java/com/example/ai4research/ai/`
- `app/src/main/java/com/example/ai4research/ai/local/`

## 当前默认推荐模型

主项目里当前默认预留的模型规格是：

- `qwen3-0.6b-q4f16_0-mlc`

显示名：

- `Qwen3-0.6B-q4f16_0-MLC`

## 模型目录约定

主项目会在 App 私有目录下查找模型：

- `filesDir/models/qwen3-0.6b-q4f16_0-mlc`

也就是说，后续如果 MLC 打包产物要接到主项目里，最终应该把模型目录内容落到这个路径。

## App 启动时会发生什么

`AI4ResearchApp` 启动时会调用：

- `OnDeviceModelBootstrapper.initialize()`

它会：

1. 扫描 `filesDir/models/`
2. 查找是否存在推荐模型目录
3. 如果找到，就把模型状态标记为 `READY`
4. 如果没找到，就只同步现有设置，不影响云端逻辑

## 内存占用策略

主项目里已经实现了一个基础策略：

- 模型文件常驻存储
- 真的开始推理时才加载 runtime
- 每次本地推理都会刷新“活跃时间”
- 空闲约 3 分钟后自动卸载模型

这意味着后续即使模型装在手机里，也不会默认一直占着运行内存。

## 当前已接好路由的轻任务

`AIService` 已经接入了端侧路由入口，但端侧 runtime 还是空实现，因此当前行为仍然是云端优先。

已接入路由的轻任务：

- `parseLink`
- `enhanceTranscription`
- `answerQuestionAboutItem`

## 后续真正接入 MLC 时要做什么

只需要把当前空实现：

- `NoOpLocalLlmEngine`

替换为真正的 MLC 运行时实现即可。

也就是说，后续真正需要做的核心工作是：

1. 实现 `LocalLlmEngine`
2. 从 MLC runtime 加载 `filesDir/models/qwen3-0.6b-q4f16_0-mlc`
3. 把生成结果返回给 `LocalAiBackend`

业务层和路由层已经提前预留好了。
