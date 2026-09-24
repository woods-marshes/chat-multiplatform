plugins {
    alias(libs.plugins.project.kotlinMultiplatform)
}

kotlin {
    if (project.extra["enableAndroid"] as Boolean) {
        extensions.configure<com.android.build.api.dsl.KotlinMultiplatformAndroidLibraryExtension> {
            namespace = "com.github.woodsmarshes.chat.core.data"
        }
    }

    sourceSets {
        commonMain {
            dependencies {
                implementation(projects.core.model)
                implementation(projects.core.common)
                implementation(projects.core.network)
                implementation(projects.core.database)
                implementation(projects.core.datastore)

                implementation(libs.androidx.paging.common)
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