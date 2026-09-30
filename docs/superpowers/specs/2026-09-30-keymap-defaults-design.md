# Keymap defaults: every action, sane defaults, keys that follow focus

Status: approved design, 2026-09-30. Jasper's keyboard shortcuts are rewired to one consistent set of defaults across
the app and its bundled plugins (Editor, Remote, Vault, History, Snippets, Sample). Actions gain a context (Global,
Terminal, Editor, or one panel or window), so a key can mean different things in different places and a key whose
action doesn't apply where focus is reaches the shell instead of being swallowed. Hardcoded keys (the Editor's
commands, terminal page scrolling, panel and dialog keys) and palette-only View toggles become Keymap actions, and 45
missing actions are added. Built on the app Keymap (`dev.jasper.app.config.KeyBindings`, Settings › Keymap), the SDK
(`jasper-sdk` 0.10.1, `ActionSpec`), and the plugins on local `main` at ea35152d.

The reviewed proposal is [assets/2026-09-30-keymap-proposal.html](assets/2026-09-30-keymap-proposal.html); the table in
[Defaults](#defaults) below is the same data, with the same row numbers.

## Outcome

- Every action has a sane default or is deliberately left unbound (appearance toggles, rarely used commands), on
  macOS and on Linux/Windows separately.
- Settings › Keymap lists every action, including the Editor's commands, the View toggles and the panel operations,
  with a Where column (Global, Terminal, Editor, a panel's or window's name).
- On Linux/Windows no default takes plain Ctrl+letter, Alt+letter or F1–F10 from a focused terminal; the Editor and
  panels use plain Ctrl, like any editor or file manager.
- Today's collisions are gone: SSH Sessions vs the Editor's Save As (⇧⌘S), the Editor's ⌘N vs New Window, the Editor's
  Find Previous vs the terminal's Find Next on Linux, and plugin defaults that skip the Linux Alt rule.

## Decisions (user, 2026-09-30)

1. **Terminal first, IntelliJ for the rest.** Tabs, panes, splits, scrollback, copy/paste and fonts follow iTerm2 and
   Ghostty (GNOME Terminal and Windows Terminal on Linux/Windows). The Editor, panels, palette and window chrome follow
   IntelliJ, which leaves appearance toggles unbound.
2. **Context-aware keys on Linux/Windows.** Terminal and window actions use Ctrl+Shift, then Ctrl+Alt+Shift; the
   Editor and panels use plain Ctrl. An action that doesn't apply where focus is lets its key through.
3. **The proposal is approved as published** (126 rows).
4. **Everything is rebindable** except keys local to one component (palette verbs, find-bar Enter/Esc, list Enter),
   which are listed read-only.

## Alternatives considered

- **IntelliJ everywhere.** ⌘1–9 would open tool windows and tab selection would move; a terminal user expects ⌘1–9
  for tabs and ⌘K to clear.
- **Ctrl+Shift everywhere on Linux/Windows.** Simplest and fully shell-safe, but Save would be Ctrl+Shift+S in the
  Editor, which no editor does.
- **Keep the current mix and fix collisions only.** Leaves the Editor's keys invisible to the Keymap and keeps Ctrl+K,
  F2 and F8 taken from the shell.

## Principles

- **macOS:** every default uses ⌘, optionally with ⇧, ⌥ or ⌃. Bare ⌃ and ⌥ keys belong to the shell (Emacs keys, Option
  as Meta). Never used: ⌘H, ⌥⌘H, ⌘Space, ⌥⌘D, ⌃⌘D, ⌃⌘Q, ⇧⌘/ (Help search), ⇧⌘3/4/5.
- **Linux/Windows, Global and Terminal contexts:** Ctrl+Shift+key, then Ctrl+Alt+Shift+key; also Ctrl+Tab, Ctrl+1–9
  (no terminal encoding), Shift+PageUp/PageDown, F11 (full screen), Alt+Shift+arrows (resize pane) and Alt+1–3
  (panel toggles; readline's rarely used digit argument is given up, noted in the table).
- **Linux/Windows, Editor and panel contexts:** plain Ctrl (Ctrl+S, Ctrl+F), F3/Shift+F3, F5, Delete.
- **Focused context wins.** The same stroke may belong to several actions in different contexts; the most specific
  applicable context wins (panel, window or content › Terminal › Global).

## Context model

### Contexts

| Context | Applies when focus is… | Declared by |
| --- | --- | --- |
| Global | anywhere in a terminal window | default for every action |
| Terminal | in a terminal pane | built-in terminal actions |
| Content (per plugin) | in a content tab the plugin opened (the Editor's tabs) | plugin, `ActionContext.content()` |
| Panel (per panel id) | inside that panel (SSH Sessions, SFTP) | plugin, `ActionContext.panel(panelId)` |
| Window (per window id) | inside that plugin window (Vault manager) | plugin, `ActionContext.window(windowId)` |
| File chooser, find bar | inside Jasper's file chooser or a terminal find bar | built-in |

Specificity: Panel, Window, Content, File chooser, Find bar (equal, they never nest) › Terminal › Global.

### Resolution and dispatch

`WindowContent.dispatchShortcut` (and the root-pane input map it backs) stops mapping a stroke to one action id.
Instead:

1. Work out the focus chain from the focus owner: which panel, plugin window, content tab, find bar or terminal pane
   contains it, plus Global.
2. Collect the actions bound to the stroke whose context is in that chain; take those in the most specific context.
3. If that action is enabled, run it and consume the key. If nothing applies, or the action is disabled, **do not
   consume**: the key goes to the focused component (the shell, for a terminal pane).

This replaces today's rule that a recognised but disabled action swallows its key, and today's hosted-content-command
precedence (`ContentCommand` strokes win while a content tab is selected): content-tab keys become content-context
actions. `ContentCommand.shortcut` stays in the SDK for compatibility; the host shows the Keymap binding of a content
action with the same id when there is one.

Plugin windows and Jasper's file chooser get the Keymap too (today only terminal windows do), limited to actions of
their own context; the palette dispatcher, overlay blocking and the Keymap recorder keep their current precedence.

### Conflicts

- Two defaults in the **same** context, or in overlapping contexts at the same specificity (Global and Terminal
  overlap in a terminal pane), are a programming error for built-ins and a dropped plugin default (`DEFAULT_DROPPED`) as
  today.
- A more specific context may reuse a less specific stroke; the Keymap page shows it as "overrides ⟨action⟩ here".
- User bindings keep today's rules: a user binding wins over defaults; the Keymap's Set refuses a same-context clash
  unless "Reassign" is ticked.

## Defaults per platform

- **Built-ins** carry two explicit default strings, macOS and other (`ActionId`), replacing today's `cmd` parsing
  tricks for defaults: `cmd` in a macOS default is ⌘, and the other default names `ctrl`, `shift` and `alt`
  literally. `cmd` stays accepted in user bindings and keeps today's meaning (Ctrl+Shift outside macOS).
- **Plugins** keep `ActionSpec.withDefaultBinding(String)`. Outside macOS it now gets the same Alt rule built-ins had
  (`cmd+shift+x` → Ctrl+Alt+Shift+X), fixing the silent collision. New `withDefaultBindings(String macOs, String other)`
  gives an explicit pair; bundled plugins use it wherever the table names plain Ctrl or a function key.
- `[keybindings]` in `config.toml` is unchanged: `action_id = "binding"`, one binding per action.

## SDK 0.11.0

- `dev.jasper.sdk.ui.ActionContext`: `global()`, `terminal()`, `content()`, `panel(String panelId)`,
  `window(String windowId)`; `panel` and `window` ids must belong to the plugin.
- `ActionSpec.withContext(ActionContext)` (default Global) and `ActionSpec.withDefaultBindings(String macOs, String other)`.
- Actions in a Content, Panel or Window context are not shown in the palette's Commands scope unless that context is
  focused.
- Testkit: `FakePluginHost` records each action's context and defaults, can invoke an action "from" a context, and
  reports `DEFAULT_DROPPED` like the app; the contract suite covers context validation and dropped defaults.
- All bundled plugins declare `sdk = ">=0.11.0, <0.12"`.

## New and changed behaviour behind the defaults

Actions in the table with status **New action** are built as part of this work. The ones with real behaviour:

- **Windows:** Close Window (closes every tab after the usual guards), Toggle Full Screen (native macOS full screen;
  maximized borderless on Linux/Windows), Minimize, Next/Previous Window (terminal windows in creation order), Float
  Window (always on top), About Jasper (version, licences link). A Window menu holds Minimize, Next/Previous Window and
  Float Window.
- **Tabs:** Select Last Tab replaces Select Tab 9; Move Tab Left/Right; Close Other Tabs (tab context menu too).
- **Panes:** ⌘W closes the focused pane and the tab with its last pane (Close Pane's new default; Close Tab moves to
  ⌥⌘W); Next/Previous Pane in reading order; Resize Pane moves the nearest divider by one cell; Equalize Panes.
- **Terminal:** Select All (whole buffer), Reset Terminal (soft reset: modes, charsets, scroll region, then a redraw;
  scrollback kept), Scroll Line Up/Down, Scroll to Top/Bottom; Shift+PageUp/PageDown become Scroll Page Up/Down actions.
- **View:** Rail, Toolbar (hidden ↔ last visible mode, plus the three mode actions), Status Bar, Show Jasper, Terminal
  Colors and Window Settings become actions (their palette entries and View menu items use them); Hide All Panels
  (restores the same panels on the second press); Toggle Left/Right/Bottom Panel; Zen Mode (toolbar, status bar, rail
  and panels together, restored on exit).
- **Find bars:** Match Case, Whole Words and Regex toggles.
- **File chooser:** its keys become File chooser actions (Finder keys on macOS, GTK keys on Linux), plus Search, New
  Folder (Save mode) and Preview Pane.
- **Editor:** every command becomes a content-context action; Replace moves to ⌘R; New Editor File moves to ⇧⌘N and is
  Global; Toggle Vim Mode gets ⌥⌘V. In Vim mode on Linux, Vim keeps the Ctrl keys it uses (as today) unless a vimrc
  `:sethandler` gives them to the IDE side.
- **Remote:** Sessions panel operations (Connect in Split, New, Edit, Duplicate, Delete, Search, Favorite) and SFTP
  operations (Enclosing Folder, Go to Path, Refresh, New Folder, Delete, Copy Paths, Select All, Download, Upload, Copy to
  Host) become panel actions; their popup items and buttons use them.
- **Vault:** Change Master Password becomes an action; the manager's New Login, Delete, Search, Copy Username and Copy
  Password become window actions.

## Defaults

The approved table. **Where** is the context; a dash means no default.

| # | Area | Action | Id | Where | macOS | Linux and Windows | Status | Why |
| ---: | --- | --- | --- | --- | --- | --- | --- | --- |
| 1 | Application and windows | New Window | `new_window` | Global | `⌘N` | `Ctrl+Shift+N` | Keep |  |
| 2 | Application and windows | Close Window | `close_window` | Global | `⇧⌘W` | `Ctrl+Shift+Q` | New action | iTerm2 and Ghostty use ⇧⌘W; GNOME Terminal uses Ctrl+Shift+Q. |
| 3 | Application and windows | Quit | `quit` | Global | `⌘Q` | `Ctrl+Alt+Shift+Q` | Change | Linux/Windows only: Ctrl+Shift+Q goes to Close Window, as in GNOME Terminal. |
| 4 | Application and windows | Settings | `open_settings` | Global | `⌘,` | `Ctrl+Shift+,` | Keep |  |
| 5 | Application and windows | Reload Config | `reload_config` | Global | — | — | Change | Remove the default: config.toml already reloads when saved. Frees ⇧⌘R. |
| 6 | Application and windows | Toggle Full Screen | `toggle_full_screen` | Global | `⌃⌘F` | `F11` | New action | The macOS standard (IntelliJ too); F11 on Windows Terminal and GNOME. |
| 7 | Application and windows | Minimize | `minimize_window` | Global | `⌘M` | — | New action | macOS standard; a Window menu gets Minimize, Next Window and Float Window. The window manager owns this on Linux. |
| 8 | Application and windows | Next Window | `next_window` | Global | `⌘`` | — | New action | macOS standard. |
| 9 | Application and windows | Previous Window | `previous_window` | Global | `⇧⌘`` | — | New action | macOS standard. |
| 10 | Application and windows | Float Window (always on top) | `toggle_float_window` | Global | — | — | New action | Window menu item; bindable. |
| 11 | Application and windows | About Jasper | `about` | Global | — | — | New action | Help menu (and the macOS app menu). |
| 12 | Application and windows | Manage Plugins… | `plugins.manage` | Global | — | — | Keep | IntelliJ leaves Plugins unbound. |
| 13 | Application and windows | Keyboard Shortcuts… | `app.shortcuts` | Global | — | — | Keep |  |
| 14 | Command palette and quick open | Command Palette | `command_palette` | Global | `⇧⌘P` | `Ctrl+Shift+P` | Change | ⌘K becomes Clear Scrollback, as in every macOS terminal. Linux Ctrl+K is readline's kill-line today. ⇧⌘P matches Ghostty, VS Code and Windows Terminal. |
| 15 | Command palette and quick open | Search Shell History | `dev.jasper.history.open` | Global | `⌘R` | `Ctrl+Shift+R` | Keep |  |
| 16 | Command palette and quick open | Snippets… | `dev.jasper.snippets.open` | Global | `⌘J` | `Ctrl+Shift+J` | Keep |  |
| 17 | Command palette and quick open | Connect to SSH Host… | `dev.jasper.remote.connect` | Global | `⇧⌘H` | `Ctrl+Shift+H` | Keep |  |
| 18 | Command palette and quick open | Recent Files… | `dev.jasper.editor.recent-files` | Global | `⌘E` | `Ctrl+Shift+E` | Add default | IntelliJ's Recent Files. |
| 19 | Command palette and quick open | Open Vault… | `dev.jasper.vault.open` | Global | `⌘\` | `Ctrl+Shift+\` | Change | F8 belongs to the shell on Linux (Midnight Commander's Delete). ⌘\ is the password-manager key from 1Password. |
| 20 | Command palette and quick open | Open in Editor… | `dev.jasper.editor.open` | Global | `⌘O` | `Ctrl+Shift+O` | Keep |  |
| 21 | Command palette and quick open | Show Editor | `dev.jasper.editor.show` | Global | — | — | Keep |  |
| 22 | Tabs | New Tab | `new_tab` | Global | `⌘T` | `Ctrl+Shift+T` | Keep |  |
| 23 | Tabs | Close Tab | `close_tab` | Global | `⌥⌘W` | `Ctrl+Alt+Shift+W` | Change | ⌘W now closes the focused pane, and the tab with its last pane (iTerm2, Ghostty). |
| 24 | Tabs | Next Tab | `next_tab` | Global | `⇧⌘]` | `Ctrl+Tab` | Change | Linux/Windows only: Windows Terminal, browsers. |
| 25 | Tabs | Previous Tab | `previous_tab` | Global | `⇧⌘[` | `Ctrl+Shift+Tab` | Change | Linux/Windows only. |
| 26 | Tabs | Select Tab 1–8 | `select_tab_1…8` | Global | `⌘1 – ⌘8` | `Ctrl+1 – Ctrl+8` | Keep | Ctrl+digit has no terminal encoding, so the shell loses nothing. |
| 27 | Tabs | Select Last Tab | `select_tab_9 → select_last_tab` | Global | `⌘9` | `Ctrl+9` | Change | ⌘9 jumps to the last tab in iTerm2, Ghostty and every browser. |
| 28 | Tabs | Rename Tab | `rename_tab` | Global | `⌘I` | `Ctrl+Shift+I` | Change | F2 is Midnight Commander's and htop's on Linux. iTerm2 edits the session name with ⌘I. |
| 29 | Tabs | Move Tab Left | `move_tab_left` | Global | `⌥⇧⌘[` | `Ctrl+Shift+PageUp` | New action | Ctrl+Shift+PageUp/Down moves tabs in GNOME Terminal and Firefox. |
| 30 | Tabs | Move Tab Right | `move_tab_right` | Global | `⌥⇧⌘]` | `Ctrl+Shift+PageDown` | New action |  |
| 31 | Tabs | Close Other Tabs | `close_other_tabs` | Global | — | — | New action | Tab context menu; IntelliJ leaves it unbound. |
| 32 | Panes and splits | Split Right | `split_right` | Global | `⌘D` | `Ctrl+Shift+D` | Keep |  |
| 33 | Panes and splits | Split Down | `split_down` | Global | `⇧⌘D` | `Ctrl+Alt+Shift+D` | Keep |  |
| 34 | Panes and splits | Close Pane | `close_pane` | Global | `⌘W` | `Ctrl+Shift+W` | Change | iTerm2 and Ghostty: ⌘W closes the focused pane; the last pane closes its tab. In an Editor tab it closes that tab. |
| 35 | Panes and splits | Zoom Pane | `zoom_pane` | Global | `⇧⌘↩` | `Ctrl+Alt+Shift+Enter` | Keep |  |
| 36 | Panes and splits | Focus Pane Left/Right/Up/Down | `focus_pane_*` | Global | `⌥⌘←→↑↓` | `Ctrl+Alt+Shift+←→↑↓` | Keep |  |
| 37 | Panes and splits | Next Pane | `next_pane` | Global | `⌘]` | `Ctrl+Shift+]` | New action | iTerm2 and Ghostty. Free on Linux once tabs move to Ctrl+Tab. |
| 38 | Panes and splits | Previous Pane | `previous_pane` | Global | `⌘[` | `Ctrl+Shift+[` | New action |  |
| 39 | Panes and splits | Resize Pane Left/Right/Up/Down | `resize_pane_*` | Global | `⌃⌘←→↑↓` | `Alt+Shift+←→↑↓` | New action | iTerm2 and Ghostty on macOS; Windows Terminal on Linux/Windows. |
| 40 | Panes and splits | Equalize Panes | `equalize_panes` | Global | `⌃⌘=` | `Ctrl+Alt+Shift+=` | New action | Ghostty's equalize_splits. |
| 41 | Panes and splits | Split with Same Host | `dev.jasper.remote.split` | Terminal | — | — | Keep |  |
| 42 | Terminal | Copy | `copy` | Terminal | `⌘C` | `Ctrl+Shift+C` | Keep |  |
| 43 | Terminal | Paste | `paste` | Terminal | `⌘V` | `Ctrl+Shift+V` | Keep |  |
| 44 | Terminal | Select All | `select_all` | Terminal | `⌘A` | `Ctrl+Shift+A` | New action | There is no terminal Select All today; ⌘A does nothing. |
| 45 | Terminal | Clear Scrollback | `clear_scrollback` | Terminal | `⌘K` | `Ctrl+Shift+K` | Change | macOS only: ⌘K clears in Terminal.app, iTerm2 and Ghostty. |
| 46 | Terminal | Reset Terminal | `reset_terminal` | Terminal | `⌥⌘R` | `Ctrl+Alt+Shift+R` | New action | Terminal.app's Soft Reset. Recovers from a program that left the screen garbled. |
| 47 | Terminal | Find | `find` | Terminal | `⌘F` | `Ctrl+Shift+F` | Keep | The same action opens the Editor's find bar in an Editor tab (see Editor). |
| 48 | Terminal | Find Next | `find_next` | Terminal | `⌘G` | `Ctrl+Shift+G` | Keep |  |
| 49 | Terminal | Find Previous | `find_previous` | Terminal | `⇧⌘G` | `Ctrl+Alt+Shift+G` | Keep |  |
| 50 | Terminal | Previous Prompt | `previous_prompt` | Terminal | `⌘↑` | `Ctrl+Shift+↑` | Keep |  |
| 51 | Terminal | Next Prompt | `next_prompt` | Terminal | `⌘↓` | `Ctrl+Shift+↓` | Keep |  |
| 52 | Terminal | Scroll Page Up / Down | `scroll_page_up/down` | Terminal | `⇧PageUp` / `⇧PageDown` | `Shift+PageUp` / `Shift+PageDown` | Make rebindable | Hardcoded and undocumented today. |
| 53 | Terminal | Scroll Line Up / Down | `scroll_line_up/down` | Terminal | `⇧⌘↑` / `⇧⌘↓` | `Ctrl+Alt+Shift+↑` / `↓` | New action |  |
| 54 | Terminal | Scroll to Top / Bottom | `scroll_top/bottom` | Terminal | `⌘Home` / `⌘End` | `Ctrl+Shift+Home` / `End` | New action | iTerm2 and GNOME Terminal. |
| 55 | Terminal | Increase / Decrease / Reset Font Size | `font_bigger/smaller/reset` | Global | `⌘=` / `⌘-` / `⌘0` | `Ctrl+Shift+=` / `-` / `0` | Keep |  |
| 56 | Terminal | Open SFTP Here | `dev.jasper.remote.sftp` | Terminal | `⇧⌘O` | `Ctrl+Alt+Shift+O` | Add default | Browse the focused SSH pane's folder. |
| 57 | View and window chrome | Rail | `view.rail → toggle_rail` | Global | — | — | Bindable, no default | Palette and View menu only today. |
| 58 | View and window chrome | Toolbar (show/hide) | `toggle_toolbar` | Global | — | — | Bindable, no default | Plus the three mode items (Icons and Labels, Icons Only, Hidden). |
| 59 | View and window chrome | Status Bar | `view.status_bar → toggle_status_bar` | Global | — | — | Bindable, no default |  |
| 60 | View and window chrome | Show Jasper (Buddy) | `view.buddy → toggle_buddy` | Global | — | — | Bindable, no default |  |
| 61 | View and window chrome | Terminal Colors: Match UI / Light / Dark | `view.terminal_colors.*` | Global | — | — | Bindable, no default |  |
| 62 | View and window chrome | Window Settings… | `view.window_settings` | Global | — | — | Bindable, no default |  |
| 63 | View and window chrome | Hide All Panels | `hide_all_panels` | Global | `⇧⌘F12` | `Ctrl+Shift+F12` | New action | IntelliJ's Hide All Tool Windows; pressing again restores them. |
| 64 | View and window chrome | Toggle Left / Right / Bottom Panel | `toggle_panel_left/right/bottom` | Global | — | — | New action | One per panel region. |
| 65 | View and window chrome | Zen Mode | `toggle_zen_mode` | Global | — | — | New action | Hides the toolbar, status bar, rail and panels at once; IntelliJ's Zen Mode. |
| 66 | Panels | SSH Sessions | `dev.jasper.remote.hosts` | Global | `⌥⌘1` | `Alt+1` | Change | Frees ⇧⌘S for the Editor's Save As. IntelliJ numbers tool windows (⌘1, Alt+1 on Linux); ⌘1–9 are tabs here, so macOS adds ⌥. Linux gives up Alt+1 (readline's rarely used digit argument). |
| 67 | Panels | SFTP | `dev.jasper.remote.sftp.toggle` | Global | `⌥⌘2` | `Alt+2` | Add default |  |
| 68 | Panels | Transfers | `dev.jasper.remote.transfers` | Global | `⌥⌘3` | `Alt+3` | Add default |  |
| 69 | Panels | Other plugin panels | `<panel>.toggle` | Global | — | — | Keep | Bindable, no default. |
| 70 | Editor | Save | `dev.jasper.editor.save` | Editor | `⌘S` | `Ctrl+S` | Make rebindable |  |
| 71 | Editor | Save As… | `dev.jasper.editor.save-as` | Editor | `⇧⌘S` | `Ctrl+Shift+S` | Make rebindable | No longer collides with SSH Sessions. |
| 72 | Editor | Save All | `dev.jasper.editor.save-all` | Editor | `⌥⌘S` | `Ctrl+Alt+S` | Make rebindable |  |
| 73 | Editor | New Editor File | `dev.jasper.editor.new` | Global | `⇧⌘N` | `Ctrl+Alt+Shift+N` | Change | ⌘N stops shadowing New Window. IntelliJ's New Scratch File is ⇧⌘N. |
| 74 | Editor | Close Editor Tab | `close_pane` | Editor | `⌘W` | `Ctrl+Shift+W` | Keep | Same key as Close Pane. |
| 75 | Editor | Find… | `find (routed)` | Editor | `⌘F` | `Ctrl+F` | Make rebindable |  |
| 76 | Editor | Replace… | `dev.jasper.editor.replace` | Editor | `⌘R` | `Ctrl+R` | Change | IntelliJ's Replace (was ⌥⌘F). In a terminal ⌘R stays Shell History. |
| 77 | Editor | Find Next | `find_next (routed)` | Editor | `⌘G` | `F3` | Change | Linux/Windows only: IntelliJ's F3. Fixes today's clash with the terminal's Find Next. |
| 78 | Editor | Find Previous | `find_previous (routed)` | Editor | `⇧⌘G` | `Shift+F3` | Change | Linux/Windows only. |
| 79 | Editor | Go to Line… | `dev.jasper.editor.go-to-line` | Editor | `⌘L` | `Ctrl+G` | Change | Linux/Windows only: IntelliJ's Ctrl+G. |
| 80 | Editor | Toggle Comment | `dev.jasper.editor.toggle-comment` | Editor | `⌘/` | `Ctrl+/` | Make rebindable |  |
| 81 | Editor | Toggle Vim Mode | `dev.jasper.editor.toggle-vim` | Editor | `⌥⌘V` | `Ctrl+Alt+V` | Add default | IdeaVim's toggle. |
| 82 | Editor | Toggle Soft Wrap | `dev.jasper.editor.wrap` | Editor | — | — | Bindable, no default |  |
| 83 | Editor | Language… | `dev.jasper.editor.language` | Editor | — | — | Bindable, no default |  |
| 84 | Editor | Revert to Saved | `dev.jasper.editor.revert` | Editor | — | — | Bindable, no default |  |
| 85 | Editor | Reload vimrc | `dev.jasper.editor.reload-vimrc` | Editor | — | — | Bindable, no default |  |
| 86 | Find bars (terminal and Editor) | Next / Previous match | `find bar` | Find bar | `↩` / `⇧↩` | `Enter` / `Shift+Enter` | Local key |  |
| 87 | Find bars (terminal and Editor) | Close | `find bar` | Find bar | `Esc` | `Esc` | Local key |  |
| 88 | Find bars (terminal and Editor) | Match Case / Whole Words / Regex | `find bar` | Find bar | `⌥C` / `⌥W` / `⌥X` | `Alt+C` / `Alt+W` / `Alt+X` | New action | IntelliJ's find-bar keys. The toggles are mouse-only today. |
| 89 | SSH Sessions panel | Connect | `host list` | Sessions panel | `↩` | `Enter` | Local key |  |
| 90 | SSH Sessions panel | Connect in Split | `dev.jasper.remote.hosts.connect-split` | Sessions panel | `⌘↩` | `Ctrl+Enter` | New action | Mouse-only popup item today. |
| 91 | SSH Sessions panel | New Host… | `dev.jasper.remote.hosts.new` | Sessions panel | `⌘N` | `Ctrl+N` | New action |  |
| 92 | SSH Sessions panel | Edit Host… | `dev.jasper.remote.hosts.edit` | Sessions panel | `⌘I` | `Ctrl+I` | New action | Finder's Get Info. |
| 93 | SSH Sessions panel | Duplicate Host | `dev.jasper.remote.hosts.duplicate` | Sessions panel | `⌘D` | `Ctrl+D` | New action | Finder's Duplicate. |
| 94 | SSH Sessions panel | Delete Host… | `dev.jasper.remote.hosts.delete` | Sessions panel | `⌘⌫` | `Delete` | New action |  |
| 95 | SSH Sessions panel | Search Hosts | `dev.jasper.remote.hosts.search` | Sessions panel | `⌘F` | `Ctrl+F` | New action |  |
| 96 | SSH Sessions panel | Add to / Remove from Favorites | `dev.jasper.remote.hosts.favorite` | Sessions panel | — | — | New action |  |
| 97 | SFTP panel | Open | `sftp table` | SFTP panel | `↩` | `Enter` | Local key |  |
| 98 | SFTP panel | Enclosing Folder | `dev.jasper.remote.sftp.parent` | SFTP panel | `⌘↑` | `Alt+↑` | Change | macOS only: Finder's ⌘↑ (⌥↑ today). |
| 99 | SFTP panel | Go to Path | `dev.jasper.remote.sftp.path` | SFTP panel | `⌘L` | `Ctrl+L` | New action |  |
| 100 | SFTP panel | Refresh | `dev.jasper.remote.sftp.refresh` | SFTP panel | `⌘R` | `F5` | New action |  |
| 101 | SFTP panel | New Folder… | `dev.jasper.remote.sftp.new-folder` | SFTP panel | `⇧⌘N` | `Ctrl+Shift+N` | New action | Finder and Nautilus. |
| 102 | SFTP panel | Delete… | `dev.jasper.remote.sftp.delete` | SFTP panel | `⌘⌫` | `Delete` | New action |  |
| 103 | SFTP panel | Copy Paths | `dev.jasper.remote.sftp.copy-paths` | SFTP panel | `⌥⌘C` | `Ctrl+Alt+C` | New action | Finder's Copy as Pathname. |
| 104 | SFTP panel | Select All | `sftp table` | SFTP panel | `⌘A` | `Ctrl+A` | New action |  |
| 105 | SFTP panel | Download / Upload / Copy to Host | `dev.jasper.remote.sftp.*` | SFTP panel | — | — | New action | Toolbar buttons today; bindable. |
| 106 | Vault | Lock Vault | `dev.jasper.vault.lock` | Global | `⇧⌘L` | `Ctrl+Alt+Shift+L` | Add default | 1Password's Lock. |
| 107 | Vault | Generate SSH Key… | `dev.jasper.vault.generate_key` | Global | — | — | Keep |  |
| 108 | Vault | Change Master Password… | `dev.jasper.vault.change_password` | Global | — | — | New action |  |
| 109 | Vault | Edit Entry | `manager list` | Vault manager | `↩` | `Enter` | Local key |  |
| 110 | Vault | New Login… | `dev.jasper.vault.manager.new` | Vault manager | `⌘N` | `Ctrl+N` | New action |  |
| 111 | Vault | Delete Entry… | `dev.jasper.vault.manager.delete` | Vault manager | `⌘⌫` | `Delete` | New action |  |
| 112 | Vault | Search | `dev.jasper.vault.manager.search` | Vault manager | `⌘F` | `Ctrl+F` | New action |  |
| 113 | Vault | Copy Username | `dev.jasper.vault.manager.copy-username` | Vault manager | `⌘C` | `Ctrl+C` | New action | 1Password's keys. |
| 114 | Vault | Copy Password | `dev.jasper.vault.manager.copy-password` | Vault manager | `⇧⌘C` | `Ctrl+Shift+C` | New action |  |
| 115 | File chooser | Back | `chooser` | File chooser | `⌘[` | `Alt+←` | Change | macOS only: Finder (⌥← today). |
| 116 | File chooser | Forward | `chooser` | File chooser | `⌘]` | `Alt+→` | Change | macOS only. |
| 117 | File chooser | Enclosing Folder | `chooser` | File chooser | `⌘↑` | `Alt+↑` | Change | macOS only. Backspace keeps working. |
| 118 | File chooser | Edit Path | `chooser` | File chooser | `⌘L` | `Ctrl+L` | Keep |  |
| 119 | File chooser | Show Hidden Files | `chooser` | File chooser | `⇧⌘.` | `Ctrl+H` | Change | Linux/Windows only: the GTK and Nautilus key. |
| 120 | File chooser | Search | `chooser` | File chooser | `⌘F` | `Ctrl+F` | New action |  |
| 121 | File chooser | New Folder (Save) | `chooser` | File chooser | `⇧⌘N` | `Ctrl+Shift+N` | New action |  |
| 122 | File chooser | Preview Pane | `chooser` | File chooser | `⇧⌘P` | `Ctrl+Shift+P` | New action | Finder's Show Preview. |
| 123 | File chooser | Open or Choose / Cancel | `chooser` | File chooser | `↩` / `Esc` | `Enter` / `Esc` | Local key |  |
| 124 | Command palette keys | Run first / second / third verb | `palette` | Palette | `↩` / `⌘↩` / `⇧↩` | `Enter` / `Ctrl+Enter` / `Shift+Enter` | Local key |  |
| 125 | Command palette keys | Close; leave a step | `palette` | Palette | `Esc` | `Esc` | Local key |  |
| 126 | Command palette keys | Remove the scope chip | `palette` | Palette | `⌫ in an empty query` | `Backspace in an empty query` | Local key |  |

## Keymap page and documentation

- Settings › Keymap gains a **Where** column and groups rows by the areas above; a context override shows as
  "overrides ⟨action⟩ here"; the read-only rows cover every Local key in the table.
- Hardcoded hint text follows bindings: palette verb hints, find-bar tooltips, the Keymap search placeholder
  (`Search actions, plugins, or Cmd+Shift+H`).
- Docs: `docs/keyboard-shortcuts.md` is rewritten (it still describes a separate window with a Save button);
  `docs/configuration.md` loses the nonexistent `history_palette` / `snippets_palette` ids and the wrong "all
  overrides revert" warning; `README.md`, `docs/command-palette.md`, `docs/editor.md`, `docs/remote.md` and the Vault docs
  list the new defaults; Shift+PageUp/PageDown are documented.
- The status entry lists every changed default so existing users know what moved.

## Testing

- `KeyBindings`/resolver unit tests: context specificity, overlapping-context collisions, disabled and inapplicable
  actions let keys through, explicit per-platform defaults, the plugin Alt rule, user overrides.
- Headless window tests: a stroke in a terminal pane vs an Editor tab vs a panel reaches the right action; an
  unbound Ctrl+letter reaches the shell on Linux; ⌘W closes a pane, then the tab.
- A defaults test asserts the table: every row's action id, context and both defaults, and no same-context
  collisions on either platform.
- SDK contract tests for contexts; plugin tests for each plugin's actions and defaults.
- GUI checks (full screen, float, window cycling, Vim-mode interplay) go to the user.

## Delivery

One plan per runnable step:

- **K1 — contexts and defaults:** SDK 0.11 context API, resolver and dispatch (pass-through), explicit per-platform
  defaults, the plugin Alt rule, Keymap Where column; every **Keep**, **Change** and **Add default** row for app and
  plugin actions that already exist, except the Editor's commands (K3) and panel and window keys (K4).
- **K2 — new app actions:** windows, tabs, panes, terminal, View toggles, Hide All Panels, Zen Mode, find-bar toggles,
  file chooser actions.
- **K3 — Editor:** commands to content actions, new defaults, New Editor File, Toggle Vim Mode, Recent Files.
- **K4 — Remote and Vault:** panel and window actions and defaults; the documentation sweep and status entry.

## Out of scope

Chords (multi-key sequences), several bindings per action, per-user keymap presets (an "IntelliJ" or "iTerm2" keymap
switch), system-wide hotkeys, and rebinding keys inside Vim beyond today's vimrc support.
