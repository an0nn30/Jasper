plugins {
    `java-library`
}

dependencies {
    implementation("org.jetbrains.jediterm:jediterm-core:3.76")
    implementation("org.jetbrains.pty4j:pty4j:0.13.10")
    testRuntimeOnly("org.slf4j:slf4j-nop:2.0.13")
}
