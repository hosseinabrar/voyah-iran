# Driver-assistance commands: direct Binder (2026-09-20)

The 13 driver-assistance actions no longer launch VehicleSetting or use Frida/UI clicks.
`RahaVehicleBridge` routes them to `OemDriverAssistanceBinder` before the remaining
phase-seven routes. The obsolete ADAS branches were removed from `OemPhaseSevenActions`.

The installed VehicleSetting APK supplies `VehicleState`, `DFVehicleState`, and
`ICanBusService.Stub`. The bridge uses `qg.canbus` and checks its Binder descriptor.
This matches `CanBusTool.setVehicleStateBack`: its actual write is
`CanBusManager.setVehicleState`; its Handler subsequently refreshes feedback/UI.
No Activity/Fragment context or native injection is involved.

## Verified OEM mappings

Source: extracted VehicleSetting in `work/raha-oem/settings/sources` in the research workspace.

* DriveAssistanceFragment.onHintSwitchClick / FragmentDriveAssistanceBindingImpl:
  DOW, FCW, RCTA, RCW, AEB, BSD, FCTA, TSR and LANE_ASSIST_SWITCH use on=1/off=2.
* **FVSR_SWITCH uses on=2/off=1**, as specified by the widget binding; do not infer
  this from generic switch conventions or analytics strings.
* FCW_SENSITIVITY: low=1, medium=2, high=3.
* setLKAState: LDW_RDP_LKS_FUNC_ENABLE warning=2, keeping=3.
  Enable LANE_ASSIST_SWITCH first; off only disables that switch.
  Centering is still unsupported: no speculative substitution with another feature.
* setLKAWarningType: LDW_WARNING_TYPE voice=1, vibration=2.
* FragmentSafetyMaintenanceBindingImpl: GESTUREID_REQ on=2/off=1.
* DMS_CAMERA_CTL on=2/off=1; read **DMS_CAMERA_STATUS on=1/off=0**.
* SafetyMaintenanceFragment.setDmsSen: DMS_WEARY_SET=2 followed by
  DMS_SEN_SET normal=0/sensitive=1. Preserve a 300ms inter-command interval.

All requested fields are resolved and their current feedback ranges checked before
any write. Supported factory flags are consulted for DOW/RCTA/AEB/BSD/DMS/gestures.
Other requests require valid live feedback; they do not modify vehicle configuration
or subscription flags. Already-matching values are idempotent. Each changed value
requires matching feedback within 3 seconds, with no repeated writes or UI fallback.
For multi-field requests a failure stops subsequent writes; earlier successful
changes are not rolled back. OEM camera privacy/availability restrictions remain;
the bridge does not grant camera consent or overwrite privacy settings.

## Validation and deployment

* `:app:compileDebugKotlin`: passed.
* Six standalone JUnit tests: passed (all toggle polarities, camera feedback,
  missing/invalid state preflight, failed acknowledgement, idempotence, lane/sensitivity values).
* Hardware execution is **not yet verified**.
* Publish and run the updated installer so the root bridge restarts with the new code.
* Log tag: `RahaAdas` records action/parameters, reads, writes and expected/actual feedback.
* This change does not repair the separate scenario or other phase-seven UI/injection routes.
