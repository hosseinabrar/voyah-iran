# Display policy (Android 11 OEM services.jar)

## Behaviour

- Settings first checkbox: نمایش برنامه‌ها در ابعاد و جهت درست. Default enabled.
- Display 0 only; standard fullscreen activities installed by the user, plus
  `com.abrar.voyahiran` explicitly, even if installed as a system application.
- Existing OEM/system apps, home, PiP/multiwindow, passenger display, IME and
  system/assistant overlay windows are excluded.
- Landscape orientation; activity bounds inset 145 px left and 50 px top from
  parent bounds. Parent content/bottom restrictions remain in force.
- On a 1920x720 parent: window bounds [145,50,1920,720]. Do not interpret content
  height as necessarily 670: OEM content insets can reduce it further.
- Turning off reconfigures activities from the original requested orientation.
  The stored manifest/activity orientation is never overwritten.

## Architecture

FrameworkPatch applies exact-template hooks to ActivityRecord orientation and
resolved configuration, and WindowState.applyGravityAndUpdateFrame. While enabled,
ActivityRecord size-compat bounds/scale are reset after resolution so a stale
portrait rectangle cannot override the safe bounds. Compatibility insets remain
available for native recomputation when disabled. The helper
is in the same WM DEX/package. Every referenced OEM WM member is checked before
patching. No PackageManager Binder calls from hot WM paths, and no Frida hooks.
The WM and IME classes may live in different DEX entries. Only those DEX entries
are replaced; every other ZIP entry is byte-for-byte checked. All smali classes
are compared after reassembly, not just the modified methods. Unknown method
implementations, missing dependencies, partial/different hooks fail closed.

A main-thread ContentObserver watches Settings.Global `voyah_iran_display_policy`.
Absent => enabled. An explicit request is `1:<32 hex nonce>` or `0:<nonce>`.
After updating existing activities under the WM lock and requesting traversal,
the helper writes the same request into `voyah_iran_display_policy_ack`.
The authenticated bridge accepts only `app-display enable|disable`, waits for
its exact nonce (not a stale success), and restores the old setting on timeout.
The app persists its checkbox preference only on successful acknowledgement.
It shows a failure and retry if the new framework/bridge is not installed.
Display polling has its own serialization and does not hold the vehicle-command
execution lock. Layout policy does not depend on the vehicle subscription gate.

## Packaging / build

Normal Visual Studio Publish bundles these precompiled helper smali and exact
original-method templates automatically using paths relative to the repository.
The normal pipeline also updates the APK and bridge. End users need no SDK/JDK.
This feature requires the new Windows installer once and its final reboot;
installing just the APK is not sufficient.

If modifying helper Java, regenerate checked-in smali with `Build-Helper.ps1`:

    .\Build-Helper.ps1 -JavaHome <JDK-directory> -AndroidSdk <SDK-directory>

Defaults: platform android-36, build-tools 37.0.0; overridable parameters.
Compile-only stubs must NEVER be packaged into services.jar. The script selects
only the four VoyahIranDisplayPolicy classes, runs 17 policy tests, then copies
the generated smali. It keeps temp artifacts for inspection.

Before shipping changes to the helper, run FrameworkPatch against a local OEM
services.jar and then against its result to verify the already-patched path.
The existing framework backup/hash/restore process remains in use.

## Validation on 2026-09-19

- Helper logic: 17 assertions passed (scope, own system app, orientation, geometry,
  content/IME preservation, excluded windows and disabled behaviour).
- Installer core: all 68 checks passed, including payload required paths.
- Android :app:compileDebugKotlin passed. Full Gradle JVM tests were blocked by
  a local javac ZipFS AccessDeniedException while reading dependency JARs.
- Local input framework (existing keyboard patch) SHA256:
  ede647f69dfdfc4ad86af3a9da6b056a8bff47e6fe9e41d33ab3837b2d26f0ce
- Local patched SHA256:
  d9cf0e22d4a93aeaa7b8f033e7d57691a0e96415653b71d511ef2ee0fad0452e
- Roundtrip 7,946 classes verified; helper dependencies checked; rerun detected
  already=true and preserved the patched file hash.
- No vehicle was attached or modified. Visual behaviour on hardware is pending.

## Vehicle acceptance test

1. Publish installer normally, install/update, allow installer reboot.
2. Open Voyah Iran: first checkbox should be enabled, no failure message.
3. Open Chrome, Myket and a portrait-preferring app. Check landscape, 145px left
   and 50px top reserved; touch coordinates/search box usable. Test fullscreen
   video too. Open Gboard and verify its existing popup behaviour is unchanged.
4. Open Voyah Iran itself and confirm the same reserved margins.
5. Disable checkbox; reopen the test apps, confirm native orientation/layout.
   Enable again and confirm restoration. Reboot and confirm preference persists.
6. Verify passenger display and OEM menus/camera remain unchanged.
7. If unsuccessful capture full installer log and unfiltered logcat, plus:

    adb shell settings get global voyah_iran_display_policy
    adb shell settings get global voyah_iran_display_policy_ack
    adb shell dumpsys activity activities
    adb shell dumpsys window windows

Useful logcat tags: VI/DisplayPolicy and VI/AppDisplay.
Emergency policy off (no JAR replacement):

    adb shell su 0 settings put global voyah_iran_display_policy 0

Disable the checkbox before reopening the app after emergency override, since
its persisted preference is reapplied on application startup. For framework
rollback use the installer's existing Restore-Framework.ps1 and matching backup.
