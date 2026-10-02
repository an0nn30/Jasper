# Workspace Glass, Plan 1: Glass Foundation — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Status:** Written 2026-10-02 on `claude/workspace-glass`; not started.

**Goal:** Terminal panes in a Jasper window become translucent, with the desktop behind them blurred, when the workspace theme (for example the new Clear Dark) or the Appearance › Workspace settings ask for it on macOS.

**Architecture:** Workspace themes gain an optional `[workspace]` table (opacity, blur) carried to `GeneratedTheme.glass()`. Two settings override it; `WindowContent` combines both into a `GlassLevels` request. A per-window `WindowGlass` (platform package) makes the native window translucent through two JDK internals, sets the blur through a small JNI bridge (`libjasper-glass.dylib`, private `CGSSetWindowBackgroundBlurRadius`), and reports the opacity actually in effect. Painting follows that reported value only: the rounded workspace host clears its surface, terminal containers stop painting, and each `TerminalPane` and `TerminalView` fills its default background with alpha using the `Src` composite.

**Tech Stack:** Java 25 on JBR 25, Swing/FlatLaf, tomlj, JUnit 5 + AssertJ, Objective-C (clang via `xcrun`), Gradle.

**Spec:** [`docs/superpowers/specs/2026-10-02-jasper-workspace-glass-design.md`](../specs/2026-10-02-jasper-workspace-glass-design.md). Read it before starting.

**Deviations from the spec (approved design; recorded here and in `docs/STATUS.md` at the end):**

1. **Opaque ancestors, one cleared hole.** A second spike (2026-10-02) showed that the window only needs to be non-opaque at the peer. The title bar wrapper, `WindowContent`, its body and the region splits keep painting solid; `WorkspaceRegions`' center host clears its rounded surface with `AlphaComposite.Clear`, and only the components inside it (`TerminalDeck`, `TerminalTab`, its splits, `TerminalPane`, `TerminalView`) change. The spec's rule "every translucent pixel gets exactly one alpha fill" still holds, and fewer components change.
2. **No `TerminalOptions.Builder.backgroundOpacity`.** Opacity is a per-window live property like inactive dim, so the terminal module gets only `TerminalView.setBackgroundOpacity(float)` / `backgroundOpacity()`.
3. **`WindowGlass` watches the frame's `JRootPane`**, not the `Window`, so its decision logic is testable headlessly (a `JFrame` cannot be created in a headless JVM). The platform functions receive the root pane and find its window.

## Global Constraints

- Java 25, JBR 25; always `./gradlew`, never a system `gradle`. Tests are headless; never launch the GUI (`./gradlew :jasper-app:run`) or the benchmark.
- Work only in `.worktrees/workspace-glass` on branch `claude/workspace-glass`. Before every commit run `git branch --show-current` and confirm `claude/workspace-glass`. End every commit message with `Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>`.
- Theme `[workspace]`: `opacity` 0.10–1.00 (absent means 1.0), `blur` 0.0–1.0 (absent means 0), inherited through `extends`; only the workspace theme's values are used.
- Settings: `ui.workspace.opacity` percent 10–100, `ui.workspace.blur` percent 0–100; unset defers to the theme; live; macOS only (rows disabled elsewhere with "Requires macOS.").
- Effective values: `opacity = setting ?? workspaceTheme.opacity ?? 1.0`, `blur = setting ?? workspaceTheme.blur ?? 0`.
- Translucent: only the terminal's default background (grid and pane padding). Explicit cell backgrounds, selection, cursor, search highlights, pane tabs, split dividers, find bar and all chrome stay solid. Blur stays constant (no inactive-window change).
- Blur radius: linear, 0–1 → 0–40 px (`WindowGlass.MAX_BLUR_RADIUS = 40`).
- Any failure (other OS, headless, inaccessible internals, missing library) leaves an opaque window: painting reads only `WindowGlass`'s reported opacity.
- JVM flags, as single tokens (jpackage receives each list element as its own `--java-options`): `--add-opens=java.desktop/java.awt=ALL-UNNAMED` and `--add-exports=java.desktop/java.awt.peer=ALL-UNNAMED`.
- `dev.jasper.app.platform` depends on no other Jasper package; `dev.jasper.app.config` must not depend on `dev.jasper.app.appearance`.
- Source hygiene: no raw control, private-use or unpaired surrogate characters in source (see `AGENTS.md`).

## File map

| File | Responsibility |
| --- | --- |
| `jasper-app/.../appearance/GlassLevels.java` (new) | Effective opacity/blur fractions; `resolve(GlassSettings, GlassLevels)` |
| `jasper-app/.../appearance/WorkspaceGlass.java` (new) | A theme's authored `[workspace]` table |
| `jasper-app/.../appearance/ThemeToml.java`, `ThemeDocument.java`, `ThemeTomlWriter.java`, `GeneratedTheme.java`, `ThemeGenerator.java` | Parse, merge, write and generate `[workspace]` |
| `jasper-app/.../themeimport/ThemeImporter.java`, `ConvertThemes.java` | Keep `[workspace]` on rename; copy the hand-written Clear themes |
| `jasper-app/src/main/resources/dev/jasper/app/themes/clear-dark.toml`, `clear-light.toml`, `index.txt` | The two ported themes |
| `jasper-app/.../config/GlassSettings.java` (new), `CoreSettings.java`, `ConfigSnapshot.java`, `config.example.toml` | The two settings |
| `jasper-app/.../settingsui/CorePages.java` | Appearance › Workspace section |
| `jasper-terminal/.../internal/rendering/TerminalPainter.java`, `view/TerminalView.java` | Translucent default background |
| `jasper-app/.../workspace/WorkspaceRegions.java`, `TerminalTab.java`, `TerminalPane.java`, `WindowContent.java`, `WorkspaceConfiguration.java`, `TerminalWindow.java` | Clear containers, glass leaves, requests and wiring |
| `jasper-app/.../platform/WindowGlass.java`, `MacGlass.java` (new) | Per-window translucency and blur; JDK internals and JNI |
| `jasper-app/src/main/native/MacGlass.m`, `src/test/native/MacGlassTest.m`, `gradle/mac-glass.gradle` (new), `gradle/packaging.gradle`, `jasper-app/build.gradle.kts` | Native bridge, its test, build, packaging, JVM flags |

---

### Task 1: Theme `[workspace]` table

**Files:**
- Create: `jasper-app/src/main/java/dev/jasper/app/appearance/GlassLevels.java`
- Create: `jasper-app/src/main/java/dev/jasper/app/appearance/WorkspaceGlass.java`
- Modify: `jasper-app/src/main/java/dev/jasper/app/appearance/ThemeToml.java`
- Modify: `jasper-app/src/main/java/dev/jasper/app/appearance/ThemeDocument.java`
- Modify: `jasper-app/src/main/java/dev/jasper/app/appearance/ThemeTomlWriter.java`
- Modify: `jasper-app/src/main/java/dev/jasper/app/appearance/GeneratedTheme.java`
- Modify: `jasper-app/src/main/java/dev/jasper/app/appearance/ThemeGenerator.java`
- Modify: `jasper-app/src/main/java/dev/jasper/app/themeimport/ThemeImporter.java`
- Modify: `docs/themes.md`
- Test: `jasper-app/src/test/java/dev/jasper/app/appearance/WorkspaceGlassThemeTest.java` (create)
- Test: `jasper-app/src/test/java/dev/jasper/app/themeimport/ThemeImporterGlassTest.java` (create)

**Interfaces:**
- Produces: `public record GlassLevels(double opacity, double blur)` with `GlassLevels.OPAQUE`, `GlassLevels.MIN_OPACITY = 0.10`, `boolean translucent()`. `public record WorkspaceGlass(Optional<Double> opacity, Optional<Double> blur)` with `WorkspaceGlass.NONE`, `GlassLevels levels()`, `boolean isEmpty()`, package-private `WorkspaceGlass over(WorkspaceGlass base)`. `ThemeDocument.workspace()` (new last record component, `WorkspaceGlass`). `GeneratedTheme.glass()` (new last record component, `GlassLevels`). Both records keep their previous constructors, which pass `WorkspaceGlass.NONE` / `GlassLevels.OPAQUE`.

- [ ] **Step 1: Write the failing theme tests**

Create `jasper-app/src/test/java/dev/jasper/app/appearance/WorkspaceGlassThemeTest.java`:

```java
package dev.jasper.app.appearance;

import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import static org.assertj.core.api.Assertions.*;

class WorkspaceGlassThemeTest {
    private static final String BASE = """
        name = "Glass"
        [colors]
        background = "#212734"
        foreground = "#e6e6e6"
        accent = "#67b5ed"
        [colors.normal]
        black = "#35424c"
        red = "#b45648"
        green = "#6caa71"
        yellow = "#c4ac62"
        blue = "#6d96b4"
        magenta = "#bd7bcd"
        cyan = "#7ccbcd"
        white = "#dee5eb"
        """;

    @Test void aThemeWithoutTheTableIsOpaque() {
        var document = ThemeToml.parse(BASE, "glass.toml");
        assertThat(document.workspace()).isEqualTo(WorkspaceGlass.NONE);
        assertThat(ThemeGenerator.generate(document).glass()).isEqualTo(GlassLevels.OPAQUE);
        assertThat(GlassLevels.OPAQUE.translucent()).isFalse();
    }

    @Test void theTableSetsOpacityAndBlur() {
        var document = ThemeToml.parse(BASE + "[workspace]\nopacity = 0.95\nblur = 0.5\n", "glass.toml");
        assertThat(document.workspace()).isEqualTo(new WorkspaceGlass(Optional.of(0.95), Optional.of(0.5)));
        var glass = ThemeGenerator.generate(document).glass();
        assertThat(glass).isEqualTo(new GlassLevels(0.95, 0.5));
        assertThat(glass.translucent()).isTrue();
    }

    @Test void wholeNumbersAreAccepted() {
        var document = ThemeToml.parse(BASE + "[workspace]\nopacity = 1\nblur = 0\n", "glass.toml");
        assertThat(ThemeGenerator.generate(document).glass()).isEqualTo(new GlassLevels(1, 0));
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
        "opacity = 0.05 | \"opacity\" must be from 0.1 to 1",
        "opacity = 1.5 | \"opacity\" must be from 0.1 to 1",
        "blur = -0.1 | \"blur\" must be from 0 to 1",
        "blur = 2 | \"blur\" must be from 0 to 1",
        "opacity = 'high' | \"opacity\" must be a number",
        "tint = 0.5 | unknown key \"tint\" in [workspace]"})
    void invalidValuesNameTheirLine(String line, String message) {
        String text = BASE + "[workspace]\n" + line + "\n";
        int expectedLine = (int) BASE.lines().count() + 2;
        assertThatThrownBy(() -> ThemeToml.parse(text, "glass.toml"))
            .isInstanceOf(ThemeFileException.class).hasMessageContaining(message)
            .satisfies(failure -> assertThat(((ThemeFileException) failure).line()).isEqualTo(expectedLine));
    }

    @Test void aChildInheritsAndOverridesItsParentsValues() {
        var parent = ThemeToml.parse(BASE + "[workspace]\nopacity = 0.9\nblur = 0.5\n", "parent.toml");
        var child = ThemeToml.parse("name = \"Child\"\nextends = \"Glass\"\n[workspace]\nblur = 0.2\n", "child.toml");
        var resolved = ThemeResolver.resolve(child, name -> name.equals("Glass") ? List.of(parent) : List.of());
        assertThat(ThemeGenerator.generate(resolved).glass()).isEqualTo(new GlassLevels(0.9, 0.2));
    }

    @Test void theWriterRoundTripsTheTable() {
        var document = ThemeToml.parse(BASE + "[workspace]\nopacity = 0.93\nblur = 0.5\n", "glass.toml");
        String written = ThemeTomlWriter.write(document);
        assertThat(written).contains("\n[workspace]\n");
        assertThat(ThemeToml.parse(written, "glass.toml").workspace()).isEqualTo(document.workspace());
        assertThat(ThemeTomlWriter.write(ThemeToml.parse(BASE, "glass.toml"))).doesNotContain("[workspace]");
    }

    @Test void levelsRejectValuesOutsideTheirRanges() {
        assertThatThrownBy(() -> new GlassLevels(0.05, 0)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new GlassLevels(1, 1.5)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new GlassLevels(Double.NaN, 0)).isInstanceOf(IllegalArgumentException.class);
    }
}
```

Create `jasper-app/src/test/java/dev/jasper/app/themeimport/ThemeImporterGlassTest.java`:

```java
package dev.jasper.app.themeimport;

import dev.jasper.app.appearance.ThemeToml;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class ThemeImporterGlassTest {
    @Test void renamingAnImportedThemeKeepsItsWorkspaceTable() {
        var document = ThemeToml.parse("name = \"Original\"\nextends = \"Jasper Dark\"\n[workspace]\nopacity = 0.9\nblur = 0.3\n", "original.toml");
        var renamed = ThemeImporter.rename(document, "Renamed");
        assertThat(renamed.name()).isEqualTo("Renamed");
        assertThat(renamed.workspace().opacity()).isEqualTo(Optional.of(0.9));
        assertThat(renamed.workspace().blur()).isEqualTo(Optional.of(0.3));
    }
}
```

- [ ] **Step 2: Run the tests to verify they fail**

Run: `./gradlew :jasper-app:test --tests "*WorkspaceGlassThemeTest" --tests "*ThemeImporterGlassTest"`
Expected: compilation FAILS (`GlassLevels`, `WorkspaceGlass`, `workspace()`, `glass()` do not exist).

- [ ] **Step 3: Add `GlassLevels` and `WorkspaceGlass`**

Create `jasper-app/src/main/java/dev/jasper/app/appearance/GlassLevels.java`:

```java
package dev.jasper.app.appearance;

/**
 * How translucent the workspace is, as fractions: {@code opacity} from {@link #MIN_OPACITY} to 1 (1 is opaque) and
 * {@code blur} from 0 to 1, Terminal.app's scale for how strongly the desktop behind is blurred.
 *
 * @param opacity the default background's opacity
 * @param blur the blur behind translucent pixels
 */
public record GlassLevels(double opacity, double blur) {
    /** The least opacity a theme or setting may ask for. */
    public static final double MIN_OPACITY = 0.10;
    /** An opaque, unblurred workspace: every theme without a {@code [workspace]} table. */
    public static final GlassLevels OPAQUE = new GlassLevels(1, 0);

    public GlassLevels {
        if (!(opacity >= MIN_OPACITY && opacity <= 1)) throw new IllegalArgumentException("opacity must be from 0.1 to 1");
        if (!(blur >= 0 && blur <= 1)) throw new IllegalArgumentException("blur must be from 0 to 1");
    }

    /** Whether anything behind the workspace shows through. */
    public boolean translucent() { return opacity < 1; }
}
```

Create `jasper-app/src/main/java/dev/jasper/app/appearance/WorkspaceGlass.java`:

```java
package dev.jasper.app.appearance;

import java.util.Objects;
import java.util.Optional;

/**
 * A theme's {@code [workspace]} table as written. Empty values inherit through {@code extends}; still empty, they mean
 * opaque and unblurred.
 *
 * @param opacity the authored opacity, 0.1–1
 * @param blur the authored blur, 0–1
 */
public record WorkspaceGlass(Optional<Double> opacity, Optional<Double> blur) {
    /** No {@code [workspace]} table. */
    public static final WorkspaceGlass NONE = new WorkspaceGlass(Optional.empty(), Optional.empty());

    public WorkspaceGlass { Objects.requireNonNull(opacity); Objects.requireNonNull(blur); }

    /** This table laid over {@code base}: each value this table sets wins. */
    WorkspaceGlass over(WorkspaceGlass base) { return new WorkspaceGlass(opacity.or(base::opacity), blur.or(base::blur)); }

    /** The values a generated theme uses. */
    public GlassLevels levels() { return new GlassLevels(opacity.orElse(1.0), blur.orElse(0.0)); }

    /** Whether the table sets nothing. */
    public boolean isEmpty() { return opacity.isEmpty() && blur.isEmpty(); }
}
```

- [ ] **Step 4: Carry `workspace` on `ThemeDocument`**

In `ThemeDocument.java`, change the record header to add a last component and keep the old constructor:

```java
public record ThemeDocument(String origin, String name, Optional<Boolean> dark, Optional<String> parent, int parentLine,
                            Optional<Boolean> uiComplete, Map<String, String> metadata, Map<Seed, Color> seeds,
                            Map<Integer, Color> ansi, Map<Token, Color> tokens, Map<SyntaxRole, SyntaxStyle> syntax,
                            Map<String, Object> ui, Map<String, Object> icons, WorkspaceGlass workspace) {
```

In the compact constructor, after `Objects.requireNonNull(parent); Objects.requireNonNull(uiComplete);` add:

```java
        Objects.requireNonNull(workspace);
```

Directly after the compact constructor's closing brace add:

```java
    /** A document without a {@code [workspace]} table. */
    public ThemeDocument(String origin, String name, Optional<Boolean> dark, Optional<String> parent, int parentLine,
                         Optional<Boolean> uiComplete, Map<String, String> metadata, Map<Seed, Color> seeds,
                         Map<Integer, Color> ansi, Map<Token, Color> tokens, Map<SyntaxRole, SyntaxStyle> syntax,
                         Map<String, Object> ui, Map<String, Object> icons) {
        this(origin, name, dark, parent, parentLine, uiComplete, metadata, seeds, ansi, tokens, syntax, ui, icons,
            WorkspaceGlass.NONE);
    }
```

In `over(ThemeDocument base)`, replace the `return` statement with:

```java
        return new ThemeDocument(origin, name, dark.or(base::dark), base.parent, base.parentLine,
            uiComplete.or(base::uiComplete), metadata, merged(base.seeds, seeds), merged(base.ansi, ansi),
            merged(base.tokens, tokens), merged(base.syntax, syntax), layered, icons.isEmpty() ? base.icons : icons,
            workspace.over(base.workspace));
```

Also extend the class Javadoc's first sentence block with: `{@code workspace} is the {@code [workspace]} table.`

- [ ] **Step 5: Parse `[workspace]` in `ThemeToml`**

In `ThemeToml.java`, add `"workspace"` to `TOP`:

```java
    private static final Set<String> TOP = Set.of("name", "dark", "extends", "author", "license", "source", "commit",
        "ui_complete", "colors", "tokens", "syntax", "ui", "icons", "workspace");
```

Replace the final `return new ThemeDocument(...)` of `parse` with:

```java
        return new ThemeDocument(origin, name.strip(), file.bool(List.of("dark")), parent.map(String::strip),
            parent.isPresent() ? file.line(List.of("extends")) : 0, file.bool(List.of("ui_complete")), metadata, seeds,
            ansi, tokens, syntax, ui, file.icons(), file.workspace());
```

In the `Reader` record, after the `bool(...)` method add:

```java
        Optional<Double> number(List<String> path, double min, double max) {
            Object value = toml.get(path);
            if (value == null) return Optional.empty();
            double number;
            if (value instanceof Double fraction) number = fraction;
            else if (value instanceof Long whole) number = whole;
            else throw error(path, "\"" + label(path) + "\" must be a number");
            if (!(number >= min && number <= max))
                throw error(path, "\"" + label(path) + "\" must be from " + plain(min) + " to " + plain(max));
            return Optional.of(number);
        }

        private static String plain(double value) { return java.math.BigDecimal.valueOf(value).stripTrailingZeros().toPlainString(); }

        WorkspaceGlass workspace() {
            TomlTable table = table(List.of("workspace"));
            if (table == null) return WorkspaceGlass.NONE;
            for (String key : table.keySet())
                if (!key.equals("opacity") && !key.equals("blur"))
                    throw error(List.of("workspace", key), "unknown key \"" + key + "\" in [workspace]");
            return new WorkspaceGlass(number(List.of("workspace", "opacity"), GlassLevels.MIN_OPACITY, 1),
                number(List.of("workspace", "blur"), 0, 1));
        }
```

- [ ] **Step 6: Write `[workspace]` in `ThemeTomlWriter`**

In `ThemeTomlWriter.write`, directly before `section(out, "ui", document.ui());` add:

```java
        if (!document.workspace().isEmpty()) {
            out.append("\n[workspace]\n");
            document.workspace().opacity().ifPresent(value -> entry(out, "opacity", value));
            document.workspace().blur().ifPresent(value -> entry(out, "blur", value));
        }
```

- [ ] **Step 7: Carry `glass` on `GeneratedTheme` and generate it**

Replace `GeneratedTheme.java`'s record header and body with:

```java
/**
 * A Jasper theme with every value filled in: all tokens and syntax roles, the terminal palette, the ordered UI
 * keys FlatLaf installs (IntelliJ theme {@code ui} entries) and the workspace glass. Immutable and Swing-free.
 */
public record GeneratedTheme(String name, boolean dark, String author, Map<Token, Color> tokens, Map<SyntaxRole, SyntaxStyle> syntax,
                             Palette terminal, Map<String, Object> uiKeys, Map<String, Object> icons, GlassLevels glass) {
    public GeneratedTheme {
        Objects.requireNonNull(name); Objects.requireNonNull(author); Objects.requireNonNull(terminal);
        Objects.requireNonNull(glass);
        tokens = Map.copyOf(tokens);
        syntax = Map.copyOf(syntax);
        uiKeys = Collections.unmodifiableMap(new LinkedHashMap<>(uiKeys));
        icons = Collections.unmodifiableMap(new LinkedHashMap<>(icons));
    }

    /** An opaque theme, for callers that predate workspace glass. */
    public GeneratedTheme(String name, boolean dark, String author, Map<Token, Color> tokens, Map<SyntaxRole, SyntaxStyle> syntax,
                          Palette terminal, Map<String, Object> uiKeys, Map<String, Object> icons) {
        this(name, dark, author, tokens, syntax, terminal, uiKeys, icons, GlassLevels.OPAQUE);
    }
}
```

In `ThemeGenerator.java`, in `withWorkspaceTokens`, replace the `return` with:

```java
        return new GeneratedTheme(original.name(), original.dark(), original.author(), updated.tokens(), updated.syntax(),
            original.terminal(), original.uiKeys(), original.icons(), original.glass());
```

and in `Generation.result()` replace the `return` with:

```java
            return new GeneratedTheme(document.name(), dark, document.metadata().getOrDefault("author", "Jasper"), tokens, syntax,
                new Palette(foreground, background, cursor, selection, ansi), uiKeys(), document.icons(), document.workspace().levels());
```

- [ ] **Step 8: Keep the table when an import is renamed**

In `themeimport/ThemeImporter.java`, replace `rename` with:

```java
    static ThemeDocument rename(ThemeDocument base, String name) {
        return new ThemeDocument(base.origin(), name, base.dark(), base.parent(), base.parentLine(), base.uiComplete(), base.metadata(),
            base.seeds(), base.ansi(), base.tokens(), base.syntax(), base.ui(), base.icons(), base.workspace());
    }
```

- [ ] **Step 9: Run the tests to verify they pass**

Run: `./gradlew :jasper-app:test --tests "*WorkspaceGlassThemeTest" --tests "*ThemeImporterGlassTest" --tests "*ThemeTomlTest" --tests "*ThemeTomlWriterTest" --tests "*ThemeGeneratorTest" --tests "*ThemeResolverTest" --tests "*BundledThemeConversionTest"`
Expected: PASS (the bundled catalog is byte-identical because no bundled theme has a `[workspace]` table).

- [ ] **Step 10: Document the table**

In `docs/themes.md`, under "## Writing a theme", in the bullet list after the `[icons]` bullet and before the `[ui]` bullet, add:

```markdown
- `[workspace]`: `opacity` (0.1–1) and `blur` (0–1, Terminal.app's scale). When this theme is the workspace theme on
  macOS, the terminal and editor default background becomes translucent and the desktop behind it is blurred;
  Settings › Appearance › Workspace can override both. Absent values are inherited through `extends`, then mean
  opaque and unblurred. Only the workspace theme's table is used;
```

- [ ] **Step 11: Commit**

```bash
git branch --show-current   # must print claude/workspace-glass
git add jasper-app/src/main/java/dev/jasper/app/appearance jasper-app/src/main/java/dev/jasper/app/themeimport/ThemeImporter.java \
  jasper-app/src/test/java/dev/jasper/app/appearance/WorkspaceGlassThemeTest.java \
  jasper-app/src/test/java/dev/jasper/app/themeimport/ThemeImporterGlassTest.java docs/themes.md
git commit -m "feat(themes): add the [workspace] opacity and blur table

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 2: Clear Dark and Clear Light

**Files:**
- Create: `jasper-app/src/main/resources/dev/jasper/app/themes/clear-dark.toml`
- Create: `jasper-app/src/main/resources/dev/jasper/app/themes/clear-light.toml`
- Modify: `jasper-app/src/main/resources/dev/jasper/app/themes/index.txt`
- Modify: `jasper-app/src/main/java/dev/jasper/app/themeimport/ConvertThemes.java:39`
- Modify: `jasper-app/src/test/java/dev/jasper/app/appearance/NativeThemeCatalogTest.java:39`
- Modify: `jasper-app/src/test/java/dev/jasper/app/appearance/ThemeManagerTest.java:175`
- Modify: `docs/themes.md:4`, `docs/app-maintenance.md:192-197`, `jasper-app/README.md:83`
- Test: `jasper-app/src/test/java/dev/jasper/app/appearance/ClearThemesTest.java` (create)

**Interfaces:**
- Consumes: `GeneratedTheme.glass()`, `GlassLevels` (Task 1).
- Produces: bundled themes named exactly `Clear Dark` and `Clear Light` (ids `clear-dark`, `clear-light`).

- [ ] **Step 1: Write the failing test**

Create `jasper-app/src/test/java/dev/jasper/app/appearance/ClearThemesTest.java`:

```java
package dev.jasper.app.appearance;

import dev.jasper.terminal.config.Palette;
import java.awt.Color;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

/** Values decoded from Terminal.app's Initial Settings/Clear {Dark,Light}.terminal, converted to sRGB. */
class ClearThemesTest {
    private static GeneratedTheme generated(String name) {
        return ThemeManager.generated(ThemeCatalog.builtIn().scan().find(name).orElseThrow());
    }

    private static String hex(Color color) { return String.format("#%06x", color.getRGB() & 0xffffff); }

    @Test void clearDarkMatchesTerminalAppsProfile() {
        GeneratedTheme theme = generated("Clear Dark");
        assertThat(theme.dark()).isTrue();
        assertThat(theme.glass()).isEqualTo(new GlassLevels(0.95, 0.5));
        Palette palette = theme.terminal();
        assertThat(hex(palette.background())).isEqualTo("#212734");
        assertThat(hex(palette.foreground())).isEqualTo("#e6e6e6");
        assertThat(hex(palette.selection())).isEqualTo("#334e5e");
        assertThat(palette.cursor()).as("no cursor in the profile: the foreground").isEqualTo(palette.foreground());
        assertThat(palette.ansi()).extracting(ClearThemesTest::hex).containsExactlyElementsOf(List.of(
            "#35424c", "#b45648", "#6caa71", "#c4ac62", "#6d96b4", "#bd7bcd", "#7ccbcd", "#dee5eb",
            "#465c6d", "#df6c5a", "#79be7e", "#e5c872", "#67b5ed", "#d389e5", "#84dde0", "#e5eff5"));
    }

    @Test void clearLightMatchesTerminalAppsProfile() {
        GeneratedTheme theme = generated("Clear Light");
        assertThat(theme.dark()).isFalse();
        assertThat(theme.glass()).isEqualTo(new GlassLevels(0.93, 0.5));
        Palette palette = theme.terminal();
        assertThat(hex(palette.background())).isEqualTo("#ffffff");
        assertThat(hex(palette.foreground())).isEqualTo("#3a4851");
        assertThat(hex(palette.selection())).isEqualTo("#e5ecf1");
        assertThat(hex(palette.cursor())).isEqualTo("#919191");
        assertThat(palette.ansi()).extracting(ClearThemesTest::hex).containsExactlyElementsOf(List.of(
            "#2d3840", "#b45648", "#6caa71", "#c4ac62", "#5685a8", "#ad64be", "#69c6c9", "#c1c8cc",
            "#506573", "#df6c5a", "#79be7e", "#e5c872", "#49a2e1", "#d389e5", "#77e1e5", "#d8e1e7"));
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `./gradlew :jasper-app:test --tests "*ClearThemesTest"`
Expected: FAIL with `NoSuchElementException` (no theme named "Clear Dark").

- [ ] **Step 3: Add the two theme files**

Create `jasper-app/src/main/resources/dev/jasper/app/themes/clear-dark.toml`:

```toml
# Clear Dark, ported from the macOS Terminal.app profile of the same name: a translucent, blurred workspace.
# Terminal.app's bold-text colour (#f0f0f0) and font (SF Mono 12) are not theme values in Jasper.
name = "Clear Dark"
dark = true
author = "Jasper"
source = "macOS Terminal.app, Clear Dark profile"

[colors]
background = "#212734"
foreground = "#e6e6e6"
accent = "#67b5ed"
selection = "#334e5e"

[colors.normal]
black = "#35424c"
red = "#b45648"
green = "#6caa71"
yellow = "#c4ac62"
blue = "#6d96b4"
magenta = "#bd7bcd"
cyan = "#7ccbcd"
white = "#dee5eb"

[colors.bright]
black = "#465c6d"
red = "#df6c5a"
green = "#79be7e"
yellow = "#e5c872"
blue = "#67b5ed"
magenta = "#d389e5"
cyan = "#84dde0"
white = "#e5eff5"

[workspace]
opacity = 0.95
blur = 0.5
```

Create `jasper-app/src/main/resources/dev/jasper/app/themes/clear-light.toml`:

```toml
# Clear Light, ported from the macOS Terminal.app profile of the same name: a translucent, blurred workspace.
# Terminal.app's bold-text colour (#313c44) and font (SF Mono 12) are not theme values in Jasper.
name = "Clear Light"
dark = false
author = "Jasper"
source = "macOS Terminal.app, Clear Light profile"

[colors]
background = "#ffffff"
foreground = "#3a4851"
accent = "#49a2e1"
cursor = "#919191"
selection = "#e5ecf1"

[colors.normal]
black = "#2d3840"
red = "#b45648"
green = "#6caa71"
yellow = "#c4ac62"
blue = "#5685a8"
magenta = "#ad64be"
cyan = "#69c6c9"
white = "#c1c8cc"

[colors.bright]
black = "#506573"
red = "#df6c5a"
green = "#79be7e"
yellow = "#e5c872"
blue = "#49a2e1"
magenta = "#d389e5"
cyan = "#77e1e5"
white = "#d8e1e7"

[workspace]
opacity = 0.93
blur = 0.5
```

- [ ] **Step 4: Index them and let the converter copy them**

In `index.txt`, insert two lines directly after `catppuccin-mocha.toml` (line 172), keeping the file sorted:

```
clear-dark.toml
clear-light.toml
```

In `ConvertThemes.java:39`, replace the hand-written list:

```java
        for (String id : List.of("jasper-dark", "jasper-light", "clear-dark", "clear-light")) {
```

- [ ] **Step 5: Update the catalog counts**

In `NativeThemeCatalogTest.java:39` change `hasSize(284)` to `hasSize(286)`. In `ThemeManagerTest.java:175` change `hasSize(284)` to `hasSize(286)` and add `"Clear Dark"` coverage on the next line:

```java
        assertThat(ThemeManager.builtIns()).extracting(Theme::name).contains("Clear Dark", "Clear Light");
```

In `docs/themes.md:4` change `**284 native themes**, including Jasper Light/Dark,` to `**286 native themes**, including Jasper Light/Dark, Clear Dark/Light (ported from Terminal.app),`. In `jasper-app/README.md:83` change `284` to `286`. In `docs/app-maintenance.md` under "## Themes", change the first paragraph to:

```markdown
Edit the hand-written Jasper Light/Dark and Clear Dark/Light TOMLs under
`jasper-app/src/main/resources/dev/jasper/app/themes/` (`ConvertThemes` copies these four unchanged);
change shared mappings in `UiTemplate` and derivations in `ThemeGenerator`. The 256 KiB native cap remains.
```

and in the next paragraph change `regenerates 284 native files` to `regenerates 286 native files`.

- [ ] **Step 6: Run the tests to verify they pass**

Run: `./gradlew :jasper-app:test --tests "*ClearThemesTest" --tests "*NativeThemeCatalogTest" --tests "*ThemeManagerTest" --tests "*BundledThemeConversionTest" --tests "*AppDocumentationTest"`
Expected: PASS. If `BundledThemeConversionTest` reports an index mismatch, the two new lines are not in `TreeMap` order; fix `index.txt` to match.

- [ ] **Step 7: Commit**

```bash
git branch --show-current   # must print claude/workspace-glass
git add jasper-app/src/main/resources/dev/jasper/app/themes jasper-app/src/main/java/dev/jasper/app/themeimport/ConvertThemes.java \
  jasper-app/src/test/java/dev/jasper/app/appearance docs/themes.md docs/app-maintenance.md jasper-app/README.md
git commit -m "feat(themes): port Terminal.app's Clear Dark and Clear Light

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 3: Opacity and blur settings

**Files:**
- Create: `jasper-app/src/main/java/dev/jasper/app/config/GlassSettings.java`
- Modify: `jasper-app/src/main/java/dev/jasper/app/config/CoreSettings.java`
- Modify: `jasper-app/src/main/java/dev/jasper/app/config/ConfigSnapshot.java`
- Modify: `jasper-app/src/main/java/dev/jasper/app/appearance/GlassLevels.java`
- Modify: `jasper-app/src/main/java/dev/jasper/app/settingsui/CorePages.java`
- Modify: `config.example.toml`
- Modify: `jasper-app/src/test/java/dev/jasper/app/config/ConfigTemplateTest.java:59`
- Modify: `docs/configuration.md` (settings table)
- Test: `jasper-app/src/test/java/dev/jasper/app/config/GlassSettingsTest.java` (create)
- Test: `jasper-app/src/test/java/dev/jasper/app/appearance/GlassLevelsTest.java` (create)
- Test: `jasper-app/src/test/java/dev/jasper/app/settingsui/CorePagesTest.java`

**Interfaces:**
- Consumes: `GlassLevels` (Task 1).
- Produces: `public record GlassSettings(float opacityPercent, float blurPercent)` with `UNSET_OPACITY = 0f`, `UNSET_BLUR = -1f`, `UNSET`, `Optional<Double> opacity()`, `Optional<Double> blur()`. `CoreSettings.UI_WORKSPACE_OPACITY`, `CoreSettings.UI_WORKSPACE_BLUR` (`Setting<Float>`). `ConfigSnapshot.glass()` (new last component) and `ConfigSnapshot.Builder.glass(GlassSettings)`. `public static GlassLevels GlassLevels.resolve(GlassSettings saved, GlassLevels theme)`.

- [ ] **Step 1: Write the failing tests**

Create `jasper-app/src/test/java/dev/jasper/app/config/GlassSettingsTest.java`:

```java
package dev.jasper.app.config;

import java.nio.file.Path;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class GlassSettingsTest {
    @Test void omittedKeysDeferToTheTheme() {
        var result = ConfigLoader.parse(Path.of("config.toml"), "", true);
        assertThat(result.snapshot().glass()).isEqualTo(GlassSettings.UNSET);
        assertThat(GlassSettings.UNSET.opacity()).isEmpty();
        assertThat(GlassSettings.UNSET.blur()).isEmpty();
    }

    @Test void savedPercentagesBecomeFractions() {
        var result = ConfigLoader.parse(Path.of("config.toml"), "[ui.workspace]\nopacity = 80\nblur = 0\n", true);
        assertThat(result.diagnostics()).isEmpty();
        assertThat(result.snapshot().glass()).isEqualTo(new GlassSettings(80f, 0f));
        assertThat(result.snapshot().glass().opacity()).isEqualTo(Optional.of(0.8));
        assertThat(result.snapshot().glass().blur()).isEqualTo(Optional.of(0.0));
    }

    @Test void outOfRangeValuesAreReportedAndDefer() {
        var result = ConfigLoader.parse(Path.of("config.toml"), "[ui.workspace]\nopacity = 5\nblur = 101\n", true);
        assertThat(result.snapshot().glass()).isEqualTo(GlassSettings.UNSET);
        assertThat(result.diagnostics()).hasSize(2);
    }

    @Test void theRecordRejectsValuesTheLoaderWouldNot() {
        assertThatThrownBy(() -> new GlassSettings(5f, GlassSettings.UNSET_BLUR)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new GlassSettings(GlassSettings.UNSET_OPACITY, 101f)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new GlassSettings(Float.NaN, 0f)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test void theBuilderCarriesGlass() {
        var snapshot = ConfigSnapshot.defaults().toBuilder().glass(new GlassSettings(90f, 10f)).build();
        assertThat(snapshot.glass()).isEqualTo(new GlassSettings(90f, 10f));
        assertThat(snapshot.toBuilder().build().glass()).isEqualTo(new GlassSettings(90f, 10f));
    }
}
```

Create `jasper-app/src/test/java/dev/jasper/app/appearance/GlassLevelsTest.java`:

```java
package dev.jasper.app.appearance;

import dev.jasper.app.config.GlassSettings;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class GlassLevelsTest {
    @Test void savedSettingsOverrideTheThemeOneValueAtATime() {
        var theme = new GlassLevels(0.95, 0.5);
        assertThat(GlassLevels.resolve(GlassSettings.UNSET, theme)).isEqualTo(theme);
        assertThat(GlassLevels.resolve(new GlassSettings(80f, GlassSettings.UNSET_BLUR), theme)).isEqualTo(new GlassLevels(0.8, 0.5));
        assertThat(GlassLevels.resolve(new GlassSettings(GlassSettings.UNSET_OPACITY, 0f), theme)).isEqualTo(new GlassLevels(0.95, 0));
        assertThat(GlassLevels.resolve(new GlassSettings(100f, 20f), GlassLevels.OPAQUE)).isEqualTo(new GlassLevels(1, 0.2));
    }
}
```

In `CorePagesTest.java`, add after `everyCoreSettingIsOnExactlyOnePage`:

```java
    @Test void appearanceHasAWorkspaceSectionForOpacityAndBlur() {
        var appearance = CorePages.specs(() -> List.of("Jasper Dark"), system).getFirst();
        assertThat(appearance.id()).isEqualTo("appearance");
        assertThat(appearance.settings()).containsSubsequence(CoreSettings.UI_WORKSPACE_OPACITY, CoreSettings.UI_WORKSPACE_BLUR);
    }
```

- [ ] **Step 2: Run the tests to verify they fail**

Run: `./gradlew :jasper-app:test --tests "*GlassSettingsTest" --tests "*GlassLevelsTest" --tests "*CorePagesTest"`
Expected: compilation FAILS (`GlassSettings`, `glass()`, `UI_WORKSPACE_OPACITY` do not exist).

- [ ] **Step 3: Add `GlassSettings`**

Create `jasper-app/src/main/java/dev/jasper/app/config/GlassSettings.java`:

```java
package dev.jasper.app.config;

import java.util.Optional;

/**
 * The saved workspace glass, in percent. {@link #UNSET_OPACITY} and {@link #UNSET_BLUR} mean "use the workspace
 * theme's value"; they lie outside the settings' ranges, so they never collide with a saved value.
 *
 * @param opacityPercent 10–100, or {@link #UNSET_OPACITY}
 * @param blurPercent 0–100, or {@link #UNSET_BLUR}
 */
public record GlassSettings(float opacityPercent, float blurPercent) {
    /** The opacity a configuration without {@code ui.workspace.opacity} has. */
    public static final float UNSET_OPACITY = 0f;
    /** The blur a configuration without {@code ui.workspace.blur} has. */
    public static final float UNSET_BLUR = -1f;
    /** Both values defer to the theme. */
    public static final GlassSettings UNSET = new GlassSettings(UNSET_OPACITY, UNSET_BLUR);

    public GlassSettings {
        if (opacityPercent != UNSET_OPACITY && !(opacityPercent >= 10f && opacityPercent <= 100f))
            throw new IllegalArgumentException("Opacity must be 10–100 percent.");
        if (blurPercent != UNSET_BLUR && !(blurPercent >= 0f && blurPercent <= 100f))
            throw new IllegalArgumentException("Blur must be 0–100 percent.");
    }

    /** The saved opacity as a fraction, or empty to use the theme's. */
    public Optional<Double> opacity() { return opacityPercent == UNSET_OPACITY ? Optional.empty() : Optional.of(opacityPercent / 100.0); }

    /** The saved blur as a fraction, or empty to use the theme's. */
    public Optional<Double> blur() { return blurPercent == UNSET_BLUR ? Optional.empty() : Optional.of(blurPercent / 100.0); }
}
```

- [ ] **Step 4: Declare the settings**

In `CoreSettings.java`, after `UI_THEME_NAME` add:

```java
    public static final Setting<Float> UI_WORKSPACE_OPACITY = Setting.of("ui.workspace.opacity", SettingKind.number(10, 100),
            GlassSettings.UNSET_OPACITY)
        .example(95f).label("Opacity").keywords("opacity", "transparency", "translucent", "glass", "workspace", "background")
        .description("macOS only, live: the terminal and editor background opacity, 10-100 percent.\n"
            + "Omit to use the workspace theme's value (opaque unless the theme sets one).").build();
    public static final Setting<Float> UI_WORKSPACE_BLUR = Setting.of("ui.workspace.blur", SettingKind.number(0, 100),
            GlassSettings.UNSET_BLUR)
        .example(50f).label("Blur").keywords("blur", "frosted", "glass", "workspace", "background")
        .description("macOS only, live: how strongly the desktop behind a translucent workspace is blurred, 0-100 percent.\n"
            + "Omit to use the workspace theme's value.").build();
```

In `SCHEMA`, change the end of the settings list from `UI_THEME_NAME, UI_THEME_WORKSPACE),` to:

```java
            TERMINAL_CURSOR_SHAPE, TERMINAL_CURSOR_BLINK, TERMINAL_ENV, UI_FONT_FAMILY, UI_FONT_SIZE, UI_THEME_NAME, UI_THEME_WORKSPACE,
            UI_WORKSPACE_OPACITY, UI_WORKSPACE_BLUR),
```

and in the descriptions map, replace the last entry `Map.entry(List.of("ui", "theme"), "The interface and workspace themes.")));` with:

```java
            Map.entry(List.of("ui", "theme"), "The interface and workspace themes."),
            Map.entry(List.of("ui", "workspace"), "Translucent terminal and editor backgrounds on macOS; omitted values follow the workspace theme.")));
```

- [ ] **Step 5: Carry `glass` on `ConfigSnapshot`**

In `ConfigSnapshot.java`:

1. Add `GlassSettings glass` as the last record component (after `WorkspaceThemeChoice workspaceTheme`).
2. In the compact constructor, after `Objects.requireNonNull(workspaceTheme, "workspaceTheme");` add `Objects.requireNonNull(glass, "glass");`.
3. Directly after the compact constructor add the previous canonical signature as a compatibility constructor:

```java
    /** Every field but the workspace glass, which defers to the theme. */
    public ConfigSnapshot(int tabHeight, ToolbarMode toolbar, boolean statusBar,
                          FontConfig font, String theme, Map<String, String> keybindings,
                          int columns, int lines, TerminalConfig terminal, boolean buddyEnabled,
                          int visibleRows, int longCommandSeconds, boolean backgroundEnabled,
                          Map<String, Map<String, Object>> plugins, UiFontConfig uiFont, TerminalColors terminalColors,
                          int railWidth, int toolbarHeight, int statusBarHeight, boolean nativeDecorations,
                          WorkspaceThemeChoice workspaceTheme) {
        this(tabHeight, toolbar, statusBar, font, theme, keybindings, columns, lines, terminal, buddyEnabled,
            visibleRows, longCommandSeconds, backgroundEnabled, plugins, uiFont, terminalColors, railWidth,
            toolbarHeight, statusBarHeight, nativeDecorations, workspaceTheme, GlassSettings.UNSET);
    }
```

4. In `from(...)`, change the final argument line `WorkspaceThemeChoice.parse(values.get(CoreSettings.UI_THEME_WORKSPACE)));` to:

```java
            WorkspaceThemeChoice.parse(values.get(CoreSettings.UI_THEME_WORKSPACE)),
            new GlassSettings(values.get(CoreSettings.UI_WORKSPACE_OPACITY), values.get(CoreSettings.UI_WORKSPACE_BLUR)));
```

5. In `Builder`: add the field `private GlassSettings glass;`, in the copying constructor add `glass = source.glass();`, add the setter `public Builder glass(GlassSettings value) { glass = value; return this; }`, and change `build()` to pass `workspaceTheme, glass` as the last two arguments.

- [ ] **Step 6: Resolve settings over the theme**

In `GlassLevels.java` add the import `import dev.jasper.app.config.GlassSettings;` and the method:

```java
    /**
     * The levels a window asks for: each saved value wins over the workspace theme's.
     *
     * @param saved the saved settings
     * @param theme the workspace theme's levels
     * @return the effective levels
     */
    public static GlassLevels resolve(GlassSettings saved, GlassLevels theme) {
        return new GlassLevels(saved.opacity().orElse(theme.opacity()), saved.blur().orElse(theme.blur()));
    }
```

- [ ] **Step 7: Add the Appearance › Workspace section**

In `CorePages.java`, add two constants after `DEFAULT_PAGE`:

```java
    /** Workspace glass needs macOS; elsewhere the rows stay visible but disabled. */
    private static final boolean GLASS = com.formdev.flatlaf.util.SystemInfo.isMacOS;
    private static final String GLASS_NOTE = GLASS ? "" : "Requires macOS.";
```

and change the Appearance page spec to:

```java
            SchemaPage.page("appearance", "Appearance").parent("appearance-behavior").anchor("theme")
                .section("Theme")
                .action(() -> "Choose and preview application colors.", new SchemaPage.Button("Choose UI theme…", () -> navigate.accept("themes#ui"), () -> true))
                .section("Workspace")
                .action(() -> "Translucent terminal and editor backgrounds; a workspace theme may set its own.",
                    new SchemaPage.Button("Choose workspace theme…", () -> navigate.accept("themes#workspace"), () -> true))
                .row(UI_WORKSPACE_OPACITY, Editors.optionalNumber("Use theme's value", 1, "%")).note(GLASS_NOTE).enabledWhen(draft -> GLASS)
                .row(UI_WORKSPACE_BLUR, Editors.optionalNumber("Use theme's value", 1, "%")).note(GLASS_NOTE).enabledWhen(draft -> GLASS)
                .section("Interface font")
                .row(UI_FONT_FAMILY, Editors.font(false))
                .row(UI_FONT_SIZE, Editors.optionalNumber("Use platform size", 0.5, "pt")),
```

- [ ] **Step 8: Example config, template test and docs**

In `config.example.toml`, after the `[ui.theme]` block (after `workspace = "match"`) add:

```toml

[ui.workspace]
# macOS only, live: translucent terminal and editor backgrounds. Omit a key to use the workspace theme's value.
# Opacity: 10-100 percent.
# opacity = 95
# Blur behind a translucent workspace: 0-100 percent.
# blur = 50
```

In `ConfigTemplateTest.java:59`, change the exclusion to:

```java
            if (!(setting.kind() instanceof SettingKind.StringMap) && setting != CoreSettings.UI_FONT_SIZE
                    && setting != CoreSettings.UI_WORKSPACE_OPACITY && setting != CoreSettings.UI_WORKSPACE_BLUR) expected.add(setting.key());
```

In `docs/configuration.md`, after the `ui.theme.workspace` table row add:

```markdown
| `ui.workspace.opacity` | Workspace theme's value (omit key) | Number 10–100 percent; macOS only | Live |
| `ui.workspace.blur` | Workspace theme's value (omit key) | Number 0–100 percent; macOS only | Live |
```

- [ ] **Step 9: Run the tests to verify they pass**

Run: `./gradlew :jasper-app:test --tests "*GlassSettingsTest" --tests "*GlassLevelsTest" --tests "*CorePagesTest" --tests "*ConfigTemplateTest" --tests "*ConfigLoaderTest" --tests "*SettingsSchemaTest" --tests "*SchemaPageTest" --tests "*SettingsDialogTest" --tests "*AppDocumentationTest"`
Expected: PASS.

- [ ] **Step 10: Commit**

```bash
git branch --show-current   # must print claude/workspace-glass
git add jasper-app/src/main/java/dev/jasper/app/config jasper-app/src/main/java/dev/jasper/app/appearance/GlassLevels.java \
  jasper-app/src/main/java/dev/jasper/app/settingsui/CorePages.java config.example.toml docs/configuration.md \
  jasper-app/src/test/java/dev/jasper/app/config jasper-app/src/test/java/dev/jasper/app/appearance/GlassLevelsTest.java \
  jasper-app/src/test/java/dev/jasper/app/settingsui/CorePagesTest.java
git commit -m "feat(settings): add workspace opacity and blur under Appearance

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 4: Translucent terminal background

**Files:**
- Modify: `jasper-terminal/src/main/java/dev/jasper/terminal/internal/rendering/TerminalPainter.java`
- Modify: `jasper-terminal/src/main/java/dev/jasper/terminal/view/TerminalView.java`
- Test: `jasper-terminal/src/test/java/dev/jasper/terminal/internal/rendering/TerminalPainterTest.java`
- Test: `jasper-terminal/src/test/java/dev/jasper/terminal/view/TerminalAppearanceTest.java`

**Interfaces:**
- Produces: `TerminalPainter.paint(Graphics2D, ScreenSnapshot, CursorLook, List<Highlight>, int widthPx, int heightPx, float backgroundOpacity)` (the six-argument form delegates with `1f`). `public void TerminalView.setBackgroundOpacity(float opacity)` (clamped to 0–1; NaN means 1) and `public float TerminalView.backgroundOpacity()`.

- [ ] **Step 1: Write the failing tests**

In `TerminalPainterTest.java`, add:

```java
    @Test
    void translucentBackgroundReplacesWhatIsUnderneathAndKeepsCellsSolid() throws Exception {
        BufferedImage image = new BufferedImage(COLUMNS * cw, ROWS * ch, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = image.createGraphics();
        try {
            g.setColor(Color.RED);
            g.fillRect(0, 0, image.getWidth(), image.getHeight());
            painter.paint(g, snapshotAfter("\033[41m \033[0m\033[32m█\033[0m", 2), cursorOff(), List.of(),
                image.getWidth(), image.getHeight(), 0.5f);
        } finally {
            g.dispose();
        }
        Color empty = new Color(image.getRGB(5 * cw + cw / 2, ch / 2), true);
        assertThat(empty.getAlpha()).isEqualTo(128);
        assertThat(rgb(empty)).isEqualTo(rgb(palette.background()));
        Color explicit = new Color(image.getRGB(cw / 2, ch / 2), true);
        assertThat(explicit.getAlpha()).isEqualTo(255);
        assertThat(rgb(explicit)).isEqualTo(rgb(palette.ansi().get(1)));
        Color glyph = new Color(image.getRGB(cw + cw / 2, ch / 2), true);
        assertThat(glyph.getAlpha()).isEqualTo(255);
        assertThat(rgb(glyph)).isEqualTo(rgb(palette.ansi().get(2)));
    }

    @Test
    void fullOpacityPaintsAsBefore() throws Exception {
        BufferedImage image = new BufferedImage(COLUMNS * cw, ROWS * ch, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = image.createGraphics();
        try {
            painter.paint(g, snapshotAfter("", 0), cursorOff(), List.of(), image.getWidth(), image.getHeight(), 1f);
        } finally {
            g.dispose();
        }
        assertThat(new Color(image.getRGB(cw / 2, ch / 2), true).getAlpha()).isEqualTo(255);
    }
```

In `TerminalAppearanceTest.java`, add (with imports `java.awt.Color`):

```java
    @Test
    void translucentViewsAreNotOpaqueAndPaintTheirDefaultBackgroundWithThatAlpha() {
        assertThat(view.isOpaque()).isTrue();
        assertThat(view.backgroundOpacity()).isEqualTo(1f);

        view.setBackgroundOpacity(0.5f);

        assertThat(view.isOpaque()).isFalse();
        assertThat(view.backgroundOpacity()).isEqualTo(0.5f);
        BufferedImage image = new BufferedImage(view.getWidth(), view.getHeight(), BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = image.createGraphics();
        try {
            view.paint(graphics);
        } finally {
            graphics.dispose();
        }
        assertThat(new Color(image.getRGB(view.getWidth() - 2, view.getHeight() - 2), true).getAlpha()).isEqualTo(128);

        view.setBackgroundOpacity(Float.NaN);
        assertThat(view.backgroundOpacity()).isEqualTo(1f);
        assertThat(view.isOpaque()).isTrue();
        view.setBackgroundOpacity(-3f);
        assertThat(view.backgroundOpacity()).isZero();
    }
```

- [ ] **Step 2: Run the tests to verify they fail**

Run: `./gradlew :jasper-terminal:test --tests "*TerminalPainterTest" --tests "*TerminalAppearanceTest"`
Expected: compilation FAILS (no seven-argument `paint`, no `setBackgroundOpacity`).

- [ ] **Step 3: Paint the default background with alpha**

In `TerminalPainter.java`, add imports `java.awt.AlphaComposite` and `java.awt.Composite`, and replace the start of `paint` (the method signature through the default-background `fillRect`) with:

```java
    public void paint(Graphics2D g, ScreenSnapshot snapshot, CursorLook cursor, List<Highlight> highlights, int widthPx, int heightPx) {
        paint(g, snapshot, cursor, highlights, widthPx, heightPx, 1f);
    }

    /**
     * Paints the snapshot. Below 1, {@code backgroundOpacity} fills the default background translucent, replacing
     * whatever is underneath; explicit cell backgrounds, highlights, glyphs and the cursor stay solid.
     */
    public void paint(Graphics2D g, ScreenSnapshot snapshot, CursorLook cursor, List<Highlight> highlights, int widthPx, int heightPx,
                      float backgroundOpacity) {
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_ON);
        if (backgroundOpacity < 1f) {
            Color background = palette.background();
            Composite previous = g.getComposite();
            g.setComposite(AlphaComposite.Src);
            g.setColor(new Color(background.getRed(), background.getGreen(), background.getBlue(),
                Math.round(255 * Math.max(0f, backgroundOpacity))));
            g.fillRect(0, 0, widthPx, heightPx);
            g.setComposite(previous);
        } else {
            g.setColor(palette.background());
            g.fillRect(0, 0, widthPx, heightPx);
        }
```

(The rest of the method, from `int cellWidth = fonts.cellWidth();`, is unchanged.)

- [ ] **Step 4: Add the view property**

In `TerminalView.java`, next to `private float inactiveDim;` add `private float backgroundOpacity = 1f;`. After `setInactiveDim` add:

```java
    /**
     * Sets how opaque the default background is: 1 (the default) is opaque, 0 is fully clear. Below 1 this view is
     * not opaque, so its owner must not paint over what lies behind it; explicitly coloured cells, the selection and
     * the cursor stay solid. Values are clamped to 0–1, and NaN means 1.
     *
     * @param opacity the default background's opacity
     */
    public void setBackgroundOpacity(float opacity) {
        float bounded = Float.isNaN(opacity) ? 1f : Math.max(0f, Math.min(1f, opacity));
        if (bounded == backgroundOpacity) {
            return;
        }
        backgroundOpacity = bounded;
        setOpaque(bounded >= 1f);
        repaint();
    }

    /**
     * The default background's opacity.
     *
     * @return 0–1, where 1 is opaque
     */
    public float backgroundOpacity() {
        return backgroundOpacity;
    }
```

In `paintComponent`, change the painter call to pass the opacity:

```java
        painter.paint((Graphics2D) g, snapshot, new TerminalPainter.CursorLook(style, on, focused),
            highlights(snapshot), getWidth(), getHeight(), backgroundOpacity);
```

- [ ] **Step 5: Run the tests to verify they pass**

Run: `./gradlew :jasper-terminal:test`
Expected: PASS (all terminal tests, including the existing painter and appearance tests).

- [ ] **Step 6: Commit**

```bash
git branch --show-current   # must print claude/workspace-glass
git add jasper-terminal/src
git commit -m "feat(terminal): paint the default background with a chosen opacity

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 5: Workspace painting and glass requests

**Files:**
- Modify: `jasper-app/src/main/java/dev/jasper/app/workspace/WorkspaceRegions.java`
- Modify: `jasper-app/src/main/java/dev/jasper/app/workspace/TerminalTab.java`
- Modify: `jasper-app/src/main/java/dev/jasper/app/workspace/TerminalPane.java`
- Modify: `jasper-app/src/main/java/dev/jasper/app/workspace/WindowContent.java`
- Modify: `jasper-app/src/main/java/dev/jasper/app/workspace/WorkspaceConfiguration.java`
- Test: `jasper-app/src/test/java/dev/jasper/app/workspace/WorkspaceGlassTest.java` (create)

**Interfaces:**
- Consumes: `TerminalView.setBackgroundOpacity(float)` (Task 4); `GlassLevels.resolve`, `GlassSettings`, `ConfigSnapshot.glass()` (Task 3); `GeneratedTheme.glass()` (Task 1); bundled `Clear Dark` (Task 2).
- Produces (all package-private in `dev.jasper.app.workspace`): `WorkspaceRegions.setGlass(boolean)`; `TerminalTab.setBackgroundOpacity(float)` and nested `TerminalTab.PaneSplit`; `TerminalPane.setBackgroundOpacity(float)`; `WindowContent.applyBackgroundOpacity(double)`, `float WindowContent.backgroundOpacity()`, `WindowContent.setGlassRequests(Consumer<GlassLevels>)`, `WindowContent.requestGlass()`.

- [ ] **Step 1: Write the failing tests**

Create `jasper-app/src/test/java/dev/jasper/app/workspace/WorkspaceGlassTest.java`:

```java
package dev.jasper.app.workspace;

import dev.jasper.app.appearance.GlassLevels;
import dev.jasper.app.appearance.ThemeCatalog;
import dev.jasper.app.appearance.ThemeController;
import dev.jasper.app.config.ConfigSnapshot;
import dev.jasper.app.config.GlassSettings;
import dev.jasper.app.config.WorkspaceThemeChoice;
import dev.jasper.app.testsupport.LayoutTestSupport;
import java.awt.Color;
import java.awt.Component;
import java.awt.Point;
import java.awt.image.BufferedImage;
import java.util.ArrayDeque;
import java.util.ArrayList;
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import javax.swing.plaf.basic.BasicSplitPaneUI;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import static dev.jasper.app.workspace.DesktopTestSupport.edt;
import static org.assertj.core.api.Assertions.assertThat;

class WorkspaceGlassTest {
    @AfterEach void closeWindows() throws Exception { DesktopTestSupport.closeOwners(); edt(() -> new ThemeController()); }

    private static BufferedImage render(JComponent root) {
        var image = new BufferedImage(root.getWidth(), root.getHeight(), BufferedImage.TYPE_INT_ARGB);
        var graphics = image.createGraphics();
        try { root.paint(graphics); } finally { graphics.dispose(); }
        return image;
    }

    private static int alpha(BufferedImage image, JComponent root, Component component, int x, int y) {
        Point point = SwingUtilities.convertPoint(component, x, y, root);
        return new Color(image.getRGB(point.x, point.y), true).getAlpha();
    }

    @Test void theWorkspaceHostClearsOnlyItsSurfaceWhenGlassIsOn() throws Exception {
        edt(() -> {
            var deck = new JPanel();
            deck.setOpaque(false);
            var regions = new WorkspaceRegions(deck);
            regions.setRailVisible(true);
            regions.setTerminalBackground(new Color(0x212734));
            regions.setSize(1000, 600);
            LayoutTestSupport.layoutTree(regions);
            LayoutTestSupport.layoutTree(regions);
            assertThat(alpha(render(regions), regions, deck, 300, 200)).isEqualTo(255);

            regions.setGlass(true);
            var glass = render(regions);
            assertThat(alpha(glass, regions, deck, 300, 200)).as("inside the rounded surface").isZero();
            assertThat(alpha(glass, regions, regions, 997, 300)).as("the right gutter is chrome").isEqualTo(255);

            regions.setGlass(false);
            assertThat(alpha(render(regions), regions, deck, 300, 200)).isEqualTo(255);
        });
    }

    @Test void splitsKeepASolidDividerBetweenTranslucentPanes() throws Exception {
        edt(() -> {
            var tab = new TerminalTab(DesktopTestSupport.HOME, DesktopTestSupport.launcher(new ArrayDeque<>()));
            try {
                tab.setBackgroundOpacity(0.5f);
                tab.split(SplitTree.Axis.RIGHT);
                assertThat(tab.isOpaque()).isFalse();
                assertThat(tab.panes()).hasSize(2).allMatch(pane -> !pane.isOpaque());
                var split = (TerminalTab.PaneSplit) tab.getComponent(0);
                assertThat(split.isOpaque()).isFalse();
                tab.setSize(600, 300);
                LayoutTestSupport.layoutTree(tab);
                LayoutTestSupport.layoutTree(tab);
                var image = render(tab);
                var divider = ((BasicSplitPaneUI) split.getUI()).getDivider();
                assertThat(alpha(image, tab, divider, divider.getWidth() / 2, divider.getHeight() / 2)).as("divider").isEqualTo(255);
                for (TerminalPane pane : tab.panes())
                    assertThat(alpha(image, tab, pane, 2, 2)).as("pane padding").isEqualTo(128);

                tab.setBackgroundOpacity(1f);
                assertThat(tab.isOpaque()).isTrue();
                assertThat(split.isOpaque()).isTrue();
                assertThat(tab.panes()).allMatch(JComponent::isOpaque);
            } finally { tab.close(); }
        });
    }

    @Test void aTranslucentWindowLeavesItsChromeSolid() throws Exception {
        edt(() -> {
            WindowContent owner = DesktopTestSupport.content(DesktopTestSupport.launcher(new ArrayDeque<>()));
            owner.setSize(900, 600);
            LayoutTestSupport.layoutTree(owner);
            LayoutTestSupport.layoutTree(owner);
            TerminalPane pane = owner.currentPane();
            assertThat(alpha(render(owner), owner, pane, 2, 2)).isEqualTo(255);

            owner.applyBackgroundOpacity(0.5);
            assertThat(owner.backgroundOpacity()).isEqualTo(0.5f);
            var glass = render(owner);
            assertThat(alpha(glass, owner, pane, 2, 2)).as("pane padding").isEqualTo(128);
            var status = owner.status();
            assertThat(status.getHeight()).as("the status bar is laid out").isPositive();
            assertThat(alpha(glass, owner, status, status.getWidth() / 2, status.getHeight() / 2)).as("status bar").isEqualTo(255);
            var header = owner.chrome().header();
            assertThat(alpha(glass, owner, header, header.getWidth() / 2, header.getHeight() / 2)).as("header").isEqualTo(255);

            owner.applyBackgroundOpacity(1);
            assertThat(alpha(render(owner), owner, pane, 2, 2)).isEqualTo(255);
        });
    }

    @Test void newTabsFollowTheWindowsOpacity() throws Exception {
        edt(() -> {
            WindowContent owner = DesktopTestSupport.content(DesktopTestSupport.launcher(new ArrayDeque<>()));
            owner.applyBackgroundOpacity(0.7);
            TerminalTab tab = owner.openTab(DesktopTestSupport.HOME);
            assertThat(tab.isOpaque()).isFalse();
            assertThat(tab.panes()).allMatch(pane -> !pane.isOpaque());
        });
    }

    @Test void requestsCombineSavedSettingsWithTheWorkspaceTheme() throws Exception {
        edt(() -> {
            var themes = new ThemeController();
            WindowContent owner = DesktopTestSupport.content(DesktopTestSupport.launcher(new ArrayDeque<>()), themes);
            var requests = new ArrayList<GlassLevels>();
            owner.setGlassRequests(requests::add);
            assertThat(requests.getLast()).isEqualTo(GlassLevels.OPAQUE);

            var clear = ThemeCatalog.builtIn().scan().find("Clear Dark").orElseThrow();
            themes.configureWorkspace(new WorkspaceThemeChoice("Clear Dark"), clear);
            assertThat(requests.getLast()).isEqualTo(new GlassLevels(0.95, 0.5));

            owner.applyConfiguration(ConfigSnapshot.defaults().toBuilder()
                .glass(new GlassSettings(80f, GlassSettings.UNSET_BLUR)).build(), true);
            assertThat(requests.getLast()).isEqualTo(new GlassLevels(0.8, 0.5));
        });
    }
}
```

- [ ] **Step 2: Run the tests to verify they fail**

Run: `./gradlew :jasper-app:test --tests "*WorkspaceGlassTest"`
Expected: compilation FAILS (`setGlass`, `setBackgroundOpacity`, `PaneSplit`, `applyBackgroundOpacity`, `setGlassRequests` do not exist).

- [ ] **Step 3: Clear the workspace host's surface**

In `WorkspaceRegions.java`, in the nested `Host` class add the field `private boolean clear;` after `private boolean rounded = true;`, and replace the body of `Host.paintComponent`'s `try` block with:

```java
                g.setColor(UIManager.getColor("Jasper.titleBackground"));
                g.fillRect(0, 0, getWidth(), getHeight());
                g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                if (clear) {
                    // A translucent window: leave the surface empty, so the panes inside paint the only glass.
                    g.setComposite(java.awt.AlphaComposite.Clear);
                    g.fill(surface());
                } else {
                    // The terminal column (tab strip included) is one surface in the terminal palette, so every
                    // theme follows the same frame, including its optional rounded corners.
                    g.setColor(getBackground()); g.fill(surface());
                }
```

After `setTerminalBackground` in `WorkspaceRegions` add:

```java
    /** In a translucent window the center surface is cleared; what it holds paints the glass. */
    void setGlass(boolean glass) {
        if (center.clear == glass) return;
        center.clear = glass;
        center.repaint();
    }
```

- [ ] **Step 4: Glass panes**

In `TerminalPane.java`, add the field `private float backgroundOpacity = 1f;` next to the other fields, and these members (for example after `setConfiguredDim`):

```java
    /** Below 1 this pane is not opaque and fills its padding with the terminal background at that opacity. */
    void setBackgroundOpacity(float opacity) {
        backgroundOpacity = opacity;
        setOpaque(opacity >= 1f);
        if (view != null) view.setBackgroundOpacity(opacity);
        repaint();
    }

    @Override protected void paintComponent(java.awt.Graphics graphics) {
        if (backgroundOpacity >= 1f) { super.paintComponent(graphics); return; }
        var g = (java.awt.Graphics2D) graphics.create();
        try {
            java.awt.Color background = getBackground();
            g.setComposite(java.awt.AlphaComposite.Src);
            g.setColor(new java.awt.Color(background.getRed(), background.getGreen(), background.getBlue(),
                Math.round(255 * backgroundOpacity)));
            g.fillRect(0, 0, getWidth(), getHeight());
        } finally { g.dispose(); }
    }
```

In `adopt(TerminalSession created)`, directly after `view = new TerminalView(session, applicationOptions());` add:

```java
        view.setBackgroundOpacity(backgroundOpacity);
```

- [ ] **Step 5: Glass tabs and splits**

In `TerminalTab.java`:

1. Add the field `private float backgroundOpacity = 1f;`.
2. In `createPane`, directly after `panes.put(pane.id(), pane);` add `pane.setBackgroundOpacity(backgroundOpacity);`.
3. In `renderNode`, replace `JSplitPane split = new JSplitPane(...` with:

```java
        PaneSplit split = new PaneSplit(branch.axis() == SplitTree.Axis.RIGHT
            ? JSplitPane.HORIZONTAL_SPLIT : JSplitPane.VERTICAL_SPLIT,
            renderNode(branch.first(), generation), renderNode(branch.second(), generation));
        split.setGlass(backgroundOpacity < 1f);
```

4. Add the method and the nested class:

```java
    /** Below 1 this tab and its splits paint nothing behind the panes, except the solid dividers. */
    void setBackgroundOpacity(float opacity) {
        backgroundOpacity = opacity;
        setOpaque(opacity >= 1f);
        for (TerminalPane pane : panes.values()) pane.setBackgroundOpacity(opacity);
        for (JSplitPane split : splits.values()) if (split instanceof PaneSplit glass) glass.setGlass(opacity < 1f);
        repaint();
    }

    /** A split between panes whose divider stays solid while the panes beside it are translucent. */
    static final class PaneSplit extends JSplitPane {
        private boolean glass;

        PaneSplit(int orientation, java.awt.Component first, java.awt.Component second) { super(orientation, first, second); }

        void setGlass(boolean value) {
            glass = value;
            setOpaque(!value);
            repaint();
        }

        @Override protected void paintComponent(java.awt.Graphics graphics) {
            if (!glass) { super.paintComponent(graphics); return; }
            if (getUI() instanceof javax.swing.plaf.basic.BasicSplitPaneUI ui) {
                var divider = ui.getDivider();
                graphics.setColor(getBackground());
                graphics.fillRect(divider.getX(), divider.getY(), divider.getWidth(), divider.getHeight());
            }
        }
    }
```

- [ ] **Step 6: Window-level opacity and requests**

In `WindowContent.java`, add imports `dev.jasper.app.appearance.GlassLevels` and `dev.jasper.app.config.GlassSettings` (`java.util.*` and `java.util.function.Consumer` are already imported). Add fields:

```java
    private float backgroundOpacity = 1f;
    private Consumer<GlassLevels> glassRequests = levels -> {};
```

Add the methods (for example after `applyTheme`):

```java
    /** Where this window's requested workspace glass goes; the native window boundary installs it. */
    void setGlassRequests(Consumer<GlassLevels> requests) {
        glassRequests = Objects.requireNonNull(requests);
        requestGlass();
    }

    /** Asks for the saved glass over the workspace theme's; called after every theme or configuration change. */
    void requestGlass() {
        if (closed) return;
        ConfigSnapshot configured = workspaceConfiguration.snapshot();
        glassRequests.accept(GlassLevels.resolve(configured == null ? GlassSettings.UNSET : configured.glass(),
            themes.current().workspace().glass()));
    }

    /**
     * The opacity actually in effect for this window, 1 being opaque. The terminal surface is cleared and each pane
     * paints its own glass; chrome, tab strips and dividers stay solid.
     */
    void applyBackgroundOpacity(double opacity) {
        float value = (float) opacity;
        if (closed || value == backgroundOpacity) return;
        backgroundOpacity = value;
        boolean glass = value < 1f;
        regions.setGlass(glass);
        tabs.setOpaque(!glass);
        for (TerminalTab tab : terminalTabs()) tab.setBackgroundOpacity(value);
        repaint();
    }

    float backgroundOpacity() { return backgroundOpacity; }
```

In `openTab(...)`, directly after `tab.setBackground(themes.current().palette().background());` add:

```java
        tab.setBackgroundOpacity(backgroundOpacity);
```

In `applyTheme(...)`, change `onThemeChanged.accept(theme); update();` to:

```java
            onThemeChanged.accept(theme); update();
            requestGlass();
```

In `WorkspaceConfiguration.apply`, add as the last statement:

```java
        owner.requestGlass();
```

- [ ] **Step 7: Run the tests to verify they pass**

Run: `./gradlew :jasper-app:test --tests "*WorkspaceGlassTest" --tests "*WorkspaceRegionsTest" --tests "*SidePanelRenderTest" --tests "*SplitDividerTest" --tests "*WindowPanelsTest" --tests "*ConfiguredTerminalBehaviorTest" --tests "*PluginPaneAppearanceTest"`
Expected: PASS. If `aTranslucentWindowLeavesItsChromeSolid` reports a pane alpha of 255 in glass mode, some container between `WorkspaceRegions`' host and the pane still paints opaque; find it by rendering and printing `isOpaque()` up the pane's ancestors, and make it follow `applyBackgroundOpacity` the way `tabs` does.

- [ ] **Step 8: Commit**

```bash
git branch --show-current   # must print claude/workspace-glass
git add jasper-app/src/main/java/dev/jasper/app/workspace jasper-app/src/test/java/dev/jasper/app/workspace/WorkspaceGlassTest.java
git commit -m "feat(workspace): paint translucent terminal panes inside a cleared surface

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 6: `WindowGlass` and the macOS Java side

**Files:**
- Create: `jasper-app/src/main/java/dev/jasper/app/platform/WindowGlass.java`
- Create: `jasper-app/src/main/java/dev/jasper/app/platform/MacGlass.java`
- Test: `jasper-app/src/test/java/dev/jasper/app/platform/WindowGlassTest.java` (create)
- Test: `jasper-app/src/test/java/dev/jasper/app/platform/MacGlassTest.java` (create)

**Interfaces:**
- Produces: `public final class WindowGlass implements AutoCloseable` with `public WindowGlass(JRootPane root, Platform platform, DoubleConsumer onOpacity)`, `public void request(double opacity, double blur)`, `public double opacity()`, `public void close()`, `public static final int MAX_BLUR_RADIUS = 40`, package-private `static int radius(double blur)`; nested `public record Platform(boolean supported, Predicate<JRootPane> translucent, Consumer<JRootPane> opaque, BiPredicate<JRootPane, Integer> blur)` with `Platform.NONE` and `Platform.current()`. Package-private `MacGlass` with `static boolean translucent(JRootPane)`, `static void opaque(JRootPane)`, `static boolean blur(JRootPane, int)`, `static Path library(String override, String appPath)` and `private static native boolean setBlur(Window, int)` (JNI symbol `Java_dev_jasper_app_platform_MacGlass_setBlur`).

- [ ] **Step 1: Write the failing tests**

Create `jasper-app/src/test/java/dev/jasper/app/platform/WindowGlassTest.java`:

```java
package dev.jasper.app.platform;

import dev.jasper.app.testsupport.EdtTestExtension;
import java.awt.BorderLayout;
import java.util.ArrayList;
import java.util.List;
import javax.swing.JPanel;
import javax.swing.JRootPane;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(EdtTestExtension.class)
class WindowGlassTest {
    private final List<String> calls = new ArrayList<>();
    private final List<Double> published = new ArrayList<>();
    private boolean refuse, noBlur;
    private final WindowGlass.Platform platform = new WindowGlass.Platform(true,
        root -> { calls.add("translucent"); return !refuse; },
        root -> calls.add("opaque"),
        (root, radius) -> { calls.add("blur " + radius); return !noBlur; });
    private final JRootPane root = new JRootPane();
    private final JPanel host = new JPanel(new BorderLayout());

    private WindowGlass glass(WindowGlass.Platform chosen) {
        host.add(root);
        return new WindowGlass(root, chosen, published::add);
    }

    @Test void waitsForAPeerThenGoesTranslucentWithItsBlur() {
        var glass = glass(platform);
        glass.request(0.95, 0.5);
        assertThat(calls).isEmpty();
        assertThat(glass.opacity()).isEqualTo(1);

        host.addNotify();

        assertThat(calls).containsExactly("translucent", "blur 20");
        assertThat(glass.opacity()).isEqualTo(0.95);
        assertThat(published).containsExactly(0.95);
    }

    @Test void anOpaqueRequestTouchesNothingEvenWithABlur() {
        host.addNotify();
        var glass = glass(platform);
        glass.request(1, 0.5);
        assertThat(calls).isEmpty();
        assertThat(glass.opacity()).isEqualTo(1);
        assertThat(published).isEmpty();
    }

    @Test void returningToOpaqueRestoresTheWindowAndClearsTheBlur() {
        host.addNotify();
        var glass = glass(platform);
        glass.request(0.8, 0.25);
        glass.request(1, 0.25);
        assertThat(calls).containsExactly("translucent", "blur 10", "opaque", "blur 0");
        assertThat(published).containsExactly(0.8, 1.0);
    }

    @Test void aRefusedWindowStaysOpaqueAndIsNotAskedAgain() {
        refuse = true;
        host.addNotify();
        var glass = glass(platform);
        glass.request(0.5, 0);
        glass.request(0.6, 0);
        assertThat(calls).containsExactly("translucent");
        assertThat(glass.opacity()).isEqualTo(1);
        assertThat(published).isEmpty();
    }

    @Test void aMissingBlurBridgeKeepsTheTranslucency() {
        noBlur = true;
        host.addNotify();
        var glass = glass(platform);
        glass.request(0.9, 0.5);
        assertThat(glass.opacity()).isEqualTo(0.9);
        assertThat(calls).containsExactly("translucent", "blur 20");
    }

    @Test void unsupportedPlatformsReportOpaque() {
        host.addNotify();
        var glass = glass(WindowGlass.Platform.NONE);
        glass.request(0.5, 1);
        assertThat(glass.opacity()).isEqualTo(1);
        assertThat(published).isEmpty();
    }

    @Test void aNewPeerIsMadeTranslucentAgain() {
        host.addNotify();
        var glass = glass(platform);
        glass.request(0.9, 0);
        host.removeNotify();
        assertThat(glass.opacity()).isEqualTo(1);
        host.addNotify();
        assertThat(calls).containsExactly("translucent", "translucent");
        assertThat(published).containsExactly(0.9, 1.0, 0.9);
    }

    @Test void closedGlassIgnoresRequestsAndPeers() {
        host.addNotify();
        var glass = glass(platform);
        glass.close();
        glass.request(0.5, 0.5);
        host.removeNotify();
        host.addNotify();
        assertThat(calls).isEmpty();
        assertThat(published).isEmpty();
    }

    @Test void blurMapsTerminalAppsScaleOntoARadius() {
        assertThat(WindowGlass.radius(0)).isZero();
        assertThat(WindowGlass.radius(0.5)).isEqualTo(20);
        assertThat(WindowGlass.radius(1)).isEqualTo(WindowGlass.MAX_BLUR_RADIUS);
        assertThat(WindowGlass.radius(2)).isEqualTo(WindowGlass.MAX_BLUR_RADIUS);
        assertThat(WindowGlass.radius(-1)).isZero();
    }
}
```

Create `jasper-app/src/test/java/dev/jasper/app/platform/MacGlassTest.java`:

```java
package dev.jasper.app.platform;

import java.nio.file.Path;
import javax.swing.JRootPane;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class MacGlassTest {
    @Test void anExplicitLibraryWins() {
        assertThat(MacGlass.library("/tmp/libjasper-glass.dylib", "/Applications/Jasper.app/Contents/MacOS/Jasper"))
            .isEqualTo(Path.of("/tmp/libjasper-glass.dylib"));
    }

    @Test void aPackagedAppLoadsItFromContentsApp() {
        assertThat(MacGlass.library(null, "/Applications/Jasper.app/Contents/MacOS/Jasper"))
            .isEqualTo(Path.of("/Applications/Jasper.app/Contents/app/libjasper-glass.dylib"));
    }

    @Test void anUnpackagedJvmHasNoLibrary() {
        assertThat(MacGlass.library(null, null)).isNull();
        assertThat(MacGlass.library(" ", "/usr/bin/java")).isNull();
    }

    @Test void headlessRuntimesAreUnsupported() {
        assertThat(WindowGlass.Platform.current().supported()).isFalse();
    }

    @Test void aRootPaneWithoutAWindowIsNeverMadeTranslucent() {
        assertThat(MacGlass.translucent(new JRootPane())).isFalse();
        assertThat(MacGlass.blur(new JRootPane(), 20)).isFalse();
    }
}
```

- [ ] **Step 2: Run the tests to verify they fail**

Run: `./gradlew :jasper-app:test --tests "*WindowGlassTest" --tests "*MacGlassTest"`
Expected: compilation FAILS (`WindowGlass`, `MacGlass` do not exist).

- [ ] **Step 3: Implement `WindowGlass`**

Create `jasper-app/src/main/java/dev/jasper/app/platform/WindowGlass.java`:

```java
package dev.jasper.app.platform;

import java.awt.GraphicsEnvironment;
import java.awt.event.HierarchyEvent;
import java.awt.event.HierarchyListener;
import java.util.Objects;
import java.util.function.BiPredicate;
import java.util.function.Consumer;
import java.util.function.DoubleConsumer;
import java.util.function.Predicate;
import java.util.logging.Logger;
import javax.swing.JRootPane;
import javax.swing.SwingUtilities;

/**
 * Makes one window translucent behind its workspace and blurs the desktop there (macOS). It reports the opacity
 * actually in effect, which painting must use: 1 whenever the window could not be made translucent, so any failure
 * leaves an ordinary opaque window. It follows the frame's root pane, so a recreated native window is made
 * translucent again. EDT only; the window's owner closes it before disposing the frame.
 */
public final class WindowGlass implements AutoCloseable {
    /** The blur radius, in points, for Terminal.app's full blur of 1; the 0–1 scale maps linearly onto it. */
    public static final int MAX_BLUR_RADIUS = 40;
    private static final Logger LOG = Logger.getLogger(WindowGlass.class.getName());

    /**
     * What a platform can do to one native window, given its root pane.
     *
     * @param supported whether this platform offers translucent windows at all
     * @param translucent makes the window translucent; false when the runtime refuses
     * @param opaque makes a window that {@code translucent} changed opaque again
     * @param blur sets the blur radius behind the window; false when the native bridge is unavailable
     */
    public record Platform(boolean supported, Predicate<JRootPane> translucent, Consumer<JRootPane> opaque,
                           BiPredicate<JRootPane, Integer> blur) {
        /** A platform without translucent windows. */
        public static final Platform NONE = new Platform(false, root -> false, root -> {}, (root, radius) -> false);

        public Platform {
            Objects.requireNonNull(translucent); Objects.requireNonNull(opaque); Objects.requireNonNull(blur);
        }

        /**
         * This runtime's platform: macOS with a display, or {@link #NONE}.
         *
         * @return the platform windows use
         */
        public static Platform current() {
            boolean mac = System.getProperty("os.name", "").toLowerCase(java.util.Locale.ROOT).startsWith("mac");
            if (!mac || GraphicsEnvironment.isHeadless()) return NONE;
            return new Platform(true, MacGlass::translucent, MacGlass::opaque, MacGlass::blur);
        }
    }

    private final JRootPane root;
    private final Platform platform;
    private final DoubleConsumer onOpacity;
    private final HierarchyListener peerChanged = event -> {
        if ((event.getChangeFlags() & HierarchyEvent.DISPLAYABILITY_CHANGED) != 0) displayabilityChanged();
    };
    private double requestedOpacity = 1, requestedBlur, opacity = 1;
    private boolean translucent, refused, blurWarned, closed;
    private int appliedRadius;

    /**
     * Follows {@code root}'s window.
     *
     * @param root the frame's root pane
     * @param platform what the platform can do
     * @param onOpacity receives each change of the opacity in effect
     */
    public WindowGlass(JRootPane root, Platform platform, DoubleConsumer onOpacity) {
        this.root = Objects.requireNonNull(root);
        this.platform = Objects.requireNonNull(platform);
        this.onOpacity = Objects.requireNonNull(onOpacity);
        root.addHierarchyListener(peerChanged);
    }

    /**
     * Asks for these levels; they apply now, or once the window has a native peer.
     *
     * @param opacity 0.1–1, where 1 is opaque
     * @param blur 0–1, Terminal.app's blur scale
     */
    public void request(double opacity, double blur) {
        requireEdt();
        if (closed) return;
        requestedOpacity = Math.max(0.1, Math.min(1, opacity));
        requestedBlur = Math.max(0, Math.min(1, blur));
        apply();
    }

    /**
     * The opacity in effect: what painting must use.
     *
     * @return 0.1–1, where 1 is opaque
     */
    public double opacity() { return opacity; }

    static int radius(double blur) { return (int) Math.round(Math.max(0, Math.min(1, blur)) * MAX_BLUR_RADIUS); }

    private void displayabilityChanged() {
        if (closed) return;
        if (!root.isDisplayable()) {
            // The native window is gone; a new one starts opaque and unblurred.
            translucent = false;
            appliedRadius = 0;
        }
        apply();
    }

    private void apply() {
        boolean wanted = requestedOpacity < 1 && platform.supported() && !refused;
        if (root.isDisplayable()) {
            if (wanted && !translucent) {
                if (platform.translucent().test(root)) translucent = true;
                else {
                    refused = true;
                    LOG.warning("This runtime cannot make Jasper windows translucent; the workspace stays opaque. "
                        + "Jasper needs --add-opens java.desktop/java.awt and --add-exports java.desktop/java.awt.peer.");
                }
            } else if (!wanted && translucent) {
                platform.opaque().accept(root);
                translucent = false;
            }
            int radius = translucent ? radius(requestedBlur) : 0;
            if (radius != appliedRadius) {
                if (platform.blur().test(root, radius)) appliedRadius = radius;
                else if (!blurWarned) {
                    blurWarned = true;
                    LOG.warning("The window blur bridge is unavailable; translucent windows are not blurred.");
                }
            }
        }
        double next = translucent ? requestedOpacity : 1;
        if (next != opacity) {
            opacity = next;
            onOpacity.accept(next);
        }
    }

    @Override public void close() {
        if (closed) return;
        closed = true;
        root.removeHierarchyListener(peerChanged);
    }

    private static void requireEdt() {
        if (!SwingUtilities.isEventDispatchThread()) throw new IllegalStateException("Window glass requires the EDT");
    }
}
```

- [ ] **Step 4: Implement `MacGlass`**

Create `jasper-app/src/main/java/dev/jasper/app/platform/MacGlass.java`:

```java
package dev.jasper.app.platform;

import java.awt.Color;
import java.awt.Component;
import java.awt.Window;
import java.lang.reflect.Field;
import java.nio.file.Path;
import java.util.Map;
import java.util.WeakHashMap;
import javax.swing.JRootPane;
import javax.swing.SwingUtilities;

/**
 * The macOS side of {@link WindowGlass}. AWT refuses a translucent background on a decorated frame, so translucency
 * goes through two JDK internals (the launcher opens them); blur is a JNI bridge to a private CoreGraphics call that
 * Terminal.app, iTerm2, Alacritty and WezTerm also use. Every failure returns false rather than throwing. EDT only.
 */
final class MacGlass {
    private static final Color CLEAR = new Color(0, 0, 0, 0);
    private static final Map<Window, Color> ORIGINAL = new WeakHashMap<>();
    private static Boolean loaded;

    private MacGlass() { }

    static boolean translucent(JRootPane root) {
        Window window = SwingUtilities.getWindowAncestor(root);
        if (window == null) return false;
        try {
            Object peer = peer(window);
            if (peer == null) return false;
            ORIGINAL.putIfAbsent(window, window.getBackground());
            background(window, peer, CLEAR);
            Class.forName("java.awt.peer.WindowPeer").getMethod("setOpaque", boolean.class).invoke(peer, false);
            return true;
        } catch (ReflectiveOperationException | RuntimeException failure) {
            restore(window);
            return false;
        }
    }

    static void opaque(JRootPane root) {
        Window window = SwingUtilities.getWindowAncestor(root);
        if (window != null) restore(window);
    }

    private static void restore(Window window) {
        try {
            Object peer = peer(window);
            if (peer != null) Class.forName("java.awt.peer.WindowPeer").getMethod("setOpaque", boolean.class).invoke(peer, true);
            Color original = ORIGINAL.remove(window);
            if (original != null && peer != null) background(window, peer, original);
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            // Nothing more can be undone; WindowGlass already reports an opaque window.
        }
    }

    private static Object peer(Window window) throws ReflectiveOperationException {
        Field field = Component.class.getDeclaredField("peer");
        field.setAccessible(true);
        return field.get(window);
    }

    /** Frame.setBackground refuses alpha on a decorated frame, so the field and the peer are set directly. */
    private static void background(Window window, Object peer, Color color) throws ReflectiveOperationException {
        Field field = Component.class.getDeclaredField("background");
        field.setAccessible(true);
        field.set(window, color);
        Class.forName("java.awt.peer.ComponentPeer").getMethod("setBackground", Color.class).invoke(peer, color);
    }

    static boolean blur(JRootPane root, int radius) {
        Window window = SwingUtilities.getWindowAncestor(root);
        if (window == null || !load()) return false;
        try {
            return setBlur(window, radius);
        } catch (UnsatisfiedLinkError failure) {
            return false;
        }
    }

    /** Under {@code ./gradlew run} the build passes {@code jasper.glassLibrary}; a packaged app has it in Contents/app. */
    static Path library(String override, String appPath) {
        if (override != null && !override.isBlank()) return Path.of(override);
        Path notifications = MacNotifications.libraryPath(appPath);
        return notifications == null ? null : notifications.resolveSibling("libjasper-glass.dylib");
    }

    @SuppressWarnings("restricted") // The launcher explicitly enables native access.
    private static synchronized boolean load() {
        if (loaded == null) {
            Path library = library(System.getProperty("jasper.glassLibrary"), System.getProperty("jpackage.app-path"));
            try {
                if (library == null) loaded = false;
                else {
                    // The bridge finds JAWT_GetAWT at run time, so the JDK's JAWT library is loaded first.
                    System.loadLibrary("jawt");
                    System.load(library.toString());
                    loaded = true;
                }
            } catch (UnsatisfiedLinkError | SecurityException failure) {
                loaded = false;
            }
        }
        return loaded;
    }

    private static native boolean setBlur(Window window, int radius);
}
```

- [ ] **Step 5: Run the tests to verify they pass**

Run: `./gradlew :jasper-app:test --tests "*WindowGlassTest" --tests "*MacGlassTest" --tests "*NativeNotifierTest"`
Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git branch --show-current   # must print claude/workspace-glass
git add jasper-app/src/main/java/dev/jasper/app/platform/WindowGlass.java jasper-app/src/main/java/dev/jasper/app/platform/MacGlass.java \
  jasper-app/src/test/java/dev/jasper/app/platform/WindowGlassTest.java jasper-app/src/test/java/dev/jasper/app/platform/MacGlassTest.java
git commit -m "feat(platform): add per-window glass with an opaque fallback

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 7: Native blur bridge, build, packaging and window wiring

**Files:**
- Create: `jasper-app/src/main/native/MacGlass.m`
- Create: `jasper-app/src/test/native/MacGlassTest.m`
- Create: `gradle/mac-glass.gradle`
- Modify: `gradle/packaging.gradle` (apply the script; copy back and verify the dylib; check the JVM flags)
- Modify: `jasper-app/build.gradle.kts` (JVM flags)
- Modify: `jasper-app/src/main/java/dev/jasper/app/workspace/TerminalWindow.java`

**Interfaces:**
- Consumes: `WindowGlass` (Task 6), `WindowContent.setGlassRequests`, `WindowContent.applyBackgroundOpacity` (Task 5).
- Produces: `build/native/libjasper-glass.dylib` (task `compileMacGlass`), native test task `testMacGlass` (run by `check` on macOS), `Contents/app/libjasper-glass.dylib` in packaged apps.

- [ ] **Step 1: Write the failing native test**

Create `jasper-app/src/test/native/MacGlassTest.m`:

```objc
// Headless: checks the radius bounds and finding a window by the layer JAWT reports, with stand-in windows.
// Creates no window and never calls the private blur function.
#import "../../main/native/MacGlass.m"
#include <assert.h>

@interface TestView : NSObject
@property CALayer *layer;
@end
@implementation TestView
@end

@interface TestWindow : NSObject
@property TestView *contentView;
@end
@implementation TestWindow
@end

static TestWindow *WindowWithLayer(CALayer *layer) {
    TestWindow *window = [TestWindow new];
    window.contentView = [TestView new];
    window.contentView.layer = layer;
    return window;
}

int main(void) {
    @autoreleasepool {
        assert(JasperClampRadius(-5) == 0);
        assert(JasperClampRadius(20) == 20);
        assert(JasperClampRadius(500) == 64);

        CALayer *content = [CALayer layer];
        CALayer *metal = [CALayer layer];
        [content addSublayer:metal];
        TestWindow *other = WindowWithLayer([CALayer layer]);
        TestWindow *jasper = WindowWithLayer(content);
        NSArray *windows = @[other, jasper];

        assert(JasperWindowForLayer(content, windows) == jasper);
        assert(JasperWindowForLayer(metal, windows) == jasper);
        assert(JasperWindowForLayer([CALayer layer], windows) == nil);
        assert(JasperWindowForLayer(nil, windows) == nil);
        assert(JasperWindowForLayer(metal, @[]) == nil);
    }
    return 0;
}
```

- [ ] **Step 2: Write the bridge**

Create `jasper-app/src/main/native/MacGlass.m`:

```objc
// Blurs the desktop behind a translucent Jasper window. Terminal.app, iTerm2, Alacritty and WezTerm use the same
// private CoreGraphics call. It and JAWT_GetAWT are looked up at run time, so a missing symbol only disables blur.
#import <AppKit/AppKit.h>
#import <QuartzCore/QuartzCore.h>
#include <dlfcn.h>
#include <jni.h>
#include <jawt.h>
#include <jawt_md.h>

typedef int JasperConnection;
typedef JasperConnection (*JasperDefaultConnection)(void);
typedef int32_t (*JasperSetBlurRadius)(JasperConnection, NSInteger, int);
typedef jboolean (JNICALL *JasperGetAWT)(JNIEnv *, JAWT *);

/** The radius CoreGraphics receives; Java already limits it to 40. */
static int JasperClampRadius(jint radius) { return radius < 0 ? 0 : radius > 64 ? 64 : (int) radius; }

/** The window whose content view hosts {@code layer}: the view's own layer or one of its direct sublayers. */
static id JasperWindowForLayer(CALayer *layer, NSArray *windows) {
    if (layer == nil) return nil;
    for (id window in windows) {
        CALayer *content = [[window contentView] layer];
        if (content == layer || [[content sublayers] containsObject:layer]) return window;
    }
    return nil;
}

/** The AWT window's Metal layer, through JAWT; nil when JAWT is unavailable or the window has no surface. */
static CALayer *JasperLayer(JNIEnv *env, jobject window) {
    JasperGetAWT getAWT = (JasperGetAWT) dlsym(RTLD_DEFAULT, "JAWT_GetAWT");
    if (getAWT == NULL) return nil;
    JAWT awt;
    awt.version = JAWT_VERSION_9;
    if (!getAWT(env, &awt)) return nil;
    JAWT_DrawingSurface *surface = awt.GetDrawingSurface(env, window);
    if (surface == NULL) return nil;
    CALayer *layer = nil;
    jint lock = surface->Lock(surface);
    if (!(lock & JAWT_LOCK_ERROR)) {
        JAWT_DrawingSurfaceInfo *info = surface->GetDrawingSurfaceInfo(surface);
        if (info != NULL) {
            id<JAWT_SurfaceLayers> layers = (__bridge id<JAWT_SurfaceLayers>) info->platformInfo;
            layer = [layers windowLayer];
            surface->FreeDrawingSurfaceInfo(info);
        }
        surface->Unlock(surface);
    }
    awt.FreeDrawingSurface(surface);
    return layer;
}

JNIEXPORT jboolean JNICALL Java_dev_jasper_app_platform_MacGlass_setBlur(JNIEnv *env, jclass type, jobject window, jint radius) {
    JasperDefaultConnection connection = (JasperDefaultConnection) dlsym(RTLD_DEFAULT, "CGSDefaultConnectionForThread");
    JasperSetBlurRadius setBlurRadius = (JasperSetBlurRadius) dlsym(RTLD_DEFAULT, "CGSSetWindowBackgroundBlurRadius");
    if (connection == NULL || setBlurRadius == NULL) return JNI_FALSE;
    CALayer *layer = JasperLayer(env, window);
    if (layer == nil) return JNI_FALSE;
    int clamped = JasperClampRadius(radius);
    // AppKit belongs to the main thread. Never wait for it here: it may be waiting for the EDT.
    dispatch_async(dispatch_get_main_queue(), ^{
        NSWindow *target = JasperWindowForLayer(layer, [NSApp windows]);
        if (target != nil) setBlurRadius(connection(), [target windowNumber], clamped);
    });
    return JNI_TRUE;
}
```

- [ ] **Step 3: Build, test and stage the bridge**

Create `gradle/mac-glass.gradle`:

```groovy
// The blur behind a translucent window is a private CoreGraphics call; this small bridge finds the window through
// JAWT. Only macOS builds compile it; elsewhere windows stay opaque and Settings disables the rows.
import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.jvm.toolchain.JavaToolchainService

if (!System.getProperty('os.name').toLowerCase(Locale.ROOT).startsWith('mac')) return

def toolchains = extensions.getByType(JavaToolchainService)
def javaExtension = extensions.getByType(JavaPluginExtension)
def sdkHome = toolchains.launcherFor(javaExtension.toolchain).map { it.metadata.installationPath }
def source = file('src/main/native/MacGlass.m')
def nativeDirectory = layout.buildDirectory.dir('native')
def library = nativeDirectory.map { it.file('libjasper-glass.dylib') }
def testSource = file('src/test/native/MacGlassTest.m')
def testExecutable = nativeDirectory.map { it.file('glass-tests') }
def compilerArguments = {
    ['-fobjc-arc', '-fblocks', '-Wall', '-Wextra', '-Werror', '-Wno-unused-parameter',
        '-mmacosx-version-min=11.0', '-framework', 'AppKit', '-framework', 'QuartzCore',
        '-I' + sdkHome.get().dir('include').asFile.absolutePath,
        '-I' + sdkHome.get().dir('include/darwin').asFile.absolutePath]
}

def compileBridge = tasks.register('compileMacGlass', Exec) {
    inputs.file(source)
    inputs.property('sdkHome', sdkHome.map { it.asFile.absolutePath })
    inputs.property('architecture', System.getProperty('os.arch'))
    outputs.file(library)
    doFirst {
        nativeDirectory.get().asFile.mkdirs()
        commandLine(['/usr/bin/xcrun', 'clang'] + compilerArguments() +
            ['-dynamiclib', '-install_name', '@rpath/libjasper-glass.dylib',
             source.absolutePath, '-o', library.get().asFile.absolutePath])
    }
    doLast {
        providers.exec {
            commandLine('/usr/bin/codesign', '--force', '--sign', '-', library.get().asFile.absolutePath)
        }.result.get()
    }
}

// `./gradlew :jasper-app:run` is unpackaged, so it names the freshly built bridge explicitly.
tasks.named('run', JavaExec) {
    dependsOn(compileBridge)
    systemProperty('jasper.glassLibrary', library.get().asFile.absolutePath)
}

def compileTests = tasks.register('compileMacGlassTests', Exec) {
    inputs.files(source, testSource)
    inputs.property('sdkHome', sdkHome.map { it.asFile.absolutePath })
    inputs.property('architecture', System.getProperty('os.arch'))
    outputs.file(testExecutable)
    doFirst {
        nativeDirectory.get().asFile.mkdirs()
        commandLine(['/usr/bin/xcrun', 'clang'] + compilerArguments() +
            [testSource.absolutePath, '-o', testExecutable.get().asFile.absolutePath])
    }
}

def nativeTests = tasks.register('testMacGlass', Exec) {
    group = 'verification'
    description = 'Checks the macOS glass bridge headlessly: radius bounds and finding a window by its layer.'
    dependsOn(compileTests, compileBridge)
    commandLine(testExecutable.get().asFile.absolutePath)
}
tasks.named('check') { dependsOn(nativeTests) }
tasks.named('stagePackage', Sync) { from(compileBridge) }
```

- [ ] **Step 4: Apply the script and run the native test**

In `gradle/packaging.gradle`, directly after `apply from: rootProject.file('gradle/mac-notifications.gradle')` add:

```groovy
apply from: rootProject.file('gradle/mac-glass.gradle')
```

Run: `./gradlew :jasper-app:testMacGlass`
Expected: BUILD SUCCESSFUL (the test executable exits 0; a failed `assert` aborts it and fails the task).

- [ ] **Step 5: Package and verify the bridge, and add the JVM flags**

In the macOS image step, directly after the `Files.copy(...)` of `libjasper-notifications.dylib` (before `resignImage(image)`), add:

```groovy
                Files.copy(new File(inputDirectory.get().asFile, 'libjasper-glass.dylib').toPath(),
                    new File(image, 'Contents/app/libjasper-glass.dylib').toPath(),
                    java.nio.file.StandardCopyOption.REPLACE_EXISTING)
```

Replace the `Jasper.cfg` JVM-option check with:

```groovy
        if (!cfg.contains('--enable-native-access=ALL-UNNAMED') || !cfg.contains('-Dapple.awt.application.name=Jasper')
                || !cfg.contains('--add-opens=java.desktop/java.awt=ALL-UNNAMED')
                || !cfg.contains('--add-exports=java.desktop/java.awt.peer=ALL-UNNAMED')) {
            throw new GradleException('Package verification failed: required JVM options are missing from Jasper.cfg')
        }
```

In the macOS verification block, replace the notification-bridge checks (from `File notificationBridge = ...` through the `codesign --verify` call) with a loop over both bridges:

```groovy
            String expectedBridgeArchitecture = context.host.architecture == 'aarch64' ? 'arm64' : 'x86_64'
            ['notification': 'libjasper-notifications.dylib', 'glass': 'libjasper-glass.dylib'].each { label, name ->
                File bridge = new File(appDir, name)
                if (!bridge.isFile()) throw new GradleException("Package verification failed: ${label} bridge missing")
                String bridgeArchitecture = executeNative(['/usr/bin/lipo', '-archs', bridge.absolutePath],
                    "${label} bridge architecture").stdout.trim()
                if (bridgeArchitecture != expectedBridgeArchitecture) throw new GradleException("Package verification failed: ${label} bridge architecture mismatch")
                executeNative(['/usr/bin/codesign', '--verify', '--strict', bridge.absolutePath], "${label} bridge signature")
            }
```

In `jasper-app/build.gradle.kts`, change `applicationDefaultJvmArgs` to:

```kotlin
    applicationDefaultJvmArgs = listOf(
        "--enable-native-access=ALL-UNNAMED",
        "-Dapple.awt.application.name=Jasper",
        // Workspace glass: AWT refuses translucency on a decorated frame, so WindowGlass reaches the peer directly.
        "--add-opens=java.desktop/java.awt=ALL-UNNAMED",
        "--add-exports=java.desktop/java.awt.peer=ALL-UNNAMED",
    )
```

and the benchmark `jvmArgs(...)` to:

```kotlin
        jvmArgs("--enable-native-access=ALL-UNNAMED", "-Dapple.awt.application.name=Jasper benchmark",
            "--add-opens=java.desktop/java.awt=ALL-UNNAMED", "--add-exports=java.desktop/java.awt.peer=ALL-UNNAMED")
```

`packaging.gradle` passes each `applicationDefaultJvmArgs` element as its own `--java-options`, which is why the flags are single `=` tokens.

- [ ] **Step 6: Check the built bridge**

Run: `./gradlew :jasper-app:testMacGlass :jasper-app:compileMacGlass`
Expected: BUILD SUCCESSFUL; `jasper-app/build/native/libjasper-glass.dylib` exists.

Run: `nm -gU jasper-app/build/native/libjasper-glass.dylib | grep MacGlass_setBlur`
Expected: `_Java_dev_jasper_app_platform_MacGlass_setBlur`.

- [ ] **Step 7: Wire the window**

In `TerminalWindow.java`, add the import `dev.jasper.app.platform.WindowGlass;` and the field `private final WindowGlass glass;`. Directly before `frame.addWindowListener(events); frame.pack();` add:

```java
        glass = new WindowGlass(frame.getRootPane(), WindowGlass.Platform.current(), content::applyBackgroundOpacity);
        content.setGlassRequests(levels -> glass.request(levels.opacity(), levels.blur()));
```

In `close()`, change `closed = true; content.close();` to:

```java
        closed = true; glass.close(); content.close();
```

- [ ] **Step 8: Compile and run the app tests**

Run: `./gradlew :jasper-app:test`
Expected: PASS. (`TerminalWindow` is not constructed headlessly; the GUI check covers it.)

- [ ] **Step 9: Commit**

```bash
git branch --show-current   # must print claude/workspace-glass
git add jasper-app/src/main/native/MacGlass.m jasper-app/src/test/native/MacGlassTest.m gradle/mac-glass.gradle gradle/packaging.gradle \
  jasper-app/build.gradle.kts jasper-app/src/main/java/dev/jasper/app/workspace/TerminalWindow.java
git commit -m "feat(platform): blur behind translucent windows through a JNI bridge

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 8: Documentation, status and full verification

**Files:**
- Modify: `docs/app-maintenance.md` (new "Workspace glass" recipe under "## Platform integration")
- Modify: `docs/STATUS.md`
- Modify: `docs/superpowers/specs/2026-10-02-jasper-workspace-glass-design.md` (status line)
- Modify: this plan's status line

- [ ] **Step 1: Add the maintenance recipe**

In `docs/app-maintenance.md`, after the "## Platform integration" section's last code block, add:

````markdown
### Workspace glass

A translucent, blurred terminal workspace on macOS. The workspace theme's `[workspace]` table and
`ui.workspace.opacity`/`ui.workspace.blur` combine in `GlassLevels.resolve`; `WindowContent.requestGlass` sends the
result to the window's `WindowGlass` after every theme or configuration change. `WindowGlass` decides, and reports the
opacity actually in effect to `WindowContent.applyBackgroundOpacity`; painting never reads the raw settings, so any
failure leaves an opaque window.

- **Translucency** needs two JDK internals (AWT refuses alpha on a decorated frame): `MacGlass` sets
  `Component.background` and calls `WindowPeer.setOpaque(false)`. The launcher passes
  `--add-opens=java.desktop/java.awt=ALL-UNNAMED` and `--add-exports=java.desktop/java.awt.peer=ALL-UNNAMED`;
  `packaging.gradle` refuses a `Jasper.cfg` without them. After a JBR upgrade, check that a Clear theme is still
  translucent; if the internals changed, `WindowGlass` logs one warning and windows stay opaque.
- **Blur** is `libjasper-glass.dylib` (`src/main/native/MacGlass.m`, built and tested by `gradle/mac-glass.gradle`).
  It finds the `NSWindow` through JAWT's layer and calls the private `CGSSetWindowBackgroundBlurRadius` on the main
  thread, never waiting for it. `./gradlew :jasper-app:run` passes `-Djasper.glassLibrary`; a packaged app loads it
  from `Contents/app/`. Tune the radius with `WindowGlass.MAX_BLUR_RADIUS` (0–1 maps linearly onto 0–40).
- **Painting**: the window peer is the only non-opaque ancestor. `WorkspaceRegions`' center host clears its rounded
  surface; `TerminalDeck`, `TerminalTab` and its `PaneSplit`s paint nothing except solid dividers; `TerminalPane` and
  `TerminalView` fill the default background with alpha using the `Src` composite. Anything new inside the terminal
  surface must either be opaque on purpose or follow `applyBackgroundOpacity`.

```sh
./gradlew :jasper-app:test --tests "*WorkspaceGlassTest" --tests "*WindowGlassTest" --tests "*MacGlassTest" --tests "*ClearThemesTest"
./gradlew :jasper-app:testMacGlass :jasper-terminal:test --tests "*TerminalPainterTest"
```
````

- [ ] **Step 2: Run the full check and source hygiene**

Run: `./gradlew check --continue 2>&1 | tee /tmp/workspace-glass-check.log | tail -30`
Expected: BUILD SUCCESSFUL. Read exact counts from `*/build/test-results/test/*.xml` (sum `tests`, `failures`, `errors`, `skipped`). The known machine-dependent `ExFilesTest.bufferSelectsATabByNumberOrName` failure is fixed on main (`185a5f8e`); any failure here is new and must be fixed.

Run the hygiene script from `AGENTS.md` ("Source hygiene"), then `git diff --check main...HEAD`.
Expected: no output from either.

- [ ] **Step 3: Record status**

In `docs/STATUS.md`, add a new first bullet under "## Current state — 2026-10-02":

```markdown
- **Workspace glass, plan 1 (foundation)** (branch `claude/workspace-glass`, user request 2026-10-02):
  [spec](superpowers/specs/2026-10-02-jasper-workspace-glass-design.md),
  [plan](superpowers/plans/2026-10-02-workspace-glass-1-foundation.md). Themes gain `[workspace] opacity/blur`;
  Settings › Appearance › Workspace overrides them (`ui.workspace.opacity`, `ui.workspace.blur`, percent); bundled
  Clear Dark (0.95/0.5) and Clear Light (0.93/0.5) are ported from Terminal.app (catalog 286). On macOS, `WindowGlass`
  makes the window translucent through two JDK internals (new launcher flags) and blurs behind it through
  `libjasper-glass.dylib`; terminal panes paint their default background with alpha inside a cleared surface, and any
  failure keeps the window opaque. Deviations from the spec: ancestors stay opaque and the workspace host clears a
  hole (second spike); opacity is `TerminalView.setBackgroundOpacity` only (no `TerminalOptions` field);
  `WindowGlass` follows the root pane. `./gradlew check`: <fill in exact tests/failures/skips from the XML>.
  Not done: plan 2 (editor glass, SDK 0.12.1); GUI checks (user-run): Clear Dark beside Terminal.app's, the blur
  radius at 0.5, live opacity/blur changes, splits, hidden-rail mode, native fullscreen, switching back to an
  opaque theme, a packaged build loading `Contents/app/libjasper-glass.dylib`, and Settings rows disabled off macOS.
```

Replace the `<fill in …>` text with the real counts before committing.

In the spec's `**Status:**` line write: `Design approved in chat by the user on 2026-10-02, section by section. Plan 1 (foundation) executed on claude/workspace-glass; plan 2 (editor) not written. Plan 1 deviations are listed in the plan and docs/STATUS.md.` In this plan's `**Status:**` line write `Executed 2026-10-02; see docs/STATUS.md.`

- [ ] **Step 4: Commit**

```bash
git branch --show-current   # must print claude/workspace-glass
git add docs/app-maintenance.md docs/STATUS.md docs/superpowers/specs/2026-10-02-jasper-workspace-glass-design.md \
  docs/superpowers/plans/2026-10-02-workspace-glass-1-foundation.md
git commit -m "docs: record workspace glass plan 1

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```
