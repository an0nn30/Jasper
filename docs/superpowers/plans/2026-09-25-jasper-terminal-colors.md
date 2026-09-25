# Terminal colours independent of the UI Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add `ui.theme.terminal = "match" | "light" | "dark"` and three View ▸ Appearance items so the terminal palette can differ from the UI's light or dark chrome, switching live.

**Architecture:**
- `ThemeState` resolves the palette from a new `TerminalColors` choice instead of always using `chrome.palette()`.
- `ThemeController` exposes the new choice and notifies listeners without reinstalling the look and feel when only the palette changes.
- Every existing consumer of `ResolvedTheme.palette()` (panes, views, the tab content area, new panes) follows automatically.
- The chrome keeps reading `UIManager`.

**Tech Stack:** Java 25 on JBR 25, Swing, FlatLaf 3.7, tomlj config, JUnit 5, AssertJ, Gradle wrapper.

**Spec:** [2026-09-25-jasper-terminal-colors-design.md](../specs/2026-09-25-jasper-terminal-colors-design.md)

**Status:** Not started. Branch `claude/intellij-chrome` (unmerged; continues the IntelliJ chrome work), worktree `/Users/RQ7RQVF/projects/moray/.worktrees/intellij-chrome`. Another session works in the main checkout: never run git in `/Users/RQ7RQVF/projects/moray` itself.

## Global Constraints

- **Config key and values:**
  - The new key is `ui.theme.terminal`, with values `"match"`, `"light"` and `"dark"`. The default is `"match"`, so existing configs look unchanged.
  - An invalid value follows the existing per-field policy of `ui.theme.style` and `ui.theme.variant`: a diagnostic on that key, a non-string value rejects, and the default is kept.
- **The new enum** is exactly `dev.jasper.app.config.TerminalColors { MATCH, LIGHT, DARK }`.
- **How the palette resolves:**
  - MATCH → `chrome.palette()`;
  - LIGHT → `BuiltinTheme.LIGHT.palette()` (`Palette.jasperLight()`);
  - DARK → `BuiltinTheme.DARK.palette()` (`Palette.jasperDark()`).
- **Retro** always resolves `RetroPalette` and ignores the setting. Its View items are disabled.
- **Live apply:** changes apply live, with no restart notice. A palette-only change must not reinstall the look and feel, and listeners receive `chromeChanged == false`.
- **Menu:** View menu labels are exactly `Terminal: Match UI`, `Terminal: Light` and `Terminal: Dark`. The choice lasts for the session and never rewrites the config. A changed saved value clears it.
- **What follows the palette:** only terminal views, their `TerminalPane` backgrounds and padding, and the tab content area behind the panes. The title row, toolbar, tab strip, find bar, status bar, split dividers, menus and dialogs keep the UI chrome.
- **No SDK change:** `Appearance.variant()` keeps reporting the UI chrome.
- **Process rules:**
  - Build with `./gradlew` only.
  - Never launch the GUI.
  - Make one commit per task, ending with the trailer below. Do not push.

    ```
    🤖 Generated with Claude Code at The Home Depot

    Co-Authored-By: Claude <noreply@anthropic.com>
    ```
  - Run the AGENTS.md source-hygiene Python check before each commit.

## Review Focus

1. **The find bar lives inside the pane.** A dark terminal on a light UI must leave the find field and its labels in UI colours. Task 3 has an assertion on the find field's foreground and background.
2. **Hidden tabs and panes created later must take the palette.** Background tabs repaint, and a tab or pane opened after the switch starts in the chosen palette. Task 3 covers both, with a second tab and a pane opened after the switch.
3. **Several windows share one `ThemeController`.** A choice made in one window must recolour the terminals in every window. Task 3 covers this with two owners.
4. **Changing the UI variant keeps a fixed terminal choice.** A reload or View change of `variant` with `terminal = "dark"` must change the chrome but keep the dark palette, and must report `chromeChanged == true`. Task 2 covers this.
5. **A repeated choice is quiet.** Choosing the same terminal option again must not notify listeners. Task 2 covers this.

---

### Task 1: `ui.theme.terminal` configuration

**Files:**
- Create: `jasper-app/src/main/java/dev/jasper/app/config/TerminalColors.java`
- Modify: `jasper-app/src/main/java/dev/jasper/app/config/ConfigLoader.java` (known keys at line 51, fields around line 90, snapshot construction around lines 116-119, `readField` after the `ui.theme.variant` case around line 226)
- Modify: `jasper-app/src/main/java/dev/jasper/app/config/ConfigSnapshot.java` (canonical record, a new compatibility constructor, `Builder`)
- Modify: `jasper-app/src/main/java/dev/jasper/app/config/ConfigTemplate.java` (the `[ui.theme]` block around lines 126-133)
- Modify: `config.example.toml` (the `[ui.theme]` block around lines 101-109)
- Test: `jasper-app/src/test/java/dev/jasper/app/config/ConfigLoaderTest.java`, `ConfigSnapshotBuilderTest.java`, `ConfigTemplateTest.java:61`

**Interfaces:**
- Produces:
  - `public enum TerminalColors { MATCH, LIGHT, DARK }` in `dev.jasper.app.config`;
  - the record component `TerminalColors terminalColors()` on `ConfigSnapshot`, the last component;
  - `ConfigSnapshot.Builder terminalColors(TerminalColors)`.

- [ ] **Step 1: Write the failing tests**

Append to `ConfigLoaderTest`, which already has `private ConfigLoader.Result parse(String text)` and imports `Appearance`:

```java
    @Test void terminalColorsDefaultToMatchAndAcceptEveryChoice() {
        assertThat(parse("").snapshot().terminalColors()).isEqualTo(TerminalColors.MATCH);
        for (var choice : TerminalColors.values())
            assertThat(parse("[ui.theme]\nterminal = '" + choice.name().toLowerCase(java.util.Locale.ROOT) + "'\n")
                .snapshot().terminalColors()).isEqualTo(choice);
        var mixed = parse("ui.theme.terminal = 'dark'\nui.theme.variant = 'light'\n").snapshot();
        assertThat(mixed.variant()).isEqualTo(Appearance.LIGHT);
        assertThat(mixed.terminalColors()).isEqualTo(TerminalColors.DARK);
    }

    @Test void invalidTerminalColorsUseTheExistingPerFieldDiagnosticPolicy() {
        for (String value : java.util.List.of("'private-value'", "7", "true")) {
            var result = parse("[ui.theme]\nterminal=" + value + "\nvariant='light'\n");
            assertThat(result.rejected()).isEqualTo(!value.startsWith("'"));
            assertThat(result.snapshot().terminalColors()).isEqualTo(TerminalColors.MATCH);
            assertThat(result.snapshot().variant()).isEqualTo(Appearance.LIGHT);
            assertThat(result.diagnostics()).singleElement().satisfies(problem -> {
                assertThat(problem.key()).isEqualTo("ui.theme.terminal");
                assertThat(problem.line()).isEqualTo(2);
                assertThat(problem.message()).doesNotContain("private-value");
            });
        }
    }
```

Append to `ConfigSnapshotBuilderTest`:

```java
    @Test void terminalColorsRoundTripThroughTheBuilderAndDefaultToMatch() {
        assertThat(ConfigSnapshot.defaults().terminalColors()).isEqualTo(TerminalColors.MATCH);
        var dark = ConfigSnapshot.builder().terminalColors(TerminalColors.DARK).build();
        assertThat(dark.terminalColors()).isEqualTo(TerminalColors.DARK);
        assertThat(dark.toBuilder().tabHeight(40).build().terminalColors()).isEqualTo(TerminalColors.DARK);
    }
```

In `ConfigTemplateTest.java` line 61, change `containsExactlyInAnyOrder("style", "variant")` to `containsExactlyInAnyOrder("style", "variant", "terminal")`.

- [ ] **Step 2: Run the tests to verify they fail**

Run: `./gradlew :jasper-app:test --tests 'dev.jasper.app.config.*'`
Expected: compilation fails, because `TerminalColors` does not exist.

- [ ] **Step 3: Implement**

Create `TerminalColors.java`:

```java
package dev.jasper.app.config;

/** Saved terminal palette choice, independent of the UI chrome variant; MATCH follows the chrome. */
public enum TerminalColors { MATCH, LIGHT, DARK }
```

In `ConfigLoader`:
- change `Map.entry(List.of("ui", "theme"), Set.of("variant", "style"))` to `Set.of("variant", "style", "terminal")`;
- add the field `private TerminalColors terminalColors = TerminalColors.MATCH;` after `private ThemeStyle style = ThemeStyle.MODERN;`;
- add after the `case "ui.theme.variant" -> …` case:

```java
            case "ui.theme.terminal" -> terminalColors = choice(path, value, Map.of(
                "match", TerminalColors.MATCH, "light", TerminalColors.LIGHT, "dark", TerminalColors.DARK), terminalColors);
```

- in the snapshot construction (the call ending `plugins, style, new UiFontConfig(uiFontFamily, uiFontSize))`), append the argument so that it ends `plugins, style, new UiFontConfig(uiFontFamily, uiFontSize), terminalColors)`.

In `ConfigSnapshot`:
- append `, TerminalColors terminalColors` as the last record component, after `UiFontConfig uiFont`;
- in the compact constructor add `Objects.requireNonNull(terminalColors, "terminalColors");`;
- add this compatibility constructor directly after the compact constructor:

```java
    /** Compatibility constructor from before independent terminal colours: the terminal matches the UI. */
    public ConfigSnapshot(int tabHeight, ToolbarMode toolbar, boolean statusBar,
                          FontConfig font, Appearance variant, Map<String, String> keybindings,
                          int columns, int lines, TerminalConfig terminal, boolean buddyEnabled,
                          int maxResults, int longCommandSeconds, boolean backgroundEnabled,
                          Map<String, Map<String, Object>> plugins, ThemeStyle style, UiFontConfig uiFont) {
        this(tabHeight, toolbar, statusBar, font, variant, keybindings, columns, lines,
            terminal, buddyEnabled, maxResults, longCommandSeconds, backgroundEnabled,
            plugins, style, uiFont, TerminalColors.MATCH);
    }
```

  Every other existing constructor already delegates to that 16-argument form and keeps compiling.
- in `Builder`:
  - add the field `private TerminalColors terminalColors;`;
  - add `terminalColors = source.terminalColors();` in `Builder(ConfigSnapshot source)`;
  - add the setter `public Builder terminalColors(TerminalColors value) { terminalColors = value; return this; }`;
  - change `build()` to pass `…, plugins, style, uiFont, terminalColors)`.

In `ConfigTemplate`, replace the two variant lines

```
            # Variant applies only to modern; retro always uses light controls and a dark terminal.
            # Theme variant: "dark" or "light"; switches chrome and terminal colors live across windows.
            # variant = "dark"
```

with

```
            # Variant applies only to modern; retro always uses light controls and a dark terminal.
            # Theme variant: "dark" or "light"; switches chrome colors live across windows.
            # variant = "dark"
            # Terminal colors: "match" follows the variant; "light" or "dark" keeps the terminal
            # independent of the UI. Switches live; ignored in retro.
            # terminal = "match"
```

In `config.example.toml`, replace

```
# Modern only: "dark" or "light"; chrome and terminal colors switch together.
variant = "dark"
```

with

```
# Modern only: "dark" or "light" for the UI chrome.
variant = "dark"
# Modern only: "match" (follow variant), "light" or "dark" terminal colors.
terminal = "match"
```

- [ ] **Step 4: Run the tests to verify they pass**

Run: `./gradlew :jasper-app:test --tests 'dev.jasper.app.config.*'`
Expected: pass. If a template-coverage assertion elsewhere in `ConfigTemplateTest` enumerates template keys, add `terminal` there and record the change.

- [ ] **Step 5: Commit**

```bash
git add jasper-app/src config.example.toml
git commit -m "feat(config): add ui.theme.terminal for terminal colours independent of the UI

🤖 Generated with Claude Code at The Home Depot

Co-Authored-By: Claude <noreply@anthropic.com>"
```

---

### Task 2: Resolve the terminal palette independently and apply it live

**Files:**
- Modify: `jasper-app/src/main/java/dev/jasper/app/appearance/ThemeState.java` (whole file)
- Modify: `jasper-app/src/main/java/dev/jasper/app/appearance/ThemeController.java` (the `choice()`, `selectAppearance`, `configure` and `apply` area, lines 61-111)
- Modify: `jasper-app/src/main/java/dev/jasper/app/application/ConfigurationController.java:56` and `:100`
- Test: `jasper-app/src/test/java/dev/jasper/app/appearance/ThemeStateTest.java` (add tests); create `jasper-app/src/test/java/dev/jasper/app/appearance/TerminalColorsThemeTest.java`; add a test to `jasper-app/src/test/java/dev/jasper/app/workspace/ConfigurationControllerTest.java`

**Interfaces:**
- Consumes: `TerminalColors` and `ConfigSnapshot.terminalColors()` (Task 1).
- Produces:
  - `public TerminalColors ThemeController.terminalColors()`: the effective choice, which is `MATCH` in retro;
  - `public void ThemeController.selectTerminalColors(TerminalColors)`: a session override, a no-op in retro;
  - `public void ThemeController.configure(Appearance saved, TerminalColors terminal, UiFontConfig font)`.
  - The existing `configure(Appearance)` and `configure(Appearance, UiFontConfig)` keep the saved terminal value unchanged.

- [ ] **Step 1: Write the failing tests**

Append to `ThemeStateTest`, and add the import `dev.jasper.app.config.TerminalColors`:

```java
    @Test void terminalChoiceResolvesThePaletteIndependentlyOfTheChrome() {
        for (Appearance ui : Appearance.values()) {
            var base = ThemeState.defaults().configure(ui);
            BuiltinTheme chrome = BuiltinTheme.of(ui);
            assertThat(base.configureTerminal(TerminalColors.MATCH).resolve()).isEqualTo(new ResolvedTheme(chrome, chrome.palette()));
            assertThat(base.configureTerminal(TerminalColors.LIGHT).resolve()).isEqualTo(new ResolvedTheme(chrome, Palette.jasperLight()));
            assertThat(base.configureTerminal(TerminalColors.DARK).resolve()).isEqualTo(new ResolvedTheme(chrome, Palette.jasperDark()));
        }
    }

    @Test void temporaryTerminalChoiceOverridesSavedUntilTheSavedValueChanges() {
        var chosen = ThemeState.defaults().chooseTerminal(TerminalColors.LIGHT);
        assertThat(chosen.terminalChoice()).isEqualTo(TerminalColors.LIGHT);
        assertThat(chosen.configureTerminal(TerminalColors.MATCH).terminalChoice())
            .as("rewriting the same saved value keeps the session choice").isEqualTo(TerminalColors.LIGHT);
        assertThat(chosen.configureTerminal(TerminalColors.DARK).terminalOverride()).isNull();
        assertThat(chosen.configureTerminal(TerminalColors.DARK).terminalChoice()).isEqualTo(TerminalColors.DARK);
        assertThat(chosen.configure(Appearance.LIGHT).terminalChoice())
            .as("a UI variant change keeps the terminal choice").isEqualTo(TerminalColors.LIGHT);
    }
```

Create `TerminalColorsThemeTest.java`:

```java
package dev.jasper.app.appearance;

import dev.jasper.app.config.Appearance;
import dev.jasper.app.config.TerminalColors;
import dev.jasper.app.config.ThemeStyle;
import dev.jasper.app.config.UiFontConfig;
import dev.jasper.app.testsupport.EdtTestExtension;
import dev.jasper.terminal.config.Palette;
import java.util.ArrayList;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(EdtTestExtension.class)
class TerminalColorsThemeTest {
    @Test void aTerminalOnlyChangeNotifiesOnceWithoutReinstallingTheLookAndFeel() {
        var installs = new ArrayList<BuiltinTheme>();
        var themes = new ThemeController(ThemeStyle.MODERN, Appearance.LIGHT, theme -> { installs.add(theme); return ThemeTestSupport.install(theme); });
        try {
            var chromeFlags = new ArrayList<Boolean>();
            var palettes = new ArrayList<Palette>();
            themes.subscribe((theme, chromeChanged) -> { chromeFlags.add(chromeChanged); palettes.add(theme.palette()); });
            installs.clear(); chromeFlags.clear(); palettes.clear();
            themes.selectTerminalColors(TerminalColors.DARK);
            assertThat(installs).isEmpty();
            assertThat(chromeFlags).containsExactly(false);
            assertThat(palettes).containsExactly(Palette.jasperDark());
            assertThat(themes.current()).isEqualTo(new ResolvedTheme(BuiltinTheme.LIGHT, Palette.jasperDark()));
            assertThat(themes.terminalColors()).isEqualTo(TerminalColors.DARK);
            themes.selectTerminalColors(TerminalColors.DARK);
            assertThat(chromeFlags).as("repeating the same choice is quiet").hasSize(1);
        } finally { new ThemeController(); }
    }

    @Test void changingTheUiVariantKeepsAFixedTerminalPalette() {
        var themes = new ThemeController(ThemeStyle.MODERN, Appearance.DARK, ThemeTestSupport::install);
        try {
            themes.configure(Appearance.DARK, TerminalColors.DARK, UiFontConfig.defaults());
            var chromeFlags = new ArrayList<Boolean>();
            themes.subscribe((theme, chromeChanged) -> chromeFlags.add(chromeChanged));
            chromeFlags.clear();
            themes.configure(Appearance.LIGHT, TerminalColors.DARK, UiFontConfig.defaults());
            assertThat(themes.current()).isEqualTo(new ResolvedTheme(BuiltinTheme.LIGHT, Palette.jasperDark()));
            assertThat(chromeFlags).containsExactly(true);
            themes.selectAppearance(Appearance.DARK);
            assertThat(themes.current()).isEqualTo(new ResolvedTheme(BuiltinTheme.DARK, Palette.jasperDark()));
            themes.configure(Appearance.LIGHT, TerminalColors.MATCH, UiFontConfig.defaults());
            assertThat(themes.current().palette()).isEqualTo(Palette.jasperLight());
        } finally { new ThemeController(); }
    }

    @Test void retroKeepsItsPaletteForEveryTerminalChoice() {
        var themes = new ThemeController(ThemeStyle.RETRO, Appearance.LIGHT, ThemeTestSupport::install);
        try {
            for (var choice : TerminalColors.values()) {
                themes.configure(Appearance.DARK, choice, UiFontConfig.defaults());
                themes.selectTerminalColors(TerminalColors.LIGHT);
                assertThat(themes.current().palette()).isEqualTo(BuiltinTheme.RETRO.palette());
                assertThat(themes.terminalColors()).isEqualTo(TerminalColors.MATCH);
            }
        } finally { new ThemeController(); }
    }
}
```

Append to `ConfigurationControllerTest`, and add the imports `dev.jasper.app.appearance.ResolvedTheme`, `dev.jasper.app.appearance.BuiltinTheme` and `dev.jasper.terminal.config.Palette` if they are missing:

```java
    @Test void savedTerminalColorsApplyLiveOnStartAndReloadWithoutARestartNotice() throws Exception {
        start("[ui.theme]\nvariant='light'\nterminal='dark'\n");
        edt(() -> assertThat(themes.current()).isEqualTo(new ResolvedTheme(BuiltinTheme.LIGHT, Palette.jasperDark())));
        reload("[ui.theme]\nvariant='light'\nterminal='match'\n");
        edt(() -> {
            assertThat(themes.current()).isEqualTo(new ResolvedTheme(BuiltinTheme.LIGHT, Palette.jasperLight()));
            assertThat(controller.shown().diagnostics()).noneMatch(d -> d.key().startsWith("ui.theme"));
        });
    }
```

- [ ] **Step 2: Run the tests to verify they fail**

Run: `./gradlew :jasper-app:test --tests 'dev.jasper.app.appearance.*' --tests dev.jasper.app.workspace.ConfigurationControllerTest`
Expected: compilation fails (`configureTerminal`, `selectTerminalColors` and the three-argument `configure` are missing).

- [ ] **Step 3: Implement**

Replace `ThemeState.java`:

```java
package dev.jasper.app.appearance;

import dev.jasper.app.config.Appearance;
import dev.jasper.app.config.TerminalColors;
import dev.jasper.terminal.config.Palette;
import java.util.Objects;

/**
 * The saved variant and terminal colours, each with an optional temporary View choice. The chrome
 * follows the variant; the terminal palette follows the terminal choice, where MATCH uses the chrome's.
 */
record ThemeState(Appearance saved, Appearance override, TerminalColors terminalSaved, TerminalColors terminalOverride) {
    ThemeState { Objects.requireNonNull(saved); Objects.requireNonNull(terminalSaved); }

    static ThemeState defaults() { return new ThemeState(Appearance.DARK, null, TerminalColors.MATCH, null); }

    /** A changed saved variant clears the temporary choice; rewriting the same value keeps it. */
    ThemeState configure(Appearance next) {
        return new ThemeState(next, saved == next ? override : null, terminalSaved, terminalOverride);
    }

    /** A changed saved terminal choice clears its temporary choice; rewriting the same value keeps it. */
    ThemeState configureTerminal(TerminalColors next) {
        return new ThemeState(saved, override, Objects.requireNonNull(next), terminalSaved == next ? terminalOverride : null);
    }

    ThemeState choose(Appearance next) {
        return new ThemeState(saved, Objects.requireNonNull(next), terminalSaved, terminalOverride);
    }

    ThemeState chooseTerminal(TerminalColors next) {
        return new ThemeState(saved, override, terminalSaved, Objects.requireNonNull(next));
    }

    Appearance choice() { return override == null ? saved : override; }

    TerminalColors terminalChoice() { return terminalOverride == null ? terminalSaved : terminalOverride; }

    ResolvedTheme resolve() {
        BuiltinTheme theme = BuiltinTheme.of(choice());
        Palette palette = switch (terminalChoice()) {
            case MATCH -> theme.palette();
            case LIGHT -> BuiltinTheme.LIGHT.palette();
            case DARK -> BuiltinTheme.DARK.palette();
        };
        return new ResolvedTheme(theme, palette);
    }
}
```

In `ThemeController.java`:
- add the import `dev.jasper.app.config.TerminalColors`;
- replace the block from `public Appearance choice() {` through the end of `public void configure(Appearance saved, UiFontConfig font) { … }` with:

```java
    public Appearance choice() {
        requireEdt(); return style == ThemeStyle.RETRO ? Appearance.LIGHT : state.choice();
    }
    /** The effective terminal colours; retro always reports MATCH because its palette is fixed. */
    public TerminalColors terminalColors() {
        requireEdt(); return style == ThemeStyle.RETRO ? TerminalColors.MATCH : state.terminalChoice();
    }
    public void selectAppearance(Appearance choice) {
        requireEdt(); Objects.requireNonNull(choice);
        if (style == ThemeStyle.MODERN) apply(state.choose(choice));
    }
    /** A temporary View choice for every window; retro ignores it. */
    public void selectTerminalColors(TerminalColors choice) {
        requireEdt(); Objects.requireNonNull(choice);
        if (style == ThemeStyle.MODERN) apply(state.chooseTerminal(choice));
    }
    public void configure(Appearance saved) { configure(saved, uiFont); }
    public void configure(Appearance saved, UiFontConfig font) { configure(saved, state.terminalSaved(), font); }
    public void configure(Appearance saved, TerminalColors terminal, UiFontConfig font) {
        requireEdt();
        apply(state.configure(Objects.requireNonNull(saved)).configureTerminal(Objects.requireNonNull(terminal)),
            Objects.requireNonNull(font));
    }
```

- in `apply(ThemeState candidate, UiFontConfig font)`, change

```java
        boolean choiceChanged = style == ThemeStyle.MODERN && state.choice() != candidate.choice();
```

to

```java
        boolean choiceChanged = style == ThemeStyle.MODERN && (state.choice() != candidate.choice()
            || state.terminalChoice() != candidate.terminalChoice());
```

  Nothing else in `apply` changes. A palette-only change leaves `chromeChanged` false, skips the look-and-feel install, and still notifies, because `!previous.equals(next)`.

In `ConfigurationController.java`, change both calls `themes.configure(<snapshot>.variant(), <snapshot>.uiFont())` (lines 56 and 100) to `themes.configure(<snapshot>.variant(), <snapshot>.terminalColors(), <snapshot>.uiFont())`, keeping each line's own snapshot expression (`state.snapshot()` and `next.snapshot()`).

- [ ] **Step 4: Run the tests to verify they pass**

Run: `./gradlew :jasper-app:test --tests 'dev.jasper.app.appearance.*' --tests 'dev.jasper.app.workspace.ConfigurationControllerTest' --tests 'dev.jasper.app.workspace.ThemeControllerTest'`
Expected: pass.

- [ ] **Step 5: Commit**

```bash
git add jasper-app/src
git commit -m "feat(appearance): resolve terminal colours independently of the UI chrome

🤖 Generated with Claude Code at The Home Depot

Co-Authored-By: Claude <noreply@anthropic.com>"
```

---

### Task 3: View ▸ Appearance terminal choices and window wiring

**Files:**
- Modify: `jasper-app/src/main/java/dev/jasper/app/workspace/WindowContent.java` (next to `appearance()` and `selectAppearance`, around lines 559-570)
- Modify: `jasper-app/src/main/java/dev/jasper/app/workspace/WindowCommands.java` (after the `view.appearance.*` loop around line 64, and in `refresh()` after the appearance loop around line 105)
- Modify: `jasper-app/src/main/java/dev/jasper/app/workspace/WindowChrome.java` (Appearance menu construction around lines 73-89, `refreshTheme()`)
- Test: create `jasper-app/src/test/java/dev/jasper/app/workspace/TerminalColorsTest.java`

**Interfaces:**
- Consumes: `ThemeController.terminalColors()` and `selectTerminalColors(TerminalColors)` (Task 2).
- Produces:
  - `TerminalColors WindowContent.terminalColors()` and `void WindowContent.selectTerminalColors(TerminalColors)` (package-private);
  - view commands `view.terminal_colors.match`, `view.terminal_colors.light` and `view.terminal_colors.dark`.

- [ ] **Step 1: Write the failing tests**

Create `TerminalColorsTest.java`:

```java
package dev.jasper.app.workspace;

import dev.jasper.app.appearance.ThemeController;
import dev.jasper.app.config.Appearance;
import dev.jasper.app.config.TerminalColors;
import dev.jasper.app.config.ThemeStyle;
import dev.jasper.terminal.config.Palette;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import javax.swing.*;
import javax.swing.event.MenuEvent;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.OS;
import static dev.jasper.app.workspace.DesktopTestSupport.*;
import static org.assertj.core.api.Assertions.assertThat;

class TerminalColorsTest {
    @AfterEach void cleanup() throws Exception { closeOwners(); edt(() -> new ThemeController()); }

    @Test @DisabledOnOs(OS.WINDOWS)
    void aLightUiRunsADarkTerminalInEveryPaneAndWindowAndSwitchesBackInPlace() throws Exception {
        var pending = new ArrayDeque<Runnable>();
        WindowContent[] owners = new WindowContent[2];
        edt(() -> {
            var themes = new ThemeController(ThemeStyle.MODERN, Appearance.LIGHT);
            owners[0] = content(launcher(pending), themes);
            owners[1] = content(launcher(pending), themes);
        });
        while (!pending.isEmpty()) pending.remove().run();
        until(() -> owners[0].currentPane().view() != null && owners[1].currentPane().view() != null);
        edt(() -> {
            var owner = owners[0];
            var hidden = owner.currentPane();
            owner.newTab(HOME);
            var pane = owner.currentPane();
            owner.selectTerminalColors(TerminalColors.DARK);
            assertThat(com.formdev.flatlaf.FlatLaf.isLafDark()).as("the UI stays light").isFalse();
            assertThat(pane.getBackground()).isEqualTo(Palette.jasperDark().background());
            assertThat(hidden.getBackground()).as("a background tab").isEqualTo(Palette.jasperDark().background());
            assertThat(hidden.view().palette()).isEqualTo(Palette.jasperDark());
            assertThat(owners[1].currentPane().view().palette()).as("another window").isEqualTo(Palette.jasperDark());
            assertThat(owner.toolbar().getBackground()).isEqualTo(UIManager.getColor("Jasper.titleBackground"));
            var find = hidden.findBar();
            assertThat(find.queryField().getForeground()).as("the find bar keeps UI colours")
                .isEqualTo(UIManager.getColor("TextField.foreground"));
            assertThat(find.getBackground()).isNotEqualTo(Palette.jasperDark().background());
            owner.newTab(HOME);
            assertThat(owner.currentPane().getBackground()).as("a tab opened after the switch")
                .isEqualTo(Palette.jasperDark().background());
            var session = hidden.session();
            owner.selectTerminalColors(TerminalColors.MATCH);
            assertThat(hidden.session()).isSameAs(session);
            assertThat(hidden.view().palette()).isEqualTo(Palette.jasperLight());
            assertThat(hidden.getBackground()).isEqualTo(Palette.jasperLight().background());
        });
    }

    @Test void appearanceMenuOffersTerminalChoicesThatApplyLiveAndReflectTheCurrentChoice() throws Exception {
        edt(() -> {
            var owner = content(launcher(new ArrayDeque<>()), new ThemeController(ThemeStyle.MODERN, Appearance.DARK));
            owner.updateActions();
            JMenu menu = appearance(owner);
            List<JRadioButtonMenuItem> items = terminalItems(menu);
            assertThat(items).extracting(JMenuItem::getText)
                .containsExactly("Terminal: Match UI", "Terminal: Light", "Terminal: Dark");
            open(menu);
            assertThat(items.get(0).isSelected()).isTrue();
            items.get(1).doClick();
            assertThat(owner.terminalColors()).isEqualTo(TerminalColors.LIGHT);
            assertThat(owner.theme().palette()).isEqualTo(Palette.jasperLight());
            owner.selectTerminalColors(TerminalColors.DARK);
            open(menu);
            assertThat(items.get(2).isSelected()).isTrue();
            assertThat(items).allMatch(JMenuItem::isEnabled);
        });
    }

    @Test void retroShowsTheTerminalChoicesDisabled() throws Exception {
        edt(() -> {
            var owner = content(launcher(new ArrayDeque<>()), new ThemeController(ThemeStyle.RETRO, Appearance.LIGHT));
            owner.updateActions();
            assertThat(terminalItems(appearance(owner))).hasSize(3).noneMatch(JMenuItem::isEnabled);
            assertThat(owner.terminalColors()).isEqualTo(TerminalColors.MATCH);
        });
    }

    private static void open(JMenu menu) {
        for (var listener : menu.getMenuListeners()) listener.menuSelected(new MenuEvent(menu));
    }

    private static JMenu appearance(WindowContent owner) {
        for (var component : owner.menuBar().getMenu(2).getMenuComponents())
            if (component instanceof JMenu menu && menu.getText().equals("Appearance")) return menu;
        throw new AssertionError("Appearance menu is absent");
    }

    private static List<JRadioButtonMenuItem> terminalItems(JMenu menu) {
        var items = new ArrayList<JRadioButtonMenuItem>();
        for (var component : menu.getMenuComponents())
            if (component instanceof JRadioButtonMenuItem item && item.getText().startsWith("Terminal:")) items.add(item);
        return items;
    }
}
```

- [ ] **Step 2: Run the tests to verify they fail**

Run: `./gradlew :jasper-app:test --tests dev.jasper.app.workspace.TerminalColorsTest`
Expected: compilation fails (`selectTerminalColors` and `terminalColors` are missing on `WindowContent`).

- [ ] **Step 3: Implement**

In `WindowContent.java`, add the import `dev.jasper.app.config.TerminalColors`, then add after `selectAppearance(...)`:

```java
    TerminalColors terminalColors() { return themes.terminalColors(); }

    /** A session-only terminal palette choice shared by every window; the chrome is unchanged. */
    void selectTerminalColors(TerminalColors colors) {
        if (closed) return;
        themes.selectTerminalColors(colors);
        chrome.refreshTheme();
    }
```

In `WindowCommands.java`, add the import `dev.jasper.app.config.TerminalColors`. Directly after the `for (var appearance : Appearance.values()) { … add(… "view.appearance." …) }` loop, add:

```java
        for (var colors : TerminalColors.values()) {
            String label = switch (colors) { case MATCH -> "Match UI"; case LIGHT -> "Light"; case DARK -> "Dark"; };
            add(registry, "view.terminal_colors." + colors.name().toLowerCase(java.util.Locale.ROOT), "Terminal: " + label,
                "Terminal Colors: " + label, () -> owner.selectTerminalColors(colors), List.of("terminal", "colors", "palette"));
        }
```

In `refresh()`, directly after the appearance loop (before `view("view.tab_height")…`), add:

```java
        for (var colors : TerminalColors.values()) {
            var action = view("view.terminal_colors." + colors.name().toLowerCase(java.util.Locale.ROOT));
            action.putValue(Action.SELECTED_KEY, owner.terminalColors() == colors);
            action.setEnabled(!owner.retro());
        }
```

In `WindowChrome.java`:
- add the import `dev.jasper.app.config.TerminalColors`;
- add the field `private final java.util.EnumMap<TerminalColors, JRadioButtonMenuItem> terminalItems = new java.util.EnumMap<>(TerminalColors.class);`;
- directly after the `for (Appearance theme : new Appearance[]{Appearance.LIGHT, Appearance.DARK}) { … }` loop, and before `appearance.addMenuListener(`, add:

```java
        appearance.addSeparator();
        ButtonGroup terminalGroup = new ButtonGroup();
        for (TerminalColors colors : TerminalColors.values()) {
            JRadioButtonMenuItem item = new JRadioButtonMenuItem(owner.windowCommands().view(
                "view.terminal_colors." + colors.name().toLowerCase(java.util.Locale.ROOT)));
            terminalItems.put(colors, item); terminalGroup.add(item); appearance.add(item);
        }
```

- in `refreshTheme()`, after `themeItems.forEach((theme, item) -> item.setSelected(owner.appearance() == theme));`, add:

```java
        terminalItems.forEach((colors, item) -> item.setSelected(owner.terminalColors() == colors));
```

The retro note ("Retro uses light Metal; change style in Settings and restart.") stays after these items, because the existing `if (owner.retro())` block runs after the menu listener is added.

- [ ] **Step 4: Run the tests to verify they pass**

Run: `./gradlew :jasper-app:test`

Expected: all pass. If a test that counts palette commands or View menu items (for example in `WindowCommandsTest`, `CommandPaletteShortcutsTest` or `WindowChromeTest`) now fails only because of the three new commands or items, update that expectation minimally and record the change.

- [ ] **Step 5: Commit**

```bash
git add jasper-app/src
git commit -m "feat(workspace): add View terminal colour choices that apply live

🤖 Generated with Claude Code at The Home Depot

Co-Authored-By: Claude <noreply@anthropic.com>"
```

---

### Task 4: Documentation and the full gate

**Files:**
- Modify: `docs/configuration.md` (the example block around lines 84-87, the key table around line 125, the "Theme variant" section around lines 531-547 and the retro paragraph around line 565)
- Modify: `docs/STATUS.md`, the spec's status line and this plan's **Status** line

- [ ] **Step 1: Update `docs/configuration.md`**

- In the example TOML block, replace

```
# Modern only: "dark" or "light"; chrome and terminal colors switch together.
variant = "dark"
```

with

```
# Modern only: "dark" or "light" for the UI chrome.
variant = "dark"
# Modern only: "match" (follow variant), "light" or "dark" terminal colors.
terminal = "match"
```

- In the key table, after the `ui.theme.variant` row, add:

```
| `ui.theme.terminal` | `"match"` | `"match"`, `"light"`, `"dark"` | Live in modern mode; retained but ignored in retro |
```

- In the "Theme variant" section:
  - change the first sentence's "`"dark"` (the default) pairs FlatLaf Dark chrome with the Jasper Dark terminal palette; `"light"` pairs FlatLaf Light chrome with the Jasper Light palette." to "`"dark"` (the default) selects FlatLaf Dark chrome and `"light"` FlatLaf Light chrome; by default the terminal uses the matching Jasper Dark or Jasper Light palette (see [Terminal colors](#terminal-colors)).";
  - change "Terminal padding and status match the palette background" to "Terminal padding and the area behind panes match the terminal palette";
  - after that section's last paragraph (the `[colors]` paragraph), insert:

```markdown
## Terminal colors

`ui.theme.terminal` chooses the terminal palette independently of the UI chrome, for example a
light UI with a dark terminal. `"match"` (the default) follows `ui.theme.variant`; `"light"` and
`"dark"` always use the Jasper Light or Jasper Dark palette. Terminal views, their padding and the
area behind the panes follow this choice; the title row, toolbar, tab strip, find bar, status
bar, split dividers, menus and dialogs keep the UI chrome. Changes apply live to every window
without reinstalling the look and feel, keeping shells, scrollback and splits.

View → Appearance offers Terminal: Match UI, Light and Dark as a temporary choice for the
session that never rewrites the configuration; it clears when the saved `terminal` value
changes. An unrecognized value produces an error diagnostic and keeps `"match"`; a non-string
value rejects the configuration. Retro keeps its fixed high-contrast terminal and ignores this
setting.
```

- In the retro section's last paragraph, change "The saved `variant` remains available for modern mode." to "The saved `variant` and `terminal` choices remain available for modern mode."

- [ ] **Step 2: Run the full gate**

Run: `./gradlew check`

Expected: `BUILD SUCCESSFUL`. Count the results:

```bash
python3 - <<'PY'
import glob, xml.etree.ElementTree as ET
t = f = e = s = 0
for path in glob.glob("*/build/test-results/test/*.xml") + glob.glob("plugins/*/build/test-results/test/*.xml"):
    root = ET.parse(path).getroot()
    t += int(root.get("tests")); f += int(root.get("failures")); e += int(root.get("errors")); s += int(root.get("skipped"))
print(f"tests={t} failures={f} errors={e} skipped={s}")
PY
```

Expected: `failures=0 errors=0`. Run the AGENTS.md source-hygiene check; it must print nothing.

- [ ] **Step 3: Record the status**

In `docs/STATUS.md`, add under the IntelliJ chrome entry:

```markdown
- **Terminal colours independent of the UI** (same branch, plan
  `docs/superpowers/plans/2026-09-25-jasper-terminal-colors.md`): `ui.theme.terminal =
  "match" | "light" | "dark"` and View → Appearance Terminal choices; live, retro ignores it, SDK
  unchanged. Visual acceptance of the six light/dark combinations and retro is user-run.
```

Set the spec's status line to "Implemented on `claude/intellij-chrome`; visual acceptance pending." Set this plan's **Status** line to "All tasks complete; `./gradlew check` passed (N tests, 0 failures, 0 errors, S skipped)", using the counts from Step 2.

- [ ] **Step 4: Commit**

```bash
git add docs
git commit -m "docs(appearance): document independent terminal colours

🤖 Generated with Claude Code at The Home Depot

Co-Authored-By: Claude <noreply@anthropic.com>"
```
