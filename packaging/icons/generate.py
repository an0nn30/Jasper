#!/usr/bin/env python3
"""Export the approved Steep SVG to native packages, runtime PNGs and a shareable set.

Requires Inkscape, macOS iconutil and Xcode (actool/Swift). Normal builds use committed assets.
Windows/Linux render directly from SVG; macOS uses the native icon renderer.
"""
from pathlib import Path
import hashlib
import json
import plistlib
import uuid
import shutil
import struct
import subprocess
import tempfile
import xml.etree.ElementTree as ET
import zipfile

HERE = Path(__file__).resolve().parent
ROOT = HERE.parent.parent
RESOURCES = ROOT / 'jasper-app/src/main/resources/dev/jasper/app/icons/app'
EXPORTS = ROOT / 'build/jasper-icon-set'
ARCHIVE = ROOT / 'build/Jasper-icon-set.zip'
SVG = 'http://www.w3.org/2000/svg'
ET.register_namespace('', SVG)
ET.register_namespace('inkscape', 'http://www.inkscape.org/namespaces/inkscape')

# Keep runtime sizes aligned with ApplicationIcon.java. The standalone pack adds
# intermediate desktop/DPI sizes without making every window load them all.
RUNTIME = {
    'macos': (16, 32, 64, 128, 256, 512, 1024),
    'windows': (16, 20, 24, 30, 32, 36, 40, 48, 60, 64, 72, 80, 96, 128, 256),
    'linux': (16, 24, 32, 48, 64, 96, 128, 256, 512, 1024),
}
ICO_SIZES = (16, 20, 24, 28, 30, 32, 36, 40, 44, 48, 56, 60, 64, 72, 80,
             96, 128, 160, 192, 256)
SIZES = {
    'macos': RUNTIME['macos'],
    'windows': ICO_SIZES + (384, 512, 768, 1024),
    'linux': (16, 20, 22, 24, 32, 36, 40, 48, 64, 72, 96, 128, 192, 256,
              384, 512, 768, 1024),
}
# Preserve existing platform optical footprints; apply padding exactly once.
COVERAGE = {'macos': 1.0, 'windows': 960 / 1024, 'linux': .90}


def run(*args):
    return subprocess.run(args, check=True, capture_output=True, text=True).stdout.strip()


def png_size(path):
    data = path.read_bytes()
    if data[:8] != b'\x89PNG\r\n\x1a\n':
        raise ValueError(f'Not a PNG: {path}')
    return struct.unpack('>II', data[16:24])


def write_ico(destination, images):
    offset = 6 + 16 * len(images)
    entries, payloads = [], []
    for size, path in images:
        if not 1 <= size <= 256 or png_size(path) != (size, size):
            raise ValueError(f'Invalid ICO representation: {path}')
        png = path.read_bytes()
        entries.append(struct.pack('<BBBBHHII', size % 256, size % 256, 0, 0,
                                   1, 32, len(png), offset))
        payloads.append(png)
        offset += len(png)
    destination.write_bytes(struct.pack('<HHH', 0, 1, len(images))
                            + b''.join(entries) + b''.join(payloads))


def framed_svg(master, bounds, coverage, destination):
    root = ET.parse(master).getroot()
    artwork = ET.Element(f'{{{SVG}}}g', {'id': 'jasper-artwork'})
    for child in list(root):
        if child.tag not in {f'{{{SVG}}}defs', f'{{{SVG}}}title', f'{{{SVG}}}desc'}:
            root.remove(child)
            artwork.append(child)
    x, y, width, height = bounds
    scale = 1024 * coverage / max(width, height)
    artwork.set('transform', f'translate(512 512) scale({scale:.12f}) '
                             f'translate({-x-width/2:.12f} {-y-height/2:.12f})')
    root.append(artwork)
    root.set('width', '1024')
    root.set('height', '1024')
    root.set('viewBox', '0 0 1024 1024')
    ET.ElementTree(root).write(destination, encoding='utf-8', xml_declaration=True)


def normalize_small_icns_alpha(path):
    """Correct iconutil's straight RGB in the two small native ARGB chunks.

    Native decoding expects premultiplied channels. Values above alpha prove
    the encoder used straight RGB; leave already-associated output untouched.
    Retina/larger PNG chunks are copied without modification.
    """
    data = path.read_bytes()
    chunks, offset = [], 8
    while offset < len(data):
        kind, length = struct.unpack_from('>4sI', data, offset)
        payload = data[offset + 8:offset + length]
        if kind in (b'ic04', b'ic05') and payload.startswith(b'ARGB'):
            pixels = (16 if kind == b'ic04' else 32) ** 2
            planes, cursor = bytearray(), 4
            while cursor < len(payload):
                tag = payload[cursor]
                cursor += 1
                if tag < 128:
                    planes.extend(payload[cursor:cursor + tag + 1])
                    cursor += tag + 1
                else:
                    planes.extend(bytes([payload[cursor]]) * (tag - 125))
                    cursor += 1
            if len(planes) != pixels * 4:
                raise ValueError(f'Unexpected {kind!r} ARGB payload')
            if any(planes[channel * pixels + i] > planes[i]
                   for channel in (1, 2, 3) for i in range(pixels)):
                for channel in (1, 2, 3):
                    for i in range(pixels):
                        at = channel * pixels + i
                        planes[at] = (planes[at] * planes[i] + 127) // 255
                # Literal ICNS RLE packets avoid another codec dependency.
                packets = [planes[i:i + 128] for i in range(0, len(planes), 128)]
                payload = b'ARGB' + b''.join(bytes([len(p) - 1]) + p for p in packets)
        chunks.append(kind + struct.pack('>I', len(payload) + 8) + payload)
        offset += length
    body = b''.join(chunks)
    path.write_bytes(b'icns' + struct.pack('>I', len(body) + 8) + body)


def render_macos(pack, temporary):
    """Compile the approved full-canvas tab artwork and render all native Mac sizes."""
    source = pack / 'macos/Jasper.icon'
    (source / 'Assets').mkdir(parents=True)
    shutil.copyfile(HERE / 'macos/icon.json', source / 'icon.json')
    shutil.copyfile(pack / 'macos/png/icon-1024.png', source / 'Assets/Jasper.png')
    compiled = Path(temporary) / 'compiled'
    compiled.mkdir()
    run('/usr/bin/xcrun', 'actool', str(source), '--compile', str(compiled),
        '--platform', 'macosx', '--minimum-deployment-target', '11.0',
        '--app-icon', 'Jasper', '--output-partial-info-plist', str(compiled / 'Info.plist'))
    shutil.copyfile(compiled / 'Assets.car', pack / 'macos/Assets.car')
    # A unique, temporary bundle avoids stale icon-service cache entries.
    app = Path(temporary) / 'Preview.app'
    resources = app / 'Contents/Resources'
    resources.mkdir(parents=True)
    executable = app / 'Contents/MacOS/Preview'
    executable.parent.mkdir()
    # A valid executable prevents the icon service from drawing a prohibited
    # badge on an incomplete bundle. It is only inspected, never launched.
    shutil.copyfile('/usr/bin/true', executable)
    executable.chmod(0o755)
    shutil.copyfile(compiled / 'Assets.car', resources / 'Assets.car')
    shutil.copyfile(compiled / 'Jasper.icns', resources / 'Jasper.icns')
    (app / 'Contents/Info.plist').write_bytes(plistlib.dumps({
        'CFBundleIdentifier': 'dev.jasper.iconpreview.' + uuid.uuid4().hex,
        'CFBundleExecutable': 'Preview',
        'CFBundlePackageType': 'APPL', 'CFBundleIconName': 'Jasper',
        'CFBundleIconFile': 'Jasper.icns'}))
    renderer = Path(temporary) / 'render-macos'
    run('/usr/bin/xcrun', 'swiftc', str(HERE / 'render-macos.swift'), '-o', str(renderer))
    run(str(renderer), str(app), str(pack / 'macos/png'), *map(str, SIZES['macos']))


def main():
    inkscape, iconutil = shutil.which('inkscape'), shutil.which('iconutil')
    if not inkscape or not iconutil:
        raise SystemExit('Regeneration requires Inkscape and macOS iconutil')
    master = HERE / 'Jasper.svg'
    with tempfile.TemporaryDirectory(prefix='jasper-steep-') as temporary:
        pack = Path(temporary) / 'Jasper-icon-set'
        (pack / 'source').mkdir(parents=True)
        shutil.copyfile(master, pack / 'source/Jasper.svg')
        bounds = tuple(float(run(inkscape, str(master), f'--query-{field}'))
                       for field in ('x', 'y', 'width', 'height'))
        if len(bounds) != 4 or min(bounds[2:]) <= 0:
            raise ValueError(f'Invalid SVG bounds: {bounds}')
        for platform, sizes in SIZES.items():
            directory = pack / platform
            pngs = directory / 'png'
            pngs.mkdir(parents=True)
            vector = directory / 'Jasper.svg'
            if platform == 'macos':
                shutil.copyfile(HERE / 'macos/Jasper.svg', vector)
            else:
                framed_svg(master, bounds, COVERAGE[platform], vector)
            for size in sizes:
                output = pngs / f'icon-{size}.png'
                run(inkscape, str(vector), '--export-type=png',
                    f'--export-filename={output}', f'--export-width={size}',
                    f'--export-height={size}')
                if png_size(output) != (size, size):
                    raise ValueError(f'Wrong export dimensions: {output}')
            print(f'Rendered {platform}: {sizes}', flush=True)
        render_macos(pack, temporary)
        iconset = pack / 'macos/Jasper.iconset'
        iconset.mkdir()
        for points in (16, 32, 128, 256, 512):
            for scale in (1, 2):
                name = f'icon_{points}x{points}{"@2x" if scale == 2 else ""}.png'
                shutil.copyfile(pack / f'macos/png/icon-{points * scale}.png', iconset / name)
        run(iconutil, '-c', 'icns', str(iconset), '-o', str(pack / 'macos/Jasper.icns'))
        normalize_small_icns_alpha(pack / 'macos/Jasper.icns')
        write_ico(pack / 'windows/Jasper.ico',
                  [(size, pack / f'windows/png/icon-{size}.png') for size in ICO_SIZES])
        shutil.copyfile(pack / 'linux/png/icon-512.png', pack / 'linux/Jasper.png')
        for size in SIZES['linux']:
            path = pack / f'linux/hicolor/{size}x{size}/apps'
            path.mkdir(parents=True)
            shutil.copyfile(pack / f'linux/png/icon-{size}.png', path / 'jasper.png')
        scalable = pack / 'linux/hicolor/scalable/apps'
        scalable.mkdir(parents=True)
        shutil.copyfile(pack / 'linux/Jasper.svg', scalable / 'jasper.svg')
        (pack / 'README.md').write_text(
            '# Jasper — Original Steep icon set\n\n'
            'The selected original Steep artwork, with the full-height 68-degree tab.\n'
            'Windows/Linux PNGs render directly from SVG. macOS adds the approved full-canvas tab artwork '
            'through Icon Composer and native rendering; transparent outer corners are preserved.\n\n'
            '- macOS: Jasper.icns, the ten standard/Retina iconset representations, '
            'PNGs at 16, 32, 64, 128, 256, 512 and 1024px, plus Jasper.icon and Assets.car. '
            'Bundle Assets.car in Contents/Resources with CFBundleIconName=Jasper; keep '
            'Jasper.icns as the fallback. Jasper.svg is the foreground layer.\n'
            f'- Windows: a 32-bit-alpha ICO containing {len(ICO_SIZES)} PNG frames '
            f'at {", ".join(map(str, ICO_SIZES))}px; extra PNGs at 384, 512, 768 and 1024px.\n'
            f'- Linux: hicolor PNGs at {", ".join(map(str, SIZES["linux"]))}px, '
            'a scalable SVG, and a 512px package PNG.\n\n'
            'source/Jasper.svg is the exact approved master. Platform SVGs include optical '
            'padding: macOS uses system framing, Windows 960/1024, Linux 90% artwork coverage. '
            'Do not add another padding pass.\n\n'
            'The iconset is ready for iconutil; Linux hicolor contents install beneath '
            'share/icons/hicolor with the desktop icon name jasper.\n'
        )
        manifest = {'artwork': 'Original Steep', 'renderer': run(inkscape, '--version'),
                    'source_sha256': hashlib.sha256(master.read_bytes()).hexdigest(),
                    'sizes': SIZES, 'ico_sizes': ICO_SIZES, 'coverage': COVERAGE, 'files': {}}
        for file in sorted(pack.rglob('*')):
            if file.is_file():
                entry = {'sha256': hashlib.sha256(file.read_bytes()).hexdigest()}
                if file.suffix == '.png':
                    entry['dimensions'] = png_size(file)
                manifest['files'][file.relative_to(pack).as_posix()] = entry
        (pack / 'manifest.json').write_text(json.dumps(manifest, indent=2) + '\n')
        # Publish only after rendering and native-container generation succeed.
        for platform, sizes in RUNTIME.items():
            target = RESOURCES / platform
            target.mkdir(parents=True, exist_ok=True)
            for size in sizes:
                shutil.copyfile(pack / f'{platform}/png/icon-{size}.png', target / f'icon-{size}.png')
        for platform, extension in [('macos', 'icns'), ('windows', 'ico'), ('linux', 'png')]:
            shutil.copyfile(pack / f'{platform}/Jasper.{extension}', HERE / f'Jasper.{extension}')
        shutil.copyfile(pack / 'macos/Assets.car', HERE / 'macos/Assets.car')
        shutil.copytree(pack / 'linux/hicolor', HERE / 'linux/hicolor', dirs_exist_ok=True)
        shutil.copytree(pack, EXPORTS, dirs_exist_ok=True)
        # Fixed ZIP metadata makes an identical set reproducible byte-for-byte.
        with zipfile.ZipFile(ARCHIVE, 'w', compression=zipfile.ZIP_DEFLATED) as archive:
            for file in sorted(pack.rglob('*')):
                if file.is_file():
                    info = zipfile.ZipInfo('Jasper-icon-set/' + file.relative_to(pack).as_posix(),
                                           date_time=(1980, 1, 1, 0, 0, 0))
                    info.compress_type = zipfile.ZIP_DEFLATED
                    info.external_attr = 0o100644 << 16
                    archive.writestr(info, file.read_bytes())
    print(f'Updated package/runtime icons. Exported {ARCHIVE}', flush=True)


if __name__ == '__main__':
    main()
