// A plugin compiles against the SDK only; the application supplies it at run time. RSyntaxTextArea is bundled:
// stagePlugins copies the runtime classpath beside the plugin jar, and PluginClassLoader loads it.
plugins { `java-library` }

dependencies {
    compileOnly(project(":jasper-sdk"))
    implementation("com.fifesoft:rsyntaxtextarea:3.6.0")
    testImplementation(project(":jasper-sdk"))
    testImplementation(project(":jasper-sdk-testkit"))
}
