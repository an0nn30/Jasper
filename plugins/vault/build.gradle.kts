// A plugin compiles against the SDK only; the application supplies it at run time. BouncyCastle is
// bundled for Argon2id and OpenSSH key encoding: stagePlugins copies the runtime classpath beside
// the plugin jar, and PluginClassLoader loads it. Only the lightweight API is used; no JCA provider.
plugins { `java-library` }

dependencies {
    compileOnly(project(":jasper-sdk"))
    implementation("org.apache.sshd:sshd-common:2.19.0")
    implementation("org.slf4j:slf4j-jdk14:2.0.13")
    implementation("org.bouncycastle:bcprov-jdk18on:1.85.2")
    testImplementation(project(":jasper-sdk"))
    testImplementation(project(":jasper-sdk-testkit"))
}
