# Browser Plugin, Plan 3: Link Routing Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Execution status (2026-10-22): executed** (`1d103ad3b..17b826302` commits); see `docs/STATUS.md`. The task reviews
recorded these deviations from the plan's text:
- **Task 2:** `LinkOrigin`'s `{@code Platform.openInBrowser}` became a `{@link}`, `Platform.browser`'s Javadoc finished
  its OSC 8 sentence (a browser treats an address as untrusted), and one `LinkOrigin` Javadoc line was re-wrapped to
  stay under 120 columns.
- **Task 4:** `HostedContext.systemBrowser`'s failure log is a fixed message plus the exception's class name, because it
  now also carries clicked addresses (this settles the plan's Ruling 5); the user-facing notification may keep the
  address. `TerminalViewInteractionTest.aNullLinkOpenerRestoresTheDesktopDefault` assumes a headless JVM, as its
  sibling does.
- **Task 7:** `Platform.browser`'s Javadoc drops "from the terminal or the page" and tells a browser to treat an address
  as untrusted input and show it before acting on it. `docs/plugin-authoring.md`, `docs/command-line.md` and
  `tools/sdk-reference/guide/03-manifest.html` name `open-url` among the reserved subcommands. Editor 0.16.0 and Remote
  0.17.0 replace their older versions in `docs/plugin-apis.md` and the `docs/STATUS.md` plugin table.
- **Task 8:** the check ran 12,846 tests, one more than this plan's sum of 12,845; all 22 tests the plan adds ran.
- **Final review:** its fixes add two tests; the check ran 12,848.
- **Rebase (2026-10-27):** onto `main` at `a67b37b14`, where the SDK is 0.22.0, Editor 0.16.0 and Remote 0.17.0, so
  the SDK becomes 0.23.0 and this plan's `@since 0.21.0` tags read `@since 0.23.0`; Editor becomes 0.17.0 and Remote
  0.18.0 in place of this plan's 0.16.0 and 0.17.0.

**Goal:** Jasper routes web addresses through one plugin-provided web browser role, as it routes files through the
editor role: a terminal's clicked `http` and `https` links, plugins' `Platform.openInBrowser`, `jasper <url>…` and
`jasper open-url <url>` reach the role first and the system browser otherwise. No bundled plugin provides the role yet
(plan 4's Browser plugin will), so after this plan every address still opens in the system browser, through the new
route.

**Architecture:**
- **SDK** (stays 0.21.0, unreleased): `WebBrowser` (functional, modelled on `FileEditor`), `LinkOrigin` (`CLICK`,
  `PROGRAM`), `Platform.browser(WebBrowser)` (a default method like `Platform.editor`, needing the thirteenth
  capability, `browser.provide`) and `Platform.openInBrowser(URI)` (modelled on `openInEditor`). `Platform.browse` keeps
  its meaning: always the system browser.
- **Both runtimes:** the app keeps the registration in a new package-private `plugins.WebBrowsers` (modelled on
  `FileEditors`); the testkit's `FakePluginHost` fakes it; the contract suite pins it on both.
- **jasper-terminal:** `TerminalView.setLinkOpener` becomes public; `null` restores the desktop default.
- **App routing:** `TabHost` wires each pane's link opener to a new `WindowContent.onOpenLink`, which
  `JasperApplication.composeContent` sets to `JasperApplication.openLink`. That sends `http` and `https` links through
  `PluginRuntime.openLink` (no SDK type in its signature) as clicks with the pane's window, and every other link the
  terminal opens to the system browser. `JasperApplication.commands()` decides URLs before paths and subcommands.
- **Plugins:** the Editor's Markdown links and Remote's tunnel Open in Browser call `openInBrowser`.

**Tech Stack:** Java 25 on JetBrains Runtime 25, Swing, Gradle 9.7 (Kotlin DSL, `./gradlew` only), JUnit 6.1.3 and
AssertJ 3.27.7, Python 3.11+ standard library for the edit scripts (always `python3 -I`).

**Spec:** [`docs/superpowers/specs/2026-10-21-jasper-browser-plugin-design.md`](../specs/2026-10-21-jasper-browser-plugin-design.md),
sections 3, 6 and 13 item 3. **Brief:** the controller's design brief (`.superpowers/plan3-design-brief.md`, git-ignored
scratch in the design worktree) carries the decisions this plan follows; where it and the spec differ, the brief wins
and the difference is listed under Deviations, which Task 7 writes back into the spec.

**The user's decisions (2026-10-22), after reading the plan:** "route Help and About through it too" (Rulings 11,
Deviations 6, Task 4). The review sheet's wording for `browser.provide`, "Open the web addresses Jasper opens, such as
links you click in a terminal", stands: the user accepted it by not objecting. Whether a `jasper` URL call restores
Jasper's windows stays with plan 4 (Rulings 6).

## Rulings the writer made

Where the code made a brief decision impossible or left it open, the writer chose the smallest sensible thing:

1. **`PluginRuntime`'s bridge is two methods, `openLink` and `openProgramLink`, with no SDK type in them.**
   `verifyApplicationArchitecture` confines SDK types to `dev.jasper.app.plugins` (`gradle/application-architecture.gradle.kts:63-66`),
   so `JasperApplication` cannot name `LinkOrigin`. They run on the UI thread, unlike `open(...)`, which hops to a worker
   and waits up to 30 s: the role must not block, and both callers (a click, the command's `onEventThread`) are on the EDT.
2. **`WebBrowsers` asks `FileEditors` which plugins are bundled** (`new WebBrowsers(containment, fileEditors::bundled)`),
   as split items already do (`HostedContext.java:96`). One bundled set, set once before plugins start
   (`PluginRuntime.java:748`) and by both contract harnesses' `declareBundled`, so neither needs a change for it.
3. **`openInBrowser` asks the role as a click in the active window** (the app's `openInEditor` uses the active window
   too, `HostedContext.java:210`), hopping to the UI thread; the system-browser fallback runs on the plugin's worker as
   `browse` does. The fake asks on the caller's thread, as its `openInEditor` does.
4. **A web address**, for clicks and the command, is an absolute URI (`new URI(text)`) whose scheme is `http` or
   `https` in any case, hierarchical, with an authority. So `https:x`, `http:/x`, Windows drive and UNC paths and
   `./https://x` are not addresses; a clicked `http` link without an authority (possible only from OSC 8) goes to the
   system browser as before. The SDK's own check for `browse` and `openInBrowser` (`HostedContext.webAddress`) stays as
   it is.
5. **Logging stays free of link text.** A clicked link that is not a URI opens nothing and logs a fixed line; a failed
   system-browser open logs the exception's class only, as `DesktopServices.openBrowser` does today (a link is program
   output).
6. **The `jasper` URL calls restore no windows** (plugin subcommands do, `JasperApplication.java:780-784`). With no
   role the address needs none; where plan 4's browser opens an address that has no window is plan 4's call.
7. **`jasper --help`'s usage text is unchanged**: it is the C client's `USAGE` (`packaging/cli/src/jasper_cli.c:63-72`),
   and the brief keeps the C client as it is. `docs/command-line.md` documents both forms.
8. **The view-to-window tests extend `FileLinkClickTest`** (in the theme-migration ledger already), and `WebBrowsers`
   has no unit test of its own: its rules are the contract's, which `AppContractTest` runs against it. It needs a ledger
   row (`ThemeMigrationCoverageTest.CANDIDATE` matches its `dev.jasper.sdk.ui` import).
9. **`TerminalPane.close` also resets the link opener**, as it resets the file link handler (`TerminalPane.java:625`);
   the brief named only `TabHost`'s unwire.
10. **A provided (remote) session's URL clicks go through the route too.** File paths open nothing there
    (`TabHost.openFileLink`), but URLs always reached the desktop browser from any pane.
11. **Jasper's own web links go through the role, as clicks** (the user: "route Help and About through it too").
    Settings' Help button (`SettingsDialog.java:254`), the Licenses window's homepages (`LicensesView.java:117`, from
    About's **Licenses…**) and the plugin manager's homepages (`PluginManagerPanel.java:175`) all open through
    `JasperApplication`'s `browse(URI)` (`:1518`, wired at `:1270`, `:1404` and `:2064`), which Task 4 routes through
    `openWebAddress`, the terminal clicks' path, and makes package-private for its test. None of these hooks knows the
    window it came from (About, Licenses and Settings are auxiliary windows, not Jasper windows), so they go with the
    active Jasper window, as Open in Editor for `config.toml` does (`PluginRuntime.open(Path, Consumer)`); with none
    active, with no window. The Help menu itself holds no web link today (Keyboard Shortcuts, Change Memory Settings…,
    Edit Custom VM Options… and About Jasper, `AboutWindows.helpEntries`), so routing the one helper covers every web
    link Help and About open. The helper's `supports(Desktop.Action.BROWSE)` check goes: the system-browser fallback
    logs an unsupported desktop instead of skipping it silently. The test calls `browse` directly; its three hook
    wirings stay the same method references.
12. **The SDK goes in two tasks.** Task 1 adds the values and the default `Platform.browser`; Task 2 adds the abstract
    `Platform.openInBrowser`, which `HostedContext` and `FakePluginContext`, the only two implementations, must provide
    in the same commit.
13. **Words and order the brief left open:** the review sheet says "Open the web addresses Jasper opens, such as links
    you click in a terminal"; `BROWSER_PROVIDE` is last in `Capabilities.ALL` and in the class, so the generated
    capability table keeps `ALL`'s order; a second registration throws "The browser is already provided by *id*".
14. **Versions:** the Editor goes from 0.15.0 to 0.16.0 and Remote from 0.16.0 to 0.17.0, `plugin.toml` only, as plan 9b
    did (`3e3deacdb`).
15. **The terminal guides go in the documentation task** (Task 7), as the controller's task list says; the brief had put
    them with the terminal hook.

## Deviations from the spec

Task 7 writes each into the spec (sections 6 and 13).

1. **`BROWSER` in local shells moves to plan 4.** Python's `webbrowser` takes a `BROWSER` entry without `%s` as a single
   program name, so `"<client> open-url"` may not work as a value; `gh` splits `BROWSER` with shlex; quoting a Windows
   path with spaces is unresolved. That needs the real `gh` and `aws` on all three platforms, which plan 4 checks while
   it sets the variable. Plan 3 builds only the route the variable will name.
2. **`BROWSER` will name `jasper open-url`, not "the `jasper` command".** `open-url` is new: the route programs use,
   reserved so no plugin can claim it, and left out of `jasper --help`.
3. **No URL-scheme handler.** The launcher's second-launch path and `setOpenURIHandler` are not routed.
4. **Spec error, section 6 "Today":** `jasper https://x` does not fail with "no such file or folder". With the plugins
   running it ends in `PluginCommands.dispatch` as `jasper: unknown command https://x; run jasper --help for the list`,
   exit 2 (`PluginCommands.java:99-106`); before they start, `jasper: Jasper's plugins are not running`, exit 1.
5. **Spec error, section 6:** the terminal's app-facing allowlist is type-level and already lists `TerminalView`
   (`jasper-terminal/README.md:44`), so making `setLinkOpener` public changes no allowlist.
6. **Jasper's own web links are routed too** (the user's decision): spec 6's "Links the user clicks" names terminal
   links and plugins' links; Settings' Help button and the Licenses window's and the plugin manager's homepages join
   them, as clicks.

## Global Constraints

- Toolchain: `./gradlew` only (never a system `gradle`, never `./gradlew --stop`, which hits other sessions), Java 25 on
  JBR 25. No GUI launch, no AOT training, no `:jasper-app:run`, no benchmark (AGENTS.md).
- Workspace: worktree `/Users/RQ7RQVF/projects/moray/.claude/worktrees/browser-link-routing`, branch
  `claude/browser-link-routing`, cut from `claude/browser-plugin-design` (Task 1). Never build in
  `/Users/RQ7RQVF/projects/moray` or in another worktree.
- One commit per task, each message ending with exactly the two lines
  `🤖 Generated with Claude Code at The Home Depot` and `Co-Authored-By: Claude <noreply@anthropic.com>` (a blank line
  between). No push, no merge.
- The SDK stays `0.21.0`: new members say `@since 0.21.0`; `JasperSdk.VERSION` and every manifest's `sdk` range stay.
- SDK types only in `dev.jasper.app.plugins` (`verifyApplicationArchitecture`): `JasperApplication`, `WindowContent` and
  `TabHost` never name `WebBrowser` or `LinkOrigin`.
- No public `jasper-terminal` method takes or returns a JediTerm type; `setLinkOpener` takes `Consumer<String>`.
- Exact values: capability `browser.provide` (`Capabilities.BROWSER_PROVIDE`); review sheet "Open the web addresses
  Jasper opens, such as links you click in a terminal"; registration failure "The browser is already provided by
  *id*"; subcommand `open-url`; usage error `jasper: open-url needs one web address (http or https)` with exit 2.
- The role runs on the UI thread and must not block; nothing waits for it. Never wait on the EDT.
- Plugin versions: Editor `0.16.0`, Remote `0.17.0`; no other plugin changes.
- `docs/sdk-reference.html` is generated: `./gradlew sdkReference` after every change to SDK, testkit or guide-page
  Javadoc, in the same commit (Tasks 1, 2 and 7). Never edit it by hand.
- The theme-migration ledger `docs/design/theme-engine/migration.csv` gets a row for each new file that matches
  `ThemeMigrationCoverageTest.CANDIDATE`: only `WebBrowsers.java` (Task 2).
- Javadoc (`-Xdoclint:all`, fail on error in jasper-sdk, jasper-terminal and jasper-app): no raw `<`, `>` or `&` outside
  `{@code}`, every `{@link}` resolves, every new public member has `@param`, `@return`, `@throws` and `@since` as its
  neighbours do.
- Source hygiene (AGENTS.md): no raw control, private-use or unpaired surrogate characters; Java escapes only.
- MVP: no compatibility shims, aliases or migration notes.
- Documentation edits are the smallest that make a document accurate (AGENTS.md "Documentation edits").
- Tests are headless (`java.awt.headless=true`): views driven with `handleMouse` or dispatched events, plugins on the
  testkit's `FakePluginHost` or `PluginJars` fixtures, the contract on both runtimes.

## Review Focus

1. **An `ftp` or `mailto` link clicked in a terminal** (a plain-text `ftp://` URL, an OSC 8 `mailto:` link). It opens
   in the system browser as before, and the plugin's browser never hears it. Task 4:
   `JasperApplicationPluginsTest.aClickedWebLinkGoesToTheProvidedBrowserAndAnyOtherLinkToTheSystemBrowser`, with
   `FileLinkClickTest.aClickedUrlGoesToTheWindowsLinkOpenerAsWritten` for the view's half.
2. **A provided browser that throws or returns false.** The address still opens in the system browser, once. Task 2:
   the contract cases `aBrowserThatThrowsOrDeclinesGivesFalseAndOneGetsItsPluginsHandleForAnOpenWindow` and
   `openInBrowserAsksTheProvidedBrowserAsAClickInTheActiveWindowAndBrowseNever` (both runtimes); Task 4: the
   application test's `/declined` address.
3. **A link clicked, or `jasper <url>` run, before the plugins start.** It opens in the system browser and the command
   answers 0. Task 4: the same application test's first click; Task 5:
   `ApplicationCommandsTest.everyArgumentAWebAddressOpensEachAndAnswersZero`.
4. **`jasper open-url` with no address, two, or one that is not `http` or `https`** (`ftp://…`, a bare `example.com`, a
   path, a Windows path). It prints `jasper: open-url needs one web address (http or https)`, exits 2 and opens nothing.
   Task 5: `ApplicationCommandsTest.openUrlNeedsExactlyOneWebAddress`.
5. **Windows paths and path-like arguments given to `jasper`** (`C:\Users\me\notes.txt`, `c:/Users/me/notes.txt`,
   `\\server\share\notes.txt`, `./https://example.com`, `https:example.com`, `http:/notes`). None is a web address, so
   the call keeps today's path or subcommand answer. Task 5:
   `ApplicationCommandsTest.onlyAnAbsoluteHttpOrHttpsAddressWithAnAuthorityIsAWebAddress` and
   `aCallThatMixesPathsAndAddressesKeepsItsAnswer`.

Also pinned: the role ending when its plugin stops (Task 2, `aBundledPluginsBrowserReplacesAnotherPluginsAndEndsWithItsPlugin`
on both runtimes); a clicked link that is no URI (Task 4); a URL in a provided session's pane (Task 4); a quitting
Jasper (Task 5, the extended `aRaiseOrAPathCallToAQuittingJasperFailsWithTheQuittingMessageAndOpensNothing`); a `null`
link opener (Task 3); Jasper's own Help and homepage links, before the plugins start, with no active window and from
the active window (Task 4, `jaspersOwnWebLinksGoToTheProvidedBrowserAndOtherwiseTheSystemBrowser`); the Editor and
Remote switches (Task 6).

## Verified facts

From `claude/browser-plugin-design` at `bc9ef8ff5` (origin/main `06746d3c2` plus the design documents). Re-read a line
before quoting it; the anchors below are what the scripts match.

1. **The editor role, end to end.** `FileEditor.edit(Path, OptionalInt, Optional<WindowOwner>)`
   (`jasper-sdk/.../ui/FileEditor.java:23`); `Platform.openInEditor` (`:20`, abstract), `browse` (`:30`, abstract),
   `editor` (`:86-89`, a default returning a no-op `Subscription`). `HostedContext.platform()` (`:202-261`):
   `openInEditor` runs `fileEditors.openFromWorker` on the plugin's worker with the active window; `browse` runs
   `environment.browser()` on the worker and posts "Could not open a web address"; `editor` does `requireOpen`,
   `requireUi`, `gate.require`, `requireNonNull`, `fileEditors.register(...)`, `gate.audit`, `ui.tracked(...)`;
   `webAddress` (`:276-282`). `FileEditors` (`:28-176`): `Holder`, `bundled(Set)`, `bundled(String)`, `register`,
   `edit` through `containment.attempt(pluginId, "file editor", ...)`. `PluginHost.fileEditors` (`:124`, made at `:142`).
2. **Plugins start after `host` is set.** `PluginRuntime.start` sets `host = created` (`:750`) before any plugin starts;
   `host` is null before (`:456`). `environment.browser()` is `dev.jasper.app.platform.Browser::open` (`:733`), which
   refuses the EDT.
3. **Capabilities.** 12 constants, `ALL` (`Capabilities.java:67-68`); `DescriptorParser.CAPABILITIES` (`:15-17`), whose
   equality with `ALL` `DescriptorParserTest.rejectsEveryMalformedDescriptorWithANamedKey` asserts (`:173-174`);
   `pluginmanager.Capabilities.describe` (`:7-24`); `CapabilitiesTest`, `TerminalValuesTest:115` (`ALL` exactly),
   `CapabilityDescriptionsTest`; `RESERVED_SUBCOMMANDS = Set.of("help", "version")` (`DescriptorParser.java:32`).
4. **Testkit.** `FakePluginContext.platform()` (`:120-158`) asks `host.editProvided` on the caller's thread and adds to
   `host.openedInEditor` otherwise; `browse` adds to `host.browsed`. `FakePluginHost`: `browsed` (`:94`), teardown
   clears `providedEditor` (`:871-872`), `ProvidedEditor`, `provideEditor`, `edit`, `editorPlugin` (`:2098-2166`);
   `workspace.lastActive` is the active window (`FakeWorkspace.java:70`, read by `FakeTerminals.activeWindow`, `:150-153`).
5. **Contract.** `ContractHarness` has `browsed()`, `declareBundled`, `edit` (`:299-329`); the editor cases
   (`PluginContractTest.java:3597-3694`) and `browseOpensOnlyWebAddressesAndReachesTheHost` (`:3138`);
   `FakeContractTest:151-154`; `AppContractTest:611-628` (its `browsed` list is the host's browser consumer, `:102`; its
   `declareBundled` calls `host.fileEditors.bundled`, `:622-625`).
6. **Terminal.** `TerminalView.linkOpener = DesktopServices::openBrowser` (`:88`), read at click time (`:114`);
   package-private `setLinkOpener` (`:579-581`); `setFileLinkHandler` and its Javadoc (`:287-296`). `MouseController`
   hands `terminal.linkAt` to the opener on a Cmd-click (Ctrl-click elsewhere) before trying file links (`:80-100`).
   `BufferQueries.linkAt` accepts OSC 8 targets `http`, `https`, `ftp`, `mailto`; `LinkDetector` plain-text `https?|ftp`.
7. **App wiring.** `TabHost.bind` (`:146-153`), `unwire` (`:156-172`); `TerminalPane.close` unwires its view (`:625`);
   `WindowContent.onOpenFile` (`:257`), reset at close (`:2641`); `composeContent` sets it (`JasperApplication.java:571`).
   `JasperApplication.plugins` is null until `startPlugins` (`:153`); `frames` is a package-private test seam (`:192`).
8. **The command.** `commands()` (`:763-786`); `jasper https://x` ends as in Deviations 4. `Invocation.pane()` is the
   calling pane's id; `holderOf` (`:1942-1945`) shows the pane-to-window lookup on `TerminalRegistry`. The C client
   forwards every argument that does not start with `--` (`jasper_cli.c:775-777`).
9. **Plugins calling `browse`.** `EditorWindow.java:177` (`new MarkdownNavigation(this, context.platform()::browse)`) and
   `TunnelsUi.java:350`. Their tests read `FakePluginHost.browsed()` (`MarkdownNavigationTest`, `TunnelsPluginTest:174`).
10. **Ledger.** `ThemeMigrationCoverageTest.CANDIDATE` matches `import dev.jasper.sdk.ui` anywhere under
    `jasper-app/src`, `jasper-sdk/src`, `jasper-sdk-testkit/src` and `plugins/*/src`: `WebBrowsers.java` matches;
    `WebBrowser.java` and `LinkOrigin.java` (package `dev.jasper.sdk.ui`, importing only `dev.jasper.sdk.WindowOwner`,
    `java.net.URI` and `java.util.Optional`) do not.

## File structure

| File | Task | Responsibility |
|---|---|---|
| `jasper-sdk/src/main/java/dev/jasper/sdk/Capabilities.java` | 1 | `BROWSER_PROVIDE`, last in `ALL` |
| `jasper-sdk/src/main/java/dev/jasper/sdk/ui/LinkOrigin.java` (new) | 1 | Who asked: `CLICK` or `PROGRAM` |
| `jasper-sdk/src/main/java/dev/jasper/sdk/ui/WebBrowser.java` (new) | 1 | The role a plugin provides |
| `jasper-sdk/src/main/java/dev/jasper/sdk/ui/Platform.java` | 1, 2 | `browser` (default), `openInBrowser`, `browse`'s Javadoc |
| `jasper-sdk/src/main/java/dev/jasper/sdk/plugin/PluginContext.java` | 1 | `platform()`'s Javadoc |
| `jasper-app/.../plugins/DescriptorParser.java` | 1, 5 | The capability; `open-url` reserved |
| `jasper-app/.../pluginmanager/Capabilities.java` | 1 | The review sheet's words |
| `jasper-app/.../plugins/WebBrowsers.java` (new) | 2 | The registration, one at a time, asked in containment |
| `jasper-app/.../plugins/PluginHost.java` | 2 | Holds `webBrowsers` |
| `jasper-app/.../plugins/HostedContext.java` | 2 | `browser`, `openInBrowser`, `browse` through one helper |
| `jasper-sdk-testkit/.../testing/FakePluginContext.java`, `FakePluginHost.java` | 2 | The fake role, `openLink`, `browserPlugin` |
| `jasper-sdk-testkit/src/testFixtures/.../contract/ContractHarness.java`, `PluginContractTest.java` | 2 | `openLink`; four cases |
| `jasper-sdk-testkit/src/test/.../FakeContractTest.java`, `jasper-app/src/test/.../plugins/AppContractTest.java` | 2 | The harness on each runtime |
| `jasper-terminal/.../view/TerminalView.java` | 3 | Public `setLinkOpener` |
| `jasper-app/.../plugins/PluginRuntime.java` | 4, 5 | `openLink`, `openProgramLink` |
| `jasper-app/.../workspace/WindowContent.java`, `TabHost.java`, `TerminalPane.java` | 4 | `onOpenLink`; wiring and unwiring |
| `jasper-app/.../application/JasperApplication.java` | 4, 5 | `openLink`, `browse` (Jasper's own links), `webAddress`, `systemBrowser`; the command's order |
| `plugins/editor/.../ui/EditorWindow.java`, `plugins/remote/.../ui/tunnels/TunnelsUi.java`, both `plugin.toml` | 6 | `openInBrowser`; versions |
| Tests | each task | Listed in each task's Files |
| Guides, `docs/STATUS.md`, the spec, `docs/sdk-reference.html` | 1, 2, 7 | Task 7, and the reference wherever Javadoc changes |
| `docs/design/theme-engine/migration.csv` | 2 | `WebBrowsers.java`'s row |

`jasper-app/...` is `jasper-app/src/main/java/dev/jasper/app/...`, `jasper-sdk-testkit/...` is
`jasper-sdk-testkit/src/main/java/dev/jasper/sdk/...`, `jasper-terminal/...` is
`jasper-terminal/src/main/java/dev/jasper/terminal/...`, `plugins/editor/...` is
`plugins/editor/src/main/java/dev/jasper/editor/...` and `plugins/remote/...` is `plugins/remote/src/main/java/dev/jasper/remote/...`.

## How the edits are written

Every edit runs as a Python script from the worktree root, `python3 -I - <<'PY'`, with the helper Task 1 creates in
`build/plan-browser3/edits.py` (git-ignored; never commit it). `edit(path, [(old, new, count), ...])` stops with a
message when `old` is not found exactly `count` times; `create(path, text)` writes a new file and stops when it
exists; `append(path, text)` appends to a file that ends with a newline. Text is in raw strings (`r'''…'''`), so Java
backslashes appear exactly as in the source. When a script stops, re-find the quoted text (a change since may have
reworded it), adjust the anchor, re-run, and record it in the status banner. Never edit around a missing anchor.

Agent shells do not keep their working directory: start every command with
`cd /Users/RQ7RQVF/projects/moray/.claude/worktrees/browser-link-routing &&`.

---

### Task 1: The worktree, the `browser.provide` capability and the SDK's web browser types

Spec 6 ("New SDK surface"), brief 1: `WebBrowser`, `LinkOrigin`, `Capabilities.BROWSER_PROVIDE` and a default
`Platform.browser`, which does nothing until Task 2's hosts provide it; the capability recipe of
`docs/app-maintenance.md` ("A new SDK capability or service"): `DescriptorParser.CAPABILITIES` and
`pluginmanager.Capabilities.describe`.

**Files:**
- Create: `jasper-sdk/src/main/java/dev/jasper/sdk/ui/LinkOrigin.java`, `jasper-sdk/src/main/java/dev/jasper/sdk/ui/WebBrowser.java`
- Modify: `jasper-sdk/src/main/java/dev/jasper/sdk/Capabilities.java` (class Javadoc `:7`, after `:65`, `ALL` `:68`);
  `jasper-sdk/src/main/java/dev/jasper/sdk/ui/Platform.java` (after `editor`, `:89`);
  `jasper-sdk/src/main/java/dev/jasper/sdk/plugin/PluginContext.java` (`platform()`'s Javadoc, `:195-199`);
  `jasper-app/src/main/java/dev/jasper/app/plugins/DescriptorParser.java` (`:17`);
  `jasper-app/src/main/java/dev/jasper/app/pluginmanager/Capabilities.java` (after `:20`); `docs/sdk-reference.html` (generated)
- Test: `jasper-sdk/src/test/java/dev/jasper/sdk/CapabilitiesTest.java`,
  `jasper-sdk/src/test/java/dev/jasper/sdk/terminal/TerminalValuesTest.java` (`:115`),
  `jasper-sdk/src/test/java/dev/jasper/sdk/ui/UiValuesTest.java`,
  `jasper-app/src/test/java/dev/jasper/app/plugins/DescriptorParserTest.java`,
  `jasper-app/src/test/java/dev/jasper/app/pluginmanager/CapabilityDescriptionsTest.java`

**Interfaces:**
- Consumes: `Platform.editor(FileEditor)`'s default shape; `dev.jasper.sdk.WindowOwner`; `dev.jasper.sdk.Subscription`.
- Produces:
  - `dev.jasper.sdk.Capabilities.BROWSER_PROVIDE = "browser.provide"`, last in `Capabilities.ALL`;
  - `enum dev.jasper.sdk.ui.LinkOrigin { CLICK, PROGRAM }`;
  - `@FunctionalInterface dev.jasper.sdk.ui.WebBrowser { boolean open(URI address, LinkOrigin origin, Optional<WindowOwner> window); }`;
  - `default Subscription Platform.browser(WebBrowser browser)` (null check, then a no-op `Subscription`; Task 2's
    hosts override it). Its Javadoc names `{@code openInBrowser}`, which Task 2 turns into a link;
  - `DescriptorParser.CAPABILITIES` contains `browser.provide`; `pluginmanager.Capabilities.describe("browser.provide")`
    is "Open the web addresses Jasper opens, such as links you click in a terminal (browser.provide)".

- [ ] **Step 1: Create the worktree**

```bash
cd /Users/RQ7RQVF/projects/moray/.claude/worktrees/browser-plugin-design && git status --short && git log --oneline -1
git -C /Users/RQ7RQVF/projects/moray/.claude/worktrees/browser-plugin-design worktree add \
    /Users/RQ7RQVF/projects/moray/.claude/worktrees/browser-link-routing -b claude/browser-link-routing claude/browser-plugin-design
cd /Users/RQ7RQVF/projects/moray/.claude/worktrees/browser-link-routing && git branch --show-current && git status --short
```

Expected: `git log` shows this plan's commit (or a later one) at the tip of `claude/browser-plugin-design`; the new
branch is cut from that commit, so anything uncommitted in the design worktree stays there. The worktree is created,
and the last two commands print `claude/browser-link-routing` and nothing.

- [ ] **Step 2: Check the anchors the plan starts from**

```bash
cd /Users/RQ7RQVF/projects/moray/.claude/worktrees/browser-link-routing
grep -c '        TERMINAL_ENVIRONMENT, EDITOR_PROVIDE, PRESENTATION_PROVIDE);' jasper-sdk/src/main/java/dev/jasper/sdk/Capabilities.java  # 1
grep -c 'default Subscription editor(FileEditor editor) {' jasper-sdk/src/main/java/dev/jasper/sdk/ui/Platform.java                  # 1
grep -c '    void setLinkOpener(Consumer<String> opener) {' jasper-terminal/src/main/java/dev/jasper/terminal/view/TerminalView.java  # 1
grep -c 'static final Set<String> RESERVED_SUBCOMMANDS = Set.of("help", "version");' jasper-app/src/main/java/dev/jasper/app/plugins/DescriptorParser.java  # 1
grep -c 'navigation = new MarkdownNavigation(this, context.platform()::browse);' plugins/editor/src/main/java/dev/jasper/editor/ui/EditorWindow.java  # 1
grep -c 'ifPresent(uri -> context.platform().browse(uri));' plugins/remote/src/main/java/dev/jasper/remote/ui/tunnels/TunnelsUi.java  # 1
grep -c '^version = "0.15.0"' plugins/editor/src/main/resources/plugin.toml   # 1 (else the Editor moved: take its next minor and record it)
grep -c '^version = "0.16.0"' plugins/remote/src/main/resources/plugin.toml   # 1 (else Remote moved: the same)
grep -c 'public static final String VERSION = "0.21.0";' jasper-sdk/src/main/java/dev/jasper/sdk/JasperSdk.java  # 1
```

Expected: every command prints `1`.

- [ ] **Step 3: Create the edit helper**

```bash
mkdir -p build/plan-browser3
cat > build/plan-browser3/edits.py <<'PY'
"""Exact edits that stop when an anchor is not found the stated number of times."""
import pathlib, sys

def _read(path):
    with open(path, encoding="utf-8", newline="") as source:
        return source.read()

def _write(path, text):
    with open(path, "w", encoding="utf-8", newline="") as target:
        target.write(text)

def edit(path, pairs):
    """pairs: (old, new, count); each old must occur exactly count times."""
    text = _read(path)
    for old, new, count in pairs:
        found = text.count(old)
        if found != count:
            sys.exit(f"{path}: expected {count} of {old[:90]!r}, found {found}")
        text = text.replace(old, new)
    _write(path, text)

def create(path, text):
    """Writes a new file; stops when it exists."""
    target = pathlib.Path(path)
    if target.exists():
        sys.exit(f"{path} exists already")
    target.parent.mkdir(parents=True, exist_ok=True)
    _write(path, text)

def append(path, text):
    """Appends to a file that ends with a newline."""
    current = _read(path)
    if not current.endswith("\n"):
        sys.exit(f"{path} must end with a newline")
    _write(path, current + text)
PY
ls build/plan-browser3/edits.py
```

- [ ] **Step 4: Write the failing tests**

```bash
python3 -I - <<'PY'
import sys; sys.path.insert(0, "build/plan-browser3")
from edits import edit
edit("jasper-sdk/src/test/java/dev/jasper/sdk/CapabilitiesTest.java", [
(r'''    @Test void theSdkIsVersionTwentyOneWithThePresentationCapability() {''',
 r'''    @Test void theSdkIsVersionTwentyOneWithTheBrowserCapability() {''', 1),
(r'''        assertThat(Capabilities.ALL).endsWith(Capabilities.CLIPBOARD_READ, Capabilities.TERMINAL_ENVIRONMENT, Capabilities.EDITOR_PROVIDE,
            Capabilities.PRESENTATION_PROVIDE);
''',
 r'''        assertThat(Capabilities.BROWSER_PROVIDE).isEqualTo("browser.provide");
        assertThat(Capabilities.ALL).endsWith(Capabilities.CLIPBOARD_READ, Capabilities.TERMINAL_ENVIRONMENT, Capabilities.EDITOR_PROVIDE,
            Capabilities.PRESENTATION_PROVIDE, Capabilities.BROWSER_PROVIDE);
''', 1)])
edit("jasper-sdk/src/test/java/dev/jasper/sdk/terminal/TerminalValuesTest.java", [
(r'''"editor.provide", "presentation.provide");''',
 r'''"editor.provide", "presentation.provide", "browser.provide");''', 1)])
edit("jasper-sdk/src/test/java/dev/jasper/sdk/ui/UiValuesTest.java", [
(r'''        assertThatNullPointerException().isThrownBy(() -> older.editor(null));
    }
''',
 r'''        assertThatNullPointerException().isThrownBy(() -> older.editor(null));
    }

    /** Browser plan 3: the browser hook is a default an older host ignores, and an address comes from a click or a program. */
    @Test void theBrowserHookIsADefaultThatAnOlderHostIgnores() throws Exception {
        assertThat(Platform.class.getMethod("browser", WebBrowser.class).isDefault()).isTrue();
        Platform older = (Platform) java.lang.reflect.Proxy.newProxyInstance(Platform.class.getClassLoader(), new Class<?>[]{Platform.class},
            (proxy, method, args) -> method.isDefault() ? java.lang.reflect.InvocationHandler.invokeDefault(proxy, method, args) : null);
        var asked = new java.util.concurrent.atomic.AtomicInteger();
        older.browser((address, origin, window) -> {
            asked.incrementAndGet();
            return true;
        }).close();
        assertThat(asked).as("an older host never asks").hasValue(0);
        assertThatNullPointerException().isThrownBy(() -> older.browser(null));
        assertThat(LinkOrigin.values()).containsExactly(LinkOrigin.CLICK, LinkOrigin.PROGRAM);
    }
''', 1)])
edit("jasper-app/src/test/java/dev/jasper/app/plugins/DescriptorParserTest.java", [
(r'''    @Test void acceptsTheEnvironmentAndEditorCapabilities() throws Exception {''',
 r'''    /** Browser plan 3: the web browser role's capability. */
    @Test void acceptsTheBrowserCapability() throws Exception {
        PluginDescriptor descriptor = DescriptorParser.parse(FULL.replace("\"session.provide\"", "\"browser.provide\""));
        assertThat(descriptor.capabilities()).contains("browser.provide");
    }

    @Test void acceptsTheEnvironmentAndEditorCapabilities() throws Exception {''', 1)])
edit("jasper-app/src/test/java/dev/jasper/app/pluginmanager/CapabilityDescriptionsTest.java", [
(r'''    @Test void theEnvironmentAndEditorCapabilitiesAreDescribed() {''',
 r'''    /** Browser plan 3: the review sheet says what the web browser role does. */
    @Test void theBrowserCapabilityIsDescribed() {
        assertThat(Capabilities.describe("browser.provide"))
            .isEqualTo("Open the web addresses Jasper opens, such as links you click in a terminal (browser.provide)");
    }

    @Test void theEnvironmentAndEditorCapabilitiesAreDescribed() {''', 1)])
PY
```

- [ ] **Step 5: Run the SDK tests to verify they fail**

Run: `./gradlew :jasper-sdk:test --tests 'dev.jasper.sdk.CapabilitiesTest' --tests 'dev.jasper.sdk.terminal.TerminalValuesTest' --tests 'dev.jasper.sdk.ui.UiValuesTest'`
Expected: FAIL to compile `compileTestJava`: `cannot find symbol` for `BROWSER_PROVIDE`, `WebBrowser` and `LinkOrigin`.

- [ ] **Step 6: Add the SDK types, the capability and the default hook**

```bash
python3 -I - <<'PY'
import sys; sys.path.insert(0, "build/plan-browser3")
from edits import edit, create
create("jasper-sdk/src/main/java/dev/jasper/sdk/ui/LinkOrigin.java", r'''package dev.jasper.sdk.ui;

/**
 * Who asked Jasper to open a web address, as {@link WebBrowser#open} hears it.
 *
 * @since 0.21.0
 */
public enum LinkOrigin {
    /**
     * The user asked for it: a link Cmd-clicked (Ctrl-clicked on Linux and Windows) in a terminal pane, one of Jasper's
     * own web links such as Settings' Help, a plugin's {@code Platform.openInBrowser}, or {@code jasper <address>}.
     */
    CLICK,
    /**
     * A program asked for it, through {@code jasper open-url <address>}, such as the sign-in page a command line tool
     * opens, which usually works only in the system browser.
     */
    PROGRAM
}
''')
create("jasper-sdk/src/main/java/dev/jasper/sdk/ui/WebBrowser.java", r'''package dev.jasper.sdk.ui;

import dev.jasper.sdk.WindowOwner;
import java.net.URI;
import java.util.Optional;

/**
 * Opens the web addresses Jasper opens, once a plugin registers it with {@link Platform#browser}.
 *
 * @since 0.21.0
 */
@FunctionalInterface
public interface WebBrowser {
    /**
     * Opens a web address. UI thread; it must not block.
     *
     * @param address an absolute {@code http} or {@code https} address, as the link or the caller gave it
     * @param origin who asked for it: the user, or a program
     * @param window the Jasper window the address came from, as this plugin's handle; empty when it came from elsewhere
     * @return true when it opened the address; false hands it to the system browser
     */
    boolean open(URI address, LinkOrigin origin, Optional<WindowOwner> window);
}
''')
edit("jasper-sdk/src/main/java/dev/jasper/sdk/Capabilities.java", [
(r''' * cannot see, and the roles one plugin at a time may take over ({@code editor.provide}, {@code presentation.provide});''',
 r''' * cannot see, and the roles one plugin at a time may take over ({@code editor.provide}, {@code browser.provide},
 * {@code presentation.provide});''', 1),
(r'''    public static final String PRESENTATION_PROVIDE = "presentation.provide";
''',
 r'''    public static final String PRESENTATION_PROVIDE = "presentation.provide";
    /**
     * Being the web browser Jasper opens web addresses with, through {@code Platform.browser}.
     *
     * @since 0.21.0
     */
    public static final String BROWSER_PROVIDE = "browser.provide";
''', 1),
(r'''        TERMINAL_ENVIRONMENT, EDITOR_PROVIDE, PRESENTATION_PROVIDE);''',
 r'''        TERMINAL_ENVIRONMENT, EDITOR_PROVIDE, PRESENTATION_PROVIDE, BROWSER_PROVIDE);''', 1)])
edit("jasper-sdk/src/main/java/dev/jasper/sdk/ui/Platform.java", [
(r'''    default Subscription editor(FileEditor editor) {
        java.util.Objects.requireNonNull(editor, "editor");
        return () -> { };
    }
''',
 r'''    default Subscription editor(FileEditor editor) {
        java.util.Objects.requireNonNull(editor, "editor");
        return () -> { };
    }

    /**
     * Makes this plugin the web browser Jasper opens web addresses with: an {@code http} or {@code https} link
     * Cmd-clicked (Ctrl-clicked on Linux and Windows) in a terminal pane, with that window; every plugin's
     * {@code openInBrowser} and Jasper's own web links, such as Settings' Help, with the active window; and the
     * addresses given to the {@code jasper} command, with the window of the pane it ran in, go to {@code browser} first,
     * on the UI thread, with who asked ({@link LinkOrigin}).
     * A clicked link comes from program output, and an OSC 8 link's target can differ from the text shown. Other
     * schemes, such as {@code mailto}, and {@link #browse} always go to the system browser. One at a time: a bundled
     * plugin's registration replaces a plugin's that is not bundled; otherwise the first stays and any later one, this
     * plugin's own included, throws. When the browser returns false or throws, and when no plugin provides one, the
     * system browser opens the address. Needs {@code browser.provide}. UI thread only. The registration ends with the
     * plugin.
     *
     * @param browser opens a web address
     * @return the registration
     * @throws IllegalStateException when another plugin already provides the browser
     *     ({@code The browser is already provided by <plugin id>}), and off the UI thread
     * @throws dev.jasper.sdk.MissingCapabilityException without {@code browser.provide}
     * @since 0.21.0
     */
    default Subscription browser(WebBrowser browser) {
        java.util.Objects.requireNonNull(browser, "browser");
        return () -> { };
    }
''', 1)])
edit("jasper-sdk/src/main/java/dev/jasper/sdk/plugin/PluginContext.java", [
(r'''         * Desktop integration: opening a file in an editor or a web address in the browser, copying to and reading the
         * clipboard, and becoming the editor Jasper opens files with. Reading the clipboard needs
         * {@code clipboard.read} and providing the editor {@code editor.provide}; the rest needs no capability.
''',
 r'''         * Desktop integration: opening a file in an editor or a web address in a browser, copying to and reading the
         * clipboard, and becoming the editor Jasper opens files with or the web browser it opens web addresses with.
         * Reading the clipboard needs {@code clipboard.read}, providing the editor {@code editor.provide} and providing
         * the browser {@code browser.provide}; the rest needs no capability.
''', 1)])
PY
```

- [ ] **Step 7: Run the SDK tests to verify they pass, and the app's to verify they fail**

Run: `./gradlew :jasper-sdk:test --tests 'dev.jasper.sdk.CapabilitiesTest' --tests 'dev.jasper.sdk.terminal.TerminalValuesTest' --tests 'dev.jasper.sdk.ui.UiValuesTest'`
Expected: `BUILD SUCCESSFUL`.

Run: `./gradlew :jasper-app:test --tests 'dev.jasper.app.plugins.DescriptorParserTest' --tests 'dev.jasper.app.pluginmanager.CapabilityDescriptionsTest'`
Expected: FAIL: `rejectsEveryMalformedDescriptorWithANamedKey` ("every SDK capability, and nothing else"),
`acceptsTheBrowserCapability` (`capabilities names an unknown capability: browser.provide`) and
`theBrowserCapabilityIsDescribed` (expected the description, was `browser.provide`).

- [ ] **Step 8: Accept and describe the capability in the app**

```bash
python3 -I - <<'PY'
import sys; sys.path.insert(0, "build/plan-browser3")
from edits import edit
edit("jasper-app/src/main/java/dev/jasper/app/plugins/DescriptorParser.java", [
(r'''        "clipboard.read", "terminal.environment", "editor.provide", "presentation.provide");''',
 r'''        "clipboard.read", "terminal.environment", "editor.provide", "presentation.provide", "browser.provide");''', 1)])
edit("jasper-app/src/main/java/dev/jasper/app/pluginmanager/Capabilities.java", [
(r'''            case "presentation.provide" -> "Change Jasper's window chrome and controls after a restart";
''',
 r'''            case "presentation.provide" -> "Change Jasper's window chrome and controls after a restart";
            case "browser.provide" -> "Open the web addresses Jasper opens, such as links you click in a terminal";
''', 1)])
PY
```

- [ ] **Step 9: Run the app tests, regenerate the SDK reference and check the SDK**

Run: `./gradlew :jasper-app:test --tests 'dev.jasper.app.plugins.DescriptorParserTest' --tests 'dev.jasper.app.pluginmanager.CapabilityDescriptionsTest'`
Expected: `BUILD SUCCESSFUL`.

Run: `./gradlew sdkReference && ./gradlew verifySdkReference verifySdkArchitecture :jasper-sdk:javadoc && git status --short`
Expected: `BUILD SUCCESSFUL` twice; `git status` lists the SDK, app and test files above, the two new SDK files and
`M docs/sdk-reference.html` (its capability table gains `browser.provide`).

- [ ] **Step 10: Commit**

```bash
git add jasper-sdk/src/main/java/dev/jasper/sdk/Capabilities.java jasper-sdk/src/main/java/dev/jasper/sdk/ui/LinkOrigin.java \
    jasper-sdk/src/main/java/dev/jasper/sdk/ui/WebBrowser.java jasper-sdk/src/main/java/dev/jasper/sdk/ui/Platform.java \
    jasper-sdk/src/main/java/dev/jasper/sdk/plugin/PluginContext.java jasper-sdk/src/test/java/dev/jasper/sdk/CapabilitiesTest.java \
    jasper-sdk/src/test/java/dev/jasper/sdk/terminal/TerminalValuesTest.java jasper-sdk/src/test/java/dev/jasper/sdk/ui/UiValuesTest.java \
    jasper-app/src/main/java/dev/jasper/app/plugins/DescriptorParser.java jasper-app/src/main/java/dev/jasper/app/pluginmanager/Capabilities.java \
    jasper-app/src/test/java/dev/jasper/app/plugins/DescriptorParserTest.java \
    jasper-app/src/test/java/dev/jasper/app/pluginmanager/CapabilityDescriptionsTest.java docs/sdk-reference.html
git commit -F - <<'MSG'
Browser link routing: the browser.provide capability and the SDK's web browser types

WebBrowser and LinkOrigin (CLICK, PROGRAM) join dev.jasper.sdk.ui, and
Platform.browser(WebBrowser) is a default method like Platform.editor;
the hosts provide it in the next commit. browser.provide is the
thirteenth capability: the descriptor parser accepts it and the review
sheet says "Open the web addresses Jasper opens, such as links you click
in a terminal". The SDK stays 0.21.0; the SDK reference is regenerated.

🤖 Generated with Claude Code at The Home Depot

Co-Authored-By: Claude <noreply@anthropic.com>
MSG
```

---

### Task 2: The web browser role on both runtimes

Spec 6 and 14 ("covered by the testkit and the contract cases on both runtimes"), brief 1 and 3: `Platform.openInBrowser`,
the app's `WebBrowsers` and `HostedContext`, the fake, and four contract cases modelled on the editor role's and on
`browseOpensOnlyWebAddressesAndReachesTheHost`.

**Files:**
- Create: `jasper-app/src/main/java/dev/jasper/app/plugins/WebBrowsers.java` (and its ledger row)
- Modify: `jasper-sdk/src/main/java/dev/jasper/sdk/ui/Platform.java` (`browse`'s Javadoc `:23-24`, after `:30`, `browser`'s
  Javadoc); `jasper-app/src/main/java/dev/jasper/app/plugins/PluginHost.java` (after `:124`, `:142`);
  `jasper-app/src/main/java/dev/jasper/app/plugins/HostedContext.java` (`browse` `:217-227`, after `editor` `:259`, a
  helper after `copied` `:273`); `jasper-sdk-testkit/src/main/java/dev/jasper/sdk/testing/FakePluginContext.java` (after
  `:135`); `jasper-sdk-testkit/src/main/java/dev/jasper/sdk/testing/FakePluginHost.java` (teardown `:871-872`, `browsed`
  `:1854-1859`, `declareBundled`'s Javadoc `:2102-2103`, after `editorPlugin` `:2166`);
  `docs/design/theme-engine/migration.csv` (a row); `docs/sdk-reference.html` (generated)
- Test: `jasper-sdk-testkit/src/testFixtures/java/dev/jasper/sdk/testing/contract/ContractHarness.java` (`:300`, `:323`,
  after `:329`), `PluginContractTest.java` (before `theCommandLineNamesTheClientOnceTheHostKnowsIt`),
  `jasper-sdk-testkit/src/test/java/dev/jasper/sdk/testing/FakeContractTest.java` (after `:154`),
  `jasper-app/src/test/java/dev/jasper/app/plugins/AppContractTest.java` (after `:628`)

**Interfaces:**
- Consumes: Task 1's `WebBrowser`, `LinkOrigin`, `Capabilities.BROWSER_PROVIDE`, `Platform.browser`;
  `FileEditors.bundled(String)`; `Containment.attempt(String, String, Callable<?>)`; `HostedTerminals.windowHandle(UUID)`;
  `HostedUi.tracked(Runnable)`; `FakePluginHost.bundledPlugins`, `failures`, `workspace.lastActive`.
- Produces:
  - `void Platform.openInBrowser(URI uri)` (abstract): absolute http or https only, else `IllegalArgumentException`;
  - `final class dev.jasper.app.plugins.WebBrowsers`: `WebBrowsers(Containment containment, Predicate<String> bundled)`,
    `dev.jasper.app.lifecycle.Subscription register(String pluginId, WebBrowser browser, Function<UUID, Optional<WindowOwner>> windows)`,
    `boolean open(URI address, LinkOrigin origin, Optional<UUID> windowId)` (UI thread);
  - `PluginHost.webBrowsers` (package-private `final WebBrowsers`);
  - `public boolean FakePluginHost.openLink(URI address, LinkOrigin origin, Optional<UUID> windowId)`,
    `public Optional<String> FakePluginHost.browserPlugin()`; package-private `provideBrowser`, `activeWindowId()`;
  - `boolean ContractHarness.openLink(java.net.URI address, dev.jasper.sdk.ui.LinkOrigin origin, java.util.Optional<java.util.UUID> windowId)`.

- [ ] **Step 1: Write the failing contract cases and the harness method**

```bash
python3 -I - <<'PY'
import sys; sys.path.insert(0, "build/plan-browser3")
from edits import edit
edit("jasper-sdk-testkit/src/testFixtures/java/dev/jasper/sdk/testing/contract/ContractHarness.java", [
(r'''        /** Every web address a plugin asked to open in the browser, in request order. */
''',
 r'''        /**
         * Every web address that reached the system browser, in request order: each {@code Platform.browse}, and each
         * {@code Platform.openInBrowser} that no provided browser opened.
         */
''', 1),
(r'''        /** Marks a plugin as one that ships with Jasper, whose editor replaces a non-bundled plugin's; before that plugin starts. */''',
 r'''        /**
         * Marks a plugin as one that ships with Jasper, whose editor and browser replace a non-bundled plugin's; before that
         * plugin starts.
         */''', 1),
(r'''        boolean edit(java.nio.file.Path file, java.util.OptionalInt line, java.util.Optional<java.util.UUID> windowId);
''',
 r'''        boolean edit(java.nio.file.Path file, java.util.OptionalInt line, java.util.Optional<java.util.UUID> windowId);
        /**
         * Opens a web address as Jasper's own routes do (a link clicked in a terminal, the {@code jasper} command), with the
         * browser a plugin provides; false when none does, or it declined or threw. Asked on the UI thread.
         */
        boolean openLink(java.net.URI address, dev.jasper.sdk.ui.LinkOrigin origin, java.util.Optional<java.util.UUID> windowId);
''', 1)])
edit("jasper-sdk-testkit/src/testFixtures/java/dev/jasper/sdk/testing/contract/PluginContractTest.java", [
(r'''    @Test void theCommandLineNamesTheClientOnceTheHostKnowsIt() {''',
 r'''    /** A browser that records {@code <address>|<origin>|<window id or ->} and answers {@code answer}. */
    private static dev.jasper.sdk.ui.WebBrowser recordingBrowser(List<String> calls, boolean answer) {
        return (address, origin, window) -> {
            calls.add(address + "|" + origin + "|"
                + window.map(owner -> ((dev.jasper.sdk.terminal.WindowHandle) owner).id().toString()).orElse("-"));
            return answer;
        };
    }

    private static final java.net.URI PAGE = java.net.URI.create("https://example.com/page");

    /** Browser plan 3: the web browser role needs its capability, and one plugin provides it at a time. */
    @Test void providingTheBrowserNeedsTheCapabilityAndOnePluginProvidesItAtATime() {
        var bare = new AtomicReference<PluginContext>();
        var a = new AtomicReference<PluginContext>();
        var b = new AtomicReference<PluginContext>();
        h.start(info("test.bare"), Set.of(), Set.of(), bare::set);
        h.start(info("test.a", Capabilities.BROWSER_PROVIDE), Set.of(), Set.of(), a::set);
        h.start(info("test.b", Capabilities.BROWSER_PROVIDE), Set.of(), Set.of(), b::set);
        List<String> calls = new CopyOnWriteArrayList<>();
        assertThat(h.openLink(PAGE, dev.jasper.sdk.ui.LinkOrigin.CLICK, Optional.empty())).as("no plugin provides one").isFalse();
        h.ui(() -> {
            assertThatThrownBy(() -> bare.get().platform().browser(recordingBrowser(calls, true)))
                .isInstanceOfSatisfying(MissingCapabilityException.class, failure -> assertThat(failure.capability()).isEqualTo("browser.provide"));
            a.get().platform().browser(recordingBrowser(calls, true));
            assertThatIllegalStateException().isThrownBy(() -> b.get().platform().browser(recordingBrowser(calls, true)))
                .withMessage("The browser is already provided by test.a");
            assertThatIllegalStateException().as("the same plugin twice").isThrownBy(() -> a.get().platform().browser(recordingBrowser(calls, true)))
                .withMessage("The browser is already provided by test.a");
            assertThatThrownBy(() -> a.get().platform().browser(null)).isInstanceOf(NullPointerException.class);
        });
        assertThat(h.openLink(PAGE, dev.jasper.sdk.ui.LinkOrigin.CLICK, Optional.empty())).isTrue();
        assertThat(h.openLink(PAGE, dev.jasper.sdk.ui.LinkOrigin.PROGRAM, Optional.empty())).isTrue();
        assertThat(calls).containsExactly(PAGE + "|CLICK|-", PAGE + "|PROGRAM|-");
    }

    /** Browser plan 3: a bundled plugin's browser replaces another plugin's, and the role ends with its plugin. */
    @Test void aBundledPluginsBrowserReplacesAnotherPluginsAndEndsWithItsPlugin() {
        h.declareBundled("test.z");
        var a = new AtomicReference<PluginContext>();
        var z = new AtomicReference<PluginContext>();
        h.start(info("test.a", Capabilities.BROWSER_PROVIDE), Set.of(), Set.of(), a::set);
        h.start(info("test.z", Capabilities.BROWSER_PROVIDE), Set.of(), Set.of(), z::set);
        List<String> byA = new CopyOnWriteArrayList<>(), byZ = new CopyOnWriteArrayList<>();
        var replaced = new AtomicReference<Subscription>();
        h.ui(() -> {
            replaced.set(a.get().platform().browser(recordingBrowser(byA, true)));
            z.get().platform().browser(recordingBrowser(byZ, true));
            assertThatIllegalStateException().isThrownBy(() -> a.get().platform().browser(recordingBrowser(byA, true)))
                .withMessage("The browser is already provided by test.z");
        });
        assertThat(h.openLink(PAGE, dev.jasper.sdk.ui.LinkOrigin.CLICK, Optional.empty())).isTrue();
        h.ui(() -> replaced.get().close());
        assertThat(h.openLink(PAGE, dev.jasper.sdk.ui.LinkOrigin.PROGRAM, Optional.empty()))
            .as("closing the replaced registration leaves the bundled one").isTrue();
        assertThat(byA).isEmpty();
        assertThat(byZ).containsExactly(PAGE + "|CLICK|-", PAGE + "|PROGRAM|-");
        h.stopAll();
        assertThat(h.openLink(PAGE, dev.jasper.sdk.ui.LinkOrigin.CLICK, Optional.empty())).as("the registration ends with its plugin").isFalse();
    }

    /** Browser plan 3, Review Focus 2: a browser that throws or declines gives false; one gets its plugin's window handle. */
    @Test void aBrowserThatThrowsOrDeclinesGivesFalseAndOneGetsItsPluginsHandleForAnOpenWindow() {
        UUID window = h.addTerminalWindow();
        var a = new AtomicReference<PluginContext>();
        h.start(info("test.a", Capabilities.BROWSER_PROVIDE), Set.of(), Set.of(), a::set);
        List<String> calls = new CopyOnWriteArrayList<>();
        dev.jasper.sdk.ui.WebBrowser recording = recordingBrowser(calls, true);
        h.ui(() -> a.get().platform().browser((address, origin, owner) -> {
            if (address.getPath().equals("/broken")) throw new IllegalStateException("the browser broke");
            if (address.getPath().equals("/declined")) return false;
            return recording.open(address, origin, owner);
        }));
        assertThat(h.openLink(java.net.URI.create("https://example.com/broken"), dev.jasper.sdk.ui.LinkOrigin.CLICK, Optional.empty()))
            .as("a browser that throws").isFalse();
        assertThat(h.openLink(java.net.URI.create("https://example.com/declined"), dev.jasper.sdk.ui.LinkOrigin.CLICK, Optional.of(window)))
            .as("a browser that declines").isFalse();
        assertThat(h.openLink(PAGE, dev.jasper.sdk.ui.LinkOrigin.CLICK, Optional.of(window))).isTrue();
        assertThat(h.openLink(PAGE, dev.jasper.sdk.ui.LinkOrigin.PROGRAM, Optional.of(UUID.randomUUID())))
            .as("a window that is not open is no window").isTrue();
        assertThat(calls).containsExactly(PAGE + "|CLICK|" + window, PAGE + "|PROGRAM|-");
    }

    /**
     * Browser plan 3, Review Focus 2: {@code openInBrowser} asks the provided browser as a click in the active window, and
     * an address it declines reaches the system browser; {@code browse} never asks it.
     */
    @Test void openInBrowserAsksTheProvidedBrowserAsAClickInTheActiveWindowAndBrowseNever() {
        UUID window = h.addTerminalWindow();
        h.activateTerminalWindow(window);
        var a = new AtomicReference<PluginContext>();
        var user = new AtomicReference<PluginContext>();
        h.start(info("test.a", Capabilities.BROWSER_PROVIDE), Set.of(), Set.of(), a::set);
        h.start(info("test.user"), Set.of(), Set.of(), user::set);
        List<String> calls = new CopyOnWriteArrayList<>();
        dev.jasper.sdk.ui.WebBrowser recording = recordingBrowser(calls, true);
        h.ui(() -> a.get().platform().browser((address, origin, owner) ->
            recording.open(address, origin, owner) && !address.getPath().equals("/declined")));
        java.net.URI declined = java.net.URI.create("https://example.com/declined");
        java.net.URI direct = java.net.URI.create("http://localhost:5432/");
        h.ui(() -> {
            user.get().platform().openInBrowser(PAGE);
            user.get().platform().openInBrowser(declined);
            user.get().platform().browse(direct);
            assertThatIllegalArgumentException().isThrownBy(() -> user.get().platform().openInBrowser(java.net.URI.create("mailto:someone@example.com")));
            assertThatIllegalArgumentException().isThrownBy(() -> user.get().platform().openInBrowser(java.net.URI.create("/relative")));
            assertThatThrownBy(() -> user.get().platform().openInBrowser(null)).isInstanceOf(NullPointerException.class);
        });
        h.flush();
        // Up to 10 s: the app host hops to the UI thread and then to the plugin's worker, which a loaded CI can delay.
        for (int i = 0; i < 1000 && (calls.size() < 2 || h.browsed().size() < 2); i++) {
            try { Thread.sleep(10); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
        }
        assertThat(calls).containsExactly(PAGE + "|CLICK|" + window, declined + "|CLICK|" + window);
        assertThat(h.browsed()).as("the declined address and browse reach the system browser").containsExactlyInAnyOrder(declined, direct);
    }

    @Test void theCommandLineNamesTheClientOnceTheHostKnowsIt() {''', 1)])
edit("jasper-sdk-testkit/src/test/java/dev/jasper/sdk/testing/FakeContractTest.java", [
(r'''                return host.edit(file, line, windowId);
            }
''',
 r'''                return host.edit(file, line, windowId);
            }
            @Override public boolean openLink(java.net.URI address, dev.jasper.sdk.ui.LinkOrigin origin, java.util.Optional<java.util.UUID> windowId) {
                return host.openLink(address, origin, windowId);
            }
''', 1)])
edit("jasper-app/src/test/java/dev/jasper/app/plugins/AppContractTest.java", [
(r'''                return onEdtValue(() -> host.fileEditors.edit(file, line, windowId));
            }
''',
 r'''                return onEdtValue(() -> host.fileEditors.edit(file, line, windowId));
            }
            @Override public boolean openLink(java.net.URI address, dev.jasper.sdk.ui.LinkOrigin origin, Optional<UUID> windowId) {
                return onEdtValue(() -> host.webBrowsers.open(address, origin, windowId));
            }
''', 1)])
PY
```

- [ ] **Step 2: Run the fake's contract to verify it fails**

Run: `./gradlew :jasper-sdk-testkit:test --tests 'dev.jasper.sdk.testing.FakeContractTest'`
Expected: FAIL to compile `compileTestFixturesJava` or `compileTestJava`: `cannot find symbol` for
`Platform.openInBrowser` and `FakePluginHost.openLink`.

- [ ] **Step 3: Add `openInBrowser` to the SDK and the fake**

```bash
python3 -I - <<'PY'
import sys; sys.path.insert(0, "build/plan-browser3")
from edits import edit
edit("jasper-sdk/src/main/java/dev/jasper/sdk/ui/Platform.java", [
(r'''     * Opens a web address in the user's browser. Runs in the background; a failure is posted in the plugin's
     * notification group, never thrown.
''',
 r'''     * Opens a web address in the system browser, even when a plugin provides Jasper's web browser
     * ({@link #openInBrowser} asks that one first). Runs in the background; a failure is posted in the plugin's
     * notification group, never thrown.
''', 1),
(r'''    void browse(URI uri);
''',
 r'''    void browse(URI uri);

    /**
     * Opens a web address the user asked for, such as a link clicked in the plugin's view: the web browser a plugin
     * provides (see {@link #browser}), asked on the UI thread with {@link LinkOrigin#CLICK} and the active Jasper
     * window, then the system browser, as {@link #browse} opens it. Returns at once; a failure is posted in the
     * plugin's notification group, never thrown.
     *
     * @param uri an absolute {@code http} or {@code https} address
     * @throws IllegalArgumentException when the address is not an absolute http or https one
     * @since 0.21.0
     */
    void openInBrowser(URI uri);
''', 1),
(r'''     * {@code openInBrowser} and Jasper's own web links''',
 r'''     * {@link #openInBrowser} and Jasper's own web links''', 1)])
edit("jasper-sdk-testkit/src/main/java/dev/jasper/sdk/testing/FakePluginContext.java", [
(r'''                @Override public void browse(java.net.URI uri) { requireOpen(); host.browsed.add(webAddress(uri)); }
''',
 r'''                @Override public void browse(java.net.URI uri) { requireOpen(); host.browsed.add(webAddress(uri)); }
                @Override public void openInBrowser(java.net.URI uri) {
                    requireOpen();
                    java.net.URI address = webAddress(uri);
                    // As the application: the browser a plugin provides first, as a click in the active window; here on the caller's thread.
                    if (!host.openLink(address, dev.jasper.sdk.ui.LinkOrigin.CLICK, host.activeWindowId())) host.browsed.add(address);
                }
                @Override public Subscription browser(dev.jasper.sdk.ui.WebBrowser browser) {
                    requireOpen();
                    if (!info.capabilities().contains(dev.jasper.sdk.Capabilities.BROWSER_PROVIDE))
                        throw new dev.jasper.sdk.MissingCapabilityException(info.id(), dev.jasper.sdk.Capabilities.BROWSER_PROVIDE);
                    Objects.requireNonNull(browser, "browser");
                    return host.provideBrowser(FakePluginContext.this, browser);
                }
''', 1)])
edit("jasper-sdk-testkit/src/main/java/dev/jasper/sdk/testing/FakePluginHost.java", [
(r'''        ProvidedEditor provided = providedEditor;
        if (provided != null && provided.context() == context) providedEditor = null;
''',
 r'''        ProvidedEditor provided = providedEditor;
        if (provided != null && provided.context() == context) providedEditor = null;
        ProvidedBrowser browser = providedBrowser;
        if (browser != null && browser.context() == context) providedBrowser = null;
''', 1),
(r'''    /**
     * Every web address a plugin asked to open in the browser.
     *
     * @return the addresses in request order
     */
''',
 r'''    /**
     * Every web address that reached the system browser: each {@code Platform.browse}, and each
     * {@code Platform.openInBrowser} that no provided browser opened.
     *
     * @return the addresses in request order
     */
''', 1),
(r'''     * Marks a plugin as one that ships with Jasper, before it starts: its {@code Platform.editor} registration replaces
     * a non-bundled plugin's, and its split items come first in the Split lists, as the application's bundled plugins' do.
''',
 r'''     * Marks a plugin as one that ships with Jasper, before it starts: its {@code Platform.editor} and
     * {@code Platform.browser} registrations replace a non-bundled plugin's, and its split items come first in the Split
     * lists, as the application's bundled plugins' do.
''', 1),
(r'''    public Optional<String> editorPlugin() { return Optional.ofNullable(providedEditor).map(provided -> provided.context().plugin().id()); }
''',
 r'''    public Optional<String> editorPlugin() { return Optional.ofNullable(providedEditor).map(provided -> provided.context().plugin().id()); }

    /** The web browser a plugin provides ({@code Platform.browser}) and the plugin that registered it. */
    record ProvidedBrowser(FakePluginContext context, dev.jasper.sdk.ui.WebBrowser browser) { }
    private volatile ProvidedBrowser providedBrowser;

    /** Makes a plugin's browser the one web addresses open with, under the application's one-at-a-time rule. */
    Subscription provideBrowser(FakePluginContext context, dev.jasper.sdk.ui.WebBrowser browser) {
        String pluginId = context.plugin().id();
        ProvidedBrowser current = providedBrowser;
        if (current != null && (!bundledPlugins.contains(pluginId) || bundledPlugins.contains(current.context().plugin().id())))
            throw new IllegalStateException("The browser is already provided by " + current.context().plugin().id());
        var provided = new ProvidedBrowser(context, browser);
        providedBrowser = provided;
        Subscription subscription = () -> { if (providedBrowser == provided) providedBrowser = null; };
        context.own(subscription);
        return subscription;
    }

    /** The open window the user activated last, as {@code Platform.openInBrowser} reads it; empty when none is. */
    Optional<UUID> activeWindowId() {
        return Optional.ofNullable(workspace.lastActive).filter(window -> window.open).map(window -> window.id);
    }

    /**
     * Opens a web address as Jasper's own routes do (a link clicked in a terminal, the {@code jasper} command): with the
     * browser a plugin provides through {@code Platform.browser}, given that plugin's handle for {@code windowId} while
     * the window is open.
     *
     * @param address an absolute http or https address
     * @param origin who asked for it
     * @param windowId the Jasper window the address came from, or empty
     * @return what the browser answered; false when no plugin provides one, or it threw, which {@link #failures()} records
     * @since 0.21.0
     */
    public boolean openLink(java.net.URI address, dev.jasper.sdk.ui.LinkOrigin origin, Optional<UUID> windowId) {
        Objects.requireNonNull(address, "address");
        Objects.requireNonNull(origin, "origin");
        Objects.requireNonNull(windowId, "windowId");
        ProvidedBrowser provided = providedBrowser;
        if (provided == null) return false;
        Optional<dev.jasper.sdk.WindowOwner> window = windowId.flatMap(id -> provided.context().terminals.window(id))
            .map(handle -> (dev.jasper.sdk.WindowOwner) handle);
        try {
            return provided.browser().open(address, origin, window);
        } catch (RuntimeException failure) {
            failures.add(provided.context().plugin().id() + " web browser: " + failure);
            return false;
        }
    }

    /**
     * Which plugin provides the web browser now.
     *
     * @return its id, or empty
     * @since 0.21.0
     */
    public Optional<String> browserPlugin() { return Optional.ofNullable(providedBrowser).map(provided -> provided.context().plugin().id()); }
''', 1)])
PY
```

- [ ] **Step 4: Run the fake's contract to verify it passes**

Run: `./gradlew :jasper-sdk-testkit:test --tests 'dev.jasper.sdk.testing.FakeContractTest'`
Expected: `BUILD SUCCESSFUL` (this run does not compile `jasper-app`); the XML under
`jasper-sdk-testkit/build/test-results/test/` shows the four new cases passing, with 0 failures.

- [ ] **Step 5: Run the app's contract to verify it fails**

Run: `./gradlew :jasper-app:test --tests 'dev.jasper.app.plugins.AppContractTest'`
Expected: FAIL to compile `:jasper-app:compileJava`: `HostedContext`'s anonymous `Platform` does not override
`openInBrowser(URI)`; and `compileTestJava`: `cannot find symbol` `webBrowsers`.

- [ ] **Step 6: Add `WebBrowsers`, its ledger row, and the host's `browser` and `openInBrowser`**

```bash
python3 -I - <<'PY'
import sys; sys.path.insert(0, "build/plan-browser3")
from edits import edit, create, append
create("jasper-app/src/main/java/dev/jasper/app/plugins/WebBrowsers.java", r'''package dev.jasper.app.plugins;

import dev.jasper.sdk.WindowOwner;
import dev.jasper.sdk.ui.LinkOrigin;
import dev.jasper.sdk.ui.WebBrowser;
import java.net.URI;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;
import java.util.function.Predicate;

/**
 * The web browser a plugin provides ({@code Platform.browser}), which Jasper's web addresses ask before the system
 * browser (browser plugin spec 6). One at a time: a bundled plugin's registration replaces a non-bundled one's; any
 * other later registration, the same plugin's included, throws. The browser runs on the UI thread inside its plugin's
 * containment and must not block, so nothing waits for it.
 */
final class WebBrowsers {
    private static final System.Logger LOG = System.getLogger(WebBrowsers.class.getName());

    /** A registration: the plugin, its browser, and its own handle for an open Jasper window's id. */
    private record Holder(String pluginId, WebBrowser browser, Function<UUID, Optional<WindowOwner>> windows) { }

    private final Containment containment;
    private final Predicate<String> bundled;
    private volatile Holder holder;

    /**
     * @param containment runs the browser inside its plugin's containment
     * @param bundled whether a plugin ships with Jasper, as {@code FileEditors.bundled(String)} answers
     */
    WebBrowsers(Containment containment, Predicate<String> bundled) {
        this.containment = Objects.requireNonNull(containment, "containment");
        this.bundled = Objects.requireNonNull(bundled, "bundled");
    }

    /**
     * Makes {@code pluginId}'s browser the one Jasper opens web addresses with. UI thread.
     *
     * @param windows the registering plugin's own handle for an open Jasper window's id
     * @return removes the browser, unless a bundled plugin's has replaced it since
     * @throws IllegalStateException when a plugin provides the browser already, unless this is a bundled plugin replacing
     *     a non-bundled one's
     */
    dev.jasper.app.lifecycle.Subscription register(String pluginId, WebBrowser browser, Function<UUID, Optional<WindowOwner>> windows) {
        Objects.requireNonNull(pluginId, "pluginId");
        Objects.requireNonNull(browser, "browser");
        Objects.requireNonNull(windows, "windows");
        Holder current = holder;
        if (current != null) {
            if (!bundled.test(pluginId) || bundled.test(current.pluginId()))
                throw new IllegalStateException("The browser is already provided by " + current.pluginId());
            LOG.log(System.Logger.Level.INFO, "The bundled plugin " + pluginId + " provides the browser instead of " + current.pluginId());
        }
        var registered = new Holder(pluginId, browser, windows);
        holder = registered;
        return new dev.jasper.app.lifecycle.Subscription(() -> { if (holder == registered) holder = null; });
    }

    /**
     * Asks the provided browser to open {@code address}. UI thread.
     *
     * @param windowId the Jasper window the address came from; the browser gets its plugin's handle while it is open
     * @return true when it opened the address; false when no plugin provides a browser, or it declined or threw
     */
    boolean open(URI address, LinkOrigin origin, Optional<UUID> windowId) {
        Objects.requireNonNull(address, "address");
        Objects.requireNonNull(origin, "origin");
        Objects.requireNonNull(windowId, "windowId");
        Holder current = holder;
        if (current == null) return false;
        Optional<WindowOwner> window = windowId.flatMap(current.windows());
        var opened = new AtomicBoolean();
        Throwable failure = containment.attempt(current.pluginId(), "web browser", () -> {
            opened.set(current.browser().open(address, origin, window));
            return null;
        });
        return failure == null && opened.get();
    }
}
''')
append("docs/design/theme-engine/migration.csv",
    "jasper-app/src/main/java/dev/jasper/app/plugins/WebBrowsers.java,app.plugins,WebBrowsers,nonvisual-helper,none,none,"
    "jasper-app/src/test/java/dev/jasper/app/plugins/AppContractTest.java,New in browser plan 3 Task 2; registry of the plugin web "
    "browser with no Swing component or theme dependency; matched only by its dev.jasper.sdk.ui import\n")
edit("jasper-app/src/main/java/dev/jasper/app/plugins/PluginHost.java", [
(r'''    final FileEditors fileEditors;
''',
 r'''    final FileEditors fileEditors;
    /** The web browser a plugin provides ({@code Platform.browser}), which Jasper's web addresses ask first. */
    final WebBrowsers webBrowsers;
''', 1),
(r'''        this.fileEditors = new FileEditors(environment.ui(), environment.onUi(), containment);
''',
 r'''        this.fileEditors = new FileEditors(environment.ui(), environment.onUi(), containment);
        this.webBrowsers = new WebBrowsers(containment, fileEditors::bundled);
''', 1)])
edit("jasper-app/src/main/java/dev/jasper/app/plugins/HostedContext.java", [
(r'''                @Override public void browse(URI uri) {
                    requireOpen();
                    URI address = webAddress(uri);
                    background().execute(() -> {
                        try { host.environment.browser().accept(address); }
                        catch (RuntimeException failure) {
                            log().log(System.Logger.Level.WARNING, "Could not open " + address + " in a browser", failure);
                            notifications.group().error("Could not open a web address", address.toString(), failure);
                        }
                    });
                }
''',
 r'''                @Override public void browse(URI uri) {
                    requireOpen();
                    systemBrowser(webAddress(uri));
                }
                @Override public void openInBrowser(URI uri) {
                    requireOpen();
                    URI address = webAddress(uri);
                    host.environment.ui().accept(() -> {
                        Optional<java.util.UUID> window = host.environment.terminals().activeWindow().map(dev.jasper.app.terminals.WindowEntry::id);
                        if (host.webBrowsers.open(address, dev.jasper.sdk.ui.LinkOrigin.CLICK, window)) return;
                        try { systemBrowser(address); }
                        catch (RejectedExecutionException stopped) {
                            log().log(System.Logger.Level.WARNING, "Could not open a web address: the plugin stopped first");
                        }
                    });
                }
''', 1),
(r'''                    // HostedUi.closeAll, which teardown runs, closes it when the plugin stops or fails to start.
                    return ui.tracked(registration::close);
                }
            };
''',
 r'''                    // HostedUi.closeAll, which teardown runs, closes it when the plugin stops or fails to start.
                    return ui.tracked(registration::close);
                }
                @Override public Subscription browser(dev.jasper.sdk.ui.WebBrowser browser) {
                    requireOpen();
                    requireUi("Platform.browser");
                    gate.require(Capabilities.BROWSER_PROVIDE);
                    Objects.requireNonNull(browser, "browser");
                    dev.jasper.app.lifecycle.Subscription registration = host.webBrowsers.register(id, browser,
                        window -> host.environment.terminals().window(window)
                            .<dev.jasper.sdk.WindowOwner>map(entry -> terminals.windowHandle(entry.id())));
                    gate.audit(Capabilities.BROWSER_PROVIDE, "became the web browser Jasper opens web addresses with");
                    // As the editor's: HostedUi.closeAll closes it when the plugin stops or fails to start.
                    return ui.tracked(registration::close);
                }
            };
''', 1),
(r'''        /** Only an absolute http or https address opens in a browser. */
''',
 r'''        /** Opens an address in the system browser on this plugin's worker; a failure is posted in its group, never thrown. */
        private void systemBrowser(URI address) {
            background().execute(() -> {
                try { host.environment.browser().accept(address); }
                catch (RuntimeException failure) {
                    log().log(System.Logger.Level.WARNING, "Could not open " + address + " in a browser", failure);
                    notifications.group().error("Could not open a web address", address.toString(), failure);
                }
            });
        }

        /** Only an absolute http or https address opens in a browser. */
''', 1)])
PY
```

- [ ] **Step 7: Run both contracts and the neighbours to verify they pass**

Run: `./gradlew :jasper-sdk-testkit:test --tests 'dev.jasper.sdk.testing.FakeContractTest' :jasper-app:test --tests 'dev.jasper.app.plugins.AppContractTest' --tests 'dev.jasper.app.plugins.FileEditorsTest' --tests 'dev.jasper.app.appearance.ThemeMigrationCoverageTest'`
Expected: `BUILD SUCCESSFUL`; each runtime's XML shows `providingTheBrowserNeedsTheCapabilityAndOnePluginProvidesItAtATime`,
`aBundledPluginsBrowserReplacesAnotherPluginsAndEndsWithItsPlugin`,
`aBrowserThatThrowsOrDeclinesGivesFalseAndOneGetsItsPluginsHandleForAnOpenWindow` and
`openInBrowserAsksTheProvidedBrowserAsAClickInTheActiveWindowAndBrowseNever` passing, and
`browseOpensOnlyWebAddressesAndReachesTheHost` still passing.

- [ ] **Step 8: Regenerate the SDK reference and run the SDK and plugin checks**

Run: `./gradlew sdkReference && ./gradlew verifySdkReference verifySdkArchitecture verifyApplicationArchitecture :jasper-sdk:javadoc :jasper-plugin-editor:test --tests 'dev.jasper.editor.ui.MarkdownNavigationTest' :jasper-plugin-remote:test --tests 'dev.jasper.remote.TunnelsPluginTest'`
Expected: `BUILD SUCCESSFUL` twice (the plugins still call `browse`, and their tests still read `browsed()`).

- [ ] **Step 9: Commit**

```bash
git add jasper-sdk/src/main/java/dev/jasper/sdk/ui/Platform.java jasper-app/src/main/java/dev/jasper/app/plugins/WebBrowsers.java \
    jasper-app/src/main/java/dev/jasper/app/plugins/PluginHost.java jasper-app/src/main/java/dev/jasper/app/plugins/HostedContext.java \
    jasper-sdk-testkit/src/main/java/dev/jasper/sdk/testing/FakePluginContext.java \
    jasper-sdk-testkit/src/main/java/dev/jasper/sdk/testing/FakePluginHost.java \
    jasper-sdk-testkit/src/testFixtures/java/dev/jasper/sdk/testing/contract/ContractHarness.java \
    jasper-sdk-testkit/src/testFixtures/java/dev/jasper/sdk/testing/contract/PluginContractTest.java \
    jasper-sdk-testkit/src/test/java/dev/jasper/sdk/testing/FakeContractTest.java \
    jasper-app/src/test/java/dev/jasper/app/plugins/AppContractTest.java docs/design/theme-engine/migration.csv docs/sdk-reference.html
git commit -F - <<'MSG'
Browser link routing: the web browser role on both runtimes

Platform.openInBrowser asks the web browser a plugin provides, as a
click in the active window, then opens the system browser as browse
does; browse always opens the system browser. The application keeps the
registration in plugins.WebBrowsers (one at a time, a bundled plugin's
replacing another's, asked inside its plugin's containment, ending with
its plugin), and the testkit fakes it (FakePluginHost.openLink,
browserPlugin). Four contract cases pin the role on both runtimes.

🤖 Generated with Claude Code at The Home Depot

Co-Authored-By: Claude <noreply@anthropic.com>
MSG
```

---

### Task 3: A public link opener in jasper-terminal

Spec 6 ("jasper-terminal also needs a supported way for the app to set a view's link opener"), brief 2:
`TerminalView.setLinkOpener(Consumer<String>)` becomes public, beside `setFileLinkHandler`, with its Javadoc's style;
`null` restores the desktop default. The guides change in Task 7.

**Files:**
- Modify: `jasper-terminal/src/main/java/dev/jasper/terminal/view/TerminalView.java` (`:579-581` removed; after `:296`)
- Test: `jasper-terminal/src/test/java/dev/jasper/terminal/view/TerminalViewInteractionTest.java` (after
  `theLinkModifierOpensTheLinkUnderThePointer`, `:227-236`)

**Interfaces:**
- Consumes: nothing from earlier tasks.
- Produces: `public void TerminalView.setLinkOpener(Consumer<String> opener)`: each link a Cmd-click (Ctrl-click
  elsewhere) opens, as the terminal read it, on the EDT; `null` puts `DesktopServices::openBrowser` back.

- [ ] **Step 1: Write the failing test**

```bash
python3 -I - <<'PY'
import sys; sys.path.insert(0, "build/plan-browser3")
from edits import edit
edit("jasper-terminal/src/test/java/dev/jasper/terminal/view/TerminalViewInteractionTest.java", [
(r'''    @Test
    void commandClickOpensALinkEvenWhenTheProgramWantsTheMouse() throws Exception {''',
 r'''    /** Browser plan 3: the link opener is public, and null puts the desktop's browser back. */
    @Test
    void aNullLinkOpenerRestoresTheDesktopDefault() throws Exception {
        assertThat(TerminalView.class.getMethod("setLinkOpener", java.util.function.Consumer.class)).as("app-facing").isNotNull();
        List<String> opened = new ArrayList<>();
        view.setLinkOpener(opened::add);
        show("go https://m.dev x", 0, "go https://m.dev x");

        view.handleMouse(press(8, 0, 1, LINK));
        view.handleMouse(release(8, 0));
        view.setLinkOpener(null);
        view.handleMouse(press(8, 0, 1, LINK));
        view.handleMouse(release(8, 0));

        assertThat(opened).as("the second click went to the desktop's browser, which a headless test cannot open")
            .containsExactly("https://m.dev");
    }

    @Test
    void commandClickOpensALinkEvenWhenTheProgramWantsTheMouse() throws Exception {''', 1)])
PY
```

- [ ] **Step 2: Run it to verify it fails**

Run: `./gradlew :jasper-terminal:test --tests 'dev.jasper.terminal.view.TerminalViewInteractionTest'`
Expected: FAIL: `aNullLinkOpenerRestoresTheDesktopDefault` with
`java.lang.NoSuchMethodException: dev.jasper.terminal.view.TerminalView.setLinkOpener(java.util.function.Consumer)`.

- [ ] **Step 3: Make the link opener public**

```bash
python3 -I - <<'PY'
import sys; sys.path.insert(0, "build/plan-browser3")
from edits import edit
edit("jasper-terminal/src/main/java/dev/jasper/terminal/view/TerminalView.java", [
(r'''    void setLinkOpener(Consumer<String> opener) {
        this.linkOpener = opener;
    }

''', '', 1),
(r'''    public void setFileLinkHandler(Consumer<FileLink> handler) {
        fileLinkHandler = handler;
    }
''',
 r'''    public void setFileLinkHandler(Consumer<FileLink> handler) {
        fileLinkHandler = handler;
    }

    /**
     * Receives each URL a Cmd-click (macOS) or Ctrl-click (Linux and Windows) opens: an {@code http}, {@code https}
     * or {@code ftp} URL written in the output, following soft wraps, or an OSC 8 link whose target is {@code http},
     * {@code https}, {@code ftp} or {@code mailto}, as the program wrote it. The text is unauthenticated program output:
     * an OSC 8 link's target can differ from the text shown, and it may not parse as a URI. Without an opener, the
     * default, the desktop's browser opens it. Called on the Event Dispatch Thread, so the opener must not block.
     *
     * @param opener the receiver, or {@code null} for the desktop's browser
     */
    public void setLinkOpener(Consumer<String> opener) {
        this.linkOpener = opener == null ? DesktopServices::openBrowser : opener;
    }
''', 1)])
PY
```

- [ ] **Step 4: Run the view's tests and the terminal's checks to verify they pass**

Run: `./gradlew :jasper-terminal:test --tests 'dev.jasper.terminal.view.*' :jasper-terminal:javadoc verifyTerminalArchitecture`
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 5: Commit**

```bash
git add jasper-terminal/src/main/java/dev/jasper/terminal/view/TerminalView.java \
    jasper-terminal/src/test/java/dev/jasper/terminal/view/TerminalViewInteractionTest.java
git commit -F - <<'MSG'
Terminal: a public link opener

TerminalView.setLinkOpener(Consumer<String>) is app-facing: it receives
each URL a Cmd-click (Ctrl-click on Linux and Windows) opens, as the
program wrote it, on the EDT, and null puts the desktop's browser back.
TerminalView is already on the supported type list.

🤖 Generated with Claude Code at The Home Depot

Co-Authored-By: Claude <noreply@anthropic.com>
MSG
```

---

### Task 4: Terminal link clicks through the web browser role

Spec 6 ("Links the user clicks: http and https links in a terminal"), brief 3: `TabHost` wires each pane's link opener
to `WindowContent.onOpenLink`; `JasperApplication.openLink` sends `http` and `https` links to the role as clicks with the
pane's window through `PluginRuntime.openLink`, other links to the system browser, everything to the system browser
before the plugins run. Jasper's own web links (Settings' Help, the Licenses window's and the plugin manager's
homepages, all through `JasperApplication.browse`) take the same path as clicks from the active window (Rulings 11).

**Files:**
- Modify: `jasper-app/src/main/java/dev/jasper/app/plugins/PluginRuntime.java` (before `:681`);
  `jasper-app/src/main/java/dev/jasper/app/workspace/WindowContent.java` (after `:257`, `:2641`);
  `jasper-app/src/main/java/dev/jasper/app/workspace/TabHost.java` (`:13-19`, `:145-153`, `:169-171`);
  `jasper-app/src/main/java/dev/jasper/app/workspace/TerminalPane.java` (`:625`);
  `jasper-app/src/main/java/dev/jasper/app/application/JasperApplication.java` (after `:192`, `:571`, before `:743`,
  `browse` `:1518-1524`)
- Test: `jasper-app/src/test/java/dev/jasper/app/workspace/FileLinkClickTest.java`,
  `jasper-app/src/test/java/dev/jasper/app/application/JasperApplicationPluginsTest.java` (before `awaitCalls`)

**Interfaces:**
- Consumes: Task 3's `TerminalView.setLinkOpener`; Task 2's `PluginHost.webBrowsers.open(URI, LinkOrigin, Optional<UUID>)`
  and `Platform.browser`; Task 1's `LinkOrigin.CLICK`.
- Produces:
  - `public void PluginRuntime.openLink(java.net.URI address, Optional<UUID> window, Consumer<java.net.URI> desktop)`
    (UI thread) and a private `openAddress(java.net.URI, dev.jasper.sdk.ui.LinkOrigin, Optional<UUID>, Consumer<java.net.URI>)`
    Task 5 reuses;
  - `public java.util.function.Consumer<String> WindowContent.onOpenLink` (default: nothing opens);
  - in `JasperApplication`: the package-private test seam `Consumer<java.net.URI> systemBrowser`,
    package-private `void openLink(String link, UUID window)` (EDT) and `static Optional<java.net.URI> webAddress(String text)`
    (any thread), private `void openWebAddress(java.net.URI address, Optional<UUID> window)` (EDT) and
    `static void openInSystemBrowser(java.net.URI address)`; `void browse(java.net.URI uri)`, now package-private
    (EDT): Jasper's own web links, as clicks from the active Jasper window, through `openWebAddress`;
  - the test fixture `JasperApplicationPluginsTest.WEB_BROWSER` (plugin `dev.example.web`, entry `fix.web.Main`): it
    appends `<address>|<origin>|<window or ->` to `data/opened` and declines the path `/declined`.

- [ ] **Step 1: Write the failing tests**

```bash
python3 -I - <<'PY'
import sys; sys.path.insert(0, "build/plan-browser3")
from edits import edit
edit("jasper-app/src/test/java/dev/jasper/app/workspace/FileLinkClickTest.java", [
(r'''/** A Cmd-click (Ctrl-click elsewhere) on a file in a pane, through the real view, to the window's {@code onOpenFile}. */''',
 r'''/**
 * A Cmd-click (Ctrl-click elsewhere) on a file or a URL in a pane, through the real view, to the window's
 * {@code onOpenFile} or {@code onOpenLink}.
 */''', 1),
(r'''    private final List<String> opened = Collections.synchronizedList(new ArrayList<>());
''',
 r'''    private final List<String> opened = Collections.synchronizedList(new ArrayList<>());
    private final List<String> links = Collections.synchronizedList(new ArrayList<>());
''', 1),
(r'''        owner.onOpenFile = (file, line) -> opened.add(file + ":" + line);
''',
 r'''        owner.onOpenFile = (file, line) -> opened.add(file + ":" + line);
        owner.onOpenLink = links::add;
''', 1),
(r'''    private WindowContent window(ShellLauncher launcher) {''',
 r'''    /**
     * Browser plan 3, Review Focus 1, the window's half: a URL under the pointer, plain text or OSC 8, goes to the window's
     * {@code onOpenLink} as the terminal read it, and a path still goes to {@code onOpenFile}.
     */
    @Test void aClickedUrlGoesToTheWindowsLinkOpenerAsWritten() throws Exception {
        TerminalPane pane = localPane(attached());
        write(pane, "see https://example.com/a?b=1 now\r\nftp://files.example.com/x\r\n"
            + "\033]8;;mailto:someone@example.com\007mail me\033]8;;\007\r\nsrc/Foo.java:3\r\n");

        click(pane, 6, 0);
        click(pane, 3, 1);
        click(pane, 2, 2);
        click(pane, 2, 3);

        assertThat(links).containsExactly("https://example.com/a?b=1", "ftp://files.example.com/x", "mailto:someone@example.com");
        assertThat(opened).containsExactly(directory.resolve("src").resolve("Foo.java") + ":3");
    }

    /** Browser plan 3: unlike a file path, a URL in a provided session's pane goes to the window too. */
    @Test @DisabledOnOs(OS.WINDOWS) void aUrlInAProvidedSessionGoesToTheWindowToo() throws Exception {
        List<SessionAttempt> attempts = Collections.synchronizedList(new ArrayList<>());
        TerminalPane[] pane = new TerminalPane[1];
        edt(() -> pane[0] = window(launcher(new ArrayDeque<>()))
            .openTab(directory, new SessionRequest("dev.example.ssh", "build-host", false, attempts::add, Runnable::run)).pane());
        until(() -> !attempts.isEmpty());
        attempts.getFirst().attach(connection());
        until(() -> pane[0].view() != null);
        write(pane[0], "see https://example.com/remote\r\n");

        click(pane[0], 6, 0);

        assertThat(links).containsExactly("https://example.com/remote");
    }

    private WindowContent window(ShellLauncher launcher) {''', 1)])
edit("jasper-app/src/test/java/dev/jasper/app/application/JasperApplicationPluginsTest.java", [
(r'''    private static void awaitCalls(List<String> calls, int count) throws InterruptedException {''',
 r'''    /** A web browser that appends {@code <address>|<origin>|<window or ->} to {@code data/opened} and declines {@code /declined}. */
    private static final String WEB_BROWSER = """
        package fix.web;
        import dev.jasper.sdk.plugin.Plugin;
        import dev.jasper.sdk.plugin.PluginContext;
        import java.nio.file.Files;
        import java.nio.file.StandardOpenOption;
        public final class Main implements Plugin {
            @Override public void start(PluginContext context) {
                context.platform().browser((address, origin, window) -> {
                    try {
                        Files.writeString(context.dataDirectory().resolve("opened"),
                            address + "|" + origin + "|" + (window.isPresent() ? "window" : "-") + "\\n",
                            StandardOpenOption.CREATE, StandardOpenOption.APPEND);
                    } catch (java.io.IOException failure) { throw new java.io.UncheckedIOException(failure); }
                    return !address.getPath().equals("/declined");
                });
            }
        }
        """;

    /**
     * Browser plan 3, Review Focus 1 to 3: a clicked web link goes to the browser a plugin provides, as a click; one it
     * declines, any other link the terminal opens and every link before the plugins start go to the system browser, and a
     * link that is no URI opens nothing.
     */
    @Test void aClickedWebLinkGoesToTheProvidedBrowserAndAnyOtherLinkToTheSystemBrowser() throws Exception {
        var system = new CopyOnWriteArrayList<String>();
        java.util.UUID window = java.util.UUID.randomUUID();
        var terminated = new CountDownLatch(1);
        JasperApplication[] application = new JasperApplication[1];
        edt(() -> {
            application[0] = new JasperApplication(null, launcher(new ArrayDeque<>()), new CommandHistory(), null, terminated::countDown);
            application[0].systemBrowser = address -> system.add(address.toString());
            application[0].openLink("https://example.com/early", window);
        });
        assertThat(system).as("before the plugins start").containsExactly("https://example.com/early");

        system.clear();
        AppDirs dirs = new AppDirs(home, home.resolve("config.toml"), home.resolve("logs"));
        Path dev = home.resolve("web-plugin");
        PluginJars.build(dev, "web.jar", PluginJars.descriptor("dev.example.web", "1.0.0", "fix.web.Main")
            + "capabilities = [\"browser.provide\"]\n", Map.of("fix.web.Main", WEB_BROWSER), List.of());
        edt(() -> {
            application[0].startPlugins(null, dev, false, dirs);
            application[0].openLink("https://example.com/a", window);
            application[0].openLink("HTTPS://Example.com/declined", window);
            application[0].openLink("mailto:someone@example.com", window);
            application[0].openLink("ftp://files.example.com/x", window);
            application[0].openLink("https://example.com/a b", window);
        });
        assertThat(java.nio.file.Files.readAllLines(dirs.plugins().resolve("dev.example.web/data/opened")))
            .as("as clicks; a window that is not open is no window")
            .containsExactly("https://example.com/a|CLICK|-", "HTTPS://Example.com/declined|CLICK|-");
        assertThat(system).as("the declined address, mailto and ftp; nothing for a link that is no URI")
            .containsExactly("HTTPS://Example.com/declined", "mailto:someone@example.com", "ftp://files.example.com/x");
        edt(application[0]::quit);
        assertThat(terminated.await(5, TimeUnit.SECONDS)).isTrue();
        ApplicationPluginCleanup.await(application[0]);
    }

    /**
     * Browser plan 3, the user's ruling ("route Help and About through it too"): Jasper's own web links, which Settings'
     * Help button, the Licenses window and the plugin manager open through {@code browse}, go to the browser a plugin
     * provides as clicks from the active window, else to the system browser.
     */
    @Test void jaspersOwnWebLinksGoToTheProvidedBrowserAndOtherwiseTheSystemBrowser() throws Exception {
        var system = new CopyOnWriteArrayList<String>();
        java.net.URI help = java.net.URI.create(dev.jasper.app.settingsui.SettingsDialog.HELP_URL + "keybindings");
        java.net.URI declined = java.net.URI.create("https://example.com/declined");
        var terminated = new CountDownLatch(1);
        JasperApplication[] application = new JasperApplication[1];
        edt(() -> {
            application[0] = new JasperApplication(null, launcher(new ArrayDeque<>()), new CommandHistory(), null, terminated::countDown);
            application[0].systemBrowser = address -> system.add(address.toString());
            application[0].browse(help);
        });
        assertThat(system).as("before the plugins start").containsExactly(help.toString());

        system.clear();
        AppDirs dirs = new AppDirs(home, home.resolve("config.toml"), home.resolve("logs"));
        Path dev = home.resolve("web-plugin");
        PluginJars.build(dev, "web.jar", PluginJars.descriptor("dev.example.web", "1.0.0", "fix.web.Main")
            + "capabilities = [\"browser.provide\"]\n", Map.of("fix.web.Main", WEB_BROWSER), List.of());
        edt(() -> {
            application[0].startPlugins(null, dev, false, dirs);
            application[0].browse(help);
            // A stand-in frame opens the main window headless, as pluginsStartBeforeWindowsAndStopBeforeTermination does.
            application[0].frames = window -> new dev.jasper.app.workspace.WindowShell(() -> { }, () -> { }, () -> { }, () -> { },
                () -> null, component -> javax.swing.SwingUtilities.isDescendingFrom(component, window.content()));
            application[0].openFirstWindow(List.of()).orElseThrow();
            application[0].terminals().windowActivated(application[0].terminals().windows().getFirst().id());
            application[0].browse(help);
            application[0].browse(declined);
        });
        assertThat(java.nio.file.Files.readAllLines(dirs.plugins().resolve("dev.example.web/data/opened")))
            .as("as clicks: with no window active, then from the active one")
            .containsExactly(help + "|CLICK|-", help + "|CLICK|window", declined + "|CLICK|window");
        assertThat(system).as("only the address the browser declined").containsExactly(declined.toString());
        edt(application[0]::quit);
        assertThat(terminated.await(5, TimeUnit.SECONDS)).isTrue();
        ApplicationPluginCleanup.await(application[0]);
    }

    private static void awaitCalls(List<String> calls, int count) throws InterruptedException {''', 1)])
PY
```

- [ ] **Step 2: Run them to verify they fail**

Run: `./gradlew :jasper-app:test --tests 'dev.jasper.app.workspace.FileLinkClickTest' --tests 'dev.jasper.app.application.JasperApplicationPluginsTest'`
Expected: FAIL to compile `compileTestJava`: `cannot find symbol` `onOpenLink`, `systemBrowser` and `openLink(String,UUID)`,
and `browse(URI) has private access in JasperApplication`.

- [ ] **Step 3: Wire the views to the window and route their links**

```bash
python3 -I - <<'PY'
import sys; sys.path.insert(0, "build/plan-browser3")
from edits import edit
edit("jasper-app/src/main/java/dev/jasper/app/plugins/PluginRuntime.java", [
(r'''    private Optional<Path> commandClient = Optional.empty();
''',
 r'''    /**
     * Opens a web address the user asked for (a link clicked in a terminal, {@code jasper <address>}): the web browser
     * a plugin provides ({@code Platform.browser}), asked on this thread as a click from {@code window}, then
     * {@code desktop} when no plugin provides one, or it declines or throws. Before the plugins start, {@code desktop}
     * alone. UI thread: the browser must not block, so nothing waits for it.
     *
     * @param address an absolute http or https address
     * @param window the Jasper window the address came from, or empty
     * @param desktop the system browser's opener; it must not block
     * @throws IllegalStateException off the UI thread
     */
    public void openLink(java.net.URI address, Optional<UUID> window, Consumer<java.net.URI> desktop) {
        openAddress(address, dev.jasper.sdk.ui.LinkOrigin.CLICK, window, desktop);
    }

    private void openAddress(java.net.URI address, dev.jasper.sdk.ui.LinkOrigin origin, Optional<UUID> window,
                             Consumer<java.net.URI> desktop) {
        if (!SwingUtilities.isEventDispatchThread()) throw new IllegalStateException("Web addresses are opened on the UI thread");
        Objects.requireNonNull(address, "address");
        Objects.requireNonNull(window, "window");
        Objects.requireNonNull(desktop, "desktop");
        PluginHost current = host;
        if (current == null || !current.webBrowsers.open(address, origin, window)) desktop.accept(address);
    }

    private Optional<Path> commandClient = Optional.empty();
''', 1)])
edit("jasper-app/src/main/java/dev/jasper/app/workspace/WindowContent.java", [
(r'''    public java.util.function.BiConsumer<Path, Integer> onOpenFile = (file, line) -> { };
''',
 r'''    public java.util.function.BiConsumer<Path, Integer> onOpenFile = (file, line) -> { };
    /**
     * A Cmd-click (Ctrl-click elsewhere) opened a URL in one of this window's panes: the link as the terminal read it, an
     * {@code http}, {@code https} or {@code ftp} URL or an OSC 8 {@code mailto} link, which may not parse as a URI. The
     * application opens it; by default nothing opens. EDT.
     */
    public java.util.function.Consumer<String> onOpenLink = link -> { };
''', 1),
(r'''onOpenFile = (file, line) -> {}; fileChooser''',
 r'''onOpenFile = (file, line) -> {}; onOpenLink = link -> {}; fileChooser''', 1)])
edit("jasper-app/src/main/java/dev/jasper/app/workspace/TabHost.java", [
(r''' * One window's side of the terminal tabs it shows (workspace tabs spec 9.3): the shortcut, context-menu and file-link
 * handlers a pane's view gets, the window its activity and terminal-registry facts report from, the window's theme and
 * opacity, and focus.''',
 r''' * One window's side of the terminal tabs it shows (workspace tabs spec 9.3): the shortcut, context-menu, file-link and
 * link handlers a pane's view gets, the window its activity and terminal-registry facts report from, the window's
 * theme and opacity, and focus.''', 1),
(r'''    /** The view's shortcut, context-menu and file-link handlers, and its launch focus, go to this window. */''',
 r'''    /** The view's shortcut, context-menu, file-link and link handlers, and its launch focus, go to this window. */''', 1),
(r'''        pane.view().setFileLinkHandler(link -> openFileLink(pane, link));
''',
 r'''        pane.view().setFileLinkHandler(link -> openFileLink(pane, link));
        pane.view().setLinkOpener(link -> owner.onOpenLink.accept(link));
''', 1),
(r'''            pane.view().setShortcutHandler(null); pane.view().setContextMenuHandler(null); pane.view().setFileLinkHandler(null);
''',
 r'''            pane.view().setShortcutHandler(null); pane.view().setContextMenuHandler(null); pane.view().setFileLinkHandler(null);
            pane.view().setLinkOpener(null);
''', 1)])
edit("jasper-app/src/main/java/dev/jasper/app/workspace/TerminalPane.java", [
(r'''view.setFileLinkHandler(null); view.setOnCloseRequest(() -> {});''',
 r'''view.setFileLinkHandler(null); view.setLinkOpener(null); view.setOnCloseRequest(() -> {});''', 1)])
edit("jasper-app/src/main/java/dev/jasper/app/application/JasperApplication.java", [
(r'''    Function<JasperWindow, WindowShell> frames = this::frameOnScreen;
''',
 r'''    Function<JasperWindow, WindowShell> frames = this::frameOnScreen;
    /** Test seam: how a web address opens in the system browser (browser plan 3). Production uses a virtual thread. EDT. */
    Consumer<java.net.URI> systemBrowser = JasperApplication::openInSystemBrowser;
''', 1),
(r'''            content.onOpenFile = (file, line) -> openFileAt(file, line, windowId);
''',
 r'''            content.onOpenFile = (file, line) -> openFileAt(file, line, windowId);
            content.onOpenLink = link -> openLink(link, windowId);
''', 1),
(r'''    /** What running plugins add to a new local shell, given Jasper's own environment; nothing before they start. EDT. */''',
 r'''    /**
     * Opens a link a Cmd-click (Ctrl-click elsewhere) in window {@code window} handed over, as the terminal read it
     * (browser plan 3): a web address ({@code webAddress}) through the web browser a plugin provides, as a click from that
     * window, else the system browser; any other link the terminal opens, an {@code ftp} URL or an OSC 8 {@code mailto}
     * link, in the system browser, as before. A link that is not a URI opens nothing, and the log does not repeat it: it
     * is program output. EDT.
     */
    void openLink(String link, UUID window) {
        Optional<java.net.URI> address = webAddress(link);
        if (address.isPresent()) {
            openWebAddress(address.get(), Optional.of(window));
            return;
        }
        try {
            systemBrowser.accept(new java.net.URI(link));
        } catch (java.net.URISyntaxException notALink) {
            LOG.log(System.Logger.Level.INFO, "A clicked link is not a URI, so nothing opens");
        }
    }

    /**
     * Opens a web address the user asked for: the web browser a plugin provides, as a click from {@code window}, else the
     * system browser; before the plugins start, the system browser. EDT.
     */
    private void openWebAddress(java.net.URI address, Optional<UUID> window) {
        PluginRuntime runtime = plugins;
        if (runtime == null) systemBrowser.accept(address);
        else runtime.openLink(address, window, systemBrowser);
    }

    /**
     * {@code text} as a web address (browser plan 3): an absolute URI whose scheme is {@code http} or {@code https}, in
     * any case, with an authority, such as {@code https://example.com/a}. Anything else is empty: another scheme, an
     * opaque URI such as {@code https:example.com}, one without an authority such as {@code http:/notes}, a Windows path,
     * and text that does not parse. Any thread.
     */
    static Optional<java.net.URI> webAddress(String text) {
        try {
            var address = new java.net.URI(text);
            String scheme = address.getScheme();
            boolean web = scheme != null && (scheme.equalsIgnoreCase("http") || scheme.equalsIgnoreCase("https"));
            return web && !address.isOpaque() && address.getRawAuthority() != null ? Optional.of(address) : Optional.empty();
        } catch (java.net.URISyntaxException notAnAddress) {
            return Optional.empty();
        }
    }

    /** The system browser's open, on a virtual thread; a failure is logged without the address, which may be program output. */
    private static void openInSystemBrowser(java.net.URI address) {
        Thread.ofVirtual().name("jasper-open-link").start(() -> {
            try {
                dev.jasper.app.platform.Browser.open(address);
            } catch (RuntimeException failure) {
                LOG.log(System.Logger.Level.WARNING, "Could not open a web address in the system browser ("
                    + failure.getClass().getSimpleName() + ")");
            }
        });
    }

    /** What running plugins add to a new local shell, given Jasper's own environment; nothing before they start. EDT. */''', 1),
(r'''        private void browse(java.net.URI uri) {
            if (!supports(Desktop.Action.BROWSE)) return;
            CompletableFuture.runAsync(() -> {
                try { Desktop.getDesktop().browse(uri); }
                catch (java.io.IOException failure) { throw new java.io.UncheckedIOException(failure); }
            });
        }
''',
 r'''        /**
         * Opens one of Jasper's own web links (browser plan 3, the user's ruling): Settings' Help button, the Licenses
         * window's homepages and the plugin manager's homepages. Their windows are not Jasper windows, so an http or https
         * address goes to the web browser a plugin provides as a click from the active Jasper window, else to the system
         * browser; any other address goes to the system browser. EDT.
         */
        void browse(java.net.URI uri) {
            Optional<UUID> window = terminals.activeWindow().map(dev.jasper.app.terminals.WindowEntry::id);
            if (webAddress(uri.toString()).isPresent()) openWebAddress(uri, window);
            else systemBrowser.accept(uri);
        }
''', 1)])
PY
```

- [ ] **Step 4: Run the tests and the architecture check to verify they pass**

Run: `./gradlew :jasper-app:test --tests 'dev.jasper.app.workspace.FileLinkClickTest' --tests 'dev.jasper.app.application.JasperApplicationPluginsTest' --tests 'dev.jasper.app.workspace.TabMoveTest' --tests 'dev.jasper.app.plugins.PluginRuntimeTest' verifyApplicationArchitecture verifyTerminalArchitecture :jasper-app:javadoc`
Expected: `BUILD SUCCESSFUL`; `FileLinkClickTest` (with its two new cases; the provided-session one is skipped on
Windows only) and `JasperApplicationPluginsTest` (with its two new cases) pass.

- [ ] **Step 5: Commit**

```bash
git add jasper-app/src/main/java/dev/jasper/app/plugins/PluginRuntime.java \
    jasper-app/src/main/java/dev/jasper/app/workspace/WindowContent.java jasper-app/src/main/java/dev/jasper/app/workspace/TabHost.java \
    jasper-app/src/main/java/dev/jasper/app/workspace/TerminalPane.java \
    jasper-app/src/main/java/dev/jasper/app/application/JasperApplication.java \
    jasper-app/src/test/java/dev/jasper/app/workspace/FileLinkClickTest.java \
    jasper-app/src/test/java/dev/jasper/app/application/JasperApplicationPluginsTest.java
git commit -F - <<'MSG'
Browser link routing: terminal link clicks through the web browser role

Each pane's link opener reports to its window (WindowContent.onOpenLink,
wired and unwired by TabHost, reset when the pane closes), and the
application opens what it gets: an http or https address goes to the
web browser a plugin provides as a click from the pane's window
(PluginRuntime.openLink), then the system browser; an ftp URL or an OSC 8
mailto link goes to the system browser as before, and so does every
link before the plugins start. A link that is not a URI opens nothing,
and the log never repeats a link's text. Jasper's own web links
(Settings' Help, the Licenses window's and the plugin manager's
homepages) take the same path, as clicks from the active window.

🤖 Generated with Claude Code at The Home Depot

Co-Authored-By: Claude <noreply@anthropic.com>
MSG
```

---

### Task 5: `jasper <url>` and `jasper open-url`

Spec 6 ("Plain `jasper <url>` recognises http(s)"), brief 4: in `JasperApplication.commands()`, ahead of the path and
subcommand handling, `open-url` with one web address goes to the role as `PROGRAM`, and a call whose every argument is a
web address sends each as `CLICK` in the calling pane's window; both answer 0. `open-url` is reserved.

**Files:**
- Modify: `jasper-app/src/main/java/dev/jasper/app/application/JasperApplication.java` (after `:99`, `commands()`
  `:755-765`, after Task 4's `openWebAddress`); `jasper-app/src/main/java/dev/jasper/app/plugins/PluginRuntime.java`
  (after Task 4's `openLink`); `jasper-app/src/main/java/dev/jasper/app/plugins/DescriptorParser.java` (`:32`)
- Test: `jasper-app/src/test/java/dev/jasper/app/application/ApplicationCommandsTest.java` (`:73`, before
  `aJasperThatIsNotResidentQuitsOnceNothingKeepsIt`), `jasper-app/src/test/java/dev/jasper/app/plugins/DescriptorParserTest.java`
  (`malformedSubcommandsNameTheirProblem`), `jasper-app/src/test/java/dev/jasper/app/application/JasperApplicationPluginsTest.java`
  (before `awaitCalls`)

**Interfaces:**
- Consumes: Task 4's `JasperApplication.webAddress(String)`, `openWebAddress`, `systemBrowser`, `PluginRuntime.openAddress`
  and the `WEB_BROWSER` fixture; `JasperApplication.onEventThread`, `acceptsRequests`, `terminals`.
- Produces:
  - `public void PluginRuntime.openProgramLink(java.net.URI address, Optional<UUID> window, Consumer<java.net.URI> desktop)`;
  - in `JasperApplication`: `static final String OPEN_URL = "open-url"`,
    `static Optional<List<java.net.URI>> webAddresses(List<String> arguments)` (present only when there is at least one
    argument and every one is a web address), private `openProgramAddress(java.net.URI, Optional<UUID>)` and
    `windowOfPane(Optional<UUID>)` (EDT);
  - `DescriptorParser.RESERVED_SUBCOMMANDS = Set.of("help", "version", "open-url")`.

- [ ] **Step 1: Write the failing tests**

```bash
python3 -I - <<'PY'
import sys; sys.path.insert(0, "build/plan-browser3")
from edits import edit
edit("jasper-app/src/test/java/dev/jasper/app/application/ApplicationCommandsTest.java", [
(r'''        for (List<String> arguments : List.of(List.<String>of(), List.of("notes.txt"))) {''',
 r'''        for (List<String> arguments : List.of(List.<String>of(), List.of("notes.txt"), List.of("https://example.com/a"),
                List.of("open-url", "https://example.com/a"))) {''', 1),
(r'''    @Test void aJasperThatIsNotResidentQuitsOnceNothingKeepsIt() throws Exception {''',
 r'''    /** Browser plan 3, Review Focus 5: only an absolute http or https address with an authority is a web address. */
    @Test void onlyAnAbsoluteHttpOrHttpsAddressWithAnAuthorityIsAWebAddress() {
        assertThat(JasperApplication.webAddress("https://example.com/a?b=1")).contains(java.net.URI.create("https://example.com/a?b=1"));
        assertThat(JasperApplication.webAddress("HTTP://Example.com")).as("the scheme in any case").contains(java.net.URI.create("HTTP://Example.com"));
        assertThat(JasperApplication.webAddress("http://under_score:8080/")).as("a host Java reads as a registry name").isPresent();
        for (String text : List.of("C:\\Users\\me\\notes.txt", "c:/Users/me/notes.txt", "\\\\server\\share\\notes.txt",
                "./https://example.com", "https:example.com", "http:/notes", "ftp://files.example.com/x", "mailto:someone@example.com",
                "file:///etc/hosts", "example.com", "https://exa mple.com", ""))
            assertThat(JasperApplication.webAddress(text)).as(text).isEmpty();
        assertThat(JasperApplication.webAddresses(List.of("https://example.com/a", "http://localhost:8080/")))
            .contains(List.of(java.net.URI.create("https://example.com/a"), java.net.URI.create("http://localhost:8080/")));
        assertThat(JasperApplication.webAddresses(List.of("https://example.com/a", "notes.txt"))).as("a mixed call").isEmpty();
        assertThat(JasperApplication.webAddresses(List.of())).as("no arguments: a raise").isEmpty();
    }

    /** Browser plan 3, Review Focus 3: before the plugins start, jasper's addresses reach the system browser, and each call answers 0. */
    @Test void everyArgumentAWebAddressOpensEachAndAnswersZero() throws Exception {
        JasperApplication application = application(() -> { });
        var opened = new java.util.concurrent.CopyOnWriteArrayList<String>();
        DesktopTestSupport.edt(() -> application.systemBrowser = address -> opened.add(address.toString()));
        var err = new StringBuilder();
        var clicked = application.commands().dispatch(new Invocation(List.of("https://example.com/a", "HTTP://Example.com/b"), dir,
            Optional.of(UUID.randomUUID()), text -> { }, err::append));
        assertThat(clicked.toCompletableFuture().get(5, TimeUnit.SECONDS)).isZero();
        var program = application.commands().dispatch(new Invocation(List.of("open-url", "https://example.com/sign-in"), dir,
            Optional.empty(), text -> { }, err::append));
        assertThat(program.toCompletableFuture().get(5, TimeUnit.SECONDS)).isZero();
        assertThat(opened).containsExactly("https://example.com/a", "HTTP://Example.com/b", "https://example.com/sign-in");
        assertThat(err).isEmpty();
        DesktopTestSupport.edt(application::quitNow);
    }

    /** Browser plan 3, Review Focus 4: open-url takes exactly one web address; anything else is a usage error that opens nothing. */
    @Test void openUrlNeedsExactlyOneWebAddress() throws Exception {
        JasperApplication application = application(() -> { });
        var opened = new java.util.concurrent.CopyOnWriteArrayList<String>();
        DesktopTestSupport.edt(() -> application.systemBrowser = address -> opened.add(address.toString()));
        Files.writeString(dir.resolve("notes.txt"), "");
        for (List<String> arguments : List.of(List.of("open-url"), List.of("open-url", "ftp://files.example.com/x"),
                List.of("open-url", "https://example.com/a", "https://example.com/b"), List.of("open-url", "notes.txt"),
                List.of("open-url", "example.com"), List.of("open-url", "C:\\Users\\me\\page.html"))) {
            var err = new StringBuilder();
            var exit = application.commands().dispatch(new Invocation(arguments, dir, Optional.empty(), text -> { }, err::append));
            assertThat(exit.toCompletableFuture().get(5, TimeUnit.SECONDS)).as("%s", arguments).isEqualTo(2);
            assertThat(err).as("%s", arguments).hasToString("jasper: open-url needs one web address (http or https)\n");
        }
        assertThat(opened).isEmpty();
        DesktopTestSupport.edt(application::quitNow);
    }

    /** Browser plan 3, Review Focus 5: a call that mixes paths and addresses keeps today's answers. */
    @Test void aCallThatMixesPathsAndAddressesKeepsItsAnswer() throws Exception {
        Files.writeString(dir.resolve("notes.txt"), "");
        JasperApplication application = application(() -> { });
        var opened = new java.util.concurrent.CopyOnWriteArrayList<String>();
        DesktopTestSupport.edt(() -> application.systemBrowser = address -> opened.add(address.toString()));
        var pathFirst = new StringBuilder();
        var exit = application.commands().dispatch(new Invocation(List.of("notes.txt", "https://example.com/a"), dir, Optional.empty(),
            text -> { }, pathFirst::append));
        assertThat(exit.toCompletableFuture().get(5, TimeUnit.SECONDS)).isEqualTo(1);
        assertThat(pathFirst).hasToString("jasper: no such file or folder: https://example.com/a\n");
        var addressFirst = new StringBuilder();
        exit = application.commands().dispatch(new Invocation(List.of("https://example.com/a", "notes.txt"), dir, Optional.empty(),
            text -> { }, addressFirst::append));
        assertThat(exit.toCompletableFuture().get(5, TimeUnit.SECONDS)).isEqualTo(1);
        assertThat(addressFirst).hasToString("jasper: Jasper's plugins are not running\n");
        assertThat(opened).isEmpty();
        DesktopTestSupport.edt(application::quitNow);
    }

    @Test void aJasperThatIsNotResidentQuitsOnceNothingKeepsIt() throws Exception {''', 1)])
edit("jasper-app/src/test/java/dev/jasper/app/plugins/DescriptorParserTest.java", [
(r'''            "[[subcommands]]\nname = \"help\"\nsummary = \"s\"\n", "subcommands.name is reserved: help",
''',
 r'''            "[[subcommands]]\nname = \"help\"\nsummary = \"s\"\n", "subcommands.name is reserved: help",
            "[[subcommands]]\nname = \"open-url\"\nsummary = \"s\"\n", "subcommands.name is reserved: open-url",
''', 1)])
edit("jasper-app/src/test/java/dev/jasper/app/application/JasperApplicationPluginsTest.java", [
(r'''    private static void awaitCalls(List<String> calls, int count) throws InterruptedException {''',
 r'''    /** Browser plan 3: jasper's addresses reach the provided browser, as clicks in the calling pane's window or as a program's. */
    @Test void theJasperCommandsAddressesGoToTheProvidedBrowser() throws Exception {
        AppDirs dirs = new AppDirs(home, home.resolve("config.toml"), home.resolve("logs"));
        Path dev = home.resolve("web-plugin");
        PluginJars.build(dev, "web.jar", PluginJars.descriptor("dev.example.web", "1.0.0", "fix.web.Main")
            + "capabilities = [\"browser.provide\"]\n", Map.of("fix.web.Main", WEB_BROWSER), List.of());
        var system = new CopyOnWriteArrayList<String>();
        var terminated = new CountDownLatch(1);
        JasperApplication[] application = new JasperApplication[1];
        java.util.UUID[] pane = new java.util.UUID[1];
        edt(() -> {
            application[0] = new JasperApplication(null, launcher(new ArrayDeque<>()), new CommandHistory(), null, terminated::countDown);
            application[0].systemBrowser = address -> system.add(address.toString());
            application[0].startPlugins(null, dev, false, dirs);
            // A stand-in frame opens the main window headless, as pluginsStartBeforeWindowsAndStopBeforeTermination does.
            application[0].frames = window -> new dev.jasper.app.workspace.WindowShell(() -> { }, () -> { }, () -> { }, () -> { },
                () -> null, component -> javax.swing.SwingUtilities.isDescendingFrom(component, window.content()));
            application[0].openFirstWindow(List.of()).orElseThrow();
            pane[0] = application[0].terminals().windows().getFirst().selectedTab().get()
                .flatMap(tab -> tab.terminal().get()).orElseThrow().id();
        });
        var commands = application[0].commands();
        var clicked = commands.dispatch(new dev.jasper.app.commandline.Invocation(List.of("https://example.com/c", "https://example.com/d"),
            home, Optional.of(pane[0]), text -> { }, text -> { }));
        assertThat(clicked.toCompletableFuture().get(5, TimeUnit.SECONDS)).isZero();
        var program = commands.dispatch(new dev.jasper.app.commandline.Invocation(List.of("open-url", "https://example.com/sign-in"),
            home, Optional.empty(), text -> { }, text -> { }));
        assertThat(program.toCompletableFuture().get(5, TimeUnit.SECONDS)).isZero();
        assertThat(java.nio.file.Files.readAllLines(dirs.plugins().resolve("dev.example.web/data/opened"))).containsExactly(
            "https://example.com/c|CLICK|window", "https://example.com/d|CLICK|window", "https://example.com/sign-in|PROGRAM|-");
        assertThat(system).as("the browser took each one").isEmpty();
        edt(application[0]::quit);
        assertThat(terminated.await(5, TimeUnit.SECONDS)).isTrue();
        ApplicationPluginCleanup.await(application[0]);
    }

    private static void awaitCalls(List<String> calls, int count) throws InterruptedException {''', 1)])
PY
```

- [ ] **Step 2: Run them to verify they fail**

Run: `./gradlew :jasper-app:test --tests 'dev.jasper.app.application.ApplicationCommandsTest' --tests 'dev.jasper.app.plugins.DescriptorParserTest' --tests 'dev.jasper.app.application.JasperApplicationPluginsTest'`
Expected: FAIL to compile `compileTestJava`: `cannot find symbol` `webAddresses(List<String>)`.

- [ ] **Step 3: Decide URLs first in `commands()`, and reserve `open-url`**

```bash
python3 -I - <<'PY'
import sys; sys.path.insert(0, "build/plan-browser3")
from edits import edit
edit("jasper-app/src/main/java/dev/jasper/app/plugins/PluginRuntime.java", [
(r'''        openAddress(address, dev.jasper.sdk.ui.LinkOrigin.CLICK, window, desktop);
    }
''',
 r'''        openAddress(address, dev.jasper.sdk.ui.LinkOrigin.CLICK, window, desktop);
    }

    /**
     * As {@link #openLink}, for an address a program asked for ({@code jasper open-url}): the browser hears it as a
     * program's. UI thread.
     *
     * @param address an absolute http or https address
     * @param window the Jasper window of the pane the program ran in, or empty
     * @param desktop the system browser's opener; it must not block
     * @throws IllegalStateException off the UI thread
     */
    public void openProgramLink(java.net.URI address, Optional<UUID> window, Consumer<java.net.URI> desktop) {
        openAddress(address, dev.jasper.sdk.ui.LinkOrigin.PROGRAM, window, desktop);
    }
''', 1)])
edit("jasper-app/src/main/java/dev/jasper/app/plugins/DescriptorParser.java", [
(r'''    static final Set<String> RESERVED_SUBCOMMANDS = Set.of("help", "version");''',
 r'''    static final Set<String> RESERVED_SUBCOMMANDS = Set.of("help", "version", "open-url");''', 1)])
edit("jasper-app/src/main/java/dev/jasper/app/application/JasperApplication.java", [
(r'''    public static final String OPEN_FILE = "open-file";
''',
 r'''    public static final String OPEN_FILE = "open-file";
    /** The {@code jasper} subcommand a program opens a web address with (browser plan 3); no plugin may declare it. */
    static final String OPEN_URL = "open-url";
''', 1),
(r'''     * What the command socket runs (workspace-tabs spec 5.4): no arguments raises or opens a window, as a launch would;
''',
 r'''     * What the command socket runs (workspace-tabs spec 5.4): {@code open-url} with one web address opens it as a
     * program's, and arguments that are all web addresses open each as the user's, in the calling pane's window (browser
     * plan 3); otherwise no arguments raises or opens a window, as a launch would;
''', 1),
(r'''        return invocation -> {
            PluginRuntime runtime = plugins;
            java.util.function.Predicate<String> subcommand = name -> runtime != null && runtime.declaresSubcommand(name);
''',
 r'''        return invocation -> {
            List<String> arguments = invocation.arguments();
            if (!arguments.isEmpty() && arguments.getFirst().equals(OPEN_URL)) {
                Optional<java.net.URI> address = arguments.size() == 2 ? webAddress(arguments.get(1)) : Optional.empty();
                if (address.isEmpty()) {
                    invocation.err("jasper: open-url needs one web address (http or https)\n");
                    return CompletableFuture.completedFuture(2);
                }
                return onEventThread(SwingUtilities::invokeLater, this::acceptsRequests,
                    () -> openProgramAddress(address.get(), windowOfPane(invocation.pane())));
            }
            Optional<List<java.net.URI>> addresses = webAddresses(arguments);
            if (addresses.isPresent())
                return onEventThread(SwingUtilities::invokeLater, this::acceptsRequests, () -> {
                    Optional<UUID> window = windowOfPane(invocation.pane());
                    addresses.get().forEach(address -> openWebAddress(address, window));
                });
            PluginRuntime runtime = plugins;
            java.util.function.Predicate<String> subcommand = name -> runtime != null && runtime.declaresSubcommand(name);
''', 1),
(r'''        else runtime.openLink(address, window, systemBrowser);
    }
''',
 r'''        else runtime.openLink(address, window, systemBrowser);
    }

    /** As {@code openWebAddress}, for an address a program asked for ({@code jasper open-url}). EDT. */
    private void openProgramAddress(java.net.URI address, Optional<UUID> window) {
        PluginRuntime runtime = plugins;
        if (runtime == null) systemBrowser.accept(address);
        else runtime.openProgramLink(address, window, systemBrowser);
    }

    /** The window holding the pane a {@code jasper} call ran in, while it is open; empty for a call from elsewhere. EDT. */
    private Optional<UUID> windowOfPane(Optional<UUID> pane) {
        return pane.flatMap(terminals::pane).flatMap(entry -> terminals.tab(entry.tabId())).map(dev.jasper.app.terminals.TabEntry::windowId);
    }

    /**
     * The {@code jasper} command's arguments as web addresses (browser plan 3): present only when there is at least one
     * and every one is a web address ({@code webAddress}); a call that mixes them with paths is a path call. Any thread.
     */
    static Optional<List<java.net.URI>> webAddresses(List<String> arguments) {
        if (arguments.isEmpty()) return Optional.empty();
        List<java.net.URI> addresses = new ArrayList<>();
        for (String argument : arguments) {
            Optional<java.net.URI> address = webAddress(argument);
            if (address.isEmpty()) return Optional.empty();
            addresses.add(address.get());
        }
        return Optional.of(List.copyOf(addresses));
    }
''', 1)])
PY
```

- [ ] **Step 4: Run the tests and the checks to verify they pass**

Run: `./gradlew :jasper-app:test --tests 'dev.jasper.app.application.ApplicationCommandsTest' --tests 'dev.jasper.app.plugins.DescriptorParserTest' --tests 'dev.jasper.app.application.JasperApplicationPluginsTest' --tests 'dev.jasper.app.commandline.*' --tests 'dev.jasper.app.plugins.PluginRuntimeTest' verifyApplicationArchitecture :jasper-app:javadoc`
Expected: `BUILD SUCCESSFUL`; `ApplicationCommandsTest`'s existing cases (`commandArgumentsArePathsOnlyWhenEveryOneExistsAndNoSubcommandClaimsTheFirst`,
`aWordThatNamesNothingIsStillACommand`, `aCallThatStartsWithAPathSaysWhichOthersAreMissing` and the rest) pass with the
four new ones, and `PluginRuntimeTest`'s subcommand cases (`PluginCommands`'s tests) still pass.

- [ ] **Step 5: Commit**

```bash
git add jasper-app/src/main/java/dev/jasper/app/application/JasperApplication.java \
    jasper-app/src/main/java/dev/jasper/app/plugins/PluginRuntime.java jasper-app/src/main/java/dev/jasper/app/plugins/DescriptorParser.java \
    jasper-app/src/test/java/dev/jasper/app/application/ApplicationCommandsTest.java \
    jasper-app/src/test/java/dev/jasper/app/plugins/DescriptorParserTest.java \
    jasper-app/src/test/java/dev/jasper/app/application/JasperApplicationPluginsTest.java
git commit -F - <<'MSG'
Browser link routing: jasper <address> and jasper open-url

The command decides web addresses before paths and subcommands. When
every argument is an absolute http or https address, each goes to the
web browser a plugin provides as a click in the calling pane's window,
else to the system browser, and the call answers 0. jasper open-url
<address> is the route programs will use: the browser hears it as a
program's (PluginRuntime.openProgramLink); without exactly one web
address it prints "jasper: open-url needs one web address (http or
https)" and exits 2. No plugin may declare open-url, and jasper --help
does not list it. A call that mixes paths and addresses keeps its
answer.

🤖 Generated with Claude Code at The Home Depot

Co-Authored-By: Claude <noreply@anthropic.com>
MSG
```

---

### Task 6: The Editor's and Remote's links through the role

Spec 6 ("links other plugins hand over"), brief 5: the Editor's Markdown link navigation and Remote's tunnel address
call `openInBrowser`; each plugin takes the next minor version.

**Files:**
- Modify: `plugins/editor/src/main/java/dev/jasper/editor/ui/EditorWindow.java` (`:177`),
  `plugins/remote/src/main/java/dev/jasper/remote/ui/tunnels/TunnelsUi.java` (`:350`),
  `plugins/editor/src/main/resources/plugin.toml` (`:3`), `plugins/remote/src/main/resources/plugin.toml` (`:3`)
- Test: `plugins/editor/src/test/java/dev/jasper/editor/ui/MarkdownNavigationTest.java` (setup, before
  `delayedTargetWaitsForLoadAndClosingItDropsNavigation`), `plugins/remote/src/test/java/dev/jasper/remote/TunnelsPluginTest.java` (`:174`)

**Interfaces:**
- Consumes: Task 2's `Platform.openInBrowser` and the fake's `browser` and `openInBrowser` (a click in the active
  window, on the caller's thread; the system browser's share in `browsed()`).
- Produces: nothing new; Editor `0.16.0`, Remote `0.17.0`.

- [ ] **Step 1: Write the failing tests**

```bash
python3 -I - <<'PY'
import sys; sys.path.insert(0, "build/plan-browser3")
from edits import edit
edit("plugins/editor/src/test/java/dev/jasper/editor/ui/MarkdownNavigationTest.java", [
(r'''navigation=new MarkdownNavigation(window,context.platform()::browse);''',
 r'''navigation=new MarkdownNavigation(window,context.platform()::openInBrowser);''', 1),
(r'''    @Test void delayedTargetWaitsForLoadAndClosingItDropsNavigation() throws Exception {''',
 r'''    /** Browser plan 3: a preview's web link goes to the browser a plugin provides, as a click, and the system browser hears nothing. */
    @Test void aPreviewLinkGoesToTheProvidedBrowserFirst() throws Exception {
        var tab=open("preview.md","[web](https://example.com/a)\n");
        List<String> seen=new ArrayList<>();
        edt(() -> {tab.surface().setFont(new java.awt.Font("Dialog",0,13));tab.markdownMode(MarkdownMode.PREVIEW,false);});drain();
        edt(() -> {
            host.start(new PluginInfo("dev.example.browser","Browser","1.0.0",Set.of("browser.provide")),Set.of(),Set.of(),
                c -> c.platform().browser((address,origin,owner) -> {seen.add(address+"|"+origin+"|"+owner.isPresent());return true;}));
            var pane=tab.surface().preview();
            dev.jasper.editor.testsupport.PreviewFixture.layout(tab.surface().previewScroll,300,100);
            pane.editor().setCaretPosition(0);pane.nextLink(true);pane.activateSelectedLink();
            assertThat(seen).containsExactly("https://example.com/a|CLICK|false");
            assertThat(host.browsed()).isEmpty();
        });
    }
    @Test void delayedTargetWaitsForLoadAndClosingItDropsNavigation() throws Exception {''', 1)])
edit("plugins/remote/src/test/java/dev/jasper/remote/TunnelsPluginTest.java", [
(r'''            assertThat(host.browsed()).containsExactly(URI.create("http://127.0.0.1:" + port + "/"));
''',
 r'''            assertThat(host.browsed()).containsExactly(URI.create("http://127.0.0.1:" + port + "/"));
            List<String> seen = new ArrayList<>();
            host.start(new dev.jasper.sdk.PluginInfo("dev.example.browser", "Browser", "1.0.0", Set.of("browser.provide")), Set.of(), Set.of(),
                context -> context.platform().browser((address, origin, owner) -> { seen.add(address + "|" + origin); return true; }));
            host.searchScope(TunnelsScope.ID, "", started.window(), null);
            host.executeInScope(TunnelsScope.ID, "tunnel." + db.id(), "browse", started.window(), null);
            assertThat(seen).as("browser plan 3: the provided browser first, as a click").containsExactly("http://127.0.0.1:" + port + "/|CLICK");
            assertThat(host.browsed()).as("the system browser heard only the first").hasSize(1);
''', 1)])
PY
```

- [ ] **Step 2: Run them to verify they fail**

Run: `./gradlew :jasper-plugin-editor:test --tests 'dev.jasper.editor.ui.MarkdownNavigationTest' :jasper-plugin-remote:test --tests 'dev.jasper.remote.TunnelsPluginTest' --continue`
Expected: FAIL: `aPreviewLinkGoesToTheProvidedBrowserFirst` (`seen` is empty; `browsed()` has the address) and
`aSavedTunnelStartsFromThePaletteForwardsAndOpensInTheBrowser` (`seen` is empty).

- [ ] **Step 3: Switch both plugins to `openInBrowser` and bump their versions**

```bash
python3 -I - <<'PY'
import sys; sys.path.insert(0, "build/plan-browser3")
from edits import edit
edit("plugins/editor/src/main/java/dev/jasper/editor/ui/EditorWindow.java", [
(r'''        navigation = new MarkdownNavigation(this, context.platform()::browse);''',
 r'''        navigation = new MarkdownNavigation(this, context.platform()::openInBrowser);''', 1)])
edit("plugins/remote/src/main/java/dev/jasper/remote/ui/tunnels/TunnelsUi.java", [
(r'''ifPresent(uri -> context.platform().browse(uri));''',
 r'''ifPresent(uri -> context.platform().openInBrowser(uri));''', 1)])
edit("plugins/editor/src/main/resources/plugin.toml", [('version = "0.15.0"\n', 'version = "0.16.0"\n', 1)])
edit("plugins/remote/src/main/resources/plugin.toml", [('version = "0.16.0"\n', 'version = "0.17.0"\n', 1)])
PY
```

- [ ] **Step 4: Run the plugins' tests and the plugin architecture check to verify they pass**

Run: `./gradlew :jasper-plugin-editor:test --tests 'dev.jasper.editor.ui.*' :jasper-plugin-remote:test --tests 'dev.jasper.remote.TunnelsPluginTest' --tests 'dev.jasper.remote.ui.tunnels.*' verifyPluginArchitecture`
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 5: Commit**

```bash
git add plugins/editor/src/main/java/dev/jasper/editor/ui/EditorWindow.java \
    plugins/remote/src/main/java/dev/jasper/remote/ui/tunnels/TunnelsUi.java plugins/editor/src/main/resources/plugin.toml \
    plugins/remote/src/main/resources/plugin.toml plugins/editor/src/test/java/dev/jasper/editor/ui/MarkdownNavigationTest.java \
    plugins/remote/src/test/java/dev/jasper/remote/TunnelsPluginTest.java
git commit -F - <<'MSG'
Editor and Remote: links open through the web browser role

A Markdown preview's web link (Editor 0.16.0) and a tunnel's Open in
Browser (Remote 0.17.0) go through Platform.openInBrowser: the web
browser a plugin provides first, as a click, then the system browser,
which is where they open while no plugin provides one.

🤖 Generated with Claude Code at The Home Depot

Co-Authored-By: Claude <noreply@anthropic.com>
MSG
```

---

### Task 7: Documentation, the SDK reference, STATUS and the spec

Brief "Plan shape" and AGENTS.md "Documentation edits": the SDK guides, the command line guide, the terminal README and
guides, the Editor and Remote guides, a `docs/STATUS.md` entry, and the spec's deviations (sections 6 and 13); then
`./gradlew sdkReference`.

**Files:**
- Modify: `docs/plugin-authoring.md` (`:216`, `:237`, `:1026-1028`, before `## Looks and themes`, `:1348`);
  `docs/plugins.md` (`:375`); `docs/sdk-architecture.md` (after `:399`); `jasper-sdk/README.md` (`:25`, `:47`, `:49`,
  `:72-73`); `jasper-sdk-testkit/README.md` (`:128-129`); `tools/sdk-reference/guide/03-manifest.html` (`:38`),
  `05-threading.html` (`:28`), `06-capabilities.html` (`:40`), `07-contributions.html` (`:30`), `13-testing.html` (`:167`);
  `docs/command-line.md` (the forms table, the messages table); `jasper-terminal/README.md` (`:240`);
  `docs/terminal-architecture.md` (`:122-123`, `:197-198`); `docs/terminal.md` (`:287-288`); `docs/editor.md` (`:377`);
  `docs/remote.md` (`:505-506`); `docs/STATUS.md` (a new entry at the top of the handoff log); the spec (`:212-233`,
  `:371-378`); `docs/sdk-reference.html` (generated)

**Interfaces:**
- Consumes: every task's names, as they landed (if a task's review renamed anything, use the landed name).
- Produces: the STATUS entry Task 8 completes with the check's numbers, and the spec plan 4's writer reads.

- [ ] **Step 1: Edit the SDK guides and the reference's guide pages**

```bash
python3 -I - <<'PY'
import sys; sys.path.insert(0, "build/plan-browser3")
from edits import edit
edit("docs/plugin-authoring.md", [
(r'''[the clipboard](#the-clipboard) and [the file editor role](#the-file-editor-role). |''',
 r'''[the clipboard](#the-clipboard), [the file editor role](#the-file-editor-role) and [the web browser role](#the-web-browser-role). |''', 1),
(r'''| `presentation.provide` | nothing at run time: it goes with the manifest's `presentation` key | Change Jasper's window chrome and controls after a restart |
''',
 r'''| `presentation.provide` | nothing at run time: it goes with the manifest's `presentation` key | Change Jasper's window chrome and controls after a restart |
| `browser.provide` | call `Platform.browser` | Open the web addresses Jasper opens, such as links you click in a terminal |
''', 1),
(r'''- `context.platform().browse(uri)` opens an `http` or `https` address in the user's browser, in the background; any
  other address throws `IllegalArgumentException`, and a failure is posted as "Could not open a web address".
- Both work from any thread.
''',
 r'''- `context.platform().openInBrowser(uri)` opens an `http` or `https` address the user asked for, such as a link they
  clicked in the plugin's view: the plugin that [provides the web browser](#the-web-browser-role) first, as a click in
  the active window, then the system browser, as `browse` opens it.
- `context.platform().browse(uri)` opens an `http` or `https` address in the system browser, in the background, even
  when a plugin provides the web browser; any other address throws `IllegalArgumentException` (from `openInBrowser`
  too), and a failure is posted as "Could not open a web address".
- All three work from any thread.
''', 1),
(r'''
## Looks and themes
''',
 r'''
## The web browser role

`context.platform().browser(webBrowser)` (`browser.provide`) makes the plugin the web browser Jasper opens web
addresses with: an `http` or `https` link Cmd-clicked (Ctrl-clicked on Linux and Windows) in any terminal pane, with the
clicked window; every plugin's `openInBrowser` and Jasper's own web links (Settings' Help button, the Licenses
window's and the plugin manager's homepages), with the active window; `jasper <address>…`, with the window of the pane
it ran in; and `jasper open-url <address>`, the command programs use ([command line](command-line.md#the-jasper-command)).

- [`WebBrowser`](sdk-reference.html#dev.jasper.sdk.ui.WebBrowser)`.open(address, origin, window)` runs on the UI thread,
  must not block, and returns whether it opened the address. `origin` is
  [`LinkOrigin`](sdk-reference.html#dev.jasper.sdk.ui.LinkOrigin)`.CLICK` when the user asked for the address and
  `PROGRAM` when a program did, through `jasper open-url`; a program's sign-in page usually works only in the system
  browser. `false`, or a handler that throws, hands the address to the system browser.
- A clicked link comes from program output, and an OSC 8 link's target can differ from the text shown.
- `ftp` and `mailto` links, and `Platform.browse`, always go to the system browser.
- One plugin at a time: a bundled plugin's registration replaces one that is not bundled; otherwise a later
  registration throws `IllegalStateException` ("The browser is already provided by *id*"). The role ends with the
  plugin.

## Looks and themes
''', 1),
(r'''`declareBundled(pluginId)` (a bundled plugin's file editor registration wins), `shellEnvironment(map)`, `edit(file, line, windowId)`,''',
 r'''`declareBundled(pluginId)` (a bundled plugin's file editor and web browser registrations win), `shellEnvironment(map)`, `edit(file, line, windowId)`, `openLink(address, origin, windowId)`, `browserPlugin()`,''', 1)])
edit("docs/plugins.md", [
(r'''| `presentation.provide` | Change Jasper's window chrome and controls after a restart | Declared by a look; one look at a time |
''',
 r'''| `presentation.provide` | Change Jasper's window chrome and controls after a restart | Declared by a look; one look at a time |
| `browser.provide` | Open the web addresses Jasper opens, such as links you click in a terminal | One plugin at a time |
''', 1)])
edit("docs/sdk-architecture.md", [
(r'''notification group, as is a failed `Platform.browse`, which accepts only an absolute http or https address.
''',
 r'''notification group, as is a failed `Platform.browse`, which accepts only an absolute http or https address.

`Platform.browser` (needs `browser.provide`) registers a `WebBrowser` in `plugins.WebBrowsers`, one at a time, by the
same bundled rule; it runs on the UI thread and must not block, so nothing waits for it. `PluginRuntime.openLink` and
`openProgramLink` ask it on the UI thread with `LinkOrigin.CLICK` or `PROGRAM`, and hand the address to the system
browser when no plugin provides one, it declines or it throws; before the plugins start, the system browser opens it.
A Cmd-click (Ctrl-click on Linux and Windows) on a URL in any terminal pane reaches `TerminalView.setLinkOpener`, which
`TabHost` points at the window's `WindowContent.onOpenLink`; `JasperApplication.openLink` sends an absolute `http` or
`https` address the role's way with the clicked window, and any other link the terminal opens (`ftp`, an OSC 8 `mailto`)
to the system browser. `JasperApplication.commands()` sends `jasper <address>…`, when every argument is one, as clicks
in the calling pane's window, and `jasper open-url <address>` as a program's, before the path and subcommand handling.
`Platform.openInBrowser` asks the role as a click in the active window, then opens the system browser on the plugin's
worker as `Platform.browse` does. `JasperApplication.browse`, which Settings' Help button, the Licenses window and the
plugin manager use for Jasper's own links, sends them the clicks' way with the active window.
''', 1)])
edit("jasper-sdk/README.md", [
(r'''`Capabilities`, the twelve capability strings,''', r'''`Capabilities`, the thirteen capability strings,''', 1),
(r'''| Desktop integration | `Platform`, `FileEditor` |''', r'''| Desktop integration | `Platform`, `FileEditor`, `WebBrowser`, `LinkOrigin` |''', 1),
(r'''`CommandHandler`, `FileEditor`, `PanelFactory`, `TabRestorer` and `ToolbarViewFactory`,''',
 r'''`CommandHandler`, `FileEditor`, `WebBrowser`, `PanelFactory`, `TabRestorer` and `ToolbarViewFactory`,''', 1),
(r'''shows the plugin's own content) and saves the workspace (`RestoreState`, `Windows.restorer`,
`SessionState.NOT_STARTED`).
''',
 r'''shows the plugin's own content), saves the workspace (`RestoreState`, `Windows.restorer`,
`SessionState.NOT_STARTED`) and adds the web browser role (`Platform.browser`, `Platform.openInBrowser`, `browser.provide`).
''', 1)])
edit("jasper-sdk-testkit/README.md", [
(r'''| `browsed()`, `openedInEditor()` | Addresses plugins opened in the browser; files opened in an editor that no provided editor took |
| `edit(file, line, windowId)`, `editorPlugin()`, `declareBundled(id)` | Opens a file as Jasper's own opens do, through the editor a plugin provides; which plugin provides it; marks a plugin as bundled before it starts, so its editor replaces another's |
''',
 r'''| `browsed()`, `openedInEditor()` | Addresses that reached the system browser (each `browse`, and each `openInBrowser` no provided browser took); files opened in an editor that no provided editor took |
| `edit(file, line, windowId)`, `editorPlugin()`, `declareBundled(id)` | Opens a file as Jasper's own opens do, through the editor a plugin provides; which plugin provides it; marks a plugin as bundled before it starts, so its editor and browser replace another's |
| `openLink(address, origin, windowId)`, `browserPlugin()` | Opens a web address as Jasper's own routes do (a clicked terminal link is `LinkOrigin.CLICK`, `jasper open-url` is `PROGRAM`), through the browser a plugin provides; which plugin provides it |
''', 1)])
edit("tools/sdk-reference/guide/03-manifest.html", [
(r'''Each one of the twelve names in''', r'''Each one of the thirteen names in''', 1)])
edit("tools/sdk-reference/guide/05-threading.html", [
(r'''<tr><td>{@link Platform#openInEditor(Path)}, {@link Platform#browse(URI)}</td>''',
 r'''<tr><td>{@link Platform#openInEditor(Path)}, {@link Platform#browse(URI)}, {@link Platform#openInBrowser(URI)}</td>''', 1)])
edit("tools/sdk-reference/guide/06-capabilities.html", [
(r'''<tr><td>{@code presentation.provide}</td><td>Change Jasper's window chrome and controls after a restart</td><td>No call. It goes with the manifest's {@code presentation} key, each requiring the other; see <a href="looks.md">looks</a>.</td><td></td></tr>
''',
 r'''<tr><td>{@code presentation.provide}</td><td>Change Jasper's window chrome and controls after a restart</td><td>No call. It goes with the manifest's {@code presentation} key, each requiring the other; see <a href="looks.md">looks</a>.</td><td></td></tr>
<tr><td>{@code browser.provide}</td><td>Open the web addresses Jasper opens, such as links you click in a terminal</td><td>{@link Platform#browser(WebBrowser)}</td><td>The registration</td></tr>
''', 1)])
edit("tools/sdk-reference/guide/07-contributions.html", [
(r'''<tr><td>The editor role</td><td>{@link Platform#editor(FileEditor)}</td><td>{@code editor.provide}</td><td>The files Jasper opens for editing, and Cmd-click (Ctrl-click on Linux and Windows) on paths in terminals</td></tr>
''',
 r'''<tr><td>The editor role</td><td>{@link Platform#editor(FileEditor)}</td><td>{@code editor.provide}</td><td>The files Jasper opens for editing, and Cmd-click (Ctrl-click on Linux and Windows) on paths in terminals</td></tr>
<tr><td>The web browser role</td><td>{@link Platform#browser(WebBrowser)}</td><td>{@code browser.provide}</td><td>The http and https addresses Jasper opens: Cmd-click (Ctrl-click on Linux and Windows) on links in terminals, plugins' {@link Platform#openInBrowser(URI)}, Jasper's own Help and homepage links, and the {@code jasper} command's addresses</td></tr>
''', 1)])
edit("tools/sdk-reference/guide/13-testing.html", [
(r'''<li>{@link Platform#openInEditor(Path)} runs on the calling thread;''',
 r'''<li>{@link Platform#openInEditor(Path)} and {@link Platform#openInBrowser(URI)} run on the calling thread;''', 1)])
PY
```

Every capability table puts `browser.provide` last, in `Capabilities.ALL`'s order.

- [ ] **Step 2: Edit the command line, terminal, Editor and Remote guides**

```bash
python3 -I - <<'PY'
import sys; sys.path.insert(0, "build/plan-browser3")
from edits import edit
edit("docs/command-line.md", [
(r'''| `jasper --help` | Prints the usage and the subcommands''',
 r'''| `jasper URL...` | When every argument is an absolute `http` or `https` address, such as `jasper https://example.com`, opens each as a clicked link opens: in the web browser a plugin provides, from the window of the pane `jasper` ran in, else in your default browser. It exits with status 0. Before Jasper's plugins start, your default browser opens them. A call that mixes addresses and paths is a `PATH...` call. |
| `jasper open-url URL` | Opens one `http` or `https` address a program asked for, such as a sign-in page, in the web browser a plugin provides when it takes it, else in your default browser, and exits with status 0. It is the command programs use, so `jasper --help` does not list it, and no plugin can declare a subcommand of that name. |
| `jasper --help` | Prints the usage and the subcommands''', 1),
(r'''| `jasper: unknown command <name>; run jasper --help for the list` | 2 | No plugin declares that subcommand. |
''',
 r'''| `jasper: unknown command <name>; run jasper --help for the list` | 2 | No plugin declares that subcommand. |
| `jasper: open-url needs one web address (http or https)` | 2 | `jasper open-url` with no address, more than one, or one that is not an absolute `http` or `https` address. |
''', 1)])
edit("jasper-terminal/README.md", [
(r'''| `setFileLinkHandler(Consumer<FileLink>)` |''',
 r'''| `setLinkOpener(Consumer<String>)` | receives each URL a Cmd-click (macOS) or Ctrl-click (Linux and Windows) opens: an `http`, `https` or `ftp` URL written in the output, or an OSC 8 link whose target is `http`, `https`, `ftp` or `mailto`, as the program wrote it, which may not parse as a URI. `null` restores the default, the desktop's browser |
| `setFileLinkHandler(Consumer<FileLink>)` |''', 1)])
edit("docs/terminal-architecture.md", [
(r'''A Cmd-click (Ctrl-click elsewhere) opens the URL under the pointer in the browser;
otherwise, once the application set `setFileLinkHandler`, it reports the path or
''',
 r'''A Cmd-click (Ctrl-click elsewhere) hands the URL under the pointer to the link opener,
the desktop's browser unless the application set `setLinkOpener`; otherwise, once the
application set `setFileLinkHandler`, it reports the path or
''', 1),
(r'''targets open in the browser, a `file:` target''',
 r'''targets go to the link opener, a `file:` target''', 1)])
edit("docs/terminal.md", [
(r'''1. An OSC 8 link whose target is `http`, `https`, `ftp` or `mailto` opens with the desktop's handler, normally your
   browser. A link with any other target opens nothing as a web link, whatever its text says.
''',
 r'''1. An OSC 8 link whose target is `http`, `https`, `ftp` or `mailto` opens with the desktop's handler, normally your
   browser; when a plugin provides Jasper's web browser, an `http` or `https` link opens there first. A link with any
   other target opens nothing as a web link, whatever its text says.
''', 1)])
edit("docs/editor.md", [
(r'''| `https://…` or `http://…`, with a host | Opening it in your browser |''',
 r'''| `https://…` or `http://…`, with a host | Opening it in the web browser a plugin provides, else in your browser |''', 1)])
edit("docs/remote.md", [
(r'''**Open in Browser** (a running local tunnel, at
`http://localhost:<port>/`),''',
 r'''**Open in Browser** (a running local tunnel, at
`http://localhost:<port>/`, in the web browser a plugin provides, else your browser),''', 1)])
PY
```

- [ ] **Step 3: Write the deviations into the spec and add the STATUS entry**

```bash
python3 -I - <<'PY'
import sys; sys.path.insert(0, "build/plan-browser3")
from edits import edit
spec = "docs/superpowers/specs/2026-10-21-jasper-browser-plugin-design.md"
edit(spec, [
(r'''  - **Links the user clicks:** http and https links in a terminal, and links other plugins hand over.
''',
 r'''  - **Links the user clicks:** http and https links in a terminal, links other plugins hand over, and Jasper's own web
    links: Settings' Help button and the Licenses window's and the plugin manager's homepages *(user)*.
''', 1),
(r'''    - `jasper <url>` fails with "no such file or folder".
  - **The design adds** a link-handler contribution. A plugin claims http(s) URLs, and the host consults it before
    falling back to the desktop browser. Terminal link clicks and `jasper` CLI URLs go through it.
  - **jasper-terminal also needs** a supported way for the app to set a view's link opener. That is an addition to its
    app-facing API and to the documented allowlist in `docs/terminal-architecture.md`. With the Browser
    plugin off, nothing is claimed and everything goes to the system browser, as today.
- **`BROWSER`.** Jasper sets `BROWSER` in its local shells to the `jasper` command, so a program's URL reaches the
  running app. The app applies the program-opened rule.
''',
 r'''    - `jasper <url>` ends as `jasper: unknown command <url>` (exit 2), or before the plugins start as "Jasper's plugins
      are not running" (exit 1).
  - **The design adds** the web browser role (plan 3): `Platform.browser(WebBrowser)`, which needs `browser.provide`,
    one plugin at a time. It hears each http(s) address with who asked (`LinkOrigin.CLICK` or `PROGRAM`), and the host
    asks it before falling back to the desktop browser. Terminal link clicks, plugins' `Platform.openInBrowser` and
    `jasper` CLI URLs go through it.
  - **jasper-terminal also needs** a supported way for the app to set a view's link opener: `TerminalView.setLinkOpener`
    becomes public. The app-facing allowlist is type-level and already lists `TerminalView`. With the Browser
    plugin off, nothing is claimed and everything goes to the system browser, as today.
- **`BROWSER`** (plan 4). Jasper sets `BROWSER` in its local shells to `jasper open-url`, so a program's URL reaches the
  running app as `LinkOrigin.PROGRAM`. The app applies the program-opened rule.
  - Python's `webbrowser` takes a `BROWSER` entry without `%s` as a single program name, and `gh` splits it with
    shlex, so plan 4 settles the value's form against the real `gh` and `aws`.
''', 1),
(r'''  - Programs that call macOS `open` or Windows' `start` directly are not caught. That is accepted.
''',
 r'''  - Programs that call macOS `open` or Windows' `start` directly are not caught, nor are addresses opened outside
    Jasper: it registers no URL scheme handler (the launcher's second-launch path, `setOpenURIHandler`). That is
    accepted.
''', 1),
(r'''  - Plain `jasper <url>` recognises http(s) and goes through the link-handler route.
''',
 r'''  - Plain `jasper <url>` recognises http(s) and goes through the web browser role.
  - `jasper open-url <url>` (plan 3) is the route programs use: one address, as `LinkOrigin.PROGRAM`. It is reserved,
    so no plugin can declare it, and `jasper --help` does not list it.
''', 1),
(r'''3. **Link routing.** The SDK link-handler contribution, jasper-terminal's link-opener API, terminal link clicks
   through them, `BROWSER` in local shells, `jasper <url>`, and the fallback to `Platform.browse`.
4. **The browser plugin core.** `plugins/browser` (`:jasper-plugin-browser`, exporting `dev.jasper.browser.api`), with:
''',
 r'''3. **Link routing.** The SDK's web browser role, jasper-terminal's link-opener API, terminal link clicks through them,
   `jasper <url>`, `jasper open-url`, and the fallback to the system browser.
4. **The browser plugin core.** `plugins/browser` (`:jasper-plugin-browser`, exporting `dev.jasper.browser.api`), with:
   - `BROWSER` in local shells, checked against the real `gh` and `aws` on all three platforms;
''', 1)])
edit("docs/STATUS.md", [
(r'''Newest first. Each entry describes its change as it stood when the entry was written.

''',
 r'''Newest first. Each entry describes its change as it stood when the entry was written.

**Browser plan 3: link routing — 2026-10-22** (`claude/browser-link-routing`, worktree
`.claude/worktrees/browser-link-routing`, from `claude/browser-plugin-design`; not merged or pushed): Jasper routes web
addresses through one plugin-provided web browser role, as it routes files through the editor role
(`docs/superpowers/plans/2026-10-22-browser-3-link-routing.md`). The SDK, still 0.21.0, gains `WebBrowser`,
`LinkOrigin` (`CLICK`, `PROGRAM`), `Platform.browser` (the thirteenth capability, `browser.provide`: one plugin at a
time, a bundled plugin's replacing another's, ending with its plugin) and `Platform.openInBrowser` (the role first, as
a click in the active window, then the system browser); `Platform.browse` still always opens the system browser. The
testkit fakes the role (`FakePluginHost.openLink`, `browserPlugin`) and four contract cases pin it on both runtimes.
`TerminalView.setLinkOpener` is public (`null` restores the desktop's browser). The application sends a pane's clicked
`http` and `https` links to the role as clicks from the pane's window (`TabHost`, `WindowContent.onOpenLink`,
`JasperApplication.openLink`, `PluginRuntime.openLink`, `plugins.WebBrowsers`), and `ftp` and OSC 8 `mailto` links, an
address the role declines and every link before the plugins start to the system browser. Jasper's own web links
(Settings' Help, the Licenses window's and the plugin manager's homepages, through `JasperApplication.browse`) go the
same way as clicks from the active window, as the user asked ("route Help and About through it too"). `jasper <address>…`, when
every argument is an absolute `http` or `https` address, goes to the role as clicks in the calling pane's window and
`jasper open-url <address>` as a program's, both exiting 0; `open-url` without exactly one web address prints
"jasper: open-url needs one web address (http or https)" and exits 2, and no plugin may declare it. The Editor's
Markdown links (Editor 0.16.0) and Remote's Open in Browser (Remote 0.17.0) use `openInBrowser`. No bundled plugin
provides the role yet, so every address still opens in the system browser. Moved to plan 4: `BROWSER` in local shells.
The spec records the deviations (sections 6 and 13); the plan lists its rulings. `./gradlew check`: CHECK-RESULT. GUI
checks for the user: a Cmd-click (Ctrl-click) on an `https` link and on an OSC 8 `mailto` link in a terminal,
`jasper https://example.com` and `jasper open-url https://example.com` in a Jasper shell, Settings' Help (?) button, a
homepage in About › Licenses… and in Settings › Plugins, a Markdown preview's web link, and a running tunnel's Open
in Browser, each reaching the default browser or mail program as before.

''', 1)])
PY
```

`CHECK-RESULT` is filled in by Task 8 with the counts it reads.

- [ ] **Step 4: Regenerate the SDK reference and run the documentation tests**

Run: `./gradlew sdkReference && ./gradlew verifySdkReference :jasper-app:test --tests 'dev.jasper.app.documentation.*' :jasper-terminal:test --tests 'dev.jasper.terminal.examples.TerminalDocumentationTest'`
Expected: `BUILD SUCCESSFUL` twice (`AppDocumentationTest` resolves `#the-web-browser-role` and
`command-line.md#the-jasper-command`).

- [ ] **Step 5: Check the edits**

```bash
git diff --stat
python3 - <<'PY'
import pathlib, sys
for root in ("jasper-terminal/src", "jasper-ui/src", "jasper-app/src", "jasper-buddy/src", "jasper-sdk/src", "jasper-sdk-testkit/src", "plugins"):
    for p in pathlib.Path(root).rglob("*.java"):
        text = p.read_text(encoding="utf-8")
        bad = sum(1 for c in text if 0xD800 <= ord(c) <= 0xDFFF or 0xE000 <= ord(c) <= 0xF8FF or (ord(c) < 0x20 and c not in "\n\t\r") or ord(c) == 0x7f)
        if bad: print(p, "bad chars:", bad)
PY
```

Expected: the stat lists the documents above and `docs/sdk-reference.html`; the hygiene script prints nothing.

- [ ] **Step 6: Commit**

```bash
git add docs/plugin-authoring.md docs/plugins.md docs/sdk-architecture.md jasper-sdk/README.md jasper-sdk-testkit/README.md \
    tools/sdk-reference/guide/03-manifest.html tools/sdk-reference/guide/05-threading.html tools/sdk-reference/guide/06-capabilities.html \
    tools/sdk-reference/guide/07-contributions.html tools/sdk-reference/guide/13-testing.html docs/command-line.md \
    jasper-terminal/README.md docs/terminal-architecture.md docs/terminal.md docs/editor.md docs/remote.md docs/STATUS.md \
    docs/superpowers/specs/2026-10-21-jasper-browser-plugin-design.md docs/sdk-reference.html
git commit -F - <<'MSG'
Docs: browser link routing

The SDK guides describe the web browser role (Platform.browser,
openInBrowser, browser.provide, the testkit's openLink and
browserPlugin), the command line guide jasper <address> and jasper
open-url, and the terminal guides the public link opener; the Editor,
Remote and terminal guides say where links open. The spec takes plan 3's
deviations: BROWSER moves to plan 4 and names jasper open-url, no URL
scheme handler, Jasper's own web links routed too, and the corrected
facts about jasper <url> and the terminal allowlist. STATUS records the plan; the SDK reference is
regenerated.

🤖 Generated with Claude Code at The Home Depot

Co-Authored-By: Claude <noreply@anthropic.com>
MSG
```

---

### Task 8: The whole build

AGENTS.md "Build and test": `./gradlew check`, and, because the SDK changed, the example external plugin built from the
SDK jars `check` writes to `build/sdk-release`. Then STATUS and this plan's banner take the numbers.

**Files:**
- Modify: `docs/STATUS.md` (`CHECK-RESULT` in Task 7's entry), this plan's status banner (`:5-7`)

**Interfaces:**
- Consumes: Tasks 1 to 7 as committed.
- Produces: the recorded check.

- [ ] **Step 1: Run the whole build**

Run: `./gradlew check --continue -Pjasper.python=/opt/homebrew/bin/python3.13`
Expected: `BUILD SUCCESSFUL`. (`checkThemeTool` needs Python 3.11 to 3.13; the dev Mac's 3.13 is the one STATUS records.
Do not run it while another worktree's build loads the Mac above a load average of about 50: plugin-cleanup waits then
time out, as the 2026-10-22 entries record. A failure that also fails on `claude/browser-plugin-design` is not this
plan's: record it, do not fix it here.)

- [ ] **Step 2: Count the tests**

```bash
python3 -I - <<'PY'
import pathlib, xml.etree.ElementTree as ET
t = f = e = s = 0
for p in pathlib.Path(".").glob("**/build/test-results/test/*.xml"):
    if "docs/examples" in p.as_posix():
        continue
    r = ET.parse(p).getroot()
    t += int(r.get("tests", 0)); f += int(r.get("failures", 0)); e += int(r.get("errors", 0)); s += int(r.get("skipped", 0))
print(f"{t:,} tests, {f} failures, {e} errors, {s} skipped")
PY
```

Expected: 0 failures and 0 errors. The count is the last recorded check's 12,823 plus the 22 tests this plan adds
(3 in Task 1, 4 contract cases on each runtime in Task 2, 1 in Task 3, 4 in Task 4, 5 in Task 5, 1 in Task 6), so
12,845 on macOS with 29 skipped, unless the branch's base has moved since.

- [ ] **Step 3: Build the example external plugin from the SDK jars**

Run: `./gradlew -p docs/examples/external-plugin -PjasperSdkDir="$PWD/build/sdk-release" check pluginZip`
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 4: Record the check**

Replace `CHECK-RESULT` in `docs/STATUS.md` with the numbers of Step 2 in the entries' style, for example
`**12,843 tests, 0 failures, 0 errors, 29 skipped** (Python 3.13); the example external plugin builds against
\`build/sdk-release\``, and replace this plan's banner line "**Execution status (2026-10-22): not started.**" with
"**Execution status (*date*): executed** (`<first>..<last>` commits); see `docs/STATUS.md`.", followed by every
deviation the task reviews recorded. Then:

```bash
grep -c 'CHECK-RESULT' docs/STATUS.md   # 0
git add docs/STATUS.md docs/superpowers/plans/2026-10-22-browser-3-link-routing.md
git commit -F - <<'MSG'
STATUS: browser plan 3's check

./gradlew check and the example external plugin's build, recorded in
the plan's STATUS entry and its status banner.

🤖 Generated with Claude Code at The Home Depot

Co-Authored-By: Claude <noreply@anthropic.com>
MSG
```

After the final whole-branch review, hand the user the GUI checks of the STATUS entry; never run them.

## Left for plan 4

- `BROWSER` in local shells: its value (`jasper open-url` as one word or with `%s`), checked against the real `gh` and
  `aws` on macOS, Linux and Windows, Windows paths with spaces included.
- Where an address with no window opens (a `jasper` call from another terminal while Jasper shows no window), and
  whether such a call restores Jasper's windows first when the browser takes it (Rulings 6).
- The first-click prompt and the remembered choice decide `CLICK`; `PROGRAM` defaults to the system browser (spec 6).

## Self-review

1. **Spec and brief coverage.** Brief 1 (SDK, testkit, contract, capability recipe): Tasks 1-2. Brief 2 (terminal):
   Task 3, its guides Task 7. Brief 3 (app routing, `WebBrowsers`, `PluginRuntime` bridge, `HostedContext`): Tasks 2
   and 4; the user's "route Help and About through it too": Task 4 (`JasperApplication.browse`) and Task 7. Brief 4 (`jasper <url>`, `open-url`, reserved, `--help`, the order in `commands()`, `ApplicationCommandsTest`):
   Task 5, `docs/command-line.md` Task 7. Brief 5 (Editor, Remote, versions): Task 6. Brief 6 (deviations): the header
   and Task 7's spec edits. Spec 13 item 3 minus `BROWSER`: Tasks 1-6. Spec 14's "covered by the testkit and the
   contract cases on both runtimes": Task 2.
2. **One reasonable thing per step.** Every code step is a script with exact anchors and complete text; every run step
   names its command and what passing looks like.
3. **Names.** `BROWSER_PROVIDE`, `LinkOrigin.CLICK`/`PROGRAM`, `WebBrowser.open`, `Platform.browser`, `openInBrowser`,
   `WebBrowsers.register`/`open`, `PluginHost.webBrowsers`, `FakePluginHost.openLink`/`browserPlugin`/`provideBrowser`/`activeWindowId`,
   `ContractHarness.openLink`, `TerminalView.setLinkOpener`, `WindowContent.onOpenLink`, `PluginRuntime.openLink`/`openProgramLink`/`openAddress`,
   `JasperApplication.systemBrowser`/`openLink`/`browse`/`webAddress`/`webAddresses`/`openWebAddress`/`openProgramAddress`/`windowOfPane`/`OPEN_URL`
   and the fixture `WEB_BROWSER` are spelled the same in every task that uses them.
4. **Review Focus.** Each of the five lines names its test, and each test is in the task that owns the code.
5. **Proportion.** The plan carries complete code, as AGENTS.md requires: about 350 production lines in 20 files (the
   two SDK types and `WebBrowsers` among them), about 400 test lines, and the documents. Every script was replayed in
   order on a `git archive` copy of `bc9ef8ff5`, and each anchor matched its stated count; nothing was compiled or run.
