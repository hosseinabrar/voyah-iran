# Raha / ParsBERT isolated browser pilot

## Production model storage (October 2026)

The Android source now keeps an immutable `model.base.onnx` plus `model.delta`
under `raha-runtime/src/main/assets/raha-intent`. Both use LFS. The base is a
one-time copy of an already verified full model, not a file regenerated on every
training update. The delta contains only changed 64 KiB blocks, source/target
SHA-256 hashes and lengths. It contains no training records.

After export/parity checks, `install_android_assets.py` updates only the delta
and metadata. During Gradle asset sealing, the JDK-only `RahaModelDelta` tool
reconstructs the exact full graph, verifies its hash against the verified
manifest, and encrypts it. Neither the base nor delta is packaged. Runtime
inference and its single encrypted ONNX graph are unchanged. Builds require
LFS objects but no Python, ONNX tooling or experiment checkpoint.

Do not rotate the base on ordinary training; a different encoder/graph layout
can make deltas large and should be treated as an explicit base migration.
Old historical LFS objects are not removed by this change.

## Expanded experiment (version 2)

The active server prefers `artifacts/full-intent` when trained. Version 1 is kept
intact in `artifacts/intent` for comparison; there is no production integration.

- **108/108** current `RahaAction` families, checked against the source enum.
- Plus contact intent, call confirmation, and dialog cancellation; one UNKNOWN
  class. Contact lookup/dialing is not connected. Sample names are fictional.
- **15,525** training examples: source vocabulary, literal regression examples,
  expanded numeric/duration cases and bounded colloquial augmentation. Each row
  retains source/family provenance. Interpolated test cases require explicit
  expansion, not blind string scraping.
- The existing Kotlin parser is an **offline annotation teacher only**. Browser
  inference does not call it or fall back to it. Rejected teacher phrases are
  not automatically labeled UNKNOWN. Contradictory positive negation examples
  are quarantined and retained in the provenance report.
- ParsBERT final four blocks are fine-tuned, with a semantic-frame classifier
  (intent + applicable parameter keys) and typed parameter classifiers. Early
  blocks are frozen and cached during training only. Runtime uses the complete
  encoder. Contact-name extraction is separate, explicitly rule-based.
- Numeric heads cover observed values, not arbitrary mathematical reasoning.
  Guardrails reject obvious number/polarity inconsistencies; this is NOT a
  production safety guarantee. Multiple commands, arbitrary saved destinations,
  real phonebooks and multi-turn dialog state are not implemented in this UI.
- `dataset.json` is the auditable training/probe artifact. All checkpoint/model
  files remain ignored. `full-report.json` includes incorrect predictions and
  separates intent accuracy from exact intent+parameter accuracy. Its synthetic
  probes are **not a representative estimate of real-user accuracy**.

To reproduce v2, first build the repository's `:command-core:jar`, then here:

```sh
.venv/bin/python prepare_full.py
.venv/bin/python supplement.py
.venv/bin/python build_dataset.py
.venv/bin/python train_full.py
.venv/bin/python evaluate_full.py
.venv/bin/python -m unittest -v test_full.py test_experiment.py
.venv/bin/python -m uvicorn server:app --host 127.0.0.1 --port 18766
```

The exporter uses this workstation's Java/Kotlin build cache and `JAVA_HOME`;
adapt cache discovery on another machine. To retrain the checked-in dataset,
only `train_full.py` is needed. `RAHA_EPOCHS` overrides the default 18 epochs.
The server must be restarted after checkpoint replacement. The searchable
browser catalog shows one example per family, and predictions always display
parameters separately from the generic family title.

Not connected to Android, CAN, production Raha, subscription logic or a vehicle.
The browser captures microphone audio only after an explicit click and permission.
PCM audio goes to the loopback server, then local Vosk. No cloud speech API, audio
storage, telemetry or automatic feedback upload. Session feedback lives in browser
memory until the user explicitly downloads JSON.

## Start

From this directory:

```sh
python3 -m venv .venv
.venv/bin/pip install -r requirements.txt
.venv/bin/python train.py
.venv/bin/python -m uvicorn server:app --host 127.0.0.1 --port 18766
```

Open http://localhost:18766 in Chrome; allow the microphone. Press start, speak,
then finish. You can edit the transcript and rerun understanding independently.
Maximum recording 30 seconds. The server must stay bound to loopback.
Training requires internet to download the official pretrained model once;
inference and speech recognition are offline. Training/ASR assets and venv are
ignored by Git. Model weights are not included in any Android build.

## Original version 1 baseline (retained, no longer the expanded model)

- Official `HooshvareLab/bert-fa-zwnj-base`, pinned revision in `train.py`.
- Last two encoder layers, pooler and classifier fine-tuned, not fake keyword
  recognition or nearest-example lookup. 27 intent classes plus unknown.
- 1,572 synthetic labeled utterances. 12 epochs, seed 42. Normalization replaces
  ZWNJ with spaces consistently for training and inference.
- 32 separate development phrases; not random splits of duplicated templates.
  Their small synthetic nature does NOT establish real-world accuracy. Feedback
  from new human phrases is the next evaluation stage. Do not train on these
  evaluation sentences to inflate the score.
- Target extraction is explicitly a small rule-based component, not learned
  slot filling. Driver/passenger only; default driver. Window openness only
  0/50/100. Arbitrary percentages, composite commands and full Raha coverage
  are not supported. Classifier confidence is not calibrated; thresholds .65
  and margin .20 are provisional. Rejection is not a production safety system.
- ASR uses the repository's `vosk-model-small-fa-0.42` with the vehicle grammar
  exported from `RahaSpeechVocabulary.phrases()` into `speech-grammar.json`.
  Regenerate it with `.venv/bin/python export_speech_grammar.py` after rebuilding
  the production core when vocabulary changes. The server never silently falls
  back to unrestricted recognition. `[unk]` is retained as in production.
  Recording is button-activated, not wake-word gated; personal contacts and saved
  navigation aliases from the car are not imported into this standalone test.
  `speech_extras.py` adds reviewed colloquial terms, fictional test contacts,
  conditional/past-tense cues and limited out-of-domain context (e.g. home TV).
  Every added word is validated against Vosk's lexicon at startup. This is not
  a vocabulary import of all training text, nor acoustic-model retraining.
  Unknown joined forms use existing components where possible (بخار گیر,
  بخار زدای، نور بالای); اوتینگ and امبینت are not newly enabled by this change.
- No changes to the pretrained language model alone can solve execution errors.

## Verify

### Full supervised practice (v3)

`practice_data.py` replaces mechanically harvested/ambiguous examples with authored
semantic specifications covering all 111 supported action families. It generates
explicit opposing examples for on/off, increase/decrease, zones, positions, numerical
values and durations. Train/dev/final surface families are disjoint. Final examples
also exclude the original checkpoint's training texts.

`practice_train.py` fine-tunes the last four encoder blocks plus frame AND slot heads,
using balanced replay and numerical coverage. Development errors are mined after
round three; later dev scores are practice scores, NOT independent accuracy.
`practice_refine.py` trains additional colloquial corrections, with replay, and
`practice_audit.py` evaluates a separate untouched hand-written audit. No TTS/audio
is generated: these runs evaluate text understanding, not speech recognition.
`practice_metrics.py` reports exact per-parameter accuracy including wrong-action
failures. All benchmarks remain synthetic, not representative road-test accuracy.

Original `artifacts/full-intent` remains intact. A reviewed model is selected explicitly
with `active-model.json` (path relative to this experiment, inside artifacts only).
Its interactive feedback head has a separate checkpoint so older feedback weights
cannot silently overwrite the newly trained model. Old feedback files are preserved.

Current selected checkpoint: `artifacts/practice/final`, after 6 general + 4 colloquial
+ 3 contrastive epochs. The first 40-case colloquial audit was subsequently promoted
to training corrections, so its later scores are NOT held-out results. The final fresh
36-case audit (`practice_final_audit.py`) improved from 19/36 to 35/36 exact predictions
(including all parameters); action accuracy improved from 31/36 to 35/36. It remains
a small synthetic benchmark. A home-TV mute request still mapped incorrectly to car
audio mute. The 979-case structural benchmark is easier and is reported separately.
No audio model/grammar or production Android code was changed during this training.

### Interactive feedback learning

Both feedback buttons now POST to `/api/feedback`. This performs genuine supervised
updates of semantic-frame and typed parameter heads on frozen ParsBERT features,
with replay of two original examples per frame and up to 256 recent corrections.
The correction editor lets users confirm or change the action and its parameters.
Legacy action-only feedback still marginalizes frames without inventing parameter labels.
The encoder and Vosk are unchanged. Contact names use extraction rules plus reusable
sentence-shell patterns learned from corrected names, not a trained BERT NER head.
Names must occur in the transcript; fix speech transcription before submitting feedback.
Latest feedback replaces earlier feedback for the same normalized text.
Updated heads, parameter corrections, and contact patterns are stored atomically in
the active model's feedback checkpoint (currently `artifacts/feedback-head-practice-v3.safetensors`)
and loaded after restart. Legacy action-only checkpoints remain readable. The original checkpoint
is untouched. This local adaptation can still regress or fail to generalize; the UI
reports whether the corrected example's action and supplied parameters were learned, separately from acceptance
guards/parameter confidence. Original evaluation scores are pre-feedback baseline scores.
Test learning and restart persistence with `python -m unittest test_feedback_training.py`.

```sh
.venv/bin/pip install httpx
.venv/bin/python -m unittest -v test_experiment.py
```

`artifacts/intent/report.json` records all probe predictions, not just successes.
The included Vosk fixture tests the streaming audio path, not laptop microphone
quality. Actual microphone permission and real spoken Persian need user testing.

Source/license: https://github.com/hooshvare/parsbert (Apache 2.0).
Model: https://huggingface.co/HooshvareLab/bert-fa-zwnj-base
