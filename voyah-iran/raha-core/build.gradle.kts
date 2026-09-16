plugins { id("org.jetbrains.kotlin.jvm") }
group = "com.abrar"
version = "0.1.0"
kotlin { compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) } }
java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}
dependencies { testImplementation("junit:junit:4.13.2") }
