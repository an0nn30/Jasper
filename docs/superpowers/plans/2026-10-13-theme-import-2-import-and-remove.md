# Theme import Plan 2: Import… and Remove in Settings — Implementation Plan

> **Status (2026-10-13): executed on `claude/theme-import` (69ae4628..0b9a7249).** Review fixes beyond this plan's
> text: `ThemeFolder`'s Javadoc writes the message prefix as `{@code}` (doclint), a source that cannot be read is
> reported through `ThemeLibrary.reason`, and the byte-order mark in `ThemeFolderTest` is an escape;
> `ImportedThemesSection` words install, Remove and save failures ("permission denied.", "the file is no longer
> there.") instead of showing a path; `AppearancePageTest` passes the sixth `AppearanceChoices` argument;
> docs/app-maintenance.md names the palette's `view.terminal_scheme.match` and `view.terminal_scheme.<theme>.<key>`
> commands.

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Settings › Appearance gets an **Imported themes** group: Import… copies a checked Jasper theme file into
`<home>/themes/` (asking before replacing one), and Remove deletes an imported theme, returning every saved or staged
pick of it to its default.

**Architecture:** `appearance.ThemeFolder` does the blocking file work: check a file, derive the id from the theme's
name, copy it in through a temporary file and a move, and delete. `SchemaPage` gains a small, generic `Part`, a page
section that is not a setting, built from functions. `settingsui.ImportedThemesSection` is that part for themes, driven
by `settingsui.ThemeImports`, a record of the application's hooks: the folder, the imported list, Jasper's file
chooser filtered to `.json`, a confirmation sheet, an immediate save, a reload, a worker and the EDT. The application
builds the hooks over the live library and asks questions with the plugin manager's `ConfirmView`.

**Tech Stack:** Java 25 (JetBrains Runtime), Swing kit descriptors (`dev.jasper.ui.model`), JUnit 6.1.3, AssertJ 3.27.7.

**Spec:** `docs/superpowers/specs/2026-10-13-jasper-theme-import-design.md` (sections 4.1–4.3, 6.3 and 7).

**Workspace:** worktree `~/projects/moray/.claude/worktrees/theme-import`, branch `claude/theme-import`, **after Plan 1
is complete on it**. Run Plan 2 before Plan 3 (Plan 3 adds its own section to `docs/themes.md` and works either way, but
this plan's documentation patch expects Plan 1's `docs/themes.md`). Run every command from the worktree root. Never
build in `~/projects/moray` itself (the user runs the app there).

**Verified:** every task was carried out before this plan was written, on a scratch branch from Plan 1's last commit;
the patches are that branch's commits split into test and production halves. After Task 5, `./gradlew check --continue`
passed: **9,807 tests, 1 failure, 0 errors, 8 skipped**; the one failure was the Remote plugin's `SftpChooseSessionTest.theMenuListsSavedHostsLiterallyAndChoosingOneBrowsesIt`, which these plans do not touch (nothing under `plugins/` changes) and which passed 3 of 3 runs alone: it checks a passing "Connecting…" state that a fast "SSH agent not available" can skip, and another build was running alongside. Task 3's `aRemovalWhoseSettingsCannotBeSavedSaysSo` was added after that run; ImportedThemesSectionTest's 8 tests passed. The patches are made with `git diff --full-index`.

## How to apply a patch step

Each "Apply" step is one command: paste the block into a shell at the worktree root. If `git apply` reports a failure,
stop: the tree is not the one this plan starts from (Plan 1's last commit). Do not edit the patched files by hand first.

## Global Constraints

- Import and Remove act at once, outside Apply; Import selects nothing.
- Import messages (spec 4.2), shown under Import…: `Could not import <file>: <reason>`, with the reasons
  "Theme files are at most 1 MiB.", the loader's detail (with `line N: ` when the reader knows the line),
  "The theme's name has no letters or digits Jasper can use for a file name" and
  "Slate Steel is built in and can't be replaced"; success is `Imported <name>.`
- The id is the theme's name: Unicode-decomposed, accents dropped, lower-cased, runs of characters other than ASCII
  letters and digits replaced by `-`, leading and trailing `-` removed, cut to 64 characters (a trailing `-` removed).
  It is the same rule as the converter's `slug`.
- The original bytes are written to `<home>/themes/<id>.json` through a temporary file in the same folder and a move
  (atomic when the file system can).
- Replace asks "Replace <name>?" (Replace / Cancel). Remove asks "Remove <label>?", says the file is deleted and adds
  one sentence for each pick it resets ("It is your Dark theme; Slate Steel will be used instead." and so on).
- Remove deletes first; if the delete fails, nothing changes. It then stages defaults for the picks that name the
  theme (`ui.theme.dark`, `ui.theme.light`, a `terminal.scheme` of `<id>/…`), saves the saved ones at once and keeps
  other staged changes.
- settingsui reads jasper-ui only through `ui.model` and `ui.swing`; it may use `dev.jasper.app.appearance`'s
  `ThemeFolder` (its package-info says so). No new interface is introduced (hooks are records of functions).
- New `jasper-app` Java files that `ThemeMigrationCoverageTest` matches get `docs/design/theme-engine/migration.csv`
  rows (the patches add them).
- No GUI launch; commit on `claude/theme-import`; each commit message ends with:

  ```
  🤖 Generated with Claude Code at The Home Depot

  Co-Authored-By: Claude <noreply@anthropic.com>
  ```

## Review Focus

1. Cancelling the file chooser does nothing: no message, no reload (Task 3, `withNothingImportedTheGroupOffersImport`).
2. Importing a second file whose theme has the same name: asked first, and Cancel keeps the installed file byte for
   byte (Task 3, `importingTheSameThemeAgainAsksBeforeReplacingIt`).
3. Removing a theme while the user has unapplied Settings changes: the theme's staged picks go, the other staged
   changes stay (Task 3, `removingAThemeInUseReturnsItsSavedAndStagedPicksToTheirDefaults`).
4. A Remove whose file is already gone: the reason shows and no setting changes (Task 3,
   `aFailedDeleteIsShownAndChangesNothing`).
5. A Remove whose settings cannot be saved (`config.toml` has errors): the theme is gone and the message says its
   settings were not saved (Task 3, `aRemovalWhoseSettingsCannotBeSavedSaysSo`).

---

### Task 1: The themes folder's files

**What changes:** `appearance.ThemeFolder(Path folder)`: `id(name)` (the converter's rule), `prepare(source)` (read
through `ThemeLibrary.readText`, so the 1 MiB limit, UTF-8 and the byte-order mark behave as in the library; parse;
derive the id; refuse an empty id and `slate-steel`; note whether it replaces an imported theme), `install(candidate)`
(temporary file and move) and `remove(id)` (only a valid, non-built-in id).

**Files:** create `jasper-app/src/main/java/dev/jasper/app/appearance/ThemeFolder.java`,
`jasper-app/src/test/java/dev/jasper/app/appearance/ThemeFolderTest.java`; modify `migration.csv`.

**Interfaces:**
- Consumes: Plan 1's `ThemeLibrary.readText(Path)`, `MAX_BYTES`, `JasperTheme.parse`, `validId`, `BUNDLED_ID`,
  `ThemeFileException.line()/detail()`; test fixtures `ThemeTestSupport.terminalThemeJson`, `uiThemeJson`.
- Produces: `final class ThemeFolder`: `record Candidate(String id, String name, boolean replaces, byte[] bytes)`,
  `static final class ImportFailure extends Exception`, `ThemeFolder(Path)`, `Path folder()`, `static String id(String name)`,
  `Candidate prepare(Path source) throws ImportFailure`, `void install(Candidate) throws IOException`,
  `void remove(String id) throws IOException` (`IllegalArgumentException` for an id that is not an imported theme's).

- [ ] **Step 1: Apply the test**

`````bash
git apply --whitespace=nowarn <<'JASPER_PATCH'
diff --git a/jasper-app/src/test/java/dev/jasper/app/appearance/ThemeFolderTest.java b/jasper-app/src/test/java/dev/jasper/app/appearance/ThemeFolderTest.java
new file mode 100644
index 0000000000000000000000000000000000000000..6097664e42c6317bd4905240d20b0a90272187a6
--- /dev/null
+++ b/jasper-app/src/test/java/dev/jasper/app/appearance/ThemeFolderTest.java
@@ -0,0 +1,67 @@
+package dev.jasper.app.appearance;
+
+import static org.assertj.core.api.Assertions.*;
+
+import java.nio.file.Files;
+import java.nio.file.Path;
+import org.junit.jupiter.api.Test;
+import org.junit.jupiter.api.io.TempDir;
+
+/** spec 4.2–4.3: a theme file is checked, then copied in under the id its name gives; an imported theme's file can be deleted. */
+class ThemeFolderTest {
+    @TempDir Path home;
+
+    Path source(String fileName, String text) throws Exception {
+        return Files.writeString(Files.createDirectories(home.resolve("downloads")).resolve(fileName), text);
+    }
+
+    @Test void idsFollowTheConvertersRules() {
+        assertThat(ThemeFolder.id("Dracula")).isEqualTo("dracula");
+        assertThat(ThemeFolder.id("Café  Noir!")).isEqualTo("cafe-noir");
+        assertThat(ThemeFolder.id("Ночь")).isEmpty();
+        assertThat(ThemeFolder.id("x".repeat(80))).hasSize(64);
+        assertThat(ThemeFolder.id("a".repeat(63) + " b")).isEqualTo("a".repeat(63));
+        assertThat(ThemeFolder.id("  Gruvbox Dark (hard)  ")).isEqualTo("gruvbox-dark-hard");
+    }
+
+    @Test void aGoodFileIsCopiedInUnderItsIdWithItsOriginalBytes() throws Exception {
+        var folder = new ThemeFolder(home.resolve("themes"));
+        String text = "﻿" + ThemeTestSupport.terminalThemeJson("Gruvbox Dark");
+        var file = source("anything.json", text);
+        var candidate = folder.prepare(file);
+        assertThat(candidate.id()).isEqualTo("gruvbox-dark");
+        assertThat(candidate.name()).isEqualTo("Gruvbox Dark");
+        assertThat(candidate.replaces()).isFalse();
+        folder.install(candidate);
+        assertThat(Files.readAllBytes(home.resolve("themes/gruvbox-dark.json"))).isEqualTo(Files.readAllBytes(file));
+        try (var listing = Files.list(home.resolve("themes"))) { assertThat(listing).hasSize(1); }
+        assertThat(ThemeLibrary.read(home.resolve("themes"), ThemeLibrary.bundledOnly()).theme("gruvbox-dark")).isPresent();
+        assertThat(folder.prepare(file).replaces()).as("a second import replaces the first").isTrue();
+    }
+
+    @Test void aFileThatCannotBeImportedSaysWhy() throws Exception {
+        var folder = new ThemeFolder(home.resolve("themes"));
+        assertThatThrownBy(() -> folder.prepare(source("half.json", ThemeTestSupport.terminalThemeJson("Half").replace("\"fg.bold\": \"#FBF1C7\",", ""))))
+            .isInstanceOf(ThemeFolder.ImportFailure.class).hasMessage("terminal.dark: Missing colour \"fg.bold\"");
+        assertThatThrownBy(() -> folder.prepare(source("broken.json", "{ \"jasper-theme\": 1,\n  \"name\": ")))
+            .hasMessageStartingWith("line 2: ");
+        assertThatThrownBy(() -> folder.prepare(source("steel.json", ThemeTestSupport.uiThemeJson("Slate Steel"))))
+            .hasMessage("Slate Steel is built in and can't be replaced");
+        assertThatThrownBy(() -> folder.prepare(source("night.json", ThemeTestSupport.terminalThemeJson("Ночь"))))
+            .hasMessage("The theme's name has no letters or digits Jasper can use for a file name");
+        var huge = Files.write(home.resolve("downloads/huge.json"), new byte[ThemeLibrary.MAX_BYTES + 1]);
+        assertThatThrownBy(() -> folder.prepare(huge)).hasMessage("Theme files are at most 1 MiB.");
+        assertThatThrownBy(() -> folder.prepare(home.resolve("downloads/missing.json"))).isInstanceOf(ThemeFolder.ImportFailure.class);
+        assertThat(home.resolve("themes")).doesNotExist();
+    }
+
+    @Test void removingDeletesTheFileAndNeverTouchesSlateSteel() throws Exception {
+        var folder = new ThemeFolder(home.resolve("themes"));
+        folder.install(folder.prepare(source("z.json", ThemeTestSupport.terminalThemeJson("Zenburn"))));
+        folder.remove("zenburn");
+        assertThat(home.resolve("themes/zenburn.json")).doesNotExist();
+        assertThatThrownBy(() -> folder.remove("zenburn")).isInstanceOf(java.nio.file.NoSuchFileException.class);
+        assertThatThrownBy(() -> folder.remove("slate-steel")).isInstanceOf(IllegalArgumentException.class);
+        assertThatThrownBy(() -> folder.remove("../config")).isInstanceOf(IllegalArgumentException.class);
+    }
+}
JASPER_PATCH
`````

- [ ] **Step 2: See it fail**

Run: `./gradlew :jasper-app:compileTestJava`
Expected: FAIL, `cannot find symbol: class ThemeFolder`.

- [ ] **Step 3: Apply the production change**

`````bash
git apply --whitespace=nowarn <<'JASPER_PATCH'
diff --git a/docs/design/theme-engine/migration.csv b/docs/design/theme-engine/migration.csv
index 596f73595932f656c25c6fc529af23ce48dae2fe..e7ee8b470d34ec1213195310b402bd7566333027 100644
--- a/docs/design/theme-engine/migration.csv
+++ b/docs/design/theme-engine/migration.csv
@@ -1207,3 +1207,5 @@ jasper-app/src/main/java/dev/jasper/app/settingsui/SchemePick.java,app.settingsu
 jasper-app/src/main/java/dev/jasper/app/settingsui/InstalledThemes.java,app.settingsui,InstalledThemes,nonvisual-helper,none,none,jasper-app/src/test/java/dev/jasper/app/settingsui/AppearancePageTest.java,New in theme import Plan 1 Task 3; what the Appearance page offers.
 jasper-app/src/main/java/dev/jasper/app/appearance/ThemeSelection.java,app.appearance,ThemeSelection,nonvisual-helper,none,none,jasper-app/src/test/java/dev/jasper/app/appearance/ThemeSelectionTest.java,New in theme import Plan 1 Task 4; the themes and scheme picked.
 jasper-app/src/test/java/dev/jasper/app/appearance/ThemeSelectionTest.java,app.appearance,ThemeSelectionTest,nonvisual-helper,none,none,jasper-app/src/test/java/dev/jasper/app/appearance/ThemeSelectionTest.java,New in theme import Plan 1 Task 4; test owner of the theme picks.
+jasper-app/src/main/java/dev/jasper/app/appearance/ThemeFolder.java,app.appearance,ThemeFolder,nonvisual-helper,none,none,jasper-app/src/test/java/dev/jasper/app/appearance/ThemeFolderTest.java,New in theme import Plan 2 Task 1; importing and removing theme files.
+jasper-app/src/test/java/dev/jasper/app/appearance/ThemeFolderTest.java,app.appearance,ThemeFolderTest,nonvisual-helper,none,none,jasper-app/src/test/java/dev/jasper/app/appearance/ThemeFolderTest.java,New in theme import Plan 2 Task 1; test owner of the theme files.
diff --git a/jasper-app/src/main/java/dev/jasper/app/appearance/ThemeFolder.java b/jasper-app/src/main/java/dev/jasper/app/appearance/ThemeFolder.java
new file mode 100644
index 0000000000000000000000000000000000000000..a43c07e0db64216147eee9d3c6ff0e0daba62f6f
--- /dev/null
+++ b/jasper-app/src/main/java/dev/jasper/app/appearance/ThemeFolder.java
@@ -0,0 +1,93 @@
+package dev.jasper.app.appearance;
+
+import dev.jasper.ui.theme.JasperTheme;
+import dev.jasper.ui.theme.ThemeFileException;
+import java.io.IOException;
+import java.nio.file.AtomicMoveNotSupportedException;
+import java.nio.file.Files;
+import java.nio.file.Path;
+import java.nio.file.StandardCopyOption;
+import java.text.Normalizer;
+import java.util.Locale;
+import java.util.Objects;
+
+/**
+ * The themes folder's files: a theme file is checked, then copied in under its id, and an imported theme's file can
+ * be deleted. Blocking file work: callers run it off the EDT. The folder is created by the first import.
+ */
+public final class ThemeFolder {
+    /** A theme file read and checked: its theme's id and name, whether it replaces an imported theme, and its bytes. */
+    public record Candidate(String id, String name, boolean replaces, byte[] bytes) {
+        public Candidate { Objects.requireNonNull(id, "id"); Objects.requireNonNull(name, "name"); bytes = bytes.clone(); }
+        @Override public byte[] bytes() { return bytes.clone(); }
+    }
+
+    /** Why a file cannot be imported, in words Settings shows after "Could not import <file>: ". */
+    public static final class ImportFailure extends Exception {
+        ImportFailure(String message) { super(message); }
+    }
+
+    private final Path folder;
+
+    public ThemeFolder(Path folder) { this.folder = Objects.requireNonNull(folder, "folder"); }
+
+    /** The folder's path. */
+    public Path folder() { return folder; }
+
+    /**
+     * The theme id Jasper gives a theme named {@code name}: Unicode-decomposed, accents dropped, lower-cased, every run
+     * of characters other than ASCII letters and digits replaced by {@code -}, leading and trailing {@code -} removed,
+     * then cut to 64 characters (and a trailing {@code -} removed). Empty when nothing usable remains.
+     */
+    public static String id(String name) {
+        String plain = Normalizer.normalize(name, Normalizer.Form.NFKD).replaceAll("[^\\p{ASCII}]", "").toLowerCase(Locale.ROOT);
+        String words = plain.replaceAll("[^a-z0-9]+", "-").replaceAll("^-+|-+$", "");
+        return (words.length() > 64 ? words.substring(0, 64) : words).replaceAll("-+$", "");
+    }
+
+    /** {@code source} read and checked as a Jasper theme file, ready for {@link #install}. */
+    public Candidate prepare(Path source) throws ImportFailure {
+        String text;
+        byte[] bytes;
+        try {
+            text = ThemeLibrary.readText(source);
+            bytes = Files.readAllBytes(source);
+        } catch (IOException failure) {
+            throw new ImportFailure(failure.getMessage());
+        }
+        JasperTheme theme;
+        try {
+            theme = JasperTheme.parse(text, "import");
+        } catch (ThemeFileException failure) {
+            throw new ImportFailure((failure.line() > 0 ? "line " + failure.line() + ": " : "") + failure.detail());
+        }
+        String id = id(theme.name());
+        if (id.isEmpty()) throw new ImportFailure("The theme's name has no letters or digits Jasper can use for a file name");
+        if (id.equals(JasperTheme.BUNDLED_ID)) throw new ImportFailure("Slate Steel is built in and can't be replaced");
+        return new Candidate(id, theme.name(), Files.exists(file(id)), bytes);
+    }
+
+    /** Writes the candidate's bytes to {@code <id>.json} through a temporary file and a move, so a failed write keeps the old file. */
+    public void install(Candidate candidate) throws IOException {
+        Files.createDirectories(folder);
+        Path temporary = Files.createTempFile(folder, candidate.id() + "-", ".tmp");
+        try {
+            Files.write(temporary, candidate.bytes());
+            try {
+                Files.move(temporary, file(candidate.id()), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
+            } catch (AtomicMoveNotSupportedException unsupported) {
+                Files.move(temporary, file(candidate.id()), StandardCopyOption.REPLACE_EXISTING);
+            }
+        } finally {
+            Files.deleteIfExists(temporary);
+        }
+    }
+
+    /** Deletes the imported theme {@code id}'s file. */
+    public void remove(String id) throws IOException {
+        if (!JasperTheme.validId(id) || id.equals(JasperTheme.BUNDLED_ID)) throw new IllegalArgumentException("Not an imported theme: " + id);
+        Files.delete(file(id));
+    }
+
+    private Path file(String id) { return folder.resolve(id + ".json"); }
+}
JASPER_PATCH
`````

- [ ] **Step 4: Run the tests**

Run: `./gradlew :jasper-app:test --tests "*ThemeFolderTest" --tests "*ThemeMigrationCoverageTest"`
Expected: PASS (ThemeFolderTest: 4 tests).

- [ ] **Step 5: Commit**

```bash
git add -A
git commit -m "Check, copy in and delete theme files

🤖 Generated with Claude Code at The Home Depot

Co-Authored-By: Claude <noreply@anthropic.com>"
```

### Task 2: Page parts: sections that are not settings

**What changes:** `SchemaPage.Part(String id, String title, BiFunction<PageContext, Runnable, Section> build, List<String> terms)`
and `Part.Section(node, event, refresh, dispose)`; `Spec.part(Part)`. A part ends the section before it and is drawn as
`Nodes.Section("section-" + id, title, …)`; it is built once, when the page is first shown, with the page's context and
a re-render; it takes kit events first, is refreshed with the page and disposed with it; its title and terms are
searchable.

**Files:** modify `jasper-app/src/main/java/dev/jasper/app/settingsui/SchemaPage.java`,
`jasper-app/src/test/java/dev/jasper/app/settingsui/SchemaPageTest.java`.

**Interfaces:**
- Produces: `SchemaPage.Part`, `SchemaPage.Part.Section`, `SchemaPage.Spec.part(Part)`.

- [ ] **Step 1: Apply the test**

`````bash
git apply --whitespace=nowarn <<'JASPER_PATCH'
diff --git a/jasper-app/src/test/java/dev/jasper/app/settingsui/SchemaPageTest.java b/jasper-app/src/test/java/dev/jasper/app/settingsui/SchemaPageTest.java
index c33d7c72bfc7ffaa896d755e93d93908a8c79aa7..3ecd810d41aa69fc645d574f33c2cbef7b316486 100644
--- a/jasper-app/src/test/java/dev/jasper/app/settingsui/SchemaPageTest.java
+++ b/jasper-app/src/test/java/dev/jasper/app/settingsui/SchemaPageTest.java
@@ -51,6 +51,43 @@ class SchemaPageTest {
         assertThatThrownBy(page::dispose).isInstanceOf(IllegalStateException.class).satisfies(failure->assertThat(failure.getSuppressed()).hasSize(1));
         page.dispose();assertThat(calls).containsExactly(CoreSettings.TERMINAL_FONT_FAMILY.name(),CoreSettings.UI_FONT_FAMILY.name());
     }
+    @Test void aPartIsItsOwnSectionWithItsEventsRefreshAndDisposal() {
+        var calls = new ArrayList<String>();
+        var shown = new String[] {"none"};
+        Runnable[] render = new Runnable[1];
+        var part = new SchemaPage.Part("extras", "Extras", (pageContext, again) -> {
+            assertThat(pageContext).isSameAs(context);
+            render[0] = again;
+            return new SchemaPage.Part.Section(
+                id -> new Nodes.Stack(id, dev.jasper.ui.model.ControlState.READY, Nodes.LayoutRole.VERTICAL, List.of(
+                    new Nodes.Text(id + ":text", dev.jasper.ui.model.ControlState.READY, "Shown: " + shown[0], Nodes.TextRole.BODY),
+                    new Nodes.Button(id + ":go", dev.jasper.ui.model.ControlState.READY, "Go", "", Nodes.ButtonKind.SECONDARY, id + ":go", ""))),
+                event -> event instanceof dev.jasper.ui.model.UiEvent.Action action && action.id().equals("extras:go") && calls.add("go"),
+                () -> calls.add("refresh"), () -> calls.add("dispose"));
+        }, List.of("extra words"));
+        var page = SchemaPage.page("parts", "Parts").section("Bars").row(CoreSettings.WINDOW_STATUS_BAR).part(part)
+            .row(CoreSettings.WINDOW_TAB_HEIGHT, Editors.integer("px")).build(context);
+        assertThat(page.searchTerms()).contains("Extras", "extra words");
+        page.component();
+        var stack = (Nodes.Stack) page.viewSpec().root();
+        assertThat(stack.children()).as("Bars, then the part, then an untitled card of the rows after it").hasSize(3);
+        assertThat(((Nodes.Section) stack.children().get(0)).title()).isEqualTo("Bars");
+        assertThat(stack.children().get(1)).isInstanceOfSatisfying(Nodes.Section.class, section -> {
+            assertThat(section.id()).isEqualTo("section-extras");
+            assertThat(section.title()).isEqualTo("Extras");
+        });
+        assertThat(stack.children().get(2)).isInstanceOf(Nodes.Card.class);
+        kit(page, "extras:go", JButton.class).doClick();
+        assertThat(calls).containsExactly("go");
+        shown[0] = "later";
+        render[0].run();
+        assertThat(((Nodes.Text) node(page, "extras:text")).text()).isEqualTo("Shown: later");
+        page.refresh();
+        assertThat(calls).containsExactly("go", "refresh");
+        page.dispose();
+        assertThat(calls).containsExactly("go", "refresh", "dispose");
+    }
+
     @Test void rowsShowTheDraftAndStageEdits() {
         var page = page();
         page.component();
JASPER_PATCH
`````

- [ ] **Step 2: See it fail**

Run: `./gradlew :jasper-app:compileTestJava`
Expected: FAIL, `cannot find symbol: class Part`.

- [ ] **Step 3: Apply the production change**

`````bash
git apply --whitespace=nowarn <<'JASPER_PATCH'
diff --git a/jasper-app/src/main/java/dev/jasper/app/settingsui/SchemaPage.java b/jasper-app/src/main/java/dev/jasper/app/settingsui/SchemaPage.java
index c6c667ec3d3a605e7c83992a0cf3acfd52a5b9be..ada977900655b29b4d1dfb7190164edfd1f0e6a7 100644
--- a/jasper-app/src/main/java/dev/jasper/app/settingsui/SchemaPage.java
+++ b/jasper-app/src/main/java/dev/jasper/app/settingsui/SchemaPage.java
@@ -12,7 +12,9 @@ import java.util.List;
 import java.util.Objects;
 import java.util.Optional;
 import java.util.concurrent.CompletableFuture;
+import java.util.function.BiFunction;
 import java.util.function.BooleanSupplier;
+import java.util.function.Function;
 import java.util.function.Predicate;
 import java.util.function.Supplier;
 import javax.swing.JComponent;
@@ -27,10 +29,43 @@ public final class SchemaPage implements SettingsPage {
         public Button(String label, Runnable action, BooleanSupplier enabled) { this(label, action, enabled, ""); }
     }
 
-    private sealed interface Item permits RowItem, SectionItem, ActionItem { }
+    /**
+     * A section of a page that is not a setting, such as a list with its own buttons, under {@code title}. {@code build}
+     * runs once, when the page is first shown, with the page's context and a runnable that shows the page again (for
+     * work that finishes later). A part ends the section before it.
+     *
+     * @param id the part's node id prefix, unique on its page
+     * @param title the section's heading
+     * @param build makes the part's {@link Section} from the page's context and its re-render
+     * @param terms extra words the page's search matches
+     */
+    public record Part(String id, String title, BiFunction<PageContext, Runnable, Section> build, List<String> terms) {
+        public Part {
+            Objects.requireNonNull(id, "id"); Objects.requireNonNull(title, "title"); Objects.requireNonNull(build, "build");
+            terms = List.copyOf(terms);
+        }
+
+        /**
+         * A built part, as functions.
+         *
+         * @param node the section's content, given the part's id
+         * @param event takes a kit event first; true when the part handled it (the page then shows itself again)
+         * @param refresh reads again what the part shows; the page calls it on each refresh
+         * @param dispose releases the part when its page is disposed
+         */
+        public record Section(Function<String, Nodes.Node> node, Predicate<UiEvent> event, Runnable refresh, Runnable dispose) {
+            public Section {
+                Objects.requireNonNull(node, "node"); Objects.requireNonNull(event, "event");
+                Objects.requireNonNull(refresh, "refresh"); Objects.requireNonNull(dispose, "dispose");
+            }
+        }
+    }
+
+    private sealed interface Item permits RowItem, SectionItem, ActionItem, PartItem { }
     private record RowItem(Setting<?> setting, Editors.Factory<?> editor, String note, Predicate<SettingsDraft> enabled, boolean shown) implements Item { }
     private record SectionItem(String title) implements Item { }
     private record ActionItem(String title, Supplier<String> description, List<Button> buttons) implements Item { }
+    private record PartItem(Part part) implements Item { }
 
     /** Starts the spec of a page. */
     public static Spec page(String id, String title) { return new Spec(id, title); }
@@ -82,6 +117,9 @@ public final class SchemaPage implements SettingsPage {
         /** A settings row of buttons: {@code title} and {@code description} on the left, the buttons at its end. */
         public Spec action(String title, Supplier<String> description, Button... buttons) { items.add(new ActionItem(title, description, List.of(buttons))); return this; }
 
+        /** A section that is not a setting; see {@link Part}. */
+        public Spec part(Part part) { items.add(new PartItem(Objects.requireNonNull(part, "part"))); return this; }
+
         /** Every setting this page shows, in order. */
         public List<Setting<?>> settings() {
             return items.stream().filter(RowItem.class::isInstance).<Setting<?>>map(item -> ((RowItem) item).setting()).toList();
@@ -139,6 +177,7 @@ public final class SchemaPage implements SettingsPage {
     private final PageContext context;
     private final List<Row<?>> rows = new ArrayList<>();
     private final List<ActionRow> actions = new ArrayList<>();
+    private final List<Part.Section> parts = new ArrayList<>();
     private KitView view;
     private PageBody body;
     private Subscription listening;
@@ -161,6 +200,7 @@ public final class SchemaPage implements SettingsPage {
                 terms.add(String.join(" ", row.setting().keywords()));
                 terms.addAll(row.editor().terms());
             } else if (item instanceof SectionItem section && sectionShown(section)) terms.add(section.title());
+            else if (item instanceof PartItem part) { terms.add(part.part().title()); terms.addAll(part.part().terms()); }
         }
         return List.copyOf(terms);
     }
@@ -173,7 +213,7 @@ public final class SchemaPage implements SettingsPage {
         boolean after = false, anyRow = false;
         for (Item item : spec.items) {
             if (!after) { after = item == section; continue; }
-            if (item instanceof SectionItem) break;
+            if (item instanceof SectionItem || item instanceof PartItem) break;
             if (item instanceof ActionItem) return true;
             if (item instanceof RowItem row) { if (row.shown()) return true; anyRow = true; }
         }
@@ -196,6 +236,7 @@ public final class SchemaPage implements SettingsPage {
         for (Item item : spec.items) {
             if (item instanceof RowItem row && row.shown()) addRow(row);
             else if (item instanceof ActionItem action) actions.add(new ActionRow(action, actionIndex++));
+            else if (item instanceof PartItem part) parts.add(part.part().build().apply(context, this::render));
         }
         listening = context.draft().onChanged(this::showValues);
         showValues(false);
@@ -218,7 +259,7 @@ public final class SchemaPage implements SettingsPage {
         var children = new ArrayList<Nodes.Node>();
         var group = new ArrayList<Nodes.Node>();
         String title = null;
-        int sections = 0, rowIndex = 0, actionIndex = 0;
+        int sections = 0, rowIndex = 0, actionIndex = 0, partIndex = 0;
         for (Item item : spec.items) {
             switch (item) {
                 case SectionItem section -> {
@@ -238,6 +279,12 @@ public final class SchemaPage implements SettingsPage {
                     }
                 }
                 case ActionItem ignored -> group.add(actionNode(actions.get(actionIndex++)));
+                case PartItem part -> {
+                    close(children, group, title, sections++);
+                    title = null;
+                    children.add(new Nodes.Section("section-" + part.part().id(), ControlState.READY, part.part().title(),
+                        parts.get(partIndex++).node().apply(part.part().id())));
+                }
             }
         }
         close(children, group, title, sections);
@@ -281,6 +328,7 @@ public final class SchemaPage implements SettingsPage {
     /** Routes a kit event to the row or action it names. */
     void event(UiEvent event) {
         if (disposed) return;
+        for (var part : parts) if (part.event().test(event)) { render(); return; }
         if (event instanceof UiEvent.Action action) {
             for (var row : rows) if (action.id().equals(row.resetId())) { reset(row); return; }
             for (var actionRow : actions)
@@ -388,6 +436,7 @@ public final class SchemaPage implements SettingsPage {
 
     @Override public void refresh() {
         for (Row<?> row : rows) row.editor.refresh();
+        for (var part : parts) part.refresh().run();
         if (body != null) showValues();
     }
 
@@ -399,6 +448,7 @@ public final class SchemaPage implements SettingsPage {
         if(listening!=null)cleanup.add(listening::close);
         if(view!=null)cleanup.add(view::close);
         for(Row<?> row:rows)cleanup.add(row.editor::dispose);
+        for(var part:parts)cleanup.add(part.dispose());
         for(var action:cleanup)try{action.run();}catch(RuntimeException problem){if(failure==null)failure=problem;else failure.addSuppressed(problem);}
         if(failure!=null)throw failure;
     }
JASPER_PATCH
`````

- [ ] **Step 4: Run the tests**

Run: `./gradlew :jasper-app:test --tests "dev.jasper.app.settingsui.*"`
Expected: PASS, including `SchemaPageTest.aPartIsItsOwnSectionWithItsEventsRefreshAndDisposal`.

- [ ] **Step 5: Commit**

```bash
git add -A
git commit -m "Settings pages can hold sections that are not settings

🤖 Generated with Claude Code at The Home Depot

Co-Authored-By: Claude <noreply@anthropic.com>"
```

### Task 3: The Imported themes group

**What changes:**
- `settingsui.ThemeImports`, the hooks record (with `Imported(id, label, offers)` and
  `Question(title, question, consequence, yes, onYes)`).
- `settingsui.ImportedThemesSection`:
  - **The group:** a settings card with the Import… row ("No imported themes" when empty, else "Jasper theme files",
    with a note that the converter makes them), a message line under it (danger-coloured for failures) and a row per
    imported theme with its offers and Remove.
  - **Import:** choose → prepare on the worker → ask before replacing → install on the worker → reload → message.
  - **Remove:** ask (listing what will be reset) → delete on the worker → stage defaults and save the saved picks →
    message; a failed delete changes nothing.
- `AppearanceChoices` gains `Optional<ThemeImports> imports`; `CorePages` adds the part after the Theme section when it
  is present; settingsui's package-info allows `dev.jasper.app.appearance`.

**Files:** create `settingsui/ThemeImports.java`, `settingsui/ImportedThemesSection.java`,
`settingsui/ImportedThemesSectionTest.java`; modify `settingsui/{AppearanceChoices,CorePages,package-info}.java`,
`application/SettingsAppearance.java` (passes `Optional.empty()` until Task 4), tests `AppearanceFixtures`,
`AppearanceSelectionTest`, `migration.csv`.

**Interfaces:**
- Consumes: Task 1's `ThemeFolder`; Task 2's `SchemaPage.Part`; Plan 1's `CoreSettings.UI_THEME_DARK`,
  `UI_THEME_LIGHT`, `TERMINAL_SCHEME`, `SettingsDraft`, `SettingsEdit`.
- Produces: `record ThemeImports(ThemeFolder folder, Supplier<List<Imported>> imported, Function<AuxiliarySurface, Optional<Path>> chooseFile, BiConsumer<AuxiliarySurface, Question> confirm, Function<SettingsEdit, CompletableFuture<?>> save, Supplier<CompletableFuture<?>> reload, Executor worker, Executor ui)`;
  `AppearanceChoices(float, boolean, Supplier<Optional<Boolean>>, Function<Runnable, Subscription>, Supplier<InstalledThemes>, Optional<ThemeImports> imports)`;
  node ids `imported-themes:import`, `imported-themes:import-row`, `imported-themes:message`, `imported-themes:theme-<i>`,
  `imported-themes:remove-<i>`, section `section-imported-themes`.

- [ ] **Step 1: Apply the tests**

`````bash
git apply --whitespace=nowarn <<'JASPER_PATCH'
diff --git a/jasper-app/src/test/java/dev/jasper/app/settingsui/AppearanceFixtures.java b/jasper-app/src/test/java/dev/jasper/app/settingsui/AppearanceFixtures.java
index a805c2cb7dfaba3f18a76c3c3188a3073d0af06b..7945b2e7867f6684c600e418261614e744cf21a3 100644
--- a/jasper-app/src/test/java/dev/jasper/app/settingsui/AppearanceFixtures.java
+++ b/jasper-app/src/test/java/dev/jasper/app/settingsui/AppearanceFixtures.java
@@ -15,7 +15,7 @@ public final class AppearanceFixtures {
 
     public static AppearanceChoices choices(Supplier<Optional<Boolean>> systemDark) {
         var themes = InstalledThemes.slateSteel(preview(true), preview(false));
-        return new AppearanceChoices(100f, false, systemDark, ignored -> new Subscription(() -> { }), () -> themes);
+        return new AppearanceChoices(100f, false, systemDark, ignored -> new Subscription(() -> { }), () -> themes, Optional.empty());
     }
 
     /** A Slate Steel variant's preview, as the application builds it. */
diff --git a/jasper-app/src/test/java/dev/jasper/app/settingsui/AppearanceSelectionTest.java b/jasper-app/src/test/java/dev/jasper/app/settingsui/AppearanceSelectionTest.java
index d2b35d3ae0988e9d6755ece5983f22c08156632b..58d2de68ac501efc326dcad6d98226ba87a2c92f 100644
--- a/jasper-app/src/test/java/dev/jasper/app/settingsui/AppearanceSelectionTest.java
+++ b/jasper-app/src/test/java/dev/jasper/app/settingsui/AppearanceSelectionTest.java
@@ -24,7 +24,7 @@ class AppearanceSelectionTest {
     static SettingsDraft draft(String config) { return new SettingsDraft(ConfigLoader.parse(Path.of("config.toml"), config, true).values()); }
     static AppearanceSelection editor(SettingsDraft draft) {
         return new AppearanceSelection(new AppearanceChoices(100f, false, () -> Optional.of(false),
-            ignored -> new dev.jasper.app.lifecycle.Subscription(() -> {}), () -> THEMES), draft);
+            ignored -> new dev.jasper.app.lifecycle.Subscription(() -> {}), () -> THEMES, Optional.empty()), draft);
     }
     static List<Nodes.Node> parts(AppearanceSelection editor) {
         return ((Nodes.Stack) editor.node("ui.appearance", ControlState.READY)).children();
diff --git a/jasper-app/src/test/java/dev/jasper/app/settingsui/ImportedThemesSectionTest.java b/jasper-app/src/test/java/dev/jasper/app/settingsui/ImportedThemesSectionTest.java
new file mode 100644
index 0000000000000000000000000000000000000000..f0759be2b6c57ceb0ad1e3b3063ea4af875cb751
--- /dev/null
+++ b/jasper-app/src/test/java/dev/jasper/app/settingsui/ImportedThemesSectionTest.java
@@ -0,0 +1,191 @@
+package dev.jasper.app.settingsui;
+
+import static dev.jasper.app.settingsui.PageKit.kit;
+import static dev.jasper.app.settingsui.PageKit.node;
+import static org.assertj.core.api.Assertions.*;
+
+import dev.jasper.app.appearance.ThemeFolder;
+import dev.jasper.app.appearance.ThemeLibrary;
+import dev.jasper.app.appearance.ThemeTestSupport;
+import dev.jasper.app.config.ConfigLoader;
+import dev.jasper.app.config.CoreSettings;
+import dev.jasper.app.config.SettingsEdit;
+import dev.jasper.app.lifecycle.Subscription;
+import dev.jasper.app.testsupport.EdtTestExtension;
+import dev.jasper.ui.model.Nodes;
+import java.nio.file.Files;
+import java.nio.file.Path;
+import java.util.ArrayDeque;
+import java.util.ArrayList;
+import java.util.List;
+import java.util.Map;
+import java.util.Optional;
+import java.util.concurrent.CompletableFuture;
+import javax.swing.JButton;
+import org.junit.jupiter.api.Test;
+import org.junit.jupiter.api.extension.ExtendWith;
+import org.junit.jupiter.api.io.TempDir;
+
+/** spec 4.1–4.3: the Imported themes group lists the imported themes and imports and removes theme files at once. */
+@ExtendWith(EdtTestExtension.class)
+class ImportedThemesSectionTest {
+    @TempDir Path home;
+    final ArrayDeque<Path> chosen = new ArrayDeque<>();
+    final List<ThemeImports.Question> asked = new ArrayList<>();
+    final List<SettingsEdit> saved = new ArrayList<>();
+    final int[] reloads = new int[1];
+    boolean agree = true, failSaves;
+    SettingsDraft draft;
+    SchemaPage page;
+
+    Path folder() { return home.resolve("themes"); }
+
+    Path download(String fileName, String text) throws Exception {
+        return Files.writeString(Files.createDirectories(home.resolve("downloads")).resolve(fileName), text);
+    }
+
+    /** The page over {@code config}, with hooks that run at once and answer questions with {@link #agree}. */
+    SchemaPage page(String config) {
+        draft = new SettingsDraft(ConfigLoader.parse(Path.of("config.toml"), config, true).values());
+        var imports = new ThemeImports(new ThemeFolder(folder()),
+            () -> ThemeLibrary.read(folder(), ThemeLibrary.bundledOnly()).imported().stream()
+                .map(theme -> new ThemeImports.Imported(theme.id(), theme.name(), theme.has(true) ? "UI theme" : "Terminal scheme")).toList(),
+            surface -> Optional.ofNullable(chosen.poll()),
+            (surface, question) -> { asked.add(question); if (agree) question.onYes().run(); },
+            edit -> { saved.add(edit); return failSaves ? CompletableFuture.failedFuture(new java.util.concurrent.CompletionException(
+                new IllegalStateException("config.toml has errors"))) : CompletableFuture.completedFuture(null); },
+            () -> { reloads[0]++; return CompletableFuture.completedFuture(null); },
+            Runnable::run, Runnable::run);
+        var themes = AppearanceFixtures.CHOICES.themes();
+        var choices = new AppearanceChoices(100f, false, Optional::empty, ignored -> new Subscription(() -> { }), themes, Optional.of(imports));
+        var system = new CorePages.SystemActions(() -> Path.of("config.toml"), () -> { }, () -> { }, () -> { }, () -> { }, () -> false);
+        page = CorePages.specs(choices, system, ShellChoices.NONE, id -> { }).stream().filter(spec -> spec.id().equals("appearance"))
+            .findFirst().orElseThrow().build(PageKit.context(draft, id -> { }, () -> { }));
+        page.component();
+        return page;
+    }
+
+    void click(String id) { kit(page, id, JButton.class).doClick(); }
+    String title(String id) { return ((Nodes.SettingRow) node(page, id)).title(); }
+    Nodes.Text message() { return (Nodes.Text) node(page, "imported-themes:message"); }
+
+    @Test void withNothingImportedTheGroupOffersImport() {
+        page("");
+        assertThat(title("imported-themes:import-row")).isEqualTo("No imported themes");
+        assertThat(node(page, "section-imported-themes")).isInstanceOfSatisfying(Nodes.Section.class,
+            section -> assertThat(section.title()).isEqualTo("Imported themes"));
+        assertThat(page.searchTerms()).contains("Imported themes", "Import theme");
+        click("imported-themes:import");
+        assertThat(reloads[0]).as("cancelling the chooser does nothing").isZero();
+    }
+
+    @Test void importingCopiesTheFileInUnderItsIdAndListsIt() throws Exception {
+        page("");
+        var file = download("gruvbox_dark.json", ThemeTestSupport.terminalThemeJson("Gruvbox Dark"));
+        chosen.add(file);
+        click("imported-themes:import");
+        assertThat(Files.readAllBytes(folder().resolve("gruvbox-dark.json"))).isEqualTo(Files.readAllBytes(file));
+        assertThat(reloads[0]).isEqualTo(1);
+        assertThat(title("imported-themes:theme-0")).isEqualTo("Gruvbox Dark");
+        assertThat(((Nodes.SettingRow) node(page, "imported-themes:theme-0")).description()).isEqualTo("Terminal scheme");
+        assertThat(title("imported-themes:import-row")).isEqualTo("Jasper theme files");
+        assertThat(message().text()).isEqualTo("Imported Gruvbox Dark.");
+        assertThat(message().severity()).isEmpty();
+        assertThat(asked).isEmpty();
+    }
+
+    @Test void aFileThatCannotBeImportedSaysWhyUnderImportAndWritesNothing() throws Exception {
+        page("");
+        chosen.add(download("half.json", ThemeTestSupport.terminalThemeJson("Half").replace("\"fg.bold\": \"#FBF1C7\",", "")));
+        click("imported-themes:import");
+        assertThat(message().text()).isEqualTo("Could not import half.json: terminal.dark: Missing colour \"fg.bold\"");
+        assertThat(message().severity()).contains(Nodes.Severity.DANGER);
+        assertThat(folder()).doesNotExist();
+        assertThat(reloads[0]).isZero();
+        chosen.add(download("steel.json", ThemeTestSupport.uiThemeJson("Slate Steel")));
+        click("imported-themes:import");
+        assertThat(message().text()).isEqualTo("Could not import steel.json: Slate Steel is built in and can't be replaced");
+    }
+
+    @Test void importingTheSameThemeAgainAsksBeforeReplacingIt() throws Exception {
+        page("");
+        chosen.add(download("one.json", ThemeTestSupport.terminalThemeJson("Gruvbox Dark")));
+        click("imported-themes:import");
+        var newer = download("two.json", ThemeTestSupport.terminalThemeJson("Gruvbox Dark").replace("#282828", "#1D2021"));
+        agree = false;
+        chosen.add(newer);
+        click("imported-themes:import");
+        assertThat(asked).singleElement().satisfies(question -> {
+            assertThat(question.question()).isEqualTo("Replace Gruvbox Dark?");
+            assertThat(question.consequence()).isEqualTo("The imported Gruvbox Dark is replaced by two.json.");
+            assertThat(question.yes()).isEqualTo("Replace");
+        });
+        assertThat(Files.readString(folder().resolve("gruvbox-dark.json"))).as("Cancel keeps the old file").doesNotContain("#1D2021");
+        agree = true;
+        chosen.add(newer);
+        click("imported-themes:import");
+        assertThat(Files.readString(folder().resolve("gruvbox-dark.json"))).contains("#1D2021");
+        assertThat(message().text()).isEqualTo("Imported Gruvbox Dark.");
+    }
+
+    @Test void removingAThemeInUseReturnsItsSavedAndStagedPicksToTheirDefaults() throws Exception {
+        Files.createDirectories(folder());
+        Files.writeString(folder().resolve("ocean.json"), ThemeTestSupport.uiThemeJson("Ocean"));
+        page("[ui.theme]\ndark = \"ocean\"\n[terminal]\nscheme = \"ocean/high-contrast\"\n");
+        draft.stage(CoreSettings.UI_THEME_LIGHT, Optional.of("ocean"));
+        draft.stage(CoreSettings.UI_FONT_SIZE, Optional.of(15f));
+        click("imported-themes:remove-0");
+        assertThat(asked).singleElement().satisfies(question -> {
+            assertThat(question.question()).isEqualTo("Remove Ocean?");
+            assertThat(question.consequence()).isEqualTo("Its file, ocean.json, is deleted from the themes folder. "
+                + "It is your Dark theme; Slate Steel will be used instead. It is your Light theme; Slate Steel will be used instead. "
+                + "Its scheme is your terminal scheme; Same as UI theme will be used instead.");
+        });
+        assertThat(folder().resolve("ocean.json")).doesNotExist();
+        assertThat(saved).singleElement().satisfies(edit -> {
+            assertThat(edit.changes()).containsOnly(Map.entry(CoreSettings.UI_THEME_DARK, Optional.empty()),
+                Map.entry(CoreSettings.TERMINAL_SCHEME, Optional.empty()));
+            assertThat(edit.expected()).containsOnly(Map.entry(CoreSettings.UI_THEME_DARK, Optional.of("ocean")),
+                Map.entry(CoreSettings.TERMINAL_SCHEME, Optional.of("ocean/high-contrast")));
+        });
+        assertThat(draft.value(CoreSettings.UI_THEME_LIGHT)).isEqualTo("slate-steel");
+        assertThat(draft.modified(CoreSettings.UI_THEME_LIGHT)).as("the staged pick is gone, not replaced").isFalse();
+        assertThat(draft.value(CoreSettings.UI_FONT_SIZE)).as("other staged changes stay").isEqualTo(15f);
+        assertThat(message().text()).isEqualTo("Removed Ocean.");
+        assertThat(title("imported-themes:import-row")).isEqualTo("No imported themes");
+    }
+
+    @Test void removingAThemeNotInUseOnlyDeletesItAndReloads() throws Exception {
+        Files.createDirectories(folder());
+        Files.writeString(folder().resolve("zenburn.json"), ThemeTestSupport.terminalThemeJson("Zenburn"));
+        page("");
+        click("imported-themes:remove-0");
+        assertThat(asked.getFirst().consequence()).isEqualTo("Its file, zenburn.json, is deleted from the themes folder.");
+        assertThat(saved).isEmpty();
+        assertThat(reloads[0]).isEqualTo(1);
+        assertThat(message().text()).isEqualTo("Removed Zenburn.");
+    }
+
+    @Test void aRemovalWhoseSettingsCannotBeSavedSaysSo() throws Exception {
+        Files.createDirectories(folder());
+        Files.writeString(folder().resolve("ocean.json"), ThemeTestSupport.uiThemeJson("Ocean"));
+        page("[ui.theme]\ndark = \"ocean\"\n");
+        failSaves = true;
+        click("imported-themes:remove-0");
+        assertThat(folder().resolve("ocean.json")).doesNotExist();
+        assertThat(message().text()).isEqualTo("Removed Ocean. Its settings were not saved: config.toml has errors");
+        assertThat(message().severity()).contains(Nodes.Severity.DANGER);
+    }
+
+    @Test void aFailedDeleteIsShownAndChangesNothing() throws Exception {
+        Files.createDirectories(folder());
+        Files.writeString(folder().resolve("ocean.json"), ThemeTestSupport.uiThemeJson("Ocean"));
+        page("[ui.theme]\ndark = \"ocean\"\n");
+        Files.delete(folder().resolve("ocean.json"));
+        click("imported-themes:remove-0");
+        assertThat(message().text()).startsWith("Could not remove ocean.json: ");
+        assertThat(message().severity()).contains(Nodes.Severity.DANGER);
+        assertThat(saved).isEmpty();
+        assertThat(draft.modified()).isFalse();
+    }
+}
JASPER_PATCH
`````

- [ ] **Step 2: See them fail**

Run: `./gradlew :jasper-app:compileTestJava`
Expected: FAIL, `cannot find symbol: class ThemeImports` and the six-argument `AppearanceChoices`.

- [ ] **Step 3: Apply the production changes**

`````bash
git apply --whitespace=nowarn <<'JASPER_PATCH'
diff --git a/docs/design/theme-engine/migration.csv b/docs/design/theme-engine/migration.csv
index e7ee8b470d34ec1213195310b402bd7566333027..a2b61c3dfbddf3dc1c06b606ca0f87b72331460e 100644
--- a/docs/design/theme-engine/migration.csv
+++ b/docs/design/theme-engine/migration.csv
@@ -1209,3 +1209,7 @@ jasper-app/src/main/java/dev/jasper/app/appearance/ThemeSelection.java,app.appea
 jasper-app/src/test/java/dev/jasper/app/appearance/ThemeSelectionTest.java,app.appearance,ThemeSelectionTest,nonvisual-helper,none,none,jasper-app/src/test/java/dev/jasper/app/appearance/ThemeSelectionTest.java,New in theme import Plan 1 Task 4; test owner of the theme picks.
 jasper-app/src/main/java/dev/jasper/app/appearance/ThemeFolder.java,app.appearance,ThemeFolder,nonvisual-helper,none,none,jasper-app/src/test/java/dev/jasper/app/appearance/ThemeFolderTest.java,New in theme import Plan 2 Task 1; importing and removing theme files.
 jasper-app/src/test/java/dev/jasper/app/appearance/ThemeFolderTest.java,app.appearance,ThemeFolderTest,nonvisual-helper,none,none,jasper-app/src/test/java/dev/jasper/app/appearance/ThemeFolderTest.java,New in theme import Plan 2 Task 1; test owner of the theme files.
+jasper-app/src/main/java/dev/jasper/app/settingsui/ThemeImports.java,app.settingsui,ThemeImports,nonvisual-helper,none,none,jasper-app/src/test/java/dev/jasper/app/settingsui/ImportedThemesSectionTest.java,New in theme import Plan 2 Task 3; the Imported themes group's application hooks.
+jasper-app/src/main/java/dev/jasper/app/settingsui/ImportedThemesSection.java,app.settingsui,ImportedThemesSection,nonvisual-helper,none,none,jasper-app/src/test/java/dev/jasper/app/settingsui/ImportedThemesSectionTest.java,New in theme import Plan 2 Task 3; kit descriptors for the Imported themes group (Import and Remove).
+jasper-app/src/test/java/dev/jasper/app/settingsui/ImportedThemesSectionTest.java,app.settingsui,ImportedThemesSectionTest,nonvisual-helper,none,none,jasper-app/src/test/java/dev/jasper/app/settingsui/ImportedThemesSectionTest.java,New in theme import Plan 2 Task 3; test owner of the Imported themes group.
+jasper-app/src/main/java/dev/jasper/app/settingsui/package-info.java,app.settingsui,settingsui package,nonvisual-helper,none,none,none-direct,New row in theme import Plan 2 Task 3; the package names its outgoing dependency on dev.jasper.app.appearance.
diff --git a/jasper-app/src/main/java/dev/jasper/app/application/SettingsAppearance.java b/jasper-app/src/main/java/dev/jasper/app/application/SettingsAppearance.java
index f788a7d76d4e997ad7db82ac7ead7374053882fd..1412c2c629484a5724152dd43884233fdff683cf 100644
--- a/jasper-app/src/main/java/dev/jasper/app/application/SettingsAppearance.java
+++ b/jasper-app/src/main/java/dev/jasper/app/application/SettingsAppearance.java
@@ -27,7 +27,7 @@ final class SettingsAppearance {
                 var current = library.get();
                 if (cache.from != current) { cache.themes = themeChoices(current); cache.from = current; }
                 return cache.themes;
-            });
+            }, Optional.empty());
     }
 
     /** What the Appearance page offers from {@code library}: its themes for each appearance and its schemes, with their labels. */
diff --git a/jasper-app/src/main/java/dev/jasper/app/settingsui/AppearanceChoices.java b/jasper-app/src/main/java/dev/jasper/app/settingsui/AppearanceChoices.java
index 6a36de7efc2766707b79c307f922b8cad6bc68ed..4e9cae85a121fe1e55520f55270129e424ae6b21 100644
--- a/jasper-app/src/main/java/dev/jasper/app/settingsui/AppearanceChoices.java
+++ b/jasper-app/src/main/java/dev/jasper/app/settingsui/AppearanceChoices.java
@@ -17,17 +17,19 @@ import java.util.function.Supplier;
  * @param systemDark the operating system's appearance, empty when unavailable
  * @param onChanged runs its argument whenever the operating system's appearance changes
  * @param themes the installed themes and schemes, read again whenever the page refreshes
+ * @param imports the Imported themes group's hooks; empty shows no group
  */
 public record AppearanceChoices(float opacity, boolean blur, Supplier<Optional<Boolean>> systemDark,
-                                Function<Runnable, Subscription> onChanged, Supplier<InstalledThemes> themes) {
+                                Function<Runnable, Subscription> onChanged, Supplier<InstalledThemes> themes,
+                                Optional<ThemeImports> imports) {
     public AppearanceChoices {
         Objects.requireNonNull(systemDark, "systemDark"); Objects.requireNonNull(onChanged, "onChanged");
-        Objects.requireNonNull(themes, "themes");
+        Objects.requireNonNull(themes, "themes"); Objects.requireNonNull(imports, "imports");
     }
 
     /** Fixed previews, Slate Steel alone, an opaque workspace and no system appearance, for pages built without an application. */
     public static AppearanceChoices of(Option.Palette dark, Option.Palette light) {
         var themes = InstalledThemes.slateSteel(dark, light);
-        return new AppearanceChoices(100f, false, Optional::empty, ignored -> new Subscription(() -> { }), () -> themes);
+        return new AppearanceChoices(100f, false, Optional::empty, ignored -> new Subscription(() -> { }), () -> themes, Optional.empty());
     }
 }
diff --git a/jasper-app/src/main/java/dev/jasper/app/settingsui/CorePages.java b/jasper-app/src/main/java/dev/jasper/app/settingsui/CorePages.java
index 9499282d0d4af75fa05036de367f47ec8646d402..922ede53475521da4c4fe94ffd1343dc64799226 100644
--- a/jasper-app/src/main/java/dev/jasper/app/settingsui/CorePages.java
+++ b/jasper-app/src/main/java/dev/jasper/app/settingsui/CorePages.java
@@ -70,26 +70,32 @@ public final class CorePages {
         return specs(appearance, system, shells, navigate, glass, NotificationGroupChoices.NONE);
     }
 
+    /** Settings › Appearance; the Imported themes group follows the Theme section when the application supplies imports. */
+    private static SchemaPage.Spec appearance(AppearanceChoices appearance, boolean glass) {
+        var page = SchemaPage.page("appearance", "Appearance").parent("appearance-behavior").anchor("theme")
+            .section("Theme")
+            .row(UI_APPEARANCE, Editors.appearance(appearance))
+            .row(UI_THEME_DARK).shownWhen(false)
+            .row(UI_THEME_LIGHT).shownWhen(false)
+            .row(TERMINAL_SCHEME, Editors.schemeChoice(appearance)).note("Colors for terminal panes");
+        appearance.imports().ifPresent(imports -> page.part(ImportedThemesSection.part(imports)));
+        return page.section("Workspace")
+            .row(UI_WORKSPACE_OPACITY, WorkspaceEditors.opacity(appearance)).shownWhen(glass)
+            .row(UI_WORKSPACE_BLUR, WorkspaceEditors.blur(appearance)).shownWhen(glass)
+            .section("Interface Font")
+            .row(UI_FONT_FAMILY, Editors.interfaceFont())
+            .row(UI_FONT_SIZE, PlatformFontSize.factory())
+            .section("View mode")
+            .row(WINDOW_VIEW_MODE)
+            .section("Animations")
+            .row(UI_ANIMATIONS_ENABLED);
+    }
+
     /** As above, with the notification groups the Notifications page lists. */
     static List<SchemaPage.Spec> specs(AppearanceChoices appearance, SystemActions system, ShellChoices shells,
                                        java.util.function.Consumer<String> navigate, boolean glass, NotificationGroupChoices groups) {
         return List.of(
-            SchemaPage.page("appearance", "Appearance").parent("appearance-behavior").anchor("theme")
-                .section("Theme")
-                .row(UI_APPEARANCE, Editors.appearance(appearance))
-                .row(UI_THEME_DARK).shownWhen(false)
-                .row(UI_THEME_LIGHT).shownWhen(false)
-                .row(TERMINAL_SCHEME, Editors.schemeChoice(appearance)).note("Colors for terminal panes")
-                .section("Workspace")
-                .row(UI_WORKSPACE_OPACITY, WorkspaceEditors.opacity(appearance)).shownWhen(glass)
-                .row(UI_WORKSPACE_BLUR, WorkspaceEditors.blur(appearance)).shownWhen(glass)
-                .section("Interface Font")
-                .row(UI_FONT_FAMILY, Editors.interfaceFont())
-                .row(UI_FONT_SIZE, PlatformFontSize.factory())
-                .section("View mode")
-                .row(WINDOW_VIEW_MODE)
-                .section("Animations")
-                .row(UI_ANIMATIONS_ENABLED),
+            appearance(appearance, glass),
             SchemaPage.page("window", "Window").parent("appearance-behavior")
                 .section("Toolbar & Status Bar")
                 .row(WINDOW_TOOLBAR, Editors.segments(Map.of("icons_and_labels", "Icons & Labels", "icons", "Icons Only", "hidden", "Hidden")))
diff --git a/jasper-app/src/main/java/dev/jasper/app/settingsui/ImportedThemesSection.java b/jasper-app/src/main/java/dev/jasper/app/settingsui/ImportedThemesSection.java
new file mode 100644
index 0000000000000000000000000000000000000000..38155c767e0eeb301dedf55d7609cf579d4a40a1
--- /dev/null
+++ b/jasper-app/src/main/java/dev/jasper/app/settingsui/ImportedThemesSection.java
@@ -0,0 +1,183 @@
+package dev.jasper.app.settingsui;
+
+import static dev.jasper.app.config.CoreSettings.TERMINAL_SCHEME;
+import static dev.jasper.app.config.CoreSettings.UI_THEME_DARK;
+import static dev.jasper.app.config.CoreSettings.UI_THEME_LIGHT;
+
+import dev.jasper.app.appearance.ThemeFolder;
+import dev.jasper.app.config.SettingsEdit;
+import dev.jasper.app.settings.Setting;
+import dev.jasper.ui.model.ControlState;
+import dev.jasper.ui.model.Nodes;
+import dev.jasper.ui.model.UiEvent;
+import java.io.IOException;
+import java.nio.file.Path;
+import java.util.ArrayList;
+import java.util.LinkedHashMap;
+import java.util.List;
+import java.util.Optional;
+import java.util.OptionalInt;
+import java.util.concurrent.CompletionException;
+
+/**
+ * Settings › Appearance's Imported themes group: Import… and each imported theme with what it offers and Remove.
+ * Import and Remove are file operations that take effect at once; Remove also returns every pick of the removed theme
+ * or its schemes, saved or staged, to its default. A message line under Import… reports the last outcome. EDT only;
+ * the file work runs through {@link ThemeImports}.
+ */
+final class ImportedThemesSection {
+    static final String ID = "imported-themes";
+    private final ThemeImports hooks;
+    private final PageContext context;
+    private final Runnable render;
+    private List<ThemeImports.Imported> entries;
+    private String message = "";
+    private boolean error, busy;
+
+    private ImportedThemesSection(ThemeImports hooks, PageContext context, Runnable render) {
+        this.hooks = hooks;
+        this.context = context;
+        this.render = render;
+        entries = List.copyOf(hooks.imported().get());
+    }
+
+    /** The group as a page part. */
+    static SchemaPage.Part part(ThemeImports hooks) {
+        return new SchemaPage.Part(ID, "Imported themes", (context, render) -> {
+            var section = new ImportedThemesSection(hooks, context, render);
+            return new SchemaPage.Part.Section(section::node, section::event, section::refresh, () -> { });
+        }, List.of("Import theme", "Import…", "Remove theme", "imported themes", "theme file", "Jasper theme"));
+    }
+
+    Nodes.Node node(String id) {
+        var rows = new ArrayList<Nodes.Node>();
+        var importButton = new Nodes.Button(id + ":import", new ControlState(true, !busy, busy, ""), "Import…", "",
+            Nodes.ButtonKind.SECONDARY, id + ":import", "Import a Jasper theme file");
+        rows.add(new Nodes.SettingRow(id + ":import-row", ControlState.READY, entries.isEmpty() ? "No imported themes" : "Jasper theme files",
+            "Convert Alacritty and IntelliJ themes with tools/themes/jasper_theme.py",
+            new Nodes.Stack(id + ":import-controls", ControlState.READY, Nodes.LayoutRole.INLINE, List.of(importButton)), false, false));
+        if (!message.isEmpty())
+            rows.add(new Nodes.Text(id + ":message", ControlState.READY, message, Nodes.TextRole.SUPPORTING,
+                error ? Optional.of(Nodes.Severity.DANGER) : Optional.empty(), OptionalInt.empty()));
+        for (int i = 0; i < entries.size(); i++) {
+            var entry = entries.get(i);
+            var remove = new Nodes.Button(id + ":remove-" + i, new ControlState(true, !busy, false, ""), "Remove", "",
+                Nodes.ButtonKind.SECONDARY, id + ":remove-" + i, "Remove " + entry.label());
+            rows.add(new Nodes.SettingRow(id + ":theme-" + i, ControlState.READY, entry.label(), entry.offers(),
+                new Nodes.Stack(id + ":theme-" + i + ":controls", ControlState.READY, Nodes.LayoutRole.INLINE, List.of(remove)), false, false));
+        }
+        return new Nodes.Card(id + ":card", ControlState.READY, Nodes.CardKind.SETTINGS, rows);
+    }
+
+    boolean event(UiEvent event) {
+        if (!(event instanceof UiEvent.Action action) || busy) return false;
+        if (action.id().equals(ID + ":import")) { chooseAndImport(); return true; }
+        String prefix = ID + ":remove-";
+        if (!action.id().startsWith(prefix)) return false;
+        int index;
+        try { index = Integer.parseInt(action.id().substring(prefix.length())); } catch (NumberFormatException ignored) { return false; }
+        if (index < 0 || index >= entries.size()) return false;
+        askRemove(entries.get(index));
+        return true;
+    }
+
+    void refresh() { entries = List.copyOf(hooks.imported().get()); }
+
+    private void chooseAndImport() {
+        Optional<Path> chosen = hooks.chooseFile().apply(context.surface().get());
+        if (chosen.isEmpty()) return;
+        Path file = chosen.get();
+        String fileName = String.valueOf(file.getFileName());
+        working();
+        hooks.worker().execute(() -> {
+            try {
+                var candidate = hooks.folder().prepare(file);
+                hooks.ui().execute(() -> prepared(fileName, candidate));
+            } catch (ThemeFolder.ImportFailure failure) {
+                hooks.ui().execute(() -> done("Could not import " + fileName + ": " + failure.getMessage(), true));
+            }
+        });
+    }
+
+    private void prepared(String fileName, ThemeFolder.Candidate candidate) {
+        if (!candidate.replaces()) { install(fileName, candidate); return; }
+        done("", false);
+        hooks.confirm().accept(context.surface().get(), new ThemeImports.Question("Replace " + candidate.name(),
+            "Replace " + candidate.name() + "?", "The imported " + candidate.name() + " is replaced by " + fileName + ".", "Replace",
+            () -> install(fileName, candidate)));
+    }
+
+    private void install(String fileName, ThemeFolder.Candidate candidate) {
+        working();
+        hooks.worker().execute(() -> {
+            try {
+                hooks.folder().install(candidate);
+                hooks.ui().execute(() -> reloaded(hooks.reload().get(), "Imported " + candidate.name() + "."));
+            } catch (IOException | RuntimeException failure) {
+                hooks.ui().execute(() -> done("Could not import " + fileName + ": " + failure.getMessage(), true));
+            }
+        });
+    }
+
+    private void askRemove(ThemeImports.Imported entry) {
+        var uses = new ArrayList<String>();
+        if (names(UI_THEME_DARK, context.draft().value(UI_THEME_DARK), entry.id())) uses.add("It is your Dark theme; Slate Steel will be used instead.");
+        if (names(UI_THEME_LIGHT, context.draft().value(UI_THEME_LIGHT), entry.id())) uses.add("It is your Light theme; Slate Steel will be used instead.");
+        if (names(TERMINAL_SCHEME, context.draft().value(TERMINAL_SCHEME), entry.id()))
+            uses.add("Its scheme is your terminal scheme; Same as UI theme will be used instead.");
+        String consequence = "Its file, " + entry.id() + ".json, is deleted from the themes folder."
+            + (uses.isEmpty() ? "" : " " + String.join(" ", uses));
+        hooks.confirm().accept(context.surface().get(), new ThemeImports.Question("Remove " + entry.label(),
+            "Remove " + entry.label() + "?", consequence, "Remove", () -> remove(entry)));
+    }
+
+    private void remove(ThemeImports.Imported entry) {
+        working();
+        hooks.worker().execute(() -> {
+            try {
+                hooks.folder().remove(entry.id());
+                hooks.ui().execute(() -> forget(entry));
+            } catch (IOException | RuntimeException failure) {
+                hooks.ui().execute(() -> done("Could not remove " + entry.id() + ".json: " + failure.getMessage(), true));
+            }
+        });
+    }
+
+    /** Returns every pick of the removed theme or its schemes to its default: staged ones in the draft, saved ones at once. */
+    private void forget(ThemeImports.Imported entry) {
+        var draft = context.draft();
+        var changes = new LinkedHashMap<Setting<?>, Optional<?>>();
+        var expected = new LinkedHashMap<Setting<?>, Optional<?>>();
+        for (Setting<String> setting : List.of(UI_THEME_DARK, UI_THEME_LIGHT, TERMINAL_SCHEME)) {
+            Optional<String> saved = draft.saved(setting);
+            if (saved.map(value -> names(setting, value, entry.id())).orElse(false)) {
+                changes.put(setting, Optional.empty());
+                expected.put(setting, saved);
+            }
+            if (names(setting, draft.value(setting), entry.id())) draft.stage(setting, Optional.empty());
+        }
+        context.changed().run();
+        var written = changes.isEmpty() ? hooks.reload().get() : hooks.save().apply(new SettingsEdit(changes, expected));
+        reloaded(written, "Removed " + entry.label() + ".");
+    }
+
+    private void reloaded(java.util.concurrent.CompletableFuture<?> work, String success) {
+        work.whenComplete((ignored, failure) -> hooks.ui().execute(() -> {
+            refresh();
+            if (failure == null) done(success, false);
+            else done(success + " Its settings were not saved: " + cause(failure).getMessage(), true);
+        }));
+    }
+
+    private static Throwable cause(Throwable failure) {
+        return failure instanceof CompletionException && failure.getCause() != null ? failure.getCause() : failure;
+    }
+
+    private static boolean names(Setting<String> setting, String value, String id) {
+        return setting == TERMINAL_SCHEME ? value.startsWith(id + "/") : value.equals(id);
+    }
+
+    private void working() { busy = true; message = ""; error = false; render.run(); }
+
+    private void done(String text, boolean failed) { busy = false; message = text; error = failed; render.run(); }
+}
diff --git a/jasper-app/src/main/java/dev/jasper/app/settingsui/ThemeImports.java b/jasper-app/src/main/java/dev/jasper/app/settingsui/ThemeImports.java
new file mode 100644
index 0000000000000000000000000000000000000000..fc4e3aa846b8f0a75bc180a8170fbe4e9ad5790d
--- /dev/null
+++ b/jasper-app/src/main/java/dev/jasper/app/settingsui/ThemeImports.java
@@ -0,0 +1,52 @@
+package dev.jasper.app.settingsui;
+
+import dev.jasper.app.appearance.ThemeFolder;
+import dev.jasper.app.config.SettingsEdit;
+import dev.jasper.app.windows.AuxiliarySurface;
+import java.nio.file.Path;
+import java.util.List;
+import java.util.Objects;
+import java.util.Optional;
+import java.util.concurrent.CompletableFuture;
+import java.util.concurrent.Executor;
+import java.util.function.BiConsumer;
+import java.util.function.Function;
+import java.util.function.Supplier;
+
+/**
+ * What Settings › Appearance's Imported themes group needs from the application. The folder's blocking file work runs
+ * on {@code worker}; every result comes back through {@code ui}, which runs it on the EDT, where the other hooks run.
+ *
+ * @param folder the themes folder, which checks, copies in and deletes theme files
+ * @param imported the imported themes now, in the dropdowns' order
+ * @param chooseFile asks for a {@code .json} file over the Settings dialog's surface (null while it is closed); empty when cancelled
+ * @param confirm asks a question over the Settings dialog's surface and runs its answer when the user agrees
+ * @param save writes settings at once, outside Apply
+ * @param reload reads config.toml and the themes folder again
+ * @param worker runs the blocking file work
+ * @param ui runs a result on the EDT
+ */
+public record ThemeImports(ThemeFolder folder, Supplier<List<Imported>> imported,
+                           Function<AuxiliarySurface, Optional<Path>> chooseFile, BiConsumer<AuxiliarySurface, Question> confirm,
+                           Function<SettingsEdit, CompletableFuture<?>> save, Supplier<CompletableFuture<?>> reload,
+                           Executor worker, Executor ui) {
+    public ThemeImports {
+        Objects.requireNonNull(folder, "folder"); Objects.requireNonNull(imported, "imported");
+        Objects.requireNonNull(chooseFile, "chooseFile"); Objects.requireNonNull(confirm, "confirm");
+        Objects.requireNonNull(save, "save"); Objects.requireNonNull(reload, "reload");
+        Objects.requireNonNull(worker, "worker"); Objects.requireNonNull(ui, "ui");
+    }
+
+    /** An imported theme as the group lists it: its id, its label and what it offers, such as "UI theme · Dark". */
+    public record Imported(String id, String label, String offers) {
+        public Imported { Objects.requireNonNull(id, "id"); Objects.requireNonNull(label, "label"); Objects.requireNonNull(offers, "offers"); }
+    }
+
+    /** A question with one consequence and a yes: the dialog's title, the question, what happens, and the yes button's label. */
+    public record Question(String title, String question, String consequence, String yes, Runnable onYes) {
+        public Question {
+            Objects.requireNonNull(title, "title"); Objects.requireNonNull(question, "question");
+            Objects.requireNonNull(consequence, "consequence"); Objects.requireNonNull(yes, "yes"); Objects.requireNonNull(onYes, "onYes");
+        }
+    }
+}
diff --git a/jasper-app/src/main/java/dev/jasper/app/settingsui/package-info.java b/jasper-app/src/main/java/dev/jasper/app/settingsui/package-info.java
index ae165d34b7358e38360c292ea45335e659df555f..aef93ae7b14c838f9825a05ceed045376c2aa22b 100644
--- a/jasper-app/src/main/java/dev/jasper/app/settingsui/package-info.java
+++ b/jasper-app/src/main/java/dev/jasper/app/settingsui/package-info.java
@@ -2,7 +2,7 @@
  * The Settings dialog: its shell, the pending edits of core settings, schema-generated and group pages, editors
  * and the core page tree. EDT only; SettingsDialog owns the pages it creates and disposes them when it closes.
  * <p>Allowed outgoing Jasper dependencies: dev.jasper.app.settings, dev.jasper.app.config, dev.jasper.app.windows,
- * dev.jasper.app.platform, dev.jasper.app.lifecycle.
+ * dev.jasper.app.platform, dev.jasper.app.lifecycle, dev.jasper.app.appearance (the themes folder's files).
  * Controls are jasper-ui kit descriptors (dev.jasper.ui.model, dev.jasper.ui.swing) mounted with the dialog's chrome KitContext.
  * The module architecture check forbids package cycles; app types are not an external plugin API.
  */
JASPER_PATCH
`````

- [ ] **Step 4: Run the tests**

Run: `./gradlew :jasper-app:test --tests "dev.jasper.app.settingsui.*" --tests "*ThemeMigrationCoverageTest"`
Expected: PASS (ImportedThemesSectionTest: 8 tests).

- [ ] **Step 5: Commit**

```bash
git add -A
git commit -m "Import and remove themes in Settings' Imported themes group

🤖 Generated with Claude Code at The Home Depot

Co-Authored-By: Claude <noreply@anthropic.com>"
```

### Task 4: Import and Remove in the application

**What changes:**
- `pluginmanager.ConfirmView` becomes public (constructor and `view()`).
- `SettingsAppearance`:
  - `imports(...)` builds the hooks: the worker is a daemon thread per job, `ui` is `SwingUtilities::invokeLater`, and
    the imported list comes from the live library.
  - `imported(library)` and `offers(theme)` give the list's text: "UI theme · Dark", "UI theme · Dark and Light, 3 terminal
    schemes", "Terminal scheme", "Terminal schemes".
  - `choices(system, library, imports)` passes the hooks on.
- `JasperApplication`:
  - It keeps the themes folder and, when there is one, supplies the hooks: Jasper's chooser with
    `shells.chooseFile(surface, "Import Theme", ".json")`, `confirmOver` (a `ConfirmView` sheet over the Settings
    dialog whose yes runs once), `configuration::save` and `configuration::reload`.

**Files:** modify `pluginmanager/ConfirmView.java`, `application/{SettingsAppearance,JasperApplication}.java`; create
`application/SettingsAppearanceTest.java`; modify `migration.csv`.

**Interfaces:**
- Consumes: Task 3's `ThemeImports`; Plan 1's `ThemeLibrary`, `ThemeController.library()`.
- Produces: `SettingsAppearance.choices(SystemAppearance, Supplier<ThemeLibrary>, Optional<ThemeImports>)`,
  `imports(ThemeFolder, Supplier<ThemeLibrary>, Function<AuxiliarySurface, Optional<Path>>, BiConsumer<AuxiliarySurface, ThemeImports.Question>, Function<SettingsEdit, CompletableFuture<?>>, Supplier<CompletableFuture<?>>)`,
  `imported(ThemeLibrary)`, `offers(JasperTheme)` (all package-private).

- [ ] **Step 1: Apply the test**

`````bash
git apply --whitespace=nowarn <<'JASPER_PATCH'
diff --git a/jasper-app/src/test/java/dev/jasper/app/application/SettingsAppearanceTest.java b/jasper-app/src/test/java/dev/jasper/app/application/SettingsAppearanceTest.java
new file mode 100644
index 0000000000000000000000000000000000000000..566d5a97c64a4303f821b7d570e4ed72ae6a2f51
--- /dev/null
+++ b/jasper-app/src/test/java/dev/jasper/app/application/SettingsAppearanceTest.java
@@ -0,0 +1,30 @@
+package dev.jasper.app.application;
+
+import static org.assertj.core.api.Assertions.*;
+
+import dev.jasper.app.appearance.ThemeLibrary;
+import dev.jasper.app.appearance.ThemeTestSupport;
+import dev.jasper.app.settingsui.ThemeImports;
+import java.nio.file.Files;
+import java.nio.file.Path;
+import org.junit.jupiter.api.Test;
+import org.junit.jupiter.api.io.TempDir;
+
+/** spec 4.1: the Imported themes group lists each imported theme with what it offers. */
+class SettingsAppearanceTest {
+    @TempDir Path home;
+
+    @Test void eachImportedThemeSaysWhatItOffers() throws Exception {
+        var themes = Files.createDirectories(home.resolve("themes"));
+        Files.writeString(themes.resolve("ocean.json"), ThemeTestSupport.uiThemeJson("Ocean"));
+        Files.writeString(themes.resolve("dusk.json"), ThemeTestSupport.uiThemeJson("Dusk")
+            .replaceAll("(?s),\\s*\"(light|high-contrast)\": \\{[^}]*}", ""));
+        Files.writeString(themes.resolve("zenburn.json"), ThemeTestSupport.terminalThemeJson("Zenburn"));
+        var library = ThemeLibrary.read(themes, ThemeLibrary.bundledOnly());
+        assertThat(library.problems()).isEmpty();
+        assertThat(SettingsAppearance.imported(library)).containsExactly(
+            new ThemeImports.Imported("dusk", "Dusk", "UI theme · Dark"),
+            new ThemeImports.Imported("ocean", "Ocean", "UI theme · Dark and Light, 3 terminal schemes"),
+            new ThemeImports.Imported("zenburn", "Zenburn", "Terminal scheme"));
+    }
+}
JASPER_PATCH
`````

- [ ] **Step 2: See it fail**

Run: `./gradlew :jasper-app:compileTestJava`
Expected: FAIL, `cannot find symbol: method imported(ThemeLibrary)`.

- [ ] **Step 3: Apply the production changes**

`````bash
git apply --whitespace=nowarn <<'JASPER_PATCH'
diff --git a/docs/design/theme-engine/migration.csv b/docs/design/theme-engine/migration.csv
index a2b61c3dfbddf3dc1c06b606ca0f87b72331460e..7e7b90bc365e0cd7f70ab9a55ba4546db701e6cd 100644
--- a/docs/design/theme-engine/migration.csv
+++ b/docs/design/theme-engine/migration.csv
@@ -1213,3 +1213,4 @@ jasper-app/src/main/java/dev/jasper/app/settingsui/ThemeImports.java,app.setting
 jasper-app/src/main/java/dev/jasper/app/settingsui/ImportedThemesSection.java,app.settingsui,ImportedThemesSection,nonvisual-helper,none,none,jasper-app/src/test/java/dev/jasper/app/settingsui/ImportedThemesSectionTest.java,New in theme import Plan 2 Task 3; kit descriptors for the Imported themes group (Import and Remove).
 jasper-app/src/test/java/dev/jasper/app/settingsui/ImportedThemesSectionTest.java,app.settingsui,ImportedThemesSectionTest,nonvisual-helper,none,none,jasper-app/src/test/java/dev/jasper/app/settingsui/ImportedThemesSectionTest.java,New in theme import Plan 2 Task 3; test owner of the Imported themes group.
 jasper-app/src/main/java/dev/jasper/app/settingsui/package-info.java,app.settingsui,settingsui package,nonvisual-helper,none,none,none-direct,New row in theme import Plan 2 Task 3; the package names its outgoing dependency on dev.jasper.app.appearance.
+jasper-app/src/test/java/dev/jasper/app/application/SettingsAppearanceTest.java,app.application,SettingsAppearanceTest,nonvisual-helper,none,none,jasper-app/src/test/java/dev/jasper/app/application/SettingsAppearanceTest.java,New in theme import Plan 2 Task 4; test owner of what imported themes offer.
diff --git a/jasper-app/src/main/java/dev/jasper/app/application/JasperApplication.java b/jasper-app/src/main/java/dev/jasper/app/application/JasperApplication.java
index 705179d7aa1ffec1982d7671f0cbdc1f192f2826..bfd4c942b1881f29b6df724deedd51c0d805ff5d 100644
--- a/jasper-app/src/main/java/dev/jasper/app/application/JasperApplication.java
+++ b/jasper-app/src/main/java/dev/jasper/app/application/JasperApplication.java
@@ -131,6 +131,8 @@ public final class JasperApplication {
     static final Duration DETECTION_WAIT = Duration.ofSeconds(12);
     private final ApplicationShutdown shutdown;
     private final Path shellIntegrationDir;
+    /** The imported themes' folder, or null for the built-in theme alone. */
+    private final Path themesFolder;
     private final BuddyIntegration buddy;
     private final dev.jasper.app.lifecycle.Subscription buddyAppearance;
     private final SystemNotices systemNotices;
@@ -250,6 +252,7 @@ public final class JasperApplication {
         // The exit thread runs this after the bounded cleanup wait, so a replacement never meets this process's endpoint.
         this.shutdown = new ApplicationShutdown(() -> { relaunchIfRequested(); terminate.run(); });
         this.shellIntegrationDir = shellIntegrationDir;
+        this.themesFolder = themesFolder;
         buddy = new BuddyIntegration(buddyStateFile, SystemFonts.system(java.awt.Font.PLAIN, 13f),
             themes.current().chrome().dark(), this::raiseTerminal, this::toggleBuddy, this::owns);
         buddyAppearance = themes.subscribe((theme, changed) -> buddy.appearance(SystemFonts.system(java.awt.Font.PLAIN, 13f), theme.chrome().dark()));
@@ -798,7 +801,11 @@ public final class JasperApplication {
             var shells = new dev.jasper.app.settingsui.ShellChoices(() -> shellProfiles.current().stream()
                     .map(profile -> new dev.jasper.app.settingsui.ShellChoices.Choice(profile.id(), profile.label())).toList(),
                 shellProfiles::refresh, shellProfiles::onChanged);
-            var appearanceChoices = SettingsAppearance.choices(systemAppearance, themes::library);
+            Optional<dev.jasper.app.settingsui.ThemeImports> imports = themesFolder == null ? Optional.empty()
+                : Optional.of(SettingsAppearance.imports(new dev.jasper.app.appearance.ThemeFolder(themesFolder), themes::library,
+                    surface -> surface == null ? Optional.empty() : this.shells.chooseFile(surface, "Import Theme", ".json"),
+                    this::confirmOver, configuration::save, configuration::reload));
+            var appearanceChoices = SettingsAppearance.choices(systemAppearance, themes::library, imports);
             settings = new dev.jasper.app.settingsui.SettingsDialog(auxiliary,
                 dev.jasper.app.settingsui.CorePages.pages(appearanceChoices, system, shells,
                     new dev.jasper.app.settingsui.NotificationGroupChoices(() -> groupChoices(notificationCenter.groups(), this::pluginName, plugins == null ? Map.of() : plugins.pluginIcons())),
@@ -1294,6 +1301,23 @@ public final class JasperApplication {
 
     Incidents incidents() { return incidents; }
 
+    /** Asks {@code question} with a kit sheet over {@code surface}, the Settings dialog; its yes runs at most once. */
+    private void confirmOver(dev.jasper.app.windows.AuxiliarySurface surface, dev.jasper.app.settingsui.ThemeImports.Question question) {
+        if (surface == null || auxiliary == null) return;
+        var dialog = auxiliary.dialog(question.title(), true, surface);
+        boolean[] answered = {false};
+        var view = new dev.jasper.app.pluginmanager.ConfirmView(themes.appearance().context(dev.jasper.ui.theme.ThemeRoute.CHROME),
+            question.question(), question.consequence(), question.yes(), () -> {
+                if (answered[0]) return;
+                answered[0] = true;
+                dialog.close();
+                question.onYes().run();
+            }, dialog::close);
+        dialog.onClosed(view::close);
+        dialog.setContent(view.view());
+        dialog.show();
+    }
+
     /** The Plugins page's controller, created the first time Settings builds that page. */
     private PluginManager pluginManager() {
         if (pluginManager == null) {
diff --git a/jasper-app/src/main/java/dev/jasper/app/application/SettingsAppearance.java b/jasper-app/src/main/java/dev/jasper/app/application/SettingsAppearance.java
index 1412c2c629484a5724152dd43884233fdff683cf..bcc27ef07dad2c144cf606ec5d2b41bd7a44e3f0 100644
--- a/jasper-app/src/main/java/dev/jasper/app/application/SettingsAppearance.java
+++ b/jasper-app/src/main/java/dev/jasper/app/application/SettingsAppearance.java
@@ -1,21 +1,32 @@
 package dev.jasper.app.application;
 
+import dev.jasper.app.appearance.ThemeFolder;
+import dev.jasper.app.config.SettingsEdit;
 import dev.jasper.app.lifecycle.Subscription;
 import dev.jasper.app.platform.SystemAppearance;
 import dev.jasper.app.appearance.ThemeLibrary;
 import dev.jasper.app.settingsui.AppearanceChoices;
 import dev.jasper.app.settingsui.InstalledThemes;
+import dev.jasper.app.settingsui.ThemeImports;
+import dev.jasper.app.windows.AuxiliarySurface;
 import dev.jasper.ui.model.Option;
 import dev.jasper.ui.theme.SemanticColor;
 import dev.jasper.ui.theme.JasperTheme;
+import java.nio.file.Path;
+import java.util.List;
 import java.util.Optional;
+import java.util.concurrent.CompletableFuture;
+import java.util.function.BiConsumer;
+import java.util.function.Function;
+import java.util.function.Supplier;
+import javax.swing.SwingUtilities;
 
 /** The Appearance page's values: the installed themes with their previews, the workspace glass and the live OS appearance. */
 final class SettingsAppearance {
     private SettingsAppearance() { }
 
-    /** The choices over {@code library}, the installed themes as they are whenever the page reads them. */
-    static AppearanceChoices choices(SystemAppearance system, java.util.function.Supplier<ThemeLibrary> library) {
+    /** The choices over {@code library}, the installed themes as they are whenever the page reads them; {@code imports} adds the Imported themes group. */
+    static AppearanceChoices choices(SystemAppearance system, Supplier<ThemeLibrary> library, Optional<ThemeImports> imports) {
         var glass = JasperTheme.bundled().generated(true).glass();
         var cache = new Object() { ThemeLibrary from; InstalledThemes themes; };
         return new AppearanceChoices((float) (glass.opacity() * 100), glass.blur(),
@@ -27,7 +38,35 @@ final class SettingsAppearance {
                 var current = library.get();
                 if (cache.from != current) { cache.themes = themeChoices(current); cache.from = current; }
                 return cache.themes;
-            }, Optional.empty());
+            }, imports);
+    }
+
+    /**
+     * The Imported themes group's hooks: the folder's file work on its own daemon thread, results on the EDT, the
+     * imported themes from {@code library} as it is when the group reads them.
+     */
+    static ThemeImports imports(ThemeFolder folder, Supplier<ThemeLibrary> library, Function<AuxiliarySurface, Optional<Path>> chooseFile,
+                                BiConsumer<AuxiliarySurface, ThemeImports.Question> confirm, Function<SettingsEdit, CompletableFuture<?>> save,
+                                Supplier<CompletableFuture<?>> reload) {
+        return new ThemeImports(folder, () -> imported(library.get()), chooseFile, confirm, save, reload,
+            work -> Thread.ofPlatform().daemon().name("jasper-theme-files").start(work), SwingUtilities::invokeLater);
+    }
+
+    /** The library's imported themes as the group lists them. */
+    static List<ThemeImports.Imported> imported(ThemeLibrary library) {
+        return library.imported().stream().map(theme -> new ThemeImports.Imported(theme.id(), library.label(theme), offers(theme))).toList();
+    }
+
+    /**
+     * What a theme offers: "UI theme · Dark", "UI theme · Light" or "UI theme · Dark and Light", with ", N terminal
+     * schemes" when it has schemes beyond its variants' own; a terminal-only theme is "Terminal scheme" or "Terminal schemes".
+     */
+    static String offers(JasperTheme theme) {
+        int schemes = theme.schemes().size();
+        if (!theme.has(true) && !theme.has(false)) return schemes == 1 ? "Terminal scheme" : "Terminal schemes";
+        String variants = theme.has(true) && theme.has(false) ? "Dark and Light" : theme.has(true) ? "Dark" : "Light";
+        int own = (theme.has(true) ? 1 : 0) + (theme.has(false) ? 1 : 0);
+        return "UI theme \u00b7 " + variants + (schemes > own ? ", " + schemes + " terminal schemes" : "");
     }
 
     /** What the Appearance page offers from {@code library}: its themes for each appearance and its schemes, with their labels. */
diff --git a/jasper-app/src/main/java/dev/jasper/app/pluginmanager/ConfirmView.java b/jasper-app/src/main/java/dev/jasper/app/pluginmanager/ConfirmView.java
index 5613d565db2f52ce76331c2b74e780aa112715bd..cd311d198b3e67cb50310c747b564c14cc5b4b4b 100644
--- a/jasper-app/src/main/java/dev/jasper/app/pluginmanager/ConfirmView.java
+++ b/jasper-app/src/main/java/dev/jasper/app/pluginmanager/ConfirmView.java
@@ -12,14 +12,14 @@ import java.util.Optional;
 /**
  * A question with one consequence and two answers, for a removal: a kit sheet whose primary answers yes (Enter) and
  * whose Cancel answers no (Escape). Kit text is literal, so plugin-supplied text is never markup. EDT only; it closes
- * with its dialog.
+ * with its dialog. Settings' Imported themes group asks its questions with it too.
  */
-final class ConfirmView implements AutoCloseable {
+public final class ConfirmView implements AutoCloseable {
     static final String OK = "ok", CANCEL = "cancel";
     private final KitView view;
     private final String question, consequence, okLabel;
 
-    ConfirmView(KitContext kit, String question, String consequence, String okLabel, Runnable onOk, Runnable onCancel) {
+    public ConfirmView(KitContext kit, String question, String consequence, String okLabel, Runnable onOk, Runnable onCancel) {
         this.question = question;
         this.consequence = consequence;
         this.okLabel = okLabel;
@@ -40,7 +40,7 @@ final class ConfirmView implements AutoCloseable {
     /** Shows the answer as busy (it cannot be given again) while the operation runs. */
     void busy(boolean value) { if (!view.isClosed()) view.update(spec(value)); }
 
-    KitView view() { return view; }
+    public KitView view() { return view; }
 
     /** The question and its consequence. */
     String text() { return question + "\n\n" + consequence; }
JASPER_PATCH
`````

- [ ] **Step 4: Run the tests**

Run: `./gradlew :jasper-app:test --tests "*SettingsAppearanceTest" --tests "*JasperApplicationThemesTest" --tests "*PluginManager*" --tests "*JasperApplicationPluginsTest" --tests "*ThemeMigrationCoverageTest"`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add -A
git commit -m "Wire theme Import and Remove into the application

🤖 Generated with Claude Code at The Home Depot

Co-Authored-By: Claude <noreply@anthropic.com>"
```

### Task 5: Documentation and verification

**What changes:** `docs/themes.md` gains "Importing and removing themes" (before "The Jasper theme format") and says
Settings imports into the themes folder; `docs/app-maintenance.md`'s Appearance section describes the group.

**Files:** `docs/themes.md`, `docs/app-maintenance.md`, `docs/STATUS.md`.

- [ ] **Step 1: Apply the documentation**

`````bash
git apply --whitespace=nowarn <<'JASPER_PATCH'
diff --git a/docs/app-maintenance.md b/docs/app-maintenance.md
index d6e088a7b0bb41e066232b32f09a9980c16d22c6..6a02f255b2f60376a2832aa0faddfb45544e7162 100644
--- a/docs/app-maintenance.md
+++ b/docs/app-maintenance.md
@@ -652,7 +652,10 @@ installed scheme, noted "Colors for terminal panes". A saved pick that is not in
 installed)". `AppearanceChoices` carries `InstalledThemes` (the theme options with previews and the schemes, read again
 on every refresh), the workspace glass defaults and the system-appearance observation from application wiring
 (`application.SettingsAppearance`, over the controller's live library), so settingsui reads jasper-ui only through
-`ui.model` and `ui.swing`. The Match System tile names the observed appearance, or says it is unavailable
+`ui.model` and `ui.swing`. When the application supplies `ThemeImports`, the Imported themes group follows the Theme
+section as a `SchemaPage.Part` (`settingsui.ImportedThemesSection`): Import… and Remove run `appearance.ThemeFolder`'s
+file work on a worker, come back on the EDT and reload the configuration, and Remove returns the removed theme's saved
+and staged picks to their defaults; the application asks its questions with `pluginmanager.ConfirmView`. The Match System tile names the observed appearance, or says it is unavailable
 and Dark is used. Terminal › Font & Colors links to the section.
 
 The command palette's `view.terminal_scheme.<name>` commands save `terminal.scheme` for every window
diff --git a/docs/themes.md b/docs/themes.md
index 0c763fa05ec3ef24dea1726ff9e4f42c613bb0cd..07128181246c46b259a70960833c75c527ccee6a 100644
--- a/docs/themes.md
+++ b/docs/themes.md
@@ -37,8 +37,8 @@ opens it; the palette's **Terminal: …** commands choose a scheme. Choices stay
 
 ## Installed themes
 
-The themes folder is `themes/` in Jasper's home, next to `config.toml`. It holds one Jasper theme file per theme,
-named after the theme's id: lowercase letters and digits in dash-separated words, at most 64 characters, so
+The themes folder is `themes/` in Jasper's home, next to `config.toml`; Settings imports into it (below) and files can
+be copied in by hand. It holds one Jasper theme file per theme, named after the theme's id: lowercase letters and digits in dash-separated words, at most 64 characters, so
 `dracula.json` is the theme `dracula`. Jasper reads the folder at start-up, on Reload config, when Settings saves and
 when `config.toml` changes; a theme changed on disk is shown again then. `slate-steel` is the built-in theme's id, so a
 `slate-steel.json` in the folder is ignored. Dropdowns list Slate Steel first, then the installed themes by name; two
@@ -52,6 +52,20 @@ text area and the active tab meet seamlessly; with High Contrast the text area i
 terminal surface. Workspace opacity and blur apply to the terminal area. The editor always follows the shown theme's
 editor scheme.
 
+## Importing and removing themes
+
+Settings › Appearance › **Imported themes** lists every imported theme with what it offers ("UI theme · Dark",
+"Terminal scheme", …) and a **Remove** button, under an **Import…** button. Both act at once, outside Apply.
+
+- **Import…** opens Jasper's file chooser for a `.json` file, checks it and copies it into the themes folder as
+  `<id>.json`, the id coming from the theme's name ("Gruvbox Dark" is `gruvbox-dark`). Its variants and schemes then
+  appear in the dropdowns; nothing is selected. A file Jasper cannot use is not copied, and the reason shows under the
+  button, for example `Could not import dracula.json: ui.dark: Missing colour "accent"`. Importing a theme whose id an
+  imported theme already has asks before replacing it; Slate Steel cannot be replaced.
+- **Remove** asks first and says what will change. The file is deleted, and every setting that names the theme or one
+  of its schemes, saved or staged, returns to its default (`slate-steel` or `match`): saved ones are written at once,
+  and other staged changes stay staged. If the file cannot be deleted, nothing changes and the reason shows.
+
 ## The Jasper theme format
 
 A Jasper theme is one UTF-8 JSON file; `slate-steel.json` is the complete example.
JASPER_PATCH
`````

- [ ] **Step 2: Run the full check**

Run: `./gradlew check --continue`
Expected: `BUILD SUCCESSFUL`. Count the tests from the XML:

```bash
python3 - <<'PY'
import glob, xml.etree.ElementTree as E
t = f = e = s = 0
for x in glob.glob("*/build/test-results/test/*.xml") + glob.glob("plugins/*/build/test-results/test/*.xml"):
    r = E.parse(x).getroot(); t += int(r.get("tests")); f += int(r.get("failures")); e += int(r.get("errors")); s += int(r.get("skipped"))
print(t, "tests,", f, "failures,", e, "errors,", s, "skipped")
PY
```

- [ ] **Step 3: Record the work in `docs/STATUS.md`**

Add at the top, below `# Jasper — Status and Handoff`, with Step 2's numbers:

```markdown
**Theme import Plan 2: Import… and Remove — 2026-10-13** (`claude/theme-import`;
[spec](superpowers/specs/2026-10-13-jasper-theme-import-design.md),
[plan](superpowers/plans/2026-10-13-theme-import-2-import-and-remove.md)). Settings › Appearance › Imported themes
lists each imported theme with what it offers. Import… (Jasper's chooser, `.json`) checks a theme file, names it after
the theme (`gruvbox-dark.json`), asks before replacing one and copies it into `<home>/themes/` through a temporary file;
failures show under the button. Remove asks, deletes the file, and returns the theme's saved picks (written at once)
and staged ones to their defaults. `appearance.ThemeFolder` does the file work; `SchemaPage.Part` is a new, generic
page section; the application asks with `pluginmanager.ConfirmView`. `./gradlew check --continue`: **N tests,
0 failures, 0 errors, S skipped**. No GUI launched. GUI checks for the user: Import… with the real chooser, the Replace
and Remove sheets over Settings, and the dropdowns after each. Not merged or pushed.
```

- [ ] **Step 4: Commit**

```bash
git add -A
git commit -m "Document importing and removing themes

🤖 Generated with Claude Code at The Home Depot

Co-Authored-By: Claude <noreply@anthropic.com>"
```
