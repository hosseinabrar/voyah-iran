# Steering media controls

Default off; Settings checkbox enables automatic MediaSession routing. Notification listener authorization is provisioned by the authenticated root bridge (fixed component, user 0). Listener does not inspect or store notifications. No APK-specific integration or framework patch is required.

OEM evidence: extracted keymanager sources, AndroidSink.tranlateKeyCode: 3 -> Android NEXT 87, 4 -> PREVIOUS 88, 6 -> PLAY_PAUSE 85, 8 -> PLAY 126, 9 -> PAUSE 127. KeyManagerReader emits DOWN 4096, SHORT 8192 and UP 65536; phone mode converts next/previous to phone keys before dispatch. Media joins the existing per-package star/voice registration; never registers a separate interceptor. Only display 0 and normal audio mode are eligible.

The highest-priority playing/buffering session wins. Otherwise reuse the last controlled paused/stopped session, then the first resumable session. A session must still exist. Recipient is latched per press; only SHORT sends Android DOWN+UP once, asynchronously outside OEM Binder callback. No session: leave OEM handling intact. Disabling/expired subscription releases only media interception, preserving star/voice. Normal calls pass through. Long presses currently do not seek.

Vehicle test: publish/install updated APK AND bridge; enable checkbox; start a track in Spotify/Apple Music; next, previous, pause then play; switch to another player; test with app in background; confirm star/back and Raha still work; toggle off and confirm OEM behavior; reboot; test pause/resume after Raha interruption. No vehicle test has been performed during development.

Diagnostics: full logcat contains VI/SteeringMedia target/package/state, OEM key/action/display, dispatch DOWN/UP accepted flags and failures, plus VI/Steering shared registered key list. Dispatch acceptance is not proof of audible playback. Apps without a MediaSession may not respond.
