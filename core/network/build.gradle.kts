import com.codingfeline.buildkonfig.compiler.FieldSpec

plugins {
    alias(libs.plugins.project.kotlinMultiplatform)
    alias(libs.plugins.kotlin.plugin.serialization)
    alias(libs.plugins.buildkonfig)
}

// 从项目根目录读取 network-config.properties，不存在则用默认值
val networkConfigFile = rootProject.file("network-config.properties")
val networkConfigProps = mutableMapOf(
    "host" to "127.0.0.1",
    "port" to "9051",
    "useTls" to "false",
)
if (networkConfigFile.exists()) {
    networkConfigFile.readLines().forEach { line ->
        val trimmed = line.trim()
        if (trimmed.isNotEmpty() && !trimmed.startsWith("#")) {
            val parts = trimmed.split("=", limit = 2)
            if (parts.size == 2) {
                networkConfigProps[parts[0].trim()] = parts[1].trim()
            }
        }
    }
}

buildkonfig {
    packageName = "com.github.woodsmarshes.chat.core.network"
    objectName = "NetworkBuildConfig"
    defaultConfigs {
        buildConfigField(FieldSpec.Type.STRING, "serverHost", networkConfigProps["host"]!!)
        buildConfigField(FieldSpec.Type.INT, "serverPort", networkConfigProps["port"]!!)
        buildConfigField(FieldSpec.Type.BOOLEAN, "serverUseTls", networkConfigProps["useTls"]!!)
    }
}

kotlin {
    if (project.extra["enableAndroid"] as Boolean) {
        extensions.configure<com.android.build.api.dsl.KotlinMultiplatformAndroidLibraryExtension> {
            namespace = "com.github.woodsmarshes.chat.core.network"
        }
    }

    sourceSets {
        commonMain {
            dependencies {
                api(projects.core.contract)

                implementation(projects.core.common)
                implementation(projects.core.model)
                implementation(projects.core.datastore)

                implementation(libs.kotlinx.coroutines.core)
                implementation(libs.kotlinx.datetime)
                implementation(libs.kotlinx.serialization.json)
                implementation(libs.kotlinx.serialization.protobuf)
                implementation(libs.kotlinx.io.core)
                implementation(libs.kotlinx.io.bytestring)
                implementation(libs.kotlinx.io.okio)

                implementation(ktorLibs.client.core)
                implementation(ktorLibs.client.contentNegotiation)
                implementation(ktorLibs.serialization.kotlinx.json)
                implementation(ktorLibs.serialization.kotlinx.protobuf)
                implementation(ktorLibs.client.logging)
                implementation(ktorLibs.client.websockets)
                implementation(ktorLibs.client.encoding)
                implementation(ktorLibs.client.bomRemover)
                implementation(ktorLibs.client.auth)
                implementation(ktorLibs.client.resources)
            }
        }

        commonTest {
            dependencies {
                implementation(libs.kotlin.test)
            }
        }

        jvmTest {
            dependencies {
                implementation(libs.kotlin.test)
                implementation(libs.kotlinx.coroutines.test)
                implementation(libs.mockk)
            }
        }

        matching { it.name == "androidMain" }.configureEach {
            dependencies {
                implementation(ktorLibs.client.okhttp)
            }
        }

        jvmMain {
            dependencies {
                implementation(ktorLibs.client.cio)
                implementation(ktorLibs.client.okhttp)
            }
        }

        webMain {
            dependencies {
                implementation(ktorLibs.client.js)
            }
        }
    }
}