# Old Raha experiment

Branch: `old-raha`. Grammar source: `version-2.1.1` at
`76a702209747d8eee2e4ababcdd746d5de6a1bf5` (not the intermediate pre-BERT migration).

Run `bash experiments/raha-old-web/run.sh` from the repository root, then open
http://127.0.0.1:18770/. Loopback HTTP supports the microphone without a self-signed certificate.
The current BERT web project on 18766 is retained. This project reuses its Python environment
and Vosk files, but never imports torch/transformers or loads an intent model.

Android RahaService and this web server call the same Kotlin OldRahaRecognizer. The web
worker runs on JVM; Android uses the compiled command-core. Execution handlers remain current,
including commands added after 2.1.1. Vosk remains the current speech recognizer/vocabulary.
BERT runtime sources are preserved under the original web project's android-reference directory;
its model assets and native Java runtime are excluded from this branch's Android package.
The small Silero ONNX VAD is still required for speech detection; it is not BERT.

Recognition order:
1. Exact normalized saved example (including UNKNOWN negatives), latest correction wins.
2. New explicit command rules, contact extraction, then the 2.1.1 grammar.
3. Unknown when no complete grammar matches. There is no nearest-neighbor or neural fallback.

The initial checkpoint contained 36,563 annotation rows: 36,531 unique usable texts after
normalization and latest-wins deduplication. The one legacy row missing parameters was
subsequently recovered after checking that SWAP_DISPLAY_APPS has an empty parameter schema,
bringing the total to 36,532. The original omission is recorded in migration-report.json. One obsolete negative for «گرمکن رو روشن کن»
was corrected to SET_SEAT_HEATING(enabled=true,seat=driver), consistent with the owner's
explicit examples. Original BERT data was not modified by this correction.

A web correction updates lessons.json and the shared Android raha-old-lessons.tsv immediately.
An Android rebuild/install is still required. This teaches that exact sentence, not statistical
generalization. A rule match is not a probability; the UI shows its source instead of claiming
model confidence. Verification checks saved-example replay, not unseen accuracy. Numeric and
side parameters are carried with each example; full unseen grammatical coverage is not claimed.

Use the verification button after a session. Results are saved to verification-report.json.
The initial exporter refuses to replace existing lessons unless --replace-lessons is explicit.
No APK release build, commit, server deployment, or vehicle command is performed by this project.
