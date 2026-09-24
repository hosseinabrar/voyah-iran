# Neshan global compatibility configuration

Endpoint: `http://voyah-iran.ir/configs/neshan.json`.
Publisher source: sibling `voyah-iran-subscription-server/configs/neshan.source.json`.
Upload artifact: sibling `voyah-iran-subscription-server/deployment/configs/neshan.json`.

## Contract

One RSA/SHA-256 signed envelope contains the complete version catalog. `payload` is base64
of the exact UTF-8 JSON bytes; `signature` is base64 PKCS#1 v1.5 SHA-256 signature over those bytes.
The purpose, package, schema and monotonically increasing revision are checked before use.
The embedded public key is the existing deployment key. Private material stays in server `secrets/`.
No downloaded script, shell command, path or executable is interpreted.

Each profile describes APK SHA256, versionCode/name, typed route update/reset class and method
identifiers, six Step getter names, free-driving labels and scoped accessibility resource IDs.
Schema 1 implements the tested `(int,double,List)` progress and `(List)` reset contract.
The fixed engine keeps original method results, ASCII-safe Unicode transport, source age expiry
and heartbeat cleanup. Cluster enum mapping/transliteration remain generic client functions.

Initial profile is only 14.12.0.3 / 70789. Unknown APKs are not guessed. To support another version,
analyze and test its contract, append its profile, increase revision, sign and upload the same file.
Never reuse a revision with different content. New method signatures or behaviors may require
a new engine/schema and app release.

## Startup and caching

Application creation, boot/package-replacement receiver and MainActivity start request an
asynchronous refresh (in-flight deduplication and 15-second burst debounce). Application also
reselects a cached profile when Neshan is installed/replaced/removed. APK hashing happens off the
UI thread and is cached by source path, size, mtime and version; never once per navigation tick.

Cache is `files/neshan-config.json`, HTTP validators in `.http`. Only validators bound to a
verified cached payload are sent. Prefer `If-None-Match`; otherwise `If-Modified-Since`.
304 transfers no body and does not rewrite cache. 200 is size-bounded to 512 KiB, verified and
validated before fsync + atomic rename. Failed HTTP, invalid signature/schema or rollback retain
the previous cache. Startup failures retry after 30, 120 and 300 seconds. Offline use has no
artificial expiry, but first use requires a successful download. HTTP can be blocked or delayed;
the signature provides integrity/authenticity, not availability or confidentiality.

Bridge checks the fixed app-cache path every five seconds and only verifies on file change.
It independently verifies signatures/revisions, maintains its own cache under its private Neshan
directory, detaches on a new catalog and selects by actual installed APK hash before attaching.
No first-version profile remains in production Android sources or the embedded JavaScript.
The Windows payload verifier now checks the generic config-engine marker.

## Verification

`NeshanConfigTest`: signature/schema/purpose checks, rollback/reused-revision rejection,
body limit, real HTTP 200/304/503, unchanged cache on invalid update, and publication signature.
Standalone published-file check: `-Dneshan.published=<path to deployment/configs/neshan.json>`.
`deployment/neshan/direct-navigation.test.js`: actual published source profile drives original
route hook regression tests, Unicode transport, lifecycle cleanup and embedded-script parity.

Hardware acceptance: install complete updated Windows bundle; with no cache, enable Neshan
options and check `VI/NeshanConfig` download and bridge acceptance. Verify navigation/free-driving.
Reopen app to check unchanged/304, disconnect internet and repeat using cache. After publishing
a higher revision, verify bridge reload without reboot. APK 70794 must remain unsupported until
its own profile is published. Device-side app-cache access and end-to-end UI need hardware testing.
