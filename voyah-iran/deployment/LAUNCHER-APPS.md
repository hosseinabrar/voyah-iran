# Launcher app list policy

The general OEM app inventory scan/reconciliation is removed.
The original passenger-only getAllApps(1) hook is retained: when the passenger
drawer requests its list, it includes launchable user apps already present in
the central OEM list. It does not scan installed packages or run periodically.
H97A and H97C use LauncherDrawerActivity via LauncherDrawerService.
Other variants retain their OEM app list, even if optional launcher hooks fail.
VehiclePlatform.launcherDrawer caches the variant decision from Android Build identifiers.
Existing navigation-bar, display and passenger-button hooks are independent and retained.
The custom drawer queries installed user apps only when displaying its own list.

After updating an existing installation, reboot the head unit so previously injected
launcher reconciliation hooks are unloaded.
Validation: LauncherFallbackPolicyTest (3 passing JVM tests).
