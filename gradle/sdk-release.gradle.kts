import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.api.tasks.Sync
import org.gradle.api.tasks.bundling.Jar

// The jars a Jasper GitHub release carries so a plugin built outside this repository can link the SDK as files:
//   sdkRelease       -> build/sdk-release/ (jasper-sdk-<v>.jar, -sources, -javadoc; jasper-sdk-testkit-<v>.jar, -sources)
//   verifySdkRelease -> checks those five files (part of :jasper-sdk:check)
// <v> is JasperSdk.VERSION; the build keeps no second copy of it.
val sdkVersion: String = Regex("VERSION = \"([^\"]+)\"")
    .find(file("jasper-sdk/src/main/java/dev/jasper/sdk/JasperSdk.java").readText())?.groupValues?.get(1)
    ?: error("jasper-sdk: JasperSdk.VERSION not found")

val sdk = project(":jasper-sdk")
val testkit = project(":jasper-sdk-testkit")

for ((module, title) in listOf(sdk to "jasper-sdk", testkit to "jasper-sdk-testkit")) {
    module.extensions.configure<JavaPluginExtension> { withSourcesJar() }
    module.tasks.named<Jar>("jar") {
        manifest { attributes("Implementation-Title" to title, "Implementation-Version" to sdkVersion) }
    }
}
// The javadoc jar is built from the same doclint-checked javadoc task :jasper-sdk:check already runs.
sdk.extensions.configure<JavaPluginExtension> { withJavadocJar() }

val releaseNames = listOf("jasper-sdk-$sdkVersion.jar", "jasper-sdk-$sdkVersion-sources.jar",
    "jasper-sdk-$sdkVersion-javadoc.jar", "jasper-sdk-testkit-$sdkVersion.jar", "jasper-sdk-testkit-$sdkVersion-sources.jar")
val releaseDirectory = layout.buildDirectory.dir("sdk-release")

val sdkRelease = tasks.register<Sync>("sdkRelease") {
    description = "Builds the SDK and testkit jars a GitHub release carries for external plugins."
    group = "distribution"
    into(releaseDirectory)
    from(sdk.tasks.named("jar")) { rename { releaseNames[0] } }
    from(sdk.tasks.named("sourcesJar")) { rename { releaseNames[1] } }
    from(sdk.tasks.named("javadocJar")) { rename { releaseNames[2] } }
    from(testkit.tasks.named("jar")) { rename { releaseNames[3] } }
    from(testkit.tasks.named("sourcesJar")) { rename { releaseNames[4] } }
}

val verifySdkRelease = tasks.register("verifySdkRelease") {
    description = "Checks the release jars: names, contents and manifest versions."
    group = "verification"
    dependsOn(sdkRelease)
    doLast {
        val folder = releaseDirectory.get().asFile
        val present = folder.listFiles().orEmpty().map { it.name }.sorted()
        check(present == releaseNames.sorted()) { "build/sdk-release holds $present, expected ${releaseNames.sorted()}" }
        fun entries(name: String): List<String> =
            java.util.zip.ZipFile(folder.resolve(name)).use { zip -> zip.entries().asSequence().map { it.name }.toList() }
        fun manifestVersion(name: String): String? =
            java.util.jar.JarFile(folder.resolve(name)).use { it.manifest?.mainAttributes?.getValue("Implementation-Version") }

        val sdkClasses = entries(releaseNames[0]).filter { it.endsWith(".class") }
        check("dev/jasper/sdk/JasperSdk.class" in sdkClasses) { "${releaseNames[0]} lacks dev/jasper/sdk/JasperSdk.class" }
        val foreign = sdkClasses.filter { !it.startsWith("dev/jasper/sdk/") || it.startsWith("dev/jasper/sdk/testing/") }
        check(foreign.isEmpty()) { "${releaseNames[0]} carries classes outside the SDK: ${foreign.take(10)}" }

        val kitEntries = entries(releaseNames[3])
        check("dev/jasper/sdk/testing/FakePluginHost.class" in kitEntries) { "${releaseNames[3]} lacks FakePluginHost" }
        val fixtures = kitEntries.filter { it.startsWith("dev/jasper/sdk/testing/contract/") }
        check(fixtures.isEmpty()) { "${releaseNames[3]} carries the contract fixtures: ${fixtures.take(10)}" }

        for (jar in listOf(releaseNames[0], releaseNames[3]))
            check(manifestVersion(jar) == sdkVersion) { "$jar has Implementation-Version ${manifestVersion(jar)}, expected $sdkVersion" }
    }
}

sdk.tasks.named("check") { dependsOn(verifySdkRelease) }
