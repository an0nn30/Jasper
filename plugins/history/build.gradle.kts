// A plugin compiles against the SDK only, plus the exported API of plugins it requires.
plugins { `java-library` }

dependencies {
    compileOnly(project(":jasper-sdk"))
    compileOnly(project(":jasper-plugin-snippets"))
    testImplementation(project(":jasper-sdk"))
    testImplementation(project(":jasper-sdk-testkit"))
    testImplementation(project(":jasper-plugin-snippets"))
}
