# Implementation status: direct model hook added

2026-09-21: NeshanHookManager owns a separate private copy of the verified existing injector. Source script is deployment/neshan/direct-navigation.js, embedded in NeshanHookScript.kt so the bridge update carries the exact script. `neshan-direct enable/disable/status` is authenticated through BridgePolicy. Enabled state persists in the bridge directory; subscription expiry suspends it. Disabling or missing/new process clears owned display and detaches; restart reattaches with backoff.

Hook: o10.i.b(int,double,List), after original invocation. Reads l10.b at current index; computes max(0, step.a()-travelled), same formula as t10.d.a, and reads f()/g()/h()/i() for maneuver/road. m10.b.b(List) clears snapshot on route replacement. No original args/results changed. No UI, notifications, resource screenshots, heap scans or periodic reading of stale objects. One-second output carries true source age; 5-second stale source clears. Unknown/roundabout/arrival icons remain text-only rather than abusing LEFT/RIGHT modifiers. OEM icon values remain experimental.

The previous accessibility publisher was removed; accessibility is now solely for free-driving menu entry. Script captures typed values whether or not an Activity is visible, but only hardware testing can establish whether this Neshan callback continues in background. Logs must compare foreground and Home with an active route. If source events cease, move upstream; do not substitute stale polling.

Build/update: publish Windows installer normally and reinstall with it, because bridge code changed. Injector is already bundled for ROA; no extra download. Prepare-Payload rejects APKs missing the embedded direct hook marker. No release APK/publish was generated during implementation.

Validation: compileDebugKotlin; Node syntax check and mocked hook tests cover source age, exact distance, semantic modifier, route reset, invalid index, preserving original return/exception, disable, and embedded-script parity. Run `node deployment/neshan/direct-navigation.test.js` after editing script; keep embedded source synchronized. Test on real car still needed (attach permissions, current Neshan profile, background updates, OEM glyphs/icons).

Logs: VI/NeshanDirect (attach/profile/errors), VI/NeshanDirectData (actual source fields), VI/NeshanDirectRuntime (injector diagnostics), VI/NeshanClusterBridge (OEM dispatch). An ATTACHED/ready marker is not proof of received navigation data.

---

## Static research preceding implementation

# Neshan direct in-process navigation data (2026-09-21)

## Decision
No notification input, screenshots, OCR or silhouette classification for the target integration. The screenshot reader and its capability have been removed. Existing accessibility text publishing is legacy and is not the desired background solution.

APK inspected: Neshan 14.12.0.3 / 70789, org.rajman.neshan.traffic.tehran.navigator. Obfuscated names are version-specific. All findings below are static, not proof of runtime/background success.

## Confirmed data model
- l10.c = UpcomingStepInfo. a(): int distanceToStart; d()/e(): namePrefix/nameSuffix; c(): t10.k maneuverIcon; f(): destination flag.
- t10.k: b(): drawable resource ID; a(): maneuver icon index. Resources.getResourceEntryName(b()) identifies the actual selected asset; no screen pixels needed.
- t10.b.a(int, java.util.List, boolean): constructs UpcomingStepInfo from selected l10.b.
- l10.b = StepInfo. a(): distanceToStart; c(): route step index; f(): rx.h maneuver type; g(): rx.d modifier (LEFT/RIGHT/STRAIGHT etc); h()/i(): name prefix/suffix; j(): turn index. Prefer these semantic enum values to asset filenames when possible.
- t10.g.e(rx.h, Integer, rx.d, boolean) selects icon; c(rx.d) handles ordinary turns. Neshan icon indices are not OEM cluster icon indices: map explicitly.
- m10.b implements m10.a; h(int, double) updates current index/distance and feeds o10.g and o10.i, unless backing store l() blocks updates. c() returns list of StepInfo. b(List<rx.q>) sets/replaces route.
- u10.t.u(int,double) forwards to m10.a.h. u10.t.u2(int) constructs next-step model and updates StateFlow v10.d; H0() clears subscriptions. Therefore do not assume this UI consumer stays active in background.
- vl0.a is Android Auto state: c() currentStepRemainingDistance, l() StepInfoModel; hl0.b.c() name, b() drawable ID. jl0.i.a() builds Car App Trip/Step from these. Android Auto state alone may not be instantiated during standalone usage.

## Integration approach
Use read-only Java method hooks inside the Neshan process via the root injector already used in deployment/roa (Frida inject 16.7.19). Do not scan RAM or hardcode pointers: ART GC/ASLR can move objects. Call originals unchanged and collect immutable values after successful calls. Observe route changes/cancellation and process death, and clear cluster on loss of a fresh source.

First instrument m10.b.h/b and t10.b.a, with logs of actual step, distance, modifier, drawable entry name and update timestamp. Compare foreground versus Home/background while navigation stays active. If m10.b updates stop, move upstream to its navigation-engine producer rather than polling a frozen UI object. Do not refresh freshness merely by reading the same object periodically. This is the remaining research/runtime validation gate.

Root alone does not guarantee attach under every SELinux/ROM policy. Existing injector feasibility is encouraging, not runtime proof for Neshan. Scope attachment to the selected installed Neshan process, supported APK profile and user's cluster setting; keep no public network listener. Retain app subscription checks. A future bridge owner should publish at most once per second and own cleanup/watchdog.

CPU: reading existing typed objects at update points avoids continuous screen capture and heap scans. Attach and Java instrumentation have overhead; measure rather than promise zero load.

Persian cluster rendering is a separate issue: full Persian name was received and sent in the test, but tester saw digits/hyphen only. Latin fallback currently exists. Direct source data does not itself add missing cluster glyphs.

## 2026-09-21 09:14 Unicode transport correction
Hardware log neshan_nav3 shows `NeshanDirectData road=????? ???? ???????` before OEM dispatch; other Persian accessibility lines remain intact. Thus this instance is upstream of cluster rendering, most likely frida-inject console/locale conversion. Script now JSON-escapes every non-ASCII UTF-16 code unit before stdout; bridge JSONObject restores Unicode, then existing Latin fallback runs. Pipe decoding explicitly UTF-8. NeshanDirectText logs restored original and final Latin text. Node tests verify Persian survives an ASCII-only channel and surrogate pairs round-trip; embedded-script parity also passes. Actual vehicle confirmation remains needed. Full installer/bridge update required (script embedded in DEX); replacing APK alone will not refresh the running bridge.
