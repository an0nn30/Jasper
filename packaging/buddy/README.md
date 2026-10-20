# Jasper desk buddy sprite

The app uses shaded pixel-art Jasper: olive green, a cream belly, brown shell and round glasses.
The homepage keeps its existing dimensional illustration.
Each frame contains 84 × 96 source pixels and paints into 42 × 48 logical units; the default
scale of 2.5 displays the companion at 105 × 120. Nearest-neighbor sampling preserves pixel edges.

- [jasper-buddy.ase](jasper-buddy.ase) — editable RGBA master, 20 frames of 84 × 96.
- [runtime strip](../../jasper-buddy/src/main/resources/dev/jasper/buddy/internal/presentation/jasper-buddy.png) — 1680 × 96, in enum order.
- [source atlas](../../docs/design/jasper-pixel/source-atlas.png) — generated poses from the approved pixel character.
- [pack-atlas.swift](pack-atlas.swift) — atlas slicing, shared scale, opaque pixel grid and foot baseline; preserves the existing spawn sparkles.
- [generate.py](generate.py) — pixel-exact master/strip exporter and verifier (Python 3 + Pillow).

Ordinary Gradle builds load the committed PNG and run no artwork generator.

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

Frames 17–19 are the working animation, requested by the host through `setWorking(true)`.
Jasper's command policy decides when to request it; Buddy has no notification threshold.
Frames 0–13 are poses. Frames 14–16 are overlays: transparent except for the stars, cycled on top of
the body while he fades in at spawn, so they never replace a pose.

## Editing and exporting

Edit the master in LibreSprite, keeping frame order, dimensions and transparent margins. The Python exporter accepts one visible normal layer with opaque, full-canvas compressed cels at (0, 0); it validates every frame before writing. For trimmed, linked or layered cels, flatten and export the horizontal strip with LibreSprite, then use `--import-strip` to normalize the master. Export and verify:

```sh
python3 packaging/buddy/generate.py
python3 packaging/buddy/generate.py --verify-only
./gradlew :jasper-buddy:test
```

To repack this source atlas on macOS and update the editable master:

```sh
swift packaging/buddy/pack-atlas.swift \
  docs/design/jasper-pixel/source-atlas.png \
  docs/design/jasper-dimensional/original-sparkle-source.png \
  jasper-buddy/src/main/resources/dev/jasper/buddy/internal/presentation/jasper-buddy.png
python3 packaging/buddy/generate.py --import-strip
python3 packaging/buddy/generate.py --verify-only
```

Atlas row/column boundaries are explicit in the packer. A replacement atlas must be inspected and
its boundaries adjusted before export. The packer keeps a common scale, centers each pose, and
raises the hop without changing runtime geometry. See [Buddy maintenance](../../docs/buddy-maintenance.md)
for headless previews and the manual native-acceptance boundary.
