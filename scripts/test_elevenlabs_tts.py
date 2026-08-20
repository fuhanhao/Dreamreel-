#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""测试 TokenFree 上四个 ElevenLabs 模型（openai-audio /v1/audio/speech）。"""
import json
import pathlib
import sys
import time
import urllib.error
import urllib.request

ROOT = pathlib.Path(__file__).resolve().parents[1]
LOCAL_YML = ROOT / "services" / "api" / "application-local.yml"
OUT_DIR = ROOT / "data" / "tts-test"
REPORT = OUT_DIR / "report.json"

# 公开短音频，用于 audio-isolation 测试
SAMPLE_AUDIO_URL = (
    "https://www2.cs.uic.edu/~i101/SoundFiles/ImperialMarch60.wav"
)


def load_config():
    text = LOCAL_YML.read_text(encoding="utf-8")
    key = base = None
    for line in text.splitlines():
        line = line.strip()
        if line.startswith("api-key:"):
            key = line.split(":", 1)[1].strip()
        if line.startswith("base-url:"):
            base = line.split(":", 1)[1].strip().rstrip("/")
    if not key or not base:
        raise SystemExit("missing api-key/base-url in application-local.yml")
    return base, key


def call_speech(base, key, payload, timeout=180):
    data = json.dumps(payload, ensure_ascii=False).encode("utf-8")
    req = urllib.request.Request(
        f"{base}/v1/audio/speech",
        data=data,
        headers={
            "Authorization": f"Bearer {key}",
            "Content-Type": "application/json",
        },
        method="POST",
    )
    started = time.time()
    try:
        with urllib.request.urlopen(req, timeout=timeout) as resp:
            body = resp.read()
            elapsed = int((time.time() - started) * 1000)
            return resp.status, resp.headers.get("Content-Type"), body, elapsed, None
    except urllib.error.HTTPError as ex:
        err = ex.read().decode("utf-8", errors="replace")
        elapsed = int((time.time() - started) * 1000)
        return ex.code, None, None, elapsed, err
    except Exception as ex:
        elapsed = int((time.time() - started) * 1000)
        return None, None, None, elapsed, str(ex)


def elevenlabs_metadata(text, voice, *, language_code=None):
    meta = {
        "text": text,
        "voice": voice,
        "stability": 0.5,
        "similarity_boost": 0.75,
        "speed": 1,
    }
    if language_code:
        meta["language_code"] = language_code
    return meta


def main():
    base, key = load_config()
    OUT_DIR.mkdir(parents=True, exist_ok=True)

    tests = [
        (
            "multilingual-v2-rachel",
            "elevenlabs/text-to-speech-multilingual-v2",
            "单音色 TTS（多语言 v2）",
            {
                "model": "elevenlabs/text-to-speech-multilingual-v2",
                "input": "Hello, this is Rachel speaking.",
                "voice": "Rachel",
                "response_format": "mp3",
                "metadata": elevenlabs_metadata(
                    "Hello, this is Rachel speaking.", "Rachel"
                ),
            },
        ),
        (
            "turbo-2-5-roger-zh",
            "elevenlabs/text-to-speech-turbo-2-5",
            "单音色 TTS（Turbo 2.5 + 中文）",
            {
                "model": "elevenlabs/text-to-speech-turbo-2-5",
                "input": "大家好，我是萧索。",
                "voice": "Roger",
                "response_format": "mp3",
                "metadata": elevenlabs_metadata(
                    "大家好，我是萧索。", "Roger", language_code="zh"
                ),
            },
        ),
        (
            "dialogue-v3",
            "elevenlabs/text-to-dialogue-v3",
            "多角色对白（metadata.dialogue）",
            {
                "model": "elevenlabs/text-to-dialogue-v3",
                "input": "Hello Roger",
                "response_format": "mp3",
                "metadata": {
                    "dialogue": [
                        {"text": "Hello, I am Xiao Suo.", "voice": "Roger"},
                        {"text": "Nice to meet you.", "voice": "Sarah"},
                    ]
                },
            },
        ),
        (
            "audio-isolation",
            "elevenlabs/audio-isolation",
            "人声分离（metadata.audio_url）",
            {
                "model": "elevenlabs/audio-isolation",
                "input": SAMPLE_AUDIO_URL,
                "response_format": "mp3",
                "metadata": {"audio_url": SAMPLE_AUDIO_URL},
            },
        ),
    ]

    results = []
    print(f"TokenFree: {base}\nOutput: {OUT_DIR}\n")

    for name, model_id, desc, payload in tests:
        print(f"=== {name} ({desc}) ===")
        status, ctype, body, elapsed, err = call_speech(base, key, payload)
        row = {
            "name": name,
            "model": model_id,
            "description": desc,
            "status": status,
            "elapsed_ms": elapsed,
            "ok": False,
            "bytes": 0,
            "file": None,
            "error": None,
        }
        if body:
            out = OUT_DIR / f"{name}.mp3"
            out.write_bytes(body)
            row["ok"] = True
            row["bytes"] = len(body)
            row["file"] = str(out)
            print(f"OK status={status} len={len(body)} ms={elapsed} ct={ctype}")
            print(f"  -> {out}")
        else:
            short = (err or "unknown error")[:400]
            row["error"] = short
            print(f"FAIL status={status} ms={elapsed}")
            print(f"  err={short}")
        results.append(row)
        print()

    ok_count = sum(1 for r in results if r["ok"])
    summary = {
        "tested_at": time.strftime("%Y-%m-%dT%H:%M:%S"),
        "base_url": base,
        "total": len(results),
        "passed": ok_count,
        "failed": len(results) - ok_count,
        "results": results,
    }
    REPORT.write_text(json.dumps(summary, ensure_ascii=False, indent=2), encoding="utf-8")

    print("=== summary ===")
    for r in results:
        mark = "OK" if r["ok"] else "FAIL"
        print(f"{r['name']}: {mark} ({r['elapsed_ms']}ms)")
    print(f"\nReport: {REPORT}")

    if ok_count == 0:
        print(
            "\n全部失败时常见原因：\n"
            "  1) TokenFree/Kie 上游 ElevenLabs 通道 internal error（约 100s 超时）\n"
            "  2) 账户未充值或未正确开通 kie-speech 分发\n"
            "  3) voice 需用 Rachel/Roger/Bill 等预设名，不能用 voice_id\n"
            "建议：在 TokenFree 控制台 Playground 试同一模型，或联系客服并提供 request id。"
        )
        sys.exit(1)


if __name__ == "__main__":
    main()
