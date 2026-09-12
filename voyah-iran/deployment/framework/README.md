# Keyboard framework patch v1

This JAR is specific to the supplied firmware, not a generic Android framework replacement.

SHA-256:

| Artifact | SHA-256 |
| --- | --- |
| Original services.jar | `0faed7ebf1094c62a4f92b4bc533bbb3c08480a2cd847567cb1cff91b2d1d045` |
| Patched services.jar | `ede647f69dfdfc4ad86af3a9da6b056a8bff47e6fe9e41d33ab3837b2d26f0ce` |

## Change

In `com.android.server.inputmethod.InputMethodManagerService.updateInputMethodsFromSettingsLocked(boolean)`, immediately after the OEM locale branches assign the factory IME ID to `v2` (the `:goto_4` merge point), insert:

```smali
invoke-direct {p0, v2}, Lcom/android/server/inputmethod/InputMethodManagerService;->voyahIranSelectedIme(Ljava/lang/String;)Ljava/lang/String;
move-result-object v2
```

The new private method is preserved in `keyboard-hook.smali.txt`. It reads `Settings.Global.voyah_iran_ime_override`, validates that the IME exists in `mMethodMap` and is enabled, then returns its ID. Missing, uninstalled or disabled selections retain the original OEM locale decision. Existing downstream selection/subtype handling remains in place.

The app enables the IME, writes the override through its authenticated root bridge and calls `ime set --user 0`. It verifies `mCurMethodId` from the primary `input_method` service through the OEM reset window. The shared `default_input_method` setting is no longer treated as proof of the primary service's current IME. On a failed selection it attempts to restore the prior override.

`InputMethodManagerServiceExt` is not modified. It continues to select the factory passenger IME. Display behavior, persistence and interaction between both services still need vehicle testing.

## Build and validation

Decoded and assembled with APKTool 3.0.3, JDK 21. The original ZIP was retained and only `classes.dex` replaced from the assembled output. Every other ZIP entry, including `classes2.dex`, is byte-for-byte identical to the supplied JAR. The patched primary service was decompiled again with JADX 1.5.6 and the call site and helper were verified.

Android app Kotlin compilation and resource processing passed; no APK was generated. Twenty JVM tests passed for the keyboard repository and bridge command/protocol validation. Windows PowerShell 5.1 and shell syntax checks passed. Host filesystem simulations passed install, repeat install, restore, rejection of different firmware, and rollback after an injected commit failure. Host simulations mock Android identity, mounts, permissions, labels, settings and free-space reporting; they do not validate those facilities on the vehicle or emulate Android boot.

## Deployment

Use `../Install-KeyboardFramework.ps1`, not manual replacement. It verifies the exact JAR hash, retains backups, replaces the JAR and removes only backed-up `services.art/odex/vdex` files in the arm/arm64 system oat directories so stale preoptimized code is not retained. Android may regenerate optimized code during the next boot. It does not alter verity, SELinux, other framework JARs, CAN services, or init configuration. Reboot is manual.

Restore reinstates the original JAR and backed-up preoptimized files. ADB and `su 0` must be available for restore; the script cannot guarantee recovery when ADB is unavailable. Keep the on-device backup directory and laptop backup. See `../KEYBOARD-INSTALL-FA.md` for installation and recovery commands.
