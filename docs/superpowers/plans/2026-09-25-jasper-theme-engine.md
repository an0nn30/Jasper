# Theme Engine and IntelliJ Light Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Status:** Executed subagent-driven on `claude/theme-engine`. All 7 tasks and the final-review fix wave are done, and `./gradlew check` passed (1897 tests, 0 failures, 0 errors, 3 skipped). Not merged, not pushed. The user checks Light and Dark visually.
- **Spike outcome (spec section 2):** IntelliJ Light and Jasper Dark matched every expected colour on the first run. The Step 6 dark-parent fallback was not needed, so the loader keeps IntelliJ's plain parent merge.
- **Deviations from the plan text:**
  - **Task 2:** `ThemeLoaderTest` casts to `Map<String, Object>` (with `@SuppressWarnings("unchecked")`), not `Map<?, ?>`. AssertJ's `containsEntry` cannot take `String` arguments on a wildcard capture. The assertion is unchanged.
  - **Task 4:** `ThemeManager.install` briefly unregistered and re-registered the legacy `dev.jasper.app.themes` custom-defaults source around `FlatLaf.setup`. FlatLaf merges JVM-global custom sources into every look and feel. Task 5 deleted this bracket along with the registration and the properties files.
  - **Task 7:** the verification grep also excludes `docs/superpowers/**` and `docs/STATUS.md`, because those are historical records.
  - **Final-review fix wave:**
    - `ToolWindowSurface` skips `CellRendererPane` subtrees and does not descend into `JList`, `JTable`, `JTree` or `JComboBox`. Cell renderers keep their selection colours, so the SSH hosts panel's selected row stays highlighted.
    - `SidePanelRenderTest` adds the spec section 5 screenshot-parity render under both themes.
    - `jasper-dark.theme.json` restores 52 values that FlatLaf's Darcula base had changed. It pins `@disabledBackground`, `@disabledForeground`, `@cellFocusColor`, `@menuHoverBackground`, `@menuAcceleratorForeground` and 12 explicit keys, and `TODAYS_DARK` checks all 52. It also drops `ComboBox.padding`, which FlatLaf ignores.
    - Two unused `Theme` imports are removed.
    - `docs/app-refactor-verification.md` lists the packaged theme resources.
- **Accepted Jasper Dark differences.** FlatLaf builds IntelliJ dark themes on `FlatDarculaLaf` plus `IntelliJTheme$ThemeLaf`, not on `FlatDarkLaf`. These differences from base `8d221b0f` remain, found by diffing every resolved colour and number:
  - **Ruled or specified:**
    - menu selection `#5e7293` with white text (decision 7);
    - `ComboBox.padding` and form-control heights (decision 6);
    - `Jasper.paletteSelectionBackground` and `Jasper.paletteSelectionForeground` (part 2b removes the palette keys);
    - the TermLab `Jasper.*` form keys, which the spec deletes.
  - **Set by FlatLaf after the theme, so a theme cannot pin them:**
    - `Spinner.background`, `EditorPane.background` and `TextPane.background` follow `TextField.background` (`#282c34`, was `#292c34`).
    - `ComboBox.editableBackground` also follows `TextField.background` (it was unset, so FlatLaf used `#333841`). The tab-height spinner and the Remote plugin's editable group combo therefore show the field colour.
    - `Desktop.background` is derived from the panel colour.
    - `TabbedPane.inactiveUnderlineColor`, `ToggleButton.selectedBackground` and `ToggleButton.disabledSelectedBackground` come from FlatLaf's `{*-dark}` rules.
  - **Not drawn by Jasper:**
    - the AWT system colours (`control`, `controlText`, `text`, `textText`, `window`, `windowText`, `windowBorder`, `desktop`, `activeCaptionText`, `inactiveCaptionText`);
    - internal frames, desktop icons, sliders, colour-chooser swatches and help buttons;
    - `TabbedPane.disabledUnderlineColor`, `TabbedPane.focus` and `TabbedPane.shadow`, because the terminal deck hides its tab area;
    - `ToggleButton.tab.disabledUnderlineColor`, because Jasper's toggles are toolbar buttons;
    - `ScrollBar.hoverButtonBackground` and `ScrollBar.pressedButtonBackground`, because scroll bar buttons are hidden;
    - `ProgressBar.selectionForeground`, because no progress bar paints its text;
    - `RootPane.activeBorderColor` and `RootPane.inactiveBorderColor`, which FlatLaf uses only for decorated frames without a native border (not on macOS, on Windows 10+ with FlatLaf's native library, or on undecorated Linux frames).
  - **New keys that change nothing:** `Button.shadowWidth` (`Button.paintShadow` is false) and the `Component.isIntelliJTheme` marker, which FlatLaf's delegates do not read.

**Goal:** Install every Jasper theme through one engine that reads IntelliJ `.theme.json` files: classic IntelliJ Light becomes the built-in Light, and today's dark look becomes `jasper-dark.theme.json`.

**Architecture:** `ThemeLoader` resolves a theme's `parentTheme` chain and named colours into one flat JSON for FlatLaf's `IntelliJTheme`. `ThemeManager` installs it, puts back the IntelliJ-only keys FlatLaf skips, and runs `ChromeKeys`, which derives Jasper's `Jasper.*` chrome keys from IntelliJ keys. `ThemeController` keeps its behaviour and uses `ThemeManager` as its installer. The `Theme` record replaces the `BuiltinTheme` enum. Side panels paint `ToolWindow.background`; the status bar paints `StatusBar.*`.

**Tech Stack:** Java 25 (JBR), Swing, FlatLaf 3.7 (`IntelliJTheme`, `com.formdev.flatlaf.json.Json`), JUnit 5, AssertJ.

**Spec:** `docs/superpowers/specs/2026-09-25-jasper-theme-engine-design.md`

**Planning evidence.** Before writing this plan, every FlatLaf behaviour below was checked against FlatLaf 3.7 in the session scratchpad, including a prototype of this plan's `ThemeLoader`, `ChromeKeys` and install path loading the real vendored files:
- `IntelliJTheme` ignores `parentTheme`.
- It fails with a NullPointerException on a theme without `author`.
- It skips the `EditorTabs.`, `MainToolbar.`, `SearchEverywhere.`, `StatusBar.`, `ToolWindow.`, `Borders.`, `Link.` and `Plugins.` namespaces.
- It applies wildcard and explicit keys in document order, as IntelliJ does.
- It rejects `$Key` references in colour values.
- It ignores a theme's `ComboBox.padding`.
- It copies `List.selectionBackground` into menu selection.
- It tries to instantiate `com.intellij.*` border class names.
- `FlatLaf.setExtraDefaults` values override everything and survive a reinstall of the same instance.
- `Json.parse` keeps key order and returns every scalar as a `String`.

Results from the prototype:
- **IntelliJ Light** resolves and installs with `Panel.background` #f2f2f2, white fields, `List.selectionBackground` #2675bf, `EditorTabs.underlineColor` #4083c9, `SearchEverywhere.Tab.selectedBackground` #dfdfdf and `ToolWindow.background` #ffffff.
- **Jasper Dark** as written in Task 4 reproduces 82 of the 82 checked colours of today's dark theme.

**Planning decisions** (rulings on points the spec leaves open; the spec is binding):
1. **The rail keeps `Jasper.titleBackground`.** Only the side panels paint `ToolWindow.background`. Today's dark rail (#23262c) and side panels (#21252b) differ, so one key cannot keep both, and the spec requires Dark to keep today's colours. IntelliJ's own stripes are chrome-coloured too.
2. **Unknown colour names fail only inside the `colors` table.** A `ui` value that names no colour passes through: Darcula uses IntelliJ's built-in palette name `Gray2`, and FlatLaf skips that key.
3. **The loader drops `com.intellij.*` values.** FlatLaf would otherwise try to load IntelliJ's border classes and lose its own scroll-pane and table borders.
4. **Derived text and stroke colours get a contrast floor.**
   - 3:1 for the inactive title against the title row, for muted text, for the split divider against the terminal background, and for the running dot against the status bar.
   - 4.5:1 for config status text against the status bar.

   The colour moves toward the theme's foreground in 5% steps until it meets the floor. A key the theme sets is never adjusted. Under IntelliJ Light, #999999 becomes #8a8a8a, and the status greens, yellows and reds darken.
5. **App defaults move into `ThemeManager.APP_DEFAULTS`.** These are the theme-independent settings in today's `FlatLaf.properties`: 12-point form typography, now including buttons, and the split divider's size and border class. They are applied to every theme as FlatLaf extra defaults. `Button.font` joins `ThemeController`'s configurable form fonts, replacing `BrandedButtonUI`'s font rule.
6. **Form-control heights follow FlatLaf.** FlatLaf has no minimum-height key and ignores a theme's `ComboBox.padding`. Jasper Dark buttons and combos become 21 px instead of 24; IntelliJ Light's are 25 px.
7. **Jasper Dark menus use the list selection colour (#5e7293).** FlatLaf copies it into menus. Menu selection text becomes white, for 4.9:1 contrast.
8. **`TabbedPane.selectedBackground` is no longer tied to the terminal palette under IntelliJ Light.** Its `ThemeControllerTest` assertion is removed; Jasper Dark still sets #282a36.
9. **The status bar gains a 1-px top rule** in `StatusBar.borderColor`, as the spec says. This is a small addition to Dark.

## Global Constraints

- **Build:** Java 25 on JBR 25, built with `./gradlew` only; `./gradlew check` must pass at the end of every task.
- **Libraries:** no new dependencies. JSON parsing uses FlatLaf's `com.formdev.flatlaf.json.Json`.
- **Vendored theme files:**
  - Byte-identical copies of `Light.theme.json`, `intellijlaf.theme.json` and `darcula.theme.json` from `JetBrains/intellij-community`, `platform/platform-resources/src/themes/`, commit `f7377708b654b73b206da40bb382ecb4d44e8f12`.
  - `LICENSE.txt` (canonical Apache-2.0) and `NOTICE.txt` are copies of `icons/intellij/LICENSE.txt` and `icons/intellij/NOTICE.txt`, which come from the same commit.
  - The user approved downloading these three theme files on 2026-09-25; nothing else is downloaded.
- **Built-in themes:** `Theme.LIGHT` has id `intellij-light` and name `IntelliJ Light`; `Theme.DARK` has id `jasper-dark` and name `Jasper Dark`. `ui.theme.variant` `"light"` → `Theme.LIGHT` and `"dark"` → `Theme.DARK`.
- **Terminal palettes are unchanged:** `Palette.jasperLight()` and `Palette.jasperDark()`, and `ui.theme.terminal` works as before.
- **No hard-coded colours:** no Java code outside tests carries a literal colour for a theme key. Derived keys only fill keys the theme left unset.
- **Contrast floors:** 3.0 and 4.5 exactly as in planning decision 4.
- **Package dependencies:** the SDK and plugins are unchanged. The `dev.jasper.app.appearance` package keeps its allowed dependencies (`dev.jasper.app.config`, `dev.jasper.app.lifecycle`, `dev.jasper.terminal.config`).
- **Agent limits:** never launch the GUI; the user checks light and dark visually.
- **Source hygiene:** no raw control, private-use or surrogate characters in Java source; run the AGENTS.md Python check.
- **Git:** work in `.worktrees/theme-engine` on branch `claude/theme-engine`. Never run git in the main checkout or use bare `git stash`.
- **Commits** end with:
  ```
  🤖 Generated with Claude Code at The Home Depot

  Co-Authored-By: Claude <noreply@anthropic.com>
  ```

## Review Focus

1. **A failed switch keeps the previous theme whole.** When an install fails and `ThemeController` rolls back by reinstalling the previous look-and-feel instance, that theme keeps its restored and derived keys. (Task 4: `restoredAndDerivedKeysSurviveReinstallingTheSameLookAndFeel`.)
2. **Nothing leaks between themes.** Switching Light → Dark leaves none of Light's restored IntelliJ keys behind. (Task 4: `switchingThemesLeavesNothingOfThePreviousOneBehind`.)
3. **Plugin panels stay painted.** Content added after a panel opens, and content reset by a theme switch, still paints `ToolWindow.background`, while plugin-set backgrounds are kept. (Task 6: `ToolWindowSurfaceTest`.)
4. **Derived text stays readable** for a theme that sets only FlatLaf's own keys. (Task 3: `derivedTextAndStrokesMeetJaspersContrastFloor`.)
5. **Real-world theme values work:** per-OS objects, `#RRGGBBAA` alpha, IntelliJ class names and IntelliJ palette names. (Task 2: `intellijImplementationClassesAreDropped`, `valuesThatNameNoThemeColourPassThroughUnchanged`, `explicitColoursApplyWildcardsInOrderAndKeepAlpha`, `namedColoursResolveThroughOtherNamesIncludingPerOsValues`.)

## File Structure

- **Create** `tools/themes/fetch-intellij-themes.py`: vendors the three theme files and the manifest.
- **Create** `jasper-app/src/main/resources/dev/jasper/app/themes/intellij/`: `Light.theme.json`, `intellijlaf.theme.json`, `darcula.theme.json`, `assets.tsv`, `LICENSE.txt`, `NOTICE.txt`, `SOURCE.md`.
- **Create** `jasper-app/src/main/resources/dev/jasper/app/themes/jasper-dark.theme.json`.
- **Create** in `jasper-app/src/main/java/dev/jasper/app/appearance/`:
  - `ThemeLoader.java`: chain, named colours, flat JSON;
  - `ChromeKeys.java`: `Jasper.*` derivation;
  - `Theme.java`: built-in theme record;
  - `ThemeManager.java`: catalogue, install, put-back, app defaults.
- **Delete** `appearance/BuiltinTheme.java`, `appearance/BrandedButtonUI.java` and `resources/dev/jasper/app/themes/{FlatLaf,FlatLightLaf,FlatDarkLaf}.properties`.
- **Modify** `appearance/ThemeController.java` (installer, fonts), `ThemeState.java` and `ResolvedTheme.java` (rename), and `application/JasperApplication.java` (`dark()`).
- **Create** `workspace/ToolWindowSurface.java`. **Modify** `workspace/WorkspaceRegions.java`, `workspace/WindowContent.java`, `workspace/WindowStatusBar.java` and `workspace/WindowChrome.java`.
- **Tests, created:** `appearance/IntellijThemesTest`, `ThemeLoaderTest`, `ChromeKeysTest`, `ThemeManagerTest`, `ThemeButtonsTest`, and `workspace/ToolWindowSurfaceTest`.
- **Tests, deleted:** `appearance/BrandedButtonsTest`.
- **Tests, modified:** every file naming `BuiltinTheme` (mechanical rename), plus `WindowChromeTest`, `JasperApplicationPluginsTest`, `PackagedResourcesTest`, `MacTitleBarTest`, `ThemeControllerTest`, `CommandPaletteShortcutsTest`, `ApplicationActionsTest`, `ConfigurationStatusTest` and `CommandPalettePreview`.
- **Docs:** `docs/configuration.md`, `docs/sdk-architecture.md`, `docs/plugin-authoring.md`, `docs/credential-vault.md`, `docs/app-architecture.md`, `docs/app-maintenance.md`, `docs/STATUS.md`.

---

### Task 1: Vendor the classic IntelliJ Light theme chain

**Files:**
- Create: `tools/themes/fetch-intellij-themes.py`
- Create: `jasper-app/src/main/resources/dev/jasper/app/themes/intellij/SOURCE.md` (the script writes the rest of that directory)
- Test: `jasper-app/src/test/java/dev/jasper/app/appearance/IntellijThemesTest.java`

**Interfaces:**
- Produces: the resources `dev/jasper/app/themes/intellij/Light.theme.json` (name "IntelliJ Light", parentTheme "IntelliJ"), `intellij/intellijlaf.theme.json` (name "IntelliJ", parentTheme "Darcula") and `intellij/darcula.theme.json` (name "Darcula"), plus `assets.tsv`, `LICENSE.txt`, `NOTICE.txt` and `SOURCE.md`.

- [ ] **Step 1: Write the failing provenance test**

Create `jasper-app/src/test/java/dev/jasper/app/appearance/IntellijThemesTest.java`:

```java
package dev.jasper.app.appearance;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class IntellijThemesTest {
    private static final String BASE = "dev/jasper/app/themes/intellij/";
    private static final Map<String, String> PINNED = Map.of(
        "Light.theme.json", "429151594b8a013938c3c66dd3a5121702182c028ad87ae1e68bc4243cc1c68d",
        "intellijlaf.theme.json", "4450e2a85381b7b644c73bc9ec21f898aa55b184fc80f109a43ae9ff67af21ba",
        "darcula.theme.json", "858efbd6afdb12f26b4757c4ff7b1bccb8bdefc286797fa6344769496b6c59cb");

    @Test void manifestPinsTheClassicLightChainToItsUpstreamFiles() throws Exception {
        List<String> rows = text("assets.tsv").lines().skip(1).toList();
        assertThat(rows).hasSize(PINNED.size());
        for (String row : rows) {
            String[] fields = row.split("\t");
            assertThat(fields).as(row).hasSize(3);
            assertThat(fields[1]).isEqualTo("platform/platform-resources/src/themes/" + fields[0]);
            assertThat(fields[2]).as(fields[0]).isEqualTo(PINNED.get(fields[0]));
            assertThat(sha256(bytes(fields[0]))).as(fields[0]).isEqualTo(fields[2]);
        }
    }

    @Test void licenseSourceAndNoticeTravelWithTheThemes() throws Exception {
        assertThat(text("LICENSE.txt").stripLeading()).startsWith("Apache License").contains("Version 2.0, January 2004");
        assertThat(text("NOTICE.txt")).isNotBlank();
        assertThat(text("SOURCE.md")).contains("f7377708b654b73b206da40bb382ecb4d44e8f12");
    }

    private static byte[] bytes(String name) throws Exception {
        try (var in = IntellijThemesTest.class.getClassLoader().getResourceAsStream(BASE + name)) {
            assertThat(in).as(name).isNotNull();
            return in.readAllBytes();
        }
    }

    private static String text(String name) throws Exception { return new String(bytes(name), StandardCharsets.UTF_8); }

    private static String sha256(byte[] data) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(data));
    }
}
```

- [ ] **Step 2: Run it to verify it fails**

Run: `./gradlew :jasper-app:test --tests dev.jasper.app.appearance.IntellijThemesTest`
Expected: FAIL; the `assets.tsv` stream is null (`[assets.tsv] Expecting actual not to be null`).

- [ ] **Step 3: Write the fetch script**

Create `tools/themes/fetch-intellij-themes.py`:

```python
#!/usr/bin/env python3
"""Vendors the classic IntelliJ Light theme chain at a pinned intellij-community commit.

Run from the repository root. Rewrites themes/intellij/*.theme.json and assets.tsv, and copies
LICENSE.txt and NOTICE.txt from the IntelliJ icons vendored at the same commit (see
tools/icons/fetch-intellij-icons.py for why LICENSE.txt is the canonical Apache-2.0 text).
A missing file (HTTP 404) fails the run.
"""
import hashlib
import pathlib
import shutil
import urllib.request

SHA = "f7377708b654b73b206da40bb382ecb4d44e8f12"
BASE = f"https://raw.githubusercontent.com/JetBrains/intellij-community/{SHA}/"
SOURCE = "platform/platform-resources/src/themes/"
OUT = pathlib.Path("jasper-app/src/main/resources/dev/jasper/app/themes/intellij")
ICONS = pathlib.Path("jasper-app/src/main/resources/dev/jasper/app/icons/intellij")
# IntelliJ Light -> IntelliJ -> Darcula: each file names the next as its parentTheme.
THEMES = ["Light.theme.json", "intellijlaf.theme.json", "darcula.theme.json"]

OUT.mkdir(parents=True, exist_ok=True)
rows = ["resource\tsource\tsha256"]
for name in THEMES:
    with urllib.request.urlopen(BASE + SOURCE + name) as response:
        data = response.read()
    (OUT / name).write_bytes(data)
    rows.append(f"{name}\t{SOURCE}{name}\t{hashlib.sha256(data).hexdigest()}")
(OUT / "assets.tsv").write_text("\n".join(rows) + "\n", encoding="utf-8")
for name in ("LICENSE.txt", "NOTICE.txt"):
    shutil.copyfile(ICONS / name, OUT / name)
print(f"{len(THEMES)} themes from {SHA}")
```

- [ ] **Step 4: Run the script and write SOURCE.md**

Run: `python3 tools/themes/fetch-intellij-themes.py`
Expected: `3 themes from f7377708b654b73b206da40bb382ecb4d44e8f12`.

Create `jasper-app/src/main/resources/dev/jasper/app/themes/intellij/SOURCE.md`:

```markdown
# Classic IntelliJ Light theme files

Source: https://github.com/JetBrains/intellij-community, directory `platform/platform-resources/src/themes`.
Commit: f7377708b654b73b206da40bb382ecb4d44e8f12, fetched 2026-09-25 with
`tools/themes/fetch-intellij-themes.py`.
License: Apache License 2.0 (LICENSE.txt, the canonical text); upstream notice in NOTICE.txt. Both
are copies of the files vendored with the IntelliJ icons from the same commit.

Files are byte-identical copies; assets.tsv records each file's upstream path and SHA-256.
`Light.theme.json` ("IntelliJ Light") names `intellijlaf.theme.json` ("IntelliJ") as its
parentTheme, which names `darcula.theme.json` ("Darcula"). Jasper's ThemeLoader resolves that
chain. These are the classic-UI themes, not the New UI ones under `themes/expUI/`.
```

- [ ] **Step 5: Run the test to verify it passes**

Run: `./gradlew :jasper-app:test --tests dev.jasper.app.appearance.IntellijThemesTest`
Expected: PASS (2 tests).

- [ ] **Step 6: Commit**

```bash
git add tools/themes/fetch-intellij-themes.py jasper-app/src/main/resources/dev/jasper/app/themes/intellij jasper-app/src/test/java/dev/jasper/app/appearance/IntellijThemesTest.java
git commit -m "feat(appearance): vendor the classic IntelliJ Light theme chain

🤖 Generated with Claude Code at The Home Depot

Co-Authored-By: Claude <noreply@anthropic.com>"
```

---

### Task 2: ThemeLoader

**Files:**
- Create: `jasper-app/src/main/java/dev/jasper/app/appearance/ThemeLoader.java`
- Test: `jasper-app/src/test/java/dev/jasper/app/appearance/ThemeLoaderTest.java`

**Interfaces:**
- Produces:
  - `ThemeLoader(Function<String, String> read, Map<String, String> filesByName)`: `read` returns a file's JSON text or null; `filesByName` maps a theme `name` to its file for `parentTheme` lookups.
  - `ThemeLoader.Resolved load(String file)`.
  - `record ThemeLoader.Resolved(String name, boolean dark, String json, Map<String, Color> colors)`: `json` is FlatLaf input; `colors` holds every explicit colour after in-order wildcards.
  - `final class ThemeLoader.ThemeException extends IllegalArgumentException`.
  - `static Color color(Object value)`, which returns null for a non-colour.

- [ ] **Step 1: Write the failing tests**

Create `jasper-app/src/test/java/dev/jasper/app/appearance/ThemeLoaderTest.java`:

```java
package dev.jasper.app.appearance;

import com.formdev.flatlaf.json.Json;
import java.awt.Color;
import java.io.StringReader;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class ThemeLoaderTest {
    private static ThemeLoader loader(Map<String, String> files) {
        return new ThemeLoader(files::get,
            Map.of("Parent", "parent.json", "Child", "child.json", "Loop A", "a.json", "Loop B", "b.json"));
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> json(ThemeLoader.Resolved resolved) throws Exception {
        return (Map<String, Object>) Json.parse(new StringReader(resolved.json()));
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> ui(ThemeLoader.Resolved resolved) throws Exception {
        return (Map<String, Object>) json(resolved).get("ui");
    }

    @Test void childEntriesReplaceTheParentsAndFollowTheChildsOrder() throws Exception {
        var resolved = loader(Map.of(
            "parent.json", """
                {"name": "Parent", "ui": {"A.x": "#111111", "*.background": "#222222", "B.y": "#333333"}}""",
            "child.json", """
                {"name": "Child", "parentTheme": "Parent", "ui": {"*.background": "#444444", "A.x": "#555555"}}"""))
            .load("child.json");
        var ui = ui(resolved);
        assertThat(List.copyOf(ui.keySet())).containsExactly("B.y", "*.background", "A.x");
        assertThat(ui).containsEntry("*.background", "#444444").containsEntry("A.x", "#555555").containsEntry("B.y", "#333333");
        assertThat(resolved.name()).isEqualTo("Child");
    }

    @Test void nestedObjectsAndDottedKeysNameTheSameKey() throws Exception {
        var ui = ui(loader(Map.of(
            "parent.json", """
                {"name": "Parent", "ui": {"Button": {"arc": 3, "default": {"foreground": "#ffffff"}}}}""",
            "child.json", """
                {"name": "Child", "parentTheme": "Parent", "ui": {"Button.arc": 5}}"""))
            .load("child.json"));
        assertThat(ui).containsOnlyKeys("Button.default.foreground", "Button.arc").containsEntry("Button.arc", "5");
    }

    @Test void namedColoursResolveThroughOtherNamesIncludingPerOsValues() throws Exception {
        var resolved = loader(Map.of("child.json", """
            {"name": "Child", "colors": {"grey15": "#F2F2F2", "panel": "grey15"},
             "ui": {"Panel.background": "panel", "Label.foreground": {"os.mac": "panel", "os.default": "#000000"}}}"""))
            .load("child.json");
        var ui = ui(resolved);
        assertThat(ui).containsEntry("Panel.background", "#f2f2f2");
        assertThat((Map<?, ?>) ui.get("Label.foreground")).containsEntry("os.mac", "#f2f2f2").containsEntry("os.default", "#000000");
        assertThat(resolved.colors()).containsEntry("Panel.background", new Color(0xf2f2f2));
    }

    @Test void valuesThatNameNoThemeColourPassThroughUnchanged() throws Exception {
        var resolved = loader(Map.of("child.json", """
            {"name": "Child", "colors": {"panel": "#f2f2f2"},
             "ui": {"WelcomeScreen.defaultBackground": "Gray2", "TabbedPane.tabFillStyle": "underline"}}"""))
            .load("child.json");
        assertThat(ui(resolved)).containsEntry("WelcomeScreen.defaultBackground", "Gray2").containsEntry("TabbedPane.tabFillStyle", "underline");
        assertThat(resolved.colors()).isEmpty();
    }

    @Test void intellijImplementationClassesAreDropped() throws Exception {
        var ui = ui(loader(Map.of("child.json", """
            {"name": "Child", "ui": {"Button.UI": "com.intellij.ide.ui.laf.darcula.ui.DarculaButtonUI",
              "ScrollPane.border": "com.intellij.ide.ui.laf.darcula.ui.DarculaScrollPaneBorder",
              "InternalFrame.border": {"os.windows": "com.intellij.ide.ui.laf.darcula.ui.DarculaInternalBorder"},
              "Panel.background": "#f2f2f2"}}"""))
            .load("child.json"));
        assertThat(ui).containsOnlyKeys("Panel.background");
    }

    @Test void explicitColoursApplyWildcardsInOrderAndKeepAlpha() {
        var resolved = loader(Map.of("child.json", """
            {"name": "Child", "ui": {"SearchEverywhere.Header.background": "#111111", "*.background": "#222222",
              "ToolWindow.background": "#333333", "EditorTabs.hoverBackground": "#00000019", "@accentBaseColor": "#61afef"}}"""))
            .load("child.json");
        assertThat(resolved.colors())
            .containsEntry("SearchEverywhere.Header.background", new Color(0x222222))
            .containsEntry("ToolWindow.background", new Color(0x333333))
            .containsEntry("EditorTabs.hoverBackground", new Color(0, 0, 0, 0x19))
            .doesNotContainKeys("*.background", "@accentBaseColor");
    }

    @Test void aThemeWithoutAuthorOrDarkFlagIsStillAValidFlatLafInput() throws Exception {
        var resolved = loader(Map.of("child.json", """
            {"name": "Child", "ui": {}}""")).load("child.json");
        // FlatLaf 3.7 fails on a theme without an author; its parser returns every scalar as a String.
        assertThat(json(resolved)).containsEntry("author", "").containsEntry("dark", "false");
        assertThat(resolved.dark()).isFalse();
        assertThat(loader(Map.of("child.json", """
            {"name": "Child", "dark": true}""")).load("child.json").dark()).isTrue();
    }

    @Test void brokenThemesFailNamingTheFileAndTheKey() {
        assertThatThrownBy(() -> loader(Map.of()).load("missing.json"))
            .isInstanceOf(ThemeLoader.ThemeException.class).hasMessageContaining("missing.json");
        assertThatThrownBy(() -> loader(Map.of("child.json", """
            {"name": "Child", "parentTheme": "Nobody"}""")).load("child.json"))
            .isInstanceOf(ThemeLoader.ThemeException.class).hasMessageContaining("child.json").hasMessageContaining("Nobody");
        assertThatThrownBy(() -> loader(Map.of(
            "a.json", """
                {"name": "Loop A", "parentTheme": "Loop B"}""",
            "b.json", """
                {"name": "Loop B", "parentTheme": "Loop A"}""")).load("a.json"))
            .isInstanceOf(ThemeLoader.ThemeException.class).hasMessageContaining("a.json -> b.json -> a.json");
        assertThatThrownBy(() -> loader(Map.of("child.json", """
            {"name": "Child", "colors": {"panel": "grey99"}}""")).load("child.json"))
            .isInstanceOf(ThemeLoader.ThemeException.class).hasMessageContaining("child.json")
            .hasMessageContaining("\"panel\"").hasMessageContaining("\"grey99\"");
        assertThatThrownBy(() -> loader(Map.of("child.json", """
            {"name": "Child", "colors": {"a": "b", "b": "a"}}""")).load("child.json"))
            .isInstanceOf(ThemeLoader.ThemeException.class).hasMessageContaining("cycle");
        assertThatThrownBy(() -> loader(Map.of("child.json", "{\"name\": ")).load("child.json"))
            .isInstanceOf(ThemeLoader.ThemeException.class).hasMessageContaining("child.json");
    }
}
```

- [ ] **Step 2: Run them to verify they fail**

Run: `./gradlew :jasper-app:test --tests dev.jasper.app.appearance.ThemeLoaderTest`
Expected: FAIL to compile; `cannot find symbol: class ThemeLoader`.

- [ ] **Step 3: Implement ThemeLoader**

Create `jasper-app/src/main/java/dev/jasper/app/appearance/ThemeLoader.java`:

```java
package dev.jasper.app.appearance;

import com.formdev.flatlaf.json.Json;
import com.formdev.flatlaf.json.ParseException;
import com.formdev.flatlaf.util.SystemInfo;
import java.awt.Color;
import java.io.IOException;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.regex.Pattern;

/**
 * Resolves an IntelliJ {@code .theme.json} and its {@code parentTheme} chain into one flat theme for
 * FlatLaf. Parent entries come first and a child's entries replace them in the child's order, as in
 * IntelliJ, so FlatLaf's in-order wildcard handling gives IntelliJ's precedence. Named colours are
 * resolved; the explicit colours are also returned because FlatLaf skips IntelliJ-only namespaces.
 */
final class ThemeLoader {
    /** A resolved theme: FlatLaf's JSON input and every explicit colour, keyed as the theme names it. */
    record Resolved(String name, boolean dark, String json, Map<String, Color> colors) {
        Resolved { colors = Map.copyOf(colors); }
    }

    /** A theme that cannot be resolved; the message names the theme file and the offending key. */
    static final class ThemeException extends IllegalArgumentException {
        ThemeException(String message) { super(message); }
        ThemeException(String message, Throwable cause) { super(message, cause); }
    }

    private record Raw(String name, boolean dark, String author,
                       LinkedHashMap<String, Object> colors, LinkedHashMap<String, Object> ui, LinkedHashMap<String, Object> icons) {
        Raw over(Raw parent) {
            return new Raw(name, dark, author, layered(parent.colors, colors), layered(parent.ui, ui), layered(parent.icons, icons));
        }

        private static LinkedHashMap<String, Object> layered(Map<String, Object> parent, Map<String, Object> child) {
            var result = new LinkedHashMap<>(parent);
            child.forEach((key, value) -> { result.remove(key); result.put(key, value); });
            return result;
        }
    }

    private static final Pattern HEX = Pattern.compile("#?([0-9a-fA-F]{6}|[0-9a-fA-F]{8})|#[0-9a-fA-F]{3}");

    private final Function<String, String> read;
    private final Map<String, String> filesByName;

    /**
     * @param read returns a theme file's JSON text, or null when the file does not exist
     * @param filesByName the theme file for each theme {@code name} a {@code parentTheme} may name
     */
    ThemeLoader(Function<String, String> read, Map<String, String> filesByName) {
        this.read = Objects.requireNonNull(read);
        this.filesByName = Map.copyOf(filesByName);
    }

    Resolved load(String file) {
        Raw theme = resolve(file, new ArrayList<>());
        Map<String, String> colors = resolveColors(file, theme.colors);
        var ui = new LinkedHashMap<String, Object>();
        theme.ui.forEach((key, value) -> { if (!intellijClass(value)) ui.put(key, named(value, colors)); });
        var json = new LinkedHashMap<String, Object>();
        json.put("name", theme.name);
        json.put("dark", theme.dark);
        // FlatLaf 3.7 fails on a theme without an author.
        json.put("author", theme.author);
        json.put("colors", new LinkedHashMap<String, Object>(colors));
        json.put("ui", ui);
        if (!theme.icons.isEmpty()) json.put("icons", theme.icons);
        return new Resolved(theme.name, theme.dark, write(json), explicitColors(ui));
    }

    private Raw resolve(String file, List<String> chain) {
        if (chain.contains(file)) throw new ThemeException("Theme parents form a cycle: " + String.join(" -> ", chain) + " -> " + file);
        chain.add(file);
        String text = read.apply(file);
        if (text == null) throw new ThemeException("Theme file not found: " + file);
        Map<String, Object> json;
        try {
            if (!(Json.parse(new StringReader(text)) instanceof Map<?, ?> map)) throw new ThemeException(file + ": a theme must be a JSON object");
            json = strings(map);
        } catch (IOException | ParseException failure) {
            throw new ThemeException(file + ": " + failure.getMessage(), failure);
        }
        // FlatLaf's parser returns every scalar, booleans included, as a String.
        var own = new Raw(json.get("name") instanceof String name ? name : file, "true".equals(String.valueOf(json.get("dark"))),
            json.get("author") instanceof String author ? author : "",
            flatten(object(json, "colors", file)), flatten(object(json, "ui", file)), new LinkedHashMap<>(object(json, "icons", file)));
        if (!(json.get("parentTheme") instanceof String parent)) return own;
        String parentFile = filesByName.get(parent);
        if (parentFile == null) throw new ThemeException(file + ": unknown parentTheme \"" + parent + "\"");
        return own.over(resolve(parentFile, chain));
    }

    /** Resolves every entry of the merged colour table to a literal; names may refer to other names. */
    private static Map<String, String> resolveColors(String file, Map<String, Object> table) {
        var resolved = new LinkedHashMap<String, String>();
        for (String name : table.keySet()) resolved.put(name, resolveColor(file, table, name, new ArrayList<>()));
        return resolved;
    }

    private static String resolveColor(String file, Map<String, Object> table, String name, List<String> path) {
        if (path.contains(name)) throw new ThemeException(file + ": colours form a cycle: " + String.join(" -> ", path) + " -> " + name);
        path.add(name);
        Object value = table.get(name);
        if (!(value instanceof String text)) throw new ThemeException(file + ": colour \"" + name + "\" is not a string");
        if (HEX.matcher(text).matches()) return normalized(text);
        if (!table.containsKey(text)) throw new ThemeException(file + ": colour \"" + name + "\" refers to unknown colour \"" + text + "\"");
        return resolveColor(file, table, text, path);
    }

    /** IntelliJ's own UI, border and painter classes, which Jasper cannot load; FlatLaf would try to. */
    private static boolean intellijClass(Object value) {
        return value instanceof String text ? text.startsWith("com.intellij.")
            : value instanceof Map<?, ?> perOs && perOs.values().stream().anyMatch(ThemeLoader::intellijClass);
    }

    /** A value naming a colour becomes that colour; other values, including IntelliJ palette names, pass through. */
    private static Object named(Object value, Map<String, String> colors) {
        if (value instanceof String text && colors.containsKey(text)) return colors.get(text);
        if (value instanceof Map<?, ?> perOs) {
            var result = new LinkedHashMap<String, Object>();
            perOs.forEach((key, inner) -> result.put(key.toString(), named(inner, colors)));
            return result;
        }
        return value;
    }

    /**
     * The colour of every explicit key, applied in order. A {@code *.suffix} wildcard recolours the keys
     * recorded so far with that suffix, the way IntelliJ applies it to the defaults present at that point.
     */
    private static Map<String, Color> explicitColors(Map<String, Object> ui) {
        var colors = new LinkedHashMap<String, Color>();
        ui.forEach((key, raw) -> {
            if (key.startsWith("@")) return;
            Color color = color(forThisOs(raw));
            if (color == null) return;
            if (key.startsWith("*.")) {
                String suffix = key.substring(1);
                colors.replaceAll((existing, previous) -> existing.endsWith(suffix) ? color : previous);
            } else colors.put(key, color);
        });
        return colors;
    }

    private static Object forThisOs(Object value) {
        if (!(value instanceof Map<?, ?> perOs)) return value;
        String os = SystemInfo.isMacOS ? "os.mac" : SystemInfo.isWindows ? "os.windows" : SystemInfo.isLinux ? "os.linux" : "";
        return perOs.containsKey(os) ? perOs.get(os) : perOs.get("os.default");
    }

    /** A {@code #RGB}, {@code #RRGGBB} or {@code #RRGGBBAA} colour (the {@code #} optional for the long forms), or null. */
    static Color color(Object value) {
        if (!(value instanceof String text) || !HEX.matcher(text).matches()) return null;
        String hex = normalized(text).substring(1);
        long rgba = Long.parseLong(hex.length() == 6 ? hex + "ff" : hex, 16);
        return new Color((int) (rgba >> 24) & 0xff, (int) (rgba >> 16) & 0xff, (int) (rgba >> 8) & 0xff, (int) rgba & 0xff);
    }

    private static String normalized(String text) {
        String hex = text.startsWith("#") ? text.substring(1) : text;
        if (hex.length() == 3) hex = "" + hex.charAt(0) + hex.charAt(0) + hex.charAt(1) + hex.charAt(1) + hex.charAt(2) + hex.charAt(2);
        return "#" + hex.toLowerCase(Locale.ROOT);
    }

    /** Nested objects become dotted keys in document order; per-OS objects ({@code os.*} keys) stay values. */
    private static LinkedHashMap<String, Object> flatten(Map<String, Object> source) {
        var result = new LinkedHashMap<String, Object>();
        flatten("", source, result);
        return result;
    }

    private static void flatten(String prefix, Map<String, Object> source, Map<String, Object> result) {
        source.forEach((key, value) -> {
            String path = prefix.isEmpty() ? key : prefix + "." + key;
            if (value instanceof Map<?, ?> nested && !perOs(nested)) flatten(path, strings(nested), result);
            else { result.remove(path); result.put(path, value); }
        });
    }

    private static boolean perOs(Map<?, ?> map) {
        return !map.isEmpty() && map.keySet().stream().allMatch(key -> key.toString().startsWith("os."));
    }

    private static Map<String, Object> object(Map<String, Object> json, String key, String file) {
        Object value = json.get(key);
        if (value == null) return Map.of();
        if (!(value instanceof Map<?, ?> map)) throw new ThemeException(file + ": \"" + key + "\" must be an object");
        return strings(map);
    }

    private static Map<String, Object> strings(Map<?, ?> map) {
        var result = new LinkedHashMap<String, Object>();
        map.forEach((key, value) -> result.put(key.toString(), value));
        return result;
    }

    private static String write(Object value) {
        var out = new StringBuilder();
        write(value, out);
        return out.toString();
    }

    private static void write(Object value, StringBuilder out) {
        if (value instanceof Map<?, ?> map) {
            out.append('{');
            boolean first = true;
            for (var entry : map.entrySet()) {
                if (!first) out.append(',');
                first = false;
                write(entry.getKey().toString(), out);
                out.append(':');
                write(entry.getValue(), out);
            }
            out.append('}');
        } else if (value instanceof List<?> list) {
            out.append('[');
            for (int i = 0; i < list.size(); i++) { if (i > 0) out.append(','); write(list.get(i), out); }
            out.append(']');
        } else if (value instanceof String text) {
            out.append('"');
            for (char c : text.toCharArray()) {
                if (c == '"' || c == '\\') out.append('\\').append(c);
                else if (c < 0x20) out.append(String.format("\\u%04x", (int) c));
                else out.append(c);
            }
            out.append('"');
        } else out.append(value);
    }
}
```

- [ ] **Step 4: Run the tests to verify they pass**

Run: `./gradlew :jasper-app:test --tests dev.jasper.app.appearance.ThemeLoaderTest`
Expected: PASS (8 tests).

- [ ] **Step 5: Commit**

```bash
git add jasper-app/src/main/java/dev/jasper/app/appearance/ThemeLoader.java jasper-app/src/test/java/dev/jasper/app/appearance/ThemeLoaderTest.java
git commit -m "feat(appearance): resolve IntelliJ theme chains for FlatLaf

🤖 Generated with Claude Code at The Home Depot

Co-Authored-By: Claude <noreply@anthropic.com>"
```

---

### Task 3: ChromeKeys

**Files:**
- Create: `jasper-app/src/main/java/dev/jasper/app/appearance/ChromeKeys.java`
- Test: `jasper-app/src/test/java/dev/jasper/app/appearance/ChromeKeysTest.java`

**Interfaces:**
- Produces:
  - `static Map<String, Color> ChromeKeys.derive(UIDefaults defaults, Color terminalBackground)`: fills the missing keys and returns the ones it added.
  - `static final List<String> ChromeKeys.KEYS`: every key it can fill.
  - `static Color readable(Color color, Color background, double minimum, Color toward)`.
  - `static Color mix(Color top, Color bottom, double amount)`.
  - `static double contrast(Color a, Color b)`.

- [ ] **Step 1: Write the failing tests**

Create `jasper-app/src/test/java/dev/jasper/app/appearance/ChromeKeysTest.java`:

```java
package dev.jasper.app.appearance;

import java.awt.Color;
import java.util.List;
import javax.swing.UIDefaults;
import javax.swing.plaf.ColorUIResource;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class ChromeKeysTest {
    private static final Color TERMINAL = new Color(0xfafafa);

    /** Only keys FlatLaf itself always defines, with light values. */
    private static UIDefaults flatLafOnly() {
        var d = new UIDefaults();
        put(d, "Label.foreground", 0x000000); put(d, "Label.disabledForeground", 0x8c8c8c);
        put(d, "TitlePane.background", 0xf2f2f2); put(d, "TitlePane.foreground", 0x6e6e6e);
        put(d, "TitlePane.inactiveForeground", 0x999999); put(d, "Separator.foreground", 0xd1d1d1);
        put(d, "Panel.background", 0xf2f2f2); put(d, "TabbedPane.hoverColor", 0xd9d9d9);
        put(d, "TabbedPane.underlineColor", 0x4083c9); put(d, "TabbedPane.inactiveUnderlineColor", 0x9ca7b8);
        put(d, "TextField.background", 0xffffff); put(d, "Actions.Red", 0xdb5860);
        put(d, "Actions.Green", 0x59a869); put(d, "Actions.Yellow", 0xeda200);
        put(d, "Component.borderColor", 0xc4c4c4); put(d, "Component.focusedBorderColor", 0x87afda);
        put(d, "List.selectionBackground", 0x2675bf); put(d, "List.selectionForeground", 0xffffff);
        return d;
    }

    private static void put(UIDefaults d, String key, int rgb) { d.put(key, new ColorUIResource(rgb)); }

    @Test void flatLafsOwnKeysAreEnoughForEveryChromeKey() {
        var defaults = flatLafOnly();
        var added = ChromeKeys.derive(defaults, TERMINAL);
        assertThat(added).containsOnlyKeys(ChromeKeys.KEYS);
        for (String key : ChromeKeys.KEYS) assertThat(defaults.getColor(key)).as(key).isNotNull();
        assertThat(defaults.getColor("Jasper.titleBackground")).isEqualTo(new Color(0xf2f2f2));
        assertThat(defaults.getColor("Jasper.titleSeparator")).isEqualTo(new Color(0xd1d1d1));
        assertThat(defaults.getColor("Jasper.chromeForeground")).isEqualTo(new Color(0x000000));
        assertThat(defaults.getColor("Jasper.tabSelectedBackground")).isEqualTo(new Color(0xf2f2f2));
        assertThat(defaults.getColor("Jasper.tabHoverBackground")).isEqualTo(new Color(0xd9d9d9));
        assertThat(defaults.getColor("Jasper.tabUnderline")).isEqualTo(new Color(0x4083c9));
        assertThat(defaults.getColor("Jasper.findErrorBackground")).isEqualTo(ChromeKeys.mix(new Color(0xdb5860), Color.WHITE, .12));
        assertThat(defaults.getColor("Jasper.paletteSelectionBackground")).isEqualTo(new Color(0x2675bf));
    }

    @Test void intellijKeysWinOverFlatLafFallbacks() {
        var defaults = flatLafOnly();
        put(defaults, "MainToolbar.background", 0xeeeeee); put(defaults, "MainToolbar.foreground", 0x111111);
        put(defaults, "Borders.color", 0xcccccc); put(defaults, "Label.infoForeground", 0x707070);
        put(defaults, "EditorTabs.underlinedTabBackground", 0xffffff); put(defaults, "EditorTabs.underlinedTabForeground", 0x222222);
        defaults.put("EditorTabs.hoverBackground", new ColorUIResource(new Color(0, 0, 0, 0x19)));
        put(defaults, "EditorTabs.underlineColor", 0x3574f0); put(defaults, "EditorTabs.inactiveUnderlineColor", 0xa0a0a0);
        put(defaults, "SearchField.errorBackground", 0xffcccc); put(defaults, "StatusBar.background", 0xf7f7f7);
        ChromeKeys.derive(defaults, TERMINAL);
        assertThat(defaults.getColor("Jasper.titleBackground")).isEqualTo(new Color(0xeeeeee));
        assertThat(defaults.getColor("Jasper.chromeForeground")).isEqualTo(new Color(0x111111));
        assertThat(defaults.getColor("Jasper.titleSeparator")).isEqualTo(new Color(0xcccccc));
        assertThat(defaults.getColor("Jasper.mutedForeground")).isEqualTo(new Color(0x707070));
        assertThat(defaults.getColor("Jasper.tabSelectedBackground")).isEqualTo(Color.WHITE);
        assertThat(defaults.getColor("Jasper.tabSelectedForeground")).isEqualTo(new Color(0x222222));
        assertThat(defaults.getColor("Jasper.tabHoverBackground")).isEqualTo(new Color(0, 0, 0, 0x19));
        assertThat(defaults.getColor("Jasper.tabUnderline")).isEqualTo(new Color(0x3574f0));
        assertThat(defaults.getColor("Jasper.tabUnderlineInactive")).isEqualTo(new Color(0xa0a0a0));
        assertThat(defaults.getColor("Jasper.findErrorBackground")).isEqualTo(new Color(0xffcccc));
    }

    @Test void aKeyTheThemeSetsIsNeverReplaced() {
        var defaults = flatLafOnly();
        put(defaults, "Jasper.titleBackground", 0x123456);
        put(defaults, "Jasper.configErrorForeground", 0xffeeee);
        var added = ChromeKeys.derive(defaults, TERMINAL);
        assertThat(defaults.getColor("Jasper.titleBackground")).isEqualTo(new Color(0x123456));
        // A theme-set colour is not adjusted, even below the floor.
        assertThat(defaults.getColor("Jasper.configErrorForeground")).isEqualTo(new Color(0xffeeee));
        assertThat(added).doesNotContainKeys("Jasper.titleBackground", "Jasper.configErrorForeground");
    }

    @Test void derivedTextAndStrokesMeetJaspersContrastFloor() {
        var defaults = flatLafOnly();
        ChromeKeys.derive(defaults, TERMINAL);
        Color title = defaults.getColor("Jasper.titleBackground");
        Color status = defaults.getColor("Panel.background");
        assertThat(defaults.getColor("Jasper.titleInactiveForeground")).isNotEqualTo(new Color(0x999999));
        assertThat(ChromeKeys.contrast(defaults.getColor("Jasper.titleInactiveForeground"), title)).isGreaterThanOrEqualTo(3);
        assertThat(ChromeKeys.contrast(defaults.getColor("Jasper.mutedForeground"), title)).isGreaterThanOrEqualTo(3);
        assertThat(ChromeKeys.contrast(defaults.getColor("Jasper.splitDivider"), TERMINAL)).isGreaterThanOrEqualTo(3);
        assertThat(ChromeKeys.contrast(defaults.getColor("Jasper.runningForeground"), status)).isGreaterThanOrEqualTo(3);
        for (String key : List.of("Jasper.configSuccessForeground", "Jasper.configWarningForeground", "Jasper.configErrorForeground"))
            assertThat(ChromeKeys.contrast(defaults.getColor(key), status)).as(key).isGreaterThanOrEqualTo(4.5);
    }

    @Test void aColourThatAlreadyMeetsTheFloorIsKept() {
        assertThat(ChromeKeys.readable(new Color(0x333333), Color.WHITE, 4.5, Color.BLACK)).isEqualTo(new Color(0x333333));
        Color moved = ChromeKeys.readable(new Color(0xdddddd), Color.WHITE, 3, Color.BLACK);
        assertThat(ChromeKeys.contrast(moved, Color.WHITE)).isGreaterThanOrEqualTo(3);
    }

    @Test void aThemeMissingAFlatLafKeyFailsNamingIt() {
        var defaults = flatLafOnly();
        defaults.remove("TitlePane.background");
        assertThatIllegalStateException().isThrownBy(() -> ChromeKeys.derive(defaults, TERMINAL))
            .withMessageContaining("TitlePane.background");
    }
}
```

- [ ] **Step 2: Run them to verify they fail**

Run: `./gradlew :jasper-app:test --tests dev.jasper.app.appearance.ChromeKeysTest`
Expected: FAIL to compile; `cannot find symbol: class ChromeKeys`.

- [ ] **Step 3: Implement ChromeKeys**

Create `jasper-app/src/main/java/dev/jasper/app/appearance/ChromeKeys.java`:

```java
package dev.jasper.app.appearance;

import java.awt.Color;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.swing.UIDefaults;
import javax.swing.plaf.ColorUIResource;

/**
 * Fills the {@code Jasper.*} chrome keys a theme leaves unset from its IntelliJ keys (the theme engine
 * specification, section 3). A key the theme sets is never replaced. Derived text and stroke colours are
 * moved toward the theme's foreground until they meet Jasper's contrast floor.
 */
final class ChromeKeys {
    /** Every key this class can fill. */
    static final List<String> KEYS = List.of("Jasper.titleBackground", "Jasper.titleForeground",
        "Jasper.titleInactiveForeground", "Jasper.titleSeparator", "Jasper.chromeForeground", "Jasper.mutedForeground",
        "Jasper.tabSelectedBackground", "Jasper.tabSelectedForeground", "Jasper.tabHoverBackground", "Jasper.tabUnderline",
        "Jasper.tabUnderlineInactive", "Jasper.splitDivider", "Jasper.findErrorBackground", "Jasper.runningForeground",
        "Jasper.configSuccessForeground", "Jasper.configWarningForeground", "Jasper.configErrorForeground",
        "Jasper.paletteBackground", "Jasper.paletteForeground", "Jasper.paletteMutedForeground", "Jasper.paletteBorder",
        "Jasper.paletteAccent", "Jasper.paletteSelectionBackground", "Jasper.paletteSelectionForeground");

    private ChromeKeys() {}

    /** Derives the missing keys into {@code defaults} and returns what it added. */
    static Map<String, Color> derive(UIDefaults defaults, Color terminalBackground) {
        var added = new LinkedHashMap<String, Color>();
        Color foreground = first(defaults, "Label.foreground");
        put(defaults, added, "Jasper.titleBackground", first(defaults, "MainToolbar.background", "TitlePane.background"));
        Color title = defaults.getColor("Jasper.titleBackground");
        put(defaults, added, "Jasper.titleForeground", first(defaults, "TitlePane.foreground"));
        put(defaults, added, "Jasper.titleInactiveForeground",
            readable(first(defaults, "TitlePane.inactiveForeground"), title, 3, foreground));
        put(defaults, added, "Jasper.titleSeparator", first(defaults, "MainToolbar.borderColor", "Borders.color", "Separator.foreground"));
        put(defaults, added, "Jasper.chromeForeground", first(defaults, "MainToolbar.foreground", "Label.foreground"));
        put(defaults, added, "Jasper.mutedForeground",
            readable(first(defaults, "Label.infoForeground", "Label.disabledForeground"), title, 3, foreground));
        put(defaults, added, "Jasper.tabSelectedBackground",
            first(defaults, "EditorTabs.underlinedTabBackground", "EditorTabs.selectedBackground", "Panel.background"));
        put(defaults, added, "Jasper.tabSelectedForeground", first(defaults, "EditorTabs.underlinedTabForeground", "Label.foreground"));
        put(defaults, added, "Jasper.tabHoverBackground", first(defaults, "EditorTabs.hoverBackground", "TabbedPane.hoverColor"));
        put(defaults, added, "Jasper.tabUnderline", first(defaults, "EditorTabs.underlineColor", "TabbedPane.underlineColor"));
        put(defaults, added, "Jasper.tabUnderlineInactive",
            first(defaults, "EditorTabs.inactiveUnderlineColor", "TabbedPane.inactiveUnderlineColor"));
        put(defaults, added, "Jasper.splitDivider",
            readable(first(defaults, "Borders.color", "Separator.foreground"), terminalBackground, 3, foreground));
        Color error = defaults.getColor("SearchField.errorBackground");
        put(defaults, added, "Jasper.findErrorBackground",
            error != null ? error : mix(first(defaults, "Actions.Red"), first(defaults, "TextField.background"), .12));
        Color status = first(defaults, "StatusBar.background", "Panel.background");
        put(defaults, added, "Jasper.runningForeground", readable(first(defaults, "Actions.Green"), status, 3, foreground));
        put(defaults, added, "Jasper.configSuccessForeground", readable(first(defaults, "Actions.Green"), status, 4.5, foreground));
        put(defaults, added, "Jasper.configWarningForeground", readable(first(defaults, "Actions.Yellow"), status, 4.5, foreground));
        put(defaults, added, "Jasper.configErrorForeground", readable(first(defaults, "Actions.Red"), status, 4.5, foreground));
        // Until the Search Everywhere palette reads the theme's own keys (part 2b).
        put(defaults, added, "Jasper.paletteBackground", defaults.getColor("Jasper.tabSelectedBackground"));
        put(defaults, added, "Jasper.paletteForeground", defaults.getColor("Jasper.chromeForeground"));
        put(defaults, added, "Jasper.paletteMutedForeground", defaults.getColor("Jasper.mutedForeground"));
        put(defaults, added, "Jasper.paletteBorder", first(defaults, "Component.borderColor"));
        put(defaults, added, "Jasper.paletteAccent", first(defaults, "Component.focusedBorderColor"));
        put(defaults, added, "Jasper.paletteSelectionBackground", first(defaults, "List.selectionBackground"));
        put(defaults, added, "Jasper.paletteSelectionForeground", first(defaults, "List.selectionForeground"));
        return added;
    }

    private static void put(UIDefaults defaults, Map<String, Color> added, String key, Color value) {
        if (defaults.getColor(key) != null) return;
        var color = new ColorUIResource(value);
        defaults.put(key, color);
        added.put(key, color);
    }

    /** The first key present; the last key in every call is one FlatLaf always defines. */
    private static Color first(UIDefaults defaults, String... keys) {
        for (String key : keys) {
            Color color = defaults.getColor(key);
            if (color != null) return color;
        }
        throw new IllegalStateException("None of " + List.of(keys) + " is defined by the theme");
    }

    /** {@code color}, moved toward {@code toward} in 5% steps until its contrast with {@code background} reaches {@code minimum}. */
    static Color readable(Color color, Color background, double minimum, Color toward) {
        for (int step = 0; step <= 20; step++) {
            Color candidate = mix(toward, color, step / 20.0);
            if (contrast(candidate, background) >= minimum) return candidate;
        }
        return toward;
    }

    /** {@code amount} of {@code top} over {@code bottom}, opaque. */
    static Color mix(Color top, Color bottom, double amount) {
        return new Color((int) Math.round(top.getRed() * amount + bottom.getRed() * (1 - amount)),
            (int) Math.round(top.getGreen() * amount + bottom.getGreen() * (1 - amount)),
            (int) Math.round(top.getBlue() * amount + bottom.getBlue() * (1 - amount)));
    }

    /** The WCAG contrast ratio of two opaque colours. */
    static double contrast(Color a, Color b) {
        double first = luminance(a), second = luminance(b);
        return (Math.max(first, second) + .05) / (Math.min(first, second) + .05);
    }

    private static double luminance(Color color) {
        double[] rgb = {color.getRed() / 255.0, color.getGreen() / 255.0, color.getBlue() / 255.0};
        for (int i = 0; i < rgb.length; i++) rgb[i] = rgb[i] <= .04045 ? rgb[i] / 12.92 : Math.pow((rgb[i] + .055) / 1.055, 2.4);
        return .2126 * rgb[0] + .7152 * rgb[1] + .0722 * rgb[2];
    }
}
```

- [ ] **Step 4: Run the tests to verify they pass**

Run: `./gradlew :jasper-app:test --tests dev.jasper.app.appearance.ChromeKeysTest`
Expected: PASS (6 tests).

- [ ] **Step 5: Commit**

```bash
git add jasper-app/src/main/java/dev/jasper/app/appearance/ChromeKeys.java jasper-app/src/test/java/dev/jasper/app/appearance/ChromeKeysTest.java
git commit -m "feat(appearance): derive Jasper chrome keys from IntelliJ theme keys

🤖 Generated with Claude Code at The Home Depot

Co-Authored-By: Claude <noreply@anthropic.com>"
```

---

### Task 4: Theme, ThemeManager and Jasper Dark (the spike)

This task is the spec's spike gate. The IntelliJ Light and Jasper Dark assertions below are the evidence that the engine works; `ThemeController` is not switched over until Task 5.

**Files:**
- Create: `jasper-app/src/main/java/dev/jasper/app/appearance/Theme.java`
- Create: `jasper-app/src/main/java/dev/jasper/app/appearance/ThemeManager.java`
- Create: `jasper-app/src/main/resources/dev/jasper/app/themes/jasper-dark.theme.json`
- Test: `jasper-app/src/test/java/dev/jasper/app/appearance/ThemeManagerTest.java`

**Interfaces:**
- Consumes:
  - `ThemeLoader(Function<String,String>, Map<String,String>)`, `ThemeLoader.Resolved` and `load(String)` (Task 2);
  - `ChromeKeys.derive(UIDefaults, Color)` (Task 3).
- Produces:
  - `public record Theme(String id, String name, String file, boolean dark, Palette palette)` with `Theme.LIGHT` and `Theme.DARK`, `static Theme of(Appearance)` and `public Appearance appearance()`.
  - `public final class ThemeManager` with:
    - `public static List<Theme> builtIns()`;
    - `public static boolean install(Theme)`;
    - `static boolean install(ThemeLoader.Resolved, Color terminalBackground)`;
    - `static ThemeLoader.Resolved resolve(Theme)`;
    - `public static final Map<String, String> APP_DEFAULTS`.
  - `ThemeManager.install` must run on the EDT.

- [ ] **Step 1: Write the failing spike tests**

Create `jasper-app/src/test/java/dev/jasper/app/appearance/ThemeManagerTest.java`:

```java
package dev.jasper.app.appearance;

import com.formdev.flatlaf.FlatLaf;
import dev.jasper.app.testsupport.EdtTestExtension;
import java.awt.Color;
import java.util.LinkedHashMap;
import java.util.Map;
import javax.swing.LookAndFeel;
import javax.swing.UIManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import static org.assertj.core.api.Assertions.*;

@ExtendWith(EdtTestExtension.class)
class ThemeManagerTest {
    /** Jasper Dark before the engine: FlatLaf Dark plus Jasper's former properties files. */
    private static final String[] TODAYS_DARK = {
        "Panel.background", "#21252b", "List.background", "#21252b", "List.foreground", "#abb2bf",
        "List.selectionBackground", "#5e7293", "List.selectionForeground", "#ffffff", "List.selectionInactiveBackground", "#5e7293",
        "TextField.background", "#282c34", "TextField.foreground", "#abb2bf", "TextField.selectionBackground", "#3e4451",
        "TextArea.background", "#282c34", "PasswordField.background", "#282c34", "ComboBox.background", "#333841",
        "ComboBox.buttonBackground", "#333841", "ComboBox.foreground", "#abb2bf", "ComboBox.selectionBackground", "#5e7293",
        "ComboBox.buttonArrowColor", "#abb2bf", "Button.background", "#3d424b", "Button.foreground", "#a7aebb",
        "Button.borderColor", "#464c55", "Button.hoverBackground", "#323842", "Button.pressedBackground", "#3e4451",
        "Button.focusedBackground", "#3d424b", "Button.default.background", "#6b80a1", "Button.default.foreground", "#ffffff",
        "Button.default.borderColor", "#6b80a1", "Button.default.hoverBackground", "#7b8dab", "Button.default.pressedBackground", "#5e7394",
        "Button.toolbar.hoverBackground", "#323842", "Button.toolbar.pressedBackground", "#3e4451", "Button.toolbar.selectedBackground", "#3e4451",
        "ToolBar.background", "#23262c", "MenuBar.background", "#23262c", "Menu.background", "#292c34", "MenuItem.background", "#292c34",
        "MenuItem.foreground", "#d3d7df", "MenuItem.acceleratorForeground", "#abb2bf", "PopupMenu.background", "#292c34",
        "PopupMenu.borderColor", "#353940", "TitlePane.background", "#23262c", "TitlePane.foreground", "#848c9b",
        "TitlePane.inactiveForeground", "#848c9b", "TabbedPane.background", "#23262c", "TabbedPane.selectedBackground", "#282a36",
        "TabbedPane.selectedForeground", "#d3d7df", "TabbedPane.hoverColor", "#323842", "TabbedPane.underlineColor", "#61afef",
        "Label.foreground", "#abb2bf", "Label.disabledForeground", "#7e8491", "Component.borderColor", "#353940",
        "Component.focusedBorderColor", "#61afef", "Component.focusColor", "#61afef", "Component.accentColor", "#61afef",
        "Component.linkColor", "#61afef", "Separator.foreground", "#353940", "ScrollBar.thumb", "#4d5262", "ScrollBar.track", "#2b2e37",
        "SplitPaneDivider.gripColor", "#d3d7df", "SplitPaneDivider.hoverColor", "#323842", "SplitPaneDivider.pressedColor", "#3e4451",
        "ToolTip.background", "#15161a", "ToolTip.foreground", "#d3d7df", "Tree.background", "#292c34",
        "Tree.selectionBackground", "#3e4451", "CheckBox.background", "#292c34", "ProgressBar.foreground", "#61afef",
        "Jasper.chromeForeground", "#d3d7df", "Jasper.configErrorForeground", "#ff858d", "Jasper.configSuccessForeground", "#a8c58d",
        "Jasper.configWarningForeground", "#e5c07b", "Jasper.findErrorBackground", "#5c3b3b", "Jasper.mutedForeground", "#848c9b",
        "Jasper.runningForeground", "#a8c58d", "Jasper.splitDivider", "#77808f", "Jasper.tabHoverBackground", "#2c3036",
        "Jasper.tabSelectedBackground", "#262a2f", "Jasper.tabSelectedForeground", "#d3d7df", "Jasper.tabUnderline", "#4a88c7",
        "Jasper.tabUnderlineInactive", "#747a80", "Jasper.titleBackground", "#23262c", "Jasper.titleForeground", "#848c9b",
        "Jasper.titleInactiveForeground", "#848c9b", "Jasper.titleSeparator", "#313439"};

    @Test void intellijLightInstallsTheClassicLightColours() throws Exception {
        LookAndFeel original = UIManager.getLookAndFeel();
        try {
            assertThat(ThemeManager.install(Theme.LIGHT)).isTrue();
            assertThat(UIManager.getLookAndFeel().getName()).isEqualTo("IntelliJ Light");
            assertThat(FlatLaf.isLafDark()).isFalse();
            assertColours(Map.ofEntries(
                Map.entry("Panel.background", "#f2f2f2"), Map.entry("TextField.background", "#ffffff"),
                Map.entry("List.background", "#ffffff"), Map.entry("List.selectionBackground", "#2675bf"),
                Map.entry("List.hoverBackground", "#edf5fc"), Map.entry("Button.background", "#ffffff"),
                Map.entry("Component.linkColor", "#2470b3"), Map.entry("ToolWindow.background", "#ffffff"),
                Map.entry("ToolWindow.Header.background", "#e2e6ec"), Map.entry("StatusBar.background", "#f2f2f2"),
                Map.entry("StatusBar.borderColor", "#d1d1d1"), Map.entry("MainToolbar.background", "#f2f2f2"),
                Map.entry("EditorTabs.underlineColor", "#4083c9"), Map.entry("EditorTabs.inactiveUnderlineColor", "#9ca7b8"),
                Map.entry("EditorTabs.hoverBackground", "#00000019"), Map.entry("EditorTabs.underlinedTabBackground", "#ffffff"),
                Map.entry("SearchEverywhere.Header.background", "#f2f2f2"), Map.entry("SearchEverywhere.Tab.selectedBackground", "#dfdfdf"),
                Map.entry("SearchEverywhere.Tab.selectedForeground", "#000000"), Map.entry("SearchEverywhere.SearchField.background", "#ffffff"),
                Map.entry("SearchEverywhere.SearchField.borderColor", "#c4c4c4"), Map.entry("SearchEverywhere.SearchField.infoForeground", "#808080"),
                Map.entry("SearchEverywhere.List.separatorColor", "#d9d9d9"), Map.entry("SearchEverywhere.List.separatorForeground", "#999999"),
                Map.entry("SearchEverywhere.Advertiser.background", "#f2f2f2"), Map.entry("SearchEverywhere.Advertiser.foreground", "#808080"),
                Map.entry("Popup.borderColor", "#ababab")));
            assertColours(Map.of("Jasper.titleBackground", "#f2f2f2", "Jasper.titleForeground", "#6e6e6e",
                "Jasper.titleInactiveForeground", "#8a8a8a", "Jasper.titleSeparator", "#d1d1d1",
                "Jasper.tabSelectedBackground", "#ffffff", "Jasper.tabUnderline", "#4083c9", "Jasper.findErrorBackground", "#ffcccc"));
            // IntelliJ's border classes are dropped, so FlatLaf keeps its own.
            assertThat(UIManager.getBorder("ScrollPane.border")).isInstanceOf(com.formdev.flatlaf.ui.FlatScrollPaneBorder.class);
            assertThat(UIManager.getFont("Label.font").getSize2D()).isEqualTo(12f);
        } finally { UIManager.setLookAndFeel(original); }
    }

    @Test void jasperDarkKeepsTheColoursItHadBeforeTheEngine() throws Exception {
        LookAndFeel original = UIManager.getLookAndFeel();
        try {
            assertThat(ThemeManager.install(Theme.DARK)).isTrue();
            assertThat(UIManager.getLookAndFeel().getName()).isEqualTo("Jasper Dark");
            assertThat(FlatLaf.isLafDark()).isTrue();
            var expected = new LinkedHashMap<String, String>();
            for (int i = 0; i < TODAYS_DARK.length; i += 2) expected.put(TODAYS_DARK[i], TODAYS_DARK[i + 1]);
            assertColours(expected);
            // FlatLaf copies the list selection into menus; white text keeps it readable.
            assertColours(Map.of("MenuItem.selectionBackground", "#5e7293", "MenuItem.selectionForeground", "#ffffff"));
            assertColours(Map.of("ToolWindow.background", "#21252b", "StatusBar.background", "#23262c",
                "SearchEverywhere.Tab.selectedBackground", "#3e4451", "List.hoverBackground", "#323842"));
        } finally { UIManager.setLookAndFeel(original); }
    }

    @Test void keysFlatLafSkipsAreRestoredWithoutReplacingItsOwn() throws Exception {
        LookAndFeel original = UIManager.getLookAndFeel();
        try {
            var resolved = new ThemeLoader(Map.of("fixture.json", """
                {"name": "Fixture", "ui": {"SearchEverywhere.Header.background": "#123456",
                  "EditorTabs.hoverBackground": "#00000019", "ComboBox.background": "#010101"}}""")::get, Map.of())
                .load("fixture.json");
            assertThat(ThemeManager.install(resolved, Color.WHITE)).isTrue();
            assertColours(Map.of("SearchEverywhere.Header.background", "#123456", "EditorTabs.hoverBackground", "#00000019"));
            // FlatLaf reads IntelliJ's combo keys its own way; its value stands.
            assertThat(hex(UIManager.getColor("ComboBox.background"))).isNotEqualTo("#010101");
        } finally { UIManager.setLookAndFeel(original); }
    }

    @Test void restoredAndDerivedKeysSurviveReinstallingTheSameLookAndFeel() throws Exception {
        LookAndFeel original = UIManager.getLookAndFeel();
        try {
            ThemeManager.install(Theme.LIGHT);
            LookAndFeel light = UIManager.getLookAndFeel();
            ThemeManager.install(Theme.DARK);
            // ThemeController rolls back like this when a later install fails.
            UIManager.setLookAndFeel(light);
            assertColours(Map.of("SearchEverywhere.Tab.selectedBackground", "#dfdfdf", "Jasper.titleBackground", "#f2f2f2",
                "Jasper.tabUnderline", "#4083c9"));
            assertThat(UIManager.getFont("Label.font").getSize2D()).isEqualTo(12f);
        } finally { UIManager.setLookAndFeel(original); }
    }

    @Test void switchingThemesLeavesNothingOfThePreviousOneBehind() throws Exception {
        LookAndFeel original = UIManager.getLookAndFeel();
        try {
            ThemeManager.install(Theme.LIGHT);
            assertThat(UIManager.getColor("Plugins.hoverBackground")).isNotNull();
            ThemeManager.install(Theme.DARK);
            assertThat(UIManager.getColor("Plugins.hoverBackground")).isNull();
            assertColours(Map.of("ToolWindow.background", "#21252b", "Jasper.titleBackground", "#23262c"));
        } finally { UIManager.setLookAndFeel(original); }
    }

    @Test void everyBuiltInThemeMatchesItsFile() {
        assertThat(ThemeManager.builtIns()).containsExactly(Theme.LIGHT, Theme.DARK);
        for (Theme theme : ThemeManager.builtIns()) {
            var resolved = ThemeManager.resolve(theme);
            assertThat(resolved.name()).isEqualTo(theme.name());
            assertThat(resolved.dark()).isEqualTo(theme.dark());
        }
        assertThat(Theme.LIGHT.id()).isEqualTo("intellij-light");
        assertThat(Theme.DARK.id()).isEqualTo("jasper-dark");
    }

    private static void assertColours(Map<String, String> expected) {
        expected.forEach((key, value) -> assertThat(hex(UIManager.getColor(key))).as(key).isEqualTo(value));
    }

    private static String hex(Color color) {
        if (color == null) return null;
        String rgb = String.format("#%06x", color.getRGB() & 0xffffff);
        return color.getAlpha() == 255 ? rgb : rgb + String.format("%02x", color.getAlpha());
    }
}
```

- [ ] **Step 2: Run them to verify they fail**

Run: `./gradlew :jasper-app:test --tests dev.jasper.app.appearance.ThemeManagerTest`
Expected: FAIL to compile; `cannot find symbol: class Theme` / `class ThemeManager`.

- [ ] **Step 3: Write Jasper Dark**

Create `jasper-app/src/main/resources/dev/jasper/app/themes/jasper-dark.theme.json`, which reproduces today's dark look in IntelliJ keys:

```json
{
  "name": "Jasper Dark",
  "dark": true,
  "author": "Jasper",
  "colors": {
    "background": "#292c34",
    "foreground": "#d3d7df",
    "formForeground": "#abb2bf",
    "disabledForeground": "#8b929f",
    "formBackground": "#21252b",
    "controlBackground": "#282c34",
    "chromeBackground": "#23262c",
    "chromeForeground": "#848c9b",
    "border": "#353940",
    "hover": "#323842",
    "selection": "#3e4451",
    "selectionForeground": "#e6e9ef",
    "formSelection": "#5e7293",
    "accent": "#61afef",
    "buttonBackground": "#3d424b",
    "buttonBorder": "#464c55",
    "buttonForeground": "#a7aebb",
    "primaryButton": "#6b80a1"
  },
  "ui": {
    "*": {
      "background": "background",
      "foreground": "foreground",
      "disabledForeground": "disabledForeground",
      "inactiveForeground": "disabledForeground",
      "selectionBackground": "selection",
      "selectionForeground": "selectionForeground",
      "selectionInactiveBackground": "selection",
      "selectionInactiveForeground": "selectionForeground"
    },
    "@accentBaseColor": "#61afef",
    "Component.arc": 4,
    "Button.arc": 4,
    "TextComponent.arc": 0,
    "ComboBox.padding": "3,6,4,6",
    "TextArea.margin": "4,8,4,8",
    "TextField.margin": "3,6,4,6",
    "PasswordField.margin": "3,6,4,6",
    "FormattedTextField.margin": "3,6,4,6",
    "Button.margin": "2,14,2,14",
    "Button.minimumWidth": 72,
    "Button.default.boldText": true,
    "Button.paintShadow": false,
    "ComboBox.popupInsets": "0,0,0,0",
    "ComboBox.buttonStyle": "button",
    "Component.arrowType": "triangle",
    "Component.focusWidth": 0,
    "Component.innerFocusWidth": 0.5,
    "Button.innerFocusWidth": 1,
    "CheckBox.icon.style": "outlined",
    "Panel.background": "formBackground",
    "Label.foreground": "formForeground",
    "Label.disabledForeground": "#7e8491",
    "List.background": "formBackground",
    "List.foreground": "formForeground",
    "List.selectionBackground": "formSelection",
    "List.selectionForeground": "#ffffff",
    "List.selectionInactiveBackground": "formSelection",
    "List.selectionInactiveForeground": "#ffffff",
    "TextField.background": "controlBackground",
    "TextField.foreground": "formForeground",
    "TextArea.background": "controlBackground",
    "TextArea.foreground": "formForeground",
    "PasswordField.background": "controlBackground",
    "PasswordField.foreground": "formForeground",
    "FormattedTextField.background": "controlBackground",
    "FormattedTextField.foreground": "formForeground",
    "ComboBox.background": "#333841",
    "ComboBox.nonEditableBackground": "#333841",
    "ComboBox.ArrowButton.background": "#333841",
    "ComboBox.ArrowButton.nonEditableBackground": "#333841",
    "ComboBox.ArrowButton.iconColor": "formForeground",
    "ComboBox.foreground": "formForeground",
    "ComboBox.selectionBackground": "formSelection",
    "ComboBox.selectionForeground": "#ffffff",
    "Button.startBackground": "buttonBackground",
    "Button.endBackground": "buttonBackground",
    "Button.startBorderColor": "buttonBorder",
    "Button.endBorderColor": "buttonBorder",
    "Button.foreground": "buttonForeground",
    "Button.focusedBackground": "buttonBackground",
    "Button.hoverBackground": "hover",
    "Button.pressedBackground": "selection",
    "Button.disabledBackground": "background",
    "Button.default.startBackground": "primaryButton",
    "Button.default.endBackground": "primaryButton",
    "Button.default.startBorderColor": "primaryButton",
    "Button.default.endBorderColor": "primaryButton",
    "Button.default.foreground": "#ffffff",
    "Button.default.focusedBackground": "primaryButton",
    "Button.default.hoverBackground": "#7b8dab",
    "Button.default.pressedBackground": "#5e7394",
    "Button.toolbar.hoverBackground": "hover",
    "Button.toolbar.pressedBackground": "selection",
    "Button.toolbar.selectedBackground": "selection",
    "Component.borderColor": "border",
    "Component.disabledBorderColor": "border",
    "Component.focusedBorderColor": "accent",
    "Component.focusColor": "accent",
    "Link.activeForeground": "accent",
    "ProgressBar.foreground": "accent",
    "ProgressBar.progressColor": "accent",
    "Separator.foreground": "border",
    "Separator.separatorColor": "border",
    "ToolBar.background": "chromeBackground",
    "MenuBar.background": "chromeBackground",
    "Menu.background": "background",
    "Menu.foreground": "foreground",
    "MenuItem.background": "background",
    "MenuItem.foreground": "foreground",
    "MenuItem.selectionForeground": "#ffffff",
    "MenuItem.acceleratorForeground": "formForeground",
    "PopupMenu.background": "background",
    "PopupMenu.borderColor": "border",
    "TitlePane.background": "chromeBackground",
    "TitlePane.inactiveBackground": "chromeBackground",
    "TitlePane.foreground": "chromeForeground",
    "TitlePane.inactiveForeground": "chromeForeground",
    "TitlePane.infoForeground": "chromeForeground",
    "TitlePane.inactiveInfoForeground": "chromeForeground",
    "TabbedPane.background": "chromeBackground",
    "TabbedPane.selectedBackground": "#282a36",
    "TabbedPane.selectedForeground": "foreground",
    "TabbedPane.hoverColor": "hover",
    "TabbedPane.focusColor": "hover",
    "TabbedPane.contentAreaColor": "border",
    "TabbedPane.underlineColor": "accent",
    "SplitPaneDivider.gripColor": "foreground",
    "SplitPaneDivider.hoverColor": "hover",
    "SplitPaneDivider.pressedColor": "selection",
    "ScrollBar.thumb": "#4d5262",
    "ScrollBar.track": "#2b2e37",
    "ToolTip.background": "#15161a",
    "ToolTip.foreground": "foreground",
    "Tree.background": "background",
    "MainToolbar.background": "chromeBackground",
    "MainToolbar.foreground": "foreground",
    "EditorTabs.underlinedTabBackground": "#262a2f",
    "EditorTabs.underlinedTabForeground": "foreground",
    "EditorTabs.hoverBackground": "#2c3036",
    "EditorTabs.underlineColor": "#4a88c7",
    "EditorTabs.inactiveUnderlineColor": "#747a80",
    "ToolWindow.background": "formBackground",
    "ToolWindow.Header.background": "chromeBackground",
    "StatusBar.background": "chromeBackground",
    "StatusBar.borderColor": "border",
    "Borders.color": "border",
    "List.hoverBackground": "hover",
    "Popup.borderColor": "border",
    "SearchEverywhere.Header.background": "chromeBackground",
    "SearchEverywhere.Tab.selectedBackground": "selection",
    "SearchEverywhere.Tab.selectedForeground": "foreground",
    "SearchEverywhere.SearchField.background": "#262a2f",
    "SearchEverywhere.SearchField.borderColor": "border",
    "SearchEverywhere.SearchField.infoForeground": "chromeForeground",
    "SearchEverywhere.List.separatorColor": "border",
    "SearchEverywhere.List.separatorForeground": "chromeForeground",
    "SearchEverywhere.Advertiser.background": "chromeBackground",
    "SearchEverywhere.Advertiser.foreground": "chromeForeground",
    "SearchField.errorBackground": "#5c3b3b",
    "Jasper.titleBackground": "chromeBackground",
    "Jasper.titleForeground": "chromeForeground",
    "Jasper.titleInactiveForeground": "chromeForeground",
    "Jasper.titleSeparator": "#313439",
    "Jasper.chromeForeground": "foreground",
    "Jasper.mutedForeground": "chromeForeground",
    "Jasper.tabSelectedBackground": "#262a2f",
    "Jasper.tabSelectedForeground": "foreground",
    "Jasper.tabHoverBackground": "#2c3036",
    "Jasper.tabUnderline": "#4a88c7",
    "Jasper.tabUnderlineInactive": "#747a80",
    "Jasper.findErrorBackground": "#5c3b3b",
    "Jasper.splitDivider": "#77808f",
    "Jasper.runningForeground": "#a8c58d",
    "Jasper.configSuccessForeground": "#a8c58d",
    "Jasper.configWarningForeground": "#e5c07b",
    "Jasper.configErrorForeground": "#ff858d"
  }
}
```

- [ ] **Step 4: Implement Theme**

Create `jasper-app/src/main/java/dev/jasper/app/appearance/Theme.java`:

```java
package dev.jasper.app.appearance;

import dev.jasper.app.config.Appearance;
import dev.jasper.terminal.config.Palette;
import java.util.Objects;

/**
 * A theme Jasper can install: an IntelliJ-format theme file under {@code dev/jasper/app/themes/} and
 * the Jasper terminal palette used when the terminal matches the UI.
 */
public record Theme(String id, String name, String file, boolean dark, Palette palette) {
    /** Classic IntelliJ Light, vendored from intellij-community. */
    public static final Theme LIGHT = new Theme("intellij-light", "IntelliJ Light", "intellij/Light.theme.json", false, Palette.jasperLight());
    /** Jasper's own dark theme. */
    public static final Theme DARK = new Theme("jasper-dark", "Jasper Dark", "jasper-dark.theme.json", true, Palette.jasperDark());

    public Theme {
        Objects.requireNonNull(id);
        Objects.requireNonNull(name);
        Objects.requireNonNull(file);
        Objects.requireNonNull(palette);
    }

    static Theme of(Appearance appearance) { return appearance == Appearance.LIGHT ? LIGHT : DARK; }

    public Appearance appearance() { return dark ? Appearance.DARK : Appearance.LIGHT; }
}
```

- [ ] **Step 5: Implement ThemeManager**

Create `jasper-app/src/main/java/dev/jasper/app/appearance/ThemeManager.java`:

```java
package dev.jasper.app.appearance;

import com.formdev.flatlaf.FlatLaf;
import com.formdev.flatlaf.IntelliJTheme;
import java.awt.Color;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.swing.SwingUtilities;
import javax.swing.UIDefaults;
import javax.swing.UIManager;
import javax.swing.plaf.ColorUIResource;

/**
 * The built-in themes and the one path that installs any theme: resolve its IntelliJ theme file, let
 * FlatLaf build the look and feel, put back the colours FlatLaf skips (IntelliJ-only namespaces such as
 * {@code SearchEverywhere.*}), then derive Jasper's chrome keys. EDT only.
 */
public final class ThemeManager {
    private static final String BASE = "dev/jasper/app/themes/";
    /** Each theme {@code name} a {@code parentTheme} may name, with its file under {@link #BASE}. */
    private static final Map<String, String> FILES = Map.of(
        "IntelliJ Light", "intellij/Light.theme.json",
        "IntelliJ", "intellij/intellijlaf.theme.json",
        "Darcula", "intellij/darcula.theme.json",
        "Jasper Dark", "jasper-dark.theme.json");
    /** Theme-independent application defaults: form typography and the split divider. Every theme gets them. */
    public static final Map<String, String> APP_DEFAULTS = appDefaults();
    private static final ThemeLoader LOADER = new ThemeLoader(ThemeManager::read, FILES);
    private static final Map<Theme, ThemeLoader.Resolved> RESOLVED = new HashMap<>();

    private ThemeManager() {}

    /** The bundled themes, Light first. */
    public static List<Theme> builtIns() { return List.of(Theme.LIGHT, Theme.DARK); }

    static ThemeLoader.Resolved resolve(Theme theme) { return RESOLVED.computeIfAbsent(theme, key -> LOADER.load(key.file())); }

    /** Installs {@code theme} as the look and feel; false when FlatLaf could not set it up. */
    public static boolean install(Theme theme) {
        requireEdt();
        return install(resolve(theme), theme.palette().background());
    }

    static boolean install(ThemeLoader.Resolved resolved, Color terminalBackground) {
        requireEdt();
        FlatLaf laf;
        try { laf = IntelliJTheme.createLaf(new ByteArrayInputStream(resolved.json().getBytes(StandardCharsets.UTF_8))); }
        catch (IOException failure) { throw new UncheckedIOException(failure); }
        laf.setExtraDefaults(APP_DEFAULTS);
        if (!FlatLaf.setup(laf)) return false;
        UIDefaults defaults = UIManager.getLookAndFeelDefaults();
        var extra = new LinkedHashMap<>(APP_DEFAULTS);
        resolved.colors().forEach((key, color) -> {
            if (defaults.get(key) != null) return;
            defaults.put(key, new ColorUIResource(color));
            extra.put(key, hex(color));
        });
        ChromeKeys.derive(defaults, terminalBackground).forEach((key, color) -> extra.put(key, hex(color)));
        // A later reinstall of this instance (a failed switch rolls back to it) rebuilds the defaults.
        laf.setExtraDefaults(extra);
        return true;
    }

    private static Map<String, String> appDefaults() {
        var defaults = new LinkedHashMap<String, String>();
        // Reference form typography in logical points; FlatLaf applies display scaling.
        for (String component : List.of("Label", "List", "TextField", "PasswordField", "FormattedTextField", "TextArea", "ComboBox", "Button"))
            defaults.put(component + ".font", "12 $defaultFont");
        defaults.put("SplitPane.dividerSize", "8");
        defaults.put("SplitPaneDivider.border", "dev.jasper.app.workspace.SplitDividerBorder");
        return Collections.unmodifiableMap(defaults);
    }

    private static String read(String file) {
        try (InputStream in = ThemeManager.class.getClassLoader().getResourceAsStream(BASE + file)) {
            return in == null ? null : new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException failure) {
            throw new UncheckedIOException(failure);
        }
    }

    private static String hex(Color color) {
        String rgb = String.format("#%06x", color.getRGB() & 0xffffff);
        return color.getAlpha() == 255 ? rgb : rgb + String.format("%02x", color.getAlpha());
    }

    private static void requireEdt() {
        if (!SwingUtilities.isEventDispatchThread()) throw new IllegalStateException("Theme operations require the EDT");
    }
}
```

- [ ] **Step 6: Run the spike tests to verify they pass**

Run: `./gradlew :jasper-app:test --tests dev.jasper.app.appearance.ThemeManagerTest`
Expected: PASS (6 tests).

If an IntelliJ Light assertion fails with a Darcula colour, the spec's fallback rule applies (the loader stops taking `ui` keys from a dark parent into a light child). Implement it in `ThemeLoader.resolve`, where `own.over(...)` is called: when `own.dark` differs from the parent's resolved `dark`, keep the parent's non-colour keys only. Then record the outcome in the ledger and in this plan's status line. The prototype needed no fallback.

- [ ] **Step 7: Run the appearance tests and commit**

Run: `./gradlew :jasper-app:test --tests 'dev.jasper.app.appearance.*'`
Expected: PASS. Existing tests are unaffected; `ThemeController` still installs FlatLaf Light and Dark.

```bash
git add jasper-app/src/main/java/dev/jasper/app/appearance/Theme.java jasper-app/src/main/java/dev/jasper/app/appearance/ThemeManager.java jasper-app/src/main/resources/dev/jasper/app/themes/jasper-dark.theme.json jasper-app/src/test/java/dev/jasper/app/appearance/ThemeManagerTest.java
git commit -m "feat(appearance): install IntelliJ Light and Jasper Dark through one theme manager

🤖 Generated with Claude Code at The Home Depot

Co-Authored-By: Claude <noreply@anthropic.com>"
```

---

### Task 5: ThemeController on ThemeManager; retire BuiltinTheme, the properties files and BrandedButtonUI

**Files:**
- Delete: `jasper-app/src/main/java/dev/jasper/app/appearance/BuiltinTheme.java`, `BrandedButtonUI.java`
- Delete: `jasper-app/src/main/resources/dev/jasper/app/themes/FlatLaf.properties`, `FlatLightLaf.properties`, `FlatDarkLaf.properties`
- Delete: `jasper-app/src/test/java/dev/jasper/app/appearance/BrandedButtonsTest.java`
- Modify: every `jasper-app/src` file naming `BuiltinTheme` (mechanical rename to `Theme`)
- Modify: `appearance/ThemeController.java`, `application/JasperApplication.java`, `appearance/ThemeTestSupport.java` (test)
- Test (create): `jasper-app/src/test/java/dev/jasper/app/appearance/ThemeButtonsTest.java`
- Test (modify):
  - `workspace/WindowChromeTest.java`
  - `application/JasperApplicationPluginsTest.java`
  - `architecture/PackagedResourcesTest.java`
  - `workspace/MacTitleBarTest.java`
  - `workspace/ThemeControllerTest.java`
  - `workspace/CommandPaletteShortcutsTest.java`
  - `workspace/ApplicationActionsTest.java`
  - `workspace/CommandPalettePreview.java`

**Interfaces:**
- Consumes: `Theme`, `ThemeManager.install(Theme)` and `ThemeManager.APP_DEFAULTS` (Task 4).
- Produces:
  - `ThemeController(Predicate<Theme>)`, `ThemeController.select(Theme)` and `ResolvedTheme(Theme chrome, Palette palette)`;
  - `ThemeTestSupport.install(Theme)`, a public test helper;
  - a `ThemeController` whose default installer is `ThemeManager::install`.

- [ ] **Step 1: Rename BuiltinTheme to Theme (behaviour unchanged)**

```bash
git rm -q jasper-app/src/main/java/dev/jasper/app/appearance/BuiltinTheme.java
git grep -lz BuiltinTheme -- jasper-app/src | xargs -0 sed -i '' 's/BuiltinTheme/Theme/g'
```

Then, still keeping FlatLaf Light and Dark as the installer:
- In `ThemeController.java`, change both `theme.label()` calls to `theme.name()`.
- In `jasper-app/src/test/java/dev/jasper/app/workspace/CommandPalettePreview.java`, replace the body of `themeSlug(Theme theme)` with `return theme.dark() ? "dark" : "light";`.

Run: `./gradlew :jasper-app:compileTestJava`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 2: Write the failing tests for the engine-installed themes**

Delete `jasper-app/src/test/java/dev/jasper/app/appearance/BrandedButtonsTest.java`. Create `jasper-app/src/test/java/dev/jasper/app/appearance/ThemeButtonsTest.java`:

```java
package dev.jasper.app.appearance;

import com.formdev.flatlaf.ui.FlatButtonUI;
import dev.jasper.app.testsupport.EdtTestExtension;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.util.List;
import javax.swing.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import static org.assertj.core.api.Assertions.*;

@ExtendWith(EdtTestExtension.class)
class ThemeButtonsTest {
    @Test void formControlsAreDrawnByFlatLafInTheThemesColours() throws Exception {
        var original = UIManager.getLookAndFeel();
        try {
            var themes = new ThemeController();
            var button = new JButton("Generate Key...");
            var parent = new JPanel(); parent.add(button);
            var field = new JTextField("Existing text", 20);
            var password = new JPasswordField(20);
            var formatted = new JFormattedTextField();
            var choices = new JComboBox<>(new String[]{"Password", "Key"});
            var note = new JTextArea("First line\nSecond line");
            parent.add(field); parent.add(password); parent.add(formatted); parent.add(choices); parent.add(note);
            var dialog = new JRootPane(); var confirm = new JButton("OK"); dialog.getContentPane().add(confirm); dialog.setDefaultButton(confirm);
            int[] clicks = {0}; button.addActionListener(event -> clicks[0]++);
            for (var theme : List.of(Theme.DARK, Theme.LIGHT)) {
                themes.select(theme);
                SwingUtilities.updateComponentTreeUI(parent);
                SwingUtilities.updateComponentTreeUI(dialog);
                assertThat(button.getUI()).isInstanceOf(FlatButtonUI.class);
                assertThat(button.getFont().getSize2D()).as("form font").isEqualTo(12f);
                assertThat(button.getPreferredSize().height).isBetween(20, 28);
                assertThat(fill(button)).as(theme.name()).isEqualTo(UIManager.getColor("Button.background"));
                // IntelliJ Light's default button is a slight gradient; sample within a small tolerance.
                assertThat(distance(fill(confirm), UIManager.getColor("Button.default.background"))).as(theme.name()).isLessThanOrEqualTo(8);
                for (var input : List.of(field, password, formatted)) {
                    assertThat(input.getPreferredSize().height).isBetween(20, 30);
                    assertThat(input.isEditable()).isTrue();
                }
                assertThat(field.getBackground()).isEqualTo(UIManager.getColor("TextField.background"));
                assertThat(password.getBackground()).isEqualTo(UIManager.getColor("PasswordField.background"));
                assertThat(formatted.getBackground()).isEqualTo(UIManager.getColor("FormattedTextField.background"));
                assertThat(choices.getBackground()).isEqualTo(UIManager.getColor("ComboBox.background"));
                assertThat(note.getBackground()).isEqualTo(UIManager.getColor("TextArea.background"));
                assertThat(field.getText()).isEqualTo("Existing text");
                assertThat(note.getText()).isEqualTo("First line\nSecond line");
                button.setEnabled(false); button.doClick(); assertThat(clicks[0]).isZero();
                button.setEnabled(true);
            }
            button.doClick(); assertThat(clicks[0]).isEqualTo(1);
            button.setText("Always allow for an unusually long plugin name");
            assertThat(button.getPreferredSize().width).isGreaterThan(200);
        } finally { UIManager.setLookAndFeel(original); }
    }

    private static Color fill(JButton button) {
        button.setSize(button.getPreferredSize());
        var image = new BufferedImage(button.getWidth(), button.getHeight(), BufferedImage.TYPE_INT_ARGB);
        var graphics = image.createGraphics(); button.paint(graphics); graphics.dispose();
        return new Color(image.getRGB(button.getWidth() / 2, 4), true);
    }

    private static int distance(Color a, Color b) {
        return Math.max(Math.abs(a.getRed() - b.getRed()), Math.max(Math.abs(a.getGreen() - b.getGreen()), Math.abs(a.getBlue() - b.getBlue())));
    }
}
```

In `jasper-app/src/test/java/dev/jasper/app/workspace/WindowChromeTest.java`:
- Remove the imports `com.formdev.flatlaf.FlatDarkLaf` and `com.formdev.flatlaf.FlatLightLaf`.
- Replace `assertThat(UIManager.getLookAndFeel()).isInstanceOf(FlatLightLaf.class);` with `assertThat(UIManager.getLookAndFeel().getName()).isEqualTo("IntelliJ Light");`.
- Replace `assertThat(UIManager.getLookAndFeel()).isInstanceOf(FlatDarkLaf.class);` with `assertThat(UIManager.getLookAndFeel().getName()).isEqualTo("Jasper Dark");`.

In `jasper-app/src/test/java/dev/jasper/app/application/JasperApplicationPluginsTest.java`, replace the expected content

```java
                "LIGHT\ncom.formdev.flatlaf.FlatLightLaf\ndev.jasper.app.appearance.BrandedButtonUI");
```

with

```java
                "LIGHT\ncom.formdev.flatlaf.IntelliJTheme$ThemeLaf\ncom.formdev.flatlaf.ui.FlatButtonUI");
```

In `jasper-app/src/test/java/dev/jasper/app/architecture/PackagedResourcesTest.java`, replace the three entries

```java
                "dev/jasper/app/themes/FlatDarkLaf.properties",
                "dev/jasper/app/themes/FlatLaf.properties",
                "dev/jasper/app/themes/FlatLightLaf.properties"
```

with

```java
                "dev/jasper/app/themes/intellij/Light.theme.json",
                "dev/jasper/app/themes/intellij/intellijlaf.theme.json",
                "dev/jasper/app/themes/intellij/darcula.theme.json",
                "dev/jasper/app/themes/intellij/LICENSE.txt",
                "dev/jasper/app/themes/jasper-dark.theme.json"
```

and replace the divider-class check

```java
            var defaults = jar.getJarEntry("dev/jasper/app/themes/FlatLaf.properties");
            var values = new java.util.Properties();
            try (var stream = jar.getInputStream(defaults)) { values.load(stream); }
            String border = values.getProperty("SplitPaneDivider.border");
            assertThat(jar.getJarEntry(border.replace('.', '/') + ".class")).isNotNull();
```

with

```java
            assertThat(jar.getJarEntry("dev/jasper/app/themes/FlatLaf.properties")).isNull();
            String border = dev.jasper.app.appearance.ThemeManager.APP_DEFAULTS.get("SplitPaneDivider.border");
            assertThat(jar.getJarEntry(border.replace('.', '/') + ".class")).isNotNull();
```

In `jasper-app/src/test/java/dev/jasper/app/workspace/MacTitleBarTest.java`, in `themeAndActivationRecolorActualHeaderAndHidingToolbarRetainsItsHeight`, replace the three IntelliJ Light expectations

```java
                assertThat(pixel(bar)).isEqualTo(new Color(0xeaeaeb));
                assertThat(label(bar).getForeground()).isEqualTo(new Color(0x696c77));
                bar.setActive(true);
                assertThat(label(bar).getForeground()).isEqualTo(new Color(0x383a42));
```

with

```java
                assertThat(pixel(bar)).isEqualTo(new Color(0xf2f2f2));
                assertThat(label(bar).getForeground()).isEqualTo(new Color(0x8a8a8a));
                bar.setActive(true);
                assertThat(label(bar).getForeground()).isEqualTo(new Color(0x6e6e6e));
```

In `jasper-app/src/test/java/dev/jasper/app/workspace/ThemeControllerTest.java`, in `resourcesCoordinateChromeAndKeepTextReadableAcrossStates`, delete this line (planning decision 8):

```java
                assertThat(UIManager.getColor("TabbedPane.selectedBackground")).isEqualTo(theme.palette().background());
```

In `jasper-app/src/test/java/dev/jasper/app/workspace/CommandPaletteShortcutsTest.java` and `ApplicationActionsTest.java`, replace `com.formdev.flatlaf.FlatDarkLaf.setup();` with:

```java
            dev.jasper.app.appearance.ThemeTestSupport.install(dev.jasper.app.appearance.Theme.DARK);
```

Run: `./gradlew :jasper-app:test --tests dev.jasper.app.appearance.ThemeButtonsTest --tests dev.jasper.app.workspace.WindowChromeTest --tests dev.jasper.app.workspace.MacTitleBarTest --tests dev.jasper.app.application.JasperApplicationPluginsTest`
Expected: FAIL. `WindowChromeTest` fails with `expected: "IntelliJ Light" but was: "FlatLaf Light"`, and `MacTitleBarTest` and `JasperApplicationPluginsTest` fail on the old light values and LAF class. `ThemeButtonsTest` may already pass on the old path, because it checks that controls match the installed theme's keys rather than pinning values; it guards Step 3's removal of `BrandedButtonUI`.

- [ ] **Step 3: Switch the installer and retire the old path**

In `jasper-app/src/main/java/dev/jasper/app/appearance/ThemeController.java`:

1. Remove the imports `com.formdev.flatlaf.FlatDarkLaf`, `com.formdev.flatlaf.FlatLaf` and `com.formdev.flatlaf.FlatLightLaf`.
2. Replace the `FORM_FONTS` declaration with:

```java
    private static final List<String> FORM_FONTS = List.of("Label.font", "List.font", "TextField.font",
        "PasswordField.font", "FormattedTextField.font", "TextArea.font", "ComboBox.font", "Button.font");
```

3. Replace `public ThemeController(Appearance saved) { this(saved, ThemeController::install); }` with:

```java
    public ThemeController(Appearance saved) { this(saved, ThemeManager::install); }
```

4. Delete the static installer at the end of the class:

```java
    private static boolean defaultsRegistered;
    static boolean install(Theme theme) {
        requireEdt();
        if (!defaultsRegistered) {
            FlatLaf.registerCustomDefaultsSource("dev.jasper.app.themes");
            defaultsRegistered = true;
        }
        return theme == Theme.LIGHT ? FlatLightLaf.setup() : FlatDarkLaf.setup();
    }
```

Replace the body of `jasper-app/src/test/java/dev/jasper/app/appearance/ThemeTestSupport.java` with:

```java
package dev.jasper.app.appearance;

/** Package-local test access, excluded from production artifacts. */
public final class ThemeTestSupport {
    public static boolean install(Theme theme) { return ThemeManager.install(theme); }
}
```

In `jasper-app/src/main/java/dev/jasper/app/application/JasperApplication.java`, derive the SDK variant from the theme's dark flag. Replace each of the four `chrome() == Theme.DARK` comparisons with `chrome().dark()`:
- `themes.current().chrome() == Theme.DARK` → `themes.current().chrome().dark()` (two places);
- `theme.chrome() == Theme.DARK` → `theme.chrome().dark()` (two places).

Then remove the `import dev.jasper.app.appearance.Theme;` line if nothing else in the file uses it.

Delete the old path:

```bash
git rm -q jasper-app/src/main/java/dev/jasper/app/appearance/BrandedButtonUI.java \
  jasper-app/src/main/resources/dev/jasper/app/themes/FlatLaf.properties \
  jasper-app/src/main/resources/dev/jasper/app/themes/FlatLightLaf.properties \
  jasper-app/src/main/resources/dev/jasper/app/themes/FlatDarkLaf.properties
```

- [ ] **Step 4: Run the focused tests to verify they pass**

Run: `./gradlew :jasper-app:test --tests 'dev.jasper.app.appearance.*' --tests dev.jasper.app.workspace.WindowChromeTest --tests dev.jasper.app.workspace.MacTitleBarTest --tests dev.jasper.app.workspace.ThemeControllerTest --tests dev.jasper.app.workspace.SplitDividerTest --tests dev.jasper.app.workspace.ConfigurationStatusTest --tests dev.jasper.app.application.JasperApplicationPluginsTest`
Expected: PASS. This includes `UiTypographyTest` (buttons follow the form font through `Button.font`) and `SplitDividerTest` (the derived divider meets 3:1).

- [ ] **Step 5: Run the whole suite and check for leftovers**

Run: `./gradlew check > /tmp/theme-engine-task5.log 2>&1; tail -5 /tmp/theme-engine-task5.log`
Expected: `BUILD SUCCESSFUL`.

Run: `git grep -nE 'BuiltinTheme|BrandedButtonUI|registerCustomDefaultsSource|FlatLightLaf|Jasper\.(formBackground|formListBackground|controlBackground|comboBackground|buttonBackground|buttonBorder|buttonForeground|accentBackground|primaryBackground|primaryBorder)' -- jasper-app/src plugins`
Expected: no output.

Run the AGENTS.md Python source-hygiene check. Expected: no output.

- [ ] **Step 6: Commit**

```bash
git add -A jasper-app/src
git commit -m "refactor(appearance): install every theme through ThemeManager

Theme replaces BuiltinTheme; the FlatLaf properties overrides and BrandedButtonUI are gone.

🤖 Generated with Claude Code at The Home Depot

Co-Authored-By: Claude <noreply@anthropic.com>"
```

---

### Task 6: Side panels paint ToolWindow.background; the status bar paints StatusBar.*

**Files:**
- Create: `jasper-app/src/main/java/dev/jasper/app/workspace/ToolWindowSurface.java`
- Modify: `workspace/WorkspaceRegions.java`, `workspace/WindowContent.java`, `workspace/WindowStatusBar.java`, `workspace/WindowChrome.java`
- Test (create): `jasper-app/src/test/java/dev/jasper/app/workspace/ToolWindowSurfaceTest.java`
- Test (modify): `jasper-app/src/test/java/dev/jasper/app/workspace/ConfigurationStatusTest.java`

**Interfaces:**
- Consumes: `ThemeTestSupport.install(Theme)` and `Theme.LIGHT`/`Theme.DARK` (Task 5).
- Produces:
  - `ToolWindowSurface.apply(Component root)` (package-private);
  - `WorkspaceRegions.refreshTheme()`;
  - `WindowStatusBar` painting `StatusBar.background` under a `StatusBar.borderColor` top rule.

- [ ] **Step 1: Write the failing tests**

Create `jasper-app/src/test/java/dev/jasper/app/workspace/ToolWindowSurfaceTest.java`:

```java
package dev.jasper.app.workspace;

import dev.jasper.app.appearance.Theme;
import dev.jasper.app.appearance.ThemeTestSupport;
import dev.jasper.app.contributions.PanelRegion;
import dev.jasper.app.testsupport.EdtTestExtension;
import java.awt.BorderLayout;
import java.awt.Color;
import java.util.List;
import javax.swing.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(EdtTestExtension.class)
class ToolWindowSurfaceTest {
    @Test void panelRegionsPaintTheToolWindowColourWhileControlsKeepTheirOwn() throws Exception {
        var original = UIManager.getLookAndFeel();
        try {
            ThemeTestSupport.install(Theme.LIGHT);
            var content = new JPanel(new BorderLayout());
            var inner = new JPanel();
            var field = new JTextField("hosts");
            var scroll = new JScrollPane(new JList<>(new String[]{"prod", "stage"}));
            var fixed = new JPanel(); fixed.setBackground(Color.MAGENTA);
            inner.add(field);
            content.add(inner, BorderLayout.NORTH); content.add(scroll); content.add(fixed, BorderLayout.SOUTH);
            var regions = new WorkspaceRegions(new JPanel());
            regions.show(PanelRegion.LEFT, content, 250);

            Color surface = UIManager.getColor("ToolWindow.background");
            assertThat(surface).isEqualTo(Color.WHITE);
            assertThat(UIManager.getColor("Panel.background")).isNotEqualTo(surface);
            assertThat(content.getParent().getBackground()).as("region host").isEqualTo(surface);
            assertThat(content.getBackground()).isEqualTo(surface);
            assertThat(inner.getBackground()).isEqualTo(surface);
            assertThat(scroll.getViewport().getBackground()).isEqualTo(surface);
            assertThat(field.getBackground()).isEqualTo(UIManager.getColor("TextField.background"));
            assertThat(fixed.getBackground()).as("a plugin's own colour").isEqualTo(Color.MAGENTA);
            var later = new JPanel();
            inner.add(later);
            assertThat(later.getBackground()).as("added after the panel opened").isEqualTo(surface);

            ThemeTestSupport.install(Theme.DARK);
            SwingUtilities.updateComponentTreeUI(regions);
            regions.refreshTheme();
            Color dark = UIManager.getColor("ToolWindow.background");
            assertThat(dark).isEqualTo(new Color(0x21252b));
            for (var component : List.of(content, inner, later)) assertThat(component.getBackground()).isEqualTo(dark);
            assertThat(fixed.getBackground()).isEqualTo(Color.MAGENTA);
        } finally { UIManager.setLookAndFeel(original); }
    }
}
```

In `jasper-app/src/test/java/dev/jasper/app/workspace/ConfigurationStatusTest.java`, replace both occurrences of

```java
assertThat(status.getBackground()).isEqualTo(UIManager.getColor("Jasper.titleBackground"));
```

with

```java
assertThat(status.getBackground()).isEqualTo(UIManager.getColor("StatusBar.background"));
```

and add this test to the class:

```java
    @Test void statusBarPaintsTheThemesStatusSurfaceUnderATopRule() throws Exception {
        edt(() -> {
            var themes = new ThemeController();
            try {
                for (var theme : List.of(Theme.DARK, Theme.LIGHT)) {
                    themes.select(theme);
                    var status = new WindowStatusBar();
                    status.setSize(600, 30); status.doLayout();
                    var image = new java.awt.image.BufferedImage(600, 30, java.awt.image.BufferedImage.TYPE_INT_RGB);
                    var graphics = image.createGraphics(); status.paint(graphics); graphics.dispose();
                    assertThat(new Color(image.getRGB(300, 0))).as(theme.name()).isEqualTo(UIManager.getColor("StatusBar.borderColor"));
                    assertThat(new Color(image.getRGB(300, 15))).as(theme.name()).isEqualTo(UIManager.getColor("StatusBar.background"));
                }
            } finally { themes.select(Theme.DARK); }
        });
    }
```

Run: `./gradlew :jasper-app:test --tests dev.jasper.app.workspace.ToolWindowSurfaceTest --tests dev.jasper.app.workspace.ConfigurationStatusTest`
Expected: FAIL. `ToolWindowSurfaceTest` fails to compile on `regions.refreshTheme()`; once it compiles, the host is `Panel.background`, and the status rule pixel is `StatusBar.background`.

- [ ] **Step 2: Implement ToolWindowSurface**

Create `jasper-app/src/main/java/dev/jasper/app/workspace/ToolWindowSurface.java`:

```java
package dev.jasper.app.workspace;

import java.awt.Color;
import java.awt.Component;
import java.awt.Container;
import java.awt.event.ContainerEvent;
import java.awt.event.ContainerListener;
import java.util.Arrays;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JToolBar;
import javax.swing.JViewport;
import javax.swing.UIManager;
import javax.swing.plaf.ColorUIResource;
import javax.swing.plaf.UIResource;

/**
 * Paints a panel region in the theme's tool window colour, as IntelliJ paints its tool windows. Plain
 * containers inside the region take it while their background is still the look and feel's; controls
 * keep their own colours and a background a plugin set is left alone. Containers added later take it
 * too. A theme change resets it with the other defaults, so the owner reapplies it. EDT only.
 */
final class ToolWindowSurface {
    private static final ContainerListener ADDED = new ContainerListener() {
        @Override public void componentAdded(ContainerEvent event) { paint(event.getChild(), colour()); }
        @Override public void componentRemoved(ContainerEvent event) { }
    };

    private ToolWindowSurface() {}

    static void apply(Component root) { paint(root, colour()); }

    private static ColorUIResource colour() {
        Color colour = UIManager.getColor("ToolWindow.background");
        return new ColorUIResource(colour != null ? colour : UIManager.getColor("Panel.background"));
    }

    private static void paint(Component component, ColorUIResource colour) {
        if (surface(component) && (component.getBackground() == null || component.getBackground() instanceof UIResource))
            component.setBackground(colour);
        if (component instanceof Container container) {
            if (!Arrays.asList(container.getContainerListeners()).contains(ADDED)) container.addContainerListener(ADDED);
            for (Component child : container.getComponents()) paint(child, colour);
        }
    }

    private static boolean surface(Component component) {
        return component instanceof JPanel || component instanceof JViewport || component instanceof JScrollPane
            || component instanceof JToolBar;
    }
}
```

- [ ] **Step 3: Wire the regions, the window and the status bar**

In `jasper-app/src/main/java/dev/jasper/app/workspace/WorkspaceRegions.java`, replace `host(PanelRegion region)`'s last three lines

```java
        host.removeAll();
        host.add(contents.get(region), BorderLayout.CENTER);
        return host;
    }
```

with

```java
        host.removeAll();
        host.add(contents.get(region), BorderLayout.CENTER);
        ToolWindowSurface.apply(host);
        return host;
    }

    /** Reapplies the tool window colour after a theme change reset the region backgrounds. */
    void refreshTheme() { hosts.values().forEach(ToolWindowSurface::apply); }
```

In `jasper-app/src/main/java/dev/jasper/app/workspace/WindowContent.java`, in `applyTheme`, replace

```java
            rail.refreshTheme();
```

with

```java
            rail.refreshTheme();
            regions.refreshTheme();
```

In `jasper-app/src/main/java/dev/jasper/app/workspace/WindowStatusBar.java`, replace

```java
    private Color muted() { return UIManager.getColor("Jasper.mutedForeground"); }
    void refreshTheme() {
        setBackground(UIManager.getColor("Jasper.titleBackground"));
```

with

```java
    private Color muted() { return UIManager.getColor("Jasper.mutedForeground"); }
    private static Color surface() {
        Color colour = UIManager.getColor("StatusBar.background");
        return colour != null ? colour : UIManager.getColor("Panel.background");
    }
    private static Color rule() {
        Color colour = UIManager.getColor("StatusBar.borderColor");
        return colour != null ? colour : UIManager.getColor("Jasper.titleSeparator");
    }
    void refreshTheme() {
        setBackground(surface());
```

and in `paintComponent` replace

```java
        super.paintComponent(g);
        g.setColor(running ? UIManager.getColor("Jasper.runningForeground") : muted());
```

with

```java
        super.paintComponent(g);
        g.setColor(rule());
        g.fillRect(0, 0, getWidth(), UIScale.scale(1));
        g.setColor(running ? UIManager.getColor("Jasper.runningForeground") : muted());
```

In `jasper-app/src/main/java/dev/jasper/app/workspace/WindowChrome.java`, in `refreshTheme()`, replace

```java
        status.setBackground(UIManager.getColor("Jasper.titleBackground"));
```

with

```java
        status.refreshTheme();
```

- [ ] **Step 4: Run the tests to verify they pass**

Run: `./gradlew :jasper-app:test --tests dev.jasper.app.workspace.ToolWindowSurfaceTest --tests dev.jasper.app.workspace.ConfigurationStatusTest --tests dev.jasper.app.workspace.WorkspaceRegionsTest --tests dev.jasper.app.workspace.MockUiTest --tests dev.jasper.app.workspace.ThemeControllerTest`
Expected: PASS. `ThemeControllerTest` still asserts that the rail uses `Jasper.titleBackground` (planning decision 1).

- [ ] **Step 5: Run the whole suite and commit**

Run: `./gradlew check > /tmp/theme-engine-task6.log 2>&1; tail -5 /tmp/theme-engine-task6.log`
Expected: `BUILD SUCCESSFUL`.

```bash
git add jasper-app/src/main/java/dev/jasper/app/workspace jasper-app/src/test/java/dev/jasper/app/workspace
git commit -m "feat(workspace): paint side panels and the status bar in the theme's tool window colours

🤖 Generated with Claude Code at The Home Depot

Co-Authored-By: Claude <noreply@anthropic.com>"
```

---

### Task 7: Documentation and status

**Files:**
- Modify: `docs/configuration.md`, `docs/sdk-architecture.md`, `docs/plugin-authoring.md`, `docs/credential-vault.md`, `docs/app-architecture.md`, `docs/app-maintenance.md`, `docs/STATUS.md`

- [ ] **Step 1: Update the configuration guide**

In `docs/configuration.md`, under `## Theme variant`, replace the first paragraph

```markdown
`ui.theme.variant` selects one of the two bundled themes. `"dark"` (the default) selects
FlatLaf Dark chrome and `"light"` FlatLaf Light chrome; by default the terminal uses the matching
Jasper Dark or Jasper Light palette (see [Terminal colors](#terminal-colors)). Jasper reads no
custom theme files. An unrecognized variant produces an error diagnostic and keeps the default;
a non-string value rejects the configuration.
```

with

```markdown
`ui.theme.variant` selects one of the two bundled themes. `"dark"` (the default) selects Jasper
Dark and `"light"` classic IntelliJ Light, the theme IntelliJ IDEA ships for its classic UI. By
default the terminal uses the matching Jasper Dark or Jasper Light palette (see [Terminal
colors](#terminal-colors)). Both are IntelliJ-format `.theme.json` files installed through one
theme engine; Jasper does not yet read theme files of your own. An unrecognized variant produces
an error diagnostic and keeps the default; a non-string value rejects the configuration.
```

- [ ] **Step 2: Update the SDK, plugin and vault docs**

In `docs/sdk-architecture.md`, replace the paragraph from "The application's look and feel supplies `BrandedButtonUI`" through "Toolbar button geometry stays independent (30-pixel height, 12-pixel arc)." with:

```markdown
Ordinary Swing buttons, fields, lists and dropdowns in any plugin layout, including content
added later, are drawn by FlatLaf in the installed theme's colours: classic IntelliJ Light or
Jasper Dark. Form text, button labels included, uses the 12-point form font and follows UI font
changes. Plugin side panels paint the theme's tool window colour (`ToolWindow.background`) behind
plain containers; a background a plugin sets itself is kept. Toolbar button geometry stays
independent (30-pixel height, 12-pixel arc).
```

In `docs/plugin-authoring.md`, under `## Consistent buttons, flexible layouts`, replace the paragraph from "Use ordinary `JButton` components inside plugin content." through "disabled button text retains Jasper's contrast-tested defaults." with:

```markdown
Use ordinary `JButton` components inside plugin content. They are drawn by FlatLaf in the
installed theme's colours, classic IntelliJ Light or Jasper Dark, with 12-point labels; text
fields, dropdowns and lists use the theme's own colours and selection. Leave plain panels at the
look and feel's background so a side panel shows the theme's tool window colour, and read any
colour you need from `UIManager` keys rather than hard-coding it, so it follows a theme change.
```

In `docs/credential-vault.md`, replace

```markdown
   Compare buttons, inputs and list colors to the TermLab reference in light and dark themes.
```

with

```markdown
   Compare buttons, inputs and list colors with IntelliJ Light and Jasper Dark.
```

- [ ] **Step 3: Update the architecture and maintenance guides**

In `docs/app-architecture.md`, under `## Resources and verification`, replace

```markdown
FlatLaf defaults and shell scripts keep their existing classpath paths. Reflection-based
class references in theme properties must track package moves. Icons use absolute paths;
```

with

```markdown
Theme files and shell scripts keep their classpath paths. `ThemeManager.APP_DEFAULTS` names
`SplitDividerBorder` by class name, so it must track package moves. Icons use absolute paths;
```

Append this section at the end of `docs/app-architecture.md`:

```markdown
## Themes

Every theme is an IntelliJ-format `.theme.json` under `dev/jasper/app/themes/`, installed by one
path in `ThemeManager`:
1. `ThemeLoader` resolves the `parentTheme` chain (parent entries first, the child's replacing
   them in its order, so FlatLaf's in-order wildcards match IntelliJ's precedence), resolves the
   `colors` table and drops IntelliJ implementation classes.
2. FlatLaf's `IntelliJTheme` builds the look and feel with `ThemeManager.APP_DEFAULTS` (form
   typography, split divider) as extra defaults.
3. The manager puts back the explicit colours FlatLaf skips (IntelliJ-only namespaces such as
   `SearchEverywhere.*`, `EditorTabs.*`, `ToolWindow.*`, `StatusBar.*`).
4. `ChromeKeys` fills each `Jasper.*` chrome key the theme did not set from IntelliJ keys, with
   contrast floors for derived text.

Restored and derived keys are also recorded as the instance's extra defaults, so a rollback to it
keeps them. The built-ins are `Theme.LIGHT` (classic IntelliJ Light, vendored from
intellij-community with provenance under `themes/intellij/`) and `Theme.DARK` (`jasper-dark.theme.json`).
Side panels paint `ToolWindow.background` through `ToolWindowSurface`; the rail stays chrome.
```

In `docs/app-maintenance.md`, add this section before `## Maintaining SDK icons`:

```markdown
## Themes

To change Jasper Dark, edit `jasper-app/src/main/resources/dev/jasper/app/themes/jasper-dark.theme.json`
using IntelliJ keys; `Jasper.*` keys set there win over derivation. To refresh the vendored IntelliJ
themes, change `SHA` in `tools/themes/fetch-intellij-themes.py` and rerun it from the repository
root, then update the pinned hashes in `IntellijThemesTest`. To add a chrome key, add it to
`ChromeKeys.KEYS` and `ChromeKeys.derive` with a fallback FlatLaf always defines, and cover it in
`ChromeKeysTest`. Run `ThemeLoaderTest`, `ChromeKeysTest`, `ThemeManagerTest` (IntelliJ Light
values and Jasper Dark parity) and `ThemeButtonsTest`, then `check`. Light and dark visual
acceptance is a user check.
```

- [ ] **Step 4: Add the STATUS entry**

In `docs/STATUS.md`, under `## Current state — 2026-09-23`, add this bullet after the "One layout" bullet:

```markdown
- **Theme engine** (branch `claude/theme-engine`, plan
  `docs/superpowers/plans/2026-09-25-jasper-theme-engine.md`):
  - **Themes:** every theme is an IntelliJ-format `.theme.json` installed by `ThemeManager`. Light
    is classic IntelliJ Light, vendored from intellij-community at `f7377708`. Dark is
    `jasper-dark.theme.json`, keeping today's colours.
  - **Keys:** `ChromeKeys` derives the `Jasper.*` chrome keys a theme leaves unset.
  - **Surfaces:** side panels paint `ToolWindow.background`, and the status bar paints `StatusBar.*`
    under a 1-px rule.
  - **Removed:** the FlatLaf properties overrides, `BrandedButtonUI` and `BuiltinTheme`.
  - **Planning decisions:** listed in the plan header. They cover the rail staying chrome, contrast
    floors for derived text, FlatLaf form-control heights, and Jasper Dark menus using the list
    selection.
  - **Not done:** visual acceptance of Light and Dark (user-run), merge and push.
```

- [ ] **Step 5: Verify and commit**

Run: `./gradlew check > /tmp/theme-engine-task7.log 2>&1; tail -5 /tmp/theme-engine-task7.log`
Expected: `BUILD SUCCESSFUL`.

Run: `git grep -nE 'FlatLaf (Light|Dark) chrome|BrandedButtonUI|TermLab reference' -- docs/*.md`
Expected: no output.

```bash
git add docs
git commit -m "docs(appearance): document the theme engine and IntelliJ Light

🤖 Generated with Claude Code at The Home Depot

Co-Authored-By: Claude <noreply@anthropic.com>"
```
