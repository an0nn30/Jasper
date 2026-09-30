# Alacritty Terminal Themes Implementation Plan

> **Status:** written 2026-09-30 on `claude/alacritty-themes`; executed subagent-driven 2026-09-30; not merged. Choices this plan makes where the spec left
> room (record further deviations here and in `docs/STATUS.md`):
> - `TerminalColors` becomes a record `TerminalColors(String name)` with constants `MATCH`/`LIGHT`/`DARK` and
>   `builtIns()` (instead of a sealed type), so existing callers keep `name()` and the constants; `terminal.colors` is a
>   string setting and `ConfigSnapshot` parses it.
> - `ThemeState` carries the named theme's palette (resolved by `ConfigurationController` from the terminal catalog), so
>   `appearance` never sees the catalog.
> - The sample terminal shows no selection: the terminal module has no public way to select text. The details pane
>   shows the cursor and selection colours as swatches instead.
> - `TerminalSession.attach` is documented as callable from any thread, so the sample session is created on the EDT.
> - Until Task 5, Font & Colors edits `terminal.colors` in a plain text field.
> - Deviations while executing: `TerminalColors` declares its reserved-names list before the constants (static init
>   order); `ThemeController.configure(Theme)` and `configure(Theme, UiFontConfig)` keep a named terminal palette; the
>   Alacritty page shows the selected file's problems in red; the sample terminal keeps its preferred (60x10) size; the
>   page test uses a non-bundled theme and a separate test shows a user file shadowing bundled "Tokyo Night" until it is
>   removed; failed rescans and reveal failures are reported.
> - Each user file is re-read on every scan only when its size or modified time changed (as the UI theme catalog does).

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** `terminal.colors` accepts an Alacritty theme's name; Jasper bundles the alacritty-theme collection and reads the user's own `.toml` files; Settings › Themes › Alacritty Themes lists them with a live sample terminal; Font & Colors' dropdown lists them and ends in More Terminal Themes….

**Architecture:** A new app package `dev.jasper.app.terminalthemes` holds the parser (`AlacrittyTheme`), the catalog (`TerminalThemeCatalog`: bundled resources plus the `terminal-themes` folder), file installs (`TerminalThemeFiles`), the sample terminal and the page. `ConfigurationController` resolves a named choice through the catalog and hands the palette to `ThemeController`; the catalog rescans with every configuration read. `settingsui` gains a shared `ActionCombo` (the UI theme dropdown's behaviour, generalised) and a `CorePages.Pages` record for application-supplied pages.

**Tech Stack:** Java 25 (JetBrains Runtime), Gradle wrapper, Swing + FlatLaf, tomlj 1.1.1, the terminal module's supported API (`TerminalSession.attach`, `AttachedConnection`, `TerminalView`, `Palette`, `TerminalOptions`), JUnit 5 + AssertJ.

**Spec:** `docs/superpowers/specs/2026-09-30-alacritty-terminal-themes-design.md`

## Global Constraints

- Work in `.worktrees/alacritty-themes` on branch `claude/alacritty-themes`; run `git branch --show-current` before every commit. Never commit on `main`.
- Every commit message ends with `Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>`.
- Use `./gradlew`, never a system `gradle`. Focused tests: `./gradlew :jasper-app:test --tests '<pattern>'`. Full gate: `./gradlew check`.
- Do not launch the GUI or the benchmark. Tests are headless.
- Never put raw control, private-use or unpaired surrogate characters in Java source; write escapes (`"…"`, `"›"`, `"✓"`).
- Bundled collection: alacritty/alacritty-theme at commit `ab88d5a80d676b5dc6157e91aba8067f2078dc94` (177 themes, Apache 2.0), under `jasper-app/src/main/resources/dev/jasper/app/terminalthemes/alacritty/` with `LICENSE`, `SOURCE.md` and `index.txt`.
- User folder: `terminal-themes` beside `config.toml`; only non-hidden `*.toml` files; at most 64 KiB each.
- Names: file name without `.toml`, `_`/`-` as spaces, each word's first letter upper-cased; matched ignoring case and surrounding spaces. `match`, `light`, `dark` are reserved. A user file beats a bundled theme of the same name.
- Palette mapping: primary foreground/background required; `colors.normal.*` → ANSI 0–7, `colors.bright.*` → 8–15 (missing bright = normal; missing normal = Jasper Dark's when background luminance < 0.5, else Jasper Light's); cursor = `colors.cursor.cursor` else foreground; selection = `colors.selection.background` else foreground blended 30% over background. Colours `#rrggbb`/`0xrrggbb`; `CellForeground`/`CellBackground` keywords fall back silently; any other non-colour value is a warning.
- Setting: `terminal.colors`, string, default `"match"`; `match`/`light`/`dark` any case. Unknown name → configuration warning, terminal uses `match`.
- Page id `themes.alacritty` ("Alacritty Themes", help anchor `alacritty-themes`), parent `themes`, after `themes.intellij`. Dropdown action `More Terminal Themes…` → `themes.alacritty`, never stored.
- `terminalthemes` may depend on `appearance`, `config`, `lifecycle`, `settings`, `settingsui` and the terminal module's `session`, `view`, `config`; `appearance` and `settingsui` never depend on `terminalthemes`.

## File Structure

Create (`jasper-app/src/main/java/dev/jasper/app/terminalthemes/`): `package-info.java`, `AlacrittyTheme.java` (parser + display names), `TerminalTheme.java` (name, palette, file), `TerminalThemeCatalog.java`, `TerminalThemeFiles.java`, `SampleTerminal.java`, `AlacrittyThemesPage.java`.
Create resources: `jasper-app/src/main/resources/dev/jasper/app/terminalthemes/alacritty/` (`*.toml`, `LICENSE`, `SOURCE.md`, `index.txt`).
Create tests (`jasper-app/src/test/java/dev/jasper/app/terminalthemes/`): `AlacrittyThemeTest.java`, `TerminalThemeCatalogTest.java`, `TerminalThemeFilesTest.java`, `AlacrittyThemesPageTest.java`; `jasper-app/src/test/java/dev/jasper/app/config/TerminalColorsValueTest.java`; `jasper-app/src/test/java/dev/jasper/app/application/TerminalThemeResolutionTest.java`.
Modify: `config/TerminalColors.java`, `config/CoreSettings.java`, `config/ConfigSnapshot.java`, `appearance/ThemeState.java`, `appearance/ThemeController.java`, `workspace/WindowChrome.java`, `workspace/WindowCommands.java`, `workspace/WindowContent.java`, `application/ConfigurationController.java`, `application/JasperApplication.java`, `application/package-info.java`, `settingsui/Editors.java`, `settingsui/CorePages.java`; tests that name the enum; `config.example.toml`; docs.

---

### Task 1: `terminal.colors` accepts a name

**Files:**
- Modify: `jasper-app/src/main/java/dev/jasper/app/config/TerminalColors.java` (replace), `config/CoreSettings.java:117-122`, `config/ConfigSnapshot.java:162`, `appearance/ThemeState.java`, `appearance/ThemeController.java:54-72`, `workspace/WindowChrome.java:33,88-91,438`, `workspace/WindowCommands.java:64-68,106-110`, `workspace/WindowContent.java:782-787`, `settingsui/CorePages.java:102`
- Modify tests: `config/ConfigLoaderTest.java:429-447`, `config/CoreSettingsTest.java:57`, `workspace/ConfigurationControllerTest.java:710`, `workspace/WindowTabsTest.java:113,133`, `workspace/GraphiteChromeTest.java:67`, `appearance/ThemeStateTest.java`
- Create test: `jasper-app/src/test/java/dev/jasper/app/config/TerminalColorsValueTest.java`
- Modify docs: `config.example.toml:79`, `docs/configuration.md:167`

**Interfaces:**
- Produces: `record TerminalColors(String name)` with `MATCH`, `LIGHT`, `DARK`, `static TerminalColors parse(String)`, `static String normalize(String)`, `static boolean reserved(String)`, `static List<TerminalColors> builtIns()`, `boolean named()`. `CoreSettings.TERMINAL_COLORS` is `Setting<String>`. `ThemeState.configureTerminal(TerminalColors, Palette named)`; `ThemeController.configure(Theme, TerminalColors, Palette named, UiFontConfig)` (`named` null for built-ins or an unknown name).

- [ ] **Step 1: Write the failing tests**

`TerminalColorsValueTest.java`:

```java
package dev.jasper.app.config;

import static org.assertj.core.api.Assertions.*;

import org.junit.jupiter.api.Test;

class TerminalColorsValueTest {
    @Test void builtInsParseInAnyCaseAndOtherTextNamesATheme() {
        assertThat(TerminalColors.parse(" DARK ")).isEqualTo(TerminalColors.DARK).satisfies(c -> assertThat(c.named()).isFalse());
        assertThat(TerminalColors.parse("Match")).isEqualTo(TerminalColors.MATCH);
        assertThat(TerminalColors.parse("")).isEqualTo(TerminalColors.MATCH);
        assertThat(TerminalColors.parse("  Dracula ")).isEqualTo(new TerminalColors("Dracula"))
            .satisfies(c -> { assertThat(c.named()).isTrue(); assertThat(c.name()).isEqualTo("Dracula"); });
        assertThat(TerminalColors.builtIns()).containsExactly(TerminalColors.MATCH, TerminalColors.LIGHT, TerminalColors.DARK);
        assertThat(TerminalColors.reserved(" Light ")).isTrue();
        assertThat(TerminalColors.reserved("Gruvbox Dark")).isFalse();
        assertThat(TerminalColors.normalize(" LIGHT ")).isEqualTo("light");
        assertThat(TerminalColors.normalize(" Nord ")).isEqualTo("Nord");
        assertThatThrownBy(() -> new TerminalColors("  ")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test void theSettingKeepsTheNameAndTheSnapshotParsesIt() {
        var snapshot = ConfigLoader.parse(java.nio.file.Path.of("config.toml"), "[terminal]\ncolors = ' Gruvbox Dark '\n", true);
        assertThat(snapshot.values().get(CoreSettings.TERMINAL_COLORS)).isEqualTo("Gruvbox Dark");
        assertThat(snapshot.snapshot().terminalColors()).isEqualTo(new TerminalColors("Gruvbox Dark"));
        assertThat(ConfigLoader.parse(java.nio.file.Path.of("config.toml"), "[terminal]\ncolors = 'DARK'\n", true)
            .snapshot().terminalColors()).isEqualTo(TerminalColors.DARK);
    }
}
```

In `ThemeStateTest.java` add:

```java
    @Test void aNamedTerminalThemeUsesItsPaletteAndAnUnknownOneMatches() {
        var named = new Palette(Color.BLACK, Color.WHITE, Color.BLUE, Color.GRAY, Collections.nCopies(16, Color.GREEN));
        var state = ThemeState.defaults().configure(INSTALLED);
        assertThat(state.configureTerminal(new TerminalColors("Dracula"), named).resolve()).isEqualTo(new ResolvedTheme(INSTALLED, named));
        assertThat(state.configureTerminal(new TerminalColors("Nope"), null).resolve()).isEqualTo(new ResolvedTheme(INSTALLED, SCHEME));
        assertThat(state.configureTerminal(TerminalColors.DARK, named).resolve().palette())
            .as("a built-in choice ignores a palette").isEqualTo(Palette.jasperDark());
        assertThat(state.configureTerminal(new TerminalColors("Dracula"), named).configure(Theme.LIGHT).resolve().palette())
            .as("a UI theme change keeps the named palette").isEqualTo(named);
    }
```

Update the existing tests that assume an enum:
- `ConfigLoaderTest.terminalColorsDefaultToMatchAndAcceptEveryChoice`: replace `TerminalColors.values()` with `TerminalColors.builtIns()`.
- `ConfigLoaderTest.invalidTerminalColorsUseTheExistingPerFieldDiagnosticPolicy`: replace the value list `java.util.List.of("'private-value'", "7", "true")` with `java.util.List.of("'   '", "7", "true")`, and the message assertion `doesNotContain("private-value")` with `doesNotContain("   ")`.
- `CoreSettingsTest.theSnapshotReadsEveryField`: `.put(CoreSettings.TERMINAL_COLORS, "dark", CoreSettings.TERMINAL_COLORS.key())`.
- `ConfigurationControllerTest` line 710: `until(() -> "dark".equals(seen.getLast().values().get(dev.jasper.app.config.CoreSettings.TERMINAL_COLORS)));`
- `WindowTabsTest` (lines 113, 133) and `GraphiteChromeTest` (line 67): `dev.jasper.app.config.TerminalColors.values()` → `dev.jasper.app.config.TerminalColors.builtIns()`.

- [ ] **Step 2: Run them to verify they fail**

Run: `./gradlew :jasper-app:test --tests 'dev.jasper.app.config.TerminalColorsValueTest' --tests 'dev.jasper.app.appearance.ThemeStateTest'`
Expected: compilation FAILS (`parse`, `builtIns`, `configureTerminal(TerminalColors, Palette)` missing).

- [ ] **Step 3: Replace `TerminalColors.java`**

```java
package dev.jasper.app.config;

import java.util.List;
import java.util.Locale;
import java.util.Objects;

/**
 * The saved terminal palette choice, independent of the UI theme: {@link #MATCH} uses the theme's own palette,
 * {@link #LIGHT} and {@link #DARK} Jasper's, and any other name a terminal theme (Settings > Themes > Alacritty Themes).
 *
 * @param name {@code match}, {@code light}, {@code dark}, or a terminal theme's name as written
 */
public record TerminalColors(String name) {
    /** The UI theme's own terminal palette. */
    public static final TerminalColors MATCH = new TerminalColors("match");
    /** Jasper Light's palette. */
    public static final TerminalColors LIGHT = new TerminalColors("light");
    /** Jasper Dark's palette. */
    public static final TerminalColors DARK = new TerminalColors("dark");
    private static final List<String> RESERVED = List.of("match", "light", "dark");

    /** Strips {@code name} and lower-cases match, light and dark; it must not be blank. */
    public TerminalColors {
        name = normalize(Objects.requireNonNull(name, "name"));
        if (name.isEmpty()) throw new IllegalArgumentException("A terminal colours choice needs a name");
    }

    /** The saved text as a choice; blank means {@link #MATCH}. */
    public static TerminalColors parse(String text) {
        String normalized = normalize(text == null ? "" : text);
        return normalized.isEmpty() ? MATCH : new TerminalColors(normalized);
    }

    /** {@code text} stripped, with match, light and dark lower-cased whatever case they were written in. */
    public static String normalize(String text) {
        String stripped = text.strip();
        String lower = stripped.toLowerCase(Locale.ROOT);
        return RESERVED.contains(lower) ? lower : stripped;
    }

    /** Whether {@code name} is one of the built-in choices, ignoring case and surrounding spaces. */
    public static boolean reserved(String name) { return RESERVED.contains(name.strip().toLowerCase(Locale.ROOT)); }

    /** Match, Light and Dark, in menu order. */
    public static List<TerminalColors> builtIns() { return List.of(MATCH, LIGHT, DARK); }

    /** Whether this names a terminal theme rather than a built-in choice. */
    public boolean named() { return !RESERVED.contains(name); }
}
```

- [ ] **Step 4: Make the setting a string and parse it in the snapshot**

`CoreSettings.java`, replace the `TERMINAL_COLORS` declaration:

```java
    public static final Setting<String> TERMINAL_COLORS = Setting.of("terminal.colors",
            SettingKind.string(text -> !text.isBlank(), "Use a terminal theme's name, or match, light or dark; using match.",
                TerminalColors::normalize), "match")
        .alias("ui.theme.terminal").label("Terminal colors").keywords("palette", "colours", "color scheme", "theme", "alacritty")
        .description("Terminal colors: \"match\" uses the theme's console colours (Jasper's palette when it has\n"
            + "none); \"light\" or \"dark\" keeps Jasper's palette; any other value names a terminal theme from\n"
            + "Settings > Themes > Alacritty Themes. Switches live.").build();
```

`ConfigSnapshot.java` line 162: `values.get(CoreSettings.TERMINAL_COLORS)` → `TerminalColors.parse(values.get(CoreSettings.TERMINAL_COLORS))`.

`CorePages.java` line 102: `.row(TERMINAL_COLORS, Editors.choice(Map.of("match", "Match the theme", "light", "Light", "dark", "Dark"))),` → `.row(TERMINAL_COLORS),` (a text field until Task 5).

- [ ] **Step 5: Carry the named palette in `ThemeState` and `ThemeController`**

Replace `ThemeState.java`'s record body:

```java
/**
 * The configured theme and terminal colours. The chrome is the configured theme; the terminal palette follows the
 * terminal choice: MATCH uses the theme's own palette, LIGHT and DARK Jasper's, and a named choice {@code named}
 * (null when the name is unknown, which then resolves as MATCH).
 */
record ThemeState(Theme saved, TerminalColors terminal, Palette named) {
    ThemeState { Objects.requireNonNull(saved); Objects.requireNonNull(terminal); }

    static ThemeState defaults() { return new ThemeState(Theme.DARK, TerminalColors.MATCH, null); }

    ThemeState configure(Theme next) { return new ThemeState(Objects.requireNonNull(next), terminal, named); }

    ThemeState configureTerminal(TerminalColors next) { return configureTerminal(next, null); }

    /** {@code palette} is the named theme's; it is kept only for a named choice. */
    ThemeState configureTerminal(TerminalColors next, Palette palette) {
        return new ThemeState(saved, Objects.requireNonNull(next), next.named() ? palette : null);
    }

    Theme choice() { return saved; }

    TerminalColors terminalChoice() { return terminal; }

    ResolvedTheme resolve() {
        Palette palette;
        if (terminal.equals(TerminalColors.LIGHT)) palette = Palette.jasperLight();
        else if (terminal.equals(TerminalColors.DARK)) palette = Palette.jasperDark();
        else if (named != null) palette = named;
        else palette = saved.palette();
        return new ResolvedTheme(saved, palette);
    }
}
```

(keep the file's imports; add `import dev.jasper.terminal.config.Palette;` if missing.)

In `ThemeController.java` replace the 3-argument `configure` and fix `apply`'s comparison:

```java
    public void configure(Theme saved, TerminalColors terminal, UiFontConfig font) { configure(saved, terminal, null, font); }
    /** As above, with {@code named} the palette of a named terminal choice (null for a built-in or unknown one). */
    public void configure(Theme saved, TerminalColors terminal, Palette named, UiFontConfig font) {
        requireEdt();
        apply(state.configure(Objects.requireNonNull(saved)).configureTerminal(Objects.requireNonNull(terminal), named),
            Objects.requireNonNull(font));
    }
```

and in `apply`: `boolean choiceChanged = state.choice() != candidate.choice() || !state.terminalChoice().equals(candidate.terminalChoice());` (import `dev.jasper.terminal.config.Palette` if missing).

- [ ] **Step 6: Built-in lists in the workspace**

`WindowChrome.java`: the field becomes `private final java.util.Map<TerminalColors, JRadioButtonMenuItem> terminalItems = new java.util.LinkedHashMap<>();`; the loop `for (TerminalColors colors : TerminalColors.builtIns())`; in `refreshTheme` `item.setSelected(owner.terminalColors().equals(colors))`.

`WindowCommands.java`, both loops use `TerminalColors.builtIns()`; the label line becomes
`String label = colors.equals(TerminalColors.MATCH) ? "Match UI" : colors.equals(TerminalColors.LIGHT) ? "Light" : "Dark";`
and the selected state `owner.terminalColors().equals(colors)`.

`WindowContent.selectTerminalColors`: `choose(dev.jasper.app.config.CoreSettings.TERMINAL_COLORS, colors.name(), () -> { ... })`.

- [ ] **Step 7: Documentation**

`config.example.toml` line 79: `# Terminal colors, live: "match" uses the theme's console colours; "light" or "dark" keeps Jasper's palette; any other value names a terminal theme.`
`docs/configuration.md` line 167 becomes:

```markdown
| `terminal.colors` | `"match"` | `"match"`, `"light"`, `"dark"`, or a terminal theme's name ([Themes](themes.md)); an unknown name is a warning and uses `"match"` | Live |
```

- [ ] **Step 8: Run the tests**

Run: `./gradlew :jasper-app:test --tests 'dev.jasper.app.config.*' --tests 'dev.jasper.app.appearance.*' --tests 'dev.jasper.app.workspace.*' --tests 'dev.jasper.app.settingsui.*'`
Expected: PASS. Fix any other compile error that names the old enum (e.g. `values()`, `==`, a `switch`) the same way and list it in your report.

- [ ] **Step 9: Commit**

```bash
git branch --show-current
git add -A jasper-app/src config.example.toml docs/configuration.md
git commit -m "terminal.colors accepts a terminal theme's name

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 2: The bundled collection and the parser

**Files:**
- Create: resources under `jasper-app/src/main/resources/dev/jasper/app/terminalthemes/alacritty/`
- Create: `jasper-app/src/main/java/dev/jasper/app/terminalthemes/package-info.java`, `AlacrittyTheme.java`
- Test: `jasper-app/src/test/java/dev/jasper/app/terminalthemes/AlacrittyThemeTest.java`

**Interfaces:**
- Produces: `public final class AlacrittyTheme` with `public record Result(Optional<Palette> palette, List<String> problems)`, `public static Result read(String toml)`, `public static String displayName(String fileName)`, `static final String RESOURCES = "/dev/jasper/app/terminalthemes/alacritty/"`, `static List<String> bundledFiles()` (from `index.txt`), `static String bundledText(String file)`.

- [ ] **Step 1: Vendor the collection**

```bash
SHA=ab88d5a80d676b5dc6157e91aba8067f2078dc94
DEST=jasper-app/src/main/resources/dev/jasper/app/terminalthemes/alacritty
TMP=$(mktemp -d)
mkdir -p "$DEST"
curl -sL "https://github.com/alacritty/alacritty-theme/archive/$SHA.tar.gz" | tar xz -C "$TMP"
cp "$TMP/alacritty-theme-$SHA"/themes/*.toml "$DEST/"
cp "$TMP/alacritty-theme-$SHA/LICENSE" "$DEST/LICENSE"
(cd "$DEST" && ls *.toml | LC_ALL=C sort > index.txt)
wc -l "$DEST/index.txt"   # 177
```

Write `$DEST/SOURCE.md`:

```markdown
# Alacritty themes

The `*.toml` files are the `themes/` folder of [alacritty/alacritty-theme](https://github.com/alacritty/alacritty-theme)
at commit `ab88d5a80d676b5dc6157e91aba8067f2078dc94` (2026-09-11), unchanged, under the Apache License 2.0 in
`LICENSE`. `index.txt` lists them, because resources cannot be listed from a jar; regenerate it after updating the files
(`ls *.toml | LC_ALL=C sort > index.txt`).
```

- [ ] **Step 2: Write the failing tests `AlacrittyThemeTest.java`**

```java
package dev.jasper.app.terminalthemes;

import static org.assertj.core.api.Assertions.*;

import dev.jasper.terminal.config.Palette;
import java.awt.Color;
import java.util.HashSet;
import org.junit.jupiter.api.Test;

class AlacrittyThemeTest {
    private static final String FULL = """
        [colors.primary]
        background = '#282a36'
        foreground = '0xF8F8F2'
        [colors.cursor]
        text = 'CellBackground'
        cursor = '#ffb86c'
        [colors.selection]
        background = '#44475a'
        [colors.normal]
        black = '#000000'
        red = '#ff5555'
        green = '#50fa7b'
        yellow = '#f1fa8c'
        blue = '#bd93f9'
        magenta = '#ff79c6'
        cyan = '#8be9fd'
        white = '#bbbbbb'
        [colors.bright]
        black = '#555555'
        red = '#ff6e6e'
        green = '#69ff94'
        yellow = '#ffffa5'
        blue = '#d6acff'
        magenta = '#ff92df'
        cyan = '#a4ffff'
        white = '#ffffff'
        [colors.dim]
        red = '#aa0000'
        [colors.search.matches]
        foreground = '#000000'
        """;

    @Test void aFullThemeMapsOntoThePalette() {
        var result = AlacrittyTheme.read(FULL);
        assertThat(result.problems()).isEmpty();
        Palette palette = result.palette().orElseThrow();
        assertThat(palette.background()).isEqualTo(new Color(0x282a36));
        assertThat(palette.foreground()).isEqualTo(new Color(0xf8f8f2));
        assertThat(palette.cursor()).isEqualTo(new Color(0xffb86c));
        assertThat(palette.selection()).isEqualTo(new Color(0x44475a));
        assertThat(palette.ansi().get(1)).isEqualTo(new Color(0xff5555));
        assertThat(palette.ansi().get(7)).isEqualTo(new Color(0xbbbbbb));
        assertThat(palette.ansi().get(9)).isEqualTo(new Color(0xff6e6e));
        assertThat(palette.ansi().get(15)).isEqualTo(new Color(0xffffff));
    }

    @Test void missingColoursFallBack() {
        var result = AlacrittyTheme.read("""
            [colors.primary]
            background = '#101010'
            foreground = '#e0e0e0'
            [colors.cursor]
            cursor = 'CellForeground'
            [colors.normal]
            red = '#ff0000'
            """);
        assertThat(result.problems()).isEmpty();
        Palette palette = result.palette().orElseThrow();
        assertThat(palette.cursor()).as("a keyword cursor is the foreground").isEqualTo(new Color(0xe0e0e0));
        assertThat(palette.selection()).as("30% foreground over background").isEqualTo(new Color(
            (int) Math.round(0xe0 * 0.3 + 0x10 * 0.7), (int) Math.round(0xe0 * 0.3 + 0x10 * 0.7), (int) Math.round(0xe0 * 0.3 + 0x10 * 0.7)));
        assertThat(palette.ansi().get(1)).isEqualTo(new Color(0xff0000));
        assertThat(palette.ansi().get(2)).as("dark background: Jasper Dark's green").isEqualTo(Palette.jasperDark().ansi().get(2));
        assertThat(palette.ansi().get(9)).as("missing bright takes normal").isEqualTo(new Color(0xff0000));
        var light = AlacrittyTheme.read("[colors.primary]\nbackground = '#fafafa'\nforeground = '#111111'\n").palette().orElseThrow();
        assertThat(light.ansi().get(2)).isEqualTo(Palette.jasperLight().ansi().get(2));
    }

    @Test void badColoursWarnAndMissingPrimaryOrBadTomlIsUnusable() {
        var warned = AlacrittyTheme.read("[colors.primary]\nbackground = '#000000'\nforeground = '#ffffff'\n[colors.normal]\nred = 'reddish'\n");
        assertThat(warned.palette()).isPresent();
        assertThat(warned.problems()).singleElement().asString().contains("colors.normal.red", "reddish");
        assertThat(AlacrittyTheme.read("[colors.primary]\nbackground = '#000000'\n").palette()).isEmpty();
        var broken = AlacrittyTheme.read("[colors.primary\nbackground =");
        assertThat(broken.palette()).isEmpty();
        assertThat(broken.problems()).singleElement().asString().startsWith("It is not valid TOML");
        assertThat(AlacrittyTheme.read("colors = 'nope'\n").palette()).isEmpty();
    }

    @Test void fileNamesBecomeDisplayNames() {
        assertThat(AlacrittyTheme.displayName("gruvbox_dark.toml")).isEqualTo("Gruvbox Dark");
        assertThat(AlacrittyTheme.displayName("Cobalt2.toml")).isEqualTo("Cobalt2");
        assertThat(AlacrittyTheme.displayName("one-half_light.TOML")).isEqualTo("One Half Light");
        assertThat(AlacrittyTheme.displayName("iterm.toml")).isEqualTo("Iterm");
    }

    @Test void everyBundledThemeIsUsableWithoutWarningsAndHasItsOwnName() {
        var files = AlacrittyTheme.bundledFiles();
        assertThat(files).hasSize(177);
        var names = new HashSet<String>();
        for (String file : files) {
            var result = AlacrittyTheme.read(AlacrittyTheme.bundledText(file));
            assertThat(result.palette()).as(file).isPresent();
            assertThat(result.problems()).as(file).isEmpty();
            String name = AlacrittyTheme.displayName(file);
            assertThat(names.add(name.toLowerCase(java.util.Locale.ROOT))).as("duplicate name " + name).isTrue();
            assertThat(dev.jasper.app.config.TerminalColors.reserved(name)).as(name).isFalse();
        }
        assertThat(names).contains("dracula", "gruvbox dark", "cobalt2");
    }
}
```

- [ ] **Step 3: Run them to verify they fail**

Run: `./gradlew :jasper-app:test --tests 'dev.jasper.app.terminalthemes.AlacrittyThemeTest'`
Expected: compilation FAILS.

- [ ] **Step 4: Write `package-info.java`**

```java
/**
 * Terminal colour themes in Alacritty's TOML format: the bundled alacritty-theme collection and the user's files in the
 * terminal-themes folder beside config.toml, read into the terminal module's palette, and Settings > Themes > Alacritty
 * Themes. The application owns the {@link dev.jasper.app.terminalthemes.TerminalThemeCatalog}; SettingsDialog owns each
 * page, whose sample terminal session it closes on disposal. Parsing and scanning run on any thread; Swing is EDT only.
 * <p>Allowed outgoing Jasper dependencies: dev.jasper.app.appearance, dev.jasper.app.config, dev.jasper.app.lifecycle,
 * dev.jasper.app.settings, dev.jasper.app.settingsui, and the terminal module's supported session, view and config
 * packages.
 * The module architecture check forbids package cycles; app types are not an external plugin API.
 */
package dev.jasper.app.terminalthemes;
```

(The `{@link}` to `TerminalThemeCatalog` fails doclint until Task 3; write `{@code TerminalThemeCatalog}` now and switch it to `{@link ...}` in Task 3.)

- [ ] **Step 5: Implement `AlacrittyTheme.java`**

```java
package dev.jasper.app.terminalthemes;

import dev.jasper.terminal.config.Palette;
import java.awt.Color;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Pattern;
import org.tomlj.Toml;
import org.tomlj.TomlParseResult;

/** Reads an Alacritty colour theme (TOML) into Jasper's terminal palette. Pure; any thread. */
public final class AlacrittyTheme {
    /**
     * A read theme.
     *
     * @param palette the palette, or empty when the theme cannot be used
     * @param problems why it cannot be used, and colours that were ignored
     */
    public record Result(Optional<Palette> palette, List<String> problems) {
        public Result { problems = List.copyOf(problems); }
    }

    static final String RESOURCES = "/dev/jasper/app/terminalthemes/alacritty/";
    static final List<String> COLOURS = List.of("black", "red", "green", "yellow", "blue", "magenta", "cyan", "white");
    private static final Pattern HEX = Pattern.compile("(?:#|0[xX])([0-9a-fA-F]{6})");
    private static final List<String> KEYWORDS = List.of("cellforeground", "cellbackground");

    private AlacrittyTheme() { }

    /** Reads a theme's text; never throws for bad input. */
    public static Result read(String toml) {
        TomlParseResult parsed = Toml.parse(toml);
        if (parsed.hasErrors())
            return new Result(Optional.empty(), List.of("It is not valid TOML: " + parsed.errors().getFirst().getMessage()));
        var problems = new ArrayList<String>();
        Color background = colour(parsed, "colors.primary.background", problems);
        Color foreground = colour(parsed, "colors.primary.foreground", problems);
        if (background == null || foreground == null) {
            problems.add("It has no colors.primary background and foreground.");
            return new Result(Optional.empty(), problems);
        }
        Palette fallback = luminance(background) < 0.5 ? Palette.jasperDark() : Palette.jasperLight();
        var ansi = new ArrayList<Color>(16);
        for (int i = 0; i < 8; i++) {
            Color normal = colour(parsed, "colors.normal." + COLOURS.get(i), problems);
            ansi.add(normal != null ? normal : fallback.ansi().get(i));
        }
        for (int i = 0; i < 8; i++) {
            Color bright = colour(parsed, "colors.bright." + COLOURS.get(i), problems);
            ansi.add(bright != null ? bright : ansi.get(i));
        }
        Color cursor = colour(parsed, "colors.cursor.cursor", problems);
        Color selection = colour(parsed, "colors.selection.background", problems);
        return new Result(Optional.of(new Palette(foreground, background, cursor != null ? cursor : foreground,
            selection != null ? selection : blend(foreground, background, 0.3), ansi)), problems);
    }

    /** The name a theme file offers: its base name with {@code _} and {@code -} as spaces and each word capitalised. */
    public static String displayName(String fileName) {
        String base = fileName.toLowerCase(Locale.ROOT).endsWith(".toml") ? fileName.substring(0, fileName.length() - 5) : fileName;
        var words = new ArrayList<String>();
        for (String word : base.split("[_\\-\\s]+"))
            if (!word.isEmpty()) words.add(Character.toUpperCase(word.charAt(0)) + word.substring(1));
        return String.join(" ", words);
    }

    /** The bundled files, from {@code index.txt}. */
    static List<String> bundledFiles() {
        return bundledText("index.txt").lines().map(String::strip).filter(line -> !line.isEmpty()).toList();
    }

    /** A bundled file's text. */
    static String bundledText(String file) {
        try (InputStream in = AlacrittyTheme.class.getResourceAsStream(RESOURCES + file)) {
            if (in == null) throw new IllegalStateException("Missing bundled terminal theme " + file);
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException failure) {
            throw new UncheckedIOException(failure);
        }
    }

    /** The colour at {@code dotted}; null when missing, a keyword, or not a colour (then a problem is added). */
    private static Color colour(TomlParseResult toml, String dotted, List<String> problems) {
        Object value;
        try { value = toml.get(dotted); }
        catch (RuntimeException notATable) { return null; }
        if (value == null) return null;
        if (value instanceof String text) {
            var match = HEX.matcher(text.strip());
            if (match.matches()) return new Color(Integer.parseInt(match.group(1), 16));
            if (KEYWORDS.contains(text.strip().toLowerCase(Locale.ROOT))) return null;
        }
        problems.add(dotted + " is not a colour (#rrggbb or 0xrrggbb): " + value + "; ignored.");
        return null;
    }

    private static double luminance(Color colour) {
        return (0.2126 * colour.getRed() + 0.7152 * colour.getGreen() + 0.0722 * colour.getBlue()) / 255.0;
    }

    private static Color blend(Color over, Color under, double amount) {
        return new Color((int) Math.round(over.getRed() * amount + under.getRed() * (1 - amount)),
            (int) Math.round(over.getGreen() * amount + under.getGreen() * (1 - amount)),
            (int) Math.round(over.getBlue() * amount + under.getBlue() * (1 - amount)));
    }
}
```

- [ ] **Step 6: Run the tests**

Run: `./gradlew :jasper-app:test --tests 'dev.jasper.app.terminalthemes.*' --tests 'dev.jasper.app.documentation.*'`
Expected: PASS. If a bundled file fails, read its text and fix the parser (not the file); list it in the report.

- [ ] **Step 7: Commit**

```bash
git branch --show-current
git add -A jasper-app/src
git commit -m "Bundle the alacritty-theme collection and read Alacritty themes

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 3: The terminal theme catalog

**Files:**
- Create: `jasper-app/src/main/java/dev/jasper/app/terminalthemes/TerminalTheme.java`, `TerminalThemeCatalog.java`
- Modify: `terminalthemes/package-info.java` (`{@link}`)
- Test: `jasper-app/src/test/java/dev/jasper/app/terminalthemes/TerminalThemeCatalogTest.java`

**Interfaces:**
- Consumes: `AlacrittyTheme.read`, `displayName`, `bundledFiles`, `bundledText`; `TerminalColors.reserved`.
- Produces: `public record TerminalTheme(String name, Palette palette, Optional<Path> file)` with `boolean bundled()`; `public final class TerminalThemeCatalog` with `public record Problem(Path file, String message)`, `public record Snapshot(List<TerminalTheme> themes, List<Problem> problems)` (`Optional<TerminalTheme> find(String)`, `List<String> names()`), `public static TerminalThemeCatalog bundledOnly()`, `public static TerminalThemeCatalog beside(Path configFile)`, `public Optional<Path> directory()`, `public Snapshot latest()`, `public synchronized Snapshot scan()`, `static final String FOLDER = "terminal-themes"`, `static final int MAX_FILE = 64 * 1024`.

- [ ] **Step 1: Write the failing tests**

```java
package dev.jasper.app.terminalthemes;

import static org.assertj.core.api.Assertions.*;

import java.awt.Color;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class TerminalThemeCatalogTest {
    @TempDir Path home;

    private Path folder() { return home.resolve("terminal-themes"); }
    private TerminalThemeCatalog catalog() { return TerminalThemeCatalog.beside(home.resolve("config.toml")); }

    static String theme(String background) {
        return "[colors.primary]\nbackground = '" + background + "'\nforeground = '#eeeeee'\n";
    }

    @Test void theBundledCollectionIsListedByNameAndFoundIgnoringCase() {
        var snapshot = TerminalThemeCatalog.bundledOnly().latest();
        assertThat(snapshot.themes()).hasSize(177).allSatisfy(theme -> assertThat(theme.bundled()).isTrue());
        assertThat(snapshot.names()).isSortedAccordingTo(String.CASE_INSENSITIVE_ORDER);
        assertThat(snapshot.find("  DRACULA ")).get().extracting(TerminalTheme::name).isEqualTo("Dracula");
        assertThat(snapshot.find("")).isEmpty();
        assertThat(snapshot.problems()).isEmpty();
        assertThat(TerminalThemeCatalog.bundledOnly().directory()).isEmpty();
    }

    @Test void userFilesComeFirstAndBeatBundledThemesOfTheSameName() throws Exception {
        Files.createDirectories(folder());
        Files.writeString(folder().resolve("dracula.toml"), theme("#010203"));
        Files.writeString(folder().resolve("my_theme.toml"), theme("#0a0b0c"));
        var snapshot = catalog().scan();
        assertThat(snapshot.names()).startsWith("Dracula", "My Theme");
        assertThat(snapshot.names().stream().filter(name -> name.equalsIgnoreCase("dracula"))).hasSize(1);
        TerminalTheme mine = snapshot.find("dracula").orElseThrow();
        assertThat(mine.bundled()).isFalse();
        assertThat(mine.file()).contains(folder().resolve("dracula.toml"));
        assertThat(mine.palette().background()).isEqualTo(new Color(0x010203));
        assertThat(catalog().directory()).contains(folder());
    }

    @Test void problemsAreReportedPerFileAndIgnoredFilesAreSkipped() throws Exception {
        Files.createDirectories(folder());
        Files.writeString(folder().resolve("broken.toml"), "[colors.primary\n");
        Files.writeString(folder().resolve("Light.toml"), theme("#ffffff"));
        // Distinct file names (macOS folders ignore case) that give one display name, "Twin Theme".
        Files.writeString(folder().resolve("twin_theme.toml"), theme("#111111"));
        Files.writeString(folder().resolve("twin-theme.toml"), theme("#222222"));
        Files.writeString(folder().resolve(".hidden.toml"), theme("#333333"));
        Files.writeString(folder().resolve("notes.txt"), "hello");
        Files.writeString(folder().resolve("huge.toml"), "#" + "x".repeat(TerminalThemeCatalog.MAX_FILE));
        var snapshot = catalog().scan();
        assertThat(snapshot.problems()).extracting(TerminalThemeCatalog.Problem::file).extracting(path -> path.getFileName().toString())
            .containsExactlyInAnyOrder("broken.toml", "huge.toml", "Light.toml", "twin_theme.toml");
        assertThat(snapshot.find("Broken")).isEmpty();
        assertThat(snapshot.find("Light")).isEmpty();
        assertThat(snapshot.find("twin theme").orElseThrow().palette().background()).as("twin-theme.toml sorts first").isEqualTo(new Color(0x222222));
        assertThat(snapshot.find("Hidden")).isEmpty();
    }

    @Test void aRescanPicksUpAddedChangedAndRemovedFiles() throws Exception {
        var catalog = catalog();
        assertThat(catalog.scan().find("Mine")).isEmpty();
        assertThat(folder()).isDirectory();
        Path file = Files.writeString(folder().resolve("mine.toml"), theme("#010101"));
        assertThat(catalog.scan().find("Mine").orElseThrow().palette().background()).isEqualTo(new Color(0x010101));
        Files.writeString(file, theme("#020202"));
        Files.setLastModifiedTime(file, FileTime.fromMillis(Files.getLastModifiedTime(file).toMillis() + 5000));
        assertThat(catalog.scan().find("Mine").orElseThrow().palette().background()).isEqualTo(new Color(0x020202));
        Files.delete(file);
        assertThat(catalog.scan().find("Mine")).isEmpty();
        assertThat(catalog.latest().find("Dracula")).isPresent();
    }
}
```

- [ ] **Step 2: Run them to verify they fail**

Run: `./gradlew :jasper-app:test --tests 'dev.jasper.app.terminalthemes.TerminalThemeCatalogTest'`
Expected: compilation FAILS.

- [ ] **Step 3: Implement `TerminalTheme.java`**

```java
package dev.jasper.app.terminalthemes;

import dev.jasper.terminal.config.Palette;
import java.nio.file.Path;
import java.util.Objects;
import java.util.Optional;

/**
 * One terminal theme.
 *
 * @param name its display name
 * @param palette its colours
 * @param file the user's file it comes from; empty for a bundled theme
 */
public record TerminalTheme(String name, Palette palette, Optional<Path> file) {
    public TerminalTheme { Objects.requireNonNull(name); Objects.requireNonNull(palette); Objects.requireNonNull(file); }

    /** Whether it ships with Jasper. */
    public boolean bundled() { return file.isEmpty(); }
}
```

- [ ] **Step 4: Implement `TerminalThemeCatalog.java`**

```java
package dev.jasper.app.terminalthemes;

import dev.jasper.app.config.TerminalColors;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.FileTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * Every terminal theme Jasper offers: the user's files in the {@code terminal-themes} folder beside
 * {@code config.toml}, then the bundled alacritty-theme collection. A user file takes precedence over a bundled theme of
 * the same name. {@link #scan()} does the file work and may run on any thread; {@link #latest()} may be read anywhere.
 */
public final class TerminalThemeCatalog {
    /** A problem with one file in the folder, shown as a configuration warning. */
    public record Problem(Path file, String message) {
        public Problem { Objects.requireNonNull(file); Objects.requireNonNull(message); }
    }

    /** One scan: the themes in list order (the user's by name, then bundled by name) and the problems met. */
    public record Snapshot(List<TerminalTheme> themes, List<Problem> problems) {
        public Snapshot { themes = List.copyOf(themes); problems = List.copyOf(problems); }

        /** The theme with this name, ignoring case and surrounding spaces; none for a blank name. */
        public Optional<TerminalTheme> find(String name) {
            String key = key(name);
            if (key.isEmpty()) return Optional.empty();
            return themes.stream().filter(theme -> key(theme.name()).equals(key)).findFirst();
        }

        /** Every theme's name, in list order. */
        public List<String> names() { return themes.stream().map(TerminalTheme::name).toList(); }
    }

    static final String FOLDER = "terminal-themes";
    static final int MAX_FILE = 64 * 1024;

    private record Stamp(long size, FileTime modified) {}
    private record Read(Stamp stamp, TerminalTheme theme, List<String> problems) {}

    private final Path directory;
    /** Guarded by this. */
    private List<TerminalTheme> bundled;
    /** Each file's last read, by path; guarded by this. */
    private Map<Path, Read> reads = Map.of();
    private volatile Snapshot latest;

    private TerminalThemeCatalog(Path directory) { this.directory = directory; }

    /** Only the bundled themes. */
    public static TerminalThemeCatalog bundledOnly() { return new TerminalThemeCatalog(null); }

    /** The {@code terminal-themes} folder in the directory holding {@code configFile}, and the bundled themes. */
    public static TerminalThemeCatalog beside(Path configFile) {
        return new TerminalThemeCatalog(configFile.toAbsolutePath().getParent().resolve(FOLDER));
    }

    /** The folder this catalog scans; empty for {@link #bundledOnly()}. */
    public Optional<Path> directory() { return Optional.ofNullable(directory); }

    /** The last scan's result, scanning first when there has been none. */
    public Snapshot latest() {
        Snapshot current = latest;
        return current != null ? current : scan();
    }

    /** Reads the bundled themes once and the folder (created when missing), re-reading only changed files. */
    public synchronized Snapshot scan() {
        if (bundled == null) bundled = readBundled();
        var problems = new ArrayList<Problem>();
        var mine = new ArrayList<TerminalTheme>();
        var owners = new HashMap<String, Path>();
        var next = new HashMap<Path, Read>();
        for (Path file : files(problems)) {
            Read read = read(file);
            if (read == null) continue;
            next.put(file, read);
            read.problems().forEach(message -> problems.add(new Problem(file, message)));
            if (read.theme() == null) continue;
            String name = read.theme().name();
            if (TerminalColors.reserved(name)) {
                problems.add(new Problem(file, "\"" + name + "\" is reserved for Jasper's own terminal colours; rename the file."));
                continue;
            }
            Path owner = owners.putIfAbsent(key(name), file);
            if (owner == null) mine.add(read.theme());
            else problems.add(new Problem(file, "Terminal theme \"" + name + "\" is also installed from " + owner.getFileName() + ", which is used."));
        }
        reads = next;
        mine.sort(Comparator.comparing(TerminalTheme::name, String.CASE_INSENSITIVE_ORDER));
        var themes = new ArrayList<>(mine);
        for (TerminalTheme theme : bundled) if (!owners.containsKey(key(theme.name()))) themes.add(theme);
        return latest = new Snapshot(themes, problems);
    }

    /** Non-hidden {@code .toml} regular files, sorted by name ignoring case, then by name. */
    private List<Path> files(List<Problem> problems) {
        if (directory == null) return List.of();
        try {
            Files.createDirectories(directory);
            try (Stream<Path> listing = Files.list(directory)) {
                return listing.filter(file -> {
                        String name = file.getFileName().toString();
                        return !name.startsWith(".") && name.toLowerCase(Locale.ROOT).endsWith(".toml");
                    })
                    .filter(Files::isRegularFile)
                    .sorted(Comparator.comparing((Path file) -> file.getFileName().toString(), String.CASE_INSENSITIVE_ORDER)
                        .thenComparing(file -> file.getFileName().toString()))
                    .toList();
            }
        } catch (IOException | UncheckedIOException | SecurityException failure) {
            problems.add(new Problem(directory, "Cannot read the terminal-themes folder: " + failure));
            return List.of();
        }
    }

    /** The file's theme, from the last read when its size and modified time are unchanged; null when it vanished. */
    private Read read(Path file) {
        Stamp stamp;
        try {
            var attributes = Files.readAttributes(file, BasicFileAttributes.class);
            stamp = new Stamp(attributes.size(), attributes.lastModifiedTime());
        } catch (IOException | SecurityException failure) {
            return null;
        }
        Read previous = reads.get(file);
        if (previous != null && previous.stamp().equals(stamp)) return previous;
        if (stamp.size() > MAX_FILE) return new Read(stamp, null, List.of(file.getFileName() + " is larger than 64 KiB; skipped."));
        String text;
        try { text = StandardCharsets.UTF_8.newDecoder().decode(java.nio.ByteBuffer.wrap(Files.readAllBytes(file))).toString(); }
        catch (CharacterCodingException notUtf8) { return new Read(stamp, null, List.of(file.getFileName() + " is not UTF-8 text; skipped.")); }
        catch (IOException | SecurityException failure) { return new Read(stamp, null, List.of(file.getFileName() + " cannot be read: " + failure.getMessage())); }
        var result = AlacrittyTheme.read(text);
        String name = AlacrittyTheme.displayName(file.getFileName().toString());
        var problems = result.problems().stream().map(problem -> file.getFileName() + ": " + problem).toList();
        if (name.isBlank()) return new Read(stamp, null, List.of(file.getFileName() + " has no usable name; skipped."));
        return new Read(stamp, result.palette().map(palette -> new TerminalTheme(name, palette, Optional.of(file))).orElse(null), problems);
    }

    private static List<TerminalTheme> readBundled() {
        var themes = new ArrayList<TerminalTheme>();
        var seen = new HashSet<String>();
        for (String file : AlacrittyTheme.bundledFiles()) {
            var result = AlacrittyTheme.read(AlacrittyTheme.bundledText(file));
            String name = AlacrittyTheme.displayName(file);
            if (result.palette().isEmpty() || TerminalColors.reserved(name) || !seen.add(key(name))) continue;
            themes.add(new TerminalTheme(name, result.palette().get(), Optional.empty()));
        }
        themes.sort(Comparator.comparing(TerminalTheme::name, String.CASE_INSENSITIVE_ORDER));
        return List.copyOf(themes);
    }

    private static String key(String name) { return name == null ? "" : name.strip().toLowerCase(Locale.ROOT); }
}
```

Switch `package-info.java`'s `{@code TerminalThemeCatalog}` to `{@link dev.jasper.app.terminalthemes.TerminalThemeCatalog}`.

- [ ] **Step 4b: Run the tests**

Run: `./gradlew :jasper-app:test --tests 'dev.jasper.app.terminalthemes.*'` and `./gradlew :jasper-app:javadoc`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git branch --show-current
git add -A jasper-app/src
git commit -m "Terminal theme catalog: bundled themes and the terminal-themes folder

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 4: Named terminal colours take effect

**Files:**
- Modify: `jasper-app/src/main/java/dev/jasper/app/application/ConfigurationController.java` (constructors, `configureThemes`), `application/JasperApplication.java` (catalog field, scan, controller), `application/package-info.java` (allowed dependency)
- Test: `jasper-app/src/test/java/dev/jasper/app/application/TerminalThemeResolutionTest.java`

**Interfaces:**
- Consumes: `TerminalThemeCatalog` (Task 3), `ThemeController.configure(Theme, TerminalColors, Palette, UiFontConfig)` (Task 1).
- Produces: `ConfigurationController(ThemeController, ConfigService, ThemeCatalog, TerminalThemeCatalog)` and `(…, TerminalThemeCatalog, Consumer<Path> editor)`; the older constructors use `TerminalThemeCatalog.bundledOnly()`. `JasperApplication` field `terminalThemes` (used by Tasks 5 and 7).

- [ ] **Step 1: Write the failing test**

```java
package dev.jasper.app.application;

import static dev.jasper.app.workspace.DesktopTestSupport.edt;
import static org.assertj.core.api.Assertions.assertThat;

import dev.jasper.app.appearance.ThemeCatalog;
import dev.jasper.app.appearance.ThemeController;
import dev.jasper.app.config.ConfigService;
import dev.jasper.app.terminalthemes.TerminalThemeCatalog;
import dev.jasper.terminal.config.Palette;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.Executors;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class TerminalThemeResolutionTest {
    @TempDir Path dir;

    @Test void aNamedThemeColoursTheTerminalAndAnUnknownOneWarnsAndMatches() throws Exception {
        Path file = dir.resolve("config.toml");
        Files.writeString(file, "[terminal]\ncolors = 'dracula'\n");
        Files.createDirectories(dir.resolve("terminal-themes"));
        Files.writeString(dir.resolve("terminal-themes/broken.toml"), "[colors.primary\n");
        var terminalThemes = TerminalThemeCatalog.beside(file);
        terminalThemes.scan();
        var worker = Executors.newSingleThreadScheduledExecutor();
        try (var service = new ConfigService(file, true, worker, SwingUtilities::invokeLater)) {
            edt(() -> {
                var themes = new ThemeController(theme -> true);
                var controller = new ConfigurationController(themes, service, ThemeCatalog.builtIn(), terminalThemes, path -> { });
                Palette dracula = terminalThemes.latest().find("Dracula").orElseThrow().palette();
                assertThat(themes.current().palette()).isEqualTo(dracula);
                assertThat(themes.terminalColors().name()).isEqualTo("dracula");
                assertThat(controller.shown().diagnostics()).singleElement()
                    .satisfies(problem -> assertThat(problem.file().getFileName().toString()).isEqualTo("broken.toml"));
                controller.close();
            });
        } finally { worker.shutdownNow(); }

        Files.writeString(file, "[terminal]\ncolors = 'No Such Theme'\n");
        var worker2 = Executors.newSingleThreadScheduledExecutor();
        try (var service = new ConfigService(file, true, worker2, SwingUtilities::invokeLater)) {
            edt(() -> {
                var themes = new ThemeController(theme -> true);
                var controller = new ConfigurationController(themes, service, ThemeCatalog.builtIn(), terminalThemes, path -> { });
                assertThat(themes.current().palette()).as("unknown: match").isEqualTo(themes.choice().palette());
                assertThat(controller.shown().diagnostics()).anySatisfy(problem -> {
                    assertThat(problem.key()).isEqualTo("terminal.colors");
                    assertThat(problem.message()).contains("No Such Theme", "Alacritty Themes");
                });
                controller.close();
            });
        } finally { worker2.shutdownNow(); }
    }
}
```


- [ ] **Step 2: Run it to verify it fails**

Run: `./gradlew :jasper-app:test --tests 'dev.jasper.app.application.TerminalThemeResolutionTest'`
Expected: compilation FAILS (no constructor taking a `TerminalThemeCatalog`).

- [ ] **Step 3: Resolve names in `ConfigurationController`**

Add the field `private final TerminalThemeCatalog terminalThemes;` and replace the constructors:

```java
    ConfigurationController(ThemeController themes, ConfigService service, ThemeCatalog catalog) {
        this(themes, service, catalog, TerminalThemeCatalog.bundledOnly(), new ConfigEditor()::open);
    }

    ConfigurationController(ThemeController themes, ConfigService service, ThemeCatalog catalog, Consumer<Path> editor) {
        this(themes, service, catalog, TerminalThemeCatalog.bundledOnly(), editor);
    }

    ConfigurationController(ThemeController themes, ConfigService service, ThemeCatalog catalog, TerminalThemeCatalog terminalThemes) {
        this(themes, service, catalog, terminalThemes, new ConfigEditor()::open);
    }

    ConfigurationController(ThemeController themes, ConfigService service, ThemeCatalog catalog, TerminalThemeCatalog terminalThemes,
                            Consumer<Path> editor) {
        requireEdt();
        this.themes = themes; this.service = service; this.catalog = catalog; this.terminalThemes = terminalThemes; this.editor = editor;
        // (the rest of the old 4-argument constructor's body, unchanged)
```

In `configureThemes`, after the UI theme's problems and before the `try`:

```java
        TerminalThemeCatalog.Snapshot terminal = terminalThemes.latest();
        for (TerminalThemeCatalog.Problem problem : terminal.problems())
            problems.add(new ConfigDiagnostic(ConfigDiagnostic.Severity.WARNING, problem.file(), 0, 0, "", problem.message()));
        TerminalColors colors = next.snapshot().terminalColors();
        Palette named = null;
        if (colors.named()) {
            named = terminal.find(colors.name()).map(TerminalTheme::palette).orElse(null);
            if (named == null)
                problems.add(new ConfigDiagnostic(ConfigDiagnostic.Severity.WARNING, next.file(), 0, 0, "terminal.colors",
                    "Unknown terminal colors \"" + colors.name() + "\"; using the theme's colours. Settings › Themes › "
                        + "Alacritty Themes lists the terminal themes."));
        }
```

and change the call to `themes.configure(theme, colors, named, next.snapshot().uiFont());`. Imports: `dev.jasper.app.config.TerminalColors`, `dev.jasper.app.terminalthemes.TerminalTheme`, `dev.jasper.app.terminalthemes.TerminalThemeCatalog`, `dev.jasper.terminal.config.Palette`.

- [ ] **Step 4: Own and scan the catalog in `JasperApplication`**

Add a field `private final dev.jasper.app.terminalthemes.TerminalThemeCatalog terminalThemes;`. After `themeCatalog = …`:

```java
        terminalThemes = service == null ? dev.jasper.app.terminalthemes.TerminalThemeCatalog.bundledOnly()
            : dev.jasper.app.terminalthemes.TerminalThemeCatalog.beside(service.initialState().file());
```

In `scanThemes`, replace `service.watchWith(themeCatalog::scan)` with `service.watchWith(() -> { themeCatalog.scan(); terminalThemes.scan(); })` and update its JavaDoc ("The first scan of the themes and terminal-themes folders…"). The field must be assigned before `scanThemes` runs (move the assignment above `if (service != null) scanThemes(service);`). Construct the controller with `new ConfigurationController(themes, service, themeCatalog, terminalThemes)`.

In `application/package-info.java`, add `dev.jasper.app.terminalthemes` to the allowed outgoing dependencies, keeping the list's order and format.

- [ ] **Step 5: Run the tests**

Run: `./gradlew :jasper-app:test --tests 'dev.jasper.app.application.*' --tests 'dev.jasper.app.workspace.*'`
Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git branch --show-current
git add -A jasper-app/src
git commit -m "Named terminal colours come from the terminal theme catalog

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 5: The shared action dropdown and More Terminal Themes…

**Files:**
- Modify: `jasper-app/src/main/java/dev/jasper/app/settingsui/Editors.java` (`ThemeCombo` → `ActionCombo` + `Choices`, `theme`, new `terminalColors`), `settingsui/CorePages.java` (`ALACRITTY_THEMES`, `Pages`, `specs`/`pages` overloads, the terminal colors row)
- Modify tests: `settingsui/EditorsTest.java` (rename `ThemeCombo` → `ActionCombo`; new test), `settingsui/CorePagesTest.java` (new test)

**Interfaces:**
- Produces: `CorePages.ALACRITTY_THEMES = "themes.alacritty"`; `public record CorePages.Pages(Function<PageContext, SettingsPage> keymap, Function<PageContext, SettingsPage> plugins, Function<PageContext, List<SettingsPage>> tools, BiFunction<PageContext, SettingsPage, SettingsPage> intellijThemes, Function<PageContext, Optional<SettingsPage>> alacrittyThemes)` with `static Pages of(keymap, plugins)`; `CorePages.pages(Supplier<List<String>> themeNames, Supplier<List<String>> terminalThemeNames, SystemActions, ShellChoices, Pages)`; `CorePages.specs(Supplier<List<String>> themeNames, Supplier<List<String>> terminalThemeNames, SystemActions, ShellChoices)`; `Editors.terminalColors(Supplier<List<String>>)`; package-private `Editors.ActionCombo` (`reload()`, `enter(ActionEvent, boolean)`, `highlight(int)`, `static navigates(AWTEvent)`), `Editors.Choices`.

- [ ] **Step 1: Write the failing tests**

In `EditorsTest.java`, replace every `Editors.ThemeCombo` with `Editors.ActionCombo`, then add:

```java
    @Test void terminalColorsOfferTheBuiltInsTheThemesAndMoreTerminalThemes() {
        var navigated = new ArrayList<String>();
        var draft = new SettingsDraft(ConfigLoader.parse(Path.of("config.toml"), "", true).values());
        var ctx = new PageContext(draft, navigated::add, Runnable::run, (title, initial) -> Optional.empty(), () -> { });
        var combo = (Editors.ActionCombo) build(Editors.terminalColors(() -> List.of("Dracula", "Nord")), CoreSettings.TERMINAL_COLORS, ctx);
        @SuppressWarnings("unchecked") var box = (JComboBox<String>) combo.component();
        assertThat(java.util.stream.IntStream.range(0, box.getItemCount()).mapToObj(box::getItemAt))
            .containsExactly("Match the theme", "Light", "Dark", "Dracula", "Nord", "More Terminal Themes…");
        combo.show("match");
        assertThat(combo.value()).isEqualTo("match");
        assertThat(((JTextField) box.getEditor().getEditorComponent()).getText()).isEqualTo("Match the theme");
        combo.show("DARK");
        assertThat(combo.value()).isEqualTo("dark");
        var edits = new int[1];
        combo.onEdit(() -> edits[0]++);
        box.setSelectedIndex(3);
        assertThat(combo.value()).isEqualTo("Dracula");
        assertThat(edits[0]).isPositive();
        box.setSelectedIndex(5);
        assertThat(navigated).containsExactly("themes.alacritty");
        assertThat(combo.value()).as("the action is never the value").isEqualTo("Dracula");
        combo.highlight(5);
        combo.enter(new java.awt.event.ActionEvent(box, java.awt.event.ActionEvent.ACTION_PERFORMED, ""), true);
        assertThat(navigated).containsExactly("themes.alacritty", "themes.alacritty");
    }
```

In `CorePagesTest.java` add:

```java
    @Test void theAlacrittyThemesPageFollowsIntellijThemesUnderThemes() {
        var alacritty = new GroupPage("themes.alacritty", "Alacritty Themes", Optional.of("themes"), false, List::of, context);
        var pages = CorePages.pages(() -> List.of("Jasper Dark"), () -> List.of("Dracula"), system, ShellChoices.NONE,
            new CorePages.Pages(CorePagesTest::keymap, CorePagesTest::plugins, page -> List.of(), (page, header) -> header,
                page -> Optional.of(alacritty))).apply(context);
        var ids = pages.stream().map(SettingsPage::id).toList();
        assertThat(ids.indexOf("themes.alacritty")).isEqualTo(ids.indexOf("themes.intellij") + 1);
        assertThat(CorePages.ALACRITTY_THEMES).isEqualTo("themes.alacritty");
        var withoutIt = CorePages.pages(() -> List.of("Jasper Dark"), system, CorePagesTest::keymap, CorePagesTest::plugins).apply(context);
        assertThat(withoutIt).extracting(SettingsPage::id).doesNotContain("themes.alacritty");
    }
```

- [ ] **Step 2: Run them to verify they fail**

Run: `./gradlew :jasper-app:test --tests 'dev.jasper.app.settingsui.*'`
Expected: compilation FAILS.

- [ ] **Step 3: Generalise `ThemeCombo` into `ActionCombo`**

In `Editors.java` (imports: `java.util.function.Consumer`, `java.util.function.IntPredicate`, `java.util.function.UnaryOperator`, `java.util.LinkedHashMap` if missing, `dev.jasper.app.config.TerminalColors`):

1. Add the labels and the built-in colour names near the top of the class:

```java
    static final String MORE_THEMES = "Get More Themes…";
    static final String MANAGE_THEMES = "Manage Themes…";
    static final String MORE_TERMINAL_THEMES = "More Terminal Themes…";
    /** The built-in terminal colours: stored value to shown text, in dropdown order. */
    private static final Map<String, String> BUILT_IN_COLORS = builtInColors();

    private static Map<String, String> builtInColors() {
        var names = new LinkedHashMap<String, String>();
        names.put("match", "Match the theme");
        names.put("light", "Light");
        names.put("dark", "Dark");
        return Collections.unmodifiableMap(names);
    }
```

2. Replace `theme(...)` and add `terminalColors(...)`:

```java
    /** An editable combo of theme names ending in Get More Themes… (Manage Themes… while the Marketplace is off). */
    public static Factory<String> theme(Supplier<List<String>> names) {
        return new Factory<>((setting, context) -> new ActionCombo(new Choices(names, UnaryOperator.identity(), UnaryOperator.identity(),
            index -> false,
            () -> context.draft().value(CoreSettings.THEMES_MARKETPLACE_ENABLED) ? MORE_THEMES : MANAGE_THEMES,
            label -> context.navigate().accept(CorePages.INTELLIJ_THEMES + (MORE_THEMES.equals(label) ? "#marketplace" : "#installed")))),
            List.of("Get More Themes", "Marketplace"));
    }

    /** Terminal colours: Match the theme, Light, Dark, then every terminal theme, ending in More Terminal Themes…. */
    public static Factory<String> terminalColors(Supplier<List<String>> themes) {
        return new Factory<>((setting, context) -> new ActionCombo(new Choices(
            () -> { var all = new ArrayList<>(BUILT_IN_COLORS.values()); all.addAll(themes.get()); return all; },
            Editors::storedColors, Editors::shownColors, index -> index == BUILT_IN_COLORS.size(),
            () -> MORE_TERMINAL_THEMES, label -> context.navigate().accept(CorePages.ALACRITTY_THEMES))),
            List.of("Match the theme", "Alacritty", "More Terminal Themes"));
    }

    /** A shown terminal colours entry as saved: Match the theme, Light and Dark become their tokens. */
    static String storedColors(String shown) {
        for (var entry : BUILT_IN_COLORS.entrySet()) if (entry.getValue().equalsIgnoreCase(shown.strip())) return entry.getKey();
        return TerminalColors.normalize(shown);
    }

    /** A saved terminal colours value as shown. */
    static String shownColors(String stored) {
        String normalized = TerminalColors.normalize(stored);
        return BUILT_IN_COLORS.getOrDefault(normalized, normalized);
    }
```

3. Replace the whole `ThemeCombo` class with `Choices` and `ActionCombo` (the same mechanics; the action entry is whatever label `reload()` last appended):

```java
    /**
     * What an {@link ActionCombo} offers.
     *
     * @param entries the choices as shown
     * @param stored shown text to the stored value
     * @param shown a stored value to shown text
     * @param ruleBefore rows that start a group, drawn below a rule
     * @param actionLabel the trailing entry's label, read each time the list opens
     * @param action runs, with the label, when the trailing entry is chosen
     */
    record Choices(Supplier<List<String>> entries, UnaryOperator<String> stored, UnaryOperator<String> shown,
                   IntPredicate ruleBefore, Supplier<String> actionLabel, Consumer<String> action) { }

    /**
     * An editable combo of choices, re-read each time the list opens, ending in an entry that runs an action (such as
     * opening another page) instead of being chosen; the value stays as it was.
     */
    static final class ActionCombo implements ValueEditor<String> {
        private final Choices choices;
        private final Model model = new Model();
        private final JComboBox<String> box = new JComboBox<>(model);
        private final List<Runnable> listeners = new ArrayList<>();
        private String action = "";
        private boolean showing;

        ActionCombo(Choices choices) {
            this.choices = choices;
            box.setEditable(true);
            box.setPrototypeDisplayValue("JetBrains Mono NL Extra");
            box.setRenderer(new Renderer());
            reload();
            // A look-and-feel update gives the combo a new editor field; follow each one (see Combo).
            onText(editor(), this::edited);
            bindEnter(editor());
            box.addPropertyChangeListener("editor", event -> {
                if (box.getEditor() == null) return;
                onText(editor(), this::edited);
                bindEnter(editor());
            });
            box.addPopupMenuListener(new PopupMenuListener() {
                @Override public void popupMenuWillBecomeVisible(PopupMenuEvent event) { reload(); }
                @Override public void popupMenuWillBecomeInvisible(PopupMenuEvent event) { }
                @Override public void popupMenuCanceled(PopupMenuEvent event) { }
            });
        }

        /** Re-reads the entries and the action's label, keeping the text shown. */
        void reload() {
            showing = true;
            try {
                String text = editor().getText();
                model.removeAllElements();
                for (String entry : choices.entries().get()) model.addElement(entry);
                action = choices.actionLabel().get();
                model.addElement(action);
                model.select(text);
                editor().setText(text);
            } finally { showing = false; }
        }

        private boolean isAction(Object item) { return item != null && item.equals(action); }

        /**
         * Whether choosing the action entry during {@code current} should run it. Basic and FlatLaf select as the arrow
         * and page keys move through the open list, so only a click, Enter or a programmatic choice (no event) runs it.
         */
        static boolean navigates(java.awt.AWTEvent current) {
            if (current instanceof java.awt.event.KeyEvent key) return key.getKeyCode() == java.awt.event.KeyEvent.VK_ENTER
                || key.getKeyChar() == '\n';
            return true;
        }

        private JTextComponent editor() { return (JTextComponent) box.getEditor().getEditorComponent(); }
        private void edited() { if (!showing) for (Runnable listener : List.copyOf(listeners)) listener.run(); }

        /**
         * Binds Enter on the editor field. An editable combo's Enter takes the typed text, not the row highlighted in the
         * open list, and the arrow keys never select the action entry, so Enter on it would do nothing without this.
         */
        private void bindEnter(JTextComponent field) {
            field.getInputMap().put(KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_ENTER, 0), "jasper.actionComboEnter");
            field.getActionMap().put("jasper.actionComboEnter", new AbstractAction() {
                @Override public void actionPerformed(java.awt.event.ActionEvent event) { enter(event, box.isPopupVisible()); }
            });
        }

        /** Enter: the highlighted action entry of the open list runs; anything else accepts the text as usual. */
        void enter(java.awt.event.ActionEvent event, boolean listOpen) {
            Object highlighted = popupList().map(JList::getSelectedValue).orElse(null);
            if (listOpen && isAction(highlighted)) { run(); return; }
            Action accept = editor().getActionMap().get(JTextField.notifyAction);
            if (accept != null) accept.actionPerformed(event);
        }

        /** Highlights a row of the list, as the arrow keys do. */
        void highlight(int index) { popupList().ifPresent(list -> list.setSelectedIndex(index)); }

        private Optional<JList<?>> popupList() {
            return box.getUI().getAccessibleChild(box, 0) instanceof javax.swing.plaf.basic.ComboPopup popup
                ? Optional.of(popup.getList()) : Optional.empty();
        }

        private void run() {
            box.hidePopup();
            choices.action().accept(action);
        }

        @Override public JComponent component() { return box; }
        @Override public String value() { return choices.stored().apply(editor().getText()); }
        @Override public void show(String value) {
            showing = true;
            try {
                String text = choices.shown().apply(value);
                model.select(text);
                editor().setText(text);
            } finally { showing = false; }
        }
        @Override public void onEdit(Runnable listener) { listeners.add(listener); }

        /** Refuses the action entry as a selection and runs it instead. */
        private final class Model extends DefaultComboBoxModel<String> {
            @Override public void setSelectedItem(Object item) {
                if (isAction(item)) {
                    if (showing || !navigates(java.awt.EventQueue.getCurrentEvent())) return;
                    // Enter on typed action text: put the chosen entry back so the action is never the value.
                    Object chosen = getSelectedItem();
                    String text = chosen == null ? "" : chosen.toString();
                    if (!editor().getText().equals(text)) editor().setText(text);
                    run();
                    return;
                }
                super.setSelectedItem(item);
            }
            void select(Object item) { super.setSelectedItem(item); }
        }

        /** Draws the action entry as a link, and a rule above it and above each row that starts a group. */
        private final class Renderer extends DefaultListCellRenderer {
            @Override public Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean selected, boolean focused) {
                var label = (JLabel) super.getListCellRendererComponent(list, value, index, selected, focused);
                label.putClientProperty("html.disable", true);
                boolean actionEntry = isAction(value);
                if (actionEntry && !selected) {
                    Color link = UIManager.getColor("Component.linkColor");
                    if (link != null) label.setForeground(link);
                }
                if (index >= 0 && (actionEntry || choices.ruleBefore().test(index))) {
                    Color rule = UIManager.getColor("Separator.foreground");
                    label.setBorder(BorderFactory.createCompoundBorder(
                        BorderFactory.createMatteBorder(1, 0, 0, 0, rule != null ? rule : Color.GRAY), label.getBorder()));
                }
                return label;
            }
        }
    }
```

(Keep the old `ThemeCombo`'s Javadoc wording where it still applies; delete the old class and its `MORE`/`MANAGE` constants.)

- [ ] **Step 4: `CorePages`: the page slot, the record and the terminal names**

```java
    /** The Alacritty Themes page's id. */
    public static final String ALACRITTY_THEMES = "themes.alacritty";

    /**
     * The pages the application supplies.
     *
     * @param keymap the Keymap page
     * @param plugins the Plugins page
     * @param tools pages under Tools, such as plugins' settings pages
     * @param intellijThemes the IntelliJ Themes page around its schema header
     * @param alacrittyThemes the Alacritty Themes page; empty leaves it out
     */
    public record Pages(Function<PageContext, SettingsPage> keymap, Function<PageContext, SettingsPage> plugins,
                        Function<PageContext, List<SettingsPage>> tools,
                        BiFunction<PageContext, SettingsPage, SettingsPage> intellijThemes,
                        Function<PageContext, Optional<SettingsPage>> alacrittyThemes) {
        /** Keymap and Plugins only. */
        public static Pages of(Function<PageContext, SettingsPage> keymap, Function<PageContext, SettingsPage> plugins) {
            return new Pages(keymap, plugins, context -> List.of(), (context, header) -> header, context -> Optional.empty());
        }
    }
```

`specs`: the existing `specs(themeNames, system, shells)` becomes `return specs(themeNames, List::of, system, shells);`, and the body moves into

```java
    /** Every schema page's spec, in tree order, with the terminal themes the Terminal colors dropdown lists. */
    public static List<SchemaPage.Spec> specs(Supplier<List<String>> themeNames, Supplier<List<String>> terminalThemeNames,
                                              SystemActions system, ShellChoices shells) {
```

where the Font & Colors row becomes `.row(TERMINAL_COLORS, Editors.terminalColors(terminalThemeNames))`.

`pages`: the 7-argument overload becomes

```java
        return pages(themeNames, List::of, system, shells, new Pages(keymap, plugins, tools, intellijThemes, context -> Optional.empty()));
```

and the tree moves into

```java
    /** The whole tree for one dialog, in display order, with the pages the application supplies. */
    public static Function<PageContext, List<SettingsPage>> pages(Supplier<List<String>> themeNames, Supplier<List<String>> terminalThemeNames,
                                                                   SystemActions system, ShellChoices shells, Pages supplied) {
        return context -> {
            var specs = specs(themeNames, terminalThemeNames, system, shells);
            var all = new ArrayList<SettingsPage>();
            Function<String, Supplier<List<SettingsPage>>> childrenOf = id ->
                () -> all.stream().filter(page -> page.parent().equals(Optional.of(id))).toList();
            all.add(new GroupPage("appearance-behavior", "Appearance & Behavior", Optional.empty(), false,
                childrenOf.apply("appearance-behavior"), context));
            for (var spec : specs.subList(0, 6)) all.add(spec.build(context));
            all.add(supplied.keymap().apply(context));
            all.add(new GroupPage(THEMES, "Themes", Optional.empty(), false, childrenOf.apply(THEMES), context));
            all.add(supplied.intellijThemes().apply(context, specs.get(6).build(context)));
            supplied.alacrittyThemes().apply(context).ifPresent(all::add);
            for (var spec : specs.subList(7, specs.size())) all.add(spec.build(context));
            all.add(supplied.plugins().apply(context));
            all.add(new GroupPage("tools", "Tools", Optional.empty(), true, childrenOf.apply("tools"), context));
            all.addAll(supplied.tools().apply(context));
            return List.copyOf(all);
        };
    }
```

Remove `Map` from `CorePages`' imports if it became unused.

- [ ] **Step 5: Run the tests**

Run: `./gradlew :jasper-app:test --tests 'dev.jasper.app.settingsui.*'`
Expected: PASS, including every existing theme-combo test under its new name.

- [ ] **Step 6: Commit**

```bash
git branch --show-current
git add -A jasper-app/src
git commit -m "Settings: shared action dropdown; terminal colours list the themes and More Terminal Themes…

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 6: Installing and removing theme files

**Files:**
- Create: `jasper-app/src/main/java/dev/jasper/app/terminalthemes/TerminalThemeFiles.java`
- Test: `jasper-app/src/test/java/dev/jasper/app/terminalthemes/TerminalThemeFilesTest.java`

**Interfaces:**
- Produces: `TerminalThemeFiles(Path folder)`, `Path folder()`, `synchronized String install(Path source, boolean replace) throws ThemeFileException` (returns the theme's display name), `synchronized void remove(Path file) throws ThemeFileException`, `static class ThemeFileException extends Exception`, `static final class Conflict extends ThemeFileException` with `Path existing()`.

- [ ] **Step 1: Write the failing tests**

```java
package dev.jasper.app.terminalthemes;

import static org.assertj.core.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class TerminalThemeFilesTest {
    @TempDir Path home;

    private Path folder() { return home.resolve("terminal-themes"); }
    private Path download(String name, String text) throws Exception {
        Files.createDirectories(home.resolve("Downloads"));
        return Files.writeString(home.resolve("Downloads").resolve(name), text);
    }

    @Test void aValidThemeIsCopiedInAndNamed() throws Exception {
        var files = new TerminalThemeFiles(folder());
        Path source = download("tokyo_night.toml", TerminalThemeCatalogTest.theme("#1a1b26"));
        assertThat(files.install(source, false)).isEqualTo("Tokyo Night");
        assertThat(folder().resolve("tokyo_night.toml")).hasSameTextualContentAs(source);
        try (var listing = Files.list(folder())) { assertThat(listing).hasSize(1); }
        assertThatThrownBy(() -> files.install(source, false)).isInstanceOfSatisfying(TerminalThemeFiles.Conflict.class,
            conflict -> assertThat(conflict.existing()).isEqualTo(folder().resolve("tokyo_night.toml")));
        assertThat(files.install(source, true)).isEqualTo("Tokyo Night");
    }

    @Test void unusableFilesAreRefused() throws Exception {
        var files = new TerminalThemeFiles(folder());
        assertThatThrownBy(() -> files.install(download("notes.txt", "hi"), false))
            .isInstanceOf(TerminalThemeFiles.ThemeFileException.class).hasMessage("notes.txt is not a .toml theme file.");
        assertThatThrownBy(() -> files.install(download("bad.toml", "[colors.primary\n"), false))
            .isInstanceOf(TerminalThemeFiles.ThemeFileException.class).hasMessageStartingWith("Bad cannot be used: It is not valid TOML");
        assertThatThrownBy(() -> files.install(download("dark.toml", TerminalThemeCatalogTest.theme("#000000")), false))
            .isInstanceOf(TerminalThemeFiles.ThemeFileException.class).hasMessageContaining("reserved");
        assertThatThrownBy(() -> files.install(download("big.toml", "#" + "x".repeat(TerminalThemeCatalog.MAX_FILE)), false))
            .isInstanceOf(TerminalThemeFiles.ThemeFileException.class).hasMessageContaining("64 KiB");
        assertThat(folder()).satisfiesAnyOf(path -> assertThat(path).doesNotExist(), path -> assertThat(path).isEmptyDirectory());
    }

    @Test void removeDeletesOnlyThemeFilesInTheFolder() throws Exception {
        var files = new TerminalThemeFiles(folder());
        files.install(download("mine.toml", TerminalThemeCatalogTest.theme("#101010")), false);
        files.remove(folder().resolve("mine.toml"));
        assertThat(folder().resolve("mine.toml")).doesNotExist();
        Path outside = download("keep.toml", TerminalThemeCatalogTest.theme("#101010"));
        assertThatThrownBy(() -> files.remove(outside)).isInstanceOf(TerminalThemeFiles.ThemeFileException.class);
        assertThat(outside).exists();
        Files.writeString(folder().resolve(".hidden.toml"), "x");
        assertThatThrownBy(() -> files.remove(folder().resolve(".hidden.toml"))).isInstanceOf(TerminalThemeFiles.ThemeFileException.class);
    }
}
```

- [ ] **Step 2: Run them to verify they fail**

Run: `./gradlew :jasper-app:test --tests 'dev.jasper.app.terminalthemes.TerminalThemeFilesTest'`
Expected: compilation FAILS.

- [ ] **Step 3: Implement `TerminalThemeFiles.java`**

```java
package dev.jasper.app.terminalthemes;

import dev.jasper.app.config.TerminalColors;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Locale;

/**
 * Writes the terminal-themes folder: installs a theme file from disk after checking it, and removes the user's own
 * files. The files are small; synchronized; any thread.
 */
final class TerminalThemeFiles {
    /** A refused or failed operation; the message is for the user. */
    static class ThemeFileException extends Exception {
        ThemeFileException(String message) { super(message); }
        ThemeFileException(String message, Throwable cause) { super(message, cause); }
    }

    /** An install whose file name is taken; ask, then install again with {@code replace}. */
    static final class Conflict extends ThemeFileException {
        private final Path existing;
        Conflict(Path existing) { super(existing.getFileName() + " is already installed."); this.existing = existing; }
        Path existing() { return existing; }
    }

    private final Path folder;

    TerminalThemeFiles(Path folder) { this.folder = folder.toAbsolutePath().normalize(); }

    Path folder() { return folder; }

    /** Checks {@code source} is a usable theme with a name Jasper allows, then copies it in; returns its name. */
    synchronized String install(Path source, boolean replace) throws ThemeFileException {
        String fileName = source.getFileName().toString();
        if (!fileName.toLowerCase(Locale.ROOT).endsWith(".toml")) throw new ThemeFileException(fileName + " is not a .toml theme file.");
        if (fileName.startsWith(".")) fileName = "_" + fileName.substring(1);
        String name = AlacrittyTheme.displayName(fileName);
        if (name.isBlank()) throw new ThemeFileException(fileName + " has no usable name.");
        if (TerminalColors.reserved(name))
            throw new ThemeFileException("\"" + name + "\" is reserved for Jasper's own terminal colours; rename the file.");
        String text;
        try {
            if (Files.size(source) > TerminalThemeCatalog.MAX_FILE) throw new ThemeFileException(fileName + " is larger than 64 KiB.");
            text = Files.readString(source, StandardCharsets.UTF_8);
        } catch (IOException failure) {
            throw new ThemeFileException("Cannot read " + fileName + ": " + failure.getMessage(), failure);
        }
        var result = AlacrittyTheme.read(text);
        if (result.palette().isEmpty()) throw new ThemeFileException(name + " cannot be used: " + result.problems().getFirst());
        Path target = folder.resolve(fileName);
        if (Files.exists(target) && !replace) throw new Conflict(target);
        Path temp = folder.resolve("." + fileName + ".part");
        try {
            Files.createDirectories(folder);
            Files.writeString(temp, text, StandardCharsets.UTF_8);
            Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            return name;
        } catch (IOException failure) {
            throw new ThemeFileException("Cannot install " + fileName + ": " + failure.getMessage(), failure);
        } finally {
            try { Files.deleteIfExists(temp); } catch (IOException ignored) { }
        }
    }

    /** Deletes one of the user's theme files. */
    synchronized void remove(Path file) throws ThemeFileException {
        Path absolute = file.toAbsolutePath().normalize();
        String name = absolute.getFileName().toString();
        if (!folder.equals(absolute.getParent()) || name.startsWith(".") || !name.toLowerCase(Locale.ROOT).endsWith(".toml"))
            throw new ThemeFileException(name + " is not one of your terminal themes.");
        try { Files.deleteIfExists(absolute); }
        catch (IOException failure) { throw new ThemeFileException("Cannot remove " + name + ": " + failure.getMessage(), failure); }
    }
}
```

- [ ] **Step 4: Run the tests**

Run: `./gradlew :jasper-app:test --tests 'dev.jasper.app.terminalthemes.*'`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git branch --show-current
git add -A jasper-app/src
git commit -m "Install and remove terminal theme files

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 7: The Alacritty Themes page and its sample terminal

**Files:**
- Create: `jasper-app/src/main/java/dev/jasper/app/terminalthemes/SampleTerminal.java`, `AlacrittyThemesPage.java`
- Modify: `jasper-app/src/main/java/dev/jasper/app/application/JasperApplication.java` (the `CorePages.pages` call)
- Test: `jasper-app/src/test/java/dev/jasper/app/terminalthemes/AlacrittyThemesPageTest.java`

**Interfaces:**
- Consumes: `TerminalThemeCatalog`, `TerminalThemeFiles`, `CorePages.ALACRITTY_THEMES`, `CorePages.THEMES`, `CorePages.Pages`, `PageContext`, `SettingsDraft.onChanged`, `CoreSettings.TERMINAL_COLORS`, the terminal API.
- Produces: `public final class AlacrittyThemesPage implements SettingsPage` with `public record Hooks(TerminalThemeCatalog catalog, Supplier<CompletableFuture<?>> rescan, Consumer<Path> reveal, BiPredicate<Component, String> confirm, Supplier<TerminalOptions> sampleOptions, Executor edt)`, `public AlacrittyThemesPage(PageContext, Hooks)`, `public static boolean ask(Component, String)`; package-private test accessors `listed()`, `filter(String)`, `select(String)`, `selectedName()`, `useButton()`, `removeButton()`, `installButton()`, `status()`, `inUse()`, `sample()`. `SampleTerminal(TerminalOptions)`, `component()`, `palette(Palette)`, `palette()`, `closed()`, `close()`.

- [ ] **Step 1: Write the failing tests**

```java
package dev.jasper.app.terminalthemes;

import static org.assertj.core.api.Assertions.*;

import dev.jasper.app.config.ConfigLoader;
import dev.jasper.app.config.ConfigSnapshot;
import dev.jasper.app.config.CoreSettings;
import dev.jasper.app.settingsui.PageContext;
import dev.jasper.app.settingsui.SettingsDraft;
import dev.jasper.app.testsupport.EdtTestExtension;
import dev.jasper.terminal.config.Palette;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;

@ExtendWith(EdtTestExtension.class)
class AlacrittyThemesPageTest {
    @TempDir Path home;
    private TerminalThemeCatalog catalog;
    private SettingsDraft draft;
    private Optional<Path> chosen = Optional.empty();
    private final List<String> questions = new ArrayList<>();
    private final List<Path> revealed = new ArrayList<>();
    private boolean answer = true;

    @BeforeEach void start() {
        catalog = TerminalThemeCatalog.beside(home.resolve("config.toml"));
        catalog.scan();
        draft = new SettingsDraft(ConfigLoader.parse(home.resolve("config.toml"), "", true).values());
    }

    private AlacrittyThemesPage page() {
        var context = new PageContext(draft, id -> { }, Runnable::run, (title, initial) -> chosen, () -> { });
        var page = new AlacrittyThemesPage(context, new AlacrittyThemesPage.Hooks(catalog,
            () -> { catalog.scan(); return CompletableFuture.completedFuture(null); }, revealed::add,
            (parent, question) -> { questions.add(question); return answer; },
            () -> ConfigSnapshot.defaults().viewOptions(13f, Palette.jasperDark()), Runnable::run));
        page.component();
        return page;
    }

    @Test void theListFiltersSelectsAndUsesATheme() {
        var page = page();
        assertThat(page.id()).isEqualTo("themes.alacritty");
        assertThat(page.parent()).contains("themes");
        assertThat(page.listed()).hasSize(177).contains("Dracula");
        page.filter("drac");
        assertThat(page.listed()).allSatisfy(name -> assertThat(name.toLowerCase()).contains("drac"));
        page.select("Dracula");
        assertThat(page.sample().palette()).isEqualTo(catalog.latest().find("Dracula").orElseThrow().palette());
        assertThat(page.removeButton().isVisible()).as("bundled themes cannot be removed").isFalse();
        page.useButton().doClick();
        assertThat(draft.value(CoreSettings.TERMINAL_COLORS)).isEqualTo("Dracula");
        assertThat(page.inUse()).isEqualTo("Dracula");
        assertThat(page.status()).contains("Apply");
        page.filter("");
        assertThat(page.listed()).hasSize(177);
    }

    @Test void installingAFileAddsAndSelectsItAndRemovingAsksFirst() throws Exception {
        Files.createDirectories(home.resolve("Downloads"));
        chosen = Optional.of(Files.writeString(home.resolve("Downloads/tokyo_night.toml"), TerminalThemeCatalogTest.theme("#1a1b26")));
        var page = page();
        page.installButton().doClick();
        assertThat(home.resolve("terminal-themes/tokyo_night.toml")).exists();
        assertThat(page.listed().getFirst()).isEqualTo("Tokyo Night");
        assertThat(page.selectedName()).isEqualTo("Tokyo Night");
        assertThat(page.removeButton().isVisible()).isTrue();
        page.installButton().doClick();
        assertThat(questions).containsExactly("tokyo_night.toml is already installed. Replace it?");
        answer = false;
        page.removeButton().doClick();
        assertThat(home.resolve("terminal-themes/tokyo_night.toml")).as("declined").exists();
        answer = true;
        page.removeButton().doClick();
        assertThat(questions.getLast()).isEqualTo("Remove Tokyo Night? This deletes tokyo_night.toml.");
        assertThat(home.resolve("terminal-themes/tokyo_night.toml")).doesNotExist();
        assertThat(page.listed()).doesNotContain("Tokyo Night");
    }

    @Test void anUnusableFileIsReportedAndDisposeClosesTheSample() throws Exception {
        Files.createDirectories(home.resolve("Downloads"));
        chosen = Optional.of(Files.writeString(home.resolve("Downloads/light.toml"), TerminalThemeCatalogTest.theme("#ffffff")));
        var page = page();
        page.installButton().doClick();
        assertThat(page.status()).contains("reserved");
        page.dispose();
        assertThat(page.sample().closed()).isTrue();
    }
}
```

- [ ] **Step 2: Run them to verify they fail**

Run: `./gradlew :jasper-app:test --tests 'dev.jasper.app.terminalthemes.AlacrittyThemesPageTest'`
Expected: compilation FAILS.

- [ ] **Step 3: Implement `SampleTerminal.java`**

```java
package dev.jasper.app.terminalthemes;

import dev.jasper.terminal.config.GridSize;
import dev.jasper.terminal.config.Palette;
import dev.jasper.terminal.config.TerminalOptions;
import dev.jasper.terminal.session.AttachedConnection;
import dev.jasper.terminal.session.TerminalSession;
import dev.jasper.terminal.view.TerminalView;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.SequenceInputStream;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import javax.swing.JComponent;

/**
 * A real terminal showing canned output, drawn by Jasper's renderer in whatever palette it is given: a prompt, a
 * listing in the ANSI colours, and the 16 colours as blocks. It takes no input and never ends until closed. EDT only.
 */
final class SampleTerminal implements AutoCloseable {
    static final String E = "\033[";
    /** The output: plain text, SGR colours and a prompt; lines end in CR LF. */
    static final String TEXT = E + "32m" + "me@jasper" + E + "0m:" + E + "34m" + "~/projects" + E + "0m$ ls -l\r\n"
        + "drwxr-xr-x  " + E + "1;34m" + "src" + E + "0m      " + E + "1;32m" + "build.sh" + E + "0m  "
        + E + "35m" + "logo.png" + E + "0m  " + E + "36m" + "link" + E + "0m -> " + E + "33m" + "notes.md" + E + "0m\r\n"
        + E + "31m" + "error:" + E + "0m something failed   " + E + "33m" + "warning:" + E + "0m check this\r\n"
        + "\r\n" + blocks(40) + "\r\n" + blocks(100) + "\r\n\r\n"
        + E + "32m" + "me@jasper" + E + "0m:" + E + "34m" + "~/projects" + E + "0m$ ";

    private final TerminalSession session;
    private final TerminalView view;
    private final CountDownLatch stopped = new CountDownLatch(1);
    private boolean closed;

    SampleTerminal(TerminalOptions options) {
        var exited = new CompletableFuture<Integer>();
        InputStream idle = new InputStream() {
            @Override public int read() throws IOException {
                try { stopped.await(); return -1; }
                catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); throw new IOException(interrupted); }
            }
            @Override public void close() { stopped.countDown(); }
        };
        InputStream output = new SequenceInputStream(new ByteArrayInputStream(TEXT.getBytes(StandardCharsets.UTF_8)), idle);
        session = TerminalSession.attach(new AttachedConnection(output, OutputStream.nullOutputStream(), (columns, rows) -> { }, exited,
            () -> { stopped.countDown(); exited.complete(0); }), new GridSize(60, 10), 100);
        view = new TerminalView(session, options);
        view.setFocusable(false);
    }

    /** Eight colour blocks, SGR {@code base}..{@code base+7}, separated by spaces. */
    private static String blocks(int base) {
        var line = new StringBuilder();
        for (int i = 0; i < 8; i++) line.append(E).append(base + i).append("m").append("    ").append(E).append("0m ");
        return line.toString();
    }

    JComponent component() { return view; }

    void palette(Palette palette) { view.setPalette(palette); }

    Palette palette() { return view.palette(); }

    boolean closed() { return closed; }

    @Override public void close() {
        if (closed) return;
        closed = true;
        session.close();
    }
}
```

(If `GridSize` lives in another supported package than `dev.jasper.terminal.config`, import it from there.)

- [ ] **Step 4: Implement `AlacrittyThemesPage.java`**

```java
package dev.jasper.app.terminalthemes;

import dev.jasper.app.config.CoreSettings;
import dev.jasper.app.config.TerminalColors;
import dev.jasper.app.lifecycle.Subscription;
import dev.jasper.app.settingsui.CorePages;
import dev.jasper.app.settingsui.FillingPage;
import dev.jasper.app.settingsui.PageContext;
import dev.jasper.app.settingsui.SettingsPage;
import dev.jasper.terminal.config.Palette;
import dev.jasper.terminal.config.TerminalOptions;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.function.BiPredicate;
import java.util.function.Consumer;
import java.util.function.Supplier;
import javax.swing.AbstractAction;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.DefaultListModel;
import javax.swing.Icon;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTextField;
import javax.swing.KeyStroke;
import javax.swing.ListCellRenderer;
import javax.swing.ListSelectionModel;
import javax.swing.UIManager;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;

/**
 * Settings > Themes > Alacritty Themes: every terminal theme with a colour strip, a sample terminal in the selected
 * theme's colours, Use (applied on Apply or OK), Install from Disk, Reveal Folder and Remove for the user's own files.
 * SettingsDialog owns each page; disposing it closes the sample terminal. EDT only.
 */
public final class AlacrittyThemesPage implements SettingsPage {
    /**
     * What the page needs from the application.
     *
     * @param catalog the terminal themes
     * @param rescan reads the folder again (the configuration reload does); called on the EDT
     * @param reveal shows a folder in the file manager
     * @param confirm asks a yes/no question over a component
     * @param sampleOptions the terminal font and behaviour the sample uses
     * @param edt runs work on the EDT, for results of {@code rescan}
     */
    public record Hooks(TerminalThemeCatalog catalog, Supplier<CompletableFuture<?>> rescan, Consumer<Path> reveal,
                        BiPredicate<Component, String> confirm, Supplier<TerminalOptions> sampleOptions, Executor edt) {
        public Hooks {
            Objects.requireNonNull(catalog); Objects.requireNonNull(rescan); Objects.requireNonNull(reveal);
            Objects.requireNonNull(confirm); Objects.requireNonNull(sampleOptions); Objects.requireNonNull(edt);
        }
    }

    private final PageContext context;
    private final Hooks hooks;
    private final TerminalThemeFiles files;
    private final DefaultListModel<TerminalTheme> model = new DefaultListModel<>();
    private final JList<TerminalTheme> list = new JList<>(model);
    private final JTextField search = new JTextField();
    private final JLabel title = new JLabel();
    private final JLabel source = new JLabel();
    private final JLabel swatches = new JLabel();
    private final JLabel status = new JLabel();
    private final JButton use = new JButton("Use");
    private final JButton remove = new JButton("Remove…");
    private final JButton install = new JButton("Install from Disk…");
    private final JButton reveal = new JButton("Reveal Folder");
    private final JPanel sampleHolder = new JPanel(new BorderLayout());
    private List<TerminalTheme> all = List.of();
    private SampleTerminal sample;
    private FillingPage body;
    private Subscription draftListening;

    public AlacrittyThemesPage(PageContext context, Hooks hooks) {
        this.context = Objects.requireNonNull(context);
        this.hooks = Objects.requireNonNull(hooks);
        this.files = new TerminalThemeFiles(hooks.catalog().directory()
            .orElseThrow(() -> new IllegalArgumentException("The terminal theme catalog has no folder")));
    }

    /** A standard Yes/No question; the production {@link Hooks#confirm}. */
    public static boolean ask(Component parent, String question) {
        return JOptionPane.showConfirmDialog(parent, question, "Alacritty Themes", JOptionPane.YES_NO_OPTION,
            JOptionPane.QUESTION_MESSAGE) == JOptionPane.YES_OPTION;
    }

    @Override public String id() { return CorePages.ALACRITTY_THEMES; }
    @Override public String title() { return "Alacritty Themes"; }
    @Override public Optional<String> parent() { return Optional.of(CorePages.THEMES); }
    @Override public String helpAnchor() { return "alacritty-themes"; }
    @Override public List<String> searchTerms() {
        return List.of("Alacritty Themes", "terminal themes colours colors palette install toml");
    }

    @Override public JComponent component() {
        if (body != null) return body;
        for (JComponent label : List.of(title, source, status, use, remove, install, reveal)) label.putClientProperty("html.disable", true);
        search.putClientProperty(com.formdev.flatlaf.FlatClientProperties.PLACEHOLDER_TEXT, "Search terminal themes");
        search.getDocument().addDocumentListener(new DocumentListener() {
            @Override public void insertUpdate(DocumentEvent event) { filter(search.getText()); }
            @Override public void removeUpdate(DocumentEvent event) { filter(search.getText()); }
            @Override public void changedUpdate(DocumentEvent event) { filter(search.getText()); }
        });
        list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        list.setCellRenderer(new Renderer());
        list.getAccessibleContext().setAccessibleName("Terminal themes");
        list.addListSelectionListener(event -> { if (!event.getValueIsAdjusting()) showDetails(); });
        list.getInputMap().put(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0), "jasper.useTheme");
        list.getActionMap().put("jasper.useTheme", new AbstractAction() {
            @Override public void actionPerformed(ActionEvent event) { use(); }
        });
        use.addActionListener(event -> use());
        remove.addActionListener(event -> remove());
        install.addActionListener(event -> installFromDisk());
        reveal.addActionListener(event -> hooks.reveal().accept(files.folder()));

        var toolbar = new JPanel(new FlowLayout(FlowLayout.LEADING, 6, 6));
        toolbar.setOpaque(false);
        toolbar.add(install);
        toolbar.add(reveal);
        toolbar.add(status);
        var left = new JPanel(new BorderLayout(0, 6));
        left.setOpaque(false);
        left.setBorder(BorderFactory.createEmptyBorder(6, 6, 6, 6));
        left.add(search, BorderLayout.NORTH);
        left.add(new JScrollPane(list), BorderLayout.CENTER);
        var buttons = new JPanel(new FlowLayout(FlowLayout.LEADING, 6, 4));
        buttons.setOpaque(false);
        buttons.add(use);
        buttons.add(remove);
        var details = new JPanel();
        details.setLayout(new BoxLayout(details, BoxLayout.Y_AXIS));
        details.setBorder(BorderFactory.createEmptyBorder(12, 14, 12, 14));
        sampleHolder.setOpaque(false);
        for (JComponent part : List.of(title, source, swatches, sampleHolder, buttons)) { part.setAlignmentX(0f); details.add(part); }
        details.add(Box.createVerticalGlue());
        var split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, left, new JScrollPane(details));
        split.setResizeWeight(0.35);
        split.setBorder(null);
        var panel = new JPanel(new BorderLayout());
        panel.setOpaque(false);
        panel.add(toolbar, BorderLayout.NORTH);
        panel.add(split, BorderLayout.CENTER);
        Font base = UIManager.getFont("Label.font");
        if (base != null) title.setFont(base.deriveFont(Font.BOLD, base.getSize2D() + 2f));
        source.setForeground(UIManager.getColor("Label.disabledForeground"));
        try {
            sample = new SampleTerminal(hooks.sampleOptions().get());
            sampleHolder.add(sample.component(), BorderLayout.CENTER);
        } catch (RuntimeException unavailable) {
            // Without a sample the colour strip alone previews the theme.
            sample = null;
        }
        body = new FillingPage(panel);
        draftListening = context.draft().onChanged(list::repaint);
        refresh(null);
        return body;
    }

    @Override public void selected() { component(); refresh(selectedName()); }
    @Override public boolean modified() { return false; }
    @Override public void reset() { }
    @Override public CompletableFuture<Void> apply() { return CompletableFuture.completedFuture(null); }
    @Override public void highlight(String query) { }

    @Override public void dispose() {
        if (draftListening != null) draftListening.close();
        if (sample != null) sample.close();
    }

    /** Re-reads the catalog, keeping the selection or selecting {@code prefer} when given. */
    private void refresh(String prefer) {
        all = hooks.catalog().latest().themes();
        String keep = prefer != null ? prefer : selectedName();
        show(keep);
    }

    void filter(String text) {
        String keep = selectedName();
        String query = text == null ? "" : text.strip().toLowerCase(Locale.ROOT);
        model.clear();
        for (TerminalTheme theme : all) if (query.isEmpty() || theme.name().toLowerCase(Locale.ROOT).contains(query)) model.addElement(theme);
        select(keep);
        if (list.getSelectedIndex() < 0 && !model.isEmpty()) list.setSelectedIndex(0);
        showDetails();
    }

    private void show(String keep) {
        filter(search.getText());
        if (keep != null) select(keep);
    }

    void select(String name) {
        if (name == null) return;
        for (int i = 0; i < model.size(); i++)
            if (model.get(i).name().equalsIgnoreCase(name)) { list.setSelectedIndex(i); list.ensureIndexIsVisible(i); return; }
    }

    private void showDetails() {
        TerminalTheme theme = list.getSelectedValue();
        use.setVisible(theme != null);
        remove.setVisible(theme != null && !theme.bundled());
        if (theme == null) {
            title.setText(model.isEmpty() ? "No terminal themes match" : "");
            source.setText("");
            swatches.setIcon(null);
            return;
        }
        title.setText(theme.name());
        source.setText(theme.bundled() ? "Bundled with Jasper (alacritty-theme)" : "Your file: " + theme.file().orElseThrow().getFileName());
        swatches.setIcon(new Strip(theme.palette(), true));
        swatches.setText("Cursor and selection colours at the end");
        if (sample != null) sample.palette(theme.palette());
    }

    private void use() {
        TerminalTheme theme = list.getSelectedValue();
        if (theme == null) return;
        context.draft().stage(CoreSettings.TERMINAL_COLORS, Optional.of(theme.name()));
        message("“" + theme.name() + "” is chosen. Apply or OK switches the terminals to it.", false);
        list.repaint();
    }

    private void installFromDisk() {
        context.chooseFile().apply("Install Terminal Theme", Optional.empty()).ifPresent(file -> install(file, false));
    }

    private void install(Path file, boolean replace) {
        String name;
        try { name = files.install(file, replace); }
        catch (TerminalThemeFiles.Conflict conflict) {
            if (hooks.confirm().test(body, conflict.existing().getFileName() + " is already installed. Replace it?")) install(file, true);
            return;
        } catch (TerminalThemeFiles.ThemeFileException refused) {
            message(refused.getMessage(), true);
            return;
        }
        message("Installed " + name + ".", false);
        rescanThen(name);
    }

    private void remove() {
        TerminalTheme theme = list.getSelectedValue();
        if (theme == null || theme.bundled()) return;
        Path file = theme.file().orElseThrow();
        if (!hooks.confirm().test(body, "Remove " + theme.name() + "? This deletes " + file.getFileName() + ".")) return;
        try { files.remove(file); }
        catch (TerminalThemeFiles.ThemeFileException refused) { message(refused.getMessage(), true); return; }
        message("Removed " + theme.name() + ".", false);
        rescanThen(null);
    }

    /** Rescans through the configuration reload, then shows the new list selecting {@code prefer}. */
    private void rescanThen(String prefer) {
        CompletableFuture<?> done;
        try { done = hooks.rescan().get(); }
        catch (RuntimeException failure) { done = CompletableFuture.failedFuture(failure); }
        done.whenComplete((ignored, failure) -> hooks.edt().execute(() -> refresh(prefer)));
    }

    private void message(String text, boolean error) {
        status.setText(text);
        status.setForeground(UIManager.getColor(error ? "Actions.Red" : "Label.foreground"));
    }

    // Test access.
    List<String> listed() { var names = new ArrayList<String>(); for (int i = 0; i < model.size(); i++) names.add(model.get(i).name()); return names; }
    String selectedName() { TerminalTheme theme = list.getSelectedValue(); return theme == null ? null : theme.name(); }
    JButton useButton() { return use; }
    JButton removeButton() { return remove; }
    JButton installButton() { return install; }
    String status() { return status.getText(); }
    SampleTerminal sample() { return sample; }

    /** The theme the draft's terminal colours name, or null. */
    String inUse() {
        TerminalColors colors = TerminalColors.parse(context.draft().value(CoreSettings.TERMINAL_COLORS));
        if (!colors.named()) return null;
        return all.stream().map(TerminalTheme::name).filter(name -> name.equalsIgnoreCase(colors.name())).findFirst().orElse(null);
    }

    /** Name in bold with a check when in use, the source, and the colour strip. */
    private final class Renderer extends JPanel implements ListCellRenderer<TerminalTheme> {
        private final JLabel name = new JLabel();
        private final JLabel line = new JLabel();

        Renderer() {
            super(new BorderLayout(10, 0));
            setBorder(BorderFactory.createEmptyBorder(6, 10, 6, 10));
            name.putClientProperty("html.disable", true);
            line.putClientProperty("html.disable", true);
            var text = new JPanel();
            text.setLayout(new BoxLayout(text, BoxLayout.Y_AXIS));
            text.setOpaque(false);
            text.add(name);
            text.add(line);
            add(text, BorderLayout.CENTER);
        }

        @Override public Component getListCellRendererComponent(JList<? extends TerminalTheme> owner, TerminalTheme value, int index,
                                                                boolean selected, boolean focused) {
            boolean current = value.name().equals(inUse());
            name.setText(current ? value.name() + "  ✓" : value.name());
            name.setFont(owner.getFont().deriveFont(Font.BOLD));
            line.setText(value.bundled() ? "Bundled" : "Your file");
            line.setIcon(new Strip(value.palette(), false));
            line.setHorizontalTextPosition(JLabel.LEFT);
            name.setForeground(selected ? owner.getSelectionForeground() : owner.getForeground());
            line.setForeground(selected ? owner.getSelectionForeground() : UIManager.getColor("Label.disabledForeground"));
            setBackground(selected ? owner.getSelectionBackground() : owner.getBackground());
            setOpaque(true);
            return this;
        }
    }

    /** The eight normal colours as squares on the theme's background; with the cursor and selection when asked. */
    private static final class Strip implements Icon {
        private static final int CELL = 12;
        private final Palette palette;
        private final boolean extras;

        Strip(Palette palette, boolean extras) { this.palette = palette; this.extras = extras; }

        @Override public int getIconWidth() { return (extras ? 10 : 8) * (CELL + 2) + 4; }
        @Override public int getIconHeight() { return CELL + 4; }

        @Override public void paintIcon(Component c, Graphics g, int x, int y) {
            g.setColor(palette.background());
            g.fillRect(x, y, getIconWidth(), getIconHeight());
            var colours = new ArrayList<>(palette.ansi().subList(0, 8));
            if (extras) { colours.add(palette.cursor()); colours.add(palette.selection()); }
            for (int i = 0; i < colours.size(); i++) {
                g.setColor(colours.get(i));
                g.fillRect(x + 2 + i * (CELL + 2), y + 2, CELL, CELL);
            }
            g.setColor(new Color(palette.foreground().getRed(), palette.foreground().getGreen(), palette.foreground().getBlue(), 80));
            g.drawRect(x, y, getIconWidth() - 1, getIconHeight() - 1);
        }
    }
}
```

(`Dimension` is imported for layout tweaks if needed; remove unused imports so the build stays warning-free.)

- [ ] **Step 5: Wire the page in `JasperApplication`**

Replace the `CorePages.pages(...)` call in the `SettingsDialog` construction with:

```java
                dev.jasper.app.settingsui.CorePages.pages(() -> themeCatalog.latest().names(), () -> terminalThemes.latest().names(),
                    system, shells, new dev.jasper.app.settingsui.CorePages.Pages(
                        context -> new dev.jasper.app.shortcuthelp.KeymapPage(keymap, context), context -> pluginManager().page(context),
                        context -> plugins.settingsPages(context), (context, header) -> themeStore().page(context, header),
                        context -> Optional.of(new dev.jasper.app.terminalthemes.AlacrittyThemesPage(context, alacrittyHooks())))),
```

and add:

```java
    /** What the Alacritty Themes page needs: the catalog, the reload that rescans it, and the sample terminal's options. */
    private dev.jasper.app.terminalthemes.AlacrittyThemesPage.Hooks alacrittyHooks() {
        var editor = new dev.jasper.app.platform.ConfigEditor();
        return new dev.jasper.app.terminalthemes.AlacrittyThemesPage.Hooks(terminalThemes, () -> configuration.reload(),
            folder -> CompletableFuture.runAsync(() -> editor.reveal(folder)), dev.jasper.app.terminalthemes.AlacrittyThemesPage::ask,
            () -> configuration.snapshot().viewOptions(configuration.snapshot().fontSize(), themes.current().palette()),
            SwingUtilities::invokeLater);
    }
```

(import `java.util.Optional` if the file does not already.)

- [ ] **Step 6: Run the tests**

Run: `./gradlew :jasper-app:test --tests 'dev.jasper.app.terminalthemes.*' --tests 'dev.jasper.app.settingsui.*' --tests 'dev.jasper.app.application.*' --tests 'dev.jasper.app.documentation.*'` and `./gradlew :jasper-app:javadoc`
Expected: PASS.

- [ ] **Step 7: Commit**

```bash
git branch --show-current
git add -A jasper-app/src
git commit -m "Alacritty Themes page with a live sample terminal

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 8: Documentation and the full gate

**Files:**
- Modify: `docs/themes.md`, `docs/configuration.md`, `docs/app-architecture.md`, `docs/STATUS.md`, this plan's status banner

- [ ] **Step 1: `docs/themes.md`**

Add a section after "Terminal colours":

```markdown
## Terminal themes

`terminal.colors` can also name a terminal theme in [Alacritty](https://alacritty.org)'s format. Jasper bundles the
whole [alacritty-theme](https://github.com/alacritty/alacritty-theme) collection (177 themes, Apache 2.0), and reads
`.toml` files you put in the `terminal-themes` folder beside `config.toml`; a file of yours wins over a bundled theme of
the same name. A theme's name comes from its file name: `gruvbox_dark.toml` is "Gruvbox Dark". `match`, `light` and
`dark` are Jasper's own choices and cannot be theme names.

Settings › Themes › **Alacritty Themes** lists every theme with its colours and draws a sample terminal in the selected
one; **Use** chooses it (Apply or OK switches the terminals), **Install from Disk…** checks and copies a theme file in,
**Reveal Folder** shows the folder, and **Remove…** deletes one of your files. Settings › Terminal › Font & Colors lists
the themes too, ending in **More Terminal Themes…**.

Jasper reads a theme's `colors.primary` foreground and background (required), `colors.normal` and `colors.bright` (the
16 ANSI colours), `colors.cursor.cursor` and `colors.selection.background`. A missing bright colour uses the normal one;
a missing cursor uses the foreground and a missing selection a blend of foreground and background. Other keys (dim
colours, search, hints, indexed colours) are ignored. An unknown name in `terminal.colors` is a warning and the terminal
matches the UI theme.
```

- [ ] **Step 2: `docs/configuration.md` and `docs/app-architecture.md`**

In the Settings dialog section, change "Themes (IntelliJ Themes)" to "Themes (IntelliJ Themes, Alacritty Themes)". In the "Terminal colors" section, add this sentence:

```markdown
`terminal.colors` may also name a terminal theme; see [Themes](themes.md#terminal-themes).
```

In `docs/app-architecture.md`'s Package ownership table, after `themestore`:

```markdown
| `terminalthemes` | Terminal colour themes in Alacritty's format: the bundled alacritty-theme collection (resources with `index.txt`), the user's `terminal-themes` folder, the parser, file installs and Settings › Themes › Alacritty Themes with its sample terminal. Application owns the TerminalThemeCatalog (scanned with each configuration read); SettingsDialog owns each page, which closes its sample session on disposal. |
```

and change the `settingsui` row's "the application supplies the Keymap, Plugins and IntelliJ Themes pages" to "the application supplies the Keymap, Plugins, IntelliJ Themes and Alacritty Themes pages (`CorePages.Pages`)".

- [ ] **Step 3: Run the full gate**

Run: `./gradlew check` (long). Expected: BUILD SUCCESSFUL. Read the counts from each module's `build/test-results/test/*.xml`, and run the AGENTS.md source-hygiene check over `jasper-app/src` (prints nothing).

- [ ] **Step 4: Status**

Add a `docs/STATUS.md` entry at the top of "Current state":

```markdown
- **Alacritty terminal themes** (branch `claude/alacritty-themes`, spec
  `docs/superpowers/specs/2026-09-30-alacritty-terminal-themes-design.md`, plan
  `docs/superpowers/plans/2026-09-30-alacritty-terminal-themes.md`): `terminal.colors` accepts a terminal theme's name;
  Jasper bundles the alacritty-theme collection (177 themes, commit ab88d5a8) and reads `.toml` files in
  `terminal-themes/`; Settings › Themes › Alacritty Themes lists them with a colour strip and a sample terminal drawn by
  the real renderer, with Use, Install from Disk…, Reveal Folder and Remove…; Font & Colors' dropdown lists them and ends
  in More Terminal Themes…. New package `terminalthemes`. `./gradlew check` green: <counts>. Not merged. GUI checks
  pending with the user: the dropdown and More Terminal Themes…; the sample in the configured font; Use then Apply
  recolours open terminals; install a downloaded theme and remove it; a theme without cursor or selection colours;
  switching back to Match the theme.
```

Update this plan's status banner to "executed … ; not merged" with any deviations.

- [ ] **Step 5: Commit**

```bash
git branch --show-current
git add -A docs
git commit -m "Document Alacritty terminal themes

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```
