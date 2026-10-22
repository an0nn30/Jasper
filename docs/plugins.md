# Plugins

Most of Jasper is plugins. The application provides the workspace (windows, tabs, panes and tool windows), the
terminal, the command palette, notifications, Settings, themes and looks. SSH, SFTP and tunnels, the credential vault,
the editor, file servers, the project index, shell history, snippets and the Enterprise look are bundled plugins,
written against the same public SDK (`jasper-sdk`, see the [SDK reference](sdk-reference.html)) that any other plugin
compiles against. Each of them can be turned off in Settings › Plugins, and a plugin you install or write uses the same
calls they do.

Bundled plugins ship inside the application, load without a review of their [capabilities](#capabilities), and can be
disabled but not removed. Plugins reach each other only through the packages they export; see
[plugin APIs](plugin-apis.md).

Plugins load once, when Jasper starts. Installing, updating, enabling, disabling and removing a plugin are recorded at
once and take effect at the next launch.

Shortcuts below are given as macOS / Linux and Windows.

## What a plugin can add

- Actions, listed in the command palette and in Settings › Keymap, with default shortcuts.
- Items in the File, Edit, View, Tab, Tools, Session and Transfer menus, a menu of its own, and items in the
  terminal's context menu. Jasper has no Tools, Session or Transfer items of its own, so those menus appear only while
  a plugin has entries in them.
- Toolbar buttons, buttons on the left tool window stripe and status-bar items, all in the main window, and dialogs.
- Content tabs: its own views in tabs and panes beside terminals, in any window. They can open a terminal or another
  item beside themselves, can be offered in the Split menus, and can come back when Jasper restores its windows.
- Background activities, shown on [Desk Buddy](notifications.md#desk-buddy) while they run and posted in the plugin's
  "*name* activities" notification group when they succeed or fail.
- Tool windows in the main window, each with a toggle in View › Tool Windows (in the Window menu under the Enterprise
  look).
- Command palette scopes, and places in Jasper's file chooser.
- Terminal sessions of its own, such as SSH, which can come back when Jasper restores its windows, and variables in
  the environment of new shells.
- The editor Jasper opens files with, `jasper` commands, notifications in its own groups, a page in Settings › Tools,
  themes and a look.

Everything a plugin registered is removed when it stops or fails to start. A shortcut you set under `[keybindings]` for
a plugin's action wins over the plugin's default. A default that collides with a shortcut already in use is dropped:
Jasper's log records "Default shortcut '…' is already used by '…'; bind the action under [keybindings] to give it one.",
and the action's row in Settings › Keymap shows the same text in its tooltip. See
[keyboard shortcuts](keyboard-shortcuts.md).

## Bundled plugins

| Plugin | Id | Version | On by default | Guide |
|---|---|---|---|---|
| Remote | `dev.jasper.remote` | 0.16.0 | Yes | [Remote](remote.md) |
| Credential Vault | `dev.jasper.vault` | 0.7.0 | Yes | [Credential Vault](credential-vault.md) |
| Editor | `dev.jasper.editor` | 0.16.0 | Yes | [Editor](editor.md) |
| File Server | `dev.jasper.fileserver` | 0.6.0 | Yes | [File Server](file-server.md) |
| Project Index | `dev.jasper.indexer` | 0.3.0 | Yes | [Project Index](project-index.md) |
| Shell History | `dev.jasper.history` | 0.3.0 | Yes | [Shell History and Snippets](history-and-snippets.md) |
| Snippets | `dev.jasper.snippets` | 0.3.0 | Yes | [Shell History and Snippets](history-and-snippets.md) |
| Enterprise | `dev.jasper.enterprise` | 0.3.0 | No | [The Enterprise look](enterprise.md) |
| Sample | `dev.jasper.sample` | 0.5.0 | Yes, but inert | [Plugin authoring](plugin-authoring.md) |

Every bundled plugin declares `sdk = ">=0.21.0, <0.22"`. A plugin uses another plugin it lists under "Uses, when
present" only while that plugin is running; without it, the features that need it are missing.

### Remote

SSH, SFTP and tunnels: saved hosts with groups, favourites and ProxyJump jump hosts, host-key trust, Vault or SSH-agent
authentication, SSH terminal tabs and splits that come back when Jasper restores its windows, an SFTP browser with
persistent file transfers, local, remote and SOCKS tunnels with live counters, import from `~/.ssh/config`, a Sessions
tool window that also lists the WSL distributions on Windows, the `ssh >` and `tunnels >` palette scopes, and saved
hosts as places in Jasper's file chooser.

- **Start with** Session › Connect to SSH Host... (Cmd+Shift+H / Ctrl+Shift+H).
- **Capabilities** `terminal.open`, `session.provide`, `terminal.observe`, `palette.contribute`, `files.source`.
- **Uses, when present** Credential Vault (`>=0.2`) for host logins and keys, Editor (`>=0.2`) to edit remote files.
- **Exports** `dev.jasper.remote.api`, which Project Index uses to index projects on saved hosts.

### Credential Vault

Logins, SSH keys and secure notes in one encrypted file, which can be bound to this device: a manager dialog, a lock
item in the status bar, a palette scope that copies a login's password or username, SSH key generation and a master
password change. Other plugins, such as Remote and File Server, get a credential only after you allow it in a grant
dialog, once or always; File › Vault Plugin Grants... shows the grants. Secure notes are never handed to other plugins.

- **Start with** File › Open Vault... (Cmd+Shift+\\ / Ctrl+Alt+Shift+\\).
- **Capabilities** `palette.contribute`.
- **Exports** `dev.jasper.vault.api`.

### Editor

Local and remote text files in workspace tabs and panes, a file in several views that share its text and undo history,
with syntax highlighting, find and replace, a Markdown preview that follows relative links and shows images, its own
fonts or the terminal's, recent files in the command palette and an optional Vim mode. It is the editor Jasper opens
files with (or it hands them to another editor you choose), it can set `EDITOR` for Jasper's shells, and it adds the
`jasper editor` command.

- **Start with** File › Open in Editor… (Cmd+O / Ctrl+Shift+O).
- **Capabilities** `palette.contribute`, `terminal.environment`, `editor.provide`, `terminal.open`.
- **Exports** `dev.jasper.editor.api`, which Remote and Project Index use to open files.

### File Server

Shares a folder on this computer over HTTP, HTTPS, FTP, FTPS, SFTP or TFTP: named servers in a File Servers tool
window, read-only and reachable from this computer only until you change that, with an activity log, Vault logins,
**Share Path** in the terminal's context menu and **Serve Current Folder over …** in the command palette.

- **Start with** Transfer › Share Folder….
- **Capabilities** `network.listen`, `terminal.observe`, `palette.contribute`, `clipboard.read`.
- **Uses, when present** Credential Vault (`>=0.2`) for Vault logins.

### Project Index

When a terminal enters a folder that looks like a project, locally or on one of Remote's saved hosts, an "Index
project?" card offers to index it. The files of indexed projects are found from the command palette's Files
[chip](command-palette.md#scope-chips) and open in the Editor. Folders can also be indexed by hand.

- **Start with** Go to File… (Cmd+P / Ctrl+Alt+Shift+P), or File › Index Folder….
- **Capabilities** `terminal.observe`, `terminal.open`, `palette.contribute`.
- **Uses, when present** Editor (`>=0.7`) to open the files it finds, Remote (`>=0.12`) for projects on saved hosts.

### Shell History

The command palette's History chip searches the history files of zsh, bash, fish, nushell and PowerShell, plus the
commands Jasper saw finish through shell integration, and pastes a command or pastes and runs it. It never writes a
history file.

- **Start with** Search Shell History (Cmd+R / Ctrl+Shift+R).
- **Capabilities** `palette.contribute`, `terminal.observe`, `terminal.inject`.
- **Uses** Snippets (`>=0.1`) for **Save as snippet…**. Shell History needs Snippets loaded: with Snippets disabled or
  missing, it fails to start.

### Snippets

Saved commands in the command palette's Snippets chip: insert one, paste and run it, fill in its `{{placeholder}}`
fields, or edit the file that holds them, `plugins/dev.jasper.snippets/data/snippets.toml` in Jasper home.

- **Start with** Snippets… (Cmd+J / Ctrl+Shift+J).
- **Capabilities** `palette.contribute`, `terminal.inject`.
- **Exports** `dev.jasper.snippets.api`, which Shell History uses to save a command as a snippet.

### Enterprise

The Enterprise look: IntelliJ Classic's title bar on macOS and Windows, with the menus in the macOS menu bar or the
Windows title bar (on Linux the system title bar and a menu row in the window), labelled tool window stripes, a 28-pixel
toolbar with a Search Everywhere button, the Run window in place of the command palette card, classic controls and
IntelliJ's classic icons, with the IBM Plex fonts (IBM Plex Mono is also the Editor's default font), in Jasper Meadow
by default. It also ships the Enterprise theme (Light and Dark, ports of IntelliJ Light and New UI Dark). It is off by
default; turn it on in Settings › Plugins and restart. The look is data, a kit file with its fonts and theme, and the
plugin's code does nothing; Credential Vault, Remote and File Server use their classic layouts under it. See
[looks](#looks-and-plugin-themes).

- **Capabilities** `presentation.provide`.

### Sample

The smallest complete plugin: the runtime's end-to-end test fixture and the source of the compiled examples in
[plugin authoring](plugin-authoring.md). It is enabled but does nothing until you set one of its `demo_*` settings in
Settings › Tools › Sample (or its settings file): a demonstration activity; toolbar, menu and status-bar items with a
Sample tool window and an **About Sample** tab that comes back when Jasper restores its windows; a terminal greeting; an
echo session; and a "Greetings" palette scope.

- **Capabilities** `terminal.observe`, `terminal.inject`, `terminal.open`, `session.provide`, `palette.contribute`.

## Where plugins live

Bundled plugins are inside the application: `Jasper.app/Contents/app/plugins/<id>/` on macOS, `lib/app/plugins/<id>/`
in the install folder on Linux and `app\plugins\<id>\` in the install folder on Windows. Everything else is in Jasper
home, the folder that holds `config.toml` (`~/.config/jasper` on macOS, `$XDG_CONFIG_HOME/jasper` or `~/.config/jasper`
on Linux, `%APPDATA%\jasper` on Windows; `JASPER_HOME` moves it, see [configuration](configuration.md)):

| Path in Jasper home | What it holds |
|---|---|
| `plugins/<id>/jars/` | An installed plugin's jar and the libraries it bundles |
| `plugins/<id>/<id>.toml` | The plugin's [settings file](#plugin-settings) |
| `plugins/<id>/data/` | The plugin's private data |
| `plugins/.pending/<id>/` | An install waiting for the next launch |
| `plugins/<name>.zip` | A zip you dropped in; installed at the next launch |
| `plugins/<name>.zip.rejected` | A dropped zip that is not a usable plugin |
| `plugins/.staging-*`, `plugins/.trash-*` | A zip being inspected, and a removed plugin's folder being deleted; Jasper deletes leftovers at launch, a staging folder once it is a day old |
| `plugins.toml` | Which plugins are enabled, the capabilities you allowed, and pending removals |
| `plugins.lock` | Held while a Jasper process changes `plugins.toml` |
| `presentation-attempt.toml` | Whether the last start with a look finished ([looks](#looks-and-plugin-themes)) |

At each launch Jasper creates `plugins/<id>/` with `<id>.toml` and `data/` for every plugin it finds, bundled ones
included, so every plugin's settings and data are in the same place. A folder there with no jars holds only settings and
data. A folder that holds jars must be named after the plugin's id; otherwise Jasper reports "directory X holds plugin Y;
rename it to the plugin id" and skips it.

### plugins.toml

Jasper changes `plugins.toml` only as a locked read-modify-write. Each plugin with saved state has one table:

```toml
version = 1

[plugins."dev.example.tool"]
enabled = true
consented = ["palette.contribute", "terminal.observe"]
remove = false
```

| Key | Meaning |
|---|---|
| `enabled` | Whether the plugin may load. Without a table a plugin is enabled, except a bundled plugin that is off by default (Enterprise) |
| `consented` | The capabilities you allowed for an installed plugin |
| `remove` | Delete the plugin's folder at the next launch |
| `opt_in` | Written as `opt_in = true` when you turn on a bundled plugin that is off by default; disabling it clears this |

An installed plugin has been reviewed when it has a table without `opt_in`. A `plugins.toml` that cannot be read
counts as empty, so installed plugins wait for a review again rather than load unreviewed.

## The Plugins page

**File › Manage Plugins…** (also **Manage Plugins…** in the command palette) opens Settings at the Plugins page. The
action's id is `plugins.manage`; it has no default shortcut. The page reads the disk again each time it is shown.

The restart banner ([restarting](#restarting)), when there is one, runs across the top. Above the list are a **Search
plugins** field, which matches a plugin's name, vendor, description or id, and a More button (**Plugin options**) that
holds **Install from Zip…**. The selected plugin's details are on the right. A message line at the bottom reports what
an operation did, or why it failed.

### The list

Plugins are grouped, each group sorted by name:

| Heading | Plugins |
|---|---|
| Development | The plugin loaded with `--plugin-dir` |
| User-installed, *N* of *M* enabled | Plugins you installed |
| Bundled, *N* of *M* enabled | Plugins that ship with Jasper |

Each row shows the plugin's icon, its name, its version and vendor, a badge when it needs attention, and an
**Enable *name*** switch. The switch is on when the plugin is enabled and reviewed. Turning it on for a plugin that needs
a review opens the review sheet. When the switch cannot act, for example while a removal is pending, its tooltip says
why. Under the Enterprise look each row leads with a checkbox instead and shows no icon.

The badge is the first that applies:

| Badge | Meaning |
|---|---|
| Remove at restart | You removed it; Keep undoes that until the next launch |
| Install at restart | An install or update is waiting in `plugins/.pending/` |
| Restart to apply | The next launch will load it differently, for example after you enabled or disabled it |
| Needs review | An installed plugin you have not reviewed, or whose new version declares a capability you have not allowed |
| Failed | It failed to load or to start at this launch |
| Skipped | Jasper did not start it; Details says why |

Right-click a row, or press Shift+F10 or the menu key on the selected row, for its menu:

| Item | What it does |
|---|---|
| Open Settings | Goes to the plugin's page in Settings › Tools when it is running and has one; otherwise opens its settings file in the default editor, creating the file first |
| Open Plugin Folder | Shows `plugins/<id>/` in Finder or the file manager, creating it first |
| Open Data Folder | Shows `plugins/<id>/data/` the same way |
| Remove… | For an installed plugin: deletes it at the next launch, after a confirmation |
| Keep | For a plugin marked for removal: withdraws the removal |
| Discard Install | For a waiting install: deletes it now |

The list is one focus stop: Up and Down move the selection, Space operates the selected row's switch, and Shift+F10 or
the menu key opens its menu.

### Details

The details show the plugin's name, its vendor, **Version** and status, and one button: **Review…** for a plugin that
needs a review, otherwise **Disable** or **Enable** (no button when the plugin cannot be switched). The status is the
badge when there is one, **Enabled** for a running plugin, and otherwise the state below. Two tabs follow:

- **Overview**: the plugin's description, or "No description.".
- **Details**: *Status* (the state and its reason, what changes at the next launch, the number of errors since Jasper
  started, where the plugin comes from, and its id), *Capabilities* (each marked "— not reviewed" until you allow it),
  *Requires* (each plugin it needs, its version range, and "(optional)"), *Exports*, and *Locations* (its folder,
  settings file and data folder). This tab also shows **Plugin homepage ↗** when the plugin names an http or https
  homepage, and the **More actions** menu, which holds the same items as the row's menu. Under the Enterprise look
  these actions are a **⋯** button (**Plugin actions**) beside the main button.

| State | Meaning |
|---|---|
| Active | Running |
| Disabled | Off: "off by default", "disabled by the user", "marked for removal", "safe mode" or "safe mode uses the standard look" |
| Needs review | Waiting for your review: "not reviewed yet" or "new capabilities: …" |
| Skipped | Not started; the reason names the cause, such as "needs SDK >=0.12.0, <0.13.0; this is SDK 0.21.0", "requires dev.example.base, which is not available", "requires dev.example.base >=2.0.0; found 1.4.0", "requires dev.example.base, which failed to start", "dependency cycle" or "the Enterprise look is active" |
| Failed | Its classes could not be loaded, or its start failed |
| Not loaded yet | Not present when this launch began, such as a plugin installed since |

What changes at the next launch reads "Will load after restart", "Will not load after restart" (with the reason),
"Version *X* loads after restart", "Version *X* will be installed at restart" or "Will be removed at restart".

## Installing a plugin

### From a zip

Choose **Install from Zip…** from the **Plugin options** menu and pick the plugin's zip. Jasper unpacks it into a staging folder and
checks it before it asks you anything:

- The zip holds the plugin's jars at its root or all in one folder, nothing deeper. Only `.jar` entries are unpacked;
  other files, names starting with `.` and anything under `__MACOSX` are ignored.
- Jar names use only letters, digits, spaces and `.`, `_`, `+`, `-`; no name may appear twice.
- At most 4096 entries, 256 jars and 512 MiB once unpacked.
- Exactly one jar holds a valid `plugin.toml` at its root, and the plugin's SDK range includes this Jasper's SDK,
  0.21.0.

A zip that fails is refused on the message line with the reason, for example "The zip holds no jar files", "The zip
must hold the plugin's jars at its root or in one folder", "Not a Jasper plugin: no plugin.toml in any jar" or "Tool
1.0.0 needs SDK >=0.12.0, <0.13.0; this Jasper has SDK 0.21.0", and nothing is installed.

A zip that passes opens the **Install *name*** sheet: the plugin's name and version, "From *vendor*", what it
[asks to do](#capabilities) and the warning that a plugin is not sandboxed. **Install** stages the plugin in
`plugins/.pending/<id>/` and records your consent to exactly the capabilities shown; **Cancel**, or closing the sheet,
deletes the staged copy. The message line then reads "*name* *version* will be installed when Jasper restarts.", the row
shows **Install at restart** and the restart banner appears. **Discard Install** withdraws the install until then.

At the next launch, before any plugin loads, the jars replace those in `plugins/<id>/jars/`. The plugin's settings file
and `data/` are kept.

### Updating

Install the new version's zip the same way. When `plugins/<id>/` or a waiting install of that id already exists, the
sheet adds "This replaces the installed version when Jasper restarts." The sheet lists every capability the new version
declares, and **Install** allows them.

### Dropping a zip into the plugins folder

A zip placed in `plugins/` in Jasper home is installed when Jasper next starts, with the same checks but without a
sheet, and the zip is deleted. A new plugin is then present but waits as **Needs review** ("not reviewed yet") until you
review it on the Plugins page. An update of a plugin you reviewed keeps your consent when it declares no capability you
have not allowed; otherwise it waits as **Needs review** ("new capabilities: …") and does not load until you review it.
A zip that is not a usable plugin is renamed `<name>.zip.rejected`; Jasper's log ([diagnostics](diagnostics.md)) gives
the reason: "Not a usable plugin zip: *name*: *why*".

### The same plugin in two places

Jasper looks for plugins in three places: the application's bundled plugins, `plugins/` in Jasper home, and the
`--plugin-dir` folder. When it finds the same id in more than one, the higher version is the copy that counts; on equal
versions the `--plugin-dir` copy beats an installed one, which beats the bundled one. The other copy is not loaded and
reports nothing. Jasper picks the copy first and checks its SDK range and review after, so a newer copy that is
incompatible or not reviewed keeps the older copy from loading too.

Installing a bundled plugin's id with the same or a higher version therefore replaces the bundled copy. Both copies
share `plugins/<id>/`, including its settings file and data.

## Reviewing capabilities

An installed plugin loads only after you have allowed every capability it declares. Bundled plugins and the
`--plugin-dir` plugin need no review.

A plugin's switch, or **Review…** in its details, opens the **Review *name*** sheet: its name and version, "From
*vendor*", "It asks to:" followed by its capabilities (or "It asks for no access to your terminals."), and this warning:

> A plugin is unrestricted code running inside Jasper, with everything your account can reach. The list above is what
> the plugin says it does; it is not a sandbox. Allow only plugins you trust.

**Allow and Enable** (Enter) records exactly the capabilities shown, enables the plugin and cancels a pending removal;
it loads at the next launch. **Cancel** (Escape), or closing the sheet, changes nothing. If the plugin on disk declares
something else by the time you allow it, Jasper refuses with "*name* changed on disk since you reviewed it. Review it
again."

### Capabilities

A capability names something a plugin does that you cannot see in the window, or a role only one plugin at a time can
take. Adding actions, menu items, tool windows, status-bar items and notifications needs none. Jasper refuses a gated
call from a plugin that did not declare its capability.

Jasper's log ([diagnostics](diagnostics.md)) records capability use under the logger `dev.jasper.app.plugins.audit`, as
"Plugin *id* used *capability*: …", with amounts and never content: each write or paste into a pane (in bytes), each
read of a selection or the clipboard (in characters), each tab or split opened (with the file name of the program it
runs, if any), the shell-variable and editor registrations, and the first palette use, file-chooser registration,
terminal-event subscription and list of other tabs' titles for a plugin's Move Pane to Tab menu.

| Capability | The sheet says | Notes |
|---|---|---|
| `terminal.observe` | See your terminals: titles, folders, the commands you run and how they end | Also every terminal event a plugin can subscribe to. Without it a plugin's Move Pane to Tab menu still works, listing a tab as "Tab 2" instead of by title unless the tab shows one of the plugin's own items |
| `terminal.selection` | Read the text you select in a terminal | |
| `terminal.inject` | Type into your terminals | Typing and pasting into a pane |
| `terminal.open` | Open terminal tabs, splits and windows, and run programs in them | |
| `session.provide` | Run its own terminal sessions, such as remote connections | |
| `palette.contribute` | Add scopes to the command palette and open it | |
| `files.source` | Add places, such as saved hosts, to the file chooser | |
| `network.listen` | Accept connections from other programs or computers, such as serving files | No call is gated by it; it tells you the plugin listens |
| `clipboard.read` | Read text you copied to the clipboard | Writing to the clipboard needs none |
| `terminal.environment` | Set environment variables in new terminal shells | |
| `editor.provide` | Open the files Jasper opens for editing | One plugin at a time |
| `presentation.provide` | Change Jasper's window chrome and controls after a restart | Declared by a look; one look at a time |

Each description is followed on the sheet by the capability's name in brackets. A capability this Jasper does not know
is shown by name.

Capabilities cover the calls a plugin makes to Jasper. They do not limit what the plugin's own code does with the Java
runtime: it can read and write your files, open network connections and start programs like any program you run. Each
plugin loads in its own class loader, which sees the SDK, the Java runtime, the plugin's own jars and the packages its
required plugins export, but none of Jasper's own classes or libraries. That keeps plugins on the public API; it is not
a security boundary either.

## Enabling, disabling and removing

**Enable** and **Disable** (the row's switch or the button in the details) change `plugins.toml`; the plugin starts or
stops loading at the next launch, and its row shows **Restart to apply** until then. For an installed plugin you have
not reviewed, the switch opens the review instead. Turning on a bundled plugin that is off by default, such as
Enterprise, is remembered as an opt-in. Turning on a look turns off every other enabled look, since one look can be
active at a time; the restart banner names them.

**Remove…** is offered for installed plugins only. Its sheet asks "Remove *name* *version*?" and explains: "Its jars,
settings and data in *folder* are deleted the next time Jasper starts. Keep undoes this until then." **Remove** marks it;
the row shows **Remove at restart** and its menu offers **Keep**. At the next launch, before any plugin loads, Jasper
deletes the whole `plugins/<id>/` folder. A folder it cannot delete, for example because another program has one of its
files open, stays marked and the plugin does not load.

Bundled plugins can be disabled but not removed; their details say "Bundled with Jasper; disable it here, or remove it
from the application image." Removing an installed copy of a bundled plugin's id deletes `plugins/<id>/`, which holds
the settings file and data the bundled copy uses too.

## Restarting

The banner "Restart Jasper to apply your changes." with **Restart Now** appears whenever the next launch would load a
different set of plugins, or different versions, than this process did, or an install is waiting. When you turned on a
look, it adds "*Name* was turned off: one look can be active at a time."

**Restart Now** quits Jasper and starts it again with the same options, except `--background` and `--no-window`; the
new Jasper waits for this one to exit. When Jasper cannot tell how it was started, the banner says "Jasper could not
work out how it was started. Quit and reopen it to apply your changes." with **Quit Jasper**.

## Safe mode

Start Jasper with `jasper --safe-mode` (see [the command line](command-line.md)) when a plugin keeps it from starting or
working. In safe mode:

- Installed plugins do not load (their state reads "safe mode").
- No look loads, bundled ones included ("safe mode uses the standard look"), so Jasper uses its default look. Safe mode
  shows no splash screen.
- Bundled plugins without a look, and a `--plugin-dir` plugin, still load.
- It applies only when Jasper starts: a launch while Jasper runs hands off, and the running Jasper says "Jasper is
  already running: quit it first to start with --safe-mode". A Jasper in safe mode is never resident.

The Plugins page shows "Safe mode: installed plugins are not loaded." with **Restart Normally**. You can still enable,
disable, review and remove plugins; they apply at the next launch, but in safe mode the page shows no **Restart Now**
banner or **Restart to apply** badge for them. An installed plugin other than a look that safe mode keeps from running
has a page in Settings › Tools that says "Start *name* to edit its settings.", gives the reason, and offers **Open in
Editor** for its settings file.

**Restart Normally** starts Jasper again without `--safe-mode`; the new Jasper waits for this one to exit.

## When a plugin fails

Jasper contains a plugin's failures: an exception thrown from plugin code is caught, logged against the plugin, counted
(Details shows "*N* errors since Jasper started; see the log") and recorded as an incident, and Jasper keeps running.
When a plugin fails to start, everything it registered is removed, and a plugin that requires it (not optionally) is
skipped with "requires *id*, which failed to start".

Problems are posted in the **Plugin problems** notification group ([notifications](notifications.md)):

| Notification | When | Actions |
|---|---|---|
| A plugin could not be read | A folder's `plugin.toml` is missing, too large or invalid, or the folder is misnamed | Open Plugins |
| *Name* could not be loaded | Its classes could not be loaded at launch | Open Plugins, and Show more… when an exception lies behind it |
| *Name* was not started | It was skipped at launch; the notification gives the reason | Open Plugins |
| *Name* failed to start, *Name* failed to stop, *Name* failed in … | Its code threw while Jasper called it | Disable Plugin, Show more… |
| Jasper started with its standard look | The last start with a look did not finish ([looks](#looks-and-plugin-themes)) | Try Again |
| The *name* look is missing some of its files | A look's font could not be used, or a theme its kit file sets as a default did not load ([looks](looks.md#other-problems)) | none |
| A plugin command was not added | Two plugins declare the same `jasper` command ([plugin commands](#plugin-commands)) | none |
| Could not open a plugin's settings file | **Open in Editor** on a plugin's page in Settings › Tools failed | none |

**Open Plugins** opens Settings › Plugins. **Show more…** opens the Errors tab at that incident. **Disable Plugin**
disables the plugin from the next launch and confirms with "*Name* will be disabled when Jasper restarts" (or "Could not
disable *name*" with the reason). A plugin that waits for a review, or that you disabled, posts nothing. Jasper's logs
are described in [diagnostics](diagnostics.md).

## Plugin settings

Each plugin keeps its settings in its own file, `plugins/<id>/<id>.toml` in Jasper home, not in `config.toml`. Jasper
creates the file at launch when it is missing, from the example the plugin ships (a `settings.toml` in its jar) or with
a one-line header, "# Settings for *name* *version* (*id*). Jasper reads this file live."; it never overwrites an
existing file.

Jasper reads the file live: it checks once a second for changes, and **Reload Config** reads every plugin's file again.
A file that is not valid TOML or is larger than 1 MiB keeps its last good values and is reported as "*file* could not be
read: *why*; the last good settings stay in force." A value the plugin does not accept is reported with its line and
the setting's default is used; a value of the wrong type ("Expected …; configuration was not applied.") rejects the
file, which keeps its last good settings. A key the plugin does not declare is reported as "Unknown setting or table;
ignored." These reports appear with Jasper's other configuration problems ([configuration](configuration.md)).

A plugin that declares settings has a page under Settings › Tools, named after it. Changing a value there writes the
file and keeps its comments. While a value of the wrong type keeps the file rejected, the page says "This plugin's
settings file has errors; fix them before changing its settings here." and Settings does not apply changes made on it.
A plugin that is not running although you meant it to (waiting for a review, skipped, failed, or, unless it is a look,
held back by safe mode) has a page that says "Start *name* to edit its settings.", gives the reason ("Not running: …")
and offers **Open in Editor**.

## Looks and plugin themes

A look is the presentation of Jasper's chrome and controls: title bar, menus, tool window stripes, tabs, status bar,
palette, dialogs, controls, fonts and metrics. A plugin provides one with the `presentation.provide` capability and a
kit file; colours always come from the theme. Enterprise is the bundled look; [the Enterprise look](enterprise.md)
describes it and [looks](looks.md) the kit file.

- One look is active at a time. Turning one on in Settings › Plugins turns the others off, and it applies after a
  restart.
- When several enabled looks could load, a `--plugin-dir` look wins, then a bundled one, then an installed one, then the
  lowest id. The others are skipped with "the *name* look is active".
- A look whose kit file is invalid is skipped, and reported once for each version of the look's files: its kit file,
  theme files, fonts and branding.
- Jasper records each start with a look in `presentation-attempt.toml` and marks it finished at the first window's first
  paint. When the last start with a look did not finish, the next one uses the default look and posts "Jasper started
  with its standard look" with **Try Again**, which forgets the record and restarts.
- Safe mode always uses the default look.

Any plugin can also ship themes. They are listed in Settings › Appearance & Behavior › Appearance after the built-in themes,
captioned "From the *name* plugin", and are available only while that plugin can load; switching the plugin on or off
takes a restart. A theme setting that names a theme of a plugin that is off uses the default theme, without a warning. See
[creating themes](creating-themes.md).

## Plugin commands

A plugin can add commands to the `jasper` command, such as the Editor's `jasper editor`. `jasper --help` lists the
commands of the plugins that were running at the last launch. A command whose plugin is off, waiting for a review or
failed answers "jasper: *command* is unavailable (the *name* plugin is off)". When two plugins declare the same
command, a bundled plugin keeps it, otherwise the lowest plugin id, and the other is reported as "A plugin command was
not added". See [the command line](command-line.md).

## Developing a plugin

`jasper --plugin-dir <dir>` starts Jasper with one more plugin, from a folder that holds its jars directly or in
`jars/`; the folder's name is free. The plugin is listed under **Development** ("Loaded from --plugin-dir."), needs no
review, and keeps its settings and data in `plugins/<id>/` in Jasper home like any other. Like `--safe-mode`, the
option applies only when Jasper starts, and a Jasper started with it is never resident. A bundled or installed copy
with a higher version of the same id is loaded instead. An id you disabled in Settings › Plugins stays off, from
`--plugin-dir` too ("disabled by the user"); enable it there first.

Plugins for this Jasper (SDK 0.21.0) declare a range such as `sdk = ">=0.21.0, <0.22"` in `plugin.toml`, raising the
floor to the SDK version that added the newest API they use ([the manifest](plugin-authoring.md#the-manifest));
[building the in-repo plugins](plugin-authoring.md#building-the-in-repo-plugins) and
[running a plugin in Jasper](plugin-authoring.md#running-a-plugin-in-jasper) give the build tasks and the source
launch's own Jasper home.

Start with [plugin authoring](plugin-authoring.md), the [SDK README](../jasper-sdk/README.md) and the
[SDK reference](sdk-reference.html), from the entry point
[`Plugin`](sdk-reference.html#dev.jasper.sdk.plugin.Plugin) and its
[`PluginContext`](sdk-reference.html#dev.jasper.sdk.plugin.PluginContext). [Plugin APIs](plugin-apis.md) describes the
packages the bundled plugins export, the [test kit](../jasper-sdk-testkit/README.md) runs a plugin against a fake host,
and the [external plugin example](examples/external-plugin/README.md) builds a plugin outside the Jasper repository.
