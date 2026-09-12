# Moray

A cross-platform terminal workstation written in Java Swing, with macOS as the immediate daily-use target and a MobaXterm-style layout. Phase 1 builds the terminal before SSH sessions, credential vault, SFTP, tunnels and plugins.

Moray has its own terminal renderer over JediTerm and pty4j, with ligatures, fallback fonts, truecolor, mouse reporting, scrollback, selection, clipboard, shell integration and links. The Plan 3 application adds multiple windows, tabs, splits, pane zoom/navigation, find controls, menus, toolbar and status. The screenshot UI revision puts tabs beside native macOS window controls, with a horizontal Tabler toolbar below and a status bar that shares the terminal background. Coordinated Atom-inspired dark/light themes update the complete window.

Current progress, limitations and next steps: [docs/STATUS.md](docs/STATUS.md). Desktop acceptance remains user-run: [Plan 3](docs/superpowers/plans/2026-09-11-moray-plan-3-manual-check.md) and [screenshot UI](docs/superpowers/plans/2026-09-11-moray-mock-ui-manual-check.md). Passing the headless tests is not the Phase 1 daily-use gate.

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

On macOS: Cmd+T new tab, Cmd+N new window, Cmd+D split right, Cmd+Shift+D split down, Cmd+Option+arrows focus a pane, Cmd+Shift+Enter zoom, Cmd+F find, F2 rename, Cmd+W close tab, Cmd+Shift+W close pane. Tabs have close controls, support middle-click close and drag reorder. Opening and closing tabs animate with a quick eased settle; the active underline slides with selection and closing gaps. Closing stops the shell immediately. A blank rename restores the shell/directory title. Cmd+1–9 selects an existing tab; Cmd+Shift+[ / Cmd+Shift+] moves to the previous/next tab and wraps at the ends.

Cmd+= / Cmd+- / Cmd+0 changes or resets the focused pane's font. Cmd+K clears history while retaining the live screen. Menus expose the full action list. View controls toolbar labels/visibility, status visibility and light/dark appearance across all windows and terminals, including hidden panes. Theme changes retain shells, scrollback, split ratios and font choices. On macOS the tabs share the title surface, with the toolbar immediately below. The title/tab row starts at 38 logical pixels. View → Tab height… adjusts it from 28–72 pixels for the current window, with a reset to 38; Cancel preserves the previous height. This setting lasts for the current window session and survives theme changes. The initial app terminal font is 16px; Cmd+0 restores it. Configuration files and persistence arrive in Plan 4; Settings/Reload are visibly disabled for now.

On Linux/Windows, Ctrl+1–9 selects an existing tab and Ctrl+Shift+[ / Ctrl+Shift+] selects the previous/next tab. For other actions, `cmd` maps to Ctrl+Shift. Those defaults written with an additional explicit Shift add Alt to remain distinct (for example split down is Ctrl+Alt+Shift+D). Other ordinary Ctrl combinations remain available to terminal programs. Keybinding overrides also accept `{` and `}` as Shift+[ and Shift+].

Working-directory inheritance and prompt navigation use OSC 7 and OSC 133 emitted by your shell. Without those sequences, a pane retains its launch-directory fallback. Search currently matches each physical row, so matches do not span a soft wrap.

## Layout

- `moray-terminal/`: sessions, emulator integration, terminal rendering/input, selection, search and shell integration.
- `moray-app/`: window/pane ownership, app models, actions, tabs/splits, find and desktop chrome.
- `docs/STATUS.md`: completed work, open items and deferred findings.
- `docs/superpowers/specs/` and `docs/superpowers/plans/`: specifications and implementation plans.
