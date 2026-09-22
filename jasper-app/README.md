# Jasper application — contributor start

Jasper is a Swing terminal workstation. This module composes independent terminal
and desktop-companion libraries; it owns windows, commands, configuration, command
recents, residency and native desktop integration; shell history and snippets are bundled plugins.

## First checkout

```sh
git clone https://github.com/an0nn30/moray.git
cd moray
./gradlew check
./gradlew :jasper-app:installDist
```

Install **JetBrains Runtime (JBR) 25**, including development tools. The Gradle wrapper
requires the JetBrains vendor and discovers the installed toolchain. Use the wrapper,
not a system Gradle. The developer Mac's path is recorded in [AGENTS.md](../AGENTS.md).
`check` is headless: tests, both bytecode architecture checks, Javadoc and executable
guide examples. Read XML counts in each module's `build/test-results/test/` directory.
Missing fish and the environment-dependent terminal font check are expected skips.

The stable entry point is [Main](src/main/java/dev/jasper/app/Main.java), which delegates
to [ApplicationBootstrap](src/main/java/dev/jasper/app/bootstrap/ApplicationBootstrap.java).
The installed launcher lives under `build/install/jasper-app/bin/`. A human can run
`./gradlew :jasper-app:run` for desktop acceptance. Agents must not launch that command,
a GUI or native benchmark without the user's request. Building the distribution is safe.

## Find the owner before editing

- [Architecture and lifecycle](../docs/app-architecture.md): module/package graph,
  startup rollback, EDT ownership, shutdown, supported library boundaries.
- [Maintenance recipes](../docs/app-maintenance.md): add a command or shortcut, palette
  scope, setting, notice producer, platform hook or lifecycle change.
- [Terminal onboarding](../jasper-terminal/README.md): sessions, view options and emulator rules.
- [Buddy embedding](../jasper-buddy/README.md): the five supported facade/value types.
- [SDK architecture](../docs/sdk-architecture.md) and [plugin authoring](../docs/plugin-authoring.md):
  how `plugins/` loads, consents to and hosts plugins, and how to write, test and package one.
- [Current handoff](../docs/STATUS.md) and [verification](../docs/app-refactor-verification.md):
  actual checkpoints, decisions, independent review and pending native acceptance.

For a command begin with `workspace/WorkspaceActions`, `commands/ActionId` and
`workspace/WindowCommands`; for live font defaults begin with `config/ConfigSnapshot`
and `workspace/WorkspaceConfiguration`; for Buddy producers begin with
`notifications/CommandNotifier` and `workspace/WorkspaceActivity`.

Tests live beside the behavior owner under `src/test/java`. Cross-feature workspace
fixtures live in the workspace test package. Pure fixtures live in `testsupport`.
Small owner-package `*TestSupport` bridges let integration tests inspect private UI
without exposing test hooks in production. Buddy's separate test-fixtures artifact
is a test dependency only. No preview or fixture is shipped.

## Plugins

Plugins compile against `jasper-sdk` only and are loaded by `plugins/` (`PluginRuntime`,
discovery, resolver, loader and host) with their own class loaders; SDK types appear in the
app only inside `dev.jasper.app.plugins`. The bundled plugins under the repository's `plugins/`
directory are staged by `stagePlugins` into `build/plugins/` and shipped as `lib/plugins/<id>/`
in the application image, never on the application classpath. `run` and the IntelliJ "Jasper
(dev home)" configuration use `build/dev-home` as the home, so the installed app's plugins,
consent (`plugins.toml`) and `plugin-data/` stay untouched; `--plugin-dir <dir>` loads one more
plugin with consent pre-granted, and `--safe-mode` loads no user plugins. The user-facing manager
is `pluginmanager/PluginManager` (File → Manage Plugins…); installs, removals and restarts are
described in the root [README](../README.md#plugins) and [plugin authoring](../docs/plugin-authoring.md).

## Change workflow

Read STATUS and the binding design before changing ownership. Work on a branch,
keep a runnable checkpoint, add meaningful regressions for behavioral changes, run
focused tests and then `./gradlew check`. Use native inline implementation for this
approved plan, with one independent whole-branch review. Do not push or merge without
current authorization. Update docs and executable examples with API changes.

A new setting must propagate through its builder, parser, template and example config,
then through its owning live-setting comparison or launch capture as appropriate;
the maintenance guide lists each route.
A new resource must be tested from the produced jar. FlatLaf and shell-integration
resource paths remain under `dev/jasper/app/`; moving a Java class can still require
updating reflective class names in `.properties` files.
