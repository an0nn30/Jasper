plugins {
    `java-library`
}

dependencies {
    implementation("org.jetbrains.jediterm:jediterm-core:3.76")
    implementation("org.jetbrains.pty4j:pty4j:0.13.10")
    implementation("net.java.dev.jna:jna:5.14.0")
    testRuntimeOnly("org.slf4j:slf4j-nop:2.0.13")
}

// Deliberately outside check: comparisons report timings, never assert wall-clock thresholds.
tasks.register<JavaExec>("refactorMeasurement") {
    dependsOn(tasks.testClasses)
    classpath = sourceSets.test.get().runtimeClasspath
    mainClass.set("dev.jasper.terminal.view.TerminalRefactorMeasurement")
    javaLauncher.set(javaToolchains.launcherFor {
        languageVersion.set(JavaLanguageVersion.of(25))
        vendor.set(JvmVendorSpec.JETBRAINS)
    })
    systemProperty("java.awt.headless", "true")
}

// Keep supported API documentation and examples in the ordinary verification path.
tasks.withType<Javadoc>().configureEach {
    isFailOnError = true
    (options as StandardJavadocDocletOptions).apply {
        encoding = "UTF-8"
        addBooleanOption("Xdoclint:all", true)
    }
}
tasks.named("check") { dependsOn(tasks.named("javadoc")) }
tasks.withType<Test>().configureEach {
    systemProperty("jasper.repoRoot", rootProject.projectDir.absolutePath)
}

// Documentation tests read these files at runtime, so docs-only edits must invalidate test results.
tasks.test {
    inputs.files(rootProject.fileTree("docs") { include("**/*.md") },
        rootProject.file("README.md"), rootProject.file("AGENTS.md"),
        rootProject.file("jasper-app/README.md"), rootProject.file("jasper-terminal/README.md"),
        rootProject.file("jasper-buddy/README.md"), rootProject.fileTree("packaging") { include("**/*.md") })
        .withPathSensitivity(PathSensitivity.RELATIVE)
}
