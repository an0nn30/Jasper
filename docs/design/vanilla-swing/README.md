# Standard Swing appearance

Originally developed in `.worktrees/vanilla-swing`, branch `codex/vanilla-swing`, based on committed main `a42c20f`. Integrated with the Jasper rebrand in `.worktrees/jasper-rebrand` on `codex/jasper-rebrand`. The combined app defaults to Motif per the rebrand direction; Metal remains selectable and is the unavailable-LAF fallback. Standard LAF dividers replace the older FlatLaf-only border. Source worktrees and the separate rail/vault work remain untouched.

The user requested default Java Swing controls and colors, a normal OS title bar, standard content-area tabs, GNOME 2 icons, and a white-on-black terminal. `ui.laf` chooses the Java built-in look and feel, with Motif as Jasper's default. The [configuration guide](../../configuration.md#swing-look-and-feel) and [copyable example](../../../config.example.toml) list every supported selector and platform availability.

This bounded visual amendment replaces the prior custom chrome and themes; no new implementation-plan document was needed. Independent config/control implementation and review were used. Legacy animation, title-bar and custom-theme tests were removed with those features; configuration, keyboard, session, split and palette behavior coverage was retained and adapted to standard Swing. Old `colors.*` and `window.tab_height` keys remain readable with deprecation warnings; theme files are no longer read or watched.

The terminal ANSI colors are classic xterm colors; default text and cursor are white and the background is black. Existing inactive-pane dimming still applies, so the inactive split in the previews is intentionally dimmer.

## Headless renders

Actual app components at 1000 × 650 logical pixels, rendered at 2×. The native OS title bar is not included because no JFrame or desktop window is created. The actual LAF divider painter is invoked explicitly because peerless Swing printing skips the AWT divider. Settings and Reload are disabled in this fixture because it has no configuration service attached.

| Look and feel | Split terminal | Command palette |
|---|---|---|
| Metal | [Preview](mock-ui-metal.png) | [Preview](commands/mock-ui-metal.png) |
| Nimbus | [Preview](mock-ui-nimbus.png) | [Preview](commands/mock-ui-nimbus.png) |
| Motif | [Preview](mock-ui-motif.png) | [Preview](commands/mock-ui-motif.png) |

Reproduce from the worktree with absolute output directories:

```sh
./gradlew :jasper-app:mockUiPreview --args='/absolute/output --palette --splits'
./gradlew :jasper-app:commandPalettePreview --args='/absolute/output/commands'
```

## Verification and native acceptance

Final `./gradlew check --rerun-tasks`: 598 tests, 597 passed, one existing font skip, zero failures/errors; all eight tasks executed. Source hygiene and `git diff --check` passed. Scoped re-review found no remaining actionable issues.

Headless tests cover built-in selection and fallback, malformed config retention, live updates across windows and retained/hidden/zoomed panes, preserved sessions and split ratios, macOS editing shortcuts, Nimbus selected-row painting, and tab close/reorder/focus behavior. The branch review caught tab-padding focus and middle-click regressions; both were reproduced with failing tests and corrected.

Native title-bar appearance, Aqua/Windows/GTK desktop rendering, screen-menu integration and physical keyboard/focus checks remain user-run. No GUI, packaging, merge or push was performed.

The bundled OldGnome2 files retain their original pixels. [Provenance](../../../jasper-app/src/main/resources/dev/jasper/app/icons/oldgnome2/README.md) records their source and the absence of a license document in the supplied archive.
