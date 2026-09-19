# Bundled apps

Publish embeds these local APK inputs into the EXE; no download is performed on the user laptop.

Myket: user-supplied myket-1030.apk, version 10.3.0, package ir.mservices.market, minSdk 23.
Gboard: exported from the vehicle after installation via Myket, version 18.2.4.969776716-release-arm64-v8a, versionCode 175981774, minSdk 29, arm64-v8a. Includes base and config.xhdpi APKs, installed together using install-multiple. Hardware installation of this bundle remains to be tested.

Gboard is installed only; enable it in Voyah Iran. Myket automatically receives REQUEST_INSTALL_PACKAGES through appops with read-back verification. No extra permission dialog is shown for either bundled app.

To update, replace myket.apk with a standalone APK, or replace BOTH Gboard APKs with a matching exported set and Publish again.

myket.apk SHA256: 6b91654f52a2fac537a6121aa6d5083f005eab545435cf3062ee89dab163268f

gboard.apk SHA256: 01cccdcd340fc505a40787fd8f07905f482ef67259cfc2bd70520d9752962ec0

gboard-config.xhdpi.apk SHA256: e070eb605034adb67e97b08575b375e426eb1fa4c1b3956260f5f00611a4d3d3
