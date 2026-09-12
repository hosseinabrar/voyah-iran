plugins { id("com.android.application") }
android {
    namespace = "com.abrar.voyahvoicetest"
    compileSdk = 36
    buildToolsVersion = "37.0.0"
    defaultConfig {
        applicationId = "com.abrar.voyahvoicetest"
        minSdk = 26
        targetSdk = 36
        versionCode = 5
        versionName = "0.5.0"
        ndk { abiFilters += listOf("arm64-v8a", "armeabi-v7a", "x86_64", "x86") }
    }
    buildFeatures { viewBinding = true }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    androidResources { noCompress += listOf("zip", "onnx") }
}
dependencies {
    implementation(project(":command-core"))
    implementation("androidx.activity:activity-ktx:1.10.1")
    implementation("com.alphacephei:vosk-android:0.3.75@aar")
    implementation("net.java.dev.jna:jna:5.18.1@aar")
    implementation(files("libs/sherpa-onnx-1.13.8.aar"))
}
