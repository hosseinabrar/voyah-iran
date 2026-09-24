# Offline pronunciation fallback

ClusterLatinText keeps its existing phrase/word dictionaries and compound rules first.
Explicit vowel marks retain the existing literal conversion. Previously unknown plain
Persian words now use PersianG2p, a pure Kotlin float32 GRU implementation. If the model
is missing, invalid, decoding incomplete or the input unsupported, the old letter
conversion remains available. No lookup dictionary entries were added for test samples.

The model loads once on first unknown word. A synchronized access-order cache holds at
most 512 results (including failed predictions). Input is limited to 2–32 supported
characters and decoding to 20 tokens. Inference runs on the existing Neshan bridge worker,
not the activity UI thread. There is no network request or Python/native ML dependency.

The 634,308-byte resource `voi-g2p/weights.bin` is included via app/src/main/resources;
the root bridge's APK class loader reads the same resource without an Android Context.
The protection step verifies preservation of voi-g2p resources alongside existing assets.
Rebuild/publish the complete installer so bridge.apk also receives the new code and model.
No remote Neshan configuration change is needed.

## Provenance and representation

Source: https://github.com/PasaOpasen/PersianG2P
Commit: 3fcb5fdfa4330090742c3e324c9bf3cfa1bb571d
Original model: PersianG2p/data/checkpoint.npy. Repository MIT notice is bundled beside
the model, together with source/attribution. The separately sourced Tihu dictionary is
NOT distributed. Model is unencrypted, as are the existing speech models.

Export format: ASCII VIG2P001, followed by each array's big-endian int32 element count
and flattened row-major big-endian float32 values. Order:
encoder.emb.weight, decoder.emb.weight, decoder.fc.weight, decoder.fc.bias,
encoder.rnn.weight_ih_l0, encoder.rnn.weight_hh_l0,
encoder.rnn.bias_ih_l0, encoder.rnn.bias_hh_l0,
decoder.rnn.weight_ih_l0, decoder.rnn.weight_hh_l0,
decoder.rnn.bias_ih_l0, decoder.rnn.bias_hh_l0.
No pickle is parsed in Android. Resource SHA256:
264382e8b01a29ff5273c0d55b15fd83e458d3dff77a3eedc9afcfc74db97927

## Verification and limits

PersianG2pTest compares predictions against the upstream NumPy model on the user's
sample words, preserves known phrases and checks unsupported/bounded inputs. Existing
ClusterLatinTextTest and PersianRoadWordsTest pass unchanged: 13 test methods total.
Targeted JVM compilation and tests passed; release packaging and hardware execution
have not been tested in this change. Python evaluation timings are not Android timings.

Examples of newly readable output: زنبق zonbagh, حسابی hesabi, بستنی bastani,
حق طلب hagh talab. Existing همت hemmat, مدیریت modiriat and محمدی پور mohammadipour
retain their prior curated pronunciation despite model-only errors. This is approximate
G2P, not a guarantee of correct pronunciation for all names or homographs.
