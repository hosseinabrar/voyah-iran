plugins { id("com.android.application") }
android {
 namespace = "com.abrar.voyahclustertest"
 compileSdk = 36
 buildToolsVersion = "37.0.0"
 defaultConfig { applicationId = "com.abrar.voyahclustertest"; minSdk = 28; targetSdk = 28; versionCode = 2; versionName = "0.2.0" }
 compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
 buildTypes { release { isMinifyEnabled = false } }
}
