# Plan 4c actual-component theme renders

Generated and visually inspected on 2026-09-12 with JBR 25.0.4.1+1-b583.48, macOS 26.6.2 (25G83), headless Swing at 958 × 958 logical pixels and 2× raster scale. No physical screens tested, native detector initialized, JFrame created or login shell started. A controlled `/bin/sh -c` fixture emits prompt metadata and waits for cleanup.

- [Dark](theme-dark.png): matching built-in terminal/padding/status and dark chrome.
- [Light](theme-light.png): matching built-in terminal/padding/status and light chrome.
- [Custom](theme-custom.png): `#101820` terminal/padding/status, white palette foreground and yellow cursor with light chrome. Status text remains readable after a configuration status refresh.

All variants retain the 38px title row, 53px toolbar, 30px status and exact 4px terminal inset. The terminal grid in this fixture is 95 × 37. Inspection found no discontinuity between padding/terminal/status, clipped status labels or unintended custom color on toolbar controls. Native traffic lights, physical display scaling and OS detection are unverified.

Reproduce from the worktree/repository root:

```sh
./gradlew :jasper-app:mockUiPreview --init-script docs/design/plan-4c-themes/preview.gradle
```

This uses the test-only `SystemThemePreview` fixture and writes only this directory. Historical mock screenshots remain unchanged.
