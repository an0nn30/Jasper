#!/usr/bin/env python3
"""Verify graph evidence from both isolated Windows runners."""
import argparse
from pathlib import Path
import sys

from windows_shards import load_graph, validate


def verify(app_dir, other_dir):
    full_app = load_graph(app_dir / 'full.json')
    full_other = load_graph(other_dir / 'full.json')
    app = load_graph(app_dir / 'actual.json')
    other = load_graph(other_dir / 'actual.json')
    if full_app != full_other:
        raise ValueError('Windows runners reported different full check graphs')
    if app != load_graph(app_dir / 'app.json') or other != load_graph(other_dir / 'other.json'):
        raise ValueError('actual Windows shard graph differs from verified discovery')
    validate(full_app, app, other)
    return len(full_app), len(app), len(other)


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('app', type=Path)
    parser.add_argument('other', type=Path)
    args = parser.parse_args()
    try:
        counts = verify(args.app, args.other)
    except (OSError, ValueError) as error:
        print('Windows shard evidence failed: %s' % error, file=sys.stderr)
        return 1
    print('Windows actual task graph union verified: full=%d app=%d other=%d' % counts)
    return 0


if __name__ == '__main__':
    sys.exit(main())
