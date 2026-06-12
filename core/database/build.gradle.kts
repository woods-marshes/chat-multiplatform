plugins {
    alias(libs.plugins.project.kotlinMultiplatform)
    alias(libs.plugins.kotlin.plugin.serialization)
    alias(libs.plugins.sqlDelight)
}

kotlin {
    android {
        namespace = "com.github.woodsmarshes.chat.core.database"
    }

    sourceSets {
        commonMain {
            dependencies {
                implementation(projects.core.common)
                implementation(projects.core.model)

                implementation(libs.kotlinx.serialization.json)

                implementation(libs.sqldelight.runtime)
                implementation(libs.sqldelight.coroutines.extensions)
                implementation(libs.sqldelight.primitive.adapters)
                implementation(libs.sqldelight.async.extensions)

                implementation(libs.androidx.paging.common)
                implementation(libs.sqldelight.androidx.paging3.extensions)
            }
        }

        commonTest {
            dependencies {
                implementation(libs.kotlin.test)
            }
        }

        androidMain {
            dependencies {
                implementation(libs.sqldelight.android.driver)
            }
        }

        jvmMain {
            dependencies {
                implementation(libs.sqldelight.sqlite.driver)
            }
        }

        webMain {
            dependencies {
                implementation(libs.sqldelight.web.driver)
                implementation(npm("@cashapp/sqldelight-sqljs-worker", libs.versions.sqlDelight.get()))
                implementation(npm("sql.js", libs.versions.sqlJs.get()))
                implementation(devNpm("copy-webpack-plugin", "9.1.0"))

                implementation(libs.kotlinx.browser)
            }
        }

    }
}

sqldelight {
    databases {
        register("ChatDatabase") {
            generateAsync = true
            packageName.set("io.github.woodsmarshes.chat.db")
            dialect("app.cash.sqldelight:sqlite-3-38-dialect:${libs.versions.sqlDelight.get()}")
        }
    }
}