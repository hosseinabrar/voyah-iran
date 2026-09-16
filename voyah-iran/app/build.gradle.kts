plugins { id("com.android.application") }

android {
    namespace = "com.abrar.voyahiran"
    compileSdk = 36
    buildToolsVersion = "37.0.0"
    defaultConfig {
        applicationId = "com.abrar.voyahiran"
        minSdk = 30
        targetSdk = 32
        versionCode = 12
        versionName = "0.2.6"
        buildConfigField("String", "SUBSCRIPTION_URL", "\"http://voyah-iran.ir/registerOrRenewSubscription\"")
        testInstrumentationRunner = "android.test.InstrumentationTestRunner"
    }
    buildFeatures { viewBinding = true; buildConfig = true }
    testOptions { unitTests.isIncludeAndroidResources = true }
    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            isDebuggable = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    lint {
        // Offline head-unit distribution. targetSdk is deliberately pinned to the tested platform contract.
        disable += "ExpiredTargetSdkVersion"
        warningsAsErrors = true
    }
}

dependencies {
    implementation(project(":raha-feedback"))
    implementation(project(":raha-runtime"))
    implementation(files("../raha-runtime/libs/sherpa-onnx-1.13.8.aar"))
    implementation(project(":command-core"))
    implementation(libs.androidx.activity)
    implementation(libs.androidx.lifecycle.viewmodel)
    implementation(libs.androidx.lifecycle.runtime)
    implementation(libs.androidx.constraintlayout)
    implementation(libs.kotlinx.coroutines.android)
    testImplementation(libs.junit)
    testImplementation("org.json:json:20240303")
    testImplementation(libs.robolectric)
}

