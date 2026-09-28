# Settings Dialog B1 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Status:** In progress (inline execution). Branch `claude/settings-dialog`, worktree `.worktrees/settings-dialog`.
Record every deviation from this text here and in `docs/STATUS.md`.

Deviations so far:
- Task 4: the test and the implementation were written before the first run, so its failing run was not observed.
- Task 5: `SchemaPage.Spec.settings()` uses an explicit `<Setting<?>>map` type witness.
- Task 7: the apply test calls `refreshNow()` after marking the fake page modified (a fake page cannot notify the dialog);
  `SettingsView` falls back to grey when the theme has no `Separator.foreground`.
- Task 9's README instruction no longer spells out a Markdown link in this plan, because `AppDocumentationTest`
  resolves every link in `docs/`, plans included.

**Goal:** An IntelliJ-style Settings dialog, opened by Cmd+, and the macOS Settings… menu item, that edits
every core setting through schema-generated pages, with search, highlighting, Apply/OK/Cancel and banners.

**Architecture:** A new package `dev.jasper.app.settingsui` holds the dialog (`SettingsDialog`, `SettingsView`,
`PageTree`), the shared `SettingsDraft`, the `SettingsPage` interface and its implementations (`SchemaPage`,
`GroupPage`, and the temporary `LauncherPage`), the editors (`Editors`, `ValueEditor`) and the core tree
(`CorePages`). `ConfigService.State` gains the loaded `SettingValues` and a `rejected` flag; `ConfigurationController`
exposes state, save and state listening; `AuxiliaryWindows` gains a modal dialog that remembers its bounds.
Keymap and Plugins appear as launcher pages that open the existing windows; plan B2 replaces them.

**Tech Stack:** Java 25 (JetBrains Runtime), Swing with FlatLaf 3.7, Gradle wrapper, JUnit 5, AssertJ.

**Spec:** `docs/superpowers/specs/2026-09-28-jasper-settings-dialog-design.md` (read it first). Sub-project A,
`docs/superpowers/specs/2026-09-27-jasper-settings-foundation-design.md`, supplies the schema, `SettingsEdit` and
`ConfigService.save`.

## Global Constraints

- Build and test only with `./gradlew` from the worktree root; never launch the GUI (`:jasper-app:run`).
- Work only in `/Users/dustin/projects/moray/.worktrees/settings-dialog`; run `git branch --show-current` before every commit and confirm `claude/settings-dialog`.
- Every commit message ends with `Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>`. Do not push.
- `dev.jasper.app.settingsui` may depend on `dev.jasper.app.settings`, `config`, `windows`, `platform` and `lifecycle` only; nothing in those packages may depend on `settingsui`. `verifyApplicationArchitecture` must pass.
- No SDK, testkit or plugin changes. No interface without two real implementations (`SettingsPage`: `SchemaPage`, `GroupPage`, `LauncherPage`; `ValueEditor`: seven editors).
- Swing work happens on the EDT in production; tests build components directly or through `DesktopTestSupport.edt`.
- Plain text only in labels (`html.disable`), as elsewhere in the app. Source hygiene per AGENTS.md: write non-ASCII characters as Java escapes (`"…"`, `"›"`, `"−"`, `"↑"`, `"↓"`, `"×"`).
- Dialog title `Settings`; bounds id `app.settings`; default size 1000 × 700; default page `appearance`.
- Help URL prefix: `https://github.com/an0nn30/moray/blob/main/docs/configuration.md#`.

## File Structure

New, `jasper-app/src/main/java/dev/jasper/app/settingsui/`:
- `package-info.java`: package contract.
- `SettingsPage.java`: the page interface.
- `PageContext.java`: what a page may ask of the dialog.
- `SettingsDraft.java`: baseline plus pending core-setting edits.
- `SearchText.java`: the search word rule.
- `ValueEditor.java`: one setting's control.
- `Editors.java`: editor factories and the seven editors; `Fonts.java`: installed font families.
- `HighlightBox.java`: search highlight behind a label; `TitledSeparator.java`: section headers.
- `SchemaPage.java`: generated pages and their spec builder.
- `GroupPage.java`: a page of links to its children. `LauncherPage.java`: a page with one button (B1 only).
- `CorePages.java`: the tree of core pages.
- `PageTree.java`: the left tree. `SettingsView.java`: the dialog's panel. `SettingsDialog.java`: the controller.

Modified: `config/ConfigService.java` (State), `application/ConfigurationController.java`, `application/JasperApplication.java`,
`windows/AuxiliaryWindows.java`, `windows/NativeShells.java`, `workspace/WindowContent.java` (tooltip), `jasper-app/build.gradle.kts`,
docs.

---

### Task 1: State carries values and rejection; the controller exposes state, save and listeners

**Files:**
- Modify: `jasper-app/src/main/java/dev/jasper/app/config/ConfigService.java`
- Modify: `jasper-app/src/main/java/dev/jasper/app/application/ConfigurationController.java`
- Modify: `jasper-app/src/test/java/dev/jasper/app/application/ConfigurationTestSupport.java`
- Test: `jasper-app/src/test/java/dev/jasper/app/config/ConfigStateTest.java`
- Test: `jasper-app/src/test/java/dev/jasper/app/workspace/ConfigurationControllerTest.java` (two new tests)

**Interfaces:**
- Produces: `ConfigService.State(ConfigSnapshot snapshot, List<ConfigDiagnostic> diagnostics, Path file, boolean present, List<LegacyKey> legacyKeys, List<List<String>> deadTables, SettingValues values, boolean rejected)` with the existing 4- and 6-argument constructors kept (values = defaults, rejected = false); `ConfigurationController.state()`, `save(SettingsEdit)` returning `CompletableFuture<ConfigService.State>`, `reload()` returning `CompletableFuture<ConfigService.State>`, `openConfigFile()` returning `CompletableFuture<Path>`, `onState(Consumer<ConfigService.State>)` returning `dev.jasper.app.lifecycle.Subscription`, `settingsOpener(Consumer<WindowContent>)`.

- [ ] **Step 1: Write the failing tests**

`ConfigStateTest.java`:

```java
package dev.jasper.app.config;

import static org.assertj.core.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ConfigStateTest {
    @TempDir Path directory;

    @Test void stateCarriesLoadedValuesAndKeepsTheLastGoodOnesWhenRejected() throws Exception {
        Path file = directory.resolve("config.toml");
        Files.writeString(file, "[window]\ntab_height = 44\n");
        try (var service = new ConfigService(file, true)) {
            var first = service.initialState();
            assertThat(first.rejected()).isFalse();
            assertThat(first.values().get(CoreSettings.WINDOW_TAB_HEIGHT)).isEqualTo(44);
            assertThat(first.values().present(CoreSettings.WINDOW_TAB_HEIGHT)).isTrue();
            Files.writeString(file, "[window\n");
            var broken = service.reload().get(5, TimeUnit.SECONDS);
            assertThat(broken.rejected()).isTrue();
            assertThat(broken.values().get(CoreSettings.WINDOW_TAB_HEIGHT)).isEqualTo(44);
            assertThat(broken.snapshot().tabHeight()).isEqualTo(44);
            Files.delete(file);
            var missing = service.reload().get(5, TimeUnit.SECONDS);
            assertThat(missing.rejected()).isFalse();
            assertThat(missing.values().present(CoreSettings.WINDOW_TAB_HEIGHT)).isFalse();
        }
    }

    @Test void unreadableFilesAreRejectedToo() throws Exception {
        Path file = directory.resolve("config.toml");
        Files.createDirectory(file);
        try (var service = new ConfigService(file, true)) {
            assertThat(service.initialState().rejected()).isTrue();
        }
    }
}
```

Add to `ConfigurationControllerTest.java`:

```java
    @Test void settingsOpenerReplacesTheEditorForCmdComma() throws Exception {
        start(""); edt(this::owner);
        var owner = owners.getFirst();
        var opened = new ArrayList<WindowContent>();
        edt(() -> controller.settingsOpener(opened::add));
        edt(owner::openSettings);
        assertThat(opened).containsExactly(owner);
    }

    @Test void stateSaveAndListenersRoundTripThroughTheService() throws Exception {
        start("[window]\ntab_height = 44\n"); edt(this::owner);
        var seen = new java.util.concurrent.CopyOnWriteArrayList<ConfigService.State>();
        edt(() -> {
            assertThat(controller.state().values().get(dev.jasper.app.config.CoreSettings.WINDOW_TAB_HEIGHT)).isEqualTo(44);
            controller.onState(seen::add);
        });
        var saved = new AtomicReference<java.util.concurrent.CompletableFuture<ConfigService.State>>();
        edt(() -> saved.set(controller.save(new dev.jasper.app.config.SettingsEdit(
            Map.of(dev.jasper.app.config.CoreSettings.WINDOW_TAB_HEIGHT, Optional.of(50)),
            Map.of(dev.jasper.app.config.CoreSettings.WINDOW_TAB_HEIGHT, Optional.of(44))))));
        saved.get().get(5, java.util.concurrent.TimeUnit.SECONDS);
        until(() -> !seen.isEmpty() && seen.getLast().values().get(dev.jasper.app.config.CoreSettings.WINDOW_TAB_HEIGHT) == 50);
        edt(() -> assertThat(controller.state().values().get(dev.jasper.app.config.CoreSettings.WINDOW_TAB_HEIGHT)).isEqualTo(50));
    }
```

Add to `ConfigurationTestSupport.java` (before `close()`):

```java
    public ConfigService.State state() { return controller.state(); }
    public java.util.concurrent.CompletableFuture<ConfigService.State> save(SettingsEdit edit) { return controller.save(edit); }
    public dev.jasper.app.lifecycle.Subscription onState(Consumer<ConfigService.State> listener) { return controller.onState(listener); }
    public void settingsOpener(Consumer<WindowContent> opener) { controller.settingsOpener(opener); }
```

- [ ] **Step 2: Run the tests to verify they fail**

Run: `./gradlew :jasper-app:test --tests "dev.jasper.app.config.ConfigStateTest" --tests "dev.jasper.app.workspace.ConfigurationControllerTest"`
Expected: FAIL: compilation errors (`values()`, `rejected()`, `state()`, `save`, `onState`, `settingsOpener` do not exist).

- [ ] **Step 3: Write the implementation**

In `ConfigService.java`, add `import dev.jasper.app.settings.SettingValues;` and replace the `State` record with:

```java
    /**
     * The published configuration.
     *
     * @param legacyKeys old keys the file still uses
     * @param deadTables old tables Migrate Config may remove
     * @param values each core setting's loaded value and where it came from (the last good values when rejected)
     * @param rejected whether the file could not be applied (syntax or type errors, or an unreadable file)
     */
    public record State(ConfigSnapshot snapshot, List<ConfigDiagnostic> diagnostics, Path file, boolean present,
                        List<LegacyKey> legacyKeys, List<List<String>> deadTables, SettingValues values, boolean rejected) {
        public State {
            Objects.requireNonNull(snapshot, "snapshot");
            Objects.requireNonNull(file, "file");
            Objects.requireNonNull(values, "values");
            diagnostics = List.copyOf(diagnostics);
            legacyKeys = List.copyOf(legacyKeys);
            deadTables = deadTables.stream().map(List::copyOf).toList();
        }

        /** A state with nothing to migrate, default values and nothing rejected. */
        public State(ConfigSnapshot snapshot, List<ConfigDiagnostic> diagnostics, Path file, boolean present) {
            this(snapshot, diagnostics, file, present, List.of(), List.of());
        }

        /** A state with default values and nothing rejected. */
        public State(ConfigSnapshot snapshot, List<ConfigDiagnostic> diagnostics, Path file, boolean present,
                     List<LegacyKey> legacyKeys, List<List<String>> deadTables) {
            this(snapshot, diagnostics, file, present, legacyKeys, deadTables, SettingValues.defaults(CoreSettings.SCHEMA), false);
        }

        /** Whether Migrate Config has old keys or dead tables to rewrite. */
        public boolean migratable() { return !legacyKeys.isEmpty() || !deadTables.isEmpty(); }
    }
```

Then make the reader carry the last good state:
- constructor: `lastConfig = readState(new State(ConfigSnapshot.defaults(), List.of(), file, false), true);`
- `refresh`: `lastConfig = readState(lastConfig, force);`
- change the parameter type of `readState`, `missingState` and `readError` from `ConfigSnapshot lastGood` to `State lastGood` (their bodies pass `lastGood` through unchanged except as below);
- in `readState`, replace the `return parsed.rejected() ? … : …;` statement with:

```java
            return parsed.rejected()
                ? new State(lastGood.snapshot(), parsed.diagnostics(), file, true, List.of(), List.of(), lastGood.values(), true)
                : new State(parsed.snapshot(), parsed.diagnostics(), file, true, parsed.legacyKeys(), parsed.deadTables(), parsed.values(), false);
```

- replace `readError` with:

```java
    private State readError(State lastGood, String message) {
        return new State(lastGood.snapshot(), List.of(new ConfigDiagnostic(
            ConfigDiagnostic.Severity.ERROR, file, 0, 0, "", message)), file, true, List.of(), List.of(), lastGood.values(), true);
    }
```

In `ConfigurationController.java`:
- imports: `dev.jasper.app.config.SettingsEdit`, `dev.jasper.app.lifecycle.Subscription`, `java.util.ArrayList`;
- fields:

```java
    private final List<Consumer<ConfigService.State>> stateListeners = new ArrayList<>();
    private Consumer<WindowContent> settingsOpener;
```

- at the end of the constructor: `settingsOpener = owner -> reportFailure(owner, service.openSettings(editor));`
- in `shown()`, replace the final `return new ConfigService.State(...)` with:

```java
        return new ConfigService.State(state.snapshot(), merged, state.file(), state.present(), state.legacyKeys(), removable,
            state.values(), state.rejected());
```

- in `register`, replace `() -> reportFailure(owner, service.openSettings(editor)),` with `() -> settingsOpener.accept(owner),`
- at the end of `accept(ConfigService.State next)` add:

```java
        var visible = shown();
        for (Consumer<ConfigService.State> listener : List.copyOf(stateListeners)) listener.accept(visible);
```

- new methods:

```java
    /** What Cmd+, and each window's Settings command open; by default config.toml in an editor. */
    void settingsOpener(Consumer<WindowContent> opener) {
        requireEdt();
        settingsOpener = java.util.Objects.requireNonNull(opener, "opener");
    }

    /** Opens config.toml in the user's editor, creating it from the template first when it is missing. */
    CompletableFuture<Path> openConfigFile() {
        requireEdt();
        return service.openSettings(editor);
    }

    /** Reads config.toml again and publishes it, even when it did not change. */
    CompletableFuture<ConfigService.State> reload() {
        requireEdt();
        return service.reload();
    }

    /** The state windows show: the saved state plus theme, application and plugin problems. */
    ConfigService.State state() {
        requireEdt();
        return shown();
    }

    /** Saves settings; the new state also arrives through the normal reload. */
    CompletableFuture<ConfigService.State> save(SettingsEdit edit) {
        requireEdt();
        return service.save(edit);
    }

    /** Calls {@code listener} with every state applied from now on. */
    Subscription onState(Consumer<ConfigService.State> listener) {
        requireEdt();
        stateListeners.add(java.util.Objects.requireNonNull(listener, "listener"));
        return new Subscription(() -> stateListeners.remove(listener));
    }
```

- in `close()`, add `stateListeners.clear();` after `applicationListener = snapshot -> {};`.

- [ ] **Step 4: Run the tests to verify they pass**

Run: `./gradlew :jasper-app:test --tests "dev.jasper.app.config.*" --tests "dev.jasper.app.workspace.*Config*" --tests "dev.jasper.app.application.*"`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git branch --show-current
git add jasper-app/src
git commit -m "Carry loaded values and rejection in the configuration state; expose it to the application

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 2: A modal dialog that remembers its bounds

**Files:**
- Modify: `jasper-app/src/main/java/dev/jasper/app/windows/AuxiliaryWindows.java`
- Modify: `jasper-app/src/main/java/dev/jasper/app/windows/NativeShells.java`
- Test: `jasper-app/src/test/java/dev/jasper/app/windows/ModalDialogTest.java`

**Interfaces:**
- Produces: `AuxiliaryWindows.modalDialog(String id, String title, Dimension preferred, UUID ownerWindowOrNull)` returning an `AuxiliarySurface` of kind `DIALOG`, modal, whose bounds are saved under `id` when it closes.

- [ ] **Step 1: Write the failing test**

```java
package dev.jasper.app.windows;

import static org.assertj.core.api.Assertions.*;

import dev.jasper.app.persistence.UiState;
import java.awt.Dimension;
import java.awt.Rectangle;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ModalDialogTest {
    private final UiState state = UiState.inMemory();
    private final AuxiliaryWindows windows = new AuxiliaryWindows(state, surface -> new AuxiliarySurface.Shell(
        () -> { }, () -> { }, () -> { }, title -> { }, () -> new Rectangle(10, 20, 900, 600), (title, initial) -> Optional.empty()));

    @Test void aNamedModalDialogRemembersItsBoundsWithOrWithoutAnOwner() {
        var dialog = windows.modalDialog("app.settings", "Settings", new Dimension(1000, 700), UUID.randomUUID());
        assertThat(dialog.kind()).isEqualTo(AuxiliarySurface.Kind.DIALOG);
        assertThat(dialog.modal()).isTrue();
        assertThat(dialog.preferredSize()).isEqualTo(new Dimension(1000, 700));
        dialog.show();
        dialog.close();
        assertThat(state.window("app.settings")).hasValue(new UiState.Bounds(10, 20, 900, 600));
        var ownerless = windows.modalDialog("app.other", "Other", new Dimension(400, 300), null);
        assertThat(ownerless.ownerWindow()).isEmpty();
        ownerless.show();
        ownerless.close();
        assertThat(state.window("app.other")).isPresent();
    }

    @Test void plainDialogsStillForgetTheirBounds() {
        var plain = windows.dialog("Confirm", true, UUID.randomUUID());
        plain.show();
        plain.close();
        assertThat(state.window("")).isEmpty();
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `./gradlew :jasper-app:test --tests "dev.jasper.app.windows.ModalDialogTest"`
Expected: FAIL: `modalDialog` does not exist.

- [ ] **Step 3: Write the implementation**

In `AuxiliaryWindows.java`, add after the second `dialog` method:

```java
    /**
     * A modal dialog that remembers its bounds under {@code id}: over the terminal window {@code ownerWindowOrNull}, or
     * application-modal with no owner when it is null. Showing it blocks until it closes.
     */
    public AuxiliarySurface modalDialog(String id, String title, Dimension preferred, UUID ownerWindowOrNull) {
        if (Objects.requireNonNull(id, "id").isBlank()) throw new IllegalArgumentException("A remembered dialog needs an id");
        return track(new AuxiliarySurface(id, title, AuxiliarySurface.Kind.DIALOG, true, preferred, ownerWindowOrNull, null, shells));
    }
```

and in `track`, replace `if (surface.kind() == AuxiliarySurface.Kind.WINDOW) surface.lastBounds().ifPresent(bounds -> {` with:

```java
            boolean remembered = surface.kind() == AuxiliarySurface.Kind.WINDOW
                || surface.kind() == AuxiliarySurface.Kind.DIALOG && !surface.id().isEmpty();
            if (remembered) surface.lastBounds().ifPresent(bounds -> {
```

In `NativeShells.dialog`, replace the lines from `var dialog = new JDialog(` through `dialog.setLocationRelativeTo(owner);` (the first one, before the theme subscription) with:

```java
        boolean remembered = !surface.id().isEmpty();
        Dialog.ModalityType modality = !surface.modal() ? Dialog.ModalityType.MODELESS
            : owner == null ? Dialog.ModalityType.APPLICATION_MODAL : Dialog.ModalityType.DOCUMENT_MODAL;
        var dialog = new JDialog(owner, surface.title(), modality);
        dialog.setIconImages(ApplicationIcon.images());
        MacTitleBar bar = installTitleBar(dialog.getRootPane(), surface, MacTitleBar.isSupported());
        dialog.setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);
        dialog.addWindowListener(new WindowAdapter() {
            @Override public void windowClosing(WindowEvent event) { surface.requestClose(); }
            @Override public void windowActivated(WindowEvent event) { if (bar != null) bar.setActive(true); surface.notifyActivated(); }
            @Override public void windowDeactivated(WindowEvent event) { if (bar != null) bar.setActive(false); }
        });
        if (bar != null) bar.attach(dialog);
        dialog.pack();
        if (remembered) {
            dialog.setSize(surface.preferredSize());
            dialog.setLocationRelativeTo(owner);
            state.window(surface.id()).map(saved -> new Rectangle(saved.x(), saved.y(), saved.width(), saved.height()))
                .filter(NativeShells::onSomeScreen).ifPresent(dialog::setBounds);
        } else dialog.setLocationRelativeTo(owner);
```

and replace the shell's show function `() -> { dialog.pack(); dialog.setLocationRelativeTo(owner); dialog.setVisible(true); }` with:

```java
() -> {
            if (!remembered) { dialog.pack(); dialog.setLocationRelativeTo(owner); }
            dialog.setVisible(true);
        }
```

- [ ] **Step 4: Run the tests to verify they pass**

Run: `./gradlew :jasper-app:test --tests "dev.jasper.app.windows.*" --tests "dev.jasper.app.pluginmanager.*"`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git branch --show-current
git add jasper-app/src
git commit -m "Add a modal dialog that remembers its bounds

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 3: Page model, draft and search words

**Files:**
- Create: `jasper-app/src/main/java/dev/jasper/app/settingsui/package-info.java`
- Create: `jasper-app/src/main/java/dev/jasper/app/settingsui/SettingsPage.java`
- Create: `jasper-app/src/main/java/dev/jasper/app/settingsui/PageContext.java`
- Create: `jasper-app/src/main/java/dev/jasper/app/settingsui/SettingsDraft.java`
- Create: `jasper-app/src/main/java/dev/jasper/app/settingsui/SearchText.java`
- Test: `jasper-app/src/test/java/dev/jasper/app/settingsui/SettingsDraftTest.java`

**Interfaces:**
- Produces: `SettingsPage` (`id`, `title`, `parent`, `searchTerms`, `component`, `modified`, `reset`, `apply`, `highlight`, `dispose`, and defaults `valid()`, `hiddenWhenChildless()`, `refresh()`, `helpAnchor()`); `PageContext(SettingsDraft draft, Consumer<String> navigate, Consumer<Runnable> closeThen, BiFunction<String, Optional<Path>, Optional<Path>> chooseFile, Runnable changed)`; `SettingsDraft(SettingValues baseline)` with `baseline()`, `value(Setting<T>)`, `saved(Setting<T>)`, `stage(Setting<T>, Optional<T>)`, `modified()`, `modified(Setting<?>)`, `size()`, `edit()`, `rebase(SettingValues)`, `reset(Collection<? extends Setting<?>>)`, `onChanged(Runnable)`; `SearchText.words(String)`, `SearchText.matches(String text, String query)`.

- [ ] **Step 1: Write the failing test**

```java
package dev.jasper.app.settingsui;

import static org.assertj.core.api.Assertions.*;

import dev.jasper.app.config.ConfigLoader;
import dev.jasper.app.config.CoreSettings;
import dev.jasper.app.settings.SettingValues;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class SettingsDraftTest {
    private static SettingValues values(String text) { return ConfigLoader.parse(Path.of("config.toml"), text, true).values(); }

    @Test void stagingTracksPendingValuesAndDropsEditsThatMatchTheBaseline() {
        var draft = new SettingsDraft(values("[window]\ntab_height = 44\n"));
        var changes = new int[1];
        draft.onChanged(() -> changes[0]++);
        assertThat(draft.value(CoreSettings.WINDOW_TAB_HEIGHT)).isEqualTo(44);
        assertThat(draft.modified()).isFalse();
        draft.stage(CoreSettings.WINDOW_TAB_HEIGHT, Optional.of(50));
        assertThat(draft.value(CoreSettings.WINDOW_TAB_HEIGHT)).isEqualTo(50);
        assertThat(draft.modified(CoreSettings.WINDOW_TAB_HEIGHT)).isTrue();
        assertThat(draft.size()).isEqualTo(1);
        draft.stage(CoreSettings.WINDOW_TAB_HEIGHT, Optional.of(44));
        assertThat(draft.modified()).as("back to the saved value").isFalse();
        draft.stage(CoreSettings.WINDOW_TAB_HEIGHT, Optional.empty());
        assertThat(draft.value(CoreSettings.WINDOW_TAB_HEIGHT)).as("reset shows the default").isEqualTo(30);
        assertThat(draft.modified(CoreSettings.WINDOW_TAB_HEIGHT)).isTrue();
        draft.stage(CoreSettings.TERMINAL_FONT_SIZE, Optional.empty());
        assertThat(draft.modified(CoreSettings.TERMINAL_FONT_SIZE)).as("already unset").isFalse();
        assertThat(changes[0]).isEqualTo(4);
    }

    @Test void theEditCarriesPendingChangesAndTheBaselineTheyReplace() {
        var draft = new SettingsDraft(values("[window]\ntab_height = 44\n"));
        draft.stage(CoreSettings.WINDOW_TAB_HEIGHT, Optional.empty());
        draft.stage(CoreSettings.TERMINAL_FONT_SIZE, Optional.of(18f));
        var edit = draft.edit();
        assertThat(edit.changes()).containsOnly(Map.entry(CoreSettings.WINDOW_TAB_HEIGHT, Optional.empty()),
            Map.entry(CoreSettings.TERMINAL_FONT_SIZE, Optional.of(18f)));
        assertThat(edit.expected()).containsOnly(Map.entry(CoreSettings.WINDOW_TAB_HEIGHT, Optional.of(44)),
            Map.entry(CoreSettings.TERMINAL_FONT_SIZE, Optional.empty()));
    }

    @Test void rebasingKeepsOnlyEditsTheNewBaselineDoesNotAlreadyHold() {
        var draft = new SettingsDraft(values(""));
        draft.stage(CoreSettings.WINDOW_TAB_HEIGHT, Optional.of(50));
        draft.stage(CoreSettings.TERMINAL_FONT_SIZE, Optional.of(18f));
        draft.rebase(values("[window]\ntab_height = 50\n"));
        assertThat(draft.modified(CoreSettings.WINDOW_TAB_HEIGHT)).isFalse();
        assertThat(draft.modified(CoreSettings.TERMINAL_FONT_SIZE)).isTrue();
        assertThat(draft.baseline().get(CoreSettings.WINDOW_TAB_HEIGHT)).isEqualTo(50);
        draft.reset(List.of(CoreSettings.TERMINAL_FONT_SIZE));
        assertThat(draft.modified()).isFalse();
    }

    @Test void searchNeedsEveryWordInAnyOrderAndCase() {
        assertThat(SearchText.words("  Font   SIZE ")).containsExactly("font", "size");
        assertThat(SearchText.matches("Terminal font size", "size font")).isTrue();
        assertThat(SearchText.matches("Terminal font size", "font colour")).isFalse();
        assertThat(SearchText.matches("anything", "   ")).isFalse();
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `./gradlew :jasper-app:test --tests "dev.jasper.app.settingsui.SettingsDraftTest"`
Expected: FAIL: the package does not exist.

- [ ] **Step 3: Write the implementation**

`package-info.java`:

```java
/**
 * The Settings dialog: its shell, the pending edits of core settings, schema-generated and group pages, editors
 * and the core page tree. EDT only; SettingsDialog owns the pages it creates and disposes them when it closes.
 * <p>Allowed outgoing Jasper dependencies: dev.jasper.app.settings, dev.jasper.app.config, dev.jasper.app.windows,
 * dev.jasper.app.platform, dev.jasper.app.lifecycle.
 * The module architecture check forbids package cycles; app types are not an external plugin API.
 */
package dev.jasper.app.settingsui;
```

`SettingsPage.java`:

```java
package dev.jasper.app.settingsui;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import javax.swing.JComponent;

/**
 * One page of the Settings dialog, in the manner of IntelliJ's {@code Configurable}. EDT only. Schema pages share the
 * dialog's {@link SettingsDraft}, which the dialog saves once before it applies other pages.
 */
public interface SettingsPage {
    /** A stable id, such as {@code terminal.font}. */
    String id();

    /** The title shown in the tree and the breadcrumb. */
    String title();

    /** The id of the page this one sits under. */
    Optional<String> parent();

    /** Text the tree search matches: labels, descriptions, keywords and choice names. */
    List<String> searchTerms();

    /** The page's content; built on first use and kept until {@link #dispose}. */
    JComponent component();

    /** Whether the page holds changes Apply would save. */
    boolean modified();

    /** Drops this page's unapplied changes. */
    void reset();

    /** Saves this page's changes; completes on any thread. */
    CompletableFuture<Void> apply();

    /** Marks what matches {@code query} on the page; a blank query clears the marks. */
    void highlight(String query);

    /** Releases listeners; the page is not used again. */
    void dispose();

    /** Whether every control holds a value Apply could save. */
    default boolean valid() { return true; }

    /** Whether the tree hides this page while no page names it as parent. */
    default boolean hiddenWhenChildless() { return false; }

    /** The application's state changed; re-read anything shown from it. */
    default void refresh() { }

    /** The configuration guide section that documents this page. */
    default String helpAnchor() { return "supported-settings"; }
}
```


`PageContext.java`:

```java
package dev.jasper.app.settingsui;

import java.nio.file.Path;
import java.util.Optional;
import java.util.function.BiFunction;
import java.util.function.Consumer;

/**
 * What a page may ask of the dialog it is in.
 *
 * @param draft the dialog's pending core-setting edits
 * @param navigate selects another page by id
 * @param closeThen closes the dialog (asking about unsaved changes) and then runs the action
 * @param chooseFile asks for an existing file over the dialog: title and initial path in, choice out
 * @param changed tells the dialog a page's validity or modification changed outside the draft
 */
public record PageContext(SettingsDraft draft, Consumer<String> navigate, Consumer<Runnable> closeThen,
                          BiFunction<String, Optional<Path>, Optional<Path>> chooseFile, Runnable changed) { }
```

`SearchText.java`:

```java
package dev.jasper.app.settingsui;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/** The Settings search rule: every word of the query occurs in the text, ignoring case. */
public final class SearchText {
    private SearchText() { }

    /** The query's words, lower-cased. */
    public static List<String> words(String query) {
        return Arrays.stream(query.toLowerCase(Locale.ROOT).trim().split("\\s+")).filter(word -> !word.isEmpty()).toList();
    }

    /** Whether every word of {@code query} occurs in {@code text}; false for a blank query. */
    public static boolean matches(String text, String query) {
        List<String> words = words(query);
        if (words.isEmpty()) return false;
        String haystack = text.toLowerCase(Locale.ROOT);
        return words.stream().allMatch(haystack::contains);
    }
}
```

`SettingsDraft.java`:

```java
package dev.jasper.app.settingsui;

import dev.jasper.app.config.SettingsEdit;
import dev.jasper.app.lifecycle.Subscription;
import dev.jasper.app.settings.Setting;
import dev.jasper.app.settings.SettingValues;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * The Settings dialog's view of the core settings: the saved values it opened with (the baseline) and the edits the
 * user has made since. An empty pending value means "restore the default", which removes the key when saved. EDT only.
 */
public final class SettingsDraft {
    private final Map<Setting<?>, Optional<?>> pending = new LinkedHashMap<>();
    private final List<Runnable> listeners = new ArrayList<>();
    private SettingValues baseline;

    public SettingsDraft(SettingValues baseline) { this.baseline = Objects.requireNonNull(baseline, "baseline"); }

    public SettingValues baseline() { return baseline; }

    /** The value the dialog shows: the pending edit, else the saved value, else the default. */
    @SuppressWarnings("unchecked")
    public <T> T value(Setting<T> setting) {
        if (!pending.containsKey(setting)) return baseline.get(setting);
        Optional<T> staged = (Optional<T>) pending.get(setting);
        return staged.orElse(setting.defaultValue());
    }

    /** The saved value, or empty when the file does not set it. */
    public <T> Optional<T> saved(Setting<T> setting) { return baseline.saved(setting); }

    /** Records an edit; one that matches the saved state removes any pending edit instead. */
    public <T> void stage(Setting<T> setting, Optional<T> value) {
        Objects.requireNonNull(value, "value");
        if (value.equals(baseline.saved(setting))) pending.remove(setting);
        else pending.put(setting, value);
        changed();
    }

    public boolean modified() { return !pending.isEmpty(); }

    public boolean modified(Setting<?> setting) { return pending.containsKey(setting); }

    /** How many settings have pending edits. */
    public int size() { return pending.size(); }

    /** The save request for every pending edit, expecting the baseline's saved values. */
    public SettingsEdit edit() {
        var expected = new LinkedHashMap<Setting<?>, Optional<?>>();
        for (Setting<?> setting : pending.keySet()) expected.put(setting, baseline.saved(setting));
        return new SettingsEdit(pending, expected);
    }

    /** Adopts newer saved values; pending edits the new values already hold are dropped. */
    public void rebase(SettingValues next) {
        baseline = Objects.requireNonNull(next, "next");
        pending.entrySet().removeIf(entry -> entry.getValue().equals(baseline.saved(entry.getKey())));
        changed();
    }

    /** Drops the pending edits of {@code settings}. */
    public void reset(Collection<? extends Setting<?>> settings) {
        if (pending.keySet().removeAll(settings)) changed();
    }

    public Subscription onChanged(Runnable listener) {
        listeners.add(Objects.requireNonNull(listener, "listener"));
        return new Subscription(() -> listeners.remove(listener));
    }

    private void changed() { for (Runnable listener : List.copyOf(listeners)) listener.run(); }
}
```

- [ ] **Step 4: Run the test to verify it passes**

Run: `./gradlew :jasper-app:test --tests "dev.jasper.app.settingsui.*"`
Expected: PASS (4 tests). If the draft's change count differs, note that `stage` always notifies, including when it removes an edit.

- [ ] **Step 5: Commit**

```bash
git branch --show-current
git add jasper-app/src
git commit -m "Add the Settings page model, pending-edit draft and search rule

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 4: Editors

**Files:**
- Create: `jasper-app/src/main/java/dev/jasper/app/settingsui/ValueEditor.java`
- Create: `jasper-app/src/main/java/dev/jasper/app/settingsui/Editors.java`
- Create: `jasper-app/src/main/java/dev/jasper/app/settingsui/Fonts.java`
- Test: `jasper-app/src/test/java/dev/jasper/app/settingsui/EditorsTest.java`

**Interfaces:**
- Consumes: `PageContext`, `Setting`, `SettingKind` records (`Bool`, `IntegerRange`, `NumberRange`, `Choice`, `Text`, `StringList`, `StringMap`).
- Produces: `ValueEditor<T>` (`component()`, `value()`, `show(T)`, `onEdit(Runnable)`, defaults `labelled()`, `removesAtDefault()`); `Editors.Factory<T>(BiFunction<Setting<T>, PageContext, ValueEditor<T>> build, List<String> terms)`; factories `Editors.byKind(Setting<T>)`, `checkbox()`, `integer(String unit)`, `number(double step, String unit)`, `choice(Map<String, String> names)`, `text()`, `font(boolean monospaced)`, `theme(Supplier<List<String>> names)`, `executable()`, `textList()`, `fontList(boolean monospaced)`, `table()`, `optionalNumber(String label, double step, String unit)`; `Editors.pretty(String token)`; `Fonts.families(boolean monospaced)`.

- [ ] **Step 1: Write the failing test**

```java
package dev.jasper.app.settingsui;

import static org.assertj.core.api.Assertions.*;

import dev.jasper.app.config.ConfigLoader;
import dev.jasper.app.config.CoreSettings;
import dev.jasper.app.config.ToolbarMode;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JSpinner;
import javax.swing.JTextField;
import org.junit.jupiter.api.Test;

class EditorsTest {
    private final PageContext context = new PageContext(
        new SettingsDraft(ConfigLoader.parse(Path.of("config.toml"), "", true).values()),
        id -> { }, Runnable::run, (title, initial) -> Optional.of(Path.of("/bin/zsh")), () -> { });

    private static <T> ValueEditor<T> build(Editors.Factory<T> factory, dev.jasper.app.settings.Setting<T> setting, PageContext context) {
        return factory.build().apply(setting, context);
    }

    @Test void kindsPickTheirDefaultEditors() {
        assertThat(build(Editors.byKind(CoreSettings.WINDOW_STATUS_BAR), CoreSettings.WINDOW_STATUS_BAR, context).component()).isInstanceOf(JCheckBox.class);
        assertThat(build(Editors.byKind(CoreSettings.WINDOW_TOOLBAR), CoreSettings.WINDOW_TOOLBAR, context).component()).isInstanceOf(JComboBox.class);
        assertThat(build(Editors.byKind(CoreSettings.UI_THEME_NAME), CoreSettings.UI_THEME_NAME, context).component()).isInstanceOf(JTextField.class);
        assertThat(build(Editors.byKind(CoreSettings.PALETTE_MAX_RESULTS), CoreSettings.PALETTE_MAX_RESULTS, context).component()).isInstanceOf(JSpinner.class);
    }

    @Test void checkboxesCarryTheirLabelAndShowingDoesNotCountAsAnEdit() {
        var editor = build(Editors.checkbox(), CoreSettings.WINDOW_STATUS_BAR, context);
        var edits = new int[1];
        editor.onEdit(() -> edits[0]++);
        assertThat(editor.labelled()).isTrue();
        assertThat(((JCheckBox) editor.component()).getText()).isEqualTo("Show status bar");
        editor.show(false);
        assertThat(editor.value()).isFalse();
        assertThat(edits[0]).isZero();
        ((JCheckBox) editor.component()).doClick();
        assertThat(editor.value()).isTrue();
        assertThat(edits[0]).isEqualTo(1);
    }

    @Test void spinnersUseTheKindsRangeAndKeepTheirValueType() {
        var height = build(Editors.integer("px"), CoreSettings.WINDOW_TAB_HEIGHT, context);
        height.show(44);
        assertThat(height.value()).isEqualTo(44);
        var size = build(Editors.number(0.5, "pt"), CoreSettings.TERMINAL_FONT_SIZE, context);
        size.show(16f);
        assertThat(size.value()).isEqualTo(16f);
    }

    @Test void choicesShowReadableNamesAndReturnTheirValues() {
        var editor = build(Editors.<ToolbarMode>choice(Map.of("icons", "Icons only")), CoreSettings.WINDOW_TOOLBAR, context);
        @SuppressWarnings("unchecked") var box = (JComboBox<String>) editor.component();
        assertThat(box.getItemAt(0)).isEqualTo("Icons and labels");
        assertThat(box.getItemAt(1)).isEqualTo("Icons only");
        editor.show(ToolbarMode.HIDDEN);
        assertThat(box.getSelectedItem()).isEqualTo("Hidden");
        assertThat(editor.value()).isEqualTo(ToolbarMode.HIDDEN);
        assertThat(Editors.pretty("close_on_success")).isEqualTo("Close on success");
        assertThat(Editors.<ToolbarMode>choice(Map.of("icons", "Icons only")).terms()).contains("Icons only");
    }

    @Test void listsTablesAndExecutablesEditTheirValues() {
        var args = (Editors.Items) build(Editors.textList(), CoreSettings.TERMINAL_SHELL_ARGS, context);
        args.show(List.of("-l"));
        args.add("-i");
        assertThat(args.value()).containsExactly("-l", "-i");
        args.select(1); args.moveUp();
        assertThat(args.value()).containsExactly("-i", "-l");
        args.select(0); args.removeSelected();
        assertThat(args.value()).containsExactly("-l");
        var env = (Editors.Table) build(Editors.table(), CoreSettings.TERMINAL_ENV, context);
        env.show(Map.of("B", "2", "A", "1"));
        assertThat(env.value()).containsExactly(Map.entry("A", "1"), Map.entry("B", "2"));
        env.addRow("C", "3");
        assertThat(env.value()).containsEntry("C", "3");
        var shell = (Editors.Executable) build(Editors.executable(), CoreSettings.TERMINAL_SHELL_PROGRAM, context);
        shell.browse();
        assertThat(shell.value()).isEqualTo("/bin/zsh");
    }

    @Test void theOptionalSizeUsesThePlatformUntilUnchecked() {
        var editor = (Editors.OptionalNumber) build(Editors.optionalNumber("Use platform size", 0.5, "pt"), CoreSettings.UI_FONT_SIZE, context);
        assertThat(editor.removesAtDefault()).isTrue();
        editor.show(0f);
        assertThat(editor.platform.isSelected()).isTrue();
        assertThat(editor.value()).isEqualTo(0f);
        editor.platform.doClick();
        assertThat(editor.value()).isEqualTo(14f);
        editor.show(18f);
        assertThat(editor.platform.isSelected()).isFalse();
        assertThat(editor.value()).isEqualTo(18f);
    }

    @Test void fontsListInstalledFamiliesAndAMonospacedSubset() {
        assertThat(Fonts.families(false)).contains("Monospaced", "Dialog");
        assertThat(Fonts.families(true)).contains("Monospaced").doesNotContain("Dialog");
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `./gradlew :jasper-app:test --tests "dev.jasper.app.settingsui.EditorsTest"`
Expected: FAIL: `ValueEditor`, `Editors` and `Fonts` do not exist.

- [ ] **Step 3: Write the implementation**

`ValueEditor.java`:

```java
package dev.jasper.app.settingsui;

import javax.swing.JComponent;

/** One setting's control on a page. EDT only. */
public interface ValueEditor<T> {
    JComponent component();

    /** The control's current value; throws IllegalArgumentException when it cannot be read as one. */
    T value();

    /** Shows {@code value} without counting as an edit. */
    void show(T value);

    /** Runs {@code listener} after each edit the user makes. */
    void onEdit(Runnable listener);

    /** Whether the control shows the setting's label itself, as a checkbox does. */
    default boolean labelled() { return false; }

    /** Whether choosing the default value means "remove the key" rather than "save the default". */
    default boolean removesAtDefault() { return false; }
}
```

`Fonts.java`:

```java
package dev.jasper.app.settingsui;

import java.awt.Font;
import java.awt.GraphicsEnvironment;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;

/** Installed font families, read once; the monospaced subset draws 'i' and 'W' at the same width. */
final class Fonts {
    private static List<String> all;
    private static List<String> monospaced;

    private Fonts() { }

    static synchronized List<String> families(boolean monospacedOnly) {
        if (all == null) {
            all = List.of(GraphicsEnvironment.getLocalGraphicsEnvironment().getAvailableFontFamilyNames());
            var graphics = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB).createGraphics();
            try {
                var fixed = new ArrayList<String>();
                for (String family : all) {
                    var metrics = graphics.getFontMetrics(new Font(family, Font.PLAIN, 12));
                    if (metrics.charWidth('i') == metrics.charWidth('W')) fixed.add(family);
                }
                monospaced = List.copyOf(fixed);
            } finally { graphics.dispose(); }
        }
        return monospacedOnly ? monospaced : all;
    }
}
```

`Editors.java`:

```java
package dev.jasper.app.settingsui;

import com.formdev.flatlaf.FlatClientProperties;
import dev.jasper.app.settings.Setting;
import dev.jasper.app.settings.SettingKind;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Supplier;
import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.table.DefaultTableModel;
import javax.swing.text.JTextComponent;

/** Editor factories for settings, chosen by kind unless a page asks for another. */
public final class Editors {
    private Editors() { }

    /**
     * Builds one setting's editor on a page.
     *
     * @param build creates the editor
     * @param terms extra words the search should match, such as choice names
     */
    public record Factory<T>(BiFunction<Setting<T>, PageContext, ValueEditor<T>> build, List<String> terms) {
        public Factory { terms = List.copyOf(terms); }
    }

    /** The editor a setting's kind implies. */
    @SuppressWarnings("unchecked")
    public static <T> Factory<T> byKind(Setting<T> setting) {
        Factory<?> factory = switch (setting.kind()) {
            case SettingKind.Bool ignored -> checkbox();
            case SettingKind.IntegerRange ignored -> integer("");
            case SettingKind.NumberRange ignored -> number(1, "");
            case SettingKind.Choice<?> ignored -> choice(Map.of());
            case SettingKind.Text ignored -> text();
            case SettingKind.StringList ignored -> textList();
            case SettingKind.StringMap ignored -> table();
        };
        return (Factory<T>) factory;
    }

    public static Factory<Boolean> checkbox() {
        return new Factory<>((setting, context) -> new Check(setting.label()), List.of());
    }

    public static Factory<Integer> integer(String unit) {
        return new Factory<>((setting, context) -> {
            var kind = (SettingKind.IntegerRange) setting.kind();
            return new Spin<>(new SpinnerNumberModel(kind.min(), kind.min(), kind.max(), 1), unit, "0", Number::intValue);
        }, List.of());
    }

    public static Factory<Float> number(double step, String unit) {
        return new Factory<>((setting, context) -> {
            var kind = (SettingKind.NumberRange) setting.kind();
            return new Spin<>(new SpinnerNumberModel(kind.min(), kind.min(), kind.max(), step), unit, "0.##", Number::floatValue);
        }, List.of());
    }

    /** A combo box; {@code names} maps tokens to display names, and other tokens are prettified. */
    public static <E> Factory<E> choice(Map<String, String> names) {
        return new Factory<>((setting, context) -> new Pick<>((SettingKind.Choice<E>) setting.kind(), names), List.copyOf(names.values()));
    }

    public static Factory<String> text() {
        return new Factory<>((setting, context) -> new Line(), List.of());
    }

    /** An editable combo of installed font families. */
    public static Factory<String> font(boolean monospaced) {
        return new Factory<>((setting, context) -> new Combo(() -> Fonts.families(monospaced)), List.of());
    }

    /** An editable combo of theme names. */
    public static Factory<String> theme(Supplier<List<String>> names) {
        return new Factory<>((setting, context) -> new Combo(names), List.of());
    }

    /** A text field with Browse…, for a program path. */
    public static Factory<String> executable() {
        return new Factory<>((setting, context) -> new Executable(context), List.of());
    }

    /** An ordered list of strings; + asks for text. */
    public static Factory<List<String>> textList() {
        return new Factory<>((setting, context) -> new Items(parent ->
            Optional.ofNullable(JOptionPane.showInputDialog(parent, "Add:")).filter(text -> !text.isEmpty())), List.of());
    }

    /** An ordered list of font families; + offers the installed ones. */
    public static Factory<List<String>> fontList(boolean monospaced) {
        return new Factory<>((setting, context) -> new Items(parent -> {
            var box = new JComboBox<>(Fonts.families(monospaced).toArray(String[]::new));
            box.setEditable(true);
            int answer = JOptionPane.showConfirmDialog(parent, box, "Add a fallback font", JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
            return answer == JOptionPane.OK_OPTION ? Optional.ofNullable((String) box.getSelectedItem()).filter(name -> !name.isBlank()) : Optional.empty();
        }), List.of());
    }

    /** A Name/Value table. */
    public static Factory<Map<String, String>> table() {
        return new Factory<>((setting, context) -> new Table(), List.of());
    }

    /** A checkbox meaning "use the default", and a spinner enabled when it is cleared; the default removes the key. */
    public static Factory<Float> optionalNumber(String label, double step, String unit) {
        return new Factory<>((setting, context) -> new OptionalNumber(setting, label, step, unit), List.of());
    }

    /** {@code icons_and_labels} becomes "Icons and labels". */
    public static String pretty(String token) {
        String spaced = token.replace('_', ' ');
        return spaced.isEmpty() ? spaced : Character.toUpperCase(spaced.charAt(0)) + spaced.substring(1);
    }

    private static JPanel line(JComponent... parts) {
        var panel = new JPanel(new FlowLayout(FlowLayout.LEADING, 6, 0));
        panel.setOpaque(false);
        for (JComponent part : parts) panel.add(part);
        return panel;
    }

    private static void onText(JTextComponent field, Runnable listener) {
        field.getDocument().addDocumentListener(new DocumentListener() {
            @Override public void insertUpdate(DocumentEvent event) { listener.run(); }
            @Override public void removeUpdate(DocumentEvent event) { listener.run(); }
            @Override public void changedUpdate(DocumentEvent event) { listener.run(); }
        });
    }

    static final class Check implements ValueEditor<Boolean> {
        private final JCheckBox box;
        Check(String label) {
            box = new JCheckBox(label);
            box.putClientProperty("html.disable", true);
            box.setOpaque(false);
        }
        @Override public JComponent component() { return box; }
        @Override public Boolean value() { return box.isSelected(); }
        @Override public void show(Boolean value) { box.setSelected(value); }
        @Override public void onEdit(Runnable listener) { box.addActionListener(event -> listener.run()); }
        @Override public boolean labelled() { return true; }
    }

    static final class Spin<T extends Number> implements ValueEditor<T> {
        final JSpinner spinner;
        private final JComponent component;
        private final Function<Number, T> convert;
        private boolean showing;
        Spin(SpinnerNumberModel model, String unit, String pattern, Function<Number, T> convert) {
            this.convert = convert;
            spinner = new JSpinner(model);
            spinner.setEditor(new JSpinner.NumberEditor(spinner, pattern));
            component = unit.isEmpty() ? spinner : line(spinner, new JLabel(unit));
        }
        @Override public JComponent component() { return component; }
        @Override public T value() { return convert.apply((Number) spinner.getValue()); }
        @Override public void show(T value) {
            showing = true;
            try {
                spinner.setValue(spinner.getModel().getValue() instanceof Double ? (Object) value.doubleValue() : (Object) value.intValue());
            } finally { showing = false; }
        }
        @Override public void onEdit(Runnable listener) { spinner.addChangeListener(event -> { if (!showing) listener.run(); }); }
    }

    static final class Pick<E> implements ValueEditor<E> {
        private final JComboBox<String> box = new JComboBox<>();
        private final List<E> values = new ArrayList<>();
        private boolean showing;
        Pick(SettingKind.Choice<E> kind, Map<String, String> names) {
            for (var token : kind.tokens()) {
                box.addItem(names.getOrDefault(token.getKey(), pretty(token.getKey())));
                values.add(token.getValue());
            }
        }
        @Override public JComponent component() { return box; }
        @Override public E value() { return values.get(box.getSelectedIndex()); }
        @Override public void show(E value) {
            showing = true;
            try { box.setSelectedIndex(values.indexOf(value)); } finally { showing = false; }
        }
        @Override public void onEdit(Runnable listener) { box.addActionListener(event -> { if (!showing) listener.run(); }); }
    }

    static final class Line implements ValueEditor<String> {
        final JTextField field = new JTextField(24);
        private boolean showing;
        @Override public JComponent component() { return field; }
        @Override public String value() { return field.getText(); }
        @Override public void show(String value) {
            showing = true;
            try { field.setText(value); } finally { showing = false; }
        }
        @Override public void onEdit(Runnable listener) { onText(field, () -> { if (!showing) listener.run(); }); }
    }

    static final class Combo implements ValueEditor<String> {
        private final JComboBox<String> box;
        private boolean showing;
        Combo(Supplier<List<String>> options) {
            box = new JComboBox<>(options.get().toArray(String[]::new));
            box.setEditable(true);
            box.setPrototypeDisplayValue("JetBrains Mono NL Extra");
        }
        private JTextComponent editor() { return (JTextComponent) box.getEditor().getEditorComponent(); }
        @Override public JComponent component() { return box; }
        @Override public String value() { return editor().getText(); }
        @Override public void show(String value) {
            showing = true;
            try { box.setSelectedItem(value); editor().setText(value); } finally { showing = false; }
        }
        @Override public void onEdit(Runnable listener) { onText(editor(), () -> { if (!showing) listener.run(); }); }
    }

    static final class Executable implements ValueEditor<String> {
        private final Line line = new Line();
        private final JComponent component;
        private final PageContext context;
        Executable(PageContext context) {
            this.context = context;
            var browse = new JButton("Browse…");
            browse.addActionListener(event -> browse());
            component = line(line.field, browse);
        }
        void browse() {
            String current = line.value();
            context.chooseFile().apply("Choose a shell program", current.isBlank() ? Optional.empty() : Optional.of(java.nio.file.Path.of(current)))
                .ifPresent(path -> line.field.setText(path.toString()));
        }
        @Override public JComponent component() { return component; }
        @Override public String value() { return line.value(); }
        @Override public void show(String value) { line.show(value); }
        @Override public void onEdit(Runnable listener) { line.onEdit(listener); }
    }

    static final class Items implements ValueEditor<List<String>> {
        private final DefaultListModel<String> model = new DefaultListModel<>();
        private final JList<String> list = new JList<>(model);
        private final JPanel component = new JPanel(new BorderLayout(0, 4));
        private final List<Runnable> listeners = new ArrayList<>();
        Items(Function<Component, Optional<String>> ask) {
            component.setOpaque(false);
            var scroll = new JScrollPane(list);
            scroll.setPreferredSize(new Dimension(320, 110));
            component.add(scroll, BorderLayout.CENTER);
            var add = button("+", "Add", () -> ask.apply(component).ifPresent(this::add));
            var remove = button("−", "Remove", this::removeSelected);
            var up = button("↑", "Move up", this::moveUp);
            var down = button("↓", "Move down", this::moveDown);
            component.add(line(add, remove, up, down), BorderLayout.SOUTH);
        }
        private static JButton button(String text, String name, Runnable action) {
            var button = new JButton(text);
            button.putClientProperty(FlatClientProperties.BUTTON_TYPE, FlatClientProperties.BUTTON_TYPE_TOOLBAR_BUTTON);
            button.setToolTipText(name);
            button.getAccessibleContext().setAccessibleName(name);
            button.addActionListener(event -> action.run());
            return button;
        }
        void add(String item) { model.addElement(item); edited(); }
        void select(int index) { list.setSelectedIndex(index); }
        void removeSelected() {
            int index = list.getSelectedIndex();
            if (index < 0) return;
            model.remove(index);
            edited();
        }
        void moveUp() { move(-1); }
        void moveDown() { move(1); }
        private void move(int by) {
            int index = list.getSelectedIndex(), target = index + by;
            if (index < 0 || target < 0 || target >= model.size()) return;
            String item = model.remove(index);
            model.add(target, item);
            list.setSelectedIndex(target);
            edited();
        }
        private void edited() { for (Runnable listener : List.copyOf(listeners)) listener.run(); }
        @Override public JComponent component() { return component; }
        @Override public List<String> value() { return List.copyOf(Collections.list(model.elements())); }
        @Override public void show(List<String> value) { model.clear(); value.forEach(model::addElement); }
        @Override public void onEdit(Runnable listener) { listeners.add(listener); }
    }

    static final class Table implements ValueEditor<Map<String, String>> {
        private final DefaultTableModel model = new DefaultTableModel(new Object[]{"Name", "Value"}, 0);
        private final JTable table = new JTable(model);
        private final JPanel component = new JPanel(new BorderLayout(0, 4));
        private boolean showing;
        Table() {
            component.setOpaque(false);
            table.putClientProperty("terminateEditOnFocusLost", true);
            var scroll = new JScrollPane(table);
            scroll.setPreferredSize(new Dimension(420, 130));
            component.add(scroll, BorderLayout.CENTER);
            var add = Items.button("+", "Add variable", () -> addRow("", ""));
            var remove = Items.button("−", "Remove variable", () -> {
                int row = table.getSelectedRow();
                if (row >= 0) model.removeRow(row);
            });
            component.add(line(add, remove), BorderLayout.SOUTH);
        }
        void addRow(String name, String value) { model.addRow(new Object[]{name, value}); }
        @Override public JComponent component() { return component; }
        @Override public Map<String, String> value() {
            var values = new LinkedHashMap<String, String>();
            for (int row = 0; row < model.getRowCount(); row++) {
                String name = String.valueOf(model.getValueAt(row, 0));
                if (!name.isBlank()) values.put(name, String.valueOf(model.getValueAt(row, 1)));
            }
            return values;
        }
        @Override public void show(Map<String, String> value) {
            showing = true;
            try {
                model.setRowCount(0);
                new TreeMap<>(value).forEach(this::addRow);
            } finally { showing = false; }
        }
        @Override public void onEdit(Runnable listener) { model.addTableModelListener(event -> { if (!showing) listener.run(); }); }
    }

    static final class OptionalNumber implements ValueEditor<Float> {
        final JCheckBox platform;
        private final Spin<Float> spin;
        private final JComponent component;
        private final float unset;
        private final float fallback;
        private final List<Runnable> listeners = new ArrayList<>();
        OptionalNumber(Setting<Float> setting, String label, double step, String unit) {
            var kind = (SettingKind.NumberRange) setting.kind();
            unset = setting.defaultValue();
            fallback = setting.example().orElse((float) kind.min());
            platform = new JCheckBox(label);
            platform.setOpaque(false);
            spin = new Spin<>(new SpinnerNumberModel(fallback, kind.min(), kind.max(), step), unit, "0.##", Number::floatValue);
            platform.addActionListener(event -> { spin.spinner.setEnabled(!platform.isSelected()); edited(); });
            spin.onEdit(this::edited);
            component = line(platform, spin.component());
        }
        private void edited() { for (Runnable listener : List.copyOf(listeners)) listener.run(); }
        @Override public JComponent component() { return component; }
        @Override public Float value() { return platform.isSelected() ? unset : spin.value(); }
        @Override public void show(Float value) {
            boolean useDefault = value == unset;
            platform.setSelected(useDefault);
            spin.spinner.setEnabled(!useDefault);
            spin.show(useDefault ? fallback : value);
        }
        @Override public void onEdit(Runnable listener) { listeners.add(listener); }
        @Override public boolean removesAtDefault() { return true; }
    }
}
```

Notes for the implementer:
- `Pick`'s constructor casts `setting.kind()` to `SettingKind.Choice<E>`; the unchecked warning is expected.
- `Spin.show` writes a `Double` into a double model and an `Integer` into an integer model, because `SpinnerNumberModel` compares values of the model's own type.
- `Items.button` is package-private static so `Table` can reuse it.

- [ ] **Step 4: Run the test to verify it passes**

Run: `./gradlew :jasper-app:test --tests "dev.jasper.app.settingsui.*"`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git branch --show-current
git add jasper-app/src
git commit -m "Add Settings editors for every setting kind

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 5: Schema pages, group pages and launcher pages

**Files:**
- Create: `jasper-app/src/main/java/dev/jasper/app/settingsui/HighlightBox.java`
- Create: `jasper-app/src/main/java/dev/jasper/app/settingsui/TitledSeparator.java`
- Create: `jasper-app/src/main/java/dev/jasper/app/settingsui/SchemaPage.java`
- Create: `jasper-app/src/main/java/dev/jasper/app/settingsui/GroupPage.java`
- Create: `jasper-app/src/main/java/dev/jasper/app/settingsui/LauncherPage.java`
- Test: `jasper-app/src/test/java/dev/jasper/app/settingsui/SchemaPageTest.java`

**Interfaces:**
- Consumes: `SettingsPage`, `PageContext`, `SettingsDraft`, `Editors.Factory`, `ValueEditor`, `SearchText`, `Setting.applies()`.
- Produces: `SchemaPage.page(String id, String title)` returning `SchemaPage.Spec` with `parent(String)`, `anchor(String)`, `section(String)`, `row(Setting<T>)`, `row(Setting<T>, Editors.Factory<T>)`, `note(String)`, `action(Supplier<String>, SchemaPage.Button...)`, `id()`, `settings()`, `build(PageContext)`; `SchemaPage.Button(String label, Runnable action, BooleanSupplier enabled)`; package-private `SchemaPage.rows()` returning `List<SchemaPage.Row<?>>` with `editor()`, `reset()` (the Reset button), `error()`, `comment()`, `box()`; `GroupPage(String id, String title, Optional<String> parent, boolean hiddenWhenChildless, Supplier<List<SettingsPage>> children, PageContext context)`; `LauncherPage(String id, String title, String description, String button, Runnable open, List<String> terms, PageContext context)`.

- [ ] **Step 1: Write the failing test**

```java
package dev.jasper.app.settingsui;

import static org.assertj.core.api.Assertions.*;

import dev.jasper.app.config.ConfigLoader;
import dev.jasper.app.config.CoreSettings;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JLabel;
import org.junit.jupiter.api.Test;

class SchemaPageTest {
    private final SettingsDraft draft = new SettingsDraft(ConfigLoader.parse(Path.of("config.toml"), "[window]\ntab_height = 44\n", true).values());
    private final List<String> navigated = new ArrayList<>();
    private final int[] changes = new int[1];
    private final PageContext context = new PageContext(draft, navigated::add, Runnable::run, (title, initial) -> Optional.empty(), () -> changes[0]++);

    private SchemaPage page() {
        return SchemaPage.page("window", "Window").parent("appearance-behavior").anchor("window-anchor")
            .section("Bars")
            .row(CoreSettings.WINDOW_STATUS_BAR)
            .row(CoreSettings.WINDOW_TAB_HEIGHT, Editors.integer("px"))
            .row(CoreSettings.TERMINAL_FONT_FAMILY, Editors.text()).note("Missing fonts fall back.")
            .row(CoreSettings.WINDOW_COLUMNS)
            .build(context);
    }

    @Test void rowsShowTheDraftAndStageEdits() {
        var page = page();
        page.component();
        var rows = page.rows();
        assertThat(rows).hasSize(4);
        var statusBar = (JCheckBox) rows.get(0).editor().component();
        assertThat(statusBar.isSelected()).isTrue();
        statusBar.doClick();
        assertThat(draft.value(CoreSettings.WINDOW_STATUS_BAR)).isFalse();
        assertThat(page.modified()).isTrue();
        page.reset();
        assertThat(page.modified()).isFalse();
        assertThat(statusBar.isSelected()).as("reset shows the saved value again").isTrue();
    }

    @Test void invalidValuesShowTheLoadersMessageAndAreNotStaged() {
        var page = page();
        page.component();
        @SuppressWarnings("unchecked") var font = (ValueEditor<String>) page.rows().get(2).editor();
        ((javax.swing.JTextField) font.component()).setText("   ");
        assertThat(page.rows().get(2).error().getText()).isEqualTo("Use a nonblank font name without NUL; using the default.");
        assertThat(page.rows().get(2).error().isVisible()).isTrue();
        assertThat(page.valid()).isFalse();
        assertThat(draft.modified(CoreSettings.TERMINAL_FONT_FAMILY)).isFalse();
        assertThat(changes[0]).isPositive();
        ((javax.swing.JTextField) font.component()).setText("Iosevka");
        assertThat(page.valid()).isTrue();
        assertThat(draft.value(CoreSettings.TERMINAL_FONT_FAMILY)).isEqualTo("Iosevka");
    }

    @Test void resetLinksAppearForNonDefaultValuesAndRemoveTheKey() {
        var page = page();
        page.component();
        JButton reset = page.rows().get(1).reset();
        assertThat(reset.isVisible()).as("44 is not the default 30").isTrue();
        assertThat(page.rows().get(0).reset().isVisible()).isFalse();
        reset.doClick();
        assertThat(draft.value(CoreSettings.WINDOW_TAB_HEIGHT)).isEqualTo(30);
        assertThat(draft.edit().changes()).containsEntry(CoreSettings.WINDOW_TAB_HEIGHT, Optional.empty());
        assertThat(reset.isVisible()).isFalse();
    }

    @Test void commentsSayWhenAChangeAppliesAndIncludeNotes() {
        var page = page();
        page.component();
        assertThat(page.rows().get(3).comment().getText()).isEqualTo("Applies to new windows");
        assertThat(page.rows().get(2).comment().getText()).isEqualTo("Missing fonts fall back.");
        assertThat(page.rows().get(0).comment().isVisible()).isFalse();
    }

    @Test void searchTermsAndHighlightCoverLabelsDescriptionsAndKeywords() {
        var page = page();
        assertThat(page.searchTerms()).contains("Window", "Show status bar", "Tab height");
        assertThat(String.join(" ", page.searchTerms())).contains("footer");
        page.component();
        page.highlight("footer");
        assertThat(page.rows().get(0).box().highlighted()).isTrue();
        assertThat(page.rows().get(1).box().highlighted()).isFalse();
        page.highlight("");
        assertThat(page.rows().get(0).box().highlighted()).isFalse();
        assertThat(page.helpAnchor()).isEqualTo("window-anchor");
        assertThat(page.parent()).hasValue("appearance-behavior");
    }

    @Test void actionRowsReadTheirTextAndEnablementOnRefresh() {
        boolean[] enabled = {false};
        int[] runs = {0};
        var page = SchemaPage.page("system", "System Settings")
            .action(() -> "Rewrite old names.", new SchemaPage.Button("Migrate Config…", () -> runs[0]++, () -> enabled[0]))
            .build(context);
        page.component();
        JButton migrate = page.actionButtons().getFirst();
        assertThat(migrate.isEnabled()).isFalse();
        enabled[0] = true;
        page.refresh();
        assertThat(migrate.isEnabled()).isTrue();
        migrate.doClick();
        assertThat(runs[0]).isEqualTo(1);
    }

    @Test void groupPagesLinkToTheirChildren() {
        var child = page();
        var group = new GroupPage("appearance-behavior", "Appearance & Behavior", Optional.empty(), false, () -> List.of(child), context);
        var link = (JButton) ((javax.swing.JPanel) group.component()).getComponent(1);
        assertThat(link.getText()).isEqualTo("Window");
        link.doClick();
        assertThat(navigated).containsExactly("window");
        assertThat(group.modified()).isFalse();
        var tools = new GroupPage("tools", "Tools", Optional.empty(), true, List::of, context);
        assertThat(tools.hiddenWhenChildless()).isTrue();
    }

    @Test void launcherPagesCloseTheDialogThenOpenTheirWindow() {
        int[] opened = {0};
        var keymap = new LauncherPage("keymap", "Keymap", "Keyboard shortcuts.", "Open Keyboard Shortcuts…", () -> opened[0]++,
            List.of("shortcuts"), context);
        assertThat(keymap.searchTerms()).contains("Keymap", "shortcuts");
        JButton open = keymap.button();
        open.doClick();
        assertThat(opened[0]).isEqualTo(1);
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `./gradlew :jasper-app:test --tests "dev.jasper.app.settingsui.SchemaPageTest"`
Expected: FAIL: `SchemaPage`, `GroupPage`, `LauncherPage` do not exist.

- [ ] **Step 3: Write the implementation**

`HighlightBox.java`:

```java
package dev.jasper.app.settingsui;

import com.formdev.flatlaf.util.UIScale;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import javax.swing.BorderFactory;
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.UIManager;

/** Holds a row's label and paints a rounded search-match background behind it while highlighted. */
final class HighlightBox extends JPanel {
    private boolean highlighted;

    HighlightBox(JComponent child) {
        super(new BorderLayout());
        setOpaque(false);
        child.setOpaque(false);
        add(child, BorderLayout.CENTER);
        setBorder(BorderFactory.createEmptyBorder(1, 3, 1, 3));
    }

    boolean highlighted() { return highlighted; }

    void setHighlighted(boolean value) {
        if (highlighted == value) return;
        highlighted = value;
        repaint();
    }

    @Override protected void paintComponent(Graphics graphics) {
        if (highlighted) {
            Color color = UIManager.getColor("SearchMatch.startBackground");
            if (color == null) color = UIManager.getColor("TextComponent.selectionBackgroundInactive");
            if (color == null) color = new Color(255, 214, 102);
            var g = (Graphics2D) graphics.create();
            try {
                g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g.setColor(color);
                int arc = UIScale.scale(8);
                g.fillRoundRect(0, 0, getWidth(), getHeight(), arc, arc);
            } finally { g.dispose(); }
        }
        super.paintComponent(graphics);
    }
}
```

`TitledSeparator.java`:

```java
package dev.jasper.app.settingsui;

import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JSeparator;
import javax.swing.UIManager;

/** IntelliJ's section header: a title followed by a line to the right edge. */
final class TitledSeparator extends JPanel {
    final JLabel title;

    TitledSeparator(String text) {
        super(new GridBagLayout());
        setOpaque(false);
        title = new JLabel(text);
        title.putClientProperty("html.disable", true);
        title.setForeground(UIManager.getColor("Label.disabledForeground"));
        var c = new GridBagConstraints();
        c.gridx = 0; c.anchor = GridBagConstraints.WEST; c.insets = new Insets(0, 0, 0, 8);
        add(title, c);
        c.gridx = 1; c.weightx = 1; c.fill = GridBagConstraints.HORIZONTAL; c.insets = new Insets(0, 0, 0, 0);
        add(new JSeparator(), c);
    }
}
```

`SchemaPage.java`:

```java
package dev.jasper.app.settingsui;

import com.formdev.flatlaf.FlatClientProperties;
import dev.jasper.app.lifecycle.Subscription;
import dev.jasper.app.platform.AppIcons;
import dev.jasper.app.settings.Setting;
import java.awt.Color;
import java.awt.FlowLayout;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.UIManager;

/** A page generated from a list of settings: IntelliJ-style rows of label, control, comment and Reset. EDT only. */
public final class SchemaPage implements SettingsPage {
    /** A button on an action row; {@code enabled} is read again on every refresh. */
    public record Button(String label, Runnable action, BooleanSupplier enabled) { }

    private sealed interface Item permits RowItem, SectionItem, ActionItem { }
    private record RowItem(Setting<?> setting, Editors.Factory<?> editor, String note) implements Item { }
    private record SectionItem(String title) implements Item { }
    private record ActionItem(Supplier<String> text, List<Button> buttons) implements Item { }

    /** Starts the spec of a page. */
    public static Spec page(String id, String title) { return new Spec(id, title); }

    /** A page's rows, in order; build it once per dialog. */
    public static final class Spec {
        private final String id;
        private final String title;
        private String parent;
        private String anchor = "supported-settings";
        private final List<Item> items = new ArrayList<>();

        private Spec(String id, String title) { this.id = Objects.requireNonNull(id); this.title = Objects.requireNonNull(title); }

        public String id() { return id; }
        public Spec parent(String value) { parent = value; return this; }
        public Spec anchor(String value) { anchor = value; return this; }
        public Spec section(String value) { items.add(new SectionItem(value)); return this; }
        public <T> Spec row(Setting<T> setting) { return row(setting, Editors.byKind(setting)); }
        public <T> Spec row(Setting<T> setting, Editors.Factory<T> editor) { items.add(new RowItem(setting, editor, "")); return this; }

        /** Adds a note to the previous row's comment. */
        public Spec note(String text) {
            if (items.isEmpty() || !(items.getLast() instanceof RowItem)) throw new IllegalStateException("A note follows a row");
            RowItem row = (RowItem) items.removeLast();
            items.add(new RowItem(row.setting(), row.editor(), text));
            return this;
        }

        public Spec action(Supplier<String> text, Button... buttons) { items.add(new ActionItem(text, List.of(buttons))); return this; }

        /** Every setting this page shows, in order. */
        public List<Setting<?>> settings() {
            return items.stream().filter(RowItem.class::isInstance).map(item -> ((RowItem) item).setting()).toList();
        }

        public SchemaPage build(PageContext context) { return new SchemaPage(this, context); }
    }

    /** One row's parts; package-private for tests. */
    static final class Row<T> {
        private final Setting<T> setting;
        private final ValueEditor<T> editor;
        private final HighlightBox box;
        private final JLabel comment = new JLabel();
        private final JLabel error = new JLabel();
        private final JButton reset = new JButton();
        private final String searchText;
        private boolean invalid;

        Row(Setting<T> setting, ValueEditor<T> editor, String note, List<String> terms) {
            this.setting = setting;
            this.editor = editor;
            JComponent labelled = editor.labelled() ? editor.component() : label(setting.label() + ":");
            box = new HighlightBox(labelled);
            String applies = switch (setting.applies()) {
                case LIVE -> "";
                case NEW_PANES -> "Applies to new panes";
                case NEW_WINDOWS -> "Applies to new windows";
                case RESTART -> "Takes effect after restart";
            };
            String text = applies.isEmpty() ? note : note.isEmpty() ? applies : applies + ". " + note;
            comment.setText(text);
            comment.setVisible(!text.isEmpty());
            comment.putClientProperty("html.disable", true);
            comment.setForeground(UIManager.getColor("Label.disabledForeground"));
            comment.setFont(comment.getFont().deriveFont(comment.getFont().getSize2D() - 1f));
            error.putClientProperty("html.disable", true);
            Color red = UIManager.getColor("Jasper.configErrorForeground");
            error.setForeground(red != null ? red : Color.RED);
            error.setVisible(false);
            reset.setIcon(AppIcons.bundled("general/reset"));
            reset.putClientProperty(FlatClientProperties.BUTTON_TYPE, FlatClientProperties.BUTTON_TYPE_BORDERLESS);
            reset.setToolTipText("Reset to default");
            reset.getAccessibleContext().setAccessibleName("Reset " + setting.label() + " to default");
            searchText = String.join(" ", List.of(setting.label(), setting.description(), String.join(" ", setting.keywords()),
                String.join(" ", terms)));
        }

        ValueEditor<T> editor() { return editor; }
        JButton reset() { return reset; }
        JLabel error() { return error; }
        JLabel comment() { return comment; }
        HighlightBox box() { return box; }
    }

    private final Spec spec;
    private final PageContext context;
    private final List<Row<?>> rows = new ArrayList<>();
    private final List<JButton> actionButtons = new ArrayList<>();
    private final List<Runnable> actionRefreshers = new ArrayList<>();
    private JPanel panel;
    private Subscription listening;

    private SchemaPage(Spec spec, PageContext context) { this.spec = spec; this.context = context; }

    @Override public String id() { return spec.id; }
    @Override public String title() { return spec.title; }
    @Override public Optional<String> parent() { return Optional.ofNullable(spec.parent); }
    @Override public String helpAnchor() { return spec.anchor; }

    @Override public List<String> searchTerms() {
        var terms = new ArrayList<String>();
        terms.add(spec.title);
        for (Item item : spec.items) {
            if (item instanceof RowItem row) {
                terms.add(row.setting().label());
                terms.add(row.setting().description());
                terms.add(String.join(" ", row.setting().keywords()));
                terms.addAll(row.editor().terms());
            } else if (item instanceof SectionItem section) terms.add(section.title());
        }
        return List.copyOf(terms);
    }

    List<Row<?>> rows() { return rows; }
    List<JButton> actionButtons() { return actionButtons; }

    @Override public JComponent component() {
        if (panel != null) return panel;
        panel = new JPanel(new GridBagLayout());
        panel.setOpaque(false);
        panel.setBorder(BorderFactory.createEmptyBorder(16, 20, 16, 20));
        int y = 0;
        boolean first = true;
        for (Item item : spec.items) {
            switch (item) {
                case SectionItem section -> {
                    var c = constraints(0, y++, 3);
                    c.fill = GridBagConstraints.HORIZONTAL;
                    c.insets = new Insets(first ? 0 : 14, 0, 6, 0);
                    panel.add(new TitledSeparator(section.title()), c);
                }
                case RowItem row -> y = addRow(row, y);
                case ActionItem action -> y = addAction(action, y);
            }
            first = false;
        }
        var filler = constraints(0, y, 3);
        filler.weighty = 1;
        filler.fill = GridBagConstraints.BOTH;
        panel.add(new JPanel() {{ setOpaque(false); }}, filler);
        listening = context.draft().onChanged(this::showValues);
        showValues();
        refresh();
        return panel;
    }

    @SuppressWarnings("unchecked")
    private <T> int addRow(RowItem item, int y) {
        var setting = (Setting<T>) item.setting();
        var factory = (Editors.Factory<T>) item.editor();
        ValueEditor<T> editor = factory.build().apply(setting, context);
        var row = new Row<>(setting, editor, item.note(), factory.terms());
        rows.add(row);
        if (editor.labelled()) {
            var c = constraints(0, y, 2);
            c.insets = new Insets(4, 0, 2, 0);
            panel.add(row.box, c);
        } else {
            var label = constraints(0, y, 1);
            label.insets = new Insets(4, 0, 2, 12);
            panel.add(row.box, label);
            var control = constraints(1, y, 1);
            control.insets = new Insets(4, 0, 2, 0);
            panel.add(editor.component(), control);
        }
        var reset = constraints(2, y, 1);
        reset.weightx = 1;
        reset.insets = new Insets(4, 4, 2, 0);
        panel.add(row.reset, reset);
        int column = editor.labelled() ? 0 : 1;
        var comment = constraints(column, y + 1, 3 - column);
        comment.insets = new Insets(0, editor.labelled() ? 24 : 0, 2, 0);
        panel.add(row.comment, comment);
        var error = constraints(column, y + 2, 3 - column);
        error.insets = new Insets(0, editor.labelled() ? 24 : 0, 4, 0);
        panel.add(row.error, error);
        editor.onEdit(() -> edited(row));
        row.reset.addActionListener(event -> {
            row.invalid = false;
            row.error.setVisible(false);
            context.draft().stage(setting, Optional.empty());
            context.changed().run();
        });
        return y + 3;
    }

    private int addAction(ActionItem action, int y) {
        var text = new JLabel();
        text.putClientProperty("html.disable", true);
        var c = constraints(0, y, 1);
        c.insets = new Insets(6, 0, 6, 12);
        panel.add(text, c);
        var buttons = new JPanel(new FlowLayout(FlowLayout.LEADING, 6, 0));
        buttons.setOpaque(false);
        var made = new ArrayList<JButton>();
        for (Button button : action.buttons()) {
            var control = new JButton(button.label());
            control.addActionListener(event -> button.action().run());
            buttons.add(control);
            made.add(control);
            actionButtons.add(control);
        }
        var b = constraints(1, y, 2);
        b.insets = new Insets(6, 0, 6, 0);
        panel.add(buttons, b);
        actionRefreshers.add(() -> {
            text.setText(action.text().get());
            for (int i = 0; i < made.size(); i++) made.get(i).setEnabled(action.buttons().get(i).enabled().getAsBoolean());
        });
        return y + 1;
    }

    private static GridBagConstraints constraints(int x, int y, int width) {
        var c = new GridBagConstraints();
        c.gridx = x; c.gridy = y; c.gridwidth = width;
        c.anchor = GridBagConstraints.WEST;
        return c;
    }

    private static JLabel label(String text) {
        var label = new JLabel(text);
        label.putClientProperty("html.disable", true);
        return label;
    }

    private <T> void edited(Row<T> row) {
        T value;
        try {
            value = row.editor.value();
        } catch (RuntimeException unreadable) {
            invalid(row, "Not a valid value.");
            return;
        }
        if (row.editor.removesAtDefault() && Objects.equals(value, row.setting.defaultValue())) {
            valid(row);
            context.draft().stage(row.setting, Optional.empty());
            return;
        }
        Optional<String> problem = row.setting.kind().check(value);
        if (problem.isPresent()) { invalid(row, problem.get()); return; }
        valid(row);
        context.draft().stage(row.setting, Optional.of(value));
    }

    private void invalid(Row<?> row, String message) {
        row.invalid = true;
        row.error.setText(message);
        row.error.setVisible(true);
        context.changed().run();
    }

    private void valid(Row<?> row) {
        boolean was = row.invalid;
        row.invalid = false;
        row.error.setVisible(false);
        if (was) context.changed().run();
    }

    private void showValues() { for (Row<?> row : rows) showValue(row); }

    private <T> void showValue(Row<T> row) {
        T value = context.draft().value(row.setting);
        if (!row.invalid) {
            T current;
            try { current = row.editor.value(); } catch (RuntimeException unreadable) { current = null; }
            if (!Objects.equals(current, value)) row.editor.show(value);
        }
        row.reset.setVisible(!Objects.equals(value, row.setting.defaultValue()));
    }

    @Override public boolean modified() {
        return spec.settings().stream().anyMatch(context.draft()::modified);
    }

    @Override public boolean valid() { return rows.stream().noneMatch(row -> row.invalid); }

    @Override public void reset() {
        for (Row<?> row : rows) { row.invalid = false; row.error.setVisible(false); }
        context.draft().reset(spec.settings());
        showValues();
    }

    @Override public CompletableFuture<Void> apply() { return CompletableFuture.completedFuture(null); }

    @Override public void highlight(String query) {
        JComponent first = null;
        for (Row<?> row : rows) {
            boolean match = SearchText.matches(row.searchText, query);
            row.box.setHighlighted(match);
            if (match && first == null) first = row.box;
        }
        if (first != null) first.scrollRectToVisible(new java.awt.Rectangle(0, 0, first.getWidth(), first.getHeight()));
    }

    @Override public void refresh() {
        actionRefreshers.forEach(Runnable::run);
        if (panel != null) showValues();
    }

    @Override public void dispose() { if (listening != null) listening.close(); }
}
```

`GroupPage.java`:

```java
package dev.jasper.app.settingsui;

import com.formdev.flatlaf.FlatClientProperties;
import java.awt.Font;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.UIManager;

/** A category page, as IntelliJ shows one: its title and a link to each child page. */
public final class GroupPage implements SettingsPage {
    private final String id;
    private final String title;
    private final Optional<String> parent;
    private final boolean hiddenWhenChildless;
    private final Supplier<List<SettingsPage>> children;
    private final PageContext context;
    private JPanel panel;

    public GroupPage(String id, String title, Optional<String> parent, boolean hiddenWhenChildless,
                     Supplier<List<SettingsPage>> children, PageContext context) {
        this.id = id; this.title = title; this.parent = parent; this.hiddenWhenChildless = hiddenWhenChildless;
        this.children = children; this.context = context;
    }

    @Override public String id() { return id; }
    @Override public String title() { return title; }
    @Override public Optional<String> parent() { return parent; }
    @Override public List<String> searchTerms() { return List.of(title); }
    @Override public boolean hiddenWhenChildless() { return hiddenWhenChildless; }

    @Override public JComponent component() {
        if (panel != null) return panel;
        panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setOpaque(false);
        panel.setBorder(BorderFactory.createEmptyBorder(16, 20, 16, 20));
        var heading = new JLabel(title);
        heading.putClientProperty("html.disable", true);
        heading.setFont(heading.getFont().deriveFont(Font.BOLD));
        panel.add(heading);
        for (SettingsPage child : children.get()) {
            var link = new JButton(child.title());
            link.putClientProperty("html.disable", true);
            link.putClientProperty(FlatClientProperties.BUTTON_TYPE, FlatClientProperties.BUTTON_TYPE_BORDERLESS);
            var color = UIManager.getColor("Component.linkColor");
            if (color != null) link.setForeground(color);
            link.setAlignmentX(0);
            link.addActionListener(event -> context.navigate().accept(child.id()));
            panel.add(link);
        }
        panel.add(Box.createVerticalGlue());
        return panel;
    }

    @Override public boolean modified() { return false; }
    @Override public void reset() { }
    @Override public CompletableFuture<Void> apply() { return CompletableFuture.completedFuture(null); }
    @Override public void highlight(String query) { }
    @Override public void dispose() { }
}
```

The test reads the first link as `getComponent(1)` (index 0 is the heading).

`LauncherPage.java`:

```java
package dev.jasper.app.settingsui;

import java.awt.FlowLayout;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.JTextArea;

/** A page that describes a feature kept in its own window and opens it (Keymap and Plugins until plan B2). */
public final class LauncherPage implements SettingsPage {
    private final String id;
    private final String title;
    private final String description;
    private final List<String> terms;
    private final JButton button;
    private JPanel panel;

    public LauncherPage(String id, String title, String description, String button, Runnable open, List<String> terms, PageContext context) {
        this.id = id; this.title = title; this.description = description;
        var all = new ArrayList<String>();
        all.add(title);
        all.addAll(terms);
        this.terms = List.copyOf(all);
        this.button = new JButton(button);
        this.button.addActionListener(event -> context.closeThen().accept(open));
    }

    JButton button() { return button; }

    @Override public String id() { return id; }
    @Override public String title() { return title; }
    @Override public Optional<String> parent() { return Optional.empty(); }
    @Override public List<String> searchTerms() { return terms; }

    @Override public JComponent component() {
        if (panel != null) return panel;
        panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setOpaque(false);
        panel.setBorder(BorderFactory.createEmptyBorder(16, 20, 16, 20));
        var text = new JTextArea(description);
        text.setEditable(false); text.setLineWrap(true); text.setWrapStyleWord(true); text.setOpaque(false);
        text.setAlignmentX(0);
        panel.add(text);
        var row = new JPanel(new FlowLayout(FlowLayout.LEADING, 0, 12));
        row.setOpaque(false);
        row.setAlignmentX(0);
        row.add(button);
        panel.add(row);
        return panel;
    }

    @Override public boolean modified() { return false; }
    @Override public void reset() { }
    @Override public CompletableFuture<Void> apply() { return CompletableFuture.completedFuture(null); }
    @Override public void highlight(String query) { }
    @Override public void dispose() { }
}
```

- [ ] **Step 4: Run the test to verify it passes**

Run: `./gradlew :jasper-app:test --tests "dev.jasper.app.settingsui.*"`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git branch --show-current
git add jasper-app/src
git commit -m "Add schema-generated, group and launcher Settings pages

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 6: The core page tree

**Files:**
- Create: `jasper-app/src/main/java/dev/jasper/app/settingsui/CorePages.java`
- Test: `jasper-app/src/test/java/dev/jasper/app/settingsui/CorePagesTest.java`

**Interfaces:**
- Consumes: `SchemaPage.Spec`, `GroupPage`, `LauncherPage`, `Editors`, `CoreSettings`.
- Produces: `CorePages.SystemActions(Supplier<Path> file, Runnable openInEditor, Runnable reveal, Runnable reload, Runnable migrate, BooleanSupplier migratable)`; `CorePages.specs(Supplier<List<String>> themeNames, SystemActions system)` returning `List<SchemaPage.Spec>`; `CorePages.pages(Supplier<List<String>> themeNames, SystemActions system, Runnable openKeymap, Runnable openPlugins)` returning `Function<PageContext, List<SettingsPage>>`; `CorePages.DEFAULT_PAGE = "appearance"`.

- [ ] **Step 1: Write the failing test**

```java
package dev.jasper.app.settingsui;

import static org.assertj.core.api.Assertions.*;

import dev.jasper.app.config.ConfigLoader;
import dev.jasper.app.config.CoreSettings;
import dev.jasper.app.settings.Setting;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class CorePagesTest {
    private final List<String> calls = new ArrayList<>();
    private final CorePages.SystemActions system = new CorePages.SystemActions(() -> Path.of("/home/me/.config/jasper/config.toml"),
        () -> calls.add("open"), () -> calls.add("reveal"), () -> calls.add("reload"), () -> calls.add("migrate"), () -> true);
    private final PageContext context = new PageContext(new SettingsDraft(ConfigLoader.parse(Path.of("config.toml"), "", true).values()),
        id -> { }, Runnable::run, (title, initial) -> Optional.empty(), () -> { });

    @Test void everyCoreSettingIsOnExactlyOnePage() {
        var shown = new ArrayList<Setting<?>>();
        for (var spec : CorePages.specs(() -> List.of("Jasper Dark"), system)) shown.addAll(spec.settings());
        assertThat(shown).doesNotHaveDuplicates().containsExactlyInAnyOrderElementsOf(CoreSettings.SCHEMA.settings());
    }

    @Test void theTreeIsTheApprovedOne() {
        var pages = CorePages.pages(() -> List.of("Jasper Dark"), system, () -> calls.add("keymap"), () -> calls.add("plugins")).apply(context);
        assertThat(pages).extracting(SettingsPage::id).containsExactly("appearance-behavior", "appearance", "window", "notifications",
            "command-palette", "desk-buddy", "system", "keymap", "terminal", "terminal.font", "terminal.cursor", "terminal.shell",
            "plugins", "tools");
        assertThat(pages).extracting(page -> page.parent().orElse("")).containsExactly("", "appearance-behavior", "appearance-behavior",
            "appearance-behavior", "appearance-behavior", "appearance-behavior", "appearance-behavior", "", "", "terminal", "terminal",
            "terminal", "", "");
        assertThat(pages).extracting(SettingsPage::title).contains("Appearance & Behavior", "System Settings", "Font & Colors", "Tools");
        assertThat(pages.getLast().hiddenWhenChildless()).isTrue();
        assertThat(CorePages.DEFAULT_PAGE).isEqualTo("appearance");
    }

    @Test void systemSettingsOffersTheConfigurationFileActions() {
        var pages = CorePages.pages(() -> List.of("Jasper Dark"), system, () -> { }, () -> { }).apply(context);
        var systemPage = (SchemaPage) pages.stream().filter(page -> page.id().equals("system")).findFirst().orElseThrow();
        systemPage.component();
        assertThat(systemPage.actionButtons()).extracting(javax.swing.JButton::getText)
            .containsExactly("Open in Editor", "Reveal", "Reload from Disk", "Migrate Config…");
        systemPage.actionButtons().forEach(javax.swing.JButton::doClick);
        assertThat(calls).containsExactly("open", "reveal", "reload", "migrate");
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `./gradlew :jasper-app:test --tests "dev.jasper.app.settingsui.CorePagesTest"`
Expected: FAIL: `CorePages` does not exist.

- [ ] **Step 3: Write the implementation**

```java
package dev.jasper.app.settingsui;

import static dev.jasper.app.config.CoreSettings.*;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.BooleanSupplier;
import java.util.function.Function;
import java.util.function.Supplier;

/** The Settings tree for the core settings, as the settings dialog spec lays it out. */
public final class CorePages {
    /** The page shown when Settings opens for the first time. */
    public static final String DEFAULT_PAGE = "appearance";

    private CorePages() { }

    /**
     * What the System Settings page's buttons do.
     *
     * @param file the configuration file's path
     * @param openInEditor opens it in the user's editor
     * @param reveal shows it in the file manager
     * @param reload reads it again
     * @param migrate runs Migrate Config
     * @param migratable whether Migrate Config has work
     */
    public record SystemActions(Supplier<Path> file, Runnable openInEditor, Runnable reveal, Runnable reload, Runnable migrate,
                                BooleanSupplier migratable) { }

    /** Every schema page's spec, in tree order. */
    public static List<SchemaPage.Spec> specs(Supplier<List<String>> themeNames, SystemActions system) {
        return List.of(
            SchemaPage.page("appearance", "Appearance").parent("appearance-behavior").anchor("theme")
                .section("Theme")
                .row(UI_THEME_NAME, Editors.theme(themeNames))
                .section("Interface font")
                .row(UI_FONT_FAMILY, Editors.font(false))
                .row(UI_FONT_SIZE, Editors.optionalNumber("Use platform size", 0.5, "pt")),
            SchemaPage.page("window", "Window").parent("appearance-behavior")
                .section("Toolbar and status bar")
                .row(WINDOW_TOOLBAR)
                .row(WINDOW_TOOLBAR_HEIGHT, Editors.integer("px"))
                .row(WINDOW_STATUS_BAR)
                .row(WINDOW_STATUS_BAR_HEIGHT, Editors.integer("px"))
                .section("Tabs and panels")
                .row(WINDOW_TAB_HEIGHT, Editors.integer("px"))
                .row(WINDOW_RAIL_WIDTH, Editors.integer("px"))
                .section("New windows")
                .row(WINDOW_COLUMNS, Editors.integer("columns"))
                .row(WINDOW_LINES, Editors.integer("lines")),
            SchemaPage.page("notifications", "Notifications").parent("appearance-behavior").anchor("finished-command-notifications")
                .row(NOTIFICATIONS_LONG_COMMAND_SECONDS, Editors.integer("s")).note("0 turns these notifications off."),
            SchemaPage.page("command-palette", "Command Palette").parent("appearance-behavior").anchor("palette")
                .row(PALETTE_MAX_RESULTS, Editors.integer("")),
            SchemaPage.page("desk-buddy", "Desk Buddy").parent("appearance-behavior").anchor("desk-buddy")
                .row(BUDDY_ENABLED),
            SchemaPage.page("system", "System Settings").parent("appearance-behavior").anchor("background-residency")
                .row(SYSTEM_RUN_IN_BACKGROUND)
                .section("Configuration file")
                .action(() -> system.file().get().toString(),
                    new SchemaPage.Button("Open in Editor", system.openInEditor(), () -> true),
                    new SchemaPage.Button("Reveal", system.reveal(), () -> true))
                .action(() -> "Read the file again after editing it elsewhere.",
                    new SchemaPage.Button("Reload from Disk", system.reload(), () -> true))
                .action(() -> "Rewrite old setting names to the current layout; the original is backed up first.",
                    new SchemaPage.Button("Migrate Config…", system.migrate(), system.migratable())),
            SchemaPage.page("terminal", "Terminal")
                .row(TERMINAL_SCROLLBACK, Editors.integer("lines"))
                .row(TERMINAL_BELL)
                .row(TERMINAL_ON_EXIT, Editors.choice(Map.of("keep_open", "Keep the pane open",
                    "close_on_success", "Close when it succeeds", "close", "Close")))
                .row(TERMINAL_COPY_ON_SELECT)
                .row(TERMINAL_OPTION_AS_META, Editors.choice(Map.of("left", "Left Option", "right", "Right Option",
                    "both", "Both", "none", "Neither")))
                .row(TERMINAL_DIM_INACTIVE_PANES, Editors.number(0.05, "")),
            SchemaPage.page("terminal.font", "Font & Colors").parent("terminal").anchor("terminal-colors")
                .section("Font")
                .row(TERMINAL_FONT_FAMILY, Editors.font(true))
                .row(TERMINAL_FONT_SIZE, Editors.number(0.5, "pt"))
                .row(TERMINAL_FONT_FALLBACK, Editors.fontList(false))
                .row(TERMINAL_FONT_LIGATURES)
                .row(TERMINAL_FONT_LINE_HEIGHT, Editors.number(0.05, "×"))
                .section("Colors")
                .row(TERMINAL_COLORS, Editors.choice(Map.of("match", "Match the theme", "light", "Light", "dark", "Dark"))),
            SchemaPage.page("terminal.cursor", "Cursor").parent("terminal")
                .row(TERMINAL_CURSOR_SHAPE)
                .row(TERMINAL_CURSOR_BLINK),
            SchemaPage.page("terminal.shell", "Shell").parent("terminal").anchor("shell-environment-and-history")
                .row(TERMINAL_SHELL_PROGRAM, Editors.executable()).note("Leave empty for your login shell.")
                .row(TERMINAL_SHELL_ARGS, Editors.textList())
                .row(TERMINAL_ENV, Editors.table())
                .row(TERMINAL_SHELL_INTEGRATION, Editors.choice(Map.of("auto", "Automatic", "manual", "Manual", "off", "Off"))));
    }

    /** The whole tree for one dialog: group, schema and launcher pages in display order. */
    public static Function<PageContext, List<SettingsPage>> pages(Supplier<List<String>> themeNames, SystemActions system,
                                                                   Runnable openKeymap, Runnable openPlugins) {
        return context -> {
            var specs = specs(themeNames, system);
            var all = new ArrayList<SettingsPage>();
            Function<String, Supplier<List<SettingsPage>>> childrenOf = id ->
                () -> all.stream().filter(page -> page.parent().equals(Optional.of(id))).toList();
            all.add(new GroupPage("appearance-behavior", "Appearance & Behavior", Optional.empty(), false,
                childrenOf.apply("appearance-behavior"), context));
            for (var spec : specs.subList(0, 6)) all.add(spec.build(context));
            all.add(new LauncherPage("keymap", "Keymap", "Keyboard shortcuts for every action, including actions plugins add.",
                "Open Keyboard Shortcuts…", openKeymap, List.of("keyboard", "shortcuts", "keys", "bindings"), context));
            for (var spec : specs.subList(6, specs.size())) all.add(spec.build(context));
            all.add(new LauncherPage("plugins", "Plugins", "Install, enable, disable and remove plugins. Changes apply after a restart.",
                "Manage Plugins…", openPlugins, List.of("extensions", "install", "enable", "disable"), context));
            all.add(new GroupPage("tools", "Tools", Optional.empty(), true, childrenOf.apply("tools"), context));
            return List.copyOf(all);
        };
    }
}
```

- [ ] **Step 4: Run the test to verify it passes**

Run: `./gradlew :jasper-app:test --tests "dev.jasper.app.settingsui.*"`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git branch --show-current
git add jasper-app/src
git commit -m "Lay out the core Settings pages

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 7: The dialog: tree, view and controller

**Files:**
- Create: `jasper-app/src/main/java/dev/jasper/app/settingsui/PageTree.java`
- Create: `jasper-app/src/main/java/dev/jasper/app/settingsui/SettingsView.java`
- Create: `jasper-app/src/main/java/dev/jasper/app/settingsui/SettingsDialog.java`
- Test: `jasper-app/src/test/java/dev/jasper/app/settingsui/SettingsDialogTest.java`

**Interfaces:**
- Consumes: everything from Tasks 1–6; `AuxiliaryWindows.modalDialog`; `ConfigService.State`.
- Produces: `SettingsDialog(AuxiliaryWindows windows, Function<PageContext, List<SettingsPage>> pages, SettingsDialog.Hooks hooks)`; `SettingsDialog.Hooks(Function<SettingsEdit, CompletableFuture<ConfigService.State>> save, Supplier<ConfigService.State> state, Function<Consumer<ConfigService.State>, Subscription> onState, Runnable openConfigFile, Runnable migrate, Consumer<URI> browse, BiPredicate<Component, Integer> confirmDiscard)`; `open(UUID ownerOrNull, String pageOrNull)`; `isOpen()`; static `askDiscard(Component, Integer)`; `HELP_URL`; package-private `view()`, `select(String)`, `search(String)`, `back()`, `forward()`, `apply()`, `ok()`, `cancel()`, `currentPage()`, `pages()`.

- [ ] **Step 1: Write the failing test**

```java
package dev.jasper.app.settingsui;

import static dev.jasper.app.workspace.DesktopTestSupport.edt;
import static dev.jasper.app.workspace.DesktopTestSupport.until;
import static org.assertj.core.api.Assertions.*;

import dev.jasper.app.config.ConfigLoader;
import dev.jasper.app.config.ConfigService;
import dev.jasper.app.config.CoreSettings;
import dev.jasper.app.config.SettingsEdit;
import dev.jasper.app.lifecycle.Subscription;
import dev.jasper.app.persistence.UiState;
import dev.jasper.app.windows.AuxiliarySurface;
import dev.jasper.app.windows.AuxiliaryWindows;
import java.awt.Rectangle;
import java.net.URI;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;

class SettingsDialogTest {
    private static final Path FILE = Path.of("/fixture/config.toml");
    private final AuxiliaryWindows windows = new AuxiliaryWindows(UiState.inMemory(), surface -> new AuxiliarySurface.Shell(
        () -> { }, () -> { }, () -> { }, title -> { }, () -> new Rectangle(0, 0, 1000, 700), (title, initial) -> Optional.empty()));
    private final List<SettingsEdit> saved = new CopyOnWriteArrayList<>();
    private final List<URI> browsed = new ArrayList<>();
    private final List<Consumer<ConfigService.State>> listeners = new ArrayList<>();
    private final boolean[] discard = {true};
    private final int[] asked = {0};
    private CompletableFuture<ConfigService.State> saveResult;
    private ConfigService.State state = state("[window]\ntab_height = 44\n");

    private static ConfigService.State state(String text) {
        var parsed = ConfigLoader.parse(FILE, text, true);
        return new ConfigService.State(parsed.snapshot(), parsed.diagnostics(), FILE, true, parsed.legacyKeys(), parsed.deadTables(),
            parsed.values(), parsed.rejected());
    }

    private final List<String> opened = new ArrayList<>();
    private final FakePage fake = new FakePage("keymap", "Keymap", List.of("shortcuts", "copy"));

    private SettingsDialog dialog() {
        var hooks = new SettingsDialog.Hooks(edit -> {
                saved.add(edit);
                return saveResult != null ? saveResult : CompletableFuture.completedFuture(state("[window]\ntab_height = 50\n"));
            }, () -> state, listener -> { listeners.add(listener); return new Subscription(() -> listeners.remove(listener)); },
            () -> opened.add("config"), () -> opened.add("migrate"), browsed::add,
            (parent, count) -> { asked[0] = count; return discard[0]; });
        return new SettingsDialog(windows, context -> {
            var pages = new ArrayList<>(CorePages.pages(() -> List.of("Jasper Dark"), new CorePages.SystemActions(() -> FILE,
                () -> { }, () -> { }, () -> { }, () -> { }, () -> false), () -> { }, () -> { }).apply(context));
            pages.set(pages.indexOf(pages.stream().filter(page -> page.id().equals("keymap")).findFirst().orElseThrow()), fake);
            return pages;
        }, hooks);
    }

    @Test void openingShowsTheTreeHidesEmptyToolsAndSelectsTheDefaultPage() throws Exception {
        var dialog = dialog();
        edt(() -> {
            dialog.open(null, null);
            assertThat(dialog.isOpen()).isTrue();
            assertThat(dialog.view().tree.visibleIds()).contains("appearance-behavior", "appearance", "terminal.shell", "plugins")
                .doesNotContain("tools");
            assertThat(dialog.currentPage()).isEqualTo("appearance");
            assertThat(dialog.view().breadcrumb.getText()).isEqualTo("Appearance & Behavior › Appearance");
            assertThat(dialog.view().apply.isEnabled()).isFalse();
        });
    }

    @Test void backAndForwardWalkThePagesVisited() throws Exception {
        var dialog = dialog();
        edt(() -> {
            dialog.open(null, "window");
            dialog.select("terminal.font");
            dialog.select("terminal.cursor");
            dialog.back();
            assertThat(dialog.currentPage()).isEqualTo("terminal.font");
            dialog.back();
            assertThat(dialog.currentPage()).isEqualTo("window");
            assertThat(dialog.view().back.isEnabled()).isFalse();
            dialog.forward();
            assertThat(dialog.currentPage()).isEqualTo("terminal.font");
            dialog.select("desk-buddy");
            assertThat(dialog.view().forward.isEnabled()).isFalse();
        });
    }

    @Test void searchFiltersTheTreeCountsMatchesAndRestoresOnClear() throws Exception {
        var dialog = dialog();
        edt(() -> {
            dialog.open(null, "desk-buddy");
            dialog.search("ligatures");
            assertThat(dialog.view().tree.visibleIds()).containsExactly("terminal", "terminal.font");
            assertThat(dialog.view().tree.count("terminal.font")).isPositive();
            assertThat(dialog.currentPage()).isEqualTo("terminal.font");
            dialog.search("shortcuts");
            assertThat(dialog.view().tree.visibleIds()).containsExactly("keymap");
            assertThat(fake.highlights).contains("shortcuts");
            dialog.search("");
            assertThat(dialog.view().tree.visibleIds()).contains("appearance", "terminal.shell");
            assertThat(dialog.currentPage()).isEqualTo("keymap");
        });
    }

    @Test void applySavesTheDraftThenOtherPagesAndOkCloses() throws Exception {
        var dialog = dialog();
        edt(() -> {
            dialog.open(null, "window");
            dialog.draft().stage(CoreSettings.WINDOW_TAB_HEIGHT, Optional.of(50));
            fake.modified = true;
            assertThat(dialog.view().apply.isEnabled()).isTrue();
            assertThat(dialog.view().tree.isModified("window")).isTrue();
            dialog.apply();
        });
        until(() -> fake.applied == 1);
        edt(() -> {
            assertThat(saved).singleElement().satisfies(edit -> assertThat(edit.changes()).containsKey(CoreSettings.WINDOW_TAB_HEIGHT));
            assertThat(dialog.draft().modified()).isFalse();
            fake.modified = false;
            dialog.refreshNow();
            assertThat(dialog.view().apply.isEnabled()).isFalse();
            dialog.draft().stage(CoreSettings.WINDOW_TAB_HEIGHT, Optional.of(60));
            dialog.ok();
        });
        until(() -> !dialog.isOpen());
    }

    @Test void aFailedSaveKeepsTheEditsAndSaysWhy() throws Exception {
        var dialog = dialog();
        saveResult = CompletableFuture.failedFuture(new IllegalArgumentException("window.tab_height changed on disk. Review the new value and try again."));
        edt(() -> {
            dialog.open(null, "window");
            dialog.draft().stage(CoreSettings.WINDOW_TAB_HEIGHT, Optional.of(50));
            dialog.ok();
        });
        until(() -> !dialog.view().message.getText().isEmpty());
        edt(() -> {
            assertThat(dialog.view().message.getText()).contains("changed on disk");
            assertThat(dialog.isOpen()).isTrue();
            assertThat(dialog.draft().modified()).isTrue();
        });
    }

    @Test void cancellingWithChangesAsksFirst() throws Exception {
        var dialog = dialog();
        edt(() -> {
            dialog.open(null, "window");
            dialog.draft().stage(CoreSettings.WINDOW_TAB_HEIGHT, Optional.of(50));
            discard[0] = false;
            dialog.cancel();
            assertThat(asked[0]).isEqualTo(1);
            assertThat(dialog.isOpen()).isTrue();
            discard[0] = true;
            dialog.cancel();
            assertThat(dialog.isOpen()).isFalse();
        });
    }

    @Test void bannersFollowTheStateAndARejectedFileBlocksApply() throws Exception {
        state = state("[font]\nsize = 18\n");
        var dialog = dialog();
        edt(() -> {
            dialog.open(null, "window");
            assertThat(dialog.view().banner.isVisible()).isTrue();
            assertThat(dialog.view().bannerText.getText()).isEqualTo("config.toml uses old setting names.");
            dialog.view().bannerButton.doClick();
            assertThat(opened).containsExactly("migrate");
            var broken = state("[window\n");
            listeners.forEach(listener -> listener.accept(new ConfigService.State(broken.snapshot(), broken.diagnostics(), FILE, true,
                List.of(), List.of(), state.values(), true)));
            assertThat(dialog.view().bannerText.getText()).isEqualTo("config.toml has errors, so changes can't be saved until they're fixed.");
            dialog.draft().stage(CoreSettings.WINDOW_TAB_HEIGHT, Optional.of(50));
            assertThat(dialog.view().apply.isEnabled()).isFalse();
            assertThat(dialog.view().ok.isEnabled()).isFalse();
        });
    }

    @Test void changesOnDiskReachRowsWithoutPendingEdits() throws Exception {
        var dialog = dialog();
        edt(() -> {
            dialog.open(null, "window");
            dialog.draft().stage(CoreSettings.WINDOW_STATUS_BAR, Optional.of(false));
            listeners.forEach(listener -> listener.accept(state("[window]\ntab_height = 60\n")));
            assertThat(dialog.draft().value(CoreSettings.WINDOW_TAB_HEIGHT)).isEqualTo(60);
            assertThat(dialog.draft().value(CoreSettings.WINDOW_STATUS_BAR)).isFalse();
        });
    }

    @Test void helpOpensThePagesSectionOfTheGuide() throws Exception {
        var dialog = dialog();
        edt(() -> {
            dialog.open(null, "terminal.font");
            dialog.view().help.doClick();
        });
        assertThat(browsed).containsExactly(URI.create(SettingsDialog.HELP_URL + "terminal-colors"));
    }

    @Test void openingAgainSelectsTheRequestedPage() throws Exception {
        var dialog = dialog();
        edt(() -> {
            dialog.open(null, "window");
            dialog.open(null, "keymap");
            assertThat(dialog.currentPage()).isEqualTo("keymap");
        });
    }

    /** A page standing in for Keymap: settable modification, counted applies and recorded highlights. */
    static final class FakePage implements SettingsPage {
        final String id;
        final String title;
        final List<String> terms;
        final List<String> highlights = new ArrayList<>();
        boolean modified;
        int applied;
        FakePage(String id, String title, List<String> terms) { this.id = id; this.title = title; this.terms = terms; }
        @Override public String id() { return id; }
        @Override public String title() { return title; }
        @Override public Optional<String> parent() { return Optional.empty(); }
        @Override public List<String> searchTerms() { return terms; }
        @Override public javax.swing.JComponent component() { return new javax.swing.JPanel(); }
        @Override public boolean modified() { return modified; }
        @Override public void reset() { modified = false; }
        @Override public CompletableFuture<Void> apply() { applied++; return CompletableFuture.completedFuture(null); }
        @Override public void highlight(String query) { highlights.add(query); }
        @Override public void dispose() { }
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `./gradlew :jasper-app:test --tests "dev.jasper.app.settingsui.SettingsDialogTest"`
Expected: FAIL: `SettingsDialog`, `SettingsView`, `PageTree` do not exist.

- [ ] **Step 3: Write the implementation**

`PageTree.java`:

```java
package dev.jasper.app.settingsui;

import com.formdev.flatlaf.FlatClientProperties;
import com.formdev.flatlaf.util.UIScale;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Predicate;
import javax.swing.BorderFactory;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTree;
import javax.swing.UIManager;
import javax.swing.event.TreeExpansionEvent;
import javax.swing.event.TreeExpansionListener;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreeCellRenderer;
import javax.swing.tree.TreePath;
import javax.swing.tree.TreeSelectionModel;

/** The Settings category tree: bold top-level pages, chevrons, blue modified pages and search counts. EDT only. */
final class PageTree extends JTree {
    private final DefaultMutableTreeNode root = new DefaultMutableTreeNode();
    private final DefaultTreeModel model = new DefaultTreeModel(root);
    private final Set<String> expanded = new HashSet<>();
    private final Map<String, DefaultMutableTreeNode> nodes = new HashMap<>();
    private Map<String, Integer> counts = Map.of();
    private Predicate<String> modified = id -> false;
    private boolean rebuilding;
    private boolean searching;

    PageTree(Consumer<String> selected) {
        setModel(model);
        setRootVisible(false);
        setShowsRootHandles(true);
        setToggleClickCount(1);
        setCellRenderer(new Row());
        setBorder(BorderFactory.createEmptyBorder(UIScale.scale(4), UIScale.scale(4), UIScale.scale(4), UIScale.scale(4)));
        putClientProperty(FlatClientProperties.STYLE, "icon.arrowType: chevron");
        putClientProperty(FlatClientProperties.TREE_WIDE_SELECTION, true);
        putClientProperty(FlatClientProperties.TREE_WIDE_CELL_RENDERER, true);
        getSelectionModel().setSelectionMode(TreeSelectionModel.SINGLE_TREE_SELECTION);
        getAccessibleContext().setAccessibleName("Settings categories");
        addTreeSelectionListener(event -> {
            if (!rebuilding) selectedId().ifPresent(selected);
        });
        addTreeExpansionListener(new TreeExpansionListener() {
            @Override public void treeExpanded(TreeExpansionEvent event) { remember(event, true); }
            @Override public void treeCollapsed(TreeExpansionEvent event) { remember(event, false); }
            private void remember(TreeExpansionEvent event, boolean open) {
                if (rebuilding || searching) return;
                if (((DefaultMutableTreeNode) event.getPath().getLastPathComponent()).getUserObject() instanceof SettingsPage page) {
                    if (open) expanded.add(page.id()); else expanded.remove(page.id());
                }
            }
        });
    }

    @Override public void updateUI() {
        super.updateUI();
        if (getFont() != null) setRowHeight(getFontMetrics(getFont()).getHeight() + UIScale.scale(10));
    }

    /**
     * Shows the pages {@code visible} accepts, with their ancestors; a page that hides while childless never shows
     * without children. Searching expands everything; otherwise remembered expansion applies (groups start open).
     */
    void show(List<SettingsPage> pages, Predicate<SettingsPage> visible, Map<String, Integer> counts, boolean searching) {
        String selection = selectedId().orElse(null);
        this.counts = Map.copyOf(counts);
        this.searching = searching;
        rebuilding = true;
        try {
            if (expanded.isEmpty() && !searching)
                for (SettingsPage page : pages) if (page.parent().isEmpty()) expanded.add(page.id());
            root.removeAllChildren();
            nodes.clear();
            var keep = new HashSet<String>();
            for (SettingsPage page : pages) {
                boolean childless = pages.stream().noneMatch(other -> other.parent().equals(Optional.of(page.id())));
                if (page.hiddenWhenChildless() && childless) continue;
                if (!visible.test(page)) continue;
                for (String id = page.id(); id != null; id = find(pages, id).flatMap(SettingsPage::parent).orElse(null)) keep.add(id);
            }
            for (SettingsPage page : pages) {
                if (!keep.contains(page.id())) continue;
                var node = new DefaultMutableTreeNode(page);
                nodes.put(page.id(), node);
                page.parent().map(nodes::get).orElse(root).add(node);
            }
            model.reload();
            for (var node : nodes.values()) {
                var page = (SettingsPage) node.getUserObject();
                if (searching || expanded.contains(page.id())) expandPath(new TreePath(node.getPath()));
            }
            if (selection != null && nodes.containsKey(selection)) setSelectionPath(new TreePath(nodes.get(selection).getPath()));
        } finally { rebuilding = false; }
    }

    private static Optional<SettingsPage> find(List<SettingsPage> pages, String id) {
        return pages.stream().filter(page -> page.id().equals(id)).findFirst();
    }

    /** Selects {@code id} without reporting it back, expanding its ancestors. */
    void select(String id) {
        var node = nodes.get(id);
        if (node == null) return;
        rebuilding = true;
        try {
            var path = new TreePath(node.getPath());
            if (path.getParentPath() != null) expandPath(path.getParentPath());
            setSelectionPath(path);
            scrollPathToVisible(path);
        } finally { rebuilding = false; }
    }

    Optional<String> selectedId() {
        return getLastSelectedPathComponent() instanceof DefaultMutableTreeNode node && node.getUserObject() instanceof SettingsPage page
            ? Optional.of(page.id()) : Optional.empty();
    }

    void modifiedBy(Predicate<String> value) { modified = value; repaint(); }

    boolean isModified(String id) { return modified.test(id); }

    int count(String id) { return counts.getOrDefault(id, 0); }

    /** Page ids in display order; for tests. */
    List<String> visibleIds() {
        var ids = new ArrayList<String>();
        Enumeration<?> all = root.preorderEnumeration();
        while (all.hasMoreElements())
            if (((DefaultMutableTreeNode) all.nextElement()).getUserObject() instanceof SettingsPage page) ids.add(page.id());
        return ids;
    }

    /** Title at left (bold at the top level, blue when modified) and a search count at right. */
    private final class Row extends JPanel implements TreeCellRenderer {
        private final JLabel title = new JLabel();
        private final JLabel count = new JLabel();

        Row() {
            super(new BorderLayout(UIScale.scale(8), 0));
            setOpaque(false);
            title.putClientProperty("html.disable", true);
            count.putClientProperty("html.disable", true);
            add(title, BorderLayout.CENTER);
            add(count, BorderLayout.EAST);
            setBorder(BorderFactory.createEmptyBorder(0, UIScale.scale(2), 0, UIScale.scale(10)));
        }

        @Override public Component getTreeCellRendererComponent(JTree tree, Object value, boolean selected, boolean open,
                                                                boolean leaf, int row, boolean focused) {
            var page = (SettingsPage) ((DefaultMutableTreeNode) value).getUserObject();
            if (page == null) return this;
            title.setText(page.title());
            title.setFont(tree.getFont().deriveFont(page.parent().isEmpty() ? Font.BOLD : Font.PLAIN));
            Color foreground = UIManager.getColor(selected && tree.hasFocus() ? "Tree.selectionForeground" : "Label.foreground");
            if (modified.test(page.id()) && !(selected && tree.hasFocus())) {
                Color link = UIManager.getColor("Component.linkColor");
                if (link != null) foreground = link;
            }
            title.setForeground(foreground);
            int matches = counts.getOrDefault(page.id(), 0);
            count.setText(matches > 0 ? Integer.toString(matches) : "");
            count.setForeground(UIManager.getColor("Label.disabledForeground"));
            count.setFont(tree.getFont());
            getAccessibleContext().setAccessibleName(page.title() + (modified.test(page.id()) ? ", modified" : ""));
            return this;
        }

        @Override public void setBounds(int x, int y, int width, int height) {
            super.setBounds(x, y, width, height);
            doLayout(); // CellRendererPane does not validate compound tree renderers.
        }

        @Override public Dimension getPreferredSize() { return new Dimension(UIScale.scale(200), getRowHeight()); }
    }
}
```

`SettingsView.java`:

```java
package dev.jasper.app.settingsui;

import com.formdev.flatlaf.FlatClientProperties;
import dev.jasper.app.platform.AppIcons;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.FlowLayout;
import java.awt.event.KeyEvent;
import javax.swing.*;

/** The Settings dialog's panel, laid out as IntelliJ's: search and tree at left; header, banner and page at right; buttons below. */
final class SettingsView extends JPanel {
    final JTextField search = new JTextField();
    final PageTree tree;
    final JLabel breadcrumb = new JLabel();
    final JButton back = iconButton("general/chevronLeft", "Back");
    final JButton forward = iconButton("general/chevronRight", "Forward");
    final JPanel banner = new JPanel(new BorderLayout(8, 0));
    final JLabel bannerText = new JLabel();
    final JButton bannerButton = new JButton();
    final JScrollPane body = new JScrollPane();
    final JButton help = iconButton("general/questionDialog", "Help");
    final JLabel message = new JLabel();
    final JButton cancel = new JButton("Cancel");
    final JButton apply = new JButton("Apply");
    final JButton ok = new JButton("OK");

    SettingsView(java.util.function.Consumer<String> selected, Runnable cancelled) {
        super(new BorderLayout());
        tree = new PageTree(selected);
        search.putClientProperty(FlatClientProperties.PLACEHOLDER_TEXT, "Search settings");
        search.putClientProperty(FlatClientProperties.TEXT_FIELD_LEADING_ICON, AppIcons.bundled("general/search"));
        search.putClientProperty(FlatClientProperties.TEXT_FIELD_SHOW_CLEAR_BUTTON, true);
        search.getAccessibleContext().setAccessibleName("Search settings");

        var left = new JPanel(new BorderLayout(0, 6));
        left.setBorder(BorderFactory.createEmptyBorder(10, 10, 0, 6));
        left.add(search, BorderLayout.NORTH);
        var treeScroll = new JScrollPane(tree);
        treeScroll.setBorder(BorderFactory.createEmptyBorder());
        left.add(treeScroll, BorderLayout.CENTER);

        breadcrumb.putClientProperty("html.disable", true);
        breadcrumb.setFont(breadcrumb.getFont().deriveFont(java.awt.Font.BOLD));
        var arrows = new JPanel(new FlowLayout(FlowLayout.TRAILING, 2, 0));
        arrows.setOpaque(false);
        arrows.add(back);
        arrows.add(forward);
        var header = new JPanel(new BorderLayout());
        header.setBorder(BorderFactory.createEmptyBorder(12, 20, 8, 12));
        header.add(breadcrumb, BorderLayout.WEST);
        header.add(arrows, BorderLayout.EAST);

        bannerText.putClientProperty("html.disable", true);
        banner.setBorder(BorderFactory.createEmptyBorder(6, 20, 6, 12));
        Color bannerBackground = UIManager.getColor("Banner.warningBackground");
        if (bannerBackground != null) banner.setBackground(bannerBackground);
        banner.add(bannerText, BorderLayout.CENTER);
        banner.add(bannerButton, BorderLayout.EAST);
        banner.setVisible(false);

        body.setBorder(BorderFactory.createEmptyBorder());
        body.getVerticalScrollBar().setUnitIncrement(16);
        var page = new JPanel(new BorderLayout());
        page.add(banner, BorderLayout.NORTH);
        page.add(body, BorderLayout.CENTER);
        var right = new JPanel(new BorderLayout());
        right.add(header, BorderLayout.NORTH);
        right.add(page, BorderLayout.CENTER);

        var split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, left, right);
        split.setDividerLocation(260);
        split.setDividerSize(1);
        split.setBorder(BorderFactory.createEmptyBorder());
        add(split, BorderLayout.CENTER);

        message.putClientProperty("html.disable", true);
        Color red = UIManager.getColor("Jasper.configErrorForeground");
        message.setForeground(red != null ? red : Color.RED);
        var buttons = new JPanel(new FlowLayout(FlowLayout.TRAILING, 8, 0));
        buttons.setOpaque(false);
        buttons.add(cancel);
        buttons.add(apply);
        buttons.add(ok);
        var footer = new JPanel(new BorderLayout(8, 0));
        footer.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createMatteBorder(1, 0, 0, 0, UIManager.getColor("Separator.foreground")),
            BorderFactory.createEmptyBorder(10, 12, 10, 12)));
        footer.add(help, BorderLayout.WEST);
        footer.add(message, BorderLayout.CENTER);
        footer.add(buttons, BorderLayout.EAST);
        add(footer, BorderLayout.SOUTH);

        getInputMap(WHEN_IN_FOCUSED_WINDOW).put(KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), "cancelSettings");
        getActionMap().put("cancelSettings", new AbstractAction() {
            @Override public void actionPerformed(java.awt.event.ActionEvent event) { cancelled.run(); }
        });
    }

    @Override public void addNotify() {
        super.addNotify();
        var root = SwingUtilities.getRootPane(this);
        if (root != null) root.setDefaultButton(ok);
    }

    void banner(String text, String button, Runnable action) {
        for (var listener : bannerButton.getActionListeners()) bannerButton.removeActionListener(listener);
        if (text == null) { banner.setVisible(false); return; }
        bannerText.setText(text);
        bannerButton.setText(button);
        bannerButton.addActionListener(event -> action.run());
        banner.setVisible(true);
    }

    void page(JComponent content) {
        body.setViewportView(content);
        body.getViewport().setOpaque(false);
    }

    private static JButton iconButton(String icon, String name) {
        var button = new JButton(AppIcons.bundled(icon));
        button.putClientProperty(FlatClientProperties.BUTTON_TYPE, FlatClientProperties.BUTTON_TYPE_TOOLBAR_BUTTON);
        button.setToolTipText(name);
        button.getAccessibleContext().setAccessibleName(name);
        return button;
    }
}
```

`SettingsDialog.java`:

```java
package dev.jasper.app.settingsui;

import dev.jasper.app.config.ConfigService;
import dev.jasper.app.config.SettingsEdit;
import dev.jasper.app.lifecycle.Subscription;
import dev.jasper.app.windows.AuxiliarySurface;
import dev.jasper.app.windows.AuxiliaryWindows;
import java.awt.Component;
import java.awt.Dimension;
import java.net.URI;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.function.BiPredicate;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;

/**
 * The Settings dialog: a modal, singleton surface with a page tree, search, back and forward, banners and
 * Apply/OK/Cancel. It owns the pages it creates for each opening and disposes them when it closes. EDT only.
 */
public final class SettingsDialog {
    /** The configuration guide; a page's help anchor follows the {@code #}. */
    public static final String HELP_URL = "https://github.com/an0nn30/moray/blob/main/docs/configuration.md#";

    /**
     * What the dialog needs from the application.
     *
     * @param save saves core settings; completes on any thread
     * @param state the configuration state now
     * @param onState subscribes to later states
     * @param openConfigFile opens config.toml in the user's editor
     * @param migrate runs Migrate Config
     * @param browse opens a web page
     * @param confirmDiscard asks, over a component, whether to discard that many unsaved changes
     */
    public record Hooks(Function<SettingsEdit, CompletableFuture<ConfigService.State>> save, Supplier<ConfigService.State> state,
                        Function<Consumer<ConfigService.State>, Subscription> onState, Runnable openConfigFile, Runnable migrate,
                        Consumer<URI> browse, BiPredicate<Component, Integer> confirmDiscard) {
        public Hooks {
            Objects.requireNonNull(save); Objects.requireNonNull(state); Objects.requireNonNull(onState);
            Objects.requireNonNull(openConfigFile); Objects.requireNonNull(migrate); Objects.requireNonNull(browse);
            Objects.requireNonNull(confirmDiscard);
        }
    }

    private final AuxiliaryWindows windows;
    private final Function<PageContext, List<SettingsPage>> pageFactory;
    private final Hooks hooks;
    private final Deque<String> backStack = new ArrayDeque<>();
    private final Deque<String> forwardStack = new ArrayDeque<>();
    private AuxiliarySurface surface;
    private SettingsView view;
    private SettingsDraft draft;
    private List<SettingsPage> pages = List.of();
    private Subscription draftListening;
    private Subscription stateListening;
    private String current;
    private String lastPage;
    private String query = "";
    private boolean rejected;
    private boolean applying;

    public SettingsDialog(AuxiliaryWindows windows, Function<PageContext, List<SettingsPage>> pages, Hooks hooks) {
        this.windows = Objects.requireNonNull(windows);
        this.pageFactory = Objects.requireNonNull(pages);
        this.hooks = Objects.requireNonNull(hooks);
    }

    /** Asks with a standard Yes/No dialog; the default {@code confirmDiscard}. */
    public static boolean askDiscard(Component parent, Integer count) {
        String changes = count == 1 ? "1 unsaved change" : count + " unsaved changes";
        return JOptionPane.showConfirmDialog(parent, "Discard " + changes + "?", "Settings", JOptionPane.YES_NO_OPTION,
            JOptionPane.WARNING_MESSAGE) == JOptionPane.YES_OPTION;
    }

    public boolean isOpen() { return surface != null && !surface.closed(); }

    /**
     * Shows Settings over {@code ownerOrNull} (no owner when null) at {@code pageOrNull} (the last page visited when null).
     * When it is already open, it comes forward and selects the page. Showing blocks until it closes.
     */
    public void open(UUID ownerOrNull, String pageOrNull) {
        if (isOpen()) {
            if (pageOrNull != null) select(pageOrNull);
            surface.toFront();
            return;
        }
        ConfigService.State state = hooks.state().get();
        draft = new SettingsDraft(state.values());
        view = new SettingsView(this::select, this::cancel);
        pages = List.copyOf(pageFactory.apply(new PageContext(draft, this::select, this::closeThen, this::chooseFile, this::refreshNow)));
        backStack.clear();
        forwardStack.clear();
        current = null;
        query = "";
        surface = windows.modalDialog("app.settings", "Settings", new Dimension(1000, 700), ownerOrNull);
        surface.setContent(view);
        surface.onClosing(this::mayClose);
        surface.onClosed(this::closed);
        wire();
        draftListening = draft.onChanged(this::refreshNow);
        stateListening = hooks.onState().apply(this::accept);
        accept(state);
        view.tree.show(pages, page -> true, Map.of(), false);
        select(pageOrNull != null ? pageOrNull : lastPage != null ? lastPage : CorePages.DEFAULT_PAGE);
        surface.show();
    }

    private void wire() {
        view.back.addActionListener(event -> back());
        view.forward.addActionListener(event -> forward());
        view.apply.addActionListener(event -> apply());
        view.ok.addActionListener(event -> ok());
        view.cancel.addActionListener(event -> cancel());
        view.help.addActionListener(event -> page(current).ifPresent(page -> hooks.browse().accept(URI.create(HELP_URL + page.helpAnchor()))));
        view.search.getDocument().addDocumentListener(new DocumentListener() {
            @Override public void insertUpdate(DocumentEvent event) { search(view.search.getText()); }
            @Override public void removeUpdate(DocumentEvent event) { search(view.search.getText()); }
            @Override public void changedUpdate(DocumentEvent event) { search(view.search.getText()); }
        });
    }

    SettingsView view() { return view; }
    SettingsDraft draft() { return draft; }
    List<SettingsPage> pages() { return pages; }
    String currentPage() { return current; }

    private Optional<SettingsPage> page(String id) {
        return pages.stream().filter(page -> page.id().equals(id)).findFirst();
    }

    /** Selects a page, remembering the one it leaves for Back. */
    void select(String id) {
        var target = page(id).or(() -> pages.stream().findFirst()).orElseThrow();
        if (target.id().equals(current)) return;
        if (current != null) { backStack.push(current); forwardStack.clear(); }
        show(target);
    }

    void back() {
        if (backStack.isEmpty()) return;
        forwardStack.push(current);
        show(page(backStack.pop()).orElseThrow());
    }

    void forward() {
        if (forwardStack.isEmpty()) return;
        backStack.push(current);
        show(page(forwardStack.pop()).orElseThrow());
    }

    private void show(SettingsPage page) {
        current = page.id();
        lastPage = page.id();
        view.page(page.component());
        view.breadcrumb.setText(breadcrumb(page));
        view.tree.select(page.id());
        page.highlight(query);
        refreshNow();
    }

    private String breadcrumb(SettingsPage page) {
        var titles = new ArrayDeque<String>();
        for (Optional<SettingsPage> at = Optional.of(page); at.isPresent(); at = at.get().parent().flatMap(this::page))
            titles.push(at.get().title());
        return String.join(" › ", titles);
    }

    /** Filters the tree to pages matching {@code text}, with match counts, and highlights the visible page. */
    void search(String text) {
        query = text == null ? "" : text;
        if (query.isBlank()) {
            view.tree.show(pages, page -> true, Map.of(), false);
            if (current != null) view.tree.select(current);
        } else {
            var counts = new LinkedHashMap<String, Integer>();
            for (SettingsPage page : pages) {
                int matches = (int) page.searchTerms().stream().filter(term -> SearchText.matches(term, query)).count();
                if (matches > 0) counts.put(page.id(), matches);
            }
            view.tree.show(pages, page -> counts.containsKey(page.id()), counts, true);
            if (!counts.containsKey(current) && !counts.isEmpty()) {
                select(counts.keySet().iterator().next());
                return;
            }
            if (current != null) view.tree.select(current);
        }
        page(current).ifPresent(page -> page.highlight(query));
    }

    private boolean modified() { return draft.modified() || pages.stream().anyMatch(SettingsPage::modified); }

    private int pendingCount() {
        return draft.size() + (int) pages.stream().filter(page -> !(page instanceof SchemaPage) && page.modified()).count();
    }

    private boolean valid() { return pages.stream().allMatch(SettingsPage::valid); }

    /** Re-evaluates the modified marks and the buttons. */
    void refreshNow() {
        if (view == null) return;
        view.tree.modifiedBy(id -> page(id).map(SettingsPage::modified).orElse(false));
        boolean blocked = rejected || !valid() || applying;
        view.apply.setEnabled(modified() && !blocked);
        view.ok.setEnabled(!blocked);
        view.back.setEnabled(!backStack.isEmpty());
        view.forward.setEnabled(!forwardStack.isEmpty());
    }

    private void accept(ConfigService.State state) {
        rejected = state.rejected();
        if (!rejected) draft.rebase(state.values());
        if (rejected) view.banner("config.toml has errors, so changes can't be saved until they're fixed.", "Open config.toml", hooks.openConfigFile());
        else if (state.migratable()) view.banner("config.toml uses old setting names.", "Migrate Config…", hooks.migrate());
        else view.banner(null, null, null);
        pages.forEach(SettingsPage::refresh);
        refreshNow();
    }

    /** Saves the draft, then every other modified page in tree order; stops at the first failure. */
    void apply() { apply(() -> { }); }

    void ok() {
        if (rejected || !valid() || applying) return;
        if (!modified()) { surface.close(); return; }
        apply(() -> { if (isOpen()) surface.close(); });
    }

    void cancel() { if (isOpen()) surface.requestClose(); }

    private void apply(Runnable done) {
        if (applying || rejected || !valid() || !modified()) return;
        applying = true;
        view.message.setText("");
        refreshNow();
        CompletableFuture<ConfigService.State> core = draft.modified() ? hooks.save().apply(draft.edit()) : CompletableFuture.completedFuture(null);
        core.whenComplete((state, failure) -> SwingUtilities.invokeLater(() -> {
            if (!isOpen()) return;
            if (failure != null) { failed(null, failure); return; }
            if (state != null) draft.rebase(state.values());
            var remaining = new ArrayDeque<SettingsPage>();
            for (SettingsPage page : pages) if (!(page instanceof SchemaPage) && page.modified()) remaining.add(page);
            applyPages(remaining, done);
        }));
    }

    private void applyPages(Deque<SettingsPage> remaining, Runnable done) {
        SettingsPage next = remaining.poll();
        if (next == null) {
            applying = false;
            refreshNow();
            done.run();
            return;
        }
        next.apply().whenComplete((ignored, failure) -> SwingUtilities.invokeLater(() -> {
            if (!isOpen()) return;
            if (failure != null) { failed(next, failure); return; }
            applyPages(remaining, done);
        }));
    }

    private void failed(SettingsPage page, Throwable failure) {
        applying = false;
        Throwable cause = failure instanceof CompletionException && failure.getCause() != null ? failure.getCause() : failure;
        view.message.setText((page == null ? "" : page.title() + ": ") + cause.getMessage());
        refreshNow();
    }

    private boolean mayClose() {
        if (!modified()) return true;
        return hooks.confirmDiscard().test(view, pendingCount());
    }

    private void closeThen(Runnable action) {
        if (!isOpen() || surface.requestClose()) action.run();
    }

    private Optional<Path> chooseFile(String title, Optional<Path> initial) {
        return isOpen() && surface.shown() ? surface.chooseFile(title, initial) : Optional.empty();
    }

    private void closed() {
        if (draftListening != null) draftListening.close();
        if (stateListening != null) stateListening.close();
        pages.forEach(SettingsPage::dispose);
        pages = List.of();
        surface = null;
    }
}
```

- [ ] **Step 4: Run the test to verify it passes**

Run: `./gradlew :jasper-app:test --tests "dev.jasper.app.settingsui.*"`
Expected: PASS. The tests drive the dialog with fake shells, so `surface.show()` returns at once; in the application the modal dialog's `show()` returns only after it closes.

- [ ] **Step 5: Commit**

```bash
git branch --show-current
git add jasper-app/src
git commit -m "Add the Settings dialog: tree, search, history, banners and Apply/OK/Cancel

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 8: Open Settings from Cmd+, and the application menu

**Files:**
- Modify: `jasper-app/src/main/java/dev/jasper/app/application/JasperApplication.java`
- Modify: `jasper-app/src/main/java/dev/jasper/app/workspace/WindowContent.java` (one tooltip)
- Modify: `jasper-app/src/main/java/dev/jasper/app/application/package-info.java` (allowed dependencies)

**Interfaces:**
- Consumes: `SettingsDialog`, `CorePages`, `ConfigurationController.settingsOpener/state/save/onState/reload/openConfigFile/migrationAvailable`.
- Produces: `JasperApplication.openSettings(UUID ownerOrNull, String pageOrNull)`; the preferences handler.

- [ ] **Step 1: Write the implementation**

In `JasperApplication.java`:
- fields:

```java
    private dev.jasper.app.settingsui.SettingsDialog settings;
    private boolean preferencesHandlerInstalled;
```

- in the constructor, after the `APP_EVENT_REOPENED` block:

```java
        if (supports(Desktop.Action.APP_PREFERENCES)) {
            Desktop.getDesktop().setPreferencesHandler(event -> SwingUtilities.invokeLater(() -> openSettings(null, null)));
            preferencesHandlerInstalled = true;
        }
```

- in `startPlugins`, after the line that adds the `app.help` menu section:

```java
        if (configuration != null) {
            var system = new dev.jasper.app.settingsui.CorePages.SystemActions(() -> configuration.state().file(),
                this::openConfigFile, this::revealConfigFile, () -> configuration.reload(), this::migrateConfiguration,
                configuration::migrationAvailable);
            settings = new dev.jasper.app.settingsui.SettingsDialog(auxiliary,
                dev.jasper.app.settingsui.CorePages.pages(() -> themeCatalog.latest().names(), system,
                    () -> { if (!quitting && !stopped) shortcutHelp.open(); }, this::managePlugins),
                new dev.jasper.app.settingsui.SettingsDialog.Hooks(configuration::save, configuration::state, configuration::onState,
                    this::openConfigFile, this::migrateConfiguration, this::browse, dev.jasper.app.settingsui.SettingsDialog::askDiscard));
            configuration.settingsOpener(owner -> openSettings(owner.id(), null));
        }
```

- new methods (next to `info`):

```java
        /** Opens Settings over the given window, or the active one when null, at a page (the last visited when null). */
        void openSettings(java.util.UUID ownerOrNull, String pageOrNull) {
            if (settings == null || quitting || stopped) return;
            java.util.UUID owner = ownerOrNull != null ? ownerOrNull
                : lastActive != null && windows.contains(lastActive) ? lastActive.content().id() : null;
            settings.open(owner, pageOrNull);
        }

        private void openConfigFile() {
            if (configuration == null) return;
            configuration.openConfigFile().whenComplete((file, failure) -> {
                if (failure != null) SwingUtilities.invokeLater(() -> notice(failure.getMessage()));
            });
        }

        private void revealConfigFile() {
            if (configuration == null) return;
            Path folder = configuration.state().file().toAbsolutePath().getParent();
            CompletableFuture.runAsync(() -> new dev.jasper.app.platform.ConfigEditor().reveal(folder))
                .whenComplete((ignored, failure) -> {
                    if (failure != null) SwingUtilities.invokeLater(() -> notice("Could not reveal " + folder));
                });
        }

        private void browse(java.net.URI uri) {
            if (!supports(Desktop.Action.BROWSE)) return;
            CompletableFuture.runAsync(() -> {
                try { Desktop.getDesktop().browse(uri); }
                catch (java.io.IOException failure) { throw new java.io.UncheckedIOException(failure); }
            });
        }
```

- in `shutdown`, after the quit-handler removal:

```java
        if (preferencesHandlerInstalled) { Desktop.getDesktop().setPreferencesHandler(null); preferencesHandlerInstalled = false; }
```

In `WindowContent.connectConfiguration`, change `"Open configuration file"` to `"Open Settings"`.

In `application/package-info.java`, add `dev.jasper.app.settingsui` to the allowed outgoing dependencies line.

- [ ] **Step 2: Run the app suite and the architecture check**

Run: `./gradlew :jasper-app:test verifyApplicationArchitecture`
Expected: PASS. If a test asserted the old tooltip text, update it to "Open Settings" and record it in the status banner.

- [ ] **Step 3: Commit**

```bash
git branch --show-current
git add jasper-app/src
git commit -m "Open the Settings dialog from Cmd+, and the application menu

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 9: Preview, documentation and the full check

**Files:**
- Create: `jasper-app/src/test/java/dev/jasper/app/settingsui/SettingsDialogPreview.java`
- Modify: `jasper-app/build.gradle.kts` (task `settingsPreview`)
- Modify: `docs/configuration.md`, `README.md`, `docs/app-architecture.md`, `docs/app-maintenance.md`, `docs/STATUS.md`, this plan's banner

- [ ] **Step 1: Write the preview**

```java
package dev.jasper.app.settingsui;

import dev.jasper.app.appearance.ThemeController;
import dev.jasper.app.appearance.ThemeManager;
import dev.jasper.app.config.ConfigLoader;
import dev.jasper.app.config.ConfigService;
import dev.jasper.app.config.UiFontConfig;
import dev.jasper.app.lifecycle.Subscription;
import dev.jasper.app.persistence.UiState;
import dev.jasper.app.windows.AuxiliarySurface;
import dev.jasper.app.windows.AuxiliaryWindows;
import java.awt.Container;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import javax.imageio.ImageIO;
import javax.swing.JComponent;
import javax.swing.SwingUtilities;

/** Opt-in renders of the real Settings dialog, headless. Never creates a native window. */
public final class SettingsDialogPreview {
    private SettingsDialogPreview() { }

    public static void main(String[] args) throws Exception {
        Path output = Path.of(args[0]);
        Files.createDirectories(output);
        Path file = Path.of("/Users/me/.config/jasper/config.toml");
        var parsed = ConfigLoader.parse(file, "[window]\ntab_height = 34\n[terminal.font]\nsize = 15\n", true);
        var state = new ConfigService.State(parsed.snapshot(), parsed.diagnostics(), file, true, parsed.legacyKeys(), parsed.deadTables(),
            parsed.values(), false);
        SwingUtilities.invokeAndWait(() -> {
            var themes = new ThemeController();
            for (var theme : ThemeManager.builtIns()) for (int size : new int[]{0, 18}) {
                themes.configure(theme, new UiFontConfig("system", size));
                var holder = new JComponent[1];
                var windows = new AuxiliaryWindows(UiState.inMemory(), surface -> {
                    holder[0] = surface.holder();
                    return new AuxiliarySurface.Shell(() -> { }, () -> { }, () -> { }, title -> { }, () -> new Rectangle(0, 0, 1000, 700),
                        (title, initial) -> Optional.empty());
                });
                var dialog = new SettingsDialog(windows, CorePages.pages(() -> List.of("Jasper Dark", "IntelliJ Light"),
                    new CorePages.SystemActions(() -> file, () -> { }, () -> { }, () -> { }, () -> { }, () -> false), () -> { }, () -> { }),
                    new SettingsDialog.Hooks(edit -> CompletableFuture.completedFuture(state), () -> state,
                        listener -> new Subscription(() -> { }), () -> { }, () -> { }, uri -> { }, (parent, count) -> true));
                String suffix = theme.id() + "-" + size + ".png";
                dialog.open(null, "appearance");
                render(holder[0], output.resolve("appearance-" + suffix));
                dialog.select("window");
                render(holder[0], output.resolve("window-" + suffix));
                dialog.select("terminal.font");
                render(holder[0], output.resolve("font-" + suffix));
                dialog.select("system");
                render(holder[0], output.resolve("system-" + suffix));
                dialog.view().search.setText("font");
                render(holder[0], output.resolve("search-" + suffix));
                dialog.view().search.setText("");
                dialog.draft().stage(dev.jasper.app.config.CoreSettings.TERMINAL_BELL, Optional.of(dev.jasper.terminal.config.BellMode.NONE));
                dialog.select("terminal");
                render(holder[0], output.resolve("modified-" + suffix));
                dialog.cancel();
            }
        });
    }

    private static void layoutVisible(Container container) {
        container.doLayout();
        for (var child : container.getComponents())
            if (child.isVisible() && child instanceof Container nested) layoutVisible(nested);
    }

    private static void render(JComponent component, Path file) {
        component.setSize(1000, 700);
        layoutVisible(component);
        layoutVisible(component);
        var image = new BufferedImage(1000, 700, BufferedImage.TYPE_INT_ARGB);
        var g = image.createGraphics();
        try { component.printAll(g); } finally { g.dispose(); }
        try { ImageIO.write(image, "png", file.toFile()); }
        catch (java.io.IOException error) { throw new java.io.UncheckedIOException(error); }
    }
}
```

In `jasper-app/build.gradle.kts`, after the `mockUiPreview` task:

```kotlin
// Opt-in render of the Settings dialog, dark and light, default and 18 pt UI font. Never creates a native window.
tasks.register<JavaExec>("settingsPreview") {
    group = "verification"
    description = "Renders the Settings dialog headlessly into build/settings-preview."
    dependsOn(tasks.testClasses)
    classpath = sourceSets["test"].runtimeClasspath
    mainClass = "dev.jasper.app.settingsui.SettingsDialogPreview"
    jvmArgs("-Djava.awt.headless=true", "--enable-native-access=ALL-UNNAMED")
    args(layout.buildDirectory.dir("settings-preview").get().asFile.absolutePath)
    providers.gradleProperty("jasper.uiScale").orNull?.let { systemProperty("flatlaf.uiScale", it) }
}
```

- [ ] **Step 2: Render and inspect**

Run: `./gradlew :jasper-app:settingsPreview`
Expected: BUILD SUCCESSFUL and 24 PNGs in `jasper-app/build/settings-preview/`. Open several with the Read tool and compare with the reference screenshot described in the spec: tree with bold top-level entries and chevrons, breadcrumb header with arrows, rows with labels, comments and Reset icons, a footer with ? / Cancel / Apply / OK, blue "Terminal" in the modified render, a filtered tree with counts in the search render. Fix layout defects (clipping, overlaps, unreadable colours) in the view or page code before continuing, and note each fix in the status banner.

- [ ] **Step 3: Documentation**

In `docs/configuration.md`, add after the `## Location and startup options` section:

```markdown
## Settings dialog

**Settings** (Cmd+, on macOS, or Jasper › Settings…) edits every setting in this guide without opening the file.
The tree on the left groups them as Appearance & Behavior (Appearance, Window, Notifications, Command Palette,
Desk Buddy, System Settings), Terminal (Font & Colors, Cursor, Shell), Keymap and Plugins; the search field
filters the tree and highlights matching settings on the page.

Changes wait until **Apply** or **OK**; **Cancel** (or Escape) discards them, asking first. A page with unsaved
changes shows its name in blue, and a setting that differs from its default has a Reset button, which removes
the key from the file. Settings writes `config.toml` in place and keeps your comments; a value the file would
reject is refused with the same message the file would produce. If `config.toml` has errors, Settings shows a
banner and saves nothing until the file is fixed; if it uses old setting names, the banner offers Migrate Config.
System Settings shows the file's location with Open in Editor, Reveal, Reload from Disk and Migrate Config.
Keymap and Plugins open their own windows for now.
```

In `README.md`, before the paragraph that introduces the root example configuration file, add one sentence: "Settings (Cmd+,) edits every setting in a dialog; see the Settings dialog.", making "the Settings dialog" a Markdown link to `docs/configuration.md` with the anchor `settings-dialog`.

In `docs/app-architecture.md`, add to the package table after the `settings` row:

```markdown
| `settingsui` | EDT Settings dialog: the shell, the draft of pending core-setting edits, schema-generated, group and launcher pages, editors and the core page tree. SettingsDialog owns each opening's pages and disposes them on close. |
```

In `docs/app-maintenance.md`, in the recipe sentence added by A ("Every new field starts in [CoreSettings]…"), add after "…and add it to `SCHEMA` in template order.": ` Place it on a page in \`settingsui.CorePages\` (a test checks every setting appears on exactly one page).`

Add a `docs/STATUS.md` entry at the top of the current state:

```markdown
- **Settings dialog B1** (`claude/settings-dialog`, 2026-09-28; spec
  `docs/superpowers/specs/2026-09-28-jasper-settings-dialog-design.md`, plan
  `docs/superpowers/plans/2026-09-28-jasper-settings-dialog-b1.md`): Cmd+, and the macOS Settings… item open
  an IntelliJ-style modal Settings dialog (`dev.jasper.app.settingsui`): search, category tree, breadcrumb with
  back/forward, schema-generated pages for all 33 core settings with Reset and inline validation,
  Apply/OK/Cancel with a discard prompt, and banners for old keys and rejected files. `ConfigService.State`
  carries the loaded values and a `rejected` flag. Keymap and Plugins are launcher pages until plan B2.
  Previews: `./gradlew :jasper-app:settingsPreview`. GUI checks pending with the user. Not pushed; not merged.
```

Update this plan's `**Status:**` line and list deviations.

- [ ] **Step 4: Run the full check and the hygiene scan**

Run: `./gradlew check`
Expected: BUILD SUCCESSFUL; count results from `*/build/test-results/test/*.xml` and `plugins/*/build/test-results/test/*.xml` (failures and errors 0) and put the totals in the STATUS entry. Run the AGENTS.md hygiene scan over `jasper-app/src`; expected no output.

- [ ] **Step 5: Commit**

```bash
git branch --show-current
git add docs README.md jasper-app/build.gradle.kts jasper-app/src/test/java/dev/jasper/app/settingsui/SettingsDialogPreview.java
git commit -m "Preview and document the Settings dialog

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

Then stop: merging waits for the user, and so does the GUI checklist (open via Cmd+, and the application menu;
Apply a change and watch it take effect; Cancel with changes; banners; resize and reopen to check remembered bounds;
switch theme while open).
