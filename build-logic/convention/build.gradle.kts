plugins {
    `kotlin-dsl`
}

group = "com.github.woodsmarshes.chat.buildlogic"

dependencies {
    compileOnly(libs.android.gradlePlugin)
    compileOnly(libs.kotlin.gradlePlugin)
}

tasks {
    validatePlugins {
        enableStricterValidation = true
        failOnWarning = true
    }
}

gradlePlugin {
    plugins {
        register("kotlinMultiplatform") {
            id = libs.plugins.project.kotlinMultiplatform.get().pluginId
            implementationClass = "KotlinMultiplatformConventionPlugin"
        }
        register("composeMultiplatform"){
            id = libs.plugins.project.composeMultiplatform.get().pluginId
            implementationClass = "ComposeMultiplatformConventionPlugin"
        }
        register("androidApplication") {
            id = libs.plugins.project.android.application.get().pluginId
            implementationClass = "AndroidApplicationConventionPlugin"
        }
        register("root") {
            id = libs.plugins.project.root.get().pluginId
            implementationClass = "RootPlugin"
        }
    }
}