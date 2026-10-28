# Instructions for coding agents working on Jasper

Start with [`docs/STATUS.md`](docs/STATUS.md): current state, open items, deferred findings and next steps. The design spec (`docs/superpowers/specs/2026-09-10-jasper-phase-1-terminal-design.md`) and its approved [terminal](docs/superpowers/specs/2026-09-20-jasper-terminal-refactor-design.md) and [app/Buddy](docs/superpowers/specs/2026-09-20-jasper-app-buddy-refactor-design.md) refactor amendments are the binding authorities; plans live in `docs/superpowers/plans/`. The guides indexed in [docs/README.md](docs/README.md) describe the system as it is.

## Build and test

- Java 25 on the **JetBrains Runtime** (JBR) 25. The Gradle toolchain requires vendor JetBrains and finds it automatically (on the dev Mac: `~/Library/Java/JavaVirtualMachines/jbrsdk_jcef-25.0.4.1-osx-aarch64-b635.75`). Use the wrapper: `./gradlew`, never a system `gradle`.
- `./gradlew check` runs the build's tests and checks (headless). For exact counts read the XML in `*/build/test-results/test/`. The example external plugin is not part of `check`; CI builds it on Linux afterwards. After an SDK change also run `./gradlew -p docs/examples/external-plugin -PjasperSdkDir="$PWD/build/sdk-release" check pluginZip`, which builds it from the SDK jars `check` writes to `build/sdk-release`.
- `check` also builds Jasper's jpackage launcher from `packaging/launcher` and runs its native tests, and builds and tests the `jasper` command from `packaging/cli`, so it needs the host's C/C++ compiler: the Xcode command line tools, `gcc`/`g++`, or MSVC (found through `vswhere`). On macOS it also compiles and tests the three Objective-C bridges (`libjasper-macos.dylib`, `libjasper-glass.dylib` and `libjasper-appearance.dylib`, from `gradle/mac-native.gradle`, `mac-glass.gradle` and `mac-appearance.gradle`). AOT training gives the launcher an empty `jasper.vmoptions`. See [the launcher README](packaging/launcher/README.md). `:jasper-ui:check` runs the theme converter's tests (`checkThemeTool`), which need Python 3.11 to 3.13 (3.14 fails `test_deeply_nested_input_is_a_message`); `-Pjasper.python=<interpreter>` picks one.
- Terminal onboarding: [module README](jasper-terminal/README.md), [architecture](docs/terminal-architecture.md), [maintenance recipes](docs/terminal-maintenance.md). Supported packages are session, view, config, search and rendering; only the documented allowlist is app-facing. `internalAccess()` and all internal types are unsupported. `verifyTerminalArchitecture` enforces this boundary and an acyclic package graph; `check` includes JavaDoc doclint and compiled documentation examples.
- UI onboarding: [module README](jasper-ui/README.md), [architecture](docs/ui-architecture.md), [maintenance recipes](docs/ui-maintenance.md).
- Tests are headless: `TerminalView` is a lightweight component; drive it with its package-private `handleKey` / `handleMouse`; inject the clipboard and link opener (`setClipboard`, `setLinkOpener`). View tests live in `dev.jasper.terminal.view`; session fixtures live in test-only `internal.emulation.EmulationFixture` / `FakeConnector`, with `testsupport.Await`. Session tests use `FakeConnector` (`feed`, `finish`, `written`, `lastResize`) and `Await.until(condition, description)`. A test that needs a folder takes JUnit's `@TempDir`; a helper that must make one in `java.io.tmpdir` deletes it (`dev.jasper.app.testsupport.TempFolders.delete`), and `TempLeaks` beside it lets a test prove it did.

## Never do these without the user

- **Do not launch the GUI** (`./gradlew :jasper-app:run`, or anything that opens a window and starts the user's login shell on their desktop) from an unattended agent. Hand GUI checks to the user. Every benchmark opens native windows (`./gradlew :jasper-app:bench`, `:jasper-app:memoryBench`, and `tools/benchmarks/run-macos.sh` for throughput, memory and start-up or `run-windows.ps1` for throughput and memory); run one only when asked, and skip it while a game or VM is running (the user's Mac once froze while Minecraft was running — unrelated, but be careful).
- **Do not train the AOT cache without asking.** On macOS, `./gradlew :jasper-app:packageDist`, `:jasper-app:trainAotCache`
  and any packaging task with `-Pjasper.aot=true` launch the packaged app once (a window for a few seconds, no Dock icon)
  to record `Contents/app/jasper.aot`. Ask the user first. `packageApp` and `verifyPackage` without that property stay
  headless.
- Do not commit directly on `main`: work on a branch and merge when the user agrees. End commit messages with a `Co-Authored-By:` trailer. The user authorized `origin` at `https://github.com/an0nn30/Jasper.git`; ask before pushing unless the session already authorizes it.

## Architecture rules

- Modules: `jasper-terminal` (`dev.jasper.terminal`), `jasper-ui` (`dev.jasper.ui`, the theme engine and Swing UI kit), `jasper-app` (`dev.jasper.app`) and JDK-only `jasper-buddy` (`dev.jasper.buddy`). The three libraries are independent of the application and each other. `verifyUiArchitecture` checks that `jasper-ui` uses no other Jasper module, that its `theme`, `model` and `kit` packages use only the JDK without Swing, and that its package graph follows the package-info files and is acyclic. [Buddy's README](jasper-buddy/README.md#supported-api) defines the supported Buddy facade and values; app production code never imports Buddy internals. `verifyApplicationArchitecture` checks both full package DAGs and vendor-free Buddy signatures. `jasper-sdk` (`dev.jasper.sdk`, JDK-only) is the plugin API and `jasper-sdk-testkit` its fake and contract suite; in-repo plugins live under `plugins/` (each the module `:jasper-plugin-<dir>`) and compile against the SDK and the exported API packages of the plugins they require, which `declaredImports` in `gradle/plugin-architecture.gradle.kts` lists per plugin. SDK types appear in the app only inside `dev.jasper.app.plugins`. `verifySdkArchitecture` and `verifyPluginArchitecture` enforce this. `verifyPluginArchitecture` also runs `gradle/architecture/PluginPresentationCheck.java`, which refuses presentation code in plugin classes: constructing ordinary Swing controls, painting, and setting colours, fonts, borders, sizes, layout, alignment or icons. The Editor's `DocumentArea`, `VimTextArea`, `EditorTextView`, `EditorTextAreaUI`, `PreviewPane`, `PreviewKit`, `PreviewImageView`, `EditorTheme` and `SystemClipboard` have operation-specific exceptions ([SDK architecture](docs/sdk-architecture.md)). `dev.jasper.app.presentation` holds the presentation a process runs with (the kit file parser, the attempt record, the projections); `PluginSelection` chooses it once on the bootstrap thread. Plugins contribute palette scopes through `dev.jasper.sdk.palette`; `dev.jasper.app.contributions` carries them and depends on `dev.jasper.app.palette` for that. History and Snippets are bundled plugins under `plugins/`; the app has no shell-history or snippet code. Snippets exports `dev.jasper.snippets.api`, which History optionally uses. Credential Vault is a bundled plugin too (`plugins/vault`, bundling BouncyCastle); other plugins reach it through `dev.jasper.vault.api` only. Remote is bundled too (`plugins/remote`, bundling Apache MINA sshd); it optionally consumes the Vault API and the Editor API (`dev.jasper.editor.api`, named in `dev.jasper.remote.edit`'s `RemoteDocument`, `SavedRemoteDocument` and `RemoteEditing` and in `RemotePlugin`'s lookup of the `Editor` service), and exports `dev.jasper.remote.api`. Editor is bundled too (`plugins/editor`, bundling RSyntaxTextArea and CommonMark Java); it exports `dev.jasper.editor.api`, and other plugins reach it through that package only. File Server (`plugins/fileserver`, bundling Apache MINA sshd, Apache FtpServer and BouncyCastle) optionally consumes the Vault API; Project Index (`plugins/indexer`) optionally consumes the Editor and Remote APIs; Sample (`plugins/sample`) holds the compiled examples of `docs/plugin-authoring.md`. Enterprise is bundled too (`plugins/enterprise`, off by default): its look is descriptor data (its kit file, the IBM Plex fonts and the Enterprise theme), and its code does nothing. Plugins read the active look through the SDK (`Appearance.presentation()`). A plugin's jars, settings file and data live under `<home>/plugins/<id>/` (`jars/`, `<id>.toml`, `data/`); the app reads plugin settings from those files, not from `config.toml`.
- `dev.jasper.app.dock` (the tool window dock model: slots, `DockState`, `DockGeometry`, and the tab and pane drag's `WorkspaceDropResolver`) is JDK-only: no Swing, from `java.awt` only `Rectangle` and `Point`, and of Jasper's other packages only `dev.jasper.app.layout`. `dev.jasper.app.layout` (the workspace layout model: a window's tabs, each tab's split panes and the one item each pane holds, which `workspace.toml` saves) is JDK-only with nothing from `java.awt`. `verifyApplicationArchitecture` enforces both, and that `MainWindow` is made only in `WindowManager.ensureMain`, `WindowManager` only in `JasperApplication`'s constructor and `WindowFrame` only in `JasperApplication.frameOnScreen` and `rehearsalWindow`. `dev.jasper.app.vmoptions` is JDK-only too (no Swing or AWT; `com.sun.management` is allowed), and only `dev.jasper.app.Main` sits in the root `dev.jasper.app` package. `dev.jasper.app.cygwin` uses only the JDK and JNA Platform: `compileCygwinBundle` (`gradle/cygwin.gradle`) compiles it, with `packaging/windows/cygwin`'s tools and no other Jasper code, into `cygwin-tool.jar`, the helper the Windows installers and Cygwin package management run.
- JediTerm (`org.jetbrains.jediterm:jediterm-core:3.76`, from `https://packages.jetbrains.team/maven/p/ij/intellij-dependencies`, not Maven Central) is an `implementation` dependency of `jasper-terminal` only. **No public method in `jasper-terminal` takes or returns a JediTerm type.** Do not add `jediterm-ui` or `jediterm-pty`.
- Every external jar the app or a bundled plugin ships, and every font a plugin bundles, needs a component in `jasper-app/src/main/resources/dev/jasper/app/about/licenses/notices.toml`; `verifyThirdPartyNotices` (in `check`) fails otherwise. See [Adding or bumping a dependency](docs/app-maintenance.md#adding-or-bumping-a-dependency).
- `TerminalSession.attach` runs a connection instead of a child process; its transport lives in `internal.transport` (JDK only), writes and resizes only enqueue, and `DirectoryProvenance` decides whether an OSC 7 report is local (local process and an empty, `localhost` or exactly matching host) or remote; the local `Path` surface never carries a remote path.
- No interface without two real implementations (SDK interfaces are implemented by the app and by the testkit). The plugin API is the SDK. No abstraction over "emulator backends".
- Child processes get `TERM=xterm-256color` and `COLORTERM=truecolor`.
- Rows: **absolute row** = `discardedLines + historyLines + screenRow` (screen row 0 = top of the live screen; negative buffer rows are scrollback). Selections, prompt marks, search matches and the scrolled-back viewport use absolute rows.
- Threading: JediTerm runs on the session's reader thread; the view runs on the Event Dispatch Thread. Every read of buffer state takes `TerminalTextBuffer`'s lock (it is re-entrant); keep work under the lock small and bounded (the regex search deliberately runs outside it).

## Verified jediterm-core 3.76 behaviour (do not re-derive; test if in doubt)

- `JediTerminal.getCursorX()/getCursorY()` are 1-based. A wide BMP character is followed by a continuation cell holding `CharUtils.DWC` (U+E000); a supplementary character (emoji) occupies two cells holding its two UTF-16 surrogates. Default colours are `null` in `TextStyle`. `getLine(0)` is the top screen row, `getLine(-1)` the newest scrollback line. `JediTerminal` locks the buffer internally for every write.
- `getCodeForKey(VK, InputEvent.*_DOWN_MASK)` handles application cursor mode, function keys and modifiers. DECSCUSR 0 arrives as `BLINK_BLOCK` (hence Jasper's `cursor-reset`); RIS arrives as `null`.
- **Every CSI ending in `q` is read as DECSCUSR**, private markers and the missing space intermediate ignored: `CSI > q` (XTVERSION, which tmux sends at session start), `CSI Ps q` (DECLL) and `CSI ? q` all arrive as `BLINK_BLOCK` and would outrank the configured cursor shape. JediTerm replies to none of them, so `ShellIntegrationFilter` drops any `q`-final CSI without a space intermediate. `CSI c` and `CSI > c` are answered normally and leave the cursor alone.
- OSC: 0/1/2 set the title; **7 is swallowed; 133 is dropped**; 8 creates hyperlinks only if `setUrlHyperlinkFilter` is set and returns one `LinkResultItem` spanning `0..uri.length()`; **104 and 1341 reach `processCustomCommand` in emulator order** with arguments split on `;`. Jasper's `ShellIntegrationFilter` rewrites OSC 7 and OSC 133 into `OSC 1341;jasper;cwd;…` / `OSC 1341;jasper;mark;…`, and follows DECSCUSR 0 (and `CSI SP q`), passed through unchanged, with `OSC 1341;jasper;cursor-reset`.
- `HyperlinkStyle` equality ignores its link, so a cache keyed by `TextStyle` gives every OSC 8 link in the same colours the first link's target; `JediCellReader` therefore converts hyperlink styles each time instead of caching them.
- Mouse: `onMouseEvent(column, row, …)` takes 0-based cells and encodes the report itself; SGR right press at (0,0) = `ESC[<2;1;1M`; it already drops MOVED/DRAGGED in click-only mode (1000); a press with the Shift flag sends nothing.
- `linesDiscardedFromHistory` fires when scrollback overflows; ED 3 and RIS fire `historyCleared` (ED 2 does not); **a width change reflows soft-wrapped lines**; while the alternate screen is active `getHistoryLinesCount()` is 0.

## Source hygiene

- Never put raw control, private-use or unpaired surrogate characters in source. Write Java escapes (for example `"\033[1;3D"`) or ASCII casts (`(char) 0xF113`, `(char) 0xD83D`). JediTerm's continuation marker U+E000 is written as the Java escape (backslash, `u`, `E000`), never as the raw character.
- macOS `grep` has no `-P`. Check files with Python instead:

```
python3 - <<'PY'
import pathlib, sys
for name in sys.argv[1:] or [str(p) for root in ("jasper-terminal/src", "jasper-ui/src", "jasper-app/src", "jasper-buddy/src", "jasper-sdk/src", "jasper-sdk-testkit/src", "plugins") for p in pathlib.Path(root).rglob("*.java")]:
    text = pathlib.Path(name).read_text(encoding="utf-8")
    bad = sum(1 for c in text if 0xD800 <= ord(c) <= 0xDFFF or 0xE000 <= ord(c) <= 0xF8FF or (ord(c) < 0x20 and c not in "\n\t\r") or ord(c) == 0x7f)
    if bad: print(name, "bad chars:", bad)
PY
```

## Workflow used so far

Plans are written with `superpowers:writing-plans` (complete code in every step, TDD, one commit per task) and executed with `superpowers:subagent-driven-development` (per-task implementer + reviewer, fix rounds, a final whole-branch review). Keep that shape: one plan per runnable deliverable, and record any deviation from plan text in the plan's status banner and in `docs/STATUS.md`.

## Contributor entry points

Use the [documentation index](docs/README.md) for every module, including
[Buddy architecture](docs/buddy-architecture.md) and [Buddy maintenance](docs/buddy-maintenance.md).
Plugin work starts with [SDK architecture](docs/sdk-architecture.md), [plugin authoring](docs/plugin-authoring.md), the [SDK README](jasper-sdk/README.md), the [SDK reference](docs/sdk-reference.html), [plugin APIs](docs/plugin-apis.md) and the [testkit README](jasper-sdk-testkit/README.md).
Start with [app onboarding](jasper-app/README.md), [Buddy embedding](jasper-buddy/README.md),
[architecture](docs/app-architecture.md) and [maintenance recipes](docs/app-maintenance.md).
App public visibility is for internal feature collaboration, not a plugin ABI. Package-info
files name allowed outgoing dependencies and lifetime owners. Pure test
fixtures live in testsupport; owner-package test access bridges stay out of production jars.

## Documentation edits

- Prefer the smallest edit that makes the doc accurate. Don't add sections, summaries, or restatements unless asked.
- When asked to remove or correct content, delete it entirely. Never replace it with a negation or disclaimer ("this does not do X").
- Document only what the system does. Negative statements are allowed only when readers would plausibly expect the behavior and be misled without them.
- After a removal request, compare before and after. If the text grew, redo the edit.
- [`README.md`](README.md) introduces Jasper to users (what it is, its bundled plugins, extending it, installing,
  building from source); [`docs/README.md`](docs/README.md) indexes every current guide by reader (Using Jasper, Plugins, Theming,
  Building plugins, Building Jasper and contributing) and lists the project records. Add a new guide to that index.
  Dated records (`docs/superpowers/**`, `docs/design/**`, `docs/benchmarks/**`, the `*-verification.md` reports,
  `docs/terminal-readiness.md`, `docs/documentation-audit.md` and the entries in `docs/STATUS.md`) describe the code when
  they were written; for how the code behaves now, the guides take precedence.
- `docs/sdk-reference.html` is generated; do not edit it by hand. `./gradlew sdkReference` rewrites it with
  `tools/sdk-reference/SdkReference.java` from the guide pages in `tools/sdk-reference/guide/`, `page.css`, `page.js`
  and the Javadoc of `jasper-sdk`, `jasper-sdk-testkit` and the bundled plugins' exported `api` packages.
  `verifySdkReference`, part of `:jasper-sdk:check`, fails when the page is stale, on an unresolved `{@link}`, a link to
  an anchor the page lacks, or a block tag written mid-line. After changing any of those sources, run `sdkReference` and
  include the page in the change. Guides link SDK types to the page's anchors: `sdk-reference.html#package-<package>`,
  `#<qualified type>`, `#<qualified type>.<member>(<simple parameter types>)` for methods and constructors,
  `#<qualified type>.<name>()` for record components and `#<qualified type>.<FIELD>` for fields and constants. No test
  checks these fragments in Markdown guides, so after renaming an SDK package, type or member, search the guides for
  its old anchor.
- To bring the guides and the SDK reference up to `main` after features land, follow the docs-sync skill
  ([`.claude/skills/docs-sync/SKILL.md`](.claude/skills/docs-sync/SKILL.md)). Each sync commit ends with a
  `Docs-Synced-Through: <main commit>` trailer, which the next sync reads to find its range.
- `docs/editor-vim-reference.md` is generated too:
  `./gradlew :jasper-plugin-editor:test --tests '*VimDocsTest' -PupdateDocs=true` rewrites it.
- CI skips changes that touch only `docs/**` (except `docs/examples/**`) and `*.md` files. The tests and checks that
  read documentation are listed in
  [Tests that read the documentation](docs/app-maintenance.md#tests-that-read-the-documentation);
  `ThirdPartyNoticesTest` (`LICENSE.md`'s first and last lines) reads one too. All run in `./gradlew check`. Settings'
  Help button opens `docs/configuration.md` anchors that no test checks; keep those headings.
