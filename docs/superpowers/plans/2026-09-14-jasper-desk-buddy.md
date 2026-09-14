# Jasper Desk Buddy Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Status:** Plan written 2026-09-14 on branch `claude/desk-buddy` (from `main` `5bc288f`). Implemented on `claude/desk-buddy` in `.worktrees/desk-buddy`; all eight tasks complete and reviewed through the final whole-branch review (see the Deviations log). Desktop acceptance is user-run.

**Goal:** A pixel-art Jasper floats above every desktop window while a terminal window is open, blinks while idle, dances on hover, can be dragged, remembers his spot, and can be hidden from the View menu, the palette, a right-click, or `buddy.enabled`.

**Architecture:** A frameless per-pixel-translucent always-on-top Swing `JWindow` (`BuddyWindow`) paints one frame of a committed PNG sprite strip (`BuddySprite`) chosen by a clock-driven state machine (`BuddyAnimator`). Pure helpers handle placement (`BuddyPlacement`), persistence (`BuddyStateFile`) and the show/hide rule (`BuddyVisibility`). `JasperApplication` owns the single buddy and feeds it terminal-window lifecycle events; `ConfigurationController` delivers `buddy.enabled`; the View menu and palette get a `view.buddy` command.

**Tech Stack:** Java 25 on JBR 25, Swing/Java2D, tomlj, JUnit 6 + AssertJ (headless). Art: Python 3 + Pillow bootstrap script writing an Aseprite `.ase` master and the PNG; LibreSprite for later hand edits.

**Spec:** `docs/superpowers/specs/2026-09-14-jasper-desk-buddy-design.md`

## Global Constraints

- Modules stay `jasper-terminal` and `jasper-app`; all new classes are package-private in `dev.jasper.app` with a `Buddy` prefix. No interface without two real implementations; no plugin API.
- Never launch the GUI (`./gradlew :jasper-app:run`) from an agent. Desktop acceptance is user-run.
- Tests are headless (`java.awt.headless=true`): never construct a `JWindow`/`JFrame` in tests. `BuddyWindow` is not unit-tested.
- Timers stop when the buddy is hidden or disposed; no timer runs when nothing is scheduled.
- Frames are exactly 42 × 48 art pixels, eight of them in the order `IDLE, BLINK, WINK, WAVE_A, WAVE_B, HOP, LEAN_LEFT, LEAN_RIGHT`; the runtime scale is 2 logical px per art pixel (window 84 × 96).
- Palette (from `packaging/icons/jasper.svg`): outline `#332f27`, skin `#a7ae70`, skin highlight `#c9ce93`, shell rim `#d8c49c`, rim shade `#b8a278`, belly `#eee0bd`, belly lines `#ab9670`, eye/lens `#f7efd7`, glint `#ffffff`.
- Source hygiene: no raw control/private-use/surrogate characters in Java source (use escapes). Run the Python checker from `AGENTS.md` after each Java task.
- Build/test with `./gradlew` only (never a system `gradle`). Commit per task with a `Co-Authored-By: Claude Fable 5.1 <noreply@anthropic.com>` trailer. Do not commit on `main`; do not push without the user.
- Use `/Applications/libresprite.app/Contents/MacOS/libresprite --batch …` only with `timeout 60`; if it opens a window or hangs, kill it and record that in the README rather than retrying.

---

## File map

| File | Responsibility |
|---|---|
| `packaging/buddy/generate.py` (new) | Bootstrap: draws the eight frames, writes `jasper-buddy.ase` and the PNG strip, verifies the `.ase` by reading it back |
| `packaging/buddy/jasper-buddy.ase` (new) | Editable LibreSprite master |
| `packaging/buddy/README.md` (new) | Palette, frame order, export and regeneration commands |
| `jasper-app/src/main/resources/dev/jasper/app/buddy/jasper-buddy.png` (new) | Runtime sprite strip 336 × 48 |
| `jasper-app/src/main/java/dev/jasper/app/BuddyFrame.java` (new) | Frame enum with column index |
| `jasper-app/src/main/java/dev/jasper/app/BuddySprite.java` (new) | Sheet loading, validation, slicing, nearest-neighbour painting |
| `jasper-app/src/main/java/dev/jasper/app/BuddyAnimator.java` (new) | Idle blink / hover dance state machine |
| `jasper-app/src/main/java/dev/jasper/app/BuddyPlacement.java` (new) | Default corner and screen clamping |
| `jasper-app/src/main/java/dev/jasper/app/BuddyStateFile.java` (new) | `buddy.toml` strict read and atomic write |
| `jasper-app/src/main/java/dev/jasper/app/BuddyVisibility.java` (new) | Saved default + session toggle + window states → shown |
| `jasper-app/src/main/java/dev/jasper/app/BuddyWindow.java` (new) | The translucent utility window |
| `ConfigLoader.java`, `ConfigSnapshot.java`, `ConfigTemplate.java`, `config.example.toml`, `docs/configuration.md` (modify) | `buddy.enabled` |
| `ConfigurationController.java` (modify) | Application-level snapshot listener |
| `WindowContent.java`, `WindowCommands.java`, `WindowChrome.java` (modify) | `view.buddy` command, View menu checkbox, hooks |
| `AppDirs.java`, `Main.java`, `JasperApplication.java`, `TerminalWindow.java` (modify) | Ownership, lifecycle events, state file path |
| `docs/superpowers/plans/2026-09-14-jasper-desk-buddy-manual-check.md` (new), `docs/STATUS.md` (modify) | Desktop acceptance checklist and handoff |

---

### Task 1: Sprite assets, `BuddyFrame` and `BuddySprite`

**Files:**
- Create: `packaging/buddy/generate.py`, `packaging/buddy/README.md`, `packaging/buddy/jasper-buddy.ase`
- Create: `jasper-app/src/main/resources/dev/jasper/app/buddy/jasper-buddy.png`
- Create: `jasper-app/src/main/java/dev/jasper/app/BuddyFrame.java`, `jasper-app/src/main/java/dev/jasper/app/BuddySprite.java`
- Test: `jasper-app/src/test/java/dev/jasper/app/BuddySpriteTest.java`

**Interfaces:**
- Produces: `enum BuddyFrame { IDLE, BLINK, WINK, WAVE_A, WAVE_B, HOP, LEAN_LEFT, LEAN_RIGHT; int column() }`
- Produces: `final class BuddySprite { static final int FRAME_WIDTH = 42, FRAME_HEIGHT = 48; static final String RESOURCE; static BuddySprite load(); BuddySprite(BufferedImage sheet); BufferedImage frame(BuddyFrame); void paint(Graphics2D g, BuddyFrame frame, int scale, int x, int y); static Dimension size(int scale) }`

- [ ] **Step 1: Write the failing test**

`jasper-app/src/test/java/dev/jasper/app/BuddySpriteTest.java`:

```java
package dev.jasper.app;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class BuddySpriteTest {
    @Test void committedSheetHasEightOpaqueFramesInsideATransparentMargin() {
        BuddySprite sprite = BuddySprite.load();
        for (BuddyFrame frame : BuddyFrame.values()) {
            BufferedImage image = sprite.frame(frame);
            assertThat(image.getWidth()).isEqualTo(BuddySprite.FRAME_WIDTH);
            assertThat(image.getHeight()).isEqualTo(BuddySprite.FRAME_HEIGHT);
            boolean opaque = false;
            for (int y = 0; y < image.getHeight() && !opaque; y++)
                for (int x = 0; x < image.getWidth() && !opaque; x++) opaque = (image.getRGB(x, y) >>> 24) == 255;
            assertThat(opaque).as("%s has opaque pixels", frame).isTrue();
            for (int y = 0; y < image.getHeight(); y++) {
                assertThat(image.getRGB(0, y) >>> 24).as("%s left margin row %d", frame, y).isZero();
                assertThat(image.getRGB(image.getWidth() - 1, y) >>> 24).as("%s right margin row %d", frame, y).isZero();
            }
            for (int x = 0; x < image.getWidth(); x++)
                assertThat(image.getRGB(x, image.getHeight() - 1) >>> 24).as("%s bottom margin column %d", frame, x).isZero();
        }
        assertThat(BuddyFrame.HOP.column()).isEqualTo(5);
        assertThat(BuddySprite.size(2)).isEqualTo(new Dimension(84, 96));
    }

    @Test void framesAreDistinctAndTheOutlineColorIsPresent() {
        BuddySprite sprite = BuddySprite.load();
        assertThat(pixels(sprite.frame(BuddyFrame.IDLE))).isNotEqualTo(pixels(sprite.frame(BuddyFrame.BLINK)));
        assertThat(pixels(sprite.frame(BuddyFrame.WAVE_A))).isNotEqualTo(pixels(sprite.frame(BuddyFrame.WAVE_B)));
        assertThat(pixels(sprite.frame(BuddyFrame.IDLE))).contains(new Color(0x33, 0x2f, 0x27).getRGB());
    }

    @Test void paintingScalesEveryArtPixelIntoASolidBlock() {
        BufferedImage sheet = new BufferedImage(BuddySprite.FRAME_WIDTH * BuddyFrame.values().length,
            BuddySprite.FRAME_HEIGHT, BufferedImage.TYPE_INT_ARGB);
        int red = new Color(200, 30, 30).getRGB();
        sheet.setRGB(BuddySprite.FRAME_WIDTH * BuddyFrame.WINK.column() + 10, 20, red);
        BuddySprite sprite = new BuddySprite(sheet);

        BufferedImage target = new BufferedImage(84, 96, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = target.createGraphics();
        sprite.paint(g, BuddyFrame.WINK, 2, 0, 0);
        g.dispose();

        for (int y = 0; y < 96; y++) for (int x = 0; x < 84; x++) {
            boolean inside = x >= 20 && x < 22 && y >= 40 && y < 42;
            assertThat(target.getRGB(x, y)).as("(%d,%d)", x, y).isEqualTo(inside ? red : 0);
        }
    }

    @Test void wrongSheetDimensionsAreRejected() {
        assertThatThrownBy(() -> new BuddySprite(new BufferedImage(42, 48, BufferedImage.TYPE_INT_ARGB)))
            .isInstanceOf(IllegalStateException.class).hasMessageContaining("336");
    }

    private static int[] pixels(BufferedImage image) {
        return image.getRGB(0, 0, image.getWidth(), image.getHeight(), null, 0, image.getWidth());
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `./gradlew :jasper-app:test --tests 'dev.jasper.app.BuddySpriteTest'`
Expected: compilation FAILS (`BuddySprite`, `BuddyFrame` do not exist).

- [ ] **Step 3: Write the bootstrap generator**

`packaging/buddy/generate.py`:

```python
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
FRAMES = ["idle", "blink", "wink", "wave_a", "wave_b", "hop", "lean_left", "lean_right"]

OUT = (0x33, 0x2F, 0x27, 255)
SKIN = (0xA7, 0xAE, 0x70, 255)
SKIN_HI = (0xC9, 0xCE, 0x93, 255)
RIM = (0xD8, 0xC4, 0x9C, 255)
RIM_LO = (0xB8, 0xA2, 0x78, 255)
BELLY = (0xEE, 0xE0, 0xBD, 255)
BELLY_LINE = (0xAB, 0x96, 0x70, 255)
EYE = (0xF7, 0xEF, 0xD7, 255)
GLINT = (255, 255, 255, 255)
CLEAR = (0, 0, 0, 0)


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
}


def frame(eyes=("open", "open"), right_arm="down", left_arm="down", dy=0, lean=0, legs="stand"):
    c = Image.new("RGBA", (W, H), CLEAR)
    hx = lean
    for box in LEGS[legs]:
        part(c, ellipse(shift(box, 0, dy)), SKIN)
    part(c, ellipse(shift(LEFT_ARM[left_arm], 0, dy)), SKIN)
    part(c, ellipse(shift(RIGHT_ARM[right_arm], 0, dy)), SKIN)
    part(c, ellipse(shift((8, 22, 33, 43), 0, dy)), RIM)
    part(c, ellipse(shift((12, 26, 29, 42), 0, dy)), BELLY)
    d = ImageDraw.Draw(c)
    d.line([(20, 27 + dy), (20, 41 + dy)], fill=BELLY_LINE)
    d.line([(13, 32 + dy), (28, 32 + dy)], fill=BELLY_LINE)
    d.line([(13, 37 + dy), (28, 37 + dy)], fill=BELLY_LINE)
    for x in range(11, 31):
        if c.getpixel((x, 42 + dy)) == RIM:
            c.putpixel((x, 42 + dy), RIM_LO)
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
    return c


def frames():
    return [
        frame(),
        frame(eyes=("shut", "shut")),
        frame(eyes=("shut", "open")),
        frame(right_arm="up"),
        frame(right_arm="up2", lean=1),
        frame(dy=-1, legs="tuck", left_arm="out", right_arm="out"),
        frame(lean=-1, legs="kick", right_arm="up"),
        frame(lean=1, left_arm="up"),
    ]


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
```

- [ ] **Step 4: Run the generator and inspect the strip**

Run:

```bash
python3 packaging/buddy/generate.py && python3 - <<'PY'
from PIL import Image
s = Image.open("jasper-app/src/main/resources/dev/jasper/app/buddy/jasper-buddy.png")
print(s.size)
big = s.resize((s.width * 4, s.height * 4), Image.NEAREST)
bg = Image.new("RGBA", (big.width + 40, big.height + 40), (0x3f, 0x7a, 0xb8, 255)); bg.paste(big, (20, 20), big)
bg.save("/private/tmp/claude-501/-Users-dustin-projects-moray/3b2f82c4-38fe-4522-979f-ca56761e02f4/scratchpad/buddy_preview.png")
PY
```

Expected: `ok: 8 frames …`, size `(336, 48)`. Open the preview PNG with the Read tool and confirm all eight frames show the turtle with no stray pixels at the bottom row and nothing touching the left/right frame edges. If a frame touches an edge, adjust the offending box in `RIGHT_ARM`/`LEFT_ARM`/`LEGS` and rerun.

- [ ] **Step 5: Optionally confirm LibreSprite reads the master**

Run (60 s cap; kill if a window appears):

```bash
timeout 60 /Applications/libresprite.app/Contents/MacOS/libresprite --batch packaging/buddy/jasper-buddy.ase --sheet /private/tmp/claude-501/-Users-dustin-projects-moray/3b2f82c4-38fe-4522-979f-ca56761e02f4/scratchpad/libresprite-export.png; echo "exit $?"
```

Expected: exit 0 and an exported PNG. Compare with Python: `Image.open(a).convert("RGBA").tobytes() == Image.open(b).convert("RGBA").tobytes()`. If the export has a different layout (LibreSprite may add padding), record that in the README's export section and note that the runtime strip must remain 336 × 48 with no padding (`--sheet-pack` options are not available in LibreSprite; crop with Pillow if needed). If the batch call fails or opens a window, record the result in the README and continue; the committed PNG is authoritative.

- [ ] **Step 6: Write the README**

`packaging/buddy/README.md`:

```markdown
# Jasper desk buddy sprite

Pixel-art Jasper for the floating desk buddy: 42 × 48 art pixels per frame, drawn at
2 logical px per art pixel by the app (84 × 96 window, 4 device px per art pixel on Retina).

- `jasper-buddy.ase` — editable master for [LibreSprite](https://libresprite.github.io). One RGBA layer, eight frames.
- `../../jasper-app/src/main/resources/dev/jasper/app/buddy/jasper-buddy.png` — runtime strip, 336 × 48, frames left to right.
- `generate.py` — bootstrap that drew the first version of both files (Python 3 + Pillow). Do not rerun it after hand edits.

## Frames (column order is fixed by `BuddyFrame` in the app)

| # | Name | Meaning |
|---|---|---|
| 0 | `IDLE` | Standing, eyes open |
| 1 | `BLINK` | Both eyes shut |
| 2 | `WINK` | Left eye shut |
| 3 | `WAVE_A` | Right arm raised |
| 4 | `WAVE_B` | Right arm raised higher, head leans right |
| 5 | `HOP` | Lifted one pixel, legs tucked, both arms out |
| 6 | `LEAN_LEFT` | Head leans left, right leg kicks, right arm up |
| 7 | `LEAN_RIGHT` | Head leans right, left arm up |

## Palette (from `../icons/jasper.svg`)

| Role | Hex |
|---|---|
| Outline, pupils, glasses | `#332f27` |
| Skin | `#a7ae70` |
| Skin highlight | `#c9ce93` |
| Shell rim | `#d8c49c` |
| Rim shade | `#b8a278` |
| Belly | `#eee0bd` |
| Belly lines | `#ab9670` |
| Lenses | `#f7efd7` |
| Eye glint | `#ffffff` |

## Editing and exporting

Open `jasper-buddy.ase` in LibreSprite, edit, save, then export the runtime strip:

```sh
/Applications/libresprite.app/Contents/MacOS/libresprite --batch packaging/buddy/jasper-buddy.ase \
    --sheet jasper-app/src/main/resources/dev/jasper/app/buddy/jasper-buddy.png
```

Keep eight frames of 42 × 48 with a transparent left, right and bottom margin;
`BuddySpriteTest` fails the build otherwise. Ordinary Gradle builds need neither
LibreSprite nor Python.
```

Append the outcome of Step 5 under "Editing and exporting" (for example "Verified 2026-09-14: batch export matches the committed strip byte-for-byte" or the observed difference).

- [ ] **Step 7: Write `BuddyFrame` and `BuddySprite`**

`jasper-app/src/main/java/dev/jasper/app/BuddyFrame.java`:

```java
package dev.jasper.app;

/** Columns of the desk-buddy sprite strip, in the committed left-to-right order. */
enum BuddyFrame {
    IDLE, BLINK, WINK, WAVE_A, WAVE_B, HOP, LEAN_LEFT, LEAN_RIGHT;

    int column() { return ordinal(); }
}
```

`jasper-app/src/main/java/dev/jasper/app/BuddySprite.java`:

```java
package dev.jasper.app;

import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;
import javax.imageio.ImageIO;

/** The committed pixel-art strip: validates, slices and paints frames without smoothing. */
final class BuddySprite {
    static final int FRAME_WIDTH = 42;
    static final int FRAME_HEIGHT = 48;
    static final String RESOURCE = "/dev/jasper/app/buddy/jasper-buddy.png";

    private final Map<BuddyFrame, BufferedImage> frames = new EnumMap<>(BuddyFrame.class);

    BuddySprite(BufferedImage sheet) {
        Objects.requireNonNull(sheet, "sheet");
        int expectedWidth = FRAME_WIDTH * BuddyFrame.values().length;
        if (sheet.getWidth() != expectedWidth || sheet.getHeight() != FRAME_HEIGHT) {
            throw new IllegalStateException("Buddy sprite must be " + expectedWidth + "x" + FRAME_HEIGHT
                + " but is " + sheet.getWidth() + "x" + sheet.getHeight());
        }
        for (BuddyFrame frame : BuddyFrame.values()) {
            BufferedImage copy = new BufferedImage(FRAME_WIDTH, FRAME_HEIGHT, BufferedImage.TYPE_INT_ARGB);
            Graphics2D g = copy.createGraphics();
            g.drawImage(sheet, 0, 0, FRAME_WIDTH, FRAME_HEIGHT,
                frame.column() * FRAME_WIDTH, 0, (frame.column() + 1) * FRAME_WIDTH, FRAME_HEIGHT, null);
            g.dispose();
            frames.put(frame, copy);
        }
    }

    static BuddySprite load() {
        try (InputStream input = BuddySprite.class.getResourceAsStream(RESOURCE)) {
            if (input == null) throw new IllegalStateException("Missing buddy sprite resource " + RESOURCE);
            BufferedImage sheet = ImageIO.read(input);
            if (sheet == null) throw new IllegalStateException("Unreadable buddy sprite resource " + RESOURCE);
            return new BuddySprite(sheet);
        } catch (IOException failure) {
            throw new IllegalStateException("Cannot read buddy sprite resource " + RESOURCE, failure);
        }
    }

    BufferedImage frame(BuddyFrame frame) { return frames.get(frame); }

    static Dimension size(int scale) { return new Dimension(FRAME_WIDTH * scale, FRAME_HEIGHT * scale); }

    /** Draws one frame at an integer scale; every art pixel becomes a solid scale-by-scale block. */
    void paint(Graphics2D g, BuddyFrame frame, int scale, int x, int y) {
        Object previous = g.getRenderingHint(RenderingHints.KEY_INTERPOLATION);
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
        g.drawImage(frames.get(frame), x, y, FRAME_WIDTH * scale, FRAME_HEIGHT * scale, null);
        if (previous != null) g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, previous);
    }
}
```

- [ ] **Step 8: Run the tests**

Run: `./gradlew :jasper-app:test --tests 'dev.jasper.app.BuddySpriteTest'`
Expected: PASS (4 tests). If the margin assertion fails, fix the generator geometry (Step 4), regenerate, rerun.

- [ ] **Step 9: Source hygiene and commit**

Run the Python hygiene check from `AGENTS.md` against `jasper-app/src/main/java/dev/jasper/app/BuddySprite.java jasper-app/src/main/java/dev/jasper/app/BuddyFrame.java jasper-app/src/test/java/dev/jasper/app/BuddySpriteTest.java`. Expected: no output.

```bash
git add packaging/buddy jasper-app/src/main/resources/dev/jasper/app/buddy jasper-app/src/main/java/dev/jasper/app/BuddyFrame.java jasper-app/src/main/java/dev/jasper/app/BuddySprite.java jasper-app/src/test/java/dev/jasper/app/BuddySpriteTest.java
git commit -m "feat: add the Jasper desk-buddy sprite strip and loader

Co-Authored-By: Claude Fable 5.1 <noreply@anthropic.com>"
```

---

### Task 2: `BuddyAnimator`

**Files:**
- Create: `jasper-app/src/main/java/dev/jasper/app/BuddyAnimator.java`
- Test: `jasper-app/src/test/java/dev/jasper/app/BuddyAnimatorTest.java`

**Interfaces:**
- Consumes: `BuddyFrame`
- Produces: `final class BuddyAnimator { BuddyAnimator(Random random); void shown(long now); void hidden(); void hoverEntered(long now); void hoverExited(long now); void tick(long now); BuddyFrame frame(); OptionalLong nextDueNanos(); static final long BLINK_MIN_NANOS, BLINK_MAX_NANOS, BLINK_NANOS, STEP_NANOS; static final List<BuddyFrame> DANCE }`

- [ ] **Step 1: Write the failing test**

`jasper-app/src/test/java/dev/jasper/app/BuddyAnimatorTest.java`:

```java
package dev.jasper.app;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class BuddyAnimatorTest {
    private static final long SECOND = TimeUnit.SECONDS.toNanos(1);

    @Test void hiddenAnimatorIsIdleWithNothingScheduled() {
        var animator = new BuddyAnimator(new Random(1));
        assertThat(animator.frame()).isEqualTo(BuddyFrame.IDLE);
        assertThat(animator.nextDueNanos()).isEmpty();
        animator.hoverEntered(0); animator.tick(SECOND);
        assertThat(animator.frame()).isEqualTo(BuddyFrame.IDLE);
        assertThat(animator.nextDueNanos()).isEmpty();
    }

    @Test void idleBlinksBetweenThreeAndSixSecondsForOneHundredTwentyMilliseconds() {
        var animator = new BuddyAnimator(new Random(7));
        long now = 10 * SECOND;
        animator.shown(now);
        int winks = 0;
        for (int i = 0; i < 200; i++) {
            long due = animator.nextDueNanos().orElseThrow();
            assertThat(due - now).isBetween(BuddyAnimator.BLINK_MIN_NANOS, BuddyAnimator.BLINK_MAX_NANOS);
            animator.tick(due - 1);
            assertThat(animator.frame()).isEqualTo(BuddyFrame.IDLE);
            animator.tick(due);
            assertThat(animator.frame()).isIn(BuddyFrame.BLINK, BuddyFrame.WINK);
            if (animator.frame() == BuddyFrame.WINK) winks++;
            long reopen = animator.nextDueNanos().orElseThrow();
            assertThat(reopen - due).isEqualTo(BuddyAnimator.BLINK_NANOS);
            animator.tick(reopen);
            assertThat(animator.frame()).isEqualTo(BuddyFrame.IDLE);
            now = reopen;
        }
        assertThat(winks).isBetween(25, 75);
    }

    @Test void hoverDancesInOrderAtEightFramesPerSecondAndFinishesTheCycleAfterExit() {
        var animator = new BuddyAnimator(new Random(3));
        animator.shown(0);
        animator.hoverEntered(SECOND);
        assertThat(animator.frame()).isEqualTo(BuddyFrame.WAVE_A);
        List<BuddyFrame> seen = new ArrayList<>();
        long now = SECOND;
        for (int i = 0; i < 7; i++) {
            long due = animator.nextDueNanos().orElseThrow();
            assertThat(due - now).isEqualTo(BuddyAnimator.STEP_NANOS);
            animator.tick(due); now = due;
            seen.add(animator.frame());
        }
        assertThat(seen).containsExactly(BuddyFrame.WAVE_B, BuddyFrame.HOP, BuddyFrame.LEAN_LEFT, BuddyFrame.LEAN_RIGHT,
            BuddyFrame.WAVE_A, BuddyFrame.WAVE_B, BuddyFrame.HOP);
        animator.hoverExited(now);
        assertThat(animator.frame()).isEqualTo(BuddyFrame.HOP);
        animator.tick(animator.nextDueNanos().orElseThrow()); assertThat(animator.frame()).isEqualTo(BuddyFrame.LEAN_LEFT);
        animator.tick(animator.nextDueNanos().orElseThrow()); assertThat(animator.frame()).isEqualTo(BuddyFrame.LEAN_RIGHT);
        long end = animator.nextDueNanos().orElseThrow();
        animator.tick(end);
        assertThat(animator.frame()).isEqualTo(BuddyFrame.IDLE);
        assertThat(animator.nextDueNanos().orElseThrow() - end).isBetween(BuddyAnimator.BLINK_MIN_NANOS, BuddyAnimator.BLINK_MAX_NANOS);
    }

    @Test void hoverInterruptsABlinkAndHiddenClearsEverything() {
        var animator = new BuddyAnimator(new Random(5));
        animator.shown(0);
        long blink = animator.nextDueNanos().orElseThrow();
        animator.tick(blink);
        assertThat(animator.frame()).isIn(BuddyFrame.BLINK, BuddyFrame.WINK);
        animator.hoverEntered(blink + 1);
        assertThat(animator.frame()).isEqualTo(BuddyFrame.WAVE_A);
        animator.hidden();
        assertThat(animator.frame()).isEqualTo(BuddyFrame.IDLE);
        assertThat(animator.nextDueNanos()).isEmpty();
        animator.shown(blink + 2);
        assertThat(animator.nextDueNanos()).isPresent();
    }

    @Test void lateTicksCatchUpWithoutSkippingTheDanceOrder() {
        var animator = new BuddyAnimator(new Random(9));
        animator.shown(0);
        animator.hoverEntered(0);
        animator.tick(BuddyAnimator.STEP_NANOS * 2 + 1);
        assertThat(animator.frame()).isEqualTo(BuddyFrame.HOP);
        assertThat(animator.nextDueNanos().orElseThrow()).isEqualTo(BuddyAnimator.STEP_NANOS * 3);
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `./gradlew :jasper-app:test --tests 'dev.jasper.app.BuddyAnimatorTest'`
Expected: compilation FAILS (`BuddyAnimator` missing).

- [ ] **Step 3: Implement**

`jasper-app/src/main/java/dev/jasper/app/BuddyAnimator.java`:

```java
package dev.jasper.app;

import java.util.List;
import java.util.Objects;
import java.util.OptionalLong;
import java.util.Random;
import java.util.concurrent.TimeUnit;

/** Clock-driven idle/hover frame selection; owns no timers and touches no Swing. */
final class BuddyAnimator {
    static final long BLINK_MIN_NANOS = TimeUnit.SECONDS.toNanos(3);
    static final long BLINK_MAX_NANOS = TimeUnit.SECONDS.toNanos(6);
    static final long BLINK_NANOS = TimeUnit.MILLISECONDS.toNanos(120);
    static final long STEP_NANOS = TimeUnit.MILLISECONDS.toNanos(125);
    static final List<BuddyFrame> DANCE = List.of(BuddyFrame.WAVE_A, BuddyFrame.WAVE_B, BuddyFrame.HOP,
        BuddyFrame.LEAN_LEFT, BuddyFrame.LEAN_RIGHT);

    private enum Mode { HIDDEN, IDLE, BLINKING, DANCING }

    private final Random random;
    private Mode mode = Mode.HIDDEN;
    private BuddyFrame frame = BuddyFrame.IDLE;
    private boolean hovered;
    private int step;
    private long due;

    BuddyAnimator(Random random) { this.random = Objects.requireNonNull(random, "random"); }

    BuddyFrame frame() { return frame; }

    OptionalLong nextDueNanos() { return mode == Mode.HIDDEN ? OptionalLong.empty() : OptionalLong.of(due); }

    void shown(long now) {
        mode = Mode.IDLE; frame = BuddyFrame.IDLE; hovered = false;
        scheduleBlink(now);
    }

    void hidden() {
        mode = Mode.HIDDEN; frame = BuddyFrame.IDLE; hovered = false; step = 0;
    }

    void hoverEntered(long now) {
        hovered = true;
        if (mode == Mode.IDLE || mode == Mode.BLINKING) startDance(now);
    }

    void hoverExited(long now) { hovered = false; }

    /** Applies every step whose due time has passed; a late tick never skips a dance frame. */
    void tick(long now) {
        for (int guard = 0; guard < 64 && mode != Mode.HIDDEN && now >= due; guard++) advance();
    }

    private void advance() {
        switch (mode) {
            case IDLE -> {
                frame = random.nextInt(4) == 0 ? BuddyFrame.WINK : BuddyFrame.BLINK;
                mode = Mode.BLINKING; due += BLINK_NANOS;
            }
            case BLINKING -> { frame = BuddyFrame.IDLE; mode = Mode.IDLE; scheduleBlink(due); }
            case DANCING -> {
                step++;
                if (step >= DANCE.size()) {
                    if (!hovered) { frame = BuddyFrame.IDLE; mode = Mode.IDLE; scheduleBlink(due); return; }
                    step = 0;
                }
                frame = DANCE.get(step); due += STEP_NANOS;
            }
            case HIDDEN -> { }
        }
    }

    private void startDance(long now) {
        mode = Mode.DANCING; step = 0; frame = DANCE.getFirst(); due = now + STEP_NANOS;
    }

    private void scheduleBlink(long from) {
        due = from + BLINK_MIN_NANOS + (long) (random.nextDouble() * (BLINK_MAX_NANOS - BLINK_MIN_NANOS));
    }
}
```

- [ ] **Step 4: Run the tests**

Run: `./gradlew :jasper-app:test --tests 'dev.jasper.app.BuddyAnimatorTest'`
Expected: PASS (5 tests).

- [ ] **Step 5: Hygiene check and commit**

Run the hygiene check on both new files. Expected: no output.

```bash
git add jasper-app/src/main/java/dev/jasper/app/BuddyAnimator.java jasper-app/src/test/java/dev/jasper/app/BuddyAnimatorTest.java
git commit -m "feat: add the desk-buddy idle blink and hover dance animator

Co-Authored-By: Claude Fable 5.1 <noreply@anthropic.com>"
```

---

### Task 3: `BuddyPlacement`

**Files:**
- Create: `jasper-app/src/main/java/dev/jasper/app/BuddyPlacement.java`
- Test: `jasper-app/src/test/java/dev/jasper/app/BuddyPlacementTest.java`

**Interfaces:**
- Produces: `final class BuddyPlacement { static final int MARGIN = 24; static Point defaultLocation(Rectangle usable, Dimension size); static Point clamp(Point saved, List<Rectangle> usableScreens, Dimension size) }`

- [ ] **Step 1: Write the failing test**

`jasper-app/src/test/java/dev/jasper/app/BuddyPlacementTest.java`:

```java
package dev.jasper.app;

import java.awt.Dimension;
import java.awt.Point;
import java.awt.Rectangle;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class BuddyPlacementTest {
    private static final Dimension SIZE = new Dimension(84, 96);
    private static final Rectangle MAIN = new Rectangle(0, 25, 1512, 982 - 25 - 70); // menu bar and Dock removed

    @Test void defaultSitsInTheBottomRightOfTheUsableAreaWithAMargin() {
        assertThat(BuddyPlacement.defaultLocation(MAIN, SIZE))
            .isEqualTo(new Point(1512 - 84 - 24, 25 + 887 - 96 - 24));
    }

    @Test void savedPointsThatAreMostlyOnAScreenAreKept() {
        Point saved = new Point(1512 - 30, 400); // 30 px visible of 84: less than half
        assertThat(BuddyPlacement.clamp(new Point(100, 100), List.of(MAIN), SIZE)).isEqualTo(new Point(100, 100));
        assertThat(BuddyPlacement.clamp(new Point(1512 - 42, 400), List.of(MAIN), SIZE)).isEqualTo(new Point(1512 - 42, 400));
        assertThat(BuddyPlacement.clamp(saved, List.of(MAIN), SIZE)).isEqualTo(new Point(1512 - 84, 400));
    }

    @Test void offScreenPointsMoveOntoTheNearestScreenFullyVisible() {
        Rectangle second = new Rectangle(1512, 0, 2560, 1440);
        assertThat(BuddyPlacement.clamp(new Point(-500, -500), List.of(MAIN, second), SIZE)).isEqualTo(new Point(0, 25));
        assertThat(BuddyPlacement.clamp(new Point(5000, 700), List.of(MAIN, second), SIZE))
            .isEqualTo(new Point(1512 + 2560 - 84, 700));
        assertThat(BuddyPlacement.clamp(new Point(2000, 3000), List.of(MAIN, second), SIZE))
            .isEqualTo(new Point(2000, 1440 - 96));
    }

    @Test void withoutScreensTheSavedPointIsReturned() {
        assertThat(BuddyPlacement.clamp(new Point(7, 9), List.of(), SIZE)).isEqualTo(new Point(7, 9));
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `./gradlew :jasper-app:test --tests 'dev.jasper.app.BuddyPlacementTest'`
Expected: compilation FAILS.

- [ ] **Step 3: Implement**

`jasper-app/src/main/java/dev/jasper/app/BuddyPlacement.java`:

```java
package dev.jasper.app;

import java.awt.Dimension;
import java.awt.Point;
import java.awt.Rectangle;
import java.util.List;

/** Screen-geometry rules for the buddy; no AWT devices are queried here. */
final class BuddyPlacement {
    static final int MARGIN = 24;

    private BuddyPlacement() { }

    static Point defaultLocation(Rectangle usable, Dimension size) {
        return new Point(usable.x + usable.width - size.width - MARGIN, usable.y + usable.height - size.height - MARGIN);
    }

    /** Keeps a saved point when at least half the sprite is on some screen; otherwise pulls it fully onto the nearest screen. */
    static Point clamp(Point saved, List<Rectangle> usableScreens, Dimension size) {
        if (usableScreens.isEmpty()) return new Point(saved);
        Rectangle sprite = new Rectangle(saved.x, saved.y, size.width, size.height);
        long half = (long) size.width * size.height / 2;
        Rectangle best = null; long bestArea = -1; double bestDistance = Double.MAX_VALUE;
        for (Rectangle screen : usableScreens) {
            Rectangle overlap = sprite.intersection(screen);
            long area = overlap.isEmpty() ? 0 : (long) overlap.width * overlap.height;
            if (area >= half) return new Point(saved);
            double distance = Point.distance(sprite.getCenterX(), sprite.getCenterY(), screen.getCenterX(), screen.getCenterY());
            if (area > bestArea || (area == bestArea && distance < bestDistance)) { best = screen; bestArea = area; bestDistance = distance; }
        }
        int x = Math.max(best.x, Math.min(saved.x, best.x + best.width - size.width));
        int y = Math.max(best.y, Math.min(saved.y, best.y + best.height - size.height));
        return new Point(x, y);
    }
}
```

- [ ] **Step 4: Run the tests**

Run: `./gradlew :jasper-app:test --tests 'dev.jasper.app.BuddyPlacementTest'`
Expected: PASS (4 tests).

- [ ] **Step 5: Hygiene check and commit**

```bash
git add jasper-app/src/main/java/dev/jasper/app/BuddyPlacement.java jasper-app/src/test/java/dev/jasper/app/BuddyPlacementTest.java
git commit -m "feat: add desk-buddy default placement and screen clamping

Co-Authored-By: Claude Fable 5.1 <noreply@anthropic.com>"
```

---

### Task 4: `BuddyStateFile` and `AppDirs.buddyState()`

**Files:**
- Create: `jasper-app/src/main/java/dev/jasper/app/BuddyStateFile.java`
- Modify: `jasper-app/src/main/java/dev/jasper/app/AppDirs.java` (add `buddyState()` after `commandHistory()`)
- Test: `jasper-app/src/test/java/dev/jasper/app/BuddyStateFileTest.java`, `jasper-app/src/test/java/dev/jasper/app/AppDirsTest.java`

**Interfaces:**
- Produces: `final class BuddyStateFile { static Optional<Point> read(Path file) throws IOException; static void write(Path file, Point location) throws IOException }`
- Produces: `AppDirs.buddyState()` → `root.resolve("buddy.toml")`

- [ ] **Step 1: Write the failing tests**

`jasper-app/src/test/java/dev/jasper/app/BuddyStateFileTest.java`:

```java
package dev.jasper.app;

import java.awt.Point;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class BuddyStateFileTest {
    @TempDir Path directory;

    @Test void roundTripWritesExactTomlAndLeavesNoTemporaryFile() throws Exception {
        Path file = directory.resolve("nested/buddy.toml");
        BuddyStateFile.write(file, new Point(-12, 340));
        assertThat(Files.readString(file)).isEqualTo("version = 1\nx = -12\ny = 340\n");
        assertThat(BuddyStateFile.read(file)).contains(new Point(-12, 340));
        try (var listing = Files.list(file.getParent())) { assertThat(listing).containsExactly(file); }
    }

    @Test void missingFileMeansNoSavedPosition() throws Exception {
        assertThat(BuddyStateFile.read(directory.resolve("missing.toml"))).isEmpty();
    }

    @Test void malformedFilesAreRejectedNotRepaired() throws Exception {
        Path file = directory.resolve("buddy.toml");
        for (String text : List.of(
                "version = 2\nx = 1\ny = 2\n",
                "version = 1\nx = 1\n",
                "version = 1\nx = 1\ny = 2\nz = 3\n",
                "version = 1\nx = 1.5\ny = 2\n",
                "version = 1\nx = \"1\"\ny = 2\n",
                "version = 1\nx = 3000000000\ny = 2\n",
                "version = 1\nx = [\n")) {
            Files.writeString(file, text);
            assertThatThrownBy(() -> BuddyStateFile.read(file)).as("reject %s", text).isInstanceOf(IOException.class);
        }
        Files.writeString(file, " ".repeat(4097));
        assertThatThrownBy(() -> BuddyStateFile.read(file)).isInstanceOf(IOException.class);
        byte[] prefix = "version = 1\nx = 1\ny = 2\n# ".getBytes(StandardCharsets.UTF_8);
        byte[] malformed = java.util.Arrays.copyOf(prefix, prefix.length + 1);
        malformed[malformed.length - 1] = (byte) 0x80;
        Files.write(file, malformed);
        assertThatThrownBy(() -> BuddyStateFile.read(file)).isInstanceOf(IOException.class);
    }
}
```

Add to `jasper-app/src/test/java/dev/jasper/app/AppDirsTest.java` (inside the class; keep existing tests):

```java
    @Test void buddyStateLivesBesideCommandHistory() {
        AppDirs dirs = AppDirs.resolve("Mac OS X", java.util.Map.of(), java.nio.file.Path.of("/Users/example"));
        org.assertj.core.api.Assertions.assertThat(dirs.buddyState())
            .isEqualTo(java.nio.file.Path.of("/Users/example/.config/jasper/buddy.toml"));
    }
```

- [ ] **Step 2: Run the tests to verify they fail**

Run: `./gradlew :jasper-app:test --tests 'dev.jasper.app.BuddyStateFileTest' --tests 'dev.jasper.app.AppDirsTest'`
Expected: compilation FAILS.

- [ ] **Step 3: Implement**

In `AppDirs.java`, after `commandHistory()`:

```java
    Path buddyState() {
        return root.resolve("buddy.toml");
    }
```

`jasper-app/src/main/java/dev/jasper/app/BuddyStateFile.java`:

```java
package dev.jasper.app;

import java.awt.Point;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Optional;
import java.util.Set;
import org.tomlj.Toml;

/** The buddy's saved screen position: strict read, atomic write, never silently repaired. */
final class BuddyStateFile {
    private static final int MAX_BYTES = 4096;

    private BuddyStateFile() { }

    static Optional<Point> read(Path file) throws IOException {
        byte[] bytes;
        try (var input = Files.newInputStream(file)) {
            bytes = input.readNBytes(MAX_BYTES + 1);
        } catch (NoSuchFileException absent) {
            return Optional.empty();
        }
        if (bytes.length > MAX_BYTES) throw new IOException("Buddy state exceeds " + MAX_BYTES + " bytes");
        var parsed = Toml.parse(decode(bytes));
        if (parsed.hasErrors() || !parsed.keySet().equals(Set.of("version", "x", "y"))
                || !Long.valueOf(1).equals(parsed.get("version"))) {
            throw new IOException("Invalid buddy state version or format");
        }
        return Optional.of(new Point(coordinate(parsed.get("x")), coordinate(parsed.get("y"))));
    }

    private static int coordinate(Object value) throws IOException {
        if (value instanceof Long number && number >= Integer.MIN_VALUE && number <= Integer.MAX_VALUE) return number.intValue();
        throw new IOException("Buddy state coordinates must be integers");
    }

    static void write(Path file, Point location) throws IOException {
        Path target = file.toAbsolutePath();
        Files.createDirectories(target.getParent());
        Path temporary = Files.createTempFile(target.getParent(), ".buddy-", ".tmp");
        try {
            Files.writeString(temporary, "version = 1\nx = " + location.x + "\ny = " + location.y + "\n", StandardCharsets.UTF_8);
            try {
                Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException unsupported) {
                Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    private static String decode(byte[] bytes) throws IOException {
        try {
            return StandardCharsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
                .decode(ByteBuffer.wrap(bytes)).toString();
        } catch (CharacterCodingException invalid) {
            throw new IOException("Invalid UTF-8 in buddy state", invalid);
        }
    }
}
```

- [ ] **Step 4: Run the tests**

Run: `./gradlew :jasper-app:test --tests 'dev.jasper.app.BuddyStateFileTest' --tests 'dev.jasper.app.AppDirsTest'`
Expected: PASS.

- [ ] **Step 5: Hygiene check and commit**

```bash
git add jasper-app/src/main/java/dev/jasper/app/BuddyStateFile.java jasper-app/src/main/java/dev/jasper/app/AppDirs.java jasper-app/src/test/java/dev/jasper/app/BuddyStateFileTest.java jasper-app/src/test/java/dev/jasper/app/AppDirsTest.java
git commit -m "feat: persist the desk-buddy position in buddy.toml

Co-Authored-By: Claude Fable 5.1 <noreply@anthropic.com>"
```

---

### Task 5: `buddy.enabled` configuration and an application-level listener

**Files:**
- Modify: `jasper-app/src/main/java/dev/jasper/app/ConfigSnapshot.java`, `ConfigLoader.java`, `ConfigTemplate.java`, `ConfigurationController.java`
- Modify: `config.example.toml`, `docs/configuration.md`
- Test: `jasper-app/src/test/java/dev/jasper/app/ConfigLoaderTest.java`, `ConfigTemplateTest.java`, `ConfigurationControllerTest.java`

**Interfaces:**
- Produces: `ConfigSnapshot.buddyEnabled()` (boolean, default `true`); new canonical 11-component record constructor with `boolean buddyEnabled` last; the existing 10-argument constructor keeps working and passes `true`.
- Produces: `ConfigurationController.onSnapshot(Consumer<ConfigSnapshot> listener)` — replaces the single application listener; called on every accepted state and immediately with the current snapshot.

- [ ] **Step 1: Write the failing tests**

Add to `ConfigLoaderTest.java`:

```java
    @Test void buddyEnabledParsesAndRejectsNonBooleans() {
        var off = parse("[buddy]\nenabled = false\n");
        assertThat(off.rejected()).isFalse();
        assertThat(off.diagnostics()).isEmpty();
        assertThat(off.snapshot().buddyEnabled()).isFalse();
        assertThat(ConfigSnapshot.defaults().buddyEnabled()).isTrue();
        var bad = parse("[buddy]\nenabled = 1\n");
        assertThat(bad.rejected()).isTrue();
        assertThat(bad.snapshot().buddyEnabled()).isTrue();
        assertDiagnostic(bad, "buddy.enabled", 2, 1, ConfigDiagnostic.Severity.ERROR);
        var unknown = parse("[buddy]\nvisible = true\n");
        assertDiagnostic(unknown, "buddy.visible", 2, 1, ConfigDiagnostic.Severity.WARNING);
    }
```

(`parse(String)` and `assertDiagnostic(result, key, line, column, severity)` already exist in that test class.)

In `ConfigTemplateTest.repositoryExampleIsCompleteAndParsesAsBuiltInDefaultsOnBothPlatforms`, after the `ui` assertion add:

```java
        assertThat(toml.getTable("buddy").keySet()).containsExactly("enabled");
```

In `ConfigTemplateTest.uncommentedDefaultsAreValidAndPreserveEveryEffectiveShortcut`, after the `statusBar` assertion add:

```java
            assertThat(all.snapshot().buddyEnabled()).isTrue();
```

Add to `ConfigurationControllerTest.java`:

```java
    @Test void applicationSnapshotListenerReceivesTheCurrentAndEveryLaterSnapshot() throws Exception {
        start("[buddy]\nenabled=false\n");
        List<Boolean> seen = new ArrayList<>();
        edt(() -> controller.onSnapshot(snapshot -> seen.add(snapshot.buddyEnabled())));
        assertThat(seen).containsExactly(false);
        reload("[buddy]\nenabled=true\n");
        assertThat(seen).containsExactly(false, true);
        reload("[buddy]\nenabled='no'\n"); // rejected: the last good snapshot is redelivered
        assertThat(seen).containsExactly(false, true, true);
        edt(() -> controller.onSnapshot(snapshot -> {})); // a later registration replaces the earlier listener
        reload("[buddy]\nenabled=false\n");
        assertThat(seen).containsExactly(false, true, true);
    }
```

- [ ] **Step 2: Run the tests to verify they fail**

Run: `./gradlew :jasper-app:test --tests 'dev.jasper.app.ConfigLoaderTest' --tests 'dev.jasper.app.ConfigTemplateTest' --tests 'dev.jasper.app.ConfigurationControllerTest'`
Expected: compilation FAILS (`buddyEnabled`, `onSnapshot` missing).

- [ ] **Step 3: Extend `ConfigSnapshot`**

Change the record header and add the delegating constructor:

```java
record ConfigSnapshot(int tabHeight, WindowContent.ToolbarMode toolbar, boolean statusBar,
                      FontConfig font, ColorsConfig colors, Map<String, String> keybindings,
                      int columns, int lines, TerminalConfig terminal, UiLookAndFeel laf, boolean buddyEnabled) {
```

Immediately after the compact constructor add:

```java
    ConfigSnapshot(int tabHeight, WindowContent.ToolbarMode toolbar, boolean statusBar,
                   FontConfig font, ColorsConfig colors, Map<String, String> keybindings,
                   int columns, int lines, TerminalConfig terminal, UiLookAndFeel laf) {
        this(tabHeight, toolbar, statusBar, font, colors, keybindings, columns, lines, terminal, laf, true);
    }
```

The existing 9-argument constructor still delegates to the 10-argument one; `defaults()` is unchanged.

- [ ] **Step 4: Extend `ConfigLoader`**

In `FIELDS`, change the root set to `Set.of("window", "font", "colors", "keybindings", "terminal", "ui", "buddy")` and add the entry `List.of("buddy"), Set.of("enabled"),` after the `ui` entry. Add the field `private boolean buddyEnabled = true;` after `statusBar`. In `readField` add, after the `window.status_bar` case:

```java
            case "buddy.enabled" -> buddyEnabled = bool(path, value, buddyEnabled);
```

In `parse()`, pass the value: change `cursorShape, cursorBlink, dimInactivePanes, copyOnSelect, bell, onExit), laf);` to `cursorShape, cursorBlink, dimInactivePanes, copyOnSelect, bell, onExit), laf, buddyEnabled);`.

- [ ] **Step 5: Extend `ConfigTemplate`, the example and the docs**

In `ConfigTemplate.text`, after the `[window]` block's `# Columns: 5-500; …` line and before `[font]`, insert:

```
            [buddy]
            # Show the pixel-art Jasper floating above your windows while a terminal is open; updates live.
            # View > Show Jasper and a right-click on Jasper toggle him for this session only.
            # enabled = true

```

In `config.example.toml`, after the `[window]` table (after `lines = 45`) insert:

```toml

[buddy]
# Live. Show the pixel-art Jasper floating above your windows while a terminal is open.
# View > Show Jasper (also in the command palette) and right-click > Hide Jasper toggle him for the session.
enabled = true
```

In `docs/configuration.md`: add `[buddy]\nenabled = true` to the TOML block under "Supported settings" (after the `[window]` block), add the table row `| \`buddy.enabled\` | \`true\` | Boolean | Live |` after `window.lines`, and add this section before "### Swing look and feel":

```markdown
### Desk buddy

`buddy.enabled` shows the pixel-art Jasper who floats above other windows while at least one
terminal window is open and not minimized. Hover to make him dance; drag him anywhere (the
position is saved in `buddy.toml` beside `config.toml`); click him to bring the last active
terminal window forward. View → Show Jasper, the command palette and right-click → Hide Jasper
toggle him for the current session; a saved change to `buddy.enabled` resets that session choice.
```

- [ ] **Step 6: Add the listener to `ConfigurationController`**

Add the field `private Consumer<ConfigSnapshot> applicationListener = snapshot -> {};` and the method:

```java
    /** One application-level observer for settings no window owns; receives the current snapshot at once. */
    void onSnapshot(Consumer<ConfigSnapshot> listener) {
        requireEdt();
        applicationListener = java.util.Objects.requireNonNull(listener, "listener");
        if (!closed) listener.accept(state.snapshot());
    }
```

In `accept(...)`, after `state = next;` add `applicationListener.accept(next.snapshot());`. In `close()`, after `closed = true;` add `applicationListener = snapshot -> {};`.

- [ ] **Step 7: Run the tests**

Run: `./gradlew :jasper-app:test --tests 'dev.jasper.app.ConfigLoaderTest' --tests 'dev.jasper.app.ConfigTemplateTest' --tests 'dev.jasper.app.ConfigurationControllerTest' --tests 'dev.jasper.app.ExpandedConfigTest'`
Expected: PASS. `ExpandedConfigTest` still compares against the 9-argument constructor, which now defaults `buddyEnabled` to `true`.

- [ ] **Step 8: Hygiene check and commit**

```bash
git add jasper-app/src/main/java/dev/jasper/app/ConfigSnapshot.java jasper-app/src/main/java/dev/jasper/app/ConfigLoader.java jasper-app/src/main/java/dev/jasper/app/ConfigTemplate.java jasper-app/src/main/java/dev/jasper/app/ConfigurationController.java config.example.toml docs/configuration.md jasper-app/src/test/java/dev/jasper/app/ConfigLoaderTest.java jasper-app/src/test/java/dev/jasper/app/ConfigTemplateTest.java jasper-app/src/test/java/dev/jasper/app/ConfigurationControllerTest.java
git commit -m "feat: add the buddy.enabled setting and an application snapshot listener

Co-Authored-By: Claude Fable 5.1 <noreply@anthropic.com>"
```

---

### Task 6: `BuddyVisibility`

**Files:**
- Create: `jasper-app/src/main/java/dev/jasper/app/BuddyVisibility.java`
- Test: `jasper-app/src/test/java/dev/jasper/app/BuddyVisibilityTest.java`

**Interfaces:**
- Produces: `final class BuddyVisibility { void configure(boolean enabled); void toggle(); boolean enabled(); void window(Object key, boolean showing, boolean iconified); void remove(Object key); boolean shown() }`

The spec calls this a pure rule; it is implemented as a small state holder with no Swing so the application stays thin and the rule is headless-testable.

- [ ] **Step 1: Write the failing test**

`jasper-app/src/test/java/dev/jasper/app/BuddyVisibilityTest.java`:

```java
package dev.jasper.app;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class BuddyVisibilityTest {
    @Test void shownOnlyWhenEnabledAndSomeWindowIsShowingAndNotIconified() {
        var visibility = new BuddyVisibility();
        assertThat(visibility.enabled()).isTrue();
        assertThat(visibility.shown()).isFalse();
        visibility.window("a", true, false);
        assertThat(visibility.shown()).isTrue();
        visibility.window("a", true, true);
        assertThat(visibility.shown()).isFalse();
        visibility.window("b", true, false);
        assertThat(visibility.shown()).isTrue();
        visibility.window("b", false, false);
        assertThat(visibility.shown()).isFalse();
        visibility.window("a", true, false);
        visibility.remove("a");
        assertThat(visibility.shown()).isFalse();
    }

    @Test void sessionToggleOverridesTheSavedDefaultUntilTheSavedValueChanges() {
        var visibility = new BuddyVisibility();
        visibility.window("a", true, false);
        visibility.toggle();
        assertThat(visibility.enabled()).isFalse();
        assertThat(visibility.shown()).isFalse();
        visibility.configure(true); // unchanged saved value keeps the session choice
        assertThat(visibility.enabled()).isFalse();
        visibility.configure(false); // changed saved value resets the session choice
        assertThat(visibility.enabled()).isFalse();
        visibility.toggle();
        assertThat(visibility.enabled()).isTrue();
        assertThat(visibility.shown()).isTrue();
        visibility.configure(true);
        assertThat(visibility.enabled()).isTrue();
        visibility.toggle();
        assertThat(visibility.shown()).isFalse();
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `./gradlew :jasper-app:test --tests 'dev.jasper.app.BuddyVisibilityTest'`
Expected: compilation FAILS.

- [ ] **Step 3: Implement**

`jasper-app/src/main/java/dev/jasper/app/BuddyVisibility.java`:

```java
package dev.jasper.app;

import java.util.LinkedHashMap;
import java.util.Map;

/** Saved default, session toggle and terminal-window states → whether the buddy is on screen. */
final class BuddyVisibility {
    private record WindowState(boolean showing, boolean iconified) { }

    private final Map<Object, WindowState> windows = new LinkedHashMap<>();
    private boolean configured = true;
    private Boolean session;

    /** Applies the saved default; a changed saved value discards the session choice. */
    void configure(boolean enabled) {
        if (configured != enabled) session = null;
        configured = enabled;
    }

    void toggle() { session = !enabled(); }

    boolean enabled() { return session != null ? session : configured; }

    void window(Object key, boolean showing, boolean iconified) { windows.put(key, new WindowState(showing, iconified)); }

    void remove(Object key) { windows.remove(key); }

    boolean shown() {
        if (!enabled()) return false;
        for (WindowState state : windows.values()) if (state.showing() && !state.iconified()) return true;
        return false;
    }
}
```

- [ ] **Step 4: Run the tests**

Run: `./gradlew :jasper-app:test --tests 'dev.jasper.app.BuddyVisibilityTest'`
Expected: PASS (2 tests).

- [ ] **Step 5: Hygiene check and commit**

```bash
git add jasper-app/src/main/java/dev/jasper/app/BuddyVisibility.java jasper-app/src/test/java/dev/jasper/app/BuddyVisibilityTest.java
git commit -m "feat: add the desk-buddy visibility rule

Co-Authored-By: Claude Fable 5.1 <noreply@anthropic.com>"
```

---

### Task 7: `view.buddy` command in the View menu and palette

**Files:**
- Modify: `jasper-app/src/main/java/dev/jasper/app/WindowContent.java` (hook fields), `WindowCommands.java` (registration and title refresh), `WindowChrome.java` (checkbox)
- Test: `jasper-app/src/test/java/dev/jasper/app/WindowCommandsTest.java`

**Interfaces:**
- Produces on `WindowContent`: `Runnable onToggleBuddy = () -> {};` and `java.util.function.BooleanSupplier buddyShown = () -> false;` (settable fields, same style as `onTitle`).
- Produces: view command id `view.buddy`, label `Show Jasper`, title `Hide Jasper` when `buddyShown` is true and `Show Jasper` otherwise, `Action.SELECTED_KEY` mirrors `buddyShown`.

- [ ] **Step 1: Write the failing test**

Add to `WindowCommandsTest.java`:

```java
    @Test void showJasperCommandTogglesThroughTheOwnerHookAndReportsItsState() throws Exception {
        DesktopTestSupport.edt(() -> {
            try (var owner = DesktopTestSupport.content(DesktopTestSupport.launcher(new ArrayDeque<>()))) {
                boolean[] shown = {true}; int[] toggles = {0};
                owner.buddyShown = () -> shown[0];
                owner.onToggleBuddy = () -> { shown[0] = !shown[0]; toggles[0]++; };
                owner.updateActions();
                var command = owner.commands().entries().stream().map(CommandSearch.Entry::command)
                    .filter(c -> c.id().equals("view.buddy")).findFirst().orElseThrow();
                assertThat(command.title()).isEqualTo("Hide Jasper");
                assertThat(command.action().getValue(Action.SELECTED_KEY)).isEqualTo(true);
                assertThat(CommandSearch.find(owner.commands().entries(), "jasper", java.util.List.of()).stream().map(e -> e.command().id()))
                    .contains("view.buddy");
                var viewMenu = owner.menuBar().getMenu(2);
                var item = java.util.Arrays.stream(viewMenu.getMenuComponents())
                    .filter(javax.swing.JCheckBoxMenuItem.class::isInstance).map(javax.swing.JCheckBoxMenuItem.class::cast)
                    .filter(box -> box.getAction() == command.action()).findFirst().orElseThrow();
                assertThat(item.isSelected()).isTrue();
                owner.setActive(true);
                command.action().actionPerformed(new java.awt.event.ActionEvent(owner, 0, "test"));
                assertThat(toggles[0]).isEqualTo(1);
                assertThat(command.title()).isEqualTo("Show Jasper");
                assertThat(item.isSelected()).isFalse();
            }
        });
    }
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `./gradlew :jasper-app:test --tests 'dev.jasper.app.WindowCommandsTest'`
Expected: compilation FAILS (`buddyShown`, `onToggleBuddy` missing).

- [ ] **Step 3: Add the hooks to `WindowContent`**

Next to `Consumer<String> onTitle = title -> {};` add:

```java
    /** Application-owned buddy toggle; the window only forwards and displays state. */
    Runnable onToggleBuddy = () -> {};
    java.util.function.BooleanSupplier buddyShown = () -> false;
```

In `close()`, where the other callbacks are neutralized (`onTitle = title -> {}; onError = message -> {}; onMinimumSizeChanged = () -> {};`), add `onToggleBuddy = () -> {}; buddyShown = () -> false;`.

- [ ] **Step 4: Register the command in `WindowCommands`**

After the `view.status_bar` registration line add:

```java
        add(registry, "view.buddy", "Show Jasper", "Hide Jasper", () -> { owner.onToggleBuddy.run(); owner.updateActions(); });
```

In `refresh()`, after the `view.status_bar` `SELECTED_KEY` line add:

```java
        boolean buddy = owner.buddyShown.getAsBoolean();
        view("view.buddy").putValue(Command.TITLE, buddy ? "Hide Jasper" : "Show Jasper");
        view("view.buddy").putValue(Action.SELECTED_KEY, buddy);
```

Extend the keyword list so the palette finds it by mascot name: in `add(...)`, the keywords are derived from the id (`view buddy`). Add an overload-free tweak: change the `add` body's registration line to

```java
        var keywords = new java.util.ArrayList<>(List.of(id.replace('.', ' ').replace('_', ' ')));
        if (id.equals("view.buddy")) keywords.addAll(List.of("jasper", "mascot", "turtle", "desk buddy"));
        registrations.add(registry.register(new Command(id, action, keywords)));
```

- [ ] **Step 5: Add the checkbox to `WindowChrome`**

Add the field `private final JCheckBoxMenuItem buddyVisible = new JCheckBoxMenuItem("Show Jasper", false);` beside `statusVisible`. After `view.add(statusVisible);` add `view.add(buddyVisible);` and after `statusVisible.setAction(...)` add `buddyVisible.setAction(owner.windowCommands().view("view.buddy"));`. Swing keeps a `JCheckBoxMenuItem` selected state in sync with its action's `SELECTED_KEY`, so no further wiring is needed.

- [ ] **Step 6: Run the tests**

Run: `./gradlew :jasper-app:test --tests 'dev.jasper.app.WindowCommandsTest' --tests 'dev.jasper.app.CommandPaletteShortcutsTest' --tests 'dev.jasper.app.WindowChromeTest'`
Expected: PASS. If `item.isSelected()` does not follow `SELECTED_KEY`, call `owner.updateActions()` before the assertion inside the test (the command callback already does).

- [ ] **Step 7: Hygiene check and commit**

```bash
git add jasper-app/src/main/java/dev/jasper/app/WindowContent.java jasper-app/src/main/java/dev/jasper/app/WindowCommands.java jasper-app/src/main/java/dev/jasper/app/WindowChrome.java jasper-app/src/test/java/dev/jasper/app/WindowCommandsTest.java
git commit -m "feat: add the Show Jasper view command and menu checkbox

Co-Authored-By: Claude Fable 5.1 <noreply@anthropic.com>"
```

---

### Task 8: `BuddyWindow`, application ownership and the manual checklist

**Files:**
- Create: `jasper-app/src/main/java/dev/jasper/app/BuddyWindow.java`
- Modify: `jasper-app/src/main/java/dev/jasper/app/JasperApplication.java`, `TerminalWindow.java`, `Main.java`
- Create: `docs/superpowers/plans/2026-09-14-jasper-desk-buddy-manual-check.md`
- Modify: `docs/STATUS.md`

**Interfaces:**
- Consumes: `BuddySprite.load()`, `BuddyAnimator`, `BuddyPlacement`, `BuddyStateFile`, `BuddyVisibility`, `ConfigurationController.onSnapshot`, `ConfigSnapshot.buddyEnabled()`, `WindowContent.onToggleBuddy/buddyShown`, `AppDirs.buddyState()`.
- Produces: `final class BuddyWindow { static BuddyWindow create(Path stateFile, Runnable raiseTerminal, Runnable toggle); void show(); void hide(); void dispose() }` — `create` returns `null` (after logging once) when the toolkit cannot host it.
- Produces on `JasperApplication`: constructor `JasperApplication(ConfigService, ShellLauncher, CommandHistory, Path buddyStateFile)`; package methods `void windowStateChanged(TerminalWindow, boolean showing, boolean iconified)`, `void windowActivated(TerminalWindow)`, `void toggleBuddy()`, `boolean buddyEnabled()`.
- Produces on `TerminalWindow`: `void toFront()`.

No unit test constructs `BuddyWindow` (headless). The verification for this task is compilation, the whole suite, hygiene, and the user's desktop checklist.

- [ ] **Step 1: Write `BuddyWindow`**

`jasper-app/src/main/java/dev/jasper/app/BuddyWindow.java`:

```java
package dev.jasper.app;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GraphicsDevice;
import java.awt.GraphicsEnvironment;
import java.awt.Insets;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.Toolkit;
import java.awt.Window;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import javax.swing.JComponent;
import javax.swing.JMenuItem;
import javax.swing.JPopupMenu;
import javax.swing.JWindow;
import javax.swing.SwingUtilities;
import javax.swing.Timer;

/** The floating translucent utility window; every rule lives in the Buddy* helpers it drives. */
final class BuddyWindow {
    private static final System.Logger LOG = System.getLogger(BuddyWindow.class.getName());
    private static final int SCALE = 2;
    private static final int DRAG_THRESHOLD = 3;

    private final JWindow window = new JWindow();
    private final BuddySprite sprite;
    private final BuddyAnimator animator = new BuddyAnimator(new Random());
    private final Path stateFile;
    private final Runnable raiseTerminal;
    private final Runnable toggle;
    private final Timer timer = new Timer(1, event -> tick());
    private Point pressScreen;
    private Point pressOrigin;
    private boolean dragged;
    private boolean disposed;

    private BuddyWindow(BuddySprite sprite, Path stateFile, Runnable raiseTerminal, Runnable toggle) {
        this.sprite = sprite; this.stateFile = stateFile; this.raiseTerminal = raiseTerminal; this.toggle = toggle;
        timer.setRepeats(false);
        window.setType(Window.Type.UTILITY);
        window.setAlwaysOnTop(true);
        window.setFocusableWindowState(false);
        window.setBackground(new Color(0, 0, 0, 0));
        JComponent canvas = new JComponent() {
            @Override protected void paintComponent(Graphics g) {
                sprite.paint((Graphics2D) g, animator.frame(), SCALE, 0, 0);
            }
        };
        canvas.setOpaque(false);
        Dimension size = BuddySprite.size(SCALE);
        canvas.setPreferredSize(size);
        window.setContentPane(canvas);
        window.pack();
        window.setSize(size);
        window.setLocation(initialLocation(size));
        MouseAdapter mouse = new MouseAdapter() {
            @Override public void mouseEntered(MouseEvent event) { animator.hoverEntered(System.nanoTime()); paintAndSchedule(); }
            @Override public void mouseExited(MouseEvent event) { animator.hoverExited(System.nanoTime()); }
            @Override public void mousePressed(MouseEvent event) {
                if (event.isPopupTrigger()) { popup(event); return; }
                pressScreen = event.getLocationOnScreen(); pressOrigin = window.getLocation(); dragged = false;
            }
            @Override public void mouseDragged(MouseEvent event) {
                if (pressScreen == null) return;
                Point now = event.getLocationOnScreen();
                int dx = now.x - pressScreen.x, dy = now.y - pressScreen.y;
                if (!dragged && Math.abs(dx) < DRAG_THRESHOLD && Math.abs(dy) < DRAG_THRESHOLD) return;
                dragged = true;
                window.setLocation(pressOrigin.x + dx, pressOrigin.y + dy);
            }
            @Override public void mouseReleased(MouseEvent event) {
                if (event.isPopupTrigger()) { popup(event); pressScreen = null; return; }
                if (pressScreen == null) return;
                pressScreen = null;
                if (dragged) save(window.getLocation());
                else if (SwingUtilities.isLeftMouseButton(event)) raiseTerminal.run();
            }
        };
        canvas.addMouseListener(mouse);
        canvas.addMouseMotionListener(mouse);
    }

    /** Null when headless or the toolkit lacks always-on-top or per-pixel translucency; logs once. */
    static BuddyWindow create(Path stateFile, Runnable raiseTerminal, Runnable toggle) {
        if (GraphicsEnvironment.isHeadless()) return null;
        GraphicsDevice device = GraphicsEnvironment.getLocalGraphicsEnvironment().getDefaultScreenDevice();
        if (!Toolkit.getDefaultToolkit().isAlwaysOnTopSupported()
                || !device.isWindowTranslucencySupported(GraphicsDevice.WindowTranslucency.PERPIXEL_TRANSLUCENT)) {
            LOG.log(System.Logger.Level.WARNING, "Desk buddy disabled: the toolkit lacks always-on-top or per-pixel translucency");
            return null;
        }
        BuddySprite sprite;
        try { sprite = BuddySprite.load(); }
        catch (IllegalStateException failure) {
            LOG.log(System.Logger.Level.WARNING, "Desk buddy disabled: sprite unavailable", failure);
            return null;
        }
        return new BuddyWindow(sprite, stateFile, raiseTerminal, toggle);
    }

    void show() {
        if (disposed || window.isVisible()) return;
        animator.shown(System.nanoTime());
        window.setVisible(true);
        paintAndSchedule();
    }

    void hide() {
        if (disposed) return;
        timer.stop();
        animator.hidden();
        window.setVisible(false);
    }

    void dispose() {
        if (disposed) return;
        disposed = true;
        timer.stop();
        window.dispose();
    }

    private void tick() {
        if (disposed || !window.isVisible()) return;
        animator.tick(System.nanoTime());
        paintAndSchedule();
    }

    private void paintAndSchedule() {
        window.getContentPane().repaint();
        timer.stop();
        animator.nextDueNanos().ifPresent(due -> {
            long millis = Math.max(1, (due - System.nanoTime()) / 1_000_000);
            timer.setInitialDelay((int) Math.min(Integer.MAX_VALUE, millis));
            timer.start();
        });
    }

    private void popup(MouseEvent event) {
        JPopupMenu menu = new JPopupMenu();
        JMenuItem hide = new JMenuItem("Hide Jasper");
        hide.addActionListener(ignored -> toggle.run());
        menu.add(hide);
        menu.show(event.getComponent(), event.getX(), event.getY());
    }

    private Point initialLocation(Dimension size) {
        List<Rectangle> screens = usableScreens();
        Rectangle primary = screens.isEmpty() ? new Rectangle(0, 0, 1280, 800) : screens.getFirst();
        Point fallback = BuddyPlacement.defaultLocation(primary, size);
        if (stateFile == null) return fallback;
        try {
            return BuddyStateFile.read(stateFile).map(saved -> BuddyPlacement.clamp(saved, screens, size)).orElse(fallback);
        } catch (IOException failure) {
            LOG.log(System.Logger.Level.WARNING, "Ignoring unreadable buddy state " + stateFile, failure);
            return fallback;
        }
    }

    private void save(Point location) {
        if (stateFile == null) return;
        try { BuddyStateFile.write(stateFile, location); }
        catch (IOException failure) { LOG.log(System.Logger.Level.WARNING, "Could not save buddy position to " + stateFile, failure); }
    }

    /** Default screen first, each reduced by its Dock/menu/taskbar insets. */
    private static List<Rectangle> usableScreens() {
        var environment = GraphicsEnvironment.getLocalGraphicsEnvironment();
        var screens = new ArrayList<Rectangle>();
        GraphicsDevice primary = environment.getDefaultScreenDevice();
        for (GraphicsDevice device : environment.getScreenDevices()) {
            var configuration = device.getDefaultConfiguration();
            Rectangle bounds = new Rectangle(configuration.getBounds());
            Insets insets = Toolkit.getDefaultToolkit().getScreenInsets(configuration);
            bounds.x += insets.left; bounds.y += insets.top;
            bounds.width -= insets.left + insets.right; bounds.height -= insets.top + insets.bottom;
            if (device == primary) screens.addFirst(bounds); else screens.add(bounds);
        }
        return screens;
    }
}
```

- [ ] **Step 2: Report lifecycle events from `TerminalWindow`**

Replace the `events` adapter with:

```java
    private final WindowAdapter events = new WindowAdapter() {
        @Override public void windowClosing(WindowEvent event) { close(); }
        @Override public void windowActivated(WindowEvent event) { setActive(true); application.windowActivated(TerminalWindow.this); }
        @Override public void windowDeactivated(WindowEvent event) { setActive(false); }
        @Override public void windowOpened(WindowEvent event) { reportState(); }
        @Override public void windowIconified(WindowEvent event) { reportState(); }
        @Override public void windowDeiconified(WindowEvent event) { reportState(); }
    };
```

Add these methods after `setActive`:

```java
    private void reportState() {
        application.windowStateChanged(this, frame.isShowing(),
            (frame.getExtendedState() & java.awt.Frame.ICONIFIED) != 0);
    }

    void toFront() {
        if ((frame.getExtendedState() & java.awt.Frame.ICONIFIED) != 0) frame.setExtendedState(frame.getExtendedState() & ~java.awt.Frame.ICONIFIED);
        frame.toFront(); frame.requestFocus();
        if (content.currentTab() != null) content.currentTab().focusTerminal();
    }
```

In the constructor, after `content.onTitle = …;` add:

```java
        content.onToggleBuddy = application::toggleBuddy;
        content.buddyShown = application::buddyEnabled;
```

In `show()`, after `frame.setVisible(true);` add `reportState();`. In `close()`, `application.windowClosed(this)` already runs; the application removes the window state there.

- [ ] **Step 3: Own the buddy in `JasperApplication`**

Add fields:

```java
    private final BuddyVisibility buddyVisibility = new BuddyVisibility();
    private final Path buddyStateFile;
    private BuddyWindow buddy;
    private boolean buddyUnavailable;
    private TerminalWindow lastActive;
```

Change the 3-argument constructor to delegate and add the new one:

```java
    JasperApplication(ConfigService service, ShellLauncher suppliedLauncher, CommandHistory history) {
        this(service, suppliedLauncher, history, null);
    }

    /** {@code buddyStateFile} may be null: the buddy then starts in the default corner and forgets drags. */
    JasperApplication(ConfigService service, ShellLauncher suppliedLauncher, CommandHistory history, Path buddyStateFile) {
        this.history = history;
        this.suppliedLauncher = suppliedLauncher;
        this.buddyStateFile = buddyStateFile;
        configuration = service == null ? null : new ConfigurationController(themes, service);
        if (configuration != null) configuration.onSnapshot(snapshot -> {
            buddyVisibility.configure(snapshot.buddyEnabled()); syncBuddy();
        });
        if (supportsNativeQuit()) Desktop.getDesktop().setQuitHandler((event, response) -> {
            // Cancel the native immediate JVM exit; pane close owns bounded child cleanup.
            response.cancelQuit(); SwingUtilities.invokeLater(this::quit);
        });
    }
```

(Move the existing `history`/`suppliedLauncher`/`configuration`/quit-handler lines into the new constructor; the old one only delegates.)

Add the buddy methods after `windowClosed`, and extend `windowClosed`/`shutdown`:

```java
    void windowClosed(TerminalWindow window) {
        windows.remove(window);
        buddyVisibility.remove(window);
        if (lastActive == window) lastActive = null;
        if (windows.isEmpty()) requestShutdown();
        else syncBuddy();
    }

    void windowStateChanged(TerminalWindow window, boolean showing, boolean iconified) {
        if (!windows.contains(window)) return;
        buddyVisibility.window(window, showing, iconified);
        syncBuddy();
    }

    void windowActivated(TerminalWindow window) { if (windows.contains(window)) lastActive = window; }

    void toggleBuddy() { buddyVisibility.toggle(); syncBuddy(); for (TerminalWindow window : windows) window.content().updateActions(); }

    boolean buddyEnabled() { return buddyVisibility.enabled(); }

    private void raiseTerminal() {
        TerminalWindow target = lastActive != null && windows.contains(lastActive) ? lastActive
            : windows.isEmpty() ? null : windows.iterator().next();
        if (target != null) target.toFront();
    }

    private void syncBuddy() {
        if (quitting || stopped || buddyUnavailable) return;
        if (buddyVisibility.shown()) {
            if (buddy == null) {
                buddy = BuddyWindow.create(buddyStateFile, this::raiseTerminal, this::toggleBuddy);
                if (buddy == null) { buddyUnavailable = true; return; }
            }
            buddy.show();
        } else if (buddy != null) buddy.hide();
    }
```

In `shutdown()`, after `launches.shutdown();` add `if (buddy != null) buddy.dispose();`.

- [ ] **Step 4: Pass the state file from `Main`**

In `Main.main`, change `application = new JasperApplication(service, null, history);` to `application = new JasperApplication(service, null, history, dirs.buddyState());`.

- [ ] **Step 5: Compile and run the whole suite**

Run: `./gradlew check`
Expected: BUILD SUCCESSFUL; read `jasper-app/build/test-results/test/*.xml` totals: all new Buddy tests present, zero failures/errors, the one known `FontSetTest` skip. Existing `JasperApplication`-based tests still use the 3-argument constructor and see no buddy because they are headless (`BuddyWindow.create` returns null and marks it unavailable).

- [ ] **Step 6: Hygiene**

Run the Python hygiene check with no arguments (all of `jasper-terminal/src`) and again with `jasper-app/src/main/java/dev/jasper/app/*.java jasper-app/src/test/java/dev/jasper/app/*.java`. Expected: no output. Run `git diff --check`. Expected: no output.

- [ ] **Step 7: Write the manual checklist**

`docs/superpowers/plans/2026-09-14-jasper-desk-buddy-manual-check.md`:

```markdown
# Desk buddy — native acceptance checklist (user-run)

Run the app from the `claude/desk-buddy` branch (`./gradlew :jasper-app:run`). Tick each item.

- [ ] Jasper appears in the bottom-right corner above the Dock when the first window opens, 84 × 96 px, crisp 2-px art pixels, transparent background (no square behind him).
- [ ] He stays above other apps' windows (click into Safari/Finder over him: he remains on top).
- [ ] He is absent from the Dock and from Cmd-Tab.
- [ ] Idle: he blinks every few seconds; roughly one blink in four is a wink.
- [ ] Hover: he waves, hops and leans in a loop; moving the pointer away finishes the cycle and he returns to idle.
- [ ] Drag: he follows the pointer; after release, quit and relaunch — he reappears where you left him (`~/.config/jasper/buddy.toml` holds `x`/`y`).
- [ ] Click (no drag): the most recently active Jasper window comes to the front and gets focus; the terminal keeps keyboard focus at all times (typing never goes to Jasper).
- [ ] Right-click → Hide Jasper hides him; View → Show Jasper shows him again and the checkbox reflects the state; Cmd+K "Show Jasper" also toggles.
- [ ] Minimize the only window: he disappears; restore it: he returns. With two windows, minimizing one keeps him.
- [ ] Close the last window: he disappears with the app.
- [ ] `buddy.enabled = false` in `config.toml` hides him within a second; `true` brings him back and resets any session toggle.
- [ ] External display: drag him onto it, quit, unplug the display, relaunch — he is pulled back onto the built-in screen.

Record findings and the date below.
```

- [ ] **Step 8: Update `docs/STATUS.md`**

Insert at the top of the file, before the first bold paragraph:

```markdown
**Desk buddy (2026-09-14):** On `claude/desk-buddy` (from main `5bc288f`), the pixel-art Jasper desk buddy is implemented: a translucent always-on-top utility window that blinks while idle, dances on hover, drags with a remembered position (`buddy.toml`), raises the last active terminal on click, and shows only while a terminal window is open and not minimized. `buddy.enabled` (live) plus View → Show Jasper / palette / right-click toggle him. Sprite pipeline: `packaging/buddy/jasper-buddy.ase` (LibreSprite master) → committed PNG strip; the bootstrap `generate.py` drew the first frames. `./gradlew check` passed (see the plan for counts). Speech bubbles/status messages are deferred. Desktop acceptance is user-run: [checklist](superpowers/plans/2026-09-14-jasper-desk-buddy-manual-check.md). [Design](superpowers/specs/2026-09-14-jasper-desk-buddy-design.md), [plan](superpowers/plans/2026-09-14-jasper-desk-buddy.md). No GUI, merge or push.

```

Replace "(see the plan for counts)" with the actual totals from Step 5.

- [ ] **Step 9: Commit**

```bash
git add jasper-app/src/main/java/dev/jasper/app/BuddyWindow.java jasper-app/src/main/java/dev/jasper/app/JasperApplication.java jasper-app/src/main/java/dev/jasper/app/TerminalWindow.java jasper-app/src/main/java/dev/jasper/app/Main.java docs/superpowers/plans/2026-09-14-jasper-desk-buddy-manual-check.md docs/STATUS.md
git commit -m "feat: float the Jasper desk buddy above the desktop while a terminal is open

Co-Authored-By: Claude Fable 5.1 <noreply@anthropic.com>"
```

---

## Deviations log

Record here any departure from the task text (and mirror it in `docs/STATUS.md`):

- Task 6: `BuddyVisibility` is a small state holder (saved default, session choice, window map) rather than a static function, so `JasperApplication` stays thin and the rule is testable. Approved by this plan.
- Task 4: `CommandHistoryFile` and `BuddyStateFile` share a new package-private `TomlStateFile` helper (bounded strict UTF-8 read, atomic temp-then-move write) instead of duplicating that logic; behaviour and existing tests unchanged. Controller ruling after the task review.
- Task 8: `TerminalWindow` assigns `content.onToggleBuddy`/`content.buddyEnabled` immediately after constructing `WindowContent`, before `configuration.register(content)`, because `register` refreshes actions and would otherwise read the stub. Supersedes the plan's "after `content.onTitle`" placement.
- Task 8: `syncBuddy()` refreshes every window's actions on each non-quitting call (config path and toggle path alike), and only quitting/stopped short-circuit it; a buddy failure latches `buddyUnavailable` and never blocks configuration delivery. `paintComponent` clears the translucent canvas with `AlphaComposite.Clear` before painting.
- Task 8: the headless branch of `BuddyWindow.create` returns null without logging (the spec asks for one warning); headless is the test path and a warning there would be noise.
- Follow-up (user request, 2026-09-14): autonomous idle timeline (sit at 20 s, sleep in shell with Zs at 60 s), one-second hover greeting instead of a loop, double-click raises the app; six new frames; `hoverExited` removed.
