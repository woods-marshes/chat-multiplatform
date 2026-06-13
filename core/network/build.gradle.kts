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
    android {
        namespace = "com.github.woodsmarshes.chat.core.network"
    }

    sourceSets {
        commonMain {
            dependencies {
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

                implementation(libs.ktor.client.core)
                implementation(libs.ktor.client.content.negotiation)
                implementation(libs.ktor.serialization.kotlinx.json)
                implementation(libs.ktor.serialization.kotlinx.protobuf)
                implementation(libs.ktor.client.logging)
                implementation(libs.ktor.client.websockets)
                implementation(libs.ktor.client.encoding)
                implementation(libs.ktor.client.bom.remover)
                implementation(libs.ktor.client.auth)
                implementation(libs.ktor.client.resources)
            }
        }

        commonTest {
            dependencies {
                implementation(libs.kotlin.test)
            }
        }

        androidMain {
            dependencies {
                implementation(libs.ktor.client.okhttp)
            }
        }

        jvmMain {
            dependencies {
                implementation(libs.ktor.client.cio)
                implementation(libs.ktor.client.okhttp)
            }
        }

        webMain {
            dependencies {
                implementation(libs.ktor.client.js)
            }
        }
    }
}