import com.codingfeline.buildkonfig.compiler.FieldSpec

plugins {
    alias(libs.plugins.project.kotlinMultiplatform)
    alias(libs.plugins.project.composeMultiplatform)
    alias(libs.plugins.kotlin.plugin.serialization)
    alias(libs.plugins.buildkonfig)
    alias(libs.plugins.aboutLibraries)
}

val gitCommitShort = providers.exec {
    commandLine("git", "rev-parse", "--short", "HEAD")
    isIgnoreExitValue = true
}.standardOutput.asText.map { it.trim().ifEmpty { "dev" } }.orElse("dev")

val gitCommitCount = providers.exec {
    commandLine("git", "rev-list", "--count", "HEAD")
    isIgnoreExitValue = true
}.standardOutput.asText.map { (it.trim().toIntOrNull() ?: 1).toString() }.orElse("1")

buildkonfig {
    packageName = "com.github.woodsmarshes.chat.feature.settings"
    objectName = "AppBuildInfo"
    defaultConfigs {
        buildConfigField(FieldSpec.Type.STRING, "versionName", "1.1.1")
        buildConfigField(FieldSpec.Type.INT, "versionCode", gitCommitCount.get())
        buildConfigField(FieldSpec.Type.STRING, "gitRevision", gitCommitShort.get())
        buildConfigField(FieldSpec.Type.STRING, "kotlinVersion", libs.versions.kotlin.version.get())
        buildConfigField(FieldSpec.Type.STRING, "composeVersion", libs.versions.composeMultiplatform.get())
        buildConfigField(FieldSpec.Type.STRING, "developerName", "woods-marshes")
        buildConfigField(FieldSpec.Type.STRING, "developerUrl", "https://github.com/woods-marshes")
        buildConfigField(FieldSpec.Type.STRING, "githubUrl", "https://github.com/woods-marshes/chat-multiplatform")
        buildConfigField(FieldSpec.Type.STRING, "issuesUrl", "https://github.com/woods-marshes/chat-multiplatform/issues")
        buildConfigField(FieldSpec.Type.STRING, "licenseName", "MIT License")
    }
}

kotlin {
    if (project.extra["enableAndroid"] as Boolean) {
        extensions.configure<com.android.build.api.dsl.KotlinMultiplatformAndroidLibraryExtension> {
            namespace = "com.github.woodsmarshes.chat.feature.settings"
        }
    }

    sourceSets {
        jvmTest.dependencies {
            implementation(libs.kotlin.test)
            implementation(libs.kotlinx.coroutines.test)
            implementation(libs.mockk)
        }
        commonMain.dependencies {
            implementation(projects.core.ui)
            implementation(projects.core.common)
            implementation(projects.core.data)
            implementation(projects.core.domain)
            implementation(projects.core.datastore)
            implementation(projects.core.model)
            implementation(projects.core.navigation)

            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.kotlinx.serialization.json)
            implementation(libs.kotlinx.io.core)
            implementation(libs.filekit.core)
            implementation(libs.filekit.dialogs)
            implementation(libs.filekit.dialogs.compose)
            implementation(libs.aboutlibraries.core)
            implementation(libs.aboutlibraries.compose.m3)
        }
    }
}

compose.resources {
    publicResClass = true
    packageOfResClass = "com.github.woodsmarshes.chat.features.settings.resources"
    generateResClass = always
}

aboutLibraries {
    export {
        outputFile = file("src/commonMain/composeResources/files/aboutlibraries.json")
        prettyPrint = true
    }
    library {
        duplicationMode = com.mikepenz.aboutlibraries.plugin.DuplicateMode.MERGE
        duplicationRule = com.mikepenz.aboutlibraries.plugin.DuplicateRule.SIMPLE
        mergePlatformArtifacts = true
    }
}
