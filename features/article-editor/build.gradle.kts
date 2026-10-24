plugins {
    alias(libs.plugins.project.kotlinMultiplatform)
    alias(libs.plugins.project.composeMultiplatform)
    alias(libs.plugins.kotlin.plugin.serialization)
    alias(libs.plugins.project.tiptapWebview)
}

kotlin {
    if (project.extra["enableAndroid"] as Boolean) {
        extensions.configure<com.android.build.api.dsl.KotlinMultiplatformAndroidLibraryExtension> {
            namespace = "com.github.woodsmarshes.chat.feature.article_editor"
        }
    }

    sourceSets {
        commonMain {
            dependencies {
                implementation(projects.core.ui)
                implementation(projects.core.common)
                implementation(projects.core.data)
                implementation(projects.core.model)
                implementation(projects.core.network)
                implementation(projects.core.navigation)

                implementation(libs.kotlinx.coroutines.core)
                implementation(libs.kotlinx.serialization.json)
                implementation(libs.androidx.paging.compose)
            }
        }
        commonTest {
            dependencies {
                implementation(libs.kotlin.test)
                implementation(libs.kotlinx.coroutines.test)
            }
        }
        jvmMain.dependencies { implementation(libs.composewebview) }
        matching { it.name == "androidMain" }.configureEach { dependencies { implementation(libs.composewebview) } }
        wasmJsMain.dependencies { implementation(libs.composewebview) }
    }
}

compose.resources {
    publicResClass = true
    packageOfResClass = "com.github.woodsmarshes.chat.features.article_editor.resources"
    generateResClass = always
}

tiptapWebview {
    npmScript = "build:webview:editor"
    viteConfig = "vite.webview.editor.config.js"
    outputDirName = "dist-webview/editor"
    includeFiles = listOf("editor.html")
    copyInto = "src/commonMain/composeResources/files"
    hookTaskPrefixes = listOf("generateComposeResClass", "copyNonXmlValueResourcesForCommonMain")
}
