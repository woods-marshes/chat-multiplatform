import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl
import org.jetbrains.kotlin.gradle.targets.js.webpack.KotlinWebpack

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.compose.compiler)
}

kotlin {
    js {
        browser()
        binaries.executable()
    }

    @OptIn(ExperimentalWasmDsl::class)
    wasmJs {
        browser()
        binaries.executable()
    }

    sourceSets {
        webMain.dependencies {
            implementation(projects.composeApp)
            implementation(projects.core.common)

            implementation(libs.compose.runtime)
            implementation(libs.compose.ui)
            implementation(libs.koin.core)

            implementation(npm("@cashapp/sqldelight-sqljs-worker", libs.versions.sqlDelight.get()))
            implementation(npm("sql.js", libs.versions.sqlJs.get()))
        }
    }
}

// Production minification of the full Compose web bundle exceeds the default
// Node.js heap; worker threads inherit --max-old-space-size from this value.
tasks.withType<KotlinWebpack>().configureEach {
    nodeArgs.add("--max-old-space-size=8192")
}
