import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Copy
import org.gradle.api.tasks.Exec
import org.gradle.kotlin.dsl.register

interface TiptapWebviewExtension {
    /** npm script inside tiptap-bridge, e.g. "build:webview:editor". */
    val npmScript: Property<String>

    /** Optional extra input file (a vite config) relative to tiptap-bridge. */
    val viteConfig: Property<String>

    /** Build output directory, relative to tiptap-bridge ("dist", "dist-webview/editor"). */
    val outputDirName: Property<String>

    /** Files copied from the output directory into [copyInto]. */
    val includeFiles: ListProperty<String>

    /** Project-relative directory the built files are copied into. */
    val copyInto: Property<String>

    /** Task-name prefixes whose tasks get the copy wired as a dependency. */
    val hookTaskPrefixes: ListProperty<String>
}

/**
 * Shared wiring for modules that embed tiptap-bridge build output (the web
 * UMD bundle or the self-contained webview HTML pages): runs the npm build
 * at the repo root and copies the selected artifacts into the module, hooked
 * into resource generation so normal builds stay turn-key.
 */
class TiptapWebviewPlugin : Plugin<Project> {
    override fun apply(target: Project): Unit = with(target) {
        val ext = extensions.create("tiptapWebview", TiptapWebviewExtension::class.java)
        val bridge = rootProject.layout.projectDirectory.dir("tiptap-bridge")

        val build = tasks.register<Exec>("buildTiptapBridge") {
            group = "build"
            description = "Runs the tiptap-bridge npm build"

            val isWindows = org.gradle.internal.os.OperatingSystem.current().isWindows
            workingDir = bridge.asFile
            val script = ext.npmScript.get()
            if (isWindows) {
                commandLine("cmd", "/c", "npm", "run", script)
            } else {
                commandLine("npm", "run", script)
            }

            inputs.dir(bridge.dir("src"))
            inputs.file(bridge.file("package.json"))
            ext.viteConfig.orNull?.let { inputs.file(bridge.file(it)) }
            outputs.dir(bridge.dir(ext.outputDirName.get()))
        }

        val copy = tasks.register<Copy>("copyTiptapBridgeAssets") {
            group = "build"
            description = "Copies built tiptap-bridge artifacts into the module"

            dependsOn(build)

            from(bridge.dir(ext.outputDirName.get())) {
                include(ext.includeFiles.get())
            }
            into(project.layout.projectDirectory.dir(ext.copyInto.get()))
        }

        tasks.matching { task ->
            ext.hookTaskPrefixes.get().any { task.name.startsWith(it) }
        }.configureEach {
            dependsOn(copy)
        }
    }
}
