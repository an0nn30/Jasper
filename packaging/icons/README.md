# Jasper app icon

The official icon is **Ember Jasper**: a faceted white turtle on an apricot,
terracotta, and burgundy gradient tile, with no terminal prompt. The user selected
this exact icon pack on 2026-09-21 after comparing native Dock previews.

![macOS icon](../../jasper-app/src/main/resources/dev/jasper/app/icons/app/macos/icon-256.png)
![Windows icon](../../jasper-app/src/main/resources/dev/jasper/app/icons/app/windows/icon-256.png)
![Linux icon](../../jasper-app/src/main/resources/dev/jasper/app/icons/app/linux/icon-256.png)

## Source and outputs

- `jasper-ember-source.png` is the approved 1254px generated raster artwork. It
  includes the presentation canvas; the renderer removes that outside canvas.
  It is not an editable vector master. Source SHA-256:
  `9c8fa27c4ce67167369bd1e12c42d49168b4a1a99e9b939edb5e366b70376e33`.
- `render.swift` uses macOS Core Graphics to prepare and resize the artwork.
  `generate.py` writes the committed platform files and native containers.
- `Jasper.icns`, `Jasper.ico`, and `Jasper.png` are the native package inputs for
  macOS, Windows, and Linux respectively. The Linux package PNG is 512px.
- `linux/hicolor/<size>x<size>/apps/jasper.png` is the standard Linux desktop icon
  hierarchy. Portable Gradle distributions include it under `share/icons/hicolor`.
- `jasper-app/src/main/resources/dev/jasper/app/icons/app/` contains the platform
  PNG sets used by Swing windows and development-launch taskbar/Dock icons.

The former silver character SVG is preserved as
[Buddy's character reference](../buddy/jasper-character-reference.svg). Buddy's
sprite artwork remains independent of the application icon. The obsolete
macOS/Windows SVG app-icon layouts have been removed to avoid regenerating the
old design accidentally.

## Platform framing

The same approved artwork is used everywhere, including its rounded tile and
faceted lighting. The outside presentation canvas is transparent in the outputs.
Margins are baked once; no packaging step adds another layer of padding.

| Platform | Maximum tile footprint | Runtime PNG sizes |
| --- | --- | --- |
| macOS | 816px per 1024px canvas | 16, 32, 64, 128, 256, 512, 1024 |
| Windows | 960px per 1024px canvas | 16, 20, 24, 30, 32, 36, 40, 48, 60, 64, 72, 80, 96, 128, 256 |
| Linux | 90% of canvas | 16, 24, 32, 48, 64, 96, 128, 256, 512, 1024 |

ICNS contains the ten standard 1x/2x representations for 16, 32, 128, 256, and
512 point sizes. The 32-bit-alpha ICO contains lossless PNG frames matching the
Windows runtime images byte-for-byte. Linux `Jasper.png` and the hicolor assets
match their runtime images byte-for-byte.

## Regeneration and checks

On macOS with Python 3 and Xcode Command Line Tools (Swift and `iconutil`):

```sh
python3 packaging/icons/generate.py
./gradlew check :jasper-app:installDist :jasper-app:packageDist
```

No Inkscape or image-generation access is required to reproduce the approved
pack. Normal builds use the committed files and need none of these authoring
tools. The generator writes no source artwork and does not launch an app.

The canvas removal is deliberately specific to this approved image: it floods
low-chroma pixels connected to the image border, preserving the white turtle
inside the colored tile. It keeps the central connected tile and resamples each
size directly from that extracted source. A different future source image needs
visual validation rather than an assumption that this extraction still fits.

Headless asset tests cover native container representations, decoding, alpha,
platform margins, all runtime resource sets, and Linux desktop/package parity.
Package verification checks macOS `CFBundleIconFile` and exact ICNS bytes or
Windows ICO payloads embedded in the launcher, without launching Jasper.

Native `packageDist` currently supports macOS and Windows. Linux has runtime and
portable-distribution icons plus the standard hicolor tree, but a native Linux
installer task has not yet been implemented. Windows and Linux desktop
appearance require acceptance checks on those operating systems.

## Verification of the approved pack

On 2026-09-21, `./gradlew check :jasper-app:installDist :jasper-app:packageDist`
passed with 1,436 tests (two expected skips; no failures). The macOS app and DMG
verified successfully; the packaged ICNS exactly matches the official source.
All runtime PNGs and native ICNS/ICO files reproduce the approved Ember preview
pack byte-for-byte. All ten hicolor files appear unchanged in `installDist`.
Windows/Linux native desktop appearance remains a platform acceptance check.
