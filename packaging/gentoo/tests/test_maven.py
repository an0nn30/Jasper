import hashlib
import json
import re
from pathlib import Path
import subprocess
import sys
import tempfile
import unittest

SCRIPT = Path(__file__).resolve().parents[1] / 'app-misc/jasper-workstation/files/maven.py'


class MavenInputsTest(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)

    def run_tool(self, *args, success=True):
        result = subprocess.run([sys.executable, str(SCRIPT), *map(str, args)], capture_output=True, text=True)
        if success:
            self.assertEqual(result.returncode, 0, result.stderr)
        else:
            self.assertNotEqual(result.returncode, 0)
        return result

    def row(self, group, filename, data=b'content'):
        return {'path': f'{group}/same/1.0/cachehash/{filename}', 'sha256': hashlib.sha256(data).hexdigest(), 'size': len(data)}

    def generate(self, rows, output='lock.json'):
        inventory = self.root / 'inventory.json'
        inventory.write_text(json.dumps(rows))
        lock = self.root / output
        self.run_tool('lock', inventory, lock)
        return lock

    def test_checked_in_catalog_matches_the_ebuild_fetch_list(self):
        root = SCRIPT.parent.parent
        result = self.run_tool('uris', SCRIPT.parent / 'maven-lock.json')
        ebuild = next(root.glob('*.ebuild')).read_text()
        uris = [line.strip() for line in ebuild.splitlines() if ' -> jasper-maven-' in line]
        self.assertEqual(uris, result.stdout.splitlines())

    def test_manifest_pins_every_locked_file_and_the_required_jcef_sdk(self):
        root = SCRIPT.parent.parent
        manifest = {line.split()[1]: line.split()[2:] for line in (root / 'Manifest').read_text().splitlines()}
        for row in json.loads((SCRIPT.parent / 'maven-lock.json').read_text())['artifacts']:
            self.assertIn(row['distfile'], manifest)
            self.assertEqual(int(manifest[row['distfile']][0]), row['size'])
        ebuild = next(root.glob('*.ebuild')).read_text()
        jbr = re.search(r'^JASPER_JBR="([^"]+)"', ebuild, re.MULTILINE).group(1) + '.tar.gz'
        checksums = root.parents[2] / 'jcef/checksums.txt'
        expected = next(line.split()[0] for line in checksums.read_text().splitlines() if line.endswith('  ' + jbr))
        values = manifest[jbr]
        self.assertEqual(values[values.index('SHA512') + 1], expected)

    def test_inventory_hashes_files_and_lock_rejects_unknown_inputs(self):
        cache = self.root / 'cache'
        file = cache / 'a/same/1.0/hash/same-1.0.pom'
        file.parent.mkdir(parents=True)
        file.write_bytes(b'content')
        inventory = self.root / 'inventory.json'
        self.run_tool('inventory', cache, inventory)
        self.assertEqual(json.loads(inventory.read_text()), [self.row('a', 'same-1.0.pom') | {'path': 'a/same/1.0/hash/same-1.0.pom'}])

    def test_excludes_gradle_tooling_source_zip(self):
        row = self.row('gradle', 'gradle-9.7.0-src.zip')
        row['path'] = 'gradle/gradle/9.7.0/hash/gradle-9.7.0-src.zip'
        lock = self.generate([row])
        self.assertEqual(json.loads(lock.read_text())['artifacts'], [])

    def test_group_collisions_keep_distfiles_and_maven_paths_distinct(self):
        lock = self.generate([self.row('one.group', 'same-1.0.jar'), self.row('two.group', 'same-1.0.jar')])
        rows = json.loads(lock.read_text())['artifacts']
        self.assertEqual(len({r['distfile'] for r in rows}), 2)
        self.assertEqual({r['path'] for r in rows}, {'one/group/same/1.0/same-1.0.jar', 'two/group/same/1.0/same-1.0.jar'})

    def test_preserves_parent_poms_module_metadata_and_jetbrains_repository(self):
        lock = self.generate([self.row('org.jetbrains.jediterm', 'same-1.0.module'), self.row('org.parent', 'same-1.0.pom')])
        rows = json.loads(lock.read_text())['artifacts']
        self.assertTrue(rows[0]['url'].startswith('https://packages.jetbrains.team/maven/p/ij/intellij-dependencies/'))
        dist = self.root / 'dist'
        dist.mkdir()
        for row in rows:
            (dist / row['distfile']).write_bytes(b'content')
        repo = self.root / 'repo'
        self.run_tool('stage', lock, dist, repo)
        for row in rows:
            self.assertEqual((repo / row['path']).read_bytes(), b'content')

    def test_output_is_deterministic_and_duplicate_content_paths_coalesce(self):
        rows = [self.row('z', 'same-1.0.pom'), self.row('a', 'same-1.0.jar')]
        first = self.generate(rows).read_bytes()
        second = self.generate(list(reversed(rows)) + rows, 'second.json').read_bytes()
        self.assertEqual(first, second)

    def test_rejects_unsafe_paths_and_unsupported_cache_files(self):
        for path in ['../same/1.0/hash/same-1.0.jar', 'a/b/../../evil.jar', 'a/same/1.0/hash/arbitrary.zip', 'a/same/1.0/hash/other-1.0.jar']:
            inventory = self.root / 'bad.json'
            row = self.row('a', 'same-1.0.jar')
            row['path'] = path
            inventory.write_text(json.dumps([row]))
            self.run_tool('lock', inventory, self.root / 'bad-lock.json', success=False)

    def test_conflicting_duplicate_coordinate_fails(self):
        rows = [self.row('a', 'same-1.0.jar'), self.row('a', 'same-1.0.jar', b'different')]
        inventory = self.root / 'bad.json'
        inventory.write_text(json.dumps(rows))
        self.run_tool('lock', inventory, self.root / 'bad-lock.json', success=False)

    def test_stage_rejects_corruption_and_malicious_lock_paths(self):
        lock = self.generate([self.row('a', 'same-1.0.jar')])
        content = json.loads(lock.read_text())
        dist = self.root / 'dist'
        dist.mkdir()
        (dist / content['artifacts'][0]['distfile']).write_bytes(b'corrupt')
        self.run_tool('stage', lock, dist, self.root / 'repo', success=False)
        content['artifacts'][0]['path'] = '../escape.jar'
        lock.write_text(json.dumps(content))
        self.run_tool('stage', lock, dist, self.root / 'repo', success=False)
        self.assertFalse((self.root / 'escape.jar').exists())


if __name__ == '__main__':
    unittest.main()
