// A plugin compiles against the SDK only; the application supplies it at run time.
plugins { `java-library` }

dependencies {
    compileOnly(project(":jasper-sdk"))
    testImplementation(project(":jasper-sdk"))
    testImplementation(project(":jasper-sdk-testkit"))
    // Headless baseline evidence only; never shipped with the plugin.
    testImplementation(testFixtures(project(":jasper-sdk-testkit")))
    testRuntimeOnly("com.formdev:flatlaf:3.7")
}
