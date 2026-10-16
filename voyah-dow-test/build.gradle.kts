plugins { id("com.android.application") }
android {
    namespace = "com.abrar.voyahdowtest"
    compileSdk = 36
    buildToolsVersion = "37.0.0"
    defaultConfig { applicationId = "com.abrar.voyahdowtest"; minSdk = 28; targetSdk = 32; versionCode = 1; versionName = "0.1" }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
    testOptions { unitTests.isIncludeAndroidResources = true }
    sourceSets["main"].kotlin.directories.add(file("../voyah-iran/app/src/main/java/com/abrar/voyahiran/feature/dow").path)
}
dependencies {
    testImplementation("junit:junit:4.13.2")
    testImplementation(libs.robolectric)
}
