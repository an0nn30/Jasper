# Shell Prompt icon verification

The approved generated raster master is `shell-prompt-source.png`. Platform exports apply optical padding once; macOS is compiled through Icon Composer and rendered through the native icon service without launching an application.

- Verified all 89 export manifest hashes, PNG dimensions and ZIP CRCs.
- All ten ICNS representations round-trip through `iconutil` with identical composited RGB pixels on white and black backgrounds.
- Windows retains 20 RGBA ICO frames; Linux retains its full hicolor tree and matching runtime PNGs.
- The website icon is byte-identical to the Linux 256px asset.
- All 11 application-icon tests pass, including forest surround and website/runtime parity assertions.
- Full headless `check` and macOS `verifyPackage` pass with AOT disabled: XML inventory 11,633 tests, zero failures/errors and 25 existing platform skips.
- The manual website bundle builds with 58 files; all five packaging tests pass.

Desktop appearance on Windows and Linux remains an installed-app check. No GUI or AOT training was launched for this integration.
