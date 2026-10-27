# Compact web experiment

`web-runtime.json` selects INT8 locally; Android assets are unchanged.
The encoder is extracted, without requantization, from the tested INT8 ONNX
graph. FP32 frame/slot heads remain trainable (as in the existing feedback
trainer). This is a mixed-precision trainable variant, not bit-identical to
the original all-heads-quantized export. No FP32 encoder is used for inference
or feedback features. Vosk is unchanged.

At first activation the existing feedback checkpoint, including contact rules
and all trained heads, is copied to `artifacts/compact-web/feedback-head.safetensors`.
Subsequent feedback updates only that checkpoint. Restarting preserves it.
The original checkpoint and Android snapshot are not modified. The existing
evaluation report describes the original model, not this experimental variant.

Dependencies include `requirements-android-export.txt`. Launch the existing
uvicorn server on port 18766. Set precision to `fp32` to return to the original
experiment without discarding either checkpoint.
