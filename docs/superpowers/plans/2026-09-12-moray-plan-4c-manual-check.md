# Plan 4c user-run native acceptance

Status: pending. No GUI, OS detector or benchmark has been run by an unattended agent. Automated evidence: reducer/file/source tests, actual retained-component integration tests and [headless renders](../../design/plan-4c-themes/comparison.md). Automated host: macOS 26.6.2 (25G83), JBR 25.0.4.1+1-b583.48; headless 958 × 958 at 2×. Physical screens tested: none. Native run JBR/OS/screens/results: fill in during the checks below.

Use a user-chosen test config and theme filename; preserve live configuration. Follow [configuration instructions](../../configuration.md). `--config` still loads custom themes from the default themes directory.

- [ ] Record native JBR build, OS version, displays/resolutions and scaling used.
- [ ] Start in macOS Light, then separately Dark, using System and a built-in selector. Check initial/recolored title, traffic lights, menus, toolbar, terminal and status.
- [ ] Open two windows, hidden tabs and a split; zoom a pane, keep a selection/find query and output history. Switch OS Light/Dark. Check all retained panes, newly opened windows and pending launches use the right theme without shell restart.
- [ ] Check native title buttons, focus, window movement/resizing, tab animation and split divider positions survive switches.
- [ ] Choose manual Light/Dark in one window, verify all menus synchronize and OS changes do not change the explicit choice. Choose Follow System and verify immediate application of the latest OS state.
- [ ] Repeat with a high-contrast custom palette. Chrome follows OS/manual choice; terminal, 4px padding and status retain exact custom background with readable metadata/diagnostics. Find controls use chrome colors.
- [ ] Edit only the custom file; check automatic recoloring and preservation of manual appearance, sessions, find, font overrides, zoom and selection. Test Reload config.
- [ ] Delete/break and repair the selected theme. Last good colors stay while diagnostics name the actual file; repair clears only theme errors. Make a main-config syntax error and confirm the last selected theme still reloads.
- [ ] Change saved appearance and check the manual override clears; unrelated config edits and same-value rewrites preserve it. Verify legacy built-in-only config behavior.
- [ ] Confirm typing, application shortcuts, selection, copy/paste, split focus and normal close/Quit still work after repeated switches.
- [ ] Where available, repeat on Windows and GNOME; record versions/displays/results. Verify unsupported desktops retain usable Dark fallback and explicit appearance choices.
- [ ] Close every window/Quit after theme activity; check no late UI callbacks or errors. Moray removes its listener; the dependency's process-wide daemon/native observer has no shutdown API.

Logging/launcher cleanup, packaging, performance and daily-use acceptance remain later work. Do not interpret headless proof as native acceptance.
