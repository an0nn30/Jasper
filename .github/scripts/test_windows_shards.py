import importlib.util
import json
import os
from pathlib import Path
import sys
import tempfile
import unittest
from unittest import mock

spec = importlib.util.spec_from_file_location('windows_shards', Path(__file__).with_name('windows_shards.py'))
shards = importlib.util.module_from_spec(spec)
spec.loader.exec_module(shards)
sys.modules["windows_shards"] = shards
verify_spec = importlib.util.spec_from_file_location("verify_windows_shards", Path(__file__).with_name("verify_windows_shards.py"))
verifier = importlib.util.module_from_spec(verify_spec)
verify_spec.loader.exec_module(verifier)


class WindowsShardsTest(unittest.TestCase):
    def test_graph_must_cover_full_check_without_extra_tasks(self):
        app, other = set(shards.APP), set(shards.OTHER)
        shards.validate(app | other | {':common'}, app | {':common'}, other)
        for full, a, b in [(app | other | {':new:check'}, app, other),
                           (app | other, app | {':unexpected'}, other),
                           (app | other, set(), other),
                           (app | other, app, other - {':jasper-sdk:check'})]:
            with self.assertRaises(ValueError):
                shards.validate(full, a, b)

    def test_graph_json_is_nonempty_unique_task_paths(self):
        with tempfile.TemporaryDirectory() as tmp:
            path = Path(tmp) / 'graph.json'
            for value in ([], {}, [':a', ':a'], ['a'], [42]):
                path.write_text(json.dumps(value))
                with self.subTest(value=value), self.assertRaises(ValueError):
                    shards.load_graph(path)
            path.write_text(json.dumps([':a', ':b']))
            self.assertEqual(shards.load_graph(path), {':a', ':b'})

    def exercise(self, shard='app', bad=None):
        calls = []
        full = sorted(set(shards.APP + shards.OTHER + [':shared']))
        app = sorted(set(shards.APP + [':shared']))
        other = sorted(set(shards.OTHER + [':shared']))
        def fake_run(wrapper, args, env):
            calls.append((args, env.copy()))
            if '--dry-run' in args:
                target = Path(env['JASPER_CI_GRAPH_FILE'])
                index = len(calls) - 1
                if bad == 'failed' and index == 0:
                    return 7
                if bad == 'missing' and index == 0:
                    return 0
                if bad == 'malformed' and index == 0:
                    target.write_text('{')
                else:
                    graph = [full, app, other][index]
                    if bad == 'uncovered' and index == 0:
                        graph = graph + [':new:check']
                    target.write_text(json.dumps(graph))
                self.assertNotIn('JASPER_CI_SUCCESS_MARKER', env)
                return 0
            self.assertEqual(Path(env['JASPER_CI_GRAPH_FILE']).name, 'actual.json')
            self.assertEqual(json.loads(Path(env['JASPER_CI_EXPECTED_GRAPH']).read_text()), app if shard == 'app' else other)
            self.assertEqual(env['JASPER_CI_SUCCESS_MARKER'], 'marker')
            return 0
        with mock.patch.object(sys, 'argv', ['windows_shards.py', shard]), \
             mock.patch.dict(os.environ, {'JASPER_CI_SUCCESS_MARKER': 'marker'}), \
             mock.patch.object(shards, 'run', side_effect=fake_run):
            result = shards.main()
        return result, calls

    def test_both_shards_run_only_after_all_graphs_are_verified(self):
        for shard in ('app', 'other'):
            result, calls = self.exercise(shard)
            self.assertEqual(result, 0)
            self.assertEqual(len(calls), 4)
            self.assertEqual(calls[-1][0][:len(shards.APP if shard == 'app' else shards.OTHER)],
                             shards.APP if shard == 'app' else shards.OTHER)

    def test_aggregate_requires_both_actual_graphs_and_same_full_graph(self):
        app, other = set(shards.APP), set(shards.OTHER)
        full = app | other
        with tempfile.TemporaryDirectory() as tmp:
            a, b = Path(tmp) / 'app', Path(tmp) / 'other'
            a.mkdir(); b.mkdir()
            def write(directory, name, graph):
                (directory / (name + '.json')).write_text(json.dumps(sorted(graph)))
            write(a, 'full', full); write(b, 'full', full)
            write(a, 'app', app); write(a, 'actual', app)
            write(b, 'other', other); write(b, 'actual', other)
            self.assertEqual(verifier.verify(a, b), (len(full), len(app), len(other)))
            write(b, 'full', full | {':host-specific'})
            with self.assertRaises(ValueError): verifier.verify(a, b)
            write(b, 'full', full); write(b, 'actual', other - {':jasper-sdk:check'})
            with self.assertRaises(ValueError): verifier.verify(a, b)
            (b / 'actual.json').unlink()
            with self.assertRaises(OSError): verifier.verify(a, b)

    def test_discovery_failure_cannot_run_real_build(self):
        for bad in ('failed', 'missing', 'malformed', 'uncovered'):
            with self.subTest(bad=bad):
                result, calls = self.exercise(bad=bad)
                self.assertNotEqual(result, 0)
                self.assertTrue(all('--dry-run' in args for args, _ in calls))


if __name__ == '__main__':
    unittest.main()
