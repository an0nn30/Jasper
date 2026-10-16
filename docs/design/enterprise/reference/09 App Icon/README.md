# Jasper app icon (9b · Narrow Square Prompt)

- **macOS:** `macOS/Jasper.icns` (16 to 1024 px, including @2x). `macOS/AppIcon.iconset/` holds the source PNGs, so you can rebuild the file with `iconutil -c icns AppIcon.iconset`. Before you do, rename each `-2x.png` file to `@2x.png`, because the download can't keep `@` in file names. `Jasper.icns` itself is already complete.
- **Windows:** `Windows/Jasper.ico` (16, 20, 24, 32, 40, 48, 64, 96, 128 and 256 px, PNG-compressed). Loose PNGs are in `Windows/png/`.
- **Linux:** a freedesktop hicolor tree (`hicolor/<size>/apps/jasper.png` plus `scalable/apps/jasper.svg`). Copy it to `/usr/share/icons/` or `~/.local/share/icons/` and set `Icon=jasper` in the .desktop file.
- **JPEG:** 64 to 1024 px on white, for docs and web use. JPEG has no transparency, so use the PNGs for the app itself.
- **Masters:** `Jasper.svg` and `Jasper-1024.png`.
- **Java:** for window and taskbar icons, pass several PNG sizes (16, 32, 48, 64, 128 and 256) to the window's icon list, and use the 1024 px master for the macOS Dock.
