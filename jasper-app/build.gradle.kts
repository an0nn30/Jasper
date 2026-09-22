plugins {
    application
}

dependencies {
    testImplementation(testFixtures(project(":jasper-buddy")))
    implementation(project(":jasper-terminal"))
    implementation(project(":jasper-buddy"))
    implementation(project(":jasper-sdk"))
    testImplementation(project(":jasper-sdk-testkit"))
    testImplementation(testFixtures(project(":jasper-sdk-testkit")))
    implementation("org.tomlj:tomlj:1.1.1")
    implementation("com.formdev:flatlaf:3.7")
    implementation("org.jetbrains.runtime:jbr-api:1.9.0")
    implementation("com.formdev:flatlaf-extras:3.7")
    runtimeOnly("org.slf4j:slf4j-nop:2.0.13")
}

application {
    mainClass = "dev.jasper.app.Main"
    applicationDefaultJvmArgs = listOf(
        "--enable-native-access=ALL-UNNAMED",
        "-Dapple.awt.application.name=Jasper",
    )
}

// Bundled plugins are separate jars in plugins/<id>/, never on the application classpath.
val stagePlugins = tasks.register<Sync>("stagePlugins") {
    from(project(":jasper-plugin-sample").tasks.named("jar")) { into("dev.jasper.sample") }
        from(project(":jasper-plugin-snippets").tasks.named("jar")) { into("dev.jasper.snippets") }
        from(project(":jasper-plugin-snippets").configurations.named("runtimeClasspath")) { into("dev.jasper.snippets") }
    from(project(":jasper-plugin-history").tasks.named("jar")) { into("dev.jasper.history") }
    from(project(":jasper-plugin-vault").tasks.named("jar")) { into("dev.jasper.vault") }
    from(project(":jasper-plugin-vault").configurations.named("runtimeClasspath")) { into("dev.jasper.vault") }
    into(layout.buildDirectory.dir("plugins"))
}
// installDist and the distribution archives carry them beside the application jar, where the runtime looks.
distributions { main { contents {
    from(stagePlugins) { into("lib/plugins") }
    // Portable distributions expose the same desktop icon set used by Linux windows.
    from(rootProject.file("packaging/icons/linux/hicolor")) { into("share/icons/hicolor") }
} } }
// A development launch keeps its own home (plugins, consent, data, history, snippets, logs, socket)
// under build/, apart from the installed app's ~/.config/jasper. The IntelliJ run configuration in
// .run/ sets the same two properties. Override with -Pjasper.home=<dir> or the JASPER_HOME variable.
tasks.named<JavaExec>("run") {
    dependsOn(stagePlugins)
    systemProperty("jasper.plugins.bundled", layout.buildDirectory.dir("plugins").get().asFile.absolutePath)
    systemProperty("jasper.home", (project.findProperty("jasper.home") as String?)
        ?: layout.buildDirectory.dir("dev-home").get().asFile.absolutePath)
}

tasks.test {
    systemProperty("jasper.repoRoot", rootProject.projectDir.absolutePath)
    dependsOn(stagePlugins)
    systemProperty("jasper.stagedPlugins", layout.buildDirectory.dir("plugins").get().asFile.absolutePath)
    // The installable zips, so a test can stage them through the real installer.
    dependsOn(rootProject.tasks.named("pluginZips"))
    systemProperty("jasper.pluginZips", rootProject.layout.projectDirectory.dir("plugins/build/zips").asFile.absolutePath)
    dependsOn(tasks.jar, ":jasper-buddy:jar", ":jasper-terminal:jar")
    systemProperty("jasper.appJar", tasks.jar.get().archiveFile.get().asFile.absolutePath)
    systemProperty("jasper.buddyJar", project(":jasper-buddy").layout.buildDirectory.file("libs/jasper-buddy.jar").get().asFile.absolutePath)
    systemProperty("jasper.terminalJar", project(":jasper-terminal").layout.buildDirectory.file("libs/jasper-terminal.jar").get().asFile.absolutePath)
    val configExample = rootProject.layout.projectDirectory.file("config.example.toml")
    inputs.file(configExample).withPathSensitivity(PathSensitivity.RELATIVE)
    inputs.files(rootProject.file("packaging/icons/Jasper.icns"), rootProject.file("packaging/icons/Jasper.ico"),
        rootProject.file("packaging/icons/Jasper.png"), rootProject.fileTree("packaging/icons/linux") { include("**/*.png") })
        .withPathSensitivity(PathSensitivity.RELATIVE)
    systemProperty("jasper.projectDir", rootProject.layout.projectDirectory.asFile.absolutePath)
}

// Explicit opt-in; check never depends on these native tasks. Use --args for exact reproducible inputs.
for ((taskName, entryPoint) in listOf("bench" to "Bench", "memoryBench" to "MemoryBench")) {
    tasks.register<JavaExec>(taskName) {
        group = "verification"
        description = "Opt-in native ${if (taskName == "bench") "throughput" else "memory"} benchmark; requires --args with --output and --revision."
        classpath = sourceSets["main"].runtimeClasspath
        mainClass = "dev.jasper.app.benchmark.$entryPoint"
        jvmArgs("--enable-native-access=ALL-UNNAMED", "-Dapple.awt.application.name=Jasper benchmark")
    }
}

// Actual application components and controlled PTY fixture; this never creates a JFrame.
tasks.register<JavaExec>("mockUiPreview") {
    group = "verification"
    description = "Renders reference-sized dark/light Swing previews headlessly."
    dependsOn(tasks.testClasses)
    classpath = sourceSets["test"].runtimeClasspath
    mainClass = "dev.jasper.app.workspace.MockUiPreview"
    jvmArgs("-Djava.awt.headless=true", "--enable-native-access=ALL-UNNAMED")
    args(rootProject.layout.projectDirectory.dir("docs/design").asFile.absolutePath)
}

// Explicit opt-in verification tools. They use real app components from the test
// runtime, but check never runs them and they never create a native window.
for ((taskName, entryPoint) in listOf(
    "commandPalettePreview" to "CommandPalettePreview",
    "titleBarPreview" to "TitleBarPreview",
    "commandSearchMeasurement" to "CommandSearchMeasurement",
    "shellHistorySearchMeasurement" to "ShellHistorySearchMeasurement",
)) {
    tasks.register<JavaExec>(taskName) {
        group = "verification"
        dependsOn(tasks.testClasses)
        classpath = sourceSets["test"].runtimeClasspath
        mainClass = when (entryPoint) {
            "CommandPalettePreview" -> "dev.jasper.app.workspace.$entryPoint"
            "TitleBarPreview" -> "dev.jasper.app.workspace.$entryPoint"
            "CommandSearchMeasurement" -> "dev.jasper.app.commands.$entryPoint"
            else -> "dev.jasper.app.history.$entryPoint"
        }
        jvmArgs("-Djava.awt.headless=true", "--enable-native-access=ALL-UNNAMED")
        providers.gradleProperty("jasper.uiScale").orNull?.let {
            systemProperty("flatlaf.uiScale", it)
        }
    }
}

apply(from = rootProject.file("gradle/packaging.gradle"))

// Keep API/package contracts in the ordinary headless verification path.
tasks.withType<Javadoc>().configureEach {
    isFailOnError = true
    (options as StandardJavadocDocletOptions).apply {
        encoding = "UTF-8"
        addBooleanOption("Xdoclint:all", true)
    }
}
tasks.named("check") { dependsOn(tasks.named("javadoc")) }

// Documentation tests read these files at runtime, so docs-only edits must invalidate test results.
tasks.test {
    inputs.files(rootProject.fileTree("docs") { include("**/*.md") },
        rootProject.file("README.md"), rootProject.file("AGENTS.md"),
        rootProject.file("jasper-app/README.md"), rootProject.file("jasper-terminal/README.md"),
        rootProject.file("jasper-buddy/README.md"), rootProject.file("jasper-sdk/README.md"),
        // Sources only: the plugin project's build directory holds other tasks' outputs and must not be an input here.
        rootProject.fileTree("plugins/sample/src") { include("**/*.java") }, rootProject.fileTree("packaging") { include("**/*.md") })
        .withPathSensitivity(PathSensitivity.RELATIVE)
}
