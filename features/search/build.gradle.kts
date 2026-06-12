plugins {
    alias(libs.plugins.project.kotlinMultiplatform)
    alias(libs.plugins.project.composeMultiplatform)
    alias(libs.plugins.kotlin.plugin.serialization)
}

kotlin {
    android {
        namespace = "com.github.woodsmarshes.chat.feature.search"
    }

    sourceSets {
        commonMain.dependencies {
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
