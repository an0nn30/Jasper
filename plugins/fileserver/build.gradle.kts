// A plugin compiles against the SDK and the exported API of plugins it requires (the Credential Vault, optional);
// the application supplies both at run time. Bundled: tomlj for servers.toml; Apache FtpServer (with MINA, and slf4j routed to JUL) for FTP; BouncyCastle for the TLS certificate; Apache MINA sshd for SFTP.
// HTTP uses the JDK's jdk.httpserver module. stagePlugins copies the runtime classpath beside the plugin jar, and PluginClassLoader loads it.
plugins { `java-library` }

dependencies {
    compileOnly(project(":jasper-sdk"))
    compileOnly(project(":jasper-plugin-vault"))
    implementation("org.tomlj:tomlj:1.1.1")
    implementation("org.apache.ftpserver:ftpserver-core:1.2.1")
    implementation("org.apache.sshd:sshd-core:2.19.0")
    implementation("org.apache.sshd:sshd-sftp:2.19.0")
    implementation("org.slf4j:slf4j-jdk14:2.0.13")
    implementation("org.bouncycastle:bcprov-jdk18on:1.85.2")
    implementation("org.bouncycastle:bcpkix-jdk18on:1.85")
    testImplementation(project(":jasper-sdk"))
    testImplementation(project(":jasper-sdk-testkit"))
    testImplementation(project(":jasper-plugin-vault"))
    testImplementation("commons-net:commons-net:3.13.0")
}
