# Jasper Retro Metal appearance

**Status:** Approved for native implementation by the user's “execute natively” instruction; implemented on `codex/retro-metal`. Headless acceptance and the independent review fix pass passed; native OS acceptance remains user-run. The user supplied GPL2+ licensing for OldGNOME2. Subsequent screenshot feedback supersedes stock borders for app chrome: icon-only 20-pixel tab-close targets, flat compact toolbar controls with 24-pixel GNOME artwork, and unboxed status actions. Form buttons retain stock Metal.

**Plan:** [Implementation plan](../plans/2026-09-22-jasper-retro-metal.md).

## Intent and decisions

Provide one configuration option that changes Jasper's presentation to a retro Java application: stock light Swing Metal controls, a black high-contrast terminal, and bundled OldGNOME2 application icons. Existing commands, terminal sessions, plugins, and SDK consumers keep working. Modern is the default, with its existing appearance and behavior.

The user's wording is “Metal.” This design interprets that as the current JDK's unmodified `MetalLookAndFeel` and `OceanTheme`, not the older `DefaultMetalTheme`/Steel. This choice is explicit for review; changing it to Steel would change the theme instance and reference renders, not the design. Do not introduce a third theme selector.

The application remains one product with one workspace, command registry, plugin host, and terminal implementation. The change provides presentation branches at existing ownership boundaries. A general skin framework or new plugin API is unnecessary.

## Configuration and restart semantics

```toml
[ui.theme]
style = "retro"   # "modern" (default) or "retro"; requires restarting Jasper
variant = "dark" # modern only; retained while retro uses light controls
```

`ThemeStyle { MODERN, RETRO }` is an application config value. `Appearance { LIGHT, DARK }` remains a brightness value. Do not add `RETRO` to the SDK's `Variant` or to `Appearance`.

Capture style from the first accepted configuration snapshot, before installing a LAF or constructing app/Buddy/plugin UI. Retain that style for the process lifetime. Every new window and plugin surface uses the captured style, including after a configuration reload requests another style.

On reload, accept and preserve the desired style in the saved snapshot, but display one derived configuration warning while it differs from the running style: `Restart Jasper to apply ui.theme.style.` Reverting the setting to the running style clears that warning. Do not start a replacement process, close sessions, or switch any delegates in response to the warning. A resident process must fully quit/restart; closing and reopening its last window is insufficient. Existing restart flows keep their close guards.

Other valid live settings continue to apply. Modern light/dark switching remains live while a style change is pending. Retro always resolves to light app appearance and the retro terminal palette. Saved modern `variant` and temporary overrides never install FlatLaf in retro. Disable the Light/Dark commands and the custom tab-height command in retro; show a short explanation in the Appearance menu. Persisted tab height remains available for modern mode. Retro uses Metal's preferred tab size.

Follow the existing parser policy: a missing style means modern; an invalid type rejects the snapshot, while an unknown string value reports the exact key/location and uses modern for that field; malformed TOML retains the last accepted service snapshot. Never echo arbitrary invalid text into diagnostics.

## Look and feel and colors

Add an app-internal `BuiltinTheme.RETRO` with light appearance and a separate palette. `ThemeController` owns installation and rollback. Install `new OceanTheme()` and `new MetalLookAndFeel()` on the EDT. Do not load Jasper's FlatLaf properties or `BrandedButtonUI` into the Metal LAF.

Install only Jasper's semantic color aliases and missing diagnostic aliases in `UIManager.getLookAndFeelDefaults()`, deriving ordinary UI colors from Metal defaults. Do not replace Metal's button, list, text field, font, border, tab, scrollbar, menu, or split-pane delegates. Do not put persistent application overrides in `UIManager`'s developer-defaults table. A failed installation restores the prior LAF and Metal theme, without publishing a new resolved theme.

`RetroPalette` lives in the application appearance package and builds the existing public terminal `Palette` value. Defaults: foreground/cursor `#ffffff`, background `#000000`, selection `#264f78`. ANSI values, in index order:

```text
000000 ff5555 55ff55 ffff55 6688ff ff55ff 55ffff dddddd
888888 ff8888 88ff88 ffff88 99bbff ff88ff 88ffff ffffff
```

This is a readable default palette, not a filter over application output. Preserve the terminal's 256-color table, truecolor, OSC color handling, cursor settings, font settings, and emulation behavior. No terminal-module code or public signatures need to change.

All application chrome, including the status bar and tab strip, uses light Metal colors. Never derive their backgrounds from the black terminal palette. Preserve semantic error, warning, and running indicators. Buddy's artwork/animation and the Jasper application/Dock logo remain product identity; existing light appearance is supplied to Buddy.

## Workspace presentation

### Tabs

Use the retained `TerminalDeck`/`JTabbedPane` selection and content model. In retro its `updateUI()` delegates to Swing, yielding `MetalTabbedPaneUI`; in modern it retains the current hidden FlatLaf tab area and `WindowTabs`.

Retro shows stock Metal tabs beneath the ordinary OS title bar, including for one tab. A small transparent Swing tab header contains a plain title label, the current shortcut hint, and a GNOME close button. No custom tab background painting or tab animation. Scroll-tab layout handles many tabs. Labels disable HTML parsing, and full titles remain available as tooltips and accessible names.

Retain selection, close-button behavior, middle-click close, drag-to-reorder with the current five-logical-pixel threshold, keyboard selection, title updates, and focus restoration. Header handlers call `WindowContent.selectTab`, `closeTab`, and `reorderTab`; never hold copied session state. Reordering and renaming retain header identity by `TerminalTab` identity. No additional visible modern tab strip or hidden animation timer in retro.

### Toolbar, rail, status, palette

Use ordinary `JToolBar`, `JButton`, `JToggleButton`, and separators in retro. Keep built-in and contributed commands, dropdowns, enabled states, accessible names, tooltips, and all toolbar visibility modes. Retain the current adaptive toolbar geometry, but use Metal button preferred sizes instead of the modern fixed 30-pixel height. Compact to icons when necessary and keep all controls reachable; do not introduce clipping at the minimum window size.

Skip modern custom button painting, rounded primary fills, forced 11.5/10-point label fonts, empty button borders, and FlatLaf-only button properties in retro. Rail controls use Metal defaults. Status metadata uses Metal label fonts; actionable contributed status items and the configuration button use normal button delegates. Inert plugin status text remains inert.

The command palette keeps its controller, query, result rows, plugin scopes, forms, keyboard behavior, and placement. Its custom card/selection/badge corners become square, shadows disappear, and text controls regain their Metal borders. This is the same component with presentation conditions, not a second palette implementation.

### Native windows

Both terminal windows and `NativeShells` auxiliary windows bypass `MacTitleBar` customization in retro. Set the root content pane and keep normal decorated `JFrame`/`JDialog` behavior. Preserve title updates, ownership, modality, close guards, geometry persistence, focus restoration, menus, and disposal. No transparent/full-window-content properties or JBR custom title bar should be installed.

User amendment (2026-09-23): retro application menus stay inside each window on macOS; modern retains the macOS screen menu bar. Select this through the startup JVM property before toolkit/LAF initialization; configuration reload cannot change it. OS-owned title bars and the existing native file chooser remain OS UI; Metal governs Swing client controls. Do not replace the file picker or change restart/packaging behavior to make native UI resemble Metal.

## Icons and redistribution

Use a small, explicit mapping of the application's semantic icon names to OldGNOME2 PNG assets copied from `/Users/dustin/Downloads/OldGNOME2`. Bundle dereferenced bytes, never symlinks, `icon-theme.cache`, or a dependency on a desktop GNOME installation. `index.theme` inherits `gnome`; the packaged subset must therefore be self-contained. The inspected collection is about 22 MB; there is no reason to ship the whole tree.

The implementation plan names every selected source and resource name. Preserve native-size color artwork. Supply the available 16- and 24-pixel images for high-DPI drawing and verify their actual dimensions; do not generate larger artwork by relabeling upscaled PNGs as native assets. Use standard image drawing with a deterministic interpolation policy when a device scale exceeds the available artwork.

`AppIcons.icon(String)` returns Swing `Icon` rather than a concrete `FlatSVGIcon` (this is internal app visibility, not SDK ABI). Modern still uses existing Tabler artwork. Existing SDK `Appearance.icon(resourcePath)` keeps its SVG-from-the-plugin-jar contract and remains functional under Metal. FlatLaf's SVG decoder/scaling utilities may remain dependencies; retro installs no FlatLaf UI delegates.

**Known packaging prerequisite:** the inspected OldGNOME2 directory contains no license/attribution document. Before committing redistributed artwork, obtain the exact collection's source and redistribution terms, record source paths, hashes, authors/notices and the license text under the bundled resource directory. Do not assume an unrelated GNOME/Tango license applies. If this evidence cannot be obtained, complete code/test work with private local fixtures but report asset packaging as blocked; do not call the retro deliverable complete or silently substitute another icon set.

## Plugins and SDK compatibility

Plugins receive existing `Variant.LIGHT` in retro, regardless of the black terminal background. No enum member, method, event payload, dependency, capability, plugin descriptor range, or SDK binary version is added. Clarify `Variant` documentation: it describes app chrome, not a guarantee about terminal background.

Ordinary plugin Swing controls use the active LAF. Host-provided toolbar, rail, menus, panels, dialogs, and window shells receive the retro presentation. Hidden panels receive the usual UI refresh and retain their component identity, subscriptions, handlers, and data. Plugins continue to load and dispose through the existing host.

Arbitrary plugin icons/custom drawing cannot be converted to GNOME artwork automatically. Preserve their semantics and their existing resource contract. Bundled Vault's custom action icons and plugin-supplied SVGs continue to render legibly; there is no special class-loader/path substitution or app-to-Vault dependency. An opt-in semantic icon SDK is outside this deliverable. Thus “transparent to plugins” means no changes are required for compatibility, not that all third-party artwork is replaced.

## Alternatives considered

1. **Only install Metal:** insufficient; the current `TerminalDeck` forcibly installs a FlatLaf delegate and modern chrome paints itself.
2. **Runtime-switchable skin system:** unnecessary lifecycle and native-peer risk for a user who accepts restarting.
3. **Startup style with focused presentation branches:** chosen; retains one behavior/model layer and minimizes ABI and lifecycle changes.

## Global constraints

- Java 25 on JetBrains Runtime 25; use `./gradlew`, never system Gradle.
- No new runtime dependency, emulator backend, public terminal API, or SDK signature.
- Swing and global look-and-feel mutation run on the EDT; preserve terminal/session threading.
- Preserve package DAGs; SDK imports in app production remain confined to `dev.jasper.app.plugins`.
- Do not launch the GUI or benchmark; native acceptance belongs to the user.
- Work on a `codex/` branch; never commit on `main`, merge, or push without authorization.
- End commit messages with `Co-Authored-By: Codex <noreply@openai.com>`.
- Preserve unrelated working-tree changes and existing plugin/session lifecycle behavior.
- Record execution deviations in the plan status banner and `docs/STATUS.md`.

## Verification and acceptance

Headless tests cover parser/default compatibility; captured style and restart-warning reversal; stock Metal delegates/defaults; modern defaults after Metal; icon resource integrity and scaled painting; real tab selection/close/reorder/rename; toolbar contributions; palette forms; hidden panels; SDK LIGHT and SVG behavior; auxiliary shell policy and lifecycle; unchanged sessions during reload. Exercise both launch directions using separate controller lifetimes, never a production live style switch.

Run all architecture guards, full `./gradlew check`, and `:jasper-app:installDist`. Inspect XML counts. Render representative lightweight components into images on EDT in headless mode, without a native window or login shell. Inspect modern light/dark and retro at 1x and 2x.

User acceptance covers a fresh retro launch, all terminal gestures/shortcuts, several tabs and splits, toolbar modes, plugin contributions and Vault windows, native picker ownership, reload with a pending style change, resident-process restart, and return to modern. No claim of native acceptance before the user performs it.
