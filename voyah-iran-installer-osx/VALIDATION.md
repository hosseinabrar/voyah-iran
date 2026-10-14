# macOS port verification — 2026-10-14

Host: Apple Silicon macOS, Swift 6.0.2, .NET SDK 8.0.426, Temurin JDK 25.

- SwiftUI app and build-settings helper compile.
- .NET backend builds with zero warnings/errors.
- Development `.app` and ZIP generated; nested and outer ad-hoc code signatures verified.
- Full Android-first Publish completed on both branches with R8 and DPT enabled: Gradle release build, original-certificate signing, protection static checks, zipalign/re-signing, offline payload verification, native app build and ZIP packaging.
- Launcher retains its existing Android version 2.0.1 (code 16); version-2.0.3 builds Android 2.0.3 (code 18). Both final APKs use the same original signing certificate. No Android version was changed by this port.
- Final Apple Silicon apps pass `codesign --verify --deep --strict`. Each output includes `build-report.json` with APK/ZIP hashes and protection settings.
- 23 Mac-specific checks pass: ARM64/Intel manifest contracts, Unix executable permissions, old Windows manifest compatibility, corruption/missing-file/path/duplicate rejection, clock readback and failure cleanup.
- Five Publish tests pass, including a failed Android build preventing stale-APK packaging and unique signing certificate enforcement.
- Launcher branch: all 127 existing installer checks pass on macOS, including simulated successful installation, reconnects, failure paths, logs and embedded-map shutdown ordering.
- Release version-2.0.3: all 106 existing installer checks and all 23 Mac-specific checks pass.
- Real H97X original services.jar patched locally with the Mac JDK/APKTool. Tool verified 7,946 classes and unchanged unrelated ZIP entries.
- Real H97A Android 9 services.vdex reconstructed/patched locally. Tool verified the input hashes and assembled 4,717 classes.

These local patch artifacts are validation fixtures under `voyah/work/macos-validation`; they are **not** files to flash manually. Production still pulls and validates the connected car's files.

Pending: visual UI inspection where desktop permissions are available; Intel-host build; Apple notarization; actual vehicle installation/boot verification, including protected APK runtime behavior, on each supported variant.

No vehicle was connected or modified, no remote deployment was performed, and no release was pushed.
