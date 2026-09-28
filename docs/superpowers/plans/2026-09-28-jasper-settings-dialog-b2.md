# Settings Dialog B2 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Status:** Implemented on `claude/settings-dialog-b2` (inline execution, checkpoints after Tasks 3 and 5); `./gradlew check`
green: 2,925 tests, 0 failures, 0 errors, 5 skipped. Worktree `.worktrees/settings-dialog-b2`, from local `main` at 3cb249e5.
Record every deviation from this text here and in `docs/STATUS.md`.

Planned deviations from the spec (decided while writing this plan):
- The spec's `ConfigurationController.save(Setting<T>, T)` is not added. A window builds a one-setting edit with the new
  `SettingsEdit.of(setting, value, saved)` and saves it through the existing `ConfigurationController.save(SettingsEdit)`;
  a lambda cannot carry a generic method, and nothing else needs the overload.
- `SettingsPage` gains `default void selected()` (the Plugins page lists plugins afresh each time it is shown, as the
  old window did on activation) and `PageContext` gains `surface()` (the Plugins page opens its consent and confirm
  dialogs over Settings). A new `settingsui.FillingPage` lets Keymap and Plugins fill the page area and scroll their
  own lists.
- Without a configuration service (benchmark and tests only), there is no Settings dialog, so Keyboard Shortcuts… and
  Manage Plugins… do nothing, and View choices apply to that window directly instead of saving.

Deviations so far:
- Task 1: `FillingPageTest` also lays out the page (`page.doLayout()`) before comparing its content's size.
- Task 2: the renderer's new pattern variable is `entry`, not `row`, which clashed with the `int row` parameter.
- Task 4: the architecture check is the root task `./gradlew verifyApplicationArchitecture`, not a `:jasper-app` task.
- Task 6: the first run stopped at compilation (`configureTerminal` missing), so the reload tests' behavioural failure
  before the change was not observed separately.
- Task 7: `docs/sdk-architecture.md`'s package row for `pluginmanager` also changed (it still described a window). The
  preview renders were inspected with no defects found; at 18 pt, long tree labels are truncated and the plugin list scrolls
  sideways, both behaviours of existing components, left as they are.

**Goal:** Keymap and Plugins become real pages of the Settings dialog, and the View menu's chrome choices save to
`config.toml` instead of lasting one session.

**Architecture:** `shortcuthelp.KeymapPage` hosts the existing `ShortcutPanel`. Its edit card's Set stages bindings in a
`KeymapDraft`, and Apply writes them in one `saveKeybindings` call. `pluginmanager.PluginsPage` hosts
`PluginManagerPanel`; `PluginManager` no longer owns a window and opens its dialogs over the Settings surface.
`CorePages.pages` takes the two page factories from the application, and `LauncherPage` and `ShortcutHelp` are deleted.
`WindowContent` saves View choices through a hook `ConfigurationController` connects. View › Window Settings… replaces
the four size dialogs. `WorkspaceConfiguration` and `ThemeState` stop keeping session overrides.

**Tech Stack:** Java 25 (JetBrains Runtime), Swing with FlatLaf 3.7, Gradle wrapper, JUnit 5, AssertJ.

**Spec:** `docs/superpowers/specs/2026-09-28-jasper-settings-dialog-design.md` (read it first; sections 1, 3, 4 and 5
and "Delivery" describe B2). The B1 plan, `docs/superpowers/plans/2026-09-28-jasper-settings-dialog-b1.md`, built the
dialog this plan extends.

## Global Constraints

- Build and test only with `./gradlew` from the worktree root `/Users/dustin/projects/moray/.worktrees/settings-dialog-b2`.
  Never launch the GUI (`:jasper-app:run`, `:jasper-app:bench`).
- Run `git branch --show-current` before every commit and confirm `claude/settings-dialog-b2`. Other sessions switch
  branches in the main checkout; never commit there.
- Every commit message ends with `Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>`. Do not push; do not merge.
- Package dependencies: `shortcuthelp` and `pluginmanager` may depend on `settingsui`. `settingsui` must never depend on
  `shortcuthelp`, `pluginmanager` or `application`. `workspace` may now depend on `dev.jasper.app.settings`.
  `verifyApplicationArchitecture` (part of `check`) must pass.
- No interface without two real implementations. `SettingsPage` ends with four: `SchemaPage`, `GroupPage`, `KeymapPage`,
  `PluginsPage`.
- Swing work happens on the EDT. Tests use `DesktopTestSupport.edt` / `until` (`dev.jasper.app.workspace`).
- Source hygiene per AGENTS.md: write non-ASCII characters in new Java code as escapes (`"…"` for the ellipsis,
  `"›"` for the breadcrumb chevron). Plain text only in labels.
- The plan and the docs are checked by `AppDocumentationTest`: every Markdown link outside a fenced block must resolve.
  Do not add Markdown links to this plan; in docs, add only links you have checked.
- Page ids: Keymap `keymap`, Plugins `plugins`, Window `window`. Help anchors: Keymap `shortcuts`, Plugins `plugins`.
- The rejected-file message for View choices is exactly
  `config.toml has errors, so changes can't be saved until they're fixed.`

## File Structure

New:
- `jasper-app/src/main/java/dev/jasper/app/settingsui/FillingPage.java`: a page body that fills the page area.
- `jasper-app/src/main/java/dev/jasper/app/shortcuthelp/KeymapDraft.java`: staged shortcut edits.
- `jasper-app/src/main/java/dev/jasper/app/shortcuthelp/KeymapPage.java`: the Keymap page.
- `jasper-app/src/main/java/dev/jasper/app/pluginmanager/PluginsPage.java`: the Plugins page.
- Tests: `settingsui/FillingPageTest.java`, `shortcuthelp/KeymapDraftTest.java`, `shortcuthelp/KeymapPageTest.java`.

Deleted: `settingsui/LauncherPage.java`, `shortcuthelp/ShortcutHelp.java`.

Modified (main): `settingsui/PageContext.java`, `settingsui/SettingsPage.java`, `settingsui/SettingsDialog.java`,
`settingsui/CorePages.java`, `shortcuthelp/ShortcutEntry.java`, `shortcuthelp/ShortcutTree.java`,
`shortcuthelp/ShortcutPanel.java`, `shortcuthelp/ShortcutEditPanel.java`, `shortcuthelp/package-info.java`,
`pluginmanager/PluginManager.java`, `pluginmanager/package-info.java`, `config/SettingsEdit.java`,
`config/ConfigSnapshot.java`, `config/ConfigTemplate.java`, `config/CoreSettings.java`,
`application/ConfigurationController.java`, `application/JasperApplication.java`, `workspace/WindowContent.java`,
`workspace/WindowCommands.java`, `workspace/WindowChrome.java`, `workspace/WorkspaceConfiguration.java`,
`workspace/package-info.java`, `appearance/ThemeState.java`, `appearance/ThemeController.java`.

All main paths above are under `jasper-app/src/main/java/dev/jasper/app/`; test paths under
`jasper-app/src/test/java/dev/jasper/app/`.

---

### Task 1: Pages can fill the page area, learn when they are shown and open dialogs over Settings

**Files:**
- Create: `jasper-app/src/main/java/dev/jasper/app/settingsui/FillingPage.java`
- Modify: `jasper-app/src/main/java/dev/jasper/app/settingsui/PageContext.java`
- Modify: `jasper-app/src/main/java/dev/jasper/app/settingsui/SettingsPage.java`
- Modify: `jasper-app/src/main/java/dev/jasper/app/settingsui/SettingsDialog.java`
- Test: `jasper-app/src/test/java/dev/jasper/app/settingsui/FillingPageTest.java` (new)
- Test: `jasper-app/src/test/java/dev/jasper/app/settingsui/SettingsDialogTest.java`

**Interfaces:**
- Produces: `public final class FillingPage extends JPanel implements Scrollable` with `public FillingPage(JComponent content)`.
- Produces: `PageContext` gains a sixth component `Supplier<AuxiliarySurface> surface` (null while the dialog is closed)
  and keeps a five-argument constructor that supplies `() -> null`.
- Produces: `SettingsPage.selected()`, a default no-op, called each time the dialog shows the page.

- [ ] **Step 1: Write the failing tests**

Create `FillingPageTest.java`:

```java
package dev.jasper.app.settingsui;

import static dev.jasper.app.workspace.DesktopTestSupport.edt;
import static org.assertj.core.api.Assertions.assertThat;

import java.awt.Dimension;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import org.junit.jupiter.api.Test;

class FillingPageTest {
    @Test void fillsTheVisibleAreaSoItsContentScrollsItself() throws Exception {
        edt(() -> {
            var content = new JPanel();
            content.setPreferredSize(new Dimension(800, 900));
            var page = new FillingPage(content);
            var scroll = new JScrollPane(page);
            scroll.setSize(400, 300);
            scroll.doLayout();
            scroll.getViewport().doLayout();
            assertThat(page.getSize()).isEqualTo(scroll.getViewport().getExtentSize());
            assertThat(content.getSize()).isEqualTo(page.getSize());
        });
    }
}
```

In `SettingsDialogTest.java`, add a field that records every page context, record it in `dialog()`, count selections in
`FakePage`, and add one test. Replace the start of `dialog()`'s page factory:

```java
        return new SettingsDialog(windows, context -> {
            var pages = new ArrayList<>(CorePages.pages(() -> List.of("Jasper Dark"), new CorePages.SystemActions(() -> FILE,
```

with:

```java
        return new SettingsDialog(windows, context -> {
            contexts.add(context);
            var pages = new ArrayList<>(CorePages.pages(() -> List.of("Jasper Dark"), new CorePages.SystemActions(() -> FILE,
```

Add the field next to `opened`:

```java
    private final List<PageContext> contexts = new ArrayList<>();
```

In `FakePage`, add a counter and override `selected()`:

```java
        int selections;
        @Override public void selected() { selections++; }
```

Add the test:

```java
    @Test void showingAPageTellsItAndPagesCanOpenDialogsOverSettings() throws Exception {
        var dialog = dialog();
        edt(() -> {
            dialog.open(null, "window");
            assertThat(fake.selections).isZero();
            dialog.select("keymap");
            assertThat(fake.selections).isEqualTo(1);
            dialog.select("window");
            dialog.back();
            assertThat(fake.selections).as("Back shows it again").isEqualTo(2);
            var surface = contexts.getLast().surface().get();
            assertThat(surface).isNotNull();
            assertThat(surface.title()).isEqualTo("Settings");
            dialog.cancel();
            assertThat(contexts.getLast().surface().get()).as("gone once the dialog closes").isNull();
        });
    }
```

- [ ] **Step 2: Run the tests to verify they fail**

Run: `./gradlew :jasper-app:test --tests 'dev.jasper.app.settingsui.FillingPageTest' --tests 'dev.jasper.app.settingsui.SettingsDialogTest'`
Expected: compilation fails: `FillingPage` does not exist, `selected()` overrides nothing, `surface()` is undefined.

- [ ] **Step 3: Implement**

Create `FillingPage.java`:

```java
package dev.jasper.app.settingsui;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.Rectangle;
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.Scrollable;
import javax.swing.SwingConstants;

/**
 * A page body that fills the dialog's page area instead of growing it, for pages such as Keymap and Plugins that scroll
 * their own lists. EDT only.
 */
public final class FillingPage extends JPanel implements Scrollable {
    public FillingPage(JComponent content) {
        super(new BorderLayout());
        setOpaque(false);
        add(content, BorderLayout.CENTER);
    }

    @Override public Dimension getPreferredScrollableViewportSize() { return getPreferredSize(); }
    @Override public int getScrollableUnitIncrement(Rectangle visible, int orientation, int direction) { return 16; }
    @Override public int getScrollableBlockIncrement(Rectangle visible, int orientation, int direction) {
        return orientation == SwingConstants.VERTICAL ? visible.height : visible.width;
    }
    @Override public boolean getScrollableTracksViewportWidth() { return true; }
    @Override public boolean getScrollableTracksViewportHeight() { return true; }
}
```

Replace `PageContext.java` with:

```java
package dev.jasper.app.settingsui;

import dev.jasper.app.windows.AuxiliarySurface;
import java.nio.file.Path;
import java.util.Optional;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * What a page may ask of the dialog it is in.
 *
 * @param draft the dialog's pending core-setting edits
 * @param navigate selects another page by id
 * @param closeThen closes the dialog (asking about unsaved changes) and then runs the action
 * @param chooseFile asks for an existing file over the dialog: title and initial path in, choice out
 * @param changed tells the dialog a page's validity or modification changed outside the draft
 * @param surface the dialog's surface, for dialogs a page opens over it; null while the dialog is closed
 */
public record PageContext(SettingsDraft draft, Consumer<String> navigate, Consumer<Runnable> closeThen,
                          BiFunction<String, Optional<Path>, Optional<Path>> chooseFile, Runnable changed,
                          Supplier<AuxiliarySurface> surface) {
    /** A context for pages that open no dialogs of their own. */
    public PageContext(SettingsDraft draft, Consumer<String> navigate, Consumer<Runnable> closeThen,
                       BiFunction<String, Optional<Path>, Optional<Path>> chooseFile, Runnable changed) {
        this(draft, navigate, closeThen, chooseFile, changed, () -> null);
    }
}
```

In `SettingsPage.java`, after `default void refresh() { }`, add:

```java

    /** The dialog shows this page now; called each time it is shown. */
    default void selected() { }
```

In `SettingsDialog.java`, give pages the surface. Replace:

```java
        pages = List.copyOf(pageFactory.apply(new PageContext(draft, this::select, this::closeThen, this::chooseFile, this::refreshNow)));
```

with:

```java
        pages = List.copyOf(pageFactory.apply(new PageContext(draft, this::select, this::closeThen, this::chooseFile, this::refreshNow,
            () -> isOpen() ? surface : null)));
```

and tell the page it is shown. Replace:

```java
        page.highlight(query);
        refreshNow();
    }
```

(the end of `show(SettingsPage page)`) with:

```java
        page.highlight(query);
        page.selected();
        refreshNow();
    }
```

- [ ] **Step 4: Run the tests to verify they pass**

Run: `./gradlew :jasper-app:test --tests 'dev.jasper.app.settingsui.*'`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git branch --show-current   # must print claude/settings-dialog-b2
git add jasper-app/src/main/java/dev/jasper/app/settingsui jasper-app/src/test/java/dev/jasper/app/settingsui
git commit -m "Let Settings pages fill the page area, learn when shown and open dialogs over Settings

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 2: Shortcut staging pieces: the draft, blue customized rows and a Set button

**Files:**
- Create: `jasper-app/src/main/java/dev/jasper/app/shortcuthelp/KeymapDraft.java`
- Modify: `jasper-app/src/main/java/dev/jasper/app/shortcuthelp/ShortcutEntry.java`
- Modify: `jasper-app/src/main/java/dev/jasper/app/shortcuthelp/ShortcutTree.java`
- Modify: `jasper-app/src/main/java/dev/jasper/app/shortcuthelp/ShortcutPanel.java`
- Modify: `jasper-app/src/main/java/dev/jasper/app/shortcuthelp/ShortcutEditPanel.java`
- Test: `jasper-app/src/test/java/dev/jasper/app/shortcuthelp/KeymapDraftTest.java` (new)
- Test: `jasper-app/src/test/java/dev/jasper/app/shortcuthelp/ShortcutSectionsTest.java`
- Test: `jasper-app/src/test/java/dev/jasper/app/shortcuthelp/ShortcutEditingTest.java`
- Test: `jasper-app/src/test/java/dev/jasper/app/shortcuthelp/ShortcutTestSupport.java`

**Interfaces:**
- Produces (package-private): `final class KeymapDraft` with `void stage(Map<String, Optional<String>> changes, Map<String, String> saved)`,
  `void prune(Map<String, String> saved)`, `Map<String, String> overrides(Map<String, String> saved)`,
  `Map<String, String> preview(Map<String, String> saved, Map<String, Optional<String>> more)`,
  `Map<String, Optional<String>> edits()`, `boolean modified()`, `void clear()`, `void saved(Map<String, Optional<String>> edits)`.
  An edit maps an action id to a binding, or to empty to restore the default (the `saveKeybindings` convention).
- Produces (package-private): `boolean ShortcutEntry.customized(boolean macOs)`; `ShortcutTree(boolean macOs, Consumer<ShortcutEntry> edit)`;
  `ShortcutEditPanel.set` (the button formerly `save`, labelled "Set").

- [ ] **Step 1: Write the failing tests**

Create `KeymapDraftTest.java`:

```java
package dev.jasper.app.shortcuthelp;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class KeymapDraftTest {
    private static final Map<String, String> SAVED = Map.of("copy", "cmd+shift+c");

    @Test void stagingMergesOverTheSavedOverridesAndDropsEditsThatChangeNothing() {
        var draft = new KeymapDraft();
        draft.stage(Map.of("new_tab", Optional.of("cmd+y")), SAVED);
        draft.stage(Map.of("copy", Optional.empty()), SAVED);
        assertThat(draft.modified()).isTrue();
        assertThat(draft.overrides(SAVED)).isEqualTo(Map.of("new_tab", "cmd+y"));
        draft.stage(Map.of("paste", Optional.empty(), "copy", Optional.of("cmd+shift+c")), SAVED);
        assertThat(draft.edits()).isEqualTo(Map.of("new_tab", Optional.of("cmd+y")));
    }

    @Test void previewStagesNothingAndSavingForgetsOnlyWhatWasSaved() {
        var draft = new KeymapDraft();
        draft.stage(Map.of("new_tab", Optional.of("cmd+y")), SAVED);
        assertThat(draft.preview(SAVED, Map.of("find", Optional.of("cmd+j"))))
            .isEqualTo(Map.of("copy", "cmd+shift+c", "new_tab", "cmd+y", "find", "cmd+j"));
        assertThat(draft.edits()).containsOnlyKeys("new_tab");
        var sent = draft.edits();
        draft.stage(Map.of("find", Optional.of("cmd+j")), SAVED);
        draft.saved(sent);
        assertThat(draft.edits()).isEqualTo(Map.of("find", Optional.of("cmd+j")));
        draft.prune(Map.of("find", "cmd+j"));
        assertThat(draft.modified()).as("the saved file already holds it").isFalse();
        draft.stage(Map.of("copy", Optional.empty()), SAVED);
        draft.clear();
        assertThat(draft.modified()).isFalse();
    }
}
```

In `ShortcutSectionsTest.java`, add a test and a helper (the file already imports `javax.swing.*`, `Container`, `List`):

```java
    @Test void rowsAndSectionsThatDifferFromTheirDefaultsAreBlue() throws Exception {
        edt(() -> {
            new ThemeController();
            var panel = new ShortcutPanel(true, changes -> { throw new AssertionError("Unexpected save"); });
            try {
                panel.setRows(List.of(
                    new ShortcutEntry("new_tab", "Tabs", "New tab", KeyStroke.getKeyStroke("meta Y"), "Terminal window", "", true, "cmd+t"),
                    new ShortcutEntry("close_tab", "Tabs", "Close tab", KeyStroke.getKeyStroke("meta W"), "Terminal window", "", true, "cmd+w"),
                    new ShortcutEntry("copy", "Clipboard", "Copy", KeyStroke.getKeyStroke("meta C"), "Terminal window", "", true, "cmd+c")));
                panel.tree.expandSections(true);
                var link = UIManager.getColor("Component.linkColor");
                var plain = UIManager.getColor("Label.foreground");
                assertThat(link).isNotNull();
                assertThat(nameColor(panel.tree, "Tabs")).as("a section holding a changed row").isEqualTo(link);
                assertThat(nameColor(panel.tree, "New tab")).isEqualTo(link);
                assertThat(nameColor(panel.tree, "Close tab")).isEqualTo(plain);
                assertThat(nameColor(panel.tree, "Clipboard")).isEqualTo(plain);
            } finally { panel.close(); }
        });
    }

    private static java.awt.Color nameColor(JTree tree, String text) {
        for (int row = 0; row < tree.getRowCount(); row++) {
            Object node = tree.getPathForRow(row).getLastPathComponent();
            var cell = (Container) tree.getCellRenderer().getTreeCellRendererComponent(tree, node, false, tree.isExpanded(row),
                tree.getModel().isLeaf(node), row, false);
            var name = (JLabel) cell.getComponent(0);
            if (name.getText().equals(text)) return name.getForeground();
        }
        throw new AssertionError("no row named " + text);
    }
```

In `ShortcutEditingTest.java` and `ShortcutTestSupport.java`, rename the button: replace every `editor.save` with
`editor.set` (11 places: `sed -i '' 's/editor\.save/editor.set/g'` on both files). In `ShortcutEditingTest.java`,
add:

```java
    @Test void theEditCardSetsRatherThanSaves() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            var panel = new ShortcutPanel(true, changes -> new CompletableFuture<>());
            panel.setRows(ShortcutCatalog.rows(KeyBindings.defaults(true), List.of(), Map.of(), true));
            panel.search.setText("Copy");
            clickShortcut(panel);
            assertThat(panel.editor.set.getText()).isEqualTo("Set");
            assertThat(panel.editor.message.getText()).contains("click Set");
            panel.editor.record.doClick();
            assertThat(panel.editor.message.getText()).contains("Apply or OK");
            panel.close();
        });
    }
```

and in `saveFailureRemainsVisibleAndCanBeRetried`, after
`assertThat(panel[0].editor.message.getText()).contains("Permission denied");` add:

```java
            assertThat(panel[0].editor.message.getText()).startsWith("Could not set: ");
```

- [ ] **Step 2: Run the tests to verify they fail**

Run: `./gradlew :jasper-app:test --tests 'dev.jasper.app.shortcuthelp.*'`
Expected: compilation fails: `KeymapDraft` does not exist and `editor.set` is undefined.

- [ ] **Step 3: Implement**

Create `KeymapDraft.java`:

```java
package dev.jasper.app.shortcuthelp;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Shortcut edits staged on the Keymap page until Apply: an action id maps to a binding, or to empty to restore its
 * default. An edit that leaves the saved overrides as they are is not kept. EDT only.
 */
final class KeymapDraft {
    private final Map<String, Optional<String>> staged = new LinkedHashMap<>();

    /** Stages {@code changes} over the {@code saved} overrides. */
    void stage(Map<String, Optional<String>> changes, Map<String, String> saved) {
        staged.putAll(changes);
        prune(saved);
    }

    /** Drops staged edits that the {@code saved} overrides already hold. */
    void prune(Map<String, String> saved) {
        staged.entrySet().removeIf(entry -> entry.getValue().equals(Optional.ofNullable(saved.get(entry.getKey()))));
    }

    /** The saved overrides with every staged edit applied. */
    Map<String, String> overrides(Map<String, String> saved) { return merged(saved, staged); }

    /** As {@link #overrides}, with {@code more} applied on top, staging nothing. */
    Map<String, String> preview(Map<String, String> saved, Map<String, Optional<String>> more) {
        var all = new LinkedHashMap<>(staged);
        all.putAll(more);
        return merged(saved, all);
    }

    Map<String, Optional<String>> edits() { return Map.copyOf(staged); }

    boolean modified() { return !staged.isEmpty(); }

    void clear() { staged.clear(); }

    /** Forgets exactly these edits once they are saved; edits staged since then stay. */
    void saved(Map<String, Optional<String>> edits) { edits.forEach(staged::remove); }

    private static Map<String, String> merged(Map<String, String> saved, Map<String, Optional<String>> edits) {
        var result = new LinkedHashMap<>(saved);
        edits.forEach((id, binding) -> {
            if (binding.isPresent()) result.put(id, binding.get());
            else result.remove(id);
        });
        return result;
    }
}
```

`staged::remove` with two arguments is `Map.remove(key, value)`: it removes an edit only if it is unchanged since it
was sent.

Replace `ShortcutEntry.java` with:

```java
package dev.jasper.app.shortcuthelp;

import dev.jasper.app.config.KeyBindings;
import java.util.Objects;
import javax.swing.KeyStroke;

/** One reference row; a null stroke means the action is currently unassigned. */
record ShortcutEntry(String id, String group, String name, KeyStroke stroke, String context, String keywords,
                     boolean editable, String defaultBinding) {
    ShortcutEntry(String id, String group, String name, KeyStroke stroke, String context, String keywords) {
        this(id, group, name, stroke, context, keywords, false, "none");
    }

    /** Whether an editable row's shortcut differs from its default. */
    boolean customized(boolean macOs) {
        return editable && !Objects.equals(stroke, KeyBindings.parse(defaultBinding, macOs).orElse(null));
    }
}
```

In `ShortcutTree.java`, add a field and take the platform. Replace:

```java
    private boolean filtering, rebuilding;

    ShortcutTree(Consumer<ShortcutEntry> edit) {
```

with:

```java
    private final boolean macOs;
    private boolean filtering, rebuilding;

    ShortcutTree(boolean macOs, Consumer<ShortcutEntry> edit) {
        this.macOs = macOs;
```

and in the renderer `Row.getTreeCellRendererComponent`, replace:

```java
            Color foreground = UIManager.getColor(selected && tree.hasFocus() ? "Tree.selectionForeground" : "Label.foreground");
            name.setForeground(foreground);
```

with:

```java
            Color foreground = UIManager.getColor(selected && tree.hasFocus() ? "Tree.selectionForeground" : "Label.foreground");
            Color link = UIManager.getColor("Component.linkColor");
            // A row whose shortcut differs from its default is blue, and so is a section holding one, as IntelliJ shows them.
            boolean customized = item instanceof ShortcutEntry shortcut ? shortcut.customized(macOs)
                : java.util.Collections.list(node.children()).stream().anyMatch(child ->
                    ((DefaultMutableTreeNode) child).getUserObject() instanceof ShortcutEntry row && row.customized(macOs));
            name.setForeground(customized && link != null && !(selected && tree.hasFocus()) ? link : foreground);
```

In `ShortcutPanel.java`, replace `tree = new ShortcutTree(this::editRow);` with `tree = new ShortcutTree(macOs, this::editRow);`.

In `ShortcutEditPanel.java`:
- Replace the class comment `/** One pending row edit. Only Save calls the asynchronous settings writer. Owned by ShortcutPanel. */`
  with `/** One row edit. Set hands it to the page, which stages it until Apply. Owned by ShortcutPanel. */`.
- Replace `final JButton save = new JButton("Save");` with `final JButton set = new JButton("Set");`.
- Replace `buttons.add(save); buttons.add(cancel);` with `buttons.add(set); buttons.add(cancel);`.
- Replace `save.addActionListener(e -> save());` with `set.addActionListener(e -> set());`.
- Replace `save.setEnabled(!saving && binding != null && (conflicts.isEmpty() || reassign.isSelected()));` with
  `set.setEnabled(!saving && binding != null && (conflicts.isEmpty() || reassign.isSelected()));`.
- Replace the message block:

```java
        message.setText(saving ? "Saving shortcut…" : !conflicts.isEmpty()
            ? "Already used by: " + String.join(", ", conflicts.stream().map(ShortcutEntry::name).toList()) + ". Reassigning removes their shortcuts."
            : recording ? "Press the new shortcut, then click Save. Use Cancel to keep the current binding."
            : "Save applies this shortcut to terminal windows and stores it in settings.");
```

  with:

```java
        message.setText(saving ? "Setting shortcut…" : !conflicts.isEmpty()
            ? "Already used by: " + String.join(", ", conflicts.stream().map(ShortcutEntry::name).toList()) + ". Reassigning removes their shortcuts."
            : recording ? "Press the new shortcut, then click Set. Use Cancel to keep the current binding."
            : "Set stages this shortcut; Apply or OK in Settings saves it.");
```

- Replace `private void save() {` and its first line `if (!save.isEnabled()) return;` with `private void set() {` and
  `if (!set.isEnabled()) return;`.
- Replace `message.setText("Could not save: " + cause.getMessage());` with `message.setText("Could not set: " + cause.getMessage());`.

- [ ] **Step 4: Run the tests to verify they pass**

Run: `./gradlew :jasper-app:test --tests 'dev.jasper.app.shortcuthelp.*' --tests 'dev.jasper.app.workspace.ShortcutEditorIntegrationTest'`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git branch --show-current   # must print claude/settings-dialog-b2
git add jasper-app/src/main/java/dev/jasper/app/shortcuthelp jasper-app/src/test/java/dev/jasper/app/shortcuthelp
git commit -m "Stage shortcut edits, show changed shortcuts in blue and rename Save to Set

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 3: The Keymap page

**Files:**
- Create: `jasper-app/src/main/java/dev/jasper/app/shortcuthelp/KeymapPage.java`
- Modify: `jasper-app/src/main/java/dev/jasper/app/shortcuthelp/package-info.java`
- Modify: `jasper-app/src/test/java/dev/jasper/app/shortcuthelp/ShortcutTestSupport.java`
- Test: `jasper-app/src/test/java/dev/jasper/app/shortcuthelp/KeymapPageTest.java` (new)

**Interfaces:**
- Consumes: `KeymapDraft`, `ShortcutPanel(boolean, Function<Map<String, Optional<String>>, CompletableFuture<Void>>)`,
  `ShortcutCatalog.rows(KeyBindings, List<ActionEntry>, Map<String, String>, boolean)`, `FillingPage`, `PageContext.changed()`.
- Produces: `public final class KeymapPage implements SettingsPage` with `public static final String ID = "keymap"`,
  `public record Hooks(Contributions contributions, Supplier<Map<String, String>> overrides, Supplier<Map<String, String>> pluginNames, boolean macOs, Function<Map<String, Optional<String>>, CompletableFuture<Void>> save)`,
  `public KeymapPage(Hooks hooks, PageContext context)` and package-private `ShortcutPanel panel()`.
- Produces (test): `ShortcutTestSupport.set(KeymapPage page, String query, KeyStroke shortcut)` and
  `ShortcutTestSupport.editing(KeymapPage page)`; the window forms `save(AuxiliarySurface, …)` and
  `editing(AuxiliarySurface)` stay until Task 4.

- [ ] **Step 1: Write the failing test**

Replace `ShortcutTestSupport.java` with the following. It keeps the window helpers `ShortcutEditorIntegrationTest`
uses until Task 4 and adds the same helpers for the Keymap page:

```java
package dev.jasper.app.shortcuthelp;

import dev.jasper.app.windows.AuxiliarySurface;
import javax.swing.KeyStroke;

/** Owner-package access to the shortcut editor for tests and previews. */
public final class ShortcutTestSupport {
    private ShortcutTestSupport() { }

    /** Searches for {@code query}, edits the first row found, records {@code shortcut} and clicks Set. */
    public static void set(KeymapPage page, String query, KeyStroke shortcut) { set(page.panel(), query, shortcut); }

    public static boolean editing(KeymapPage page) { return page.panel().editor.isVisible(); }

    /** As {@link #set(KeymapPage, String, KeyStroke)}, in the Keyboard Shortcuts window (removed in Task 4). */
    public static void save(AuxiliarySurface surface, String query, KeyStroke shortcut) {
        set((ShortcutPanel) surface.holder().getComponent(0), query, shortcut);
    }

    public static boolean editing(AuxiliarySurface surface) {
        return ((ShortcutPanel) surface.holder().getComponent(0)).editor.isVisible();
    }

    private static void set(ShortcutPanel panel, String query, KeyStroke shortcut) {
        panel.search.setText(query);
        panel.tree.setSelectionRow(1);
        panel.tree.getActionMap().get("editShortcut").actionPerformed(null);
        if (!panel.capture(shortcut)) throw new AssertionError("not recording");
        panel.editor.set.doClick();
    }
}
```

Create `KeymapPageTest.java`:

```java
package dev.jasper.app.shortcuthelp;

import static dev.jasper.app.shortcuthelp.ShortcutTestSupport.set;
import static dev.jasper.app.workspace.DesktopTestSupport.edt;
import static org.assertj.core.api.Assertions.*;

import dev.jasper.app.config.KeyBindings;
import dev.jasper.app.contributions.Contributions;
import dev.jasper.app.settingsui.PageContext;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import javax.swing.KeyStroke;
import org.junit.jupiter.api.Test;

class KeymapPageTest {
    private final Contributions contributions = new Contributions();
    private final Map<String, String> saved = new HashMap<>();
    private final List<Map<String, Optional<String>>> writes = new ArrayList<>();
    private CompletableFuture<Void> result = CompletableFuture.completedFuture(null);
    private final int[] changes = {0};
    // The Keymap page never touches the core-setting draft.
    private final PageContext context = new PageContext(null, id -> { }, Runnable::run, (title, initial) -> Optional.empty(),
        () -> changes[0]++);

    private KeymapPage page() {
        return new KeymapPage(new KeymapPage.Hooks(contributions, () -> Map.copyOf(saved), () -> Map.of("dev.test", "Test Plugin"),
            true, edits -> { writes.add(edits); return result; }), context);
    }

    @Test void setStagesWithoutWritingAndTheRowsShowTheStagedBinding() throws Exception {
        var page = new KeymapPage[1];
        edt(() -> {
            page[0] = page();
            page[0].component();
            set(page[0], "Copy", KeyStroke.getKeyStroke("ctrl H"));
            assertThat(writes).isEmpty();
            assertThat(page[0].modified()).isTrue();
            assertThat(changes[0]).as("the dialog hears about it").isPositive();
            assertThat(page[0].panel().tree.entryAt(1).stroke()).isEqualTo(KeyStroke.getKeyStroke("ctrl H"));
        });
        edt(() -> {
            assertThat(ShortcutTestSupport.editing(page[0])).as("the card closes after Set").isFalse();
            page[0].dispose();
        });
    }

    @Test void applyWritesEveryStagedEditOnceThenTheSavedBindingsTakeOver() throws Exception {
        var page = new KeymapPage[1];
        edt(() -> {
            page[0] = page();
            page[0].component();
            set(page[0], "Copy", KeyStroke.getKeyStroke("ctrl H"));
        });
        edt(() -> set(page[0], "Paste", KeyStroke.getKeyStroke("ctrl J")));
        var applied = new AtomicReference<CompletableFuture<Void>>();
        edt(() -> applied.set(page[0].apply()));
        edt(() -> { });
        applied.get().get(5, TimeUnit.SECONDS);
        assertThat(writes).containsExactly(Map.of("copy", Optional.of("ctrl+h"), "paste", Optional.of("ctrl+j")));
        edt(() -> {
            assertThat(page[0].modified()).isFalse();
            saved.put("copy", "ctrl+h");
            saved.put("paste", "ctrl+j");
            page[0].refresh();
            page[0].panel().search.setText("Copy");
            assertThat(page[0].panel().tree.entryAt(1).stroke()).isEqualTo(KeyStroke.getKeyStroke("ctrl H"));
            page[0].dispose();
        });
    }

    @Test void aFailedApplyKeepsTheEditsStagedAndResetDropsThem() throws Exception {
        result = CompletableFuture.failedFuture(new IllegalArgumentException("Shortcuts changed on disk."));
        var page = new KeymapPage[1];
        edt(() -> {
            page[0] = page();
            page[0].component();
            set(page[0], "Copy", KeyStroke.getKeyStroke("ctrl H"));
        });
        var applied = new AtomicReference<CompletableFuture<Void>>();
        edt(() -> applied.set(page[0].apply()));
        edt(() -> { });
        assertThatThrownBy(() -> applied.get().get(5, TimeUnit.SECONDS)).hasMessageContaining("changed on disk");
        edt(() -> {
            assertThat(page[0].modified()).isTrue();
            page[0].reset();
            assertThat(page[0].modified()).isFalse();
            page[0].panel().search.setText("Copy");
            assertThat(page[0].panel().tree.entryAt(1).stroke())
                .isEqualTo(KeyBindings.defaults(true).strokeFor("copy").orElseThrow());
            page[0].dispose();
        });
    }

    @Test void searchTermsHighlightAndContributedActionsFollowTheCatalog() throws Exception {
        edt(() -> {
            var page = page();
            assertThat(page.id()).isEqualTo("keymap");
            assertThat(page.helpAnchor()).isEqualTo("shortcuts");
            assertThat(page.searchTerms()).contains("Keymap", "Clipboard").anySatisfy(term -> assertThat(term).startsWith("Copy"));
            page.component();
            page.highlight("paste");
            assertThat(page.panel().search.getText()).isEqualTo("paste");
            page.highlight("");
            assertThat(page.panel().search.getText()).as("clearing Settings' search clears what it put there").isEmpty();
            var action = contributions.addAction("dev.test.hello", "Hello", null, List.of(), Optional.of("cmd+alt+h"), ignored -> { });
            page.panel().search.setText("hello");
            assertThat(page.panel().tree.entryAt(1).group()).isEqualTo("Test Plugin");
            assertThat(page.searchTerms()).contains("Test Plugin");
            action.close();
            assertThat(page.panel().tree.getRowCount()).isZero();
            page.dispose();
        });
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `./gradlew :jasper-app:test --tests 'dev.jasper.app.shortcuthelp.KeymapPageTest'`
Expected: compilation fails: `KeymapPage` does not exist.

- [ ] **Step 3: Implement**

Create `KeymapPage.java`:

```java
package dev.jasper.app.shortcuthelp;

import dev.jasper.app.config.KeyBindings;
import dev.jasper.app.contributions.Contributions;
import dev.jasper.app.lifecycle.Subscription;
import dev.jasper.app.settingsui.FillingPage;
import dev.jasper.app.settingsui.PageContext;
import dev.jasper.app.settingsui.SettingsPage;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;
import java.util.function.Supplier;
import javax.swing.JComponent;
import javax.swing.SwingUtilities;

/**
 * The Keymap page of Settings: the shortcut editor. Set stages an edit; Apply writes every staged edit in one save.
 * SettingsDialog owns each page and disposes it, which closes its subscription and key recorder. EDT only.
 */
public final class KeymapPage implements SettingsPage {
    /** The page id. */
    public static final String ID = "keymap";

    /**
     * What the page reads and writes.
     *
     * @param contributions the contributed actions; their changes refresh the rows
     * @param overrides the saved {@code [keybindings]} overrides
     * @param pluginNames plugin ids to display names, for grouping plugin actions
     * @param macOs the platform the bindings are parsed for
     * @param save writes shortcut edits (action id to a binding, or empty for the default); completes on any thread
     */
    public record Hooks(Contributions contributions, Supplier<Map<String, String>> overrides,
                        Supplier<Map<String, String>> pluginNames, boolean macOs,
                        Function<Map<String, Optional<String>>, CompletableFuture<Void>> save) {
        public Hooks {
            Objects.requireNonNull(contributions); Objects.requireNonNull(overrides);
            Objects.requireNonNull(pluginNames); Objects.requireNonNull(save);
        }
    }

    private final Hooks hooks;
    private final PageContext context;
    private final KeymapDraft draft = new KeymapDraft();
    private final Subscription listening;
    private ShortcutPanel panel;
    private FillingPage body;
    private String highlighted;

    public KeymapPage(Hooks hooks, PageContext context) {
        this.hooks = Objects.requireNonNull(hooks);
        this.context = Objects.requireNonNull(context);
        listening = hooks.contributions().onChanged(kind -> { if (kind == Contributions.Kind.ACTIONS) refreshRows(); });
    }

    ShortcutPanel panel() { return panel; }

    @Override public String id() { return ID; }
    @Override public String title() { return "Keymap"; }
    @Override public Optional<String> parent() { return Optional.empty(); }
    @Override public String helpAnchor() { return "shortcuts"; }

    /** The page title, every group name, and each action's name with its binding. */
    @Override public List<String> searchTerms() {
        var terms = new ArrayList<String>(List.of("Keymap", "keyboard shortcuts keys bindings"));
        var groups = new LinkedHashSet<String>();
        for (ShortcutEntry row : rows()) {
            groups.add(row.group());
            terms.add(row.name() + " " + ShortcutText.format(row.stroke()));
        }
        terms.addAll(groups);
        return List.copyOf(terms);
    }

    @Override public JComponent component() {
        if (body != null) return body;
        panel = new ShortcutPanel(hooks.macOs(), this::stage);
        panel.setRows(rows());
        body = new FillingPage(panel);
        return body;
    }

    @Override public boolean modified() { return draft.modified(); }

    @Override public void reset() {
        draft.clear();
        refreshRows();
        context.changed().run();
    }

    /** Writes every staged edit at once; edits staged after this call stay staged. */
    @Override public CompletableFuture<Void> apply() {
        if (!draft.modified()) return CompletableFuture.completedFuture(null);
        var edits = draft.edits();
        var done = new CompletableFuture<Void>();
        CompletableFuture<Void> saving;
        try { saving = hooks.save().apply(edits); }
        catch (RuntimeException failure) { saving = CompletableFuture.failedFuture(failure); }
        saving.whenComplete((ignored, failure) -> SwingUtilities.invokeLater(() -> {
            if (failure != null) { done.completeExceptionally(failure); return; }
            draft.saved(edits);
            refreshRows();
            done.complete(null);
        }));
        return done;
    }

    /** Puts a Settings search into the page's own search field, and takes it out again when Settings' search clears. */
    @Override public void highlight(String query) {
        if (panel == null) return;
        String text = query == null ? "" : query.strip();
        if (!text.isEmpty()) {
            panel.search.setText(text);
            highlighted = text;
        } else if (highlighted != null && panel.search.getText().equals(highlighted)) {
            panel.search.setText("");
            highlighted = null;
        }
    }

    /** The saved bindings changed: staged edits they now hold are dropped and the rows are rebuilt. */
    @Override public void refresh() {
        draft.prune(hooks.overrides().get());
        refreshRows();
        context.changed().run();
    }

    @Override public void dispose() {
        listening.close();
        if (panel != null) panel.close();
    }

    /** The edit card's Set: refuses a staging that would collide, otherwise stages it and shows it. */
    private CompletableFuture<Void> stage(Map<String, Optional<String>> changes) {
        var saved = hooks.overrides().get();
        try { KeyBindings.withOverrides(hooks.macOs(), draft.preview(saved, changes)); }
        catch (IllegalArgumentException collision) { return CompletableFuture.failedFuture(collision); }
        draft.stage(changes, saved);
        refreshRows();
        context.changed().run();
        return CompletableFuture.completedFuture(null);
    }

    /** The catalog over the saved overrides plus staged edits; the saved ones alone if a later reload made those collide. */
    private List<ShortcutEntry> rows() {
        var saved = hooks.overrides().get();
        KeyBindings base;
        try { base = KeyBindings.withOverrides(hooks.macOs(), draft.overrides(saved)); }
        catch (IllegalArgumentException collision) { base = KeyBindings.withOverrides(hooks.macOs(), saved); }
        return ShortcutCatalog.rows(base, hooks.contributions().actions(), hooks.pluginNames().get(), hooks.macOs());
    }

    private void refreshRows() { if (panel != null) panel.setRows(rows()); }
}
```

Replace `shortcuthelp/package-info.java` with:

```java
/**
 * EDT keyboard shortcut editor: effective catalog, grouped searchable tree, staged edits and window-scoped recorder,
 * shown as the Settings dialog's Keymap page. SettingsDialog owns each KeymapPage; disposing it closes its
 * subscription and recorder. Application owns ShortcutHelp until the Keymap page replaces it.
 * Allowed outgoing Jasper dependencies: commands, config, contributions, lifecycle, platform, settingsui, windows.
 * No SDK types or plugin implementation types cross this boundary.
 */
package dev.jasper.app.shortcuthelp;
```

- [ ] **Step 4: Run the tests to verify they pass**

Run: `./gradlew :jasper-app:test --tests 'dev.jasper.app.shortcuthelp.*' --tests 'dev.jasper.app.workspace.ShortcutEditorIntegrationTest'`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git branch --show-current   # must print claude/settings-dialog-b2
git add jasper-app/src/main/java/dev/jasper/app/shortcuthelp jasper-app/src/test/java/dev/jasper/app/shortcuthelp
git commit -m "Add the Keymap settings page: Set stages shortcuts and Apply saves them at once

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 4: The Plugins page, and Keymap and Plugins wired into Settings

**Files:**
- Create: `jasper-app/src/main/java/dev/jasper/app/pluginmanager/PluginsPage.java`
- Modify: `jasper-app/src/main/java/dev/jasper/app/pluginmanager/PluginManager.java` (replace)
- Modify: `jasper-app/src/main/java/dev/jasper/app/pluginmanager/package-info.java`
- Modify: `jasper-app/src/main/java/dev/jasper/app/settingsui/CorePages.java`
- Delete: `jasper-app/src/main/java/dev/jasper/app/settingsui/LauncherPage.java`
- Delete: `jasper-app/src/main/java/dev/jasper/app/shortcuthelp/ShortcutHelp.java`
- Modify: `jasper-app/src/main/java/dev/jasper/app/shortcuthelp/package-info.java`
- Modify: `jasper-app/src/main/java/dev/jasper/app/application/JasperApplication.java`
- Test: `shortcuthelp/ShortcutTestSupport.java`, `pluginmanager/PluginManagerTest.java`, `settingsui/CorePagesTest.java`, `settingsui/SettingsDialogTest.java`,
  `settingsui/SchemaPageTest.java`, `settingsui/SettingsDialogPreview.java`, `shortcuthelp/ShortcutHelpTest.java`,
  `workspace/ShortcutEditorIntegrationTest.java`

**Interfaces:**
- Consumes: `KeymapPage`, `KeymapPage.Hooks`, `FillingPage`, `PageContext.surface()`, `SettingsPage.selected()`,
  `SearchText.matches(String, String)`.
- Produces: `public SettingsPage PluginManager.page(PageContext context)`; package-private `PluginManagerPanel attach(Supplier<AuxiliarySurface>)`,
  `void detach(PluginManagerPanel)`, `void refresh()`, `List<PluginRuntime.Row> rows()`.
  `PluginManager.open()` and `WINDOW_ID` are removed.
- Produces: `public final class PluginsPage implements SettingsPage` with `public static final String ID = "plugins"`.
- Produces: `CorePages.pages(Supplier<List<String>> themeNames, SystemActions system, Function<PageContext, SettingsPage> keymap, Function<PageContext, SettingsPage> plugins)`.

- [ ] **Step 1: Write the failing tests**

`PluginManagerTest.java`: host the manager in a page over a stand-in Settings surface.
- Add imports `dev.jasper.app.settingsui.PageContext` and `dev.jasper.app.settingsui.SettingsPage`.
- Add fields `private SettingsPage page;` and `private AuxiliarySurface host;`.
- Add the helper:

```java
    /** Opens the Plugins page as Settings would: over a Settings surface, built, then shown. */
    private void open() throws Exception {
        edt(() -> {
            host = windows.window("app.settings", "Settings", new java.awt.Dimension(1000, 700), true);
            page = manager.page(new PageContext(null, id -> { }, Runnable::run, (title, initial) -> Optional.empty(), () -> { },
                () -> host));
            page.component();
            page.selected();
        });
    }
```

- In `stop()`, replace `edt(() -> windows.close());` with `edt(() -> { if (page != null) page.dispose(); windows.close(); });`.
- Replace every `edt(manager::open);` with `open();`.
- In `installsAZipAfterConsentAndOffersTheRestart`, replace

```java
            assertThat(windows.open()).singleElement().satisfies(surface -> {
                assertThat(surface.id()).isEqualTo(PluginManager.WINDOW_ID);
                assertThat(surface.title()).isEqualTo("Plugins");
            });
```

  with

```java
            assertThat(windows.open()).as("the manager opens no window of its own").containsExactly(host);
            assertThat(page.id()).isEqualTo("plugins");
            assertThat(page.modified()).isFalse();
```

  and `.as("the consent dialog belongs to the manager window")` with `.as("the consent dialog opens over Settings")`.
- Replace both `edt(() -> windows.open().get(0).notifyActivated());` with `edt(() -> page.selected());`, and the
  comment `// Another process disables it; the manager notices when its window regains focus.` with
  `// Another process disables it; the page notices when it is shown again.`
- Add:

```java
    @Test void thePageSearchesPluginNamesSelectsAMatchAndLetsGoWhenDisposed() throws Exception {
        PluginJars.build(user().resolve("dev.example.found/jars"), "main.jar",
            PluginJars.descriptor("dev.example.found", "1.0.0", "fix.Main"), Map.of(), List.of());
        start(false, false);
        open();
        until(() -> manager.panel().listed().size() == 1);
        edt(() -> {
            assertThat(page.searchTerms()).anySatisfy(term -> assertThat(term).contains("dev.example.found"));
            page.highlight("example found");
            assertThat(manager.panel().selected()).isEqualTo("dev.example.found");
            page.dispose();
            assertThat(manager.panel()).isNull();
        });
        page = null;
    }
```

`CorePagesTest.java`: replace both calls
`CorePages.pages(() -> List.of("Jasper Dark"), system, () -> calls.add("keymap"), () -> calls.add("plugins"))` and
`CorePages.pages(() -> List.of("Jasper Dark"), system, () -> { }, () -> { })` with
`CorePages.pages(() -> List.of("Jasper Dark"), system, CorePagesTest::keymap, CorePagesTest::plugins)`, and add:

```java
    private static SettingsPage keymap(PageContext context) {
        return new GroupPage("keymap", "Keymap", Optional.empty(), false, List::of, context);
    }

    private static SettingsPage plugins(PageContext context) {
        return new GroupPage("plugins", "Plugins", Optional.empty(), false, List::of, context);
    }
```

`SettingsDialogTest.java`: replace the whole page factory in `dialog()`:

```java
        return new SettingsDialog(windows, context -> {
            contexts.add(context);
            var pages = new ArrayList<>(CorePages.pages(() -> List.of("Jasper Dark"), new CorePages.SystemActions(() -> FILE,
                () -> { }, () -> { }, () -> { }, () -> { }, () -> false), () -> { }, () -> { }).apply(context));
            pages.set(pages.indexOf(pages.stream().filter(page -> page.id().equals("keymap")).findFirst().orElseThrow()), fake);
            return pages;
        }, hooks);
```

with:

```java
        return new SettingsDialog(windows, context -> {
            contexts.add(context);
            return CorePages.pages(() -> List.of("Jasper Dark"), new CorePages.SystemActions(() -> FILE,
                    () -> { }, () -> { }, () -> { }, () -> { }, () -> false), page -> fake,
                page -> new GroupPage("plugins", "Plugins", Optional.empty(), false, List::of, page)).apply(context);
        }, hooks);
```

`SchemaPageTest.java`: delete the test `launcherPagesCloseTheDialogThenOpenTheirWindow`, and delete
`import javax.swing.JButton;` if `grep -n JButton` finds no other use in the file.

`SettingsDialogPreview.java`: replace `() -> { }, () -> { }),` at the end of the `CorePages.pages(...)` call (the two
launcher arguments) with

```java
                    context -> new GroupPage("keymap", "Keymap", Optional.empty(), false, List::of, context),
                    context -> new GroupPage("plugins", "Plugins", Optional.empty(), false, List::of, context)),
```

(Task 7 replaces these stand-ins with the real pages.)

`ShortcutHelpTest.java`: delete the test `windowIsReusedAndRefreshesLiveWithoutLosingSearchAndReleasesItsListener`
(its live refresh is now covered by `KeymapPageTest`) and the imports only it used:
`dev.jasper.app.persistence.UiState`, `dev.jasper.app.windows.AuxiliarySurface`, `dev.jasper.app.windows.AuxiliaryWindows`,
`java.awt.Rectangle`.

In `ShortcutTestSupport.java`, delete the window forms `save(AuxiliarySurface, String, KeyStroke)` and
`editing(AuxiliarySurface)` and the `AuxiliarySurface` import. Replace `ShortcutEditorIntegrationTest.java` with:

```java
package dev.jasper.app.workspace;

import dev.jasper.app.application.ConfigurationTestSupport;
import dev.jasper.app.appearance.ThemeController;
import dev.jasper.app.commands.ActionId;
import dev.jasper.app.config.ConfigService;
import dev.jasper.app.contributions.Contributions;
import dev.jasper.app.palette.PaletteScope;
import dev.jasper.app.palette.PaletteTestSupport;
import dev.jasper.app.settingsui.PageContext;
import dev.jasper.app.shortcuthelp.KeymapPage;
import dev.jasper.app.shortcuthelp.ShortcutTestSupport;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import javax.swing.Action;
import javax.swing.JRootPane;
import javax.swing.KeyStroke;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static dev.jasper.app.workspace.DesktopTestSupport.edt;
import static org.assertj.core.api.Assertions.*;

class ShortcutEditorIntegrationTest {
    @TempDir Path directory;

    @Test void thePaletteOpensKeymapAndApplyChangesBothWindowsAndDisk() throws Exception {
        Path file = directory.resolve("config.toml");
        try (var service = new ConfigService(file, true)) {
            WindowContent[] owners = new WindowContent[2];
            KeymapPage[] page = new KeymapPage[1];
            ConfigurationTestSupport[] config = new ConfigurationTestSupport[1];
            var applied = new AtomicReference<CompletableFuture<Void>>();
            int[] opened = {0};
            try {
                edt(() -> {
                    var themes = new ThemeController();
                    config[0] = new ConfigurationTestSupport(themes, service);
                    var contributions = new Contributions();
                    page[0] = new KeymapPage(new KeymapPage.Hooks(contributions, () -> config[0].snapshot().keybindings(), Map::of, true,
                        changes -> config[0].saveKeybindings(changes, List.of())),
                        new PageContext(null, id -> { }, Runnable::run, (title, initial) -> Optional.empty(), () -> { }));
                    page[0].component();
                    config[0].onSnapshot(snapshot -> page[0].refresh());
                    contributions.addAction("app.shortcuts", "Keyboard Shortcuts…", null, List.of("keymap"), Optional.empty(),
                        ignored -> opened[0]++);
                    for (int i = 0; i < owners.length; i++) {
                        owners[i] = DesktopTestSupport.content(DesktopTestSupport.launcher(new ArrayDeque<>()));
                        var root = new JRootPane(); root.setContentPane(owners[i]); owners[i].installRootBindings(root);
                        config[0].register(owners[i]); owners[i].connectContributions(contributions);
                    }
                    var palette = owners[0].commandPalette();
                    palette.open(PaletteScope.COMMANDS_ID);
                    palette.component().queryField().setText("keyboard shortcuts");
                    assertThat(PaletteTestSupport.selectedRow(palette.component()).id()).isEqualTo("app.shortcuts");
                    PaletteTestSupport.executeSelected(palette.component());
                    assertThat(palette.isOpen()).isFalse();
                    assertThat(opened[0]).isEqualTo(1);
                    ShortcutTestSupport.set(page[0], "New tab", KeyStroke.getKeyStroke("ctrl alt N"));
                });
                assertThat(Files.exists(file) ? Files.readString(file) : "").as("Set writes nothing").doesNotContain("ctrl+alt+n");
                edt(() -> {
                    assertThat(page[0].modified()).isTrue();
                    assertThat(ShortcutTestSupport.editing(page[0])).isFalse();
                    applied.set(page[0].apply());
                });
                applied.get().get(5, TimeUnit.SECONDS);
                edt(() -> {}); // Drain publication.
                edt(() -> {
                    for (var owner : owners)
                        assertThat(owner.action(ActionId.NEW_TAB).getValue(Action.ACCELERATOR_KEY)).isEqualTo(KeyStroke.getKeyStroke("ctrl alt N"));
                    assertThat(page[0].modified()).isFalse();
                });
                assertThat(Files.readString(file)).contains("ctrl+alt+n");
            } finally {
                edt(() -> {
                    if (page[0] != null) page[0].dispose();
                    for (var owner : owners) if (owner != null) owner.close();
                    if (config[0] != null) config[0].close();
                });
            }
        }
    }
}
```

- [ ] **Step 2: Run the tests to verify they fail**

Run: `./gradlew :jasper-app:test --tests 'dev.jasper.app.pluginmanager.*' --tests 'dev.jasper.app.settingsui.*'`
Expected: compilation fails: `PluginManager.page` does not exist and `CorePages.pages` takes `Runnable`s.

- [ ] **Step 3: Implement**

Replace `PluginManager.java` with:

```java
package dev.jasper.app.pluginmanager;

import dev.jasper.app.plugins.PluginRuntime;
import dev.jasper.app.restart.ResidentControl;
import dev.jasper.app.restart.RestartFlow;
import dev.jasper.app.settingsui.PageContext;
import dev.jasper.app.settingsui.SettingsPage;
import dev.jasper.app.windows.AuxiliarySurface;
import dev.jasper.app.windows.AuxiliaryWindows;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.Executor;
import java.util.function.Function;
import java.util.function.Supplier;
import javax.swing.SwingUtilities;

/**
 * Connects the Settings dialog's Plugins page to the runtime's operations and to the restart conversation. Every change
 * takes effect at the next launch, so the manager's main job after an operation is to say whether a restart is due.
 * EDT only.
 */
public final class PluginManager {
    /**
     * What the manager needs from the application.
     *
     * @param chooseZip asks the user for a plugin zip over the Settings dialog; empty when cancelled
     * @param restarts the restart conversation
     * @param quit quits Jasper, for when it cannot restart itself
     * @param resident the resident process, probed for the standalone notice
     * @param standaloneNotice whether this process is a {@code --standalone} replacement that should say when a resident still runs
     * @param worker runs the blocking resident probe
     */
    public record Hooks(Function<AuxiliarySurface, Optional<Path>> chooseZip, RestartFlow restarts, Runnable quit,
                        ResidentControl resident, boolean standaloneNotice, Executor worker,
                        java.util.function.Consumer<Path> openInEditor, java.util.function.Consumer<Path> reveal) {
        /** Rejects nulls. */
        public Hooks {
            Objects.requireNonNull(chooseZip); Objects.requireNonNull(restarts); Objects.requireNonNull(quit);
            Objects.requireNonNull(resident); Objects.requireNonNull(worker);
            Objects.requireNonNull(openInEditor); Objects.requireNonNull(reveal);
        }
    }

    private final PluginRuntime runtime;
    private final AuxiliaryWindows windows;
    private final Hooks hooks;
    private Supplier<AuxiliarySurface> host;
    private PluginManagerPanel panel;
    private PluginRuntime.Snapshot snapshot = new PluginRuntime.Snapshot(List.of(), false, false);
    private AuxiliarySurface consentDialog;
    private ConsentView consent;
    private ConfirmView confirm;
    private AuxiliarySurface confirmDialog;

    /**
     * Creates the manager; nothing is shown until Settings shows its Plugins page.
     *
     * @param runtime the plugin runtime
     * @param windows builds the consent and confirm dialogs
     * @param hooks what the application provides
     */
    public PluginManager(PluginRuntime runtime, AuxiliaryWindows windows, Hooks hooks) {
        this.runtime = Objects.requireNonNull(runtime); this.windows = Objects.requireNonNull(windows);
        this.hooks = Objects.requireNonNull(hooks);
        hooks.restarts().onChanged(this::renderBanner);
    }

    PluginManagerPanel panel() { return panel; }
    ConsentView consent() { return consent; }
    ConfirmView confirm() { return confirm; }

    /** The Plugins page for one opening of Settings; it lists the plugins afresh. */
    public SettingsPage page(PageContext context) { return new PluginsPage(this, context); }

    /** Builds the list for a page whose dialogs open over {@code host}; a later attachment replaces this one. */
    PluginManagerPanel attach(Supplier<AuxiliarySurface> host) {
        this.host = Objects.requireNonNull(host);
        panel = new PluginManagerPanel(new PluginManagerPanel.Handlers(this::toggle, this::review, this::remove, this::discardInstall,
            this::install, this::openSettings, this::openFolder, this::openData));
        panel.show(snapshot);
        renderBanner();
        return panel;
    }

    /** The page is gone: forget its list and end any restart conversation. */
    void detach(PluginManagerPanel detached) {
        if (panel != detached) return;
        panel = null;
        host = null;
        hooks.restarts().cancel();
    }

    /** The plugins as last listed. */
    List<PluginRuntime.Row> rows() { return snapshot.rows(); }

    /** Lists the plugins again: another process may have changed plugins.toml since the page was last shown. */
    void refresh() {
        runtime.snapshot(this::accept);
        if (panel == null || !hooks.standaloneNotice()) return;
        hooks.worker().execute(() -> {
            boolean live = hooks.resident().live().getAsBoolean();
            SwingUtilities.invokeLater(() -> {
                if (panel != null) panel.notice(live
                    ? "Another Jasper process is still running with the plugin set it started with. Quit it for your changes to apply there." : "");
            });
        });
    }

    private void accept(PluginRuntime.Outcome outcome) { accept(outcome, ""); }

    private void accept(PluginRuntime.Outcome outcome, String success) {
        if (outcome.ok()) snapshot = outcome.snapshot();
        if (panel == null) return;
        panel.busy(false);
        if (!outcome.ok()) { panel.message(outcome.message(), true); return; }
        panel.show(snapshot);
        panel.message(success, false);
        renderBanner();
    }

    private void renderBanner() {
        if (panel == null) return;
        RestartFlow flow = hooks.restarts();
        var launchAnyway = new PluginManagerPanel.BannerAction("Launch Anyway", flow::launchAnyway);
        var cancel = new PluginManagerPanel.BannerAction("Cancel", flow::cancel);
        switch (flow.state()) {
            case UNAVAILABLE -> panel.banner("Jasper could not work out how it was started. Quit and reopen it to apply your changes.",
                List.of(new PluginManagerPanel.BannerAction("Quit Jasper", hooks.quit())));
            case PROBING -> panel.banner("Looking for another Jasper process…", List.of());
            case RESIDENT_FOUND -> panel.banner("Another Jasper process is running with the previous plugin set. It has to quit before a normal restart can apply your changes.",
                List.of(new PluginManagerPanel.BannerAction("Quit It and Restart", flow::askResidentToQuit), launchAnyway, cancel));
            case WAITING -> panel.banner("Waiting for the other Jasper process to quit…", List.of(launchAnyway, cancel));
            case RESIDENT_STUCK -> panel.banner("The other Jasper process did not quit. Launch Anyway starts a separate Jasper with your changes; the other one keeps the previous plugin set until you quit it.",
                List.of(new PluginManagerPanel.BannerAction("Try Again", flow::askResidentToQuit), launchAnyway, cancel));
            case IDLE -> {
                if (snapshot.safeMode()) panel.banner("Safe mode: installed plugins are not loaded.",
                    List.of(new PluginManagerPanel.BannerAction("Restart Normally", flow::restartNormally)));
                else if (snapshot.restartNeeded()) panel.banner("Restart Jasper to apply your changes.",
                    List.of(new PluginManagerPanel.BannerAction("Restart Now", flow::restartNow)));
                else panel.banner("", List.of());
            }
        }
    }

    private void toggle(PluginRuntime.Row row) {
        panel.busy(true);
        runtime.setEnabled(row.id(), !row.enabled(), this::accept);
    }

    private void remove(PluginRuntime.Row row) {
        if (row.pendingRemoval()) { panel.busy(true); runtime.remove(row.id(), false, this::accept); return; }
        AuxiliarySurface dialog = windows.dialog("Remove " + row.name(), true, host.get());
        var view = new ConfirmView("Remove " + row.name() + " " + row.version() + "?\n\nIts jars, settings and data in " + row.directory()
            + " are deleted the next time Jasper starts. Keep undoes this until then.", "Remove",
            () -> { dialog.close(); panel.busy(true); runtime.remove(row.id(), true, this::accept); }, dialog::close);
        dialog.onClosed(() -> { if (confirmDialog == dialog) { confirmDialog = null; confirm = null; } });
        dialog.setContent(view);
        confirmDialog = dialog;
        confirm = view;
        dialog.show();
    }

    /** Off the EDT: seeds the file if needed, then hands it to the editor; a failure is shown on the page. */
    private void openSettings(PluginRuntime.Row row) {
        hooks.worker().execute(() -> {
            try { runtime.prepareSettings(row.id()); hooks.openInEditor().accept(row.settingsFile()); }
            catch (java.io.IOException | RuntimeException failure) { failed(failure); }
        });
    }

    private void openFolder(PluginRuntime.Row row) { reveal(row.directory()); }
    private void openData(PluginRuntime.Row row) { reveal(row.dataDirectory()); }

    private void reveal(Path directory) {
        hooks.worker().execute(() -> {
            try { java.nio.file.Files.createDirectories(directory); hooks.reveal().accept(directory); }
            catch (java.io.IOException | RuntimeException failure) { failed(failure); }
        });
    }

    private void failed(Exception failure) {
        SwingUtilities.invokeLater(() -> { if (panel != null) panel.message(failure.getMessage() == null ? failure.toString() : failure.getMessage(), true); });
    }

    private void discardInstall(PluginRuntime.Row row) {
        panel.busy(true);
        runtime.discardInstall(row.id(), this::accept);
    }

    private void review(PluginRuntime.Row row) {
        ask("Review " + row.name(), row.name(), row.version(), row.vendor(), row.capabilities(), false, "Allow and Enable", () -> {
            panel.busy(true);
            runtime.consent(row.id(), row.capabilities(), this::accept);
        }, () -> { });
    }

    private void install() {
        Optional<Path> zip = hooks.chooseZip().apply(host.get());
        if (zip.isEmpty()) return;
        panel.busy(true);
        panel.message("", false);
        runtime.inspect(zip.get(), (inspection, problem) -> {
            if (panel == null) { if (inspection != null) runtime.discard(inspection); return; }
            panel.busy(false);
            if (inspection == null) { panel.message(problem, true); return; }
            ask("Install " + inspection.name(), inspection.name(), inspection.version(), inspection.vendor(),
                inspection.capabilities(), inspection.update(), "Install", () -> {
                    panel.busy(true);
                    runtime.install(inspection, outcome -> accept(outcome,
                        inspection.name() + " " + inspection.version() + " will be installed when Jasper restarts."));
                }, () -> runtime.discard(inspection));
        });
    }

    /**
     * A modal consent dialog over Settings. The decision is taken in the button callbacks rather than after
     * {@code show()} returns: a modal show blocks until the dialog closes, and closing it by its title bar is a refusal.
     */
    private void ask(String title, String name, String version, String vendor, List<String> capabilities, boolean update,
                     String allowLabel, Runnable allowed, Runnable refused) {
        AuxiliarySurface dialog = windows.dialog(title, true, host.get());
        boolean[] decided = {false};
        var view = new ConsentView(name, version, vendor, capabilities, update, allowLabel,
            () -> { decided[0] = true; dialog.close(); allowed.run(); }, dialog::close);
        dialog.onClosed(() -> {
            if (consentDialog == dialog) { consentDialog = null; consent = null; }
            if (!decided[0]) refused.run();
        });
        dialog.setContent(view);
        consentDialog = dialog;
        consent = view;
        dialog.show();
    }
}
```

The banner strings are unchanged except `"Looking for another Jasper process…"` and `"Waiting for … quit…"`, whose
ellipsis is now the escape `…` (the same character). Check that the original file used U+2026 there
(`grep -n "process…" PluginManager.java` before replacing); if it used three dots, keep three dots.

Create `PluginsPage.java`:

```java
package dev.jasper.app.pluginmanager;

import dev.jasper.app.plugins.PluginRuntime;
import dev.jasper.app.settingsui.FillingPage;
import dev.jasper.app.settingsui.PageContext;
import dev.jasper.app.settingsui.SearchText;
import dev.jasper.app.settingsui.SettingsPage;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import javax.swing.JComponent;

/**
 * The Plugins page of Settings. Its operations apply at once and take effect after a restart, so it is never
 * modified and Apply has nothing to do for it. SettingsDialog owns each page; disposing it detaches the list. EDT only.
 */
public final class PluginsPage implements SettingsPage {
    /** The page id. */
    public static final String ID = "plugins";

    private final PluginManager manager;
    private final PageContext context;
    private PluginManagerPanel panel;
    private FillingPage body;

    PluginsPage(PluginManager manager, PageContext context) {
        this.manager = manager;
        this.context = context;
        manager.refresh();
    }

    @Override public String id() { return ID; }
    @Override public String title() { return "Plugins"; }
    @Override public Optional<String> parent() { return Optional.empty(); }
    @Override public String helpAnchor() { return "plugins"; }

    /** The page title and each listed plugin's name and id. */
    @Override public List<String> searchTerms() {
        var terms = new ArrayList<String>(List.of("Plugins", "extensions install enable disable remove safe mode"));
        for (PluginRuntime.Row row : manager.rows()) terms.add(row.name() + " " + row.id());
        return List.copyOf(terms);
    }

    @Override public JComponent component() {
        if (body != null) return body;
        panel = manager.attach(context.surface());
        body = new FillingPage(panel);
        return body;
    }

    @Override public boolean modified() { return false; }
    @Override public void reset() { }
    @Override public CompletableFuture<Void> apply() { return CompletableFuture.completedFuture(null); }

    /** Selects the first plugin whose name or id matches. */
    @Override public void highlight(String query) {
        if (panel == null || query == null || query.isBlank()) return;
        manager.rows().stream().filter(row -> SearchText.matches(row.name() + " " + row.id(), query)).findFirst()
            .ifPresent(row -> panel.select(row.id()));
    }

    @Override public void selected() { manager.refresh(); }

    @Override public void dispose() { if (panel != null) manager.detach(panel); }
}
```

Replace `pluginmanager/package-info.java` with:

```java
/**
 * The Settings dialog's Plugins page: the plugin list, consent and confirm dialogs over Settings, and an EDT controller
 * that drives the runtime's manager operations and the restart conversation. It sees only app-native values. The
 * application owns the {@link dev.jasper.app.pluginmanager.PluginManager}; SettingsDialog owns each PluginsPage.
 * <p>Allowed outgoing Jasper dependencies: dev.jasper.app.plugins, dev.jasper.app.restart, dev.jasper.app.settingsui,
 * dev.jasper.app.windows.
 * The module architecture check forbids package cycles; app types are not an external plugin API.
 */
package dev.jasper.app.pluginmanager;
```

In `CorePages.java`, replace the whole `pages` method and its comment with:

```java
    /**
     * The whole tree for one dialog, in display order: group and schema pages, with the Keymap and Plugins pages the
     * application supplies.
     */
    public static Function<PageContext, List<SettingsPage>> pages(Supplier<List<String>> themeNames, SystemActions system,
                                                                   Function<PageContext, SettingsPage> keymap,
                                                                   Function<PageContext, SettingsPage> plugins) {
        return context -> {
            var specs = specs(themeNames, system);
            var all = new ArrayList<SettingsPage>();
            Function<String, Supplier<List<SettingsPage>>> childrenOf = id ->
                () -> all.stream().filter(page -> page.parent().equals(Optional.of(id))).toList();
            all.add(new GroupPage("appearance-behavior", "Appearance & Behavior", Optional.empty(), false,
                childrenOf.apply("appearance-behavior"), context));
            for (var spec : specs.subList(0, 6)) all.add(spec.build(context));
            all.add(keymap.apply(context));
            for (var spec : specs.subList(6, specs.size())) all.add(spec.build(context));
            all.add(plugins.apply(context));
            all.add(new GroupPage("tools", "Tools", Optional.empty(), true, childrenOf.apply("tools"), context));
            return List.copyOf(all);
        };
    }
```

Delete `settingsui/LauncherPage.java` and `shortcuthelp/ShortcutHelp.java`. In `shortcuthelp/package-info.java`,
replace the sentence `Application owns ShortcutHelp until the Keymap page replaces it.` with nothing and replace
`Allowed outgoing Jasper dependencies: commands, config, contributions, lifecycle, platform, settingsui, windows.` with
`Allowed outgoing Jasper dependencies: commands, config, contributions, lifecycle, platform, settingsui.`

In `JasperApplication.java`:
1. Delete the field `private dev.jasper.app.shortcuthelp.ShortcutHelp shortcutHelp;`.
2. In the `configuration.onSnapshot(...)` listener in the constructor, delete the line
   `if (shortcutHelp != null) shortcutHelp.refresh();` (the Keymap page refreshes from Settings' state listener).
3. In `startPlugins`, replace `Optional.empty(), invocation -> managePlugins());` (the `plugins.manage` action) with
   `Optional.empty(), invocation -> openSettings(null, dev.jasper.app.pluginmanager.PluginsPage.ID));`.
4. Replace the block from `shortcutHelp = new dev.jasper.app.shortcuthelp.ShortcutHelp(auxiliary, contributions,` through
   `configuration.settingsOpener(owner -> openSettings(owner.id(), null));` and its closing `}` with:

```java
        var keymap = new dev.jasper.app.shortcuthelp.KeymapPage.Hooks(contributions,
            () -> (configuration == null ? ConfigSnapshot.defaults() : configuration.snapshot()).keybindings(),
            plugins::pluginNames, macOs, changes -> configuration == null
                ? java.util.concurrent.CompletableFuture.failedFuture(new IllegalStateException("Settings are unavailable."))
                : configuration.saveKeybindings(changes, contributions.actions().stream()
                    .filter(a -> KeyBindings.extensionId(a.id()))
                    .map(a -> new KeyBindings.Extension(a.id(), a.defaultBinding())).toList()));
        contributions.addAction("app.shortcuts", "Keyboard Shortcuts…", null, List.of("help", "keys", "bindings", "keymap", "edit shortcuts", "keyboard settings"),
            Optional.empty(), invocation -> openSettings(null, dev.jasper.app.shortcuthelp.KeymapPage.ID));
        contributions.addMenuSection(MenuTarget.topLevel("app.help", "Help")).set(List.of(new MenuEntry.Item("app.shortcuts")));
        if (configuration != null) {
            var system = new dev.jasper.app.settingsui.CorePages.SystemActions(() -> configuration.state().file(),
                this::openConfigFile, this::revealConfigFile, () -> configuration.reload(), this::migrateConfiguration,
                configuration::migrationAvailable);
            settings = new dev.jasper.app.settingsui.SettingsDialog(auxiliary,
                dev.jasper.app.settingsui.CorePages.pages(() -> themeCatalog.latest().names(), system,
                    context -> new dev.jasper.app.shortcuthelp.KeymapPage(keymap, context), context -> pluginManager().page(context)),
                new dev.jasper.app.settingsui.SettingsDialog.Hooks(configuration::save, configuration::state, configuration::onState,
                    this::openConfigFile, this::migrateConfiguration, this::browse, dev.jasper.app.settingsui.SettingsDialog::askDiscard));
            configuration.settingsOpener(owner -> openSettings(owner.id(), null));
        }
```

   (Keep the existing `"Keyboard Shortcuts…"` literal byte for byte; it is not new code.)
5. Replace the method `private void managePlugins() { … pluginManager.open(); }` with:

```java
    /** The Plugins page's controller, created the first time Settings builds that page. */
    private PluginManager pluginManager() {
        if (pluginManager == null) {
            java.util.concurrent.Executor worker = work -> Thread.ofPlatform().daemon().name("jasper-restart").start(work);
            // Only a plain replacement can be swallowed by a resident; any other is standalone and needs no conversation.
            var flow = new RestartFlow(replacementHandsOff ? residentControl : ResidentControl.NONE, this::restart, worker,
                SwingUtilities::invokeLater, Duration.ofSeconds(30), Duration.ofMillis(250));
            var editor = new dev.jasper.app.platform.ConfigEditor();
            pluginManager = new PluginManager(plugins, auxiliary, new PluginManager.Hooks(
                surface -> shells.chooseFile(surface, "Install Plugin", ".zip"), flow, this::quit, residentControl, standaloneNotice, worker,
                editor::open, editor::reveal));
        }
        return pluginManager;
    }
```

   Keep the body identical to the old `managePlugins` apart from the missing `open()` call and the early return: the
   Settings dialog exists only after `startPlugins`, so `plugins` is set whenever this runs.

- [ ] **Step 4: Run the tests to verify they pass**

Run: `./gradlew :jasper-app:test --tests 'dev.jasper.app.pluginmanager.*' --tests 'dev.jasper.app.settingsui.*' --tests 'dev.jasper.app.shortcuthelp.*' --tests 'dev.jasper.app.workspace.ShortcutEditorIntegrationTest' --tests 'dev.jasper.app.application.*'`
Expected: PASS. Then `./gradlew :jasper-app:verifyApplicationArchitecture` (or `./gradlew check -x test` if that task
name differs; `grep -n verifyApplicationArchitecture gradle/application-architecture.gradle.kts`): PASS.

- [ ] **Step 5: Commit**

```bash
git branch --show-current   # must print claude/settings-dialog-b2
git add -A jasper-app/src
git commit -m "Make Keymap and Plugins pages of Settings and retire their windows

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 5: The View menu saves its choices; Window Settings… replaces the size dialogs

**Files:**
- Modify: `jasper-app/src/main/java/dev/jasper/app/config/SettingsEdit.java`
- Modify: `jasper-app/src/main/java/dev/jasper/app/workspace/WindowContent.java`
- Modify: `jasper-app/src/main/java/dev/jasper/app/workspace/WindowCommands.java`
- Modify: `jasper-app/src/main/java/dev/jasper/app/workspace/WindowChrome.java`
- Modify: `jasper-app/src/main/java/dev/jasper/app/workspace/package-info.java`
- Modify: `jasper-app/src/main/java/dev/jasper/app/application/ConfigurationController.java`
- Modify: `jasper-app/src/main/java/dev/jasper/app/application/JasperApplication.java`
- Test: `application/ConfigurationTestSupport.java`, `workspace/ConfigurationControllerTest.java`,
  `workspace/WindowCommandsTest.java`, `workspace/TabHeightTest.java`, `workspace/RailWidthTest.java`,
  `workspace/BarHeightTest.java`, `config/SettingsEditTest.java` (new)

**Interfaces:**
- Produces: `public static <T> SettingsEdit SettingsEdit.of(Setting<T> setting, T value, SettingValues saved)`.
- Produces: `public void WindowContent.connectSettings(Function<SettingsEdit, CompletableFuture<?>> save, Consumer<String> openAt)`;
  package-private `chooseToolbarMode(ToolbarMode)`, `chooseStatusVisible(boolean)`, `openWindowSettings()`,
  `settingsConnected()`; `selectTerminalColors(TerminalColors)` now saves when connected.
- Produces: `ConfigurationController.settingsOpener(BiConsumer<WindowContent, String>)`: the page is null for Cmd+,.
- Removes: `WindowContent.confirmTabHeight` / `confirmRailWidth` / `confirmToolbarHeight` / `confirmStatusBarHeight`,
  `WindowChrome.editTabHeight` / `editRailWidth` / `editToolbarHeight` / `editStatusBarHeight` / `editSize`, and the
  commands `view.tab_height`, `view.rail_width`, `view.toolbar_height`, `view.status_bar_height`.

- [ ] **Step 1: Write the failing tests**

Create `jasper-app/src/test/java/dev/jasper/app/config/SettingsEditTest.java`:

```java
package dev.jasper.app.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class SettingsEditTest {
    @Test void oneSettingExpectsWhatTheFileHolds() {
        var values = ConfigLoader.parse(Path.of("config.toml"), "[window]\ntoolbar = \"icons\"\n", true).values();
        var edit = SettingsEdit.of(CoreSettings.WINDOW_TOOLBAR, ToolbarMode.HIDDEN, values);
        assertThat(edit.changes()).isEqualTo(Map.of(CoreSettings.WINDOW_TOOLBAR, Optional.of(ToolbarMode.HIDDEN)));
        assertThat(edit.expected()).isEqualTo(Map.of(CoreSettings.WINDOW_TOOLBAR, Optional.of(ToolbarMode.ICONS)));
        var unset = SettingsEdit.of(CoreSettings.WINDOW_STATUS_BAR, false, values);
        assertThat(unset.expected()).isEqualTo(Map.of(CoreSettings.WINDOW_STATUS_BAR, Optional.empty()));
    }
}
```

In `ConfigurationTestSupport.java`, replace
`public void settingsOpener(Consumer<WindowContent> opener) { controller.settingsOpener(opener); }` with
`public void settingsOpener(java.util.function.BiConsumer<WindowContent, String> opener) { controller.settingsOpener(opener); }`.

In `ConfigurationControllerTest.java`, replace `settingsOpenerReplacesTheEditorForCmdComma` with the following three
tests:

```java
    @Test void settingsOpenerServesCmdCommaAndWindowSettings() throws Exception {
        start(""); edt(this::owner);
        var owner = owners.getFirst();
        var opened = new ArrayList<String>();
        edt(() -> controller.settingsOpener((window, page) -> opened.add((window == owner) + ":" + page)));
        edt(owner::openSettings);
        edt(() -> {
            owner.setActive(true);
            owner.windowCommands().view("view.window_settings").actionPerformed(new java.awt.event.ActionEvent(owner, 0, "menu"));
        });
        assertThat(opened).containsExactly("true:null", "true:window");
    }

    @Test void viewMenuChoicesSaveAndEveryWindowFollowsTheReload() throws Exception {
        start("[window]\ntab_height = 44\n"); edt(() -> { owner(); owner(); });
        var first = owners.getFirst(); var second = owners.get(1);
        var seen = new java.util.concurrent.CopyOnWriteArrayList<ConfigService.State>();
        edt(() -> controller.onState(seen::add));
        edt(() -> {
            first.setActive(true);
            first.windowCommands().view("view.toolbar.hidden").actionPerformed(new java.awt.event.ActionEvent(first, 0, "menu"));
        });
        until(() -> !seen.isEmpty() && seen.getLast().values().get(dev.jasper.app.config.CoreSettings.WINDOW_TOOLBAR) == ToolbarMode.HIDDEN);
        edt(() -> first.windowCommands().view("view.status_bar").actionPerformed(new java.awt.event.ActionEvent(first, 0, "menu")));
        until(() -> Boolean.FALSE.equals(seen.getLast().values().get(dev.jasper.app.config.CoreSettings.WINDOW_STATUS_BAR)));
        edt(() -> first.windowCommands().view("view.terminal_colors.dark").actionPerformed(new java.awt.event.ActionEvent(first, 0, "menu")));
        until(() -> seen.getLast().values().get(dev.jasper.app.config.CoreSettings.TERMINAL_COLORS) == dev.jasper.app.config.TerminalColors.DARK);
        edt(() -> {
            for (var owner : List.of(first, second)) {
                assertThat(owner.toolbar().isVisible()).isFalse();
                assertThat(owner.status().isVisible()).isFalse();
                assertThat(owner.terminalColors()).isEqualTo(dev.jasper.app.config.TerminalColors.DARK);
            }
        });
        assertThat(Files.readString(directory.resolve("config.toml"))).contains("tab_height = 44");
    }

    @Test void aRejectedFileRefusesViewChoicesAndTheMenuStaysTruthful() throws Exception {
        start("[window\n"); edt(this::owner);
        var owner = owners.getFirst();
        var errors = new ArrayList<String>();
        edt(() -> {
            owner.onError = errors::add;
            owner.setActive(true);
            var status = owner.windowCommands().view("view.status_bar");
            status.putValue(Action.SELECTED_KEY, false); // what a clicked check box does before the action runs
            status.actionPerformed(new java.awt.event.ActionEvent(owner, 0, "menu"));
            assertThat(errors).containsExactly("config.toml has errors, so changes can't be saved until they're fixed.");
            assertThat(owner.status().isVisible()).isTrue();
            assertThat(status.getValue(Action.SELECTED_KEY)).isEqualTo(true);
        });
    }
```

The file imports `javax.swing.*` (for `Action`) and `java.util.*` already; if `Action` is ambiguous, write
`javax.swing.Action.SELECTED_KEY`.

In `WindowCommandsTest.java`, replace the lines from `var sizes = java.util.List.of(` through the end of the `for`
loop over `sizes` with:

```java
                assertThat(entries.stream().map(e -> e.command().id())).contains("select_tab_1", "view.terminal_colors.dark", "view.window_settings")
                    .doesNotContain("view.tab_height", "view.rail_width", "view.toolbar_height", "view.status_bar_height");
                var windowSettings = entries.stream().map(CommandSearch.Entry::command)
                    .filter(c -> c.id().equals("view.window_settings")).findFirst().orElseThrow();
                assertThat(windowSettings.title()).isEqualTo("Window Settings…");
                var view = owner.menuBar().getMenu(2);
                assertThat(view.getItem(view.getItemCount() - 1).getAction()).as("View ends with Window Settings…")
                    .isSameAs(windowSettings.action());
                owner.updateActions();
                assertThat(windowSettings.action().isEnabled()).as("nothing to open without a configuration").isFalse();
```

Delete the View-dialog tests and their helpers:
- `TabHeightTest.java`: the test `viewControlStartsAtCurrentHeightCommitsOnlyOnOkAndCanReset` and the helpers
  `spinner(java.awt.Container)` and `heightItem(WindowContent)`.
- `RailWidthTest.java`: the test `viewControlStartsAtCurrentWidthCommitsOnlyOnOkAndCanReset`, the helpers
  `spinner(java.awt.Container)` and `widthItem(WindowContent)`, and in `closingTheWindowIgnoresLaterWidthChanges` the
  line `assertThat(owner.confirmRailWidth.applyAsInt(new JPanel())).isEqualTo(JOptionPane.CANCEL_OPTION);`.
- `BarHeightTest.java`: the test `viewControlsStartAtTheCurrentHeightCommitOnlyOnOkAndCanReset`, the helpers
  `exercise(...)`, `spinner(java.awt.Container, String)` and `item(WindowContent, String)`, and in
  `closingTheWindowIgnoresLaterHeightChanges` the two `owner.confirm…Height.applyAsInt(...)` assertions.
- In those three files, delete imports that no longer have a use (check each of `JSpinner`, `JMenuItem`,
  `JOptionPane`, `JPanel`, `JComponent`, `Consumer`, `ToIntFunction`, `IntSupplier`, `IntConsumer` with `grep -n`).

- [ ] **Step 2: Run the tests to verify they fail**

Run: `./gradlew :jasper-app:test --tests 'dev.jasper.app.config.SettingsEditTest' --tests 'dev.jasper.app.workspace.ConfigurationControllerTest' --tests 'dev.jasper.app.workspace.WindowCommandsTest'`
Expected: compilation fails: `SettingsEdit.of` does not exist and `settingsOpener` takes a `Consumer`.

- [ ] **Step 3: Implement**

In `SettingsEdit.java`, add the import `import dev.jasper.app.settings.SettingValues;` and, after the compact
constructor, add:

```java

    /** One setting's new value, expecting the value {@code saved} holds for it (empty when the file does not set it). */
    public static <T> SettingsEdit of(Setting<T> setting, T value, SettingValues saved) {
        return new SettingsEdit(Map.<Setting<?>, Optional<?>>of(setting, Optional.of(value)),
            Map.<Setting<?>, Optional<?>>of(setting, saved.saved(setting)));
    }
```

In `workspace/package-info.java`, add `dev.jasper.app.settings, ` after `dev.jasper.app.persistence, ` in the allowed list.

In `WindowContent.java`:
1. Next to `private Runnable openSettings, reloadConfiguration, migrateConfiguration;`, add:

```java
    private java.util.function.Function<dev.jasper.app.config.SettingsEdit, java.util.concurrent.CompletableFuture<?>> saveSettings;
    private Consumer<String> openSettingsAt;
    private ConfigService.State configurationState;
    static final String REJECTED_CHOICE = "config.toml has errors, so changes can't be saved until they're fixed.";
```

2. Delete the four `confirmTabHeight`, `confirmRailWidth`, `confirmToolbarHeight` and `confirmStatusBarHeight` field
   declarations (each is two lines) and, in `close()`, the four lines that reset them.
3. After `connectConfiguration(Runnable settings, Runnable reload, Runnable migrate, Runnable unregister)`, add:

```java
    /** How View choices save, and how Window Settings… opens a Settings page; connected with the configuration. */
    public void connectSettings(java.util.function.Function<dev.jasper.app.config.SettingsEdit, java.util.concurrent.CompletableFuture<?>> save,
                                Consumer<String> openAt) {
        if (closed) return;
        saveSettings = java.util.Objects.requireNonNull(save);
        openSettingsAt = java.util.Objects.requireNonNull(openAt);
        updateActions();
    }
```

4. In `disconnectConfiguration()`, after `openSettings = null; reloadConfiguration = null; migrateConfiguration = null; unregisterConfiguration = () -> {};`
   add `saveSettings = null; openSettingsAt = null; configurationState = null;`.
5. In `setConfigurationState(ConfigService.State state)`, after `if (closed) return;` add `configurationState = state;`.
6. Next to `void openSettings() { openSettings.run(); }`, add:

```java
    boolean settingsConnected() { return openSettingsAt != null; }
    void openWindowSettings() { if (openSettingsAt != null) openSettingsAt.accept("window"); }
```

7. Replace:

```java
    /** A session-only terminal palette choice shared by every window; the chrome is unchanged. */
    void selectTerminalColors(TerminalColors colors) {
        if (closed) return;
        themes.selectTerminalColors(colors);
        chrome.refreshTheme();
    }
```

   with:

```java
    /** View › Terminal Colors: saved for every window; applied to this window's themes when no configuration is connected. */
    void selectTerminalColors(TerminalColors colors) {
        choose(dev.jasper.app.config.CoreSettings.TERMINAL_COLORS, colors, () -> {
            themes.selectTerminalColors(colors);
            chrome.refreshTheme();
        });
    }

    /** View › Toolbar. */
    void chooseToolbarMode(ToolbarMode mode) {
        choose(dev.jasper.app.config.CoreSettings.WINDOW_TOOLBAR, mode, () -> setToolbarMode(mode));
    }

    /** View › Status Bar. */
    void chooseStatusVisible(boolean visible) {
        choose(dev.jasper.app.config.CoreSettings.WINDOW_STATUS_BAR, visible, () -> setStatusVisible(visible));
    }

    /**
     * A View choice. With a configuration it is saved, and every window follows the reload; a rejected file or a
     * failed save leaves everything as it was and says why. Without one it applies here only.
     */
    private <T> void choose(dev.jasper.app.settings.Setting<T> setting, T value, Runnable locally) {
        if (closed) return;
        var save = saveSettings;
        var state = configurationState;
        if (save == null || state == null) { locally.run(); return; }
        if (state.rejected()) { revertChoices(); onError.accept(REJECTED_CHOICE); return; }
        java.util.concurrent.CompletableFuture<?> result;
        try { result = save.apply(dev.jasper.app.config.SettingsEdit.of(setting, value, state.values())); }
        catch (RuntimeException failure) { result = java.util.concurrent.CompletableFuture.failedFuture(failure); }
        result.whenComplete((ignored, failure) -> {
            if (failure == null) return;
            SwingUtilities.invokeLater(() -> {
                if (closed) return;
                revertChoices();
                Throwable cause = failure instanceof java.util.concurrent.CompletionException && failure.getCause() != null
                    ? failure.getCause() : failure;
                onError.accept(cause.getMessage());
            });
        });
    }

    /** A clicked check box or radio item shows its new state at once; put the menus back to what is in effect. */
    private void revertChoices() {
        updateActions();
        chrome.refreshTheme();
    }
```

In `WindowCommands.java`:
1. Replace `"Toolbar: " + label, () -> owner.setToolbarMode(mode));` with `"Toolbar: " + label, () -> owner.chooseToolbarMode(mode));`.
2. Replace `add(registry, "view.status_bar", "Status Bar", "Hide Status Bar", () -> owner.setStatusVisible(!owner.status().isVisible()));`
   with `add(registry, "view.status_bar", "Status Bar", "Hide Status Bar", () -> owner.chooseStatusVisible(!owner.status().isVisible()));`.
3. Replace the four `add(registry, "view.tab_height" …`, `"view.rail_width"`, `"view.toolbar_height"` and
   `"view.status_bar_height"` registrations (five lines) with:

```java
        add(registry, "view.window_settings", "Window Settings…", "Window Settings…", owner::openWindowSettings,
            List.of("tab height", "rail width", "toolbar height", "status bar height", "size"));
```

4. In `refresh()`, replace the four `view("view.…").setEnabled(true);` lines for the size commands with
   `view("view.window_settings").setEnabled(owner.settingsConnected());`.

In `WindowChrome.java`:
1. Replace:

```java
        JMenuItem tabHeight = new JMenuItem(owner.windowCommands().view("view.tab_height"));
        view.add(tabHeight);
        view.add(new JMenuItem(owner.windowCommands().view("view.rail_width")));
        view.add(new JMenuItem(owner.windowCommands().view("view.toolbar_height")));
        view.add(new JMenuItem(owner.windowCommands().view("view.status_bar_height")));
```

   with:

```java
        view.add(new JMenuItem(owner.windowCommands().view("view.window_settings")));
```

2. Delete `editTabHeight()`, `editRailWidth()`, `editToolbarHeight()`, `editStatusBarHeight()` and `editSize(...)`
   with its comment.
3. Delete imports that no longer have a use (check `JSpinner`, `SpinnerNumberModel`, `JOptionPane`, `FlowLayout`,
   `JLabel`, `JButton`, `JPanel`, `JComponent` with `grep -n`; the star import `javax.swing.*`, if present, stays).

In `ConfigurationController.java`:
1. Replace `private Consumer<WindowContent> settingsOpener;` with
   `private java.util.function.BiConsumer<WindowContent, String> settingsOpener;`.
2. In the constructor, replace `settingsOpener = owner -> reportFailure(owner, service.openSettings(editor));` with
   `settingsOpener = (owner, page) -> reportFailure(owner, service.openSettings(editor));`.
3. In `register`, replace:

```java
        owner.connectConfiguration(() -> settingsOpener.accept(owner),
            () -> reportFailure(owner, service.reload()), () -> migrateFor(owner), () -> unregister(owner));
```

   with:

```java
        owner.connectConfiguration(() -> settingsOpener.accept(owner, null),
            () -> reportFailure(owner, service.reload()), () -> migrateFor(owner), () -> unregister(owner));
        owner.connectSettings(this::save, page -> settingsOpener.accept(owner, page));
```

4. Replace the `settingsOpener(Consumer<WindowContent> opener)` method and its comment with:

```java
    /**
     * What Cmd+, each window's Settings command and View › Window Settings… open, with the page to show (null for the
     * last one visited); by default config.toml in an editor.
     */
    void settingsOpener(java.util.function.BiConsumer<WindowContent, String> opener) {
        requireEdt();
        settingsOpener = java.util.Objects.requireNonNull(opener, "opener");
    }
```

In `JasperApplication.java`, replace `configuration.settingsOpener(owner -> openSettings(owner.id(), null));` with
`configuration.settingsOpener((owner, page) -> openSettings(owner.id(), page));`.

- [ ] **Step 4: Run the tests to verify they pass**

Run: `./gradlew :jasper-app:test --tests 'dev.jasper.app.config.*' --tests 'dev.jasper.app.workspace.*' --tests 'dev.jasper.app.application.*'`
Expected: PASS. `TerminalColorsTest`, `WindowChromeTest` and `WindowContributionsTest` click View items on windows
without a configuration, which still apply locally.

- [ ] **Step 5: Commit**

```bash
git branch --show-current   # must print claude/settings-dialog-b2
git add -A jasper-app/src
git commit -m "Save View menu choices and replace the size dialogs with Window Settings

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 6: No more session overrides

**Files:**
- Modify: `jasper-app/src/main/java/dev/jasper/app/workspace/WorkspaceConfiguration.java`
- Modify: `jasper-app/src/main/java/dev/jasper/app/appearance/ThemeState.java`
- Modify: `jasper-app/src/main/java/dev/jasper/app/appearance/ThemeController.java`
- Modify: `jasper-app/src/main/java/dev/jasper/app/workspace/WindowContent.java`
- Modify: `jasper-app/src/main/java/dev/jasper/app/config/ConfigSnapshot.java` (comment)
- Test: `appearance/ThemeStateTest.java`, `appearance/TerminalColorsThemeTest.java`, `workspace/RailWidthTest.java`,
  `workspace/BarHeightTest.java`, `workspace/ConfigurationControllerTest.java`

**Interfaces:**
- Produces: `record ThemeState(Theme saved, TerminalColors terminal)`; `ThemeController.configureTerminal(TerminalColors)`.
- Removes: `ThemeState.chooseTerminal`, `ThemeState.terminalOverride`, `ThemeController.selectTerminalColors`.

- [ ] **Step 1: Write the failing tests**

`ThemeStateTest.java`: replace `temporaryTerminalChoiceOverridesSavedUntilTheSavedValueChanges` with:

```java
    @Test void theTerminalChoiceIsTheConfiguredOneAndSurvivesThemeChanges() {
        var configured = ThemeState.defaults().configureTerminal(TerminalColors.LIGHT);
        assertThat(configured.terminalChoice()).isEqualTo(TerminalColors.LIGHT);
        assertThat(configured.configure(Theme.LIGHT).terminalChoice())
            .as("a theme change keeps the terminal choice").isEqualTo(TerminalColors.LIGHT);
        assertThat(configured.configureTerminal(TerminalColors.DARK).terminalChoice()).isEqualTo(TerminalColors.DARK);
    }
```

`TerminalColorsThemeTest.java`: replace both `themes.selectTerminalColors(TerminalColors.DARK);` with
`themes.configureTerminal(TerminalColors.DARK);`.

`RailWidthTest.java`: replace `theSavedWidthAppliesLiveAndAnUnrelatedReloadKeepsTheSessionChoice` with:

```java
    @Test void theSavedWidthAppliesOnEveryReload() throws Exception {
        edt(() -> {
            var owner = content(launcher(new ArrayDeque<>()));
            var saved = ConfigSnapshot.defaults().toBuilder().railWidth(44).build();
            owner.applyConfiguration(saved, true);
            assertThat(owner.railWidth()).isEqualTo(44);
            owner.setRailWidth(52);
            owner.applyConfiguration(saved.toBuilder().tabHeight(40).build(), true);
            assertThat(owner.railWidth()).as("the saved width is the only width").isEqualTo(44);
            owner.applyConfiguration(saved.toBuilder().tabHeight(40).railWidth(30).build(), true);
            assertThat(owner.railWidth()).isEqualTo(30);
        });
    }
```

`BarHeightTest.java`: replace `savedHeightsApplyLiveAndAnUnrelatedReloadKeepsTheSessionChoice` with:

```java
    @Test void savedHeightsApplyOnEveryReload() throws Exception {
        edt(() -> {
            var owner = content(launcher(new ArrayDeque<>()));
            var saved = ConfigSnapshot.defaults().toBuilder().toolbarHeight(40).statusBarHeight(26).build();
            owner.applyConfiguration(saved, true);
            assertThat(owner.toolbarHeight()).isEqualTo(40);
            assertThat(owner.statusBarHeight()).isEqualTo(26);
            owner.setToolbarHeight(50); owner.setStatusBarHeight(34);
            owner.applyConfiguration(saved.toBuilder().tabHeight(40).build(), true);
            assertThat(owner.toolbarHeight()).as("the saved height is the only height").isEqualTo(40);
            assertThat(owner.statusBarHeight()).isEqualTo(26);
            owner.applyConfiguration(saved.toBuilder().tabHeight(40).toolbarHeight(44).statusBarHeight(22).build(), true);
            assertThat(owner.toolbarHeight()).isEqualTo(44);
            assertThat(owner.statusBarHeight()).isEqualTo(22);
        });
    }
```

`ConfigurationControllerTest.java`: replace the first `edt` block after the first `reload(...)` in
`unrelatedChangesPreserveTemporaryChoicesAndNewOwnersUseSavedDefaults`, and the test's name, so the test reads:

```java
    @Test @DisabledOnOs(OS.WINDOWS)
    void reloadsReapplyTheSavedChromeKeepPaneFontSizesAndNewOwnersUseSavedDefaults() throws Exception {
        start("[terminal.font]\nsize=19\n"); edt(this::owner); launchAll();
        var first = owners.getFirst();
        edt(() -> {
            first.setTabHeight(60); first.setToolbarMode(ToolbarMode.HIDDEN);
            first.setStatusVisible(false); first.currentPane().view().setFontSize(28);
        });
        reload("# comment\n[terminal.font]\nsize=19\nunknown=1\n");
        edt(() -> {
            assertThat(first.tabHeight()).as("the saved chrome is reapplied").isEqualTo(30);
            assertThat(first.toolbar().isVisible()).isTrue();
            assertThat(first.status().isVisible()).isTrue(); assertThat(first.theme().chrome()).isEqualTo(Theme.DARK);
            assertThat(first.currentPane().view().fontSize()).as("a pane's own size stays").isEqualTo(28);
            var next = owner(); assertThat(next.tabHeight()).isEqualTo(30);
            assertThat(next.toolbar().isVisible()).isTrue(); assertThat(next.status().isVisible()).isTrue();
            assertThat(next.theme().chrome()).isEqualTo(Theme.DARK);
        }); launchAll();
        edt(() -> assertThat(owners.get(1).currentPane().view().fontSize()).isEqualTo(19));
        reload("[window]\ntab_height=45\ntoolbar='icons'\nstatus_bar=false\n[terminal.font]\nsize=20\n[ui.theme]\nname='IntelliJ Light'\n");
        edt(() -> {
            assertThat(first.tabHeight()).isEqualTo(45); assertThat(first.toolbar().isVisible()).isTrue();
            assertThat(first.currentPane().view().fontSize()).isEqualTo(20);
            assertThat(first.theme().chrome()).as("the saved theme name applies").isEqualTo(Theme.LIGHT);
        });
    }
```

- [ ] **Step 2: Run the tests to verify they fail**

Run: `./gradlew :jasper-app:test --tests 'dev.jasper.app.appearance.*' --tests 'dev.jasper.app.workspace.RailWidthTest' --tests 'dev.jasper.app.workspace.BarHeightTest' --tests 'dev.jasper.app.workspace.ConfigurationControllerTest'`
Expected: `TerminalColorsThemeTest` fails to compile (`configureTerminal` is undefined); once that compiles, the
rewritten reload tests fail because unchanged saved values are not reapplied.

- [ ] **Step 3: Implement**

Replace `ThemeState.java` with:

```java
package dev.jasper.app.appearance;

import dev.jasper.app.config.TerminalColors;
import dev.jasper.terminal.config.Palette;
import java.util.Objects;

/**
 * The configured theme and terminal colours. The chrome is the configured theme; the terminal palette follows the
 * terminal choice, where MATCH uses the theme's own palette.
 */
record ThemeState(Theme saved, TerminalColors terminal) {
    ThemeState { Objects.requireNonNull(saved); Objects.requireNonNull(terminal); }

    static ThemeState defaults() { return new ThemeState(Theme.DARK, TerminalColors.MATCH); }

    ThemeState configure(Theme next) { return new ThemeState(Objects.requireNonNull(next), terminal); }

    ThemeState configureTerminal(TerminalColors next) { return new ThemeState(saved, Objects.requireNonNull(next)); }

    Theme choice() { return saved; }

    TerminalColors terminalChoice() { return terminal; }

    ResolvedTheme resolve() {
        Palette palette = switch (terminal) {
            case MATCH -> saved.palette();
            case LIGHT -> Palette.jasperLight();
            case DARK -> Palette.jasperDark();
        };
        return new ResolvedTheme(saved, palette);
    }
}
```

In `ThemeController.java`, replace:

```java
    /** The effective terminal colours. */
    public TerminalColors terminalColors() { requireEdt(); return state.terminalChoice(); }
    /** A temporary View choice for every window. */
    public void selectTerminalColors(TerminalColors choice) {
        requireEdt(); apply(state.chooseTerminal(Objects.requireNonNull(choice)));
    }
    public void configure(Theme saved) { configure(saved, uiFont); }
    public void configure(Theme saved, UiFontConfig font) { configure(saved, state.terminalSaved(), font); }
```

with:

```java
    /** The configured terminal colours. */
    public TerminalColors terminalColors() { requireEdt(); return state.terminalChoice(); }
    /** Applies configured terminal colours to every window; the chrome is unchanged. */
    public void configureTerminal(TerminalColors terminal) {
        requireEdt(); apply(state.configureTerminal(Objects.requireNonNull(terminal)));
    }
    public void configure(Theme saved) { configure(saved, uiFont); }
    public void configure(Theme saved, UiFontConfig font) { configure(saved, state.terminalChoice(), font); }
```

In `WindowContent.selectTerminalColors`, replace `themes.selectTerminalColors(colors);` with
`themes.configureTerminal(colors);`.

In `WorkspaceConfiguration.java`, replace the class comment
`/** Applies changed saved values on the EDT without resetting unrelated temporary choices. */` with
`/** Applies saved values on the EDT: window chrome always takes them; a pane's own font size survives reloads that keep the saved size. */`,
and replace the six guarded chrome lines:

```java
        if (previous == null || previous.tabHeight() != next.tabHeight()) owner.setTabHeight(next.tabHeight());
        if (previous == null || previous.railWidth() != next.railWidth()) owner.setRailWidth(next.railWidth());
        if (previous == null || previous.toolbarHeight() != next.toolbarHeight()) owner.setToolbarHeight(next.toolbarHeight());
        if (previous == null || previous.statusBarHeight() != next.statusBarHeight()) owner.setStatusBarHeight(next.statusBarHeight());
        if (previous == null || previous.toolbar() != next.toolbar()) owner.setToolbarMode(next.toolbar());
        if (previous == null || previous.statusBar() != next.statusBar()) owner.setStatusVisible(next.statusBar());
```

with:

```java
        owner.setTabHeight(next.tabHeight());
        owner.setRailWidth(next.railWidth());
        owner.setToolbarHeight(next.toolbarHeight());
        owner.setStatusBarHeight(next.statusBarHeight());
        if (owner.toolbarMode() != next.toolbar()) owner.setToolbarMode(next.toolbar());
        if (owner.status().isVisible() != next.statusBar()) owner.setStatusVisible(next.statusBar());
```

(The size setters already return early when the size is unchanged.)

In `ConfigSnapshot.java`, replace `/** Validated saved defaults; runtime View choices are kept separately by each owner. */`
with `/** Validated saved defaults; only a pane's own font size is kept separately, by the pane. */`.

- [ ] **Step 4: Run the tests to verify they pass**

Run: `./gradlew :jasper-app:test`
Expected: PASS (the whole app module; `TerminalColorsTest`, `WindowTabsTest` and `GraphiteChromeTest` call
`owner.selectTerminalColors`, which applies through `configureTerminal` on windows without a configuration).

- [ ] **Step 5: Commit**

```bash
git branch --show-current   # must print claude/settings-dialog-b2
git add -A jasper-app/src
git commit -m "Drop the session overrides for window chrome and terminal colours

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 7: Preview, documentation and the full check

**Files:**
- Modify: `jasper-app/src/test/java/dev/jasper/app/settingsui/SettingsDialogPreview.java` (replace)
- Modify: `jasper-app/src/main/java/dev/jasper/app/config/ConfigTemplate.java`, `config/CoreSettings.java` (text only)
- Modify: `docs/configuration.md`, `docs/app-architecture.md`, `docs/app-maintenance.md`, `README.md`,
  `jasper-app/README.md`, `docs/STATUS.md`, this plan's status banner

- [ ] **Step 1: Render Keymap and Plugins in the preview**

Replace `SettingsDialogPreview.java` with:

```java
package dev.jasper.app.settingsui;

import dev.jasper.app.appearance.ThemeController;
import dev.jasper.app.appearance.ThemeManager;
import dev.jasper.app.config.ConfigLoader;
import dev.jasper.app.config.ConfigService;
import dev.jasper.app.config.UiFontConfig;
import dev.jasper.app.contributions.Contributions;
import dev.jasper.app.lifecycle.Subscription;
import dev.jasper.app.notifications.ActivityNotifier;
import dev.jasper.app.persistence.UiState;
import dev.jasper.app.pluginmanager.PluginManager;
import dev.jasper.app.plugins.PluginRuntime;
import dev.jasper.app.restart.ResidentControl;
import dev.jasper.app.restart.RestartFlow;
import dev.jasper.app.shortcuthelp.KeymapPage;
import dev.jasper.app.shortcuthelp.ShortcutTestSupport;
import dev.jasper.app.terminals.TerminalRegistry;
import dev.jasper.app.testsupport.PluginJars;
import dev.jasper.app.windows.AuxiliarySurface;
import dev.jasper.app.windows.AuxiliaryWindows;
import dev.jasper.buddy.view.BuddyTestSupport;
import java.awt.Container;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;
import javax.imageio.ImageIO;
import javax.swing.JComponent;
import javax.swing.KeyStroke;
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
        Path home = Files.createTempDirectory("jasper-settings-preview");
        PluginJars.build(home.resolve("plugins/dev.example.preview/jars"), "main.jar",
            PluginJars.descriptor("dev.example.preview", "1.0.0", "fix.Main"), Map.of(), List.of());
        var holder = new JComponent[1];
        var windows = new AuxiliaryWindows(UiState.inMemory(), surface -> {
            holder[0] = surface.holder();
            return new AuxiliarySurface.Shell(() -> { }, () -> { }, () -> { }, title -> { }, () -> new Rectangle(0, 0, 1000, 700),
                (title, initial) -> Optional.empty());
        });
        var themes = new ThemeController[1];
        var runtime = new PluginRuntime[1];
        var manager = new PluginManager[1];
        SwingUtilities.invokeAndWait(() -> {
            themes[0] = new ThemeController();
            runtime[0] = new PluginRuntime(new PluginRuntime.Options(null, home.resolve("plugins"), null, false,
                home.resolve("plugins.toml"), home.resolve("plugins.lock")), new ActivityNotifier(new BuddyTestSupport().companion(), () -> { }),
                PluginRuntime.SettingsReports.IGNORED, new Contributions(), windows, new TerminalRegistry());
            runtime[0].start(Map.of(), true);
            var flow = new RestartFlow(ResidentControl.NONE, mode -> true, Runnable::run, Runnable::run,
                Duration.ofMillis(20), Duration.ofMillis(1));
            manager[0] = new PluginManager(runtime[0], windows, new PluginManager.Hooks(surface -> Optional.empty(), flow, () -> { },
                ResidentControl.NONE, false, Runnable::run, path -> { }, path -> { }));
        });
        var keymapHooks = new KeymapPage.Hooks(new Contributions(), Map::of, Map::of, true, edits -> CompletableFuture.completedFuture(null));
        try {
            for (var theme : ThemeManager.builtIns()) for (int size : new int[]{0, 18}) {
                String suffix = theme.id() + "-" + size + ".png";
                var keymap = new KeymapPage[1];
                var dialog = new SettingsDialog(windows, CorePages.pages(() -> List.of("Jasper Dark", "IntelliJ Light"),
                    new CorePages.SystemActions(() -> file, () -> { }, () -> { }, () -> { }, () -> { }, () -> false),
                    context -> keymap[0] = new KeymapPage(keymapHooks, context), context -> manager[0].page(context)),
                    new SettingsDialog.Hooks(edit -> CompletableFuture.completedFuture(state), () -> state,
                        listener -> new Subscription(() -> { }), () -> { }, () -> { }, uri -> { }, (parent, count) -> true));
                SwingUtilities.invokeAndWait(() -> {
                    themes[0].configure(theme, new UiFontConfig("system", size));
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
                    dialog.select("keymap");
                    ShortcutTestSupport.set(keymap[0], "Copy", KeyStroke.getKeyStroke("ctrl H"));
                });
                SwingUtilities.invokeAndWait(() -> {
                    render(holder[0], output.resolve("keymap-" + suffix));
                    dialog.view().search.setText("paste");
                    render(holder[0], output.resolve("keymap-search-" + suffix));
                    dialog.view().search.setText("");
                    dialog.select("plugins");
                });
                await(() -> dialog.pages().stream().filter(page -> page.id().equals("plugins")).findFirst().orElseThrow()
                    .searchTerms().stream().anyMatch(term -> term.contains("dev.example.preview")));
                SwingUtilities.invokeAndWait(() -> {
                    render(holder[0], output.resolve("plugins-" + suffix));
                    dialog.cancel();
                });
            }
        } finally {
            var stopping = new CompletableFuture<List<CompletableFuture<?>>>();
            SwingUtilities.invokeAndWait(() -> stopping.complete(runtime[0].stop()));
            CompletableFuture.allOf(stopping.get().toArray(CompletableFuture[]::new)).get(10, TimeUnit.SECONDS);
            SwingUtilities.invokeAndWait(windows::close);
        }
    }

    /** Polls a condition on the EDT, so runtime callbacks queued there can run in between. */
    private static void await(BooleanSupplier onEdt) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
        boolean[] done = {false};
        while (true) {
            SwingUtilities.invokeAndWait(() -> done[0] = onEdt.getAsBoolean());
            if (done[0]) return;
            if (System.nanoTime() > deadline) throw new IllegalStateException("Timed out waiting for the plugin list");
            Thread.sleep(20);
        }
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

Before replacing, compare the old file's `render` and `layoutVisible` bodies with the ones above and keep the old ones
if they differ (the plan copied them from B1's final version).

Run: `./gradlew :jasper-app:settingsPreview`
Expected: 36 PNGs in `jasper-app/build/settings-preview` (four theme/size combinations, nine renders each). Open
`keymap-*`, `keymap-search-*` and `plugins-*` for every combination with the Read tool and compare them with the
user's IntelliJ Keymap reference (search and tree at left, breadcrumb header, full-height list, blue changed rows and
section, footer buttons). Check: the Keymap list fills the page with no outer scroll bar; the Copy row and the
Clipboard section are blue after the staged Set; Keymap's own search shows "paste" in the search render; the Plugins
page shows `dev.example.preview 1.0.0` needing review and fills the page. Fix any defect found in the page code (not in
the preview), rerun, and record each fix as a deviation.

- [ ] **Step 2: Update the text that described session overrides**

`ConfigTemplate.java`: replace the header line
`# View menu choices are temporary overrides until that saved setting changes.` with
`# View menu choices (toolbar, status bar, terminal colors) are saved here.`

`CoreSettings.java`, `TERMINAL_FONT_SIZE`'s description: replace
`"Temporary size/appearance choices survive reloads until their respective saved value changes."` with
`"A pane's own size (Cmd+= and Cmd+-) survives reloads until this saved size changes."`

Run: `./gradlew :jasper-app:test --tests 'dev.jasper.app.config.*'`
Expected: PASS (the template tests check keys and values, not these comments).

- [ ] **Step 3: Update the documentation**

`docs/configuration.md`:
- In "## Settings dialog", replace the sentence `Keymap and Plugins open their own windows for now.` with:

```text
Keymap lists every shortcut, grouped as in the menus; shortcuts that differ from their defaults, and the
sections holding them, are blue. Click one, press the new keys and click Set; Apply or OK saves every change
at once. Plugins lists the installed plugins: installing, enabling, disabling and removing apply at once and
take effect after a restart. Help › Keyboard Shortcuts… and File › Manage Plugins… open these pages.
```

- In "### Live settings and temporary choices", replace the paragraph that starts `View menu choices and per-pane
  font sizes are temporary runtime overrides` with:

```text
View menu choices save: Toolbar, Status Bar and Terminal Colors write `window.toolbar`, `window.status_bar` and `terminal.colors` to `config.toml`, and every window follows. View › Window Settings… opens Settings at the Window page for the tab, rail, toolbar and status bar sizes. If `config.toml` has errors, a View choice is refused with a message and nothing changes. Per-pane font sizes (Cmd+= and Cmd+-) and View › Show/Hide Jasper stay temporary. Changing font family, fallback, ligatures, line height or terminal behavior preserves each pane's manually adjusted size when the saved `terminal.font.size` is unchanged. Changing saved `terminal.font.size` applies it to all retained panes. New panes and Font reset use the saved size.
```

- In "### Plugins", replace `Right-click a plugin in File → Manage
  Plugins… and choose Open Settings` (it spans a line break) with `Right-click a plugin in Settings › Plugins
  (File → Manage Plugins…) and choose Open Settings`.
- In "## Shortcuts", after the first paragraph add this paragraph:

```text
Settings › Keymap edits these bindings without opening the file; it writes the same `[keybindings]` entries.
```

  and replace `` `"plugins.manage"` opens the Plugins manager and has no default shortcut.`` with
  `` `"plugins.manage"` opens Settings at Plugins and `"app.shortcuts"` at Keymap; neither has a default shortcut.``
- In "## Reload and diagnostics", replace `Deleting the file restores saved defaults to built-in values; unchanged
  fields still preserve temporary runtime overrides.` with `Deleting the file restores saved defaults to built-in values.`
- In "## Terminal colors", replace the paragraph that starts `View → Terminal Colors offers Terminal: Match UI, Light
  and Dark as a temporary choice` with:

```text
View → Terminal Colors offers Terminal: Match UI, Light and Dark; choosing one saves `terminal.colors`. An
unrecognized value produces an error diagnostic and keeps `"match"`; a non-string value rejects the configuration.
```

`docs/app-architecture.md`:
- Under "## Saved versus temporary configuration", replace the first paragraph with:

```text
`ConfigSnapshot` validates immutable canonical values; builder and toBuilder use that same
validation. `WorkspaceConfiguration` applies the saved window chrome (sizes, toolbar mode,
status bar) on every reload: the View menu saves those choices through `WindowContent.choose`
instead of keeping them per window, and a window without a configuration applies them directly.
`ConfigurationController` sends appearance, including `terminal.colors`, to `ThemeController`.
```

- In the package table, replace the `settingsui` row's text `schema-generated, group and launcher pages, editors and
  the core page tree.` with `schema-generated and group pages, editors and the core page tree; the application supplies
  the Keymap and Plugins pages.`; replace the `shortcuthelp` row's description with
  `The Settings dialog's Keymap page: effective shortcut catalog, expandable searchable sections, staged binding edits saved on Apply and a window-scoped key recorder. SettingsDialog owns each KeymapPage; disposing it closes its subscription and capture.`;
  replace the `pluginmanager` row's description with
  `The Settings dialog's Plugins page: passive view, consent view and an EDT controller over PluginRuntime's public records. Application owns the PluginManager; SettingsDialog owns each PluginsPage, whose consent and confirm dialogs open over Settings.`

`docs/app-maintenance.md`, "## Saved, live and session-only settings": replace `Update reload/override tests and docs.
Session-only overrides remain owner state and are not written back. A saved value changing may reset an override; an
unchanged value must preserve it.` with `Update reload tests and docs. A View menu choice for a saved field saves it
(WindowContent.choose); only per-pane font sizes and Show/Hide Jasper stay session-only, and an unchanged saved font
size must preserve a pane's size.`

`README.md`: replace `View → Tab height… adjusts it from 28–72 pixels for the current window, with a reset to 38;
Cancel preserves the previous height. View choices are temporary overrides and survive unrelated configuration
reloads.` with `View → Window Settings… opens Settings at the Window page to change it. View choices are saved to
config.toml and apply to every window.`; and replace `**Managing plugins.** File → Manage Plugins… (also in the
command palette) lists every plugin with its state:` with `**Managing plugins.** File → Manage Plugins… (also in the
command palette) opens Settings at the Plugins page, which lists every plugin with its state:`.

`jasper-app/README.md`: replace ``The user-facing manager
is `pluginmanager/PluginManager` (File → Manage Plugins…);`` with ``The user-facing manager
is the Settings dialog's Plugins page, `pluginmanager/PluginsPage` (File → Manage Plugins…);``.

Run: `./gradlew :jasper-app:test --tests '*AppDocumentationTest*'`
Expected: PASS.

- [ ] **Step 4: The full check**

Run: `./gradlew check`
Expected: BUILD SUCCESSFUL. Count the tests from the XML:

```bash
python3 - <<'PY'
import pathlib, xml.etree.ElementTree as ET
t = f = e = s = 0
for p in pathlib.Path(".").glob("*/build/test-results/test/*.xml"):
    r = ET.parse(p).getroot(); t += int(r.get("tests")); f += int(r.get("failures")); e += int(r.get("errors")); s += int(r.get("skipped"))
for p in pathlib.Path("plugins").glob("*/build/test-results/test/*.xml"):
    r = ET.parse(p).getroot(); t += int(r.get("tests")); f += int(r.get("failures")); e += int(r.get("errors")); s += int(r.get("skipped"))
print(f"tests={t} failures={f} errors={e} skipped={s}")
PY
```

Also run the source hygiene check from AGENTS.md over the changed files.

- [ ] **Step 5: Record the outcome**

In `docs/STATUS.md`, under "## Current state", add this entry above the B1 entry, with the real test count in place
of the count placeholder, and change the B1 entry's last sentence `Not pushed; not merged.` to
`Merged to local main (3cb249e5); not pushed.`:

```text
- **Settings dialog B2** (`claude/settings-dialog-b2`, 2026-09-28; plan
  `docs/superpowers/plans/2026-09-28-jasper-settings-dialog-b2.md`): Keymap and Plugins are pages of Settings.
  Keymap stages shortcut edits (Set) and saves them in one write on Apply; shortcuts and sections that differ from
  their defaults are blue. Plugins hosts the plugin list, with its consent and confirm dialogs over Settings. Help ›
  Keyboard Shortcuts… and File › Manage Plugins… open those pages; their old windows are gone. The View menu saves
  toolbar mode, the status bar and terminal colours; View › Window Settings… replaces the four size dialogs; the
  session overrides in `WorkspaceConfiguration` and `ThemeState` are removed. Previews: `settingsPreview` renders 36
  images, inspected against the IntelliJ reference. `./gradlew check`: COUNT tests, 0 failures. GUI checks pending
  with the user: record a shortcut, Set, Apply and use it; the plugin restart banner and an install's consent dialog
  over Settings; View choices in two windows; a View choice with a broken config.toml; Window Settings… Deviations:
  see the plan's status banner. Not pushed; not merged.
```

Update this plan's **Status** line to `Implemented on claude/settings-dialog-b2; ./gradlew check green, COUNT tests.`
(real count) and list any deviations under "Deviations so far".

- [ ] **Step 6: Commit**

```bash
git branch --show-current   # must print claude/settings-dialog-b2
git add -A docs README.md jasper-app
git commit -m "Preview and document the Keymap and Plugins pages and saved View choices

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```
