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
        // The dock model is pure: JDK types only, and from java.awt only two value classes.
        val dockAwt = setOf("java.awt.Rectangle", "java.awt.Point")
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
                if (module == app && packageOf(from) == "dev.jasper.app.dock")
                    check(to.startsWith("dev.jasper.app.dock.") || (to.startsWith("java.") && !to.startsWith("java.awt.")) || to in dockAwt) {
                        "dev.jasper.app.dock is JDK-only and holds no Swing or AWT components: $line"
                    }
                // The workspace layout model is pure: JDK types only and nothing from java.awt.
                if (module == app && packageOf(from) == "dev.jasper.app.layout")
                    check(to.startsWith("dev.jasper.app.layout.") || (to.startsWith("java.") && !to.startsWith("java.awt."))) {
                        "dev.jasper.app.layout is JDK-only and holds no Swing, AWT or file format: $line"
                    }
                if (module == app && packageOf(from) == "dev.jasper.app.vmoptions")
                    check(to.startsWith("dev.jasper.app.vmoptions.") || to.startsWith("com.sun.management.")
                        || (jdk(to) && !to.startsWith("java.awt.") && !to.startsWith("javax.swing."))) {
                        "dev.jasper.app.vmoptions is JDK-only and holds no Swing or AWT: $line"
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
                val removed = setOf("dev.jasper.app.appearance.LegacyAppearanceDefaults", "dev.jasper.app.appearance.WorkspaceDefaults",
                    "dev.jasper.app.appearance.AppListUI", "dev.jasper.app.workspace.WorkspaceStyler", "dev.jasper.app.workspace.WorkspaceStyles",
                    "dev.jasper.app.workspace.WorkspaceStyleLayer", "dev.jasper.app.workspace.WorkspaceComboPopup")
                check(types.none { topLevel(it) in removed }) { "Removed private presentation adapter reintroduced" }
                for (batch in types.chunked(100)) {
                    val code = tool("javap", listOf("-classpath", classes.absolutePath, "-c", "-p") + batch)
                    check(removed.none { code.contains(it.replace('.', '/')) }) { "Application references removed private presentation adapters" }
                    check(!code.contains("dev/jasper/terminal/internal/") && !code.contains("TerminalSession.internalAccess")) {
                        "Application bypasses supported terminal API"
                    }
                }
                // Workspace tabs spec 6.1, one main window per process: MainWindow, its WindowManager and the native
                // WindowFrame are each made only at the sites below, with an explicit `new`, the type's own code
                // included: its `new` names the class, where javap prints its own constructor calls unqualified, and a
                // this(...) or super(...) call is no `new`. The constant pool (javap -v) also shows a constructor
                // reference (X::new), which javap -c alone would miss; it counts as a site.
                val pinned = mapOf(
                    "dev/jasper/app/workspace/MainWindow" to listOf("dev.jasper.app.workspace.WindowManager.ensureMain"),
                    "dev/jasper/app/workspace/WindowManager" to listOf("dev.jasper.app.application.JasperApplication.<init>"),
                    "dev/jasper/app/workspace/WindowFrame" to listOf("dev.jasper.app.application.JasperApplication.frameOnScreen",
                        "dev.jasper.app.application.JasperApplication.rehearsalWindow"))
                val sites = pinned.keys.associateWith { mutableListOf<String>() }
                val header = Regex("^  \\S.*?([\\w$.]+)\\(.*\\)( throws .*)?;$")
                val created = Regex("^\\s+\\d+: new\\s+#\\d+\\s+// class (\\S+)\\s*$")
                for (batch in types.chunked(100)) {
                    val verbose = tool("javap", listOf("-classpath", classes.absolutePath, "-v", "-p") + batch)
                    for (section in verbose.split(Regex("(?m)^Classfile ")).drop(1)) {
                        if (pinned.keys.none { section.contains("$it.\"<init>\"") }) continue
                        val type = Regex("this_class: #\\d+\\s+// (\\S+)").find(section)?.groupValues?.get(1)?.replace('/', '.') ?: continue
                        var method = "(class)"
                        for (line in section.lines()) {
                            header.find(line)?.let { declared ->
                                val name = declared.groupValues[1]
                                method = when {
                                    name.contains('.') -> "<init>"
                                    name.startsWith("lambda$") -> name.removePrefix("lambda$").substringBefore('$').let { if (it == "new") "<init>" else it }
                                    else -> name
                                }
                            }
                            val made = created.find(line)?.groupValues?.get(1)
                            for ((pinnedType, list) in sites) {
                                if (made == pinnedType) list.add("${topLevel(type)}.$method")
                                else if (line.contains("REF_newInvokeSpecial") && line.contains("$pinnedType.\"<init>\""))
                                    list.add("${topLevel(type)}::new")
                            }
                        }
                    }
                }
                for ((made, allowed) in pinned) check(sites.getValue(made).sorted() == allowed.sorted()) {
                    "${made.substringAfterLast('/')} is made only at ${allowed.joinToString()}: ${sites.getValue(made)}"
                }
            }
        }
    }
}
gradle.projectsEvaluated {
    app.tasks.named("check") { dependsOn(verifyApplicationArchitecture) }
    buddy.tasks.named("check") { dependsOn(verifyApplicationArchitecture) }
}
