# microG compatibility support

The shared Windows/macOS installation pipeline runs this local verification pass after the existing framework patch, before any framework write. Android 11 (H97X/H97C/H97E) and reconstructed Android 9 (H97A) have distinct reviewed PackageManager templates. Unknown implementations fail before vehicle changes. H97E uses the Android 11 route; no separate H97E ROM fixture is currently available.

Only `PackageManagerService.generatePackageInfo` presentation changes. Official microG packages (`com.google.android.gms`, `com.android.vending`, `com.google.android.gsf`) must have exactly one signer matching SHA-256 `9bd06727e62796c0130eb6dab39b73157451582cbd138e86c468acc395d14165`. Legacy and modern PackageInfo signatures then present the Google certificate. Package installation verification, signature permission enforcement, `checkSignatures` and `hasSigningCertificate` remain untouched. No global spoof permission is introduced. The previously tested device-local helper is accepted as an upgrade source; arbitrary helpers are rejected.

After reboot, the installer removes user 0's `no_modify_accounts` restriction and checks framework signature presentation when official microG is present. The app's root bridge owns Companion installation and permission grants, on boot and on GmsCore install/upgrade broadcasts. It uses a separate worker with bounded retries and no periodic polling. This covers GmsCore installed after the installer has completed, even with the UI closed. It never replaces existing Google services/Play Store or reads account data.

GmsCore itself is not bundled or automatically installed. The Companion APK and provisioning DEX are copied from this single source directory into the app's generated `assets/microg` directory by Gradle; the installer does not carry a second copy of Companion. Cached root assets are SHA-256 checked. Changing either binary requires updating the app's pins and passing its asset test. A current installer framework patch remains a prerequisite; the app never patches live framework files.

## Pinned upstream companion

- Project/source: https://github.com/microg/GmsCore/tree/v0.3.17.252432
- Asset: https://github.com/microg/GmsCore/releases/download/v0.3.17.252432/com.android.vending-84022634.apk
- APK SHA-256: `9b8616011cc8f026bcbbe094df850794abb9d6d9f23214d7a8f8c4d1033ea012`.
- Signed by the official microG certificate above. Bundled license: Apache-2.0 (`LICENSE.txt`).
- Payload manifests verify every bundled artifact on both platforms.

## Rebuilding

`python3 build.py ANDROID_SDK BUILD_TOOLS_VERSION` regenerates `VoyahMicrogCompat.smali` and `provision.dex` with Java 8 bytecode / Android API 28 minimum. Java sources are kept next to both artifacts. No full firmware, account data or credentials are included.

## Validation

Local patch/reassembly/whole-class comparison passed on available X, C and reconstructed Android 9 fixtures. Repeating the patch is byte-identical and reports `already=true`; upgrade from the device-local patch passes. The provisioning DEX was exercised on the attached Android 11 vehicle. Windows and H97E hardware execution still require on-device testing. Installer release artifacts must be rebuilt before distribution.

## Package visibility

On Android 11+, provisioning sets and verifies `package_manager_service/package_query_filtering_enabled=false` through DeviceConfig. This is a global user-requested compatibility choice: all applications may discover installed packages, not only microG. It does not grant access to their private data or bypass component permissions. Android 9 has no corresponding package visibility filter and is skipped. The setting is persistent and does not require per-app overrides. This fixed stock YouTube reporting missing GMS on the test vehicle.
