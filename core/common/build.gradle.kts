plugins {
    alias(libs.plugins.project.kotlinMultiplatform)
}

kotlin {
    android {
        namespace = "com.github.woodsmarshes.chat.core.common"
    }

    sourceSets {
        commonMain.dependencies {
            api(libs.kotlinx.coroutines.core)
            api(libs.koin.core)

            api(libs.kotlin.logging)

            api(libs.kotlin.result)
            api(libs.kotlin.result.coroutines)
        }
        androidMain.dependencies {
            api(libs.koin.android)
        }
    }
}