# Jasper app icon

The official icon is **Silver Desk Buddy Jasper**: a friendly turtle with round
glasses, a natural shell, and a silver background taken from JBR 25's Motif
`controlHighlight` color (`#dcdee5`). There is no terminal imagery on Jasper.
The user selected this artwork on 2026-09-14.

![macOS icon](../../jasper-app/src/main/resources/dev/jasper/app/icons/app/macos/icon-256.png)
![Windows icon](../../jasper-app/src/main/resources/dev/jasper/app/icons/app/windows/icon-256.png)

## Source and outputs

- `jasper.svg` is the approved editable SVG master, copied from the silver study.
  Open it directly in Illustrator or Inkscape. All artwork is vector shapes;
  there are no embedded bitmaps, fonts, filters, or external dependencies.
- `jasper-macos.svg` and `jasper-windows.svg` are generated platform layouts.
- `Jasper.icns` and `Jasper.ico` are the native package inputs.
- `jasper-app/src/main/resources/dev/jasper/app/icons/app/` contains PNG sizes
  loaded by Swing and the development-launch Dock icon.

The application, asset filenames, Java packages, and packaging configuration
now use Jasper. The Git repository remains `an0nn30/moray`.

## Platform framing

**macOS:** The approved artwork is preserved exactly, including its silver
816px-wide rounded tile and 104px transparent side margins on the 1024px canvas.
The subtle bottom shadow is part of the SVG. No extra padding is added during
rendering or packaging. ICNS includes the ten standard 1x/2x representations
for 16, 32, 128, 256, and 512 point sizes, up to 1024px.

**Windows:** The same turtle and silver palette use tighter tile corners. The
artwork scales together to a 960px-wide footprint and 32px side margins.
The 32-bit-alpha ICO contains PNG frames at 16, 20, 24, 30, 32, 36, 40, 48,
60, 64, 72, 80, 96, 128, and 256px. Runtime window images use these same frames.

## Regeneration and checks

On macOS with Python 3, Inkscape, and Apple's `iconutil`:

```bash
python3 packaging/icons/generate.py
./gradlew check :jasper-app:packageDist
```

An alternate Inkscape path can be passed with `--inkscape /path/to/inkscape`.
The generator renders every size directly from SVG. Ordinary builds consume
the committed assets and require none of the icon authoring tools.

Asset tests check container representations, alpha, platform padding and runtime
loading. Package verification checks the macOS bundle's `CFBundleIconFile` and
exact ICNS bytes, or ICO payloads embedded in the Windows executable. None of
these checks launches the app. Native Windows packaging and live desktop icon
appearance still require platform acceptance checks.

## Verified 2026-09-14

- `./gradlew check :jasper-app:packageDist` passed. XML totals: 662 tests,
  661 passed and one existing terminal font skip; terminal checks were up to date.
- The master matches the selected silver study byte-for-byte.
- All 15 ICO frames match runtime PNG payloads byte-for-byte.
- macOS and Windows rendered artwork was visually inspected.
- macOS package verification passed and the DMG was rebuilt. No app was launched.
