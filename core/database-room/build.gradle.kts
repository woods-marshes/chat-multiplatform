plugins {
    alias(libs.plugins.project.kotlinMultiplatform)
    alias(libs.plugins.ksp)
    alias(libs.plugins.room3)
}

kotlin {
    android {
        namespace = "com.github.woodsmarshes.chat.core.database.room"
    }

    sourceSets {
        commonMain {
            dependencies {
                implementation(projects.core.common)
                implementation(projects.core.model)

                implementation(libs.room3.runtime)
                implementation(libs.room3.paging)
                implementation(libs.kotlinx.coroutines.core)
                implementation(libs.kotlinx.datetime)
                implementation(libs.kotlinx.serialization.json)
            }
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
        }
        androidMain.dependencies {
            implementation(libs.room3.sqlite.bundled)
        }
        jvmMain.dependencies {
            implementation(libs.room3.sqlite.bundled)
        }
    }
}

dependencies {
    add("kspJvm", libs.room3.compiler)
    add("kspAndroid", libs.room3.compiler)
    add("kspJs", libs.room3.compiler)
    add("kspWasmJs", libs.room3.compiler)
}

room3 {
    schemaDirectory(layout.projectDirectory.dir("schemas"))
}
