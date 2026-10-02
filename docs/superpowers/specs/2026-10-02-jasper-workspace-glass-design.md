# Jasper workspace glass: opacity, blur and the Clear themes

**Status:** Design approved in chat by the user on 2026-10-02, section by section. Plan 1 (foundation) executed on claude/workspace-glass; plan 2 (editor) not written. Plan 1 deviations are listed in the plan and docs/STATUS.md.

## Purpose

The user wants the terminal and editor areas of a Jasper window to be translucent, with an adjustable opacity and a
background blur, like macOS Terminal.app. Terminal.app's built-in **Clear Dark** profile (and its sibling **Clear
Light**) are ported to Jasper themes, including their opacity and blur. The settings live under
Appearance › Workspace.

## Feasibility (spike, 2026-10-02)

A throwaway spike on JBR 25.0.4 (Metal) with a decorated `JFrame` (full-window content, transparent title bar, as
`MacTitleBar` installs) established:

- `Frame.setBackground` with alpha throws `IllegalComponentStateException: The frame is decorated`, and JBR has no
  translucency API (jbr-api 1.9.0). `MethodHandles.findSpecial` cannot skip `Frame`'s override (invokespecial resolves
  from the direct superclass).
- What works: after `addNotify`, set `Component.background` to transparent by reflection, call
  `java.awt.peer.WindowPeer.setOpaque(false)` and `ComponentPeer.setBackground(transparent)` on the peer, and make the
  root and layered panes non-opaque. This needs `--add-opens java.desktop/java.awt=ALL-UNNAMED` and
  `--add-exports java.desktop/java.awt.peer=ALL-UNNAMED`. Setting only the peer is not enough: Swing still treats the
  window as opaque and composites over the frame's light-grey background.
- With that, opaque Swing children stay opaque, a component filling a translucent colour shows the desktop through
  it, and 75 repaints at 20 Hz did not accumulate alpha.
- JAWT returns the frame's `MTLLayer` (its `delegate` is nil); the owning `NSWindow` is found by scanning
  `NSApp.windows` for the window whose content view's layer is, or directly contains, that layer.
- The private `CGSSetWindowBackgroundBlurRadius(CGSDefaultConnectionForThread(), windowNumber, radius)` (used by
  Terminal.app, iTerm2, Alacritty and WezTerm) returns 0 and blurs the desktop behind translucent pixels only; radius 0
  gives clear glass.

## Clear Dark and Clear Light (decoded from Terminal.app)

Source: `/System/Applications/Utilities/Terminal.app/Contents/Resources/Initial Settings/Clear {Dark,Light}.terminal`,
colours unarchived and converted to sRGB.

| Key | Clear Dark | Clear Light |
| --- | --- | --- |
| Background | `#212734`, alpha 0.95 | `#FFFFFF`, alpha 0.93 |
| Blur (active / inactive) | 0.5 / 0 | 0.5 / 0 |
| Text | `#E6E6E6` | `#3A4851` |
| Bold text | `#F0F0F0` | `#313C44` |
| Selection | `#334E5E` | `#E5ECF1` |
| Cursor | not set (Terminal default) | `#919191` |
| Black / bright | `#35424C` / `#465C6D` | `#2D3840` / `#506573` |
| Red / bright | `#B45648` / `#DF6C5A` | `#B45648` / `#DF6C5A` |
| Green / bright | `#6CAA71` / `#79BE7E` | `#6CAA71` / `#79BE7E` |
| Yellow / bright | `#C4AC62` / `#E5C872` | `#C4AC62` / `#E5C872` |
| Blue / bright | `#6D96B4` / `#67B5ED` | `#5685A8` / `#49A2E1` |
| Magenta / bright | `#BD7BCD` / `#D389E5` | `#AD64BE` / `#D389E5` |
| Cyan / bright | `#7CCBCD` / `#84DDE0` | `#69C6C9` / `#77E1E5` |
| White / bright | `#DEE5EB` / `#E5EFF5` | `#C1C8CC` / `#D8E1E7` |
| Font | `.AppleSystemUIFont` 12 (SF Mono) | same |

## 1. Behaviour and data model

### Theme format

Native theme TOML gains an optional top-level `[workspace]` table (added to `ThemeToml`'s top-level allowlist):

```toml
[workspace]
opacity = 0.95   # 0.10–1.00; absent means 1.0
blur = 0.5       # 0.0–1.0, Terminal.app's scale; absent means 0
```

Values outside the range are errors with the file's line, like other theme diagnostics. Both values are inherited
through `extends` (child wins), written by `ThemeTomlWriter`, and carried on `GeneratedTheme` so the resolved
workspace theme exposes them. The 284 bundled themes do not set the table, so they stay opaque and the converter's
drift test is unchanged. Only the **workspace** theme's values matter; a chrome theme's `[workspace]` table is ignored.

### Settings

The Appearance page gains a **Workspace** section:

- **Opacity** — `ui.workspace.opacity`, a percentage 10–100, with "Use theme's value" (unset).
- **Blur** — `ui.workspace.blur`, a percentage 0–100, with "Use theme's value" (unset).
- A **Choose workspace theme…** button that navigates to `themes#workspace`.

`config.toml` stores percentages (`opacity = 95`); theme files store fractions, as Terminal.app does. Unset defers to
the theme, following the `ui.font.size` pattern (a default outside the range plus `Editors.optionalNumber`); an
out-of-range value falls back to unset with the usual settings warning. Changes apply live to every open window, like
a theme switch.

**Effective values:** `opacity = setting ?? workspaceTheme.opacity ?? 1.0`, `blur = setting ?? workspaceTheme.blur ?? 0`.

### What is translucent

Only the **default background** of the terminal grid, the editor text area and gutter, and the editor's current-line
highlight. Explicitly coloured cells (ANSI backgrounds, a vim colour scheme), the selection, the cursor and search
highlights stay solid, as in Alacritty's default. Pane tabs, split dividers, the find bar and all chrome (title bar,
toolbar, status bar, rail, tool windows) stay opaque. Non-editor plugin panes in the center stay opaque. Blur shows
only where something is translucent. Detached tool windows and dialogs are never translucent.

### Platforms

macOS only. On other platforms the two rows are disabled with "Requires macOS"; saved values are kept and ignored.

### Inactive windows

Not ported: Terminal.app's Clear profiles drop blur when the window loses focus (`BackgroundBlurInactive 0`). Jasper
keeps blur constant (user decision, 2026-10-02).

### The Clear themes

Two hand-authored bundled native themes, `Clear Dark` and `Clear Light` (catalog 284 → 286), with the colours above,
`[workspace]` 0.95/0.5 and 0.93/0.5, accents from each profile's bright blue (`#67B5ED`, `#49A2E1`), and `source`
naming macOS Terminal.app's profile. Clear Dark sets no cursor colour, so it uses the generator's default. Bold colour
and font are not ported (Jasper has no per-theme bold colour or font). They are not produced by the converter and are
not in `tools/themes/sources.json`.

## 2. Window and native layer

### `WindowGlass` (`dev.jasper.app.platform`)

One per terminal window. When the effective opacity is below 1.0 on macOS it applies the spike's sequence (transparent
`Component.background`, `WindowPeer.setOpaque(false)`, peer background, non-opaque root pane, layered pane and the
`MacTitleBar` wrapper) and sets the blur radius through the native bridge. When opacity returns to 1.0 it restores the
opaque peer, the original background and blur 0, so the window behaves exactly as today. At 1.0 no JDK internals are
touched, even when a blur is set. It applies once the peer exists and re-applies when the peer is recreated
(displayability changes, as `MacTitleBar` already handles).

It publishes the **opacity in effect**: 1.0 whenever glass could not be enabled (another OS, inaccessible internals
after a JBR change, missing library). All painting reads this value, never the raw setting, so any failure yields
today's opaque window rather than alpha over a black buffer. Failures are logged once at warning level through
`AppLog`; no UI.

All reflective and peer access is confined to `WindowGlass`.

### Native bridge `libjasper-glass.dylib`

`MacGlass.m`: JAWT gives the frame's layer; the owning `NSWindow` is the one whose content view hosts it; the blur call
runs on the AppKit main thread (`dispatch_async` to the main queue, never synchronously, so the EDT cannot deadlock
against AppKit). Blur maps linearly from 0–1 to a radius of 0–40 px (Clear's 0.5 → 20 px); the constant is tuned during
the GUI check.

The build mirrors `gradle/mac-notifications.gradle`: clang, ad-hoc codesign, a native test (`MacGlassTest.m`) that
`check` runs on macOS, bundling into `Contents/app/`, and architecture/signature verification in `packaging.gradle`.
Unlike notifications, the `run` task passes `-Djasper.glassLibrary=<build path>` so glass also works under
`./gradlew :jasper-app:run`; packaged apps load it from `Contents/app/`.

### JVM flags

`--add-opens java.desktop/java.awt=ALL-UNNAMED` and `--add-exports java.desktop/java.awt.peer=ALL-UNNAMED` are added to
`applicationDefaultJvmArgs`, the hand-written run/bench/preview `jvmArgs`, and the `Jasper.cfg` check in
`packaging.gradle`.

## 3. Painting

Rule: **containers are clear, leaves paint their own glass**. Every translucent pixel gets exactly one alpha fill.

### jasper-terminal

Additive public API: `TerminalOptions.Builder.backgroundOpacity(double)` (default 1.0) and
`TerminalView.setBackgroundOpacity(double)`. Below 1.0 the view is non-opaque and `TerminalPainter` fills the default
background with that alpha using the `Src` composite; runs whose background differs from the default, the cursor,
selection and search highlights stay solid; a block cursor's glyph is drawn in the solid background colour. The
inactive-dim overlay and visual bell keep their alpha on top. At 1.0 the paint path is unchanged.

### App window composition (only while the opacity in effect is below 1.0)

- `TerminalPane` paints its padding in the background colour with alpha; its `TerminalView` paints the cells. They do
  not overlap.
- The `MacTitleBar` wrapper, `WindowContent` and its body, `TerminalDeck` and `TerminalTab` are non-opaque and paint
  no background.
- `WorkspaceRegions.Host` paints chrome around the rounded workspace surface (corners, gutters) but leaves the surface
  clear; in hidden-rail mode the surface is the whole rectangle.
- `WindowContent` paints chrome everywhere except the workspace surface, so no other gap can expose the desktop.
- The header, rail, status bar, `WindowTabs` (including its overflow fade), find bar and `SplitDividerBorder` are
  unchanged and opaque. `WorkspaceStyler` keeps non-editor plugin panes solid.

### Editor

- **SDK 0.12.1:** `Appearance.workspaceOpacity()`, a default method returning 1.0, documented as the opacity in effect
  (UI thread only; 1.0 when glass is off). `onWorkspaceChanged` also fires when it changes. The host implements it from
  `WindowGlass`.
- **Editor plugin** (next version, requiring `>=0.12.1`): below 1.0, `EditorTheme.apply` makes the text area, viewport,
  scroll pane and gutter non-opaque; the text area and gutter paint the editor background and current-line colour with
  that alpha; the selection stays solid. If `RSyntaxTextArea` does not repaint cleanly when non-opaque, the fallback is
  a thin background-painting wrapper in the plugin, not a library change.

## 4. Testing, documentation and plans

### Tests (headless, TDD)

- Theme: `[workspace]` parsing (ranges, line-numbered errors), `extends` inheritance, writer round-trip; effective-value
  resolution (setting over theme, unset defers, out-of-range falls back).
- Clear Dark/Light: every colour against the table above; catalog tests 284 → 286.
- Terminal: pixel tests on an ARGB `BufferedImage` — default cells carry the expected alpha, explicit backgrounds are
  solid, a second paint gives identical pixels, 1.0 matches the current output.
- Window composition: with a fake `WindowGlass` reporting 0.9, a headless render of `WindowContent` has translucent
  pixels only inside the workspace surface and opaque chrome elsewhere; at 1.0 it matches the current render.
- `WindowGlass`: decision logic through an injected peer/bridge seam — non-macOS, inaccessible internals, missing
  library, and 1.0 touching no internals; each failure reports 1.0 and logs once.
- Editor: `EditorTheme` alpha/opacity rules; an SDK testkit contract case for `workspaceOpacity()`.
- Native: `MacGlassTest.m` — radius mapping and clamping, nil window is a no-op.
- Architecture gates, doclint and source hygiene.

### GUI checks (user-run; the agent does not launch Jasper)

Clear Dark next to Terminal.app's; blur radius at 0.5; live opacity and blur changes; splits, editor tabs, hidden-rail
mode, native fullscreen; switching back to an opaque theme; a packaged build (`Contents/app/libjasper-glass.dylib`).

### Documentation

`docs/themes.md` (`[workspace]`), app maintenance recipe (`WindowGlass`, JVM flags, the dylib), SDK architecture
(0.12.1), `docs/STATUS.md`.

### Plans

1. **Glass foundation** — theme `[workspace]`, settings rows, Clear Dark/Light, terminal `backgroundOpacity`,
   `WindowGlass`, dylib, JVM flags, window composition. Result: terminals are glass in Clear Dark.
2. **Editor glass** — SDK 0.12.1, testkit contract, editor plugin.

Work happens on `claude/workspace-glass` in `.worktrees/workspace-glass`.
