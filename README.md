# Jasper

A pluggable DevOps workstation for macOS, Linux and Windows, written in Java Swing on the JetBrains Runtime.

Jasper puts the tools you use to run systems in one window: local terminals with tabs and splits, SSH sessions, SFTP
transfers and tunnels to your hosts, an encrypted credential vault, an editor for local and remote files, file servers
for moving files to other machines, and a command palette that reaches all of them. It is for engineers who spend
their day in shells and on remote hosts. Most of it is delivered as plugins on a public SDK, so the workstation can
grow with what you need: the bundled plugins use the same API as any plugin you write or install.

## What's inside

### The workstation core

- **Terminal**: truecolor rendering with ligatures and fallback fonts, scrollback, mouse reporting for full-screen
  programs, regex search, links and file paths you Cmd-click (Ctrl-click on Linux and Windows) to open, and shell
  integration for zsh, bash and fish: jumps between prompts, new tabs and splits in the same folder, and a
  notification when a long command finishes. See [the terminal](docs/terminal.md).
- **Workspace**: a main window and child windows whose tabs hold split panes, each pane a terminal, an Editor file or a
  plugin's view. Drag a tab along its strip, into another tab or window, or out into a window of its own, drag a pane
  beside another, and maximise a pane; Jasper saves its windows, tabs and panes and restores them at the next start. The
  main window has a dock of tool windows that you drag between its left, right and bottom edges. See
  [the workspace](docs/workspace.md).
- **Command palette**: one search surface for commands and the plugins' scopes: saved hosts, shell history,
  snippets, vault logins, project files and more. Under the Enterprise look it is the Run window. See
  [the command palette](docs/command-palette.md).
- **Notifications**: cards in the window, a Notifications tool window with history and an unread badge, and system
  notifications while Jasper is in the background. See [notifications](docs/notifications.md).
- **Settings**: a searchable Settings dialog over `config.toml`, a Keymap page where every action can be rebound, and
  most changes applied without a restart. See [configuration](docs/configuration.md).
- **Themes and looks**: Jasper Meadow in both the Dark and the Light appearance by default; Slate Cobalt, Catppuccin
  Latte, Frappé, Macchiato and Mocha, and Ubuntu Yaru built in; import further Jasper theme files. A *look* sets the
  chrome and controls: the default look, or a plugin's look such as Enterprise. See [themes](docs/themes.md) and
  [looks](docs/looks.md).
- **The `jasper` command**: bring Jasper forward, open files and folders in it (`jasper <path>...`), start Jasper
  with options, or run plugin commands such as `jasper editor FILE:LINE` from any shell. One Jasper runs per home:
  launching it again hands what you asked for to the running Jasper. See [the command line](docs/command-line.md).
- **Background residency**: by default on macOS and Windows, Jasper keeps running when its last window closes, so the
  next window opens at once. See [running in the background](docs/getting-started.md#running-in-the-background).
- **Desk Buddy**: an optional pixel-art companion above your windows that shows long-running commands and plugin work;
  off by default. See [Desk Buddy](docs/notifications.md#desk-buddy).

### Plugins

Everything below ships with Jasper as a bundled plugin, listed in Settings › Plugins, where each can be turned off.

| Plugin | Id | What it does | On by default |
| --- | --- | --- | --- |
| Remote | `dev.jasper.remote` | SSH terminals, an SFTP browser with persistent transfers, local, remote and SOCKS tunnels, saved hosts with groups, favourites and jump hosts, host-key trust, Vault or SSH-agent authentication, import from `~/.ssh/config`, and a Sessions tool window that also lists WSL distributions | Yes |
| Credential Vault | `dev.jasper.vault` | Logins, SSH keys and secure notes in one encrypted file, which can be bound to this device; other plugins fetch credentials under per-plugin grants | Yes |
| Editor | `dev.jasper.editor` | Local and remote text files in workspace panes (one file can show in several), with syntax highlighting, a Markdown preview, its own fonts or the terminal's, a Vim mode and the `jasper editor` command | Yes |
| File Server | `dev.jasper.fileserver` | Shares a local folder over HTTP, HTTPS, FTP, FTPS, SFTP or TFTP; servers are read-only and reachable from this computer only until you change that | Yes |
| Project Index | `dev.jasper.indexer` | Offers to index the project a terminal enters, locally or on a saved host, and finds its files from the command palette | Yes |
| Shell History | `dev.jasper.history` | Searches every shell's history from the command palette, to paste or run a command or save it as a snippet | Yes |
| Snippets | `dev.jasper.snippets` | Saved commands with fill-in placeholders, from the command palette | Yes |
| Enterprise | `dev.jasper.enterprise` | The Enterprise look: IntelliJ Classic's title bar on macOS and Windows, labelled tool-window stripes led by their icons, the Run window, classic controls and icons and IBM Plex fonts, in Jasper Meadow by default; it also ships the Enterprise theme | No |
| Sample | `dev.jasper.sample` | Exercises the plugin runtime for plugin authors; does nothing until one of its `demo_*` settings is set | Yes |

See [plugins](docs/plugins.md) for managing plugins and a guide to each one.

## Built to be extended

Every plugin in the table is built on the Jasper SDK (`dev.jasper.sdk`, Java, JDK only), the same public API a
third-party plugin compiles against. A plugin is a folder of jars, one of which carries a `plugin.toml`; Jasper loads
each plugin in its own class loader when it starts. Through the SDK and its `plugin.toml` a plugin adds actions, menus,
toolbar items, status bar items, tool windows, command palette scopes, settings pages, notifications, workspace tabs,
terminal sessions, `jasper` subcommands, places in the file chooser, themes and even a whole look, which can be data
alone, as Enterprise's is. Plugins read the look Jasper runs with through the SDK, and a look can give other plugins'
settings their defaults.

- **Capabilities and consent.** A plugin declares in `plugin.toml` what it does that you cannot see on screen, such as
  typing into terminals, reading the clipboard or accepting network connections. Jasper shows those capabilities
  before a plugin you installed first runs, and again when an update asks for more, and loads it only once you allow
  them. Consent is not a sandbox: an allowed plugin runs with everything your account can reach.
- **Plugins build on plugins.** A plugin can export an API package and publish services that other plugins look up:
  Remote and File Server take credentials from the Vault's API, Remote and Project Index open files through the
  Editor's, Project Index reaches saved hosts through Remote's, and Shell History saves snippets through Snippets'.
- **Tooling.** Each release carries the SDK and test-kit jars; the test kit's fake host runs a plugin in unit tests
  without Jasper. `jasper --plugin-dir <dir>` loads a plugin under development, and Settings › Plugins installs a
  plugin from a zip.

Start with the [SDK reference](docs/sdk-reference.html) and [writing a plugin](docs/plugin-authoring.md); the
[documentation index](docs/README.md#building-plugins) lists the rest.

## Install

Jasper's [GitHub releases](https://github.com/an0nn30/Jasper/releases) are built by the Release workflow from a
`vX.Y.Z` tag and published as prereleases, with a `SHA256SUMS` file, the SDK jars and the Windows packages' Cygwin
sources (`Jasper-<version>-cygwin-sources-<amd64|aarch64>.zip`) beside the packages:

| Platform | Package |
| --- | --- |
| macOS (Apple Silicon) | `Jasper-<version>-macos-aarch64.dmg`: drag `Jasper.app` to Applications |
| Windows x64 / ARM64 | `Jasper-<version>-windows-<x64\|aarch64>-setup.exe`, a per-user setup wizard, or `Jasper-<version>-windows-<x64\|aarch64>.zip`, a portable folder |
| Linux x64 / ARM64 | `Jasper-<version>-linux-<amd64\|arm64>.deb` or `Jasper-<version>-linux-<x86_64\|aarch64>.rpm`, package `jasper-workstation` installed to `/opt/jasper-workstation` |

Every package bundles its own Java runtime. On Windows the setup wizard also installs a Cygwin environment, offered as
the **Cygwin Bash** shell; with the portable ZIP, run `provision-cygwin.cmd` in its folder once. The packages are not
notarized or signed by a publisher, so macOS and Windows may ask you to confirm before opening them.
[Getting started](docs/getting-started.md) covers installation, first launch and uninstalling on each platform.

## Getting started

Launch Jasper and a window opens with your shell: the login shell on macOS and Linux, PowerShell on Windows. Settings
live in `config.toml` in Jasper's home folder (`~/.config/jasper` on macOS, `$XDG_CONFIG_HOME/jasper` or
`~/.config/jasper` on Linux, `%APPDATA%\jasper` on Windows). The most used default shortcuts:

| Action | macOS | Linux and Windows |
| --- | --- | --- |
| Command Palette | Cmd+Shift+P | Ctrl+Shift+P |
| New Tab | Cmd+T | Ctrl+Shift+T |
| New Terminal Window | Cmd+N | Ctrl+Shift+N |
| Split Right | Cmd+D | Ctrl+Shift+D |
| Split Down | Cmd+Shift+D | Ctrl+Alt+Shift+D |
| Focus Pane Left / Right / Up / Down | Cmd+Option+Arrow | Ctrl+Alt+Shift+Arrow |
| Maximize Pane | Cmd+Shift+Enter | Ctrl+Alt+Shift+Enter |
| Close Pane | Cmd+W | Ctrl+Shift+W |
| Close Tab | Cmd+Option+W | Ctrl+Alt+Shift+W |
| Find | Cmd+F | Ctrl+Shift+F |
| Settings | Cmd+, | Ctrl+Shift+, |
| Connect to SSH Host... | Cmd+Shift+H | Ctrl+Shift+H |
| Search Shell History | Cmd+R | Ctrl+Shift+R |

Read on in [getting started](docs/getting-started.md), [the workspace](docs/workspace.md),
[the terminal](docs/terminal.md), [keyboard shortcuts](docs/keyboard-shortcuts.md) and
[configuration](docs/configuration.md).

## Documentation

The [documentation index](docs/README.md) lists every guide. The main ones:

- **Using Jasper**: [getting started](docs/getting-started.md), [workspace](docs/workspace.md),
  [terminal](docs/terminal.md), [command palette](docs/command-palette.md), [notifications](docs/notifications.md),
  [keyboard shortcuts](docs/keyboard-shortcuts.md), [configuration](docs/configuration.md),
  [command line](docs/command-line.md), [diagnostics](docs/diagnostics.md).
- **Plugins**: [managing plugins](docs/plugins.md), [Remote](docs/remote.md),
  [Credential Vault](docs/credential-vault.md), [Editor](docs/editor.md), [File Server](docs/file-server.md),
  [Project Index](docs/project-index.md), [Shell History and Snippets](docs/history-and-snippets.md),
  [the Enterprise look](docs/enterprise.md).
- **Theming**: [themes](docs/themes.md), [creating themes](docs/creating-themes.md), [looks](docs/looks.md).
- **Building plugins**: [SDK reference](docs/sdk-reference.html), [writing a plugin](docs/plugin-authoring.md),
  [plugin UI](docs/plugin-ui.md), [plugin APIs](docs/plugin-apis.md), [SDK architecture](docs/sdk-architecture.md),
  [external plugin example](docs/examples/external-plugin/README.md).
- **Building from source and contributing**: [packaging](docs/packaging.md), the module guides and architecture notes
  in the [documentation index](docs/README.md#building-jasper-and-contributing), [AGENTS.md](AGENTS.md) for coding
  agents, and [STATUS](docs/STATUS.md) for open work.

## Building from source

### Requirements

- The **JetBrains Runtime SDK 25** (JBR SDK) with JCEF (`jbrsdk_jcef-25.0.4.1`) for the host's architecture, including
  `jpackage`; a JRE, the plain JBR or another vendor's JDK is not enough. Gradle's toolchain requires vendor JetBrains
  and usually finds the SDK itself, but cannot tell the plain and JCEF builds apart: keep only the JCEF build of JBR 25
  installed and run Gradle on it. If Gradle does not find the SDK, set `JAVA_HOME` to it and put its `bin` directory on
  `PATH`, or set `org.gradle.java.installations.paths` in your Gradle properties.
- **Python 3.11–3.13** on the `PATH` as `python3` or `python` for the theme converter's tests (or name an
  interpreter with `-Pjasper.python=<path>`). The `python3` in Xcode's command line tools (3.9) is too old. Under
  Python 3.14 `checkThemeTool`'s `test_deeply_nested_input_is_a_message` fails; use 3.11 to 3.13.
- A **C/C++ compiler**, because `check` builds and tests Jasper's own launcher and the native `jasper` command: the Xcode
  command line tools on macOS, `gcc` and `g++` on Linux, or Visual Studio or its Build Tools with the
  "Desktop development with C++" workload on Windows.
- **Linux native dependencies**: `pkg-config` and GLib/GIO development headers for the desktop helpers.
  The checks also need GTK 3, GTK 4.10+ and libadwaita development headers, `readelf` (binutils), D-Bus,
  Xvfb, Xauth, `unzip` and a decimal-comma locale such as `de_DE.UTF-8`. The launcher links the static C++
  runtime (`libstdc++-static` on Fedora).
  Qt/KDE helpers also require CMake 3.21+, Qt 5.15 and Qt 6.5+ Widgets, KF5 5.92+ Config/ConfigWidgets,
  and KF6 6.0+ Config/ColorScheme development packages. Source builds, including `:jasper-app:run`, compile
  both Qt/KDE generations on every Linux desktop. Install the build/check dependencies and fonts with:

  ```bash
  # Debian / Ubuntu with KF6 packages available (use Python 3.11–3.13 for the checks)
  sudo apt-get update
  sudo apt-get install build-essential cmake extra-cmake-modules \
    qtbase5-dev qt6-base-dev libkf5config-dev libkf5configwidgets-dev \
    libkf6config-dev libkf6colorscheme-dev pkg-config libglib2.0-dev \
    libgtk-3-dev libgtk-4-dev libadwaita-1-dev binutils dbus xvfb xauth \
    unzip locales python3 fonts-dejavu-core fonts-noto-cjk
  sudo localedef -i de_DE -f UTF-8 de_DE.UTF-8

  # Fedora (select Python 3.13 explicitly when the system Python is newer)
  sudo dnf install gcc gcc-c++ libstdc++-static make cmake extra-cmake-modules \
    qt5-qtbase-devel qt6-qtbase-devel kf5-kconfig-devel kf5-kconfigwidgets-devel \
    kf6-kconfig-devel kf6-kcolorscheme-devel pkgconf-pkg-config glib2-devel \
    gtk3-devel gtk4-devel libadwaita-devel binutils dbus-daemon \
    xorg-x11-server-Xvfb xorg-x11-xauth unzip glibc-langpack-de python3.13 \
    dejavu-sans-fonts google-noto-sans-cjk-fonts
  ./gradlew check -Pjasper.python=/usr/bin/python3.13
  ```

  If your distribution lacks the required Qt/KDE development versions, configure the SDK prefixes described in
  [Desktop colors helpers](packaging/desktop-colors/README.md). Run Gradle as your normal user so the filesystem
  permission tests exercise ordinary user access.

Use the checked-in Gradle wrapper (`./gradlew`, or `.\gradlew.bat` in PowerShell); the first build downloads Gradle
and the dependencies.

### Build, test and run

```bash
./gradlew build                 # compile, then run every check
./gradlew check                 # tests, architecture checks and doclint; opens no window
./gradlew :jasper-app:run       # run Jasper from source
```

`:jasper-app:run` opens a window and starts your login shell. It keeps its own Jasper home in
`jasper-app/build/dev-home` (choose another with `-Pjasper.home=<dir>`), so an installed Jasper's settings and plugins
are untouched. Coding agents follow [AGENTS.md](AGENTS.md) and leave anything that opens a window to the user.

### Packages

Packages are built on the operating system and architecture they are for, with a JBR SDK of that architecture.
Without `-PjasperVersion=X.Y.Z` the version comes from the commit's `vX.Y.Z` tag, else the latest such tag behind it,
else `1.0.0`. Outputs land in `jasper-app/build/packaging/dist/`. [Packaging](docs/packaging.md) has the details,
the image layouts and the acceptance checklists.

- **macOS**: `./gradlew check :jasper-app:packageDist` builds `Jasper.app` with a trimmed runtime, launches it once
  for a few seconds (no Dock icon) to train its start-up cache, and builds the DMG for the build machine's
  architecture. The app is ad-hoc signed. `:jasper-app:packageApp` and `:jasper-app:verifyPackage` build and check
  the app without launching it.
- **Linux**: `./gradlew check :jasper-app:packageDist` builds the `.rpm` on recognized RPM-family hosts and both
  `.deb` and `.rpm` on other hosts. RPM builds need `rpmbuild`, `readelf`, `rpm2cpio` and `cpio`; DEB builds also need
  `fakeroot`, `dpkg-deb` and a Debian-family build host for dependency lookup (on Ubuntu:
  `sudo apt-get install fakeroot dpkg rpm cpio binutils g++`; on Fedora:
  `sudo dnf install rpm-build rpm cpio binutils libstdc++-static`).
- **Windows**: `.\gradlew.bat check :jasper-app:packageDist` builds the portable ZIP;
  `.\gradlew.bat :jasper-app:packageInstaller` builds the setup wizard and needs WiX 3 on the `PATH`. Both carry the
  pinned Cygwin seed: Gradle downloads its hash-locked artifacts (about 510 MB) into
  `jasper-app/build/cygwin/downloads` the first time, or takes a retained copy from `-Pjasper.cygwinSnapshot=<dir>`.

`./gradlew pluginZips` zips every in-repo plugin into `plugins/build/zips/`, and `./gradlew sdkRelease` writes the
SDK and test-kit jars to `build/sdk-release/`.

### Windows user installation

`.\gradlew.bat :jasper-app:installWindowsUser` (in IntelliJ's Gradle panel, `jasper-app` › `distribution` ›
`installWindowsUser`) installs Jasper for the current user without a C/C++ compiler, WiX, an installer or administrator
rights. It builds the application and the bundled plugins into a jpackage app image with the standard JBR launcher,
installs it in `%APPDATA%\JasperApp` and adds a Start Menu shortcut, from which you then start Jasper. A small Java
helper, compiled with the JBR SDK, copies the files and creates the shortcut through Windows APIs, so PowerShell script
execution can stay disabled; Windows must allow the generated `Jasper.exe` to run. Run the task on its own, not with
the native packaging tasks.

The task also installs the pinned Cygwin starter set in `%LOCALAPPDATA%\Jasper\cygwin`, using bundled Setup and relay
binaries. Gradle fetches only hash-locked artifacts using Java; installation works offline once staged. Choose
**Cygwin Bash** as **Shell profile** in Settings › Terminal › Shell, and use **Manage Cygwin packages…** beside it, or
in the command palette, to add tools. Updates and uninstall preserve the Cygwin root, packages and user files. See
[Cygwin Bash](docs/terminal.md#cygwin-bash) for the environment, its packages and repairing it, and
[Cygwin on Windows](docs/diagnostics.md#cygwin-on-windows) for its logs and messages.

Exit Jasper, including its background process, before installing an update or running
`.\gradlew.bat :jasper-app:uninstallWindowsUser`, which does not build the application; it removes the installation,
its shortcut and its matching login entry, keeping your settings and data in `%APPDATA%\jasper`. Jasper's background
setting controls residency and login start-up as usual. This installation has no custom `jasper.vmoptions`, no native
start-up recovery and no native `jasper` command.

## Repository layout

| Path | Contents |
| --- | --- |
| `jasper-app/` | The application: workspace, settings, command palette, notifications, plugin host, platform integration |
| `jasper-terminal/` | The terminal library: sessions, emulation over JediTerm, rendering, input, selection, search and shell integration |
| `jasper-ui/` | The theme engine and Swing UI kit: themes, kits, metrics, icons and components |
| `jasper-buddy/` | The JDK-only desk companion library behind Desk Buddy |
| `jasper-sdk/` | The plugin SDK, `dev.jasper.sdk` (JDK only) |
| `jasper-sdk-testkit/` | The test kit's fake plugin host and the contract suite it shares with the real host |
| `plugins/` | The bundled plugins, one Gradle module (`:jasper-plugin-<folder>`) per folder |
| `packaging/` | Jasper's launcher and launch watcher, the native `jasper` command, icons, Buddy sprites, the Linux and Windows packaging scripts, and the Windows Cygwin seed, PTY relay and installer tools |
| `tools/` | Benchmark runners, the theme converter, the SDK reference generator, icon and tab previews, and the mock comparison tools |
| `docs/` | The documentation, the external plugin example, and dated design records |
| `website/` | Jasper's homepage: static HTML, CSS and JavaScript, its screenshots and the script that packages it for GitHub Pages |
| `gradle/` | The build's shared scripts: architecture checks, packaging, launcher, SDK release |
| `config.example.toml` | The core settings at their defaults, as a starting `config.toml` |
| `design/` | The app icon's exploration rounds |
| `.github/workflows/` | CI, the Linux package checks, the Release workflow, the SDK jar upload and the manual website deployment |

## License

Jasper is licensed under the [PolyForm Noncommercial License 1.0.0](LICENSE.md). About Jasper › Licenses… lists the
third-party components Jasper ships and their licences.
