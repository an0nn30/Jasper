# Jasper desk buddy sprite

The app uses shaded pixel-art Jasper: olive green, a cream belly, a brown shell and round glasses.
The homepage keeps its existing dimensional illustration.
Each frame holds 84 × 96 source pixels and paints into a 42 × 48 logical frame; Buddy's scale of 2.5
displays the companion at 105 × 120. Nearest-neighbour sampling keeps the pixel edges hard.

- [jasper-buddy.ase](jasper-buddy.ase) — editable master for [LibreSprite](https://libresprite.github.io). One RGBA layer, 23 frames of 84 × 96.
- [jasper-buddy.png](../../jasper-buddy/src/main/resources/dev/jasper/buddy/internal/presentation/jasper-buddy.png) — runtime strip, 1932 × 96, 23 frames left to right in `BuddyFrame` order with no inter-frame padding.
- [source-atlas.png](../../docs/design/jasper-pixel/source-atlas.png) — the generated poses of the approved pixel character
  ([how it was made](../../docs/design/jasper-pixel/README.md)).
- [pack-atlas.swift](pack-atlas.swift) — packs that atlas into the runtime strip on macOS: slicing, one shared scale,
  an opaque pixel grid and a common foot baseline, keeping the existing spawn sparkles.
- [generate.py](generate.py) — pixel-exact exporter and verifier for the master and the strip (Python 3 + Pillow).
- `jasper-character-reference.svg` is the earlier silver character artwork; nothing uses it.

The runtime resource belongs to `jasper-buddy`, beside
[BuddySprite](../../jasper-buddy/src/main/java/dev/jasper/buddy/internal/presentation/BuddySprite.java),
which slices it into one 84 × 96 cell per `BuddyFrame` constant and refuses a sheet of
any other size; `BuddyCompanion.show()` then returns false. Gradle builds load the
committed PNG and run none of these tools.

## Frames

Column order is fixed by [BuddyFrame](../../jasper-buddy/src/main/java/dev/jasper/buddy/internal/animation/BuddyFrame.java).

| # | Name | Meaning |
|---|---|---|
| 0 | `IDLE` | Standing, eyes open |
| 1 | `BLINK` | Both eyes shut |
| 2 | `WINK` | One eye shut |
| 3 | `WAVE_A` | Right arm raised |
| 4 | `WAVE_B` | Right arm raised higher |
| 5 | `HOP` | Lifted, legs tucked, both arms out |
| 6 | `LEAN_LEFT` | Head leans left |
| 7 | `LEAN_RIGHT` | Head leans right |
| 8 | `SIT` | Sitting, feet in front of the shell |
| 9 | `SIT_BLINK` | Sitting with both eyes shut |
| 10 | `TUCK` | Head tucking into the shell |
| 11 | `SLEEP_A` | Sleeping shell, one Z |
| 12 | `SLEEP_B` | Sleeping shell, two Zs |
| 13 | `SLEEP_C` | Sleeping shell, three Zs |
| 14 | `SPARKLE_A` | Spawn overlay: stars only, no body (painted on top of another frame) |
| 15 | `SPARKLE_B` | Spawn overlay, stars in different places |
| 16 | `SPARKLE_C` | Spawn overlay, stars in different places again |
| 17 | `TYPE_A` | Sitting with a laptop in his lap, left hand raised off the keys |
| 18 | `TYPE_B` | As A, right hand raised instead |
| 19 | `TYPE_REST` | As A with both hands down, the beat between bursts |
| 20 | `CLOSE_LAPTOP` | Seated, lowering the laptop lid |
| 21 | `LOWER_LAPTOP` | Feet planted, laptop lowered out of view |
| 22 | `RISE` | Legs extending toward standing |

Frames 17–19 are the working animation (A, B, A, B, rest at 250 ms per frame), requested
by the host through `setWorking(true)`. Jasper requests it while a terminal command runs
past `notifications.long_command_seconds`, or runs out of sight, unless that setting is 0;
Buddy has no notification threshold. Frames 0–13 and 17–19 are poses. Frames 14–16 are overlays:
transparent except for the stars, cycled on top of the body while he fades in at spawn, so they never replace a pose. [BuddyAnimator](../../jasper-buddy/src/main/java/dev/jasper/buddy/internal/animation/BuddyAnimator.java)
sequences the frames; [Buddy architecture](../../docs/buddy-architecture.md#animation-drawing-and-placement)
lists the timings.

## Editing and exporting

Edit the master in LibreSprite, keeping the frame order, the 84 × 96 frame size and the transparent left, right and
bottom margins. The exporter reads a master with one visible normal layer and full-canvas compressed cels at (0, 0)
with full opacity, and validates every frame before it writes. Export and verify from the repository root:

```sh
python3 packaging/buddy/generate.py
python3 packaging/buddy/generate.py --verify-only
./gradlew :jasper-buddy:test
```

The first command writes the runtime strip from the master, then checks that each master frame matches its PNG cell;
it never writes the master. `--verify-only` makes the same comparison without writing anything. Both print
`ok: 23 pixel frames; master and runtime match` on success; otherwise an assertion names what failed: a malformed
`.ase` (header, frame magic or chunk sizes), a master in another layout (more than one layer, or trimmed, offset or
linked cels), the frame count, the strip's dimensions, or the first frame, by its `FRAMES` name, whose `.ase` and PNG
cells differ. Keep the Java enum order, the `FRAMES` order, the master and the runtime PNG synchronized.

When LibreSprite saves trimmed, linked or layered cels, flatten the master and export it as a horizontal strip to the
runtime path instead (macOS install path shown):

```sh
/Applications/libresprite.app/Contents/MacOS/libresprite --batch packaging/buddy/jasper-buddy.ase \
    --sheet jasper-buddy/src/main/resources/dev/jasper/buddy/internal/presentation/jasper-buddy.png
python3 packaging/buddy/generate.py --import-strip
```

Confirm the PNG was rewritten first (a batch export has been seen to write nothing and not exit). `--import-strip`
rewrites the master from the strip in the exporter's layout, then verifies the pair as `--verify-only` does; no
automated check runs the LibreSprite export.

`BuddySpriteTest` checks the exact dimensions and frame count, transparent left, right and bottom margins, pixels that
are either opaque or transparent, shaded artwork, sparkle overlays that use the star colour and leave the body's
centre uncovered, distinguishing poses, and nearest-neighbour painting that keeps the logical bounds, blends no
colours and shows nothing of a neighbouring frame; `BuddyApiTest` decodes the resource from the jar.

To repack the source atlas on macOS, which writes the runtime strip, and update the master:

```sh
swift packaging/buddy/pack-atlas.swift \
  docs/design/jasper-pixel/source-atlas.png \
  docs/design/jasper-dimensional/original-sparkle-source.png \
  jasper-buddy/src/main/resources/dev/jasper/buddy/internal/presentation/jasper-buddy.png
python3 packaging/buddy/generate.py --import-strip
python3 packaging/buddy/generate.py --verify-only
```

The packer accepts only the 1429 × 1100 atlas, whose measured row and column boundaries it names; inspect a
replacement atlas and adjust those boundaries before packing it. It crops each pose to its opaque pixels, scales all
17 poses by one shared factor with nearest-neighbour sampling, centres each in its cell on a common baseline with the
hop raised above it, draws the three spawn sparkles of the earlier 42 × 48 strip
(`original-sparkle-source.png`) at twice their size, and makes every pixel fully opaque or fully transparent. Runtime
geometry does not change. See [Buddy maintenance](../../docs/buddy-maintenance.md) for headless image previews and the
manual native-acceptance boundary.

The three transition poses come from [the stand-up atlas](../../docs/design/jasper-stand-up/source-atlas.png)
([prompt and provenance](../../docs/design/jasper-stand-up/README.md)). Append them to an existing runtime strip
without re-rendering its original twenty cells, then synchronize the master:

```sh
swift packaging/buddy/pack-stand-up.swift docs/design/jasper-stand-up/source-atlas.png /tmp/buddy-transition.png
python3 packaging/buddy/append-stand-up.py \
  jasper-buddy/src/main/resources/dev/jasper/buddy/internal/presentation/jasper-buddy.png /tmp/buddy-transition.png
python3 packaging/buddy/generate.py --import-strip
python3 packaging/buddy/generate.py --verify-only
```

The historical atlas packer above writes only twenty cells; follow it with this append step before importing.
The transition packer uses measured transparent gutters, one shared scale of 0.15, nearest-neighbour sampling,
binary alpha and the existing foot baseline at row 91. The append step checks opaque bounds and preserves the
original decoded RGBA pixels exactly. It also accepts a 23-cell strip for reproducible repacking.
