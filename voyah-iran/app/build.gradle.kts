import java.util.Properties

plugins { id("com.android.application") }

val releaseMinify = providers.gradleProperty("voyah.minify").orNull?.let {
    it.toBooleanStrictOrNull() ?: error("voyah.minify must be true or false")
} ?: true

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
        versionCode = 21
        versionName = "3.0.0"
        buildConfigField("String", "NESHAN_MAP_KEY", neshanKey("neshan.mapKey", "NESHAN_MAP_KEY"))
        buildConfigField("String", "NESHAN_SEARCH_KEY", neshanKey("neshan.searchKey", "NESHAN_SEARCH_KEY"))
        buildConfigField("String", "SUBSCRIPTION_URL", "\"http://voyah-iran.ir/registerOrRenewSubscription\"")
        testInstrumentationRunner = "android.test.InstrumentationTestRunner"
    }
    buildFeatures { viewBinding = true; buildConfig = true }
    androidResources { noCompress += listOf("onnx", "sealed") }
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
            isMinifyEnabled = releaseMinify
            isShrinkResources = releaseMinify
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
    implementation(files("libs/hiddenapibypass-6.1.aar"))
    implementation("net.java.dev.jna:jna:5.18.1@aar")
    implementation(project(":raha-feedback"))
    implementation(project(":raha-runtime"))
    implementation(files("../raha-runtime/libs/sherpa-onnx-1.13.8.aar"))
    implementation(files("../raha-runtime/libs/onnxruntime-java-1.19.2-shared.aar"))
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

// Robolectric's older ASM cannot inspect the JDK 25 bundled with Android Studio.
// Only host-side test bytecode tooling is affected; no runtime dependency ships in the APK.
configurations.matching { it.name.endsWith("UnitTestRuntimeClasspath") }.configureEach {
    resolutionStrategy.force("org.ow2.asm:asm:9.9", "org.ow2.asm:asm-tree:9.9",
        "org.ow2.asm:asm-commons:9.9", "org.ow2.asm:asm-analysis:9.9", "org.ow2.asm:asm-util:9.9")
}

// Loaded inside the OEM process: never load our protected APK's ClassLoader there.
// Only these Android-only presentation widgets are exposed, not application logic.
val clusterSources = rootProject.file("cluster-renderer/src")
val clusterClasses = layout.buildDirectory.dir("cluster-renderer/classes")
val clusterAssets = layout.buildDirectory.dir("generated/cluster-renderer/assets")
val compileClusterRenderer by tasks.registering(JavaCompile::class) {
    source(fileTree(clusterSources) { include("**/*.java") })
    classpath = files(androidComponents.sdkComponents.bootClasspath)
    destinationDirectory.set(clusterClasses)
    sourceCompatibility = "1.8"
    targetCompatibility = "1.8"
}
val clusterRendererJar by tasks.registering(Jar::class) {
    dependsOn(compileClusterRenderer)
    from(clusterClasses)
    archiveFileName.set("cluster-renderer.jar")
    destinationDirectory.set(layout.buildDirectory.dir("cluster-renderer"))
}
val dexClusterRenderer by tasks.registering(JavaExec::class) {
    dependsOn(clusterRendererJar)
    val jar = clusterRendererJar.flatMap { it.archiveFile }
    val output = clusterAssets.map { it.dir("cluster/renderer") }
    classpath = files(androidComponents.sdkComponents.sdkDirectory.map { it.file("build-tools/${android.buildToolsVersion}/lib/d8.jar") })
    mainClass.set("com.android.tools.r8.D8")
    inputs.file(jar)
    inputs.files(androidComponents.sdkComponents.bootClasspath)
    outputs.dir(output)
    doFirst {
        output.get().asFile.mkdirs()
        args = listOf("--release", "--min-api", "28", "--lib", androidComponents.sdkComponents.bootClasspath.get().first().asFile.path,
            "--output", output.get().asFile.path, jar.get().asFile.path)
    }
}
android.sourceSets.getByName("main").assets.srcDir(clusterAssets.get().asFile)
android.sourceSets.getByName("test").java.srcDir(clusterSources)
tasks.configureEach {
    if (name.startsWith("merge") && name.endsWith("Assets")) dependsOn(dexClusterRenderer)
}

