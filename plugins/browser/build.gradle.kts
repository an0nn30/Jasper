// A plugin compiles against the SDK only; the application supplies it at run time. Chromium is the runtime's own `jcef`
// module (JetBrains Runtime): plugins compile on the class path against the toolchain's system modules, which include it,
// so the Browser bundles no library. Never set options.release here: the JBR's ct.sym has no `jcef`.
plugins { `java-library` }

dependencies {
    compileOnly(project(":jasper-sdk"))
    testImplementation(project(":jasper-sdk"))
    testImplementation(project(":jasper-sdk-testkit"))
}
