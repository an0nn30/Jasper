# Theme System Plan 1: Format and Generator Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Status:** Executed 2026-10-01 (Tasks 1–9 on claude/theme-system); ./gradlew check green.

**Goal:** Jasper themes become TOML files generated into FlatLaf's key set: the format, `extends`, the generator with
contrast floors, native Jasper Dark and a new Jasper Light, IntelliJ Light and Darcula converted, installed IntelliJ
plugins converted in memory, and user theme files in the `themes` folder.

**Architecture:** Everything lives in `dev.jasper.app.appearance`. `ThemeToml` parses a file into a `ThemeDocument`
(what the file says); `ThemeResolver` merges its `extends` chain; `ThemeGenerator` fills every seed, token and syntax
role and lays `UiTemplate` (tokens → FlatLaf/IntelliJ and `Jasper.*` keys) under the theme's own `[ui]` keys. The
result, a `GeneratedTheme`, is installed through the existing IntelliJ-JSON path (`ThemeLoader` JSON → FlatLaf's
`IntelliJTheme` → put back skipped keys → `ChromeKeys`). Converted IntelliJ themes are marked `ui_complete`: their own
keys install as today and only tokens they set explicitly reach `Jasper.*`, so their look does not change.

**Tech Stack:** Java 25 (JetBrains Runtime), FlatLaf 3.7 (`IntelliJTheme`), tomlj 1.1.1 (already an app dependency),
JUnit 5 + AssertJ, `EdtTestExtension`.

**Spec:** `docs/superpowers/specs/2026-10-01-jasper-theme-system-design.md` (sections 1–4 and 8, plan 1).

### Spike (run 2026-10-01 while writing this plan, throwaway test deleted)

Through `ThemeLoader` → `ThemeManager.install`:
- **A.** A dark theme whose `ui` sets only `@background` / `@foreground` re-derives 385 colour keys (for example
  `Button.background`, `Button.disabledSelectedBackground`, `CheckBox.icon.background`); `Panel.background` equals
  `@background`. So FlatLaf 3.7 re-derives controls from base variables set in an IntelliJ theme's `ui`.
- **B.** Adding `@background #21252a`, `@foreground #d5dee5`, `@selectionBackground #5e7293`,
  `@selectionForeground #ffffff` to today's `jasper-dark.theme.json` changes **none** of the colour keys (0 of every
  installed colour key, 0 of `ThemeManagerTest`'s `TODAYS_DARK`).

Consequence: `UiTemplate` sets those four base variables plus Jasper Dark's existing `@` variables. Task 4 keeps
spike A as a regression test.

### Deviations from the spec (record again in `docs/STATUS.md`, Task 9)

1. **Token list refined** (spec 2.1 said "about 40"; the final list is 48, in Task 1's `Token`): added
   `text_selection`, `text_selection_text`, `tab_active_border`, `button_border`, `button_default`, `scrollbar_track`,
   `accent`, `error_background`; dropped `tab_text`, `input_border`, `info`, which nothing would read.
2. **New top-level key `ui_complete`** (boolean, default false, inherited through `extends`): the theme's `[ui]` is a
   complete IntelliJ key set, so the generator skips `UiTemplate`'s FlatLaf section and applies only the tokens the
   theme sets explicitly to `Jasper.*` keys. Converted IntelliJ themes set it; this is what keeps their look
   unchanged.
3. **Contrast floors** (spec section 3): text 4.5:1, muted text 3:1, disabled text 2:1, link 4.5:1, divider 3:1 against
   the terminal background, status colours 4.5:1 against the status bar; **no floor for `border` / `separator`**
   (a 3:1 border would be far heavier than Jasper Dark's own 1.4:1).
4. **Jasper Light's terminal palette is today's `Palette.jasperLight()`** (One Light), not a newly tuned ANSI set,
   so `terminal.colors = "light"` keeps its colours.
5. **The IntelliJ converter lives in `dev.jasper.app.appearance` (`IntellijConversion`) in this plan** and converts
   only seeds and `[ui]` (plus IntelliJ's `icons` table, carried internally in `ThemeDocument.icons`). Plan 4 moves it
   to `dev.jasper.app.themeimport` and adds tokens and syntax.
6. **`Theme.LIGHT` is renamed `Theme.INTELLIJ_LIGHT`**, and Jasper Light is `Theme.JASPER_LIGHT`, so nothing reads an
   ambiguous "LIGHT". IntelliJ Light's terminal palette now comes from its own editor scheme (spec 6.1), not from
   `Palette.jasperLight()`.

### Notes for later plans

- Plan 2 needs workspace tokens for `ui_complete` themes: read them from the theme's derived FlatLaf defaults (or
  extend the converter), because their generated tokens are seed-derived placeholders.
- Plan 4 must decide how converted TOML files carry IntelliJ's `icons` table and keep `[ui]` wildcard order (tomlj
  keeps table order; `ThemeDocument.ui` keeps it too).

## Global Constraints

- Java 25 on the JetBrains Runtime 25; always `./gradlew` from the worktree root, never a system `gradle`.
- Work in `.worktrees/theme-system` on branch `claude/theme-system`; run `git branch --show-current` before every
  commit (other sessions switch branches in the main checkout).
- Every commit message ends with `Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>`.
- Never launch the GUI (`:jasper-app:run`, `:jasper-app:bench`); visual checks are the user's.
- Swing state and `ThemeManager.install` are EDT-only; `ThemeToml`, `ThemeResolver`, `ThemeGenerator`,
  `IntellijConversion` and `ThemeCatalog.scan` touch no Swing state and may run on any thread.
- Source hygiene: no raw control, private-use or unpaired surrogate characters in sources; ASCII in new files.
- The app package graph stays acyclic (`verifyApplicationArchitecture`); all new types are in
  `dev.jasper.app.appearance`, whose allowed outgoing Jasper dependencies stay `dev.jasper.app.config`,
  `dev.jasper.app.lifecycle`, `dev.jasper.terminal.config`.
- Theme colours in files are `#RRGGBB` or `#RRGGBBAA`; names match ignoring case and surrounding whitespace.
- `./gradlew check` must be green at the end of every task.

## File map

| File | Responsibility |
|---|---|
| `appearance/Seed.java` (new) | The five `[colors]` seeds |
| `appearance/Token.java` (new) | The 48 semantic tokens |
| `appearance/SyntaxRole.java`, `SyntaxStyle.java` (new) | Syntax roles and their style |
| `appearance/ThemeDocument.java` (new) | A theme as written, and `extends` merging |
| `appearance/ThemeFileException.java` (new) | A theme-file problem with its line |
| `appearance/ThemeToml.java` (new) | TOML → `ThemeDocument`, with every error check |
| `appearance/ThemeResolver.java` (new) | `extends` chains, cycles, required seeds |
| `appearance/GeneratedTheme.java`, `ThemeGenerator.java`, `UiTemplate.java` (new) | Seeds → tokens, syntax, palette, UI keys |
| `appearance/IntellijConversion.java`, `BuiltInThemes.java` (new) | IntelliJ → document; the bundled themes |
| `appearance/ThemeLoader.java`, `ThemeManager.java`, `Theme.java`, `ThemeCatalog.java`, `ChromeKeys.java` (modify) | Install path, sources, catalog |
| `resources/dev/jasper/app/themes/jasper-dark.toml`, `jasper-light.toml` (new); `jasper-dark.theme.json` (delete) | Built-in themes |
| `application/ConfigurationController.java` (modify) | Theme-file problems carry their line |

All Java paths are under `jasper-app/src/main/java/dev/jasper/app/` (tests under `jasper-app/src/test/java/dev/jasper/app/`).

---

### Task 1: Theme vocabulary, document and TOML parser

**Files:**
- Create: `jasper-app/src/main/java/dev/jasper/app/appearance/Seed.java`, `Token.java`, `SyntaxRole.java`, `SyntaxStyle.java`, `ThemeDocument.java`, `ThemeFileException.java`, `ThemeToml.java`
- Test: `jasper-app/src/test/java/dev/jasper/app/appearance/ThemeTomlTest.java`

**Interfaces:**
- Produces: `enum Seed { BACKGROUND, FOREGROUND, ACCENT, CURSOR, SELECTION; String key(); static Optional<Seed> of(String) }`;
  `enum Token { …48…; String key(); static Optional<Token> of(String) }`; `enum SyntaxRole { …16…; String key(); static Optional<SyntaxRole> of(String) }`;
  `record SyntaxStyle(Color color, boolean bold, boolean italic)`;
  `record ThemeDocument(String origin, String name, Optional<Boolean> dark, Optional<String> parent, int parentLine, Optional<Boolean> uiComplete, Map<String,String> metadata, Map<Seed,Color> seeds, Map<Integer,Color> ansi, Map<Token,Color> tokens, Map<SyntaxRole,SyntaxStyle> syntax, Map<String,Object> ui, Map<String,Object> icons)` with `static final List<String> ANSI_NAMES` and `ThemeDocument over(ThemeDocument base)`;
  `final class ThemeFileException extends IllegalArgumentException { String origin(); int line(); }`;
  `static ThemeDocument ThemeToml.parse(String text, String origin)`; `static Color ThemeToml.color(String hex)`.

- [ ] **Step 1: Write the failing test**

```java
package dev.jasper.app.appearance;

import java.awt.Color;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class ThemeTomlTest {
    private static final String FULL = """
        name = "  Nord  "
        dark = true
        extends = "Jasper Dark"
        author = "Arctic Ice Studio"
        license = "MIT"
        ui_complete = false

        [colors]
        background = "#2e3440"
        foreground = "#D8DEE9"
        accent = "#88c0d0"
        selection = "#434c5e80"
        [colors.normal]
        black = "#3b4252"
        red = "#bf616a"
        [colors.bright]
        white = "#eceff4"

        [tokens]
        tab_active = "#3b4252"

        [syntax]
        keyword = "#81a1c1"
        comment = { color = "#616e88", italic = true }

        [ui]
        "*.background" = "#2e3440"
        Button.arc = 6
        "Component.innerFocusWidth" = 0.5
        "Button.default.boldText" = true
        "Button.border" = "com.formdev.flatlaf.ui.FlatButtonBorder"
        "Tree.rowHeight" = { "os.mac" = 22, "os.default" = 20 }
        """;

    @Test void aFullThemeFileReadsEverySection() {
        ThemeDocument theme = ThemeToml.parse(FULL, "nord.toml");
        assertThat(theme.origin()).isEqualTo("nord.toml");
        assertThat(theme.name()).isEqualTo("Nord");
        assertThat(theme.dark()).contains(true);
        assertThat(theme.parent()).contains("Jasper Dark");
        assertThat(theme.parentLine()).isEqualTo(3);
        assertThat(theme.uiComplete()).contains(false);
        assertThat(theme.metadata()).containsExactly(Map.entry("author", "Arctic Ice Studio"), Map.entry("license", "MIT"));
        assertThat(theme.seeds()).containsOnly(Map.entry(Seed.BACKGROUND, new Color(0x2e3440)),
            Map.entry(Seed.FOREGROUND, new Color(0xd8dee9)), Map.entry(Seed.ACCENT, new Color(0x88c0d0)),
            Map.entry(Seed.SELECTION, new Color(0x43, 0x4c, 0x5e, 0x80)));
        assertThat(theme.ansi()).containsOnly(Map.entry(0, new Color(0x3b4252)), Map.entry(1, new Color(0xbf616a)),
            Map.entry(15, new Color(0xeceff4)));
        assertThat(theme.tokens()).containsOnly(Map.entry(Token.TAB_ACTIVE, new Color(0x3b4252)));
        assertThat(theme.syntax()).containsOnly(
            Map.entry(SyntaxRole.KEYWORD, new SyntaxStyle(new Color(0x81a1c1), false, false)),
            Map.entry(SyntaxRole.COMMENT, new SyntaxStyle(new Color(0x616e88), false, true)));
        assertThat(List.copyOf(theme.ui().keySet())).containsExactly("*.background", "Button.arc",
            "Component.innerFocusWidth", "Button.default.boldText", "Button.border", "Tree.rowHeight");
        assertThat(theme.ui().get("Button.arc")).isEqualTo(6L);
        assertThat(theme.ui().get("Component.innerFocusWidth")).isEqualTo(0.5);
        assertThat(theme.ui().get("Button.default.boldText")).isEqualTo(true);
        assertThat(theme.ui().get("Tree.rowHeight")).isEqualTo(Map.of("os.mac", 22L, "os.default", 20L));
        assertThat(theme.icons()).isEmpty();
    }

    @Test void aMinimalThemeHasEmptySectionsAndNoOptionalValues() {
        ThemeDocument theme = ThemeToml.parse("name = \"Bare\"", "bare.toml");
        assertThat(theme.dark()).isEmpty();
        assertThat(theme.parent()).isEmpty();
        assertThat(theme.parentLine()).isZero();
        assertThat(theme.uiComplete()).isEmpty();
        assertThat(theme.seeds()).isEmpty();
        assertThat(theme.ui()).isEmpty();
    }

    @Test void everyProblemNamesTheFileAndLine() {
        assertProblem("name = \"x\"\n[colors\n", 2, "");
        assertProblem("name = \"x\"\nflavour = \"mocha\"\n", 2, "unknown key \"flavour\"");
        assertProblem("dark = true\n", 0, "needs a \"name\"");
        assertProblem("name = \"  \"\n", 1, "must not be blank");
        assertProblem("name = \"x\"\nextends = \"\"\n", 2, "must not be blank");
        assertProblem("name = 3\n", 1, "\"name\" must be a string");
        assertProblem("name = \"x\"\ndark = \"yes\"\n", 2, "\"dark\" must be true or false");
        assertProblem("name = \"x\"\n[colors]\nbackgound = \"#000000\"\n", 3, "unknown colour \"backgound\"");
        assertProblem("name = \"x\"\n[colors]\nbackground = \"#00000\"\n", 3, "must be a colour like #1e1e2e");
        assertProblem("name = \"x\"\n[colors]\nbackground = \"000000\"\n", 3, "must be a colour like #1e1e2e");
        assertProblem("name = \"x\"\n[colors.normal]\npurple = \"#000000\"\n", 3, "unknown ANSI colour \"purple\"");
        assertProblem("name = \"x\"\n[tokens]\ntab = \"#000000\"\n", 3, "unknown token \"tab\"");
        assertProblem("name = \"x\"\n[syntax]\nmacro = \"#000000\"\n", 3, "unknown syntax role \"macro\"");
        assertProblem("name = \"x\"\n[syntax]\nkeyword = { colour = \"#000000\" }\n", 3, "unknown field \"colour\"");
        assertProblem("name = \"x\"\n[syntax]\nkeyword = { bold = true }\n", 3, "needs a \"color\"");
        assertProblem("name = \"x\"\n[syntax]\nkeyword = 3\n", 3, "must be a colour or { color, bold, italic }");
        assertProblem("name = \"x\"\n[ui]\n\"ButtonUI\" = \"x\"\n", 3, "Swing UI delegate");
        assertProblem("name = \"x\"\n[ui]\n\"Button.border\" = \"com.example.EvilBorder\"\n", 3, "names a Java class");
        assertProblem("name = \"x\"\n[ui]\n\"Button.margin\" = [1, 2]\n", 3, "must be a string, number or boolean");
        assertProblem("name = \"x\"\ncolors = \"#000000\"\n", 2, "\"colors\" must be a table");
    }

    @Test void colourTextParsesWithAndWithoutAlpha() {
        assertThat(ThemeToml.color("#0A0b0C")).isEqualTo(new Color(0x0a0b0c));
        assertThat(ThemeToml.color("#0a0b0c7f")).isEqualTo(new Color(0x0a, 0x0b, 0x0c, 0x7f));
    }

    private static void assertProblem(String text, int line, String message) {
        assertThatThrownBy(() -> ThemeToml.parse(text, "bad.toml"))
            .isInstanceOfSatisfying(ThemeFileException.class, failure -> {
                assertThat(failure.origin()).isEqualTo("bad.toml");
                assertThat(failure.line()).as(text).isEqualTo(line);
                assertThat(failure.getMessage()).as(text).startsWith(line > 0 ? "bad.toml:" + line + ": " : "bad.toml: ").contains(message);
            });
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :jasper-app:test --tests 'dev.jasper.app.appearance.ThemeTomlTest'`
Expected: FAIL to compile: `ThemeToml`, `ThemeDocument`, `Seed`, `Token`, `SyntaxRole`, `SyntaxStyle`, `ThemeFileException` not found.

- [ ] **Step 3: Write the vocabulary types**

`Seed.java`:

```java
package dev.jasper.app.appearance;

import java.util.Locale;
import java.util.Optional;

/** The {@code [colors]} seeds of a Jasper theme; the eight ANSI colours are separate ({@link ThemeDocument#ansi()}). */
public enum Seed {
    BACKGROUND, FOREGROUND, ACCENT, CURSOR, SELECTION;

    /** The TOML key, for example {@code background}. */
    public String key() { return name().toLowerCase(Locale.ROOT); }

    /** The seed with this TOML key. */
    public static Optional<Seed> of(String key) {
        for (Seed seed : values()) if (seed.key().equals(key)) return Optional.of(seed);
        return Optional.empty();
    }
}
```

`Token.java`:

```java
package dev.jasper.app.appearance;

import java.util.Locale;
import java.util.Optional;

/**
 * Jasper's semantic colours (a theme's {@code [tokens]}). Every token is derivable from the seeds
 * ({@link ThemeGenerator}); {@link UiTemplate} maps tokens onto FlatLaf, IntelliJ and {@code Jasper.*} keys.
 */
public enum Token {
    SURFACE, SURFACE_RAISED, SURFACE_SUNKEN, TITLE_BAR, STATUS_BAR, SIDEBAR,
    TEXT, TEXT_MUTED, TEXT_DISABLED, TEXT_ON_ACCENT, LINK,
    BORDER, SEPARATOR, DIVIDER, FOCUS_RING,
    HOVER, PRESSED, SELECTION, SELECTION_TEXT, SELECTION_INACTIVE, TEXT_SELECTION, TEXT_SELECTION_TEXT,
    TAB_BAR, TAB_ACTIVE, TAB_ACTIVE_TEXT, TAB_ACTIVE_BORDER, TAB_HOVER, TAB_UNDERLINE, TAB_UNDERLINE_INACTIVE,
    INPUT_BACKGROUND, BUTTON_BACKGROUND, BUTTON_BORDER, BUTTON_TEXT, BUTTON_DEFAULT, SCROLLBAR_THUMB, SCROLLBAR_TRACK,
    ACCENT, SUCCESS, WARNING, ERROR, ERROR_BACKGROUND,
    EDITOR_BACKGROUND, EDITOR_FOREGROUND, EDITOR_CARET, EDITOR_SELECTION, EDITOR_CURRENT_LINE, EDITOR_GUTTER,
    EDITOR_LINE_NUMBERS;

    /** The TOML key, for example {@code tab_active}. */
    public String key() { return name().toLowerCase(Locale.ROOT); }

    /** The token with this TOML key. */
    public static Optional<Token> of(String key) {
        for (Token token : values()) if (token.key().equals(key)) return Optional.of(token);
        return Optional.empty();
    }
}
```

`SyntaxRole.java`:

```java
package dev.jasper.app.appearance;

import java.util.Locale;
import java.util.Optional;

/** The syntax roles a theme colours (a theme's {@code [syntax]}); the editor maps them onto its token types. */
public enum SyntaxRole {
    KEYWORD, TYPE, FUNCTION, VARIABLE, CONSTANT, NUMBER, STRING, COMMENT, DOC_COMMENT, ANNOTATION, OPERATOR,
    PUNCTUATION, TAG, ATTRIBUTE, REGEX, ERROR;

    /** The TOML key, for example {@code doc_comment}. */
    public String key() { return name().toLowerCase(Locale.ROOT); }

    /** The role with this TOML key. */
    public static Optional<SyntaxRole> of(String key) {
        for (SyntaxRole role : values()) if (role.key().equals(key)) return Optional.of(role);
        return Optional.empty();
    }
}
```

`SyntaxStyle.java`:

```java
package dev.jasper.app.appearance;

import java.awt.Color;
import java.util.Objects;

/** How one syntax role is drawn. */
public record SyntaxStyle(Color color, boolean bold, boolean italic) {
    public SyntaxStyle { Objects.requireNonNull(color); }
}
```

`ThemeFileException.java`:

```java
package dev.jasper.app.appearance;

/** A theme file Jasper cannot use. The message starts with the origin and, when known, the 1-based line. */
public final class ThemeFileException extends IllegalArgumentException {
    private final String origin;
    private final int line;

    ThemeFileException(String origin, int line, String message) {
        super(origin + (line > 0 ? ":" + line : "") + ": " + message);
        this.origin = origin;
        this.line = line;
    }

    /** The file name or resource the problem is in. */
    public String origin() { return origin; }

    /** The 1-based line, or 0 when unknown. */
    public int line() { return line; }
}
```

`ThemeDocument.java`:

```java
package dev.jasper.app.appearance;

import java.awt.Color;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * One Jasper theme as written: only what its file sets, nothing derived. {@code ansi} maps 0-7 to
 * {@code [colors.normal]} and 8-15 to {@code [colors.bright]}. {@code ui} keeps the file's order, which matters for
 * IntelliJ's {@code *.} wildcard keys. {@code icons} is IntelliJ's icon table, which only converted IntelliJ themes
 * carry. {@code parentLine} is the line of {@code extends}, 0 without one.
 */
public record ThemeDocument(String origin, String name, Optional<Boolean> dark, Optional<String> parent, int parentLine,
                            Optional<Boolean> uiComplete, Map<String, String> metadata, Map<Seed, Color> seeds,
                            Map<Integer, Color> ansi, Map<Token, Color> tokens, Map<SyntaxRole, SyntaxStyle> syntax,
                            Map<String, Object> ui, Map<String, Object> icons) {
    /** The TOML names of the eight ANSI colours, in index order. */
    public static final List<String> ANSI_NAMES = List.of("black", "red", "green", "yellow", "blue", "magenta", "cyan", "white");

    public ThemeDocument {
        Objects.requireNonNull(origin); Objects.requireNonNull(name); Objects.requireNonNull(dark);
        Objects.requireNonNull(parent); Objects.requireNonNull(uiComplete);
        metadata = ordered(metadata);
        seeds = Map.copyOf(seeds);
        ansi = Map.copyOf(ansi);
        tokens = Map.copyOf(tokens);
        syntax = Map.copyOf(syntax);
        ui = ordered(ui);
        icons = ordered(icons);
    }

    /**
     * This document laid over {@code base}: every section merges key by key with this document's values winning,
     * and a {@code [ui]} key this document sets replaces the base's and moves after the base's keys. The result
     * keeps this document's origin, name and metadata, and extends whatever {@code base} extends.
     */
    ThemeDocument over(ThemeDocument base) {
        var layered = new LinkedHashMap<>(base.ui);
        ui.forEach((key, value) -> { layered.remove(key); layered.put(key, value); });
        return new ThemeDocument(origin, name, dark.or(base::dark), base.parent, base.parentLine,
            uiComplete.or(base::uiComplete), metadata, merged(base.seeds, seeds), merged(base.ansi, ansi),
            merged(base.tokens, tokens), merged(base.syntax, syntax), layered, icons.isEmpty() ? base.icons : icons);
    }

    private static <K, V> Map<K, V> merged(Map<K, V> base, Map<K, V> top) {
        var result = new HashMap<>(base);
        result.putAll(top);
        return result;
    }

    private static <V> Map<String, V> ordered(Map<String, V> map) {
        return Collections.unmodifiableMap(new LinkedHashMap<>(Objects.requireNonNull(map)));
    }
}
```

- [ ] **Step 4: Write the parser**

`ThemeToml.java`:

```java
package dev.jasper.app.appearance;

import java.awt.Color;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;
import org.tomlj.Toml;
import org.tomlj.TomlParseResult;
import org.tomlj.TomlPosition;
import org.tomlj.TomlTable;

/** Reads a Jasper theme file. Every problem is a {@link ThemeFileException} naming the file and line. */
final class ThemeToml {
    private static final Set<String> TOP = Set.of("name", "dark", "extends", "author", "license", "source", "commit",
        "ui_complete", "colors", "tokens", "syntax", "ui");
    private static final List<String> METADATA = List.of("author", "license", "source", "commit");
    private static final Set<String> STYLE_FIELDS = Set.of("color", "bold", "italic");
    private static final Pattern COLOR = Pattern.compile("#[0-9a-fA-F]{6}(?:[0-9a-fA-F]{2})?");
    /** A full Java class name: FlatLaf or Swing would load it. FlatLaf's own classes are allowed. */
    private static final Pattern CLASS_NAME = Pattern.compile("(?:[a-z_$][\\w$]*\\.)+[A-Z][\\w$]*");

    private ThemeToml() {}

    /** {@code #RRGGBB} or {@code #RRGGBBAA}, already checked against {@link #COLOR}. */
    static Color color(String text) {
        String hex = text.substring(1);
        int rgb = Integer.parseInt(hex.substring(0, 6), 16);
        int alpha = hex.length() == 8 ? Integer.parseInt(hex.substring(6), 16) : 255;
        return new Color((rgb >> 16) & 0xff, (rgb >> 8) & 0xff, rgb & 0xff, alpha);
    }

    static ThemeDocument parse(String text, String origin) {
        TomlParseResult toml = Toml.parse(text);
        if (toml.hasErrors()) {
            var error = toml.errors().getFirst();
            throw new ThemeFileException(origin, error.position().line(), error.getMessage());
        }
        var file = new Reader(toml, origin);
        for (String key : toml.keySet()) if (!TOP.contains(key)) throw file.error(List.of(key), "unknown key \"" + key + "\"");
        String name = file.string(List.of("name")).orElseThrow(() -> new ThemeFileException(origin, 0, "a theme needs a \"name\""));
        if (name.isBlank()) throw file.error(List.of("name"), "\"name\" must not be blank");
        Optional<String> parent = file.string(List.of("extends"));
        if (parent.isPresent() && parent.get().isBlank()) throw file.error(List.of("extends"), "\"extends\" must not be blank");
        var metadata = new LinkedHashMap<String, String>();
        for (String key : METADATA) file.string(List.of(key)).ifPresent(value -> metadata.put(key, value));
        var seeds = new EnumMap<Seed, Color>(Seed.class);
        var ansi = new HashMap<Integer, Color>();
        TomlTable colors = file.table(List.of("colors"));
        if (colors != null) for (String key : colors.keySet()) {
            List<String> path = List.of("colors", key);
            if (key.equals("normal") || key.equals("bright")) {
                int offset = key.equals("normal") ? 0 : 8;
                for (String colour : file.table(path).keySet()) {
                    int index = ThemeDocument.ANSI_NAMES.indexOf(colour);
                    List<String> at = List.of("colors", key, colour);
                    if (index < 0) throw file.error(at, "unknown ANSI colour \"" + colour + "\"");
                    ansi.put(offset + index, file.color(at));
                }
            } else {
                Seed seed = Seed.of(key).orElseThrow(() -> file.error(path, "unknown colour \"" + key + "\" in [colors]"));
                seeds.put(seed, file.color(path));
            }
        }
        var tokens = new EnumMap<Token, Color>(Token.class);
        TomlTable tokenTable = file.table(List.of("tokens"));
        if (tokenTable != null) for (String key : tokenTable.keySet()) {
            List<String> path = List.of("tokens", key);
            tokens.put(Token.of(key).orElseThrow(() -> file.error(path, "unknown token \"" + key + "\"")), file.color(path));
        }
        var syntax = new EnumMap<SyntaxRole, SyntaxStyle>(SyntaxRole.class);
        TomlTable syntaxTable = file.table(List.of("syntax"));
        if (syntaxTable != null) for (String key : syntaxTable.keySet()) {
            List<String> path = List.of("syntax", key);
            SyntaxRole role = SyntaxRole.of(key).orElseThrow(() -> file.error(path, "unknown syntax role \"" + key + "\""));
            syntax.put(role, file.style(path));
        }
        var ui = new LinkedHashMap<String, Object>();
        TomlTable uiTable = file.table(List.of("ui"));
        if (uiTable != null) file.flatten(List.of("ui"), "", uiTable, ui);
        return new ThemeDocument(origin, name.strip(), file.bool(List.of("dark")), parent.map(String::strip),
            parent.isPresent() ? file.line(List.of("extends")) : 0, file.bool(List.of("ui_complete")), metadata, seeds,
            ansi, tokens, syntax, ui, Map.of());
    }

    /** Typed reads from one parsed file, each failure naming the key's line. */
    private record Reader(TomlParseResult toml, String origin) {
        int line(List<String> path) {
            TomlPosition position = toml.inputPositionOf(path);
            return position == null ? 0 : position.line();
        }

        ThemeFileException error(List<String> path, String message) { return new ThemeFileException(origin, line(path), message); }

        private static String label(List<String> path) { return path.getLast(); }

        TomlTable table(List<String> path) {
            Object value = toml.get(path);
            if (value == null) return null;
            if (!(value instanceof TomlTable table)) throw error(path, "\"" + label(path) + "\" must be a table");
            return table;
        }

        Optional<String> string(List<String> path) {
            Object value = toml.get(path);
            if (value == null) return Optional.empty();
            if (!(value instanceof String text)) throw error(path, "\"" + label(path) + "\" must be a string");
            return Optional.of(text);
        }

        Optional<Boolean> bool(List<String> path) {
            Object value = toml.get(path);
            if (value == null) return Optional.empty();
            if (!(value instanceof Boolean flag)) throw error(path, "\"" + label(path) + "\" must be true or false");
            return Optional.of(flag);
        }

        Color color(List<String> path) {
            Object value = toml.get(path);
            if (!(value instanceof String text) || !COLOR.matcher(text).matches())
                throw error(path, "\"" + label(path) + "\" must be a colour like #1e1e2e or #1e1e2e80");
            return ThemeToml.color(text);
        }

        SyntaxStyle style(List<String> path) {
            Object value = toml.get(path);
            if (value instanceof String) return new SyntaxStyle(color(path), false, false);
            if (!(value instanceof TomlTable inline)) throw error(path, "\"" + label(path) + "\" must be a colour or { color, bold, italic }");
            for (String field : inline.keySet())
                if (!STYLE_FIELDS.contains(field)) throw error(path, "unknown field \"" + field + "\" in \"" + label(path) + "\"");
            if (!inline.contains(List.of("color"))) throw error(path, "\"" + label(path) + "\" needs a \"color\"");
            return new SyntaxStyle(color(with(path, "color")), bool(with(path, "bold")).orElse(false),
                bool(with(path, "italic")).orElse(false));
        }

        /** Nested tables become dotted keys; a table whose keys all start with {@code os.} stays one per-OS value. */
        void flatten(List<String> path, String prefix, TomlTable table, Map<String, Object> out) {
            for (String key : table.keySet()) {
                List<String> at = with(path, key);
                String name = prefix.isEmpty() ? key : prefix + "." + key;
                Object value = table.get(List.of(key));
                if (value instanceof TomlTable nested && !nested.isEmpty()
                        && nested.keySet().stream().allMatch(inner -> inner.startsWith("os."))) {
                    var perOs = new LinkedHashMap<String, Object>();
                    for (String os : nested.keySet()) perOs.put(os, scalar(with(at, os), name, nested.get(List.of(os))));
                    put(at, name, perOs, out);
                } else if (value instanceof TomlTable nested) {
                    flatten(at, name, nested, out);
                } else {
                    put(at, name, scalar(at, name, value), out);
                }
            }
        }

        private Object scalar(List<String> at, String name, Object value) {
            if (value instanceof String text) {
                String stripped = text.strip();
                if (CLASS_NAME.matcher(stripped).matches() && !stripped.startsWith("com.formdev.flatlaf."))
                    throw error(at, "\"" + name + "\" names a Java class; themes cannot load classes");
                return text;
            }
            if (value instanceof Long || value instanceof Double || value instanceof Boolean) return value;
            throw error(at, "\"" + name + "\" must be a string, number or boolean");
        }

        private void put(List<String> at, String name, Object value, Map<String, Object> out) {
            if (name.endsWith("UI")) throw error(at, "\"" + name + "\" is a Swing UI delegate; themes cannot set it");
            out.remove(name);
            out.put(name, value);
        }

        private static List<String> with(List<String> path, String key) {
            var result = new ArrayList<>(path);
            result.add(key);
            return List.copyOf(result);
        }
    }
}
```

(`TomlTable.contains(List<String>)` and `isEmpty()` exist in tomlj 1.1.1; checked with `javap`.)

- [ ] **Step 5: Run test to verify it passes**

Run: `./gradlew :jasper-app:test --tests 'dev.jasper.app.appearance.ThemeTomlTest'`
Expected: PASS (4 tests). If a parse-error line differs (tomlj reports `[colors` on line 2), adjust only the
expectation in `everyProblemNamesTheFileAndLine`'s first case to tomlj's line, never the parser.

- [ ] **Step 6: Commit**

```bash
git branch --show-current   # must print claude/theme-system
git add jasper-app/src/main/java/dev/jasper/app/appearance/{Seed,Token,SyntaxRole,SyntaxStyle,ThemeDocument,ThemeFileException,ThemeToml}.java jasper-app/src/test/java/dev/jasper/app/appearance/ThemeTomlTest.java
git commit -m "Theme system: TOML theme format and parser

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 2: `extends` resolution

**Files:**
- Create: `jasper-app/src/main/java/dev/jasper/app/appearance/ThemeResolver.java`
- Test: `jasper-app/src/test/java/dev/jasper/app/appearance/ThemeResolverTest.java`

**Interfaces:**
- Consumes: `ThemeDocument`, `ThemeDocument.over`, `ThemeToml.parse`, `ThemeFileException` (Task 1).
- Produces: `static ThemeDocument ThemeResolver.resolve(ThemeDocument document, Function<String, List<ThemeDocument>> candidates)`
  (candidates: every document with that name, highest precedence first) and `static ThemeDocument ThemeResolver.resolve(ThemeDocument document)`
  (no other themes). The result has `parent()` empty and every required seed.

- [ ] **Step 1: Write the failing test**

```java
package dev.jasper.app.appearance;

import java.awt.Color;
import java.util.List;
import java.util.Locale;
import java.util.function.Function;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class ThemeResolverTest {
    static final String BASE = """
        name = "Base"
        dark = true
        ui_complete = false
        [colors]
        background = "#101010"
        foreground = "#eeeeee"
        accent = "#3388ff"
        [colors.normal]
        black = "#000000"
        red = "#aa0000"
        green = "#00aa00"
        yellow = "#aaaa00"
        blue = "#0000aa"
        magenta = "#aa00aa"
        cyan = "#00aaaa"
        white = "#aaaaaa"
        [tokens]
        surface = "#151515"
        tab_active = "#202020"
        [ui]
        "*.background" = "#151515"
        "Panel.background" = "#151515"
        """;

    private static ThemeDocument doc(String text) { return ThemeToml.parse(text, "t.toml"); }

    private static Function<String, List<ThemeDocument>> among(ThemeDocument... documents) {
        return name -> List.of(documents).stream()
            .filter(d -> d.name().toLowerCase(Locale.ROOT).equals(name.strip().toLowerCase(Locale.ROOT))).toList();
    }

    @Test void aThemeWithoutExtendsResolvesToItself() {
        ThemeDocument base = doc(BASE);
        assertThat(ThemeResolver.resolve(base)).isEqualTo(base);
    }

    @Test void aChildMergesOverItsParentKeyByKey() {
        ThemeDocument base = doc(BASE);
        ThemeDocument child = doc("""
            name = "Child"
            extends = "  base "
            [colors]
            accent = "#ff8800"
            [colors.bright]
            red = "#ff0000"
            [tokens]
            tab_active = "#303030"
            [ui]
            "*.background" = "#202020"
            "Button.arc" = 6
            """);
        ThemeDocument resolved = ThemeResolver.resolve(child, among(child, base));
        assertThat(resolved.name()).isEqualTo("Child");
        assertThat(resolved.parent()).isEmpty();
        assertThat(resolved.dark()).as("inherited").contains(true);
        assertThat(resolved.uiComplete()).as("inherited").contains(false);
        assertThat(resolved.seeds()).containsEntry(Seed.ACCENT, new Color(0xff8800)).containsEntry(Seed.BACKGROUND, new Color(0x101010));
        assertThat(resolved.ansi()).containsEntry(9, new Color(0xff0000)).containsEntry(1, new Color(0xaa0000));
        assertThat(resolved.tokens()).containsEntry(Token.TAB_ACTIVE, new Color(0x303030)).containsEntry(Token.SURFACE, new Color(0x151515));
        assertThat(List.copyOf(resolved.ui().keySet())).as("the child's keys move last")
            .containsExactly("Panel.background", "*.background", "Button.arc");
        assertThat(resolved.ui().get("*.background")).isEqualTo("#202020");
    }

    @Test void aChainOfThreeResolvesInOrder() {
        ThemeDocument base = doc(BASE);
        ThemeDocument middle = doc("name = \"Middle\"\nextends = \"Base\"\n[tokens]\ntab_active = \"#404040\"\n");
        ThemeDocument top = doc("name = \"Top\"\nextends = \"Middle\"\ndark = false\n");
        ThemeDocument resolved = ThemeResolver.resolve(top, among(top, middle, base));
        assertThat(resolved.dark()).contains(false);
        assertThat(resolved.tokens()).containsEntry(Token.TAB_ACTIVE, new Color(0x404040));
        assertThat(resolved.seeds()).containsKey(Seed.BACKGROUND);
    }

    @Test void aThemeMayExtendTheThemeItReplacesByName() {
        ThemeDocument builtIn = doc(BASE);
        ThemeDocument mine = doc("name = \"Base\"\nextends = \"Base\"\n[colors]\naccent = \"#00ff00\"\n");
        ThemeDocument resolved = ThemeResolver.resolve(mine, among(mine, builtIn));
        assertThat(resolved.seeds()).containsEntry(Seed.ACCENT, new Color(0x00ff00)).containsKey(Seed.BACKGROUND);
    }

    @Test void aMissingParentNamesTheExtendsLine() {
        ThemeDocument child = doc("name = \"Child\"\n\nextends = \"Nowhere\"\n");
        assertThatThrownBy(() -> ThemeResolver.resolve(child, among(child)))
            .isInstanceOfSatisfying(ThemeFileException.class, failure -> {
                assertThat(failure.line()).isEqualTo(3);
                assertThat(failure.getMessage()).contains("\"Nowhere\"").contains("not a theme Jasper has");
            });
    }

    @Test void aCycleIsAnError() {
        ThemeDocument a = doc("name = \"A\"\nextends = \"B\"\n");
        ThemeDocument b = doc("name = \"B\"\nextends = \"A\"\n");
        assertThatThrownBy(() -> ThemeResolver.resolve(a, among(a, b)))
            .isInstanceOf(ThemeFileException.class).hasMessageContaining("cycle").hasMessageContaining("A -> B -> A");
    }

    @Test void missingSeedsAreListed() {
        ThemeDocument bare = doc("name = \"Bare\"\n[colors]\nbackground = \"#000000\"\n[colors.normal]\nblack = \"#000000\"\n");
        assertThatThrownBy(() -> ThemeResolver.resolve(bare))
            .isInstanceOf(ThemeFileException.class)
            .hasMessageContaining("[colors] is missing foreground, accent, normal.red, normal.green");
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :jasper-app:test --tests 'dev.jasper.app.appearance.ThemeResolverTest'`
Expected: FAIL to compile: `ThemeResolver` not found.

- [ ] **Step 3: Write the resolver**

```java
package dev.jasper.app.appearance;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Merges a theme's {@code extends} chain and checks that the result has the seeds a theme needs. */
final class ThemeResolver {
    private static final List<Seed> REQUIRED = List.of(Seed.BACKGROUND, Seed.FOREGROUND, Seed.ACCENT);

    private ThemeResolver() {}

    /** {@code document} on its own: it may not extend anything. */
    static ThemeDocument resolve(ThemeDocument document) { return resolve(document, name -> List.of()); }

    /**
     * @param candidates every theme document with a given name, highest precedence first; may include
     *                   {@code document} itself, which is then skipped, so a theme can extend the theme it replaces
     */
    static ThemeDocument resolve(ThemeDocument document, Function<String, List<ThemeDocument>> candidates) {
        var chain = new ArrayList<ThemeDocument>(List.of(document));
        ThemeDocument merged = document;
        ThemeDocument current = document;
        while (current.parent().isPresent()) {
            String name = current.parent().get();
            ThemeDocument from = current;
            List<ThemeDocument> found = candidates.apply(name);
            if (found.isEmpty())
                throw new ThemeFileException(from.origin(), from.parentLine(), "\"extends\" names \"" + name + "\", which is not a theme Jasper has");
            ThemeDocument parent = found.stream().filter(candidate -> chain.stream().noneMatch(seen -> seen == candidate))
                .findFirst().orElseThrow(() -> new ThemeFileException(from.origin(), from.parentLine(),
                    "\"extends\" forms a cycle: " + chain.stream().map(ThemeDocument::name).collect(Collectors.joining(" -> ")) + " -> " + name));
            chain.add(parent);
            merged = merged.over(parent);
            current = parent;
        }
        var missing = new ArrayList<String>();
        for (Seed seed : REQUIRED) if (!merged.seeds().containsKey(seed)) missing.add(seed.key());
        for (int i = 0; i < 8; i++) if (!merged.ansi().containsKey(i)) missing.add("normal." + ThemeDocument.ANSI_NAMES.get(i));
        if (!missing.isEmpty()) throw new ThemeFileException(document.origin(), 0, "[colors] is missing " + String.join(", ", missing));
        return merged;
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :jasper-app:test --tests 'dev.jasper.app.appearance.ThemeResolverTest'`
Expected: PASS (7 tests).

- [ ] **Step 5: Commit**

```bash
git branch --show-current
git add jasper-app/src/main/java/dev/jasper/app/appearance/ThemeResolver.java jasper-app/src/test/java/dev/jasper/app/appearance/ThemeResolverTest.java
git commit -m "Theme system: extends chains, cycles and required seeds

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 3: The generator and the UI key template

**Files:**
- Create: `jasper-app/src/main/java/dev/jasper/app/appearance/GeneratedTheme.java`, `ThemeGenerator.java`, `UiTemplate.java`
- Modify: `jasper-app/src/main/java/dev/jasper/app/appearance/ChromeKeys.java` (`luminance` becomes package-private)
- Test: `jasper-app/src/test/java/dev/jasper/app/appearance/ThemeGeneratorTest.java`

**Interfaces:**
- Consumes: Task 1 types, `ThemeResolver.resolve(ThemeDocument)` (Task 2), `ChromeKeys.mix/readable/contrast/luminance`.
- Produces: `record GeneratedTheme(String name, boolean dark, Map<Token,Color> tokens, Map<SyntaxRole,SyntaxStyle> syntax, Palette terminal, Map<String,Object> uiKeys, Map<String,Object> icons)`;
  `static GeneratedTheme ThemeGenerator.generate(ThemeDocument resolved)`;
  `UiTemplate.FLATLAF` (`Map<String,Object>`: values are `Token` or literal `String`/`Integer`/`Double`/`Boolean`),
  `UiTemplate.JASPER` (`Map<String,Token>`), `static String UiTemplate.hex(Color)`.

- [ ] **Step 1: Write the failing test**

```java
package dev.jasper.app.appearance;

import java.awt.Color;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class ThemeGeneratorTest {
    /** Catppuccin Mocha's seeds: a typical terminal-first theme with no tokens. */
    static final String SEEDS = """
        name = "Mocha"
        [colors]
        background = "#1e1e2e"
        foreground = "#cdd6f4"
        accent = "#89b4fa"
        [colors.normal]
        black = "#45475a"
        red = "#f38ba8"
        green = "#a6e3a1"
        yellow = "#f9e2af"
        blue = "#89b4fa"
        magenta = "#f5c2e7"
        cyan = "#94e2d5"
        white = "#bac2de"
        """;

    static GeneratedTheme generate(String toml) {
        return ThemeGenerator.generate(ThemeResolver.resolve(ThemeToml.parse(toml, "test.toml")));
    }

    @Test void seedsAloneMakeACompleteTheme() {
        GeneratedTheme theme = generate(SEEDS);
        assertThat(theme.name()).isEqualTo("Mocha");
        assertThat(theme.dark()).as("inferred from the background").isTrue();
        assertThat(theme.tokens()).containsOnlyKeys(Token.values());
        assertThat(theme.syntax()).containsOnlyKeys(SyntaxRole.values());
        assertThat(theme.terminal().background()).isEqualTo(new Color(0x1e1e2e));
        assertThat(theme.terminal().foreground()).isEqualTo(new Color(0xcdd6f4));
        assertThat(theme.terminal().cursor()).as("cursor defaults to the foreground").isEqualTo(new Color(0xcdd6f4));
        assertThat(theme.terminal().ansi().get(1)).isEqualTo(new Color(0xf38ba8));
        assertThat(theme.terminal().ansi().get(9)).as("bright red: 20% toward white")
            .isEqualTo(ChromeKeys.mix(Color.WHITE, new Color(0xf38ba8), .20));
        assertThat(ChromeKeys.contrast(theme.terminal().selection(), new Color(0x1e1e2e))).isGreaterThanOrEqualTo(1.5);
        assertThat(theme.tokens().get(Token.SURFACE)).isEqualTo(new Color(0x1e1e2e));
        assertThat(theme.tokens().get(Token.TEXT)).isEqualTo(new Color(0xcdd6f4));
        assertThat(theme.tokens().get(Token.ACCENT)).isEqualTo(new Color(0x89b4fa));
        assertThat(theme.tokens().get(Token.TAB_UNDERLINE)).isEqualTo(new Color(0x89b4fa));
        assertThat(theme.tokens().get(Token.EDITOR_BACKGROUND)).isEqualTo(new Color(0x1e1e2e));
        assertThat(theme.icons()).isEmpty();
    }

    @Test void derivedTextAndStrokesMeetTheirContrastFloors() {
        GeneratedTheme theme = generate(SEEDS.replace("#a6e3a1", "#203320").replace("#f38ba8", "#3a1f24")
            .replace("#f9e2af", "#33301f").replace("#45475a", "#26263a"));
        var tokens = theme.tokens();
        Color surface = tokens.get(Token.SURFACE), statusBar = tokens.get(Token.STATUS_BAR);
        assertThat(ChromeKeys.contrast(tokens.get(Token.TEXT_MUTED), surface)).isGreaterThanOrEqualTo(3);
        assertThat(ChromeKeys.contrast(tokens.get(Token.TEXT_DISABLED), surface)).isGreaterThanOrEqualTo(2);
        assertThat(ChromeKeys.contrast(tokens.get(Token.LINK), surface)).isGreaterThanOrEqualTo(4.5);
        assertThat(ChromeKeys.contrast(tokens.get(Token.DIVIDER), new Color(0x1e1e2e))).isGreaterThanOrEqualTo(3);
        for (Token status : List.of(Token.SUCCESS, Token.WARNING, Token.ERROR))
            assertThat(ChromeKeys.contrast(tokens.get(status), statusBar)).as(status.key()).isGreaterThanOrEqualTo(4.5);
        assertThat(ChromeKeys.contrast(tokens.get(Token.SELECTION_TEXT), tokens.get(Token.SELECTION))).isGreaterThanOrEqualTo(4.5);
        assertThat(ChromeKeys.contrast(tokens.get(Token.TEXT_ON_ACCENT), tokens.get(Token.BUTTON_DEFAULT))).isGreaterThanOrEqualTo(3);
        Color editor = tokens.get(Token.EDITOR_BACKGROUND);
        assertThat(ChromeKeys.contrast(theme.syntax().get(SyntaxRole.STRING).color(), editor)).isGreaterThanOrEqualTo(4.5);
        assertThat(ChromeKeys.contrast(theme.syntax().get(SyntaxRole.COMMENT).color(), editor)).isGreaterThanOrEqualTo(3);
        assertThat(theme.syntax().get(SyntaxRole.COMMENT).italic()).isTrue();
        assertThat(theme.syntax().get(SyntaxRole.KEYWORD).italic()).isFalse();
    }

    @Test void explicitValuesAreKeptAsWrittenAndFeedTheDerivations() {
        GeneratedTheme theme = generate(SEEDS + """
            [tokens]
            surface = "#101018"
            text_muted = "#2a2a3a"
            [syntax]
            string = { color = "#000001", bold = true }
            """);
        assertThat(theme.tokens().get(Token.SURFACE)).isEqualTo(new Color(0x101018));
        assertThat(theme.tokens().get(Token.TEXT_MUTED)).as("below the floor, but written by the author").isEqualTo(new Color(0x2a2a3a));
        assertThat(theme.tokens().get(Token.HOVER)).isEqualTo(ChromeKeys.mix(new Color(0xcdd6f4), new Color(0x101018), .08));
        assertThat(theme.syntax().get(SyntaxRole.STRING)).isEqualTo(new SyntaxStyle(new Color(0x000001), true, false));
    }

    @Test void darknessIsInferredFromTheBackgroundUnlessSet() {
        String light = SEEDS.replace("#1e1e2e", "#fafafa").replace("#cdd6f4", "#383a42");
        GeneratedTheme theme = generate(light);
        assertThat(theme.dark()).isFalse();
        assertThat(theme.terminal().ansi().get(9)).as("bright red: 20% toward black on a light theme")
            .isEqualTo(ChromeKeys.mix(Color.BLACK, new Color(0xf38ba8), .20));
        assertThat(generate(light.replace("name = \"Mocha\"", "name = \"Mocha\"\ndark = true")).dark()).isTrue();
    }

    @Test void uiKeysAreTheTemplateThenTheThemesOwnKeys() {
        GeneratedTheme theme = generate(SEEDS + """
            [ui]
            "Panel.background" = "#010203"
            "Button.arc" = 6
            """);
        var keys = theme.uiKeys();
        var tokens = theme.tokens();
        assertThat(List.copyOf(keys.keySet()).getFirst()).isEqualTo("*.background");
        assertThat(keys.get("*.background")).isEqualTo(UiTemplate.hex(tokens.get(Token.SURFACE)));
        assertThat(keys.get("@background")).isEqualTo(UiTemplate.hex(tokens.get(Token.SURFACE)));
        assertThat(keys.get("@foreground")).isEqualTo(UiTemplate.hex(tokens.get(Token.TEXT)));
        assertThat(keys.get("List.background")).isEqualTo(UiTemplate.hex(tokens.get(Token.SURFACE_SUNKEN)));
        assertThat(keys.get("Jasper.tabSelectedBackground")).isEqualTo(UiTemplate.hex(tokens.get(Token.TAB_ACTIVE)));
        assertThat(keys.get("Jasper.splitDivider")).isEqualTo(UiTemplate.hex(tokens.get(Token.DIVIDER)));
        assertThat(keys.get("Component.arc")).isEqualTo(4);
        assertThat(keys.get("Panel.background")).isEqualTo("#010203");
        assertThat(keys.get("Button.arc")).isEqualTo(6L);
        assertThat(List.copyOf(keys.keySet())).as("the theme's keys come last").endsWith("Panel.background", "Button.arc");
    }

    @Test void aCompleteThemeKeepsItsOwnKeysAndOnlyItsExplicitTokens() {
        GeneratedTheme theme = generate(SEEDS.replace("name = \"Mocha\"", "name = \"Mocha\"\nui_complete = true") + """
            [tokens]
            tab_active = "#123456"
            [ui]
            "Panel.background" = "#010203"
            """);
        assertThat(theme.uiKeys()).containsOnlyKeys("Jasper.tabSelectedBackground", "Panel.background");
        assertThat(theme.uiKeys().get("Jasper.tabSelectedBackground")).isEqualTo("#123456");
    }

    @Test void theTemplateHoldsOnlyTokensAndPlainValues() {
        assertThat(UiTemplate.FLATLAF.values()).allSatisfy(value -> assertThat(value)
            .isInstanceOfAny(Token.class, String.class, Integer.class, Double.class, Boolean.class));
        assertThat(UiTemplate.FLATLAF.keySet()).noneMatch(key -> key.startsWith("Jasper."));
        assertThat(UiTemplate.JASPER.keySet()).allMatch(key -> key.startsWith("Jasper."));
        assertThat(UiTemplate.hex(new Color(1, 2, 3))).isEqualTo("#010203");
        assertThat(UiTemplate.hex(new Color(1, 2, 3, 4))).isEqualTo("#01020304");
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :jasper-app:test --tests 'dev.jasper.app.appearance.ThemeGeneratorTest'`
Expected: FAIL to compile: `GeneratedTheme`, `ThemeGenerator`, `UiTemplate` not found.

- [ ] **Step 3: Make `ChromeKeys.luminance` package-private**

In `ChromeKeys.java` change `private static double luminance(Color color)` to `static double luminance(Color color)`
and give it the JavaDoc `/** WCAG relative luminance of an opaque colour, 0 (black) to 1 (white). */`.

- [ ] **Step 4: Write `GeneratedTheme`**

```java
package dev.jasper.app.appearance;

import dev.jasper.terminal.config.Palette;
import java.awt.Color;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * A Jasper theme with every value filled in: all tokens and syntax roles, the terminal palette, and the ordered UI
 * keys FlatLaf installs (IntelliJ theme {@code ui} entries). Immutable and Swing-free.
 */
public record GeneratedTheme(String name, boolean dark, Map<Token, Color> tokens, Map<SyntaxRole, SyntaxStyle> syntax,
                             Palette terminal, Map<String, Object> uiKeys, Map<String, Object> icons) {
    public GeneratedTheme {
        Objects.requireNonNull(name); Objects.requireNonNull(terminal);
        tokens = Map.copyOf(tokens);
        syntax = Map.copyOf(syntax);
        uiKeys = Collections.unmodifiableMap(new LinkedHashMap<>(uiKeys));
        icons = Collections.unmodifiableMap(new LinkedHashMap<>(icons));
    }
}
```

- [ ] **Step 5: Write `UiTemplate`**

The FlatLaf section is today's `jasper-dark.theme.json` with each named colour replaced by its token and Jasper
Dark's own literal shades left to Jasper Dark's `[ui]` (Task 6), plus the four base variables the spike proved.

```java
package dev.jasper.app.appearance;

import java.awt.Color;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import static dev.jasper.app.appearance.Token.*;

/**
 * How tokens become UI keys. {@link #FLATLAF} is the base layer for every theme that is not {@code ui_complete}: wildcards
 * first (IntelliJ applies keys in order), FlatLaf's base variables, Jasper's control geometry, then FlatLaf and IntelliJ
 * keys. {@link #JASPER} feeds Jasper's own chrome keys; {@link ChromeKeys} still derives the {@code Jasper.*} keys
 * without a token.
 */
final class UiTemplate {
    static final Map<String, Object> FLATLAF = flatlaf();
    static final Map<String, Token> JASPER = jasper();

    private UiTemplate() {}

    /** {@code #rrggbb}, or {@code #rrggbbaa} when not opaque. */
    static String hex(Color color) {
        String rgb = String.format("#%06x", color.getRGB() & 0xffffff);
        return color.getAlpha() == 255 ? rgb : rgb + String.format("%02x", color.getAlpha());
    }

    private static Map<String, Object> flatlaf() {
        var keys = new LinkedHashMap<String, Object>();
        keys.put("*.background", SURFACE);
        keys.put("*.foreground", TEXT);
        keys.put("*.disabledForeground", TEXT_DISABLED);
        keys.put("*.inactiveForeground", TEXT_DISABLED);
        keys.put("*.selectionBackground", TEXT_SELECTION);
        keys.put("*.selectionForeground", TEXT_SELECTION_TEXT);
        keys.put("*.selectionInactiveBackground", TEXT_SELECTION);
        keys.put("*.selectionInactiveForeground", TEXT_SELECTION_TEXT);
        keys.put("@background", SURFACE);
        keys.put("@foreground", TEXT);
        keys.put("@selectionBackground", SELECTION);
        keys.put("@selectionForeground", SELECTION_TEXT);
        keys.put("@accentBaseColor", ACCENT);
        keys.put("@disabledBackground", SURFACE);
        keys.put("@disabledForeground", TEXT_DISABLED);
        keys.put("@menuHoverBackground", HOVER);
        keys.put("@menuAcceleratorForeground", TEXT);
        keys.put("Component.arc", 4);
        keys.put("Button.arc", 8);
        keys.put("TextArea.margin", "4,8,4,8");
        keys.put("Button.margin", "2,14,2,14");
        keys.put("Button.minimumWidth", 72);
        keys.put("Button.default.boldText", true);
        keys.put("Button.paintShadow", false);
        keys.put("ComboBox.popupInsets", "0,0,0,0");
        keys.put("ComboBox.buttonStyle", "button");
        keys.put("Component.arrowType", "triangle");
        keys.put("Component.focusWidth", 0);
        keys.put("Component.innerFocusWidth", 0.5);
        keys.put("Component.innerOutlineWidth", 1);
        keys.put("Button.innerFocusWidth", 1);
        keys.put("CheckBox.icon.style", "outlined");
        keys.put("CheckBox.icon.focusWidth", 1);
        keys.put("RadioButton.icon.centerDiameter", 8);
        keys.put("Panel.background", SURFACE);
        keys.put("Label.foreground", TEXT);
        keys.put("Label.disabledForeground", TEXT_DISABLED);
        keys.put("List.background", SURFACE_SUNKEN);
        keys.put("List.foreground", TEXT);
        keys.put("List.selectionBackground", SELECTION);
        keys.put("List.selectionForeground", SELECTION_TEXT);
        keys.put("List.selectionInactiveBackground", SELECTION_INACTIVE);
        keys.put("List.selectionInactiveForeground", SELECTION_TEXT);
        for (String field : new String[] {"TextField", "TextArea", "PasswordField", "FormattedTextField"}) {
            keys.put(field + ".background", INPUT_BACKGROUND);
            keys.put(field + ".foreground", TEXT);
        }
        keys.put("ComboBox.background", INPUT_BACKGROUND);
        keys.put("ComboBox.nonEditableBackground", INPUT_BACKGROUND);
        keys.put("ComboBox.ArrowButton.background", INPUT_BACKGROUND);
        keys.put("ComboBox.ArrowButton.nonEditableBackground", INPUT_BACKGROUND);
        keys.put("ComboBox.ArrowButton.iconColor", TEXT);
        keys.put("ComboBox.foreground", TEXT);
        keys.put("ComboBox.selectionBackground", SELECTION);
        keys.put("ComboBox.selectionForeground", SELECTION_TEXT);
        keys.put("Button.startBackground", BUTTON_BACKGROUND);
        keys.put("Button.endBackground", BUTTON_BACKGROUND);
        keys.put("Button.startBorderColor", BUTTON_BORDER);
        keys.put("Button.endBorderColor", BUTTON_BORDER);
        keys.put("Button.foreground", BUTTON_TEXT);
        keys.put("Button.focusedBackground", BUTTON_BACKGROUND);
        keys.put("Button.hoverBackground", HOVER);
        keys.put("Button.pressedBackground", PRESSED);
        keys.put("Button.disabledBackground", SURFACE);
        keys.put("Button.default.startBackground", BUTTON_DEFAULT);
        keys.put("Button.default.endBackground", BUTTON_DEFAULT);
        keys.put("Button.default.startBorderColor", BUTTON_DEFAULT);
        keys.put("Button.default.endBorderColor", BUTTON_DEFAULT);
        keys.put("Button.default.foreground", TEXT_ON_ACCENT);
        keys.put("Button.default.focusedBackground", BUTTON_DEFAULT);
        keys.put("Button.toolbar.hoverBackground", HOVER);
        keys.put("Button.toolbar.pressedBackground", PRESSED);
        keys.put("Button.toolbar.selectedBackground", PRESSED);
        keys.put("Component.borderColor", BORDER);
        keys.put("Component.disabledBorderColor", BORDER);
        keys.put("Component.focusedBorderColor", FOCUS_RING);
        keys.put("Component.focusColor", FOCUS_RING);
        keys.put("Link.activeForeground", LINK);
        keys.put("ProgressBar.foreground", ACCENT);
        keys.put("ProgressBar.progressColor", ACCENT);
        keys.put("Separator.foreground", SEPARATOR);
        keys.put("Separator.separatorColor", SEPARATOR);
        keys.put("ToolBar.background", TITLE_BAR);
        keys.put("MenuBar.background", TITLE_BAR);
        keys.put("Menu.background", SURFACE_RAISED);
        keys.put("Menu.foreground", TEXT);
        keys.put("MenuItem.background", SURFACE_RAISED);
        keys.put("MenuItem.foreground", TEXT);
        keys.put("MenuItem.selectionForeground", SELECTION_TEXT);
        keys.put("MenuItem.acceleratorForeground", TEXT);
        keys.put("PopupMenu.background", SURFACE_RAISED);
        keys.put("PopupMenu.borderColor", BORDER);
        keys.put("TitlePane.background", TITLE_BAR);
        keys.put("TitlePane.inactiveBackground", TITLE_BAR);
        keys.put("TitlePane.foreground", TEXT_MUTED);
        keys.put("TitlePane.inactiveForeground", TEXT_MUTED);
        keys.put("TitlePane.infoForeground", TEXT_MUTED);
        keys.put("TitlePane.inactiveInfoForeground", TEXT_MUTED);
        keys.put("TabbedPane.background", TAB_BAR);
        keys.put("TabbedPane.selectedBackground", SURFACE_SUNKEN);
        keys.put("TabbedPane.selectedForeground", TEXT);
        keys.put("TabbedPane.hoverColor", HOVER);
        keys.put("TabbedPane.focusColor", HOVER);
        keys.put("TabbedPane.contentAreaColor", BORDER);
        keys.put("TabbedPane.underlineColor", ACCENT);
        keys.put("SplitPaneDivider.gripColor", TEXT);
        keys.put("SplitPaneDivider.hoverColor", HOVER);
        keys.put("SplitPaneDivider.pressedColor", PRESSED);
        keys.put("ScrollBar.thumb", SCROLLBAR_THUMB);
        keys.put("ScrollBar.track", SCROLLBAR_TRACK);
        keys.put("ScrollPane.background", SCROLLBAR_TRACK);
        keys.put("ToolTip.background", SURFACE_RAISED);
        keys.put("ToolTip.foreground", TEXT);
        keys.put("Tree.background", SURFACE_SUNKEN);
        keys.put("Table.background", SURFACE_SUNKEN);
        keys.put("MainToolbar.background", TITLE_BAR);
        keys.put("MainToolbar.foreground", TEXT);
        keys.put("EditorTabs.underlinedTabBackground", SURFACE_SUNKEN);
        keys.put("EditorTabs.underlinedTabForeground", TEXT);
        keys.put("EditorTabs.hoverBackground", TAB_HOVER);
        keys.put("EditorTabs.underlineColor", TAB_UNDERLINE);
        keys.put("EditorTabs.inactiveUnderlineColor", TAB_UNDERLINE_INACTIVE);
        keys.put("ToolWindow.background", SIDEBAR);
        keys.put("ToolWindow.Header.background", TITLE_BAR);
        keys.put("StatusBar.background", STATUS_BAR);
        keys.put("StatusBar.borderColor", BORDER);
        keys.put("Borders.color", BORDER);
        keys.put("List.hoverBackground", HOVER);
        keys.put("Popup.borderColor", BORDER);
        keys.put("SearchEverywhere.Header.background", TITLE_BAR);
        keys.put("SearchEverywhere.Tab.selectedBackground", PRESSED);
        keys.put("SearchEverywhere.Tab.selectedForeground", TEXT);
        keys.put("SearchEverywhere.SearchField.background", INPUT_BACKGROUND);
        keys.put("SearchEverywhere.SearchField.borderColor", BORDER);
        keys.put("SearchEverywhere.SearchField.infoForeground", TEXT_MUTED);
        keys.put("SearchEverywhere.List.separatorColor", BORDER);
        keys.put("SearchEverywhere.List.separatorForeground", TEXT_MUTED);
        keys.put("SearchEverywhere.Advertiser.background", TITLE_BAR);
        keys.put("SearchEverywhere.Advertiser.foreground", TEXT_MUTED);
        keys.put("SearchField.errorBackground", ERROR_BACKGROUND);
        return Collections.unmodifiableMap(keys);
    }

    private static Map<String, Token> jasper() {
        var keys = new LinkedHashMap<String, Token>();
        keys.put("Jasper.editorBackground", EDITOR_BACKGROUND);
        keys.put("Jasper.editorForeground", EDITOR_FOREGROUND);
        keys.put("Jasper.editorGutter", EDITOR_GUTTER);
        keys.put("Jasper.editorCurrentLine", EDITOR_CURRENT_LINE);
        keys.put("Jasper.editorSelection", EDITOR_SELECTION);
        keys.put("Jasper.editorCaret", EDITOR_CARET);
        keys.put("Jasper.editorLineNumbers", EDITOR_LINE_NUMBERS);
        keys.put("Jasper.titleBackground", TITLE_BAR);
        keys.put("Jasper.titleForeground", TEXT_MUTED);
        keys.put("Jasper.titleInactiveForeground", TEXT_MUTED);
        keys.put("Jasper.titleSeparator", BORDER);
        keys.put("Jasper.chromeForeground", TEXT);
        keys.put("Jasper.mutedForeground", TEXT_MUTED);
        keys.put("Jasper.tabSelectedBackground", TAB_ACTIVE);
        keys.put("Jasper.tabSelectedForeground", TAB_ACTIVE_TEXT);
        keys.put("Jasper.tabSelectedBorder", TAB_ACTIVE_BORDER);
        keys.put("Jasper.tabSelectedBorderInactive", TAB_UNDERLINE_INACTIVE);
        keys.put("Jasper.tabHoverBackground", TAB_HOVER);
        keys.put("Jasper.tabUnderline", TAB_UNDERLINE);
        keys.put("Jasper.tabUnderlineInactive", TAB_UNDERLINE_INACTIVE);
        keys.put("Jasper.findErrorBackground", ERROR_BACKGROUND);
        keys.put("Jasper.splitDivider", DIVIDER);
        keys.put("Jasper.runningForeground", SUCCESS);
        keys.put("Jasper.configSuccessForeground", SUCCESS);
        keys.put("Jasper.configWarningForeground", WARNING);
        keys.put("Jasper.configErrorForeground", ERROR);
        return Collections.unmodifiableMap(keys);
    }
}
```

- [ ] **Step 6: Write `ThemeGenerator`**

```java
package dev.jasper.app.appearance;

import dev.jasper.terminal.config.Palette;
import java.awt.Color;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import static dev.jasper.app.appearance.ChromeKeys.contrast;
import static dev.jasper.app.appearance.ChromeKeys.mix;
import static dev.jasper.app.appearance.ChromeKeys.readable;
import static dev.jasper.app.appearance.Token.*;

/**
 * Fills a resolved theme document in: missing seeds, every token and syntax role, the terminal palette and the UI
 * keys. A value the theme sets is used as written; a derived text or stroke colour is moved toward the foreground
 * until it meets its contrast floor (4.5:1 text, links and status colours; 3:1 muted text, dividers and comments;
 * 2:1 disabled text). Pure: no Swing state.
 */
final class ThemeGenerator {
    private ThemeGenerator() {}

    /** @param resolved a document from {@link ThemeResolver}, so every required seed is present */
    static GeneratedTheme generate(ThemeDocument resolved) { return new Generation(resolved).result(); }

    private static final class Generation {
        private final ThemeDocument document;
        private final Color background, foreground, accent, cursor, selection;
        private final boolean dark;
        private final List<Color> ansi;
        private final Map<Token, Color> tokens = new EnumMap<>(Token.class);

        Generation(ThemeDocument document) {
            this.document = document;
            background = seed(Seed.BACKGROUND);
            foreground = seed(Seed.FOREGROUND);
            accent = seed(Seed.ACCENT);
            dark = document.dark().orElse(ChromeKeys.luminance(background) < .5);
            var colours = new ArrayList<Color>(16);
            for (int i = 0; i < 8; i++) {
                Color normal = document.ansi().get(i);
                if (normal == null) throw new IllegalArgumentException(document.origin() + ": [colors.normal] has no "
                    + ThemeDocument.ANSI_NAMES.get(i));
                colours.add(normal);
            }
            for (int i = 8; i < 16; i++)
                colours.add(document.ansi().getOrDefault(i, mix(dark ? Color.WHITE : Color.BLACK, colours.get(i - 8), .20)));
            ansi = List.copyOf(colours);
            cursor = document.seeds().getOrDefault(Seed.CURSOR, foreground);
            selection = document.seeds().getOrDefault(Seed.SELECTION, readable(mix(accent, background, .35), background, 1.5, accent));
        }

        private Color seed(Seed seed) {
            Color value = document.seeds().get(seed);
            if (value == null) throw new IllegalArgumentException(document.origin() + ": [colors] has no " + seed.key());
            return value;
        }

        GeneratedTheme result() {
            for (Token token : Token.values()) token(token);
            var syntax = new EnumMap<SyntaxRole, SyntaxStyle>(SyntaxRole.class);
            for (SyntaxRole role : SyntaxRole.values()) syntax.put(role, syntax(role));
            return new GeneratedTheme(document.name(), dark, tokens, syntax,
                new Palette(foreground, background, cursor, selection, ansi), uiKeys(), document.icons());
        }

        private Color token(Token token) {
            Color value = tokens.get(token);
            if (value != null) return value;
            value = document.tokens().containsKey(token) ? document.tokens().get(token) : derive(token);
            tokens.put(token, value);
            return value;
        }

        private Color derive(Token token) {
            return switch (token) {
                case SURFACE -> background;
                case SURFACE_RAISED -> mix(foreground, token(SURFACE), .06);
                case SURFACE_SUNKEN -> mix(dark ? Color.BLACK : Color.WHITE, token(SURFACE), .12);
                case TITLE_BAR, STATUS_BAR, TAB_BAR -> mix(foreground, token(SURFACE), .04);
                case SIDEBAR -> token(SURFACE_SUNKEN);
                case TEXT -> foreground;
                case TEXT_MUTED -> readable(mix(foreground, token(SURFACE), .65), token(SURFACE), 3, foreground);
                case TEXT_DISABLED -> readable(mix(foreground, token(SURFACE), .45), token(SURFACE), 2, foreground);
                case TEXT_ON_ACCENT -> blackOrWhite(token(BUTTON_DEFAULT));
                case LINK -> readable(token(ACCENT), token(SURFACE), 4.5, foreground);
                case BORDER -> mix(foreground, token(SURFACE), .16);
                case SEPARATOR -> token(BORDER);
                case DIVIDER -> readable(mix(foreground, background, .40), background, 3, foreground);
                case FOCUS_RING, TAB_UNDERLINE, BUTTON_DEFAULT -> token(ACCENT);
                case HOVER -> mix(foreground, token(SURFACE), .08);
                case PRESSED -> mix(foreground, token(SURFACE), .14);
                case SELECTION -> mix(token(ACCENT), token(SURFACE), .55);
                case SELECTION_TEXT -> textOn(token(SELECTION));
                case SELECTION_INACTIVE -> mix(token(SELECTION), token(SURFACE), .6);
                case TEXT_SELECTION, EDITOR_SELECTION -> selection;
                case TEXT_SELECTION_TEXT, TAB_ACTIVE_TEXT, BUTTON_TEXT -> token(TEXT);
                case TAB_ACTIVE -> mix(token(ACCENT), token(SURFACE), .18);
                case TAB_ACTIVE_BORDER -> mix(token(ACCENT), token(SURFACE), .45);
                case TAB_HOVER -> token(HOVER);
                case TAB_UNDERLINE_INACTIVE -> mix(foreground, token(SURFACE), .45);
                case INPUT_BACKGROUND -> mix(foreground, token(SURFACE), .03);
                case BUTTON_BACKGROUND -> mix(foreground, token(SURFACE), .12);
                case BUTTON_BORDER -> mix(foreground, token(SURFACE), .18);
                case SCROLLBAR_THUMB -> mix(foreground, token(SURFACE), .22);
                case SCROLLBAR_TRACK -> mix(foreground, token(SURFACE), .05);
                case ACCENT -> accent;
                case SUCCESS -> readable(ansi.get(2), token(STATUS_BAR), 4.5, foreground);
                case WARNING -> readable(ansi.get(3), token(STATUS_BAR), 4.5, foreground);
                case ERROR -> readable(ansi.get(1), token(STATUS_BAR), 4.5, foreground);
                case ERROR_BACKGROUND -> mix(ansi.get(1), token(INPUT_BACKGROUND), .20);
                case EDITOR_BACKGROUND -> background;
                case EDITOR_FOREGROUND -> foreground;
                case EDITOR_CARET -> cursor;
                case EDITOR_CURRENT_LINE -> mix(foreground, background, .05);
                case EDITOR_GUTTER -> token(EDITOR_BACKGROUND);
                case EDITOR_LINE_NUMBERS -> token(TEXT_DISABLED);
            };
        }

        /** The foreground when it reads well on {@code fill}, else black or white, whichever contrasts more. */
        private Color textOn(Color fill) { return contrast(foreground, fill) >= 4.5 ? foreground : blackOrWhite(fill); }

        private static Color blackOrWhite(Color fill) {
            return contrast(Color.WHITE, fill) >= contrast(Color.BLACK, fill) ? Color.WHITE : Color.BLACK;
        }

        private SyntaxStyle syntax(SyntaxRole role) {
            SyntaxStyle explicit = document.syntax().get(role);
            if (explicit != null) return explicit;
            Color editor = token(EDITOR_BACKGROUND), text = token(EDITOR_FOREGROUND);
            Color base = switch (role) {
                case KEYWORD -> ansi.get(5);
                case TYPE, REGEX -> ansi.get(6);
                case FUNCTION -> ansi.get(4);
                case VARIABLE -> text;
                case CONSTANT, NUMBER, ANNOTATION, ATTRIBUTE -> ansi.get(3);
                case STRING -> ansi.get(2);
                case COMMENT, DOC_COMMENT -> ansi.get(8);
                case OPERATOR, PUNCTUATION -> mix(text, editor, .75);
                case TAG, ERROR -> ansi.get(1);
            };
            boolean comment = role == SyntaxRole.COMMENT || role == SyntaxRole.DOC_COMMENT;
            return new SyntaxStyle(readable(base, editor, comment ? 3 : 4.5, text), false, comment);
        }

        /**
         * The template's FlatLaf section (skipped for a {@code ui_complete} theme), its {@code Jasper.*} section (only
         * explicit tokens for a complete theme), then the theme's own keys, which replace and follow the template's.
         */
        private Map<String, Object> uiKeys() {
            boolean complete = document.uiComplete().orElse(false);
            var keys = new LinkedHashMap<String, Object>();
            if (!complete) UiTemplate.FLATLAF.forEach((key, value) ->
                keys.put(key, value instanceof Token token ? UiTemplate.hex(token(token)) : value));
            UiTemplate.JASPER.forEach((key, token) -> {
                if (!complete || document.tokens().containsKey(token)) keys.put(key, UiTemplate.hex(token(token)));
            });
            document.ui().forEach((key, value) -> { keys.remove(key); keys.put(key, value); });
            return keys;
        }
    }
}
```

- [ ] **Step 7: Run test to verify it passes**

Run: `./gradlew :jasper-app:test --tests 'dev.jasper.app.appearance.ThemeGeneratorTest' --tests 'dev.jasper.app.appearance.ChromeKeysTest'`
Expected: PASS (7 + existing ChromeKeys tests).

- [ ] **Step 8: Commit**

```bash
git branch --show-current
git add jasper-app/src/main/java/dev/jasper/app/appearance/{GeneratedTheme,ThemeGenerator,UiTemplate,ChromeKeys}.java jasper-app/src/test/java/dev/jasper/app/appearance/ThemeGeneratorTest.java
git commit -m "Theme system: generator, contrast floors and the UI key template

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 4: Installing a generated theme

**Files:**
- Modify: `jasper-app/src/main/java/dev/jasper/app/appearance/ThemeLoader.java`, `ThemeManager.java`
- Test: `jasper-app/src/test/java/dev/jasper/app/appearance/ThemeManagerTest.java` (add three tests)

**Interfaces:**
- Consumes: `GeneratedTheme`, `UiTemplate.hex`, `ThemeGeneratorTest.SEEDS` and `ThemeGeneratorTest.generate` (Task 3).
- Produces: `ThemeLoader.Resolved` gains `Map<String,Object> ui` and `Map<String,Object> icons` (last two components);
  package-private `static Map<String,Color> ThemeLoader.explicitColors(Map<String,Object> ui)`;
  `static String ThemeLoader.json(String name, boolean dark, String author, Map<String,Object> ui, Map<String,Object> icons)`;
  `static boolean ThemeManager.install(GeneratedTheme theme)` (EDT).

- [ ] **Step 1: Write the failing tests** (add to `ThemeManagerTest`, which already imports `FlatLaf`, `Color`, `UIManager`, `LookAndFeel`)

```java
    @Test void aGeneratedThemeInstallsItsTokensAsTheLookAndFeel() throws Exception {
        LookAndFeel original = UIManager.getLookAndFeel();
        try {
            GeneratedTheme theme = ThemeGeneratorTest.generate(ThemeGeneratorTest.SEEDS);
            assertThat(ThemeManager.install(theme)).isTrue();
            assertThat(UIManager.getLookAndFeel().getName()).isEqualTo("Mocha");
            assertThat(FlatLaf.isLafDark()).isTrue();
            assertThat(UIManager.getColor("Panel.background")).isEqualTo(theme.tokens().get(Token.SURFACE));
            assertThat(UIManager.getColor("List.background")).isEqualTo(theme.tokens().get(Token.SURFACE_SUNKEN));
            assertThat(UIManager.getColor("Jasper.tabSelectedBackground")).isEqualTo(theme.tokens().get(Token.TAB_ACTIVE));
            assertThat(UIManager.getColor("EditorTabs.underlineColor")).as("a key FlatLaf skips is put back")
                .isEqualTo(theme.tokens().get(Token.TAB_UNDERLINE));
            assertThat(UIManager.getColor("Jasper.titleSearch.background")).as("ChromeKeys fills keys without a token").isNotNull();
            assertThat(UIManager.getInt("Component.arc")).isEqualTo(4);
            assertThat(UIManager.getFont("Label.font").getSize2D()).as("app defaults still apply").isEqualTo(12f);
        } finally { UIManager.setLookAndFeel(original); }
    }

    /** The 2026-10-01 spike: FlatLaf re-derives its controls from the base variables the template sets. */
    @Test void flatLafReDerivesUnsetControlsFromTheBaseVariables() throws Exception {
        LookAndFeel original = UIManager.getLookAndFeel();
        try {
            ThemeManager.install(ThemeGeneratorTest.generate(ThemeGeneratorTest.SEEDS));
            Color first = UIManager.getColor("Button.disabledSelectedBackground");
            ThemeManager.install(ThemeGeneratorTest.generate(ThemeGeneratorTest.SEEDS.replace("#1e1e2e", "#2e3440")));
            assertThat(UIManager.getColor("Button.disabledSelectedBackground")).isNotEqualTo(first);
        } finally { UIManager.setLookAndFeel(original); }
    }

    @Test void aThemesOwnKeysWinOverTheTemplate() throws Exception {
        LookAndFeel original = UIManager.getLookAndFeel();
        try {
            ThemeManager.install(ThemeGeneratorTest.generate(ThemeGeneratorTest.SEEDS + "[ui]\n\"Panel.background\" = \"#010203\"\n"));
            assertThat(hex(UIManager.getColor("Panel.background"))).isEqualTo("#010203");
        } finally { UIManager.setLookAndFeel(original); }
    }
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `./gradlew :jasper-app:test --tests 'dev.jasper.app.appearance.ThemeManagerTest'`
Expected: FAIL to compile: no `ThemeManager.install(GeneratedTheme)`.

- [ ] **Step 3: Extend `ThemeLoader`**

1. Change the record to:

```java
    /**
     * A resolved theme: FlatLaf's JSON input, every explicit colour keyed as the theme names it, the problems met, and
     * the resolved {@code ui} and {@code icons} entries (named colours replaced) for conversion.
     */
    record Resolved(String name, boolean dark, String json, Map<String, Color> colors, List<String> warnings,
                    Map<String, Object> ui, Map<String, Object> icons) {
        Resolved {
            colors = Map.copyOf(colors); warnings = List.copyOf(warnings);
            ui = java.util.Collections.unmodifiableMap(new LinkedHashMap<>(ui));
            icons = java.util.Collections.unmodifiableMap(new LinkedHashMap<>(icons));
        }
    }
```

2. In `load(String file)`, after the `ui` map is built, resolve the icon table and return the new components:

```java
        var icons = new LinkedHashMap<String, Object>();
        theme.icons.forEach((key, value) -> icons.put(key, named(value, colors)));
        var json = new LinkedHashMap<String, Object>();
        json.put("name", theme.name);
        json.put("dark", theme.dark);
        // FlatLaf 3.7 fails on a theme without an author.
        json.put("author", theme.author);
        json.put("colors", new LinkedHashMap<String, Object>(colors));
        json.put("ui", ui);
        if (!icons.isEmpty()) json.put("icons", icons);
        return new Resolved(theme.name, theme.dark, write(json), explicitColors(ui), warnings, ui, icons);
```

3. Add, next to `write`:

```java
    /** FlatLaf's JSON input for already-resolved {@code ui} and {@code icons} entries (no named colours left). */
    static String json(String name, boolean dark, String author, Map<String, Object> ui, Map<String, Object> icons) {
        var json = new LinkedHashMap<String, Object>();
        json.put("name", name);
        json.put("dark", dark);
        json.put("author", author);
        json.put("colors", new LinkedHashMap<String, Object>());
        json.put("ui", ui);
        if (!icons.isEmpty()) json.put("icons", icons);
        return write(json);
    }
```

4. Change `private static Map<String, Color> explicitColors(Map<String, Object> ui)` to package-private
   (`static Map<String, Color> explicitColors(Map<String, Object> ui)`).

- [ ] **Step 4: Add `ThemeManager.install(GeneratedTheme)`**

Add to `ThemeManager` (and replace its private `hex` method and both call sites with `UiTemplate.hex`):

```java
    /** Installs a generated theme through the same path as any IntelliJ theme; false when FlatLaf could not set it up. */
    static boolean install(GeneratedTheme theme) {
        requireEdt();
        var resolved = new ThemeLoader.Resolved(theme.name(), theme.dark(),
            ThemeLoader.json(theme.name(), theme.dark(), "Jasper", theme.uiKeys(), theme.icons()),
            ThemeLoader.explicitColors(theme.uiKeys()), List.of(), theme.uiKeys(), theme.icons());
        return install(resolved, theme.terminal().background());
    }
```

- [ ] **Step 5: Run tests to verify they pass**

Run: `./gradlew :jasper-app:test --tests 'dev.jasper.app.appearance.*'`
Expected: PASS, including the unchanged `ThemeLoaderTest` and the existing `ThemeManagerTest` cases.

- [ ] **Step 6: Commit**

```bash
git branch --show-current
git add jasper-app/src/main/java/dev/jasper/app/appearance/{ThemeLoader,ThemeManager}.java jasper-app/src/test/java/dev/jasper/app/appearance/ThemeManagerTest.java
git commit -m "Theme system: install generated themes through the IntelliJ theme path

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 5: Every theme is a document; IntelliJ themes are converted

Every `Theme` now carries a resolved `ThemeDocument` and installs through the generator. IntelliJ Light, Darcula,
today's Jasper Dark JSON (temporarily, until Task 6) and installed plugin themes are converted as `ui_complete`
documents, so their installed keys do not change.

**Files:**
- Create: `jasper-app/src/main/java/dev/jasper/app/appearance/IntellijConversion.java`, `BuiltInThemes.java`
- Modify: `appearance/Theme.java`, `ThemeManager.java`, `ThemeCatalog.java`, `ThemeTestSupport.java` (test)
- Modify (rename `Theme.LIGHT` → `Theme.INTELLIJ_LIGHT`): every file under `jasper-app/src` that uses it
- Test: `jasper-app/src/test/java/dev/jasper/app/appearance/IntellijConversionTest.java`; update `ThemeManagerTest`, `ThemeCatalogTest`, `ThemeStateTest`, `TerminalColorsThemeTest`

**Interfaces:**
- Consumes: Tasks 1–4.
- Produces: `Theme(String id, String name, boolean dark, Palette palette, Source source)` with
  `sealed interface Source permits Bundled, Archive { ThemeDocument document(); }`, `record Bundled(String file, ThemeDocument document)`,
  `record Archive(Path archive, String entry, ThemeDocument document)`, `static Theme Theme.of(String id, Source source)`,
  constants `Theme.DARK`, `Theme.INTELLIJ_LIGHT`, `Theme.DARCULA`;
  `static ThemeDocument IntellijConversion.convert(ThemeLoader.Resolved resolved, Palette palette, String origin)`;
  `ThemeManager`: `static ThemeLoader.Resolved loadBuiltIn(String file)`, `static ThemeLoader.Resolved loadArchive(String entry, String json)`,
  `static String read(String file)`, `static GeneratedTheme generated(Theme theme)`; `builtIns()` = `[INTELLIJ_LIGHT, DARK, DARCULA]`.

- [ ] **Step 1: Write the failing conversion test**

```java
package dev.jasper.app.appearance;

import dev.jasper.terminal.config.Palette;
import java.awt.Color;
import java.util.Map;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class IntellijConversionTest {
    @Test void aResolvedIntellijThemeBecomesACompleteDocumentWithItsPaletteAsSeeds() {
        var resolved = new ThemeLoader(Map.of("t.json", """
            {"name": "Fixture", "dark": true, "author": "Someone", "colors": {"blue": "#3592c4"},
             "ui": {"*": {"background": "#2b2b2b"}, "Panel.background": "#2b2b2b", "Component.focusColor": "blue"},
             "icons": {"ColorPalette": {"Actions.Red": "#c75450"}}}""")::get, Map.of()).load("t.json");
        Palette palette = Palette.jasperDark();
        ThemeDocument document = IntellijConversion.convert(resolved, palette, "fixture.jar!/t.json");
        assertThat(document.origin()).isEqualTo("fixture.jar!/t.json");
        assertThat(document.name()).isEqualTo("Fixture");
        assertThat(document.dark()).contains(true);
        assertThat(document.uiComplete()).contains(true);
        assertThat(document.parent()).isEmpty();
        assertThat(document.seeds()).containsEntry(Seed.BACKGROUND, palette.background())
            .containsEntry(Seed.FOREGROUND, palette.foreground()).containsEntry(Seed.CURSOR, palette.cursor())
            .containsEntry(Seed.SELECTION, palette.selection()).containsEntry(Seed.ACCENT, new Color(0x3592c4));
        for (int i = 0; i < 16; i++) assertThat(document.ansi()).containsEntry(i, palette.ansi().get(i));
        assertThat(document.tokens()).isEmpty();
        assertThat(document.ui()).isEqualTo(resolved.ui());
        assertThat(document.icons()).isEqualTo(Map.of("ColorPalette", Map.of("Actions.Red", "#c75450")));
        GeneratedTheme generated = ThemeGenerator.generate(document);
        assertThat(generated.uiKeys()).as("a complete theme installs its own keys only").isEqualTo(resolved.ui());
        assertThat(generated.terminal()).isEqualTo(palette);
    }

    @Test void withoutAnAccentKeyTheAccentIsAnsiBlue() {
        var resolved = new ThemeLoader(Map.of("t.json", "{\"name\": \"Plain\", \"ui\": {}}")::get, Map.of()).load("t.json");
        ThemeDocument document = IntellijConversion.convert(resolved, Palette.jasperLight(), "t.json");
        assertThat(document.seeds()).containsEntry(Seed.ACCENT, Palette.jasperLight().ansi().get(4));
        assertThat(document.dark()).contains(false);
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :jasper-app:test --tests 'dev.jasper.app.appearance.IntellijConversionTest'`
Expected: FAIL to compile: `IntellijConversion` not found.

- [ ] **Step 3: Write `IntellijConversion`**

```java
package dev.jasper.app.appearance;

import dev.jasper.terminal.config.Palette;
import java.awt.Color;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Turns a resolved IntelliJ theme and its terminal palette into a complete ({@code ui_complete}) Jasper theme
 * document: the palette becomes the seeds, the theme's keys its {@code [ui]}. Plan 4 adds tokens and syntax and moves
 * this to the importer.
 */
final class IntellijConversion {
    /** Where an IntelliJ theme keeps its accent, most specific first. */
    private static final List<String> ACCENT_KEYS = List.of("Component.focusColor", "Component.accentColor",
        "EditorTabs.underlineColor", "Button.default.startBackground", "Component.linkColor");

    private IntellijConversion() {}

    static ThemeDocument convert(ThemeLoader.Resolved resolved, Palette palette, String origin) {
        var seeds = new EnumMap<Seed, Color>(Seed.class);
        seeds.put(Seed.BACKGROUND, palette.background());
        seeds.put(Seed.FOREGROUND, palette.foreground());
        seeds.put(Seed.CURSOR, palette.cursor());
        seeds.put(Seed.SELECTION, palette.selection());
        Color accent = ACCENT_KEYS.stream().map(resolved.colors()::get).filter(Objects::nonNull).findFirst()
            .orElse(palette.ansi().get(4));
        seeds.put(Seed.ACCENT, new Color(accent.getRGB() & 0xffffff));
        var ansi = new HashMap<Integer, Color>();
        for (int i = 0; i < 16; i++) ansi.put(i, palette.ansi().get(i));
        return new ThemeDocument(origin, resolved.name(), Optional.of(resolved.dark()), Optional.empty(), 0,
            Optional.of(true), Map.of(), seeds, ansi, Map.of(), Map.of(), resolved.ui(), resolved.icons());
    }
}
```

- [ ] **Step 4: Rewrite `Theme` and add `BuiltInThemes`**

`Theme.java`:

```java
package dev.jasper.app.appearance;

import dev.jasper.terminal.config.Palette;
import java.nio.file.Path;
import java.util.Objects;

/**
 * A theme Jasper can install: its name, darkness and terminal palette, and the resolved Jasper theme document
 * ({@code extends} merged in) it is generated from.
 */
public record Theme(String id, String name, boolean dark, Palette palette, Source source) {
    /** Where a theme comes from; every source carries the resolved document. */
    public sealed interface Source permits Bundled, Archive {
        /** The resolved theme document. */
        ThemeDocument document();
    }

    /** A theme Jasper ships, from {@code file} under {@code dev/jasper/app/themes/}: a Jasper theme or a converted IntelliJ one. */
    public record Bundled(String file, ThemeDocument document) implements Source {
        public Bundled { Objects.requireNonNull(file); Objects.requireNonNull(document); }
    }

    /** A theme an installed IntelliJ plugin provides, converted when the catalog read the archive. */
    public record Archive(Path archive, String entry, ThemeDocument document) implements Source {
        public Archive { Objects.requireNonNull(archive); Objects.requireNonNull(entry); Objects.requireNonNull(document); }
    }

    /** Jasper's own dark theme, the default. */
    public static final Theme DARK = BuiltInThemes.intellij("jasper-dark", "jasper-dark.theme.json", BuiltInThemes.GRAPHITE);
    /** Classic IntelliJ Light, converted from intellij-community. */
    public static final Theme INTELLIJ_LIGHT = BuiltInThemes.intellij("intellij-light", "intellij/Light.theme.json", "IntelliJ Light");
    /** Darcula, converted from intellij-community. */
    public static final Theme DARCULA = BuiltInThemes.intellij("darcula", "intellij/darcula.theme.json", "Darcula");

    public Theme {
        Objects.requireNonNull(id);
        Objects.requireNonNull(name);
        Objects.requireNonNull(palette);
        Objects.requireNonNull(source);
    }

    /** The theme {@code source}'s document generates, under {@code id}. */
    static Theme of(String id, Source source) {
        GeneratedTheme generated = ThemeGenerator.generate(source.document());
        return new Theme(id, generated.name(), generated.dark(), generated.terminal(), source);
    }
}
```

`BuiltInThemes.java`:

```java
package dev.jasper.app.appearance;

import dev.jasper.terminal.config.Palette;
import java.awt.Color;

/** The themes Jasper ships: Jasper's own theme files and IntelliJ's classic themes, converted. */
final class BuiltInThemes {
    /** Jasper Dark's terminal palette until Task 6 moves it into {@code jasper-dark.toml}. */
    static final Palette GRAPHITE = new Palette(new Color(0xd5dee5), new Color(0x191c20), new Color(0xd5dee5),
        new Color(0x35434e), Palette.jasperDark().ansi());

    private BuiltInThemes() {}

    /** An IntelliJ theme file, converted, with the terminal palette of the editor scheme it names. */
    static Theme intellij(String id, String file, String scheme) {
        ThemeLoader.Resolved resolved = ThemeManager.loadBuiltIn(file);
        return intellij(id, file, resolved, EditorScheme.named(scheme, scheme, resolved.dark()).palette());
    }

    /** An IntelliJ theme file, converted, with {@code palette} as its terminal palette. */
    static Theme intellij(String id, String file, Palette palette) {
        return intellij(id, file, ThemeManager.loadBuiltIn(file), palette);
    }

    private static Theme intellij(String id, String file, ThemeLoader.Resolved resolved, Palette palette) {
        return Theme.of(id, new Theme.Bundled(file, IntellijConversion.convert(resolved, palette, file)));
    }
}
```

- [ ] **Step 5: Rework `ThemeManager`**

Replace the `RESOLVED` field and the `builtIns`, `resolve`, `load` and `install(Theme)` methods with:

```java
    private static final Map<Theme, GeneratedTheme> GENERATED = new HashMap<>();

    /** The bundled themes, Light first. */
    public static List<Theme> builtIns() { return List.of(Theme.INTELLIJ_LIGHT, Theme.DARK, Theme.DARCULA); }

    /** {@code theme}'s generated values; EDT only (the cache is unsynchronized). */
    static GeneratedTheme generated(Theme theme) {
        return GENERATED.computeIfAbsent(theme, key -> ThemeGenerator.generate(key.source().document()));
    }

    /** A theme file Jasper ships, resolved; touches no Swing state. */
    static ThemeLoader.Resolved loadBuiltIn(String file) { return LOADER.load(file); }

    /** A plugin's theme file, resolved against the parent themes Jasper ships; touches no Swing state. */
    static ThemeLoader.Resolved loadArchive(String entry, String json) {
        return new ThemeLoader(file -> file.equals(entry) ? json : read(file), FILES).load(entry);
    }

    /** Installs {@code theme} as the look and feel; false when FlatLaf could not set it up. */
    public static boolean install(Theme theme) {
        requireEdt();
        return install(generated(theme));
    }
```

Make `private static String read(String file)` package-private (`static String read(String file)`).

- [ ] **Step 6: Convert archives in `ThemeCatalog.load`**

Replace the loop body in `ThemeCatalog.load(Path, Function)`:

```java
        for (ThemeArchive.Found found : contents.themes()) {
            Theme theme;
            try {
                ThemeLoader.Resolved resolved = ThemeManager.loadArchive(found.label(), found.json());
                resolved.warnings().forEach(warning -> problems.add(new Problem(file, warning)));
                EditorScheme.Result scheme = scheme(found, resolved.dark());
                scheme.warnings().forEach(warning -> problems.add(new Problem(file, warning)));
                theme = Theme.of("installed:" + found.label(),
                    new Theme.Archive(file, found.label(), IntellijConversion.convert(resolved, scheme.palette(), found.label())));
            }
            catch (ThemeLoader.ThemeException failure) { problems.add(new Problem(file, failure.getMessage())); continue; }
            catch (RuntimeException failure) { problems.add(new Problem(file, found.label() + " cannot be loaded: " + failure)); continue; }
            themes.add(theme);
        }
```

- [ ] **Step 7: Rename `Theme.LIGHT` everywhere**

```bash
grep -rlE '\bTheme\.LIGHT\b' jasper-app/src | xargs perl -pi -e 's/\bTheme\.LIGHT\b/Theme.INTELLIJ_LIGHT/g'
grep -rnE '\bTheme\.LIGHT\b' jasper-app/src   # expect no output
```

- [ ] **Step 8: Update the appearance tests for documents**

- `ThemeTestSupport`: unchanged API (it already delegates to `ThemeManager.install(Theme)`).
- `ThemeStateTest`: replace the `INSTALLED` constant and the palette expectation of IntelliJ Light:

```java
    private static final Theme INSTALLED = new Theme("installed:fixture", "Fixture", true, SCHEME,
        new Theme.Bundled("fixture.toml", Theme.DARK.source().document()));
```

  and in `theConfiguredThemeResolvesWithItsOwnPalette` use `new ResolvedTheme(Theme.INTELLIJ_LIGHT, Theme.INTELLIJ_LIGHT.palette())`.
- `TerminalColorsThemeTest.changingTheThemeKeepsAFixedTerminalPalette`: the MATCH expectation becomes
  `new ResolvedTheme(Theme.INTELLIJ_LIGHT, Theme.INTELLIJ_LIGHT.palette())`.
- `ThemeManagerTest`:
  - replace `everyBuiltInThemeMatchesItsFile` and `theBuiltInThemesLoadWithoutWarnings` with:

```java
    @Test void everyBuiltInThemeGeneratesUnderItsOwnName() {
        assertThat(ThemeManager.builtIns()).containsExactly(Theme.INTELLIJ_LIGHT, Theme.DARK, Theme.DARCULA);
        for (Theme theme : ThemeManager.builtIns()) {
            GeneratedTheme generated = ThemeManager.generated(theme);
            assertThat(generated.name()).isEqualTo(theme.name());
            assertThat(generated.dark()).isEqualTo(theme.dark());
            assertThat(generated.terminal()).isEqualTo(theme.palette());
        }
        assertThat(Theme.INTELLIJ_LIGHT.id()).isEqualTo("intellij-light");
        assertThat(Theme.DARK.id()).isEqualTo("jasper-dark");
        assertThat(Theme.DARCULA).extracting(Theme::id, Theme::name, Theme::dark).containsExactly("darcula", "Darcula", true);
    }

    @Test void theVendoredIntellijThemesLoadWithoutWarnings() {
        for (String file : List.of("intellij/Light.theme.json", "intellij/darcula.theme.json"))
            assertThat(ThemeManager.loadBuiltIn(file).warnings()).as(file).isEmpty();
    }
```

  (add `import java.util.List;`).
- `ThemeCatalogTest`:
  - `ThemeManager.load(theme.source()).name()` → `theme.source().document().name()`;
  - every `containsExactly(Theme.INTELLIJ_LIGHT, Theme.DARK)` → `containsExactlyElementsOf(ThemeManager.builtIns())`;
  - `.endsWith(Theme.INTELLIJ_LIGHT, Theme.DARK)` → `.endsWith(Theme.INTELLIJ_LIGHT, Theme.DARK, Theme.DARCULA)`;
  - `names()` expectations become `containsExactly("Darcula", "IntelliJ Light", "Jasper Dark")`;
  - `snapshot.find("Good").map(Theme::palette)).contains(Palette.jasperLight())` stays (a light theme without a scheme
    still gets Jasper Light's palette).

- [ ] **Step 9: Run the appearance tests**

Run: `./gradlew :jasper-app:test --tests 'dev.jasper.app.appearance.*'`
Expected: PASS. In particular `intellijLightInstallsTheClassicLightColours` and
`jasperDarkUsesGraphiteSurfacesAndRetainsControlContracts` must pass unchanged: the converted themes install the same
keys as before.

- [ ] **Step 10: Run the whole app suite and fix palette expectations only**

Run: `./gradlew :jasper-app:test`
IntelliJ Light's terminal palette now comes from its editor scheme (Deviation 6). For each failure:
- an assertion that IntelliJ Light's terminal background or ANSI colours equal `Palette.jasperLight()` (or a literal
  from it, such as `#fafafa`) → expect `Theme.INTELLIJ_LIGHT.palette()` (or its value) instead;
- any other failure is a bug in this task: fix the code, not the test.
Record each test file changed this way in the commit message body.

- [ ] **Step 11: Run `check` and commit**

Run: `./gradlew check`
Expected: BUILD SUCCESSFUL.

```bash
git branch --show-current
git add -A jasper-app/src
git commit -m "Theme system: every theme is a document; IntelliJ themes converted

Darcula becomes a bundled theme; Theme.LIGHT is renamed Theme.INTELLIJ_LIGHT, whose terminal
palette now comes from its own editor scheme.

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 6: Jasper Dark as a native theme file

**Files:**
- Create: `jasper-app/src/main/resources/dev/jasper/app/themes/jasper-dark.toml`
- Delete: `jasper-app/src/main/resources/dev/jasper/app/themes/jasper-dark.theme.json`
- Modify: `appearance/Theme.java`, `BuiltInThemes.java`, `ThemeManager.java` (`FILES` loses `"Jasper Dark"`)
- Test: `jasper-app/src/test/java/dev/jasper/app/appearance/ThemeManagerTest.java` (one new test; the parity test is the oracle)

**Interfaces:**
- Consumes: Tasks 1–5.
- Produces: `static Theme BuiltInThemes.toml(String id, String file)`; `Theme.DARK` from `jasper-dark.toml`;
  `BuiltInThemes.GRAPHITE` and `intellij(String, String, Palette)` removed.

- [ ] **Step 1: Write the failing test** (add to `ThemeManagerTest`)

```java
    @Test void jasperDarkIsANativeThemeFile() {
        assertThat(Theme.DARK.source()).isInstanceOfSatisfying(Theme.Bundled.class, bundled -> {
            assertThat(bundled.file()).isEqualTo("jasper-dark.toml");
            assertThat(bundled.document().uiComplete()).isEmpty();
        });
        assertThat(Theme.DARK.palette()).isEqualTo(new Palette(new Color(0xd5dee5), new Color(0x191c20),
            new Color(0xd5dee5), new Color(0x35434e), Palette.jasperDark().ansi()));
        assertThat(ThemeManager.generated(Theme.DARK).tokens().get(Token.TAB_ACTIVE)).isEqualTo(new Color(0x293d51));
    }
```

(add `import dev.jasper.terminal.config.Palette;`).

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :jasper-app:test --tests 'dev.jasper.app.appearance.ThemeManagerTest.jasperDarkIsANativeThemeFile'`
Expected: FAIL: the source file is `jasper-dark.theme.json` and `uiComplete` is `true`.

- [ ] **Step 3: Write `jasper-dark.toml`**

```toml
# Jasper Dark, Jasper's default theme. Seeds and tokens carry the graphite look; [ui] keeps the few control shades
# that are Jasper Dark's own rather than a token's.
name = "Jasper Dark"
dark = true
author = "Jasper"

[colors]
background = "#191c20"
foreground = "#d5dee5"
accent = "#8eb8d2"
cursor = "#d5dee5"
selection = "#35434e"

[colors.normal]
black = "#21222c"
red = "#ff5555"
green = "#50fa7b"
yellow = "#f1fa8c"
blue = "#bd93f9"
magenta = "#ff79c6"
cyan = "#8be9fd"
white = "#f8f8f2"

[colors.bright]
black = "#6272a4"
red = "#ff6e6e"
green = "#69ff94"
yellow = "#ffffa5"
blue = "#d6acff"
magenta = "#ff92df"
cyan = "#a4ffff"
white = "#ffffff"

[tokens]
surface = "#21252a"
surface_raised = "#21252a"
surface_sunken = "#191c20"
title_bar = "#25292e"
status_bar = "#25292e"
sidebar = "#191c20"
text = "#d5dee5"
text_muted = "#a7b2bf"
text_disabled = "#8b929f"
text_on_accent = "#ffffff"
link = "#8eb8d2"
border = "#343a42"
separator = "#343a42"
divider = "#77808f"
focus_ring = "#8eb8d2"
hover = "#323942"
pressed = "#3e4451"
selection = "#5e7293"
selection_text = "#ffffff"
selection_inactive = "#5e7293"
text_selection = "#3e4451"
text_selection_text = "#e6e9ef"
tab_bar = "#25292e"
tab_active = "#293d51"
tab_active_text = "#d5dee5"
tab_active_border = "#4c7291"
tab_hover = "#2c3036"
tab_underline = "#8eb8d2"
tab_underline_inactive = "#747a80"
input_background = "#25292e"
button_background = "#3d424b"
button_border = "#464c55"
button_text = "#a7aebb"
button_default = "#6b80a1"
scrollbar_thumb = "#4d5262"
scrollbar_track = "#2b2e37"
accent = "#8eb8d2"
success = "#a8c58d"
warning = "#e5c07b"
error = "#ff858d"
error_background = "#5c3b3b"
editor_background = "#191c20"
editor_foreground = "#d5dee5"
editor_caret = "#d5dee5"
editor_selection = "#5e7293"
editor_current_line = "#25292e"
editor_gutter = "#191c20"
editor_line_numbers = "#8b929f"

[ui]
"@cellFocusColor" = "#545c6e"
"CheckBox.icon.background" = "#34373e"
"CheckBox.icon.focusedBackground" = "#61afef4d"
"Label.disabledForeground" = "#7e8491"
"ComboBox.background" = "#333841"
"ComboBox.nonEditableBackground" = "#333841"
"ComboBox.ArrowButton.background" = "#2f333b"
"ComboBox.ArrowButton.nonEditableBackground" = "#333841"
"Spinner.buttonBackground" = "#25272e"
"Spinner.buttonArrowColor" = "#afb2b9"
"Button.default.hoverBackground" = "#7b8dab"
"Button.default.pressedBackground" = "#5e7394"
"ProgressBar.trackColor" = "#3b3f4b"
"CheckBoxMenuItem.icon.checkmarkColor" = "#afb2b9"
"PopupMenu.hoverScrollArrowBackground" = "#343842"
"ToolTip.background" = "#15161a"
"SearchEverywhere.SearchField.background" = "#262a2f"
"Jasper.headerHighlight" = "#343a42"
```

- [ ] **Step 4: Load it**

In `BuiltInThemes`, delete `GRAPHITE` and `intellij(String, String, Palette)`, and add:

```java
    /** A Jasper theme file Jasper ships; it may extend nothing but itself standing alone. */
    static Theme toml(String id, String file) {
        String text = ThemeManager.read(file);
        if (text == null) throw new IllegalStateException("Missing bundled theme " + file);
        return Theme.of(id, new Theme.Bundled(file, ThemeResolver.resolve(ThemeToml.parse(text, file))));
    }
```

In `Theme`, `DARK` becomes `BuiltInThemes.toml("jasper-dark", "jasper-dark.toml")`. In `ThemeManager.FILES`, delete
`Map.entry("Jasper Dark", "jasper-dark.theme.json")`. Delete `jasper-dark.theme.json`. Remove now-unused imports.

- [ ] **Step 5: Run the parity oracle**

Run: `./gradlew :jasper-app:test --tests 'dev.jasper.app.appearance.*'`
Expected: PASS, above all `jasperDarkUsesGraphiteSurfacesAndRetainsControlContracts` (130 keys of today's Jasper
Dark). If one of its keys differs, the template or `jasper-dark.toml` disagrees with the retired JSON for that key:
put the retired JSON's literal value for that key into `jasper-dark.toml`'s `[ui]` (see `git show HEAD:jasper-app/src/main/resources/dev/jasper/app/themes/jasper-dark.theme.json`)
and note it in the commit body. Never change `TODAYS_DARK`.

- [ ] **Step 6: Run `check` and commit**

Run: `./gradlew check` — expected BUILD SUCCESSFUL.

```bash
git branch --show-current
git add -A jasper-app/src
git commit -m "Theme system: Jasper Dark is a native theme file

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 7: Jasper Light

**Files:**
- Create: `jasper-app/src/main/resources/dev/jasper/app/themes/jasper-light.toml`
- Modify: `appearance/Theme.java` (`JASPER_LIGHT`), `ThemeManager.java` (`builtIns`)
- Test: `jasper-app/src/test/java/dev/jasper/app/appearance/JasperLightTest.java`; `ThemeManagerTest`, `ThemeCatalogTest` built-in lists

**Interfaces:**
- Produces: `Theme.JASPER_LIGHT` (id `jasper-light`, name `Jasper Light`); `ThemeManager.builtIns()` =
  `[JASPER_LIGHT, DARK, INTELLIJ_LIGHT, DARCULA]`.

- [ ] **Step 1: Write the failing test**

```java
package dev.jasper.app.appearance;

import com.formdev.flatlaf.FlatLaf;
import dev.jasper.app.testsupport.EdtTestExtension;
import dev.jasper.terminal.config.Palette;
import java.awt.Color;
import java.util.List;
import javax.swing.LookAndFeel;
import javax.swing.UIManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import static org.assertj.core.api.Assertions.*;

@ExtendWith(EdtTestExtension.class)
class JasperLightTest {
    private static final GeneratedTheme LIGHT = ThemeManager.generated(Theme.JASPER_LIGHT);

    private static Color token(Token token) { return LIGHT.tokens().get(token); }

    @Test void jasperLightIsABundledNativeLightThemeWithJasperLightsTerminal() {
        assertThat(Theme.JASPER_LIGHT).extracting(Theme::id, Theme::name, Theme::dark)
            .containsExactly("jasper-light", "Jasper Light", false);
        assertThat(Theme.JASPER_LIGHT.palette()).isEqualTo(Palette.jasperLight());
        assertThat(ThemeManager.builtIns()).containsExactly(Theme.JASPER_LIGHT, Theme.DARK, Theme.INTELLIJ_LIGHT, Theme.DARCULA);
    }

    @Test void itsTextAndStatusColoursMeetJaspersFloors() {
        for (Token background : List.of(Token.SURFACE, Token.TITLE_BAR, Token.SIDEBAR, Token.INPUT_BACKGROUND)) {
            assertThat(ChromeKeys.contrast(token(Token.TEXT), token(background))).as("text on " + background.key()).isGreaterThanOrEqualTo(4.5);
            assertThat(ChromeKeys.contrast(token(Token.TEXT_MUTED), token(background))).as("muted on " + background.key()).isGreaterThanOrEqualTo(3);
        }
        assertThat(ChromeKeys.contrast(token(Token.LINK), token(Token.SURFACE))).isGreaterThanOrEqualTo(4.5);
        for (Token status : List.of(Token.SUCCESS, Token.WARNING, Token.ERROR)) {
            assertThat(ChromeKeys.contrast(token(status), token(Token.STATUS_BAR))).as(status.key()).isGreaterThanOrEqualTo(4.5);
            assertThat(ChromeKeys.contrast(token(status), token(Token.SURFACE))).as(status.key()).isGreaterThanOrEqualTo(4.5);
        }
        assertThat(ChromeKeys.contrast(token(Token.SELECTION_TEXT), token(Token.SELECTION))).isGreaterThanOrEqualTo(4.5);
        assertThat(ChromeKeys.contrast(token(Token.TEXT_ON_ACCENT), token(Token.BUTTON_DEFAULT))).isGreaterThanOrEqualTo(4.5);
        assertThat(ChromeKeys.contrast(token(Token.DIVIDER), Palette.jasperLight().background())).isGreaterThanOrEqualTo(3);
    }

    @Test void itInstallsAsALightLookAndFeel() throws Exception {
        LookAndFeel original = UIManager.getLookAndFeel();
        try {
            assertThat(ThemeManager.install(Theme.JASPER_LIGHT)).isTrue();
            assertThat(UIManager.getLookAndFeel().getName()).isEqualTo("Jasper Light");
            assertThat(FlatLaf.isLafDark()).isFalse();
            assertThat(UIManager.getColor("Panel.background")).isEqualTo(new Color(0xf3f4f6));
            assertThat(UIManager.getColor("List.background")).isEqualTo(Color.WHITE);
            assertThat(UIManager.getColor("Jasper.titleBackground")).isEqualTo(new Color(0xe9ebef));
            assertThat(UIManager.getColor("Jasper.tabSelectedBackground")).isEqualTo(Color.WHITE);
        } finally { UIManager.setLookAndFeel(original); }
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :jasper-app:test --tests 'dev.jasper.app.appearance.JasperLightTest'`
Expected: FAIL to compile: `Theme.JASPER_LIGHT` not found.

- [ ] **Step 3: Write `jasper-light.toml`**

```toml
# Jasper Light: a soft grey chrome around a white workspace, with Jasper Dark's steel-blue accent darkened for light
# backgrounds. The terminal keeps Jasper's light palette (One Light).
name = "Jasper Light"
dark = false
author = "Jasper"

[colors]
background = "#fafafa"
foreground = "#383a42"
accent = "#3d7aa8"
cursor = "#526fff"
selection = "#d5def5"

[colors.normal]
black = "#383a42"
red = "#e45649"
green = "#50a14f"
yellow = "#c18401"
blue = "#4078f2"
magenta = "#a626a4"
cyan = "#0184bc"
white = "#a0a1a7"

[colors.bright]
black = "#696c77"
red = "#ca4035"
green = "#3d7d3b"
yellow = "#986801"
blue = "#315fc4"
magenta = "#87218b"
cyan = "#006b96"
white = "#ffffff"

[tokens]
surface = "#f3f4f6"
surface_raised = "#ffffff"
surface_sunken = "#ffffff"
title_bar = "#e9ebef"
status_bar = "#e9ebef"
sidebar = "#f7f8fa"
text = "#2b2f36"
text_muted = "#5f6672"
text_disabled = "#9097a2"
text_on_accent = "#ffffff"
link = "#2f6a96"
border = "#d3d7dd"
separator = "#dfe2e6"
divider = "#868d98"
focus_ring = "#3d7aa8"
hover = "#e3e6eb"
pressed = "#d6dae1"
selection = "#d4e4f2"
selection_text = "#1f2328"
selection_inactive = "#e3e8ee"
text_selection = "#cfe0f2"
text_selection_text = "#1f2328"
tab_bar = "#e9ebef"
tab_active = "#ffffff"
tab_active_text = "#2b2f36"
tab_active_border = "#9dbbd6"
tab_hover = "#dfe3e8"
tab_underline = "#3d7aa8"
tab_underline_inactive = "#9aa3ae"
input_background = "#ffffff"
button_background = "#ffffff"
button_border = "#c5cad2"
button_text = "#2b2f36"
button_default = "#3d7aa8"
scrollbar_thumb = "#c2c7cf"
scrollbar_track = "#eef0f3"
accent = "#3d7aa8"
success = "#2f6b33"
warning = "#8a5d00"
error = "#b8322a"
error_background = "#f9d9d9"
editor_background = "#fafafa"
editor_foreground = "#383a42"
editor_caret = "#526fff"
editor_selection = "#d5def5"
editor_current_line = "#f1f3f5"
editor_gutter = "#fafafa"
editor_line_numbers = "#9097a2"
```

- [ ] **Step 4: Register it**

In `Theme`, above `DARK`:

```java
    /** Jasper's own light theme. */
    public static final Theme JASPER_LIGHT = BuiltInThemes.toml("jasper-light", "jasper-light.toml");
```

In `ThemeManager.builtIns()`: `return List.of(Theme.JASPER_LIGHT, Theme.DARK, Theme.INTELLIJ_LIGHT, Theme.DARCULA);`
and its JavaDoc `/** The bundled themes: Jasper's own, then IntelliJ's. */`.

Update the built-in expectations: `ThemeManagerTest.everyBuiltInThemeGeneratesUnderItsOwnName` →
`containsExactly(Theme.JASPER_LIGHT, Theme.DARK, Theme.INTELLIJ_LIGHT, Theme.DARCULA)`; `ThemeCatalogTest` names →
`containsExactly("Darcula", "IntelliJ Light", "Jasper Dark", "Jasper Light")`, and `.endsWith(...)` →
`.endsWith(Theme.JASPER_LIGHT, Theme.DARK, Theme.INTELLIJ_LIGHT, Theme.DARCULA)`.

- [ ] **Step 5: Run tests**

Run: `./gradlew :jasper-app:test --tests 'dev.jasper.app.appearance.*'`
Expected: PASS. If a floor assertion fails, change that token's value in `jasper-light.toml` (darken text-like colours
toward `#2b2f36`), never the floor.

- [ ] **Step 6: Run `check` and commit**

Run: `./gradlew check` — expected BUILD SUCCESSFUL.

```bash
git branch --show-current
git add -A jasper-app/src
git commit -m "Theme system: Jasper Light

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 8: User theme files in the themes folder

**Files:**
- Modify: `appearance/Theme.java` (`UserFile` source), `ThemeCatalog.java`, `application/ConfigurationController.java`
- Test: `jasper-app/src/test/java/dev/jasper/app/appearance/ThemeCatalogTest.java` (new tests)

**Interfaces:**
- Consumes: Tasks 1–7.
- Produces: `record Theme.UserFile(Path file, ThemeDocument document) implements Source`;
  `ThemeCatalog.Problem(Path file, int line, String message)` with a `Problem(Path, String)` constructor (line 0);
  `ThemeCatalog.MAX_THEME_FILE = 256 * 1024`.

- [ ] **Step 1: Write the failing tests** (add to `ThemeCatalogTest`)

```java
    @Test void aThemeFileInTheFolderIsAThemeThatMayExtendABuiltIn() throws Exception {
        Files.createDirectories(themes());
        Files.writeString(themes().resolve("mine.toml"), """
            name = "Mine"
            extends = "Jasper Dark"
            [tokens]
            tab_active = "#123456"
            """);
        var snapshot = catalog().scan();
        assertThat(snapshot.problems()).isEmpty();
        Theme mine = snapshot.find("mine").orElseThrow();
        assertThat(mine.id()).isEqualTo("user:mine.toml");
        assertThat(mine.dark()).isTrue();
        assertThat(mine.palette()).isEqualTo(Theme.DARK.palette());
        assertThat(mine.source()).isInstanceOfSatisfying(Theme.UserFile.class,
            user -> assertThat(user.file()).isEqualTo(themes().resolve("mine.toml")));
        assertThat(ThemeGenerator.generate(mine.source().document()).tokens().get(Token.TAB_ACTIVE)).isEqualTo(new Color(0x123456));
        assertThat(snapshot.themes()).as("user themes come first").startsWith(mine);
    }

    @Test void aThemeFileMayReplaceABuiltInThemeAndExtendIt() throws Exception {
        Files.createDirectories(themes());
        Files.writeString(themes().resolve("dark.toml"), "name = \"Jasper Dark\"\nextends = \"Jasper Dark\"\n[colors]\naccent = \"#ff8800\"\n");
        var snapshot = catalog().scan();
        assertThat(snapshot.problems()).isEmpty();
        assertThat(snapshot.find("Jasper Dark").map(Theme::source)).get().isInstanceOf(Theme.UserFile.class);
        assertThat(snapshot.themes()).doesNotContain(Theme.DARK);
    }

    @Test void aThemeFileMayExtendAPluginTheme() throws Exception {
        plugin(themes().resolve("fixture.jar"), "Fixture Dark", true, "#123456", "101820");
        Files.writeString(themes().resolve("tweak.toml"), "name = \"Tweak\"\nextends = \"Fixture Dark\"\n");
        var snapshot = catalog().scan();
        assertThat(snapshot.problems()).isEmpty();
        Theme tweak = snapshot.find("Tweak").orElseThrow();
        assertThat(tweak.palette().background()).isEqualTo(new Color(0x101820));
        assertThat(tweak.source().document().uiComplete()).contains(true);
    }

    @Test void aBrokenThemeFileIsAProblemOnItsLine() throws Exception {
        Files.createDirectories(themes());
        Files.writeString(themes().resolve("bad.toml"), "name = \"Bad\"\n[colors]\nbackground = \"blue\"\n");
        Files.writeString(themes().resolve("orphan.toml"), "name = \"Orphan\"\n\nextends = \"Nowhere\"\n");
        var snapshot = catalog().scan();
        assertThat(snapshot.find("Bad")).isEmpty();
        assertThat(snapshot.find("Orphan")).isEmpty();
        assertThat(snapshot.problems()).extracting(ThemeCatalog.Problem::line, problem -> problem.file().getFileName().toString())
            .containsExactlyInAnyOrder(tuple(3, "bad.toml"), tuple(3, "orphan.toml"));
        assertThat(snapshot.problems()).extracting(ThemeCatalog.Problem::message)
            .anySatisfy(message -> assertThat(message).startsWith("bad.toml:3: ").contains("must be a colour"))
            .anySatisfy(message -> assertThat(message).startsWith("orphan.toml:3: ").contains("\"Nowhere\""));
    }

    @Test void anOversizedThemeFileIsAProblem() throws Exception {
        Files.createDirectories(themes());
        Files.writeString(themes().resolve("huge.toml"), "name = \"Huge\"\n# " + "x".repeat(ThemeCatalog.MAX_THEME_FILE));
        var snapshot = catalog().scan();
        assertThat(snapshot.find("Huge")).isEmpty();
        assertThat(snapshot.problems()).singleElement().satisfies(problem -> assertThat(problem.message()).contains("larger than 256 KiB"));
    }
```

(add `import static org.assertj.core.api.Assertions.tuple;` if not covered by the existing `Assertions.*` import.)

- [ ] **Step 2: Run tests to verify they fail**

Run: `./gradlew :jasper-app:test --tests 'dev.jasper.app.appearance.ThemeCatalogTest'`
Expected: FAIL to compile: no `Theme.UserFile`, `Problem.line()`, `MAX_THEME_FILE`.

- [ ] **Step 3: Add the source**

In `Theme`, change `permits Bundled, Archive` to `permits Bundled, UserFile, Archive` and add:

```java
    /** A Jasper theme file in the {@code themes} folder beside {@code config.toml}. */
    public record UserFile(Path file, ThemeDocument document) implements Source {
        public UserFile { Objects.requireNonNull(file); Objects.requireNonNull(document); }
    }
```

- [ ] **Step 4: Read theme files in `ThemeCatalog`**

1. `Problem` gains a line:

```java
    /** A problem with one file in the themes folder, shown as a configuration warning; {@code line} is 0 when unknown. */
    public record Problem(Path file, int line, String message) {
        public Problem { Objects.requireNonNull(file); Objects.requireNonNull(message); }
        public Problem(Path file, String message) { this(file, 0, message); }
    }
```

2. Add `/** Theme files larger than this are refused. */ public static final int MAX_THEME_FILE = 256 * 1024;`
   and change the class JavaDoc's first sentence to "Every theme Jasper can install: the Jasper theme files and theme
   plugins in the {@code themes} folder beside {@code config.toml}, then the built-in themes."
3. `Read` becomes `private record Read(Stamp stamp, List<Theme> themes, ThemeDocument document, List<Problem> problems) {}`
   (`document` is a theme file's parsed, unresolved document; null for an archive or a broken file).
4. Replace `scan()` and `read(Path)` with:

```java
    /** Reads the themes folder, creating it when missing, and re-reads only files whose size or modified time changed. */
    public synchronized Snapshot scan() {
        if (directory == null) return latest;
        var problems = new ArrayList<Problem>();
        var next = new HashMap<Path, Read>();
        var found = new ArrayList<Map.Entry<Path, Read>>();
        for (Path file : files(problems)) {
            String lower = file.getFileName().toString().toLowerCase(Locale.ROOT);
            if (lower.endsWith(".theme.json")) {
                problems.add(new Problem(file, "Standalone .theme.json files aren't supported; install the theme plugin's .jar or .zip instead."));
                continue;
            }
            boolean archive = lower.endsWith(".jar") || lower.endsWith(".zip");
            if (!archive && !lower.endsWith(".toml")) continue;
            Read read;
            // Nothing one file does may stop the scan: it becomes that file's problem.
            try { read = read(file, archive); }
            catch (RuntimeException | OutOfMemoryError | StackOverflowError failure) {
                problems.add(new Problem(file, file.getFileName() + " cannot be read: " + failure));
                continue;
            }
            if (read == null) continue;
            next.put(file, read);
            problems.addAll(read.problems());
            found.add(Map.entry(file, read));
        }
        reads = next;
        // "extends" may name any theme: the folder's, in file order, then the built-in themes.
        var documents = new ArrayList<ThemeDocument>();
        for (var entry : found) {
            if (entry.getValue().document() != null) documents.add(entry.getValue().document());
            entry.getValue().themes().forEach(theme -> documents.add(theme.source().document()));
        }
        ThemeManager.builtIns().forEach(theme -> documents.add(theme.source().document()));
        java.util.function.Function<String, List<ThemeDocument>> candidates =
            name -> documents.stream().filter(document -> key(document.name()).equals(key(name))).toList();
        var installed = new ArrayList<Theme>();
        var owners = new HashMap<String, Path>();
        for (var entry : found) {
            Path file = entry.getKey();
            List<Theme> provided = entry.getValue().themes();
            ThemeDocument document = entry.getValue().document();
            if (document != null) {
                try { provided = List.of(Theme.of("user:" + file.getFileName(), new Theme.UserFile(file, ThemeResolver.resolve(document, candidates)))); }
                catch (ThemeFileException failure) { problems.add(new Problem(file, failure.line(), failure.getMessage())); continue; }
            }
            for (Theme theme : provided) {
                Path owner = owners.putIfAbsent(key(theme.name()), file);
                if (owner == null) installed.add(theme);
                else if (owner.equals(file)) problems.add(new Problem(file, "Theme \"" + theme.name() + "\" is provided twice; the first is used."));
                else problems.add(new Problem(file, "Theme \"" + theme.name() + "\" is also installed from " + owner.getFileName() + ", which is used."));
            }
        }
        var themes = new ArrayList<>(installed);
        for (Theme builtIn : ThemeManager.builtIns()) if (!owners.containsKey(key(builtIn.name()))) themes.add(builtIn);
        return latest = new Snapshot(themes, problems);
    }

    /** The file's themes or document, from the last read when its size and modified time are unchanged; null when it vanished. */
    private Read read(Path file, boolean archive) {
        Stamp stamp;
        try {
            var attributes = Files.readAttributes(file, BasicFileAttributes.class);
            stamp = new Stamp(attributes.size(), attributes.lastModifiedTime());
        } catch (IOException | SecurityException failure) {
            return null;
        }
        Read previous = reads.get(file);
        if (previous != null && previous.stamp().equals(stamp)) return previous;
        if (archive) {
            Loaded loaded = load(file, reader);
            return new Read(stamp, loaded.themes(), null, loaded.problems());
        }
        String name = file.getFileName().toString();
        if (stamp.size() > MAX_THEME_FILE)
            return new Read(stamp, List.of(), null, List.of(new Problem(file, name + " is larger than 256 KiB.")));
        try {
            return new Read(stamp, List.of(), ThemeToml.parse(Files.readString(file), name), List.of());
        } catch (ThemeFileException failure) {
            return new Read(stamp, List.of(), null, List.of(new Problem(file, failure.line(), failure.getMessage())));
        } catch (IOException failure) {
            return new Read(stamp, List.of(), null, List.of(new Problem(file, name + " cannot be read: " + failure)));
        }
    }
```

5. Remove the now-unused `Loaded`-only paths only if the compiler reports them unused; `inspect` keeps using `load`.

- [ ] **Step 5: Show the line in configuration warnings**

In `ConfigurationController.configureThemes`, the catalog-problem diagnostic becomes:

```java
        for (ThemeCatalog.Problem problem : available.problems())
            problems.add(new ConfigDiagnostic(ConfigDiagnostic.Severity.WARNING, problem.file(), problem.line(), 0, "", problem.message()));
```

- [ ] **Step 6: Run tests**

Run: `./gradlew :jasper-app:test --tests 'dev.jasper.app.appearance.*' --tests 'dev.jasper.app.workspace.ConfigurationControllerTest'`
Expected: PASS, including the existing archive tests (`aRescanRereadsOnlyFilesWhoseSizeOrTimeChanged`,
`onlyPluginArchivesAreReadAndLooseThemeFilesAreExplained`).

- [ ] **Step 7: Run `check` and commit**

Run: `./gradlew check` — expected BUILD SUCCESSFUL.

```bash
git branch --show-current
git add -A jasper-app/src
git commit -m "Theme system: Jasper theme files in the themes folder

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 9: Documentation and status

**Files:**
- Modify: `docs/themes.md`, `docs/configuration.md`, `config.example.toml`, `jasper-app/src/main/java/dev/jasper/app/config/CoreSettings.java`,
  `docs/app-architecture.md`, `docs/app-maintenance.md`, `docs/STATUS.md`, this plan's status line

- [ ] **Step 1: `docs/themes.md`**

Replace the opening paragraph (lines 3–5) with:

```markdown
Jasper's themes are Jasper theme files: TOML that colours the whole app, the terminal palette and (from a later
release) syntax highlighting. Four are built in: **Jasper Dark** (the default), **Jasper Light**, and IntelliJ's
classic **IntelliJ Light** and **Darcula**, converted. Theme plugins from the JetBrains Marketplace can still be
installed; Jasper converts them when it reads them.
```

Insert after the "Choosing a theme" section:

````markdown
## Writing a theme

Put a `.toml` file in the `themes` folder beside `config.toml` (`~/.config/jasper/themes/` on macOS) and name it in
`ui.theme.name`. Jasper rereads the folder when the configuration is reloaded. The smallest theme names its seed colours:

```toml
name = "Mocha"

[colors]
background = "#1e1e2e"
foreground = "#cdd6f4"
accent     = "#89b4fa"

[colors.normal]
black = "#45475a"
red = "#f38ba8"
green = "#a6e3a1"
yellow = "#f9e2af"
blue = "#89b4fa"
magenta = "#f5c2e7"
cyan = "#94e2d5"
white = "#bac2de"
```

Everything else is derived. A theme can also set:
- `dark` (otherwise inferred from the background), `author`, `license`, `source`, `commit`;
- `extends = "<theme>"`: start from any other theme and change only what you write; a file may even be named like the
  theme it extends ("Jasper Dark" extending "Jasper Dark") to replace it;
- `cursor`, `selection` and `[colors.bright]` (otherwise each bright colour is its normal colour lightened, or darkened
  on a light theme);
- `[tokens]`: Jasper's semantic colours, any subset of `surface`, `surface_raised`, `surface_sunken`, `title_bar`,
  `status_bar`, `sidebar`, `text`, `text_muted`, `text_disabled`, `text_on_accent`, `link`, `border`, `separator`,
  `divider`, `focus_ring`, `hover`, `pressed`, `selection`, `selection_text`, `selection_inactive`, `text_selection`,
  `text_selection_text`, `tab_bar`, `tab_active`, `tab_active_text`, `tab_active_border`, `tab_hover`,
  `tab_underline`, `tab_underline_inactive`, `input_background`, `button_background`, `button_border`, `button_text`,
  `button_default`, `scrollbar_thumb`, `scrollbar_track`, `accent`, `success`, `warning`, `error`,
  `error_background`, `editor_background`, `editor_foreground`, `editor_caret`, `editor_selection`,
  `editor_current_line`, `editor_gutter`, `editor_line_numbers`;
- `[syntax]`: `keyword`, `type`, `function`, `variable`, `constant`, `number`, `string`, `comment`, `doc_comment`,
  `annotation`, `operator`, `punctuation`, `tag`, `attribute`, `regex`, `error`, each a colour or
  `{ color = "#…", bold = true, italic = true }`;
- `[ui]`: raw FlatLaf or IntelliJ keys (`"Button.arc" = 6`), laid over everything else.

Derived text colours are adjusted until they are readable (4.5:1 for text, 3:1 for muted text); colours you write are
used as written. Colours are `#RRGGBB` or `#RRGGBBAA`. A mistake (an unknown key, a bad colour, a missing seed, an
`extends` that names no theme or loops) is a configuration warning naming the file and line, and that theme is skipped.
````

- [ ] **Step 2: `docs/configuration.md`, `config.example.toml`, `CoreSettings`**

- In `docs/configuration.md` "## Theme", the list becomes:

```markdown
- `"Jasper Dark"` (the default) or `"Jasper Light"`;
- `"IntelliJ Light"` or `"Darcula"` (IntelliJ's classic themes, converted);
- or a Jasper theme file or theme plugin in the `themes` folder beside this file.
```

  and in the settings table row for `ui.theme.name`, the third cell becomes "A built-in theme, or a theme file or plugin in the `themes` folder", keeping the existing link to Themes after it.
- `config.example.toml` line 119: `# Live across open windows: "Jasper Dark", "Jasper Light", "IntelliJ Light", "Darcula", or a theme in the themes folder.`
- `CoreSettings.UI_THEME_NAME` description:

```java
        .description("The UI theme by name: \"Jasper Dark\" (the default), \"Jasper Light\", \"IntelliJ Light\",\n"
            + "\"Darcula\", or a theme file or theme plugin in the themes folder beside this file. Switches live across windows.").build();
```

  Then run `./gradlew :jasper-app:test --tests '*Config*'` and update any generated-config or example expectation the
  wording change breaks (tests that compare `config.example.toml` with generated text).

- [ ] **Step 3: Architecture and maintenance docs**

- `docs/app-architecture.md`: replace the sentence "The built-ins are `Theme.LIGHT` … (`jasper-dark.theme.json`)." with:

```markdown
Every theme is generated from a Jasper theme document (`ThemeToml` → `ThemeResolver` for `extends` →
`ThemeGenerator`): missing seeds, the 48 tokens and the syntax roles are derived with contrast floors, and
`UiTemplate` maps tokens onto FlatLaf, IntelliJ and `Jasper.*` keys under the theme's own `[ui]` keys. The generated
keys install through the IntelliJ path above. The built-ins are `Theme.DARK` (`themes/jasper-dark.toml`),
`Theme.JASPER_LIGHT` (`themes/jasper-light.toml`), and `Theme.INTELLIJ_LIGHT` and `Theme.DARCULA`, converted from the
vendored IntelliJ files by `IntellijConversion` as `ui_complete` documents (their own keys install unchanged).
```

  and in the catalog bullet list add "- **Theme files:** `.toml` files in the folder are Jasper themes; `extends` may
  name any theme in the folder or built in." and change "**Installing:** a `Theme` records its source, a bundled
  resource or an archive" to "**Installing:** a `Theme` records its source (bundled, a theme file, or an archive) and
  its resolved document".
- `docs/app-maintenance.md` "## Themes": replace the first sentence with "To change Jasper Dark or Jasper Light, edit
  `jasper-app/src/main/resources/dev/jasper/app/themes/jasper-dark.toml` or `jasper-light.toml`: seeds and tokens
  first, `[ui]` only for a key no token covers. To change what a token colours for every theme, edit `UiTemplate`; to
  change a derivation or floor, `ThemeGenerator`." and add `ThemeGeneratorTest`, `ThemeTomlTest` and `JasperLightTest`
  to the list of tests to run.

- [ ] **Step 4: `docs/STATUS.md` and the plan banner**

Add at the top of "## Current state" (with today's date in the heading if the section is dated):

```markdown
- **Theme system plan 1: format and generator** (branch `claude/theme-system`, spec
  `docs/superpowers/specs/2026-10-01-jasper-theme-system-design.md`, plan
  `docs/superpowers/plans/2026-10-01-theme-system-1-format-generator.md`): themes are Jasper TOML files generated into
  FlatLaf keys (`ThemeToml`, `ThemeResolver`, `ThemeGenerator`, `UiTemplate`). Jasper Dark is native
  (`jasper-dark.toml`, parity with the retired JSON), Jasper Light is new, IntelliJ Light and Darcula are converted
  bundled themes, installed IntelliJ plugins are converted in memory, and `.toml` files in the `themes` folder are
  themes (problems carry their line). Deviations (plan banner): 48 tokens; new `ui_complete` key; no contrast floor
  for borders; Jasper Light keeps the One Light terminal palette; the converter stays in `appearance` until plan 4;
  `Theme.LIGHT` became `Theme.INTELLIJ_LIGHT`, whose terminal palette now comes from its own scheme. Next: plan 2
  (workspace zone). GUI checks for the user: Jasper Dark unchanged; Jasper Light, IntelliJ Light and Darcula across
  the window, Settings and dialogs; a hand-written theme file in `themes/` (including a broken one's warning).
```

Change this plan's `**Status:**` line to `**Status:** Executed <date> through <last commit>; ./gradlew check green.`

- [ ] **Step 5: Verify and commit**

Run: `./gradlew check` — expected BUILD SUCCESSFUL. Then run the source-hygiene check from `AGENTS.md` over the new
files:

```bash
python3 - jasper-app/src/main/java/dev/jasper/app/appearance/*.java <<'PY'
import pathlib, sys
for name in sys.argv[1:]:
    text = pathlib.Path(name).read_text(encoding="utf-8")
    bad = sum(1 for c in text if 0xD800 <= ord(c) <= 0xDFFF or 0xE000 <= ord(c) <= 0xF8FF or (ord(c) < 0x20 and c not in "\n\t\r") or ord(c) == 0x7f)
    if bad: print(name, "bad chars:", bad)
PY
```

Expected: no output.

```bash
git branch --show-current
git add docs config.example.toml jasper-app/src/main/java/dev/jasper/app/config/CoreSettings.java jasper-app/src/test
git commit -m "Theme system plan 1: documentation and status

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```
