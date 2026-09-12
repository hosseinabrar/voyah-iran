# Bundled apps

Publish embeds these local APK inputs into the EXE; no download is performed on the user laptop.

Myket: user-supplied myket-1030.apk, version 10.3.0, package ir.mservices.market, minSdk 23.
Gboard: user-downloaded APKMirror APK, version 15.2.08.736047990-release-arm64-v8a, minSdk 26; apksigner verify passed. Android <=32 signer SHA1 38918a453d07199354f8b19af05ec6562ced5788.

Gboard is installed only; enable it in Voyah Iran. Myket automatically receives REQUEST_INSTALL_PACKAGES through appops with read-back verification. No extra permission dialog is shown for either bundled app.

To update, replace myket.apk / gboard.apk with the corresponding standalone APK and Publish again.

myket.apk SHA256: 6b91654f52a2fac537a6121aa6d5083f005eab545435cf3062ee89dab163268f

gboard.apk SHA256: 8a14c7b2d21b006c14b1938d1fd08cba534ab97b5f426f559761f8fc79fa6aae
