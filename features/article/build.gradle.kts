plugins {
    alias(libs.plugins.project.kotlinMultiplatform)
    alias(libs.plugins.project.composeMultiplatform)
    alias(libs.plugins.kotlin.plugin.serialization)
}

kotlin {
    android {
        namespace = "com.github.woodsmarshes.chat.feature.article"
    }

    sourceSets {
        commonMain {
            dependencies {
                implementation(projects.core.ui)
                implementation(projects.core.common)
                implementation(projects.core.data)
                implementation(projects.core.domain)
                implementation(projects.core.model)
                implementation(projects.core.network)
                implementation(projects.core.navigation)

                implementation(libs.kotlinx.coroutines.core)
                implementation(libs.kotlinx.serialization.json)
                implementation(libs.androidx.paging.compose)
            }
        }
        jvmMain.dependencies { implementation(libs.composewebview) }
        androidMain.dependencies { implementation(libs.composewebview) }
        wasmJsMain.dependencies { implementation(libs.composewebview) }
    }
}

compose.resources {
    publicResClass = true
    packageOfResClass = "com.github.woodsmarshes.chat.features.article.resources"
    generateResClass = always
}

val buildTiptapViewerWebview = tasks.register<Exec>("buildTiptapViewerWebview") {
    group = "build"
    description = "执行 tiptap-bridge 的 npm 编译 (webview自包含单网页包)"

    val isWindows = org.gradle.internal.os.OperatingSystem.current().isWindows

    workingDir = layout.projectDirectory.dir("../../tiptap-bridge").asFile

    if (isWindows) {
        commandLine("cmd", "/c", "npm run build:webview:viewer")
    } else {
        commandLine("npm", "run", "build:webview:viewer")
    }

    inputs.dir(layout.projectDirectory.dir("../../tiptap-bridge/src"))
    inputs.file(layout.projectDirectory.file("../../tiptap-bridge/package.json"))
    inputs.file(layout.projectDirectory.file("../../tiptap-bridge/vite.webview.viewer.config.js"))
    outputs.dir(layout.projectDirectory.dir("../../tiptap-bridge/dist-webview/viewer"))
}

val copyTiptapWebviewToResources = tasks.register<Copy>("copyTiptapWebviewToResources") {
    group = "build"
    description = "分发自包含的 HTML 页面到本模块的 Compose Resources 目录"

    dependsOn(buildTiptapViewerWebview)

    from(layout.projectDirectory.dir("../../tiptap-bridge/dist-webview/viewer")) {
        include("viewer.html")
    }

    into(layout.projectDirectory.dir("src/commonMain/composeResources/files"))
}

tasks.matching { it.name.startsWith("generateComposeResClass") }.configureEach {
    dependsOn(copyTiptapWebviewToResources)
}
tasks.matching { it.name == "copyNonXmlValueResourcesForCommonMain" }.configureEach {
    dependsOn(copyTiptapWebviewToResources)
}
