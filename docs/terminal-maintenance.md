# Maintaining the terminal

Start with the [module README](../jasper-terminal/README.md), then the
[ownership guide](terminal-architecture.md). Work from a failing behaviour test,
make the smallest owner change, run that test, then `./gradlew check`.
Tests are headless. Module view tests use package-private `handleKey`/`handleMouse`
and injected clipboard/link opener; emulator fixtures are test-source-only.
[EmulationFixture](../jasper-terminal/src/test/java/dev/jasper/terminal/internal/emulation/EmulationFixture.java)
can feed output and expose a session without a desktop shell. Never add a
production vendor constructor just to simplify a test.

## Add a terminal action

1. Add a case to [TerminalAction](../jasper-terminal/src/main/java/dev/jasper/terminal/view/TerminalAction.java).
2. Route it in [TerminalView.execute](../jasper-terminal/src/main/java/dev/jasper/terminal/view/TerminalView.java)
   to the existing state owner. If new state is necessary, give it one concrete
   owner rather than duplicating selection/search/viewport state in the view.
3. Add real behaviour and off-EDT rejection coverage in
   [TerminalActionTest](../jasper-terminal/src/test/java/dev/jasper/terminal/view/TerminalActionTest.java).
4. If Jasper offers the action, follow
   [Workspace command or shortcut](app-maintenance.md#workspace-command-or-shortcut) (use
   `KeyContext.TERMINAL` for a terminal-only shortcut), and add it to the Shortcuts table in
   [Terminal panes](terminal.md#shortcuts).
5. Run `./gradlew :jasper-terminal:test --tests '*TerminalActionTest'`, and for app changes
   `KeymapDefaultsTest` (every built-in action's approved defaults and context) and
   `WindowCommandsTest`, then full check.

Existing example, on EDT: `view.execute(TerminalAction.CLEAR_SCROLLBACK);`.
Commands are synchronous and parameterless. Keep `view.paste(text)` and
`view.findAsync(query, callback)` typed; don't add an untyped argument bag or a
second command registry. To offer an operation to plugins, add it to the SDK's
[`PaneHandle`](sdk-reference.html#dev.jasper.sdk.terminal.PaneHandle), adapt it in the app's
`plugins.HostedTerminals` and implement it in the testkit (`FakeTerminals`, `FakePluginHost`),
stating the behaviour in `PluginContractTest` first (see
[Contract suite](sdk-architecture.md#contract-suite)).

## Change key encoding

1. Add the desired byte assertion in
   [SessionInputTest](../jasper-terminal/src/test/java/dev/jasper/terminal/internal/emulation/SessionInputTest.java)
   and the pressed/typed event case in
   [TerminalViewTest](../jasper-terminal/src/test/java/dev/jasper/terminal/view/TerminalViewTest.java).
2. Change [KeyEncoder](../jasper-terminal/src/main/java/dev/jasper/terminal/view/KeyEncoder.java)
   for Jasper's encoding rules; change
   [KeyboardController](../jasper-terminal/src/main/java/dev/jasper/terminal/view/KeyboardController.java)
   for modifier tracking, app shortcut precedence or duplicate typed suppression.
   Protocol-mode-dependent encoding stays in the engine.
3. Run both tests and `KeyEncoderTest`; check Meta, Unicode, application cursor
   mode and pressed/typed duplication before full check.

Example in a view-package test: `view.handleKey(new KeyEvent(view,
KeyEvent.KEY_PRESSED, 0, 0, KeyEvent.VK_UP, KeyEvent.CHAR_UNDEFINED));`.
Execute on EDT, feed application mode first if needed, then assert the fixture's
written bytes. Use Java escapes such as `"\033[A"`, never raw controls. Ordinary
keystrokes should not allocate command objects. App shortcuts win before bytes
reach the child.

## Add a live option

1. Add validation/default/defensive-copy and `toBuilder` round-trip coverage in
   [FluentOptionsTest](../jasper-terminal/src/test/java/dev/jasper/terminal/config/FluentOptionsTest.java)
   and [TerminalOptionsTest](../jasper-terminal/src/test/java/dev/jasper/terminal/config/TerminalOptionsTest.java).
2. Add the record component, constructor handling, builder field, setter, copy
   and build forwarding in [TerminalOptions](../jasper-terminal/src/main/java/dev/jasper/terminal/config/TerminalOptions.java).
3. Apply it in `TerminalView.applyOptions` and its specific controller. Test an
   already-running session in [TerminalLiveOptionsTest](../jasper-terminal/src/test/java/dev/jasper/terminal/view/TerminalLiveOptionsTest.java),
   including retention of unrelated appearance and no process restart. Also update
   the full `TerminalOptions` reconstruction in `TerminalView.setPalette` and
   `setFontSize`: both currently call the record constructor. Exercise each helper
   with a nondefault value for the new option so recolouring or zooming cannot reset it.
4. Separately wire the app setting: its `Setting` (key, kind, default, label, keywords,
   description) and its place in `SCHEMA` in
   [CoreSettings](../jasper-app/src/main/java/dev/jasper/app/config/CoreSettings.java); a row on a
   Settings page in [CorePages](../jasper-app/src/main/java/dev/jasper/app/settingsui/CorePages.java);
   the field in [TerminalConfig](../jasper-app/src/main/java/dev/jasper/app/config/TerminalConfig.java)
   or [FontConfig](../jasper-app/src/main/java/dev/jasper/app/config/FontConfig.java) and its
   `defaults()`, read in `ConfigSnapshot.from` and passed on in `ConfigSnapshot.viewOptions`
   ([ConfigSnapshot](../jasper-app/src/main/java/dev/jasper/app/config/ConfigSnapshot.java)); and
   forwarding plus change detection for open panes in
   [WorkspaceConfiguration](../jasper-app/src/main/java/dev/jasper/app/workspace/WorkspaceConfiguration.java).
   `ConfigLoader` and the generated template read `CoreSettings.SCHEMA` and need no change. Add the
   key at its default to `config.example.toml` and to the settings table of the
   [configuration guide](configuration.md).
5. Run option/live-option tests, then the app's `CoreSettingsTest`, `CorePagesTest` (every core
   setting is on exactly one page), `ConfigTemplateTest` (the example file names the settings and
   parses as the defaults; the guide names every key) and `ConfigurationControllerTest` (open panes
   receive the change; an unrelated reload keeps it), then full check.

Existing example: `view.applyOptions(view.options().toBuilder().bell(BellMode.NONE).build());`
(on EDT). Immutable options alone do not implement a live change. Decide explicitly
whether the setting is live or new-session: scrollback capacity and shell launch
are new-session settings. Builders perform no I/O. Settings persistence is app
policy; don't teach the terminal to read TOML.

## Add a shell event

1. Add split-at-every-boundary stream tests in
   [ShellIntegrationFilterTest](../jasper-terminal/src/test/java/dev/jasper/terminal/internal/shell/ShellIntegrationFilterTest.java).
2. If JediTerm swallows the sequence, rewrite it in
   [ShellIntegrationFilter](../jasper-terminal/src/main/java/dev/jasper/terminal/internal/shell/ShellIntegrationFilter.java)
   into the existing bounded custom protocol. Preserve order; don't emit a
   separate out-of-band callback while parsing bytes.
3. Decode/state-track in [ShellCommandTracker](../jasper-terminal/src/main/java/dev/jasper/terminal/internal/shell/ShellCommandTracker.java),
   with deterministic clock tests in `ShellCommandTrackerTest`. Capture metadata
   when the mark arrives, not later from mutable session state.
4. Carry the event through the engine callback bundle and facade listener mapping;
   document its thread/lock contract on
   [TerminalSessionListener](../jasper-terminal/src/main/java/dev/jasper/terminal/session/TerminalSessionListener.java).
   For plugins, the app carries it on: `workspace.TerminalPane` receives it on its session
   listener and moves it to the EDT, `WindowContent` reports it as a `terminals.TerminalEvent`, and
   `plugins.TerminalBridge` publishes it as a topic added to the SDK's
   [`TerminalEvents`](sdk-reference.html#dev.jasper.sdk.terminal.TerminalEvents). Implement the
   topic in the testkit too, as a `FakePluginHost` driver that publishes it, stating the behaviour
   in `PluginContractTest` first (see [Contract suite](sdk-architecture.md#contract-suite)).
5. Test the whole stream path in
   [ShellIntegrationSessionTest](../jasper-terminal/src/test/java/dev/jasper/terminal/internal/emulation/ShellIntegrationSessionTest.java),
   including resets, repeated marks and truncated/oversized input, then full check.

Example fixture input: `fixture.feed("\033]133;A\007");` marks a prompt.
Use `EmulationFixture.open(80, 24, 100)` in try-with-resources and await
`fixture.session().shellIntegrationDetected()`. Reader callbacks must not block
on the EDT. Command start/finish callbacks run after buffer capture releases the
lock. Preserve OSC 7/133, OSC 8 link and q-final CSI behaviour. The command text a
script sends as `OSC 1341;jasper;cmd;<base64>` takes precedence over the screen capture; keep
its 16 KiB bound.

## Change rendering

1. Pin the expected pixels/runs/text in `TerminalPainterTest`, `RunBuilderTest`,
   `TerminalRowTest` and relevant `TerminalAppearanceTest` cases.
2. Change [RunBuilder](../jasper-terminal/src/main/java/dev/jasper/terminal/internal/rendering/RunBuilder.java)
   for style/run grouping, [TerminalPainter](../jasper-terminal/src/main/java/dev/jasper/terminal/internal/rendering/TerminalPainter.java)
   for painting, and [FontSet](../jasper-terminal/src/main/java/dev/jasper/terminal/rendering/FontSet.java)
   for glyph choice/metrics. Change
   [JediCellReader](../jasper-terminal/src/main/java/dev/jasper/terminal/internal/emulation/JediCellReader.java)
   only when cell capture/representation must change.
3. Preserve CJK continuation, UTF-16 surrogate pairs, combining marks, links,
   default/indexed/RGB colours and reverse video. All live reads lock; shaping and
   painting use detached rows after unlock.
4. Run `./gradlew :jasper-terminal:refactorMeasurement` against the same runtime
   and fixtures as [verification](terminal-refactor-verification.md), plus tests
   and full check. Compare capture allocation as well as elapsed time. Native
   throughput/RSS acceptance is a separate [benchmark](benchmarks.md) the user runs.

Existing example on EDT: `view.setPalette(Palette.jasperDark());`. For a font
metric experiment use `new FontSet("Monospaced", 14f, List.of(), false).cellWidth()`.
Avoid additional full-screen copies or an object per cell. Retained vendor entry
assumptions require tests on dependency upgrades. Anything new that reads the
screen consumes snapshots; never expose the live buffer.

## Extend search

1. Extend [SearchQuery](../jasper-terminal/src/main/java/dev/jasper/terminal/search/SearchQuery.java)
   only for a real typed search input. Add algorithm tests in
   [TerminalSearchTest](../jasper-terminal/src/test/java/dev/jasper/terminal/internal/text/TerminalSearchTest.java).
2. Change [TerminalSearch](../jasper-terminal/src/main/java/dev/jasper/terminal/internal/text/TerminalSearch.java)
   for matching and [SearchController](../jasper-terminal/src/main/java/dev/jasper/terminal/view/SearchController.java)
   for admission, navigation or publication. Regex must run outside the buffer lock.
3. Run `SearchControllerTest` and `TerminalAppIntegrationTest` for stale results,
   hide/detach/reset cancellation and bounded queue behaviour. Preserve
   `rowResetRejectsACompletedSearchAlreadyQueuedAheadOfReconciliation`: it covers
   history clear, RIS, alternate-screen switch and width reflow while a completed
   search is already queued ahead of EDT reconciliation. Check the atomic row epoch
   at admission and publication as well as query generation; run
   [TerminalSearchPaintingTest](../jasper-terminal/src/test/java/dev/jasper/terminal/view/TerminalSearchPaintingTest.java)
   for actual highlight behaviour and viewport work bounds, then full check.

Example on EDT: `view.findAsync(new SearchQuery("error", false, false), result ->
statusLabel.setText(result.error() == null ? Integer.toString(result.count()) : result.error()));`.
Keep one running and at most one queued request, immutable published results and
latest-generation-only callbacks. Invalid regex is a result error, not a view
crash.

## Change selection and copy

1. Pin the pure rule in [SelectionTest](../jasper-terminal/src/test/java/dev/jasper/terminal/internal/text/SelectionTest.java),
   [SelectionTextTest](../jasper-terminal/src/test/java/dev/jasper/terminal/internal/text/SelectionTextTest.java),
   [WordBoundariesTest](../jasper-terminal/src/test/java/dev/jasper/terminal/internal/text/WordBoundariesTest.java) or
   [LogicalLineTest](../jasper-terminal/src/test/java/dev/jasper/terminal/internal/text/LogicalLineTest.java), and the
   live buffer and EDT behaviour in
   [SelectionSessionTest](../jasper-terminal/src/test/java/dev/jasper/terminal/internal/emulation/SelectionSessionTest.java)
   or [SelectionControllerTest](../jasper-terminal/src/test/java/dev/jasper/terminal/view/SelectionControllerTest.java).
2. Change [Selection](../jasper-terminal/src/main/java/dev/jasper/terminal/internal/text/Selection.java) (stream or
   block range), [SelectionText](../jasper-terminal/src/main/java/dev/jasper/terminal/internal/text/SelectionText.java)
   (the copied text), [WordBoundaries](../jasper-terminal/src/main/java/dev/jasper/terminal/internal/text/WordBoundaries.java)
   (what a double-click selects) or [LogicalLine](../jasper-terminal/src/main/java/dev/jasper/terminal/internal/text/LogicalLine.java)
   (the soft-wrapped line a triple-click selects) for the rules, `BufferQueries` for the locked reads, and
   [SelectionController](../jasper-terminal/src/main/java/dev/jasper/terminal/view/SelectionController.java) for
   anchors and validation.
3. Run these tests and `TerminalLogicalLineTest`, then full check.

Selections use absolute rows. Keep validation and text extraction atomic under one hold of the buffer lock, drop a
selection that output overwrote, and keep `TerminalView` dropping it with the other absolute-row state on width
reflow, history reset and alternate-screen switch.

## Change mouse routing

1. Reproduce the whole press/drag/release gesture in
   [TerminalViewInteractionTest](../jasper-terminal/src/test/java/dev/jasper/terminal/view/TerminalViewInteractionTest.java)
   or a pure decision in `MouseRoutingTest`.
2. Change [MouseRouting](../jasper-terminal/src/main/java/dev/jasper/terminal/view/MouseRouting.java)
   for route decisions, [MouseController](../jasper-terminal/src/main/java/dev/jasper/terminal/view/MouseController.java)
   for gesture state, then [MouseInput](../jasper-terminal/src/main/java/dev/jasper/terminal/internal/text/MouseInput.java)
   and the engine only if the report value/translation changes.
3. Run these tests plus [MouseReportingEfficiencyTest](../jasper-terminal/src/test/java/dev/jasper/terminal/view/MouseReportingEfficiencyTest.java)
   and full check. Reported gestures must read zero screen lines and cause no
   local repaint; local copy/link/selection behaviour must still work.

Example view-test input on EDT: `view.handleMouse(new MouseEvent(view,
MouseEvent.MOUSE_PRESSED, 0, InputEvent.BUTTON1_DOWN_MASK, 1, 1, 1, false,
MouseEvent.BUTTON1));`. Keep press-time modifiers through release, Shift bypass,
a macOS Cmd-click staying local under mouse reporting, right-click behaviour,
out-of-bounds checks, fractional wheel accumulation, alternate-screen arrow keys and
the macOS Shift-wheel rule.
Do not let a plugin borrow a controller's in-flight gesture state.

## Change process metadata

1. Reproduce the OS/executable case in
   [TerminalSessionTest](../jasper-terminal/src/test/java/dev/jasper/terminal/internal/emulation/TerminalSessionTest.java)
   and [TerminalTitleIntegrationTest](../jasper-app/src/test/java/dev/jasper/app/workspace/TerminalTitleIntegrationTest.java)
   as appropriate. Use explicit executable fixtures; `/bin/sh` may resolve to
   Bash on the test runtime.
2. Change [ForegroundJobResolver](../jasper-terminal/src/main/java/dev/jasper/terminal/internal/process/ForegroundJobResolver.java).
   Keep native resource and closing guards in [PtyChild](../jasper-terminal/src/main/java/dev/jasper/terminal/internal/process/PtyChild.java).
   Application title formatting remains in `jasper-app`.
3. Run the focused tests and `PtyConnectorTest`/`PtySessionFactoryTest` if process
   ownership changed; run full check. Unsupported OS/environment cases should
   remain explicit skips or absent metadata rather than invented names.

Example off EDT: `Optional<String> job = session.foregroundJob();`. Marshal a
result to EDT only after checking it still belongs to the live pane/session.
Keep startup failure cleanup, repeated close safety and bounded shutdown. Plugins
read the foreground job through the SDK's
[`PaneHandle.foregroundJob()`](sdk-reference.html#dev.jasper.sdk.terminal.PaneHandle.foregroundJob()),
which the app answers on a worker; they never own the native PTY.

## Change attached sessions

1. Reproduce queueing, dropped writes, resize coalescing, the drain window or a single close in
   [AttachedTransportTest](../jasper-terminal/src/test/java/dev/jasper/terminal/internal/transport/AttachedTransportTest.java),
   and the session's view of it (exit status, exceptional exit, no exit line, remote-only
   directories, ownership on a failed attach) in
   [AttachedSessionTest](../jasper-terminal/src/test/java/dev/jasper/terminal/session/AttachedSessionTest.java).
2. Change [AttachedTransport](../jasper-terminal/src/main/java/dev/jasper/terminal/internal/transport/AttachedTransport.java)
   for the writer thread, queue and drain, `TerminalSession.attach` for ownership and validation,
   and the package-private `AttachedConnector` only when the JediTerm adapter must change.
3. Run both tests, the app's `HostedSessionsTest` when the SDK adapter is affected, then full check.

Example: `TerminalSession.attach(new AttachedConnection(output, input, (columns, rows) -> { },
exited, () -> { }), new GridSize(80, 24), 100)`. Writes and resizes must keep only enqueueing, so a
stalled remote never blocks the EDT, and the connection's `close` must keep running exactly once.

## Change working directories and file links

1. Pin the case in [DirectoryProvenanceTest](../jasper-terminal/src/test/java/dev/jasper/terminal/internal/shell/DirectoryProvenanceTest.java)
   (host rules and the path mapper), [SessionLocalPathsTest](../jasper-terminal/src/test/java/dev/jasper/terminal/session/SessionLocalPathsTest.java)
   or [SessionLaunchOptionsNamesTest](../jasper-terminal/src/test/java/dev/jasper/terminal/session/SessionLaunchOptionsNamesTest.java)
   for a session's directories, and [PathDetectorTest](../jasper-terminal/src/test/java/dev/jasper/terminal/internal/text/PathDetectorTest.java),
   [LinkDetectorTest](../jasper-terminal/src/test/java/dev/jasper/terminal/internal/text/LinkDetectorTest.java) or
   [FileLinkTest](../jasper-terminal/src/test/java/dev/jasper/terminal/view/FileLinkTest.java) for clicks.
2. Change [DirectoryProvenance](../jasper-terminal/src/main/java/dev/jasper/terminal/internal/shell/DirectoryProvenance.java)
   for local and remote classification, [PathDetector](../jasper-terminal/src/main/java/dev/jasper/terminal/internal/text/PathDetector.java)
   for path syntax, [LinkDetector](../jasper-terminal/src/main/java/dev/jasper/terminal/internal/text/LinkDetector.java)
   for plain-text URLs, and `BufferQueries.linkAt`/`fileLinkAt` for OSC 8 targets and soft-wrap joining.
3. Jasper's own rules stay in the app: `launch.LocalHostNames` gives the names passed as
   `localHostNames`, `LaunchSettings.localPaths` (with `WslPaths` and `UncHosts`) maps a local path,
   and `workspace.FileLinks` resolves a `FileLink` against the pane's local directory. Run
   `LocalHostNamesTest`, `WslPathsTest`, `UncHostsTest` and `FileLinksTest` when they change, then
   full check.

A remote path must never reach the local surface: keep exactly one of the local and remote
directory, ambiguity resolving to remote, and the OSC 8 browser scheme allowlist (`http`, `https`,
`ftp`, `mailto`).

## Keep these guides checked

`TerminalDocumentationTest` reads this guide, the
[architecture guide](terminal-architecture.md) and the [module README](../jasper-terminal/README.md).
Every relative link outside a code fence must resolve, and a `#fragment` into Markdown must match a
heading. The Java block after each `<!-- example:NAME -->` marker must equal the
`// example:NAME:start` … `// example:NAME:end` region of
[TerminalExamplesTest](../jasper-terminal/src/test/java/dev/jasper/terminal/examples/TerminalExamplesTest.java),
so change the compiled example and the guide together. Every production package needs a
`package-info.java` with JavaDoc. `TerminalArchitectureTest` keeps production types out of the flat
`dev.jasper.terminal` package, JediTerm types out of public and protected signatures and imports
outside `internal.emulation`, and `dev.jasper.terminal.internal.` and `.internalAccess(` out of app
sources.
