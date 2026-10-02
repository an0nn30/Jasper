#!/usr/bin/env python3
"""Explicit maintainer-only acquisition of locked theme assets. Never invoked by Gradle or runtime.

Reads sources.json, downloads exact pinned bytes and verifies SHA-256 before publishing.
Use --source ID to fetch one family. Existing files with the expected digest are retained.
"""
import argparse, concurrent.futures, hashlib, json, pathlib, urllib.parse, urllib.request

ROOT = pathlib.Path(__file__).resolve().parents[2]
def safe(root, value):
    path = pathlib.PurePosixPath(value)
    if path.is_absolute() or '..' in path.parts or '\\' in value or not value:
        raise ValueError('Unsafe asset path: ' + value)
    result = root.joinpath(*path.parts)
    if any(p.is_symlink() for p in [result, *result.parents] if p != root.parent):
        raise ValueError('Symbolic link in asset path: ' + value)
    return result

def fetch(source, asset, dest):
    path = safe(dest / source['id'], asset['path'])
    expected = asset['sha256']
    if path.is_file() and hashlib.sha256(path.read_bytes()).hexdigest() == expected:
        return
    url = asset.get('url') or ('https://raw.githubusercontent.com/' + source['repository'].removeprefix('https://github.com/')
          + '/' + source['commit'] + '/' + urllib.parse.quote(asset['upstream']))
    if asset.get('local', False):
        data = safe(ROOT, asset['localSource']).read_bytes()
    else:
        with urllib.request.urlopen(urllib.request.Request(url, headers={'User-Agent':'Jasper-theme-maintainer'}), timeout=60) as response:
            data = response.read(64 * 1024 * 1024 + 1)
    if len(data) > 64 * 1024 * 1024 or hashlib.sha256(data).hexdigest() != expected:
        raise ValueError('Asset digest mismatch: ' + source['id'] + '/' + asset['path'])
    path.parent.mkdir(parents=True, exist_ok=True)
    pending = path.with_name(path.name + '.download')
    pending.write_bytes(data)
    pending.replace(path)

def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--manifest', type=pathlib.Path, default=ROOT/'tools/themes/sources.json')
    parser.add_argument('--sources', type=pathlib.Path, default=ROOT/'third_party/themes')
    parser.add_argument('--source')
    args = parser.parse_args()
    manifest = json.loads(args.manifest.read_text())
    if manifest['version'] != 1: raise ValueError('Unsupported manifest version')
    jobs = [(source, asset) for source in manifest['sources'] if not args.source or source['id'] == args.source
            for asset in source['assets']]
    with concurrent.futures.ThreadPoolExecutor(max_workers=6) as pool:
        list(pool.map(lambda job: fetch(*job, args.sources), jobs))
    print('Verified', len(jobs), 'locked assets')
if __name__ == '__main__': main()
