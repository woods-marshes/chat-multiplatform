package com.github.woodsmarshes.chat

import org.gradle.api.DefaultTask
import org.gradle.api.Project
import org.gradle.api.artifacts.Configuration
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
 * Generates module dependency graphs with `graphDump` task, and update the corresponding `README.md` file with `graphUpdate`.
 *
 * This is not an optimal implementation and could be improved if needed:
 * - [Graph.invoke] is **recursively** searching through dependent projects (although in practice it will never reach a stack overflow).
 * - [Graph.invoke] is entirely re-executed for all projects, without re-using intermediate values.
 * - [Graph.invoke] is always executed during Gradle's Configuration phase (but takes in general less than 1 ms for a project).
 *
 * The resulting graphs can be configured with `graph.ignoredProjects` and `graph.supportedConfigurations` properties.
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
            .map { it.split(",").toSet() }
            .orElse(setOf(
                "api",
                "implementation",
                "commonMainApi",
                "commonMainImplementation",
                "androidMainApi",
                "androidMainImplementation",
                "jvmMainApi",
                "jvmMainImplementation",
                "jsMainApi",
                "jsMainImplementation",
                "wasmJsMainApi",
                "wasmJsMainImplementation",
            ))

    operator fun invoke(project: Project = root): Graph {
        if (project.path in seen) return this
        seen += project.path
        plugins.putIfAbsent(
            project,
            PluginType.entries.firstOrNull { project.pluginManager.hasPlugin(it.id) } ?: PluginType.Unknown,
        )
        dependencies.compute(project) { _, u -> u.orEmpty() }
        project.configurations
            .filter { config ->
                val configName = config.name.lowercase()
                supportedConfigurations.get().any { configName.endsWith(it.lowercase()) } &&
                        !configName.contains("test") &&
                        !configName.contains("debug") &&
                        !configName.contains("release")
            }
            .flatMap { config ->
                config.dependencies.withType<ProjectDependency>()
                    .map { dep -> config to project.project(dep.path) }
            }
            .filter { (_, p) -> p.path !in ignoredProjects.get() }
            .forEach { (configuration, projectDependency) ->
                val simplifiedConfigName = when {
                    configuration.name.lowercase().endsWith("api") -> "api"
                    else -> "implementation"
                }

                dependencies.compute(project) { _, u ->
                    u.orEmpty() + (simplifiedConfigName to projectDependency)
                }
                invoke(projectDependency)
            }
        return this
    }

    fun dependencies(): Map<String, Set<Pair<String, String>>> = dependencies
        .mapKeys { it.key.path }
        .mapValues { it.value.mapTo(mutableSetOf()) { (c, p) -> c to p.path } }

    fun plugins() = plugins.mapKeys { it.key.path }
}

/**
 * Declaration order is important, as only the first match will be retained.
 */
internal enum class PluginType(val id: String, val ref: String, val style: String) {
    AndroidApplication(
        id = "project.android.application",
        ref = "android-application",
        style = "fill:#CAFFBF,stroke:#000,stroke-width:2px,color:#000",
    ),
    ComposeMultiplatform(
        id = "project.kotlin.composeMultiplatform",
        ref = "compose-multiplatform",
        style = "fill:#FFD6A5,stroke:#000,stroke-width:2px,color:#000",
    ),
    KotlinMultiplatform(
        id = "project.kotlin.multiplatform",
        ref = "kotlin-multiplatform",
        style = "fill:#9BF6FF,stroke:#000,stroke-width:2px,color:#000",
    ),
    Unknown(
        id = "?",
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
        val dependencies: Set<Dependency> = dependencies.get()
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
        val (rootProjects, nestedProjects) = dependencies
            .map { listOf(it.project, it.dependency) }.flatten().toSet()
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
                        dependencies.count { dep ->
                            val toGroup = dep.dependency.substringBeforeLast(":")
                            toGroup == group && dep.project.substringBeforeLast(":") != group
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
        if (dependencies.isNotEmpty()) appendLine()
        dependencies
            .sortedWith(compareBy({ it.project }, { it.dependency }, { it.configuration }))
            .forEach { appendLine(it.link(indent = 2)) }
        // Classes
        appendLine()
        PluginType.entries.forEach { appendLine(it.classDef()) }
    }

    private fun legend() = buildString {
        appendLine("graph TB")
        listOf(
            "AndroidApp" to PluginType.AndroidApplication,
            "ComposeUI" to PluginType.ComposeMultiplatform,
            "SharedKMP" to PluginType.KotlinMultiplatform,
        ).forEach { (name, type) ->
            appendLine(name.alias(indent = 2, type))
        }
        appendLine()
        listOf(
            Dependency("AndroidApp", "implementation", "SharedKMP"),
            Dependency("SharedKMP", "api", "ComposeUI"),
        ).forEach {
            appendLine(it.link(indent = 2))
        }
        appendLine()
        PluginType.entries.forEach { appendLine(it.classDef()) }
    }

    private class Dependency(val project: String, val configuration: String, val dependency: String)

    private fun Pair<String, String>.toDependency(project: String) =
        Dependency(project, configuration = first, dependency = second)

    private fun String.alias(indent: Int, pluginType: PluginType): String = buildString {
        append(" ".repeat(indent))
        if (this@alias.startsWith("\"")) {
            // For quoted nodes (should not happen anymore)
            append(this@alias)
            append(":::")
            append(pluginType.ref)
        } else {
            // For legend nodes and actual project nodes
            append(this@alias)
            val displayName = when {
                this@alias.contains(":") -> substringAfterLast(":")
                this@alias == "AndroidApp" -> "Android App"
                this@alias == "ComposeUI" -> "Compose UI"
                this@alias == "SharedKMP" -> "Shared KMP"
                else -> this@alias
            }
            append("[").append(displayName).append("]:::")
            append(pluginType.ref)
        }
    }

    private fun Dependency.link(indent: Int) = buildString {
        append(" ".repeat(indent))
        append(project).append(" ")
        
        val isApi = configuration.lowercase().endsWith("api")
        val label = if (configuration in setOf("api", "implementation")) "" else "|$configuration|"

        if (isApi) {
            append("-->") // solid line for API dependencies
        } else {
            append("-.->") // dashed line for implementation dependencies
        }

        if (label.isNotEmpty()) {
            append(label)
        }
        append(" ").append(dependency)
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
        val text = readText().replace(regex) { match ->
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
