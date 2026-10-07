import java.util.Properties

plugins { id("com.android.application") }

val mapLocalProperties = Properties().apply {
    rootProject.file("local.properties").takeIf { it.isFile }?.inputStream()?.use { load(it) }
}
fun neshanKey(property: String, environment: String): String {
    val value = providers.gradleProperty(property).orNull ?: providers.environmentVariable(environment).orNull
        ?: mapLocalProperties.getProperty(property, "")
    return "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "").replace("\r", "") + "\""
}

android {
    namespace = "com.abrar.voyahiran"
    compileSdk = 36
    buildToolsVersion = "37.0.0"
    defaultConfig {
        applicationId = "com.abrar.voyahiran"
        minSdk = 28
        targetSdk = 32
        versionCode = 15
        versionName = "2.0.0"
        buildConfigField("String", "NESHAN_MAP_KEY", neshanKey("neshan.mapKey", "NESHAN_MAP_KEY"))
        buildConfigField("String", "NESHAN_SEARCH_KEY", neshanKey("neshan.searchKey", "NESHAN_SEARCH_KEY"))
        buildConfigField("String", "SUBSCRIPTION_URL", "\"http://voyah-iran.ir/registerOrRenewSubscription\"")
        testInstrumentationRunner = "android.test.InstrumentationTestRunner"
    }
    buildFeatures { viewBinding = true; buildConfig = true }
    testOptions { unitTests.isIncludeAndroidResources = true }
    // Publish credentials live only in the invoking process environment.
    val publishStore = System.getenv("VI_PUBLISH_KEYSTORE")
    if (!publishStore.isNullOrBlank()) {
        signingConfigs.create("installerPublish") {
            storeFile = file(publishStore)
            storePassword = System.getenv("VI_DPT_STORE_PASSWORD")
            keyAlias = System.getenv("VI_PUBLISH_ALIAS")
            keyPassword = System.getenv("VI_DPT_KEY_PASSWORD")
        }
    }
    buildTypes {
        release {
            if (!publishStore.isNullOrBlank()) {
                signingConfig = signingConfigs.getByName("installerPublish")
            }
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
    implementation("net.java.dev.jna:jna:5.18.1@aar")
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

