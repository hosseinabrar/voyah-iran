plugins { id("org.jetbrains.kotlin.jvm") }
group = "com.abrar"
version = "0.1.0"
kotlin { compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) } }
java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}
dependencies {
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20240303")
}

// A JVM host for the exact same recognizer used by Android's RahaService.
tasks.register("writeOldRahaClasspath") {
    dependsOn("jar")
    doLast {
        layout.buildDirectory.file("old-raha-classpath.txt").get().asFile.writeText(
            tasks.named<Jar>("jar").get().archiveFile.get().asFile.path + File.pathSeparator +
                configurations.runtimeClasspath.get().asPath)
    }
}
