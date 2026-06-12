plugins {
    alias(libs.plugins.project.kotlinMultiplatform)
    alias(libs.plugins.project.composeMultiplatform)
    alias(libs.plugins.ksp)
}

kotlin {
    android {
        namespace = "com.github.woodsmarshes.chat.core.ui"
    }

    sourceSets {
        commonMain {
            dependencies {
                implementation(projects.core.common)

                api(libs.lyricist)

                api(libs.compose.runtime)
                api(libs.compose.foundation)
                api(libs.compose.animation)
                api(libs.compose.components.resources)
                api(libs.compose.ui)
                api(libs.compose.ui.tooling.preview)
                api(libs.compose.material.icons.extended)
                api(libs.compose.material3)
                api(libs.compose.material3.adaptive)
                api(libs.compose.material3.adaptive.layout)

                api(libs.koin.compose)
                api(libs.koin.compose.viewmodel)

                api(libs.androidx.lifecycle.runtimeCompose)
                api(libs.androidx.lifecycle.viewmodelCompose)

                api(libs.androidx.window)

                api(libs.kotlinx.coroutines.core)
                api(libs.kotlinx.datetime)

                // Core model for shared UI types
                api(projects.core.model)

                // Miuix design system
                api(libs.miuix.ui)
                api(libs.miuix.icons)

                // Coil 3 — KMP image loading
                api(libs.coil.compose)
                api(libs.coil.network.ktor3)
            }
        }

        commonTest {
            dependencies {
                implementation(libs.kotlin.test)
            }
        }

        androidMain {
            dependencies {
                implementation(libs.androidx.activity.compose)
            }
        }
    }
}

compose.resources {
    publicResClass = true
    packageOfResClass = "com.github.woodsmarshes.chat.resources"
    generateResClass = always
}

dependencies {
    add("kspCommonMainMetadata", libs.lyricist.processor.xml)
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompilationTask<*>>().configureEach {
    if (name != "kspCommonMainKotlinMetadata") {
        dependsOn("kspCommonMainKotlinMetadata")
    }
}

kotlin.sourceSets.commonMain {
    kotlin.srcDir("build/generated/ksp/metadata/commonMain/kotlin")
}

ksp {
    arg("lyricist.packageName", "com.github.woodsmarshes.chat.lyricist")
    arg("lyricist.moduleName", "ui")
    arg("lyricist.xml.resourcesPath", project.projectDir.resolve("src/commonMain/composeResources").absolutePath)
    arg("lyricist.xml.defaultLanguageTag", "zh")
}

afterEvaluate {
    tasks.matching { it.name == "kspCommonMainKotlinMetadata" }.configureEach {
        val stringsFile = layout.buildDirectory
            .file("generated/ksp/metadata/commonMain/kotlin/com/github/woodsmarshes/chat/lyricist/Strings.kt")
            .get().asFile
        val zhStringsFile = layout.buildDirectory
            .file("generated/ksp/metadata/commonMain/kotlin/com/github/woodsmarshes/chat/lyricist/ZhStrings.kt")
            .get().asFile
        doLast {
            if (stringsFile.exists()) {
                var content = stringsFile.readText()
                content = content.replace("public val Strings: Map<LanguageTag, Strings>", "public val stringResources: Map<LanguageTag, Strings>")
                content = content.replace("rememberStrings(Strings,", "rememberStrings(stringResources,")
                content = content.replace("return Strings[", "return stringResources[")
                stringsFile.writeText(content)
            }
            if (zhStringsFile.exists()) {
                var content = zhStringsFile.readText()
                // Replace JVM-only String.format() with KMP-compatible replace() for %s placeholders
                // Handles both single-line and multi-line format() calls via RegexOption.DOT_MATCHES_ALL
                content = content.replace(Regex("\"([^\"]*)\"\\s*\\.format\\(([^)]+)\\)", RegexOption.DOT_MATCHES_ALL)) { match ->
                    val template = match.groupValues[1]
                    val args = match.groupValues[2].replace("\\s+".toRegex(), "").split(",").map { it.trim() }
                    if (args.size == 1) {
                        "\"$template\".replace(\"%s\", ${args[0]})"
                    } else {
                        val chain = args.joinToString("") { ".replace(\"%s\", $it)" }
                        "\"$template\"$chain"
                    }
                }
                zhStringsFile.writeText(content)
            }
        }
    }
}

kotlin {
    composeCompiler {
        stabilityConfigurationFiles.add(
            rootProject.layout.projectDirectory.file("stability_config.conf")
        )
    }
}
