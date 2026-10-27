import java.io.File

plugins { id("com.android.library") }
val sealedRoot = layout.buildDirectory.dir("generated/rahaSealed")
val sealRahaAssets = tasks.register("sealRahaAssets") {
    val source = layout.projectDirectory.dir("src/main/assets")
    val codec = project(":command-core").file("src/main/java/com/abrar/raha/intent/RahaSealedCodec.java")
    val tool = file("tools/SealRahaAssets.java")
    inputs.dir(source); inputs.files(codec, tool)
    outputs.dir(sealedRoot)
    doLast {
        val output = sealedRoot.get().asFile
        // Only generated task outputs are replaced. Source/training assets stay intact.
        delete(output); output.mkdirs()
        val classes = output.resolve("tool").apply { mkdirs() }
        val suffix = if (System.getProperty("os.name").startsWith("Windows")) ".exe" else ""
        fun runTool(vararg command: String) {
            check(ProcessBuilder(*command).inheritIO().start().waitFor() == 0) { "Raha asset sealing failed" }
        }
        val bin = File(System.getProperty("java.home"), "bin")
        runTool(bin.resolve("javac$suffix").path,"--release","17","-d",classes.path,codec.path,tool.path)
        runTool(bin.resolve("java$suffix").path,"-cp",classes.path,"SealRahaAssets",source.asFile.path,
            output.resolve("assets").path,output.resolve("java").path)
    }
}
tasks.named("preBuild").configure { dependsOn(sealRahaAssets) }
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
    androidResources { noCompress += listOf("zip", "onnx", "sealed") }
}
extensions.configure<com.android.build.api.dsl.LibraryExtension> {
    sourceSets.getByName("main") {
        assets.setSrcDirs(listOf(sealedRoot.get().dir("assets").asFile))
        java.srcDir(sealedRoot.get().dir("java").asFile)
    }
}
dependencies {
    api(project(":command-core"))
    implementation("com.alphacephei:vosk-android:0.3.75@aar")
    implementation("net.java.dev.jna:jna:5.18.1@aar")
    // Each application packages this local native AAR; the reusable library only compiles against it.
    compileOnly(files("libs/sherpa-onnx-1.13.8.aar"))
    compileOnly(files("libs/onnxruntime-java-1.19.2-shared.aar"))
}
