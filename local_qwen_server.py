import sys
import time
import uuid
from typing import Any

sys.path.insert(0, r"d:\Android-mobile-terminal\.pydeps")

import torch
from fastapi import FastAPI, Header, HTTPException
from pydantic import BaseModel, Field
from transformers import AutoModelForCausalLM, AutoTokenizer


MODEL_DIR = r"d:\Android-mobile-terminal\models\Qwen3.5-0.8B"
MODEL_NAME = "Qwen3.5-0.8B-local"


class Message(BaseModel):
    role: str
    content: str


class ChatCompletionRequest(BaseModel):
    model: str | None = None
    messages: list[Message]
    max_tokens: int = Field(default=128, ge=1, le=1024)
    temperature: float = Field(default=0.4, ge=0.0, le=2.0)
    stream: bool = False


class GenerateRequest(BaseModel):
    message: str
    max_new_tokens: int = Field(default=128, ge=1, le=1024)
    temperature: float = Field(default=0.4, ge=0.0, le=2.0)


print("Loading tokenizer from", MODEL_DIR)
tokenizer = AutoTokenizer.from_pretrained(MODEL_DIR, trust_remote_code=True)

print("Loading model from", MODEL_DIR)
model = AutoModelForCausalLM.from_pretrained(
    MODEL_DIR,
    trust_remote_code=True,
    dtype=torch.float16,
    device_map="auto",
)
model.eval()

app = FastAPI(title="Local Qwen Server")


def _build_prompt(messages: list[Message]) -> str:
    if hasattr(tokenizer, "apply_chat_template"):
        return tokenizer.apply_chat_template(
            [message.model_dump() for message in messages],
            tokenize=False,
            add_generation_prompt=True,
        )

    lines = []
    for message in messages:
        lines.append(f"{message.role}: {message.content}")
    lines.append("assistant:")
    return "\n".join(lines)


def _generate_from_prompt(prompt: str, max_new_tokens: int, temperature: float) -> dict[str, Any]:
    device = next(model.parameters()).device
    inputs = tokenizer(prompt, return_tensors="pt").to(device)
    prompt_tokens = inputs["input_ids"].shape[-1]

    with torch.no_grad():
        outputs = model.generate(
            **inputs,
            max_new_tokens=max_new_tokens,
            temperature=temperature,
            do_sample=temperature > 0,
            top_p=0.9,
            repetition_penalty=1.1,
            pad_token_id=tokenizer.eos_token_id,
        )

    generated_ids = outputs[0][prompt_tokens:]
    generated_text = tokenizer.decode(generated_ids, skip_special_tokens=True).strip()

    return {
        "text": generated_text,
        "prompt_tokens": prompt_tokens,
        "completion_tokens": generated_ids.shape[-1],
    }


@app.get("/health")
def health() -> dict[str, Any]:
    return {
        "status": "ok",
        "model": MODEL_NAME,
        "device": str(next(model.parameters()).device),
    }


@app.post("/generate")
def generate(req: GenerateRequest) -> dict[str, Any]:
    result = _generate_from_prompt(
        prompt=req.message,
        max_new_tokens=req.max_new_tokens,
        temperature=req.temperature,
    )
    return {"text": result["text"]}


@app.post("/v1/chat/completions")
def chat_completions(
    req: ChatCompletionRequest,
    authorization: str | None = Header(default=None),
) -> dict[str, Any]:
    if req.stream:
        raise HTTPException(status_code=400, detail="stream is not supported by this local server")

    if not req.messages:
        raise HTTPException(status_code=400, detail="messages must not be empty")

    prompt = _build_prompt(req.messages)
    result = _generate_from_prompt(
        prompt=prompt,
        max_new_tokens=req.max_tokens,
        temperature=req.temperature,
    )

    return {
        "id": f"chatcmpl-{uuid.uuid4().hex}",
        "object": "chat.completion",
        "created": int(time.time()),
        "model": req.model or MODEL_NAME,
        "choices": [
            {
                "index": 0,
                "message": {
                    "role": "assistant",
                    "content": result["text"],
                },
                "finish_reason": "stop",
            }
        ],
        "usage": {
            "prompt_tokens": result["prompt_tokens"],
            "completion_tokens": result["completion_tokens"],
            "total_tokens": result["prompt_tokens"] + result["completion_tokens"],
        },
        "authorization_ignored": authorization is not None,
    }
