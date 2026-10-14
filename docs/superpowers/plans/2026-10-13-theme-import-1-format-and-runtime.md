# Theme import Plan 1: the format and the runtime — Implementation Plan

> **Status (2026-10-13): executed on `claude/theme-import` (e76bd6ab..6a49da4a).** Review fixes beyond this plan's
> text: a prerequisite commit makes AppDocumentationTest strip fenced code the CommonMark way (a fence closes only on
> a line of at least as many backticks) and check example snippets in the current guides only; `JasperTheme` checks
> the `jasper-theme` marker before it reports unknown entries; `ThemeJson` refuses objects nested more than 8 deep;
> `ThemeLibrary` says why a file could not be read and survives a failed folder listing; a Settings row with
> companion settings stages only the settings an edit changed (`SchemaPage.edited`), so picking a theme no longer
> writes `appearance`; README.md, docs/README.md, docs/ui-architecture.md, docs/sdk-architecture.md and
> docs/plugin-ui.md no longer name Slate Steel as the only theme. The final whole-branch review's fixes are in
> docs/STATUS.md.

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

> **Deviation from the spec's section 8 (recorded here and in `docs/STATUS.md`):** the spec puts the Dark theme and
> Light theme dropdowns and the dynamic Terminal scheme list in Plan 2. They are in this plan (Tasks 3 and 5) because
> `terminal.scheme` stops being a fixed choice in Task 3, which breaks the fixed dropdown, and a theme installed by hand
> should be choosable in Settings as soon as Jasper can show it. Plan 2 is then Import… and Remove only.

**Goal:** One public Jasper theme format, read by one loader (Slate Steel moves onto it), and a runtime that shows
any theme installed in `<home>/themes/`: separate Dark and Light theme picks, scheme ids for `terminal.scheme`, the
Appearance page's dropdowns and the palette's "Terminal: …" commands over the installed schemes.

**Architecture:** In jasper-ui, `SlateSteel` becomes the general `JasperTheme` loader (variants, schemes with ids,
literal editor colours) and the `TerminalScheme` enum becomes a record. In the app, `ThemeLibrary` reads the themes
folder, `ThemeSelection` turns the three settings into themes the library has (reporting and replacing any it lacks),
`ThemeController` shows a selection, and `ConfigurationController` rescans the folder through
`ConfigService.watchWith` before every forced or changed configuration state. Settings and the command palette read the
controller's live library.

**Tech Stack:** Java 25 (JetBrains Runtime), Swing/FlatLaf, Gradle 9.7, JUnit 6.1.3, AssertJ 3.27.7.

**Spec:** `docs/superpowers/specs/2026-10-13-jasper-theme-import-design.md` (sections 2, 3, 4.1, 4.4, 6.3 and 7).

**Workspace:** worktree `~/projects/moray/.claude/worktrees/theme-import`, branch `claude/theme-import` (spec commit
`bc27aed7` on `main` `38557864`). Run every command from the worktree root. Never build in `~/projects/moray` itself
(the user runs the app there).

**Verified:** every task below was carried out before this plan was written, in this order, on a scratch branch from
`bc27aed7`, and each task's patches are that branch's commits split into test and production halves. After Task 6,
`./gradlew check --continue` passed: **9,794 tests, 0 failures, 0 errors, 8 skipped** (Task 2's byte-order-mark case
was added after that run and its tests passed). The patches are made with `git diff --full-index`, so `git apply`
reports any file that differs from this plan's starting point instead of guessing.

## How to apply a patch step

Each "Apply" step is one command: paste the block into a shell at the worktree root. If `git apply` reports a failure,
stop: the tree is not the one this plan starts from; compare with the named commit before going on. Patches create,
delete and modify files; do not edit those files by hand before the step that patches them.

## Global Constraints

- The format (spec section 2): `"jasper-theme": 1`; `name` required, `author` optional; `ui` with `dark` and/or `light`
  listing every non-ruled role; `terminal` with at least one scheme, each `name` plus exactly the 21 colours; `editor`
  with exactly `ui`'s variants; `ui.<v>` needs `terminal.<v>`; editor values are `#RRGGBB` or references, with an
  optional `@ N%` share and `, italic` / `, bold` styles.
- Ids: a theme id matches `[a-z0-9]+(-[a-z0-9]+)*`, at most 64 characters, and is the file name without `.json`;
  `slate-steel` is the built-in id and reserved; a scheme key matches the same pattern, at most 32 characters; a scheme
  id is `<theme-id>/<scheme-key>`.
- Settings: `ui.theme.dark` and `ui.theme.light` default to `slate-steel`; `terminal.scheme` is `match` (default) or a
  scheme id. Invalid text reports "Use a theme id such as \"slate-steel\"; using the default." or
  "Use \"match\" or a scheme id such as \"slate-steel/high-contrast\"; using the default."
- Hex colour literals live only in `slate-steel.json` among production code and resources (`ColourLiteralScanTest`).
- Every new or changed `jasper-app` / `plugins` Java file that `ThemeMigrationCoverageTest` matches needs its
  `docs/design/theme-engine/migration.csv` row (the patches add them); new type names avoid `RemovedThemeLayerTest`'s
  list and the CSV's "Removed" rows (so no `settingsui/ThemeChoices.java` or `settingsui/ThemeSelection.java`).
- jasper-ui's `dev.jasper.ui.theme` stays JDK-only; the theme files are parsed off the EDT in the app's configuration
  worker and only published on it.
- Jasper is an MVP: old values are not migrated (`slate-steel-dark` and friends are simply invalid now).
- Source hygiene (AGENTS.md): no raw control, private-use or unpaired surrogate characters in Java sources.
- No GUI launch, no AOT training; commit on `claude/theme-import` only; each commit message ends with:

  ```
  🤖 Generated with Claude Code at The Home Depot

  Co-Authored-By: Claude <noreply@anthropic.com>
  ```

## Review Focus

1. A theme file saved by an editor that writes a UTF-8 byte-order mark loads like any other (Task 2,
   `ThemeLibraryTest.aFileThatCannotBeUsedIsSkippedAndReported`, the `marked.json` case).
2. A theme the user deletes by hand while it is their saved Dark pick: Slate Steel is shown and the pick is reported,
   not silently rewritten (Task 4, `ConfigurationControllerTest.aSavedImportedThemeShowsAndAnUnknownOneIsReported`).
3. Two installed themes with the same name: dropdowns tell them apart by id (Task 2,
   `ThemeLibraryTest.importedThemesFollowSlateSteelByNameThenId`).
4. A dark-only theme picked for Dark under Match System: Light shows Slate Steel, and a library refresh that leaves the
   theme unchanged never reinstalls the look and feel (Task 4, `ThemeSelectionTest`).
5. A saved pick that is not installed opens in Settings as "<id> (not installed)" and Apply keeps it saved unless the
   user changes it (Task 5, `AppearanceSelectionTest.aSavedPickIsKeptAsSavedAndOneThatIsNotInstalledIsShownAsSuch`).

---

### Task 1: The Jasper theme format and loader

**What changes:**
- `slate-steel.json` (and the identical block in `docs/design/theme-engine/slate-steel.md`) moves onto the format:
  `"jasper-theme": 1, "name": "Slate Steel", "author": "Jasper"`, variants keyed `dark` / `light`, schemes `dark`,
  `light`, `high-contrast` with names. Every colour value is unchanged.
- `ThemeJson` reads whole numbers (the marker); `ThemeFileException.detail()` is the message without the file.
- `SlateSteel` is replaced by `JasperTheme` (any theme file; missing variants allowed; literal editor colours);
  `SchemeColors` and the `TerminalScheme` enum are replaced by the `TerminalScheme` record and `SchemeId`.
- Every caller moves over with behaviour unchanged; `ThemeController.scheme` maps the old `TerminalSchemeChoice`
  enum onto Slate Steel's schemes until Task 3. 29 migration-CSV authority rows point at `JasperTheme.java`.

**Files:** see the patches; production: `jasper-ui/src/main/java/dev/jasper/ui/theme/{JasperTheme,SchemeId,TerminalScheme,ThemeJson,ThemeFileException,package-info}.java`
(`SlateSteel.java`, `SchemeColors.java` deleted), `slate-steel.json`, `appearance/{ResolvedTheme,TerminalPaletteAdapter,ThemeController}.java`,
`application/SettingsAppearance.java`, `plugins/HostedUi.java`, docs (`slate-steel.md`, `ui-architecture.md`, `app-architecture.md`,
`app-maintenance.md`, `jasper-ui/README.md`), `migration.csv`; tests: `JasperThemeTest`, `SchemeIdTest` (new) and every test that used `SlateSteel`
or the enum constants.

**Interfaces:**
- Produces (jasper-ui `dev.jasper.ui.theme`):
  - `final class JasperTheme`: `static final String BUNDLED_ID = "slate-steel"`, `static final long FORMAT = 1`;
    `static JasperTheme bundled()`, `static JasperTheme parse(String json, String id)` (errors name `<id>.json`),
    `static boolean validId(String)`; `String id()`, `String name()`, `String author()` (empty when absent),
    `boolean has(boolean dark)`, and for an existing variant `SemanticPalette palette(boolean)`,
    `EditorColors editor(boolean)`, `Map<SyntaxRole, SyntaxStyle> syntax(boolean)`, `GeneratedTheme generated(boolean)`,
    `TerminalScheme matching(boolean)` (each throws `IllegalStateException("<name> has no Dark variant")` otherwise);
    `List<TerminalScheme> schemes()` (file order), `Optional<TerminalScheme> scheme(String key)`.
  - `record SchemeId(String theme, String key)`: `static boolean validKey(String)`, `static Optional<SchemeId> parse(String)`,
    `toString()` is `theme/key`.
  - `record TerminalScheme(SchemeId id, String name, TerminalColors colors, Color boldForeground)`.
  - `ThemeFileException.detail()`.
- Produces (app): `TerminalPaletteAdapter.toTerminal(TerminalScheme)` and `toTerminal(TerminalColors, Color)`.
- Removed: `SlateSteel`, `SchemeColors`, the `TerminalScheme` enum.

- [ ] **Step 1: Apply the test changes**

`````bash
git apply --whitespace=nowarn <<'JASPER_PATCH'
diff --git a/jasper-app/src/test/java/dev/jasper/app/appearance/SlateSteelControllerTest.java b/jasper-app/src/test/java/dev/jasper/app/appearance/SlateSteelControllerTest.java
index 783f75b324477d8c8c83fc412345d1b640e8a4ff..cdb4407d165fe135782ad50775a1bddd06e80364 100644
--- a/jasper-app/src/test/java/dev/jasper/app/appearance/SlateSteelControllerTest.java
+++ b/jasper-app/src/test/java/dev/jasper/app/appearance/SlateSteelControllerTest.java
@@ -6,7 +6,7 @@ import dev.jasper.app.config.TerminalSchemeChoice;
 import dev.jasper.app.testsupport.EdtTestExtension;
 import dev.jasper.ui.theme.GeneratedTheme;
 import dev.jasper.ui.theme.SemanticColor;
-import dev.jasper.ui.theme.SlateSteel;
+import dev.jasper.ui.theme.JasperTheme;
 import dev.jasper.ui.theme.TerminalScheme;
 import java.util.ArrayList;
 import org.junit.jupiter.api.Test;
@@ -15,7 +15,7 @@ import org.junit.jupiter.api.extension.ExtendWith;
 /** spec 5.2–5.3: one Slate Steel variant serves chrome and workspace; the scheme colours only the terminal palette. */
 @ExtendWith(EdtTestExtension.class)
 class SlateSteelControllerTest {
-    static final SlateSteel STEEL = SlateSteel.bundled();
+    static final JasperTheme STEEL = JasperTheme.bundled();
 
     @Test void aFailureNamesTheVariantOnlyForSlateSteelItself() {
         var copy = ThemeTestSupport.recolored(STEEL.generated(true), java.util.Map.of());
@@ -29,7 +29,7 @@ class SlateSteelControllerTest {
         var controller = new ThemeController(false, TerminalSchemeChoice.HIGH_CONTRAST, theme -> true);
         var current = controller.current();
         assertThat(current.chrome()).isSameAs(STEEL.generated(false)).isSameAs(current.workspace());
-        var scheme = STEEL.terminal(TerminalScheme.HIGH_CONTRAST);
+        var scheme = STEEL.scheme("high-contrast").orElseThrow();
         assertThat(current.palette()).isEqualTo(TerminalPaletteAdapter.toTerminal(scheme));
         assertThat(current.palette().boldForeground()).isEqualTo(scheme.boldForeground());
         assertThat(current.surface()).as("around panes and under tabs: the UI variant")
@@ -40,7 +40,7 @@ class SlateSteelControllerTest {
     @Test void matchFollowsTheVariantAndASchemeChangeKeepsTheChrome() {
         var installs = new ArrayList<GeneratedTheme>();
         var controller = new ThemeController(true, TerminalSchemeChoice.MATCH, installs::add);
-        assertThat(controller.current().palette()).isEqualTo(TerminalPaletteAdapter.toTerminal(STEEL.terminal(TerminalScheme.SLATE_STEEL_DARK)));
+        assertThat(controller.current().palette()).isEqualTo(TerminalPaletteAdapter.toTerminal(STEEL.matching(true)));
         var chromeChanges = new ArrayList<Boolean>();
         controller.subscribe((theme, chrome) -> chromeChanges.add(chrome));
         chromeChanges.clear();
@@ -50,7 +50,7 @@ class SlateSteelControllerTest {
         controller.configure(false, TerminalSchemeChoice.MATCH);
         assertThat(chromeChanges).containsExactly(false, true);
         assertThat(installs).hasSize(2).last().isSameAs(STEEL.generated(false));
-        assertThat(controller.current().palette().background()).isEqualTo(STEEL.terminal(TerminalScheme.SLATE_STEEL_LIGHT).colors().background());
+        assertThat(controller.current().palette().background()).isEqualTo(STEEL.matching(false).colors().background());
         controller.configureScheme(TerminalSchemeChoice.SLATE_STEEL_DARK);
         assertThat(controller.current().chrome()).as("the UI stays Light").isSameAs(STEEL.generated(false));
     }
@@ -60,13 +60,13 @@ class SlateSteelControllerTest {
         var controller = ThemeController.startup(false, TerminalSchemeChoice.MATCH, GeneratedTheme::dark, failures::add);
         assertThat(failures).hasSize(1);
         assertThat(controller.current().chrome()).isSameAs(STEEL.generated(true));
-        assertThat(controller.current().palette().background()).isEqualTo(STEEL.terminal(TerminalScheme.SLATE_STEEL_DARK).colors().background());
+        assertThat(controller.current().palette().background()).isEqualTo(STEEL.matching(true).colors().background());
     }
 
     @Test void everySchemeChoiceNamesABundledScheme() {
         for (var choice : TerminalSchemeChoice.values())
             for (boolean dark : new boolean[] {true, false})
                 assertThat(ThemeController.scheme(dark, choice)).isNotNull();
-        assertThat(ThemeController.scheme(false, TerminalSchemeChoice.MATCH)).isEqualTo(TerminalScheme.SLATE_STEEL_LIGHT);
+        assertThat(ThemeController.scheme(false, TerminalSchemeChoice.MATCH)).isEqualTo(JasperTheme.bundled().matching(false));
     }
 }
diff --git a/jasper-app/src/test/java/dev/jasper/app/appearance/ThemeTestSupport.java b/jasper-app/src/test/java/dev/jasper/app/appearance/ThemeTestSupport.java
index ac09bcda80d4985d8d580b6d00af21c3ef676ac3..0c555e5e02137d5bc96433e816dfe34ca7bc14f1 100644
--- a/jasper-app/src/test/java/dev/jasper/app/appearance/ThemeTestSupport.java
+++ b/jasper-app/src/test/java/dev/jasper/app/appearance/ThemeTestSupport.java
@@ -4,10 +4,9 @@ import dev.jasper.app.config.TerminalSchemeChoice;
 import dev.jasper.terminal.config.Palette;
 import dev.jasper.ui.theme.GeneratedTheme;
 import dev.jasper.ui.theme.GlassLevels;
-import dev.jasper.ui.theme.SchemeColors;
 import dev.jasper.ui.theme.SemanticColor;
 import dev.jasper.ui.theme.SemanticPalette;
-import dev.jasper.ui.theme.SlateSteel;
+import dev.jasper.ui.theme.JasperTheme;
 import dev.jasper.ui.theme.TerminalScheme;
 import java.awt.Color;
 import java.util.EnumMap;
@@ -26,22 +25,22 @@ public final class ThemeTestSupport {
     public static boolean install(GeneratedTheme theme) { return ThemeManager.install(theme); }
 
     /** Slate Steel's Dark or Light variant. */
-    public static GeneratedTheme generated(boolean dark) { return SlateSteel.bundled().generated(dark); }
+    public static GeneratedTheme generated(boolean dark) { return JasperTheme.bundled().generated(dark); }
     /** The variant with its matching terminal scheme, as a window shows it. */
-    public static ResolvedTheme resolved(boolean dark) { return resolved(dark, TerminalScheme.matching(dark)); }
+    public static ResolvedTheme resolved(boolean dark) { return resolved(dark, JasperTheme.bundled().matching(dark)); }
     /** The variant with {@code scheme} in its terminal panes. */
     public static ResolvedTheme resolved(boolean dark, TerminalScheme scheme) { return ResolvedTheme.slateSteel(dark, scheme); }
     /** Chrome and workspace that differ, for hosting code that accepts any pair; panes take the workspace's terminal colours. */
     public static ResolvedTheme mixed(GeneratedTheme chrome, GeneratedTheme workspace) {
         return new ResolvedTheme(chrome, workspace,
-            TerminalPaletteAdapter.toTerminal(new SchemeColors(workspace.terminal(), workspace.terminal().foreground())));
+            TerminalPaletteAdapter.toTerminal(workspace.terminal(), workspace.terminal().foreground()));
     }
     /** The variant's matching terminal scheme as a terminal palette. */
-    public static Palette terminal(boolean dark) { return terminal(TerminalScheme.matching(dark)); }
+    public static Palette terminal(boolean dark) { return terminal(JasperTheme.bundled().matching(dark)); }
     /** {@code scheme} as a terminal palette. */
-    public static Palette terminal(TerminalScheme scheme) { return TerminalPaletteAdapter.toTerminal(SlateSteel.bundled().terminal(scheme)); }
+    public static Palette terminal(TerminalScheme scheme) { return TerminalPaletteAdapter.toTerminal(scheme); }
     /** {@code role} in the variant. */
-    public static Color color(boolean dark, SemanticColor role) { return SlateSteel.bundled().palette(dark).color(role); }
+    public static Color color(boolean dark, SemanticColor role) { return JasperTheme.bundled().palette(dark).color(role); }
     /** A controller showing the variant with the matching scheme; constructing one also resets the installed look and fonts. */
     public static ThemeController controller(boolean dark) { return new ThemeController(dark, TerminalSchemeChoice.MATCH); }
     /** As {@link #controller(boolean)}, installing through {@code installer}. */
diff --git a/jasper-app/src/test/java/dev/jasper/app/application/JasperApplicationPluginsTest.java b/jasper-app/src/test/java/dev/jasper/app/application/JasperApplicationPluginsTest.java
index 792748767d93d8a01e1c0bb007c93f39349a564c..59ffea5b8037a49b3f62e4aad2a65d1c2a622a2b 100644
--- a/jasper-app/src/test/java/dev/jasper/app/application/JasperApplicationPluginsTest.java
+++ b/jasper-app/src/test/java/dev/jasper/app/application/JasperApplicationPluginsTest.java
@@ -344,7 +344,7 @@ class JasperApplicationPluginsTest {
     private static String installedLightLaf() {
         var laf = javax.swing.UIManager.getLookAndFeel();
         assertThat(laf).isInstanceOf(com.formdev.flatlaf.IntelliJTheme.ThemeLaf.class);
-        var light = dev.jasper.ui.theme.SlateSteel.bundled().generated(false);
+        var light = dev.jasper.ui.theme.JasperTheme.bundled().generated(false);
         assertThat(dev.jasper.ui.swing.FlatLafInstaller.installedTheme()).isSameAs(light);
         assertThat(laf.getDefaults().get("Jasper.installedTheme")).as("the snapshot travels with the LAF itself").isSameAs(light);
         assertThat(laf.getName()).isEqualTo(light.name());
diff --git a/jasper-app/src/test/java/dev/jasper/app/application/JasperApplicationThemesTest.java b/jasper-app/src/test/java/dev/jasper/app/application/JasperApplicationThemesTest.java
index 6708850d41e2a642a219ebda3477785313661286..2b0acea97caee02d345e7e2e0f413b1df3e6725b 100644
--- a/jasper-app/src/test/java/dev/jasper/app/application/JasperApplicationThemesTest.java
+++ b/jasper-app/src/test/java/dev/jasper/app/application/JasperApplicationThemesTest.java
@@ -4,7 +4,7 @@ import dev.jasper.app.appearance.ThemeTestSupport;
 import dev.jasper.app.appearance.ThemeController;
 import dev.jasper.app.commands.CommandHistory;
 import dev.jasper.app.config.ConfigService;
-import dev.jasper.ui.theme.SlateSteel;
+import dev.jasper.ui.theme.JasperTheme;
 import java.nio.file.Files;
 import java.nio.file.Path;
 import java.util.ArrayDeque;
@@ -32,12 +32,12 @@ class JasperApplicationThemesTest {
                 catch (javax.swing.UnsupportedLookAndFeelException failure) { throw new AssertionError(failure); }
                 org.assertj.core.api.Assertions.assertThatIllegalStateException().isThrownBy(dev.jasper.ui.swing.FlatLafInstaller::installedTheme);
                 application[0] = new JasperApplication(service, launcher(new ArrayDeque<>()), new CommandHistory(), null, terminated::countDown);
-                var expected = SlateSteel.bundled().generated(dark);
+                var expected = JasperTheme.bundled().generated(dark);
                 assertThat(dev.jasper.ui.swing.FlatLafInstaller.installedTheme()).isSameAs(expected);
                 assertThat(dev.jasper.app.platform.ThemeZone.theme(null)).isSameAs(expected);
                 // Plan 2 finding 8: the configured scheme reaches the terminal palette.
                 assertThat(application[0].themes().current().palette())
-                    .isEqualTo(dev.jasper.app.appearance.TerminalPaletteAdapter.toTerminal(SlateSteel.bundled().terminal(scheme)));
+                    .isEqualTo(dev.jasper.app.appearance.TerminalPaletteAdapter.toTerminal(scheme));
             });
         } finally {
             if (application[0] != null) { edt(application[0]::quit); assertThat(terminated.await(5, TimeUnit.SECONDS)).isTrue(); }
@@ -48,12 +48,12 @@ class JasperApplicationThemesTest {
 
     /** From a fresh look and feel (no Jasper snapshot), start-up installs its variant before any chrome reads the snapshot. */
     @Test void aFreshProcessInstallsTheConfiguredVariantBeforeAnyChromeReadsTheSnapshot() throws Exception {
-        start("[ui]\nappearance = 'light'\n[terminal]\nscheme = 'high-contrast'\n", false, dev.jasper.ui.theme.TerminalScheme.HIGH_CONTRAST);
+        start("[ui]\nappearance = 'light'\n[terminal]\nscheme = 'high-contrast'\n", false, dev.jasper.ui.theme.JasperTheme.bundled().scheme("high-contrast").orElseThrow());
     }
 
     /** The default follows the operating system; a headless run has none, so it starts Dark. */
     @Test void withNoAppearanceAHeadlessStartIsDark() throws Exception {
-        start("", true, dev.jasper.ui.theme.TerminalScheme.SLATE_STEEL_DARK);
+        start("", true, dev.jasper.ui.theme.JasperTheme.bundled().matching(true));
     }
 
     /** Review Focus 1: an older install's theme folders are neither read nor changed; nothing else appears beside them. */
@@ -66,7 +66,7 @@ class JasperApplicationThemesTest {
         java.nio.file.Files.writeString(home.resolve("terminal-themes/dark.toml"), "[colors]\n");
         java.nio.file.Files.writeString(home.resolve("config.toml"), config);
         var before = snapshot(home);
-        start(config, false, dev.jasper.ui.theme.TerminalScheme.SLATE_STEEL_LIGHT);
+        start(config, false, dev.jasper.ui.theme.JasperTheme.bundled().matching(false));
         assertThat(snapshot(home)).as("every path and byte under the home").isEqualTo(before);
     }
 
diff --git a/jasper-app/src/test/java/dev/jasper/app/application/SystemThemeApplicationTest.java b/jasper-app/src/test/java/dev/jasper/app/application/SystemThemeApplicationTest.java
index 649ca12fa4fde368dda4ba6b88078fb73fa70f76..8424c6a56f3a08d6ca11c71601639541ecf41983 100644
--- a/jasper-app/src/test/java/dev/jasper/app/application/SystemThemeApplicationTest.java
+++ b/jasper-app/src/test/java/dev/jasper/app/application/SystemThemeApplicationTest.java
@@ -5,7 +5,7 @@ import dev.jasper.app.appearance.*;
 import dev.jasper.app.config.*;
 import dev.jasper.app.platform.SystemAppearance.Observation;
 import dev.jasper.app.testsupport.EdtTestExtension;
-import dev.jasper.ui.theme.SlateSteel;
+import dev.jasper.ui.theme.JasperTheme;
 import dev.jasper.ui.theme.TerminalScheme;
 import java.nio.file.*;
 import java.util.*;
@@ -16,7 +16,7 @@ import org.junit.jupiter.api.io.TempDir;
 /** spec 5.2, Review Focus 1: the OS appearance re-themes live and never writes configuration or reaches a draft. */
 @ExtendWith(EdtTestExtension.class)
 class SystemThemeApplicationTest {
-    static final SlateSteel STEEL = SlateSteel.bundled();
+    static final JasperTheme STEEL = JasperTheme.bundled();
     @TempDir Path home;
 
     @Test void withTheSystemAppearanceAnOsChangeReThemesWithoutPublishingOrWritingConfiguration() throws Exception {
@@ -33,7 +33,7 @@ class SystemThemeApplicationTest {
             draft.stage(dev.jasper.app.config.CoreSettings.WINDOW_TAB_HEIGHT, java.util.Optional.of(44));
             controller.systemAppearanceChanged(Observation.light());
             assertThat(themes.current().chrome()).isSameAs(STEEL.generated(false));
-            assertThat(themes.current().palette().background()).isEqualTo(STEEL.terminal(TerminalScheme.SLATE_STEEL_LIGHT).colors().background());
+            assertThat(themes.current().palette().background()).isEqualTo(STEEL.matching(false).colors().background());
             assertThat(states).as("no configuration state reaches an open Settings draft").isEmpty();
             assertThat(Files.readString(file)).isEqualTo(text);
             assertThat(draft.modified()).isTrue();
diff --git a/jasper-app/src/test/java/dev/jasper/app/plugins/HostedPalettesTest.java b/jasper-app/src/test/java/dev/jasper/app/plugins/HostedPalettesTest.java
index 182c3891c3cdff78c3b7be6994803f388cfb283e..0aa84c4915afc3750df4452740b0ee99b7bd4c21 100644
--- a/jasper-app/src/test/java/dev/jasper/app/plugins/HostedPalettesTest.java
+++ b/jasper-app/src/test/java/dev/jasper/app/plugins/HostedPalettesTest.java
@@ -5,7 +5,7 @@ import static org.assertj.core.api.Assertions.assertThat;
 
 import dev.jasper.sdk.ui.EditorPalette;
 import dev.jasper.sdk.ui.WorkspacePalette;
-import dev.jasper.ui.theme.SlateSteel;
+import dev.jasper.ui.theme.JasperTheme;
 import org.junit.jupiter.api.Test;
 
 /** Review Focus 2: plugins receive Slate Steel's roles and editor scheme, as before the token view went. */
@@ -17,7 +17,7 @@ class HostedPalettesTest {
             int i = role.ordinal() + 1;
             roles.put(role, new java.awt.Color(i * 3, 255 - i * 2, (i * 53) % 256));
         }
-        var base = dev.jasper.app.appearance.ThemeTestSupport.recolored(SlateSteel.bundled().generated(true), roles);
+        var base = dev.jasper.app.appearance.ThemeTestSupport.recolored(JasperTheme.bundled().generated(true), roles);
         var editor = new dev.jasper.ui.theme.EditorColors(new java.awt.Color(1, 2, 3), new java.awt.Color(4, 5, 6), new java.awt.Color(7, 8, 9),
             new java.awt.Color(10, 11, 12), new java.awt.Color(13, 14, 15), new java.awt.Color(16, 17, 18), new java.awt.Color(19, 20, 21));
         var theme = new dev.jasper.ui.theme.GeneratedTheme(base.name(), base.dark(), base.author(), base.semantic(), base.terminal(), editor,
@@ -33,13 +33,13 @@ class HostedPalettesTest {
 
     @Test void thePalettesAreTheVariantsRolesAndEditorScheme() {
         for (boolean dark : new boolean[]{true, false}) {
-            var theme = SlateSteel.bundled().generated(dark);
+            var theme = JasperTheme.bundled().generated(dark);
             var p = theme.semantic();
             assertThat(HostedPalettes.workspace(theme)).isEqualTo(new WorkspacePalette(p.color(SURFACE_PANEL), p.color(SURFACE_PANEL_RAISED),
                 p.color(SURFACE_CARD), p.color(TEXT_PRIMARY), p.color(TEXT_SECONDARY), p.color(BORDER_WINDOW), p.color(SELECTION_BG),
                 p.color(TEXT_ONACCENT), p.color(SURFACE_CONTROL_HOVER), p.color(ACCENT), p.color(STATUS_SUCCESS),
                 p.color(STATUS_WARNING), p.color(STATUS_DANGER)));
-            var e = SlateSteel.bundled().editor(dark);
+            var e = JasperTheme.bundled().editor(dark);
             assertThat(theme.editor()).isEqualTo(e);
             assertThat(HostedPalettes.editor(theme)).isEqualTo(new EditorPalette(e.background(), e.foreground(), e.caret(),
                 e.selection(), e.currentLine(), e.gutter(), e.lineNumbers()));
diff --git a/jasper-app/src/test/java/dev/jasper/app/settingsui/AppearanceFixtures.java b/jasper-app/src/test/java/dev/jasper/app/settingsui/AppearanceFixtures.java
index c714388c85c5eab60d1a4ba7d1cde484f0f2a5ce..1a028adb8843d1ff31df30e7f4ced5cc8427c0e8 100644
--- a/jasper-app/src/test/java/dev/jasper/app/settingsui/AppearanceFixtures.java
+++ b/jasper-app/src/test/java/dev/jasper/app/settingsui/AppearanceFixtures.java
@@ -3,7 +3,7 @@ package dev.jasper.app.settingsui;
 import dev.jasper.app.lifecycle.Subscription;
 import dev.jasper.ui.model.Option;
 import dev.jasper.ui.theme.SemanticColor;
-import dev.jasper.ui.theme.SlateSteel;
+import dev.jasper.ui.theme.JasperTheme;
 import java.util.Optional;
 import java.util.function.Supplier;
 
@@ -19,7 +19,7 @@ public final class AppearanceFixtures {
 
     /** A Slate Steel variant's preview, as the application builds it. */
     public static Option.Palette preview(boolean dark) {
-        var palette = SlateSteel.bundled().palette(dark);
+        var palette = JasperTheme.bundled().palette(dark);
         return new Option.Palette(palette.color(SemanticColor.SURFACE_INPUT).getRGB(), palette.color(SemanticColor.SURFACE_SIDEBAR).getRGB(),
             palette.color(SemanticColor.SURFACE_APP).getRGB(), palette.color(SemanticColor.TEXT_PRIMARY).getRGB(),
             palette.color(SemanticColor.ACCENT).getRGB());
diff --git a/jasper-app/src/test/java/dev/jasper/app/workspace/ConfigurationControllerTest.java b/jasper-app/src/test/java/dev/jasper/app/workspace/ConfigurationControllerTest.java
index 9e59756b664a264565325b945a0faa8af85f6e53..5adb0495a36decc325680fa45f1427769cb6c790 100644
--- a/jasper-app/src/test/java/dev/jasper/app/workspace/ConfigurationControllerTest.java
+++ b/jasper-app/src/test/java/dev/jasper/app/workspace/ConfigurationControllerTest.java
@@ -13,7 +13,7 @@ import dev.jasper.app.config.AppearanceMode;
 import dev.jasper.app.config.ConfigService;
 import dev.jasper.app.config.TerminalSchemeChoice;
 import dev.jasper.ui.theme.GeneratedTheme;
-import dev.jasper.ui.theme.SlateSteel;
+import dev.jasper.ui.theme.JasperTheme;
 import dev.jasper.ui.theme.TerminalScheme;
 import dev.jasper.app.config.ConfigSnapshot;
 import dev.jasper.app.config.FontConfig;
@@ -80,7 +80,7 @@ class ConfigurationControllerTest {
             assertThat(first.tabHeight()).isEqualTo(44);
             assertThat(((JButton) first.toolbar().getComponent(0)).getText()).isNull();
             assertThat(first.status().isVisible()).isFalse();
-            assertThat(first.theme().chrome()).isSameAs(SlateSteel.bundled().generated(false));
+            assertThat(first.theme().chrome()).isSameAs(JasperTheme.bundled().generated(false));
             assertThat(retained.view().fontSize()).isEqualTo(19);
             retained.findBar().open(); retained.findBar().queryField().setText("alpha");
             first.newTab(HOME); // pending and hides the retained terminal
@@ -113,18 +113,18 @@ class ConfigurationControllerTest {
         edt(() -> {
             assertThat(first.tabHeight()).as("the saved chrome is reapplied").isEqualTo(30);
             assertThat(first.toolbar().isVisible()).isTrue();
-            assertThat(first.status().isVisible()).isTrue(); assertThat(first.theme().chrome()).isSameAs(SlateSteel.bundled().generated(true));
+            assertThat(first.status().isVisible()).isTrue(); assertThat(first.theme().chrome()).isSameAs(JasperTheme.bundled().generated(true));
             assertThat(first.currentPane().view().fontSize()).as("a pane's own size stays").isEqualTo(28);
             var next = owner(); assertThat(next.tabHeight()).isEqualTo(30);
             assertThat(next.toolbar().isVisible()).isTrue(); assertThat(next.status().isVisible()).isTrue();
-            assertThat(next.theme().chrome()).isSameAs(SlateSteel.bundled().generated(true));
+            assertThat(next.theme().chrome()).isSameAs(JasperTheme.bundled().generated(true));
         }); launchAll();
         edt(() -> assertThat(owners.get(1).currentPane().view().fontSize()).isEqualTo(19));
         reload("[window]\ntab_height=45\ntoolbar='icons'\nstatus_bar=false\n[terminal.font]\nsize=20\n[ui]\nappearance='light'\n");
         edt(() -> {
             assertThat(first.tabHeight()).isEqualTo(45); assertThat(first.toolbar().isVisible()).isTrue();
             assertThat(first.currentPane().view().fontSize()).isEqualTo(20);
-            assertThat(first.theme().chrome()).as("the saved appearance applies").isSameAs(SlateSteel.bundled().generated(false));
+            assertThat(first.theme().chrome()).as("the saved appearance applies").isSameAs(JasperTheme.bundled().generated(false));
         });
     }
 
@@ -244,7 +244,7 @@ class ConfigurationControllerTest {
             var state = new ConfigService.State(snapshot, List.of(), directory.resolve("config.toml"), true);
             controller.accept(state);
             assertThat(owners.getFirst().tabHeight()).isEqualTo(53);
-            assertThat(owners.getFirst().theme().chrome()).isSameAs(SlateSteel.bundled().generated(true));
+            assertThat(owners.getFirst().theme().chrome()).isSameAs(JasperTheme.bundled().generated(true));
             // A theme that fails to install is also a configuration warning now, not just a toast: the
             // status bar reflects it and shown() carries the diagnostic (both still keep the other
             // settings that theme accompanied, per this test's name).
@@ -278,7 +278,7 @@ class ConfigurationControllerTest {
             var published=new ArrayList<ResolvedTheme>();
             var listening=themes.subscribe((value,changed) -> published.add(value));published.clear();
             assertThatCode(() -> controller.accept(next)).doesNotThrowAnyException();
-            assertThat(themes.current().chrome()).isSameAs(SlateSteel.bundled().generated(false));
+            assertThat(themes.current().chrome()).isSameAs(JasperTheme.bundled().generated(false));
             assertThat(published).containsExactly(themes.current());
             assertThat(controller.shown().snapshot()).isEqualTo(next.snapshot());
             assertThat(controller.shown().diagnostics()).noneMatch(diagnostic -> diagnostic.message().contains("Could not apply"));
@@ -536,7 +536,7 @@ class ConfigurationControllerTest {
         };
         var broken = new java.util.concurrent.atomic.AtomicBoolean(false);
         var attempts = new ArrayList<GeneratedTheme>();
-        var dark = SlateSteel.bundled().generated(true); var light = SlateSteel.bundled().generated(false);
+        var dark = JasperTheme.bundled().generated(true); var light = JasperTheme.bundled().generated(false);
         var errors = new ArrayList<String>();
         service = new ConfigService(file, false, configWorker, SwingUtilities::invokeLater);
         edt(() -> {
@@ -563,7 +563,7 @@ class ConfigurationControllerTest {
         edt(() -> {
             assertThat(themes.current().chrome()).isSameAs(light);
             assertThat(owners.getFirst().theme()).isEqualTo(themes.current());
-            assertThat(themes.current()).isEqualTo(dev.jasper.app.appearance.ResolvedTheme.slateSteel(false, TerminalScheme.SLATE_STEEL_LIGHT));
+            assertThat(themes.current()).isEqualTo(dev.jasper.app.appearance.ResolvedTheme.slateSteel(false, JasperTheme.bundled().matching(false)));
             assertThat(attempts).containsExactly(dark, light, light);
             assertThat(errors).hasSize(1);
             owners.getFirst().selectVariant(true);
@@ -580,7 +580,7 @@ class ConfigurationControllerTest {
     }
 
     private static Palette slateSteelDarkPalette() {
-        return dev.jasper.app.appearance.TerminalPaletteAdapter.toTerminal(SlateSteel.bundled().terminal(TerminalScheme.SLATE_STEEL_DARK));
+        return dev.jasper.app.appearance.TerminalPaletteAdapter.toTerminal(JasperTheme.bundled().matching(true));
     }
 
     private static String liveSettings(int size) {
@@ -657,10 +657,10 @@ class ConfigurationControllerTest {
 
     @Test void savedTerminalSchemeAppliesLiveOnStartAndReloadWithoutARestartNotice() throws Exception {
         start("[ui]\nappearance='light'\n[terminal]\nscheme='slate-steel-dark'\n");
-        edt(() -> assertThat(themes.current()).isEqualTo(dev.jasper.app.appearance.ResolvedTheme.slateSteel(false, TerminalScheme.SLATE_STEEL_DARK)));
+        edt(() -> assertThat(themes.current()).isEqualTo(dev.jasper.app.appearance.ResolvedTheme.slateSteel(false, JasperTheme.bundled().matching(true))));
         reload("[ui]\nappearance='light'\n[terminal]\nscheme='match'\n");
         edt(() -> {
-            assertThat(themes.current()).isEqualTo(dev.jasper.app.appearance.ResolvedTheme.slateSteel(false, TerminalScheme.SLATE_STEEL_LIGHT));
+            assertThat(themes.current()).isEqualTo(dev.jasper.app.appearance.ResolvedTheme.slateSteel(false, JasperTheme.bundled().matching(false)));
             assertThat(controller.shown().diagnostics()).noneMatch(d -> d.key().startsWith("ui.") || d.key().startsWith("terminal."));
         });
     }
diff --git a/jasper-app/src/test/java/dev/jasper/app/workspace/HostedContentPreview.java b/jasper-app/src/test/java/dev/jasper/app/workspace/HostedContentPreview.java
index c1fe71e1591d8a03f2f4f49bfc2a0bc60124b969..d536dce6db35cb8d83832aa42a81a307e6c2d243 100644
--- a/jasper-app/src/test/java/dev/jasper/app/workspace/HostedContentPreview.java
+++ b/jasper-app/src/test/java/dev/jasper/app/workspace/HostedContentPreview.java
@@ -45,8 +45,8 @@ public final class HostedContentPreview {
                     for (var entry : model.content().entries()) {
                         var scroll = (JScrollPane)entry.component();
                         var text = (JTextArea)scroll.getViewport().getView();
-                        text.setBackground(dev.jasper.ui.theme.SlateSteel.bundled().editor(dev.jasper.app.platform.ThemeZone.theme(text).dark()).background());
-                        text.setForeground(dev.jasper.ui.theme.SlateSteel.bundled().editor(dev.jasper.app.platform.ThemeZone.theme(text).dark()).foreground());
+                        text.setBackground(dev.jasper.ui.theme.JasperTheme.bundled().editor(dev.jasper.app.platform.ThemeZone.theme(text).dark()).background());
+                        text.setForeground(dev.jasper.ui.theme.JasperTheme.bundled().editor(dev.jasper.app.platform.ThemeZone.theme(text).dark()).foreground());
                         scroll.getViewport().setBackground(text.getBackground());
                     }
                     selected.select();
diff --git a/jasper-app/src/test/java/dev/jasper/app/workspace/TerminalSchemeCommandsTest.java b/jasper-app/src/test/java/dev/jasper/app/workspace/TerminalSchemeCommandsTest.java
index 316be256f8c2cc44d842abab763d5d0161a30a97..8a7cc516f4ecdb246a49f4dd429a130bfe7cd2e7 100644
--- a/jasper-app/src/test/java/dev/jasper/app/workspace/TerminalSchemeCommandsTest.java
+++ b/jasper-app/src/test/java/dev/jasper/app/workspace/TerminalSchemeCommandsTest.java
@@ -7,7 +7,7 @@ import static org.assertj.core.api.Assertions.assertThat;
 import dev.jasper.app.appearance.TerminalPaletteAdapter;
 import dev.jasper.app.appearance.ThemeController;
 import dev.jasper.app.config.TerminalSchemeChoice;
-import dev.jasper.ui.theme.SlateSteel;
+import dev.jasper.ui.theme.JasperTheme;
 import dev.jasper.ui.theme.TerminalScheme;
 import java.util.ArrayDeque;
 import java.util.Locale;
@@ -21,7 +21,7 @@ class TerminalSchemeCommandsTest {
     @AfterEach void cleanup() throws Exception { closeOwners(); edt(() -> ThemeTestSupport.controller(true)); }
 
     static dev.jasper.terminal.config.Palette palette(TerminalScheme scheme) {
-        return TerminalPaletteAdapter.toTerminal(SlateSteel.bundled().terminal(scheme));
+        return TerminalPaletteAdapter.toTerminal(scheme);
     }
 
     @Test @DisabledOnOs(OS.WINDOWS)
@@ -42,19 +42,19 @@ class TerminalSchemeCommandsTest {
             owner.newTab(HOME);
             owner.selectTerminalScheme(TerminalSchemeChoice.HIGH_CONTRAST);
             assertThat(com.formdev.flatlaf.FlatLaf.isLafDark()).as("the UI stays light").isFalse();
-            assertThat(hidden[0].view().palette()).as("a background tab").isEqualTo(palette(TerminalScheme.HIGH_CONTRAST));
-            assertThat(owners[1].currentPane().view().palette()).as("another window").isEqualTo(palette(TerminalScheme.HIGH_CONTRAST));
+            assertThat(hidden[0].view().palette()).as("a background tab").isEqualTo(palette(JasperTheme.bundled().scheme("high-contrast").orElseThrow()));
+            assertThat(owners[1].currentPane().view().palette()).as("another window").isEqualTo(palette(JasperTheme.bundled().scheme("high-contrast").orElseThrow()));
             owner.newTab(HOME);
         });
         while (!pending.isEmpty()) pending.remove().run();
         until(() -> owners[0].currentPane().view() != null);
         edt(() -> {
             assertThat(owners[0].currentPane().view().palette()).as("a tab opened after the switch")
-                .isEqualTo(palette(TerminalScheme.HIGH_CONTRAST));
+                .isEqualTo(palette(JasperTheme.bundled().scheme("high-contrast").orElseThrow()));
             var session = hidden[0].session();
             owners[0].selectTerminalScheme(TerminalSchemeChoice.MATCH);
             assertThat(hidden[0].session()).isSameAs(session);
-            assertThat(hidden[0].view().palette()).isEqualTo(palette(TerminalScheme.SLATE_STEEL_LIGHT));
+            assertThat(hidden[0].view().palette()).isEqualTo(palette(JasperTheme.bundled().matching(false)));
         });
     }
 
diff --git a/jasper-app/src/test/java/dev/jasper/app/workspace/TerminalSchemeSurfaceTest.java b/jasper-app/src/test/java/dev/jasper/app/workspace/TerminalSchemeSurfaceTest.java
index 86b200ae2123f530ce053afdc72db62f62e64e65..9e3bb97b1df650b9d5cc90a978dd8e099426d2b8 100644
--- a/jasper-app/src/test/java/dev/jasper/app/workspace/TerminalSchemeSurfaceTest.java
+++ b/jasper-app/src/test/java/dev/jasper/app/workspace/TerminalSchemeSurfaceTest.java
@@ -7,7 +7,7 @@ import static org.assertj.core.api.Assertions.*;
 import dev.jasper.app.appearance.ThemeController;
 import dev.jasper.app.config.TerminalSchemeChoice;
 import dev.jasper.ui.theme.SemanticColor;
-import dev.jasper.ui.theme.SlateSteel;
+import dev.jasper.ui.theme.JasperTheme;
 import dev.jasper.ui.theme.TerminalScheme;
 import java.util.ArrayDeque;
 import org.junit.jupiter.api.AfterEach;
@@ -31,8 +31,8 @@ class TerminalSchemeSurfaceTest {
         while (!pending.isEmpty()) pending.remove().run();
         until(() -> owner[0].currentPane().view() != null);
         edt(() -> {
-            var light = SlateSteel.bundled().palette(false);
-            var black = SlateSteel.bundled().terminal(TerminalScheme.HIGH_CONTRAST).colors().background();
+            var light = JasperTheme.bundled().palette(false);
+            var black = JasperTheme.bundled().scheme("high-contrast").orElseThrow().colors().background();
             assertThat(owner[0].getBackground()).isEqualTo(light.color(SemanticColor.SURFACE_TERMINAL));
             assertThat(owner[0].workspaceSurface()).isEqualTo(light.color(SemanticColor.SURFACE_TERMINAL));
             assertThat(owner[0].windowTabs().getBackground()).as("the tab strip").isEqualTo(light.color(SemanticColor.SURFACE_TERMINAL));
diff --git a/jasper-app/src/test/java/dev/jasper/app/workspace/WindowVariantTest.java b/jasper-app/src/test/java/dev/jasper/app/workspace/WindowVariantTest.java
index a9aa343cd1d92cbf49e53a59566f221bc0d73216..32218dec9593853d3917257ec8a966bafe0b80b8 100644
--- a/jasper-app/src/test/java/dev/jasper/app/workspace/WindowVariantTest.java
+++ b/jasper-app/src/test/java/dev/jasper/app/workspace/WindowVariantTest.java
@@ -7,7 +7,7 @@ import dev.jasper.app.appearance.ThemeController;
 import dev.jasper.app.appearance.ThemeTestSupport;
 import dev.jasper.app.config.TerminalSchemeChoice;
 import dev.jasper.ui.theme.SemanticColor;
-import dev.jasper.ui.theme.SlateSteel;
+import dev.jasper.ui.theme.JasperTheme;
 import dev.jasper.ui.theme.TerminalScheme;
 import java.util.ArrayDeque;
 import org.junit.jupiter.api.AfterEach;
@@ -29,13 +29,13 @@ class WindowVariantTest {
         edt(() -> {
             var session = owner[0].currentPane().session();
             owner[0].selectVariant(false);
-            assertThat(owner[0].theme().chrome()).isSameAs(SlateSteel.bundled().generated(false));
+            assertThat(owner[0].theme().chrome()).isSameAs(JasperTheme.bundled().generated(false));
             assertThat(owner[0].terminalSchemeSelected(TerminalSchemeChoice.HIGH_CONTRAST)).as("the scheme stays").isTrue();
             assertThat(owner[0].currentPane().session()).as("the shell keeps running").isSameAs(session);
             assertThat(owner[0].getBackground()).isEqualTo(ThemeTestSupport.color(false, SemanticColor.SURFACE_TERMINAL));
-            assertThat(owner[0].currentPane().view().palette()).isEqualTo(ThemeTestSupport.terminal(TerminalScheme.HIGH_CONTRAST));
+            assertThat(owner[0].currentPane().view().palette()).isEqualTo(ThemeTestSupport.terminal(JasperTheme.bundled().scheme("high-contrast").orElseThrow()));
             owner[0].selectVariant(true);
-            assertThat(owner[0].theme().chrome()).isSameAs(SlateSteel.bundled().generated(true));
+            assertThat(owner[0].theme().chrome()).isSameAs(JasperTheme.bundled().generated(true));
             assertThat(owner[0].getBackground()).isEqualTo(ThemeTestSupport.color(true, SemanticColor.SURFACE_TERMINAL));
         });
     }
diff --git a/jasper-ui/src/test/java/dev/jasper/ui/gallery/UiExamplesTest.java b/jasper-ui/src/test/java/dev/jasper/ui/gallery/UiExamplesTest.java
index 537dd411431bc6f12dca3783e73042e70e0bb0af..8deea5249135a6f14b63365a5e0491a248c478e5 100644
--- a/jasper-ui/src/test/java/dev/jasper/ui/gallery/UiExamplesTest.java
+++ b/jasper-ui/src/test/java/dev/jasper/ui/gallery/UiExamplesTest.java
@@ -1,5 +1,7 @@
 package dev.jasper.ui.gallery;
 
+import dev.jasper.ui.theme.JasperTheme;
+
 import dev.jasper.ui.icons.IconRegistry;
 import dev.jasper.ui.metrics.DesignMetrics;
 import dev.jasper.ui.model.*;
@@ -15,10 +17,10 @@ import static org.assertj.core.api.Assertions.*;
 class UiExamplesTest {
     @Test void colorEngineWorksWithoutSwing() {
         // example:palette:start
-        var theme = SlateSteel.bundled().generated(true);
+        var theme = JasperTheme.bundled().generated(true);
         var cardColor = theme.semantic().color(SemanticColor.SURFACE_CARD);
         // example:palette:end
-        assertThat(cardColor).isEqualTo(SlateSteel.bundled().palette(true).color(SemanticColor.SURFACE_CARD));
+        assertThat(cardColor).isEqualTo(JasperTheme.bundled().palette(true).color(SemanticColor.SURFACE_CARD));
     }
     @Test void viewOwnsItsControlsAndCallerOwnsBindingAndIcons() {
         Edt.run(() -> {
diff --git a/jasper-ui/src/test/java/dev/jasper/ui/swing/SlateSteelInstallTest.java b/jasper-ui/src/test/java/dev/jasper/ui/swing/SlateSteelInstallTest.java
index 5f8c1bd58fb16a09c78f93e69d751b3629fae88b..44d0ba14333093febe485c62736deb5b5a2ed74d 100644
--- a/jasper-ui/src/test/java/dev/jasper/ui/swing/SlateSteelInstallTest.java
+++ b/jasper-ui/src/test/java/dev/jasper/ui/swing/SlateSteelInstallTest.java
@@ -4,7 +4,7 @@ import static org.assertj.core.api.Assertions.*;
 
 import dev.jasper.ui.testsupport.Edt;
 import dev.jasper.ui.theme.SemanticColor;
-import dev.jasper.ui.theme.SlateSteel;
+import dev.jasper.ui.theme.JasperTheme;
 import java.awt.Insets;
 import java.util.List;
 import javax.swing.UIManager;
@@ -17,7 +17,7 @@ class SlateSteelInstallTest {
             var prior = UIManager.getLookAndFeel();
             try {
                 for (boolean dark : new boolean[] {true, false}) {
-                    var theme = SlateSteel.bundled().generated(dark);
+                    var theme = JasperTheme.bundled().generated(dark);
                     assertThat(theme.uiKeys()).doesNotContainKeys("Popup.dropShadowColor", "Popup.dropShadowOpacity");
                     FlatLafInstaller.install(theme);
                     assertThat(UIManager.getColor("Popup.dropShadowColor")).isEqualTo(java.awt.Color.BLACK);
@@ -34,7 +34,7 @@ class SlateSteelInstallTest {
         Edt.run(() -> {
             var prior = UIManager.getLookAndFeel();
             try {
-                FlatLafInstaller.install(SlateSteel.bundled().generated(true));
+                FlatLafInstaller.install(JasperTheme.bundled().generated(true));
                 for (var key : List.of("Button.arc", "Component.arc", "TextComponent.arc")) assertThat(UIManager.getInt(key)).as(key).isEqualTo(8);
                 assertThat(UIManager.getInsets("Button.margin")).isEqualTo(new Insets(2, 14, 2, 14));
                 assertThat(UIManager.getInt("Button.minimumWidth")).isEqualTo(72);
@@ -60,7 +60,7 @@ class SlateSteelInstallTest {
             var prior = UIManager.getLookAndFeel();
             try {
                 for (boolean dark : new boolean[] {true, false}) {
-                    var theme = SlateSteel.bundled().generated(dark); var p = theme.semantic();
+                    var theme = JasperTheme.bundled().generated(dark); var p = theme.semantic();
                     FlatLafInstaller.install(theme);
                     for (var key : List.of("PopupMenu.background", "MenuItem.background", "Menu.background")) assertThat(UIManager.getColor(key)).as(key).isEqualTo(p.color(SemanticColor.SURFACE_MENU));
                     for (var key : List.of("MenuItem.selectionBackground", "Menu.selectionBackground", "CheckBoxMenuItem.selectionBackground", "RadioButtonMenuItem.selectionBackground"))
@@ -80,7 +80,7 @@ class SlateSteelInstallTest {
             var prior = UIManager.getLookAndFeel();
             try {
                 for (boolean dark : new boolean[] {true, false}) {
-                    var theme = SlateSteel.bundled().generated(dark);
+                    var theme = JasperTheme.bundled().generated(dark);
                     FlatLafInstaller.install(theme);
                     assertThat(UIManager.get("Jasper.installedTheme")).isSameAs(theme);
                     assertThat(UIManager.getColor("Panel.background")).isEqualTo(theme.semantic().color(SemanticColor.SURFACE_PANEL));
diff --git a/jasper-ui/src/test/java/dev/jasper/ui/testsupport/KitFixtures.java b/jasper-ui/src/test/java/dev/jasper/ui/testsupport/KitFixtures.java
index cd1105d0eb7d2c9df7717e0dd4e994162ddf3468..07dfbc6c54c67ea6d36081ec7e851d801a5fe9aa 100644
--- a/jasper-ui/src/test/java/dev/jasper/ui/testsupport/KitFixtures.java
+++ b/jasper-ui/src/test/java/dev/jasper/ui/testsupport/KitFixtures.java
@@ -1,5 +1,7 @@
 package dev.jasper.ui.testsupport;
 
+import dev.jasper.ui.theme.JasperTheme;
+
 import dev.jasper.ui.icons.IconRegistry;
 import dev.jasper.ui.metrics.DesignMetrics;
 import dev.jasper.ui.swing.KitContext;
@@ -9,9 +11,9 @@ import dev.jasper.ui.theme.*;
 public final class KitFixtures {
     private KitFixtures() {}
 
-    public static GeneratedTheme dark() { return SlateSteel.bundled().generated(true); }
+    public static GeneratedTheme dark() { return JasperTheme.bundled().generated(true); }
 
-    public static GeneratedTheme light() { return SlateSteel.bundled().generated(false); }
+    public static GeneratedTheme light() { return JasperTheme.bundled().generated(false); }
 
     /** A third appearance: Slate Steel Dark with every role 20% plum (alpha kept), so a hard-coded variant colour shows. */
     public static GeneratedTheme custom() {
diff --git a/jasper-ui/src/test/java/dev/jasper/ui/testsupport/KitFixturesTest.java b/jasper-ui/src/test/java/dev/jasper/ui/testsupport/KitFixturesTest.java
index 7266995f7e6de05c976a5a00cefac73807d419fa..bb595233a674f60826e09f61791fe8ba6e5b8525 100644
--- a/jasper-ui/src/test/java/dev/jasper/ui/testsupport/KitFixturesTest.java
+++ b/jasper-ui/src/test/java/dev/jasper/ui/testsupport/KitFixturesTest.java
@@ -3,13 +3,13 @@ package dev.jasper.ui.testsupport;
 import static org.assertj.core.api.Assertions.assertThat;
 
 import dev.jasper.ui.theme.SemanticColor;
-import dev.jasper.ui.theme.SlateSteel;
+import dev.jasper.ui.theme.JasperTheme;
 import org.junit.jupiter.api.Test;
 
 class KitFixturesTest {
     @Test void theFixturesAreSlateSteelAndCustomDiffersInEveryOpaqueRole() {
-        assertThat(KitFixtures.dark()).isSameAs(SlateSteel.bundled().generated(true));
-        assertThat(KitFixtures.light()).isSameAs(SlateSteel.bundled().generated(false));
+        assertThat(KitFixtures.dark()).isSameAs(JasperTheme.bundled().generated(true));
+        assertThat(KitFixtures.light()).isSameAs(JasperTheme.bundled().generated(false));
         var custom = KitFixtures.custom();
         assertThat(custom.dark()).isTrue();
         for (var role : SemanticColor.values()) {
diff --git a/jasper-ui/src/test/java/dev/jasper/ui/theme/JasperThemeTest.java b/jasper-ui/src/test/java/dev/jasper/ui/theme/JasperThemeTest.java
new file mode 100644
index 0000000000000000000000000000000000000000..24701e6097d910d81ab2dd64ab075957c6363281
--- /dev/null
+++ b/jasper-ui/src/test/java/dev/jasper/ui/theme/JasperThemeTest.java
@@ -0,0 +1,148 @@
+package dev.jasper.ui.theme;
+
+import static org.assertj.core.api.Assertions.*;
+
+import java.awt.Color;
+import java.nio.charset.StandardCharsets;
+import org.junit.jupiter.api.Test;
+
+/** The Jasper theme format (spec section 2): every rule of the file, through the bundled file and small variations. */
+class JasperThemeTest {
+    static String bundledJson() throws Exception {
+        try (var in = JasperThemeTest.class.getResourceAsStream(JasperTheme.RESOURCE)) {
+            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
+        }
+    }
+
+    /** {@code ui.dark}, {@code editor.dark} and the dark and high-contrast schemes removed: a Light-only theme. */
+    static String lightOnly() throws Exception {
+        String json = bundledJson();
+        json = json.replaceFirst("(?s)\"ui\": \\{\\s*\"dark\": \\{.*?},\\s*\"light\"", "\"ui\": {\n    \"light\"");
+        json = json.replaceFirst("(?s)\"editor\": \\{\\s*\"dark\": \\{.*?},\\s*\"light\"", "\"editor\": {\n    \"light\"");
+        json = json.replaceFirst("(?s)\"terminal\": \\{\\s*\"dark\": \\{.*?},\\s*\"light\"", "\"terminal\": {\n    \"light\"");
+        return json.replaceFirst("(?s),\\s*\"high-contrast\": \\{.*?}", "");
+    }
+
+    /** A terminal-only theme with one scheme. */
+    static final String GRUVBOX = """
+        { "jasper-theme": 1, "name": "Gruvbox Dark",
+          "terminal": { "dark": { "name": "Gruvbox Dark", "bg": "#282828", "fg": "#EBDBB2", "fg.bold": "#FBF1C7",
+            "cursor": "#D5C4A1", "selection": "#504945", "black": "#282828", "red": "#CC241D", "green": "#98971A",
+            "yellow": "#D79921", "blue": "#458588", "magenta": "#B16286", "cyan": "#689D6A", "white": "#A89984",
+            "bright.black": "#928374", "bright.red": "#FB4934", "bright.green": "#B8BB26", "bright.yellow": "#FABD2F",
+            "bright.blue": "#83A598", "bright.magenta": "#D3869B", "bright.cyan": "#8EC07C", "bright.white": "#EBDBB2" } } }
+        """;
+
+    static ThemeFileException failure(String json) {
+        return catchThrowableOfType(ThemeFileException.class, () -> JasperTheme.parse(json, "t"));
+    }
+
+    @Test void aTerminalOnlyThemeHasSchemesAndNoVariant() {
+        var theme = JasperTheme.parse(GRUVBOX, "gruvbox-dark");
+        assertThat(theme.name()).isEqualTo("Gruvbox Dark");
+        assertThat(theme.author()).isEmpty();
+        assertThat(theme.has(true)).isFalse();
+        assertThat(theme.has(false)).isFalse();
+        var scheme = theme.schemes().getFirst();
+        assertThat(scheme.id()).isEqualTo(new SchemeId("gruvbox-dark", "dark"));
+        assertThat(scheme.name()).isEqualTo("Gruvbox Dark");
+        assertThat(scheme.colors().background()).isEqualTo(new Color(0x282828));
+        assertThat(scheme.boldForeground()).isEqualTo(new Color(0xFBF1C7));
+        assertThatThrownBy(() -> theme.generated(true)).isInstanceOf(IllegalStateException.class).hasMessage("Gruvbox Dark has no Dark variant");
+        assertThatThrownBy(() -> theme.matching(false)).isInstanceOf(IllegalStateException.class);
+    }
+
+    @Test void aLightOnlyThemeOffersOnlyLight() throws Exception {
+        var theme = JasperTheme.parse(lightOnly(), "dawn");
+        assertThat(theme.has(true)).isFalse();
+        assertThat(theme.has(false)).isTrue();
+        assertThat(theme.schemes()).extracting(scheme -> scheme.id().key()).containsExactly("light");
+        assertThat(theme.generated(false).terminal()).isEqualTo(theme.matching(false).colors());
+        assertThat(theme.generated(false).name()).isEqualTo("Slate Steel");
+    }
+
+    @Test void anEditorEntryMayBeALiteralColourWithStyles() throws Exception {
+        String json = bundledJson().replaceFirst("\"keyword\": \"terminal.ansi.magenta\"", "\"keyword\": \"#FF79C6, italic, bold\"")
+            .replaceFirst("\"gutter\": \"terminal.bg\"", "\"gutter\": \"#101010\"");
+        var theme = JasperTheme.parse(json, "t");
+        assertThat(theme.syntax(true).get(SyntaxRole.KEYWORD)).isEqualTo(new SyntaxStyle(new Color(0xFF79C6), true, true));
+        assertThat(theme.editor(true).gutter()).isEqualTo(new Color(0x101010));
+        assertThat(theme.syntax(false).get(SyntaxRole.KEYWORD)).as("only the Dark entry changed").isEqualTo(JasperTheme.bundled().syntax(false).get(SyntaxRole.KEYWORD));
+        assertThat(failure(bundledJson().replaceFirst("\"keyword\": \"terminal.ansi.magenta\"", "\"keyword\": \"#FF79C680\"")))
+            .hasMessageContaining("editor.dark.keyword: Expected #RRGGBB or a reference, got \"#FF79C680\"");
+        assertThat(failure(bundledJson().replaceFirst("\"gutter\": \"terminal.bg\"", "\"gutter\": \"#101010, bold\"")))
+            .hasMessageContaining("editor.dark.gutter: A surface takes no style");
+    }
+
+    @Test void theMarkerNameAndAuthorAreChecked() throws Exception {
+        String json = bundledJson();
+        assertThat(failure(json.replace("\"jasper-theme\": 1, ", ""))).hasMessage("t.json: Not a Jasper theme: \"jasper-theme\": 1 is missing");
+        assertThat(failure(json.replace("\"jasper-theme\": 1,", "\"jasper-theme\": 2,")))
+            .hasMessageContaining("jasper-theme: Format 2 is not one this Jasper reads; expected 1");
+        assertThat(failure(json.replace("\"jasper-theme\": 1,", "\"jasper-theme\": \"1\","))).hasMessageContaining("jasper-theme: Expected the number 1");
+        assertThat(failure(json.replace("\"name\": \"Slate Steel\", ", ""))).hasMessageContaining("Missing entry \"name\"");
+        assertThat(failure(json.replace("\"name\": \"Slate Steel\"", "\"name\": \" \""))).hasMessageContaining("name: Expected a non-empty string");
+        assertThat(failure(json.replace("\"author\": \"Jasper\"", "\"author\": {}"))).hasMessageContaining("author: Expected a string");
+        assertThat(failure(json.replace("\"author\": \"Jasper\",", "\"author\": \"Jasper\", \"extra\": \"x\","))).hasMessageContaining("Unknown entry \"extra\"");
+    }
+
+    @Test void aVariantNeedsItsMatchingSchemeAndEditorAndNothingElse() throws Exception {
+        String json = bundledJson();
+        assertThat(failure(json.replace("\"light\": {\n      \"surface.app\"", "\"pale\": {\n      \"surface.app\"")))
+            .hasMessageContaining("ui: Unknown variant \"pale\"");
+        assertThat(failure(json.replace("\"light\": {\n      \"name\": \"Slate Steel Light\"", "\"pale\": {\n      \"name\": \"Slate Steel Light\"")))
+            .hasMessageContaining("ui.light: Missing its matching scheme \"terminal.light\"");
+        assertThat(failure(json.replaceFirst("(?s)\"editor\": \\{.*}\\s*}\\s*$", "\"x\": \"y\" }"))).hasMessageContaining("Unknown entry \"x\"");
+        assertThat(failure(GRUVBOX.replace("\"name\": \"Gruvbox Dark\",\n", "\"name\": \"Gruvbox Dark\", \"ui\": {},\n")))
+            .hasMessageContaining("ui: Expected \"dark\", \"light\" or both");
+        assertThat(failure(GRUVBOX.replace("\"name\": \"Gruvbox Dark\",\n", "\"name\": \"Gruvbox Dark\", \"editor\": {},\n")))
+            .hasMessageContaining("editor: A theme without ui has no editor schemes");
+        assertThat(failure(lightOnly().replace("\"editor\": {\n    \"light\"", "\"editor\": {\n    \"dark\": {}, \"light\"")))
+            .hasMessageContaining("editor: Unknown variant \"dark\"");
+    }
+
+    @Test void schemesNeedAKeyANameAndExactlyTheirColours() throws Exception {
+        assertThat(failure(GRUVBOX.replace("\"dark\": {", "\"Dark_1\": {"))).hasMessageContaining(
+            "terminal: Scheme key \"Dark_1\" must be lowercase letters, digits and dashes, at most 32 characters");
+        assertThat(failure(GRUVBOX.replace("\"name\": \"Gruvbox Dark\", \"bg\"", "\"bg\""))).hasMessageContaining("terminal.dark: Missing entry \"name\"");
+        assertThat(failure(GRUVBOX.replace("\"name\": \"Gruvbox Dark\", \"bg\"", "\"name\": \"\", \"bg\""))).hasMessageContaining("terminal.dark.name: Expected a non-empty string");
+        assertThat(failure(GRUVBOX.replace("\"fg.bold\": \"#FBF1C7\",", ""))).hasMessageContaining("terminal.dark: Missing colour \"fg.bold\"");
+        assertThat(failure(GRUVBOX.replace("\"bg\":", "\"bgx\": \"#000000\", \"bg\":"))).hasMessageContaining("terminal.dark: Unknown colour \"bgx\"");
+        assertThat(failure(GRUVBOX.replace("\"terminal\": { \"dark\"", "\"terminal\": {}, \"x\": { \"dark\""))).hasMessageContaining("Unknown entry \"x\"");
+        assertThat(failure("{ \"jasper-theme\": 1, \"name\": \"Empty\", \"terminal\": {} }")).hasMessageContaining("terminal: Expected at least one scheme");
+        assertThat(failure("{ \"jasper-theme\": 1, \"name\": \"None\" }")).hasMessageContaining("Missing object \"terminal\"");
+    }
+
+    @Test void aFileTheEngineCannotUseFailsNamingTheKey() throws Exception {
+        String json = bundledJson();
+        assertThat(failure(json.replace("\"surface.app\": \"#1E2127\",", "\"surface.app\": \"#1E2127\", \"surface.nope\": \"#000000\",")))
+            .hasMessageContaining("ui.dark: Unknown colour \"surface.nope\"");
+        assertThat(failure(json.replace("\"surface.panel\": \"#272A31\", ", ""))).hasMessageContaining("ui.dark: Missing colour \"surface.panel\"");
+        assertThat(failure(json.replace("\"#1E2127\"", "\"#1E21\""))).hasMessageContaining("surface.app").hasMessageContaining("#RRGGBB");
+        assertThat(failure(json.replaceFirst("\"caret\": \"[^\"]+\",\\s*", ""))).hasMessageContaining("editor.dark: Missing entry \"caret\"");
+        assertThat(failure(json.replaceFirst("\"keyword\": \"terminal.ansi.magenta\"", "\"keyword\": \"terminal.ansi.pink\"")))
+            .hasMessageContaining("editor.dark.keyword").hasMessageContaining("terminal.ansi.pink");
+        assertThat(failure(json.replaceFirst("\"currentLine\": \"hover.overlay @ 5%\"", "\"currentLine\": \"hover.overlay @ 105%\"")))
+            .hasMessageContaining("currentLine");
+        assertThat(failure(json.replaceFirst("\"background\": \"terminal.bg\"", "\"background\": \"terminal.bg @ 50%\""))).hasMessageContaining("background");
+        assertThat(failure(json.replaceFirst("terminal.ansi.bright.black, italic", "terminal.ansi.bright.black, wavy"))).hasMessageContaining("wavy");
+        assertThat(failure(GRUVBOX.replace("\"name\": \"Gruvbox Dark\",\n", "\"name\": \"Gruvbox Dark\", \"ui\": \"x\",\n")))
+            .hasMessageContaining(": ui: Expected an object");
+    }
+
+    @Test void theDetailOmitsTheFileForCallersThatShowItSeparately() throws Exception {
+        var failure = failure(bundledJson().replace("\"surface.panel\": \"#272A31\", ", ""));
+        assertThat(failure.origin()).isEqualTo("t.json");
+        assertThat(failure.detail()).isEqualTo("ui.dark: Missing colour \"surface.panel\"");
+        assertThat(failure.getMessage()).isEqualTo("t.json: " + failure.detail());
+    }
+
+    @Test void idsAreLowercaseWordsOfAtMost64Characters() {
+        assertThat(JasperTheme.validId("ocean-dawn-2")).isTrue();
+        assertThat(JasperTheme.validId("x".repeat(64))).isTrue();
+        assertThat(JasperTheme.validId("x".repeat(65))).isFalse();
+        for (String bad : new String[] {"", "Ocean", "ocean_dawn", "-ocean", "ocean-", "ocean--dawn", "oc ean", null})
+            assertThat(JasperTheme.validId(bad)).as(bad).isFalse();
+        assertThatThrownBy(() -> JasperTheme.parse(GRUVBOX, "Gruvbox")).isInstanceOf(IllegalArgumentException.class);
+    }
+}
diff --git a/jasper-ui/src/test/java/dev/jasper/ui/theme/SchemeIdTest.java b/jasper-ui/src/test/java/dev/jasper/ui/theme/SchemeIdTest.java
new file mode 100644
index 0000000000000000000000000000000000000000..105910984d86105f825f08ec8da837306bdd75f3
--- /dev/null
+++ b/jasper-ui/src/test/java/dev/jasper/ui/theme/SchemeIdTest.java
@@ -0,0 +1,22 @@
+package dev.jasper.ui.theme;
+
+import static org.assertj.core.api.Assertions.*;
+
+import org.junit.jupiter.api.Test;
+
+class SchemeIdTest {
+    @Test void anIdIsAThemeIdAndASchemeKey() {
+        var id = new SchemeId("slate-steel", "high-contrast");
+        assertThat(id.toString()).isEqualTo("slate-steel/high-contrast");
+        assertThat(SchemeId.parse("slate-steel/high-contrast")).contains(id);
+        assertThat(SchemeId.validKey("k".repeat(32))).isTrue();
+        assertThat(SchemeId.validKey("k".repeat(33))).isFalse();
+    }
+
+    @Test void anythingElseIsNotAnId() {
+        for (String bad : new String[] {"high-contrast", "slate-steel/", "/dark", "Slate/dark", "slate-steel/dark/x", "a//b", "", null})
+            assertThat(SchemeId.parse(bad)).as(bad).isEmpty();
+        assertThatThrownBy(() -> new SchemeId("slate-steel", "Dark")).isInstanceOf(IllegalArgumentException.class);
+        assertThatThrownBy(() -> new SchemeId("Slate", "dark")).isInstanceOf(IllegalArgumentException.class);
+    }
+}
diff --git a/jasper-ui/src/test/java/dev/jasper/ui/theme/SemanticPaletteTest.java b/jasper-ui/src/test/java/dev/jasper/ui/theme/SemanticPaletteTest.java
index 6195d53e329157d0ddfc651aa87908a80b6b8275..68cf71030838c3a988c7381f5dfcde7f5eed5efa 100644
--- a/jasper-ui/src/test/java/dev/jasper/ui/theme/SemanticPaletteTest.java
+++ b/jasper-ui/src/test/java/dev/jasper/ui/theme/SemanticPaletteTest.java
@@ -13,7 +13,7 @@ class SemanticPaletteTest {
 
     @Test void authoredSharedTextRemainsRawWhileDedicatedSurfacesApplyTheirFloors() {
         var values = new EnumMap<SemanticColor, Color>(SemanticColor.class);
-        var light = SlateSteel.bundled().palette(false);
+        var light = JasperTheme.bundled().palette(false);
         for (var role : SemanticColor.values()) values.put(role, light.color(role));
         values.putAll(Map.of(SemanticColor.TEXT_PLACEHOLDER, new Color(0xeeeeee), SemanticColor.TEXT_SECONDARY, new Color(0xeeeeee),
             SemanticColor.SURFACE_INPUT, Color.WHITE, SemanticColor.SURFACE_CARD, Color.WHITE));
@@ -54,7 +54,7 @@ class SemanticPaletteTest {
     /** The selected tab (the terminal surface) and the strip behind the inactive tabs are distinguishable in both variants. */
     @Test void theSelectedTabAndTheStripDifferInBothVariants() {
         for (boolean dark : new boolean[]{true, false}) {
-            var p = SlateSteel.bundled().generated(dark).semantic();
+            var p = JasperTheme.bundled().generated(dark).semantic();
             assertThat(ColorMath.luminance(p.color(SemanticColor.SURFACE_TERMINAL))).as(dark ? "dark" : "light")
                 .isNotEqualTo(ColorMath.luminance(p.color(SemanticColor.SURFACE_TABSTRIP)));
         }
diff --git a/jasper-ui/src/test/java/dev/jasper/ui/theme/SlateSteelEditorTest.java b/jasper-ui/src/test/java/dev/jasper/ui/theme/SlateSteelEditorTest.java
index efa2b9d3d06983098bee1c3cd099530ca5447b5c..7b7e2288f704e6568957d45d962843b9108034fd 100644
--- a/jasper-ui/src/test/java/dev/jasper/ui/theme/SlateSteelEditorTest.java
+++ b/jasper-ui/src/test/java/dev/jasper/ui/theme/SlateSteelEditorTest.java
@@ -7,27 +7,27 @@ import org.junit.jupiter.api.Test;
 
 class SlateSteelEditorTest {
     @Test void theEditorSchemesAreTheirVariantsTokens() {
-        var dark = SlateSteel.bundled().editor(true);
+        var dark = JasperTheme.bundled().editor(true);
         assertThat(dark.background()).isEqualTo(new Color(0x23262C)).isEqualTo(dark.gutter());
         assertThat(dark.foreground()).isEqualTo(new Color(0xF4F6F8));
         assertThat(dark.caret()).isEqualTo(Color.WHITE);
         assertThat(dark.selection()).isEqualTo(new Color(0x3D5F8A));
         assertThat(dark.currentLine()).isEqualTo(ColorMath.mix(Color.WHITE, new Color(0x23262C), .05));
         assertThat(dark.lineNumbers()).isEqualTo(new Color(0x8B919C));
-        var syntax = SlateSteel.bundled().syntax(true);
+        var syntax = JasperTheme.bundled().syntax(true);
         assertThat(syntax).containsOnlyKeys(SyntaxRole.values());
         assertThat(syntax.get(SyntaxRole.KEYWORD)).isEqualTo(new SyntaxStyle(new Color(0xE99CFF), false, false));
         assertThat(syntax.get(SyntaxRole.COMMENT)).isEqualTo(new SyntaxStyle(new Color(0x99A2B1), false, true));
         assertThat(syntax.get(SyntaxRole.OPERATOR).color()).isEqualTo(new Color(0xA7ADB8));
-        var light = SlateSteel.bundled().syntax(false);
+        var light = JasperTheme.bundled().syntax(false);
         assertThat(light.get(SyntaxRole.DOC_COMMENT)).isEqualTo(new SyntaxStyle(new Color(0x5D6470), false, true));
-        assertThat(SlateSteel.bundled().editor(false).currentLine()).isEqualTo(ColorMath.mix(Color.BLACK, new Color(0xFBFBFC), .05));
+        assertThat(JasperTheme.bundled().editor(false).currentLine()).isEqualTo(ColorMath.mix(Color.BLACK, new Color(0xFBFBFC), .05));
     }
 
     @Test void everyRoleReadsOnItsBackground() {
         for (boolean dark : new boolean[] {true, false}) {
-            var editor = SlateSteel.bundled().editor(dark);
-            SlateSteel.bundled().syntax(dark).forEach((role, style) -> {
+            var editor = JasperTheme.bundled().editor(dark);
+            JasperTheme.bundled().syntax(dark).forEach((role, style) -> {
                 boolean comment = role == SyntaxRole.COMMENT || role == SyntaxRole.DOC_COMMENT;
                 assertThat(ColorMath.contrast(style.color(), editor.background())).as("%s %s", dark, role)
                     .isGreaterThanOrEqualTo(comment ? 3 : 4.5);
@@ -36,16 +36,4 @@ class SlateSteelEditorTest {
             assertThat(ColorMath.contrast(editor.lineNumbers(), editor.background())).isGreaterThanOrEqualTo(3);
         }
     }
-
-    @Test void aReferenceTheEngineCannotResolveFailsLoading() throws Exception {
-        String json = SlateSteelPaletteTest.bundledJson();
-        assertThatThrownBy(() -> SlateSteel.parse(json.replaceFirst("\"keyword\": \"terminal.ansi.magenta\"", "\"keyword\": \"terminal.ansi.pink\""), "t.json"))
-            .isInstanceOf(ThemeFileException.class).hasMessageContaining("editor.slate-steel-dark.keyword").hasMessageContaining("terminal.ansi.pink");
-        assertThatThrownBy(() -> SlateSteel.parse(json.replaceFirst("\"currentLine\": \"hover.overlay @ 5%\"", "\"currentLine\": \"hover.overlay @ 105%\""), "t.json"))
-            .isInstanceOf(ThemeFileException.class).hasMessageContaining("currentLine");
-        assertThatThrownBy(() -> SlateSteel.parse(json.replaceFirst("\"background\": \"terminal.bg\"", "\"background\": \"terminal.bg @ 50%\""), "t.json"))
-            .isInstanceOf(ThemeFileException.class).hasMessageContaining("background");
-        assertThatThrownBy(() -> SlateSteel.parse(json.replaceFirst("terminal.ansi.bright.black, italic", "terminal.ansi.bright.black, wavy"), "t.json"))
-            .isInstanceOf(ThemeFileException.class).hasMessageContaining("wavy");
-    }
 }
diff --git a/jasper-ui/src/test/java/dev/jasper/ui/theme/SlateSteelFileTest.java b/jasper-ui/src/test/java/dev/jasper/ui/theme/SlateSteelFileTest.java
index c827c7ccc9f1dc223310efda3532b4aa775a5b7d..bebb2fe9811e925fd9ddd647daec408fdcc75f32 100644
--- a/jasper-ui/src/test/java/dev/jasper/ui/theme/SlateSteelFileTest.java
+++ b/jasper-ui/src/test/java/dev/jasper/ui/theme/SlateSteelFileTest.java
@@ -24,6 +24,6 @@ class SlateSteelFileTest {
             bundled = new String(in.readAllBytes(), StandardCharsets.UTF_8);
         }
         assertThat(bundled.strip()).isEqualTo(json.strip());
-        assertThat(json).contains("\"editor\"", "\"slate-steel-dark\"", "\"high-contrast\"");
+        assertThat(json).contains("\"jasper-theme\": 1", "\"editor\"", "\"high-contrast\"");
     }
 }
diff --git a/jasper-ui/src/test/java/dev/jasper/ui/theme/SlateSteelGeneratedTest.java b/jasper-ui/src/test/java/dev/jasper/ui/theme/SlateSteelGeneratedTest.java
index a401b72f695fdf37d1ab7fbcac96659698577d0d..401e1b8e7bb59e083c1ff70c572343fd5f5a9605 100644
--- a/jasper-ui/src/test/java/dev/jasper/ui/theme/SlateSteelGeneratedTest.java
+++ b/jasper-ui/src/test/java/dev/jasper/ui/theme/SlateSteelGeneratedTest.java
@@ -7,13 +7,14 @@ import org.junit.jupiter.api.Test;
 class SlateSteelGeneratedTest {
     @Test void eachVariantIsACompleteGeneratedTheme() {
         for (boolean dark : new boolean[] {true, false}) {
-            var steel = SlateSteel.bundled();
+            var steel = JasperTheme.bundled();
             var theme = steel.generated(dark);
             assertThat(theme).isSameAs(steel.generated(dark));
             assertThat(theme.name()).isEqualTo("Slate Steel");
             assertThat(theme.dark()).isEqualTo(dark);
             assertThat(theme.semantic()).isEqualTo(steel.palette(dark));
-            assertThat(theme.terminal()).isEqualTo(steel.terminal(TerminalScheme.matching(dark)).colors());
+            assertThat(theme.terminal()).isEqualTo(steel.matching(dark).colors());
+            assertThat(theme.author()).isEqualTo("Jasper");
             assertThat(theme.syntax()).isEqualTo(steel.syntax(dark));
             assertThat(theme.glass()).isEqualTo(GlassLevels.OPAQUE);
             assertThat(theme.editor()).isEqualTo(steel.editor(dark));
diff --git a/jasper-ui/src/test/java/dev/jasper/ui/theme/SlateSteelPaletteTest.java b/jasper-ui/src/test/java/dev/jasper/ui/theme/SlateSteelPaletteTest.java
index 6f63e8395601a0bb001a10658bbf49693af4f65b..81a9e8dc0292efcf5bf624971ba391cb973182a3 100644
--- a/jasper-ui/src/test/java/dev/jasper/ui/theme/SlateSteelPaletteTest.java
+++ b/jasper-ui/src/test/java/dev/jasper/ui/theme/SlateSteelPaletteTest.java
@@ -3,19 +3,12 @@ package dev.jasper.ui.theme;
 import static org.assertj.core.api.Assertions.*;
 
 import java.awt.Color;
-import java.nio.charset.StandardCharsets;
 import org.junit.jupiter.api.Test;
 
 class SlateSteelPaletteTest {
-    static String bundledJson() throws Exception {
-        try (var in = SlateSteelPaletteTest.class.getResourceAsStream(SlateSteel.RESOURCE)) {
-            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
-        }
-    }
-
     @Test void bothVariantsCoverEveryRoleByValueOrRule() {
         for (boolean dark : new boolean[] {true, false}) {
-            var palette = SlateSteel.bundled().palette(dark);
+            var palette = JasperTheme.bundled().palette(dark);
             for (var role : SemanticColor.values()) assertThat(palette.color(role)).as("%s %s", dark, role.key()).isNotNull();
             assertThat(palette.color(SemanticColor.BORDER_FOCUS)).isEqualTo(palette.color(SemanticColor.ACCENT));
             assertThat(palette.color(SemanticColor.SURFACE_STATUS)).isEqualTo(palette.color(SemanticColor.SURFACE_APP));
@@ -26,7 +19,7 @@ class SlateSteelPaletteTest {
     }
 
     @Test void valuesAreTheFilesIncludingTheirAlpha() {
-        var dark = SlateSteel.bundled().palette(true); var light = SlateSteel.bundled().palette(false);
+        var dark = JasperTheme.bundled().palette(true); var light = JasperTheme.bundled().palette(false);
         assertThat(dark.color(SemanticColor.SURFACE_APP)).isEqualTo(new Color(0x1E2127));
         assertThat(light.color(SemanticColor.SURFACE_APP)).isEqualTo(new Color(0xE4E7EB));
         assertThat(dark.color(SemanticColor.SURFACE_SCRIM)).isEqualTo(new Color(0x0A, 0x0C, 0x10, 0x8C));
@@ -38,34 +31,12 @@ class SlateSteelPaletteTest {
         assertThat(light.color(SemanticColor.TEXT_STRONG_SECONDARY)).isEqualTo(new Color(0x383E48));
     }
 
-    @Test void aFileTheEngineCannotUseFailsNamingTheKey() throws Exception {
-        String json = bundledJson();
-        assertThatThrownBy(() -> SlateSteel.parse(json.replace("\"surface.app\": \"#1E2127\",", "\"surface.app\": \"#1E2127\", \"surface.nope\": \"#000000\","), "t.json"))
-            .isInstanceOf(ThemeFileException.class).hasMessageContaining("ui.slate-steel-dark").hasMessageContaining("Unknown colour \"surface.nope\"");
-        assertThatThrownBy(() -> SlateSteel.parse(json.replace("\"surface.panel\": \"#272A31\", ", ""), "t.json"))
-            .isInstanceOf(ThemeFileException.class).hasMessageContaining("Missing colour \"surface.panel\"");
-        assertThatThrownBy(() -> SlateSteel.parse(json.replace("\"#1E2127\"", "\"#1E21\""), "t.json"))
-            .isInstanceOf(ThemeFileException.class).hasMessageContaining("surface.app").hasMessageContaining("#RRGGBB");
-        assertThatThrownBy(() -> SlateSteel.parse(json.replace("\"slate-steel-light\": {\n      \"surface.app\"", "\"slate-steel-pale\": {\n      \"surface.app\""), "t.json"))
-            .isInstanceOf(ThemeFileException.class).hasMessageContaining("ui");
-        assertThatThrownBy(() -> SlateSteel.parse("{ \"ui\": {} }", "t.json")).isInstanceOf(ThemeFileException.class)
-            .hasMessageContaining("Missing object \"terminal\"");
-    }
-
-    @Test void aTopLevelEntryThatIsNotAnObjectIsNamedWithoutALeadingDot() throws Exception {
-        assertThatThrownBy(() -> SlateSteel.parse("{ \"ui\": \"x\", \"terminal\": {}, \"editor\": {} }", "t.json"))
-            .isInstanceOf(ThemeFileException.class).hasMessageContaining(": ui: Expected an object").hasMessageNotContaining(".ui");
-    }
-
-    @Test void aMissingOrUnknownSchemeOrEditorEntryIsNamed() throws Exception {
-        String json = bundledJson();
-        assertThatThrownBy(() -> SlateSteel.parse(json.replaceFirst("\"fg\\.bold\": \"#[0-9A-Fa-f]+\",\\s*", ""), "t.json"))
-            .hasMessageContaining("terminal.slate-steel-dark: Missing colour \"fg.bold\"");
-        assertThatThrownBy(() -> SlateSteel.parse(json.replaceFirst("\"bg\":", "\"bgx\": \"#000000\", \"bg\":"), "t.json"))
-            .hasMessageContaining("terminal.slate-steel-dark: Unknown colour \"bgx\"");
-        assertThatThrownBy(() -> SlateSteel.parse(json.replaceFirst("\"caret\": \"[^\"]+\",\\s*", ""), "t.json"))
-            .hasMessageContaining("editor.slate-steel-dark: Missing entry \"caret\"");
-        assertThatThrownBy(() -> SlateSteel.parse(json.replaceFirst("\"editor\":", "\"extra\": {}, \"editor\":"), "t.json"))
-            .hasMessageContaining("Unknown object \"extra\"");
+    @Test void theBundledThemeIsSlateSteelByJasperInBothVariants() {
+        var steel = JasperTheme.bundled();
+        assertThat(steel.id()).isEqualTo(JasperTheme.BUNDLED_ID).isEqualTo("slate-steel");
+        assertThat(steel.name()).isEqualTo("Slate Steel");
+        assertThat(steel.author()).isEqualTo("Jasper");
+        assertThat(steel.has(true)).isTrue();
+        assertThat(steel.has(false)).isTrue();
     }
 }
diff --git a/jasper-ui/src/test/java/dev/jasper/ui/theme/SlateSteelTerminalTest.java b/jasper-ui/src/test/java/dev/jasper/ui/theme/SlateSteelTerminalTest.java
index fdb3584d4c43f67743c48ce6d77349422cc1112e..d9c984e6fcff6b1cc66c7e051f4cd1ab0f74b115 100644
--- a/jasper-ui/src/test/java/dev/jasper/ui/theme/SlateSteelTerminalTest.java
+++ b/jasper-ui/src/test/java/dev/jasper/ui/theme/SlateSteelTerminalTest.java
@@ -8,28 +8,31 @@ import java.util.Set;
 import org.junit.jupiter.api.Test;
 
 class SlateSteelTerminalTest {
+    static final TerminalScheme DARK = JasperTheme.bundled().matching(true), LIGHT = JasperTheme.bundled().matching(false),
+        HIGH_CONTRAST = JasperTheme.bundled().scheme("high-contrast").orElseThrow();
+
     @Test void eachSchemeIsItsPaletteInAnsiOrder() {
-        var dark = SlateSteel.bundled().terminal(TerminalScheme.SLATE_STEEL_DARK);
-        assertThat(dark.colors().background()).isEqualTo(new Color(0x23262C));
-        assertThat(dark.colors().foreground()).isEqualTo(new Color(0xF4F6F8));
-        assertThat(dark.colors().cursor()).isEqualTo(Color.WHITE);
-        assertThat(dark.colors().selection()).isEqualTo(new Color(0x3D5F8A));
-        assertThat(dark.boldForeground()).isEqualTo(Color.WHITE);
-        assertThat(dark.colors().ansi().get(1)).isEqualTo(new Color(0xFF928A));
-        assertThat(dark.colors().ansi().get(8)).isEqualTo(new Color(0x99A2B1));
-        assertThat(dark.colors().ansi().get(15)).isEqualTo(Color.WHITE);
-        var light = SlateSteel.bundled().terminal(TerminalScheme.SLATE_STEEL_LIGHT);
-        assertThat(light.boldForeground()).isEqualTo(Color.BLACK);
-        assertThat(light.colors().ansi().get(7)).isEqualTo(new Color(0x5D6470));
-        assertThat(SlateSteel.bundled().terminal(TerminalScheme.HIGH_CONTRAST).colors().background()).isEqualTo(Color.BLACK);
-        assertThat(SlateSteel.bundled().palette(true).color(SemanticColor.SURFACE_TERMINAL)).isEqualTo(dark.colors().background());
+        assertThat(DARK.colors().background()).isEqualTo(new Color(0x23262C));
+        assertThat(DARK.colors().foreground()).isEqualTo(new Color(0xF4F6F8));
+        assertThat(DARK.colors().cursor()).isEqualTo(Color.WHITE);
+        assertThat(DARK.colors().selection()).isEqualTo(new Color(0x3D5F8A));
+        assertThat(DARK.boldForeground()).isEqualTo(Color.WHITE);
+        assertThat(DARK.colors().ansi().get(1)).isEqualTo(new Color(0xFF928A));
+        assertThat(DARK.colors().ansi().get(8)).isEqualTo(new Color(0x99A2B1));
+        assertThat(DARK.colors().ansi().get(15)).isEqualTo(Color.WHITE);
+        assertThat(LIGHT.boldForeground()).isEqualTo(Color.BLACK);
+        assertThat(LIGHT.colors().ansi().get(7)).isEqualTo(new Color(0x5D6470));
+        assertThat(HIGH_CONTRAST.colors().background()).isEqualTo(Color.BLACK);
+        assertThat(JasperTheme.bundled().palette(true).color(SemanticColor.SURFACE_TERMINAL)).isEqualTo(DARK.colors().background());
     }
 
-    @Test void schemesHaveKeysAndFollowTheUiVariantByDefault() {
-        assertThat(TerminalScheme.of("high-contrast")).contains(TerminalScheme.HIGH_CONTRAST);
-        assertThat(TerminalScheme.of("nord")).isEmpty();
-        assertThat(TerminalScheme.matching(true)).isEqualTo(TerminalScheme.SLATE_STEEL_DARK);
-        assertThat(TerminalScheme.matching(false)).isEqualTo(TerminalScheme.SLATE_STEEL_LIGHT);
+    @Test void theSchemesHaveIdsAndNamesInTheFilesOrder() {
+        assertThat(JasperTheme.bundled().schemes()).containsExactly(DARK, LIGHT, HIGH_CONTRAST);
+        assertThat(JasperTheme.bundled().schemes()).extracting(scheme -> scheme.id().toString())
+            .containsExactly("slate-steel/dark", "slate-steel/light", "slate-steel/high-contrast");
+        assertThat(JasperTheme.bundled().schemes()).extracting(TerminalScheme::name)
+            .containsExactly("Slate Steel Dark", "Slate Steel Light", "High Contrast");
+        assertThat(JasperTheme.bundled().scheme("nord")).isEmpty();
     }
 
     /**
@@ -40,15 +43,15 @@ class SlateSteelTerminalTest {
     @Test void eachSchemeMeetsItsContrastTargetsWithTheNamedExceptions() {
         record Target(double normal, double bright, Set<Integer> exempt) { }
         var targets = Map.of(
-            TerminalScheme.SLATE_STEEL_DARK, new Target(7, 9.5, Set.of(0, 8)),
-            TerminalScheme.SLATE_STEEL_LIGHT, new Target(4.9, 4.5, Set.of(6)),
-            TerminalScheme.HIGH_CONTRAST, new Target(8, 12, Set.of(0, 8, 9)));
+            DARK, new Target(7, 9.5, Set.of(0, 8)),
+            LIGHT, new Target(4.9, 4.5, Set.of(6)),
+            HIGH_CONTRAST, new Target(8, 12, Set.of(0, 8, 9)));
         targets.forEach((scheme, target) -> {
-            var colors = SlateSteel.bundled().terminal(scheme).colors();
+            var colors = scheme.colors();
             for (int i = 0; i < 16; i++) {
                 double ratio = ColorMath.contrast(colors.ansi().get(i), colors.background()), floor = i < 8 ? target.normal() : target.bright();
-                if (target.exempt().contains(i)) assertThat(ratio).as("%s ansi %s is a named exception", scheme, i).isLessThan(floor);
-                else assertThat(ratio).as("%s ansi %s", scheme, i).isGreaterThanOrEqualTo(floor);
+                if (target.exempt().contains(i)) assertThat(ratio).as("%s ansi %s is a named exception", scheme.name(), i).isLessThan(floor);
+                else assertThat(ratio).as("%s ansi %s", scheme.name(), i).isGreaterThanOrEqualTo(floor);
             }
         });
     }
diff --git a/jasper-ui/src/test/java/dev/jasper/ui/theme/ThemeJsonTest.java b/jasper-ui/src/test/java/dev/jasper/ui/theme/ThemeJsonTest.java
index 88ca1da5f358fa82f3c1262f6eca1f2531ebd884..d66e106aadb60852450ab7a4f5c0c7d01c9fb979 100644
--- a/jasper-ui/src/test/java/dev/jasper/ui/theme/ThemeJsonTest.java
+++ b/jasper-ui/src/test/java/dev/jasper/ui/theme/ThemeJsonTest.java
@@ -17,10 +17,19 @@ class ThemeJsonTest {
         assertThat(root.get("z")).isEqualTo(Map.of());
     }
 
+    @Test void readsWholeNumbers() {
+        assertThat(ThemeJson.parse("{ \"jasper-theme\": 1, \"n\": 120 }", "t.json")).containsExactly(Map.entry("jasper-theme", 1L), Map.entry("n", 120L));
+    }
+
     @Test void rejectsWhatTheThemeFileNeverHoldsNamingTheOriginAndLine() {
         for (var bad : List.of(
-                Map.entry("{ \"a\": 1 }", "Expected a string or an object for \"a\""),
-                Map.entry("{ \"a\": [\"x\"] }", "Expected a string or an object for \"a\""),
+                Map.entry("{ \"a\": 1.5 }", "Expected a whole number"),
+                Map.entry("{ \"a\": 1e3 }", "Expected a whole number"),
+                Map.entry("{ \"a\": 01 }", "A number has no leading zeros"),
+                Map.entry("{ \"a\": 1234567890 }", "A number of at most nine digits"),
+                Map.entry("{ \"a\": -1 }", "Expected a string, an object or a whole number for \"a\""),
+                Map.entry("{ \"a\": true }", "Expected a string, an object or a whole number for \"a\""),
+                Map.entry("{ \"a\": [\"x\"] }", "Expected a string, an object or a whole number for \"a\""),
                 Map.entry("{ \"a\": \"x\",\n  \"a\": \"y\" }", "t.json:2: Duplicate key \"a\""),
                 Map.entry("{ \"a\": \"x\" } x", "Unexpected content after the theme object"),
                 Map.entry("{ \"a\": \"x\" ", "Unexpected end"),
@@ -34,7 +43,7 @@ class ThemeJsonTest {
     @Test void readsTheBundledFile() throws Exception {
         try (var in = ThemeJsonTest.class.getResourceAsStream("/dev/jasper/ui/theme/slate-steel.json")) {
             var root = ThemeJson.parse(new String(in.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8), "slate-steel.json");
-            assertThat(root.keySet()).containsExactly("ui", "terminal", "editor");
+            assertThat(root.keySet()).containsExactly("jasper-theme", "name", "author", "ui", "terminal", "editor");
         }
     }
 }
diff --git a/jasper-ui/src/test/java/dev/jasper/ui/theme/UiColorKeysTest.java b/jasper-ui/src/test/java/dev/jasper/ui/theme/UiColorKeysTest.java
index 8e48b37fe06b44425db21909b373e561a8572581..0b3cfa139b38b661aae3ca66a29308ee9a041af7 100644
--- a/jasper-ui/src/test/java/dev/jasper/ui/theme/UiColorKeysTest.java
+++ b/jasper-ui/src/test/java/dev/jasper/ui/theme/UiColorKeysTest.java
@@ -45,8 +45,8 @@ class UiColorKeysTest {
 
     @Test void everyKeyIsARolesColour() {
         for (boolean dark : new boolean[]{true, false}) {
-            var p = SlateSteel.bundled().palette(dark);
-            var selection = SlateSteel.bundled().terminal(TerminalScheme.matching(dark)).colors().selection();
+            var p = JasperTheme.bundled().palette(dark);
+            var selection = JasperTheme.bundled().matching(dark).colors().selection();
             var allowed = new HashSet<Object>();
             for (var role : SemanticColor.values()) allowed.add(ColorMath.hex(p.color(role)));
             allowed.add(ColorMath.hex(selection)); allowed.add(ColorMath.hex(UiColorKeys.errorTint(p)));
@@ -57,8 +57,8 @@ class UiColorKeysTest {
 
     @Test void textSelectionAndTheSearchTintKeepTheirSources() {
         for (boolean dark : new boolean[]{true, false}) {
-            var p = SlateSteel.bundled().palette(dark);
-            var selection = SlateSteel.bundled().terminal(TerminalScheme.matching(dark)).colors().selection();
+            var p = JasperTheme.bundled().palette(dark);
+            var selection = JasperTheme.bundled().matching(dark).colors().selection();
             var keys = UiColorKeys.flatLaf(p, selection);
             assertThat(keys.get("*.selectionBackground")).isEqualTo(ColorMath.hex(selection));
             assertThat(keys.get("SearchField.errorBackground")).isEqualTo(ColorMath.hex(UiColorKeys.errorTint(p)));
JASPER_PATCH
`````

- [ ] **Step 2: See them fail**

Run: `./gradlew :jasper-ui:compileTestJava`
Expected: FAIL, `cannot find symbol` for `JasperTheme` and `SchemeId`.

- [ ] **Step 3: Apply the production changes**

`````bash
git apply --whitespace=nowarn <<'JASPER_PATCH'
diff --git a/docs/app-architecture.md b/docs/app-architecture.md
index 43bfdc36a5d6fd41af8d176ed87b5cdbd89bcd64..e48c0acf1b226b89cb49ed66d489de58e48facc7 100644
--- a/docs/app-architecture.md
+++ b/docs/app-architecture.md
@@ -231,7 +231,7 @@ the theme's UI keys, which `UiColorKeys` derives from the semantic roles, then t
 the popup shadow) and adds the app's native defaults: the split divider border by class name and the centred title.
 Tool windows paint `surface.panel` (theme.md's tool-window body) through `ToolWindowSurface`, their headers included; the stripes stay chrome.
 
-`SlateSteel.bundled()` (jasper-ui) supplies one `GeneratedTheme` per variant and the terminal schemes. `ThemeController.configure(dark, scheme, font)` installs the variant for chrome and workspace and
+`JasperTheme.bundled()` (jasper-ui) supplies one `GeneratedTheme` per variant and the terminal schemes. `ThemeController.configure(dark, scheme, font)` installs the variant for chrome and workspace and
 resolves the scheme's palette for terminal panes (`ResolvedTheme.slateSteel`); `ResolvedTheme.surface()`, the
 workspace's `surface.terminal`, is what the window behind panes, tab backgrounds and the tab strip use, so a scheme
 colours only the text area. `ConfigurationController` resolves `ui.appearance` with `AppearancePolicy` against the
diff --git a/docs/app-maintenance.md b/docs/app-maintenance.md
index 116aecc0e407db8d25d52c2a3db39cbf8ba8dafe..cc795ed49ab037e3169e640418bc00571f93af6e 100644
--- a/docs/app-maintenance.md
+++ b/docs/app-maintenance.md
@@ -324,8 +324,8 @@ boundaries; this refactor promises no plugin loading, binary compatibility or pe
 
 Jasper has one theme, Slate Steel, in Dark and Light ([themes](themes.md)). Its colours live only in jasper-ui's
 `slate-steel.json`, the machine-readable block of [the Slate Steel record](design/theme-engine/slate-steel.md) (a test
-keeps the two identical). `SlateSteel` reads the file once: the semantic palettes, the terminal schemes (`TerminalScheme`,
-`SchemeColors`), the derived editor schemes and syntax styles, and one `GeneratedTheme` per variant whose FlatLaf UI keys
+keeps the two identical). `JasperTheme` reads the file once: the semantic palettes, the terminal schemes (`TerminalScheme`),
+the derived editor schemes and syntax styles, and one `GeneratedTheme` per variant whose FlatLaf UI keys
 `UiColorKeys.flatLaf` derives from the roles (text selection from the terminal selection, a search miss from
 `UiColorKeys.errorTint`). To change a colour, edit the record's block and the JSON together; to give a component a
 colour, give it a role, never a value.
@@ -335,7 +335,7 @@ plugins) outside `slate-steel.json`; `UiColorKeysTest`, `SlateSteelGeneratedTest
 keys, the variants and the plugins' palettes. `RemovedThemeLayerTest` pins that the former theme layer is gone.
 
 ```sh
-./gradlew :jasper-ui:test --tests "*SlateSteel*" --tests "*UiColorKeysTest"
+./gradlew :jasper-ui:test --tests "*SlateSteel*" --tests "*JasperTheme*" --tests "*UiColorKeysTest"
 ./gradlew :jasper-app:test --tests "*ColourLiteralScanTest" --tests "*HostedPalettesTest" --tests "*RemovedThemeLayerTest"
 ```
 
diff --git a/docs/design/theme-engine/migration.csv b/docs/design/theme-engine/migration.csv
index 7d1e9338b0b6361766718317dd96e1d23eab4a6b..dc20a30383569eee67cda14612b910cc4f34f6a2 100644
--- a/docs/design/theme-engine/migration.csv
+++ b/docs/design/theme-engine/migration.csv
@@ -2,33 +2,33 @@ path,owner,surface,classification,migration_task,composition_task,existing_test,
 jasper-app/src/main/java/dev/jasper/app/appearance/AppListUI.java,app.appearance,AppListUI,standard-ui,2,10,jasper-app/src/test/java/dev/jasper/app/appearance/ThemeManagerTest.java;jasper-app/src/test/java/dev/jasper/app/appearance/ChromeKeysTest.java;jasper-app/src/test/java/dev/jasper/app/appearance/GlassLevelsTest.java;jasper-app/src/test/java/dev/jasper/app/appearance/SelectionRenderingTest.java;jasper-app/src/test/java/dev/jasper/app/appearance/ThemeButtonsTest.java;jasper-ui/src/test/java/dev/jasper/ui/theme/UiColorKeysTest.java;jasper-app/src/test/java/dev/jasper/app/appearance/ThemeMigrationCoverageTest.java;jasper-app/src/test/java/dev/jasper/app/appearance/UiTypographyTest.java,Removed in Plan 2 Task 11; authority=jasper-ui/src/main/java/dev/jasper/ui/swing/KitView.java; private legacy implementation/adapter contract superseded by owned kit and explicit native route.
 jasper-app/src/main/java/dev/jasper/app/appearance/AppearanceBindings.java,app.appearance,AppearanceBindings,nonvisual-helper,2;3,none,none-direct,New in Plan 2 Tasks 2-3; module routing / preserved integration regression ownership.
 jasper-app/src/main/java/dev/jasper/app/appearance/BuiltInThemes.java,app.appearance,BuiltInThemes,nonvisual-helper,2,10,jasper-app/src/test/java/dev/jasper/app/appearance/ThemeManagerTest.java,Removed in Slate Steel Plan 3; authority=jasper-app/src/main/java/dev/jasper/app/appearance/ThemeController.java; Retain/adapt app catalog entries and lazy discovery; remove parser/generator copies and redirect bundled pure parent inputs.
-jasper-app/src/main/java/dev/jasper/app/appearance/ChromeKeys.java,app.appearance,ChromeKeys,nonvisual-helper,2,10,jasper-app/src/test/java/dev/jasper/app/appearance/ChromeKeysTest.java,Removed in Slate Steel Plan 3; authority=jasper-ui/src/main/java/dev/jasper/ui/theme/SlateSteel.java; Removed in Plan 2 Task 5; authority=jasper-ui/src/main/java/dev/jasper/ui/theme/ThemeGenerator.java; module snapshots replace obsolete raw derivation/test oracle.
-jasper-app/src/main/java/dev/jasper/app/appearance/FlatLafThemeData.java,app.appearance,FlatLafThemeData,nonvisual-helper,2,10,jasper-app/src/test/java/dev/jasper/app/appearance/ThemeManagerTest.java;jasper-app/src/test/java/dev/jasper/app/appearance/ChromeKeysTest.java;jasper-app/src/test/java/dev/jasper/app/appearance/GlassLevelsTest.java;jasper-app/src/test/java/dev/jasper/app/appearance/SelectionRenderingTest.java;jasper-app/src/test/java/dev/jasper/app/appearance/ThemeButtonsTest.java;jasper-ui/src/test/java/dev/jasper/ui/theme/UiColorKeysTest.java;jasper-app/src/test/java/dev/jasper/app/appearance/ThemeMigrationCoverageTest.java;jasper-app/src/test/java/dev/jasper/app/appearance/UiTypographyTest.java,Removed in Slate Steel Plan 3; authority=jasper-ui/src/main/java/dev/jasper/ui/theme/SlateSteel.java; Removed in Plan 2 Tasks 2-3; authority=jasper-ui/src/main/java/dev/jasper/ui/themeimport/FlatLafThemeData.java; consumers use module values directly.
+jasper-app/src/main/java/dev/jasper/app/appearance/ChromeKeys.java,app.appearance,ChromeKeys,nonvisual-helper,2,10,jasper-app/src/test/java/dev/jasper/app/appearance/ChromeKeysTest.java,Removed in Slate Steel Plan 3; authority=jasper-ui/src/main/java/dev/jasper/ui/theme/JasperTheme.java; Removed in Plan 2 Task 5; authority=jasper-ui/src/main/java/dev/jasper/ui/theme/ThemeGenerator.java; module snapshots replace obsolete raw derivation/test oracle.
+jasper-app/src/main/java/dev/jasper/app/appearance/FlatLafThemeData.java,app.appearance,FlatLafThemeData,nonvisual-helper,2,10,jasper-app/src/test/java/dev/jasper/app/appearance/ThemeManagerTest.java;jasper-app/src/test/java/dev/jasper/app/appearance/ChromeKeysTest.java;jasper-app/src/test/java/dev/jasper/app/appearance/GlassLevelsTest.java;jasper-app/src/test/java/dev/jasper/app/appearance/SelectionRenderingTest.java;jasper-app/src/test/java/dev/jasper/app/appearance/ThemeButtonsTest.java;jasper-ui/src/test/java/dev/jasper/ui/theme/UiColorKeysTest.java;jasper-app/src/test/java/dev/jasper/app/appearance/ThemeMigrationCoverageTest.java;jasper-app/src/test/java/dev/jasper/app/appearance/UiTypographyTest.java,Removed in Slate Steel Plan 3; authority=jasper-ui/src/main/java/dev/jasper/ui/theme/JasperTheme.java; Removed in Plan 2 Tasks 2-3; authority=jasper-ui/src/main/java/dev/jasper/ui/themeimport/FlatLafThemeData.java; consumers use module values directly.
 jasper-app/src/main/java/dev/jasper/app/appearance/GeneratedTheme.java,app.appearance,GeneratedTheme,nonvisual-helper,2,10,jasper-app/src/test/java/dev/jasper/app/appearance/ThemeManagerTest.java;jasper-app/src/test/java/dev/jasper/app/appearance/ChromeKeysTest.java;jasper-app/src/test/java/dev/jasper/app/appearance/GlassLevelsTest.java;jasper-app/src/test/java/dev/jasper/app/appearance/SelectionRenderingTest.java;jasper-app/src/test/java/dev/jasper/app/appearance/ThemeButtonsTest.java;jasper-ui/src/test/java/dev/jasper/ui/theme/UiColorKeysTest.java;jasper-app/src/test/java/dev/jasper/app/appearance/ThemeMigrationCoverageTest.java;jasper-app/src/test/java/dev/jasper/app/appearance/UiTypographyTest.java,Removed in Plan 2 Tasks 2-3; authority=jasper-ui/src/main/java/dev/jasper/ui/theme/GeneratedTheme.java; consumers use module values directly.
 jasper-app/src/main/java/dev/jasper/app/appearance/GlassLevels.java,app.appearance,GlassLevels,nonvisual-helper,2,10,jasper-app/src/test/java/dev/jasper/app/appearance/GlassLevelsTest.java,Removed in Plan 2 Tasks 2-3; authority=jasper-ui/src/main/java/dev/jasper/ui/theme/GlassLevels.java; consumers use module values directly.
 jasper-app/src/main/java/dev/jasper/app/appearance/LegacyAppearanceDefaults.java,app.appearance,LegacyAppearanceDefaults,nonvisual-helper,2;3,none,none-direct,Removed in Plan 2 Task 11; authority=jasper-ui/src/main/java/dev/jasper/ui/swing/FlatLafInstaller.java; private legacy implementation/adapter contract superseded by owned kit and explicit native route.
 jasper-app/src/main/java/dev/jasper/app/appearance/ResolvedTheme.java,app.appearance,ResolvedTheme,nonvisual-helper,2,10,jasper-app/src/test/java/dev/jasper/app/appearance/ThemeManagerTest.java;jasper-app/src/test/java/dev/jasper/app/appearance/ChromeKeysTest.java;jasper-app/src/test/java/dev/jasper/app/appearance/GlassLevelsTest.java;jasper-app/src/test/java/dev/jasper/app/appearance/SelectionRenderingTest.java;jasper-app/src/test/java/dev/jasper/app/appearance/ThemeButtonsTest.java;jasper-ui/src/test/java/dev/jasper/ui/theme/UiColorKeysTest.java;jasper-app/src/test/java/dev/jasper/app/appearance/ThemeMigrationCoverageTest.java;jasper-app/src/test/java/dev/jasper/app/appearance/UiTypographyTest.java,Retain/adapt wrapper to module snapshots.
-jasper-app/src/main/java/dev/jasper/app/appearance/Seed.java,app.appearance,Seed,nonvisual-helper,2,10,jasper-app/src/test/java/dev/jasper/app/appearance/ThemeManagerTest.java;jasper-app/src/test/java/dev/jasper/app/appearance/ChromeKeysTest.java;jasper-app/src/test/java/dev/jasper/app/appearance/GlassLevelsTest.java;jasper-app/src/test/java/dev/jasper/app/appearance/SelectionRenderingTest.java;jasper-app/src/test/java/dev/jasper/app/appearance/ThemeButtonsTest.java;jasper-ui/src/test/java/dev/jasper/ui/theme/UiColorKeysTest.java;jasper-app/src/test/java/dev/jasper/app/appearance/ThemeMigrationCoverageTest.java;jasper-app/src/test/java/dev/jasper/app/appearance/UiTypographyTest.java,Removed in Slate Steel Plan 3; authority=jasper-ui/src/main/java/dev/jasper/ui/theme/SlateSteel.java; Removed in Plan 2 Tasks 2-3; authority=jasper-ui/src/main/java/dev/jasper/ui/theme/Seed.java; consumers use module values directly.
+jasper-app/src/main/java/dev/jasper/app/appearance/Seed.java,app.appearance,Seed,nonvisual-helper,2,10,jasper-app/src/test/java/dev/jasper/app/appearance/ThemeManagerTest.java;jasper-app/src/test/java/dev/jasper/app/appearance/ChromeKeysTest.java;jasper-app/src/test/java/dev/jasper/app/appearance/GlassLevelsTest.java;jasper-app/src/test/java/dev/jasper/app/appearance/SelectionRenderingTest.java;jasper-app/src/test/java/dev/jasper/app/appearance/ThemeButtonsTest.java;jasper-ui/src/test/java/dev/jasper/ui/theme/UiColorKeysTest.java;jasper-app/src/test/java/dev/jasper/app/appearance/ThemeMigrationCoverageTest.java;jasper-app/src/test/java/dev/jasper/app/appearance/UiTypographyTest.java,Removed in Slate Steel Plan 3; authority=jasper-ui/src/main/java/dev/jasper/ui/theme/JasperTheme.java; Removed in Plan 2 Tasks 2-3; authority=jasper-ui/src/main/java/dev/jasper/ui/theme/Seed.java; consumers use module values directly.
 jasper-app/src/main/java/dev/jasper/app/appearance/SyntaxRole.java,app.appearance,SyntaxRole,nonvisual-helper,2,10,jasper-app/src/test/java/dev/jasper/app/appearance/ThemeManagerTest.java;jasper-app/src/test/java/dev/jasper/app/appearance/ChromeKeysTest.java;jasper-app/src/test/java/dev/jasper/app/appearance/GlassLevelsTest.java;jasper-app/src/test/java/dev/jasper/app/appearance/SelectionRenderingTest.java;jasper-app/src/test/java/dev/jasper/app/appearance/ThemeButtonsTest.java;jasper-ui/src/test/java/dev/jasper/ui/theme/UiColorKeysTest.java;jasper-app/src/test/java/dev/jasper/app/appearance/ThemeMigrationCoverageTest.java;jasper-app/src/test/java/dev/jasper/app/appearance/UiTypographyTest.java,Removed in Plan 2 Tasks 2-3; authority=jasper-ui/src/main/java/dev/jasper/ui/theme/SyntaxRole.java; consumers use module values directly.
 jasper-app/src/main/java/dev/jasper/app/appearance/SyntaxStyle.java,app.appearance,SyntaxStyle,nonvisual-helper,2,10,jasper-app/src/test/java/dev/jasper/app/appearance/ThemeManagerTest.java;jasper-app/src/test/java/dev/jasper/app/appearance/ChromeKeysTest.java;jasper-app/src/test/java/dev/jasper/app/appearance/GlassLevelsTest.java;jasper-app/src/test/java/dev/jasper/app/appearance/SelectionRenderingTest.java;jasper-app/src/test/java/dev/jasper/app/appearance/ThemeButtonsTest.java;jasper-ui/src/test/java/dev/jasper/ui/theme/UiColorKeysTest.java;jasper-app/src/test/java/dev/jasper/app/appearance/ThemeMigrationCoverageTest.java;jasper-app/src/test/java/dev/jasper/app/appearance/UiTypographyTest.java,Removed in Plan 2 Tasks 2-3; authority=jasper-ui/src/main/java/dev/jasper/ui/theme/SyntaxStyle.java; consumers use module values directly.
 jasper-app/src/main/java/dev/jasper/app/appearance/TerminalPaletteAdapter.java,app.appearance,TerminalPaletteAdapter,nonvisual-helper,2;3,none,none-direct,New in Plan 2 Tasks 2-3; module routing / preserved integration regression ownership.
 jasper-app/src/main/java/dev/jasper/app/appearance/Theme.java,app.appearance,Theme,nonvisual-helper,2,10,jasper-app/src/test/java/dev/jasper/app/appearance/ThemeManagerTest.java;jasper-app/src/test/java/dev/jasper/app/appearance/ChromeKeysTest.java;jasper-app/src/test/java/dev/jasper/app/appearance/GlassLevelsTest.java;jasper-app/src/test/java/dev/jasper/app/appearance/SelectionRenderingTest.java;jasper-app/src/test/java/dev/jasper/app/appearance/ThemeButtonsTest.java;jasper-ui/src/test/java/dev/jasper/ui/theme/UiColorKeysTest.java;jasper-app/src/test/java/dev/jasper/app/appearance/ThemeMigrationCoverageTest.java;jasper-app/src/test/java/dev/jasper/app/appearance/UiTypographyTest.java,Removed in Slate Steel Plan 3; authority=jasper-app/src/main/java/dev/jasper/app/appearance/ThemeController.java; Retain/adapt wrapper to module values; remove embedded use of legacy pure engine.
 jasper-app/src/main/java/dev/jasper/app/appearance/ThemeCatalog.java,app.appearance,ThemeCatalog,nonvisual-helper,2,10,jasper-app/src/test/java/dev/jasper/app/appearance/ThemeManagerTest.java,Removed in Slate Steel Plan 3; authority=jasper-app/src/main/java/dev/jasper/app/appearance/ThemeController.java; Retain owner and size/fingerprint boundaries; use module documents/resolution.
 jasper-app/src/main/java/dev/jasper/app/appearance/ThemeController.java,app.appearance,ThemeController,standard-ui,2;4,10,jasper-app/src/test/java/dev/jasper/app/workspace/ThemeControllerTest.java;jasper-app/src/test/java/dev/jasper/app/plugins/KitMountingTest.java,Retain owner; bind module `ThemePair` transactionally.; Task 4 owned-view mounting and live motion integration (transitional API removal Task 11)
-jasper-app/src/main/java/dev/jasper/app/appearance/ThemeDocument.java,app.appearance,ThemeDocument,nonvisual-helper,2,10,jasper-app/src/test/java/dev/jasper/app/appearance/ThemeManagerTest.java;jasper-app/src/test/java/dev/jasper/app/appearance/ChromeKeysTest.java;jasper-app/src/test/java/dev/jasper/app/appearance/GlassLevelsTest.java;jasper-app/src/test/java/dev/jasper/app/appearance/SelectionRenderingTest.java;jasper-app/src/test/java/dev/jasper/app/appearance/ThemeButtonsTest.java;jasper-ui/src/test/java/dev/jasper/ui/theme/UiColorKeysTest.java;jasper-app/src/test/java/dev/jasper/app/appearance/ThemeMigrationCoverageTest.java;jasper-app/src/test/java/dev/jasper/app/appearance/UiTypographyTest.java,Removed in Slate Steel Plan 3; authority=jasper-ui/src/main/java/dev/jasper/ui/theme/SlateSteel.java; Removed in Plan 2 Tasks 2-3; authority=jasper-ui/src/main/java/dev/jasper/ui/theme/ThemeDocument.java; consumers use module values directly.
+jasper-app/src/main/java/dev/jasper/app/appearance/ThemeDocument.java,app.appearance,ThemeDocument,nonvisual-helper,2,10,jasper-app/src/test/java/dev/jasper/app/appearance/ThemeManagerTest.java;jasper-app/src/test/java/dev/jasper/app/appearance/ChromeKeysTest.java;jasper-app/src/test/java/dev/jasper/app/appearance/GlassLevelsTest.java;jasper-app/src/test/java/dev/jasper/app/appearance/SelectionRenderingTest.java;jasper-app/src/test/java/dev/jasper/app/appearance/ThemeButtonsTest.java;jasper-ui/src/test/java/dev/jasper/ui/theme/UiColorKeysTest.java;jasper-app/src/test/java/dev/jasper/app/appearance/ThemeMigrationCoverageTest.java;jasper-app/src/test/java/dev/jasper/app/appearance/UiTypographyTest.java,Removed in Slate Steel Plan 3; authority=jasper-ui/src/main/java/dev/jasper/ui/theme/JasperTheme.java; Removed in Plan 2 Tasks 2-3; authority=jasper-ui/src/main/java/dev/jasper/ui/theme/ThemeDocument.java; consumers use module values directly.
 jasper-app/src/main/java/dev/jasper/app/appearance/ThemeFileException.java,app.appearance,ThemeFileException,nonvisual-helper,2,10,jasper-app/src/test/java/dev/jasper/app/appearance/ThemeManagerTest.java;jasper-app/src/test/java/dev/jasper/app/appearance/ChromeKeysTest.java;jasper-app/src/test/java/dev/jasper/app/appearance/GlassLevelsTest.java;jasper-app/src/test/java/dev/jasper/app/appearance/SelectionRenderingTest.java;jasper-app/src/test/java/dev/jasper/app/appearance/ThemeButtonsTest.java;jasper-ui/src/test/java/dev/jasper/ui/theme/UiColorKeysTest.java;jasper-app/src/test/java/dev/jasper/app/appearance/ThemeMigrationCoverageTest.java;jasper-app/src/test/java/dev/jasper/app/appearance/UiTypographyTest.java,Removed in Plan 2 Tasks 2-3; authority=jasper-ui/src/main/java/dev/jasper/ui/theme/ThemeFileException.java; consumers use module values directly.
-jasper-app/src/main/java/dev/jasper/app/appearance/ThemeGenerator.java,app.appearance,ThemeGenerator,nonvisual-helper,2,10,jasper-ui/src/test/java/dev/jasper/ui/theme/UiColorKeysTest.java,Removed in Slate Steel Plan 3; authority=jasper-ui/src/main/java/dev/jasper/ui/theme/SlateSteel.java; Removed in Plan 2 Tasks 2-3; authority=jasper-ui/src/main/java/dev/jasper/ui/theme/ThemeGenerator.java; consumers use module values directly.
+jasper-app/src/main/java/dev/jasper/app/appearance/ThemeGenerator.java,app.appearance,ThemeGenerator,nonvisual-helper,2,10,jasper-ui/src/test/java/dev/jasper/ui/theme/UiColorKeysTest.java,Removed in Slate Steel Plan 3; authority=jasper-ui/src/main/java/dev/jasper/ui/theme/JasperTheme.java; Removed in Plan 2 Tasks 2-3; authority=jasper-ui/src/main/java/dev/jasper/ui/theme/ThemeGenerator.java; consumers use module values directly.
 jasper-app/src/main/java/dev/jasper/app/appearance/ThemeManager.java,app.appearance,ThemeManager,standard-ui,2,10,jasper-app/src/test/java/dev/jasper/app/appearance/ThemeManagerTest.java,Retain application policy; replace legacy generation; token/geometry installation and global appearance derivation with module contracts.
-jasper-app/src/main/java/dev/jasper/app/appearance/ThemeResolver.java,app.appearance,ThemeResolver,nonvisual-helper,2,10,jasper-app/src/test/java/dev/jasper/app/appearance/ThemeManagerTest.java,Removed in Slate Steel Plan 3; authority=jasper-ui/src/main/java/dev/jasper/ui/theme/SlateSteel.java; Removed in Plan 2 Tasks 2-3; authority=jasper-ui/src/main/java/dev/jasper/ui/theme/ThemeResolver.java; consumers use module values directly.
+jasper-app/src/main/java/dev/jasper/app/appearance/ThemeResolver.java,app.appearance,ThemeResolver,nonvisual-helper,2,10,jasper-app/src/test/java/dev/jasper/app/appearance/ThemeManagerTest.java,Removed in Slate Steel Plan 3; authority=jasper-ui/src/main/java/dev/jasper/ui/theme/JasperTheme.java; Removed in Plan 2 Tasks 2-3; authority=jasper-ui/src/main/java/dev/jasper/ui/theme/ThemeResolver.java; consumers use module values directly.
 jasper-app/src/main/java/dev/jasper/app/appearance/ThemeState.java,app.appearance,ThemeState,nonvisual-helper,2,10,jasper-app/src/test/java/dev/jasper/app/appearance/ThemeManagerTest.java,Removed in Slate Steel Plan 3; authority=jasper-app/src/main/java/dev/jasper/app/appearance/ThemeController.java; Retain/adapt selection state and independent routes.
-jasper-app/src/main/java/dev/jasper/app/appearance/ThemeToml.java,app.appearance,ThemeToml,nonvisual-helper,2,10,jasper-ui/src/test/java/dev/jasper/ui/theme/UiColorKeysTest.java,Removed in Slate Steel Plan 3; authority=jasper-ui/src/main/java/dev/jasper/ui/theme/SlateSteel.java; Removed in Plan 2 Tasks 2-3; authority=jasper-ui/src/main/java/dev/jasper/ui/themeio/ThemeToml.java; consumers use module values directly.
-jasper-app/src/main/java/dev/jasper/app/appearance/ThemeTomlWriter.java,app.appearance,ThemeTomlWriter,nonvisual-helper,2,10,jasper-app/src/test/java/dev/jasper/app/appearance/ThemeManagerTest.java,Removed in Slate Steel Plan 3; authority=jasper-ui/src/main/java/dev/jasper/ui/theme/SlateSteel.java; Removed in Plan 2 Tasks 2-3; authority=jasper-ui/src/main/java/dev/jasper/ui/themeio/ThemeTomlWriter.java; consumers use module values directly.
-jasper-app/src/main/java/dev/jasper/app/appearance/Token.java,app.appearance,Token,nonvisual-helper,2,10,jasper-app/src/test/java/dev/jasper/app/appearance/ThemeManagerTest.java;jasper-app/src/test/java/dev/jasper/app/appearance/ChromeKeysTest.java;jasper-app/src/test/java/dev/jasper/app/appearance/GlassLevelsTest.java;jasper-app/src/test/java/dev/jasper/app/appearance/SelectionRenderingTest.java;jasper-app/src/test/java/dev/jasper/app/appearance/ThemeButtonsTest.java;jasper-ui/src/test/java/dev/jasper/ui/theme/UiColorKeysTest.java;jasper-app/src/test/java/dev/jasper/app/appearance/ThemeMigrationCoverageTest.java;jasper-app/src/test/java/dev/jasper/app/appearance/UiTypographyTest.java,Removed in Slate Steel Plan 3; authority=jasper-ui/src/main/java/dev/jasper/ui/theme/SlateSteel.java; Removed in Plan 2 Tasks 2-3; authority=jasper-ui/src/main/java/dev/jasper/ui/theme/Token.java; consumers use module values directly.
-jasper-app/src/main/java/dev/jasper/app/appearance/UiTemplate.java,app.appearance,UiTemplate,nonvisual-helper,2,10,jasper-app/src/test/java/dev/jasper/app/appearance/ThemeManagerTest.java;jasper-app/src/test/java/dev/jasper/app/appearance/ChromeKeysTest.java;jasper-app/src/test/java/dev/jasper/app/appearance/GlassLevelsTest.java;jasper-app/src/test/java/dev/jasper/app/appearance/SelectionRenderingTest.java;jasper-app/src/test/java/dev/jasper/app/appearance/ThemeButtonsTest.java;jasper-ui/src/test/java/dev/jasper/ui/theme/UiColorKeysTest.java;jasper-app/src/test/java/dev/jasper/app/appearance/ThemeMigrationCoverageTest.java;jasper-app/src/test/java/dev/jasper/app/appearance/UiTypographyTest.java,Removed in Slate Steel Plan 3; authority=jasper-ui/src/main/java/dev/jasper/ui/theme/SlateSteel.java; Removed in Plan 2 Tasks 2-3; authority=jasper-ui/src/main/java/dev/jasper/ui/theme/UiTemplate.java; consumers use module values directly.
+jasper-app/src/main/java/dev/jasper/app/appearance/ThemeToml.java,app.appearance,ThemeToml,nonvisual-helper,2,10,jasper-ui/src/test/java/dev/jasper/ui/theme/UiColorKeysTest.java,Removed in Slate Steel Plan 3; authority=jasper-ui/src/main/java/dev/jasper/ui/theme/JasperTheme.java; Removed in Plan 2 Tasks 2-3; authority=jasper-ui/src/main/java/dev/jasper/ui/themeio/ThemeToml.java; consumers use module values directly.
+jasper-app/src/main/java/dev/jasper/app/appearance/ThemeTomlWriter.java,app.appearance,ThemeTomlWriter,nonvisual-helper,2,10,jasper-app/src/test/java/dev/jasper/app/appearance/ThemeManagerTest.java,Removed in Slate Steel Plan 3; authority=jasper-ui/src/main/java/dev/jasper/ui/theme/JasperTheme.java; Removed in Plan 2 Tasks 2-3; authority=jasper-ui/src/main/java/dev/jasper/ui/themeio/ThemeTomlWriter.java; consumers use module values directly.
+jasper-app/src/main/java/dev/jasper/app/appearance/Token.java,app.appearance,Token,nonvisual-helper,2,10,jasper-app/src/test/java/dev/jasper/app/appearance/ThemeManagerTest.java;jasper-app/src/test/java/dev/jasper/app/appearance/ChromeKeysTest.java;jasper-app/src/test/java/dev/jasper/app/appearance/GlassLevelsTest.java;jasper-app/src/test/java/dev/jasper/app/appearance/SelectionRenderingTest.java;jasper-app/src/test/java/dev/jasper/app/appearance/ThemeButtonsTest.java;jasper-ui/src/test/java/dev/jasper/ui/theme/UiColorKeysTest.java;jasper-app/src/test/java/dev/jasper/app/appearance/ThemeMigrationCoverageTest.java;jasper-app/src/test/java/dev/jasper/app/appearance/UiTypographyTest.java,Removed in Slate Steel Plan 3; authority=jasper-ui/src/main/java/dev/jasper/ui/theme/JasperTheme.java; Removed in Plan 2 Tasks 2-3; authority=jasper-ui/src/main/java/dev/jasper/ui/theme/Token.java; consumers use module values directly.
+jasper-app/src/main/java/dev/jasper/app/appearance/UiTemplate.java,app.appearance,UiTemplate,nonvisual-helper,2,10,jasper-app/src/test/java/dev/jasper/app/appearance/ThemeManagerTest.java;jasper-app/src/test/java/dev/jasper/app/appearance/ChromeKeysTest.java;jasper-app/src/test/java/dev/jasper/app/appearance/GlassLevelsTest.java;jasper-app/src/test/java/dev/jasper/app/appearance/SelectionRenderingTest.java;jasper-app/src/test/java/dev/jasper/app/appearance/ThemeButtonsTest.java;jasper-ui/src/test/java/dev/jasper/ui/theme/UiColorKeysTest.java;jasper-app/src/test/java/dev/jasper/app/appearance/ThemeMigrationCoverageTest.java;jasper-app/src/test/java/dev/jasper/app/appearance/UiTypographyTest.java,Removed in Slate Steel Plan 3; authority=jasper-ui/src/main/java/dev/jasper/ui/theme/JasperTheme.java; Removed in Plan 2 Tasks 2-3; authority=jasper-ui/src/main/java/dev/jasper/ui/theme/UiTemplate.java; consumers use module values directly.
 jasper-app/src/main/java/dev/jasper/app/appearance/WorkspaceDefaults.java,app.appearance,WorkspaceDefaults,standard-ui,2,10,jasper-app/src/test/java/dev/jasper/app/appearance/ThemeManagerTest.java;jasper-app/src/test/java/dev/jasper/app/appearance/ChromeKeysTest.java;jasper-app/src/test/java/dev/jasper/app/appearance/GlassLevelsTest.java;jasper-app/src/test/java/dev/jasper/app/appearance/SelectionRenderingTest.java;jasper-app/src/test/java/dev/jasper/app/appearance/ThemeButtonsTest.java;jasper-ui/src/test/java/dev/jasper/ui/theme/UiColorKeysTest.java;jasper-app/src/test/java/dev/jasper/app/appearance/ThemeMigrationCoverageTest.java;jasper-app/src/test/java/dev/jasper/app/appearance/UiTypographyTest.java,Removed in Plan 2 Task 11; authority=jasper-ui/src/main/java/dev/jasper/ui/swing/FlatLafInstaller.java; private legacy implementation/adapter contract superseded by owned kit and explicit native route.
-jasper-app/src/main/java/dev/jasper/app/appearance/WorkspaceGlass.java,app.appearance,WorkspaceGlass,nonvisual-helper,2,10,jasper-app/src/test/java/dev/jasper/app/workspace/WorkspaceGlassTest.java,Removed in Slate Steel Plan 3; authority=jasper-ui/src/main/java/dev/jasper/ui/theme/SlateSteel.java; Removed in Plan 2 Tasks 2-3; authority=jasper-ui/src/main/java/dev/jasper/ui/theme/WorkspaceGlass.java; consumers use module values directly.
-jasper-app/src/main/java/dev/jasper/app/appearance/WorkspaceTokenKeys.java,app.appearance,WorkspaceTokenKeys,nonvisual-helper,2,10,jasper-app/src/test/java/dev/jasper/app/appearance/ThemeManagerTest.java;jasper-app/src/test/java/dev/jasper/app/appearance/ChromeKeysTest.java;jasper-app/src/test/java/dev/jasper/app/appearance/GlassLevelsTest.java;jasper-app/src/test/java/dev/jasper/app/appearance/SelectionRenderingTest.java;jasper-app/src/test/java/dev/jasper/app/appearance/ThemeButtonsTest.java;jasper-ui/src/test/java/dev/jasper/ui/theme/UiColorKeysTest.java;jasper-app/src/test/java/dev/jasper/app/appearance/ThemeMigrationCoverageTest.java;jasper-app/src/test/java/dev/jasper/app/appearance/UiTypographyTest.java,Removed in Slate Steel Plan 3; authority=jasper-ui/src/main/java/dev/jasper/ui/theme/SlateSteel.java; Removed in Plan 2 Tasks 2-3; authority=jasper-ui/src/main/java/dev/jasper/ui/themeimport/WorkspaceTokenKeys.java; consumers use module values directly.
-jasper-app/src/main/java/dev/jasper/app/appearance/WorkspaceValues.java,app.appearance,WorkspaceValues,nonvisual-helper,2,10,jasper-app/src/test/java/dev/jasper/app/appearance/ThemeManagerTest.java,Removed in Slate Steel Plan 3; authority=jasper-ui/src/main/java/dev/jasper/ui/theme/SlateSteel.java; Removed in Plan 2 Tasks 2-3; authority=jasper-ui/src/main/java/dev/jasper/ui/theme/ThemeGenerator.java; consumers use module values directly.
+jasper-app/src/main/java/dev/jasper/app/appearance/WorkspaceGlass.java,app.appearance,WorkspaceGlass,nonvisual-helper,2,10,jasper-app/src/test/java/dev/jasper/app/workspace/WorkspaceGlassTest.java,Removed in Slate Steel Plan 3; authority=jasper-ui/src/main/java/dev/jasper/ui/theme/JasperTheme.java; Removed in Plan 2 Tasks 2-3; authority=jasper-ui/src/main/java/dev/jasper/ui/theme/WorkspaceGlass.java; consumers use module values directly.
+jasper-app/src/main/java/dev/jasper/app/appearance/WorkspaceTokenKeys.java,app.appearance,WorkspaceTokenKeys,nonvisual-helper,2,10,jasper-app/src/test/java/dev/jasper/app/appearance/ThemeManagerTest.java;jasper-app/src/test/java/dev/jasper/app/appearance/ChromeKeysTest.java;jasper-app/src/test/java/dev/jasper/app/appearance/GlassLevelsTest.java;jasper-app/src/test/java/dev/jasper/app/appearance/SelectionRenderingTest.java;jasper-app/src/test/java/dev/jasper/app/appearance/ThemeButtonsTest.java;jasper-ui/src/test/java/dev/jasper/ui/theme/UiColorKeysTest.java;jasper-app/src/test/java/dev/jasper/app/appearance/ThemeMigrationCoverageTest.java;jasper-app/src/test/java/dev/jasper/app/appearance/UiTypographyTest.java,Removed in Slate Steel Plan 3; authority=jasper-ui/src/main/java/dev/jasper/ui/theme/JasperTheme.java; Removed in Plan 2 Tasks 2-3; authority=jasper-ui/src/main/java/dev/jasper/ui/themeimport/WorkspaceTokenKeys.java; consumers use module values directly.
+jasper-app/src/main/java/dev/jasper/app/appearance/WorkspaceValues.java,app.appearance,WorkspaceValues,nonvisual-helper,2,10,jasper-app/src/test/java/dev/jasper/app/appearance/ThemeManagerTest.java,Removed in Slate Steel Plan 3; authority=jasper-ui/src/main/java/dev/jasper/ui/theme/JasperTheme.java; Removed in Plan 2 Tasks 2-3; authority=jasper-ui/src/main/java/dev/jasper/ui/theme/ThemeGenerator.java; consumers use module values directly.
 jasper-app/src/main/java/dev/jasper/app/appearance/package-info.java,cycles,package-info,nonvisual-helper,5,10,none-direct,Plan 2 Task 5 audited nonvisual/native behavior owner; retained with no independent styling authority.
 jasper-app/src/main/java/dev/jasper/app/application/AppearancePolicy.java,app.application,AppearancePolicy,nonvisual-helper,none,none,jasper-app/src/test/java/dev/jasper/app/application/AppearancePolicyTest.java,"New in Slate Steel Plan 2 Task 5; the appearance policy: Dark, Light or the system's, Dark when the system is unknown"
 jasper-app/src/main/java/dev/jasper/app/application/BuddyIntegration.java,app.application,BuddyIntegration,nonvisual-helper,6,9;10,jasper-app/src/test/java/dev/jasper/app/application/BuddyIntegrationTest.java,"Plan 2 Task 6c audited: supported appearance adaptation only (primaryFont and dark through BuddyOptions.toBuilder; supported config/notice/view types only), asserted by BuddyIntegrationTest.appearanceAdaptationUsesOnlySupportedFacadeValues; 13 pt system font kept (ruling G9)."
@@ -154,7 +154,7 @@ jasper-app/src/main/java/dev/jasper/app/plugins/HostedUi.java,app.plugins,Hosted
 jasper-app/src/main/java/dev/jasper/app/plugins/KitContent.java,app.plugins,KitContent,standard-ui,4,4;10,jasper-app/src/test/java/dev/jasper/app/plugins/AppContractTest.java;jasper-app/src/test/java/dev/jasper/app/plugins/BundledSamplePluginTest.java;jasper-app/src/test/java/dev/jasper/app/plugins/ContainmentTest.java;jasper-app/src/test/java/dev/jasper/app/plugins/DeclaredSettingsTest.java;jasper-app/src/test/java/dev/jasper/app/plugins/DescriptorParserTest.java;jasper-app/src/test/java/dev/jasper/app/plugins/EventBusTest.java;jasper-app/src/test/java/dev/jasper/app/plugins/HostedChooserTest.java;jasper-app/src/test/java/dev/jasper/app/plugins/HostedFileSourcesTest.java;jasper-app/src/test/java/dev/jasper/app/plugins/HostedKitContractTest.java;jasper-app/src/test/java/dev/jasper/app/plugins/HostedKitTest.java;jasper-app/src/test/java/dev/jasper/app/plugins/HostedNotificationsTest.java;jasper-app/src/test/java/dev/jasper/app/plugins/HostedPaletteTest.java;jasper-app/src/test/java/dev/jasper/app/plugins/HostedSessionsTest.java;jasper-app/src/test/java/dev/jasper/app/plugins/HostedTerminalsTest.java;jasper-app/src/test/java/dev/jasper/app/plugins/HostedUiTest.java;jasper-app/src/test/java/dev/jasper/app/plugins/KitValuesTest.java;jasper-app/src/test/java/dev/jasper/app/plugins/ManagedVaultLoadingTest.java;jasper-app/src/test/java/dev/jasper/app/plugins/PluginAdminTest.java;jasper-app/src/test/java/dev/jasper/app/plugins/PluginCatalogTest.java;jasper-app/src/test/java/dev/jasper/app/plugins/PluginDiscoveryTest.java;jasper-app/src/test/java/dev/jasper/app/plugins/PluginHostTest.java;jasper-app/src/test/java/dev/jasper/app/plugins/PluginIconFilesTest.java;jasper-app/src/test/java/dev/jasper/app/plugins/PluginInstallerTest.java;jasper-app/src/test/java/dev/jasper/app/plugins/PluginLoaderTest.java;jasper-app/src/test/java/dev/jasper/app/plugins/PluginMaintenanceTest.java;jasper-app/src/test/java/dev/jasper/app/plugins/PluginPanelPageTest.java;jasper-app/src/test/java/dev/jasper/app/plugins/PluginResolverTest.java;jasper-app/src/test/java/dev/jasper/app/plugins/PluginRuntimeTest.java;jasper-app/src/test/java/dev/jasper/app/plugins/PluginSettingsFilesTest.java;jasper-app/src/test/java/dev/jasper/app/plugins/PluginSettingsPageTest.java;jasper-app/src/test/java/dev/jasper/app/plugins/PluginStateStoreTest.java;jasper-app/src/test/java/dev/jasper/app/plugins/PluginZipsTest.java;jasper-app/src/test/java/dev/jasper/app/plugins/RemoteShortcutConfigTest.java;jasper-app/src/test/java/dev/jasper/app/plugins/StagedTransferLifecycleTest.java;jasper-app/src/test/java/dev/jasper/app/plugins/TerminalBridgeTest.java;jasper-app/src/test/java/dev/jasper/app/plugins/TomlTextTest.java;jasper-app/src/test/java/dev/jasper/app/plugins/VersionRangeTest.java;jasper-app/src/test/java/dev/jasper/app/plugins/KitMountingTest.java,Plan2 source migration audited; kit descriptors or documented native/domain adapter; existing owner tests retained; Task 4 owned-view mounting and live motion integration (transitional API removal Task 11); App owner source audit; plugin-only compiled presentation gate does not inspect this app class.
 jasper-app/src/main/java/dev/jasper/app/plugins/KitDiagnostics.java,app.plugins,KitDiagnostics,standard-ui,4,4;10,jasper-app/src/test/java/dev/jasper/app/plugins/AppContractTest.java;jasper-app/src/test/java/dev/jasper/app/plugins/BundledSamplePluginTest.java;jasper-app/src/test/java/dev/jasper/app/plugins/ContainmentTest.java;jasper-app/src/test/java/dev/jasper/app/plugins/DeclaredSettingsTest.java;jasper-app/src/test/java/dev/jasper/app/plugins/DescriptorParserTest.java;jasper-app/src/test/java/dev/jasper/app/plugins/EventBusTest.java;jasper-app/src/test/java/dev/jasper/app/plugins/HostedChooserTest.java;jasper-app/src/test/java/dev/jasper/app/plugins/HostedFileSourcesTest.java;jasper-app/src/test/java/dev/jasper/app/plugins/HostedKitContractTest.java;jasper-app/src/test/java/dev/jasper/app/plugins/HostedKitTest.java;jasper-app/src/test/java/dev/jasper/app/plugins/HostedNotificationsTest.java;jasper-app/src/test/java/dev/jasper/app/plugins/HostedPaletteTest.java;jasper-app/src/test/java/dev/jasper/app/plugins/HostedSessionsTest.java;jasper-app/src/test/java/dev/jasper/app/plugins/HostedTerminalsTest.java;jasper-app/src/test/java/dev/jasper/app/plugins/HostedUiTest.java;jasper-app/src/test/java/dev/jasper/app/plugins/KitValuesTest.java;jasper-app/src/test/java/dev/jasper/app/plugins/ManagedVaultLoadingTest.java;jasper-app/src/test/java/dev/jasper/app/plugins/PluginAdminTest.java;jasper-app/src/test/java/dev/jasper/app/plugins/PluginCatalogTest.java;jasper-app/src/test/java/dev/jasper/app/plugins/PluginDiscoveryTest.java;jasper-app/src/test/java/dev/jasper/app/plugins/PluginHostTest.java;jasper-app/src/test/java/dev/jasper/app/plugins/PluginIconFilesTest.java;jasper-app/src/test/java/dev/jasper/app/plugins/PluginInstallerTest.java;jasper-app/src/test/java/dev/jasper/app/plugins/PluginLoaderTest.java;jasper-app/src/test/java/dev/jasper/app/plugins/PluginMaintenanceTest.java;jasper-app/src/test/java/dev/jasper/app/plugins/PluginPanelPageTest.java;jasper-app/src/test/java/dev/jasper/app/plugins/PluginResolverTest.java;jasper-app/src/test/java/dev/jasper/app/plugins/PluginRuntimeTest.java;jasper-app/src/test/java/dev/jasper/app/plugins/PluginSettingsFilesTest.java;jasper-app/src/test/java/dev/jasper/app/plugins/PluginSettingsPageTest.java;jasper-app/src/test/java/dev/jasper/app/plugins/PluginStateStoreTest.java;jasper-app/src/test/java/dev/jasper/app/plugins/PluginZipsTest.java;jasper-app/src/test/java/dev/jasper/app/plugins/RemoteShortcutConfigTest.java;jasper-app/src/test/java/dev/jasper/app/plugins/StagedTransferLifecycleTest.java;jasper-app/src/test/java/dev/jasper/app/plugins/TerminalBridgeTest.java;jasper-app/src/test/java/dev/jasper/app/plugins/TomlTextTest.java;jasper-app/src/test/java/dev/jasper/app/plugins/VersionRangeTest.java,Plan2 source migration audited; kit descriptors or documented native/domain adapter; existing owner tests retained; App owner source audit; plugin-only compiled presentation gate does not inspect this app class.
 jasper-app/src/main/java/dev/jasper/app/plugins/KitSettingRows.java,app.plugins,KitSettingRows,standard-ui,4,4;10,jasper-app/src/test/java/dev/jasper/app/plugins/PluginPanelPageTest.java,Task 4 owned-view mounting/draft/motion integration; retain behavior at Task 11 API cutover; Task 4 review fix: standard Reset stages absent override via page-owned unset callback; App owner source audit; plugin-only compiled presentation gate does not inspect this app class.; Plan 3 Task 8: host rows use the generated pages SETTINGS_TRAILING slot and an icon-only Reset (refresh glyph) shown while the value differs from its default; ids/events unchanged
-jasper-app/src/main/java/dev/jasper/app/plugins/KitThemeBridge.java,app.plugins,KitThemeBridge,nonvisual-helper,4,4;10,jasper-app/src/test/java/dev/jasper/app/plugins/AppContractTest.java;jasper-app/src/test/java/dev/jasper/app/plugins/BundledSamplePluginTest.java;jasper-app/src/test/java/dev/jasper/app/plugins/ContainmentTest.java;jasper-app/src/test/java/dev/jasper/app/plugins/DeclaredSettingsTest.java;jasper-app/src/test/java/dev/jasper/app/plugins/DescriptorParserTest.java;jasper-app/src/test/java/dev/jasper/app/plugins/EventBusTest.java;jasper-app/src/test/java/dev/jasper/app/plugins/HostedChooserTest.java;jasper-app/src/test/java/dev/jasper/app/plugins/HostedFileSourcesTest.java;jasper-app/src/test/java/dev/jasper/app/plugins/HostedKitContractTest.java;jasper-app/src/test/java/dev/jasper/app/plugins/HostedKitTest.java;jasper-app/src/test/java/dev/jasper/app/plugins/HostedNotificationsTest.java;jasper-app/src/test/java/dev/jasper/app/plugins/HostedPaletteTest.java;jasper-app/src/test/java/dev/jasper/app/plugins/HostedSessionsTest.java;jasper-app/src/test/java/dev/jasper/app/plugins/HostedTerminalsTest.java;jasper-app/src/test/java/dev/jasper/app/plugins/HostedUiTest.java;jasper-app/src/test/java/dev/jasper/app/plugins/KitValuesTest.java;jasper-app/src/test/java/dev/jasper/app/plugins/ManagedVaultLoadingTest.java;jasper-app/src/test/java/dev/jasper/app/plugins/PluginAdminTest.java;jasper-app/src/test/java/dev/jasper/app/plugins/PluginCatalogTest.java;jasper-app/src/test/java/dev/jasper/app/plugins/PluginDiscoveryTest.java;jasper-app/src/test/java/dev/jasper/app/plugins/PluginHostTest.java;jasper-app/src/test/java/dev/jasper/app/plugins/PluginIconFilesTest.java;jasper-app/src/test/java/dev/jasper/app/plugins/PluginInstallerTest.java;jasper-app/src/test/java/dev/jasper/app/plugins/PluginLoaderTest.java;jasper-app/src/test/java/dev/jasper/app/plugins/PluginMaintenanceTest.java;jasper-app/src/test/java/dev/jasper/app/plugins/PluginPanelPageTest.java;jasper-app/src/test/java/dev/jasper/app/plugins/PluginResolverTest.java;jasper-app/src/test/java/dev/jasper/app/plugins/PluginRuntimeTest.java;jasper-app/src/test/java/dev/jasper/app/plugins/PluginSettingsFilesTest.java;jasper-app/src/test/java/dev/jasper/app/plugins/PluginSettingsPageTest.java;jasper-app/src/test/java/dev/jasper/app/plugins/PluginStateStoreTest.java;jasper-app/src/test/java/dev/jasper/app/plugins/PluginZipsTest.java;jasper-app/src/test/java/dev/jasper/app/plugins/RemoteShortcutConfigTest.java;jasper-app/src/test/java/dev/jasper/app/plugins/StagedTransferLifecycleTest.java;jasper-app/src/test/java/dev/jasper/app/plugins/TerminalBridgeTest.java;jasper-app/src/test/java/dev/jasper/app/plugins/TomlTextTest.java;jasper-app/src/test/java/dev/jasper/app/plugins/VersionRangeTest.java,Removed in Slate Steel Plan 3; authority=jasper-ui/src/main/java/dev/jasper/ui/theme/SlateSteel.java; Removed in Plan 2 Tasks 2-3; authority=jasper-ui/src/main/java/dev/jasper/ui/theme/ThemeGenerator.java; consumers use module values directly.
+jasper-app/src/main/java/dev/jasper/app/plugins/KitThemeBridge.java,app.plugins,KitThemeBridge,nonvisual-helper,4,4;10,jasper-app/src/test/java/dev/jasper/app/plugins/AppContractTest.java;jasper-app/src/test/java/dev/jasper/app/plugins/BundledSamplePluginTest.java;jasper-app/src/test/java/dev/jasper/app/plugins/ContainmentTest.java;jasper-app/src/test/java/dev/jasper/app/plugins/DeclaredSettingsTest.java;jasper-app/src/test/java/dev/jasper/app/plugins/DescriptorParserTest.java;jasper-app/src/test/java/dev/jasper/app/plugins/EventBusTest.java;jasper-app/src/test/java/dev/jasper/app/plugins/HostedChooserTest.java;jasper-app/src/test/java/dev/jasper/app/plugins/HostedFileSourcesTest.java;jasper-app/src/test/java/dev/jasper/app/plugins/HostedKitContractTest.java;jasper-app/src/test/java/dev/jasper/app/plugins/HostedKitTest.java;jasper-app/src/test/java/dev/jasper/app/plugins/HostedNotificationsTest.java;jasper-app/src/test/java/dev/jasper/app/plugins/HostedPaletteTest.java;jasper-app/src/test/java/dev/jasper/app/plugins/HostedSessionsTest.java;jasper-app/src/test/java/dev/jasper/app/plugins/HostedTerminalsTest.java;jasper-app/src/test/java/dev/jasper/app/plugins/HostedUiTest.java;jasper-app/src/test/java/dev/jasper/app/plugins/KitValuesTest.java;jasper-app/src/test/java/dev/jasper/app/plugins/ManagedVaultLoadingTest.java;jasper-app/src/test/java/dev/jasper/app/plugins/PluginAdminTest.java;jasper-app/src/test/java/dev/jasper/app/plugins/PluginCatalogTest.java;jasper-app/src/test/java/dev/jasper/app/plugins/PluginDiscoveryTest.java;jasper-app/src/test/java/dev/jasper/app/plugins/PluginHostTest.java;jasper-app/src/test/java/dev/jasper/app/plugins/PluginIconFilesTest.java;jasper-app/src/test/java/dev/jasper/app/plugins/PluginInstallerTest.java;jasper-app/src/test/java/dev/jasper/app/plugins/PluginLoaderTest.java;jasper-app/src/test/java/dev/jasper/app/plugins/PluginMaintenanceTest.java;jasper-app/src/test/java/dev/jasper/app/plugins/PluginPanelPageTest.java;jasper-app/src/test/java/dev/jasper/app/plugins/PluginResolverTest.java;jasper-app/src/test/java/dev/jasper/app/plugins/PluginRuntimeTest.java;jasper-app/src/test/java/dev/jasper/app/plugins/PluginSettingsFilesTest.java;jasper-app/src/test/java/dev/jasper/app/plugins/PluginSettingsPageTest.java;jasper-app/src/test/java/dev/jasper/app/plugins/PluginStateStoreTest.java;jasper-app/src/test/java/dev/jasper/app/plugins/PluginZipsTest.java;jasper-app/src/test/java/dev/jasper/app/plugins/RemoteShortcutConfigTest.java;jasper-app/src/test/java/dev/jasper/app/plugins/StagedTransferLifecycleTest.java;jasper-app/src/test/java/dev/jasper/app/plugins/TerminalBridgeTest.java;jasper-app/src/test/java/dev/jasper/app/plugins/TomlTextTest.java;jasper-app/src/test/java/dev/jasper/app/plugins/VersionRangeTest.java,Removed in Slate Steel Plan 3; authority=jasper-ui/src/main/java/dev/jasper/ui/theme/JasperTheme.java; Removed in Plan 2 Tasks 2-3; authority=jasper-ui/src/main/java/dev/jasper/ui/theme/ThemeGenerator.java; consumers use module values directly.
 jasper-app/src/main/java/dev/jasper/app/plugins/PluginAdmin.java,app.plugins,PluginAdmin,nonvisual-helper,4,4;10,jasper-app/src/test/java/dev/jasper/app/plugins/PluginAdminTest.java,Nonvisual helper or fixture false positive; retain behavior and adapt referenced values only
 jasper-app/src/main/java/dev/jasper/app/plugins/PluginCatalog.java,app.plugins,PluginCatalog,nonvisual-helper,4,4;10,jasper-app/src/test/java/dev/jasper/app/plugins/PluginCatalogTest.java,Nonvisual helper or fixture false positive; retain behavior and adapt referenced values only
 jasper-app/src/main/java/dev/jasper/app/plugins/PluginDiscovery.java,app.plugins,PluginDiscovery,nonvisual-helper,4,4;10,jasper-app/src/test/java/dev/jasper/app/plugins/PluginDiscoveryTest.java,Nonvisual helper or fixture false positive; retain behavior and adapt referenced values only
@@ -213,22 +213,22 @@ jasper-app/src/main/java/dev/jasper/app/startup/StartupTraceFile.java,app.startu
 jasper-app/src/main/java/dev/jasper/app/terminals/SessionAttempt.java,app.terminals,SessionAttempt,nonvisual-helper,5,10,jasper-app/src/test/java/dev/jasper/app/terminals/SessionAttemptTest.java,Plan 2 Task 5 audited nonvisual/native behavior owner; retained with no independent styling authority.
 jasper-app/src/main/java/dev/jasper/app/terminals/SessionRequest.java,app.terminals,SessionRequest,nonvisual-helper,5,10,jasper-app/src/test/java/dev/jasper/app/terminals/SessionAttemptTest.java;jasper-app/src/test/java/dev/jasper/app/terminals/TerminalRegistryTest.java,Plan 2 Task 5 audited nonvisual/native behavior owner; retained with no independent styling authority.
 jasper-app/src/main/java/dev/jasper/app/terminals/TerminalRegistry.java,app.terminals,TerminalRegistry,nonvisual-helper,5,10,jasper-app/src/test/java/dev/jasper/app/terminals/TerminalRegistryTest.java,Plan 2 Task 5 audited nonvisual/native behavior owner; retained with no independent styling authority.
-jasper-app/src/main/java/dev/jasper/app/themeimport/AlacrittyConversion.java,app.themeimport,AlacrittyConversion,nonvisual-helper,3,9,jasper-app/src/test/java/dev/jasper/app/application/JasperApplicationThemesTest.java;jasper-app/src/test/java/dev/jasper/app/appearance/ThemeManagerTest.java,Removed in Slate Steel Plan 3; authority=jasper-ui/src/main/java/dev/jasper/ui/theme/SlateSteel.java; Removed in Plan 2 Tasks 2-3; authority=jasper-ui/src/main/java/dev/jasper/ui/themeimport/AlacrittyConversion.java; consumers use module values directly.
-jasper-app/src/main/java/dev/jasper/app/themeimport/AlacrittyTheme.java,app.themeimport,AlacrittyTheme,nonvisual-helper,3,9,jasper-app/src/test/java/dev/jasper/app/application/JasperApplicationThemesTest.java,Removed in Slate Steel Plan 3; authority=jasper-ui/src/main/java/dev/jasper/ui/theme/SlateSteel.java; Removed in Plan 2 Tasks 2-3; authority=jasper-ui/src/main/java/dev/jasper/ui/themeimport/AlacrittyTheme.java; consumers use module values directly.
-jasper-app/src/main/java/dev/jasper/app/themeimport/ConversionResult.java,app.themeimport,ConversionResult,nonvisual-helper,3,9,jasper-app/src/test/java/dev/jasper/app/application/JasperApplicationThemesTest.java;jasper-app/src/test/java/dev/jasper/app/appearance/ThemeManagerTest.java,Removed in Slate Steel Plan 3; authority=jasper-ui/src/main/java/dev/jasper/ui/theme/SlateSteel.java; Removed in Plan 2 Tasks 2-3; authority=jasper-ui/src/main/java/dev/jasper/ui/themeimport/ConversionResult.java; consumers use module values directly.
+jasper-app/src/main/java/dev/jasper/app/themeimport/AlacrittyConversion.java,app.themeimport,AlacrittyConversion,nonvisual-helper,3,9,jasper-app/src/test/java/dev/jasper/app/application/JasperApplicationThemesTest.java;jasper-app/src/test/java/dev/jasper/app/appearance/ThemeManagerTest.java,Removed in Slate Steel Plan 3; authority=jasper-ui/src/main/java/dev/jasper/ui/theme/JasperTheme.java; Removed in Plan 2 Tasks 2-3; authority=jasper-ui/src/main/java/dev/jasper/ui/themeimport/AlacrittyConversion.java; consumers use module values directly.
+jasper-app/src/main/java/dev/jasper/app/themeimport/AlacrittyTheme.java,app.themeimport,AlacrittyTheme,nonvisual-helper,3,9,jasper-app/src/test/java/dev/jasper/app/application/JasperApplicationThemesTest.java,Removed in Slate Steel Plan 3; authority=jasper-ui/src/main/java/dev/jasper/ui/theme/JasperTheme.java; Removed in Plan 2 Tasks 2-3; authority=jasper-ui/src/main/java/dev/jasper/ui/themeimport/AlacrittyTheme.java; consumers use module values directly.
+jasper-app/src/main/java/dev/jasper/app/themeimport/ConversionResult.java,app.themeimport,ConversionResult,nonvisual-helper,3,9,jasper-app/src/test/java/dev/jasper/app/application/JasperApplicationThemesTest.java;jasper-app/src/test/java/dev/jasper/app/appearance/ThemeManagerTest.java,Removed in Slate Steel Plan 3; authority=jasper-ui/src/main/java/dev/jasper/ui/theme/JasperTheme.java; Removed in Plan 2 Tasks 2-3; authority=jasper-ui/src/main/java/dev/jasper/ui/themeimport/ConversionResult.java; consumers use module values directly.
 jasper-app/src/main/java/dev/jasper/app/themeimport/ConvertThemes.java,app.themeimport,ConvertThemes,nonvisual-helper,3,9,jasper-app/src/test/java/dev/jasper/app/application/JasperApplicationThemesTest.java;jasper-app/src/test/java/dev/jasper/app/appearance/ThemeManagerTest.java,Removed in Slate Steel Plan 3; authority=jasper-app/src/main/java/dev/jasper/app/application/JasperApplication.java; Retain entry point; delegate conversion to module.
-jasper-app/src/main/java/dev/jasper/app/themeimport/EditorScheme.java,app.themeimport,EditorScheme,nonvisual-helper,3,9,jasper-app/src/test/java/dev/jasper/app/appearance/ThemeManagerTest.java,Removed in Slate Steel Plan 3; authority=jasper-ui/src/main/java/dev/jasper/ui/theme/SlateSteel.java; Removed in Plan 2 Tasks 2-3; authority=jasper-ui/src/main/java/dev/jasper/ui/themeimport/EditorScheme.java; consumers use module values directly.
-jasper-app/src/main/java/dev/jasper/app/themeimport/IntellijConversion.java,app.themeimport,IntellijConversion,nonvisual-helper,3,9,jasper-app/src/test/java/dev/jasper/app/appearance/ThemeManagerTest.java,Removed in Slate Steel Plan 3; authority=jasper-ui/src/main/java/dev/jasper/ui/theme/SlateSteel.java; Removed in Plan 2 Tasks 2-3; authority=jasper-ui/src/main/java/dev/jasper/ui/themeimport/IntellijConversion.java; consumers use module values directly.
+jasper-app/src/main/java/dev/jasper/app/themeimport/EditorScheme.java,app.themeimport,EditorScheme,nonvisual-helper,3,9,jasper-app/src/test/java/dev/jasper/app/appearance/ThemeManagerTest.java,Removed in Slate Steel Plan 3; authority=jasper-ui/src/main/java/dev/jasper/ui/theme/JasperTheme.java; Removed in Plan 2 Tasks 2-3; authority=jasper-ui/src/main/java/dev/jasper/ui/themeimport/EditorScheme.java; consumers use module values directly.
+jasper-app/src/main/java/dev/jasper/app/themeimport/IntellijConversion.java,app.themeimport,IntellijConversion,nonvisual-helper,3,9,jasper-app/src/test/java/dev/jasper/app/appearance/ThemeManagerTest.java,Removed in Slate Steel Plan 3; authority=jasper-ui/src/main/java/dev/jasper/ui/theme/JasperTheme.java; Removed in Plan 2 Tasks 2-3; authority=jasper-ui/src/main/java/dev/jasper/ui/themeimport/IntellijConversion.java; consumers use module values directly.
 jasper-app/src/main/java/dev/jasper/app/themeimport/MigrationJournal.java,app.themeimport,MigrationJournal,nonvisual-helper,3,9,jasper-app/src/test/java/dev/jasper/app/application/JasperApplicationThemesTest.java,Removed in Slate Steel Plan 3; authority=jasper-app/src/main/java/dev/jasper/app/application/JasperApplication.java; Retain file identity/locking owner; pure converter's bounded reading already lives in module `ThemeConverter`.
-jasper-app/src/main/java/dev/jasper/app/themeimport/SafeXml.java,app.themeimport,SafeXml,nonvisual-helper,3,9,jasper-app/src/test/java/dev/jasper/app/application/JasperApplicationThemesTest.java;jasper-app/src/test/java/dev/jasper/app/appearance/ThemeManagerTest.java,Removed in Slate Steel Plan 3; authority=jasper-ui/src/main/java/dev/jasper/ui/theme/SlateSteel.java; Removed in Plan 2 Tasks 2-3; authority=jasper-ui/src/main/java/dev/jasper/ui/themeimport/SafeXml.java; consumers use module values directly.
-jasper-app/src/main/java/dev/jasper/app/themeimport/ThemeArchive.java,app.themeimport,ThemeArchive,nonvisual-helper,3,9,jasper-app/src/test/java/dev/jasper/app/appearance/ThemeManagerTest.java,Removed in Slate Steel Plan 3; authority=jasper-ui/src/main/java/dev/jasper/ui/theme/SlateSteel.java; Removed in Plan 2 Tasks 2-3; authority=jasper-ui/src/main/java/dev/jasper/ui/themeimport/ThemeArchive.java; consumers use module values directly.
-jasper-app/src/main/java/dev/jasper/app/themeimport/ThemeConverter.java,app.themeimport,ThemeConverter,nonvisual-helper,3,9,jasper-app/src/test/java/dev/jasper/app/application/JasperApplicationThemesTest.java,Removed in Slate Steel Plan 3; authority=jasper-ui/src/main/java/dev/jasper/ui/theme/SlateSteel.java; Removed in Plan 2 Tasks 2-3; authority=jasper-ui/src/main/java/dev/jasper/ui/themeimport/ThemeConverter.java; consumers use module values directly.
+jasper-app/src/main/java/dev/jasper/app/themeimport/SafeXml.java,app.themeimport,SafeXml,nonvisual-helper,3,9,jasper-app/src/test/java/dev/jasper/app/application/JasperApplicationThemesTest.java;jasper-app/src/test/java/dev/jasper/app/appearance/ThemeManagerTest.java,Removed in Slate Steel Plan 3; authority=jasper-ui/src/main/java/dev/jasper/ui/theme/JasperTheme.java; Removed in Plan 2 Tasks 2-3; authority=jasper-ui/src/main/java/dev/jasper/ui/themeimport/SafeXml.java; consumers use module values directly.
+jasper-app/src/main/java/dev/jasper/app/themeimport/ThemeArchive.java,app.themeimport,ThemeArchive,nonvisual-helper,3,9,jasper-app/src/test/java/dev/jasper/app/appearance/ThemeManagerTest.java,Removed in Slate Steel Plan 3; authority=jasper-ui/src/main/java/dev/jasper/ui/theme/JasperTheme.java; Removed in Plan 2 Tasks 2-3; authority=jasper-ui/src/main/java/dev/jasper/ui/themeimport/ThemeArchive.java; consumers use module values directly.
+jasper-app/src/main/java/dev/jasper/app/themeimport/ThemeConverter.java,app.themeimport,ThemeConverter,nonvisual-helper,3,9,jasper-app/src/test/java/dev/jasper/app/application/JasperApplicationThemesTest.java,Removed in Slate Steel Plan 3; authority=jasper-ui/src/main/java/dev/jasper/ui/theme/JasperTheme.java; Removed in Plan 2 Tasks 2-3; authority=jasper-ui/src/main/java/dev/jasper/ui/themeimport/ThemeConverter.java; consumers use module values directly.
 jasper-app/src/main/java/dev/jasper/app/themeimport/ThemeFiles.java,app.themeimport,ThemeFiles,nonvisual-helper,3,9,jasper-app/src/test/java/dev/jasper/app/application/JasperApplicationThemesTest.java,Removed in Slate Steel Plan 3; authority=jasper-app/src/main/java/dev/jasper/app/application/JasperApplication.java; Retain owner/limits/locks; redirect parsing/serialization to module.
 jasper-app/src/main/java/dev/jasper/app/themeimport/ThemeImporter.java,app.themeimport,ThemeImporter,nonvisual-helper,3,9,jasper-app/src/test/java/dev/jasper/app/application/JasperApplicationThemesTest.java,Removed in Slate Steel Plan 3; authority=jasper-app/src/main/java/dev/jasper/app/application/JasperApplication.java; Retain owner; consume module conversion values.
-jasper-app/src/main/java/dev/jasper/app/themeimport/ThemeLoader.java,app.themeimport,ThemeLoader,nonvisual-helper,3,9,jasper-app/src/test/java/dev/jasper/app/appearance/ThemeManagerTest.java,Removed in Slate Steel Plan 3; authority=jasper-ui/src/main/java/dev/jasper/ui/theme/SlateSteel.java; Removed in Plan 2 Tasks 2-3; authority=jasper-ui/src/main/java/dev/jasper/ui/themeimport/ThemeLoader.java; consumers use module values directly.
+jasper-app/src/main/java/dev/jasper/app/themeimport/ThemeLoader.java,app.themeimport,ThemeLoader,nonvisual-helper,3,9,jasper-app/src/test/java/dev/jasper/app/appearance/ThemeManagerTest.java,Removed in Slate Steel Plan 3; authority=jasper-ui/src/main/java/dev/jasper/ui/theme/JasperTheme.java; Removed in Plan 2 Tasks 2-3; authority=jasper-ui/src/main/java/dev/jasper/ui/themeimport/ThemeLoader.java; consumers use module values directly.
 jasper-app/src/main/java/dev/jasper/app/themeimport/ThemeMigration.java,app.themeimport,ThemeMigration,nonvisual-helper,3,9,jasper-app/src/test/java/dev/jasper/app/application/JasperApplicationThemesTest.java,Removed in Slate Steel Plan 3; authority=jasper-app/src/main/java/dev/jasper/app/application/JasperApplication.java; Retain owner/rollback guarantees; consume module algorithms.
-jasper-app/src/main/java/dev/jasper/app/themeimport/ThemeParents.java,app.themeimport,ThemeParents,nonvisual-helper,3,9,jasper-app/src/test/java/dev/jasper/app/application/JasperApplicationThemesTest.java;jasper-app/src/test/java/dev/jasper/app/appearance/ThemeManagerTest.java,Removed in Slate Steel Plan 3; authority=jasper-ui/src/main/java/dev/jasper/ui/theme/SlateSteel.java; Removed in Plan 2 Tasks 2-3; authority=jasper-ui/src/main/java/dev/jasper/ui/themeimport/ThemeParents.java; consumers use module values directly.
-jasper-app/src/main/java/dev/jasper/app/themeimport/ThemeSources.java,app.themeimport,ThemeSources,nonvisual-helper,3,9,jasper-app/src/test/java/dev/jasper/app/application/JasperApplicationThemesTest.java;jasper-app/src/test/java/dev/jasper/app/appearance/ThemeManagerTest.java,Removed in Slate Steel Plan 3; authority=jasper-ui/src/main/java/dev/jasper/ui/theme/SlateSteel.java; Removed in Plan 2 Tasks 2-3; authority=jasper-ui/src/main/java/dev/jasper/ui/themeimport/ThemeSources.java; consumers use module values directly.
+jasper-app/src/main/java/dev/jasper/app/themeimport/ThemeParents.java,app.themeimport,ThemeParents,nonvisual-helper,3,9,jasper-app/src/test/java/dev/jasper/app/application/JasperApplicationThemesTest.java;jasper-app/src/test/java/dev/jasper/app/appearance/ThemeManagerTest.java,Removed in Slate Steel Plan 3; authority=jasper-ui/src/main/java/dev/jasper/ui/theme/JasperTheme.java; Removed in Plan 2 Tasks 2-3; authority=jasper-ui/src/main/java/dev/jasper/ui/themeimport/ThemeParents.java; consumers use module values directly.
+jasper-app/src/main/java/dev/jasper/app/themeimport/ThemeSources.java,app.themeimport,ThemeSources,nonvisual-helper,3,9,jasper-app/src/test/java/dev/jasper/app/application/JasperApplicationThemesTest.java;jasper-app/src/test/java/dev/jasper/app/appearance/ThemeManagerTest.java,Removed in Slate Steel Plan 3; authority=jasper-ui/src/main/java/dev/jasper/ui/theme/JasperTheme.java; Removed in Plan 2 Tasks 2-3; authority=jasper-ui/src/main/java/dev/jasper/ui/themeimport/ThemeSources.java; consumers use module values directly.
 jasper-app/src/main/java/dev/jasper/app/themeimport/package-info.java,app.themeimport,package-info,nonvisual-helper,3,9,jasper-app/src/test/java/dev/jasper/app/application/JasperApplicationThemesTest.java;jasper-app/src/test/java/dev/jasper/app/appearance/ThemeManagerTest.java,Removed in Slate Steel Plan 3; authority=jasper-app/src/main/java/dev/jasper/app/application/JasperApplication.java; Nonvisual helper or fixture false positive; retain behavior and adapt referenced values only
 jasper-app/src/main/java/dev/jasper/app/themesui/ThemeDialogs.java,app.themesui,ThemeDialogs,standard-ui,3;6,9,jasper-app/src/test/java/dev/jasper/app/application/JasperApplicationThemesTest.java,Removed in Slate Steel Plan 3; authority=jasper-app/src/main/java/dev/jasper/app/application/JasperApplication.java; Plan 2 Task 6a migrated: owner-bound dialogs host kit SHEET forms on the Settings KitContext; message body is literal kit text. Plan3 Task9 app-unmocked: sheet(title; sections) shows section labels over cards with the one primary Close.
 jasper-app/src/main/java/dev/jasper/app/themesui/ThemeImport.java,app.themesui,ThemeImport,nonvisual-helper,3;6,9,jasper-app/src/test/java/dev/jasper/app/application/JasperApplicationThemesTest.java,"Removed in Slate Steel Plan 3; authority=jasper-app/src/main/java/dev/jasper/app/application/JasperApplication.java; Plan 2 Task 6a adapted: passes the page's kit context to its dialogs; worker, gating and reload unchanged. Plan3 Task9 app-unmocked: the report is an Imported card of name and file rows then Warnings and Not Imported cards; Title Case titles."
@@ -297,14 +297,14 @@ jasper-app/src/test/java/dev/jasper/app/appearance/ThemeButtonsTest.java,app.app
 jasper-app/src/test/java/dev/jasper/app/appearance/ThemeCatalogTest.java,app.appearance,ThemeCatalogTest,nonvisual-helper,2,10,jasper-app/src/test/java/dev/jasper/app/appearance/ThemeManagerTest.java,Removed in Slate Steel Plan 3; authority=jasper-app/src/test/java/dev/jasper/app/appearance/ThemeManagerTest.java; Test/fixture ownership; preserve behavioral assertions; Nonvisual helper or fixture false positive; retain behavior and adapt referenced values only
 jasper-app/src/test/java/dev/jasper/app/appearance/ThemeControllerStartupTest.java,app.appearance,ThemeControllerStartupTest,nonvisual-helper,5,none,none-direct,Removed in Slate Steel Plan 2; authority=jasper-app/src/test/java/dev/jasper/app/appearance/SlateSteelControllerTest.java; New in Plan 2 Task 5 review fix round 1; start-up fallback evidence: a saved theme that cannot install is rolled back and Jasper Dark is installed before any snapshot read.
 jasper-app/src/test/java/dev/jasper/app/appearance/ThemeFixtures.java,app.appearance,ThemeFixtures,nonvisual-helper,2,10,jasper-app/src/test/java/dev/jasper/app/appearance/ThemeManagerTest.java;jasper-app/src/test/java/dev/jasper/app/appearance/ChromeKeysTest.java;jasper-app/src/test/java/dev/jasper/app/appearance/GlassLevelsTest.java;jasper-app/src/test/java/dev/jasper/app/appearance/SelectionRenderingTest.java;jasper-app/src/test/java/dev/jasper/app/appearance/ThemeButtonsTest.java;jasper-ui/src/test/java/dev/jasper/ui/theme/UiColorKeysTest.java;jasper-app/src/test/java/dev/jasper/app/appearance/ThemeMigrationCoverageTest.java;jasper-app/src/test/java/dev/jasper/app/appearance/UiTypographyTest.java,Removed in Slate Steel Plan 3; authority=jasper-app/src/test/java/dev/jasper/app/appearance/ThemeManagerTest.java; Test/fixture ownership; preserve behavioral assertions; Nonvisual helper or fixture false positive; retain behavior and adapt referenced values only
-jasper-app/src/test/java/dev/jasper/app/appearance/ThemeGeneratorTest.java,app.appearance,ThemeGeneratorTest,nonvisual-helper,2,10,jasper-ui/src/test/java/dev/jasper/ui/theme/UiColorKeysTest.java,Removed in Slate Steel Plan 3; authority=jasper-ui/src/main/java/dev/jasper/ui/theme/SlateSteel.java; Moved in Plan 2 Tasks 2-3 to jasper-app/src/test/java/dev/jasper/ui/theme/ThemeGeneratorTest.java; existing regression suite retained in module owner package.
+jasper-app/src/test/java/dev/jasper/app/appearance/ThemeGeneratorTest.java,app.appearance,ThemeGeneratorTest,nonvisual-helper,2,10,jasper-ui/src/test/java/dev/jasper/ui/theme/UiColorKeysTest.java,Removed in Slate Steel Plan 3; authority=jasper-ui/src/main/java/dev/jasper/ui/theme/JasperTheme.java; Moved in Plan 2 Tasks 2-3 to jasper-app/src/test/java/dev/jasper/ui/theme/ThemeGeneratorTest.java; existing regression suite retained in module owner package.
 jasper-app/src/test/java/dev/jasper/app/appearance/ThemeManagerTest.java,app.appearance,ThemeManagerTest,standard-ui,2,10,jasper-app/src/test/java/dev/jasper/app/appearance/ThemeManagerTest.java,Test/fixture ownership; preserve behavioral assertions; Migrate standard controls and preserve owner behavior
 jasper-app/src/test/java/dev/jasper/app/appearance/ThemeMigrationCoverageTest.java,app.appearance,ThemeMigrationCoverageTest,standard-ui,2,10,jasper-app/src/test/java/dev/jasper/app/appearance/ThemeMigrationCoverageTest.java,Test/fixture ownership; preserve behavioral assertions; Migrate standard controls and preserve owner behavior
 jasper-app/src/test/java/dev/jasper/app/appearance/ThemeMigrationPreview.java,app.appearance,ThemeMigrationPreview,nonvisual-helper,2,10,jasper-app/src/test/java/dev/jasper/app/appearance/ThemeManagerTest.java;jasper-app/src/test/java/dev/jasper/app/appearance/ChromeKeysTest.java;jasper-app/src/test/java/dev/jasper/app/appearance/GlassLevelsTest.java;jasper-app/src/test/java/dev/jasper/app/appearance/SelectionRenderingTest.java;jasper-app/src/test/java/dev/jasper/app/appearance/ThemeButtonsTest.java;jasper-ui/src/test/java/dev/jasper/ui/theme/UiColorKeysTest.java;jasper-app/src/test/java/dev/jasper/app/appearance/ThemeMigrationCoverageTest.java;jasper-app/src/test/java/dev/jasper/app/appearance/UiTypographyTest.java,Removed in Slate Steel Plan 3; authority=jasper-app/src/test/java/dev/jasper/app/appearance/ThemeManagerTest.java; Test/fixture ownership; preserve behavioral assertions; Nonvisual helper or fixture false positive; retain behavior and adapt referenced values only
 jasper-app/src/test/java/dev/jasper/app/appearance/ThemeResolverTest.java,app.appearance,ThemeResolverTest,nonvisual-helper,2,10,jasper-app/src/test/java/dev/jasper/app/appearance/ThemeManagerTest.java,Removed in Slate Steel Plan 3; authority=jasper-app/src/test/java/dev/jasper/app/appearance/ThemeManagerTest.java; Test/fixture ownership; preserve behavioral assertions; Nonvisual helper or fixture false positive; retain behavior and adapt referenced values only
 jasper-app/src/test/java/dev/jasper/app/appearance/ThemeStateTest.java,app.appearance,ThemeStateTest,nonvisual-helper,2,10,jasper-app/src/test/java/dev/jasper/app/appearance/ThemeManagerTest.java,Removed in Slate Steel Plan 3; authority=jasper-app/src/test/java/dev/jasper/app/appearance/ThemeManagerTest.java; Test/fixture ownership; preserve behavioral assertions; Nonvisual helper or fixture false positive; retain behavior and adapt referenced values only
 jasper-app/src/test/java/dev/jasper/app/appearance/ThemeTestSupport.java,app.appearance,ThemeTestSupport,nonvisual-helper,2,10,jasper-app/src/test/java/dev/jasper/app/appearance/ThemeManagerTest.java;jasper-app/src/test/java/dev/jasper/app/appearance/ChromeKeysTest.java;jasper-app/src/test/java/dev/jasper/app/appearance/GlassLevelsTest.java;jasper-app/src/test/java/dev/jasper/app/appearance/SelectionRenderingTest.java;jasper-app/src/test/java/dev/jasper/app/appearance/ThemeButtonsTest.java;jasper-ui/src/test/java/dev/jasper/ui/theme/UiColorKeysTest.java;jasper-app/src/test/java/dev/jasper/app/appearance/ThemeMigrationCoverageTest.java;jasper-app/src/test/java/dev/jasper/app/appearance/UiTypographyTest.java,Test/fixture ownership; preserve behavioral assertions; Nonvisual helper or fixture false positive; retain behavior and adapt referenced values only
-jasper-app/src/test/java/dev/jasper/app/appearance/ThemeTomlTest.java,app.appearance,ThemeTomlTest,nonvisual-helper,2,10,jasper-ui/src/test/java/dev/jasper/ui/theme/UiColorKeysTest.java,Removed in Slate Steel Plan 3; authority=jasper-ui/src/main/java/dev/jasper/ui/theme/SlateSteel.java; Moved in Plan 2 Tasks 2-3 to jasper-app/src/test/java/dev/jasper/ui/themeio/ThemeTomlTest.java; existing regression suite retained in module owner package.
+jasper-app/src/test/java/dev/jasper/app/appearance/ThemeTomlTest.java,app.appearance,ThemeTomlTest,nonvisual-helper,2,10,jasper-ui/src/test/java/dev/jasper/ui/theme/UiColorKeysTest.java,Removed in Slate Steel Plan 3; authority=jasper-ui/src/main/java/dev/jasper/ui/theme/JasperTheme.java; Moved in Plan 2 Tasks 2-3 to jasper-app/src/test/java/dev/jasper/ui/themeio/ThemeTomlTest.java; existing regression suite retained in module owner package.
 jasper-app/src/test/java/dev/jasper/app/appearance/ThemeTomlWriterTest.java,app.appearance,ThemeTomlWriterTest,nonvisual-helper,2,10,jasper-app/src/test/java/dev/jasper/app/appearance/ThemeManagerTest.java,Removed in Slate Steel Plan 3; authority=jasper-app/src/test/java/dev/jasper/app/appearance/ThemeManagerTest.java; Test/fixture ownership; preserve behavioral assertions; Nonvisual helper or fixture false positive; retain behavior and adapt referenced values only
 jasper-app/src/test/java/dev/jasper/app/appearance/UiTypographyTest.java,app.appearance,UiTypographyTest,standard-ui,2,10,jasper-app/src/test/java/dev/jasper/app/appearance/UiTypographyTest.java,Test/fixture ownership; preserve behavioral assertions; Migrate standard controls and preserve owner behavior
 jasper-app/src/test/java/dev/jasper/app/appearance/WorkspaceGlassThemeTest.java,app.appearance,WorkspaceGlassThemeTest,nonvisual-helper,2,10,jasper-app/src/test/java/dev/jasper/app/appearance/ThemeManagerTest.java,Removed in Slate Steel Plan 3; authority=jasper-app/src/test/java/dev/jasper/app/appearance/ThemeManagerTest.java; Test/fixture ownership; preserve behavioral assertions; Nonvisual helper or fixture false positive; retain behavior and adapt referenced values only
@@ -632,14 +632,14 @@ jasper-app/src/test/java/dev/jasper/app/workspace/WorkspaceThemeTest.java,app.wo
 jasper-app/src/test/java/dev/jasper/app/workspace/ZenModeTest.java,app.workspace,ZenModeTest,standard-ui,5,10,jasper-app/src/test/java/dev/jasper/app/workspace/ZenModeTest.java,Test/fixture ownership; preserve behavioral assertions; Migrate standard controls and preserve owner behavior
 jasper-app/src/test/java/dev/jasper/terminal/view/TerminalKeyTestSupport.java,terminal.view,TerminalKeyTestSupport,nonvisual-helper,5,10,none-direct,Test/fixture ownership; preserve behavioral assertions; Nonvisual helper or fixture false positive; retain behavior and adapt referenced values only
 jasper-app/src/test/java/dev/jasper/ui/swing/KitMotionProbe.java,app.test-ui,KitMotionProbe,nonvisual-helper,4,4;10,jasper-app/src/test/java/dev/jasper/ui/swing/KitMotionProbe.java,Task 4 owned-view mounting/draft/motion integration; retain behavior at Task 11 API cutover
-jasper-app/src/test/java/dev/jasper/ui/theme/ThemeGeneratorTest.java,app.integration-tests,ThemeGeneratorTest,nonvisual-helper,2;3,none,none-direct,Removed in Slate Steel Plan 3; authority=jasper-ui/src/main/java/dev/jasper/ui/theme/SlateSteel.java; New in Plan 2 Tasks 2-3; module routing / preserved integration regression ownership.
+jasper-app/src/test/java/dev/jasper/ui/theme/ThemeGeneratorTest.java,app.integration-tests,ThemeGeneratorTest,nonvisual-helper,2;3,none,none-direct,Removed in Slate Steel Plan 3; authority=jasper-ui/src/main/java/dev/jasper/ui/theme/JasperTheme.java; New in Plan 2 Tasks 2-3; module routing / preserved integration regression ownership.
 jasper-app/src/test/java/dev/jasper/ui/themeimport/EditorSchemeTest.java,app.integration-tests,EditorSchemeTest,nonvisual-helper,2;3,none,none-direct,Removed in Slate Steel Plan 3; authority=jasper-app/src/test/java/dev/jasper/app/appearance/ThemeManagerTest.java; New in Plan 2 Tasks 2-3; module routing / preserved integration regression ownership.
 jasper-app/src/test/java/dev/jasper/ui/themeimport/IntellijConversionTest.java,app.integration-tests,IntellijConversionTest,nonvisual-helper,2;3,none,none-direct,Removed in Slate Steel Plan 3; authority=jasper-app/src/test/java/dev/jasper/app/appearance/ThemeManagerTest.java; New in Plan 2 Tasks 2-3; module routing / preserved integration regression ownership.
 jasper-app/src/test/java/dev/jasper/ui/themeimport/IntellijThemesTest.java,app.integration-tests,IntellijThemesTest,nonvisual-helper,2;3,none,none-direct,Removed in Slate Steel Plan 3; authority=jasper-app/src/test/java/dev/jasper/app/appearance/ThemeManagerTest.java; New in Plan 2 Tasks 2-3; module routing / preserved integration regression ownership.
 jasper-app/src/test/java/dev/jasper/ui/themeimport/LegacyThemeTestSupport.java,app.integration-tests,LegacyThemeTestSupport,nonvisual-helper,2;3,none,none-direct,Removed in Slate Steel Plan 3; authority=jasper-app/src/test/java/dev/jasper/app/appearance/ThemeManagerTest.java; New in Plan 2 Tasks 2-3; module routing / preserved integration regression ownership.
 jasper-app/src/test/java/dev/jasper/ui/themeimport/ThemeArchiveTest.java,app.integration-tests,ThemeArchiveTest,nonvisual-helper,2;3,none,none-direct,Removed in Slate Steel Plan 3; authority=jasper-app/src/test/java/dev/jasper/app/appearance/ThemeManagerTest.java; New in Plan 2 Tasks 2-3; module routing / preserved integration regression ownership.
 jasper-app/src/test/java/dev/jasper/ui/themeimport/ThemeLoaderTest.java,app.integration-tests,ThemeLoaderTest,nonvisual-helper,2;3,none,none-direct,Removed in Slate Steel Plan 3; authority=jasper-app/src/test/java/dev/jasper/app/appearance/ThemeManagerTest.java; New in Plan 2 Tasks 2-3; module routing / preserved integration regression ownership.
-jasper-app/src/test/java/dev/jasper/ui/themeio/ThemeTomlTest.java,app.integration-tests,ThemeTomlTest,nonvisual-helper,2;3,none,none-direct,Removed in Slate Steel Plan 3; authority=jasper-ui/src/main/java/dev/jasper/ui/theme/SlateSteel.java; New in Plan 2 Tasks 2-3; module routing / preserved integration regression ownership.
+jasper-app/src/test/java/dev/jasper/ui/themeio/ThemeTomlTest.java,app.integration-tests,ThemeTomlTest,nonvisual-helper,2;3,none,none-direct,Removed in Slate Steel Plan 3; authority=jasper-ui/src/main/java/dev/jasper/ui/theme/JasperTheme.java; New in Plan 2 Tasks 2-3; module routing / preserved integration regression ownership.
 jasper-sdk-testkit/src/main/java/dev/jasper/sdk/testing/FakeBundledIcon.java,sdk-testkit,FakeBundledIcon,nonvisual-helper,11,11,jasper-sdk-testkit/src/test/java/dev/jasper/sdk/testing/FakeChooserTest.java;jasper-sdk-testkit/src/test/java/dev/jasper/sdk/testing/FakeContractTest.java;jasper-sdk-testkit/src/test/java/dev/jasper/sdk/testing/FakeFileSourcesTest.java;jasper-sdk-testkit/src/test/java/dev/jasper/sdk/testing/FakeKitTest.java;jasper-sdk-testkit/src/test/java/dev/jasper/sdk/testing/FakeNotificationsTest.java;jasper-sdk-testkit/src/test/java/dev/jasper/sdk/testing/FakePaletteTest.java;jasper-sdk-testkit/src/test/java/dev/jasper/sdk/testing/FakePanelsAndWindowsTest.java;jasper-sdk-testkit/src/test/java/dev/jasper/sdk/testing/FakePluginHostTest.java;jasper-sdk-testkit/src/test/java/dev/jasper/sdk/testing/FakePluginSettingsTest.java;jasper-sdk-testkit/src/test/java/dev/jasper/sdk/testing/FakeSessionsTest.java;jasper-sdk-testkit/src/test/java/dev/jasper/sdk/testing/FakeStatusProgressTest.java;jasper-sdk-testkit/src/test/java/dev/jasper/sdk/testing/FakeTerminalsTest.java;jasper-sdk-testkit/src/test/java/dev/jasper/sdk/testing/FakeUiTest.java,Removed in Plan 2 Task 11; authority=jasper-ui/src/main/java/dev/jasper/ui/icons/IconRegistry.java; private legacy implementation/adapter contract superseded by owned kit and explicit native route.
 jasper-sdk-testkit/src/main/java/dev/jasper/sdk/testing/FakeContent.java,sdk-testkit,FakeContent,standard-ui,11,11,jasper-sdk-testkit/src/test/java/dev/jasper/sdk/testing/FakeChooserTest.java;jasper-sdk-testkit/src/test/java/dev/jasper/sdk/testing/FakeContractTest.java;jasper-sdk-testkit/src/test/java/dev/jasper/sdk/testing/FakeFileSourcesTest.java;jasper-sdk-testkit/src/test/java/dev/jasper/sdk/testing/FakeKitTest.java;jasper-sdk-testkit/src/test/java/dev/jasper/sdk/testing/FakeNotificationsTest.java;jasper-sdk-testkit/src/test/java/dev/jasper/sdk/testing/FakePaletteTest.java;jasper-sdk-testkit/src/test/java/dev/jasper/sdk/testing/FakePanelsAndWindowsTest.java;jasper-sdk-testkit/src/test/java/dev/jasper/sdk/testing/FakePluginHostTest.java;jasper-sdk-testkit/src/test/java/dev/jasper/sdk/testing/FakePluginSettingsTest.java;jasper-sdk-testkit/src/test/java/dev/jasper/sdk/testing/FakeSessionsTest.java;jasper-sdk-testkit/src/test/java/dev/jasper/sdk/testing/FakeStatusProgressTest.java;jasper-sdk-testkit/src/test/java/dev/jasper/sdk/testing/FakeTerminalsTest.java;jasper-sdk-testkit/src/test/java/dev/jasper/sdk/testing/FakeUiTest.java,SDK 0.15 fake ownership/value projection; shared host/fake contracts; no rendered standard controls
 jasper-sdk-testkit/src/main/java/dev/jasper/sdk/testing/FakeKit.java,sdk-testkit,FakeKit,nonvisual-helper,4;11,11,jasper-sdk-testkit/src/test/java/dev/jasper/sdk/testing/FakeKitTest.java;jasper-sdk-testkit/src/test/java/dev/jasper/sdk/testing/FakeKitMountingTest.java,Nonvisual helper or fixture false positive; retain behavior and adapt referenced values only; Task 4 owned-view mounting and live motion integration (transitional API removal Task 11)
diff --git a/docs/design/theme-engine/slate-steel.md b/docs/design/theme-engine/slate-steel.md
index 22bc54f7758bdf7c0025e1fd8a0936fab3002380..554edda674c18f713601c63883175fce623d30b6 100644
--- a/docs/design/theme-engine/slate-steel.md
+++ b/docs/design/theme-engine/slate-steel.md
@@ -209,8 +209,9 @@ Against the editor background, every text role is at least 4.5:1 (lowest: Light
 
 ```json
 {
+  "jasper-theme": 1, "name": "Slate Steel", "author": "Jasper",
   "ui": {
-    "slate-steel-dark": {
+    "dark": {
       "surface.app": "#1E2127", "surface.panel": "#272A31", "surface.panel.raised": "#2D3139",
       "surface.dialog": "#2D3139", "surface.card": "#272A31", "surface.sidebar": "#23262C",
       "surface.input": "#1F2228", "surface.field.raised": "#30343C", "surface.control": "#363B45",
@@ -230,7 +231,7 @@ Against the editor background, every text role is at least 4.5:1 (lowest: Light
       "status.success": "#4FD06C", "status.warning": "#F2C55C", "status.danger": "#F07F78", "status.idle": "#5B616C",
       "hue.blue": "#94B4D6", "hue.green": "#72DE8F", "hue.amber": "#F2C55C", "hue.purple": "#C8A4FF", "hue.neutral": "#D1D5DC"
     },
-    "slate-steel-light": {
+    "light": {
       "surface.app": "#E4E7EB", "surface.panel": "#F5F6F8", "surface.panel.raised": "#ECEEF1",
       "surface.dialog": "#F5F6F8", "surface.card": "#FFFFFF", "surface.sidebar": "#ECEEF1",
       "surface.input": "#FFFFFF", "surface.field.raised": "#FFFFFF", "surface.control": "#FFFFFF",
@@ -252,22 +253,22 @@ Against the editor background, every text role is at least 4.5:1 (lowest: Light
     }
   },
   "terminal": {
-    "slate-steel-dark": {
-      "bg": "#23262C", "fg": "#F4F6F8", "fg.bold": "#FFFFFF", "cursor": "#FFFFFF", "selection": "#3D5F8A",
+    "dark": {
+      "name": "Slate Steel Dark", "bg": "#23262C", "fg": "#F4F6F8", "fg.bold": "#FFFFFF", "cursor": "#FFFFFF", "selection": "#3D5F8A",
       "black": "#3A3F4A", "red": "#FF928A", "green": "#5FE88A", "yellow": "#FFD75F",
       "blue": "#6CB6FF", "magenta": "#E99CFF", "cyan": "#4FE0E8", "white": "#D9DEE5",
       "bright.black": "#99A2B1", "bright.red": "#FFC6C0", "bright.green": "#9CF5B6", "bright.yellow": "#FFE89A",
       "bright.blue": "#B2D9FF", "bright.magenta": "#F6C8FF", "bright.cyan": "#98F2F5", "bright.white": "#FFFFFF"
     },
-    "slate-steel-light": {
-      "bg": "#FBFBFC", "fg": "#1C2027", "fg.bold": "#000000", "cursor": "#1C2027", "selection": "#C7D8EE",
+    "light": {
+      "name": "Slate Steel Light", "bg": "#FBFBFC", "fg": "#1C2027", "fg.bold": "#000000", "cursor": "#1C2027", "selection": "#C7D8EE",
       "black": "#1C2027", "red": "#C8302C", "green": "#1F7A35", "yellow": "#8A6200",
       "blue": "#1F5FBF", "magenta": "#9B3FB5", "cyan": "#0F7A8A", "white": "#5D6470",
       "bright.black": "#6B7280", "bright.red": "#B8342F", "bright.green": "#23853C", "bright.yellow": "#946800",
       "bright.blue": "#2867C8", "bright.magenta": "#A847C2", "bright.cyan": "#127F91", "bright.white": "#000000"
     },
     "high-contrast": {
-      "bg": "#000000", "fg": "#FFFFFF", "fg.bold": "#FFFFFF", "cursor": "#FFFFFF", "selection": "#264F9E",
+      "name": "High Contrast", "bg": "#000000", "fg": "#FFFFFF", "fg.bold": "#FFFFFF", "cursor": "#FFFFFF", "selection": "#264F9E",
       "black": "#4D4D4D", "red": "#FF7B72", "green": "#3CFF7A", "yellow": "#FFE14D",
       "blue": "#7CB8FF", "magenta": "#FF8CFF", "cyan": "#4DF0FF", "white": "#E6E6E6",
       "bright.black": "#A6A6A6", "bright.red": "#FFA8A2", "bright.green": "#9DFFBC", "bright.yellow": "#FFF08A",
@@ -275,7 +276,7 @@ Against the editor background, every text role is at least 4.5:1 (lowest: Light
     }
   },
   "editor": {
-    "slate-steel-dark": {
+    "dark": {
       "background": "terminal.bg", "foreground": "terminal.fg", "caret": "terminal.cursor", "selection": "terminal.selection",
       "currentLine": "hover.overlay @ 5%", "gutter": "terminal.bg", "lineNumbers": "text.tertiary",
       "keyword": "terminal.ansi.magenta", "type": "terminal.ansi.cyan", "function": "terminal.ansi.blue",
@@ -285,7 +286,7 @@ Against the editor background, every text role is at least 4.5:1 (lowest: Light
       "operator": "text.secondary", "punctuation": "text.secondary", "tag": "terminal.ansi.red",
       "attribute": "terminal.ansi.yellow", "regex": "terminal.ansi.cyan", "error": "terminal.ansi.red"
     },
-    "slate-steel-light": {
+    "light": {
       "background": "terminal.bg", "foreground": "terminal.fg", "caret": "terminal.cursor", "selection": "terminal.selection",
       "currentLine": "hover.overlay @ 5%", "gutter": "terminal.bg", "lineNumbers": "text.tertiary",
       "keyword": "terminal.ansi.magenta", "type": "terminal.ansi.cyan", "function": "terminal.ansi.blue",
diff --git a/docs/ui-architecture.md b/docs/ui-architecture.md
index 8918841371c0932d9a5c3da85a96965b723b9c1f..795cc9112c76acb1316a4b52c7440befdba7bb7f 100644
--- a/docs/ui-architecture.md
+++ b/docs/ui-architecture.md
@@ -21,7 +21,7 @@ Read a Slate Steel role without constructing Swing controls:
 
 <!-- example:palette -->
 ```java
-var theme = SlateSteel.bundled().generated(true);
+var theme = JasperTheme.bundled().generated(true);
 var cardColor = theme.semantic().color(SemanticColor.SURFACE_CARD);
 ```
 
@@ -97,11 +97,11 @@ mid-way. While it moves the card paints one snapshot of its content, so it fades
 paints its own disabled alpha ahead of the rest); at rest the content paints live. The host adds the overlay to a
 layered pane and owns keyboard routing and focus; the app's `WindowOverlay` stacks them per window.
 
-`SlateSteel` reads Jasper's theme from the bundled `slate-steel.json` (the colour reference
-`docs/design/theme-engine/slate-steel.md`'s machine-readable block, kept identical by a test) with the JDK-only
-`ThemeJson` reader: the Dark and Light semantic palettes (roles the file omits follow fixed rules), the terminal
-schemes (`TerminalScheme`, `SchemeColors` with a bold foreground) and the derived editor schemes (`EditorColors`
-and syntax styles, written as references into the palettes). `generated(dark)` assembles a full `GeneratedTheme`: the
+`JasperTheme` reads a Jasper theme file with the JDK-only `ThemeJson` reader; `JasperTheme.bundled()` is Slate Steel,
+the bundled `slate-steel.json` (the colour reference `docs/design/theme-engine/slate-steel.md`'s machine-readable
+block, kept identical by a test). A theme has the Dark and/or Light semantic palettes (roles the file omits follow
+fixed rules), its terminal schemes (`TerminalScheme`, with an id and a bold foreground) and an editor scheme per
+variant (`EditorColors` and syntax styles, written as colours or as references into the palettes). `generated(dark)` assembles a full `GeneratedTheme`: the
 palette, the matching terminal scheme, the editor scheme and syntax, FlatLaf's UI keys from the roles (`UiColorKeys`)
 and the glass. Components paint theme.md's roles; alpha treatments (focus and error rings, hover, category tiles)
 apply one kit metric to a base role, and a role that carries alpha (the scrim, `border.subtle`) is painted as given.
diff --git a/jasper-app/src/main/java/dev/jasper/app/appearance/ResolvedTheme.java b/jasper-app/src/main/java/dev/jasper/app/appearance/ResolvedTheme.java
index 88e7aa093d6096d6dcaa5e005e570e58cfba8418..4d14dd5e555e191f5397fd2241aae4f2ddd5c6bc 100644
--- a/jasper-app/src/main/java/dev/jasper/app/appearance/ResolvedTheme.java
+++ b/jasper-app/src/main/java/dev/jasper/app/appearance/ResolvedTheme.java
@@ -3,7 +3,7 @@ package dev.jasper.app.appearance;
 import dev.jasper.terminal.config.Palette;
 import dev.jasper.ui.theme.GeneratedTheme;
 import dev.jasper.ui.theme.SemanticColor;
-import dev.jasper.ui.theme.SlateSteel;
+import dev.jasper.ui.theme.JasperTheme;
 import dev.jasper.ui.theme.TerminalScheme;
 import java.awt.Color;
 import java.util.Objects;
@@ -14,8 +14,8 @@ public record ResolvedTheme(GeneratedTheme chrome, GeneratedTheme workspace, Pal
 
     /** Slate Steel: one variant for chrome and workspace, and the terminal scheme's palette. */
     public static ResolvedTheme slateSteel(boolean dark, TerminalScheme scheme) {
-        var theme = SlateSteel.bundled().generated(dark);
-        return new ResolvedTheme(theme, theme, TerminalPaletteAdapter.toTerminal(SlateSteel.bundled().terminal(scheme)));
+        var theme = JasperTheme.bundled().generated(dark);
+        return new ResolvedTheme(theme, theme, TerminalPaletteAdapter.toTerminal(scheme));
     }
 
     public dev.jasper.ui.theme.ThemePair pair() { return new dev.jasper.ui.theme.ThemePair(chrome, workspace); }
diff --git a/jasper-app/src/main/java/dev/jasper/app/appearance/TerminalPaletteAdapter.java b/jasper-app/src/main/java/dev/jasper/app/appearance/TerminalPaletteAdapter.java
index a52a6d4b0940582b653531bbe5c0aaec860c3aba..91bf28b05815db4d6e32836e11250352d3bbe7f6 100644
--- a/jasper-app/src/main/java/dev/jasper/app/appearance/TerminalPaletteAdapter.java
+++ b/jasper-app/src/main/java/dev/jasper/app/appearance/TerminalPaletteAdapter.java
@@ -1,13 +1,18 @@
 package dev.jasper.app.appearance;
 
 import dev.jasper.terminal.config.Palette;
+import dev.jasper.ui.theme.TerminalColors;
+import dev.jasper.ui.theme.TerminalScheme;
+import java.awt.Color;
 
 /** Lossless module-to-terminal value boundary; constructor arguments are mapped by meaning. */
 public final class TerminalPaletteAdapter {
     private TerminalPaletteAdapter() {}
-    /** A Slate Steel terminal scheme, its bold foreground included. */
-    public static Palette toTerminal(dev.jasper.ui.theme.SchemeColors scheme) {
-        var colors = scheme.colors();
-        return new Palette(colors.foreground(), colors.background(), colors.cursor(), colors.selection(), colors.ansi(), scheme.boldForeground());
+    /** A theme's terminal scheme, its bold foreground included. */
+    public static Palette toTerminal(TerminalScheme scheme) { return toTerminal(scheme.colors(), scheme.boldForeground()); }
+
+    /** Terminal colours and the foreground for bold text. */
+    public static Palette toTerminal(TerminalColors colors, Color boldForeground) {
+        return new Palette(colors.foreground(), colors.background(), colors.cursor(), colors.selection(), colors.ansi(), boldForeground);
     }
 }
diff --git a/jasper-app/src/main/java/dev/jasper/app/appearance/ThemeController.java b/jasper-app/src/main/java/dev/jasper/app/appearance/ThemeController.java
index 643005f0d72dc020f6b79cb3f1117eb3fb002b4e..69b50c288329fbc9df083cd4621205920153d673 100644
--- a/jasper-app/src/main/java/dev/jasper/app/appearance/ThemeController.java
+++ b/jasper-app/src/main/java/dev/jasper/app/appearance/ThemeController.java
@@ -3,7 +3,7 @@ package dev.jasper.app.appearance;
 import dev.jasper.app.config.TerminalSchemeChoice;
 import dev.jasper.app.config.UiFontConfig;
 import dev.jasper.ui.theme.GeneratedTheme;
-import dev.jasper.ui.theme.SlateSteel;
+import dev.jasper.ui.theme.JasperTheme;
 import dev.jasper.ui.theme.TerminalScheme;
 import java.awt.Font;
 import javax.swing.plaf.FontUIResource;
@@ -30,7 +30,7 @@ public final class ThemeController implements AutoCloseable {
     public static final class InstallationFailure extends IllegalStateException {
         InstallationFailure(GeneratedTheme theme, RuntimeException cause) {
             super("Could not apply theme: " + theme.name()
-                + (theme == SlateSteel.bundled().generated(theme.dark()) ? (theme.dark() ? " Dark" : " Light") : ""), cause);
+                + (theme == JasperTheme.bundled().generated(theme.dark()) ? (theme.dark() ? " Dark" : " Light") : ""), cause);
         }
     }
 
@@ -89,7 +89,13 @@ public final class ThemeController implements AutoCloseable {
 
     /** The bundled scheme for a UI variant: the choice's, or with {@code MATCH} the variant's own. */
     public static TerminalScheme scheme(boolean dark, TerminalSchemeChoice choice) {
-        return choice == TerminalSchemeChoice.MATCH ? TerminalScheme.matching(dark) : TerminalScheme.of(choice.key()).orElseThrow();
+        var bundled = JasperTheme.bundled();
+        return switch (choice) {
+            case MATCH -> bundled.matching(dark);
+            case SLATE_STEEL_DARK -> bundled.matching(true);
+            case SLATE_STEEL_LIGHT -> bundled.matching(false);
+            case HIGH_CONTRAST -> bundled.scheme("high-contrast").orElseThrow();
+        };
     }
     private static ResolvedTheme resolve(Slate slate) {
         return ResolvedTheme.slateSteel(slate.dark(), scheme(slate.dark(), slate.scheme()));
diff --git a/jasper-app/src/main/java/dev/jasper/app/application/SettingsAppearance.java b/jasper-app/src/main/java/dev/jasper/app/application/SettingsAppearance.java
index 0546878b754daa5afeeefe8412cafa4736776855..e505490452fddf10327a2e85b6e1e126d3715c9c 100644
--- a/jasper-app/src/main/java/dev/jasper/app/application/SettingsAppearance.java
+++ b/jasper-app/src/main/java/dev/jasper/app/application/SettingsAppearance.java
@@ -5,7 +5,7 @@ import dev.jasper.app.platform.SystemAppearance;
 import dev.jasper.app.settingsui.AppearanceChoices;
 import dev.jasper.ui.model.Option;
 import dev.jasper.ui.theme.SemanticColor;
-import dev.jasper.ui.theme.SlateSteel;
+import dev.jasper.ui.theme.JasperTheme;
 import java.util.Optional;
 
 /** The Appearance page's values: Slate Steel's variants as previews, its workspace glass and the live OS appearance. */
@@ -13,7 +13,7 @@ final class SettingsAppearance {
     private SettingsAppearance() { }
 
     static AppearanceChoices choices(SystemAppearance system) {
-        var glass = SlateSteel.bundled().generated(true).glass();
+        var glass = JasperTheme.bundled().generated(true).glass();
         return new AppearanceChoices(preview(true), preview(false), (float) (glass.opacity() * 100), glass.blur(),
             () -> switch (system.current().preference()) {
                 case DARK -> Optional.of(true);
@@ -24,7 +24,7 @@ final class SettingsAppearance {
 
     /** A variant's preview: its input, sidebar and app surfaces, text and accent. */
     static Option.Palette preview(boolean dark) {
-        var palette = SlateSteel.bundled().palette(dark);
+        var palette = JasperTheme.bundled().palette(dark);
         return new Option.Palette(palette.color(SemanticColor.SURFACE_INPUT).getRGB(), palette.color(SemanticColor.SURFACE_SIDEBAR).getRGB(),
             palette.color(SemanticColor.SURFACE_APP).getRGB(), palette.color(SemanticColor.TEXT_PRIMARY).getRGB(),
             palette.color(SemanticColor.ACCENT).getRGB());
diff --git a/jasper-app/src/main/java/dev/jasper/app/plugins/HostedUi.java b/jasper-app/src/main/java/dev/jasper/app/plugins/HostedUi.java
index c69b0c07aa2ffb3fe3369ea10f3ddcf558e5c978..4ba12eb58dd07c533fc25353374cdd59fde9063b 100644
--- a/jasper-app/src/main/java/dev/jasper/app/plugins/HostedUi.java
+++ b/jasper-app/src/main/java/dev/jasper/app/plugins/HostedUi.java
@@ -307,7 +307,7 @@ final class HostedUi {
     dev.jasper.ui.theme.ThemePair kitThemes() {
         var source = appearanceSnapshot;
         if (source != null) return source.pair();
-        var theme = dev.jasper.ui.theme.SlateSteel.bundled().generated(variant.get() == Variant.DARK);
+        var theme = dev.jasper.ui.theme.JasperTheme.bundled().generated(variant.get() == Variant.DARK);
         return new dev.jasper.ui.theme.ThemePair(theme, theme);
     }
 
diff --git a/jasper-ui/README.md b/jasper-ui/README.md
index ccb63c02dfb7e3fb32a1c86f2e5723d52c714fff..82c58fcb2270cad017b9680865deb7352e6032c2 100644
--- a/jasper-ui/README.md
+++ b/jasper-ui/README.md
@@ -4,7 +4,7 @@ Standalone theme engine and Swing UI kit. `jasper-ui` has no dependency on the
 application, terminal, Buddy, SDK or plugins. The app adapts its configuration
 and library APIs into these values; plugins use SDK contracts and host adapters.
 
-The engine is Slate Steel (`dev.jasper.ui.theme`): `SlateSteel` reads the bundled `slate-steel.json` once and gives
+The engine is `dev.jasper.ui.theme`: `JasperTheme` reads a Jasper theme file (the bundled Slate Steel is `slate-steel.json`) and gives
 each variant's semantic palette, terminal and editor schemes, syntax styles, glass levels and FlatLaf UI keys
 (`UiColorKeys`, from the roles) as an immutable `GeneratedTheme`. There are no other themes and no theme files to
 parse. The app and plugins use this authority; [completion evidence](../docs/design/theme-engine/completion.md)
@@ -22,7 +22,7 @@ the kit. Views and bindings are EDT-confined and their owner closes them.
 Use Java 25 on JetBrains Runtime and the repository wrapper:
 
 ```sh
-./gradlew :jasper-ui:test --tests '*SlateSteel*'
+./gradlew :jasper-ui:test --tests '*SlateSteel*' --tests '*JasperTheme*'
 ./gradlew :jasper-ui:check verifyUiArchitecture
 ```
 
diff --git a/jasper-ui/src/main/java/dev/jasper/ui/theme/JasperTheme.java b/jasper-ui/src/main/java/dev/jasper/ui/theme/JasperTheme.java
new file mode 100644
index 0000000000000000000000000000000000000000..8b41915f43ee60ae0aa20968119cf3223a206c25
--- /dev/null
+++ b/jasper-ui/src/main/java/dev/jasper/ui/theme/JasperTheme.java
@@ -0,0 +1,322 @@
+package dev.jasper.ui.theme;
+
+import java.awt.Color;
+import java.io.IOException;
+import java.io.UncheckedIOException;
+import java.nio.charset.StandardCharsets;
+import java.util.ArrayList;
+import java.util.Collection;
+import java.util.EnumMap;
+import java.util.HashSet;
+import java.util.LinkedHashMap;
+import java.util.List;
+import java.util.Map;
+import java.util.Objects;
+import java.util.Optional;
+import java.util.Set;
+import java.util.function.Function;
+import java.util.regex.Pattern;
+
+/**
+ * A Jasper theme file ({@code docs/themes.md}): a name, an optional author, a Dark and/or Light UI variant, terminal
+ * schemes and an editor scheme for each UI variant. The bundled theme, Slate Steel, is {@code slate-steel.json}. A UI
+ * variant lists every role except those that follow fixed rules, never colour values: {@code border.focus} is
+ * {@code accent}, {@code surface.status} is {@code surface.app}, {@code border.identity} is {@code border.control} and
+ * each {@code hue.<name>.surface} is its hue (the kit applies the tile alpha). A variant's matching terminal scheme is
+ * the scheme with its key, {@code dark} or {@code light}. A missing, unknown or malformed entry fails loading with a
+ * {@link ThemeFileException} naming the file and the entry. Immutable.
+ */
+public final class JasperTheme {
+    /** The built-in theme's id, which no installed theme may use. */
+    public static final String BUNDLED_ID = "slate-steel";
+    /** The format this Jasper reads: {@code "jasper-theme": 1}. */
+    public static final long FORMAT = 1;
+    static final String RESOURCE = "/dev/jasper/ui/theme/slate-steel.json";
+    static final Pattern WORDS = Pattern.compile("[a-z0-9]+(-[a-z0-9]+)*");
+    static final String DARK = "dark", LIGHT = "light";
+    private static final List<String> TOP = List.of("jasper-theme", "name", "author", "ui", "terminal", "editor");
+    private static final Set<SemanticColor> RULED = Set.of(SemanticColor.BORDER_FOCUS, SemanticColor.SURFACE_STATUS,
+        SemanticColor.BORDER_IDENTITY, SemanticColor.HUE_BLUE_SURFACE, SemanticColor.HUE_GREEN_SURFACE,
+        SemanticColor.HUE_AMBER_SURFACE, SemanticColor.HUE_PURPLE_SURFACE, SemanticColor.HUE_NEUTRAL_SURFACE);
+
+    /** One UI variant with every value filled in. */
+    private record Variant(SemanticPalette palette, EditorColors editor, Map<SyntaxRole, SyntaxStyle> syntax, GeneratedTheme generated) { }
+
+    private final String id, origin, name, author;
+    private final Map<String, Object> ui, editor;
+    private final Map<String, TerminalScheme> schemes;
+    private final List<TerminalScheme> schemeList;
+    private final Variant dark, light;
+
+    private JasperTheme(Map<String, Object> root, String id) {
+        this.id = id;
+        origin = id + ".json";
+        for (String key : root.keySet()) if (!TOP.contains(key)) throw error("Unknown entry \"" + key + "\"");
+        if (!root.containsKey("jasper-theme")) throw error("Not a Jasper theme: \"jasper-theme\": " + FORMAT + " is missing");
+        if (!(root.get("jasper-theme") instanceof Long format)) throw error("jasper-theme: Expected the number " + FORMAT);
+        if (format != FORMAT) throw error("jasper-theme: Format " + format + " is not one this Jasper reads; expected " + FORMAT);
+        name = text(root, "name");
+        if (root.containsKey("author") && !(root.get("author") instanceof String)) throw error("author: Expected a string");
+        author = (String) root.getOrDefault("author", "");
+        var terminal = object(root, "terminal");
+        if (terminal.isEmpty()) throw error("terminal: Expected at least one scheme");
+        var parsed = new LinkedHashMap<String, TerminalScheme>();
+        terminal.forEach((key, value) -> parsed.put(key, scheme(key, value)));
+        schemes = parsed;
+        schemeList = List.copyOf(parsed.values());
+        ui = root.containsKey("ui") ? object(root, "ui") : Map.of();
+        if (root.containsKey("ui") && ui.isEmpty()) throw error("ui: Expected \"dark\", \"light\" or both");
+        for (var entry : ui.entrySet()) {
+            String variant = entry.getKey();
+            if (!variant.equals(DARK) && !variant.equals(LIGHT)) throw error("ui: Unknown variant \"" + variant + "\"");
+            if (!(entry.getValue() instanceof Map)) throw error("ui." + variant + ": Expected an object");
+            if (!schemes.containsKey(variant)) throw error("ui." + variant + ": Missing its matching scheme \"terminal." + variant + "\"");
+        }
+        if (ui.isEmpty()) {
+            if (root.containsKey("editor")) throw error("editor: A theme without ui has no editor schemes");
+            editor = Map.of();
+        } else {
+            editor = object(root, "editor");
+            exactly(editor.keySet(), ui.keySet(), "editor", "variant");
+            for (String variant : editor.keySet())
+                if (!(editor.get(variant) instanceof Map)) throw error("editor." + variant + ": Expected an object");
+        }
+        dark = ui.containsKey(DARK) ? build(true) : null;
+        light = ui.containsKey(LIGHT) ? build(false) : null;
+    }
+
+    /** The bundled theme, Slate Steel, read once. */
+    public static JasperTheme bundled() { return Bundled.THEME; }
+
+    private static final class Bundled {
+        static final JasperTheme THEME = load();
+        private static JasperTheme load() {
+            try (var in = JasperTheme.class.getResourceAsStream(RESOURCE)) {
+                if (in == null) throw new IllegalStateException("Missing bundled theme " + RESOURCE);
+                return parse(new String(in.readAllBytes(), StandardCharsets.UTF_8), BUNDLED_ID);
+            } catch (IOException failure) { throw new UncheckedIOException(failure); }
+        }
+    }
+
+    /** The theme in {@code json}, whose file is {@code <id>.json}; errors name that file. */
+    public static JasperTheme parse(String json, String id) {
+        if (!validId(id)) throw new IllegalArgumentException("Not a theme id: " + id);
+        return new JasperTheme(ThemeJson.parse(json, id + ".json"), id);
+    }
+
+    /** A theme id: lowercase letters and digits in dash-separated words, at most 64 characters. */
+    public static boolean validId(String id) { return id != null && id.length() <= 64 && WORDS.matcher(id).matches(); }
+
+    /** The id: the file name without {@code .json}. */
+    public String id() { return id; }
+    /** The display name. */
+    public String name() { return name; }
+    /** The author, or empty. */
+    public String author() { return author; }
+    /** Whether the theme has the Dark or Light UI variant; a terminal-only theme has neither. */
+    public boolean has(boolean dark) { return (dark ? this.dark : light) != null; }
+
+    /** The Dark or Light UI palette; the variant must exist. */
+    public SemanticPalette palette(boolean dark) { return variant(dark).palette(); }
+    /** The Dark or Light editor scheme's surfaces; the variant must exist. */
+    public EditorColors editor(boolean dark) { return variant(dark).editor(); }
+    /** The Dark or Light editor scheme's syntax styles, one for every role; the variant must exist. */
+    public Map<SyntaxRole, SyntaxStyle> syntax(boolean dark) { return variant(dark).syntax(); }
+    /** The Dark or Light variant as a complete generated theme; the variant must exist. */
+    public GeneratedTheme generated(boolean dark) { return variant(dark).generated(); }
+    /** The Dark or Light variant's matching terminal scheme; the variant must exist. */
+    public TerminalScheme matching(boolean dark) { variant(dark); return schemes.get(dark ? DARK : LIGHT); }
+    /** The terminal schemes, in the file's order. */
+    public List<TerminalScheme> schemes() { return schemeList; }
+    /** The scheme with this key. */
+    public Optional<TerminalScheme> scheme(String key) { return Optional.ofNullable(schemes.get(key)); }
+
+    @Override public String toString() { return "JasperTheme[" + id + "]"; }
+
+    private Variant variant(boolean dark) {
+        var variant = dark ? this.dark : light;
+        if (variant == null) throw new IllegalStateException(name + " has no " + (dark ? "Dark" : "Light") + " variant");
+        return variant;
+    }
+
+    private Variant build(boolean dark) {
+        var palette = palette(dark ? DARK : LIGHT);
+        var styles = new EnumMap<SyntaxRole, SyntaxStyle>(SyntaxRole.class);
+        var colors = editor(dark, palette, styles);
+        var syntax = Map.copyOf(styles);
+        var scheme = schemes.get(dark ? DARK : LIGHT);
+        var generated = new GeneratedTheme(name, dark, author, palette, scheme.colors(), colors, syntax,
+            UiColorKeys.flatLaf(palette, scheme.colors().selection()), GlassLevels.OPAQUE);
+        return new Variant(palette, colors, syntax, generated);
+    }
+
+    private SemanticPalette palette(String variant) {
+        @SuppressWarnings("unchecked") var values = (Map<String, Object>) ui.get(variant);
+        String where = "ui." + variant;
+        var colors = new EnumMap<SemanticColor, Color>(SemanticColor.class);
+        values.forEach((key, value) -> {
+            var role = SemanticColor.of(key).filter(r -> !RULED.contains(r))
+                .orElseThrow(() -> error(where + ": Unknown colour \"" + key + "\""));
+            if (!(value instanceof String text)) throw error(where + "." + key + ": Expected a colour");
+            colors.put(role, color(text, origin, where + "." + key));
+        });
+        for (var role : SemanticColor.values()) {
+            if (RULED.contains(role)) continue;
+            if (!colors.containsKey(role)) throw error(where + ": Missing colour \"" + role.key() + "\"");
+        }
+        colors.put(SemanticColor.BORDER_FOCUS, colors.get(SemanticColor.ACCENT));
+        colors.put(SemanticColor.SURFACE_STATUS, colors.get(SemanticColor.SURFACE_APP));
+        colors.put(SemanticColor.BORDER_IDENTITY, colors.get(SemanticColor.BORDER_CONTROL));
+        colors.put(SemanticColor.HUE_BLUE_SURFACE, colors.get(SemanticColor.HUE_BLUE));
+        colors.put(SemanticColor.HUE_GREEN_SURFACE, colors.get(SemanticColor.HUE_GREEN));
+        colors.put(SemanticColor.HUE_AMBER_SURFACE, colors.get(SemanticColor.HUE_AMBER));
+        colors.put(SemanticColor.HUE_PURPLE_SURFACE, colors.get(SemanticColor.HUE_PURPLE));
+        colors.put(SemanticColor.HUE_NEUTRAL_SURFACE, colors.get(SemanticColor.HUE_NEUTRAL));
+        return new SemanticPalette(colors);
+    }
+
+    static final List<String> ANSI = List.of("black", "red", "green", "yellow", "blue", "magenta", "cyan", "white");
+    static final List<String> TERMINAL_KEYS = terminalKeys();
+    private static List<String> terminalKeys() {
+        var keys = new ArrayList<>(List.of("bg", "fg", "fg.bold", "cursor", "selection"));
+        keys.addAll(ANSI);
+        for (String name : ANSI) keys.add("bright." + name);
+        return List.copyOf(keys);
+    }
+
+    private TerminalScheme scheme(String key, Object value) {
+        if (!SchemeId.validKey(key))
+            throw error("terminal: Scheme key \"" + key + "\" must be lowercase letters, digits and dashes, at most 32 characters");
+        String where = "terminal." + key;
+        if (!(value instanceof Map<?, ?> map)) throw error(where + ": Expected an object");
+        @SuppressWarnings("unchecked") var values = (Map<String, Object>) map;
+        if (!values.containsKey("name")) throw error(where + ": Missing entry \"name\"");
+        if (!(values.get("name") instanceof String schemeName) || schemeName.isBlank())
+            throw error(where + ".name: Expected a non-empty string");
+        var colours = new HashSet<>(values.keySet());
+        colours.remove("name");
+        exactly(colours, TERMINAL_KEYS, where, "colour");
+        Function<String, Color> get = colour -> {
+            if (!(values.get(colour) instanceof String text)) throw error(where + "." + colour + ": Expected a colour");
+            return color(text, origin, where + "." + colour);
+        };
+        var ansi = new ArrayList<Color>(16);
+        for (String name : ANSI) ansi.add(get.apply(name));
+        for (String name : ANSI) ansi.add(get.apply("bright." + name));
+        return new TerminalScheme(new SchemeId(id, key), schemeName,
+            new TerminalColors(get.apply("bg"), get.apply("fg"), get.apply("cursor"), get.apply("selection"), ansi), get.apply("fg.bold"));
+    }
+
+    static final List<String> EDITOR_SURFACES = List.of("background", "foreground", "caret", "selection", "currentLine", "gutter", "lineNumbers");
+
+    private EditorColors editor(boolean dark, SemanticPalette palette, Map<SyntaxRole, SyntaxStyle> syntax) {
+        String variant = dark ? DARK : LIGHT, where = "editor." + variant;
+        @SuppressWarnings("unchecked") var values = (Map<String, Object>) editor.get(variant);
+        var expected = new HashSet<>(EDITOR_SURFACES);
+        for (var role : SyntaxRole.values()) expected.add(role.key());
+        exactly(values.keySet(), expected, where, "entry");
+        Function<String, String> text = key -> {
+            if (!(values.get(key) instanceof String value)) throw error(where + "." + key + ": Expected a reference");
+            return value;
+        };
+        Color background = reference(text.apply("background"), dark, palette, null, where + ".background").color();
+        Function<String, Color> surface = key -> {
+            var resolved = reference(text.apply(key), dark, palette, background, where + "." + key);
+            if (resolved.bold() || resolved.italic()) throw error(where + "." + key + ": A surface takes no style");
+            return resolved.color();
+        };
+        for (var role : SyntaxRole.values())
+            syntax.put(role, reference(text.apply(role.key()), dark, palette, background, where + "." + role.key()));
+        return new EditorColors(background, surface.apply("foreground"), surface.apply("caret"), surface.apply("selection"),
+            surface.apply("currentLine"), surface.apply("gutter"), surface.apply("lineNumbers"));
+    }
+
+    /**
+     * {@code <colour>[ @ <N>%][, italic][, bold]} resolved for a variant, where the colour is {@code #RRGGBB}, a
+     * {@code terminal.} reference to the matching scheme or a role ({@link SyntaxStyle} doubles as the resolved value: a
+     * colour and style flags); a share lays the colour over {@code background}.
+     */
+    private SyntaxStyle reference(String value, boolean dark, SemanticPalette palette, Color background, String where) {
+        String[] parts = value.split(",");
+        boolean bold = false, italic = false;
+        for (int i = 1; i < parts.length; i++) {
+            switch (parts[i].strip()) {
+                case "italic" -> italic = true;
+                case "bold" -> bold = true;
+                default -> throw error(where + ": Unknown style \"" + parts[i].strip() + "\"");
+            }
+        }
+        String[] shared = parts[0].split("@", -1);
+        if (shared.length > 2) throw error(where + ": Expected one share, got \"" + value + "\"");
+        String key = shared[0].strip();
+        Color base;
+        if (key.startsWith("#")) {
+            if (!key.matches("#[0-9A-Fa-f]{6}")) throw error(where + ": Expected #RRGGBB or a reference, got \"" + key + "\"");
+            base = color(key, origin, where);
+        } else if (key.startsWith("terminal.")) {
+            base = terminalReference(key, dark, where);
+        } else {
+            base = SemanticColor.of(key).map(palette::color).orElseThrow(() -> error(where + ": Unknown reference \"" + key + "\""));
+        }
+        if (shared.length == 2) {
+            String share = shared[1].strip();
+            if (background == null) throw error(where + ": The background cannot be laid over itself");
+            if (!share.matches("\\d{1,3}%")) throw error(where + ": Expected a share such as 5%, got \"" + share + "\"");
+            int percent = Integer.parseInt(share.substring(0, share.length() - 1));
+            if (percent < 1 || percent > 100) throw error(where + ": A share is from 1% to 100%, got " + share);
+            base = ColorMath.mix(base, background, percent / 100.0);
+        }
+        return new SyntaxStyle(base, bold, italic);
+    }
+
+    private Color terminalReference(String key, boolean dark, String where) {
+        var colors = schemes.get(dark ? DARK : LIGHT);
+        String name = key.substring("terminal.".length());
+        switch (name) {
+            case "bg": return colors.colors().background();
+            case "fg": return colors.colors().foreground();
+            case "fg.bold": return colors.boldForeground();
+            case "cursor": return colors.colors().cursor();
+            case "selection": return colors.colors().selection();
+            default:
+        }
+        if (name.startsWith("ansi.bright.") && ANSI.contains(name.substring("ansi.bright.".length())))
+            return colors.colors().ansi().get(8 + ANSI.indexOf(name.substring("ansi.bright.".length())));
+        if (name.startsWith("ansi.") && ANSI.contains(name.substring("ansi.".length())))
+            return colors.colors().ansi().get(ANSI.indexOf(name.substring("ansi.".length())));
+        throw error(where + ": Unknown reference \"" + key + "\"");
+    }
+
+    /** {@code #RRGGBB}, or {@code #RRGGBBAA} with alpha; {@code where} names the entry in errors. */
+    static Color color(String text, String origin, String where) {
+        if (text.matches("#[0-9A-Fa-f]{6}")) return new Color(Integer.parseInt(text.substring(1), 16));
+        if (text.matches("#[0-9A-Fa-f]{8}")) {
+            long value = Long.parseLong(text.substring(1), 16);
+            return new Color((int) (value >> 24) & 0xFF, (int) (value >> 16) & 0xFF, (int) (value >> 8) & 0xFF, (int) value & 0xFF);
+        }
+        throw new ThemeFileException(origin, 0, where + ": Expected #RRGGBB or #RRGGBBAA, got \"" + text + "\"");
+    }
+
+    /** {@code keys} exactly: names the first missing, then the first unknown entry. */
+    private void exactly(Set<?> actual, Collection<String> keys, String where, String noun) {
+        for (String key : keys) if (!actual.contains(key)) throw error(where + ": Missing " + noun + " \"" + key + "\"");
+        for (Object key : actual) if (!keys.contains(key)) throw error(where + ": Unknown " + noun + " \"" + key + "\"");
+    }
+
+    /** The object at {@code key} of the top level, which must be present. */
+    @SuppressWarnings("unchecked")
+    private Map<String, Object> object(Map<String, Object> root, String key) {
+        if (!root.containsKey(key)) throw error("Missing object \"" + key + "\"");
+        if (!(root.get(key) instanceof Map<?, ?> map)) throw error(key + ": Expected an object");
+        return (Map<String, Object>) map;
+    }
+
+    /** The non-empty string at {@code key} of the top level, which must be present. */
+    private String text(Map<String, Object> root, String key) {
+        if (!root.containsKey(key)) throw error("Missing entry \"" + key + "\"");
+        if (!(root.get(key) instanceof String value) || value.isBlank()) throw error(key + ": Expected a non-empty string");
+        return value;
+    }
+
+    private ThemeFileException error(String message) { return new ThemeFileException(origin, 0, Objects.requireNonNull(message)); }
+}
diff --git a/jasper-ui/src/main/java/dev/jasper/ui/theme/SchemeColors.java b/jasper-ui/src/main/java/dev/jasper/ui/theme/SchemeColors.java
deleted file mode 100644
index af2304e7497212e752772d73d63073ef1754e946..0000000000000000000000000000000000000000
--- a/jasper-ui/src/main/java/dev/jasper/ui/theme/SchemeColors.java
+++ /dev/null
@@ -1,13 +0,0 @@
-package dev.jasper.ui.theme;
-
-import java.awt.Color;
-import java.util.Objects;
-
-/**
- * A terminal scheme's colours: the terminal palette and the foreground for bold or emphasized text.
- * @param colors background, foreground, cursor, selection and the 16 ANSI colours
- * @param boldForeground the default foreground for bold text
- */
-public record SchemeColors(TerminalColors colors, Color boldForeground) {
-    public SchemeColors { Objects.requireNonNull(colors, "colors"); Objects.requireNonNull(boldForeground, "boldForeground"); }
-}
diff --git a/jasper-ui/src/main/java/dev/jasper/ui/theme/SchemeId.java b/jasper-ui/src/main/java/dev/jasper/ui/theme/SchemeId.java
new file mode 100644
index 0000000000000000000000000000000000000000..a7b6a339eaa26938ddfa7bd31b66b817652d4e76
--- /dev/null
+++ b/jasper-ui/src/main/java/dev/jasper/ui/theme/SchemeId.java
@@ -0,0 +1,28 @@
+package dev.jasper.ui.theme;
+
+import java.util.Optional;
+
+/**
+ * A terminal scheme's id: its theme's id and its key in that theme's file, written {@code theme/key}, such as
+ * {@code slate-steel/high-contrast}.
+ */
+public record SchemeId(String theme, String key) {
+    public SchemeId {
+        if (!JasperTheme.validId(theme)) throw new IllegalArgumentException("Not a theme id: " + theme);
+        if (!validKey(key)) throw new IllegalArgumentException("Not a scheme key: " + key);
+    }
+
+    /** A scheme key: lowercase letters and digits in dash-separated words, at most 32 characters. */
+    public static boolean validKey(String key) { return key != null && key.length() <= 32 && JasperTheme.WORDS.matcher(key).matches(); }
+
+    /** The id {@code text} names, or empty when it is not {@code <theme>/<key>}. */
+    public static Optional<SchemeId> parse(String text) {
+        int slash = text == null ? -1 : text.indexOf('/');
+        if (slash < 0) return Optional.empty();
+        String theme = text.substring(0, slash), key = text.substring(slash + 1);
+        return JasperTheme.validId(theme) && validKey(key) ? Optional.of(new SchemeId(theme, key)) : Optional.empty();
+    }
+
+    /** {@code theme/key}, as config.toml writes it. */
+    @Override public String toString() { return theme + "/" + key; }
+}
diff --git a/jasper-ui/src/main/java/dev/jasper/ui/theme/SlateSteel.java b/jasper-ui/src/main/java/dev/jasper/ui/theme/SlateSteel.java
deleted file mode 100644
index 0e80c253dfeb5c0f3a0c9f572c2714685e1f34c6..0000000000000000000000000000000000000000
--- a/jasper-ui/src/main/java/dev/jasper/ui/theme/SlateSteel.java
+++ /dev/null
@@ -1,253 +0,0 @@
-package dev.jasper.ui.theme;
-
-import java.awt.Color;
-import java.io.IOException;
-import java.io.UncheckedIOException;
-import java.nio.charset.StandardCharsets;
-import java.util.ArrayList;
-import java.util.Collection;
-import java.util.EnumMap;
-import java.util.HashSet;
-import java.util.LinkedHashMap;
-import java.util.List;
-import java.util.Map;
-import java.util.Objects;
-import java.util.Set;
-import java.util.function.Function;
-
-/**
- * Jasper's theme, Slate Steel, from its bundled file {@code slate-steel.json} (the colour reference
- * {@code docs/design/theme-engine/slate-steel.md}'s machine-readable block): the Dark and Light UI palettes, the
- * terminal schemes and the derived editor schemes. Roles the file does not list follow fixed rules, never colour
- * values: {@code border.focus} is {@code accent}, {@code surface.status} is {@code surface.app}, {@code border.identity}
- * is {@code border.control} and each {@code hue.<name>.surface} is its hue (the kit applies the tile alpha). A role with
- * neither a value nor a rule, an unknown key or a malformed colour fails loading. Immutable.
- */
-public final class SlateSteel {
-    static final String RESOURCE = "/dev/jasper/ui/theme/slate-steel.json";
-    static final String DARK = "slate-steel-dark", LIGHT = "slate-steel-light";
-    private static final Set<SemanticColor> RULED = Set.of(SemanticColor.BORDER_FOCUS, SemanticColor.SURFACE_STATUS,
-        SemanticColor.BORDER_IDENTITY, SemanticColor.HUE_BLUE_SURFACE, SemanticColor.HUE_GREEN_SURFACE,
-        SemanticColor.HUE_AMBER_SURFACE, SemanticColor.HUE_PURPLE_SURFACE, SemanticColor.HUE_NEUTRAL_SURFACE);
-
-    private final String origin;
-    private final Map<String, Object> ui, terminal, editor;
-    private final SemanticPalette dark, light;
-    private final Map<TerminalScheme, SchemeColors> schemes;
-    private final EditorColors darkEditor, lightEditor;
-    private final Map<SyntaxRole, SyntaxStyle> darkSyntax, lightSyntax;
-    private final GeneratedTheme darkTheme, lightTheme;
-
-    private SlateSteel(Map<String, Object> root, String origin) {
-        this.origin = origin;
-        objects(root, "", List.of("ui", "terminal", "editor"));
-        ui = objects(root.get("ui"), "ui", List.of(DARK, LIGHT));
-        terminal = objects(root.get("terminal"), "terminal", List.of(DARK, LIGHT, "high-contrast"));
-        var parsed = new EnumMap<TerminalScheme, SchemeColors>(TerminalScheme.class);
-        for (var scheme : TerminalScheme.values()) parsed.put(scheme, scheme(scheme));
-        schemes = Map.copyOf(parsed);
-        editor = objects(root.get("editor"), "editor", List.of(DARK, LIGHT));
-        dark = palette(DARK);
-        light = palette(LIGHT);
-        var darkStyles = new EnumMap<SyntaxRole, SyntaxStyle>(SyntaxRole.class);
-        darkEditor = editor(true, darkStyles);
-        darkSyntax = Map.copyOf(darkStyles);
-        var lightStyles = new EnumMap<SyntaxRole, SyntaxStyle>(SyntaxRole.class);
-        lightEditor = editor(false, lightStyles);
-        lightSyntax = Map.copyOf(lightStyles);
-        darkTheme = generate(true);
-        lightTheme = generate(false);
-    }
-
-    /** The bundled theme, read once. */
-    public static SlateSteel bundled() { return Bundled.THEME; }
-
-    private static final class Bundled {
-        static final SlateSteel THEME = load();
-        private static SlateSteel load() {
-            try (var in = SlateSteel.class.getResourceAsStream(RESOURCE)) {
-                if (in == null) throw new IllegalStateException("Missing bundled theme " + RESOURCE);
-                return parse(new String(in.readAllBytes(), StandardCharsets.UTF_8), "slate-steel.json");
-            } catch (IOException failure) { throw new UncheckedIOException(failure); }
-        }
-    }
-
-    /** A theme file in the bundled file's shape; {@code origin} names it in errors. */
-    static SlateSteel parse(String json, String origin) { return new SlateSteel(ThemeJson.parse(json, origin), origin); }
-
-    /** The Dark or Light UI palette. */
-    public SemanticPalette palette(boolean dark) { return dark ? this.dark : light; }
-
-    private SemanticPalette palette(String variant) {
-        @SuppressWarnings("unchecked") var values = (Map<String, Object>) ui.get(variant);
-        String where = "ui." + variant;
-        var colors = new EnumMap<SemanticColor, Color>(SemanticColor.class);
-        values.forEach((key, value) -> {
-            var role = SemanticColor.of(key).filter(r -> !RULED.contains(r))
-                .orElseThrow(() -> error(where + ": Unknown colour \"" + key + "\""));
-            if (!(value instanceof String text)) throw error(where + "." + key + ": Expected a colour");
-            colors.put(role, color(text, origin, where + "." + key));
-        });
-        for (var role : SemanticColor.values()) {
-            if (RULED.contains(role)) continue;
-            if (!colors.containsKey(role)) throw error(where + ": Missing colour \"" + role.key() + "\"");
-        }
-        colors.put(SemanticColor.BORDER_FOCUS, colors.get(SemanticColor.ACCENT));
-        colors.put(SemanticColor.SURFACE_STATUS, colors.get(SemanticColor.SURFACE_APP));
-        colors.put(SemanticColor.BORDER_IDENTITY, colors.get(SemanticColor.BORDER_CONTROL));
-        colors.put(SemanticColor.HUE_BLUE_SURFACE, colors.get(SemanticColor.HUE_BLUE));
-        colors.put(SemanticColor.HUE_GREEN_SURFACE, colors.get(SemanticColor.HUE_GREEN));
-        colors.put(SemanticColor.HUE_AMBER_SURFACE, colors.get(SemanticColor.HUE_AMBER));
-        colors.put(SemanticColor.HUE_PURPLE_SURFACE, colors.get(SemanticColor.HUE_PURPLE));
-        colors.put(SemanticColor.HUE_NEUTRAL_SURFACE, colors.get(SemanticColor.HUE_NEUTRAL));
-        return new SemanticPalette(colors);
-    }
-
-    static final List<String> ANSI = List.of("black", "red", "green", "yellow", "blue", "magenta", "cyan", "white");
-    static final List<String> TERMINAL_KEYS = terminalKeys();
-    private static List<String> terminalKeys() {
-        var keys = new ArrayList<>(List.of("bg", "fg", "fg.bold", "cursor", "selection"));
-        keys.addAll(ANSI);
-        for (String name : ANSI) keys.add("bright." + name);
-        return List.copyOf(keys);
-    }
-
-    /** A terminal scheme's colours. */
-    public SchemeColors terminal(TerminalScheme scheme) { return schemes.get(Objects.requireNonNull(scheme)); }
-
-    private SchemeColors scheme(TerminalScheme scheme) {
-        @SuppressWarnings("unchecked") var values = (Map<String, Object>) terminal.get(scheme.key());
-        String where = "terminal." + scheme.key();
-        exactly(values.keySet(), TERMINAL_KEYS, where, "colour");
-        Function<String, Color> get = key -> {
-            if (!(values.get(key) instanceof String text)) throw error(where + "." + key + ": Expected a colour");
-            return color(text, origin, where + "." + key);
-        };
-        var ansi = new ArrayList<Color>(16);
-        for (String name : ANSI) ansi.add(get.apply(name));
-        for (String name : ANSI) ansi.add(get.apply("bright." + name));
-        return new SchemeColors(new TerminalColors(get.apply("bg"), get.apply("fg"), get.apply("cursor"), get.apply("selection"), ansi),
-            get.apply("fg.bold"));
-    }
-
-    static final List<String> EDITOR_SURFACES = List.of("background", "foreground", "caret", "selection", "currentLine", "gutter", "lineNumbers");
-
-    /** The Dark or Light editor scheme's surfaces. */
-    public EditorColors editor(boolean dark) { return dark ? darkEditor : lightEditor; }
-
-    /** The Dark or Light editor scheme's syntax styles, one for every role. */
-    public Map<SyntaxRole, SyntaxStyle> syntax(boolean dark) { return dark ? darkSyntax : lightSyntax; }
-
-    private EditorColors editor(boolean dark, Map<SyntaxRole, SyntaxStyle> syntax) {
-        String variant = dark ? DARK : LIGHT, where = "editor." + variant;
-        @SuppressWarnings("unchecked") var values = (Map<String, Object>) editor.get(variant);
-        var expected = new HashSet<>(EDITOR_SURFACES);
-        for (var role : SyntaxRole.values()) expected.add(role.key());
-        exactly(values.keySet(), expected, where, "entry");
-        Function<String, String> text = key -> {
-            if (!(values.get(key) instanceof String value)) throw error(where + "." + key + ": Expected a reference");
-            return value;
-        };
-        Color background = reference(text.apply("background"), dark, null, where + ".background").color();
-        Function<String, Color> surface = key -> {
-            var resolved = reference(text.apply(key), dark, background, where + "." + key);
-            if (resolved.bold() || resolved.italic()) throw error(where + "." + key + ": A surface takes no style");
-            return resolved.color();
-        };
-        for (var role : SyntaxRole.values()) syntax.put(role, reference(text.apply(role.key()), dark, background, where + "." + role.key()));
-        return new EditorColors(background, surface.apply("foreground"), surface.apply("caret"), surface.apply("selection"),
-            surface.apply("currentLine"), surface.apply("gutter"), surface.apply("lineNumbers"));
-    }
-
-    /**
-     * {@code <reference>[ @ <N>%][, italic][, bold]} resolved for a variant ({@link SyntaxStyle} doubles as the resolved
-     * reference: a colour and style flags); a share lays the reference over {@code background}.
-     */
-    private SyntaxStyle reference(String value, boolean dark, Color background, String where) {
-        String[] parts = value.split(",");
-        boolean bold = false, italic = false;
-        for (int i = 1; i < parts.length; i++) {
-            switch (parts[i].strip()) {
-                case "italic" -> italic = true;
-                case "bold" -> bold = true;
-                default -> throw error(where + ": Unknown style \"" + parts[i].strip() + "\"");
-            }
-        }
-        String[] shared = parts[0].split("@", -1);
-        if (shared.length > 2) throw error(where + ": Expected one share, got \"" + value + "\"");
-        String key = shared[0].strip();
-        Color base = key.startsWith("terminal.") ? terminalReference(key, dark, where)
-            : SemanticColor.of(key).map(role -> palette(dark).color(role))
-                .orElseThrow(() -> error(where + ": Unknown reference \"" + key + "\""));
-        if (shared.length == 2) {
-            String share = shared[1].strip();
-            if (background == null) throw error(where + ": The background cannot be laid over itself");
-            if (!share.matches("\\d{1,3}%")) throw error(where + ": Expected a share such as 5%, got \"" + share + "\"");
-            int percent = Integer.parseInt(share.substring(0, share.length() - 1));
-            if (percent < 1 || percent > 100) throw error(where + ": A share is from 1% to 100%, got " + share);
-            base = ColorMath.mix(base, background, percent / 100.0);
-        }
-        return new SyntaxStyle(base, bold, italic);
-    }
-
-    private Color terminalReference(String key, boolean dark, String where) {
-        var colors = terminal(TerminalScheme.matching(dark));
-        String name = key.substring("terminal.".length());
-        switch (name) {
-            case "bg": return colors.colors().background();
-            case "fg": return colors.colors().foreground();
-            case "fg.bold": return colors.boldForeground();
-            case "cursor": return colors.colors().cursor();
-            case "selection": return colors.colors().selection();
-            default:
-        }
-        if (name.startsWith("ansi.bright.") && ANSI.contains(name.substring("ansi.bright.".length())))
-            return colors.colors().ansi().get(8 + ANSI.indexOf(name.substring("ansi.bright.".length())));
-        if (name.startsWith("ansi.") && ANSI.contains(name.substring("ansi.".length())))
-            return colors.colors().ansi().get(ANSI.indexOf(name.substring("ansi.".length())));
-        throw error(where + ": Unknown reference \"" + key + "\"");
-    }
-
-    /** The Dark or Light variant as a complete generated theme. */
-    public GeneratedTheme generated(boolean dark) { return dark ? darkTheme : lightTheme; }
-
-    private GeneratedTheme generate(boolean dark) {
-        var palette = palette(dark);
-        var scheme = terminal(TerminalScheme.matching(dark));
-        return new GeneratedTheme("Slate Steel", dark, "Jasper", palette, scheme.colors(), editor(dark), syntax(dark),
-            uiKeys(palette, scheme), GlassLevels.OPAQUE);
-    }
-
-    /** FlatLaf's colour keys from the variant's roles; FlatLafInstaller adds the metric keys and the popup shadow. */
-    private static Map<String, Object> uiKeys(SemanticPalette palette, SchemeColors scheme) {
-        return UiColorKeys.flatLaf(palette, scheme.colors().selection());
-    }
-
-    /** {@code #RRGGBB}, or {@code #RRGGBBAA} with alpha; {@code where} names the entry in errors. */
-    static Color color(String text, String origin, String where) {
-        if (text.matches("#[0-9A-Fa-f]{6}")) return new Color(Integer.parseInt(text.substring(1), 16));
-        if (text.matches("#[0-9A-Fa-f]{8}")) {
-            long value = Long.parseLong(text.substring(1), 16);
-            return new Color((int) (value >> 24) & 0xFF, (int) (value >> 16) & 0xFF, (int) (value >> 8) & 0xFF, (int) value & 0xFF);
-        }
-        throw new ThemeFileException(origin, 0, where + ": Expected #RRGGBB or #RRGGBBAA, got \"" + text + "\"");
-    }
-
-    /** {@code keys} exactly: names the first missing, then the first unknown entry. */
-    private void exactly(Set<?> actual, Collection<String> keys, String where, String noun) {
-        for (String key : keys) if (!actual.contains(key)) throw error(where + ": Missing " + noun + " \"" + key + "\"");
-        for (Object key : actual) if (!keys.contains(key)) throw error(where + ": Unknown " + noun + " \"" + key + "\"");
-    }
-
-    /** The object at {@code value}, whose keys must be exactly {@code keys}, each an object. */
-    @SuppressWarnings("unchecked")
-    private Map<String, Object> objects(Object value, String where, List<String> keys) {
-        if (!(value instanceof Map<?, ?> map)) throw error((where.isEmpty() ? "" : where + ": ") + "Expected the objects " + keys);
-        exactly(map.keySet(), keys, where.isEmpty() ? "slate-steel.json" : where, "object");
-        for (String key : keys)
-            if (!(map.get(key) instanceof Map)) throw error((where.isEmpty() ? key : where + "." + key) + ": Expected an object");
-        return (Map<String, Object>) map;
-    }
-
-    ThemeFileException error(String message) { return new ThemeFileException(origin, 0, Objects.requireNonNull(message)); }
-}
diff --git a/jasper-ui/src/main/java/dev/jasper/ui/theme/TerminalScheme.java b/jasper-ui/src/main/java/dev/jasper/ui/theme/TerminalScheme.java
index 4d3f1301a4300104b668c1a5bc3b10ca5a0971d3..94137721179d89f4042120919b7f13577dfb8726 100644
--- a/jasper-ui/src/main/java/dev/jasper/ui/theme/TerminalScheme.java
+++ b/jasper-ui/src/main/java/dev/jasper/ui/theme/TerminalScheme.java
@@ -1,23 +1,18 @@
 package dev.jasper.ui.theme;
 
-import java.util.Optional;
+import java.awt.Color;
+import java.util.Objects;
 
-/** The terminal colour schemes Jasper ships; each UI variant pairs with its own by default. */
-public enum TerminalScheme {
-    SLATE_STEEL_DARK("slate-steel-dark"), SLATE_STEEL_LIGHT("slate-steel-light"), HIGH_CONTRAST("high-contrast");
-
-    private final String key;
-    TerminalScheme(String key) { this.key = key; }
-
-    /** The scheme's key in the theme file and in configuration, for example {@code high-contrast}. */
-    public String key() { return key; }
-
-    /** The scheme with this key. */
-    public static Optional<TerminalScheme> of(String key) {
-        for (var scheme : values()) if (scheme.key.equals(key)) return Optional.of(scheme);
-        return Optional.empty();
+/**
+ * One of a theme's terminal schemes.
+ * @param id its theme's id and its key
+ * @param name the name Settings and the command palette show
+ * @param colors background, foreground, cursor, selection and the 16 ANSI colours
+ * @param boldForeground the default foreground for bold text
+ */
+public record TerminalScheme(SchemeId id, String name, TerminalColors colors, Color boldForeground) {
+    public TerminalScheme {
+        Objects.requireNonNull(id, "id"); Objects.requireNonNull(colors, "colors"); Objects.requireNonNull(boldForeground, "boldForeground");
+        if (name == null || name.isBlank()) throw new IllegalArgumentException("A terminal scheme needs a name");
     }
-
-    /** The scheme that matches a UI variant: Slate Steel Dark with Dark, Slate Steel Light with Light. */
-    public static TerminalScheme matching(boolean dark) { return dark ? SLATE_STEEL_DARK : SLATE_STEEL_LIGHT; }
 }
diff --git a/jasper-ui/src/main/java/dev/jasper/ui/theme/ThemeFileException.java b/jasper-ui/src/main/java/dev/jasper/ui/theme/ThemeFileException.java
index e84a0d73723c5c7621abd543a4e2604d38716566..cc91e7bac32de923a00fd2ff1e7f38fe73ea0eb6 100644
--- a/jasper-ui/src/main/java/dev/jasper/ui/theme/ThemeFileException.java
+++ b/jasper-ui/src/main/java/dev/jasper/ui/theme/ThemeFileException.java
@@ -4,12 +4,14 @@ package dev.jasper.ui.theme;
 public final class ThemeFileException extends IllegalArgumentException {
     private final String origin;
     private final int line;
+    private final String detail;
 
     /** Creates a diagnostic with its source origin and optional 1-based line. */
     public ThemeFileException(String origin, int line, String message) {
         super(origin + (line > 0 ? ":" + line : "") + ": " + message);
         this.origin = origin;
         this.line = line;
+        this.detail = message;
     }
 
     /** The file name or resource the problem is in. */
@@ -17,4 +19,7 @@ public final class ThemeFileException extends IllegalArgumentException {
 
     /** The 1-based line, or 0 when unknown. */
     public int line() { return line; }
+
+    /** The problem without the origin and line, for callers that show those separately. */
+    public String detail() { return detail; }
 }
diff --git a/jasper-ui/src/main/java/dev/jasper/ui/theme/ThemeJson.java b/jasper-ui/src/main/java/dev/jasper/ui/theme/ThemeJson.java
index f280ffa039f0c71e242586d49b41eb30ad9ea4db..a951ec19db5ea568dbc794b463243bc9cfb89320 100644
--- a/jasper-ui/src/main/java/dev/jasper/ui/theme/ThemeJson.java
+++ b/jasper-ui/src/main/java/dev/jasper/ui/theme/ThemeJson.java
@@ -4,9 +4,9 @@ import java.util.LinkedHashMap;
 import java.util.Map;
 
 /**
- * A strict reader for Jasper's bundled theme file: JSON objects whose values are strings or objects, nothing else.
- * Numbers, arrays, booleans, null, duplicate keys and trailing content are errors naming the origin and line. The
- * theme package is JDK-only, so it cannot use FlatLaf's parser.
+ * A strict reader for Jasper theme files: JSON objects whose values are strings, objects or whole numbers (the format
+ * marker), nothing else. Fractions, signs, exponents, arrays, booleans, null, duplicate keys and trailing content are
+ * errors naming the origin and line. The theme package is JDK-only, so it cannot use FlatLaf's parser.
  */
 final class ThemeJson {
     private final String text, origin;
@@ -14,7 +14,7 @@ final class ThemeJson {
 
     private ThemeJson(String text, String origin) { this.text = text; this.origin = origin; }
 
-    /** The top-level object, as ordered maps of strings and maps. */
+    /** The top-level object, as ordered maps of strings, maps and {@code Long}s. */
     static Map<String, Object> parse(String text, String origin) {
         var reader = new ThemeJson(text, origin);
         reader.space();
@@ -35,8 +35,8 @@ final class ThemeJson {
             String key = string();
             space(); expect(':'); space();
             char first = peek();
-            Object value = first == '{' ? object() : first == '"' ? string() : null;
-            if (value == null) throw error("Expected a string or an object for \"" + key + "\"");
+            Object value = first == '{' ? object() : first == '"' ? string() : first >= '0' && first <= '9' ? number() : null;
+            if (value == null) throw error("Expected a string, an object or a whole number for \"" + key + "\"");
             if (map.putIfAbsent(key, value) != null) { at = keyAt; throw error("Duplicate key \"" + key + "\""); }
             space();
             char next = peek();
@@ -46,6 +46,17 @@ final class ThemeJson {
         }
     }
 
+    /** Digits without sign, fraction, exponent or leading zero, such as the format marker {@code 1}. */
+    private Long number() {
+        int start = at;
+        while (at < text.length() && text.charAt(at) >= '0' && text.charAt(at) <= '9') at++;
+        String digits = text.substring(start, at);
+        if (at < text.length() && ".eE".indexOf(text.charAt(at)) >= 0) throw error("Expected a whole number");
+        if (digits.length() > 1 && digits.charAt(0) == '0') { at = start; throw error("A number has no leading zeros"); }
+        if (digits.length() > 9) { at = start; throw error("A number of at most nine digits"); }
+        return Long.parseLong(digits);
+    }
+
     private String string() {
         expect('"');
         var out = new StringBuilder();
diff --git a/jasper-ui/src/main/java/dev/jasper/ui/theme/package-info.java b/jasper-ui/src/main/java/dev/jasper/ui/theme/package-info.java
index bd06a97b5b86562d9a34f4b18ae0a93f4df081bb..bd0b3fcb3517f15dee05a4e830c2df3b9ff0d980 100644
--- a/jasper-ui/src/main/java/dev/jasper/ui/theme/package-info.java
+++ b/jasper-ui/src/main/java/dev/jasper/ui/theme/package-info.java
@@ -1,6 +1,6 @@
 /**
- * The Slate Steel engine: the bundled theme file, its semantic palette, terminal and editor schemes, syntax styles,
- * FlatLaf's UI keys from the roles, glass levels and the generated variants.
+ * The theme engine: Jasper theme files (the bundled Slate Steel and installed themes), their semantic palettes,
+ * terminal and editor schemes, syntax styles, FlatLaf's UI keys from the roles, glass levels and the generated variants.
  * JDK-only, including AWT color values and EDT-confined appearance publication; no Swing or outgoing Jasper package dependencies.
  * Control geometry and delegates belong to the Swing kit.
  * Callers own resolved values; these types own no threads or native resources. Binding owners close their replaying subscriptions on EDT.
diff --git a/jasper-ui/src/main/resources/dev/jasper/ui/theme/slate-steel.json b/jasper-ui/src/main/resources/dev/jasper/ui/theme/slate-steel.json
index a14fee1407c55f5330ceb72bda69f522f5d0c577..8003fea60a8d3a6548885cc7783555af75542624 100644
--- a/jasper-ui/src/main/resources/dev/jasper/ui/theme/slate-steel.json
+++ b/jasper-ui/src/main/resources/dev/jasper/ui/theme/slate-steel.json
@@ -1,6 +1,7 @@
 {
+  "jasper-theme": 1, "name": "Slate Steel", "author": "Jasper",
   "ui": {
-    "slate-steel-dark": {
+    "dark": {
       "surface.app": "#1E2127", "surface.panel": "#272A31", "surface.panel.raised": "#2D3139",
       "surface.dialog": "#2D3139", "surface.card": "#272A31", "surface.sidebar": "#23262C",
       "surface.input": "#1F2228", "surface.field.raised": "#30343C", "surface.control": "#363B45",
@@ -20,7 +21,7 @@
       "status.success": "#4FD06C", "status.warning": "#F2C55C", "status.danger": "#F07F78", "status.idle": "#5B616C",
       "hue.blue": "#94B4D6", "hue.green": "#72DE8F", "hue.amber": "#F2C55C", "hue.purple": "#C8A4FF", "hue.neutral": "#D1D5DC"
     },
-    "slate-steel-light": {
+    "light": {
       "surface.app": "#E4E7EB", "surface.panel": "#F5F6F8", "surface.panel.raised": "#ECEEF1",
       "surface.dialog": "#F5F6F8", "surface.card": "#FFFFFF", "surface.sidebar": "#ECEEF1",
       "surface.input": "#FFFFFF", "surface.field.raised": "#FFFFFF", "surface.control": "#FFFFFF",
@@ -42,22 +43,22 @@
     }
   },
   "terminal": {
-    "slate-steel-dark": {
-      "bg": "#23262C", "fg": "#F4F6F8", "fg.bold": "#FFFFFF", "cursor": "#FFFFFF", "selection": "#3D5F8A",
+    "dark": {
+      "name": "Slate Steel Dark", "bg": "#23262C", "fg": "#F4F6F8", "fg.bold": "#FFFFFF", "cursor": "#FFFFFF", "selection": "#3D5F8A",
       "black": "#3A3F4A", "red": "#FF928A", "green": "#5FE88A", "yellow": "#FFD75F",
       "blue": "#6CB6FF", "magenta": "#E99CFF", "cyan": "#4FE0E8", "white": "#D9DEE5",
       "bright.black": "#99A2B1", "bright.red": "#FFC6C0", "bright.green": "#9CF5B6", "bright.yellow": "#FFE89A",
       "bright.blue": "#B2D9FF", "bright.magenta": "#F6C8FF", "bright.cyan": "#98F2F5", "bright.white": "#FFFFFF"
     },
-    "slate-steel-light": {
-      "bg": "#FBFBFC", "fg": "#1C2027", "fg.bold": "#000000", "cursor": "#1C2027", "selection": "#C7D8EE",
+    "light": {
+      "name": "Slate Steel Light", "bg": "#FBFBFC", "fg": "#1C2027", "fg.bold": "#000000", "cursor": "#1C2027", "selection": "#C7D8EE",
       "black": "#1C2027", "red": "#C8302C", "green": "#1F7A35", "yellow": "#8A6200",
       "blue": "#1F5FBF", "magenta": "#9B3FB5", "cyan": "#0F7A8A", "white": "#5D6470",
       "bright.black": "#6B7280", "bright.red": "#B8342F", "bright.green": "#23853C", "bright.yellow": "#946800",
       "bright.blue": "#2867C8", "bright.magenta": "#A847C2", "bright.cyan": "#127F91", "bright.white": "#000000"
     },
     "high-contrast": {
-      "bg": "#000000", "fg": "#FFFFFF", "fg.bold": "#FFFFFF", "cursor": "#FFFFFF", "selection": "#264F9E",
+      "name": "High Contrast", "bg": "#000000", "fg": "#FFFFFF", "fg.bold": "#FFFFFF", "cursor": "#FFFFFF", "selection": "#264F9E",
       "black": "#4D4D4D", "red": "#FF7B72", "green": "#3CFF7A", "yellow": "#FFE14D",
       "blue": "#7CB8FF", "magenta": "#FF8CFF", "cyan": "#4DF0FF", "white": "#E6E6E6",
       "bright.black": "#A6A6A6", "bright.red": "#FFA8A2", "bright.green": "#9DFFBC", "bright.yellow": "#FFF08A",
@@ -65,7 +66,7 @@
     }
   },
   "editor": {
-    "slate-steel-dark": {
+    "dark": {
       "background": "terminal.bg", "foreground": "terminal.fg", "caret": "terminal.cursor", "selection": "terminal.selection",
       "currentLine": "hover.overlay @ 5%", "gutter": "terminal.bg", "lineNumbers": "text.tertiary",
       "keyword": "terminal.ansi.magenta", "type": "terminal.ansi.cyan", "function": "terminal.ansi.blue",
@@ -75,7 +76,7 @@
       "operator": "text.secondary", "punctuation": "text.secondary", "tag": "terminal.ansi.red",
       "attribute": "terminal.ansi.yellow", "regex": "terminal.ansi.cyan", "error": "terminal.ansi.red"
     },
-    "slate-steel-light": {
+    "light": {
       "background": "terminal.bg", "foreground": "terminal.fg", "caret": "terminal.cursor", "selection": "terminal.selection",
       "currentLine": "hover.overlay @ 5%", "gutter": "terminal.bg", "lineNumbers": "text.tertiary",
       "keyword": "terminal.ansi.magenta", "type": "terminal.ansi.cyan", "function": "terminal.ansi.blue",
JASPER_PATCH
`````

- [ ] **Step 4: Run the tests**

```bash
./gradlew :jasper-ui:test
./gradlew :jasper-app:test --tests "*SlateSteel*" --tests "*ThemeController*" --tests "*TerminalScheme*" \
  --tests "*ThemeMigrationCoverageTest" --tests "*ColourLiteralScanTest" --tests "*WindowVariantTest" \
  --tests "*JasperApplicationThemesTest" --tests "*SystemThemeApplicationTest" --tests "*ConfigurationControllerTest" \
  --tests "*HostedPalettesTest" --tests "*Appearance*" --tests "*PackagedResourcesTest" --tests "*AppDocumentationTest"
```

Expected: both PASS (jasper-ui: 544 tests).

- [ ] **Step 5: Commit**

```bash
git add -A
git commit -m "One Jasper theme format: JasperTheme reads any theme file, Slate Steel included

🤖 Generated with Claude Code at The Home Depot

Co-Authored-By: Claude <noreply@anthropic.com>"
```

### Task 2: The theme library

**What changes:** `appearance.ThemeLibrary` holds Slate Steel plus every valid `<home>/themes/*.json` (regular files
directly in the folder), ordered by name then id, labels that show the id when names collide, and a `Problem` (file,
line, message) for each skipped file: a bad name, `slate-steel.json`, more than 1 MiB, not UTF-8 (a leading
byte-order mark is dropped), or a loader error. `read(folder, previous)` reuses the parsed theme of an unchanged file
and returns `previous` when nothing changed. `AppDirs.themes()` is `<root>/themes`. `ThemeTestSupport` gains two theme
file fixtures used from here on.

**Files:** create `jasper-app/src/main/java/dev/jasper/app/appearance/ThemeLibrary.java`,
`jasper-app/src/test/java/dev/jasper/app/appearance/ThemeLibraryTest.java`; modify `platform/AppDirs.java`,
`platform/AppDirsTest.java`, `appearance/ThemeTestSupport.java`, `migration.csv`.

**Interfaces:**
- Consumes: Task 1's `JasperTheme`, `SchemeId`, `TerminalScheme`, `ThemeFileException.line()/detail()`.
- Produces: `final class ThemeLibrary` (thread-safe, immutable): `record Problem(Path file, int line, String message)`,
  `static final int MAX_BYTES = 1 << 20`, `static ThemeLibrary bundledOnly()`,
  `static ThemeLibrary read(Path folder, ThemeLibrary previous)`, `static String readText(Path)` (package-private),
  `List<JasperTheme> themes()`, `List<JasperTheme> imported()`, `List<JasperTheme> themes(boolean dark)`,
  `Optional<JasperTheme> theme(String id)`, `List<TerminalScheme> schemes()`, `Optional<TerminalScheme> scheme(SchemeId)`,
  `List<Problem> problems()`, `String label(JasperTheme)`, `String label(TerminalScheme)`; `AppDirs.themes()`;
  `ThemeTestSupport.terminalThemeJson(String name)` and `uiThemeJson(String name)`.

- [ ] **Step 1: Apply the test changes**

`````bash
git apply --whitespace=nowarn <<'JASPER_PATCH'
diff --git a/jasper-app/src/test/java/dev/jasper/app/appearance/ThemeLibraryTest.java b/jasper-app/src/test/java/dev/jasper/app/appearance/ThemeLibraryTest.java
new file mode 100644
index 0000000000000000000000000000000000000000..cc96791acf26f3cae3dbb6db55f227b0bf3a8ed8
--- /dev/null
+++ b/jasper-app/src/test/java/dev/jasper/app/appearance/ThemeLibraryTest.java
@@ -0,0 +1,92 @@
+package dev.jasper.app.appearance;
+
+import static org.assertj.core.api.Assertions.*;
+
+import dev.jasper.ui.theme.JasperTheme;
+import dev.jasper.ui.theme.SchemeId;
+import dev.jasper.ui.theme.TerminalScheme;
+import java.nio.file.Files;
+import java.nio.file.Path;
+import org.junit.jupiter.api.Test;
+import org.junit.jupiter.api.io.TempDir;
+
+/** spec 3.2: the built-in theme plus every valid {@code <home>/themes/*.json}; bad files are skipped and reported. */
+class ThemeLibraryTest {
+    @TempDir Path home;
+
+    static String terminalTheme(String name) { return ThemeTestSupport.terminalThemeJson(name); }
+    static String uiTheme(String name) { return ThemeTestSupport.uiThemeJson(name); }
+
+    Path themes() throws Exception { return Files.createDirectories(home.resolve("themes")); }
+
+    @Test void aMissingFolderIsTheBuiltInThemeAlone() {
+        var library = ThemeLibrary.read(home.resolve("themes"), ThemeLibrary.bundledOnly());
+        assertThat(library).isSameAs(ThemeLibrary.bundledOnly());
+        assertThat(library.themes()).containsExactly(JasperTheme.bundled());
+        assertThat(library.imported()).isEmpty();
+        assertThat(library.problems()).isEmpty();
+        assertThat(library.schemes()).extracting(scheme -> scheme.id().toString())
+            .containsExactly("slate-steel/dark", "slate-steel/light", "slate-steel/high-contrast");
+    }
+
+    @Test void importedThemesFollowSlateSteelByNameThenId() throws Exception {
+        Files.writeString(themes().resolve("zenburn.json"), terminalTheme("Zenburn"));
+        Files.writeString(themes().resolve("ocean.json"), uiTheme("ocean"));
+        Files.writeString(themes().resolve("abyss.json"), uiTheme("Ocean"));
+        Files.writeString(themes().resolve("notes.txt"), "ignored");
+        Files.createDirectories(themes().resolve("folder.json"));
+        var library = ThemeLibrary.read(themes(), ThemeLibrary.bundledOnly());
+        assertThat(library.problems()).isEmpty();
+        assertThat(library.themes()).extracting(JasperTheme::id).containsExactly("slate-steel", "abyss", "ocean", "zenburn");
+        assertThat(library.themes(true)).extracting(JasperTheme::id).containsExactly("slate-steel", "abyss", "ocean");
+        assertThat(library.themes(false)).extracting(JasperTheme::id).containsExactly("slate-steel", "abyss", "ocean");
+        assertThat(library.theme("zenburn")).get().extracting(JasperTheme::name).isEqualTo("Zenburn");
+        assertThat(library.theme("nord")).isEmpty();
+        assertThat(library.schemes()).extracting(scheme -> scheme.id().toString()).containsExactly(
+            "slate-steel/dark", "slate-steel/light", "slate-steel/high-contrast",
+            "abyss/dark", "abyss/light", "abyss/high-contrast", "ocean/dark", "ocean/light", "ocean/high-contrast", "zenburn/dark");
+        assertThat(library.scheme(new SchemeId("zenburn", "dark"))).get().extracting(TerminalScheme::name).isEqualTo("Zenburn");
+        assertThat(library.label(library.theme("abyss").orElseThrow())).as("two themes are named Ocean").isEqualTo("Ocean (abyss)");
+        assertThat(library.label(library.theme("ocean").orElseThrow())).isEqualTo("ocean (ocean)");
+        assertThat(library.label(library.theme("zenburn").orElseThrow())).isEqualTo("Zenburn");
+        assertThat(library.label(library.scheme(new SchemeId("zenburn", "dark")).orElseThrow())).isEqualTo("Zenburn");
+        assertThat(library.label(library.scheme(new SchemeId("abyss", "high-contrast")).orElseThrow()))
+            .isEqualTo("High Contrast (abyss/high-contrast)");
+    }
+
+    @Test void aFileThatCannotBeUsedIsSkippedAndReported() throws Exception {
+        Files.writeString(themes().resolve("broken.json"), "{ \"jasper-theme\": 1,\n  \"name\": \"Broken\" ");
+        Files.writeString(themes().resolve("half.json"), terminalTheme("Half").replace("\"fg.bold\": \"#FBF1C7\",", ""));
+        Files.writeString(themes().resolve("My Theme.json"), terminalTheme("Mine"));
+        Files.writeString(themes().resolve("slate-steel.json"), terminalTheme("Impostor"));
+        Files.write(themes().resolve("huge.json"), new byte[ThemeLibrary.MAX_BYTES + 1]);
+        Files.write(themes().resolve("latin.json"), new byte[] {'{', (byte) 0xE9, '}'});
+        Files.writeString(themes().resolve("good.json"), terminalTheme("Good"));
+        Files.writeString(themes().resolve("marked.json"), "\uFEFF" + terminalTheme("Marked"));
+        var library = ThemeLibrary.read(themes(), ThemeLibrary.bundledOnly());
+        assertThat(library.imported()).as("a byte-order mark is not part of the text").extracting(JasperTheme::id).containsExactly("good", "marked");
+        assertThat(library.problems()).extracting(problem -> problem.file().getFileName().toString())
+            .containsExactly("My Theme.json", "broken.json", "half.json", "huge.json", "latin.json", "slate-steel.json");
+        assertThat(library.problems().get(0).message()).startsWith("A theme file is named after its id");
+        assertThat(library.problems().get(1).line()).isEqualTo(2);
+        assertThat(library.problems().get(1).message()).isEqualTo("Unexpected end");
+        assertThat(library.problems().get(2).message()).isEqualTo("terminal.dark: Missing colour \"fg.bold\"");
+        assertThat(library.problems().get(3).message()).isEqualTo("Theme files are at most 1 MiB.");
+        assertThat(library.problems().get(4).message()).isEqualTo("A theme file is UTF-8 text.");
+        assertThat(library.problems().get(5).message()).isEqualTo("Slate Steel is built in; this file is ignored.");
+    }
+
+    @Test void aRescanKeepsUnchangedThemesAndAnUnchangedFolderKeepsTheLibrary() throws Exception {
+        Files.writeString(themes().resolve("ocean.json"), uiTheme("Ocean"));
+        Files.writeString(themes().resolve("zenburn.json"), terminalTheme("Zenburn"));
+        var first = ThemeLibrary.read(themes(), ThemeLibrary.bundledOnly());
+        assertThat(ThemeLibrary.read(themes(), first)).isSameAs(first);
+        Files.writeString(themes().resolve("zenburn.json"), terminalTheme("Zenburn Night"));
+        var second = ThemeLibrary.read(themes(), first);
+        assertThat(second).isNotSameAs(first);
+        assertThat(second.theme("ocean").orElseThrow()).isSameAs(first.theme("ocean").orElseThrow());
+        assertThat(second.theme("zenburn").orElseThrow().name()).isEqualTo("Zenburn Night");
+        Files.delete(themes().resolve("ocean.json"));
+        assertThat(ThemeLibrary.read(themes(), second).themes()).extracting(JasperTheme::id).containsExactly("slate-steel", "zenburn");
+    }
+}
diff --git a/jasper-app/src/test/java/dev/jasper/app/appearance/ThemeTestSupport.java b/jasper-app/src/test/java/dev/jasper/app/appearance/ThemeTestSupport.java
index 0c555e5e02137d5bc96433e816dfe34ca7bc14f1..7a461d37016ad8f9c0fe65588adc71ee0f8f90b7 100644
--- a/jasper-app/src/test/java/dev/jasper/app/appearance/ThemeTestSupport.java
+++ b/jasper-app/src/test/java/dev/jasper/app/appearance/ThemeTestSupport.java
@@ -21,6 +21,24 @@ public final class ThemeTestSupport {
         var theme = generated(dark);
         return new dev.jasper.ui.theme.ThemePair(theme, theme);
     }
+    /** A terminal-only theme file named {@code name}, with one scheme keyed {@code dark}. */
+    public static String terminalThemeJson(String name) {
+        return """
+            { "jasper-theme": 1, "name": "%s",
+              "terminal": { "dark": { "name": "%s", "bg": "#282828", "fg": "#EBDBB2", "fg.bold": "#FBF1C7",
+                "cursor": "#D5C4A1", "selection": "#504945", "black": "#282828", "red": "#CC241D", "green": "#98971A",
+                "yellow": "#D79921", "blue": "#458588", "magenta": "#B16286", "cyan": "#689D6A", "white": "#A89984",
+                "bright.black": "#928374", "bright.red": "#FB4934", "bright.green": "#B8BB26", "bright.yellow": "#FABD2F",
+                "bright.blue": "#83A598", "bright.magenta": "#D3869B", "bright.cyan": "#8EC07C", "bright.white": "#EBDBB2" } } }
+            """.formatted(name, name);
+    }
+    /** Slate Steel's file renamed {@code name}: a UI theme with both variants and three schemes. */
+    public static String uiThemeJson(String name) {
+        try (var in = ThemeTestSupport.class.getResourceAsStream("/dev/jasper/ui/theme/slate-steel.json")) {
+            return new String(in.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8)
+                .replace("\"name\": \"Slate Steel\"", "\"name\": \"" + name + "\"");
+        } catch (java.io.IOException failure) { throw new java.io.UncheckedIOException(failure); }
+    }
     /** Installs a generated theme, as ThemeController's installer does. */
     public static boolean install(GeneratedTheme theme) { return ThemeManager.install(theme); }
 
diff --git a/jasper-app/src/test/java/dev/jasper/app/platform/AppDirsTest.java b/jasper-app/src/test/java/dev/jasper/app/platform/AppDirsTest.java
index 9c6d2fbb99940356fd60caaf3254798dd28a8d2c..b2ddd65cb3aa698adec0c0884de7e4f7eada72b5 100644
--- a/jasper-app/src/test/java/dev/jasper/app/platform/AppDirsTest.java
+++ b/jasper-app/src/test/java/dev/jasper/app/platform/AppDirsTest.java
@@ -77,6 +77,7 @@ class AppDirsTest {
         assertThat(byProperty.overridden()).isTrue();
         assertThat(byProperty.configFile()).isEqualTo(home.resolve("prop-home/config.toml"));
         assertThat(byProperty.plugins()).isEqualTo(home.resolve("prop-home/plugins"));
+        assertThat(byProperty.themes()).isEqualTo(home.resolve("prop-home/themes"));
         assertThat(byProperty.pluginState()).isEqualTo(home.resolve("prop-home/plugins.toml"));
         assertThat(byProperty.daemonSocket()).isEqualTo(home.resolve("prop-home/daemon/socket"));
         assertThat(byProperty.logs()).isEqualTo(home.resolve("prop-home/logs"));
JASPER_PATCH
`````

- [ ] **Step 2: See them fail**

Run: `./gradlew :jasper-app:compileTestJava`
Expected: FAIL, `cannot find symbol` for `ThemeLibrary` and `themes()`.

- [ ] **Step 3: Apply the production changes**

`````bash
git apply --whitespace=nowarn <<'JASPER_PATCH'
diff --git a/docs/design/theme-engine/migration.csv b/docs/design/theme-engine/migration.csv
index dc20a30383569eee67cda14612b910cc4f34f6a2..fc7ea83f5883cd0a8344570c914111df5e2a0367 100644
--- a/docs/design/theme-engine/migration.csv
+++ b/docs/design/theme-engine/migration.csv
@@ -1200,3 +1200,5 @@ jasper-app/src/test/java/dev/jasper/app/workspace/Task9cHistoryEvidenceTest.java
 jasper-app/src/main/java/dev/jasper/app/platform/HostClipboard.java,app.platform,HostClipboard,native-platform,,,jasper-app/src/test/java/dev/jasper/app/platform/HostClipboardTest.java,SDK 0.16.1 clipboard plan Task 2: the host AWT system clipboard behind Platform.copy and copySecret and clipboardText; native platform ownership; no presentation values
 jasper-app/src/test/java/dev/jasper/app/platform/HostClipboardTest.java,app.platform,HostClipboardTest,nonvisual-helper,,,jasper-app/src/test/java/dev/jasper/app/platform/HostClipboardTest.java,SDK 0.16.1 clipboard plan Task 2: Test/fixture ownership; headless host clipboard behavior over an injected system clipboard
 jasper-sdk-testkit/src/main/java/dev/jasper/sdk/testing/FakeClipboard.java,sdk-testkit,FakeClipboard,nonvisual-helper,,,jasper-sdk-testkit/src/test/java/dev/jasper/sdk/testing/FakeContractTest.java,SDK 0.16.1 clipboard plan Task 2: nonvisual helper or fixture false positive; the fake host clipboard keeps the application's secret-expiry behavior
+jasper-app/src/main/java/dev/jasper/app/appearance/ThemeLibrary.java,app.appearance,ThemeLibrary,nonvisual-helper,none,none,jasper-app/src/test/java/dev/jasper/app/appearance/ThemeLibraryTest.java,New in theme import Plan 1 Task 2; the installed themes.
+jasper-app/src/test/java/dev/jasper/app/appearance/ThemeLibraryTest.java,app.appearance,ThemeLibraryTest,nonvisual-helper,none,none,jasper-app/src/test/java/dev/jasper/app/appearance/ThemeLibraryTest.java,New in theme import Plan 1 Task 2; test owner of the installed themes.
diff --git a/jasper-app/src/main/java/dev/jasper/app/appearance/ThemeLibrary.java b/jasper-app/src/main/java/dev/jasper/app/appearance/ThemeLibrary.java
new file mode 100644
index 0000000000000000000000000000000000000000..f0c172eb64bd1cad61e33904d9cef145a8232737
--- /dev/null
+++ b/jasper-app/src/main/java/dev/jasper/app/appearance/ThemeLibrary.java
@@ -0,0 +1,156 @@
+package dev.jasper.app.appearance;
+
+import dev.jasper.ui.theme.JasperTheme;
+import dev.jasper.ui.theme.SchemeId;
+import dev.jasper.ui.theme.TerminalScheme;
+import dev.jasper.ui.theme.ThemeFileException;
+import java.io.IOException;
+import java.io.InputStream;
+import java.nio.charset.CharacterCodingException;
+import java.nio.charset.CodingErrorAction;
+import java.nio.charset.StandardCharsets;
+import java.nio.ByteBuffer;
+import java.nio.file.Files;
+import java.nio.file.Path;
+import java.util.ArrayList;
+import java.util.Comparator;
+import java.util.HashMap;
+import java.util.LinkedHashMap;
+import java.util.List;
+import java.util.Locale;
+import java.util.Map;
+import java.util.Objects;
+import java.util.Optional;
+
+/**
+ * The installed themes: the built-in Slate Steel and every valid {@code <home>/themes/<id>.json}. Imported themes are
+ * ordered by display name (ignoring case), then id. A file that cannot be used is skipped and kept as a problem.
+ * Immutable and thread-safe; {@link #read} builds a new library, reusing the parsed themes of unchanged files.
+ */
+public final class ThemeLibrary {
+    /** A file the library skipped: where it is, the 1-based line when known (else 0), and what is wrong. */
+    public record Problem(Path file, int line, String message) {
+        public Problem { Objects.requireNonNull(file, "file"); Objects.requireNonNull(message, "message"); }
+    }
+
+    /** Theme files are at most 1 MiB. */
+    public static final int MAX_BYTES = 1 << 20;
+    private static final ThemeLibrary BUNDLED = new ThemeLibrary(List.of(), Map.of(), List.of());
+
+    private final List<JasperTheme> imported, themes;
+    private final Map<String, String> texts;
+    private final List<Problem> problems;
+    private final List<TerminalScheme> schemes;
+    private final Map<String, String> themeLabels = new HashMap<>(), schemeLabels = new HashMap<>();
+
+    private ThemeLibrary(List<JasperTheme> imported, Map<String, String> texts, List<Problem> problems) {
+        this.imported = List.copyOf(imported);
+        this.texts = Map.copyOf(texts);
+        this.problems = List.copyOf(problems);
+        var all = new ArrayList<JasperTheme>();
+        all.add(JasperTheme.bundled());
+        all.addAll(this.imported);
+        themes = List.copyOf(all);
+        var allSchemes = new ArrayList<TerminalScheme>();
+        for (var theme : themes) allSchemes.addAll(theme.schemes());
+        schemes = List.copyOf(allSchemes);
+        var names = new HashMap<String, Integer>();
+        for (var theme : themes) names.merge(theme.name().toLowerCase(Locale.ROOT), 1, Integer::sum);
+        for (var theme : themes)
+            themeLabels.put(theme.id(), names.get(theme.name().toLowerCase(Locale.ROOT)) > 1 ? theme.name() + " (" + theme.id() + ")" : theme.name());
+        var schemeNames = new HashMap<String, Integer>();
+        for (var scheme : schemes) schemeNames.merge(scheme.name().toLowerCase(Locale.ROOT), 1, Integer::sum);
+        for (var scheme : schemes)
+            schemeLabels.put(scheme.id().toString(),
+                schemeNames.get(scheme.name().toLowerCase(Locale.ROOT)) > 1 ? scheme.name() + " (" + scheme.id() + ")" : scheme.name());
+    }
+
+    /** Only the built-in theme. */
+    public static ThemeLibrary bundledOnly() { return BUNDLED; }
+
+    /**
+     * The themes in {@code folder}: every regular {@code *.json} file directly in it (a missing folder has none).
+     * {@code previous}'s parsed theme is reused for a file whose text is unchanged, and {@code previous} itself is
+     * returned when nothing changed, so an unchanged folder never reinstalls a theme. Never throws for a bad file.
+     */
+    public static ThemeLibrary read(Path folder, ThemeLibrary previous) {
+        Objects.requireNonNull(folder, "folder");
+        Objects.requireNonNull(previous, "previous");
+        var files = new ArrayList<Path>();
+        var problems = new ArrayList<Problem>();
+        if (Files.isDirectory(folder)) {
+            try (var listing = Files.list(folder)) {
+                listing.filter(path -> path.getFileName().toString().endsWith(".json") && Files.isRegularFile(path))
+                    .sorted(Comparator.comparing(path -> path.getFileName().toString())).forEach(files::add);
+            } catch (IOException failure) {
+                problems.add(new Problem(folder, 0, "Could not list the themes folder: " + failure.getMessage()));
+            }
+        }
+        var imported = new ArrayList<JasperTheme>();
+        var texts = new LinkedHashMap<String, String>();
+        for (Path file : files) {
+            String fileName = file.getFileName().toString();
+            String id = fileName.substring(0, fileName.length() - ".json".length());
+            if (id.equals(JasperTheme.BUNDLED_ID)) {
+                problems.add(new Problem(file, 0, "Slate Steel is built in; this file is ignored."));
+                continue;
+            }
+            if (!JasperTheme.validId(id)) {
+                problems.add(new Problem(file, 0, "A theme file is named after its id: lowercase letters, digits and dashes, at most 64 characters."));
+                continue;
+            }
+            String text;
+            try { text = readText(file); }
+            catch (IOException failure) { problems.add(new Problem(file, 0, failure.getMessage())); continue; }
+            var reused = previous.theme(id).filter(theme -> text.equals(previous.texts.get(id)));
+            try {
+                imported.add(reused.orElseGet(() -> JasperTheme.parse(text, id)));
+                texts.put(id, text);
+            } catch (ThemeFileException failure) {
+                problems.add(new Problem(file, failure.line(), failure.detail()));
+            }
+        }
+        imported.sort(Comparator.comparing((JasperTheme theme) -> theme.name().toLowerCase(Locale.ROOT)).thenComparing(JasperTheme::id));
+        if (sameThemes(imported, previous.imported) && problems.equals(previous.problems)) return previous;
+        return new ThemeLibrary(imported, texts, problems);
+    }
+
+    private static boolean sameThemes(List<JasperTheme> next, List<JasperTheme> previous) {
+        if (next.size() != previous.size()) return false;
+        for (int i = 0; i < next.size(); i++) if (next.get(i) != previous.get(i)) return false;
+        return true;
+    }
+
+    /** The file's text, read as UTF-8 without a leading byte-order mark; larger than {@link #MAX_BYTES} or not UTF-8 is an error. */
+    static String readText(Path file) throws IOException {
+        byte[] bytes;
+        try (InputStream in = Files.newInputStream(file)) { bytes = in.readNBytes(MAX_BYTES + 1); }
+        if (bytes.length > MAX_BYTES) throw new IOException("Theme files are at most 1 MiB.");
+        try {
+            String text = StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
+                .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString();
+            return text.startsWith("\uFEFF") ? text.substring(1) : text;
+        } catch (CharacterCodingException failure) {
+            throw new IOException("A theme file is UTF-8 text.");
+        }
+    }
+
+    /** Slate Steel, then the imported themes in order. */
+    public List<JasperTheme> themes() { return themes; }
+    /** The imported themes, in order. */
+    public List<JasperTheme> imported() { return imported; }
+    /** The themes that have the Dark or the Light variant, Slate Steel first. */
+    public List<JasperTheme> themes(boolean dark) { return themes.stream().filter(theme -> theme.has(dark)).toList(); }
+    /** The theme with this id. */
+    public Optional<JasperTheme> theme(String id) { return themes.stream().filter(theme -> theme.id().equals(id)).findFirst(); }
+    /** Every theme's terminal schemes: Slate Steel's, then each imported theme's, in the files' order. */
+    public List<TerminalScheme> schemes() { return schemes; }
+    /** The scheme with this id. */
+    public Optional<TerminalScheme> scheme(SchemeId id) { return schemes.stream().filter(scheme -> scheme.id().equals(id)).findFirst(); }
+    /** The files that were skipped, in file-name order. */
+    public List<Problem> problems() { return problems; }
+    /** A theme's name, followed by its id in parentheses when another theme has the same name. */
+    public String label(JasperTheme theme) { return themeLabels.getOrDefault(theme.id(), theme.name()); }
+    /** A scheme's name, followed by its id in parentheses when another scheme has the same name. */
+    public String label(TerminalScheme scheme) { return schemeLabels.getOrDefault(scheme.id().toString(), scheme.name()); }
+}
diff --git a/jasper-app/src/main/java/dev/jasper/app/platform/AppDirs.java b/jasper-app/src/main/java/dev/jasper/app/platform/AppDirs.java
index c009a9e3b0d12cca51ea32858339655a6f20c6b7..87da28de47edd9c1cb62935d0e19ffd319a7444f 100644
--- a/jasper-app/src/main/java/dev/jasper/app/platform/AppDirs.java
+++ b/jasper-app/src/main/java/dev/jasper/app/platform/AppDirs.java
@@ -36,6 +36,11 @@ public record AppDirs(Path root, Path configFile, Path logs, boolean overridden)
         return root.resolve("plugins");
     }
 
+    /** Imported themes: one Jasper theme file per theme, named after its id. */
+    public Path themes() {
+        return root.resolve("themes");
+    }
+
     /** Enabled flags, consented capabilities and pending removals. */
     public Path pluginState() {
         return root.resolve("plugins.toml");
JASPER_PATCH
`````

- [ ] **Step 4: Run the tests**

Run: `./gradlew :jasper-app:test --tests "*ThemeLibraryTest" --tests "*AppDirsTest" --tests "*ThemeMigrationCoverageTest"`
Expected: PASS (ThemeLibraryTest: 4 tests).

- [ ] **Step 5: Commit**

```bash
git add -A
git commit -m "Read installed themes from the themes folder

🤖 Generated with Claude Code at The Home Depot

Co-Authored-By: Claude <noreply@anthropic.com>"
```

### Task 3: Settings values and the terminal scheme dropdown

**What changes:**
- `TerminalSchemeChoice` becomes a record: `MATCH` or one `SchemeId`; `terminal.scheme` is validated text
  (`match` or a scheme id); new `ui.theme.dark` and `ui.theme.light` settings (theme ids, default `slate-steel`), on
  the Appearance page as hidden rows (Task 5 gives them their dropdowns); `ConfigSnapshot` gains `darkTheme()` and
  `lightTheme()`.
- The Terminal scheme row becomes `SchemePick` over `InstalledThemes.schemes()` from a new `AppearanceChoices.themes`
  supplier (Slate Steel only until Task 4); a saved scheme that is not installed shows "(not installed)".
- `WindowCommands` names its scheme commands `view.terminal_scheme.match` and `view.terminal_scheme.<theme>.<key>`
  (`schemeCommandId`), over Slate Steel's schemes until Task 6; `WindowContent` saves the choice's `key()`.
- `config.example.toml` and `docs/configuration.md` document the new keys and values (the template and guide tests
  require it).

**Files:** see the patches; production: `config/{TerminalSchemeChoice,CoreSettings,ConfigSnapshot}.java`,
`settingsui/{InstalledThemes,SchemePick,Editors,AppearanceChoices,CorePages}.java`, `application/SettingsAppearance.java`,
`appearance/ThemeController.java`, `workspace/{WindowContent,WindowCommands}.java`, `config.example.toml`,
`docs/configuration.md`, `migration.csv`; tests: config, settingsui and workspace tests that used the enum or the old
values.

**Interfaces:**
- Consumes: Task 1's `SchemeId`, `JasperTheme.validId`, `BUNDLED_ID`; Task 2's `ThemeLibrary`.
- Produces: `record TerminalSchemeChoice(Optional<SchemeId> scheme)`: `MATCH`, `static of(SchemeId)`,
  `static Optional<TerminalSchemeChoice> parse(String)`, `String key()`; `Setting<String>` constants
  `CoreSettings.UI_THEME_DARK`, `UI_THEME_LIGHT`, `TERMINAL_SCHEME`; `ConfigSnapshot.darkTheme()`, `lightTheme()`,
  `Builder.darkTheme(String)`, `lightTheme(String)`; `record InstalledThemes(List<ThemeOption> dark, List<ThemeOption> light, List<SchemeOption> schemes)`
  with `record ThemeOption(String id, String label, Option.Palette preview)`, `record SchemeOption(String key, String label)`,
  `static InstalledThemes slateSteel(Option.Palette dark, Option.Palette light)`; `AppearanceChoices` gains a last
  component `Supplier<InstalledThemes> themes`; `Editors.schemeChoice(AppearanceChoices)`;
  `SettingsAppearance.themeChoices(ThemeLibrary)`, `preview(JasperTheme, boolean)`;
  `WindowCommands.schemeCommandId(TerminalSchemeChoice)` (package-private); `ThemeTestSupport.SCHEME_CHOICES`.

- [ ] **Step 1: Apply the test changes**

`````bash
git apply --whitespace=nowarn <<'JASPER_PATCH'
diff --git a/jasper-app/src/test/java/dev/jasper/app/appearance/SlateSteelControllerTest.java b/jasper-app/src/test/java/dev/jasper/app/appearance/SlateSteelControllerTest.java
index cdb4407d165fe135782ad50775a1bddd06e80364..e821376c9e904901968a0b60be8cf3b2941fee15 100644
--- a/jasper-app/src/test/java/dev/jasper/app/appearance/SlateSteelControllerTest.java
+++ b/jasper-app/src/test/java/dev/jasper/app/appearance/SlateSteelControllerTest.java
@@ -26,7 +26,7 @@ class SlateSteelControllerTest {
     }
 
     @Test void oneVariantServesChromeAndWorkspaceAndTheSchemeColoursTheTerminalOnly() {
-        var controller = new ThemeController(false, TerminalSchemeChoice.HIGH_CONTRAST, theme -> true);
+        var controller = new ThemeController(false, TerminalSchemeChoice.parse("slate-steel/high-contrast").orElseThrow(), theme -> true);
         var current = controller.current();
         assertThat(current.chrome()).isSameAs(STEEL.generated(false)).isSameAs(current.workspace());
         var scheme = STEEL.scheme("high-contrast").orElseThrow();
@@ -34,7 +34,7 @@ class SlateSteelControllerTest {
         assertThat(current.palette().boldForeground()).isEqualTo(scheme.boldForeground());
         assertThat(current.surface()).as("around panes and under tabs: the UI variant")
             .isEqualTo(STEEL.palette(false).color(SemanticColor.SURFACE_TERMINAL));
-        assertThat(controller.schemeChoice()).isEqualTo(TerminalSchemeChoice.HIGH_CONTRAST);
+        assertThat(controller.schemeChoice()).isEqualTo(TerminalSchemeChoice.parse("slate-steel/high-contrast").orElseThrow());
     }
 
     @Test void matchFollowsTheVariantAndASchemeChangeKeepsTheChrome() {
@@ -44,14 +44,14 @@ class SlateSteelControllerTest {
         var chromeChanges = new ArrayList<Boolean>();
         controller.subscribe((theme, chrome) -> chromeChanges.add(chrome));
         chromeChanges.clear();
-        controller.configure(true, TerminalSchemeChoice.HIGH_CONTRAST);
+        controller.configure(true, TerminalSchemeChoice.parse("slate-steel/high-contrast").orElseThrow());
         assertThat(chromeChanges).containsExactly(false);
         assertThat(installs).hasSize(1);
         controller.configure(false, TerminalSchemeChoice.MATCH);
         assertThat(chromeChanges).containsExactly(false, true);
         assertThat(installs).hasSize(2).last().isSameAs(STEEL.generated(false));
         assertThat(controller.current().palette().background()).isEqualTo(STEEL.matching(false).colors().background());
-        controller.configureScheme(TerminalSchemeChoice.SLATE_STEEL_DARK);
+        controller.configureScheme(TerminalSchemeChoice.parse("slate-steel/dark").orElseThrow());
         assertThat(controller.current().chrome()).as("the UI stays Light").isSameAs(STEEL.generated(false));
     }
 
@@ -64,7 +64,7 @@ class SlateSteelControllerTest {
     }
 
     @Test void everySchemeChoiceNamesABundledScheme() {
-        for (var choice : TerminalSchemeChoice.values())
+        for (var choice : dev.jasper.app.appearance.ThemeTestSupport.SCHEME_CHOICES)
             for (boolean dark : new boolean[] {true, false})
                 assertThat(ThemeController.scheme(dark, choice)).isNotNull();
         assertThat(ThemeController.scheme(false, TerminalSchemeChoice.MATCH)).isEqualTo(JasperTheme.bundled().matching(false));
diff --git a/jasper-app/src/test/java/dev/jasper/app/appearance/ThemeTestSupport.java b/jasper-app/src/test/java/dev/jasper/app/appearance/ThemeTestSupport.java
index 7a461d37016ad8f9c0fe65588adc71ee0f8f90b7..9dea10beb297c360d250d673603a56e79c0227f2 100644
--- a/jasper-app/src/test/java/dev/jasper/app/appearance/ThemeTestSupport.java
+++ b/jasper-app/src/test/java/dev/jasper/app/appearance/ThemeTestSupport.java
@@ -16,6 +16,10 @@ import java.util.function.Predicate;
 
 /** Package-local test access, excluded from production artifacts. */
 public final class ThemeTestSupport {
+    /** "Same as UI theme" and Slate Steel's three schemes, in the dropdown's order. */
+    public static final java.util.List<TerminalSchemeChoice> SCHEME_CHOICES = java.util.List.of(TerminalSchemeChoice.MATCH,
+        TerminalSchemeChoice.parse("slate-steel/dark").orElseThrow(), TerminalSchemeChoice.parse("slate-steel/light").orElseThrow(),
+        TerminalSchemeChoice.parse("slate-steel/high-contrast").orElseThrow());
     /** Slate Steel's variant on both routes. */
     public static dev.jasper.ui.theme.ThemePair pair(boolean dark) {
         var theme = generated(dark);
diff --git a/jasper-app/src/test/java/dev/jasper/app/application/JasperApplicationThemesTest.java b/jasper-app/src/test/java/dev/jasper/app/application/JasperApplicationThemesTest.java
index 2b0acea97caee02d345e7e2e0f413b1df3e6725b..d2309b1b6ec2177cc55d217be5ca6fbd60529aa6 100644
--- a/jasper-app/src/test/java/dev/jasper/app/application/JasperApplicationThemesTest.java
+++ b/jasper-app/src/test/java/dev/jasper/app/application/JasperApplicationThemesTest.java
@@ -48,7 +48,7 @@ class JasperApplicationThemesTest {
 
     /** From a fresh look and feel (no Jasper snapshot), start-up installs its variant before any chrome reads the snapshot. */
     @Test void aFreshProcessInstallsTheConfiguredVariantBeforeAnyChromeReadsTheSnapshot() throws Exception {
-        start("[ui]\nappearance = 'light'\n[terminal]\nscheme = 'high-contrast'\n", false, dev.jasper.ui.theme.JasperTheme.bundled().scheme("high-contrast").orElseThrow());
+        start("[ui]\nappearance = 'light'\n[terminal]\nscheme = 'slate-steel/high-contrast'\n", false, dev.jasper.ui.theme.JasperTheme.bundled().scheme("high-contrast").orElseThrow());
     }
 
     /** The default follows the operating system; a headless run has none, so it starts Dark. */
diff --git a/jasper-app/src/test/java/dev/jasper/app/application/SystemThemeApplicationTest.java b/jasper-app/src/test/java/dev/jasper/app/application/SystemThemeApplicationTest.java
index 8424c6a56f3a08d6ca11c71601639541ecf41983..dc0003e4f5e1214c1929177f922e2f5da90e1072 100644
--- a/jasper-app/src/test/java/dev/jasper/app/application/SystemThemeApplicationTest.java
+++ b/jasper-app/src/test/java/dev/jasper/app/application/SystemThemeApplicationTest.java
@@ -46,13 +46,13 @@ class SystemThemeApplicationTest {
     }
 
     @Test void anExplicitAppearanceIgnoresTheOperatingSystemAndTheSchemeApplies() throws Exception {
-        Path file = home.resolve("config.toml"); Files.writeString(file, "[ui]\nappearance = 'dark'\n[terminal]\nscheme = 'high-contrast'\n");
+        Path file = home.resolve("config.toml"); Files.writeString(file, "[ui]\nappearance = 'dark'\n[terminal]\nscheme = 'slate-steel/high-contrast'\n");
         var service = java.util.concurrent.CompletableFuture.supplyAsync(() -> new ConfigService(file, true)).get();
         var themes = new ThemeController(false, TerminalSchemeChoice.MATCH);
         var controller = new ConfigurationController(themes, service, path -> {}, Observation.light());
         try {
             assertThat(themes.current().chrome()).isSameAs(STEEL.generated(true));
-            assertThat(themes.schemeChoice()).isEqualTo(TerminalSchemeChoice.HIGH_CONTRAST);
+            assertThat(themes.schemeChoice()).isEqualTo(TerminalSchemeChoice.parse("slate-steel/high-contrast").orElseThrow());
             controller.systemAppearanceChanged(Observation.dark());
             controller.systemAppearanceChanged(Observation.light());
             assertThat(themes.current().chrome()).isSameAs(STEEL.generated(true));
diff --git a/jasper-app/src/test/java/dev/jasper/app/config/ConfigLoaderTest.java b/jasper-app/src/test/java/dev/jasper/app/config/ConfigLoaderTest.java
index 7adf1634cb8f9b4f68baeb87c53b0f37cd41405d..33054072110255c9e4bd01ad4ead46b635e1aec0 100644
--- a/jasper-app/src/test/java/dev/jasper/app/config/ConfigLoaderTest.java
+++ b/jasper-app/src/test/java/dev/jasper/app/config/ConfigLoaderTest.java
@@ -424,7 +424,8 @@ class ConfigLoaderTest {
         var result = parse("[ui.theme]\nname = \"Nord\"\nfollow_system = true\nworkspace = \"Nord\"\n[terminal]\ncolors = \"light\"\n");
         assertThat(result.rejected()).isFalse();
         assertThat(result.snapshot()).isEqualTo(ConfigSnapshot.defaults());
-        assertThat(result.diagnostics()).extracting(ConfigDiagnostic::key).containsExactlyInAnyOrder("ui.theme", "terminal.colors");
+        assertThat(result.diagnostics()).extracting(ConfigDiagnostic::key)
+            .containsExactlyInAnyOrder("ui.theme.name", "ui.theme.follow_system", "ui.theme.workspace", "terminal.colors");
         assertThat(result.diagnostics()).allSatisfy(d -> {
             assertThat(d.severity()).isEqualTo(ConfigDiagnostic.Severity.WARNING);
             assertThat(d.message()).isEqualTo("Unknown setting or table; ignored.");
@@ -432,17 +433,35 @@ class ConfigLoaderTest {
     }
 
     @Test void appearanceAndTerminalSchemeReadTheirChoicesAndDefault() {
-        var chosen = parse("[ui]\nappearance = \"light\"\n[terminal]\nscheme = \"high-contrast\"\n");
+        var chosen = parse("[ui]\nappearance = \"light\"\n[terminal]\nscheme = \"slate-steel/high-contrast\"\n");
         assertThat(chosen.diagnostics()).isEmpty();
         assertThat(chosen.snapshot().appearance()).isEqualTo(AppearanceMode.LIGHT);
-        assertThat(chosen.snapshot().terminalScheme()).isEqualTo(TerminalSchemeChoice.HIGH_CONTRAST);
+        assertThat(chosen.snapshot().terminalScheme()).isEqualTo(TerminalSchemeChoice.parse("slate-steel/high-contrast").orElseThrow());
         var defaults = parse("");
         assertThat(defaults.snapshot().appearance()).isEqualTo(AppearanceMode.SYSTEM);
         assertThat(defaults.snapshot().terminalScheme()).isEqualTo(TerminalSchemeChoice.MATCH);
-        for (var scheme : TerminalSchemeChoice.values())
+        for (var scheme : dev.jasper.app.appearance.ThemeTestSupport.SCHEME_CHOICES)
             assertThat(parse("[terminal]\nscheme = \"" + scheme.key() + "\"\n").snapshot().terminalScheme()).isEqualTo(scheme);
     }
 
+    @Test void theThemePicksReadAnyThemeIdAndRejectOthers() {
+        var chosen = parse("[ui.theme]\ndark = \"ocean\"\nlight = \"ocean-dawn\"\n");
+        assertThat(chosen.diagnostics()).isEmpty();
+        assertThat(chosen.snapshot().darkTheme()).isEqualTo("ocean");
+        assertThat(chosen.snapshot().lightTheme()).isEqualTo("ocean-dawn");
+        var bad = parse("[ui.theme]\ndark = \"Ocean Theme\"\n");
+        assertThat(bad.snapshot().darkTheme()).isEqualTo("slate-steel");
+        assertDiagnostic(bad, "ui.theme.dark", 2, 1, ConfigDiagnostic.Severity.ERROR);
+        assertThat(bad.diagnostics().getFirst().message()).isEqualTo("Use a theme id such as \"slate-steel\"; using the default.");
+    }
+
+    @Test void anOldSchemeValueIsUnknownNow() {
+        var result = parse("[terminal]\nscheme = \"high-contrast\"\n");
+        assertThat(result.snapshot().terminalScheme()).isEqualTo(TerminalSchemeChoice.MATCH);
+        assertThat(result.diagnostics().getFirst().message())
+            .isEqualTo("Use \"match\" or a scheme id such as \"slate-steel/high-contrast\"; using the default.");
+    }
+
     @Test void anUnknownAppearanceOrSchemeUsesTheDefaultAndSaysSo() {
         var result = parse("[ui]\nappearance = \"sepia\"\n[terminal]\nscheme = \"nord\"\n");
         assertThat(result.snapshot().appearance()).isEqualTo(AppearanceMode.SYSTEM);
diff --git a/jasper-app/src/test/java/dev/jasper/app/config/ConfigSnapshotBuilderTest.java b/jasper-app/src/test/java/dev/jasper/app/config/ConfigSnapshotBuilderTest.java
index 44b72de4f1fa16ea8579fc60b0807b63bb8bd066..f458342cbaadfeaf80b0adba89ff843e9608a02d 100644
--- a/jasper-app/src/test/java/dev/jasper/app/config/ConfigSnapshotBuilderTest.java
+++ b/jasper-app/src/test/java/dev/jasper/app/config/ConfigSnapshotBuilderTest.java
@@ -20,9 +20,9 @@ class ConfigSnapshotBuilderTest {
     @Test void appearanceAndSchemeRoundTripThroughTheBuilderAndDefaultToSystemAndMatch() {
         assertThat(ConfigSnapshot.defaults().appearance()).isEqualTo(AppearanceMode.SYSTEM);
         assertThat(ConfigSnapshot.defaults().terminalScheme()).isEqualTo(TerminalSchemeChoice.MATCH);
-        var chosen = ConfigSnapshot.builder().appearance(AppearanceMode.LIGHT).terminalScheme(TerminalSchemeChoice.HIGH_CONTRAST).build();
+        var chosen = ConfigSnapshot.builder().appearance(AppearanceMode.LIGHT).terminalScheme(TerminalSchemeChoice.parse("slate-steel/high-contrast").orElseThrow()).build();
         var copied = chosen.toBuilder().tabHeight(40).build();
         assertThat(copied.appearance()).isEqualTo(AppearanceMode.LIGHT);
-        assertThat(copied.terminalScheme()).isEqualTo(TerminalSchemeChoice.HIGH_CONTRAST);
+        assertThat(copied.terminalScheme()).isEqualTo(TerminalSchemeChoice.parse("slate-steel/high-contrast").orElseThrow());
     }
 }
diff --git a/jasper-app/src/test/java/dev/jasper/app/config/CoreSettingsTest.java b/jasper-app/src/test/java/dev/jasper/app/config/CoreSettingsTest.java
index cea4785f1de32029c687044a1f4b965988148296..d468d7a6f75987fd03c6f09faeacf0b69b2756eb 100644
--- a/jasper-app/src/test/java/dev/jasper/app/config/CoreSettingsTest.java
+++ b/jasper-app/src/test/java/dev/jasper/app/config/CoreSettingsTest.java
@@ -28,7 +28,7 @@ class CoreSettingsTest {
     }
 
     @Test void everySettingIsDescribedAndItsTemplateValueRoundTrips() {
-        assertThat(CoreSettings.SCHEMA.settings()).hasSize(41);
+        assertThat(CoreSettings.SCHEMA.settings()).hasSize(43);
         for (Setting<?> setting : CoreSettings.SCHEMA.settings()) {
             assertThat(setting.label()).as(setting.name()).isNotBlank();
             assertThat(setting.description()).as(setting.name()).isNotBlank();
@@ -66,14 +66,17 @@ class CoreSettingsTest {
             .put(CoreSettings.TERMINAL_FONT_SIZE, 20f, CoreSettings.TERMINAL_FONT_SIZE.key())
             .put(CoreSettings.SYSTEM_RUN_IN_BACKGROUND, true, CoreSettings.SYSTEM_RUN_IN_BACKGROUND.key())
             .put(CoreSettings.UI_APPEARANCE, AppearanceMode.LIGHT, CoreSettings.UI_APPEARANCE.key())
-            .put(CoreSettings.TERMINAL_SCHEME, TerminalSchemeChoice.HIGH_CONTRAST, CoreSettings.TERMINAL_SCHEME.key())
+            .put(CoreSettings.TERMINAL_SCHEME, "slate-steel/high-contrast", CoreSettings.TERMINAL_SCHEME.key())
+            .put(CoreSettings.UI_THEME_DARK, "ocean", CoreSettings.UI_THEME_DARK.key())
             .put(CoreSettings.TERMINAL_ENV, Map.of("A", "1"), CoreSettings.TERMINAL_ENV.key())
             .put(CoreSettings.WINDOW_STATUS_BAR_HEIGHT, 30, CoreSettings.WINDOW_STATUS_BAR_HEIGHT.key()).build();
         var snapshot = ConfigSnapshot.from(values, Map.of("copy", "none"), Map.of());
         assertThat(snapshot.fontSize()).isEqualTo(20f);
         assertThat(snapshot.backgroundEnabled()).isTrue();
         assertThat(snapshot.appearance()).isEqualTo(AppearanceMode.LIGHT);
-        assertThat(snapshot.terminalScheme()).isEqualTo(TerminalSchemeChoice.HIGH_CONTRAST);
+        assertThat(snapshot.terminalScheme()).isEqualTo(TerminalSchemeChoice.parse("slate-steel/high-contrast").orElseThrow());
+        assertThat(snapshot.darkTheme()).isEqualTo("ocean");
+        assertThat(snapshot.lightTheme()).isEqualTo("slate-steel");
         assertThat(snapshot.terminal().env()).containsExactly(Map.entry("A", "1"));
         assertThat(snapshot.statusBarHeight()).isEqualTo(30);
         assertThat(snapshot.keybindings()).containsExactly(Map.entry("copy", "none"));
diff --git a/jasper-app/src/test/java/dev/jasper/app/settingsui/AppearanceFixtures.java b/jasper-app/src/test/java/dev/jasper/app/settingsui/AppearanceFixtures.java
index 1a028adb8843d1ff31df30e7f4ced5cc8427c0e8..9c703bb51503eb10a7ef6188121625bce0aea7a3 100644
--- a/jasper-app/src/test/java/dev/jasper/app/settingsui/AppearanceFixtures.java
+++ b/jasper-app/src/test/java/dev/jasper/app/settingsui/AppearanceFixtures.java
@@ -14,7 +14,8 @@ public final class AppearanceFixtures {
     public static final AppearanceChoices CHOICES = choices(Optional::empty);
 
     public static AppearanceChoices choices(Supplier<Optional<Boolean>> systemDark) {
-        return new AppearanceChoices(preview(true), preview(false), 100f, false, systemDark, ignored -> new Subscription(() -> { }));
+        var themes = InstalledThemes.slateSteel(preview(true), preview(false));
+        return new AppearanceChoices(preview(true), preview(false), 100f, false, systemDark, ignored -> new Subscription(() -> { }), () -> themes);
     }
 
     /** A Slate Steel variant's preview, as the application builds it. */
diff --git a/jasper-app/src/test/java/dev/jasper/app/settingsui/AppearancePageTest.java b/jasper-app/src/test/java/dev/jasper/app/settingsui/AppearancePageTest.java
index d7fca09d55c9f588ef9493c6213027a19fb8b57e..6eb1c2ed2a86eabbd995ecade9d53b64dab46aae 100644
--- a/jasper-app/src/test/java/dev/jasper/app/settingsui/AppearancePageTest.java
+++ b/jasper-app/src/test/java/dev/jasper/app/settingsui/AppearancePageTest.java
@@ -107,7 +107,7 @@ class AppearancePageTest {
         assertThat(items(box)).containsExactly("Same as UI theme", "Slate Steel Dark", "Slate Steel Light", "High Contrast");
         assertThat(selected(box)).isEqualTo("Same as UI theme");
         pick(box, "High Contrast");
-        assertThat(draft.value(CoreSettings.TERMINAL_SCHEME)).isEqualTo(dev.jasper.app.config.TerminalSchemeChoice.HIGH_CONTRAST);
+        assertThat(draft.value(CoreSettings.TERMINAL_SCHEME)).isEqualTo("slate-steel/high-contrast");
         assertThat(page.searchTerms()).contains("Terminal scheme", "Same as UI theme", "Slate Steel", "Match System").doesNotContain("Import theme…");
     }
 }
diff --git a/jasper-app/src/test/java/dev/jasper/app/settingsui/AppearanceSelectionTest.java b/jasper-app/src/test/java/dev/jasper/app/settingsui/AppearanceSelectionTest.java
index 278f0836d7b9c236f18e7d5c28e078caacff29dd..a11decb0c3bd0554b640535fd03cbc7cfa36afd2 100644
--- a/jasper-app/src/test/java/dev/jasper/app/settingsui/AppearanceSelectionTest.java
+++ b/jasper-app/src/test/java/dev/jasper/app/settingsui/AppearanceSelectionTest.java
@@ -18,7 +18,8 @@ class AppearanceSelectionTest {
     }
 
     @Test void theTilesPreviewBothVariantsAndTheThemeRowOffersOnlySlateSteel() {
-        var editor = new AppearanceSelection(new AppearanceChoices(DARK, LIGHT, 100f, false, () -> Optional.of(false), ignored -> new dev.jasper.app.lifecycle.Subscription(() -> {})));
+        var editor = new AppearanceSelection(new AppearanceChoices(DARK, LIGHT, 100f, false, () -> Optional.of(false), ignored -> new dev.jasper.app.lifecycle.Subscription(() -> {}),
+            () -> InstalledThemes.slateSteel(DARK, LIGHT)));
         editor.show(AppearanceMode.SYSTEM);
         var tiles = (Nodes.Choice) parts(editor).get(0);
         assertThat(tiles.id()).isEqualTo("ui.appearance:policy");
@@ -53,6 +54,7 @@ class AppearanceSelectionTest {
         var system = new CorePages.SystemActions(() -> java.nio.file.Path.of("config.toml"), () -> { }, () -> { }, () -> { }, () -> { }, () -> true);
         var specs = CorePages.specs(AppearanceChoices.of(DARK, LIGHT), system, ShellChoices.NONE, ignored -> { }, false);
         var appearance = specs.getFirst();
-        assertThat(appearance.settings()).startsWith(CoreSettings.UI_APPEARANCE, CoreSettings.TERMINAL_SCHEME);
+        assertThat(appearance.settings()).startsWith(CoreSettings.UI_APPEARANCE, CoreSettings.UI_THEME_DARK, CoreSettings.UI_THEME_LIGHT,
+            CoreSettings.TERMINAL_SCHEME);
     }
 }
diff --git a/jasper-app/src/test/java/dev/jasper/app/settingsui/SettingsDialogTest.java b/jasper-app/src/test/java/dev/jasper/app/settingsui/SettingsDialogTest.java
index b9ea28820e35cfc91f4d3b60a75010a4b8e99613..53a7467f1ee21c3f9ce605e1d0dd5886d1bdaab4 100644
--- a/jasper-app/src/test/java/dev/jasper/app/settingsui/SettingsDialogTest.java
+++ b/jasper-app/src/test/java/dev/jasper/app/settingsui/SettingsDialogTest.java
@@ -224,7 +224,7 @@ class SettingsDialogTest {
         edt(() -> {
             dialog.open(null, "appearance");
             dialog.draft().stage(CoreSettings.UI_APPEARANCE, Optional.of(dev.jasper.app.config.AppearanceMode.LIGHT));
-            dialog.draft().stage(CoreSettings.TERMINAL_SCHEME, Optional.of(dev.jasper.app.config.TerminalSchemeChoice.HIGH_CONTRAST));
+            dialog.draft().stage(CoreSettings.TERMINAL_SCHEME, Optional.of("slate-steel/high-contrast"));
             dialog.apply();
         });
         edt(() -> {});
@@ -233,13 +233,13 @@ class SettingsDialogTest {
             assertThat(saved.getFirst().changes()).containsKeys(CoreSettings.UI_APPEARANCE, CoreSettings.TERMINAL_SCHEME);
             assertThat(dialog.draft().modified()).isTrue();
         });
-        state = state("ui.appearance='light'\nterminal.scheme='high-contrast'\n"); saveResult = CompletableFuture.completedFuture(state);
+        state = state("ui.appearance='light'\nterminal.scheme='slate-steel/high-contrast'\n"); saveResult = CompletableFuture.completedFuture(state);
         edt(dialog::apply); edt(() -> {});
         edt(() -> {
             assertThat(saved).hasSize(2); assertThat(dialog.draft().modified()).isFalse();
-            dialog.draft().stage(CoreSettings.TERMINAL_SCHEME, Optional.of(dev.jasper.app.config.TerminalSchemeChoice.SLATE_STEEL_DARK)); dialog.cancel();
+            dialog.draft().stage(CoreSettings.TERMINAL_SCHEME, Optional.of("slate-steel/dark")); dialog.cancel();
             assertThat(dialog.isOpen()).isFalse(); dialog.open(null,"appearance");
-            assertThat(dialog.draft().value(CoreSettings.TERMINAL_SCHEME)).isEqualTo(dev.jasper.app.config.TerminalSchemeChoice.HIGH_CONTRAST); dialog.cancel();
+            assertThat(dialog.draft().value(CoreSettings.TERMINAL_SCHEME)).isEqualTo("slate-steel/high-contrast"); dialog.cancel();
         });
     }
 
diff --git a/jasper-app/src/test/java/dev/jasper/app/workspace/AlacrittyTabsPreview.java b/jasper-app/src/test/java/dev/jasper/app/workspace/AlacrittyTabsPreview.java
index db156593d6a636867a5e074fc9ee361dcbe9fab4..fd547e4d660d81fdc877548dfeaeb535871e879f 100644
--- a/jasper-app/src/test/java/dev/jasper/app/workspace/AlacrittyTabsPreview.java
+++ b/jasper-app/src/test/java/dev/jasper/app/workspace/AlacrittyTabsPreview.java
@@ -70,7 +70,7 @@ public final class AlacrittyTabsPreview {
                     document.select(); render(root, owner, output, name + "-document", 1040);
                     owner.selectTab(owner.terminalTabs().get(1)); render(root, owner, output, name + "-overflow", 350);
                 }
-                owner.selectVariant(false); owner.selectTerminalScheme(dev.jasper.app.config.TerminalSchemeChoice.SLATE_STEEL_DARK);
+                owner.selectVariant(false); owner.selectTerminalScheme(dev.jasper.app.config.TerminalSchemeChoice.parse("slate-steel/dark").orElseThrow());
                 owner.selectTab(owner.terminalTabs().get(1)); render(root, owner, output, "light-chrome-dark-workspace", 1040);
                 owner.applyBackgroundOpacity(.5); render(root, owner, output, "glass", 1040);
                 owner.applyBackgroundOpacity(1); owner.selectVariant(true);
diff --git a/jasper-app/src/test/java/dev/jasper/app/workspace/ConfigurationControllerTest.java b/jasper-app/src/test/java/dev/jasper/app/workspace/ConfigurationControllerTest.java
index 5adb0495a36decc325680fa45f1427769cb6c790..001a656b9f96c5f8348a659a421e3a99cbf55f26 100644
--- a/jasper-app/src/test/java/dev/jasper/app/workspace/ConfigurationControllerTest.java
+++ b/jasper-app/src/test/java/dev/jasper/app/workspace/ConfigurationControllerTest.java
@@ -656,7 +656,7 @@ class ConfigurationControllerTest {
     }
 
     @Test void savedTerminalSchemeAppliesLiveOnStartAndReloadWithoutARestartNotice() throws Exception {
-        start("[ui]\nappearance='light'\n[terminal]\nscheme='slate-steel-dark'\n");
+        start("[ui]\nappearance='light'\n[terminal]\nscheme='slate-steel/dark'\n");
         edt(() -> assertThat(themes.current()).isEqualTo(dev.jasper.app.appearance.ResolvedTheme.slateSteel(false, JasperTheme.bundled().matching(true))));
         reload("[ui]\nappearance='light'\n[terminal]\nscheme='match'\n");
         edt(() -> {
@@ -758,13 +758,13 @@ class ConfigurationControllerTest {
         until(() -> !seen.isEmpty() && seen.getLast().values().get(dev.jasper.app.config.CoreSettings.WINDOW_TOOLBAR) == ToolbarMode.HIDDEN);
         edt(() -> first.windowCommands().view("view.status_bar").actionPerformed(new java.awt.event.ActionEvent(first, 0, "menu")));
         until(() -> Boolean.FALSE.equals(seen.getLast().values().get(dev.jasper.app.config.CoreSettings.WINDOW_STATUS_BAR)));
-        edt(() -> first.windowCommands().view("view.terminal_scheme.slate_steel_dark").actionPerformed(new java.awt.event.ActionEvent(first, 0, "menu")));
-        until(() -> dev.jasper.app.config.TerminalSchemeChoice.SLATE_STEEL_DARK == seen.getLast().values().get(dev.jasper.app.config.CoreSettings.TERMINAL_SCHEME));
+        edt(() -> first.windowCommands().view("view.terminal_scheme.slate-steel.dark").actionPerformed(new java.awt.event.ActionEvent(first, 0, "menu")));
+        until(() -> "slate-steel/dark".equals(seen.getLast().values().get(dev.jasper.app.config.CoreSettings.TERMINAL_SCHEME)));
         edt(() -> {
             for (var owner : List.of(first, second)) {
                 assertThat(owner.toolbar().isVisible()).isFalse();
                 assertThat(owner.status().isVisible()).isFalse();
-                assertThat(owner.terminalSchemeSelected(dev.jasper.app.config.TerminalSchemeChoice.SLATE_STEEL_DARK)).isTrue();
+                assertThat(owner.terminalSchemeSelected(dev.jasper.app.config.TerminalSchemeChoice.parse("slate-steel/dark").orElseThrow())).isTrue();
             }
         });
         assertThat(Files.readString(directory.resolve("config.toml"))).contains("tab_height = 44");
diff --git a/jasper-app/src/test/java/dev/jasper/app/workspace/FlatTabsPreview.java b/jasper-app/src/test/java/dev/jasper/app/workspace/FlatTabsPreview.java
index 63a35b5df7b34d744edbf55e9cc7336f3b585af8..62773e7b4af787de0a5c1aa6bf24111b5be531c0 100644
--- a/jasper-app/src/test/java/dev/jasper/app/workspace/FlatTabsPreview.java
+++ b/jasper-app/src/test/java/dev/jasper/app/workspace/FlatTabsPreview.java
@@ -67,7 +67,7 @@ public final class FlatTabsPreview {
                     document.select(); render(root, owner, output, name + "-document", 1040);
                     owner.selectTab(owner.terminalTabs().get(1)); render(root, owner, output, name + "-overflow", 350);
                 }
-                owner.selectVariant(false); owner.selectTerminalScheme(dev.jasper.app.config.TerminalSchemeChoice.SLATE_STEEL_DARK);
+                owner.selectVariant(false); owner.selectTerminalScheme(dev.jasper.app.config.TerminalSchemeChoice.parse("slate-steel/dark").orElseThrow());
                 owner.selectTab(owner.terminalTabs().get(1)); render(root, owner, output, "light-chrome-dark-workspace", 1040);
                 owner.applyBackgroundOpacity(.5); render(root, owner, output, "glass", 1040);
             });
diff --git a/jasper-app/src/test/java/dev/jasper/app/workspace/FolderTabsPreview.java b/jasper-app/src/test/java/dev/jasper/app/workspace/FolderTabsPreview.java
index 668aa1f6bd28bbbafda7f3d231f8521dafbe74d6..3170aff15763006984531ea432fe85dcee6151d8 100644
--- a/jasper-app/src/test/java/dev/jasper/app/workspace/FolderTabsPreview.java
+++ b/jasper-app/src/test/java/dev/jasper/app/workspace/FolderTabsPreview.java
@@ -62,7 +62,7 @@ public final class FolderTabsPreview {
                     owner.selectTab(owner.terminalTabs().get(1)); render(root, owner, output, name + "-middle", 1040);
                     owner.selectTab(owner.terminalTabs().get(3)); render(root, owner, output, name + "-overflow", 350);
                 }
-                owner.selectVariant(false); owner.selectTerminalScheme(dev.jasper.app.config.TerminalSchemeChoice.SLATE_STEEL_DARK);
+                owner.selectVariant(false); owner.selectTerminalScheme(dev.jasper.app.config.TerminalSchemeChoice.parse("slate-steel/dark").orElseThrow());
                 owner.selectTab(owner.terminalTabs().get(1)); render(root, owner, output, "light-chrome-dark-workspace", 1040);
                 owner.applyBackgroundOpacity(.5); render(root, owner, output, "glass", 1040);
             });
diff --git a/jasper-app/src/test/java/dev/jasper/app/workspace/GraphiteChromeTest.java b/jasper-app/src/test/java/dev/jasper/app/workspace/GraphiteChromeTest.java
index 87dabf262f56b427d59b348dd014898d6a099753..6020282431f904488efdd6bdc04d619ddf120175 100644
--- a/jasper-app/src/test/java/dev/jasper/app/workspace/GraphiteChromeTest.java
+++ b/jasper-app/src/test/java/dev/jasper/app/workspace/GraphiteChromeTest.java
@@ -109,7 +109,7 @@ class GraphiteChromeTest {
                 showRail(owner);
                 var root = new javax.swing.JRootPane();
                 try (var header = WindowContent.installTitleBar(root, owner, true, title -> {})) {
-                    for (var scheme : dev.jasper.app.config.TerminalSchemeChoice.values()) {
+                    for (var scheme : dev.jasper.app.appearance.ThemeTestSupport.SCHEME_CHOICES) {
                         owner.selectTerminalScheme(scheme);
                         root.setSize(1000, 640); layoutTree(root); layoutTree(root);
                         var grid = owner.tabStrip();
diff --git a/jasper-app/src/test/java/dev/jasper/app/workspace/TerminalSchemeCommandsTest.java b/jasper-app/src/test/java/dev/jasper/app/workspace/TerminalSchemeCommandsTest.java
index 8a7cc516f4ecdb246a49f4dd429a130bfe7cd2e7..ffe5f8df08fb13118d1a7ac53a7020a3939150cc 100644
--- a/jasper-app/src/test/java/dev/jasper/app/workspace/TerminalSchemeCommandsTest.java
+++ b/jasper-app/src/test/java/dev/jasper/app/workspace/TerminalSchemeCommandsTest.java
@@ -40,7 +40,7 @@ class TerminalSchemeCommandsTest {
             var owner = owners[0];
             hidden[0] = owner.currentPane();
             owner.newTab(HOME);
-            owner.selectTerminalScheme(TerminalSchemeChoice.HIGH_CONTRAST);
+            owner.selectTerminalScheme(TerminalSchemeChoice.parse("slate-steel/high-contrast").orElseThrow());
             assertThat(com.formdev.flatlaf.FlatLaf.isLafDark()).as("the UI stays light").isFalse();
             assertThat(hidden[0].view().palette()).as("a background tab").isEqualTo(palette(JasperTheme.bundled().scheme("high-contrast").orElseThrow()));
             assertThat(owners[1].currentPane().view().palette()).as("another window").isEqualTo(palette(JasperTheme.bundled().scheme("high-contrast").orElseThrow()));
@@ -62,13 +62,13 @@ class TerminalSchemeCommandsTest {
         edt(() -> {
             var themes = new ThemeController(true, TerminalSchemeChoice.MATCH);
             var owner = content(launcher(new ArrayDeque<>()), themes); owner.setActive(true);
-            for (var scheme : TerminalSchemeChoice.values()) {
-                var command = owner.windowCommands().view("view.terminal_scheme." + scheme.name().toLowerCase(Locale.ROOT));
+            for (var scheme : dev.jasper.app.appearance.ThemeTestSupport.SCHEME_CHOICES) {
+                var command = owner.windowCommands().view(WindowCommands.schemeCommandId(scheme));
                 command.actionPerformed(new java.awt.event.ActionEvent(owner, 0, ""));
                 assertThat(themes.schemeChoice()).isEqualTo(scheme);
                 assertThat(owner.terminalSchemeSelected(scheme)).isTrue();
             }
-            assertThat(owner.windowCommands().view("view.terminal_scheme.high_contrast").getValue(dev.jasper.app.commands.Command.TITLE))
+            assertThat(owner.windowCommands().view("view.terminal_scheme.slate-steel.high-contrast").getValue(dev.jasper.app.commands.Command.TITLE))
                 .isEqualTo("Terminal Scheme: High Contrast");
         });
     }
diff --git a/jasper-app/src/test/java/dev/jasper/app/workspace/TerminalSchemeSurfaceTest.java b/jasper-app/src/test/java/dev/jasper/app/workspace/TerminalSchemeSurfaceTest.java
index 9e3bb97b1df650b9d5cc90a978dd8e099426d2b8..557c26e66542c6c8acad551d8e1dad1849fa6d66 100644
--- a/jasper-app/src/test/java/dev/jasper/app/workspace/TerminalSchemeSurfaceTest.java
+++ b/jasper-app/src/test/java/dev/jasper/app/workspace/TerminalSchemeSurfaceTest.java
@@ -24,7 +24,7 @@ class TerminalSchemeSurfaceTest {
         var pending = new ArrayDeque<Runnable>();
         WindowContent[] owner = new WindowContent[1];
         edt(() -> {
-            owner[0] = content(launcher(pending), new ThemeController(false, TerminalSchemeChoice.HIGH_CONTRAST));
+            owner[0] = content(launcher(pending), new ThemeController(false, TerminalSchemeChoice.parse("slate-steel/high-contrast").orElseThrow()));
             owner[0].currentTab().rename("other"); owner[0].newTab(HOME); owner[0].currentTab().rename("selected");
             owner[0].update();
         });
diff --git a/jasper-app/src/test/java/dev/jasper/app/workspace/ThemeControllerTest.java b/jasper-app/src/test/java/dev/jasper/app/workspace/ThemeControllerTest.java
index e5df4468148242f590dd805a2d5c4d3603d0f18a..760ca0b169d550697b60f71344467bbffdb4105d 100644
--- a/jasper-app/src/test/java/dev/jasper/app/workspace/ThemeControllerTest.java
+++ b/jasper-app/src/test/java/dev/jasper/app/workspace/ThemeControllerTest.java
@@ -59,7 +59,7 @@ class ThemeControllerTest {
                 "Button.foreground", UIManager.getColor("Button.foreground"));
             var changes = new ArrayList<ResolvedTheme>();
             controller.subscribe((value, refresh) -> changes.add(value)); changes.clear();
-            assertThatThrownBy(() -> controller.configure(false, dev.jasper.app.config.TerminalSchemeChoice.HIGH_CONTRAST,
+            assertThatThrownBy(() -> controller.configure(false, dev.jasper.app.config.TerminalSchemeChoice.parse("slate-steel/high-contrast").orElseThrow(),
                 new dev.jasper.app.config.UiFontConfig("Monospaced", 19)))
                 .isInstanceOf(ThemeController.InstallationFailure.class);
             assertThat(controller.current()).isSameAs(old);
@@ -314,9 +314,9 @@ class ThemeControllerTest {
             assertThat(UIManager.getLookAndFeel()).isSameAs(before);
             assertThat(errors).singleElement().asString().contains("Could not apply theme");
             // The selection rolled back too: a later scheme change keeps Dark instead of retrying Light.
-            themes.configureScheme(dev.jasper.app.config.TerminalSchemeChoice.HIGH_CONTRAST);
+            themes.configureScheme(dev.jasper.app.config.TerminalSchemeChoice.parse("slate-steel/high-contrast").orElseThrow());
             assertThat(themes.current().chrome()).isSameAs(dev.jasper.app.appearance.ThemeTestSupport.generated(true));
-            assertThat(themes.schemeChoice()).isEqualTo(dev.jasper.app.config.TerminalSchemeChoice.HIGH_CONTRAST);
+            assertThat(themes.schemeChoice()).isEqualTo(dev.jasper.app.config.TerminalSchemeChoice.parse("slate-steel/high-contrast").orElseThrow());
         });
     }
 
diff --git a/jasper-app/src/test/java/dev/jasper/app/workspace/WindowCommandsTest.java b/jasper-app/src/test/java/dev/jasper/app/workspace/WindowCommandsTest.java
index 6a5d6ba0579d1f4baa6ab2ed7da833ebd6c51239..ad9bc369a87a0ade4836b35a09c2e84b2fbe80e0 100644
--- a/jasper-app/src/test/java/dev/jasper/app/workspace/WindowCommandsTest.java
+++ b/jasper-app/src/test/java/dev/jasper/app/workspace/WindowCommandsTest.java
@@ -18,7 +18,7 @@ class WindowCommandsTest {
                 assertThat(CommandSearch.find(entries, "paste", java.util.List.of(), 5).stream().map(Command::id)).doesNotContain("paste");
                 assertThat(owner.action(ActionId.SPLIT_RIGHT).getValue(Action.NAME)).isEqualTo(ActionId.SPLIT_RIGHT.label());
                 assertThat(owner.action(ActionId.SPLIT_RIGHT).getValue(Command.TITLE)).isEqualTo("Split Right \u00b7 Vertical");
-                assertThat(entries.stream().map(e -> e.command().id())).contains("select_tab_1", "view.terminal_scheme.slate_steel_dark", "view.window_settings")
+                assertThat(entries.stream().map(e -> e.command().id())).contains("select_tab_1", "view.terminal_scheme.slate-steel.dark", "view.window_settings")
                     .doesNotContain("view.tab_height", "view.rail_width", "view.toolbar_height", "view.status_bar_height");
                 var windowSettings = entries.stream().map(CommandSearch.Entry::command)
                     .filter(c -> c.id().equals("view.window_settings")).findFirst().orElseThrow();
diff --git a/jasper-app/src/test/java/dev/jasper/app/workspace/WindowTabsTest.java b/jasper-app/src/test/java/dev/jasper/app/workspace/WindowTabsTest.java
index 4dead4d335701afcf4560fdeba233192a463d439..0112c95e40f8d9eb1286fb1e8296d0e704da40da 100644
--- a/jasper-app/src/test/java/dev/jasper/app/workspace/WindowTabsTest.java
+++ b/jasper-app/src/test/java/dev/jasper/app/workspace/WindowTabsTest.java
@@ -122,7 +122,7 @@ class WindowTabsTest {
             owner.currentTab().rename("other"); owner.newTab(HOME); owner.currentTab().rename("selected"); owner.update();
             for (boolean chromeDark : new boolean[]{true, false}) {
                 owner.selectVariant(chromeDark);
-                for (var scheme : dev.jasper.app.config.TerminalSchemeChoice.values()) {
+                for (var scheme : dev.jasper.app.appearance.ThemeTestSupport.SCHEME_CHOICES) {
                     owner.selectTerminalScheme(scheme);
                     var strip = owner.windowTabs(); layout(strip, 800, 38);
                     assertThat(strip.getBackground()).as("%s UI, %s terminal", (chromeDark ? "dark" : "light"), scheme).isEqualTo(owner.theme().surface());
diff --git a/jasper-app/src/test/java/dev/jasper/app/workspace/WindowVariantTest.java b/jasper-app/src/test/java/dev/jasper/app/workspace/WindowVariantTest.java
index 32218dec9593853d3917257ec8a966bafe0b80b8..ea8a9a3ada43de7542ea8fa49f05959be5d1d392 100644
--- a/jasper-app/src/test/java/dev/jasper/app/workspace/WindowVariantTest.java
+++ b/jasper-app/src/test/java/dev/jasper/app/workspace/WindowVariantTest.java
@@ -23,14 +23,14 @@ class WindowVariantTest {
     void selectingAVariantRethemesTheWindowAndKeepsTheSchemeAndSessions() throws Exception {
         var pending = new ArrayDeque<Runnable>();
         WindowContent[] owner = new WindowContent[1];
-        edt(() -> owner[0] = content(launcher(pending), new ThemeController(true, TerminalSchemeChoice.HIGH_CONTRAST)));
+        edt(() -> owner[0] = content(launcher(pending), new ThemeController(true, TerminalSchemeChoice.parse("slate-steel/high-contrast").orElseThrow())));
         while (!pending.isEmpty()) pending.remove().run();
         until(() -> owner[0].currentPane().view() != null);
         edt(() -> {
             var session = owner[0].currentPane().session();
             owner[0].selectVariant(false);
             assertThat(owner[0].theme().chrome()).isSameAs(JasperTheme.bundled().generated(false));
-            assertThat(owner[0].terminalSchemeSelected(TerminalSchemeChoice.HIGH_CONTRAST)).as("the scheme stays").isTrue();
+            assertThat(owner[0].terminalSchemeSelected(TerminalSchemeChoice.parse("slate-steel/high-contrast").orElseThrow())).as("the scheme stays").isTrue();
             assertThat(owner[0].currentPane().session()).as("the shell keeps running").isSameAs(session);
             assertThat(owner[0].getBackground()).isEqualTo(ThemeTestSupport.color(false, SemanticColor.SURFACE_TERMINAL));
             assertThat(owner[0].currentPane().view().palette()).isEqualTo(ThemeTestSupport.terminal(JasperTheme.bundled().scheme("high-contrast").orElseThrow()));
JASPER_PATCH
`````

- [ ] **Step 2: See them fail**

Run: `./gradlew :jasper-app:compileTestJava`
Expected: FAIL, among others `cannot find symbol: method parse(String)` on `TerminalSchemeChoice` and
`cannot find symbol: class InstalledThemes`.

- [ ] **Step 3: Apply the production changes**

`````bash
git apply --whitespace=nowarn <<'JASPER_PATCH'
diff --git a/config.example.toml b/config.example.toml
index b1ee2690eeb8ab275f24bc8d1a12a6d4f9dbc3d7..bb9196ebcce38f316c68ddf703cf1e0f86861a71 100644
--- a/config.example.toml
+++ b/config.example.toml
@@ -91,7 +91,8 @@ on_exit = "keep_open"
 # New panes only. "auto" loads Jasper's shell integration script (zsh, bash, fish) after your startup files;
 # "manual" only exports JASPER_SHELL_INTEGRATION for you to source; "off" does neither.
 shell_integration = "auto"
-# Colors for terminal panes, live: "match" follows the UI appearance; or "slate-steel-dark", "slate-steel-light", "high-contrast".
+# Colors for terminal panes, live: "match" uses the shown theme's own scheme; or a scheme id, <theme id>/<scheme key>,
+# such as "slate-steel/dark", "slate-steel/light" or "slate-steel/high-contrast".
 scheme = "match"
 
 [terminal.shell]
@@ -124,9 +125,15 @@ blink = true
 
 [ui]
 # The UI appearance, live across windows: "system" follows the operating system's light or dark setting;
-# "dark" or "light" chooses Slate Steel Dark or Light.
+# "dark" or "light" chooses one. [ui.theme] names the theme each shows.
 appearance = "system"
 
+[ui.theme]
+# The themes the Dark and Light appearances show, live: "slate-steel" or an imported theme's id (its file name in the
+# themes folder without .json). A theme without that variant is reported and Slate Steel is used.
+dark = "slate-steel"
+light = "slate-steel"
+
 [ui.font]
 # Live UI font for app chrome and plugin controls, independent of [terminal.font].
 # "system" uses the platform font on macOS, Linux and Windows.
diff --git a/docs/configuration.md b/docs/configuration.md
index 35ab393ec7dc76e19024bbb2b5ff0a1f86bb0f51..08490997ce4e1b6a72a47652678374cedfb5523f 100644
--- a/docs/configuration.md
+++ b/docs/configuration.md
@@ -171,11 +171,13 @@ family = "system"
 | `terminal.dim_inactive_panes` | `0.3` | Finite number 0–1; 0 disables dimming, 1 fully dims | Live |
 | `terminal.copy_on_select` | `false` | Boolean | Live |
 | `terminal.bell` | `"visual"` | `"visual"`, `"sound"`, `"none"` | Live |
-| `terminal.scheme` | `"match"` | `"match"`, `"slate-steel-dark"`, `"slate-steel-light"`, `"high-contrast"`: the terminal panes' colors ([Themes](themes.md)) | Live |
+| `terminal.scheme` | `"match"` | `"match"` or a scheme id such as `"slate-steel/high-contrast"`: the terminal panes' colors ([Themes](themes.md)) | Live |
 | `terminal.on_exit` | `"keep_open"` | `"keep_open"`, `"close_on_success"`, `"close"` | Live for future shell exits |
 | `terminal.shell_integration` | `"auto"` | `"auto"`, `"manual"`, `"off"` | New pane requests |
 | `ui.animations.enabled` | `true` | Enable app animations; disabling finishes active transitions immediately | Live |
 | `ui.appearance` | `"system"` | `"system"` (the operating system's light or dark), `"dark"`, `"light"` | Live |
+| `ui.theme.dark` | `"slate-steel"` | A theme id: the theme the Dark appearance shows ([Themes](themes.md)) | Live |
+| `ui.theme.light` | `"slate-steel"` | A theme id: the theme the Light appearance shows ([Themes](themes.md)) | Live |
 | `ui.workspace.opacity` | Opaque (omit key) | Number 10–100 percent; macOS only, ignored elsewhere | Live |
 | `ui.workspace.blur` | `"theme"` | `"theme"` (Slate Steel's: no blur), `"on"`, `"off"`; macOS only, ignored elsewhere | Live |
 | `keybindings.<action>` | Platform-specific | Shortcut string or `"none"` | Live |
@@ -759,21 +761,20 @@ Bounded application logging is implemented; see [diagnostics](diagnostics.md) fo
 
 ## Theme
 
-Jasper's theme is Slate Steel, in Dark and Light.
+Jasper's built-in theme is Slate Steel, in Dark and Light; imported themes live in the themes folder ([Themes](themes.md)).
 
-`ui.appearance` chooses the UI appearance: `"dark"` or `"light"` for Slate Steel Dark or Light, or `"system"`
-(the default) to follow the operating system's light or dark setting live. When the operating system's appearance is
-unavailable, `"system"` uses Dark. An operating-system change never writes `config.toml`.
+`ui.appearance` chooses the UI appearance: `"dark"`, `"light"`, or `"system"` (the default) to follow the operating
+system's light or dark setting live. When the operating system's appearance is unavailable, `"system"` uses Dark. An
+operating-system change never writes `config.toml`. `ui.theme.dark` and `ui.theme.light` name the theme each
+appearance shows, `"slate-steel"` by default.
 
-`terminal.scheme` chooses the terminal panes' colours: `"match"` (the default) uses Slate Steel Dark with the Dark
-appearance and Slate Steel Light with the Light one; `"slate-steel-dark"`, `"slate-steel-light"` and
-`"high-contrast"` choose one. Only the terminal text area changes: tabs, the tab strip, the editor and panels stay on
-the UI appearance.
+`terminal.scheme` chooses the terminal panes' colours: `"match"` (the default) uses the shown theme's own scheme; a
+scheme id such as `"slate-steel/high-contrast"` chooses one. Only the terminal text area changes: tabs, the tab strip,
+the editor and panels stay on the UI appearance.
 
 Appearance and scheme changes keep shells, scrollback, split ratios and font choices. A failed installation keeps the
-current appearance, is reported as a warning on `ui.appearance`, and can be retried with Reload config. The former
-`[ui.theme]` table (`name`, `source`, `follow_system`, `workspace`), `terminal.colors` and the `[colors]` table are
-no longer read: each is reported as an unknown setting and ignored.
+current appearance, is reported as a warning on `ui.appearance`, and can be retried with Reload config.
+`terminal.colors` and the `[colors]` table are no longer read: each is reported as an unknown setting and ignored.
 
 ### Interface typography
 
diff --git a/docs/design/theme-engine/migration.csv b/docs/design/theme-engine/migration.csv
index fc7ea83f5883cd0a8344570c914111df5e2a0367..9568c5864abdfba98a4e306c041393c51492f573 100644
--- a/docs/design/theme-engine/migration.csv
+++ b/docs/design/theme-engine/migration.csv
@@ -1202,3 +1202,6 @@ jasper-app/src/test/java/dev/jasper/app/platform/HostClipboardTest.java,app.plat
 jasper-sdk-testkit/src/main/java/dev/jasper/sdk/testing/FakeClipboard.java,sdk-testkit,FakeClipboard,nonvisual-helper,,,jasper-sdk-testkit/src/test/java/dev/jasper/sdk/testing/FakeContractTest.java,SDK 0.16.1 clipboard plan Task 2: nonvisual helper or fixture false positive; the fake host clipboard keeps the application's secret-expiry behavior
 jasper-app/src/main/java/dev/jasper/app/appearance/ThemeLibrary.java,app.appearance,ThemeLibrary,nonvisual-helper,none,none,jasper-app/src/test/java/dev/jasper/app/appearance/ThemeLibraryTest.java,New in theme import Plan 1 Task 2; the installed themes.
 jasper-app/src/test/java/dev/jasper/app/appearance/ThemeLibraryTest.java,app.appearance,ThemeLibraryTest,nonvisual-helper,none,none,jasper-app/src/test/java/dev/jasper/app/appearance/ThemeLibraryTest.java,New in theme import Plan 1 Task 2; test owner of the installed themes.
+jasper-app/src/main/java/dev/jasper/app/config/TerminalSchemeChoice.java,app.config,TerminalSchemeChoice,nonvisual-helper,none,none,jasper-app/src/test/java/dev/jasper/app/config/ConfigLoaderTest.java,New in theme import Plan 1 Task 3; terminal.scheme as match or a scheme id.
+jasper-app/src/main/java/dev/jasper/app/settingsui/SchemePick.java,app.settingsui,SchemePick,nonvisual-helper,none,none,jasper-app/src/test/java/dev/jasper/app/settingsui/AppearancePageTest.java,New in theme import Plan 1 Task 3; the terminal scheme dropdown over the installed schemes.
+jasper-app/src/main/java/dev/jasper/app/settingsui/InstalledThemes.java,app.settingsui,InstalledThemes,nonvisual-helper,none,none,jasper-app/src/test/java/dev/jasper/app/settingsui/AppearancePageTest.java,New in theme import Plan 1 Task 3; what the Appearance page offers.
diff --git a/jasper-app/src/main/java/dev/jasper/app/appearance/ThemeController.java b/jasper-app/src/main/java/dev/jasper/app/appearance/ThemeController.java
index 69b50c288329fbc9df083cd4621205920153d673..8b2e583fcb5faffa21fe6291072a20f9928ae6b7 100644
--- a/jasper-app/src/main/java/dev/jasper/app/appearance/ThemeController.java
+++ b/jasper-app/src/main/java/dev/jasper/app/appearance/ThemeController.java
@@ -87,15 +87,11 @@ public final class ThemeController implements AutoCloseable {
         platformLabelSize = UIManager.getFont("Label.font").getSize2D();
     }
 
-    /** The bundled scheme for a UI variant: the choice's, or with {@code MATCH} the variant's own. */
+    /** The bundled scheme for a UI variant: the choice's, or with {@code MATCH} (or a scheme Slate Steel lacks) the variant's own. */
     public static TerminalScheme scheme(boolean dark, TerminalSchemeChoice choice) {
         var bundled = JasperTheme.bundled();
-        return switch (choice) {
-            case MATCH -> bundled.matching(dark);
-            case SLATE_STEEL_DARK -> bundled.matching(true);
-            case SLATE_STEEL_LIGHT -> bundled.matching(false);
-            case HIGH_CONTRAST -> bundled.scheme("high-contrast").orElseThrow();
-        };
+        return choice.scheme().filter(id -> id.theme().equals(bundled.id())).flatMap(id -> bundled.scheme(id.key()))
+            .orElseGet(() -> bundled.matching(dark));
     }
     private static ResolvedTheme resolve(Slate slate) {
         return ResolvedTheme.slateSteel(slate.dark(), scheme(slate.dark(), slate.scheme()));
diff --git a/jasper-app/src/main/java/dev/jasper/app/application/SettingsAppearance.java b/jasper-app/src/main/java/dev/jasper/app/application/SettingsAppearance.java
index e505490452fddf10327a2e85b6e1e126d3715c9c..001a5602bc87de26d74be177081d6a48b69ce549 100644
--- a/jasper-app/src/main/java/dev/jasper/app/application/SettingsAppearance.java
+++ b/jasper-app/src/main/java/dev/jasper/app/application/SettingsAppearance.java
@@ -2,7 +2,9 @@ package dev.jasper.app.application;
 
 import dev.jasper.app.lifecycle.Subscription;
 import dev.jasper.app.platform.SystemAppearance;
+import dev.jasper.app.appearance.ThemeLibrary;
 import dev.jasper.app.settingsui.AppearanceChoices;
+import dev.jasper.app.settingsui.InstalledThemes;
 import dev.jasper.ui.model.Option;
 import dev.jasper.ui.theme.SemanticColor;
 import dev.jasper.ui.theme.JasperTheme;
@@ -14,17 +16,29 @@ final class SettingsAppearance {
 
     static AppearanceChoices choices(SystemAppearance system) {
         var glass = JasperTheme.bundled().generated(true).glass();
+        var themes = themeChoices(ThemeLibrary.bundledOnly());
         return new AppearanceChoices(preview(true), preview(false), (float) (glass.opacity() * 100), glass.blur(),
             () -> switch (system.current().preference()) {
                 case DARK -> Optional.of(true);
                 case LIGHT -> Optional.of(false);
                 case UNAVAILABLE -> Optional.empty();
-            }, listener -> new Subscription(system.subscribe(ignored -> listener.run())));
+            }, listener -> new Subscription(system.subscribe(ignored -> listener.run())), () -> themes);
     }
 
-    /** A variant's preview: its input, sidebar and app surfaces, text and accent. */
-    static Option.Palette preview(boolean dark) {
-        var palette = JasperTheme.bundled().palette(dark);
+    /** What the Appearance page offers from {@code library}: its themes for each appearance and its schemes, with their labels. */
+    static InstalledThemes themeChoices(ThemeLibrary library) {
+        java.util.function.Function<Boolean, java.util.List<InstalledThemes.ThemeOption>> themes = dark -> library.themes(dark).stream()
+            .map(theme -> new InstalledThemes.ThemeOption(theme.id(), library.label(theme), preview(theme, dark))).toList();
+        return new InstalledThemes(themes.apply(true), themes.apply(false), library.schemes().stream()
+            .map(scheme -> new InstalledThemes.SchemeOption(scheme.id().toString(), library.label(scheme))).toList());
+    }
+
+    /** A Slate Steel variant's preview. */
+    static Option.Palette preview(boolean dark) { return preview(JasperTheme.bundled(), dark); }
+
+    /** A theme variant's preview: its input, sidebar and app surfaces, text and accent. */
+    static Option.Palette preview(JasperTheme theme, boolean dark) {
+        var palette = theme.palette(dark);
         return new Option.Palette(palette.color(SemanticColor.SURFACE_INPUT).getRGB(), palette.color(SemanticColor.SURFACE_SIDEBAR).getRGB(),
             palette.color(SemanticColor.SURFACE_APP).getRGB(), palette.color(SemanticColor.TEXT_PRIMARY).getRGB(),
             palette.color(SemanticColor.ACCENT).getRGB());
diff --git a/jasper-app/src/main/java/dev/jasper/app/config/ConfigSnapshot.java b/jasper-app/src/main/java/dev/jasper/app/config/ConfigSnapshot.java
index 2ac4ade85fc93e08624ab178a2962877868cbc12..2499e2179460b36a4640045a64bf15793cac15d1 100644
--- a/jasper-app/src/main/java/dev/jasper/app/config/ConfigSnapshot.java
+++ b/jasper-app/src/main/java/dev/jasper/app/config/ConfigSnapshot.java
@@ -17,7 +17,7 @@ public record ConfigSnapshot(int tabHeight, ToolbarMode toolbar, boolean statusB
                       Map<String, Map<String, Object>> plugins, UiFontConfig uiFont,
                       int railWidth, int toolbarHeight, int statusBarHeight, boolean nativeDecorations,
                       ViewMode viewMode, GlassSettings glass, AnimationSettings animations,
-                      AppearanceMode appearance, TerminalSchemeChoice terminalScheme) {
+                      AppearanceMode appearance, TerminalSchemeChoice terminalScheme, String darkTheme, String lightTheme) {
     /** The rail's width in logical pixels without {@code window.rail_width}, and its limits. */
     public static final int DEFAULT_RAIL_WIDTH = 28, MIN_RAIL_WIDTH = 28, MAX_RAIL_WIDTH = 64;
     /** The toolbar (and unified macOS title bar) height in logical pixels without {@code window.toolbar_height}, and its limits. */
@@ -47,6 +47,8 @@ public record ConfigSnapshot(int tabHeight, ToolbarMode toolbar, boolean statusB
         Objects.requireNonNull(animations, "animations");
         Objects.requireNonNull(appearance, "appearance");
         Objects.requireNonNull(terminalScheme, "terminalScheme");
+        Objects.requireNonNull(darkTheme, "darkTheme");
+        Objects.requireNonNull(lightTheme, "lightTheme");
         keybindings = Map.copyOf(keybindings);
         // Plugin tables arrive deeply immutable from the loader; only the outer map is copied here.
         plugins = Map.copyOf(plugins);
@@ -77,7 +79,8 @@ public record ConfigSnapshot(int tabHeight, ToolbarMode toolbar, boolean statusB
         return new ConfigSnapshot(30, ToolbarMode.ICONS_AND_LABELS, true, FontConfig.defaults(), Map.of(), 100, 35, TerminalConfig.defaults(),
             false, PaletteSettings.DEFAULT_VISIBLE_ROWS, 10, CoreSettings.SYSTEM_RUN_IN_BACKGROUND.defaultValue(), Map.of(),
             UiFontConfig.defaults(), DEFAULT_RAIL_WIDTH, DEFAULT_TOOLBAR_HEIGHT, DEFAULT_STATUS_BAR_HEIGHT, false, ViewMode.NORMAL,
-            GlassSettings.UNSET, AnimationSettings.defaults(), AppearanceMode.SYSTEM, TerminalSchemeChoice.MATCH);
+            GlassSettings.UNSET, AnimationSettings.defaults(), AppearanceMode.SYSTEM, TerminalSchemeChoice.MATCH,
+            CoreSettings.UI_THEME_DARK.defaultValue(), CoreSettings.UI_THEME_LIGHT.defaultValue());
     }
 
     /** The snapshot for loaded settings plus the keybindings and legacy plugin tables their own readers produced. */
@@ -102,7 +105,9 @@ public record ConfigSnapshot(int tabHeight, ToolbarMode toolbar, boolean statusB
             new GlassSettings(values.get(CoreSettings.UI_WORKSPACE_OPACITY), values.get(CoreSettings.UI_WORKSPACE_BLUR)),
             new AnimationSettings(values.get(CoreSettings.UI_ANIMATIONS_ENABLED), AnimationSettings.DEFAULT_OPEN_MILLIS,
                 AnimationSettings.DEFAULT_CLOSE_MILLIS, AnimationSettings.DEFAULT_SWITCH_MILLIS),
-            values.get(CoreSettings.UI_APPEARANCE), values.get(CoreSettings.TERMINAL_SCHEME));
+            values.get(CoreSettings.UI_APPEARANCE),
+            TerminalSchemeChoice.parse(values.get(CoreSettings.TERMINAL_SCHEME)).orElse(TerminalSchemeChoice.MATCH),
+            values.get(CoreSettings.UI_THEME_DARK), values.get(CoreSettings.UI_THEME_LIGHT));
     }
 
     public KeyBindings bindings(boolean macOs) {
@@ -132,6 +137,8 @@ public record ConfigSnapshot(int tabHeight, ToolbarMode toolbar, boolean statusB
         private AnimationSettings animations;
         private AppearanceMode appearance;
         private TerminalSchemeChoice terminalScheme;
+        private String darkTheme;
+        private String lightTheme;
         private int railWidth;
         private int toolbarHeight;
         private int statusBarHeight;
@@ -156,6 +163,8 @@ public record ConfigSnapshot(int tabHeight, ToolbarMode toolbar, boolean statusB
             animations = source.animations();
             appearance = source.appearance();
             terminalScheme = source.terminalScheme();
+            darkTheme = source.darkTheme();
+            lightTheme = source.lightTheme();
             railWidth = source.railWidth();
             toolbarHeight = source.toolbarHeight();
             statusBarHeight = source.statusBarHeight();
@@ -174,6 +183,8 @@ public record ConfigSnapshot(int tabHeight, ToolbarMode toolbar, boolean statusB
         public Builder font(FontConfig value) { font = value; return this; }
         public Builder appearance(AppearanceMode value) { appearance = value; return this; }
         public Builder terminalScheme(TerminalSchemeChoice value) { terminalScheme = value; return this; }
+        public Builder darkTheme(String value) { darkTheme = value; return this; }
+        public Builder lightTheme(String value) { lightTheme = value; return this; }
         public Builder keybindings(Map<String, String> value) { keybindings = Map.copyOf(value); return this; }
         public Builder columns(int value) { columns = value; return this; }
         public Builder lines(int value) { lines = value; return this; }
@@ -188,7 +199,7 @@ public record ConfigSnapshot(int tabHeight, ToolbarMode toolbar, boolean statusB
         public ConfigSnapshot build() {
             return new ConfigSnapshot(tabHeight, toolbar, statusBar, font, keybindings, columns, lines, terminal, buddyEnabled,
                 visibleRows, longCommandSeconds, backgroundEnabled, plugins, uiFont, railWidth, toolbarHeight, statusBarHeight,
-                nativeDecorations, viewMode, glass, animations, appearance, terminalScheme);
+                nativeDecorations, viewMode, glass, animations, appearance, terminalScheme, darkTheme, lightTheme);
         }
     }
 }
diff --git a/jasper-app/src/main/java/dev/jasper/app/config/CoreSettings.java b/jasper-app/src/main/java/dev/jasper/app/config/CoreSettings.java
index ae8023639a083737b0d7af7364b27c14f7031ec1..23e36c9a4b5f25ebf99341575c8e707fd34e8937 100644
--- a/jasper-app/src/main/java/dev/jasper/app/config/CoreSettings.java
+++ b/jasper-app/src/main/java/dev/jasper/app/config/CoreSettings.java
@@ -230,14 +230,24 @@ public final class CoreSettings {
             AppearanceMode.SYSTEM)
         .label("Appearance").keywords("appearance", "theme", "dark", "light", "system", "slate steel", "look")
         .description("The UI appearance: \"system\" (the default) follows the operating system's light or dark setting;\n"
-            + "\"dark\" or \"light\" chooses Slate Steel Dark or Light. Switches live across windows.").build();
-    public static final Setting<TerminalSchemeChoice> TERMINAL_SCHEME = Setting.of("terminal.scheme", SettingKind.choice(List.of(
-            Map.entry("match", TerminalSchemeChoice.MATCH), Map.entry("slate-steel-dark", TerminalSchemeChoice.SLATE_STEEL_DARK),
-            Map.entry("slate-steel-light", TerminalSchemeChoice.SLATE_STEEL_LIGHT), Map.entry("high-contrast", TerminalSchemeChoice.HIGH_CONTRAST))),
-            TerminalSchemeChoice.MATCH)
+            + "\"dark\" or \"light\" chooses one. [ui.theme] names the theme each shows. Switches live across windows.").build();
+    static final String THEME_ID = "Use a theme id such as \"slate-steel\"; using the default.";
+    public static final Setting<String> UI_THEME_DARK = Setting.of("ui.theme.dark",
+            SettingKind.string(dev.jasper.ui.theme.JasperTheme::validId, THEME_ID), dev.jasper.ui.theme.JasperTheme.BUNDLED_ID)
+        .label("Dark theme").keywords("theme", "dark", "appearance", "slate steel", "imported")
+        .description("The theme the Dark appearance shows: \"slate-steel\" (the default) or an imported theme's id, its file name\n"
+            + "in the themes folder without .json. A theme without a Dark variant is reported and Slate Steel is used. Switches live.").build();
+    public static final Setting<String> UI_THEME_LIGHT = Setting.of("ui.theme.light",
+            SettingKind.string(dev.jasper.ui.theme.JasperTheme::validId, THEME_ID), dev.jasper.ui.theme.JasperTheme.BUNDLED_ID)
+        .label("Light theme").keywords("theme", "light", "appearance", "slate steel", "imported")
+        .description("The theme the Light appearance shows: \"slate-steel\" (the default) or an imported theme's id, its file name\n"
+            + "in the themes folder without .json. A theme without a Light variant is reported and Slate Steel is used. Switches live.").build();
+    static final String SCHEME_ID = "Use \"match\" or a scheme id such as \"slate-steel/high-contrast\"; using the default.";
+    public static final Setting<String> TERMINAL_SCHEME = Setting.of("terminal.scheme",
+            SettingKind.string(text -> TerminalSchemeChoice.parse(text).isPresent(), SCHEME_ID), TerminalSchemeChoice.MATCH.key())
         .label("Terminal scheme").keywords("terminal", "scheme", "colors", "colours", "palette", "high contrast", "slate steel")
-        .description("Colors for terminal panes: \"match\" (the default) follows the UI appearance; \"slate-steel-dark\",\n"
-            + "\"slate-steel-light\" or \"high-contrast\" chooses one. Only the terminal text area changes; updates live.").build();
+        .description("Colors for terminal panes: \"match\" (the default) uses the shown theme's own scheme; a scheme id,\n"
+            + "<theme id>/<scheme key> such as \"slate-steel/high-contrast\", chooses one. Only the terminal text area changes; updates live.").build();
     public static final Setting<Boolean> UI_ANIMATIONS_ENABLED = Setting.of("ui.animations.enabled", SettingKind.bool(), true)
         .label("Enable animations").keywords("animations", "motion", "transitions", "reduce motion")
         .description("Enable animations throughout the app. Disabling finishes running transitions immediately. Updates live in every window.").build();
@@ -257,7 +267,7 @@ public final class CoreSettings {
             PALETTE_VISIBLE_ROWS, TERMINAL_SCROLLBACK, TERMINAL_OPTION_AS_META, TERMINAL_DIM_INACTIVE_PANES, TERMINAL_COPY_ON_SELECT,
             TERMINAL_BELL, TERMINAL_ON_EXIT, TERMINAL_SHELL_INTEGRATION, TERMINAL_SCHEME, TERMINAL_FONT_FAMILY, TERMINAL_FONT_SIZE,
             TERMINAL_FONT_FALLBACK, TERMINAL_FONT_LIGATURES, TERMINAL_FONT_LINE_HEIGHT, TERMINAL_SHELL_PROFILE, TERMINAL_SHELL_PROGRAM, TERMINAL_SHELL_ARGS,
-            TERMINAL_CURSOR_SHAPE, TERMINAL_CURSOR_BLINK, TERMINAL_ENV, UI_APPEARANCE, UI_FONT_FAMILY, UI_FONT_SIZE,
+            TERMINAL_CURSOR_SHAPE, TERMINAL_CURSOR_BLINK, TERMINAL_ENV, UI_APPEARANCE, UI_THEME_DARK, UI_THEME_LIGHT, UI_FONT_FAMILY, UI_FONT_SIZE,
             UI_WORKSPACE_OPACITY, UI_WORKSPACE_BLUR, UI_ANIMATIONS_ENABLED),
         Set.of(List.of("keybindings"), List.of("plugins"), List.of("palette", "scopes")),
         Map.ofEntries(
@@ -271,6 +281,7 @@ public final class CoreSettings {
             Map.entry(List.of("terminal", "shell"), "The shell new panes start."),
             Map.entry(List.of("terminal", "cursor"), "The cursor a program sees until it chooses its own."),
             Map.entry(List.of("ui"), "The interface: its appearance, fonts, workspace glass and animations."),
+            Map.entry(List.of("ui", "theme"), "The theme each appearance shows: slate-steel or an imported theme's id."),
             Map.entry(List.of("ui", "font"), "Interface typography, independent of the terminal font."),
             Map.entry(List.of("ui", "workspace"), "Translucent terminal and editor backgrounds on macOS; omitted values keep the terminal area opaque."),
             Map.entry(List.of("ui", "animations"), "App-wide animation policy. Changes apply live.")));
diff --git a/jasper-app/src/main/java/dev/jasper/app/config/TerminalSchemeChoice.java b/jasper-app/src/main/java/dev/jasper/app/config/TerminalSchemeChoice.java
index 5038b6b34a09470890a6503af3de91be050fa384..67bbdc4cf0e3881ac2e49b02d1cee0b59e9e0da4 100644
--- a/jasper-app/src/main/java/dev/jasper/app/config/TerminalSchemeChoice.java
+++ b/jasper-app/src/main/java/dev/jasper/app/config/TerminalSchemeChoice.java
@@ -1,12 +1,25 @@
 package dev.jasper.app.config;
 
-/** {@code terminal.scheme}: the terminal panes' colour scheme; {@code MATCH} follows the UI variant. */
-public enum TerminalSchemeChoice {
-    MATCH("match"), SLATE_STEEL_DARK("slate-steel-dark"), SLATE_STEEL_LIGHT("slate-steel-light"), HIGH_CONTRAST("high-contrast");
+import dev.jasper.ui.theme.SchemeId;
+import java.util.Objects;
+import java.util.Optional;
 
-    private final String key;
-    TerminalSchemeChoice(String key) { this.key = key; }
+/** {@code terminal.scheme}: {@code match}, the shown theme's own scheme, or one terminal scheme by its id. */
+public record TerminalSchemeChoice(Optional<SchemeId> scheme) {
+    /** The default: the shown theme's matching scheme. */
+    public static final TerminalSchemeChoice MATCH = new TerminalSchemeChoice(Optional.empty());
 
-    /** The value in config.toml, which is also the bundled theme file's scheme key. */
-    public String key() { return key; }
+    public TerminalSchemeChoice { Objects.requireNonNull(scheme, "scheme"); }
+
+    /** One scheme, whichever theme is shown. */
+    public static TerminalSchemeChoice of(SchemeId id) { return new TerminalSchemeChoice(Optional.of(id)); }
+
+    /** {@code match} or a scheme id such as {@code slate-steel/high-contrast}; empty for anything else. */
+    public static Optional<TerminalSchemeChoice> parse(String text) {
+        if ("match".equals(text)) return Optional.of(MATCH);
+        return SchemeId.parse(text).map(TerminalSchemeChoice::of);
+    }
+
+    /** The value in config.toml: {@code match} or the scheme id. */
+    public String key() { return scheme.map(SchemeId::toString).orElse("match"); }
 }
diff --git a/jasper-app/src/main/java/dev/jasper/app/settingsui/AppearanceChoices.java b/jasper-app/src/main/java/dev/jasper/app/settingsui/AppearanceChoices.java
index 3bdcca6d24f4ec1de0c9883457f3ec0248639542..06dd6112d7daadee236eba1f9686b8ae7d961af0 100644
--- a/jasper-app/src/main/java/dev/jasper/app/settingsui/AppearanceChoices.java
+++ b/jasper-app/src/main/java/dev/jasper/app/settingsui/AppearanceChoices.java
@@ -18,16 +18,20 @@ import java.util.function.Supplier;
  * @param blur whether the theme blurs a translucent workspace
  * @param systemDark the operating system's appearance, empty when unavailable
  * @param onChanged runs its argument whenever the operating system's appearance changes
+ * @param themes the installed themes and schemes, read again whenever the page refreshes
  */
 public record AppearanceChoices(Option.Palette dark, Option.Palette light, float opacity, boolean blur,
-                                Supplier<Optional<Boolean>> systemDark, Function<Runnable, Subscription> onChanged) {
+                                Supplier<Optional<Boolean>> systemDark, Function<Runnable, Subscription> onChanged,
+                                Supplier<InstalledThemes> themes) {
     public AppearanceChoices {
         Objects.requireNonNull(dark, "dark"); Objects.requireNonNull(light, "light");
         Objects.requireNonNull(systemDark, "systemDark"); Objects.requireNonNull(onChanged, "onChanged");
+        Objects.requireNonNull(themes, "themes");
     }
 
-    /** Fixed previews, an opaque workspace and no system appearance, for pages built without an application. */
+    /** Fixed previews, Slate Steel alone, an opaque workspace and no system appearance, for pages built without an application. */
     public static AppearanceChoices of(Option.Palette dark, Option.Palette light) {
-        return new AppearanceChoices(dark, light, 100f, false, Optional::empty, ignored -> new Subscription(() -> { }));
+        var themes = InstalledThemes.slateSteel(dark, light);
+        return new AppearanceChoices(dark, light, 100f, false, Optional::empty, ignored -> new Subscription(() -> { }), () -> themes);
     }
 }
diff --git a/jasper-app/src/main/java/dev/jasper/app/settingsui/CorePages.java b/jasper-app/src/main/java/dev/jasper/app/settingsui/CorePages.java
index 8ee02e438a9eaeb809e66842117c8507cc209eaf..9499282d0d4af75fa05036de367f47ec8646d402 100644
--- a/jasper-app/src/main/java/dev/jasper/app/settingsui/CorePages.java
+++ b/jasper-app/src/main/java/dev/jasper/app/settingsui/CorePages.java
@@ -77,8 +77,9 @@ public final class CorePages {
             SchemaPage.page("appearance", "Appearance").parent("appearance-behavior").anchor("theme")
                 .section("Theme")
                 .row(UI_APPEARANCE, Editors.appearance(appearance))
-                .row(TERMINAL_SCHEME, Editors.choice(Map.of("match", Editors.SAME_AS_UI_THEME, "slate-steel-dark", "Slate Steel Dark",
-                    "slate-steel-light", "Slate Steel Light", "high-contrast", "High Contrast"))).note("Colors for terminal panes")
+                .row(UI_THEME_DARK).shownWhen(false)
+                .row(UI_THEME_LIGHT).shownWhen(false)
+                .row(TERMINAL_SCHEME, Editors.schemeChoice(appearance)).note("Colors for terminal panes")
                 .section("Workspace")
                 .row(UI_WORKSPACE_OPACITY, WorkspaceEditors.opacity(appearance)).shownWhen(glass)
                 .row(UI_WORKSPACE_BLUR, WorkspaceEditors.blur(appearance)).shownWhen(glass)
diff --git a/jasper-app/src/main/java/dev/jasper/app/settingsui/Editors.java b/jasper-app/src/main/java/dev/jasper/app/settingsui/Editors.java
index 854bb5ad74e7f5d87d8edba304c84bf90b50a8d8..32202913d2d7b8b1dfcbc9fa8fac30825a00c143 100644
--- a/jasper-app/src/main/java/dev/jasper/app/settingsui/Editors.java
+++ b/jasper-app/src/main/java/dev/jasper/app/settingsui/Editors.java
@@ -139,6 +139,12 @@ public final class Editors {
     /** The terminal scheme entry for {@code match}. */
     static final String SAME_AS_UI_THEME = "Same as UI theme";
 
+    /** {@code terminal.scheme}: "Same as UI theme", then the installed schemes {@code appearance} lists, read again on refresh. */
+    public static Factory<String> schemeChoice(AppearanceChoices appearance) {
+        return new Factory<>((setting, context) -> new SchemePick(setting.label(), () -> appearance.themes().get().schemes()),
+            List.of(SAME_AS_UI_THEME, "Slate Steel Dark", "Slate Steel Light", "High Contrast"));
+    }
+
     /** The Dark / Light / Match System tiles, then the Theme row with its one entry, Slate Steel. */
     public static Factory<AppearanceMode> appearance(AppearanceChoices choices) {
         return new Factory<>((setting, context) -> new AppearanceSelection(choices),
diff --git a/jasper-app/src/main/java/dev/jasper/app/settingsui/InstalledThemes.java b/jasper-app/src/main/java/dev/jasper/app/settingsui/InstalledThemes.java
new file mode 100644
index 0000000000000000000000000000000000000000..dec03f0a605ab06917aeedc161b8e4d8cfd68ef4
--- /dev/null
+++ b/jasper-app/src/main/java/dev/jasper/app/settingsui/InstalledThemes.java
@@ -0,0 +1,35 @@
+package dev.jasper.app.settingsui;
+
+import dev.jasper.ui.model.Option;
+import java.util.List;
+import java.util.Objects;
+
+/**
+ * What the Appearance page offers, read again whenever the page refreshes: the themes for each appearance and every
+ * terminal scheme, in order.
+ *
+ * @param dark the themes with a Dark variant, Slate Steel first
+ * @param light the themes with a Light variant, Slate Steel first
+ * @param schemes every installed terminal scheme, Slate Steel's first
+ */
+public record InstalledThemes(List<ThemeOption> dark, List<ThemeOption> light, List<SchemeOption> schemes) {
+    public InstalledThemes { dark = List.copyOf(dark); light = List.copyOf(light); schemes = List.copyOf(schemes); }
+
+    /** A theme for one appearance: its id, its label and its tile preview. */
+    public record ThemeOption(String id, String label, Option.Palette preview) {
+        public ThemeOption { Objects.requireNonNull(id, "id"); Objects.requireNonNull(label, "label"); Objects.requireNonNull(preview, "preview"); }
+    }
+
+    /** A terminal scheme: its value in config.toml and its label. */
+    public record SchemeOption(String key, String label) {
+        public SchemeOption { Objects.requireNonNull(key, "key"); Objects.requireNonNull(label, "label"); }
+    }
+
+    /** Slate Steel alone, for pages built without an application: these previews and its three schemes. */
+    public static InstalledThemes slateSteel(Option.Palette dark, Option.Palette light) {
+        return new InstalledThemes(List.of(new ThemeOption("slate-steel", "Slate Steel", dark)),
+            List.of(new ThemeOption("slate-steel", "Slate Steel", light)),
+            List.of(new SchemeOption("slate-steel/dark", "Slate Steel Dark"), new SchemeOption("slate-steel/light", "Slate Steel Light"),
+                new SchemeOption("slate-steel/high-contrast", "High Contrast")));
+    }
+}
diff --git a/jasper-app/src/main/java/dev/jasper/app/settingsui/SchemePick.java b/jasper-app/src/main/java/dev/jasper/app/settingsui/SchemePick.java
new file mode 100644
index 0000000000000000000000000000000000000000..5019da601d9c9425e6a137866b028c29416699d4
--- /dev/null
+++ b/jasper-app/src/main/java/dev/jasper/app/settingsui/SchemePick.java
@@ -0,0 +1,53 @@
+package dev.jasper.app.settingsui;
+
+import dev.jasper.ui.model.ControlState;
+import dev.jasper.ui.model.Nodes;
+import dev.jasper.ui.model.Option;
+import dev.jasper.ui.model.UiEvent;
+import java.util.ArrayList;
+import java.util.List;
+import java.util.Optional;
+import java.util.function.Supplier;
+
+/**
+ * {@code terminal.scheme}'s dropdown: "Same as UI theme", then every installed scheme, read again on each refresh. A
+ * saved scheme that is not installed is shown as such until another is chosen. EDT only.
+ */
+final class SchemePick implements ValueEditor<String> {
+    private final String label;
+    private final Supplier<List<InstalledThemes.SchemeOption>> schemes;
+    private List<InstalledThemes.SchemeOption> offered = List.of();
+    private String value = "match";
+
+    SchemePick(String label, Supplier<List<InstalledThemes.SchemeOption>> schemes) {
+        this.label = label;
+        this.schemes = schemes;
+        refresh();
+    }
+
+    /** The entries' labels, in order. */
+    List<String> labels() { return options().stream().map(Option::label).toList(); }
+
+    private List<Option> options() {
+        var options = new ArrayList<Option>();
+        options.add(new Option("match", Editors.SAME_AS_UI_THEME, Optional.empty()));
+        for (var scheme : offered) options.add(new Option(scheme.key(), scheme.label(), Optional.empty()));
+        if (options.stream().noneMatch(option -> option.id().equals(value)))
+            options.add(new Option(value, value + " (not installed)", Optional.empty()));
+        return options;
+    }
+
+    @Override public Nodes.Node node(String id, ControlState state) {
+        return new Nodes.Choice(id, state, Nodes.ChoiceKind.DROPDOWN, options(), value, label, "");
+    }
+    @Override public String value() { return value; }
+    @Override public void show(String value) { this.value = value; }
+    @Override public boolean event(String id, UiEvent event) {
+        if (!(event instanceof UiEvent.Selected picked) || !picked.id().equals(id)) return false;
+        String next = picked.ids().stream().findFirst().orElse("");
+        if (next.isEmpty() || next.equals(value) || options().stream().noneMatch(option -> option.id().equals(next))) return false;
+        value = next;
+        return true;
+    }
+    @Override public void refresh() { offered = List.copyOf(schemes.get()); }
+}
diff --git a/jasper-app/src/main/java/dev/jasper/app/workspace/WindowCommands.java b/jasper-app/src/main/java/dev/jasper/app/workspace/WindowCommands.java
index 00ea4292f7b344ec986b4e46e88419020ea40ddf..d4c412fef9b1e22ede2a543210681f34ac138509 100644
--- a/jasper-app/src/main/java/dev/jasper/app/workspace/WindowCommands.java
+++ b/jasper-app/src/main/java/dev/jasper/app/workspace/WindowCommands.java
@@ -5,7 +5,10 @@ import dev.jasper.app.commands.Command;
 import dev.jasper.app.commands.CommandRegistry;
 import dev.jasper.app.platform.AppIcons;
 import dev.jasper.app.lifecycle.Subscription;
+import dev.jasper.app.config.TerminalSchemeChoice;
 import dev.jasper.app.config.ToolbarMode;
+import dev.jasper.ui.theme.JasperTheme;
+import dev.jasper.ui.theme.TerminalScheme;
 
 import java.awt.event.ActionEvent;
 import java.util.ArrayList;
@@ -62,14 +65,10 @@ final class WindowCommands implements AutoCloseable {
         add(registry, "view.rail", "Rail", "Hide Rail", () -> owner.setRailVisible(!owner.railVisible()), List.of("panels", "sidebar"));
         add(registry, "view.buddy", "Show Jasper", "Hide Jasper", () -> { owner.onToggleBuddy.run(); owner.updateActions(); },
             List.of("jasper", "mascot", "turtle", "desk buddy"));
-        for (var scheme : dev.jasper.app.config.TerminalSchemeChoice.values()) {
-            String label = switch (scheme) {
-                case MATCH -> "Same as UI theme";
-                case SLATE_STEEL_DARK -> "Slate Steel Dark";
-                case SLATE_STEEL_LIGHT -> "Slate Steel Light";
-                case HIGH_CONTRAST -> "High Contrast";
-            };
-            add(registry, "view.terminal_scheme." + scheme.name().toLowerCase(java.util.Locale.ROOT), "Terminal: " + label,
+        for (var scheme : schemeChoices()) {
+            String label = scheme.scheme().flatMap(id -> JasperTheme.bundled().scheme(id.key())).map(TerminalScheme::name)
+                .orElse("Same as UI theme");
+            add(registry, schemeCommandId(scheme), "Terminal: " + label,
                 "Terminal Scheme: " + label, () -> owner.selectTerminalScheme(scheme), List.of("terminal", "scheme", "colors", "palette"));
         }
         add(registry, "view.themes", "Themes…", "Themes…", owner::openThemesSettings, List.of("theme", "appearance", "dark", "light", "terminal scheme"));
@@ -98,6 +97,18 @@ final class WindowCommands implements AutoCloseable {
 
     Action view(String id) { return views.get(id); }
 
+    /** "Same as UI theme", then Slate Steel's schemes. */
+    private static List<TerminalSchemeChoice> schemeChoices() {
+        var choices = new ArrayList<TerminalSchemeChoice>(List.of(TerminalSchemeChoice.MATCH));
+        for (var scheme : JasperTheme.bundled().schemes()) choices.add(TerminalSchemeChoice.of(scheme.id()));
+        return choices;
+    }
+
+    /** The command that chooses {@code choice}: {@code view.terminal_scheme.match} or {@code view.terminal_scheme.<theme>.<key>}. */
+    static String schemeCommandId(TerminalSchemeChoice choice) {
+        return "view.terminal_scheme." + choice.scheme().map(id -> id.theme() + "." + id.key()).orElse("match");
+    }
+
     void refresh() {
         boolean zen = owner.zenMode();
         owner.action(ActionId.TOGGLE_ZEN_MODE).putValue(Action.SELECTED_KEY, zen);
@@ -117,8 +128,8 @@ final class WindowCommands implements AutoCloseable {
             var action = view("view.toolbar." + mode.name().toLowerCase(java.util.Locale.ROOT));
             action.putValue(Action.SELECTED_KEY, owner.toolbarMode() == mode);
         }
-        for (var scheme : dev.jasper.app.config.TerminalSchemeChoice.values()) {
-            var action = view("view.terminal_scheme." + scheme.name().toLowerCase(java.util.Locale.ROOT));
+        for (var scheme : schemeChoices()) {
+            var action = view(schemeCommandId(scheme));
             action.putValue(Action.SELECTED_KEY, owner.terminalSchemeSelected(scheme));
             action.setEnabled(true);
         }
diff --git a/jasper-app/src/main/java/dev/jasper/app/workspace/WindowContent.java b/jasper-app/src/main/java/dev/jasper/app/workspace/WindowContent.java
index a5ce7358df02a6556b73619ac96a2b628d9f834d..ffd2aff092aef7e0d11780f1dc8f431f669b1861 100644
--- a/jasper-app/src/main/java/dev/jasper/app/workspace/WindowContent.java
+++ b/jasper-app/src/main/java/dev/jasper/app/workspace/WindowContent.java
@@ -1130,13 +1130,13 @@ public final class WindowContent extends JPanel implements AutoCloseable {
 
     /** View \u203a Terminal Scheme: saved for every window; applied to this window's themes when no configuration is connected. */
     void selectTerminalScheme(dev.jasper.app.config.TerminalSchemeChoice scheme) {
-        choose(dev.jasper.app.config.CoreSettings.TERMINAL_SCHEME, scheme, () -> {
+        choose(dev.jasper.app.config.CoreSettings.TERMINAL_SCHEME, scheme.key(), () -> {
             themes.configureScheme(scheme);
             chrome.refreshTheme();
         });
     }
 
-    boolean terminalSchemeSelected(dev.jasper.app.config.TerminalSchemeChoice scheme) { return themes.schemeChoice() == scheme; }
+    boolean terminalSchemeSelected(dev.jasper.app.config.TerminalSchemeChoice scheme) { return themes.schemeChoice().equals(scheme); }
 
     /** View \u203a Toolbar. */
     void chooseToolbarMode(ToolbarMode mode) {
JASPER_PATCH
`````

- [ ] **Step 4: Run the tests**

```bash
./gradlew :jasper-app:test --tests "dev.jasper.app.config.*" --tests "dev.jasper.app.settingsui.*" \
  --tests "*TerminalScheme*" --tests "*SlateSteelController*" --tests "*ThemeControllerTest" --tests "*WindowCommandsTest" \
  --tests "*WindowVariantTest" --tests "*WindowTabsTest" --tests "*GraphiteChromeTest" --tests "*ConfigurationControllerTest" \
  --tests "*JasperApplicationThemesTest" --tests "*SystemThemeApplicationTest" --tests "*ThemeMigrationCoverageTest" \
  --tests "*ColourLiteralScanTest"
```

Expected: PASS (about 370 tests).

- [ ] **Step 5: Commit**

```bash
git add -A
git commit -m "Theme picks and scheme ids in the settings, and a scheme dropdown over the installed schemes

🤖 Generated with Claude Code at The Home Depot

Co-Authored-By: Claude <noreply@anthropic.com>"
```

### Task 4: The runtime: selection, controller, configuration and start-up

**What changes:**
- `ThemeSelection` (record: library, Dark theme, Light theme, scheme choice): `of(library, dark, light, scheme, problems)`
  replaces each pick the library cannot honour by its default and reports it as (setting key, "… Use one of …; using
  the default."); `terminal(dark)` is the chosen scheme or, with `match`, the shown theme's own.
- `ResolvedTheme.of(dark, selection)`; `ThemeController` holds a selection (constructors and `startup` for a
  selection; the Slate Steel overloads stay), `configure(dark, selection, font)`, `selection()`, `library()`; a failure
  message always names the variant ("Could not apply theme: Slate Steel Dark").
- `ConfigurationController` takes the themes folder, rescans it with `ConfigService.watchWith` (worker thread, before
  every forced or changed publication), builds the selection from the snapshot and reports skipped files, unusable
  picks and installation failures as its theme problems (`ConfigDiagnostic` warnings; a file problem's diagnostic is the
  file, with its line when known).
- `JasperApplication` gains a constructor with the themes folder, reads the library before the first theme is
  installed, and `ApplicationBootstrap` passes `dirs.themes()`.

**Files:** create `appearance/ThemeSelection.java`, `appearance/ThemeSelectionTest.java`; modify `appearance/{ResolvedTheme,ThemeController}.java`,
`application/{ConfigurationController,JasperApplication}.java`, `bootstrap/ApplicationBootstrap.java`, `migration.csv`;
tests `SlateSteelControllerTest`, `ConfigurationTestSupport`, `ConfigurationControllerTest`.

**Interfaces:**
- Consumes: Task 2's `ThemeLibrary`, Task 3's `TerminalSchemeChoice`, `ConfigSnapshot.darkTheme()/lightTheme()`.
- Produces: `record ThemeSelection(ThemeLibrary library, JasperTheme dark, JasperTheme light, TerminalSchemeChoice scheme)`:
  `static slateSteel(TerminalSchemeChoice)`, `static of(ThemeLibrary, String dark, String light, TerminalSchemeChoice, BiConsumer<String, String> problems)`,
  `JasperTheme theme(boolean dark)`, `TerminalScheme terminal(boolean dark)`, `ThemeSelection withScheme(TerminalSchemeChoice)`;
  `ResolvedTheme.of(boolean, ThemeSelection)`; `ThemeController(boolean, ThemeSelection)`,
  `ThemeController.startup(boolean, ThemeSelection, Consumer<InstallationFailure>)`, `ThemeSelection selection()`,
  `ThemeLibrary library()`, `configure(boolean, ThemeSelection, UiFontConfig)`;
  `ConfigurationController(ThemeController, ConfigService, Consumer<Path>, SystemAppearance.Observation, Path themesFolder)`;
  `JasperApplication(ConfigService, ShellLauncher, CommandHistory, Path buddyStateFile, Runnable terminate, Path shellIntegrationDir, Path themesFolder)`;
  test `ConfigurationTestSupport(ThemeController, ConfigService, Path themesFolder)`.
- Removed: `ThemeController.scheme(boolean, TerminalSchemeChoice)`.

- [ ] **Step 1: Apply the test changes**

`````bash
git apply --whitespace=nowarn <<'JASPER_PATCH'
diff --git a/jasper-app/src/test/java/dev/jasper/app/appearance/SlateSteelControllerTest.java b/jasper-app/src/test/java/dev/jasper/app/appearance/SlateSteelControllerTest.java
index e821376c9e904901968a0b60be8cf3b2941fee15..98eda5963828e674d08ce7953e16b33f10516170 100644
--- a/jasper-app/src/test/java/dev/jasper/app/appearance/SlateSteelControllerTest.java
+++ b/jasper-app/src/test/java/dev/jasper/app/appearance/SlateSteelControllerTest.java
@@ -17,10 +17,10 @@ import org.junit.jupiter.api.extension.ExtendWith;
 class SlateSteelControllerTest {
     static final JasperTheme STEEL = JasperTheme.bundled();
 
-    @Test void aFailureNamesTheVariantOnlyForSlateSteelItself() {
+    @Test void aFailureNamesTheThemeAndItsVariant() {
         var copy = ThemeTestSupport.recolored(STEEL.generated(true), java.util.Map.of());
         assertThat(new ThemeController.InstallationFailure(copy, new IllegalStateException("x")).getMessage())
-            .isEqualTo("Could not apply theme: Slate Steel");
+            .isEqualTo("Could not apply theme: Slate Steel Dark");
         assertThat(new ThemeController.InstallationFailure(STEEL.generated(false), new IllegalStateException("x")).getMessage())
             .isEqualTo("Could not apply theme: Slate Steel Light");
     }
@@ -63,10 +63,12 @@ class SlateSteelControllerTest {
         assertThat(controller.current().palette().background()).isEqualTo(STEEL.matching(true).colors().background());
     }
 
-    @Test void everySchemeChoiceNamesABundledScheme() {
-        for (var choice : dev.jasper.app.appearance.ThemeTestSupport.SCHEME_CHOICES)
+    @Test void everySchemeChoiceShowsABundledScheme() {
+        for (var choice : ThemeTestSupport.SCHEME_CHOICES)
             for (boolean dark : new boolean[] {true, false})
-                assertThat(ThemeController.scheme(dark, choice)).isNotNull();
-        assertThat(ThemeController.scheme(false, TerminalSchemeChoice.MATCH)).isEqualTo(JasperTheme.bundled().matching(false));
+                assertThat(ThemeSelection.slateSteel(choice).terminal(dark)).isNotNull();
+        assertThat(ThemeSelection.slateSteel(TerminalSchemeChoice.MATCH).terminal(false)).isEqualTo(STEEL.matching(false));
+        assertThat(ThemeSelection.slateSteel(TerminalSchemeChoice.parse("ocean/dark").orElseThrow()).scheme())
+            .as("a scheme Slate Steel lacks becomes match").isEqualTo(TerminalSchemeChoice.MATCH);
     }
 }
diff --git a/jasper-app/src/test/java/dev/jasper/app/appearance/ThemeSelectionTest.java b/jasper-app/src/test/java/dev/jasper/app/appearance/ThemeSelectionTest.java
new file mode 100644
index 0000000000000000000000000000000000000000..c4b5d09edaa2011b9cca33aeff14a5138f6ba3d8
--- /dev/null
+++ b/jasper-app/src/test/java/dev/jasper/app/appearance/ThemeSelectionTest.java
@@ -0,0 +1,92 @@
+package dev.jasper.app.appearance;
+
+import static org.assertj.core.api.Assertions.*;
+
+import dev.jasper.app.config.TerminalSchemeChoice;
+import dev.jasper.app.testsupport.EdtTestExtension;
+import dev.jasper.ui.theme.GeneratedTheme;
+import dev.jasper.ui.theme.JasperTheme;
+import java.nio.file.Files;
+import java.nio.file.Path;
+import java.util.ArrayList;
+import java.util.LinkedHashMap;
+import java.util.Map;
+import org.junit.jupiter.api.Test;
+import org.junit.jupiter.api.extension.ExtendWith;
+import org.junit.jupiter.api.io.TempDir;
+
+/** spec 3.3–3.4: a pick the library cannot honour is reported and replaced; the controller shows any library theme. */
+@ExtendWith(EdtTestExtension.class)
+class ThemeSelectionTest {
+    @TempDir Path home;
+
+    /** Slate Steel's file as {@code ocean} (both variants) and a terminal-only {@code zenburn}. */
+    ThemeLibrary library() throws Exception {
+        var themes = Files.createDirectories(home.resolve("themes"));
+        Files.writeString(themes.resolve("ocean.json"), ThemeLibraryTest.uiTheme("Ocean"));
+        Files.writeString(themes.resolve("zenburn.json"), ThemeLibraryTest.terminalTheme("Zenburn"));
+        return ThemeLibrary.read(themes, ThemeLibrary.bundledOnly());
+    }
+
+    @Test void picksTheLibraryHasAreKept() throws Exception {
+        var library = library();
+        var problems = new LinkedHashMap<String, String>();
+        var zenburn = TerminalSchemeChoice.parse("zenburn/dark").orElseThrow();
+        var selection = ThemeSelection.of(library, "ocean", "slate-steel", zenburn, problems::put);
+        assertThat(problems).isEmpty();
+        assertThat(selection.theme(true)).isSameAs(library.theme("ocean").orElseThrow());
+        assertThat(selection.theme(false)).isSameAs(JasperTheme.bundled());
+        assertThat(selection.terminal(true).name()).isEqualTo("Zenburn");
+        assertThat(selection.terminal(false).name()).as("a chosen scheme whatever the appearance").isEqualTo("Zenburn");
+        var matching = selection.withScheme(TerminalSchemeChoice.MATCH);
+        assertThat(matching.terminal(true)).isSameAs(library.theme("ocean").orElseThrow().matching(true));
+        assertThat(matching.terminal(false)).isSameAs(JasperTheme.bundled().matching(false));
+    }
+
+    @Test void aPickTheLibraryCannotHonourIsReportedAndReplaced() throws Exception {
+        var library = library();
+        var problems = new LinkedHashMap<String, String>();
+        var selection = ThemeSelection.of(library, "zenburn", "nord", TerminalSchemeChoice.parse("nord/dark").orElseThrow(), problems::put);
+        assertThat(selection.theme(true)).isSameAs(JasperTheme.bundled());
+        assertThat(selection.theme(false)).isSameAs(JasperTheme.bundled());
+        assertThat(selection.scheme()).isEqualTo(TerminalSchemeChoice.MATCH);
+        assertThat(problems).containsExactly(
+            Map.entry("ui.theme.dark", "\"zenburn\" has no Dark variant. Use one of \"slate-steel\", \"ocean\"; using the default."),
+            Map.entry("ui.theme.light", "\"nord\" is not an installed theme. Use one of \"slate-steel\", \"ocean\"; using the default."),
+            Map.entry("terminal.scheme", "\"nord/dark\" is not an installed scheme. Use \"match\" or one of \"slate-steel/dark\", "
+                + "\"slate-steel/light\", \"slate-steel/high-contrast\", \"ocean/dark\", \"ocean/light\", \"ocean/high-contrast\", "
+                + "\"zenburn/dark\"; using the default."));
+        assertThat(selection.withScheme(TerminalSchemeChoice.parse("nord/dark").orElseThrow()).scheme()).isEqualTo(TerminalSchemeChoice.MATCH);
+    }
+
+    @Test void theControllerShowsEachAppearancesThemeAndSwitchesBetweenThem() throws Exception {
+        var library = library();
+        var ocean = library.theme("ocean").orElseThrow();
+        var selection = ThemeSelection.of(library, "ocean", "slate-steel", TerminalSchemeChoice.MATCH, (key, message) -> { });
+        var installs = new ArrayList<GeneratedTheme>();
+        var controller = new ThemeController(true, selection, installs::add);
+        assertThat(controller.current().chrome()).isSameAs(ocean.generated(true)).isSameAs(controller.current().workspace());
+        assertThat(controller.library()).isSameAs(library);
+        controller.configure(false, selection, dev.jasper.app.config.UiFontConfig.defaults());
+        assertThat(controller.current().chrome()).as("Match System's Light").isSameAs(JasperTheme.bundled().generated(false));
+        controller.configure(true, selection, dev.jasper.app.config.UiFontConfig.defaults());
+        assertThat(installs).containsExactly(ocean.generated(true), JasperTheme.bundled().generated(false), ocean.generated(true));
+    }
+
+    @Test void aRefreshedLibraryNotifiesSubscribersWithoutReinstallingAnUnchangedTheme() throws Exception {
+        var library = library();
+        var selection = ThemeSelection.of(library, "ocean", "slate-steel", TerminalSchemeChoice.MATCH, (key, message) -> { });
+        var installs = new ArrayList<GeneratedTheme>();
+        var controller = new ThemeController(true, selection, installs::add);
+        var chromeChanges = new ArrayList<Boolean>();
+        controller.subscribe((theme, chrome) -> chromeChanges.add(chrome));
+        chromeChanges.clear();
+        Files.writeString(home.resolve("themes/nord.json"), ThemeLibraryTest.terminalTheme("Nord"));
+        var refreshed = ThemeLibrary.read(home.resolve("themes"), library);
+        controller.configure(true, ThemeSelection.of(refreshed, "ocean", "slate-steel", TerminalSchemeChoice.MATCH, (key, message) -> { }),
+            dev.jasper.app.config.UiFontConfig.defaults());
+        assertThat(chromeChanges).containsExactly(false);
+        assertThat(installs).hasSize(1);
+        assertThat(controller.library()).isSameAs(refreshed);
+    }
+}
diff --git a/jasper-app/src/test/java/dev/jasper/app/application/ConfigurationTestSupport.java b/jasper-app/src/test/java/dev/jasper/app/application/ConfigurationTestSupport.java
index 2b8bf1aa50f5fabdd588ff457d6e323da161cc62..e20b26ee5526f8ce6aa68a4dd430bf8e2ea58862 100644
--- a/jasper-app/src/test/java/dev/jasper/app/application/ConfigurationTestSupport.java
+++ b/jasper-app/src/test/java/dev/jasper/app/application/ConfigurationTestSupport.java
@@ -15,6 +15,11 @@ public final class ConfigurationTestSupport {
     public ConfigurationTestSupport(ThemeController themes, ConfigService service, Consumer<Path> editor) {
         controller = new ConfigurationController(themes, service, editor);
     }
+    /** With imported themes in {@code themesFolder}. */
+    public ConfigurationTestSupport(ThemeController themes, ConfigService service, Path themesFolder) {
+        controller = new ConfigurationController(themes, service, path -> { },
+            dev.jasper.app.platform.SystemAppearance.Observation.unavailable("No system appearance in tests."), themesFolder);
+    }
     public ConfigService.State shown() { return controller.shown(); }
     public void register(WindowContent owner) { controller.register(owner); }
     public void unregister(WindowContent owner) { controller.unregister(owner); }
diff --git a/jasper-app/src/test/java/dev/jasper/app/workspace/ConfigurationControllerTest.java b/jasper-app/src/test/java/dev/jasper/app/workspace/ConfigurationControllerTest.java
index 001a656b9f96c5f8348a659a421e3a99cbf55f26..89201bcee2f0ba9d2c9777b627196cc6af25d374 100644
--- a/jasper-app/src/test/java/dev/jasper/app/workspace/ConfigurationControllerTest.java
+++ b/jasper-app/src/test/java/dev/jasper/app/workspace/ConfigurationControllerTest.java
@@ -59,6 +59,18 @@ class ConfigurationControllerTest {
         service = new ConfigService(directory.resolve("config.toml"), false);
         edt(() -> { themes = new ThemeController(true, TerminalSchemeChoice.MATCH); controller = new ConfigurationTestSupport(themes, service); });
     }
+    /** As {@link #start}, with imported themes read from {@code themes/} next to config.toml. */
+    void startWithThemes(String text) throws Exception {
+        Files.writeString(directory.resolve("config.toml"), text);
+        service = new ConfigService(directory.resolve("config.toml"), false);
+        var folder = directory.resolve("themes");
+        edt(() -> {
+            var library = dev.jasper.app.appearance.ThemeLibrary.read(folder, dev.jasper.app.appearance.ThemeLibrary.bundledOnly());
+            themes = new ThemeController(true, dev.jasper.app.appearance.ThemeSelection.of(library, "slate-steel", "slate-steel",
+                TerminalSchemeChoice.MATCH, (key, message) -> { }));
+            controller = new ConfigurationTestSupport(themes, service, folder);
+        });
+    }
     WindowContent owner() { return owner(launcher(pending)); }
     WindowContent owner(ShellLauncher launcher) {
         var result = new WindowContent(launcher, HOME, path -> {}, () -> {}, () -> {}, themes);
@@ -665,6 +677,36 @@ class ConfigurationControllerTest {
         });
     }
 
+    @Test void aSavedImportedThemeShowsAndAnUnknownOneIsReported() throws Exception {
+        var folder = Files.createDirectories(directory.resolve("themes"));
+        Files.writeString(folder.resolve("ocean.json"), ThemeTestSupport.uiThemeJson("Ocean"));
+        startWithThemes("[ui]\nappearance='dark'\n[ui.theme]\ndark='ocean'\n");
+        edt(() -> {
+            assertThat(themes.current().chrome().name()).isEqualTo("Ocean");
+            assertThat(controller.shown().diagnostics()).noneMatch(d -> d.key().startsWith("ui.theme"));
+        });
+        reload("[ui]\nappearance='dark'\n[ui.theme]\ndark='nord'\n");
+        edt(() -> {
+            assertThat(themes.current().chrome()).isSameAs(JasperTheme.bundled().generated(true));
+            assertThat(controller.shown().diagnostics()).filteredOn(d -> d.key().equals("ui.theme.dark")).singleElement()
+                .satisfies(d -> assertThat(d.message()).startsWith("\"nord\" is not an installed theme."));
+        });
+    }
+
+    @Test void reloadReadsTheThemesFolderAgainAndReportsAFileItSkips() throws Exception {
+        startWithThemes("[ui]\nappearance='dark'\n[ui.theme]\ndark='ocean'\n");
+        edt(() -> assertThat(themes.current().chrome()).isSameAs(JasperTheme.bundled().generated(true)));
+        var folder = Files.createDirectories(directory.resolve("themes"));
+        Files.writeString(folder.resolve("ocean.json"), ThemeTestSupport.uiThemeJson("Ocean"));
+        Files.writeString(folder.resolve("broken.json"), "{ \"jasper-theme\": 1 }");
+        service.reload().get(); edt(() -> {});
+        edt(() -> {
+            assertThat(themes.current().chrome().name()).isEqualTo("Ocean");
+            assertThat(controller.shown().diagnostics()).filteredOn(d -> d.file().getFileName().toString().equals("broken.json"))
+                .singleElement().satisfies(d -> assertThat(d.formatted()).endsWith("broken.json: warning: Missing entry \"name\""));
+        });
+    }
+
     @Test void diagnosticsOfferMigrateConfigWhichRewritesTheFileAndReportsIt() throws Exception {
         start("[font]\nsize = 19\n"); edt(this::owner);
         var owner = owners.getFirst();
JASPER_PATCH
`````

- [ ] **Step 2: See them fail**

Run: `./gradlew :jasper-app:compileTestJava`
Expected: FAIL, `cannot find symbol` for `ThemeSelection` and for the five-argument `ConfigurationController`.

- [ ] **Step 3: Apply the production changes**

`````bash
git apply --whitespace=nowarn <<'JASPER_PATCH'
diff --git a/docs/design/theme-engine/migration.csv b/docs/design/theme-engine/migration.csv
index 9568c5864abdfba98a4e306c041393c51492f573..596f73595932f656c25c6fc529af23ce48dae2fe 100644
--- a/docs/design/theme-engine/migration.csv
+++ b/docs/design/theme-engine/migration.csv
@@ -1205,3 +1205,5 @@ jasper-app/src/test/java/dev/jasper/app/appearance/ThemeLibraryTest.java,app.app
 jasper-app/src/main/java/dev/jasper/app/config/TerminalSchemeChoice.java,app.config,TerminalSchemeChoice,nonvisual-helper,none,none,jasper-app/src/test/java/dev/jasper/app/config/ConfigLoaderTest.java,New in theme import Plan 1 Task 3; terminal.scheme as match or a scheme id.
 jasper-app/src/main/java/dev/jasper/app/settingsui/SchemePick.java,app.settingsui,SchemePick,nonvisual-helper,none,none,jasper-app/src/test/java/dev/jasper/app/settingsui/AppearancePageTest.java,New in theme import Plan 1 Task 3; the terminal scheme dropdown over the installed schemes.
 jasper-app/src/main/java/dev/jasper/app/settingsui/InstalledThemes.java,app.settingsui,InstalledThemes,nonvisual-helper,none,none,jasper-app/src/test/java/dev/jasper/app/settingsui/AppearancePageTest.java,New in theme import Plan 1 Task 3; what the Appearance page offers.
+jasper-app/src/main/java/dev/jasper/app/appearance/ThemeSelection.java,app.appearance,ThemeSelection,nonvisual-helper,none,none,jasper-app/src/test/java/dev/jasper/app/appearance/ThemeSelectionTest.java,New in theme import Plan 1 Task 4; the themes and scheme picked.
+jasper-app/src/test/java/dev/jasper/app/appearance/ThemeSelectionTest.java,app.appearance,ThemeSelectionTest,nonvisual-helper,none,none,jasper-app/src/test/java/dev/jasper/app/appearance/ThemeSelectionTest.java,New in theme import Plan 1 Task 4; test owner of the theme picks.
diff --git a/jasper-app/src/main/java/dev/jasper/app/appearance/ResolvedTheme.java b/jasper-app/src/main/java/dev/jasper/app/appearance/ResolvedTheme.java
index 4d14dd5e555e191f5397fd2241aae4f2ddd5c6bc..b6d7666391ea7649d62abb39c67d2847e6e1fc8b 100644
--- a/jasper-app/src/main/java/dev/jasper/app/appearance/ResolvedTheme.java
+++ b/jasper-app/src/main/java/dev/jasper/app/appearance/ResolvedTheme.java
@@ -12,7 +12,13 @@ import java.util.Objects;
 public record ResolvedTheme(GeneratedTheme chrome, GeneratedTheme workspace, Palette palette) {
     public ResolvedTheme { Objects.requireNonNull(chrome); Objects.requireNonNull(workspace); Objects.requireNonNull(palette); }
 
-    /** Slate Steel: one variant for chrome and workspace, and the terminal scheme's palette. */
+    /** The theme {@code selection} shows with the Dark or Light appearance, for chrome and workspace, and its terminal scheme's palette. */
+    public static ResolvedTheme of(boolean dark, ThemeSelection selection) {
+        var theme = selection.theme(dark).generated(dark);
+        return new ResolvedTheme(theme, theme, TerminalPaletteAdapter.toTerminal(selection.terminal(dark)));
+    }
+
+    /** Slate Steel: one variant for chrome and workspace, and {@code scheme}'s palette. */
     public static ResolvedTheme slateSteel(boolean dark, TerminalScheme scheme) {
         var theme = JasperTheme.bundled().generated(dark);
         return new ResolvedTheme(theme, theme, TerminalPaletteAdapter.toTerminal(scheme));
diff --git a/jasper-app/src/main/java/dev/jasper/app/appearance/ThemeController.java b/jasper-app/src/main/java/dev/jasper/app/appearance/ThemeController.java
index 8b2e583fcb5faffa21fe6291072a20f9928ae6b7..7d189fefddc5efd1d3b49f8c9865420bde499658 100644
--- a/jasper-app/src/main/java/dev/jasper/app/appearance/ThemeController.java
+++ b/jasper-app/src/main/java/dev/jasper/app/appearance/ThemeController.java
@@ -4,7 +4,6 @@ import dev.jasper.app.config.TerminalSchemeChoice;
 import dev.jasper.app.config.UiFontConfig;
 import dev.jasper.ui.theme.GeneratedTheme;
 import dev.jasper.ui.theme.JasperTheme;
-import dev.jasper.ui.theme.TerminalScheme;
 import java.awt.Font;
 import javax.swing.plaf.FontUIResource;
 import java.util.LinkedHashSet;
@@ -17,8 +16,8 @@ import java.util.function.Predicate;
 import javax.swing.*;
 
 /**
- * Application-owned, EDT-confined theme selection and value subscriptions. The application shows Slate Steel: one
- * variant for chrome and workspace, and the terminal scheme's palette in the terminal panes.
+ * Application-owned, EDT-confined theme selection and value subscriptions. The shown theme's variant serves chrome and
+ * workspace, and the terminal scheme's palette the terminal panes.
  */
 public final class ThemeController implements AutoCloseable {
     private AppearanceBindings appearance;
@@ -29,18 +28,16 @@ public final class ThemeController implements AutoCloseable {
 
     public static final class InstallationFailure extends IllegalStateException {
         InstallationFailure(GeneratedTheme theme, RuntimeException cause) {
-            super("Could not apply theme: " + theme.name()
-                + (theme == JasperTheme.bundled().generated(theme.dark()) ? (theme.dark() ? " Dark" : " Light") : ""), cause);
+            super("Could not apply theme: " + theme.name() + (theme.dark() ? " Dark" : " Light"), cause);
         }
     }
 
-    /** The Slate Steel selection: the UI variant and the terminal scheme chosen. */
-    private record Slate(boolean dark, TerminalSchemeChoice scheme) { }
+    /** What is shown: the appearance in effect and the selection. */
+    private record Shown(boolean dark, ThemeSelection selection) { }
 
     private final Set<BiConsumer<ResolvedTheme, Boolean>> listeners = new LinkedHashSet<>();
     private final Predicate<GeneratedTheme> installer;
-    /** The Slate Steel selection. */
-    private Slate slate;
+    private Shown shown;
     private ResolvedTheme current;
     private UiFontConfig uiFont = UiFontConfig.defaults();
     private final Font platformFont;
@@ -49,74 +46,86 @@ public final class ThemeController implements AutoCloseable {
         "PasswordField.font", "FormattedTextField.font", "TextArea.font", "ComboBox.font", "Button.font");
 
     /**
-     * Start-up selection: Slate Steel in {@code dark}, or Dark when that cannot be installed. A failed attempt is rolled
-     * back completely, and the fallback is installed before this returns, so nothing can read the installed snapshot
-     * (ThemeZone, named icons, native chrome) before one exists. The fallback's own failure propagates.
+     * Start-up selection: {@code selection} in {@code dark}, or Slate Steel Dark when that cannot be installed. A failed
+     * attempt is rolled back completely, and the fallback is installed before this returns, so nothing can read the
+     * installed snapshot (ThemeZone, named icons, native chrome) before one exists. The fallback's own failure propagates.
      */
+    public static ThemeController startup(boolean dark, ThemeSelection selection, java.util.function.Consumer<InstallationFailure> fallingBack) {
+        return startup(dark, selection, ThemeManager::install, fallingBack);
+    }
+    /** As {@link #startup(boolean, ThemeSelection, java.util.function.Consumer)} for Slate Steel with {@code scheme}. */
     public static ThemeController startup(boolean dark, TerminalSchemeChoice scheme, java.util.function.Consumer<InstallationFailure> fallingBack) {
-        return startup(dark, scheme, ThemeManager::install, fallingBack);
+        return startup(dark, ThemeSelection.slateSteel(scheme), fallingBack);
     }
     static ThemeController startup(boolean dark, TerminalSchemeChoice scheme, Predicate<GeneratedTheme> installer,
                                    java.util.function.Consumer<InstallationFailure> fallingBack) {
-        try { return new ThemeController(dark, scheme, installer); }
+        return startup(dark, ThemeSelection.slateSteel(scheme), installer, fallingBack);
+    }
+    static ThemeController startup(boolean dark, ThemeSelection selection, Predicate<GeneratedTheme> installer,
+                                   java.util.function.Consumer<InstallationFailure> fallingBack) {
+        try { return new ThemeController(new Shown(dark, selection), installer); }
         catch (InstallationFailure failure) {
             fallingBack.accept(failure);
-            return new ThemeController(true, scheme, installer);
+            var steel = JasperTheme.bundled();
+            return new ThemeController(new Shown(true, new ThemeSelection(selection.library(), steel, steel, selection.scheme())), installer);
         }
     }
 
     /** Slate Steel in {@code dark} with {@code scheme}. */
     public ThemeController(boolean dark, TerminalSchemeChoice scheme) { this(dark, scheme, ThemeManager::install); }
     ThemeController(boolean dark, TerminalSchemeChoice scheme, Predicate<GeneratedTheme> installer) {
-        this(new Slate(dark, Objects.requireNonNull(scheme)), installer);
+        this(new Shown(dark, ThemeSelection.slateSteel(Objects.requireNonNull(scheme))), installer);
     }
-    private ThemeController(Slate slate, Predicate<GeneratedTheme> installer) {
+    /** {@code selection} in {@code dark}. */
+    public ThemeController(boolean dark, ThemeSelection selection) { this(new Shown(dark, Objects.requireNonNull(selection)), ThemeManager::install); }
+    ThemeController(boolean dark, ThemeSelection selection, Predicate<GeneratedTheme> installer) {
+        this(new Shown(dark, Objects.requireNonNull(selection)), installer);
+    }
+    private ThemeController(Shown shown, Predicate<GeneratedTheme> installer) {
         requireEdt();
         this.installer = Objects.requireNonNull(installer);
-        this.slate = slate;
+        this.shown = shown;
         // Swing otherwise tries the component's plugin loader for third-party LAF delegates.
         // Only UI delegate lookup belongs to the app; plugin class visibility stays isolated.
         UIManager.put("ClassLoader", ThemeController.class.getClassLoader());
         FORM_FONTS.forEach(key -> UIManager.put(key, null));
         UIManager.put("defaultFont", null);
         UIManager.put("Jasper.uiFontFamilyOverride", false);
-        current = resolve(slate);
+        current = ResolvedTheme.of(shown.dark(), shown.selection());
         appearance = new AppearanceBindings(current.pair(), ThemeController::reportFailure, () -> animationsEnabled);
         new dev.jasper.ui.swing.AppearanceTransaction(appearance.binding(), ignored -> installOrThrow(current.chrome()), ignored -> {}).apply(current.pair());
         platformFont = UIManager.getFont("defaultFont") != null ? UIManager.getFont("defaultFont") : UIManager.getFont("Label.font");
         platformLabelSize = UIManager.getFont("Label.font").getSize2D();
     }
 
-    /** The bundled scheme for a UI variant: the choice's, or with {@code MATCH} (or a scheme Slate Steel lacks) the variant's own. */
-    public static TerminalScheme scheme(boolean dark, TerminalSchemeChoice choice) {
-        var bundled = JasperTheme.bundled();
-        return choice.scheme().filter(id -> id.theme().equals(bundled.id())).flatMap(id -> bundled.scheme(id.key()))
-            .orElseGet(() -> bundled.matching(dark));
-    }
-    private static ResolvedTheme resolve(Slate slate) {
-        return ResolvedTheme.slateSteel(slate.dark(), scheme(slate.dark(), slate.scheme()));
-    }
-
     public ResolvedTheme current() { requireEdt(); return current; }
     /** The terminal scheme chosen. */
-    public TerminalSchemeChoice schemeChoice() { requireEdt(); return slate.scheme(); }
-
-    /** Slate Steel in {@code dark} with {@code scheme} and {@code font}. */
-    public void configure(boolean dark, TerminalSchemeChoice scheme, UiFontConfig font) {
+    public TerminalSchemeChoice schemeChoice() { requireEdt(); return shown.selection().scheme(); }
+    /** The themes picked and the library they come from. */
+    public ThemeSelection selection() { requireEdt(); return shown.selection(); }
+    /** The installed themes the selection comes from. */
+    public ThemeLibrary library() { requireEdt(); return shown.selection().library(); }
+
+    /** {@code selection} in {@code dark} with {@code font}. */
+    public void configure(boolean dark, ThemeSelection selection, UiFontConfig font) {
         requireEdt();
-        var next = new Slate(dark, Objects.requireNonNull(scheme));
-        apply(next, resolve(next), Objects.requireNonNull(font));
+        var next = new Shown(dark, Objects.requireNonNull(selection));
+        apply(next, ResolvedTheme.of(dark, selection), Objects.requireNonNull(font));
+    }
+    /** The current themes in {@code dark} with {@code scheme} and {@code font}. */
+    public void configure(boolean dark, TerminalSchemeChoice scheme, UiFontConfig font) {
+        requireEdt(); configure(dark, shown.selection().withScheme(Objects.requireNonNull(scheme)), font);
     }
     public void configure(boolean dark, TerminalSchemeChoice scheme) { requireEdt(); configure(dark, scheme, uiFont); }
-    /** Changes only the terminal scheme; the UI variant and font stay. */
+    /** Changes only the terminal scheme; the appearance, themes and font stay. */
     public void configureScheme(TerminalSchemeChoice scheme) {
-        requireEdt(); configure(slate.dark(), scheme, uiFont);
+        requireEdt(); configure(shown.dark(), scheme, uiFont);
     }
 
-    private void apply(Slate nextSlate, ResolvedTheme next, UiFontConfig font) {
+    private void apply(Shown nextShown, ResolvedTheme next, UiFontConfig font) {
         ResolvedTheme previous = current;
         boolean chromeChanged = previous.chrome() != next.chrome() || !uiFont.equals(font);
-        boolean selectionChanged = !Objects.equals(slate, nextSlate);
+        boolean selectionChanged = !Objects.equals(shown, nextShown);
         if (applying) throw new IllegalStateException("Theme configuration cannot re-enter");
         applying = true;
         try {
@@ -127,7 +136,7 @@ public final class ThemeController implements AutoCloseable {
                 }
                 UIManager.put("Jasper.uiFontFamilyOverride", !font.family().equalsIgnoreCase("system"));
                 uiFont = font;
-                slate = nextSlate;
+                shown = nextShown;
                 current = next;
             }, ignored -> {}).apply(next.pair());
             if (chromeChanged || !previous.equals(next) || selectionChanged) {
diff --git a/jasper-app/src/main/java/dev/jasper/app/appearance/ThemeSelection.java b/jasper-app/src/main/java/dev/jasper/app/appearance/ThemeSelection.java
new file mode 100644
index 0000000000000000000000000000000000000000..a3ee786a96ad2ab3947db634d0ebc9b115f95ce6
--- /dev/null
+++ b/jasper-app/src/main/java/dev/jasper/app/appearance/ThemeSelection.java
@@ -0,0 +1,71 @@
+package dev.jasper.app.appearance;
+
+import dev.jasper.app.config.TerminalSchemeChoice;
+import dev.jasper.ui.theme.JasperTheme;
+import dev.jasper.ui.theme.TerminalScheme;
+import java.util.Objects;
+import java.util.function.BiConsumer;
+import java.util.stream.Collectors;
+
+/**
+ * The themes shown with the Dark and Light appearances and the terminal scheme, all from {@code library}. Every pick
+ * is one the library can honour: {@link #of} replaces any other by its default and reports it. Immutable; a selection
+ * from a refreshed library is a different selection, even when its picks are the same.
+ */
+public record ThemeSelection(ThemeLibrary library, JasperTheme dark, JasperTheme light, TerminalSchemeChoice scheme) {
+    public ThemeSelection {
+        Objects.requireNonNull(library, "library"); Objects.requireNonNull(dark, "dark");
+        Objects.requireNonNull(light, "light"); Objects.requireNonNull(scheme, "scheme");
+        if (!dark.has(true)) throw new IllegalArgumentException(dark.name() + " has no Dark variant");
+        if (!light.has(false)) throw new IllegalArgumentException(light.name() + " has no Light variant");
+        if (scheme.scheme().isPresent() && library.scheme(scheme.scheme().get()).isEmpty())
+            throw new IllegalArgumentException("Not an installed scheme: " + scheme.key());
+    }
+
+    /** Slate Steel for both appearances, from the built-in library; a scheme Slate Steel lacks becomes match. */
+    public static ThemeSelection slateSteel(TerminalSchemeChoice scheme) {
+        return of(ThemeLibrary.bundledOnly(), JasperTheme.BUNDLED_ID, JasperTheme.BUNDLED_ID, scheme, (key, message) -> { });
+    }
+
+    /**
+     * The picks {@code dark}, {@code light} and {@code scheme} from {@code library}. A pick the library cannot honour (a
+     * theme it does not have, a theme without that variant, a scheme that is not installed) is reported to
+     * {@code problems} as its setting's key and a message, and replaced by its default.
+     */
+    public static ThemeSelection of(ThemeLibrary library, String dark, String light, TerminalSchemeChoice scheme,
+                                    BiConsumer<String, String> problems) {
+        var darkTheme = pick(library, dark, true, problems);
+        var lightTheme = pick(library, light, false, problems);
+        var choice = scheme;
+        if (scheme.scheme().isPresent() && library.scheme(scheme.scheme().get()).isEmpty()) {
+            problems.accept("terminal.scheme", "\"" + scheme.key() + "\" is not an installed scheme. Use \"match\" or one of "
+                + library.schemes().stream().map(found -> "\"" + found.id() + "\"").collect(Collectors.joining(", ")) + "; using the default.");
+            choice = TerminalSchemeChoice.MATCH;
+        }
+        return new ThemeSelection(library, darkTheme, lightTheme, choice);
+    }
+
+    private static JasperTheme pick(ThemeLibrary library, String id, boolean dark, BiConsumer<String, String> problems) {
+        var theme = library.theme(id);
+        if (theme.isPresent() && theme.get().has(dark)) return theme.get();
+        String reason = theme.isPresent() ? "\"" + id + "\" has no " + (dark ? "Dark" : "Light") + " variant."
+            : "\"" + id + "\" is not an installed theme.";
+        problems.accept(dark ? "ui.theme.dark" : "ui.theme.light", reason + " Use one of "
+            + library.themes(dark).stream().map(found -> "\"" + found.id() + "\"").collect(Collectors.joining(", ")) + "; using the default.");
+        return JasperTheme.bundled();
+    }
+
+    /** The theme shown with the Dark or Light appearance. */
+    public JasperTheme theme(boolean dark) { return dark ? this.dark : light; }
+
+    /** The terminal scheme shown with the Dark or Light appearance: the chosen one, or with match the shown theme's own. */
+    public TerminalScheme terminal(boolean dark) {
+        return scheme.scheme().flatMap(library::scheme).orElseGet(() -> theme(dark).matching(dark));
+    }
+
+    /** This selection with another scheme; one the library does not have becomes match. */
+    public ThemeSelection withScheme(TerminalSchemeChoice choice) {
+        boolean installed = choice.scheme().map(id -> library.scheme(id).isPresent()).orElse(true);
+        return new ThemeSelection(library, dark, light, installed ? choice : TerminalSchemeChoice.MATCH);
+    }
+}
diff --git a/jasper-app/src/main/java/dev/jasper/app/application/ConfigurationController.java b/jasper-app/src/main/java/dev/jasper/app/application/ConfigurationController.java
index 0f8999dc023fbbb78a149d3cf77d26ee18e404cf..bbfd8b36a45eb7ffee1adc9575ebe00635e26c46 100644
--- a/jasper-app/src/main/java/dev/jasper/app/application/ConfigurationController.java
+++ b/jasper-app/src/main/java/dev/jasper/app/application/ConfigurationController.java
@@ -5,6 +5,8 @@ import java.util.Map;
 import java.util.Optional;
 
 import dev.jasper.app.appearance.ThemeController;
+import dev.jasper.app.appearance.ThemeLibrary;
+import dev.jasper.app.appearance.ThemeSelection;
 import dev.jasper.app.config.ConfigDiagnostic;
 import dev.jasper.app.config.ConfigService;
 import dev.jasper.app.config.ConfigSnapshot;
@@ -35,8 +37,12 @@ final class ConfigurationController implements AutoCloseable {
     private dev.jasper.app.platform.SystemAppearance.Observation systemAppearance;
     private Consumer<ConfigSnapshot> applicationListener = snapshot -> {};
     private final List<ConfigDiagnostic> reported = new java.util.ArrayList<>();
-    /** A Slate Steel variant that could not be installed, from the last state applied. */
+    /** From the last state applied: theme files the library skipped, theme picks it cannot honour, a theme that could not be installed. */
     private List<ConfigDiagnostic> themeProblems = List.of();
+    /** The themes folder, or null for the built-in theme alone. */
+    private final Path themesFolder;
+    /** Read again on the configuration worker before each forced or changed publication; used on the EDT. */
+    private volatile ThemeLibrary library;
     private Predicate<String> pluginSettingsExist = id -> false;
     private final List<Consumer<ConfigService.State>> stateListeners = new java.util.ArrayList<>();
     private Consumer<ConfigService.State> problemListener = state -> {};
@@ -90,9 +96,20 @@ final class ConfigurationController implements AutoCloseable {
     }
     ConfigurationController(ThemeController themes, ConfigService service, Consumer<Path> editor,
                             dev.jasper.app.platform.SystemAppearance.Observation initialAppearance) {
+        this(themes, service, editor, initialAppearance, null);
+    }
+    /**
+     * {@code themesFolder} holds the imported themes; null shows the built-in theme alone. The folder is read again
+     * whenever the configuration is (Reload config, Settings, a change to config.toml), before that state is applied.
+     */
+    ConfigurationController(ThemeController themes, ConfigService service, Consumer<Path> editor,
+                            dev.jasper.app.platform.SystemAppearance.Observation initialAppearance, Path themesFolder) {
         this.systemAppearance = Objects.requireNonNull(initialAppearance);
         requireEdt();
         this.themes = themes; this.service = service; this.editor = editor;
+        this.themesFolder = themesFolder;
+        library = themes.library();
+        if (themesFolder != null) service.watchWith(() -> library = ThemeLibrary.read(themesFolder, library));
         state = service.initialState();
         try { configureThemes(state); }
         catch (ThemeController.InstallationFailure ignored) {
@@ -249,19 +266,27 @@ final class ConfigurationController implements AutoCloseable {
         problemListener.accept(visible);
     }
 
-    /** Applies the saved appearance, terminal scheme and interface font. */
+    /** Applies the saved appearance, themes, terminal scheme and interface font from the current library. */
     private void configureThemes(ConfigService.State next) {
         var snapshot = next.snapshot();
         boolean dark = AppearancePolicy.dark(snapshot.appearance(), systemAppearance);
         themes.configureAnimations(snapshot.animations().enabled());
+        var problems = new java.util.ArrayList<ConfigDiagnostic>();
+        var installed = library;
+        for (var problem : installed.problems())
+            problems.add(new ConfigDiagnostic(ConfigDiagnostic.Severity.WARNING, problem.file(), problem.line(), problem.line() == 0 ? 0 : 1,
+                "", problem.message()));
+        var selection = ThemeSelection.of(installed, snapshot.darkTheme(), snapshot.lightTheme(), snapshot.terminalScheme(),
+            (key, message) -> problems.add(new ConfigDiagnostic(ConfigDiagnostic.Severity.WARNING, next.file(), 0, 0, key, message)));
         try {
-            themes.configure(dark, snapshot.terminalScheme(), snapshot.uiFont());
-            themeProblems = List.of();
+            themes.configure(dark, selection, snapshot.uiFont());
+            themeProblems = List.copyOf(problems);
         } catch (ThemeController.InstallationFailure failure) {
             Throwable cause = failure.getCause();
-            themeProblems = List.of(new ConfigDiagnostic(ConfigDiagnostic.Severity.WARNING, next.file(), 0, 0, "ui.appearance",
-                "Could not apply Slate Steel " + (dark ? "Dark" : "Light") + ": " + (cause != null ? cause.getMessage() : failure.getMessage())
-                    + "; keeping the current appearance."));
+            problems.add(new ConfigDiagnostic(ConfigDiagnostic.Severity.WARNING, next.file(), 0, 0, "ui.appearance",
+                "Could not apply " + selection.theme(dark).name() + (dark ? " Dark" : " Light") + ": "
+                    + (cause != null ? cause.getMessage() : failure.getMessage()) + "; keeping the current appearance."));
+            themeProblems = List.copyOf(problems);
             throw failure;
         }
     }
diff --git a/jasper-app/src/main/java/dev/jasper/app/application/JasperApplication.java b/jasper-app/src/main/java/dev/jasper/app/application/JasperApplication.java
index 521326bd1b3be3eb3e8169fe35290a1cf4606b10..b82549bc45d74dd08b6e2f7a6b5a64e34964bba4 100644
--- a/jasper-app/src/main/java/dev/jasper/app/application/JasperApplication.java
+++ b/jasper-app/src/main/java/dev/jasper/app/application/JasperApplication.java
@@ -213,6 +213,12 @@ public final class JasperApplication {
      */
     public JasperApplication(ConfigService service, ShellLauncher suppliedLauncher, CommandHistory history, Path buddyStateFile,
                       Runnable terminate, Path shellIntegrationDir) {
+        this(service, suppliedLauncher, history, buddyStateFile, terminate, shellIntegrationDir, null);
+    }
+
+    /** As above; {@code themesFolder} holds the imported themes, or is null for the built-in theme alone. */
+    public JasperApplication(ConfigService service, ShellLauncher suppliedLauncher, CommandHistory history, Path buddyStateFile,
+                      Runnable terminate, Path shellIntegrationDir, Path themesFolder) {
         dev.jasper.app.platform.BundledFonts.register();
         ConfigSnapshot startup = service == null ? ConfigSnapshot.defaults() : service.initialState().snapshot();
         startupViewMode = startup.viewMode();
@@ -221,7 +227,12 @@ public final class JasperApplication {
         boolean dark = AppearancePolicy.dark(startup.appearance(), systemAppearance.current());
         // First Swing work: every later component may read the installed snapshot. A variant that cannot be installed
         // never stops start-up; the configuration reports it.
-        themes = ThemeController.startup(dark, startup.terminalScheme(),
+        var library = themesFolder == null ? dev.jasper.app.appearance.ThemeLibrary.bundledOnly()
+            : dev.jasper.app.appearance.ThemeLibrary.read(themesFolder, dev.jasper.app.appearance.ThemeLibrary.bundledOnly());
+        // The configuration reports unusable picks; start-up only replaces them.
+        var selection = dev.jasper.app.appearance.ThemeSelection.of(library, startup.darkTheme(), startup.lightTheme(),
+            startup.terminalScheme(), (key, message) -> { });
+        themes = ThemeController.startup(dark, selection,
             failure -> LOG.log(System.Logger.Level.WARNING, "Starting in Slate Steel Dark: " + failure.getMessage(), failure));
         // App-native menus show each item's action binding from the live keymap.
         themes.appearance().shortcuts(this::shortcutLabel);
@@ -244,7 +255,8 @@ public final class JasperApplication {
         buddyAppearance = themes.subscribe((theme, changed) -> buddy.appearance(SystemFonts.system(java.awt.Font.PLAIN, 13f), theme.chrome().dark()));
         notifications = new CommandNotifier(() -> java.time.Duration.ofSeconds(configuredLongCommandSeconds()),
             buddy.companion(), notificationCenter, buddy.companion()::setWorking, JasperApplication::afterDelay);
-        configuration = service == null ? null : new ConfigurationController(themes, service, new dev.jasper.app.platform.ConfigEditor()::open, systemAppearance.current());
+        configuration = service == null ? null : new ConfigurationController(themes, service, new dev.jasper.app.platform.ConfigEditor()::open,
+            systemAppearance.current(), themesFolder);
         if (configuration != null) systemAppearanceListening = systemAppearance.subscribe(configuration::systemAppearanceChanged);
         Supplier<TerminalConfig.Shell> savedShell = () -> (configuration == null ? ConfigSnapshot.defaults() : configuration.snapshot()).terminal().shell();
         shellProfiles = ShellProfileCatalog.system(savedShell);
diff --git a/jasper-app/src/main/java/dev/jasper/app/bootstrap/ApplicationBootstrap.java b/jasper-app/src/main/java/dev/jasper/app/bootstrap/ApplicationBootstrap.java
index 9a6a8abd0603641e3f9294cac343203ab422663f..67adfad0342f2fb470136f3ce284763335a90d82 100644
--- a/jasper-app/src/main/java/dev/jasper/app/bootstrap/ApplicationBootstrap.java
+++ b/jasper-app/src/main/java/dev/jasper/app/bootstrap/ApplicationBootstrap.java
@@ -159,7 +159,7 @@ public final class ApplicationBootstrap {
                     "Shell integration scripts could not be installed; integration is off", failure);
             }
             var application = new JasperApplication(service, null, history, dirs.buddyState(),
-                () -> System.exit(0), integrationDir);
+                () -> System.exit(0), integrationDir, dirs.themes());
             Path source = HandoffSocket.codeSource();
             application.residentControl(new ResidentControl(() -> HandoffSocket.live(dirs.daemonSocket()),
                 () -> HandoffSocket.retire(dirs.daemonSocket(), dirs.daemonToken(), source, HandoffSocket.lastModified(source))),
JASPER_PATCH
`````

- [ ] **Step 4: Run the tests**

```bash
./gradlew :jasper-app:test --tests "*ThemeSelectionTest" --tests "*SlateSteelController*" --tests "*ThemeLibraryTest" \
  --tests "*ConfigurationControllerTest" --tests "*ThemeControllerTest" --tests "*JasperApplicationThemesTest" \
  --tests "*SystemThemeApplicationTest" --tests "*ThemeMigrationCoverageTest"
```

Expected: PASS (ThemeSelectionTest: 4 tests; ConfigurationControllerTest includes
`aSavedImportedThemeShowsAndAnUnknownOneIsReported` and `reloadReadsTheThemesFolderAgainAndReportsAFileItSkips`).

- [ ] **Step 5: Commit**

```bash
git add -A
git commit -m "Show any installed theme: the selection, the controller and the themes folder at start-up and reload

🤖 Generated with Claude Code at The Home Depot

Co-Authored-By: Claude <noreply@anthropic.com>"
```

### Task 5: The Dark theme and Light theme pickers

**What changes:**
- `AppearanceSelection` (the Appearance card) shows the tiles, each previewing its pick and captioned with its label,
  then a **Dark theme** row (dropdown and Reset) and a **Light theme** row; the picks are staged as companions of
  `ui.appearance` and an untouched pick stages what the file already holds; Reset returns all three to their
  defaults; a pick that is not installed shows "<id> (not installed)" and previews Slate Steel.
- `AppearanceChoices` drops the two fixed previews (`InstalledThemes` carries them); `SettingsAppearance.choices` reads
  the controller's live library (`themes::library`), rebuilding `InstalledThemes` only when the library changes.

**Files:** modify `settingsui/{AppearanceSelection,AppearanceChoices,Editors}.java`, `application/{SettingsAppearance,JasperApplication}.java`;
tests `AppearanceFixtures`, `AppearanceSelectionTest`, `AppearancePageTest`.

**Interfaces:**
- Consumes: Task 3's `InstalledThemes`, `UI_THEME_DARK`, `UI_THEME_LIGHT`; Task 4's `ThemeController.library()`.
- Produces: `AppearanceChoices(float opacity, boolean blur, Supplier<Optional<Boolean>> systemDark, Function<Runnable, Subscription> onChanged, Supplier<InstalledThemes> themes)`
  (`of(Option.Palette dark, Option.Palette light)` unchanged); `AppearanceSelection(AppearanceChoices, SettingsDraft)`;
  `SettingsAppearance.choices(SystemAppearance, Supplier<ThemeLibrary>)`; node ids `ui.appearance:dark-theme`,
  `ui.appearance:light-theme`, rows `row:ui.appearance`, `row:ui.appearance:light`.

- [ ] **Step 1: Apply the test changes**

`````bash
git apply --whitespace=nowarn <<'JASPER_PATCH'
diff --git a/jasper-app/src/test/java/dev/jasper/app/settingsui/AppearanceFixtures.java b/jasper-app/src/test/java/dev/jasper/app/settingsui/AppearanceFixtures.java
index 9c703bb51503eb10a7ef6188121625bce0aea7a3..a805c2cb7dfaba3f18a76c3c3188a3073d0af06b 100644
--- a/jasper-app/src/test/java/dev/jasper/app/settingsui/AppearanceFixtures.java
+++ b/jasper-app/src/test/java/dev/jasper/app/settingsui/AppearanceFixtures.java
@@ -15,7 +15,7 @@ public final class AppearanceFixtures {
 
     public static AppearanceChoices choices(Supplier<Optional<Boolean>> systemDark) {
         var themes = InstalledThemes.slateSteel(preview(true), preview(false));
-        return new AppearanceChoices(preview(true), preview(false), 100f, false, systemDark, ignored -> new Subscription(() -> { }), () -> themes);
+        return new AppearanceChoices(100f, false, systemDark, ignored -> new Subscription(() -> { }), () -> themes);
     }
 
     /** A Slate Steel variant's preview, as the application builds it. */
diff --git a/jasper-app/src/test/java/dev/jasper/app/settingsui/AppearancePageTest.java b/jasper-app/src/test/java/dev/jasper/app/settingsui/AppearancePageTest.java
index 6eb1c2ed2a86eabbd995ecade9d53b64dab46aae..515acda9b192c30a4a9197cad1dbf77e2745fd4c 100644
--- a/jasper-app/src/test/java/dev/jasper/app/settingsui/AppearancePageTest.java
+++ b/jasper-app/src/test/java/dev/jasper/app/settingsui/AppearancePageTest.java
@@ -102,6 +102,20 @@ class AppearancePageTest {
         assertThat(page.modified()).isTrue();
     }
 
+    @Test void pickingADarkThemeStagesItForApplyAndResetReturnsToSlateSteel() {
+        page.component();
+        var box = kit(page, "ui.appearance:dark-theme.input", JComboBox.class);
+        assertThat(items(box)).containsExactly("Slate Steel");
+        assertThat(selected(box)).isEqualTo("Slate Steel");
+        draft.rebase(ConfigLoader.parse(Path.of("config.toml"), "[ui.theme]\ndark = 'ocean'\n", true).values());
+        page.refresh();
+        assertThat(items(kit(page, "ui.appearance:dark-theme.input", JComboBox.class))).containsExactly("Slate Steel", "ocean (not installed)");
+        pick(kit(page, "ui.appearance:dark-theme.input", JComboBox.class), "Slate Steel");
+        assertThat(draft.value(CoreSettings.UI_THEME_DARK)).isEqualTo("slate-steel");
+        assertThat(draft.edit().changes()).containsEntry(CoreSettings.UI_THEME_DARK, Optional.empty());
+        assertThat(page.modified()).isTrue();
+    }
+
     @Test void theTerminalSchemeIsADropdownOfTheFourSchemes() {
         var box = combo("terminal.scheme");
         assertThat(items(box)).containsExactly("Same as UI theme", "Slate Steel Dark", "Slate Steel Light", "High Contrast");
diff --git a/jasper-app/src/test/java/dev/jasper/app/settingsui/AppearanceSelectionTest.java b/jasper-app/src/test/java/dev/jasper/app/settingsui/AppearanceSelectionTest.java
index a11decb0c3bd0554b640535fd03cbc7cfa36afd2..d2b35d3ae0988e9d6755ece5983f22c08156632b 100644
--- a/jasper-app/src/test/java/dev/jasper/app/settingsui/AppearanceSelectionTest.java
+++ b/jasper-app/src/test/java/dev/jasper/app/settingsui/AppearanceSelectionTest.java
@@ -3,50 +3,93 @@ package dev.jasper.app.settingsui;
 import static org.assertj.core.api.Assertions.*;
 
 import dev.jasper.app.config.AppearanceMode;
+import dev.jasper.app.config.ConfigLoader;
 import dev.jasper.app.config.CoreSettings;
 import dev.jasper.ui.model.*;
+import java.nio.file.Path;
 import java.util.List;
 import java.util.Optional;
 import org.junit.jupiter.api.Test;
 
-/** spec 6: the tiles write ui.appearance; the Theme row's one entry is Slate Steel, with no gear. */
+/** spec 4.1: the tiles write ui.appearance and preview each appearance's pick; the Dark and Light theme rows list the installed themes. */
 class AppearanceSelectionTest {
-    static final Option.Palette DARK = new Option.Palette(1, 2, 3, 4, 5), LIGHT = new Option.Palette(6, 7, 8, 9, 10);
+    static final Option.Palette DARK = new Option.Palette(1, 2, 3, 4, 5), LIGHT = new Option.Palette(6, 7, 8, 9, 10),
+        OCEAN = new Option.Palette(11, 12, 13, 14, 15);
+    /** Slate Steel, and Ocean with a Dark variant only. */
+    static final InstalledThemes THEMES = new InstalledThemes(
+        List.of(new InstalledThemes.ThemeOption("slate-steel", "Slate Steel", DARK), new InstalledThemes.ThemeOption("ocean", "Ocean", OCEAN)),
+        List.of(new InstalledThemes.ThemeOption("slate-steel", "Slate Steel", LIGHT)),
+        InstalledThemes.slateSteel(DARK, LIGHT).schemes());
 
+    static SettingsDraft draft(String config) { return new SettingsDraft(ConfigLoader.parse(Path.of("config.toml"), config, true).values()); }
+    static AppearanceSelection editor(SettingsDraft draft) {
+        return new AppearanceSelection(new AppearanceChoices(100f, false, () -> Optional.of(false),
+            ignored -> new dev.jasper.app.lifecycle.Subscription(() -> {}), () -> THEMES), draft);
+    }
     static List<Nodes.Node> parts(AppearanceSelection editor) {
         return ((Nodes.Stack) editor.node("ui.appearance", ControlState.READY)).children();
     }
+    static Nodes.Choice pick(AppearanceSelection editor, int row) {
+        return (Nodes.Choice) ((Nodes.Stack) ((Nodes.SettingRow) parts(editor).get(row)).control()).children().get(0);
+    }
 
-    @Test void theTilesPreviewBothVariantsAndTheThemeRowOffersOnlySlateSteel() {
-        var editor = new AppearanceSelection(new AppearanceChoices(DARK, LIGHT, 100f, false, () -> Optional.of(false), ignored -> new dev.jasper.app.lifecycle.Subscription(() -> {}),
-            () -> InstalledThemes.slateSteel(DARK, LIGHT)));
+    @Test void theTilesPreviewEachAppearancesPickAndTheRowsListTheInstalledThemes() {
+        var editor = editor(draft(""));
         editor.show(AppearanceMode.SYSTEM);
         var tiles = (Nodes.Choice) parts(editor).get(0);
         assertThat(tiles.id()).isEqualTo("ui.appearance:policy");
         assertThat(tiles.options()).extracting(Option::id).containsExactly("dark", "light", "system");
         assertThat(tiles.options()).extracting(Option::label).containsExactly("Dark", "Light", "Match System");
         assertThat(tiles.selectedId()).isEqualTo("system");
+        assertThat(tiles.options().get(0).description()).isEqualTo("Slate Steel");
         assertThat(tiles.options().get(2).preview()).contains(new Option.Preview(DARK, Optional.of(LIGHT)));
         assertThat(tiles.options().get(2).description()).isEqualTo("System appearance: Light");
-        var row = (Nodes.SettingRow) parts(editor).get(1);
-        assertThat(row.title()).isEqualTo("Theme");
-        var controls = (Nodes.Stack) row.control();
-        var theme = (Nodes.Choice) controls.children().get(0);
-        assertThat(theme.kind()).isEqualTo(Nodes.ChoiceKind.DROPDOWN);
-        assertThat(theme.options()).extracting(Option::label).containsExactly("Slate Steel");
-        assertThat(controls.children()).noneMatch(node -> node instanceof Nodes.MenuButton).as("no gear");
-        assertThat(((Nodes.Button) controls.children().get(1)).state().visible()).as("Reset hidden at the default").isFalse();
+        assertThat(((Nodes.SettingRow) parts(editor).get(1)).title()).isEqualTo("Dark theme");
+        assertThat(((Nodes.SettingRow) parts(editor).get(2)).title()).isEqualTo("Light theme");
+        assertThat(pick(editor, 1).kind()).isEqualTo(Nodes.ChoiceKind.DROPDOWN);
+        assertThat(pick(editor, 1).options()).extracting(Option::label).containsExactly("Slate Steel", "Ocean");
+        assertThat(pick(editor, 2).options()).extracting(Option::label).containsExactly("Slate Steel");
+        var reset = (Nodes.Button) ((Nodes.Stack) ((Nodes.SettingRow) parts(editor).get(1)).control()).children().get(1);
+        assertThat(reset.state().visible()).as("Reset hidden at the defaults").isFalse();
+        assertThat(reset.action()).isEqualTo("ui.appearance:reset");
+    }
+
+    @Test void pickingADarkThemeStagesItWithTheTilesAndPreviewsIt() {
+        var editor = editor(draft(""));
+        editor.show(AppearanceMode.SYSTEM);
+        assertThat(editor.event("ui.appearance", new UiEvent.Selected("ui.appearance:dark-theme", java.util.Set.of("ocean")))).isTrue();
+        assertThat(editor.companionValues()).containsEntry(CoreSettings.UI_THEME_DARK, Optional.of("ocean"))
+            .containsEntry(CoreSettings.UI_THEME_LIGHT, Optional.empty());
+        var tiles = (Nodes.Choice) parts(editor).get(0);
+        assertThat(tiles.options().get(0).preview()).contains(new Option.Preview(OCEAN, Optional.empty()));
+        assertThat(tiles.options().get(0).description()).isEqualTo("Ocean");
+        assertThat(tiles.options().get(2).preview()).contains(new Option.Preview(OCEAN, Optional.of(LIGHT)));
+        assertThat(editor.event("ui.appearance", new UiEvent.Selected("ui.appearance:light-theme", java.util.Set.of("ocean"))))
+            .as("Ocean has no Light variant").isFalse();
+        var reset = (Nodes.Button) ((Nodes.Stack) ((Nodes.SettingRow) parts(editor).get(1)).control()).children().get(1);
+        assertThat(reset.state().visible()).isTrue();
+        assertThat(editor.relatedSettings()).containsExactly(CoreSettings.UI_THEME_DARK, CoreSettings.UI_THEME_LIGHT);
+    }
+
+    @Test void aSavedPickIsKeptAsSavedAndOneThatIsNotInstalledIsShownAsSuch() {
+        var editor = editor(draft("[ui.theme]\ndark = \"ocean\"\nlight = \"dawn\"\n"));
+        assertThat(editor.companionValues()).as("untouched picks stage what the file holds")
+            .containsEntry(CoreSettings.UI_THEME_DARK, Optional.of("ocean")).containsEntry(CoreSettings.UI_THEME_LIGHT, Optional.of("dawn"));
+        assertThat(pick(editor, 2).options()).extracting(Option::label).containsExactly("Slate Steel", "dawn (not installed)");
+        assertThat(pick(editor, 2).selectedId()).isEqualTo("dawn");
+        var tiles = (Nodes.Choice) parts(editor).get(0);
+        assertThat(tiles.options().get(1).preview()).as("Slate Steel is shown instead").contains(new Option.Preview(LIGHT, Optional.empty()));
+        assertThat(tiles.options().get(1).description()).isEqualTo("dawn (not installed)");
     }
 
     @Test void choosingATileEditsTheValueAndResetShows() {
-        var editor = new AppearanceSelection(AppearanceChoices.of(DARK, LIGHT));
+        var editor = new AppearanceSelection(AppearanceChoices.of(DARK, LIGHT), draft(""));
         editor.show(AppearanceMode.SYSTEM);
         assertThat(editor.event("ui.appearance", new UiEvent.Selected("ui.appearance:policy", java.util.Set.of("light")))).isTrue();
         assertThat(editor.value()).isEqualTo(AppearanceMode.LIGHT);
         assertThat(editor.event("ui.appearance", new UiEvent.Selected("ui.appearance:policy", java.util.Set.of("light")))).as("the same tile again").isFalse();
         var reset = (Nodes.Button) ((Nodes.Stack) ((Nodes.SettingRow) parts(editor).get(1)).control()).children().get(1);
         assertThat(reset.state().visible()).isTrue();
-        assertThat(reset.action()).isEqualTo("ui.appearance:reset");
         assertThat(((Nodes.Choice) parts(editor).get(0)).options().get(2).description()).isEqualTo("System appearance unavailable; using Dark");
     }
 
JASPER_PATCH
`````

- [ ] **Step 2: See them fail**

Run: `./gradlew :jasper-app:compileTestJava`
Expected: FAIL: `AppearanceChoices` and `AppearanceSelection` constructors cannot be applied to the given types.

- [ ] **Step 3: Apply the production changes**

`````bash
git apply --whitespace=nowarn <<'JASPER_PATCH'
diff --git a/jasper-app/src/main/java/dev/jasper/app/application/JasperApplication.java b/jasper-app/src/main/java/dev/jasper/app/application/JasperApplication.java
index b82549bc45d74dd08b6e2f7a6b5a64e34964bba4..705179d7aa1ffec1982d7671f0cbdc1f192f2826 100644
--- a/jasper-app/src/main/java/dev/jasper/app/application/JasperApplication.java
+++ b/jasper-app/src/main/java/dev/jasper/app/application/JasperApplication.java
@@ -798,7 +798,7 @@ public final class JasperApplication {
             var shells = new dev.jasper.app.settingsui.ShellChoices(() -> shellProfiles.current().stream()
                     .map(profile -> new dev.jasper.app.settingsui.ShellChoices.Choice(profile.id(), profile.label())).toList(),
                 shellProfiles::refresh, shellProfiles::onChanged);
-            var appearanceChoices = SettingsAppearance.choices(systemAppearance);
+            var appearanceChoices = SettingsAppearance.choices(systemAppearance, themes::library);
             settings = new dev.jasper.app.settingsui.SettingsDialog(auxiliary,
                 dev.jasper.app.settingsui.CorePages.pages(appearanceChoices, system, shells,
                     new dev.jasper.app.settingsui.NotificationGroupChoices(() -> groupChoices(notificationCenter.groups(), this::pluginName, plugins == null ? Map.of() : plugins.pluginIcons())),
diff --git a/jasper-app/src/main/java/dev/jasper/app/application/SettingsAppearance.java b/jasper-app/src/main/java/dev/jasper/app/application/SettingsAppearance.java
index 001a5602bc87de26d74be177081d6a48b69ce549..f788a7d76d4e997ad7db82ac7ead7374053882fd 100644
--- a/jasper-app/src/main/java/dev/jasper/app/application/SettingsAppearance.java
+++ b/jasper-app/src/main/java/dev/jasper/app/application/SettingsAppearance.java
@@ -10,19 +10,24 @@ import dev.jasper.ui.theme.SemanticColor;
 import dev.jasper.ui.theme.JasperTheme;
 import java.util.Optional;
 
-/** The Appearance page's values: Slate Steel's variants as previews, its workspace glass and the live OS appearance. */
+/** The Appearance page's values: the installed themes with their previews, the workspace glass and the live OS appearance. */
 final class SettingsAppearance {
     private SettingsAppearance() { }
 
-    static AppearanceChoices choices(SystemAppearance system) {
+    /** The choices over {@code library}, the installed themes as they are whenever the page reads them. */
+    static AppearanceChoices choices(SystemAppearance system, java.util.function.Supplier<ThemeLibrary> library) {
         var glass = JasperTheme.bundled().generated(true).glass();
-        var themes = themeChoices(ThemeLibrary.bundledOnly());
-        return new AppearanceChoices(preview(true), preview(false), (float) (glass.opacity() * 100), glass.blur(),
+        var cache = new Object() { ThemeLibrary from; InstalledThemes themes; };
+        return new AppearanceChoices((float) (glass.opacity() * 100), glass.blur(),
             () -> switch (system.current().preference()) {
                 case DARK -> Optional.of(true);
                 case LIGHT -> Optional.of(false);
                 case UNAVAILABLE -> Optional.empty();
-            }, listener -> new Subscription(system.subscribe(ignored -> listener.run())), () -> themes);
+            }, listener -> new Subscription(system.subscribe(ignored -> listener.run())), () -> {
+                var current = library.get();
+                if (cache.from != current) { cache.themes = themeChoices(current); cache.from = current; }
+                return cache.themes;
+            });
     }
 
     /** What the Appearance page offers from {@code library}: its themes for each appearance and its schemes, with their labels. */
diff --git a/jasper-app/src/main/java/dev/jasper/app/settingsui/AppearanceChoices.java b/jasper-app/src/main/java/dev/jasper/app/settingsui/AppearanceChoices.java
index 06dd6112d7daadee236eba1f9686b8ae7d961af0..6a36de7efc2766707b79c307f922b8cad6bc68ed 100644
--- a/jasper-app/src/main/java/dev/jasper/app/settingsui/AppearanceChoices.java
+++ b/jasper-app/src/main/java/dev/jasper/app/settingsui/AppearanceChoices.java
@@ -8,23 +8,19 @@ import java.util.function.Function;
 import java.util.function.Supplier;
 
 /**
- * What the Appearance page shows, supplied by the application: Slate Steel's Dark and Light previews, the theme's
- * workspace glass (the defaults the Workspace rows return to), the operating system's appearance when it is known, and
- * its changes. EDT only.
+ * What the Appearance page shows, supplied by the application: the installed themes and schemes, the workspace glass
+ * (the defaults the Workspace rows return to), the operating system's appearance when it is known, and its changes.
+ * EDT only.
  *
- * @param dark the Dark tile's preview
- * @param light the Light tile's preview
  * @param opacity the theme's workspace opacity, 10–100
  * @param blur whether the theme blurs a translucent workspace
  * @param systemDark the operating system's appearance, empty when unavailable
  * @param onChanged runs its argument whenever the operating system's appearance changes
  * @param themes the installed themes and schemes, read again whenever the page refreshes
  */
-public record AppearanceChoices(Option.Palette dark, Option.Palette light, float opacity, boolean blur,
-                                Supplier<Optional<Boolean>> systemDark, Function<Runnable, Subscription> onChanged,
-                                Supplier<InstalledThemes> themes) {
+public record AppearanceChoices(float opacity, boolean blur, Supplier<Optional<Boolean>> systemDark,
+                                Function<Runnable, Subscription> onChanged, Supplier<InstalledThemes> themes) {
     public AppearanceChoices {
-        Objects.requireNonNull(dark, "dark"); Objects.requireNonNull(light, "light");
         Objects.requireNonNull(systemDark, "systemDark"); Objects.requireNonNull(onChanged, "onChanged");
         Objects.requireNonNull(themes, "themes");
     }
@@ -32,6 +28,6 @@ public record AppearanceChoices(Option.Palette dark, Option.Palette light, float
     /** Fixed previews, Slate Steel alone, an opaque workspace and no system appearance, for pages built without an application. */
     public static AppearanceChoices of(Option.Palette dark, Option.Palette light) {
         var themes = InstalledThemes.slateSteel(dark, light);
-        return new AppearanceChoices(dark, light, 100f, false, Optional::empty, ignored -> new Subscription(() -> { }), () -> themes);
+        return new AppearanceChoices(100f, false, Optional::empty, ignored -> new Subscription(() -> { }), () -> themes);
     }
 }
diff --git a/jasper-app/src/main/java/dev/jasper/app/settingsui/AppearanceSelection.java b/jasper-app/src/main/java/dev/jasper/app/settingsui/AppearanceSelection.java
index 4b3913ecdf59e5693aedd6f8ee4d3a1739a70a20..79489409ee944aef53bbdc58f0b1fefdf202e16d 100644
--- a/jasper-app/src/main/java/dev/jasper/app/settingsui/AppearanceSelection.java
+++ b/jasper-app/src/main/java/dev/jasper/app/settingsui/AppearanceSelection.java
@@ -1,28 +1,39 @@
 package dev.jasper.app.settingsui;
 
 import static dev.jasper.app.config.CoreSettings.UI_APPEARANCE;
+import static dev.jasper.app.config.CoreSettings.UI_THEME_DARK;
+import static dev.jasper.app.config.CoreSettings.UI_THEME_LIGHT;
 
 import dev.jasper.app.config.AppearanceMode;
 import dev.jasper.app.lifecycle.Subscription;
+import dev.jasper.app.settings.Setting;
 import dev.jasper.ui.model.*;
+import java.util.ArrayList;
 import java.util.List;
+import java.util.Map;
 import java.util.Optional;
 
 /**
- * Settings › Appearance's theme card: the Dark / Light / Match System tiles for {@code ui.appearance}, previewing
- * Slate Steel's variants, then the Theme row, whose one entry is Slate Steel (there is nothing else to choose). EDT only.
+ * Settings › Appearance's theme card: the Dark / Light / Match System tiles for {@code ui.appearance}, each previewing
+ * the theme picked for it, then the Dark theme and Light theme rows ({@code ui.theme.dark}, {@code ui.theme.light}),
+ * which list the installed themes with that variant. The picks are staged with the tiles; Reset returns all three to
+ * their defaults. EDT only.
  */
 final class AppearanceSelection implements ValueEditor<AppearanceMode> {
-    /** The one theme. */
-    static final String THEME = "Slate Steel";
     private final AppearanceChoices choices;
+    private final SettingsDraft draft;
     private final Subscription listening;
+    private InstalledThemes themes;
     private AppearanceMode value = UI_APPEARANCE.defaultValue();
+    private String dark = UI_THEME_DARK.defaultValue(), light = UI_THEME_LIGHT.defaultValue();
     private Runnable render = () -> { };
 
-    AppearanceSelection(AppearanceChoices choices) {
+    AppearanceSelection(AppearanceChoices choices, SettingsDraft draft) {
         this.choices = choices;
+        this.draft = draft;
+        themes = choices.themes().get();
         listening = choices.onChanged().apply(() -> render.run());
+        showRelated();
     }
 
     @Override public boolean cardRows() { return true; }
@@ -31,25 +42,52 @@ final class AppearanceSelection implements ValueEditor<AppearanceMode> {
     @Override public Nodes.Node node(String id, ControlState state, boolean highlighted) {
         var plain = new ControlState(state.visible(), state.enabled(), state.busy(), "");
         Optional<Boolean> system = choices.systemDark().get();
+        var darkPreview = preview(themes.dark(), dark);
+        var lightPreview = preview(themes.light(), light);
         var tiles = new Nodes.Choice(id + ":policy", plain, Nodes.ChoiceKind.PREVIEWS, List.of(
-            tile("dark", "Dark", THEME + " Dark", new Option.Preview(choices.dark(), Optional.empty())),
-            tile("light", "Light", THEME + " Light", new Option.Preview(choices.light(), Optional.empty())),
-            tile("system", "Match System", system.map(dark -> "System appearance: " + (dark ? "Dark" : "Light"))
-                .orElse("System appearance unavailable; using Dark"), new Option.Preview(choices.dark(), Optional.of(choices.light())))),
+            tile("dark", "Dark", label(themes.dark(), dark), new Option.Preview(darkPreview, Optional.empty())),
+            tile("light", "Light", label(themes.light(), light), new Option.Preview(lightPreview, Optional.empty())),
+            tile("system", "Match System", system.map(isDark -> "System appearance: " + (isDark ? "Dark" : "Light"))
+                .orElse("System appearance unavailable; using Dark"), new Option.Preview(darkPreview, Optional.of(lightPreview)))),
             token(value), "Appearance", "");
-        var theme = new Nodes.Choice(id + ":theme", plain, Nodes.ChoiceKind.DROPDOWN,
-            List.of(new Option("slate-steel", THEME, Optional.empty())), "slate-steel", "Theme", "");
-        var reset = new Nodes.Button(id + ":reset", new ControlState(value != UI_APPEARANCE.defaultValue(), true, false, ""), "",
-            "reset", Nodes.ButtonKind.GHOST, id + ":reset", "Reset " + UI_APPEARANCE.label() + " to default");
-        var controls = new Nodes.Stack(id + ":controls", plain, Nodes.LayoutRole.SETTINGS_TRAILING, List.of(theme, reset));
-        return new Nodes.Stack(id + ":selection", plain, Nodes.LayoutRole.VERTICAL,
-            List.of(tiles, new Nodes.SettingRow("row:" + id, plain, "Theme", "", controls, false, highlighted)));
+        boolean modified = value != UI_APPEARANCE.defaultValue() || !dark.equals(UI_THEME_DARK.defaultValue())
+            || !light.equals(UI_THEME_LIGHT.defaultValue());
+        var reset = new Nodes.Button(id + ":reset", new ControlState(modified, true, false, ""), "",
+            "reset", Nodes.ButtonKind.GHOST, id + ":reset", "Reset " + UI_APPEARANCE.label() + " and its themes to their defaults");
+        var darkPick = new Nodes.Choice(id + ":dark-theme", plain, Nodes.ChoiceKind.DROPDOWN, options(themes.dark(), dark), dark,
+            UI_THEME_DARK.label(), "");
+        var lightPick = new Nodes.Choice(id + ":light-theme", plain, Nodes.ChoiceKind.DROPDOWN, options(themes.light(), light), light,
+            UI_THEME_LIGHT.label(), "");
+        var darkControls = new Nodes.Stack(id + ":dark-controls", plain, Nodes.LayoutRole.SETTINGS_TRAILING, List.of(darkPick, reset));
+        var lightControls = new Nodes.Stack(id + ":light-controls", plain, Nodes.LayoutRole.SETTINGS_TRAILING, List.of(lightPick));
+        return new Nodes.Stack(id + ":selection", plain, Nodes.LayoutRole.VERTICAL, List.of(tiles,
+            new Nodes.SettingRow("row:" + id, plain, UI_THEME_DARK.label(), "", darkControls, false, highlighted),
+            new Nodes.SettingRow("row:" + id + ":light", plain, UI_THEME_LIGHT.label(), "", lightControls, false, highlighted)));
     }
 
     private static Option tile(String id, String label, String caption, Option.Preview preview) {
         return new Option(id, label, Optional.empty(), "", caption, Optional.of(preview));
     }
 
+    /** The installed themes, plus the picked one marked when it is not installed. */
+    private static List<Option> options(List<InstalledThemes.ThemeOption> installed, String picked) {
+        var options = new ArrayList<Option>();
+        for (var theme : installed) options.add(new Option(theme.id(), theme.label(), Optional.empty()));
+        if (installed.stream().noneMatch(theme -> theme.id().equals(picked)))
+            options.add(new Option(picked, picked + " (not installed)", Optional.empty()));
+        return options;
+    }
+
+    /** The picked theme's preview; one that is not installed previews the first, Slate Steel, which is shown instead. */
+    private static Option.Palette preview(List<InstalledThemes.ThemeOption> installed, String picked) {
+        return installed.stream().filter(theme -> theme.id().equals(picked)).findFirst().orElse(installed.getFirst()).preview();
+    }
+
+    private static String label(List<InstalledThemes.ThemeOption> installed, String picked) {
+        return installed.stream().filter(theme -> theme.id().equals(picked)).findFirst().map(InstalledThemes.ThemeOption::label)
+            .orElse(picked + " (not installed)");
+    }
+
     private static String token(AppearanceMode mode) {
         return switch (mode) { case DARK -> "dark"; case LIGHT -> "light"; case SYSTEM -> "system"; };
     }
@@ -57,8 +95,20 @@ final class AppearanceSelection implements ValueEditor<AppearanceMode> {
     @Override public AppearanceMode value() { return value; }
     @Override public void show(AppearanceMode value) { this.value = value; }
     @Override public boolean event(String id, UiEvent event) {
-        if (!(event instanceof UiEvent.Selected selected) || !selected.id().equals(id + ":policy")) return false;
-        AppearanceMode next = switch (selected.ids().stream().findFirst().orElse("")) {
+        if (!(event instanceof UiEvent.Selected selected)) return false;
+        String picked = selected.ids().stream().findFirst().orElse("");
+        if (selected.id().equals(id + ":dark-theme")) {
+            if (picked.isEmpty() || picked.equals(dark) || themes.dark().stream().noneMatch(theme -> theme.id().equals(picked))) return false;
+            dark = picked;
+            return true;
+        }
+        if (selected.id().equals(id + ":light-theme")) {
+            if (picked.isEmpty() || picked.equals(light) || themes.light().stream().noneMatch(theme -> theme.id().equals(picked))) return false;
+            light = picked;
+            return true;
+        }
+        if (!selected.id().equals(id + ":policy")) return false;
+        AppearanceMode next = switch (picked) {
             case "dark" -> AppearanceMode.DARK;
             case "light" -> AppearanceMode.LIGHT;
             case "system" -> AppearanceMode.SYSTEM;
@@ -68,6 +118,21 @@ final class AppearanceSelection implements ValueEditor<AppearanceMode> {
         value = next;
         return true;
     }
+
+    @Override public Map<Setting<?>, Optional<?>> companionValues() {
+        return Map.of(UI_THEME_DARK, staged(UI_THEME_DARK, dark), UI_THEME_LIGHT, staged(UI_THEME_LIGHT, light));
+    }
+
+    /** The saved value when the pick is what the file already means, so an untouched pick never becomes an edit. */
+    private Optional<String> staged(Setting<String> setting, String pick) {
+        var saved = draft.saved(setting);
+        if (saved.map(pick::equals).orElse(pick.equals(setting.defaultValue()))) return saved;
+        return pick.equals(setting.defaultValue()) ? Optional.empty() : Optional.of(pick);
+    }
+
+    @Override public void showRelated() { dark = draft.value(UI_THEME_DARK); light = draft.value(UI_THEME_LIGHT); }
+    @Override public List<Setting<?>> relatedSettings() { return List.of(UI_THEME_DARK, UI_THEME_LIGHT); }
+    @Override public void refresh() { themes = choices.themes().get(); }
     @Override public void onChanged(Runnable render) { this.render = render; }
     @Override public void dispose() { listening.close(); }
 }
diff --git a/jasper-app/src/main/java/dev/jasper/app/settingsui/Editors.java b/jasper-app/src/main/java/dev/jasper/app/settingsui/Editors.java
index 32202913d2d7b8b1dfcbc9fa8fac30825a00c143..049314d0416a51384562b8ae58e20bb29e48afe6 100644
--- a/jasper-app/src/main/java/dev/jasper/app/settingsui/Editors.java
+++ b/jasper-app/src/main/java/dev/jasper/app/settingsui/Editors.java
@@ -145,10 +145,10 @@ public final class Editors {
             List.of(SAME_AS_UI_THEME, "Slate Steel Dark", "Slate Steel Light", "High Contrast"));
     }
 
-    /** The Dark / Light / Match System tiles, then the Theme row with its one entry, Slate Steel. */
+    /** The Dark / Light / Match System tiles, then the Dark theme and Light theme rows over the installed themes. */
     public static Factory<AppearanceMode> appearance(AppearanceChoices choices) {
-        return new Factory<>((setting, context) -> new AppearanceSelection(choices),
-            List.of("Dark", "Light", "Match System", "Theme", AppearanceSelection.THEME));
+        return new Factory<>((setting, context) -> new AppearanceSelection(choices, context.draft()),
+            List.of("Dark", "Light", "Match System", "Theme", "Dark theme", "Light theme", "Slate Steel"));
     }
 
     /** A path field with Browse…, for a program path. */
JASPER_PATCH
`````

- [ ] **Step 4: Run the tests**

Run: `./gradlew :jasper-app:test --tests "dev.jasper.app.settingsui.*" --tests "*ThemeMigrationCoverageTest"`
Expected: PASS (about 120 tests).

- [ ] **Step 5: Commit**

```bash
git add -A
git commit -m "Dark theme and Light theme pickers on the Appearance page

🤖 Generated with Claude Code at The Home Depot

Co-Authored-By: Claude <noreply@anthropic.com>"
```

### Task 6: The palette's terminal scheme commands follow the installed schemes

**What changes:** `WindowCommands` subscribes to the window's `ThemeController` and, whenever the library is a
different one, closes the old "Terminal: …" registrations and registers "Terminal: Same as UI theme" and one command
per installed scheme (labels from the library); `refresh()` also syncs, then marks the chosen one.
`WindowContent.themes()` exposes the shared controller.

**Files:** modify `workspace/{WindowCommands,WindowContent}.java`, test `TerminalSchemeCommandsTest`.

**Interfaces:**
- Consumes: Task 4's `ThemeController.library()`, `subscribe`; Task 3's `schemeCommandId`.
- Produces: `WindowContent.themes()` (package-private).

- [ ] **Step 1: Apply the test change**

`````bash
git apply --whitespace=nowarn <<'JASPER_PATCH'
diff --git a/jasper-app/src/test/java/dev/jasper/app/workspace/TerminalSchemeCommandsTest.java b/jasper-app/src/test/java/dev/jasper/app/workspace/TerminalSchemeCommandsTest.java
index ffe5f8df08fb13118d1a7ac53a7020a3939150cc..e6e7e2c1437716975004bacf5d80ab845a893e93 100644
--- a/jasper-app/src/test/java/dev/jasper/app/workspace/TerminalSchemeCommandsTest.java
+++ b/jasper-app/src/test/java/dev/jasper/app/workspace/TerminalSchemeCommandsTest.java
@@ -58,6 +58,29 @@ class TerminalSchemeCommandsTest {
         });
     }
 
+    @Test void theCommandsFollowTheInstalledSchemes(@org.junit.jupiter.api.io.TempDir java.nio.file.Path home) throws Exception {
+        var folder = java.nio.file.Files.createDirectories(home.resolve("themes"));
+        java.nio.file.Files.writeString(folder.resolve("zenburn.json"), ThemeTestSupport.terminalThemeJson("Zenburn"));
+        edt(() -> {
+            var themes = new ThemeController(true, TerminalSchemeChoice.MATCH);
+            var owner = content(launcher(new ArrayDeque<>()), themes); owner.setActive(true);
+            assertThat(owner.windowCommands().view("view.terminal_scheme.zenburn.dark")).isNull();
+            var library = dev.jasper.app.appearance.ThemeLibrary.read(folder, themes.library());
+            themes.configure(true, dev.jasper.app.appearance.ThemeSelection.of(library, "slate-steel", "slate-steel",
+                TerminalSchemeChoice.MATCH, (key, message) -> { }), dev.jasper.app.config.UiFontConfig.defaults());
+            var zenburn = owner.windowCommands().view("view.terminal_scheme.zenburn.dark");
+            assertThat(zenburn.getValue(javax.swing.Action.NAME)).isEqualTo("Terminal: Zenburn");
+            assertThat(zenburn.getValue(dev.jasper.app.commands.Command.TITLE)).isEqualTo("Terminal Scheme: Zenburn");
+            zenburn.actionPerformed(new java.awt.event.ActionEvent(owner, 0, ""));
+            assertThat(themes.schemeChoice()).isEqualTo(TerminalSchemeChoice.parse("zenburn/dark").orElseThrow());
+            themes.configure(true, dev.jasper.app.appearance.ThemeSelection.slateSteel(TerminalSchemeChoice.MATCH),
+                dev.jasper.app.config.UiFontConfig.defaults());
+            assertThat(owner.windowCommands().view("view.terminal_scheme.zenburn.dark")).isNull();
+            assertThat(owner.commands().find("view.terminal_scheme.zenburn.dark")).isEmpty();
+            assertThat(owner.commands().find("view.terminal_scheme.slate-steel.high-contrast")).isPresent();
+        });
+    }
+
     @Test void theCommandsChooseEachSchemeAndShowWhichIsChosen() throws Exception {
         edt(() -> {
             var themes = new ThemeController(true, TerminalSchemeChoice.MATCH);
JASPER_PATCH
`````

- [ ] **Step 2: See it fail**

Run: `./gradlew :jasper-app:test --tests "*TerminalSchemeCommandsTest"`
Expected: FAIL in `theCommandsFollowTheInstalledSchemes` (no `view.terminal_scheme.zenburn.dark`).

- [ ] **Step 3: Apply the production changes**

`````bash
git apply --whitespace=nowarn <<'JASPER_PATCH'
diff --git a/jasper-app/src/main/java/dev/jasper/app/workspace/WindowCommands.java b/jasper-app/src/main/java/dev/jasper/app/workspace/WindowCommands.java
index d4c412fef9b1e22ede2a543210681f34ac138509..54a635cc74aa004341ffdbc9d676d3b5d9d7f1c1 100644
--- a/jasper-app/src/main/java/dev/jasper/app/workspace/WindowCommands.java
+++ b/jasper-app/src/main/java/dev/jasper/app/workspace/WindowCommands.java
@@ -5,10 +5,9 @@ import dev.jasper.app.commands.Command;
 import dev.jasper.app.commands.CommandRegistry;
 import dev.jasper.app.platform.AppIcons;
 import dev.jasper.app.lifecycle.Subscription;
+import dev.jasper.app.appearance.ThemeLibrary;
 import dev.jasper.app.config.TerminalSchemeChoice;
 import dev.jasper.app.config.ToolbarMode;
-import dev.jasper.ui.theme.JasperTheme;
-import dev.jasper.ui.theme.TerminalScheme;
 
 import java.awt.event.ActionEvent;
 import java.util.ArrayList;
@@ -18,14 +17,23 @@ import java.util.Map;
 import javax.swing.AbstractAction;
 import javax.swing.Action;
 
-/** Adapts existing window actions to searchable commands, including the shared View menu actions. */
+/**
+ * Adapts existing window actions to searchable commands, including the shared View menu actions. The "Terminal: …"
+ * commands follow the installed schemes: they are rebuilt whenever the theme library changes.
+ */
 final class WindowCommands implements AutoCloseable {
     private final WindowContent owner;
+    private final CommandRegistry registry;
     private final List<Subscription> registrations = new ArrayList<>();
     private final Map<String, Action> views = new LinkedHashMap<>();
+    private final Map<String, Subscription> schemeCommands = new LinkedHashMap<>();
+    private List<TerminalSchemeChoice> schemes = List.of();
+    private ThemeLibrary schemesFrom;
+    private final Subscription themeListening;
 
     WindowCommands(WindowContent owner, CommandRegistry registry) {
         this.owner = owner;
+        this.registry = registry;
         owner.action(ActionId.SPLIT_RIGHT).putValue(Command.TITLE, "Split Right \u00b7 Vertical");
         owner.action(ActionId.SPLIT_DOWN).putValue(Command.TITLE, "Split Down \u00b7 Horizontal");
         for (ActionId id : ActionId.values()) {
@@ -65,12 +73,7 @@ final class WindowCommands implements AutoCloseable {
         add(registry, "view.rail", "Rail", "Hide Rail", () -> owner.setRailVisible(!owner.railVisible()), List.of("panels", "sidebar"));
         add(registry, "view.buddy", "Show Jasper", "Hide Jasper", () -> { owner.onToggleBuddy.run(); owner.updateActions(); },
             List.of("jasper", "mascot", "turtle", "desk buddy"));
-        for (var scheme : schemeChoices()) {
-            String label = scheme.scheme().flatMap(id -> JasperTheme.bundled().scheme(id.key())).map(TerminalScheme::name)
-                .orElse("Same as UI theme");
-            add(registry, schemeCommandId(scheme), "Terminal: " + label,
-                "Terminal Scheme: " + label, () -> owner.selectTerminalScheme(scheme), List.of("terminal", "scheme", "colors", "palette"));
-        }
+        themeListening = owner.themes().subscribe((theme, chrome) -> syncSchemes());
         add(registry, "view.themes", "Themes…", "Themes…", owner::openThemesSettings, List.of("theme", "appearance", "dark", "light", "terminal scheme"));
         add(registry, "view.window_settings", "Window Settings\u2026", "Window Settings\u2026", owner::openWindowSettings,
             List.of("tab height", "rail width", "toolbar height", "status bar height", "size"));
@@ -82,6 +85,11 @@ final class WindowCommands implements AutoCloseable {
 
     private void add(CommandRegistry registry, String id, String label, String title, Runnable callback,
                       List<String> extraKeywords) {
+        registrations.add(register(registry, id, label, title, callback, extraKeywords));
+    }
+
+    private Subscription register(CommandRegistry registry, String id, String label, String title, Runnable callback,
+                                  List<String> extraKeywords) {
         Action action = new AbstractAction(label) {
             @Override public void actionPerformed(ActionEvent event) {
                 if (isEnabled() && owner.isActiveAndOpen() && !owner.commandPalette().isOpen()
@@ -92,16 +100,28 @@ final class WindowCommands implements AutoCloseable {
         views.put(id, action);
         var keywords = new java.util.ArrayList<>(List.of(id.replace('.', ' ').replace('_', ' ')));
         keywords.addAll(extraKeywords);
-        registrations.add(registry.register(new Command(id, action, keywords)));
+        return registry.register(new Command(id, action, keywords));
     }
 
     Action view(String id) { return views.get(id); }
 
-    /** "Same as UI theme", then Slate Steel's schemes. */
-    private static List<TerminalSchemeChoice> schemeChoices() {
+    /** "Terminal: Same as UI theme", then one command per installed scheme; rebuilt only when the library changed. */
+    private void syncSchemes() {
+        var library = owner.themes().library();
+        if (library == schemesFrom) return;
+        schemesFrom = library;
+        schemeCommands.values().forEach(Subscription::close);
+        schemeCommands.keySet().forEach(id -> views.remove(id).setEnabled(false));
+        schemeCommands.clear();
         var choices = new ArrayList<TerminalSchemeChoice>(List.of(TerminalSchemeChoice.MATCH));
-        for (var scheme : JasperTheme.bundled().schemes()) choices.add(TerminalSchemeChoice.of(scheme.id()));
-        return choices;
+        for (var scheme : library.schemes()) choices.add(TerminalSchemeChoice.of(scheme.id()));
+        for (var choice : choices) {
+            String label = choice.scheme().flatMap(library::scheme).map(library::label).orElse("Same as UI theme");
+            String id = schemeCommandId(choice);
+            schemeCommands.put(id, register(registry, id, "Terminal: " + label, "Terminal Scheme: " + label,
+                () -> owner.selectTerminalScheme(choice), List.of("terminal", "scheme", "colors", "palette")));
+        }
+        schemes = List.copyOf(choices);
     }
 
     /** The command that chooses {@code choice}: {@code view.terminal_scheme.match} or {@code view.terminal_scheme.<theme>.<key>}. */
@@ -128,7 +148,8 @@ final class WindowCommands implements AutoCloseable {
             var action = view("view.toolbar." + mode.name().toLowerCase(java.util.Locale.ROOT));
             action.putValue(Action.SELECTED_KEY, owner.toolbarMode() == mode);
         }
-        for (var scheme : schemeChoices()) {
+        syncSchemes();
+        for (var scheme : schemes) {
             var action = view(schemeCommandId(scheme));
             action.putValue(Action.SELECTED_KEY, owner.terminalSchemeSelected(scheme));
             action.setEnabled(true);
@@ -138,7 +159,9 @@ final class WindowCommands implements AutoCloseable {
     }
 
     @Override public void close() {
+        themeListening.close();
         registrations.forEach(Subscription::close); registrations.clear();
+        schemeCommands.values().forEach(Subscription::close); schemeCommands.clear();
         views.values().forEach(action -> action.setEnabled(false));
     }
 }
diff --git a/jasper-app/src/main/java/dev/jasper/app/workspace/WindowContent.java b/jasper-app/src/main/java/dev/jasper/app/workspace/WindowContent.java
index ffd2aff092aef7e0d11780f1dc8f431f669b1861..3d0a7182d9b520970f93c78e9db52d14de252857 100644
--- a/jasper-app/src/main/java/dev/jasper/app/workspace/WindowContent.java
+++ b/jasper-app/src/main/java/dev/jasper/app/workspace/WindowContent.java
@@ -661,6 +661,8 @@ public final class WindowContent extends JPanel implements AutoCloseable {
     void requestQuit() { quit.run(); }
 
     CommandRegistry commands() { return commands; }
+    /** The application's theme controller, shared by every window. */
+    ThemeController themes() { return themes; }
     ScopeRegistry scopes() { return scopes; }
     CommandsScope commandsScope() { return commandsScope; }
     void dispatchCommand(Command command) {
JASPER_PATCH
`````

- [ ] **Step 4: Run the tests**

Run: `./gradlew :jasper-app:test --tests "*TerminalScheme*" --tests "*WindowCommandsTest" --tests "*WindowChromeTest" --tests "*ConfigurationControllerTest"`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add -A
git commit -m "Terminal scheme commands for every installed scheme

🤖 Generated with Claude Code at The Home Depot

Co-Authored-By: Claude <noreply@anthropic.com>"
```

### Task 7: Documentation and verification

**What changes:** `docs/themes.md` is rewritten for installed themes and the format (sections "Choosing the
appearance, the themes and the terminal scheme", "Installed themes", "The Jasper theme format"; the existing "The token
rule" and "Native acceptance for this upgrade (user-run)" headings stay, since other documents link to the latter);
`app-architecture.md`, `app-maintenance.md`, `configuration.md`, `jasper-app/README.md`, `jasper-ui/README.md` and
`README.md` stop saying there is one theme and no themes folder; `docs/STATUS.md` records the plan and its deviation.

**Files:** the docs above.

**Interfaces:** Plans 2 and 3 add sections to `docs/themes.md` before `## The Jasper theme format`.

- [ ] **Step 1: Apply the documentation**

`````bash
git apply --whitespace=nowarn <<'JASPER_PATCH'
diff --git a/README.md b/README.md
index 373fec8accb8523bacc99e42b746c046fb24d965..d88e87e5a2ab4304752a6f36611eff6591d03445 100644
--- a/README.md
+++ b/README.md
@@ -6,7 +6,7 @@ Formerly Moray. The app, the Java packages and the Git repository
 ([an0nn30/Jasper](https://github.com/an0nn30/Jasper)) are now Jasper. Existing settings can be
 carried over using the [rebrand migration notes](docs/rebranding.md).
 
-Jasper has its own terminal renderer over JediTerm and pty4j, with ligatures, fallback fonts, truecolor, mouse reporting, scrollback, selection, clipboard, shell integration and links. The Plan 3 application adds multiple windows, tabs, splits, pane zoom/navigation, find controls, menus, toolbar and status. The screenshot UI revision puts tabs beside native macOS window controls, with a horizontal Tabler toolbar below and a status bar that shares the terminal background. One theme, Slate Steel, in Dark and Light, colours the complete window; its terminal schemes are Slate Steel Dark, Slate Steel Light and High Contrast.
+Jasper has its own terminal renderer over JediTerm and pty4j, with ligatures, fallback fonts, truecolor, mouse reporting, scrollback, selection, clipboard, shell integration and links. The Plan 3 application adds multiple windows, tabs, splits, pane zoom/navigation, find controls, menus, toolbar and status. The screenshot UI revision puts tabs beside native macOS window controls, with a horizontal Tabler toolbar below and a status bar that shares the terminal background. The built-in theme, Slate Steel, in Dark and Light, colours the complete window; its terminal schemes are Slate Steel Dark, Slate Steel Light and High Contrast, and installed Jasper theme files add more themes and schemes.
 
 Current progress, limitations and next steps: [docs/STATUS.md](docs/STATUS.md). Desktop acceptance remains user-run: [Plan 3](docs/superpowers/plans/2026-09-11-jasper-plan-3-manual-check.md) and [screenshot UI](docs/superpowers/plans/2026-09-11-jasper-mock-ui-manual-check.md). Passing the headless tests is not the Phase 1 daily-use gate.
 
diff --git a/docs/app-architecture.md b/docs/app-architecture.md
index e48c0acf1b226b89cb49ed66d489de58e48facc7..e17a635c9fab4f46259a79a0ce9b1ff5db4545ef 100644
--- a/docs/app-architecture.md
+++ b/docs/app-architecture.md
@@ -225,21 +225,27 @@ icon values or identities, and their painting and text sizes come from `NativeCh
 
 ## Themes
 
-The installed theme is Slate Steel; there are no other themes, no theme files and no themes folder.
+The built-in theme is Slate Steel; installed themes are Jasper theme files in `<home>/themes/` ([themes](themes.md)).
 `ThemeManager.install` installs a variant through jasper-ui's `FlatLafInstaller` (FlatLaf's IntelliJ-format input from
 the theme's UI keys, which `UiColorKeys` derives from the semantic roles, then the `Jasper.semantic.*` role keys and
 the popup shadow) and adds the app's native defaults: the split divider border by class name and the centred title.
 Tool windows paint `surface.panel` (theme.md's tool-window body) through `ToolWindowSurface`, their headers included; the stripes stay chrome.
 
-`JasperTheme.bundled()` (jasper-ui) supplies one `GeneratedTheme` per variant and the terminal schemes. `ThemeController.configure(dark, scheme, font)` installs the variant for chrome and workspace and
-resolves the scheme's palette for terminal panes (`ResolvedTheme.slateSteel`); `ResolvedTheme.surface()`, the
+`JasperTheme` (jasper-ui) reads a theme file into one `GeneratedTheme` per variant and its terminal schemes;
+`JasperTheme.bundled()` is Slate Steel. `appearance.ThemeLibrary` holds Slate Steel and every valid
+`<home>/themes/*.json`, reusing the parsed theme of an unchanged file, and keeps a problem for each file it skips.
+`appearance.ThemeSelection` is the Dark and Light theme picks and the scheme choice from one library; its `of` replaces
+a pick the library cannot honour by its default and reports it. `ThemeController.configure(dark, selection, font)`
+installs the shown variant for chrome and workspace and resolves the scheme's palette for terminal panes
+(`ResolvedTheme.of`); `ResolvedTheme.surface()`, the
 workspace's `surface.terminal`, is what the window behind panes, tab backgrounds and the tab strip use, so a scheme
 colours only the text area. `ConfigurationController` resolves `ui.appearance` with `AppearancePolicy` against the
-live `SystemAppearance` observation; an installation failure keeps the current appearance and is a warning on
-`ui.appearance`.
-
-Nothing reads a themes folder or converts, imports or migrates a theme; an older install's `themes` and
-`terminal-themes` folders are left as they are. Plugins receive the roles and the editor scheme through
+live `SystemAppearance` observation and builds the selection from `ui.theme.dark`, `ui.theme.light` and
+`terminal.scheme`. It reads the themes folder again through `ConfigService.watchWith` on the configuration worker,
+before each forced or changed state is published, so Reload config, a Settings save or a `config.toml` change picks up
+theme files. Skipped files, unusable picks and an installation failure are its theme problems, shown with the
+configuration's diagnostics; an installation failure keeps the current appearance. Each window's `WindowCommands`
+rebuilds the "Terminal: …" commands when the library changes. Plugins receive the roles and the editor scheme through
 `plugins.HostedPalettes`.
 
 ## Hosted workspace styling
diff --git a/docs/app-maintenance.md b/docs/app-maintenance.md
index cc795ed49ab037e3169e640418bc00571f93af6e..d6e088a7b0bb41e066232b32f09a9980c16d22c6 100644
--- a/docs/app-maintenance.md
+++ b/docs/app-maintenance.md
@@ -322,7 +322,8 @@ boundaries; this refactor promises no plugin loading, binary compatibility or pe
 
 ## Themes
 
-Jasper has one theme, Slate Steel, in Dark and Light ([themes](themes.md)). Its colours live only in jasper-ui's
+Jasper's built-in theme is Slate Steel, in Dark and Light; installed themes are theme files in `<home>/themes/`
+([themes](themes.md)). Slate Steel's colours live only in jasper-ui's
 `slate-steel.json`, the machine-readable block of [the Slate Steel record](design/theme-engine/slate-steel.md) (a test
 keeps the two identical). `JasperTheme` reads the file once: the semantic palettes, the terminal schemes (`TerminalScheme`),
 the derived editor schemes and syntax styles, and one `GeneratedTheme` per variant whose FlatLaf UI keys
@@ -644,11 +645,14 @@ expanded and editing states in both themes and larger UI type without opening a
 ### Maintaining the Appearance section
 
 The Appearance page's Theme section is `Editors.appearance(AppearanceChoices)`: `settingsui.AppearanceSelection` edits
-`ui.appearance` with the Dark / Light / Match System tiles, a read-only Theme row whose one entry is "Slate Steel", and
-a Reset button shown when the value is not `system`; `terminal.scheme` is an ordinary `Editors.choice` row noted
-"Colors for terminal panes". `AppearanceChoices` carries the two tile previews, the workspace glass defaults and the
-system-appearance observation from application wiring (`application.SettingsAppearance`), so settingsui reads jasper-ui
-only through `ui.model` and `ui.swing`. The Match System tile names the observed appearance, or says it is unavailable
+`ui.appearance` with the Dark / Light / Match System tiles, each previewing its pick, and stages `ui.theme.dark` and
+`ui.theme.light` from the Dark theme and Light theme dropdowns as companions (their rows are hidden); one Reset returns
+all three to their defaults. `terminal.scheme` is `Editors.schemeChoice`'s `SchemePick`, "Same as UI theme" then every
+installed scheme, noted "Colors for terminal panes". A saved pick that is not installed shows as "<id> (not
+installed)". `AppearanceChoices` carries `InstalledThemes` (the theme options with previews and the schemes, read again
+on every refresh), the workspace glass defaults and the system-appearance observation from application wiring
+(`application.SettingsAppearance`, over the controller's live library), so settingsui reads jasper-ui only through
+`ui.model` and `ui.swing`. The Match System tile names the observed appearance, or says it is unavailable
 and Dark is used. Terminal › Font & Colors links to the section.
 
 The command palette's `view.terminal_scheme.<name>` commands save `terminal.scheme` for every window
diff --git a/docs/configuration.md b/docs/configuration.md
index 08490997ce4e1b6a72a47652678374cedfb5523f..b97ea79b67b9288f30eb6f11eb570d2aba0ffbb8 100644
--- a/docs/configuration.md
+++ b/docs/configuration.md
@@ -46,7 +46,7 @@ sections holding them, are blue. Click one, press the new keys and click Set; Ap
 at once. Plugins lists the installed plugins: installing, enabling, disabling and removing apply at once and
 take effect after a restart. Help › Keyboard Shortcuts… and File › Manage Plugins… open these pages.
 Appearance holds View mode (Normal or Compact, applied after restart), the Dark, Light and Match System tiles,
-the Theme row (Slate Steel) and the Terminal scheme dropdown; Terminal › Font & Colors links there. See
+the Dark theme and Light theme dropdowns and the Terminal scheme dropdown; Terminal › Font & Colors links there. See
 [Themes](themes.md).
 
 Tools holds a page for each running plugin that has settings, built from what the plugin declares or by the plugin
diff --git a/docs/themes.md b/docs/themes.md
index 8057cfb773c6b0cbca1658ac28236db291833183..0c763fa05ec3ef24dea1726ff9e4f42c613bb0cd 100644
--- a/docs/themes.md
+++ b/docs/themes.md
@@ -1,37 +1,92 @@
 # Themes
 
-Jasper has one theme, **Slate Steel**, in Dark and Light, and three terminal colour schemes: **Slate Steel Dark**,
-**Slate Steel Light** and **High Contrast**. Its colours are defined once, in
-[the Slate Steel colour reference](design/theme-engine/slate-steel.md), and bundled as `slate-steel.json`; there are no
-other themes, no themes folder and no theme import.
+Jasper's built-in theme is **Slate Steel**, in Dark and Light, with three terminal colour schemes: **Slate Steel
+Dark**, **Slate Steel Light** and **High Contrast**. Its colours are defined once, in
+[the Slate Steel colour reference](design/theme-engine/slate-steel.md), and bundled as `slate-steel.json`. Other themes
+are Jasper theme files in the themes folder: a UI theme recolours the whole application and brings its own terminal and
+editor schemes; a terminal-only theme adds terminal schemes.
 
-## Choosing the appearance and the terminal scheme
+## Choosing the appearance, the themes and the terminal scheme
 
 ```toml
 [ui]
 appearance = "system" # or "dark", "light"
 
+[ui.theme]
+dark = "slate-steel"  # the theme the Dark appearance shows
+light = "slate-steel" # the theme the Light appearance shows
+
 [terminal]
-scheme = "match" # or "slate-steel-dark", "slate-steel-light", "high-contrast"
+scheme = "match" # or a scheme id such as "slate-steel/high-contrast"
 ```
 
-`ui.appearance` (`system`, `dark`, `light`) chooses Slate Steel Dark or Light, or follows the operating system;
-`terminal.scheme` (`match`, `slate-steel-dark`, `slate-steel-light`, `high-contrast`) colours the terminal panes. With
-`system`, Jasper follows the operating system's light or dark setting live and uses Dark when it cannot read it; an
-operating-system change never writes `config.toml`. `match` uses Slate Steel Dark with the Dark appearance and Slate
-Steel Light with the Light one.
+`ui.appearance` (`system`, `dark`, `light`) chooses Dark or Light, or follows the operating system's light or dark
+setting live; with `system`, Jasper uses Dark when it cannot read the operating system's setting, and an
+operating-system change never writes `config.toml`. `ui.theme.dark` and `ui.theme.light` name, by id, the theme each
+appearance shows: `slate-steel`, or an installed theme's id. A theme has a Dark variant, a Light variant or both; with
+`system`, Jasper switches between the two picks as the operating system does.
+
+`terminal.scheme` colours the terminal panes: `match` uses the shown theme's own scheme, and a scheme id,
+`<theme id>/<scheme key>`, chooses one whatever theme is shown: `slate-steel/dark`, `slate-steel/light`,
+`slate-steel/high-contrast`, or any installed theme's scheme, such as `gruvbox-dark/dark`.
+
+Settings › Appearance & Behavior › Appearance has the Dark, Light and Match System tiles, each previewing the theme
+picked for it, the **Dark theme** and **Light theme** dropdowns, and the **Terminal scheme** dropdown, whose first
+entry, **Same as UI theme**, is `match`. Terminal › Font & Colors links there, and the command palette's **Themes…**
+opens it; the palette's **Terminal: …** commands choose a scheme. Choices stay staged until Apply or OK.
 
-Settings › Appearance & Behavior › Appearance has the Dark, Light and Match System tiles, the Theme row (Slate Steel)
-and the **Terminal scheme** dropdown, whose first entry, **Same as UI theme**, is `match`. Terminal › Font & Colors
-links there, and the command palette's **Themes…** opens it. Choices stay staged until Apply or OK.
+## Installed themes
+
+The themes folder is `themes/` in Jasper's home, next to `config.toml`. It holds one Jasper theme file per theme,
+named after the theme's id: lowercase letters and digits in dash-separated words, at most 64 characters, so
+`dracula.json` is the theme `dracula`. Jasper reads the folder at start-up, on Reload config, when Settings saves and
+when `config.toml` changes; a theme changed on disk is shown again then. `slate-steel` is the built-in theme's id, so a
+`slate-steel.json` in the folder is ignored. Dropdowns list Slate Steel first, then the installed themes by name; two
+themes with the same name show their ids.
 
 ## What the terminal scheme colours
 
 A scheme colours only the terminal text area: background, text, bold text, cursor, selection and the 16 ANSI colours.
-Tabs, the tab strip, the area around panes, the editor, panels and dialogs stay on the appearance. With `match` the
-text area and the active tab meet seamlessly; with High Contrast the text area is black and the tab keeps the
-appearance's terminal surface. Workspace opacity and blur apply to the terminal area. The editor always follows the
-appearance, with the editor scheme derived from Slate Steel.
+Tabs, the tab strip, the area around panes, the editor, panels and dialogs stay on the shown theme. With `match` the
+text area and the active tab meet seamlessly; with High Contrast the text area is black and the tab keeps the theme's
+terminal surface. Workspace opacity and blur apply to the terminal area. The editor always follows the shown theme's
+editor scheme.
+
+## The Jasper theme format
+
+A Jasper theme is one UTF-8 JSON file; `slate-steel.json` is the complete example.
+
+```json
+{
+  "jasper-theme": 1,
+  "name": "Dracula",
+  "author": "Dracula Theme",
+  "ui": { "dark": { "surface.app": "#21222C", "surface.panel": "#282A36" } },
+  "terminal": { "dark": { "name": "Dracula", "bg": "#282A36", "fg": "#F8F8F2" } },
+  "editor": { "dark": { "keyword": "#FF79C6, bold", "comment": "#6272A4, italic", "gutter": "terminal.bg" } }
+}
+```
+
+(Each object is shortened here; a real file lists every entry.)
+
+- `jasper-theme` is `1`. `name` is required; `author` is optional.
+- `ui` holds `dark`, `light` or both. Each lists every role `slate-steel.json` lists, as `#RRGGBB` or `#RRGGBBAA`.
+  `border.focus`, `surface.status`, `border.identity` and the `hue.*.surface` roles are not listed: they are `accent`,
+  `surface.app`, `border.control` and each hue.
+- `terminal` holds one or more schemes, keyed by lowercase words with dashes (at most 32 characters). Each has a
+  `name` and exactly 21 colours: `bg`, `fg`, `fg.bold`, `cursor`, `selection`, `black`, `red`, `green`, `yellow`,
+  `blue`, `magenta`, `cyan`, `white` and their `bright.` forms. A scheme's id is `<theme id>/<key>`.
+- A UI variant's own scheme, the one `match` uses, is the scheme with its key: `ui.dark` needs `terminal.dark` and
+  `ui.light` needs `terminal.light`. Other keys are extra schemes, such as Slate Steel's `high-contrast`.
+- `editor` has one entry per UI variant: `background`, `foreground`, `caret`, `selection`, `currentLine`, `gutter`,
+  `lineNumbers` and every syntax role (`keyword`, `type`, `function`, `variable`, `constant`, `number`, `string`,
+  `comment`, `doc_comment`, `annotation`, `operator`, `punctuation`, `tag`, `attribute`, `regex`, `error`). A value is a
+  colour, `#RRGGBB`, or a reference: `terminal.bg`, `terminal.fg`, `terminal.fg.bold`, `terminal.cursor`,
+  `terminal.selection`, `terminal.ansi.<colour>`, `terminal.ansi.bright.<colour>` (the variant's own scheme) or a role
+  such as `text.secondary`. `@ N%` lays it over the background at N per cent (not for `background` itself), and
+  `, italic` and `, bold` style a syntax role.
+- A file without `ui` (and so without `editor`) is a terminal-only theme.
+- Anything else, anything missing and any malformed colour stops the file from loading.
 
 ## The token rule
 
@@ -43,18 +98,23 @@ desk buddy's own artwork, decimal component literals (transparent fills, the edi
 black and white that shadows and inset lines are painted in. A role that carries opacity (`surface.scrim`,
 `border.subtle`, `text.onAccent.secondary`) is painted as given; treatments such as focus rings, hovers and tile tints
 are kit metrics applied to a role. Shadows are black at the strengths the colour reference gives. Plugins receive the
-same roles through the SDK.
+same roles through the SDK. Installed themes live in the themes folder, outside the scan.
 
 ## When something is wrong
 
-- An unknown `ui.appearance` or `terminal.scheme` value is reported ("Use …; using the default.") and uses the
-  default (`system`, `match`).
-- A removed setting (`[ui.theme]`, `terminal.colors`, `[colors]`) is reported as an unknown setting and ignored.
-- If Slate Steel cannot be installed, start-up uses Dark and a reload keeps the current appearance; Reload config
-  retries.
+- A value that is not a valid `ui.appearance`, theme id or scheme id is reported ("Use …; using the default.") and
+  the default is used (`system`, `slate-steel`, `match`).
+- A theme file that cannot be used is skipped and reported with the configuration's warnings, naming the file, the
+  line when known and the entry, for example `dracula.json: warning: ui.dark: Missing colour "accent"`.
+- A theme pick that names a theme that is not installed, or one without that variant, and a scheme that is not
+  installed, are reported on their setting and replaced by the default until the theme is installed again.
+- A removed setting (`terminal.colors`, `[colors]`) is reported as an unknown setting and ignored.
+- If a theme cannot be installed, start-up uses Slate Steel Dark and a reload keeps the current appearance; Reload
+  config retries.
 
 ## Native acceptance for this upgrade (user-run)
 
 On a Mac, check Dark, Light and Match System (switch the operating system's appearance with Settings and a sheet
 open), each terminal scheme including High Contrast, and compare the mocked pages at 100%; the
-[acceptance record](design/theme-engine/acceptance.md) lists every check.
+[acceptance record](design/theme-engine/acceptance.md) lists every check. With an installed theme, also check a Dark
+theme pick other than Slate Steel under Match System, and an installed terminal scheme on Slate Steel.
diff --git a/jasper-app/README.md b/jasper-app/README.md
index c5774437e84cd6131f0e8f19f195402c374c442d..6014cdeb27563b51ba17746fcf0d40b61a361a40 100644
--- a/jasper-app/README.md
+++ b/jasper-app/README.md
@@ -86,11 +86,11 @@ updating reflective class names in `.properties` files.
 
 ## Native theme engine
 
-The app runs on Slate Steel only. `ui.appearance` (system, dark, light) picks Slate Steel Dark or Light and
-`terminal.scheme` the terminal panes' colours; `application.ConfigurationController` applies both through
-`appearance.ThemeController`, with `application.AppearancePolicy` resolving `system` against the operating system.
-No themes folder is read and nothing is converted or upgraded at start-up; there is no other theme and no theme
-import. See [themes](../docs/themes.md) and
+The built-in theme is Slate Steel; installed themes are theme files in `<home>/themes/`, read by
+`appearance.ThemeLibrary`. `ui.appearance` (system, dark, light) picks the appearance, `ui.theme.dark` and
+`ui.theme.light` the theme each shows, and `terminal.scheme` the terminal panes' colours;
+`application.ConfigurationController` applies them through `appearance.ThemeController`, with
+`application.AppearancePolicy` resolving `system` against the operating system. See [themes](../docs/themes.md) and
 [maintenance](../docs/app-maintenance.md#themes).
 
 The [Plan 2 completion audit](../docs/design/theme-engine/completion.md) records SDK 0.15 migration,
diff --git a/jasper-ui/README.md b/jasper-ui/README.md
index 82c58fcb2270cad017b9680865deb7352e6032c2..c2c915ed00f08227ea646afb87c19b1ce17d31e8 100644
--- a/jasper-ui/README.md
+++ b/jasper-ui/README.md
@@ -6,8 +6,8 @@ and library APIs into these values; plugins use SDK contracts and host adapters.
 
 The engine is `dev.jasper.ui.theme`: `JasperTheme` reads a Jasper theme file (the bundled Slate Steel is `slate-steel.json`) and gives
 each variant's semantic palette, terminal and editor schemes, syntax styles, glass levels and FlatLaf UI keys
-(`UiColorKeys`, from the roles) as an immutable `GeneratedTheme`. There are no other themes and no theme files to
-parse. The app and plugins use this authority; [completion evidence](../docs/design/theme-engine/completion.md)
+(`UiColorKeys`, from the roles) as an immutable `GeneratedTheme`; the app reads installed theme files with the same
+loader. The app and plugins use this authority; [completion evidence](../docs/design/theme-engine/completion.md)
 separates source migration from Plan 3 composition.
 
 `TerminalColors` takes background before foreground and defensively copies exactly
JASPER_PATCH
`````

- [ ] **Step 2: Run the documentation tests**

Run: `./gradlew :jasper-app:test --tests "*AppDocumentationTest" --tests "*ConfigTemplateTest" :jasper-ui:test --tests "*UiDocumentationTest"`
Expected: PASS.

- [ ] **Step 3: Run the full check**

Run: `./gradlew check --continue`
Expected: `BUILD SUCCESSFUL`, about 9,790 tests, 0 failures. Count them from the XML:

```bash
python3 - <<'PY'
import glob, xml.etree.ElementTree as E
t = f = e = s = 0
for x in glob.glob("*/build/test-results/test/*.xml") + glob.glob("plugins/*/build/test-results/test/*.xml"):
    r = E.parse(x).getroot(); t += int(r.get("tests")); f += int(r.get("failures")); e += int(r.get("errors")); s += int(r.get("skipped"))
print(t, "tests,", f, "failures,", e, "errors,", s, "skipped")
PY
```

- [ ] **Step 4: Record the work in `docs/STATUS.md`**

Add at the top, below `# Jasper — Status and Handoff`, with Step 3's numbers:

```markdown
**Theme import Plan 1: the format and the runtime — 2026-10-13** (`claude/theme-import`;
[spec](superpowers/specs/2026-10-13-jasper-theme-import-design.md),
[plan](superpowers/plans/2026-10-13-theme-import-1-format-and-runtime.md)). One public theme format: jasper-ui's
`JasperTheme` reads any Jasper theme file and `slate-steel.json` is one (`"jasper-theme": 1`, variants `dark`/`light`,
named schemes, literal editor colours). `appearance.ThemeLibrary` reads `<home>/themes/*.json` at start-up and with
every forced or changed configuration read; `ui.theme.dark` and `ui.theme.light` pick the theme each appearance shows,
`terminal.scheme` is `match` or a scheme id (`slate-steel/high-contrast`); unusable picks and skipped files are
configuration warnings. Settings › Appearance has Dark theme and Light theme dropdowns and a Terminal scheme dropdown
over the installed schemes; the palette's "Terminal: …" commands follow them. Deviation: the spec's section 8 put the
dropdowns in Plan 2; they are here (Plan 2 is Import… and Remove). `./gradlew check --continue`: **N tests, 0 failures,
0 errors, S skipped**. No GUI launched. For the user: copy a theme file into the themes folder, Reload config, pick it.
Not merged or pushed.
```

- [ ] **Step 5: Commit**

```bash
git add -A
git commit -m "Document installed themes and the Jasper theme format

🤖 Generated with Claude Code at The Home Depot

Co-Authored-By: Claude <noreply@anthropic.com>"
```
