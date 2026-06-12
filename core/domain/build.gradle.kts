plugins {
    alias(libs.plugins.project.kotlinMultiplatform)
}

kotlin {
    android {
        namespace = "com.github.woodsmarshes.chat.core.domain"
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