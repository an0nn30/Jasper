#!/usr/bin/env python3
"""Exercise distro locale setup without package installation or native windows."""
import os
import pathlib
import subprocess
import tempfile
import unittest

SCRIPT = pathlib.Path(__file__).with_name("check-runtime.sh")


@unittest.skipIf(os.name == "nt", "requires POSIX shell")
class RuntimeLocaleTest(unittest.TestCase):
    def run_setup(self, distro, available=True):
        with tempfile.TemporaryDirectory() as directory:
            tools = pathlib.Path(directory)
            commands = {
                "dnf": "printf ready > \"$FIXTURE_LOCALE\"\n",
                "locale": 'test "$LC_ALL" = de_DE.UTF-8 || exit 1\n'
                          'if [ -f "$FIXTURE_LOCALE" ]; then echo UTF-8; else echo ANSI_X3.4-1968; fi\n',
                "localedef": ('printf ready > "$FIXTURE_LOCALE"\n' if distro == "ubuntu" else
                              'echo "character map file UTF-8 not found" >&2\nexit 4\n'),
                # Stop at the first real verification boundary; no native helper can start.
                "python3": "exit 73\n",
            }
            if distro == "ubuntu":
                commands["apt-get"] = "exit 0\n"
            if not available:
                commands["dnf"] = "exit 0\n"
            for name, body in commands.items():
                command = tools/name
                command.write_text("#!/bin/sh\n" + body)
                command.chmod(0o755)
            return subprocess.run(["/bin/sh", str(SCRIPT)], text=True, capture_output=True,
                                  env={**os.environ, "PATH": str(tools),
                                       "FIXTURE_LOCALE": str(tools/"locale-ready")})

    def test_fedora_consumes_installed_precompiled_german_locale(self):
        result = self.run_setup("fedora")
        self.assertEqual(result.returncode, 73, result.stdout + result.stderr)

    def test_ubuntu_builds_german_locale_before_native_verification(self):
        result = self.run_setup("ubuntu")
        self.assertEqual(result.returncode, 73, result.stdout + result.stderr)

    def test_missing_german_utf8_locale_fails_before_native_verification(self):
        result = self.run_setup("fedora", available=False)
        self.assertEqual(result.returncode, 1, result.stdout + result.stderr)


if __name__ == "__main__":
    unittest.main()
