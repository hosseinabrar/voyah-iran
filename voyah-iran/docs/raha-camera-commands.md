# Camera command gating — 2026-09-23

Before launching or dispatching any camera command, OemCameraCapability reads VehicleOnlineState.DVR through the installed OEM SDK. Only 1 is accepted, matching com.qinggan.camera.common.SupportUtils.isSupportDvr(). Missing/unreadable/disabled configuration fails before camera launch. This does not enable hardware options or prove a camera sensor is healthy.

OPEN_CAMERA launches RecordVideoActivity and returns without process injection. Photo/start video/switch open the activity first, then the existing OEM camera adapter waits up to five seconds for an active visible activity. Photo/video retain OEM CameraVoiceGestureAction operations and reverse/standby/occupied-camera guards. Stop recording does not launch the activity. The camera-side adapter rechecks DVR support.

New SWITCH_CAMERA intent has empty parameters. It performs the installed camera_change_iv button's click on the main thread only when shown/enabled and not recording. RecordVideoActivity.onClick handles changeClick and updateCameraView. No guessed numeric camera ID or direct sensor override. Photo/video/switch/stop still use the existing camera process injection adapter; hardware validation of that path is pending. Success logs for these are dispatch acknowledgement, not proof a media file was saved.

Twenty phrase examples cover open, photo, video start/stop and switch, including بازکردن دوربین, برنامه دوربین رو باز کن, یه عکس بگیر, گرفتن عکس, دوربین رو عوض کن. Fourteen focused JUnit tests passed; changed bridge/vehicle-plan Kotlin compiled and generated JS syntax checked. Install updated app and bridge together through the installer. Release build not run.
