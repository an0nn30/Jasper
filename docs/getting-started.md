# Getting started

This guide installs Jasper, explains what you see the first time it opens, and walks through the features most
people use first: tabs and splits, the command palette, Settings, tool windows, SSH hosts and the Credential Vault.

## Install

Packaged builds are published as prereleases on Jasper's
[GitHub releases](https://github.com/an0nn30/Jasper/releases). Each release lists a SHA-256 checksum for every file in
`SHA256SUMS`. Every package bundles the JetBrains Runtime Jasper needs, so no separate Java installation is required.
To build a package yourself, see [building from source](../README.md#building-from-source).

### macOS

Download `Jasper-<version>-macos-aarch64.dmg` (Apple Silicon). Releases carry no Intel build; an Intel Mac can build
its own DMG from source.

1. Open the DMG and drag `Jasper.app` to Applications. Copy it with Finder or `ditto`: a copy that resets file
   modification times, such as `cp -R`, still runs but loses Jasper's start-up cache and starts more slowly.
2. Open Jasper from Applications. The app has only an ad-hoc signature and is not notarised, so macOS may ask you to
   confirm opening software from an unidentified developer.

Connecting to an SSH host on your local network needs macOS's Local Network permission. If a connection fails with
"No route to host", allow Jasper under System Settings › Privacy & Security › Local Network.

Before you replace `Jasper.app` with a newer version, choose **Quit Jasper Completely** from Jasper's menu bar item:
Cmd+Q only closes the windows (see [running in the background](#running-in-the-background)).

### Windows

Two packages exist for each architecture, x64 and ARM64 (`aarch64`). On Windows on Arm, use the ARM64 package: an x64
Jasper runs there entirely under emulation, which makes every launch and new window markedly slower.

- **Setup wizard**, `Jasper-<version>-windows-<x64|aarch64>-setup.exe`: installs Jasper for your user only (no
  administrator rights), lets you choose the folder and the Start menu and desktop shortcuts, and registers Jasper
  with Windows Installed apps. The installer is not signed, so Windows may warn about an unknown publisher. To
  upgrade, quit Jasper, including its background process (the notification-area icon › **Quit Jasper**), and run the
  newer setup: it replaces the installed program files.
- **Portable ZIP**, `Jasper-<version>-windows-<x64|aarch64>.zip`: extract the complete `Jasper` folder and run
  `Jasper.exe`. Keep the folder's files together.

The setup wizard also installs a Cygwin environment for [Cygwin Bash](terminal.md#cygwin-bash) in
`%LOCALAPPDATA%\Jasper\cygwin`, from files it carries, so no download is needed. The first installation needs about
2.7 GB free on that drive and leaves about 1.3 GB there; when it cannot finish, setup fails, and running it again
retries. A newer setup keeps the environment as it is. For the portable ZIP, run `provision-cygwin.cmd` in the `Jasper`
folder once to install the same environment. Each release also carries that environment's source code as
`Jasper-<version>-cygwin-sources-<amd64|aarch64>.zip`, which installing does not need.

### Linux

Download the package for your distribution and architecture and install it with your package manager:

```bash
sudo apt install ./Jasper-<version>-linux-amd64.deb                         # Debian, Ubuntu (arm64 on ARM)
sudo dnf install ./Jasper-<version>-linux-x86_64.rpm                        # Fedora (aarch64 on ARM)
sudo zypper install --allow-unsigned-rpm ./Jasper-<version>-linux-x86_64.rpm  # openSUSE
```

Both packages install Jasper to `/opt/jasper-workstation`, with the launcher at `/opt/jasper-workstation/bin/Jasper` and an entry in the
desktop menu. The package is named `jasper-workstation`. The packages are unsigned.

## First launch

Jasper opens a window with one tab running your shell: your login shell on macOS and Linux, PowerShell on
Windows (Settings › Terminal › Shell chooses another, such as [Cygwin Bash](terminal.md#cygwin-bash)). The first window
is sized for 100 columns by 35 rows of the terminal font, bundled Cousine Nerd Font Mono at 12 points; after that,
Jasper [restores its windows, tabs and panes](workspace.md#saving-and-restoring-the-windows) as you left them at each
start. On macOS Jasper follows the system's dark or light appearance; on Linux and Windows it shows Dark until you
choose Light. The default theme is Jasper Meadow in both Dark and Light.

While Jasper starts, a splash shows a meadow with the [Desk Buddy](notifications.md#desk-buddy), Jasper's version,
each start-up step and a progress bar, until the window has painted. A look with its own [branding](looks.md#branding)
shows its own splash instead. With **Enable animations** (`ui.animations.enabled`) off, the Desk Buddy stands still. A
start with `--safe-mode`, `--background` or `--no-window` shows no splash.

Under the default look the window has:

- a **header** with a search field in the middle, which shows where the current pane is (`user@~/path` locally,
  `host:path` over SSH) and opens the [command palette](command-palette.md) when you click it, and a toolbar: New
  tab, New window, Split, Maximize pane, the plugins' buttons (the Editor's New Editor Window, Remote's
  Sessions menu), Find, Settings and the window's ⋮ menu. On macOS the header shares the title bar with the window
  controls and the menus are in the menu bar; on Linux and Windows the menus sit in Jasper's own title bar;
- the **terminal**, with a row of tabs above it once the window has more than one tab;
- **rails** on the left and right edges with a button for each tool window;
- a **status bar** with the shell's name, a dot showing whether shell integration is active (● active, ○ not
  detected), the current folder, the grid size, and the state of `config.toml`, which reads "Built-in defaults" until
  that file exists.

The [workspace guide](workspace.md) describes every part of the window, and the [terminal guide](terminal.md) the
terminal panes.

### Jasper home

Jasper keeps its settings and data in one folder, its home:

| Platform | Jasper home |
| --- | --- |
| macOS | `~/.config/jasper` |
| Linux | `$XDG_CONFIG_HOME/jasper` when that variable holds an absolute path, else `~/.config/jasper` |
| Windows | `%APPDATA%\jasper` |

Setting the `JASPER_HOME` environment variable moves it; a Jasper in another home runs beside the default home's and
never stays in the background ([one Jasper per home](command-line.md#one-jasper-per-home)). The home holds
`config.toml` (written the first time you save a change in Settings), `logs/`, `themes/` for imported themes, `plugins/`
with each plugin's settings file and data, `plugins.toml` with each plugin's enabled state and your consent,
`ui-state.toml` with the tool window layout, and `workspace.toml` with the windows, tabs and panes Jasper restores. The
home is separate from the installed application, so removing or upgrading Jasper keeps your settings and data.
[Configuration](configuration.md) lists every setting and file.

### Running in the background

The **Run in background** setting (Settings › Appearance & Behavior › System Settings, `system.run_in_background`)
keeps Jasper running with no windows open, so a new window opens at once. It takes effect at the next start.

| | macOS | Windows | Linux |
| --- | --- | --- | --- |
| Default | On | On | Off |
| Closing the last window | Jasper keeps running in its menu bar item: **New Window**, **Settings…**, **Quit Jasper Completely** | Jasper keeps running in its notification-area icon: **Open Jasper**, **Quit Jasper** | Jasper quits. With the setting on, it keeps running with no icon; launch Jasper again for a window |
| Quit | Cmd+Q closes every window and keeps Jasper in the menu bar; **Quit Jasper Completely** ends it | Quit (Ctrl+Alt+Shift+Q) ends Jasper | Quit (Ctrl+Alt+Shift+Q) ends Jasper |
| At login | An installed Jasper starts in the background, with no Dock icon | An installed Jasper starts in the background | Not started |

One Jasper runs per home, with the setting on or off: launching it again brings the running Jasper's most recently active window
forward, restoring its windows first when none is open, and opens the files and folders you named. On macOS a file or
folder you open with Jasper in Finder (Open With), or drop on its Dock icon, opens the same way, and the Dock icon shows
only while one of Jasper's windows is open. Turning the setting off also removes the login item when you save.

## A first tour

### Tabs and splits

- **New Tab** (Cmd+T on macOS, Ctrl+Shift+T on Linux and Windows) opens a tab; **New Window** (Cmd+N,
  Ctrl+Shift+N) a new window. New tabs, windows and splits start in the
  current pane's folder once its shell has reported it, as shell integration does.
- **Split Right** (Cmd+D, Ctrl+Shift+D) and **Split Down** (Cmd+Shift+D, Ctrl+Alt+Shift+D) split the current pane;
  drag the divider to resize. **Focus Pane Left / Right / Up / Down** (Cmd+Option+Arrow, Ctrl+Alt+Shift+Arrow) moves
  between panes, and **Maximize Pane** (Cmd+Shift+Enter, Ctrl+Alt+Shift+Enter) shows only the current pane until you
  press it again. The **Split** submenu (Tab menu, or a terminal's right-click menu) opens an editor or a plugin's view
  beside the pane instead, and **Move Pane to New Tab** (Cmd+Option+T, Ctrl+Alt+Shift+T) gives the pane a tab of its
  own.
- **Close Pane** (Cmd+W, Ctrl+Shift+W) closes the current pane, and its tab with the last pane; **Close Tab**
  (Cmd+Option+W, Ctrl+Alt+Shift+W) closes the whole tab, and **Close Window** (Cmd+Shift+W on macOS, no default
  shortcut on Linux and Windows) the window. A window stays open with
  an empty workspace when its last tab closes. **Rename Tab** (Cmd+I, Ctrl+Shift+I) names a tab; an empty name returns to the automatic title.
- Drag a tab to reorder it, onto another window to move it there, or out of every window into a window of its
  own; middle-click it to close it. Hold Cmd+Option (Ctrl+Alt on Linux and Windows) and drag a pane to move it beside
  another pane or into a tab of its own. Right-click a pane for Copy, Paste, Find, the split and move commands and the
  plugins' commands for that pane.

On Linux and Windows, Copy and Paste are Ctrl+Shift+C and Ctrl+Shift+V, so Ctrl+C still reaches the program in the
terminal. **Find** (Cmd+F, Ctrl+Shift+F) searches the pane's text as you type; see
[the terminal](terminal.md#find).

### The command palette

**Command Palette** (Cmd+Shift+P, Ctrl+Shift+P), or a click on the header's search field, opens one search over
Jasper's commands and the plugins' scopes: saved SSH hosts, shell history, snippets, project files, tunnels and file
servers. With an empty query it lists what you used recently. Each row offers up to three actions, on Enter,
Cmd+Enter (Ctrl+Enter on Linux and Windows) and Shift+Enter; the palette's hint bar names them.

Type `>` first to search only commands, or a scope's name and `>` (for example `ssh >`) to search one scope. Several
scopes also have their own shortcut, such as **Search Shell History** (Cmd+R, Ctrl+Shift+R). Under the Enterprise look
the palette is the Run window, which its shortcut, **▶ Run…** in the status bar and the toolbar's **Search
Everywhere** button open. See [the command palette](command-palette.md).

### Settings

**Settings** (Cmd+, on macOS, Ctrl+Shift+, on Linux and Windows; also the toolbar's Settings button and File ›
Settings) opens the Settings dialog. Its pages are:

- **Appearance & Behavior**: Appearance (Dark, Light or Match System, the theme for each, the terminal scheme, the
  interface font), Window, Notifications, Command Palette, Desk Buddy and System Settings (Run in background, memory,
  the `jasper` command, the configuration file);
- **Keymap**: every action and its shortcut, where you can record a new one;
- **Plugins**: the installed and bundled plugins;
- **Terminal**: scrollback, bell and exit behaviour, then Font & Colors, Cursor and Shell;
- **Tools**: a page for each plugin that has settings.

Type in **Search settings** to find a setting by name. Changes wait until you click **Apply** or **OK**, which writes
them to `config.toml` (plugin pages write each plugin's own settings file). Most apply at once; a row notes when a
change applies only to new panes, new windows or after a restart. **Help › Keyboard Shortcuts…** opens the Keymap
page. See [configuration](configuration.md) and [keyboard shortcuts](keyboard-shortcuts.md).

### Tool windows

Tool windows are panels beside and below the terminal in each window: Sessions, SFTP, Tunnels and File Servers on
the left, and Notifications on the right; Remote's file transfers show at the foot of the SFTP tool window. Click a rail
button to show or hide one; View › Tool Windows lists them too, and some have shortcuts, such as **Toggle SSH hosts**
(Cmd+Option+1, Alt+1) and **Toggle SFTP** (Cmd+Option+2, Alt+2). Drag a rail button or a tool window's header to
another edge, or right-click the button and choose **Move to**. Each side, and the bottom, has two slots, and each slot
shows one tool window at a time; Jasper remembers the layout. Under the Enterprise look the rails are labelled stripes,
each label starting with its tool window's icon, and the tool windows are in the Window menu. See
[the workspace](workspace.md#tool-windows-and-the-dock).

### Connect to a host

The Remote plugin keeps your SSH hosts.

1. Choose Session › **New Host...**, or **New session** (+) in the Sessions tool window.
2. Enter a **Name**, the **Host** and **Port**, and the **Username**; choose a **Jump host** to connect through
   another saved host.
3. Under **Authentication**, choose **Vault Credential** and pick a login or stored keys from the Credential Vault, or
   **SSH Agent** to use the keys your agent holds.
4. Click **Add and Connect**. The first time, Jasper shows the server's host key fingerprint: compare it with the
   server's, then choose **Trust and connect** to remember the key or **Connect once**.

The session opens in a new tab. Afterwards, **Connect to SSH Host...** (Cmd+Shift+H, Ctrl+Shift+H) lists your saved
hosts in the command palette: Enter connects in a new tab, Cmd+Enter (Ctrl+Enter) in a split. Session › **Import from
~/.ssh/config...** saves the hosts in your SSH configuration and imports their key files into the Vault. From an
SSH pane, **Open SFTP here** (Cmd+Shift+O, Ctrl+Alt+Shift+O) browses the host's files in the SFTP tool window. See
[Remote](remote.md).

### Keep credentials in the Vault

The Credential Vault keeps logins, SSH keys and secure notes in one encrypted file in Jasper's home.

1. Choose File › **Open Vault...** (Cmd+Shift+\\ on macOS, Ctrl+Alt+Shift+\\ on Linux and Windows). The first time,
   **Create Vault** asks for a master password of at least 8 characters. **Bind to this device** (marked Recommended)
   stores a device secret with the system's keychain (in a file in Jasper's home where there is none), so the vault
   opens only on this computer. Your master password cannot be recovered if you forget it.
2. In the Credential Vault manager, the **Add entry** (+) menu (**New** under the Enterprise look) adds a **Login**,
   an **SSH Key** or a **Secure Note**, or runs **Generate SSH Key...**.
3. **Search Vault Logins** (Cmd+\\, Ctrl+Shift+\\) finds a login in the command palette: Enter copies its password,
   Cmd+Enter (Ctrl+Enter) its username, and Jasper clears the copy from the clipboard after 30 seconds.

The vault locks after 15 minutes without keyboard or mouse input in Jasper (Settings › Tools › Credential Vault
changes that), or when you choose **Lock Vault** (Cmd+Shift+L, Ctrl+Alt+Shift+L). Choosing a credential in a
plugin's form, as in Remote's host form above, lets that plugin use it once. When a plugin such as Remote fetches a
credential it has no grant for, the Vault asks whether to **Allow once**, **Always allow for** that plugin (for
Remote, **Always allow for Remote**) or **Deny**. See [Credential Vault](credential-vault.md).

## Useful shortcuts

Every shortcut can be changed in Settings › Keymap. [Keyboard shortcuts](keyboard-shortcuts.md) lists them all.

## Safe mode

If a plugin you installed stops Jasper from starting, start it in safe mode. Safe mode loads no plugin you installed
and no look plugin such as Enterprise, so Jasper uses its default look; the other bundled plugins load as usual. Start
it with `jasper --safe-mode` once the [`jasper` command](command-line.md#installing-the-jasper-command) is installed
(**Install** under Settings › Appearance & Behavior › System Settings › Command line), or run the launcher with the
option:

| Platform | Command |
| --- | --- |
| macOS | `/Applications/Jasper.app/Contents/MacOS/Jasper --safe-mode` |
| Linux | `/opt/jasper-workstation/bin/Jasper --safe-mode` |
| Windows | `Jasper.exe --safe-mode`, from Jasper's installation folder or the portable folder |

`--safe-mode` applies only to a Jasper that starts, so quit Jasper completely first: a launch while Jasper runs, in
the background too, hands off to it, and the running Jasper says to quit it first. A safe-mode Jasper never stays in
the background. Settings › Plugins then shows "Safe mode: installed plugins are not loaded." Disable or remove the
plugin there, then click **Restart Normally**. See [safe mode](plugins.md#safe-mode) in the plugins
guide.

If Jasper itself does not start, its launcher tries again without its start-up cache and without your custom VM
options before it reports "Jasper could not start" ([start-up recovery](diagnostics.md#start-up-recovery)). Jasper's
log files are in `logs/` in Jasper's home; see [logs](diagnostics.md#logs).

## Uninstall

Before you remove Jasper:

- turn off **Run in background** in Settings › Appearance & Behavior › System Settings and click **OK**, which removes
  the login item (on macOS `~/Library/LaunchAgents/dev.jasper.background.plist`, on Windows the `Jasper` value under
  `HKCU\Software\Microsoft\Windows\CurrentVersion\Run`);
- if you installed the `jasper` command, click **Uninstall** under Command line on the same page;
- quit Jasper completely: **Quit Jasper Completely** in its menu bar item on macOS, **Quit Jasper** in its
  notification-area icon on Windows.

Then remove the application:

| Package | Remove it with |
| --- | --- |
| macOS app | Move `Jasper.app` from Applications to the Trash |
| Windows setup | Settings › Apps › Installed apps › Jasper › Uninstall |
| Windows portable | Delete the `Jasper` folder |
| Linux `.deb` | `sudo apt remove jasper-workstation` |
| Linux `.rpm` | `sudo dnf remove jasper-workstation` or `sudo zypper remove jasper-workstation` |

Removing the application leaves [Jasper home](#jasper-home) in place, with your settings, plugins and Vault. Delete that
folder to remove them too. On Windows it also leaves the Cygwin environment in `%LOCALAPPDATA%\Jasper\cygwin`; close
every program using it before you delete that folder. A vault bound to this device also has an item named "Jasper
Credential Vault" in the macOS keychain or the Linux secret service; on Windows its device secret is a file in Jasper's
home.

## Where to go next

- [The workspace](workspace.md): windows, tabs, panes, tool windows, menus, the toolbar and the status bar.
- [The terminal](terminal.md): selection, links, search, prompt jumps and shell integration.
- [Plugins](plugins.md): managing plugins and a guide to each bundled one, including the
  [Enterprise look](enterprise.md).
- [Themes](themes.md): choosing and importing themes and terminal schemes.
- [The command line](command-line.md): the `jasper` command and Jasper's startup options.
- [Configuration](configuration.md): every setting in `config.toml`.
- [Writing a plugin](plugin-authoring.md) and the [SDK reference](sdk-reference.html).
