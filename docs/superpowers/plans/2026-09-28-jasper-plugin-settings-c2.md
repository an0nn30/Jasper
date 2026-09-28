# Plugin Settings C2 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Status:** Not started. Branch `claude/plugin-settings-c2`, worktree `.worktrees/plugin-settings-c2`, from local `main` at 1032e797
(C1 merged; `./gradlew check` on the merged tree: 3,648 tests, 0 failures, 5 skipped).
Record every deviation from this text here and in `docs/STATUS.md`.

Scope notes (decided while writing this plan):
- Plugin pages come from `PluginRuntime.settingsPages(PageContext)`, which the application passes to `CorePages`,
  rather than through `Contributions`: the application builds Settings and already owns the runtime, and a
  contributions kind would have one consumer.
- `EditorHint.Directory` shows a text field. A folder chooser needs a directory picker on `AuxiliarySurface`, which
  has only an existing-file picker; `EditorHint.File` gets Browse… through that picker.
- The Plugins page's "Open Settings" opens a running plugin's page; for a plugin that is not running it opens the
  file as before, instead of the stub page (which would only offer the same file).
- The Sample plugin becomes the custom-page example here (it moves to declarations now, ahead of C3).

Deviations so far: none.

**Goal:** Each running plugin with settings gets a page under Tools in Settings, generated from its declarations or
built by the plugin itself, and a plugin that is not running gets a stub page.

**Architecture:** The SDK gains `SettingsPanel` and `SettingsPanelHost` and `PluginSettings.page(...)`. In
`dev.jasper.app.plugins`, `PluginSettingsPage` wraps a `settingsui.SchemaPage` over the plugin's own
`SettingsDraft` and saves it through `DeclaredSettings.save(SettingsEdit)`; `PluginPanelPage` hosts a plugin's own
component, handing it standard rows bound to the same kind of draft; `PluginStubPage` explains why a plugin is not
running. `PluginRuntime.settingsPages` collects them, `CorePages` places them under Tools, and the Plugins page's
"Open Settings" navigates to them.

**Tech Stack:** Java 25 (JetBrains Runtime), Swing with FlatLaf 3.7, JUnit 5, AssertJ, Gradle wrapper.

**Spec:** `docs/superpowers/specs/2026-09-28-jasper-plugin-settings-design.md` (sections 1 "Custom pages", 2
"Pages" to "Plugins page", 3 "Sample", 4, and "Delivery"). The C1 plan,
`docs/superpowers/plans/2026-09-28-jasper-plugin-settings-c1.md`, built the back end this plan uses.

## Global Constraints

- Build and test only with `./gradlew` from `/Users/dustin/projects/moray/.worktrees/plugin-settings-c2`; never launch the GUI.
- Run `git branch --show-current` before every commit and confirm `claude/plugin-settings-c2`.
- Every commit message ends with `Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>`. Do not push; do not merge.
- SDK: JDK-only; complete Javadoc under `-Xdoclint:all` (every public method's `@param`, `@return` and `@throws` on
  lines of their own). `JasperSdk.VERSION` stays `0.8.5`.
- SDK types appear in the application only inside `dev.jasper.app.plugins`; `settingsui` depends on no plugin
  package. `verifyApplicationArchitecture` and `verifySdkArchitecture` must pass.
- Plugin code (panel methods, rules) runs under `Containment`; nothing a plugin throws reaches Settings.
- Page ids: a plugin's page is `plugin.<plugin id>`; its parent is `tools`; help anchor `plugins`.
- Source hygiene per AGENTS.md: write `"…"` for the ellipsis in Java strings; no raw non-ASCII in Java source.
- `AppDocumentationTest` checks Markdown links in `docs/` (plans included) and that the guide's `example:plugin`
  block equals the Sample source between its `example:plugin` markers.

## File Structure

New:
- SDK `jasper-sdk/src/main/java/dev/jasper/sdk/settings/SettingsPanel.java`, `SettingsPanelHost.java`.
- Testkit `jasper-sdk-testkit/src/main/java/dev/jasper/sdk/testing/FakeSettingsPanelHost.java`.
- App `jasper-app/src/main/java/dev/jasper/app/plugins/PluginSettingsPage.java`, `PluginPanelPage.java`, `PluginStubPage.java`.
- Sample `plugins/sample/src/main/java/dev/jasper/sample/SampleSettings.java`, `SampleSettingsPanel.java`.
- Tests: `jasper-app/src/test/java/dev/jasper/app/plugins/PluginSettingsPageTest.java`, `PluginPanelPageTest.java`,
  `PluginPreviewSupport.java` (test support).

Modified: SDK `PluginSettings.java`; testkit `FakePluginSettings.java`, `FakeSetting.java`, `FakePluginHost.java`;
app `plugins/DeclaredSettings.java`, `DeclaredSetting.java`, `PluginHost.java`, `PluginRuntime.java`,
`plugins/package-info.java`, `settingsui/Editors.java`, `settingsui/CorePages.java`, `pluginmanager/PluginManager.java`,
`pluginmanager/PluginsPage.java`, `application/JasperApplication.java`; Sample `SamplePlugin.java`; tests and docs.

---

### Task 1: The custom-page API, in the SDK and the fake

**Files:**
- Create: `jasper-sdk/src/main/java/dev/jasper/sdk/settings/SettingsPanel.java`, `SettingsPanelHost.java`
- Modify: `jasper-sdk/src/main/java/dev/jasper/sdk/settings/PluginSettings.java`
- Create: `jasper-sdk-testkit/src/main/java/dev/jasper/sdk/testing/FakeSettingsPanelHost.java`
- Modify: `jasper-sdk-testkit/src/main/java/dev/jasper/sdk/testing/FakePluginSettings.java`, `FakeSetting.java`, `FakePluginHost.java`
- Modify: `jasper-app/src/main/java/dev/jasper/app/plugins/DeclaredSettings.java`
- Test: `jasper-sdk-testkit/src/test/java/dev/jasper/sdk/testing/FakePluginSettingsTest.java`

**Interfaces:**
- Produces: `interface SettingsPanel` (`JComponent create(SettingsPanelHost)`, defaults `keywords()`, `highlight(String)`,
  `modified()`, `apply()`, `reset()`, `dispose()`); `interface SettingsPanelHost` (`JComponent row(Setting<?>)`,
  `<T> T pending(Setting<T>)`, `<T> void stage(Setting<T>, T)`, `void changed()`); `PluginSettings.page(SettingsPanel)`.
- Produces (testkit): `FakePluginHost.settingsPanel(String pluginId)` → `Optional<JComponent>`,
  `pendingSettings(String pluginId)` → `Map<String, Object>`, `settingsPanelChanges(String pluginId)` → `int`.
- Produces (app, package-private): `Optional<SettingsPanel> DeclaredSettings.panel()`.

- [ ] **Step 1: Write the failing test**

Add to `FakePluginSettingsTest.java` (imports `dev.jasper.sdk.settings.SettingsPanel`, `javax.swing.JLabel`, `javax.swing.JPanel`):

```java
    @Test void aPluginsOwnPageGetsRowsAndStagesValues() {
        var size = new AtomicReference<Setting<Integer>>();
        var settings = new AtomicReference<PluginSettings>();
        var seen = new ArrayList<String>();
        host.start(INFO, Set.of(), Set.of(), context -> {
            settings.set(context.settings());
            size.set(context.settings().declare(SettingSpec.of("font_size", SettingKind.integer(8, 48), 13).label("Font size")));
            context.settings().page(rows -> {
                var panel = new JPanel();
                panel.add(rows.row(size.get()));
                seen.add("pending " + rows.pending(size.get()));
                rows.stage(size.get(), 20);
                seen.add("pending " + rows.pending(size.get()));
                assertThatIllegalArgumentException().isThrownBy(() -> rows.stage(size.get(), 99));
                rows.changed();
                return panel;
            });
            assertThatIllegalStateException().isThrownBy(() -> context.settings().page(rows -> new JPanel()));
        });
        var component = host.settingsPanel("test.fake").orElseThrow();
        assertThat(((JLabel) component.getComponent(0)).getText()).isEqualTo("Font size");
        assertThat(seen).containsExactly("pending 13", "pending 20");
        assertThat(host.pendingSettings("test.fake")).containsEntry("font_size", 20);
        assertThat(host.settingsPanelChanges("test.fake")).isEqualTo(1);
        assertThat(size.get().get()).as("staging saves nothing").isEqualTo(13);
        assertThatIllegalStateException().isThrownBy(() -> settings.get().page(rows -> new JPanel()));
    }
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `./gradlew :jasper-sdk-testkit:test --tests 'dev.jasper.sdk.testing.FakePluginSettingsTest'`
Expected: compilation fails: `PluginSettings.page` does not exist.

- [ ] **Step 3: Implement**

Create `SettingsPanel.java`:

```java
package dev.jasper.sdk.settings;

import java.util.List;
import javax.swing.JComponent;

/**
 * A plugin's own page in Settings, used instead of the page Settings generates from its declarations, in the manner
 * of IntelliJ's {@code Configurable}. Every method runs on the UI thread. Settings saves the declared settings the page
 * shows through {@link SettingsPanelHost} on Apply; {@link #modified}, {@link #apply} and {@link #reset} cover only
 * state of the plugin's own.
 */
public interface SettingsPanel {
    /**
     * Builds the page, once per opening of Settings, when the page is first shown.
     *
     * @param host standard rows and unsaved values for the plugin's declared settings
     * @return the page's component
     */
    JComponent create(SettingsPanelHost host);

    /**
     * Extra words Settings' search matches for this page.
     *
     * @return the words
     */
    default List<String> keywords() { return List.of(); }

    /**
     * Marks what matches a search in the plugin's own controls; standard rows mark themselves.
     *
     * @param query the search text; blank clears the marks
     */
    default void highlight(String query) { }

    /**
     * Whether state of the plugin's own, beyond its declared settings, has unsaved changes.
     *
     * @return true when Apply would save something of the plugin's own
     */
    default boolean modified() { return false; }

    /** Saves state of the plugin's own; Settings saves the declared settings. */
    default void apply() { }

    /** Drops unsaved state of the plugin's own. */
    default void reset() { }

    /** Settings closed the page; release listeners. */
    default void dispose() { }
}
```

Create `SettingsPanelHost.java`:

```java
package dev.jasper.sdk.settings;

import javax.swing.JComponent;

/** What a {@link SettingsPanel} may use: standard rows for declared settings and the page's unsaved values. UI thread. */
public interface SettingsPanelHost {
    /**
     * The standard label, control, Reset button and validation message for a declared setting, bound to the page's
     * unsaved value. The row marks itself when a search matches it.
     *
     * @param setting a setting this plugin declared
     * @return the row
     */
    JComponent row(Setting<?> setting);

    /**
     * The page's unsaved value of a declared setting, or its saved value when nothing is pending.
     *
     * @param setting a setting this plugin declared
     * @param <T> the value type
     * @return the value Apply would save
     */
    <T> T pending(Setting<T> setting);

    /**
     * Sets the page's unsaved value of a declared setting from a control of the plugin's own; Apply saves it.
     *
     * @param setting a setting this plugin declared
     * @param value the new value
     * @param <T> the value type
     * @throws IllegalArgumentException when the setting's kind rejects the value
     */
    <T> void stage(Setting<T> setting, T value);

    /** Tells Settings that the page's modified state or validity changed. */
    void changed();
}
```

In `PluginSettings.java`, after `declare(...)`, add:

```java

    /**
     * Shows a page of the plugin's own in Settings instead of the page generated from its declarations. UI thread,
     * while the plugin starts; at most once.
     *
     * @param panel the page
     * @throws IllegalStateException after the plugin's start has returned, or when a page was already given
     */
    void page(SettingsPanel panel);
```

Create `FakeSettingsPanelHost.java`:

```java
package dev.jasper.sdk.testing;

import dev.jasper.sdk.settings.Setting;
import dev.jasper.sdk.settings.SettingsPanelHost;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import javax.swing.JComponent;
import javax.swing.JLabel;

/** The fake's host for a plugin's own settings page: a labelled placeholder per row, and the values it staged. */
final class FakeSettingsPanelHost implements SettingsPanelHost {
    private final FakePluginSettings settings;
    final Map<String, Object> pending = new LinkedHashMap<>();
    int changes;

    FakeSettingsPanelHost(FakePluginSettings settings) { this.settings = settings; }

    @Override public JComponent row(Setting<?> setting) { return new JLabel(settings.handle(setting).spec().label()); }

    @SuppressWarnings("unchecked")
    @Override public <T> T pending(Setting<T> setting) {
        settings.handle(setting);
        return pending.containsKey(setting.key()) ? (T) pending.get(setting.key()) : setting.get();
    }

    @SuppressWarnings("unchecked")
    @Override public <T> void stage(Setting<T> setting, T value) {
        var handle = (FakeSetting<T>) settings.handle(setting);
        Optional<String> problem = handle.spec().kind().check(value);
        if (problem.isPresent()) throw new IllegalArgumentException(problem.get());
        pending.put(setting.key(), value);
    }

    @Override public void changed() { changes++; }
}
```

In `FakeSetting.java`, add `SettingSpec<T> spec() { return spec; }`.

In `FakePluginSettings.java`, add the field `private dev.jasper.sdk.settings.SettingsPanel panel;` and:

```java
    @Override public void page(dev.jasper.sdk.settings.SettingsPanel panel) {
        Objects.requireNonNull(panel, "panel");
        if (!open) throw new IllegalStateException("Give a settings page while the plugin starts");
        if (this.panel != null) throw new IllegalStateException("A plugin has one settings page");
        this.panel = panel;
    }

    java.util.Optional<dev.jasper.sdk.settings.SettingsPanel> panel() { return java.util.Optional.ofNullable(panel); }

    /** The handle this plugin declared, by identity. */
    FakeSetting<?> handle(Setting<?> setting) {
        for (FakeSetting<?> declared : this.declared) if (declared == setting) return declared;
        throw new IllegalArgumentException("Not a setting of this plugin: " + setting.key());
    }
```

In `FakePluginHost.java`, add the field `final Map<String, FakeSettingsPanelHost> panelHosts = new java.util.concurrent.ConcurrentHashMap<>();`
and after `declared(String pluginId)`:

```java
    /**
     * Builds a plugin's own settings page as Settings would, over a host whose rows are labelled placeholders.
     *
     * @param pluginId the plugin
     * @return the page's component; empty when the plugin gave no page or is not running
     */
    public java.util.Optional<javax.swing.JComponent> settingsPanel(String pluginId) {
        FakePluginContext context = contexts.get(pluginId);
        if (context == null) return java.util.Optional.empty();
        return context.settings.panel().map(panel -> {
            var rows = new FakeSettingsPanelHost(context.settings);
            panelHosts.put(pluginId, rows);
            return panel.create(rows);
        });
    }

    /**
     * The values a plugin's own settings page staged since {@link #settingsPanel} built it.
     *
     * @param pluginId the plugin
     * @return the values by dotted key
     */
    public Map<String, Object> pendingSettings(String pluginId) {
        FakeSettingsPanelHost rows = panelHosts.get(pluginId);
        return rows == null ? Map.of() : Map.copyOf(rows.pending);
    }

    /**
     * How often a plugin's own settings page said its state changed.
     *
     * @param pluginId the plugin
     * @return the count since {@link #settingsPanel} built it
     */
    public int settingsPanelChanges(String pluginId) {
        FakeSettingsPanelHost rows = panelHosts.get(pluginId);
        return rows == null ? 0 : rows.changes;
    }
```

In the application's `DeclaredSettings.java`, add the field `private volatile SettingsPanel panel;`, the import
`dev.jasper.sdk.settings.SettingsPanel`, and:

```java
    @Override public void page(SettingsPanel panel) {
        Objects.requireNonNull(panel, "panel");
        if (!open) throw new IllegalStateException("Give a settings page while the plugin starts");
        if (this.panel != null) throw new IllegalStateException("A plugin has one settings page");
        this.panel = panel;
    }

    Optional<SettingsPanel> panel() { return Optional.ofNullable(panel); }
```

- [ ] **Step 4: Run the tests to verify they pass**

Run: `./gradlew :jasper-sdk-testkit:test :jasper-sdk:javadoc :jasper-sdk-testkit:javadoc :jasper-app:compileJava verifySdkArchitecture`
Expected: PASS, no doclint errors in the new files.

- [ ] **Step 5: Commit**

```bash
git branch --show-current   # must print claude/plugin-settings-c2
git add jasper-sdk/src jasper-sdk-testkit/src jasper-app/src/main/java/dev/jasper/app/plugins/DeclaredSettings.java
git commit -m "Let plugins give Settings a page of their own (SDK and fake)

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 2: Generated plugin pages

**Files:**
- Modify: `jasper-app/src/main/java/dev/jasper/app/settingsui/Editors.java`
- Modify: `jasper-app/src/main/java/dev/jasper/app/plugins/DeclaredSettings.java`, `DeclaredSetting.java`, `PluginHost.java`, `PluginRuntime.java`, `package-info.java`
- Create: `jasper-app/src/main/java/dev/jasper/app/plugins/PluginSettingsPage.java`
- Test: `jasper-app/src/test/java/dev/jasper/app/settingsui/EditorsTest.java`, `jasper-app/src/test/java/dev/jasper/app/plugins/PluginSettingsPageTest.java` (new)

**Interfaces:**
- Produces: `Editors.path(String title)` (a text field with Browse…) and its editor class `Editors.Browse` (package-private `browse()`).
- Produces (package-private, `DeclaredSettings`): `CompletableFuture<Void> save(SettingsEdit edit)`, `boolean rejected()`,
  `List<DeclaredSetting<?>> handles()`, `boolean declaresAnything()`, `Runnable onReloaded(Runnable listener)` (returns the remover),
  `<T> DeclaredSetting<T> handle(dev.jasper.sdk.settings.Setting<T>)`.
- Produces: `DeclaredSetting.sdkValue(Object appValue)` → `T`.
- Produces: `public static final String PluginRuntime.TOOLS = "tools"`, `public static String PluginRuntime.settingsPageId(String pluginId)`
  (`"plugin." + pluginId`); package-private `List<SettingsPage> PluginHost.settingsPages(PageContext)`.
- Produces: `final class PluginSettingsPage implements SettingsPage` with package-private `draft()`, `declared()`, `message()`,
  and `static void row(SchemaPage.Spec, DeclaredSetting<?>)`.

- [ ] **Step 1: Write the failing tests**

Add to `EditorsTest.java`:

```java
    @Test void aPathEditorBrowsesForAFile() {
        var tool = dev.jasper.app.settings.Setting.of("tool", dev.jasper.app.settings.SettingKind.string(text -> true, ""), "")
            .label("Tool").build();
        var editor = (Editors.Browse) build(Editors.path("Choose a file"), tool, context);
        editor.browse();
        assertThat(editor.value()).isEqualTo("/bin/zsh");
    }
```

Create `PluginSettingsPageTest.java`:

```java
package dev.jasper.app.plugins;

import static dev.jasper.app.plugins.AppContractTest.onEdt;
import static dev.jasper.app.plugins.AppContractTest.onEdtValue;
import static org.assertj.core.api.Assertions.*;

import dev.jasper.app.config.ConfigDiagnostic;
import dev.jasper.app.contributions.Contributions;
import dev.jasper.app.settings.Setting;
import dev.jasper.app.settings.TomlDocument;
import dev.jasper.app.settingsui.PageContext;
import dev.jasper.app.settingsui.SettingsPage;
import dev.jasper.app.terminals.TerminalRegistry;
import dev.jasper.sdk.PluginInfo;
import dev.jasper.sdk.settings.Choice;
import dev.jasper.sdk.settings.PluginSettings;
import dev.jasper.sdk.settings.SettingKind;
import dev.jasper.sdk.settings.SettingSpec;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class PluginSettingsPageTest {
    private static final String ID = "test.pages";
    @TempDir Path data;
    private final List<ConfigDiagnostic> diagnostics = new CopyOnWriteArrayList<>();
    private final List<String> navigated = new ArrayList<>();
    private final int[] changes = {0};
    private PluginHost host;
    private final PageContext context = new PageContext(null, navigated::add, Runnable::run, (title, initial) -> Optional.empty(),
        () -> changes[0]++);

    @BeforeEach void open() {
        host = AppContractTest.host(data, Map.of(), Duration.ofMillis(200), onEdtValue(Contributions::new), new AtomicBoolean(true),
            onEdtValue(AppContractTest::headlessWindows), onEdtValue(TerminalRegistry::new), new CopyOnWriteArrayList<>(),
            new CopyOnWriteArrayList<>(), diagnostics);
    }

    @AfterEach void stop() {
        var pending = new AtomicReference<List<java.util.concurrent.CompletableFuture<?>>>(List.of());
        onEdt(() -> pending.set(host.stop()));
        java.util.concurrent.CompletableFuture.allOf(pending.get().toArray(java.util.concurrent.CompletableFuture[]::new)).join();
    }

    private Path file(String id) { return data.resolve(id).resolve(id + ".toml"); }

    private void write(String id, String text) throws Exception {
        Files.createDirectories(file(id).getParent());
        Files.writeString(file(id), text);
    }

    private void start(String id, String name, Consumer<PluginSettings> declare) {
        onEdt(() -> host.start(new HostedPlugin(new PluginInfo(id, name, "1.0.0", Set.of()), Set.of(), Set.of(), Set.of(),
            PluginSettingsPageTest.class.getClassLoader(), () -> started -> declare.accept(started.settings()))));
        assertThat(host.active(id)).isTrue();
    }

    private void reload() {
        onEdt(() -> {
            var changed = host.environment.settingsFiles().refresh(true);
            for (String id : changed) host.environment.clearReports().accept(host.environment.settingsFiles().file(id));
            host.settingsChanged(changed);
        });
    }

    private PluginSettingsPage page() {
        var pages = onEdtValue(() -> host.settingsPages(context));
        assertThat(pages).singleElement().isInstanceOf(PluginSettingsPage.class);
        return (PluginSettingsPage) pages.getFirst();
    }

    private static void declareEditor(PluginSettings settings) {
        settings.declare(SettingSpec.of("font_size", SettingKind.integer(8, 48), 13).label("Font size")
            .description("Font size in points.").section("Editor"));
        settings.declare(SettingSpec.of("vim", SettingKind.bool(), false).label("Vim mode").section("Editor"));
        settings.declare(SettingSpec.of("sftp.parallel", SettingKind.integer(1, 8), 2).label("Parallel files").section("SFTP"));
        settings.declare(SettingSpec.of("mode", SettingKind.choice(List.of(Choice.of("fast", "Fast", 1), Choice.of("safe", "Safe", 2))), 1)
            .label("Mode").section("SFTP"));
    }

    @Test void thePageFollowsTheDeclarationsUnderTools() {
        start(ID, "Pages", PluginSettingsPageTest::declareEditor);
        start("test.empty", "Empty", settings -> { });
        var page = page();
        onEdt(() -> {
            assertThat(page.id()).isEqualTo("plugin." + ID);
            assertThat(page.title()).isEqualTo("Pages");
            assertThat(page.parent()).contains("tools");
            assertThat(page.helpAnchor()).isEqualTo("plugins");
            assertThat(page.searchTerms()).contains("Pages", "Font size", "Font size in points.", "Editor", "SFTP", "Fast", "Safe");
            assertThat(page.component()).isNotNull();
            assertThat(page.modified()).isFalse();
            assertThat(page.valid()).isTrue();
        });
    }

    @Test @SuppressWarnings("unchecked") void applyWritesThePluginsFileAndTheDraftFollows() throws Exception {
        write(ID, "# Pages\nfont_size = 13\n");
        start(ID, "Pages", PluginSettingsPageTest::declareEditor);
        var page = page();
        var size = (Setting<Integer>) page.declared().handles().get(0).appSetting();
        var parallel = (Setting<Integer>) page.declared().handles().get(2).appSetting();
        onEdt(() -> {
            page.component();
            page.draft().stage(size, Optional.of(15));
            page.draft().stage(parallel, Optional.of(4));
            assertThat(page.modified()).isTrue();
            assertThat(changes[0]).as("the dialog hears about draft changes").isPositive();
        });
        var applied = new AtomicReference<java.util.concurrent.CompletableFuture<Void>>();
        onEdt(() -> applied.set(page.apply()));
        applied.get().get(5, TimeUnit.SECONDS);
        onEdt(() -> { });
        String text = Files.readString(file(ID));
        assertThat(text).startsWith("# Pages\n");
        assertThat(TomlDocument.leaves(text)).containsEntry(List.of("font_size"), 15L).containsEntry(List.of("sftp", "parallel"), 4L);
        onEdt(() -> assertThat(page.modified()).as("the draft follows the saved file").isFalse());
    }

    @Test @SuppressWarnings("unchecked") void aRejectedFileShowsItsMessageAndBlocksApplyUntilFixed() throws Exception {
        write(ID, "font_size = \"big\"\n");
        start(ID, "Pages", PluginSettingsPageTest::declareEditor);
        var page = page();
        onEdt(() -> {
            page.component();
            assertThat(page.valid()).isFalse();
            assertThat(page.message().isVisible()).isTrue();
            assertThat(page.message().getText()).contains("has errors");
        });
        write(ID, "font_size = 20\n");
        reload();
        var size = (Setting<Integer>) page.declared().handles().get(0).appSetting();
        onEdt(() -> {
            assertThat(page.valid()).isTrue();
            assertThat(page.message().isVisible()).isFalse();
            assertThat(page.draft().value(size)).isEqualTo(20);
        });
    }

    @Test @SuppressWarnings("unchecked") void resetDropsPendingEditsAndAChangeOnDiskMovesTheBaseline() throws Exception {
        write(ID, "font_size = 13\n");
        start(ID, "Pages", PluginSettingsPageTest::declareEditor);
        var page = page();
        var size = (Setting<Integer>) page.declared().handles().get(0).appSetting();
        var vim = (Setting<Boolean>) page.declared().handles().get(1).appSetting();
        onEdt(() -> {
            page.component();
            page.draft().stage(vim, Optional.of(true));
            page.reset();
            assertThat(page.modified()).isFalse();
            page.draft().stage(vim, Optional.of(true));
        });
        write(ID, "font_size = 20\n");
        reload();
        onEdt(() -> {
            assertThat(page.draft().baseline().get(size)).isEqualTo(20);
            assertThat(page.modified()).as("the pending edit stays").isTrue();
            page.dispose();
        });
    }
}
```

- [ ] **Step 2: Run the tests to verify they fail**

Run: `./gradlew :jasper-app:test --tests 'dev.jasper.app.plugins.PluginSettingsPageTest' --tests 'dev.jasper.app.settingsui.EditorsTest'`
Expected: compilation fails: `Editors.path`, `PluginHost.settingsPages` and `PluginSettingsPage` do not exist.

- [ ] **Step 3: Implement**

In `Editors.java`, after `executable()`, add:

```java
    /** A text field with Browse…, for a file path. */
    public static Factory<String> path(String title) {
        return new Factory<>((setting, context) -> new Browse(context, title), List.of());
    }
```

and after the `Executable` class:

```java
    static final class Browse implements ValueEditor<String> {
        private final Line line = new Line();
        private final JComponent component;
        private final PageContext context;
        private final String title;
        Browse(PageContext context, String title) {
            this.context = context;
            this.title = title;
            var browse = new JButton("Browse…");
            browse.addActionListener(event -> browse());
            component = line(line.field, browse);
        }
        void browse() {
            String current = line.value();
            context.chooseFile().apply(title, current.isBlank() ? Optional.empty() : Optional.of(java.nio.file.Path.of(current)))
                .ifPresent(path -> line.field.setText(path.toString()));
        }
        @Override public JComponent component() { return component; }
        @Override public String value() { return line.value(); }
        @Override public void show(String value) { line.show(value); }
        @Override public void onEdit(Runnable listener) { line.onEdit(listener); }
    }
```

In `DeclaredSetting.java`, add:

```java
    @SuppressWarnings("unchecked")
    T sdkValue(Object appValue) { return (T) translated.toSdk().apply(appValue); }
```

In `DeclaredSettings.java`:
1. Add the import `dev.jasper.app.config.SettingsEdit` and the fields:

```java
    private final CopyOnWriteArrayList<Runnable> reloadListeners = new CopyOnWriteArrayList<>();
    private volatile boolean rejected;
```

2. In `reload(boolean report)`, replace

```java
        if (result.rejected()) return;
        SettingValues before = values;
        values = result.values();
```

   with

```java
        rejected = result.rejected();
        if (rejected) { reloadListeners.forEach(Runnable::run); return; }
        SettingValues before = values;
        values = result.values();
        reloadListeners.forEach(Runnable::run);
```

3. Replace the whole `save(DeclaredSetting<T> handle, T value)` method and the `edit(...)` method with:

```java
    /** Checks and writes one value off the UI thread; the future completes after the handles show it. */
    <T> CompletableFuture<Void> save(DeclaredSetting<T> handle, T value) {
        Objects.requireNonNull(value, "value");
        Optional<String> problem = handle.spec().kind().check(value);
        if (problem.isPresent()) return CompletableFuture.failedFuture(new IllegalArgumentException(problem.get()));
        @SuppressWarnings("unchecked") Setting<Object> setting = (Setting<Object>) handle.appSetting();
        return save(SettingsEdit.of(setting, handle.toApp(value), values));
    }

    /** Writes a set of edits off the UI thread; the future completes after the handles show them. */
    CompletableFuture<Void> save(SettingsEdit edit) {
        if (closed) return CompletableFuture.failedFuture(new IllegalStateException("The plugin has stopped."));
        SettingsSchema current = schema;
        var done = new CompletableFuture<Void>();
        Thread.ofVirtual().name("jasper-plugin-settings-save").start(() -> {
            try {
                files.save(pluginId, text -> edit(current, text, edit));
                ui.accept(() -> { written.run(); done.complete(null); });
            } catch (IOException | RuntimeException failure) {
                done.completeExceptionally(failure);
            }
        });
        return done;
    }

    private String edit(SettingsSchema current, String text, SettingsEdit edit) {
        String name = file().getFileName().toString();
        var read = SchemaReader.read(current, text);
        if (read.rejected()) throw new IllegalStateException(name + " has errors; fix them before saving settings.");
        TomlDocument document = TomlDocument.parse(text);
        for (var change : edit.changes().entrySet()) {
            Setting<?> setting = change.getKey();
            if (!read.values().saved(setting).equals(edit.expected().get(setting)))
                throw new IllegalStateException(setting.name() + " changed on disk. Review the new value and try again.");
            document = write(document, setting, change.getValue());
        }
        String updated = document.text();
        var checked = SchemaReader.read(current, updated);
        for (var change : edit.changes().entrySet())
            if (checked.rejected() || !checked.values().saved(change.getKey()).equals(change.getValue()))
                throw new IllegalStateException("Cannot safely save " + change.getKey().name() + "; review " + name + ".");
        return updated;
    }

    @SuppressWarnings("unchecked")
    private static <T> TomlDocument write(TomlDocument document, Setting<T> setting, Optional<?> value) {
        return SettingWriter.write(document, setting, (Optional<T>) value);
    }

    boolean rejected() { return rejected; }

    boolean declaresAnything() { return !handles.isEmpty(); }

    List<DeclaredSetting<?>> handles() { return List.copyOf(handles); }

    /** Runs {@code listener} on the UI thread after each read of the file; the returned action removes it. */
    Runnable onReloaded(Runnable listener) {
        reloadListeners.add(Objects.requireNonNull(listener, "listener"));
        return () -> reloadListeners.remove(listener);
    }

    /** The handle this plugin declared, by identity. */
    @SuppressWarnings("unchecked")
    <T> DeclaredSetting<T> handle(dev.jasper.sdk.settings.Setting<T> setting) {
        for (DeclaredSetting<?> declared : handles) if (declared == setting) return (DeclaredSetting<T>) declared;
        throw new IllegalArgumentException("Not a setting of this plugin: " + setting.key());
    }
```

4. In `close()`, add `reloadListeners.clear();`.

In `PluginRuntime.java`, add near the top of the class:

```java
    /** The parent of plugins' pages in Settings. */
    public static final String TOOLS = "tools";

    /**
     * The id of a plugin's page in Settings.
     *
     * @param pluginId the plugin
     * @return {@code plugin.<id>}
     */
    public static String settingsPageId(String pluginId) { return "plugin." + pluginId; }
```

Create `PluginSettingsPage.java`:

```java
package dev.jasper.app.plugins;

import dev.jasper.app.settings.Setting;
import dev.jasper.app.settingsui.Editors;
import dev.jasper.app.settingsui.PageContext;
import dev.jasper.app.settingsui.SchemaPage;
import dev.jasper.app.settingsui.SettingsDraft;
import dev.jasper.app.settingsui.SettingsPage;
import dev.jasper.sdk.settings.Choice;
import dev.jasper.sdk.settings.EditorHint;
import java.awt.BorderLayout;
import java.awt.Color;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import javax.swing.BorderFactory;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.Scrollable;
import javax.swing.UIManager;

/**
 * The page Settings generates for a running plugin's declared settings: rows in declaration order under their
 * sections, over the plugin's own draft, saved to the plugin's file on Apply. EDT only.
 */
final class PluginSettingsPage implements SettingsPage {
    private final String title;
    private final DeclaredSettings declared;
    private final SettingsDraft draft;
    private final SchemaPage inner;
    private final Runnable stopListening;
    private final dev.jasper.app.lifecycle.Subscription draftListening;
    private final JLabel message = new JLabel("This plugin's settings file has errors; fix them before changing its settings here.");
    private JComponent body;

    PluginSettingsPage(String pluginId, String title, DeclaredSettings declared, PageContext context) {
        this.title = title;
        this.declared = declared;
        draft = new SettingsDraft(declared.values());
        var own = new PageContext(draft, context.navigate(), context.closeThen(), context.chooseFile(), context.changed(), context.surface());
        inner = spec(PluginRuntime.settingsPageId(pluginId), title, declared.handles()).build(own);
        message.putClientProperty("html.disable", true);
        Color red = UIManager.getColor("Jasper.configErrorForeground");
        message.setForeground(red != null ? red : Color.RED);
        stopListening = declared.onReloaded(() -> {
            draft.rebase(declared.values());
            showMessage();
            context.changed().run();
        });
        draftListening = draft.onChanged(context.changed());
    }

    SettingsDraft draft() { return draft; }
    DeclaredSettings declared() { return declared; }
    JLabel message() { return message; }

    /** The rows of a plugin's page: declaration order, a heading wherever the section changes. */
    static SchemaPage.Spec spec(String id, String title, List<DeclaredSetting<?>> handles) {
        var spec = SchemaPage.page(id, title).parent(PluginRuntime.TOOLS).anchor("plugins");
        String section = "";
        for (DeclaredSetting<?> handle : handles) {
            String next = handle.spec().section();
            if (!next.equals(section)) {
                if (!next.isEmpty()) spec.section(next);
                section = next;
            }
            row(spec, handle);
        }
        return spec;
    }

    /** One declared setting's row, with its description as the row's note. */
    @SuppressWarnings({"unchecked", "rawtypes"})
    static void row(SchemaPage.Spec spec, DeclaredSetting<?> handle) {
        Setting setting = handle.appSetting();
        spec.row(setting, editor(handle));
        if (!handle.spec().description().isEmpty()) spec.note(handle.spec().description());
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static Editors.Factory editor(DeclaredSetting<?> handle) {
        var spec = handle.spec();
        EditorHint hint = spec.editor().orElse(null);
        if (hint instanceof EditorHint.Font font) return Editors.font(font.monospaced());
        if (hint instanceof EditorHint.File) return Editors.path("Choose a file");
        return switch (spec.kind()) {
            case dev.jasper.sdk.settings.SettingKind.OneOf<?> oneOf -> Editors.choice(names(oneOf));
            case dev.jasper.sdk.settings.SettingKind.NumberRange range -> Editors.number(0.1, "");
            default -> Editors.byKind((Setting) handle.appSetting());
        };
    }

    private static Map<String, String> names(dev.jasper.sdk.settings.SettingKind.OneOf<?> oneOf) {
        var names = new LinkedHashMap<String, String>();
        for (Choice<?> choice : oneOf.choices()) names.put(choice.token(), choice.displayName());
        return names;
    }

    @Override public String id() { return inner.id(); }
    @Override public String title() { return title; }
    @Override public Optional<String> parent() { return Optional.of(PluginRuntime.TOOLS); }
    @Override public String helpAnchor() { return "plugins"; }
    @Override public List<String> searchTerms() { return inner.searchTerms(); }

    @Override public JComponent component() {
        if (body == null) {
            body = new Holder(message, inner.component());
            showMessage();
        }
        return body;
    }

    @Override public boolean modified() { return draft.modified(); }
    @Override public boolean valid() { return !declared.rejected() && inner.valid(); }
    @Override public void reset() { inner.reset(); }

    /** Saves the draft; the plugin's reload moves the draft's baseline, which drops the edits now saved. */
    @Override public CompletableFuture<Void> apply() {
        return draft.modified() ? declared.save(draft.edit()) : CompletableFuture.completedFuture(null);
    }

    @Override public void highlight(String query) { inner.highlight(query); }

    @Override public void refresh() {
        draft.rebase(declared.values());
        inner.refresh();
        showMessage();
    }

    @Override public void dispose() {
        stopListening.run();
        draftListening.close();
        inner.dispose();
    }

    private void showMessage() { message.setVisible(declared.rejected()); }

    /** The message above the rows; follows the dialog's width like the rows do. */
    private static final class Holder extends JPanel implements Scrollable {
        Holder(JComponent message, JComponent rows) {
            super(new BorderLayout());
            setOpaque(false);
            message.setBorder(BorderFactory.createEmptyBorder(12, 20, 0, 20));
            add(message, BorderLayout.NORTH);
            add(rows, BorderLayout.CENTER);
        }
        @Override public java.awt.Dimension getPreferredScrollableViewportSize() { return getPreferredSize(); }
        @Override public int getScrollableUnitIncrement(java.awt.Rectangle visible, int orientation, int direction) { return 16; }
        @Override public int getScrollableBlockIncrement(java.awt.Rectangle visible, int orientation, int direction) { return visible.height; }
        @Override public boolean getScrollableTracksViewportWidth() { return true; }
        @Override public boolean getScrollableTracksViewportHeight() { return false; }
    }
}
```

In `PluginHost.java`, add (with the imports `dev.jasper.app.settingsui.PageContext` and `dev.jasper.app.settingsui.SettingsPage`):

```java
    /** Settings pages for running plugins that declared settings, in start order. UI thread. */
    List<SettingsPage> settingsPages(PageContext context) {
        requireUi();
        var pages = new ArrayList<SettingsPage>();
        for (HostedContext hosted : contexts.values()) {
            if (hosted.state != HostedContext.State.ACTIVE || !hosted.declared.declaresAnything()) continue;
            var info = hosted.hosted.info();
            pages.add(new PluginSettingsPage(info.id(), info.name(), hosted.declared, context));
        }
        return pages;
    }
```

In `plugins/package-info.java`, add `dev.jasper.app.lifecycle, dev.jasper.app.settingsui, ` before `dev.jasper.app.terminals` in the allowed list.

- [ ] **Step 4: Run the tests to verify they pass**

Run: `./gradlew :jasper-app:test --tests 'dev.jasper.app.plugins.*' --tests 'dev.jasper.app.settingsui.*' verifyApplicationArchitecture`
Expected: PASS (`DeclaredSettingsTest` still passes with the generalised save).

- [ ] **Step 5: Commit**

```bash
git branch --show-current   # must print claude/plugin-settings-c2
git add jasper-app/src
git commit -m "Generate a Settings page for each running plugin's declared settings

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 3: Custom and stub pages, and the runtime's page list

**Files:**
- Create: `jasper-app/src/main/java/dev/jasper/app/plugins/PluginPanelPage.java`, `PluginStubPage.java`
- Modify: `jasper-app/src/main/java/dev/jasper/app/plugins/PluginHost.java`, `PluginRuntime.java`
- Test: `jasper-app/src/test/java/dev/jasper/app/plugins/PluginPanelPageTest.java` (new)

**Interfaces:**
- Consumes: Task 1's `DeclaredSettings.panel()`, Task 2's `PluginSettingsPage.row`, `DeclaredSettings.handle/handles/save/onReloaded/rejected`.
- Produces: `public List<SettingsPage> PluginRuntime.settingsPages(PageContext context)`,
  `public boolean PluginRuntime.hasSettingsPage(String pluginId)`, package-private `static boolean PluginRuntime.stubbed(PluginStatus)`,
  `boolean PluginHost.hasSettingsPage(String)`.

- [ ] **Step 1: Write the failing test**

Create `PluginPanelPageTest.java`:

```java
package dev.jasper.app.plugins;

import static dev.jasper.app.plugins.AppContractTest.onEdt;
import static dev.jasper.app.plugins.AppContractTest.onEdtValue;
import static org.assertj.core.api.Assertions.*;

import dev.jasper.app.config.ConfigDiagnostic;
import dev.jasper.app.contributions.Contributions;
import dev.jasper.app.settings.TomlDocument;
import dev.jasper.app.settingsui.PageContext;
import dev.jasper.app.settingsui.SettingsPage;
import dev.jasper.app.terminals.TerminalRegistry;
import dev.jasper.sdk.PluginInfo;
import dev.jasper.sdk.settings.PluginSettings;
import dev.jasper.sdk.settings.Setting;
import dev.jasper.sdk.settings.SettingKind;
import dev.jasper.sdk.settings.SettingSpec;
import dev.jasper.sdk.settings.SettingsPanel;
import dev.jasper.sdk.settings.SettingsPanelHost;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class PluginPanelPageTest {
    private static final String ID = "test.panel";
    @TempDir Path data;
    private final List<ConfigDiagnostic> diagnostics = new CopyOnWriteArrayList<>();
    private final int[] changes = {0};
    private PluginHost host;
    private final PageContext context = new PageContext(null, id -> { }, Runnable::run, (title, initial) -> Optional.empty(),
        () -> changes[0]++);

    @BeforeEach void open() {
        host = AppContractTest.host(data, Map.of(), Duration.ofMillis(200), onEdtValue(Contributions::new), new AtomicBoolean(true),
            onEdtValue(AppContractTest::headlessWindows), onEdtValue(TerminalRegistry::new), new CopyOnWriteArrayList<>(),
            new CopyOnWriteArrayList<>(), diagnostics);
    }

    @AfterEach void stop() {
        var pending = new AtomicReference<List<java.util.concurrent.CompletableFuture<?>>>(List.of());
        onEdt(() -> pending.set(host.stop()));
        java.util.concurrent.CompletableFuture.allOf(pending.get().toArray(java.util.concurrent.CompletableFuture[]::new)).join();
    }

    private void start(Consumer<PluginSettings> declare) {
        onEdt(() -> host.start(new HostedPlugin(new PluginInfo(ID, "Panel", "1.0.0", Set.of()), Set.of(), Set.of(), Set.of(),
            PluginPanelPageTest.class.getClassLoader(), () -> started -> declare.accept(started.settings()))));
        assertThat(host.active(ID)).isTrue();
    }

    /** A plugin page with one standard row, one control of its own for {@code size}, and its own state. */
    private static final class Panel implements SettingsPanel {
        final Setting<Boolean> vim;
        final Setting<Integer> size;
        final List<String> calls = new ArrayList<>();
        boolean ownModified;
        SettingsPanelHost host;
        Panel(Setting<Boolean> vim, Setting<Integer> size) { this.vim = vim; this.size = size; }
        @Override public JComponent create(SettingsPanelHost host) {
            this.host = host;
            var page = new JPanel();
            page.add(host.row(vim));
            page.add(new JLabel("size " + host.pending(size)));
            return page;
        }
        @Override public List<String> keywords() { return List.of("panel", "custom"); }
        @Override public void highlight(String query) { calls.add("highlight " + query); }
        @Override public boolean modified() { return ownModified; }
        @Override public void apply() { calls.add("apply"); ownModified = false; }
        @Override public void reset() { calls.add("reset"); ownModified = false; }
        @Override public void dispose() { calls.add("dispose"); }
    }

    private Panel startWithPanel() {
        var panel = new AtomicReference<Panel>();
        start(settings -> {
            var vim = settings.declare(SettingSpec.of("vim", SettingKind.bool(), false).label("Vim mode").keywords("modal"));
            var size = settings.declare(SettingSpec.of("font_size", SettingKind.integer(8, 48), 13).label("Font size"));
            panel.set(new Panel(vim, size));
            settings.page(panel.get());
        });
        return panel.get();
    }

    @Test void theOwnPageGetsRowsStagesValuesAndAppliesBothKindsOfState() throws Exception {
        Files.createDirectories(data.resolve(ID));
        Files.writeString(data.resolve(ID).resolve(ID + ".toml"), "font_size = 13\n");
        var panel = startWithPanel();
        var pages = onEdtValue(() -> host.settingsPages(context));
        assertThat(pages).singleElement().isInstanceOf(PluginPanelPage.class);
        SettingsPage page = pages.getFirst();
        onEdt(() -> {
            assertThat(page.id()).isEqualTo("plugin." + ID);
            assertThat(page.parent()).contains("tools");
            assertThat(page.searchTerms()).contains("Panel", "Vim mode", "modal", "panel", "custom");
            page.component();
            assertThat(panel.host).isNotNull();
            panel.host.stage(panel.size, 20);
            assertThat(panel.host.pending(panel.size)).isEqualTo(20);
            assertThatIllegalArgumentException().isThrownBy(() -> panel.host.stage(panel.size, 99));
            assertThat(page.modified()).isTrue();
            panel.ownModified = true;
            page.highlight("vim");
            assertThat(panel.calls).containsExactly("highlight vim");
        });
        var applied = new AtomicReference<java.util.concurrent.CompletableFuture<Void>>();
        onEdt(() -> applied.set(page.apply()));
        applied.get().get(5, TimeUnit.SECONDS);
        onEdt(() -> { });
        assertThat(TomlDocument.leaves(Files.readString(data.resolve(ID).resolve(ID + ".toml")))).containsEntry(List.of("font_size"), 20L);
        onEdt(() -> {
            assertThat(panel.calls).contains("apply");
            assertThat(page.modified()).isFalse();
            page.dispose();
            assertThat(panel.calls).contains("dispose");
        });
    }

    @Test void aPageThatFailsToBuildShowsWhyAndSettingsCarriesOn() {
        start(settings -> {
            settings.declare(SettingSpec.of("vim", SettingKind.bool(), false).label("Vim mode"));
            settings.page(rows -> { throw new IllegalStateException("boom"); });
        });
        var page = onEdtValue(() -> host.settingsPages(context)).getFirst();
        onEdt(() -> {
            var component = page.component();
            assertThat(component).isInstanceOf(JLabel.class);
            assertThat(((JLabel) component).getText()).isEqualTo("This page could not be shown: boom");
        });
        assertThat(host.failures(ID)).isPositive();
    }

    @Test void pluginsThatAreNotRunningGetAStubAndOnlyWhenTheUserMeantThemToRun() {
        var opened = new ArrayList<String>();
        var stub = new PluginStubPage("dev.example.off", "Off", "not reviewed yet", () -> opened.add("open"));
        onEdt(() -> {
            assertThat(stub.id()).isEqualTo("plugin.dev.example.off");
            assertThat(stub.parent()).contains("tools");
            stub.component();
            assertThat(stub.text()).isEqualTo("Start Off to edit its settings.");
            assertThat(stub.reason()).isEqualTo("Not running: not reviewed yet");
            stub.open.doClick();
        });
        assertThat(opened).containsExactly("open");
        assertThat(PluginRuntime.stubbed(status(PluginStatus.State.NEEDS_CONSENT, "not reviewed yet"))).isTrue();
        assertThat(PluginRuntime.stubbed(status(PluginStatus.State.FAILED, "boom"))).isTrue();
        assertThat(PluginRuntime.stubbed(status(PluginStatus.State.SKIPPED, "requires x"))).isTrue();
        assertThat(PluginRuntime.stubbed(status(PluginStatus.State.DISABLED, "safe mode"))).isTrue();
        assertThat(PluginRuntime.stubbed(status(PluginStatus.State.DISABLED, "disabled"))).isFalse();
        assertThat(PluginRuntime.stubbed(status(PluginStatus.State.ACTIVE, ""))).isFalse();
    }

    private static PluginStatus status(PluginStatus.State state, String reason) {
        return new PluginStatus("dev.example.x", "X", "1.0.0", PluginCandidate.Origin.USER, state, reason);
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `./gradlew :jasper-app:test --tests 'dev.jasper.app.plugins.PluginPanelPageTest'`
Expected: compilation fails: `PluginPanelPage`, `PluginStubPage` and `PluginRuntime.stubbed` do not exist.

- [ ] **Step 3: Implement**

Create `PluginPanelPage.java`:

```java
package dev.jasper.app.plugins;

import dev.jasper.app.settings.Setting;
import dev.jasper.app.settingsui.PageContext;
import dev.jasper.app.settingsui.SchemaPage;
import dev.jasper.app.settingsui.SettingsDraft;
import dev.jasper.app.settingsui.SettingsPage;
import dev.jasper.sdk.settings.SettingsPanel;
import dev.jasper.sdk.settings.SettingsPanelHost;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.atomic.AtomicReference;
import javax.swing.BorderFactory;
import javax.swing.JComponent;
import javax.swing.JLabel;

/**
 * A plugin's own page in Settings: its component, standard rows for its declared settings over the plugin's own
 * draft, and the plugin's own state. EDT only; every call into the plugin runs contained.
 */
final class PluginPanelPage implements SettingsPage {
    private final String pluginId;
    private final String title;
    private final DeclaredSettings declared;
    private final SettingsPanel panel;
    private final Containment containment;
    private final PageContext context;
    private final SettingsDraft draft;
    private final PageContext own;
    private final List<SchemaPage> rows = new ArrayList<>();
    private final Runnable stopListening;
    private final dev.jasper.app.lifecycle.Subscription draftListening;
    private JComponent body;

    PluginPanelPage(String pluginId, String title, DeclaredSettings declared, SettingsPanel panel, Containment containment,
                    PageContext context) {
        this.pluginId = pluginId; this.title = title; this.declared = declared; this.panel = panel;
        this.containment = containment; this.context = context;
        draft = new SettingsDraft(declared.values());
        own = new PageContext(draft, context.navigate(), context.closeThen(), context.chooseFile(), context.changed(), context.surface());
        stopListening = declared.onReloaded(() -> { draft.rebase(declared.values()); context.changed().run(); });
        draftListening = draft.onChanged(context.changed());
    }

    @Override public String id() { return PluginRuntime.settingsPageId(pluginId); }
    @Override public String title() { return title; }
    @Override public Optional<String> parent() { return Optional.of(PluginRuntime.TOOLS); }
    @Override public String helpAnchor() { return "plugins"; }

    @Override public List<String> searchTerms() {
        var terms = new ArrayList<String>();
        terms.add(title);
        for (DeclaredSetting<?> handle : declared.handles()) {
            terms.add(handle.spec().label());
            terms.add(handle.spec().description());
            terms.addAll(handle.spec().keywords());
        }
        var extra = new AtomicReference<List<String>>(List.of());
        containment.attempt(pluginId, "settings page keywords", () -> { extra.set(List.copyOf(panel.keywords())); return null; });
        terms.addAll(extra.get());
        return List.copyOf(terms);
    }

    @Override public JComponent component() {
        if (body != null) return body;
        var made = new AtomicReference<JComponent>();
        Throwable failure = containment.attempt(pluginId, "settings page", () -> { made.set(panel.create(new Host())); return null; });
        body = failure == null && made.get() != null ? made.get() : failed(failure);
        return body;
    }

    private static JComponent failed(Throwable failure) {
        var label = new JLabel("This page could not be shown: " + (failure == null ? "it returned nothing." : failure.getMessage()));
        label.putClientProperty("html.disable", true);
        label.setBorder(BorderFactory.createEmptyBorder(16, 20, 16, 20));
        return label;
    }

    @Override public boolean modified() {
        if (draft.modified()) return true;
        boolean[] changed = {false};
        containment.attempt(pluginId, "settings page state", () -> { changed[0] = panel.modified(); return null; });
        return changed[0];
    }

    @Override public boolean valid() { return !declared.rejected() && rows.stream().allMatch(SchemaPage::valid); }

    @Override public void reset() {
        rows.forEach(SchemaPage::reset);
        draft.reset(declared.handles().stream().<Setting<?>>map(DeclaredSetting::appSetting).toList());
        containment.run(pluginId, "settings page reset", panel::reset);
    }

    /** Saves the declared settings, then lets the plugin save its own state. */
    @Override public CompletableFuture<Void> apply() {
        CompletableFuture<Void> saved = draft.modified() ? declared.save(draft.edit()) : CompletableFuture.completedFuture(null);
        return saved.thenRun(() -> {
            Throwable failure = containment.attempt(pluginId, "settings page apply", () -> { panel.apply(); return null; });
            if (failure != null) throw new CompletionException(failure);
        });
    }

    @Override public void highlight(String query) {
        rows.forEach(row -> row.highlight(query));
        containment.run(pluginId, "settings page highlight", () -> panel.highlight(query));
    }

    @Override public void dispose() {
        stopListening.run();
        draftListening.close();
        rows.forEach(SchemaPage::dispose);
        containment.run(pluginId, "settings page dispose", panel::dispose);
    }

    /** What the plugin's page may use: standard rows and the page's draft. */
    private final class Host implements SettingsPanelHost {
        @Override public JComponent row(dev.jasper.sdk.settings.Setting<?> setting) {
            DeclaredSetting<?> handle = declared.handle(setting);
            var spec = SchemaPage.page(id() + "." + handle.key(), title);
            PluginSettingsPage.row(spec, handle);
            SchemaPage page = spec.build(own);
            rows.add(page);
            return page.component();
        }

        @SuppressWarnings("unchecked")
        @Override public <T> T pending(dev.jasper.sdk.settings.Setting<T> setting) {
            DeclaredSetting<T> handle = declared.handle(setting);
            return handle.sdkValue(draft.value((Setting<Object>) handle.appSetting()));
        }

        @SuppressWarnings("unchecked")
        @Override public <T> void stage(dev.jasper.sdk.settings.Setting<T> setting, T value) {
            DeclaredSetting<T> handle = declared.handle(setting);
            handle.spec().kind().check(value).ifPresent(problem -> { throw new IllegalArgumentException(problem); });
            draft.stage((Setting<Object>) handle.appSetting(), Optional.of(handle.toApp(value)));
        }

        @Override public void changed() { context.changed().run(); }
    }
}
```

Create `PluginStubPage.java`:

```java
package dev.jasper.app.plugins;

import dev.jasper.app.settingsui.SettingsPage;
import java.awt.FlowLayout;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.UIManager;

/** Settings' page for an installed plugin that is not running: why, and a way to edit its file. EDT only. */
final class PluginStubPage implements SettingsPage {
    private final String pluginId;
    private final String title;
    private final JLabel text;
    private final JLabel reason;
    final JButton open = new JButton("Open in Editor");
    private JPanel panel;

    PluginStubPage(String pluginId, String title, String why, Runnable openFile) {
        this.pluginId = pluginId;
        this.title = title;
        text = new JLabel("Start " + title + " to edit its settings.");
        reason = new JLabel(why.isBlank() ? "" : "Not running: " + why);
        text.putClientProperty("html.disable", true);
        reason.putClientProperty("html.disable", true);
        reason.setForeground(UIManager.getColor("Label.disabledForeground"));
        open.addActionListener(event -> openFile.run());
    }

    String text() { return text.getText(); }
    String reason() { return reason.getText(); }

    @Override public String id() { return PluginRuntime.settingsPageId(pluginId); }
    @Override public String title() { return title; }
    @Override public Optional<String> parent() { return Optional.of(PluginRuntime.TOOLS); }
    @Override public String helpAnchor() { return "plugins"; }
    @Override public List<String> searchTerms() { return List.of(title); }

    @Override public JComponent component() {
        if (panel != null) return panel;
        panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setOpaque(false);
        panel.setBorder(BorderFactory.createEmptyBorder(16, 20, 16, 20));
        text.setAlignmentX(0);
        reason.setAlignmentX(0);
        panel.add(text);
        panel.add(reason);
        var buttons = new JPanel(new FlowLayout(FlowLayout.LEADING, 0, 12));
        buttons.setOpaque(false);
        buttons.setAlignmentX(0);
        buttons.add(open);
        panel.add(buttons);
        return panel;
    }

    @Override public boolean modified() { return false; }
    @Override public void reset() { }
    @Override public CompletableFuture<Void> apply() { return CompletableFuture.completedFuture(null); }
    @Override public void highlight(String query) { }
    @Override public void dispose() { }
}
```

In `PluginHost.java`, replace the body of the `for` loop in `settingsPages(PageContext)` with:

```java
            if (hosted.state != HostedContext.State.ACTIVE) continue;
            var info = hosted.hosted.info();
            var panel = hosted.declared.panel();
            if (panel.isPresent())
                pages.add(new PluginPanelPage(info.id(), info.name(), hosted.declared, panel.get(), containment, context));
            else if (hosted.declared.declaresAnything())
                pages.add(new PluginSettingsPage(info.id(), info.name(), hosted.declared, context));
```

and change its comment to `/** Settings pages for running plugins that declared settings or gave a page of their own, in start order. UI thread. */`.
Add after it:

```java
    /** Whether a running plugin has a page in Settings. UI thread. */
    boolean hasSettingsPage(String pluginId) {
        HostedContext hosted = contexts.get(pluginId);
        return hosted != null && hosted.state == HostedContext.State.ACTIVE
            && (hosted.declared.panel().isPresent() || hosted.declared.declaresAnything());
    }
```

In `PluginRuntime.java`, add (imports `dev.jasper.app.settingsui.PageContext`, `dev.jasper.app.settingsui.SettingsPage`):

```java
    /**
     * Settings pages for plugins: one for each running plugin with declared settings or a page of its own, and a stub
     * for each plugin the user meant to run that is not running. Sorted by title. UI thread.
     *
     * @param context what the pages may ask of Settings
     * @return the pages, each under {@link #TOOLS}
     */
    public List<SettingsPage> settingsPages(PageContext context) {
        PluginHost current = host;
        PluginCatalog.Launch started = launch;
        if (current == null || started == null) return List.of();
        var pages = new ArrayList<SettingsPage>(current.settingsPages(context));
        for (PluginStatus status : started.statuses())
            if (stubbed(status)) pages.add(new PluginStubPage(status.id(), status.name(), status.reason(), () -> openSettingsFile(status.id())));
        pages.sort(java.util.Comparator.comparing(SettingsPage::title, String.CASE_INSENSITIVE_ORDER));
        return pages;
    }

    /**
     * Whether a running plugin has a page in Settings. UI thread.
     *
     * @param pluginId the plugin
     * @return true when it declared settings or gave a page of its own
     */
    public boolean hasSettingsPage(String pluginId) {
        PluginHost current = host;
        return current != null && current.hasSettingsPage(pluginId);
    }

    /** Not running although the user meant it to: waiting for review, skipped, failed, or held back by safe mode. */
    static boolean stubbed(PluginStatus status) {
        return switch (status.state()) {
            case NEEDS_CONSENT, SKIPPED, FAILED -> true;
            case DISABLED -> status.reason().equals("safe mode");
            case ACTIVE -> false;
        };
    }

    /** Seeds and opens a plugin's settings file off the UI thread; a failure becomes a notice. */
    private void openSettingsFile(String id) {
        Thread.ofVirtual().name("jasper-plugin-settings-open").start(() -> {
            try {
                prepareSettings(id);
                editor.accept(PluginSettingsFiles.file(options.userDirectory(), id));
            } catch (IOException | RuntimeException failure) {
                SwingUtilities.invokeLater(() -> notice.accept(failure.getMessage() == null ? failure.toString() : failure.getMessage()));
            }
        });
    }
```

- [ ] **Step 4: Run the tests to verify they pass**

Run: `./gradlew :jasper-app:test --tests 'dev.jasper.app.plugins.*' verifyApplicationArchitecture`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git branch --show-current   # must print claude/plugin-settings-c2
git add jasper-app/src
git commit -m "Host plugins' own Settings pages, and stub pages for plugins that are not running

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 4: Plugin pages in the dialog, and Open Settings goes to them

**Files:**
- Modify: `jasper-app/src/main/java/dev/jasper/app/settingsui/CorePages.java`
- Modify: `jasper-app/src/main/java/dev/jasper/app/application/JasperApplication.java`
- Modify: `jasper-app/src/main/java/dev/jasper/app/pluginmanager/PluginManager.java`, `PluginsPage.java`
- Test: `jasper-app/src/test/java/dev/jasper/app/settingsui/CorePagesTest.java`, `jasper-app/src/test/java/dev/jasper/app/pluginmanager/PluginManagerTest.java`

**Interfaces:**
- Consumes: `PluginRuntime.settingsPages`, `hasSettingsPage`, `settingsPageId` (Tasks 2 and 3).
- Produces: `CorePages.pages(Supplier<List<String>>, SystemActions, Function<PageContext, SettingsPage> keymap,
  Function<PageContext, SettingsPage> plugins, Function<PageContext, List<SettingsPage>> tools)`; the four-argument
  form stays and supplies no tools pages. `PluginManager.attach(Supplier<AuxiliarySurface>, Consumer<String> navigate)`.

- [ ] **Step 1: Write the failing tests**

Add to `CorePagesTest.java`:

```java
    @Test void toolsPagesComeLastUnderTools() {
        var extra = new GroupPage("plugin.dev.example", "Example", Optional.of("tools"), false, List::of, context);
        var pages = CorePages.pages(() -> List.of("Jasper Dark"), system, CorePagesTest::keymap, CorePagesTest::plugins,
            page -> List.of(extra)).apply(context);
        assertThat(pages.getLast()).isSameAs(extra);
        assertThat(pages.get(pages.size() - 2).id()).isEqualTo("tools");
    }
```

In `PluginManagerTest.java`, add a `navigated` list and pass it to the page context in `open()`: add the field
`private final List<String> navigated = new CopyOnWriteArrayList<>();` and in `open()` replace
`page = manager.page(new PageContext(null, id -> { }, Runnable::run,` with `page = manager.page(new PageContext(null, navigated::add, Runnable::run,`.
Then add:

```java
    @Test void openSettingsGoesToARunningPluginsPageInSettings() throws Exception {
        String source = """
            package fix.declared;
            public final class Main implements dev.jasper.sdk.plugin.Plugin {
                @Override public void start(dev.jasper.sdk.plugin.PluginContext context) {
                    context.settings().declare(dev.jasper.sdk.settings.SettingSpec.of("size",
                        dev.jasper.sdk.settings.SettingKind.integer(1, 9), 3).label("Size"));
                }
            }
            """;
        PluginJars.build(user().resolve("dev.example.declares/jars"), "main.jar",
            PluginJars.descriptor("dev.example.declares", "1.0.0", "fix.declared.Main"), Map.of("fix.declared.Main", source), List.of());
        Files.writeString(root.resolve("plugins.toml"), "version = 1\n[plugins.\"dev.example.declares\"]\nenabled = true\nconsented = []\n");
        start(false, false);
        open();
        until(() -> manager.panel().listed().stream().anyMatch(line -> line.contains("dev.example.declares")));
        edt(() -> {
            assertThat(runtime.hasSettingsPage("dev.example.declares")).isTrue();
            manager.panel().select("dev.example.declares");
            manager.panel().clickMenu("Open Settings");
        });
        assertThat(navigated).containsExactly("plugin.dev.example.declares");
        assertThat(edited).as("a running plugin's page, not its file").isEmpty();
    }
```

- [ ] **Step 2: Run the tests to verify they fail**

Run: `./gradlew :jasper-app:test --tests 'dev.jasper.app.settingsui.CorePagesTest' --tests 'dev.jasper.app.pluginmanager.PluginManagerTest'`
Expected: `CorePagesTest` fails to compile (no five-argument `pages`); once it compiles, the new manager test fails
because Open Settings opens the file.

- [ ] **Step 3: Implement**

In `CorePages.java`, replace these lines (the start of `pages`):

```java
    /**
     * The whole tree for one dialog, in display order: group and schema pages, with the Keymap and Plugins pages the
     * application supplies.
     */
    public static Function<PageContext, List<SettingsPage>> pages(Supplier<List<String>> themeNames, SystemActions system,
                                                                   Function<PageContext, SettingsPage> keymap,
                                                                   Function<PageContext, SettingsPage> plugins) {
        return context -> {
```

with:

```java
    /**
     * The whole tree for one dialog, in display order: group and schema pages, with the Keymap and Plugins pages the
     * application supplies.
     */
    public static Function<PageContext, List<SettingsPage>> pages(Supplier<List<String>> themeNames, SystemActions system,
                                                                   Function<PageContext, SettingsPage> keymap,
                                                                   Function<PageContext, SettingsPage> plugins) {
        return pages(themeNames, system, keymap, plugins, context -> List.of());
    }

    /** As above, followed by {@code tools}: pages under Tools, such as plugins' settings pages. */
    public static Function<PageContext, List<SettingsPage>> pages(Supplier<List<String>> themeNames, SystemActions system,
                                                                   Function<PageContext, SettingsPage> keymap,
                                                                   Function<PageContext, SettingsPage> plugins,
                                                                   Function<PageContext, List<SettingsPage>> tools) {
        return context -> {
```

and, further down in the same method, replace

```java
            all.add(new GroupPage("tools", "Tools", Optional.empty(), true, childrenOf.apply("tools"), context));
```

with

```java
            all.add(new GroupPage("tools", "Tools", Optional.empty(), true, childrenOf.apply("tools"), context));
            all.addAll(tools.apply(context));
```

In `JasperApplication.java`, replace
`                    context -> new dev.jasper.app.shortcuthelp.KeymapPage(keymap, context), context -> pluginManager().page(context)),`
with

```java
                    context -> new dev.jasper.app.shortcuthelp.KeymapPage(keymap, context), context -> pluginManager().page(context),
                    context -> plugins.settingsPages(context)),
```

In `PluginManager.java`:
- Add the field `private java.util.function.Consumer<String> navigate = id -> { };`.
- Replace `PluginManagerPanel attach(Supplier<AuxiliarySurface> host) {` and its first line with:

```java
    PluginManagerPanel attach(Supplier<AuxiliarySurface> host, java.util.function.Consumer<String> navigate) {
        this.host = Objects.requireNonNull(host);
        this.navigate = Objects.requireNonNull(navigate);
```

  and change the method's comment to `/** Builds the list for a page whose dialogs open over {@code host} and which moves Settings with {@code navigate}. */`.
- In `detach(...)`, after `host = null;` add `navigate = id -> { };`.
- In `openSettings(PluginRuntime.Row row)`, add as the first line:

```java
        if (runtime.hasSettingsPage(row.id())) { navigate.accept(PluginRuntime.settingsPageId(row.id())); return; }
```

  and change its comment to `/** A running plugin with a Settings page goes there; otherwise, off the EDT, its file is seeded and opened. */`.

In `PluginsPage.java`, replace `panel = manager.attach(context.surface());` with `panel = manager.attach(context.surface(), context.navigate());`.

- [ ] **Step 4: Run the tests to verify they pass**

Run: `./gradlew :jasper-app:test --tests 'dev.jasper.app.settingsui.*' --tests 'dev.jasper.app.pluginmanager.*' --tests 'dev.jasper.app.application.*' verifyApplicationArchitecture`
Expected: PASS (the existing "Open Settings" test still opens the file: its plugin fails to start, so it has no page).

- [ ] **Step 5: Commit**

```bash
git branch --show-current   # must print claude/plugin-settings-c2
git add jasper-app/src
git commit -m "Show plugins' pages under Tools and send Open Settings there

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 5: The Sample plugin's own page

**Files:**
- Create: `plugins/sample/src/main/java/dev/jasper/sample/SampleSettings.java`, `SampleSettingsPanel.java`
- Modify: `plugins/sample/src/main/java/dev/jasper/sample/SamplePlugin.java`
- Modify: `plugins/sample/src/test/java/dev/jasper/sample/SamplePluginTest.java`
- Modify: `docs/plugin-authoring.md` (the `example:plugin` block and the settings sentence)

**Interfaces:**
- Consumes: SDK `PluginSettings.declare/page`, `SettingsPanel`, `SettingsPanelHost`; testkit `setSetting`, `settingsReports`,
  `settingsPanel`, `pendingSettings`, `settingsPanelChanges`, `declared`.

- [ ] **Step 1: Write the failing tests**

In `SamplePluginTest.java`, replace every `host.setConfig("dev.jasper.sample", Map.of(...));` with one
`host.setSetting("dev.jasper.sample", key, value);` call per key and value, in the same order (for example
`host.setConfig("dev.jasper.sample", Map.of("demo_activity", true, "demo_step_millis", 0L));` becomes
`host.setSetting("dev.jasper.sample", "demo_activity", true); host.setSetting("dev.jasper.sample", "demo_step_millis", 0L);`).
Replace the test `reportsAnOutOfRangeStepDelay` with:

```java
    @Test void reportsAnOutOfRangeStepDelay() {
        try (var host = new FakePluginHost()) {
            host.setSetting("dev.jasper.sample", "demo_step_millis", 60_000L);
            host.start(INFO, Set.of(), Set.of(), new SamplePlugin());
            assertThat(host.settingsReports("dev.jasper.sample")).containsExactly("demo_step_millis: Use a whole number from 0–5000.");
        }
    }
```

Add:

```java
    @Test void offersItsOwnSettingsPage() {
        try (var host = new FakePluginHost()) {
            host.start(INFO, Set.of(), Set.of(), new SamplePlugin());
            assertThat(host.declared("dev.jasper.sample"))
                .containsExactly("demo_activity", "demo_ui", "demo_terminal", "demo_session", "demo_scope", "demo_step_millis");
            var page = host.settingsPanel("dev.jasper.sample").orElseThrow();
            assertThat(((javax.swing.JLabel) page.getComponent(0)).getText()).isEqualTo("Run the Buddy activity at start");
            var slider = find(page, javax.swing.JSlider.class);
            assertThat(slider.getValue()).isEqualTo(300);
            slider.setValue(1200);
            assertThat(host.pendingSettings("dev.jasper.sample")).containsEntry("demo_step_millis", 1200);
            assertThat(host.settingsPanelChanges("dev.jasper.sample")).isPositive();
        }
    }

    private static <T> T find(java.awt.Container root, Class<T> type) {
        for (java.awt.Component child : root.getComponents()) {
            if (type.isInstance(child)) return type.cast(child);
            if (child instanceof java.awt.Container nested) { T found = find(nested, type); if (found != null) return found; }
        }
        return null;
    }
```

Remove `import java.util.Map;` if nothing else uses it.

- [ ] **Step 2: Run the tests to verify they fail**

Run: `./gradlew :jasper-plugin-sample:test`
Expected: `offersItsOwnSettingsPage` and `reportsAnOutOfRangeStepDelay` fail, and the demo tests fail because the
Sample still reads `PluginConfig`, which `setSetting` does not feed.

- [ ] **Step 3: Implement**

Create `SampleSettings.java`:

```java
package dev.jasper.sample;

import dev.jasper.sdk.settings.Applies;
import dev.jasper.sdk.settings.PluginSettings;
import dev.jasper.sdk.settings.Setting;
import dev.jasper.sdk.settings.SettingKind;
import dev.jasper.sdk.settings.SettingSpec;

/** The sample's declared settings. The sample reads them only when it starts, so each applies after a restart. */
final class SampleSettings {
    final Setting<Boolean> activity;
    final Setting<Boolean> ui;
    final Setting<Boolean> terminal;
    final Setting<Boolean> session;
    final Setting<Boolean> scope;
    final Setting<Integer> stepMillis;

    SampleSettings(PluginSettings settings) {
        activity = demo(settings, "demo_activity", "Run the Buddy activity at start", "A short demonstration activity on Buddy.");
        ui = demo(settings, "demo_ui", "Show the sample action", "On the toolbar, in the menus and in the status bar.");
        terminal = demo(settings, "demo_terminal", "Terminal demo", "\"Insert Sample Greeting\" and the last command's exit status.");
        session = demo(settings, "demo_session", "Echo session", "\"Open Sample Echo Session\", a pane whose session the plugin provides.");
        scope = demo(settings, "demo_scope", "Greetings in the command palette", "A \"Greetings\" scope in the command palette.");
        stepMillis = settings.declare(SettingSpec.of("demo_step_millis", SettingKind.integer(0, 5000), 300)
            .label("Step delay").description("Milliseconds between the demo's steps.").section("Timing").applies(Applies.RESTART));
    }

    private static Setting<Boolean> demo(PluginSettings settings, String key, String label, String description) {
        return settings.declare(SettingSpec.of(key, SettingKind.bool(), false).label(label).description(description)
            .section("Demos").applies(Applies.RESTART));
    }
}
```

Create `SampleSettingsPanel.java`:

```java
package dev.jasper.sample;

import dev.jasper.sdk.settings.SettingsPanel;
import dev.jasper.sdk.settings.SettingsPanelHost;
import java.awt.BorderLayout;
import java.awt.Font;
import java.util.List;
import java.util.Locale;
import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JSlider;

/** The sample's own settings page: standard rows for the demos, and a slider of its own for the step delay. */
final class SampleSettingsPanel implements SettingsPanel {
    private final SampleSettings settings;
    private JLabel delayLabel;

    SampleSettingsPanel(SampleSettings settings) { this.settings = settings; }

    @Override public JComponent create(SettingsPanelHost host) {
        var page = new JPanel();
        page.setLayout(new BoxLayout(page, BoxLayout.Y_AXIS));
        page.setOpaque(false);
        for (var demo : List.of(settings.activity, settings.ui, settings.terminal, settings.session, settings.scope)) {
            JComponent row = host.row(demo);
            row.setAlignmentX(0);
            page.add(row);
        }
        delayLabel = new JLabel("Step delay");
        delayLabel.putClientProperty("html.disable", true);
        var delay = new JSlider(0, 5000, host.pending(settings.stepMillis));
        delay.setMajorTickSpacing(1000);
        delay.setPaintTicks(true);
        delay.getAccessibleContext().setAccessibleName("Step delay in milliseconds");
        delay.addChangeListener(event -> {
            host.stage(settings.stepMillis, delay.getValue());
            host.changed();
        });
        var row = new JPanel(new BorderLayout(8, 0));
        row.setOpaque(false);
        row.setBorder(BorderFactory.createEmptyBorder(8, 20, 8, 20));
        row.setAlignmentX(0);
        row.add(delayLabel, BorderLayout.WEST);
        row.add(delay, BorderLayout.CENTER);
        page.add(row);
        return page;
    }

    @Override public List<String> keywords() { return List.of("sample", "demo", "step delay", "milliseconds"); }

    @Override public void highlight(String query) {
        if (delayLabel == null) return;
        String words = query == null ? "" : query.strip().toLowerCase(Locale.ROOT);
        boolean match = !words.isEmpty() && "step delay milliseconds".contains(words);
        delayLabel.setFont(delayLabel.getFont().deriveFont(match ? Font.BOLD : Font.PLAIN));
    }
}
```

In `SamplePlugin.java`, replace the body between `// example:plugin:start` and `// example:plugin:end` with:

```java
    @Override public void start(PluginContext context) {
        context.log().log(System.Logger.Level.INFO, "Sample plugin " + context.plugin().version() + " started");
        context.events().subscribe(AppEvents.THEME_CHANGED,
            event -> context.log().log(System.Logger.Level.INFO, "The look is now " + event.variant()));
        var settings = new SampleSettings(context.settings());
        context.settings().page(new SampleSettingsPanel(settings));
        long stepMillis = settings.stepMillis.get();
        if (settings.ui.get()) installUi(context, stepMillis);
        if (settings.terminal.get()) installTerminalDemo(context);
        if (settings.session.get()) installSessionDemo(context, stepMillis);
        if (settings.scope.get()) installPaletteDemo(context);
        if (settings.activity.get()) context.background().execute(() -> demo(context, stepMillis));
    }
```

In `docs/plugin-authoring.md`, replace the Java block that follows `<!-- example:plugin -->` with the same method,
exactly as above (no indentation beyond the method's own), and replace the sentence part
``their meaning; `context.config()` reads that file live and `config().file()` names it.`` with
``their meaning; declare each key with `context.settings()` (see "Declared settings" below), and `settings().file()` names the file.``

- [ ] **Step 4: Run the tests to verify they pass**

Run: `./gradlew :jasper-plugin-sample:test :jasper-app:test --tests '*AppDocumentationTest*' verifyPluginArchitecture`
Expected: PASS (the documentation test checks the guide's block against the new source).

- [ ] **Step 5: Commit**

```bash
git branch --show-current   # must print claude/plugin-settings-c2
git add plugins/sample docs/plugin-authoring.md
git commit -m "Give the Sample plugin declared settings and a Settings page of its own

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 6: Preview, documentation and the full check

**Files:**
- Create: `jasper-app/src/test/java/dev/jasper/app/plugins/PluginPreviewSupport.java`
- Modify: `jasper-app/src/test/java/dev/jasper/app/settingsui/SettingsDialogPreview.java`
- Modify: `docs/configuration.md`, `docs/plugin-authoring.md`, `docs/sdk-architecture.md`, `docs/app-architecture.md`,
  `docs/STATUS.md`, this plan's status banner

- [ ] **Step 1: Render plugin pages in the preview**

Create `PluginPreviewSupport.java`:

```java
package dev.jasper.app.plugins;

import dev.jasper.app.contributions.Contributions;
import dev.jasper.app.settingsui.PageContext;
import dev.jasper.app.settingsui.SettingsPage;
import dev.jasper.app.terminals.TerminalRegistry;
import dev.jasper.sdk.PluginInfo;
import dev.jasper.sdk.plugin.Plugin;
import dev.jasper.sdk.settings.EditorHint;
import dev.jasper.sdk.settings.SettingKind;
import dev.jasper.sdk.settings.SettingSpec;
import java.awt.BorderLayout;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JSlider;

/** For previews: two in-memory plugins, Editor with a generated page and Sample with a page of its own. UI thread. */
public final class PluginPreviewSupport implements AutoCloseable {
    private final PluginHost host;

    public PluginPreviewSupport(Path data) {
        host = AppContractTest.host(data, Map.of(), Duration.ofMillis(200), new Contributions(), new AtomicBoolean(true),
            AppContractTest.headlessWindows(), new TerminalRegistry(), new CopyOnWriteArrayList<>(), new CopyOnWriteArrayList<>());
        start("dev.example.editor", "Editor", context -> {
            var settings = context.settings();
            settings.declare(SettingSpec.of("font_family", SettingKind.text(), "JetBrains Mono").label("Font")
                .editor(EditorHint.font(true)).section("Editor"));
            settings.declare(SettingSpec.of("font_size", SettingKind.integer(8, 48), 13).label("Font size")
                .description("Points.").section("Editor"));
            settings.declare(SettingSpec.of("tab_size", SettingKind.integer(1, 16), 4).label("Tab size").section("Editor"));
            settings.declare(SettingSpec.of("soft_wrap", SettingKind.bool(), false).label("Wrap long lines")
                .description("Applies to new tabs.").section("Editor"));
            settings.declare(SettingSpec.of("vim", SettingKind.bool(), false).label("Vim mode").section("Editor"));
            settings.declare(SettingSpec.of("max_file_mib", SettingKind.integer(1, 256), 10).label("Largest file")
                .description("Larger files are refused.").section("Files"));
        });
        start("dev.example.sample", "Sample", context -> {
            var settings = context.settings();
            var ui = settings.declare(SettingSpec.of("demo_ui", SettingKind.bool(), false).label("Show the sample action"));
            var delay = settings.declare(SettingSpec.of("demo_step_millis", SettingKind.integer(0, 5000), 300).label("Step delay"));
            settings.page(rows -> {
                var page = new JPanel();
                page.setLayout(new BoxLayout(page, BoxLayout.Y_AXIS));
                page.setOpaque(false);
                JComponent row = rows.row(ui);
                row.setAlignmentX(0);
                page.add(row);
                var slider = new JSlider(0, 5000, rows.pending(delay));
                var own = new JPanel(new BorderLayout(8, 0));
                own.setOpaque(false);
                own.setBorder(BorderFactory.createEmptyBorder(8, 20, 8, 20));
                own.setAlignmentX(0);
                own.add(new JLabel("Step delay"), BorderLayout.WEST);
                own.add(slider, BorderLayout.CENTER);
                page.add(own);
                return page;
            });
        });
    }

    private void start(String id, String name, Plugin plugin) {
        host.start(new HostedPlugin(new PluginInfo(id, name, "1.0.0", Set.of()), Set.of(), Set.of(), Set.of(),
            PluginPreviewSupport.class.getClassLoader(), () -> plugin));
    }

    /** Settings pages for the two plugins. */
    public Function<PageContext, List<SettingsPage>> pages() { return host::settingsPages; }

    /** Stops the plugins; call off the UI thread. */
    @Override public void close() {
        var pending = new AtomicReference<List<CompletableFuture<?>>>(List.of());
        AppContractTest.onEdt(() -> pending.set(host.stop()));
        CompletableFuture.allOf(pending.get().toArray(CompletableFuture[]::new)).join();
    }
}
```

In `SettingsDialogPreview.java`:
- Add `var previewPlugins = new dev.jasper.app.plugins.PluginPreviewSupport[1];` beside `var manager = new PluginManager[1];`
  and, inside the first `SwingUtilities.invokeAndWait(() -> { ... })` block (after `manager[0] = ...`), add
  `previewPlugins[0] = new dev.jasper.app.plugins.PluginPreviewSupport(home.resolve("preview-plugins"));`.
- In the `CorePages.pages(...)` call, replace `context -> keymap[0] = new KeymapPage(keymapHooks, context), context -> manager[0].page(context)),`
  with `context -> keymap[0] = new KeymapPage(keymapHooks, context), context -> manager[0].page(context), previewPlugins[0].pages()),`.
- In the last `SwingUtilities.invokeAndWait(() -> { render(... "plugins-" ...); dialog.cancel(); });`, replace
  `dialog.cancel();` with:

```java
                    dialog.select("plugin.dev.example.editor");
                    render(holder[0], output.resolve("plugin-editor-" + suffix));
                    dialog.select("plugin.dev.example.sample");
                    render(holder[0], output.resolve("plugin-sample-" + suffix));
                    dialog.cancel();
```

- In the `finally` block, before stopping the runtime, add `if (previewPlugins[0] != null) previewPlugins[0].close();`.

Run: `./gradlew :jasper-app:settingsPreview`
Expected: 44 PNGs in `jasper-app/build/settings-preview`. Read `plugin-editor-*` and `plugin-sample-*` for every
theme and size with the Read tool: the tree shows Tools with Editor and Sample under it; Editor's page has "Editor" and
"Files" section headings, the font picker, spinners, checkboxes and notes; Sample's page has its row and the slider;
nothing overlaps or scrolls sideways. Fix any defect in the page code (not the preview), rerun, and record each fix as
a deviation.

- [ ] **Step 2: Documentation**

`docs/configuration.md`:
- In "## Settings dialog", after the paragraph that ends `Help › Keyboard Shortcuts… and File › Manage Plugins… open these pages.`, add:

```text
Tools holds a page for each running plugin that has settings, built from what the plugin declares or by the plugin
itself; Apply writes the plugin's own file, keeping its comments. A plugin that is installed but not running (it
needs review, failed to start, or safe mode is on) shows why, with Open in Editor for its file.
```

- In "### Plugins", replace `Jasper never rewrites the file once it exists.` with
  `Settings writes the file when you change a plugin's settings there, keeping its comments.` and replace
  `and choose Open Settings to edit the file.` with
  `and choose Open Settings to go to its page in Settings (or to its file, when the plugin is not running).`

`docs/plugin-authoring.md`: at the end of the "## Declared settings" section, add:

```text
Settings shows a page for your plugin under Tools, generated from your declarations: rows in declaration order, a
heading wherever `section` changes, the description under each control, and an editor chosen by kind or by
`editor(EditorHint...)`. To build the page yourself, call `context.settings().page(panel)` during `start` with a
`SettingsPanel`: `create(host)` builds the component, `host.row(setting)` gives you the standard row for a declared
setting, and `host.stage(setting, value)` sets a value from a control of your own; Settings saves them on Apply. Use
`modified`, `apply` and `reset` only for state of your own, and `keywords` and `highlight` for search. The Sample
plugin's `SampleSettingsPanel` is a complete example; the testkit's `settingsPanel`, `pendingSettings` and
`settingsPanelChanges` let you test it without a window.
```

`docs/sdk-architecture.md`: at the end of the file, add:

```text
### Plugin pages in Settings (plan C2)

`PluginSettings.page(SettingsPanel)` lets a plugin build its own page; `SettingsPanelHost` gives it standard rows
and the page's unsaved values. The application's `PluginRuntime.settingsPages` returns a generated page
(`PluginSettingsPage`, a `SchemaPage` over the plugin's own draft) or the plugin's own (`PluginPanelPage`, every call
contained) for each running plugin with settings, and a `PluginStubPage` for each plugin that is enabled but not
running; `CorePages` places them under Tools. Apply writes the plugin's file through `DeclaredSettings.save`.
```

`docs/app-architecture.md`: append to the `plugins` row's description
` PluginRuntime.settingsPages gives Settings a page per plugin under Tools.`

Run: `./gradlew :jasper-app:test --tests '*AppDocumentationTest*'`
Expected: PASS.

- [ ] **Step 3: The full check**

Run: `./gradlew check`
Expected: BUILD SUCCESSFUL. Count the tests:

```bash
python3 - <<'PY'
import pathlib, xml.etree.ElementTree as ET
t = f = e = s = 0
for pat in ["*/build/test-results/test/*.xml", "plugins/*/build/test-results/test/*.xml"]:
    for p in pathlib.Path(".").glob(pat):
        r = ET.parse(p).getroot(); t += int(r.get("tests")); f += int(r.get("failures")); e += int(r.get("errors")); s += int(r.get("skipped"))
print(f"tests={t} failures={f} errors={e} skipped={s}")
PY
```

Also run the source hygiene check from AGENTS.md.

- [ ] **Step 4: Record the outcome**

In `docs/STATUS.md`, under "## Current state", add at the top (with the real count in place of COUNT):

```text
- **Plugin settings C2** (`claude/plugin-settings-c2`, 2026-09-28; plan
  `docs/superpowers/plans/2026-09-28-jasper-plugin-settings-c2.md`): Settings has a page under Tools for each running
  plugin with settings, generated from its declarations (`PluginSettingsPage`) or built by the plugin through the new
  SDK `SettingsPanel` and `SettingsPanelHost` (`PluginPanelPage`); plugins that are enabled but not running get a stub
  with Open in Editor. Apply writes the plugin's own file. The Plugins page's Open Settings goes to a running plugin's
  page. The Sample plugin declares its settings and has its own page. Previews: `settingsPreview` renders 44 images.
  `./gradlew check`: COUNT tests, 0 failures. GUI checks pending with the user: change a plugin setting and Apply; the
  Sample page's slider; Open Settings from the Plugins page; a plugin in safe mode (stub page). Deviations: see the
  plan's status banner. Not pushed; not merged.
```

Update this plan's **Status** line to `Implemented on claude/plugin-settings-c2; ./gradlew check green, COUNT tests.`
(real count) and list deviations under "Deviations so far".

- [ ] **Step 5: Commit**

```bash
git branch --show-current   # must print claude/plugin-settings-c2
git add -A docs jasper-app/src/test
git commit -m "Preview and document plugin pages in Settings (plan C2)

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```
