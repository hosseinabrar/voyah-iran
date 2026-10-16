VOYAH DOW Probe — read-only CAN monitoring + explicit ambient-light diagnostic

Test lights starts the MAIN bridge's actual warning engine for at most 10 seconds.
Requires the updated main installer/bridge, root access and an active VIP session.
The car must be powered and stationary. Stop test / restore or leaving this app
ends the manual trigger; a real DOW warning can still keep the automatic warning active.
No subscription preferences or DOW CAN states are changed. The root-only credential
is read into memory, never logged or copied into probe storage.

Open this directory directly in Android Studio, or open ../voyah-iran and select module dow-test.
For a standalone IDE import, use Gradle 9.5.0 (matching the main project's wrapper).
The standalone settings reuse the main project's dependency catalog and DOW sources;
keep this directory alongside ../voyah-iran rather than copying it out on its own.
This diagnostic module is not an installer dependency and is not included in production bundles.
Build only when requested:
  cd ../voyah-iran
  ./gradlew :dow-test:assembleDebug
APK: ../voyah-dow-test/build/outputs/apk/debug/dow-test-debug.apk
Alternatively, from this directory use the shared wrapper without building the main app:
  ../voyah-iran/gradlew -p . assembleDebug
Package: com.abrar.voyahdowtest

Test:
1. Leave the main app's experimental ambient-light checkbox OFF while identifying the state.
2. Open this probe on a stationary vehicle in a controlled safe location.
3. Target defaults to 3. Enter a value and press Apply value.
4. Try 3, then 2, then 1; these are candidates, NOT documented warning values.
5. DOW_STATE 4 is explicitly a factory fault; do not adopt it as danger.
6. The field is three bits: 0..7. Test 0,5,6,7 only if actually observed in the log.
7. Compare the START AND END of the car's normal audible warning with the red pulse.
   A value that also matches normal idle operation is not a valid danger trigger.
   Do not create a real traffic hazard to provoke DOW.
8. Save log opens the system document picker. Share log shares the last 200,000 characters.
   Complete file fallback:
   adb pull /sdcard/Android/data/com.abrar.voyahdowtest/files/dow-probe.log
   Also collect dow-probe.previous.log if present.
   Logcat tag: DOWProbe. Recording runs while this test Activity is foreground.
9. Record approximately when the audible alert began and ended; send the full log even
   if none of the proposed values matches. All observed values are logged independent
   of the chosen target. Existing logs are appended, not cleared on launch.
10. If the CAN service disconnects, flashing stops. Reopen the activity to reconnect.

Protocol:
Resolve DOW_STATE and DOW_SYSTEM_SWITCH from the installed VehicleSetting APK;
H97A's DOW enum differs from X/C. No guessed fallback if SDK is unavailable.
Register CAN callback (0x1c), read vehicle state (0x39), read ACC (0x42), unregister (0x1d).
Read-only diagnostic; no root, DOW setting changes or ambient writes.
Log is bounded to two ~2 MB files.
Red pulse uses the same 500 ms, 10-level curve as the main feature.
Preview images contain explicitly simulated data, not vehicle evidence.

Main feature:
VIP checkbox in Comfort; disabled by default. Experimental warning value 3 is defined
once in DowContract.EXPERIMENTAL_WARNING. No installer or APK was built for this change.
OEM red slot 62; normal mode uses music off and vehicle_setting_drive_mode_atm=0.
Each cycle is 250 ms ON at brightness 10, then 250 ms OFF (real power switch).
All four lighting controls travel together in each CAN bundle. Hardware timing needs testing.
No per-frame shell process. Resident root bridge serializes writes with other commands.
The original power/music/drive-follow/color/brightness snapshot is fsynced before writes
to /data/local/voyahiran-dow-ambient.json, outside installer payload/temporary backups.
Pending rollback is retained until feedback confirms recovery; it is allowed even after
VIP expires because it only undoes this feature's own changes.
Normal preferences are also remembered while enabled and restored on the next activation.
CAN disconnects retain recovery; unavailable hardware cannot be forced to acknowledge.
A 15-second app heartbeat lease and VIP lease prevent orphan indefinite pulsing.
Actual ECU response speed, acknowledgement of modes while lighting is off, and DOW danger
value still require vehicle testing. Do not use this experimental feature as a safety guarantee.
