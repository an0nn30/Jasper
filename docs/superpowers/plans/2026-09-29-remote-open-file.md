# Remote Open File Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Cmd/Ctrl+O ("Open in Editor…") opens a remote file picker when an SSH pane or a remote Editor tab is focused, and File › Open Remote File… opens the same picker with a host dropdown.

**Architecture:** The Editor API gains `OpenRequest` and a default `Editor.opener(Predicate<OpenRequest>)`; the Editor's single Open routine (`EditorWindow.openFrom`) asks registered openers before its local chooser, and the Editor tab's hardcoded Cmd+O goes away so the Keymap binding rules everywhere. Remote extracts the SFTP panel's open-in-Editor logic into `EditorOpening`, adds `RemoteFilePicker` (a modal dialog from `SftpUi.openRemoteFile`), registers `RemoteOpening` as an opener, and adds the `dev.jasper.remote.open-file` action to File and SSH menus.

**Tech Stack:** Java 25 (JetBrains Runtime), Gradle wrapper, Swing, JUnit 5 + AssertJ, the SDK testkit (`FakePluginHost`), Apache MINA sshd loopback server in Remote's tests.

**Spec:** `docs/superpowers/specs/2026-09-29-remote-open-file-design.md`

## Global Constraints

- Work in `.worktrees/remote-open-file` on branch `claude/remote-open-file`; run `git branch --show-current` before every commit. Never commit on `main`.
- Every commit message ends with `Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>`.
- Use `./gradlew`; projects `:jasper-plugin-editor`, `:jasper-plugin-remote`. Do not launch the GUI or the benchmark.
- Remote names Editor types only in `dev.jasper.remote.edit` (`RemoteDocument`, `RemoteEditing`, and the new `RemoteOpening`). `RemotePlugin` constructs them only inside its existing `try { … } catch (NoClassDefFoundError absent)` block.
- No raw control, private-use or unpaired surrogate characters in source; write `"…"` for "…" in Java string literals.
- Versions: Editor `0.4.0` → `0.5.0`; Remote `0.6.2` → `0.7.0`. No SDK change.
- Ids and titles: action `dev.jasper.remote.open-file` "Open Remote File…" (no default binding); dialog title `Open Remote File`; host dropdown placeholder `Choose a host…`; buttons `Cancel`, `Open`.
- Match each file's existing style (Remote's `ui.sftp` classes are written densely; keep that).

---

### Task 1: Editor API — `OpenRequest`, `Editor.opener`, and `Openers`

**Files:**
- Create: `plugins/editor/src/main/java/dev/jasper/editor/api/OpenRequest.java`
- Modify: `plugins/editor/src/main/java/dev/jasper/editor/api/Editor.java`
- Modify: `plugins/editor/src/main/resources/plugin.toml`
- Create: `plugins/editor/src/main/java/dev/jasper/editor/open/package-info.java`
- Create: `plugins/editor/src/main/java/dev/jasper/editor/open/Openers.java`
- Test: `plugins/editor/src/test/java/dev/jasper/editor/open/OpenersTest.java`

**Interfaces:**
- Produces: `record OpenRequest(WindowHandle window, Optional<PaneHandle> pane, Optional<String> documentKey)`; `default Subscription Editor.opener(Predicate<OpenRequest> handler)`; `Openers.register(Predicate<OpenRequest>) : Subscription`, `Openers.handle(OpenRequest) : boolean`.

- [ ] **Step 1: Write the failing test**

`plugins/editor/src/test/java/dev/jasper/editor/open/OpenersTest.java`:

```java
package dev.jasper.editor.open;

import dev.jasper.editor.api.OpenRequest;
import dev.jasper.sdk.Subscription;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class OpenersTest {
    private final Openers openers = new Openers();
    private final List<String> asked = new ArrayList<>();
    private static final OpenRequest REQUEST = new OpenRequest(null, Optional.empty(), Optional.of("remote:x"));

    @Test void handlersRunInRegistrationOrderAndTheFirstToAnswerTrueWins() {
        openers.register(request -> { asked.add("first"); return false; });
        openers.register(request -> { asked.add("second"); return true; });
        openers.register(request -> { asked.add("third"); return true; });
        assertThat(openers.handle(REQUEST)).isTrue();
        assertThat(asked).containsExactly("first", "second");
    }

    @Test void aHandlerThatThrowsCountsAsFalse() {
        openers.register(request -> { throw new IllegalStateException("boom"); });
        openers.register(request -> { asked.add("next"); return true; });
        assertThat(openers.handle(REQUEST)).isTrue();
        assertThat(asked).containsExactly("next");
    }

    @Test void withNoHandlerOrAfterUnsubscribingNothingIsHandled() {
        assertThat(openers.handle(REQUEST)).isFalse();
        Subscription registration = openers.register(request -> true);
        assertThat(openers.handle(REQUEST)).isTrue();
        registration.close();
        assertThat(openers.handle(REQUEST)).isFalse();
    }
}
```

(`OpenRequest` accepts a null window here only because the record does not validate; the Editor never builds one without a window.)

- [ ] **Step 2: Run it to verify it fails**

Run: `./gradlew :jasper-plugin-editor:test --tests 'dev.jasper.editor.open.OpenersTest'`
Expected: compilation FAILS — `cannot find symbol OpenRequest` / `Openers`.

- [ ] **Step 3: Add the API**

`plugins/editor/src/main/java/dev/jasper/editor/api/OpenRequest.java`:

```java
package dev.jasper.editor.api;

import dev.jasper.sdk.terminal.PaneHandle;
import dev.jasper.sdk.terminal.WindowHandle;
import java.util.Optional;

/**
 * What Open in Editor… was invoked on. Since Editor 0.5.0.
 *
 * @param window the workspace window
 * @param pane the focused terminal pane; empty while another kind of tab is selected
 * @param documentKey the key of the Editor tab that is active, when one is and no terminal pane is focused
 */
public record OpenRequest(WindowHandle window, Optional<PaneHandle> pane, Optional<String> documentKey) { }
```

In `plugins/editor/src/main/java/dev/jasper/editor/api/Editor.java`, add the import `import java.util.function.Predicate;` after `import java.util.function.Function;`, and add before the interface's closing brace:

```java

    /**
     * Lets a plugin answer Open in Editor… in its own way, for example by browsing a remote host. Handlers run on the
     * UI thread in registration order before the local file chooser; the first to return true has handled the
     * request. A handler that throws counts as false. Since Editor 0.5.0.
     *
     * @param handler answers a request; true when it handled it
     * @return removes the handler
     */
    default Subscription opener(Predicate<OpenRequest> handler) {
        return () -> { };
    }
```

In `plugins/editor/src/main/resources/plugin.toml` change `version = "0.4.0"` to `version = "0.5.0"`.

- [ ] **Step 4: Write `Openers`**

`plugins/editor/src/main/java/dev/jasper/editor/open/package-info.java`:

```java
/** Open in Editor…: the handlers other plugins register to answer it before the local chooser. UI thread only. May depend on: {@code api}, the SDK. */
package dev.jasper.editor.open;
```

`plugins/editor/src/main/java/dev/jasper/editor/open/Openers.java`:

```java
package dev.jasper.editor.open;

import dev.jasper.editor.api.OpenRequest;
import dev.jasper.sdk.Subscription;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Predicate;

/** The handlers {@code Editor.opener} registers, asked in registration order; one that throws counts as false. */
public final class Openers {
    private final List<Predicate<OpenRequest>> handlers = new CopyOnWriteArrayList<>();

    /**
     * @param handler answers a request; true when it handled it
     * @return removes the handler
     */
    public Subscription register(Predicate<OpenRequest> handler) {
        handlers.add(Objects.requireNonNull(handler));
        return () -> handlers.remove(handler);
    }

    /**
     * @param request what Open in Editor… was invoked on
     * @return whether a handler handled it
     */
    public boolean handle(OpenRequest request) {
        for (Predicate<OpenRequest> handler : handlers) {
            try { if (handler.test(request)) return true; }
            catch (RuntimeException failure) { /* counts as false: the next handler, or the local chooser, answers */ }
        }
        return false;
    }
}
```

- [ ] **Step 5: Run the test to verify it passes**

Run: `./gradlew :jasper-plugin-editor:test --tests 'dev.jasper.editor.open.OpenersTest'`
Expected: PASS (3 tests).

- [ ] **Step 6: Commit**

```bash
git branch --show-current   # must print claude/remote-open-file
git add plugins/editor
git commit -m "Add the Editor API's opener hook

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 2: The Editor's context-aware Open

**Files:**
- Modify: `plugins/editor/src/main/java/dev/jasper/editor/ui/EditorWindow.java`
- Modify: `plugins/editor/src/main/java/dev/jasper/editor/EditorPlugin.java`
- Test: `plugins/editor/src/test/java/dev/jasper/editor/ui/EditorWindowTest.java`
- Test: `plugins/editor/src/test/java/dev/jasper/editor/EditorPluginTest.java`

**Interfaces:**
- Consumes: `OpenRequest`, `Openers` (Task 1).
- Produces: `EditorWindow(EditorEnvironment, Runnable onClosed, Consumer<EditorDocument> opened, Predicate<OpenRequest> openers)` (the two- and three-argument constructors stay, with no openers); `public void EditorWindow.openFrom(WindowHandle window, Optional<PaneHandle> pane)`; `openChooser()` now calls `openFrom(lastWindow(), Optional.empty())`. The "open" command keeps id and title "Open…" but has no stroke and is not bound.

- [ ] **Step 1: Write the failing tests**

In `plugins/editor/src/test/java/dev/jasper/editor/ui/EditorWindowTest.java` add this test:

```java
    @Test void theOpenCommandHasNoKeyButVimStillRunsIt() throws Exception {
        settings.set(new EditorSettings("Monospaced", 13, 4, false, 1, true));
        EditorTab tab = opened(file("from.txt", "one\n"));
        assertThat(tab.area().getInputMap(javax.swing.JComponent.WHEN_FOCUSED).get(KeyNames.stroke(java.awt.event.KeyEvent.VK_O, 0)))
            .as("Cmd/Ctrl+O is left to the Keymap's Open in Editor…").isNotEqualTo("open");
        Path other = file("other.txt", "two\n");
        host.queuePathSelection(List.of(other));
        vimKeys(tab, ":action open<CR>");
        drain();
        assertThat(window.selected().orElseThrow().session().name()).isEqualTo("other.txt");
    }
```

In `plugins/editor/src/test/java/dev/jasper/editor/EditorPluginTest.java` add the import `import dev.jasper.editor.api.OpenRequest;` and these tests:

```java
    @Test void openInEditorShowsTheLocalChooserWhenNoOpenerAnswers() throws Exception {
        try (var host = new FakePluginHost(home.resolve("plugins"))) {
            host.start(INFO, Set.of(), Set.of(), plugin());
            UUID terminal = host.addTerminalWindow();
            var consumer = new AtomicReference<PluginContext>();
            host.start(new PluginInfo("test.consumer", "Consumer", "1.0.0", Set.of()), Set.of("dev.jasper.editor"), Set.of(), consumer::set);
            var asked = new ArrayList<OpenRequest>();
            consumer.get().services().require(Editor.class).opener(request -> { asked.add(request); return false; });
            host.queuePathSelection(List.of(Files.writeString(home.resolve("local.txt"), "x\n")));
            assertThat(host.invoke("dev.jasper.editor.open", terminal, null)).isTrue();
            drain(host);
            assertThat(asked).singleElement().satisfies(request -> {
                assertThat(request.window().id()).isEqualTo(terminal);
                assertThat(request.pane()).isEmpty();
                assertThat(request.documentKey()).isEmpty();
            });
            assertThat(host.contentTabs()).hasSize(1);
        }
    }

    @Test void anOpenerThatAnswersReplacesTheChooserAndSeesTheActiveEditorTabsKey() throws Exception {
        try (var host = new FakePluginHost(home.resolve("plugins"))) {
            host.start(INFO, Set.of(), Set.of(), plugin());
            UUID terminal = host.addTerminalWindow();
            var consumer = new AtomicReference<PluginContext>();
            host.start(new PluginInfo("test.consumer", "Consumer", "1.0.0", Set.of()), Set.of("dev.jasper.editor"), Set.of(), consumer::set);
            Editor editor = consumer.get().services().require(Editor.class);
            editor.open(new StubDocument("stub:/srv/app.conf", "app.conf", "dustin@prod:/srv/app.conf", "prod"));
            drain(host);
            var asked = new ArrayList<OpenRequest>();
            Subscription registration = editor.opener(request -> { asked.add(request); return true; });
            host.queuePathSelection(List.of(Files.writeString(home.resolve("unused.txt"), "x\n")));
            assertThat(host.invoke("dev.jasper.editor.open", terminal, null)).isTrue();
            drain(host);
            assertThat(asked).singleElement().satisfies(request -> assertThat(request.documentKey()).contains("stub:/srv/app.conf"));
            assertThat(host.contentTabs()).as("the chooser never ran").hasSize(1);
            registration.close();
        }
    }
```

- [ ] **Step 2: Run them to verify they fail**

Run: `./gradlew :jasper-plugin-editor:test --tests 'dev.jasper.editor.EditorPluginTest' --tests 'dev.jasper.editor.ui.EditorWindowTest'`
Expected: FAIL — the openers are never asked (`asked` is empty), and the input map still binds Cmd/Ctrl+O to "open".

- [ ] **Step 3: Route Open through the openers**

In `plugins/editor/src/main/java/dev/jasper/editor/ui/EditorWindow.java`:

Add imports:

```java
import dev.jasper.editor.api.OpenRequest;
import dev.jasper.sdk.terminal.PaneHandle;
import dev.jasper.sdk.terminal.WindowHandle;
import java.util.function.Predicate;
```

After `private final java.util.function.Consumer<EditorDocument> opened;` add:

```java
    private final Predicate<OpenRequest> openers;
```

Replace the three-argument constructor's header and first lines

```java
    public EditorWindow(EditorEnvironment environment, Runnable onClosed, java.util.function.Consumer<EditorDocument> opened) {
        this.environment = environment;
        this.context = environment.context();
        this.onClosed = onClosed;
        this.opened = java.util.Objects.requireNonNull(opened);
```

with

```java
    public EditorWindow(EditorEnvironment environment, Runnable onClosed, java.util.function.Consumer<EditorDocument> opened) {
        this(environment, onClosed, opened, request -> false);
    }

    /**
     * Creates the window, not yet shown.
     *
     * @param environment what the window needs from the plugin
     * @param onClosed runs once the window has closed
     * @param opened told a document once its new tab's first read succeeds, and each time its loaded tab is opened again
     * @param openers asked before the local chooser by Open in Editor…; true when one handled the request
     */
    public EditorWindow(EditorEnvironment environment, Runnable onClosed, java.util.function.Consumer<EditorDocument> opened,
                        Predicate<OpenRequest> openers) {
        this.environment = environment;
        this.context = environment.context();
        this.onClosed = onClosed;
        this.opened = java.util.Objects.requireNonNull(opened);
        this.openers = java.util.Objects.requireNonNull(openers);
```

(The three-argument constructor's JavaDoc stays above it unchanged.)

In the `commands = List.of(` list, replace

```java
            command("open", "Open…", KeyEvent.VK_O, 0, this::openChooser),
```

with

```java
            new Command("open", "Open…", Optional.empty(), false, this::openChooser),
```

Replace

```java
    public void openChooser() {
        Optional<Path> near = selected().flatMap(tab -> localFile(tab.session())).map(Path::getParent);
        openLocalFiles(context.windows().chooseFiles(owner(), "Open", near));
    }
```

with

```java
    /** The Editor's own "open" command (Vim's {@code :action open}): Open in Editor… for the window the Editor was last used from. */
    public void openChooser() { openFrom(lastWindow(), Optional.empty()); }

    /**
     * Open in Editor…: the openers first, then the local chooser, which starts in the active local document's folder.
     *
     * @param window the workspace window it was invoked in; null skips the openers
     * @param pane the focused terminal pane
     */
    public void openFrom(WindowHandle window, Optional<PaneHandle> pane) {
        Optional<EditorTab> active = hosted.entrySet().stream().filter(entry -> entry.getValue().isActive()).map(java.util.Map.Entry::getKey).findFirst();
        Optional<String> key = pane.isPresent() ? Optional.empty() : active.flatMap(tab -> tab.session().document()).map(EditorDocument::key);
        if (window != null && openers.test(new OpenRequest(window, pane, key))) return;
        Optional<Path> near = active.or(this::selected).flatMap(tab -> localFile(tab.session())).map(Path::getParent);
        WindowOwner owner = window != null ? window : owner();
        openLocalFiles(context.windows().chooseFiles(owner, "Open in Editor", near), owner);
    }

    /** @return the terminal window the Editor was last opened from, else the active one; null when there is none */
    private WindowHandle lastWindow() {
        if (origin instanceof WindowHandle window && window.isOpen()) return window;
        return context.terminals().activeWindow().orElse(null);
    }
```

- [ ] **Step 4: Wire the plugin**

In `plugins/editor/src/main/java/dev/jasper/editor/EditorPlugin.java`:

Add imports:

```java
import dev.jasper.editor.api.OpenRequest;
import dev.jasper.editor.open.Openers;
import java.util.function.Predicate;
```

Add a field after `private RecentFiles recent;`:

```java
    private final Openers openers = new Openers();
```

Inside the published `Editor`, after the `reopener` override, add:

```java
            @Override public Subscription opener(Predicate<OpenRequest> handler) { return openers.register(handler); }
```

Replace the `OPEN` action's handler

```java
            invoked -> {
                List<Path> files = context.windows().chooseFiles(invoked.window(), "Open in Editor", Optional.empty());
                if (!files.isEmpty()) window().openLocalFiles(files, invoked.window());
            });
```

with

```java
            invoked -> window().openFrom(invoked.window(), invoked.pane()));
```

Replace the `window()` factory line

```java
        if (window == null) window = new EditorWindow(environment, () -> window = null, document -> recent.record(document, Instant.now()));
```

with

```java
        if (window == null) window = new EditorWindow(environment, () -> window = null, document -> recent.record(document, Instant.now()), openers::handle);
```

If `Path` or `List` imports become unused, leave them only if still used elsewhere in the file (they are: `open(Path file)` and the action keyword lists).

- [ ] **Step 5: Run the Editor's tests**

Run: `./gradlew :jasper-plugin-editor:test`
Expected: PASS, including the three new tests and the existing `theOpenActionUsesATerminalWindowsChooserAndClosingTheWindowLetsAFreshOneOpen` (the chooser is still asked with the terminal window as owner).

- [ ] **Step 6: Commit**

```bash
git branch --show-current   # must print claude/remote-open-file
git add plugins/editor
git commit -m "Ask openers before the local chooser in Open in Editor

The Editor tab's own Cmd/Ctrl+O is gone, so the Keymap's binding for Open in
Editor rules in terminal panes and Editor tabs alike.

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 3: Share the SFTP panel's open-in-Editor logic, and a pick mode

**Files:**
- Create: `plugins/remote/src/main/java/dev/jasper/remote/ui/sftp/EditorOpening.java`
- Modify: `plugins/remote/src/main/java/dev/jasper/remote/ui/sftp/SftpUi.java`
- Modify: `plugins/remote/src/main/java/dev/jasper/remote/ui/sftp/SftpPanel.java`
- Test: `plugins/remote/src/test/java/dev/jasper/remote/ui/sftp/SftpPanelTest.java`

**Interfaces:**
- Produces: package-private `EditorOpening.Browser(Executor ui, BooleanSupplier live, Predicate<SftpController.Capture> showing, Consumer<String> error, Consumer<String> navigate, Runnable opened)`; `static void EditorOpening.activate(RemoteEditing, Browser, SftpController.Capture, FileEntry, WindowHandle)`; `static void EditorOpening.openSelected(RemoteEditing, Browser, SftpController.Capture, WindowHandle)`; `public void SftpPanel.pickMode()`.

This task is a refactor guarded by the existing `RemoteEditingPluginTest` (Enter opens files and links, a link to a folder navigates, the 20-file cap), plus one new panel test.

- [ ] **Step 1: Write the failing test**

Add to `plugins/remote/src/test/java/dev/jasper/remote/ui/sftp/SftpPanelTest.java` (use the class's existing imports; add `import java.util.List;` and `import javax.swing.ListSelectionModel;` if missing):

```java
    @Test void pickModeKeepsNavigationAndSeveralSelectionsButDropsTransfersAndTheMenu() {
        var panel = new SftpPanel(name -> null);
        panel.pickMode();
        assertThat(panel.table().getSelectionModel().getSelectionMode()).isEqualTo(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
        assertThat(panel.table().getComponentPopupMenu()).isNull();
        assertThat(panel.following()).isTrue();
        List<String> names = new java.util.ArrayList<>();
        new Object() { void walk(java.awt.Container root) {
            for (var child : root.getComponents()) {
                if (child instanceof javax.swing.JButton button && button.getAccessibleContext().getAccessibleName() != null) names.add(button.getAccessibleContext().getAccessibleName());
                if (child instanceof javax.swing.JCheckBox box && box.isVisible()) names.add(box.getText());
                if (child instanceof java.awt.Container container) walk(container);
            }
        } }.walk(panel);
        assertThat(names).contains("Up", "Refresh").doesNotContain("Download selected", "Upload files", "New folder", "Delete selected", "Copy paths", "Follow terminal folder");
    }
```

- [ ] **Step 2: Run it to verify it fails**

Run: `./gradlew :jasper-plugin-remote:test --tests 'dev.jasper.remote.ui.sftp.SftpPanelTest'`
Expected: compilation FAILS — `cannot find symbol pickMode()`.

- [ ] **Step 3: Add pick mode**

In `plugins/remote/src/main/java/dev/jasper/remote/ui/sftp/SftpPanel.java`, after `destinationMode()` add:

```java
    /** Open Remote File: files and folders, several at once, with navigation only; no transfers, deletion, follow or menu. */
    public void pickMode() { follow.setVisible(false);for(String name:List.of("Download selected","Upload files","New folder","Delete selected","Copy paths")) buttons.get(name).getParent().remove(buttons.get(name));table.setComponentPopupMenu(null); }
```

- [ ] **Step 4: Extract the open logic**

`plugins/remote/src/main/java/dev/jasper/remote/ui/sftp/EditorOpening.java`:

```java
package dev.jasper.remote.ui.sftp;

import dev.jasper.remote.edit.RemoteEditing;
import dev.jasper.remote.sftp.FileEntry;
import dev.jasper.remote.sftp.FilePaths;
import dev.jasper.sdk.terminal.WindowHandle;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.*;

/** Opening what an SFTP browser shows in the Editor, shared by the SFTP panel and Open Remote File. UI thread only. */
final class EditorOpening {
    /**
     * The browser the files came from.
     *
     * @param ui the UI thread
     * @param live whether the browser and its window are still open
     * @param showing whether the browser still shows the connection and folder a capture was taken from
     * @param error shows an error in the browser
     * @param navigate enters a folder in the browser
     * @param opened runs after files were handed to the Editor
     */
    record Browser(Executor ui,BooleanSupplier live,Predicate<SftpController.Capture> showing,Consumer<String> error,Consumer<String> navigate,Runnable opened) {}
    private EditorOpening() {}
    /** Double-click or Enter on one file or link: a file opens in the Editor; a link opens when it leads to a file and navigates when it leads to a folder. */
    static void activate(RemoteEditing editing,Browser browser,SftpController.Capture capture,FileEntry entry,WindowHandle owner) {
        String path;try { path=FilePaths.child(capture.directory(),entry.name()); } catch(IOException invalid) { browser.error().accept(invalid.getMessage());return; }
        if(entry.kind()==FileEntry.Kind.FILE) { editing.open(capture.identity(),path,owner);browser.opened().run();return; }
        editing.kinds(capture.identity(),List.of(path),owner).whenComplete((kinds,failure)->browser.ui().execute(()-> {
            if(!browser.live().getAsBoolean()) return;
            if(failure!=null) { if(browser.showing().test(capture)) browser.error().accept(message(failure));return; }
            var kind=kinds.getFirst();
            if(kind.isEmpty()) { if(browser.showing().test(capture)) browser.error().accept(entry.name()+": its target does not exist"); }
            else if(kind.orElseThrow()==FileEntry.Kind.FILE) { editing.open(capture.identity(),path,owner);browser.opened().run(); }
            else if(kind.orElseThrow()==FileEntry.Kind.DIRECTORY) { if(browser.showing().test(capture)) browser.navigate().accept(path); }
            else if(browser.showing().test(capture)) browser.error().accept(entry.name()+" is not a regular file");
        }));
    }
    /** Open: the selected files and links to files, at most {@link RemoteEditing#MAX_OPEN}. A failed link resolution still opens the selection's regular files. */
    static void openSelected(RemoteEditing editing,Browser browser,SftpController.Capture capture,WindowHandle owner) {
        var candidates=capture.selection().stream().filter(entry->entry.kind()==FileEntry.Kind.FILE || entry.kind()==FileEntry.Kind.LINK).toList();if(candidates.isEmpty()) return;
        var paths=new ArrayList<String>();var links=new ArrayList<String>();
        try { for(var entry:candidates) { String path=FilePaths.child(capture.directory(),entry.name());paths.add(path);if(entry.kind()==FileEntry.Kind.LINK) links.add(path); } }
        catch(IOException invalid) { browser.error().accept(invalid.getMessage());return; }
        CompletableFuture<List<Optional<FileEntry.Kind>>> resolved=links.isEmpty()?CompletableFuture.completedFuture(List.of()):editing.kinds(capture.identity(),links,owner);
        resolved.whenComplete((kinds,failure)->browser.ui().execute(()-> {
            if(!browser.live().getAsBoolean()) return;
            var files=new ArrayList<String>();
            if(failure==null) {
                int link=0;
                for(int i=0;i<candidates.size();i++) {
                    if(candidates.get(i).kind()==FileEntry.Kind.FILE) files.add(paths.get(i));
                    else if(kinds.get(link++).filter(kind->kind==FileEntry.Kind.FILE).isPresent()) files.add(paths.get(i));
                }
            } else for(int i=0;i<candidates.size();i++) if(candidates.get(i).kind()==FileEntry.Kind.FILE) files.add(paths.get(i));
            for(String path:files.subList(0,Math.min(files.size(),RemoteEditing.MAX_OPEN))) editing.open(capture.identity(),path,owner);
            if(!files.isEmpty()) browser.opened().run();
            if(!browser.showing().test(capture)) return;
            if(failure!=null) browser.error().accept(message(failure));
            else if(files.size()>RemoteEditing.MAX_OPEN) browser.error().accept("Opened "+RemoteEditing.MAX_OPEN+" of "+files.size()+" files.");
        }));
    }
    static String message(Throwable failure) { while((failure instanceof CompletionException || failure instanceof ExecutionException) && failure.getCause()!=null) failure=failure.getCause();return failure.getMessage()==null?"SFTP operation failed":failure.getMessage(); }
}
```

In `plugins/remote/src/main/java/dev/jasper/remote/ui/sftp/SftpUi.java`, replace the whole `activate(View view,FileEntry entry)` method (with its JavaDoc) and the whole `openSelected(View view)` method (with its JavaDoc) by:

```java
    private EditorOpening.Browser browser(View view,WindowHandle owner) {
        return new EditorOpening.Browser(ui,()->!closed && owner.isOpen(),capture->stillShowing(view,owner,capture),view.panel()::error,view.controller()::manual,()->{});
    }
    /** Double-click or Enter on one file or link; see {@link EditorOpening#activate}. */
    private void activate(View view,FileEntry entry) {
        var captured=view.controller().capture();if(captured.isEmpty() || closed || editing==null) return;WindowHandle owner=view.host().window();
        EditorOpening.activate(editing,browser(view,owner),captured.orElseThrow(),entry,owner);
    }
    /** Open in Editor on the selection; see {@link EditorOpening#openSelected}. */
    private void openSelected(View view) {
        var captured=view.controller().capture();if(captured.isEmpty() || closed || editing==null) return;WindowHandle owner=view.host().window();
        EditorOpening.openSelected(editing,browser(view,owner),captured.orElseThrow(),owner);
    }
```

- [ ] **Step 5: Run the Remote tests**

Run: `./gradlew :jasper-plugin-remote:test`
Expected: PASS, including `RemoteEditingPluginTest` unchanged and the new `SftpPanelTest` test.

- [ ] **Step 6: Commit**

```bash
git branch --show-current   # must print claude/remote-open-file
git add plugins/remote
git commit -m "Share the SFTP panel's open-in-Editor logic and add a pick mode

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 4: The Open Remote File picker and action

**Files:**
- Create: `plugins/remote/src/main/java/dev/jasper/remote/ui/sftp/RemoteFilePicker.java`
- Modify: `plugins/remote/src/main/java/dev/jasper/remote/ui/sftp/SftpUi.java`
- Modify: `plugins/remote/src/main/java/dev/jasper/remote/RemotePlugin.java`
- Modify: `plugins/remote/src/test/java/dev/jasper/remote/RemoteEditingPluginTest.java` (fixture only)
- Test: `plugins/remote/src/test/java/dev/jasper/remote/RemoteOpenFileTest.java`

**Interfaces:**
- Consumes: `EditorOpening`, `SftpPanel.pickMode()` (Task 3).
- Produces: `RemoteFilePicker` (constructor below; `start(ConnectionIdentity, String)`, `navigate(String)`, `error(String)`, `capture()`, `hosts()`, `browser()`, `openButton()`, `cancelButton()`, package-private `closed()`); `public void SftpUi.openRemoteFile(WindowHandle window, Optional<ConnectionIdentity> identity, String directory)`; `RemotePlugin.OPEN_FILE = "dev.jasper.remote.open-file"`; test fixture `RemoteEditingPluginTest.Browsed setUp(...)`.

- [ ] **Step 1: Extend the test fixture**

In `plugins/remote/src/test/java/dev/jasper/remote/RemoteEditingPluginTest.java`, replace the second `browse` overload

```java
    static SftpPanel browse(FakePluginHost host, LoopbackServer server, Path files, Path dir, Optional<FakeEditor> editor, int rows, SftpSubsystemFactory factory) throws Exception {
```

(through its `return panel;` and closing brace) with:

```java
    static SftpPanel browse(FakePluginHost host, LoopbackServer server, Path files, Path dir, Optional<FakeEditor> editor, int rows, SftpSubsystemFactory factory) throws Exception {
        return setUp(host, server, files, dir, editor, rows, factory).panel();
    }

    /** What {@link #setUp} leaves open: the SFTP panel, the terminal window and SSH pane, and Remote's context. */
    record Browsed(SftpPanel panel, UUID window, UUID pane, PluginContext context) { }

    /** As {@link #browse}, returning the window, the SSH pane and Remote's context too. */
    static Browsed setUp(FakePluginHost host, LoopbackServer server, Path files, Path dir, Optional<FakeEditor> editor, int rows, SftpSubsystemFactory factory) throws Exception {
        server.server.setFileSystemFactory(new VirtualFileSystemFactory(files.toRealPath()));
        server.server.setSubsystemFactories(List.of(factory));
        var vault = new FakeVault();
        host.start(FakeVault.INFO, Set.of(), Set.of(), vault);
        editor.ifPresent(fake -> host.start(EDITOR, Set.of(), Set.of(), fake));
        RemotePlugin plugin = new TestRemotePlugin(Runnable::run, context -> Optional.empty(), (delay, task) -> () -> {}, dir.resolve("ssh"));
        var context = host.start(RemotePluginTest.INFO, Set.of(), Set.of("dev.jasper.vault", "dev.jasper.editor"), plugin);
        UUID credential = vault.password("deploy login", "deploy", "s3cret");
        RemoteHost prod = RemoteHost.create("prod", "127.0.0.1", server.port(), "", new Auth.Vault(credential), "", Optional.empty()).withFollowDirectory(false);
        plugin.store().put(prod);
        RemotePluginTest.settle(host);
        new KnownHosts(context.dataDirectory().resolve("known_hosts"), Optional.empty()).trust("127.0.0.1", server.port(), server.hostPublicKey());
        UUID window = host.addTerminalWindow();
        host.activateTerminalWindow(window);
        var panel = (SftpPanel) host.openPanel(SftpUi.PANEL, window);
        plugin.openHost(context.terminals().window(window).orElseThrow(), prod); RemotePluginTest.settle(host);
        UUID pane = host.terminalPanes().getFirst();
        host.focusTerminalPane(pane); host.flush();
        RemotePluginTest.awaitRowCount(host, panel, rows);
        return new Browsed(panel, window, pane, context);
    }
```

(This is the old body with the pane id kept in a variable; `PluginContext` is already imported by the class's `FakeEditor`.)

In the same file's `FakeEditor`, add a list and an override so tests can call Remote's opener:

```java
        final List<java.util.function.Predicate<dev.jasper.editor.api.OpenRequest>> openers = new CopyOnWriteArrayList<>();
```

and inside its anonymous `Editor`:

```java
                @Override public dev.jasper.sdk.Subscription opener(java.util.function.Predicate<dev.jasper.editor.api.OpenRequest> handler) {
                    openers.add(handler);
                    return () -> openers.remove(handler);
                }
```

- [ ] **Step 2: Write the failing tests**

`plugins/remote/src/test/java/dev/jasper/remote/RemoteOpenFileTest.java`:

```java
package dev.jasper.remote;

import dev.jasper.remote.client.LoopbackServer;
import dev.jasper.remote.hosts.RemoteHost;
import dev.jasper.remote.ui.sftp.RemoteFilePicker;
import dev.jasper.editor.api.EditorDocument;
import dev.jasper.sdk.testing.FakePluginHost;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import org.apache.sshd.sftp.server.SftpSubsystemFactory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;
import static dev.jasper.remote.RemoteEditingPluginTest.*;
import static org.assertj.core.api.Assertions.assertThat;

/** File › Open Remote File…: the picker, against a loopback SFTP server. POSIX paths and links. */
@DisabledOnOs(OS.WINDOWS)
class RemoteOpenFileTest {
    static final String ACTION = "dev.jasper.remote.open-file";

    static RemoteFilePicker picker(FakePluginHost host) throws Exception {
        await(host, () -> host.windowContent("dev.jasper.remote", "Open Remote File").isPresent(), "the picker is shown");
        return (RemoteFilePicker) host.windowContent("dev.jasper.remote", "Open Remote File").orElseThrow();
    }

    @Test void itIsInTheFileAndSshMenusOnlyWithTheEditor(@TempDir Path dir) throws Exception {
        Path files = Files.createDirectories(dir.resolve("files"));
        Files.writeString(files.resolve("notes.toml"), "a = 1\n");
        try (var server = new LoopbackServer(); var host = new FakePluginHost()) {
            setUp(host, server, files, dir, Optional.of(new FakeEditor()), 1, new SftpSubsystemFactory());
            assertThat(host.actions()).anySatisfy(line -> assertThat(line).startsWith(ACTION + "|Open Remote File…|"));
            assertThat(host.menu("FILE")).contains("item:" + ACTION);
            assertThat(host.menu("top:dev.jasper.remote.menu")).contains("item:" + ACTION);
            host.stopAll();
        }
        try (var server = new LoopbackServer(); var host = new FakePluginHost()) {
            setUp(host, server, files, dir, Optional.empty(), 1, new SftpSubsystemFactory());
            assertThat(host.actions()).noneSatisfy(line -> assertThat(line).startsWith(ACTION + "|"));
            assertThat(host.menu("FILE")).doesNotContain("item:" + ACTION);
            host.stopAll();
        }
    }

    @Test void fromAnSshPaneItStartsOnThatConnectionAndOpensTheSelectedFiles(@TempDir Path dir) throws Exception {
        Path files = Files.createDirectories(dir.resolve("files"));
        Files.writeString(files.resolve("a.txt"), "a\n");
        Files.writeString(files.resolve("b.txt"), "b\n");
        Files.createDirectory(files.resolve("conf"));
        var editor = new FakeEditor();
        try (var server = new LoopbackServer(); var host = new FakePluginHost()) {
            var set = setUp(host, server, files, dir, Optional.of(editor), 3, new SftpSubsystemFactory());
            assertThat(host.invoke(ACTION, set.window(), set.pane())).isTrue();
            var picker = picker(host);
            assertThat(((RemoteHost) picker.hosts().getSelectedItem()).name()).isEqualTo("prod");
            RemotePluginTest.awaitRowCount(host, picker.browser(), 3);
            picker.browser().table().selectAll();
            assertThat(picker.openButton().isEnabled()).isTrue();
            picker.openButton().doClick();
            await(host, () -> editor.documents.size() == 2, "both files open");
            assertThat(editor.documents).extracting(EditorDocument::name).containsExactlyInAnyOrder("a.txt", "b.txt");
            await(host, () -> host.windowContent("dev.jasper.remote", "Open Remote File").isEmpty(), "the picker closes");
            host.stopAll();
            assertThat(host.failures()).isEmpty();
        }
    }

    @Test void foldersAreEnteredAndALinkToAFileOpens(@TempDir Path dir) throws Exception {
        Path files = Files.createDirectories(dir.resolve("files"));
        Files.createDirectory(files.resolve("conf"));
        Files.writeString(files.resolve("conf/app.toml"), "x = 1\n");
        Files.createSymbolicLink(files.resolve("alias.toml"), Path.of("conf/app.toml"));
        var editor = new FakeEditor();
        try (var server = new LoopbackServer(); var host = new FakePluginHost()) {
            var set = setUp(host, server, files, dir, Optional.of(editor), 2, new SftpSubsystemFactory());
            host.invoke(ACTION, set.window(), set.pane());
            var picker = picker(host);
            RemotePluginTest.awaitRowCount(host, picker.browser(), 2);
            enter(picker.browser(), "conf");
            await(host, () -> picker.browser().directory().equals("/conf"), "the folder is entered");
            assertThat(editor.documents).isEmpty();
            picker.navigate("/");
            RemotePluginTest.awaitRowCount(host, picker.browser(), 2);
            enter(picker.browser(), "alias.toml");
            await(host, () -> editor.documents.size() == 1, "the link to a file opens");
            await(host, () -> host.windowContent("dev.jasper.remote", "Open Remote File").isEmpty(), "the picker closes");
            host.stopAll();
            assertThat(host.failures()).isEmpty();
        }
    }

    @Test void withoutAnSshPaneTheHostIsChosenInThePickerAndCancelCloses(@TempDir Path dir) throws Exception {
        Path files = Files.createDirectories(dir.resolve("files"));
        Files.writeString(files.resolve("notes.toml"), "a = 1\n");
        var editor = new FakeEditor();
        try (var server = new LoopbackServer(); var host = new FakePluginHost()) {
            var set = setUp(host, server, files, dir, Optional.of(editor), 1, new SftpSubsystemFactory());
            assertThat(host.invoke(ACTION, set.window(), null)).isTrue();
            var picker = picker(host);
            assertThat(picker.hosts().getSelectedIndex()).isEqualTo(-1);
            assertThat(picker.openButton().isEnabled()).isFalse();
            picker.hosts().setSelectedIndex(0);
            RemotePluginTest.awaitRowCount(host, picker.browser(), 1);
            picker.cancelButton().doClick();
            assertThat(host.windowContent("dev.jasper.remote", "Open Remote File")).isEmpty();
            assertThat(editor.documents).isEmpty();
            host.stopAll();
            assertThat(host.failures()).isEmpty();
        }
    }
}
```

- [ ] **Step 3: Run them to verify they fail**

Run: `./gradlew :jasper-plugin-remote:test --tests 'dev.jasper.remote.RemoteOpenFileTest'`
Expected: compilation FAILS — `cannot find symbol RemoteFilePicker`.

- [ ] **Step 4: Write the picker**

`plugins/remote/src/main/java/dev/jasper/remote/ui/sftp/RemoteFilePicker.java`:

```java
package dev.jasper.remote.ui.sftp;

import dev.jasper.remote.client.ConnectionIdentity;
import dev.jasper.remote.hosts.RemoteHost;
import dev.jasper.remote.sftp.FileEndpoint;
import dev.jasper.remote.sftp.FileEntry;
import dev.jasper.sdk.ui.IconName;
import java.awt.*;
import java.nio.file.Path;
import java.util.*;
import java.util.List;
import java.util.concurrent.*;
import java.util.function.*;
import javax.swing.*;

/** Open Remote File: a host, a browsable listing of files and folders, and Open. What opening does is the caller's. */
public final class RemoteFilePicker extends JPanel implements AutoCloseable {
    private final JComboBox<RemoteHost> hosts;
    private final SftpPanel browser;
    private final SftpController controller;
    private final JButton open=new JButton("Open"),cancel=new JButton("Cancel");
    private final Executor ui;
    private final Function<UUID,CompletableFuture<ConnectionIdentity>> resolve;
    private CompletableFuture<ConnectionIdentity> resolving;
    private long generation;
    private boolean closed,quiet;
    /**
     * @param saved the saved hosts, in the dropdown's order
     * @param cache the listing cache folder
     * @param background where listings run
     * @param ui the UI thread
     * @param icons the host's icons
     * @param resolve signs in to a chosen host
     * @param endpoints opens a file endpoint on a connection
     * @param activate double-click or Enter on one file or link
     * @param openSelected Open on the selection
     * @param cancelled Cancel
     */
    public RemoteFilePicker(List<RemoteHost> saved,Path cache,Executor background,Executor ui,Function<IconName,Icon> icons,
                            Function<UUID,CompletableFuture<ConnectionIdentity>> resolve,Function<ConnectionIdentity,CompletableFuture<FileEndpoint>> endpoints,
                            BiConsumer<SftpController.Capture,FileEntry> activate,Consumer<SftpController.Capture> openSelected,Runnable cancelled) {
        super(new BorderLayout(0,6));this.ui=ui;this.resolve=resolve;setBorder(BorderFactory.createEmptyBorder(8,8,8,8));
        hosts=new JComboBox<>(saved.toArray(RemoteHost[]::new));hosts.setSelectedIndex(-1);hosts.getAccessibleContext().setAccessibleName("Host");
        hosts.setRenderer(new DefaultListCellRenderer() { @Override public Component getListCellRendererComponent(JList<?> list,Object value,int index,boolean selected,boolean focus) { super.getListCellRendererComponent(list,value,index,selected,focus);putClientProperty("html.disable",true);setText(value instanceof RemoteHost host?host.name()+" — "+host.hostname():"Choose a host…");return this; } });
        add(hosts,BorderLayout.NORTH);
        browser=new SftpPanel(icons);browser.pickMode();controller=new SftpController(cache,background,ui,endpoints,browser);
        browser.editing(new SftpPanel.Editing(entry->controller.capture().ifPresent(capture->activate.accept(capture,entry)),()->controller.capture().ifPresent(openSelected)));
        browser.table().getSelectionModel().addListSelectionListener(event->openEnabled());browser.onLoaded(this::openEnabled);add(browser,BorderLayout.CENTER);
        var buttons=new JPanel(new FlowLayout(FlowLayout.RIGHT));cancel.addActionListener(event->cancelled.run());buttons.add(cancel);buttons.add(open);open.setEnabled(false);
        open.addActionListener(event->controller.capture().ifPresent(openSelected));add(buttons,BorderLayout.SOUTH);
        hosts.addActionListener(event->{ if(!closed && !quiet && hosts.getSelectedItem() instanceof RemoteHost host) choose(host); });
        setPreferredSize(new Dimension(620,480));
    }
    /** Starts on {@code identity} at {@code directory} (the home folder when blank), selecting its host without signing in again. */
    public void start(ConnectionIdentity identity,String directory) {
        if(closed) return;generation++;if(resolving!=null) resolving.cancel(true);
        quiet=true;try { for(int i=0;i<hosts.getItemCount();i++) if(hosts.getItemAt(i).id().equals(identity.host().id())) { hosts.setSelectedIndex(i);break; } } finally { quiet=false; }
        controller.open(identity.host().id(),identity,directory==null?"":directory,true);
    }
    /** Enters {@code path}, absolute or relative to the folder shown. */
    public void navigate(String path) { if(!closed) controller.manual(path); }
    public void error(String text) { if(!closed) browser.error(text); }
    public Optional<SftpController.Capture> capture() { return controller.capture(); }
    boolean closed() { return closed; }
    public JComboBox<RemoteHost> hosts() { return hosts; }
    public SftpPanel browser() { return browser; }
    public JButton openButton() { return open; }
    public JButton cancelButton() { return cancel; }
    private void choose(RemoteHost host) {
        long token=++generation;open.setEnabled(false);controller.cancel();if(resolving!=null) resolving.cancel(true);
        browser.busy(true,"Connecting…");resolving=resolve.apply(host.id());resolving.whenComplete((identity,failure)->ui.execute(()-> {
            if(closed || token!=generation) return;
            if(failure!=null) browser.error("Cannot connect: "+EditorOpening.message(failure));else controller.open(host.id(),identity,"",true);
        }));
    }
    private void openEnabled() { open.setEnabled(!closed && controller.capture().map(capture->capture.selection().stream().anyMatch(entry->entry.kind()==FileEntry.Kind.FILE || entry.kind()==FileEntry.Kind.LINK)).orElse(false)); }
    @Override public void close() { if(closed) return;closed=true;generation++;if(resolving!=null) resolving.cancel(true);controller.close(); }
}
```

The four component accessors are public because the tests in package `dev.jasper.remote` drive them; `closed()` stays package-private.

- [ ] **Step 5: Show it from `SftpUi`**

In `plugins/remote/src/main/java/dev/jasper/remote/ui/sftp/SftpUi.java`, after `public void editing(RemoteEditing editing) { this.editing=editing; }` add:

```java
    /**
     * Open Remote File…: a modal picker whose files open in the Editor, starting on {@code identity} at {@code directory}
     * (the home folder when blank), or with no host chosen. Does nothing without the Editor.
     */
    public void openRemoteFile(WindowHandle window,Optional<ConnectionIdentity> identity,String directory) {
        if(closed || editing==null) return;
        var dialog=context.windows().dialog(new DialogSpec("Open Remote File",window,true));dialogs.add(dialog);
        var reference=new java.util.concurrent.atomic.AtomicReference<RemoteFilePicker>();
        Supplier<EditorOpening.Browser> browser=()-> { var picker=reference.get();return new EditorOpening.Browser(ui,()->!closed && window.isOpen() && !picker.closed(),
            capture->picker.capture().filter(now->now.identity().equals(capture.identity()) && now.directory().equals(capture.directory())).isPresent(),picker::error,picker::navigate,dialog::close); };
        var picker=new RemoteFilePicker(hosts.get(),context.dataDirectory().resolve("browser-cache"),background,ui,context.appearance()::icon,
            id->connections.resolveIdentity(id,window),value->endpoints.open(Optional.of(value),window,status->{}),
            (capture,entry)->EditorOpening.activate(editing,browser.get(),capture,entry,window),capture->EditorOpening.openSelected(editing,browser.get(),capture,window),dialog::close);
        reference.set(picker);
        dialog.onClosed(()-> { dialogs.remove(dialog);picker.close(); });
        dialog.setContent(picker);
        identity.ifPresent(value->picker.start(value,directory));
        dialog.show();
    }
```

- [ ] **Step 6: Register the action**

In `plugins/remote/src/main/java/dev/jasper/remote/RemotePlugin.java`:

Change

```java
    public static final String SFTP="dev.jasper.remote.sftp", SFTP_TOGGLE=dev.jasper.remote.ui.sftp.SftpUi.TOGGLE;
```

to

```java
    public static final String SFTP="dev.jasper.remote.sftp", SFTP_TOGGLE=dev.jasper.remote.ui.sftp.SftpUi.TOGGLE, OPEN_FILE="dev.jasper.remote.open-file";
```

Add a field after `private dev.jasper.sdk.Subscription reopener;`:

```java
    private boolean openFile;
```

After the line `try { reopener=editing.registerReopener(); } catch(NoSuchMethodError older) { /* an Editor before 0.4 keeps no recent files */ }` add:

```java
                context.actions().register(ActionSpec.of(OPEN_FILE,"Open Remote File…").withIcon(context.appearance().icon(dev.jasper.sdk.ui.IconName.FOLDER))
                        .withKeywords(List.of("sftp","remote","open","file","edit")),
                    invoked-> { var target=invoked.pane().flatMap(this::sftpPane);
                        sftpUi.openRemoteFile(invoked.window(),target.map(dev.jasper.remote.ui.sftp.SftpUi.PaneTarget::identity),target.map(dev.jasper.remote.ui.sftp.SftpUi.PaneTarget::directory).orElse("")); });
                context.menus().standard(dev.jasper.sdk.ui.StandardMenu.FILE).add(OPEN_FILE);
                openFile=true;
```

Change

```java
        menu.addSeparator();menu.add(SFTP);menu.add(dev.jasper.remote.ui.transfers.TransferUi.SHOW);
```

to

```java
        menu.addSeparator();menu.add(SFTP);if(openFile)menu.add(OPEN_FILE);menu.add(dev.jasper.remote.ui.transfers.TransferUi.SHOW);
```

- [ ] **Step 7: Run the Remote tests**

Run: `./gradlew :jasper-plugin-remote:test`
Expected: PASS, including the 4 `RemoteOpenFileTest` tests.

- [ ] **Step 8: Commit**

```bash
git branch --show-current   # must print claude/remote-open-file
git add plugins/remote
git commit -m "Add File > Open Remote File and its picker

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 5: Remote answers Open in Editor…

**Files:**
- Create: `plugins/remote/src/main/java/dev/jasper/remote/edit/RemoteOpening.java`
- Modify: `plugins/remote/src/main/java/dev/jasper/remote/edit/RemoteEditing.java`
- Modify: `plugins/remote/src/main/java/dev/jasper/remote/edit/package-info.java`
- Modify: `plugins/remote/src/main/java/dev/jasper/remote/RemotePlugin.java`
- Modify: `plugins/remote/src/main/resources/plugin.toml`
- Test: `plugins/remote/src/test/java/dev/jasper/remote/edit/RemoteOpeningTest.java`
- Test: `plugins/remote/src/test/java/dev/jasper/remote/RemoteOpenFileTest.java`

**Interfaces:**
- Consumes: `OpenRequest`, `Editor.opener` (Task 1); `SftpUi.openRemoteFile` (Task 4); `RemoteDocument.parseKey` (existing).
- Produces: `RemoteOpening(Editor, RemoteEditing, Function<PaneHandle, Optional<RemoteOpening.Target>> panes, BiConsumer<WindowHandle, RemoteOpening.Target> show)`; `record RemoteOpening.Target(ConnectionIdentity identity, String directory)`; `Subscription register()`; package-private `boolean handle(OpenRequest)`; package-private `Optional<ConnectionIdentity> RemoteEditing.identity(RemoteDocument.ParsedKey)`.

- [ ] **Step 1: Write the failing tests**

`plugins/remote/src/test/java/dev/jasper/remote/edit/RemoteOpeningTest.java`:

```java
package dev.jasper.remote.edit;

import dev.jasper.editor.api.Editor;
import dev.jasper.editor.api.EditorDocument;
import dev.jasper.editor.api.OpenRequest;
import dev.jasper.remote.client.ConnectionIdentity;
import dev.jasper.remote.hosts.RemoteHost;
import dev.jasper.remote.sftp.LocalEndpoint;
import dev.jasper.sdk.Subscription;
import dev.jasper.sdk.terminal.PaneHandle;
import java.lang.reflect.Proxy;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Predicate;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class RemoteOpeningTest {
    final ExecutorService ui = Executors.newSingleThreadExecutor();
    final ExecutorService background = Executors.newCachedThreadPool();
    final EditChannels channels = new EditChannels((identity, owner) -> CompletableFuture.completedFuture(new LocalEndpoint()), identity -> true, background, ui);
    final List<Predicate<OpenRequest>> openers = new CopyOnWriteArrayList<>();
    final Editor editor = new Editor() {
        @Override public void open(Path file) { throw new AssertionError("not used"); }
        @Override public void open(EditorDocument document) { throw new AssertionError("not used"); }
        @Override public Subscription opener(Predicate<OpenRequest> handler) { openers.add(handler); return () -> openers.remove(handler); }
    };
    final Map<UUID, RemoteHost> saved = new ConcurrentHashMap<>();
    final RemoteEditing editing = new RemoteEditing(editor, channels, Optional::empty, notice -> { }, id -> Optional.ofNullable(saved.get(id)));
    final Map<UUID, RemoteOpening.Target> sshPanes = new ConcurrentHashMap<>();
    final List<String> shown = new CopyOnWriteArrayList<>();
    final RemoteOpening opening = new RemoteOpening(editor, editing, pane -> Optional.ofNullable(sshPanes.get(pane.id())),
        (window, target) -> shown.add(target.identity().host().name() + ":" + target.directory()));
    final ConnectionIdentity prod = EditChannelsTest.identity("prod");
    final TestWindow window = new TestWindow(UUID.randomUUID(), new AtomicBoolean(true));

    @AfterEach void stop() throws Exception {
        ui.submit(channels::close).get(10, TimeUnit.SECONDS);
        ui.shutdownNow(); background.shutdownNow();
    }

    static PaneHandle pane(UUID id) {
        return (PaneHandle) Proxy.newProxyInstance(PaneHandle.class.getClassLoader(), new Class<?>[] { PaneHandle.class }, (proxy, method, args) -> switch (method.getName()) {
            case "id" -> id;
            case "hashCode" -> id.hashCode();
            case "equals" -> proxy == args[0];
            case "toString" -> "pane " + id;
            default -> throw new UnsupportedOperationException(method.getName());
        });
    }

    @Test void anSshPaneOpensThePickerAtItsFolder() {
        UUID id = UUID.randomUUID();
        sshPanes.put(id, new RemoteOpening.Target(prod, "/srv/app"));
        assertThat(opening.handle(new OpenRequest(window, Optional.of(pane(id)), Optional.empty()))).isTrue();
        assertThat(shown).containsExactly("prod:/srv/app");
    }

    @Test void aRemoteFileOpensThePickerAtItsFolder() {
        var host = prod.hops().getFirst().host();
        saved.put(host.id(), host);
        assertThat(opening.handle(new OpenRequest(window, Optional.empty(), Optional.of(RemoteDocument.key(prod, "/etc/nginx/nginx.conf"))))).isTrue();
        assertThat(shown).containsExactly("prod:/etc/nginx");
    }

    @Test void aLocalPaneALocalFileADeletedHostOrNothingIsLeftToTheEditor() {
        assertThat(opening.handle(new OpenRequest(window, Optional.of(pane(UUID.randomUUID())), Optional.empty()))).isFalse();
        assertThat(opening.handle(new OpenRequest(window, Optional.empty(), Optional.of("local:/tmp/a.txt")))).isFalse();
        assertThat(opening.handle(new OpenRequest(window, Optional.empty(), Optional.of(RemoteDocument.key(prod, "/etc/hosts"))))).isFalse();
        assertThat(opening.handle(new OpenRequest(window, Optional.empty(), Optional.empty()))).isFalse();
        assertThat(shown).isEmpty();
    }

    @Test void registeringHandsTheEditorTheHandlerUntilClosed() {
        Subscription registration = opening.register();
        assertThat(openers).hasSize(1);
        registration.close();
        assertThat(openers).isEmpty();
    }
}
```

Add to `plugins/remote/src/test/java/dev/jasper/remote/RemoteOpenFileTest.java` (add imports `dev.jasper.editor.api.OpenRequest` and `dev.jasper.sdk.terminal.WindowHandle`):

```java
    @Test void theEditorsOpenInEditorOpensThePickerFromAnSshPane(@TempDir Path dir) throws Exception {
        Path files = Files.createDirectories(dir.resolve("files"));
        Files.writeString(files.resolve("notes.toml"), "a = 1\n");
        var editor = new FakeEditor();
        try (var server = new LoopbackServer(); var host = new FakePluginHost()) {
            var set = setUp(host, server, files, dir, Optional.of(editor), 1, new SftpSubsystemFactory());
            assertThat(editor.openers).hasSize(1);
            WindowHandle window = set.context().terminals().window(set.window()).orElseThrow();
            var pane = set.context().terminals().pane(set.pane()).orElseThrow();
            assertThat(editor.openers.getFirst().test(new OpenRequest(window, Optional.of(pane), Optional.empty()))).isTrue();
            var picker = picker(host);
            assertThat(((RemoteHost) picker.hosts().getSelectedItem()).name()).isEqualTo("prod");
            picker.cancelButton().doClick();
            assertThat(editor.openers.getFirst().test(new OpenRequest(window, Optional.empty(), Optional.of("local:/tmp/a.txt")))).isFalse();
            host.stopAll();
            assertThat(editor.openers).as("stopping Remote removes its opener").isEmpty();
            assertThat(host.failures()).isEmpty();
        }
    }
```

- [ ] **Step 2: Run them to verify they fail**

Run: `./gradlew :jasper-plugin-remote:test --tests 'dev.jasper.remote.edit.RemoteOpeningTest' --tests 'dev.jasper.remote.RemoteOpenFileTest'`
Expected: compilation FAILS — `cannot find symbol RemoteOpening`.

- [ ] **Step 3: Share identity building in `RemoteEditing`**

In `plugins/remote/src/main/java/dev/jasper/remote/edit/RemoteEditing.java`, replace the body of `reopen(String key)`

```java
        Optional<RemoteDocument.ParsedKey> parsed = RemoteDocument.parseKey(key);
        if (parsed.isEmpty()) return Optional.empty();
        var route = new ArrayList<ConnectionIdentity.Hop>();
        for (RemoteDocument.KeyHop hop : parsed.get().hops()) {
            Optional<RemoteHost> host = hosts.apply(hop.hostId());
            if (host.isEmpty()) return Optional.empty();
            route.add(new ConnectionIdentity.Hop(host.get(), hop.login()));
        }
        var identity = new ConnectionIdentity(route);
        String path = parsed.get().path();
        return Optional.of(owner -> new RemoteDocument(identity, path, channels, owner instanceof WindowHandle window ? owner(window) : activeWindow));
```

with

```java
        Optional<RemoteDocument.ParsedKey> parsed = RemoteDocument.parseKey(key);
        Optional<ConnectionIdentity> identity = parsed.flatMap(this::identity);
        if (identity.isEmpty()) return Optional.empty();
        String path = parsed.get().path();
        return Optional.of(owner -> new RemoteDocument(identity.get(), path, channels, owner instanceof WindowHandle window ? owner(window) : activeWindow));
    }

    /**
     * @param parsed a parsed key
     * @return its route with the saved hosts as they are now and the recorded logins; empty when a host is no longer saved
     */
    Optional<ConnectionIdentity> identity(RemoteDocument.ParsedKey parsed) {
        var route = new ArrayList<ConnectionIdentity.Hop>();
        for (RemoteDocument.KeyHop hop : parsed.hops()) {
            Optional<RemoteHost> host = hosts.apply(hop.hostId());
            if (host.isEmpty()) return Optional.empty();
            route.add(new ConnectionIdentity.Hop(host.get(), hop.login()));
        }
        return Optional.of(new ConnectionIdentity(route));
```

(The method's closing brace that followed the old body now closes `identity`.)

- [ ] **Step 4: Write `RemoteOpening`**

`plugins/remote/src/main/java/dev/jasper/remote/edit/RemoteOpening.java`:

```java
package dev.jasper.remote.edit;

import dev.jasper.editor.api.Editor;
import dev.jasper.editor.api.OpenRequest;
import dev.jasper.remote.client.ConnectionIdentity;
import dev.jasper.remote.sftp.FilePaths;
import dev.jasper.sdk.Subscription;
import dev.jasper.sdk.terminal.PaneHandle;
import dev.jasper.sdk.terminal.WindowHandle;
import java.io.IOException;
import java.util.Objects;
import java.util.Optional;
import java.util.function.BiConsumer;
import java.util.function.Function;

/**
 * Remote's answer to the Editor's Open in Editor…: a focused SSH pane, or an active Editor tab showing a remote file,
 * opens the remote file picker there instead of the local chooser. Constructed only when the Editor is installed; UI
 * thread only.
 */
public final class RemoteOpening {
    /**
     * Where the picker starts.
     *
     * @param identity the connection
     * @param directory the folder; blank for the home folder
     */
    public record Target(ConnectionIdentity identity, String directory) {
        /** Rejects a null identity; a null directory is blank. */
        public Target { Objects.requireNonNull(identity, "identity"); directory = directory == null ? "" : directory; }
    }

    private final Editor editor;
    private final RemoteEditing editing;
    private final Function<PaneHandle, Optional<Target>> panes;
    private final BiConsumer<WindowHandle, Target> show;

    /**
     * @param editor the Editor's service
     * @param editing rebuilds connections from remote keys
     * @param panes the connection and folder of a connected SSH pane
     * @param show shows the picker for a window
     */
    public RemoteOpening(Editor editor, RemoteEditing editing, Function<PaneHandle, Optional<Target>> panes, BiConsumer<WindowHandle, Target> show) {
        this.editor = Objects.requireNonNull(editor); this.editing = Objects.requireNonNull(editing);
        this.panes = Objects.requireNonNull(panes); this.show = Objects.requireNonNull(show);
    }

    /**
     * Registers with the Editor. Throws {@link NoSuchMethodError} with an Editor older than 0.5, which opens only local files.
     *
     * @return removes the registration
     */
    public Subscription register() { return editor.opener(this::handle); }

    boolean handle(OpenRequest request) {
        Optional<Target> target = request.pane().flatMap(panes);
        if (target.isEmpty()) target = request.documentKey().flatMap(this::fromKey);
        if (target.isEmpty()) return false;
        show.accept(request.window(), target.get());
        return true;
    }

    private Optional<Target> fromKey(String key) {
        Optional<RemoteDocument.ParsedKey> parsed = RemoteDocument.parseKey(key);
        if (parsed.isEmpty()) return Optional.empty();
        String folder;
        try { folder = FilePaths.parent(parsed.get().path()); }
        catch (IOException root) { folder = ""; }
        String directory = folder;
        return editing.identity(parsed.get()).map(identity -> new Target(identity, directory));
    }
}
```

Replace `plugins/remote/src/main/java/dev/jasper/remote/edit/package-info.java` with:

```java
/**
 * Editing remote files in the Editor: the write contract over a file endpoint, one queued SFTP channel per
 * connection identity, the documents the Editor opens, and Remote's answer to Open in Editor…. May depend on:
 * {@code sftp}, {@code client}, the SDK and {@code dev.jasper.editor.api}. Only
 * {@link dev.jasper.remote.edit.RemoteDocument}, {@link dev.jasper.remote.edit.RemoteEditing} and
 * {@link dev.jasper.remote.edit.RemoteOpening} name Editor types; they load only when the Editor is installed.
 */
package dev.jasper.remote.edit;
```

- [ ] **Step 5: Register from the plugin**

In `plugins/remote/src/main/java/dev/jasper/remote/RemotePlugin.java`, add a field after `private boolean openFile;`:

```java
    private dev.jasper.sdk.Subscription opener;
```

After the `openFile=true;` line added in Task 4, add:

```java
                var opening=new dev.jasper.remote.edit.RemoteOpening(editor.orElseThrow(),editing,
                    pane->sftpPane(pane).map(target->new dev.jasper.remote.edit.RemoteOpening.Target(target.identity(),target.directory())),
                    (window,target)->sftpUi.openRemoteFile(window,Optional.of(target.identity()),target.directory()));
                try { opener=opening.register(); } catch(NoSuchMethodError older) { /* an Editor before 0.5 opens only local files */ }
```

In `stop()`, change `if(reopener!=null)reopener.close();` to `if(opener!=null)opener.close();if(reopener!=null)reopener.close();`.

In `plugins/remote/src/main/resources/plugin.toml` change `version = "0.6.2"` to `version = "0.7.0"`.

- [ ] **Step 6: Run the Remote tests**

Run: `./gradlew :jasper-plugin-remote:test`
Expected: PASS, including the 4 `RemoteOpeningTest` tests and the 5 `RemoteOpenFileTest` tests.

- [ ] **Step 7: Commit**

```bash
git branch --show-current   # must print claude/remote-open-file
git add plugins/remote
git commit -m "Open the remote picker from Open in Editor on SSH panes and remote files

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 6: Full check and status

**Files:**
- Modify: `docs/STATUS.md`
- Modify: `docs/superpowers/plans/2026-09-29-remote-open-file.md` (status banner)

- [ ] **Step 1: Source hygiene**

Run:

```bash
python3 - <<'PY'
import pathlib
for root in ("plugins/editor/src", "plugins/remote/src"):
    for p in pathlib.Path(root).rglob("*.java"):
        text = p.read_text(encoding="utf-8")
        bad = sum(1 for c in text if 0xD800 <= ord(c) <= 0xDFFF or 0xE000 <= ord(c) <= 0xF8FF or (ord(c) < 0x20 and c not in "\n\t\r") or ord(c) == 0x7f)
        if bad: print(p, "bad chars:", bad)
PY
```

Expected: no output.

- [ ] **Step 2: Whole build**

Run: `./gradlew check`
Expected: BUILD SUCCESSFUL (JavaDoc doclint and `verifyPluginArchitecture` included). Tally tests/failures/skipped per module from `*/build/test-results/test/*.xml` and `plugins/*/build/test-results/test/*.xml`.

- [ ] **Step 3: Record the state**

Under `## Current state` in `docs/STATUS.md` (update its date to 2026-09-29 if older), add first:

```markdown
- **Opening remote files from Cmd/Ctrl+O and File › Open Remote File…** (branch `claude/remote-open-file`, spec
  `docs/superpowers/specs/2026-09-29-remote-open-file-design.md`, plan
  `docs/superpowers/plans/2026-09-29-remote-open-file.md`): Open in Editor… asks openers before the local chooser
  (Editor 0.5.0: `OpenRequest`, `Editor.opener`); the Editor tab's hardcoded Cmd/Ctrl+O is gone, so the Keymap's
  binding for Open in Editor… applies in terminal panes and Editor tabs. Remote 0.7.0 answers for connected SSH panes
  and remote Editor tabs with a modal Open Remote File picker (host dropdown, navigation, multi-select, up to 20
  files, links followed), also on File and SSH › Open Remote File… (no default shortcut). `./gradlew check`:
  <per-module tests/failures/skipped from Step 2>. Not merged. GUI checks pending with the user: Cmd/Ctrl+O in an SSH
  pane, a remote Editor tab and a local pane; rebinding Open in Editor… in the Keymap moves all three; File › Open
  Remote File… with and without an SSH pane focused; on Linux/Windows, the Keymap's cmd+o is Ctrl+Shift+O, which now
  applies in Editor tabs too (their old in-tab key was Ctrl+O).
```

Replace the `<…>` text with the real counts. Under this plan's title add:

```markdown
> **Status:** executed on `claude/remote-open-file`, 2026-09-29; not merged. Deviations from the plan text: <none, or list them>.
```

- [ ] **Step 4: Commit**

```bash
git branch --show-current   # must print claude/remote-open-file
git add docs/STATUS.md docs/superpowers/plans/2026-09-29-remote-open-file.md
git commit -m "Record the remote open-file work

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```
