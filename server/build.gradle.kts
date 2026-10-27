import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(ktorLibs.plugins.ktor)
    alias(libs.plugins.kotlin.plugin.serialization)
}

group = "com.github.woodsmarshes.chat"
version = libs.versions.appVersion.get()

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

    fatJar {
        archiveFileName.set("fat.jar")
    }
}

val stageWebAssets = tasks.register<Sync>("stageWebAssets") {
    group = "build"
    description = "Stages the web client production bundle for packaging"

    // Keep the explicit edge: the fat jar embeds the web client, so packaging
    // must wait for the web build — but failures now surface here, on the
    // staging task, instead of masquerading as a server test failure.
    dependsOn(":web:jsBrowserDistribution")

    from(rootProject.layout.projectDirectory.dir("web/build/dist/js/productionExecutable")) {
        include("*.html", "*.css", "*.js", "*.js.LICENSE.txt", "*.wasm")
        exclude("*.js.map", "kobweb/**", "markdown/**", "public/**")
    }
    // Staged under a `static/` prefix so the staging root can go straight
    // onto a classpath — the SPA route reads `static/…` from the classpath.
    into(layout.buildDirectory.dir("generated/web-assets/static"))
}

// Ktor's fat jar is Shadow-based; assets live only in the shadow jar, so
// the test classpath (classes/resources dirs) never contains the web build
// and web failures surface in packaging, not as server test failures.
tasks.named<Jar>("shadowJar") {
    from(stageWebAssets) {
        into("static")
    }

    duplicatesStrategy = DuplicatesStrategy.WARN
}

// :server:run serves the SPA locally too: the staging root joins the run
// classpath directly (never sourceSets.main.output, which would leak the
// web build back into the test classpath).
tasks.named<JavaExec>("run") {
    dependsOn(stageWebAssets)
    classpath(layout.buildDirectory.dir("generated/web-assets"))
}

dependencies {
    implementation(projects.core.model)
    implementation(projects.core.contract)

    implementation(libs.kotlinx.html)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.datetime)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.serialization.protobuf)

    implementation(ktorLibs.server.core)
    implementation(ktorLibs.serialization.kotlinx.json)
    implementation(ktorLibs.serialization.kotlinx.protobuf)
    implementation(ktorLibs.server.contentNegotiation)
    implementation(ktorLibs.server.metrics.micrometer)
    implementation(ktorLibs.server.callId)
    implementation(libs.micrometer.registry.prometheus)
    implementation(ktorLibs.server.websockets)
    implementation(libs.koin.ktor)
    implementation(libs.koin.logger.slf4j)
    implementation(ktorLibs.server.htmlBuilder)
    implementation(libs.kotlin.css)
    implementation(ktorLibs.server.auth)
    implementation(ktorLibs.server.auth.jwt)
    implementation(ktorLibs.server.swagger)
    implementation(ktorLibs.server.partialContent)
    implementation(ktorLibs.server.openapi)
    implementation(ktorLibs.server.forwardedHeader)
    implementation(ktorLibs.server.defaultHeaders)
    implementation(ktorLibs.server.cachingHeaders)
    implementation(ktorLibs.server.compression)
    implementation(ktorLibs.server.doubleReceive)
    implementation(ktorLibs.server.autoHeadResponse)
    implementation(ktorLibs.server.statusPages)
    implementation(ktorLibs.server.rateLimit)
    implementation(ktorLibs.server.requestValidation)
    implementation(ktorLibs.server.resources)
    implementation(ktorLibs.server.sessions)
    implementation(ktorLibs.server.netty)
    implementation(ktorLibs.server.cors)
    implementation(ktorLibs.server.callLogging)
    implementation(ktorLibs.server.methodOverride)
    implementation(ktorLibs.server.routingOpenapi)
    implementation(ktorLibs.server.di)
    implementation(libs.logback.classic)
    implementation(ktorLibs.server.config.yaml)
    testImplementation(ktorLibs.server.testHost)
    testImplementation(libs.kotlin.test)
    testImplementation(libs.kotlin.test.junit)
    testImplementation(ktorLibs.client.contentNegotiation)
    testImplementation(ktorLibs.client.websockets)
    testImplementation(libs.exposed.migration.jdbc)
    testImplementation(libs.exposed.migration.core)
    testImplementation(libs.mockk)

    implementation(libs.h2)
    implementation(libs.postgresql)
    implementation(libs.exposed.core)
    implementation(libs.exposed.jdbc)
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
