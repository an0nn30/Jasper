# Jasper WSL hosts and shell profiles

**Date:** 2026-09-28  
**Status:** Approved in conversation on 2026-09-28 (Remote panel group, detected shell profiles, ConPTY in scope; section 9 resolved). Plans A and B written. Plans A and B are implemented on `worktree-wsl-hosts` (not merged).  
**Baseline:** `7fd829cc` on `main`, branch `worktree-wsl-hosts`.  
**Scope:** On Windows, detect installed WSL 1 and WSL 2 distributions, list them for connection in the Remote hosts panel (MobaXterm-style), and let the user pick any detected shell, including a WSL distro, as the default for new panes. Includes switching Windows PTYs from winpty to ConPTY.

## 1. Intent and decisions

The user wants WSL distributions to appear as connectable entries next to SSH hosts,
as MobaXterm shows them in its session list, and wants to be able to make a WSL
distro the default shell on Windows. Decisions made in conversation:

1. **The list lives in the Remote hosts panel** as a fixed, read-only "WSL" group,
   and in the Sessions toolbar menu and the Remote palette scope. It does not
   pretend distros are SSH hosts.
2. **The default shell becomes a detected profile.** The app detects shells
   (PowerShell, PowerShell 7, Command Prompt, each WSL distro) and the Shell settings
   page offers them in a dropdown. The existing `program`/`args` settings remain as
   the "Custom" profile.
3. **ConPTY is part of this work.** Windows sessions currently run on winpty, which
   does not pass OSC through (STATUS.md, three-platform CI), so titles, OSC 7 and
   shell integration would not work in WSL tabs.

## 2. Findings that shape the design

- `LocalSpec` carries only a working directory; a plugin cannot open a local tab
  running a chosen command (sdk-architecture.md lists this as not implemented).
  Remote's SSH tabs are provided sessions over MINA channels; a plugin cannot reach
  the app's pty4j, so it cannot give `wsl.exe` a real PTY on its own.
- `DefaultShell` hard-codes `powershell.exe -NoLogo` on Windows; the only override is
  the free-text `terminal.shell.program` plus `args`.
- `PtyChild` never calls `setUseWinConPty`, so pty4j 0.13.10 uses winpty.
- A WSL shell reports OSC 7 as `file://<hostname>/home/x`. WSL's hostname defaults to
  the Windows computer name, which `LocalHostNames` includes, so `DirectoryProvenance`
  classifies it as **local** and produces `\home\x`. New tabs and splits inheriting
  that directory fail `ShellLauncher`'s `Files.isDirectory` check.
- Windows environment variables reach Linux only through `WSLENV`, so `TERM_PROGRAM`,
  `COLORTERM` and `JASPER_*` do not arrive today.
- Detection facts, measured on the user's machine:
  - `wsl.exe -l -v` writes UTF-16LE.
  - In `HKCU\Software\Microsoft\Windows\CurrentVersion\Lxss\{guid}`, the `Version`
    value is the **filesystem** version, not the WSL version. The user's Ubuntu has
    `Version=2`, `Flags=7` and is WSL 1 per `wsl -l -v`.
  - WSL 2 is `Flags & 0x8` (VM mode). `DefaultDistribution` on the `Lxss` key names
    the default distro's GUID.

## 3. Shell profiles (app, `dev.jasper.app.launch`)

A `ShellProfile` is an id, a display name, a kind and the argv to run:

| Kind | Id | Command |
|------|----|---------|
| PowerShell | `powershell` | `powershell.exe -NoLogo` |
| PowerShell 7 | `pwsh` | `pwsh.exe -NoLogo` (when found on `PATH` or in `%ProgramFiles%\PowerShell\*`) |
| Command Prompt | `cmd` | `%ComSpec%` (falls back to `cmd.exe`) |
| WSL | `wsl:<distro>` | `wsl.exe -d <distro> --cd <dir>` |
| Login shell (macOS/Linux) | `login` | today's `DefaultShell` behaviour |
| Custom | `custom` | `terminal.shell.program` + `args` |

- **`WslDistributions`** detects distros from the `Lxss` registry keys through
  `reg.exe query` (no JNA dependency in the app), reading `DistributionName`,
  `Flags`, `State` and `DefaultDistribution`. The fallback is `wsl.exe -l -v`,
  decoded as UTF-16LE. Each distro is `WslDistro(name, version 1|2, isDefault)`.
  `docker-desktop` and `docker-desktop-data` are hidden. Parsing is pure and
  unit-tested against captured outputs. The process call runs off the EDT with a
  timeout and is cached; the Settings page and the Remote panel can refresh it.
- **`ShellProfiles`** lists the profiles for the current OS: fixed ones plus the
  detected WSL distros.
- **Config:** a new `terminal.shell.profile` setting (string, `Applies.NEW_PANES`).
  The default is empty, meaning `powershell` on Windows and `login` elsewhere, so
  existing configs keep working. A non-empty `program` with an empty `profile`
  resolves to `custom`, so current users' `program` settings keep winning. An
  unknown or uninstalled profile id falls back to the platform default with a
  status-bar warning, not a failed pane.
- **Settings UI:** the Shell page gets a "Profile" dropdown built on `Editors.Combo`,
  fed by `ShellProfiles`. WSL entries read "Ubuntu (WSL 1)". Program/Args are
  enabled only when "Custom" is chosen.
- `LaunchSettings.resolve` takes the resolved profile instead of reading
  `program`/`args` directly. `label()` uses the profile's display name, so a tab reads
  "Ubuntu", not "wsl.exe".

### WSL launch details

- **Command:** `wsl.exe -d <distro> --cd <dir>`. `<dir>` is `~` by default. It is
  the Linux path when the directory is a WSL UNC path (section 5), and the Windows
  path otherwise (`--cd` accepts Windows paths). The PTY's own working directory is
  the user's home, so `ShellLauncher`'s `Files.isDirectory` check is not applied to
  the Linux side.
- **Environment:** add `WSLENV` entries (appended to any existing value) for
  `TERM_PROGRAM`, `JASPER_TERMINAL`, `COLORTERM` and, when set,
  `JASPER_SHELL_INTEGRATION/p`. The `/p` flag makes WSL translate the Windows script
  directory to `/mnt/c/...`. `TERM` is set by `wsl.exe` from the console, which reads
  `xterm-256color` under ConPTY; this must be checked and forced through `WSLENV`
  if needed.
- **Shell integration:** `LaunchSettings.inject` matches `zsh`/`bash`/`fish` by
  basename and cannot know the distro's login shell. For WSL, auto-injection is
  **out of scope for v1**. The translated `JASPER_SHELL_INTEGRATION` makes manual
  sourcing work, and docs/shell-integration.md documents the one line to add to
  `~/.bashrc` / `~/.zshrc`.

## 4. ConPTY (jasper-terminal)

- `PtyChild` calls `setUseWinConPty(true)` on Windows.
- **Verify:**
  - OSC 0/2/7/133/1341 pass through, and resize works.
  - The `=C:` environment handling still works.
  - `ForegroundJobResolver` stays inert on Windows.
- ConPTY rewrites and re-renders output. The known consequences to test are:
  - it may reorder or regenerate SGR
  - it may emit its own cursor-visibility and title sequences
  - it answers DSR itself
- Record the results in docs/windows-startup-verification.md.
- **Revisit the existing Windows test failures** listed there (`ShellIntegrationSessionTest`,
  `ShellCommandTrackerTest` host cases) under ConPTY.
- **Risk:** if ConPTY turns out to break something serious, keep a hidden
  `terminal.windows_pty = "winpty"` escape hatch. Decide during implementation;
  do not add it up front.

## 5. Directories from WSL tabs (jasper-terminal + app)

`jasper-terminal` must stay WSL-agnostic. `SessionLaunchOptions` gains an optional
**local path mapper**: a function from a reported `(host, path)` to an
`Optional<Path>`. `DirectoryProvenance` uses it for reports it would classify as
local; when the mapper returns empty, the report is ignored (no directory), never
turned into `\home\x`. The default mapper is today's behaviour.

The app's WSL mapper for distro `D`:

- `/mnt/<letter>/rest` becomes `<LETTER>:\rest`.
- Anything else becomes `\\wsl.localhost\D\path`. `\\wsl$\D\...` is also accepted
  when reversing.

The reverse direction, used when a new pane starts in a WSL UNC directory: the Linux
path is passed to `--cd`. When a non-WSL profile inherits a `\\wsl.localhost\...`
directory, it starts in the user's home instead, because PowerShell and cmd
misbehave in UNC directories and touching the share can start the WSL 2 VM.

Breadcrumbs, the status bar and plugin `PaneInfo.directory` then show real, openable
Windows paths.

## 6. SDK 0.8.6 (`dev.jasper.sdk.terminal`)

- `record ShellProfileInfo(String id, String name, Kind kind, Optional<String> detail)`,
  where `Kind` is `LOGIN_SHELL, LOCAL_SHELL, WSL, CUSTOM`, mirroring the app. For WSL, `detail` holds
  "WSL 1" or "WSL 2", with " · default" appended for the default distribution.
- `Terminals.profiles()` returns `List<ShellProfileInfo>` (EDT, no capability beyond
  `terminal.open`). `Terminals.onProfilesChanged(Runnable)` returns a `Subscription`.
- `LocalSpec` gains `Optional<String> profile`; empty means the user's default. The
  existing constructor and factories are kept. An unknown profile id makes
  `openTab`/`split` return empty and log a warning.
- **Why profiles and not arbitrary commands:** plugins open only shells the app
  detected, which keeps "Explicit commands in `LocalSpec`" a separate, later decision.
- **Wiring:**
  - `HostedTerminals` maps the new field.
  - `OpenSpec.Local` gains the profile.
  - `FakeTerminals` implements `profiles()` with a settable list.
  - The contract suite covers a known profile, an unknown one, and the default.
- Bump `JasperSdk.VERSION` to 0.8.6 and raise the `sdk` range of the bundled plugins
  that need it (Remote).

## 7. Remote panel "WSL" group (plugins/remote)

- **Model.** A new `HostRows.Row.Distro(ShellProfileInfo profile, int sessions)`
  variant. `RemoteHost`, `HostStore` and `hosts.toml` are untouched, and WSL entries
  are never persisted.
- **Placement.**
  - `HostRows.rows` adds a "WSL" group only when `profiles()` contains WSL entries,
    which means Windows with WSL installed.
  - The group sorts **first**, before the SSH groups, like MobaXterm.
  - Its collapsed state uses the key `wsl` in `PanelState`.
  - The search query matches distro names.
- **Rendering.**
  - Row icon: `BundledIcon.TERMINAL_TOOLWINDOW_TERMINAL`; there is no Linux icon.
  - Badge: "WSL 1" or "WSL 2", plus "default" for the default distro.
  - Session dot and count, as for hosts.
  - The card shows the distro name, version and the command that will run.
- **Actions.**
  - Double-click opens a tab: `openTab(window, OpenRequest.local(new LocalSpec(empty, Optional.of(id))))`.
  - The context menu offers "Open in new tab" and "Open in split" (no "Use as
    default shell"; see section 9).
  - There is no Edit, Duplicate, Delete, Favorite or SFTP for distros.
- **Session counting.** Remote tracks panes it opened for a distro by pane id, as it
  already does for hosts. It uses `PaneInfo.kind == LOCAL` plus its own bookkeeping,
  not SSH channels. The status item stays "N SSH sessions".
- **Other surfaces.**
  - `SessionsToolbar` lists distros under a "WSL" separator.
  - `RemoteScope` returns rows `wsl.<distro>` tagged "WSL".
- **Copy changes.**
  - Panel title: "SSH hosts" becomes "Sessions".
  - The empty text is unchanged when WSL is absent.
  - Update docs/remote.md and its native acceptance checklist.
- The manifest already has `terminal.open`, so no new capability is needed.

## 8. Testing

- **Unit tests:**
  - registry and `wsl -l -v` parsing: UTF-16LE, WSL 1 and WSL 2 flags, the default
    distro, Docker filtering, no WSL installed, and `wsl.exe` missing
  - profile resolution and fallback
  - `WSLENV` merging
  - the WSL path mapper in both directions
  - `DirectoryProvenance` with a mapper
  - `HostRows` with a WSL group
  - SDK contract tests for profiles
- **Headless and cross-platform:** all detection sits behind a pure parser plus a
  small process runner, so tests run on macOS and Linux CI.
- **GUI and native acceptance** is handed to the user, per AGENTS.md:
  - WSL 1 and WSL 2 tabs open, and title and OSC 7 work under ConPTY
  - a split from a WSL tab starts in the same Linux directory
  - choosing a WSL distro as the default shell in Settings works
  - PowerShell regressions under ConPTY

## 9. Resolved questions (user accepted the proposals, 2026-09-28)

1. **Splits inherit the pane's profile; New Tab uses the default profile.** The app
   records the profile id each local pane was launched with.
2. **No "Use as default shell" item in the Remote panel in v1.** The SDK can neither
   change a core setting nor open a settings page, so users choose the default under
   Settings → Shell. Section 7's context menu is therefore "Open in new tab" and
   "Open in split" only.
3. **No running/stopped distro state in v1.**
4. **No automatic shell integration inside WSL in v1.** The manual `source` line is
   documented instead.

## 10. Delivery

Two plans, one per runnable deliverable:

- **Plan A** (`docs/superpowers/plans/2026-09-28-jasper-wsl-a-profiles.md`): ConPTY,
  the terminal path mapper, WSL detection, shell profiles, WSL launch, per-pane
  profile inheritance and the Settings dropdown. Result: a WSL distro can be the
  default shell.
- **Plan B** (`docs/superpowers/plans/2026-09-28-jasper-wsl-b-remote.md`): SDK 0.8.6
  profiles, the app's plugin wiring, the testkit, and the Remote panel's WSL group,
  Sessions menu and palette rows. Depends on plan A.
