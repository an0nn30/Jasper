#!/usr/bin/env python3
"""Exercise the real Gradle policy with controlled SDK/tool discovery, not a desktop SDK."""
import json
import os
import pathlib
import shlex
import shutil
import subprocess
import sys
import tempfile
import unittest
import venv

ROOT = pathlib.Path(__file__).resolve().parents[3]
NAMES = ("gtk3", "gtk4", "qt5", "qt6", "kde5", "kde6")


class BuildPolicyTest(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory(prefix="jasper-desktop-policy-")
        self.addCleanup(self.temp.cleanup)
        self.root = pathlib.Path(self.temp.name)
        (self.root / "packaging").symlink_to(ROOT / "packaging", target_is_directory=True)
        (self.root / "settings.gradle").write_text("rootProject.name = 'desktop-policy'\n")
        (self.root / "build.gradle").write_text("""
plugins { id 'application' }
tasks.register('stagePackage', Sync) { into(layout.buildDirectory.dir('package')) }
tasks.register('verifyPackage')
apply from: System.getenv('JASPER_DESKTOP_SCRIPT')
""")
        self.bin = self.root / "bin"
        self.bin.mkdir()
        self.tool("cc", """
import sys, pathlib, shutil
if os.getenv('POLICY_COMPILE_FAIL'):
    print('intentional compiler error', file=sys.stderr); sys.exit(2)
shutil.copy('/bin/true', sys.argv[sys.argv.index('-o') + 1])
""")
        self.tool("pkg-config", """
import sys
if os.getenv('POLICY_NO_GTK') and any(x.startswith(('gtk+-3.0', 'gtk4', 'libadwaita-1')) for x in sys.argv):
    sys.exit(1)
""")
        self.tool("cmake", """
import sys, pathlib, shutil
args = sys.argv
if '--version' in args:
    print('cmake version ' + os.getenv('POLICY_CMAKE_VERSION', '3.21.0')); sys.exit(0)
if '--build' in args:
    if os.getenv('POLICY_COMPILE_FAIL'):
        print('intentional compiler error', file=sys.stderr); sys.exit(2)
    build = pathlib.Path(args[args.index('--build') + 1])
    target = build.name
    production = build / 'production'; production.mkdir(exist_ok=True)
    shutil.copy('/bin/true', production / ('jasper-desktop-' + target))
else:
    if os.getenv('POLICY_CONFIGURE_FAIL'):
        print('intentional CMake configuration error', file=sys.stderr); sys.exit(2)
    build = pathlib.Path(args[args.index('-B') + 1]); build.mkdir(parents=True, exist_ok=True)
    missing = build.name in os.getenv('POLICY_MISSING', '').split(',')
    (build / 'desktop-sdk-status.txt').write_text('missing: controlled absent SDK' if missing else 'available')
""")
        self.env = dict(os.environ, PATH=str(self.bin) + os.pathsep + os.environ["PATH"],
                        JASPER_DESKTOP_SCRIPT=str(ROOT / "gradle/linux-desktop-colors.gradle"))

    def tool(self, name, code):
        path = self.bin / name
        # A shell trampoline preserves interpreter paths that contain spaces.
        path.write_text("#!/bin/sh\n'''exec' " + shlex.quote(sys.executable)
                        + " \"$0\" \"$@\"\n' '''\nimport os\n" + code)
        path.chmod(0o755)

    def gradle(self, *args, success=True):
        result = subprocess.run([str(ROOT / "gradlew"), "-p", str(self.root), "--console=plain",
                                 "--max-workers=1", "--no-daemon", *args], env=self.env, text=True,
                                stdout=subprocess.PIPE, stderr=subprocess.STDOUT, timeout=120)
        self.assertEqual(result.returncode == 0, success, result.stdout)
        return result.stdout

    def selected(self):
        return {p.name for p in (self.root / "build/desktop-helpers").iterdir()}

    def test_auto_skips_only_missing_sdks_and_cleans_stale_stage(self):
        self.gradle("stageDesktopHelpers")
        self.assertEqual(len(self.selected()), 7)
        self.env["POLICY_MISSING"] = "qt5,kde5"
        self.gradle("stageDesktopHelpers")
        self.assertEqual(self.selected(), {"jasper-desktop-" + n for n in ("portal", "gtk3", "gtk4", "qt6", "kde6")})
        self.assertFalse((self.root / "build/desktop-colors/cmake/qt5/production/jasper-desktop-qt5").exists())

    def test_all_off_keeps_portal_and_removes_stale_helpers(self):
        self.gradle("stageDesktopHelpers")
        self.gradle("stageDesktopHelpers", *("-Pjasper.desktop." + n + "=off" for n in NAMES))
        self.assertEqual(self.selected(), {"jasper-desktop-portal"})
        manifest = (self.root / "build/desktop-colors/selected-helpers.txt").read_text().splitlines()
        self.assertEqual(manifest, ["jasper-desktop-portal"])
        self.gradle("stageDesktopHelpers")
        self.assertEqual(self.selected(), {"jasper-desktop-" + n for n in ("portal", *NAMES)})

    def test_required_and_strict_fail_on_missing_sdk(self):
        self.env["POLICY_MISSING"] = "qt5"
        for prop in ("-Pjasper.desktop.qt5=required", "-Pjasper.desktop.strict=true"):
            with self.subTest(prop=prop):
                self.assertIn("qt5", self.gradle("stageDesktopHelpers", prop, success=False))

    def test_invalid_modes_and_strict_conflict_fail(self):
        for prop in ("-Pjasper.desktop.qt5=typo", "-Pjasper.desktop.strict=typo"):
            with self.subTest(prop=prop):
                self.assertIn("jasper.desktop", self.gradle("stageDesktopHelpers", prop, success=False))
        self.gradle("stageDesktopHelpers", "-Pjasper.desktop.strict=true", "-Pjasper.desktop.qt5=off", success=False)

    def test_real_compile_errors_are_not_optional_dependency_absence(self):
        self.env["POLICY_COMPILE_FAIL"] = "1"
        self.assertIn("intentional compiler error", self.gradle("compileDesktopQtHelpers", success=False))

    def test_old_cmake_is_an_optional_missing_prerequisite(self):
        self.env["POLICY_CMAKE_VERSION"] = "3.20.0"
        self.gradle("stageDesktopHelpers")
        self.assertEqual(self.selected(), {"jasper-desktop-" + n for n in ("portal", "gtk3", "gtk4")})
        self.assertIn("CMake 3.21+", self.gradle("stageDesktopHelpers", "-Pjasper.desktop.qt5=required", success=False))

    def test_configuration_errors_and_invalid_prefixes_fail(self):
        self.env["POLICY_CONFIGURE_FAIL"] = "1"
        self.assertIn("intentional CMake configuration error", self.gradle("compileDesktopQtHelpers", success=False))
        del self.env["POLICY_CONFIGURE_FAIL"]
        self.assertIn("existing absolute SDK prefix", self.gradle("compileDesktopQtHelpers", "-Pjasper.qt5Prefix=missing", success=False))

    def test_missing_gtk_headers_do_not_disable_production_probes(self):
        self.env["POLICY_NO_GTK"] = "1"
        self.gradle("stageDesktopHelpers", "verifyDesktopGtkAbi")
        self.assertIn("jasper-desktop-gtk3", self.selected())
        self.assertIn("jasper-desktop-gtk4", self.selected())
        self.gradle("verifyDesktopGtkAbi", "-Pjasper.desktop.gtk3=required", success=False)


class GeneratedToolsTest(unittest.TestCase):
    def test_uses_selected_interpreter_with_spaces_without_path_python3(self):
        with tempfile.TemporaryDirectory(prefix="jasper policy tools ") as temp:
            root = pathlib.Path(temp).resolve()
            selected = root / "selected python"
            venv.EnvBuilder(with_pip=False).create(selected)
            interpreter = selected / "bin/python"
            tools = root / "tools"
            tools.mkdir()
            result = subprocess.run([str(interpreter), "-c", """
import os, pathlib, runpy, sys
fixture = runpy.run_path(sys.argv[1])['BuildPolicyTest']()
fixture.bin = pathlib.Path(sys.argv[2])
fixture.tool('probe', 'import json, sys; print(json.dumps([sys.executable, sys.argv[1:]]))')
os.execv(str(fixture.bin / 'probe'), ['probe', 'argument with spaces', "quote'argument"])
""", str(pathlib.Path(__file__).resolve()), str(tools)],
                                    env=dict(os.environ, PATH=str(tools)), text=True,
                                    stdout=subprocess.PIPE, stderr=subprocess.PIPE, timeout=10)
            self.assertEqual(result.returncode, 0, result.stderr)
            self.assertEqual(json.loads(result.stdout),
                             [str(interpreter), ['argument with spaces', "quote'argument"]])


if __name__ == "__main__":
    unittest.main()
