import java.util.Properties

rootProject.name = "chat-multiplatform"
enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")

val localProperties = Properties().apply {
    val localPropertiesFile = rootDir.resolve("local.properties")
    if (localPropertiesFile.exists()) {
        localPropertiesFile.inputStream().use { load(it) }
    }
}

val gprUser: String? = localProperties.getProperty("gpr.user")
    ?: providers.gradleProperty("gpr.user").orNull
    ?: System.getenv("GITHUB_ACTOR")

val gprKey: String? = localProperties.getProperty("gpr.key")
    ?: providers.gradleProperty("gpr.key").orNull
    ?: System.getenv("GITHUB_TOKEN")

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

        versionCatalogs {
            create("kotlinWrappers") {
                val wrappersVersion = "2026.5.7"
                from("org.jetbrains.kotlin-wrappers:kotlin-wrappers-catalog:$wrappersVersion")
            }
        }

        mavenCentral()
        maven {
            url = uri("https://maven.pkg.github.com/woods-marshes/ComposeNativeWebview")
            credentials {
                username = gprUser
                password = gprKey
            }
        }
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

include(":web")
include(":server")
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
include(":features:article")
include(":features:article-editor")
