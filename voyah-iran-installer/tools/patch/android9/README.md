# Android 9 framework port

This is a separate port, not an Android 11 signature bypass. Supported source:

* API 28 H97A / f0506h, supplied 2023 China dump.
* stripped services.jar SHA-256: `1c16f5976318fadcf7add92c518a93896f99e2a78f742b7347d8539103264882`
* arm64 services.vdex SHA-256: `a9303263150cc12ee9e941c9d295a3bf0aa8282284b9c5b15adc6efd59c41c06`
* reconstructed standard DEX: `90d3c5c8fb3d7ee0e0ac11ac0fc6b996351e0fc3d02b24f2f1a12e79ae3f6138`

The installer pulls and verifies VDEX, reconstructs its single compact DEX, restores
quick field/method references and removed check-casts, then writes an ordinary DEX.
Only reviewed VDEX/DEX pairs are accepted. The converted DEX hash is checked as well.
1,569 optimized opcodes and 1,068 quickening indices were processed. All 4,713
original classes passed a complete disassemble/assemble/disassemble comparison.

Additional reviewed H97A source (2026-10-14): OTA7.0_1030-37, SDK 28,
supplied dump `voyah h97a 20260106`. The stripped JAR is unchanged. VDEX SHA-256:
`664c053504beb67f977132419b2a8080b207965f958530b9366d84758ead7299`;
reconstructed DEX SHA-256:
`d0b537189af5389b272bebbb444f85af8d7105ff9929f923d5415979e893b931`.
This revision has 4,714 original classes (4,718 with the four helpers). All seven
patched original methods match the preceding source. Full output comparison retains
the OTA's added audio class and changed activity/input/audio/transition methods.
The old 4,713/4,717 source and reinstall paths remain accepted. This is host-side
verification; vehicle boot and interaction testing is still required.

## Hooks

* `com.android.server.InputMethodManagerService`: retains the existing enabled,
  installed IME override; otherwise uses the original Qinggan fallback. Android 9
  uses HashMap and `com.android.internal.inputmethod.InputMethodUtils`.
* `com.android.server.am.ActivityRecord`: applies safe bounds before the stock
  override-configuration calculation; returns landscape for eligible apps.
* `com.android.server.wm.AppWindowToken`: orientation hooks retain the -2 sentinel;
  suppresses the stock letterbox for policy-controlled apps so its blank surfaces
  cannot cover the vehicle's bars. Existing letterboxes follow the stock hide path.
* `WindowState`: clips app frames through the old two-Rect layout method. IME and
  system windows are excluded. No Android 11 WindowFrames/ATMS APIs are referenced.

Displays 0 and 1 use their own width/height, with 145 px left and 50 px top margins.
User apps and Voyah Iran are included; launcher, system apps, cluster and multiwindow
are excluded. One Settings observer handles toggling; no polling/process injection.
On disable, previously constrained bounds are cleared before stock recalculation.
This capability is separate from SDK-30-only launcher/screen-switch hooks.

## Verification and build

`Build-Helper.ps1` compiles against signature-only stubs, runs the 14 policy checks,
and writes exactly four helper classes. Stubs/tests must never enter services.dex.
`Verify-Links.py` checks generated server member references against decoded firmware,
including access levels (93 references in the initial build).

`PrepareFixtures.java` is developer-only: compile together with Android9Patch.java,
using smali-runtime.jar as the classpath, then run with original sequential-label
smali, this directory and a new work directory. Review all generated method changes.
It is not shipped in the installer and is never used to accept unknown firmware.

The installer source-launches Android9Patch.java with four arguments: this directory,
input JAR, VDEX path (or `none` on reinstall), and a new job directory. The patcher
requires exact original/patched methods and helpers, verifies all other classes and
methods unchanged, preserves other JAR entries, and emits `VOYAH_PATCH_VERIFIED` only
after verification. Reinstall verifies and copies an already-patched JAR unchanged.

Initial combined output SHA-256:
`eef3ea537b062110fd83897d5d532fa1d9127d67f578a8940d5130165a803128`.
The original and already-patched paths both passed local verification; installer
102 checks and Android release Kotlin compilation passed. This does **not** replace
the first hardware boot/IME persistence/display/touch test.

## Backup / recovery

### Bottom-area correction, 2026-09-27

Hardware dumps showed real display 1920x720 but task-parent/application bounds
ending at y=608. The original helper copied that shortened parent, retaining a
112px black band. Bounds now come from ActivityDisplay itself, and the activity
configuration is corrected after OEM TaskRecord decor calculations. Window frame
handling restores only edges coinciding with the bottom decor boundary, retains
earlier-ending frames, and skips restoration while that display's IME is visible.
Left=145 and top=50 remain unchanged; system apps, multiwindow and cluster remain
excluded. There are 16 policy/configuration checks and 4 bottom-frame checks.

`previous-v1/` contains exact prior helper/method fixtures, shipped in the payload.
An exact v1 match upgrades through reassembly and full class verification;
unknown/mixed helpers are rejected. A current match remains an unchanged reinstall.
The original and v1 paths must both pass before publication. Test on hardware with
the option on/off, keyboard open/closed, and both application displays.

Local verification passed for fresh reconstruction, exact v1 upgrade, and unchanged
reinstall of v2; all unrelated classes passed comparison. 116 server member links
were checked against the supplied firmware. Hardware validation remains pending.

`manage-dynamic-framework.sh` receives the VDEX hash as an optional fifth argument.
Before replacement it verifies the live code hash and all backed-up preopt files.
For stripped JARs the backup directory is keyed by **both** JAR and VDEX hashes:
`/data/local/tmp/voyahiran-framework-backups/<original>-<vdex>`.
It preserves services.jar and existing arm/arm64 ART/ODEX/VDEX files. Failure during
replacement restores them. Recovery uses the same script with `restore` instead of
`install`, the original/patched hashes and the same fifth VDEX hash. Keep USB/ADB and
the installer report available for the first hardware test. No SELinux policy change.

## Third-party runtime

`smali-runtime.jar` contains the unchanged Smali 3.0.9 / dexlib2 / baksmali classes
and their supporting classes extracted from the existing local JADX distribution.
No JADX UI or decompiler plugins are included. `runtime-provenance.json` records the
source/runtime digests and included prefixes; `Build-Runtime.py` reproduces the JAR
from that exact source archive. No tools are downloaded during installation.

Smali notices are in SMALI-LICENSE.txt. Guava, Error Prone, J2ObjC and JCommander use
Apache-2.0 (GUAVA-LICENSE.txt); ANTLR and JSR305 notices are included separately.
Only Checker Framework annotations are included (MIT, see CHECKER-LICENSE.txt).
The VDEX 019 quickening-table and instruction restoration implementation is adapted
from Anestis Bechtsoudis's vdexExtractor, Apache-2.0; see VDEX-LICENSE.txt and
https://github.com/anestisb/vdexExtractor/tree/master/src/vdex .

## Real display height correction (2026-09-27)

Hardware dumps still showed [145,50][1920,608] after the first bottom fix.
Firmware ActivityDisplay.updateBounds() itself uses Display.getSize(), so using
ActivityDisplay bounds also inherits the 112px OEM decor exclusion. The helper
now uses that display's Display.getRealSize(); only app bounds are changed, not
the global display configuration or OEM windows. Unit coverage deliberately sets
both task and ActivityDisplay bounds to 608 while real height remains 720.
Exact previous-v1 and previous-v2 fixtures allow upgrading both released patches;
unknown modifications are still rejected. Hardware validation is pending.
