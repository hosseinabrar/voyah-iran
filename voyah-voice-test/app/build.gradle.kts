plugins { id("com.android.application") }
android {
    namespace = "com.abrar.voyahvoicetest"
    compileSdk = 36
    buildToolsVersion = "37.0.0"
    defaultConfig {
        applicationId = "com.abrar.voyahvoicetest"
        minSdk = 26
        targetSdk = 36
        versionCode = 8
        versionName = "0.7.0"
        ndk { abiFilters += listOf("arm64-v8a", "armeabi-v7a", "x86_64", "x86") }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    androidResources { noCompress += listOf("zip", "onnx") }
}
dependencies {
    implementation(project(":command-core"))
    implementation("androidx.activity:activity-ktx:1.10.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.9.1")
    implementation(project(":raha-runtime"))
    implementation(files("../../voyah-iran/raha-runtime/libs/sherpa-onnx-1.13.8.aar"))
    implementation(files("../../voyah-iran/raha-runtime/libs/onnxruntime-java-1.19.2-shared.aar"))
}
