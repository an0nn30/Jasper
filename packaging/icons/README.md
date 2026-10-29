# Jasper app icon

Jasper uses the supplied Protean artwork in `protean-source/`. Native ICNS/ICO containers and matching platform PNGs are copied byte for byte. The source desktop file is retained as metadata; Jasper keeps its existing application identity.

`generate.py` imports the assets with Python 3 and Pillow, and compiles `macos/Assets.car` with Xcode `actool` from the supplied macOS iconset. It launches no application. Run:

```sh
python3 packaging/icons/generate.py
./gradlew :jasper-app:test --tests '*ApplicationIconTest' --tests '*LinuxNotificationsTest' --tests '*MacNotificationsTest'
./gradlew :jasper-app:packageApp :jasper-app:verifyPackage -Pjasper.aot=false
```

Runtime PNG sizes remain aligned with `ApplicationIcon`. Missing Windows sizes are resized from its supplied 256 px image; missing Linux desktop sizes use the flat 1024 px master. Matching source sizes retain their original pixels and encoding. The website uses the Linux 256 px image, and `Jasper.png` uses Linux 512 px. The Linux scalable SVG and `Jasper.svg` preserve the supplied SVGs.

macOS bundles the static Jasper catalog and supplied ICNS fallback. Windows embeds the supplied ICO in its launcher and installer, and uses runtime PNGs for windows and the resident tray that carries notification balloons. Linux ships the hicolor tree and runtime PNGs; notifications rewrite their cached 128 px image from the runtime resource. macOS notifications use the packaged bundle identity. Look-provided icon overrides remain supported.

The importer updates checked-in assets directly. Normal builds use committed assets. `verifyPackage` checks packaged icon bytes without starting Jasper. Native desktop appearance remains a manual acceptance check.
