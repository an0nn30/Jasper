// A plugin compiles against the SDK only; the application supplies it at run time. RSyntaxTextArea and tomlj
// (for recent-files.toml) are bundled: stagePlugins copies the runtime classpath beside the plugin jar, and
// PluginClassLoader loads it.
plugins { `java-library` }

dependencies {
    compileOnly(project(":jasper-sdk"))
    implementation("com.fifesoft:rsyntaxtextarea:3.6.0")
    implementation("org.tomlj:tomlj:1.1.1")
    testImplementation(project(":jasper-sdk"))
    testImplementation(project(":jasper-sdk-testkit"))
    // Headless baseline evidence only; never shipped with the plugin.
    testImplementation(testFixtures(project(":jasper-sdk-testkit")))
    testRuntimeOnly("com.formdev:flatlaf:3.7")
}

tasks.test {
    // VimDocsTest reads, and with -PupdateDocs=true rewrites, files in the repository's docs folder.
    systemProperty("jasper.repoRoot", rootDir.absolutePath)
    systemProperty("jasper.updateDocs", providers.gradleProperty("updateDocs").getOrElse("false"))
    inputs.files(rootDir.resolve("docs/vim-divergences.md"), rootDir.resolve("docs/editor-vim-reference.md")).optional()
}

// Records the Vim corpus's expected results by running real Vim (Vim spec 11.1). Needs vim on PATH, or
// -PvimBinary=/path/to/vim; -Pcase=<text> records only the cases whose names contain the text. Not part of check.
tasks.register<JavaExec>("recordVimCorpus") {
    group = "verification"
    description = "Records the expected results of the Vim corpus by running real Vim."
    classpath = sourceSets["test"].runtimeClasspath
    mainClass = "dev.jasper.editor.vim.RecordCorpus"
    args(layout.projectDirectory.dir("src/test/resources/vim/corpus").asFile.absolutePath,
        providers.gradleProperty("vimBinary").getOrElse("vim"), providers.gradleProperty("case").getOrElse(""))
}
