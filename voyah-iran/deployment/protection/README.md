# Application APK protection

Installer Publish builds Release, runs DPT protection, signs with the existing
application key, and packages the verified protected APK. Historical script and
artifact names (`Protect-Raha.ps1`, `raha-protected.apk`) remain for compatibility;
protection is no longer limited to Raha.

## Scope

All surviving `com.abrar.*` classes in the exact R8 mapping are candidates.
Android entry points keep the names required by the manifest, installer and
shell commands; keeping a name does not exclude the implementation from DPT.
JNI/JNA bindings retain required native names.

Root `app_process` entry points cannot use the Android Application DPT loader.
`DexDependencies.java` scans the actual release DEX (code references, signatures,
annotations, exception types, method handles, call sites and literal reflection).
The complete transitive dependency closure of the bridge, root main classes and
native bindings stays in ordinary DEX. It is still R8-obfuscated where allowed.
Third-party classes and mixed ownership classes are not selected. Every excluded
first-party class and its dependency reason is recorded in `report.json`.
Computed reflective names cannot be inferred by static analysis: new root code
using dynamic reflection must receive a matching keep rule and dependency review.

R8 shrinking and renaming remain enabled. Optimization is disabled to prevent
shared generated lambda holders from merging Android and root implementations.
Package keep rules alone did not prevent this on the current R8 version. This
trades some R8 optimization for reliable protection boundaries; the dependency
scan still verifies each APK rather than relying on the rules alone.

Renamed classes are repackaged under `com.abrar.voyahiran.obfuscated` rather
than the default package. This prevents ordinary reflective-method literals
such as the OEM value `on` from accidentally matching a renamed UI class `on`.
The conservative reflection scan is retained, not weakened to ignore dependencies.
Both Windows and macOS use this same R8 configuration and protection script.
Before DPT runs, `selection-report.json` records the selected classes and exclusion
paths. Publishing fails if fewer than half of the first-party DEX classes are
selected, or key Android-only entry/entitlement/voice classes enter root exclusions.
This is a regression guard, not a security guarantee or permission to weaken the
root dependency checks; intentional scope changes require reviewing the policy.

## Build and tools

Use the installer Publish flow normally. For a standalone run from the Android
project directory, first build a fresh signed Release and preserve its matching
mapping, then run:

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\deployment\protection\Protect-Raha.ps1
powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\deployment\protection\Sign-Raha.ps1 -RunDirectory "RUN_FOLDER" -Keystore "KEYSTORE_FILE" -Alias "KEY_ALIAS"
```

Python 3.10+, a full JDK 17+ and Android SDK Build Tools are required. The wrapper
prefers Android Studio's JDK. Pinned DPT v2.21.0 public artifacts are downloaded
once and SHA-256 verified; APK contents are processed locally. Signing passwords
are passed via temporary process environment variables, never to DPT or its log.
The output certificate must match the original APK certificate.

Each run has a separate folder under `build/raha-protected`. Distribute only the
signed `raha-protected.apk` or the installer containing it. Do not distribute
mapping files, input APKs, logs, unsigned APKs or entire run folders.

## Verification and limitations

The existing DPT ReflectionClinitInjector patch makes excluded classes skip its
native initializer, preserving root startup without the Android loader. Native
shell binaries are unchanged. The MIT notice is included in the output APK.

Protection fails closed on mismatched mapping IDs, incomplete dependency graphs,
missing root entries or altered unprotected class structures. Checks verify:

- Selected classes are removed from ordinary top-level DEX.
- Other classes retain method names, flags and code lengths.
- Root BridgeServer retains its callable static main.
- Original assets and native libraries are byte-identical.
- The original APK is unchanged.

Signing and installer packaging require `application-v2` reports with successful
dependency checks; old Raha-only runs cannot silently enter a new Publish.
Raha's model, manifest, schema, vocabulary and learned contact rules are sealed
before packaging with AES-256-GCM in authenticated 64 KiB chunks. Each regenerated
asset set gets a new random key and per-chunk nonce. The key is split in generated
Java code and that class, the reader, inference and execution-confidence classes
must all be inside the DPT selection. Splitting is only obfuscation, not a secret
independent of the app. No plaintext fallback is allowed. The model is decrypted
in RAM for ONNX session creation and the Java buffer is wiped in finally; legacy
plaintext model cache files are removed when Raha initializes. This incurs about
115 MiB additional temporary Java heap, plus native initialization allocations.
Vehicle memory/cold-start testing is mandatory; OOM fails closed, not to plaintext.

Publish/sign/package on Windows and macOS reject old reports without the sealed
asset and sensitive-class checks. Other assets, resources, protocol constants and
root implementations remain outside this sealing step. Debug/unprotected test
builds contain recoverable key code, so sealing alone is not DPT protection.
Offline keys and live model weights can still be recovered by a sufficiently
privileged runtime observer; no anti-extraction guarantee is made. Root rejection
is deliberately not enabled because the vehicle application relies on root.

Generated keys/tool classes live only under ignored `raha-runtime/build/` and
must not be distributed. The original assets remain private source inputs,
not APK assets; automatic web training still updates those inputs and the next
Gradle build reseals them. No signing key or password is used for encryption.

Run selection regression tests:

```powershell
python .\deployment\protection\test_protection.py
```

Before distributing a protected build, test on the vehicle: cold boot, root
bridge, subscription, Raha, launcher, navigation, music, accessibility and seat
memory. Static APK validation does not replace runtime testing on Android 9 and
newer vehicle firmware.
