import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.jvm.toolchain.JavaToolchainService
import org.gradle.jvm.toolchain.JavaLanguageVersion
import org.gradle.jvm.toolchain.JvmVendorSpec

// SDK modules may reference only the JDK and dev.jasper.sdk, and their package graphs are acyclic.
// A module listed here must have the java plugin applied and produce classes/java/main.
val sdkModules = listOf(":jasper-sdk")
// Resolved at configuration time, like the other architecture scripts; tasks must not call project() while running.
val sdkProjects = sdkModules.associateWith { project(it) }
val verifySdkArchitecture = tasks.register("verifySdkArchitecture") {
    sdkModules.forEach { dependsOn("$it:classes") }
    doLast {
        val launcher = sdkProjects.values.first().extensions.getByType<JavaToolchainService>().launcherFor {
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
        fun packageOf(name: String) = name.substringBeforeLast('.')
        val edge = Regex("^\\s*(dev\\.jasper\\.sdk\\.\\S+)\\s+->\\s+(\\S+).*$")
        for (path in sdkModules) {
            val module = sdkProjects.getValue(path)
            val classes = module.layout.buildDirectory.dir("classes/java/main").get().asFile
            val classpath = module.configurations.getByName("runtimeClasspath").asPath
            val args = mutableListOf("--multi-release", "25", "--ignore-missing-deps", "-verbose:class", "-filter:none")
            if (classpath.isNotEmpty()) args += listOf("--class-path", classpath)
            val output = tool("jdeps", args + classes.absolutePath)
            val graph = mutableMapOf<String, MutableSet<String>>()
            output.lineSequence().forEach { line ->
                val match = edge.matchEntire(line) ?: return@forEach
                val (from, to) = match.destructured
                check(jdk(to) || to.startsWith("dev.jasper.sdk.")) { "$path depends on a non-JDK, non-SDK type: $line" }
                if (to.startsWith("dev.jasper.sdk.") && packageOf(from) != packageOf(to))
                    graph.getOrPut(packageOf(from)) { linkedSetOf() }.add(packageOf(to))
            }
            val active = linkedSetOf<String>(); val done = mutableSetOf<String>()
            fun visit(node: String) {
                if (node in done) return
                check(active.add(node)) { "SDK package cycle: $active -> $node" }
                graph[node].orEmpty().forEach { visit(it) }; active.remove(node); done.add(node)
            }
            graph.keys.forEach { visit(it) }
        }
    }
}
gradle.projectsEvaluated {
    sdkProjects.values.forEach { it.tasks.named("check") { dependsOn(verifySdkArchitecture) } }
}
