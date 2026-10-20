"""Build the Pages artifact from an explicit allowlist; Python standard library only."""
import argparse
from html.parser import HTMLParser
from pathlib import Path
import shutil
from urllib.parse import unquote, urlsplit


PUBLIC_FILES = (
    'index.html', 'style.css', 'script.js', 'workspace-tour.js', 'meadow.css', 'meadow.js',
    'assets/jasper.png', 'assets/demo-cursor.svg', 'assets/tabler-LICENSE.txt',
    'assets/buddy-dimensional-v1.png', 'assets/buddy-presenting-v1.png',
    'assets/buddy-coding-v1.png',
    *(f'assets/{name}.png' for name in ('windows', 'macos', 'linux')),
    *(f'assets/plugins-{name}.png' for name in ('editor', 'terminal', 'focus')),
    *(f'assets/tour-{name}.png' for name in ('vault', 'sessions', 'sftp', 'tunnels', 'sharing')),
    *(f'assets/workspace-typing/{name}.webp' for name in (
        'editor-base', 'terminal-result', 'focus',
        *(f'editor-{i:02}' for i in range(15)),
        *(f'terminal-{i:02}' for i in range(17)),
    )),
)


class PublicLinks(HTMLParser):
    def handle_starttag(self, tag, attrs):
        for key, value in attrs:
            if key not in ('src', 'href') or not value:
                continue
            url = urlsplit(value)
            if url.scheme or url.netloc or not url.path:
                continue
            if unquote(url.path) not in PUBLIC_FILES:
                raise ValueError(f'Homepage references a file outside the public bundle: {value}')


def build(source: Path, output: Path):
    source = source.resolve()
    if output.exists():
        raise FileExistsError(f'Use a new output directory: {output}')
    if output.resolve().is_relative_to(source):
        raise ValueError('Build outside the website source directory')
    for name in PUBLIC_FILES:
        path = source / name
        if path.resolve() != path:
            raise ValueError(f'Symlinks are not public assets: {name}')
        if not path.is_file():
            raise FileNotFoundError(f'Missing public asset: {name}')
    PublicLinks().feed((source / 'index.html').read_text(encoding='utf-8'))
    output.mkdir(parents=True)
    for name in PUBLIC_FILES:
        target = output / name
        target.parent.mkdir(parents=True, exist_ok=True)
        shutil.copyfile(source / name, target)
    return len(PUBLIC_FILES)


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('output', type=Path, help='New directory for the public Pages artifact')
    args = parser.parse_args()
    count = build(Path(__file__).parent, args.output)
    print(f'Packaged {count} public files into {args.output}')
