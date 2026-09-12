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

Cmd+= / Cmd+- / Cmd+0 changes or resets the focused pane's font. Cmd+K clears history while retaining the live screen. Menus expose the full action list. View controls toolbar labels/visibility, status visibility and light/dark appearance across all windows and terminals, including hidden panes. Theme changes retain shells, scrollback, split ratios and font choices. On macOS the tabs share the title surface, with the toolbar immediately below. The title/tab row defaults to 38 logical pixels. View → Tab height… adjusts it from 28–72 pixels for the current window, with a reset to 38; Cancel preserves the previous height. View choices are temporary overrides and survive unrelated configuration reloads. The default app font is 16 points; Cmd+0 restores the configured size.

Settings opens the TOML configuration, creating a commented template only when absent. Saved tab height, toolbar/status visibility, font family/fallback/size/ligatures/line height, cursor/input/copy/bell options, shell-exit behavior, inactive-pane dimming, theme and shortcuts reload live across windows. Shell command, exact arguments, environment and scrollback defaults apply to new pane requests; columns/lines supply the initial grid for new windows. Existing sessions continue running. Reload config forces a read; the status indicator opens file diagnostics. See [configuration](docs/configuration.md) for the complete settings table, defaults/ranges, paths, `--config`, shell/shortcut examples and error behavior.

### Configuration example

`terminal.on_exit` defaults to `"keep_open"`: a stopped shell retains its output and exit marker. Set it to `"close_on_success"` to close on exit code 0, or `"close"` for any shell-process exit. Add `on_exit = "close_on_success"` under the existing `[terminal]` table to opt in. Only the exited pane closes; its tab and window close when empty, while siblings keep running. A command returning to the shell does not trigger this behavior. Reload affects future exits and never closes already retained output or restarts the stopped shell.

The root [config.example.toml](config.example.toml) is a copyable file containing every supported non-shortcut setting at its built-in default. From the repository or worktree root on macOS, install it with:

```bash
mkdir -p ~/.config/moray
cp -i config.example.toml ~/.config/moray/config.toml
```

If `config.toml` already exists, `cp -i` prompts before replacing it; answer `n` to keep the existing file.

Custom theme files, automatic system appearance, logging and app packaging remain planned. Native font/input/audio and initial sizing acceptance is tracked in the [terminal configuration checklist](docs/superpowers/plans/2026-09-12-moray-plan-4b-manual-check.md).

On Linux/Windows, Ctrl+1–9 selects an existing tab and Ctrl+Shift+[ / Ctrl+Shift+] selects the previous/next tab. For other actions, `cmd` maps to Ctrl+Shift. Those defaults written with an additional explicit Shift add Alt to remain distinct (for example split down is Ctrl+Alt+Shift+D). Other ordinary Ctrl combinations remain available to terminal programs. Keybinding overrides also accept `{` and `}` as Shift+[ and Shift+].

Working-directory inheritance and prompt navigation use OSC 7 and OSC 133 emitted by your shell. Without those sequences, a pane retains its launch-directory fallback. Search currently matches each physical row, so matches do not span a soft wrap.

## Layout

- `moray-terminal/`: sessions, emulator integration, terminal rendering/input, selection, search and shell integration.
- `moray-app/`: window/pane ownership, app models, actions, tabs/splits, find and desktop chrome.
- `docs/STATUS.md`: completed work, open items and deferred findings.
- `docs/superpowers/specs/` and `docs/superpowers/plans/`: specifications and implementation plans.
