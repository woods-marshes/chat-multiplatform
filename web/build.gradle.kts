import org.gradle.kotlin.dsl.sourceSets
import com.varabyte.kobweb.gradle.application.util.configAsKobwebApplication

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.kotlin.plugin.serialization)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.kobweb.application)
    alias(libs.plugins.kobwebx.markdown)
}

kobweb {
    app {
        index {
            description.set("Powered by Kobweb")
        }
    }
}

kotlin {
    sourceSets.all {
        languageSettings.optIn("kotlin.time.ExperimentalTime")
    }

    configAsKobwebApplication("example" /*, includeServer = true*/)
    @OptIn(org.jetbrains.kotlin.gradle.ExperimentalWasmDsl::class)
    js(IR) {
        browser {
//            commonWebpackConfig {
//                cssSupport {
//                    enabled = true
//                }
////                outputFileName = "app.js"
//                sourceMaps = true
//            }
//            testTask {
//                useKarma {
//                    useChromeHeadless()
//                }
//            }
        }
        binaries.executable()
        compilerOptions {
            target.set("es2015")
        }
    }

    sourceSets {
        jsMain.dependencies {
//            implementation(npm("htmx.org", "2.0.3"))
//            implementation(libs.kotlinx.browser)
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.kotlinx.serialization.json)

//            implementation(libs.doodle.browser)
//            implementation(libs.doodle.controls)
//            implementation(libs.doodle.animation)
//            implementation(libs.doodle.themes)

            implementation(libs.compose.runtime)
            implementation(libs.compose.html.core)
            implementation(libs.kobweb.core)
            implementation(libs.kobweb.silk)
            implementation(libs.kobwebx.serialization.kotlinx)
            // This default template uses built-in SVG icons, but what's available is limited.
            // Uncomment the following if you want access to a large set of font-awesome icons:
            implementation(libs.silk.icons.fa)
            implementation(libs.kobwebx.markdown)
        }
    }
}
