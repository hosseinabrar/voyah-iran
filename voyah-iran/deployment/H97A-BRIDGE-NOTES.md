# H97A bridge startup and VIN (2026-09-26)

Evidence from the Android 9 H97A firmware dump and hardware logs:

- Init -> `app_process64` with `seclabel u:r:shell:s0` is rejected with
  `entrypoint` on `zygote_exec`, exit 127. This is not an APK launch failure.
- Starting ART manually through root ADB reaches `BridgeServer: ready`, but
  runs in `u:r:su:s0`. VIN requests reach the bridge and fail immediately.
- OEM `SystemProperties.nvramRead(21)` returns empty. The native log says
  `/dev/block/vdh` cannot be opened; the associated AVC denies `write` on
  `vdh_device`. Do not interpret the native “No such file” text as proof that
  the partition is absent or replace it with a guessed device path.
- Dump `vendor/etc/selinux/vendor_sepolicy.cil` grants `su_28_0` only read/open
  on `vdh_device`; `shell_28_0` has read/write/open. The read API requests write
  access internally; our code still calls only `nvramRead`, never `nvramWrite`.
- Dump `etc/selinux/plat_sepolicy.cil` allows `shell_exec` as a shell domain
  entrypoint and allows shell to execute `zygote_exec` without transitioning.

The installer now generates the H97A service command as `/system/bin/sh -c
"exec /system/bin/app_process /system/bin com.abrar.voyahiran.core.bridge.BridgeServer"`.
The existing shell seclabel, root UID, CLASSPATH and init supervision remain.
`exec` preserves the service PID. Other firmware profiles keep their original
command. No SELinux policy or block-device contents are modified.

Hardware validation still required: publish/reinstall, allow the installer to
reboot (terminates the temporary ADB bridge), check stable init state and a
successful `subscription-vin` result. Registration success is not proven by
`ready` alone. Do not retain a second manually launched bridge on the same port.

The retry UI had an independent bug: it manually displayed a spinner while
the manager remained VEHICLE_UNAVAILABLE. A repeated equal StateFlow value
did not redraw it. Retry now goes through CHECKING in the manager, and only
the state collector renders the UI. Native-read diagnostics log failure stage
and exception type, never the VIN contents.
