# Moray — Phase 1: The Terminal — Design Spec

**Date:** 2026-09-10
**Status:** Approved in brainstorming, awaiting written-spec review
**Repo:** `~/projects/moray`

## 1. What Moray is

Moray is a cross-platform (macOS, Linux, Windows) desktop terminal workstation written in Java Swing, laid out like MobaXterm: menu bar, clickable toolbar, tabs, and — from phase 2 — a left sidebar. It replaces `~/projects/conch` (Rust + Tauri + xterm.js), which remains the user's daily driver until Moray passes the switch-over test in §9.

It is built in phases. Each phase gets its own spec, and each lands on an app the user already uses daily:

1. **The terminal** — this spec. A local terminal good enough to replace iTerm2 / Alacritty / conch.
2. SSH session management (left sidebar, saved hosts).
3. Vault (credentials, keys).
4. SFTP (panel attached to an SSH session).
5. Tunnels.

Plugins (e.g. Minecraft RCON) and a file editor come after phase 5. Tested non-UI Java from `~/projects/termlab-bundle` (MINA SSH client, vault crypto and lock states, host store) is to be reused in phases 2–5 rather than rewritten.

### Why a rewrite

The user's reasons, in order of weight: conch's codebase has become tangled; the web UI does not feel like a desktop app; Java is preferred (plugin ecosystem, reuse of TermLab code); and conch's terminal feel is somewhat lacking.

### Guardrail against the redesign loop

Previous attempts (conch, TermLab, termlab-bundle) stalled by re-platforming or re-architecting before features worked. Therefore:

- No redesign of phase-1 structure until phase 1 passes §9. New ideas go on the later list (§10).
- No interface without two real implementations. No plugin API in phase 1.
- JediTerm is the emulator, full stop. No abstraction over "emulator backends".

## 2. Decisions

| Topic | Decision |
|---|---|
| Name | **Moray** (Remora rejected: collides with `wuuJiawei/Remora`, a macOS SSH + SFTP app) |
| Language / runtime | Java 25 on the JetBrains Runtime (JBR) 25 |
| Build | Gradle, Kotlin DSL |
| UI toolkit | Swing + FlatLaf |
| Emulator | `org.jetbrains.jediterm:jediterm-core:3.76` — published only to `https://packages.jetbrains.team/maven/p/ij/intellij-dependencies`, not Maven Central |
| PTY | `org.jetbrains.pty4j:pty4j:0.13.10` (Maven Central) |
| Terminal view | **Moray's own Swing component** ("option B"). `jediterm-ui` is not used. |
| Config | Own TOML file; see §7 |

### Why Moray owns the terminal view (engine spike, 2026-09-10)

A throwaway spike compared `jediterm-core` alone against `jediterm-core` + `jediterm-ui`, feeding 100 MB of ANSI-colored text through a real PTY on macOS arm64:

| Measurement | Throughput |
|---|---|
| Raw PTY drain, no emulator | 77.7 MB/s |
| `jediterm-core`, headless | 50.8 MB/s |
| `jediterm-core` + `jediterm-ui` widget | 35.0 MB/s |

Rendering checks: `jediterm-ui` does **not** render ligatures. JBR itself renders them correctly with the same font (a plain `drawString` control strip showed `-> => != ===` as ligatures), but `TerminalPanel` draws per character. Nerd Font glyphs, box drawing, CJK, emoji, SGR styles, 256-color and truecolor all rendered correctly. TermLab also needed reflection-based patches to `jediterm-ui` for right-click mouse reporting, context menus stealing clicks from tmux, cursor-shape drift and bracketed paste. Owning the view fixes ligatures and removes those patches.

`jediterm-pty` is not used — it stopped at 2.69. Moray's PTY connector is its own small class.

## 3. Architecture

Two Gradle modules with one-way dependencies, enforced by the build:

```
moray-app  ──►  moray-terminal  ──►  jediterm-core, pty4j
```

- **`moray-terminal`** — a self-contained terminal component. It takes `TerminalOptions` (font, fallback fonts, colors, scrollback, cursor, Option-as-Meta, dimming) and a command to run, and exposes a Swing component plus a small API (write, resize, search, copy, events for title / working directory / prompt marks / bell / exit). It knows nothing about config files, tabs, keybindings or the app. All JediTerm usage is confined to this module.
- **`moray-app`** — the window, toolbar, menus, tabs, split tree, status bar, keybindings, config loading and live reload, per-OS paths, themes, CLI arguments, logging.

`moray-terminal` never depends on `moray-app`.

## 4. `moray-terminal`

### 4.1 Session and threading

- `TerminalSession` spawns the command through pty4j with `TERM=xterm-256color` and `COLORTERM=truecolor`, starting in the given working directory.
- `PtyConnector` implements JediTerm's `TtyConnector` over `PtyProcess` (UTF-8 reader, write + flush, `resize(TermSize)` → `setWinSize`).
- One reader thread per session runs the JediTerm emulator loop (`JediEmulator.hasNext()` / `next()` over a `TtyBasedArrayDataStream`), updating a `JediTerminal` backed by a `TerminalTextBuffer`.
- Moray implements JediTerm's `TerminalDisplay` interface itself, receiving cursor, scroll, title, alternate-screen, mouse-mode and bracketed-paste callbacks.
- The Event Dispatch Thread (EDT) paints while holding the text buffer's lock, copying only the visible rows it needs, so the reader thread is blocked briefly.
- On process exit the pane shows "[process exited with code N]"; the pane closes on the next key press.

### 4.2 Rendering (`TerminalView`)

- Paints row by row. Each row's cells are grouped into **runs** of identical style (colors, bold, italic, underline, inverse, strikethrough) and identical font.
- Each run is drawn in one call with the ligature-enabled font (`TextAttribute.LIGATURES_ON`), so ligatures form within a run. Glyph positions are pinned to the cell grid, so a ligature spanning N cells occupies exactly N cells.
- **Font fallback:** the primary font plus an ordered fallback list (e.g. a Nerd Font symbols font, then an emoji font). The font is chosen per code point with `Font.canDisplay`, and runs split on font changes. Results are cached per code point.
- Wide characters (CJK, emoji) occupy two cells, following the text buffer's width information.
- Box-drawing and block characters fill the full cell height; `line_height` controls row spacing.
- **Repaint coalescing:** buffer changes mark rows dirty. A timer at the display's refresh rate repaints only dirty rows and only when something changed. Under a flood of output, frames are skipped rather than queued.
- Cursor shapes: block, beam, underline, optional blink. The configured shape is the default. Applications may change it with DECSCUSR (e.g. vim's beam cursor in insert mode), and it returns to the configured shape when they reset it (DECSCUSR 0) or on terminal reset. This deliberately differs from TermLab, which ignored DECSCUSR.
- Inactive panes are dimmed by `dim_inactive_panes`.

### 4.3 Input

- **Keys → bytes:** printable text; Enter, Tab, Backspace and Escape; arrows, Home/End and PageUp/Down in both normal and application-cursor modes; F1–F12; Ctrl combinations. **Option-as-Meta** (macOS) is configurable as left, right, both or none; as Meta, Option sends ESC plus the key.
- Shortcuts owned by the app (the keybinding table) are consumed before encoding; everything else goes to the shell.
- **Mouse reporting:** X10, normal, button-event and any-event modes; SGR and legacy encodings. **Right-click is reported** when the application enables mouse mode, and no context menu is shown then. Shift bypasses mouse reporting to allow local selection. Out-of-bounds (negative) coordinates are never sent.
- **Paste** is wrapped in bracketed-paste markers when the application enables that mode.
- **Scroll wheel:** scrolls scrollback normally; in the alternate screen it is sent as mouse-wheel events, or as arrow keys if mouse mode is off.

### 4.4 Selection, copy, scrollback

- Click-drag selects; double-click selects a word; triple-click selects a line; Alt-drag selects a rectangular block.
- Copy joins soft-wrapped lines, keeps wide characters intact and trims trailing spaces. `copy_on_select` is optional.
- The scrollback depth is configurable; the view scrolls through history with a thin, auto-hiding scrollbar.

### 4.5 Search

- Plain or regex, case-sensitive or not, over the screen and scrollback.
- All matches are highlighted, the current match in a stronger color; next / previous wrap around.
- The search API lives in `moray-terminal`; the find bar UI lives in `moray-app`.

### 4.6 Shell integration

- **OSC 7** (current working directory) → a working-directory event for the status bar; new tabs and splits start in that directory.
- **OSC 133** (prompt, command and output marks) → prompt positions; jump to the previous or next prompt.
- **OSC 8** hyperlinks, plus detected URLs → Cmd+click opens them in the default browser.
- **OSC 0/2** (title) → a title event for the tab name.
- **Implementation:** if `jediterm-core` does not surface OSC 7 / 133 / 8 to `TerminalDisplay`, the connector observes those sequences in the byte stream before passing it to the emulator unchanged — the approach TermLab's `OscTrackingTtyConnector` used. The implementation plan verifies which route applies.

## 5. `moray-app`: the window

From top to bottom:

1. **Menu bar** — the macOS screen menu bar (`apple.laf.useScreenMenuBar`); an in-window `JMenuBar` on Linux and Windows. Every action appears in a menu.
2. **Toolbar** — a `JToolBar` of MobaXterm-style buttons: SVG icon (via FlatLaf) with a short label underneath. Phase 1 buttons: **New tab**, **New window**, **Split** (with a drop-down for right / down), **Zoom pane**, **Find**, **Settings**, **Reload config**. Later phases add Sessions, SFTP and Tunnels. `window.toolbar` selects `icons_and_labels`, `icons` or `hidden`; the same options are in the View menu.
3. **Left sidebar** — not in phase 1. Phase 2 adds it (collapsible, MobaXterm-style), holding saved sessions; later phases add tabs to it.
4. **Tab strip** — tabs across the top of the content area.
   - The title comes from OSC 0/2 or else the working-directory name. F2 renames a tab, and a renamed tab keeps its name.
   - Tabs can be dragged to reorder; middle-click closes.
5. **Pane area** — each tab holds a split tree.
   - The tree is a pure model (split, close, navigate, zoom, resize ratios) rendered as nested `JSplitPane`s.
   - Split right or down; move focus with Cmd+Alt+arrows; drag dividers.
   - Zoom toggles the focused pane to fill the tab.
   - Closing the last pane closes the tab; closing the last tab closes the window.
6. **Find bar** — a slim bar at the top of the focused pane: text field, next/previous, case and regex toggles, match count, Escape to close.
7. **Status bar** — the focused pane's shell and working directory (from OSC 7), its size in columns × rows, and the **config status indicator**: green when the config is fine, yellow for warnings, red for errors with the line number. Clicking it shows the full messages.

Multiple windows are supported (Cmd+N). Window size and position are not persisted in phase 1.

### 5.1 Keybindings

Keybinding strings use `cmd` to mean the primary modifier: ⌘ on macOS, Ctrl+Shift on Linux and Windows (plain Ctrl belongs to the shell there). `ctrl`, `alt` and `shift` are literal. Setting an action to `"none"` removes its binding. Unknown action names produce a config warning.

| Action | Default |
|---|---|
| `new_tab` | `cmd+t` |
| `close_tab` | `cmd+w` |
| `new_window` | `cmd+n` |
| `split_right` | `cmd+d` |
| `split_down` | `cmd+shift+d` |
| `close_pane` | `cmd+shift+w` |
| `zoom_pane` | `cmd+shift+enter` |
| `focus_pane_left` / `_right` / `_up` / `_down` | `cmd+alt+left` / `right` / `up` / `down` |
| `next_tab` / `previous_tab` | `cmd+shift+]` / `cmd+shift+[` |
| `select_tab_1` … `select_tab_9` | `cmd+1` … `cmd+9` |
| `rename_tab` | `f2` |
| `find` | `cmd+f` |
| `find_next` / `find_previous` | `cmd+g` / `cmd+shift+g` |
| `previous_prompt` / `next_prompt` | `cmd+up` / `cmd+down` |
| `copy` / `paste` | `cmd+c` / `cmd+v` |
| `clear_scrollback` | `cmd+k` |
| `font_bigger` / `font_smaller` / `font_reset` | `cmd+=` / `cmd+-` / `cmd+0` |
| `open_settings` | `cmd+,` |
| `reload_config` | `cmd+shift+r` |
| `quit` | `cmd+q` |

## 6. Look

- FlatLaf with light and dark variants; `colors.appearance` selects `system`, `dark` or `light` for the app chrome.
- The terminal colors come from the selected theme (§7.3).
- Icons are bundled SVGs from a permissively licensed icon set (chosen in the plan, e.g. Tabler or Lucide).

## 7. Configuration

### 7.1 Location

| OS | Folder |
|---|---|
| macOS | `$HOME/.config/moray/` |
| Linux | `$XDG_CONFIG_HOME/moray/` if set, else `$HOME/.config/moray/` |
| Windows | `%APPDATA%\moray\` |

- One class (`AppDirs`) resolves these from the OS name and environment, passed in so every platform is testable anywhere. No other code builds paths.
- `--config <path>` overrides the config file only; logs and themes stay in the default folder.

```
moray/
├── config.toml
├── themes/*.toml
└── logs/moray.log      (rotated)
```

Moray never writes `config.toml` unasked. Without it, built-in defaults apply. The first **Settings** click creates a fully commented template listing every option with its default, marked "applies live" or "applies to new panes", then opens it in the OS default editor. If no editor opens it, the file is revealed in Finder or Explorer instead.

### 7.2 Format

```toml
[window]
columns = 150
lines = 45
toolbar = "icons_and_labels"   # "icons_and_labels" | "icons" | "hidden"
status_bar = true

[font]
family = "JetBrains Mono"
size = 14.0
ligatures = true
fallback = ["Symbols Nerd Font Mono", "Apple Color Emoji"]
line_height = 1.0

[colors]
theme = "moray-dark"
appearance = "system"          # "system" | "dark" | "light"

[terminal]
shell = { program = "", args = [] }   # "" = the user's login shell
env = {}
scrollback = 10000
option_as_meta = "left"        # "left" | "right" | "both" | "none"
cursor = { shape = "block", blink = true }   # "block" | "beam" | "underline"
dim_inactive_panes = 0.3       # 0.0 – 1.0
copy_on_select = false
bell = "visual"                # "visual" | "sound" | "none"

[keybindings]
# action = "keys"; see §5.1
```

JBR bundles JetBrains Mono, so the default font works without installation.

### 7.3 Themes

Theme files use Alacritty's color layout (`[colors.primary]`, `[colors.cursor]`, `[colors.selection]`, `[colors.normal]`, `[colors.bright]`), so existing Alacritty themes work unchanged. Built-ins: `moray-dark`, `moray-light`. `colors.theme` names a built-in or a file in `themes/`.

### 7.4 Loading and live reload

- The config file's modification time is checked once per second. (The JDK's `WatchService` polls slowly on macOS.) The **Reload config** action forces a check.
- A changed file is parsed and validated completely, then swapped in as one immutable snapshot and published to listeners.
- **Applies live:** font, colors and theme, keybindings, toolbar and status-bar visibility, dimming, cursor, bell, `copy_on_select`, `option_as_meta`, appearance.
- **Applies to new panes only:** shell, env, scrollback.

### 7.5 Errors

- **Syntax or type errors:** keep the current snapshot (the defaults, at startup); the status indicator turns red with the file, line and message. Moray always starts.
- **Unknown keys or action names:** a yellow warning with the key and line; everything else applies.
- **Invalid values** (e.g. `dim_inactive_panes = 4`): an error for that key, which falls back to its default, reported like a type error.
- Nothing is ever silently discarded. (conch discards the whole file on a duplicate key.)

Parsing uses Jackson's TOML data format binding into Java records, subject to the plan confirming that it reports line numbers for type errors and unknown keys. If it doesn't, the plan picks a TOML library that does.

## 8. Testing

Logic lives in plain classes so most tests need no window.

**`moray-terminal`**
- Emulation through an in-memory `TtyConnector`: escape sequences in, expected buffer contents out.
- Run building: row cells → runs (split on style or font), fallback-font selection, wide-character handling, ligature runs staying on the cell grid.
- Key encoding tables (normal and application modes, Option-as-Meta, function keys, Ctrl combinations).
- Mouse encoding: SGR and legacy formats, right-click, wheel, negative-coordinate suppression. Bracketed paste.
- Selection → copied text: soft wraps, wide characters, trailing spaces, block selection.
- Search: plain, regex, case, across scrollback.
- Shell integration parsing: OSC 7, 133, 8, 0/2.

**`moray-app`**
- Config: defaults, full example round-trip, syntax / type / unknown-key / invalid-value reporting with line numbers, last-good retention, reload.
- `AppDirs` for macOS, Linux (with and without `XDG_CONFIG_HOME`) and Windows.
- Keybinding parsing, including `cmd` mapping per OS and `"none"`.
- Split-tree model: split, close, navigate, zoom, resize.

**Benchmark** — a Gradle task that runs the spike's 100 MB ANSI `cat` through `TerminalSession` + `TerminalView` in a window and prints MB/s.

**CI** — GitHub Actions runs all tests on macOS, Linux and Windows. Windows is required because ConPTY is a separate code path in pty4j.

## 9. Definition of done

1. **Manual checklist on macOS passes:**
   - tmux with mouse (click, right-click, drag-resize panes); vim; htop
   - ligatures; Nerd Font icons; emoji; CJK
   - Option-as-Meta; resizing while output streams
   - split / close / zoom / navigate panes; search; previous / next prompt
   - Cmd+click links; working directory in the status bar
   - config live reload; a broken config shows red and keeps the previous settings
2. **Throughput** ≥ 35 MB/s on the benchmark (the `jediterm-ui` result); target ≥ 45 MB/s.
3. **CI green** on macOS, Linux and Windows. `./gradlew run` works on all three. A macOS `.app` bundling JBR is produced (via `jpackage`) so Moray launches from the Dock.
4. **Switch-over test:** Moray is the only terminal used for two weeks, with conch still installed. Each time conch gets opened, the reason is recorded; those become fixes before phase 2 starts.

## 10. Later (explicitly not phase 1)

- Command palette
- Dragging a tab out into its own window
- Session and window-layout restore
- Quake-style / global hotkey window
- Keybindings that send arbitrary text
- Linux and Windows installers
- Importing conch's `config.toml`
- Everything in phases 2–5; plugins; the editor

## 11. Risks and items the plan must verify

- **`jediterm-core` API surface:** the view relies on `TerminalTextBuffer` / line / style iteration and `TerminalDisplay` callbacks. The plan pins 3.76 and confines usage to `moray-terminal`.
- **OSC 7 / 133 / 8 visibility** in `jediterm-core` (§4.6): pick the route before building the status bar and prompt jumps.
- **Rendering speed of runs with ligatures** on large windows: measured by the benchmark; glyph-layout caching per run is the first lever.
- **Jackson TOML line numbers** (§7.5).
- **Windows ConPTY and Linux fonts:** covered by CI tests; manual verification on those platforms is a phase-1 stretch goal.

**Findings from plans 1–2 (2026-09-10):**
- **Strikethrough (§4.2) is not shown.** jediterm-core 3.76's `TextStyle` has no strikethrough option, so SGR 9 is dropped before Moray sees it. Supporting it would mean tracking SGR 9 in the shell-integration filter; deferred.
- **Font fallback on macOS.** The JetBrains Runtime reports `canDisplay` as true for CJK and emoji in any font, because the system font cascade draws them. The configured fallback list therefore takes effect only for glyphs the primary font truly lacks, in practice Nerd Font icons. Plan 4's config should make naming a Nerd Font fallback easy.
- **Search matches do not span soft-wrapped rows** (§4.5); matching is per physical row.
- **Shell integration (§4.6).** JediTerm ignores OSC 7 and OSC 133; Moray rewrites them, and DECSCUSR 0, into OSC 1341 custom commands so they arrive in emulator order.
- **Absolute rows are only stable while output streams.** Erasing the scrollback (ED 3, RIS) renumbers every line, and jediterm-core reflows soft-wrapped lines when the width changes, which moves lines to other rows. Either one therefore clears the selection, the search matches and the recorded prompt marks, and returns the view to the live screen.
