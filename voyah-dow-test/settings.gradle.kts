// Used only when opening this directory directly; the main build supplies
// these settings itself when this project is included as :dow-test.
pluginManagement {
    repositories { google(); mavenCentral(); gradlePluginPortal() }
    plugins {
        id("com.android.application") version "9.3.1"
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories { google(); mavenCentral() }
    versionCatalogs {
        create("libs") { from(files("../voyah-iran/gradle/libs.versions.toml")) }
    }
}
rootProject.name = "dow-test"
