pluginManagement { repositories { google(); mavenCentral(); gradlePluginPortal() } }
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories { google(); mavenCentral() }
}
rootProject.name = "VoyahVoiceTest"
include(":app")
include(":command-core", ":raha-runtime")
project(":command-core").projectDir = file("../voyah-iran/raha-core")
project(":raha-runtime").projectDir = file("../voyah-iran/raha-runtime")
