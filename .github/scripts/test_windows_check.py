import os
import pathlib
import subprocess
import sys
import tempfile
import time
import unittest
import unittest.mock

SCRIPT = pathlib.Path(__file__).with_name('windows_check.py')

def run_git_bash(command, *args):
    return subprocess.run([os.environ['JASPER_CI_GIT_BASH'], '-c', command, *args],
        capture_output=True, text=True, timeout=15)

class WindowsCheckTest(unittest.TestCase):
    def run_check(self, script, timeout):
        with tempfile.TemporaryDirectory() as folder:
            result = subprocess.run([sys.executable, str(SCRIPT), '--timeout', str(timeout),
                '--capture-at', '0.1', '--diagnostics', folder, '--', sys.executable, '-c', script],
                capture_output=True, text=True, timeout=15)
            return result, list(pathlib.Path(folder).glob('*'))

    def test_zero_exit_without_fresh_marker_fails(self):
        with tempfile.TemporaryDirectory() as folder:
            marker = pathlib.Path(folder) / 'marker'
            marker.touch()
            result = subprocess.run([sys.executable, str(SCRIPT), '--timeout', '5',
                '--diagnostics', folder, '--success-marker', str(marker), '--',
                sys.executable, '-c', 'pass'], capture_output=True, text=True, timeout=15)
            self.assertNotEqual(result.returncode, 0, result.stdout)
            self.assertIn('completion marker', result.stdout)

    def test_zero_exit_with_fresh_marker_succeeds(self):
        with tempfile.TemporaryDirectory() as folder:
            marker = pathlib.Path(folder) / 'marker'
            child = "import os,pathlib; pathlib.Path(os.environ['JASPER_CI_SUCCESS_MARKER']).write_bytes(b'Gradle build completed successfully\\n')"
            result = subprocess.run([sys.executable, str(SCRIPT), '--timeout', '5',
                '--diagnostics', folder, '--success-marker', str(marker), '--',
                sys.executable, '-c', child], capture_output=True, text=True, timeout=15)
            self.assertEqual(result.returncode, 0, result.stderr + result.stdout)

    def test_fresh_empty_and_malformed_markers_fail(self):
        for contents in (b'', b'Gradle build completed successfully', b'wrong'):
            with self.subTest(contents=contents), tempfile.TemporaryDirectory() as folder:
                marker = pathlib.Path(folder) / 'marker'
                child = "import os,pathlib; pathlib.Path(os.environ['JASPER_CI_SUCCESS_MARKER']).write_bytes(%r)" % contents
                result = subprocess.run([sys.executable, str(SCRIPT), '--timeout', '5',
                    '--diagnostics', folder, '--success-marker', str(marker), '--',
                    sys.executable, '-c', child], capture_output=True, text=True, timeout=15)
                self.assertNotEqual(result.returncode, 0, result.stdout)
                self.assertIn('completion marker', result.stdout)

    def test_failure_is_preserved_without_marker(self):
        with tempfile.TemporaryDirectory() as folder:
            marker = pathlib.Path(folder) / 'marker'
            result = subprocess.run([sys.executable, str(SCRIPT), '--timeout', '5',
                '--diagnostics', folder, '--success-marker', str(marker), '--',
                sys.executable, '-c', 'raise SystemExit(7)'],
                capture_output=True, text=True, timeout=15)
            self.assertEqual(result.returncode, 7, result.stderr)

    def test_git_bash_selection_ignores_wsl_first_on_path(self):
        with tempfile.TemporaryDirectory() as folder:
            wsl_bash = pathlib.Path(folder) / ('bash.exe' if os.name == 'nt' else 'bash')
            wsl_bash.write_text('not Git Bash')
            wsl_bash.chmod(0o755)
            with unittest.mock.patch.dict(os.environ, {
                    'PATH': folder, 'JASPER_CI_GIT_BASH': sys.executable}):
                result = run_git_bash("print('selected shell')")
            self.assertEqual(result.returncode, 0, result.stderr)
            self.assertEqual(result.stdout.strip(), 'selected shell')

    @unittest.skipUnless(os.name == 'nt', 'requires Windows Git Bash and cmd.exe')
    def test_git_bash_batch_launch_preserves_arguments_and_exit(self):
        with tempfile.TemporaryDirectory() as folder:
            batch_dir = pathlib.Path(folder) / 'batch with spaces'
            batch_dir.mkdir()
            batch = batch_dir / 'fixture.bat'
            batch.write_bytes(b'@echo off\r\necho ARGS:%*\r\nexit /b 7\r\n')
            command = '"$1" "$2" --timeout 5 --diagnostics "$3" --windows-batch -- "$4" check --parallel --max-workers=1 --continue --stacktrace --console=plain --init-script .github/scripts/windows-test-progress.gradle'
            result = run_git_bash(command, 'fixture', sys.executable,
                str(SCRIPT), folder, str(batch))
            self.assertEqual(result.returncode, 7, result.stdout + result.stderr)
            self.assertIn('ARGS:check --parallel --max-workers=1 --continue --stacktrace --console=plain --init-script .github/scripts/windows-test-progress.gradle', result.stdout)

    def test_preserves_child_failure(self):
        result, _ = self.run_check('raise SystemExit(7)', 5)
        self.assertEqual(result.returncode, 7, result.stderr)

    def test_timeout_reports_failure_and_captures_diagnostics(self):
        result, files = self.run_check('import time; time.sleep(10)', 0.4)
        self.assertNotEqual(result.returncode, 0)
        self.assertIn('timed out', result.stdout)
        self.assertTrue(files, 'expected diagnostic snapshots')

    def test_timeout_stops_child_processes(self):
        with tempfile.TemporaryDirectory() as folder:
            marker = pathlib.Path(folder) / 'survived'
            child = "import pathlib,time; time.sleep(2); pathlib.Path(%r).touch()" % str(marker)
            parent = "import subprocess,sys,time; subprocess.Popen([sys.executable, '-c', %r]); time.sleep(10)" % child
            self.run_check(parent, 0.4)
            time.sleep(2.2)
            self.assertFalse(marker.exists(), 'descendant survived timeout')

    def test_success_preserves_status(self):
        result, _ = self.run_check('pass', 5)
        self.assertEqual(result.returncode, 0, result.stderr)

if __name__ == '__main__':
    unittest.main()
