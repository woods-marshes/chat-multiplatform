plugins {
    alias(libs.plugins.project.kotlinMultiplatform)
    alias(libs.plugins.project.composeMultiplatform)
    alias(libs.plugins.kotlin.plugin.serialization)
}

kotlin {
    if (project.extra["enableAndroid"] as Boolean) {
        extensions.configure<com.android.build.api.dsl.KotlinMultiplatformAndroidLibraryExtension> {
            namespace = "com.github.woodsmarshes.chat.core.navigation"
        }
    }

    sourceSets {
        commonMain {
            dependencies {

                api(libs.androidx.navigation3.ui)
                api(libs.androidx.navigationevent)
                api(libs.compose.material3.adaptiveNavigation3)
                api(libs.androidx.lifecycle.viewmodelNavigation3)
                api(libs.androidx.savedstate)
            }
        }
        commonTest {
            dependencies {
                implementation(libs.kotlin.test)
            }
        }
    }
}
