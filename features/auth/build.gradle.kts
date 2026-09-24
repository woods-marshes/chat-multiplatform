plugins {
    alias(libs.plugins.project.kotlinMultiplatform)
    alias(libs.plugins.project.composeMultiplatform)
    alias(libs.plugins.kotlin.plugin.serialization)
}

kotlin {
    if (project.extra["enableAndroid"] as Boolean) {
        extensions.configure<com.android.build.api.dsl.KotlinMultiplatformAndroidLibraryExtension> {
            namespace = "com.github.woodsmarshes.chat.feature.auth"
        }
    }

    sourceSets {
        commonMain {
            dependencies {
                implementation(projects.core.ui)
                implementation(projects.core.common)
                implementation(projects.core.data)
                implementation(projects.core.domain)
                implementation(projects.core.model)
                implementation(projects.core.navigation)

                implementation(libs.kotlinx.coroutines.core)
            }
        }
    }
}