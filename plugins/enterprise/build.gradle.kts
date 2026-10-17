// The Enterprise look: its kit file, fonts and theme are data the host reads; its code only publishes Workstation.
// It compiles against the SDK only and bundles nothing.
plugins { `java-library` }

dependencies {
    compileOnly(project(":jasper-sdk"))
    testImplementation(project(":jasper-sdk"))
    testImplementation(project(":jasper-sdk-testkit"))
}
