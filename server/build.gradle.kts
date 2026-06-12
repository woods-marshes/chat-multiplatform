import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.ktor)
    alias(libs.plugins.kotlin.plugin.serialization)
}

group = "com.github.woodsmarshes.chat"
version = "0.0.1"

application {
    mainClass = "io.ktor.server.netty.EngineMain"
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_25)
    }

    sourceSets.all {
        languageSettings.optIn("kotlinx.serialization.ExperimentalSerializationApi")
        languageSettings.optIn("kotlin.uuid.ExperimentalUuidApi")
    }
}

ktor {
    development = true

    openApi {
        enabled = true
        codeInferenceEnabled = true
        onlyCommented = false
        debug = false
    }
}

//tasks.withType<ProcessResources> {
//    dependsOn(":web:jsBrowserDistribution")
//
//    val jsOutput = file("../web/build/dist/js/productionExecutable")
//    if (jsOutput.exists()) {
//        inputs.dir(jsOutput)
//    }
//
//    from(jsOutput.resolve("public")) {
//        into("web")
//    }
//
//    from(jsOutput.resolve("markdown")) {
//        into("web")
//    }
//
//    from(jsOutput) {
//        into("web")
//        include("*.js", "*.js.LICENSE.txt", "*.wasm")
//        exclude("*.js.map", "kobweb/**", "markdown/**", "public/**")
//    }
//
//    duplicatesStrategy = DuplicatesStrategy.WARN
//}

dependencies {
    implementation(projects.core.model)
    implementation(projects.core.network)

    implementation(libs.kotlinx.html)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.datetime)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.serialization.protobuf)

    implementation(libs.ktor.server.core)
    implementation(libs.ktor.serialization.kotlinx.json)
    implementation(libs.ktor.serialization.kotlinx.protobuf)
    implementation(libs.ktor.server.content.negotiation)
    implementation(libs.exposed.core)
    implementation(libs.exposed.jdbc)
    implementation(libs.h2)
    implementation(libs.postgresql)
    implementation(libs.ktor.server.websockets)
    implementation(libs.koin.ktor)
    implementation(libs.koin.logger.slf4j)
    implementation(libs.ktor.server.html.builder)
    implementation(libs.kotlin.css)
    implementation(libs.ktor.server.auth)
    implementation(libs.ktor.server.auth.jwt)
    implementation(libs.ktor.server.swagger)
    implementation(libs.ktor.server.partial.content)
    implementation(libs.ktor.server.openapi)
    implementation(libs.ktor.server.forwarded.header)
    implementation(libs.ktor.server.default.headers)
    implementation(libs.ktor.server.caching.headers)
    implementation(libs.ktor.server.compression)
    implementation(libs.ktor.server.double.receive)
    implementation(libs.ktor.server.auto.head.response)
    implementation(libs.ktor.server.host.common)
    implementation(libs.ktor.server.status.pages)
    implementation(libs.ktor.server.rate.limit)
    implementation(libs.ktor.server.request.validation)
    implementation(libs.ktor.server.resources)
    implementation(libs.ktor.server.sessions)
    implementation(libs.ktor.server.netty)
    implementation(libs.ktor.server.cors)
    implementation(libs.ktor.server.call.logging)
    implementation(libs.ktor.server.method.override)
    implementation(libs.ktor.server.routing.openapi)
    implementation(libs.ktor.server.di)
    implementation(libs.logback.classic)
    implementation(libs.ktor.server.config.yaml)
    testImplementation(libs.ktor.server.test.host)
    testImplementation(libs.kotlin.test.junit)
    testImplementation(libs.ktor.client.content.negotiation)

    implementation(libs.exposed.kotlin.datetime)
    implementation(libs.exposed.json)

    //Commons Codec - Password Hash
    implementation(libs.commons.codec)

    //BCrypt - Secure Password Hashing
    implementation(libs.bcrypt)

    //HikariCP
    implementation(libs.hikariCP)

    //jave2
    implementation(libs.jave.core)
    implementation(libs.jave.nativebin.linux64)
    implementation(libs.jave.nativebin.linux.arm64)
    implementation(libs.jave.nativebin.win64)

    implementation(libs.thumbnailator)

    implementation(libs.kotlin.result)
    implementation(libs.kotlin.result.coroutines)
}

val wsTest by tasks.registering(JavaExec::class) {
    description = "Run WebSocket messaging integration test"
    classpath = sourceSets.test.get().runtimeClasspath
    mainClass = "com.github.woodsmarshes.chat.WsMainKt"
}
