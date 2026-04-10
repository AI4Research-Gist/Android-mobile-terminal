# 本地 Qwen 服务启动说明

这份说明用于在电脑上把 `models/Qwen3.5-0.8B` 跑成一个本地 HTTP 服务。

## 1. 准备目录

在项目根目录执行：

```powershell
cd d:\Android-mobile-terminal
mkdir .pydeps -Force
mkdir .piptmp2 -Force
$env:TEMP='d:\Android-mobile-terminal\.piptmp2'
$env:TMP='d:\Android-mobile-terminal\.piptmp2'
```

## 2. 安装依赖

按顺序执行，便于观察进度：

```powershell
python -m pip install --target .pydeps -i https://pypi.tuna.tsinghua.edu.cn/simple safetensors
python -m pip install --target .pydeps -i https://pypi.tuna.tsinghua.edu.cn/simple sentencepiece
python -m pip install --target .pydeps -i https://pypi.tuna.tsinghua.edu.cn/simple accelerate
python -m pip install --target .pydeps -i https://pypi.tuna.tsinghua.edu.cn/simple transformers
python -m pip install --target .pydeps -i https://pypi.tuna.tsinghua.edu.cn/simple fastapi uvicorn pydantic
```

如果 `.pydeps` 里出现了单独安装的 `torch`，建议删掉，让脚本优先使用系统里已经可用的 CUDA 版 `torch`：

```powershell
Get-ChildItem .pydeps | Where-Object { $_.Name -like 'torch*' -or $_.Name -like 'functorch*' } | Remove-Item -Recurse -Force
```

## 3. 验证依赖

```powershell
python -c "import torch, sys; print('torch=', torch.__version__, 'cuda=', torch.cuda.is_available()); sys.path.insert(0, r'd:\Android-mobile-terminal\.pydeps'); import transformers, accelerate, safetensors, fastapi, uvicorn, huggingface_hub; print('transformers=', transformers.__version__); print('accelerate=', accelerate.__version__); print('safetensors=', safetensors.__version__); print('fastapi=', fastapi.__version__); print('uvicorn=', uvicorn.__version__); print('huggingface_hub=', huggingface_hub.__version__)"
```

## 4. 启动本地服务

服务脚本已经在项目根目录：

- `local_qwen_server.py`

启动命令：

```powershell
python -c "import sys; sys.path.insert(0, r'd:\Android-mobile-terminal\.pydeps'); import uvicorn; uvicorn.run('local_qwen_server:app', host='0.0.0.0', port=8001)"
```

## 5. 测试服务

健康检查：

```powershell
curl http://127.0.0.1:8001/health
```

简单文本生成：

```powershell
curl -X POST http://127.0.0.1:8001/generate -H "Content-Type: application/json" -d "{\"message\":\"请用两句话介绍大语言模型\",\"max_new_tokens\":80,\"temperature\":0.4}"
```

OpenAI 兼容接口测试：

```powershell
curl -X POST http://127.0.0.1:8001/v1/chat/completions -H "Content-Type: application/json" -d "{\"model\":\"Qwen3.5-0.8B-local\",\"messages\":[{\"role\":\"system\",\"content\":\"你是一个简洁的中文助手\"},{\"role\":\"user\",\"content\":\"请用一句话介绍你自己\"}],\"max_tokens\":80,\"temperature\":0.4}"
```

## 6. 和 Android 项目对接

当前本地服务已经提供了一个最小的 OpenAI 兼容接口：

- `POST /v1/chat/completions`

这意味着后续可以让 Android 项目把基地址切到：

- `http://你的电脑IP:8001/v1/`

然后优先把轻任务切过来，例如：

- `enhanceTranscription()`
- `parseLinkStructured()`
- `answerQuestionAboutItem()`

重任务仍然保留云端：

- 图片理解
- 音频转写
- 项目级总结
- 双文献深度对比
