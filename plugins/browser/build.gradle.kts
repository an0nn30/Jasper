// A plugin compiles against the SDK and the exported API of plugins it requires (the Credential Vault's, optionally, for
// saved logins); the application supplies them at run time. Chromium is the runtime's own `jcef`
// module (JetBrains Runtime): plugins compile on the class path against the toolchain's system modules, which include it,
// so the Browser bundles no Chromium library. Never set options.release here: the JBR's ct.sym has no `jcef`.
// Bundled: sqlite-jdbc for the history and bookmarks database, the same coordinates Remote bundles.
plugins { `java-library` }

dependencies {
    compileOnly(project(":jasper-sdk"))
    compileOnly(project(":jasper-plugin-vault"))
    implementation("org.xerial:sqlite-jdbc:3.53.4.0")
    testImplementation(project(":jasper-sdk"))
    testImplementation(project(":jasper-sdk-testkit"))
    testImplementation(project(":jasper-plugin-vault"))
}
