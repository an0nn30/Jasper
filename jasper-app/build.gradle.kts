plugins {
    application
}

dependencies {
    testImplementation(testFixtures(project(":jasper-buddy")))
    implementation(project(":jasper-ui"))
    testImplementation(testFixtures(project(":jasper-ui")))
    implementation(project(":jasper-terminal"))
    implementation(project(":jasper-buddy"))
    implementation(project(":jasper-sdk"))
    testImplementation(project(":jasper-sdk-testkit"))
    testImplementation(testFixtures(project(":jasper-sdk-testkit")))
    implementation("org.tomlj:tomlj:1.1.1")
    implementation("com.formdev:flatlaf:3.7")
    implementation("org.jetbrains.runtime:jbr-api:1.9.0")
    implementation("com.formdev:flatlaf-extras:3.7")
    // flatlaf-extras brings jsvg at runtime only; javac needs its SVGDocument to resolve FlatSVGUtils.svg2image.
    // Keep this the version flatlaf-extras declares.
    compileOnly("com.github.weisj:jsvg:2.0.0")
    runtimeOnly("org.slf4j:slf4j-nop:2.0.13")
}

// Workspace glass (macOS only): AWT refuses translucency on a decorated frame, so WindowGlass reaches the peer
// directly; only a macOS build host opens those JDK internals.
val glassJvmArgs = if (System.getProperty("os.name").lowercase().startsWith("mac"))
    listOf("--add-opens=java.desktop/java.awt=ALL-UNNAMED", "--add-exports=java.desktop/java.awt.peer=ALL-UNNAMED")
else emptyList()

application {
    mainClass = "dev.jasper.app.Main"
    // The heap default and IntelliJ's tolerance of outdated flags; a user's jasper.vmoptions overrides them.
    applicationDefaultJvmArgs = listOf(
        "--enable-native-access=ALL-UNNAMED",
        "-Dapple.awt.application.name=Jasper",
        "-Xmx2048m",
        "-XX:+IgnoreUnrecognizedVMOptions",
    ) + glassJvmArgs
}

// Bundled plugins are separate jars in plugins/<id>/, never on the application classpath.
val stagePlugins = tasks.register<Sync>("stagePlugins") {
    from(project(":jasper-plugin-sample").tasks.named("jar")) { into("dev.jasper.sample") }
        from(project(":jasper-plugin-snippets").tasks.named("jar")) { into("dev.jasper.snippets") }
        from(project(":jasper-plugin-snippets").configurations.named("runtimeClasspath")) { into("dev.jasper.snippets") }
    from(project(":jasper-plugin-history").tasks.named("jar")) { into("dev.jasper.history") }
    from(project(":jasper-plugin-vault").tasks.named("jar")) { into("dev.jasper.vault") }
    from(project(":jasper-plugin-vault").configurations.named("runtimeClasspath")) { into("dev.jasper.vault") }
    from(project(":jasper-plugin-remote").tasks.named("jar")) { into("dev.jasper.remote") }
    from(project(":jasper-plugin-remote").configurations.named("runtimeClasspath")) { into("dev.jasper.remote") }
    from(project(":jasper-plugin-editor").tasks.named("jar")) { into("dev.jasper.editor") }
    from(project(":jasper-plugin-editor").configurations.named("runtimeClasspath")) { into("dev.jasper.editor") }
    from(project(":jasper-plugin-fileserver").tasks.named("jar")) { into("dev.jasper.fileserver") }
    from(project(":jasper-plugin-fileserver").configurations.named("runtimeClasspath")) { into("dev.jasper.fileserver") }
    from(project(":jasper-plugin-indexer").tasks.named("jar")) { into("dev.jasper.indexer") }
    from(project(":jasper-plugin-indexer").configurations.named("runtimeClasspath")) { into("dev.jasper.indexer") }
    from(project(":jasper-plugin-enterprise").tasks.named("jar")) { into("dev.jasper.enterprise") }
    into(layout.buildDirectory.dir("plugins"))
}
// installDist and the distribution archives carry them beside the application jar, where the runtime looks.
distributions { main { contents {
    from(rootProject.file("LICENSE.md"))
    from(stagePlugins) { into("lib/plugins") }
    // Portable distributions expose the same desktop icon set used by Linux windows.
    from(rootProject.file("packaging/icons/linux/hicolor")) { into("share/icons/hicolor") }
} } }
// Jasper's own license has one copy, at the root; the Licenses window reads it from the jar.
tasks.named<ProcessResources>("processResources") {
    from(rootProject.file("LICENSE.md")) {
        into("dev/jasper/app/about/licenses")
        rename { "jasper.md" }
    }
}
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
    // Explicit opt-in scaled geometry tests; use the same property as headless previews.
    providers.gradleProperty("jasper.uiScale").orNull?.let { systemProperty("flatlaf.uiScale", it) }
    systemProperty("jasper.repoRoot", rootProject.projectDir.absolutePath)
    // Tests start the staged plugins and install the zips, so their contents are inputs (which also brings
    // the task dependency); a path property alone left this task UP-TO-DATE after a plugin's code changed.
    inputs.files(stagePlugins).withPropertyName("stagedPlugins").withPathSensitivity(PathSensitivity.RELATIVE)
    systemProperty("jasper.stagedPlugins", layout.buildDirectory.dir("plugins").get().asFile.absolutePath)
    // The installable zips, so a test can stage them through the real installer.
    inputs.files(rootProject.tasks.named("pluginZips")).withPropertyName("pluginZips").withPathSensitivity(PathSensitivity.RELATIVE)
    systemProperty("jasper.pluginZips", rootProject.layout.projectDirectory.dir("plugins/build/zips").asFile.absolutePath)
    dependsOn(tasks.jar, ":jasper-buddy:jar", ":jasper-terminal:jar", ":jasper-ui:jar")
    systemProperty("jasper.appJar", tasks.jar.get().archiveFile.get().asFile.absolutePath)
    systemProperty("jasper.buddyJar", project(":jasper-buddy").layout.buildDirectory.file("libs/jasper-buddy.jar").get().asFile.absolutePath)
    systemProperty("jasper.terminalJar", project(":jasper-terminal").layout.buildDirectory.file("libs/jasper-terminal.jar").get().asFile.absolutePath)
    systemProperty("jasper.uiJar", project(":jasper-ui").layout.buildDirectory.file("libs/jasper-ui.jar").get().asFile.absolutePath)
    val configExample = rootProject.layout.projectDirectory.file("config.example.toml")
    inputs.file(configExample).withPathSensitivity(PathSensitivity.RELATIVE)
    // The Enterprise conformance, fixture and render tests read the plugin's sources (spec 16.1.5 and 16.1.6).
    inputs.dir(rootProject.file("plugins/enterprise/src/main/resources")).withPathSensitivity(PathSensitivity.RELATIVE)
    inputs.dir(rootProject.file("tools/themes/testdata/expected")).withPathSensitivity(PathSensitivity.RELATIVE)
    inputs.file(rootProject.file("docs/design/theme-engine/migration.csv")).withPathSensitivity(PathSensitivity.RELATIVE)
    // Plan 8a: RunWindowRenderTest compares with the 06 Run Dialog mocks.
    inputs.dir(rootProject.file("docs/design/enterprise/reference/06 Run Dialog")).withPropertyName("runDialogMocks").withPathSensitivity(PathSensitivity.RELATIVE)
    inputs.dir(rootProject.file("docs/design/enterprise/reference")).withPathSensitivity(PathSensitivity.RELATIVE)
    inputs.files(rootProject.fileTree("docs/design/theme-engine/before-f16ae32f")).withPathSensitivity(PathSensitivity.RELATIVE)
    inputs.file(rootProject.layout.projectDirectory.file("docs/configuration.md")).withPathSensitivity(PathSensitivity.RELATIVE)
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
        jvmArgs(glassJvmArgs)
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

// Opt-in render of the Settings dialog, dark and light, default and 18 pt UI font. Never creates a native window.
tasks.register<JavaExec>("settingsPreview") {
    group = "verification"
    description = "Renders the Settings dialog headlessly into build/settings-preview."
    dependsOn(tasks.testClasses)
    dependsOn(stagePlugins)
    systemProperty("jasper.stagedPlugins", layout.buildDirectory.dir("plugins").get().asFile.absolutePath)
    classpath = sourceSets["test"].runtimeClasspath
    mainClass = "dev.jasper.app.settingsui.SettingsDialogPreview"
    jvmArgs("-Djava.awt.headless=true", "--enable-native-access=ALL-UNNAMED")
    args(layout.buildDirectory.dir("settings-preview").get().asFile.absolutePath)
    providers.gradleProperty("jasper.uiScale").orNull?.let { systemProperty("flatlaf.uiScale", it) }
}

// Explicit opt-in verification tools. They use real app components from the test
// runtime, but check never runs them and they never create a native window.
for ((taskName, entryPoint) in listOf(
    "hostedContentPreview" to "HostedContentPreview",
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
            "HostedContentPreview" -> "dev.jasper.app.workspace.$entryPoint"
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

// The commit this jar is built from (dev/jasper/app/build-info.properties); packaging.gradle reads its version from it.
apply(from = rootProject.file("gradle/build-info.gradle"))
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
        rootProject.fileTree("plugins/sample/src") { include("**/*.java") }, rootProject.fileTree("packaging") { include("**/*.md") },
        // The example external plugin's descriptor and build script (not its own build output).
        rootProject.fileTree("docs/examples") { include("**/*.toml", "**/*.kts"); exclude("**/build/**", "**/.gradle/**") },
        // ColourLiteralScanTest reads every production source and resource, PromptVocabularyTest every source.
        rootProject.fileTree("jasper-ui/src"), rootProject.fileTree("jasper-sdk/src"), rootProject.fileTree("jasper-sdk-testkit/src"),
        rootProject.fileTree("plugins") { include("*/src/**") })
        .withPathSensitivity(PathSensitivity.RELATIVE)
}

// The actual shortcut reference, rendered without creating a native window.
tasks.register<JavaExec>("shortcutHelpPreview") {
    group = "verification"
    dependsOn(tasks.testClasses)
    classpath = sourceSets["test"].runtimeClasspath
    mainClass = "dev.jasper.app.shortcuthelp.ShortcutHelpPreview"
    jvmArgs("-Djava.awt.headless=true")
    args(layout.buildDirectory.dir("reports/shortcut-help").get().asFile.absolutePath)
}

// Exact device-scale captures of real staged feature owners in disposable homes.
tasks.register<JavaExec>("themeSurfacePreview") {
    group = "verification"
    description = "Captures actual mounted feature views for reference calibration, headlessly."
    dependsOn(tasks.testClasses, stagePlugins)
    classpath = sourceSets["test"].runtimeClasspath
    mainClass = "dev.jasper.app.appearance.SurfacePreview"
    providers.gradleProperty("jasper.uiScale").orNull?.let { systemProperty("flatlaf.uiScale", it) }
    systemProperty("jasper.stagedPlugins", layout.buildDirectory.dir("plugins").get().asFile.absolutePath)
    args(layout.buildDirectory.dir("theme-surfaces").get().asFile.absolutePath)
    jvmArgs("-Djava.awt.headless=true", "--enable-native-access=ALL-UNNAMED")
}

// Exact-scale actual Settings owners, with synthetic config and declared notification fixtures.
tasks.register<JavaExec>("settingsSurfacePreview") {
    group = "verification"
    dependsOn(tasks.testClasses, stagePlugins)
    classpath = sourceSets["test"].runtimeClasspath
    mainClass = "dev.jasper.app.settingsui.SettingsSurfacePreview"
    systemProperty("jasper.stagedPlugins", layout.buildDirectory.dir("plugins").get().asFile.absolutePath)
    args(layout.buildDirectory.dir("theme-surfaces").get().asFile.absolutePath)
    jvmArgs("-Djava.awt.headless=true", "--enable-native-access=ALL-UNNAMED")
}

// Actual Task 9b Settings owners; separate output preserves the approved 9a archive.
tasks.register<JavaExec>("settings9bPreview") {
    group = "verification"
    dependsOn(tasks.testClasses, stagePlugins)
    classpath = sourceSets["test"].runtimeClasspath
    mainClass = "dev.jasper.app.settingsui.Settings9bPreview"
    val captureHome = layout.buildDirectory.dir("settings-preview-home").get().asFile.absolutePath
    systemProperty("user.home", captureHome)
    environment("HISTFILE", "$captureHome/.zsh_history")
    environment("XDG_CONFIG_HOME", "$captureHome/.config")
    environment("APPDATA", "$captureHome/AppData/Roaming")
    systemProperty("jasper.stagedPlugins", layout.buildDirectory.dir("plugins").get().asFile.absolutePath)
    args(layout.buildDirectory.dir("theme-surfaces9b").get().asFile.absolutePath)
    jvmArgs("-Djava.awt.headless=true", "--enable-native-access=ALL-UNNAMED")
}

// Actual workspace chrome (title bar, tabs, rails, dock, status) with staged plugins at the Desktop references' sizes.
// The disposable home and user name are fixture data for the header context; no native window or login shell.
tasks.register<JavaExec>("workspaceSurfacePreview") {
    group = "verification"
    description = "Captures the actual workspace chrome and integrated panels headlessly at device scale 2."
    dependsOn(tasks.testClasses, stagePlugins)
    classpath = sourceSets["test"].runtimeClasspath
    mainClass = "dev.jasper.app.workspace.WorkspaceSurfacePreview"
    providers.gradleProperty("jasper.uiScale").orNull?.let { systemProperty("flatlaf.uiScale", it) }
    systemProperty("jasper.stagedPlugins", layout.buildDirectory.dir("plugins").get().asFile.absolutePath)
    val captureHome = layout.buildDirectory.dir("workspace-preview-home").get().asFile.absolutePath
    systemProperty("user.home", captureHome)
    systemProperty("user.name", "dustin")
    environment("HISTFILE", "$captureHome/.zsh_history")
    environment("XDG_CONFIG_HOME", "$captureHome/.config")
    environment("APPDATA", "$captureHome/AppData/Roaming")
    args(listOf(layout.buildDirectory.dir("workspace-surfaces").get().asFile.absolutePath) +
        (providers.gradleProperty("jasper.workspaceFilter").orNull?.let { listOf(it) } ?: emptyList()))
    jvmArgs("-Djava.awt.headless=true", "--enable-native-access=ALL-UNNAMED")
}

// Mounted management dialogs and the Settings-owned restart conversation, with inert native/process boundaries.
tasks.register<JavaExec>("pluginManagerStatesPreview") {
    group = "verification"
    dependsOn(tasks.testClasses)
    classpath = sourceSets["test"].runtimeClasspath
    mainClass = "dev.jasper.app.pluginmanager.PluginManagerStatesPreview"
    args(layout.buildDirectory.dir("theme-surfaces9b/plugin-states").get().asFile.absolutePath)
    jvmArgs("-Djava.awt.headless=true", "--enable-native-access=ALL-UNNAMED")
}
