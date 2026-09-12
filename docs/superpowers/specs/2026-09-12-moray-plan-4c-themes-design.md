# Moray Plan 4c — Custom themes and system appearance

**Status:** Prepared for review, 2026-09-12. No implementation started. Based on main `b92221f`, after Plan 4b, compact padding and shell-exit policy. The user requested preparation of the next plan and selected automatic switching of **both chrome and built-in terminal palette**, with custom palettes fixed.

**Parent:** [Phase 1 design](2026-09-10-moray-phase-1-terminal-design.md), especially configuration and themes. This document amends those sections where stated below; completed terminal, window and configuration behavior remains the baseline.

## 1. Runnable deliverable

Load a local TOML terminal palette, reload edits without restarting sessions, and offer System / Light / Dark appearance across all windows. In System mode, the built-in palette follows the OS along with FlatLaf and native macOS chrome. A custom terminal palette stays fixed as chrome changes. Terminal padding and status-bar background continue to match the terminal surface.

Logging, launcher environment cleanup, `.app` packaging, SSH and a theme gallery/editor are outside this slice. Native appearance and physical display checks remain user-run.

## 2. Configuration and precedence

New template and empty/missing configuration:

```toml
[colors]
appearance = "system" # system, light, dark
theme = "moray-dark"  # built-in pair, or a file in Moray's themes directory
```

Custom example:

```toml
[colors]
appearance = "system"
theme = "my-theme.toml"
```

| Saved values | Chrome | Terminal |
|---|---|---|
| Explicit `appearance = "system"`, either built-in ID | OS appearance | Corresponding built-in light/dark palette |
| Explicit `appearance = "light"` or `"dark"`, either built-in ID | Selected appearance | Corresponding built-in palette |
| Any appearance, custom theme | Selected/system appearance | Fixed custom palette |
| No appearance key, explicit `theme = "moray-light"` | Light | Built-in light; preserves old config behavior |
| No appearance key, explicit `theme = "moray-dark"` | Dark | Built-in dark; preserves old config behavior |
| Neither key, or custom theme without appearance key | System | Following built-in pair, or fixed custom palette |

An explicit appearance takes precedence over the variant suffix of a built-in ID. Both old IDs remain accepted; with explicit appearance they identify the same built-in family. This is intentional compatibility behavior, not an independent dark/light terminal override. Users who want a fixed palette with automatic chrome use a custom file. Invalid string values get positioned diagnostics and defaults; incorrect TOML types retain the existing whole-snapshot rejection behavior. An invalid explicit appearance falls back to System, not legacy inference. Legacy inference only applies when that key is absent and a valid built-in theme key is present.

View → Appearance retains Light and Dark and adds Follow System. These are application-wide temporary appearance choices, reflected in every window. They never rewrite a config file. They affect the built-in palette together with chrome; custom palettes stay fixed. OS events update the remembered OS state even during a manual override. Follow System takes effect immediately. Unrelated reloads and custom-file edits preserve the override. A change in the parsed saved appearance clears it; changing only a custom theme does not. Legacy built-in changes also change the inferred saved appearance and therefore clear it. Selecting the same saved value by rewriting the file does not clear an override.

## 3. Theme files and supported format

`AppDirs.themes()` is the only directory root. On macOS this is `~/.config/moray/themes/`. `--config` changes the main config path only. A theme selector is either a reserved built-in ID or one basename, optionally ending in `.toml`; absent extension is appended. Names may contain spaces and Unicode but not separators, NUL/control characters, Windows-reserved filename characters, `.` or `..`. Absolute paths, nested paths, URLs and imports are not supported. No directories/files are created just by loading a theme. A selected file must resolve to a regular file within the real themes directory; an escaping symlink is rejected.

Shared Alacritty TOML layout supported in this slice:

| Table | Keys |
|---|---|
| `colors.primary` | `foreground`, `background` |
| `colors.cursor` | `cursor` |
| `colors.selection` | `background` |
| `colors.normal` | `black`, `red`, `green`, `yellow`, `blue`, `magenta`, `cyan`, `white` |
| `colors.bright` | Same eight names |

Values are quoted `#RRGGBB` (hex case insensitive); also accept `0xRRGGBB` for older theme exports. Missing supported colors inherit the fixed Moray Dark palette, independent of OS appearance. A file must specify at least one supported color. Unknown tables/keys warn and are ignored. A malformed supported value, wrong table/type, duplicate definition or syntax error rejects the entire candidate palette. Diagnostics identify the actual theme file and TOML position, or line/column zero for I/O errors. Do not place theme-file diagnostics at a fabricated config-file position.

**Explicit parent-spec amendment:** This is compatibility with the listed Alacritty color fields, not complete Alacritty configuration/rendering parity. Current `Palette` exposes four UI colors plus 16 ANSI colors. Selection foreground, cursor text, `CellForeground`/`CellBackground` references, dim colors, indexed overrides, search/hint/vi-mode colors and imports are not implemented here. Unsupported keys warn; symbolic references in supported keys reject the candidate with an explanatory diagnostic. Do not silently substitute a global color for a per-cell reference. No terminal renderer API expansion is required. [Alacritty's current configuration reference](https://alacritty.org/config-alacritty.html) documents additional color behaviors beyond this subset.

## 4. Loading, failure and lifecycle

Theme I/O/parsing runs on the configuration worker. Limit theme reads to 256 KiB plus one sentinel byte, require strict UTF-8, and use bounded regular-file reads. Poll the selected theme on the existing one-second configuration cadence even when the main config fingerprint has not changed. Fingerprint theme path, real path, modification time and size; explicit Reload bypasses both caches. Creating, replacing, deleting and repairing the selected file must be noticed. An unchanged bad candidate can cache its diagnostic; metadata changes or forced reload retry it.

Maintain desired saved configuration separately from its resolved palette. A theme error does not discard valid font, terminal, shortcut or window changes. Retain the previous successfully loaded saved palette; if no theme ever loaded, use Moray Dark. A failed transition from a built-in uses that saved built-in's palette as fallback. During a failed custom selection the fallback stays fixed, just like any custom palette. A successful retry replaces it and clears only theme diagnostics. Main-config syntax failure retains the last good selection while that selected theme continues to be watched.

Publish one immutable state containing configuration, loaded palette and combined diagnostics. Compare by value and retain existing revision/close guards so queued stale updates cannot touch disposed owners. Do not perform duplicate per-window reads or introduce another theme polling executor. Application close stops configuration polling and removes the OS listener. Pending launches receive the latest resolved theme at view attachment.

## 5. Appearance detection and UI application

Keep `BuiltinTheme` as the chrome installation choice. Introduce separate `Appearance` (System/Light/Dark), `ColorsConfig` (saved intent), and `ResolvedTheme` (chrome plus terminal palette). FlatLaf delegates and native title appearance follow resolved chrome; terminal view, pane padding and status background follow resolved palette. Toolbar/find/menu surfaces follow chrome so their controls remain legible even with a high-contrast custom terminal palette. Status text and separators derive from the custom palette's foreground, maintaining the seamless terminal/status surface.

Use the pinned `com.github.Dansoftowner:jSystemThemeDetector:3.9.1` adapter in the app module, with JitPack restricted to that group. Its official API provides `isSupported`, `getDetector`, `isDark`, `registerListener`, and `removeListener`; initialization and callbacks run outside the EDT, with value changes marshalled through the existing publisher/revision boundary. Do not initialize native detection during headless tests: inject a supplier/listener boundary and test it with synthetic events. Supported desktops include modern macOS, Windows and GNOME; other desktops use Dark fallback. Initialization/linkage failures use Dark and emit one plain diagnostic; a later valid event can recover. Register before the initial read to avoid missing a startup change, serializing initial read and callback-triggered fresh readings through the worker. Ignore event payloads when re-sampling, so an older queued event cannot overwrite a newer read. Until the asynchronous first reading, System uses Dark; startup may recolor once. No child process polling is needed in Moray.

The library owns a process-wide native observer/daemon infrastructure and has no shutdown API for it. Moray owns exactly one listener, removes it on application close, and guards already-queued callbacks. Do not claim native observer teardown. This dependency tradeoff avoids inventing a JBR/FlatLaf API and writing our own Objective-C bridge; include the additional transitive libraries in future packaging. The pinned API and observer implementation were checked against [upstream source](https://github.com/Dansoftowner/jSystemThemeDetector/tree/3.9.1); its published POM was reachable during preparation. Native behavior on this JBR/Mac remains an acceptance check, not a verified claim.

Do not read a Moray window's effective appearance to detect system changes: Moray itself explicitly sets that property. `apple.awt.application.appearance=system` remains set before Swing initialization, but it does not substitute for switching FlatLaf. [FlatLaf macOS guidance](https://www.formdev.com/flatlaf/macos/) covers native appearance configuration.

Install a new LAF only when resolved chrome changes. Custom palette edits with unchanged chrome update palette surfaces without rebuilding UI delegates. Existing rollback-on-install-failure remains: preserve old LAF, effective theme and menu choice, and report the failure. Reuse retained-pane theme update guards around real chrome switches; preserve sessions, scrollback/selection, find query/results, fonts, split components/ratios, active tab, zoom and keyboard focus. Native dimensions, tab animations and 4px terminal padding are unaffected.

## 6. Acceptance

Headless coverage must prove parser diagnostics and bounds; legacy/explicit precedence; palette-only reload with untouched config; bad/delete/repair lifecycle; explicit reload; config-error/theme-update independence; separate theme path under `--config`; manual/system transitions; custom palettes remaining fixed; all retained/hidden/zoomed/pending/new views; no shell restart; palette-only updates avoiding LAF installation; install-failure rollback; stale callback and application-close cleanup.

User-run native checks: start in macOS Light and Dark; switch while two windows and a zoomed split are open; compare chrome, title buttons, built-in palettes and status background; repeat with a high-contrast custom palette; test manual override/Follow System and live file repair; confirm typing, selection, splits and animation retain state. Check Windows/GNOME where available; unsupported desktops must remain usable with explicit choices.

## 7. Sequence after this slice

Plan 4d: application logging and launcher environment cleanup. Plan 4e: macOS `.app` packaging. Then native/performance acceptance and the daily-use switch-over gate before SSH. These labels describe the intended sequence, not completed work or implementation plans already written.
