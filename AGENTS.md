# Instructions for coding agents working on Jasper

Start with [`docs/STATUS.md`](docs/STATUS.md): current state, open items, deferred findings and next steps. The design spec (`docs/superpowers/specs/2026-09-10-jasper-phase-1-terminal-design.md`) and its approved [terminal](docs/superpowers/specs/2026-09-20-jasper-terminal-refactor-design.md) and [app/Buddy](docs/superpowers/specs/2026-09-20-jasper-app-buddy-refactor-design.md) refactor amendments are the binding authorities; plans live in `docs/superpowers/plans/`.

## Build and test

- Java 25 on the **JetBrains Runtime** (JBR) 25. The Gradle toolchain requires vendor JetBrains and finds it automatically (on the dev Mac: `~/Library/Java/JavaVirtualMachines/jbrsdk-25.0.4.1-osx-aarch64-b583.48`). Use the wrapper: `./gradlew`, never a system `gradle`.
- `./gradlew check` runs everything (headless). For exact counts read the XML in `*/build/test-results/test/`.
- Terminal onboarding: [module README](jasper-terminal/README.md), [architecture](docs/terminal-architecture.md), [maintenance recipes](docs/terminal-maintenance.md). Supported packages are session, view, config, search and rendering; only the documented allowlist is app-facing. `internalAccess()` and all internal types are unsupported. `verifyTerminalArchitecture` enforces this boundary and an acyclic package graph; `check` includes JavaDoc doclint and compiled documentation examples.
- Tests are headless: `TerminalView` is a lightweight component; drive it with its package-private `handleKey` / `handleMouse`; inject the clipboard and link opener (`setClipboard`, `setLinkOpener`). View tests live in `dev.jasper.terminal.view`; session fixtures live in test-only `internal.emulation.EmulationFixture` / `FakeConnector`, with `testsupport.Await`. Session tests use `FakeConnector` (`feed`, `finish`, `written`, `lastResize`) and `Await.until(condition, description)`.

## Never do these without the user

- **Do not launch the GUI** (`./gradlew :jasper-app:run`, or anything that opens a window and starts the user's login shell on their desktop) from an unattended agent. Hand GUI checks to the user. The benchmark (`./gradlew :jasper-app:bench`) opens a window for a few seconds; run it only when asked, and skip it while a game or VM is running (the user's Mac once froze while Minecraft was running — unrelated, but be careful).
- Do not commit directly on `main`: work on a branch and merge when the user agrees. End commit messages with a `Co-Authored-By:` trailer. The user authorized `origin` at `https://github.com/an0nn30/moray.git`; ask before pushing unless the session already authorizes it.

## Architecture rules (from the spec and the plans' Global Constraints)

- Modules: `jasper-terminal` (`dev.jasper.terminal`), `jasper-app` (`dev.jasper.app`) and JDK-only `jasper-buddy` (`dev.jasper.buddy`). Both libraries are independent of the application and each other. The [approved app/Buddy amendment](docs/superpowers/specs/2026-09-20-jasper-app-buddy-refactor-design.md) defines the supported Buddy facade/values; app production code never imports Buddy internals. `verifyApplicationArchitecture` checks both full package DAGs and vendor-free Buddy signatures. `jasper-sdk` (`dev.jasper.sdk`, JDK-only) is the plugin API and `jasper-sdk-testkit` its fake and contract suite; in-repo plugins live under `plugins/` and compile against the SDK only. SDK types appear in the app only inside `dev.jasper.app.plugins`. `verifySdkArchitecture` and `verifyPluginArchitecture` enforce this. Plugins contribute palette scopes through `dev.jasper.sdk.palette`; `dev.jasper.app.contributions` carries them and depends on `dev.jasper.app.palette` for that. History and Snippets are bundled plugins under `plugins/`; the app has no shell-history or snippet code. Credential Vault is a bundled plugin too (`plugins/vault`, bundling BouncyCastle); other plugins reach it through `dev.jasper.vault.api` only. Remote is bundled too (`plugins/remote`, bundling Apache MINA sshd); it consumes only the Vault API. A plugin's jars, settings file and data live under `<home>/plugins/<id>/` (`jars/`, `<id>.toml`, `data/`); the app has no `plugin-data` directory and reads plugin settings from those files, not from `config.toml`.
- JediTerm (`org.jetbrains.jediterm:jediterm-core:3.76`, from `https://packages.jetbrains.team/maven/p/ij/intellij-dependencies`, not Maven Central) is an `implementation` dependency of `jasper-terminal` only. **No public method in `jasper-terminal` takes or returns a JediTerm type.** Do not add `jediterm-ui` or `jediterm-pty`.
- `TerminalSession.attach` runs a connection instead of a child process; its transport lives in `internal.transport` (JDK only), writes and resizes only enqueue, and `DirectoryProvenance` decides whether an OSC 7 report is local (local process and an empty, `localhost` or exactly matching host) or remote; the local `Path` surface never carries a remote path.
- No interface without two real implementations (SDK interfaces are implemented by the app and by the testkit). The plugin API is the SDK; the user lifted "no plugin API in phase 1" on 2026-09-21 ([design](docs/superpowers/specs/2026-09-21-jasper-plugin-sdk-design.md)). No abstraction over "emulator backends".
- Child processes get `TERM=xterm-256color` and `COLORTERM=truecolor`.
- Rows: **absolute row** = `discardedLines + historyLines + screenRow` (screen row 0 = top of the live screen; negative buffer rows are scrollback). Selections, prompt marks, search matches and the scrolled-back viewport use absolute rows.
- Threading: JediTerm runs on the session's reader thread; the view runs on the Event Dispatch Thread. Every read of buffer state takes `TerminalTextBuffer`'s lock (it is re-entrant); keep work under the lock small and bounded (the regex search deliberately runs outside it).

## Verified jediterm-core 3.76 behaviour (do not re-derive; test if in doubt)

- `JediTerminal.getCursorX()/getCursorY()` are 1-based. A wide BMP character is followed by a continuation cell holding `CharUtils.DWC` (U+E000); a supplementary character (emoji) occupies two cells holding its two UTF-16 surrogates. Default colours are `null` in `TextStyle`. `getLine(0)` is the top screen row, `getLine(-1)` the newest scrollback line. `JediTerminal` locks the buffer internally for every write.
- `getCodeForKey(VK, InputEvent.*_DOWN_MASK)` handles application cursor mode, function keys and modifiers. DECSCUSR 0 arrives as `BLINK_BLOCK` (hence Jasper's rewrite); RIS arrives as `null`.
- **Every CSI ending in `q` is read as DECSCUSR**, private markers and the missing space intermediate ignored: `CSI > q` (XTVERSION, which tmux sends at session start), `CSI Ps q` (DECLL) and `CSI ? q` all arrive as `BLINK_BLOCK` and would outrank the configured cursor shape. JediTerm replies to none of them, so `ShellIntegrationFilter` drops any `q`-final CSI without a space intermediate. `CSI c` and `CSI > c` are answered normally and leave the cursor alone.
- OSC: 0/1/2 set the title; **7 is swallowed; 133 is dropped**; 8 creates hyperlinks only if `setUrlHyperlinkFilter` is set and returns one `LinkResultItem` spanning `0..uri.length()`; **104 and 1341 reach `processCustomCommand` in emulator order** with arguments split on `;`. Jasper's `ShellIntegrationFilter` rewrites OSC 7, OSC 133 and DECSCUSR 0 into `OSC 1341;jasper;…`.
- Mouse: `onMouseEvent(column, row, …)` takes 0-based cells and encodes the report itself; SGR right press at (0,0) = `ESC[<2;1;1M`; it already drops MOVED/DRAGGED in click-only mode (1000); a press with the Shift flag sends nothing.
- `linesDiscardedFromHistory` fires when scrollback overflows; ED 3 and RIS fire `historyCleared` (ED 2 does not); **a width change reflows soft-wrapped lines**; while the alternate screen is active `getHistoryLinesCount()` is 0.

## Source hygiene

- Never put raw control, private-use or unpaired surrogate characters in source. Write Java escapes (for example `"\033[1;3D"`) or ASCII casts (`(char) 0xF113`, `(char) 0xD83D`). JediTerm's continuation marker U+E000 is written as the Java escape (backslash, `u`, `E000`), never as the raw character.
- macOS `grep` has no `-P`. Check files with Python instead:

```
python3 - <<'PY'
import pathlib, sys
for name in sys.argv[1:] or [str(p) for root in ("jasper-terminal/src", "jasper-app/src", "jasper-buddy/src", "jasper-sdk/src", "jasper-sdk-testkit/src", "plugins") for p in pathlib.Path(root).rglob("*.java")]:
    text = pathlib.Path(name).read_text(encoding="utf-8")
    bad = sum(1 for c in text if 0xD800 <= ord(c) <= 0xDFFF or 0xE000 <= ord(c) <= 0xF8FF or (ord(c) < 0x20 and c not in "\n\t\r") or ord(c) == 0x7f)
    if bad: print(name, "bad chars:", bad)
PY
```

## Workflow used so far

Plans are written with `superpowers:writing-plans` (complete code in every step, TDD, one commit per task) and executed with `superpowers:subagent-driven-development` (per-task implementer + reviewer, fix rounds, a final whole-branch review). Keep that shape for plans 3 and 4: one plan per runnable deliverable, and record any deviation from plan text in the plan's status banner and in `docs/STATUS.md`.

## App and Buddy contributor entry points

Use the [documentation index](docs/README.md) for all three modules, including
[Buddy architecture](docs/buddy-architecture.md) and [Buddy maintenance](docs/buddy-maintenance.md).
Plugin work starts with [SDK architecture](docs/sdk-architecture.md), [plugin authoring](docs/plugin-authoring.md) and the [SDK README](jasper-sdk/README.md).
Start with [app onboarding](jasper-app/README.md), [Buddy embedding](jasper-buddy/README.md),
[architecture](docs/app-architecture.md) and [maintenance recipes](docs/app-maintenance.md).
App public visibility is for internal feature collaboration, not a plugin ABI. Package-info
files name allowed outgoing dependencies and lifetime owners. The app/Buddy refactor uses
the user-approved native inline workflow with one independent final review. Pure test
fixtures live in testsupport; owner-package test access bridges stay out of production jars.
