#!/usr/bin/env python3
"""Verify the full check graph is covered before running one isolated Windows shard."""
import argparse
import json
import os
from pathlib import Path
import subprocess
import sys
import tempfile

APP = [':jasper-app:check']
OTHER = [':jasper-app:verifyJcefToolchain', ':jasper-buddy:check', ':jasper-ui:check',
         ':jasper-terminal:check', ':jasper-sdk:check', ':jasper-sdk-testkit:check',
         ':jasper-plugin-editor:check', ':jasper-plugin-enterprise:check', ':jasper-plugin-fileserver:check',
         ':jasper-plugin-history:check', ':jasper-plugin-indexer:check', ':jasper-plugin-remote:check',
         ':jasper-plugin-sample:check', ':jasper-plugin-snippets:check', ':jasper-plugin-vault:check']
OPTIONS = ['--parallel', '--max-workers=1', '--continue', '--stacktrace', '--console=plain',
           '--init-script', '.github/scripts/windows-test-progress.gradle',
           '--init-script', '.github/scripts/windows_shard_graph.gradle']


def load_graph(path):
    value = json.loads(path.read_text(encoding='utf-8'))
    if not isinstance(value, list) or not value or any(not isinstance(x, str) or not x.startswith(':') for x in value):
        raise ValueError('invalid or empty Gradle task graph')
    if len(set(value)) != len(value):
        raise ValueError('duplicate Gradle task paths')
    return set(value)


def validate(full, app, other):
    if not set(APP) <= app or not set(OTHER) <= other:
        raise ValueError('a required shard root is missing')
    missing, extra = full - (app | other), (app | other) - full
    if missing or extra:
        raise ValueError('shards differ from full check; missing=%s extra=%s' % (sorted(missing), sorted(extra)))


def run(wrapper, args, env):
    return subprocess.run(wrapper + args, env=env, check=False).returncode


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('shard', choices=['app', 'other'])
    parser.add_argument('--wrapper', default='gradlew.bat' if os.name == 'nt' else './gradlew')
    parser.add_argument('--graph-evidence', type=Path)
    args = parser.parse_args()
    wrapper = [os.environ.get('COMSPEC', 'cmd.exe'), '/c', args.wrapper] if os.name == 'nt' else [args.wrapper]
    with tempfile.TemporaryDirectory(prefix='jasper-check-graphs-') as tmp:
        directory = args.graph_evidence.resolve() if args.graph_evidence else Path(tmp)
        directory.mkdir(parents=True, exist_ok=True)
        graphs = {}
        for name, roots in [('full', ['check']), ('app', APP), ('other', OTHER)]:
            target = directory / (name + '.json')
            target.unlink(missing_ok=True)
            env = os.environ.copy()
            env.pop('JASPER_CI_SUCCESS_MARKER', None)
            env.pop('JASPER_CI_EXPECTED_GRAPH', None)
            env['JASPER_CI_GRAPH_FILE'] = str(target)
            code = run(wrapper, roots + OPTIONS + ['--dry-run'], env)
            if code:
                return code
            try:
                graphs[name] = load_graph(target)
            except (OSError, ValueError, json.JSONDecodeError) as error:
                print('Cannot read %s task graph: %s' % (name, error), file=sys.stderr, flush=True)
                return 1
        try:
            validate(graphs['full'], graphs['app'], graphs['other'])
        except ValueError as error:
            print(str(error), file=sys.stderr, flush=True)
            return 1
        print('Verified check graph: full=%d app=%d other=%d overlap=%d' %
              (len(graphs['full']), len(graphs['app']), len(graphs['other']),
               len(graphs['app'] & graphs['other'])), flush=True)
        expected = directory / 'expected.json'
        expected.write_text(json.dumps(sorted(graphs[args.shard])), encoding='utf-8')
        env = os.environ.copy()
        actual = directory / 'actual.json'
        actual.unlink(missing_ok=True)
        env['JASPER_CI_GRAPH_FILE'] = str(actual)
        env['JASPER_CI_EXPECTED_GRAPH'] = str(expected)
        return run(wrapper, (APP if args.shard == 'app' else OTHER) + OPTIONS, env)


if __name__ == '__main__':
    sys.exit(main())
