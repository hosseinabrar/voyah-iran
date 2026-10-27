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

## Automatic project updates

`auto_sync_project: true` enables a background worker on web-server startup and
after every saved feedback update. It snapshots the latest checkpoint, exports
the compact graph, verifies all feedback plus the held-out corpus against the
web model, and only then replaces project assets. New feedback arriving during
verification supersedes that pending export. Failed exports keep the previous
model and retry; restart also catches up any saved training not yet transferred.

The web page shows progress via `/api/project-sync`. Logs and the last parity
report are under `artifacts/project-sync/`. Every run has a separate temporary
workspace. Model files are replaced atomically (no partially written ONNX).
Wait for the synced message before starting an Android build: multiple asset
files and the manifest are updated sequentially, not in one filesystem transaction.
This never builds, installs, commits or pushes. An already installed car app
does not change until a newly built APK is installed. Only saved feedback is
transferred; simply saying a command without submitting feedback is not training.
