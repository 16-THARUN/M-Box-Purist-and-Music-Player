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
        maven { url = uri("https://jitpack.io") }
    }
}

rootProject.name = "MBoxPlayer"
include(":app")
include(":core:model")
include(":core:lyrics")
include(":core:audio")
include(":core:cast")
include(":core:ui")

project(":core").projectDir = file("shared/core")
project(":core:model").projectDir = file("shared/core/model")
project(":core:lyrics").projectDir = file("shared/core/lyrics")
project(":core:audio").projectDir = file("shared/core/audio")
project(":core:cast").projectDir = file("shared/core/cast")
project(":core:ui").projectDir = file("shared/core/ui")
