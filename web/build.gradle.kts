import org.gradle.kotlin.dsl.sourceSets

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.kotlin.plugin.serialization)
    alias(libs.plugins.project.tiptapWebview)
}

tiptapWebview {
    npmScript = "build"
    outputDirName = "dist"
    includeFiles = listOf("tiptap-editor-bridge.umd.js", "tiptap-bridge.css")
    copyInto = "src/jsMain/resources"
    hookTaskPrefixes = listOf("jsProcessResources", "compileKotlinJs")
}

val copyTiptapJsToBuild = tasks.register<Copy>("copyTiptapJsToBuild") {
    group = "build"
    description = "Copy UMD bridge into webpack output dir"
    dependsOn("copyTiptapBridgeAssets", "compileKotlinJs")
    from(layout.projectDirectory.dir("src/jsMain/resources")) {
        include("tiptap-editor-bridge.umd.js")
    }
    into(layout.buildDirectory.dir("js/packages/chat-multiplatform-web/kotlin"))
}

tasks.matching { it.name == "jsDevelopmentExecutableCompileSync" }.configureEach {
    dependsOn("copyTiptapJsToBuild")
}

kotlin {
    sourceSets.all {
        languageSettings.optIn("kotlin.time.ExperimentalTime")
        languageSettings.optIn("kotlin.uuid.ExperimentalUuidApi")
    }

    js {
        browser {}
        binaries.executable()
        compilerOptions {
            target.set("es2015")
        }
    }

    sourceSets {
        jsMain.dependencies {
            implementation(projects.core.common)
            implementation(projects.core.model)
            implementation(projects.core.network)
            implementation(projects.core.datastore)

            implementation(libs.koin.core.js)

            implementation(libs.kotlinx.browser)
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.kotlinx.datetime)
            implementation(libs.kotlinx.serialization.json)
            implementation(libs.ktor.client.core)
            implementation(libs.ktor.client.js)
            implementation(libs.ktor.client.content.negotiation)
            implementation(libs.ktor.serialization.kotlinx.json)
            implementation(libs.ktor.client.resources)

            implementation(kotlinWrappers.js)
            implementation(kotlinWrappers.react)
            implementation(kotlinWrappers.reactUse)
            implementation(kotlinWrappers.reactDom)
        }
    }
}
