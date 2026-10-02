# Screen transfer button — Binder implementation (2026-09-23)

## Failure evidence
`not support log-2026-09-23_12-47-50.txt`: key 3115 reached the original MultiDisplayImpl at 12:47:27–40. At 12:47:46 process 5433 (SystemService) died with SIGSEGV, fault address 0x94, anonymous native frame. The ScreenSwap injector reported connection closed. Service restarted as PID 6610. This establishes failure during attachment, but the short native trace does not establish the exact native instruction responsible.

## Replacement
No injection into SystemService. Removed OemScreenSwapScript and its host startup. ScreenSwapControl uses the existing single QingganStarMapping interceptor (shared registration with star/voice/media; key 3115). It consumes DOWN/UP/SHORT for eligible transfers, executes only SHORT, and queues work outside the Binder callback. Factory key processing remains for ineligible apps or disabled/expired feature.

Every 500ms while enabled/subscribed, the authenticated bridge prepares the current two-display plan. The callback uses a bounded 1200ms eligibility lease. Execution rechecks identities, display, foreground plan, setting, subscription, screen-lift state and 1500ms debounce. It never captures a fixed first-app identity. A changed plan is rejected instead of moving a different app.

The bridge calls Android IActivityTaskManager.getAllStackInfos/moveStackToDisplay via reflection in our own root process. No fake OEM animation registration or stale OEM animation state is created. User apps/our own app can swap with each other or transfer to Home. The passenger factory SecondMainActivity is explicitly treated as Home even if Android marks it as standard activity. Home never moves. Unknown system apps, cameras, recents and multiwindow stay on the OEM route. This conservative version does not add mixed user-app/system-app transfer.

Each Binder move is read back. On failure after a possible mutation, restore only matching pinned stacks and verify; never replay the original swap after partial work. Logs: VI/ScreenSwap plan, key consume/execute, MOVED, ROLLBACK and error details. Polling ends/releases only this listener when disabled/expired; star/voice/media remain registered.

## Validation / deployment
Ten JVM tests cover both directions, two apps, passenger Home typed as standard, changing apps, exclusions and key ownership throughout a press. Targeted Kotlin compilation checks modified bridge/controller/key mapping against local compiled project dependencies. These are not vehicle integration tests.

Publish/install the updated installer to replace both app and root bridge, then reboot once to remove the old injector and restart the previously crashed OEM service. Test Apple Music -> passenger Home, Neshan -> center/return, two apps, physical button followed by three-finger transfers, disabled setting and OEM app pairs. Collect full logcat if needed. No new framework patch is introduced by this change.

## Follow-up log 13:23 (2026-09-23)
The root bridge failed before planning: Settings.Global through a synthetic app_process Context raised `Unable to find app for caller ... when getting content provider settings`. The log repeatedly shows consume=false, so that session did not validate the new Binder transfer at all. Removed synthetic Context entirely. Settings are read via the standard settings CLI (2s bounded cache, fresh on execute); application flags via IPackageManager Binder, not Context.

READY and EMPTY both consume the button. EMPTY is rechecked at execution and produces a Toast explicitly associated with display 0. Missing displays/unsupported windows are not treated as empty. Single-app transfer launches the pinned source Home component after verified movement. Two user apps swap; both Home screens display the no-app message. Unknown system-app restrictions remain. Visible stack metadata is now logged to distinguish OEM Home representations on hardware.

## Follow-up 13:44
Transfer itself succeeds (MOVED), but Home launch used unsupported `--activity-new-task`. ActivityManagerShellCommand rejected it, and our rollback restored the app. Replaced with numeric Intent flag `-f 0x10000000` (FLAG_ACTIVITY_NEW_TASK). The factory runStartActivity implementation also adds this flag. No change to rollback for actual failures. Validate a single app in both directions after updating the bridge.

## Bilateral transfer visual gap
Home is launched only for one-app plans. Two-app plans previously ran two full snapshots and package metadata reads between moves. Prevalidate both identities, send the two pinned moves consecutively, then verify both results together. Rollback still handles partially applied moves. This reduces the exposed Home interval but does not make the two Binder calls an atomic compositor transaction; visual confirmation remains a hardware test.

## Excluded foreground applications as destinations
A non-movable foreground (e.g. Vehicle) no longer blocks the other display's user app. Only movable foreground stacks enter the transfer list; excluded stacks are never moved or closed. One candidate transfers onto the opposite display and opens Home only on its source. Two candidates swap. No candidates on two present displays yields EMPTY (central message); a missing display still does not qualify as EMPTY. This supersedes the earlier mixed user/system-app restriction.

## Android 9 H97A support (2026-09-27)

Verified factory ActivityManagerService exposes getAllStackInfos() and moveStackToDisplay(II); ActivityStackSupervisor supplies configuration/topActivity/visible/displayId/stackId. AndroidSource maps physical key 287 to 3115. Select IActivityManager via ActivityManager.getService() on API 28; keep IActivityTaskManager on API 30. The capability gate is separate from OEM launcher hooks. Existing plan/debounce/subscription/source Home/rollback behavior remains shared. 23 JVM tests cover API selection, key codec, press handling and transfer planning. Deploy app and bridge together via installer; hardware validation pending.

## H97A app crash after successful transfer

app switch log-2026-09-27_16-05-33: at 16:05:27.714 untrusted_app lookup of input_method_ext is denied; Neshan crashes in InputMethodManager.getInstance during ViewRootImpl.moveWindowToDisplay. MOVED/DONE only confirmed stack movement, not app survival. Vendor CIL permits untrusted_app_27 but not modern untrusted_app to find this service.

install-bridge.sh now recognizes the exact factory plat_service_contexts SHA256 5857c40c8bdc6e325149a5c92be0c5b9ff93796ad449a5550b224d05e5594668 on API28. It backs up and changes only input_method_ext to the existing input_method_service label (the standard primary IME app API classification). Patched hash c9a724ddd2486f025260b80113fd24ef73956f5c6e6bdf54eab52486a48149c7. Compiled policy and enforcing mode remain unchanged; system_server already has add/find for this label. No permission to other OEM services is added. Unknown files are skipped and logged. Reboot is required because servicemanager loads these labels at startup.

Backup location is printed as VOYAH_PASSENGER_IME_ACCESS. To revert, restore that verified original file to /system/etc/selinux/plat_service_contexts using root while system is writable, preserve root:root mode0644, run restorecon and reboot. Verify original/patched hashes before restoration. Static transform/hash/idempotency checks passed; hardware validation pending.

## Supersedes the service-context replacement above (16:17 failure)
The actual install rejected rename over plat_service_contexts. Firmware vendor CIL grants su_28_0 service_contexts_file_28_0 read/getattr/open only, unlike system_file. Removed the entire attempted context-file mutation from install-bridge.sh; do not try in-place writes or disable SELinux. Original file was not replaced by the failed mv; the backup remains. API28 transfer gated off to prevent the confirmed modern-app passenger IME crash. Android11 transfer remains enabled. A supported API28 passenger IME access solution is still unresolved.
