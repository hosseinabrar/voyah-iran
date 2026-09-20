# Scene recovery — 2026-09-20

Evidence: `senario_log-2026-09-20_18-21-08.txt` correctly recognizes carWash and
camp. Frida prints `script-loaded arch=arm`, then VehicleSetting crashes with
SIGSEGV at address 0x94 for both requests. The failure precedes scene execution.
No evidence in this log implicates recognition or DPT.

ACTIVATE_SCENE / DEACTIVATE_SCENE now route through OemSceneActions and are removed
from the accepted action set of OemEnergySceneActions. No scene entry/exit injects
the VehicleSetting process.

* CampMainActivity.parseIntent and RestMode MainActivity.parseIntent require
  boolean `key_auto_play=true`; `src_voice` alone only opens their UI. Both now
  receive autoplay via `am start`, package restricted, user/display 0. Camp uses
  the existing fire theme; rest preserves OEM saved theme/duration. Confirm the
  corresponding OEM scene property within 6 seconds.
* DriveModeIntentService.washCar only displays a disclaimer. Its agree callback
  writes CAR_CLEANING_MODE_SWITCH=1, and VEHICLE_TUNK_CONTROL_SWITCH=1 if trunk
  feedback is not 1. The new bridge uses that sequence and confirms SELF_CAR_WASH_STS.
  `com.qinggan.manualwashmode.state` is a dialog flag, not the active mode! Exit
  writes 0 and confirms feedback before clearing the mode property.
* PowerHoldModeManager.enterPowerHoldMode uses a vehicle Bundle: switch=1,
  time=minutes/30 (15 for unlimited). Direct route preserves engine setting on
  entry; exit clears switch/extender. Bridge property follows confirmed feedback.
  This direct route does not run OEM in-memory listener/analytics/prefs callbacks;
  staged/leave-car behavior and timer must be verified on hardware.
* Party/babyCare use the factory SELECT_MODE service with MODE_NAME and isClick.
  QuickWarm uses the factory scene-engine broadcast. These one-shot presets have
  no persistent acknowledgement in this adapter: return DISPATCHED, not CONFIRMED.
* Global exit reads actual active scenes, irrespective of the spoken scene name.
  Rest/camp exits publish the installed SDK QGBusEvent via the factory Messenger
  protocol (message 3, Bundle object key event). Decompiled QGBusService case 3
  directly forwards this event; no registration is needed for publish-only clients.
  Binding and event send occur on a dedicated HandlerThread using root system
  context. The callback cannot publish after its connection timeout. Confirm OEM
  property off afterward; never kill the OEM scene process to imitate exit.

Factory P, raised-screen, feature and SOC checks remain. No mutation retries.
Other energy/charging parameter actions still using OemEnergySceneActions are
outside this fix, including separate power-keep duration/engine preference actions.

Validation: Kotlin compilation and 3 focused scene entry regression tests pass.
Device-side Binder, Activity startup, QGBus binding and actual hardware behavior
still require vehicle tests. Re-publish and install the full bundle to restart the
privileged bridge. Log tag RahaScene includes active scenes, route, CAN writes,
shell output, and confirmation. No release APK was generated.
