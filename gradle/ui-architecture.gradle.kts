import org.gradle.jvm.toolchain.JavaToolchainService
import org.gradle.jvm.toolchain.JavaLanguageVersion
import org.gradle.jvm.toolchain.JvmVendorSpec

@Suppress("UNCHECKED_CAST")
val verificationClasspaths = extra["verificationClasspaths"] as Map<String, org.gradle.api.artifacts.Configuration>

// UI is independent of every other Jasper module; pure value/parser packages never use Swing.
val uiProject = project(":jasper-ui")
val verifyUiArchitecture = tasks.register("verifyUiArchitecture") {
    dependsOn(":jasper-ui:classes")
    inputs.files(verificationClasspaths.getValue(uiProject.path))
    doLast {
        val launcher = uiProject.extensions.getByType<JavaToolchainService>().launcherFor {
            languageVersion = JavaLanguageVersion.of(25)
            vendor = JvmVendorSpec.JETBRAINS
        }.get()
        val bin = launcher.metadata.installationPath.dir("bin").asFile
        val suffix = if (System.getProperty("os.name").startsWith("Windows")) ".exe" else ""
        val classes = uiProject.layout.buildDirectory.dir("classes/java/main").get().asFile
        val classpath = verificationClasspaths.getValue(uiProject.path).asPath
        val process = ProcessBuilder(listOf(bin.resolve("jdeps$suffix").absolutePath,
            "--multi-release", "25", "--ignore-missing-deps", "-verbose:class", "-filter:none",
            "--class-path", classpath, classes.absolutePath)).redirectErrorStream(true).start()
        val output = process.inputStream.bufferedReader().readText()
        check(process.waitFor() == 0) { output }
        val edge = Regex("^\\s*(dev\\.jasper\\.ui\\.\\S+)\\s+->\\s+(\\S+).*$")
        val pure = setOf("dev.jasper.ui.theme", "dev.jasper.ui.model", "dev.jasper.ui.kit")
        // Each package's allowed outgoing UI packages: the ones its package-info's "Allowed outgoing Jasper
        // dependencies:" line names; a package-info without that line (theme, model) allows none.
        val sources = uiProject.layout.projectDirectory.dir("src/main/java/dev/jasper/ui").asFile
        val listed = Regex("Allowed outgoing Jasper dependenc(?:y|ies): (.*)")
        val named = Regex("dev\\.jasper\\.ui\\.[a-z]+")
        val allowed = sources.listFiles().orEmpty().filter { it.resolve("package-info.java").isFile }.associate { dir ->
            val names = listed.find(dir.resolve("package-info.java").readText())?.groupValues?.get(1).orEmpty()
            "dev.jasper.ui.${dir.name}" to named.findAll(names).map { it.value }.toSet()
        }
        val graph = mutableMapOf<String, MutableSet<String>>()
        output.lineSequence().forEach { line ->
            val match = edge.matchEntire(line) ?: return@forEach
            val (fromClass, toClass) = match.destructured
            val from = fromClass.substringBeforeLast('.')
            check(!toClass.startsWith("dev.jasper.") || toClass.startsWith("dev.jasper.ui.")) {
                "UI depends on another Jasper module: $line"
            }
            check(pure.none { from == it || from.startsWith("$it.") } || !toClass.startsWith("javax.swing.")) {
                "Swing dependency in pure UI package: $line"
            }
            val jdkOnly = pure.any { from == it || from.startsWith("$it.") }
            check(!jdkOnly || toClass.startsWith("dev.jasper.ui.") || toClass.startsWith("java.")
                || toClass.startsWith("javax.") || toClass.startsWith("jdk.")) {
                "Vendor dependency in JDK-only UI package: $line"
            }
            if (toClass.startsWith("dev.jasper.ui.")) {
                val to = toClass.substringBeforeLast('.')
                if (from != to) {
                    val permitted = allowed[from] ?: error("UI package without a package-info: $from")
                    check(to in permitted) { "UI package edge outside its package-info: $line" }
                    graph.getOrPut(from) { linkedSetOf() }.add(to)
                }
            }
        }
        val active = linkedSetOf<String>()
        val done = mutableSetOf<String>()
        fun visit(node: String) {
            if (node in done) return
            check(active.add(node)) { "UI package cycle: $active -> $node" }
            graph[node].orEmpty().forEach { visit(it) }
            active.remove(node)
            done.add(node)
        }
        graph.keys.forEach { visit(it) }
    }
}
gradle.projectsEvaluated {
    uiProject.tasks.named("check") { dependsOn(verifyUiArchitecture) }
}
