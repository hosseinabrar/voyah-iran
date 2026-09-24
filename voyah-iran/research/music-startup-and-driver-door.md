# Music startup and driver-door pause

The media listener owns `MusicAutomation`; its authorization is needed when any of wheel controls, autoplay or door pause is enabled. Notification content is not used. All operations are subscription-gated.

- Startup is claimed once per Android BOOT_COUNT, persisted across listener/app recreation. An observed CAN ACC-off/on also re-arms startup for head units that sleep. No automatic resume on door close.
- Third-party players: connect an exported MediaBrowserService first; after 3 seconds send targeted transport Play. If initialization requires an Activity, request `makeTaskLaunchBehind` with NEW_DOCUMENT/MULTIPLE_TASK. Never send Home or force-stop the foreground app. Apps with unusual launch modes/background restrictions need hardware verification.
- Bounded retries: up to 10 attempts, 3 seconds apart. Stop on confirmed playback, another active player, selection/option changes, loss of subscription, phone mode, ACC off or driver-door opening. Opening the Activity is not reported as confirmed playback. Apps can still require login and a queue.
- OEM music: `com.qinggan.media.usbmusic.LocalMediaBrowserService`; radio: `com.qinggan.media.radio.LocalRadioBrowserService`. Both live in `com.qinggan.media`, matching the OEM launcher's MediaBrowserHelper. Radio preference uses `com.qinggan.media#radio` to distinguish the sources.
- Door: existing CAN binder `ICanBusService.getDoorStatus`, transaction 2. Parcelable fields start with bonnet, front-left; front-left 0=closed/1=open/-1=unknown. These are not door-lock states. Read at 250 ms while either automation is enabled. Only a closed-to-open transition pauses active MediaSessions. Unknown/reconnected samples reset the baseline.
- Logs: `MusicAutomation`, `MusicDoor`, `MusicPlayReceiver`, existing `SteeringMedia` and `CAN`.

Validation: targeted Kotlin compilation of the automation, listener, preferences and CAN adapter; four transition-policy tests; layout binding ID/resource validation. Vehicle tests remain necessary: third-party cold start, OEM radio/music, driver/passenger doors, disabled wheel controls, second launch in the same boot and ACC sleep/wake.
