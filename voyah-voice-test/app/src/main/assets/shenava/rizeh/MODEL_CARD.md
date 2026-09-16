---
language:
- fa
license: cc-by-nc-4.0
library_name: sherpa-onnx
pipeline_tag: automatic-speech-recognition
base_model:
- Reza2kn/Shenava-Rizeh-v1.0
base_model_relation: finetune
tags:
- automatic-speech-recognition
- speech
- persian
- farsi
- fastconformer
- ctc
- streaming
- on-device
- shenava
- shenava-1
- onnx
- sherpa-onnx
- int8
- quantized
metrics:
- wer
- cer
datasets:
- Reza2kn/visualears-persian-asr-16k
- Reza2kn/visualears-golden-6669
- Reza2kn/fleurs-fa-benchmark
---

# Shenava — Rizeh v1.0 (32M) · CTC Streaming · ONNX (INT8)

**Rizeh** (ریزه, "small grain") is the **32M mid‑tier student** of the **Shenava‑1** family — distilled from the 114M Koochik teacher. This repository contains the **CTC streaming INT8 quantized ONNX export** for use with [sherpa-onnx](https://github.com/k2-fsa/sherpa-onnx).

## Model Details

| Property | Value |
|---|---|
| **Parent Model** | [`Reza2kn/Shenava-Rizeh-v1.0`](https://huggingface.co/Reza2kn/Shenava-Rizeh-v1.0) |
| **Architecture** | FastConformer Hybrid RNNT/CTC → CTC decoder export |
| **Language** | Persian (Farsi) |
| **Parameters** | 32M |
| **Vocabulary Size** | 1025 tokens (BPE + blank) |
| **Tokenizer** | ve_tok_v4 — SentencePiece BPE‑1024 |
| **Sample Rate** | 16 kHz |
| **Subsampling** | 8× |
| **Streaming** | ✅ Yes (with cache support) |
| **Weight Type** | INT8 (dynamic quantization) |
| **Author** | [Reza2kn](https://github.com/Reza2kn) |
| **Version** | 1.0 |

## Files

```
.
├── README.md
├── model.int8.onnx   # ONNX model (INT8 quantized)
└── tokens.txt        # Token vocabulary (1025 tokens)
```

## Usage

```bash
sherpa-onnx-offline \
  --tokens=tokens.txt \
  --encoder=model.int8.onnx \
  --num-threads=4 \
  /path/to/test.wav
```

## Conversion

Exported from a NeMo checkpoint and quantized dynamically with ONNX Runtime.

## License

CC-BY-NC 4.0 (inherited from the parent model)