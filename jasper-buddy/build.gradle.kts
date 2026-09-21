// The companion production library depends only on the JDK.
plugins { `java-library`; `java-test-fixtures` }

for ((taskName, entryPoint) in listOf(
    "buddyNotificationPreview" to "BuddyNotificationPreview",
    "buddyMotionPreview" to "BuddyMotionPreview",
    "buddyPerformanceMeasurement" to "BuddyPerformanceMeasurement"
)) {
    tasks.register<JavaExec>(taskName) {
        group = "verification"
        dependsOn(tasks.testClasses)
        classpath = sourceSets["test"].runtimeClasspath
        mainClass = "dev.jasper.buddy.internal.presentation.$entryPoint"
        jvmArgs("-Djava.awt.headless=true")
    }
}

tasks.test {
    dependsOn(tasks.jar)
    systemProperty("jasper.buddyJar", tasks.jar.get().archiveFile.get().asFile.absolutePath)
}

// Keep API/package contracts in the ordinary headless verification path.
tasks.withType<Javadoc>().configureEach {
    isFailOnError = true
    (options as StandardJavadocDocletOptions).apply {
        encoding = "UTF-8"
        addBooleanOption("Xdoclint:all", true)
    }
}
tasks.named("check") { dependsOn(tasks.named("javadoc")) }
tasks.javadoc {
    dependsOn(tasks.classes)
    exclude("dev/jasper/buddy/internal/**")
    classpath += sourceSets.main.get().output
}
