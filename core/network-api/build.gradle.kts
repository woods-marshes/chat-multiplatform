plugins {
    alias(libs.plugins.project.kotlinMultiplatform)
    alias(libs.plugins.kotlin.plugin.serialization)
}

kotlin {
    if (project.extra["enableAndroid"] as Boolean) {
        extensions.configure<com.android.build.api.dsl.KotlinMultiplatformAndroidLibraryExtension> {
            namespace = "com.github.woodsmarshes.chat.core.network.api"
        }
    }

    sourceSets {
        commonMain {
            dependencies {
                api(projects.core.model)
                api(libs.kotlinx.serialization.json)
                api(libs.kotlinx.serialization.protobuf)
                api(libs.ktor.resources)
            }
        }

        commonTest {
            dependencies {
                implementation(libs.kotlin.test)
            }
        }
    }
}
