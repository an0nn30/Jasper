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
RESOURCES = ROOT / 'jasper-app/src/main/resources/dev/jasper/app/icons/app'
SVG = 'http://www.w3.org/2000/svg'
INKSCAPE = 'http://www.inkscape.org/namespaces/inkscape'
ET.register_namespace('', SVG)
ET.register_namespace('inkscape', INKSCAPE)
WINDOWS_SIZES = (16, 20, 24, 30, 32, 36, 40, 48, 60, 64, 72, 80, 96, 128, 256)
MAC_SIZES = (16, 32, 64, 128, 256, 512, 1024)
def platform_svg(platform):
    root = ET.parse(HERE / 'jasper.svg').getroot()
    root.find(f'{{{SVG}}}title').text = f'Jasper Silver Desk Buddy / {platform}'
    root.find(f'{{{SVG}}}desc').text = (
        'Approved Jasper turtle mascot with glasses and natural shell. '
        'Motif silver background #dcdee5. Editable vector shapes.')
    if platform == 'macos':
        # The approved master already has the Mac tile and transparent margin.
        return ET.tostring(root, encoding='unicode')
    background = next(e for e in root if e.get('id') == 'background')
    for rectangle in background.findall(f'{{{SVG}}}rect'):
        rectangle.set('rx', '100')
    # Adapt the tile highlight to the tighter Windows corners.
    background.find(f'{{{SVG}}}path').set('d', 'M132 213 Q132 124 221 124 H786')
    # Replace the 104px side margins with 32px; scale the artwork together.
    scale = 960 / 816
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
        source = HERE / f'jasper-{platform}.svg'
        source.write_text(platform_svg(platform), encoding='utf-8')
        target = RESOURCES / platform
        target.mkdir(parents=True, exist_ok=True)
        for size in sizes:
            render(args.inkscape, source, target / f'icon-{size}.png', size)
        print(f'Rendered {platform}: {sizes}', flush=True)
    write_ico(HERE / 'Jasper.ico', [(s, RESOURCES / f'windows/icon-{s}.png') for s in WINDOWS_SIZES])
    with tempfile.TemporaryDirectory(prefix='jasper-icon-') as temporary:
        iconset = Path(temporary) / 'Jasper.iconset'
        iconset.mkdir()
        for points in (16, 32, 128, 256, 512):
            for scale in (1, 2):
                name = f'icon_{points}x{points}{"@2x" if scale == 2 else ""}.png'
                shutil.copyfile(RESOURCES / f'macos/icon-{points * scale}.png', iconset / name)
        subprocess.run(['iconutil', '-c', 'icns', str(iconset), '-o', str(HERE / 'Jasper.icns')], check=True)
    print('Wrote Jasper.icns and Jasper.ico')


if __name__ == '__main__':
    main()
