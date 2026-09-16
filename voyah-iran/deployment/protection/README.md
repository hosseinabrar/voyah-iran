# Experimental Raha DPT protection

This is a separate developer step. It does not run during installer Publish and
does not overwrite `app/release/app-release.apk`. Never distribute an unsigned
APK, a candidate APK, `input.apk`, mapping files, logs, or the entire run folder.

## Prerequisites

- A fresh signed Release APK built **after** the new Raha boundary keep rules.
- The mapping.txt from that exact build (checked against the R8 ID inside DEX).
- Python 3.10+ (standard library only), and a full JDK 17+.
- Android SDK Build Tools for the final signing step.
- Internet for the first pinned DPT download; subsequent runs use the verified cache.

The Windows wrapper prefers Android Studio's bundled JDK. If necessary, pass
`-JavaHome` and `-PythonExe`. A restricted Windows JDK 21 environment hit a javac
ZIP filesystem AccessDenied error; Android Studio's JBR 25 completed the local
tool patch and protection experiment. A fresh local output folder is used for
each attempt. Errors stop the pipeline.

## Steps (from the Android project directory)

1. Build signed Release in Android Studio. Preserve its mapping file.
2. Run:

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\deployment\protection\Protect-Raha.ps1
```

The script prints a new folder under `build/raha-protected`. Check `report.json`.
It refuses mismatched mappings and known R8 inlining/merging outside the selected
Raha classes. An old APK must be rebuilt rather than bypassing this check.

3. Sign using the SAME keystore/alias used for the original APK:

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\deployment\protection\Sign-Raha.ps1 -RunDirectory "RUN_FOLDER" -Keystore "KEYSTORE_FILE" -Alias "KEY_ALIAS"
```

Passwords are prompted without echo and passed to Android apksigner through
temporary process environment variables. They are not passed to DPT, saved in
its JSON configuration, or written to the protection log. Add
`-SeparateKeyPassword` if the key password differs from the store password.
The script verifies the final APK certificate matches the input certificate.
Only `raha-protected.apk` is the signed test artifact.

4. Install the signed test APK as an update on the test vehicle and restart the
device so the root bridge loads the same APK version. Test cold boot, subscription,
Raha model/wake/button/cancel/commands, star Back, clock, and comfort features.
Do not select the installer's main installation action with its old bundle after
installing the protected test APK: that action still carries the ordinary APK.

## Protection scope and compatibility patch

Pinned upstream: dpt-shell v2.21.0, MIT, https://github.com/luoyesiqiu/dpt-shell.
Archive and source SHA-256 values are pinned in `protect_raha.py`. DPT runs locally;
APK contents are never uploaded. Downloads fetch only public tool artifacts.

Only the recognizer, phase recognizers, and vocabulary classes are selected,
using their renamed R8 descriptors. Everything else is kept in ordinary DEX
using DPT `-K` and an explicit exclusion rule. Model assets and OEM protocol
constants remain readable. This does not guarantee resistance to runtime extraction.

Upstream ReflectionClinitInjector modifies excluded classes too. Our one-condition
source patch makes `isSkippedClass` honor ProtectRules before injecting native
initialization. Without it, BridgeServer's app_process startup can depend on a
protector that has not been initialized. Only that Java tool class (including
its nested classes) is recompiled; upstream native shell binaries are unchanged.
The MIT notice is also included in the protected APK.

After protection, the script verifies that:

- Selected classes disappeared from the ordinary top-level DEX class tables.
- Every other original class retains its method names, flags and code lengths.
- Root BridgeServer has its callable static main method.
- Original assets/native libraries have identical bytes.
- Original APK has not changed.

These are static checks, not on-device execution tests. DPT runtime signature
checking is not enabled; final APK signing is performed separately with Android
apksigner. The full owner keystore is never given to DPT.

## Validation history

2026-09-16: An initial 19-class protection trial passed static structure checks.
Its matching R8 mapping then revealed code moved into seven shared class sections.
That trial is **not** a release candidate. Boundary keep rules and fail-closed
inlining checks were added. Sign-Raha refuses reports from the older trial.
Six selection tests cover stale mappings, shared-class merging, inlining leaks,
field references and missing root entry points. No vehicle runtime test has run.

After the boundary rules: R8 completed successfully with a local JBR 25 javac
workaround (no release APK was packaged). The new DEX/mapping passed ownership
checks for 24 classes. A disposable unsigned structural probe using that DEX
also passed DPT and preservation checks. It is not a signed installation artifact.
Build a new signed Release in Android Studio before running the real pipeline;
the old app/release APK no longer matches the latest generated mapping file.

Run tests:

```powershell
python .\deployment\protection\test_protection.py
```
