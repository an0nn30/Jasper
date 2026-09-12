plugins {
    application
}

dependencies {
    implementation(project(":moray-terminal"))
    implementation("org.tomlj:tomlj:1.1.1")
    implementation("com.formdev:flatlaf:3.7")
    implementation("org.jetbrains.runtime:jbr-api:1.9.0")
    implementation("com.formdev:flatlaf-extras:3.7")
    runtimeOnly("org.slf4j:slf4j-nop:2.0.13")
}

application {
    mainClass = "dev.moray.app.Main"
    applicationDefaultJvmArgs = listOf(
        "--enable-native-access=ALL-UNNAMED",
        "-Dapple.awt.application.name=Moray",
    )
}

tasks.register<JavaExec>("bench") {
    group = "verification"
    description = "Pipes ~100 MB of ANSI-colored text through a Moray terminal window and prints MB/s."
    classpath = sourceSets["main"].runtimeClasspath
    mainClass = "dev.moray.app.Bench"
    jvmArgs("--enable-native-access=ALL-UNNAMED")
    args(layout.buildDirectory.file("bench/ansi-100mb.txt").get().asFile.absolutePath)
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
