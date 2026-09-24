# Display command repair — 2026-09-23

Evidence: `screen log-2026-09-23_13-15-18.txt` recognizes brightness and day mode, then Settings crashes with SIGSEGV after the injected action script loads. This is an execution failure, not evidence of command recognition or protection failure.

Brightness and day/night/auto now use the installed Settings APK's `ISystemPolicy` Binder interface (`qg.system_policy`) from the bridge. No Settings activity or process injection is used. Calls match OEM DisplayPresenter: `setBackLight(value, 1, displayMask, false)` and `setDimmer(mode, 1, 19, false)`. Screen masks are central=1, passenger=2, cluster=16. Theme modes are night=0, day=1, automatic=2. Brightness remains 1–100; percentages map to this range and relative changes are 10 points.

Preserves the OEM LCD thermal guard through `qg.power_manager.getLCDTemp()`, and the cluster's additional ICM_SCR_BRI_LEVEL CAN write. Readback is checked after writes. Failure is reported rather than replaying an uncertain write. `RahaDisplay` logs service acceptance and expected/actual feedback. Plural display brightness requests now target all displays.

Validation: targeted Kotlin compilation and eight JUnit tests (display phrases plus music and previous regression tests) passed. Vehicle verification remains required. Publish/install the updated bundle including the bridge, then test central/passenger/all brightness and day/night/auto. Capture full logcat including RahaDisplay if rejected or ineffective. No release APK was built during this repair.

## Rear display lock

`rear monitor log-2026-09-23_13-14-33.txt` likewise recognizes SET_REAR_DISPLAY_LOCK, then VehicleSetting crashes with SIGSEGV after injection. Replaced that path with `/system/bin/content` insert/query against `content://qinggan.settings/global`, key `MainScreenVehicleRearSCRLock`: locked=2, unlocked=0. This matches DrivePreferenceFragment's switch, preserving provider notifications rather than editing its database. Readback confirms the saved preference, not physical panel feedback. The installed OEM `AppCommonUtils.is97CBasePlatform()` capability check is retained. No process attachment or menu opening.

Screen disable/deactivate/off phrases mean lock; enable/activate/on mean unlock. Explicit “disable the lock” means unlock. “عقف” is accepted as a recognition alias for “عقب”. Fourteen lock/unlock phrases covered; nine targeted JUnit tests pass, and changed bridge Kotlin compiles. Requires updated bridge installation and vehicle test.
