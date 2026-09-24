# Lane controls — 2026-09-23

Verified against OEM DriveAssistanceFragment and FragmentDriveAssistanceBindingImpl:

| Setting | Field | Values |
| --- | --- | --- |
| Master | LANE_ASSIST_SWITCH | on=1, off=2 |
| Mode | LDW_RDP_LKS_FUNC_ENABLE | warning=2, warning + steering=3 |
| Warning type | LDW_WARNING_TYPE | voice=1, vibration=2 |
| Sensitivity | LDW_LKS_SENSITIVITY | low=1, high=2 |

The binding's callbacks 13/14 belong to LowRb/HeightRb and call setLKASensitivity(1/2). These are distinct settings, not interchangeable boolean values.

Generic enable now changes only the master, preserving selected mode. Explicit mode selection retains enable-then-mode behavior, with readback before proceeding. Disable has precedence over the word warning; previously a warning-containing disable phrase could select warning mode. Alert-type and sensitivity commands change only their respective fields. All use existing direct CAN Binder execution, without menus/process injection.

Recognizer accepts singular/plural lane wording, radar/driving prefixes, activation noun forms, voice/vibration and relative sensitivity. “بین خطوط رانندگی کن” selects steering assistance; “رانندگی بین خطوط رو کم کن” selects low sensitivity. Centering remains unsupported by the installed two-mode interface and is not silently substituted.

Validation: 15 targeted phrase examples, nine recognizer test methods and six driver-assistance executor tests passed. Changed Android execution classes compiled. No new lane-specific hardware log supplied; vehicle verification is pending. Update app and bridge together through installer.
