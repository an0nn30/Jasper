plugins {
    application
}

dependencies {
    implementation(project(":moray-terminal"))
    implementation("org.tomlj:tomlj:1.1.1")
    implementation("com.formdev:flatlaf:3.7")
    implementation("org.jetbrains.runtime:jbr-api:1.9.0")
    implementation("com.formdev:flatlaf-extras:3.7")
    implementation("com.github.Dansoftowner:jSystemThemeDetector:3.9.1") {
        // pty4j already supplies newer JNA modules; the detector's old JPMS variant is unpublished.
        exclude(group = "net.java.dev.jna")
    }
    runtimeOnly("org.slf4j:slf4j-nop:2.0.13")
}

application {
    mainClass = "dev.moray.app.Main"
    applicationDefaultJvmArgs = listOf(
        "--enable-native-access=ALL-UNNAMED",
        "-Dapple.awt.application.name=Moray",
    )
}

tasks.test {
    val configExample = rootProject.layout.projectDirectory.file("config.example.toml")
    inputs.file(configExample).withPathSensitivity(PathSensitivity.RELATIVE)
    inputs.files(rootProject.file("packaging/icons/Moray.icns"), rootProject.file("packaging/icons/Moray.ico"))
        .withPathSensitivity(PathSensitivity.RELATIVE)
    systemProperty("moray.projectDir", rootProject.layout.projectDirectory.asFile.absolutePath)
}

// Explicit opt-in; check never depends on these native tasks. Use --args for exact reproducible inputs.
for ((taskName, entryPoint) in listOf("bench" to "Bench", "memoryBench" to "MemoryBench")) {
    tasks.register<JavaExec>(taskName) {
        group = "verification"
        description = "Opt-in native ${if (taskName == "bench") "throughput" else "memory"} benchmark; requires --args with --output and --revision."
        classpath = sourceSets["main"].runtimeClasspath
        mainClass = "dev.moray.app.$entryPoint"
        jvmArgs("--enable-native-access=ALL-UNNAMED", "-Dapple.awt.application.name=Moray benchmark")
    }
}

// Actual application components and controlled PTY fixture; this never creates a JFrame.
tasks.register<JavaExec>("mockUiPreview") {
    group = "verification"
    description = "Renders reference-sized dark/light Swing previews headlessly."
    dependsOn(tasks.testClasses)
    classpath = sourceSets["test"].runtimeClasspath
    mainClass = "dev.moray.app.MockUiPreview"
    jvmArgs("-Djava.awt.headless=true", "--enable-native-access=ALL-UNNAMED")
    args(rootProject.layout.projectDirectory.dir("docs/design").asFile.absolutePath)
}

apply(from = rootProject.file("gradle/packaging.gradle"))
