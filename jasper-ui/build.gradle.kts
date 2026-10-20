import java.util.jar.JarFile

plugins { `java-library`; `java-test-fixtures` }

dependencies {
    implementation("com.formdev:flatlaf:3.7")
    implementation("com.formdev:flatlaf-extras:3.7")
    implementation("com.github.weisj:jsvg:2.0.0")
    // The gallery and the kit fixtures, shared with jasper-app's tests (spec 16.1.3); never in the production jar.
    testFixturesImplementation("com.formdev:flatlaf:3.7")
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
    inputs.dir(rootProject.file("tools/themes/testdata/expected")).withPathSensitivity(PathSensitivity.RELATIVE)
    inputs.dir(rootProject.file("tools/themes/testdata/intellij/loose")).withPathSensitivity(PathSensitivity.RELATIVE)
    // EncodedArtworkRasterTest checks the Boot Splash art (Enterprise plan 11).
    inputs.file(rootProject.file("docs/design/enterprise/reference/10 Boot Splash/extracted/art.svg")).withPathSensitivity(PathSensitivity.RELATIVE)
    // KitFixtures.themes() includes the Enterprise plugin's theme (spec 16.1.2); TypographyTest registers its IBM Plex Sans.
    inputs.file(rootProject.file("plugins/enterprise/src/main/resources/themes/enterprise.json")).withPathSensitivity(PathSensitivity.RELATIVE)
    inputs.file(rootProject.file("plugins/enterprise/src/main/resources/presentation/fonts/ibm-plex-sans-regular.ttf"))
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
    val kit = providers.gradleProperty("jasper.galleryKit").orElse("default")
    doFirst {
        require(scale.get() in listOf("1", "2")) { "jasper.galleryScale must be 1 or 2" }
        require(theme.get() in listOf("dark", "light")) { "Unknown jasper.galleryTheme" }
        require(kit.get() in listOf("default", "enterprise")) { "jasper.galleryKit must be default or enterprise" }
    }
    systemProperty("java.awt.headless", "true")
    systemProperty("flatlaf.uiScale", scale.get())
    systemProperty("jasper.galleryTheme", theme.get())
    systemProperty("jasper.galleryKit", kit.get())
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
    val plugins = rootProject.subprojects.filter { it.name.startsWith("jasper-plugin-") }
        .associate { it.path to it.tasks.named("stagePlugin") }
    inputs.files(plugins.values)
    doLast {
        val lib = rootProject.file("jasper-app/build/install/jasper-app/lib")
        val uiJar = tasks.jar.get().archiveFile.get().asFile
        require(lib.resolve(uiJar.name).isFile) { "Application distribution is missing ${uiJar.name}" }
        require(plugins.isNotEmpty()) { "No bundled plugin runtimes were checked" }
        for ((plugin, stage) in plugins) {
            for (artifact in stage.get().outputs.files.asFileTree.files) {
                require(artifact.name != uiJar.name) { "${plugin} exposes the UI engine" }
                if (artifact.extension == "jar") JarFile(artifact).use { jar ->
                    require(jar.entries().asSequence().none { it.name.startsWith("dev/jasper/ui/") }) {
                        "${plugin} embeds UI implementation classes in ${artifact.name}"
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
