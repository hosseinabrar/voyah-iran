IMPORTANT SOURCE CORRECTION (2026-11-09):
The archived OEM engine used by this prototype is H97C 1.6.9p, not the live H97X 2.0.7 backup. Do not distribute/install the bundled engine as compatible. See TBox/TBox-remote/firmware/SOURCE-CORRECTION.txt. Earlier X/archived-runtime wording below is historical and superseded.

TBox remote — paired vehicle test
================================

Layout (all paths relative to this directory)
 client/web-test: loopback web UI and encrypted client backend.
 server: opaque HTTPS message relay; no vehicle private keys.
 TBox/TBox-remote: ARM64 agent, OEM bridge, SMS patch and USB installer.
 TBox/TBox-antenna: existing antenna/APN/head-unit integration kit.

Run locally
 Double-click client/web-test/start_mac.command or start_win.cmd.
 Open http://127.0.0.1:18781/ on this laptop.
 Double-click TBox/TBox-remote/install_mac.command or install_win.cmd
 with the TBox connected directly over USB. The installer selects a TBox,
 requires version 2.0.7 and known binary hashes, installs the antenna kit
 if needed, then the remote payload. It must not target the head unit.
 Use the matching uninstall launcher to remove remote overlays. Antenna
 configuration and replay journals are retained.

Pairing and relay
 This is a single-laptop, single-TBox test pairing. Endpoint-specific keys
 are embedded at build time from ignored pairing.local.json files. Keep
 those files for rebuilds; never publish or send the laptop's private key.
 The TBox payload necessarily contains its own private key: distribute this
 test payload only to the owner's vehicle. Do not reuse it across customers.
 Run setup only for a NEW pairing, not on ordinary rebuilds.

 The relay is https://remote.voyah-iran.ir at 193.242.125.144, with an isolated
 nginx virtual host and loopback service on port 18780. The existing
 voyah-iran.ir HTTP subscription virtual host is unchanged. Only the remote
 virtual host redirects HTTP to HTTPS. No global HTTP redirect was added.
 TLS uses a dedicated pinned certificate for this test; the clients trust
 the bundled public certificate and preserve hostname verification even
 when dialing the fixed IP. Ordinary browsers do not trust this test CA.
 Renewing that certificate requires updating pairing RootCA and rebuilding
 both endpoints. The local browser talks to the localhost backend.

SMS and command execution
 Only SMS from normalized 09358624411 (+98/0098 variants supported) opens
 a 60-second session. SMS contents do not execute commands. The daemon
 automatically subscribes to the firmware SMS signal; no laptop action is
 required after installation. A repeated SMS identity cannot reopen it.
 CLOCK_BOOTTIME includes suspend and avoids wall-clock corrections.

 X25519 + direction-separated HKDF keys + AES-GCM authenticate and encrypt
 requests and replies. The gateway sees ciphertext and routing metadata.
 Random lease IDs and short-lived one-use grants bind commands to a fresh
 session. UUID replay records are fsynced before OEM execution. Storage
 failure rejects execution. Replays and stale grants are rejected.
 Offline UI requests time out after 10 seconds without being queued for a
 later SMS. Online commands also expire within 10 seconds and the lease.
 No automatic command retries: a timeout can mean an OEM operation already
 began but its outcome is unknown. The UI must not report that as success.

 OEM execution uses the existing remotecontrol callback on its GLib loop;
 matched OEM result messages supply completion status. All unrelated OEM
 traffic is forwarded unchanged. The bridge is process-local LD_PRELOAD,
 not a global preload. Bind mounts preserve immutable firmware originals.
 prepare.sh validates both binaries before mounting and removes its own
 overlays/disables startup if readiness fails. Unknown firmware is rejected.
 The SMS patch adds initialization missing in the original hrild binary.

Coverage and limitations
 protocol/catalog.json lists 44 externally selectable operations from the
 X firmware. firmware/all-control-branches.json preserves all 70 XML
 branches; remaining entries include internal cleanup/roof-check variants.
 Scheduled climate starts about one minute later for ten minutes: its OEM
 acknowledgement is reported as accepted, not physical completion.
 Camera commands currently expose request/result acknowledgements only;
 media download is not implemented. Standalone fuel/battery telemetry is
 not implemented in this first test client.

Validation completed on laptop/server
 Go protocol/session/gateway/agent tests with race detector passed.
 Real deployed relay simulation passed: offline 10-second timeout,
 encrypted round trip, explicit simulation status, 60-second expiry and
 another offline timeout. Simulation does not send SMS or operate a car.
 Native ARM64 bridge and patched firmware compiled, payload checksums and
 shell syntax verified. macOS/Windows launch binaries built.
 Actual SMS wake from vehicle sleep, OEM actuation, power consumption and
 Windows execution still require installation and testing on hardware.
 A valid SMS callback on an awake TBox does not itself prove deep-sleep wake.

Build
 Run bash build.sh with Go installed. The prebuilt native bridge and SMS
 patch are packaged by TBox/TBox-remote/package.py; editing native sources
 requires rebuilding those artifacts first. Do not rotate pairing on build.
 See native/build-sms-patch.py for exact source hashes and patch addresses.
 State remains under /usr/local/lib/TBox-remote/state on the TBox.
 Service logs: /tmp/TBox-remote-agent.log, /tmp/TBox-remote-prepare.log and
 /tmp/TBox-remote-oem.log (this device has no usable journal). No SMS body or
 private keys should be logged. See the hardware validation update below.

2026-11-09 hardware validation update
 The attached 2.0.7 TBox has NO factory remotecontrol executable. Its version
 file is /mnt/hirain_app/etc/software_versions.xml, not /usr/etc/.
 The package now includes the archived engine, its matching private XML and
 a process-local library closure. No factory library is replaced. Boost
 dependencies come from the archived system image; provenance/hashes are in
 TBox/TBox-remote/firmware/bundled-runtime.json. This is not proof that all
 versions sharing the same version string have an identical ABI or CAN map.
 Native build-oem-bundle.py reproduces these artifacts from the local archive.
 Only path strings in the bundled engine are adjusted; layout is preserved.
 Shared libraries are debug-stripped, stored compressed and unpacked in /run
 because /usr/local is only 30 MB. Existing factory remote engines are rejected
 to prevent duplicate control daemons. The bundled engine has its own service.
 Real USB installation and reinstallation succeeded. OEM engine and agent
 were active after startup (one OEM restart observed during initialization);
 the bridge socket and SMS interface were
 present. The original antenna radio-patch hash remained unchanged.
 No actuator command, incoming real SMS, sleep wakeup or reboot was tested.
 The installer now detects shell failure via an explicit success marker,
 because this ADB shell can report host exit 0 even when remote scripts fail.

2026-11-09 hazard execution investigation
 Owner confirmed real SMS opens the client session. Encrypted command 5:1
 reached the OEM callback and generated acknowledgement 0x101, but no
 completion within the 10-second client deadline. Live power-manager has
 no get_mcu_status method; the archived engine's library expects it. The
 live library instead reads PowerManagerContext shared memory. A temporary
 read-only compatibility probe confirmed MCU state=1, but execution still
 failed: archived VNS requests cause session disconnects/subscription errors.
 The shared-memory probe was removed, not shipped as a speculative fix.
 Therefore active services and a ready socket do NOT prove actuator support.
 No successful hazard actuation has been confirmed. Do not distribute this
 test package as compatible until the live VNS interface has been adapted.

 Temporary owner-authorized bench mode: agent -test-no-sms automatically
 renews a paired lease every 45 seconds; encryption and UUID replay checks
 remain required. This flag is only in /run/systemd/system/TBox-remote.service.d/
 test.conf on this device, so reboot removes it and restores SMS gating.
 Remove that drop-in and restart the service to end bench mode explicitly.
 Original diagnostics are archived outside Git under
 ../work/tbox-remote-validation-20261109 (relative to repository root).

2026-11-09 follow-up, live bench investigation (15:09 local)
 SMS gating restored to temporary -test-no-sms after reboot, owner authorized.
 The bundled OEM engine is STOPPED on the test device to stop its crash loop.
 Backtrace: archived libvns TerminalSession::sendSubMCUDataMsg -> boost mutex
 Invalid argument during HFramework reconnectSession. Native live VNS opens
 a session successfully in a separate read-only probe.
 Swapping libvns alone fails on hirain::Logger::gMinLogLevel; using all live
 libraries fails on ByteArray(const char*,int,bool). Disassembly proves an
 ABI/layout change: old ByteArray virtual RefBase at +0x18, live at +0x20.
 DO NOT stub the missing constructor or mix these libraries: that risks
 memory corruption. The temporary libvns swap was reverted. Factory files
 were not modified. A version-matched adapter/engine is still required.

 Earlier native remote dispatch returned MCU acceptance, NOT confirmed
 actuation; MCU logged Auth Recv No random. Correct native remote item header
 is 10 bytes. MCU diagnostic formatter repeats a byte; do not infer a 9-byte
 header from that output. Raw-header experiments must never ship.
 Climate preparation did not reach confirmed power-on; AC-on was not sent.
 Device reboot occurred immediately after a vehicle_once wake transition;
 causation is not proven, and no valid kernel panic report was found.
 Current read-only state returned zeros while SPI/CAN slept; these are NOT
 evidence of fresh vehicle state. No new actuator request was sent in this
 follow-up. Preserve freshness checks before enabling a replacement engine.
 Evidence: ../work/tbox-remote-validation-20261109/messages-after-sms.log.
 Test gate and service stop are runtime-only and reset on reboot.

2026-11-09 staged-wake investigation (15:17-15:18 local)
 Timed open wakelock type 1, then vehicle_once type 2 after two seconds,
 brought CAN from sleep to wake and supplied OFF=1 / normal remote mode=1.
 No reboot occurred during these bounded probes. Both locks were released.
 Native authenticated hazard request 177 was acknowledged (0,0), but hazard
 stayed 0 and MCU logged Auth Recv No random. The native climate power-on
 request also hit No random; OFF stayed 1, so no AC-on request was sent.
 The failure is therefore not isolated to hazard control. Key-inside versus
 key-outside is pending a controlled comparison, not an established cause.
 Owner reports a sound near climate equipment, with no actual ventilation.

 Diagnostic source: TBox/TBox-remote/diagnostics/live_vns.py (relative to remote).
 The on-device status path was tested successfully under factory Python 2.7.
 It checks live library hashes; it is NOT integrated or shipped as a working
 remote executor. Raw signal cache does not prove freshness. Current factory
 shared-library files remain untouched. Runtime SMS bypass is active.
 Logs: ../work/tbox-remote-validation-20261109/native-staged-wake.log,
 staged-hazard.log, staged-climate.log, sms-reboot.log.

 MCU C comparison: extracted the App block from the existing C 2.2.1 update
 into ../work/tbox-remote-tools/c-mcu (private, never flashed). Its firmware
 also contains a BCM random-challenge wait and a No random failure path,
 unlike the assumption that C remote execution is unrestricted. The C
 state machine has three request classes versus two in the inspected X
 cached MCU; this does not establish live MCU identity or interchangeability.
 No keys were extracted and no authentication checks were bypassed.

2026-11-09 key-out comparison, 16:27-16:28 local
 Owner announced leaving the vehicle with the key and locking it after ten
 seconds. Waited before testing. Device had rebooted since previous tests;
 restored runtime test-no-sms and stopped the incompatible OEM engine.
 Native hazard-on request 9234 reached MCU, acknowledgement (0,0), but hazard
 remained 0 and MCU reported No random. Native climate preparation likewise
 left power OFF=1 and reported No random; AC-on was not sent. Both bounded
 probes returned exit 2; locks were released and no retries remained.
 Key removal did not change the observed failure; this alone does not prove
 whether BCM rejects or never receives the outgoing request. No CAN-wire
 capture is available yet. Evidence: key-out-hazard.log, key-out-climate.log
 and their -system.log companions under the private validation directory.

2026-11-09 headunit alternative-path test, 16:55 local
 With vehicle OFF per owner, client 48:1 wake/preparation was followed by HU
 ADB reappearance. First HU window closed before command; second wake/test
 reached the installed Voyah bridge. Readback before: speed=0, AirSW=0,
 AirAC=0, WindSpeed=0. Explicit SET_CLIMATE_POWER enabled=true reached OEM
 CanBusService at 16:55:09.032: AC_POWER_SWITCH state=1 pid=1690.
 Bridge returned 'feedback unchanged; toggle not retried'. Subsequent read
 still SW=0, AC=0, fan=0. No automatic toggle retry or other actuator sent.
 Thus headunit software access works in this wake window; actual HVAC power
 did NOT. This does not prove all HU commands fail or that READY/HV is the
 exact blocker. TBox client still reported MCU receipt without state change.
 Evidence: ../work/tbox-remote-validation-20261109/headunit-climate-direct.log.

Further verification: the 1.6.9p label is an internal file in the supplied MPU, not proof of its overall reported version. Our own active-slot NAND backup library matches the live library exactly. See remote/TBox/TBox-remote/firmware/SOURCE-CORRECTION.txt (repository-relative).
