# Keyboard shortcuts

Every built-in action, and every action Jasper or a plugin registers, can have a shortcut; the View › Show/Hide ›
Toolbar items cannot. Built-in actions have one default for macOS and one for Linux and Windows; plugins suggest their
own. Menus, toolbar tooltips and the command palette always show the shortcut in effect. Change any of them in
[Settings › Keymap](#settings--keymap) or in [`config.toml`](#in-configtoml).

macOS shortcuts are written with Cmd (Command), Ctrl (Control), Option and Shift. The Linux and Windows defaults use
Ctrl+Shift (or Ctrl+Alt+Shift) where macOS uses Cmd, so that plain Ctrl keys such as Ctrl+C and Ctrl+K reach the
program in the terminal; Ctrl+Tab and Ctrl+1 to Ctrl+9 are the exceptions.

## Built-in actions

The tables follow the sections of Settings › Keymap. The ID is the name to use under `[keybindings]`.

### Tabs

| Action | ID | macOS | Linux and Windows |
|---|---|---|---|
| New Tab | `new_tab` | Cmd+T | Ctrl+Shift+T |
| Close Tab | `close_tab` | Cmd+Option+W | Ctrl+Alt+Shift+W |
| Next Tab | `next_tab` | Cmd+Shift+] | Ctrl+Tab |
| Previous Tab | `previous_tab` | Cmd+Shift+[ | Ctrl+Shift+Tab |
| Rename Tab | `rename_tab` | Cmd+I | Ctrl+Shift+I |
| Select Tab 1 … Select Tab 9 | `select_tab_1` … `select_tab_9` | Cmd+1 … Cmd+9 | Ctrl+1 … Ctrl+9 |

### Panes

| Action | ID | macOS | Linux and Windows |
|---|---|---|---|
| Split Right | `split_right` | Cmd+D | Ctrl+Shift+D |
| Split Down | `split_down` | Cmd+Shift+D | Ctrl+Alt+Shift+D |
| Close Pane | `close_pane` | Cmd+W | Ctrl+Shift+W |
| Zoom Pane | `zoom_pane` | Cmd+Shift+Enter | Ctrl+Alt+Shift+Enter |
| Focus Pane Left | `focus_pane_left` | Cmd+Option+Left | Ctrl+Alt+Shift+Left |
| Focus Pane Right | `focus_pane_right` | Cmd+Option+Right | Ctrl+Alt+Shift+Right |
| Focus Pane Up | `focus_pane_up` | Cmd+Option+Up | Ctrl+Alt+Shift+Up |
| Focus Pane Down | `focus_pane_down` | Cmd+Option+Down | Ctrl+Alt+Shift+Down |

### Search

| Action | ID | macOS | Linux and Windows |
|---|---|---|---|
| Find | `find` | Cmd+F | Ctrl+Shift+F |
| Find Next | `find_next` | Cmd+G | Ctrl+Shift+G |
| Find Previous | `find_previous` | Cmd+Shift+G | Ctrl+Alt+Shift+G |

### Clipboard

| Action | ID | macOS | Linux and Windows |
|---|---|---|---|
| Copy | `copy` | Cmd+C | Ctrl+Shift+C |
| Paste | `paste` | Cmd+V | Ctrl+Shift+V |

### Appearance

| Action | ID | macOS | Linux and Windows |
|---|---|---|---|
| Increase Font Size | `font_bigger` | Cmd+= | Ctrl+Shift+= |
| Decrease Font Size | `font_smaller` | Cmd+- | Ctrl+Shift+- |
| Reset Font Size | `font_reset` | Cmd+0 | Ctrl+Shift+0 |

### Terminal

These apply only while a terminal pane (or its find bar) has the focus.

| Action | ID | macOS | Linux and Windows |
|---|---|---|---|
| Previous Prompt | `previous_prompt` | Cmd+Up | Ctrl+Shift+Up |
| Next Prompt | `next_prompt` | Cmd+Down | Ctrl+Shift+Down |
| Clear Scrollback | `clear_scrollback` | Cmd+K | Ctrl+Shift+K |
| Clear Terminal | `clear_terminal` | none | none |

### Command Palette

| Action | ID | macOS | Linux and Windows |
|---|---|---|---|
| Command Palette | `command_palette` | Cmd+Shift+P | Ctrl+Shift+P |

### Application

| Action | ID | macOS | Linux and Windows |
|---|---|---|---|
| New Window | `new_window` | Cmd+N | Ctrl+Shift+N |
| Open File… | `open_file` | none | none |
| Zen (Enter Zen Mode / Exit Zen Mode) | `toggle_zen_mode` | none | none |
| Rail (Show Rail / Hide Rail) | `toggle_rail` | none | none |
| Status Bar (Show Status Bar / Hide Status Bar) | `toggle_status_bar` | none | none |
| Settings | `open_settings` | Cmd+, | Ctrl+Shift+, |
| Reload Config | `reload_config` | none | none |
| Quit | `quit` | Cmd+Q | Ctrl+Alt+Shift+Q |

[The workspace](workspace.md) describes what each action does.

## Jasper's other actions

These actions have no default shortcut; give them one in Settings › Keymap (in the section shown) or under
`[keybindings]` with the quoted ID.

| Action | ID | Keymap section |
|---|---|---|
| Manage Plugins… | `"plugins.manage"` | Application |
| Keyboard Shortcuts… | `"app.shortcuts"` | Application |
| Change Memory Settings… | `"app.memory"` | Application |
| Edit Custom VM Options… | `"app.vmoptions"` | Application |
| About Jasper | `"app.about"` | Application |
| Run Garbage Collection | `"app.memory.gc"` | Application |
| Migrate Config | `"config.migrate"` | config |
| Install 'jasper' Command in PATH | `"commandline.install"` | commandline |
| Uninstall 'jasper' Command from PATH | `"commandline.uninstall"` | commandline |
| Notifications: Show History | `"notifications.history"` | notifications |
| Notifications: Dismiss Cards | `"notifications.dismiss"` | notifications |
| Notifications: Show Errors | `"notifications.errors"` | notifications |

A plugin's tool window that has no toggle action of its own gets **Toggle** *title*, with the ID `<panel id>.toggle`.
Under the [Enterprise look](enterprise.md#windows-and-menus) a tool window's toggle shortcut makes it the active tool
window; pressed while the window shows but is not the active one, it activates the window instead of hiding it.

The palette's view commands (**Toolbar: Icons and Labels**, **Themes…**, **Terminal Scheme: …** and the like) and the
View › Show/Hide › Toolbar items are not in the keymap and take no shortcut.

## Bundled plugins

These bundled plugin actions suggest a default shortcut. All other plugin actions have none; Settings › Keymap lists
them under each plugin's name.

| Plugin | Action | ID | macOS | Linux and Windows | Applies |
|---|---|---|---|---|---|
| Remote | Connect to SSH Host... | `dev.jasper.remote.connect` | Cmd+Shift+H | Ctrl+Shift+H | Anywhere |
| Remote | Toggle SSH hosts | `dev.jasper.remote.hosts` | Cmd+Option+1 | Alt+1 | Anywhere |
| Remote | Toggle SFTP | `dev.jasper.remote.sftp.toggle` | Cmd+Option+2 | Alt+2 | Anywhere |
| Remote | Transfers | `dev.jasper.remote.transfers` | Cmd+Option+3 | Alt+3 | Anywhere |
| Remote | Open SFTP here | `dev.jasper.remote.sftp` | Cmd+Shift+O | Ctrl+Alt+Shift+O | Terminal pane |
| Remote | SFTP: Back | `dev.jasper.remote.sftp.back` | Option+Left | Alt+Left | SFTP panel |
| Remote | SFTP: Forward | `dev.jasper.remote.sftp.forward` | Option+Right | Alt+Right | SFTP panel |
| Remote | SFTP: Parent Folder | `dev.jasper.remote.sftp.parent` | Option+Up | Alt+Up | SFTP panel |
| Credential Vault | Open Vault... | `dev.jasper.vault.open` | Cmd+Shift+\ | Ctrl+Alt+Shift+\ | Anywhere |
| Credential Vault | Search Vault Logins | `dev.jasper.vault.search` | Cmd+\ | Ctrl+Shift+\ | Anywhere |
| Credential Vault | Lock Vault | `dev.jasper.vault.lock` | Cmd+Shift+L | Ctrl+Alt+Shift+L | Anywhere |
| Editor | Open in Editor… | `dev.jasper.editor.open` | Cmd+O | Ctrl+Shift+O | Anywhere |
| Project Index | Go to File… | `dev.jasper.indexer.goto` | Cmd+P | Ctrl+Alt+Shift+P | Anywhere |
| Shell History | Search Shell History | `dev.jasper.history.open` | Cmd+R | Ctrl+Shift+R | Anywhere |
| Snippets | Snippets… | `dev.jasper.snippets.open` | Cmd+J | Ctrl+Shift+J | Anywhere |

The Sample plugin registers its demo actions only when its demo settings are on: **Run Sample Activity**
(`dev.jasper.sample.demo`, Cmd+Option+J; Ctrl+Alt+Shift+J) and **Sample Greetings…**
(`dev.jasper.sample.greetings.open`, Cmd+Option+Y; Ctrl+Alt+Shift+Y).

While an Editor tab is selected, the Editor's own commands take their keys, for example Cmd+S (Ctrl+S) to save. They
are listed in the Editor tab's right-click menu and in [Editor](editor.md#commands-and-shortcuts), and are not part of
the keymap. The window's Close Pane, Cmd+W (Ctrl+Shift+W), closes an Editor tab.

## Fixed keys

These keys belong to one control and cannot be changed. Settings › Keymap lists those of the find bar, the command
palette (except Backspace), plugin and help windows, the Plugins list and the SSH Hosts and Vault entries lists as
read-only rows.

| Where | Key | Does |
|---|---|---|
| Find bar | Enter, Shift+Enter | Next match, previous match |
| Find bar | Escape | Closes the find bar |
| Command palette | Enter, Cmd+Enter (Ctrl+Enter), Shift+Enter | Runs the selected row's first, second or third action |
| Command palette | Up, Down | Moves the selection |
| Command palette | Tab, Shift+Tab | Next or previous field of a step form |
| Command palette | Escape | Leaves a step form, or closes the palette |
| Command palette | Backspace in an empty query | Removes the scope chip |
| Terminal pane | Shift+Page Up, Shift+Page Down | Scrolls the pane up or down by a screen less one line |
| Plugin and help windows | Cmd+W (Ctrl+W) | Close Window |
| Plugins list (Settings › Plugins) | Context Menu, Shift+F10 | Opens the selected plugin's menu |
| SSH Hosts list (Remote) | Enter | Goes to the host's most recently used running pane, or connects when none runs; on a group, expands or collapses it |
| SSH Hosts list (Remote) | Up, Down | Next or previous host or group |
| Vault entries list (Credential Vault) | Enter | Edits the selected credential |
| Vault entries list (Credential Vault) | Up, Down | Next or previous credential |
| File chooser | Enter, Escape | The primary button (Open, Save or Choose), Cancel |
| File chooser | Option+Left, Option+Right (Alt+Left, Alt+Right) | Back, Forward |
| File chooser | Option+Up (Alt+Up); Backspace in the listing | Up one folder |
| File chooser | Cmd+L (Ctrl+L) | Type a location |
| File chooser | Cmd+Shift+. (Ctrl+Shift+.) | Show or hide hidden items |
| Settings | Enter, Escape | OK, Cancel |
| Dragging a tool window | Escape | Cancels the drag |
| Menus in the window (Linux and Windows) | F10 | Opens the first menu (in a terminal pane F10 goes to the program) |

## How shortcuts apply

- A shortcut applies in a place: **Global** (anywhere in a workspace window), **Terminal** (in a terminal pane and its
  find bar), or a plugin's own place: its tabs or one of its tool windows. The same key may serve a Global action and
  a plugin's place, and the most specific place that holds the focus wins; within the Global and Terminal places each
  key has one action.
- When the action is unavailable (Copy with nothing selected, for example), the key reaches the focused control, except
  in a terminal pane: there Jasper takes the key and does nothing, so Ctrl+Shift+C never reaches the shell as Ctrl+C.
- Copy and Paste in a text field use the field's own copy and paste.
- On Linux and Windows, Ctrl+Tab and Ctrl+Shift+Tab switch tabs even while a text field, a list or a table has the
  focus.
- While the command palette card is open it takes the keyboard; Jasper's other shortcuts wait until it closes, except
  Copy and Paste in its query field, the palette's own shortcut (back to All from a scope chip, otherwise closing the
  card) and the plugin scopes' shortcuts, which switch the open palette to that scope, or close the card when that
  scope is already showing.
- Some keys never reach Jasper because the operating system keeps them, such as Cmd+Tab on macOS.

## Changing shortcuts

### Settings › Keymap

Open it with Help › **Keyboard Shortcuts…**, **Keyboard Shortcuts…** in the command palette, or Settings › Keymap. It
lists every built-in action, every action Jasper and the plugins have registered, and some of the
[fixed keys](#fixed-keys) as read-only rows, in sections for each area and each plugin. Each row shows where its
shortcut applies; an unassigned action shows **Unassigned**, and a plugin default that could not be used says why.

- **Search.** Type an action, a plugin, a section or a shortcut. A query with `+`, such as `cmd+shift+h`, matches that
  shortcut in any modifier order; `command` and `meta` mean `cmd`, `control` means `ctrl`, `option` means `alt`, `esc`
  and `return` mean Escape and Enter, and the macOS menu symbols for Command, Shift, Option and Control work too.
  **Record** (in the search field) finds actions by the keys you press, Tab and Escape included, until you click **Stop
  recording**. **Clear** empties the search; the buttons beside it expand or collapse every section.
- **Edit.** Click a row's shortcut, or select the row and press Enter. The edit card records the next combination you
  press: Enter sets it, Escape cancels. **Set** stages the shortcut and **Cancel** drops it. The ⋯ menu has
  **Record any key…** (the next key is taken literally, even Enter or Escape), **Remove shortcut** and **Restore
  default**. While Jasper records, keys such as Cmd+W and Cmd+Q are recorded instead of acting.
- **Conflicts.** If the shortcut is taken, the card says "Already used by: …". Turn on **Reassign from conflicting
  action** to take it; the other action is left without a shortcut.
- **Save.** **Apply** or **OK** in Settings writes every staged change to `[keybindings]` in `config.toml` at once, and
  every window uses it immediately. Changes made to the file outside Jasper show up in the page as it reloads.

Under the Enterprise look the page is a table with Action, Scope and Shortcut columns and text buttons (**Find by
Shortcut**, **Stop**, **Expand All**, **Collapse All**).

### In config.toml

Each line under `[keybindings]` binds one action by its ID; a plugin action's ID is quoted.

```toml
[keybindings]
toggle_zen_mode = "cmd+shift+z"
clear_terminal = "cmd+alt+k"
reload_config = "none"
"dev.jasper.vault.lock" = "cmd+alt+l"
```

[Shortcut syntax](configuration.md#shortcut-syntax) gives the modifier and key names, and
[Conflicts and invalid bindings](configuration.md#conflicts-and-invalid-bindings) what happens to a binding Jasper
cannot use.
