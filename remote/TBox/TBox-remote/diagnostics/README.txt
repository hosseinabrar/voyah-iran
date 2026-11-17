Live firmware diagnostic (H97X test vehicle only)

live_vns.py runs on the TBox factory Python 2.7 interpreter and verifies the
exact SHA256 of libvns and libhcore before loading either. It is not installed
by the product installer and is not connected to the public command gateway.
The current product OEM backend remains incompatible; this probe does not
claim general command support.

Status (does not wake or actuate anything):
  LD_LIBRARY_PATH=/mnt/hirain_app/usr/lib:/usr/lib64 python -u live_vns.py --action status

Explicit bench tests, only while an operator is present and has authorized:
  --wake --action hazard-on
  --wake --action climate-on

Wake sequence: timed OEM open lock (type 1), wait 2 seconds, timed vehicle_once
lock (type 2), wait 2 seconds. Release both when done, with a 25-second expiry
as a backup. This is not a permanent vehicle wake lock.

A remote result callback is only logged. Success requires the requested
vehicle state; otherwise the test returns exit 2 and never retries. Climate
sends AC-on only after power-on and HV readiness are observed. Unknown power
state refuses actuation. Cached signal reads alone cannot prove freshness;
correlate with current CAN/wakeup logs and operator observation. This tool
must NOT be promoted to a production executor without freshness guarantees.

Native ABI: ByteArray is 48 bytes, virtual RefBase at +32. Native remote item
is 16 bytes in a libstdc++ list node; the firmware handles authenticated CAN
transmission. Do not mix archived and live C++ libraries, change auth=true,
or substitute a custom raw header. No ECU security keys are extracted.

On-device diagnostic only has no pairing layer: do not expose it as a network
endpoint. Pairing, expiry and replay checks remain in the existing Go agent.

2026-11-09 temporary client bench bridge:
bench_bridge.py serves the existing root-only Unix socket, behind the unchanged
paired agent. It accepts ONLY 5:1 (hazard) and 48:1 (climate preparation), and
never maps cooling to a different seat-only action. It preserves native auth
and power-state guards. Expired requests cannot send additional frames.
All responses remain failure/diagnostic, even when cached state matches:
250 unsupported, 251 insufficient budget, 252 probe matched cache (not certified),
253 probe failed/refused. The existing client labels these as Factory errors;
these particular codes are BENCH codes, not actual OEM results.
The transient vi-bench-bridge.service and scripts under /tmp vanish on reboot.
No installer payload or factory binary was changed. Stop this unit before
starting TBox-remote-oem.service. This bridge is not production command support.
On the live device the client reached this adapter; climate preparation was
refused while power=2, remoteMode=1, before sending any actuator frame.

Follow-up: headunit-off request reached MCU (request 13008, receipt 0/0),
then MCU logged Auth Recv No random at 16:46:46.092. Power stayed OFF;
AC-on was not sent. Earlier 253 results with power=2 were precondition refusals.
Bench bridge now distinguishes 254 submitted/no callback and 255 callback seen
but state unconfirmed. Agent flag -bench-diagnostics translates these codes to
explicit diagnostic messages. Observation is bounded by request expiry so that
cleanup/reply has time; no late secondary frame or automatic retry is allowed.
A temporary agent in /tmp is selected only by a /run systemd override. Installed
agent and installer payload remain unchanged. Runtime-only setup skips prepare
because this boot is already initialized; reboot removes this entire override.
