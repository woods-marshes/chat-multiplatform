import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.kotlin.multiplatform.library) apply false
    alias(libs.plugins.kotlin.plugin.serialization)
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.compose.compiler)
}

if (project.extra["enableAndroid"] as Boolean) {
    apply(plugin = libs.plugins.android.kotlin.multiplatform.library.get().pluginId)
}

kotlin {
    if (project.extra["enableAndroid"] as Boolean) {
        targets.withType<com.android.build.api.dsl.KotlinMultiplatformAndroidLibraryTarget>().configureEach {
            namespace = "com.github.woodsmarshes.chat.ui"
            compileSdk = libs.versions.android.compileSdk.get().toInt()
            minSdk = libs.versions.android.minSdk.get().toInt()

            androidResources {
                enable = true
            }

            withJava()

            compilerOptions {
                jvmTarget.set(JvmTarget.JVM_17)
            }
        }
    }

    // jvm / js / wasmJs targets are kept so the desktopApp and webApp entry
    // point modules can consume this shared module on those platforms.
    jvm {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_25)
        }
    }

    js {
        browser()
    }

    @OptIn(ExperimentalWasmDsl::class)
    wasmJs {
        browser()
    }

    sourceSets {
        matching { it.name == "androidMain" }.configureEach {
            dependencies {
                implementation(libs.compose.ui.tooling)
            }
        }
        commonMain.dependencies {
            implementation(libs.compose.runtime)
            implementation(libs.compose.foundation)
            implementation(libs.compose.material3)
            implementation(libs.compose.material3.adaptive)
            implementation(libs.compose.material3.adaptive.layout)
            implementation(libs.compose.material3.adaptiveNavigation3)
            implementation(libs.compose.material3.adaptive.navigation.suite)
            implementation(libs.compose.ui)
            implementation(libs.compose.components.resources)
            implementation(libs.compose.ui.tooling.preview)

            implementation(libs.androidx.lifecycle.viewmodelCompose)
            implementation(libs.androidx.lifecycle.runtimeCompose)
            implementation(libs.androidx.navigation3.ui)

            implementation(libs.koin.core)
            implementation(libs.koin.compose)
            implementation(libs.koin.compose.viewmodel)

            // Core modules
            implementation(projects.core.common)
            implementation(projects.core.ui)
            implementation(projects.core.navigation)
            implementation(projects.core.data)
            implementation(projects.core.model)
            implementation(projects.core.database)
            implementation(projects.core.datastore)
            implementation(projects.core.network)
            implementation(projects.core.domain)

            // Feature modules
            implementation(projects.features.auth)
            implementation(projects.features.conversations)
            implementation(projects.features.contacts)
            implementation(projects.features.chat)
            implementation(projects.features.profile)
            implementation(projects.features.settings)
            implementation(projects.features.article)
            implementation(projects.features.articleEditor)
            implementation(projects.features.search)

            // Not needed: composeApp uses core/* + features/*
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
        }
    }
}

compose.resources {
    // Public so the desktopApp / webApp entry point modules can reference the
    // generated Res class (e.g. the desktop window icon).
    publicResClass = true
    packageOfResClass = "com.github.woodsmarshes.chat.resources.composeApp"
    generateResClass = always
}

dependencies {
    if (project.extra["enableAndroid"] as Boolean) add("androidRuntimeClasspath", libs.compose.ui.tooling)
}
