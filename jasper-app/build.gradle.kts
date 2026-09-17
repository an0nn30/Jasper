plugins {
    application
}

dependencies {
    implementation(project(":jasper-terminal"))
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

tasks.test {
    val configExample = rootProject.layout.projectDirectory.file("config.example.toml")
    inputs.file(configExample).withPathSensitivity(PathSensitivity.RELATIVE)
    inputs.files(rootProject.file("packaging/icons/Jasper.icns"), rootProject.file("packaging/icons/Jasper.ico"))
        .withPathSensitivity(PathSensitivity.RELATIVE)
    systemProperty("jasper.projectDir", rootProject.layout.projectDirectory.asFile.absolutePath)
}

// Explicit opt-in; check never depends on these native tasks. Use --args for exact reproducible inputs.
for ((taskName, entryPoint) in listOf("bench" to "Bench", "memoryBench" to "MemoryBench")) {
    tasks.register<JavaExec>(taskName) {
        group = "verification"
        description = "Opt-in native ${if (taskName == "bench") "throughput" else "memory"} benchmark; requires --args with --output and --revision."
        classpath = sourceSets["main"].runtimeClasspath
        mainClass = "dev.jasper.app.$entryPoint"
        jvmArgs("--enable-native-access=ALL-UNNAMED", "-Dapple.awt.application.name=Jasper benchmark")
    }
}

// Actual application components and controlled PTY fixture; this never creates a JFrame.
tasks.register<JavaExec>("mockUiPreview") {
    group = "verification"
    description = "Renders reference-sized dark/light Swing previews headlessly."
    dependsOn(tasks.testClasses)
    classpath = sourceSets["test"].runtimeClasspath
    mainClass = "dev.jasper.app.MockUiPreview"
    jvmArgs("-Djava.awt.headless=true", "--enable-native-access=ALL-UNNAMED")
    args(rootProject.layout.projectDirectory.dir("docs/design").asFile.absolutePath)
}

// Explicit opt-in verification tools. They use real app components from the test
// runtime, but check never runs them and they never create a native window.
for ((taskName, entryPoint) in listOf(
    "commandPalettePreview" to "CommandPalettePreview",
    "buddyNotificationPreview" to "BuddyNotificationPreview",
    "titleBarPreview" to "TitleBarPreview",
    "commandSearchMeasurement" to "CommandSearchMeasurement",
    "shellHistorySearchMeasurement" to "ShellHistorySearchMeasurement",
)) {
    tasks.register<JavaExec>(taskName) {
        group = "verification"
        dependsOn(tasks.testClasses)
        classpath = sourceSets["test"].runtimeClasspath
        mainClass = "dev.jasper.app.$entryPoint"
        jvmArgs("-Djava.awt.headless=true", "--enable-native-access=ALL-UNNAMED")
        providers.gradleProperty("jasper.uiScale").orNull?.let {
            systemProperty("flatlaf.uiScale", it)
        }
    }
}

apply(from = rootProject.file("gradle/packaging.gradle"))
