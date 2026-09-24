# Current status (2026-09-21)

Supersedes all earlier screen/notification proposals below: direct in-process model hook is implemented; see [neshan-direct-data-findings.md](neshan-direct-data-findings.md). No screenshot/notification/accessibility data publishing remains. Accessibility is only used for entering free driving. Full installer update is required for the new bridge. Real foreground/background navigation must be tested; no hardware success claimed.

# Current architecture: no notification dependency

User confirmed that the vehicle has no usable notification UI. Do not base Neshan navigation integration on notification availability. As of 2026-09-20, NeshanClusterListener was removed from the manifest and source. NavigationPreferences no longer requests notification access or rebinds a notification listener. NeshanDrivingService owns NeshanClusterPublisher, which publishes fresh accessibility snapshots every second independently of notifications. It respects subscription and checkbox state and clears stale data. Existing bridge watchdog remains the process-death fallback.

Current limitation: visible upcoming-turn panel only. Background navigation needs a future direct Neshan data integration; do not assume notification fallback. Music-session access is a separate feature and was not changed. The unused legacy cluster-navigation-access bridge command is retained for compatibility with older APKs, not called by this APK.

Validation: compileDebugKotlin. Hardware testing remains necessary. Updating the APK is sufficient on a previously provisioned vehicle; no new bridge operation is required.

---

## Historical research (superseded where it refers to notification input)

# Neshan cluster experiment

Checkbox: «نمایش اطلاعات ناوبری در کلاستر راننده», under the selected Neshan navigator. Off by default. Independent notification listener; does not require enabling steering media controls or Raha. Installer must update APK **and bridge**.

The listener accepts only Neshan, navigation category or known guidance ID1874524. Generic foreground service ID504311 is ignored. Title/text supply the real instruction, without inventing route information. Current version's Android Auto notification may not be emitted in standalone mode: missing notifications mean waiting, not fabricated guidance. Other apps' notification contents are never consumed.

Every second it forwards a live snapshot. Polling does not refresh its source timestamp. After15 seconds without a source update the instruction expires; removal clears it. Snapshot recovery respects original post time. OEM service may deduplicate identical data. This does not generate a more accurate distance than Neshan publishes (known notification uses distance buckets).

At the user's explicit request, candidate TBT mappings are enabled for testing: straight1, left2, right3, slight left4, slight right5, U-turn6, sharp right7, sharp left8. These are **hypotheses from the generic OEM enum**, not proven Voyah TBT mappings. Source icon names come from the installed Neshan resources (common_ic / routing_module_ic), not fixed resource integers. Compound lane icons and unknown icons are not guessed. A recognized icon plus leading distance (Persian/Arabic/Latin digits, m/km) uses GUIDING with tbtIconId/nextDistance/turnRoadName. Otherwise ROAM displays original instruction text, without guessing distance or icon.

Root bridge loads installed VehicleSetting cluster SDK and calls qg.cluster.service -> IInstrumentClusterManagerService.updateNaviInfo. It does not inject into Neshan or transmit raw CAN frames. A3.5-second heartbeat timeout and subscription expiry trigger MAN_STOP with empty road. Clear can run after subscription expiration. A separate worker retries failed cleanup; it does not clear a cluster it has never attempted to write.

Logs: `NeshanCluster` shows notification ID/category/channel, icon resource name, candidateTbtIcon and meters. `NeshanClusterBridge` shows Binder dispatch/failures. Binder completion is not a visual acknowledgement.

Hardware tests: enable, start a real route, left/right/straight turns, Persian street display, foreground/background, return from route, cancel route, checkbox off, another navigator selected, subscription expiry, app force-stop and bridge restart. Check for conflict with stock navigation. No claim of end-to-end hardware success yet.

## 2026-09-20 hardware-log correction

The supplied 22:21 log contained only generic service notification 504311 (rejected), and the former speed-dependent map button timed out after 20 seconds. Deprecation warnings in the successful release build do not explain either failure.

Free-driving now uses bottomNavigationViewMain > exact Persian label «سایر» > «حالت رانندگی آزاد». Clicks use an accessible clickable ancestor, never screen coordinates. The menu is opened at most once per foreground visit. An active upcoming-turn panel prevents switching an existing route to free driving. Successful dispatch consumes the attempt so a manual exit is respected. GPS prompts are not automatically accepted.

Cluster input now also reads the visible upcoming-step fields verified in Neshan 14.12 APK: streetNameNormal/Compact, distanceToStartNormal/Compact, maneuverIconNormal/Compact. A fresh visible snapshot is preferred over notifications and expires after 2.5 seconds; a missing panel clears it immediately. This fallback is foreground-only. Background operation remains dependent on a valid guidance notification; it is not claimed fixed by this change. Drawable-only arrows cannot be read through accessibility. A readable maneuver description supplies an experimental left/right/straight/U-turn candidate; otherwise the actual road and distance are forwarded as text with no invented arrow.

Both cluster-only and driving-only settings now provision accessibility. New logs: NeshanClusterScreen (fields), NeshanUi (bounded visible tree every 15 seconds when no panel is found), NeshanDriving (More/menu entry), NeshanCluster (source and bridge response). No bridge or installer change was needed for this correction; update the APK on the previously provisioned car. CompileDebugKotlin and 10 isolated parser/freshness tests passed; no hardware verification performed.

## 23:07 test corrections

Observed: More tab is exposed as itemTextView=سایر without bottomNavigationViewMain. Menu entry is ورود به حالت رانندگی. Support that exact label and a scoped itemTextView fallback; retain the older label for the researched APK version.

Observed: road=علامه طباطبایی جنوبی, distance=90 متر reached the app and bridge successfully; direction=null produced ROAM text, so no arrow was requested. Tester saw only ASCII digits/hyphen. Likely cluster lacks Persian glyphs (not a proven encoding diagnosis). Publisher now transliterates to ASCII, including m/km; original Persian remains in diagnostic logs. Generic Persian transliteration is approximate.

Arrow experiment: include non-important accessibility nodes to locate maneuverIconNormal/Compact bounds, take an Android 11 accessibility screenshot, analyze only that rectangle against alpha silhouettes loaded from the installed Neshan common_ic resources. No screenshots saved or uploaded. Require score >=0.60 and >=0.07 lead over runner-up; ambiguous/unsupported arrows remain text-only. A supplied-screen silhouette fixture chose left (~0.87 overlap), ahead of other tested turns. Async results are generation/foreground/age guarded. Known OEM TBT IDs remain experimental and need vehicle validation.

12 parser, freshness, transliteration and silhouette tests passed. Only app/accessibility metadata changed, not bridge protocol. Update APK (service must reload the new screenshot capability); no new root installation needed on a previously provisioned vehicle. Logs NeshanArrow include node bounds, screenshot failure code, chosen shape and score; NeshanClusterPayload records actual Latin command; NeshanClusterArrow records structured frame. Still foreground-only, no notifications.

## Stationary navigation lifecycle (2026-09-23)
Observed own active=false after source age exceeded 5s (13:59:18.246 and 13:59:37.520), not proof of cluster duplicate rejection. Config revision 2 adds routeState to verified APK a5e32b...: BaseApplication.N (volatile ki0.c singleton), H() checks active state bl0.d.a, A() returns engine; o() clears engine and sets null. Both active flag and non-null engine are required. These are read-only getters; no singleton creation or heap scan.

Every 200ms, while live navigation is confirmed, DATA carries ageMs=0 (fresh validation lease) and sourceAgeMs for unchanged data age. Manager's existing 5s transport watchdog, process-death handling, parent heartbeat, subscription gating, route reset and 1s cluster refresh remain. Inactive/missing/read-error state clears cached guidance; reactivation needs a fresh step. Legacy profiles without routeState keep their age limit. No foreground requirement: background navigation continues.

Publish the new app/root bridge AND upload subscription-server/deployment/configs/neshan.json to /var/www/html/configs/neshan.json. The APK contains the generic engine, not version-specific class names. No server image rebuild required for the static JSON. Tests simulate 10h without step updates, navigation exit, engine/singleton removal, read failure, reset and parent loss. Actual-car validation still needed, including leaving navigation while keeping Neshan open and running background guidance.

## Route-state read failure (2026-09-23, nav 6 log)
Log reports navigation-state-error repeatedly with ageMs=-1, despite app verifying route guidance at 18:30:00.685. The catch previously discarded every valid step; it omitted the exception, so exact runtime cause cannot be proven from this log.
Singleton access now uses a cached retained java.lang.reflect.Field rather than Frida property lookup (avoids obfuscated field/method collisions); getters use explicit zero-argument overloads. Read failures retain only fresh guidance under the existing 5s expiry and log VI_NESHAN_STATE_ERROR (forwarded as NeshanDirectState), without falsely granting an active-route lease. Confirmed inactive/null engine still clears immediately. Test: node tools/tests/neshan-state-test.cjs. Hardware validation remains required. Config revision 2 is unchanged; update bridge through installer.

## Explicit app exit (2026-09-24)
The retained navigation singleton/process can outlive the last Activity. Observe Instrumentation lifecycle calls in the Neshan process: a finishing task-root Activity (excluding configuration recreation) latches app-closed and emits CLEAR immediately. Late step updates are ignored until a task-root Activity starts again; reopening requires a fresh step. Ordinary stop/background and child-screen closure do not clear guidance. Existing process-death and route-state checks remain. No config/server change. Lifecycle tests are in tools/tests/neshan-state-test.cjs; actual Back exit needs vehicle verification. Install updated bridge through installer.
