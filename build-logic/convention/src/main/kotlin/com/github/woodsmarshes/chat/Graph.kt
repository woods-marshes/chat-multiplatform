package com.github.woodsmarshes.chat

import org.gradle.api.DefaultTask
import org.gradle.api.Project
import org.gradle.api.artifacts.ProjectDependency
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.MapProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity.NONE
import org.gradle.api.tasks.TaskAction
import org.gradle.kotlin.dsl.assign
import org.gradle.kotlin.dsl.register
import org.gradle.kotlin.dsl.withType
import kotlin.text.RegexOption.DOT_MATCHES_ALL

/**
 * Generates module dependency graphs with the `graphDump` task and updates the matching `README.md` with `graphUpdate`.
 *
 * The graph is computed during Gradle's configuration phase and passed to the tasks as plain values, so the tasks stay
 * compatible with the configuration cache. Recursion is bounded by the number of projects in the build.
 *
 * `graph.ignoredProjects` and `graph.supportedConfigurations` narrow what is recorded.
 */
private class Graph(
    private val root: Project,
    private val dependencies: MutableMap<Project, Set<Pair<String, Project>>> = mutableMapOf(),
    private val plugins: MutableMap<Project, PluginType> = mutableMapOf(),
    private val seen: MutableSet<String> = mutableSetOf(),
) {

    private val ignoredProjects = root.providers.gradleProperty("graph.ignoredProjects")
        .map { it.split(",").toSet() }
        .orElse(emptySet())

    private val supportedConfigurations =
        root.providers.gradleProperty("graph.supportedConfigurations")
            .map { it.split(",").map(String::lowercase).toSet() }
            // Every source set exposes its dependencies as `<sourceSet>Api`/`<sourceSet>Implementation`.
            .orElse(setOf("api", "implementation"))

    operator fun invoke(project: Project = root): Graph {
        if (!seen.add(project.path)) return this
        plugins[project] = PluginType.entries.firstOrNull {
            it.id.isNotEmpty() && project.pluginManager.hasPlugin(it.id)
        } ?: PluginType.Unknown

        val ignored = ignoredProjects.get()
        val scopes = supportedConfigurations.get()
        val edges = project.configurations.asSequence()
            .filter { configuration ->
                val name = configuration.name.lowercase()
                scopes.any { name.endsWith(it) } &&
                        !name.contains("test") &&
                        !name.contains("debug") &&
                        !name.contains("release")
            }
            .flatMap { configuration ->
                val scope = if (configuration.name.lowercase().endsWith("api")) "api" else "implementation"
                configuration.dependencies.withType<ProjectDependency>().asSequence()
                    .map { scope to project.project(it.path) }
            }
            .filter { (_, projectDependency) -> projectDependency.path !in ignored }
            .toSet()

        dependencies[project] = edges
        edges.forEach { (_, projectDependency) -> invoke(projectDependency) }
        return this
    }

    fun dependencies(): Map<String, Set<Pair<String, String>>> = dependencies.entries.associate { (project, edges) ->
        project.path to edges.mapTo(mutableSetOf()) { (scope, dependency) -> scope to dependency.path }
    }

    fun plugins() = plugins.mapKeys { it.key.path }
}

/**
 * The ids are the plugins this build actually applies, including the upstream ones behind the `project.*` conventions.
 * Declaration order is important, as only the first match is retained.
 */
internal enum class PluginType(
    val id: String,
    val label: String,
    val ref: String,
    val style: String,
) {
    AndroidApplication(
        id = "com.android.application",
        label = "Android App",
        ref = "android-application",
        style = "fill:#CAFFBF,stroke:#000,stroke-width:2px,color:#000",
    ),
    DesktopApplication(
        id = "dev.nucleusframework",
        label = "Desktop App",
        ref = "desktop-application",
        style = "fill:#BDB2FF,stroke:#000,stroke-width:2px,color:#000",
    ),
    ComposeMultiplatform(
        id = "org.jetbrains.compose",
        label = "Compose UI",
        ref = "compose-multiplatform",
        style = "fill:#FFD6A5,stroke:#000,stroke-width:2px,color:#000",
    ),
    KtorServer(
        id = "io.ktor.plugin",
        label = "Ktor Server",
        ref = "ktor-server",
        style = "fill:#FDFFB6,stroke:#000,stroke-width:2px,color:#000",
    ),
    KotlinMultiplatform(
        id = "org.jetbrains.kotlin.multiplatform",
        label = "Kotlin Multiplatform",
        ref = "kotlin-multiplatform",
        style = "fill:#9BF6FF,stroke:#000,stroke-width:2px,color:#000",
    ),
    Unknown(
        id = "",
        label = "Unknown",
        ref = "unknown",
        style = "fill:#FFADAD,stroke:#000,stroke-width:2px,color:#000",
    ),
}

internal fun Project.configureGraphTasks() {
    if (!buildFile.exists()) return // Ignore root modules without build file
    val dumpTask = tasks.register<GraphDumpTask>("graphDump") {
        val graph = Graph(this@configureGraphTasks).invoke()
        projectPath = this@configureGraphTasks.path
        dependencies = graph.dependencies()
        plugins = graph.plugins()
        output = this@configureGraphTasks.layout.buildDirectory.file("mermaid/graph.txt")
        legend = this@configureGraphTasks.layout.buildDirectory.file("mermaid/legend.txt")
    }
    tasks.register<GraphUpdateTask>("graphUpdate") {
        projectPath = this@configureGraphTasks.path
        input = dumpTask.flatMap { it.output }
        legend = dumpTask.flatMap { it.legend }
        output = this@configureGraphTasks.layout.projectDirectory.file("README.md")
    }
}

@CacheableTask
private abstract class GraphDumpTask : DefaultTask() {

    @get:Input
    abstract val projectPath: Property<String>

    @get:Input
    abstract val dependencies: MapProperty<String, Set<Pair<String, String>>>

    @get:Input
    abstract val plugins: MapProperty<String, PluginType>

    @get:OutputFile
    abstract val output: RegularFileProperty

    @get:OutputFile
    abstract val legend: RegularFileProperty

    override fun getDescription() = "Dumps project dependencies to a mermaid file."

    @TaskAction
    operator fun invoke() {
        output.get().asFile.writeText(mermaid())
        legend.get().asFile.writeText(legend())
        logger.lifecycle(output.get().asFile.toPath().toUri().toString())
    }

    private fun mermaid() = buildString {
        val edges: Set<Dependency> = dependencies.get()
            .flatMapTo(mutableSetOf()) { (project, entries) -> entries.map { it.toDependency(project) } }
        // FrontMatter configuration (not supported yet on GitHub.com)
        appendLine(
            // language=YAML
            """
            ---
            config:
              layout: elk
              elk:
                nodePlacementStrategy: SIMPLE
            ---
            """.trimIndent(),
        )
        // Graph declaration
        appendLine("graph TB")
        // Nodes and subgraphs
        val (rootProjects, nestedProjects) = edges
            .flatMapTo(mutableSetOf()) { listOf(it.project, it.dependency) }
            .plus(projectPath.get()) // Special case when this specific module has no other dependency
            .groupBy { it.substringBeforeLast(":") }
            .entries.partition { it.key.isEmpty() }

        val orderedGroups = nestedProjects.groupBy {
            if (it.key.count { char -> char == ':' } > 1) it.key.substringBeforeLast(":") else ""
        }

        orderedGroups.forEach { (outerGroup, innerGroups) ->
            if (outerGroup.isNotEmpty()) {
                appendLine("  subgraph $outerGroup")
                appendLine("    direction TB")
            }
            innerGroups.sortedWith(
                compareBy(
                    { (group, _) ->
                        edges.count { edge ->
                            val toGroup = edge.dependency.substringBeforeLast(":")
                            toGroup == group && edge.project.substringBeforeLast(":") != group
                        }
                    },
                    { -it.value.size },
                ),
            ).forEach { (group, projects) ->
                val indent = if (outerGroup.isNotEmpty()) 4 else 2
                appendLine(" ".repeat(indent) + "subgraph $group")
                appendLine(" ".repeat(indent) + "  direction TB")
                projects.sorted().forEach {
                    appendLine(it.alias(indent = indent + 2, plugins.get().getValue(it)))
                }
                appendLine(" ".repeat(indent) + "end")
            }
            if (outerGroup.isNotEmpty()) {
                appendLine("  end")
            }
        }

        rootProjects.flatMap { it.value }.sortedDescending().forEach {
            appendLine(it.alias(indent = 2, plugins.get().getValue(it)))
        }
        // Links
        if (edges.isNotEmpty()) appendLine()
        edges
            .sortedWith(compareBy({ it.project }, { it.dependency }, { it.isApi }))
            .forEach { appendLine(it.link(indent = 2)) }
        // Classes
        appendLine()
        PluginType.entries.forEach { appendLine(it.classDef()) }
    }

    private fun legend() = buildString {
        appendLine("graph TB")
        PluginType.entries
            .filterNot { it == PluginType.Unknown }
            .forEach { appendLine(it.legendNode(indent = 2)) }
        appendLine()
        listOf(
            Dependency(
                project = PluginType.AndroidApplication.name,
                isApi = false,
                dependency = PluginType.KotlinMultiplatform.name,
            ),
            Dependency(
                project = PluginType.KotlinMultiplatform.name,
                isApi = true,
                dependency = PluginType.ComposeMultiplatform.name,
            ),
        ).forEach { appendLine(it.link(indent = 2)) }
        appendLine()
        PluginType.entries.forEach { appendLine(it.classDef()) }
    }

    private class Dependency(val project: String, val isApi: Boolean, val dependency: String)

    private fun Pair<String, String>.toDependency(project: String) =
        Dependency(project, isApi = first == "api", dependency = second)

    private fun String.alias(indent: Int, pluginType: PluginType) =
        " ".repeat(indent) + this + "[" + substringAfterLast(":") + "]:::" + pluginType.ref

    private fun PluginType.legendNode(indent: Int) = " ".repeat(indent) + "$name[$label]:::$ref"

    private fun Dependency.link(indent: Int) = buildString {
        append(" ".repeat(indent))
        append(project)
        append(if (isApi) " --> " else " -.-> ")
        append(dependency)
    }

    private fun PluginType.classDef() = "classDef $ref $style;"
}

@CacheableTask
private abstract class GraphUpdateTask : DefaultTask() {

    @get:Input
    abstract val projectPath: Property<String>

    @get:InputFile
    @get:PathSensitive(NONE)
    abstract val input: RegularFileProperty

    @get:InputFile
    @get:PathSensitive(NONE)
    abstract val legend: RegularFileProperty

    @get:OutputFile
    abstract val output: RegularFileProperty

    override fun getDescription() = "Updates Markdown file with the corresponding dependency graph."

    @TaskAction
    operator fun invoke() = with(output.get().asFile) {
        if (!exists()) {
            createNewFile()
            writeText(
                """
                # `${projectPath.get()}`

                ## Module dependency graph

                <!--region graph--> <!--endregion-->

                """.trimIndent(),
            )
        }
        val mermaid = input.get().asFile.readText().trimTrailingNewLines()
        val legend = legend.get().asFile.readText().trimTrailingNewLines()
        val regex = """(<!--region graph-->)(.*?)(<!--endregion-->)""".toRegex(DOT_MATCHES_ALL)
        val current = readText()
        // Hand-written READMEs predate the graph region, so add the section instead of silently doing nothing.
        val withRegion = if (regex.containsMatchIn(current)) {
            current
        } else {
            current.trimEnd() + "\n\n## Module dependency graph\n\n<!--region graph--> <!--endregion-->\n"
        }
        val text = withRegion.replace(regex) { match ->
            val (start, _, end) = match.destructured
            """
            |$start
            |```mermaid
            |$mermaid
            |```
            |
            |<details><summary>📋 Graph legend</summary>
            |
            |```mermaid
            |$legend
            |```
            |
            |</details>
            |$end
            """.trimMargin()
        }
        writeText(text)
    }

    private fun String.trimTrailingNewLines() = lines()
        .dropLastWhile(String::isBlank)
        .joinToString(System.lineSeparator())
}
