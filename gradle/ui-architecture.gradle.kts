import org.gradle.jvm.toolchain.JavaToolchainService
import org.gradle.jvm.toolchain.JavaLanguageVersion
import org.gradle.jvm.toolchain.JvmVendorSpec

// UI is independent of every other Jasper module; pure value/parser packages never use Swing.
val uiProject = project(":jasper-ui")
val verifyUiArchitecture = tasks.register("verifyUiArchitecture") {
    dependsOn(":jasper-ui:classes")
    doLast {
        val launcher = uiProject.extensions.getByType<JavaToolchainService>().launcherFor {
            languageVersion = JavaLanguageVersion.of(25)
            vendor = JvmVendorSpec.JETBRAINS
        }.get()
        val bin = launcher.metadata.installationPath.dir("bin").asFile
        val suffix = if (System.getProperty("os.name").startsWith("Windows")) ".exe" else ""
        val classes = uiProject.layout.buildDirectory.dir("classes/java/main").get().asFile
        val classpath = uiProject.configurations.getByName("runtimeClasspath").asPath
        val process = ProcessBuilder(listOf(bin.resolve("jdeps$suffix").absolutePath,
            "--multi-release", "25", "--ignore-missing-deps", "-verbose:class", "-filter:none",
            "--class-path", classpath, classes.absolutePath)).redirectErrorStream(true).start()
        val output = process.inputStream.bufferedReader().readText()
        check(process.waitFor() == 0) { output }
        val edge = Regex("^\\s*(dev\\.jasper\\.ui\\.\\S+)\\s+->\\s+(\\S+).*$")
        val pure = setOf("dev.jasper.ui.theme", "dev.jasper.ui.model", "dev.jasper.ui.themeio", "dev.jasper.ui.themeimport")
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
            val jdkOnly = from == "dev.jasper.ui.theme" || from.startsWith("dev.jasper.ui.theme.")
                || from == "dev.jasper.ui.model" || from.startsWith("dev.jasper.ui.model.")
            check(!jdkOnly || toClass.startsWith("dev.jasper.ui.") || toClass.startsWith("java.")
                || toClass.startsWith("javax.") || toClass.startsWith("jdk.")) {
                "Vendor dependency in JDK-only UI package: $line"
            }
            if (toClass.startsWith("dev.jasper.ui.")) {
                val to = toClass.substringBeforeLast('.')
                if (from != to) graph.getOrPut(from) { linkedSetOf() }.add(to)
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
