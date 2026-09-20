# Energy action recovery — 2026-09-20

The tester's `energy_mode_log-2026-09-20_17-02-15.txt` shows successful recognition of hybrid, electric and fuel. Each request enters `OemActionScriptRunner`, then the **OEM VehicleSetting process** crashes with SIGSEGV, fault address 0x94, ABI arm. PIDs 7900, 8077 and 8200 fail in succession. The injector reports `The connection is closed`; no `RAHA_ACTION_OK` occurs. This identifies an injection-path failure, not a recognizer/DPT failure. The exact native incompatibility is not established by the single anonymous-frame backtrace.

`SET_ENERGY_STRATEGY` and `SET_BATTERY_RESERVE_TARGET` now run through `OemEnergyBinder` before the injection dispatcher. It loads the installed SDK in the root bridge, reads the installed `DrivePreferenceConfigs` flags, and invokes `ICanBusService.setVehicleAndAirConditionBundleState(null, bundle)` as the OEM `DrivePreferenceFragment.setPowerMode()` does. No attachment to the VehicleSetting process is performed for these two commands.

All mode-dependent mapping remains in `EnergyStrategyPolicy`. A missing older `is97XOversea` method follows the older China menu branch; other reflection/configuration failures propagate. Unsupported modes never substitute a different mode. A Save+percentage request resolves both fields before writing and waits for mode feedback before applying the reserve. Feedback is checked once per request with a bounded wait; the physical write is not retried.

Validation: Kotlin application compilation passed; six pure executor tests passed using standalone Kotlin/JUnit. The Android Gradle unit-test pipeline encountered the pre-existing access error on cached `activity-ktx-1.10.1-api.jar`. Hardware behavior has not yet been verified. Install through the republished installer so the root bridge receives the changed code.

Other `OemEnergySceneActions` and `OemPhaseSevenActions` injection-based paths are **not fixed by this change**. The common runner now records script-load architecture, target survival and the last injector message to support further diagnosis. This is diagnostic logging, not a claimed native crash fix. Do not mark these paths hardware-verified based on the energy fix.

Vehicle test: record full logcat, request supported energy modes in sequence, and check both the OEM displayed mode and `RahaEnergy` logs (`route=binder`, installed capabilities, write and confirmed feedback). An energy request must not produce `RahaOemAction` attachment. No APK/installer release was produced by the agent.
