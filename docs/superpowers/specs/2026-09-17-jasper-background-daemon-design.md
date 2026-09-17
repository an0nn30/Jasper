# Jasper background residency — design

**Status:** Designed, not implemented. Development branch: `claude/background-daemon`, from main `1f1afeb`.

**Builds on:** the [packaging design](2026-09-12-jasper-plan-4d-packaging-design.md) (the jpackage image whose launcher the login item points at) and the [config design](2026-09-11-jasper-plan-4a-config-design.md) (`ConfigSnapshot`, `ConfigLoader` and live reload).

## Purpose

Jasper exits when its last window closes, so every launch pays a full cold start: JVM, AWT toolkit, FlatLaf, font resolution and metrics, theme, shell-integration extraction, shell-history discovery. The user wants Jasper resident in the background — started at login — so that opening a terminal is instant rather than a wait.

The feature is opt-in and off by default. It is for the user's own machine and is expected to be exercised before it is trusted.

**Not measured.** The current cold-start time is unknown. Measuring it means launching the GUI, which `AGENTS.md` reserves for the user, so this design is written without a "before" number. Confirming the win — and the idle cost that buys it — is the first item on the user's manual checklist.

## Confirmed decisions

- **The daemon is the app.** A resident Jasper is an ordinary Jasper process that happens to have no windows. Rejected: a separate supervisor pre-forking app processes, which buys crash isolation and memory return for two binaries, two lifetimes, and a full cold start in the background after every launch. Rejected: relying purely on the OS to keep the app alive, which is most of the value on macOS and nothing at all on Windows, where every launch is a new process.
- **Nothing of the user's runs while resident.** No shell, no PTY, no child process. Sessions have already exited through the normal pane-close path before the last window goes.
- **Close is not quit.** Closing the last window goes resident; Quit (Cmd+Q, the palette's Quit, the native quit handler) exits for good. This is how a macOS app that survives window close is expected to behave, and it is the off switch that does not require editing config. Rejected: Quit also going resident, which reads as a bug. Rejected: respawning a fresh daemon behind a Quit, which means Quit never quits.
- **One key does both.** Enabling residency also registers the login item; there is no second switch. Rejected: separate residency and autostart settings, which is more surface and more states for a feature whose entire point is "be running when I want you".
- **No new UI.** No tray icon, no menu-bar extra, and the resident process stays visible in the Dock on macOS. You summon a window by launching Jasper the way you already do. Rejected: a tray icon, which is discoverable and gives a kill switch but adds a surface that cannot be verified headlessly. Rejected: hiding the process entirely (`LSUIElement`), which also drops it out of Cmd+Tab.
- **Warm means warm.** A resident process that has never initialized AWT is barely warmer than a cold one, so it runs an explicit warm-up pass at start. Rejected, for now: an invisible anchor frame to pre-warm native peer creation — the obvious next lever, but it edges toward the pre-built-window model the user declined, and it should be added only if measurement shows window creation dominates.

## The setting

```toml
[background]
# Restart required for residency; the login item updates on reload. Keep Jasper running with no
# windows after the last one closes so the next launch is instant, and start it in the background
# at login. Quit still exits completely. Autostart requires a packaged install.
enabled = false
```

A `boolean backgroundEnabled` on `ConfigSnapshot`, beside `buddyEnabled` and `historyEnabled`, read by `ConfigLoader` from a new `[background]` table. Default `false`. An unknown key inside the table is reported the way every other unknown key is.

The key has two scopes, deliberately different:

- **Residency is decided once, at startup.** Whether *this* process owns the handoff socket is not something to renegotiate mid-flight: a live toggle would have to contend for a socket another Jasper may hold, and fail in a way with no good report. A process that started with the key off never becomes resident, and one that started with it on stays resident until it is quit.
- **The login item is reconciled on every config load.** Read the key, make the OS match, idempotently. Turning the key on registers autostart immediately; turning it off removes it immediately.

## One binary, three roles

There is no second executable. Startup resolves which role this process plays:

- **Launcher** (default). Before any AWT or Swing initialization, attempt the handoff. If a resident process accepts, exit 0. Doing this ahead of toolkit init is what makes the handoff near-instant, and on macOS it avoids a second Dock icon settling in before the process goes away.
- **Resident** (`--background`, the form written into the login item). Full startup, warm-up pass, bind the socket, open no window.
- **App** (no daemon answered, or `--config` was given). Today's path exactly. It additionally binds the socket, and if `background.enabled` is on it stays resident when its last window closes.

`AppArguments` gains `--background`; `AppArguments.USAGE` becomes
`Usage: jasper [--config <path>] [--background] [--help]`. Passing it twice is rejected like a duplicate `--help`.

Two triggers reach a resident process, and both are needed:

| Trigger | Path |
|---|---|
| macOS Dock click, Spotlight, `open -a Jasper` | No new process is created; AppKit sends a reopen event. `java.awt.desktop.AppReopenedListener` and `Desktop.Action.APP_EVENT_REOPENED` are both present on JBR 25 (verified by probe). |
| Windows shortcut, Start menu, taskbar; running the executable directly on either OS | A new process starts, hands off over the socket, and exits. |

The reopen listener is registered next to the existing `Desktop.setQuitHandler` call in `JasperApplication`, guarded by the same `supportsNativeQuit`-style capability check, and it is registered **regardless of the setting** — clicking the Dock icon of a running Jasper whose windows are all minimized should raise a window whether or not residency is on. Its handler is the same one the socket uses: open a window if none is open, otherwise raise the last active one.

## Handoff protocol

An `AF_UNIX` socket at `<AppDirs.root>/daemon.sock`. Verified on JBR 25: bind, connect, transfer and an owner-only parent directory all work. Java supports `AF_UNIX` on Windows 10 1803 and later; **this is unverified on Windows and is a flagged acceptance check.**

`AppDirs` gains `daemonSocket()`, `daemonToken()` and `daemonLock()` beside the existing `commandHistory()`, `buddyState()` and `snippets()`, resolving against the same per-user root and still touching no filesystem.

One request line, one response line, UTF-8, newline-terminated:

```
jasper<TAB>1<TAB><token><TAB><base64 cwd><TAB><base64 code source><TAB><mtime millis>\n
```

Response is `ok\n`, or `refused\t<reason>\n` with reason one of `token`, `stale`, `protocol`. `ok` acknowledges that the resident process has **accepted** the request, not that a window is on screen; the launcher exits as soon as it reads the line rather than waiting for the window, so a slow first paint never holds a process open.

Base64 for paths because a working directory may contain tabs, newlines or invalid UTF-16; the shell integration already carries its command payload the same way. `1` is the protocol version of the wire format itself.

**Token.** 256 random bits, base64, written to `<AppDirs.root>/daemon.token` at bind time with owner-only permissions, and the socket's parent directory is set to `rwx------` where POSIX permissions apply. Without this, any local user could make the resident process open a terminal window running the user's login shell on the user's display. It is roughly twenty lines and closes a real if minor local hole. On Windows the per-user `%APPDATA%` ACL is the directory gate and the token still applies.

**Upgrade guard.** The launcher sends the absolute path of its own code source and that file's last-modified time. If either differs from the resident process's own, the resident process replies `refused stale` and unbinds, releasing ownership so the newer launcher can take it; the launcher then starts normally. This catches a real reinstall and a developer rebuild with the same mechanism and needs no change to the build files. A version string would read better in a log but would need a new jar manifest entry to say anything the code source does not already say.

A stale resident process **exits only if it has no windows open.** If the user still has windows from the older build, it stops listening and keeps running as an ordinary app until those windows are closed or quit normally. An upgrade must never take a running terminal down.

**Stale sockets.** After a crash the socket file remains. Binding is therefore: take a `FileLock` on `<AppDirs.root>/daemon.lock`; try to connect to any existing socket; if the connect succeeds, this process is not the owner; if it fails, unlink and bind. The lock makes check-unlink-bind atomic, so two simultaneous cold launches cannot both conclude they are the owner.

**`--config` never hands off.** A launch pointed at a different configuration file runs standalone, because the resident process is holding a different configuration entirely.

## Lifecycle

The change in `JasperApplication` is small. Today `windowClosed` calls `requestShutdown()` when the window set empties, which reaches `shutdown()` and then `terminate.run()`. With residency on and the last window gone, it skips `requestShutdown` and keeps `history`, `shellHistory`, `snippets`, `configuration` and the `launches` executor alive — those *are* the warm state, and closing them would undo the feature. The buddy hides on its existing no-visible-window path. `quit()` is untouched and still terminates.

`JasperApplication` already takes an injected `terminate` Runnable, so the whole behaviour is assertable headlessly with no new seam.

**AWT will not hold the JVM.** AWT shuts its event dispatch thread down when nothing is displayable and nothing has registered as busy, so a resident process needs a non-daemon thread of its own. The socket accept loop is that thread; it is a platform thread, not a daemon, and its termination is what lets the JVM exit. A later `SwingUtilities.invokeLater` restarts the EDT on demand, which is the supported behaviour.

**Logs are already bounded.** `AppLog` uses a `FileHandler` with 1 MiB × 3 rotating files, so a process resident for weeks cannot fill the disk. No change needed.

## Warm-up

On start, a resident process performs everything the first window would otherwise pay for, and creates no window:

- install FlatLaf and realize the toolkit,
- resolve the configured font family and its fallbacks and build the font set,
- resolve the theme for the configured variant,
- extract the shell-integration scripts (`Main` already does this before the first window),
- refresh the shell-history index (`newWindow` does this today only for the first window).

The last two already happen at startup or first window; the first three are the new work, and they are what the feature is actually buying.

## Login item

`LoginItem.plan(osName, enabled, installLocation, home, env)` returns a description of files to write or delete and commands to run, touching nothing. A thin executor applies it. This mirrors the existing pure resolvers `AppDirs.resolve` and `LaunchSettings.resolve`, and it means both platforms' output is asserted in `./gradlew check` on any host. There is no `LoginItem` interface: one resolver switching on the OS name, as `AppDirs` and `LaunchSettings` already do.

- **macOS** — `~/Library/LaunchAgents/dev.jasper.background.plist` with `RunAtLoad` true, `KeepAlive` false and `LimitLoadToSessionType: Aqua`, then `launchctl bootstrap gui/<uid>` to load it (and `bootout` to remove it). `KeepAlive` is false deliberately: a crashed Jasper should stay down until the user asks for it, not respawn in a loop.
- **Windows** — a value under `HKCU\Software\Microsoft\Windows\CurrentVersion\Run` written with `reg add` and removed with `reg delete`. User scope, no elevation. Rejected: a `.lnk` in the Startup folder, which needs a COM call or a PowerShell shell-out to create.

**Only a packaged install registers.** The plan resolves the install location from the running code source and produces no plan when that is not a jpackage image — so a `./gradlew run` development session stays resident but never writes a login item pointing into a build directory.

## Documentation

- `config.example.toml` gains the `[background]` table, commented as every other key is, with its default and the restart/reload split stated.
- `docs/configuration.md` gains a **Background residency** section under *Supported settings*: what residency does, that Quit still exits, what the login item writes on each platform, that autostart needs a packaged install, and where the socket, token and lock live.
- `docs/packaging.md` gains the login-item and `AF_UNIX`-on-Windows items in its native acceptance checklist.
- `docs/STATUS.md` records the branch, the decisions, the deviations and the measurements the user takes.

## Errors and edge cases

- **`--background` with `enabled = false`.** A stale login item fired after the feature was turned off. The process logs, removes the leftover login item, and exits 0. Self-healing.
- **The socket cannot be bound** (permissions, a path too long, `AF_UNIX` unsupported). Log a warning and continue as an ordinary non-resident app. Residency is an optimization; failing to get it is never fatal.
- **A request arrives with a bad token.** Refuse with `token`, log, keep listening. The launcher starts standalone.
- **A request arrives while a window is already open.** Raise the last active window rather than opening a second one — the same handler the reopen event uses. A window is opened only when none exists.
- **The requested working directory no longer exists or is not a directory.** Open the window at the user's home directory, as `Main` does today.
- **Jasper is uninstalled while a login item remains.** launchd fails the job at login and, with `KeepAlive` false, stops. Noisy in the system log, harmless. Unavoidable from inside an app that is no longer there.
- **The resident process is killed externally.** Its socket file is left behind and the next launch unlinks it under the lock.
- **Sleep, wake, display changes, fast user switching.** A process resident across these is new exposure for Swing. Nothing in the design addresses it; it is on the manual checklist.

## Testing

Headless, in `./gradlew check`:

- Handoff round-trip over a socket in a temporary directory, in one JVM: request accepted, `ok` returned, the handler invoked with the decoded working directory.
- Every refusal: a wrong token, a differing code source, a differing mtime, a malformed line, an unknown protocol version.
- Stale-socket recovery: a socket file with nothing listening is unlinked and rebound; a live one is not.
- Two concurrent bind attempts under the lock: exactly one owner.
- `LoginItem.plan` for macOS and Windows, enabled and disabled, with an install path containing spaces, and the empty plan for a non-packaged code source.
- `ConfigLoader`: the key defaults to `false`, a non-boolean is rejected with a clear message, an unknown key in `[background]` is reported.
- `JasperApplication`: with residency on, closing the last window does not run `terminate` and does not close `history`, `shellHistory` or `snippets`; `quit()` does all of it. With residency off, today's behaviour is unchanged.
- `AppArguments`: `--background` parses, duplicates are rejected, the usage string is updated.

The user's, on the desktop, because none of it can be verified headlessly:

- Cold launch time before, and warm launch time after — the number this whole feature is judged on.
- Idle RSS of the resident process, which is what it costs.
- The macOS Dock icon persisting with zero windows, and a Dock click producing a window.
- Login start on macOS and on Windows, from a real packaged install.
- `AF_UNIX` on Windows at all.
- Quit really quitting, and the next launch being cold.
- Survival across sleep/wake and a monitor change.

## Out of scope

- A global hotkey or a drop-down/Quake-style window. Java has no portable global hotkey without native code, and it is a separate feature.
- A tray or menu-bar icon.
- Pre-spawning a shell or a PTY.
- A pre-built hidden window, and the invisible anchor frame that would pre-warm peer creation.
- Any Linux login-item support. The residency and handoff mechanisms are portable and would work; only `LoginItem` is macOS and Windows.
- Crash supervision or automatic restart of a resident process.
