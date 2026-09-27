#!/usr/bin/env python3
"""Render a headless old/new contact sheet; no native windows or shell sessions.

Run from any directory. Requires Git, Python 3 and the project's JBR Gradle toolchain.
Output: jasper-app/build/icon-preview/comparison.png.
"""
from pathlib import Path
import re
import subprocess
import tempfile

ROOT = Path(__file__).resolve().parents[2]
BASE = "90c2e574"
OUT = ROOT / "jasper-app/build/icon-preview"
RESOURCES = "jasper-app/src/main/resources/"


def git(*args):
    return subprocess.check_output(["git", *args], cwd=ROOT)


def main():
    baseline = OUT / "baseline"
    baseline.mkdir(parents=True, exist_ok=True)
    paths = git("ls-tree", "-r", "--name-only", BASE, "--",
                RESOURCES + "dev/jasper/app/icons/intellij", RESOURCES + "dev/jasper/app/icons/standard").decode().splitlines()
    for path in paths:
        if path.endswith(".svg"):
            target = baseline / path.removeprefix(RESOURCES)
            target.parent.mkdir(parents=True, exist_ok=True)
            target.write_bytes(git("show", BASE + ":" + path))
    source = git("show", BASE + ":jasper-app/src/main/java/dev/jasper/app/platform/NamedIcons.java").decode()
    rows = [name + "=dev/jasper/app/icons/" + family.lower() + "/" + file
            for name, family, file in re.findall(r'Map.entry\("([A-Z_]+)", (INTELLIJ|STANDARD) \+ "([^"]+)"\)', source)]
    (baseline / "icons.properties").write_text("\n".join(rows) + "\n")
    with tempfile.TemporaryDirectory(prefix="jasper-icon-preview-") as temp:
        init = Path(temp) / "preview.gradle"
        init.write_text('''gradle.projectsEvaluated {
    def app = rootProject.project(':jasper-app')
    app.tasks.register('iconPreviewClasspath') {
        dependsOn app.tasks.named('classes')
        doLast {
            def out = app.layout.buildDirectory.dir('icon-preview').get().asFile
            out.mkdirs()
            new File(out, 'classpath.txt').text = app.sourceSets.main.runtimeClasspath.asPath
            new File(out, 'java.txt').text = app.javaToolchains.launcherFor(app.java.toolchain).get().executablePath.asFile.absolutePath
        }
    }
}
''')
        subprocess.run([str(ROOT / "gradlew"), "-I", str(init), ":jasper-app:iconPreviewClasspath"], cwd=ROOT, check=True)
    subprocess.run([(OUT / "java.txt").read_text(), "-Djava.awt.headless=true", "--class-path",
                    (OUT / "classpath.txt").read_text(), str(ROOT / "tools/icons/IconPreview.java"),
                    str(baseline), str(OUT / "comparison.png")], cwd=ROOT, check=True)


if __name__ == "__main__":
    main()
