#!/usr/bin/env python3
"""Bootstrap the Jasper desk-buddy sprite: writes the LibreSprite master (.ase)
and the runtime PNG strip. Requires Python 3 and Pillow (dev machine only).

After the master has been hand-edited in LibreSprite, re-export the PNG with:
  /Applications/libresprite.app/Contents/MacOS/libresprite --batch packaging/buddy/jasper-buddy.ase \
      --sheet jasper-app/src/main/resources/dev/jasper/app/buddy/jasper-buddy.png
and do not run this script again (it would overwrite the hand edits).
"""
import struct
import sys
import zlib
from pathlib import Path

from PIL import Image, ImageDraw, ImageFilter

HERE = Path(__file__).resolve().parent
ROOT = HERE.parent.parent
PNG = ROOT / "jasper-app/src/main/resources/dev/jasper/app/buddy/jasper-buddy.png"
ASE = HERE / "jasper-buddy.ase"

W, H = 42, 48
FRAMES = ["idle", "blink", "wink", "wave_a", "wave_b", "hop", "lean_left", "lean_right",
          "sit", "sit_blink", "tuck", "sleep_a", "sleep_b", "sleep_c",
          "sparkle_a", "sparkle_b", "sparkle_c",
          "type_a", "type_b", "type_rest"]

OUT = (0x33, 0x2F, 0x27, 255)
SKIN = (0xA7, 0xAE, 0x70, 255)
SKIN_HI = (0xC9, 0xCE, 0x93, 255)
RIM = (0xD8, 0xC4, 0x9C, 255)
RIM_LO = (0xB8, 0xA2, 0x78, 255)
BELLY = (0xEE, 0xE0, 0xBD, 255)
BELLY_LINE = (0xAB, 0x96, 0x70, 255)
EYE = (0xF7, 0xEF, 0xD7, 255)
GLINT = (255, 255, 255, 255)
STAR = (0xFF, 0xF3, 0xB0, 255)
STAR_HI = (255, 255, 255, 255)
CLEAR = (0, 0, 0, 0)
# The laptop borrows the terminal's own colours so it reads as a screen, not another shell part.
LAPTOP_SCREEN = (0x1E, 0x22, 0x2A, 255)
LAPTOP_TEXT = (0x7F, 0xD9, 0xA8, 255)
LAPTOP_BASE = (0x5A, 0x62, 0x6E, 255)


def mask():
    return Image.new("L", (W, H), 0)


def ellipse(box):
    m = mask()
    ImageDraw.Draw(m).ellipse(box, fill=255)
    return m


def rect(box):
    m = mask()
    ImageDraw.Draw(m).rectangle(box, fill=255)
    return m


def part(canvas, m, fill, outline=True):
    """Paint a shape with a 1-px outline ring around it (back-to-front layering)."""
    if outline:
        canvas.paste(Image.new("RGBA", (W, H), OUT), (0, 0), m.filter(ImageFilter.MaxFilter(3)))
    canvas.paste(Image.new("RGBA", (W, H), fill), (0, 0), m)


def put(canvas, points, color):
    for x, y in points:
        if 0 <= x < W and 0 <= y < H:
            canvas.putpixel((x, y), color)


def shift(box, dx, dy):
    return (box[0] + dx, box[1] + dy, box[2] + dx, box[3] + dy)


RIGHT_ARM = {"down": (32, 27, 37, 36), "up": (33, 14, 38, 24), "up2": (34, 17, 39, 27), "out": (33, 23, 39, 29)}
LEFT_ARM = {"down": (4, 27, 9, 36), "up": (3, 14, 8, 24), "out": (2, 23, 8, 29)}
LEGS = {
    "stand": [(12, 38, 18, 45), (23, 38, 29, 45)],
    "tuck": [(12, 38, 18, 43), (23, 38, 29, 43)],
    "kick": [(12, 38, 18, 45), (27, 36, 34, 42)],
    # Sitting feet are drawn in front of the shell's bottom rim; they keep their own place while
    # the rest of the body drops by dy, so their outline stops on row 46, off the bottom margin.
    "sit": [(6, 40, 16, 45), (25, 40, 35, 45)],
    "none": [],
}
Z_SMALL = ("###", ".#.", "###")
Z_BIG = ("#####", "...#.", "..#..", ".#...", "#####")
SMALL_STAR = (".#.", "###", ".#.")
BIG_STAR = ("..#..", "..#..", "#####", "..#..", "..#..")
# Spawn overlays: (big?, x, y, colour) per star, drawn on an otherwise empty cell. Every glyph
# stays inside columns 1-40 and rows 0-46 so the transparent margins survive, and none of them
# reaches the body, which occupies roughly columns 6-39 and rows 2-46.
SPARKLES = [
    [(True, 3, 6, STAR), (False, 33, 4, STAR_HI), (False, 8, 30, STAR), (False, 36, 24, STAR)],
    [(False, 5, 14, STAR_HI), (True, 34, 10, STAR), (False, 2, 38, STAR), (True, 30, 34, STAR)],
    [(False, 12, 2, STAR), (False, 37, 16, STAR_HI), (True, 1, 26, STAR), (False, 26, 44, STAR_HI)],
]


def shell(c, dy):
    """Rim, belly, belly lines and the shaded rim row, lowered by dy."""
    part(c, ellipse(shift((8, 22, 33, 43), 0, dy)), RIM)
    part(c, ellipse(shift((12, 26, 29, 42), 0, dy)), BELLY)
    d = ImageDraw.Draw(c)
    d.line([(20, 27 + dy), (20, 41 + dy)], fill=BELLY_LINE)
    d.line([(13, 32 + dy), (28, 32 + dy)], fill=BELLY_LINE)
    d.line([(13, 37 + dy), (28, 37 + dy)], fill=BELLY_LINE)
    for x in range(11, 31):
        if c.getpixel((x, 42 + dy)) == RIM:
            c.putpixel((x, 42 + dy), RIM_LO)


def head(c, eyes, hx, dy):
    """Neck, skull, highlight, glasses, eyes and mouth, moved by hx/dy."""
    d = ImageDraw.Draw(c)
    part(c, rect(shift((17, 20, 24, 25), hx, dy)), SKIN)
    part(c, ellipse(shift((6, 2, 35, 23), hx, dy)), SKIN)
    put(c, [(x + hx, y + dy) for x, y in [(12, 3), (13, 3), (14, 2), (15, 2), (16, 2), (17, 2), (11, 4), (10, 5)]], SKIN_HI)
    for ox in (0, 13):
        c.paste(Image.new("RGBA", (W, H), OUT), (0, 0), ellipse(shift((8 + ox, 8, 19 + ox, 19), hx, dy)))
        c.paste(Image.new("RGBA", (W, H), EYE), (0, 0), ellipse(shift((9 + ox, 9, 18 + ox, 18), hx, dy)))
    put(c, [(20 + hx, 13 + dy), (21 + hx, 13 + dy), (7 + hx, 13 + dy), (33 + hx, 13 + dy)], OUT)
    for ox, state in zip((0, 13), eyes):
        if state == "open":
            part(c, ellipse(shift((12 + ox, 11, 15 + ox, 16), hx, dy)), OUT, outline=False)
            put(c, [(14 + ox + hx, 12 + dy)], GLINT)
        else:
            d.line([(11 + ox + hx, 14 + dy), (16 + ox + hx, 14 + dy)], fill=OUT)
    put(c, [(17 + hx, 20 + dy), (24 + hx, 20 + dy)], OUT)
    put(c, [(x + hx, y + dy) for x, y in
            [(15, 20), (16, 21), (17, 22), (18, 22), (19, 22), (20, 22), (21, 22), (22, 22), (23, 22), (24, 22), (25, 21), (26, 20)]], OUT)


def glyph(c, rows, x, y, color=OUT):
    put(c, [(x + gx, y + gy) for gy, row in enumerate(rows) for gx, cell in enumerate(row) if cell == "#"], color)


def stars(specs):
    """One spawn overlay cell: nothing but stars, transparent everywhere else. A big star's
    centre pixel is always the bright highlight so it reads as a twinkle rather than a plus."""
    c = Image.new("RGBA", (W, H), CLEAR)
    for big, x, y, color in specs:
        glyph(c, BIG_STAR if big else SMALL_STAR, x, y, color)
        if big:
            put(c, [(x + 2, y + 2)], STAR_HI)
    return c


def frame(eyes=("open", "open"), right_arm="down", left_arm="down", dy=0, lean=0, legs="stand",
          arms=True, head_at="after", head_dy=None, shell_dy=None, z_glyphs=()):
    """One 42x48 cell. head_at picks "before"/"after" the shell or "none"; head_dy and
    shell_dy override the body offset dy for the tucked poses; z_glyphs adds sleep Zs.
    The sitting feet keep their own place and are drawn in front of the shell."""
    c = Image.new("RGBA", (W, H), CLEAR)
    hx = lean
    hdy = dy if head_dy is None else head_dy
    sdy = dy if shell_dy is None else shell_dy
    sitting = legs == "sit"
    if not sitting:
        for box in LEGS[legs]:
            part(c, ellipse(shift(box, 0, dy)), SKIN)
    if arms:
        part(c, ellipse(shift(LEFT_ARM[left_arm], 0, dy)), SKIN)
        part(c, ellipse(shift(RIGHT_ARM[right_arm], 0, dy)), SKIN)
    if head_at == "before":
        head(c, eyes, hx, hdy)
    shell(c, sdy)
    if sitting:
        for box in LEGS[legs]:
            part(c, ellipse(box), SKIN)
    if head_at == "after":
        head(c, eyes, hx, hdy)
    for rows, x, y in z_glyphs:
        glyph(c, rows, x, y)
    return c


def laptop(c, hands):
    """A compact laptop in his lap: small enough that the shell, belly and glasses stay visible,
    which is what makes the pose read as *him* typing rather than a screen with a head behind it.
    `hands` is "left", "right" or "rest"; the typing poses raise one hand off the keys."""
    # Arms come down and forward to the deck, drawn before the laptop so the keys cover the wrists.
    part(c, ellipse((4, 30, 10, 39)), SKIN)
    part(c, ellipse((31, 30, 37, 39)), SKIN)
    # Screen, standing in his lap and leaving the head and belly clear above it.
    part(c, rect((13, 33, 29, 40)), LAPTOP_SCREEN)
    glyph(c, ("#..", ".#.", "..#", ".#.", "#.."), 15, 35, LAPTOP_TEXT)
    put(c, [(20, 39), (21, 39)], LAPTOP_TEXT)
    # Key deck in front of the screen.
    part(c, rect((11, 41, 31, 43)), LAPTOP_BASE)
    # Hands on the key row; the raised one is a pixel higher, so A and B read as alternating.
    left_y, right_y = {"left": (39, 41), "right": (41, 39), "rest": (42, 42)}[hands]
    put(c, [(x, y) for x in range(9, 13) for y in (left_y, left_y + 1)], SKIN)
    put(c, [(x, y) for x in range(30, 34) for y in (right_y, right_y + 1)], SKIN)


def typing(hands):
    """The sitting pose with a laptop in his lap; the head dips a little toward the screen."""
    c = frame(dy=1, legs="sit", head_dy=3, arms=False)
    laptop(c, hands)
    return c


def frames():
    shell_only = dict(legs="none", arms=False, head_at="none", shell_dy=2)
    return [
        frame(),
        frame(eyes=("shut", "shut")),
        frame(eyes=("shut", "open")),
        frame(right_arm="up"),
        frame(right_arm="up2", lean=1),
        frame(dy=-1, legs="tuck", left_arm="out", right_arm="out"),
        frame(lean=-1, legs="kick", right_arm="up"),
        frame(lean=1, left_arm="up"),
        frame(dy=1, legs="sit", head_dy=2),
        frame(dy=1, legs="sit", head_dy=2, eyes=("shut", "shut")),
        frame(legs="none", arms=False, head_at="before", head_dy=5, shell_dy=2),
        frame(z_glyphs=[(Z_BIG, 22, 13), (Z_SMALL, 28, 6)], **shell_only),
        frame(z_glyphs=[(Z_BIG, 23, 9), (Z_SMALL, 29, 3)], **shell_only),
        frame(z_glyphs=[(Z_BIG, 24, 5), (Z_SMALL, 28, 15)], **shell_only),
    ] + [stars(specs) for specs in SPARKLES] + [typing("left"), typing("right"), typing("rest")]


def write_png(images):
    sheet = Image.new("RGBA", (W * len(images), H), CLEAR)
    for i, image in enumerate(images):
        sheet.paste(image, (i * W, 0))
    PNG.parent.mkdir(parents=True, exist_ok=True)
    sheet.save(PNG, optimize=True)


# --- Aseprite file format (https://github.com/aseprite/aseprite/blob/main/docs/ase-file-specs.md) ---

def string(text):
    data = text.encode("utf-8")
    return struct.pack("<H", len(data)) + data


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
    offset, images = 128, []
    for _ in range(count):
        frame_size, frame_magic, old_count, _duration, new_count = struct.unpack_from("<IHHH2xI", data, offset)
        assert frame_magic == 0xF1FA, "bad frame magic"
        pos, end = offset + 16, offset + frame_size
        for _ in range(new_count or old_count):
            chunk_size, kind = struct.unpack_from("<IH", data, pos)
            if kind == 0x2005:
                w, h = struct.unpack_from("<HH", data, pos + 6 + 16)
                raw = zlib.decompress(data[pos + 6 + 20:pos + chunk_size])
                images.append(Image.frombytes("RGBA", (w, h), raw))
            pos += chunk_size
        assert pos == end, "chunk sizes do not add up"
        offset = end
    return images


def main(argv):
    images = frames()
    if "--verify-only" not in argv:
        write_png(images)
        write_ase(images)
    back = read_ase(ASE)
    sheet = Image.open(PNG).convert("RGBA")
    assert len(back) == len(images) == len(FRAMES), "frame count"
    for i, (name, image) in enumerate(zip(FRAMES, back)):
        cell = sheet.crop((i * W, 0, (i + 1) * W, H))
        assert list(cell.getdata()) == list(image.getdata()), f"{name}: .ase and PNG differ"
    print(f"ok: {len(images)} frames -> {PNG.relative_to(ROOT)} and {ASE.relative_to(ROOT)}")


if __name__ == "__main__":
    main(sys.argv[1:])
