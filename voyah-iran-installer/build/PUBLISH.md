# Publishing
Use the Release publish profile in Visual Studio with build enabled.
Before embedding the payload, Publish-Android.ps1 builds :app:assembleRelease,
protects the resulting APK, signs the new protection run and then Prepare-Payload.ps1
packs it. Any failure stops publication. Ordinary builds without a publish profile
only prepare the existing signed payload.

A masked desktop dialog requests the keystore password on each publish.
Leave the key password empty if it matches. Credentials are not written to source,
command arguments or configuration files. They are passed through the child process
environment and removed/restored when the pipeline ends.

Keep voyah-iran and voyah-iran-installer as siblings. The keystore defaults to
voyah-iran/Keystore/voyahiran, alias voyahiran. Copy the existing private keystore
securely to a new development machine; do not commit it.

Development machine requirements:
- Visual Studio with .NET 8 / WPF tooling.
- Android SDK packages and accepted licenses required by the Android project.
  Configure local.properties locally, or ANDROID_HOME / ANDROID_SDK_ROOT.
- Compatible JDK (the project's Gradle daemon currently specifies Java 25);
  JAVA_HOME or the standard Android Studio JBR installation.
- Python 3.10+ accessible through py/python/python3. The existing Codex runtime
  fallback is also supported.
- Installer tools/assets as required by Prepare-Payload.ps1.
- Network access for uncached Gradle dependencies and the initial DPT tool download.

No workstation-specific repository path is embedded in the publish scripts.
SDK paths in local.properties are machine configuration and must be updated on
a new machine. No Android SDK or compiler is bundled for developers.
