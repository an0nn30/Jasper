# Moray

A cross-platform terminal workstation written in Java Swing, with macOS as the immediate daily-use target and a MobaXterm-style layout. Phase 1 builds the terminal before SSH sessions, credential vault, SFTP, tunnels and plugins.

Moray has its own terminal renderer over JediTerm and pty4j, with ligatures, fallback fonts, truecolor, mouse reporting, scrollback, selection, clipboard, shell integration and links. The Plan 3 application adds multiple windows, tabs, splits, pane zoom/navigation, find controls, menus, toolbar and status.

Current progress, limitations and next steps: [docs/STATUS.md](docs/STATUS.md). Plan 3's desktop acceptance remains a [user-run checklist](docs/superpowers/plans/2026-09-11-moray-plan-3-manual-check.md). Passing the headless tests is not the Phase 1 daily-use gate.

## Requirements

Java 25 on the **JetBrains Runtime** (JBR JDK). Gradle requires vendor JetBrains and discovers the runtime automatically on the development Mac. Use the wrapper, not a system Gradle installation.

## Build and run

```bash
./gradlew check
./gradlew :moray-app:run
```

`check` is headless. `run` opens windows and starts your login shell. Coding agents must follow [AGENTS.md](AGENTS.md) and leave GUI checks to the user.

For the throughput benchmark, when no game or VM is running:

```bash
./gradlew :moray-app:bench
```

The benchmark opens a temporary window and measures ~100 MB of ANSI output. Minimum acceptance is 35 MB/s; target 45 MB/s. See STATUS for recorded measurements and pending verification.

## Using the application

On macOS: Cmd+T new tab, Cmd+N new window, Cmd+D split right, Cmd+Shift+D split down, Cmd+Option+arrows focus a pane, Cmd+Shift+Enter zoom, Cmd+F find, F2 rename, Cmd+W close tab, Cmd+Shift+W close pane. Tabs have close controls, support middle-click close and drag reorder. A blank rename restores the shell/directory title.

Cmd+= / Cmd+- / Cmd+0 changes or resets the focused pane's font. Cmd+K clears history while retaining the live screen. Menus expose the full action list. View controls toolbar labels/visibility, status visibility and light/dark chrome. Configuration files and persistence arrive in Plan 4; Settings/Reload are visibly disabled for now.

On Linux/Windows, `cmd` maps to Ctrl+Shift. Defaults written with an additional explicit Shift add Alt to remain distinct (for example split down is Ctrl+Alt+Shift+D). Ordinary Ctrl remains available to terminal programs.

Working-directory inheritance and prompt navigation use OSC 7 and OSC 133 emitted by your shell. Without those sequences, a pane retains its launch-directory fallback. Search currently matches each physical row, so matches do not span a soft wrap.

## Layout

- `moray-terminal/`: sessions, emulator integration, terminal rendering/input, selection, search and shell integration.
- `moray-app/`: window/pane ownership, app models, actions, tabs/splits, find and desktop chrome.
- `docs/STATUS.md`: completed work, open items and deferred findings.
- `docs/superpowers/specs/` and `docs/superpowers/plans/`: specifications and implementation plans.
