# Jasper desk buddy sprite

Pixel-art Jasper for the floating desk buddy: 42 × 48 art pixels per frame, drawn at
2 logical px per art pixel by Buddy (84 × 96 window, 4 device px per art pixel at 2× display scale).

- [jasper-buddy.ase](jasper-buddy.ase) — editable master for [LibreSprite](https://libresprite.github.io). One RGBA layer, 20 frames of 42 × 48.
- [jasper-buddy.png](../../jasper-buddy/src/main/resources/dev/jasper/buddy/internal/presentation/jasper-buddy.png) — runtime strip, 840 × 48, 20 frames left to right with no inter-frame padding.
- [generate.py](generate.py) — programmatic artwork and exporter (Python 3 + Pillow).
  Prefer editing its drawing functions and regenerating both artifacts together. Running
  it normally overwrites the master and runtime strip; it does not preserve hand edits.

The runtime resource belongs to `jasper-buddy`, beside
[BuddySprite](../../jasper-buddy/src/main/java/dev/jasper/buddy/internal/presentation/BuddySprite.java).
Ordinary Gradle builds load committed artwork and run no generator.

## Frames

Column order is fixed by [BuddyFrame](../../jasper-buddy/src/main/java/dev/jasper/buddy/internal/animation/BuddyFrame.java).

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

Frames 17–19 are the working animation, requested by the host through `setWorking(true)`.
Jasper's command policy decides when to request it; Buddy has no notification threshold.
Frames 0–13 are poses. Frames 14–16 are overlays: transparent except for the stars, cycled on top of
the body while he fades in at spawn, so they never replace a pose.

## Palette

Character colors follow [the application artwork](../icons/jasper.svg); laptop colors
are explicit constants in `generate.py`.

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
frame matches its PNG cell. `--verify-only` reads existing artifacts without writing:
it checks frame count and compares the `.ase` pixels with the PNG cells. It does not
compare those pixels with newly generated drawing output, so passing that check alone
does not establish that hand-edited artwork matches the generator. `BuddySpriteTest`
checks exact dimensions/frame count, transparent side/bottom margins, nearest-neighbor
scaling and distinguishing artwork; `BuddyApiTest` decodes the resource from the jar.
Keep the Java enum order, generator frame order, master and runtime PNG synchronized.

If a human chooses to edit the master in LibreSprite, export to the current runtime path:

```sh
/Applications/libresprite.app/Contents/MacOS/libresprite --batch packaging/buddy/jasper-buddy.ase \
    --sheet jasper-buddy/src/main/resources/dev/jasper/buddy/internal/presentation/jasper-buddy.png
```

That is a human-run export alternative, not part of Gradle verification. Keep 20 frames
of 42 × 48, no inter-frame padding, and transparent left/right/bottom margins. Do not run
the generator's writing mode after hand edits unless those edits have been ported back
to its drawing functions. No LibreSprite export was run during this documentation audit.

Historical acceptance on 2026-09-14 found that LibreSprite's batch export produced no
output and did not exit within 60 seconds; the process was killed. That check used the
then-current 17-frame, 714 × 48 strip and is not validation of today's 20-frame artwork
or current native export. The `.ase` reader/writer verification is independent of
LibreSprite. See [Buddy maintenance](../../docs/buddy-maintenance.md) for headless
image previews and the manual native-acceptance boundary.
