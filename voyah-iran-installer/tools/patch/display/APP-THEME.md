# Installed-app default theme

## Installed-app size

Per-package overrides use `voyah_iran_app_density_packages`, a sorted
`package=120;other.package=200` map (`-` for empty). Valid presets only are loaded
into an immutable map during setting changes, so activity configuration needs only
a map lookup. A missing entry follows the current global choice; a stored entry
remains fixed across global changes and reboot. The authenticated bridge command
`app-density-package PACKAGE inherit|120|140|160|180|200` serializes updates and waits
for a matching `voyah_iran_app_density_packages_ack`, rolling back on failure.
The permissions page displays the effective value, with a separate inheritance
switch; the selector is hidden while inheritance is on. Picking a size explicitly creates an override, even if it currently equals
the global value. Enabling inheritance removes that package's entry only.
Exact global-density-only upgrade fixtures are `display/legacy-global-density`
and `android9/previous-v5`.

`voyah_iran_app_density` stores one of 120, 140, 160 (default), 180, 200.
Both framework helpers apply density and recompute logical screen dimensions for
user-installed activities on displays 0 and 1, excluding Voyah Iran itself and system/updated-system apps. Physical safe-area
bounds remain unchanged. Density is independent of safe-area enablement; OEM apps,
the keyboard and system bars keep their original density. Choices take effect via
configuration updates and persist across boot. Apps may recreate their activities.
The authenticated `app-density` bridge command waits for the `_ack` setting and
restores the previous value if an older framework does not acknowledge the change.
Full installer installation and reboot are required. Exact pre-density upgrade
fixtures are `display/legacy-before-density` and `android9/previous-v4`.

On hardware, try all five sizes in Neshan and Apple Music on both displays, return
to normal, then repeat with safe area off and after reboot. Check app controls,
coordinate-based interactions and that the vehicle bars retain their size.

`voyah_iran_app_theme` (Settings.Global): `dark`, `light`, `vehicle` (default).
The framework acknowledges applied selection through `voyah_iran_app_theme_ack`.
The authenticated app bridge rolls the selection back if the installed framework
does not acknowledge it. Publish/install the full installer and reboot; APK-only
updates cannot install this framework capability.

Both display helpers override only UI_MODE_NIGHT_MASK (0x30) in the activity
configuration, separately from safe-area enablement. Existing user-app eligibility
and display 0/1 restrictions remain; OEM apps, system windows, keyboard, cluster,
and multiwindow are excluded. This is a resource configuration default, not Android
force-dark, image recoloring, or modification of an app's theme preference. An app
must implement following the system theme for this choice to affect its UI.

Verified in H97X Setting-release-signed.apk, ThemeModels and ThemeUtils:

| Vehicle theme | OEM theme ID | persist.sys.qg.currenttheme | App default |
| --- | --- | --- | --- |
| Classic (theme_standard) | 2 | default | dark |
| Modern / Minimalist (theme_comfortable) | 1 | default | dark |
| Speedy Dash (theme_chiyue) | 4 | simple | light |

The property is checked in-process every two seconds; no shell subprocess, CAN
traffic, or app enumeration is involved. Reconfiguration occurs only on an actual
effective night-mode change or an explicit preference change. Unrecognized vehicle
themes default to dark. State is reapplied when system_server starts and survives
Voyah Iran UI closure. The Android 9 path also updates the current override directly
because stock updateOverrideConfiguration exits early when bounds haven't changed.

Pre-theme helpers are bundled as exact upgrade fixtures: display/legacy-before-theme
and android9/previous-v3. Older accepted versions remain supported. Unknown framework
changes remain rejected. Tests cover theme mapping, light/dark transitions, preserved
uiMode type bits, OEM exclusions, passenger display, and safe-area independence.

Hardware validation: switch all three choices with Neshan/Apple Music set to follow
system; change vehicle theme while in vehicle mode; check both displays; repeat with
safe-area off and after reboot. Finally choose a manual theme inside the app and
confirm it takes precedence. Framework configuration changes may recreate activities.

The user-app-only revision retains exact prior fixtures in display/legacy-before-user-density and android9/previous-v6. Theme and safe-area eligibility are unchanged; only density excludes Voyah Iran.
