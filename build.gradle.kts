subprojects {
    apply(plugin = "java")

    repositories {
        mavenCentral()
        maven("https://packages.jetbrains.team/maven/p/ij/intellij-dependencies") {
            content { includeGroup("org.jetbrains.jediterm") }
        }
    }

    extensions.configure<JavaPluginExtension> {
        toolchain {
            languageVersion = JavaLanguageVersion.of(25)
            vendor = JvmVendorSpec.JETBRAINS
        }
    }

    dependencies {
        "testImplementation"(platform("org.junit:junit-bom:6.1.3"))
        "testImplementation"("org.junit.jupiter:junit-jupiter")
        "testRuntimeOnly"("org.junit.platform:junit-platform-launcher")
        "testImplementation"("org.assertj:assertj-core:3.27.7")
    }

    tasks.withType<JavaCompile>().configureEach {
        options.compilerArgs.addAll(listOf("-Xlint:all", "-Xlint:-serial"))
    }

    tasks.withType<Test>().configureEach {
        useJUnitPlatform()
        systemProperty("java.awt.headless", "true")
        jvmArgs("--enable-native-access=ALL-UNNAMED")
    }
}

// Enforce the supported API and an acyclic terminal package graph from bytecode.
val terminalProject = project(":jasper-terminal")
val appProject = project(":jasper-app")
val verifyTerminalArchitecture = tasks.register("verifyTerminalArchitecture") {
    dependsOn(":jasper-terminal:classes", ":jasper-app:classes")
    doLast {
        val toolchains = terminalProject.extensions.getByType<JavaToolchainService>()
        val launcher = toolchains.launcherFor {
            languageVersion = JavaLanguageVersion.of(25)
            vendor = JvmVendorSpec.JETBRAINS
        }.get()
        val bin = launcher.metadata.installationPath.dir("bin").asFile
        val suffix = if (System.getProperty("os.name").startsWith("Windows")) ".exe" else ""
        fun tool(name: String, args: List<String>): String {
            val process = ProcessBuilder(listOf(bin.resolve(name + suffix).absolutePath) + args)
                .redirectErrorStream(true).start()
            val output = process.inputStream.bufferedReader().readText()
            check(process.waitFor() == 0) { output }
            return output
        }
        val terminalClasses = terminalProject.layout.buildDirectory.dir("classes/java/main").get().asFile
        val appClasses = appProject.layout.buildDirectory.dir("classes/java/main").get().asFile
        val runtime = terminalProject.configurations.getByName("runtimeClasspath").asPath
        val output = tool("jdeps", listOf("--multi-release", "25", "--ignore-missing-deps", "-verbose:class", "-filter:none",
            "--class-path", runtime, terminalClasses.absolutePath))
        val edgePattern = Regex("^\\s*(dev\\.jasper\\.terminal\\.\\S+)\\s+->\\s+(\\S+).*$")
        val graph = mutableMapOf<String, MutableSet<String>>()
        output.lineSequence().forEach { line ->
            val match = edgePattern.matchEntire(line) ?: return@forEach
            val fromClass = match.groupValues[1]
            val toClass = match.groupValues[2]
            val from = fromClass.substringBeforeLast('.')
            check(!toClass.startsWith("dev.jasper.app.")) { line }
            if (toClass.startsWith("com.jediterm."))
                check(from == "dev.jasper.terminal.internal.emulation") { line }
            if (toClass.startsWith("dev.jasper.terminal.")) {
                val to = toClass.substringBeforeLast('.')
                if (from != to) graph.getOrPut(from) { mutableSetOf() }.add(to)
            }
        }
        fun visit(node: String, stack: MutableSet<String>, done: MutableSet<String>) {
            if (node in done) return
            check(stack.add(node)) { "Terminal package cycle: $stack -> $node" }
            graph[node].orEmpty().forEach { visit(it, stack, done) }
            stack.remove(node); done.add(node)
        }
        val done = mutableSetOf<String>()
        graph.keys.forEach { visit(it, linkedSetOf(), done) }
        val supported = setOf(
            "session.TerminalSession", "session.SessionLaunchOptions", "session.TerminalSessionListener",
            "view.TerminalView", "view.TerminalAction", "config.TerminalOptions", "config.Palette",
            "config.CursorStyle", "config.BellMode", "config.OptionAsMeta", "config.GridSize",
            "search.SearchQuery", "search.FindResult", "rendering.FontSet"
        ).map { "dev.jasper.terminal.$it" }.toSet()
        val appOutput = tool("jdeps", listOf("--multi-release", "25", "--ignore-missing-deps", "-verbose:class", "-filter:none",
            "--class-path", runtime + java.io.File.pathSeparator + terminalClasses.absolutePath,
            appClasses.absolutePath))
        val appEdge = Regex("^\\s*(dev\\.jasper\\.app\\.\\S+)\\s+->\\s+(dev\\.jasper\\.terminal\\.\\S+).*$")
        appOutput.lineSequence().forEach { line ->
            val match = appEdge.matchEntire(line) ?: return@forEach
            val target = match.groupValues[2].substringBefore('$')
            check(target in supported) { "Unsupported terminal API: $line" }
        }
        val appTypes = appClasses.walkTopDown().filter { it.extension == "class" }.map {
            it.relativeTo(appClasses).invariantSeparatorsPath.removeSuffix(".class").replace('/', '.')
        }.toList()
        for (types in appTypes.chunked(100)) {
            val bytecode = tool("javap", listOf("-classpath", appClasses.absolutePath, "-c", "-p") + types)
            val type = bytecode
            check(!bytecode.contains("dev/jasper/terminal/internal/")) { "$type uses terminal internals" }
            check(!bytecode.contains("TerminalSession.internalAccess")) { "$type calls internalAccess" }
        }
    }
}

gradle.projectsEvaluated {
    appProject.tasks.named("check") { dependsOn(verifyTerminalArchitecture) }
}

apply(from = "gradle/application-architecture.gradle.kts")

apply(from = "gradle/sdk-architecture.gradle.kts")
apply(from = "gradle/plugin-architecture.gradle.kts")
