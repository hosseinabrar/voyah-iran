# Raha intent inference

The Android client uses one trained ParsBERT frame/slot model. There is no
legacy rule-parser fallback. Existing Vosk ASR, endpoint detection, subscription
gating, assistant preferences and vehicle command executors remain in place.

The immutable snapshot includes 1,503 saved feedback records' trained heads,
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

## Sealed packaging

Gradle packages the five intent assets only as authenticated `.sealed` files.
The generated sealing key and the complete `com.abrar.raha.intent` code must
remain in the application's DPT-protected selection; publishing fails otherwise.
The model is opened in RAM, authenticated, SHA-256 checked, passed to ONNX, then
the Java input buffer is cleared. No plaintext ONNX cache is written. Previous
content-addressed plaintext caches are removed at preparation. Budget additional
temporary Java heap of about 115 MiB and test initialization on the vehicle.
Debug builds are not a protected distribution; the offline key can ultimately
be recovered from code/runtime. See deployment/protection/README.md.

Vehicle execution requires the action score to display as 100/100 using the
web's four-decimal score then whole-percent rounding. Existing parameter
confidence and consistency checks still apply; displayed 100 is necessary,
not sufficient. Web training acceptance thresholds are unchanged. Automatic
model updates replace assets, not this execution policy.

## Reproducible export

In `experiments/raha-parsbert-web`, use its Python environment:

1. `export_compact_android.py` snapshots the compact web feedback heads and combines
   the exact frozen INT8 encoder with the trained FP32 heads into one ONNX graph.
2. `verify_android_export.py model.compact.onnx` compares with the captured compact web model.
3. `install_android_assets.py` refuses an unverified snapshot and copies assets.
4. `export_guard_fixtures.py` emits cross-language numeric fixtures.

The checked-in tokenizer fixtures correspond to `artifacts/android/parity-fixtures.json`.
Keep these in sync on subsequent exports. Do not export while updating the base
checkpoint. Feedback updates use atomic replacement; export retains its own
snapshot and never alters the live web-training checkpoint.

## Validation and remaining device checks

- Compact graph: 2,488 examples, including all 979 held-out final cases, identical top-three action/parameter selections to the trained compact web model.
- Tokenizer fixtures cover those 2,488 examples.
- Numeric grounding/guard fixtures cover 3,035 cases against Python, without direction correction.
- The deployed graph preserves the compact web encoder unchanged and uses its latest
  trained full-precision heads. It does not requantize the updated heads.
- The single model is 120,874,241 bytes (about 115 MiB), replacing the 451 MiB
  FP32 model at the same asset path; no alternative intent model is packaged.
- Android native peak memory, latency, VAD/ASR
  coexistence and cold startup still require testing on the actual device.
- No APK was assembled or installed as part of this migration.

Java JNI 1.19.2 uses its matching native runtime, isolated as `librahaortcore.so`.
Sherpa's existing runtime remains untouched. Sharing it failed on the vehicle
with an unresolved `OrtGetApiBase`; a newer version string is not ABI proof.
See `libs/onnxruntime-java-NOTICE.txt`
and the reproducible repacking script. Do not use Gradle `pickFirst` to conceal
duplicate runtime libraries.

Historical parser tests were retained as non-executable reference text under
`experiments/raha-parsbert-web/regression-contracts`, outside Android sources.
The old parser implementations themselves were removed; Git history retains them.
