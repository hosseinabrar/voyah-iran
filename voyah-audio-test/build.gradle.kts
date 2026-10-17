plugins { id("com.android.application") }
android {
    namespace = "com.abrar.voyahaudiotest"
    compileSdk = 36
    buildToolsVersion = "37.0.0"
    defaultConfig {
        applicationId = "com.abrar.voyahaudiotest"
        minSdk = 28; targetSdk = 32; versionCode = 1; versionName = "0.1"
    }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
    lint { disable += "ExpiredTargetSdkVersion" }
    sourceSets["main"].assets.srcDir(layout.buildDirectory.dir("probeRuntime"))
}
val probeRuntime by tasks.registering(Sync::class) {
    from("../voyah-iran/deployment/roa") { include("frida-inject", "FRIDA-COPYING.txt") }
    into(layout.buildDirectory.dir("probeRuntime"))
}
tasks.named("preBuild") { dependsOn(probeRuntime) }
