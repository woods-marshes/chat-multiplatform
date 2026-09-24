import dev.nucleusframework.desktop.application.dsl.TargetFormat
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.nucleus)
}

kotlin {
    target {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_25)
        }
    }
}

dependencies {
    implementation(projects.composeApp)
    implementation(projects.core.common)

    implementation(compose.desktop.currentOs)
    implementation(libs.compose.components.resources)
    implementation(libs.compose.ui.tooling)
    implementation(libs.kotlinx.coroutines.swing)
    implementation(libs.logback.classic)
    implementation(libs.koin.core)

    // Nucleus Tao windowing stack: required to host the native desktop
    // WebView (dev.nucleusframework:composewebview) used by the article
    // features on JVM.
    implementation(libs.nucleus.application)
    implementation(libs.nucleus.decorated.window.tao)
    implementation(libs.nucleus.core.runtime)
}

nucleus.application {
    mainClass = "com.github.woodsmarshes.chat.MainKt"

    jvmArgs(
        "--enable-native-access=ALL-UNNAMED",
        "--add-exports=java.base/sun.misc=ALL-UNNAMED",
        "--add-exports=java.base/sun.nio.ch=ALL-UNNAMED",
    )

    nativeDistributions {
        targetFormats(TargetFormat.Dmg, TargetFormat.Msi, TargetFormat.Deb)
        appName = "Chat"
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
