# Apollo experimental settings

The approved main section and the master 18-field bundle remain unchanged.
The bundle fields are feature entitlements, not 18 independent operational switches.
Only verified settings are exposed; no parking/steering start request is synthesized.

## Evidence

VehicleSetting-release-signed.apk decoded in `work/apollo-settings-res` and decompiled in
`work/raha-oem/settings` of the research workspace.

- `DriveAssistanceFragment.java`: setters at 651–668; ALC selection encoding at 450–551.
- `FragmentDriveAssistanceBindingImpl.java`: bindings at 388927–388956 and callbacks 28–38.
- `res/layout/fragment_drive_assistance.xml` and `res/values/strings.xml`: button labels.

| Setting | Values |
|---|---|
| ANP_SWITCH | on=2, off=1 (OEM fragmentIca) |
| ISA_ISLC_SWITCH | on=1, off=2 |
| ISA_ISLC_MODE | 2 auto, 3 manual, 4 display speed limit only |
| ISA_ISLC_OVER_SPEED_WARNING_SWITCH | 1 no, 2 yes |
| ISA_ISLC_SPEED_OFFSET_STYLE | 1 fixed, 2 percentage |
| ALC_SWITCH | 2 paddle confirmation, 1 no confirmation |
| ALC_SETTING | 1 fast lane, 2 overtake, 3 large vehicle avoidance; 4=1+2, 5=1+3, 6=2+3, 7=all |

ALC_SETTING is an OEM combination code, not a normal bit mask. Zero is not selectable.
Speed-offset magnitudes retain the existing vehicle value; these controls select the style only.

## Review of all 18 master fields

This is not a claimed one-to-one mapping between entitlement and switch: some capabilities
share controls, and some have no proven settings interface in the examined Settings APK.

| Master field | Settings finding |
|---|---|
| RPA_FUNC_ENABLE | No verified independent setting; parking action requests are not toggles |
| HPP_FUNC_ENABLE | IVI_VOICE_REQ_HPP_SW exists but is an action, not exposed |
| GLC_FUNC_ENABLE | GLA_SWITCH / GLA_LIGHT_CHANGE_SWITCH already in main section |
| ISLC_FUNC_ENABLE | ISA_ISLC controls in experimental section |
| TLC_FUNC_ENABLE | TLC_CONTROL / TLC_SETTING names exist; values not proven, not exposed |
| NOA_FUNC_ENABLE | ANP/ALC controls found; entitlement-to-setting identity not assumed |
| ELK_FUNC_ENABLE | ELK_SWITCH already in main section |
| ESA_FUNC_ENABLE | ESA_SWITCH already in main section |
| APA_FUNC_ENABLE_SA | Parking operation requests exist; no proven independent setting |
| RPA_FUNC_ENABLE_SA | Same RPA finding above |
| HAVP_FUNC_ENABLE_SA | No verified independent setting |
| ACC_FUNC_ENABLE_SA | IACC_OR_ACC_FUNCTION_SWITCH is not bound by this OEM DF settings UI; values unproven |
| ICA_FUNC_ENABLE_SA | OEM fragmentIca binds ANP_SWITCH; exposed as ANP |
| PLC_FUNC_ENABLE_SA | PLC_SWITCH already in main; ALC confirmation and scenarios in test section |
| HANP_FUNC_ENABLE_SA | No separately proven switch; do not assume ANP solely from acronym |
| ISA_FUNC_ENABLE_SA | ISA_ISLC group available; no separately proven ISA setting |
| ISLC_FUNC_ENABLE_SA | ISA_ISLC group in test section |
| TLA_FUNC_ENABLE_SA | Traffic-light controls already in main; no separately proven TLA mode |

## Runtime and testing

Read-only page refresh resolves symbolic fields from the installed vehicle SDK, independently per field.
Missing fields, invalid/unknown values and bridge failures leave those controls disabled.
Writes are allowlisted, subscription-gated and checked against vehicle readback (up to 2 seconds).
Only bridge-busy rejection before execution is retried; ambiguous writes are not repeated.
Logging tag: ApolloTest (field, old value, requested value, actual value and failures).
No experimental choice is persisted/replayed on startup. Startup/master entitlement behavior is unchanged.

Install the newly built app and restart the bridge/device before testing: new bridge commands
are needed. An old bridge rejects them and the test controls stay unavailable.
Hardware functionality still requires testing; a CAN readback is not proof of actual ADAS operation.
