"""Explicit maintainer refresh: signed metadata -> reviewed, exact offline snapshot.

Requires Python 3 and GnuPG only for refresh. Build/install uses the JDK verifier.
Never executes package scripts or extracts archives while resolving a snapshot.
"""
import argparse
import concurrent.futures
import hashlib
import json
import lzma
import pathlib
import re
import shutil
import subprocess
import tempfile
import urllib.parse
import urllib.request

KEY = '56405CF6FCC81574682A5D561A698DE9E2E56300'
STARTER = 'bash coreutils findutils grep sed gawk diffutils less tar gzip bzip2 xz unzip git openssh curl ca-certificates'.split()

def version(value):
    if not re.fullmatch(r'[0-9]+(?:[.-][0-9]+)*', value):
        raise ValueError('Version constraint needs maintainer support: ' + value)
    return tuple(int(n) for n in re.split('[.-]', value))

def resolve(text, requested=STARTER):
    packages = {}
    for block in text.split('\n@ ')[1:]:
        lines = block.splitlines(); fields = {}
        for line in lines[1:]:
            if line.startswith('['):
                if 'version' in fields: break
                # Setup selects the sole available version even when it is tagged [test].
                continue
            if re.match(r'^[\w-]+: ', line):
                key, value = line.split(': ', 1); fields[key] = value
        packages[lines[0]] = fields
    providers = {}
    for name, fields in packages.items():
        if 'install' not in fields: continue
        for cap in fields.get('provides', '').split(','):
            cap = cap.strip()
            if cap: providers.setdefault(cap.split()[0], []).append(name)
    selected = {n for n, p in packages.items() if 'install' in p and 'Base' in p.get('category', '').split()} | set(requested)
    pending = list(selected)
    while pending:
        name = pending.pop()
        if name not in packages or 'install' not in packages[name] or 'source' not in packages[name]:
            raise ValueError('Missing binary/source package: ' + name)
        fields = packages[name]; dependencies = set()
        for dep in fields.get('depends2', '').split(','):
            if not dep.strip(): continue
            match = re.fullmatch(r'\s*([\w+.-]+)(?:\s*\(\s*(>=|<=|=|>|<)\s*([^ )]+)\s*\))?\s*', dep)
            if not match: raise ValueError('Unsupported dependency: ' + dep)
            target, operator, constraint = match.groups()
            if target == '_windows':
                current = '10.0'
            else:
                if target not in packages:
                    choices = providers.get(target, [])
                    if len(choices) != 1 or operator: raise ValueError('Ambiguous/versioned virtual dependency: ' + dep)
                    target = choices[0]
                if 'install' not in packages[target]: raise ValueError('Dependency has no binary: ' + target)
                current = packages[target]['version']; dependencies.add(target)
                if target not in selected: selected.add(target); pending.append(target)
            if operator:
                left, right = version(current), version(constraint)
                width = max(len(left), len(right)); left += (0,) * (width-len(left)); right += (0,) * (width-len(right))
                if not {'>=': left >= right, '<=': left <= right, '=': left == right, '>': left > right, '<': left < right}[operator]:
                    raise ValueError('Unsatisfied dependency: ' + dep)
        fields['dependencies'] = sorted(dependencies)
    return {n: packages[n] for n in sorted(selected)}

def safe_path(name):
    if not name or '\\' in name or ':' in name or any(p in ('', '.', '..') for p in name.split('/')):
        raise ValueError('Unsafe artifact path: ' + name)
    return name

def fetch(url, target, size=None, digest=None):
    if urllib.parse.urlparse(url).scheme != 'https': raise ValueError('HTTPS origin required')
    target.parent.mkdir(parents=True, exist_ok=True)
    if not target.exists():
        part = target.with_name(target.name + '.part')
        with urllib.request.urlopen(url, timeout=90) as response, part.open('wb') as output:
            if urllib.parse.urlparse(response.url).scheme != 'https': raise ValueError('Insecure redirect')
            shutil.copyfileobj(response, output)
        part.replace(target)
    data = target.read_bytes()
    if size is not None and len(data) != size or digest is not None and hashlib.sha512(data).hexdigest() != digest:
        raise ValueError('Artifact verification failed: ' + str(target))
    return {'origin': url, 'bytes': len(data), 'sha512': hashlib.sha512(data).hexdigest()}

def verify_signature(keyring, signature, data):
    with tempfile.TemporaryDirectory(prefix='jasper-cygwin-gpg-') as home:
        subprocess.run(['gpg', '--homedir', home, '--batch', '--import', str(keyring)], check=True, capture_output=True)
        result = subprocess.run(['gpg', '--homedir', home, '--batch', '--status-fd=1', '--verify', str(signature), str(data)], check=True, capture_output=True, text=True)
        if '[GNUPG:] VALIDSIG ' + KEY + ' ' not in result.stdout: raise ValueError('Unexpected signing key')

def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--mirror', required=True)
    parser.add_argument('--snapshot', required=True, type=pathlib.Path)
    parser.add_argument('--metadata', required=True, type=pathlib.Path, help='Retained setup.xz, setup.xz.sig and pubring.asc')
    parser.add_argument('--licenses', required=True, type=pathlib.Path, help='Reviewed per-package license inventory')
    parser.add_argument('--relays', required=True, type=pathlib.Path, help='Verified prebuilt relay directory and provenance.json')
    parser.add_argument('--installed-bytes', required=True, type=int, help='Measured size of this exact snapshot')
    args = parser.parse_args()
    if not args.snapshot.is_absolute() or args.installed_bytes <= 0: parser.error('Absolute snapshot and positive measured size required')
    if (args.snapshot/'seed.lock.json').exists(): parser.error('Snapshot is already locked; choose a new destination')
    verify_signature(args.metadata/'pubring.asc', args.metadata/'setup.xz.sig', args.metadata/'setup.xz')
    packages = resolve(lzma.decompress((args.metadata/'setup.xz').read_bytes()).decode())
    licenses = json.loads(args.licenses.read_text())
    if set(licenses) != set(packages): raise ValueError('License inventory must match complete package closure')
    mirror = args.mirror.rstrip('/') + '/'
    cache = 'cache/' + urllib.parse.quote(mirror, safe='').lower() + '/'
    artifacts = {}
    def retain(path, origin, source):
        safe_path(path); dest = args.snapshot/path; dest.parent.mkdir(parents=True, exist_ok=True); shutil.copyfile(source, dest)
        artifacts[path] = {'path': path, **fetch(origin, dest)}
        return path
    for name in ('setup.xz', 'setup.xz.sig'):
        retain(cache+'x86_64/'+name, mirror+'x86_64/'+name, args.metadata/name)
    retain('pubring.asc', 'https://cygwin.com/key/pubring.asc', args.metadata/'pubring.asc')
    # Pin the Setup release validated by Task 1; no latest-version lookup.
    for name, origin in [('setup-x86_64.exe', 'https://cygwin.com/setup/setup-2.937.x86_64.exe'),
                         ('setup-x86_64.exe.sig', 'https://cygwin.com/setup/setup-2.937.x86_64.exe.sig')]:
        artifacts[name] = {'path': name, **fetch(origin, args.snapshot/name)}
    verify_signature(args.snapshot/'pubring.asc', args.snapshot/'setup-x86_64.exe.sig', args.snapshot/'setup-x86_64.exe')
    wanted = {}
    for package in packages.values():
        for key in ('install', 'source'):
            name, size, digest = package[key].split(); safe_path(name)
            previous = wanted.setdefault(name, (int(size), digest))
            if previous != (int(size), digest): raise ValueError('Conflicting metadata artifact')
    def download(item):
        name, (size, digest) = item; path = cache+name
        return path, {'path': path, **fetch(mirror+name, args.snapshot/path, size, digest)}
    with concurrent.futures.ThreadPoolExecutor(max_workers=6) as pool:
        artifacts.update(pool.map(download, wanted.items()))
    locked_packages = []
    for name, package in packages.items():
        license = licenses[name]
        if not license.get('expression') or not license.get('reviewed') or not license.get('notice'):
            raise ValueError('Unreviewed license: ' + name)
        notice = pathlib.Path(license['notice'])
        if not notice.is_absolute(): notice = args.licenses.parent/notice
        notice_path = retain('notices/'+name+'.txt', license['origin'], notice)
        locked_packages.append({'name': name, 'version': package['version'], 'binary': cache+package['install'].split()[0],
            'source': cache+package['source'].split()[0], 'license': notice_path, 'dependencies': package['dependencies']})
    provenance = json.loads((args.relays/'provenance.json').read_text())
    relays = {}
    for arch in ('aarch64', 'amd64', 'backend'):
        entry = provenance[arch]; path = 'relay/'+entry['file']; source = args.relays/entry['file']
        if hashlib.sha512(source.read_bytes()).hexdigest() != entry['sha512']: raise ValueError('Relay provenance mismatch')
        relays[arch] = retain(path, entry['origin'], source)
    for entry in provenance['sources']:
        source = args.relays/entry.get('source', entry['file'])
        if hashlib.sha512(source.read_bytes()).hexdigest() != entry['sha512']: raise ValueError('Relay source provenance mismatch')
        retain('relay/'+entry['file'], entry['origin'], source)
    manifest = {'schema': 1, 'architecture': 'x86_64', 'installedBytes': args.installed_bytes, 'setup': 'setup-x86_64.exe',
        'metadata': cache+'x86_64/setup.xz', 'signature': cache+'x86_64/setup.xz.sig',
        'artifacts': sorted(artifacts.values(), key=lambda a:a['path']), 'packages': locked_packages, 'relays': relays}
    (args.snapshot/'seed.lock.json').write_text(json.dumps(manifest, indent=2)+'\n')
    print('Candidate locked:', args.snapshot, 'packages:', len(packages), 'artifacts:', len(artifacts))

if __name__ == '__main__': main()
