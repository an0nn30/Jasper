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
