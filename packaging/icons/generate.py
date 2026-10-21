#!/usr/bin/env python3
"""Export the approved Shell Prompt raster master to native packages, runtime PNGs and a shareable set.

Requires Pillow, macOS iconutil and Xcode (actool/Swift). Normal builds use committed assets.
Windows/Linux resize the raster master; macOS uses the native icon renderer.
"""
from pathlib import Path
import base64
import hashlib
import json
import plistlib
import uuid
import shutil
import struct
import subprocess
import tempfile
from PIL import Image
import zipfile

HERE = Path(__file__).resolve().parent
ROOT = HERE.parent.parent
RESOURCES = ROOT / 'jasper-app/src/main/resources/dev/jasper/app/icons/app'
EXPORTS = ROOT / 'build/jasper-icon-set'
ARCHIVE = ROOT / 'build/Jasper-icon-set.zip'
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


def raster_svg(image, destination):
    """Embed the selected pixels for SVG-consuming desktop integrations."""
    import io
    buffer = io.BytesIO()
    image.save(buffer, format='PNG')
    encoded = base64.b64encode(buffer.getvalue()).decode('ascii')
    destination.write_text(
        '<svg xmlns="http://www.w3.org/2000/svg" width="1024" height="1024" viewBox="0 0 1024 1024">'
        '<title>Jasper Shell Prompt</title>'
        f'<image width="1024" height="1024" href="data:image/png;base64,{encoded}"/></svg>\n')


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
    """Compile the approved Shell Prompt artwork and render all native Mac sizes."""
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
    iconutil = shutil.which('iconutil')
    if not iconutil:
        raise SystemExit('Regeneration requires Pillow and macOS iconutil')
    master = HERE / 'shell-prompt-source.png'
    artwork = Image.open(master).convert('RGBA')
    # Remove generated transparent padding/shadow before applying platform padding once.
    bounds = artwork.getchannel('A').point(lambda alpha: 255 if alpha >= 240 else 0).getbbox()
    if not bounds:
        raise ValueError('Master contains no opaque artwork')
    artwork = artwork.crop(bounds)
    with tempfile.TemporaryDirectory(prefix='jasper-shell-prompt-') as temporary:
        pack = Path(temporary) / 'Jasper-icon-set'
        (pack / 'source').mkdir(parents=True)
        shutil.copyfile(master, pack / 'source/shell-prompt-source.png')
        for platform, sizes in SIZES.items():
            directory = pack / platform
            pngs = directory / 'png'
            pngs.mkdir(parents=True)
            extent = round(1024 * COVERAGE[platform])
            tile = artwork.resize((extent, extent), Image.Resampling.LANCZOS)
            # Icon Composer applies the Mac mask. Fill source corners with forest, not silver.
            canvas = Image.new('RGBA', (1024, 1024), '#303D29' if platform == 'macos' else (0, 0, 0, 0))
            canvas.alpha_composite(tile, ((1024 - extent) // 2, (1024 - extent) // 2))
            raster_svg(canvas, directory / 'Jasper.svg')
            for size in sizes:
                output = pngs / f'icon-{size}.png'
                canvas.resize((size, size), Image.Resampling.LANCZOS).save(output)
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
            '# Jasper — Shell Prompt icon set\n\n'
            'The approved moss shell and cream terminal prompt. source/shell-prompt-source.png '
            'is the generated raster master. SVG files embed this artwork; they are not vector redraws.\n\n'
            'macOS: native Icon Composer catalog, ICNS fallback, ten standard/Retina iconset '
            'representations and PNGs through 1024px. Windows: 20 RGBA ICO frames through 256px '
            'plus standalone PNGs through 1024px. Linux: hicolor PNGs through 1024px and an embedded-raster SVG.\n\n'
            'Platform padding is applied once: macOS system framing, Windows 960/1024, Linux 90%.\n')
        manifest = {'artwork': 'Shell Prompt', 'renderer': 'Pillow ' + Image.__version__,
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
        shutil.copyfile(pack / 'macos/Jasper.svg', HERE / 'macos/Jasper.svg')
        shutil.copyfile(pack / 'windows/Jasper.svg', HERE / 'Jasper.svg')
        shutil.copyfile(pack / 'linux/png/icon-256.png', ROOT / 'website/assets/jasper.png')
        shutil.copytree(pack / 'linux/hicolor', HERE / 'linux/hicolor', dirs_exist_ok=True)
        if EXPORTS.exists():
            shutil.rmtree(EXPORTS)
        shutil.copytree(pack, EXPORTS)
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
