"""Exercise the CI installer with real archives and pinned digest verification."""
import hashlib
import io
import os
from pathlib import Path
import subprocess
import tarfile
import tempfile
import unittest

SCRIPT = Path(__file__).with_name('install.sh')


class InstallTest(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory(prefix='jasper jbr test ')
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)
        self.workspace = self.root / 'workspace'
        (self.workspace / 'packaging/jcef').mkdir(parents=True)
        self.bin = self.root / 'bin'
        self.bin.mkdir()
        curl = self.bin / 'curl'
        curl.write_text('''#!/bin/bash
set -eu
while [[ $# -gt 0 ]]; do
  case "$1" in
    --output) destination=$2; shift 2 ;;
    https:*) printf '%s\\n' "$1" > "$TEST_URL"; shift ;;
    *) shift ;;
  esac
done
cp "$TEST_ARCHIVE" "$destination"
''')
        curl.chmod(0o755)
        # On real Windows runners Git Bash supplies cygpath. Here paths already
        # use the host format, allowing the Windows branch to execute headlessly.
        cygpath = self.bin / 'cygpath'
        cygpath.write_text('#!/bin/bash\nprintf "%s\\n" "$2"\n')
        cygpath.chmod(0o755)
        self.env = dict(os.environ, PATH=str(self.bin) + os.pathsep + os.environ['PATH'],
                        GITHUB_WORKSPACE=str(self.workspace), RUNNER_TEMP=str(self.root),
                        GITHUB_ENV=str(self.root / 'env'), GITHUB_PATH=str(self.root / 'path'),
                        TEST_URL=str(self.root / 'url'), TEST_ARCHIVE=str(self.root / 'fixture.tar.gz'))

    def fixture(self, platform, arch, corrupt=False):
        name = f'jbrsdk_jcef-25.0.4.1-{platform}-{arch}-b635.75.tar.gz'
        prefix = 'bundle/Contents/Home' if platform == 'osx' else 'bundle'
        executable = 'java.exe' if platform == 'windows' else 'java'
        with tarfile.open(self.env['TEST_ARCHIVE'], 'w:gz') as archive:
            content = b'#!/bin/bash\nexit 0\n'
            entry = tarfile.TarInfo(f'{prefix}/bin/{executable}')
            entry.size, entry.mode = len(content), 0o755
            archive.addfile(entry, io.BytesIO(content))
            if platform == 'osx':
                entry = tarfile.TarInfo('bundle/Contents/Frameworks/chromium')
                entry.size = 4
                archive.addfile(entry, io.BytesIO(b'full'))
        digest = hashlib.sha512(Path(self.env['TEST_ARCHIVE']).read_bytes()).hexdigest()
        (self.workspace / 'packaging/jcef/checksums.txt').write_text(
            f'{digest if not corrupt else "0" * 128}  {name}\n')

    def run_installer(self, os_name, arch):
        return subprocess.run(['bash', str(SCRIPT)], env=dict(self.env, RUNNER_OS=os_name, RUNNER_ARCH=arch),
                              capture_output=True, text=True)

    def test_selects_verified_sdk_for_every_supported_runner(self):
        for os_name, platform, runner_arch, archive_arch in [
            ('Linux', 'linux', 'X64', 'x64'), ('Linux', 'linux', 'ARM64', 'aarch64'),
            ('macOS', 'osx', 'X64', 'x64'), ('macOS', 'osx', 'ARM64', 'aarch64'),
            ('Windows', 'windows', 'X64', 'x64'), ('Windows', 'windows', 'ARM64', 'aarch64'),
        ]:
            with self.subTest(os=os_name, arch=runner_arch):
                self.fixture(platform, archive_arch)
                result = self.run_installer(os_name, runner_arch)
                self.assertEqual(result.returncode, 0, result.stderr)
                self.assertTrue((self.root / 'url').is_file(), 'installer did not request the SDK archive')
                self.assertEqual((self.root / 'url').read_text().strip(),
                                 f'https://cache-redirector.jetbrains.com/intellij-jbr/jbrsdk_jcef-25.0.4.1-{platform}-{archive_arch}-b635.75.tar.gz')
                java_home = Path((self.root / 'env').read_text().splitlines()[-1].removeprefix('JAVA_HOME='))
                expected_bin = str(java_home) + ('\\bin' if platform == 'windows' else '/bin')
                self.assertEqual((self.root / 'path').read_text().splitlines()[-1], expected_bin)
                self.assertTrue((java_home / 'bin' / ('java.exe' if platform == 'windows' else 'java')).is_file())
                if platform == 'osx':
                    self.assertEqual((java_home.parent / 'Frameworks/chromium').read_bytes(), b'full')

    def test_checksum_failure_does_not_extract_or_publish_java(self):
        self.fixture('linux', 'x64', corrupt=True)
        result = self.run_installer('Linux', 'X64')
        self.assertNotEqual(result.returncode, 0)
        self.assertIn('checksum', result.stderr.lower())
        self.assertFalse((self.root / 'env').exists())
        self.assertFalse(list(self.root.glob('jasper-jbr.*/sdk/bin/java')))

    def test_unsupported_platform_fails_before_download(self):
        result = self.run_installer('Linux', 'RISCV64')
        self.assertNotEqual(result.returncode, 0)
        self.assertIn('Unsupported', result.stderr)
        self.assertFalse((self.root / 'url').exists())


if __name__ == '__main__':
    unittest.main()
