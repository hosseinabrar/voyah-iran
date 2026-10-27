plugins { id("com.android.library") }
android {
    namespace = "com.abrar.raha.runtime"
    compileSdk = 36
    buildToolsVersion = "37.0.0"
    defaultConfig {
        minSdk = 26
        consumerProguardFiles("consumer-rules.pro")
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    androidResources { noCompress += listOf("zip", "onnx") }
}
dependencies {
    api(project(":command-core"))
    implementation("com.alphacephei:vosk-android:0.3.75@aar")
    implementation("net.java.dev.jna:jna:5.18.1@aar")
    // Each application packages this local native AAR; the reusable library only compiles against it.
    compileOnly(files("libs/sherpa-onnx-1.13.8.aar"))
    compileOnly(files("libs/onnxruntime-java-1.19.2-shared.aar"))
}
