plugins {
    alias(libs.plugins.project.android.application)
    alias(libs.plugins.project.composeMultiplatform)
}

android {
    namespace = "com.github.woodsmarshes.chat"

    defaultConfig {
        applicationId = "com.github.woodsmarshes.chat"
        versionCode = 1
        versionName = "1.1"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
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
}