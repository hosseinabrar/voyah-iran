# Passenger navigation bar repair — 2026-09-23

Scope: bridge launcher injection only; center behavior and services.jar bounds unchanged.

OEM source: LauncherModel.handleUpdateSecondNavigationBar compares supplied activity literally to AppUtils.getTopAppInfo(context,1,4); NavigationBarController.dismiss can also run directly (e.g. display movement). The former hook alone does not cover every hide path, and previous refresh skipped an unchanged component without checking real bar geometry.

Repair:
- Prefer OEM passenger top-app API, retain ActivityManager fallback.
- Pass empty activity to the OEM handler, retaining its foreground-package check.
- Guard direct dismiss only for screen 1, acknowledged enabled policy and a current eligible foreground app. Preserve release, screen moves, OEM apps and all center behavior.
- Once per second inspect actual attachment/x position even with unchanged app. Restore only if missing/offscreen; do not interrupt a running animation or display move.
- Keep original hide intent for disabling the checkbox.

29 Node checks exercise the actual injected source, including omitted passenger task, direct hide, stale hidden frame, display moves, release, OEM camera, disabled policy and center isolation. Hardware still needs validation. Publish/install bridge update; an APK-only update does not replace the running root bridge. Existing framework patch need not change.
Logs: VI_NAV_PASSENGER_KEEP, VI_NAV_PASSENGER_RECOVER, VI_NAV_PASSENGER_TOP_ERROR and VI_NAV_PASSENGER_GUARD_ERROR/UNAVAILABLE through VI/Navigation.

## Passenger app drawer (2026-09-23)
Default-on independent setting: `voyah_iran_passenger_apps`; bounded bridge operation `passenger-apps enable|disable`.
Hooks NavigationBarSecond.initAllAppShow temporarily override visibility while preserving the OEM boolean; checkbox changes restore native layout within one second. Only its all-app button opens BaseApplication.expandAllApp(1), which uses AppUtils.openAllApp and the factory passenger AllAppActivity/display-1 launch path.
AllAppDataManager.getAllApps(1) returns a copy of the original list plus launchable user apps from the center list, deduplicated by package. Screen 0 and native lists remain untouched. No APK modification or vehicle-model spoofing.
Test: `node tools/tests/passenger-drawer-test.cjs`; Kotlin release compilation passed. Hardware validation pending: icon visibility, launching Myket/music directly on display 1, and toggle restoration. Install with the updated installer/bridge; an APK-only update does not refresh the running bridge script. Log prefix: VI_PASSENGER_APPS.

## Independent passenger button (2026-09-24; supersedes initial button hooks)
PassengerDrawerScript is appended independently to the launcher injector. It enumerates attached WindowManagerGlobal.mViews on the UI thread once per second (no heap scan), restricted to display 1, narrow navigation windows with passenger-navigation class or home resource evidence. It creates one tagged ImageButton in a free vertical slot of the FrameLayout, draws its own grid icon, and launches the factory passenger AllAppActivity with explicit display 1. Existing native all-app icons are hidden. Disabled means remove our button AND hide the native icon, per user request. Center windows are not modified. No isShowScreenAllApp/initAllAppShow/onClick dependencies remain. List enrichment is independent, and still uses OEM AllAppDataManager. Thus this is independent of native visibility conditions, not a promise of compatibility with every possible launcher/list implementation.
Logs: VI_PASSENGER_APPS_UI shown/waiting-passenger-bar/no-free-slot/unsupported-container plus CLICK_ERROR. If no safe space is found the button is not drawn over existing controls. UI repair tests use the actual update function: missing/native hidden/native reappearing, no duplicates, checkbox off, no safe slot. Physical layout and unknown tester firmware need vehicle testing. Requires updated installer/root bridge, no APK patch or server config change.

## Button construction failure (tester log 2026-09-24 15:26)
VI_PASSENGER_APPS_UI_ERROR identifies setContentDescription(CharSequence) argument mismatch, while UI disabled confirms checkbox delivery. Use explicit java.lang.String wrappers for content description and tag/findViewWithTag(Object), and cast generic findViewWithTag result to View. Constructor regression test now executes actual makeButton with Java argument checks rather than stubbing that function. Hardware confirmation still pending; update root bridge through installer.
