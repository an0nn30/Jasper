rootProject.name = "jasper"
include("jasper-terminal", "jasper-app", "jasper-buddy", "jasper-sdk", "jasper-sdk-testkit")
// Every plugins/<name>/ with a build script is the module :jasper-plugin-<name>; nothing else to register.
file("plugins").listFiles { child -> child.isDirectory && File(child, "build.gradle.kts").isFile }
    ?.sortedBy { it.name }?.forEach { directory ->
        include("jasper-plugin-${directory.name}")
        project(":jasper-plugin-${directory.name}").projectDir = directory
    }
