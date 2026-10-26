"""Host-independent checks of the explicit dependency preparation boundary."""
import importlib.util
import json
import pathlib
import subprocess
import tempfile
import unittest
import shutil
import hashlib
import os
import tomllib

ROOT = pathlib.Path(__file__).resolve().parents[1]
SCRIPT = ROOT / "toolchains/build-baseline.sh"
MODULE = ROOT / "toolchains/build_baseline.py"


def load_preparer():
    spec = importlib.util.spec_from_file_location("build_baseline", MODULE)
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module


class RecipeCorrectionsTest(unittest.TestCase):
    def setUp(self):
        self.module = load_preparer()
        self.manifest = tomllib.loads((ROOT / "toolchains/manifest.toml").read_text())

    def test_sdk_binary_path_uses_target_even_when_root_contains_qt5(self):
        for target, expected in [("qt6", "/tmp/qt5-investigation/prefixes/qt6/bin"),
                                 ("kde6", "/tmp/qt5-investigation/prefixes/kde6-qt/bin")]:
            with self.subTest(target=target):
                steps = self.module.plan(target, pathlib.Path('/tmp/qt5-investigation'), self.manifest)
                self.assertTrue(hasattr(self.module, 'step_environment'), 'explicit SDK environment missing')
                env = self.module.step_environment(steps[-1], {'PATH': '/usr/bin', 'LD_LIBRARY_PATH': '/old'})
                self.assertEqual(env['PATH'].split(os.pathsep)[0], expected)
                self.assertNotIn('LD_LIBRARY_PATH', env)

    def test_qt6_configuration_disables_opengl_input_check(self):
        self.assertTrue(hasattr(self.module, 'cmake_options'), 'testable configure boundary missing')
        for target in ['qt6', 'kde6']:
            step = self.module.plan(target, pathlib.Path('/tmp/compat'), self.manifest)[0]
            self.assertIn('-DINPUT_opengl=no', self.module.cmake_options(step))

    def fixture(self, root):
        source = root / 'source'; source.mkdir()
        patch_root = root / 'patches'; patch_root.mkdir()
        original = b'first\nold\nlast\n'; changed = b'first\nnew\nlast\n'
        (source / 'CMakeLists.txt').write_bytes(original)
        patch = b'--- a/CMakeLists.txt\n+++ b/CMakeLists.txt\n@@ -1,3 +1,3 @@\n first\n-old\n+new\n last\n'
        (patch_root / 'fix.patch').write_bytes(patch)
        spec = dict(file='fix.patch', sha256=hashlib.sha256(patch).hexdigest(), files=[dict(
            path='CMakeLists.txt', before=hashlib.sha256(original).hexdigest(),
            after=hashlib.sha256(changed).hexdigest())])
        return source, patch_root, spec

    def test_patch_applies_once_and_rerun_preserves_source(self):
        self.assertTrue(hasattr(self.module, 'apply_patches'), 'audited patch boundary missing')
        with tempfile.TemporaryDirectory() as directory:
            source, patch_root, spec = self.fixture(pathlib.Path(directory))
            self.module.apply_patches(source, [spec], patch_root)
            file = source / 'CMakeLists.txt'; modified = file.stat().st_mtime_ns
            self.assertEqual(file.read_text(), 'first\nnew\nlast\n')
            self.module.apply_patches(source, [spec], patch_root)
            self.assertEqual(file.stat().st_mtime_ns, modified)

    def test_changed_source_or_corrupt_patch_fails_before_source_mutation(self):
        self.assertTrue(hasattr(self.module, 'apply_patches'), 'audited patch boundary missing')
        for corrupt in ['source', 'patch', 'expected result']:
            with self.subTest(corrupt=corrupt), tempfile.TemporaryDirectory() as directory:
                source, patch_root, spec = self.fixture(pathlib.Path(directory))
                if corrupt == 'source': (source / 'CMakeLists.txt').write_text('unreviewed source\n')
                if corrupt == 'patch': (patch_root / 'fix.patch').write_text('unreviewed patch\n')
                if corrupt == 'expected result': spec['files'][0]['after'] = '0' * 64
                before = (source / 'CMakeLists.txt').read_bytes()
                with self.assertRaisesRegex(ValueError, 'SHA256|source precondition|patched result'):
                    self.module.apply_patches(source, [spec], patch_root)
                self.assertEqual((source / 'CMakeLists.txt').read_bytes(), before)

    def test_partially_patched_source_is_rejected(self):
        with tempfile.TemporaryDirectory() as directory:
            source, patch_root, spec = self.fixture(pathlib.Path(directory))
            (source / 'CMakeLists.txt').write_text('first\nnew\nlast\n')
            (source / 'Other.txt').write_bytes(b'old\n')
            spec['files'].append(dict(path='Other.txt', before=hashlib.sha256(b'old\n').hexdigest(),
                                      after=hashlib.sha256(b'new\n').hexdigest()))
            with self.assertRaisesRegex(ValueError, 'source precondition'):
                self.module.apply_patches(source, [spec], patch_root)
            self.assertEqual((source / 'Other.txt').read_bytes(), b'old\n')

    def test_missing_target_tool_has_named_preflight_diagnostic(self):
        self.assertTrue(hasattr(self.module, 'check_tools'), 'named tool preflight missing')
        with tempfile.TemporaryDirectory() as directory:
            root = pathlib.Path(directory)
            for tool in ['cmake', 'ninja', 'cc', 'c++', 'pkg-config', 'make', 'perl', 'patch', 'lrelease', 'lconvert']:
                (root / tool).symlink_to(shutil.which('true'))
            steps = self.module.plan('kde5', pathlib.Path('/tmp/compat'), self.manifest)
            with self.assertRaisesRegex(ValueError, 'missing build dependency: gperf'):
                self.module.check_tools(steps, search_path=directory)

    def test_missing_pkg_config_module_has_named_preflight_diagnostic(self):
        self.assertTrue(hasattr(self.module, 'check_pkg_config'), 'named pkg-config preflight missing')
        with self.assertRaisesRegex(ValueError, 'jasper-desktop-colors-test-missing'):
            self.module.check_pkg_config([dict(pkg_config=['jasper-desktop-colors-test-missing'])], dict(os.environ))


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
