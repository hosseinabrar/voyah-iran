# DVR investigation — 2026-09-12

Static evidence from the supplied vendor image, not a live-car diagnosis.

APK: /vendor/app/Dvr-release-signed/Dvr-release-signed.apk
Decompiled source: work/dvr-source/smali_classes2/com/qinggan/dvr/

- AppMain.getCameraXConfig (AppMain.smali:94) restricts CameraX to camera ID "3" using DVRCameraFilter. This does not establish which physical camera is wired to that ID.
- common/SupportUtils.isSupportDvr requires VehicleConfigHelper.readOnlineConfigItem(VehicleOnlineState.DVR) == 1.
- activity/DVRActivity.onResume finishes the activity immediately when that condition is false (DVRActivity.smali:1772). This proves an application gate, not the launcher's filtering rule.
- common/CanBusHelper.onDVRStateChenaged reads getDvrSysState and getDvrSdcardState. System state 7 invokes showDvrCameraFaultToast. Logs include DVRApp_getDvrSysState and getDvrSdcardState (CanBusHelper.smali:1082 onward).
- showDvrCameraFaultToast shows resource 0x7f0f0075 when the activity is inactive and the warning has not already been shown. Exact correspondence to the user's remembered toast/status-bar icon is not yet confirmed.
- PreviewFragment$7 logs Camera in use, Max cameras in use, Stream config error, Camera disabled, Fatal error, etc. These are possible branches, not observed errors.
- /vendor/etc/init/vendor.qti.dvr@1.0-service.rc starts the native qti-dvr-hal-1-0 service.
- /vendor/bin/ais_v4l2loopback_config.xml maps input_id 3 to /dev/video54, 1920x1080 UYVY. Android camera ID and this input ID must not be assumed identical without HAL evidence.

Need live dumpsys media.camera, dumpsys package com.qinggan.dvr, service state and unfiltered logcat from the affected car. No camera ID, hardware configuration or APK has been changed. Two visible lenses alone do not establish DVR routing or hardware compatibility.
