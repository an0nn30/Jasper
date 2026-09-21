# Maintaining the terminal

Start with the [module README](../jasper-terminal/README.md), then the
[ownership guide](terminal-architecture.md). Work from a failing behavior test,
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
3. Add real behavior and off-EDT rejection coverage in
   [TerminalActionTest](../jasper-terminal/src/test/java/dev/jasper/terminal/view/TerminalActionTest.java).
4. Add an app catalog adapter in [WindowContent](../jasper-app/src/main/java/dev/jasper/app/workspace/WindowContent.java)
   only if the action needs a menu/key binding. Reuse the existing registry.
5. Run `./gradlew :jasper-terminal:test --tests '*TerminalActionTest'` and app
   `WindowCommandsTest` if the catalog changed, then full check.

Existing example, on EDT: `view.execute(TerminalAction.CLEAR_SCROLLBACK);`.
Commands are synchronous and parameterless. Keep `view.paste(text)` and
`view.findAsync(query, callback)` typed; don't add an untyped argument bag or a
second command registry. A future SDK may adapt these operations after its
capability and lifetime contracts are designed.

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
   with a nondefault value for the new option so recoloring or zooming cannot reset it.
4. Separately update app [TerminalConfig](../jasper-app/src/main/java/dev/jasper/app/config/TerminalConfig.java),
   [ConfigLoader](../jasper-app/src/main/java/dev/jasper/app/config/ConfigLoader.java),
   [ConfigSnapshot](../jasper-app/src/main/java/dev/jasper/app/config/ConfigSnapshot.java), configuration
   template and [configuration guide](configuration.md). Add parser/default/error
   tests and an unrelated-reload retention test in `ConfigurationControllerTest`.
5. Run option/live-option tests, affected app config tests, then full check.

Existing example: `view.applyOptions(view.options().toBuilder().bell(BellMode.NONE).build());`
(on EDT). Immutable options alone do not implement a live change. Decide explicitly
whether the setting is live or new-session: scrollback capacity and shell launch
are new-session settings. Builders perform no I/O. Future SDK settings need an
app-owned persistence policy; don't teach the terminal to read TOML.

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
5. Test the whole stream path in
   [ShellIntegrationSessionTest](../jasper-terminal/src/test/java/dev/jasper/terminal/internal/emulation/ShellIntegrationSessionTest.java),
   including resets, repeated marks and truncated/oversized input, then full check.

Example fixture input: `fixture.feed("\033]133;A\007");` marks a prompt.
Use `EmulationFixture.open(80, 24, 100)` in try-with-resources and await
`fixture.session().shellIntegrationDetected()`. Reader callbacks must not block
on the EDT. Command start/finish callbacks run after buffer capture releases the
lock. Preserve OSC 7/133, OSC 8 link and q-final CSI behavior. Future plugin event
subscriptions need explicit cancellation and backpressure policies.

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
   default/indexed/RGB colors and reverse video. All live reads lock; shaping and
   painting use detached rows after unlock.
4. Run `./gradlew :jasper-terminal:refactorMeasurement` against the same runtime
   and fixtures as [verification](terminal-refactor-verification.md), plus tests
   and full check. Compare capture allocation as well as elapsed time. Native
   throughput/RSS acceptance is a separate user-run benchmark.

Existing example on EDT: `view.setPalette(Palette.jasperDark());`. For a font
metric experiment use `new FontSet("Monospaced", 14f, List.of(), false).cellWidth()`.
Avoid additional full-screen copies or an object per cell. Retained vendor entry
assumptions require tests on dependency upgrades. Future renderer capabilities
must consume snapshots, never expose the live buffer.

## Extend search

1. Extend [SearchQuery](../jasper-terminal/src/main/java/dev/jasper/terminal/search/SearchQuery.java)
   only for a real typed search input. Add algorithm tests in
   [TerminalSearchTest](../jasper-terminal/src/test/java/dev/jasper/terminal/internal/text/TerminalSearchTest.java).
2. Change [TerminalSearch](../jasper-terminal/src/main/java/dev/jasper/terminal/internal/text/TerminalSearch.java)
   for matching and [SearchController](../jasper-terminal/src/main/java/dev/jasper/terminal/view/SearchController.java)
   for admission, navigation or publication. Regex must run outside the buffer lock.
3. Run `SearchControllerTest` and `TerminalAppIntegrationTest` for stale results,
   hide/detach/reset cancellation and bounded queue behavior. Preserve
   `rowResetRejectsACompletedSearchAlreadyQueuedAheadOfReconciliation`: it covers
   history clear, RIS, alternate-screen switch and width reflow while a completed
   search is already queued ahead of EDT reconciliation. Check the atomic row epoch
   at admission and publication as well as query generation; run
   [TerminalSearchPaintingTest](../jasper-terminal/src/test/java/dev/jasper/terminal/view/TerminalSearchPaintingTest.java)
   for actual highlight behavior and viewport work bounds, then full check.

Example on EDT: `view.findAsync(new SearchQuery("error", false, false), result ->
statusLabel.setText(result.error() == null ? Integer.toString(result.count()) : result.error()));`.
Keep one running and at most one queued request, immutable published results and
latest-generation-only callbacks. Invalid regex is a result error, not a view
crash. SDK search access will need cancellation/ownership rules before exposure.

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
   local repaint; local copy/link/selection behavior must still work.

Example view-test input on EDT: `view.handleMouse(new MouseEvent(view,
MouseEvent.MOUSE_PRESSED, 0, InputEvent.BUTTON1_DOWN_MASK, 1, 1, 1, false,
MouseEvent.BUTTON1));`. Keep press-time modifiers through release, Shift bypass,
right-click behavior, out-of-bounds checks and fractional wheel accumulation.
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
Keep startup failure cleanup, repeated close safety and bounded shutdown. Future
plugins may consume a metadata capability; they should not own the native PTY.
