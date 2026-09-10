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
