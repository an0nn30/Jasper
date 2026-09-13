#!/usr/bin/env python3
"""Regenerate committed native and Swing icons on macOS with Inkscape + iconutil.

Ordinary Gradle builds consume the committed outputs; no Python, Inkscape, or
Apple tooling is needed on Windows to build the application.
"""
import argparse
from pathlib import Path
import shutil
import struct
import subprocess
import tempfile
import xml.etree.ElementTree as ET

HERE = Path(__file__).resolve().parent
ROOT = HERE.parent.parent
RESOURCES = ROOT / 'moray-app/src/main/resources/dev/moray/app/icons/app'
SVG = 'http://www.w3.org/2000/svg'
INKSCAPE = 'http://www.inkscape.org/namespaces/inkscape'
ET.register_namespace('', SVG)
ET.register_namespace('inkscape', INKSCAPE)
WINDOWS_SIZES = (16, 20, 24, 30, 32, 36, 40, 48, 60, 64, 72, 80, 96, 128, 256)
MAC_SIZES = (16, 32, 64, 128, 256, 512, 1024)
MAC_OUTLINE = ('M288 64H736C893 64 960 131 960 288V736'
               'C960 893 893 960 736 960H288C131 960 64 893 64 736'
               'V288C64 131 131 64 288 64Z')


def platform_svg(platform):
    root = ET.parse(HERE / 'moray.svg').getroot()
    root.find(f'{{{SVG}}}title').text = f'Moray Eclipse / {platform}'
    root.find(f'{{{SVG}}}desc').text = (
        'Approved brighter Eclipse artwork. Native platform framing; '
        'eel, prompt, purple palette and rim lighting preserved.')
    if platform == 'macos':
        case = next(e for e in root if e.get('id') == '01-case')
        for path in case:
            path.set('d', MAC_OUTLINE)
    # One transform replaces the design canvas margin, rather than adding a second one.
    scale = (824 if platform == 'macos' else 960) / 896
    frame = ET.Element(f'{{{SVG}}}g', {'id': 'platform-frame',
        'transform': f'translate(512 512) scale({scale:.12f}) translate(-512 -512)'})
    for child in list(root):
        if child.tag == f'{{{SVG}}}g':
            root.remove(child)
            frame.append(child)
    root.append(frame)
    return ET.tostring(root, encoding='unicode')


def render(inkscape, source, destination, size):
    subprocess.run([inkscape, str(source), '--export-area-page',
                    f'--export-filename={destination}', f'--export-width={size}',
                    '--export-background-opacity=0'], check=True, capture_output=True)


def write_ico(destination, images):
    # ICO directory + lossless PNG frames (supported by modern Windows/jpackage).
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
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--inkscape', default=shutil.which('inkscape') or
                        '/Applications/Inkscape.app/Contents/MacOS/inkscape')
    args = parser.parse_args()
    if not Path(args.inkscape).is_file() or not shutil.which('iconutil'):
        parser.error('Regeneration requires Inkscape and macOS iconutil')
    for platform, sizes in [('macos', MAC_SIZES), ('windows', WINDOWS_SIZES)]:
        source = HERE / f'moray-{platform}.svg'
        source.write_text(platform_svg(platform), encoding='utf-8')
        target = RESOURCES / platform
        target.mkdir(parents=True, exist_ok=True)
        for size in sizes:
            render(args.inkscape, source, target / f'icon-{size}.png', size)
        print(f'Rendered {platform}: {sizes}', flush=True)
    write_ico(HERE / 'Moray.ico', [(s, RESOURCES / f'windows/icon-{s}.png') for s in WINDOWS_SIZES])
    with tempfile.TemporaryDirectory(prefix='moray-icon-') as temporary:
        iconset = Path(temporary) / 'Moray.iconset'
        iconset.mkdir()
        for points in (16, 32, 128, 256, 512):
            for scale in (1, 2):
                name = f'icon_{points}x{points}{"@2x" if scale == 2 else ""}.png'
                shutil.copyfile(RESOURCES / f'macos/icon-{points * scale}.png', iconset / name)
        subprocess.run(['iconutil', '-c', 'icns', str(iconset), '-o', str(HERE / 'Moray.icns')], check=True)
    print('Wrote Moray.icns and Moray.ico')


if __name__ == '__main__':
    main()
