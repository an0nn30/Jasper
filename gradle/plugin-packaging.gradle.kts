// Packaging for the in-repo plugins: what a plugin directory holds is its jar plus the libraries it
// bundles (its runtime classpath), the same files stagePlugins copies into the application image.
//   :jasper-plugin-<name>:stagePlugin  -> plugins/<name>/build/plugin/<id>/     (a --plugin-dir directory)
//   :jasper-plugin-<name>:pluginZip    -> plugins/<name>/build/distributions/<id>-<version>.zip
//                                         (File > Manage Plugins... > Install from Zip...)
//   pluginZips                          -> every plugin's zip
val pluginPaths = listOf(":jasper-plugin-sample", ":jasper-plugin-snippets", ":jasper-plugin-history")

/** id and version from the plugin's own descriptor, so the build has no second copy of either. */
fun descriptor(project: Project): Pair<String, String> {
    val text = project.file("src/main/resources/plugin.toml").readText()
    fun field(name: String) = Regex("^$name\\s*=\\s*\"([^\"]+)\"", RegexOption.MULTILINE).find(text)?.groupValues?.get(1)
        ?: error("${project.path}: plugin.toml has no $name")
    return field("id") to field("version")
}

val pluginZips = tasks.register("pluginZips") {
    description = "Builds every in-repo plugin's installable zip."
    group = "distribution"
}

pluginPaths.forEach { path ->
    val plugin = project(path)
    val (id, version) = descriptor(plugin)
    val contents = plugin.files(plugin.tasks.named("jar"), plugin.configurations.named("runtimeClasspath"))
    plugin.tasks.register<Sync>("stagePlugin") {
        description = "Stages $id as a plugin directory for --plugin-dir."
        group = "distribution"
        from(contents)
        into(plugin.layout.buildDirectory.dir("plugin/$id"))
    }
    val zip = plugin.tasks.register<Zip>("pluginZip") {
        description = "Zips $id for File > Manage Plugins... > Install from Zip..."
        group = "distribution"
        from(contents)
        archiveFileName.set("$id-$version.zip")
        destinationDirectory.set(plugin.layout.buildDirectory.dir("distributions"))
    }
    pluginZips.configure { dependsOn(zip) }
}
