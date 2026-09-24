# Display, music and navigation

- New page owns the existing display correction and steering-media controls. Their original preferences and controllers are unchanged.
- `display_media_map` preferences store musicPlayer, navigator, neshanDriving. Music choice is reserved for later functionality; wheel buttons still use the active media session.
- User app picker reuses the permissions app list and icon adapter. OEM player `com.qinggan.media` is added only when installed (OEM SceenLiftConstants.MEDIA_CONCATE).
- Neshan automation is independent of Raha, subscription-gated and restricted to display 0. Selecting another navigator or disabling its checkbox prevents clicks immediately.
- `neshan-access enable` is a bounded authenticated bridge operation. It appends the service without removing other accessibility services. Update **both APK and bridge with the installer**.

## Neshan evidence and behavior

Inspected Neshan 14.12.0.3 (70789), package `org.rajman.neshan.traffic.tehran.navigator`.
`vd0.k0.R` references `drivingActionExtendedFloatingActionButton`, click calls `ee0.i.g0()`, observed through `qh0.r0.J -> ph0.a.b -> MainActivity.K6`; GPS callback selects CAR and state 2 (free driving).
The automation clicks that exact visible, enabled accessibility node. It does not send an invented Intent, patch the APK, click coordinates, or change GPS/permissions automatically.
Driving controls `closeMaterialCardViewDrivingControl` / `closeImageViewDrivingControl` verify entry. An already active driving screen is left alone. No second click is attempted during the same foreground visit, even after a manual exit or failed verification.
A real transition to another launchable application re-arms the next visit. System overlays/keyboard do not re-arm it. Map loading is allowed 20 seconds; click verification 8 seconds. Transient dialogs are never dismissed automatically.

## Hardware verification still required

Accessibility exposure of the identified resource IDs must be tested on the installed Neshan version. Logcat tag `NeshanDriving` records foreground entry/exit, click acceptance, verification and timeout. Missing GPS or location permission may prompt Neshan instead of entering driving mode; resolve those in Neshan first.
Test first launch, manual exit with no reactivation, switching apps and reopening, already navigating, checkbox off, a different default navigator, subscription expiry, and passenger display isolation.
