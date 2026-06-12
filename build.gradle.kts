plugins {
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.multiplatform) apply false
    alias(libs.plugins.compose.hotReload) apply false
    alias(libs.plugins.compose.multiplatform) apply false
    alias(libs.plugins.compose.compiler) apply false
    alias(libs.plugins.ktor) apply false
    alias(libs.plugins.kotlin.plugin.serialization) apply false
    alias(libs.plugins.sqlDelight) apply false
    alias(libs.plugins.android.kotlin.multiplatform.library) apply false
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.room) apply false
    alias(libs.plugins.room3) apply false
    alias(libs.plugins.android.lint) apply false
    alias(libs.plugins.project.root)
}

buildscript {
    dependencies {
        // For KGP
        classpath(libs.kotlin.gradlePlugin)

        // For KSP
        classpath(libs.kotlin.symbol.processing.gradlePlugin)
    }
}