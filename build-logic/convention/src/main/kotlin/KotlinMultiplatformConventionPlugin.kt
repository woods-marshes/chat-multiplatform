import com.android.build.api.dsl.KotlinMultiplatformAndroidLibraryExtension
import com.github.woodsmarshes.chat.getAndroidSdkVersions
import com.github.woodsmarshes.chat.libs
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure
import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension

class KotlinMultiplatformConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            with(pluginManager) {
                apply(libs.findPlugin("kotlin-multiplatform").get().get().pluginId)
                apply(libs.findPlugin("android-kotlin-multiplatform-library").get().get().pluginId)
            }
            extensions.configure<KotlinMultiplatformExtension> {
                compilerOptions {
                    freeCompilerArgs.add("-Xexpect-actual-classes")
                }

                sourceSets.all {
                    languageSettings.optIn("kotlin.uuid.ExperimentalUuidApi")
                    languageSettings.optIn("kotlinx.serialization.ExperimentalSerializationApi")
                }

                extensions.configure<KotlinMultiplatformAndroidLibraryExtension> {
                    val sdkVersions = getAndroidSdkVersions()
                    compileSdk = sdkVersions.compileSdk
                    minSdk = sdkVersions.minSdk

                    androidResources.enable = true

                    withHostTest {}
                }

                jvm()

                js(IR) {
                    browser()
                    compilerOptions {
                        this.target.set("es2015")
                    }
                }

                @OptIn(ExperimentalWasmDsl::class)
                wasmJs {
                    browser()
                }
            }
        }
    }
}