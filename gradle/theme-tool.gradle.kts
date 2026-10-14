// The theme converter's tests (tools/themes: Python 3.11+, standard library only), run by :jasper-ui:check.
// -Pjasper.python=<interpreter> chooses the interpreter; otherwise python3, then python, from the PATH.
val themeToolUi = project(":jasper-ui")
val themeToolPassed = layout.buildDirectory.file("theme-tool/passed")
val checkThemeTool = tasks.register("checkThemeTool") {
    group = "verification"
    description = "Runs the theme converter's tests (tools/themes) with Python 3.11 or later."
    inputs.files(fileTree("tools/themes") { exclude("**/__pycache__/**") }).withPathSensitivity(PathSensitivity.RELATIVE)
    inputs.file("jasper-ui/src/main/resources/dev/jasper/ui/theme/slate-steel.json").withPathSensitivity(PathSensitivity.RELATIVE)
    val chosen = providers.gradleProperty("jasper.python")
    inputs.property("python", chosen.orElse(""))
    outputs.file(themeToolPassed)
    val root = layout.projectDirectory.asFile
    doLast {
        fun usable(python: String) = runCatching {
            val probe = ProcessBuilder(python, "-c", "import sys; sys.exit(0 if sys.version_info >= (3, 11) else 1)")
                .redirectErrorStream(true).start()
            probe.inputStream.readAllBytes()
            probe.waitFor() == 0
        }.getOrDefault(false)
        val python = (chosen.orNull?.let { listOf(it) } ?: listOf("python3", "python")).firstOrNull { usable(it) }
            ?: throw GradleException("The theme tool's tests need Python 3.11 or later on the PATH")
        val run = ProcessBuilder(python, "-B", "-m", "unittest", "discover", "-s", "tools/themes", "-p", "test_*.py")
            .directory(root).redirectErrorStream(true).start()
        val output = run.inputStream.bufferedReader().readText()
        if (run.waitFor() != 0) throw GradleException("The theme tool's tests failed:\n$output")
        logger.lifecycle(output.trim().lines().takeLast(3).joinToString("\n"))
        themeToolPassed.get().asFile.apply { parentFile.mkdirs(); writeText("ok\n") }
    }
}
themeToolUi.tasks.named("check") { dependsOn(checkThemeTool) }
