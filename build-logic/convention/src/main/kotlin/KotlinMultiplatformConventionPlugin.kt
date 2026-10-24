import com.android.build.api.dsl.KotlinMultiplatformAndroidLibraryExtension
import com.github.woodsmarshes.chat.getAndroidSdkVersions
import com.github.woodsmarshes.chat.libs
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.tasks.compile.JavaCompile
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.extra
import org.gradle.kotlin.dsl.withType
import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension

class KotlinMultiplatformConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            with(pluginManager) {
                apply(libs.findPlugin("kotlin-multiplatform").get().get().pluginId)
                if (project.extra["enableAndroid"] as Boolean) {
                    apply(libs.findPlugin("android-kotlin-multiplatform-library").get().get().pluginId)
                }
            }
            extensions.configure<KotlinMultiplatformExtension> {
                compilerOptions {
                    freeCompilerArgs.add("-Xexpect-actual-classes")
                }

                sourceSets.all {
                    languageSettings.optIn("kotlin.uuid.ExperimentalUuidApi")
                    languageSettings.optIn("kotlinx.serialization.ExperimentalSerializationApi")
                }

                if (project.extra["enableAndroid"] as Boolean) {
                    extensions.configure<KotlinMultiplatformAndroidLibraryExtension> {
                        val sdkVersions = getAndroidSdkVersions()
                        compileSdk = sdkVersions.compileSdk
                        minSdk = sdkVersions.minSdk

                        androidResources.enable = true

                        withHostTest {}
                    }
                }

                // Pin to the lowest consumer (Android, JVM 17) so the shared
                // bytecode level no longer floats with the build machine's JDK.
                jvm {
                    compilerOptions {
                        jvmTarget.set(JvmTarget.JVM_17)
                    }
                }

                js {
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

            // Keep any Java sources consistent with the Kotlin target above.
            tasks.withType<JavaCompile>().configureEach {
                options.release.set(17)
            }
        }
    }
}