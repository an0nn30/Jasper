#!/usr/bin/env python3
"""Export Jasper's pixel-art LibreSprite master, or verify it against the runtime strip.
Use --import-strip after packing a new source atlas to update the editable master.
Requires Python 3 and Pillow; ordinary builds use the committed PNG.
"""
import struct
import sys
import zlib
from pathlib import Path
from PIL import Image
HERE = Path(__file__).resolve().parent
ROOT = HERE.parent.parent
PNG = ROOT / "jasper-buddy/src/main/resources/dev/jasper/buddy/internal/presentation/jasper-buddy.png"
ASE = HERE / "jasper-buddy.ase"
W, H = 84, 96
FRAMES = ["idle", "blink", "wink", "wave_a", "wave_b", "hop", "lean_left", "lean_right",
          "sit", "sit_blink", "tuck", "sleep_a", "sleep_b", "sleep_c",
          "sparkle_a", "sparkle_b", "sparkle_c", "type_a", "type_b", "type_rest",
          "close_laptop", "lower_laptop", "rise"]

def string(text):
    raw = text.encode("utf-8")
    return struct.pack("<H", len(raw)) + raw


def chunk(kind, payload):
    return struct.pack("<IH", 6 + len(payload), kind) + payload


def layer_chunk():
    # flags: visible|editable, type normal, child level 0, ignored w/h, blend normal, opacity 255, 3 reserved
    return chunk(0x2004, struct.pack("<HHHHHHB3x", 3, 0, 0, 0, 0, 0, 255) + string("Jasper"))


def cel_chunk(image):
    raw = image.tobytes("raw", "RGBA")
    # layer 0, x 0, y 0, opacity 255, type 2 (compressed image), z-index 0, 5 reserved, then w, h, zlib
    head = struct.pack("<HhhBHh5x", 0, 0, 0, 255, 2, 0)
    return chunk(0x2005, head + struct.pack("<HH", W, H) + zlib.compress(raw, 9))


def frame_bytes(image, first):
    chunks = (layer_chunk() if first else b"") + cel_chunk(image)
    count = 2 if first else 1
    # frame size, magic, old chunk count, duration ms, 2 reserved, new chunk count
    return struct.pack("<IHHH2xI", 16 + len(chunks), 0xF1FA, count, 100, count) + chunks


def write_ase(images):
    body = b"".join(frame_bytes(image, i == 0) for i, image in enumerate(images))
    header = struct.pack(
        "<IHHHHHIHIIB3xHBBhhHH84x",
        128 + len(body), 0xA5E0, len(images), W, H, 32, 1, 100, 0, 0,
        0, 0, 1, 1, 0, 0, 16, 16)
    assert len(header) == 128
    ASE.write_bytes(header + body)


def read_ase(path):
    """Minimal reader used to verify our own output pixel-for-pixel."""
    data = path.read_bytes()
    size, magic, count, width, height, depth = struct.unpack_from("<IHHHHH", data, 0)
    assert magic == 0xA5E0 and size == len(data) and (width, height, depth) == (W, H, 32), "bad header"
    offset, images, layers = 128, [], 0
    for _ in range(count):
        frame_size, frame_magic, old_count, _duration, new_count = struct.unpack_from("<IHHH2xI", data, offset)
        assert frame_magic == 0xF1FA, "bad frame magic"
        pos, end = offset + 16, offset + frame_size
        frame_images = []
        assert end <= len(data), "frame exceeds file"
        for _ in range(new_count or old_count):
            chunk_size, kind = struct.unpack_from("<IH", data, pos)
            assert chunk_size >= 6 and pos + chunk_size <= end, "bad chunk size"
            if kind == 0x2004:
                flags, layer_type, child, _w, _h, blend, opacity = struct.unpack_from("<HHHHHHB", data, pos + 6)
                layers += 1
                assert layers == 1 and flags & 1 and layer_type == child == blend == 0 and opacity == 255, "export requires one visible normal opaque layer"
            if kind == 0x2005:
                layer, x, y, opacity, cel_type, z_index = struct.unpack_from("<HhhBHh", data, pos + 6)
                assert (layer, x, y, opacity, cel_type, z_index) == (0, 0, 0, 255, 2, 0), "export requires full-canvas compressed cels at (0, 0); flatten and export with LibreSprite for other layouts"
                w, h = struct.unpack_from("<HH", data, pos + 6 + 16)
                raw = zlib.decompress(data[pos + 6 + 20:pos + chunk_size])
                assert (w, h) == (W, H), "export requires full-canvas cels"
                frame_images.append(Image.frombytes("RGBA", (w, h), raw))
            pos += chunk_size
        assert pos == end, "chunk sizes do not add up"
        assert len(frame_images) == 1, "export requires one cel per frame"
        images.extend(frame_images)
        offset = end
    assert layers == 1 and offset == len(data), "invalid layer count or trailing data"
    return images


def main(argv):
    if any(arg not in ("--verify-only", "--import-strip") for arg in argv):
        raise SystemExit("Use --verify-only, --import-strip, or no arguments to export the master")
    if "--import-strip" in argv:
        sheet = Image.open(PNG).convert("RGBA")
        assert sheet.size == (W * len(FRAMES), H), "runtime strip dimensions"
        write_ase([sheet.crop((i*W, 0, (i+1)*W, H)) for i in range(len(FRAMES))])
    images = read_ase(ASE)
    assert len(images) == len(FRAMES), "frame count"
    if not argv:
        sheet = Image.new("RGBA", (W * len(FRAMES), H))
        for i, image in enumerate(images): sheet.paste(image, (i*W, 0))
        sheet.save(PNG)
    sheet = Image.open(PNG).convert("RGBA")
    assert sheet.size == (W * len(FRAMES), H), "runtime strip dimensions"
    for i, image in enumerate(images):
        assert image.size == (W, H), "master frame dimensions"
        assert sheet.crop((i*W, 0, (i+1)*W, H)).tobytes() == image.tobytes(), FRAMES[i]
    print(f"ok: {len(images)} pixel frames; master and runtime match")

if __name__ == "__main__": main(sys.argv[1:])
