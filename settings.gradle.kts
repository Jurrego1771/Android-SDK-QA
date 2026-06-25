pluginManagement {
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
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
        // mavenLocal primero: para QA de ramas de trabajo del SDK (feature/fix), el binario se
        // buildea desde la rama con publishToMavenLocal (scripts/build-sdk-local.sh). Para versiones
        // publicadas, mavenLocal no tiene el artefacto y resuelve normalmente desde los repos de abajo.
        mavenLocal()
        google()
        mavenCentral()
        maven { url = uri("https://jitpack.io") }
        maven { url = uri("https://npaw.jfrog.io/artifactory/youbora/") }
        maven { url = uri("https://artifact.plugin.npaw.com/artifactory/plugins/android") }
    }
}

rootProject.name = "sdk-qa"
include(":app")
 