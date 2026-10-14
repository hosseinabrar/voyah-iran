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

- Windows UI parity pass: fixed XAML palette, RTL home actions, separate progress screen, independent APK/removal/permission/help windows. Four visual-contract checks pass against the Windows XAML, in addition to the five Publish tests.
- Native desktop inspection now available: home, help, progress and permissions inspected using a payload-free development build. Corrected double-mirrored action ordering and log alignment during inspection. This is not a Windows-versus-Mac pixel comparison; OS window chrome and font rendering differ.

## Universal / macOS 12 source changes (not built)

- User requested no builds without explicit permission. No Swift/.NET/Android compilation or Publish was run for these changes; existing dist artifacts remain single-architecture.
- Pipeline now plans both .NET RIDs and Swift slices, one Android build, two matching payloads and one stable-named app/ZIP. Python mocked-process tests exercise this plan without invoking build tools.
- Source target lowered to macOS 12; removed SwiftUI Layout/defaultSize dependencies and preserved measured RTL flow using older APIs. Swift compilation and visual verification of the replacement remain pending.
- Downloaded Intel Temurin 25.0.4.1+1 from the official adoptium/temurin25-binaries GitHub release. Archive SHA-256: e6229d9504f7922053ab31821b9e6bee8761daf7b026a3476d1a027563009880 (matches GitHub asset digest).
- Read-only Mach-O architecture and minimum-OS checks pass for both installed JDK trees against macOS 12. Existing universal ADB has minimum targets 10.14 (x64) and 11.0 (arm64). These headers do not prove runtime compatibility on an older OS.
- Current .NET support policy excludes OS releases past vendor support, including macOS 12: https://github.com/dotnet/core/blob/main/release-notes/8.0/supported-os.md. Compatibility targeting is distinct from vendor security support.

Pending: first universal build, both-architecture launch and full install tests, macOS 12 runtime/UI testing; Apple notarization; actual vehicle installation/boot verification, including protected APK runtime behavior, on each supported variant.

No vehicle was connected or modified, no remote deployment was performed, and no release was pushed.
