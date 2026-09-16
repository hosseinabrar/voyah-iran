# Release protection audit

## Reference inspected (2026-09-16)

Local input: `C:/Users/Nahid/Desktop/VT_2.8.2.apk`.
SHA-256: `0f60737ca67f83d8740c00e086fc197fbfca6074e2dd81daf69ff05a3b4ca824`.
The DEX class table contains four classes. The application wrapper is
`v7ff5548e.l7ff5548e`, with a Virbox string and native methods. The APK
contains `assets/l7ff5548e_a64.so` and `assets/l7ff5548e_x64.so`.
This supports identification of a Virbox protection wrapper, not a claim
that its original code cannot be recovered. Its precise protection options
and license/version have not been established. Vosk model assets remain readable.

Vendor reference: https://documentation.virbox.com/use-cases/protect-mobile-applications/protect-android-apk-projects

## Changes in our project

Release now enables R8 optimization/renaming/code shrinking and resource shrinking.
Debug remains suitable for development. The root BridgeServer.main entry point
must remain reachable by its original name: both app_process and installer
validation address it outside the normal application lifecycle.
JNI/JNA vendor bindings remain intact; our Raha recognizers have no package-wide
keep rule. OEM classes loaded from the car firmware are outside our APK.
Diagnostic logs are intentionally retained for vehicle testing requested by the
owner. String constants, speech models and protocol names are not encrypted by R8.

Archive `app/build/outputs/mapping/release/` privately for EACH distributed build,
along with its APK hash/version. Do not ship mappings in the installer or upload
mapping/seeds/configuration reports with the APK. Use the matching mapping with
R8 Retrace for tester stack traces. Subsequent builds overwrite this directory.

## Remaining gap to the reference

R8 is NOT equivalent to Virbox DEX encryption/virtualization. A licensed protector
and runtime compatibility testing are still required before claiming comparable
protection. Do not feed the whole APK through a packer blindly: BridgeServer runs
via app_process, bypassing the protector Application bootstrap. First validate
selective method protection or a separate bridge artifact. Protecting all classes
can break startup and the installer's BridgeServer DEX check.
Keep the existing signing certificate for upgrades. Do not enable root rejection:
our application deliberately runs on a rooted head unit. Do not upload the APK,
keystore or private signing material to a third-party service without authorization.

Vehicle acceptance after the first minified Release: bridge startup after reboot,
VIN/subscription refresh, Raha model load/wake/button/cancel, overlay, star Back,
and vehicle action execution. These have not been tested on hardware here.

## Validation status
Release Kotlin compilation passed. Full R8 verification is NOT complete: Java compilation failed reading annotation-experimental-1.4.0-api.jar from the local Gradle transform cache (AccessDeniedException), before R8 executed. No release APK was generated. Run :app:minifyReleaseWithR8 from the owner's Android Studio environment and resolve any R8 diagnostics before distributing. Vehicle testing remains required.


## DPT integration update (2026-09-16)

See `deployment/protection/README.md`. Pinned DPT v2.21.0 integration and a narrowly scoped exclusion-aware initializer patch are implemented. R8 succeeded using local JBR 25 javac; the earlier cache failure is no longer the final validation status. Boundary-preserving keep rules retain obfuscation while preventing Raha implementations from migrating into shared classes. Six guard tests passed. A 24-class unsigned structural DPT probe passed preservation checks. No signed protected release or vehicle runtime test has been completed. A fresh signed Release build is required before invoking the protection/signing scripts.
