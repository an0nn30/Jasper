# IntelliJ Theme Marketplace Implementation Plan

> **Status:** written 2026-09-30 on `claude/theme-marketplace`; executed subagent-driven 2026-09-30; not merged. Choices this plan makes where the spec left
> room (record further deviations here and in `docs/STATUS.md` as you go):
> - Deviations made during execution: the Marketplace client refuses a first request to any host other than the base and
>   uses shutdownNow on close; the installer replaces files atomically (disk installs via `.downloads`), writes provenance
>   before deleting old versions, accepts only .jar/.zip and only uninstalls .jar/.zip; ThemeStore calls the rescan hook on
>   the EDT, retries icon fetches and clears leftover downloads once per run; the description pane uses an allowlist HTML
>   sanitiser (basic formatting tags, only a[href]); the Marketplace tab loads the installed list when shown; details
>   failures show inline Retry and install/update failures offer Retry in the banner; package-info `{@link}` names
>   ThemeStore; the ThemeCombo renderer calls `ThemeCombo.action(value)`.
> - `MarketplaceClient` methods block; `ThemeStore` runs them on its worker and returns `CompletableFuture`s completed
>   on the EDT (the spec had the client return futures).
> - **Use** stages `ui.theme.name` in the dialog's draft; the theme switches on Apply or OK like every other setting.
> - The `themes.marketplace.enabled` checkbox is an ordinary schema page (`themes.intellij` in `CorePages.specs`)
>   that the application's `IntelliJThemesPage` wraps as its header, so "every core setting is on exactly one page"
>   still holds.
> - Provenance entries also keep the plugin's `name`, which titles the Installed card.
> - Installed and Marketplace lists are `JList`s with renderers (cards hold no controls; buttons live in the details).
> - The details pane's version comes from `latest(id)`; a blank search orders by downloads (`orderBy=downloads`).

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Settings › Themes › IntelliJ Themes installs IntelliJ theme plugins from disk and from the JetBrains Marketplace, lists, updates and uninstalls them, and the Appearance theme dropdown ends in Get More Themes….

**Architecture:** A new app package `dev.jasper.app.themestore` holds a blocking JDK `HttpClient` Marketplace client, an installer that owns writes to the existing `themes` folder (validating with a new public `ThemeCatalog.inspect`), and an application-owned `ThemeStore` that runs both on a virtual-thread worker and builds the Settings page. `settingsui` gains a Themes group, the `themes.intellij` schema header, `#fragment` navigation (`SettingsPage.reveal`) and a theme combo with a navigation sentinel; it never depends on `themestore`.

**Tech Stack:** Java 25 (JetBrains Runtime), Gradle wrapper, Swing + FlatLaf 3.7 (`FlatSVGIcon` from flatlaf-extras), `java.net.http.HttpClient`, `com.formdev.flatlaf.json.Json`, tomlj 1.1.1, JUnit 5 + AssertJ, `com.sun.net.httpserver` for tests.

**Spec:** `docs/superpowers/specs/2026-09-30-intellij-theme-marketplace-design.md`

## Global Constraints

- Work in `.worktrees/theme-marketplace` on branch `claude/theme-marketplace`; run `git branch --show-current` before every commit. Never commit on `main`.
- Every commit message ends with `Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>`.
- Use `./gradlew`, never a system `gradle`. App tests: `./gradlew :jasper-app:test --tests '<pattern>'`. Full gate: `./gradlew check`.
- Do not launch the GUI (`./gradlew :jasper-app:run`) or the benchmark. No test may contact the real Marketplace.
- Never put raw control, private-use or unpaired surrogate characters in source; write Java escapes (`"“"`, `"…"`, `"·"`, `"★"`).
- Setting: `themes.marketplace.enabled`, `SettingKind.bool()`, default `true`, label `Browse the JetBrains Marketplace`, live.
- Page ids: group `themes` ("Themes"), page `themes.intellij` ("IntelliJ Themes", help anchor `intellij-themes`); fragments `marketplace` and `installed`.
- Dropdown entries: `Get More Themes…` (Marketplace on) → `themes.intellij#marketplace`; `Manage Themes…` (off) → `themes.intellij#installed`. Never stored as a value.
- Marketplace: base `https://plugins.jetbrains.com`; search `GET /api/searchPlugins?tags=Theme&max=20&offset=N[&orderBy=downloads][&search=Q]`; details `GET /api/plugins/{id}`; latest `GET /api/plugins/{id}/updates?size=1`; download `GET /files/{file}`. Redirects only to `https` hosts equal to or ending in `.jetbrains.com`, at most 5. Timeouts: connect 10 s, request 30 s. Caps: JSON 4 MiB, icon 512 KiB, download 50 MiB. PAID listings dropped, FREEMIUM badged. JSON numbers may arrive as strings.
- Files: Marketplace installs land at `themes/<xmlId>-<version>.<jar|zip>`; downloads in `themes/.downloads/<updateId>.part`; provenance in `themes/.sources.toml` (tables keyed by file name: `marketplace_id`, `xml_id`, `name`, `version`). Dot-files are ignored by the catalog scan.
- `themestore` may depend on `appearance`, `config`, `lifecycle`, `settings`, `settingsui`; nothing depends on `themestore` except `application`. `verifyApplicationArchitecture` (acyclic packages) must stay green.
- EDT only for Swing; all file and network work on `ThemeStore`'s worker.

## File Structure

Create (main, `jasper-app/src/main/java/dev/jasper/app/themestore/`):
- `package-info.java` — ownership contract.
- `MarketplaceClient.java` — blocking HTTP + JSON → `Listing`, `Page`, `Details`, `Release`; redirect and size policy.
- `Provenance.java` — reads/writes `.sources.toml`.
- `Versions.java` — dotted version comparison.
- `ThemeInstaller.java` — downloads' landing, validation, disk installs, uninstall; synchronized.
- `InstalledTheme.java` — one archive as the Installed tab shows it, built from a catalog snapshot + provenance.
- `ThemeStore.java` — application-owned controller: worker, EDT hand-off, hooks, page factory.
- `Banner.java`, `InstalledTab.java`, `MarketplaceTab.java`, `IntelliJThemesPage.java` — the page.

Create (tests, `jasper-app/src/test/java/dev/jasper/app/themestore/`): `FakeMarketplace.java`, `MarketplaceClientTest.java`, `VersionsTest.java`, `ThemeInstallerTest.java`, `ThemeStoreTest.java`, `IntelliJThemesPageTest.java`.

Modify:
- `config/CoreSettings.java` — new setting, schema, table note.
- `settingsui/SettingsPage.java` — `reveal(String)`.
- `settingsui/SettingsDialog.java` — `#fragment` in `select`.
- `settingsui/CorePages.java` — ids, the `themes.intellij` spec, Themes group, `intellijThemes` wrapper parameter.
- `settingsui/Editors.java` — `ThemeCombo`.
- `appearance/ThemeCatalog.java` — `inspect`, `Inspection`, `directory()`.
- `application/JasperApplication.java` — create, wire and close the `ThemeStore`.
- Tests: `settingsui/CorePagesTest.java`, `settingsui/EditorsTest.java`, `settingsui/SettingsDialogTest.java`, `appearance/ThemeCatalogTest.java`.
- Docs: `config.example.toml`, `docs/configuration.md`, `docs/themes.md`, `docs/app-architecture.md`, `docs/STATUS.md`.

---

### Task 1: The setting, the Themes group and the IntelliJ Themes header

**Files:**
- Modify: `jasper-app/src/main/java/dev/jasper/app/config/CoreSettings.java` (after `UI_THEME_NAME`, the `SCHEMA` list and its table notes)
- Modify: `jasper-app/src/main/java/dev/jasper/app/settingsui/CorePages.java`
- Modify: `config.example.toml`, `docs/configuration.md`
- Test: `jasper-app/src/test/java/dev/jasper/app/settingsui/CorePagesTest.java`

**Interfaces:**
- Produces: `CoreSettings.THEMES_MARKETPLACE_ENABLED` (`Setting<Boolean>`); `CorePages.THEMES = "themes"`, `CorePages.INTELLIJ_THEMES = "themes.intellij"`; overload
  `CorePages.pages(Supplier<List<String>> themeNames, SystemActions system, ShellChoices shells, Function<PageContext, SettingsPage> keymap, Function<PageContext, SettingsPage> plugins, Function<PageContext, List<SettingsPage>> tools, BiFunction<PageContext, SettingsPage, SettingsPage> intellijThemes)`
  — `intellijThemes` receives the built `themes.intellij` schema page and returns the page to place in the tree. Older overloads pass `(context, header) -> header`.

- [ ] **Step 1: Write the failing tests**

In `CorePagesTest.java`, replace `theTreeIsTheApprovedOne` and add a test (no new imports; `ShellChoices` is in the same package):

```java
    @Test void theTreeIsTheApprovedOne() {
        var pages = CorePages.pages(() -> List.of("Jasper Dark"), system, CorePagesTest::keymap, CorePagesTest::plugins).apply(context);
        assertThat(pages).extracting(SettingsPage::id).containsExactly("appearance-behavior", "appearance", "window", "notifications",
            "command-palette", "desk-buddy", "system", "keymap", "themes", "themes.intellij", "terminal", "terminal.font",
            "terminal.cursor", "terminal.shell", "plugins", "tools");
        assertThat(pages).extracting(page -> page.parent().orElse("")).containsExactly("", "appearance-behavior", "appearance-behavior",
            "appearance-behavior", "appearance-behavior", "appearance-behavior", "appearance-behavior", "", "", "themes", "", "terminal",
            "terminal", "terminal", "", "");
        assertThat(pages).extracting(SettingsPage::title).contains("Appearance & Behavior", "System Settings", "Font & Colors", "Tools",
            "Themes", "IntelliJ Themes");
        assertThat(pages.getLast().hiddenWhenChildless()).isTrue();
        assertThat(CorePages.DEFAULT_PAGE).isEqualTo("appearance");
    }

    @Test void theIntellijThemesPageWrapsItsSchemaHeaderUnderThemes() {
        var wrapped = new GroupPage("themes.intellij", "IntelliJ Themes", Optional.of("themes"), false, List::of, context);
        var headers = new ArrayList<SettingsPage>();
        var pages = CorePages.pages(() -> List.of("Jasper Dark"), system, ShellChoices.NONE, CorePagesTest::keymap, CorePagesTest::plugins,
            page -> List.of(), (page, header) -> { headers.add(header); return wrapped; }).apply(context);
        assertThat(pages).contains(wrapped);
        assertThat(headers).singleElement().satisfies(header -> {
            assertThat(header.id()).isEqualTo(CorePages.INTELLIJ_THEMES);
            assertThat(header.parent()).contains(CorePages.THEMES);
            assertThat(header.helpAnchor()).isEqualTo("intellij-themes");
            assertThat(header.searchTerms()).anyMatch(term -> term.contains("Marketplace"));
        });
    }
```

`everyCoreSettingIsOnExactlyOnePage` stays as it is; it fails until the new setting is on a page.

- [ ] **Step 2: Run the tests to verify they fail**

Run: `./gradlew :jasper-app:test --tests 'dev.jasper.app.settingsui.CorePagesTest'`
Expected: compilation FAILS (`INTELLIJ_THEMES`, `THEMES` and the 7-argument `pages` do not exist).

- [ ] **Step 3: Add the setting**

In `CoreSettings.java`, directly after the `UI_THEME_NAME` declaration:

```java
    public static final Setting<Boolean> THEMES_MARKETPLACE_ENABLED = Setting.of("themes.marketplace.enabled", SettingKind.bool(), true)
        .label("Browse the JetBrains Marketplace").keywords("themes", "marketplace", "download", "update", "offline", "intellij")
        .description("Let Settings > Themes > IntelliJ Themes search plugins.jetbrains.com and check installed themes for\n"
            + "updates, only while that page is open. Off keeps Jasper from contacting the Marketplace.").build();
```

In `SCHEMA`, change the end of the settings list from `UI_FONT_FAMILY, UI_FONT_SIZE, UI_THEME_NAME),` to:

```java
            TERMINAL_CURSOR_SHAPE, TERMINAL_CURSOR_BLINK, TERMINAL_ENV, UI_FONT_FAMILY, UI_FONT_SIZE, UI_THEME_NAME, THEMES_MARKETPLACE_ENABLED),
```

(keep the preceding lines unchanged; only `THEMES_MARKETPLACE_ENABLED` is appended). In the table-notes `Map.ofEntries(...)`, after the `List.of("ui", "theme")` entry, add (mind the comma on the previous entry):

```java
            Map.entry(List.of("ui", "theme"), "The interface theme; terminal colours are terminal.colors."),
            Map.entry(List.of("themes", "marketplace"), "IntelliJ themes from the JetBrains Marketplace (Settings > Themes).")));
```

- [ ] **Step 4: Add the ids, the spec and the tree positions in `CorePages.java`**

Add imports `java.util.function.BiFunction`. After `DEFAULT_PAGE`:

```java
    /** The Themes group's id. */
    public static final String THEMES = "themes";
    /** The IntelliJ Themes page's id; {@code #marketplace} and {@code #installed} select its tabs. */
    public static final String INTELLIJ_THEMES = "themes.intellij";
```

In `specs(themeNames, system, shells)`, insert this spec between the `system` spec and the `terminal` spec, so it is element 6:

```java
            SchemaPage.page(INTELLIJ_THEMES, "IntelliJ Themes").parent(THEMES).anchor("intellij-themes")
                .row(THEMES_MARKETPLACE_ENABLED),
```

Replace the 6-argument `pages(themeNames, system, shells, keymap, plugins, tools)` body so it delegates, and add the 7-argument overload that holds the tree:

```java
    /** As above with {@code shells}, followed by {@code tools}: pages under Tools, such as plugins' settings pages. */
    public static Function<PageContext, List<SettingsPage>> pages(Supplier<List<String>> themeNames, SystemActions system, ShellChoices shells,
                                                                   Function<PageContext, SettingsPage> keymap,
                                                                   Function<PageContext, SettingsPage> plugins,
                                                                   Function<PageContext, List<SettingsPage>> tools) {
        return pages(themeNames, system, shells, keymap, plugins, tools, (context, header) -> header);
    }

    /**
     * The whole tree: as above, with {@code intellijThemes} turning the IntelliJ Themes schema page (its settings) into
     * the page the tree shows.
     */
    public static Function<PageContext, List<SettingsPage>> pages(Supplier<List<String>> themeNames, SystemActions system, ShellChoices shells,
                                                                   Function<PageContext, SettingsPage> keymap,
                                                                   Function<PageContext, SettingsPage> plugins,
                                                                   Function<PageContext, List<SettingsPage>> tools,
                                                                   BiFunction<PageContext, SettingsPage, SettingsPage> intellijThemes) {
        return context -> {
            var specs = specs(themeNames, system, shells);
            var all = new ArrayList<SettingsPage>();
            Function<String, Supplier<List<SettingsPage>>> childrenOf = id ->
                () -> all.stream().filter(page -> page.parent().equals(Optional.of(id))).toList();
            all.add(new GroupPage("appearance-behavior", "Appearance & Behavior", Optional.empty(), false,
                childrenOf.apply("appearance-behavior"), context));
            for (var spec : specs.subList(0, 6)) all.add(spec.build(context));
            all.add(keymap.apply(context));
            all.add(new GroupPage(THEMES, "Themes", Optional.empty(), false, childrenOf.apply(THEMES), context));
            all.add(intellijThemes.apply(context, specs.get(6).build(context)));
            for (var spec : specs.subList(7, specs.size())) all.add(spec.build(context));
            all.add(plugins.apply(context));
            all.add(new GroupPage("tools", "Tools", Optional.empty(), true, childrenOf.apply("tools"), context));
            all.addAll(tools.apply(context));
            return List.copyOf(all);
        };
    }
```

- [ ] **Step 5: Document the key**

In `config.example.toml`, after the `[ui.theme]` block (after `name = "Jasper Dark"` and before the blank line and `[keybindings]`):

```toml

[themes.marketplace]
# Search the JetBrains Marketplace from Settings > Themes; false keeps Jasper offline.
enabled = true
```

In `docs/configuration.md`, apply the same insertion to the example block after `name = "Jasper Dark"`, and add a table row right after the `ui.theme.name` row:

```markdown
| `themes.marketplace.enabled` | `true` | Boolean; `false` hides Settings › Themes › IntelliJ Themes' Marketplace tab and stops its update checks | Live |
```

- [ ] **Step 6: Run the tests**

Run: `./gradlew :jasper-app:test --tests 'dev.jasper.app.settingsui.*' --tests 'dev.jasper.app.config.*'`
Expected: PASS. If `SettingsDialogTest` or `ConfigTemplateTest` fails, read the message: tree ids and template tables are the only expected changes.

- [ ] **Step 7: Commit**

```bash
git branch --show-current   # claude/theme-marketplace
git add -A jasper-app/src config.example.toml docs/configuration.md
git commit -m "Add themes.marketplace.enabled and the Themes › IntelliJ Themes page slot

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 2: Fragment navigation and Get More Themes… in the dropdown

**Files:**
- Modify: `jasper-app/src/main/java/dev/jasper/app/settingsui/SettingsPage.java`
- Modify: `jasper-app/src/main/java/dev/jasper/app/settingsui/SettingsDialog.java` (`select`)
- Modify: `jasper-app/src/main/java/dev/jasper/app/settingsui/Editors.java` (`theme`, new `ThemeCombo`)
- Test: `jasper-app/src/test/java/dev/jasper/app/settingsui/SettingsDialogTest.java`, `EditorsTest.java`

**Interfaces:**
- Consumes: `CorePages.INTELLIJ_THEMES`, `CoreSettings.THEMES_MARKETPLACE_ENABLED`, the 7-argument `CorePages.pages` (Task 1).
- Produces: `default void SettingsPage.reveal(String fragment)`; `SettingsDialog.select("id#fragment")` selects `id` then calls `reveal(fragment)`, also when `id` is already shown; `Editors.ThemeCombo` (package-private) with `reload()`, constants `MORE = "Get More Themes…"`, `MANAGE = "Manage Themes…"`.

- [ ] **Step 1: Write the failing tests**

In `SettingsDialogTest.java`, extract the hooks from `dialog()` into a method and use it there:

```java
    private SettingsDialog.Hooks hooks() {
        return new SettingsDialog.Hooks(edit -> {
                saved.add(edit);
                return saveResult != null ? saveResult : CompletableFuture.completedFuture(state("[window]\ntab_height = 50\n"));
            }, () -> state, listener -> { listeners.add(listener); return new Subscription(() -> listeners.remove(listener)); },
            () -> opened.add("config"), () -> opened.add("migrate"), browsed::add,
            (parent, count) -> { asked[0] = count; return discard[0]; });
    }

    private SettingsDialog dialog() {
        return new SettingsDialog(windows, context -> {
            contexts.add(context);
            return CorePages.pages(() -> List.of("Jasper Dark", "IntelliJ Light"), new CorePages.SystemActions(() -> FILE,
                    () -> { }, () -> { }, () -> { }, () -> { }, () -> false), page -> fake,
                page -> new GroupPage("plugins", "Plugins", Optional.empty(), false, List::of, page)).apply(context);
        }, hooks());
    }
```

Add the test and its helper page:

```java
    @Test void aFragmentSelectsThePageAndRevealsItsPartEvenWhenItIsAlreadyShown() throws Exception {
        var revealed = new ArrayList<String>();
        var dialog = new SettingsDialog(windows, context -> CorePages.pages(() -> List.of("Jasper Dark"),
                new CorePages.SystemActions(() -> FILE, () -> { }, () -> { }, () -> { }, () -> { }, () -> false), ShellChoices.NONE,
                page -> fake, page -> new GroupPage("plugins", "Plugins", Optional.empty(), false, List::of, page), page -> List.of(),
                (page, header) -> new Revealing(header, revealed)).apply(context), hooks());
        edt(() -> {
            dialog.open(null, "appearance");
            dialog.select("themes.intellij#marketplace");
            assertThat(dialog.currentPage()).isEqualTo("themes.intellij");
            dialog.select("themes.intellij#installed");
            assertThat(revealed).containsExactly("marketplace", "installed");
            dialog.back();
            assertThat(dialog.currentPage()).isEqualTo("appearance");
        });
    }

    /** Records what it is asked to reveal; everything else is the schema page it wraps. */
    private record Revealing(SettingsPage header, List<String> revealed) implements SettingsPage {
        @Override public String id() { return header.id(); }
        @Override public String title() { return header.title(); }
        @Override public Optional<String> parent() { return header.parent(); }
        @Override public List<String> searchTerms() { return header.searchTerms(); }
        @Override public javax.swing.JComponent component() { return header.component(); }
        @Override public boolean modified() { return header.modified(); }
        @Override public void reset() { header.reset(); }
        @Override public CompletableFuture<Void> apply() { return header.apply(); }
        @Override public void highlight(String query) { header.highlight(query); }
        @Override public void dispose() { header.dispose(); }
        @Override public void reveal(String fragment) { revealed.add(fragment); }
    }
```

In `EditorsTest.java` (add `import java.util.ArrayList;` if missing):

```java
    @Test void theThemeComboEndsInGetMoreThemesWhichNavigatesWithoutEditing() {
        var navigated = new ArrayList<String>();
        var draft = new SettingsDraft(ConfigLoader.parse(Path.of("config.toml"), "", true).values());
        var names = new ArrayList<>(List.of("Jasper Dark", "IntelliJ Light"));
        var themes = new PageContext(draft, navigated::add, Runnable::run, (title, initial) -> Optional.empty(), () -> { });
        var editor = build(Editors.theme(() -> List.copyOf(names)), CoreSettings.UI_THEME_NAME, themes);
        var edits = new int[1];
        editor.show("Jasper Dark");
        editor.onEdit(() -> edits[0]++);
        @SuppressWarnings("unchecked") var box = (JComboBox<String>) editor.component();
        assertThat(box.getItemAt(box.getItemCount() - 1)).isEqualTo("Get More Themes…");
        box.setSelectedIndex(box.getItemCount() - 1);
        assertThat(navigated).containsExactly("themes.intellij#marketplace");
        assertThat(editor.value()).isEqualTo("Jasper Dark");
        assertThat(edits[0]).isZero();

        draft.stage(CoreSettings.THEMES_MARKETPLACE_ENABLED, Optional.of(false));
        names.add("Dracula");
        ((Editors.ThemeCombo) editor).reload();
        assertThat(box.getItemCount()).isEqualTo(4);
        assertThat(box.getItemAt(2)).isEqualTo("Dracula");
        assertThat(box.getItemAt(3)).isEqualTo("Manage Themes…");
        assertThat(editor.value()).isEqualTo("Jasper Dark");
        box.setSelectedIndex(3);
        assertThat(navigated).containsExactly("themes.intellij#marketplace", "themes.intellij#installed");
        assertThat(edits[0]).isZero();
        box.setSelectedIndex(2);
        assertThat(editor.value()).isEqualTo("Dracula");
        assertThat(edits[0]).isPositive();
    }
```

- [ ] **Step 2: Run the tests to verify they fail**

Run: `./gradlew :jasper-app:test --tests 'dev.jasper.app.settingsui.SettingsDialogTest' --tests 'dev.jasper.app.settingsui.EditorsTest'`
Expected: compilation FAILS (`reveal` is not a `SettingsPage` method; `Editors.ThemeCombo` does not exist).

- [ ] **Step 3: Add `reveal` to `SettingsPage`**

After `selected()`:

```java
    /** Shows the part of the page {@code fragment} names, such as a tab; called after the page is shown. Unknown fragments are ignored. */
    default void reveal(String fragment) { }
```

- [ ] **Step 4: Handle fragments in `SettingsDialog.select`**

Replace `select`:

```java
    /** Selects a page, remembering the one it leaves for Back; {@code id#fragment} then reveals that part of the page. */
    void select(String id) {
        int hash = id.indexOf('#');
        String base = hash < 0 ? id : id.substring(0, hash);
        var target = page(base).or(() -> pages.stream().findFirst()).orElseThrow();
        if (!target.id().equals(current)) {
            if (current != null) { backStack.push(current); forwardStack.clear(); }
            show(target);
        }
        if (hash >= 0 && target.id().equals(base)) target.reveal(id.substring(hash + 1));
    }
```

- [ ] **Step 5: Add `ThemeCombo` to `Editors.java`**

Add imports `java.awt.Color`, `java.awt.Component`, `javax.swing.BorderFactory`, `javax.swing.DefaultComboBoxModel`, `javax.swing.DefaultListCellRenderer`, `javax.swing.JLabel`, `javax.swing.JList`, `javax.swing.UIManager`, `javax.swing.event.PopupMenuEvent`, `javax.swing.event.PopupMenuListener`, `dev.jasper.app.config.CoreSettings` (skip any already imported). Replace `theme`:

```java
    /** An editable combo of theme names ending in Get More Themes… (Manage Themes… while the Marketplace is off). */
    public static Factory<String> theme(Supplier<List<String>> names) {
        return new Factory<>((setting, context) -> new ThemeCombo(names, context), List.of("Get More Themes", "Marketplace"));
    }
```

Add beside `Combo`:

```java
    /**
     * The theme combo: the installed theme names, re-read each time the list opens, then an entry that opens Settings ›
     * Themes › IntelliJ Themes. Choosing that entry navigates and leaves the value as it was.
     */
    static final class ThemeCombo implements ValueEditor<String> {
        static final String MORE = "Get More Themes…";
        static final String MANAGE = "Manage Themes…";
        private final Supplier<List<String>> names;
        private final PageContext context;
        private final Model model = new Model();
        private final JComboBox<String> box = new JComboBox<>(model);
        private final List<Runnable> listeners = new ArrayList<>();
        private boolean showing;

        ThemeCombo(Supplier<List<String>> names, PageContext context) {
            this.names = names;
            this.context = context;
            box.setEditable(true);
            box.setPrototypeDisplayValue("JetBrains Mono NL Extra");
            box.setRenderer(new Renderer());
            reload();
            // A look-and-feel update gives the combo a new editor field; follow each one (see Combo).
            onText(editor(), this::edited);
            box.addPropertyChangeListener("editor", event -> { if (box.getEditor() != null) onText(editor(), this::edited); });
            box.addPopupMenuListener(new PopupMenuListener() {
                @Override public void popupMenuWillBecomeVisible(PopupMenuEvent event) { reload(); }
                @Override public void popupMenuWillBecomeInvisible(PopupMenuEvent event) { }
                @Override public void popupMenuCanceled(PopupMenuEvent event) { }
            });
        }

        /** Re-reads the theme names, keeping the text shown, so themes installed since the page was built are offered. */
        void reload() {
            showing = true;
            try {
                String text = editor().getText();
                model.removeAllElements();
                for (String name : names.get()) model.addElement(name);
                model.addElement(action());
                model.select(text);
                editor().setText(text);
            } finally { showing = false; }
        }

        private String action() { return context.draft().value(CoreSettings.THEMES_MARKETPLACE_ENABLED) ? MORE : MANAGE; }
        private static boolean action(Object item) { return MORE.equals(item) || MANAGE.equals(item); }
        private JTextComponent editor() { return (JTextComponent) box.getEditor().getEditorComponent(); }
        private void edited() { if (!showing) for (Runnable listener : List.copyOf(listeners)) listener.run(); }

        private void open(boolean marketplace) {
            box.hidePopup();
            context.navigate().accept(CorePages.INTELLIJ_THEMES + (marketplace ? "#marketplace" : "#installed"));
        }

        @Override public JComponent component() { return box; }
        @Override public String value() { return editor().getText(); }
        @Override public void show(String value) {
            showing = true;
            try { model.select(value); editor().setText(value); } finally { showing = false; }
        }
        @Override public void onEdit(Runnable listener) { listeners.add(listener); }

        /** Refuses the action entry as a selection and navigates instead. */
        private final class Model extends DefaultComboBoxModel<String> {
            @Override public void setSelectedItem(Object item) {
                if (action(item)) { if (!showing) open(MORE.equals(item)); return; }
                super.setSelectedItem(item);
            }
            void select(Object item) { super.setSelectedItem(item); }
        }

        /** Draws the action entry as a link below a rule. */
        private static final class Renderer extends DefaultListCellRenderer {
            @Override public Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean selected, boolean focused) {
                var label = (JLabel) super.getListCellRendererComponent(list, value, index, selected, focused);
                label.putClientProperty("html.disable", true);
                if (action(value) && index >= 0) {
                    Color link = UIManager.getColor("Component.linkColor");
                    if (link != null && !selected) label.setForeground(link);
                    Color rule = UIManager.getColor("Separator.foreground");
                    label.setBorder(BorderFactory.createCompoundBorder(
                        BorderFactory.createMatteBorder(1, 0, 0, 0, rule != null ? rule : Color.GRAY), label.getBorder()));
                }
                return label;
            }
        }
    }
```

If the test shows `editor.value()` changing to the sentinel after `setSelectedIndex`, the Swing UI copied the item into the editor: guard by restoring the text in `open` (`String text = editor().getText();` before navigating, `editor().setText(text)` after, both under `showing = true`) and note it in the status banner.

- [ ] **Step 6: Run the tests**

Run: `./gradlew :jasper-app:test --tests 'dev.jasper.app.settingsui.*'`
Expected: PASS, including `anEditableComboStillReportsPicksAfterItsLookAndFeelIsUpdated` and `pickingAThemeFromTheListEnablesApply`.

- [ ] **Step 7: Commit**

```bash
git branch --show-current
git add -A jasper-app/src
git commit -m "Settings: #fragment navigation and Get More Themes… in the theme dropdown

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 3: Inspecting a theme archive without installing it

**Files:**
- Modify: `jasper-app/src/main/java/dev/jasper/app/appearance/ThemeCatalog.java`
- Test: `jasper-app/src/test/java/dev/jasper/app/appearance/ThemeCatalogTest.java`

**Interfaces:**
- Produces: `public record ThemeCatalog.Inspection(List<String> themes, List<String> problems)`; `public static ThemeCatalog.Inspection ThemeCatalog.inspect(Path archive)` (any thread; the theme names are resolved names as the catalog would list them); `public Optional<Path> ThemeCatalog.directory()` (empty for `builtIn()`).

- [ ] **Step 1: Write the failing test**

```java
    @Test void inspectingAnArchiveNamesItsThemesWithoutInstallingIt() {
        Path file = plugin(home.resolve("elsewhere/fixture.jar"), "Fixture Dark", true, "#123456", "101820");
        var inspection = ThemeCatalog.inspect(file);
        assertThat(inspection.themes()).containsExactly("Fixture Dark");
        assertThat(inspection.problems()).isEmpty();
        Path empty = jar(home.resolve("elsewhere/empty.jar"), entries("META-INF/plugin.xml", pluginXml("Nothing")));
        assertThat(ThemeCatalog.inspect(empty).themes()).isEmpty();
        assertThat(ThemeCatalog.inspect(home.resolve("elsewhere/missing.jar")).problems()).isNotEmpty();
        assertThat(catalog().directory()).contains(themes());
        assertThat(ThemeCatalog.builtIn().directory()).isEmpty();
        assertThat(themes()).doesNotExist();
    }
```

- [ ] **Step 2: Run it to verify it fails**

Run: `./gradlew :jasper-app:test --tests 'dev.jasper.app.appearance.ThemeCatalogTest'`
Expected: compilation FAILS (`inspect`, `directory` missing).

- [ ] **Step 3: Implement**

In `ThemeCatalog.java`, after the `Snapshot` record:

```java
    /** What an archive would install: its themes' names, and the problems reading it met. */
    public record Inspection(List<String> themes, List<String> problems) {
        public Inspection { themes = List.copyOf(themes); problems = List.copyOf(problems); }
    }

    private record Loaded(List<Theme> themes, List<Problem> problems) {}
```

After `latest()`:

```java
    /** The folder this catalog scans; empty for the built-in catalog. */
    public Optional<Path> directory() { return Optional.ofNullable(directory); }

    /** Reads {@code archive} as a scan would, without installing it; may run on any thread. */
    public static Inspection inspect(Path archive) {
        Loaded loaded;
        try { loaded = load(archive, ThemeArchive::read); }
        catch (RuntimeException | OutOfMemoryError | StackOverflowError failure) {
            return new Inspection(List.of(), List.of(archive.getFileName() + " cannot be read: " + failure));
        }
        return new Inspection(loaded.themes().stream().map(Theme::name).toList(),
            loaded.problems().stream().map(Problem::message).toList());
    }
```

In `read(Path file)`, replace everything from `ThemeArchive.Contents contents;` to the method's final `return new Read(stamp, themes, problems);` with:

```java
        Loaded loaded = load(file, reader);
        return new Read(stamp, loaded.themes(), loaded.problems());
    }

    /** The archive's themes, loaded and resolved, and every problem met; never throws for a bad archive. */
    private static Loaded load(Path file, Function<Path, ThemeArchive.Contents> reader) {
        ThemeArchive.Contents contents;
        try { contents = reader.apply(file); }
        catch (RuntimeException failure) {
            return new Loaded(List.of(), List.of(new Problem(file, file.getFileName() + " cannot be read: " + failure)));
        }
        var problems = new ArrayList<Problem>();
        contents.problems().forEach(problem -> problems.add(new Problem(file, problem)));
        var themes = new ArrayList<Theme>();
        for (ThemeArchive.Found found : contents.themes()) {
            var source = new Theme.Archive(file, found.label(), found.json());
            ThemeLoader.Resolved resolved;
            try { resolved = ThemeManager.load(source); }
            catch (ThemeLoader.ThemeException failure) { problems.add(new Problem(file, failure.getMessage())); continue; }
            catch (RuntimeException failure) { problems.add(new Problem(file, found.label() + " cannot be loaded: " + failure)); continue; }
            resolved.warnings().forEach(warning -> problems.add(new Problem(file, warning)));
            EditorScheme.Result scheme = scheme(found, resolved.dark());
            scheme.warnings().forEach(warning -> problems.add(new Problem(file, warning)));
            themes.add(new Theme("installed:" + found.label(), resolved.name(), resolved.dark(), scheme.palette(), source));
        }
        return new Loaded(themes, problems);
```

(the method's closing `}` is the one that closed `read` before.)

- [ ] **Step 4: Run the tests**

Run: `./gradlew :jasper-app:test --tests 'dev.jasper.app.appearance.*'`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git branch --show-current
git add -A jasper-app/src
git commit -m "ThemeCatalog.inspect: read a theme archive without installing it

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 4: The Marketplace client

**Files:**
- Create: `jasper-app/src/main/java/dev/jasper/app/themestore/package-info.java`
- Create: `jasper-app/src/main/java/dev/jasper/app/themestore/MarketplaceClient.java`
- Create: `jasper-app/src/test/java/dev/jasper/app/themestore/FakeMarketplace.java`
- Test: `jasper-app/src/test/java/dev/jasper/app/themestore/MarketplaceClientTest.java`

**Interfaces:**
- Produces (all in `MarketplaceClient`, public, blocking — never call on the EDT):
  - `record Listing(int id, String xmlId, String name, String preview, long downloads, double rating, boolean freemium, String iconPath, String vendor, String link)`
  - `record Page(List<Listing> listings, int total, int nextOffset)` with `boolean more()`
  - `record Details(int id, String xmlId, String name, String descriptionHtml, String vendor, String link)`
  - `record Release(long updateId, String version, String file)`
  - `static final class MarketplaceException extends Exception` (message is for the user)
  - `MarketplaceClient()`; package-private `MarketplaceClient(URI base, Predicate<URI> trusted, long maxDownload)`
  - `Page search(String query, int offset)`, `Details details(int id)`, `Optional<Release> latest(int id)`, `byte[] icon(String path)`, `void download(Release release, Path target)`, `void close()`
  - `static final URI DEFAULT_BASE`, `static final long MAX_DOWNLOAD`, `static final int PAGE_SIZE = 20`, `static boolean jetbrains(URI)`
- `FakeMarketplace` (test): `FakeMarketplace()`, `URI base()`, `MarketplaceClient client()`, `client(long maxDownload)`, `json(path, body)`, `bytes(path, status, body)`, `redirect(path, location)`, `theme(Path scratch, int id, String xmlId, String name, String themeName, String version)`, `List<String> requests`, static `search(int total, String... plugins)`, `plugin(id, xmlId, name, pricing)`, `release(updateId, version, file)`, `details(id, xmlId, name, html)`.

- [ ] **Step 1: Write the package-info**

```java
/**
 * Settings › Themes › IntelliJ Themes: the theme plugins in the themes folder beside config.toml, installing them from
 * disk, and browsing, installing and updating them from the JetBrains Marketplace, which makes this Jasper's only network
 * client. Nothing contacts the Marketplace unless that page asks, and themes.marketplace.enabled turns it off. The
 * application owns the {@link dev.jasper.app.themestore.ThemeStore} (its worker and HTTP client) and closes it at
 * shutdown; SettingsDialog owns each page. Swing work is EDT only; file and network work runs on the store's worker.
 * <p>Allowed outgoing Jasper dependencies: dev.jasper.app.appearance, dev.jasper.app.config, dev.jasper.app.lifecycle,
 * dev.jasper.app.settings, dev.jasper.app.settingsui.
 * The module architecture check forbids package cycles; app types are not an external plugin API.
 */
package dev.jasper.app.themestore;
```

- [ ] **Step 2: Write the test fixture `FakeMarketplace.java`**

```java
package dev.jasper.app.themestore;

import static java.nio.charset.StandardCharsets.UTF_8;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import dev.jasper.app.appearance.ThemeFixtures;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/** A local HTTP server standing in for plugins.jetbrains.com: it answers by path and records every request. */
final class FakeMarketplace implements AutoCloseable {
    interface Handler { void handle(HttpExchange exchange) throws IOException; }

    private final HttpServer server;
    private final Map<String, Handler> routes = new ConcurrentHashMap<>();
    /** Each request's path and query, in order. */
    final List<String> requests = new CopyOnWriteArrayList<>();

    FakeMarketplace() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            try {
                requests.add(exchange.getRequestURI().toString());
                Handler handler = routes.get(exchange.getRequestURI().getPath());
                if (handler == null) exchange.sendResponseHeaders(404, -1);
                else handler.handle(exchange);
            } finally {
                exchange.close();
            }
        });
        server.start();
    }

    URI base() { return URI.create("http://127.0.0.1:" + server.getAddress().getPort()); }

    MarketplaceClient client() { return client(MarketplaceClient.MAX_DOWNLOAD); }

    MarketplaceClient client(long maxDownload) {
        return new MarketplaceClient(base(), uri -> "127.0.0.1".equals(uri.getHost()), maxDownload);
    }

    FakeMarketplace json(String path, String body) { return bytes(path, 200, body.getBytes(UTF_8)); }

    FakeMarketplace bytes(String path, int status, byte[] body) {
        routes.put(path, exchange -> {
            exchange.sendResponseHeaders(status, body.length == 0 ? -1 : body.length);
            if (body.length > 0) try (var out = exchange.getResponseBody()) { out.write(body); }
        });
        return this;
    }

    FakeMarketplace redirect(String path, String location) {
        routes.put(path, exchange -> {
            exchange.getResponseHeaders().add("Location", location);
            exchange.sendResponseHeaders(302, -1);
        });
        return this;
    }

    /** Serves plugin {@code id} providing one dark theme, {@code themeName}, whose latest release is {@code version}. */
    FakeMarketplace theme(Path scratch, int id, String xmlId, String name, String themeName, String version) throws IOException {
        Path jar = ThemeFixtures.plugin(scratch.resolve(id + "-" + version + ".jar"), themeName, true, "#282a36", "282a36");
        String file = id + "/99/" + xmlId + "-" + version + ".jar";
        json("/api/searchPlugins", search(1, plugin(id, xmlId, name, "FREE")));
        json("/api/plugins/" + id, details(id, xmlId, name, "<p>A dark theme</p><img src='https://example.invalid/x.png'>"));
        json("/api/plugins/" + id + "/updates", release(99, version, file));
        bytes("/files/" + file, 200, Files.readAllBytes(jar));
        bytes("/files/" + id + "/icon/default.svg", 200,
            "<svg xmlns='http://www.w3.org/2000/svg' width='16' height='16'/>".getBytes(UTF_8));
        return this;
    }

    static String search(int total, String... plugins) {
        return "{\"plugins\":[" + String.join(",", plugins) + "],\"total\":" + total + "}";
    }

    /** A search listing shaped like the real one; numbers are plain JSON numbers here, strings are accepted too. */
    static String plugin(int id, String xmlId, String name, String pricing) {
        return "{\"id\":" + id + ",\"xmlId\":\"" + xmlId + "\",\"link\":\"/plugin/" + id + "\",\"name\":\"" + name
            + "\",\"preview\":\"About " + name + "\",\"downloads\":1234567,\"pricingModel\":\"" + pricing
            + "\",\"icon\":\"/files/" + id + "/icon/default.svg\",\"rating\":4.42,"
            + "\"vendor\":{\"name\":\"Vendor " + id + "\",\"isVerified\":false}}";
    }

    static String release(long updateId, String version, String file) {
        return "[{\"id\":" + updateId + ",\"version\":\"" + version + "\",\"file\":\"" + file + "\"}]";
    }

    /** Plugin details; like the real API, the vendor's {@code name} is an id and {@code publicName} is what people read. */
    static String details(int id, String xmlId, String name, String html) {
        return "{\"id\":" + id + ",\"name\":\"" + name + "\",\"xmlId\":\"" + xmlId + "\",\"link\":\"/plugin/" + id
            + "\",\"description\":\"" + html.replace("\"", "\\\"") + "\","
            + "\"vendor\":{\"name\":\"uuid-" + id + "\",\"publicName\":\"Vendor " + id + "\"}}";
    }

    @Override public void close() { server.stop(0); }
}
```

- [ ] **Step 3: Write the failing tests `MarketplaceClientTest.java`**

```java
package dev.jasper.app.themestore;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.*;

import java.net.ServerSocket;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class MarketplaceClientTest {
    @TempDir Path home;
    private FakeMarketplace market;
    private MarketplaceClient client;

    @BeforeEach void start() throws Exception { market = new FakeMarketplace(); client = market.client(); }
    @AfterEach void stop() { client.close(); market.close(); }

    @Test void searchReadsListingsDropsPaidAndMalformedOnesAndPagesByTheRawOffset() throws Exception {
        market.json("/api/searchPlugins", FakeMarketplace.search(45,
            FakeMarketplace.plugin(12275, "com.vermouthx.idea", "Dracula Theme", "FREE"),
            FakeMarketplace.plugin(2, "x.paid", "Paid Theme", "PAID"),
            FakeMarketplace.plugin(3, "x.freemium", "Freemium Theme", "FREEMIUM"),
            "{\"id\":\"4\",\"name\":\"No xmlId\"}",
            "{\"id\":\"5\",\"xmlId\":\"x.strings\",\"name\":\"Strings\",\"downloads\":\"3598855\",\"rating\":\"4.5\",\"pricingModel\":\"FREE\"}"));
        var page = client.search("  dracula pro ", 20);
        assertThat(page.listings()).extracting(MarketplaceClient.Listing::name).containsExactly("Dracula Theme", "Freemium Theme", "Strings");
        var dracula = page.listings().getFirst();
        assertThat(dracula).isEqualTo(new MarketplaceClient.Listing(12275, "com.vermouthx.idea", "Dracula Theme", "About Dracula Theme",
            1234567, 4.42, false, "/files/12275/icon/default.svg", "Vendor 12275", "/plugin/12275"));
        assertThat(page.listings().get(1).freemium()).isTrue();
        assertThat(page.listings().get(2).downloads()).isEqualTo(3598855);
        assertThat(page.total()).isEqualTo(45);
        assertThat(page.nextOffset()).isEqualTo(40);
        assertThat(page.more()).isTrue();
        assertThat(market.requests).containsExactly("/api/searchPlugins?tags=Theme&max=20&offset=20&search=dracula+pro");
        client.search("", 0);
        assertThat(market.requests.getLast()).isEqualTo("/api/searchPlugins?tags=Theme&max=20&offset=0&orderBy=downloads");
    }

    @Test void detailsPreferTheVendorsPublicNameAndLatestReadsTheNewestRelease() throws Exception {
        market.json("/api/plugins/12275", FakeMarketplace.details(12275, "com.vermouthx.idea", "Dracula Theme", "<p>Hi</p>"))
            .json("/api/plugins/12275/updates", FakeMarketplace.release(1162585, "2.3.1", "12275/1162585/dracula-theme-2.3.1.zip"))
            .json("/api/plugins/7/updates", "[]");
        assertThat(client.details(12275)).isEqualTo(new MarketplaceClient.Details(12275, "com.vermouthx.idea", "Dracula Theme",
            "<p>Hi</p>", "Vendor 12275", "/plugin/12275"));
        assertThat(client.latest(12275)).contains(new MarketplaceClient.Release(1162585, "2.3.1", "12275/1162585/dracula-theme-2.3.1.zip"));
        assertThat(market.requests).contains("/api/plugins/12275/updates?size=1");
        assertThat(client.latest(7)).isEmpty();
    }

    @Test void downloadsFollowTrustedRedirectsOnlyAndStopAtTheCap() throws Exception {
        byte[] body = "zip bytes".getBytes(UTF_8);
        market.redirect("/files/1/2/theme.zip", "/cdn/theme.zip").bytes("/cdn/theme.zip", 200, body)
            .redirect("/files/1/3/theme.zip", "http://example.invalid/theme.zip")
            .bytes("/files/1/4/big.zip", 200, new byte[2048]);
        Path target = home.resolve("theme.part");
        client.download(new MarketplaceClient.Release(2, "1.0", "1/2/theme.zip"), target);
        assertThat(target).hasBinaryContent(body);
        assertThatThrownBy(() -> client.download(new MarketplaceClient.Release(3, "1.0", "1/3/theme.zip"), home.resolve("x.part")))
            .isInstanceOf(MarketplaceClient.MarketplaceException.class).hasMessageContaining("example.invalid").hasMessageContaining("does not trust");
        try (var small = market.client(1024)) {
            assertThatThrownBy(() -> small.download(new MarketplaceClient.Release(4, "1.0", "1/4/big.zip"), home.resolve("big.part")))
                .isInstanceOf(MarketplaceClient.MarketplaceException.class).hasMessageContaining("larger than");
        }
        assertThatThrownBy(() -> client.download(new MarketplaceClient.Release(5, "1.0", "../../etc/passwd"), home.resolve("y.part")))
            .isInstanceOf(MarketplaceClient.MarketplaceException.class);
        assertThat(market.requests).noneMatch(request -> request.contains("etc"));
    }

    @Test void failuresBecomeMarketplaceExceptionsWithAMessageForPeople() throws Exception {
        market.bytes("/api/plugins/1", 500, new byte[0]).json("/api/plugins/2", "{not json");
        assertThatThrownBy(() -> client.details(1)).isInstanceOf(MarketplaceClient.MarketplaceException.class)
            .hasMessage("The Marketplace answered HTTP 500.");
        assertThatThrownBy(() -> client.details(2)).isInstanceOf(MarketplaceClient.MarketplaceException.class)
            .hasMessageContaining("cannot read");
        int closed;
        try (var socket = new ServerSocket(0)) { closed = socket.getLocalPort(); }
        try (var offline = new MarketplaceClient(URI.create("http://127.0.0.1:" + closed), uri -> true, 1024)) {
            assertThatThrownBy(() -> offline.search("", 0)).isInstanceOf(MarketplaceClient.MarketplaceException.class)
                .hasMessageStartingWith("Could not reach the JetBrains Marketplace");
        }
    }

    @Test void onlyHttpsJetbrainsHostsAreTrusted() {
        assertThat(MarketplaceClient.jetbrains(URI.create("https://downloads.marketplace.jetbrains.com/files/x.zip"))).isTrue();
        assertThat(MarketplaceClient.jetbrains(URI.create("https://plugins.jetbrains.com/x"))).isTrue();
        assertThat(MarketplaceClient.jetbrains(URI.create("http://plugins.jetbrains.com/x"))).isFalse();
        assertThat(MarketplaceClient.jetbrains(URI.create("https://jetbrains.com.evil.io/x"))).isFalse();
        assertThat(MarketplaceClient.jetbrains(URI.create("https://notjetbrains.com/x"))).isFalse();
        assertThat(Files.exists(home.resolve("nothing"))).isFalse();
    }
}
```

- [ ] **Step 4: Run them to verify they fail**

Run: `./gradlew :jasper-app:test --tests 'dev.jasper.app.themestore.MarketplaceClientTest'`
Expected: compilation FAILS (`MarketplaceClient` does not exist).

- [ ] **Step 5: Implement `MarketplaceClient.java`**

```java
package dev.jasper.app.themestore;

import com.formdev.flatlaf.json.Json;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.StringReader;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Predicate;

/**
 * The JetBrains Marketplace's public plugin API, for Theme-tagged plugins: search, details, the latest release, icons and
 * downloads. Every method blocks; call them on a worker, never the EDT. Requests carry the search text and nothing about
 * the user. Redirects are followed only to https JetBrains hosts, and every body is capped.
 */
public final class MarketplaceClient implements AutoCloseable {
    /** The Marketplace. */
    public static final URI DEFAULT_BASE = URI.create("https://plugins.jetbrains.com");
    /** The largest download accepted. */
    public static final long MAX_DOWNLOAD = 50L << 20;
    /** Listings asked for per search page. */
    public static final int PAGE_SIZE = 20;
    private static final int MAX_JSON = 4 << 20;
    private static final int MAX_ICON = 512 << 10;
    private static final int MAX_REDIRECTS = 5;

    /** One plugin in a search result. */
    public record Listing(int id, String xmlId, String name, String preview, long downloads, double rating, boolean freemium,
                          String iconPath, String vendor, String link) { }

    /** One page of search results; {@code nextOffset} asks for the page after it. */
    public record Page(List<Listing> listings, int total, int nextOffset) {
        public Page { listings = List.copyOf(listings); }
        /** Whether the Marketplace has more results after this page. */
        public boolean more() { return nextOffset < total; }
    }

    /** A plugin's page: its description is the Marketplace's HTML. */
    public record Details(int id, String xmlId, String name, String descriptionHtml, String vendor, String link) { }

    /** A plugin release: {@code file} is its path under {@code /files/}. */
    public record Release(long updateId, String version, String file) { }

    /** A failed call: offline, a timeout, an HTTP error, a refused redirect or a response Jasper cannot read. */
    public static final class MarketplaceException extends Exception {
        MarketplaceException(String message) { super(message); }
        MarketplaceException(String message, Throwable cause) { super(message, cause); }
    }

    private final URI base;
    private final Predicate<URI> trusted;
    private final long maxDownload;
    private final HttpClient http;

    public MarketplaceClient() { this(DEFAULT_BASE, MarketplaceClient::jetbrains, MAX_DOWNLOAD); }

    MarketplaceClient(URI base, Predicate<URI> trusted, long maxDownload) {
        this.base = Objects.requireNonNull(base);
        this.trusted = Objects.requireNonNull(trusted);
        this.maxDownload = maxDownload;
        http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).followRedirects(HttpClient.Redirect.NEVER).build();
    }

    /** Theme-tagged plugins matching {@code query} (the most downloaded when blank), from {@code offset}; paid ones are left out. */
    public Page search(String query, int offset) throws MarketplaceException {
        String text = query == null ? "" : query.strip();
        int from = Math.max(0, offset);
        String path = "/api/searchPlugins?tags=Theme&max=" + PAGE_SIZE + "&offset=" + from
            + (text.isEmpty() ? "&orderBy=downloads" : "&search=" + URLEncoder.encode(text, StandardCharsets.UTF_8));
        Object root = parse(get(path, MAX_JSON), "a search result");
        if (!(root instanceof Map<?, ?> map) || !(map.get("plugins") instanceof List<?> plugins))
            throw new MarketplaceException("The Marketplace sent a search result Jasper cannot read.");
        var listings = new ArrayList<Listing>();
        for (Object item : plugins) {
            if (!(item instanceof Map<?, ?> plugin) || "PAID".equals(plugin.get("pricingModel"))) continue;
            // One malformed listing is left out; the rest of the page still shows.
            try { listings.add(listing(plugin)); } catch (IllegalArgumentException malformed) { }
        }
        int total;
        try { total = map.get("total") == null ? from + plugins.size() : (int) number(map.get("total")); }
        catch (IllegalArgumentException malformed) { total = from + plugins.size(); }
        return new Page(listings, total, from + PAGE_SIZE);
    }

    /** The plugin's page. */
    public Details details(int id) throws MarketplaceException {
        Object root = parse(get("/api/plugins/" + id, MAX_JSON), "plugin details");
        if (!(root instanceof Map<?, ?> json)) throw new MarketplaceException("The Marketplace sent plugin details Jasper cannot read.");
        try {
            String vendor = json.get("vendor") instanceof Map<?, ?> v ? text(v, "publicName", text(v, "name", ""))
                : text(json, "vendor", "");
            return new Details(id, required(json, "xmlId"), required(json, "name"), text(json, "description", ""), vendor, text(json, "link", ""));
        } catch (IllegalArgumentException malformed) {
            throw new MarketplaceException("The Marketplace sent plugin details Jasper cannot read.", malformed);
        }
    }

    /** The plugin's newest release, or empty when it has none. */
    public Optional<Release> latest(int id) throws MarketplaceException {
        Object root = parse(get("/api/plugins/" + id + "/updates?size=1", MAX_JSON), "a release list");
        if (!(root instanceof List<?> list)) throw new MarketplaceException("The Marketplace sent a release list Jasper cannot read.");
        if (list.isEmpty()) return Optional.empty();
        try {
            if (!(list.getFirst() instanceof Map<?, ?> first)) throw new IllegalArgumentException("not an object");
            return Optional.of(new Release((long) number(first.get("id")), required(first, "version"), required(first, "file")));
        } catch (IllegalArgumentException malformed) {
            throw new MarketplaceException("The Marketplace sent a release Jasper cannot read.", malformed);
        }
    }

    /** An icon's bytes (usually SVG); {@code path} is a listing's {@code iconPath}. */
    public byte[] icon(String path) throws MarketplaceException {
        if (!path.startsWith("/") || path.contains("..")) throw new MarketplaceException("Not a Marketplace icon: " + path);
        return get(path, MAX_ICON);
    }

    /** Streams the release's file to {@code target}, which it creates or replaces; the caller deletes it on failure. */
    public void download(Release release, Path target) throws MarketplaceException {
        String file = release.file();
        if (!file.matches("[A-Za-z0-9._+/-]+") || file.contains("..") || file.startsWith("/"))
            throw new MarketplaceException("The Marketplace named a file Jasper will not download: " + file);
        var response = open("/files/" + file);
        try (InputStream in = response.body(); OutputStream out = Files.newOutputStream(target)) {
            byte[] buffer = new byte[64 << 10];
            long total = 0;
            for (int read; (read = in.read(buffer)) != -1; ) {
                total += read;
                if (total > maxDownload)
                    throw new MarketplaceException("The download is larger than " + (maxDownload >> 20) + " MB; Jasper stopped it.");
                out.write(buffer, 0, read);
            }
        } catch (IOException failure) {
            throw new MarketplaceException("The download failed: " + message(failure), failure);
        }
    }

    @Override public void close() { http.close(); }

    /** Whether {@code uri} is https on jetbrains.com or one of its subdomains. */
    static boolean jetbrains(URI uri) {
        String host = uri.getHost();
        if (!"https".equalsIgnoreCase(uri.getScheme()) || host == null) return false;
        String lower = host.toLowerCase(Locale.ROOT);
        return lower.equals("jetbrains.com") || lower.endsWith(".jetbrains.com");
    }

    private byte[] get(String path, int limit) throws MarketplaceException {
        var response = open(path);
        try (InputStream in = response.body()) {
            byte[] bytes = in.readNBytes(limit + 1);
            if (bytes.length > limit) throw new MarketplaceException("The Marketplace sent more than Jasper expected.");
            return bytes;
        } catch (IOException failure) {
            throw new MarketplaceException("The Marketplace stopped answering: " + message(failure), failure);
        }
    }

    /** A 200 response for {@code path}, following at most five trusted redirects. */
    private HttpResponse<InputStream> open(String path) throws MarketplaceException {
        URI uri = base.resolve(path);
        for (int hop = 0; hop <= MAX_REDIRECTS; hop++) {
            HttpResponse<InputStream> response;
            try {
                var request = HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(30)).header("User-Agent", "Jasper").GET().build();
                response = http.send(request, HttpResponse.BodyHandlers.ofInputStream());
            } catch (IOException failure) {
                throw new MarketplaceException("Could not reach the JetBrains Marketplace: " + message(failure), failure);
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                throw new MarketplaceException("The request was interrupted.", interrupted);
            }
            int status = response.statusCode();
            if (status / 100 == 3) {
                discard(response);
                URI current = uri;
                URI next = response.headers().firstValue("Location").map(current::resolve).orElse(null);
                if (next == null || !trusted.test(next))
                    throw new MarketplaceException("The Marketplace redirected to " + (next == null ? "nowhere" : next.getHost())
                        + ", which Jasper does not trust.");
                uri = next;
                continue;
            }
            if (status != 200) {
                discard(response);
                throw new MarketplaceException("The Marketplace answered HTTP " + status + ".");
            }
            return response;
        }
        throw new MarketplaceException("The Marketplace redirected too many times.");
    }

    private static void discard(HttpResponse<InputStream> response) {
        try { response.body().close(); } catch (IOException ignored) { }
    }

    private static Object parse(byte[] bytes, String what) throws MarketplaceException {
        try { return Json.parse(new StringReader(new String(bytes, StandardCharsets.UTF_8))); }
        catch (Exception malformed) {
            throw new MarketplaceException("The Marketplace sent " + what + " Jasper cannot read.", malformed);
        }
    }

    private static Listing listing(Map<?, ?> json) {
        String vendor = json.get("vendor") instanceof Map<?, ?> v ? text(v, "name", "") : "";
        return new Listing((int) number(json.get("id")), required(json, "xmlId"), required(json, "name"), text(json, "preview", ""),
            json.get("downloads") == null ? 0 : (long) number(json.get("downloads")),
            json.get("rating") == null ? 0 : number(json.get("rating")),
            "FREEMIUM".equals(json.get("pricingModel")), text(json, "icon", ""), vendor, text(json, "link", ""));
    }

    /** A JSON number; FlatLaf's parser hands numbers over as strings. */
    private static double number(Object value) {
        if (value instanceof Number number) return number.doubleValue();
        if (value instanceof String text) {
            try { return Double.parseDouble(text); } catch (NumberFormatException malformed) { }
        }
        throw new IllegalArgumentException("not a number: " + value);
    }

    private static String required(Map<?, ?> json, String key) {
        if (json.get(key) instanceof String text && !text.isBlank()) return text;
        throw new IllegalArgumentException("missing " + key);
    }

    private static String text(Map<?, ?> json, String key, String fallback) {
        return json.get(key) instanceof String text ? text : fallback;
    }

    private static String message(Throwable failure) {
        String message = failure.getMessage();
        return message == null || message.isBlank() ? failure.getClass().getSimpleName() : message;
    }
}
```

`Json.parse` declares `ParseException`; catching `Exception` covers it and any runtime failure.

- [ ] **Step 6: Run the tests**

Run: `./gradlew :jasper-app:test --tests 'dev.jasper.app.themestore.MarketplaceClientTest'`
Expected: PASS.

- [ ] **Step 7: Commit**

```bash
git branch --show-current
git add -A jasper-app/src
git commit -m "Marketplace client for IntelliJ theme plugins

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 5: Provenance, versions and the installer

**Files:**
- Create: `jasper-app/src/main/java/dev/jasper/app/themestore/Provenance.java`, `Versions.java`, `ThemeInstaller.java`
- Test: `jasper-app/src/test/java/dev/jasper/app/themestore/VersionsTest.java`, `ThemeInstallerTest.java`

**Interfaces:**
- Consumes: `ThemeCatalog.inspect` (Task 3), `MarketplaceClient.Release` (Task 4), `TomlLiterals` (`dev.jasper.app.settings`), tomlj.
- Produces:
  - `record Provenance.Entry(int marketplaceId, String xmlId, String name, String version)`; `Provenance(Path themes)`, `Map<String, Entry> read()`, `void write(Map<String, Entry>)`, `static final String FILE = ".sources.toml"`
  - `static boolean Versions.newer(String candidate, String installed)`
  - `ThemeInstaller(Path themes)`; `Path folder()`; `Map<String, Provenance.Entry> sources()`; `Path downloadTarget(MarketplaceClient.Release)`; `void clearDownloads()`; `Installed installDownload(Path download, String extension, Provenance.Entry source)`; `Installed installFromDisk(Path source, boolean replace)`; `void uninstall(Path file)`; `record Installed(Path file, List<String> themes)`; `static class InstallException extends Exception`; `static final class Conflict extends InstallException` with `Path existing()`.

- [ ] **Step 1: Write the failing tests**

`VersionsTest.java`:

```java
package dev.jasper.app.themestore;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class VersionsTest {
    @Test void numericPartsCompareAsNumbersAndAQualifierIsOlder() {
        assertThat(Versions.newer("2.3.1", "2.3.0")).isTrue();
        assertThat(Versions.newer("2.10", "2.9")).isTrue();
        assertThat(Versions.newer("2.3.0", "2.3.1")).isFalse();
        assertThat(Versions.newer("2.3", "2.3.0")).isFalse();
        assertThat(Versions.newer("2.3.1", "2.3.1")).isFalse();
        assertThat(Versions.newer("1.0.0", "1.0.0-beta")).isTrue();
        assertThat(Versions.newer("1.0.0-beta", "1.0.0")).isFalse();
        assertThat(Versions.newer("2024.2", "2024.1.3")).isTrue();
        assertThat(Versions.newer("1.0-rc2", "1.0-rc1")).isTrue();
    }
}
```

`ThemeInstallerTest.java`:

```java
package dev.jasper.app.themestore;

import static dev.jasper.app.appearance.ThemeFixtures.*;
import static org.assertj.core.api.Assertions.*;

import dev.jasper.app.appearance.ThemeCatalog;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ThemeInstallerTest {
    @TempDir Path home;
    private final Provenance.Entry dracula = new Provenance.Entry(12275, "com.vermouthx.idea", "Dracula Theme", "2.3.1");

    private Path themes() { return home.resolve("themes"); }

    private Path download(ThemeInstaller installer, long updateId, String theme) throws Exception {
        Path part = installer.downloadTarget(new MarketplaceClient.Release(updateId, "x", updateId + "/x.jar"));
        plugin(part, theme, true, "#282a36", "282a36");
        return part;
    }

    @Test void aMarketplaceDownloadWithAThemeLandsUnderItsPluginIdAndVersionAndIsRecorded() throws Exception {
        var installer = new ThemeInstaller(themes());
        var installed = installer.installDownload(download(installer, 1, "Dracula"), ".jar", dracula);
        assertThat(installed.file()).isEqualTo(themes().resolve("com.vermouthx.idea-2.3.1.jar"));
        assertThat(installed.themes()).containsExactly("Dracula");
        assertThat(installer.sources()).containsExactly(entry("com.vermouthx.idea-2.3.1.jar", dracula));
        assertThat(themes().resolve(".downloads")).isEmptyDirectory();
        assertThat(ThemeCatalog.beside(home.resolve("config.toml")).scan().find("Dracula")).isPresent();
    }

    @Test void aDownloadWithoutThemesIsRefusedAndLeavesNothingBehind() throws Exception {
        var installer = new ThemeInstaller(themes());
        Path part = installer.downloadTarget(new MarketplaceClient.Release(8, "1.0", "8/material.zip"));
        jar(part, entries("META-INF/plugin.xml", pluginXml("Material")));
        var material = new Provenance.Entry(8006, "com.chrisrm.idea.MaterialThemeUI", "Material Theme UI", "1.0");
        assertThatThrownBy(() -> installer.installDownload(part, ".zip", material))
            .isInstanceOf(ThemeInstaller.InstallException.class).hasMessageStartingWith("Material Theme UI has no themes Jasper can use.");
        try (var files = Files.list(themes())) {
            assertThat(files.map(file -> file.getFileName().toString())).containsExactly(".downloads");
        }
        assertThat(themes().resolve(".downloads")).isEmptyDirectory();
        assertThat(installer.sources()).isEmpty();
    }

    @Test void anUpdateReplacesTheOlderFileOfTheSamePlugin() throws Exception {
        var installer = new ThemeInstaller(themes());
        installer.installDownload(download(installer, 1, "Dracula"), ".jar", new Provenance.Entry(12275, "com.vermouthx.idea", "Dracula Theme", "2.3.0"));
        installer.installDownload(download(installer, 2, "Dracula"), ".jar", dracula);
        assertThat(themes().resolve("com.vermouthx.idea-2.3.0.jar")).doesNotExist();
        assertThat(themes().resolve("com.vermouthx.idea-2.3.1.jar")).isRegularFile();
        assertThat(installer.sources()).containsOnlyKeys("com.vermouthx.idea-2.3.1.jar");
    }

    @Test void aDiskInstallKeepsItsNameAndAsksBeforeReplacing() throws Exception {
        var installer = new ThemeInstaller(themes());
        Path nord = plugin(home.resolve("Downloads/nord.jar"), "Nord", true, "#2e3440", "2e3440");
        assertThat(installer.installFromDisk(nord, false).themes()).containsExactly("Nord");
        assertThat(themes().resolve("nord.jar")).isRegularFile();
        assertThat(installer.sources()).isEmpty();
        assertThatThrownBy(() -> installer.installFromDisk(nord, false)).isInstanceOfSatisfying(ThemeInstaller.Conflict.class,
            conflict -> assertThat(conflict.existing()).isEqualTo(themes().resolve("nord.jar")));
        assertThat(installer.installFromDisk(nord, true).file()).isEqualTo(themes().resolve("nord.jar"));
        Path notes = Files.writeString(home.resolve("Downloads/notes.txt"), "hi");
        assertThatThrownBy(() -> installer.installFromDisk(notes, false)).isInstanceOf(ThemeInstaller.InstallException.class)
            .hasMessage("notes.txt is not a .jar or .zip theme plugin.");
    }

    @Test void aDiskInstallOverAMarketplaceFileForgetsWhereTheOldOneCameFrom() throws Exception {
        var installer = new ThemeInstaller(themes());
        installer.installDownload(download(installer, 1, "Dracula"), ".jar", dracula);
        Path mine = plugin(home.resolve("Downloads/com.vermouthx.idea-2.3.1.jar"), "Dracula", true, "#000000", "000000");
        installer.installFromDisk(mine, true);
        assertThat(installer.sources()).isEmpty();
    }

    @Test void uninstallRemovesTheFileAndItsRecordAndOnlyInsideTheFolder() throws Exception {
        var installer = new ThemeInstaller(themes());
        var installed = installer.installDownload(download(installer, 1, "Dracula"), ".jar", dracula);
        installer.uninstall(installed.file());
        assertThat(installed.file()).doesNotExist();
        assertThat(installer.sources()).isEmpty();
        Path outside = plugin(home.resolve("Downloads/nord.jar"), "Nord", true, "#2e3440", "2e3440");
        assertThatThrownBy(() -> installer.uninstall(outside)).isInstanceOf(ThemeInstaller.InstallException.class);
        assertThat(outside).isRegularFile();
    }

    @Test void staleRecordsAndLeftoverDownloadsAreDropped() throws Exception {
        var installer = new ThemeInstaller(themes());
        Files.createDirectories(themes().resolve(".downloads"));
        Files.writeString(themes().resolve(".downloads/1.part"), "partial");
        Files.writeString(themes().resolve(".sources.toml"),
            "[\"gone-1.0.zip\"]\nmarketplace_id = 1\nxml_id = \"gone\"\nname = \"Gone\"\nversion = \"1.0\"\n");
        assertThat(installer.sources()).isEmpty();
        installer.clearDownloads();
        assertThat(themes().resolve(".downloads")).isEmptyDirectory();
        Files.writeString(themes().resolve(".sources.toml"), "this is = not [ toml");
        assertThat(installer.sources()).isEmpty();
    }
}
```

- [ ] **Step 2: Run them to verify they fail**

Run: `./gradlew :jasper-app:test --tests 'dev.jasper.app.themestore.VersionsTest' --tests 'dev.jasper.app.themestore.ThemeInstallerTest'`
Expected: compilation FAILS.

- [ ] **Step 3: Implement `Versions.java`**

```java
package dev.jasper.app.themestore;

/** IntelliJ-style dotted plugin versions. Pure. */
final class Versions {
    private Versions() { }

    /**
     * Whether {@code candidate} is newer than {@code installed}. Parts split on {@code .} and {@code -}; numbers compare
     * as numbers, a number beats a word ({@code 1.0.0} is newer than {@code 1.0.0-beta}), words compare ignoring case, and
     * a missing part counts as 0.
     */
    static boolean newer(String candidate, String installed) {
        if (candidate.equals(installed)) return false;
        String[] a = candidate.split("[.-]");
        String[] b = installed.split("[.-]");
        for (int i = 0; i < Math.max(a.length, b.length); i++) {
            int order = compare(i < a.length ? a[i] : "0", i < b.length ? b[i] : "0");
            if (order != 0) return order > 0;
        }
        return false;
    }

    private static int compare(String x, String y) {
        boolean xNumber = x.matches("\\d{1,18}");
        boolean yNumber = y.matches("\\d{1,18}");
        if (xNumber && yNumber) return Long.compare(Long.parseLong(x), Long.parseLong(y));
        if (xNumber != yNumber) return xNumber ? 1 : -1;
        return x.compareToIgnoreCase(y);
    }
}
```

- [ ] **Step 4: Implement `Provenance.java`**

```java
package dev.jasper.app.themestore;

import dev.jasper.app.settings.TomlLiterals;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import org.tomlj.Toml;
import org.tomlj.TomlParseResult;
import org.tomlj.TomlTable;

/**
 * Where each Marketplace install came from: {@code .sources.toml} in the themes folder, one table per installed file
 * name. A file without an entry was installed from disk. Callers serialize access (ThemeInstaller is synchronized).
 */
final class Provenance {
    /** A Marketplace install: the plugin's numeric id, its plugin.xml id, its name and the installed version. */
    record Entry(int marketplaceId, String xmlId, String name, String version) { }

    static final String FILE = ".sources.toml";
    private final Path file;

    Provenance(Path themes) { file = themes.resolve(FILE); }

    /** Every entry whose file still exists, by file name; empty when the file is missing or unreadable. */
    Map<String, Entry> read() {
        if (!Files.isRegularFile(file)) return Map.of();
        TomlParseResult toml;
        try { toml = Toml.parse(file); } catch (IOException | RuntimeException unreadable) { return Map.of(); }
        if (toml.hasErrors()) return Map.of();
        var entries = new TreeMap<String, Entry>();
        for (String name : toml.keySet()) {
            TomlTable table = toml.getTable(List.of(name));
            if (table == null) continue;
            Long id = table.getLong("marketplace_id");
            String xmlId = table.getString("xml_id");
            String plugin = table.getString("name");
            String version = table.getString("version");
            if (id == null || xmlId == null || version == null) continue;
            if (name.startsWith(".") || name.contains("/") || !Files.isRegularFile(file.resolveSibling(name))) continue;
            entries.put(name, new Entry(id.intValue(), xmlId, plugin == null ? xmlId : plugin, version));
        }
        return entries;
    }

    /** Replaces the file with {@code entries}, leaving out any whose file is gone. */
    void write(Map<String, Entry> entries) throws IOException {
        var text = new StringBuilder("# Where Jasper installed each theme plugin from. Settings > Themes keeps this file.\n");
        for (var named : new TreeMap<>(entries).entrySet()) {
            if (!Files.isRegularFile(file.resolveSibling(named.getKey()))) continue;
            Entry entry = named.getValue();
            text.append('\n').append('[').append(TomlLiterals.key(named.getKey())).append("]\n")
                .append("marketplace_id = ").append(entry.marketplaceId()).append('\n')
                .append("xml_id = ").append(TomlLiterals.string(entry.xmlId())).append('\n')
                .append("name = ").append(TomlLiterals.string(entry.name())).append('\n')
                .append("version = ").append(TomlLiterals.string(entry.version())).append('\n');
        }
        Files.createDirectories(file.getParent());
        Path temp = file.resolveSibling(FILE + ".tmp");
        Files.writeString(temp, text);
        Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
    }
}
```

- [ ] **Step 5: Implement `ThemeInstaller.java`**

```java
package dev.jasper.app.themestore;

import dev.jasper.app.appearance.ThemeCatalog;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

/**
 * Owns writes to the themes folder: lands Marketplace downloads, copies theme plugins from disk, uninstalls, and keeps
 * provenance. Every archive is checked with {@link ThemeCatalog#inspect} first; one without a usable theme is refused.
 * Blocking and synchronized; run it on the store's worker, never the EDT.
 */
final class ThemeInstaller {
    /** A refused or failed operation; the message is for the user. */
    static class InstallException extends Exception {
        InstallException(String message) { super(message); }
        InstallException(String message, Throwable cause) { super(message, cause); }
    }

    /** A disk install whose file name is taken; ask, then install again with {@code replace}. */
    static final class Conflict extends InstallException {
        private final Path existing;
        Conflict(Path existing) { super(existing.getFileName() + " is already installed."); this.existing = existing; }
        Path existing() { return existing; }
    }

    /** An installed archive and the themes it provides. */
    record Installed(Path file, List<String> themes) {
        Installed { themes = List.copyOf(themes); }
    }

    static final String DOWNLOADS = ".downloads";
    private final Path themes;
    private final Provenance provenance;

    ThemeInstaller(Path themes) {
        this.themes = themes.toAbsolutePath().normalize();
        this.provenance = new Provenance(this.themes);
    }

    Path folder() { return themes; }

    synchronized Map<String, Provenance.Entry> sources() { return provenance.read(); }

    /** Where {@code release} downloads to before it is checked; creates the downloads folder. */
    synchronized Path downloadTarget(MarketplaceClient.Release release) throws InstallException {
        try { return Files.createDirectories(themes.resolve(DOWNLOADS)).resolve(release.updateId() + ".part"); }
        catch (IOException failure) { throw new InstallException("Cannot write the themes folder: " + failure.getMessage(), failure); }
    }

    /** Deletes what earlier downloads left behind. */
    synchronized void clearDownloads() {
        Path folder = themes.resolve(DOWNLOADS);
        if (!Files.isDirectory(folder)) return;
        try (var files = Files.list(folder)) {
            for (Path file : files.toList()) Files.deleteIfExists(file);
        } catch (IOException ignored) {
            // A stale part file is harmless; the next page opening tries again.
        }
    }

    /**
     * Installs a downloaded Marketplace release as {@code <xmlId>-<version><extension>}, records it and removes the older
     * file of the same plugin. The download is deleted either way.
     */
    synchronized Installed installDownload(Path download, String extension, Provenance.Entry source) throws InstallException {
        try {
            var inspection = ThemeCatalog.inspect(download);
            if (inspection.themes().isEmpty()) throw new InstallException(source.name() + " has no themes Jasper can use." + detail(inspection));
            String name = safe(source.xmlId() + "-" + source.version()) + extension;
            Path target = themes.resolve(name);
            Files.move(download, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            var entries = new TreeMap<>(provenance.read());
            for (var entry : List.copyOf(entries.entrySet())) {
                if (!entry.getValue().xmlId().equals(source.xmlId()) || entry.getKey().equals(name)) continue;
                Files.deleteIfExists(themes.resolve(entry.getKey()));
                entries.remove(entry.getKey());
            }
            entries.put(name, source);
            provenance.write(entries);
            return new Installed(target, inspection.themes());
        } catch (IOException failure) {
            throw new InstallException("Cannot install " + source.name() + ": " + failure.getMessage(), failure);
        } finally {
            try { Files.deleteIfExists(download); } catch (IOException ignored) { }
        }
    }

    /** Copies {@code source} in under its own name; throws {@link Conflict} when the name is taken and {@code replace} is false. */
    synchronized Installed installFromDisk(Path source, boolean replace) throws InstallException {
        String name = source.getFileName().toString();
        String lower = name.toLowerCase(Locale.ROOT);
        if (!lower.endsWith(".jar") && !lower.endsWith(".zip")) throw new InstallException(name + " is not a .jar or .zip theme plugin.");
        if (name.startsWith(".")) name = "_" + name.substring(1);
        var inspection = ThemeCatalog.inspect(source);
        if (inspection.themes().isEmpty()) throw new InstallException(name + " has no themes Jasper can use." + detail(inspection));
        Path target = themes.resolve(name);
        if (Files.exists(target) && !replace) throw new Conflict(target);
        try {
            Files.createDirectories(themes);
            if (!Files.exists(target) || !Files.isSameFile(source, target)) Files.copy(source, target, StandardCopyOption.REPLACE_EXISTING);
            var entries = new TreeMap<>(provenance.read());
            if (entries.remove(name) != null) provenance.write(entries);
            return new Installed(target, inspection.themes());
        } catch (IOException failure) {
            throw new InstallException("Cannot install " + name + ": " + failure.getMessage(), failure);
        }
    }

    /** Deletes an installed archive and its record. */
    synchronized void uninstall(Path file) throws InstallException {
        Path absolute = file.toAbsolutePath().normalize();
        if (!themes.equals(absolute.getParent())) throw new InstallException(file.getFileName() + " is not in the themes folder.");
        try {
            Files.deleteIfExists(absolute);
            var entries = new TreeMap<>(provenance.read());
            entries.remove(absolute.getFileName().toString());
            provenance.write(entries);
        } catch (IOException failure) {
            throw new InstallException("Cannot uninstall " + file.getFileName() + ": " + failure.getMessage(), failure);
        }
    }

    private static String detail(ThemeCatalog.Inspection inspection) {
        return inspection.problems().isEmpty() ? "" : " " + inspection.problems().getFirst();
    }

    /** A file name made only of letters, digits, dots, dashes and underscores, never hidden. */
    private static String safe(String text) {
        String name = text.replaceAll("[^A-Za-z0-9._-]", "_");
        return name.startsWith(".") ? "_" + name.substring(1) : name;
    }
}
```

- [ ] **Step 6: Run the tests**

Run: `./gradlew :jasper-app:test --tests 'dev.jasper.app.themestore.*'`
Expected: PASS.

- [ ] **Step 7: Commit**

```bash
git branch --show-current
git add -A jasper-app/src
git commit -m "Theme installer: Marketplace downloads, disk installs, uninstall and provenance

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 6: The store and the installed list

**Files:**
- Create: `jasper-app/src/main/java/dev/jasper/app/themestore/InstalledTheme.java`, `ThemeStore.java`
- Test: `jasper-app/src/test/java/dev/jasper/app/themestore/ThemeStoreTest.java`

**Interfaces:**
- Consumes: Tasks 3–5.
- Produces:
  - `record InstalledTheme(Path file, String title, List<String> themes, Optional<Provenance.Entry> source, List<String> problems)` with `String version()`, `String origin()` ("Marketplace" / "From disk"), `static List<InstalledTheme> of(ThemeCatalog.Snapshot, Path folder, Map<String, Provenance.Entry>)` sorted by file name ignoring case.
  - `public final class ThemeStore implements AutoCloseable`: `public record Hooks(Supplier<CompletableFuture<?>> rescan, Consumer<URI> browse, Consumer<Path> reveal, BiPredicate<Component, String> confirm)`; `public static ThemeStore create(ThemeCatalog, Hooks)`; `public static boolean ask(Component, String)`; `public SettingsPage page(PageContext, SettingsPage header)` (added in Task 7); package-private `ThemeStore(ThemeCatalog, MarketplaceClient, Executor worker, Executor edt, Hooks, Runnable stopWorker)`; futures completed on `edt`: `search(String, int)`, `details(int)`, `latest(int)`, `icon(String)`, `installed()`, `updates(List<InstalledTheme>)` → `Map<Path, Release>`, `install(int id, String xmlId, String name)`, `installFromDisk(Path, boolean)`, `uninstall(Path)`; plus `clearDownloads()`, `revealFolder()`, `browse(String href)`, `confirm(Component, String)`, `static Throwable cause(Throwable)`, `static String message(Throwable)`, `close()`.

- [ ] **Step 1: Write the failing tests `ThemeStoreTest.java`**

```java
package dev.jasper.app.themestore;

import static dev.jasper.app.appearance.ThemeFixtures.plugin;
import static org.assertj.core.api.Assertions.*;

import dev.jasper.app.appearance.ThemeCatalog;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ThemeStoreTest {
    @TempDir Path home;
    private FakeMarketplace market;
    private ThemeCatalog catalog;
    private ThemeStore store;
    private final int[] rescans = new int[1];
    private final List<URI> browsed = new ArrayList<>();

    @BeforeEach void start() throws Exception {
        market = new FakeMarketplace();
        catalog = ThemeCatalog.beside(home.resolve("config.toml"));
        catalog.scan();
        store = new ThemeStore(catalog, market.client(), Runnable::run, Runnable::run, new ThemeStore.Hooks(
            () -> { rescans[0]++; catalog.scan(); return CompletableFuture.completedFuture(null); }, browsed::add, folder -> { },
            (parent, question) -> true), () -> { });
    }

    @AfterEach void stop() { store.close(); market.close(); }

    @Test void installingDownloadsTheLatestReleaseRecordsItRescansAndChecksForUpdates() throws Exception {
        market.theme(home.resolve("served"), 12275, "com.vermouthx.idea", "Dracula Theme", "Dracula", "2.3.1");
        var installed = store.install(12275, "com.vermouthx.idea", "Dracula Theme").get();
        assertThat(installed.file()).isEqualTo(home.resolve("themes/com.vermouthx.idea-2.3.1.jar"));
        assertThat(installed.themes()).containsExactly("Dracula");
        assertThat(rescans[0]).isEqualTo(1);
        assertThat(catalog.latest().find("Dracula")).isPresent();
        var listed = store.installed().get();
        assertThat(listed).singleElement().satisfies(theme -> {
            assertThat(theme.title()).isEqualTo("Dracula Theme");
            assertThat(theme.themes()).containsExactly("Dracula");
            assertThat(theme.version()).isEqualTo("2.3.1");
            assertThat(theme.origin()).isEqualTo("Marketplace");
        });
        assertThat(store.updates(listed).get()).isEmpty();
        market.theme(home.resolve("served"), 12275, "com.vermouthx.idea", "Dracula Theme", "Dracula", "2.4.0");
        assertThat(store.updates(listed).get()).containsOnlyKeys(installed.file());
    }

    @Test void aFailedDownloadLeavesNothingAndExplainsItself() {
        market.json("/api/plugins/7/updates", FakeMarketplace.release(1, "1.0", "7/1/gone.zip"));
        var failure = catchThrowable(() -> store.install(7, "x.gone", "Gone").get());
        assertThat(ThemeStore.cause(failure)).isInstanceOf(MarketplaceClient.MarketplaceException.class);
        assertThat(ThemeStore.message(failure)).isEqualTo("The Marketplace answered HTTP 404.");
        assertThat(home.resolve("themes/.downloads")).isEmptyDirectory();
        assertThat(rescans[0]).isZero();
    }

    @Test void theInstalledListShowsDiskInstallsAndFilesWithOnlyProblems() throws Exception {
        plugin(home.resolve("themes/nord.jar"), "Nord", true, "#2e3440", "2e3440");
        Files.writeString(home.resolve("themes/broken.zip"), "not a zip");
        catalog.scan();
        var listed = store.installed().get();
        assertThat(listed).extracting(InstalledTheme::title).containsExactly("broken", "nord");
        assertThat(listed.get(0).themes()).isEmpty();
        assertThat(listed.get(0).problems()).isNotEmpty();
        assertThat(listed.get(1).themes()).containsExactly("Nord");
        assertThat(listed.get(1).origin()).isEqualTo("From disk");
        assertThat(listed.get(1).version()).isEmpty();
        assertThat(store.updates(listed).get()).isEmpty();
        assertThat(market.requests).isEmpty();
    }

    @Test void browsingOpensOnlyWebLinksResolvedAgainstTheMarketplace() {
        store.browse("/plugin/12275-dracula-theme");
        store.browse("https://draculatheme.com/jetbrains");
        store.browse("javascript:alert(1)");
        store.browse("file:///etc/passwd");
        assertThat(browsed).containsExactly(URI.create("https://plugins.jetbrains.com/plugin/12275-dracula-theme"),
            URI.create("https://draculatheme.com/jetbrains"));
    }
}
```

- [ ] **Step 2: Run them to verify they fail**

Run: `./gradlew :jasper-app:test --tests 'dev.jasper.app.themestore.ThemeStoreTest'`
Expected: compilation FAILS.

- [ ] **Step 3: Implement `InstalledTheme.java`**

```java
package dev.jasper.app.themestore;

import dev.jasper.app.appearance.Theme;
import dev.jasper.app.appearance.ThemeCatalog;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.TreeSet;

/** One archive in the themes folder as the Installed tab shows it: a title, its themes, its source and its problems. */
record InstalledTheme(Path file, String title, List<String> themes, Optional<Provenance.Entry> source, List<String> problems) {
    InstalledTheme { themes = List.copyOf(themes); problems = List.copyOf(problems); }

    /** The installed version; empty for a disk install. */
    String version() { return source.map(Provenance.Entry::version).orElse(""); }

    /** "Marketplace" or "From disk". */
    String origin() { return source.isPresent() ? "Marketplace" : "From disk"; }

    /** Every archive in {@code folder} the snapshot provides themes from or has problems with, by file name ignoring case. */
    static List<InstalledTheme> of(ThemeCatalog.Snapshot snapshot, Path folder, Map<String, Provenance.Entry> sources) {
        var themes = new LinkedHashMap<Path, List<String>>();
        var problems = new LinkedHashMap<Path, List<String>>();
        for (Theme theme : snapshot.themes())
            if (theme.source() instanceof Theme.Archive archive && inFolder(archive.archive(), folder))
                themes.computeIfAbsent(archive.archive(), key -> new ArrayList<>()).add(theme.name());
        for (ThemeCatalog.Problem problem : snapshot.problems())
            if (inFolder(problem.file(), folder)) problems.computeIfAbsent(problem.file(), key -> new ArrayList<>()).add(problem.message());
        var files = new TreeSet<Path>(Comparator.comparing((Path file) -> file.getFileName().toString(), String.CASE_INSENSITIVE_ORDER)
            .thenComparing(file -> file.getFileName().toString()));
        files.addAll(themes.keySet());
        files.addAll(problems.keySet());
        var result = new ArrayList<InstalledTheme>();
        for (Path file : files) {
            String name = file.getFileName().toString();
            var source = Optional.ofNullable(sources.get(name));
            String title = source.map(Provenance.Entry::name).orElse(name.substring(0, Math.max(1, name.lastIndexOf('.'))));
            result.add(new InstalledTheme(file, title, themes.getOrDefault(file, List.of()), source, problems.getOrDefault(file, List.of())));
        }
        return List.copyOf(result);
    }

    private static boolean inFolder(Path file, Path folder) {
        String name = file.getFileName() == null ? "" : file.getFileName().toString().toLowerCase(Locale.ROOT);
        return folder.equals(file.toAbsolutePath().normalize().getParent()) && (name.endsWith(".jar") || name.endsWith(".zip"));
    }
}
```

- [ ] **Step 4: Implement `ThemeStore.java`** (without `page`, which Task 7 adds)

```java
package dev.jasper.app.themestore;

import dev.jasper.app.appearance.ThemeCatalog;
import java.awt.Component;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.function.BiPredicate;
import java.util.function.Consumer;
import java.util.function.Supplier;
import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;

/**
 * The IntelliJ Themes page's controller: the Marketplace client, the installer and a worker for their blocking work.
 * Call it on the EDT; every future it returns completes on the EDT. The application owns one and closes it at shutdown.
 */
public final class ThemeStore implements AutoCloseable {
    /**
     * What the store needs from the application.
     *
     * @param rescan reads the themes folder again (the configuration reload does) and completes when the catalog is current
     * @param browse opens a web page
     * @param reveal shows a folder in the file manager
     * @param confirm asks a yes/no question over a component
     */
    public record Hooks(Supplier<CompletableFuture<?>> rescan, Consumer<URI> browse, Consumer<Path> reveal,
                        BiPredicate<Component, String> confirm) {
        public Hooks {
            Objects.requireNonNull(rescan); Objects.requireNonNull(browse); Objects.requireNonNull(reveal); Objects.requireNonNull(confirm);
        }
    }

    private final ThemeCatalog catalog;
    private final MarketplaceClient client;
    private final ThemeInstaller installer;
    private final Executor worker;
    private final Executor edt;
    private final Hooks hooks;
    private final Runnable stopWorker;
    private final Map<String, CompletableFuture<byte[]>> icons = new ConcurrentHashMap<>();

    /** The application's store: the real Marketplace, a virtual-thread worker, results on the EDT. */
    public static ThemeStore create(ThemeCatalog catalog, Hooks hooks) {
        var worker = Executors.newThreadPerTaskExecutor(Thread.ofVirtual().name("jasper-themes-", 0).factory());
        return new ThemeStore(catalog, new MarketplaceClient(), worker, SwingUtilities::invokeLater, hooks, () -> worker.shutdownNow());
    }

    ThemeStore(ThemeCatalog catalog, MarketplaceClient client, Executor worker, Executor edt, Hooks hooks, Runnable stopWorker) {
        this.catalog = Objects.requireNonNull(catalog);
        this.client = Objects.requireNonNull(client);
        this.worker = Objects.requireNonNull(worker);
        this.edt = Objects.requireNonNull(edt);
        this.hooks = Objects.requireNonNull(hooks);
        this.stopWorker = Objects.requireNonNull(stopWorker);
        this.installer = new ThemeInstaller(catalog.directory()
            .orElseThrow(() -> new IllegalArgumentException("The built-in theme catalog has no themes folder")));
    }

    /** A standard Yes/No question; the production {@link Hooks#confirm}. */
    public static boolean ask(Component parent, String question) {
        return JOptionPane.showConfirmDialog(parent, question, "IntelliJ Themes", JOptionPane.YES_NO_OPTION,
            JOptionPane.QUESTION_MESSAGE) == JOptionPane.YES_OPTION;
    }

    CompletableFuture<MarketplaceClient.Page> search(String query, int offset) { return onEdt(supply(() -> client.search(query, offset))); }

    CompletableFuture<MarketplaceClient.Details> details(int id) { return onEdt(supply(() -> client.details(id))); }

    CompletableFuture<Optional<MarketplaceClient.Release>> latest(int id) { return onEdt(supply(() -> client.latest(id))); }

    /** An icon's bytes, fetched once per session. */
    CompletableFuture<byte[]> icon(String path) { return onEdt(icons.computeIfAbsent(path, key -> supply(() -> client.icon(key)))); }

    /** The archives in the themes folder as the catalog last read them. */
    CompletableFuture<List<InstalledTheme>> installed() {
        return onEdt(supply(() -> InstalledTheme.of(catalog.latest(), installer.folder(), installer.sources())));
    }

    /** The newer release of each Marketplace install that has one; a failed check leaves that theme out. */
    CompletableFuture<Map<Path, MarketplaceClient.Release>> updates(List<InstalledTheme> themes) {
        var checks = new ArrayList<CompletableFuture<Optional<Map.Entry<Path, MarketplaceClient.Release>>>>();
        for (InstalledTheme theme : themes) {
            if (theme.source().isEmpty()) continue;
            Provenance.Entry source = theme.source().get();
            checks.add(supply(() -> client.latest(source.marketplaceId())).handle((release, failure) ->
                failure != null || release == null ? Optional.<Map.Entry<Path, MarketplaceClient.Release>>empty()
                    : release.filter(found -> Versions.newer(found.version(), source.version()))
                        .map(found -> Map.entry(theme.file(), found))));
        }
        return onEdt(CompletableFuture.allOf(checks.toArray(CompletableFuture[]::new)).thenApply(ignored -> {
            var found = new LinkedHashMap<Path, MarketplaceClient.Release>();
            for (var check : checks) check.join().ifPresent(entry -> found.put(entry.getKey(), entry.getValue()));
            return Map.copyOf(found);
        }));
    }

    /** Downloads the plugin's latest release, installs it (replacing an older one) and rescans. */
    CompletableFuture<ThemeInstaller.Installed> install(int id, String xmlId, String name) {
        return onEdt(supply(() -> {
            MarketplaceClient.Release release = client.latest(id)
                .orElseThrow(() -> new ThemeInstaller.InstallException(name + " has no release to download."));
            Path part = installer.downloadTarget(release);
            try { client.download(release, part); }
            catch (MarketplaceClient.MarketplaceException failure) { Files.deleteIfExists(part); throw failure; }
            String extension = release.file().toLowerCase(Locale.ROOT).endsWith(".jar") ? ".jar" : ".zip";
            return installer.installDownload(part, extension, new Provenance.Entry(id, xmlId, name, release.version()));
        }).thenCompose(this::rescanned));
    }

    CompletableFuture<ThemeInstaller.Installed> installFromDisk(Path file, boolean replace) {
        return onEdt(supply(() -> installer.installFromDisk(file, replace)).thenCompose(this::rescanned));
    }

    CompletableFuture<Void> uninstall(Path file) {
        return onEdt(supply(() -> { installer.uninstall(file); return (Void) null; }).thenCompose(this::rescanned));
    }

    /** Deletes leftover downloads in the background. */
    void clearDownloads() {
        try { worker.execute(installer::clearDownloads); } catch (RejectedExecutionException closed) { }
    }

    void revealFolder() { hooks.reveal().accept(installer.folder()); }

    boolean confirm(Component parent, String question) { return hooks.confirm().test(parent, question); }

    /** Opens {@code href}, resolved against the Marketplace, when it is an http or https link. */
    void browse(String href) {
        URI uri;
        try { uri = MarketplaceClient.DEFAULT_BASE.resolve(href.strip()); }
        catch (IllegalArgumentException malformed) { return; }
        String scheme = uri.getScheme();
        if ("https".equalsIgnoreCase(scheme) || "http".equalsIgnoreCase(scheme)) hooks.browse().accept(uri);
    }

    /** The failure behind a future's wrapping. */
    static Throwable cause(Throwable failure) {
        Throwable at = failure;
        while ((at instanceof CompletionException || at instanceof ExecutionException) && at.getCause() != null) at = at.getCause();
        return at;
    }

    /** A message for the user about {@code failure}. */
    static String message(Throwable failure) {
        Throwable cause = cause(failure);
        if (cause instanceof MarketplaceClient.MarketplaceException || cause instanceof ThemeInstaller.InstallException) return cause.getMessage();
        return "Something went wrong: " + cause;
    }

    @Override public void close() {
        stopWorker.run();
        client.close();
    }

    /** {@code value} once the catalog has been read again; a failed rescan does not undo the change. */
    private <T> CompletableFuture<T> rescanned(T value) {
        return hooks.rescan().get().handle((ignored, failure) -> value);
    }

    private <T> CompletableFuture<T> supply(Callable<T> work) {
        var result = new CompletableFuture<T>();
        try {
            worker.execute(() -> {
                try { result.complete(work.call()); }
                catch (Throwable failure) { result.completeExceptionally(failure); }
            });
        } catch (RejectedExecutionException closed) {
            result.completeExceptionally(closed);
        }
        return result;
    }

    private <T> CompletableFuture<T> onEdt(CompletableFuture<T> work) {
        var result = new CompletableFuture<T>();
        work.whenComplete((value, failure) -> edt.execute(() -> {
            if (failure == null) result.complete(value);
            else result.completeExceptionally(cause(failure));
        }));
        return result;
    }
}
```

- [ ] **Step 5: Run the tests**

Run: `./gradlew :jasper-app:test --tests 'dev.jasper.app.themestore.*'`
Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git branch --show-current
git add -A jasper-app/src
git commit -m "Theme store: installed list, update checks and installs on a worker

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 7: The IntelliJ Themes page

**Files:**
- Create: `jasper-app/src/main/java/dev/jasper/app/themestore/Banner.java`, `InstalledTab.java`, `MarketplaceTab.java`, `IntelliJThemesPage.java`
- Modify: `jasper-app/src/main/java/dev/jasper/app/themestore/ThemeStore.java` (add `page`)
- Test: `jasper-app/src/test/java/dev/jasper/app/themestore/IntelliJThemesPageTest.java`

**Interfaces:**
- Consumes: `ThemeStore` (Task 6), `CorePages.INTELLIJ_THEMES`, `CorePages.specs`, `SettingsPage.reveal`, `FillingPage`, `PageContext`, `SettingsDraft.onChanged` (returns `dev.jasper.app.lifecycle.Subscription`).
- Produces: `public SettingsPage ThemeStore.page(PageContext context, SettingsPage header)`; `IntelliJThemesPage` package-private accessors for tests: `List<String> tabTitles()`, `String selectedTab()`, `Banner banner()`, `InstalledTab installed()`, `MarketplaceTab marketplace()`. Tab accessors listed in each class below.

- [ ] **Step 1: Write the failing tests `IntelliJThemesPageTest.java`**

```java
package dev.jasper.app.themestore;

import static dev.jasper.app.appearance.ThemeFixtures.plugin;
import static org.assertj.core.api.Assertions.assertThat;

import dev.jasper.app.appearance.ThemeCatalog;
import dev.jasper.app.config.ConfigLoader;
import dev.jasper.app.config.CoreSettings;
import dev.jasper.app.settingsui.CorePages;
import dev.jasper.app.settingsui.PageContext;
import dev.jasper.app.settingsui.SettingsDraft;
import dev.jasper.app.testsupport.EdtTestExtension;
import java.net.URI;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;

@ExtendWith(EdtTestExtension.class)
class IntelliJThemesPageTest {
    @TempDir Path home;
    private FakeMarketplace market;
    private ThemeCatalog catalog;
    private ThemeStore store;
    private SettingsDraft draft;
    private PageContext context;
    private Optional<Path> chosen = Optional.empty();
    private final List<String> confirmations = new ArrayList<>();
    private final List<URI> browsed = new ArrayList<>();

    @BeforeEach void start() throws Exception {
        market = new FakeMarketplace();
        catalog = ThemeCatalog.beside(home.resolve("config.toml"));
        catalog.scan();
        store = new ThemeStore(catalog, market.client(), Runnable::run, Runnable::run, new ThemeStore.Hooks(
            () -> { catalog.scan(); return CompletableFuture.completedFuture(null); }, browsed::add, folder -> { },
            (parent, question) -> { confirmations.add(question); return true; }), () -> { });
        draft = new SettingsDraft(ConfigLoader.parse(home.resolve("config.toml"), "", true).values());
        context = new PageContext(draft, id -> { }, Runnable::run, (title, initial) -> chosen, () -> { });
    }

    @AfterEach void stop() { store.close(); market.close(); }

    private IntelliJThemesPage page() {
        var system = new CorePages.SystemActions(() -> home.resolve("config.toml"), () -> { }, () -> { }, () -> { }, () -> { }, () -> false);
        var header = CorePages.specs(List::of, system).stream().filter(spec -> spec.id().equals(CorePages.INTELLIJ_THEMES))
            .findFirst().orElseThrow().build(context);
        var page = (IntelliJThemesPage) store.page(context, header);
        page.component();
        return page;
    }

    @Test void theMarketplaceTabFollowsTheSettingAndRevealPicksATab() {
        var page = page();
        assertThat(page.id()).isEqualTo("themes.intellij");
        assertThat(page.tabTitles()).containsExactly("Marketplace", "Installed");
        draft.stage(CoreSettings.THEMES_MARKETPLACE_ENABLED, Optional.of(false));
        assertThat(page.tabTitles()).containsExactly("Installed");
        page.reveal("marketplace");
        assertThat(page.selectedTab()).isEqualTo("Installed");
        assertThat(market.requests).isEmpty();
        draft.stage(CoreSettings.THEMES_MARKETPLACE_ENABLED, Optional.of(true));
        assertThat(page.tabTitles()).containsExactly("Marketplace", "Installed");
        page.reveal("marketplace");
        assertThat(page.selectedTab()).isEqualTo("Marketplace");
        page.reveal("installed");
        assertThat(page.selectedTab()).isEqualTo("Installed");
    }

    @Test void searchingAndInstallingAThemeOffersToUseIt() throws Exception {
        market.theme(home.resolve("served"), 12275, "com.vermouthx.idea", "Dracula Theme", "Dracula", "2.3.1");
        var page = page();
        page.selected();
        var tab = page.marketplace();
        assertThat(market.requests).contains("/api/searchPlugins?tags=Theme&max=20&offset=0&orderBy=downloads");
        assertThat(tab.listed()).containsExactly("Dracula Theme");
        tab.select("Dracula Theme");
        assertThat(tab.details()).contains("Dracula Theme", "Vendor 12275", "Version 2.3.1", "A dark theme");
        assertThat(market.requests).noneMatch(request -> request.contains("example.invalid"));
        assertThat(tab.installButton().getText()).isEqualTo("Install");
        tab.installButton().doClick();
        assertThat(home.resolve("themes/com.vermouthx.idea-2.3.1.jar")).isRegularFile();
        assertThat(catalog.latest().find("Dracula")).isPresent();
        assertThat(page.banner().text()).isEqualTo("Installed Dracula Theme.");
        assertThat(page.banner().actionText()).isEqualTo("Use “Dracula”");
        assertThat(tab.installButton().getText()).isEqualTo("Installed");
        page.banner().click();
        assertThat(draft.value(CoreSettings.UI_THEME_NAME)).isEqualTo("Dracula");
        page.reveal("installed");
        assertThat(page.installed().listed()).containsExactly("Dracula Theme");
        assertThat(page.installed().details()).contains("2.3.1", "Marketplace");
        tab.viewButton().doClick();
        assertThat(browsed).containsExactly(URI.create("https://plugins.jetbrains.com/plugin/12275"));
    }

    @Test void anInstalledMarketplaceThemeOffersItsUpdate() throws Exception {
        market.theme(home.resolve("served"), 12275, "com.vermouthx.idea", "Dracula Theme", "Dracula", "2.4.0");
        var installer = new ThemeInstaller(home.resolve("themes"));
        Path part = installer.downloadTarget(new MarketplaceClient.Release(1, "2.3.1", "x.jar"));
        plugin(part, "Dracula", true, "#282a36", "282a36");
        installer.installDownload(part, ".jar", new Provenance.Entry(12275, "com.vermouthx.idea", "Dracula Theme", "2.3.1"));
        catalog.scan();
        var page = page();
        page.reveal("installed");
        var tab = page.installed();
        tab.select("Dracula Theme");
        assertThat(tab.updateButton().isVisible()).isTrue();
        assertThat(tab.updateButton().getText()).isEqualTo("Update to 2.4.0");
        tab.updateButton().doClick();
        assertThat(home.resolve("themes/com.vermouthx.idea-2.4.0.jar")).isRegularFile();
        assertThat(home.resolve("themes/com.vermouthx.idea-2.3.1.jar")).doesNotExist();
        assertThat(tab.updateButton().isVisible()).isFalse();
    }

    @Test void installingFromDiskAsksBeforeReplacingAndUninstallAsksFirst() throws Exception {
        chosen = Optional.of(plugin(home.resolve("Downloads/nord.jar"), "Nord", true, "#2e3440", "2e3440"));
        var page = page();
        page.reveal("installed");
        var tab = page.installed();
        tab.installFromDiskButton().doClick();
        assertThat(home.resolve("themes/nord.jar")).isRegularFile();
        assertThat(tab.listed()).containsExactly("nord");
        assertThat(page.banner().actionText()).isEqualTo("Use “Nord”");
        tab.installFromDiskButton().doClick();
        assertThat(confirmations).containsExactly("nord.jar is already installed. Replace it?");
        tab.select("nord");
        assertThat(tab.useButtons()).containsExactly("Use “Nord”");
        tab.uninstallButton().doClick();
        assertThat(confirmations.getLast()).isEqualTo("Uninstall nord? Its themes: Nord.");
        assertThat(home.resolve("themes/nord.jar")).doesNotExist();
        assertThat(tab.listed()).isEmpty();
        assertThat(tab.details()).contains("No theme plugins installed");
        assertThat(market.requests).isEmpty();
    }

    @Test void aMarketplaceErrorOffersRetry() {
        market.bytes("/api/searchPlugins", 500, new byte[0]);
        var page = page();
        page.selected();
        assertThat(page.marketplace().status()).isEqualTo("The Marketplace answered HTTP 500.");
        assertThat(page.marketplace().retryButton().isVisible()).isTrue();
        market.json("/api/searchPlugins", FakeMarketplace.search(0));
        page.marketplace().retryButton().doClick();
        assertThat(page.marketplace().status()).isEqualTo("The Marketplace lists no themes.");
        assertThat(page.marketplace().retryButton().isVisible()).isFalse();
    }
}
```

- [ ] **Step 2: Run them to verify they fail**

Run: `./gradlew :jasper-app:test --tests 'dev.jasper.app.themestore.IntelliJThemesPageTest'`
Expected: compilation FAILS (`ThemeStore.page` and the page classes do not exist).

- [ ] **Step 3: Implement `Banner.java`**

```java
package dev.jasper.app.themestore;

import java.awt.BorderLayout;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.UIManager;

/** A one-line message above the tabs, with an optional action button; hidden while empty. EDT only. */
final class Banner extends JPanel {
    private final JLabel text = new JLabel();
    private final JButton action = new JButton();
    private Runnable onAction = () -> { };
    private boolean error;

    Banner() {
        super(new BorderLayout(8, 0));
        setOpaque(false);
        setBorder(BorderFactory.createEmptyBorder(0, 20, 8, 20));
        text.putClientProperty("html.disable", true);
        action.putClientProperty("html.disable", true);
        action.addActionListener(event -> onAction.run());
        add(text, BorderLayout.CENTER);
        add(action, BorderLayout.EAST);
        clear();
    }

    void info(String message) { show(message, false, null, null); }
    void error(String message) { show(message, true, null, null); }
    void offer(String message, String label, Runnable run) { show(message, false, label, run); }
    void clear() { show("", false, null, null); }

    private void show(String message, boolean isError, String label, Runnable run) {
        error = isError;
        text.setText(message);
        action.setText(label == null ? "" : label);
        action.setVisible(label != null);
        onAction = run == null ? () -> { } : run;
        setVisible(!message.isEmpty());
        restyle();
        revalidate();
        repaint();
    }

    @Override public void updateUI() {
        super.updateUI();
        // JPanel's constructor calls this before the fields exist.
        if (text != null) restyle();
    }

    private void restyle() { text.setForeground(UIManager.getColor(error ? "Actions.Red" : "Label.foreground")); }

    String text() { return text.getText(); }
    String actionText() { return action.isVisible() ? action.getText() : ""; }
    void click() { action.doClick(); }
}
```

- [ ] **Step 4: Implement `InstalledTab.java`**

```java
package dev.jasper.app.themestore;

import dev.jasper.app.settingsui.PageContext;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.FlowLayout;
import java.awt.Font;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.stream.Stream;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTextArea;
import javax.swing.ListCellRenderer;
import javax.swing.ListSelectionModel;
import javax.swing.UIManager;

/** The Installed tab: the theme plugins in the themes folder, Use, Update and Uninstall, and Install from Disk…. EDT only. */
final class InstalledTab {
    private final ThemeStore store;
    private final PageContext context;
    private final Banner banner;
    private final Consumer<String> use;
    private final List<Runnable> changed = new ArrayList<>();
    private final DefaultListModel<InstalledTheme> model = new DefaultListModel<>();
    private final JList<InstalledTheme> list = new JList<>(model);
    private final JPanel component = new JPanel(new BorderLayout());
    private final JPanel details = new JPanel();
    private final JLabel title = new JLabel();
    private final JLabel meta = new JLabel();
    private final JPanel uses = new JPanel(new FlowLayout(FlowLayout.LEADING, 6, 4));
    private final JTextArea problems = new JTextArea();
    private final JButton update = new JButton();
    private final JButton uninstall = new JButton("Uninstall");
    private final JButton fromDisk = new JButton("Install from Disk…");
    private final JButton reveal = new JButton("Reveal Folder");
    private Map<Path, MarketplaceClient.Release> updates = Map.of();
    private boolean marketplaceEnabled = true;
    private int generation;

    InstalledTab(ThemeStore store, PageContext context, Banner banner, Consumer<String> use) {
        this.store = store;
        this.context = context;
        this.banner = banner;
        this.use = use;
        for (JComponent label : List.of(title, meta, update, uninstall, fromDisk, reveal)) label.putClientProperty("html.disable", true);
        var toolbar = new JPanel(new FlowLayout(FlowLayout.LEADING, 6, 6));
        toolbar.setOpaque(false);
        toolbar.add(fromDisk);
        toolbar.add(reveal);
        fromDisk.addActionListener(event -> chooseFromDisk());
        reveal.addActionListener(event -> store.revealFolder());
        list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        list.setCellRenderer(new Renderer());
        list.getAccessibleContext().setAccessibleName("Installed theme plugins");
        list.addListSelectionListener(event -> { if (!event.getValueIsAdjusting()) showDetails(); });
        problems.setEditable(false);
        problems.setLineWrap(true);
        problems.setWrapStyleWord(true);
        problems.setOpaque(false);
        uses.setOpaque(false);
        update.addActionListener(event -> update());
        uninstall.addActionListener(event -> uninstall());
        details.setLayout(new BoxLayout(details, BoxLayout.Y_AXIS));
        details.setBorder(BorderFactory.createEmptyBorder(12, 14, 12, 14));
        var buttons = new JPanel(new FlowLayout(FlowLayout.LEADING, 6, 4));
        buttons.setOpaque(false);
        buttons.add(update);
        buttons.add(uninstall);
        for (JComponent part : List.of(title, meta, uses, buttons, problems)) { part.setAlignmentX(0f); details.add(part); }
        details.add(Box.createVerticalGlue());
        var split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, new JScrollPane(list), new JScrollPane(details));
        split.setResizeWeight(0.4);
        split.setBorder(null);
        component.setOpaque(false);
        component.add(toolbar, BorderLayout.NORTH);
        component.add(split, BorderLayout.CENTER);
        restyle();
        showDetails();
    }

    JComponent component() { return component; }

    /** Runs {@code listener} after each reload shows the folder's contents. */
    void onChanged(Runnable listener) { changed.add(listener); }

    void marketplaceEnabled(boolean enabled) { marketplaceEnabled = enabled; showDetails(); }

    /** Each installed Marketplace plugin's xmlId and version. */
    Map<String, String> installedVersions() {
        var versions = new HashMap<String, String>();
        for (int i = 0; i < model.size(); i++) model.get(i).source().ifPresent(source -> versions.put(source.xmlId(), source.version()));
        return versions;
    }

    /** Re-reads the folder, then asks the Marketplace for updates to the themes installed from it. */
    void reload() {
        int asked = ++generation;
        store.installed().whenComplete((themes, failure) -> {
            if (asked != generation) return;
            if (failure != null) { banner.error(ThemeStore.message(failure)); return; }
            show(themes);
            if (!marketplaceEnabled) return;
            store.updates(themes).whenComplete((found, ignored) -> {
                if (asked != generation || found == null) return;
                updates = found;
                showDetails();
            });
        });
    }

    private void show(List<InstalledTheme> themes) {
        InstalledTheme selected = list.getSelectedValue();
        Path keep = selected == null ? null : selected.file();
        updates = Map.of();
        model.clear();
        themes.forEach(model::addElement);
        int index = 0;
        for (int i = 0; i < model.size(); i++) if (model.get(i).file().equals(keep)) index = i;
        if (!model.isEmpty()) list.setSelectedIndex(index);
        showDetails();
        changed.forEach(Runnable::run);
    }

    private void showDetails() {
        InstalledTheme theme = list.getSelectedValue();
        uses.removeAll();
        if (theme == null) {
            title.setText(model.isEmpty() ? "No theme plugins installed" : "");
            meta.setText(model.isEmpty() ? "Install one from the Marketplace, or a .jar or .zip from disk." : "");
            problems.setText("");
            update.setVisible(false);
            uninstall.setVisible(false);
        } else {
            title.setText(theme.title());
            meta.setText(String.join("  ·  ", Stream.of(theme.version(), theme.origin(), theme.file().getFileName().toString())
                .filter(part -> !part.isEmpty()).toList()));
            for (String name : theme.themes()) {
                var button = new JButton("Use “" + name + "”");
                button.putClientProperty("html.disable", true);
                button.addActionListener(event -> use.accept(name));
                uses.add(button);
            }
            problems.setText(String.join("\n", theme.problems()));
            MarketplaceClient.Release release = updates.get(theme.file());
            update.setVisible(release != null && marketplaceEnabled);
            if (release != null) update.setText("Update to " + release.version());
            uninstall.setVisible(true);
        }
        details.revalidate();
        details.repaint();
    }

    private void update() {
        InstalledTheme theme = list.getSelectedValue();
        if (theme == null || theme.source().isEmpty()) return;
        var source = theme.source().get();
        busy(true);
        banner.info("Updating " + source.name() + "…");
        store.install(source.marketplaceId(), source.xmlId(), source.name()).whenComplete((result, failure) -> {
            busy(false);
            if (failure != null) banner.error(ThemeStore.message(failure));
            else banner.info("Updated " + source.name() + ".");
            reload();
        });
    }

    private void uninstall() {
        InstalledTheme theme = list.getSelectedValue();
        if (theme == null) return;
        String question = "Uninstall " + theme.title() + "?"
            + (theme.themes().isEmpty() ? "" : " Its themes: " + String.join(", ", theme.themes()) + ".");
        if (!store.confirm(component, question)) return;
        busy(true);
        store.uninstall(theme.file()).whenComplete((ignored, failure) -> {
            busy(false);
            if (failure != null) banner.error(ThemeStore.message(failure));
            else banner.info("Uninstalled " + theme.title() + ".");
            reload();
        });
    }

    private void chooseFromDisk() {
        context.chooseFile().apply("Install Theme Plugin", Optional.empty()).ifPresent(file -> installFromDisk(file, false));
    }

    private void installFromDisk(Path file, boolean replace) {
        busy(true);
        store.installFromDisk(file, replace).whenComplete((result, failure) -> {
            busy(false);
            if (ThemeStore.cause(failure) instanceof ThemeInstaller.Conflict conflict) {
                if (store.confirm(component, conflict.existing().getFileName() + " is already installed. Replace it?")) installFromDisk(file, true);
                return;
            }
            if (failure != null) { banner.error(ThemeStore.message(failure)); return; }
            reload();
            String first = result.themes().getFirst();
            banner.offer("Installed " + result.file().getFileName() + ".", "Use “" + first + "”", () -> use.accept(first));
        });
    }

    private void busy(boolean busy) {
        for (JButton button : List.of(update, uninstall, fromDisk)) button.setEnabled(!busy);
    }

    private void restyle() {
        Font base = UIManager.getFont("Label.font");
        if (base != null) title.setFont(base.deriveFont(Font.BOLD, base.getSize2D() + 2f));
        meta.setForeground(UIManager.getColor("Label.disabledForeground"));
        problems.setForeground(UIManager.getColor("Actions.Red"));
    }

    // Test access.
    List<String> listed() { var titles = new ArrayList<String>(); for (int i = 0; i < model.size(); i++) titles.add(model.get(i).title()); return titles; }
    void select(String name) { for (int i = 0; i < model.size(); i++) if (model.get(i).title().equals(name)) list.setSelectedIndex(i); }
    String details() { return String.join("\n", title.getText(), meta.getText(), problems.getText()); }
    List<String> useButtons() { return Stream.of(uses.getComponents()).map(button -> ((JButton) button).getText()).toList(); }
    JButton updateButton() { return update; }
    JButton uninstallButton() { return uninstall; }
    JButton installFromDiskButton() { return fromDisk; }

    /** Title in bold, then the themes it provides, its version and source; problems in red. */
    private static final class Renderer extends JPanel implements ListCellRenderer<InstalledTheme> {
        private final JLabel name = new JLabel();
        private final JLabel line = new JLabel();

        Renderer() {
            setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
            setBorder(BorderFactory.createEmptyBorder(6, 10, 6, 10));
            name.putClientProperty("html.disable", true);
            line.putClientProperty("html.disable", true);
            add(name);
            add(line);
        }

        @Override public Component getListCellRendererComponent(JList<? extends InstalledTheme> list, InstalledTheme value, int index,
                                                                boolean selected, boolean focused) {
            name.setText(value.title());
            name.setFont(list.getFont().deriveFont(Font.BOLD));
            boolean broken = value.themes().isEmpty();
            line.setText(broken ? "Not usable: " + value.problems().stream().findFirst().orElse("no themes")
                : String.join(", ", value.themes()) + (value.version().isEmpty() ? "" : "  ·  " + value.version()));
            name.setForeground(selected ? list.getSelectionForeground() : list.getForeground());
            line.setForeground(selected ? list.getSelectionForeground()
                : UIManager.getColor(broken ? "Actions.Red" : "Label.disabledForeground"));
            setBackground(selected ? list.getSelectionBackground() : list.getBackground());
            setOpaque(true);
            return this;
        }
    }
}
```

- [ ] **Step 5: Implement `MarketplaceTab.java`**

```java
package dev.jasper.app.themestore;

import com.formdev.flatlaf.FlatClientProperties;
import com.formdev.flatlaf.extras.FlatSVGIcon;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Supplier;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.DefaultListModel;
import javax.swing.Icon;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JEditorPane;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTextField;
import javax.swing.ListCellRenderer;
import javax.swing.ListSelectionModel;
import javax.swing.Timer;
import javax.swing.UIManager;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.event.HyperlinkEvent;
import javax.swing.text.BadLocationException;

/** The Marketplace tab: a search over Theme-tagged plugins, the selected plugin's details, and Install. EDT only. */
final class MarketplaceTab {
    private static final Icon BLANK = new ImageIcon(new BufferedImage(32, 32, BufferedImage.TYPE_INT_ARGB));
    private final ThemeStore store;
    private final Banner banner;
    private final Supplier<Map<String, String>> installedVersions;
    private final Consumer<String> use;
    private final Runnable installedChanged;
    private final JPanel component = new JPanel(new BorderLayout());
    private final JTextField search = new JTextField();
    private final DefaultListModel<MarketplaceClient.Listing> model = new DefaultListModel<>();
    private final JList<MarketplaceClient.Listing> list = new JList<>(model);
    private final JButton more = new JButton("Load more");
    private final JLabel status = new JLabel();
    private final JButton retry = new JButton("Retry");
    private final JLabel name = new JLabel();
    private final JLabel vendor = new JLabel();
    private final JLabel version = new JLabel();
    private final JEditorPane description = new JEditorPane();
    private final JButton install = new JButton("Install");
    private final JButton view = new JButton("View on Marketplace");
    private final Map<String, Icon> icons = new HashMap<>();
    private final Timer debounce;
    private String query = "";
    private int nextOffset;
    private boolean activated;
    private boolean busy;
    private int generation;
    private int detailGeneration;
    private MarketplaceClient.Release release;

    MarketplaceTab(ThemeStore store, Banner banner, Supplier<Map<String, String>> installedVersions, Consumer<String> use,
                   Runnable installedChanged) {
        this.store = store;
        this.banner = banner;
        this.installedVersions = installedVersions;
        this.use = use;
        this.installedChanged = installedChanged;
        debounce = new Timer(300, event -> search(search.getText()));
        debounce.setRepeats(false);
        search.putClientProperty(FlatClientProperties.PLACEHOLDER_TEXT, "Search themes");
        search.putClientProperty(FlatClientProperties.TEXT_FIELD_SHOW_CLEAR_BUTTON, true);
        search.getDocument().addDocumentListener(new DocumentListener() {
            @Override public void insertUpdate(DocumentEvent event) { debounce.restart(); }
            @Override public void removeUpdate(DocumentEvent event) { debounce.restart(); }
            @Override public void changedUpdate(DocumentEvent event) { debounce.restart(); }
        });
        search.addActionListener(event -> { debounce.stop(); search(search.getText()); });
        for (JComponent label : List.of(status, name, vendor, version, install, view, more, retry)) label.putClientProperty("html.disable", true);
        list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        list.setCellRenderer(new Renderer());
        list.getAccessibleContext().setAccessibleName("Marketplace themes");
        list.addListSelectionListener(event -> { if (!event.getValueIsAdjusting()) showDetails(); });
        more.addActionListener(event -> load());
        retry.addActionListener(event -> load());
        install.addActionListener(event -> install());
        view.putClientProperty(FlatClientProperties.BUTTON_TYPE, FlatClientProperties.BUTTON_TYPE_BORDERLESS);
        view.addActionListener(event -> {
            MarketplaceClient.Listing listing = list.getSelectedValue();
            if (listing != null && !listing.link().isEmpty()) store.browse(listing.link());
        });
        description.setContentType("text/html");
        description.setEditable(false);
        description.putClientProperty(JEditorPane.HONOR_DISPLAY_PROPERTIES, true);
        description.addHyperlinkListener(event -> {
            if (event.getEventType() == HyperlinkEvent.EventType.ACTIVATED && event.getDescription() != null) store.browse(event.getDescription());
        });

        var statusRow = new JPanel(new FlowLayout(FlowLayout.LEADING, 6, 4));
        statusRow.setOpaque(false);
        statusRow.add(status);
        statusRow.add(retry);
        statusRow.add(more);
        var left = new JPanel(new BorderLayout(0, 6));
        left.setOpaque(false);
        left.setBorder(BorderFactory.createEmptyBorder(6, 6, 6, 6));
        left.add(search, BorderLayout.NORTH);
        left.add(new JScrollPane(list), BorderLayout.CENTER);
        left.add(statusRow, BorderLayout.SOUTH);
        var header = new JPanel();
        header.setLayout(new BoxLayout(header, BoxLayout.Y_AXIS));
        header.setOpaque(false);
        header.setBorder(BorderFactory.createEmptyBorder(12, 14, 6, 14));
        var buttons = new JPanel(new FlowLayout(FlowLayout.LEADING, 6, 4));
        buttons.setOpaque(false);
        buttons.add(install);
        buttons.add(view);
        for (JComponent part : List.of(name, vendor, version, buttons)) { part.setAlignmentX(0f); header.add(part); }
        header.add(Box.createVerticalStrut(4));
        var right = new JPanel(new BorderLayout());
        right.add(header, BorderLayout.NORTH);
        right.add(new JScrollPane(description), BorderLayout.CENTER);
        var split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, left, right);
        split.setResizeWeight(0.45);
        split.setBorder(null);
        component.setOpaque(false);
        component.add(split, BorderLayout.CENTER);
        restyle();
        status("", false);
        more.setVisible(false);
        showDetails();
    }

    JComponent component() { return component; }

    /** Runs the first search the first time the tab is shown. */
    void activate() {
        if (activated) return;
        activated = true;
        search(search.getText());
    }

    /** Refreshes what depends on the installed themes: badges and the Install button. */
    void installedChanged() {
        list.repaint();
        MarketplaceClient.Listing listing = list.getSelectedValue();
        if (listing != null) syncInstall(listing);
    }

    void dispose() { debounce.stop(); generation++; detailGeneration++; }

    /** Starts a new search now. */
    void search(String text) {
        query = text == null ? "" : text.strip();
        model.clear();
        nextOffset = 0;
        load();
    }

    private void load() {
        int asked = ++generation;
        status("Searching…", false);
        more.setVisible(false);
        store.search(query, nextOffset).whenComplete((page, failure) -> {
            if (asked != generation) return;
            if (failure != null) { status(ThemeStore.message(failure), true); return; }
            page.listings().forEach(model::addElement);
            nextOffset = page.nextOffset();
            more.setVisible(page.more());
            status(!model.isEmpty() ? "" : query.isEmpty() ? "The Marketplace lists no themes." : "No themes match “" + query + "”.", false);
            if (list.getSelectedIndex() < 0 && !model.isEmpty()) list.setSelectedIndex(0);
        });
    }

    private void status(String text, boolean error) {
        status.setText(text);
        status.setForeground(UIManager.getColor(error ? "Actions.Red" : "Label.disabledForeground"));
        status.setVisible(!text.isEmpty());
        retry.setVisible(error);
    }

    private void showDetails() {
        MarketplaceClient.Listing listing = list.getSelectedValue();
        int asked = ++detailGeneration;
        release = null;
        boolean shown = listing != null;
        install.setVisible(shown);
        view.setVisible(shown && !listing.link().isEmpty());
        if (!shown) {
            name.setText(""); vendor.setText(""); version.setText(""); description.setText("");
            return;
        }
        name.setText(listing.name());
        vendor.setText(listing.vendor());
        version.setText("");
        description.setText("<html><body><p>" + escape(listing.preview()) + "</p></body></html>");
        syncInstall(listing);
        store.details(listing.id()).whenComplete((details, failure) -> {
            if (asked != detailGeneration || failure != null) return;
            description.setText(safeHtml(details.descriptionHtml()));
            description.setCaretPosition(0);
            if (!details.vendor().isEmpty()) vendor.setText(details.vendor());
        });
        store.latest(listing.id()).whenComplete((found, failure) -> {
            if (asked != detailGeneration) return;
            release = failure != null || found == null ? null : found.orElse(null);
            version.setText(release == null ? "" : "Version " + release.version());
            syncInstall(listing);
        });
    }

    private void syncInstall(MarketplaceClient.Listing listing) {
        String have = installedVersions.get().get(listing.xmlId());
        if (busy) { install.setText("Installing…"); install.setEnabled(false); }
        else if (have == null) { install.setText("Install"); install.setEnabled(true); }
        else if (release != null && Versions.newer(release.version(), have)) { install.setText("Update"); install.setEnabled(true); }
        else { install.setText("Installed"); install.setEnabled(false); }
    }

    private void install() {
        MarketplaceClient.Listing listing = list.getSelectedValue();
        if (listing == null || busy) return;
        busy = true;
        syncInstall(listing);
        banner.info("Installing " + listing.name() + "…");
        store.install(listing.id(), listing.xmlId(), listing.name()).whenComplete((result, failure) -> {
            busy = false;
            if (failure != null) banner.error(ThemeStore.message(failure));
            else {
                installedChanged.run();
                String first = result.themes().getFirst();
                banner.offer("Installed " + listing.name() + ".", "Use “" + first + "”", () -> use.accept(first));
            }
            installedChanged();
        });
    }

    private Icon icon(String path) {
        if (path.isEmpty()) return BLANK;
        Icon known = icons.get(path);
        if (known != null) return known;
        icons.put(path, BLANK);
        store.icon(path).whenComplete((bytes, failure) -> {
            if (failure != null) return;
            try { icons.put(path, new FlatSVGIcon(new ByteArrayInputStream(bytes)).derive(32, 32)); }
            catch (IOException | RuntimeException unreadable) { return; }
            list.repaint();
        });
        return BLANK;
    }

    private void restyle() {
        Font base = UIManager.getFont("Label.font");
        if (base != null) name.setFont(base.deriveFont(Font.BOLD, base.getSize2D() + 2f));
        vendor.setForeground(UIManager.getColor("Label.disabledForeground"));
        version.setForeground(UIManager.getColor("Label.disabledForeground"));
    }

    /** The description without anything that would load from the network or run: images, frames, objects, scripts and styles. */
    static String safeHtml(String html) {
        String body = html.replaceAll("(?is)<(script|style|iframe|object)\\b.*?</\\1\\s*>", "")
            .replaceAll("(?is)<(img|link|embed|iframe|object|meta|base)\\b[^>]*>", "");
        return "<html><body>" + body + "</body></html>";
    }

    static String escape(String text) { return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;"); }

    /** Vendor, downloads, rating and badges, joined with middle dots. */
    static String meta(MarketplaceClient.Listing listing, boolean installed) {
        var parts = new ArrayList<String>();
        if (!listing.vendor().isEmpty()) parts.add(listing.vendor());
        parts.add(downloads(listing.downloads()) + " downloads");
        if (listing.rating() > 0) parts.add(String.format(Locale.ROOT, "★ %.1f", listing.rating()));
        if (listing.freemium()) parts.add("Freemium");
        if (installed) parts.add("Installed");
        return String.join("  ·  ", parts);
    }

    static String downloads(long count) {
        if (count >= 1_000_000) return String.format(Locale.ROOT, "%.1fM", count / 1_000_000.0);
        if (count >= 1_000) return String.format(Locale.ROOT, "%.0fK", count / 1_000.0);
        return Long.toString(count);
    }

    // Test access.
    List<String> listed() { var names = new ArrayList<String>(); for (int i = 0; i < model.size(); i++) names.add(model.get(i).name()); return names; }
    void select(String listingName) { for (int i = 0; i < model.size(); i++) if (model.get(i).name().equals(listingName)) list.setSelectedIndex(i); }
    String details() {
        try { return String.join("\n", name.getText(), vendor.getText(), version.getText(),
            description.getDocument().getText(0, description.getDocument().getLength())); }
        catch (BadLocationException impossible) { throw new IllegalStateException(impossible); }
    }
    JButton installButton() { return install; }
    JButton viewButton() { return view; }
    JButton retryButton() { return retry; }
    String status() { return status.getText(); }

    /** Icon, bold name, then vendor, downloads, rating and badges. */
    private final class Renderer extends JPanel implements ListCellRenderer<MarketplaceClient.Listing> {
        private final JLabel image = new JLabel();
        private final JLabel title = new JLabel();
        private final JLabel line = new JLabel();

        Renderer() {
            super(new BorderLayout(10, 0));
            setBorder(BorderFactory.createEmptyBorder(6, 10, 6, 10));
            title.putClientProperty("html.disable", true);
            line.putClientProperty("html.disable", true);
            var text = new JPanel();
            text.setLayout(new BoxLayout(text, BoxLayout.Y_AXIS));
            text.setOpaque(false);
            text.add(title);
            text.add(line);
            add(image, BorderLayout.WEST);
            add(text, BorderLayout.CENTER);
        }

        @Override public Component getListCellRendererComponent(JList<? extends MarketplaceClient.Listing> owner,
                                                                MarketplaceClient.Listing value, int index, boolean selected, boolean focused) {
            image.setIcon(icon(value.iconPath()));
            title.setText(value.name());
            title.setFont(owner.getFont().deriveFont(Font.BOLD));
            line.setText(meta(value, installedVersions.get().containsKey(value.xmlId())));
            title.setForeground(selected ? owner.getSelectionForeground() : owner.getForeground());
            line.setForeground(selected ? owner.getSelectionForeground() : UIManager.getColor("Label.disabledForeground"));
            setBackground(selected ? owner.getSelectionBackground() : owner.getBackground());
            setOpaque(true);
            return this;
        }
    }
}
```

- [ ] **Step 6: Implement `IntelliJThemesPage.java`**

```java
package dev.jasper.app.themestore;

import dev.jasper.app.config.CoreSettings;
import dev.jasper.app.lifecycle.Subscription;
import dev.jasper.app.settingsui.FillingPage;
import dev.jasper.app.settingsui.PageContext;
import dev.jasper.app.settingsui.SettingsPage;
import java.awt.BorderLayout;
import java.awt.Component;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.JTabbedPane;

/**
 * Settings › Themes › IntelliJ Themes: the {@code themes.intellij} schema page (its settings) on top, a banner, and the
 * Marketplace and Installed tabs. Installs and uninstalls apply at once; only the header's settings wait for Apply.
 * SettingsDialog owns each page; disposing it drops late results. EDT only.
 */
final class IntelliJThemesPage implements SettingsPage {
    static final String MARKETPLACE = "marketplace";
    static final String INSTALLED = "installed";

    private final ThemeStore store;
    private final PageContext context;
    private final SettingsPage header;
    private FillingPage body;
    private JTabbedPane tabs;
    private Banner banner;
    private InstalledTab installed;
    private MarketplaceTab marketplace;
    private Subscription draftListening;

    IntelliJThemesPage(ThemeStore store, PageContext context, SettingsPage header) {
        this.store = store;
        this.context = context;
        this.header = header;
    }

    @Override public String id() { return header.id(); }
    @Override public String title() { return header.title(); }
    @Override public Optional<String> parent() { return header.parent(); }
    @Override public String helpAnchor() { return header.helpAnchor(); }

    @Override public List<String> searchTerms() {
        var terms = new ArrayList<>(header.searchTerms());
        terms.add("IntelliJ Themes Marketplace Installed install uninstall update theme plugins jar zip");
        return List.copyOf(terms);
    }

    @Override public JComponent component() {
        if (body != null) return body;
        banner = new Banner();
        installed = new InstalledTab(store, context, banner, this::use);
        marketplace = new MarketplaceTab(store, banner, installed::installedVersions, this::use, installed::reload);
        installed.onChanged(marketplace::installedChanged);
        tabs = new JTabbedPane();
        tabs.addTab("Marketplace", marketplace.component());
        tabs.addTab("Installed", installed.component());
        tabs.addChangeListener(event -> activate());
        var top = new JPanel(new BorderLayout());
        top.setOpaque(false);
        top.add(header.component(), BorderLayout.NORTH);
        top.add(banner, BorderLayout.SOUTH);
        var panel = new JPanel(new BorderLayout());
        panel.setOpaque(false);
        panel.add(top, BorderLayout.NORTH);
        panel.add(tabs, BorderLayout.CENTER);
        body = new FillingPage(panel);
        draftListening = context.draft().onChanged(this::syncTabs);
        syncTabs();
        return body;
    }

    @Override public void selected() {
        component();
        store.clearDownloads();
        activate();
    }

    @Override public void reveal(String fragment) {
        component();
        Component target = switch (fragment) {
            case MARKETPLACE -> marketplaceEnabled() ? marketplace.component() : installed.component();
            case INSTALLED -> installed.component();
            default -> null;
        };
        if (target != null && tabs.getSelectedComponent() != target) tabs.setSelectedComponent(target);
    }

    @Override public boolean modified() { return header.modified(); }
    @Override public boolean valid() { return header.valid(); }
    @Override public void reset() { header.reset(); }
    @Override public CompletableFuture<Void> apply() { return header.apply(); }
    @Override public void highlight(String query) { header.highlight(query); }
    @Override public void refresh() { header.refresh(); }

    @Override public void dispose() {
        header.dispose();
        if (draftListening != null) draftListening.close();
        if (marketplace != null) marketplace.dispose();
    }

    private boolean marketplaceEnabled() { return context.draft().value(CoreSettings.THEMES_MARKETPLACE_ENABLED); }

    /** Shows the Marketplace tab only while the setting (as edited) allows it. */
    private void syncTabs() {
        if (tabs == null) return;
        boolean enabled = marketplaceEnabled();
        int index = tabs.indexOfComponent(marketplace.component());
        if (enabled && index < 0) tabs.insertTab("Marketplace", null, marketplace.component(), null, 0);
        if (!enabled && index >= 0) tabs.removeTabAt(index);
        installed.marketplaceEnabled(enabled);
    }

    /** Loads what the visible tab shows: the Marketplace's first search, or the installed themes and their updates. */
    private void activate() {
        if (tabs.getSelectedComponent() == marketplace.component()) marketplace.activate();
        else installed.reload();
    }

    /** Chooses {@code theme} on the Appearance page's setting; it switches on Apply or OK. */
    private void use(String theme) {
        context.draft().stage(CoreSettings.UI_THEME_NAME, Optional.of(theme));
        banner.info("“" + theme + "” is chosen. Apply or OK switches to it.");
    }

    // Test access.
    List<String> tabTitles() { var titles = new ArrayList<String>(); for (int i = 0; i < tabs.getTabCount(); i++) titles.add(tabs.getTitleAt(i)); return titles; }
    String selectedTab() { return tabs.getTitleAt(tabs.getSelectedIndex()); }
    Banner banner() { return banner; }
    InstalledTab installed() { return installed; }
    MarketplaceTab marketplace() { return marketplace; }
}
```

- [ ] **Step 7: Add `page` to `ThemeStore`**

Imports `dev.jasper.app.settingsui.PageContext`, `dev.jasper.app.settingsui.SettingsPage`. After `ask`:

```java
    /** The IntelliJ Themes page for one Settings dialog, around {@code header}, the schema page holding its settings. */
    public SettingsPage page(PageContext context, SettingsPage header) { return new IntelliJThemesPage(this, context, header); }
```

- [ ] **Step 8: Run the tests**

Run: `./gradlew :jasper-app:test --tests 'dev.jasper.app.themestore.*'`
Expected: PASS. If `searchingAndInstallingAThemeOffersToUseIt` finds the banner's text replaced by the `use` message, check that `installed.reload()` does not call `banner`; only failures and installs write it.

- [ ] **Step 9: Commit**

```bash
git branch --show-current
git add -A jasper-app/src
git commit -m "IntelliJ Themes page: Marketplace and Installed tabs

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 8: Wiring, documentation and the full gate

**Files:**
- Modify: `jasper-app/src/main/java/dev/jasper/app/application/JasperApplication.java`
- Modify: `docs/themes.md`, `docs/configuration.md`, `docs/app-architecture.md`, `docs/STATUS.md`

**Interfaces:**
- Consumes: `ThemeStore.create`, `ThemeStore.Hooks`, `ThemeStore.ask`, `ThemeStore.page`, the 7-argument `CorePages.pages`.

- [ ] **Step 1: Wire the store**

In `JasperApplication.java`, next to `private PluginManager pluginManager;`:

```java
    private dev.jasper.app.themestore.ThemeStore themeStore;
```

Next to `pluginManager()`:

```java
    /** The IntelliJ Themes page's store, created the first time Settings builds that page. */
    private dev.jasper.app.themestore.ThemeStore themeStore() {
        if (themeStore == null) {
            var editor = new dev.jasper.app.platform.ConfigEditor();
            themeStore = dev.jasper.app.themestore.ThemeStore.create(themeCatalog, new dev.jasper.app.themestore.ThemeStore.Hooks(
                () -> configuration.reload(), this::browse,
                folder -> CompletableFuture.runAsync(() -> editor.reveal(folder)), dev.jasper.app.themestore.ThemeStore::ask));
        }
        return themeStore;
    }
```

In the `SettingsDialog` construction, replace the `CorePages.pages(...)` call with the 7-argument form:

```java
                dev.jasper.app.settingsui.CorePages.pages(() -> themeCatalog.latest().names(), system, shells,
                    context -> new dev.jasper.app.shortcuthelp.KeymapPage(keymap, context), context -> pluginManager().page(context),
                    context -> plugins.settingsPages(context), (context, header) -> themeStore().page(context, header)),
```

In `shutdown()`, next to `if (pluginTheme != null) pluginTheme.close();`:

```java
        if (themeStore != null) themeStore.close();
```

- [ ] **Step 2: Compile and run the app tests**

Run: `./gradlew :jasper-app:test`
Expected: PASS.

- [ ] **Step 3: Update the user documentation**

In `docs/themes.md`, replace the sentence "There is no theme picker: the configuration file is the only place a theme is chosen." with:

```markdown
Settings › Appearance › Theme lists every built-in and installed theme; **Get More Themes…** at the bottom opens
Settings › Themes › IntelliJ Themes.
```

Replace the section `## Installing a Marketplace theme` (through the paragraph ending "…and reads each theme's `.theme.json`" and its continuation up to "A theme you install takes precedence over a built-in theme of the same name.") with:

```markdown
## Installing a theme

Settings › Themes › IntelliJ Themes has two tabs.

- **Marketplace** searches the JetBrains Marketplace's theme plugins (most downloaded first). Select one to read its
  description, then **Install**. Jasper downloads the latest release, checks that it has a theme Jasper can use, and
  puts it in the `themes` folder beside `config.toml` (`~/.config/jasper/themes/` on macOS); the banner then offers
  **Use "…"**, which chooses it on the Appearance page (Apply or OK switches). Paid plugins are not listed; freemium
  ones are marked.
- **Installed** lists the theme plugins in the folder, with **Use** for each theme, **Update to …** when the
  Marketplace has a newer release of one installed from it, and **Uninstall**. **Install from Disk…** copies a
  downloaded `.jar` or `.zip` into the folder; **Reveal Folder** shows it.

You can also put a plugin's `.jar` or `.zip`, unchanged, in the folder yourself and choose **Reload config**.

Jasper contacts the Marketplace only while that page is open: searches, the selected plugin's details and icons, the
download, and one update check per Marketplace install each time the Installed tab opens. Requests carry your search
text and nothing else about you. Uncheck **Browse the JetBrains Marketplace** (`themes.marketplace.enabled = false`)
to hide the Marketplace tab and stop update checks.

Jasper finds a plugin's themes through its `META-INF/plugin.xml` and reads each theme's `.theme.json` and editor
colour scheme straight from the file. Nothing is unpacked, and no code in the plugin runs. A plugin whose theme needs
IntelliJ's code, such as Material Theme UI, is refused with a message. A theme you install takes precedence over a
built-in theme of the same name. Marketplace installs are named `<plugin id>-<version>`, and `themes/.sources.toml`
records where each came from.
```

In `docs/configuration.md`'s "Settings dialog" section, change the tree sentence to:

```markdown
The tree on the left groups them as Appearance & Behavior (Appearance, Window, Notifications, Command Palette,
Desk Buddy, System Settings), Themes (IntelliJ Themes), Terminal (Font & Colors, Cursor, Shell), Keymap and Plugins;
the search field filters the tree and highlights matching settings on the page.
```

and after the sentence ending "Help › Keyboard Shortcuts… and File › Manage Plugins… open these pages." add:

```markdown
Themes › IntelliJ Themes installs, updates and uninstalls theme plugins at once (see [Themes](themes.md)); only its
Browse the JetBrains Marketplace checkbox waits for Apply.
```

- [ ] **Step 4: Update the architecture guide**

In `docs/app-architecture.md`'s Package ownership table, after the `pluginmanager` row:

```markdown
| `themestore` | Settings › Themes › IntelliJ Themes: the blocking Marketplace client (Jasper's only network client, https JetBrains hosts only, capped bodies), the synchronized installer that owns writes to the themes folder and `.sources.toml`, and the page. Application owns the ThemeStore (a virtual-thread worker and the HTTP client) and closes it at shutdown; SettingsDialog owns each page. Futures complete on the EDT. |
```

Change the `settingsui` row's "the application supplies the Keymap and Plugins pages" to "the application supplies the Keymap, Plugins and IntelliJ Themes pages". At the end of the `## Themes` section add:

```markdown
Installed theme plugins live in the `themes` folder that `ThemeCatalog` scans. `themestore.ThemeInstaller` writes that
folder: it validates every archive with `ThemeCatalog.inspect` (the same loading a scan does) before moving or copying
it in, then asks the configuration service to reload, which rescans the catalog.
```

- [ ] **Step 5: Run the full gate**

Run: `./gradlew check`
Expected: BUILD SUCCESSFUL. Read the counts from `jasper-app/build/test-results/test/*.xml` (tests/failures/skipped), e.g.:

```bash
python3 - <<'PY'
import glob, xml.etree.ElementTree as ET
t=f=s=0
for p in glob.glob("jasper-app/build/test-results/test/*.xml"):
    r=ET.parse(p).getroot(); t+=int(r.get("tests")); f+=int(r.get("failures"))+int(r.get("errors")); s+=int(r.get("skipped"))
print(f"app {t}/{f}/{s}")
PY
```

Then run the source-hygiene check from `AGENTS.md` over `jasper-app/src`; it must print nothing.

- [ ] **Step 6: Record status**

In `docs/STATUS.md`, add an entry beside the other recent ones:

```markdown
- **IntelliJ themes from disk and the Marketplace** (branch `claude/theme-marketplace`, spec
  `docs/superpowers/specs/2026-09-30-intellij-theme-marketplace-design.md`, plan
  `docs/superpowers/plans/2026-09-30-intellij-theme-marketplace.md`): Settings gains a Themes group whose IntelliJ Themes
  page has Marketplace (search, details, Install) and Installed (Use, Update, Uninstall, Install from Disk…, Reveal
  Folder) tabs; the Appearance dropdown ends in Get More Themes… (Manage Themes… when `themes.marketplace.enabled` is
  false). New package `themestore` is Jasper's first network client. `./gradlew check` green: app <tests>/<failures>/<skipped>.
  Not merged. GUI checks pending with the user: Get More Themes… from the dropdown; search "dracula", install, Use and
  Apply; an Islands-based theme; Material Theme UI refused cleanly; Install from Disk…; Uninstall of the active theme;
  the offline checkbox; the page in dark and light themes.
```

(fill in the counts from Step 5, and add any deviations to the plan's status banner.)

- [ ] **Step 7: Commit**

```bash
git branch --show-current
git add -A jasper-app/src docs
git commit -m "Wire the IntelliJ Themes store into the app and document it

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```
