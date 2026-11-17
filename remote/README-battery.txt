VI Remote battery page — 2026-11-17

Implemented: selectable vehicle/battery tabs, refresh-on-entry, shared two-arrow PNG
refresh/loading icon, Persian labels / ASCII numbers, battery summary, limit slider,
schedule dialog, health and 12V cards. No generated concept numbers in production.
Collector accepts inspected X/C/VT schema hashes; X signal suffixes are normalized.

Source audit (paths relative to parent Voyah directory):
Car Versions/voyah h97x/system/priv-app/VehicleSetting-release-signed/VehicleSetting-release-signed.apk
SHA256: b61e44456bdf15039fcde5c2ab070e640eef37a788d0e11b2520b2a90abf47b6
Decoded source: work/launcher-control-oem-x/smali_classes2/com/qinggan/
- app/vehiclesetting/databinding/FragmentChargeBindingImpl.smali:
  AC state==3 OR DC state==3 OR BMS state==9 disables schedule controls
  (lines 1759-1800, 2558-2580, 2870-2920, 3010-3130, 3580-3615).
- app/vehiclesetting/fragments/charge/ChargeFragment.smali:
  MAX_CHARGE_SOC_SET and HUM_CHARGEMODE writes temporarily disable the
  limit slider and schedule controls; callbacks re-enable after 2500 ms.
  No permanent charging inhibition of the limit slider was located here.
- aapt2 dump xmltree, res/layout/fragment_charge.xml line 246:
  charge target start_value=60, end_value=100, step=5.
- Schedule mode: HUM_CHARGEMODE=2 scheduled, =1 immediate.
These findings do NOT prove the live TBox has the same write API.

Conservative product guards retained from the owner's requested behavior:
block limit while charging or charge complete; block schedule while a gun is connected; stale/unknown
required state and any pending operation disable controls. These are stricter than
what the audited HU bindings alone establish, not a claim of identical OEM policy.

Native dispatch integration — 2026-11-17:
Encrypted battery-command -> agent entitlement/replay/state gates -> JSON stdin
of vns-battery -> installed OEM libvns VnsProxy.vnsSendCanData -> CAN 0x7e.
This is the charging-settings CAN API, not the BCM remote-auth command path.
The general remoteControl handler inspected in cached X only queues 0x58/0xa8;
using it for 0x7e would yield a misleading receipt. No BCM auth is bypassed.
The installed libvns/libhcore pair is preferred. If absent, the installer unpacks
24 vendor/Boost dependencies from the owner-confirmed mpu.zip into the app's
private native/lib directory. No factory file, MCU or radio service is replaced.
A read-only VNS session/symbol probe runs during installation and service start.
Unknown library hashes fail the probe, rather than guessing native ABI layouts.
The fallback package is recreated by native/package-battery-runtime.py from the
verified private extraction under parent work/tbox-x-remote-runtime-20261117.
Both Mac/Windows installers use the same installer core and device scripts.

Native ABI evidence: archived libvns DWARF __can_data__ size24, header size16,
fields id@0 time@4 millisecond@8 channel(uint16)@12 length(uint16)@14 data*@16.
SendCanData disassembly returns the serializer's error code (zero on submission).
X and archived library exports/disassembly inspected; the native transport has
NOT been executed on a vehicle in this implementation turn.
Charging frame positions come from the matching X/C can-in.xml. Hour encoding
1..12 plus half-day follows HU ChargeFragment.twentyFourHourTime (12AM=00).
Changing limit preserves current limit/form/schedule; changing schedule preserves
SOC/current/form. Missing or stale settings block submission instead of defaulting
the AC current to 5A. Noncharging controls in the same CAN frame remain zero.
The charge-settings validity/acceptance and physical effect still need the owner's
vehicle test. Native submission returns accepted, never confirmed. No synthetic
settings are written into the telemetry snapshot. Real BMS/IVI feedback drives UI.
Native command timeout is6s, below the8s phone timeout; no automatic command retry.

Read mappings added from work/tbox-russian-x-c-auth-audit/C/etc/vns/dbc/can-in.xml:
bms_vPackBms -> batteryVoltage; bms_iPack -> batteryCurrent (signed as decoded);
bms_packTempAvg -> batteryTemperature; bms_SOH -> batteryHealth;
bms_maxChargeSOC -> chargeLimit; bcm_ebs_U_BATT -> auxVoltage;
bcm_12VBatterySOC -> auxSOC. OEM XML factor/offset are already applied by decoder.
Unverified power direction / remaining-time units / schedule feedback are NOT
invented or derived from TBOX/IVI request echoes. UI displays dashes until supplied
by a verified collector profile. No extra wake/actuation during these local tests.

Validation:
Go telemetry-agent / collector / crypto tests; JS policy tests; browser encrypted
snapshot rendering, charging/unsupported disabled controls, navigation and bottom
layout. Screenshots use explicit test fixtures, not connected-vehicle measurements.
Rebuilt Linux ARM64 agent/collector, macOS ARM64/AMD64 and Windows AMD64 installers,
server and PWA staging output. Payload checksums verified. No deployment, device
installation or live actuator test performed in this turn.
