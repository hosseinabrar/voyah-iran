# H97X AVM command correction (2026-10-03)

Evidence: `turn signal 2 log-2026-10-03_12-52-58.txt`.
Open IVI_VOICE_REQ_AVM_SW=1 works. sendMsg(14,4,0) returns zero
without changing the view. touchEvent(0/1,60,75,...) also returns zero
without a native touch packet or AVM hide. Manual taps emit real packets.

Local firmware: Desktop/voyah h97x/system/bin/dls_reverse_svr, AArch64.
- sendMsg entry 0x268b4 handles only request 13; other requests return zero.
- SendViewSelect 0x4aa30 builds 10 bytes: 1c 08 00 00 00 00 (view<<1) 00 00 00.
- At 0x4aab0 it calls wrapper 0x25a00, which calls qg_canbus_control(1, buffer).
- libqg_hal.so exports qg_canbus_open(), qg_canbus_control(int, pointer),
  qg_canbus_close(). open returns 0 on success; control uses ioctl and returns 0 on success.
- Captured physical close at x=80,y=76: down 1c 08 00 0a 4c 00 00 01 00 00;
  up 1c 08 00 0a 4c 00 00 03 00 00.

Follow-up capture `turn signal 3 log-2026-10-03_13-15-33.txt` shows
NativeLibrary.loadLibrary throwing UnsatisfiedLinkError before sending any packet.
HAL dependencies include private libuhab_sys/libutils/libbinder libraries.
The specific linker cause was not recorded by the previous logger.
At libqg_hal.so 0x10a10, open uses /dev/cis_can with O_RDWR; at 0x10ac0
control calls ioctl(fd, command, buffer), treating all nonnegative results as success.
Bridge now uses that direct public-libc ioctl transport with bounded camera-only packets;
it does not load libqg_hal or its private dependencies. Descriptor ownership is local.
No generic raw CAN endpoint. JNA dispatch is extracted from the installed bridge APK
because app_process does not have the app native search path. Camera state still
comes from the existing read-only netlink receiver. Dispatch return alone is not
success: view changes / close require a matching native state update.
Reverse, PDC, speed, session ownership, and 2D/3D preservation guards remain.

Unit packet/policy tests and Kotlin compilation pass. Actual HAL access and camera
response must still be verified on the vehicle with the updated installer bridge.
