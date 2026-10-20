# Jasper app icon

**Shell Prompt** is the approved icon: a moss turtle shell surrounding a cream terminal prompt.

- `shell-prompt-source.png` is the generated raster master.
- `generate.py` removes the source's transparent padding and applies platform framing once: macOS uses the system mask, Windows uses 960/1024 of the canvas, and Linux uses 90%.
- `Jasper.icns`, `Jasper.ico` and the 512px `Jasper.png` are native package inputs. macOS also bundles `macos/Assets.car` with `CFBundleIconName=Jasper`.
- Runtime PNGs live in `jasper-app/src/main/resources/dev/jasper/app/icons/app/`. Linux desktop assets live in `linux/hicolor/`. The website uses the matching 256px PNG.
- `Jasper.svg`, `macos/Jasper.svg` and the Linux scalable entry embed the selected raster artwork; they are not vector redraws.
- `macos/icon.json` disables extra glass effects and supplies a forest background beneath the artwork. Packaged macOS launches retain the native catalog icon; development launches use the matching rendered PNGs.

## Regeneration

On macOS with Pillow, `iconutil` and full Xcode:

```sh
python3 packaging/icons/generate.py
./gradlew :jasper-app:test --tests dev.jasper.app.platform.ApplicationIconTest
```

The generator compiles the macOS catalog and renders its native representations without launching an app. It updates package/runtime assets and `website/assets/jasper.png`, then exports `build/jasper-icon-set/` and `build/Jasper-icon-set.zip` with a SHA-256 manifest. Normal builds use committed assets and require no authoring tools.

macOS PNG sizes: 16, 32, 64, 128, 256, 512 and 1024px, with ten standard/Retina ICNS representations. Windows includes 20 RGBA ICO frames from 16 through 256px and standalone PNGs through 1024px. Linux includes 18 PNG sizes from 16 through 1024px. Runtime sizes stay aligned with `ApplicationIcon.java`.

Small ICNS ARGB representations are corrected to premultiplied alpha to avoid bright fringes. Tests cover native container representations, decoding, transparent corners, platform padding, visible prompt coverage and Linux package/runtime parity. Native desktop appearance should also be checked when installing the build.
