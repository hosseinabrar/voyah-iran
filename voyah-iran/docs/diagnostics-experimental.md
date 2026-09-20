# Experimental vehicle diagnostics — 2026-09-20

This is an unverified on-vehicle DoIP experiment, not a complete vehicle scanner.
No installer or privileged bridge changes are required; the existing INTERNET permission is used.

## First vehicle result and activation compatibility

`diag_log-2026-09-20_16-58-53.txt`: automotive Ethernet discovery succeeded:
local 172.16.104.40, peer 172.16.104.90, advertised logical address 071A, version 2.
TCP connected. The 7-byte default routing-activation request received generic
header NACK (type 0000), code 04 (invalid payload length). No UDS read was sent.
Loopback discovery found no entity. This is not evidence of an empty fault memory,
nor proof that 071A can route to all vehicle ECUs.

Added one compatibility attempt: only after this exact activation-phase NACK,
close the connection and retry activation on a new socket with the standard
11-byte form (four optional OEM bytes zero). Both 7 and 11 are documented forms;
whether this peer accepts 11 remains to be tested. No address/type guessing,
authentication changes, or retry after sending UDS. A ClearDTC request is still
sent at most once. Other activation rejections and ambiguous disconnects stop.

Reference: AUTOSAR CP DiagnosticOverIP, SWS_DoIP_00019 / routing activation length:
https://www.autosar.org/fileadmin/standards/R24-11/CP/AUTOSAR_CP_SWS_DiagnosticOverIP.pdf

Fake TCP peer tests reproduce the recorded NACK, verify the second request bytes,
then cover successful DTC read, both lengths rejected, other rejection without
retry, and no reconnect/repeated ClearDTC after a diagnostic-phase NACK.

## UI
- Main read discovers loopback and active eth/usb/rndis IPv4 broadcasts. Local test only probes loopback.
- Discovery uses UDP13400 vehicle identification, then the entity's advertised logical address and protocol version, TCP13400 routing activation with tester0E00/default activation.
- No arbitrary ECU address sweep, extended/programming session, security access, gateway unlock, or firmware routine.
- Read UDS1902FF. Show three-byte raw DTC + status + responding endpoint. OEM descriptions and severity are unknown. An empty positive read only covers that responding entity, not the whole car.
- Clear requires a successful non-empty read this page visit and explicit confirmation naming the endpoints. UDS14FFFFFF is sent once per confirmed endpoint; only54 confirms acceptance. Re-read afterwards; failures/remaining DTCs stay visible. No automatic retry of mutations.
- Navigating away/re-entering discards results and clear targets. In-flight old results cannot repopulate the page. Operations are serialized.
- VI/Diagnostics logs interface discovery, endpoints, request IDs, TX/RX (bounded raw preview), every decoded DTC, rejections, and timeouts. VIN discovery bytes intentionally not logged.

## OEM evidence
- DoIPServer-release APK: com.qingos.doipserver.DoIPManager has native initDoIPServer, sendDoIPResultByRID, upgrade/dependency/download callbacks. This is evidence of an inbound headunit update server, NOT proof of a cross-ECU diagnostic client.
- H97C setCanRawData only accepts IDs1526,1509,1510,1362,1363,821. getCanRawData returns a cache. Neither is an arbitrary ISO-TP interface.
- SDK DiagnosticInfo handles board temperature, audio, USB etc. RealityWarningInfo is warning state, not DTC history.

## Test plan
Stationary vehicle: record full logcat; open Diag, run main read then local read; send log even if neither returns a DTC. Distinguish no discovery, routing denied, UDS service rejected, valid empty read, valid records. Only after preserving fault report and service advice test clear. A gateway entity may expose only its own faults; other ECU logical addresses/routes still need OEM evidence. Unknown faults never imply safe driving.

## Reference contracts
https://python-doipclient.readthedocs.io/en/latest/messages.html
https://udsoncan.readthedocs.io/en/latest/udsoncan/services.html

Protocol parser and fake TCP ECU tests cover positive/empty/status-masked reads, malformed records, pending response, routing activation, acknowledged clear, rejected clear, and wrong response addresses. Hardware behavior remains untested.

Validation: Android compileDebugKotlin passed; 6 focused JUnit tests passed using standalone Kotlin/JUnit (fake TCP ECU). Full Gradle unit-test pipeline was blocked in compileDebugJavaWithJavac by AccessDeniedException on cached activity-ktx-1.10.1-api.jar, unrelated to these Kotlin sources. No release APK was generated.
