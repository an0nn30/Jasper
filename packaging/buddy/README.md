# Jasper desk buddy sprite

Pixel-art Jasper for the floating desk buddy: 42 × 48 art pixels per frame, drawn at
2 logical px per art pixel by the app (84 × 96 window, 4 device px per art pixel on Retina).

- `jasper-buddy.ase` — editable master for [LibreSprite](https://libresprite.github.io). One RGBA layer, seventeen frames.
- `../../jasper-app/src/main/resources/dev/jasper/app/buddy/jasper-buddy.png` — runtime strip, 714 × 48, frames left to right.
- `generate.py` — **the source of truth** for both files (Python 3 + Pillow). Every frame so far has
  been drawn here, never by hand: rerunning it reproduces the committed PNG byte for byte. Change the
  art here and rerun, rather than editing the `.ase` and stranding the script.

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
| 8 | `SIT` | Sitting: body a pixel lower, head slumped, feet out in front of the shell |
| 9 | `SIT_BLINK` | Sitting with both eyes shut |
| 10 | `TUCK` | Retracted: no limbs, only the top of the head above the shell |
| 11 | `SLEEP_A` | Empty shell, two Zs low |
| 12 | `SLEEP_B` | Empty shell, the Zs risen and drifted right |
| 13 | `SLEEP_C` | Empty shell, the big Z at the top and a new small Z below |
| 14 | `SPARKLE_A` | Spawn overlay: stars only, no body (painted on top of another frame) |
| 15 | `SPARKLE_B` | Spawn overlay, stars in different places |
| 16 | `SPARKLE_C` | Spawn overlay, stars in different places again |
| 17 | `TYPE_A` | Sitting with a laptop in his lap, left hand raised off the keys |
| 18 | `TYPE_B` | As A, right hand raised instead |
| 19 | `TYPE_REST` | As A with both hands down, the beat between bursts |

Frames 17–19 are the working animation, cycled while a command has been running past the
notification threshold. Frames 0–13 are poses. Frames 14–16 are overlays: transparent except for the stars, cycled on top of
the body while he fades in at spawn, so they never replace a pose.

## Palette (from `../icons/jasper.svg`)

| Role | Hex |
|---|---|
| Outline, pupils, glasses | `#332f27` |
| Laptop screen | `#1e222a` |
| Laptop prompt text | `#7fd9a8` |
| Laptop key deck | `#5a626e` |
| Skin | `#a7ae70` |
| Skin highlight | `#c9ce93` |
| Shell rim | `#d8c49c` |
| Rim shade | `#b8a278` |
| Belly | `#eee0bd` |
| Belly lines | `#ab9670` |
| Lenses | `#f7efd7` |
| Eye glint | `#ffffff` |
| Spawn star | `#fff3b0` |
| Spawn star highlight | `#ffffff` |

## Editing and exporting

Open `jasper-buddy.ase` in LibreSprite, edit, save, then export the runtime strip:

```sh
/Applications/libresprite.app/Contents/MacOS/libresprite --batch packaging/buddy/jasper-buddy.ase \
    --sheet jasper-app/src/main/resources/dev/jasper/app/buddy/jasper-buddy.png
```

Keep seventeen frames of 42 × 48 with a transparent left, right and bottom margin;
`BuddySpriteTest` fails the build otherwise. Ordinary Gradle builds need neither
LibreSprite nor Python.

**Verified 2026-09-14:** `libresprite --batch packaging/buddy/jasper-buddy.ase --sheet <out>.png`
produced no output and no window, and did not exit within 60 seconds; it was killed
(`kill -9`) rather than retried, per the project's unattended-agent rules. No exported
PNG was produced to compare against the committed strip. The `.ase` master round-trips
correctly through `generate.py`'s own reader/writer (`generate.py`'s `main()` re-reads
the `.ase` it just wrote and asserts it matches the PNG pixel-for-pixel, which passed).
The committed `jasper-buddy.png` (714 × 48, no padding, written directly by `generate.py`)
remains authoritative for the runtime resource; a human with a GUI session should
re-attempt the LibreSprite batch export if the master is hand-edited later.

**Rerun 2026-09-14:** the three spawn sparkle overlays were added by rerunning `generate.py`, which
was safe because the master had still not been hand-edited in LibreSprite. The first fourteen cells
of the regenerated PNG are byte-identical to the previous strip.
