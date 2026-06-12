package com.github.woodsmarshes.chat

import org.gradle.api.Project

/**
 * Extension to get Android SDK versions from version catalog
 */
internal fun Project.getAndroidSdkVersions(): AndroidSdkVersions {
    return AndroidSdkVersions(
        compileSdk = libs.findVersion("android-compileSdk").get().requiredVersion.toInt(),
        minSdk = libs.findVersion("android-minSdk").get().requiredVersion.toInt(),
        targetSdk = libs.findVersion("android-targetSdk").get().requiredVersion.toInt()
    )
}

/**
 * Data class to hold Android SDK version information
 */
data class AndroidSdkVersions(
    val compileSdk: Int,
    val minSdk: Int,
    val targetSdk: Int
)