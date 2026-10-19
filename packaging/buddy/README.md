# Jasper desk buddy sprite

Pixel-art Jasper for the floating desk buddy: 42 × 48 art pixels per frame, drawn at
2 logical px per art pixel by Buddy (84 × 96 window, 4 device px per art pixel at 2× display scale).

- [jasper-buddy.ase](jasper-buddy.ase) — editable master for [LibreSprite](https://libresprite.github.io). One RGBA layer, 20 frames of 42 × 48.
- [jasper-buddy.png](../../jasper-buddy/src/main/resources/dev/jasper/buddy/internal/presentation/jasper-buddy.png) — runtime strip, 840 × 48, 20 frames left to right with no inter-frame padding.
- [generate.py](generate.py) — programmatic artwork and exporter (Python 3 + Pillow).
  Prefer editing its drawing functions and regenerating both artifacts together. Running
  it without `--verify-only` overwrites the master and runtime strip, discarding hand edits.
- [jasper-character-reference.svg](jasper-character-reference.svg) — the silver character
  artwork the sprite's colours follow.

The runtime resource belongs to `jasper-buddy`, beside
[BuddySprite](../../jasper-buddy/src/main/java/dev/jasper/buddy/internal/presentation/BuddySprite.java),
which slices it into one 42 × 48 cell per `BuddyFrame` constant and refuses a sheet of
any other size; `BuddyCompanion.show()` then returns false. Gradle builds load the
committed artwork and run no generator.

## Frames

Column order is fixed by [BuddyFrame](../../jasper-buddy/src/main/java/dev/jasper/buddy/internal/animation/BuddyFrame.java).

| # | Name | Meaning |
|---|---|---|
| 0 | `IDLE` | Standing, eyes open |
| 1 | `BLINK` | Both eyes shut |
| 2 | `WINK` | Left eye shut |
| 3 | `WAVE_A` | Right arm raised |
| 4 | `WAVE_B` | Right arm swung lower and further out, head leans right |
| 5 | `HOP` | Lifted one pixel, legs tucked, both arms out |
| 6 | `LEAN_LEFT` | Head leans left, right leg kicks, right arm up |
| 7 | `LEAN_RIGHT` | Head leans right, left arm up |
| 8 | `SIT` | Sitting: body a pixel lower, head slumped, feet out in front of the shell |
| 9 | `SIT_BLINK` | Sitting with both eyes shut |
| 10 | `TUCK` | Retracted: no limbs, the head sunk into the top of the shell |
| 11 | `SLEEP_A` | Empty shell, two Zs low |
| 12 | `SLEEP_B` | Empty shell, the Zs risen and drifted right |
| 13 | `SLEEP_C` | Empty shell, the big Z at the top and a new small Z below |
| 14 | `SPARKLE_A` | Spawn overlay: stars only, no body (painted on top of another frame) |
| 15 | `SPARKLE_B` | Spawn overlay, stars in different places |
| 16 | `SPARKLE_C` | Spawn overlay, stars in different places again |
| 17 | `TYPE_A` | Sitting with a laptop in his lap, left hand raised off the keys |
| 18 | `TYPE_B` | As A, right hand raised instead |
| 19 | `TYPE_REST` | As A with both hands down, the beat between bursts |

Frames 17–19 are the working animation (A, B, A, B, rest at 250 ms per frame), requested
by the host through `setWorking(true)`. Jasper requests it while a terminal command runs
past `notifications.long_command_seconds`, or runs out of sight, unless that setting is 0;
Buddy has no notification threshold. Frames 0–13 are poses. Frames 14–16 are overlays:
transparent except for the stars, cycled on top of the body while he fades in at spawn, so they never replace a pose. [BuddyAnimator](../../jasper-buddy/src/main/java/dev/jasper/buddy/internal/animation/BuddyAnimator.java)
sequences the frames; [Buddy architecture](../../docs/buddy-architecture.md#animation-drawing-and-placement)
lists the timings.

## Palette

Character colours follow [the silver character artwork](jasper-character-reference.svg);
laptop colours are explicit constants in `generate.py`.

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

For the generated-art workflow, edit `generate.py` and run from the repository root:

```sh
python3 packaging/buddy/generate.py
python3 packaging/buddy/generate.py --verify-only
./gradlew :jasper-buddy:test --tests '*BuddySpriteTest' --tests '*BuddyApiTest'
```

The first command overwrites both committed artifacts and verifies that each master
frame matches its PNG cell. `--verify-only` reads the existing artifacts without writing:
it checks the frame count and compares the `.ase` pixels with the PNG cells. It still
needs Pillow, and it does not compare those pixels with newly generated drawing output,
so passing it alone does not establish that hand-edited artwork matches the generator.
Both modes print `ok: 20 frames -> …` on success; otherwise an assertion names what
failed: a malformed `.ase` (header, frame magic or chunk sizes), the frame count, or the
first frame whose `.ase` and PNG cells differ. `BuddySpriteTest` checks exact
dimensions/frame count, transparent left, right and bottom margins, sparkle overlays that
use the star colour and leave the body's centre uncovered, nearest-neighbour scaling and
distinguishing artwork; `BuddyApiTest` decodes the resource from the jar. Keep the Java enum order, generator
`FRAMES` order, master and runtime PNG synchronized.

To edit the master by hand in LibreSprite instead, export it to the runtime path (macOS
install path shown):

```sh
/Applications/libresprite.app/Contents/MacOS/libresprite --batch packaging/buddy/jasper-buddy.ase \
    --sheet jasper-buddy/src/main/resources/dev/jasper/buddy/internal/presentation/jasper-buddy.png
```

No automated check runs this export. Keep 20 frames of 42 × 48, no inter-frame padding,
and transparent left/right/bottom margins. Confirm the PNG was rewritten (a batch export
has been seen to write nothing and not exit), then run `--verify-only` and the tests
above. Do not run the generator's writing mode after hand edits unless those edits have
been ported back to its drawing functions. See [Buddy maintenance](../../docs/buddy-maintenance.md)
for headless image previews and the manual native-acceptance boundary.
