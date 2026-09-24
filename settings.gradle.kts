import java.util.Properties

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

        versionCatalogs {
            create("kotlinWrappers") {
                val wrappersVersion = "2026.5.7"
                from("org.jetbrains.kotlin-wrappers:kotlin-wrappers-catalog:$wrappersVersion")
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

include(":web")
include(":server")
include(":composeApp")
// Temporary workaround for KTIJ-39840. Keep full builds enabled by default.
// Explicit Gradle properties take precedence over project-local options.
val localBuildProperties = Properties()
val localBuildPropertiesFile = file("local-build.properties")
if (localBuildPropertiesFile.isFile) {
    localBuildPropertiesFile.inputStream().use { localBuildProperties.load(it) }
}
fun buildBoolean(name: String, default: Boolean): Boolean {
    val value = providers.gradleProperty(name)
        .orElse(localBuildProperties.getProperty(name) ?: default.toString()).get()
    return value.toBooleanStrictOrNull() ?: error("$name must be 'true' or 'false'.")
}

val enableAndroid = buildBoolean("enableAndroid", true)
val includeAndroidApp = buildBoolean("includeAndroidApp", true)
gradle.beforeProject {
    extra["enableAndroid"] = enableAndroid
}
if (enableAndroid && includeAndroidApp) {
    include(":androidApp")
}
if (!enableAndroid) {
    logger.lifecycle("Android targets disabled (enableAndroid=false). Use -PenableAndroid=true to enable them.")
}

include(":desktopApp")
include(":webApp")

include(":core:common")
include(":core:data")
include(":core:database")
include(":core:database-room")
include(":core:datastore")
include(":core:domain")
include(":core:model")
include(":core:network")
include(":core:network-api")
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
