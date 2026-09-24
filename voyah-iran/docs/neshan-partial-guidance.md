# Partial guidance (2026-09-21)

Navigation no longer requires road + maneuver + distance together. Each getter is isolated:
null/throwing distance does not discard direction or road; null/throwing direction does not
discard distance or road. Snapshot fields are not merged with older steps. The existing source
age / route-reset / parent-heartbeat cleanup remains in force.

Missing road is `-`. Missing maneuver uses cluster icon 0, with no fabricated straight arrow.
Missing distance is represented internally by -1; actual zero remains zero. Known distance is
still floored by ClusterDistance. The generic JS engine changes; published Neshan profiles do not.

The OEM GuidanceFormat turns negative distances into 0. A lazy formatter helper therefore attaches
to the verified package `com.qinggan.cluster` only when a missing-distance frame is needed. It
overrides only `toDistance(int)` and `toDistanceUnit(Context,int)` for our reserved sentinel
`-2147483000`, returning `-` and an empty unit. All other values call the original methods.
No second native cluster socket, firmware patch, or cluster service restart is introduced.
Uses the already verified private frida-inject copy. Acknowledgement required before sentinel
submission. Detached on normal-distance frames / clear / subscription expiry; 15s parent watchdog.

If attachment fails or methods differ on a firmware, the fallback is ROAM text `road | -`, logged
under ClusterPlaceholder/NeshanClusterBridge. It preserves truthful text and does not emit fake
zero meters; the arrow cannot be shown with this fallback. This compatibility limitation needs
hardware testing, not a claim that every firmware has passed.

Tests: partial direction-only, distance-only and name-only samples (including throwing getters),
no unknown-direction fallback arrow, dash road labels, formatter exact-sentinel behavior,
unchanged ordinary distance/unit results, watchdog and embedded script parity. Targeted Android
source compilation passes. Hardware acceptance is still required. Update via complete installer.


## 200 ms navigation cadence

Direct-hook sampling, bridge consumption and cluster dispatch now use 200 ms ticks. These are independent ticks, not an end-to-end latency guarantee; the source route model still determines when new data exists. PID inspection and parent pings stay at one second; config checks at five seconds. Whole-road translation is reused while the Persian label is unchanged.

The bridge keeps refreshing its local 3.5-second ownership lease even when values repeat. OEM Binder sends are skipped between keepalives when the final displayed road, icon and quantized distance match the last successful send. Unchanged valid frames are resent after one second since the last successful dispatch (on the next 200 ms worker tick), because vehicle testing showed the cluster expires silent guidance. A dead Binder, failed send, clear/restart, or placeholder availability transition forces a fresh send. Missing-distance formatter heartbeat continues independently of display deduplication. Source expiry and subscription gating are unchanged. Vehicle retest is pending for the one-second keepalive. The source five-second freshness limit and local lease are deliberately not extended by outbound keepalives.
