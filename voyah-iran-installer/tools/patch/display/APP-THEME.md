# Installed-app default theme

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
