plugins {
    application
}

dependencies {
    implementation(project(":moray-terminal"))
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
