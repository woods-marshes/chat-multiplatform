plugins {
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.multiplatform) apply false
    alias(libs.plugins.compose.multiplatform) apply false
    alias(libs.plugins.compose.compiler) apply false
    alias(ktorLibs.plugins.ktor) apply false
    alias(libs.plugins.kotlin.plugin.serialization) apply false
    alias(libs.plugins.sqlDelight) apply false
    alias(libs.plugins.android.kotlin.multiplatform.library) apply false
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.android.lint) apply false
    alias(libs.plugins.nucleus) apply false
    alias(libs.plugins.aboutLibraries) apply false
    alias(libs.plugins.project.root)
}

buildscript {
    // AGP pulls commons-compress -> commons-lang3:3.16.0, while a platform
    // constraint requires commons-lang3 >= 3.18.0. Resolving that conflict
    // retargets the edge to 3.22.0 and hits a Gradle bug ("edge has no target
    // nodes" while writing the resolution result to the binary store), which
    // fails fresh-cache builds such as the Docker image build. Forcing a single
    // commons-lang3 version removes the conflict and avoids the retargeting.
    // See https://github.com/gradle/gradle/issues/37192
    configurations.named("classpath") {
        resolutionStrategy {
            force("org.apache.commons:commons-lang3:3.22.0")
        }
    }
    dependencies {
        // For KGP
        classpath(libs.kotlin.gradlePlugin)

        // For KSP
        classpath(libs.kotlin.symbol.processing.gradlePlugin)
    }
}

rootProject.plugins.withType<org.jetbrains.kotlin.gradle.targets.js.yarn.YarnPlugin> {
    rootProject.the<org.jetbrains.kotlin.gradle.targets.js.yarn.YarnRootExtension>().apply {
        yarnLockMismatchReport = org.jetbrains.kotlin.gradle.targets.js.yarn.YarnLockMismatchReport.FAIL

        yarnLockAutoReplace = false
    }
}