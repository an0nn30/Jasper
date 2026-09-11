# Moray Plan 3 — App Chrome Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** A tabbed, split-pane, multi-window Moray terminal with shared application actions, find UI and native desktop chrome.

**Architecture:** Plain SplitTree, TabState and KeyBindings models drive Swing windows. Pane owners retain TerminalSession/TerminalView across layout changes. Terminal-owned APIs expose input interception, appearance changes, local popup and asynchronous search without leaking emulator types.

**Tech Stack:** Java 25 on JBR, Gradle 9.7.0, Swing, FlatLaf/flatlaf-extras 3.7, JUnit 6.1.3 and AssertJ 3.27.7.

**Spec:** `docs/superpowers/specs/2026-09-11-moray-plan-3-app-chrome-design.md`; parent `docs/superpowers/specs/2026-09-10-moray-phase-1-terminal-design.md`.

**Execution update (2026-09-11):** The newly merged AGENTS.md and docs/STATUS.md reserve GUI launches and benchmarks for the user. Root acceptance below is a handoff checklist, not agent-run UI validation. Task 2 also fixes lost-release link gestures, invalidates alternate-screen selections/search, prunes expired prompt marks and ensures pane close terminates its child. Remaining performance/selection refinements in STATUS §5 remain explicitly deferred to terminal hardening after Plan 3. New commits include a Co-Authored-By trailer.

## Global Constraints

- Packages: `dev.moray.terminal` and `dev.moray.app`.
- Java 25 on JetBrains Runtime; preserve existing Gradle/JUnit/AssertJ versions.
- No public terminal method takes or returns a JediTerm type; moray-terminal never depends on moray-app.
- JediTerm 3.76 and pty4j 0.13.10 remain pinned. No jediterm-ui, emulator abstraction or plugin API.
- No interface without two real implementations. Use existing concrete classes and JDK callbacks.
- Terminal defaults: JetBrains Mono 14, 10,000 history lines, left Option-as-Meta. Preserve existing terminal behavior.
- New app actions and Swing mutations run on the EDT. Process launch and potentially expensive searches do not block it.
- macOS uses the screen menu bar; Linux/Windows use a window menu bar. Closing one window must not exit others.
- No config parser, theme-file loading, automatic config writing, SSH or packaging. Settings/reload are visibly disabled pending Plan 4.
- Use the new design's explicit Linux/Windows shortcut collision resolution; exact macOS defaults remain as in parent §5.1.
- Use TDD for behavior, commit each task's files, and do not launch the GUI from a subagent.

## File map

App models: SplitTree.java (layout/focus), TabState.java (names), ActionId.java (catalog), KeyBindings.java (parsing/defaults).
Terminal updates: TerminalView.java, TerminalSession.java; new TerminalAppearanceTest.java and TerminalAppIntegrationTest.java.
App UI: MorayApplication.java (window lifecycle), TerminalWindow.java (actions/chrome/tabs), TerminalTab.java (split rendering), TerminalPane.java (session lifecycle/find), FindBar.java, AppIcons.java and bundled SVG/license resources. Further small classes may separate cohesive responsibilities; avoid generic frameworks.
App tests: SplitTreeTest.java, TabStateTest.java, KeyBindingsTest.java, and Swing/lifecycle integration tests named for the unit under test.

---

### Task 1: Plain application models and keybindings

**Files:**
- Create: `moray-app/src/main/java/dev/moray/app/{SplitTree,TabState,ActionId,KeyBindings}.java`
- Test: `moray-app/src/test/java/dev/moray/app/{SplitTreeTest,TabStateTest,KeyBindingsTest}.java`

**Interfaces:**
- `SplitTree(UUID firstPane)`; nested `Direction { LEFT, RIGHT, UP, DOWN }`, `Axis { RIGHT, DOWN }`, sealed `Node`, `Leaf(UUID paneId)`, `Branch(UUID id, Axis axis, double ratio, Node first, Node second)`.
- `Optional<Node> root()`, `Optional<UUID> focused()`, `List<UUID> panes()`, `boolean zoomed()`, `void focus(UUID)`, `void split(UUID newPane, Axis)`, `void close(UUID)`, `void navigate(Direction)`, `void toggleZoom()`, `void setRatio(UUID branchId, double ratio)`. Reject duplicate pane IDs and invalid IDs predictably; ratios must be finite and clamped 0.1–0.9.
- `TabState`: `void rename(String)`, `String title(String shellTitle, Path workingDirectory)`; null/blank rename restores automatic naming. Focused pane details are passed, not stored redundantly.
- `ActionId` enum lists every parent §5.1 action, including SELECT_TAB_1 through SELECT_TAB_9; `String id()`, `String label()`, `String defaultBinding()`.
- `KeyBindings.defaults(boolean macOs)`, `KeyBindings.withOverrides(boolean macOs, Map<String,String>)`, `Optional<ActionId> actionFor(KeyStroke)`, `Optional<KeyStroke> strokeFor(ActionId)`, `static Optional<KeyStroke> parse(String, boolean macOs)`. Invalid overrides throw IllegalArgumentException with action/key/collision context; unknown action names included. Plan 4 can translate them to diagnostics.

- [ ] **Step 1: Write model tests and observe failure.** Include actual sequences like:

```java
UUID a = UUID.randomUUID(), b = UUID.randomUUID(), c = UUID.randomUUID();
SplitTree tree = new SplitTree(a);
tree.split(b, SplitTree.Axis.RIGHT);
tree.split(c, SplitTree.Axis.DOWN);
tree.navigate(SplitTree.Direction.UP);
assertThat(tree.focused()).contains(b);
tree.close(b);
assertThat(tree.panes()).containsExactly(a, c);
tree.toggleZoom();
assertThat(tree.zoomed()).isTrue();
tree.close(c);
assertThat(tree.panes()).containsExactly(a);
```

Add ratio retention, asymmetric directional geometry, no-wrap navigation, last-close, rename precedence and invalid-input cases. Key tests assert cmd+c is META+C on macOS and CTRL+SHIFT+C elsewhere; defaults have no duplicate keystrokes on any platform; `none` removes copy; explicit duplicate overrides produce useful errors; unknown key/action is rejected; F2, punctuation and arrows parse.

Run `./gradlew :moray-app:test --tests '*SplitTreeTest' --tests '*TabStateTest' --tests '*KeyBindingsTest'` and record missing-class failure.

- [ ] **Step 2: Implement models.** Rebuild immutable tree nodes through recursive replacement. Traverse normalized unit rectangles according to branch ratios to select directional neighbors. Tie-break by forward edge distance then perpendicular center distance then stable traversal order. Keep focus valid after removing any leaf; prefer the surviving sibling subtree's first leaf when focused leaf is removed. For keys, expand cmd to META or CTRL|SHIFT; add ALT for non-mac defaults containing explicit cmd+shift. Parse overrides literally, remove their previous defaults before validating final collisions, and report both action names for a collision.

```java
int primary = macOs ? InputEvent.META_DOWN_MASK
    : InputEvent.CTRL_DOWN_MASK | InputEvent.SHIFT_DOWN_MASK;
// Extra Shift in non-mac defaults needs a distinct chord.
int extra = !macOs && binding.contains("cmd+shift") ? InputEvent.ALT_DOWN_MASK : 0;
```

- [ ] **Step 3: Run the covering tests and app check.** Expect all model tests to pass; examine the exact final shortcut map for collisions.
- [ ] **Step 4: Commit and report.** Commit only this task's source/tests; report command/output and final APIs.

### Task 2: Terminal APIs for app ownership

**Files:**
- Modify: `moray-terminal/src/main/java/dev/moray/terminal/TerminalView.java`
- Modify: `moray-terminal/src/main/java/dev/moray/terminal/TerminalSession.java`
- Modify: `moray-terminal/src/main/java/dev/moray/terminal/PtyConnector.java` and `SessionDisplay.java` as needed for close and alternate-buffer callbacks.
- Test: `moray-terminal/src/test/java/dev/moray/terminal/TerminalAppIntegrationTest.java`
- Test: `moray-terminal/src/test/java/dev/moray/terminal/TerminalAppearanceTest.java`
- Modify existing interaction tests only where app-owned vs standalone behavior needs a new assertion.

**Interfaces:**
- `TerminalView.setShortcutHandler(Predicate<KeyEvent>)`: app before encoder; null reinstates standalone built-ins. Keep Shift+PageUp/PageDown. Every handled pressed key suppresses following typed event.
- `TerminalView.pasteClipboard()`, `clearScrollback()`, `float fontSize()`, `setFontSize(float)`, `resetFontSize()`, `setInactiveDim(float)` (0–1 overlay, owner supplies 0 for active pane).
- `TerminalView.setContextMenuHandler(Consumer<MouseEvent>)`: callback for local popup trigger, never application-owned right-click; focus clicked pane first in app. Preserve Shift bypass.
- `TerminalView.findAsync(String query, boolean regex, boolean caseSensitive, Consumer<FindResult>)`: capture/search without blocking EDT, latest result only, callback on EDT. Keep existing synchronous find for tests/backward compatibility. `clearFind` invalidates pending results; view detach/disposal must not leak worker threads. `setFindResultListener(Consumer<FindResult>)` informs bar when reflow/history reset invalidates count. Existing `findNext`/`findPrevious` remain usable.
- `TerminalSession.clearScrollback()` clears only history under the buffer lock, preserves live screen, notifies through existing scrollback reset mechanism.

- Additional carryovers: reset lost link-gesture capture on each new press and focus loss; clear selection, pending async results and matches on alternate-buffer transitions; prune evicted prompt rows during history eviction. Close owned PTY with hang-up where supported and bounded forced termination for an unresponsive child, without waiting on the EDT. Preserve final output on normal exit.

- [ ] **Step 1: Write and run failing tests.** Add regressions for a link press with missing release followed by a reported gesture, alternate-screen row invalidation, prompt eviction, and a real non-GUI process that ignores normal termination. Use FakeConnector, Await and SwingUtilities. Prove an intercepted Ctrl+D does not reach the PTY while unintercepted input does; an app handler returning false prevents fallback copy shortcuts; standalone copy still works. Prove font resize preserves the session/text and changes metrics; reset returns 14. History clear preserves screen rows. Popup fires only for locally owned gestures. A cleared or superseded async query cannot restore stale highlights/count.

```java
view.setShortcutHandler(event -> event.getKeyCode() == KeyEvent.VK_D);
view.handleKey(new KeyEvent(view, KeyEvent.KEY_PRESSED, 0,
    InputEvent.CTRL_DOWN_MASK, KeyEvent.VK_D, (char) 4));
assertThat(connector.written()).isEmpty();
```

Run `./gradlew :moray-terminal:test --tests '*TerminalAppIntegrationTest' --tests '*TerminalAppearanceTest'` and record failure before implementation.

- [ ] **Step 2: Implement the ownership hooks.** Intercept inside the existing key press path so key suppression remains centralized. Reconstruct FontSet/TerminalPainter using current palette/font options for size changes and resize existing session to fit. Apply dim overlay after painting. Use existing JediTerm buffer history clearing API discovered from the pinned jar, never inject a clear command into the shell. For async search use bounded execution and a monotonically increasing request generation; workers calculate only, EDT applies results if generation matches. Coalesce pending requests; do not queue unbounded queries or spawn a thread per keystroke.

```java
long generation = ++searchGeneration;
// Worker computes result; EDT completion checks generation before applying it.
SwingUtilities.invokeLater(() -> {
    if (generation == searchGeneration) {
        // Replace matches and notify the current find UI.
    }
});
```

- [ ] **Step 3: Run terminal module check.** All previous tests and new integration tests must pass. Report worker lifecycle/cancellation limits explicitly.
- [ ] **Step 4: Commit and report.** List exact public hooks consumed by Task 3, including thread/lifecycle contracts.

### Task 3: Working multi-window application and desktop chrome

**Files:**
- Modify: `moray-app/build.gradle.kts`, `moray-app/src/main/java/dev/moray/app/Main.java`
- Create: `moray-app/src/main/java/dev/moray/app/{MorayApplication,TerminalWindow,TerminalTab,TerminalPane,FindBar,AppIcons}.java`
- Create: `moray-app/src/main/resources/dev/moray/app/icons/*.svg` and `LICENSE.txt`
- Test: `moray-app/src/test/java/dev/moray/app/{FindBarTest,TerminalTabTest,ApplicationActionsTest}.java`; add focused lifecycle helpers/tests as needed.

**Interfaces:**
- Consumes Task 1 models and Task 2 terminal hooks; inspect their final report before implementation.
- `MorayApplication` owns all windows and shell-launch executor; `newWindow(Path)`, `quit()`.
- `TerminalWindow` owns JFrame and shared Action map; closing a window only disposes its tabs/panes, deregisters handlers and informs application.
- `TerminalTab` owns SplitTree and a map UUID→TerminalPane; renders tree using nested JSplitPane without restarting sessions; tab title derives from TabState and focused pane.
- `TerminalPane` owns one asynchronously started session, view and find bar; close is idempotent and handles launch races. Tracks launch directory, current OSC directory and shell label. Launch failures must reach the user.
- `FindBar` uses async find, debounce timer, count/error label, next/previous, Case/Regex, Escape/close; reset invalidation updates count.

- [ ] **Step 1: Add tests and observe failure.** Headless Swing tests exercise find interactions with real terminal where practical, tab lifecycle/model rendering and shared action dispatch. Include closing one of multiple owners preserves the others, pending launch closed before completion closes the returned session, title override survives OSC changes, and tab reorder preserves active identity. Keep test injection as standard JDK functions/concrete objects, not new production interfaces with one real implementation. Tests should assert user-observable state and terminal bytes, not repeat implementation details.

```java
// Common setup for Swing interaction tests:
SwingUtilities.invokeAndWait(() -> {
    // Construct real non-window components, invoke their actions,
    // and assert focus target, title, or resulting terminal operation.
});
```

Run the named new tests to show missing behavior before coding. Window behavior that needs a real screen goes in the root agent's GUI smoke checklist, not fake JFrame mocks.

- [ ] **Step 2: Implement ownership and tab/split UI.** Launch shells via the app-owned executor. Completion executes on EDT and checks owner disposal. Render proportional dividers only after layout gives a usable size; preserve ratios when components are rebuilt and suppress divider property writes during restoration. Selection/focus never recreate sessions. Tab strip supports close buttons, middle-click, drag reorder, and F2 rename with blank restoring automatic naming. Render find bar at top of pane; retain query state per pane.

```java
executor.execute(() -> {
    // Start shell using DefaultShell.command, inherited working directory and defaults.
    SwingUtilities.invokeLater(() -> {
        // If owner closed, close session; otherwise attach view/listeners and focus it.
    });
});
```

- [ ] **Step 3: Implement action routing and all chrome.** Resolve the Task 1 review minor in KeyBindings: F13–F24 Java keycodes are not contiguous with F1–F12; map from VK_F13 and test an override resolving the actual F13 stroke. Register each ActionId once per window; resolve current pane at invocation. Terminal shortcut handler uses KeyStroke.getKeyStrokeForEvent and the keybinding map. Register root pane bindings for focus outside the terminal while retaining native text-field copy/paste; avoid duplicate delivery. All executable actions have menu entries. Toolbar mode radio items switch icons+labels/icons/hidden, status visibility toggles, appearance switches light/dark. Status shows focused shell/directory/size. Context menu uses shared actions and obeys mouse routing. Main installs FlatLaf before creating UI and removes per-window System.exit; keep windowTitle helper tests.

Add dependencies:
```kotlin
implementation("com.formdev:flatlaf:3.7")
implementation("com.formdev:flatlaf-extras:3.7")
```

Bundle Tabler outline SVGs from the user-supplied ~/projects/tabler-icons repository for seven toolbar icons and retain its MIT license/source attribution. Use FlatSVGIcon to load resources with meaningful accessible names/tooltips. Add Settings and Reload config in toolbar/menus disabled, with a tooltip explaining configuration support is unavailable (no internal plan number in product UI); Built-in defaults status must be honest.

- [ ] **Step 4: Verify application tests and full check.** Run `./gradlew check --rerun-tasks`. Check `git diff --check`. Do not launch UI from subagent. Report exact commands, counts, warnings, remaining manual validation, resource license provenance, and any spec discrepancies.
- [ ] **Step 5: Commit and report.** Root performs task review and actual GUI smoke, then final branch review. Update completion checkboxes only after reviewed success.

## Root acceptance and finish

- [ ] Task reviews approve spec compliance and quality; findings fixed and re-reviewed.
- [ ] Full local check succeeds on final code.
- [ ] User-run GUI smoke (agent must not launch it per AGENTS.md): create second window, tabs, nested splits, focus/zoom/restore, rename/reorder, find, clipboard, font controls, close and independent window lifetime. Inspect actual rendered chrome.
- [ ] User-run benchmark after integration, only without a running game or VM; minimum 35 MB/s, target 45 MB/s.
- [ ] Record remaining manual terminal checks and three-platform CI status; do not claim Phase 1 complete.
- [ ] Final whole-branch review; retain completed code on feature branch for user review.
