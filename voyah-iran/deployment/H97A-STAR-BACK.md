# H97A star Back (2026-09-27)

The earlier H97A port fixed the three-field QGKeyEvent parcel (code, action,
Bundle). Unlike API 30, that parcel cannot carry a destination display. A new
bounded bridge operation, `steering-back center`, handles synthesized short Back
presses on API 28; API 30 continues using its existing display-aware OEM replay.

Evidence in the supplied Android 9 services DEX:
`InputManagerService.injectInputEvent(InputEvent,int)` passes literal displayId 0
to `injectInputEventInternal(InputEvent,int,int)`, which passes it to the native
injector. LegacySteeringBack uses this API from the authenticated root bridge and
does not cache a task, window or previous display. It sends Android KEYCODE_BACK
(4), not Qinggan BACK (33), with one DOWN/UP pair, keyboard source and virtual device.
The OEM AndroidSink also translates 33 to 4 before using InputManager, but the new
path bypasses the OEM replay/interceptor chain for this synthesized short press.

The pair is serialized separately from slow vehicle operations. UP is attempted
even when DOWN fails; uncertain injection is never retried. Failure invalidates
the app's star interception state through the existing recovery path. Original
long/double actions still use OEM replay; disabling the feature still restores the
saved mapping. The command accepts no key code, display id or additional arguments,
requires an active subscription session, and rejects platforms other than API 28.

Logs: `StarEvent`, `backend=android9-input display=0 downUpAccepted=...`; acceptance
is not proof that a particular app consumed Back. Native routing/UI behavior still
requires hardware verification: open a page on the center display, press Star,
move an app to/from the passenger display, and repeat on the center app. Also test
long press and disabling the feature. Install with the updated Windows installer
so the bridge APK is replaced as well as the normal app.

Release Kotlin compilation and 51 standalone JUnit tests (steering codec, routing,
repository and bridge policy) passed. The Gradle debug test task was blocked by a
Java cache AccessDeniedException; the unchanged source tests were compiled and run
directly against the freshly compiled release classes. The Android-dependent
StarDesiredStateTest was not part of that standalone run.
