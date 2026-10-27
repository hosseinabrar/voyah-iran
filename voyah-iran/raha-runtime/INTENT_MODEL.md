# Raha intent inference

The Android client uses one trained ParsBERT frame/slot model. There is no
legacy rule-parser fallback. Existing Vosk ASR, endpoint detection, subscription
gating, assistant preferences and vehicle command executors remain in place.

The immutable snapshot includes 850 saved feedback records' trained heads,
the expanded slot schema and learned contact-span patterns. Training records,
Python, the web server and FP32/INT8 experimental alternatives are not packaged.
Provenance is in `src/main/assets/raha-intent/manifest.json`.

Only explicit numeric quantities override slot values after prediction. Seat,
window, display and other directions are predicted by the trained model; no
right/passenger alias correction is applied. Dynamic phonebook and saved-place
names remain entities, not alternative intent recognizers. Call confirmation
continues to use the existing pending-call workflow.

Initialization, warmup and inference run off the UI thread. Inference results
are discarded after cancellation, service shutdown, preference disablement or
VIP expiry. Native resources are serialized and closed after outstanding work.
No model load/inference failure falls back to the removed recognizer.

## Reproducible export

In `experiments/raha-parsbert-web`, use its Python environment:

1. `export_android.py` snapshots the active feedback heads and exports ONNX.
2. `verify_android_export.py` compares the graph with the captured trained model.
3. `install_android_assets.py` refuses an unverified snapshot and copies assets.
4. `export_guard_fixtures.py` emits cross-language numeric fixtures.

The checked-in tokenizer fixtures correspond to `artifacts/android/parity-fixtures.json`.
Keep these in sync on subsequent exports. Do not export while updating the base
checkpoint. Feedback updates use atomic replacement; export retains its own
snapshot and never alters the live web-training checkpoint.

## Validation and remaining device checks

- FP32 graph: 1,835 examples, including all 979 held-out final cases, identical top-three action/parameter selections.
- Kotlin tokenizer: exact input IDs on those 1,835 examples.
- Kotlin numeric grounding/guards: 2,363 cases against Python, without direction correction.
- Core tests, Raha app unit tests and both Android projects' Kotlin compilation pass.
- INT8 was evaluated but rejected because it changed ranked predictions.
- FP32 model is about 451 MiB. Android native peak memory, latency, VAD/ASR
  coexistence and cold startup still require testing on the actual device.
- No APK was assembled or installed as part of this migration.

Java JNI 1.19.2 shares Sherpa's existing newer ORT native runtime; the older
duplicate native library is deliberately absent. See `libs/onnxruntime-java-NOTICE.txt`
and the reproducible repacking script. Do not use Gradle `pickFirst` to conceal
duplicate runtime libraries.

Historical parser tests were retained as non-executable reference text under
`experiments/raha-parsbert-web/regression-contracts`, outside Android sources.
The old parser implementations themselves were removed; Git history retains them.
