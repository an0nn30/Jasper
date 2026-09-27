# Jasper app icon

The official icon is **Original Steep**, selected on 2026-09-26: a white and
slate-blue overlapping terminal prompt, graphite face, silver border and a
full-height tab with a 68-degree slope. The user-approved macOS variant places
the terminal face across the native canvas, with the tab drawn into its top edge.

![macOS icon](../../jasper-app/src/main/resources/dev/jasper/app/icons/app/macos/icon-256.png)
![Windows icon](../../jasper-app/src/main/resources/dev/jasper/app/icons/app/windows/icon-256.png)
![Linux icon](../../jasper-app/src/main/resources/dev/jasper/app/icons/app/linux/icon-256.png)

## Source and outputs

- `Jasper.svg` is the exact selected editable vector master. Its paths, prompt,
  colors and tab geometry match the original Steep from design round 7.
- `generate.py` renders each size directly from the SVG using Inkscape, creates
  the native containers, updates packaged/runtime assets and exports a ZIP.
- `Jasper.icns`, `Jasper.ico` and `Jasper.png` are the native package inputs.
  macOS also bundles `macos/Assets.car` with `CFBundleIconName=Jasper`.
  `macos/icon.json` is the editable Icon Composer configuration.
  The Linux package PNG is 512px.
- `linux/hicolor/<size>x<size>/apps/jasper.png` and
  `linux/hicolor/scalable/apps/jasper.svg` form the Linux desktop icon tree.
  Portable distributions include the tree under `share/icons/hicolor`.
- `jasper-app/src/main/resources/dev/jasper/app/icons/app/` contains the PNG
  sets used by Swing windows and development-launch taskbar/Dock icons. Packaged
  macOS launches retain the system-rendered icon instead of replacing it.
- `build/jasper-icon-set/` is the complete standalone export, with all platform
  PNG sizes, framed SVGs, native containers, the original SVG master, macOS
  `.iconset`, `Jasper.icon` and `Assets.car` sources/outputs, Linux hicolor tree,
  README and SHA-256 manifest.
- `build/Jasper-icon-set.zip` is the shareable archive of that standalone set.
  Build outputs are regenerated, not checked into Git.

The former `jasper-ember-source.png` is retained only as historical artwork.
It is no longer used by the generator. Buddy's character/sprite artwork is
independent and unchanged.

## Platform framing and sizes

Windows and Linux render directly from the selected SVG with platform-specific
optical padding applied once. macOS renders its full-canvas `macos/Jasper.svg` at 1024px, compiles it with
Icon Composer, then exports each size through the native icon service. Its ICNS
fallback and runtime PNGs contain the same native mask and edge treatment. Normal packaging adds no extra margin.

| Platform | Maximum artwork footprint | Standalone PNG sizes |
| --- | --- | --- |
| macOS | Full 1024px artwork; system-applied outer mask | 16, 32, 64, 128, 256, 512, 1024 |
| Windows | 960px per 1024px canvas | 16, 20, 24, 28, 30, 32, 36, 40, 44, 48, 56, 60, 64, 72, 80, 96, 128, 160, 192, 256, 384, 512, 768, 1024 |
| Linux | 90% of canvas | 16, 20, 22, 24, 32, 36, 40, 48, 64, 72, 96, 128, 192, 256, 384, 512, 768, 1024; scalable SVG |

ICNS includes the ten standard 1x/2x representations for 16, 32, 128, 256 and
512 point sizes. The retained `.iconset` contains the same representations.
The 32-bit-alpha ICO contains 20 lossless PNG frames, covering every listed
Windows size through 256px. Larger Windows PNGs are separate files, outside
the ICO size limit.

Runtime lists stay aligned with `ApplicationIcon.java`: macOS uses all seven
sizes; Windows uses 16, 20, 24, 30, 32, 36, 40, 48, 60, 64, 72, 80, 96, 128,
256; Linux uses 16, 24, 32, 48, 64, 96, 128, 256, 512, 1024. Matching runtime,
ICO and hicolor representations are byte-identical.

## Regeneration and checks

On macOS 26+ with Python 3, Inkscape, `iconutil` and full Xcode (`actool`/Swift):

```sh
python3 packaging/icons/generate.py
./gradlew check :jasper-app:installDist :jasper-app:packageDist
```

Inkscape 1.4.4 was used for this pack. The generator records the renderer
version, source hash, sizes and file hashes in the exported manifest. Normal
Gradle builds use checked-in assets and require no icon-authoring tools.
The obsolete raster-canvas extraction renderer has been removed.

Headless application-icon checks cover native container representations,
decoding, alpha, platform margins, runtime resource sets and Linux parity.
Package verification checks the macOS bundle’s ICNS or the Windows launcher’s
embedded ICO payloads without opening Jasper. macOS also verifies the catalog
name and bytes. Catalog resources are copied before signing.

The generator corrects straight RGB emitted by `iconutil` in the 16/32px ARGB
chunks to premultiplied alpha. This prevents bright antialiased edges in native
decoding; larger PNG chunks are unchanged. An asset regression checks the alpha
convention, and native `iconutil` round-trip verification confirms identical
composited pixels. Use the generator to rebuild the final ICNS from the iconset.

Native packaging currently supports macOS and Windows. Linux receives runtime
and portable-distribution icons with the hicolor tree; a native Linux installer
is not implemented. Windows/Linux desktop appearance remains a check on those
operating systems.

## Finder and launch-time appearance on recent macOS

The original irregular ICNS was displayed on a silver system tile, then Jasper's
runtime PNG override replaced it with the free-standing outline. Standard PNG
asset catalogs did not fix this. A Finder custom icon preserved the silhouette,
but its metadata failed strict signature verification and is not used.

The user requested a full-canvas macOS variant: the terminal face fills the
system mask, and the steep tab becomes a drawn seam along the top edge. There
is no smaller icon sitting inside an outer background. `macos/Jasper.svg` is its
editable vector source; `macos/icon.json` disables glass effects on the artwork.
`Assets.car` supplies the modern native appearance, and `Jasper.icns` supplies
older macOS fallback representations. Packaged Mac launches (identified by
`jpackage.app-path`) leave the native Dock icon in place, preventing a switch
when Java starts. Development launches can still install the flattened PNG.

The generator renders the catalog from a unique temporary app bundle using
`render-macos.swift`; the bundle contains a copy of `/usr/bin/true` solely to
prevent an incomplete-app badge. No executable or application is launched.
Native appearance modes are controlled by macOS. Regeneration requires modern
macOS/Xcode; ordinary builds use the committed assets.
