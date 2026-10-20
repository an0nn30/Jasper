# Command palette

The command palette is one search over Jasper's commands and over every scope the plugins add: shell history,
snippets, project files, recent files, Vault logins, SSH hosts, tunnels and file servers. Open it with
**Command Palette** (Cmd+Shift+P on macOS, Ctrl+Shift+P on Linux and Windows), View › Command Palette, a click on the
header's search field, or a scope's own shortcut (see [bundled scopes](#bundled-scopes)). It opens only in the active
window.

The active [look](looks.md) decides how the palette shows. The default look drops a [card](#the-card) from the
header's search field; the [Enterprise look](enterprise.md) shows the [Run window](#the-run-window). Both search, rank
and run rows the same way.

## Searching

The palette opens with no scope chip, searching **All**; the card's field reads "Search everything".

- **Empty query.** Up to three **Recently used** rows, the rows you ran last from the palette, then rows ranked by how
  often and how recently you ran them under **Most used**, topped up with each scope's own suggestions (Commands'
  first) until there are ten. When every used row already shows under Recently used (at first, until a fourth row has
  been used), the second heading reads **Suggested**. Only scopes marked "in recents" in the
  [scope table](#bundled-scopes) appear here, and a row its scope no longer finds (a deleted host or snippet) drops
  out.
- **Typing.** Every scope that takes part in All is searched at once, each under a heading with its name: Commands
  first, then the plugin scopes in the order the plugins registered them. The list holds at most 200 rows. When that
  cuts a scope short, a "More in <Scope>…" row (hint "Show every match in <Scope>") sets that scope's chip and keeps
  the query; a scope reached after the 200 rows are used shows no section.
- **No match.** The list reads "Nothing found", or "No matching <scope>" with a chip set.

The list shows `palette.visible_rows` rows before it scrolls (5 by default, 8 under the Enterprise look); headings add
their own height.

## Scope chips

A chip narrows the search to one scope.

- `>` at the very start of the query sets the Commands chip. `<scope> >` sets the chip of the scope whose name is that
  text, ignoring case, or the only scope whose name starts with it: `history > ls`, `hist >`, `ssh >`. The prefix
  leaves the field and the rest of the text stays as the query. After a leading space, with a chip already set or
  during a step, `>` is ordinary text. Deleting text never sets a chip.
- Backspace in an empty query removes the chip and returns to All.
- A scope's opener shortcut opens the palette on its chip, or switches to that chip while the palette is open (except
  [Tunnels](#bundled-scopes) while the card is open). Pressing it again on the chip already showing closes the card;
  the Run window takes the keyboard instead.
- The palette's own shortcut switches from a chip back to All. With no chip, it closes the card; the Run window takes
  the keyboard instead.

## Rows and keys

A row shows an icon (card only), its title, its detail and a tag on the right, such as a command's shortcut. A
scope offers one to three verbs per row. The card's hint bar, under the list, shows the selected row's detail (or its
tag when it has none) and, when the scope has more than one verb, every verb with its key, such as "Paste Enter" and
"Paste and run Ctrl+Enter" on Linux and Windows; on macOS the keys show as the Return, Command and Shift symbols. Click
one to run it.

| macOS | Linux and Windows | Action |
|---|---|---|
| Enter | Enter | Run the selected row's first verb; in a step, complete it |
| Cmd+Enter | Ctrl+Enter | Run the second verb |
| Shift+Enter | Shift+Enter | Run the third verb |
| Up, Down | Up, Down | Move the selection, skipping headings and stopping at the ends |
| Escape | Escape | Leave a step, back to the list with the query kept; otherwise close the palette |
| Backspace | Backspace | In an empty query, remove the chip |
| Tab, Shift+Tab | Tab, Shift+Tab | Move between a step's fields, wrapping; otherwise nothing |

These keys are fixed: Settings › Keymap lists the Enter, arrow, Tab and Escape keys, but none of them can be rebound.
Digits are search text. While an input method is composing, unmodified keys other than Escape go to the field.

Just before a verb runs, its scope checks that the row can still run; when it cannot, the list refreshes instead. When
a command fails, the palette closes and a **Command failed** notification reads "Could not run <title>. See the
application log for details." A plugin scope that fails is reported as that plugin's problem (see
[the Errors window](notifications.md#the-errors-window)).

## Steps

Some verbs show a small form in the palette, a **step**, instead of running at once: a snippet's placeholders, or the
name that History's Save as snippet… asks for. Each field is a labelled row, prefilled; the first takes the keyboard
with its text selected. Enter completes the step, Escape returns to the list. A problem the scope reports keeps the
form open and shows under the fields; a plugin step that fails reads "The plugin could not complete this step." A step
can end by reopening the palette on another scope with a row selected: a saved snippet opens the Snippets chip on the
new snippet.

## The card

Under the default look the card drops from the header's search field: it covers the field, centred on it and at least
560 px wide, and moves or narrows to stay clear of the window controls. In Compact view mode, or when the window has no
room for the field, it opens centred across the window with its top one fifth of the way down.

- A fresh opening unrolls the card downward over 300 ms while animations are on (`ui.animations.enabled`).
- While the card is open it holds the window's input: Jasper's other shortcuts and actions do nothing, except Copy
  and Paste in its fields, the palette shortcut and the scope openers.
- Rows run in the pane that was focused when the card opened.
- It closes on Escape, a click outside it (which goes no further), running a verb, completing a step, switching tab and
  the window losing focus. The keyboard returns where it was.
- Drag it by an empty area: the padding around the field, the hint bar or an empty list. Near the window's vertical
  centre line, or one fifth down from its top, it snaps to that line; the lines are never drawn. Releasing saves the
  place for every window (`[palette]` `center_x` and `top` in `ui-state.toml`). Dragged back to the field (near its
  usual place across, at or above it) it re-attaches there. A double click on an empty area forgets the saved place.

## The Run window

Under the Enterprise look the palette is the **Run window**, which opens centred across the workspace with its top a
seventh of the way down; when that would take it past the middle, it moves up to end there, as far as the tool window
area allows. Its title row reads **Run** with the palette shortcut and × (Close). The help row under it describes the
chosen scope, or reads "Type the name of a session, command, file or snippet, and Jasper will open it for you." with
no chip. The **Open:** field holds the chip and the query, with no placeholder, and ends in
a ▼ menu, "Choose what to open", which lists All and every scope with its shortcut.

- The palette shortcut, **▶ Run…** at the start of the status bar and the toolbar's **Search Everywhere** button (its
  magnifier, before **Settings…**) open it.
  While it is open they give it the keyboard back and refresh its list; a click anywhere on it also gives it the
  keyboard.
- It stays open while you work. Clicks outside it reach the window, a click in a pane gives that pane the keyboard,
  and a row runs in the pane that is current when you run it. Switching tab refreshes the list; the window losing focus
  leaves it open.
- It closes on Escape (with the keyboard in it), ×, **Cancel**, running a row or completing a step.
- The list keeps the height of `palette.visible_rows` rows, fewer when the window is too short, under **Recent** and
  **Most used** (or **Suggested**), under a chip's own heading, or under one **Matches** heading while you type, with
  the selected row's position ("3 of 12") on its right. Each row starts with a square in its scope's colour; a row's
  second and third verbs are in its context menu.
- The line under the list names the selected row by its kind, such as "Session:", "File:" or "Command:", followed by
  its detail or title.
- **Open in a split pane** is enabled when the selected row's scope has a split verb (SSH's Connect in split); ticked,
  Enter and the primary button run that verb. Each opening clears it.
- The primary button reads the selected row's first verb ("Open" with no row selected) and is disabled during a step.
  **Browse…** runs File › Open File… and leaves the Run window open.
- Drag it by its title row or an empty area. Released near its home, across and down, it goes back there; a double
  click on its title row forgets the place and sends it home. The place is saved for every window
  (`[palette.run_window]` `left` and `bottom` in `ui-state.toml`). The Run window has no opening animation and no snap
  lines.

## Bundled scopes

Commands is built in; every other scope belongs to a [bundled plugin](plugins.md) and goes when that plugin is
disabled. Openers are actions, rebindable in Settings › Keymap; "none" means no default shortcut.

| Scope (chip) | Id | Enter | Cmd+Enter (Ctrl+Enter) | Shift+Enter | Opener, macOS / Linux and Windows | In All | In recents |
|---|---|---|---|---|---|---|---|
| Commands | `jasper.commands` | Run | | | type `>` | yes | yes |
| History (Shell History) | `dev.jasper.history.scope` | Paste | Paste and run | Save as snippet… | Search Shell History, Cmd+R / Ctrl+Shift+R | yes | no |
| Snippets (Snippets) | `dev.jasper.snippets.scope` | Insert | Paste and run | Edit file | Snippets…, Cmd+J / Ctrl+Shift+J | yes | yes |
| Files (Project Index) | `dev.jasper.indexer.files` | Open | Copy path | Open terminal here | Go to File…, Cmd+P / Ctrl+Alt+Shift+P | yes | yes |
| Recent files (Editor) | `dev.jasper.editor.recent` | Open | Remove from recent files | | Recent Files…, none | yes | yes |
| Vault (Credential Vault) | `dev.jasper.vault.scope` | Copy password | Copy username | Open in Vault | Search Vault Logins, Cmd+\\ / Ctrl+Shift+\\ | no | no |
| SSH (Remote) | `dev.jasper.remote.scope` | Connect | Connect in split | Edit host… | Connect to SSH Host..., Cmd+Shift+H / Ctrl+Shift+H | yes | yes |
| Tunnels (Remote) | `dev.jasper.remote.tunnels.scope` | Start or stop | Open in browser | Edit tunnel… | Start or Stop a Tunnel..., none | yes | yes |
| File Servers (File Server) | `dev.jasper.fileserver.scope` | Start or Stop | Edit | | Start or Stop a File Server…, none | yes | no |

- History's Save as snippet… is there only while the Snippets plugin runs.
- History is searched in All but never offered before you type, since shell commands can hold anything. Vault logins
  are searched only with the Vault chip. History, Vault and File Servers never appear among the empty query's rows.
- The Tunnels scope is not tied to its opener: a shortcut you bind to Start or Stop a Tunnel... opens the palette on
  the Tunnels chip, but does nothing while the card is open, and the Run window's ▼ menu shows no shortcut for Tunnels.
- The Sample plugin adds a Greetings scope (Paste, Paste and run; opener Sample Greetings…, Cmd+Option+G on macOS and none
  on Linux and Windows) only when its `demo_scope` setting is on.

What each scope lists and does is described with its plugin: [Shell History and Snippets](history-and-snippets.md),
[Project Index](project-index.md), [Editor](editor.md), [Credential Vault](credential-vault.md), [Remote](remote.md)
and [File Server](file-server.md).

## Commands

The Commands scope lists the window's enabled commands: Jasper's built-in actions except Command Palette itself, every
plugin action, the View › Show/Hide choices ("Toolbar: Icons and Labels", "Toolbar: Icons Only", "Toolbar: Hidden",
"Show Status Bar" or "Hide Status Bar", "Show Rail" or "Hide Rail"), and commands found only here, such as
"Show Jasper", "Themes…", "Window Settings…" and "Terminal Scheme: <name>". Split Right and Split Down read
"Split Right · Vertical" and "Split Down · Horizontal". A row's tag is the command's shortcut.

- A search ranks an exact title first, then titles that start with the query, titles whose words start with each query
  word, titles that contain it, keyword matches, and fuzzy matches with fewer gaps first. Ties go to recently used
  commands, then to alphabetical order.
- With the Commands chip and an empty query, it lists your last three distinct palette commands under **Recent**, or,
  before there are any, the first three enabled of New Tab, Split Right, Settings and New Window under **Suggested**.
- Only commands run from the palette count as recent: running one from the toolbar, a menu or a shortcut changes
  nothing here.

The palette shortcut is the `command_palette` action; see [keyboard shortcuts](keyboard-shortcuts.md) to change it.

## Recents and command-history.toml

Every verb run on a row of a scope that takes part in recents counts as a use of that row, before its step or action
runs. Jasper keeps the 50 most recently used rows, dropping the least recent, and the Commands scope's last three
commands. Both live in `command-history.toml` in [Jasper home](getting-started.md#jasper-home), shared by every window:

```toml
version = 2
recent = ["split_right", "new_tab"]

[[used]]
scope = "jasper.commands"
row = "split_right"
title = "Split Right · Vertical"
count = 4
last = 1760000000000
```

`recent` holds at most three command ids. Each `[[used]]` table names the scope, the row's id and title, how often it
ran (1 to 1,000,000) and when last, in milliseconds since 1970. A use is found again by searching its title in its
scope. A file with no uses is written as `version = 1` with `recent` alone. The file is read as strict UTF-8 of at most
128 KiB; a missing file means no history, and an unreadable or invalid one is logged and ignored.

Jasper writes the file after every change, on a background thread, through a temporary file that replaces it. Quitting
waits up to two seconds for the last write. Separate Jasper processes do not lock the file: the last one to write wins.

## Settings and saved state

| Key or file | Values | Default | Notes |
|---|---|---|---|
| `palette.visible_rows` | 1–20 | 5; 8 under the Enterprise look | Settings › Appearance & Behavior › Command Palette › Visible rows; applies live, also to an open palette. See [configuration](configuration.md#palette). |
| [`ui-state.toml`](configuration.md#ui-statetoml) `[palette]` `center_x`, `top` | fractions 0–1 | none | The dragged card's place; absent while the card stays at the search field |
| `ui-state.toml` `[palette.run_window]` `left`, `bottom` | fractions 0–1 | none | The dragged Run window's left and bottom edges |
| `command-history.toml` | see [above](#recents-and-command-historytoml) | none | Uses and Commands recents |

## Scopes from plugins

A plugin adds a scope with [`Palette.register`](sdk-reference.html#dev.jasper.sdk.palette.Palette) and needs the
`palette.contribute` capability. Every window shows it after Commands; its id starts with the plugin's id, and it goes
when the plugin stops. A scope's calls run on the UI thread and must do no I/O; a call that fails is contained, so the
scope shows no rows or does nothing, and the failure is the plugin's problem.

The two views use a scope's [`ScopeSpec`](sdk-reference.html#dev.jasper.sdk.palette.ScopeSpec) differently:

| Field | Card | Run window |
|---|---|---|
| `label` | chip and section heading | chip and ▼ menu entry |
| `placeholder` | the field's placeholder with the chip set | not shown |
| `description` | not shown | the help row with the chip set |
| `hue`, `itemNoun` | not shown | the row's square (the plugin's `icon_hue` when `NONE`); the detail line's "<Noun>:" |
| `monospaceRows` | titles in the monospace face | not used |
| a row's icon | shown | not shown |
| a `SPLIT` verb | runs on its key like any other verb | Open in a split pane |
| `shortcutActionId` | that action's shortcut switches to the chip | shown in the ▼ menu, and switches to the chip |

[`Palette.open`](sdk-reference.html#dev.jasper.sdk.palette.Palette) opens the palette on a scope, optionally with a
query and a selected row. See [palette scopes](plugin-authoring.md#palette-scopes) for the plugin side and the testkit.
Recipes for new commands are in [application maintenance](app-maintenance.md#palette-scope).
