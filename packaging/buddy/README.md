# Jasper desk buddy sprite

Pixel-art Jasper for the floating desk buddy: 42 × 48 art pixels per frame, drawn at
2 logical px per art pixel by the app (84 × 96 window, 4 device px per art pixel on Retina).

- `jasper-buddy.ase` — editable master for [LibreSprite](https://libresprite.github.io). One RGBA layer, fourteen frames.
- `../../jasper-app/src/main/resources/dev/jasper/app/buddy/jasper-buddy.png` — runtime strip, 588 × 48, frames left to right.
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
| 8 | `SIT` | Sitting: body a pixel lower, no standing legs, feet out sideways |
| 9 | `SIT_BLINK` | Sitting with both eyes shut |
| 10 | `TUCK` | Retracted: no limbs, only the top of the head above the shell |
| 11 | `SLEEP_A` | Empty shell, two Zs low |
| 12 | `SLEEP_B` | Empty shell, the Zs risen and drifted right |
| 13 | `SLEEP_C` | Empty shell, the big Z at the top and a new small Z below |

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

Keep fourteen frames of 42 × 48 with a transparent left, right and bottom margin;
`BuddySpriteTest` fails the build otherwise. Ordinary Gradle builds need neither
LibreSprite nor Python.

**Verified 2026-09-14:** `libresprite --batch packaging/buddy/jasper-buddy.ase --sheet <out>.png`
produced no output and no window, and did not exit within 60 seconds; it was killed
(`kill -9`) rather than retried, per the project's unattended-agent rules. No exported
PNG was produced to compare against the committed strip. The `.ase` master round-trips
correctly through `generate.py`'s own reader/writer (`generate.py`'s `main()` re-reads
the `.ase` it just wrote and asserts it matches the PNG pixel-for-pixel, which passed).
The committed `jasper-buddy.png` (588 × 48, no padding, written directly by `generate.py`)
remains authoritative for the runtime resource; a human with a GUI session should
re-attempt the LibreSprite batch export if the master is hand-edited later.
