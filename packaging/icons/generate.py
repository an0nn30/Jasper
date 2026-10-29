#!/usr/bin/env python3
"""Import supplied Protean assets. Requires Pillow and Xcode actool on macOS."""
from pathlib import Path
import json
import shutil
import subprocess
import tempfile
from PIL import Image

HERE = Path(__file__).resolve().parent
ROOT = HERE.parent.parent
SOURCE = HERE / 'protean-source'
RESOURCES = ROOT / 'jasper-app/src/main/resources/dev/jasper/app/icons/app'
RUNTIME = {
    'macos': (16, 32, 64, 128, 256, 512, 1024),
    'windows': (16, 20, 24, 30, 32, 36, 40, 48, 60, 64, 72, 80, 96, 128, 256),
    'linux': (16, 24, 32, 48, 64, 96, 128, 256, 512, 1024),
}


def copy(source, target):
    target.parent.mkdir(parents=True, exist_ok=True)
    shutil.copyfile(source, target)


def main():
    # Compile the static catalog first; no icon service or application is launched.
    with tempfile.TemporaryDirectory() as temporary:
        work = Path(temporary)
        catalog = work / 'Jasper.xcassets'
        appicon = catalog / 'Jasper.appiconset'
        appicon.mkdir(parents=True)
        entries = []
        for points in (16, 32, 128, 256, 512):
            for scale in (1, 2):
                source = SOURCE / 'macOS/Protean.iconset' / (f'icon_{points}x{points}' + ('-2x' if scale == 2 else '') + '.png')
                copy(source, appicon / source.name)
                entries.append(dict(idiom='mac', size=f'{points}x{points}', scale=f'{scale}x', filename=source.name))
        (appicon / 'Contents.json').write_text(json.dumps(dict(images=entries, info=dict(version=1, author='xcode'))))
        subprocess.run(['xcrun', 'actool', str(catalog), '--compile', str(work), '--platform', 'macosx',
                        '--minimum-deployment-target', '11.0', '--app-icon', 'Jasper',
                        '--output-partial-info-plist', str(work / 'Info.plist')], check=True)
        copy(work / 'Assets.car', HERE / 'macos/Assets.car')
    copy(SOURCE / 'macOS/Protean.icns', HERE / 'Jasper.icns')
    copy(SOURCE / 'Windows/Protean.ico', HERE / 'Jasper.ico')
    copy(SOURCE / 'Protean.svg', HERE / 'Jasper.svg')
    copy(SOURCE / 'Protean.svg', HERE / 'macos/Jasper.svg')
    copy(SOURCE / 'Linux/hicolor/scalable/apps/protean.svg', HERE / 'linux/hicolor/scalable/apps/jasper.svg')
    # Keep every existing hicolor size; use source pixels whenever a matching size exists.
    linux_sizes = {int(p.parent.parent.name.split('x')[0]) for p in (HERE / 'linux/hicolor').glob('*x*/apps/jasper.png')}
    for platform, sizes in RUNTIME.items():
        existing = {int(p.stem.split('-')[1]) for p in (RESOURCES / platform).glob('icon-*.png')}
        for size in sorted(set(sizes) | existing | (linux_sizes if platform == 'linux' else set())):
            if platform == 'macos':
                points, scale = (size, '') if size <= 512 and size != 64 else (size // 2, '-2x')
                source = SOURCE / f'macOS/Protean.iconset/icon_{points}x{points}{scale}.png'
            elif platform == 'windows':
                source = SOURCE / f'Windows/PNG/Protean-{size}.png'
            else:
                source = SOURCE / f'Linux/hicolor/{size}x{size}/apps/protean.png'
                if not source.exists():
                    source = SOURCE / f'PNG/Protean-{size}.png'
            target = (HERE / f'linux/hicolor/{size}x{size}/apps/jasper.png'
                      if platform == 'linux' and size not in sizes else RESOURCES / platform / f'icon-{size}.png')
            if source.exists():
                copy(source, target)
            else:
                master = SOURCE / ('Windows/PNG/Protean-256.png' if platform == 'windows' else 'PNG/Protean-1024.png')
                target.parent.mkdir(parents=True, exist_ok=True)
                with Image.open(master) as image:
                    image.resize((size, size), Image.Resampling.LANCZOS).save(target)
            if platform == 'linux' and size in sizes:
                copy(target, HERE / f'linux/hicolor/{size}x{size}/apps/jasper.png')
    copy(RESOURCES / 'linux/icon-512.png', HERE / 'Jasper.png')
    copy(RESOURCES / 'linux/icon-256.png', ROOT / 'website/assets/jasper.png')


if __name__ == '__main__':
    main()
