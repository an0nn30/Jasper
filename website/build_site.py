"""Build the Pages artifact from an explicit allowlist; Python standard library only."""
import argparse
from html.parser import HTMLParser
from pathlib import Path
import posixpath
import re
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


# The plugin SDK reference, generated into docs/ by ./gradlew sdkReference, is published at sdk/. Its links to the
# repository's Markdown guides are relative to docs/; on the site they lead to the guides on GitHub instead.
SDK_REFERENCE = 'sdk/index.html'
PUBLIC_PAGES = {'sdk/', SDK_REFERENCE}
REPOSITORY_FILES = 'https://github.com/an0nn30/Jasper/blob/main/'
ATTRIBUTE = re.compile(r'(\b(?:href|src)=")([^"]*)(")')


def published_reference(reference: Path):
    """The SDK reference with every repository-relative link turned into its GitHub address."""
    def rewrite(match):
        value = match.group(2)
        url = urlsplit(value)
        if not value or value.startswith('#') or url.scheme or url.netloc:
            return match.group(0)
        path = posixpath.normpath(posixpath.join('docs', unquote(url.path)))
        if path.startswith('..'):
            raise ValueError(f'The SDK reference links outside the repository: {value}')
        return match.group(1) + REPOSITORY_FILES + path + (f'#{url.fragment}' if url.fragment else '') + match.group(3)
    return ATTRIBUTE.sub(rewrite, reference.read_text(encoding='utf-8'))


class PublicLinks(HTMLParser):
    def handle_starttag(self, tag, attrs):
        for key, value in attrs:
            if key not in ('src', 'href') or not value:
                continue
            url = urlsplit(value)
            if url.scheme or url.netloc or not url.path:
                continue
            if unquote(url.path) not in PUBLIC_FILES and unquote(url.path) not in PUBLIC_PAGES:
                raise ValueError(f'Homepage references a file outside the public bundle: {value}')


def build(source: Path, output: Path, reference: Path = None):
    source = source.resolve()
    reference = (reference or source.parent / 'docs' / 'sdk-reference.html').resolve()
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
    if not reference.is_file():
        raise FileNotFoundError(f'Missing the SDK reference (run ./gradlew sdkReference): {reference}')
    PublicLinks().feed((source / 'index.html').read_text(encoding='utf-8'))
    sdk_page = published_reference(reference)
    output.mkdir(parents=True)
    for name in PUBLIC_FILES:
        target = output / name
        target.parent.mkdir(parents=True, exist_ok=True)
        shutil.copyfile(source / name, target)
    (output / SDK_REFERENCE).parent.mkdir(parents=True, exist_ok=True)
    (output / SDK_REFERENCE).write_text(sdk_page, encoding='utf-8')
    return len(PUBLIC_FILES) + 1


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('output', type=Path, help='New directory for the public Pages artifact')
    args = parser.parse_args()
    count = build(Path(__file__).parent, args.output)
    print(f'Packaged {count} public files into {args.output}')
