plugins {
    `kotlin-dsl`
}

group = "com.github.woodsmarshes.chat.buildlogic"

dependencies {
    compileOnly(libs.android.gradlePlugin)
    compileOnly(libs.kotlin.gradlePlugin)
    compileOnly(libs.composeCompiler.gradlePlugin)
    // Runtime-scoped: RootPlugin applies SpotlessPlugin by id inside
    // subprojects, so the plugin must ship on the convention plugin's
    // runtime classpath (compileOnly would leave it unresolved there).
    implementation(libs.spotless.gradle.plugin)
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
        register("tiptapWebview") {
            id = libs.plugins.project.tiptapWebview.get().pluginId
            implementationClass = "TiptapWebviewPlugin"
        }
    }
}