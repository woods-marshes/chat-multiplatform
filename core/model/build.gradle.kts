plugins {
    alias(libs.plugins.project.kotlinMultiplatform)
    alias(libs.plugins.kotlin.plugin.serialization)
}

kotlin {
    android {
        namespace = "com.github.woodsmarshes.chat.core.model"
    }

    sourceSets {
        commonMain {
            dependencies {
                implementation(projects.core.common)

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
