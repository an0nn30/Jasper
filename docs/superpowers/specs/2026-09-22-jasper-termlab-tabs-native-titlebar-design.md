# Jasper TermLab Tabs and Native Title Bar — Design

**Status:** Approved 2026-09-22 (user decisions recorded in section 2). Supersedes the
presentation half of `docs/design/iterm-title-bar-reference.md`, the macOS window-integration
section of `2026-09-11-jasper-plan-3-5-titlebar-themes-design.md`, the tab-strip architecture of
`2026-09-11-jasper-mock-ui-design.md` and all of `2026-09-11-jasper-tab-motion-design.md`. The
title-source rules of the iTerm reference (OSC 7 directory, foreground-job polling, Buddy
capture) stay in force except where section 5 changes the composed text.

**Scope:** Replace Jasper's integrated macOS title-bar tabs with a TermLab-style tab strip under
the operating system's own title bar on every platform; make that title bar follow the
configured light or dark theme where the platform allows; compose tab and window titles the way
TermLab does; and ship the packaged macOS app with a launcher that AppKit treats as built
against the current SDK so it receives the macOS 26 window controls.

**Not in scope:** Custom-drawn window decorations on any platform; a TermLab-style custom title
bar with inline menus on Windows and Linux; tab drag-out to new windows; a tab-switcher overlay;
tabs for editor panes; changes to the plugin SDK; changes to the Buddy's title capture; a fix for
the window controls under `./gradlew :jasper-app:run`.

---

## 1. Why

Jasper's tabs live inside a JBR custom title bar on macOS, iTerm-style: equal-width, animated,
hover-only close buttons, shortcut hints and a plus button, with the window title hidden and the
frame's content pulled up under the stoplights. The user's 2026-09-22 screenshots compare that with
TermLab (the `~/projects/conch` Rust/Tauri app): a native title bar carrying the active tab's title,
and beneath it a flat 28-pixel strip of content-width tabs with a leading terminal glyph, an
always-visible close cross and an accent underline under the active tab. The user wants Jasper's
tabs to look and behave like TermLab's, the title bar to be the system's on macOS, Windows and
Linux, and that title bar to be light or dark according to `ui.theme.variant`.

The same screenshots show Jasper's stoplights in the pre-macOS-26 flat style while TermLab has the
new controls. A spike on 2026-09-22 established the cause: AppKit draws the macOS 26 window
controls only for a process whose main executable records a macOS 26 or later SDK in its
`LC_BUILD_VERSION` load command. Both the JetBrains Runtime's `java` binary and the jpackage
launcher stub inside `Jasper.app` record SDK 13.3. Rewriting that one load command on a copy of
`java` with Apple's `vtool` and re-signing it produced the new controls for a plain Swing frame with
no other change. Tauri gets them because cargo links against the installed SDK.

## 2. Decisions

The user chose these on 2026-09-22:

1. **The title bar is drawn by the operating system on every platform.** On Windows the native
   caption's colours are set through the Desktop Window Manager attributes using the Java Foreign
   Function and Memory API; no JNI or JNA. On Linux the decorations follow the desktop theme and
   Jasper does not try to override them per window (section 3.3 records why).
2. **Tab titles use TermLab's composition** including the ` — cols×rows` suffix, even though the
   status bar also shows the size.
3. **TermLab look, Jasper's invisible gestures kept.** Drag reorder, middle-click close and
   overflow scrolling stay because they do not change the look. The plus button, the per-tab
   shortcut hints, the equal-width layout, the width animation and the overflow arrows go. TermLab's
   right-click menu (Rename Tab, Close Tab), F2 and inline rename are added.
4. **Liquid Glass through a post-jpackage `vtool` step**, proven by the spike first. Recorded
   deviation from the approved question wording: the approved plan was a small C launcher stub; the
   spike showed the jpackage launcher is self-contained (it links only Cocoa, libc++ and libSystem
   and reads `Jasper.cfg` itself), so rewriting its build-version load command is strictly simpler
   and carries no launcher logic of its own. The user accepted that substitution in the design
   review.

Two further choices were presented and accepted in the same review:

5. **Closing a tab selects its neighbour** (the previous tab, or the next when the first closes),
   which is Swing's existing behaviour, not TermLab's jump to the first tab.
6. **Linux decorations are the desktop theme's.** No per-window light or dark override.

## 3. Window chrome

### 3.1 All platforms

Every `TerminalWindow` and every auxiliary plugin window (`NativeShells`) is a plain decorated
`JFrame`. The frame's title is set with `JFrame.setTitle` and is visible. The frame's content pane
is the workspace content directly; nothing is layered above it for the title.

Removed: `dev.jasper.app.platform.MacTitleBar`, `WindowContent.installTitleBar`, the
`apple.awt.fullWindowContent`, `apple.awt.transparentTitleBar` and `apple.awt.windowTitleVisible`
root-pane properties, the `FULL_WINDOW_CONTENT_BUTTONS_BOUNDS` listener, the `jbr-api`
`WindowDecorations` usage, `WindowContent.onThemeChanged`'s title-bar consumer, and the
`TitleBarPreview` renderer. The `jbr-api` dependency stays only if another use remains after the
removal; if `MacTitleBar` was its last user it is removed from `jasper-app/build.gradle.kts` too.

`WindowContent` keeps `onTitle`; `TerminalWindow` wires it to `frame::setTitle` directly. The
layout stays `north = tabs above toolbar`, `body = rail + regions`, `south = status bar` on every
platform, which is what non-macOS already does today.

### 3.2 Platform appearance adapters

A new interface-free pair of small classes in `dev.jasper.app.platform`, each with one public
static entry point called by `TerminalWindow` and `NativeShells` after the frame is displayable and
again on every theme change:

- **`MacWindowAppearance.apply(JFrame frame, boolean light)`** sets the root pane's
  `apple.awt.windowAppearance` client property to `NSAppearanceNameAqua` or
  `NSAppearanceNameDarkAqua`. `ApplicationBootstrap` keeps `apple.awt.application.appearance=system`
  so dialogs and menus follow the system while windows follow the theme. This is the existing
  behaviour of `MacTitleBar.setLight`, moved.
- **`WindowsTitleBar.apply(JFrame frame, boolean light, Color caption, Color text)`** runs only when
  `SystemInfo.isWindows_10_orLater`. It resolves the frame's `HWND` by temporarily setting a unique
  title (a random UUID string), calling `FindWindowW(NULL, title)` and restoring the real title in
  a `finally`. It then calls `DwmSetWindowAttribute` with `DWMWA_USE_IMMERSIVE_DARK_MODE` (20) set to
  `!light`, and, when `SystemInfo.isWindows_11_orLater`, `DWMWA_CAPTION_COLOR` (35) and
  `DWMWA_TEXT_COLOR` (36) with the chrome colours as `COLORREF` (`0x00BBGGRR`). All calls go through
  `java.lang.foreign` (`Linker.nativeLinker()`, `SymbolLookup.libraryLookup("dwmapi", arena)` and
  `user32`). Any `Throwable` is logged once at `DEBUG` and swallowed; the window stays usable with
  whatever caption Windows drew. The decision of which attributes to set is a pure static method
  `WindowsTitleBar.plan(boolean light, boolean windows11, Color caption, Color text)` returning a
  small record list, so the logic is unit-tested headlessly on any host; the native call is the only
  untested line.

The caption colour is `Jasper.titleBackground` and the text colour `Jasper.titleForeground`,
resolved after the FlatLaf theme is installed, so the Windows caption matches the toolbar surface
below it exactly.

### 3.3 Linux

Server-side decorations on X11 and Wayland belong to the window manager, which colours them from
the desktop theme. The only per-window override, the `_GTK_THEME_VARIANT` X property honoured by
mutter, needs the window's XID, which Java does not expose without internal APIs, and does not exist
on Wayland. Jasper therefore sets nothing on Linux. `docs/configuration.md` records that
`ui.theme.variant` changes Jasper's own chrome on Linux while the title bar follows the desktop
theme.

## 4. The tab strip

### 4.1 Component

`dev.jasper.app.workspace.WindowTabs` is rewritten as a fixed-height strip over the same retained
`TerminalDeck` selection model. `TerminalDeck` (the hidden `JTabbedPane`) and `WindowContent`'s tab
lifecycle (`openTab`, `selectTab`, `reorderTab`, `closeTab`, `update`) are unchanged.
`TabMotion` is deleted.

The strip is a `JPanel` with a hand-rolled `doLayout`; its children are one `Entry` per tab, laid
out left to right at each entry's preferred width. The public seam stays: `refresh()`, `setActive`,
`close()`, and the component names `windowTabs`, `select:<title>` and `close:<title>` that tests
address. `newTab`, `previousTabs`, `nextTabs` and `shortcut:<title>` no longer exist.

### 4.2 Metrics and colours, from TermLab's `layout.css`

| Property | Value |
|---|---|
| Strip height | `window.tab_height`, default **28**, range 24–48 logical pixels |
| Visibility | hidden with zero or one tab |
| Tab width | content width, capped at 220; the label ellipsises |
| Tab insets | 0 vertical, 8 horizontal |
| Gap icon / label / close | 8 |
| Leading icon | 16×16 `icons/title/terminal-2.svg`, tinted `Jasper.tabBarIcon` |
| Font | `SystemFonts.system(Font.PLAIN, 13)` (TermLab bundles Inter; Jasper does not add a font) |
| Close glyph | `icons/title/x.svg` at 12, always visible, `Jasper.tabBarMuted`; hover fills `Jasper.tabBarHover` behind a 3-pixel radius |
| Inactive tab | transparent over the strip background, 1-pixel bottom rule `Jasper.tabBarBorder` |
| Hover | `Jasper.tabBarHover` fill |
| Active tab | `Jasper.tabBarActive` fill and a 2-pixel `Jasper.tabBarUnderline` bottom rule replacing the 1-pixel rule |
| Trailing space | the 1-pixel bottom rule continues to the strip's right edge |

UIManager keys added to the three theme property files:

| Key | Dark | Light |
|---|---|---|
| strip background (no new key) | `Jasper.titleBackground`, `#23262c` | `Jasper.titleBackground`, `#eaeaeb` |
| `Jasper.tabBarForeground` | `#abb2bf` | `#1F2933` |
| `Jasper.tabBarMuted` | `#5c6370` | `#8A94A3` |
| `Jasper.tabBarIcon` | `#9AA7B0` (TermLab's glyph fill) | `#6B7680` (chosen for the light strip; TermLab uses one fill) |
| `Jasper.tabBarBorder` | `#333841` | `#C5CDD6` |
| `Jasper.tabBarHover` | `#3d424b` | `#D6DDE6` |
| `Jasper.tabBarActive` | `#3d424b` | `#D6DDE6` |
| `Jasper.tabBarUnderline` | `#6B80A1` | `#6B80A1` |

`Jasper.tabSelectedBackground` and `Jasper.tabSelectedForeground` are removed with their last
users. The inactive window state paints labels with `Jasper.titleInactiveForeground` as today.

### 4.3 Behaviour

- **Select**: left click anywhere on the entry except the close glyph.
- **Close**: the cross, or middle click on the entry. Closing the active tab selects the previous
  tab, or the next when the first tab closed (Swing's `JTabbedPane` default, decision 5). Closing
  the last tab closes the window, as today.
- **Open**: new tabs append at the end and are selected, as today.
- **Reorder**: press-and-drag beyond 5 scaled pixels, dropped on the entry under the pointer, as
  today (`owner.reorderTab`).
- **Overflow**: when the entries' total width exceeds the strip, the strip scrolls horizontally.
  Selecting a tab scrolls it into view; the mouse wheel over the strip scrolls it; there are no arrow
  buttons and nothing is clipped permanently. No scroll indicator is drawn; the partially visible
  edge tab is the cue.
- **Context menu** on right click: **Rename Tab** and **Close Tab**, plus plugin
  `MenuTarget.Type.CONTEXT` sections are *not* included (the pane menu owns those).
- **Rename**: `ActionId.RENAME_TAB` (F2) and the menu item swap the label for an inline
  `JTextField` inside the entry, pre-filled and selected; Enter commits through
  `TerminalTab.rename`, Escape reverts, focus loss commits. `WorkspaceActions`' `JOptionPane` rename
  is removed. An empty commit clears the rename (`TabState.rename(null)`).
- **Keyboard**: unchanged `ActionId` bindings (new, close, next, previous with wrap, 1–9, rename).
- **Accessibility**: each entry's accessible name is the full title; the close glyph's is
  "Close <title>"; the tooltip is the full title when it was ellipsised.
- **Entering animation**: none. TermLab's fade-and-drop is a CSS transition; Jasper adds no timer.

### 4.4 Configuration

`window.tab_height` keeps its key and live semantics; its default becomes 28 and its range 24–48.
`ConfigLoader` reports a value outside the new range the way it reports one outside the old range
today. The View menu spinner keeps working with the new bounds. `docs/configuration.md` and
`config.example.toml` change accordingly.

## 5. Titles

### 5.1 Tab title

`TerminalTitle.tab` is replaced by `TabTitle.compose(TabTitle.Source source)` in
`dev.jasper.app.workspace`, a port of TermLab's `pane-title.js`:

```
head = rename                                  if the user renamed the tab
     | osc title                               if the shell set one (OSC 0/2)
     | foreground job                          if a job other than the shell is running
     | cwd with the home directory as "~"      for a local directory
     | remote label                            for a remote directory (RemoteLocation.label)
     | "Terminal"
prefix = user + "@" + host + ": "               for a local session; empty for remote or unknown
title  = prefix + head, unless head already starts with prefix's "user@host"
title += " — " + cols + "×" + rows              when both are positive
```

`user` is `System.getProperty("user.name")`; `host` is the first entry of
`LocalHostNames.cached()` with any domain suffix removed, or empty, in which case there is no
prefix. A rename replaces `head` only; the prefix and size still apply, matching TermLab where a
renamed tab keeps its size suffix. The size comes from the focused pane's session. The rules and
the duplicate-prefix suppression are pure functions over a `Source` record
`(rename, oscTitle, job, directory, remoteLabel, user, host, columns, rows, home)` so
`TabTitleTest` covers every branch without a session.

`TerminalPane.title()` builds the `Source` from its existing fields (`reportedTitle`,
`foregroundJob`, `shellLabel`, `directory()`, `remoteOf(session)`, `session.columns()/rows()`).
`TabState.title` collapses to "rename if present, else the composed title"; the directory-basename
fallback moves into the composer.

### 5.2 Window title

`TerminalTitle.windowTitle` returns the active tab's composed title, or `Jasper` when the window has
no tab. The `— Jasper` suffix is not reintroduced. On macOS 26 the system draws the title
left-aligned next to the controls; that is the platform's choice.

### 5.3 What does not change

The Buddy reads `TerminalPane.programTitle()` (the raw reported title or the running command) and is
untouched. `PaneSnapshot.title` and `PaneInfo.title` keep carrying the pane's reported title, so
`pane.info().title()` in the SDK contract is unchanged. `TabHandle.title()` returns the composed
tab title as it returns the composed title today; only the composition changes. The 500 ms
foreground-job poller, OSC 7 directory source and remote provenance rules stay.

## 6. Packaging: the macOS 26 window controls

`gradle/packaging.gradle` gains a `refreshLauncherSdk` step inside `packageApp`'s `doLast`, run on
macOS after `jpackage --type app-image` succeeds:

1. Resolve `xcrun --show-sdk-version`. If `xcrun` is missing or fails, log
   `Launcher SDK version not updated: Xcode command line tools not found` at lifecycle level and
   return; the image is still valid with the legacy controls.
2. Read the launcher's current `minos` with `xcrun vtool -show-build <launcher>`; keep it.
3. Run `xcrun vtool -set-build-version macos <minos> <sdk> -replace -output <tmp> <launcher>` and
   move `<tmp>` over the launcher.
4. Re-sign with `/usr/bin/codesign --force --sign - <launcher>` (ad hoc, the same identity jpackage
   used), then `codesign --force --deep --sign - Jasper.app` so the bundle seal matches. If the build
   later gains a real signing identity, that step signs after this one; this step must stay before it.

`verifyPackage` asserts, on macOS with `xcrun` available, that the launcher's recorded SDK is at
least 26.0, and otherwise logs that the check was skipped. The existing `codesign --verify --deep
--strict` remains and must pass after the rewrite (the spike confirmed it does on a re-signed
binary). `Info.plist` is untouched: `LSMinimumSystemVersion` stays as jpackage writes it and no
`UIDesignRequiresCompatibility` key is added.

`./gradlew :jasper-app:run` and `installDist` launch the JetBrains Runtime's own `java` binary and
keep the legacy controls; `docs/app-maintenance.md` says so and points at the packaged app for
appearance checks.

## 7. Plugin SDK

No SDK type, capability, event or testkit class changes. `WindowHandle`, `TabHandle` and
`PaneHandle` hold ids only; `WindowOwner` exposes no frame; the `TerminalBridge` topics
`TAB_OPENED/CLOSED/SELECTED` and `TITLE_CHANGED` fire from the same `WindowContent` paths. The
bundled History and Snippets plugins use no tab or window surface; the sample plugin's toolbar,
status and window contributions render as before. `verifySdkArchitecture` and
`verifyPluginArchitecture` are unaffected. `docs/sdk-architecture.md`'s sentence that plugin
windows "follow the `TerminalWindow`/`WindowContent` split" stays true.

## 8. Tests

- `WindowTabsTest` is rewritten: strip hidden for one tab; entries at content width capped at
  220; icon, label and always-visible cross present with the right names; active fill and
  underline painted (probe the entry's `paintComponent` into an image); hover fill; select, close,
  middle-click, reorder and overflow scrolling through real `MouseEvent`s; inline rename commit,
  cancel and empty-commit clearing; context-menu items; theme change re-reads the keys; disposed
  owner ignores clicks.
- `TabMotionTest`, `MacTitleBarTest` and `TitleBarPreview` are deleted. `MockUiPreview` and
  `CommandPalettePreview` lose their title-bar fixtures.
- `TabTitleTest` (new) covers every branch of section 5.1, including the duplicate-prefix rule,
  the remote label, a missing host and the size suffix; `TerminalTitleTest` and `TabStateTest`
  are updated to the new rules; `TerminalTitleIntegrationTest` asserts the composed title of a
  real pane.
- `WindowsTitleBarTest` (new) covers `plan(...)`: dark on Windows 10 sets only attribute 20;
  Windows 11 adds 35 and 36 with the `COLORREF` encoding of the given colours; light resets 20.
- `TabHeightTest` and `ConfigLoaderTest` move to the 24–48 range and the 28 default.
- A `tabBarPreview` Gradle task renders the strip at 2× in both themes with one, three and
  overflowing tabs into `jasper-app/build/reports/tab-bar/`, replacing `titleBarPreview`.
- Packaging: no unit test; `verifyPackage` carries the SDK assertion and the user's native
  acceptance covers the controls, the dark and light title bar on macOS and Windows, and the
  Linux desktop-theme note.

## 9. Documentation

- `docs/app-architecture.md`: the platform package now holds appearance adapters, not title-bar
  paint; the window layout is the same on every platform; the tab strip section.
- `docs/app-maintenance.md`: recipes for the strip's keys, the title composer, the Windows
  attributes and the launcher SDK step.
- `docs/configuration.md` and `config.example.toml`: `tab_height` default and range; the Linux
  title-bar note under `ui.theme.variant`.
- `docs/design/iterm-title-bar-reference.md`: a supersession banner pointing here; the title-source
  paragraphs stay authoritative where section 5 does not change them.
- `docs/STATUS.md`: the spike result, the decisions above and the acceptance steps for the user.
- `packaging/` README if one describes the macOS image: the `vtool` step and its Xcode
  requirement.

## 10. Plan shape

One implementation plan, executed with `superpowers:subagent-driven-development`, in this order so
each task leaves the app runnable: (1) `TabTitle` composer and title tests; (2) the tab strip
rewrite, keys and previews; (3) native frames: remove `MacTitleBar`, add the macOS and Windows
appearance adapters and wire theme changes; (4) configuration range and docs; (5) packaging step
and verification. Native acceptance is the user's.
