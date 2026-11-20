plugins {
    alias(libs.plugins.project.android.application)
    alias(libs.plugins.project.composeMultiplatform)
}

// Release signing is injected by CI (secrets → env vars); local builds without
// ANDROID_KEYSTORE_FILE stay unsigned so `assembleRelease` still works.
val releaseStoreFile = providers.environmentVariable("ANDROID_KEYSTORE_FILE")
    .orNull
    ?.let { file(it) }
    ?.takeIf { it.exists() }

android {
    namespace = "com.github.woodsmarshes.chat"

    defaultConfig {
        applicationId = "com.github.woodsmarshes.chat"
        versionCode = providers.gradleProperty("appVersionCode")
            .map(String::toInt)
            .getOrElse(1)
        versionName = libs.versions.appVersion.get()

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        if (releaseStoreFile != null) {
            create("release") {
                storeFile = releaseStoreFile
                storePassword = providers.environmentVariable("ANDROID_KEYSTORE_PASSWORD").get()
                keyAlias = providers.environmentVariable("ANDROID_KEY_ALIAS").get()
                keyPassword = providers.environmentVariable("ANDROID_KEY_PASSWORD").get()
            }
        }
    }

    buildTypes {
        debug {
            isDebuggable = true
            // 其他 debug 专属配置
        }

        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            if (releaseStoreFile != null) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
    }

    packaging {
        resources {
            excludes.add("/META-INF/{AL2.0,LGPL2.1}")
            excludes.add("DebugProbesKt.bin")
        }
    }

    testOptions.unitTests.isIncludeAndroidResources = true
}

dependencies {
    implementation(projects.composeApp)
    implementation(projects.core.database)
    implementation(projects.core.common)
    // Not needed: androidApp gets core/* + composeApp

    implementation(libs.androidx.activity.compose)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.slf4j.simple)
    implementation(libs.filekit.dialogs)
}