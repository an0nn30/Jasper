import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.jvm.toolchain.JavaToolchainService
import org.gradle.jvm.toolchain.JavaLanguageVersion
import org.gradle.jvm.toolchain.JvmVendorSpec

// Bytecode, rather than imports, catches fully-qualified references and inferred type edges.
val app = project(":jasper-app")
val buddy = project(":jasper-buddy")
val verifyApplicationArchitecture = tasks.register("verifyApplicationArchitecture") {
    dependsOn(":jasper-app:classes", ":jasper-buddy:jar", ":jasper-terminal:jar", ":jasper-sdk:jar")
    doLast {
        val launcher = app.extensions.getByType<JavaToolchainService>().launcherFor {
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
        val supported = setOf("view.BuddyCompanion", "notice.BuddyNotice", "notice.BuddyNoticeId",
            "config.BuddyOptions", "config.BuddyPosition").map { "dev.jasper.buddy.$it" }.toSet()
        fun topLevel(name: String) = name.substringBefore('$')
        fun packageOf(name: String) = name.substringBeforeLast('.')
        fun jdk(name: String) = name.startsWith("java.") || name.startsWith("javax.") || name.startsWith("jdk.")
        fun acyclic(graph: Map<String, Set<String>>) {
            val active = linkedSetOf<String>(); val done = mutableSetOf<String>()
            fun visit(node: String) {
                if (node in done) return
                check(active.add(node)) { "Package cycle: $active -> $node" }
                graph[node].orEmpty().forEach { visit(it) }; active.remove(node); done.add(node)
            }
            graph.keys.forEach { visit(it) }
        }
        for (module in listOf(app, buddy)) {
            val classes = module.layout.buildDirectory.dir("classes/java/main").get().asFile
            val prefix = if (module == app) "dev.jasper.app." else "dev.jasper.buddy."
            val types = classes.walkTopDown().filter { it.extension == "class" }.map {
                it.relativeTo(classes).invariantSeparatorsPath.removeSuffix(".class").replace('/', '.')
            }.toList()
            if (module == app) check(types.none { packageOf(it) == "dev.jasper.app" && topLevel(it) != "dev.jasper.app.Main" && !it.endsWith("package-info") }) {
                "Flat app production types remain; only Main belongs at the root"
            }
            val output = tool("jdeps", listOf("--multi-release", "25", "-verbose:class", "-filter:none",
                "--class-path", module.configurations.getByName("runtimeClasspath").asPath.ifEmpty { classes.absolutePath }, classes.absolutePath))
            val edge = Regex("^\\s*(dev\\.jasper\\.\\S+)\\s+->\\s+(\\S+).*$")
            val graph = mutableMapOf<String, MutableSet<String>>()
            output.lineSequence().forEach { line ->
                val match = edge.matchEntire(line) ?: return@forEach
                val (from, to) = match.destructured
                if (!from.startsWith(prefix)) return@forEach
                if (module == buddy) check(to.startsWith(prefix) || jdk(to)) { "Buddy depends on non-JDK type: $line" }
                if (module == app && to.startsWith("dev.jasper.buddy.")) check(topLevel(to) in supported) { "Unsupported Buddy API: $line" }
                if (module == app && to.startsWith("dev.jasper.sdk."))
                    check(packageOf(from) == "dev.jasper.app.plugins" || packageOf(from).startsWith("dev.jasper.app.plugins.")) {
                        "SDK types are confined to dev.jasper.app.plugins: $line"
                    }
                if (to.startsWith(prefix) && packageOf(from) != packageOf(to))
                    graph.getOrPut(packageOf(from)) { linkedSetOf() }.add(packageOf(to))
            }
            acyclic(graph)
            if (module == buddy) {
                for (type in types.filter { topLevel(it) in supported }) {
                    val api = tool("javap", listOf("-classpath", classes.absolutePath, "-public", "-s", type))
                    Regex("(?:L([a-z][a-z0-9_/]*[A-Z][A-Za-z0-9_$/]*);)|(?:([a-z][a-z0-9_.]*\\.[A-Z][A-Za-z0-9_$]*))")
                        .findAll(api).forEach { match ->
                            val name = (match.groups[1]?.value ?: match.groups[2]!!.value).replace('/', '.')
                            check(jdk(name) || topLevel(name) in supported) { "Unsupported Buddy signature type $name in $api" }
                        }
                }
            } else {
                for (batch in types.chunked(100)) {
                    val code = tool("javap", listOf("-classpath", classes.absolutePath, "-c", "-p") + batch)
                    check(!code.contains("dev/jasper/terminal/internal/") && !code.contains("TerminalSession.internalAccess")) {
                        "Application bypasses supported terminal API"
                    }
                }
            }
        }
    }
}
gradle.projectsEvaluated {
    app.tasks.named("check") { dependsOn(verifyApplicationArchitecture) }
    buddy.tasks.named("check") { dependsOn(verifyApplicationArchitecture) }
}
