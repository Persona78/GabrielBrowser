pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}
plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        // GeckoView (motor do Firefox) é publicado aqui
        maven { url = uri("https://maven.mozilla.org/maven2/") }
    }
}

rootProject.name = "GabrielBrowser"
include(":app")
