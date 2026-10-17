VOYAH Audio Lab — standalone root-authorized X/C diagnostic, not a production feature

Open this folder in Android Studio. Shared wrapper:
  bash gradlew assembleDebug
Requires the sibling voyah-iran folder, Android SDK 36 / build-tools 37 and the
same Gradle/JDK setup as the main app. The existing pinned Frida 16.7.19 arm64
injector is packaged from ../voyah-iran/deployment/roa; no server/network download.
APK is not added to either installer. No main-app feature, subscription or CAN
configuration is changed. Package: com.abrar.voyahaudiotest.

HOW TO TEST
1. Park. Start at low source/playback volume. Do not test while driving.
2. Connect. Both com.android.bluetooth and com.qinggan.audiopolicy.service must
   already be running. Each control is enabled only after runtime capability
   discovery. Root/SELinux refusal is reported, never bypassed.
3. For Bluetooth attenuation, reconnect/start playback AFTER attaching, so the
   instrument observes the original gain. Unknown gain is NOT assumed to be 1.
4. Test one item at a time against the same track and OEM volume. Restore it,
   then compare the next. Multiple independent tests are possible if needed.
5. Native test accepts only channel 1 (media), integer 0..40. It calls the live
   OEM AudioPolicyNative.setVolume, bypassing the normal UI range check. No new
   native instance/init is created. Requested value, native result and feedback
   are logged; clamping/refusal is a valid finding, not proof of increased gain.
   Each Native test lasts THREE SECONDS. Other controls last at most 60 seconds.
6. Record full audio diagnostics before/after a test, at the same track/volume.
   Save log exports the current log; on-device files (current + previous) are at:
   /sdcard/Android/data/com.abrar.voyahaudiotest/files/audio-lab.log
   /sdcard/Android/data/com.abrar.voyahaudiotest/files/audio-lab.previous.log

CONTROLS / LIMITATIONS
- Bluetooth duck: positive gain below 1 is replaced with 1. Zero/focus-loss mute
  remains zero. Handles shared Bluetooth ducking (navigation/notification/etc.);
  it cannot distinguish the origin of each duck request.
- Absolute volume: suppresses incoming phone setAbsVolume calls only. It does
  NOT claim to disable all bidirectional synchronization or set phone gain.
- Navigation: temporarily ends OEM navigation-mix state and suppresses renewed
  setNaviMix(true) until restored. Not a blanket bypass of all OEM audio focus.
- DTS, Atmos, Loudness: independent reversible OEM toggles if supported. These
  are processing comparisons, not established causes of attenuation.
- Separate notification/voice policies that return "Not Implement yet" in the
  factory service are NOT represented by nonfunctional switches.
- Thermal protection, safety warning streams, Android safe-headphone limits,
  arbitrary amplifier channels, system partitions and persistent firmware/config
  are not modified. No guarantee of overriding DSP hardware limiters.

RECOVERY
Every mutation uses prepare/read -> synchronous durable app-private journal ->
apply. Unknown original values refuse the test. Restoration is confirmed by a
getter before clearing the journal. Keep the app installed until pending list is
empty. Stop/restore and leaving the Activity attempt rollback. Injected watchdog
restores after 10 seconds without heartbeat; Native additionally has a 3-second
deadline. These require the target process to be alive/responsive. Power loss,
target crash or SELinux can prevent immediate rollback: reconnect and press
Restore all. Stale journals block new experiments until restored. Never silently
discard a failed rollback. Direct BT gain cannot be recovered after reconnect
until a fresh gain event/object is observed; reconnect/play Bluetooth first.
No auto-apply/reinjection on boot or process restart. OEM changes may overwrite
Native test values; logs are evidence, not an acoustic measurement.

Runtime files use only /data/local/tmp/voyah-audio-test (two fixed overwritten
files), not production bridge paths. Source changes have not been tested on a
vehicle. No build is performed automatically.
