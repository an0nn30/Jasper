// A plugin compiles against the SDK and the exported API of plugins it requires (the Editor, optional);
// the application supplies both at run time. Bundled: tomlj for data/projects.toml.
// stagePlugins copies the runtime classpath beside the plugin jar, and PluginClassLoader loads it.
plugins { `java-library` }

dependencies {
    compileOnly(project(":jasper-sdk"))
    compileOnly(project(":jasper-plugin-editor"))
    implementation("org.tomlj:tomlj:1.1.1")
    testImplementation(project(":jasper-sdk"))
    testImplementation(project(":jasper-sdk-testkit"))
    testImplementation(project(":jasper-plugin-editor"))
}
