import com.github.woodsmarshes.chat.libs
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure
import org.jetbrains.kotlin.compose.compiler.gradle.ComposeCompilerGradlePluginExtension

class ComposeMultiplatformConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            with(pluginManager) {
                apply(libs.findPlugin("compose-multiplatform").get().get().pluginId)
                apply(libs.findPlugin("compose-compiler").get().get().pluginId)
                // apply(libs.findPlugin("compose-hotReload").get().get().pluginId)
            }
            extensions.configure<ComposeCompilerGradlePluginExtension> {
                stabilityConfigurationFiles.add(
                    rootProject.layout.projectDirectory.file("stability_config.conf")
                )
            }
        }
    }
}