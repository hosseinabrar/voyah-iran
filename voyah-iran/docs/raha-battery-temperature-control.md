# Battery temperature control — 2026-09-23

User reported activation ineffective (no new battery-specific log provided). The old route still injected Java into VehicleSetting, the path implicated by the display/rear-lock SIGSEGV logs. Replaced with OemEnergyBinder's installed ICanBusService interface, dispatched ahead of OemEnergySceneActions in RahaVehicleBridge. Removed the old injected branch.

OEM evidence: ChargeFragment.onCommonSwitchClick -> CanBusTool.setVehicleStateBack -> CanBusManager.setVehicleState; FragmentChargeBindingImpl maps DRIVER_PREHEAT_SET to on=1/off=0. The new route validates current state 0/1, skips redundant writes, writes the desired state once and polls feedback for up to three seconds. Unknown values fail instead of reporting success. Logs use RahaEnergy. ECU thermal decisions remain untouched; successful state readback means enabled control, not immediate physical warming.

Recognizer now extracts battery target separately from temperature/heating/control words, handles plural and reordered phrases, and retains off/deactivate behavior. Thirteen activation/deactivation examples plus existing regressions covered. Fourteen focused JUnit tests passed and changed bridge Kotlin compiled. Hardware verification remains pending; update the bridge through the installer. No release build produced.
