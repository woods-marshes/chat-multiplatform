plugins {
    alias(libs.plugins.project.kotlinMultiplatform)
    alias(libs.plugins.kotlin.plugin.serialization)
}

kotlin {
    if (project.extra["enableAndroid"] as Boolean) {
        extensions.configure<com.android.build.api.dsl.KotlinMultiplatformAndroidLibraryExtension> {
            namespace = "com.github.woodsmarshes.chat.core.model"
        }
    }

    sourceSets {
        commonMain {
            dependencies {

                implementation(libs.kotlinx.serialization.json)
                implementation(libs.kotlinx.serialization.protobuf)
            }
        }

        commonTest {
            dependencies {
                implementation(libs.kotlin.test)
            }
        }
    }
}
