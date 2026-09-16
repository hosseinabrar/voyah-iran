pluginManagement {
    repositories { google(); mavenCentral(); gradlePluginPortal() }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories { google(); mavenCentral() }
}
rootProject.name = "VoyahIran"
include(":app", ":command-core", ":raha-runtime")
project(":command-core").projectDir = file("raha-core")

include(":raha-feedback")
