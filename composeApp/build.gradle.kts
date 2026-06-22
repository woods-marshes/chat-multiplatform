import org.jetbrains.compose.desktop.application.dsl.TargetFormat
import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.kotlin.multiplatform.library)
    alias(libs.plugins.kotlin.plugin.serialization)
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.compose.compiler)
}

kotlin {
    android {
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

    jvm {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_25)
        }
    }

    js {
        browser()
        binaries.executable()
    }

    @OptIn(ExperimentalWasmDsl::class)
    wasmJs {
        browser()
        binaries.executable()
    }

    sourceSets {
        androidMain.dependencies {
            implementation(libs.compose.ui.tooling)
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
        jvmMain.dependencies {
            implementation(compose.desktop.currentOs)
            implementation(libs.compose.ui.tooling)
            implementation(libs.kotlinx.coroutines.swing)
            implementation(libs.logback.classic)
        }
        webMain.dependencies {
            implementation(npm("@cashapp/sqldelight-sqljs-worker", libs.versions.sqlDelight.get()))
            implementation(npm("sql.js", libs.versions.sqlJs.get()))
        }
    }
}

compose.resources {
    publicResClass = false
    packageOfResClass = "com.github.woodsmarshes.chat.resources.composeApp"
    generateResClass = always
}

dependencies {
    "androidRuntimeClasspath"(libs.compose.ui.tooling)
}

compose.desktop {
    application {
        mainClass = "com.github.woodsmarshes.chat.MainKt"

        jvmArgs += "--enable-native-access=ALL-UNNAMED"

        nativeDistributions {
            jvmArgs.add("--add-exports=java.base/sun.misc=ALL-UNNAMED")
            jvmArgs.add("--add-exports=java.base/sun.nio.ch=ALL-UNNAMED")
            targetFormats(TargetFormat.Dmg, TargetFormat.Msi, TargetFormat.Deb)
            packageName = "com.github.woodsmarshes.chat"
            packageVersion = "1.0.0"

            macOS {
                iconFile.set(project.file("icons/icon.icns"))
            }
            windows {
                iconFile.set(project.file("icons/icon.ico"))
            }
            linux {
                iconFile.set(project.file("icons/icon.png"))
            }
        }
    }
}
