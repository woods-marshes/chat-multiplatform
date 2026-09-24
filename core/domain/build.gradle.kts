plugins {
    alias(libs.plugins.project.kotlinMultiplatform)
}

kotlin {
    if (project.extra["enableAndroid"] as Boolean) {
        extensions.configure<com.android.build.api.dsl.KotlinMultiplatformAndroidLibraryExtension> {
            namespace = "com.github.woodsmarshes.chat.core.domain"
        }
    }

    sourceSets {
        commonMain {
            dependencies {
                implementation(projects.core.common)
                implementation(projects.core.model)
                implementation(libs.kotlinx.coroutines.core)
                implementation(libs.kotlin.result)
                implementation(libs.kotlin.result.coroutines)
                implementation(libs.androidx.paging.common)
            }
        }

        commonTest {
            dependencies {
                implementation(libs.kotlin.test)
            }
        }
    }
}