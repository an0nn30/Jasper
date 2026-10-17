import org.gradle.api.artifacts.component.ModuleComponentIdentifier

// Every external jar Jasper or a bundled plugin ships has a component in the third-party notices index, and every file
// the index names exists. The index is Jasper's own format (notices.toml); ThirdPartyNotices reads it with tomlj at
// runtime, and this check reads the few fields it needs with regular expressions, as plugin-packaging reads plugin.toml.
val noticesApp = project(":jasper-app")
val verifyThirdPartyNotices = tasks.register("verifyThirdPartyNotices") {
    group = "verification"
    description = "Checks that every shipped third-party jar has a license entry and that every named license file exists."
    val index = noticesApp.file("src/main/resources/dev/jasper/app/about/licenses/notices.toml")
    inputs.file(index)
    inputs.files(rootProject.fileTree("plugins") { include("*/src/main/resources/plugin.toml", "*/src/main/resources/**/*.ttf", "*/src/main/resources/**/*.otf") })
    doLast {
        val modules = listOf(noticesApp) + rootProject.subprojects.filter { it.path.startsWith(":jasper-plugin-") }
        val shipped = modules.flatMap { module ->
            module.configurations.getByName("runtimeClasspath").incoming.artifacts.artifacts.mapNotNull { artifact ->
                (artifact.id.componentIdentifier as? ModuleComponentIdentifier)?.let { "${it.group}:${it.module}:${it.version}" }
            }
        }.toSortedSet()
        val resources = listOf(noticesApp.file("src/main/resources"), project(":jasper-ui").file("src/main/resources"))
        fun field(block: String, key: String) = Regex("(?m)^\\s*$key\\s*=\\s*\"([^\"]*)\"").find(block)?.groupValues?.get(1)
        val problems = mutableListOf<String>()
        val listed = mutableListOf<String>()
        val listedFiles = mutableListOf<String>()
        for (block in index.readText().split(Regex("(?m)^\\[\\[component]]")).drop(1)) {
            val name = field(block, "name") ?: "(unnamed component)"
            Regex("(?s)modules\\s*=\\s*\\[(.*?)]").find(block)?.groupValues?.get(1)?.let { list ->
                Regex("\"([^\"]+)\"").findAll(list).forEach { listed.add(it.groupValues[1]) }
            }
            Regex("(?s)files\\s*=\\s*\\[(.*?)]").find(block)?.groupValues?.get(1)?.let { list ->
                Regex("\"([^\"]+)\"").findAll(list).forEach { listedFiles.add(it.groupValues[1]) }
            }
            for (key in listOf("text", "notice")) {
                val path = field(block, key)
                if (path == null) {
                    if (key == "text") problems.add("$name has no text")
                    continue
                }
                val exists = if (path.startsWith("/")) resources.any { it.resolve(path.removePrefix("/")).isFile }
                    else index.parentFile.resolve(path).isFile
                if (!exists) problems.add("$name: its $key file $path does not exist")
            }
        }
        listed.groupingBy { it }.eachCount().filterValues { it > 1 }.keys.forEach { problems.add("$it is listed twice") }
        // Font files ship inside plugin jars, not as Maven modules: each must be listed in a component's files (spec 14.1).
        val fonts = rootProject.subprojects.filter { it.path.startsWith(":jasper-plugin-") }.flatMap { plugin ->
            val pluginResources = plugin.file("src/main/resources")
            val descriptor = pluginResources.resolve("plugin.toml")
            val id = if (descriptor.isFile) Regex("(?m)^id\\s*=\\s*\"([^\"]+)\"").find(descriptor.readText())?.groupValues?.get(1) else null
            if (id == null) emptyList<String>() else pluginResources.walkTopDown().filter { it.isFile && (it.extension == "ttf" || it.extension == "otf") }
                .map { "$id/" + it.relativeTo(pluginResources).invariantSeparatorsPath }.toList()
        }.toSortedSet()
        (fonts - listedFiles.toSet()).forEach { problems.add("$it ships but no component in notices.toml lists it in files") }
        (listedFiles.toSet() - fonts).forEach { problems.add("$it is listed in notices.toml files but no bundled plugin ships it") }
        (shipped - listed.toSet()).forEach { problems.add("$it ships but no component in notices.toml lists it") }
        (listed.toSet() - shipped).forEach { problems.add("$it is listed in notices.toml but no longer ships") }
        check(problems.isEmpty()) { "Third-party notices are incomplete:\n" + problems.joinToString("\n") }
        logger.lifecycle("Verified third-party notices for ${shipped.size} shipped modules")
    }
}
gradle.projectsEvaluated { noticesApp.tasks.named("check") { dependsOn(verifyThirdPartyNotices) } }
