"""Host-independent checks of the explicit dependency preparation boundary."""
import importlib.util
import json
import pathlib
import subprocess
import tempfile
import unittest
import shutil

ROOT = pathlib.Path(__file__).resolve().parents[1]
SCRIPT = ROOT / "toolchains/build-baseline.sh"
MODULE = ROOT / "toolchains/build_baseline.py"


class BaselineToolchainsTest(unittest.TestCase):
    def run_plan(self, target, root):
        return subprocess.run(["bash", str(SCRIPT), "--plan", target, str(root)],
                              text=True, capture_output=True)

    def test_plan_keeps_generic_qt6_separate_from_kde6(self):
        with tempfile.TemporaryDirectory() as directory:
            root = pathlib.Path(directory)
            generic = self.run_plan("qt6", root)
            kde = self.run_plan("kde6", root)
            self.assertEqual(generic.returncode, 0, generic.stderr)
            self.assertEqual(kde.returncode, 0, kde.stderr)
            qt_steps = json.loads(generic.stdout)
            kde_steps = json.loads(kde.stdout)
            self.assertEqual(qt_steps[0]["version"], "6.2.4")
            self.assertEqual(kde_steps[0]["version"], "6.5.0")
            self.assertEqual(qt_steps[0]["prefix"], str(root / "prefixes/qt6"))
            self.assertEqual(kde_steps[0]["prefix"], str(root / "prefixes/kde6-qt"))
            self.assertNotEqual(qt_steps[0]["build"], kde_steps[0]["build"])
            self.assertEqual(kde_steps[-1]["name"], "kcolorscheme")
            self.assertEqual(list(root.iterdir()), [], "--plan must not download or write")

    def test_kde5_dependency_order_and_target_local_search_paths(self):
        with tempfile.TemporaryDirectory() as directory:
            result = self.run_plan("kde5", pathlib.Path(directory))
            self.assertEqual(result.returncode, 0, result.stderr)
            steps = json.loads(result.stdout)
            self.assertEqual([s["name"] for s in steps], ["qtbase", "extra-cmake-modules",
                "kcoreaddons", "kcodecs", "kconfig", "kguiaddons", "ki18n",
                "kwidgetsaddons", "kconfigwidgets"])
            for step in steps[1:]:
                self.assertIn("prefixes/qt5", step["cmake_prefix_path"])
                self.assertIn("prefixes/kf5", step["cmake_prefix_path"])
                self.assertNotIn("kde6", step["cmake_prefix_path"])

    def test_rejects_relative_root_and_unknown_target_without_side_effects(self):
        for target, root in [("qt6", "relative"), ("qt7", "/tmp/not-used")]:
            with self.subTest(target=target):
                result = self.run_plan(target, root)
                self.assertNotEqual(result.returncode, 0)
                self.assertIn("desktop-colors:", result.stderr)

    def test_corrupt_source_archive_is_rejected_before_extraction(self):
        self.assertTrue(MODULE.is_file(), "dependency preparer is missing")
        spec = importlib.util.spec_from_file_location("build_baseline", MODULE)
        module = importlib.util.module_from_spec(spec)
        spec.loader.exec_module(module)
        with tempfile.TemporaryDirectory() as directory:
            archive = pathlib.Path(directory) / "source.tar.xz"
            archive.write_bytes(b"")
            empty_sha256 = "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855"
            module.verify_archive(archive, empty_sha256)
            archive.write_bytes(b"corrupted source")
            with self.assertRaisesRegex(ValueError, "SHA256 mismatch"):
                module.verify_archive(archive, empty_sha256)

    @unittest.skipUnless(shutil.which("cmake"), "CMake is not installed")
    def test_missing_sdk_diagnostic_names_required_target(self):
        for major, kind, package in [(5, "qt", "Qt5 Widgets >= 5.15"),
                                     (6, "qt", "Qt6 Widgets >= 6.2"),
                                     (6, "kde", "Qt6 Widgets >= 6.5")]:
            with self.subTest(major=major, kind=kind), tempfile.TemporaryDirectory() as directory:
                result = subprocess.run(["cmake", "-S", str(ROOT), "-B", directory,
                    f"-DDC_QT_MAJOR={major}", f"-DDC_PROBE_KIND={kind}",
                    f"-DCMAKE_DISABLE_FIND_PACKAGE_Qt{major}=ON"], text=True, capture_output=True)
                self.assertNotEqual(result.returncode, 0)
                self.assertIn("Missing desktop-colors SDK: " + package, result.stdout + result.stderr)


if __name__ == "__main__":
    unittest.main()
