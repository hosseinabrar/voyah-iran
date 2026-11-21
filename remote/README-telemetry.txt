VI Remote telemetry implementation — 2026-11-15

Layout
client/vi-remote: standalone PWA, installation instructions, camera pairing and vehicle page.
server: opaque HTTPS relay; v2 coexists with old v1 test protocol.
TBox/TBox-remote/telemetry-agent: entitlement, 3 phone slots, pairing and encrypted responses.
TBox/TBox-remote/telemetry-collector: OEM receive-only CAN monitor and normalized snapshot.
TBox/TBox-remote/telemetry-installer: shared macOS/Windows installation core.

Build from repository root: python3 remote/build-telemetry.py
Tests: go -C remote test -race ./internal/telemetry ./server ./TBox/TBox-remote/telemetry-agent ./TBox/TBox-remote/telemetry-collector
Install: TBox/TBox-remote/install_mac.command or install_win.cmd.
These are the only supported installers. The obsolete command-test installer was removed.
Uninstall: uninstall_mac.command or uninstall_win.cmd; pairing and messages are retained.
The bundled ADB is reused from ../TBox-antenna/tools within remote/TBox.

Deployment
Build creates server/dist/vi-remote (excludes full-resolution artwork) and remote-gateway.
server/deploy.py uploads only the remote subdomain/service and PWA.
Subscription server must separately be rebuilt/deployed so its signed JWT contains remote_access.
DatabaseSchema.sql contains idempotent ALTER TABLE with DEFAULT FALSE, NOT NULL.
Existing and newly registered subscriptions default false; renewal preserves administrator choice.
No live subscription database, production server or connected vehicle was changed in this implementation turn.

Pairing
Headunit learns remote_access from its verified subscription JWT, hides the remote tab when false,
and reconciles entitlement/expiry to the TBox every 30 seconds, including after startup.
Three device slots; per-phone rename and revoke; short-lived one-time QR invitation.
P-256 ECDH, SHA-256 domain-separated key derivation, AES-256-GCM with direction/route/request AAD.
Relay has no plaintext keys or telemetry. Browser stores nonextractable AES CryptoKey in IndexedDB.
UUID-equivalent 256-bit random request IDs, 15-second timestamp window and persisted replay rejection.
Revoked devices get no decryptable reply; they cannot retrieve subsequent snapshots.
Headunit local management trusts the dedicated vehicle LAN IP, per the physical/local-trust scope.
TBox clock must be correct for TLS, subscription and request expiry.

Socket lifecycle and freshness — 2026-11-17
Standalone paired phone opens /v2/stream/phone only while visible. Agent maintains
/v2/stream/agent with bearer authentication. Payloads retain AES-GCM, route AAD,
entitlement checks and persisted replay protection. Pairing retains HTTP exchange,
which is dispatched to the same agent socket. There is no application ping loop.
Active page requests a snapshot immediately and every5seconds (8s request timeout,
at most two in-flight snapshots). Page changes request the new page's fields.
Phone goes red10seconds after the last valid telemetry reply.20seconds without a
socket reply replaces that socket. Opening timeout8s, reconnect backoff1..5s with
jitter. Hiding/closing the app cancels requests and closes its socket; commands
are never replayed. Server/agent read deadlines20s, writes5s, bounded queues,
message sizes, pending jobs, and global connection count. Replaced agent sockets
and disconnected phone jobs are removed; no persistent per-phone goroutines.
Without active phones the agent may reconnect an idle transport, but no snapshots
or vehicle wake calls occur. Idle agent sockets close after20s as requested.
Collector stores cumulative CANMessages and LastCANAt; zero count or zero frames
in the last5seconds triggers an OEM temporary wake lease before reading again.
No ignition command is sent. No fresh CAN after the bounded wake wait returns
no telemetry; cached values are never stamped as fresh. Only authenticated,
entitled snapshot requests can wake the vehicle. Fields retain original timestamps.
The relay forwards ciphertext and cannot choose which vehicle controls to invoke.
UI updates only changed text/layers, with260ms overlapping cross-fades and image
predecode. Cached values stay visible while disconnected; stale data is gray.

Supported collector profile and limitations
Installer currently accepts the inspected C 2.2.1 / VT 2.2.2 CAN schema SHA256:
3a83470d0b2c2a43b3a4d64b3bf96c21b33e028bffcfc0fa76cf33a0d51d123e
Original X schema 641700045deb8ed8fe1b6b1f006c2be93e650a77625f620616c628c47e19d668 and supplied MPU schema dd69b0cdbdc808756c805a5c6688bb6ec8cad0cf5f4f0334ebac1ab0ec9483b8 are also accepted; X signal suffixes are normalized. Other schemas stop before changing files. Full vehicle-page X enum parity remains a separate audit.
OEM source: usr/etc/remotecontrol/control_atoms.xml and etc/vns/dbc/can-in.xml.
Lock 0=locked/1=unlocked; tailgate 0=open/1=closed; sunroof 0=vent/3=closed/6=open;
windows 2=closed/1=open, confirmed position10=vent; power1=off,2=on, vcu_ready1=ready.
Generic veh_common_stat defaults are not authoritative for these enum polarities.
Pressure unit, battery range signal and fuel-cap enum still need verification; no invented values.
Their UI supports values, but collector intentionally does not populate them yet.
Unknown values show dashes; an absent open-warning is not proof of a closed panel.
Real USB install, headunit LAN reachability, power-manager wake and physical state verification remain pending.
Local builds and mocked browser encryption/rendering are not vehicle acceptance tests.

Artwork
Fonts, battery/fuel icons and bar rasters copied from launcher assets.
Car state layers are tightly cropped PNGs at displayed dimensions; runtime only composes images.
QR/camera decoding and loading animation are functional UI, not generated decorative artwork.
Source art and build scripts remain under client/vi-remote/artwork, excluded from deployment.

Production deployment — 2026-11-17
Supersedes the earlier not-deployed statement above. Subscription service rebuilt
on the production host and restarted, with additive remote_access BOOLEAN NOT NULL
DEFAULT FALSE migration. Existing 113 subscriptions remain disabled (113 total,
0 enabled). No customer's entitlement was enabled. Live synthetic-VIN API test
verified default false, manual enable, preservation during renewal, and manual
disable; synthetic subscription/history removed afterwards.
Remote gateway and PWA deployed at https://remote.voyah-iran.ir. Both health checks
passed. Public WSS agent/phone round trip passed using a random synthetic route;
no vehicle commands were sent. Let's Encrypt certificate installed with webroot
renewal and nginx reload hook. The old pinned/self-signed v1 prototype certificate
is no longer the public certificate; old v1 test binaries need their trust updated.
Production backups: /root/remote-release-backup-20261117/subscriptions.dump and
services.tar.gz; build and issuance logs are in the same restricted directory.
TBox telemetry agent/collector and macOS/Windows installers rebuilt locally;
no physical TBox was installed or restarted during server deployment.
Recheck relay explicitly: VI_LIVE_RELAY=wss://remote.voyah-iran.ir go -C remote test
./server -run TestLiveRelay -count=1 -v. This opt-in test uses no real vehicle route.
The server-only tests/subscription-live.py creates and removes a unique test VIN.
