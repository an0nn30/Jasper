import java.util.jar.JarFile

plugins { `java-library` }

dependencies {
    implementation("org.tomlj:tomlj:1.1.1")
    implementation("com.formdev:flatlaf:3.7")
    implementation("com.formdev:flatlaf-extras:3.7")
    implementation("com.github.weisj:jsvg:2.0.0")
}

// Exercise the same logical geometry in a separately scaled Swing process.
tasks.withType<Test>().configureEach {
    System.getProperty("flatlaf.uiScale")?.let { systemProperty("flatlaf.uiScale", it) }
}

// Test code and gallery assets never enter the production jar.
tasks.test {
    dependsOn(tasks.jar)
    systemProperty("jasper.uiJar", tasks.jar.get().archiveFile.get().asFile.absolutePath)
    systemProperty("jasper.repoRoot", rootProject.projectDir.absolutePath)
    inputs.files(rootProject.fileTree("docs") { include("**/*.md") }, file("README.md"))
        .withPathSensitivity(PathSensitivity.RELATIVE)
}
tasks.register<JavaExec>("gallery") {
    dependsOn(tasks.testClasses)
    classpath = sourceSets.test.get().runtimeClasspath
    mainClass.set("dev.jasper.ui.gallery.KitGallery")
    javaLauncher.set(javaToolchains.launcherFor {
        languageVersion.set(JavaLanguageVersion.of(25))
        vendor.set(JvmVendorSpec.JETBRAINS)
    })
    val scale = providers.gradleProperty("jasper.galleryScale").orElse("1")
    val theme = providers.gradleProperty("jasper.galleryTheme").orElse("dark")
    doFirst {
        require(scale.get() in listOf("1", "2")) { "jasper.galleryScale must be 1 or 2" }
        require(theme.get() in listOf("dark", "light", "custom", "calibration")) { "Unknown jasper.galleryTheme" }
    }
    systemProperty("java.awt.headless", "true")
    systemProperty("flatlaf.uiScale", scale.get())
    systemProperty("jasper.galleryTheme", theme.get())
    args(layout.buildDirectory.dir("gallery").get().asFile.absolutePath)
}
tasks.withType<Javadoc>().configureEach {
    isFailOnError = true
    (options as StandardJavadocDocletOptions).apply {
        encoding = "UTF-8"
        addBooleanOption("Xdoclint:all", true)
        addBooleanOption("Xdoclint:-missing", true)
    }
}
tasks.named("check") { dependsOn(tasks.named("javadoc")) }

// This portable distribution task never launches the application or trains AOT.
val verifyUiDistribution = tasks.register("verifyUiDistribution") {
    dependsOn(":jasper-app:installDist", tasks.jar)
    doLast {
        val lib = rootProject.file("jasper-app/build/install/jasper-app/lib")
        val uiJar = tasks.jar.get().archiveFile.get().asFile
        require(lib.resolve(uiJar.name).isFile) { "Application distribution is missing ${uiJar.name}" }
        val plugins = rootProject.subprojects.filter { it.name.startsWith("jasper-plugin-") }
        require(plugins.isNotEmpty()) { "No bundled plugin runtimes were checked" }
        for (plugin in plugins) {
            for (artifact in plugin.configurations.getByName("runtimeClasspath").files) {
                require(artifact.name != uiJar.name) { "${plugin.path} exposes the UI engine" }
                if (artifact.extension == "jar") JarFile(artifact).use { jar ->
                    require(jar.entries().asSequence().none { it.name.startsWith("dev/jasper/ui/") }) {
                        "${plugin.path} embeds UI implementation classes in ${artifact.name}"
                    }
                }
            }
        }
        for (artifact in lib.resolve("plugins").walkTopDown().filter { it.isFile && it.extension == "jar" }) {
            JarFile(artifact).use { jar ->
                require(jar.entries().asSequence().none { it.name.startsWith("dev/jasper/ui/") }) {
                    "Staged plugin artifact exposes the UI engine: ${artifact.name}"
                }
            }
        }
        logger.lifecycle("UI distribution verified: app engine jar present, ${plugins.size} plugin runtimes isolated")
    }
}
