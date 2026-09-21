// A plugin compiles against the SDK only; the application supplies it at run time.
plugins { `java-library` }

dependencies {
    compileOnly(project(":jasper-sdk"))
    testImplementation(project(":jasper-sdk"))
    testImplementation(project(":jasper-sdk-testkit"))
}
