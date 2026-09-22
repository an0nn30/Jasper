#!/usr/bin/env python3
"""Regenerate the approved Ember icons with macOS Core Graphics and iconutil.

Normal Gradle builds consume committed outputs and need no authoring tools.
The source is the approved generated PNG, not an editable SVG.
"""
from pathlib import Path
import shutil
import struct
import subprocess
import tempfile

HERE = Path(__file__).resolve().parent
ROOT = HERE.parent.parent
RESOURCES = ROOT / 'jasper-app/src/main/resources/dev/jasper/app/icons/app'
WINDOWS_SIZES = (16, 20, 24, 30, 32, 36, 40, 48, 60, 64, 72, 80, 96, 128, 256)
MAC_SIZES = (16, 32, 64, 128, 256, 512, 1024)
LINUX_SIZES = (16, 24, 32, 48, 64, 96, 128, 256, 512, 1024)


def write_ico(destination, images):
    # Lossless PNG frames in the standard ICO directory, supported by jpackage.
    offset = 6 + 16 * len(images)
    entries, payloads = [], []
    for size, path in images:
        png = path.read_bytes()
        entries.append(struct.pack('<BBBBHHII', size % 256, size % 256, 0, 0,
                                   1, 32, len(png), offset))
        payloads.append(png)
        offset += len(png)
    destination.write_bytes(struct.pack('<HHH', 0, 1, len(images))
                            + b''.join(entries) + b''.join(payloads))


def main():
    if not shutil.which('xcrun') or not shutil.which('iconutil'):
        raise SystemExit('Regeneration requires macOS with Swift/Xcode Command Line Tools and iconutil')
    with tempfile.TemporaryDirectory(prefix='jasper-ember-') as temporary:
        work = Path(temporary)
        renderer = work / 'render-icons'
        subprocess.run(['xcrun', 'swiftc', '-O', str(HERE / 'render.swift'), '-o', str(renderer)], check=True)
        subprocess.run([str(renderer), str(HERE / 'jasper-ember-source.png'), str(work)], check=True)
        for platform, sizes in [('macos', MAC_SIZES), ('windows', WINDOWS_SIZES), ('linux', LINUX_SIZES)]:
            target = RESOURCES / platform
            target.mkdir(parents=True, exist_ok=True)
            for size in sizes:
                shutil.copyfile(work / platform / f'icon-{size}.png', target / f'icon-{size}.png')
            print(f'Wrote {platform}: {sizes}', flush=True)
        iconset = work / 'Jasper.iconset'
        iconset.mkdir()
        for points in (16, 32, 128, 256, 512):
            for scale in (1, 2):
                name = f'icon_{points}x{points}{"@2x" if scale == 2 else ""}.png'
                shutil.copyfile(RESOURCES / f'macos/icon-{points * scale}.png', iconset / name)
        subprocess.run(['iconutil', '-c', 'icns', str(iconset), '-o', str(HERE / 'Jasper.icns')], check=True)
    write_ico(HERE / 'Jasper.ico', [(s, RESOURCES / f'windows/icon-{s}.png') for s in WINDOWS_SIZES])
    shutil.copyfile(RESOURCES / 'linux/icon-512.png', HERE / 'Jasper.png')
    for size in LINUX_SIZES:
        directory = HERE / f'linux/hicolor/{size}x{size}/apps'
        directory.mkdir(parents=True, exist_ok=True)
        shutil.copyfile(RESOURCES / f'linux/icon-{size}.png', directory / 'jasper.png')
    print('Wrote ICNS, ICO, Linux package PNG, and hicolor assets')


if __name__ == '__main__':
    main()
