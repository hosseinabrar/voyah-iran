# Local seat-memory UI activation

VIP-only, opt-in, h97a/h97c only. The Settings checkbox follows vehicle activation.
The root preference survives loss of entitlement; the runtime requires the short VIP
lease and detaches when disabled or expired. Injected methods also require a 10-second
monotonic heartbeat, so a dead bridge cannot leave the bypass enabled.

## Verified factory paths

Inspected the supplied h97a and h97c APKs (no factory APK edits):

- h97a Launcher: `com.qinggan.mainlauncher.hiboard.vehiclehiboard.widget.ComponentSmallView.handleSeatClick`.
- h97c Hiboard: `com.qinggan.app.hiboard.vehiclehiboard.main.ComponentSmallView.handleSeatClick`
  and `ComponentSmallOTA1x1View.handleSeatClick`.
- Both reject Guest before issuing the seat operation. The h97c handler also has an
  in-progress stop path. Retain the complete factory handler, returning a private
  non-Guest account bean only during this handler on its calling thread. Never change
  the shared account, token, account ID or general login state.
- Long-press opens `SeatService` with `showSeatMemory`. Normal clicks use
  `sendSeat01/02/03`. The service calls `SeatMemoryManager.executeSeatByOtherProcess`,
  the same receiver Raha reaches through its `SeatMemory` QGBus event.
- VehicleAir `MirrorMainView.initFirstStatusView` hides the complete memory panel for
  Guest. Scope the manager's Guest predicate to this UI initialization so all three
  save/recall controls are available. Their original methods send `saveSeat01/02/03`
  and `sendSeat01/02/03` and retain factory driving restrictions.
- Seat position feedback (`modifyIsPosSame`, h97a `noMemoryModifyPosSame`) and the
  manual-adjustment listener also have Guest gates. Scope these independently so
  selected-slot state and the small seat-memory island function correctly.
- Island's `SeatMemoryView` already sends save/recall to the same receiver and opens
  the full memory panel; it has no separate Guest gate to replace.
- Cloud uploads and account-change automatic recall keep their original Guest checks.
  No startup or enable action issues any seat motion/save command.

The root bridge owns an isolated injector executable and one attachment per relevant
process. Restarted OEM processes reattach; stale owned injectors are cleaned up.
Unsupported method contracts log an error and restore installed hooks. No global
Toast suppression and no CAN polling are used.

## Validation

- `node tools/tests/seat-memory-test.cjs`: scoped Guest override, three slots, both
  h97c widget types, preserved stop/speed branches, exceptions, lease expiry,
  deactivation, unmodified account object and partial-install rollback (mocked Frida).
- `BridgePolicyTest`: only enable/disable/status accepted on API 28 and 30.
- Subscription UI tests cover VIP checkbox masking and preserved desired selection.
- Compile and unit tests do not validate injection or physical seat movement.

## Vehicle test

Install the updated APK/root bridge using the installer and reboot. On each variant:
1. With VIP valid, enable the new setting. Wait for `VI_SEAT_READY` for vehicle and
   launcher (h97a) or hiboard (h97c) under `SeatMemoryActivation` in logs.
2. In a parked vehicle save distinguishable positions to Drive, Rest and Other via
   the full factory memory panel. Recall each from the drawer and full panel.
3. Long-press a drawer memory button and check the three-slot panel opens without
   the Guest panel. Verify the small memory island after manually adjusting the seat.
4. Check ordinary stop/in-progress behavior. Do not bypass the factory driving lock.
5. Disable and re-enable; test reboot and an OEM process restart. Stored positions
   must remain, and enabling by itself must not move the seat.
6. Remove VIP: the checkbox masks off and factory Guest behavior returns. Restore
   VIP: the saved activation preference resumes without rewriting seat positions.
