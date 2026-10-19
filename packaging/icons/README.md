# Jasper app icon

Jasper's icon is **Original Steep**: a white and slate-blue overlapping terminal prompt on a graphite face with a
silver border, and a full-height tab with a 68-degree slope. The macOS variant fills the system's icon mask with the
terminal face and draws the tab into its top edge.

![macOS icon](../../jasper-app/src/main/resources/dev/jasper/app/icons/app/macos/icon-256.png)
![Windows icon](../../jasper-app/src/main/resources/dev/jasper/app/icons/app/windows/icon-256.png)
![Linux icon](../../jasper-app/src/main/resources/dev/jasper/app/icons/app/linux/icon-256.png)

## Source and outputs

- `Jasper.svg` is the editable vector master for Windows and Linux. `macos/Jasper.svg` is the full-canvas macOS
  master, and `macos/icon.json` its Icon Composer configuration, which turns glass effects off for the artwork.
- `Jasper.icns`, `Jasper.ico` and `Jasper.png` (512 px) are jpackage's `--icon` inputs on macOS, Windows and Linux.
  macOS also bundles `macos/Assets.car`, the compiled asset catalog, with `CFBundleIconName=Jasper`.
- `linux/hicolor/<size>x<size>/apps/jasper.png` and `linux/hicolor/scalable/apps/jasper.svg` form the Linux desktop
  icon tree. The portable distributions (`installDist`, `distZip`, `distTar`) carry it under `share/icons/hicolor`, so
  a desktop launcher can use `Icon=jasper` once the tree is in an icon search path.
- `jasper-app/src/main/resources/dev/jasper/app/icons/app/<macos|windows|linux>/icon-<size>.png` are the runtime PNG
  sets for window icons and for the Dock or taskbar icon of a development launch.
- `generate.py` renders all of these from the SVGs, and `render-macos.swift` renders the macOS sizes through the
  native icon service. The generator also exports a standalone set to `build/jasper-icon-set/` (every platform PNG
  size, the framed SVGs, the native containers, the SVG master, the macOS `.iconset`, `Jasper.icon` and `Assets.car`,
  the Linux hicolor tree, a README and a SHA-256 manifest) and the archive `build/Jasper-icon-set.zip`. Build outputs
  are not checked in.
- `jasper-ember-source.png` is earlier artwork; nothing uses it.

## Where each icon is used

| Platform | Package | Running app |
|---|---|---|
| macOS | `Contents/Resources/Assets.car` on current macOS, `Jasper.icns` as the fallback | A packaged launch (one with `jpackage.app-path`) keeps the bundle's Dock icon; a development launch sets the 1024 px runtime PNG. Window icons use the runtime PNGs. |
| Windows | The ICO frames embedded in `Jasper.exe`; the setup wizard is built with `Jasper.ico` too | Window icons from the runtime PNGs |
| Linux | `lib/Jasper.png`, the `.deb` and `.rpm` menu entry's icon; the packages do not install the hicolor tree | Window icons from the runtime PNGs |

A look can bring its own application icons, as the [Enterprise look](../../docs/enterprise.md) does. They then replace
Jasper's in new windows' icon lists and on the Dock or taskbar, in packaged macOS launches too; the icon of the package
itself, in Finder, Explorer or the desktop menu, stays Jasper's.

## Platform framing and sizes

Windows and Linux render directly from `Jasper.svg` with their platform's optical padding applied once. macOS renders
its full-canvas `macos/Jasper.svg` at 1024 px, compiles it with Icon Composer, then exports each size through the native
icon service; its ICNS fallback and runtime PNGs have the same native mask and edge treatment. Packaging adds no
margin.

| Platform | Maximum artwork footprint | Standalone PNG sizes |
| --- | --- | --- |
| macOS | Full 1024px artwork; system-applied outer mask | 16, 32, 64, 128, 256, 512, 1024 |
| Windows | 960px per 1024px canvas | 16, 20, 24, 28, 30, 32, 36, 40, 44, 48, 56, 60, 64, 72, 80, 96, 128, 160, 192, 256, 384, 512, 768, 1024 |
| Linux | 90% of canvas | 16, 20, 22, 24, 32, 36, 40, 48, 64, 72, 96, 128, 192, 256, 384, 512, 768, 1024; scalable SVG |

The ICNS holds the ten standard 1x and 2x representations for the 16, 32, 128, 256 and 512 point sizes, as does the
exported `.iconset`. The 32-bit-alpha ICO holds 20 lossless PNG frames, every listed Windows size through 256 px; the
larger Windows PNGs are separate files, beyond the ICO size limit.

The runtime lists match `ApplicationIcon.java`: macOS uses all seven sizes; Windows uses 16, 20, 24, 30, 32, 36, 40,
48, 60, 64, 72, 80, 96, 128 and 256; Linux uses 16, 24, 32, 48, 64, 96, 128, 256, 512 and 1024. Matching runtime, ICO
and hicolor representations are byte-identical.

## Regeneration and checks

On macOS 26 or later with Python 3, Inkscape, `iconutil` and full Xcode (`actool` and Swift):

```sh
python3 packaging/icons/generate.py
./gradlew check :jasper-app:installDist :jasper-app:packageDist
```

The second command trains the AOT cache on macOS, which starts Jasper for a few seconds
([packaging](../../docs/packaging.md#training-the-aot-cache)). Normal builds use the checked-in assets and need no
icon tools. The generator records the Inkscape version, the source hash, the sizes and every file's hash in the
exported manifest, and replaces the checked-in assets only after rendering and the native containers succeed.

`iconutil` writes straight RGB in the 16 and 32 px ARGB chunks of the ICNS; the generator converts those two chunks to
premultiplied alpha, which native decoding expects, so their antialiased edges are not too bright. Larger chunks are
unchanged. Rebuild the ICNS through the generator, not with `iconutil` alone.

The generator renders the macOS sizes from a temporary app bundle with a unique identifier, so the icon service holds
no stale entry for it; the bundle contains a copy of `/usr/bin/true` only so that the icon service draws no
incomplete-app badge. Nothing is launched.

`ApplicationIconTest` checks the containers' representations, decoding, alpha, platform margins, the runtime resource
sets and the parity of the Linux sets headlessly. `verifyPackage` checks the macOS bundle's ICNS bytes and
`CFBundleIconName` with the `Assets.car` bytes (copied before signing), every ICO frame embedded in the Windows launcher,
and the bytes of the Linux image's `lib/Jasper.png`, without starting Jasper. Desktop appearance on each operating
system is part of the [native acceptance checklist](../../docs/packaging.md#native-acceptance-checklist).
