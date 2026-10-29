#!/usr/bin/env python3
"""ELF dependency policy: exact optional paths and direct distro providers."""
import importlib.util, os, pathlib, platform, subprocess, tempfile, unittest
from unittest.mock import patch
ROOT = pathlib.Path(__file__).resolve().parents[3]
spec = importlib.util.spec_from_file_location("dependencies", ROOT / "packaging/linux/dependencies.py")
deps = importlib.util.module_from_spec(spec); spec.loader.exec_module(deps)

class DependencyPolicyTest(unittest.TestCase):
    def test_only_exact_optional_paths_are_exempt(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = pathlib.Path(tmp)
            names = list(deps.OPTIONAL_HELPERS) + ["bin/jasper-desktop-qt5", "lib/app/desktop-colors/jasper-desktop-gtk3", "lib/runtime/lib/libQt6Core.so.6"]
            for name in names:
                path = root/name; path.parent.mkdir(parents=True, exist_ok=True); path.write_bytes(b"\x7fELFfixture")
            mandatory, bundled = deps.elf_inputs(root)
            self.assertEqual({p.relative_to(root).as_posix() for p in mandatory}, set(names) - deps.OPTIONAL_HELPERS)
            self.assertIn("libQt6Core.so.6", bundled)
            self.assertIn("jasper-desktop-qt5", {p.name for p in mandatory})

    @unittest.skipUnless(platform.system() == "Linux", "requires Linux ELF toolchain")
    def test_mandatory_binary_keeps_qt_named_requirements(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = pathlib.Path(tmp); image = root/"image"; image.mkdir()
            source = root/"library.c"; source.write_text("int policy_fixture(void) { return 7; }\n")
            library = root/"libQt5PolicyFixture.so.5"
            subprocess.run(["cc", "-shared", "-fPIC", "-Wl,-soname,libQt5PolicyFixture.so.5", str(source), "-o", str(library)], check=True)
            source.write_text("extern int policy_fixture(void); int main(void) { return policy_fixture(); }\n")
            optional = image/"lib/app/desktop-colors/jasper-desktop-qt5"; optional.parent.mkdir(parents=True)
            subprocess.run(["cc", str(source), str(library), "-o", str(optional)], check=True)
            self.assertNotIn("libQt5PolicyFixture.so.5()(64bit)", deps.requirements(image, "rpm"))
            mandatory = image/"bin/jasper-desktop-qt5"; mandatory.parent.mkdir(); mandatory.write_bytes(optional.read_bytes())
            self.assertIn("libQt5PolicyFixture.so.5()(64bit)", deps.requirements(image, "rpm"))
            with self.assertRaisesRegex(ValueError, "unresolved mandatory image dependency"):
                deps.requirements(image, "deb")

    @unittest.skipUnless(platform.system() == "Linux", "requires Linux ELF toolchain")
    def test_debian_requirements_leave_host_transitive_libraries_to_the_provider(self):
        # A distro may change an indirect SONAME while preserving the direct ABI:
        # Ubuntu22's libcups2 closure needs libunistring2, Ubuntu24's does not.
        with tempfile.TemporaryDirectory() as tmp:
            root = pathlib.Path(tmp); image = root/"image"; image.mkdir()
            source = root/"library.c"
            indirect = root/"libjasper-policy-transitive.so.2"
            source.write_text("int indirect(void) { return 7; }\n")
            subprocess.run(["cc", "-shared", "-fPIC", "-Wl,-soname," + indirect.name,
                            str(source), "-o", str(indirect)], check=True)
            direct = root/"libjasper-policy-direct.so.1"
            source.write_text("extern int indirect(void); int direct(void) { return indirect(); }\n")
            subprocess.run(["cc", "-shared", "-fPIC", "-Wl,-soname," + direct.name,
                            "-Wl,-rpath," + str(root), str(source), str(indirect), "-o", str(direct)], check=True)
            source.write_text("extern int direct(void); int main(void) { return direct(); }\n")
            subprocess.run(["cc", "-Wl,-rpath," + str(root), str(source), str(direct),
                            "-o", str(image/"app")], check=True)
            # Only package ownership is synthetic; readelf, ldd and the ELF graph are real.
            tools = root/"tools"; tools.mkdir()
            query = tools/"dpkg-query"
            query.write_text('''#!/bin/sh
case "$2" in
    */libjasper-policy-direct.so.1) provider=libcups2 ;;
    */libjasper-policy-transitive.so.2) provider=libunistring2 ;;
    */libc.so.6|*/ld-linux*.so.*) provider=libc6 ;;
    *) exit 1 ;;
esac
printf '%s:fixture-arch: %s\n' "$provider" "$2"
''')
            query.chmod(0o755)
            with patch.dict(os.environ, PATH=str(tools) + os.pathsep + os.environ["PATH"]):
                self.assertEqual(deps.requirements(image, "deb"), ["libc6", "libcups2", "xdg-utils"])
                # Declaration narrowing must still reject missing indirect libraries.
                indirect.unlink()
                with self.assertRaisesRegex(ValueError, "unresolved mandatory image dependency"):
                    deps.requirements(image, "deb")

if __name__ == "__main__": unittest.main()
