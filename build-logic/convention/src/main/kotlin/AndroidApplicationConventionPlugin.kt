import com.android.build.api.dsl.ApplicationExtension
import com.github.woodsmarshes.chat.configureKotlinAndroid
import com.github.woodsmarshes.chat.libs
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure

class AndroidApplicationConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            with(pluginManager) {
                apply(libs.findPlugin("android-application").get().get().pluginId)
                // kotlin-android is no longer needed with AGP 9.0+ built-in Kotlin support
            }

            extensions.configure<ApplicationExtension> {
                configureKotlinAndroid(this)
                defaultConfig.targetSdk = libs.findVersion("android-targetSdk").get().requiredVersion.toInt()
                testOptions.animationsDisabled = true

                buildFeatures {
                    compose = true
                }
            }
        }
    }
}