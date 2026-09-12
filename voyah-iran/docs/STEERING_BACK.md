# Star button → Android Back

## Contract

- Only Qinggan `KEYACTION_SHORT` becomes Android Back.
- LONG, HOLD, release/stop and DOUBLE retain the previous OEM key mapping and action.
- Unchecking restores the exact saved mapping. No guessed factory default is used.
- A foreground service owns interception while other apps are open. It has a notification with a restore action.
- The app needs the compatible exported Qinggan service. This feature does not use root, Accessibility screen access, or Android `KEYCODE_STAR`.

## Evidence from supplied firmware

The implementation was derived from the user's decompiled `KeyManagerService-release` and the shared Binder classes in `Launcher-release-signed/smali_classes3/com/qinggan/system`. These are reverse-engineered private APIs, not an official cross-firmware SDK.

| Evidence | Finding |
| --- | --- |
| `QGKeyEvent` | SWC_USER_DEFINE = 0xC12; OEM BACK = 0x21; SHORT = 0x2000. OEM BACK is translated to Android key code 4. |
| `IKeyManagerService$Stub$Proxy` | Transactions 3/4 register/unregister interception, 7 injects a QG event, 8/9 write/read mapping. |
| `QGKeyEvent.writeToParcel` | Key, action, flags, Bundle, display ID, in that order. |
| `IKeyManagerCallback$Stub` | Transaction 1 = onKeyEvent; 2 = onEvent, followed by package string and integer context. Both return consumed boolean. |
| `KeyManagerService$1` | Mapping and listener writes enter the engine message queue; the immediate Binder response is not confirmation of execution. |
| `KeyManagerSinkCollector.push` | Translation happens before interceptors and before the OEM action sinks. |
| `TranslatorSink.mappingAll` | Mapping affects all press phases, so directly mapping the star to Back would not preserve long press. |
| `EventListenerSink` | An interceptor consuming a key stops downstream dispatch. Keys 1 and 2 are excluded from interception; 0xC12 is not. |
| `KtAndroidSink` | OEM Back 0x21 maps to Android Back 4. SHORT injects one down/up pair. |

The adapter explicitly binds `com.qinggan.keymanager.service/.KeyManagerService`, checks version name 1.0.0 and interface descriptor `com.qinggan.system.IKeyManagerService`. Version/descriptor checks do not prove firmware equivalence. Other firmwares need independent validation. Android binding lifecycle reference: [Bound services](https://developer.android.com/develop/background-work/services/bound-services).

## Routing

Before any mutation, a synchronous SharedPreferences commit records the previous mapping. The handler is registered for 0xC12, then the star's mapping is changed to identity (0xC12 → 0xC12). This keeps interception specific to the physical star, instead of intercepting another button with the same configured action.

On SHORT, the handler consumes the original event and queues one OEM Back event. DOWN/UP are buffered until the firmware classifies the press, preventing the old action from firing on key-down during a short press. For a long/hold/double press, buffered events and subsequent phases are replayed with the saved key code, preserving their action, flags, Bundle and display as received. This defers the old DOWN until classification. If the saved mapping is already identity, non-short events fall through normally, avoiding a reinjection loop. Nested mappings are rejected. Callback work is queued on one executor; Binder injection must not block the callback while the OEM engine waits for its result.

The handler does not translate other steering buttons or combination key codes. Dedicated OEM emergency/combination handling upstream of translation is outside this feature. A trailing SHORT after a detected HOLD/LONG is not converted to Back.

## Recovery and limitations

- Restoration is journaled before its OEM write. Its saved value is cleared only after reading the original mapping back and releasing interception.
- Process restart or boot resumes an outstanding enabled journal; an interrupted restoration resumes restoration instead.
- A normal service destruction attempts restoration. Abrupt process death/force-stop cannot execute cleanup. Until the service restarts, the OEM identity mapping can remain and Back may be unavailable. Reopen the app and use restoration if recovery is needed.
- Uninstalling or clearing app data destroys the journal; turn this feature off first. Android does not allow an app to reliably restore settings after it has been removed.
- Another app or the OEM settings UI can overwrite the mapping. The UI shows recovery needed; it does not continually overwrite that external choice.
- Read-back verifies stored mapping, not physical key delivery, downstream behavior, or plugin overrides. Foreground restrictions and service interception priority also need vehicle testing.
- Short/long behavior, lack of duplicate actions, lifecycle and OEM plugin compatibility have **not yet been tested on a car**. Do not treat JVM tests or successful compilation as that validation.

## Vehicle acceptance test (when packaging is authorized)

1. Record the current short and long behavior, firmware identity and OEM mapping before enabling.
2. Test with at least two different factory star assignments, plus the default assignment.
3. Enable. In an installed app with two navigation levels, press once: exactly one Back, no original short action.
4. Hold and release: original long behavior, no Back during hold or release. Test HOLD/LONG_STOP too.
5. Repeat short presses after a long press, double press, and other steering buttons; verify no interference.
6. Leave Voyah Iran, rotate/recreate the Activity, sleep/wake, restart the service/head unit. Confirm correct behavior and recovery notification.
7. Disable: both short and long must match step 1, with no handler remaining.
8. Simulate service denial/death and interrupted restore. The UI must not claim success; saved recovery value must remain available.
9. Change the OEM star assignment externally while enabled. Confirm that conflict is visible and explicit restoration restores the original saved assignment.

No APK was built or installed for this change.
