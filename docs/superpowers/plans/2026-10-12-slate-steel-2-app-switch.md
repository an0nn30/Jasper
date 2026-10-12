# Slate Steel Plan 2: app switch — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Run the app on Slate Steel only: `ui.appearance` and `terminal.scheme` replace the four theme settings, the terminal scheme colours only the terminal text area (bold text included), and the Appearance page offers Dark / Light / Match System, the one "Slate Steel" theme and the terminal scheme.

**Architecture:** Parallel change, so every task leaves `./gradlew check` green. Tasks 1–3 add the terminal bold colour, the two settings and a Slate Steel path through `ThemeController` beside the legacy one. Task 4 moves the Appearance page onto the new settings. Task 5 switches the runtime (start-up, configuration, OS appearance, View commands, terminal surroundings) and drops the catalog scan, theme upgrade and import wiring. Task 6 removes the four old settings and their plumbing. The legacy theme classes stay only as test seams for Plan 3's test migration; no production code uses them after Task 5.

**Tech Stack:** Java 25 on JBR 25 (`./gradlew` only), Swing + FlatLaf, tomlj, JUnit 5 + AssertJ.

**Spec:** [`docs/superpowers/specs/2026-10-12-jasper-slate-steel-themes-design.md`](../specs/2026-10-12-jasper-slate-steel-themes-design.md) sections 1, 3.4 (bold), 5, 6 and phasing 13.2. Plan 1 ([engine and kit](2026-10-12-slate-steel-1-engine-and-kit.md)) built `SlateSteel`, `TerminalScheme`, `SchemeColors` and the generated themes.

## Global Constraints

- Work in `/Users/RQ7RQVF/projects/moray/.claude/worktrees/modal-overlay` on branch `claude/slate-steel`. Never build in `/Users/RQ7RQVF/projects/moray` (the user runs `:jasper-app:run` there). Never launch the GUI. Never push.
- Settings, exactly (spec 5.1): `ui.appearance` = `"system"` | `"dark"` | `"light"`, default `"system"`; `terminal.scheme` = `"match"` | `"slate-steel-dark"` | `"slate-steel-light"` | `"high-contrast"`, default `"match"`. Removed outright with their aliases: `ui.theme.name` (`ui.theme.variant`), `ui.theme.source`, `ui.theme.follow_system`, `ui.theme.workspace` (`terminal.colors`, `ui.theme.terminal`). No migration, aliases or shims (Jasper is an MVP).
- `match` selects Slate Steel Dark with the Dark UI and Slate Steel Light with the Light UI. With `system` and no OS appearance, the variant is Dark and a diagnostic is logged. OS changes never write configuration or mark Settings dirty.
- A terminal scheme recolours the terminal text area only: background, text, bold text, cursor, selection and ANSI. Tabs, the tab strip, the area around panes, the editor, panels and dialogs stay on the UI variant (`surface.terminal`, `surface.tabstrip`).
- Appearance page copy: tiles **Dark**, **Light**, **Match System**; row **Theme** with the one entry **Slate Steel** and no gear (no Import theme…); row **Terminal scheme**, note **Colors for terminal panes**, choices **Same as UI theme**, **Slate Steel Dark**, **Slate Steel Light**, **High Contrast**.
- `dev.jasper.app.config` stays free of jasper-ui types (its package-info allows commands, persistence, settings and terminal.config only); `dev.jasper.app.settingsui` uses jasper-ui only through `dev.jasper.ui.model`/`dev.jasper.ui.swing`. The application package reads Slate Steel and passes plain values down.
- The legacy theme classes (`Theme`, `ThemeManager`, `ThemeCatalog`, `BuiltInThemes`, `WorkspaceThemeChoice`, `config.TerminalColors`, the app's `themeimport`/`themesui`, jasper-ui's legacy layer) and `ThemeController`'s `Theme`-based constructors and `configure*` overloads stay as test seams for Plan 3; after Task 5 no production code calls them.
- jasper-terminal: no public signature takes or returns a JediTerm type; new public members carry JavaDoc (doclint fails the build).
- Commit messages end with:
  ```
  🤖 Generated with Claude Code at The Home Depot

  Co-Authored-By: Claude <noreply@anthropic.com>
  ```
- Test commands: `./gradlew :jasper-terminal:test --tests '<pattern>'`, `./gradlew :jasper-app:test --tests '<pattern>'`; a task ends with its whole-module suites green (`:jasper-app:test` takes about 4 minutes).

## Decisions recorded by this plan (for the user's review)

1. An unknown `ui.appearance` or `terminal.scheme` value is reported like every other enumerated setting's: "Use …; using the default.", which titles the existing notice "config.toml has errors". The spec says "warnings"; the plan keeps one convention for all enumerated settings.
2. A removed `ui.theme.*` key left in a file is reported once for its table (`ui.theme`: "Unknown setting or table; ignored."), as any unknown table is; `terminal.colors` is reported as its own key.
3. The Theme dropdown is enabled with its one entry, so it opens and shows "Slate Steel" and nothing else can be chosen; it is not greyed out, matching the mock.
4. The Match System tile says "System appearance: Dark/Light", or "System appearance unavailable; using Dark".
5. The command palette's "Terminal: Match UI / Light / Dark" become "Terminal: Same as UI theme / Slate Steel Dark / Slate Steel Light / High Contrast" (`view.terminal_scheme.<name>`); "Themes…" stays and opens Appearance.
6. Bold text in the default foreground takes the bold colour; an explicit SGR colour stays; the bold colour is chosen before inverse, so inverse bold text has it as its background (as xterm's bold colour does).
7. `new ThemeController()` and the other legacy seams keep Jasper Dark for existing tests until Plan 3 migrates them; production starts with `ThemeController.startup(dark, scheme, …)` and configures only Slate Steel.
8. The app's `themeimport` and `themesui` packages lose their wiring here (no Import theme…, no start-up theme upgrade) and are deleted with the rest in Plan 3.

## Review Focus

1. The OS appearance flips while Settings has unsaved edits: the window re-themes, the draft and config.toml are untouched — pinned in Task 5 (`SystemThemeApplicationTest`).
2. A config.toml from an older install (`[ui.theme] name = "Nord"`, `terminal.colors = "light"`) starts in Slate Steel and reports the removed keys, not a crash — pinned in Task 6 (`ConfigLoaderTest`).
3. `terminal.scheme = "high-contrast"` with the Light UI: only the text area turns black; the window behind panes, tabs and strip stay Light `surface.terminal` — pinned in Task 5 (`TerminalSchemeSurfaceTest`).
4. Bold text whose colour is explicit (`ESC[1;31m`) keeps it; default-colour bold takes the bold colour; inverse bold — pinned in Task 1 (`CellStyleTest`).
5. Choosing Light in Settings, then Reset, removes `ui.appearance`; Cancel discards — pinned in Task 4 (`AppearanceSelectionTest`, `SettingsDialogTest`).

---

### Task 1: A bold foreground in terminal panes

**Files:**
- Modify: `jasper-terminal/src/main/java/dev/jasper/terminal/config/Palette.java` (component, docs, validation, factories)
- Modify: `jasper-terminal/src/main/java/dev/jasper/terminal/internal/rendering/CellStyle.java:18`
- Modify: `jasper-app/src/main/java/dev/jasper/app/appearance/TerminalPaletteAdapter.java:10`
- Modify: `jasper-terminal/src/test/java/dev/jasper/terminal/config/PaletteTest.java:52`, `jasper-terminal/src/test/java/dev/jasper/terminal/view/TerminalPaletteTest.java:168-178`, `jasper-app/src/test/java/dev/jasper/app/appearance/ThemeManagerTest.java:114`
- Modify: `docs/terminal-architecture.md` (the palette paragraph near line 290)
- Test: `jasper-terminal/src/test/java/dev/jasper/terminal/internal/rendering/CellStyleTest.java`, `.../internal/rendering/TerminalPainterTest.java`, `.../config/PaletteTest.java`

**Interfaces:**
- Produces: `record Palette(Color foreground, Color background, Color cursor, Color selection, List<Color> ansi, Color boldForeground)`; `Palette.jasperDark()`/`jasperLight()` use their foreground as bold; `TerminalPaletteAdapter.toTerminal(TerminalColors)` passes the foreground as bold.

- [ ] **Step 1: Write the failing tests**

Add to `CellStyleTest` (it imports `TextStyle`, `TerminalColor`, `Option`, `EnumSet`, `Color`):

```java
    @Test
    void boldTextInTheDefaultColorTakesTheBoldForeground() {
        Palette bold = new Palette(palette.foreground(), palette.background(), palette.cursor(), palette.selection(),
            palette.ansi(), new Color(0x12ab34));
        CellStyle plain = CellStyle.resolve(EmulationFixture.attributes(TextStyle.EMPTY), bold);
        CellStyle strong = CellStyle.resolve(EmulationFixture.attributes(new TextStyle(null, null, EnumSet.of(Option.BOLD))), bold);
        CellStyle red = CellStyle.resolve(EmulationFixture.attributes(new TextStyle(TerminalColor.index(1), null, EnumSet.of(Option.BOLD))), bold);
        CellStyle inverse = CellStyle.resolve(EmulationFixture.attributes(new TextStyle(null, null, EnumSet.of(Option.BOLD, Option.INVERSE))), bold);
        assertThat(plain.foreground()).isEqualTo(palette.foreground());
        assertThat(strong.foreground()).isEqualTo(new Color(0x12ab34));
        assertThat(red.foreground()).as("an explicit color stays").isEqualTo(palette.ansi().get(1));
        assertThat(inverse.background()).as("inverse bold text has the bold color behind it").isEqualTo(new Color(0x12ab34));
    }
```

Add to `TerminalPainterTest`:

```java
    @Test
    void boldTextInTheDefaultColorIsDrawnInTheBoldForeground() throws Exception {
        Palette bold = new Palette(palette.foreground(), palette.background(), palette.cursor(), palette.selection(),
            palette.ansi(), new Color(0x12ab34));
        TerminalPainter boldPainter = new TerminalPainter(fonts, bold);
        ScreenSnapshot snapshot = snapshotAfter("\033[1m█\033[0m", 1);
        BufferedImage image = new BufferedImage(COLUMNS * cw, ROWS * ch, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();
        try {
            boldPainter.paint(g, snapshot, cursorOff(), List.of(), image.getWidth(), image.getHeight());
        } finally {
            g.dispose();
        }
        assertThat(rgb(image, cw / 2, ch / 2)).isEqualTo(0x12ab34);
    }
```

Add to `PaletteTest`:

```java
    @Test
    void rejectsAMissingBoldForeground() {
        assertThatThrownBy(() -> new Palette(Color.WHITE, Color.BLACK, Color.WHITE, Color.GRAY, Collections.nCopies(16, Color.RED), null))
            .isInstanceOf(NullPointerException.class).hasMessageContaining("boldForeground");
    }
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `./gradlew :jasper-terminal:test --tests '*CellStyleTest' --tests '*TerminalPainterTest' --tests '*PaletteTest'`
Expected: FAIL to compile (no six-argument `Palette` constructor).

- [ ] **Step 3: Implement**

In `Palette.java`, add `@param boldForeground the default color of bold text; an explicit SGR color stays as it is` after the `ansi` `@param`, change the header to

```java
public record Palette(Color foreground, Color background, Color cursor, Color selection, List<Color> ansi, Color boldForeground) {
```

add `Objects.requireNonNull(boldForeground, "boldForeground");` after the `selection` check, and in `jasperDark()` and `jasperLight()` add the foreground as the last argument (`, new Color(0xf8f8f2)` after the dark list and `, new Color(0x383a42)` after the light list).

In `CellStyle.resolve`, replace the first line with:

```java
        // Bold text in the default color takes the palette's bold color (xterm's colorBD), before inverse and dim apply.
        Color fallback = style.has(CellAttributes.BOLD) ? palette.boldForeground() : palette.foreground();
        Color fg = resolve(style.foreground(), fallback, palette);
```

In `TerminalPaletteAdapter.toTerminal(TerminalColors)`: `return new Palette(colors.foreground(), colors.background(), colors.cursor(), colors.selection(), colors.ansi(), colors.foreground());`.

Add the foreground as a sixth argument to the five-argument `new Palette(...)` calls in `PaletteTest.java:52` (keep `Collections.nCopies(8, Color.RED)`; add `, Color.WHITE`), each call in `TerminalPaletteTest.java:168-178` (add `, Color.WHITE`) and `ThemeManagerTest.java:114` (add `, new Color(0xd5dee5)`).

In `docs/terminal-architecture.md`, where the palette is described as a live option, add the sentence: "The palette's bold foreground colours bold text drawn in the default foreground; an explicit SGR colour stays."

- [ ] **Step 4: Run tests**

Run: `./gradlew :jasper-terminal:test :jasper-terminal:javadoc :jasper-app:test --tests '*ThemeManagerTest' --tests '*TerminalColorsThemeTest'`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add -A jasper-terminal jasper-app/src docs/terminal-architecture.md
git commit -m "A bold foreground in terminal panes

The terminal palette gains a bold foreground: bold text in the default
foreground takes it, an explicit SGR colour stays, and it is chosen before
inverse. The standalone palettes and the legacy themes use their foreground.

🤖 Generated with Claude Code at The Home Depot

Co-Authored-By: Claude <noreply@anthropic.com>"
```

---

### Task 2: The `ui.appearance` and `terminal.scheme` settings

**Files:**
- Create: `jasper-app/src/main/java/dev/jasper/app/config/AppearanceMode.java`, `jasper-app/src/main/java/dev/jasper/app/config/TerminalSchemeChoice.java`
- Modify: `jasper-app/src/main/java/dev/jasper/app/config/CoreSettings.java` (two settings, `SCHEMA`, a `[ui]` table note)
- Modify: `jasper-app/src/main/java/dev/jasper/app/config/ConfigSnapshot.java` (two components, `from`, `Builder`)
- Modify: `jasper-app/src/main/java/dev/jasper/app/settingsui/CorePages.java:79-82` (hidden rows until Task 4)
- Modify: `config.example.toml`, `docs/configuration.md` (key table)
- Test: `jasper-app/src/test/java/dev/jasper/app/config/ConfigLoaderTest.java`

**Interfaces:**
- Produces: `enum AppearanceMode { SYSTEM, DARK, LIGHT }`; `enum TerminalSchemeChoice { MATCH, SLATE_STEEL_DARK, SLATE_STEEL_LIGHT, HIGH_CONTRAST; String key() }` with keys `match`, `slate-steel-dark`, `slate-steel-light`, `high-contrast`; `CoreSettings.UI_APPEARANCE`, `CoreSettings.TERMINAL_SCHEME`; `ConfigSnapshot.appearance()`, `ConfigSnapshot.terminalScheme()`, `Builder.appearance(AppearanceMode)`, `Builder.terminalScheme(TerminalSchemeChoice)`.

- [ ] **Step 1: Write the failing tests** — add to `ConfigLoaderTest` (its `parse(String)` helper and `assertDiagnostic(result, key, line, column, severity)` exist):

```java
    @Test void appearanceAndTerminalSchemeReadTheirChoicesAndDefault() {
        var chosen = parse("[ui]\nappearance = \"light\"\n[terminal]\nscheme = \"high-contrast\"\n");
        assertThat(chosen.diagnostics()).isEmpty();
        assertThat(chosen.snapshot().appearance()).isEqualTo(AppearanceMode.LIGHT);
        assertThat(chosen.snapshot().terminalScheme()).isEqualTo(TerminalSchemeChoice.HIGH_CONTRAST);
        var defaults = parse("");
        assertThat(defaults.snapshot().appearance()).isEqualTo(AppearanceMode.SYSTEM);
        assertThat(defaults.snapshot().terminalScheme()).isEqualTo(TerminalSchemeChoice.MATCH);
        for (var scheme : TerminalSchemeChoice.values())
            assertThat(parse("[terminal]\nscheme = \"" + scheme.key() + "\"\n").snapshot().terminalScheme()).isEqualTo(scheme);
    }

    @Test void anUnknownAppearanceOrSchemeUsesTheDefaultAndSaysSo() {
        var result = parse("[ui]\nappearance = \"sepia\"\n[terminal]\nscheme = \"nord\"\n");
        assertThat(result.snapshot().appearance()).isEqualTo(AppearanceMode.SYSTEM);
        assertThat(result.snapshot().terminalScheme()).isEqualTo(TerminalSchemeChoice.MATCH);
        assertDiagnostic(result, "ui.appearance", 2, 1, ConfigDiagnostic.Severity.ERROR);
        assertDiagnostic(result, "terminal.scheme", 4, 1, ConfigDiagnostic.Severity.ERROR);
    }
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `./gradlew :jasper-app:test --tests '*ConfigLoaderTest'`
Expected: FAIL to compile (`AppearanceMode` does not exist).

- [ ] **Step 3: The value types**

`AppearanceMode.java`:

```java
package dev.jasper.app.config;

/** {@code ui.appearance}: the UI variant, or with {@code SYSTEM} the operating system's. */
public enum AppearanceMode { SYSTEM, DARK, LIGHT }
```

`TerminalSchemeChoice.java`:

```java
package dev.jasper.app.config;

/** {@code terminal.scheme}: the terminal panes' colour scheme; {@code MATCH} follows the UI variant. */
public enum TerminalSchemeChoice {
    MATCH("match"), SLATE_STEEL_DARK("slate-steel-dark"), SLATE_STEEL_LIGHT("slate-steel-light"), HIGH_CONTRAST("high-contrast");

    private final String key;
    TerminalSchemeChoice(String key) { this.key = key; }

    /** The value in config.toml, which is also the bundled theme file's scheme key. */
    public String key() { return key; }
}
```

- [ ] **Step 4: The settings** — in `CoreSettings`, after `UI_THEME_SOURCE` add:

```java
    public static final Setting<AppearanceMode> UI_APPEARANCE = Setting.of("ui.appearance", SettingKind.choice(List.of(
            Map.entry("system", AppearanceMode.SYSTEM), Map.entry("dark", AppearanceMode.DARK), Map.entry("light", AppearanceMode.LIGHT))),
            AppearanceMode.SYSTEM)
        .label("Appearance").keywords("appearance", "theme", "dark", "light", "system", "slate steel", "look")
        .description("The UI appearance: \"system\" (the default) follows the operating system's light or dark setting;\n"
            + "\"dark\" or \"light\" chooses Slate Steel Dark or Light. Switches live across windows.").build();
    public static final Setting<TerminalSchemeChoice> TERMINAL_SCHEME = Setting.of("terminal.scheme", SettingKind.choice(List.of(
            Map.entry("match", TerminalSchemeChoice.MATCH), Map.entry("slate-steel-dark", TerminalSchemeChoice.SLATE_STEEL_DARK),
            Map.entry("slate-steel-light", TerminalSchemeChoice.SLATE_STEEL_LIGHT), Map.entry("high-contrast", TerminalSchemeChoice.HIGH_CONTRAST))),
            TerminalSchemeChoice.MATCH)
        .label("Terminal scheme").keywords("terminal", "scheme", "colors", "colours", "palette", "high contrast", "slate steel")
        .description("Colors for terminal panes: \"match\" (the default) follows the UI appearance; \"slate-steel-dark\",\n"
            + "\"slate-steel-light\" or \"high-contrast\" chooses one. Only the terminal text area changes; updates live.").build();
```

In `SCHEMA`, insert `TERMINAL_SCHEME` right after `TERMINAL_SHELL_INTEGRATION`, and `UI_APPEARANCE` right before `UI_FONT_FAMILY`. In the table notes add `Map.entry(List.of("ui"), "The interface: its appearance, fonts, workspace glass and animations."),` before the `ui.font` entry.

- [ ] **Step 5: The snapshot** — in `ConfigSnapshot`, append `, AppearanceMode appearance, TerminalSchemeChoice terminalScheme` to the record header after `boolean followSystemTheme`; in the compact constructor add `Objects.requireNonNull(appearance, "appearance"); Objects.requireNonNull(terminalScheme, "terminalScheme");`; the compatibility constructor that ends `…, ThemeSource.CATALOG, false);` becomes `…, ThemeSource.CATALOG, false, AppearanceMode.SYSTEM, TerminalSchemeChoice.MATCH);`; `from(...)` ends `values.get(CoreSettings.UI_THEME_SOURCE), values.get(CoreSettings.UI_THEME_FOLLOW_SYSTEM), values.get(CoreSettings.UI_APPEARANCE), values.get(CoreSettings.TERMINAL_SCHEME));`. In the `Builder`, add the fields `private AppearanceMode appearance; private TerminalSchemeChoice terminalScheme;`, copy them in `Builder(ConfigSnapshot source)` (`appearance = source.appearance(); terminalScheme = source.terminalScheme();`), add the setters

```java
        public Builder appearance(AppearanceMode value) { appearance = value; return this; }
        public Builder terminalScheme(TerminalSchemeChoice value) { terminalScheme = value; return this; }
```

and append `, appearance, terminalScheme` to the `new ConfigSnapshot(...)` in `build()`. If `ConfigTestSupport.java:13` calls the canonical constructor, append `, d.appearance(), d.terminalScheme()` there too.

- [ ] **Step 6: Pages, example and guide**
  - `CorePages`, Appearance page: after `.row(UI_THEME_FOLLOW_SYSTEM).shownWhen(false)` add `.row(UI_APPEARANCE).shownWhen(false)` and `.row(TERMINAL_SCHEME).shownWhen(false)` (Task 4 shows them).
  - `config.example.toml`: before the `[ui.font]` table add

    ```toml
    [ui]
    # The UI appearance, live across windows: "system" follows the operating system's light or dark setting;
    # "dark" or "light" chooses Slate Steel Dark or Light.
    appearance = "system"

    ```

    and in the `[terminal]` table, after `shell_integration`, add

    ```toml
    # Colors for terminal panes, live: "match" follows the UI appearance; or "slate-steel-dark", "slate-steel-light", "high-contrast".
    scheme = "match"
    ```
  - `docs/configuration.md`, key table: after the `terminal.bell` row add ``| `terminal.scheme` | `"match"` | `"match"`, `"slate-steel-dark"`, `"slate-steel-light"`, `"high-contrast"`: the terminal panes' colors, linked to the Themes guide | Live |`` and before the `ui.theme.source` row add ``| `ui.appearance` | `"system"` | `"system"` (the operating system's light or dark), `"dark"`, `"light"` | Live |``.

- [ ] **Step 7: Run tests**

Run: `./gradlew :jasper-app:test --tests '*ConfigLoaderTest' --tests '*ConfigTemplateTest' --tests '*CorePagesTest' --tests '*CoreSettingsTest' --tests '*ConfigSnapshotBuilderTest'`
Expected: PASS. If `ConfigTemplateTest` reports the generated template's order, move `ui.appearance`/`terminal.scheme` in `config.example.toml` to where the generated template puts them.

- [ ] **Step 8: Commit**

```bash
git add -A jasper-app/src config.example.toml docs/configuration.md
git commit -m "The ui.appearance and terminal.scheme settings

ui.appearance (system, dark, light; default system) and terminal.scheme
(match, slate-steel-dark, slate-steel-light, high-contrast; default match)
join the schema and the snapshot. Nothing reads them yet.

🤖 Generated with Claude Code at The Home Depot

Co-Authored-By: Claude <noreply@anthropic.com>"
```

---

### Task 3: Slate Steel through `ThemeController`

**Files:**
- Modify: `jasper-app/src/main/java/dev/jasper/app/appearance/ResolvedTheme.java`, `TerminalPaletteAdapter.java`, `ThemeController.java`
- Test: `jasper-app/src/test/java/dev/jasper/app/appearance/SlateSteelControllerTest.java`

**Interfaces:**
- Consumes: `SlateSteel.bundled().generated(boolean)`, `terminal(TerminalScheme)`, `TerminalScheme.of(String)`/`matching(boolean)`; `TerminalSchemeChoice` (Task 2); `Palette` with bold (Task 1).
- Produces: `record ResolvedTheme(GeneratedTheme chrome, GeneratedTheme workspace, Palette palette)` with `ResolvedTheme(chrome, workspace)` (palette from the workspace theme), `static ResolvedTheme slateSteel(boolean dark, TerminalScheme scheme)`, `Color surface()`; `TerminalPaletteAdapter.toTerminal(SchemeColors)`; `ThemeController.startup(boolean dark, TerminalSchemeChoice scheme, Consumer<InstallationFailure>)`, `ThemeController(boolean dark, TerminalSchemeChoice scheme)`, `configure(boolean dark, TerminalSchemeChoice scheme, UiFontConfig font)`, `configure(boolean dark, TerminalSchemeChoice scheme)`, `configureScheme(TerminalSchemeChoice)`, `schemeChoice()`, `static TerminalScheme scheme(boolean dark, TerminalSchemeChoice choice)`; the installer becomes `Predicate<GeneratedTheme>`.

- [ ] **Step 1: Write the failing test**

```java
package dev.jasper.app.appearance;

import static org.assertj.core.api.Assertions.*;

import dev.jasper.app.config.TerminalSchemeChoice;
import dev.jasper.app.testsupport.EdtTestExtension;
import dev.jasper.ui.theme.GeneratedTheme;
import dev.jasper.ui.theme.SemanticColor;
import dev.jasper.ui.theme.SlateSteel;
import dev.jasper.ui.theme.TerminalScheme;
import java.util.ArrayList;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

/** spec 5.2–5.3: one Slate Steel variant serves chrome and workspace; the scheme colours only the terminal palette. */
@ExtendWith(EdtTestExtension.class)
class SlateSteelControllerTest {
    static final SlateSteel STEEL = SlateSteel.bundled();

    @Test void oneVariantServesChromeAndWorkspaceAndTheSchemeColoursTheTerminalOnly() {
        var controller = new ThemeController(false, TerminalSchemeChoice.HIGH_CONTRAST, theme -> true);
        var current = controller.current();
        assertThat(current.chrome()).isSameAs(STEEL.generated(false)).isSameAs(current.workspace());
        var scheme = STEEL.terminal(TerminalScheme.HIGH_CONTRAST);
        assertThat(current.palette()).isEqualTo(TerminalPaletteAdapter.toTerminal(scheme));
        assertThat(current.palette().boldForeground()).isEqualTo(scheme.boldForeground());
        assertThat(current.surface()).as("around panes and under tabs: the UI variant")
            .isEqualTo(STEEL.palette(false).color(SemanticColor.SURFACE_TERMINAL));
        assertThat(controller.schemeChoice()).isEqualTo(TerminalSchemeChoice.HIGH_CONTRAST);
    }

    @Test void matchFollowsTheVariantAndASchemeChangeKeepsTheChrome() {
        var installs = new ArrayList<GeneratedTheme>();
        var controller = new ThemeController(true, TerminalSchemeChoice.MATCH, installs::add);
        assertThat(controller.current().palette()).isEqualTo(TerminalPaletteAdapter.toTerminal(STEEL.terminal(TerminalScheme.SLATE_STEEL_DARK)));
        var chromeChanges = new ArrayList<Boolean>();
        controller.subscribe((theme, chrome) -> chromeChanges.add(chrome));
        chromeChanges.clear();
        controller.configure(true, TerminalSchemeChoice.HIGH_CONTRAST);
        assertThat(chromeChanges).containsExactly(false);
        assertThat(installs).hasSize(1);
        controller.configure(false, TerminalSchemeChoice.MATCH);
        assertThat(chromeChanges).containsExactly(false, true);
        assertThat(installs).hasSize(2).last().isSameAs(STEEL.generated(false));
        assertThat(controller.current().palette().background()).isEqualTo(STEEL.terminal(TerminalScheme.SLATE_STEEL_LIGHT).colors().background());
        controller.configureScheme(TerminalSchemeChoice.SLATE_STEEL_DARK);
        assertThat(controller.current().chrome()).as("the UI stays Light").isSameAs(STEEL.generated(false));
    }

    @Test void startUpFallsBackToDarkWhenLightCannotBeInstalled() {
        var failures = new ArrayList<ThemeController.InstallationFailure>();
        var controller = ThemeController.startup(false, TerminalSchemeChoice.MATCH, GeneratedTheme::dark, failures::add);
        assertThat(failures).hasSize(1);
        assertThat(controller.current().chrome()).isSameAs(STEEL.generated(true));
        assertThat(controller.current().palette().background()).isEqualTo(STEEL.terminal(TerminalScheme.SLATE_STEEL_DARK).colors().background());
    }

    @Test void everySchemeChoiceNamesABundledScheme() {
        for (var choice : TerminalSchemeChoice.values())
            for (boolean dark : new boolean[] {true, false})
                assertThat(ThemeController.scheme(dark, choice)).isNotNull();
        assertThat(ThemeController.scheme(false, TerminalSchemeChoice.MATCH)).isEqualTo(TerminalScheme.SLATE_STEEL_LIGHT);
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :jasper-app:test --tests '*SlateSteelControllerTest'`
Expected: FAIL to compile.

- [ ] **Step 3: `ResolvedTheme` and the adapter**

```java
package dev.jasper.app.appearance;

import dev.jasper.terminal.config.Palette;
import dev.jasper.ui.theme.GeneratedTheme;
import dev.jasper.ui.theme.SemanticColor;
import dev.jasper.ui.theme.SlateSteel;
import dev.jasper.ui.theme.TerminalScheme;
import java.awt.Color;
import java.util.Objects;

/** The immutable chrome and workspace values and the terminal palette, published together on the EDT. */
public record ResolvedTheme(GeneratedTheme chrome, GeneratedTheme workspace, Palette palette) {
    public ResolvedTheme { Objects.requireNonNull(chrome); Objects.requireNonNull(workspace); Objects.requireNonNull(palette); }

    /** A legacy fixture pair: the terminal palette is the workspace theme's own, bold text in its foreground. */
    public ResolvedTheme(GeneratedTheme chrome, GeneratedTheme workspace) {
        this(chrome, workspace, TerminalPaletteAdapter.toTerminal(workspace.terminal()));
    }

    /** Slate Steel: one variant for chrome and workspace, and the terminal scheme's palette. */
    public static ResolvedTheme slateSteel(boolean dark, TerminalScheme scheme) {
        var theme = SlateSteel.bundled().generated(dark);
        return new ResolvedTheme(theme, theme, TerminalPaletteAdapter.toTerminal(SlateSteel.bundled().terminal(scheme)));
    }

    public dev.jasper.ui.theme.ThemePair pair() { return new dev.jasper.ui.theme.ThemePair(chrome, workspace); }
    public boolean matching() { return chrome == workspace; }

    /** The workspace surface around terminal panes and under tabs; a terminal scheme colours only the text area. */
    public Color surface() { return workspace.semantic().color(SemanticColor.SURFACE_TERMINAL); }
}
```

Add to `TerminalPaletteAdapter`:

```java
    /** A Slate Steel terminal scheme, its bold foreground included. */
    public static Palette toTerminal(dev.jasper.ui.theme.SchemeColors scheme) {
        var colors = scheme.colors();
        return new Palette(colors.foreground(), colors.background(), colors.cursor(), colors.selection(), colors.ansi(), scheme.boldForeground());
    }
```

- [ ] **Step 4: `ThemeController`** — make these changes:
  - `InstallationFailure(Theme theme, RuntimeException cause)` becomes `InstallationFailure(GeneratedTheme theme, RuntimeException cause)` with `super("Could not apply theme: " + theme.name() + (theme.name().equals("Slate Steel") ? (theme.dark() ? " Dark" : " Light") : ""), cause);`.
  - `private final Predicate<Theme> installer;` becomes `Predicate<GeneratedTheme>`; every constructor's `Predicate<Theme>` parameter becomes `Predicate<GeneratedTheme>`; the default installer `ThemeManager::install` now resolves to `ThemeManager.install(GeneratedTheme)`.
  - Add the Slate Steel selection beside the legacy one: `private record Slate(boolean dark, TerminalSchemeChoice scheme) { }`, `private Slate slate;` (null while a legacy theme is shown) and make `state` null while Slate Steel is shown.
  - Replace the body of the legacy package constructor `ThemeController(Theme saved, Predicate<…> installer)` with `this(ThemeState.defaults().configure(Objects.requireNonNull(saved)), null, installer);`, and add:

```java
    /** Slate Steel in {@code dark} with {@code scheme}. */
    public ThemeController(boolean dark, TerminalSchemeChoice scheme) { this(dark, scheme, ThemeManager::install); }
    ThemeController(boolean dark, TerminalSchemeChoice scheme, Predicate<GeneratedTheme> installer) {
        this(null, new Slate(dark, Objects.requireNonNull(scheme)), installer);
    }
    private ThemeController(ThemeState legacy, Slate slate, Predicate<GeneratedTheme> installer) {
        requireEdt();
        this.installer = Objects.requireNonNull(installer);
        this.state = legacy;
        this.slate = slate;
        // Swing otherwise tries the component's plugin loader for third-party LAF delegates.
        // Only UI delegate lookup belongs to the app; plugin class visibility stays isolated.
        UIManager.put("ClassLoader", ThemeController.class.getClassLoader());
        FORM_FONTS.forEach(key -> UIManager.put(key, null));
        UIManager.put("defaultFont", null);
        UIManager.put("Jasper.uiFontFamilyOverride", false);
        current = resolve(legacy, slate);
        appearance = new AppearanceBindings(current.pair(), ThemeController::reportFailure, () -> animationsEnabled);
        new dev.jasper.ui.swing.AppearanceTransaction(appearance.binding(), ignored -> installOrThrow(current.chrome()), ignored -> {}).apply(current.pair());
        platformFont = UIManager.getFont("defaultFont") != null ? UIManager.getFont("defaultFont") : UIManager.getFont("Label.font");
        platformLabelSize = UIManager.getFont("Label.font").getSize2D();
    }

    /** The bundled scheme for a UI variant: the choice's, or with {@code MATCH} the variant's own. */
    public static TerminalScheme scheme(boolean dark, TerminalSchemeChoice choice) {
        return choice == TerminalSchemeChoice.MATCH ? TerminalScheme.matching(dark) : TerminalScheme.of(choice.key()).orElseThrow();
    }
    private static ResolvedTheme resolve(ThemeState legacy, Slate slate) {
        return slate != null ? ResolvedTheme.slateSteel(slate.dark(), scheme(slate.dark(), slate.scheme())) : legacy.resolve();
    }

    /**
     * Start-up selection: Slate Steel in {@code dark}, or Dark when that cannot be installed. A failed attempt is rolled
     * back completely, and the fallback is installed before this returns, so nothing can read the installed snapshot
     * (ThemeZone, named icons, native chrome) before one exists. The fallback's own failure propagates.
     */
    public static ThemeController startup(boolean dark, TerminalSchemeChoice scheme, java.util.function.Consumer<InstallationFailure> fallingBack) {
        return startup(dark, scheme, ThemeManager::install, fallingBack);
    }
    static ThemeController startup(boolean dark, TerminalSchemeChoice scheme, Predicate<GeneratedTheme> installer,
                                   java.util.function.Consumer<InstallationFailure> fallingBack) {
        try { return new ThemeController(dark, scheme, installer); }
        catch (InstallationFailure failure) {
            fallingBack.accept(failure);
            return new ThemeController(true, scheme, installer);
        }
    }

    /** Slate Steel in {@code dark} with {@code scheme} and {@code font}. */
    public void configure(boolean dark, TerminalSchemeChoice scheme, UiFontConfig font) {
        requireEdt();
        var next = new Slate(dark, Objects.requireNonNull(scheme));
        apply(null, next, resolve(null, next), Objects.requireNonNull(font));
    }
    public void configure(boolean dark, TerminalSchemeChoice scheme) { requireEdt(); configure(dark, scheme, uiFont); }
    /** Changes only the terminal scheme; the UI variant and font stay. */
    public void configureScheme(TerminalSchemeChoice scheme) {
        requireEdt(); configure(slate != null ? slate.dark() : current.chrome().dark(), scheme, uiFont);
    }
    /** The terminal scheme chosen; {@code MATCH} while a legacy fixture theme is shown. */
    public TerminalSchemeChoice schemeChoice() { requireEdt(); return slate == null ? TerminalSchemeChoice.MATCH : slate.scheme(); }
```

  - `choice()`, `workspaceChoice()` and `activeThemes()` read `state`: when `state == null` they return `null`, `WorkspaceThemeChoice.MATCH` and `List.of()`.
  - The legacy `configure(Theme saved, WorkspaceThemeChoice choice, Theme workspaceOrNull, UiFontConfig font)` body becomes `requireEdt(); var next = new ThemeState(saved, choice, workspaceOrNull); apply(next, null, next.resolve(), Objects.requireNonNull(font));`, and the other legacy overloads read `state` as before, starting from `ThemeState.defaults()` when it is null (`var base = state == null ? ThemeState.defaults() : state;`).
  - Replace `apply(ThemeState candidate, UiFontConfig font)` with:

```java
    private void apply(ThemeState nextLegacy, Slate nextSlate, ResolvedTheme next, UiFontConfig font) {
        ResolvedTheme previous = current;
        boolean chromeChanged = previous.chrome() != next.chrome() || !uiFont.equals(font);
        boolean selectionChanged = !Objects.equals(state, nextLegacy) || !Objects.equals(slate, nextSlate);
        if (applying) throw new IllegalStateException("Theme configuration cannot re-enter");
        applying = true;
        try {
            new dev.jasper.ui.swing.AppearanceTransaction(appearance.binding(), ignored -> {
                if (chromeChanged) {
                    installFontDefaults(font);
                    installOrThrow(next.chrome());
                }
                UIManager.put("Jasper.uiFontFamilyOverride", !font.family().equalsIgnoreCase("system"));
                uiFont = font;
                state = nextLegacy;
                slate = nextSlate;
                current = next;
            }, ignored -> {}).apply(next.pair());
            if (chromeChanged || !previous.equals(next) || selectionChanged) {
                for (var listener : List.copyOf(listeners)) {
                    if (listeners.contains(listener)) notifyListener(listener, next, chromeChanged);
                }
            }
        } finally { applying = false; }
    }
```

  - `installOrThrow(Theme theme)` becomes `installOrThrow(GeneratedTheme theme)` with `installer.test(theme)` and `new InstallationFailure(theme, failure)`.
  - Imports: `dev.jasper.app.config.TerminalSchemeChoice`, `dev.jasper.ui.theme.GeneratedTheme`, `dev.jasper.ui.theme.TerminalScheme`.
  - Every existing test that builds a `ThemeController` with an installer lambda keeps compiling when the lambda ignores its argument; one that reads it as a `Theme` (for example `ThemeControllerStartupTest`) compares `GeneratedTheme`s instead, such as `theme -> theme != ThemeManager.generated(Theme.X)`.

- [ ] **Step 5: Run tests**

Run: `./gradlew :jasper-app:test --tests '*SlateSteelControllerTest' --tests '*ThemeController*' --tests '*ThemeStateTest' --tests '*TerminalColorsThemeTest'`
Expected: PASS. Then `./gradlew :jasper-app:test` — PASS (the legacy behaviour is unchanged).

- [ ] **Step 6: Commit**

```bash
git add -A jasper-app/src
git commit -m "Slate Steel through ThemeController

ThemeController shows Slate Steel in one variant for chrome and workspace,
with the terminal scheme's palette (bold foreground included), beside the
legacy fixture themes the tests still use. ResolvedTheme carries the palette
and names the workspace surface around panes; start-up falls back to Dark.

🤖 Generated with Claude Code at The Home Depot

Co-Authored-By: Claude <noreply@anthropic.com>"
```

---

### Task 4: The Appearance page on the new settings

**Files:**
- Create: `jasper-app/src/main/java/dev/jasper/app/settingsui/AppearanceChoices.java`, `jasper-app/src/main/java/dev/jasper/app/settingsui/AppearanceSelection.java`, `jasper-app/src/main/java/dev/jasper/app/application/SettingsAppearance.java`
- Modify: `jasper-app/src/main/java/dev/jasper/app/settingsui/CorePages.java` (Appearance page, the Font & Colors link, `specs`/`pages` signatures), `Editors.java` (remove `theme`, `workspaceScheme`, `ThemePick`, `IMPORT_THEME`; add `appearance`), `WorkspaceEditors.java` (theme defaults from `AppearanceChoices`), `SettingsDraft.java` (remove the theme-selection coupling)
- Modify: `jasper-app/src/main/java/dev/jasper/app/application/JasperApplication.java:821-832`, `jasper-app/src/main/java/dev/jasper/app/application/package-info.java` (drop `dev.jasper.app.themesui`)
- Delete: `jasper-app/src/main/java/dev/jasper/app/settingsui/ThemeChoices.java`, `ThemeSelection.java`, `jasper-app/src/main/java/dev/jasper/app/application/SettingsThemeSources.java`, `jasper-app/src/test/java/dev/jasper/app/application/SettingsThemeSourcesTestAccess.java`, `jasper-app/src/test/java/dev/jasper/app/settingsui/ThemeSelectionTest.java`
- Test: `jasper-app/src/test/java/dev/jasper/app/settingsui/AppearanceSelectionTest.java`; update `AppearancePageTest`, `CorePagesTest`, `EditorsTest`, `SettingsDialogTest`, `NativeThemeNavigationTest`, `SettingsSurfacePreview`, `Settings9bPreview`, `SettingsDialogPreview`, `PluginSettingsPagesEvidenceTest`

**Interfaces:**
- Produces: `record AppearanceChoices(Option.Palette dark, Option.Palette light, float opacity, boolean blur, Supplier<Optional<Boolean>> systemDark, Function<Runnable, Subscription> onChanged)` with `static AppearanceChoices of(Option.Palette dark, Option.Palette light)`; `Editors.appearance(AppearanceChoices)`; `CorePages.specs(AppearanceChoices, …)`/`pages(AppearanceChoices, …)`; `SettingsAppearance.choices(SystemAppearance)`.

- [ ] **Step 1: Write the failing test**

```java
package dev.jasper.app.settingsui;

import static org.assertj.core.api.Assertions.*;

import dev.jasper.app.config.AppearanceMode;
import dev.jasper.app.config.CoreSettings;
import dev.jasper.ui.model.*;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/** spec 6: the tiles write ui.appearance; the Theme row's one entry is Slate Steel, with no gear. */
class AppearanceSelectionTest {
    static final Option.Palette DARK = new Option.Palette(1, 2, 3, 4, 5), LIGHT = new Option.Palette(6, 7, 8, 9, 10);

    static List<Nodes.Node> parts(AppearanceSelection editor) {
        return ((Nodes.Stack) editor.node("ui.appearance", ControlState.READY)).children();
    }

    @Test void theTilesPreviewBothVariantsAndTheThemeRowOffersOnlySlateSteel() {
        var editor = new AppearanceSelection(new AppearanceChoices(DARK, LIGHT, 100f, false, () -> Optional.of(false), ignored -> new dev.jasper.app.lifecycle.Subscription(() -> {})));
        editor.show(AppearanceMode.SYSTEM);
        var tiles = (Nodes.Choice) parts(editor).get(0);
        assertThat(tiles.id()).isEqualTo("ui.appearance:policy");
        assertThat(tiles.options()).extracting(Option::id).containsExactly("dark", "light", "system");
        assertThat(tiles.options()).extracting(Option::label).containsExactly("Dark", "Light", "Match System");
        assertThat(tiles.selectedId()).isEqualTo("system");
        assertThat(tiles.options().get(2).preview()).contains(new Option.Preview(DARK, Optional.of(LIGHT)));
        assertThat(tiles.options().get(2).description()).isEqualTo("System appearance: Light");
        var row = (Nodes.SettingRow) parts(editor).get(1);
        assertThat(row.title()).isEqualTo("Theme");
        var controls = (Nodes.Stack) row.control();
        var theme = (Nodes.Choice) controls.children().get(0);
        assertThat(theme.kind()).isEqualTo(Nodes.ChoiceKind.DROPDOWN);
        assertThat(theme.options()).extracting(Option::label).containsExactly("Slate Steel");
        assertThat(controls.children()).noneMatch(node -> node instanceof Nodes.MenuButton).as("no gear");
        assertThat(((Nodes.Button) controls.children().get(1)).state().visible()).as("Reset hidden at the default").isFalse();
    }

    @Test void choosingATileEditsTheValueAndResetShows() {
        var editor = new AppearanceSelection(AppearanceChoices.of(DARK, LIGHT));
        editor.show(AppearanceMode.SYSTEM);
        assertThat(editor.event("ui.appearance", new UiEvent.Selected("ui.appearance:policy", java.util.Set.of("light")))).isTrue();
        assertThat(editor.value()).isEqualTo(AppearanceMode.LIGHT);
        assertThat(editor.event("ui.appearance", new UiEvent.Selected("ui.appearance:policy", java.util.Set.of("light")))).as("the same tile again").isFalse();
        var reset = (Nodes.Button) ((Nodes.Stack) ((Nodes.SettingRow) parts(editor).get(1)).control()).children().get(1);
        assertThat(reset.state().visible()).isTrue();
        assertThat(reset.action()).isEqualTo("ui.appearance:reset");
        assertThat(((Nodes.Choice) parts(editor).get(0)).options().get(2).description()).isEqualTo("System appearance unavailable; using Dark");
    }

    @Test void theAppearancePageShowsTheTilesThenTheTerminalSchemeRow() {
        var system = new CorePages.SystemActions(() -> java.nio.file.Path.of("config.toml"), () -> { }, () -> { }, () -> { }, () -> { }, () -> true);
        var specs = CorePages.specs(AppearanceChoices.of(DARK, LIGHT), system, ShellChoices.NONE, ignored -> { }, false);
        var appearance = specs.getFirst();
        assertThat(appearance.settings()).startsWith(CoreSettings.UI_APPEARANCE, CoreSettings.TERMINAL_SCHEME);
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :jasper-app:test --tests '*AppearanceSelectionTest'`
Expected: FAIL to compile.

- [ ] **Step 3: `AppearanceChoices`**

```java
package dev.jasper.app.settingsui;

import dev.jasper.app.lifecycle.Subscription;
import dev.jasper.ui.model.Option;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * What the Appearance page shows, supplied by the application: Slate Steel's Dark and Light previews, the theme's
 * workspace glass (the defaults the Workspace rows return to), the operating system's appearance when it is known, and
 * its changes. EDT only.
 *
 * @param dark the Dark tile's preview
 * @param light the Light tile's preview
 * @param opacity the theme's workspace opacity, 10–100
 * @param blur whether the theme blurs a translucent workspace
 * @param systemDark the operating system's appearance, empty when unavailable
 * @param onChanged runs its argument whenever the operating system's appearance changes
 */
public record AppearanceChoices(Option.Palette dark, Option.Palette light, float opacity, boolean blur,
                                Supplier<Optional<Boolean>> systemDark, Function<Runnable, Subscription> onChanged) {
    public AppearanceChoices {
        Objects.requireNonNull(dark, "dark"); Objects.requireNonNull(light, "light");
        Objects.requireNonNull(systemDark, "systemDark"); Objects.requireNonNull(onChanged, "onChanged");
    }

    /** Fixed previews, an opaque workspace and no system appearance, for pages built without an application. */
    public static AppearanceChoices of(Option.Palette dark, Option.Palette light) {
        return new AppearanceChoices(dark, light, 100f, false, Optional::empty, ignored -> new Subscription(() -> { }));
    }
}
```

- [ ] **Step 4: `AppearanceSelection`**

```java
package dev.jasper.app.settingsui;

import static dev.jasper.app.config.CoreSettings.UI_APPEARANCE;

import dev.jasper.app.config.AppearanceMode;
import dev.jasper.app.lifecycle.Subscription;
import dev.jasper.ui.model.*;
import java.util.List;
import java.util.Optional;

/**
 * Settings › Appearance's theme card: the Dark / Light / Match System tiles for {@code ui.appearance}, previewing
 * Slate Steel's variants, then the Theme row, whose one entry is Slate Steel (there is nothing else to choose). EDT only.
 */
final class AppearanceSelection implements ValueEditor<AppearanceMode> {
    /** The one theme. */
    static final String THEME = "Slate Steel";
    private final AppearanceChoices choices;
    private final Subscription listening;
    private AppearanceMode value = UI_APPEARANCE.defaultValue();
    private Runnable render = () -> { };

    AppearanceSelection(AppearanceChoices choices) {
        this.choices = choices;
        listening = choices.onChanged().apply(() -> render.run());
    }

    @Override public boolean cardRows() { return true; }
    @Override public boolean fullSpan() { return true; }
    @Override public Nodes.Node node(String id, ControlState state) { return node(id, state, false); }
    @Override public Nodes.Node node(String id, ControlState state, boolean highlighted) {
        var plain = new ControlState(state.visible(), state.enabled(), state.busy(), "");
        Optional<Boolean> system = choices.systemDark().get();
        var tiles = new Nodes.Choice(id + ":policy", plain, Nodes.ChoiceKind.PREVIEWS, List.of(
            tile("dark", "Dark", THEME + " Dark", new Option.Preview(choices.dark(), Optional.empty())),
            tile("light", "Light", THEME + " Light", new Option.Preview(choices.light(), Optional.empty())),
            tile("system", "Match System", system.map(dark -> "System appearance: " + (dark ? "Dark" : "Light"))
                .orElse("System appearance unavailable; using Dark"), new Option.Preview(choices.dark(), Optional.of(choices.light())))),
            token(value), "Appearance", "");
        var theme = new Nodes.Choice(id + ":theme", plain, Nodes.ChoiceKind.DROPDOWN,
            List.of(new Option("slate-steel", THEME, Optional.empty())), "slate-steel", "Theme", "");
        var reset = new Nodes.Button(id + ":reset", new ControlState(value != UI_APPEARANCE.defaultValue(), true, false, ""), "",
            "reset", Nodes.ButtonKind.GHOST, id + ":reset", "Reset " + UI_APPEARANCE.label() + " to default");
        var controls = new Nodes.Stack(id + ":controls", plain, Nodes.LayoutRole.SETTINGS_TRAILING, List.of(theme, reset));
        return new Nodes.Stack(id + ":selection", plain, Nodes.LayoutRole.VERTICAL,
            List.of(tiles, new Nodes.SettingRow("row:" + id, plain, "Theme", "", controls, false, highlighted)));
    }

    private static Option tile(String id, String label, String caption, Option.Preview preview) {
        return new Option(id, label, Optional.empty(), "", caption, Optional.of(preview));
    }

    private static String token(AppearanceMode mode) {
        return switch (mode) { case DARK -> "dark"; case LIGHT -> "light"; case SYSTEM -> "system"; };
    }

    @Override public AppearanceMode value() { return value; }
    @Override public void show(AppearanceMode value) { this.value = value; }
    @Override public boolean event(String id, UiEvent event) {
        if (!(event instanceof UiEvent.Selected selected) || !selected.id().equals(id + ":policy")) return false;
        AppearanceMode next = switch (selected.ids().stream().findFirst().orElse("")) {
            case "dark" -> AppearanceMode.DARK;
            case "light" -> AppearanceMode.LIGHT;
            case "system" -> AppearanceMode.SYSTEM;
            default -> null;
        };
        if (next == null || next == value) return false;
        value = next;
        return true;
    }
    @Override public void onChanged(Runnable render) { this.render = render; }
    @Override public void dispose() { listening.close(); }
}
```

- [ ] **Step 5: Editors, pages, workspace defaults and draft**
  - `Editors`: delete `IMPORT_THEME`, `theme(ThemeChoices)`, `workspaceScheme(ThemeChoices)` and the `ThemePick` class (with its JavaDoc); keep `SAME_AS_UI_THEME`; add

    ```java
    /** The Dark / Light / Match System tiles, then the Theme row with its one entry, Slate Steel. */
    public static Factory<AppearanceMode> appearance(AppearanceChoices choices) {
        return new Factory<>((setting, context) -> new AppearanceSelection(choices),
            List.of("Dark", "Light", "Match System", "Theme", AppearanceSelection.THEME));
    }
    ```
  - `CorePages`: every `ThemeChoices themes` parameter of `specs(...)` and `pages(...)` becomes `AppearanceChoices appearance` (and the arguments passed on); the Appearance page's Theme section becomes

    ```java
                .section("Theme")
                .row(UI_APPEARANCE, Editors.appearance(appearance))
                .row(TERMINAL_SCHEME, Editors.choice(Map.of("match", Editors.SAME_AS_UI_THEME, "slate-steel-dark", "Slate Steel Dark",
                    "slate-steel-light", "Slate Steel Light", "high-contrast", "High Contrast"))).note("Colors for terminal panes")
                .row(UI_THEME_NAME).shownWhen(false)
                .row(UI_THEME_WORKSPACE).shownWhen(false)
                .row(UI_THEME_SOURCE).shownWhen(false)
                .row(UI_THEME_FOLLOW_SYSTEM).shownWhen(false)
                .section("Workspace")
                .row(UI_WORKSPACE_OPACITY, WorkspaceEditors.opacity(appearance)).shownWhen(glass)
                .row(UI_WORKSPACE_BLUR, WorkspaceEditors.blur(appearance)).shownWhen(glass)
    ```

    (remove Task 2's hidden `UI_APPEARANCE`/`TERMINAL_SCHEME` rows), and the Terminal › Font & Colors action reads `"Terminal colors come from the terminal scheme in Appearance."` with the button `"Choose terminal scheme…"`.
  - `WorkspaceEditors`: `opacity(ThemeChoices)`/`blur(ThemeChoices)` take `AppearanceChoices`; delete `source(...)`; `Opacity` and `Blur` hold the `AppearanceChoices` and use `choices.opacity()` and `choices.blur()` wherever they used `source(choices, draft).map(ThemeChoices.Source::opacity).orElse(100f)` and the source's blur.
  - `SettingsDraft`: delete `THEME_SELECTION` and `normalizeThemeSelection()`; `stageAll` becomes

    ```java
    /** Publishes one edit atomically. */
    public void stageAll(Map<Setting<?>, Optional<?>> values) {
        values.forEach((setting, value) -> { Objects.requireNonNull(setting); Objects.requireNonNull(value); });
        values.forEach((setting, value) -> {
            if (setting.sameSaved(value, baseline.saved(setting))) pending.remove(setting);
            else pending.put(setting, value);
        });
        changed();
    }
    ```

    `rebase` keeps only `pending.entrySet().removeIf(entry -> entry.getKey().sameSaved(entry.getValue(), baseline.saved(entry.getKey())));` then `changed();`, and `reset` becomes `if (pending.keySet().removeAll(settings)) changed();`.
  - Delete `ThemeChoices.java` and `ThemeSelection.java`.

- [ ] **Step 6: The application side** — create `application/SettingsAppearance.java`:

```java
package dev.jasper.app.application;

import dev.jasper.app.lifecycle.Subscription;
import dev.jasper.app.platform.SystemAppearance;
import dev.jasper.app.settingsui.AppearanceChoices;
import dev.jasper.ui.model.Option;
import dev.jasper.ui.theme.SemanticColor;
import dev.jasper.ui.theme.SlateSteel;
import java.util.Optional;

/** The Appearance page's values: Slate Steel's variants as previews, its workspace glass and the live OS appearance. */
final class SettingsAppearance {
    private SettingsAppearance() { }

    static AppearanceChoices choices(SystemAppearance system) {
        var glass = SlateSteel.bundled().generated(true).glass();
        return new AppearanceChoices(preview(true), preview(false), (float) (glass.opacity() * 100), glass.blur(),
            () -> switch (system.current().preference()) {
                case DARK -> Optional.of(true);
                case LIGHT -> Optional.of(false);
                case UNAVAILABLE -> Optional.empty();
            }, listener -> new Subscription(system.subscribe(ignored -> listener.run())));
    }

    /** A variant's preview: its input, sidebar and app surfaces, text and accent. */
    static Option.Palette preview(boolean dark) {
        var palette = SlateSteel.bundled().palette(dark);
        return new Option.Palette(palette.color(SemanticColor.SURFACE_INPUT).getRGB(), palette.color(SemanticColor.SURFACE_SIDEBAR).getRGB(),
            palette.color(SemanticColor.SURFACE_APP).getRGB(), palette.color(SemanticColor.TEXT_PRIMARY).getRGB(),
            palette.color(SemanticColor.ACCENT).getRGB());
    }
}
```

In `JasperApplication`, replace the `var themeChoices = new dev.jasper.app.settingsui.ThemeChoices(...);` statement (lines 821-830) with `var appearanceChoices = SettingsAppearance.choices(systemAppearance);` and pass `appearanceChoices` to `CorePages.pages(...)`. Delete `application/SettingsThemeSources.java` and the test helper `SettingsThemeSourcesTestAccess.java`. In `application/package-info.java`, remove `dev.jasper.app.themesui, ` from the allowed dependencies.

- [ ] **Step 7: Move the page's tests to the new page**
  - Delete `ThemeSelectionTest.java` (its cases are now `AppearanceSelectionTest`'s).
  - `AppearancePageTest`: replace the theme and workspace dropdown tests (`theThemeAndWorkspaceSchemeAreSearchableDropdownsOfEveryThemeName`, `choosingStagesTheChoiceForApply`, `bothDropdownsSearchAndEnterStagesTheMatch`, `pickingTheEntryAlreadyShownIsNoEdit`, `theListsFollowTheCatalogAndKeepAChoiceItNoLongerHas`, `theGearOffersImportThemeAndThePageClosesIt`) with two tests: choosing the Light tile stages `UI_APPEARANCE = LIGHT`; choosing "High Contrast" in `terminal.scheme`'s dropdown stages `TERMINAL_SCHEME = HIGH_CONTRAST` (select the `terminal.scheme` choice node's option whose label is "High Contrast"); the page's search terms contain "Terminal scheme", "Same as UI theme", "Slate Steel" and "Match System" and not "Import theme…". Build pages with `AppearanceChoices.of(new Option.Palette(1, 2, 3, 4, 5), new Option.Palette(6, 7, 8, 9, 10))` wherever the tests used `ThemeChoices.of(...)`; keep the view-mode and animation tests.
  - `CorePagesTest`: the Appearance settings subsequence starts `UI_APPEARANCE, TERMINAL_SCHEME`; rename `fontAndColorsLinksToTheWorkspaceSchemeInAppearance` to `fontAndColorsLinksToTheTerminalSchemeInAppearance` and expect "Terminal colors come from the terminal scheme in Appearance." and "Choose terminal scheme…".
  - `EditorsTest`: delete the `Editors.theme`/`ThemePick` tests (around lines 69 and 108).
  - `SettingsDialogTest`: `themeAndWorkspaceSchemeShareOneSaveRetainFailedDraftAndCancelDiscardsThem` stages `UI_APPEARANCE`/`TERMINAL_SCHEME` instead of the theme name/workspace and keeps its save-failure and Cancel assertions; `pickingAThemeFromTheListEnablesApply` clicks `ui.appearance:policy.option.light` instead; `retainedThemeSelectionsApplyAndReopenAfterExternalTupleChanges` clicks `ui.appearance:policy.option.dark|light` and Reset (`ui.appearance:reset`) instead of the gear's Reset to defaults, and expects `ui.appearance` removed after Reset.
  - `NativeThemeNavigationTest`, `SettingsSurfacePreview`, `Settings9bPreview`, `SettingsDialogPreview`, `PluginSettingsPagesEvidenceTest`: build pages with `AppearanceChoices` (`AppearanceChoices.of(...)` with the two fixture palettes, or `new AppearanceChoices(..., () -> Optional.empty(), ...)` for `SettingsSurfacePreview`'s `system-unavailable.png`, which now stages `UI_APPEARANCE = SYSTEM`); delete `SettingsSurfacePreview`'s `missing-source.png` extra.

- [ ] **Step 8: Run tests**

Run: `./gradlew :jasper-app:test --tests '*settingsui*' --tests '*AppearanceSelectionTest' --tests '*JasperApplication*'` then `./gradlew :jasper-app:test`
Expected: PASS.

- [ ] **Step 9: Commit**

```bash
git add -A jasper-app/src
git commit -m "The Appearance page on ui.appearance and terminal.scheme

The theme card is the Dark / Light / Match System tiles for ui.appearance,
previewing Slate Steel's variants, then the Theme row whose one entry is
Slate Steel (no gear, no Import theme…). The Terminal scheme row offers Same
as UI theme, Slate Steel Dark, Slate Steel Light and High Contrast.

🤖 Generated with Claude Code at The Home Depot

Co-Authored-By: Claude <noreply@anthropic.com>"
```

---

### Task 5: The runtime on Slate Steel

**Files:**
- Create: `jasper-app/src/main/java/dev/jasper/app/application/AppearancePolicy.java`
- Modify: `jasper-app/src/main/java/dev/jasper/app/application/ConfigurationController.java` (constructors, `configureThemes`, `systemAppearanceChanged`, `problems`, the window wiring at line 154)
- Modify: `jasper-app/src/main/java/dev/jasper/app/application/JasperApplication.java` (constructor, start-up, `scanThemes`)
- Modify: `jasper-app/src/main/java/dev/jasper/app/bootstrap/ApplicationBootstrap.java:41,149,280-291`
- Modify: `jasper-app/src/main/java/dev/jasper/app/workspace/WindowContent.java` (theme catalog and menu methods, `applyTheme`, `workspaceSurface`), `WindowCommands.java:65-70,115-120`
- Modify: `jasper-app/src/main/java/dev/jasper/app/plugins/HostedUi.java:307-313`
- Modify: `jasper-app/src/main/java/dev/jasper/app/application/package-info.java` (drop `dev.jasper.app.themeimport`)
- Delete: `jasper-app/src/main/java/dev/jasper/app/application/ThemePolicy.java`, `ThemeUpgrade.java`; tests `ThemePolicyTest`, `ThemeUpgradeTest`, `NativeThemeUpgradeTest`, `ImportThemePolicyTest`, `workspace/InstalledThemesTest`, `workspace/WindowThemeSelectionTest`
- Test: `jasper-app/src/test/java/dev/jasper/app/application/AppearancePolicyTest.java`, `workspace/TerminalSchemeSurfaceTest.java`, `workspace/TerminalSchemeCommandsTest.java` (replacing `TerminalColorsTest`); rewrite `application/SystemThemeApplicationTest`, `application/JasperApplicationThemesTest`

**Interfaces:**
- Consumes: Task 3's `ThemeController` API; `ConfigSnapshot.appearance()`/`terminalScheme()`.
- Produces: `AppearancePolicy.dark(AppearanceMode, SystemAppearance.Observation)`; `ConfigurationController(ThemeController, ConfigService[, Consumer<Path> editor[, Observation initialAppearance]])`; `JasperApplication(ConfigService, ShellLauncher, CommandHistory, Path, Runnable, Path)` as the full constructor; `WindowContent.selectTerminalScheme(TerminalSchemeChoice)`, `terminalSchemeSelected(TerminalSchemeChoice)`; commands `view.terminal_scheme.<name lower-case>`.

- [ ] **Step 1: Write the failing tests**

`AppearancePolicyTest.java`:

```java
package dev.jasper.app.application;

import static org.assertj.core.api.Assertions.*;

import dev.jasper.app.config.AppearanceMode;
import dev.jasper.app.platform.SystemAppearance.Observation;
import org.junit.jupiter.api.Test;

class AppearancePolicyTest {
    @Test void anExplicitAppearanceWinsAndSystemFollowsTheOperatingSystemOrDark() {
        assertThat(AppearancePolicy.dark(AppearanceMode.DARK, Observation.light())).isTrue();
        assertThat(AppearancePolicy.dark(AppearanceMode.LIGHT, Observation.dark())).isFalse();
        assertThat(AppearancePolicy.dark(AppearanceMode.SYSTEM, Observation.light())).isFalse();
        assertThat(AppearancePolicy.dark(AppearanceMode.SYSTEM, Observation.dark())).isTrue();
        assertThat(AppearancePolicy.dark(AppearanceMode.SYSTEM, Observation.unavailable("headless"))).as("unavailable: Dark").isTrue();
    }
}
```

`TerminalSchemeSurfaceTest.java` (Review Focus 3):

```java
package dev.jasper.app.workspace;

import static dev.jasper.app.workspace.DesktopTestSupport.*;
import static org.assertj.core.api.Assertions.*;

import dev.jasper.app.appearance.ThemeController;
import dev.jasper.app.config.TerminalSchemeChoice;
import dev.jasper.ui.theme.SemanticColor;
import dev.jasper.ui.theme.SlateSteel;
import dev.jasper.ui.theme.TerminalScheme;
import java.util.ArrayDeque;
import org.junit.jupiter.api.Test;

/** spec 5.3: a High Contrast terminal under the Light UI: only the text area is black. */
class TerminalSchemeSurfaceTest {
    @Test void aSchemeColoursTheTextAreaAndTheWindowStaysOnTheUiVariant() throws Exception {
        edt(() -> {
            var themes = new ThemeController(false, TerminalSchemeChoice.HIGH_CONTRAST);
            try (var owner = content(launcher(new ArrayDeque<>()), themes)) {
                owner.newTab(HOME);
                var light = SlateSteel.bundled().palette(false).color(SemanticColor.SURFACE_TERMINAL);
                var black = SlateSteel.bundled().terminal(TerminalScheme.HIGH_CONTRAST).colors().background();
                assertThat(owner.getBackground()).isEqualTo(light);
                assertThat(owner.workspaceSurface()).isEqualTo(light);
                assertThat(owner.currentPane().view().palette().background()).isEqualTo(black);
            } finally { themes.close(); }
        });
    }
}
```

`TerminalSchemeCommandsTest.java` — replace `TerminalColorsTest` (delete it): each of the four `view.terminal_scheme.match|slate_steel_dark|slate_steel_light|high_contrast` commands exists in the command palette with titles "Terminal Scheme: Same as UI theme", "Terminal Scheme: Slate Steel Dark", "Terminal Scheme: Slate Steel Light", "Terminal Scheme: High Contrast"; performing one with no configuration connected applies it (`themes.schemeChoice()` is that choice and the pane palette's background is that scheme's); with a configuration connected it saves `terminal.scheme` (copy `TerminalColorsTest`'s two harnesses, replacing `view.terminal_colors.<key>` and `selectTerminalColors` with the new ids and `selectTerminalScheme`); the selected command is checked.

Rewrite `SystemThemeApplicationTest` (Review Focus 1): with `ui.appearance = "system"`, `systemAppearanceChanged(Observation.light())` makes `themes.current().chrome()` Slate Steel Light without writing config.toml (file text unchanged) or touching an open draft (a `SettingsDraft` staged with another setting keeps its pending edit and `modified()`); with `ui.appearance = "dark"` an OS change does nothing; with `system` and `Observation.unavailable("x")` the window is Dark.

Rewrite `JasperApplicationThemesTest` to: a config with `[ui] appearance = "light"` and `[terminal] scheme = "high-contrast"` starts with `themes.current().chrome()` Slate Steel Light and the High Contrast palette; a config with no appearance starts per the system observation (headless: Dark).

- [ ] **Step 2: Run tests to verify they fail**

Run: `./gradlew :jasper-app:test --tests '*AppearancePolicyTest' --tests '*TerminalSchemeSurfaceTest' --tests '*TerminalSchemeCommandsTest'`
Expected: FAIL to compile.

- [ ] **Step 3: `AppearancePolicy`**

```java
package dev.jasper.app.application;

import dev.jasper.app.config.AppearanceMode;
import dev.jasper.app.platform.SystemAppearance;

/** One rule for start-up, saved configuration and OS observations: the UI variant for {@code ui.appearance}. */
final class AppearancePolicy {
    private static final System.Logger LOG = System.getLogger(AppearancePolicy.class.getName());
    private AppearancePolicy() { }

    /** Whether the UI is Dark: the chosen variant, or with {@code SYSTEM} the operating system's, Dark when unknown. */
    static boolean dark(AppearanceMode mode, SystemAppearance.Observation observation) {
        return switch (mode) {
            case DARK -> true;
            case LIGHT -> false;
            case SYSTEM -> switch (observation.preference()) {
                case DARK -> true;
                case LIGHT -> false;
                case UNAVAILABLE -> {
                    LOG.log(System.Logger.Level.INFO, "The system appearance is unavailable (" + observation.reason() + "); using Dark.");
                    yield true;
                }
            };
        };
    }
}
```

- [ ] **Step 4: `ConfigurationController`**
  - Remove the fields `catalog`, `lastChromeDark`, `migrationProblems`, `migrationSource` (and the `appearance` subscription that only fed `lastChromeDark`, with its `close()` line), the `merged.addAll(migrationProblems);` line and `owner.connectThemeCatalog(catalog::latest);`.
  - Constructors become `(ThemeController themes, ConfigService service)`, `(…, Consumer<Path> editor)` and `(…, Consumer<Path> editor, SystemAppearance.Observation initialAppearance)`, each delegating to the last as the old ones did.
  - `configureThemes` becomes:

```java
    /** Applies the saved appearance, terminal scheme and interface font. */
    private void configureThemes(ConfigService.State next) {
        var snapshot = next.snapshot();
        boolean dark = AppearancePolicy.dark(snapshot.appearance(), systemAppearance);
        themes.configureAnimations(snapshot.animations().enabled());
        try {
            themes.configure(dark, snapshot.terminalScheme(), snapshot.uiFont());
            themeProblems = List.of();
        } catch (ThemeController.InstallationFailure failure) {
            Throwable cause = failure.getCause();
            themeProblems = List.of(new ConfigDiagnostic(ConfigDiagnostic.Severity.WARNING, next.file(), 0, 0, "ui.appearance",
                "Could not apply Slate Steel " + (dark ? "Dark" : "Light") + ": " + (cause != null ? cause.getMessage() : failure.getMessage())
                    + "; keeping the current appearance."));
            throw failure;
        }
    }
```

  - In `systemAppearanceChanged`, `if (!state.snapshot().followSystemTheme()) return;` becomes `if (state.snapshot().appearance() != AppearanceMode.SYSTEM) return;`.
  - Remove the now-unused imports (`ThemeCatalog`, `Theme`, `WorkspaceThemeChoice`).

- [ ] **Step 5: `JasperApplication` and the bootstrap**
  - Delete the 7-argument constructor's `List<ThemeCatalog.Problem> startupThemeProblems` parameter (the 6-argument constructor becomes the full one), the fields `themeCatalog`, `migrationProblems`, `startupConfigProblems`, the `scanThemes` and (if now unused) `scanQuietly` methods, and the `if (service != null) scanThemes(service);` line.
  - Start-up becomes:

```java
        systemAppearance = dev.jasper.app.platform.SystemAppearance.open();
        try {
        boolean dark = AppearancePolicy.dark(startup.appearance(), systemAppearance.current());
        // First Swing work: every later component may read the installed snapshot. A variant that cannot be installed
        // never stops start-up; the configuration reports it.
        themes = ThemeController.startup(dark, startup.terminalScheme(),
            failure -> LOG.log(System.Logger.Level.WARNING, "Starting in Slate Steel Dark: " + failure.getMessage(), failure));
```

  - `configuration = service == null ? null : new ConfigurationController(themes, service, new dev.jasper.app.platform.ConfigEditor()::open, systemAppearance.current());`.
  - `ApplicationBootstrap`: delete the `ThemeUpgrade.prepare(...)` call and its "theme upgrade" trace phase; the `Startup` record (line 41) loses its `problems` component and line 149's matching parameter; `launch.accept(new Startup(service, options));` and the code that built `JasperApplication` from a `Startup` passes no problems.
  - Delete `ThemePolicy.java` and `ThemeUpgrade.java`; in `application/package-info.java` remove `dev.jasper.app.themeimport, ` from the allowed dependencies.

- [ ] **Step 6: Windows**
  - `WindowContent`: delete the `themeCatalog` field, `connectThemeCatalog`, `availableThemes`, `chooseUiTheme`, `chooseWorkspaceTheme`, the `themeCatalog = …` line in `disconnectConfiguration`, `workspaceChoice()`, `terminalColorsSelected`, `workspaceName` and `selectTerminalColors`; add

```java
    /** View › Terminal Scheme: saved for every window; applied to this window's themes when no configuration is connected. */
    void selectTerminalScheme(dev.jasper.app.config.TerminalSchemeChoice scheme) {
        choose(dev.jasper.app.config.CoreSettings.TERMINAL_SCHEME, scheme, () -> {
            themes.configureScheme(scheme);
            chrome.refreshTheme();
        });
    }

    boolean terminalSchemeSelected(dev.jasper.app.config.TerminalSchemeChoice scheme) { return themes.schemeChoice() == scheme; }
```

    In `applyTheme`, the four uses of `theme.palette().background()` (`area.setTerminalBackground`, `tab.setBackground`, `setBackground`, `tabs.setBackground`) become `theme.surface()`, and in `workspaceSurface()` `themes.current().palette().background()` becomes `themes.current().surface()` (the status bar keeps `applyPalette(theme.palette())`, and each pane's view keeps the palette). `selectTheme(Theme)` stays only if a test calls it; otherwise delete it too.
  - `WindowCommands`: the `TerminalColors.builtIns()` loop at line 65 becomes

```java
        for (var scheme : dev.jasper.app.config.TerminalSchemeChoice.values()) {
            String label = switch (scheme) {
                case MATCH -> "Same as UI theme";
                case SLATE_STEEL_DARK -> "Slate Steel Dark";
                case SLATE_STEEL_LIGHT -> "Slate Steel Light";
                case HIGH_CONTRAST -> "High Contrast";
            };
            add(registry, "view.terminal_scheme." + scheme.name().toLowerCase(java.util.Locale.ROOT), "Terminal: " + label,
                "Terminal Scheme: " + label, () -> owner.selectTerminalScheme(scheme), List.of("terminal", "scheme", "colors", "palette"));
        }
```

    `view.themes` keeps its label and gets the keywords `List.of("theme", "appearance", "dark", "light", "terminal scheme")`; the `updateActions` loop at line 115 iterates `TerminalSchemeChoice.values()` with `view("view.terminal_scheme." + scheme.name().toLowerCase(java.util.Locale.ROOT))` and `owner.terminalSchemeSelected(scheme)`.
  - `HostedUi.kitThemes()`: the fallback becomes `var theme = dev.jasper.ui.theme.SlateSteel.bundled().generated(variant.get() == Variant.DARK);`.

- [ ] **Step 7: Retire the tests of removed behaviour and fix their neighbours**
  - Delete `ThemePolicyTest`, `ThemeUpgradeTest`, `NativeThemeUpgradeTest`, `ImportThemePolicyTest`, `InstalledThemesTest`, `WindowThemeSelectionTest` and `TerminalColorsTest` (theme names, the catalog, the start-up theme upgrade, the theme menu and terminal colours no longer exist at runtime).
  - `ConfigurationTestSupport`, `ConfigurationReportTest`, `ConfigurationControllerTest`: build `ConfigurationController` without a catalog; in `ConfigurationControllerTest`, cases that assert catalog themes, unknown theme names or workspace-scheme diagnostics are deleted, and `view.terminal_colors.dark` (line 749) becomes `view.terminal_scheme.slate_steel_dark` saving `terminal.scheme`.
  - `ThemeControllerStartupTest`: delete the cases of the removed `startup(Theme, …)` (`SlateSteelControllerTest` covers start-up); `ThemeController.startup(Theme, …)` and its package overload are deleted from `ThemeController`.
  - Every other failing test is a construction change: a `JasperApplication` or `Startup` built with theme problems, or a window that called a deleted method. Change the construction only.

- [ ] **Step 8: Run tests**

Run: `./gradlew :jasper-app:test` then `./gradlew verifyApplicationArchitecture`
Expected: PASS.

- [ ] **Step 9: Commit**

```bash
git add -A jasper-app/src
git commit -m "The runtime on Slate Steel

Start-up and the configuration apply ui.appearance (with system, the OS's
appearance, live, or Dark when unknown) and terminal.scheme through
ThemeController; no themes folder is scanned, no theme is upgraded. The
scheme colours only the terminal text area: the window behind panes, tabs and
strip keep the UI variant's surface. The command palette's terminal commands
choose a scheme.

🤖 Generated with Claude Code at The Home Depot

Co-Authored-By: Claude <noreply@anthropic.com>"
```

---

### Task 6: The old theme settings removed

**Files:**
- Modify: `jasper-app/src/main/java/dev/jasper/app/config/CoreSettings.java` (remove `UI_THEME_NAME`, `UI_THEME_SOURCE`, `UI_THEME_FOLLOW_SYSTEM`, `UI_THEME_WORKSPACE`, `TERMINAL_COLORS`, their `SCHEMA` entries and the `ui.theme` table note)
- Modify: `ConfigSnapshot.java` (remove `theme`, `terminalColors`, `workspaceTheme`, `themeSource`, `followSystemTheme`, `DEFAULT_THEME`, every compatibility constructor, `workspaceFromTerminal`, `terminalFromWorkspace` and their builder members)
- Modify: `ConfigLoader.java:68,85-105` (the alias-value hook), `ConfigMigration.java:66-90` (`transformed`, `stringSetting`)
- Modify: `jasper-app/src/main/java/dev/jasper/app/settingsui/CorePages.java` (the four hidden rows)
- Delete: `config/ThemeSource.java`, `config/ThemeSelectionMigration.java` and its test; callers of `ThemeSelectionMigration` drop the call
- Modify: `config.example.toml` (the `[ui.theme]` table), `docs/configuration.md` (the four rows, the migration-table rows at 728-729, the theme sections at 759-804 and the example at 130-133)
- Test: `ConfigLoaderTest` (Review Focus 2) and the tests listed in Step 5

**Interfaces:**
- Produces: `record ConfigSnapshot(int tabHeight, ToolbarMode toolbar, boolean statusBar, FontConfig font, Map<String, String> keybindings, int columns, int lines, TerminalConfig terminal, boolean buddyEnabled, int visibleRows, int longCommandSeconds, boolean backgroundEnabled, Map<String, Map<String, Object>> plugins, UiFontConfig uiFont, int railWidth, int toolbarHeight, int statusBarHeight, boolean nativeDecorations, ViewMode viewMode, GlassSettings glass, AnimationSettings animations, AppearanceMode appearance, TerminalSchemeChoice terminalScheme)` and its `Builder`.

- [ ] **Step 1: Write the failing test** — add to `ConfigLoaderTest`:

```java
    /** Review Focus 2: an older install's theme keys start Slate Steel and are reported, never applied or migrated. */
    @Test void theRemovedThemeKeysAreUnknownSettings() {
        var result = parse("[ui.theme]\nname = \"Nord\"\nfollow_system = true\nworkspace = \"Nord\"\n[terminal]\ncolors = \"light\"\n");
        assertThat(result.rejected()).isFalse();
        assertThat(result.snapshot()).isEqualTo(ConfigSnapshot.defaults());
        assertThat(result.diagnostics()).extracting(ConfigDiagnostic::key).containsExactlyInAnyOrder("ui.theme", "terminal.colors");
        assertThat(result.diagnostics()).allSatisfy(d -> {
            assertThat(d.severity()).isEqualTo(ConfigDiagnostic.Severity.WARNING);
            assertThat(d.message()).isEqualTo("Unknown setting or table; ignored.");
        });
    }
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :jasper-app:test --tests '*ConfigLoaderTest'`
Expected: FAIL (the keys still apply; the `ui.theme.variant`-style aliases still move).

- [ ] **Step 3: Remove the settings and the snapshot fields**
  - `CoreSettings`: delete the five constants and their `SCHEMA` entries and the `Map.entry(List.of("ui", "theme"), …)` note.
  - `ConfigSnapshot`: the record header becomes the one in **Interfaces**; the compact constructor drops the `theme`/`terminalColors`/`workspaceTheme`/`themeSource` null checks; delete `DEFAULT_THEME`, every constructor other than the canonical one, `workspaceFromTerminal` and `terminalFromWorkspace`; `defaults()` becomes

```java
    public static ConfigSnapshot defaults() {
        return new ConfigSnapshot(30, ToolbarMode.ICONS_AND_LABELS, true, FontConfig.defaults(), Map.of(), 100, 35, TerminalConfig.defaults(),
            false, PaletteSettings.DEFAULT_VISIBLE_ROWS, 10, CoreSettings.SYSTEM_RUN_IN_BACKGROUND.defaultValue(), Map.of(),
            UiFontConfig.defaults(), DEFAULT_RAIL_WIDTH, DEFAULT_TOOLBAR_HEIGHT, DEFAULT_STATUS_BAR_HEIGHT, false, ViewMode.NORMAL,
            GlassSettings.UNSET, AnimationSettings.defaults(), AppearanceMode.SYSTEM, TerminalSchemeChoice.MATCH);
    }
```

    `from(...)` drops `values.get(CoreSettings.UI_THEME_NAME)`, `terminalFromWorkspace(…)`, `WorkspaceThemeChoice.parse(…)`, `values.get(CoreSettings.UI_THEME_SOURCE)` and `values.get(CoreSettings.UI_THEME_FOLLOW_SYSTEM)` in the canonical order; the `Builder` drops the fields, copies and setters `theme`, `terminalColors`, `workspaceTheme`, `themeSource`, `followSystemTheme`, and `build()` passes the canonical order.
  - `ConfigLoader`: delete `aliasValue` and construct the reader with `new SchemaReader(CoreSettings.SCHEMA, text, (path, value) -> value)`.
  - `ConfigMigration`: every legacy key moves (`document = document.move(key.from(), key.to());`); delete `transformed` and `stringSetting` and the branch that used them.
  - Delete `ThemeSource.java` and `ThemeSelectionMigration.java` (remove its call site) and `CorePages`' four hidden `UI_THEME_*` rows.

- [ ] **Step 4: Example and guide**
  - `config.example.toml`: delete the `[ui.theme]` table (name, source, follow_system, workspace and their comments).
  - `docs/configuration.md`: delete the `ui.theme.source`, `ui.theme.follow_system`, `ui.theme.name` and `ui.theme.workspace` rows and the two migration rows for `ui.theme.terminal`/`terminal.colors` and `ui.theme.variant`; replace the example's `[ui.theme]` block with `[ui]` / `appearance = "system"`; replace the theme sections (about lines 759-804) with:

    ```markdown
    `ui.appearance` chooses the UI appearance: `"dark"` or `"light"` for Slate Steel Dark or Light, or `"system"`
    (the default) to follow the operating system's light or dark setting live. When the operating system's appearance is
    unavailable, `"system"` uses Dark. An operating-system change never writes `config.toml`.

    `terminal.scheme` chooses the terminal panes' colours: `"match"` (the default) uses Slate Steel Dark with the Dark
    appearance and Slate Steel Light with the Light one; `"slate-steel-dark"`, `"slate-steel-light"` and
    `"high-contrast"` choose one. Only the terminal text area changes: tabs, the tab strip, the editor and panels stay on
    the UI appearance.
    ```

- [ ] **Step 5: Move the remaining tests**
  - The 30 `new ConfigSnapshot(...)` calls in `testsupport/ConfigTestSupport`, `config/ConfigLoaderTest`, `config/ViewModeTest`, `launch/LaunchSettingsTest`, `launch/ShellIntegrationEndToEndTest`, `workspace/InitialWindowSizeTest`, `workspace/ExpandedConfigTest` and `workspace/ConfigurationControllerTest` become `ConfigSnapshot.builder()` chains. A compatibility constructor's arguments were, in order, a prefix of `(tabHeight, toolbar, statusBar, font, theme, keybindings, columns, lines, terminal, buddyEnabled, visibleRows, longCommandSeconds, backgroundEnabled, plugins, uiFont, terminalColors, railWidth, toolbarHeight, statusBarHeight, nativeDecorations, workspaceTheme, viewMode | glass, glass, animations)`. Each argument becomes the builder setter of the same name, the theme arguments (`theme`, `terminalColors`, `workspaceTheme`) are dropped, and the rest default as before. For example `new ConfigSnapshot(44, t, s, f, theme, k, c, l, term)` becomes `ConfigSnapshot.builder().tabHeight(44).toolbar(t).statusBar(s).font(f).keybindings(k).columns(c).lines(l).terminal(term).build()`. An assertion that compared a parsed snapshot with such a constructor compares it with the builder chain.
  - Builder calls `.theme(…)`, `.terminalColors(…)`, `.workspaceTheme(…)`, `.themeSource(…)`, `.followSystemTheme(…)` in tests: delete them; a test whose only point was one of those values (`ConfigSnapshotBuilderTest`, `ViewModeTest` and `CoreSettingsTest` cases at lines 52 and 63) asserts `appearance`/`terminalScheme` instead.
  - Delete the alias and migration cases of the removed keys: `ConfigLoaderTest` 45-118 and 489-521 (including `aLeftoverStyleLineIsAnOrdinaryUnknownSettingAndChangesNothing`, now covered by the table warning), `ConfigAliasTest` cases naming `ui.theme.*`/`terminal.colors`, `ConfigMigrationTest` 102 and 111, `ThemePolicyConfigTest` and `ThemeSelectionMigrationTest`; `SettingsSaveTest.savingWorkspaceRemovesSupersededAliasAndKeepsOtherContent` moves to the setting at `CoreSettings.java:115` that still has an alias, or is deleted if none remains.
  - `TomlDocumentTableTest`, `WindowTabsTest`, `GraphiteChromeTest`, the `*TabsPreview` files and `plugins/SessionsCompositionTest`, `StagedAddHostTest`, `StagedFileServerFormTest`: drop their uses of the removed constants and builder setters.

- [ ] **Step 6: Run tests**

Run: `./gradlew :jasper-app:test --tests '*ConfigLoaderTest' --tests '*ConfigTemplateTest'` then `./gradlew :jasper-app:test`
Expected: PASS.

- [ ] **Step 7: Commit**

```bash
git add -A jasper-app/src config.example.toml docs/configuration.md
git commit -m "The old theme settings removed

ui.theme.name, ui.theme.source, ui.theme.follow_system and ui.theme.workspace
go with their aliases (ui.theme.variant, terminal.colors, ui.theme.terminal),
their snapshot fields and compatibility constructors, the alias value hook
and the theme-name migration. A file that still sets them starts Slate Steel
and reports them as unknown settings.

🤖 Generated with Claude Code at The Home Depot

Co-Authored-By: Claude <noreply@anthropic.com>"
```

---

### Task 7: Documentation and the full check

**Files:**
- Modify: `README.md:134`, `docs/themes.md` (settings references only; Plan 3 rewrites it), `docs/app-architecture.md:132`, `docs/rebranding.md:30`, `docs/STATUS.md`, `docs/design/theme-engine/acceptance.md`, the spec's status line, this plan's status banner

- [ ] **Step 1: Documentation**
  - `README.md:134`, `docs/themes.md` (lines 16-17, 67, 160-167, 189), `docs/app-architecture.md:132`, `docs/rebranding.md:30`: wherever they name `ui.theme.name`, `ui.theme.source`, `ui.theme.follow_system`, `ui.theme.workspace` or `terminal.colors`, name `ui.appearance` and `terminal.scheme` instead, in one sentence each: "`ui.appearance` (`system`, `dark`, `light`) chooses Slate Steel Dark or Light, or follows the operating system; `terminal.scheme` (`match`, `slate-steel-dark`, `slate-steel-light`, `high-contrast`) colours the terminal panes."
  - `docs/design/theme-engine/acceptance.md`: add a "Slate Steel Plan 2: app switch" section listing what changed (settings, runtime, terminal surroundings, bold colour, the Appearance page, the command palette's scheme commands) and this plan's eight decisions, verbatim.
  - `docs/STATUS.md`: a dated entry after the Plan 1 entry: "Slate Steel Plan 2 (app switch) complete on `claude/slate-steel`: `ui.appearance` and `terminal.scheme` replace the four theme settings; the app starts and runs on Slate Steel only; a terminal scheme colours only the text area (bold text included); the Appearance page has the Dark / Light / Match System tiles, the one Slate Steel theme and the terminal scheme. Plan 3 (removal and verification) next."
  - Spec status line: append "Plan 2 (app switch) complete on 2026-10-12."; this plan's header gains a status banner with any deviations recorded during execution.

- [ ] **Step 2: Source hygiene** — run the Python check from AGENTS.md over `jasper-ui/src`, `jasper-app/src`, `jasper-terminal/src`, `jasper-sdk/src`, `jasper-sdk-testkit/src` and `plugins`. Expected: no output.

- [ ] **Step 3: Full check** — `./gradlew check --continue` in the worktree. Expected: BUILD SUCCESSFUL; read failures from `*/build/test-results/test/*.xml` and fix each at its owner.

- [ ] **Step 4: Commit**

```bash
git add -A docs README.md
git commit -m "Slate Steel Plan 2: documentation and verification

🤖 Generated with Claude Code at The Home Depot

Co-Authored-By: Claude <noreply@anthropic.com>"
```

---

## Not in Plan 2 (Plan 3)

Removal of the legacy layer and its test seams (`Token`, `GeneratedTheme.tokens()`, `SemanticDefaults`, `ThemeResolver`/`ThemeDocument`, `UiTemplate`'s token tables, `themeio`, `themeimport` in jasper-ui and the app, `themesui`, `ThemeCatalog`, `BuiltInThemes`, `ThemeManager`'s legacy install, `Theme` and its constants, `WorkspaceThemeChoice`, `config.TerminalColors`, `ThemeController`'s `Theme`-based seams), the old themes and assets, `third_party/themes`, `tools/themes`, the Gradle conversion tasks, migration of the ~150 test files to a Slate Steel helper, `docs/themes.md` rewritten, the colour-hex source scan, and the re-rendered mock comparisons.
