# Original Steep icon-set verification

Work is isolated on `codex/jasper-steep-icon`. The Windows/Linux master is
byte-identical to round 7's Original Steep. The macOS-specific SVG follows the
user's revised direction: fill the system mask and draw the steep tab into the
top edge, without a separate inner icon or surrounding background.

## Asset verification

- macOS: seven PNG sizes and ten standard/Retina ICNS representations, plus
  the editable full-canvas SVG, Icon Composer document and compiled Assets.car.
- Windows: 24 PNG sizes with 20 ICO representations. Linux: 18 PNG sizes and SVG.
- All 89 manifest file hashes, 79 PNG dimensions and ZIP CRCs verified.
- Java ImageIO decoded all 78 finished PNG representations, including duplicate
  iconset/hicolor files: square dimensions, alpha and transparent corners pass.
  The additional Icon Composer input PNG is intentionally opaque and unmasked.
- Native iconutil round-trip decoded all ten ICNS representations with zero
  pixel difference from the iconset, including the 512pt @2x (1024px) image.
- The small-ICNS alpha regression failed before correction and passed afterward.
- The new every-size artwork regression caught the native renderer returning
  only a shadow for a 1024-point request. The renderer now requests 512pt @2x
  for 1024px and checks opaque face/bright prompt coverage before publishing.
- Packaged-Mac Dock policy has a regression: jpackage launches retain the native
  icon; development and other platforms can still set their runtime icon.
- Python syntax, changed-Java source hygiene and git diff whitespace checks pass.

## Native appearance and signing

Headless NSWorkspace rendering on macOS 27 reproduced the silver background for
legacy irregular icons. Standard PNG catalogs and transparent Icon Composer
backgrounds did not preserve the free-standing outline. A custom Finder icon
preserved it but failed strict signature verification, so it is not shipped.

The revised full-canvas artwork uses Apple's native mask. Packaging installs
Assets.car and CFBundleIconName before signing and verifies the catalog/ICNS
bytes. No FinderInfo/resource-fork workaround is used. The packaged app skips
Taskbar.setIconImage on macOS so it keeps the native appearance after launch.

## Final build

`./gradlew check :jasper-app:installDist :jasper-app:packageDist` passed:
**1,997 tests, zero failures/errors, three skips**. All Linux installed assets
match the exported set. Independent final review found no remaining issues.

The final DMG was mounted read-only without opening Finder. Its catalog name,
Assets.car bytes, ICNS bytes and `codesign --verify --deep --strict` all passed.
A native icon-service render of the app inside the mounted DMG showed the
full-canvas edge-tab design, matching the package image preview.
No desktop GUI was launched. Actual launch acceptance remains user-run.
Windows/Linux desktop appearance and older macOS fallback appearance have not
been exercised on those operating systems. Integration into main and publication
were subsequently authorized by the user.
