import com.github.woodsmarshes.chat.configureGraphTasks
import com.diffplug.gradle.spotless.SpotlessExtension
import org.gradle.api.Plugin
import org.gradle.api.Project

class RootPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        require(target.path == ":")
        target.subprojects {
            configureGraphTasks()
            // ktlint via Spotless: format-only, mechanical fixes, zero
            // false positives. Applied to every module from one place so no
            // per-module build file needs a new plugin alias.
            plugins.apply("com.diffplug.spotless")
            val spotless = extensions.getByType(SpotlessExtension::class.java)
            spotless.kotlin {
                target("src/**/*.kt")
                ktlint()
            }
        }
    }
}
