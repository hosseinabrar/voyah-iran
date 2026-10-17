import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.FileSystemOperations
import javax.inject.Inject

plugins { id("com.android.application") }

abstract class PrepareProbeRuntime : DefaultTask() {
    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val runtimeFiles: ConfigurableFileCollection

    @get:OutputDirectory
    abstract val outputDirectory: DirectoryProperty

    @get:Inject
    abstract val fileSystemOperations: FileSystemOperations

    @TaskAction
    fun prepare() {
        fileSystemOperations.sync {
            from(runtimeFiles)
            into(outputDirectory)
        }
    }
}
android {
    namespace = "com.abrar.voyahaudiotest"
    compileSdk = 36
    buildToolsVersion = "37.0.0"
    defaultConfig {
        applicationId = "com.abrar.voyahaudiotest"
        minSdk = 28; targetSdk = 32; versionCode = 1; versionName = "0.1"
    }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
    lint { disable += "ExpiredTargetSdkVersion" }
}
val probeRuntime = tasks.register<PrepareProbeRuntime>("probeRuntime") {
    runtimeFiles.from(
        "../voyah-iran/deployment/roa/frida-inject",
        "../voyah-iran/deployment/roa/FRIDA-COPYING.txt"
    )
    outputDirectory.set(layout.buildDirectory.dir("probeRuntime"))
}
androidComponents {
    onVariants { variant ->
        variant.sources.assets?.addGeneratedSourceDirectory(probeRuntime) { it.outputDirectory }
    }
}
