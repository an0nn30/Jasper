// A plugin compiles against the SDK and the exported API of plugins it requires (Enterprise, optional);
// the application supplies both at run time. BouncyCastle is
// bundled for Argon2id and OpenSSH key encoding: stagePlugins copies the runtime classpath beside
// the plugin jar, and PluginClassLoader loads it. Only the lightweight API is used; no JCA provider.
plugins { `java-library` }

dependencies {
    compileOnly(project(":jasper-sdk"))
    compileOnly(project(":jasper-plugin-enterprise"))
    implementation("org.apache.sshd:sshd-common:2.19.0")
    implementation("org.slf4j:slf4j-jdk14:2.0.13")
    implementation("org.bouncycastle:bcprov-jdk18on:1.85.2")
    testImplementation(project(":jasper-sdk"))
    testImplementation(project(":jasper-sdk-testkit"))
    testImplementation(project(":jasper-plugin-enterprise"))
    // Headless baseline evidence only; never shipped with the plugin.
    testImplementation(testFixtures(project(":jasper-sdk-testkit")))
    testRuntimeOnly("com.formdev:flatlaf:3.7")
}
