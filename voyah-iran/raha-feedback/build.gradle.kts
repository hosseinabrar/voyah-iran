plugins { id("com.android.library") }
android {
    namespace = "com.abrar.raha.feedback"
    compileSdk = 36
    buildToolsVersion = "37.0.0"
    defaultConfig { minSdk = 26 }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
}
dependencies {
    implementation("androidx.constraintlayout:constraintlayout:2.2.1")
    // Resolve this library independently with the same AndroidX versions as the app.
    // App dependencies do not participate in this module's own release/lint tasks.
    constraints {
        implementation("androidx.activity:activity:1.10.1")
        implementation("androidx.lifecycle:lifecycle-runtime:2.9.1")
        implementation("androidx.lifecycle:lifecycle-viewmodel:2.9.1")
        implementation("androidx.lifecycle:lifecycle-livedata:2.9.1")
    }
}
