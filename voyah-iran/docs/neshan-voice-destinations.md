# Voice navigation to dealerships

Implemented 2026-09-22. Opt-in `neshanVoiceNavigation` under the selected Neshan navigator. `NAVIGATE_TO_SAVED_PLACE` carries a stable destination ID in Raha core; coordinates/Android execution stay in the main app. Navigation is subscription-gated. Includes recognizer vocabulary and DPT protection selection.

## Verified coordinates
Coordinates copied from the Google Maps links on the official Soroush Motor dealer pages, not approximate geocoding or a guessed street center.

| ID | Destination | Latitude | Longitude | Source |
|---|---|---|---|---|
| soroush_central | Central service dealership, code 200; Chahardangeh, Ayatollah Saeidi highway, after Mosalla, no. 194 | 35.59486389160156 | 51.29362487792969 | https://soroushmotor.com/تهران-خدمات-مرکزی-سواری/ |
| shahpary | Shahpary dealership, Resalat/Soleimani highway, westbound after Sarsabz | 35.7361246 | 51.4933056 | https://soroushmotor.com/نمایندگی-تهران-بزرگراه-رسالت/ |

Shahpary's own https://shahpary.com/sh/ map link agrees. Waze's separately indexed pin is about 22 m away (35.73607254, 51.493068695); the implementation uses the official dealer-directory pin.

## Execution
- `geo:latitude,longitude`, ACTION_VIEW, explicit Neshan package. No chooser, browser or web API key.
- Ensure existing Neshan accessibility access, then issue one in-process pending request; no boot-persisted navigation request.
- Hide Raha overlay/release transient audio focus before Neshan UI automation.
- Visible, enabled, exact `رانندگی`, then `شروع`; use the existing label/parent click helper, no screen coordinates or image processing.
- Minimum 800 ms between stages. Each stage clicked once, and success only after Neshan route-guidance view IDs become visible after Start.
- Maximum 45 seconds; user leaving Neshan after entry, disabling the option, subscription loss, service interruption or another steering-button press cancels automation. It does not cancel a route already dispatched by Neshan.
- During this explicit route request, consume the free-driving entry for this foreground visit. Ordinary later visits retain the existing free-driving preference.
- Logs: `NeshanRoute` stages and result, `NeshanUi` timeout view inventory, existing `Raha` transcript/action.

## Validation and hardware checklist
Targeted Kotlin compilation of core, vehicle plan, navigation service/executor and dependencies. Tests cover aliases/word orders, ordered clicks, stale-route rejection, timeout, and DPT class selection. No release APK produced.

Hardware tests still required: both places with Neshan initially closed/open, free-driving preference enabled/disabled, slow network/GPS, option off, cancellation by wheel button, and visibility of actual guidance after Start. Exact button labels come from the user's tested UI. No claim of completed vehicle testing.
