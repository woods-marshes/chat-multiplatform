rootProject.name = "chat-multiplatform"
enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")

pluginManagement {
    includeBuild("build-logic")
    repositories {
        google {
            mavenContent {
                includeGroupAndSubgroups("androidx")
                includeGroupAndSubgroups("com.android")
                includeGroupAndSubgroups("com.google")
            }
        }
        mavenCentral()
        gradlePluginPortal()
        maven("https://maven.pkg.jetbrains.space/public/p/compose/dev")
    }
}

dependencyResolutionManagement {
    repositories {
        google {
            mavenContent {
                includeGroupAndSubgroups("androidx")
                includeGroupAndSubgroups("com.android")
                includeGroupAndSubgroups("com.google")
            }
        }
        mavenCentral()
        maven("https://packages.confluent.io/maven/")
        maven("https://maven.pkg.jetbrains.space/kotlin/p/kotlin/kotlin-js-wrappers")
        maven("https://maven.pkg.jetbrains.space/public/p/compose/dev")
        maven("https://jitpack.io")
        maven("https://maven.pkg.jetbrains.space/public/p/ktor/eap")
        mavenLocal()
    }
}

plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

include(":server")
include(":web")

include(":composeApp")
include(":androidApp")

include(":core:common")
include(":core:data")
include(":core:database")
include(":core:database-room")
include(":core:datastore")
include(":core:domain")
include(":core:model")
include(":core:network")
include(":core:ui")
include(":core:navigation")

include(":features:auth")
include(":features:chat")
include(":features:contacts")
include(":features:conversations")
include(":features:profile")
include(":features:search")
include(":features:settings")
