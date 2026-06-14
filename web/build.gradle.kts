import org.gradle.kotlin.dsl.sourceSets

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.kotlin.plugin.serialization)
}

val buildTiptap = tasks.register<Exec>("buildTiptap") {
    group = "build"
    description = "执行 tiptap-bridge 的 npm 打包"

    val isWindows = org.gradle.internal.os.OperatingSystem.current().isWindows

    workingDir = layout.projectDirectory.dir("../tiptap-bridge").asFile

    if (isWindows) {
        commandLine("cmd", "/c", "npm run build")
    } else {
        commandLine("npm", "run", "build")
    }

    inputs.dir(layout.projectDirectory.dir("../tiptap-bridge/src"))
    inputs.file(layout.projectDirectory.file("../tiptap-bridge/package.json"))
    outputs.dir(layout.projectDirectory.dir("../tiptap-bridge/dist"))
}

val copyTiptapJs = tasks.register<Copy>("copyTiptapJs") {
    group = "build"
    description = "分发打包好的 UMD.js 文件到静态资源层（供 webpack 打包）"

    dependsOn(buildTiptap)

    from(layout.projectDirectory.dir("../tiptap-bridge/dist")) {
        include("tiptap-editor-bridge.umd.js")
    }
    into(layout.projectDirectory.dir("src/jsMain/resources"))
}


val copyTiptapCss = tasks.register<Copy>("copyTiptapCss") {
    group = "build"
    description = "分发打包好的 css 样式表到静态资源层"

    dependsOn(buildTiptap)

    from(layout.projectDirectory.dir("../tiptap-bridge/dist")) {
        include("tiptap-bridge.css")
    }
    into(layout.projectDirectory.dir("src/jsMain/resources"))
}

tasks.register("buildAndCopyTiptap") {
    group = "build"
    description = "一键自动打包并分发 Tiptap 编辑器组件"

    dependsOn(copyTiptapJs, copyTiptapCss)
}

tasks.matching { it.name.startsWith("jsProcessResources") }.configureEach {
    dependsOn("buildAndCopyTiptap")
}

tasks.matching { it.name == "compileKotlinJs" }.configureEach {
    dependsOn("copyTiptapJs")
}

val copyTiptapJsToBuild = tasks.register<Copy>("copyTiptapJsToBuild") {
    group = "build"
    description = "Copy UMD bridge into webpack output dir"
    dependsOn("copyTiptapJs", "copyTiptapCss", "compileKotlinJs")
    from(layout.projectDirectory.dir("src/jsMain/resources")) {
        include("tiptap-editor-bridge.umd.js")
    }
    into(layout.buildDirectory.dir("js/packages/chat-multiplatform-web/kotlin"))
}

tasks.matching { it.name == "jsDevelopmentExecutableCompileSync" }.configureEach {
    dependsOn("copyTiptapJsToBuild")
}

tasks.withType<org.jetbrains.kotlin.gradle.targets.js.webpack.KotlinWebpack>().configureEach {
    outputs.upToDateWhen { false }
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
            implementation(npm("@tiptap/core", "3.26.1"))
            implementation(npm("@tiptap/pm", "3.26.1"))
            implementation(npm("@tiptap/react", "3.26.1"))
            implementation(npm("@tiptap/starter-kit", "3.26.1"))
            implementation(npm("@tiptap/extension-placeholder", "3.26.1"))
            implementation(npm("@tiptap/static-renderer", "3.26.1"))
            implementation(npm("@floating-ui/dom", "1.7.6"))
        }
    }
}
