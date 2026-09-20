# Raha phase 6 — energy, charging and scenes

Implemented 2026-09-20. Software tests pass; physical operation has not been tested on a car.

## Execution and firmware selection

`RahaPhaseSixRecognizer` is shared by the test app and Voyah Iran. The authenticated root bridge delegates these actions to `OemEnergySceneActions` in the already running `com.qinggan.app.vehiclesetting` process. The bounded script runner uses the existing verified Frida runtime. A timeout/error is never replayed automatically. Logs: `VI/RahaOemAction`, `VI/RahaVehicle`, `RAHA_ENERGY_CAPABILITIES`, `RAHA_ACTION_WRITE`.

The energy profile is obtained from the installed `DrivePreferenceConfigs`, the same class used by `FragmentDrivePreferenceBindingImpl`. Transmission support must be true. `isDoFengH97acd()` and, on newer firmware, `is97XOversea()` select the labels and visibility. The older China class has no X-overseas method.

| Menu profile | Value 1 | Value 2 | Value 3 | Value 4 |
|---|---|---|---|---|
| Earlier non-C/A, non-X | Smart | EV | Fuel | Save |
| C/A, non-X | hidden | EV | Hybrid | Save |
| X overseas | hidden | EV | Hybrid | Fuel |

`Save` on the last profile is rejected before any write. A pure `EnergyStrategyPolicy` generates the profile maps consumed by the script; there is no fallback from an unsupported spoken name to another mode. Forced EV is not exposed by Raha. «فقط برق» selects EV First (value 2). Actual ECU acceptance still depends on the vehicle state.

## OEM source evidence

Source trees inspected: extracted `VehicleSetting-release-signed.apk` (settings and China variants), `VehicleAir-release-signed.apk`, CAN SDK. Relative Java class locations:

- `com/qinggan/app/vehiclesetting/fragments/drivepreference/DrivePreferenceConfigs.java`
- `.../DrivePreferenceFragment.java`: `setPowerMode`, `onHintSwitchClick`, `powerChangeProToOx`, `onProgressChanged`
- `com/qinggan/app/vehiclesetting/databinding/FragmentDrivePreferenceBindingImpl.java`: radio labels/visibility, callbacks 16–19
- `res/layout/fragment_drive_preference.xml`: reserve slider 25..80, step 5
- `com/qinggan/app/vehiclesetting/fragments/charge/ChargeFragment.java`: stop-charge confirmation sends `HUM_STOP_CHARGE=1` then `CHARGE_GUN_UNLOCK_SET=1`
- `.../databinding/FragmentChargeBindingImpl.java`: `DRIVER_PREHEAT_SET` on=1/off=0
- `com/qinggan/app/vehiclesetting/scenemode/SceneModeActivity.java`: scene intents, service actions and quick-warm broadcast
- `com/qinggan/app/vehiclebase/model97/voice/VoiceSmartModeListener.java`: native scene entry restrictions and QGBus exit events
- `com/qinggan/scene/powerhold/PowerHoldModeManager.java`: complete entry/exit bundle, status, duration preferences, engine setting
- `R.array.power_hold_duration`: 60,90,120,150,180,-1,30; runtime rechecks the installed array

A standalone reserve-target request requires mode 4 already selected and uses `(percent-25)/5`. Explicit combined requests such as «سیو ۷۰» select supported Save and set the target; both parameters and firmware support are validated before writing. Power-hold duration is persisted through OEM preferences; changing it while inactive does not enter the scene. While active, the OEM manager updates its bundle and related state. Engine charging is available only with the OEM engine-support flag and follows the manager's session setting.

Scene exits exist for rest, camp, car wash and power hold. Baby care, party and quick warm are one-shot presets without a general undo/exit method. Every spoken scene-exit request (including a named preset) closes the actual persistent active scene(s). No active scene means a no-op; preset effects are not rolled back. Entry uses OEM scene services/activities/managers and checks the corresponding parking, battery, screen and scene-conflict conditions. Entry may present OEM UI (especially the car-wash confirmation); a dispatch result is not proof that the ECU or user completed it.

## Pending mirror action

`SET_MIRROR_FOLD` parses but deliberately sends nothing. H97's `VoiceDoorMirrorListener.onFold/onSpread` reports unsupported; `RVMAutoFoldingSwitch`/`MIRROR_FADE_IN_OUT_FUNCTION_SWITCH` concern automatic folding. Generic `REAR_MIRROR_FOLD_SET` exists in the SDK but no H97 direct-control usage was found. Catalog row 276 stays yellow. Need a captured OEM manual-fold operation and CAN/service mapping before enabling it.

## Sample vehicle tests

| Phrase | Expected |
|---|---|
| ماشین رو بذار تو حالت سیو | Save on supported menu, rejection on Fuel-first overseas menu |
| حالت اولویت بنزین رو فعال کن | Fuel value selected according to actual menu profile |
| حد حفظ شارژ باتری رو روی پنجاه درصد بذار | 50% target while reserve mode is selected |
| شارژ رو متوقف کن | OEM stop and connector unlock sequence |
| کنترل دمای باتری رو روشن کن | OEM preconditioning switch on |
| حالت کمپ رو فعال کن | Native camp entry, subject to OEM conditions |
| از سناریوی فعلی خارج شو | Exit the single persistent active scene |
| زمان حفظ برق رو روی یک و نیم ساعت تنظیم کن | 90 minutes, without activating an inactive scene |
| شارژ با موتور در حالت حفظ برق رو فعال کن | Engine setting; updates active power hold if applicable |

Rebuild/publish installer so its bundled APK/bridge are both current, then install through it. No new system-framework patch or Windows installer source change is needed for this phase.

## Verification

- Android project: `:command-core:test :app:compileDebugKotlin` (offline) passed; recognizer suite includes generated regression tests including generated phase-6 aliases and word order.
- `EnergyStrategyPolicyTest`: 4 JVM tests passed (profile labels, unsupported modes, non-extender, reserve bounds/encoding).
- `node tools/test-oem-energy-scenes.cjs`: 12 tests execute the shipped script against fake OEM objects (not a hardware substitute).
- Workbook: only 8 green cells and 9 implementation notes changed; other cell values/styles and OOXML parts preserved.

Speech follow-up: all supplied energy/charge/mirror/scene/power-hold phrases have explicit regression cases. «ماشین برای یک ساعت روشن بمونه» activates power hold for 60 minutes; a duration-only setting still does not activate the scene. Mirror phrases are recognition-only pending hardware mapping. «دمای باتری رو بالا ببر» enables OEM battery preconditioning; it does not promise a particular temperature.
