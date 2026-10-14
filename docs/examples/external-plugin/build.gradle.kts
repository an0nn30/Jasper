// An external Jasper plugin built from the jars a Jasper GitHub release carries. Put jasper-sdk-<v>.jar and
// jasper-sdk-testkit-<v>.jar in libs/, or pass -PjasperSdkDir=<folder>; -PjasperSdkVersion=<v> picks another release.
// The SDK is compileOnly: Jasper supplies it at run time. Libraries your plugin bundles go in implementation.
plugins { `java-library` }

val jasperSdkDir = file(providers.gradleProperty("jasperSdkDir").getOrElse("libs"))
val jasperSdkVersion = providers.gradleProperty("jasperSdkVersion").getOrElse("0.17.1")
fun jasperJar(name: String) = files(jasperSdkDir.resolve("$name-$jasperSdkVersion.jar"))

// Name the missing jar instead of failing later with "package dev.jasper.sdk... does not exist".
tasks.withType<JavaCompile>().configureEach {
    doFirst {
        for (name in listOf("jasper-sdk", "jasper-sdk-testkit")) {
            val jar = jasperSdkDir.resolve("$name-$jasperSdkVersion.jar")
            check(jar.isFile) {
                "Missing $jar: put the Jasper release's SDK jars in $jasperSdkDir, " +
                    "or pass -PjasperSdkDir=<folder> and -PjasperSdkVersion=<version>"
            }
        }
    }
}

/** The id and version come from plugin.toml, so the build keeps no second copy of either. */
val descriptor = file("src/main/resources/plugin.toml").readText()
fun field(name: String) = Regex("^$name\\s*=\\s*\"([^\"]+)\"", RegexOption.MULTILINE).find(descriptor)?.groupValues?.get(1)
    ?: error("plugin.toml has no $name")
val pluginId = field("id")
version = field("version")

repositories { mavenCentral() }

// Jasper runs plugins on Java 25; any Java 25 compiles them (Jasper itself needs the JetBrains Runtime only to run).
java { toolchain { languageVersion = JavaLanguageVersion.of(25) } }

dependencies {
    compileOnly(jasperJar("jasper-sdk"))
    testImplementation(jasperJar("jasper-sdk"))
    testImplementation(jasperJar("jasper-sdk-testkit"))
    testImplementation(platform("org.junit:junit-bom:6.1.3"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    testImplementation("org.assertj:assertj-core:3.27.7")
}

tasks.jar { archiveBaseName.set(pluginId) }

tasks.test {
    useJUnitPlatform()
    systemProperty("java.awt.headless", "true")
}

// What Jasper installs: the plugin jar beside the libraries it bundles (its runtime classpath).
//   stagePlugin -> build/plugin/<id>/                     (jasper --plugin-dir build/plugin/<id>)
//   pluginZip   -> build/distributions/<id>-<version>.zip (File > Manage Plugins... > Install from Zip...)
val pluginContents = files(tasks.jar, configurations.runtimeClasspath)

tasks.register<Sync>("stagePlugin") {
    description = "Stages $pluginId as a plugin directory for jasper --plugin-dir."
    group = "distribution"
    from(pluginContents)
    into(layout.buildDirectory.dir("plugin/$pluginId"))
}

tasks.register<Zip>("pluginZip") {
    description = "Zips $pluginId for File > Manage Plugins... > Install from Zip..."
    group = "distribution"
    from(pluginContents)
    archiveFileName.set("$pluginId-$version.zip")
    destinationDirectory.set(layout.buildDirectory.dir("distributions"))
}
