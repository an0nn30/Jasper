# Jasper documentation

Jasper is a pluggable DevOps workstation. These guides describe the code in this repository: the application, its
bundled plugins, the plugin SDK, and the libraries the application is built from. To use Jasper, start with
[Using Jasper](#using-jasper); to extend it, with [Building plugins](#building-plugins).

## Using Jasper

- [Getting started](getting-started.md): installing on each platform, the first launch, a first tour, safe mode and
  uninstalling.
- [Workspace](workspace.md): windows, tabs, panes, tool windows and the dock, menus, the toolbar, the status bar, view
  modes and the file chooser.
- [Terminal](terminal.md): terminal panes, shell profiles (WSL and [Cygwin Bash](terminal.md#cygwin-bash) among them),
  selection, links, search and shell integration.
- [Command palette](command-palette.md): commands, scopes, recents and the Run window.
- [Notifications](notifications.md): cards, the Notifications tool window, groups, the Errors window, system
  notifications and Desk Buddy.
- [Keyboard shortcuts](keyboard-shortcuts.md): every default shortcut on macOS, Linux and Windows, and the Keymap.
- [Configuration](configuration.md): the Settings dialog, every setting in `config.toml`, Jasper's home and its files.
- [Command line](command-line.md): Jasper's startup options and the `jasper` command.
- [Diagnostics](diagnostics.md): log files, their bounds and privacy, memory, start-up timing, and what Jasper does
  when it cannot start.

## Plugins

- [Plugins](plugins.md): managing plugins in Settings › Plugins, installing from a zip, consent, and the bundled
  plugins.
- [Remote](remote.md): SSH hosts, WSL distributions, SFTP and transfers, editing remote files, tunnels, host-key
  trust, the Sessions tool window and the API Remote exports to other plugins.
- [Credential Vault](credential-vault.md): logins, SSH keys and secure notes, grants and key generation.
- [Editor](editor.md): editing local and remote files in workspace tabs, previewing Markdown and the `jasper editor`
  command. Its Vim mode has a list of [supported keys](editor-vim-reference.md) and of
  [differences from Vim](vim-divergences.md).
- [File Server](file-server.md): sharing a folder over HTTP, HTTPS, FTP, FTPS, SFTP and TFTP.
- [Project Index](project-index.md): indexing projects and finding their files from the command palette.
- [Shell History and Snippets](history-and-snippets.md): searching shell history and saving commands.
- [Enterprise](enterprise.md): the Enterprise look and how to turn it on.

## Theming

- [Themes](themes.md): the built-in, plugin and installed themes, choosing the appearance and the terminal scheme, and
  importing and removing themes.
- [Creating themes](creating-themes.md): the theme file format, writing a theme, and converting Alacritty and IntelliJ
  themes.
- [Looks](looks.md): what a look changes and the kit file a look plugin ships.

## Building plugins

- [SDK reference](sdk-reference.html): every SDK package, type and member, and the bundled plugins' exported APIs.
- [Writing a plugin](plugin-authoring.md): the manifest, capabilities, lifecycle, contributions, settings, packaging
  and testing.
- [Plugin UI](plugin-ui.md): building panels, forms and windows with the theme engine.
- [Plugin APIs](plugin-apis.md): the APIs that the Vault, Editor, Remote and Snippets plugins export to other
  plugins.
- [SDK architecture](sdk-architecture.md): how Jasper discovers, loads, threads and stops plugins.
- [Plugin icons](sdk-icons.md): the semantic icon names and registering a plugin's own monochrome icons.
- [jasper-sdk](../jasper-sdk/README.md): the SDK's packages and release jars.
- [jasper-sdk-testkit](../jasper-sdk-testkit/README.md): the fake plugin host for unit tests.
- [External plugin example](examples/external-plugin/README.md): a plugin built outside this repository against the
  SDK release jars.

## Building Jasper and contributing

[AGENTS.md](../AGENTS.md) covers the toolchain, the architecture rules and what coding agents may run unattended.
The root [README](../README.md#building-from-source) lists the build requirements and, under
[Windows user installation](../README.md#windows-user-installation), says how to install a build for the current
Windows user without a C/C++ compiler. Use the Gradle wrapper with a JetBrains Runtime SDK 25:

```sh
./gradlew check :jasper-app:installDist
```

This runs the headless checks, doclint, the compiled documentation examples and the architecture guards, then
assembles the application distribution. It opens no window. `:jasper-app:run`, the benchmarks and, on macOS,
`:jasper-app:packageDist`, `:jasper-app:trainAotCache` and any packaging task run with `-Pjasper.aot=true` open a
window, so a coding agent runs them only with the user's authorization.

| Module | Start here | Architecture | Maintenance and extensions |
| --- | --- | --- | --- |
| `jasper-app` | [Onboarding](../jasper-app/README.md) | [Composition and feature ownership](app-architecture.md) | [Commands, scopes, settings, notifications, platform hooks, packaging and chrome](app-maintenance.md) |
| `jasper-terminal` | [Onboarding and embedding](../jasper-terminal/README.md) | [Owners, API and threading](terminal-architecture.md) | [Actions, keys, options, shell events, attached sessions and file links](terminal-maintenance.md) |
| `jasper-ui` | [Engine and kit](../jasper-ui/README.md) | [Packages, API and ownership](ui-architecture.md) | [Gallery, extensions and distribution](ui-maintenance.md) |
| `jasper-buddy` | [Embedding](../jasper-buddy/README.md) | [Facade, model and presentation](buddy-architecture.md) | [Notices, options, animation and assets](buddy-maintenance.md) |

- [Packaging](packaging.md): native images, the DMG, the Windows ZIP, setup wizard and user installation, the Linux
  packages, the release workflow and the acceptance checklists.
- [Bundled Windows Cygwin](../packaging/windows/cygwin/README.md): the seed, provisioning, package management and
  native acceptance, and its [prebuilt PTY relay](../packaging/windows/cygwin/relay/README.md).
- [Benchmarks](benchmarks.md): throughput, memory and start-up measurements and how to compare them.
- [Launcher](../packaging/launcher/README.md): Jasper's build of the jpackage launcher and its launch watcher.
- [The `jasper` command](../packaging/cli/README.md): the native client and its protocol.
- [App icon assets](../packaging/icons/README.md) and [Buddy sprite assets](../packaging/buddy/README.md): sources and
  regeneration.
- [Mock comparison tools](../tools/visual/README.md): comparing rendered surfaces with design references.
- [Website](../website/README.md): the homepage, previewing it locally and its manual GitHub Pages deployment.

## Project records

These files record how Jasper got here. Dated records describe the code at the time they were written: their names,
paths, commands and counts may no longer match, and the guides above take precedence.

- [STATUS](STATUS.md): the running log of integrated work, open items and pending manual checks.
- [Specifications](superpowers/specs/) and [implementation plans](superpowers/plans/): the approved designs and the
  plans that built them, starting with the
  [original terminal design](superpowers/specs/2026-09-10-jasper-phase-1-terminal-design.md).
- [Design records](design/) and [benchmark results](benchmarks/): screenshots, references and measurements from their
  recorded revisions.
- Verification reports: [terminal refactor](terminal-refactor-verification.md),
  [app and Buddy refactor](app-refactor-verification.md), [Editor](editor-verification.md),
  [Remote SSH](remote-7a-verification.md), [Remote SFTP](remote-7c-verification.md),
  [Windows start-up](windows-startup-verification.md), [macOS Local Network](mac-local-network-verification.md) and
  [new UI icons](new-ui-icons-verification.md).
- [Terminal readiness](terminal-readiness.md): the terminal's 2026-09-12 readiness handoff.
- [Documentation audit](documentation-audit.md): the 2026-09-21 documentation audit.
- [Renamed from Moray](rebranding.md): why older records use the name Moray.
