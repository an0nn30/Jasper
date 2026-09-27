# Editor Plugin, Plan A: SDK 0.8.2 and the Editor for Local Files — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Status:** Executed 2026-09-27 on branch claude/editor-plugin, subagent-driven (one implementer and one reviewer per task). Deviations from the task text, from the task reviews:
- Task 1 (ruling): the testkit's `FakeWindow.closeCommand()` returns true for an already-closed window, matching `AuxiliarySurface.closeCommand()` (applied in Task 2).
- Task 2 (review fix): `JasperApplication.quitNow()` quits without asking plugin windows; start-up rollback, a `--background` launch that cannot stay resident and the benchmark exit use it, while user quit/restart and a resident's retire request keep `quit()`.
- Task 5 (review fix, replaces planning decision 4): the TOML lexer carries line state in private negative end-of-line token types `-(1 + depth*3 + kind)` (array depth and multi-line string kind), as spec §3.2 first proposed, so nested arrays spanning lines keep their depth; `getClosestStandardTokenTypeForInternalType` maps them back.
- Task 6 (review fix): `LocalFiles.write` also writes in place when `Files.move` reports `AtomicMoveNotSupportedException` (spec §4.4's fourth trigger); untested like the owner-mismatch fallback.
- Task 7 (review fixes): a reload whose text changed while its read was in flight keeps the text and shows the CHANGED banner; save and Save As cancel an in-flight read; reloads, Revert and banner Reload do nothing while a save runs; Save As during a pending save chains after it.
- Task 8 (review fix): the watcher also catches unchecked exceptions from a stat, so polling never stops.
- Task 11 (deviation and review fixes): `FindBar` validates a regular expression itself (RSyntaxTextArea swallows invalid patterns), compiling exactly what `SearchEngine` compiles (`\b…\b` with whole word); Escape is bound once on the bar, so it closes from any control in it.
- Task 12 (ruling): a shared `Forms.bind(component, condition, stroke, name, action)` replaces near-identical key-binding helpers in `LanguagePicker` and `FindBar`.
- Task 13 (ruling and review fixes): `EditorTab.menuItem(Command)` builds menu items for the right-click menu and the ⋯ menu; closing a tab after its save re-checks for edits typed during the save; the closing guard loops — it waits only on running saves, asks about what is still unsaved, and re-checks after Save All.
- Final review (fix wave): the safe-write sibling is created owner-only before any bytes are written; a read-only target is refused; a failed automatic reload shows the banner instead of a dialog; Save All honours Cancel and closed tabs; the closing guard refuses re-entry; destructive questions default to Cancel; non-regular files are refused and reads are bounded; package-info files name their dependencies.

Not done: plan B (SFTP editing through Remote), GUI acceptance (user-run), merge and push.

**Goal:** A bundled Editor plugin that opens, edits and saves local text files in tabs inside one Editor window, built on RSyntaxTextArea, on top of SDK 0.8.2 (quit asks plugin windows, a Close Window command handler, a save-file chooser).

**Architecture:**
- **SDK 0.8.2.** `PluginWindow.onCloseCommand`, quit and restart consulting plugin window guards, and `Windows.chooseSaveFile` are added to the SDK, the application (`AuxiliarySurface`, `AuxiliaryWindows`, `HostedUi`, `NativeShells`, `JasperApplication`) and the testkit.
- **The plugin.** `plugins/editor` exports `dev.jasper.editor.api` (`Editor`, `EditorDocument` and values), which Plan B's Remote will call.
- **Internals, bottom up:**
  - `text`: pure decoding and encoding.
  - `syntax`: language detection and a hand-written TOML lexer.
  - `local`: the write contract, `LocalDocument` and a polling watcher.
  - `session`: Swing-free per-tab state machine and the tab model.
  - `ui`: settings, theme, the tab view, find bar, dialogs and the Editor window.
  - `EditorPlugin`: wiring.

**Tech Stack:** Java 25 (JBR), Swing, FlatLaf (through client-property strings only), RSyntaxTextArea 3.6.0, JUnit 5, AssertJ.

**Spec:** `docs/superpowers/specs/2026-09-27-jasper-editor-plugin-design.md`

**Planning evidence** (checked before this plan was written):
- RSyntaxTextArea 3.6.0 sources:
  - `AbstractTokenMakerFactory.putMapping(String, String, ClassLoader)` exists.
  - `TokenMakerBase` offers `addToken(char[], start, inclusiveEnd, type, startOffset)`, `addNullToken()` and `getLastTokenTypeOnLine`.
  - `SearchEngine.find`, `markAll`, `replace` and `replaceAll` all take a `SearchContext` (`setSearchWrap`, `setMarkAll`, …).
  - `Theme.load(InputStream)` and `Theme.apply(RSyntaxTextArea)` exist, and so do `RTextArea.getPopupMenu()`, `setTabsEmulated`, `discardAllEdits`, `RTextScrollPane.getGutter()` and the `Gutter` colour setters.
  - The default input maps bind ⌘/ to toggle comment, ⌘[ to go-to-matching-bracket and **⇧⌘S to "end macro recording"**. The Editor therefore installs its own bindings in each text area's `WHEN_FOCUSED` map, which overrides those defaults.
- The testkit's modal `PluginDialog.show()` returns at once and never blocks, so no Editor logic may rely on a blocking `show()`. Questions and waits are injected functions.
- Plugin dialogs are `DOCUMENT_MODAL`, and `DialogSpec` owners can only be windows. A modal "Saving…" dialog would therefore block conflict prompts owned by the same window, so the production wait is a non-modal dialog plus a `SecondaryLoop`.
- `Toolkit.getMenuShortcutKeyMaskEx()` throws when headless, so shortcuts use `os.name` to choose Meta or Ctrl.

**Planning decisions** (rulings where the spec leaves room; the spec is binding otherwise):
1. **`onCloseCommand` is on `PluginWindow`, not `WindowSurface`.** Spec §1.4 names `WindowSurface`, but only plugin windows have a Close Window command; dialogs and overlays have none.
2. **⌘W is never put in an input map.** The Editor relies only on the window's Close Window accelerator, which is present on every platform, through `onCloseCommand`. This avoids closing two tabs if both the screen menu and the key binding fired.
3. **Unsaved-changes tracking compares the text with the saved text using `String.equals`.** Spec §4.3 says length and hash. Equality is exact, and `equals` checks the length first.
4. **The TOML lexer carries state in the string token types themselves.** A line ending inside `"""` ends with `LITERAL_STRING_DOUBLE_QUOTE`, one inside `'''` with `LITERAL_CHAR`, and one inside a multi-line array with a zero-length `SEPARATOR`. This is how RSyntaxTextArea's own Python lexer carries `"""`; spec §3.2 said private negative types, which are unnecessary. Superseded during execution: see the Status banner (Task 5).
5. **`EditorSettings` lives in `dev.jasper.editor.ui`,** not the root package, so the root package (which wires the UI) and `ui` do not form a cycle.
6. **Selections keep token colours.** `TextArea.selectionForeground` is not applied, because RSyntaxTextArea draws token colours over the selection background unless told otherwise, which is IntelliJ's look. The gutter border uses the gutter background, so no border line shows.
7. **Wrong-type settings detection:** a value is "present but wrong" when another accessor (`string`, `integer`, `bool`, `stringList`, `table`) sees it. A float such as `13.5` has no accessor and is treated as absent.
8. **The local owner-mismatch fallback is not unit-tested,** because it needs files owned by another user (root). Plan B tests the same decision with a fake endpoint.
9. **A failed reload keeps the text.** Revert, Reload and the watcher's silent reload keep the tab READY while fetching; a failure asks an error question and leaves the current text, rather than showing the failed-open state and losing edits.
10. **The Save All wait is sequential.** Saves run one after another, so each conflict prompt appears on its own.
11. **Spec §9's `Prompts` is three classes:**
    - `DialogAsker`, for modal questions;
    - `SaveWait`, for the Save All wait;
    - `Forms`, their shared layouts.

    `LanguagePicker`, `GoToLinePanel`, `FileDrop`, `KeyNames`, `Command` and `EditorEnvironment` are further `ui` classes the spec did not list.
12. **A tab that is saving is not asked about when the window closes or Jasper quits.** The guard first waits for running saves, then asks about whatever is still unsaved. This covers Review Focus 3, and edits typed after a save's snapshot are still asked about.

## Global Constraints

- **Build:** Java 25 on JBR 25, built with `./gradlew` only. `./gradlew check` passes at the end of every task. For counts, read `*/build/test-results/test/*.xml`.
- **SDK:** `JasperSdk.VERSION = "0.8.2"`. New SDK methods carry full Javadoc (doclint runs in `check`) with `@since 0.8.2`.
- **Editor manifest:** `id = "dev.jasper.editor"`, `version = "0.1.0"`, `sdk = ">=0.8.2, <0.9"`, `capabilities = []`, `exports = ["dev.jasper.editor.api"]`.
- **Dependencies:** `com.fifesoft:rsyntaxtextarea:3.6.0` is `implementation` in `plugins/editor` only. There is no other new dependency, and nothing new in the app or SDK.
- **Plugin boundary:** plugin code references only the JDK, the SDK and RSyntaxTextArea (`verifyPluginArchitecture`). FlatLaf is reached only through client-property string keys whose values are JDK types.
- **Threads:** everything in `session` and `ui` runs on the UI thread. File I/O, decoding and encoding run on `context.background()`. Nothing blocks the UI thread except modal dialogs and the save wait's `SecondaryLoop`.
- **Source hygiene:** no raw control, private-use or unpaired-surrogate characters in source (AGENTS.md check). `…`, `●`, `⌘`, `⇧`, `⌥`, `⌃`, `→`, `—` and `·` are ordinary characters and are allowed.
- **Never** launch the GUI (`:jasper-app:run`) from an agent; GUI checks are the user's.
- **Commits:** one per task on `claude/editor-plugin`. Every message ends with:

  ```
  🤖 Generated with Claude Code at The Home Depot

  Co-Authored-By: Claude <noreply@anthropic.com>
  ```

## Review Focus

Inputs the spec implies but no feature step exercises on its own; each has its test in the owning task.
1. **An empty file** (0 bytes) opens as an empty UTF-8/LF tab and saves back as zero bytes. Tests: Task 4 `anEmptyFileRoundTripsToZeroBytes`, Task 6 `anEmptyFileReadsAndWritesAsZeroBytes`.
2. **Opening a file again while its first read is still loading** selects the loading tab and starts no second read. Test: Task 13 `openingAFileThatIsStillLoadingSelectsItsTab`.
3. **Quitting while a save is running** (the tab no longer dirty, but saving) waits for that save instead of quitting mid-write. Test: Task 13 `quitWaitsForASaveThatIsStillRunning`.
4. **A folder:** `Editor.open(folder)` shows "is a folder" in the tab, and folders dropped on the window are ignored. Tests: Task 6 `aFolderIsRefused`, Task 12 `droppedFoldersAreIgnoredFilesOpenAndTextGoesToTheDelegate`.
5. **A save whose write fails** (a read-only file) leaves the tab dirty and the file untouched, and the next save retries. Test: Task 7 `aFailedWriteLeavesTheTabDirtyAndTheNextSaveRetries`.

## File Structure

**SDK, testkit, application (Tasks 1–3):**

| File | Change |
|---|---|
| `jasper-sdk/src/main/java/dev/jasper/sdk/ui/PluginWindow.java` | `onCloseCommand` |
| `jasper-sdk/src/main/java/dev/jasper/sdk/ui/WindowSurface.java` | `onClosing` Javadoc: quit asks window guards |
| `jasper-sdk/src/main/java/dev/jasper/sdk/ui/Windows.java` | `chooseSaveFile` |
| `jasper-sdk/src/main/java/dev/jasper/sdk/JasperSdk.java` | `0.8.2` |
| `jasper-sdk-testkit/src/main/java/dev/jasper/sdk/testing/FakeUi.java` | fake close command, `mayClose`, save chooser |
| `jasper-sdk-testkit/src/main/java/dev/jasper/sdk/testing/FakePluginHost.java` | `closeCommand`, `requestQuit` |
| `jasper-sdk-testkit/src/testFixtures/.../contract/ContractHarness.java`, `PluginContractTest.java` | two harness methods, two contract tests |
| `jasper-app/src/main/java/dev/jasper/app/windows/AuxiliarySurface.java` | `onCloseCommand`, `closeCommand`, `mayClose` |
| `jasper-app/src/main/java/dev/jasper/app/windows/AuxiliaryWindows.java` | `mayQuit` |
| `jasper-app/src/main/java/dev/jasper/app/windows/PathChoice.java` | `Mode` replaces `directory` |
| `jasper-app/src/main/java/dev/jasper/app/windows/NativeShells.java` | Close Window runs `closeCommand`; save dialog |
| `jasper-app/src/main/java/dev/jasper/app/plugins/HostedUi.java` | `onCloseCommand`, `chooseSaveFile` |
| `jasper-app/src/main/java/dev/jasper/app/application/JasperApplication.java` | quit and restart ask plugin windows |

**Editor plugin (`plugins/editor`, Tasks 4–14):**

| File | Responsibility |
|---|---|
| `build.gradle.kts`, `src/main/resources/plugin.toml`, `settings.toml` | module, manifest, commented settings |
| `api/Editor.java`, `EditorDocument.java`, `Content.java`, `Revision.java`, `TooLargeException.java` | exported API |
| `text/LineSeparator.java`, `TextFormat.java`, `DecodedText.java`, `TextCodec.java`, `BinaryFileException.java`, `UnencodableException.java` | pure decoding and encoding |
| `syntax/Languages.java`, `SyntaxDetector.java`, `TomlTokenMaker.java` | languages, detection, TOML lexer |
| `local/LocalFiles.java`, `LocalDocument.java`, `LocalWatcher.java` | write contract, local document, polling |
| `session/Question.java`, `SessionEnvironment.java`, `Failures.java`, `DocumentSession.java`, `EditorModel.java` | per-tab state machine and tab model |
| `ui/EditorSettings.java`, `EditorTheme.java`, `KeyNames.java`, `Command.java` | settings, colours, shortcuts |
| `ui/EditorTab.java`, `StatusLine.java` | one tab's view and the status line |
| `ui/FindBar.java` | find and replace |
| `ui/LanguagePicker.java`, `GoToLinePanel.java`, `FileDrop.java`, `DialogAsker.java`, `SaveWait.java` | dialogs and pickers |
| `ui/EditorEnvironment.java`, `EditorWindow.java` | the Editor window |
| `EditorPlugin.java` | entry point |

Package paths are `plugins/editor/src/main/java/dev/jasper/editor/<package>/`. Tests mirror them under `src/test/java`.

**Application tests touched by bundling:** `jasper-app/src/test/java/dev/jasper/app/plugins/PluginZipsTest.java` (Task 4) and `BundledSamplePluginTest.java` (Task 14). `jasper-app/build.gradle.kts` `stagePlugins` also changes in Task 14.

---

### Task 1: A plugin window's Close Window command (SDK 0.8.2)

**Files:**
- Modify: `jasper-sdk/src/main/java/dev/jasper/sdk/ui/PluginWindow.java`
- Modify: `jasper-sdk/src/main/java/dev/jasper/sdk/JasperSdk.java:6`
- Modify: `jasper-sdk/src/test/java/dev/jasper/sdk/palette/PaletteValuesTest.java:65`, `jasper-sdk/src/test/java/dev/jasper/sdk/ui/AppearanceContractTest.java:15`
- Modify: `jasper-sdk-testkit/src/main/java/dev/jasper/sdk/testing/FakeUi.java` (inner `FakeWindow`)
- Modify: `jasper-sdk-testkit/src/main/java/dev/jasper/sdk/testing/FakePluginHost.java` (after `requestClose`)
- Modify: `jasper-sdk-testkit/src/testFixtures/java/dev/jasper/sdk/testing/contract/ContractHarness.java`, `PluginContractTest.java`
- Modify: `jasper-sdk-testkit/src/test/java/dev/jasper/sdk/testing/FakeContractTest.java`, `jasper-app/src/test/java/dev/jasper/app/plugins/AppContractTest.java`
- Modify: `jasper-app/src/main/java/dev/jasper/app/windows/AuxiliarySurface.java`, `jasper-app/src/main/java/dev/jasper/app/plugins/HostedUi.java:330`, `jasper-app/src/main/java/dev/jasper/app/windows/NativeShells.java` (`menuBar`)
- Test: `jasper-app/src/test/java/dev/jasper/app/windows/AuxiliaryWindowsTest.java`
- Docs: `docs/sdk-architecture.md`, `docs/plugin-authoring.md`

**Interfaces:**
- Produces:
  - `PluginWindow.onCloseCommand(BooleanSupplier handler) -> Subscription`;
  - `AuxiliarySurface.onCloseCommand(BooleanSupplier) -> dev.jasper.app.lifecycle.Subscription` and `AuxiliarySurface.closeCommand() -> boolean` (true when the window closed);
  - `FakePluginHost.closeCommand(String windowId) -> boolean`;
  - `ContractHarness.closeCommand(String windowId) -> boolean`.

- [ ] **Step 1: Write the failing tests**

In `PluginContractTest.java`, after the test `windowsAreSingletonsGuardedOwnedAndClosedWithTheirPlugin`, add:

```java
    @Test void theCloseCommandAsksTheContentNewestFirstBeforeTheGuards() {
        var handled = new java.util.concurrent.atomic.AtomicBoolean(true);
        var allow = new java.util.concurrent.atomic.AtomicBoolean(false);
        List<String> asked = Collections.synchronizedList(new ArrayList<>());
        h.start(info("test.alpha"), Set.of(), Set.of(), context -> {
            PluginWindow window = context.windows().create(new WindowSpec("test.alpha.editor", "Editor", new Dimension(640, 480), true));
            window.onCloseCommand(() -> { asked.add("older"); return false; });
            window.onCloseCommand(() -> { asked.add("newer"); return handled.get(); });
            window.onCloseCommand(() -> { throw new IllegalStateException("handler failure"); });
            window.onClosing(() -> { asked.add("guard"); return allow.get(); });
            window.show();
        });
        h.ui(() -> assertThat(h.closeCommand("test.alpha.editor")).as("the newest handler that answers true keeps it open").isFalse());
        assertThat(asked).containsExactly("newer");
        asked.clear();
        handled.set(false);
        h.ui(() -> assertThat(h.closeCommand("test.alpha.editor")).as("unhandled, the guard still refuses").isFalse());
        assertThat(asked).containsExactly("newer", "older", "guard");
        asked.clear();
        allow.set(true);
        h.ui(() -> {
            assertThat(h.closeCommand("test.alpha.editor")).isTrue();
            assertThat(h.closeCommand("test.alpha.absent")).isFalse();
        });
        assertThat(asked).containsExactly("newer", "older", "guard");
        assertThat(h.windows()).isEmpty();
    }
```

In `ContractHarness.java`, after `boolean requestClose(String windowId);`, add:

```java
    /**
     * UI thread: runs the Close Window command of the first open window with this id: its close-command
     * handlers newest first, then its closing guards. True when the window closed.
     */
    boolean closeCommand(String windowId);
```

In `FakeContractTest.java`, after the `requestClose` override, add:

```java
            @Override public boolean closeCommand(String windowId) { return host.closeCommand(windowId); }
```

In `AppContractTest.java`, after the `requestClose` override, add:

```java
            @Override public boolean closeCommand(String windowId) {
                return onEdtValue(() -> auxiliary.open().stream().filter(surface -> surface.id().equals(windowId)).findFirst()
                    .map(dev.jasper.app.windows.AuxiliarySurface::closeCommand).orElse(false));
            }
```

In `AuxiliaryWindowsTest.java`, add:

```java
    @Test void theCloseCommandAsksHandlersNewestFirstThenCloses() {
        AuxiliarySurface editor = windows.window("dev.x.editor", "Editor", new Dimension(640, 480), true);
        List<String> asked = new ArrayList<>();
        boolean[] handled = {true};
        editor.onCloseCommand(() -> { asked.add("older"); return false; });
        editor.onCloseCommand(() -> { asked.add("newer"); return handled[0]; });
        editor.onCloseCommand(() -> { throw new IllegalStateException("boom"); });
        editor.show();
        assertThat(editor.closeCommand()).isFalse();
        assertThat(asked).containsExactly("newer");
        handled[0] = false;
        assertThat(editor.closeCommand()).as("nothing handled it and no guard refused").isTrue();
        assertThat(asked).containsExactly("newer", "newer", "older");
        assertThat(editor.closed()).isTrue();
    }
```

In both `PaletteValuesTest.java:65` and `AppearanceContractTest.java:15`, change `isEqualTo("0.8.1")` to `isEqualTo("0.8.2")`.

- [ ] **Step 2: Run them to verify they fail**

Run: `./gradlew :jasper-sdk:test :jasper-sdk-testkit:test :jasper-app:test --tests '*AuxiliaryWindowsTest' --tests '*AppContractTest'`
Expected: compilation fails with `cannot find symbol: method onCloseCommand(...)` and `closeCommand(String)`.

- [ ] **Step 3: Add the SDK method and version**

Replace `jasper-sdk/src/main/java/dev/jasper/sdk/ui/PluginWindow.java` with:

```java
package dev.jasper.sdk.ui;

import dev.jasper.sdk.Subscription;
import dev.jasper.sdk.WindowOwner;
import java.util.function.BooleanSupplier;

/** A plugin's own top-level window; it can own dialogs. */
public interface PluginWindow extends WindowSurface, WindowOwner {
    /**
     * Adds a handler for the window's Close Window command: File → Close Window and its shortcut, Cmd+W on
     * macOS and Ctrl+W elsewhere. Handlers run on the UI thread, newest first. The first that returns true has
     * handled the command, for example by closing a tab, and the window stays open. When none returns true,
     * the window closes through its {@link #onClosing} guards, as if the user had clicked its close button. The
     * close button does not consult these handlers. A handler that throws has not handled the command.
     *
     * @param handler returns whether the content handled the command
     * @return the registration
     * @since 0.8.2
     */
    Subscription onCloseCommand(BooleanSupplier handler);
}
```

In `JasperSdk.java`, change `VERSION = "0.8.1"` to `VERSION = "0.8.2"`.

- [ ] **Step 4: Implement it in the testkit**

In `FakeUi.java`, inside `final class FakeWindow`, after the `onClosed` override, add:

```java
        final List<BooleanSupplier> closeCommands = new ArrayList<>();
        @Override public Subscription onCloseCommand(BooleanSupplier handler) {
            closeCommands.add(java.util.Objects.requireNonNull(handler));
            return () -> closeCommands.remove(handler);
        }
        boolean closeCommand() {
            if (closed) return false;
            List<BooleanSupplier> handlers = new ArrayList<>(closeCommands);
            java.util.Collections.reverse(handlers);
            for (BooleanSupplier handler : handlers) {
                try { if (handler.getAsBoolean()) return false; }
                catch (RuntimeException failure) { host.recordFailure(pluginId() + " close command: " + failure); }
            }
            return requestClose();
        }
```

In `FakePluginHost.java`, after the `requestClose(String windowId)` method, add:

```java
    /**
     * Runs the Close Window command of the first open window with this id, as File → Close Window or Cmd/Ctrl+W
     * would: its close-command handlers newest first, then, when none handled it, its closing guards.
     *
     * @param windowId the window id
     * @return true when the window closed; false when a handler handled the command, a guard vetoed or there is no such window
     * @since 0.8.2
     */
    public boolean closeCommand(String windowId) {
        for (FakePluginContext context : contexts.values())
            for (FakeUi.FakeWindow window : List.copyOf(context.ui.windows)) if (window.id.equals(windowId)) return window.closeCommand();
        return false;
    }
```

- [ ] **Step 5: Implement it in the application**

In `AuxiliarySurface.java`, add a field after `guards`:

```java
    private final List<BooleanSupplier> closeCommands = new ArrayList<>();
```

After `requestClose()`, add:

```java
    /**
     * The window's Close Window command: handlers newest first; the first that returns true has handled it and
     * the window stays open. Otherwise this is a user's close. One that throws has not handled it.
     *
     * @return true when the window closed
     */
    public boolean closeCommand() {
        if (closed) return true;
        List<BooleanSupplier> handlers = new ArrayList<>(closeCommands);
        java.util.Collections.reverse(handlers);
        for (BooleanSupplier handler : handlers) {
            try { if (handler.getAsBoolean()) return false; }
            catch (RuntimeException failure) { LOG.log(System.Logger.Level.WARNING, "A window close command handler failed", failure); }
        }
        return requestClose();
    }

    public Subscription onCloseCommand(BooleanSupplier handler) {
        closeCommands.add(java.util.Objects.requireNonNull(handler));
        return new Subscription(() -> closeCommands.remove(handler));
    }
```

In `close()`, after `guards.clear();`, add `closeCommands.clear();`.

In `HostedUi.java`, replace line 330:

```java
    private final class OwnedWindow extends Surface implements PluginWindow { OwnedWindow(AuxiliarySurface surface) { super(surface); } }
```

with:

```java
    private final class OwnedWindow extends Surface implements PluginWindow {
        OwnedWindow(AuxiliarySurface surface) { super(surface); }
        @Override public Subscription onCloseCommand(BooleanSupplier handler) {
            requireUi("onCloseCommand");
            return wrap(surface.onCloseCommand(() -> {
                boolean[] handled = {false};
                containment.run(pluginId, "close command", () -> handled[0] = handler.getAsBoolean());
                return handled[0];
            }));
        }
    }
```

In `NativeShells.menuBar`, replace:

```java
            if (!dev.jasper.app.platform.WindowInput.captureShortcut(surface.holder(), close.getAccelerator())) surface.requestClose();
```

with:

```java
            if (!dev.jasper.app.platform.WindowInput.captureShortcut(surface.holder(), close.getAccelerator())) surface.closeCommand();
```

- [ ] **Step 6: Run the tests to verify they pass**

Run: `./gradlew :jasper-sdk:test :jasper-sdk-testkit:test :jasper-app:test --tests '*AuxiliaryWindowsTest' --tests '*AppContractTest'`
Expected: PASS.

- [ ] **Step 7: Document it**

In `docs/sdk-architecture.md`, after the section "## File workflow additions (SDK 0.7.5)" (at the end of the file), add:

```markdown
## Close command, quit guards and the save chooser (SDK 0.8.2)

`PluginWindow.onCloseCommand` lets a plugin window's content handle File → Close Window and
Cmd/Ctrl+W, for example by closing a tab. `NativeShells`' Close Window item calls
`AuxiliarySurface.closeCommand()`, which asks the handlers newest first and otherwise closes like
the close button, through the guards. The close button itself (`windowClosing`) still calls
`requestClose()`. With the macOS screen menu bar, the menu accelerator fires before the content's
key bindings see Cmd+W, so a key binding could not do this.
```

In `docs/plugin-authoring.md`, in the bullet list after the `pluginpanels` example, after the bullet that starts "`WindowSurface.onClosing` guards run when the user closes the window", add:

```markdown
- In SDK 0.8.2+, `window.onCloseCommand(() -> …)` lets your content handle File → Close Window and
  Cmd/Ctrl+W, for example by closing a tab; return true when you handled it and false to let the
  window close. The close button is not affected.
```

- [ ] **Step 8: Run the full check and commit**

Run: `./gradlew check`
Expected: BUILD SUCCESSFUL.

```bash
git add jasper-sdk jasper-sdk-testkit jasper-app docs/sdk-architecture.md docs/plugin-authoring.md
git commit -F - <<'EOF'
feat(sdk): a plugin window's content can handle Close Window (SDK 0.8.2)

PluginWindow.onCloseCommand runs before the closing guards when the user
chooses File → Close Window or presses Cmd/Ctrl+W; the close button is
unchanged. Implemented by the app and the testkit, with a contract test.

🤖 Generated with Claude Code at The Home Depot

Co-Authored-By: Claude <noreply@anthropic.com>
EOF
```

---

### Task 2: Quit and restart ask plugin windows first

**Files:**
- Modify: `jasper-sdk/src/main/java/dev/jasper/sdk/ui/WindowSurface.java` (`onClosing` Javadoc)
- Modify: `jasper-sdk-testkit/src/main/java/dev/jasper/sdk/testing/FakeUi.java`, `FakePluginHost.java`
- Modify: `jasper-sdk-testkit/src/testFixtures/java/dev/jasper/sdk/testing/contract/ContractHarness.java`, `PluginContractTest.java`
- Modify: `jasper-sdk-testkit/src/test/java/dev/jasper/sdk/testing/FakeContractTest.java`, `jasper-app/src/test/java/dev/jasper/app/plugins/AppContractTest.java`
- Modify: `jasper-app/src/main/java/dev/jasper/app/windows/AuxiliarySurface.java`, `AuxiliaryWindows.java`
- Modify: `jasper-app/src/main/java/dev/jasper/app/application/JasperApplication.java` (`restart`, `quit`)
- Test: `jasper-app/src/test/java/dev/jasper/app/windows/AuxiliaryWindowsTest.java`, `jasper-app/src/test/java/dev/jasper/app/application/JasperApplicationPluginsTest.java`
- Docs: `docs/sdk-architecture.md`, `docs/plugin-authoring.md`

**Interfaces:**
- Consumes: Task 1's `AuxiliarySurface.closeCommand()`.
- Produces:
  - `AuxiliarySurface.mayClose() -> boolean` (asks the guards, closes nothing);
  - `AuxiliaryWindows.mayQuit() -> boolean`;
  - `FakePluginHost.requestQuit() -> boolean`;
  - `ContractHarness.requestQuit() -> boolean`.

- [ ] **Step 1: Write the failing tests**

In `PluginContractTest.java`, after the test added in Task 1, add:

```java
    @Test void quitAsksEveryPluginWindowsGuardsAndClosesNothing() {
        var allow = new java.util.concurrent.atomic.AtomicBoolean(false);
        List<String> asked = Collections.synchronizedList(new ArrayList<>());
        h.start(info("test.alpha"), Set.of(), Set.of(), context -> {
            PluginWindow first = context.windows().create(new WindowSpec("test.alpha.first", "First", new Dimension(100, 100), false));
            first.onClosing(() -> { asked.add("first"); return true; });
            first.show();
            PluginWindow second = context.windows().create(new WindowSpec("test.alpha.second", "Second", new Dimension(100, 100), false));
            second.onClosing(() -> { asked.add("second"); return allow.get(); });
            context.windows().dialog(new DialogSpec("Details", second, false)).onClosing(() -> { asked.add("dialog"); return false; });
        });
        h.ui(() -> assertThat(h.requestQuit()).isFalse());
        assertThat(asked).containsExactly("first", "second");
        allow.set(true);
        asked.clear();
        h.ui(() -> assertThat(h.requestQuit()).as("dialogs are not asked").isTrue());
        assertThat(asked).containsExactly("first", "second");
        assertThat(h.windows()).as("asking closes nothing").hasSize(3);
    }
```

In `ContractHarness.java`, after `closeCommand`, add:

```java
    /** UI thread: asks every open plugin window's closing guards as quitting Jasper does, closing nothing; true when the quit may go ahead. */
    boolean requestQuit();
```

In `FakeContractTest.java`, add:

```java
            @Override public boolean requestQuit() { return host.requestQuit(); }
```

In `AppContractTest.java`, add:

```java
            @Override public boolean requestQuit() { return onEdtValue(auxiliary::mayQuit); }
```

In `AuxiliaryWindowsTest.java`, add:

```java
    @Test void quitAsksOnlyWindowGuardsInCreationOrderAndClosesNothing() {
        List<String> asked = new ArrayList<>();
        boolean[] allow = {false};
        AuxiliarySurface first = windows.window("dev.x.first", "First", new Dimension(100, 100), false);
        first.onClosing(() -> { asked.add("first"); return true; });
        AuxiliarySurface second = windows.window("dev.x.second", "Second", new Dimension(100, 100), false);
        second.onClosing(() -> { asked.add("second"); return allow[0]; });
        second.onClosing(() -> { throw new IllegalStateException("boom"); });
        windows.dialog("Details", false, second).onClosing(() -> { asked.add("dialog"); return false; });
        windows.overlay("Connecting", UUID.randomUUID());
        assertThat(windows.mayQuit()).isFalse();
        assertThat(asked).containsExactly("first", "second");
        allow[0] = true;
        asked.clear();
        assertThat(windows.mayQuit()).isTrue();
        assertThat(asked).containsExactly("first", "second");
        assertThat(windows.open()).hasSize(4).noneMatch(AuxiliarySurface::closed);
    }
```

In `JasperApplicationPluginsTest.java`, add the fixture and test:

```java
    private static final String GUARD_FIXTURE = """
        package fix.guard;
        import dev.jasper.sdk.plugin.Plugin;
        import dev.jasper.sdk.plugin.PluginContext;
        import dev.jasper.sdk.ui.WindowSpec;
        import java.awt.Dimension;
        import java.nio.file.Files;
        public final class Main implements Plugin {
            @Override public void start(PluginContext context) {
                var window = context.windows().create(new WindowSpec("dev.example.guard.window", "Guarded", new Dimension(100, 100), false));
                window.onClosing(() -> {
                    try { Files.writeString(context.dataDirectory().resolve("asked"), "yes"); }
                    catch (java.io.IOException failure) { throw new java.io.UncheckedIOException(failure); }
                    return Files.exists(context.dataDirectory().resolve("allow"));
                });
            }
        }
        """;

    @Test void quitAndRestartAskPluginWindowsFirstAndARefusalKeepsJasperRunning() throws Exception {
        AppDirs dirs = new AppDirs(home, home.resolve("config.toml"), home.resolve("logs"));
        Path dev = home.resolve("dev-plugin");
        PluginJars.build(dev, "guard.jar", PluginJars.descriptor("dev.example.guard", "1.0.0", "fix.guard.Main"),
            Map.of("fix.guard.Main", GUARD_FIXTURE), List.of());
        Path data = dirs.plugins().resolve("dev.example.guard/data");
        List<String> spawned = new CopyOnWriteArrayList<>();
        var terminated = new CountDownLatch(1);
        JasperApplication[] application = new JasperApplication[1];
        edt(() -> {
            application[0] = new JasperApplication(null, launcher(new ArrayDeque<>()), new CommandHistory(), null, terminated::countDown);
            application[0].restartPlanner = mode -> Optional.of(List.of("jasper"));
            application[0].spawner = command -> spawned.add(String.join(" ", command));
            application[0].startPlugins(null, dev, false, dirs);
        });
        edt(application[0]::quit);
        edt(() -> assertThat(application[0].restart(RestartMode.SAME)).as("handled: the window said no").isTrue());
        edt(() -> { });
        assertThat(data.resolve("asked")).exists();
        assertThat(terminated.getCount()).as("still running").isEqualTo(1);
        java.nio.file.Files.writeString(data.resolve("allow"), "yes");
        edt(application[0]::quit);
        assertThat(terminated.await(5, TimeUnit.SECONDS)).isTrue();
        assertThat(spawned).as("the refused restart left no relaunch behind").isEmpty();
    }
```

- [ ] **Step 2: Run them to verify they fail**

Run: `./gradlew :jasper-sdk-testkit:test :jasper-app:test --tests '*AuxiliaryWindowsTest' --tests '*AppContractTest' --tests '*JasperApplicationPluginsTest'`
Expected: compilation fails with `cannot find symbol: method requestQuit()` and `mayQuit()`.

- [ ] **Step 3: Implement asking without closing**

In `AuxiliarySurface.java`, replace `requestClose()` with:

```java
    /**
     * Asks every closing guard without closing: quit uses this. One that throws cannot trap the window open.
     *
     * @return whether the window may close
     */
    public boolean mayClose() {
        if (closed) return true;
        for (BooleanSupplier guard : List.copyOf(guards)) {
            try { if (!guard.getAsBoolean()) return false; }
            catch (RuntimeException failure) { LOG.log(System.Logger.Level.WARNING, "A window closing guard failed", failure); }
        }
        return true;
    }

    /** A user's attempt to close: every guard is consulted; one that throws cannot trap the window open. */
    public boolean requestClose() {
        if (closed) return true;
        if (kind == Kind.OVERLAY) return false;
        if (!mayClose()) return false;
        close();
        return true;
    }
```

In `AuxiliaryWindows.java`, after `open()`, add:

```java
    /**
     * Quit and restart ask every open window's closing guards first, in creation order; the first refusal
     * cancels them. Dialogs and overlays are not asked, and nothing closes.
     *
     * @return whether the quit may go ahead
     */
    public boolean mayQuit() {
        for (AuxiliarySurface surface : List.copyOf(open))
            if (surface.kind() == AuxiliarySurface.Kind.WINDOW && !surface.mayClose()) return false;
        return true;
    }
```

In `FakeUi.java`, replace `FakeWindow.requestClose()` with:

```java
        boolean mayClose() {
            for (BooleanSupplier guard : List.copyOf(guards)) {
                try { if (!guard.getAsBoolean()) return false; }
                catch (RuntimeException failure) { host.recordFailure(pluginId() + " closing guard: " + failure); }
            }
            return true;
        }
        boolean requestClose() {
            if (id.equals("overlay")) return false;
            if (!mayClose()) return false;
            close();
            return true;
        }
```

In `FakePluginHost.java`, after `closeCommand`, add:

```java
    /**
     * Asks the closing guards of every open plugin window, as quitting or restarting Jasper does, and closes
     * nothing. Dialogs and overlays are not asked.
     *
     * @return whether the quit may go ahead
     * @since 0.8.2
     */
    public boolean requestQuit() {
        for (FakePluginContext context : contexts.values())
            for (FakeUi.FakeWindow window : List.copyOf(context.ui.windows))
                if (!window.id.equals("dialog") && !window.id.equals("overlay") && !window.mayClose()) return false;
        return true;
    }
```

- [ ] **Step 4: Make quit and restart ask first**

In `JasperApplication.java`, replace the `restart` method body and `quit()`:

```java
    public boolean restart(RestartMode mode) {
        if (quitting || stopped) return true;
        Optional<List<String>> planned = restartPlanner.apply(mode);
        if (planned.isEmpty()) return false;
        // A refusal leaves no relaunch behind: a later ordinary quit must not start a replacement.
        if (!pluginWindowsMayClose()) return true;
        relaunch = planned.get();
        endpointOwner = resident;
        quitConfirmed();
        return true;
    }
```

```java
    /** Asks plugin windows first; any refusal leaves Jasper running with nothing changed. */
    public void quit() {
        if (quitting || stopped) return;
        if (!pluginWindowsMayClose()) return;
        quitConfirmed();
    }

    /** Plugin windows' closing guards, asked before quit or restart changes anything and before the exit deadline is armed. */
    private boolean pluginWindowsMayClose() { return auxiliary == null || auxiliary.mayQuit(); }

    private void quitConfirmed() {
        quitting = true;
        for (TerminalWindow window : List.copyOf(windows)) window.close();
        requestShutdown();
    }
```

- [ ] **Step 5: Update the SDK wording**

In `WindowSurface.java`, replace the `onClosing` Javadoc sentence `Quit does not consult guards.` with:

```
     * Since 0.8.2, quitting or restarting Jasper asks the guards of every open plugin window (not dialogs or
     * overlays) first, and a refusal cancels the quit; nothing closes while they are asked.
```

- [ ] **Step 6: Run the tests to verify they pass**

Run: `./gradlew :jasper-sdk:javadoc :jasper-sdk-testkit:test :jasper-app:test --tests '*AuxiliaryWindowsTest' --tests '*AppContractTest' --tests '*JasperApplicationPluginsTest' --tests '*WindowCommandPaletteTest'`
Expected: PASS. `WindowCommandPaletteTest` calls `quit()` twice and must still pass with the new early return.

- [ ] **Step 7: Document it**

In `docs/sdk-architecture.md`, at the end of the "Close command, quit guards and the save chooser (SDK 0.8.2)" section, add:

```markdown

Quit and restart ask plugin windows first: `JasperApplication.quit()` and `restart()` call
`AuxiliaryWindows.mayQuit()`, which asks every open plugin window's guards in creation order (not
dialogs or overlays) without closing anything, before `quitting` is set and before
`ApplicationShutdown` arms the exit deadline. A refusal leaves Jasper running; a refused restart
records no relaunch. Signals and start-up failures do not go through `quit()` and are not asked.
```

In `docs/plugin-authoring.md`, replace the bullet sentence "Quitting Jasper does not consult them." (in the bullet about `WindowSurface.onClosing`) with:

```
  Since SDK 0.8.2, quitting or restarting Jasper asks the guards of your open windows first, so a guard
  can offer to save; a guard that returns false keeps Jasper running.
```

- [ ] **Step 8: Run the full check and commit**

Run: `./gradlew check`
Expected: BUILD SUCCESSFUL.

```bash
git add jasper-sdk jasper-sdk-testkit jasper-app docs/sdk-architecture.md docs/plugin-authoring.md
git commit -F - <<'EOF'
feat(sdk): quit and restart ask plugin windows' closing guards first

A refusal leaves Jasper running and records no relaunch; nothing closes
while the guards are asked, and the exit deadline is armed only after.

🤖 Generated with Claude Code at The Home Depot

Co-Authored-By: Claude <noreply@anthropic.com>
EOF
```

---

### Task 3: A save-file chooser

**Files:**
- Modify: `jasper-sdk/src/main/java/dev/jasper/sdk/ui/Windows.java`
- Modify: `jasper-sdk-testkit/src/main/java/dev/jasper/sdk/testing/FakeUi.java` (`windows()`), `FakePluginHost.java` (`queuePathSelection` Javadoc)
- Modify: `jasper-app/src/main/java/dev/jasper/app/windows/PathChoice.java`, `NativeShells.java` (`choosePaths`), `jasper-app/src/main/java/dev/jasper/app/plugins/HostedUi.java` (`choosePaths`, `windows()`)
- Test: `jasper-sdk-testkit/src/test/java/dev/jasper/sdk/testing/FakeChooserTest.java`, `jasper-app/src/test/java/dev/jasper/app/plugins/HostedChooserTest.java`
- Docs: `docs/sdk-architecture.md`, `docs/plugin-authoring.md`

**Interfaces:**
- Produces:
  - `Windows.chooseSaveFile(WindowOwner owner, String title, Optional<Path> initial) -> Optional<Path>`;
  - `PathChoice(UUID terminalOwner, AuxiliarySurface auxiliaryOwner, String title, Optional<Path> initial, PathChoice.Mode mode)` with `enum Mode { FILES, DIRECTORY, SAVE }`.

- [ ] **Step 1: Write the failing tests**

In `FakeChooserTest.java`, before `host.queuePathSelection(List.of(Path.of("/a")));` (the line followed by `duringPathSelection`), add:

```java
            host.queuePathSelection(List.of(Path.of("/notes/new.toml")));
            assertThat(context.windows().chooseSaveFile(owner, "Save As", Optional.of(Path.of("/notes/old.toml")))).contains(Path.of("/notes/new.toml"));
            assertThat(context.windows().chooseSaveFile(owner, "Save As", Optional.empty())).as("nothing queued cancels").isEmpty();
```

In `HostedChooserTest.java`, in `terminalOwnerSelectsMultipleFilesOrOneDirectory`, replace `assertThat(requests.getLast().directory()).isTrue();` with:

```java
        assertThat(requests.getLast().mode()).isEqualTo(PathChoice.Mode.DIRECTORY);
        selected = List.of(Path.of("/notes/new.toml"));
        assertThat(ui.windows().chooseSaveFile(owner, "Save As", Optional.of(Path.of("/notes/old.toml")))).contains(Path.of("/notes/new.toml"));
        assertThat(requests.getLast().mode()).isEqualTo(PathChoice.Mode.SAVE);
        assertThat(requests.getLast().initial()).contains(Path.of("/notes/old.toml"));
```

- [ ] **Step 2: Run them to verify they fail**

Run: `./gradlew :jasper-sdk-testkit:test --tests '*FakeChooserTest' :jasper-app:test --tests '*HostedChooserTest'`
Expected: compilation fails with `cannot find symbol: method chooseSaveFile(...)` and `mode()`.

- [ ] **Step 3: Add the SDK method**

In `Windows.java`, after `chooseDirectory`, add:

```java
    /**
     * Asks for a local file to save to, in the platform's save dialog, which confirms replacing an existing
     * file. The same owner, lifetime and UI-thread rules as {@link #chooseFiles} apply.
     * @param owner live terminal window or shown window created by this plugin
     * @param title dialog title
     * @param initial a directory to start in, or a suggested file whose folder and name are filled in; empty for the platform default
     * @return an absolute normalized path, or empty on cancellation or owner closure
     * @throws IllegalArgumentException for a foreign, unshown or dead owner
     * @throws IllegalStateException for a closed context or off-UI call
     * @throws UnsupportedOperationException if the host lacks this picker
     * @since 0.8.2
     */
    default Optional<Path> chooseSaveFile(WindowOwner owner, String title, Optional<Path> initial) {
        throw new UnsupportedOperationException("Save selection is unavailable");
    }
```

- [ ] **Step 4: Implement it in the testkit**

In `FakeUi.java`, inside `windows()`, after the `chooseDirectory` override, add:

```java
            @Override public java.util.Optional<java.nio.file.Path> chooseSaveFile(WindowOwner owner, String title, java.util.Optional<java.nio.file.Path> initial) {
                return choosePaths(owner, title, initial).stream().findFirst();
            }
```

In `FakePluginHost.java`, replace the `queuePathSelection` Javadoc first line `Queues multiple files or one directory; an empty list scripts cancellation.` with `Queues multiple files, one directory or one save target (chooseFiles, chooseDirectory, chooseSaveFile); an empty list scripts cancellation.`

- [ ] **Step 5: Implement it in the application**

Replace `jasper-app/src/main/java/dev/jasper/app/windows/PathChoice.java` with:

```java
package dev.jasper.app.windows;

import java.nio.file.Path;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/** Owner and mode for a local path picker; exactly one owner is present. */
public record PathChoice(UUID terminalOwner, AuxiliarySurface auxiliaryOwner, String title,
                         Optional<Path> initial, Mode mode) {
    /** Existing files, one existing directory, or one file to save to. */
    public enum Mode { FILES, DIRECTORY, SAVE }

    public PathChoice {
        if ((terminalOwner == null) == (auxiliaryOwner == null)) throw new IllegalArgumentException("Exactly one owner is required");
        Objects.requireNonNull(title); Objects.requireNonNull(initial); Objects.requireNonNull(mode);
        initial = initial.map(path -> path.toAbsolutePath().normalize());
    }
}
```

In `HostedUi.java`:
- change the signature `private java.util.List<Path> choosePaths(WindowOwner owner, String title, Optional<Path> initial, boolean directory)` to `private java.util.List<Path> choosePaths(WindowOwner owner, String title, Optional<Path> initial, dev.jasper.app.windows.PathChoice.Mode mode)`;
- in its two `new dev.jasper.app.windows.PathChoice(...)` calls, replace the last argument `directory` with `mode`;
- in `windows()`, replace the two chooser overrides with:

```java
            @Override public java.util.List<Path> chooseFiles(WindowOwner owner, String title, Optional<Path> initial) {
                return choosePaths(owner, title, initial, dev.jasper.app.windows.PathChoice.Mode.FILES);
            }
            @Override public Optional<Path> chooseDirectory(WindowOwner owner, String title, Optional<Path> initial) {
                return choosePaths(owner, title, initial, dev.jasper.app.windows.PathChoice.Mode.DIRECTORY).stream().findFirst();
            }
            @Override public Optional<Path> chooseSaveFile(WindowOwner owner, String title, Optional<Path> initial) {
                return choosePaths(owner, title, initial, dev.jasper.app.windows.PathChoice.Mode.SAVE).stream().findFirst();
            }
```

In `NativeShells.java`, replace the whole `choosePaths` method with:

```java
    /** Owner-based multiple-file, single-directory or save-target selection, without global chooser properties. */
    public java.util.List<Path> choosePaths(PathChoice choice, java.util.function.Consumer<Runnable> cancellation) {
        Window owner = choice.terminalOwner() != null ? terminalWindows.apply(choice.terminalOwner()) : natives.get(choice.auxiliaryOwner());
        if (owner == null || !owner.isShowing()) throw new IllegalArgumentException("Picker requires a shown owner");
        if (choice.mode() != PathChoice.Mode.DIRECTORY) {
            int mode = choice.mode() == PathChoice.Mode.SAVE ? FileDialog.SAVE : FileDialog.LOAD;
            FileDialog chooser = owner instanceof Dialog dialog ? new FileDialog(dialog, choice.title(), mode)
                : new FileDialog((Frame) owner, choice.title(), mode);
            try {
                chooser.setMultipleMode(choice.mode() == PathChoice.Mode.FILES);
                choice.initial().ifPresent(path -> {
                    if (choice.mode() == PathChoice.Mode.SAVE && !java.nio.file.Files.isDirectory(path)) {
                        if (path.getParent() != null) chooser.setDirectory(path.getParent().toString());
                        if (path.getFileName() != null) chooser.setFile(path.getFileName().toString());
                    } else chooser.setDirectory(path.toString());
                });
                cancellation.accept(chooser::dispose);
                chooser.setVisible(true);
                return java.util.Arrays.stream(chooser.getFiles()).map(file -> file.toPath().toAbsolutePath().normalize()).toList();
            } finally { chooser.dispose(); }
        }
        var picker = new javax.swing.JFileChooser();
        picker.setFileSelectionMode(javax.swing.JFileChooser.DIRECTORIES_ONLY);
        picker.setMultiSelectionEnabled(false); picker.setAcceptAllFileFilterUsed(false);
        choice.initial().ifPresent(path -> picker.setCurrentDirectory(path.toFile()));
        var dialog = new JDialog(owner, choice.title(), Dialog.ModalityType.DOCUMENT_MODAL);
        var selected = new java.util.ArrayList<Path>();
        picker.addActionListener(event -> {
            if (javax.swing.JFileChooser.APPROVE_SELECTION.equals(event.getActionCommand()) && picker.getSelectedFile() != null)
                selected.add(picker.getSelectedFile().toPath().toAbsolutePath().normalize());
            dialog.dispose();
        });
        try {
            dialog.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
            dialog.setContentPane(picker); dialog.pack(); dialog.setLocationRelativeTo(owner);
            cancellation.accept(dialog::dispose);
            dialog.setVisible(true);
            return java.util.List.copyOf(selected);
        } finally { dialog.dispose(); }
    }
```

- [ ] **Step 6: Run the tests to verify they pass**

Run: `./gradlew :jasper-sdk:javadoc :jasper-sdk-testkit:test --tests '*FakeChooserTest' :jasper-app:test --tests '*HostedChooserTest'`
Expected: PASS.

- [ ] **Step 7: Document it**

In `docs/sdk-architecture.md`, at the end of the 0.8.2 section, add:

```markdown

`Windows.chooseSaveFile` is the save-target twin of `chooseFiles`: `PathChoice.Mode.SAVE` makes
`NativeShells` open an AWT `FileDialog` in `SAVE` mode, filling in a suggested file's folder and
name; the platform dialog confirms replacing an existing file. The testkit answers it from the same
`queuePathSelection` queue.
```

In `docs/plugin-authoring.md`, at the end of the section "### Local path pickers (SDK 0.7.5)", add:

```markdown
SDK 0.8.2 adds `chooseSaveFile(owner, title, initial)` for a file to save to: pass a suggested file to
fill in its folder and name, or a folder to start in. The platform dialog asks before replacing an
existing file, so you need not ask again.
```

- [ ] **Step 8: Run the full check and commit**

Run: `./gradlew check`
Expected: BUILD SUCCESSFUL.

```bash
git add jasper-sdk jasper-sdk-testkit jasper-app docs/sdk-architecture.md docs/plugin-authoring.md
git commit -F - <<'EOF'
feat(sdk): Windows.chooseSaveFile opens the platform save dialog (SDK 0.8.2)

PathChoice now carries a Mode (files, directory, save) instead of a
directory flag; the testkit answers the save chooser from queuePathSelection.

🤖 Generated with Claude Code at The Home Depot

Co-Authored-By: Claude <noreply@anthropic.com>
EOF
```

---
### Task 4: The Editor module, its API and the text codec

**Files:**
- Create: `plugins/editor/build.gradle.kts`, `plugins/editor/src/main/resources/plugin.toml`, `plugins/editor/src/main/resources/settings.toml`
- Create: `plugins/editor/src/main/java/dev/jasper/editor/api/{Editor,EditorDocument,Content,Revision,TooLargeException,package-info}.java`
- Create: `plugins/editor/src/main/java/dev/jasper/editor/text/{LineSeparator,TextFormat,DecodedText,TextCodec,BinaryFileException,UnencodableException,package-info}.java`
- Test: `plugins/editor/src/test/java/dev/jasper/editor/text/TextCodecTest.java`
- Modify: `jasper-app/src/test/java/dev/jasper/app/plugins/PluginZipsTest.java`

**Interfaces:**
- Produces (used by every later task and by Plan B):
  - `Editor { void open(Path file); void open(EditorDocument document); }`
  - `EditorDocument { String key(); String name(); String location(); Optional<String> host(); CompletableFuture<Content> read(long limitBytes); CompletableFuture<Optional<Revision>> revision(); CompletableFuture<Revision> write(byte[] content); }`
  - `record Content(byte[] bytes, Revision revision)`, `record Revision(long modifiedMillis, long size)`, `TooLargeException(long size, long limit)` with `size()` and `limit()`
  - `enum LineSeparator { LF, CRLF, CR; String chars(); String label(); }`
  - `record TextFormat(Charset charset, boolean bom, LineSeparator separator)` with `TextFormat.DEFAULT` and `label()`
  - `record DecodedText(String text, TextFormat format, boolean mixed)`
  - `TextCodec.decode(byte[]) throws BinaryFileException`, `TextCodec.encode(String, TextFormat) throws UnencodableException`, `TextCodec.indentsWithTabs(String)`
  - `UnencodableException.line()`, `UnencodableException.charset()`

- [ ] **Step 1: Create the module**

`plugins/editor/build.gradle.kts`:

```kotlin
// A plugin compiles against the SDK only; the application supplies it at run time. RSyntaxTextArea is bundled:
// stagePlugins copies the runtime classpath beside the plugin jar, and PluginClassLoader loads it.
plugins { `java-library` }

dependencies {
    compileOnly(project(":jasper-sdk"))
    implementation("com.fifesoft:rsyntaxtextarea:3.6.0")
    testImplementation(project(":jasper-sdk"))
    testImplementation(project(":jasper-sdk-testkit"))
}
```

`plugins/editor/src/main/resources/plugin.toml`:

```toml
id = "dev.jasper.editor"
name = "Editor"
version = "0.1.0"
entry = "dev.jasper.editor.EditorPlugin"
sdk = ">=0.8.2, <0.9"
description = "Edit local and remote text files in tabs, with syntax highlighting."
vendor = "Jasper"
capabilities = []
exports = ["dev.jasper.editor.api"]
```

`plugins/editor/src/main/resources/settings.toml`:

```toml
# Editor settings. Jasper reads this file live; changes apply at once to open tabs.

# The editor font; it must be an installed family. Default: the first installed of
# JetBrains Mono, Menlo, Consolas and DejaVu Sans Mono, else Monospaced.
# font_family = "JetBrains Mono"

# Font size in points, 8 to 48.
# font_size = 13

# Columns per tab stop, 1 to 16.
# tab_size = 4

# Wrap long lines in new tabs.
# soft_wrap = false

# Files larger than this many MiB (1 to 256) are refused.
# max_file_mib = 10
```

- [ ] **Step 2: Write the exported API**

`api/package-info.java`:

```java
/** The Editor's exported API: open a local file, or a document another plugin reads and writes. JDK only. */
package dev.jasper.editor.api;
```

`api/Editor.java`:

```java
package dev.jasper.editor.api;

import java.nio.file.Path;

/** Published by the Editor plugin with {@code Services.publish}. UI thread only. */
public interface Editor {
    /**
     * Opens a local file in a tab, or selects the tab already showing it, and brings the Editor window to the front.
     *
     * @param file a local file
     */
    void open(Path file);

    /**
     * Opens a document another plugin provides, or selects the tab whose document has the same key.
     *
     * @param document the document
     */
    void open(EditorDocument document);
}
```

`api/EditorDocument.java`:

```java
package dev.jasper.editor.api;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;

/**
 * A file the editor reads and writes through whoever provides it. The editor calls these methods on the
 * UI thread; none may block. Futures may complete on any thread.
 */
public interface EditorDocument {
    /** @return stable identity: two documents with equal keys are the same file */
    String key();

    /** @return the file name, used as the tab title */
    String name();

    /** @return the full location for tooltips and the status line, for example {@code dustin@prod-web:/etc/nginx/nginx.conf} */
    String location();

    /** @return the host a remote document lives on, shown dimmed after the name; empty for a local document */
    Optional<String> host();

    /**
     * Reads the whole file. Fails with {@link TooLargeException}, before transferring content, when it is
     * larger than {@code limitBytes}. Cancelling the future aborts the transfer.
     *
     * @param limitBytes the largest size the editor accepts
     * @return the content and the revision it was read at
     */
    CompletableFuture<Content> read(long limitBytes);

    /** @return the file's current revision, or empty when it no longer exists */
    CompletableFuture<Optional<Revision>> revision();

    /**
     * Replaces the file's content: resolve symbolic links to the file they name; write a hidden sibling, give
     * it the target's permissions and atomically replace the target; write in place instead when the sibling
     * cannot be created, would change the owner or group, when the target has more than one hard link, or
     * when an atomic replacement is unavailable; create a missing target; then stat it.
     *
     * @param content the new bytes; the receiver may keep the array
     * @return the new revision
     */
    CompletableFuture<Revision> write(byte[] content);
}
```

`api/Content.java`:

```java
package dev.jasper.editor.api;

import java.util.Objects;

/**
 * A file's bytes and the revision they were read at. The array passes to the receiver; the provider must not reuse it.
 *
 * @param bytes the file's content
 * @param revision when it was read
 */
public record Content(byte[] bytes, Revision revision) {
    /** Rejects nulls. */
    public Content {
        Objects.requireNonNull(bytes, "bytes");
        Objects.requireNonNull(revision, "revision");
    }
}
```

`api/Revision.java`:

```java
package dev.jasper.editor.api;

/**
 * Modification time and size: two reads with equal revisions are treated as the same content.
 *
 * @param modifiedMillis modification time in milliseconds since the epoch
 * @param size size in bytes
 */
public record Revision(long modifiedMillis, long size) { }
```

`api/TooLargeException.java`:

```java
package dev.jasper.editor.api;

import java.io.IOException;

/** The file is larger than the limit the editor asked for. */
public final class TooLargeException extends IOException {
    private final long size;
    private final long limit;

    /**
     * @param size the file's size in bytes
     * @param limit the limit in bytes
     */
    public TooLargeException(long size, long limit) {
        super("The file is " + size + " bytes; the limit is " + limit + " bytes");
        this.size = size;
        this.limit = limit;
    }

    /** @return the file's size in bytes */
    public long size() { return size; }

    /** @return the limit in bytes */
    public long limit() { return limit; }
}
```

- [ ] **Step 3: Write the failing codec tests**

`plugins/editor/src/test/java/dev/jasper/editor/text/TextCodecTest.java`:

```java
package dev.jasper.editor.text;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.*;

class TextCodecTest {
    private static byte[] bytes(int... values) {
        byte[] out = new byte[values.length];
        for (int i = 0; i < values.length; i++) out[i] = (byte) values[i];
        return out;
    }

    private static byte[] concat(byte[] first, byte[] second) {
        byte[] out = new byte[first.length + second.length];
        System.arraycopy(first, 0, out, 0, first.length);
        System.arraycopy(second, 0, out, first.length, second.length);
        return out;
    }

    private static void roundTrips(byte[] original) throws Exception {
        DecodedText decoded = TextCodec.decode(original);
        assertThat(TextCodec.encode(decoded.text(), decoded.format())).isEqualTo(original);
    }

    @Test void utf8WithAndWithoutABomRoundTrips() throws Exception {
        byte[] plain = "name = \"jasper\"\nport = 8080\n".getBytes(UTF_8);
        DecodedText decoded = TextCodec.decode(plain);
        assertThat(decoded.text()).isEqualTo("name = \"jasper\"\nport = 8080\n");
        assertThat(decoded.format()).isEqualTo(new TextFormat(UTF_8, false, LineSeparator.LF));
        assertThat(decoded.mixed()).isFalse();
        roundTrips(plain);
        roundTrips("no trailing newline".getBytes(UTF_8));
        byte[] bom = concat(bytes(0xEF, 0xBB, 0xBF), "é\n".getBytes(UTF_8));
        assertThat(TextCodec.decode(bom).text()).isEqualTo("é\n");
        assertThat(TextCodec.decode(bom).format().label()).isEqualTo("UTF-8 BOM");
        roundTrips(bom);
    }

    @Test void crlfAndCrAreRecordedNormalisedAndWrittenBack() throws Exception {
        byte[] crlf = "a\r\nb\r\n".getBytes(UTF_8);
        assertThat(TextCodec.decode(crlf).text()).isEqualTo("a\nb\n");
        assertThat(TextCodec.decode(crlf).format().separator()).isEqualTo(LineSeparator.CRLF);
        roundTrips(crlf);
        byte[] cr = "a\rb".getBytes(UTF_8);
        assertThat(TextCodec.decode(cr).text()).isEqualTo("a\nb");
        assertThat(TextCodec.decode(cr).format().separator()).isEqualTo(LineSeparator.CR);
        roundTrips(cr);
    }

    @Test void mixedEndingsUseTheMajorityAndSavingConvertsEveryLine() throws Exception {
        DecodedText decoded = TextCodec.decode("a\nb\r\nc\r\nd".getBytes(UTF_8));
        assertThat(decoded.mixed()).isTrue();
        assertThat(decoded.format().separator()).isEqualTo(LineSeparator.CRLF);
        assertThat(new String(TextCodec.encode(decoded.text(), decoded.format()), UTF_8)).isEqualTo("a\r\nb\r\nc\r\nd");
        assertThat(TextCodec.decode("a\nb\r\n".getBytes(UTF_8)).format().separator()).as("a tie prefers LF").isEqualTo(LineSeparator.LF);
    }

    @Test void utf16WithABomRoundTripsInBothByteOrders() throws Exception {
        byte[] little = concat(bytes(0xFF, 0xFE), "héllo\r\n".getBytes(StandardCharsets.UTF_16LE));
        DecodedText decoded = TextCodec.decode(little);
        assertThat(decoded.text()).isEqualTo("héllo\n");
        assertThat(decoded.format().label()).isEqualTo("UTF-16LE BOM");
        roundTrips(little);
        roundTrips(concat(bytes(0xFE, 0xFF), "héllo\n".getBytes(StandardCharsets.UTF_16BE)));
    }

    @Test void invalidUtf8FallsBackToIso88591WhichRoundTripsEveryByte() throws Exception {
        byte[] latin = bytes('c', 'a', 'f', 0xE9, '\n', 0xFF, 0x80);
        DecodedText decoded = TextCodec.decode(latin);
        assertThat(decoded.format().charset()).isEqualTo(StandardCharsets.ISO_8859_1);
        assertThat(decoded.format().label()).isEqualTo("ISO-8859-1");
        assertThat(decoded.text()).startsWith("café\n");
        roundTrips(latin);
    }

    @Test void aNulByteWithoutAUtf16BomIsBinary() {
        assertThatExceptionOfType(BinaryFileException.class).isThrownBy(() -> TextCodec.decode(bytes('P', 'K', 3, 4, 0, 0)));
        assertThatCode(() -> TextCodec.decode(concat(bytes(0xFF, 0xFE), "a".getBytes(StandardCharsets.UTF_16LE)))).doesNotThrowAnyException();
    }

    @Test void anEmptyFileRoundTripsToZeroBytes() throws Exception {
        DecodedText decoded = TextCodec.decode(new byte[0]);
        assertThat(decoded.text()).isEmpty();
        assertThat(decoded.format()).isEqualTo(TextFormat.DEFAULT);
        assertThat(TextCodec.encode("", decoded.format())).isEmpty();
    }

    @Test void textTheCharsetCannotHoldNamesItsLine() {
        var latin = new TextFormat(StandardCharsets.ISO_8859_1, false, LineSeparator.LF);
        assertThatExceptionOfType(UnencodableException.class).isThrownBy(() -> TextCodec.encode("ok\nsmile \uD83D\uDE00\n", latin))
            .satisfies(failure -> {
                assertThat(failure.line()).isEqualTo(2);
                assertThat(failure.charset()).isEqualTo(StandardCharsets.ISO_8859_1);
            });
    }

    @Test void tabIndentationIsDetectedByMajority() {
        assertThat(TextCodec.indentsWithTabs("a:\n\tb\n\tc\n  d\n")).isTrue();
        assertThat(TextCodec.indentsWithTabs("a:\n  b\n  c\n\td\n")).isFalse();
        assertThat(TextCodec.indentsWithTabs("flat\n")).isFalse();
    }
}
```

- [ ] **Step 4: Run them to verify they fail**

Run: `./gradlew :jasper-plugin-editor:test --tests '*TextCodecTest'`
Expected: compilation fails with `cannot find symbol: class TextCodec`.

- [ ] **Step 5: Write the codec**

`text/package-info.java`:

```java
/** Bytes to editable text and back: charset, byte-order mark and line endings. Pure: no I/O, no UI. */
package dev.jasper.editor.text;
```

`text/LineSeparator.java`:

```java
package dev.jasper.editor.text;

/** A line break style. */
public enum LineSeparator {
    LF("\n", "LF"), CRLF("\r\n", "CRLF"), CR("\r", "CR");

    private final String chars;
    private final String label;

    LineSeparator(String chars, String label) { this.chars = chars; this.label = label; }

    /** @return the characters written for a line break */
    public String chars() { return chars; }

    /** @return the status line's label */
    public String label() { return label; }
}
```

`text/TextFormat.java`:

```java
package dev.jasper.editor.text;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Objects;

/**
 * How a file's text is stored.
 *
 * @param charset the charset
 * @param bom whether a byte-order mark starts the file
 * @param separator the line separator written between lines
 */
public record TextFormat(Charset charset, boolean bom, LineSeparator separator) {
    /** UTF-8 without a byte-order mark, with LF: new and empty files. */
    public static final TextFormat DEFAULT = new TextFormat(StandardCharsets.UTF_8, false, LineSeparator.LF);

    /** Rejects nulls. */
    public TextFormat {
        Objects.requireNonNull(charset, "charset");
        Objects.requireNonNull(separator, "separator");
    }

    /** @return the status line's encoding label, such as {@code UTF-8} or {@code UTF-16LE BOM} */
    public String label() { return charset.name() + (bom ? " BOM" : ""); }
}
```

`text/DecodedText.java`:

```java
package dev.jasper.editor.text;

import java.util.Objects;

/**
 * A file's text with {@code \n} line breaks, the format to save it in, and whether the file mixed line endings.
 *
 * @param text the text
 * @param format how to save it
 * @param mixed whether the file had more than one kind of line ending
 */
public record DecodedText(String text, TextFormat format, boolean mixed) {
    /** Rejects nulls. */
    public DecodedText {
        Objects.requireNonNull(text, "text");
        Objects.requireNonNull(format, "format");
    }
}
```

`text/BinaryFileException.java`:

```java
package dev.jasper.editor.text;

import java.io.IOException;

/** The bytes look like a binary file: a NUL byte near the start and no UTF-16 byte-order mark. */
public final class BinaryFileException extends IOException {
    /** Creates the exception. */
    public BinaryFileException() { super("This looks like a binary file"); }
}
```

`text/UnencodableException.java`:

```java
package dev.jasper.editor.text;

import java.io.IOException;
import java.nio.charset.Charset;

/** The text holds a character its charset cannot store. */
public final class UnencodableException extends IOException {
    private final Charset charset;
    private final int line;

    /**
     * @param charset the charset that cannot store it
     * @param line the first line holding such a character, from 1
     */
    public UnencodableException(Charset charset, int line) {
        super("Line " + line + " has a character " + charset.name() + " cannot store");
        this.charset = charset;
        this.line = line;
    }

    /** @return the charset */
    public Charset charset() { return charset; }

    /** @return the first line holding such a character, from 1 */
    public int line() { return line; }
}
```

`text/TextCodec.java`:

```java
package dev.jasper.editor.text;

import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CharsetEncoder;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;

/** Bytes to editable text and back (spec 4.2). Opening a file and saving it unedited writes the same bytes. */
public final class TextCodec {
    /** How much of a file without a UTF-16 byte-order mark is searched for a NUL byte. */
    static final int BINARY_PROBE = 8192;
    private static final byte[] UTF8_BOM = {(byte) 0xEF, (byte) 0xBB, (byte) 0xBF};
    private static final byte[] UTF16LE_BOM = {(byte) 0xFF, (byte) 0xFE};
    private static final byte[] UTF16BE_BOM = {(byte) 0xFE, (byte) 0xFF};

    private TextCodec() { }

    /**
     * Decodes a file: a byte-order mark selects its charset; otherwise strict UTF-8, falling back to ISO-8859-1,
     * which maps every byte. Line endings become {@code \n}; the most common one is recorded.
     *
     * @param bytes the file's content
     * @return the text and how to save it
     * @throws BinaryFileException when a NUL byte appears in the first 8 KiB without a UTF-16 byte-order mark
     */
    public static DecodedText decode(byte[] bytes) throws BinaryFileException {
        Charset charset = StandardCharsets.UTF_8;
        int skip = 0;
        boolean bom = true;
        if (startsWith(bytes, UTF8_BOM)) skip = UTF8_BOM.length;
        else if (startsWith(bytes, UTF16LE_BOM)) { charset = StandardCharsets.UTF_16LE; skip = UTF16LE_BOM.length; }
        else if (startsWith(bytes, UTF16BE_BOM)) { charset = StandardCharsets.UTF_16BE; skip = UTF16BE_BOM.length; }
        else {
            bom = false;
            for (int i = 0; i < Math.min(bytes.length, BINARY_PROBE); i++) if (bytes[i] == 0) throw new BinaryFileException();
        }
        String raw;
        try {
            raw = charset.newDecoder().onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT)
                .decode(ByteBuffer.wrap(bytes, skip, bytes.length - skip)).toString();
        } catch (CharacterCodingException malformed) {
            charset = StandardCharsets.ISO_8859_1;
            bom = false;
            raw = new String(bytes, StandardCharsets.ISO_8859_1);
        }
        int lf = 0, crlf = 0, cr = 0;
        for (int i = 0; i < raw.length(); i++) {
            char c = raw.charAt(i);
            if (c == '\n') lf++;
            else if (c == '\r') {
                if (i + 1 < raw.length() && raw.charAt(i + 1) == '\n') { crlf++; i++; }
                else cr++;
            }
        }
        LineSeparator separator = crlf > lf && crlf >= cr ? LineSeparator.CRLF
            : cr > lf && cr > crlf ? LineSeparator.CR : LineSeparator.LF;
        boolean mixed = (lf > 0 ? 1 : 0) + (crlf > 0 ? 1 : 0) + (cr > 0 ? 1 : 0) > 1;
        String text = raw.replace("\r\n", "\n").replace('\r', '\n');
        return new DecodedText(text, new TextFormat(charset, bom, separator), mixed);
    }

    /**
     * Encodes text with {@code \n} line breaks in a format, adding its byte-order mark.
     *
     * @param text the text
     * @param format how to store it
     * @return the file's bytes
     * @throws UnencodableException when the charset cannot store a character; it names the first such line
     */
    public static byte[] encode(String text, TextFormat format) throws UnencodableException {
        String stored = format.separator() == LineSeparator.LF ? text : text.replace("\n", format.separator().chars());
        CharsetEncoder encoder = format.charset().newEncoder()
            .onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT);
        ByteBuffer body;
        try { body = encoder.encode(CharBuffer.wrap(stored)); }
        catch (CharacterCodingException failure) {
            throw new UnencodableException(format.charset(), firstUnencodableLine(text, format.charset()));
        }
        byte[] mark = !format.bom() ? new byte[0]
            : format.charset().equals(StandardCharsets.UTF_16LE) ? UTF16LE_BOM
            : format.charset().equals(StandardCharsets.UTF_16BE) ? UTF16BE_BOM
            : format.charset().equals(StandardCharsets.UTF_8) ? UTF8_BOM : new byte[0];
        int length = body.remaining();
        byte[] out = new byte[mark.length + length];
        System.arraycopy(mark, 0, out, 0, mark.length);
        body.get(out, mark.length, length);
        return out;
    }

    /**
     * @param text text with {@code \n} line breaks
     * @return whether more indented lines start with a tab than with a space
     */
    public static boolean indentsWithTabs(String text) {
        int tabs = 0, spaces = 0;
        for (String line : text.split("\n", -1)) {
            if (line.startsWith("\t")) tabs++;
            else if (line.startsWith(" ")) spaces++;
        }
        return tabs > spaces;
    }

    static int firstUnencodableLine(String text, Charset charset) {
        CharsetEncoder encoder = charset.newEncoder();
        int line = 1;
        for (int i = 0; i < text.length(); ) {
            int point = text.codePointAt(i);
            if (point == '\n') line++;
            else if (!encoder.canEncode(new String(Character.toChars(point)))) return line;
            i += Character.charCount(point);
        }
        return line;
    }

    private static boolean startsWith(byte[] bytes, byte[] prefix) {
        if (bytes.length < prefix.length) return false;
        for (int i = 0; i < prefix.length; i++) if (bytes[i] != prefix[i]) return false;
        return true;
    }
}
```

- [ ] **Step 6: Run the codec tests to verify they pass**

Run: `./gradlew :jasper-plugin-editor:test --tests '*TextCodecTest'`
Expected: PASS, 9 tests.

- [ ] **Step 7: Count the new plugin zip**

In `PluginZipsTest.java`:
- change `assertThat(zips).as("one zip per plugins/ directory").hasSize(5);` to `hasSize(6)`;
- before the final `else assertThat(jars)...` branch, add:

```java
            else if (descriptor.id().equals("dev.jasper.editor"))
                assertThat(jars).as("RSyntaxTextArea travels with the editor").contains("rsyntaxtextarea-3.6.0.jar");
```

- change the final assertion to:

```java
        assertThat(ids).containsExactlyInAnyOrder("dev.jasper.sample", "dev.jasper.snippets", "dev.jasper.history", "dev.jasper.vault", "dev.jasper.remote", "dev.jasper.editor");
```

- [ ] **Step 8: Run the full check and commit**

Run: `./gradlew check`
Expected: BUILD SUCCESSFUL; `verifyPluginArchitecture` accepts the new module.

```bash
git add plugins/editor jasper-app/src/test/java/dev/jasper/app/plugins/PluginZipsTest.java
git commit -F - <<'EOF'
feat(editor): the Editor module, its exported API and the text codec

dev.jasper.editor.api is what Remote will call; TextCodec decodes BOMs,
UTF-8 with an ISO-8859-1 fallback and line endings, and encodes back to
the same bytes.

🤖 Generated with Claude Code at The Home Depot

Co-Authored-By: Claude <noreply@anthropic.com>
EOF
```

---

### Task 5: Languages, detection and the TOML lexer

**Files:**
- Create: `plugins/editor/src/main/java/dev/jasper/editor/syntax/{Languages,SyntaxDetector,TomlTokenMaker,package-info}.java`
- Test: `plugins/editor/src/test/java/dev/jasper/editor/syntax/{LanguagesTest,SyntaxDetectorTest,TomlTokenMakerTest}.java`

**Interfaces:**
- Produces:
  - `Languages.TOML = "text/toml"`, `record Languages.Language(String style, String name)`, `Languages.register()`, `Languages.all() -> List<Language>`, `Languages.name(String style) -> String`;
  - `SyntaxDetector.detect(String fileName, String firstLine) -> String`;
  - `TomlTokenMaker` (public, no-argument constructor).

- [ ] **Step 1: Write the failing tests**

`syntax/TomlTokenMakerTest.java`:

```java
package dev.jasper.editor.syntax;

import java.util.ArrayList;
import java.util.List;
import javax.swing.text.Segment;
import org.fife.ui.rsyntaxtextarea.Token;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class TomlTokenMakerTest {
    private static List<String> tokens(String line, int initial) {
        char[] chars = line.toCharArray();
        List<String> out = new ArrayList<>();
        for (Token token = new TomlTokenMaker().getTokenList(new Segment(chars, 0, chars.length), initial, 0);
             token != null && token.isPaintable(); token = token.getNextToken())
            if (token.getType() != Token.WHITESPACE && token.length() > 0) out.add(name(token.getType()) + ":" + token.getLexeme());
        return out;
    }

    private static List<String> tokens(String line) { return tokens(line, Token.NULL); }

    private static int endState(String line, int initial) {
        char[] chars = line.toCharArray();
        return new TomlTokenMaker().getLastTokenTypeOnLine(new Segment(chars, 0, chars.length), initial);
    }

    private static String name(int type) {
        return switch (type) {
            case Token.COMMENT_EOL -> "COMMENT";
            case Token.RESERVED_WORD -> "HEADER";
            case Token.VARIABLE -> "KEY";
            case Token.LITERAL_STRING_DOUBLE_QUOTE -> "STRING";
            case Token.LITERAL_CHAR -> "LITERAL";
            case Token.LITERAL_NUMBER_DECIMAL_INT -> "INT";
            case Token.LITERAL_NUMBER_HEXADECIMAL -> "RADIX";
            case Token.LITERAL_NUMBER_FLOAT -> "FLOAT";
            case Token.LITERAL_BOOLEAN -> "BOOL";
            case Token.DATA_TYPE -> "DATE";
            case Token.OPERATOR -> "OP";
            case Token.SEPARATOR -> "SEP";
            case Token.ERROR_IDENTIFIER -> "ERROR";
            default -> String.valueOf(type);
        };
    }

    @Test void commentsAndTableHeaders() {
        assertThat(tokens("# note")).containsExactly("COMMENT:# note");
        assertThat(tokens("[server.http]  # main")).containsExactly("HEADER:[server.http]", "COMMENT:# main");
        assertThat(tokens("  [[bin]]")).containsExactly("HEADER:[[bin]]");
    }

    @Test void bareQuotedAndDottedKeys() {
        assertThat(tokens("name = \"jasper\"")).containsExactly("KEY:name", "OP:=", "STRING:\"jasper\"");
        assertThat(tokens("site.\"google.com\".port = 8080"))
            .containsExactly("KEY:site", "OP:.", "KEY:\"google.com\"", "OP:.", "KEY:port", "OP:=", "INT:8080");
        assertThat(tokens("path = 'C:\\Users'")).containsExactly("KEY:path", "OP:=", "LITERAL:'C:\\Users'");
    }

    @Test void numbersBooleansAndDates() {
        assertThat(tokens("hex = 0xDEAD_BEEF")).containsExactly("KEY:hex", "OP:=", "RADIX:0xDEAD_BEEF");
        assertThat(tokens("mode = 0o755")).endsWith("RADIX:0o755");
        assertThat(tokens("mask = 0b1010")).endsWith("RADIX:0b1010");
        assertThat(tokens("big = 1_000")).endsWith("INT:1_000");
        assertThat(tokens("pi = 3.14e-2")).endsWith("FLOAT:3.14e-2");
        assertThat(tokens("low = -inf")).endsWith("FLOAT:-inf");
        assertThat(tokens("odd = nan")).endsWith("FLOAT:nan");
        assertThat(tokens("on = true")).endsWith("BOOL:true");
        assertThat(tokens("when = 1979-05-27T07:32:00Z")).endsWith("DATE:1979-05-27T07:32:00Z");
        assertThat(tokens("local = 1979-05-27 07:32:00")).endsWith("DATE:1979-05-27 07:32:00");
        assertThat(tokens("day = 1979-05-27")).endsWith("DATE:1979-05-27");
        assertThat(tokens("time = 07:32:00.999")).endsWith("DATE:07:32:00.999");
        assertThat(tokens("a = 1 junk")).endsWith("INT:1", "ERROR:junk");
    }

    @Test void arraysAndInlineTables() {
        assertThat(tokens("ports = [ 8080, 8081 ]"))
            .containsExactly("KEY:ports", "OP:=", "SEP:[", "INT:8080", "SEP:,", "INT:8081", "SEP:]");
        assertThat(tokens("point = { x = 1, y = \"two\" }")).containsExactly(
            "KEY:point", "OP:=", "SEP:{", "KEY:x", "OP:=", "INT:1", "SEP:,", "KEY:y", "OP:=", "STRING:\"two\"", "SEP:}");
    }

    @Test void multiLineBasicStringsCarryAcrossLines() {
        assertThat(tokens("text = \"\"\"first")).containsExactly("KEY:text", "OP:=", "STRING:\"\"\"first");
        assertThat(endState("text = \"\"\"first", Token.NULL)).isEqualTo(Token.LITERAL_STRING_DOUBLE_QUOTE);
        assertThat(tokens("still \"inside\"", Token.LITERAL_STRING_DOUBLE_QUOTE)).containsExactly("STRING:still \"inside\"");
        assertThat(endState("", Token.LITERAL_STRING_DOUBLE_QUOTE)).as("an empty line stays inside").isEqualTo(Token.LITERAL_STRING_DOUBLE_QUOTE);
        assertThat(tokens("end\"\"\" # done", Token.LITERAL_STRING_DOUBLE_QUOTE)).containsExactly("STRING:end\"\"\"", "COMMENT:# done");
        assertThat(endState("end\"\"\" # done", Token.LITERAL_STRING_DOUBLE_QUOTE)).isEqualTo(Token.NULL);
        assertThat(tokens("a = \"\"\"x \\\"\"\" y\"\"\"")).as("an escaped quote does not close").endsWith("STRING:\"\"\"x \\\"\"\" y\"\"\"");
        assertThat(tokens("q = \"\"\"a\"\"\"\"")).as("a quote before the delimiter").endsWith("STRING:\"\"\"a\"\"\"\"");
    }

    @Test void multiLineLiteralStringsCarryAcrossLines() {
        assertThat(endState("raw = '''one", Token.NULL)).isEqualTo(Token.LITERAL_CHAR);
        assertThat(tokens("two'''", Token.LITERAL_CHAR)).containsExactly("LITERAL:two'''");
        assertThat(endState("two'''", Token.LITERAL_CHAR)).isEqualTo(Token.NULL);
    }

    @Test void arraysOpenedOnAnEarlierLineKeepValueContext() {
        assertThat(endState("ports = [", Token.NULL)).isEqualTo(Token.SEPARATOR);
        assertThat(tokens("  8080,", Token.SEPARATOR)).containsExactly("INT:8080", "SEP:,");
        assertThat(endState("  8080,", Token.SEPARATOR)).isEqualTo(Token.SEPARATOR);
        assertThat(tokens("  [1, 2],", Token.SEPARATOR)).as("not a table header inside an array")
            .containsExactly("SEP:[", "INT:1", "SEP:,", "INT:2", "SEP:]", "SEP:,");
        assertThat(endState("]", Token.SEPARATOR)).isEqualTo(Token.NULL);
    }

    @Test void theCommentDelimiterMakesToggleCommentWork() {
        assertThat(new TomlTokenMaker().getLineCommentStartAndEnd(0)).containsExactly("#", null);
    }
}
```

`syntax/SyntaxDetectorTest.java`:

```java
package dev.jasper.editor.syntax;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import static org.assertj.core.api.Assertions.assertThat;
import static org.fife.ui.rsyntaxtextarea.SyntaxConstants.*;

class SyntaxDetectorTest {
    @ParameterizedTest
    @CsvSource({
        "config.toml,text/toml", "Cargo.lock,text/toml", "Pipfile,text/toml", "poetry.lock,text/toml",
        "package.json,text/json", "settings.jsonc,text/jshintrc",
        "app.py,text/python", "types.pyi,text/python", "gui.pyw,text/python",
        "deploy.sh,text/unix", "x.bash,text/unix", "x.zsh,text/unix", ".bashrc,text/unix", ".bash_profile,text/unix",
        ".profile,text/unix", ".zshrc,text/unix",
        "ci.yml,text/yaml", "ci.yaml,text/yaml",
        "main.go,text/golang", "Dockerfile,text/dockerfile", "Makefile,text/makefile",
        "nginx.conf,text/plain", "README,text/plain"
    })
    void namesAndExtensions(String name, String style) {
        assertThat(SyntaxDetector.detect(name, "")).isEqualTo(style);
    }

    @Test void aShebangDecidesOnlyWhenTheNameHasNoRecognisedExtension() {
        assertThat(SyntaxDetector.detect("deploy", "#!/usr/bin/env python3")).isEqualTo(SYNTAX_STYLE_PYTHON);
        assertThat(SyntaxDetector.detect("run", "#!/bin/bash -e")).isEqualTo(SYNTAX_STYLE_UNIX_SHELL);
        assertThat(SyntaxDetector.detect("run", "#!/usr/bin/env -S zsh -f")).isEqualTo(SYNTAX_STYLE_UNIX_SHELL);
        assertThat(SyntaxDetector.detect("tool.py", "#!/bin/sh")).as("the extension wins").isEqualTo(SYNTAX_STYLE_PYTHON);
        assertThat(SyntaxDetector.detect("notes.txt", "#!/usr/bin/python")).isEqualTo(SYNTAX_STYLE_PYTHON);
        assertThat(SyntaxDetector.detect("run", "#!/usr/bin/perl")).isEqualTo(SYNTAX_STYLE_NONE);
        assertThat(SyntaxDetector.detect("run", "echo hi")).isEqualTo(SYNTAX_STYLE_NONE);
    }
}
```

`syntax/LanguagesTest.java`:

```java
package dev.jasper.editor.syntax;

import org.fife.ui.rsyntaxtextarea.RSyntaxTextArea;
import org.fife.ui.rsyntaxtextarea.Token;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;
import static org.fife.ui.rsyntaxtextarea.SyntaxConstants.SYNTAX_STYLE_NONE;

class LanguagesTest {
    @Test void everyFactoryStyleIsListedPlainTextFirstWithTomlAdded() {
        var all = Languages.all();
        assertThat(all.getFirst()).isEqualTo(new Languages.Language(SYNTAX_STYLE_NONE, "Plain Text"));
        assertThat(all).extracting(Languages.Language::name).contains("TOML", "JSON", "Python", "Shell (Bash)", "YAML");
        assertThat(Languages.name("text/unheard-of")).isEqualTo("unheard-of");
    }

    @Test void theTomlLexerHighlightsInARealTextArea() {
        Languages.register();
        var area = new RSyntaxTextArea();
        area.setSyntaxEditingStyle(Languages.TOML);
        area.setText("port = 8080\n");
        Token first = area.getTokenListForLine(0);
        assertThat(first.getType()).isEqualTo(Token.VARIABLE);
        assertThat(first.getLexeme()).isEqualTo("port");
    }
}
```

- [ ] **Step 2: Run them to verify they fail**

Run: `./gradlew :jasper-plugin-editor:test --tests 'dev.jasper.editor.syntax.*'`
Expected: compilation fails with `cannot find symbol: class TomlTokenMaker`.

- [ ] **Step 3: Write the syntax package**

`syntax/package-info.java`:

```java
/** Languages: RSyntaxTextArea's styles and names, detection from a file name or shebang, and the TOML lexer. */
package dev.jasper.editor.syntax;
```

`syntax/Languages.java`:

```java
package dev.jasper.editor.syntax;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.fife.ui.rsyntaxtextarea.AbstractTokenMakerFactory;
import org.fife.ui.rsyntaxtextarea.TokenMakerFactory;
import static java.util.Map.entry;
import static org.fife.ui.rsyntaxtextarea.SyntaxConstants.*;

/** The languages the editor can highlight: RSyntaxTextArea's styles plus the Editor's own TOML. */
public final class Languages {
    /** The TOML style key, served by {@link TomlTokenMaker}. */
    public static final String TOML = "text/toml";

    /**
     * One entry in the language picker.
     *
     * @param style RSyntaxTextArea's style key
     * @param name the display name
     */
    public record Language(String style, String name) { }

    private static final Map<String, String> NAMES = Map.ofEntries(
        entry(SYNTAX_STYLE_NONE, "Plain Text"), entry(SYNTAX_STYLE_ACTIONSCRIPT, "ActionScript"),
        entry(SYNTAX_STYLE_ASSEMBLER_X86, "Assembly (x86)"), entry(SYNTAX_STYLE_ASSEMBLER_6502, "Assembly (6502)"),
        entry(SYNTAX_STYLE_BBCODE, "BBCode"), entry(SYNTAX_STYLE_C, "C"), entry(SYNTAX_STYLE_CLOJURE, "Clojure"),
        entry(SYNTAX_STYLE_CPLUSPLUS, "C++"), entry(SYNTAX_STYLE_CSHARP, "C#"), entry(SYNTAX_STYLE_CSS, "CSS"),
        entry(SYNTAX_STYLE_CSV, "CSV"), entry(SYNTAX_STYLE_D, "D"), entry(SYNTAX_STYLE_DOCKERFILE, "Dockerfile"),
        entry(SYNTAX_STYLE_DART, "Dart"), entry(SYNTAX_STYLE_DELPHI, "Delphi"), entry(SYNTAX_STYLE_DTD, "DTD"),
        entry(SYNTAX_STYLE_FORTRAN, "Fortran"), entry(SYNTAX_STYLE_GO, "Go"), entry(SYNTAX_STYLE_GROOVY, "Groovy"),
        entry(SYNTAX_STYLE_HANDLEBARS, "Handlebars"), entry(SYNTAX_STYLE_HOSTS, "Hosts"), entry(SYNTAX_STYLE_HTACCESS, ".htaccess"),
        entry(SYNTAX_STYLE_HTML, "HTML"), entry(SYNTAX_STYLE_INI, "INI"), entry(SYNTAX_STYLE_JAVA, "Java"),
        entry(SYNTAX_STYLE_JAVASCRIPT, "JavaScript"), entry(SYNTAX_STYLE_JSON, "JSON"),
        entry(SYNTAX_STYLE_JSON_WITH_COMMENTS, "JSON with Comments"), entry(SYNTAX_STYLE_JSP, "JSP"),
        entry(SYNTAX_STYLE_KOTLIN, "Kotlin"), entry(SYNTAX_STYLE_LATEX, "LaTeX"), entry(SYNTAX_STYLE_LESS, "Less"),
        entry(SYNTAX_STYLE_LISP, "Lisp"), entry(SYNTAX_STYLE_LUA, "Lua"), entry(SYNTAX_STYLE_MAKEFILE, "Makefile"),
        entry(SYNTAX_STYLE_MARKDOWN, "Markdown"), entry(SYNTAX_STYLE_MXML, "MXML"), entry(SYNTAX_STYLE_NSIS, "NSIS"),
        entry(SYNTAX_STYLE_PERL, "Perl"), entry(SYNTAX_STYLE_PHP, "PHP"), entry(SYNTAX_STYLE_PROTO, "Protocol Buffers"),
        entry(SYNTAX_STYLE_PROPERTIES_FILE, "Properties"), entry(SYNTAX_STYLE_PYTHON, "Python"), entry(SYNTAX_STYLE_RUBY, "Ruby"),
        entry(SYNTAX_STYLE_RUST, "Rust"), entry(SYNTAX_STYLE_SAS, "SAS"), entry(SYNTAX_STYLE_SCALA, "Scala"),
        entry(SYNTAX_STYLE_SQL, "SQL"), entry(SYNTAX_STYLE_TCL, "Tcl"), entry(SYNTAX_STYLE_TYPESCRIPT, "TypeScript"),
        entry(SYNTAX_STYLE_UNIX_SHELL, "Shell (Bash)"), entry(SYNTAX_STYLE_VISUAL_BASIC, "Visual Basic"),
        entry(SYNTAX_STYLE_VHDL, "VHDL"), entry(SYNTAX_STYLE_WINDOWS_BATCH, "Windows Batch"), entry(SYNTAX_STYLE_XML, "XML"),
        entry(SYNTAX_STYLE_YAML, "YAML"), entry(TOML, "TOML"));

    private static boolean registered;

    private Languages() { }

    /** Registers the TOML lexer with RSyntaxTextArea's default factory; later calls do nothing. */
    public static synchronized void register() {
        if (registered) return;
        // RSyntaxTextArea creates lexers by class name, so it needs the plugin's own class loader.
        ((AbstractTokenMakerFactory) TokenMakerFactory.getDefaultInstance())
            .putMapping(TOML, TomlTokenMaker.class.getName(), TomlTokenMaker.class.getClassLoader());
        registered = true;
    }

    /** @return every language the factory knows, plain text first, then by name */
    public static List<Language> all() {
        register();
        return TokenMakerFactory.getDefaultInstance().keySet().stream().map(style -> new Language(style, name(style)))
            .sorted(Comparator.comparing((Language language) -> !language.style().equals(SYNTAX_STYLE_NONE))
                .thenComparing(language -> language.name().toLowerCase(Locale.ROOT)))
            .toList();
    }

    /**
     * @param style a style key
     * @return its display name, or the key's subtype for a style without one
     */
    public static String name(String style) {
        return NAMES.getOrDefault(style, style.substring(style.indexOf('/') + 1));
    }
}
```

`syntax/SyntaxDetector.java`:

```java
package dev.jasper.editor.syntax;

import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import static java.util.Map.entry;
import static org.fife.ui.rsyntaxtextarea.SyntaxConstants.*;

/**
 * Chooses a language from a file name and its first line (spec 3.1). This is the only place a language is
 * decided, which is where a TextMate lexer would plug in.
 */
public final class SyntaxDetector {
    private static final Map<String, String> NAMES = Map.ofEntries(
        entry("cargo.lock", Languages.TOML), entry("pipfile", Languages.TOML), entry("poetry.lock", Languages.TOML),
        entry(".bashrc", SYNTAX_STYLE_UNIX_SHELL), entry(".bash_profile", SYNTAX_STYLE_UNIX_SHELL),
        entry(".profile", SYNTAX_STYLE_UNIX_SHELL), entry(".zshrc", SYNTAX_STYLE_UNIX_SHELL),
        entry("dockerfile", SYNTAX_STYLE_DOCKERFILE), entry("makefile", SYNTAX_STYLE_MAKEFILE),
        entry("gnumakefile", SYNTAX_STYLE_MAKEFILE), entry(".htaccess", SYNTAX_STYLE_HTACCESS), entry("hosts", SYNTAX_STYLE_HOSTS));

    private static final Map<String, String> EXTENSIONS = Map.ofEntries(
        entry("toml", Languages.TOML), entry("json", SYNTAX_STYLE_JSON), entry("jsonc", SYNTAX_STYLE_JSON_WITH_COMMENTS),
        entry("py", SYNTAX_STYLE_PYTHON), entry("pyi", SYNTAX_STYLE_PYTHON), entry("pyw", SYNTAX_STYLE_PYTHON),
        entry("sh", SYNTAX_STYLE_UNIX_SHELL), entry("bash", SYNTAX_STYLE_UNIX_SHELL), entry("zsh", SYNTAX_STYLE_UNIX_SHELL),
        entry("yml", SYNTAX_STYLE_YAML), entry("yaml", SYNTAX_STYLE_YAML),
        entry("go", SYNTAX_STYLE_GO), entry("rs", SYNTAX_STYLE_RUST), entry("js", SYNTAX_STYLE_JAVASCRIPT),
        entry("mjs", SYNTAX_STYLE_JAVASCRIPT), entry("cjs", SYNTAX_STYLE_JAVASCRIPT), entry("ts", SYNTAX_STYLE_TYPESCRIPT),
        entry("java", SYNTAX_STYLE_JAVA), entry("kt", SYNTAX_STYLE_KOTLIN), entry("kts", SYNTAX_STYLE_KOTLIN),
        entry("groovy", SYNTAX_STYLE_GROOVY), entry("gradle", SYNTAX_STYLE_GROOVY), entry("scala", SYNTAX_STYLE_SCALA),
        entry("c", SYNTAX_STYLE_C), entry("h", SYNTAX_STYLE_C), entry("cpp", SYNTAX_STYLE_CPLUSPLUS), entry("cc", SYNTAX_STYLE_CPLUSPLUS),
        entry("cxx", SYNTAX_STYLE_CPLUSPLUS), entry("hpp", SYNTAX_STYLE_CPLUSPLUS), entry("cs", SYNTAX_STYLE_CSHARP),
        entry("css", SYNTAX_STYLE_CSS), entry("less", SYNTAX_STYLE_LESS), entry("html", SYNTAX_STYLE_HTML), entry("htm", SYNTAX_STYLE_HTML),
        entry("xml", SYNTAX_STYLE_XML), entry("xsd", SYNTAX_STYLE_XML), entry("svg", SYNTAX_STYLE_XML), entry("plist", SYNTAX_STYLE_XML),
        entry("sql", SYNTAX_STYLE_SQL), entry("md", SYNTAX_STYLE_MARKDOWN), entry("markdown", SYNTAX_STYLE_MARKDOWN),
        entry("rb", SYNTAX_STYLE_RUBY), entry("pl", SYNTAX_STYLE_PERL), entry("pm", SYNTAX_STYLE_PERL), entry("php", SYNTAX_STYLE_PHP),
        entry("lua", SYNTAX_STYLE_LUA), entry("dart", SYNTAX_STYLE_DART), entry("proto", SYNTAX_STYLE_PROTO),
        entry("properties", SYNTAX_STYLE_PROPERTIES_FILE), entry("ini", SYNTAX_STYLE_INI), entry("bat", SYNTAX_STYLE_WINDOWS_BATCH),
        entry("cmd", SYNTAX_STYLE_WINDOWS_BATCH), entry("csv", SYNTAX_STYLE_CSV), entry("tcl", SYNTAX_STYLE_TCL),
        entry("vb", SYNTAX_STYLE_VISUAL_BASIC), entry("tex", SYNTAX_STYLE_LATEX), entry("clj", SYNTAX_STYLE_CLOJURE),
        entry("dtd", SYNTAX_STYLE_DTD), entry("jsp", SYNTAX_STYLE_JSP), entry("hbs", SYNTAX_STYLE_HANDLEBARS));

    private static final Set<String> SHELLS = Set.of("sh", "bash", "zsh", "dash", "ksh");

    private SyntaxDetector() { }

    /**
     * @param fileName the file's name, without folders
     * @param firstLine the file's first line, or empty
     * @return a style key; plain text when nothing matches
     */
    public static String detect(String fileName, String firstLine) {
        String name = fileName.toLowerCase(Locale.ROOT);
        String byName = NAMES.get(name);
        if (byName != null) return byName;
        int dot = name.lastIndexOf('.');
        if (dot > 0 && dot < name.length() - 1) {
            String byExtension = EXTENSIONS.get(name.substring(dot + 1));
            if (byExtension != null) return byExtension;
        }
        return shebang(firstLine).orElse(SYNTAX_STYLE_NONE);
    }

    private static Optional<String> shebang(String line) {
        if (!line.startsWith("#!")) return Optional.empty();
        String[] words = line.substring(2).trim().split("\\s+");
        String program = words[0].substring(words[0].lastIndexOf('/') + 1);
        if (program.equals("env")) {
            program = "";
            for (int i = 1; i < words.length; i++) if (!words[i].startsWith("-")) { program = words[i]; break; }
        }
        if (program.matches("python[0-9.]*")) return Optional.of(SYNTAX_STYLE_PYTHON);
        if (SHELLS.contains(program)) return Optional.of(SYNTAX_STYLE_UNIX_SHELL);
        return Optional.empty();
    }
}
```

`syntax/TomlTokenMaker.java`:

```java
package dev.jasper.editor.syntax;

import java.util.regex.Pattern;
import javax.swing.text.Segment;
import org.fife.ui.rsyntaxtextarea.Token;
import org.fife.ui.rsyntaxtextarea.TokenMakerBase;

/**
 * A hand-written TOML lexer (spec 3.2). A line ending inside {@code """} ends with a basic-string token, one
 * inside {@code '''} with a literal-string token, and one inside an array opened on an earlier line with a
 * zero-length separator; RSyntaxTextArea hands that type back as the next line's initial type.
 */
public final class TomlTokenMaker extends TokenMakerBase {
    private static final Pattern DECIMAL = Pattern.compile("[+-]?(0|[1-9](_?\\d)*)");
    private static final Pattern FLOAT = Pattern.compile("[+-]?(0|[1-9](_?\\d)*)(\\.\\d(_?\\d)*)?([eE][+-]?\\d(_?\\d)*)?|[+-]?(inf|nan)");
    private static final Pattern RADIX = Pattern.compile("0x[0-9A-Fa-f](_?[0-9A-Fa-f])*|0o[0-7](_?[0-7])*|0b[01](_?[01])*");
    private static final Pattern DATE_TIME = Pattern.compile(
        "\\d{4}-\\d{2}-\\d{2}([Tt ]\\d{2}:\\d{2}(:\\d{2}(\\.\\d+)?)?([Zz]|[+-]\\d{2}:\\d{2})?)?|\\d{2}:\\d{2}(:\\d{2}(\\.\\d+)?)?");
    private static final Pattern LOCAL_DATE = Pattern.compile("\\d{4}-\\d{2}-\\d{2}");

    @Override public String[] getLineCommentStartAndEnd(int languageIndex) { return new String[] {"#", null}; }

    @Override public boolean getMarkOccurrencesOfTokenType(int type) { return type == Token.VARIABLE; }

    @Override
    public Token getTokenList(Segment text, int initialTokenType, int startOffset) {
        resetTokenList();
        char[] a = text.array;
        int from = text.offset, end = text.offset + text.count, shift = startOffset - text.offset;
        int i = from;
        StringBuilder brackets = new StringBuilder();
        boolean value = false, seen = false;
        if (initialTokenType == Token.SEPARATOR) { brackets.append('['); value = true; }
        else if (initialTokenType == Token.LITERAL_STRING_DOUBLE_QUOTE || initialTokenType == Token.LITERAL_CHAR) {
            int close = closeMultiline(a, from, end, initialTokenType == Token.LITERAL_STRING_DOUBLE_QUOTE ? '"' : '\'');
            if (close < 0) { add(a, from, end, initialTokenType, shift); return firstToken; }
            add(a, from, close, initialTokenType, shift);
            i = close;
            value = true;
            seen = true;
        }
        while (i < end) {
            char c = a[i];
            int start = i;
            if (c == ' ' || c == '\t') {
                while (i < end && (a[i] == ' ' || a[i] == '\t')) i++;
                add(a, start, i, Token.WHITESPACE, shift);
                continue;
            }
            if (c == '#') { add(a, i, end, Token.COMMENT_EOL, shift); i = end; break; }
            if (!value && !seen && brackets.isEmpty() && c == '[') {
                i = header(a, i, end);
                add(a, start, i, Token.RESERVED_WORD, shift);
                seen = true;
                continue;
            }
            seen = true;
            if (c == '"' || c == '\'') {
                int type = c == '"' ? Token.LITERAL_STRING_DOUBLE_QUOTE : Token.LITERAL_CHAR;
                if (value && i + 2 < end && a[i + 1] == c && a[i + 2] == c) {
                    int close = closeMultiline(a, i + 3, end, c);
                    if (close < 0) { add(a, start, end, type, shift); return firstToken; }
                    add(a, start, close, type, shift);
                    i = close;
                    continue;
                }
                i = closeString(a, i + 1, end, c);
                add(a, start, i, value ? type : Token.VARIABLE, shift);
                continue;
            }
            if (c == ']' || c == '}') {
                if (!brackets.isEmpty()) brackets.setLength(brackets.length() - 1);
                add(a, i, i + 1, Token.SEPARATOR, shift);
                i++;
                value = true;
                continue;
            }
            if (c == ',') {
                add(a, i, i + 1, Token.SEPARATOR, shift);
                i++;
                value = !inTable(brackets);
                continue;
            }
            if (!value) {
                if (bare(c)) {
                    while (i < end && bare(a[i])) i++;
                    add(a, start, i, Token.VARIABLE, shift);
                    continue;
                }
                add(a, i, i + 1, c == '.' || c == '=' ? Token.OPERATOR : Token.ERROR_IDENTIFIER, shift);
                i++;
                if (c == '=') value = true;
                continue;
            }
            if (c == '[' || c == '{') {
                brackets.append(c);
                add(a, i, i + 1, Token.SEPARATOR, shift);
                i++;
                value = c == '[';
                continue;
            }
            if (Character.isLetterOrDigit(c) || c == '+' || c == '-') {
                i = wordEnd(a, i, end);
                add(a, start, i, classify(new String(a, start, i - start)), shift);
                continue;
            }
            add(a, i, i + 1, Token.ERROR_IDENTIFIER, shift);
            i++;
        }
        if (!brackets.isEmpty()) { addToken(a, end, end - 1, Token.SEPARATOR, end + shift); return firstToken; }
        addNullToken();
        return firstToken;
    }

    /** Adds a token over {@code a[start, end)}; RSyntaxTextArea's end index is inclusive. */
    private void add(char[] a, int start, int end, int type, int shift) { addToken(a, start, end - 1, type, start + shift); }

    static int classify(String word) {
        if (word.equals("true") || word.equals("false")) return Token.LITERAL_BOOLEAN;
        if (RADIX.matcher(word).matches()) return Token.LITERAL_NUMBER_HEXADECIMAL;
        if (DECIMAL.matcher(word).matches()) return Token.LITERAL_NUMBER_DECIMAL_INT;
        if (FLOAT.matcher(word).matches()) return Token.LITERAL_NUMBER_FLOAT;
        if (DATE_TIME.matcher(word).matches()) return Token.DATA_TYPE;
        return Token.ERROR_IDENTIFIER;
    }

    /** The end of a {@code [table]} or {@code [[array]]} header, or of the text before a comment when it is unclosed. */
    private static int header(char[] a, int i, int end) {
        boolean array = i + 1 < end && a[i + 1] == '[';
        for (int k = i + (array ? 2 : 1); k < end; k++) {
            if (a[k] == '#') return k;
            if (a[k] == ']' && (!array || k + 1 < end && a[k + 1] == ']')) return k + (array ? 2 : 1);
        }
        return end;
    }

    /** The index after a single-line string's closing quote, or the line's end when it has none. */
    private static int closeString(char[] a, int i, int end, char quote) {
        while (i < end) {
            char c = a[i++];
            if (c == '\\' && quote == '"' && i < end) i++;
            else if (c == quote) return i;
        }
        return end;
    }

    /** The index after a multi-line string's closing delimiter, with up to two extra quotes, or -1 when the line has none. */
    private static int closeMultiline(char[] a, int i, int end, char quote) {
        for (int k = i; k + 2 < end; k++) {
            if (a[k] != quote || a[k + 1] != quote || a[k + 2] != quote) continue;
            if (quote == '"' && escaped(a, i, k)) continue;
            int close = k + 3;
            for (int extra = 0; extra < 2 && close < end && a[close] == quote; extra++) close++;
            return close;
        }
        return -1;
    }

    private static boolean escaped(char[] a, int from, int k) {
        int slashes = 0;
        for (int j = k - 1; j >= from && a[j] == '\\'; j--) slashes++;
        return slashes % 2 == 1;
    }

    /** A value word; a local date followed by a space and a time is one date-time. */
    private static int wordEnd(char[] a, int i, int end) {
        int start = i;
        while (i < end && word(a[i])) i++;
        if (LOCAL_DATE.matcher(new String(a, start, i - start)).matches() && i + 3 < end && a[i] == ' '
                && Character.isDigit(a[i + 1]) && Character.isDigit(a[i + 2]) && a[i + 3] == ':') {
            i++;
            while (i < end && word(a[i])) i++;
        }
        return i;
    }

    private static boolean word(char c) { return Character.isLetterOrDigit(c) || c == '_' || c == '+' || c == '-' || c == '.' || c == ':'; }

    private static boolean bare(char c) { return c >= 'A' && c <= 'Z' || c >= 'a' && c <= 'z' || c >= '0' && c <= '9' || c == '_' || c == '-'; }

    private static boolean inTable(StringBuilder brackets) { return !brackets.isEmpty() && brackets.charAt(brackets.length() - 1) == '{'; }
}
```

- [ ] **Step 4: Run the tests to verify they pass**

Run: `./gradlew :jasper-plugin-editor:test --tests 'dev.jasper.editor.syntax.*'`
Expected: PASS. If `theTomlLexerHighlightsInARealTextArea` fails headless, the plan's premise that RSyntaxTextArea constructs headless is wrong: stop and report it rather than working around it.

- [ ] **Step 5: Run the full check and commit**

Run: `./gradlew check`
Expected: BUILD SUCCESSFUL.

```bash
git add plugins/editor
git commit -F - <<'EOF'
feat(editor): detect languages and highlight TOML

SyntaxDetector maps names, extensions and shebangs to styles; a
hand-written TomlTokenMaker carries multi-line strings and arrays
across lines; Languages lists every style for the picker.

🤖 Generated with Claude Code at The Home Depot

Co-Authored-By: Claude <noreply@anthropic.com>
EOF
```

---

### Task 6: Local files and the local document

**Files:**
- Create: `plugins/editor/src/main/java/dev/jasper/editor/local/{LocalFiles,LocalDocument,package-info}.java`
- Test: `plugins/editor/src/test/java/dev/jasper/editor/local/{LocalFilesTest,LocalDocumentTest}.java`

**Interfaces:**
- Consumes: Task 4's `EditorDocument`, `Content`, `Revision`, `TooLargeException`.
- Produces:
  - `LocalFiles.resolve(Path) -> Path`;
  - `LocalFiles.read(Path, long limitBytes) -> Content`;
  - `LocalFiles.revision(Path) -> Optional<Revision>`;
  - `LocalFiles.write(Path, byte[]) -> Revision` (all `throws IOException`);
  - `LocalDocument(Path file, Executor background)`, `LocalDocument.file() -> Path`, `LocalDocument.key(Path) -> String` (`"local:" + absolute normalized path`).

- [ ] **Step 1: Write the failing tests**

`local/LocalFilesTest.java`:

```java
package dev.jasper.editor.local;

import dev.jasper.editor.api.Revision;
import dev.jasper.editor.api.TooLargeException;
import java.nio.file.FileSystemException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.PosixFilePermissions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeFalse;

@DisabledOnOs(OS.WINDOWS)
class LocalFilesTest {
    @TempDir Path folder;

    @Test void aSafeWriteReplacesTheFileAtomicallyAndKeepsItsPermissions() throws Exception {
        Path file = Files.writeString(folder.resolve("app.toml"), "old\n");
        Files.setPosixFilePermissions(file, PosixFilePermissions.fromString("rw-r-----"));
        Object before = Files.readAttributes(file, BasicFileAttributes.class).fileKey();
        Revision revision = LocalFiles.write(file, "new content\n".getBytes(UTF_8));
        assertThat(Files.readString(file)).isEqualTo("new content\n");
        assertThat(Files.getPosixFilePermissions(file)).isEqualTo(PosixFilePermissions.fromString("rw-r-----"));
        assertThat(Files.readAttributes(file, BasicFileAttributes.class).fileKey()).as("a new file replaced the old one").isNotEqualTo(before);
        assertThat(revision).isEqualTo(LocalFiles.revision(file).orElseThrow());
        try (var files = Files.list(folder)) { assertThat(files).as("no sibling left behind").containsExactly(file); }
    }

    @Test void aSymlinkStaysALinkAndItsTargetChanges() throws Exception {
        Path target = Files.writeString(folder.resolve("real.conf"), "a\n");
        Path link = Files.createSymbolicLink(folder.resolve("link.conf"), target.getFileName());
        LocalFiles.write(link, "b\n".getBytes(UTF_8));
        assertThat(Files.isSymbolicLink(link)).isTrue();
        assertThat(Files.readString(target)).isEqualTo("b\n");
    }

    @Test void aHardLinkedFileIsWrittenInPlace() throws Exception {
        Path file = Files.writeString(folder.resolve("shared.txt"), "one\n");
        Path other = Files.createLink(folder.resolve("other.txt"), file);
        LocalFiles.write(file, "two\n".getBytes(UTF_8));
        assertThat(Files.readString(other)).as("both names still share the file").isEqualTo("two\n");
    }

    @Test void anUnwritableFolderFallsBackToWritingInPlace() throws Exception {
        assumeFalse("root".equals(System.getProperty("user.name")), "root ignores folder permissions");
        Path locked = Files.createDirectory(folder.resolve("locked"));
        Path file = Files.writeString(locked.resolve("f.txt"), "long original text\n");
        Files.setPosixFilePermissions(locked, PosixFilePermissions.fromString("r-xr-xr-x"));
        try {
            LocalFiles.write(file, "short\n".getBytes(UTF_8));
            assertThat(Files.readString(file)).as("truncated to the new length").isEqualTo("short\n");
        } finally { Files.setPosixFilePermissions(locked, PosixFilePermissions.fromString("rwxr-xr-x")); }
    }

    @Test void aMissingFileIsCreated() throws Exception {
        Path file = folder.resolve("new.yaml");
        LocalFiles.write(file, "a: 1\n".getBytes(UTF_8));
        assertThat(Files.readString(file)).isEqualTo("a: 1\n");
    }

    @Test void anOversizedFileIsRefused() throws Exception {
        Path file = Files.write(folder.resolve("big.log"), new byte[2048]);
        assertThatExceptionOfType(TooLargeException.class).isThrownBy(() -> LocalFiles.read(file, 1024))
            .satisfies(failure -> {
                assertThat(failure.size()).isEqualTo(2048);
                assertThat(failure.limit()).isEqualTo(1024);
            });
    }

    @Test void anEmptyFileReadsAndWritesAsZeroBytes() throws Exception {
        Path file = Files.createFile(folder.resolve("empty.toml"));
        assertThat(LocalFiles.read(file, 10).bytes()).isEmpty();
        Files.writeString(file, "x");
        LocalFiles.write(file, new byte[0]);
        assertThat(Files.size(file)).isZero();
    }

    @Test void aFolderIsRefused() {
        assertThatExceptionOfType(FileSystemException.class).isThrownBy(() -> LocalFiles.read(folder, 1024))
            .withMessageContaining("Is a folder");
    }

    @Test void revisionsFollowTheFileAndAreEmptyOnceItIsGone() throws Exception {
        Path file = Files.writeString(folder.resolve("r.txt"), "abc");
        assertThat(LocalFiles.revision(file)).hasValueSatisfying(revision -> assertThat(revision.size()).isEqualTo(3));
        Files.delete(file);
        assertThat(LocalFiles.revision(file)).isEmpty();
    }

    @Test void aLinkLoopIsAnError() throws Exception {
        Path a = folder.resolve("a"), b = folder.resolve("b");
        Files.createSymbolicLink(a, b);
        Files.createSymbolicLink(b, a);
        assertThatExceptionOfType(FileSystemException.class).isThrownBy(() -> LocalFiles.resolve(a))
            .withMessageContaining("Too many levels");
    }
}
```

`local/LocalDocumentTest.java`:

```java
package dev.jasper.editor.local;

import dev.jasper.editor.api.TooLargeException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.*;

class LocalDocumentTest {
    @TempDir Path folder;

    @Test void describesItselfAndDoesItsIoOnTheBackgroundExecutor() throws Exception {
        List<Runnable> queued = new ArrayList<>();
        Path file = Files.writeString(folder.resolve("app.py"), "print(1)\n");
        var document = new LocalDocument(file, queued::add);
        assertThat(document.key()).isEqualTo("local:" + file.toAbsolutePath().normalize()).isEqualTo(LocalDocument.key(file));
        assertThat(document.name()).isEqualTo("app.py");
        assertThat(document.location()).isEqualTo(file.toAbsolutePath().normalize().toString());
        assertThat(document.host()).isEmpty();
        var read = document.read(1024);
        assertThat(read).as("nothing runs on the caller's thread").isNotDone();
        queued.removeFirst().run();
        assertThat(new String(read.get().bytes(), UTF_8)).isEqualTo("print(1)\n");
        var written = document.write("print(2)\n".getBytes(UTF_8));
        queued.removeFirst().run();
        var revision = document.revision();
        queued.removeFirst().run();
        assertThat(revision.get()).contains(written.get());
        var tooBig = document.read(1);
        queued.removeFirst().run();
        assertThatThrownBy(tooBig::get).hasCauseInstanceOf(TooLargeException.class);
        var cancelled = document.read(1024);
        cancelled.cancel(true);
        queued.removeFirst().run();
        assertThat(cancelled).isCancelled();
    }
}
```

- [ ] **Step 2: Run them to verify they fail**

Run: `./gradlew :jasper-plugin-editor:test --tests 'dev.jasper.editor.local.*'`
Expected: compilation fails with `cannot find symbol: class LocalFiles`.

- [ ] **Step 3: Write the local package**

`local/package-info.java`:

```java
/** Files on this machine: the write contract, the local document and the watcher. Blocking I/O runs off the UI thread. */
package dev.jasper.editor.local;
```

`local/LocalFiles.java`:

```java
package dev.jasper.editor.local;

import dev.jasper.editor.api.Content;
import dev.jasper.editor.api.Revision;
import dev.jasper.editor.api.TooLargeException;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.FileSystemException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.PosixFileAttributeView;
import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;

/** Reading, revisions and the write contract (spec 4.4) for files on this machine. Blocking: call off the UI thread. */
public final class LocalFiles {
    static final int MAX_LINKS = 40;

    private LocalFiles() { }

    /**
     * @param path a path that may be a symbolic link
     * @return the file the links name, which need not exist
     * @throws IOException after more than 40 links, or when a link cannot be read
     */
    public static Path resolve(Path path) throws IOException {
        Path current = path.toAbsolutePath().normalize();
        for (int hops = 0; Files.isSymbolicLink(current); hops++) {
            if (hops == MAX_LINKS) throw new FileSystemException(path.toString(), null, "Too many levels of symbolic links");
            Path target = Files.readSymbolicLink(current);
            Path parent = current.getParent();
            current = (parent == null ? target : parent.resolve(target)).toAbsolutePath().normalize();
        }
        return current;
    }

    /**
     * @param path a file
     * @param limitBytes the largest size accepted
     * @return its content, refused before reading when it is larger than the limit
     * @throws IOException for a folder, a missing or unreadable file, or {@link TooLargeException}
     */
    public static Content read(Path path, long limitBytes) throws IOException {
        BasicFileAttributes attributes = Files.readAttributes(path, BasicFileAttributes.class);
        if (attributes.isDirectory()) throw new FileSystemException(path.toString(), null, "Is a folder");
        if (attributes.size() > limitBytes) throw new TooLargeException(attributes.size(), limitBytes);
        byte[] bytes = Files.readAllBytes(path);
        if (bytes.length > limitBytes) throw new TooLargeException(bytes.length, limitBytes);
        return new Content(bytes, new Revision(attributes.lastModifiedTime().toMillis(), bytes.length));
    }

    /**
     * @param path a file, following links
     * @return its revision, or empty when it does not exist
     * @throws IOException when it cannot be read
     */
    public static Optional<Revision> revision(Path path) throws IOException {
        try {
            BasicFileAttributes attributes = Files.readAttributes(path, BasicFileAttributes.class);
            return Optional.of(new Revision(attributes.lastModifiedTime().toMillis(), attributes.size()));
        } catch (NoSuchFileException gone) {
            return Optional.empty();
        }
    }

    /**
     * Replaces a file's content following the write contract.
     *
     * @param path the file, possibly a symbolic link
     * @param content the new bytes
     * @return the new revision
     * @throws IOException when neither a safe write nor a write in place succeeds
     */
    public static Revision write(Path path, byte[] content) throws IOException {
        Path target = resolve(path);
        boolean exists = Files.exists(target, LinkOption.NOFOLLOW_LINKS);
        if (exists && Files.isDirectory(target)) throw new FileSystemException(target.toString(), null, "Is a folder");
        if (exists && links(target) > 1) return inPlace(target, content);
        Path parent = target.getParent();
        if (parent == null) throw new FileSystemException(target.toString(), null, "Has no folder");
        Path sibling;
        try {
            sibling = Files.createFile(parent.resolve("." + target.getFileName() + ".jasper-"
                + Long.toHexString(ThreadLocalRandom.current().nextLong()) + ".tmp"));
        } catch (IOException cannotCreate) {
            if (!exists) throw cannotCreate;
            return inPlace(target, content);
        }
        try {
            try (FileChannel channel = FileChannel.open(sibling, StandardOpenOption.WRITE)) {
                writeAll(channel, content);
                channel.force(true);
            }
            if (exists) {
                if (!sameOwner(sibling, target)) {
                    Files.deleteIfExists(sibling);
                    return inPlace(target, content);
                }
                copyPermissions(target, sibling);
            }
            Files.move(sibling, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException | RuntimeException failure) {
            try { Files.deleteIfExists(sibling); } catch (IOException suppressed) { failure.addSuppressed(suppressed); }
            throw failure;
        }
        return revision(target).orElseThrow(() -> new NoSuchFileException(target.toString()));
    }

    /** Keeps the owner, group, hard links and extended attributes; not atomic. */
    private static Revision inPlace(Path target, byte[] content) throws IOException {
        try (FileChannel channel = FileChannel.open(target, StandardOpenOption.WRITE)) {
            writeAll(channel, content);
            channel.truncate(content.length);
            channel.force(true);
        }
        return revision(target).orElseThrow(() -> new NoSuchFileException(target.toString()));
    }

    private static void writeAll(FileChannel channel, byte[] content) throws IOException {
        ByteBuffer buffer = ByteBuffer.wrap(content);
        while (buffer.hasRemaining()) channel.write(buffer, buffer.position());
    }

    private static int links(Path path) {
        try { return ((Number) Files.getAttribute(path, "unix:nlink")).intValue(); }
        catch (UnsupportedOperationException | IllegalArgumentException | IOException unknown) { return 1; }
    }

    private static boolean sameOwner(Path first, Path second) {
        try {
            return Files.getAttribute(first, "unix:uid").equals(Files.getAttribute(second, "unix:uid"))
                && Files.getAttribute(first, "unix:gid").equals(Files.getAttribute(second, "unix:gid"));
        } catch (UnsupportedOperationException | IllegalArgumentException | IOException unknown) {
            return true;
        }
    }

    private static void copyPermissions(Path from, Path to) throws IOException {
        if (Files.getFileAttributeView(from, PosixFileAttributeView.class) == null) return;
        Files.setPosixFilePermissions(to, Files.getPosixFilePermissions(from));
    }
}
```

`local/LocalDocument.java`:

```java
package dev.jasper.editor.local;

import dev.jasper.editor.api.Content;
import dev.jasper.editor.api.EditorDocument;
import dev.jasper.editor.api.Revision;
import java.nio.file.Path;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

/** A file on this machine; its reads, stats and writes run on the plugin's background executor. */
public final class LocalDocument implements EditorDocument {
    private final Path file;
    private final Executor background;

    /**
     * @param file the file
     * @param background where its I/O runs
     */
    public LocalDocument(Path file, Executor background) {
        this.file = file.toAbsolutePath().normalize();
        this.background = Objects.requireNonNull(background);
    }

    /**
     * @param file a local file
     * @return the key its document has
     */
    public static String key(Path file) { return "local:" + file.toAbsolutePath().normalize(); }

    /** @return the absolute, normalized file */
    public Path file() { return file; }

    @Override public String key() { return key(file); }
    @Override public String name() { Path name = file.getFileName(); return name == null ? file.toString() : name.toString(); }
    @Override public String location() { return file.toString(); }
    @Override public Optional<String> host() { return Optional.empty(); }
    @Override public CompletableFuture<Content> read(long limitBytes) { return supply(() -> LocalFiles.read(file, limitBytes)); }
    @Override public CompletableFuture<Optional<Revision>> revision() { return supply(() -> LocalFiles.revision(file)); }
    @Override public CompletableFuture<Revision> write(byte[] content) { return supply(() -> LocalFiles.write(file, content)); }

    private <T> CompletableFuture<T> supply(Callable<T> work) {
        var result = new CompletableFuture<T>();
        try {
            background.execute(() -> {
                if (result.isDone()) return;
                try { result.complete(work.call()); }
                catch (Exception failure) { result.completeExceptionally(failure); }
            });
        } catch (RuntimeException rejected) {
            result.completeExceptionally(rejected);
        }
        return result;
    }
}
```

- [ ] **Step 4: Run the tests to verify they pass**

Run: `./gradlew :jasper-plugin-editor:test --tests 'dev.jasper.editor.local.*'`
Expected: PASS.

- [ ] **Step 5: Run the full check and commit**

Run: `./gradlew check`
Expected: BUILD SUCCESSFUL.

```bash
git add plugins/editor
git commit -F - <<'EOF'
feat(editor): read and safely write local files

LocalFiles follows links, writes a sibling and atomically replaces the
target with its permissions, and writes in place for hard links, a
read-only folder or a different owner; LocalDocument runs it off the UI thread.

🤖 Generated with Claude Code at The Home Depot

Co-Authored-By: Claude <noreply@anthropic.com>
EOF
```

---

### Task 7: Document sessions and the tab model

**Files:**
- Create: `plugins/editor/src/main/java/dev/jasper/editor/session/{Question,SessionEnvironment,Failures,DocumentSession,EditorModel,package-info}.java`
- Test: `plugins/editor/src/test/java/dev/jasper/editor/session/{FakeDocument,DocumentSessionTest,EditorModelTest}.java`

**Interfaces:**
- Consumes: Task 4's API and codec, and Task 5's `SyntaxDetector`.
- Produces:
  - `record Question(String title, String message, List<String> choices)` with `dismissed()`;
  - `record SessionEnvironment(Executor background, Executor ui, Function<Question, String> ask, LongSupplier limitBytes)`;
  - `Failures.describe(String name, Throwable) -> String`, `Failures.reason(Throwable) -> String`, `Failures.retryable(Throwable) -> boolean`, `Failures.size(long) -> String`;
  - `DocumentSession`:
    - creation: `DocumentSession(EditorDocument, SessionEnvironment)` and `static DocumentSession untitled(String name, SessionEnvironment)`;
    - types: `enum State { LOADING, READY, FAILED }`, `enum Banner { NONE, CHANGED, DELETED }`;
    - answer constants: `OVERWRITE`, `RELOAD`, `SAVE_ANYWAY`, `CONVERT`, `DISCARD`;
    - wiring: `attach(Supplier<String>, BiConsumer<String, Boolean>)`, `addListener(Runnable)`;
    - loading: `load()`, `cancelLoad()`, `reload()`, `revert()`;
    - editing and saving: `textEdited()`, `save() -> CompletableFuture<Boolean>`, `saveAs(EditorDocument) -> CompletableFuture<Boolean>`, `pendingSave() -> Optional<CompletableFuture<Boolean>>`;
    - outside changes: `externalChange(Optional<Revision>)`, `keepMine()`, `keepDeleted()`, `reloadFromBanner()`;
    - per-tab options: `setStyle(String)`, `setWrap(boolean)`, `clearNotice()`;
    - getters: `document()`, `untitled()`, `key()`, `name()`, `location()`, `host()`, `parentName()`, `state()`, `saving()`, `dirty()`, `failure()`, `retryable()`, `format()`, `mixed()`, `style()`, `tabs()`, `wrap()`, `banner()`, `revision()`, `notice()`.
  - `EditorModel`: `sessions()`, `find(String key)`, `add`, `remove`, `indexOf`, `nextUntitledName()`, `detail(DocumentSession)`, `unsaved()`.

- [ ] **Step 1: Write the test double and failing tests**

`session/FakeDocument.java` (test source set):

```java
package dev.jasper.editor.session;

import dev.jasper.editor.api.Content;
import dev.jasper.editor.api.EditorDocument;
import dev.jasper.editor.api.Revision;
import dev.jasper.editor.api.TooLargeException;
import java.io.IOException;
import java.nio.file.NoSuchFileException;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import static java.nio.charset.StandardCharsets.UTF_8;

/** A document whose futures complete at once unless held; the file lives in fields. */
final class FakeDocument implements EditorDocument {
    final String name, location;
    final Optional<String> host;
    byte[] bytes = new byte[0];
    long modified = 1_000;
    boolean exists = true;
    IOException readFailure, writeFailure;
    boolean holdReads, holdWrites;
    CompletableFuture<Content> heldRead;
    CompletableFuture<Revision> heldWrite;
    int reads, writes;

    FakeDocument(String name, String host) {
        this.name = name;
        this.host = Optional.ofNullable(host);
        this.location = host == null ? "/srv/app/" + name : "dustin@" + host + ":/srv/app/" + name;
    }

    FakeDocument text(String value) { bytes = value.getBytes(UTF_8); return this; }
    String text() { return new String(bytes, UTF_8); }
    Revision now() { return new Revision(modified, bytes.length); }
    void change(String value) { bytes = value.getBytes(UTF_8); modified += 1_000; exists = true; }

    @Override public String key() { return "fake:" + location; }
    @Override public String name() { return name; }
    @Override public String location() { return location; }
    @Override public Optional<String> host() { return host; }

    @Override public CompletableFuture<Content> read(long limitBytes) {
        reads++;
        if (holdReads) return heldRead = new CompletableFuture<>();
        if (readFailure != null) return CompletableFuture.failedFuture(readFailure);
        if (!exists) return CompletableFuture.failedFuture(new NoSuchFileException(location));
        if (bytes.length > limitBytes) return CompletableFuture.failedFuture(new TooLargeException(bytes.length, limitBytes));
        return CompletableFuture.completedFuture(new Content(bytes.clone(), now()));
    }

    @Override public CompletableFuture<Optional<Revision>> revision() {
        return CompletableFuture.completedFuture(exists ? Optional.of(now()) : Optional.empty());
    }

    @Override public CompletableFuture<Revision> write(byte[] content) {
        writes++;
        if (holdWrites) return heldWrite = new CompletableFuture<>();
        if (writeFailure != null) return CompletableFuture.failedFuture(writeFailure);
        bytes = content.clone();
        modified += 1_000;
        exists = true;
        return CompletableFuture.completedFuture(now());
    }
}
```

`session/DocumentSessionTest.java`:

```java
package dev.jasper.editor.session;

import dev.jasper.editor.api.Content;
import dev.jasper.editor.api.Revision;
import dev.jasper.editor.syntax.Languages;
import java.nio.charset.StandardCharsets;
import java.nio.file.AccessDeniedException;
import java.nio.file.NoSuchFileException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;
import static org.fife.ui.rsyntaxtextarea.SyntaxConstants.SYNTAX_STYLE_PYTHON;

class DocumentSessionTest {
    private final Deque<String> answers = new ArrayDeque<>();
    private final List<Question> asked = new ArrayList<>();
    private final SessionEnvironment environment = new SessionEnvironment(Runnable::run, Runnable::run,
        question -> { asked.add(question); return answers.isEmpty() ? question.dismissed() : answers.removeFirst(); }, () -> 1024);
    private final StringBuilder buffer = new StringBuilder();
    private final List<Boolean> keeps = new ArrayList<>();
    private int changes;

    private DocumentSession attach(DocumentSession session) {
        session.attach(buffer::toString, (text, keep) -> { buffer.setLength(0); buffer.append(text); keeps.add(keep); });
        session.addListener(() -> changes++);
        return session;
    }

    private DocumentSession open(FakeDocument document) {
        DocumentSession session = attach(new DocumentSession(document, environment));
        session.load();
        return session;
    }

    private void type(DocumentSession session, String text) {
        buffer.setLength(0);
        buffer.append(text);
        session.textEdited();
    }

    @Test void loadsDecodesDetectsAndIsCleanUntilEdited() {
        var document = new FakeDocument("config.toml", "prod-web").text("name = \"x\"\n");
        var session = open(document);
        assertThat(session.state()).isEqualTo(DocumentSession.State.READY);
        assertThat(buffer).hasToString("name = \"x\"\n");
        assertThat(keeps).containsExactly(false);
        assertThat(session.style()).isEqualTo(Languages.TOML);
        assertThat(session.format().label()).isEqualTo("UTF-8");
        assertThat(session.revision()).contains(document.now());
        assertThat(session.dirty()).isFalse();
        int before = changes;
        type(session, "name = \"y\"\n");
        assertThat(session.dirty()).isTrue();
        assertThat(changes).as("listeners hear the change").isGreaterThan(before);
        type(session, "name = \"x\"\n");
        assertThat(session.dirty()).as("undoing back to the saved text clears it").isFalse();
    }

    @Test void aLoadFailureIsShownWithRetryUnlessTheFileIsBinary() {
        var document = new FakeDocument("config.toml", null);
        document.readFailure = new AccessDeniedException(document.location);
        var session = open(document);
        assertThat(session.state()).isEqualTo(DocumentSession.State.FAILED);
        assertThat(session.failure()).isEqualTo("Permission denied: config.toml.");
        assertThat(session.retryable()).isTrue();
        document.readFailure = null;
        session.load();
        assertThat(session.state()).isEqualTo(DocumentSession.State.READY);
        var binary = new FakeDocument("image.png", null);
        binary.bytes = new byte[] {(byte) 0x89, 'P', 'N', 'G', 0, 0};
        var refused = open(binary);
        assertThat(refused.failure()).isEqualTo("image.png looks like a binary file.");
        assertThat(refused.retryable()).isFalse();
        var large = new FakeDocument("big.log", null);
        large.bytes = new byte[4096];
        assertThat(open(large).failure()).isEqualTo("big.log is 4.0 KiB; the limit is 1.0 KiB (max_file_mib).");
    }

    @Test void cancellingALoadIgnoresItsLateResult() {
        var document = new FakeDocument("app.py", null).text("print(1)\n");
        document.holdReads = true;
        var session = open(document);
        assertThat(session.state()).isEqualTo(DocumentSession.State.LOADING);
        session.cancelLoad();
        document.heldRead.complete(new Content("late".getBytes(StandardCharsets.UTF_8), document.now()));
        assertThat(session.state()).isEqualTo(DocumentSession.State.LOADING);
        assertThat(buffer).isEmpty();
    }

    @Test void savingWritesTheSnapshotAndAdoptsTheNewRevision() {
        var document = new FakeDocument("config.toml", "prod-web").text("a = 1\n");
        var session = open(document);
        type(session, "a = 2\n");
        var saved = session.save();
        assertThat(saved).isCompletedWithValue(true);
        assertThat(document.text()).isEqualTo("a = 2\n");
        assertThat(session.revision()).contains(document.now());
        assertThat(session.dirty()).isFalse();
        assertThat(session.saving()).isFalse();
        assertThat(asked).isEmpty();
    }

    @Test void typingDuringASaveKeepsTheTabDirtyAndASecondSaveJoinsTheFirst() {
        var document = new FakeDocument("config.toml", null).text("a = 1\n");
        var session = open(document);
        type(session, "a = 2\n");
        document.holdWrites = true;
        var first = session.save();
        assertThat(session.saving()).isTrue();
        assertThat(session.save()).isSameAs(first);
        assertThat(session.pendingSave()).contains(first);
        type(session, "a = 3\n");
        document.heldWrite.complete(new Revision(9_000, 6));
        assertThat(first).isCompletedWithValue(true);
        assertThat(session.dirty()).as("edits after the snapshot are still unsaved").isTrue();
        type(session, "a = 2\n");
        assertThat(session.dirty()).as("the snapshot became the saved text").isFalse();
    }

    @Test void aChangedFileAsksOverwriteReloadOrCancel() {
        var document = new FakeDocument("config.toml", "prod-web").text("a = 1\n");
        var session = open(document);
        type(session, "mine\n");
        document.change("theirs\n");
        answers.add("Cancel");
        assertThat(session.save()).isCompletedWithValue(false);
        assertThat(asked.getLast()).isEqualTo(new Question("File changed", "config.toml changed on prod-web since you opened it.",
            List.of(DocumentSession.OVERWRITE, DocumentSession.RELOAD, "Cancel")));
        assertThat(document.text()).isEqualTo("theirs\n");
        assertThat(session.dirty()).isTrue();
        answers.add(DocumentSession.OVERWRITE);
        assertThat(session.save()).isCompletedWithValue(true);
        assertThat(document.text()).isEqualTo("mine\n");
        document.change("theirs again\n");
        type(session, "mine again\n");
        answers.add(DocumentSession.RELOAD);
        assertThat(session.save()).isCompletedWithValue(false);
        assertThat(buffer).hasToString("theirs again\n");
        assertThat(keeps.getLast()).as("a reload keeps the caret's place").isTrue();
        assertThat(session.dirty()).isFalse();
    }

    @Test void aDeletedFileAsksSaveAnyway() {
        var document = new FakeDocument("config.toml", "prod-web").text("a = 1\n");
        var session = open(document);
        type(session, "a = 2\n");
        document.exists = false;
        answers.add("Cancel");
        assertThat(session.save()).isCompletedWithValue(false);
        assertThat(asked.getLast().message()).isEqualTo("config.toml no longer exists on prod-web.");
        answers.add(DocumentSession.SAVE_ANYWAY);
        assertThat(session.save()).isCompletedWithValue(true);
        assertThat(document.exists).isTrue();
        assertThat(document.text()).isEqualTo("a = 2\n");
    }

    @Test void aFailedWriteLeavesTheTabDirtyAndTheNextSaveRetries() {
        var document = new FakeDocument("config.toml", null).text("a = 1\n");
        var session = open(document);
        type(session, "a = 2\n");
        document.writeFailure = new AccessDeniedException(document.location);
        assertThat(session.save()).isCompletedWithValue(false);
        assertThat(asked.getLast()).isEqualTo(new Question("Save failed", "Could not save config.toml: permission denied.", List.of("OK")));
        assertThat(document.text()).isEqualTo("a = 1\n");
        assertThat(session.dirty()).isTrue();
        document.writeFailure = null;
        assertThat(session.save()).isCompletedWithValue(true);
        assertThat(document.text()).isEqualTo("a = 2\n");
    }

    @Test void textTheCharsetCannotHoldOffersUtf8() {
        var document = new FakeDocument("notes.txt", null);
        document.bytes = new byte[] {'c', 'a', 'f', (byte) 0xE9, '\n'};
        var session = open(document);
        assertThat(session.format().label()).isEqualTo("ISO-8859-1");
        type(session, "café \uD83D\uDE00\n");
        answers.add("Cancel");
        assertThat(session.save()).isCompletedWithValue(false);
        assertThat(asked.getLast().message()).isEqualTo("Line 1 of notes.txt has a character that ISO-8859-1 cannot store.");
        assertThat(document.writes).isZero();
        answers.add(DocumentSession.CONVERT);
        assertThat(session.save()).isCompletedWithValue(true);
        assertThat(document.text()).isEqualTo("café \uD83D\uDE00\n");
        assertThat(session.format().label()).isEqualTo("UTF-8");
    }

    @Test void outsideChangesReloadACleanTabAndBannerADirtyOne() {
        var document = new FakeDocument("app.py", null).text("a\n");
        var session = open(document);
        document.change("b\n");
        session.externalChange(Optional.of(document.now()));
        assertThat(buffer).hasToString("b\n");
        assertThat(session.notice()).contains("Reloaded: changed on disk");
        session.clearNotice();
        assertThat(session.notice()).isEmpty();
        type(session, "mine\n");
        document.change("c\n");
        session.externalChange(Optional.of(document.now()));
        assertThat(session.banner()).isEqualTo(DocumentSession.Banner.CHANGED);
        assertThat(buffer).hasToString("mine\n");
        session.externalChange(Optional.of(document.now()));
        assertThat(session.banner()).isEqualTo(DocumentSession.Banner.CHANGED);
        session.keepMine();
        assertThat(session.banner()).isEqualTo(DocumentSession.Banner.NONE);
        assertThat(session.save()).isCompletedWithValue(true);
        assertThat(asked).as("Keep Mine adopted the new revision").isEmpty();
        assertThat(document.text()).isEqualTo("mine\n");
    }

    @Test void aDeletedFileShowsItsBannerAndKeepRecreatesItOnSave() {
        var document = new FakeDocument("app.py", null).text("a\n");
        var session = open(document);
        document.exists = false;
        session.externalChange(Optional.empty());
        assertThat(session.banner()).isEqualTo(DocumentSession.Banner.DELETED);
        session.keepDeleted();
        assertThat(session.dirty()).as("the tab stays unsaved").isTrue();
        assertThat(session.save()).isCompletedWithValue(true);
        assertThat(asked).isEmpty();
        assertThat(document.exists).isTrue();
    }

    @Test void ownSavesAreNotOutsideChanges() {
        var document = new FakeDocument("app.py", null).text("a\n");
        var session = open(document);
        type(session, "b\n");
        session.save();
        int reads = document.reads;
        session.externalChange(Optional.of(document.now()));
        assertThat(document.reads).isEqualTo(reads);
        assertThat(session.banner()).isEqualTo(DocumentSession.Banner.NONE);
    }

    @Test void revertAsksBeforeDiscardingEditsAndAFailedReloadKeepsTheText() {
        var document = new FakeDocument("app.py", null).text("saved\n");
        var session = open(document);
        type(session, "mine\n");
        answers.add("Cancel");
        session.revert();
        assertThat(buffer).hasToString("mine\n");
        answers.add(DocumentSession.DISCARD);
        session.revert();
        assertThat(buffer).hasToString("saved\n");
        type(session, "mine\n");
        document.readFailure = new NoSuchFileException(document.location);
        answers.add(DocumentSession.DISCARD);
        session.revert();
        assertThat(asked.getLast().title()).isEqualTo("Reload failed");
        assertThat(buffer).hasToString("mine\n");
        assertThat(session.state()).isEqualTo(DocumentSession.State.READY);
    }

    @Test void saveAsWritesToTheTargetAndAdoptsIt() {
        var session = attach(DocumentSession.untitled("Untitled", environment));
        assertThat(session.untitled()).isTrue();
        assertThat(session.key()).isEqualTo("untitled:Untitled");
        type(session, "print(1)\n");
        assertThat(session.dirty()).isTrue();
        var target = new FakeDocument("app.py", null);
        target.exists = false;
        assertThat(session.saveAs(target)).isCompletedWithValue(true);
        assertThat(target.text()).isEqualTo("print(1)\n");
        assertThat(session.name()).isEqualTo("app.py");
        assertThat(session.key()).isEqualTo(target.key());
        assertThat(session.style()).isEqualTo(SYNTAX_STYLE_PYTHON);
        assertThat(session.dirty()).isFalse();
        assertThat(asked).isEmpty();
    }
}
```

`session/EditorModelTest.java`:

```java
package dev.jasper.editor.session;

import java.util.List;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class EditorModelTest {
    private final SessionEnvironment environment = new SessionEnvironment(Runnable::run, Runnable::run, Question::dismissed, () -> 1024);

    @Test void detailsShowTheHostAndTheFolderWhenNamesClash() {
        var model = new EditorModel();
        var local = new DocumentSession(new FakeDocument("config.toml", null), environment);
        var remote = new DocumentSession(new FakeDocument("config.toml", "prod-web"), environment);
        var other = new DocumentSession(new FakeDocument("app.py", "prod-web"), environment);
        model.add(local);
        assertThat(model.detail(local)).isEmpty();
        model.add(remote);
        model.add(other);
        assertThat(model.detail(local)).isEqualTo("app");
        assertThat(model.detail(remote)).isEqualTo("app · prod-web");
        assertThat(model.detail(other)).isEqualTo("prod-web");
        assertThat(model.find(remote.key())).containsSame(remote);
        assertThat(model.indexOf(other)).isEqualTo(2);
        model.remove(remote);
        assertThat(model.sessions()).containsExactly(local, other);
    }

    @Test void untitledNamesReuseTheLowestFreeNumber() {
        var model = new EditorModel();
        var first = DocumentSession.untitled(model.nextUntitledName(), environment);
        model.add(first);
        var second = DocumentSession.untitled(model.nextUntitledName(), environment);
        model.add(second);
        assertThat(List.of(first.name(), second.name())).containsExactly("Untitled", "Untitled 2");
        model.remove(first);
        assertThat(model.nextUntitledName()).isEqualTo("Untitled");
    }

    @Test void unsavedListsDirtyAndSavingSessions() {
        var model = new EditorModel();
        var session = DocumentSession.untitled("Untitled", environment);
        StringBuilder text = new StringBuilder();
        session.attach(text::toString, (value, keep) -> { });
        model.add(session);
        assertThat(model.unsaved()).isEmpty();
        text.append("x");
        assertThat(model.unsaved()).containsExactly(session);
    }
}
```

- [ ] **Step 2: Run them to verify they fail**

Run: `./gradlew :jasper-plugin-editor:test --tests 'dev.jasper.editor.session.*'`
Expected: compilation fails with `cannot find symbol: class DocumentSession`.

- [ ] **Step 3: Write the session package**

`session/package-info.java`:

```java
/** One tab's document as a state machine, and the set of tabs. UI thread only; no Swing. */
package dev.jasper.editor.session;
```

`session/Question.java`:

```java
package dev.jasper.editor.session;

import java.util.List;
import java.util.Objects;

/**
 * A question for the user, answered with one of its choices.
 *
 * @param title the dialog title
 * @param message the question
 * @param choices the buttons, first the default; the last is what closing the dialog means
 */
public record Question(String title, String message, List<String> choices) {
    /** Validates and copies. */
    public Question {
        if (title == null || title.isBlank()) throw new IllegalArgumentException("A question needs a title");
        Objects.requireNonNull(message, "message");
        choices = List.copyOf(choices);
        if (choices.isEmpty()) throw new IllegalArgumentException("A question needs a choice");
    }

    /** @return the answer when the dialog is closed without a choice */
    public String dismissed() { return choices.getLast(); }
}
```

`session/SessionEnvironment.java`:

```java
package dev.jasper.editor.session;

import java.util.concurrent.Executor;
import java.util.function.Function;
import java.util.function.LongSupplier;

/**
 * What a session needs from the plugin.
 *
 * @param background where decoding and encoding run
 * @param ui the UI thread; results come back here
 * @param ask asks the user a question on the UI thread and returns the chosen answer
 * @param limitBytes the largest file the editor opens
 */
public record SessionEnvironment(Executor background, Executor ui, Function<Question, String> ask, LongSupplier limitBytes) { }
```

`session/Failures.java`:

```java
package dev.jasper.editor.session;

import dev.jasper.editor.api.TooLargeException;
import dev.jasper.editor.text.BinaryFileException;
import java.nio.file.AccessDeniedException;
import java.nio.file.FileSystemException;
import java.nio.file.NoSuchFileException;
import java.util.Locale;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;

/** Words for what went wrong (spec 8). */
public final class Failures {
    private Failures() { }

    /**
     * @param name the file's name
     * @param error what went wrong opening it
     * @return a sentence for the tab
     */
    public static String describe(String name, Throwable error) {
        Throwable cause = unwrap(error);
        if (cause instanceof TooLargeException large)
            return name + " is " + size(large.size()) + "; the limit is " + size(large.limit()) + " (max_file_mib).";
        if (cause instanceof BinaryFileException) return name + " looks like a binary file.";
        if (cause instanceof NoSuchFileException) return name + " does not exist.";
        if (cause instanceof AccessDeniedException) return "Permission denied: " + name + ".";
        return name + ": " + sentence(reason(cause));
    }

    /**
     * @param error what went wrong
     * @return a short reason, to follow "Could not save x: "
     */
    public static String reason(Throwable error) {
        Throwable cause = unwrap(error);
        if (cause instanceof TooLargeException large) return "it is " + size(large.size()) + "; the limit is " + size(large.limit());
        if (cause instanceof NoSuchFileException) return "it does not exist";
        if (cause instanceof AccessDeniedException) return "permission denied";
        if (cause instanceof FileSystemException system && system.getReason() != null) return system.getReason();
        String message = cause.getMessage();
        return message == null || message.isBlank() ? cause.getClass().getSimpleName() : message;
    }

    /**
     * @param error what went wrong opening a file
     * @return whether trying again could help
     */
    public static boolean retryable(Throwable error) { return !(unwrap(error) instanceof BinaryFileException); }

    /**
     * @param bytes a size
     * @return it in bytes, KiB or MiB
     */
    public static String size(long bytes) {
        long mib = 1024L * 1024L;
        if (bytes >= mib) return bytes % mib == 0 ? bytes / mib + " MiB" : String.format(Locale.ROOT, "%.1f MiB", bytes / (double) mib);
        if (bytes >= 1024) return String.format(Locale.ROOT, "%.1f KiB", bytes / 1024.0);
        return bytes + " bytes";
    }

    static String sentence(String text) { return text.endsWith(".") ? text : text + "."; }

    static Throwable unwrap(Throwable error) {
        Throwable cause = error;
        while ((cause instanceof CompletionException || cause instanceof ExecutionException) && cause.getCause() != null) cause = cause.getCause();
        return cause;
    }
}
```

`session/DocumentSession.java`:

```java
package dev.jasper.editor.session;

import dev.jasper.editor.api.Content;
import dev.jasper.editor.api.EditorDocument;
import dev.jasper.editor.api.Revision;
import dev.jasper.editor.syntax.SyntaxDetector;
import dev.jasper.editor.text.DecodedText;
import dev.jasper.editor.text.TextCodec;
import dev.jasper.editor.text.TextFormat;
import dev.jasper.editor.text.UnencodableException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.RejectedExecutionException;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Supplier;
import org.fife.ui.rsyntaxtextarea.SyntaxConstants;

/**
 * One tab's document (spec 2.3, 4, 5): loading, the saved text, saving with conflict checks, reloading and
 * outside changes. UI thread only; no Swing. The tab's text reaches it through {@link #attach}.
 */
public final class DocumentSession {
    public enum State { LOADING, READY, FAILED }
    public enum Banner { NONE, CHANGED, DELETED }

    public static final String OVERWRITE = "Overwrite", RELOAD = "Reload", SAVE_ANYWAY = "Save Anyway",
        CONVERT = "Convert to UTF-8 and Save", DISCARD = "Discard Changes";

    private final SessionEnvironment environment;
    private final String untitledName;
    private final List<Runnable> listeners = new ArrayList<>();
    private EditorDocument document;
    private State state;
    private boolean saving;
    private Optional<Revision> revision = Optional.empty();
    private Optional<Revision> reported = Optional.empty();
    private TextFormat format = TextFormat.DEFAULT;
    private boolean mixed;
    private String savedText = "";
    private boolean forcedDirty;
    private boolean lastDirty;
    private String failure = "";
    private boolean retryable = true;
    private String style = SyntaxConstants.SYNTAX_STYLE_NONE;
    private boolean styleChosen;
    private boolean tabs;
    private boolean wrap;
    private Banner banner = Banner.NONE;
    private String notice = "";
    private String reloadNotice = "";
    private CompletableFuture<Content> loading;
    private CompletableFuture<Boolean> pendingSave;
    private String detached = "";
    private Supplier<String> text = () -> detached;
    private BiConsumer<String, Boolean> replace = (value, keep) -> detached = value;

    /**
     * A tab for a document; call {@link #load()} after attaching the tab's text.
     *
     * @param document the file
     * @param environment executors, the question asker and the size limit
     */
    public DocumentSession(EditorDocument document, SessionEnvironment environment) {
        this.document = Objects.requireNonNull(document);
        this.environment = Objects.requireNonNull(environment);
        this.untitledName = "";
        this.state = State.LOADING;
    }

    private DocumentSession(String untitledName, SessionEnvironment environment) {
        this.environment = Objects.requireNonNull(environment);
        this.untitledName = Objects.requireNonNull(untitledName);
        this.state = State.READY;
    }

    /**
     * @param name the tab's name, such as "Untitled 2"
     * @param environment executors, the question asker and the size limit
     * @return an empty, ready tab with no file; it is saved with {@link #saveAs}
     */
    public static DocumentSession untitled(String name, SessionEnvironment environment) { return new DocumentSession(name, environment); }

    /**
     * @param read reads the tab's current text
     * @param replace replaces the tab's text; the flag asks to keep the caret's line and scroll position
     */
    public void attach(Supplier<String> read, BiConsumer<String, Boolean> replace) {
        this.text = Objects.requireNonNull(read);
        this.replace = Objects.requireNonNull(replace);
    }

    /** @param listener runs after any change a view shows */
    public void addListener(Runnable listener) { listeners.add(Objects.requireNonNull(listener)); }

    /** Reads the document for the first time, or again after a failure. */
    public void load() {
        if (document == null) return;
        state = State.LOADING;
        failure = "";
        changed();
        fetch(false);
    }

    /** Re-reads the document while the tab stays usable; a failure asks and keeps the current text. */
    public void reload() {
        if (document == null || state != State.READY) return;
        fetch(true);
    }

    /** Stops a read in progress; its late result is ignored. */
    public void cancelLoad() {
        CompletableFuture<Content> read = loading;
        if (read == null) return;
        // Cleared first: cancel() runs the completion callback at once, and it must find the read superseded.
        loading = null;
        read.cancel(true);
    }

    /** The tab's text changed; call shortly after typing stops. */
    public void textEdited() {
        boolean now = dirty();
        if (now == lastDirty) return;
        lastDirty = now;
        changed();
    }

    /** Revert to Saved: confirms when there are edits, then re-reads the document. */
    public void revert() {
        if (document == null || state != State.READY) return;
        if (dirty() && !DISCARD.equals(environment.ask().apply(new Question("Revert to Saved",
                "Discard your changes to " + name() + "?", List.of(DISCARD, "Cancel"))))) return;
        reload();
    }

    /**
     * Saves to the document, checking first whether it changed since it was read.
     *
     * @return completes with true when saved, false when cancelled or failed (already reported)
     */
    public CompletableFuture<Boolean> save() {
        if (document == null) throw new IllegalStateException("An untitled document is saved with saveAs");
        if (pendingSave != null) return pendingSave;
        if (state != State.READY) return CompletableFuture.completedFuture(false);
        return write(document, true);
    }

    /**
     * Saves to another document, which this tab then edits; the caller has confirmed any replacement.
     *
     * @param target the new file
     * @return completes with true when saved, false when cancelled or failed (already reported)
     */
    public CompletableFuture<Boolean> saveAs(EditorDocument target) {
        Objects.requireNonNull(target);
        if (pendingSave != null) return pendingSave;
        if (state != State.READY) return CompletableFuture.completedFuture(false);
        return write(target, false);
    }

    /**
     * The document's revision as a watcher saw it.
     *
     * @param current the revision now, or empty when the file is gone
     */
    public void externalChange(Optional<Revision> current) {
        if (document == null || state != State.READY || saving || loading != null) return;
        if (current.equals(revision) || current.equals(reported)) return;
        reported = current;
        if (current.isEmpty()) { banner = Banner.DELETED; changed(); return; }
        if (dirty()) { banner = Banner.CHANGED; changed(); return; }
        reloadNotice = "Reloaded: changed on disk";
        reload();
    }

    /** Keep Mine: adopts the revision seen on disk, so the next save overwrites without asking. */
    public void keepMine() {
        revision = reported;
        banner = Banner.NONE;
        changed();
    }

    /** Keep after a deletion: the tab stays unsaved, and its next save recreates the file. */
    public void keepDeleted() {
        revision = Optional.empty();
        forcedDirty = true;
        lastDirty = true;
        banner = Banner.NONE;
        changed();
    }

    /** Reload from the banner. */
    public void reloadFromBanner() {
        banner = Banner.NONE;
        changed();
        reload();
    }

    /** @param style a style key the user chose; detection no longer changes it */
    public void setStyle(String style) {
        this.style = Objects.requireNonNull(style);
        styleChosen = true;
        changed();
    }

    /** @param wrap whether long lines wrap in this tab */
    public void setWrap(boolean wrap) {
        this.wrap = wrap;
        changed();
    }

    /** Clears the status line's notice. */
    public void clearNotice() {
        notice = "";
        changed();
    }

    public Optional<EditorDocument> document() { return Optional.ofNullable(document); }
    public boolean untitled() { return document == null; }
    public String key() { return document != null ? document.key() : "untitled:" + untitledName; }
    public String name() { return document != null ? document.name() : untitledName; }
    public String location() { return document != null ? document.location() : untitledName; }
    public Optional<String> host() { return document != null ? document.host() : Optional.empty(); }
    public State state() { return state; }
    public boolean saving() { return saving; }
    public boolean dirty() { return state == State.READY && (forcedDirty || !text.get().equals(savedText)); }
    public String failure() { return failure; }
    public boolean retryable() { return retryable; }
    public TextFormat format() { return format; }
    public boolean mixed() { return mixed; }
    public String style() { return style; }
    public boolean tabs() { return tabs; }
    public boolean wrap() { return wrap; }
    public Banner banner() { return banner; }
    public Optional<Revision> revision() { return revision; }
    public Optional<String> notice() { return notice.isEmpty() ? Optional.empty() : Optional.of(notice); }
    public Optional<CompletableFuture<Boolean>> pendingSave() { return Optional.ofNullable(pendingSave); }

    /** @return the name of the folder holding the file, or empty */
    public String parentName() {
        String where = location();
        int slash = Math.max(where.lastIndexOf('/'), where.lastIndexOf('\\'));
        if (slash <= 0) return "";
        String folder = where.substring(0, slash);
        int previous = Math.max(Math.max(folder.lastIndexOf('/'), folder.lastIndexOf('\\')), folder.lastIndexOf(':'));
        return folder.substring(previous + 1);
    }

    private void fetch(boolean again) {
        cancelLoad();
        CompletableFuture<Content> read = document.read(environment.limitBytes().getAsLong());
        loading = read;
        read.whenComplete((content, error) -> {
            if (error != null) { environment.ui().execute(() -> fetched(read, null, null, error, again)); return; }
            background(() -> {
                try {
                    DecodedText decoded = TextCodec.decode(content.bytes());
                    environment.ui().execute(() -> fetched(read, decoded, content.revision(), null, again));
                } catch (IOException binary) {
                    environment.ui().execute(() -> fetched(read, null, null, binary, again));
                }
            }, problem -> fetched(read, null, null, problem, again));
        });
    }

    private void fetched(CompletableFuture<Content> read, DecodedText decoded, Revision at, Throwable error, boolean again) {
        if (loading != read) return;
        loading = null;
        if (error != null) {
            reloadNotice = "";
            if (again) {
                environment.ask().apply(new Question("Reload failed", "Could not reload " + name() + ": " + Failures.sentence(Failures.reason(error)), List.of("OK")));
                changed();
                return;
            }
            failure = Failures.describe(name(), error);
            retryable = Failures.retryable(error);
            state = State.FAILED;
            changed();
            return;
        }
        format = decoded.format();
        mixed = decoded.mixed();
        savedText = decoded.text();
        forcedDirty = false;
        revision = Optional.of(at);
        reported = revision;
        if (!styleChosen) style = SyntaxDetector.detect(document.name(), firstLine(savedText));
        tabs = TextCodec.indentsWithTabs(savedText) || SyntaxConstants.SYNTAX_STYLE_MAKEFILE.equals(style);
        banner = Banner.NONE;
        notice = again ? reloadNotice : "";
        reloadNotice = "";
        replace.accept(savedText, again);
        state = State.READY;
        lastDirty = dirty();
        changed();
    }

    private CompletableFuture<Boolean> write(EditorDocument target, boolean check) {
        var result = new CompletableFuture<Boolean>();
        pendingSave = result;
        saving = true;
        changed();
        encode(target, check, text.get(), format, result);
        return result;
    }

    private void encode(EditorDocument target, boolean check, String snapshot, TextFormat as, CompletableFuture<Boolean> result) {
        background(() -> {
            try {
                byte[] bytes = TextCodec.encode(snapshot, as);
                environment.ui().execute(() -> encoded(target, check, snapshot, as, bytes, result));
            } catch (UnencodableException unencodable) {
                environment.ui().execute(() -> {
                    String answer = environment.ask().apply(new Question("Cannot encode", "Line " + unencodable.line() + " of " + name()
                        + " has a character that " + unencodable.charset().name() + " cannot store.", List.of(CONVERT, "Cancel")));
                    if (CONVERT.equals(answer)) encode(target, check, snapshot, new TextFormat(StandardCharsets.UTF_8, false, as.separator()), result);
                    else finish(result, false);
                });
            }
        }, problem -> failed(result, problem));
    }

    private void encoded(EditorDocument target, boolean check, String snapshot, TextFormat as, byte[] bytes, CompletableFuture<Boolean> result) {
        if (!check) { store(target, snapshot, as, bytes, result); return; }
        target.revision().whenComplete((current, error) -> environment.ui().execute(() -> {
            if (error != null) { failed(result, error); return; }
            if (current.isEmpty() && revision.isPresent()) {
                String answer = environment.ask().apply(new Question("File deleted", name() + " no longer exists " + where() + ".",
                    List.of(SAVE_ANYWAY, "Cancel")));
                if (!SAVE_ANYWAY.equals(answer)) { finish(result, false); return; }
            } else if (current.isPresent() && !current.equals(revision)) {
                String answer = environment.ask().apply(new Question("File changed", name() + " changed " + where() + " since you opened it.",
                    List.of(OVERWRITE, RELOAD, "Cancel")));
                if (RELOAD.equals(answer)) { finish(result, false); reload(); return; }
                if (!OVERWRITE.equals(answer)) { finish(result, false); return; }
            }
            store(target, snapshot, as, bytes, result);
        }));
    }

    private void store(EditorDocument target, String snapshot, TextFormat as, byte[] bytes, CompletableFuture<Boolean> result) {
        target.write(bytes).whenComplete((written, error) -> environment.ui().execute(() -> {
            if (error != null) { failed(result, error); return; }
            boolean renamed = target != document;
            document = target;
            format = as;
            mixed = false;
            revision = Optional.of(written);
            reported = revision;
            savedText = snapshot;
            forcedDirty = false;
            banner = Banner.NONE;
            if (renamed && !styleChosen) style = SyntaxDetector.detect(target.name(), firstLine(snapshot));
            lastDirty = dirty();
            finish(result, true);
        }));
    }

    private void failed(CompletableFuture<Boolean> result, Throwable error) {
        environment.ask().apply(new Question("Save failed", "Could not save " + name() + ": " + Failures.sentence(Failures.reason(error)), List.of("OK")));
        finish(result, false);
    }

    private void finish(CompletableFuture<Boolean> result, boolean saved) {
        if (pendingSave == result) { pendingSave = null; saving = false; }
        changed();
        result.complete(saved);
    }

    private String where() { return host().map(name -> "on " + name).orElse("on disk"); }

    private void background(Runnable work, Consumer<Throwable> rejected) {
        try { environment.background().execute(work); }
        catch (RejectedExecutionException stopped) { environment.ui().execute(() -> rejected.accept(stopped)); }
    }

    private void changed() { for (Runnable listener : List.copyOf(listeners)) listener.run(); }

    private static String firstLine(String text) {
        int newline = text.indexOf('\n');
        return newline < 0 ? text : text.substring(0, newline);
    }
}
```

`session/EditorModel.java`:

```java
package dev.jasper.editor.session;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** The Editor window's tabs, in order. UI thread only. */
public final class EditorModel {
    private final List<DocumentSession> sessions = new ArrayList<>();

    public List<DocumentSession> sessions() { return List.copyOf(sessions); }
    public Optional<DocumentSession> find(String key) { return sessions.stream().filter(session -> session.key().equals(key)).findFirst(); }
    public void add(DocumentSession session) { sessions.add(session); }
    public void remove(DocumentSession session) { sessions.remove(session); }
    public int indexOf(DocumentSession session) { return sessions.indexOf(session); }

    /** @return "Untitled", "Untitled 2", …: the lowest name no open untitled tab uses */
    public String nextUntitledName() {
        for (int n = 1; ; n++) {
            String name = n == 1 ? "Untitled" : "Untitled " + n;
            if (sessions.stream().noneMatch(session -> session.untitled() && session.name().equals(name))) return name;
        }
    }

    /**
     * @param session an open tab
     * @return the dim text after its name: the parent folder when another tab shares the name, and a remote file's host
     */
    public String detail(DocumentSession session) {
        boolean shared = sessions.stream().anyMatch(other -> other != session && other.name().equals(session.name()));
        List<String> parts = new ArrayList<>();
        if (shared && !session.parentName().isEmpty()) parts.add(session.parentName());
        session.host().ifPresent(parts::add);
        return String.join(" · ", parts);
    }

    /** @return tabs with unsaved edits or a save in progress */
    public List<DocumentSession> unsaved() { return sessions.stream().filter(session -> session.dirty() || session.saving()).toList(); }
}
```

- [ ] **Step 4: Run the tests to verify they pass**

Run: `./gradlew :jasper-plugin-editor:test --tests 'dev.jasper.editor.session.*'`
Expected: PASS.

- [ ] **Step 5: Run the full check and commit**

Run: `./gradlew check`
Expected: BUILD SUCCESSFUL.

```bash
git add plugins/editor
git commit -F - <<'EOF'
feat(editor): document sessions with conflict-checked saves

DocumentSession loads and decodes off the UI thread, saves a snapshot
after checking the revision (Overwrite/Reload, Save Anyway, Convert to
UTF-8), reloads without losing text on failure, and banners outside changes.

🤖 Generated with Claude Code at The Home Depot

Co-Authored-By: Claude <noreply@anthropic.com>
EOF
```

---

### Task 8: Watching open local files

**Files:**
- Create: `plugins/editor/src/main/java/dev/jasper/editor/local/LocalWatcher.java`
- Test: `plugins/editor/src/test/java/dev/jasper/editor/local/LocalWatcherTest.java`

**Interfaces:**
- Consumes: `LocalDocument`, `LocalFiles.revision`, and `DocumentSession.externalChange` from Tasks 6–7.
- Produces: `LocalWatcher(Executor background, Executor ui, Supplier<List<DocumentSession>> sessions)` and `tick()`, which the Editor window calls every 2 s.

- [ ] **Step 1: Write the failing test**

`local/LocalWatcherTest.java`:

```java
package dev.jasper.editor.local;

import dev.jasper.editor.session.DocumentSession;
import dev.jasper.editor.session.Question;
import dev.jasper.editor.session.SessionEnvironment;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.assertj.core.api.Assertions.assertThat;

class LocalWatcherTest {
    @TempDir Path folder;
    private final List<Runnable> background = new ArrayList<>();
    private final SessionEnvironment environment = new SessionEnvironment(background::add, Runnable::run, Question::dismissed, () -> 1 << 20);

    private void drain() { while (!background.isEmpty()) background.removeFirst().run(); }

    private DocumentSession open(Path file, StringBuilder text) {
        var session = new DocumentSession(new LocalDocument(file, background::add), environment);
        session.attach(text::toString, (value, keep) -> { text.setLength(0); text.append(value); });
        session.load();
        return session;
    }

    @Test void aCleanTabReloadsADirtyOneShowsTheBannerAndDeletionIsReported() throws Exception {
        Path clean = Files.writeString(folder.resolve("clean.yaml"), "a: 1\n");
        Path dirty = Files.writeString(folder.resolve("dirty.yaml"), "b: 1\n");
        StringBuilder cleanText = new StringBuilder(), dirtyText = new StringBuilder();
        var cleanSession = open(clean, cleanText);
        var dirtySession = open(dirty, dirtyText);
        drain();
        dirtyText.setLength(0);
        dirtyText.append("b: mine\n");
        dirtySession.textEdited();
        var watcher = new LocalWatcher(background::add, Runnable::run, () -> List.of(cleanSession, dirtySession));
        watcher.tick();
        drain();
        assertThat(cleanSession.banner()).isEqualTo(DocumentSession.Banner.NONE);
        assertThat(cleanSession.notice()).as("nothing changed").isEmpty();
        Files.writeString(clean, "a: 22\n");
        Files.writeString(dirty, "b: 22\n");
        watcher.tick();
        drain();
        assertThat(cleanText).hasToString("a: 22\n");
        assertThat(cleanSession.notice()).contains("Reloaded: changed on disk");
        assertThat(dirtySession.banner()).isEqualTo(DocumentSession.Banner.CHANGED);
        assertThat(dirtyText).hasToString("b: mine\n");
        Files.delete(clean);
        watcher.tick();
        drain();
        assertThat(cleanSession.banner()).isEqualTo(DocumentSession.Banner.DELETED);
    }

    @Test void aTickWhileTheLastIsRunningAndTabsWithoutALocalFileAreSkipped() throws Exception {
        Path file = Files.writeString(folder.resolve("a.toml"), "a = 1\n");
        var session = open(file, new StringBuilder());
        drain();
        var untitled = DocumentSession.untitled("Untitled", environment);
        var watcher = new LocalWatcher(background::add, Runnable::run, () -> List.of(session, untitled));
        watcher.tick();
        watcher.tick();
        assertThat(background).as("one stat pass at a time").hasSize(1);
        drain();
        var none = new LocalWatcher(background::add, Runnable::run, () -> List.of(untitled));
        none.tick();
        assertThat(background).isEmpty();
    }
}
```

- [ ] **Step 2: Run it to verify it fails**

Run: `./gradlew :jasper-plugin-editor:test --tests '*LocalWatcherTest'`
Expected: compilation fails with `cannot find symbol: class LocalWatcher`.

- [ ] **Step 3: Write the watcher**

`local/LocalWatcher.java`:

```java
package dev.jasper.editor.local;

import dev.jasper.editor.api.Revision;
import dev.jasper.editor.session.DocumentSession;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import java.util.function.Supplier;

/**
 * Notices outside changes to open local files (spec 5). The Editor window calls {@link #tick()} every 2 s
 * while it is open; polling behaves the same on network mounts and under a fake clock.
 */
public final class LocalWatcher {
    private final Executor background;
    private final Executor ui;
    private final Supplier<List<DocumentSession>> sessions;
    private boolean running;

    /**
     * @param background where the stats run
     * @param ui where results are delivered
     * @param sessions the open tabs
     */
    public LocalWatcher(Executor background, Executor ui, Supplier<List<DocumentSession>> sessions) {
        this.background = Objects.requireNonNull(background);
        this.ui = Objects.requireNonNull(ui);
        this.sessions = Objects.requireNonNull(sessions);
    }

    /** UI thread: stats every ready local tab's file in the background, then reports each revision to its tab. */
    public void tick() {
        if (running) return;
        List<DocumentSession> watched = new ArrayList<>();
        List<Path> files = new ArrayList<>();
        for (DocumentSession session : sessions.get())
            if (session.state() == DocumentSession.State.READY && session.document().orElse(null) instanceof LocalDocument local) {
                watched.add(session);
                files.add(local.file());
            }
        if (watched.isEmpty()) return;
        running = true;
        try {
            background.execute(() -> {
                List<Optional<Revision>> revisions = new ArrayList<>();
                for (Path file : files) {
                    try { revisions.add(LocalFiles.revision(file)); }
                    catch (IOException unreadable) { revisions.add(null); }
                }
                ui.execute(() -> {
                    running = false;
                    for (int i = 0; i < watched.size(); i++) if (revisions.get(i) != null) watched.get(i).externalChange(revisions.get(i));
                });
            });
        } catch (RejectedExecutionException stopped) {
            running = false;
        }
    }
}
```

- [ ] **Step 4: Run it to verify it passes**

Run: `./gradlew :jasper-plugin-editor:test --tests '*LocalWatcherTest'`
Expected: PASS.

- [ ] **Step 5: Run the full check and commit**

Run: `./gradlew check`
Expected: BUILD SUCCESSFUL.

```bash
git add plugins/editor
git commit -F - <<'EOF'
feat(editor): poll open local files for outside changes

LocalWatcher stats ready local tabs off the UI thread, one pass at a
time, and reports each revision to its session.

🤖 Generated with Claude Code at The Home Depot

Co-Authored-By: Claude <noreply@anthropic.com>
EOF
```

---
### Task 9: Settings, theme and shortcuts

**Files:**
- Create: `plugins/editor/src/main/java/dev/jasper/editor/ui/{EditorSettings,EditorTheme,KeyNames,Command,package-info}.java`
- Test: `plugins/editor/src/test/java/dev/jasper/editor/ui/{EditorSettingsTest,EditorThemeTest,KeyNamesTest}.java`

**Interfaces:**
- Produces:
  - `record EditorSettings(String fontFamily, int fontSize, int tabSize, boolean softWrap, int maxFileMib)` with `read(PluginConfig, Set<String> installedFamilies)`, `maxFileBytes()`, `font()` and `PREFERRED_FONTS`;
  - `EditorTheme()` with `apply(RSyntaxTextArea, RTextScrollPane, Variant)` and `static blend(Color, Color, double)`;
  - `KeyNames.MAC`, `KeyNames.MENU`, `KeyNames.stroke(int keyCode, int extraModifiers)`, `KeyNames.describe(KeyStroke)`;
  - `record Command(String id, String title, Optional<KeyStroke> stroke, boolean bound, Runnable run)`, where `bound` means "install the stroke in input maps".

- [ ] **Step 1: Write the failing tests**

`ui/EditorSettingsTest.java`:

```java
package dev.jasper.editor.ui;

import dev.jasper.sdk.PluginInfo;
import dev.jasper.sdk.testing.FakePluginHost;
import java.awt.Font;
import java.nio.file.Path;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.assertj.core.api.Assertions.assertThat;

class EditorSettingsTest {
    @TempDir Path home;
    private static final PluginInfo INFO = new PluginInfo("dev.jasper.editor", "Editor", "0.1.0", Set.of());

    @Test void defaultsPickTheFirstInstalledPreferredFont() {
        try (var host = new FakePluginHost(home.resolve("plugins"))) {
            var context = host.start(INFO, Set.of(), Set.of(), c -> { });
            assertThat(EditorSettings.read(context.config(), Set.of("Menlo", "JetBrains Mono")))
                .isEqualTo(new EditorSettings("JetBrains Mono", 13, 4, false, 10));
            assertThat(EditorSettings.read(context.config(), Set.of()).fontFamily()).isEqualTo("Monospaced");
            assertThat(host.reports()).isEmpty();
        }
    }

    @Test void badValuesAreReportedAndTheirDefaultsUsed() {
        try (var host = new FakePluginHost(home.resolve("plugins"))) {
            host.setConfig("dev.jasper.editor", Map.of("font_family", "Nope Mono", "font_size", 99L, "tab_size", "four",
                "soft_wrap", true, "max_file_mib", 20L));
            var context = host.start(INFO, Set.of(), Set.of(), c -> { });
            var settings = EditorSettings.read(context.config(), Set.of("Menlo", "Consolas"));
            assertThat(settings).isEqualTo(new EditorSettings("Menlo", 13, 4, true, 20));
            assertThat(settings.maxFileBytes()).isEqualTo(20L * 1024 * 1024);
            assertThat(settings.font()).isEqualTo(new Font("Menlo", Font.PLAIN, 13));
            assertThat(host.reports()).containsExactly(
                "dev.jasper.editor: font_family: No installed font family is named \"Nope Mono\"; using Menlo.",
                "dev.jasper.editor: font_size: Use 8 to 48; using 13.",
                "dev.jasper.editor: tab_size: Use a whole number from 1 to 16; using 4.");
        }
    }
}
```

`ui/EditorThemeTest.java`:

```java
package dev.jasper.editor.ui;

import dev.jasper.sdk.Variant;
import java.awt.Color;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.swing.UIManager;
import javax.swing.plaf.ColorUIResource;
import org.fife.ui.rsyntaxtextarea.RSyntaxTextArea;
import org.fife.ui.rsyntaxtextarea.Token;
import org.fife.ui.rtextarea.RTextScrollPane;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class EditorThemeTest {
    private static final List<String> KEYS = List.of("TextArea.background", "TextArea.foreground", "TextArea.caretForeground",
        "TextArea.selectionBackground", "Panel.background", "Label.disabledForeground");
    private final Map<String, Object> saved = new HashMap<>();

    @BeforeEach void install() {
        for (String key : KEYS) saved.put(key, UIManager.get(key));
        UIManager.put("TextArea.background", new ColorUIResource(0x2B2B2B));
        UIManager.put("TextArea.foreground", new ColorUIResource(0xA9B7C6));
        UIManager.put("TextArea.caretForeground", new ColorUIResource(0xBBBBBB));
        UIManager.put("TextArea.selectionBackground", new ColorUIResource(0x214283));
        UIManager.put("Panel.background", new ColorUIResource(0x3C3F41));
        UIManager.put("Label.disabledForeground", new ColorUIResource(0x777777));
    }

    @AfterEach void restore() { saved.forEach(UIManager::put); }

    @Test void tokenColoursFollowTheVariantAndSurfacesFollowTheJasperTheme() {
        var area = new RSyntaxTextArea();
        var scroll = new RTextScrollPane(area);
        var theme = new EditorTheme();
        theme.apply(area, scroll, Variant.DARK);
        assertThat(area.getBackground()).isEqualTo(new Color(0x2B2B2B));
        assertThat(area.getCaretColor()).isEqualTo(new Color(0xBBBBBB));
        assertThat(area.getSelectionColor()).isEqualTo(new Color(0x214283));
        assertThat(area.getCurrentLineHighlightColor()).isEqualTo(EditorTheme.blend(new Color(0x2B2B2B), new Color(0xA9B7C6), 0.06));
        assertThat(scroll.getGutter().getBackground()).isEqualTo(new Color(0x3C3F41));
        assertThat(scroll.getGutter().getLineNumberColor()).isEqualTo(new Color(0x777777));
        Color darkKeyword = area.getSyntaxScheme().getStyle(Token.RESERVED_WORD).foreground;
        theme.apply(area, scroll, Variant.LIGHT);
        assertThat(area.getSyntaxScheme().getStyle(Token.RESERVED_WORD).foreground).isNotEqualTo(darkKeyword);
        assertThat(area.getBackground()).as("the surface still follows Jasper").isEqualTo(new Color(0x2B2B2B));
    }

    @Test void blendMovesPartWayTowardTheSecondColour() {
        assertThat(EditorTheme.blend(Color.BLACK, Color.WHITE, 0.5)).isEqualTo(new Color(128, 128, 128));
    }
}
```

`ui/KeyNamesTest.java`:

```java
package dev.jasper.editor.ui;

import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import javax.swing.KeyStroke;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class KeyNamesTest {
    @Test void macUsesSymbolsAndOtherPlatformsSpellModifiersOut() {
        assertThat(KeyNames.describe(KeyStroke.getKeyStroke(KeyEvent.VK_S, InputEvent.META_DOWN_MASK | InputEvent.ALT_DOWN_MASK), true)).isEqualTo("⌥⌘S");
        assertThat(KeyNames.describe(KeyStroke.getKeyStroke(KeyEvent.VK_SLASH, InputEvent.META_DOWN_MASK), true)).isEqualTo("⌘/");
        assertThat(KeyNames.describe(KeyStroke.getKeyStroke(KeyEvent.VK_CLOSE_BRACKET, InputEvent.CTRL_DOWN_MASK | InputEvent.SHIFT_DOWN_MASK), false))
            .isEqualTo("Ctrl+Shift+]");
        assertThat(KeyNames.stroke(KeyEvent.VK_O, 0).getModifiers() & KeyNames.MENU).isEqualTo(KeyNames.MENU);
    }
}
```

- [ ] **Step 2: Run them to verify they fail**

Run: `./gradlew :jasper-plugin-editor:test --tests 'dev.jasper.editor.ui.*'`
Expected: compilation fails with `cannot find symbol: class EditorSettings`.

- [ ] **Step 3: Write the classes**

`ui/package-info.java`:

```java
/** The Editor window: settings, theme, the tab view, find bar, dialogs and pickers. Swing on the UI thread; the SDK owns the frame. */
package dev.jasper.editor.ui;
```

`ui/EditorSettings.java`:

```java
package dev.jasper.editor.ui;

import dev.jasper.sdk.plugin.PluginConfig;
import java.awt.Font;
import java.util.List;
import java.util.OptionalLong;
import java.util.Set;

/**
 * The Editor's settings file (spec 7); bad values are reported and their defaults used.
 *
 * @param fontFamily the editor font's family
 * @param fontSize points, 8 to 48
 * @param tabSize columns per tab stop, 1 to 16
 * @param softWrap whether new tabs wrap long lines
 * @param maxFileMib the largest file opened, 1 to 256 MiB
 */
public record EditorSettings(String fontFamily, int fontSize, int tabSize, boolean softWrap, int maxFileMib) {
    /** Tried in order for the default font. */
    public static final List<String> PREFERRED_FONTS = List.of("JetBrains Mono", "Menlo", "Consolas", "DejaVu Sans Mono");

    /**
     * @param config the plugin's settings
     * @param installed the installed font families
     * @return the settings, reporting each bad value under its key
     */
    public static EditorSettings read(PluginConfig config, Set<String> installed) {
        String font = PREFERRED_FONTS.stream().filter(installed::contains).findFirst().orElse("Monospaced");
        var family = config.string("font_family");
        if (family.isPresent()) {
            if (installed.contains(family.get())) font = family.get();
            else config.report("font_family", "No installed font family is named \"" + family.get() + "\"; using " + font + ".");
        } else if (present(config, "font_family")) config.report("font_family", "Use a font family name in quotes; using " + font + ".");
        int size = number(config, "font_size", 13, 8, 48);
        int tab = number(config, "tab_size", 4, 1, 16);
        boolean wrap = flag(config, "soft_wrap", false);
        int max = number(config, "max_file_mib", 10, 1, 256);
        return new EditorSettings(font, size, tab, wrap, max);
    }

    /** @return {@link #maxFileMib()} in bytes */
    public long maxFileBytes() { return maxFileMib * 1024L * 1024L; }

    /** @return the plain editor font */
    public Font font() { return new Font(fontFamily, Font.PLAIN, fontSize); }

    private static int number(PluginConfig config, String key, int fallback, int min, int max) {
        OptionalLong value = config.integer(key);
        if (value.isEmpty()) {
            if (present(config, key)) config.report(key, "Use a whole number from " + min + " to " + max + "; using " + fallback + ".");
            return fallback;
        }
        if (value.getAsLong() < min || value.getAsLong() > max) {
            config.report(key, "Use " + min + " to " + max + "; using " + fallback + ".");
            return fallback;
        }
        return (int) value.getAsLong();
    }

    private static boolean flag(PluginConfig config, String key, boolean fallback) {
        var value = config.bool(key);
        if (value.isPresent()) return value.get();
        if (present(config, key)) config.report(key, "Use true or false; using " + fallback + ".");
        return fallback;
    }

    /** A value another accessor can see is present but of the wrong type; a float has no accessor and counts as absent. */
    private static boolean present(PluginConfig config, String key) {
        return config.string(key).isPresent() || config.integer(key).isPresent() || config.bool(key).isPresent()
            || !config.stringList(key).isEmpty() || config.table(key).isPresent();
    }
}
```

`ui/EditorTheme.java`:

```java
package dev.jasper.editor.ui;

import dev.jasper.sdk.Variant;
import java.awt.Color;
import java.awt.Font;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import javax.swing.UIManager;
import org.fife.ui.rsyntaxtextarea.RSyntaxTextArea;
import org.fife.ui.rsyntaxtextarea.Theme;
import org.fife.ui.rtextarea.Gutter;
import org.fife.ui.rtextarea.RTextScrollPane;

/**
 * Editor colours (spec 3.4): token colours from RSyntaxTextArea's {@code idea.xml} or {@code dark.xml}, and every
 * surface from the running Jasper theme's {@code UIManager} colours.
 */
public final class EditorTheme {
    private final Theme light = load("idea.xml");
    private final Theme dark = load("dark.xml");

    /**
     * @param area the text area
     * @param scroll its scroll pane, whose gutter is coloured too
     * @param variant Jasper's light or dark look
     */
    public void apply(RSyntaxTextArea area, RTextScrollPane scroll, Variant variant) {
        Font font = area.getFont();
        (variant == Variant.DARK ? dark : light).apply(area);
        area.setFont(font);
        Color background = color("TextArea.background", area.getBackground());
        Color foreground = color("TextArea.foreground", area.getForeground());
        area.setBackground(background);
        area.setForeground(foreground);
        area.setCaretColor(color("TextArea.caretForeground", foreground));
        area.setSelectionColor(color("TextArea.selectionBackground", area.getSelectionColor()));
        area.setCurrentLineHighlightColor(blend(background, foreground, 0.06));
        Gutter gutter = scroll.getGutter();
        Color panel = color("Panel.background", background);
        gutter.setBackground(panel);
        gutter.setBorderColor(panel);
        gutter.setFoldBackground(panel);
        gutter.setLineNumberColor(color("Label.disabledForeground", foreground));
    }

    /**
     * @param base the starting colour
     * @param toward the colour to move toward
     * @param amount 0 keeps {@code base}, 1 gives {@code toward}
     * @return the blend
     */
    public static Color blend(Color base, Color toward, double amount) {
        return new Color((int) Math.round(base.getRed() + (toward.getRed() - base.getRed()) * amount),
            (int) Math.round(base.getGreen() + (toward.getGreen() - base.getGreen()) * amount),
            (int) Math.round(base.getBlue() + (toward.getBlue() - base.getBlue()) * amount));
    }

    /** A plain copy, so a later {@code updateUI} does not replace it with the look and feel's default. */
    private static Color color(String key, Color fallback) {
        Color value = UIManager.getColor(key);
        return value == null ? fallback : new Color(value.getRGB(), true);
    }

    private static Theme load(String name) {
        try (InputStream in = RSyntaxTextArea.class.getResourceAsStream("/org/fife/ui/rsyntaxtextarea/themes/" + name)) {
            if (in == null) throw new IOException("RSyntaxTextArea has no theme " + name);
            return Theme.load(in);
        } catch (IOException failure) {
            throw new UncheckedIOException(failure);
        }
    }
}
```

`ui/KeyNames.java`:

```java
package dev.jasper.editor.ui;

import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.util.Locale;
import javax.swing.KeyStroke;

/** Shortcuts with the platform's menu modifier, and how they read. Uses no Toolkit, so it works headless. */
public final class KeyNames {
    /** Whether this is macOS. */
    public static final boolean MAC = System.getProperty("os.name", "").toLowerCase(Locale.ROOT).startsWith("mac");
    /** Cmd on macOS, Ctrl elsewhere. */
    public static final int MENU = MAC ? InputEvent.META_DOWN_MASK : InputEvent.CTRL_DOWN_MASK;

    private KeyNames() { }

    /**
     * @param keyCode a {@link KeyEvent} key code
     * @param extraModifiers modifiers besides the menu modifier, such as {@link InputEvent#SHIFT_DOWN_MASK}
     * @return the stroke
     */
    public static KeyStroke stroke(int keyCode, int extraModifiers) { return KeyStroke.getKeyStroke(keyCode, MENU | extraModifiers); }

    /**
     * @param stroke a shortcut
     * @return how it reads on this platform, such as "⇧⌘S" or "Ctrl+Shift+S"
     */
    public static String describe(KeyStroke stroke) { return describe(stroke, MAC); }

    static String describe(KeyStroke stroke, boolean mac) {
        int modifiers = stroke.getModifiers();
        StringBuilder out = new StringBuilder();
        if (mac) {
            if ((modifiers & InputEvent.CTRL_DOWN_MASK) != 0) out.append('⌃');
            if ((modifiers & InputEvent.ALT_DOWN_MASK) != 0) out.append('⌥');
            if ((modifiers & InputEvent.SHIFT_DOWN_MASK) != 0) out.append('⇧');
            if ((modifiers & InputEvent.META_DOWN_MASK) != 0) out.append('⌘');
        } else {
            if ((modifiers & InputEvent.CTRL_DOWN_MASK) != 0) out.append("Ctrl+");
            if ((modifiers & InputEvent.ALT_DOWN_MASK) != 0) out.append("Alt+");
            if ((modifiers & InputEvent.SHIFT_DOWN_MASK) != 0) out.append("Shift+");
            if ((modifiers & InputEvent.META_DOWN_MASK) != 0) out.append("Meta+");
        }
        return out.append(key(stroke.getKeyCode())).toString();
    }

    private static String key(int code) {
        return switch (code) {
            case KeyEvent.VK_SLASH -> "/";
            case KeyEvent.VK_OPEN_BRACKET -> "[";
            case KeyEvent.VK_CLOSE_BRACKET -> "]";
            default -> code >= KeyEvent.VK_A && code <= KeyEvent.VK_Z || code >= KeyEvent.VK_0 && code <= KeyEvent.VK_9
                ? String.valueOf((char) code) : KeyEvent.getKeyText(code);
        };
    }
}
```

`ui/Command.java`:

```java
package dev.jasper.editor.ui;

import java.util.Optional;
import javax.swing.KeyStroke;

/**
 * An Editor window command: listed in the ⋯ menu, and bound in the text areas when {@code bound}.
 *
 * @param id the action-map key
 * @param title the menu title
 * @param stroke its shortcut, shown in menus
 * @param bound whether the shortcut goes in input maps; Close Tab's Cmd/Ctrl+W does not (the window's menu owns it)
 * @param run what it does, on the UI thread
 */
public record Command(String id, String title, Optional<KeyStroke> stroke, boolean bound, Runnable run) { }
```

- [ ] **Step 4: Run the tests to verify they pass**

Run: `./gradlew :jasper-plugin-editor:test --tests 'dev.jasper.editor.ui.*'`
Expected: PASS.

- [ ] **Step 5: Run the full check and commit**

Run: `./gradlew check`
Expected: BUILD SUCCESSFUL.

```bash
git add plugins/editor
git commit -F - <<'EOF'
feat(editor): settings, theme colours and shortcuts

EditorSettings reads and reports the settings file; EditorTheme takes
token colours from idea.xml or dark.xml and surfaces from the Jasper
theme; KeyNames builds and describes menu-modifier shortcuts headlessly.

🤖 Generated with Claude Code at The Home Depot

Co-Authored-By: Claude <noreply@anthropic.com>
EOF
```

---

### Task 10: The tab view and the status line

**Files:**
- Create: `plugins/editor/src/main/java/dev/jasper/editor/ui/{EditorTab,StatusLine}.java`
- Test: `plugins/editor/src/test/java/dev/jasper/editor/ui/{EditorTabTest,StatusLineTest}.java`

**Interfaces:**
- Consumes: `DocumentSession` (Task 7), `EditorSettings`, `EditorTheme`, `Command` (Task 9), and `Languages` (Task 5).
- Produces:
  - `EditorTab(DocumentSession session, Consumer<EditorTab> close, boolean debounce)`, a `JPanel`;
  - `EditorTab.session()`, `area() -> RSyntaxTextArea`, `scroll() -> RTextScrollPane`;
  - `EditorTab.apply(EditorSettings, EditorTheme, Variant)`, `bind(List<Command>)`, `addToPopup(List<Command>)`;
  - `static Action EditorTab.action(Command)`;
  - package-private test accessors: `showingText()`, `stateText()`, `retryButton()`, `dismissButton()`, `bannerVisible()`, `bannerText()`, `bannerFirst()`, `bannerSecond()`;
  - `StatusLine(Runnable chooseLanguage, Runnable toggleWrap)` with `display(Optional<EditorTab> tab, int tabSize)`;
  - package-private `positionText()`, `languageText()`, `encodingText()`, `separatorText()`, `indentText()`, `noticeText()`, `wrapSelected()`.

- [ ] **Step 1: Write the failing tests**

`ui/EditorTabTest.java`:

```java
package dev.jasper.editor.ui;

import dev.jasper.editor.local.LocalDocument;
import dev.jasper.editor.local.LocalFiles;
import dev.jasper.editor.session.DocumentSession;
import dev.jasper.editor.session.Question;
import dev.jasper.editor.session.SessionEnvironment;
import dev.jasper.editor.syntax.Languages;
import dev.jasper.sdk.Variant;
import java.awt.event.ActionEvent;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import javax.swing.JComponent;
import javax.swing.JMenuItem;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.assertj.core.api.Assertions.assertThat;

class EditorTabTest {
    @TempDir Path folder;
    private final List<Runnable> background = new ArrayList<>();
    private final SessionEnvironment environment = new SessionEnvironment(background::add, Runnable::run, Question::dismissed, () -> 1 << 20);
    private final List<EditorTab> closed = new ArrayList<>();

    @BeforeEach void languages() { Languages.register(); }

    private void drain() { while (!background.isEmpty()) background.removeFirst().run(); }

    private EditorTab open(Path file) {
        var tab = new EditorTab(new DocumentSession(new LocalDocument(file, background::add), environment), closed::add, false);
        tab.session().load();
        return tab;
    }

    @Test void showsLoadingThenTheTextInItsLanguage() throws Exception {
        var tab = open(Files.writeString(folder.resolve("a.toml"), "a = 1\n"));
        assertThat(tab.showingText()).isFalse();
        assertThat(tab.stateText()).isEqualTo("Loading a.toml…");
        assertThat(tab.dismissButton().getText()).isEqualTo("Cancel");
        drain();
        assertThat(tab.showingText()).isTrue();
        assertThat(tab.area().getText()).isEqualTo("a = 1\n");
        assertThat(tab.area().getSyntaxEditingStyle()).isEqualTo(Languages.TOML);
        assertThat(tab.area().canUndo()).as("loading is not an edit").isFalse();
        tab.area().append("b = 2\n");
        assertThat(tab.session().dirty()).isTrue();
    }

    @Test void aFailureShowsRetryAndCloseAndABinaryFileHasNoRetry() throws Exception {
        var missing = open(folder.resolve("gone.txt"));
        drain();
        assertThat(missing.stateText()).isEqualTo("gone.txt does not exist.");
        assertThat(missing.retryButton().isVisible()).isTrue();
        missing.dismissButton().doClick();
        assertThat(closed).containsExactly(missing);
        var binary = open(Files.write(folder.resolve("x.bin"), new byte[] {1, 0, 2}));
        drain();
        assertThat(binary.retryButton().isVisible()).isFalse();
    }

    @Test void theBannerOffersReloadOrKeepMineAndCloseOrKeep() throws Exception {
        Path file = Files.writeString(folder.resolve("b.yaml"), "a: 1\n");
        var tab = open(file);
        drain();
        tab.area().setText("a: mine\n");
        Files.writeString(file, "a: 22\n");
        tab.session().externalChange(LocalFiles.revision(file));
        assertThat(tab.bannerVisible()).isTrue();
        assertThat(tab.bannerText()).isEqualTo("b.yaml changed on disk.");
        assertThat(tab.bannerFirst().getText()).isEqualTo("Reload");
        assertThat(tab.bannerSecond().getText()).isEqualTo("Keep Mine");
        tab.bannerFirst().doClick();
        drain();
        assertThat(tab.area().getText()).isEqualTo("a: 22\n");
        assertThat(tab.bannerVisible()).isFalse();
        Files.delete(file);
        tab.session().externalChange(Optional.empty());
        assertThat(tab.bannerText()).isEqualTo("b.yaml was deleted.");
        tab.bannerSecond().doClick();
        assertThat(tab.session().dirty()).isTrue();
        assertThat(tab.bannerVisible()).isFalse();
        tab.session().externalChange(Optional.empty());
        assertThat(closed).isEmpty();
    }

    @Test void commandsOverrideTheTextAreasOwnBindingsAndJoinItsMenu() {
        var tab = new EditorTab(DocumentSession.untitled("Untitled", environment), closed::add, false);
        int[] ran = {0};
        var saveAs = KeyNames.stroke(KeyEvent.VK_S, InputEvent.SHIFT_DOWN_MASK);
        tab.bind(List.of(new Command("save-as", "Save As…", Optional.of(saveAs), true, () -> ran[0]++),
            new Command("close-tab", "Close Tab", Optional.of(KeyNames.stroke(KeyEvent.VK_W, 0)), false, () -> ran[0] += 10)));
        Object key = tab.area().getInputMap(JComponent.WHEN_FOCUSED).get(saveAs);
        assertThat(key).as("RSyntaxTextArea's own binding for this stroke is overridden").isEqualTo("save-as");
        tab.area().getActionMap().get(key).actionPerformed(new ActionEvent(tab.area(), ActionEvent.ACTION_PERFORMED, "save-as"));
        assertThat(ran[0]).isEqualTo(1);
        assertThat(tab.area().getInputMap(JComponent.WHEN_FOCUSED).get(KeyNames.stroke(KeyEvent.VK_W, 0))).as("unbound commands stay out").isNotEqualTo("close-tab");
        tab.addToPopup(List.of(new Command("save", "Save", Optional.empty(), true, () -> ran[0]++)));
        assertThat(Arrays.stream(tab.area().getPopupMenu().getComponents()).filter(JMenuItem.class::isInstance)
            .map(item -> ((JMenuItem) item).getText())).contains("Save");
    }

    @Test void settingsApplyTheFontAndTabSize() {
        var tab = new EditorTab(DocumentSession.untitled("Untitled", environment), closed::add, false);
        tab.apply(new EditorSettings("Monospaced", 17, 2, false, 10), new EditorTheme(), Variant.DARK);
        assertThat(tab.area().getFont().getSize()).isEqualTo(17);
        assertThat(tab.area().getTabSize()).isEqualTo(2);
    }
}
```

`ui/StatusLineTest.java`:

```java
package dev.jasper.editor.ui;

import dev.jasper.editor.session.DocumentSession;
import dev.jasper.editor.session.Question;
import dev.jasper.editor.session.SessionEnvironment;
import dev.jasper.editor.syntax.Languages;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class StatusLineTest {
    private final SessionEnvironment environment = new SessionEnvironment(Runnable::run, Runnable::run, Question::dismissed, () -> 1024);

    @Test void describesTheCaretLanguageEncodingEndingsIndentationAndWrap() {
        Languages.register();
        var session = DocumentSession.untitled("Untitled", environment);
        var tab = new EditorTab(session, closing -> { }, false);
        tab.area().setText("abc\ndef");
        tab.area().setCaretPosition(6);
        session.setStyle(Languages.TOML);
        session.setWrap(true);
        int[] clicked = {0, 0};
        var status = new StatusLine(() -> clicked[0]++, () -> clicked[1]++);
        status.display(Optional.of(tab), 4);
        assertThat(status.positionText()).isEqualTo("Ln 2, Col 3");
        assertThat(status.languageText()).isEqualTo("TOML");
        assertThat(status.encodingText()).isEqualTo("UTF-8");
        assertThat(status.separatorText()).isEqualTo("LF");
        assertThat(status.indentText()).isEqualTo("Spaces: 4");
        assertThat(status.wrapSelected()).isTrue();
        assertThat(status.noticeText()).isEmpty();
        status.display(Optional.empty(), 4);
        assertThat(status.noticeText()).isEmpty();
    }
}
```

- [ ] **Step 2: Run them to verify they fail**

Run: `./gradlew :jasper-plugin-editor:test --tests '*EditorTabTest' --tests '*StatusLineTest'`
Expected: compilation fails with `cannot find symbol: class EditorTab`.

- [ ] **Step 3: Write the tab view**

`ui/EditorTab.java`:

```java
package dev.jasper.editor.ui;

import dev.jasper.editor.session.DocumentSession;
import dev.jasper.sdk.Variant;
import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Color;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridBagLayout;
import java.awt.event.ActionEvent;
import java.util.List;
import java.util.function.Consumer;
import javax.swing.AbstractAction;
import javax.swing.Action;
import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.Timer;
import javax.swing.UIManager;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.text.BadLocationException;
import org.fife.ui.rsyntaxtextarea.RSyntaxTextArea;
import org.fife.ui.rtextarea.RTextScrollPane;

/** One tab's view (spec 2, 2.3): the text area bound to its session, the loading and failure states, and the banner. */
public final class EditorTab extends JPanel {
    private static final String TEXT = "text", STATE = "state";
    private final DocumentSession session;
    private final RSyntaxTextArea area = new RSyntaxTextArea();
    private final RTextScrollPane scroll;
    private final CardLayout cards = new CardLayout();
    private final JPanel body = new JPanel(cards);
    private final JLabel stateText = new JLabel();
    private final JButton retry = new JButton("Retry");
    private final JButton dismiss = new JButton("Close");
    private final JPanel banner = new JPanel(new BorderLayout(8, 0));
    private final JLabel bannerText = new JLabel();
    private final JButton bannerFirst = new JButton();
    private final JButton bannerSecond = new JButton();
    private final Timer edits;
    private final boolean debounce;
    private boolean replacing;
    private String showing = STATE;

    /**
     * @param session the tab's document
     * @param close closes this tab (Cancel, Close and the deleted banner's Close)
     * @param debounce whether edits reach the session 300 ms after typing stops; tests pass false for at once
     */
    public EditorTab(DocumentSession session, Consumer<EditorTab> close, boolean debounce) {
        super(new BorderLayout());
        this.session = session;
        this.debounce = debounce;
        area.setCodeFoldingEnabled(true);
        area.setMarkOccurrences(true);
        area.setBracketMatchingEnabled(true);
        area.setAnimateBracketMatching(false);
        area.setHighlightCurrentLine(true);
        area.setAntiAliasingEnabled(true);
        scroll = new RTextScrollPane(area);
        scroll.setLineNumbersEnabled(true);
        scroll.setFoldIndicatorEnabled(true);
        scroll.setBorder(BorderFactory.createEmptyBorder());
        edits = new Timer(300, event -> session.textEdited());
        edits.setRepeats(false);
        area.getDocument().addDocumentListener(new DocumentListener() {
            @Override public void insertUpdate(DocumentEvent event) { edited(); }
            @Override public void removeUpdate(DocumentEvent event) { edited(); }
            @Override public void changedUpdate(DocumentEvent event) { }
        });
        session.attach(area::getText, this::replaceText);
        stateText.putClientProperty("html.disable", true);
        bannerText.putClientProperty("html.disable", true);
        stateText.setAlignmentX(CENTER_ALIGNMENT);
        var buttons = new JPanel(new FlowLayout(FlowLayout.CENTER, 8, 8));
        buttons.setOpaque(false);
        buttons.add(retry);
        buttons.add(dismiss);
        var stack = new JPanel();
        stack.setLayout(new BoxLayout(stack, BoxLayout.Y_AXIS));
        stack.setOpaque(false);
        stack.add(stateText);
        stack.add(buttons);
        var state = new JPanel(new GridBagLayout());
        state.add(stack);
        body.add(scroll, TEXT);
        body.add(state, STATE);
        retry.addActionListener(event -> session.load());
        dismiss.addActionListener(event -> { session.cancelLoad(); close.accept(this); });
        var bannerButtons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
        bannerButtons.setOpaque(false);
        bannerButtons.add(bannerFirst);
        bannerButtons.add(bannerSecond);
        banner.add(bannerText, BorderLayout.CENTER);
        banner.add(bannerButtons, BorderLayout.EAST);
        bannerFirst.addActionListener(event -> {
            if (session.banner() == DocumentSession.Banner.DELETED) close.accept(this);
            else session.reloadFromBanner();
        });
        bannerSecond.addActionListener(event -> {
            if (session.banner() == DocumentSession.Banner.DELETED) session.keepDeleted();
            else session.keepMine();
        });
        add(banner, BorderLayout.NORTH);
        add(body, BorderLayout.CENTER);
        session.addListener(this::refresh);
        refresh();
    }

    public DocumentSession session() { return session; }
    public RSyntaxTextArea area() { return area; }
    public RTextScrollPane scroll() { return scroll; }

    /**
     * @param settings font and tab size
     * @param theme the colours
     * @param variant Jasper's light or dark look
     */
    public void apply(EditorSettings settings, EditorTheme theme, Variant variant) {
        theme.apply(area, scroll, variant);
        Font font = settings.font();
        area.setFont(font);
        scroll.getGutter().setLineNumberFont(font);
        area.setTabSize(settings.tabSize());
        Color rule = UIManager.getColor("Separator.foreground");
        banner.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createMatteBorder(0, 0, 1, 0, rule == null ? Color.GRAY : rule), BorderFactory.createEmptyBorder(6, 10, 6, 10)));
    }

    /** @param commands bound in the text area's own map, which overrides RSyntaxTextArea's defaults for the same strokes */
    public void bind(List<Command> commands) {
        for (Command command : commands) {
            if (!command.bound() || command.stroke().isEmpty()) continue;
            area.getInputMap(JComponent.WHEN_FOCUSED).put(command.stroke().get(), command.id());
            area.getActionMap().put(command.id(), action(command));
        }
    }

    /** @param commands added below RSyntaxTextArea's own right-click items */
    public void addToPopup(List<Command> commands) {
        JPopupMenu popup = area.getPopupMenu();
        popup.addSeparator();
        for (Command command : commands) {
            var item = new JMenuItem(command.title());
            command.stroke().ifPresent(item::setAccelerator);
            item.addActionListener(event -> command.run().run());
            popup.add(item);
        }
    }

    /**
     * @param command a command
     * @return a Swing action running it
     */
    public static Action action(Command command) {
        return new AbstractAction(command.title()) {
            @Override public void actionPerformed(ActionEvent event) { command.run().run(); }
        };
    }

    boolean showingText() { return showing.equals(TEXT); }
    String stateText() { return stateText.getText(); }
    JButton retryButton() { return retry; }
    JButton dismissButton() { return dismiss; }
    boolean bannerVisible() { return banner.isVisible(); }
    String bannerText() { return bannerText.getText(); }
    JButton bannerFirst() { return bannerFirst; }
    JButton bannerSecond() { return bannerSecond; }

    private void edited() {
        if (replacing) return;
        if (debounce) edits.restart();
        else session.textEdited();
    }

    private void replaceText(String text, boolean keepPosition) {
        int line = keepPosition ? area.getCaretLineNumber() : 0;
        replacing = true;
        try { area.setText(text); }
        finally { replacing = false; }
        area.discardAllEdits();
        try { area.setCaretPosition(area.getLineStartOffset(Math.min(line, area.getLineCount() - 1))); }
        catch (BadLocationException impossible) { area.setCaretPosition(0); }
    }

    private void refresh() {
        switch (session.state()) {
            case LOADING -> {
                stateText.setText("Loading " + session.name() + session.host().map(host -> " from " + host).orElse("") + "…");
                retry.setVisible(false);
                dismiss.setText("Cancel");
                showCard(STATE);
            }
            case FAILED -> {
                stateText.setText(session.failure());
                retry.setVisible(session.retryable());
                dismiss.setText("Close");
                showCard(STATE);
            }
            case READY -> showCard(TEXT);
        }
        if (!area.getSyntaxEditingStyle().equals(session.style())) area.setSyntaxEditingStyle(session.style());
        area.setLineWrap(session.wrap());
        area.setWrapStyleWord(true);
        area.setTabsEmulated(!session.tabs());
        switch (session.banner()) {
            case NONE -> banner.setVisible(false);
            case CHANGED -> banner(session.name() + " changed on disk.", "Reload", "Keep Mine");
            case DELETED -> banner(session.name() + " was deleted.", "Close", "Keep");
        }
    }

    private void showCard(String card) {
        showing = card;
        cards.show(body, card);
    }

    private void banner(String text, String first, String second) {
        bannerText.setText(text);
        bannerFirst.setText(first);
        bannerSecond.setText(second);
        banner.setVisible(true);
    }
}
```

`ui/StatusLine.java`:

```java
package dev.jasper.editor.ui;

import dev.jasper.editor.session.DocumentSession;
import dev.jasper.editor.syntax.Languages;
import java.awt.FlowLayout;
import java.util.List;
import java.util.Optional;
import javax.swing.AbstractButton;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JToggleButton;
import javax.swing.text.BadLocationException;
import org.fife.ui.rsyntaxtextarea.RSyntaxTextArea;

/** {@code Ln 12, Col 4 · Python · UTF-8 · LF · Spaces: 4 · Wrap}; the language and Wrap are clickable (spec 2). */
public final class StatusLine extends JPanel {
    private final JLabel notice = label(), position = label(), encoding = label(), separator = label(), indent = label();
    private final JButton language = new JButton();
    private final JToggleButton wrap = new JToggleButton("Wrap");

    /**
     * @param chooseLanguage opens the language picker
     * @param toggleWrap toggles soft wrap for the current tab
     */
    public StatusLine(Runnable chooseLanguage, Runnable toggleWrap) {
        super(new FlowLayout(FlowLayout.RIGHT, 10, 2));
        language.addActionListener(event -> chooseLanguage.run());
        wrap.addActionListener(event -> toggleWrap.run());
        for (AbstractButton button : List.of(language, wrap)) {
            button.putClientProperty("JButton.buttonType", "borderless");
            button.setFocusable(false);
        }
        add(notice);
        add(position);
        add(language);
        add(encoding);
        add(separator);
        add(indent);
        add(wrap);
        display(Optional.empty(), 4);
    }

    /**
     * @param tab the selected tab, or empty
     * @param tabSize the settings' tab size
     */
    public void display(Optional<EditorTab> tab, int tabSize) {
        boolean ready = tab.isPresent() && tab.get().session().state() == DocumentSession.State.READY;
        for (JComponent part : List.of(position, language, encoding, separator, indent, wrap)) part.setVisible(ready);
        if (tab.isEmpty()) { notice.setText(""); return; }
        DocumentSession session = tab.get().session();
        notice.setText(session.saving() ? "Saving…" : session.notice().orElse(""));
        if (!ready) return;
        RSyntaxTextArea area = tab.get().area();
        int line = area.getCaretLineNumber();
        int start;
        try { start = area.getLineStartOffset(line); }
        catch (BadLocationException impossible) { start = 0; }
        position.setText("Ln " + (line + 1) + ", Col " + (area.getCaretPosition() - start + 1));
        language.setText(Languages.name(session.style()));
        encoding.setText(session.format().label());
        String ending = session.format().separator().label();
        separator.setText(session.mixed() ? "Mixed → " + ending : ending);
        indent.setText((session.tabs() ? "Tab: " : "Spaces: ") + tabSize);
        wrap.setSelected(session.wrap());
    }

    String positionText() { return position.getText(); }
    String languageText() { return language.getText(); }
    String encodingText() { return encoding.getText(); }
    String separatorText() { return separator.getText(); }
    String indentText() { return indent.getText(); }
    String noticeText() { return notice.getText(); }
    boolean wrapSelected() { return wrap.isSelected(); }

    private static JLabel label() {
        var label = new JLabel();
        label.putClientProperty("html.disable", true);
        return label;
    }
}
```

- [ ] **Step 4: Run the tests to verify they pass**

Run: `./gradlew :jasper-plugin-editor:test --tests '*EditorTabTest' --tests '*StatusLineTest'`
Expected: PASS.

- [ ] **Step 5: Run the full check and commit**

Run: `./gradlew check`
Expected: BUILD SUCCESSFUL.

```bash
git add plugins/editor
git commit -F - <<'EOF'
feat(editor): the tab view and the status line

EditorTab binds RSyntaxTextArea to its session: loading and failure
states, the outside-change banner, commands that override RSyntaxTextArea's
own bindings; StatusLine shows position, language, encoding, endings and wrap.

🤖 Generated with Claude Code at The Home Depot

Co-Authored-By: Claude <noreply@anthropic.com>
EOF
```

---

### Task 11: The find and replace bar

**Files:**
- Create: `plugins/editor/src/main/java/dev/jasper/editor/ui/FindBar.java`
- Test: `plugins/editor/src/test/java/dev/jasper/editor/ui/FindBarTest.java`

**Interfaces:**
- Produces:
  - `FindBar(Supplier<Optional<RSyntaxTextArea>> target)`, a `JPanel`, hidden until opened;
  - `open(boolean withReplace)`, `close()`, `next()`, `previous()`, `replaceOne()`, `replaceAll()`;
  - package-private `findField()`, `replaceField()`, `matchCase()`, `wholeWord()`, `regex()`, `status()`, `replaceVisible()`.

- [ ] **Step 1: Write the failing test**

`ui/FindBarTest.java`:

```java
package dev.jasper.editor.ui;

import java.util.Optional;
import org.fife.ui.rsyntaxtextarea.RSyntaxTextArea;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class FindBarTest {
    private final RSyntaxTextArea area = new RSyntaxTextArea("foo bar Foo foo\n");
    private final FindBar bar = new FindBar(() -> Optional.of(area));

    @Test void countsMatchesAndHonoursTheToggles() {
        assertThat(bar.isVisible()).isFalse();
        bar.open(false);
        assertThat(bar.isVisible()).isTrue();
        assertThat(bar.replaceVisible()).isFalse();
        bar.findField().setText("foo");
        assertThat(bar.status()).isEqualTo("3 matches");
        bar.matchCase().doClick();
        assertThat(bar.status()).isEqualTo("2 matches");
        bar.findField().setText("fo+");
        assertThat(bar.status()).isEqualTo("No matches");
        bar.regex().doClick();
        assertThat(bar.status()).isEqualTo("2 matches");
        bar.findField().setText("(");
        assertThat(bar.status()).isEqualTo("Invalid pattern");
        bar.regex().doClick();
        bar.matchCase().doClick();
        bar.wholeWord().doClick();
        bar.findField().setText("fo");
        assertThat(bar.status()).as("whole words only").isEqualTo("No matches");
    }

    @Test void nextAndPreviousSelectMatches() {
        area.setCaretPosition(0);
        bar.open(false);
        bar.findField().setText("foo");
        bar.next();
        assertThat(area.getSelectionStart()).isEqualTo(0);
        bar.next();
        assertThat(area.getSelectionStart()).isEqualTo(8);
        bar.previous();
        assertThat(area.getSelectionStart()).isEqualTo(0);
    }

    @Test void openingTakesTheSelectionAndClosingHidesTheBar() {
        area.select(4, 7);
        bar.open(true);
        assertThat(bar.findField().getText()).isEqualTo("bar");
        assertThat(bar.replaceVisible()).isTrue();
        bar.close();
        assertThat(bar.isVisible()).isFalse();
    }

    @Test void replaceOneAndReplaceAll() {
        area.setText("a1 a2 a3");
        area.setCaretPosition(0);
        bar.open(true);
        bar.findField().setText("a");
        bar.replaceField().setText("b");
        bar.replaceOne();
        assertThat(area.getText()).isEqualTo("b1 a2 a3");
        bar.replaceAll();
        assertThat(area.getText()).isEqualTo("b1 b2 b3");
        assertThat(bar.status()).isEqualTo("Replaced 2");
    }
}
```

- [ ] **Step 2: Run it to verify it fails**

Run: `./gradlew :jasper-plugin-editor:test --tests '*FindBarTest'`
Expected: compilation fails with `cannot find symbol: class FindBar`.

- [ ] **Step 3: Write the find bar**

`ui/FindBar.java`:

```java
package dev.jasper.editor.ui;

import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.event.ActionEvent;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.util.Optional;
import java.util.function.Supplier;
import java.util.regex.PatternSyntaxException;
import javax.swing.AbstractAction;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.JToggleButton;
import javax.swing.KeyStroke;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import org.fife.ui.rsyntaxtextarea.RSyntaxTextArea;
import org.fife.ui.rtextarea.SearchContext;
import org.fife.ui.rtextarea.SearchEngine;
import org.fife.ui.rtextarea.SearchResult;

/** Find and replace in the selected tab (spec 2), using RSyntaxTextArea's {@code SearchEngine}. Hidden until opened. */
public final class FindBar extends JPanel {
    private final Supplier<Optional<RSyntaxTextArea>> target;
    private final JTextField find = new JTextField(22);
    private final JTextField replace = new JTextField(22);
    private final JToggleButton matchCase = toggle("Aa", "Match case");
    private final JToggleButton wholeWord = toggle("W", "Whole word");
    private final JToggleButton regex = toggle(".*", "Regular expression");
    private final JLabel status = new JLabel();
    private final JPanel replaceRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 2));

    /** @param target the selected tab's text area, if any */
    public FindBar(Supplier<Optional<RSyntaxTextArea>> target) {
        super(new BorderLayout());
        this.target = target;
        setBorder(BorderFactory.createEmptyBorder(2, 6, 2, 6));
        find.putClientProperty("JTextField.placeholderText", "Find");
        replace.putClientProperty("JTextField.placeholderText", "Replace");
        status.putClientProperty("html.disable", true);
        var findRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 2));
        findRow.add(find);
        findRow.add(button("Previous", this::previous));
        findRow.add(button("Next", this::next));
        findRow.add(matchCase);
        findRow.add(wholeWord);
        findRow.add(regex);
        findRow.add(status);
        findRow.add(button("Close", this::close));
        replaceRow.add(replace);
        replaceRow.add(button("Replace", this::replaceOne));
        replaceRow.add(button("Replace All", this::replaceAll));
        add(findRow, BorderLayout.NORTH);
        add(replaceRow, BorderLayout.SOUTH);
        find.getDocument().addDocumentListener(new DocumentListener() {
            @Override public void insertUpdate(DocumentEvent event) { update(); }
            @Override public void removeUpdate(DocumentEvent event) { update(); }
            @Override public void changedUpdate(DocumentEvent event) { }
        });
        for (JToggleButton toggle : new JToggleButton[] {matchCase, wholeWord, regex}) toggle.addActionListener(event -> update());
        key(find, KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0), "next", this::next);
        key(find, KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, InputEvent.SHIFT_DOWN_MASK), "previous", this::previous);
        key(replace, KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0), "replace", this::replaceOne);
        for (JTextField field : new JTextField[] {find, replace}) key(field, KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), "close", this::close);
        setVisible(false);
    }

    /** @param withReplace whether the replace row shows; a one-line selection becomes the search text */
    public void open(boolean withReplace) {
        target.get().ifPresent(area -> {
            String selected = area.getSelectedText();
            if (selected != null && !selected.isEmpty() && !selected.contains("\n")) find.setText(selected);
        });
        replaceRow.setVisible(withReplace);
        setVisible(true);
        revalidate();
        find.requestFocusInWindow();
        find.selectAll();
        update();
    }

    /** Hides the bar, clears the highlights and returns focus to the text. */
    public void close() {
        setVisible(false);
        revalidate();
        target.get().ifPresent(area -> { area.clearMarkAllHighlights(); area.requestFocusInWindow(); });
    }

    public void next() { search(true); }
    public void previous() { search(false); }

    public void replaceOne() {
        target.get().ifPresent(area -> guarded(() -> {
            SearchContext context = context(true);
            context.setReplaceWith(replace.getText());
            if (SearchEngine.replace(area, context).wasFound()) update();
            else status.setText("No matches");
        }));
    }

    public void replaceAll() {
        target.get().ifPresent(area -> guarded(() -> {
            SearchContext context = context(true);
            context.setReplaceWith(replace.getText());
            status.setText("Replaced " + SearchEngine.replaceAll(area, context).getCount());
        }));
    }

    JTextField findField() { return find; }
    JTextField replaceField() { return replace; }
    JToggleButton matchCase() { return matchCase; }
    JToggleButton wholeWord() { return wholeWord; }
    JToggleButton regex() { return regex; }
    String status() { return status.getText(); }
    boolean replaceVisible() { return replaceRow.isVisible(); }

    private void update() {
        Optional<RSyntaxTextArea> area = target.get();
        if (area.isEmpty() || find.getText().isEmpty()) {
            area.ifPresent(RSyntaxTextArea::clearMarkAllHighlights);
            status.setText("");
            return;
        }
        guarded(() -> {
            int count = SearchEngine.markAll(area.get(), context(true)).getMarkedCount();
            status.setText(count == 0 ? "No matches" : count == 1 ? "1 match" : count + " matches");
        });
    }

    private void search(boolean forward) {
        if (find.getText().isEmpty()) return;
        target.get().ifPresent(area -> guarded(() -> {
            SearchResult result = SearchEngine.find(area, context(forward));
            if (!result.wasFound()) status.setText("No matches");
        }));
    }

    private SearchContext context(boolean forward) {
        var context = new SearchContext(find.getText());
        context.setMatchCase(matchCase.isSelected());
        context.setWholeWord(wholeWord.isSelected());
        context.setRegularExpression(regex.isSelected());
        context.setSearchForward(forward);
        context.setSearchWrap(true);
        context.setMarkAll(true);
        return context;
    }

    private void guarded(Runnable search) {
        try { search.run(); }
        catch (PatternSyntaxException bad) { status.setText("Invalid pattern"); }
    }

    private static JToggleButton toggle(String text, String tooltip) {
        var toggle = new JToggleButton(text);
        toggle.setToolTipText(tooltip);
        toggle.getAccessibleContext().setAccessibleName(tooltip);
        toggle.setFocusable(false);
        return toggle;
    }

    private static JButton button(String text, Runnable action) {
        var button = new JButton(text);
        button.addActionListener(event -> action.run());
        return button;
    }

    private static void key(JComponent component, KeyStroke stroke, String name, Runnable action) {
        component.getInputMap(JComponent.WHEN_FOCUSED).put(stroke, name);
        component.getActionMap().put(name, new AbstractAction() {
            @Override public void actionPerformed(ActionEvent event) { action.run(); }
        });
    }
}
```

- [ ] **Step 4: Run it to verify it passes**

Run: `./gradlew :jasper-plugin-editor:test --tests '*FindBarTest'`
Expected: PASS. If `nextAndPreviousSelectMatches` fails because RSyntaxTextArea's search starts elsewhere than the caret, record the observed starting rule in the plan's status banner and adjust only the expected offsets, not the bar.

- [ ] **Step 5: Run the full check and commit**

Run: `./gradlew check`
Expected: BUILD SUCCESSFUL.

```bash
git add plugins/editor
git commit -F - <<'EOF'
feat(editor): find and replace

FindBar counts and marks matches with case, whole-word and regular
expression toggles, steps through them and replaces one or all.

🤖 Generated with Claude Code at The Home Depot

Co-Authored-By: Claude <noreply@anthropic.com>
EOF
```

---
### Task 12: Dialogs, pickers and file drops

**Files:**
- Create: `plugins/editor/src/main/java/dev/jasper/editor/ui/{Forms,LanguagePicker,GoToLinePanel,FileDrop,DialogAsker,SaveWait}.java`
- Test: `plugins/editor/src/test/java/dev/jasper/editor/ui/{FormsTest,LanguagePickerTest,GoToLinePanelTest,FileDropTest,DialogsTest}.java`

**Interfaces:**
- Consumes: `Question` (Task 7) and `Languages` (Task 5).
- Produces:
  - `Forms.question(Question, Consumer<String>) -> JPanel`, `Forms.progress(String, JButton) -> JPanel`, `Forms.defaultButton(JComponent, JButton)` (package-private);
  - `LanguagePicker(String currentStyle, Consumer<String> chosen)` with `filter(String)`, `chooseSelected()`, and package-private `visibleNames()` and `selectedName()`;
  - `GoToLinePanel(int currentLine, int lineCount, IntConsumer go, Runnable cancel)` with `submit()` and package-private `field()`;
  - `FileDrop(TransferHandler delegateOrNull, Consumer<List<Path>> open)`;
  - `DialogAsker(Windows, WindowOwner) implements Function<Question, String>`;
  - `SaveWait(Windows, WindowOwner) implements BiFunction<String, CompletableFuture<Boolean>, Boolean>`.

- [ ] **Step 1: Write the failing tests**

`ui/FormsTest.java`:

```java
package dev.jasper.editor.ui;

import dev.jasper.editor.session.Question;
import java.awt.Component;
import java.awt.Container;
import java.util.ArrayList;
import java.util.List;
import javax.swing.JButton;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class FormsTest {
    static List<JButton> buttons(Container root) {
        List<JButton> out = new ArrayList<>();
        for (Component child : root.getComponents()) {
            if (child instanceof JButton button) out.add(button);
            if (child instanceof Container container) out.addAll(buttons(container));
        }
        return out;
    }

    @Test void aQuestionHasOneButtonPerChoiceAndReportsTheOneClicked() {
        List<String> answers = new ArrayList<>();
        var content = Forms.question(new Question("Unsaved changes", "a.txt has unsaved changes.", List.of("Save All", "Discard", "Cancel")), answers::add);
        List<JButton> buttons = buttons(content);
        assertThat(buttons).extracting(JButton::getText).containsExactly("Save All", "Discard", "Cancel");
        buttons.get(1).doClick();
        assertThat(answers).containsExactly("Discard");
    }
}
```

`ui/LanguagePickerTest.java`:

```java
package dev.jasper.editor.ui;

import dev.jasper.editor.syntax.Languages;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;
import static org.fife.ui.rsyntaxtextarea.SyntaxConstants.SYNTAX_STYLE_PYTHON;

class LanguagePickerTest {
    @Test void filtersByNameAndChoosesTheSelectedStyle() {
        Languages.register();
        List<String> chosen = new ArrayList<>();
        var picker = new LanguagePicker(SYNTAX_STYLE_PYTHON, chosen::add);
        assertThat(picker.selectedName()).as("the current language starts selected").isEqualTo("Python");
        picker.filter("tom");
        assertThat(picker.visibleNames()).containsExactly("TOML");
        picker.chooseSelected();
        assertThat(chosen).containsExactly(Languages.TOML);
        picker.filter("zzz");
        assertThat(picker.visibleNames()).isEmpty();
        picker.chooseSelected();
        assertThat(chosen).hasSize(1);
    }
}
```

`ui/GoToLinePanelTest.java`:

```java
package dev.jasper.editor.ui;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class GoToLinePanelTest {
    @Test void goesToAValidLineAndMarksAnInvalidOne() {
        List<Integer> lines = new ArrayList<>();
        int[] cancelled = {0};
        var panel = new GoToLinePanel(2, 10, lines::add, () -> cancelled[0]++);
        assertThat(panel.field().getText()).isEqualTo("2");
        panel.field().setText("11");
        panel.submit();
        assertThat(lines).isEmpty();
        assertThat(panel.field().getClientProperty("JComponent.outline")).isEqualTo("error");
        panel.field().setText(" 7 ");
        panel.submit();
        assertThat(lines).containsExactly(7);
        assertThat(panel.field().getClientProperty("JComponent.outline")).isNull();
        assertThat(cancelled[0]).isZero();
    }
}
```

`ui/FileDropTest.java`:

```java
package dev.jasper.editor.ui;

import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.StringSelection;
import java.awt.datatransfer.Transferable;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import javax.swing.JTextArea;
import javax.swing.TransferHandler;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.assertj.core.api.Assertions.assertThat;

class FileDropTest {
    @TempDir Path folder;

    private static Transferable files(File... files) {
        return new Transferable() {
            @Override public DataFlavor[] getTransferDataFlavors() { return new DataFlavor[] {DataFlavor.javaFileListFlavor}; }
            @Override public boolean isDataFlavorSupported(DataFlavor flavor) { return DataFlavor.javaFileListFlavor.equals(flavor); }
            @Override public Object getTransferData(DataFlavor flavor) { return List.of(files); }
        };
    }

    @Test void droppedFoldersAreIgnoredFilesOpenAndTextGoesToTheDelegate() throws Exception {
        Path file = Files.writeString(folder.resolve("a.txt"), "a");
        Path directory = Files.createDirectory(folder.resolve("d"));
        List<List<Path>> opened = new ArrayList<>();
        var target = new JTextArea();
        var drop = new FileDrop(target.getTransferHandler(), opened::add);
        assertThat(drop.importData(new TransferHandler.TransferSupport(target, files(file.toFile(), directory.toFile())))).isTrue();
        assertThat(opened).containsExactly(List.of(file));
        assertThat(drop.importData(new TransferHandler.TransferSupport(target, files(directory.toFile())))).isFalse();
        assertThat(opened).hasSize(1);
        assertThat(drop.importData(new TransferHandler.TransferSupport(target, new StringSelection("typed")))).isTrue();
        assertThat(target.getText()).isEqualTo("typed");
        assertThat(new FileDrop(null, opened::add).importData(new TransferHandler.TransferSupport(target, new StringSelection("x")))).isFalse();
    }
}
```

`ui/DialogsTest.java`:

```java
package dev.jasper.editor.ui;

import dev.jasper.editor.session.Question;
import dev.jasper.sdk.PluginInfo;
import dev.jasper.sdk.testing.FakePluginHost;
import dev.jasper.sdk.ui.WindowSpec;
import java.awt.Dimension;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.assertj.core.api.Assertions.assertThat;

class DialogsTest {
    @TempDir Path home;

    @Test void theAskerBuildsAModalDialogWhoseButtonsCloseIt() {
        try (var host = new FakePluginHost(home.resolve("plugins"))) {
            var context = host.start(new PluginInfo("dev.jasper.editor", "Editor", "0.1.0", Set.of()), Set.of(), Set.of(), c -> { });
            var owner = context.windows().create(new WindowSpec("dev.jasper.editor.window", "Editor", new Dimension(100, 100), true));
            owner.show();
            // The testkit's modal show() returns at once, so the answer is the dismissal and the dialog stays for inspection.
            assertThat(new DialogAsker(context.windows(), owner).apply(new Question("Replace", "Replace it?", List.of("Yes", "No")))).isEqualTo("No");
            var content = host.windowContent("dev.jasper.editor", "Replace").orElseThrow();
            FormsTest.buttons(content).getFirst().doClick();
            assertThat(host.windows()).containsExactly("dev.jasper.editor.window|Editor|true");
            var wait = new SaveWait(context.windows(), owner);
            assertThat(wait.apply("Saving 1 file…", CompletableFuture.completedFuture(true))).isTrue();
            assertThat(wait.apply("Saving 1 file…", CompletableFuture.completedFuture(false))).isFalse();
            assertThat(wait.apply("Saving 1 file…", CompletableFuture.failedFuture(new IllegalStateException("x")))).isFalse();
            assertThat(host.windows()).as("a finished save needs no dialog").hasSize(1);
        }
    }
}
```

- [ ] **Step 2: Run them to verify they fail**

Run: `./gradlew :jasper-plugin-editor:test --tests '*FormsTest' --tests '*LanguagePickerTest' --tests '*GoToLinePanelTest' --tests '*FileDropTest' --tests '*DialogsTest'`
Expected: compilation fails with `cannot find symbol: class Forms`.

- [ ] **Step 3: Write the classes**

`ui/Forms.java`:

```java
package dev.jasper.editor.ui;

import dev.jasper.editor.session.Question;
import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.event.HierarchyEvent;
import java.util.function.Consumer;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.JRootPane;
import javax.swing.JTextArea;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;

/** Small form helpers shared by the Editor's dialogs. */
final class Forms {
    private Forms() { }

    /** Makes the button the root pane's default while the content shows, and releases it when the content goes. */
    static void defaultButton(JComponent content, JButton button) {
        content.addHierarchyListener(event -> {
            if ((event.getChangeFlags() & HierarchyEvent.SHOWING_CHANGED) == 0) return;
            JRootPane root = SwingUtilities.getRootPane(content);
            if (root == null) return;
            if (content.isShowing()) root.setDefaultButton(button);
            else if (root.getDefaultButton() == button) root.setDefaultButton(null);
        });
    }

    /** A question's content: its message and one button per choice, the first the default. */
    static JPanel question(Question question, Consumer<String> answer) {
        var message = new JTextArea(question.message());
        message.setEditable(false);
        message.setFocusable(false);
        message.setOpaque(false);
        message.setLineWrap(true);
        message.setWrapStyleWord(true);
        message.setColumns(36);
        message.setFont(UIManager.getFont("Label.font"));
        var buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        JButton first = null;
        for (String choice : question.choices()) {
            var button = new JButton(choice);
            button.addActionListener(event -> answer.accept(choice));
            buttons.add(button);
            if (first == null) first = button;
        }
        var content = new JPanel(new BorderLayout(0, 12));
        content.setBorder(BorderFactory.createEmptyBorder(16, 16, 12, 16));
        content.add(message, BorderLayout.CENTER);
        content.add(buttons, BorderLayout.SOUTH);
        defaultButton(content, first);
        return content;
    }

    /** "Saving 3 files…" over an indeterminate bar, with Cancel. */
    static JPanel progress(String message, JButton cancel) {
        var label = new JLabel(message);
        label.putClientProperty("html.disable", true);
        var bar = new JProgressBar();
        bar.setIndeterminate(true);
        var buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0));
        buttons.add(cancel);
        var content = new JPanel(new BorderLayout(0, 10));
        content.setBorder(BorderFactory.createEmptyBorder(16, 16, 12, 16));
        content.add(label, BorderLayout.NORTH);
        content.add(bar, BorderLayout.CENTER);
        content.add(buttons, BorderLayout.SOUTH);
        return content;
    }
}
```

`ui/LanguagePicker.java`:

```java
package dev.jasper.editor.ui;

import dev.jasper.editor.syntax.Languages;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;
import javax.swing.AbstractAction;
import javax.swing.BorderFactory;
import javax.swing.DefaultListCellRenderer;
import javax.swing.DefaultListModel;
import javax.swing.JComponent;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextField;
import javax.swing.KeyStroke;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;

/** Every language by display name, with a filter field (spec 3.1); Enter or a double-click chooses. */
public final class LanguagePicker extends JPanel {
    private final List<Languages.Language> all = Languages.all();
    private final DefaultListModel<Languages.Language> shown = new DefaultListModel<>();
    private final JList<Languages.Language> list = new JList<>(shown);
    private final JTextField filter = new JTextField(24);
    private final Consumer<String> chosen;

    /**
     * @param current the tab's style, selected at first
     * @param chosen receives the chosen style
     */
    public LanguagePicker(String current, Consumer<String> chosen) {
        super(new BorderLayout(0, 8));
        this.chosen = chosen;
        setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));
        filter.putClientProperty("JTextField.placeholderText", "Filter languages");
        list.setVisibleRowCount(14);
        list.setCellRenderer(new DefaultListCellRenderer() {
            @Override public Component getListCellRendererComponent(JList<?> owner, Object value, int index, boolean selected, boolean focused) {
                super.getListCellRendererComponent(owner, ((Languages.Language) value).name(), index, selected, focused);
                putClientProperty("html.disable", true);
                return this;
            }
        });
        filter.getDocument().addDocumentListener(new DocumentListener() {
            @Override public void insertUpdate(DocumentEvent event) { filter(filter.getText()); }
            @Override public void removeUpdate(DocumentEvent event) { filter(filter.getText()); }
            @Override public void changedUpdate(DocumentEvent event) { }
        });
        bind(filter, KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0), "choose", this::chooseSelected);
        bind(filter, KeyStroke.getKeyStroke(KeyEvent.VK_DOWN, 0), "down", () -> move(1));
        bind(filter, KeyStroke.getKeyStroke(KeyEvent.VK_UP, 0), "up", () -> move(-1));
        bind(list, KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0), "choose", this::chooseSelected);
        list.addMouseListener(new MouseAdapter() {
            @Override public void mouseClicked(MouseEvent event) { if (event.getClickCount() == 2) chooseSelected(); }
        });
        add(filter, BorderLayout.NORTH);
        add(new JScrollPane(list), BorderLayout.CENTER);
        filter("");
        for (int i = 0; i < shown.size(); i++) if (shown.get(i).style().equals(current)) { list.setSelectedIndex(i); list.ensureIndexIsVisible(i); }
    }

    /** @param text shows the languages whose names contain it, ignoring case; selects the first */
    public void filter(String text) {
        String needle = text.strip().toLowerCase(Locale.ROOT);
        shown.clear();
        for (Languages.Language language : all) if (language.name().toLowerCase(Locale.ROOT).contains(needle)) shown.addElement(language);
        if (!shown.isEmpty()) list.setSelectedIndex(0);
    }

    /** Chooses the selected language, if any. */
    public void chooseSelected() {
        Languages.Language language = list.getSelectedValue();
        if (language != null) chosen.accept(language.style());
    }

    List<String> visibleNames() {
        List<String> names = new ArrayList<>();
        for (int i = 0; i < shown.size(); i++) names.add(shown.get(i).name());
        return names;
    }

    String selectedName() { return list.getSelectedValue() == null ? "" : list.getSelectedValue().name(); }

    private void move(int delta) {
        if (shown.isEmpty()) return;
        int index = Math.max(0, Math.min(shown.size() - 1, list.getSelectedIndex() + delta));
        list.setSelectedIndex(index);
        list.ensureIndexIsVisible(index);
    }

    private static void bind(JComponent component, KeyStroke stroke, String name, Runnable action) {
        component.getInputMap(JComponent.WHEN_FOCUSED).put(stroke, name);
        component.getActionMap().put(name, new AbstractAction() {
            @Override public void actionPerformed(ActionEvent event) { action.run(); }
        });
    }
}
```

`ui/GoToLinePanel.java`:

```java
package dev.jasper.editor.ui;

import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.util.function.IntConsumer;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;

/** Go to Line: a line number from 1 to the tab's line count; anything else is marked and ignored. */
public final class GoToLinePanel extends JPanel {
    private final JTextField field = new JTextField(8);
    private final int lineCount;
    private final IntConsumer go;

    /**
     * @param currentLine the caret's line, from 1
     * @param lineCount the tab's line count
     * @param go receives a valid line, from 1
     * @param cancel closes the dialog
     */
    public GoToLinePanel(int currentLine, int lineCount, IntConsumer go, Runnable cancel) {
        super(new BorderLayout(0, 10));
        this.lineCount = lineCount;
        this.go = go;
        setBorder(BorderFactory.createEmptyBorder(16, 16, 12, 16));
        field.setText(String.valueOf(currentLine));
        field.addActionListener(event -> submit());
        var ok = new JButton("Go");
        ok.addActionListener(event -> submit());
        var no = new JButton("Cancel");
        no.addActionListener(event -> cancel.run());
        var row = new JPanel(new BorderLayout(8, 0));
        row.add(new JLabel("Line (1–" + lineCount + "):"), BorderLayout.WEST);
        row.add(field, BorderLayout.CENTER);
        var buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        buttons.add(no);
        buttons.add(ok);
        add(row, BorderLayout.CENTER);
        add(buttons, BorderLayout.SOUTH);
        Forms.defaultButton(this, ok);
    }

    /** Goes to the typed line when it is valid; otherwise outlines the field in the theme's error colour. */
    public void submit() {
        try {
            int line = Integer.parseInt(field.getText().strip());
            if (line >= 1 && line <= lineCount) {
                field.putClientProperty("JComponent.outline", null);
                go.accept(line);
                return;
            }
        } catch (NumberFormatException notANumber) {
            // Marked below.
        }
        field.putClientProperty("JComponent.outline", "error");
    }

    JTextField field() { return field; }
}
```

`ui/FileDrop.java`:

```java
package dev.jasper.editor.ui;

import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.awt.datatransfer.UnsupportedFlavorException;
import java.awt.event.InputEvent;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;
import javax.swing.Icon;
import javax.swing.JComponent;
import javax.swing.TransferHandler;

/** Opens regular files dropped from the desktop and ignores folders; everything else goes to the component's own handler. */
public final class FileDrop extends TransferHandler {
    private final TransferHandler delegate;
    private final Consumer<List<Path>> open;

    /**
     * @param delegate the component's own handler (text drag and drop, copy and paste), or null for none
     * @param open receives the dropped regular files
     */
    public FileDrop(TransferHandler delegate, Consumer<List<Path>> open) {
        this.delegate = delegate;
        this.open = Objects.requireNonNull(open);
    }

    @Override public boolean canImport(TransferSupport support) {
        if (support.isDataFlavorSupported(DataFlavor.javaFileListFlavor)) return true;
        return delegate != null && delegate.canImport(support);
    }

    @Override public boolean importData(TransferSupport support) {
        if (!support.isDataFlavorSupported(DataFlavor.javaFileListFlavor)) return delegate != null && delegate.importData(support);
        try {
            List<?> dropped = (List<?>) support.getTransferable().getTransferData(DataFlavor.javaFileListFlavor);
            List<Path> files = dropped.stream().filter(File.class::isInstance).map(file -> ((File) file).toPath())
                .filter(Files::isRegularFile).toList();
            if (files.isEmpty()) return false;
            open.accept(files);
            return true;
        } catch (UnsupportedFlavorException | IOException unreadable) {
            return false;
        }
    }

    @Override public int getSourceActions(JComponent component) { return delegate == null ? NONE : delegate.getSourceActions(component); }

    @Override public void exportAsDrag(JComponent component, InputEvent event, int action) {
        if (delegate != null) delegate.exportAsDrag(component, event, action);
    }

    @Override public void exportToClipboard(JComponent component, Clipboard clipboard, int action) {
        if (delegate != null) delegate.exportToClipboard(component, clipboard, action);
    }

    @Override public Icon getVisualRepresentation(Transferable transferable) {
        return delegate == null ? null : delegate.getVisualRepresentation(transferable);
    }
}
```

`ui/DialogAsker.java`:

```java
package dev.jasper.editor.ui;

import dev.jasper.editor.session.Question;
import dev.jasper.sdk.WindowOwner;
import dev.jasper.sdk.ui.DialogSpec;
import dev.jasper.sdk.ui.PluginDialog;
import dev.jasper.sdk.ui.Windows;
import java.util.Objects;
import java.util.function.Function;

/** Asks a question in a modal dialog over the Editor window; the application's modal show() returns after it closes. */
public final class DialogAsker implements Function<Question, String> {
    private final Windows windows;
    private final WindowOwner owner;

    /**
     * @param windows the plugin's windows
     * @param owner the Editor window
     */
    public DialogAsker(Windows windows, WindowOwner owner) {
        this.windows = Objects.requireNonNull(windows);
        this.owner = Objects.requireNonNull(owner);
    }

    @Override public String apply(Question question) {
        PluginDialog dialog = windows.dialog(new DialogSpec(question.title(), owner, true));
        String[] answer = {question.dismissed()};
        dialog.setContent(Forms.question(question, choice -> { answer[0] = choice; dialog.close(); }));
        dialog.show();
        return answer[0];
    }
}
```

`ui/SaveWait.java`:

```java
package dev.jasper.editor.ui;

import dev.jasper.sdk.WindowOwner;
import dev.jasper.sdk.ui.DialogSpec;
import dev.jasper.sdk.ui.PluginDialog;
import dev.jasper.sdk.ui.Windows;
import java.awt.SecondaryLoop;
import java.awt.Toolkit;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.function.BiFunction;
import javax.swing.JButton;

/**
 * Waits for Save All inside a closing guard. A modal dialog would block the window's own prompts (conflicts,
 * Save As), since plugin dialogs are document-modal and can only be owned by windows; so this shows a
 * non-modal dialog and pumps events in a nested loop until the saves finish or the user cancels.
 */
public final class SaveWait implements BiFunction<String, CompletableFuture<Boolean>, Boolean> {
    private final Windows windows;
    private final WindowOwner owner;

    /**
     * @param windows the plugin's windows
     * @param owner the Editor window
     */
    public SaveWait(Windows windows, WindowOwner owner) {
        this.windows = Objects.requireNonNull(windows);
        this.owner = Objects.requireNonNull(owner);
    }

    @Override public Boolean apply(String message, CompletableFuture<Boolean> saves) {
        if (saves.isDone()) return succeeded(saves);
        PluginDialog dialog = windows.dialog(new DialogSpec("Saving", owner, false));
        SecondaryLoop loop = Toolkit.getDefaultToolkit().getSystemEventQueue().createSecondaryLoop();
        boolean[] cancelled = {false};
        var cancel = new JButton("Cancel");
        cancel.addActionListener(event -> { cancelled[0] = true; loop.exit(); });
        dialog.onClosed(() -> { if (!saves.isDone()) cancelled[0] = true; loop.exit(); });
        dialog.setContent(Forms.progress(message, cancel));
        saves.whenComplete((saved, error) -> loop.exit());
        dialog.show();
        // Saves complete on the UI thread, so none can finish between this check and entering the loop.
        if (!saves.isDone() && !cancelled[0]) loop.enter();
        boolean result = !cancelled[0] && succeeded(saves);
        dialog.close();
        return result;
    }

    private static boolean succeeded(CompletableFuture<Boolean> saves) {
        return saves.isDone() && !saves.isCompletedExceptionally() && Boolean.TRUE.equals(saves.getNow(false));
    }
}
```

- [ ] **Step 4: Run the tests to verify they pass**

Run: `./gradlew :jasper-plugin-editor:test --tests '*FormsTest' --tests '*LanguagePickerTest' --tests '*GoToLinePanelTest' --tests '*FileDropTest' --tests '*DialogsTest'`
Expected: PASS.

- [ ] **Step 5: Run the full check and commit**

Run: `./gradlew check`
Expected: BUILD SUCCESSFUL.

```bash
git add plugins/editor
git commit -F - <<'EOF'
feat(editor): dialogs, the language picker, Go to Line and file drops

DialogAsker asks modal questions; SaveWait waits for Save All with a
non-modal dialog and a nested loop so conflict prompts stay usable;
FileDrop opens dropped files and hands text transfers to the text area.

🤖 Generated with Claude Code at The Home Depot

Co-Authored-By: Claude <noreply@anthropic.com>
EOF
```

---

### Task 13: The Editor window

**Files:**
- Create: `plugins/editor/src/main/java/dev/jasper/editor/ui/{EditorEnvironment,EditorWindow}.java`
- Test: `plugins/editor/src/test/java/dev/jasper/editor/ui/EditorWindowTest.java`

**Interfaces:**
- Consumes: everything from Tasks 5–12; `PluginWindow.onCloseCommand` (Task 1); `Windows.chooseSaveFile` (Task 3).
- Produces:
  - `record EditorEnvironment(PluginContext context, Executor ui, Function<WindowOwner, Function<Question, String>> askers, Function<WindowOwner, BiFunction<String, CompletableFuture<Boolean>, Boolean>> waits, Supplier<EditorSettings> settings, boolean timers)`, with `static production(PluginContext, Executor ui, Supplier<EditorSettings>)`;
  - `EditorWindow(EditorEnvironment, Runnable onClosed)` and `EditorWindow.WINDOW_ID = "dev.jasper.editor.window"`;
  - opening: `open(EditorDocument)`, `openLocal(Path)`, `openLocalFiles(List<Path>)`, `newUntitled()`, `show()`, `dispose()`;
  - updates: `apply()` (settings or theme changed), `tick()`;
  - commands: `save()`, `saveAs()`, `saveAll() -> CompletableFuture<Boolean>`, `openChooser()`, `closeCurrent()`, `closeTab(EditorTab)`, `goToLine()`, `chooseLanguage()`, `toggleWrap()`, `toggleComment()`, `revert()`;
  - for tests: `sessions()`, `selected()`, and package-private `headerTitle(int)`, `headerDetail(int)`, `showingEmpty()`, `menu()`.

- [ ] **Step 1: Write the failing test**

`ui/EditorWindowTest.java`:

```java
package dev.jasper.editor.ui;

import dev.jasper.editor.session.DocumentSession;
import dev.jasper.editor.session.Question;
import dev.jasper.editor.syntax.Languages;
import dev.jasper.sdk.PluginInfo;
import dev.jasper.sdk.plugin.PluginContext;
import dev.jasper.sdk.testing.FakePluginHost;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import javax.swing.JMenuItem;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.assertj.core.api.Assertions.assertThat;
import static org.fife.ui.rsyntaxtextarea.SyntaxConstants.SYNTAX_STYLE_PYTHON;

class EditorWindowTest {
    @TempDir Path home;
    private FakePluginHost host;
    private final Deque<String> answers = new ArrayDeque<>();
    private final List<Question> asked = new ArrayList<>();
    private final AtomicReference<EditorSettings> settings = new AtomicReference<>(new EditorSettings("Monospaced", 13, 4, false, 1));
    private EditorWindow window;
    private boolean windowClosed;

    @BeforeEach void open() {
        Languages.register();
        host = new FakePluginHost(home.resolve("plugins"));
        PluginContext context = host.start(new PluginInfo("dev.jasper.editor", "Editor", "0.1.0", Set.of()), Set.of(), Set.of(), c -> { });
        var environment = new EditorEnvironment(context, Runnable::run,
            owner -> question -> { asked.add(question); return answers.isEmpty() ? question.dismissed() : answers.removeFirst(); },
            owner -> (message, saves) -> { asked.add(new Question("Wait", message, List.of("OK"))); drain(); return saves.getNow(false); },
            settings::get, false);
        window = new EditorWindow(environment, () -> windowClosed = true);
    }

    @AfterEach void close() { host.close(); }

    private void drain() { while (host.runBackground() > 0) { } }

    private Path file(String name, String text) throws Exception {
        Path file = home.resolve(name);
        Files.createDirectories(file.getParent());
        return Files.writeString(file, text);
    }

    private EditorTab opened(Path file) {
        window.openLocal(file);
        drain();
        return window.selected().orElseThrow();
    }

    @Test void openingAFileThatIsStillLoadingSelectsItsTab() throws Exception {
        Path file = file("config.toml", "a = 1\n");
        window.openLocal(file);
        assertThat(host.windows()).containsExactly("dev.jasper.editor.window|Editor|true");
        assertThat(window.selected().orElseThrow().session().state()).isEqualTo(DocumentSession.State.LOADING);
        window.openLocal(file);
        assertThat(window.sessions()).hasSize(1);
        assertThat(host.runBackground()).as("one read and one decode, not two reads").isEqualTo(2);
        EditorTab tab = window.selected().orElseThrow();
        assertThat(tab.area().getText()).isEqualTo("a = 1\n");
        assertThat(window.headerTitle(0)).isEqualTo("config.toml");
        assertThat(window.showingEmpty()).isFalse();
    }

    @Test void editingShowsTheMarkerAndSavingWritesTheFile() throws Exception {
        Path file = file("config.toml", "a = 1\n");
        EditorTab tab = opened(file);
        tab.area().setText("a = 2\n");
        assertThat(window.headerTitle(0)).isEqualTo("● config.toml");
        window.save();
        drain();
        assertThat(Files.readString(file)).isEqualTo("a = 2\n");
        assertThat(window.headerTitle(0)).isEqualTo("config.toml");
    }

    @Test void theCloseCommandClosesTabsFirstAskingAboutUnsavedOnesThenTheWindow() throws Exception {
        opened(file("a.toml", "a = 1\n"));
        EditorTab second = opened(file("b.toml", "b = 1\n"));
        second.area().setText("b = 2\n");
        answers.add("Cancel");
        assertThat(host.closeCommand(EditorWindow.WINDOW_ID)).as("handled by asking about the tab").isFalse();
        assertThat(asked.getLast()).isEqualTo(new Question("Unsaved changes", "Save changes to b.toml?", List.of("Save", "Don't Save", "Cancel")));
        assertThat(window.sessions()).hasSize(2);
        answers.add("Don't Save");
        assertThat(host.closeCommand(EditorWindow.WINDOW_ID)).isFalse();
        assertThat(window.sessions()).hasSize(1);
        assertThat(host.closeCommand(EditorWindow.WINDOW_ID)).isFalse();
        assertThat(window.sessions()).isEmpty();
        assertThat(window.showingEmpty()).isTrue();
        assertThat(host.closeCommand(EditorWindow.WINDOW_ID)).as("with no tabs the window closes").isTrue();
        assertThat(windowClosed).isTrue();
    }

    @Test void quittingOrClosingTheWindowAsksSaveAllDiscardOrCancel() throws Exception {
        Path file = file("config.toml", "a = 1\n");
        EditorTab tab = opened(file);
        tab.area().setText("a = 2\n");
        answers.add("Cancel");
        assertThat(host.requestQuit()).isFalse();
        assertThat(asked.getLast()).isEqualTo(new Question("Unsaved changes", "config.toml has unsaved changes.", List.of("Save All", "Discard", "Cancel")));
        answers.add("Save All");
        assertThat(host.requestQuit()).isTrue();
        assertThat(asked.getLast().message()).isEqualTo("Saving 1 file…");
        assertThat(Files.readString(file)).isEqualTo("a = 2\n");
        tab.area().setText("a = 3\n");
        answers.add("Discard");
        assertThat(host.requestClose(EditorWindow.WINDOW_ID)).isTrue();
        assertThat(Files.readString(file)).isEqualTo("a = 2\n");
    }

    @Test void aFailedSaveAllKeepsJasperRunning() throws Exception {
        Path file = file("config.toml", "a = 1\n");
        EditorTab tab = opened(file);
        tab.area().setText("a = 2\n");
        Files.delete(file);
        Files.createDirectory(file);
        answers.addAll(List.of("Save All", DocumentSession.OVERWRITE));
        assertThat(host.requestQuit()).isFalse();
        assertThat(asked).extracting(Question::title).containsExactly("Unsaved changes", "Wait", "File changed", "Save failed");
    }

    @Test void closingATabWhileItSavesClosesItAfterTheSave() throws Exception {
        Path file = file("config.toml", "a = 1\n");
        EditorTab tab = opened(file);
        tab.area().setText("a = 2\n");
        window.save();
        window.closeCurrent();
        assertThat(window.sessions()).as("waits for the save").hasSize(1);
        assertThat(asked).as("asks nothing").isEmpty();
        drain();
        assertThat(window.sessions()).isEmpty();
        assertThat(Files.readString(file)).isEqualTo("a = 2\n");
    }

    @Test void quitWaitsForASaveThatIsStillRunning() throws Exception {
        Path file = file("config.toml", "a = 1\n");
        EditorTab tab = opened(file);
        tab.area().setText("a = 2\n");
        window.save();
        assertThat(tab.session().saving()).isTrue();
        assertThat(host.requestQuit()).as("waits for the running save and asks nothing").isTrue();
        assertThat(asked).extracting(Question::message).containsExactly("Saving 1 file…");
        assertThat(Files.readString(file)).isEqualTo("a = 2\n");
    }

    @Test void untitledTabsAreSavedAsALocalFileAndAnOpenPathIsRefused() throws Exception {
        window.newUntitled();
        assertThat(window.headerTitle(0)).isEqualTo("Untitled");
        EditorTab tab = window.selected().orElseThrow();
        tab.area().setText("print(1)\n");
        Path target = home.resolve("app.py");
        host.queuePathSelection(List.of(target));
        window.save();
        drain();
        assertThat(Files.readString(target)).isEqualTo("print(1)\n");
        assertThat(window.headerTitle(0)).isEqualTo("app.py");
        assertThat(tab.area().getSyntaxEditingStyle()).isEqualTo(SYNTAX_STYLE_PYTHON);
        window.newUntitled();
        host.queuePathSelection(List.of(target));
        window.saveAs();
        assertThat(asked.getLast().message()).isEqualTo(target + " is open in another tab.");
    }

    @Test void sameNamedFilesShowTheirFolders() throws Exception {
        opened(file("one/config.toml", "a = 1\n"));
        opened(file("two/config.toml", "a = 2\n"));
        assertThat(window.headerDetail(0)).isEqualTo("one");
        assertThat(window.headerDetail(1)).isEqualTo("two");
        window.closeCurrent();
        assertThat(window.headerDetail(0)).isEmpty();
    }

    @Test void theLanguagePickerSoftWrapGoToLineAndToggleCommentActOnTheCurrentTab() throws Exception {
        EditorTab tab = opened(file("notes.txt", "one\ntwo\nthree\n"));
        window.chooseLanguage();
        var picker = (LanguagePicker) host.windowContent("dev.jasper.editor", "Language").orElseThrow();
        picker.filter("tom");
        picker.chooseSelected();
        assertThat(tab.session().style()).isEqualTo(Languages.TOML);
        assertThat(host.windows()).containsExactly("dev.jasper.editor.window|Editor|true");
        window.toggleWrap();
        assertThat(tab.area().getLineWrap()).isTrue();
        window.goToLine();
        var goTo = (GoToLinePanel) host.windowContent("dev.jasper.editor", "Go to Line").orElseThrow();
        goTo.field().setText("3");
        goTo.submit();
        assertThat(tab.area().getCaretLineNumber()).isEqualTo(2);
        window.toggleComment();
        assertThat(tab.area().getText()).contains("#three");
    }

    @Test void outsideChangesReachTabsThroughTheWatcher() throws Exception {
        Path file = file("config.toml", "a = 1\n");
        EditorTab tab = opened(file);
        Files.writeString(file, "a = 22\n");
        window.tick();
        drain();
        assertThat(tab.area().getText()).isEqualTo("a = 22\n");
    }

    @Test void theMenuListsEveryCommandAndSettingsApplyToOpenTabs() throws Exception {
        EditorTab tab = opened(file("a.json", "{}\n"));
        assertThat(window.menu().getComponents()).extracting(item -> ((JMenuItem) item).getText())
            .contains("Save", "Save As…", "Save All", "Open…", "New File", "Close Tab", "Find…", "Replace…", "Go to Line…",
                "Toggle Comment", "Next Tab", "Previous Tab", "Revert to Saved", "Language…", "Toggle Soft Wrap");
        settings.set(new EditorSettings("Monospaced", 18, 2, false, 1));
        window.apply();
        assertThat(tab.area().getFont().getSize()).isEqualTo(18);
        assertThat(tab.area().getTabSize()).isEqualTo(2);
        assertThat(tab.area().getInputMap().get(KeyNames.stroke(java.awt.event.KeyEvent.VK_S, java.awt.event.InputEvent.SHIFT_DOWN_MASK)))
            .as("Save As overrides RSyntaxTextArea's macro binding").isEqualTo("save-as");
    }
}
```

- [ ] **Step 2: Run it to verify it fails**

Run: `./gradlew :jasper-plugin-editor:test --tests '*EditorWindowTest'`
Expected: compilation fails with `cannot find symbol: class EditorEnvironment`.

- [ ] **Step 3: Write the environment and the window**

`ui/EditorEnvironment.java`:

```java
package dev.jasper.editor.ui;

import dev.jasper.editor.session.Question;
import dev.jasper.sdk.WindowOwner;
import dev.jasper.sdk.plugin.PluginContext;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * What the Editor window needs from the plugin.
 *
 * @param context the plugin's context
 * @param ui the UI thread
 * @param askers builds the window's question asker
 * @param waits builds the window's Save All wait
 * @param settings the current settings
 * @param timers whether the watcher, notice and typing timers run; tests drive them by hand
 */
public record EditorEnvironment(PluginContext context, Executor ui, Function<WindowOwner, Function<Question, String>> askers,
                                Function<WindowOwner, BiFunction<String, CompletableFuture<Boolean>, Boolean>> waits,
                                Supplier<EditorSettings> settings, boolean timers) {
    /**
     * @param context the plugin's context
     * @param ui the UI thread
     * @param settings the current settings
     * @return modal dialogs, the nested-loop wait and running timers
     */
    public static EditorEnvironment production(PluginContext context, Executor ui, Supplier<EditorSettings> settings) {
        return new EditorEnvironment(context, ui, owner -> new DialogAsker(context.windows(), owner),
            owner -> new SaveWait(context.windows(), owner), settings, true);
    }
}
```

`ui/EditorWindow.java`:

```java
package dev.jasper.editor.ui;

import dev.jasper.editor.api.EditorDocument;
import dev.jasper.editor.local.LocalDocument;
import dev.jasper.editor.local.LocalWatcher;
import dev.jasper.editor.session.DocumentSession;
import dev.jasper.editor.session.EditorModel;
import dev.jasper.editor.session.Question;
import dev.jasper.editor.session.SessionEnvironment;
import dev.jasper.sdk.plugin.PluginContext;
import dev.jasper.sdk.ui.DialogSpec;
import dev.jasper.sdk.ui.IconName;
import dev.jasper.sdk.ui.PluginDialog;
import dev.jasper.sdk.ui.PluginWindow;
import dev.jasper.sdk.ui.WindowSpec;
import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.event.ActionEvent;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.function.BiFunction;
import java.util.function.Function;
import javax.swing.Action;
import javax.swing.BorderFactory;
import javax.swing.Icon;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JTabbedPane;
import javax.swing.SwingConstants;
import javax.swing.Timer;
import javax.swing.UIManager;
import javax.swing.text.BadLocationException;
import org.fife.ui.rsyntaxtextarea.RSyntaxTextArea;
import org.fife.ui.rsyntaxtextarea.RSyntaxTextAreaEditorKit;

/** The Editor window (spec 2): tabs, commands, find, the status line, and closing and quitting. UI thread only. */
public final class EditorWindow {
    /** The window's id; it is a singleton. */
    public static final String WINDOW_ID = "dev.jasper.editor.window";
    private static final Set<String> POPUP = Set.of("save", "find", "go-to-line", "toggle-comment");

    private final EditorEnvironment environment;
    private final PluginContext context;
    private final Runnable onClosed;
    private final EditorModel model = new EditorModel();
    private final EditorTheme theme = new EditorTheme();
    private final PluginWindow window;
    private final Function<Question, String> ask;
    private final BiFunction<String, CompletableFuture<Boolean>, Boolean> await;
    private final SessionEnvironment sessions;
    private final JPanel root = new JPanel(new BorderLayout());
    private final CardLayout cards = new CardLayout();
    private final JPanel center = new JPanel(cards);
    private final JTabbedPane tabs = new JTabbedPane(JTabbedPane.TOP, JTabbedPane.SCROLL_TAB_LAYOUT);
    private final JLabel empty = new JLabel();
    private final FindBar findBar;
    private final StatusLine status;
    private final List<Command> commands;
    private final LocalWatcher watcher;
    private final Timer ticker;
    private final Timer noticeTimer;
    private final Icon fileIcon, serverIcon, closeIcon;
    private boolean showingEmpty = true;

    /**
     * Creates the window, not yet shown.
     *
     * @param environment what the window needs from the plugin
     * @param onClosed runs once the window has closed
     */
    public EditorWindow(EditorEnvironment environment, Runnable onClosed) {
        this.environment = environment;
        this.context = environment.context();
        this.onClosed = onClosed;
        fileIcon = context.appearance().icon(IconName.FILE);
        serverIcon = context.appearance().icon(IconName.SERVER);
        closeIcon = context.appearance().icon(IconName.CLOSE);
        window = context.windows().create(new WindowSpec(WINDOW_ID, "Editor", new Dimension(900, 650), true));
        ask = environment.askers().apply(window);
        await = environment.waits().apply(window);
        sessions = new SessionEnvironment(context.background(), environment.ui(), ask, () -> environment.settings().get().maxFileBytes());
        findBar = new FindBar(() -> selected().map(EditorTab::area));
        status = new StatusLine(this::chooseLanguage, this::toggleWrap);
        commands = List.of(
            command("save", "Save", KeyEvent.VK_S, 0, this::save),
            command("save-as", "Save As…", KeyEvent.VK_S, InputEvent.SHIFT_DOWN_MASK, this::saveAs),
            command("save-all", "Save All", KeyEvent.VK_S, InputEvent.ALT_DOWN_MASK, () -> saveAll()),
            command("open", "Open…", KeyEvent.VK_O, 0, this::openChooser),
            command("new", "New File", KeyEvent.VK_N, 0, this::newUntitled),
            new Command("close-tab", "Close Tab", Optional.of(KeyNames.stroke(KeyEvent.VK_W, 0)), false, this::closeCurrent),
            command("find", "Find…", KeyEvent.VK_F, 0, () -> findBar.open(false)),
            command("replace", "Replace…", KeyEvent.VK_F, InputEvent.ALT_DOWN_MASK, () -> findBar.open(true)),
            command("find-next", "Find Next", KeyEvent.VK_G, 0, findBar::next),
            command("find-previous", "Find Previous", KeyEvent.VK_G, InputEvent.SHIFT_DOWN_MASK, findBar::previous),
            command("go-to-line", "Go to Line…", KeyEvent.VK_L, 0, this::goToLine),
            command("toggle-comment", "Toggle Comment", KeyEvent.VK_SLASH, 0, this::toggleComment),
            command("next-tab", "Next Tab", KeyEvent.VK_CLOSE_BRACKET, InputEvent.SHIFT_DOWN_MASK, () -> step(1)),
            command("previous-tab", "Previous Tab", KeyEvent.VK_OPEN_BRACKET, InputEvent.SHIFT_DOWN_MASK, () -> step(-1)),
            new Command("revert", "Revert to Saved", Optional.empty(), false, this::revert),
            new Command("language", "Language…", Optional.empty(), false, this::chooseLanguage),
            new Command("wrap", "Toggle Soft Wrap", Optional.empty(), false, this::toggleWrap));
        watcher = new LocalWatcher(context.background(), environment.ui(), model::sessions);
        ticker = new Timer(2000, event -> watcher.tick());
        noticeTimer = new Timer(3000, event -> selected().ifPresent(tab -> tab.session().clearNotice()));
        noticeTimer.setRepeats(false);
        layOut();
        window.setContent(root);
        window.onClosing(this::mayClose);
        window.onCloseCommand(this::closeCommand);
        window.onClosed(this::closed);
        if (environment.timers()) ticker.start();
    }

    /** @param document opened in a new tab, or its open tab selected; the window comes to the front */
    public void open(EditorDocument document) {
        DocumentSession session = model.find(document.key()).orElseGet(() -> {
            var created = new DocumentSession(document, sessions);
            add(created);
            created.load();
            return created;
        });
        select(session);
        show();
    }

    public void openLocal(Path file) { open(new LocalDocument(file, context.background())); }

    public void openLocalFiles(List<Path> files) { for (Path file : files) openLocal(file); }

    public void newUntitled() {
        var session = DocumentSession.untitled(model.nextUntitledName(), sessions);
        add(session);
        select(session);
        show();
    }

    public void show() {
        window.show();
        window.toFront();
    }

    /** Closes the window at once, without asking; the plugin is stopping. */
    public void dispose() { window.close(); }

    /** Settings or the theme changed: applies them to every tab. */
    public void apply() {
        EditorSettings settings = environment.settings().get();
        for (EditorTab tab : editorTabs()) tab.apply(settings, theme, context.appearance().variant());
        refreshStatus();
    }

    /** Checks open local files for outside changes; the window's timer calls this every 2 s. */
    public void tick() { watcher.tick(); }

    public void save() { selected().ifPresent(tab -> saveSession(tab.session())); }

    public void saveAs() { selected().ifPresent(tab -> saveAsSession(tab.session())); }

    /** @return completes with true when every tab with unsaved changes was saved */
    public CompletableFuture<Boolean> saveAll() { return saveSessions(model.sessions().stream().filter(DocumentSession::dirty).toList()); }

    public void openChooser() {
        Optional<Path> near = selected().flatMap(tab -> localFile(tab.session())).map(Path::getParent);
        openLocalFiles(context.windows().chooseFiles(window, "Open", near));
    }

    public void closeCurrent() { selected().ifPresent(this::closeTab); }

    /** @param tab closed after asking about unsaved changes; a tab that is saving closes when its save succeeds */
    public void closeTab(EditorTab tab) {
        DocumentSession session = tab.session();
        Optional<CompletableFuture<Boolean>> pending = session.pendingSave();
        if (pending.isPresent()) {
            pending.get().thenAccept(saved -> environment.ui().execute(() -> { if (saved) remove(tab); }));
            return;
        }
        if (session.dirty()) {
            String answer = ask.apply(new Question("Unsaved changes", "Save changes to " + session.name() + "?", List.of("Save", "Don't Save", "Cancel")));
            if ("Don't Save".equals(answer)) remove(tab);
            else if ("Save".equals(answer)) saveSession(session).thenAccept(saved -> environment.ui().execute(() -> { if (saved) remove(tab); }));
            return;
        }
        remove(tab);
    }

    public void goToLine() {
        selected().filter(tab -> tab.session().state() == DocumentSession.State.READY).ifPresent(tab -> {
            RSyntaxTextArea area = tab.area();
            PluginDialog dialog = context.windows().dialog(new DialogSpec("Go to Line", window, true));
            dialog.setContent(new GoToLinePanel(area.getCaretLineNumber() + 1, area.getLineCount(), line -> {
                dialog.close();
                try { area.setCaretPosition(area.getLineStartOffset(line - 1)); }
                catch (BadLocationException impossible) { return; }
                area.requestFocusInWindow();
            }, dialog::close));
            dialog.show();
        });
    }

    public void chooseLanguage() {
        selected().ifPresent(tab -> {
            PluginDialog dialog = context.windows().dialog(new DialogSpec("Language", window, true));
            dialog.setContent(new LanguagePicker(tab.session().style(), style -> { dialog.close(); tab.session().setStyle(style); }));
            dialog.show();
        });
    }

    public void toggleWrap() { selected().ifPresent(tab -> tab.session().setWrap(!tab.session().wrap())); }

    public void toggleComment() {
        selected().ifPresent(tab -> {
            Action action = tab.area().getActionMap().get(RSyntaxTextAreaEditorKit.rstaToggleCommentAction);
            if (action != null) action.actionPerformed(new ActionEvent(tab.area(), ActionEvent.ACTION_PERFORMED, "toggle-comment"));
        });
    }

    public void revert() { selected().ifPresent(tab -> tab.session().revert()); }

    public List<DocumentSession> sessions() { return model.sessions(); }

    public Optional<EditorTab> selected() {
        return tabs.getSelectedComponent() instanceof EditorTab tab ? Optional.of(tab) : Optional.empty();
    }

    String headerTitle(int index) { return ((TabHeader) tabs.getTabComponentAt(index)).name.getText(); }
    String headerDetail(int index) { return ((TabHeader) tabs.getTabComponentAt(index)).detail.getText(); }
    boolean showingEmpty() { return showingEmpty; }

    JPopupMenu menu() {
        var menu = new JPopupMenu();
        for (Command command : commands) {
            var item = new JMenuItem(command.title());
            command.stroke().ifPresent(item::setAccelerator);
            item.addActionListener(event -> command.run().run());
            menu.add(item);
        }
        return menu;
    }

    /**
     * The closing guard (spec 2.3), asked when the window closes and when Jasper quits: waits for running saves,
     * then asks about unsaved tabs, and saves them all when asked to.
     */
    boolean mayClose() {
        List<DocumentSession> running = model.sessions().stream().filter(DocumentSession::saving).toList();
        if (!running.isEmpty() && !await.apply(saving(running.size()), saveSessions(running))) return false;
        List<DocumentSession> dirty = model.sessions().stream().filter(DocumentSession::dirty).toList();
        if (dirty.isEmpty()) return true;
        String answer = ask.apply(new Question("Unsaved changes", unsaved(dirty), List.of("Save All", "Discard", "Cancel")));
        if ("Discard".equals(answer)) return true;
        if (!"Save All".equals(answer)) return false;
        return await.apply(saving(dirty.size()), saveSessions(dirty));
    }

    private boolean closeCommand() {
        if (tabs.getTabCount() == 0) return false;
        closeCurrent();
        return true;
    }

    private void closed() {
        ticker.stop();
        noticeTimer.stop();
        for (DocumentSession session : model.sessions()) session.cancelLoad();
        onClosed.run();
    }

    private CompletableFuture<Boolean> saveSession(DocumentSession session) {
        return session.untitled() ? saveAsSession(session) : session.save();
    }

    private CompletableFuture<Boolean> saveAsSession(DocumentSession session) {
        Optional<Path> initial = localFile(session).or(() -> Optional.of(Path.of(System.getProperty("user.home"), session.name())));
        Optional<Path> chosen = context.windows().chooseSaveFile(window, "Save As", initial);
        if (chosen.isEmpty()) return CompletableFuture.completedFuture(false);
        if (model.find(LocalDocument.key(chosen.get())).filter(other -> other != session).isPresent()) {
            ask.apply(new Question("Already open", chosen.get() + " is open in another tab.", List.of("OK")));
            return CompletableFuture.completedFuture(false);
        }
        return session.saveAs(new LocalDocument(chosen.get(), context.background()));
    }

    /** One after another, so each conflict prompt appears on its own; true only when every save succeeded. */
    private CompletableFuture<Boolean> saveSessions(List<DocumentSession> pending) {
        CompletableFuture<Boolean> chain = CompletableFuture.completedFuture(true);
        for (DocumentSession session : pending)
            chain = chain.thenCompose(ok -> {
                var step = new CompletableFuture<Boolean>();
                environment.ui().execute(() -> session.pendingSave()
                    .orElseGet(() -> session.dirty() ? saveSession(session) : CompletableFuture.completedFuture(true))
                    .whenComplete((saved, error) -> step.complete(ok && error == null && Boolean.TRUE.equals(saved))));
                return step;
            });
        return chain;
    }

    private void add(DocumentSession session) {
        EditorSettings settings = environment.settings().get();
        session.setWrap(settings.softWrap());
        var tab = new EditorTab(session, this::closeTab, environment.timers());
        tab.apply(settings, theme, context.appearance().variant());
        tab.bind(commands);
        tab.addToPopup(commands.stream().filter(command -> POPUP.contains(command.id())).toList());
        tab.area().setTransferHandler(new FileDrop(tab.area().getTransferHandler(), this::openLocalFiles));
        tab.area().addCaretListener(event -> { if (selected().orElse(null) == tab) refreshStatus(); });
        session.addListener(() -> {
            refreshHeaders();
            if (selected().orElse(null) == tab) refreshStatus();
        });
        model.add(session);
        tabs.addTab(session.name(), tab);
        tabs.setTabComponentAt(tabs.getTabCount() - 1, new TabHeader(tab));
        refresh();
    }

    private void remove(EditorTab tab) {
        int index = tabs.indexOfComponent(tab);
        if (index < 0) return;
        tab.session().cancelLoad();
        model.remove(tab.session());
        tabs.removeTabAt(index);
        refresh();
    }

    private void select(DocumentSession session) {
        int index = model.indexOf(session);
        if (index >= 0) tabs.setSelectedIndex(index);
    }

    private void step(int delta) {
        int count = tabs.getTabCount();
        if (count > 1) tabs.setSelectedIndex(Math.floorMod(tabs.getSelectedIndex() + delta, count));
    }

    private List<EditorTab> editorTabs() {
        List<EditorTab> out = new ArrayList<>();
        for (int i = 0; i < tabs.getTabCount(); i++) if (tabs.getComponentAt(i) instanceof EditorTab tab) out.add(tab);
        return out;
    }

    private void layOut() {
        empty.setHorizontalAlignment(SwingConstants.CENTER);
        empty.putClientProperty("html.disable", true);
        empty.setText("Open a file with " + KeyNames.describe(KeyNames.stroke(KeyEvent.VK_O, 0)) + ", or double-click one in the SFTP panel.");
        empty.setForeground(UIManager.getColor("Label.disabledForeground"));
        tabs.putClientProperty("JTabbedPane.trailingComponent", moreButton());
        tabs.addChangeListener(event -> refreshStatus());
        center.add(empty, "empty");
        center.add(tabs, "tabs");
        var south = new JPanel(new BorderLayout());
        south.add(findBar, BorderLayout.NORTH);
        south.add(status, BorderLayout.SOUTH);
        root.add(center, BorderLayout.CENTER);
        root.add(south, BorderLayout.SOUTH);
        for (Command command : commands) {
            if (!command.bound() || command.stroke().isEmpty()) continue;
            root.getInputMap(JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT).put(command.stroke().get(), command.id());
            root.getActionMap().put(command.id(), EditorTab.action(command));
        }
        var drop = new FileDrop(null, this::openLocalFiles);
        for (JComponent target : List.of(root, empty, tabs)) target.setTransferHandler(drop);
        refresh();
    }

    private JButton moreButton() {
        var more = new JButton("⋯");
        more.setToolTipText("Editor commands");
        more.getAccessibleContext().setAccessibleName("Editor commands");
        more.putClientProperty("JButton.buttonType", "borderless");
        more.addActionListener(event -> menu().show(more, 0, more.getHeight()));
        return more;
    }

    private void refresh() {
        showingEmpty = tabs.getTabCount() == 0;
        cards.show(center, showingEmpty ? "empty" : "tabs");
        refreshHeaders();
        refreshStatus();
    }

    private void refreshHeaders() {
        for (int i = 0; i < tabs.getTabCount(); i++) {
            if (!(tabs.getComponentAt(i) instanceof EditorTab tab) || !(tabs.getTabComponentAt(i) instanceof TabHeader header)) continue;
            DocumentSession session = tab.session();
            header.update((session.dirty() ? "● " : "") + session.name(), model.detail(session), session.host().isPresent() ? serverIcon : fileIcon);
            tabs.setToolTipTextAt(i, session.location());
        }
    }

    private void refreshStatus() {
        status.display(selected(), environment.settings().get().tabSize());
        if (environment.timers() && selected().flatMap(tab -> tab.session().notice()).isPresent()) noticeTimer.restart();
    }

    private static Command command(String id, String title, int keyCode, int extraModifiers, Runnable run) {
        return new Command(id, title, Optional.of(KeyNames.stroke(keyCode, extraModifiers)), true, run);
    }

    private static Optional<Path> localFile(DocumentSession session) {
        return session.document().filter(LocalDocument.class::isInstance).map(document -> ((LocalDocument) document).file());
    }

    private static String unsaved(List<DocumentSession> dirty) {
        if (dirty.size() == 1) return dirty.getFirst().name() + " has unsaved changes.";
        StringBuilder message = new StringBuilder("These files have unsaved changes:");
        for (DocumentSession session : dirty.subList(0, Math.min(10, dirty.size()))) message.append("\n  ").append(session.name());
        if (dirty.size() > 10) message.append("\n  and ").append(dirty.size() - 10).append(" more");
        return message.toString();
    }

    private static String saving(int count) { return count == 1 ? "Saving 1 file…" : "Saving " + count + " files…"; }

    /** A tab's title: its icon, name, dim detail and a close button. */
    private final class TabHeader extends JPanel {
        private final JLabel name = new JLabel();
        private final JLabel detail = new JLabel();

        TabHeader(EditorTab tab) {
            super(new FlowLayout(FlowLayout.LEFT, 4, 0));
            setOpaque(false);
            name.putClientProperty("html.disable", true);
            detail.putClientProperty("html.disable", true);
            detail.setForeground(UIManager.getColor("Label.disabledForeground"));
            var close = new JButton(closeIcon);
            close.setToolTipText("Close tab");
            close.getAccessibleContext().setAccessibleName("Close tab");
            close.putClientProperty("JButton.buttonType", "toolBarButton");
            close.setFocusable(false);
            close.setBorder(BorderFactory.createEmptyBorder(1, 1, 1, 1));
            close.addActionListener(event -> closeTab(tab));
            add(name);
            add(detail);
            add(close);
        }

        void update(String title, String detailText, Icon icon) {
            name.setText(title);
            name.setIcon(icon);
            detail.setText(detailText);
            detail.setVisible(!detailText.isEmpty());
        }
    }
}
```

- [ ] **Step 4: Run it to verify it passes**

Run: `./gradlew :jasper-plugin-editor:test --tests '*EditorWindowTest'`
Expected: PASS.

- [ ] **Step 5: Run the full check and commit**

Run: `./gradlew check`
Expected: BUILD SUCCESSFUL.

```bash
git add plugins/editor
git commit -F - <<'EOF'
feat(editor): the Editor window

Tabs with dirty markers and folder or host details, the ⋯ command menu,
key bindings, find, the status line, Save As through the save chooser,
Close Tab through onCloseCommand, and a closing guard that waits for
running saves and asks Save All, Discard or Cancel.

🤖 Generated with Claude Code at The Home Depot

Co-Authored-By: Claude <noreply@anthropic.com>
EOF
```

---

### Task 14: The plugin entry point, and bundling it

**Files:**
- Create: `plugins/editor/src/main/java/dev/jasper/editor/{EditorPlugin,package-info}.java`
- Test: `plugins/editor/src/test/java/dev/jasper/editor/EditorPluginTest.java`
- Modify: `jasper-app/build.gradle.kts` (`stagePlugins`), `jasper-app/src/test/java/dev/jasper/app/plugins/BundledSamplePluginTest.java`

**Interfaces:**
- Consumes: `Editor` (Task 4), `Languages` (Task 5), and `EditorSettings`, `EditorEnvironment`, `EditorWindow` (Tasks 9, 13).
- Produces:
  - `EditorPlugin()` (production), plus the package-private `EditorPlugin(Executor ui, BiFunction<PluginContext, Supplier<EditorSettings>, EditorEnvironment> environments)` for tests;
  - action ids `dev.jasper.editor.open`, `dev.jasper.editor.new`, `dev.jasper.editor.show`;
  - the published `Editor` service.

- [ ] **Step 1: Write the failing test**

`EditorPluginTest.java`:

```java
package dev.jasper.editor;

import dev.jasper.editor.api.Editor;
import dev.jasper.editor.session.Question;
import dev.jasper.editor.ui.EditorEnvironment;
import dev.jasper.editor.ui.EditorWindow;
import dev.jasper.sdk.PluginInfo;
import dev.jasper.sdk.plugin.PluginContext;
import dev.jasper.sdk.testing.FakePluginHost;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.assertj.core.api.Assertions.assertThat;

class EditorPluginTest {
    @TempDir Path home;
    private static final PluginInfo INFO = new PluginInfo("dev.jasper.editor", "Editor", "0.1.0", Set.of());

    private static EditorPlugin plugin() {
        return new EditorPlugin(Runnable::run, (context, settings) -> new EditorEnvironment(context, Runnable::run,
            owner -> Question::dismissed, owner -> (message, saves) -> saves.getNow(false), settings, false));
    }

    @Test void registersItsActionsAndPublishesTheEditorService() throws Exception {
        try (var host = new FakePluginHost(home.resolve("plugins"))) {
            host.start(INFO, Set.of(), Set.of(), plugin());
            assertThat(host.actions()).contains("dev.jasper.editor.open|Open in Editor…|true",
                "dev.jasper.editor.new|New Editor File|true", "dev.jasper.editor.show|Show Editor|true");
            assertThat(host.menu("FILE")).contains("item:dev.jasper.editor.open", "item:dev.jasper.editor.new");
            var consumer = new AtomicReference<PluginContext>();
            host.start(new PluginInfo("test.consumer", "Consumer", "1.0.0", Set.of()), Set.of("dev.jasper.editor"), Set.of(), consumer::set);
            Editor editor = consumer.get().services().require(Editor.class);
            editor.open(Files.writeString(home.resolve("a.json"), "{}\n"));
            assertThat(host.windows()).containsExactly("dev.jasper.editor.window|Editor|true");
            while (host.runBackground() > 0) { }
            assertThat(host.failures()).isEmpty();
        }
    }

    @Test void theOpenActionUsesATerminalWindowsChooserAndClosingTheWindowLetsAFreshOneOpen() throws Exception {
        try (var host = new FakePluginHost(home.resolve("plugins"))) {
            host.start(INFO, Set.of(), Set.of(), plugin());
            UUID terminal = host.addTerminalWindow();
            host.queuePathSelection(List.of(Files.writeString(home.resolve("b.yaml"), "a: 1\n")));
            assertThat(host.invoke("dev.jasper.editor.open", terminal, null)).isTrue();
            assertThat(host.windows()).containsExactly("dev.jasper.editor.window|Editor|true");
            while (host.runBackground() > 0) { }
            assertThat(host.closeCommand(EditorWindow.WINDOW_ID)).as("closes the one clean tab").isFalse();
            assertThat(host.closeCommand(EditorWindow.WINDOW_ID)).as("then the window").isTrue();
            assertThat(host.windows()).isEmpty();
            assertThat(host.invoke("dev.jasper.editor.new", terminal, null)).isTrue();
            assertThat(host.windows()).containsExactly("dev.jasper.editor.window|Editor|true");
        }
    }

    @Test void badSettingsAreReportedAtStartAndOnEveryChange() {
        try (var host = new FakePluginHost(home.resolve("plugins"))) {
            host.setConfig("dev.jasper.editor", Map.of("font_size", 99L));
            host.start(INFO, Set.of(), Set.of(), plugin());
            assertThat(host.reports()).containsExactly("dev.jasper.editor: font_size: Use 8 to 48; using 13.");
            host.setConfig("dev.jasper.editor", Map.of("tab_size", 0L));
            assertThat(host.reports()).contains("dev.jasper.editor: tab_size: Use 1 to 16; using 4.");
        }
    }
}
```

- [ ] **Step 2: Run it to verify it fails**

Run: `./gradlew :jasper-plugin-editor:test --tests '*EditorPluginTest'`
Expected: compilation fails with `cannot find symbol: class EditorPlugin`.

- [ ] **Step 3: Write the plugin**

`package-info.java`:

```java
/** The Editor plugin: {@link dev.jasper.editor.EditorPlugin} wires the text, syntax, local, session and UI layers to the SDK. */
package dev.jasper.editor;
```

`EditorPlugin.java`:

```java
package dev.jasper.editor;

import dev.jasper.editor.api.Editor;
import dev.jasper.editor.api.EditorDocument;
import dev.jasper.editor.syntax.Languages;
import dev.jasper.editor.ui.EditorEnvironment;
import dev.jasper.editor.ui.EditorSettings;
import dev.jasper.editor.ui.EditorWindow;
import dev.jasper.sdk.plugin.Plugin;
import dev.jasper.sdk.plugin.PluginContext;
import dev.jasper.sdk.ui.ActionSpec;
import dev.jasper.sdk.ui.IconName;
import dev.jasper.sdk.ui.PluginMenu;
import dev.jasper.sdk.ui.StandardMenu;
import java.awt.GraphicsEnvironment;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.Executor;
import java.util.function.BiFunction;
import java.util.function.Supplier;
import javax.swing.SwingUtilities;

/** Edits local and remote text files in tabs in one Editor window; publishes {@link Editor} for other plugins. */
public final class EditorPlugin implements Plugin {
    static final String OPEN = "dev.jasper.editor.open", NEW = "dev.jasper.editor.new", SHOW = "dev.jasper.editor.show";

    private final Executor ui;
    private final BiFunction<PluginContext, Supplier<EditorSettings>, EditorEnvironment> environments;
    private EditorEnvironment environment;
    private EditorSettings settings;
    private EditorWindow window;

    /** The production plugin: Swing's event queue, modal dialogs and running timers. */
    public EditorPlugin() {
        this(SwingUtilities::invokeLater, (context, settings) -> EditorEnvironment.production(context, SwingUtilities::invokeLater, settings));
    }

    EditorPlugin(Executor ui, BiFunction<PluginContext, Supplier<EditorSettings>, EditorEnvironment> environments) {
        this.ui = ui;
        this.environments = environments;
    }

    @Override public void start(PluginContext context) {
        Languages.register();
        // Set.copyOf tolerates a platform that lists a family twice; Set.of would throw.
        Set<String> fonts = Set.copyOf(Arrays.asList(GraphicsEnvironment.getLocalGraphicsEnvironment().getAvailableFontFamilyNames()));
        settings = EditorSettings.read(context.config(), fonts);
        environment = environments.apply(context, () -> settings);
        context.config().onChanged(() -> ui.execute(() -> {
            settings = EditorSettings.read(context.config(), fonts);
            if (window != null) window.apply();
        }));
        context.appearance().onChanged(variant -> ui.execute(() -> { if (window != null) window.apply(); }));
        context.services().publish(Editor.class, new Editor() {
            @Override public void open(Path file) { window().openLocal(file); }
            @Override public void open(EditorDocument document) { window().open(document); }
        });
        context.actions().register(ActionSpec.of(OPEN, "Open in Editor…").withIcon(context.appearance().icon(IconName.FILE))
                .withKeywords(List.of("editor", "open", "file", "edit")).withDefaultBinding("cmd+o"),
            invoked -> {
                List<Path> files = context.windows().chooseFiles(invoked.window(), "Open in Editor", Optional.empty());
                if (!files.isEmpty()) window().openLocalFiles(files);
            });
        context.actions().register(ActionSpec.of(NEW, "New Editor File").withKeywords(List.of("editor", "new", "file", "untitled")),
            invoked -> window().newUntitled());
        context.actions().register(ActionSpec.of(SHOW, "Show Editor").withKeywords(List.of("editor", "tabs")), invoked -> window().show());
        PluginMenu file = context.menus().standard(StandardMenu.FILE);
        file.add(OPEN);
        file.add(NEW);
    }

    @Override public void stop() {
        if (window != null) window.dispose();
    }

    private EditorWindow window() {
        if (window == null) window = new EditorWindow(environment, () -> window = null);
        return window;
    }
}
```

- [ ] **Step 4: Run it to verify it passes**

Run: `./gradlew :jasper-plugin-editor:test --tests '*EditorPluginTest'`
Expected: PASS.

- [ ] **Step 5: Bundle it**

In `jasper-app/build.gradle.kts`, inside `stagePlugins`, after the two Remote lines, add:

```kotlin
    from(project(":jasper-plugin-editor").tasks.named("jar")) { into("dev.jasper.editor") }
    from(project(":jasper-plugin-editor").configurations.named("runtimeClasspath")) { into("dev.jasper.editor") }
```

In `BundledSamplePluginTest.java`, replace:

```java
        assertThat(runtime.get().statusLines()).as("the sample and the four bundled feature plugins").hasSize(5)
```

with:

```java
        assertThat(runtime.get().statusLines()).as("the sample and the five bundled feature plugins").hasSize(6)
            .anySatisfy(line -> assertThat(line).contains("dev.jasper.editor", "BUNDLED", "ACTIVE"))
```

- [ ] **Step 6: Run the full check and commit**

Run: `./gradlew check`
Expected: BUILD SUCCESSFUL, including `BundledSamplePluginTest` with the Editor active in a real host.

```bash
git add plugins/editor jasper-app/build.gradle.kts jasper-app/src/test/java/dev/jasper/app/plugins/BundledSamplePluginTest.java
git commit -F - <<'EOF'
feat(editor): the Editor plugin, bundled in the application

EditorPlugin publishes the Editor service, registers Open in Editor…
(Cmd/Ctrl+O), New Editor File and Show Editor, applies settings and theme
changes live, and ships in lib/plugins/dev.jasper.editor with RSyntaxTextArea.

🤖 Generated with Claude Code at The Home Depot

Co-Authored-By: Claude <noreply@anthropic.com>
EOF
```

---

### Task 15: Documentation, the verification checklist and status

**Files:**
- Create: `docs/editor.md`, `docs/editor-verification.md`
- Modify: `docs/README.md`, `docs/keyboard-shortcuts.md`, `docs/configuration.md`, `docs/plugin-authoring.md`, `jasper-sdk/README.md`, `AGENTS.md`, `docs/STATUS.md`, and this plan's status banner

**Interfaces:** none (documentation only). `AppDocumentationTest` checks that every relative link resolves.

- [ ] **Step 1: Write the user guide**

`docs/editor.md`:

```markdown
# Editor

The bundled Editor plugin (`dev.jasper.editor`) edits text files in tabs in one Editor window,
with syntax highlighting from RSyntaxTextArea. The [design](superpowers/specs/2026-09-27-jasper-editor-plugin-design.md)
is the binding reference.

## Opening files

- **Open in Editor…** (Cmd+O on macOS, Ctrl+O elsewhere) in a terminal window's File menu or the
  palette, and **Open…** inside the Editor window.
- **New Editor File**, or **New File** (Cmd/Ctrl+N) inside the Editor window, for an untitled tab.
- Drop files from the desktop onto the Editor window. Folders are ignored.

Opening a file that is already open selects its tab. Files larger than `max_file_mib` and binary
files (a NUL byte near the start) are refused with a message in the tab.

## Tabs and the window

Each tab shows the file's name, a ● when it has unsaved changes, and, when two open files share a
name, the parent folder's name. The ⋯ button at the right of the tab row lists every command with
its shortcut. The status line shows the caret's line and column, the language (click to choose
another), the encoding, the line endings (`Mixed → LF` until the first save converts them), the
indentation and Wrap (click to toggle soft wrap for the tab).

| Shortcut (macOS / others) | Command |
|---|---|
| ⌘S / Ctrl+S | Save |
| ⇧⌘S / Ctrl+Shift+S | Save As… (a local path) |
| ⌥⌘S / Ctrl+Alt+S | Save All |
| ⌘O / Ctrl+O | Open… |
| ⌘N / Ctrl+N | New File |
| ⌘W / Ctrl+W | Close Tab; with no tabs, close the window |
| ⌘F / Ctrl+F, ⌥⌘F / Ctrl+Alt+F | Find, Replace |
| ⌘G / Ctrl+G, ⇧⌘G / Ctrl+Shift+G | Next, previous match |
| ⌘L / Ctrl+L | Go to Line |
| ⌘/ / Ctrl+/ | Toggle Comment |
| ⇧⌘] / Ctrl+Shift+], ⇧⌘[ / Ctrl+Shift+[ | Next, previous tab |

Revert to Saved, Language… and Toggle Soft Wrap have no shortcut; they are in the ⋯ menu.

## Saving

Saving checks first whether the file changed since it was opened, and asks **Overwrite**,
**Reload** (discarding your edits) or **Cancel**; a deleted file asks **Save Anyway**. A save writes a
hidden sibling file and atomically replaces the original, keeping its permissions and any
symbolic link; for a hard-linked file, a folder you cannot write, or a different owner, it writes in
place instead. Text the file's encoding cannot hold offers **Convert to UTF-8 and Save**.

Closing a tab with unsaved changes asks **Save**, **Don't Save** or **Cancel**. Closing the Editor
window, or quitting or restarting Jasper, asks **Save All**, **Discard** or **Cancel** and waits for
saves already running.

## Outside changes

While the window is open the Editor checks open local files every 2 seconds. A tab without unsaved
changes reloads by itself; one with changes shows a banner offering **Reload** or **Keep Mine**, and
a deleted file's banner offers **Close** or **Keep**.

## Languages

TOML, JSON, Python, shell (Bash) and YAML are detected from the file name, extension or a `#!` line,
as are the other languages RSyntaxTextArea knows by an unambiguous extension; the rest open as
plain text. TOML uses the Editor's own lexer.

## Settings

`plugins/dev.jasper.editor/dev.jasper.editor.toml` under Jasper's home, applied live:

| Key | Default | Meaning |
|---|---|---|
| `font_family` | first installed of JetBrains Mono, Menlo, Consolas, DejaVu Sans Mono, else Monospaced | editor font |
| `font_size` | 13 | points, 8–48 |
| `tab_size` | 4 | columns, 1–16 |
| `soft_wrap` | false | wrapping for new tabs |
| `max_file_mib` | 10 | larger files are refused, 1–256 |

A bad value is reported and its default used.

## For other plugins

The Editor exports `dev.jasper.editor.api`. Require `dev.jasper.editor`, look up `Editor` with
`services().find`, and call `open(Path)` for a local file or `open(EditorDocument)` for a file you
read and write yourself; `EditorDocument.write` documents the save contract a provider follows.
```

- [ ] **Step 2: Write the verification checklist**

`docs/editor-verification.md`:

```markdown
# Editor verification

Automated coverage runs in `./gradlew check` (`plugins/editor/src/test`, the SDK contract suite and
`JasperApplicationPluginsTest`). These GUI checks are the user's; an unchecked item is unverified.

## Plan A: local files

- [ ] 1. File → Open in Editor… (Cmd/Ctrl+O) in a terminal window opens the chosen files in tabs in one Editor window.
- [ ] 2. Dropping a file from Finder onto the Editor window opens it; dropping a folder does nothing.
- [ ] 3. TOML, JSON, Python, Bash and YAML samples are highlighted; `Cargo.lock` and a `#!/usr/bin/env python3` script without an extension are detected.
- [ ] 4. Editing shows ●; Cmd/Ctrl+S saves and clears it; `ls -l` shows the same permissions, and a symbolic link to the file is still a link.
- [ ] 5. Cmd/Ctrl+W closes the current tab, asking about unsaved changes; with no tabs it closes the window.
- [ ] 6. Cmd+Q with an unsaved tab asks Save All / Discard / Cancel; Cancel keeps Jasper running, Save All saves then quits.
- [ ] 7. Changing an open file from the terminal (`echo x >> file`) reloads a clean tab within a few seconds and shows the banner on an edited one.
- [ ] 8. Switching the theme between light and dark recolours the editor; the tab row, status line and find bar follow the theme.
- [ ] 9. Find (Cmd/Ctrl+F), Replace (Alt+Cmd/Ctrl+F), Go to Line (Cmd/Ctrl+L) and Toggle Comment (Cmd/Ctrl+/) work; Shift+Cmd/Ctrl+S opens the save dialog rather than anything else.
```

- [ ] **Step 3: Link and document it elsewhere**

In `docs/README.md`, after the "Command palette" bullet under "User and operational guides", add:

```markdown
- [Editor](editor.md): editing local files in tabs in the Editor window, saving safely, and outside changes; [verification](editor-verification.md).
```

In `docs/keyboard-shortcuts.md`, at the end of the file, add:

```markdown
## Editor window

The bundled Editor's window has its own shortcuts, listed in its ⋯ menu and in [Editor](editor.md).
They belong to that window's content, not to terminal windows, so they are not in this catalog; the
terminal-window action **Open in Editor…** (`dev.jasper.editor.open`, Cmd/Ctrl+O) is.
```

In `docs/configuration.md`, after the "### Snippets" section (before "### Live settings and temporary choices"), add:

```markdown
### Editor

The bundled Editor reads `plugins/dev.jasper.editor/dev.jasper.editor.toml` (see [Plugins](#plugins)):
`font_family`, `font_size`, `tab_size`, `soft_wrap` and `max_file_mib`, applied live. See
[Editor](editor.md#settings) for defaults and ranges.
```

In `jasper-sdk/README.md`, replace:

```
the running theme. Bundled plugins declare `sdk = ">=0.8.0, <0.9"` (Remote `>=0.8.1`, for
`PanelSpec.withToggleActionId`). Custom older hosts
```

with:

```
the running theme. Bundled plugins declare `sdk = ">=0.8.0, <0.9"` (Remote `>=0.8.1`, for
`PanelSpec.withToggleActionId`; Editor `>=0.8.2`, for `onCloseCommand`, quit guards and
`chooseSaveFile`). Custom older hosts
```

In `docs/plugin-authoring.md`, replace:

```
  (Remote `>=0.8.1`, for `PanelSpec.withToggleActionId`).
```

with:

```
  (Remote `>=0.8.1`, for `PanelSpec.withToggleActionId`; Editor `>=0.8.2`).
```

and, at the end of the section "## Palette scopes" (after the Vault paragraphs, before the `pluginpalette` example), add:

```markdown
The Editor (`plugins/editor`) is the worked example for an exported interface that the *caller*
implements: `dev.jasper.editor.api.EditorDocument` is written by whoever supplies a file (Remote, for
SFTP), while the Editor publishes `Editor` and calls the document's futures from the UI thread.
```

In `AGENTS.md`, in the Architecture rules bullet, after "Remote is bundled too (`plugins/remote`, bundling Apache MINA sshd); it consumes only the Vault API.", add:

```
Editor is bundled too (`plugins/editor`, bundling RSyntaxTextArea); it exports `dev.jasper.editor.api`, and other plugins reach it through that package only.
```

- [ ] **Step 4: Record the status**

In `docs/STATUS.md`, under "## Current state — 2026-09-23", insert as the first bullet:

```markdown
- **Editor plugin, plan A** (branch `claude/editor-plugin`, plan
  `docs/superpowers/plans/2026-09-27-jasper-editor-plugin-a.md`, spec
  `docs/superpowers/specs/2026-09-27-jasper-editor-plugin-design.md`): SDK 0.8.2 adds
  `PluginWindow.onCloseCommand`, quit and restart asking plugin windows' closing guards, and
  `Windows.chooseSaveFile`. The bundled Editor (`plugins/editor`, RSyntaxTextArea 3.6.0) opens, edits
  and safely saves local files in tabs in one window, with a TOML lexer, conflict checks and outside-change
  polling. Planning decisions and deviations are in the plan's header. Not done: plan B (SFTP editing
  through Remote), GUI acceptance ([checklist](editor-verification.md), user-run), merge and push.
```

Replace this plan's `**Status:**` line with: `**Status:** Executed <date> on branch claude/editor-plugin, <method>. Deviations from the task text:` followed by one bullet per deviation recorded during execution (or "none").

- [ ] **Step 5: Run the full check and commit**

Run: `./gradlew check`
Expected: BUILD SUCCESSFUL; `AppDocumentationTest` resolves every new link.

Run the source-hygiene check from AGENTS.md over `plugins`:

```bash
python3 - <<'PY'
import pathlib
for p in pathlib.Path("plugins/editor").rglob("*.java"):
    text = p.read_text(encoding="utf-8")
    bad = sum(1 for c in text if 0xD800 <= ord(c) <= 0xDFFF or 0xE000 <= ord(c) <= 0xF8FF or (ord(c) < 0x20 and c not in "\n\t\r") or ord(c) == 0x7f)
    if bad: print(p, "bad chars:", bad)
PY
```

Expected: no output.

```bash
git add docs AGENTS.md jasper-sdk/README.md
git commit -F - <<'EOF'
docs(editor): the Editor guide, verification checklist and status

🤖 Generated with Claude Code at The Home Depot

Co-Authored-By: Claude <noreply@anthropic.com>
EOF
```
