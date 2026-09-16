# Raha integration

## Shared source

`raha-core` is the pure Kotlin command recognizer and regression suite (Gradle name
`:command-core`). `raha-runtime` owns recording, Vosk, the Persian model, wake gating
and Silero VAD. Both Voyah Iran and the sibling voice-test app use these modules.
The production service selects `CAR_LIVE`; the experimental Shenava weights remain
in the test application. There is no second copy of the Persian model to update.

The existing keyword recognition rules and enum/parameter contract are retained.
The voice-test app still previews commands; only the main app executes them.

## Lifecycle and execution

`VoicePageController` stores the master and three wake selections. Microphone
permission is requested on enabling. `RahaService` owns capture independently of
the Activity, serializes manual/wake capture, and executes final transcripts only.
Turning the master off releases capture and the voice key. The OEM key service
indexes interception by package, so voice key 130 and the star key share one
`QingganStarMapping` registration. A one-second watchdog reconnects the listener.

Boot starts the microphone foreground service through the authenticated root
bridge, without showing an Activity. Both supplied Android 11 ActiveServices
implementations allow while-in-use microphone access for a foreground-service
start whose caller is UID 0. This requires the updated bridge as well as the APK.

`RahaVehiclePlan` resolves parameters/current state before writes.
`RahaVehicleBridge` loads the installed OEM SDK and sends its typed Binder calls.
Seat memory publishes the OEM `SeatMemory` event for the Driving profile; the
supplied QGBusService handles publish message 3 without a subscriber registration.
Vehicle dispatch is logged separately from speech recognition. A dispatched
request is not proof that an ECU completed it. Ambiguous writes are not replayed.

## Explicit execution gaps

These recognized intents currently return an execution error rather than a
different physical action:


- General DRL power: the discovered field changes parking behavior instead.
- Rain-sensing wiper enable/disable: sensitivity is mapped, but no matching
  enable/disable operation was established in the supplied H97C interface.

Sunroof/shade percentages use their actual percentage interface. Numeric ranges
are mapped to OEM ranges independently of the recognizer's abstract levels.
Child-lock requests, including requests naming either side, operate the vehicle's
single combined child-lock control.

Window percentage/relative commands now use `WindowPositionController`: read all
selected positions before motion, send manual down (4) or up (2), poll percentage
feedback every 60 ms and STOP (5) each window within 3 percentage points of target.
Endpoints 0 and 100 use zero tolerance. Relative commands calculate each target
from that window's latest reported position and clamp to 0..100. Full open/close
and vent commands retain their native OEM operations. No duration-to-distance
calibration is assumed. Stalled feedback (1.5 seconds), invalid values, reversal
or overall timeout (8 seconds) abort and attempt STOP on all active windows.
These timings and the OEM manual-motion/stop response require vehicle testing.
Logs under `RahaWindow` include start, changing position, target, stop reason and
elapsed time. No corrective reversal is attempted after overshoot.

The inspected Tweaks 2.8.2 resources contain `double_smoke` and
`double_ventilation`: their descriptions say lower further than standard vent,
driver window only for smoke and all windows for ventilation. They do not claim
an exact 50% target. Its application code is Virbox-protected, so these resources
do not establish its command timing or whether it repeats vent vs. uses manual
motion/stop. Do not present either hypothesis as a recovered implementation.

## Validation and device checks

Both apps' Kotlin/resource compilation passed. The 47 core regression tests and
38 standalone planner/steering domain tests passed (including window control, directional child
lock and OEM massage patterns). Full Android Gradle unit-test
execution was blocked by this host's Java compiler JAR access error; it is not
reported as a pass. No APK was generated and no vehicle was connected.

Build the main release, publish/update the installer bundle, install through the
installer and reboot so the updated bridge process is running. Enable Raha and
grant microphone access once. Test manual trigger, each wake phrase, a command
immediately after the wake phrase, master off, boot with the UI hidden, and star
Back while Raha is enabled. Check real vehicle state for every command family
before wider release. Capture unfiltered logcat; relevant tags include Raha,
RahaVehicle, RahaSeat, RahaBoot, Steering and StarEvent.

## Overlay and earcons

`RahaFeedback` displays a vector orb centered horizontally at 25% screen height.
It is hidden while passively waiting for a wake phrase. During an interaction,
waiting has a slow wave, voice capture reacts to microphone level, processing
continues animating, and success/failure show green/red for 1.3 seconds.
Success means the adapter accepted/dispatched the request, not independent ECU
confirmation. Three original synthesized WAV cues are bundled in res/raw.

A wake phrase alone schedules its ready cue after two seconds. Admitted command
text suppresses it; Silero speech activity prevents playing it over ongoing
speech. Manual steering activation uses an immediate ready cue. VAD observation
does not change the Vosk wake/command endpoint logic. Touching the transparent
full-display overlay dismisses it, cancels capture and invalidates queued results.
Already dispatched physical requests are not rolled back. This is a standard
Android application overlay; protected system surfaces may appear above it.

SYSTEM_ALERT_WINDOW is declared and granted to this package only via the updated
root bridge's exact `raha-overlay enable` operation. Reinstall the new APK/bridge
through the installer and reboot for this operation to be available. Logs tagged
RahaFeedback record permission outcomes, cues/volume, overlay errors and touches.
Hardware checks still needed: overlay on other apps, audio routing/volume, joining
wake and command without a pause, wake-only delay, cancellation, boot and sleep.


## Shared preview and transcript

The sibling voice-test app and vehicle app both depend on :raha-feedback.
That module owns the orb, overlay layout, optional transcript bubble, state
machine and three sound cues. The test app fixes recognition to CAR_LIVE and
hosts the same overlay in its activity window, requiring no overlay permission.
The vehicle service continues using the system application overlay.

Show transcript defaults to false and is persisted separately in each app.
When enabled, admitted partial/final speech appears below the orb as white text
on a nearly opaque navy bubble with a light border, independent of host theme.
Touch cancellation invalidates callbacks before dismissing the overlay. Unknown
samples can still be reviewed/saved using the test page review button.
