#!/usr/bin/env python3
"""Pin Gradle's Maven cache inventory and stage Portage distfiles as a local repository."""
import argparse
import hashlib
import json
from pathlib import Path, PurePosixPath
import re
import shutil
import sys

CENTRAL = 'https://repo.maven.apache.org/maven2/'
JETBRAINS = 'https://packages.jetbrains.team/maven/p/ij/intellij-dependencies/'


def safe_segment(value):
    if not re.fullmatch(r'[A-Za-z0-9_][A-Za-z0-9_.+-]*', value) or '..' in value:
        raise ValueError(f'unsupported path component: {value!r}')
    return value


def artifact(row):
    parts = row['path'].split('/')
    if len(parts) != 5:
        raise ValueError(f'unsupported cache path: {row["path"]}')
    group, name, version, cachehash, filename = map(safe_segment, parts)
    if not re.fullmatch(re.escape(name + '-' + version) + r'(?:-[A-Za-z0-9_.+-]+)?\.(?:jar|pom|module)', filename):
        raise ValueError(f'unsupported Maven filename: {filename}')
    digest = row['sha256']
    if not re.fullmatch('[0-9a-f]{64}', digest) or type(row['size']) is not int or row['size'] <= 0:
        raise ValueError('invalid artifact hash or size')
    path = '/'.join([*group.split('.'), name, version, filename])
    distfile = 'jasper-maven-' + hashlib.sha256(path.encode()).hexdigest() + '-' + filename
    return {'path': path, 'distfile': distfile, 'url': (JETBRAINS if group == 'org.jetbrains.jediterm' else CENTRAL) + path,
            'sha256': digest, 'size': row['size']}


def make_lock(rows):
    found = {}
    for row in rows:
        # Gradle's IDE tooling stores its distribution sources alongside Maven files.
        if re.fullmatch(r'gradle/gradle/([0-9.]+)/[^/]+/gradle-\1-src\.zip', row['path']):
            continue
        value = artifact(row)
        previous = found.setdefault(value['path'], value)
        if previous != value:
            raise ValueError(f'conflicting artifact: {value["path"]}')
    return {'format': 1, 'artifacts': [found[k] for k in sorted(found)]}


def read_lock(path):
    lock = json.loads(path.read_text())
    if lock['format'] != 1:
        raise ValueError('unsupported lock format')
    rows = lock['artifacts']
    for row in rows:
        path = PurePosixPath(row['path'])
        parts = row['path'].split('/')
        if path.is_absolute() or len(parts) < 4 or any(safe_segment(p) != p for p in parts):
            raise ValueError('unsafe Maven path')
        canonical = artifact({**row, 'path': '/'.join(['.'.join(parts[:-3]), *parts[-3:-1], 'cache', parts[-1]])})
        if canonical != row:
            raise ValueError('inconsistent artifact lock')
    if len({r['path'] for r in rows}) != len(rows) or len({r['distfile'] for r in rows}) != len(rows):
        raise ValueError('duplicate artifacts')
    return rows


def stage(lock, distdir, destination):
    rows = read_lock(lock)
    if destination.exists():
        raise ValueError('Maven destination must be new')
    # Verify every input before writing the repository.
    for row in rows:
        source = distdir / row['distfile']
        if source.stat().st_size != row['size'] or hashlib.sha256(source.read_bytes()).hexdigest() != row['sha256']:
            raise ValueError(f'artifact checksum mismatch: {source.name}')
    for row in rows:
        target = destination / row['path']
        target.parent.mkdir(parents=True, exist_ok=True)
        shutil.copyfile(distdir / row['distfile'], target)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    commands = parser.add_subparsers(dest='command', required=True)
    inventory = commands.add_parser('inventory')
    inventory.add_argument('cache', type=Path)
    inventory.add_argument('output', type=Path)
    lock = commands.add_parser('lock')
    lock.add_argument('inventory', type=Path)
    lock.add_argument('output', type=Path)
    uris = commands.add_parser('uris')
    uris.add_argument('lock', type=Path)
    staging = commands.add_parser('stage')
    staging.add_argument('lock', type=Path)
    staging.add_argument('distdir', type=Path)
    staging.add_argument('destination', type=Path)
    args = parser.parse_args()
    if args.command == 'inventory':
        rows = [{'path': p.relative_to(args.cache).as_posix(), 'size': p.stat().st_size,
                 'sha256': hashlib.sha256(p.read_bytes()).hexdigest()}
                for p in sorted(args.cache.rglob('*')) if p.is_file()]
        args.output.write_text(json.dumps(rows, indent=2) + '\n')
    elif args.command == 'lock':
        args.output.write_text(json.dumps(make_lock(json.loads(args.inventory.read_text())), indent=2) + '\n')
    elif args.command == 'uris':
        print('\n'.join(f'{row["url"]} -> {row["distfile"]}' for row in read_lock(args.lock)))
    else:
        stage(args.lock, args.distdir, args.destination)


if __name__ == '__main__':
    try:
        main()
    except (ValueError, KeyError, OSError) as error:
        sys.exit(str(error))
