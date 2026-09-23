// A plugin compiles against the SDK and the exported API of plugins it requires; the application
// supplies both at run time. Bundled: Apache MINA sshd (with slf4j routed to JUL), BouncyCastle so MINA
// reads every OpenSSH key format the Vault produces, and tomlj for hosts.toml. stagePlugins copies the
// runtime classpath beside the plugin jar, and PluginClassLoader loads it.
plugins { `java-library` }

dependencies {
    compileOnly(project(":jasper-sdk"))
    compileOnly(project(":jasper-plugin-vault"))
    implementation("org.apache.sshd:sshd-core:2.19.0")
    implementation("org.apache.sshd:sshd-sftp:2.19.0")
    implementation("org.slf4j:slf4j-jdk14:2.0.13")
    implementation("org.bouncycastle:bcprov-jdk18on:1.85.2")
    implementation("org.tomlj:tomlj:1.1.1")
    testImplementation(project(":jasper-sdk"))
    testImplementation(project(":jasper-sdk-testkit"))
    testImplementation(project(":jasper-plugin-vault"))
}
