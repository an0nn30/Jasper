import pathlib
import subprocess
import sys
import tempfile
import time
import unittest

SCRIPT = pathlib.Path(__file__).with_name('windows_check.py')

class WindowsCheckTest(unittest.TestCase):
    def run_check(self, script, timeout):
        with tempfile.TemporaryDirectory() as folder:
            result = subprocess.run([sys.executable, str(SCRIPT), '--timeout', str(timeout),
                '--capture-at', '0.1', '--diagnostics', folder, '--', sys.executable, '-c', script],
                capture_output=True, text=True, timeout=15)
            return result, list(pathlib.Path(folder).glob('*'))

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
