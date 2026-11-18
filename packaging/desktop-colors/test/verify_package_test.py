#!/usr/bin/env python3
"""Selected production files retain the full ELF validation contract."""
import importlib.util
import pathlib
import stat
import tempfile
import unittest
from unittest.mock import patch

spec = importlib.util.spec_from_file_location("verify", pathlib.Path(__file__).with_name("verify-qt-package.py"))
verify = importlib.util.module_from_spec(spec)
spec.loader.exec_module(verify)


class SelectedPackageTest(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.root = pathlib.Path(self.temp.name)
        header = bytearray(20)
        header[:6] = b"\x7fELF\x02\x01"
        header[18:20] = (183 if verify.platform.machine() in ("aarch64", "arm64") else 62).to_bytes(2, "little")
        self.helper = self.root / "jasper-desktop-portal"
        self.helper.write_bytes(header)
        self.helper.chmod(0o755)
        self.commands = []

    def output(self, *args):
        self.commands.append(args)
        return "GLIBC_2.99" if "--version-info" in args else ""

    def validate(self, floor=False, expected=("jasper-desktop-portal",)):
        with patch.object(verify, "output", self.output), patch.object(verify.subprocess, "run") as run:
            run.return_value.returncode = 0
            run.return_value.stdout = ""
            run.return_value.stderr = ""
            verify.verify(self.root, floor=floor, expected=expected)

    def test_portal_only_selection_passes_without_release_floor(self):
        self.validate()
        self.assertFalse(any("--version-info" in args for args in self.commands))

    def test_release_validation_still_enforces_floor(self):
        with self.assertRaisesRegex(AssertionError, "floor exceeded"):
            self.validate(floor=True)

    def test_stale_unselected_file_and_missing_selected_file_fail(self):
        (self.root / "jasper-desktop-qt5").touch()
        with self.assertRaisesRegex(AssertionError, "selected production helpers"):
            self.validate()
        (self.root / "jasper-desktop-qt5").unlink()
        with self.assertRaisesRegex(AssertionError, "selected production helpers"):
            self.validate(expected=("jasper-desktop-portal", "jasper-desktop-gtk3"))

    def test_portal_cannot_be_omitted_and_names_must_be_known_unique_helpers(self):
        for expected in ((), ("jasper-desktop-qt5",), ("jasper-desktop-portal", "../helper"),
                         ("jasper-desktop-portal", "jasper-desktop-portal")):
            with self.subTest(expected=expected), self.assertRaisesRegex(AssertionError, "selection"):
                self.validate(expected=expected)

    def test_selected_helpers_still_require_executable_mode(self):
        self.helper.chmod(stat.S_IRUSR)
        with self.assertRaisesRegex(AssertionError, "expected mode 0755"):
            self.validate()


if __name__ == "__main__":
    unittest.main()
