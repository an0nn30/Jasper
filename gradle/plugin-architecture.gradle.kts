import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.jvm.toolchain.JavaToolchainService
import org.gradle.jvm.toolchain.JavaLanguageVersion
import org.gradle.jvm.toolchain.JvmVendorSpec

// The build-time twin of PluginClassLoader: an in-repo plugin may reference only the JDK, the SDK,
// its own packages, libraries it bundles, and the exported packages of plugins it requires.
// Map each plugin project to the exported packages of its declared dependencies.
val pluginImports = mapOf(":jasper-plugin-sample" to listOf<String>())
val pluginProjects = pluginImports.keys.associateWith { project(it) }
val verifyPluginArchitecture = tasks.register("verifyPluginArchitecture") {
    pluginImports.keys.forEach { dependsOn("$it:classes") }
    doLast {
        val launcher = pluginProjects.values.first().extensions.getByType<JavaToolchainService>().launcherFor {
            languageVersion = JavaLanguageVersion.of(25)
            vendor = JvmVendorSpec.JETBRAINS
        }.get()
        val bin = launcher.metadata.installationPath.dir("bin").asFile
        val suffix = if (System.getProperty("os.name").startsWith("Windows")) ".exe" else ""
        fun tool(name: String, args: List<String>): String {
            val process = ProcessBuilder(listOf(bin.resolve(name + suffix).absolutePath) + args)
                .redirectErrorStream(true).start()
            val output = process.inputStream.bufferedReader().readText()
            check(process.waitFor() == 0) { output }; return output
        }
        fun jdk(name: String) = name.startsWith("java.") || name.startsWith("javax.") || name.startsWith("jdk.")
        val edge = Regex("^\\s*(\\S+)\\s+->\\s+(\\S+)\\s*(.*)$")
        for ((path, imports) in pluginImports) {
            val module = pluginProjects.getValue(path)
            val classes = module.layout.buildDirectory.dir("classes/java/main").get().asFile
            val own = classes.walkTopDown().filter { it.extension == "class" }.map {
                it.relativeTo(classes).invariantSeparatorsPath.removeSuffix(".class").replace('/', '.').substringBeforeLast('.')
            }.toSet()
            // Bundled libraries are whatever the plugin ships beside its jar: its runtime classpath.
            val bundled = module.configurations.getByName("runtimeClasspath").asPath
            val args = mutableListOf("--multi-release", "25", "--ignore-missing-deps", "-verbose:class", "-filter:none")
            if (bundled.isNotEmpty()) args += listOf("--class-path", bundled)
            tool("jdeps", args + classes.absolutePath).lineSequence().forEach { line ->
                val match = edge.matchEntire(line) ?: return@forEach
                val (from, to, where) = match.destructured
                if (from.substringBeforeLast('.') !in own) return@forEach
                val target = to.substringBeforeLast('.')
                val allowed = jdk(to) || target in own || imports.any { target == it }
                    || (to.startsWith("dev.jasper.sdk.") && !to.startsWith("dev.jasper.sdk.testing."))
                    || (bundled.isNotEmpty() && where.isNotBlank() && !where.contains("not found"))
                check(allowed) { "$path references a type plugins cannot see: $line" }
            }
        }
    }
}
gradle.projectsEvaluated {
    pluginProjects.values.forEach { it.tasks.named("check") { dependsOn(verifyPluginArchitecture) } }
}
