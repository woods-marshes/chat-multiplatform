plugins {
    alias(libs.plugins.project.kotlinMultiplatform)
}

kotlin {
    if (project.extra["enableAndroid"] as Boolean) {
        extensions.configure<com.android.build.api.dsl.KotlinMultiplatformAndroidLibraryExtension> {
            namespace = "com.github.woodsmarshes.chat.core.common"
        }
    }

    sourceSets {
        commonMain.dependencies {
            api(libs.kotlinx.coroutines.core)
            api(libs.koin.core)

            api(libs.kotlin.logging)

            api(libs.kotlin.result)
            api(libs.kotlin.result.coroutines)
        }
        matching { it.name == "androidMain" }.configureEach {
            dependencies {
                api(libs.koin.android)
            }
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
            implementation(libs.kotlinx.coroutines.test)
        }
    }
}