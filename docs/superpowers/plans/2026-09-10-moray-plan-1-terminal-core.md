# Moray Plan 1 — Terminal Core Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** A single-window, single-pane Moray terminal that runs your login shell, renders with Moray's own Swing view (ligatures, fallback fonts, 256/truecolor, cursor shapes) on top of `jediterm-core` + pty4j, accepts keyboard input including Option-as-Meta, and has a throughput benchmark and three-OS CI.

**Architecture:** Two Gradle modules. `moray-terminal` owns everything terminal: `TerminalSession` (pty4j process + JediTerm emulator on a reader thread), `TerminalView` (a `JComponent` that snapshots the text buffer under its lock and paints runs of same-style cells as glyph vectors pinned to the cell grid), `KeyEncoder` (keys → bytes). `moray-app` holds `Main` (one `JFrame` with one `TerminalView`) and `Bench`. JediTerm types never appear in `moray-terminal`'s public API.

**Tech Stack:** Java 25 on JetBrains Runtime 25, Gradle 9.7.0 (Kotlin DSL), Swing, `jediterm-core` 3.76, pty4j 0.13.10, JUnit 6.1.3, AssertJ 3.27.7, GitHub Actions.

**Spec:** `docs/superpowers/specs/2026-09-10-moray-phase-1-terminal-design.md`

## Global Constraints

- Java toolchain: language version 25, vendor `JvmVendorSpec.JETBRAINS` (JBR). Locally JBR lives at `~/Library/Java/JavaVirtualMachines/jbrsdk-25.0.4.1-osx-aarch64-b583.48`.
- `org.jetbrains.jediterm:jediterm-core:3.76` comes only from `https://packages.jetbrains.team/maven/p/ij/intellij-dependencies`. Do **not** add `jediterm-ui` or `jediterm-pty`.
- `org.jetbrains.pty4j:pty4j:0.13.10` from Maven Central.
- Packages: `dev.moray.terminal` (module `moray-terminal`), `dev.moray.app` (module `moray-app`).
- `moray-terminal` must never depend on `moray-app`. JediTerm is an `implementation` (not `api`) dependency of `moray-terminal`; no public method in `moray-terminal` takes or returns a JediTerm type.
- No interface without two real implementations. No plugin API.
- Child processes get `TERM=xterm-256color` and `COLORTERM=truecolor`.
- JediTerm facts (verified 2026-09-10 against 3.76): cursor X/Y from `JediTerminal.getCursorX()/getCursorY()` are **1-based**; a wide BMP char occupies its cell followed by a `CharUtils.DWC` (`U+E000`) cell; a supplementary char (emoji) occupies two cells holding its two UTF-16 surrogates; default colors are `null` in `TextStyle`; `getLine(0)` is the top screen row and `getLine(-1)` the newest scrollback line; `JediTerminal` locks `TerminalTextBuffer` internally for every write; `getCodeForKey(VK, InputEvent.*_DOWN_MASK)` handles app-cursor mode, F-keys and modifiers; DECSCUSR 0 arrives as `BLINK_BLOCK`; RIS arrives as `null`.

## Scope of this plan

Phase 1 is delivered by four plans, each ending in runnable software:

1. **This plan — terminal core.**
2. Terminal completeness: mouse reporting, selection/copy, scrollback viewing, search, shell integration (OSC 7/133/8, title), DECSCUSR 0 → configured cursor (via the byte-stream observer), exit message in the pane.
3. App chrome: menus, toolbar, tabs, split tree, find bar, status bar, keybindings.
4. Config & packaging: `AppDirs`, TOML config, themes, live reload, error reporting, `--config`, logging, macOS `.app`.

Deliberate simplifications in this plan (revisited by the benchmark gate in Task 10): the view repaints the whole component (coalesced to ≤ 125 Hz) rather than tracking dirty rows. When the shell exits, the app exits.

## File Structure

```
settings.gradle.kts                      modules
build.gradle.kts                         shared: repos, toolchain, JUnit/AssertJ, test config
.gitignore
.github/workflows/ci.yml                 macOS/Linux/Windows `./gradlew check`
moray-terminal/build.gradle.kts
moray-terminal/src/main/java/dev/moray/terminal/
  Palette.java          theme colors + xterm 256-color table; TerminalColor → java.awt.Color
  CellStyle.java        resolved per-cell style (fg/bg after inverse/dim/hidden, bold/italic/underline)
  FontSet.java          primary + fallback fonts, per-code-point font choice, cell metrics, glyph layout
  Run.java              one drawable run of same-style, same-font cells
  RunBuilder.java       TerminalLine → cells → List<Run>
  CursorStyle.java      BLOCK / BEAM / UNDERLINE + mapping from JediTerm CursorShape
  SessionDisplay.java   Moray's TerminalDisplay: cursor visibility/shape, title, bell, mouse/paste modes
  ScreenSnapshot.java   visible lines + cursor copied under the buffer lock
  TerminalSession.java  connector + JediTerminal + reader thread; write/resize/snapshot/listeners/exit
  PtyConnector.java     JediTerm TtyConnector over a pty4j PtyProcess
  TerminalPainter.java  paints a ScreenSnapshot: backgrounds, glyph runs, underline, cursor
  KeyInput.java         platform-neutral key event record
  OptionAsMeta.java     LEFT / RIGHT / BOTH / NONE
  KeyEncoder.java       KeyInput → bytes for the PTY
  GridSize.java         pixels → columns × rows
  TerminalOptions.java  font, fallbacks, ligatures, palette, cursor, Option-as-Meta, scrollback
  TerminalView.java     the Swing component
moray-terminal/src/test/java/dev/moray/terminal/
  PaletteTest, CellStyleTest, FontSetTest, RunBuilderTest, FakeConnector, Await,
  TerminalSessionTest, PtyConnectorTest, TerminalPainterTest, KeyEncoderTest, GridSizeTest, CursorStyleTest
moray-app/build.gradle.kts
moray-app/src/main/java/dev/moray/app/
  DefaultShell.java     login-shell command per OS
  Main.java             one window, one TerminalView
  Bench.java            100 MB ANSI throughput benchmark
moray-app/src/test/java/dev/moray/app/DefaultShellTest.java
```

---

### Task 1: Build skeleton, CI, and the color palette

**Files:**
- Create: `settings.gradle.kts`, `build.gradle.kts`, `.gitignore`, `.github/workflows/ci.yml`
- Create: `moray-terminal/build.gradle.kts`, `moray-app/build.gradle.kts`
- Create: `moray-terminal/src/main/java/dev/moray/terminal/Palette.java`
- Test: `moray-terminal/src/test/java/dev/moray/terminal/PaletteTest.java`

**Interfaces:**
- Produces: `public record Palette(Color foreground, Color background, Color cursor, List<Color> ansi)` with `static Palette morayDark()`, `Color indexed(int index)`, package-private `Color foreground(TerminalColor)`, `Color background(TerminalColor)` (null → defaults).

- [ ] **Step 1: Create the Gradle build files**

`settings.gradle.kts`:
```kotlin
rootProject.name = "moray"
include("moray-terminal", "moray-app")
```

`build.gradle.kts`:
```kotlin
subprojects {
    apply(plugin = "java")

    repositories {
        mavenCentral()
        maven("https://packages.jetbrains.team/maven/p/ij/intellij-dependencies") {
            content { includeGroup("org.jetbrains.jediterm") }
        }
    }

    extensions.configure<JavaPluginExtension> {
        toolchain {
            languageVersion = JavaLanguageVersion.of(25)
            vendor = JvmVendorSpec.JETBRAINS
        }
    }

    dependencies {
        "testImplementation"(platform("org.junit:junit-bom:6.1.3"))
        "testImplementation"("org.junit.jupiter:junit-jupiter")
        "testRuntimeOnly"("org.junit.platform:junit-platform-launcher")
        "testImplementation"("org.assertj:assertj-core:3.27.7")
    }

    tasks.withType<JavaCompile>().configureEach {
        options.compilerArgs.addAll(listOf("-Xlint:all", "-Xlint:-serial"))
    }

    tasks.withType<Test>().configureEach {
        useJUnitPlatform()
        systemProperty("java.awt.headless", "true")
        jvmArgs("--enable-native-access=ALL-UNNAMED")
    }
}
```

`moray-terminal/build.gradle.kts`:
```kotlin
plugins {
    `java-library`
}

dependencies {
    implementation("org.jetbrains.jediterm:jediterm-core:3.76")
    implementation("org.jetbrains.pty4j:pty4j:0.13.10")
    testRuntimeOnly("org.slf4j:slf4j-nop:2.0.13")
}
```

`moray-app/build.gradle.kts`:
```kotlin
plugins {
    application
}

dependencies {
    implementation(project(":moray-terminal"))
    runtimeOnly("org.slf4j:slf4j-nop:2.0.13")
}

application {
    mainClass = "dev.moray.app.Main"
    applicationDefaultJvmArgs = listOf(
        "--enable-native-access=ALL-UNNAMED",
        "-Dapple.awt.application.name=Moray",
    )
}
```

`.gitignore`:
```
.gradle/
build/
out/
.idea/
*.iml
.DS_Store
```

`.github/workflows/ci.yml`:
```yaml
name: ci

on:
  push:
  pull_request:

jobs:
  check:
    strategy:
      fail-fast: false
      matrix:
        os: [macos-latest, ubuntu-latest, windows-latest]
    runs-on: ${{ matrix.os }}
    steps:
      - uses: actions/checkout@v7
      - uses: actions/setup-java@v6
        with:
          distribution: jetbrains
          java-version: '25'
      - uses: gradle/actions/setup-gradle@v6
      - name: Check
        shell: bash
        run: ./gradlew check --stacktrace
```

- [ ] **Step 2: Generate the Gradle wrapper**

Run: `gradle wrapper --gradle-version 9.7.0`
Expected: creates `gradlew`, `gradlew.bat`, `gradle/wrapper/`. Then `./gradlew --version` prints `Gradle 9.7.0`.

- [ ] **Step 3: Write the failing test**

`moray-terminal/src/test/java/dev/moray/terminal/PaletteTest.java`:
```java
package dev.moray.terminal;

import com.jediterm.terminal.TerminalColor;
import org.junit.jupiter.api.Test;

import java.awt.Color;
import java.util.Collections;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PaletteTest {
    private final Palette palette = Palette.morayDark();

    @Test
    void nullColorsUseThemeDefaults() {
        assertThat(palette.foreground(null)).isEqualTo(palette.foreground());
        assertThat(palette.background(null)).isEqualTo(palette.background());
    }

    @Test
    void firstSixteenIndexesComeFromTheTheme() {
        assertThat(palette.foreground(TerminalColor.index(1))).isEqualTo(palette.ansi().get(1));
        assertThat(palette.indexed(15)).isEqualTo(palette.ansi().get(15));
    }

    @Test
    void colorCubeFollowsXtermLevels() {
        assertThat(palette.indexed(16)).isEqualTo(new Color(0, 0, 0));
        assertThat(palette.indexed(196)).isEqualTo(new Color(255, 0, 0));
        assertThat(palette.indexed(231)).isEqualTo(new Color(255, 255, 255));
        assertThat(palette.indexed(67)).isEqualTo(new Color(95, 135, 175));
    }

    @Test
    void grayRampFollowsXterm() {
        assertThat(palette.indexed(232)).isEqualTo(new Color(8, 8, 8));
        assertThat(palette.indexed(255)).isEqualTo(new Color(238, 238, 238));
    }

    @Test
    void rgbColorsPassThrough() {
        assertThat(palette.foreground(TerminalColor.rgb(10, 20, 30))).isEqualTo(new Color(10, 20, 30));
    }

    @Test
    void rejectsAnsiListOfWrongSize() {
        assertThatThrownBy(() -> new Palette(Color.WHITE, Color.BLACK, Color.WHITE, Collections.nCopies(8, Color.RED)))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("16");
    }
}
```

- [ ] **Step 4: Run the test to verify it fails**

Run: `./gradlew :moray-terminal:test --tests dev.moray.terminal.PaletteTest`
Expected: FAIL — compilation error, `cannot find symbol: class Palette`.

- [ ] **Step 5: Implement `Palette`**

`moray-terminal/src/main/java/dev/moray/terminal/Palette.java`:
```java
package dev.moray.terminal;

import com.jediterm.terminal.TerminalColor;

import java.awt.Color;
import java.util.List;

/** Terminal colors: theme defaults, the 16 ANSI colors, and the xterm 256-color table. */
public record Palette(Color foreground, Color background, Color cursor, List<Color> ansi) {

    public Palette {
        if (ansi.size() != 16) {
            throw new IllegalArgumentException("ansi must have 16 colors, got " + ansi.size());
        }
        ansi = List.copyOf(ansi);
    }

    public static Palette morayDark() {
        return new Palette(
            new Color(0xd7dae0), new Color(0x1e2127), new Color(0xd7dae0),
            List.of(
                new Color(0x282c34), new Color(0xe06c75), new Color(0x98c379), new Color(0xe5c07b),
                new Color(0x61afef), new Color(0xc678dd), new Color(0x56b6c2), new Color(0xabb2bf),
                new Color(0x5c6370), new Color(0xef7b85), new Color(0xa9d48a), new Color(0xf0cc8c),
                new Color(0x74bff8), new Color(0xd68bee), new Color(0x67c7d3), new Color(0xe6e9ef)));
    }

    /** xterm color index 0–255. */
    public Color indexed(int index) {
        if (index < 16) {
            return ansi.get(index);
        }
        if (index < 232) {
            int i = index - 16;
            return new Color(cubeLevel(i / 36), cubeLevel((i / 6) % 6), cubeLevel(i % 6));
        }
        int gray = 8 + 10 * (index - 232);
        return new Color(gray, gray, gray);
    }

    Color foreground(TerminalColor color) {
        return color == null ? foreground : resolve(color);
    }

    Color background(TerminalColor color) {
        return color == null ? background : resolve(color);
    }

    private Color resolve(TerminalColor color) {
        if (color.isIndexed()) {
            return indexed(color.getColorIndex());
        }
        com.jediterm.core.Color c = color.toColor();
        return new Color(c.getRed(), c.getGreen(), c.getBlue());
    }

    private static int cubeLevel(int n) {
        return n == 0 ? 0 : 55 + 40 * n;
    }
}
```

- [ ] **Step 6: Run the test to verify it passes**

Run: `./gradlew :moray-terminal:test --tests dev.moray.terminal.PaletteTest`
Expected: PASS (6 tests).

- [ ] **Step 7: Run the full check**

Run: `./gradlew check`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 8: Commit**

```bash
git add settings.gradle.kts build.gradle.kts .gitignore .github gradle gradlew gradlew.bat moray-terminal moray-app
git commit -m "Add Gradle skeleton, CI, and terminal color palette"
```

### Task 2: Cell styles and the font set

**Files:**
- Create: `moray-terminal/src/main/java/dev/moray/terminal/CellStyle.java`
- Create: `moray-terminal/src/main/java/dev/moray/terminal/FontSet.java`
- Test: `moray-terminal/src/test/java/dev/moray/terminal/CellStyleTest.java`
- Test: `moray-terminal/src/test/java/dev/moray/terminal/FontSetTest.java`

**Interfaces:**
- Consumes: `Palette.foreground(TerminalColor)`, `Palette.background(TerminalColor)` (Task 1).
- Produces:
  - `record CellStyle(Color foreground, Color background, boolean bold, boolean italic, boolean underline)` with `static CellStyle resolve(TextStyle style, Palette palette)` (package-private).
  - `public final class FontSet` — `FontSet(String family, float size, List<String> fallbackFamilies, boolean ligatures)`, `Font fontFor(int codePoint, boolean bold, boolean italic)`, `GlyphVector layout(Font font, char[] text)`, `String primaryFamily()`, `int cellWidth()`, `int cellHeight()`, `int ascent()`.

- [ ] **Step 1: Write the failing tests**

`moray-terminal/src/test/java/dev/moray/terminal/CellStyleTest.java`:
```java
package dev.moray.terminal;

import com.jediterm.terminal.TerminalColor;
import com.jediterm.terminal.TextStyle;
import com.jediterm.terminal.TextStyle.Option;
import org.junit.jupiter.api.Test;

import java.awt.Color;
import java.util.EnumSet;

import static org.assertj.core.api.Assertions.assertThat;

class CellStyleTest {
    private final Palette palette = Palette.morayDark();

    @Test
    void emptyStyleUsesThemeDefaults() {
        CellStyle s = CellStyle.resolve(TextStyle.EMPTY, palette);
        assertThat(s.foreground()).isEqualTo(palette.foreground());
        assertThat(s.background()).isEqualTo(palette.background());
        assertThat(s.bold()).isFalse();
        assertThat(s.italic()).isFalse();
        assertThat(s.underline()).isFalse();
    }

    @Test
    void inverseSwapsForegroundAndBackground() {
        TextStyle style = new TextStyle(TerminalColor.index(1), null, EnumSet.of(Option.INVERSE));
        CellStyle s = CellStyle.resolve(style, palette);
        assertThat(s.foreground()).isEqualTo(palette.background());
        assertThat(s.background()).isEqualTo(palette.ansi().get(1));
    }

    @Test
    void hiddenTextUsesBackgroundAsForeground() {
        TextStyle style = new TextStyle(TerminalColor.index(2), null, EnumSet.of(Option.HIDDEN));
        assertThat(CellStyle.resolve(style, palette).foreground()).isEqualTo(palette.background());
    }

    @Test
    void dimBlendsForegroundHalfwayToBackground() {
        TextStyle style = new TextStyle(TerminalColor.rgb(200, 200, 200), TerminalColor.rgb(0, 0, 0), EnumSet.of(Option.DIM));
        assertThat(CellStyle.resolve(style, palette).foreground()).isEqualTo(new Color(100, 100, 100));
    }

    @Test
    void boldItalicUnderlineFlags() {
        TextStyle style = new TextStyle(null, null, EnumSet.of(Option.BOLD, Option.ITALIC, Option.UNDERLINED));
        CellStyle s = CellStyle.resolve(style, palette);
        assertThat(s.bold()).isTrue();
        assertThat(s.italic()).isTrue();
        assertThat(s.underline()).isTrue();
    }
}
```

`moray-terminal/src/test/java/dev/moray/terminal/FontSetTest.java`:
```java
package dev.moray.terminal;

import org.junit.jupiter.api.Test;

import java.awt.Font;
import java.awt.font.GlyphVector;
import java.util.List;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class FontSetTest {
    private static final String MONO = "JetBrains Mono"; // bundled with the JetBrains Runtime

    @Test
    void primaryFontResolvesFromTheRuntime() {
        assertThat(new FontSet(MONO, 14f, List.of(), true).primaryFamily()).isEqualTo(MONO);
    }

    @Test
    void cellMetricsAreSane() {
        FontSet fonts = new FontSet(MONO, 14f, List.of(), true);
        assertThat(fonts.cellWidth()).isPositive();
        assertThat(fonts.cellHeight()).isGreaterThan(fonts.cellWidth());
        assertThat(fonts.ascent()).isPositive().isLessThan(fonts.cellHeight());
    }

    @Test
    void asciiUsesThePrimaryFont() {
        FontSet fonts = new FontSet(MONO, 14f, List.of(Font.DIALOG), true);
        assertThat(fonts.fontFor('a', false, false).getFamily()).isEqualTo(MONO);
    }

    @Test
    void boldAndItalicVariants() {
        FontSet fonts = new FontSet(MONO, 14f, List.of(), true);
        assertThat(fonts.fontFor('a', true, false).isBold()).isTrue();
        assertThat(fonts.fontFor('a', false, true).isItalic()).isTrue();
        assertThat(fonts.fontFor('a', true, true).getStyle()).isEqualTo(Font.BOLD | Font.ITALIC);
    }

    @Test
    void sameCodePointAndStyleReturnsSameInstance() {
        FontSet fonts = new FontSet(MONO, 14f, List.of(), true);
        assertThat(fonts.fontFor('x', false, false)).isSameAs(fonts.fontFor('x', false, false));
    }

    @Test
    void fallsBackWhenPrimaryCannotDisplay() {
        Font primary = new Font(MONO, Font.PLAIN, 14);
        Font fallback = new Font(Font.DIALOG, Font.PLAIN, 14);
        int codePoint = IntStream.of(0x65E5, 0xAC00, 0x2603, 0x1F680)
            .filter(c -> !primary.canDisplay(c) && fallback.canDisplay(c))
            .findFirst().orElse(-1);
        assumeTrue(codePoint != -1, "no code point that Dialog covers but JetBrains Mono lacks on this machine");

        FontSet fonts = new FontSet(MONO, 14f, List.of(Font.DIALOG), true);
        assertThat(fonts.fontFor(codePoint, false, false).getFamily()).isNotEqualTo(MONO);
    }

    @Test
    void ligaturesChangeHowAnArrowIsShaped() {
        char[] arrow = "->".toCharArray();
        FontSet on = new FontSet(MONO, 14f, List.of(), true);
        FontSet off = new FontSet(MONO, 14f, List.of(), false);
        assertThat(glyphCodes(on.layout(on.fontFor('-', false, false), arrow)))
            .isNotEqualTo(glyphCodes(off.layout(off.fontFor('-', false, false), arrow)));
    }

    private static int[] glyphCodes(GlyphVector gv) {
        return gv.getGlyphCodes(0, gv.getNumGlyphs(), null);
    }
}
```

- [ ] **Step 2: Run the tests to verify they fail**

Run: `./gradlew :moray-terminal:test --tests 'dev.moray.terminal.CellStyleTest' --tests 'dev.moray.terminal.FontSetTest'`
Expected: FAIL — compilation errors for `CellStyle` and `FontSet`.

- [ ] **Step 3: Implement `CellStyle`**

`moray-terminal/src/main/java/dev/moray/terminal/CellStyle.java`:
```java
package dev.moray.terminal;

import com.jediterm.terminal.TextStyle;
import com.jediterm.terminal.TextStyle.Option;

import java.awt.Color;

/** A cell's style with colors fully resolved (inverse, dim and hidden already applied). */
record CellStyle(Color foreground, Color background, boolean bold, boolean italic, boolean underline) {

    static CellStyle resolve(TextStyle style, Palette palette) {
        Color fg = palette.foreground(style.getForeground());
        Color bg = palette.background(style.getBackground());
        if (style.hasOption(Option.INVERSE)) {
            Color swap = fg;
            fg = bg;
            bg = swap;
        }
        if (style.hasOption(Option.DIM)) {
            fg = blend(fg, bg, 0.5f);
        }
        if (style.hasOption(Option.HIDDEN)) {
            fg = bg;
        }
        return new CellStyle(fg, bg,
            style.hasOption(Option.BOLD), style.hasOption(Option.ITALIC), style.hasOption(Option.UNDERLINED));
    }

    static Color blend(Color from, Color to, float amount) {
        return new Color(
            Math.round(from.getRed() + (to.getRed() - from.getRed()) * amount),
            Math.round(from.getGreen() + (to.getGreen() - from.getGreen()) * amount),
            Math.round(from.getBlue() + (to.getBlue() - from.getBlue()) * amount));
    }
}
```

- [ ] **Step 4: Implement `FontSet`**

`moray-terminal/src/main/java/dev/moray/terminal/FontSet.java`:
```java
package dev.moray.terminal;

import java.awt.Font;
import java.awt.RenderingHints;
import java.awt.font.FontRenderContext;
import java.awt.font.GlyphVector;
import java.awt.font.LineMetrics;
import java.awt.font.TextAttribute;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The primary font plus fallbacks, chosen per code point, and the cell grid metrics.
 * Used on the Event Dispatch Thread only.
 */
public final class FontSet {
    private static final FontRenderContext FRC = new FontRenderContext(
        null, RenderingHints.VALUE_TEXT_ANTIALIAS_ON, RenderingHints.VALUE_FRACTIONALMETRICS_ON);

    /** [font index][style index]: style index = (bold ? 1 : 0) | (italic ? 2 : 0). */
    private final Font[][] fonts;
    private final boolean ligatures;
    private final Map<Integer, Integer> fontIndexByCodePoint = new HashMap<>();
    private final int cellWidth;
    private final int cellHeight;
    private final int ascent;

    public FontSet(String family, float size, List<String> fallbackFamilies, boolean ligatures) {
        this.ligatures = ligatures;
        List<String> families = new ArrayList<>();
        families.add(family);
        families.addAll(fallbackFamilies);
        fonts = new Font[families.size()][];
        for (int i = 0; i < families.size(); i++) {
            Font plain = create(families.get(i), size, ligatures);
            fonts[i] = new Font[] {
                plain, plain.deriveFont(Font.BOLD), plain.deriveFont(Font.ITALIC), plain.deriveFont(Font.BOLD | Font.ITALIC)};
        }
        Font primary = fonts[0][0];
        cellWidth = Math.max(1, Math.round(primary.createGlyphVector(FRC, "M").getGlyphMetrics(0).getAdvance()));
        LineMetrics metrics = primary.getLineMetrics("Mg", FRC);
        ascent = (int) Math.ceil(metrics.getAscent());
        cellHeight = Math.max(1, (int) Math.ceil(metrics.getAscent() + metrics.getDescent() + metrics.getLeading()));
    }

    public Font fontFor(int codePoint, boolean bold, boolean italic) {
        int index = fontIndexByCodePoint.computeIfAbsent(codePoint, this::firstFontThatCanDisplay);
        return fonts[index][(bold ? 1 : 0) | (italic ? 2 : 0)];
    }

    /** Shaped (ligatures on) or unshaped (ligatures off) glyphs for one run of text. */
    public GlyphVector layout(Font font, char[] text) {
        return ligatures
            ? font.layoutGlyphVector(FRC, text, 0, text.length, Font.LAYOUT_LEFT_TO_RIGHT)
            : font.createGlyphVector(FRC, text);
    }

    public String primaryFamily() {
        return fonts[0][0].getFamily();
    }

    public int cellWidth() {
        return cellWidth;
    }

    public int cellHeight() {
        return cellHeight;
    }

    public int ascent() {
        return ascent;
    }

    private int firstFontThatCanDisplay(int codePoint) {
        for (int i = 0; i < fonts.length; i++) {
            if (fonts[i][0].canDisplay(codePoint)) {
                return i;
            }
        }
        return 0;
    }

    private static Font create(String family, float size, boolean ligatures) {
        Map<TextAttribute, Object> attributes = new HashMap<>();
        attributes.put(TextAttribute.FAMILY, family);
        attributes.put(TextAttribute.SIZE, size);
        if (ligatures) {
            attributes.put(TextAttribute.LIGATURES, TextAttribute.LIGATURES_ON);
        }
        return new Font(attributes);
    }
}
```

- [ ] **Step 5: Run the tests to verify they pass**

Run: `./gradlew :moray-terminal:test --tests 'dev.moray.terminal.CellStyleTest' --tests 'dev.moray.terminal.FontSetTest'`
Expected: PASS (5 + 7 tests; `fallsBackWhenPrimaryCannotDisplay` may be reported as skipped on a machine without a suitable code point).

- [ ] **Step 6: Commit**

```bash
git add moray-terminal/src
git commit -m "Add cell style resolution and font set with fallback and ligature shaping"
```

### Task 3: Runs — turning a terminal line into drawable pieces

**Files:**
- Create: `moray-terminal/src/main/java/dev/moray/terminal/Run.java`
- Create: `moray-terminal/src/main/java/dev/moray/terminal/RunBuilder.java`
- Test: `moray-terminal/src/test/java/dev/moray/terminal/RunBuilderTest.java`

**Interfaces:**
- Consumes: `CellStyle.resolve(TextStyle, Palette)`, `FontSet.fontFor(int, boolean, boolean)` (Task 2).
- Produces (package-private):
  - `record Run(int startColumn, int columns, char[] text, int[] charColumns, CellStyle style, Font font)` — `charColumns[i]` is the column of `text[i]` relative to `startColumn`; both halves of a surrogate pair map to the same column.
  - `final class RunBuilder` — `RunBuilder(FontSet fonts, Palette palette)`, `List<Run> build(TerminalLine line, int width)`, `List<Run> build(char[] chars, TextStyle[] styles, int width)`, `static void readCells(TerminalLine line, int width, char[] chars, TextStyle[] styles)`.

Rules: a new run starts whenever the resolved `CellStyle` or the chosen `Font` changes. A cell followed by a `CharUtils.DWC` cell spans two columns and the DWC cell contributes no character. A high+low surrogate pair in two cells is one code point spanning two columns. A DWC cell not preceded by its wide character renders as a space. `NUL` characters render as spaces. Cells past the end of the line are spaces with `TextStyle.EMPTY`.

- [ ] **Step 1: Write the failing test**

`moray-terminal/src/test/java/dev/moray/terminal/RunBuilderTest.java`:
```java
package dev.moray.terminal;

import com.jediterm.terminal.TerminalColor;
import com.jediterm.terminal.TextStyle;
import com.jediterm.terminal.model.CharBuffer;
import com.jediterm.terminal.model.TerminalLine;
import com.jediterm.terminal.util.CharUtils;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class RunBuilderTest {
    private static final int WIDTH = 6;
    private static final TextStyle RED = new TextStyle(TerminalColor.index(1), null);

    // No fallback fonts, so every code point uses the primary font and only styles split runs.
    private final Palette palette = Palette.morayDark();
    private final RunBuilder builder = new RunBuilder(new FontSet("JetBrains Mono", 14f, List.of(), true), palette);

    @Test
    void plainTextIsOneRunCoveringTheRow() {
        List<Run> runs = builder.build("ab    ".toCharArray(), styles(TextStyle.EMPTY, WIDTH), WIDTH);

        assertThat(runs).hasSize(1);
        Run run = runs.getFirst();
        assertThat(run.startColumn()).isZero();
        assertThat(run.columns()).isEqualTo(6);
        assertThat(new String(run.text())).isEqualTo("ab    ");
        assertThat(run.charColumns()).containsExactly(0, 1, 2, 3, 4, 5);
    }

    @Test
    void styleChangeStartsANewRun() {
        TextStyle[] styles = styles(TextStyle.EMPTY, WIDTH);
        Arrays.fill(styles, 2, WIDTH, RED);

        List<Run> runs = builder.build("abcdef".toCharArray(), styles, WIDTH);

        assertThat(runs).hasSize(2);
        assertThat(runs.get(0).columns()).isEqualTo(2);
        assertThat(runs.get(1).startColumn()).isEqualTo(2);
        assertThat(runs.get(1).columns()).isEqualTo(4);
        assertThat(new String(runs.get(1).text())).isEqualTo("cdef");
        assertThat(runs.get(1).style().foreground()).isEqualTo(palette.ansi().get(1));
    }

    @Test
    void wideCharacterSpansTwoColumns() {
        char[] chars = {'日', CharUtils.DWC, 'x', ' ', ' ', ' '};

        Run run = builder.build(chars, styles(TextStyle.EMPTY, WIDTH), WIDTH).getFirst();

        assertThat(new String(run.text())).isEqualTo("日x   ");
        assertThat(run.charColumns()).containsExactly(0, 2, 3, 4, 5);
        assertThat(run.columns()).isEqualTo(6);
    }

    @Test
    void surrogatePairIsOneCodePointOverTwoColumns() {
        char[] chars = {'a', '\uD83D', '\uDE80', 'b', ' ', ' '};

        Run run = builder.build(chars, styles(TextStyle.EMPTY, WIDTH), WIDTH).getFirst();

        assertThat(new String(run.text())).isEqualTo("a🚀b  ");
        assertThat(run.charColumns()).containsExactly(0, 1, 1, 3, 4, 5);
    }

    @Test
    void orphanContinuationCellRendersAsSpace() {
        char[] chars = {CharUtils.DWC, 'a', ' ', ' ', ' ', ' '};

        Run run = builder.build(chars, styles(TextStyle.EMPTY, WIDTH), WIDTH).getFirst();

        assertThat(new String(run.text())).isEqualTo(" a    ");
    }

    @Test
    void readCellsPadsWithSpacesAndEmptyStyle() {
        TerminalLine line = new TerminalLine(new TerminalLine.TextEntry(RED, new CharBuffer("hi")));
        char[] chars = new char[4];
        TextStyle[] styles = new TextStyle[4];

        RunBuilder.readCells(line, 4, chars, styles);

        assertThat(new String(chars)).isEqualTo("hi  ");
        assertThat(styles[0]).isEqualTo(RED);
        assertThat(styles[3]).isEqualTo(TextStyle.EMPTY);
    }

    @Test
    void readCellsTurnsNulIntoSpace() {
        TerminalLine line = new TerminalLine(new TerminalLine.TextEntry(TextStyle.EMPTY, new CharBuffer(CharUtils.NUL_CHAR, 3)));
        char[] chars = new char[3];

        RunBuilder.readCells(line, 3, chars, new TextStyle[3]);

        assertThat(new String(chars)).isEqualTo("   ");
    }

    @Test
    void buildFromTerminalLineCombinesReadAndBuild() {
        TerminalLine line = new TerminalLine(new TerminalLine.TextEntry(RED, new CharBuffer("ok")));

        List<Run> runs = builder.build(line, 4);

        assertThat(runs).hasSize(2);
        assertThat(new String(runs.get(0).text())).isEqualTo("ok");
        assertThat(new String(runs.get(1).text())).isEqualTo("  ");
    }

    private static TextStyle[] styles(TextStyle style, int width) {
        TextStyle[] styles = new TextStyle[width];
        Arrays.fill(styles, style);
        return styles;
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `./gradlew :moray-terminal:test --tests dev.moray.terminal.RunBuilderTest`
Expected: FAIL — compilation errors for `Run` and `RunBuilder`.

- [ ] **Step 3: Implement `Run`**

`moray-terminal/src/main/java/dev/moray/terminal/Run.java`:
```java
package dev.moray.terminal;

import java.awt.Font;

/**
 * Consecutive cells sharing one style and one font, drawn in a single call.
 * {@code charColumns[i]} is the column of {@code text[i]}, relative to {@code startColumn}.
 */
record Run(int startColumn, int columns, char[] text, int[] charColumns, CellStyle style, Font font) {
}
```

- [ ] **Step 4: Implement `RunBuilder`**

`moray-terminal/src/main/java/dev/moray/terminal/RunBuilder.java`:
```java
package dev.moray.terminal;

import com.jediterm.terminal.TextStyle;
import com.jediterm.terminal.model.CharBuffer;
import com.jediterm.terminal.model.TerminalLine;
import com.jediterm.terminal.util.CharUtils;

import java.awt.Font;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Splits a terminal line into runs of identical style and font. Event Dispatch Thread only. */
final class RunBuilder {
    private static final int MAX_CACHED_STYLES = 4096;

    private final FontSet fonts;
    private final Palette palette;
    private final Map<TextStyle, CellStyle> styleCache = new HashMap<>();

    RunBuilder(FontSet fonts, Palette palette) {
        this.fonts = fonts;
        this.palette = palette;
    }

    List<Run> build(TerminalLine line, int width) {
        char[] chars = new char[width];
        TextStyle[] styles = new TextStyle[width];
        readCells(line, width, chars, styles);
        return build(chars, styles, width);
    }

    static void readCells(TerminalLine line, int width, char[] chars, TextStyle[] styles) {
        Arrays.fill(chars, 0, width, ' ');
        Arrays.fill(styles, 0, width, TextStyle.EMPTY);
        int column = 0;
        for (TerminalLine.TextEntry entry : line.getEntries()) {
            CharBuffer text = entry.getText();
            TextStyle style = entry.getStyle();
            for (int i = 0; i < text.length() && column < width; i++, column++) {
                char c = text.charAt(i);
                chars[column] = c == CharUtils.NUL_CHAR ? ' ' : c;
                styles[column] = style;
            }
        }
    }

    List<Run> build(char[] chars, TextStyle[] styles, int width) {
        List<Run> runs = new ArrayList<>();
        Accumulator current = null;
        int column = 0;
        while (column < width) {
            char first = chars[column];
            char second = 0;
            int codePoint;
            int span;
            if (Character.isHighSurrogate(first) && column + 1 < width && Character.isLowSurrogate(chars[column + 1])) {
                second = chars[column + 1];
                codePoint = Character.toCodePoint(first, second);
                span = 2;
            } else {
                if (first == CharUtils.DWC) {
                    first = ' ';
                }
                codePoint = first;
                span = column + 1 < width && chars[column + 1] == CharUtils.DWC ? 2 : 1;
            }
            CellStyle style = styleOf(styles[column]);
            Font font = fonts.fontFor(codePoint, style.bold(), style.italic());
            if (current == null || !current.accepts(style, font)) {
                if (current != null) {
                    runs.add(current.toRun());
                }
                current = new Accumulator(column, style, font);
            }
            current.add(first, column);
            if (second != 0) {
                current.add(second, column);
            }
            column += span;
            current.endColumn = column;
        }
        if (current != null) {
            runs.add(current.toRun());
        }
        return runs;
    }

    private CellStyle styleOf(TextStyle style) {
        if (styleCache.size() > MAX_CACHED_STYLES) {
            styleCache.clear();
        }
        return styleCache.computeIfAbsent(style, s -> CellStyle.resolve(s, palette));
    }

    private static final class Accumulator {
        private final int startColumn;
        private final CellStyle style;
        private final Font font;
        private final StringBuilder text = new StringBuilder();
        private int[] charColumns = new int[16];
        private int endColumn;

        Accumulator(int startColumn, CellStyle style, Font font) {
            this.startColumn = startColumn;
            this.endColumn = startColumn;
            this.style = style;
            this.font = font;
        }

        boolean accepts(CellStyle style, Font font) {
            return this.font == font && this.style.equals(style);
        }

        void add(char c, int column) {
            if (text.length() == charColumns.length) {
                charColumns = Arrays.copyOf(charColumns, charColumns.length * 2);
            }
            charColumns[text.length()] = column - startColumn;
            text.append(c);
        }

        Run toRun() {
            int length = text.length();
            char[] chars = new char[length];
            text.getChars(0, length, chars, 0);
            return new Run(startColumn, endColumn - startColumn, chars, Arrays.copyOf(charColumns, length), style, font);
        }
    }
}
```

- [ ] **Step 5: Run the test to verify it passes**

Run: `./gradlew :moray-terminal:test --tests dev.moray.terminal.RunBuilderTest`
Expected: PASS (8 tests).

- [ ] **Step 6: Commit**

```bash
git add moray-terminal/src
git commit -m "Add run builder that groups cells by style and font"
```

### Task 4: The terminal session (emulator + reader thread)

**Files:**
- Create: `moray-terminal/src/main/java/dev/moray/terminal/SessionDisplay.java`
- Create: `moray-terminal/src/main/java/dev/moray/terminal/ScreenSnapshot.java`
- Create: `moray-terminal/src/main/java/dev/moray/terminal/TerminalSession.java`
- Test: `moray-terminal/src/test/java/dev/moray/terminal/FakeConnector.java`
- Test: `moray-terminal/src/test/java/dev/moray/terminal/Await.java`
- Test: `moray-terminal/src/test/java/dev/moray/terminal/TerminalSessionTest.java`

**Interfaces:**
- Consumes: nothing from earlier tasks.
- Produces:
  - `final class SessionDisplay implements TerminalDisplay` (package-private) — `SessionDisplay(Consumer<String> onTitle, Runnable onBell)`; getters `boolean cursorVisible()`, `CursorShape cursorShape()` (null = no application request), `String title()`, `MouseMode mouseMode()`, `MouseFormat mouseFormat()`, `boolean bracketedPaste()`.
  - `record ScreenSnapshot(int width, int height, List<TerminalLine> lines, int cursorColumn, int cursorRow, boolean cursorVisible, CursorShape cursorShape)` (package-private; cursor is 0-based) — `static ScreenSnapshot capture(TerminalTextBuffer, JediTerminal, SessionDisplay)`, `String lineText(int row)`.
  - `public final class TerminalSession implements AutoCloseable`:
    - `public interface Listener { default void screenChanged() {} default void titleChanged(String title) {} default void bell() {} }` — called on the reader thread.
    - package-private `TerminalSession(TtyConnector connector, int columns, int rows, int scrollback)` and `void startReading()`.
    - `public void write(byte[])`, `public void write(String)`, `public void resize(int columns, int rows)`, `public int columns()`, `public int rows()`, `public String title()`, `public void addListener(Listener)`, `public void removeListener(Listener)`, `public CompletableFuture<Integer> exitFuture()`, `public void close()`.
    - package-private `ScreenSnapshot snapshot()`, `byte[] codeForKey(int keyCode, int modifiers)`, `SessionDisplay display()`.
    - Task 5 adds `public static TerminalSession start(...)`.

- [ ] **Step 1: Write the test helpers**

`moray-terminal/src/test/java/dev/moray/terminal/FakeConnector.java`:
```java
package dev.moray.terminal;

import com.jediterm.core.util.TermSize;
import com.jediterm.terminal.TtyConnector;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PipedReader;
import java.io.PipedWriter;
import java.nio.charset.StandardCharsets;

/** In-memory stand-in for a PTY: tests feed "program output" and inspect what the terminal wrote back. */
final class FakeConnector implements TtyConnector {
    private final PipedWriter output = new PipedWriter();
    private final PipedReader reader;
    private final ByteArrayOutputStream written = new ByteArrayOutputStream();
    private volatile TermSize lastResize;

    FakeConnector() throws IOException {
        reader = new PipedReader(output, 1 << 16);
    }

    void feed(String text) throws IOException {
        output.write(text);
        output.flush();
    }

    /** Simulates the program exiting: the reader sees end of stream. */
    void finish() throws IOException {
        output.close();
    }

    synchronized String written() {
        return written.toString(StandardCharsets.UTF_8);
    }

    TermSize lastResize() {
        return lastResize;
    }

    @Override
    public int read(char[] buf, int offset, int length) throws IOException {
        return reader.read(buf, offset, length);
    }

    @Override
    public synchronized void write(byte[] bytes) {
        written.writeBytes(bytes);
    }

    @Override
    public void write(String string) {
        write(string.getBytes(StandardCharsets.UTF_8));
    }

    @Override
    public boolean isConnected() {
        return true;
    }

    @Override
    public void resize(TermSize size) {
        lastResize = size;
    }

    @Override
    public int waitFor() {
        return 0;
    }

    @Override
    public boolean ready() throws IOException {
        return reader.ready();
    }

    @Override
    public String getName() {
        return "fake";
    }

    @Override
    public void close() {
        try {
            output.close();
        } catch (IOException ignored) {
            // already closed
        }
    }
}
```

`moray-terminal/src/test/java/dev/moray/terminal/Await.java`:
```java
package dev.moray.terminal;

import java.time.Duration;
import java.util.function.BooleanSupplier;

final class Await {
    private Await() {
    }

    static void until(BooleanSupplier condition, String description) throws InterruptedException {
        long deadline = System.nanoTime() + Duration.ofSeconds(10).toNanos();
        while (!condition.getAsBoolean()) {
            if (System.nanoTime() > deadline) {
                throw new AssertionError("timed out waiting for: " + description);
            }
            Thread.sleep(10);
        }
    }
}
```

- [ ] **Step 2: Write the failing test**

`moray-terminal/src/test/java/dev/moray/terminal/TerminalSessionTest.java`:
```java
package dev.moray.terminal;

import com.jediterm.core.util.TermSize;
import com.jediterm.terminal.CursorShape;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class TerminalSessionTest {
    private FakeConnector connector;
    private TerminalSession session;

    @BeforeEach
    void start() throws Exception {
        connector = new FakeConnector();
        session = new TerminalSession(connector, 20, 4, 100);
        session.startReading();
    }

    @AfterEach
    void stop() {
        session.close();
    }

    @Test
    void outputAppearsOnScreenWithZeroBasedCursor() throws Exception {
        connector.feed("hello");

        Await.until(() -> session.snapshot().lineText(0).equals("hello"), "hello on row 0");
        ScreenSnapshot snapshot = session.snapshot();
        assertThat(snapshot.cursorColumn()).isEqualTo(5);
        assertThat(snapshot.cursorRow()).isZero();
        assertThat(snapshot.width()).isEqualTo(20);
        assertThat(snapshot.height()).isEqualTo(4);
    }

    @Test
    void screenChangesAreReported() throws Exception {
        AtomicInteger changes = new AtomicInteger();
        session.addListener(new TerminalSession.Listener() {
            @Override
            public void screenChanged() {
                changes.incrementAndGet();
            }
        });

        connector.feed("x");

        Await.until(() -> changes.get() > 0, "a screenChanged callback");
    }

    @Test
    void cursorPositionRequestIsAnsweredThroughTheConnector() throws Exception {
        connector.feed("ab\033[6n");

        Await.until(() -> connector.written().equals("\033[1;3R"), "cursor position report");
    }

    @Test
    void titleIsReported() throws Exception {
        AtomicReference<String> title = new AtomicReference<>();
        session.addListener(new TerminalSession.Listener() {
            @Override
            public void titleChanged(String newTitle) {
                title.set(newTitle);
            }
        });

        connector.feed("\033]0;my title\007");

        Await.until(() -> "my title".equals(title.get()), "title callback");
        assertThat(session.title()).isEqualTo("my title");
    }

    @Test
    void bellIsReported() throws Exception {
        AtomicInteger bells = new AtomicInteger();
        session.addListener(new TerminalSession.Listener() {
            @Override
            public void bell() {
                bells.incrementAndGet();
            }
        });

        connector.feed("\007");

        Await.until(() -> bells.get() == 1, "bell callback");
    }

    @Test
    void writeGoesToTheConnector() {
        session.write("ls\r");

        assertThat(connector.written()).isEqualTo("ls\r");
    }

    @Test
    void resizeUpdatesBufferAndConnector() {
        session.resize(30, 10);

        ScreenSnapshot snapshot = session.snapshot();
        assertThat(snapshot.width()).isEqualTo(30);
        assertThat(snapshot.height()).isEqualTo(10);
        assertThat(connector.lastResize()).isEqualTo(new TermSize(30, 10));
        assertThat(session.columns()).isEqualTo(30);
        assertThat(session.rows()).isEqualTo(10);
    }

    @Test
    void cursorShapeRequestsAreRecordedAndClearedByReset() throws Exception {
        connector.feed("\033[6 q");
        Await.until(() -> session.display().cursorShape() == CursorShape.STEADY_VERTICAL_BAR, "beam cursor request");

        connector.feed("\033c");
        Await.until(() -> session.display().cursorShape() == null, "reset clears the request");
    }

    @Test
    void exitFutureCompletesWhenOutputEnds() throws Exception {
        connector.finish();

        assertThat(session.exitFuture().get(10, TimeUnit.SECONDS)).isZero();
    }
}
```

- [ ] **Step 3: Run the test to verify it fails**

Run: `./gradlew :moray-terminal:test --tests dev.moray.terminal.TerminalSessionTest`
Expected: FAIL — compilation errors for `TerminalSession`, `ScreenSnapshot`.

- [ ] **Step 4: Implement `SessionDisplay`**

`moray-terminal/src/main/java/dev/moray/terminal/SessionDisplay.java`:
```java
package dev.moray.terminal;

import com.jediterm.terminal.CursorShape;
import com.jediterm.terminal.TerminalDisplay;
import com.jediterm.terminal.emulator.mouse.MouseFormat;
import com.jediterm.terminal.emulator.mouse.MouseMode;
import com.jediterm.terminal.model.TerminalSelection;

import java.util.function.Consumer;

/**
 * Receives display callbacks from the emulator on the reader thread and keeps the resulting state
 * for the view to read. Drawing is done by {@code TerminalView}, not here.
 */
final class SessionDisplay implements TerminalDisplay {
    private final Consumer<String> onTitle;
    private final Runnable onBell;
    private volatile boolean cursorVisible = true;
    private volatile CursorShape cursorShape;
    private volatile String title = "";
    private volatile MouseMode mouseMode = MouseMode.MOUSE_REPORTING_NONE;
    private volatile MouseFormat mouseFormat;
    private volatile boolean bracketedPaste;

    SessionDisplay(Consumer<String> onTitle, Runnable onBell) {
        this.onTitle = onTitle;
        this.onBell = onBell;
    }

    @Override
    public void setCursor(int x, int y) {
        // The view reads the cursor position from JediTerminal when it snapshots.
    }

    @Override
    public void setCursorShape(CursorShape shape) {
        cursorShape = shape;
    }

    @Override
    public void beep() {
        onBell.run();
    }

    @Override
    public void scrollArea(int scrollRegionTop, int scrollRegionSize, int dy) {
        // The view repaints from the buffer; nothing to scroll here.
    }

    @Override
    public void setCursorVisible(boolean visible) {
        cursorVisible = visible;
    }

    @Override
    public void useAlternateScreenBuffer(boolean useAlternateScreenBuffer) {
        // TerminalTextBuffer tracks this itself.
    }

    @Override
    public String getWindowTitle() {
        return title;
    }

    @Override
    public void setWindowTitle(String newTitle) {
        title = newTitle == null ? "" : newTitle;
        onTitle.accept(title);
    }

    @Override
    public TerminalSelection getSelection() {
        return null; // selection arrives in plan 2
    }

    @Override
    public void terminalMouseModeSet(MouseMode mode) {
        mouseMode = mode;
    }

    @Override
    public void setMouseFormat(MouseFormat format) {
        mouseFormat = format;
    }

    @Override
    public boolean ambiguousCharsAreDoubleWidth() {
        return false;
    }

    @Override
    public void setBracketedPasteMode(boolean enabled) {
        bracketedPaste = enabled;
    }

    boolean cursorVisible() {
        return cursorVisible;
    }

    /** The shape last requested by the application, or null for "use the configured shape". */
    CursorShape cursorShape() {
        return cursorShape;
    }

    String title() {
        return title;
    }

    MouseMode mouseMode() {
        return mouseMode;
    }

    MouseFormat mouseFormat() {
        return mouseFormat;
    }

    boolean bracketedPaste() {
        return bracketedPaste;
    }
}
```

- [ ] **Step 5: Implement `ScreenSnapshot`**

`moray-terminal/src/main/java/dev/moray/terminal/ScreenSnapshot.java`:
```java
package dev.moray.terminal;

import com.jediterm.terminal.CursorShape;
import com.jediterm.terminal.model.JediTerminal;
import com.jediterm.terminal.model.TerminalLine;
import com.jediterm.terminal.model.TerminalTextBuffer;

import java.util.ArrayList;
import java.util.List;

/** The visible screen, copied under the buffer lock so painting never races the emulator. Cursor is 0-based. */
record ScreenSnapshot(int width, int height, List<TerminalLine> lines,
                      int cursorColumn, int cursorRow, boolean cursorVisible, CursorShape cursorShape) {

    static ScreenSnapshot capture(TerminalTextBuffer buffer, JediTerminal terminal, SessionDisplay display) {
        buffer.lock();
        try {
            int width = buffer.getWidth();
            int height = buffer.getHeight();
            List<TerminalLine> lines = new ArrayList<>(height);
            for (int row = 0; row < height; row++) {
                lines.add(buffer.getLine(row).copy());
            }
            return new ScreenSnapshot(width, height, lines,
                terminal.getCursorX() - 1, terminal.getCursorY() - 1,
                display.cursorVisible(), display.cursorShape());
        } finally {
            buffer.unlock();
        }
    }

    String lineText(int row) {
        return lines.get(row).getText();
    }
}
```

- [ ] **Step 6: Implement `TerminalSession`**

`moray-terminal/src/main/java/dev/moray/terminal/TerminalSession.java`:
```java
package dev.moray.terminal;

import com.jediterm.core.util.TermSize;
import com.jediterm.terminal.RequestOrigin;
import com.jediterm.terminal.TerminalOutputStream;
import com.jediterm.terminal.TtyBasedArrayDataStream;
import com.jediterm.terminal.TtyConnector;
import com.jediterm.terminal.emulator.JediEmulator;
import com.jediterm.terminal.model.JediTerminal;
import com.jediterm.terminal.model.StyleState;
import com.jediterm.terminal.model.TerminalTextBuffer;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;

/** A program running in a pseudo-terminal, emulated by JediTerm on a dedicated reader thread. */
public final class TerminalSession implements AutoCloseable {

    /** Callbacks arrive on the session's reader thread. */
    public interface Listener {
        default void screenChanged() {
        }

        default void titleChanged(String title) {
        }

        default void bell() {
        }
    }

    private final TtyConnector connector;
    private final TerminalTextBuffer buffer;
    private final JediTerminal terminal;
    private final SessionDisplay display;
    private final List<Listener> listeners = new CopyOnWriteArrayList<>();
    private final CompletableFuture<Integer> exit = new CompletableFuture<>();
    private volatile int columns;
    private volatile int rows;
    private volatile Thread reader;

    TerminalSession(TtyConnector connector, int columns, int rows, int scrollback) {
        this.connector = connector;
        this.columns = columns;
        this.rows = rows;
        StyleState styleState = new StyleState();
        buffer = new TerminalTextBuffer(columns, rows, styleState, scrollback);
        display = new SessionDisplay(
            title -> listeners.forEach(l -> l.titleChanged(title)),
            () -> listeners.forEach(Listener::bell));
        terminal = new JediTerminal(display, buffer, styleState);
        terminal.setTerminalOutput(new TerminalOutputStream() {
            @Override
            public void sendBytes(byte[] bytes, boolean userInput) {
                write(bytes);
            }

            @Override
            public void sendString(String string, boolean userInput) {
                write(string);
            }
        });
        buffer.addModelListener(() -> listeners.forEach(Listener::screenChanged));
    }

    void startReading() {
        reader = Thread.ofPlatform().name("moray-session-reader").daemon().start(this::readLoop);
    }

    private void readLoop() {
        JediEmulator emulator = new JediEmulator(new TtyBasedArrayDataStream(connector), terminal);
        try {
            while (!Thread.currentThread().isInterrupted() && emulator.hasNext()) {
                emulator.next();
            }
        } catch (IOException endOfStream) {
            // End of output, or EIO from a macOS PTY whose child has exited.
        } catch (RuntimeException emulatorFailure) {
            emulatorFailure.printStackTrace(); // replaced by the app log in plan 4
        }
        int code;
        try {
            code = connector.waitFor();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            code = -1;
        }
        exit.complete(code);
    }

    public void write(byte[] bytes) {
        try {
            connector.write(bytes);
        } catch (IOException closed) {
            // The program has exited; input is dropped.
        }
    }

    public void write(String text) {
        write(text.getBytes(StandardCharsets.UTF_8));
    }

    public void resize(int newColumns, int newRows) {
        if (newColumns == columns && newRows == rows) {
            return;
        }
        columns = newColumns;
        rows = newRows;
        TermSize size = new TermSize(newColumns, newRows);
        buffer.lock();
        try {
            terminal.resize(size, RequestOrigin.User);
        } finally {
            buffer.unlock();
        }
        connector.resize(size);
    }

    public int columns() {
        return columns;
    }

    public int rows() {
        return rows;
    }

    public String title() {
        return display.title();
    }

    public void addListener(Listener listener) {
        listeners.add(listener);
    }

    public void removeListener(Listener listener) {
        listeners.remove(listener);
    }

    /** Completes with the exit code once the program's output has ended. */
    public CompletableFuture<Integer> exitFuture() {
        return exit.copy();
    }

    @Override
    public void close() {
        connector.close();
        Thread thread = reader;
        if (thread != null) {
            thread.interrupt();
        }
    }

    ScreenSnapshot snapshot() {
        return ScreenSnapshot.capture(buffer, terminal, display);
    }

    byte[] codeForKey(int keyCode, int modifiers) {
        return terminal.getCodeForKey(keyCode, modifiers);
    }

    SessionDisplay display() {
        return display;
    }
}
```

- [ ] **Step 7: Run the test to verify it passes**

Run: `./gradlew :moray-terminal:test --tests dev.moray.terminal.TerminalSessionTest`
Expected: PASS (9 tests).

- [ ] **Step 8: Commit**

```bash
git add moray-terminal/src
git commit -m "Add terminal session with emulator reader thread and screen snapshots"
```

### Task 5: Real processes through pty4j

**Files:**
- Create: `moray-terminal/src/main/java/dev/moray/terminal/PtyConnector.java`
- Modify: `moray-terminal/src/main/java/dev/moray/terminal/TerminalSession.java` (add `start`)
- Test: `moray-terminal/src/test/java/dev/moray/terminal/PtyConnectorTest.java`

**Interfaces:**
- Consumes: `TerminalSession(TtyConnector, int, int, int)`, `startReading()`, `snapshot()`, `exitFuture()` (Task 4); `Await.until` (Task 4 test helper).
- Produces: `public static TerminalSession start(List<String> command, Map<String, String> environment, Path workingDirectory, int columns, int rows, int scrollback) throws IOException` — adds `TERM=xterm-256color` and `COLORTERM=truecolor` to the environment.

- [ ] **Step 1: Write the failing test**

`moray-terminal/src/test/java/dev/moray/terminal/PtyConnectorTest.java`:
```java
package dev.moray.terminal;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.OS;

import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

class PtyConnectorTest {
    private static final boolean WINDOWS = System.getProperty("os.name").startsWith("Windows");

    @Test
    void programOutputReachesTheScreenAndExitCodeIsReported() throws Exception {
        List<String> command = WINDOWS
            ? List.of("cmd.exe", "/c", "echo moray-pty-ok")
            : List.of("/bin/sh", "-c", "echo moray-pty-ok");
        try (TerminalSession session = start(command)) {
            Await.until(() -> screenText(session).contains("moray-pty-ok"), "echo output on screen");
            assertThat(session.exitFuture().get(10, TimeUnit.SECONDS)).isZero();
        }
    }

    @Test
    @DisabledOnOs(OS.WINDOWS)
    void childSeesTerminalEnvironment() throws Exception {
        try (TerminalSession session = start(List.of("/bin/sh", "-c", "printf '%s %s' \"$TERM\" \"$COLORTERM\""))) {
            Await.until(() -> screenText(session).contains("xterm-256color truecolor"), "TERM and COLORTERM");
        }
    }

    @Test
    @DisabledOnOs(OS.WINDOWS)
    void childSeesTheInitialWindowSize() throws Exception {
        try (TerminalSession session = start(List.of("/bin/sh", "-c", "stty size"))) {
            Await.until(() -> screenText(session).contains("24 80"), "stty reports 24 rows, 80 columns");
        }
    }

    @Test
    @DisabledOnOs(OS.WINDOWS)
    void inputReachesTheProgram() throws Exception {
        try (TerminalSession session = start(List.of("/bin/sh", "-c", "read line; echo \"got:$line\""))) {
            session.write("abc\r");
            Await.until(() -> screenText(session).contains("got:abc"), "program echoes its input");
        }
    }

    private static TerminalSession start(List<String> command) throws Exception {
        return TerminalSession.start(command, System.getenv(), Path.of(System.getProperty("user.home")), 80, 24, 100);
    }

    private static String screenText(TerminalSession session) {
        ScreenSnapshot snapshot = session.snapshot();
        StringBuilder text = new StringBuilder();
        for (int row = 0; row < snapshot.height(); row++) {
            text.append(snapshot.lineText(row)).append('\n');
        }
        return text.toString();
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `./gradlew :moray-terminal:test --tests dev.moray.terminal.PtyConnectorTest`
Expected: FAIL — compilation error, `cannot find symbol: method start(...)`.

- [ ] **Step 3: Implement `PtyConnector`**

`moray-terminal/src/main/java/dev/moray/terminal/PtyConnector.java`:
```java
package dev.moray.terminal;

import com.jediterm.core.util.TermSize;
import com.jediterm.terminal.TtyConnector;
import com.pty4j.PtyProcess;
import com.pty4j.WinSize;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.Reader;
import java.nio.charset.StandardCharsets;

/** Connects JediTerm to a pty4j process: UTF-8 output in, bytes out, window size changes. */
final class PtyConnector implements TtyConnector {
    private final PtyProcess process;
    private final Reader reader;
    private final OutputStream input;

    PtyConnector(PtyProcess process) {
        this.process = process;
        this.reader = new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8);
        this.input = process.getOutputStream();
    }

    @Override
    public int read(char[] buf, int offset, int length) throws IOException {
        return reader.read(buf, offset, length);
    }

    @Override
    public void write(byte[] bytes) throws IOException {
        input.write(bytes);
        input.flush();
    }

    @Override
    public void write(String string) throws IOException {
        write(string.getBytes(StandardCharsets.UTF_8));
    }

    @Override
    public boolean isConnected() {
        return process.isAlive();
    }

    @Override
    public void resize(TermSize size) {
        process.setWinSize(new WinSize(size.getColumns(), size.getRows()));
    }

    @Override
    public int waitFor() throws InterruptedException {
        return process.waitFor();
    }

    @Override
    public boolean ready() throws IOException {
        return reader.ready();
    }

    @Override
    public String getName() {
        return "pty";
    }

    @Override
    public void close() {
        process.destroy();
    }
}
```

- [ ] **Step 4: Add `TerminalSession.start`**

In `moray-terminal/src/main/java/dev/moray/terminal/TerminalSession.java`, add these imports:
```java
import com.pty4j.PtyProcess;
import com.pty4j.PtyProcessBuilder;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
```

and add this method directly above the package-private constructor:
```java
    /** Starts {@code command} in a new pseudo-terminal and begins emulating its output. */
    public static TerminalSession start(List<String> command, Map<String, String> environment, Path workingDirectory,
                                        int columns, int rows, int scrollback) throws IOException {
        Map<String, String> env = new HashMap<>(environment);
        env.put("TERM", "xterm-256color");
        env.put("COLORTERM", "truecolor");
        PtyProcess process = new PtyProcessBuilder(command.toArray(String[]::new))
            .setEnvironment(env)
            .setDirectory(workingDirectory.toString())
            .setInitialColumns(columns)
            .setInitialRows(rows)
            .setUnixOpenTtyToPreserveOutputAfterTermination(true)
            .start();
        TerminalSession session = new TerminalSession(new PtyConnector(process), columns, rows, scrollback);
        session.startReading();
        return session;
    }
```

- [ ] **Step 5: Run the test to verify it passes**

Run: `./gradlew :moray-terminal:test --tests dev.moray.terminal.PtyConnectorTest`
Expected: PASS (4 tests on macOS/Linux; 1 run + 3 skipped on Windows).

- [ ] **Step 6: Commit**

```bash
git add moray-terminal/src
git commit -m "Run programs in a real pseudo-terminal via pty4j"
```

### Task 6: Cursor styles and the painter

**Files:**
- Create: `moray-terminal/src/main/java/dev/moray/terminal/CursorStyle.java`
- Create: `moray-terminal/src/main/java/dev/moray/terminal/TerminalPainter.java`
- Test: `moray-terminal/src/test/java/dev/moray/terminal/CursorStyleTest.java`
- Test: `moray-terminal/src/test/java/dev/moray/terminal/TerminalPainterTest.java`

**Interfaces:**
- Consumes: `FontSet` (Task 2), `CellStyle.resolve` (Task 2), `RunBuilder`, `Run` (Task 3), `ScreenSnapshot`, `TerminalSession(TtyConnector, …)`, `FakeConnector`, `Await` (Task 4).
- Produces:
  - `public enum CursorStyle { BLOCK, BEAM, UNDERLINE }` with package-private `static CursorStyle effective(CursorShape requested, CursorStyle configured)` and `static boolean effectiveBlink(CursorShape requested, boolean configured)` — `requested == null` means "use the configured value".
  - `final class TerminalPainter` — `TerminalPainter(FontSet fonts, Palette palette)`, `void paint(Graphics2D g, ScreenSnapshot snapshot, CursorLook cursor, int widthPx, int heightPx)`; nested `record CursorLook(CursorStyle style, boolean on, boolean focused)` — `on` is false during the blink-off phase.

Painting order per frame: fill the whole area with the theme background → per row, fill non-default run backgrounds, then draw each run's glyphs with every glyph pinned to its cell (`charColumns[glyphCharIndex] * cellWidth`) → underline → cursor. A focused block cursor redraws the character beneath it in the background color; an unfocused cursor is a hollow rectangle.

- [ ] **Step 1: Write the failing tests**

`moray-terminal/src/test/java/dev/moray/terminal/CursorStyleTest.java`:
```java
package dev.moray.terminal;

import com.jediterm.terminal.CursorShape;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class CursorStyleTest {

    @Test
    void noRequestUsesTheConfiguredStyle() {
        assertThat(CursorStyle.effective(null, CursorStyle.UNDERLINE)).isEqualTo(CursorStyle.UNDERLINE);
        assertThat(CursorStyle.effectiveBlink(null, true)).isTrue();
    }

    @Test
    void applicationRequestsMapToStyles() {
        assertThat(CursorStyle.effective(CursorShape.STEADY_VERTICAL_BAR, CursorStyle.BLOCK)).isEqualTo(CursorStyle.BEAM);
        assertThat(CursorStyle.effective(CursorShape.BLINK_BLOCK, CursorStyle.BEAM)).isEqualTo(CursorStyle.BLOCK);
        assertThat(CursorStyle.effective(CursorShape.STEADY_UNDERLINE, CursorStyle.BLOCK)).isEqualTo(CursorStyle.UNDERLINE);
    }

    @Test
    void applicationRequestsDecideBlinking() {
        assertThat(CursorStyle.effectiveBlink(CursorShape.BLINK_VERTICAL_BAR, false)).isTrue();
        assertThat(CursorStyle.effectiveBlink(CursorShape.STEADY_BLOCK, true)).isFalse();
    }
}
```

`moray-terminal/src/test/java/dev/moray/terminal/TerminalPainterTest.java`:
```java
package dev.moray.terminal;

import org.junit.jupiter.api.Test;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class TerminalPainterTest {
    private static final int COLUMNS = 10;
    private static final int ROWS = 3;

    private final Palette palette = Palette.morayDark();
    private final FontSet fonts = new FontSet("JetBrains Mono", 14f, List.of(), true);
    private final TerminalPainter painter = new TerminalPainter(fonts, palette);
    private final int cw = fonts.cellWidth();
    private final int ch = fonts.cellHeight();

    @Test
    void emptyScreenIsThemeBackground() throws Exception {
        BufferedImage image = paint(snapshotAfter("", 0), cursorOff());

        assertThat(rgb(image, COLUMNS * cw - 1, ROWS * ch - 1)).isEqualTo(rgb(palette.background()));
    }

    @Test
    void coloredBackgroundFillsOnlyItsCells() throws Exception {
        BufferedImage image = paint(snapshotAfter("\033[41m  \033[0m", 2), cursorOff());

        assertThat(rgb(image, cw / 2, ch / 2)).isEqualTo(rgb(palette.ansi().get(1)));
        assertThat(rgb(image, cw + cw / 2, ch / 2)).isEqualTo(rgb(palette.ansi().get(1)));
        assertThat(rgb(image, 3 * cw + cw / 2, ch / 2)).isEqualTo(rgb(palette.background()));
    }

    @Test
    void textIsDrawnInItsForegroundColor() throws Exception {
        BufferedImage image = paint(snapshotAfter("\033[32m█\033[0m", 1), cursorOff());

        assertThat(rgb(image, cw / 2, ch / 2)).isEqualTo(rgb(palette.ansi().get(2)));
    }

    @Test
    void focusedBlockCursorFillsItsCell() throws Exception {
        BufferedImage image = paint(snapshotAfter("ab", 2), new TerminalPainter.CursorLook(CursorStyle.BLOCK, true, true));

        assertThat(rgb(image, 2 * cw + 1, 1)).isEqualTo(rgb(palette.cursor()));
        assertThat(rgb(image, 2 * cw + cw / 2, ch / 2)).isEqualTo(rgb(palette.cursor()));
    }

    @Test
    void cursorInBlinkOffPhaseIsNotDrawn() throws Exception {
        BufferedImage image = paint(snapshotAfter("ab", 2), cursorOff());

        assertThat(rgb(image, 2 * cw + cw / 2, ch / 2)).isEqualTo(rgb(palette.background()));
    }

    @Test
    void unfocusedCursorIsHollow() throws Exception {
        BufferedImage image = paint(snapshotAfter("ab", 2), new TerminalPainter.CursorLook(CursorStyle.BLOCK, true, false));

        assertThat(rgb(image, 2 * cw, ch / 2)).isEqualTo(rgb(palette.cursor()));
        assertThat(rgb(image, 2 * cw + cw / 2, ch / 2)).isEqualTo(rgb(palette.background()));
    }

    @Test
    void beamCursorIsNarrow() throws Exception {
        BufferedImage image = paint(snapshotAfter("ab", 2), new TerminalPainter.CursorLook(CursorStyle.BEAM, true, true));

        assertThat(rgb(image, 2 * cw, ch / 2)).isEqualTo(rgb(palette.cursor()));
        assertThat(rgb(image, 2 * cw + cw - 2, ch / 2)).isEqualTo(rgb(palette.background()));
    }

    private ScreenSnapshot snapshotAfter(String output, int expectedCursorColumn) throws Exception {
        FakeConnector connector = new FakeConnector();
        TerminalSession session = new TerminalSession(connector, COLUMNS, ROWS, 10);
        session.startReading();
        try {
            connector.feed(output);
            Await.until(() -> session.snapshot().cursorColumn() == expectedCursorColumn, "cursor at column " + expectedCursorColumn);
            return session.snapshot();
        } finally {
            session.close();
        }
    }

    private BufferedImage paint(ScreenSnapshot snapshot, TerminalPainter.CursorLook cursor) {
        BufferedImage image = new BufferedImage(COLUMNS * cw, ROWS * ch, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();
        try {
            painter.paint(g, snapshot, cursor, image.getWidth(), image.getHeight());
        } finally {
            g.dispose();
        }
        return image;
    }

    private static TerminalPainter.CursorLook cursorOff() {
        return new TerminalPainter.CursorLook(CursorStyle.BLOCK, false, true);
    }

    private static int rgb(BufferedImage image, int x, int y) {
        return image.getRGB(x, y) & 0xFFFFFF;
    }

    private static int rgb(Color color) {
        return color.getRGB() & 0xFFFFFF;
    }
}
```

- [ ] **Step 2: Run the tests to verify they fail**

Run: `./gradlew :moray-terminal:test --tests 'dev.moray.terminal.CursorStyleTest' --tests 'dev.moray.terminal.TerminalPainterTest'`
Expected: FAIL — compilation errors for `CursorStyle` and `TerminalPainter`.

- [ ] **Step 3: Implement `CursorStyle`**

`moray-terminal/src/main/java/dev/moray/terminal/CursorStyle.java`:
```java
package dev.moray.terminal;

import com.jediterm.terminal.CursorShape;

public enum CursorStyle {
    BLOCK, BEAM, UNDERLINE;

    /** The application's DECSCUSR request if there is one, otherwise the configured style. */
    static CursorStyle effective(CursorShape requested, CursorStyle configured) {
        if (requested == null) {
            return configured;
        }
        return switch (requested) {
            case BLINK_BLOCK, STEADY_BLOCK -> BLOCK;
            case BLINK_UNDERLINE, STEADY_UNDERLINE -> UNDERLINE;
            case BLINK_VERTICAL_BAR, STEADY_VERTICAL_BAR -> BEAM;
        };
    }

    static boolean effectiveBlink(CursorShape requested, boolean configured) {
        if (requested == null) {
            return configured;
        }
        return switch (requested) {
            case BLINK_BLOCK, BLINK_UNDERLINE, BLINK_VERTICAL_BAR -> true;
            case STEADY_BLOCK, STEADY_UNDERLINE, STEADY_VERTICAL_BAR -> false;
        };
    }
}
```

- [ ] **Step 4: Implement `TerminalPainter`**

`moray-terminal/src/main/java/dev/moray/terminal/TerminalPainter.java`:
```java
package dev.moray.terminal;

import com.jediterm.terminal.TextStyle;
import com.jediterm.terminal.util.CharUtils;

import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.font.GlyphVector;
import java.awt.geom.Point2D;
import java.util.List;

/** Draws a {@link ScreenSnapshot}. Event Dispatch Thread only. */
final class TerminalPainter {

    /** How the cursor should look this frame; {@code on} is false during the blink-off phase. */
    record CursorLook(CursorStyle style, boolean on, boolean focused) {
    }

    private static final int BAR_THICKNESS = 2;

    private final FontSet fonts;
    private final Palette palette;
    private final RunBuilder runs;

    TerminalPainter(FontSet fonts, Palette palette) {
        this.fonts = fonts;
        this.palette = palette;
        this.runs = new RunBuilder(fonts, palette);
    }

    void paint(Graphics2D g, ScreenSnapshot snapshot, CursorLook cursor, int widthPx, int heightPx) {
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_ON);
        g.setColor(palette.background());
        g.fillRect(0, 0, widthPx, heightPx);

        int cellWidth = fonts.cellWidth();
        int cellHeight = fonts.cellHeight();
        for (int row = 0; row < snapshot.height(); row++) {
            List<Run> rowRuns = runs.build(snapshot.lines().get(row), snapshot.width());
            int top = row * cellHeight;
            for (Run run : rowRuns) {
                Color background = run.style().background();
                if (!background.equals(palette.background())) {
                    g.setColor(background);
                    g.fillRect(run.startColumn() * cellWidth, top, run.columns() * cellWidth, cellHeight);
                }
            }
            for (Run run : rowRuns) {
                drawRun(g, run, top);
            }
        }
        paintCursor(g, snapshot, cursor);
    }

    private void drawRun(Graphics2D g, Run run, int top) {
        int cellWidth = fonts.cellWidth();
        int left = run.startColumn() * cellWidth;
        int baseline = top + fonts.ascent();
        g.setColor(run.style().foreground());
        if (!isBlank(run.text())) {
            GlyphVector glyphs = fonts.layout(run.font(), run.text());
            int[] charColumns = run.charColumns();
            for (int i = 0; i < glyphs.getNumGlyphs(); i++) {
                int charIndex = Math.min(glyphs.getGlyphCharIndex(i), charColumns.length - 1);
                glyphs.setGlyphPosition(i, new Point2D.Float(charColumns[charIndex] * cellWidth, 0));
            }
            g.drawGlyphVector(glyphs, left, baseline);
        }
        if (run.style().underline()) {
            g.fillRect(left, baseline + 1, run.columns() * cellWidth, 1);
        }
    }

    private void paintCursor(Graphics2D g, ScreenSnapshot snapshot, CursorLook look) {
        if (!look.on() || !snapshot.cursorVisible()) {
            return;
        }
        int column = snapshot.cursorColumn();
        int row = snapshot.cursorRow();
        if (column < 0 || row < 0 || column >= snapshot.width() || row >= snapshot.height()) {
            return;
        }
        int cellWidth = fonts.cellWidth();
        int cellHeight = fonts.cellHeight();
        int x = column * cellWidth;
        int y = row * cellHeight;
        g.setColor(palette.cursor());
        if (!look.focused()) {
            g.drawRect(x, y, cellWidth - 1, cellHeight - 1);
            return;
        }
        switch (look.style()) {
            case BLOCK -> {
                g.fillRect(x, y, cellWidth, cellHeight);
                drawCharacterUnderBlockCursor(g, snapshot, column, row, x, y);
            }
            case BEAM -> g.fillRect(x, y, BAR_THICKNESS, cellHeight);
            case UNDERLINE -> g.fillRect(x, y + cellHeight - BAR_THICKNESS, cellWidth, BAR_THICKNESS);
        }
    }

    private void drawCharacterUnderBlockCursor(Graphics2D g, ScreenSnapshot snapshot, int column, int row, int x, int y) {
        char[] chars = new char[snapshot.width()];
        TextStyle[] styles = new TextStyle[snapshot.width()];
        RunBuilder.readCells(snapshot.lines().get(row), snapshot.width(), chars, styles);
        char c = chars[column];
        if (c == ' ' || c == CharUtils.DWC || Character.isSurrogate(c)) {
            return;
        }
        CellStyle style = CellStyle.resolve(styles[column], palette);
        Font font = fonts.fontFor(c, style.bold(), style.italic());
        g.setColor(palette.background());
        g.drawGlyphVector(fonts.layout(font, new char[] {c}), x, y + fonts.ascent());
    }

    private static boolean isBlank(char[] text) {
        for (char c : text) {
            if (c != ' ') {
                return false;
            }
        }
        return true;
    }
}
```

- [ ] **Step 5: Run the tests to verify they pass**

Run: `./gradlew :moray-terminal:test --tests 'dev.moray.terminal.CursorStyleTest' --tests 'dev.moray.terminal.TerminalPainterTest'`
Expected: PASS (3 + 7 tests).

- [ ] **Step 6: Commit**

```bash
git add moray-terminal/src
git commit -m "Add cursor styles and the grid-pinned run painter"
```

### Task 7: Keyboard encoding

**Files:**
- Create: `moray-terminal/src/main/java/dev/moray/terminal/OptionAsMeta.java`
- Create: `moray-terminal/src/main/java/dev/moray/terminal/KeyInput.java`
- Create: `moray-terminal/src/main/java/dev/moray/terminal/KeyEncoder.java`
- Test: `moray-terminal/src/test/java/dev/moray/terminal/KeyEncoderTest.java`

**Interfaces:**
- Consumes: `TerminalSession.codeForKey(int keyCode, int modifiers)` (Task 4) — passed in as a `BiFunction<Integer, Integer, byte[]>`; `FakeConnector`, `Await` (Task 4 tests).
- Produces:
  - `public enum OptionAsMeta { LEFT, RIGHT, BOTH, NONE }`
  - `record KeyInput(int keyCode, char keyChar, int modifiers, boolean leftAltHeld, boolean rightAltHeld)` (package-private) — `modifiers` is `KeyEvent.getModifiersEx()`; helpers `shift()`, `ctrl()`, `alt()`, `meta()`.
  - `final class KeyEncoder` (package-private) — `KeyEncoder(OptionAsMeta optionAsMeta, boolean macOs)`, `byte[] pressed(KeyInput key, BiFunction<Integer, Integer, byte[]> specialKeys)`, `byte[] typed(KeyInput key)`; both return `null` for "not mine".

Rules:
- Any key with Meta (⌘ on macOS) returns `null` from both methods — those belong to the app.
- **KEY_PRESSED** handles: arrows, Home/End, PageUp/PageDown, Insert, Delete, F1–F12 (delegated to JediTerm's `getCodeForKey` with only the Shift/Ctrl/Alt bits); Enter → `\r`; Backspace → `0x7f` (Ctrl+Backspace → `0x08`); Tab → `\t`, Shift+Tab → `ESC [ Z`; Escape → `ESC`; Ctrl+Space → `NUL`; and every key while Alt acts as Meta.
- **Alt acts as Meta** on Linux/Windows whenever Alt is down without Ctrl (Ctrl+Alt is AltGr there). On macOS it depends on `OptionAsMeta` and which Option key is held. As Meta, a key sends `ESC` followed by its plain character (letters, digits, common punctuation, space, Enter, Backspace, Tab); Ctrl+Meta+letter sends `ESC` + the control character.
- **KEY_TYPED** sends the typed character as UTF-8, except characters already handled on KEY_PRESSED (`\n`, `\r`, `\b`, `DEL`, `\t`, `ESC`) and `CHAR_UNDEFINED`. A high surrogate is held until its low surrogate arrives.
- The caller (Task 8) skips the KEY_TYPED event that follows a KEY_PRESSED which returned bytes.

- [ ] **Step 1: Write the failing test**

`moray-terminal/src/test/java/dev/moray/terminal/KeyEncoderTest.java`:
```java
package dev.moray.terminal;

import org.junit.jupiter.api.Test;

import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.nio.charset.StandardCharsets;
import java.util.function.BiFunction;

import static org.assertj.core.api.Assertions.assertThat;

class KeyEncoderTest {
    private static final int SHIFT = InputEvent.SHIFT_DOWN_MASK;
    private static final int CTRL = InputEvent.CTRL_DOWN_MASK;
    private static final int ALT = InputEvent.ALT_DOWN_MASK;
    private static final int META = InputEvent.META_DOWN_MASK;
    private static final BiFunction<Integer, Integer, byte[]> NO_SPECIAL_KEYS = (key, modifiers) -> null;

    private final KeyEncoder mac = new KeyEncoder(OptionAsMeta.LEFT, true);
    private final KeyEncoder linux = new KeyEncoder(OptionAsMeta.NONE, false);

    @Test
    void arrowsUseJediTermEncodingIncludingApplicationMode() throws Exception {
        FakeConnector connector = new FakeConnector();
        TerminalSession session = new TerminalSession(connector, 20, 4, 10);
        session.startReading();
        try {
            assertThat(text(mac.pressed(key(KeyEvent.VK_UP, 0), session::codeForKey))).isEqualTo("\033[A");
            connector.feed("\033[?1h"); // application cursor keys on
            Await.until(() -> "\033OA".equals(text(mac.pressed(key(KeyEvent.VK_UP, 0), session::codeForKey))), "application-mode arrow");
            assertThat(text(mac.pressed(key(KeyEvent.VK_UP, SHIFT), session::codeForKey))).isEqualTo("\033[1;2A");
        } finally {
            session.close();
        }
    }

    @Test
    void specialKeysReceiveOnlyShiftCtrlAltBits() {
        int[] seen = new int[1];
        linux.pressed(key(KeyEvent.VK_F5, CTRL | InputEvent.BUTTON1_DOWN_MASK), (k, modifiers) -> {
            seen[0] = modifiers;
            return new byte[0];
        });
        assertThat(seen[0]).isEqualTo(CTRL);
    }

    @Test
    void editingKeys() {
        assertThat(text(mac.pressed(key(KeyEvent.VK_ENTER, 0), NO_SPECIAL_KEYS))).isEqualTo("\r");
        assertThat(text(mac.pressed(key(KeyEvent.VK_BACK_SPACE, 0), NO_SPECIAL_KEYS))).isEqualTo("\177");
        assertThat(text(mac.pressed(key(KeyEvent.VK_BACK_SPACE, CTRL), NO_SPECIAL_KEYS))).isEqualTo("\b");
        assertThat(text(mac.pressed(key(KeyEvent.VK_TAB, 0), NO_SPECIAL_KEYS))).isEqualTo("\t");
        assertThat(text(mac.pressed(key(KeyEvent.VK_TAB, SHIFT), NO_SPECIAL_KEYS))).isEqualTo("\033[Z");
        assertThat(text(mac.pressed(key(KeyEvent.VK_ESCAPE, 0), NO_SPECIAL_KEYS))).isEqualTo("\033");
        assertThat(text(mac.pressed(key(KeyEvent.VK_SPACE, CTRL), NO_SPECIAL_KEYS))).isEqualTo("\0");
    }

    @Test
    void commandKeysBelongToTheApp() {
        assertThat(mac.pressed(key(KeyEvent.VK_C, META), NO_SPECIAL_KEYS)).isNull();
        assertThat(mac.typed(typed('c', META))).isNull();
    }

    @Test
    void plainLettersAreLeftToKeyTyped() {
        assertThat(mac.pressed(key(KeyEvent.VK_A, 0), NO_SPECIAL_KEYS)).isNull();
        assertThat(text(mac.typed(typed('a', 0)))).isEqualTo("a");
    }

    @Test
    void typedCharactersAreUtf8() {
        assertThat(mac.typed(typed('é', 0))).containsExactly(0xC3, 0xA9);
        assertThat(mac.typed(typed('\003', CTRL))).containsExactly(0x03);
    }

    @Test
    void typedCharactersHandledOnPressAreIgnored() {
        assertThat(mac.typed(typed('\n', 0))).isNull();
        assertThat(mac.typed(typed('\b', 0))).isNull();
        assertThat(mac.typed(typed('\t', 0))).isNull();
        assertThat(mac.typed(typed('\033', 0))).isNull();
        assertThat(mac.typed(typed(KeyEvent.CHAR_UNDEFINED, 0))).isNull();
    }

    @Test
    void surrogatePairIsEncodedOnceComplete() {
        assertThat(mac.typed(typed('\uD83D', 0))).isNull();
        assertThat(text(mac.typed(typed('\uDE80', 0)))).isEqualTo("🚀");
    }

    @Test
    void leftOptionActsAsMetaWhenConfiguredLeft() {
        assertThat(text(mac.pressed(new KeyInput(KeyEvent.VK_F, 'ƒ', ALT, true, false), NO_SPECIAL_KEYS))).isEqualTo("\033f");
        assertThat(mac.pressed(new KeyInput(KeyEvent.VK_F, 'ƒ', ALT, false, true), NO_SPECIAL_KEYS)).isNull();
    }

    @Test
    void optionAsMetaSettings() {
        KeyEncoder right = new KeyEncoder(OptionAsMeta.RIGHT, true);
        KeyEncoder both = new KeyEncoder(OptionAsMeta.BOTH, true);
        KeyEncoder none = new KeyEncoder(OptionAsMeta.NONE, true);
        assertThat(text(right.pressed(new KeyInput(KeyEvent.VK_B, '∫', ALT, false, true), NO_SPECIAL_KEYS))).isEqualTo("\033b");
        assertThat(text(both.pressed(new KeyInput(KeyEvent.VK_B, '∫', ALT, true, false), NO_SPECIAL_KEYS))).isEqualTo("\033b");
        assertThat(none.pressed(new KeyInput(KeyEvent.VK_B, '∫', ALT, true, false), NO_SPECIAL_KEYS)).isNull();
    }

    @Test
    void metaVariants() {
        assertThat(text(mac.pressed(new KeyInput(KeyEvent.VK_F, 'Ï', ALT | SHIFT, true, false), NO_SPECIAL_KEYS))).isEqualTo("\033F");
        assertThat(text(mac.pressed(new KeyInput(KeyEvent.VK_BACK_SPACE, '\b', ALT, true, false), NO_SPECIAL_KEYS))).isEqualTo("\033\177");
        assertThat(text(mac.pressed(new KeyInput(KeyEvent.VK_PERIOD, '≥', ALT, true, false), NO_SPECIAL_KEYS))).isEqualTo("\033.");
        assertThat(text(mac.pressed(new KeyInput(KeyEvent.VK_7, '¶', ALT, true, false), NO_SPECIAL_KEYS))).isEqualTo("\0337");
        assertThat(text(mac.pressed(new KeyInput(KeyEvent.VK_X, '\030', ALT | CTRL, true, false), NO_SPECIAL_KEYS))).isEqualTo("\033\030");
    }

    @Test
    void altIsMetaOnLinuxAndWindowsButAltGrIsNot() {
        assertThat(text(linux.pressed(new KeyInput(KeyEvent.VK_B, 'b', ALT, true, false), NO_SPECIAL_KEYS))).isEqualTo("\033b");
        assertThat(linux.pressed(new KeyInput(KeyEvent.VK_Q, '@', ALT | CTRL, false, true), NO_SPECIAL_KEYS)).isNull();
        assertThat(text(linux.typed(new KeyInput(KeyEvent.VK_UNDEFINED, '@', ALT | CTRL, false, true)))).isEqualTo("@");
    }

    private static KeyInput key(int keyCode, int modifiers) {
        return new KeyInput(keyCode, KeyEvent.CHAR_UNDEFINED, modifiers, false, false);
    }

    private static KeyInput typed(char c, int modifiers) {
        return new KeyInput(KeyEvent.VK_UNDEFINED, c, modifiers, false, false);
    }

    private static String text(byte[] bytes) {
        return bytes == null ? null : new String(bytes, StandardCharsets.UTF_8);
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `./gradlew :moray-terminal:test --tests dev.moray.terminal.KeyEncoderTest`
Expected: FAIL — compilation errors for `KeyEncoder`, `KeyInput`, `OptionAsMeta`.

- [ ] **Step 3: Implement `OptionAsMeta` and `KeyInput`**

`moray-terminal/src/main/java/dev/moray/terminal/OptionAsMeta.java`:
```java
package dev.moray.terminal;

/** Which macOS Option key sends Meta (ESC-prefixed keys) instead of composing characters. */
public enum OptionAsMeta {
    LEFT, RIGHT, BOTH, NONE
}
```

`moray-terminal/src/main/java/dev/moray/terminal/KeyInput.java`:
```java
package dev.moray.terminal;

import java.awt.event.InputEvent;

/** The parts of a KeyEvent the encoder needs. {@code modifiers} is {@code KeyEvent.getModifiersEx()}. */
record KeyInput(int keyCode, char keyChar, int modifiers, boolean leftAltHeld, boolean rightAltHeld) {

    boolean shift() {
        return (modifiers & InputEvent.SHIFT_DOWN_MASK) != 0;
    }

    boolean ctrl() {
        return (modifiers & InputEvent.CTRL_DOWN_MASK) != 0;
    }

    boolean alt() {
        return (modifiers & InputEvent.ALT_DOWN_MASK) != 0;
    }

    boolean meta() {
        return (modifiers & InputEvent.META_DOWN_MASK) != 0;
    }
}
```

- [ ] **Step 4: Implement `KeyEncoder`**

`moray-terminal/src/main/java/dev/moray/terminal/KeyEncoder.java`:
```java
package dev.moray.terminal;

import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.function.BiFunction;

/** Turns key events into the bytes a terminal program expects. Event Dispatch Thread only. */
final class KeyEncoder {
    private static final int SHIFT_CTRL_ALT =
        InputEvent.SHIFT_DOWN_MASK | InputEvent.CTRL_DOWN_MASK | InputEvent.ALT_DOWN_MASK;
    private static final byte ESC = 0x1b;
    private static final Map<Integer, Character> PUNCTUATION = Map.ofEntries(
        Map.entry(KeyEvent.VK_PERIOD, '.'), Map.entry(KeyEvent.VK_COMMA, ','), Map.entry(KeyEvent.VK_SLASH, '/'),
        Map.entry(KeyEvent.VK_MINUS, '-'), Map.entry(KeyEvent.VK_EQUALS, '='), Map.entry(KeyEvent.VK_SEMICOLON, ';'),
        Map.entry(KeyEvent.VK_QUOTE, '\''), Map.entry(KeyEvent.VK_OPEN_BRACKET, '['),
        Map.entry(KeyEvent.VK_CLOSE_BRACKET, ']'), Map.entry(KeyEvent.VK_BACK_SLASH, '\\'),
        Map.entry(KeyEvent.VK_BACK_QUOTE, '`'));

    private final OptionAsMeta optionAsMeta;
    private final boolean macOs;
    private char pendingHighSurrogate;

    KeyEncoder(OptionAsMeta optionAsMeta, boolean macOs) {
        this.optionAsMeta = optionAsMeta;
        this.macOs = macOs;
    }

    /** Bytes for a KEY_PRESSED event, or null to leave the key to KEY_TYPED (or to the app). */
    byte[] pressed(KeyInput key, BiFunction<Integer, Integer, byte[]> specialKeys) {
        if (key.meta()) {
            return null;
        }
        int code = key.keyCode();
        if (isSpecialKey(code)) {
            return specialKeys.apply(code, key.modifiers() & SHIFT_CTRL_ALT);
        }
        boolean asMeta = altActsAsMeta(key);
        byte[] plain = switch (code) {
            case KeyEvent.VK_ENTER -> new byte[] {'\r'};
            case KeyEvent.VK_BACK_SPACE -> new byte[] {key.ctrl() ? (byte) 0x08 : (byte) 0x7f};
            case KeyEvent.VK_TAB -> key.shift() ? "\033[Z".getBytes(StandardCharsets.US_ASCII) : new byte[] {'\t'};
            case KeyEvent.VK_ESCAPE -> new byte[] {ESC};
            case KeyEvent.VK_SPACE -> key.ctrl() ? new byte[] {0} : asMeta ? new byte[] {' '} : null;
            default -> asMeta ? metaCharacter(key) : null;
        };
        if (plain == null) {
            return null;
        }
        return asMeta && code != KeyEvent.VK_ESCAPE ? prefixEscape(plain) : plain;
    }

    /** Bytes for a KEY_TYPED event that {@link #pressed} did not already handle, or null. */
    byte[] typed(KeyInput key) {
        if (key.meta()) {
            return null;
        }
        char c = key.keyChar();
        if (c == KeyEvent.CHAR_UNDEFINED || c == '\n' || c == '\r' || c == '\b' || c == 0x7f || c == '\t' || c == ESC) {
            return null;
        }
        if (Character.isHighSurrogate(c)) {
            pendingHighSurrogate = c;
            return null;
        }
        if (Character.isLowSurrogate(c) && pendingHighSurrogate != 0) {
            String pair = new String(new char[] {pendingHighSurrogate, c});
            pendingHighSurrogate = 0;
            return pair.getBytes(StandardCharsets.UTF_8);
        }
        pendingHighSurrogate = 0;
        return String.valueOf(c).getBytes(StandardCharsets.UTF_8);
    }

    private boolean altActsAsMeta(KeyInput key) {
        if (!key.alt()) {
            return false;
        }
        if (!macOs) {
            return !key.ctrl(); // Ctrl+Alt is AltGr on Linux/Windows layouts
        }
        return switch (optionAsMeta) {
            case LEFT -> key.leftAltHeld();
            case RIGHT -> key.rightAltHeld();
            case BOTH -> true;
            case NONE -> false;
        };
    }

    private static byte[] metaCharacter(KeyInput key) {
        int code = key.keyCode();
        char c;
        if (code >= KeyEvent.VK_A && code <= KeyEvent.VK_Z) {
            c = key.ctrl()
                ? (char) (code - KeyEvent.VK_A + 1)
                : (char) ((key.shift() ? 'A' : 'a') + (code - KeyEvent.VK_A));
        } else if (code >= KeyEvent.VK_0 && code <= KeyEvent.VK_9) {
            c = (char) ('0' + (code - KeyEvent.VK_0));
        } else {
            Character punctuation = PUNCTUATION.get(code);
            if (punctuation == null) {
                return null;
            }
            c = punctuation;
        }
        return new byte[] {(byte) c};
    }

    private static byte[] prefixEscape(byte[] bytes) {
        byte[] result = new byte[bytes.length + 1];
        result[0] = ESC;
        System.arraycopy(bytes, 0, result, 1, bytes.length);
        return result;
    }

    private static boolean isSpecialKey(int code) {
        return switch (code) {
            case KeyEvent.VK_UP, KeyEvent.VK_DOWN, KeyEvent.VK_LEFT, KeyEvent.VK_RIGHT,
                 KeyEvent.VK_HOME, KeyEvent.VK_END, KeyEvent.VK_PAGE_UP, KeyEvent.VK_PAGE_DOWN,
                 KeyEvent.VK_INSERT, KeyEvent.VK_DELETE,
                 KeyEvent.VK_F1, KeyEvent.VK_F2, KeyEvent.VK_F3, KeyEvent.VK_F4, KeyEvent.VK_F5, KeyEvent.VK_F6,
                 KeyEvent.VK_F7, KeyEvent.VK_F8, KeyEvent.VK_F9, KeyEvent.VK_F10, KeyEvent.VK_F11, KeyEvent.VK_F12 -> true;
            default -> false;
        };
    }
}
```

- [ ] **Step 5: Run the test to verify it passes**

Run: `./gradlew :moray-terminal:test --tests dev.moray.terminal.KeyEncoderTest`
Expected: PASS (12 tests).

- [ ] **Step 6: Commit**

```bash
git add moray-terminal/src
git commit -m "Add key encoder with Option-as-Meta and application cursor keys"
```

### Task 8: The terminal view

**Files:**
- Create: `moray-terminal/src/main/java/dev/moray/terminal/TerminalOptions.java`
- Create: `moray-terminal/src/main/java/dev/moray/terminal/GridSize.java`
- Create: `moray-terminal/src/main/java/dev/moray/terminal/TerminalView.java`
- Test: `moray-terminal/src/test/java/dev/moray/terminal/GridSizeTest.java`
- Test: `moray-terminal/src/test/java/dev/moray/terminal/TerminalViewTest.java`

**Interfaces:**
- Consumes: `Palette.morayDark()` (Task 1), `FontSet` (Task 2), `TerminalSession` incl. `snapshot()`, `codeForKey`, `resize`, `Listener` (Tasks 4–5), `CursorStyle.effective/effectiveBlink`, `TerminalPainter`, `TerminalPainter.CursorLook` (Task 6), `KeyEncoder`, `KeyInput`, `OptionAsMeta` (Task 7), `FakeConnector` (Task 4 test).
- Produces:
  - `public record TerminalOptions(String fontFamily, float fontSize, List<String> fallbackFonts, boolean ligatures, Palette palette, CursorStyle cursorStyle, boolean cursorBlink, OptionAsMeta optionAsMeta, int scrollback)` with `static TerminalOptions defaults()`.
  - `record GridSize(int columns, int rows)` with `static GridSize fit(int widthPx, int heightPx, int cellWidth, int cellHeight)` (package-private).
  - `public final class TerminalView extends JComponent` — `TerminalView(TerminalSession session, TerminalOptions options)`; package-private `void handleKey(KeyEvent e)`, `void resizeSessionToFit()`.

Behavior: an 8 ms Swing timer repaints only when the session reported a screen change since the last frame. A 530 ms timer blinks the cursor; any key sent to the shell restarts the blink "on". Focus traversal keys are disabled so Tab reaches the shell. Clicking focuses the view. Resizing the component resizes the session to the whole cells that fit. Which Option key is held (left/right) is tracked from `VK_ALT` press/release locations.

- [ ] **Step 1: Write the failing tests**

`moray-terminal/src/test/java/dev/moray/terminal/GridSizeTest.java`:
```java
package dev.moray.terminal;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class GridSizeTest {

    @Test
    void wholeCellsThatFit() {
        assertThat(GridSize.fit(803, 600, 8, 17)).isEqualTo(new GridSize(100, 35));
    }

    @Test
    void neverSmallerThanOneCell() {
        assertThat(GridSize.fit(3, 0, 8, 17)).isEqualTo(new GridSize(1, 1));
    }
}
```

`moray-terminal/src/test/java/dev/moray/terminal/TerminalViewTest.java`:
```java
package dev.moray.terminal;

import com.jediterm.core.util.TermSize;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.event.KeyEvent;
import java.awt.image.BufferedImage;

import static org.assertj.core.api.Assertions.assertThat;

class TerminalViewTest {
    private final TerminalOptions options = TerminalOptions.defaults();
    private final FontSet fonts = new FontSet(options.fontFamily(), options.fontSize(), options.fallbackFonts(), options.ligatures());
    private FakeConnector connector;
    private TerminalSession session;
    private TerminalView view;

    @BeforeEach
    void setUp() throws Exception {
        connector = new FakeConnector();
        session = new TerminalSession(connector, 20, 4, 100);
        session.startReading();
        view = new TerminalView(session, options);
    }

    @AfterEach
    void tearDown() {
        session.close();
    }

    @Test
    void preferredSizeIsTheSessionGrid() {
        assertThat(view.getPreferredSize())
            .isEqualTo(new Dimension(20 * fonts.cellWidth(), 4 * fonts.cellHeight()));
    }

    @Test
    void resizingTheViewResizesTheSession() {
        view.setSize(10 * fonts.cellWidth() + 3, 2 * fonts.cellHeight() + 1);

        view.resizeSessionToFit();

        assertThat(session.columns()).isEqualTo(10);
        assertThat(session.rows()).isEqualTo(2);
        assertThat(connector.lastResize()).isEqualTo(new TermSize(10, 2));
    }

    @Test
    void enterSendsCarriageReturnAndItsTypedEventIsSkipped() {
        view.handleKey(pressed(KeyEvent.VK_ENTER));
        view.handleKey(typed('\n'));

        assertThat(connector.written()).isEqualTo("\r");
    }

    @Test
    void typedCharactersAreSent() {
        view.handleKey(pressed(KeyEvent.VK_A));
        view.handleKey(typed('a'));

        assertThat(connector.written()).isEqualTo("a");
    }

    @Test
    void anArrowKeyDoesNotSwallowTheNextCharacter() {
        view.handleKey(pressed(KeyEvent.VK_LEFT));
        view.handleKey(pressed(KeyEvent.VK_B));
        view.handleKey(typed('b'));

        assertThat(connector.written()).isEqualTo("\033[Db");
    }

    @Test
    void paintsTheThemeBackground() {
        view.setSize(20 * fonts.cellWidth(), 4 * fonts.cellHeight());
        BufferedImage image = new BufferedImage(view.getWidth(), view.getHeight(), BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();
        try {
            view.paint(g);
        } finally {
            g.dispose();
        }

        assertThat(image.getRGB(view.getWidth() - 1, view.getHeight() - 1) & 0xFFFFFF)
            .isEqualTo(options.palette().background().getRGB() & 0xFFFFFF);
    }

    private KeyEvent pressed(int keyCode) {
        return new KeyEvent(view, KeyEvent.KEY_PRESSED, 0, 0, keyCode, KeyEvent.CHAR_UNDEFINED);
    }

    private KeyEvent typed(char c) {
        return new KeyEvent(view, KeyEvent.KEY_TYPED, 0, 0, KeyEvent.VK_UNDEFINED, c);
    }
}
```

- [ ] **Step 2: Run the tests to verify they fail**

Run: `./gradlew :moray-terminal:test --tests 'dev.moray.terminal.GridSizeTest' --tests 'dev.moray.terminal.TerminalViewTest'`
Expected: FAIL — compilation errors for `GridSize`, `TerminalOptions`, `TerminalView`.

- [ ] **Step 3: Implement `TerminalOptions` and `GridSize`**

`moray-terminal/src/main/java/dev/moray/terminal/TerminalOptions.java`:
```java
package dev.moray.terminal;

import java.util.List;

/** Everything about how a terminal looks and behaves that the app can configure. */
public record TerminalOptions(String fontFamily, float fontSize, List<String> fallbackFonts, boolean ligatures,
                              Palette palette, CursorStyle cursorStyle, boolean cursorBlink,
                              OptionAsMeta optionAsMeta, int scrollback) {

    public TerminalOptions {
        fallbackFonts = List.copyOf(fallbackFonts);
    }

    /** The spec's defaults (spec §7.2), used until plan 4 adds the config file. */
    public static TerminalOptions defaults() {
        return new TerminalOptions("JetBrains Mono", 14f, List.of("Symbols Nerd Font Mono", "Apple Color Emoji"), true,
            Palette.morayDark(), CursorStyle.BLOCK, true, OptionAsMeta.LEFT, 10_000);
    }
}
```

`moray-terminal/src/main/java/dev/moray/terminal/GridSize.java`:
```java
package dev.moray.terminal;

/** How many whole cells fit in a pixel area; never less than one by one. */
record GridSize(int columns, int rows) {

    static GridSize fit(int widthPx, int heightPx, int cellWidth, int cellHeight) {
        return new GridSize(Math.max(1, widthPx / cellWidth), Math.max(1, heightPx / cellHeight));
    }
}
```

- [ ] **Step 4: Implement `TerminalView`**

`moray-terminal/src/main/java/dev/moray/terminal/TerminalView.java`:
```java
package dev.moray.terminal;

import javax.swing.JComponent;
import javax.swing.Timer;
import java.awt.AWTEvent;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;

/** The Swing component that shows a {@link TerminalSession} and sends it keyboard input. */
public final class TerminalView extends JComponent {
    private static final int FRAME_MILLIS = 8;
    private static final int BLINK_MILLIS = 530;

    private final TerminalSession session;
    private final TerminalOptions options;
    private final FontSet fonts;
    private final TerminalPainter painter;
    private final KeyEncoder keys;
    private final AtomicBoolean dirty = new AtomicBoolean(true);
    private final Timer frameTimer;
    private final Timer blinkTimer;
    private final TerminalSession.Listener listener = new TerminalSession.Listener() {
        @Override
        public void screenChanged() {
            dirty.set(true);
        }
    };
    private boolean blinkOn = true;
    private boolean suppressNextTyped;
    private boolean leftAltHeld;
    private boolean rightAltHeld;

    public TerminalView(TerminalSession session, TerminalOptions options) {
        this.session = session;
        this.options = options;
        this.fonts = new FontSet(options.fontFamily(), options.fontSize(), options.fallbackFonts(), options.ligatures());
        this.painter = new TerminalPainter(fonts, options.palette());
        this.keys = new KeyEncoder(options.optionAsMeta(),
            System.getProperty("os.name", "").toLowerCase(Locale.ROOT).startsWith("mac"));
        this.frameTimer = new Timer(FRAME_MILLIS, e -> {
            if (dirty.getAndSet(false)) {
                repaint();
            }
        });
        this.blinkTimer = new Timer(BLINK_MILLIS, e -> {
            blinkOn = !blinkOn;
            repaint();
        });

        setOpaque(true);
        setFocusable(true);
        setFocusTraversalKeysEnabled(false);
        setBackground(options.palette().background());
        enableEvents(AWTEvent.KEY_EVENT_MASK);
        addComponentListener(new ComponentAdapter() {
            @Override
            public void componentResized(ComponentEvent e) {
                resizeSessionToFit();
            }
        });
        addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                requestFocusInWindow();
            }
        });
        addFocusListener(new FocusAdapter() {
            @Override
            public void focusGained(FocusEvent e) {
                restartBlink();
            }

            @Override
            public void focusLost(FocusEvent e) {
                leftAltHeld = false;
                rightAltHeld = false;
                repaint();
            }
        });
    }

    @Override
    public void addNotify() {
        super.addNotify();
        session.addListener(listener);
        frameTimer.start();
        blinkTimer.start();
    }

    @Override
    public void removeNotify() {
        frameTimer.stop();
        blinkTimer.stop();
        session.removeListener(listener);
        super.removeNotify();
    }

    @Override
    public Dimension getPreferredSize() {
        return new Dimension(session.columns() * fonts.cellWidth(), session.rows() * fonts.cellHeight());
    }

    @Override
    protected void paintComponent(Graphics g) {
        ScreenSnapshot snapshot = session.snapshot();
        CursorStyle style = CursorStyle.effective(snapshot.cursorShape(), options.cursorStyle());
        boolean blinks = CursorStyle.effectiveBlink(snapshot.cursorShape(), options.cursorBlink());
        boolean focused = isFocusOwner();
        boolean on = !blinks || !focused || blinkOn;
        painter.paint((Graphics2D) g, snapshot, new TerminalPainter.CursorLook(style, on, focused), getWidth(), getHeight());
    }

    @Override
    protected void processKeyEvent(KeyEvent e) {
        handleKey(e);
        if (!e.isConsumed()) {
            super.processKeyEvent(e);
        }
    }

    void handleKey(KeyEvent e) {
        trackAltKeys(e);
        KeyInput input = new KeyInput(e.getKeyCode(), e.getKeyChar(), e.getModifiersEx(), leftAltHeld, rightAltHeld);
        byte[] bytes = switch (e.getID()) {
            case KeyEvent.KEY_PRESSED -> {
                byte[] pressed = keys.pressed(input, session::codeForKey);
                suppressNextTyped = pressed != null; // re-decided on every press
                yield pressed;
            }
            case KeyEvent.KEY_TYPED -> {
                if (suppressNextTyped) {
                    suppressNextTyped = false;
                    e.consume();
                    yield null;
                }
                yield keys.typed(input);
            }
            default -> null;
        };
        if (bytes != null) {
            session.write(bytes);
            restartBlink();
            e.consume();
        }
    }

    void resizeSessionToFit() {
        GridSize grid = GridSize.fit(getWidth(), getHeight(), fonts.cellWidth(), fonts.cellHeight());
        session.resize(grid.columns(), grid.rows());
        dirty.set(true);
    }

    private void trackAltKeys(KeyEvent e) {
        if (e.getKeyCode() != KeyEvent.VK_ALT) {
            return;
        }
        boolean down = e.getID() == KeyEvent.KEY_PRESSED;
        if (e.getKeyLocation() == KeyEvent.KEY_LOCATION_RIGHT) {
            rightAltHeld = down;
        } else {
            leftAltHeld = down;
        }
    }

    private void restartBlink() {
        blinkOn = true;
        blinkTimer.restart();
        repaint();
    }
}
```

- [ ] **Step 5: Run the tests to verify they pass**

Run: `./gradlew :moray-terminal:test --tests 'dev.moray.terminal.GridSizeTest' --tests 'dev.moray.terminal.TerminalViewTest'`
Expected: PASS (2 + 6 tests).

- [ ] **Step 6: Run the whole module's tests**

Run: `./gradlew :moray-terminal:check`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 7: Commit**

```bash
git add moray-terminal/src
git commit -m "Add the terminal view: coalesced repaint, cursor blink, keyboard, resize"
```

### Task 9: The app — one window running your shell

**Files:**
- Create: `moray-app/src/main/java/dev/moray/app/DefaultShell.java`
- Create: `moray-app/src/main/java/dev/moray/app/Main.java`
- Test: `moray-app/src/test/java/dev/moray/app/DefaultShellTest.java`

**Interfaces:**
- Consumes: `TerminalSession.start(...)`, `exitFuture()`, `addListener`, `Listener.titleChanged`, `close()` (Tasks 4–5); `TerminalView(TerminalSession, TerminalOptions)`, `TerminalOptions.defaults()` (Task 8).
- Produces: `final class DefaultShell` with `static List<String> command(String osName, Map<String, String> environment)`; `public final class Main` with `main(String[])`.

Default shell: macOS/Linux use `$SHELL` as a login shell (`-l`), falling back to `/bin/zsh` on macOS and `/bin/bash` on Linux when `$SHELL` is unset or blank. Windows uses `powershell.exe -NoLogo`. The window title follows the shell's OSC title. When the shell exits, or the window closes, the app exits.

- [ ] **Step 1: Write the failing test**

`moray-app/src/test/java/dev/moray/app/DefaultShellTest.java`:
```java
package dev.moray.app;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class DefaultShellTest {

    @Test
    void usesTheUsersShellAsALoginShell() {
        assertThat(DefaultShell.command("Mac OS X", Map.of("SHELL", "/opt/homebrew/bin/fish")))
            .containsExactly("/opt/homebrew/bin/fish", "-l");
    }

    @Test
    void fallsBackToZshOnMacAndBashOnLinux() {
        assertThat(DefaultShell.command("Mac OS X", Map.of())).containsExactly("/bin/zsh", "-l");
        assertThat(DefaultShell.command("Linux", Map.of("SHELL", " "))).containsExactly("/bin/bash", "-l");
    }

    @Test
    void usesPowerShellOnWindows() {
        assertThat(DefaultShell.command("Windows 11", Map.of("SHELL", "/bin/bash")))
            .containsExactly("powershell.exe", "-NoLogo");
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `./gradlew :moray-app:test --tests dev.moray.app.DefaultShellTest`
Expected: FAIL — compilation error, `cannot find symbol: class DefaultShell`.

- [ ] **Step 3: Implement `DefaultShell`**

`moray-app/src/main/java/dev/moray/app/DefaultShell.java`:
```java
package dev.moray.app;

import java.util.List;
import java.util.Locale;
import java.util.Map;

/** The command for the user's login shell on this OS. Plan 4 lets the config override it. */
final class DefaultShell {
    private DefaultShell() {
    }

    static List<String> command(String osName, Map<String, String> environment) {
        String os = osName.toLowerCase(Locale.ROOT);
        if (os.startsWith("windows")) {
            return List.of("powershell.exe", "-NoLogo");
        }
        String shell = environment.get("SHELL");
        if (shell == null || shell.isBlank()) {
            shell = os.startsWith("mac") ? "/bin/zsh" : "/bin/bash";
        }
        return List.of(shell, "-l");
    }
}
```

- [ ] **Step 4: Run the test to verify it passes**

Run: `./gradlew :moray-app:test --tests dev.moray.app.DefaultShellTest`
Expected: PASS (3 tests).

- [ ] **Step 5: Implement `Main`**

`moray-app/src/main/java/dev/moray/app/Main.java`:
```java
package dev.moray.app;

import dev.moray.terminal.TerminalOptions;
import dev.moray.terminal.TerminalSession;
import dev.moray.terminal.TerminalView;

import javax.swing.JFrame;
import javax.swing.SwingUtilities;
import javax.swing.WindowConstants;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.nio.file.Path;

public final class Main {
    private Main() {
    }

    public static void main(String[] args) throws Exception {
        System.setProperty("apple.laf.useScreenMenuBar", "true");
        TerminalOptions options = TerminalOptions.defaults();
        TerminalSession session = TerminalSession.start(
            DefaultShell.command(System.getProperty("os.name"), System.getenv()),
            System.getenv(), Path.of(System.getProperty("user.home")), 120, 36, options.scrollback());
        session.exitFuture().thenAccept(code -> SwingUtilities.invokeLater(() -> System.exit(0)));

        SwingUtilities.invokeLater(() -> {
            JFrame frame = new JFrame("Moray");
            TerminalView view = new TerminalView(session, options);
            frame.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
            frame.addWindowListener(new WindowAdapter() {
                @Override
                public void windowClosed(WindowEvent e) {
                    session.close();
                    System.exit(0);
                }
            });
            session.addListener(new TerminalSession.Listener() {
                @Override
                public void titleChanged(String title) {
                    SwingUtilities.invokeLater(() -> frame.setTitle(title.isBlank() ? "Moray" : title));
                }
            });
            frame.add(view);
            frame.pack();
            frame.setLocationRelativeTo(null);
            frame.setVisible(true);
            view.requestFocusInWindow();
        });
    }
}
```

- [ ] **Step 6: Run it and check by hand (macOS)**

Run: `./gradlew :moray-app:run`
Expected, one by one:
1. A window titled with your shell's title shows your prompt within about a second.
2. `ls -G` shows colors; `printf '\033[38;2;255;100;0mTRUECOLOR\033[0m\n'` prints orange text.
3. `echo '-> => != === <= >='` shows ligatures (arrows, not separate characters).
4. `echo '日本語 🚀'` shows CJK and the emoji, each taking two cells.
5. `vim` opens; arrow keys move; `:q` returns to the prompt; in insert mode the cursor becomes a beam if your vim config requests it.
6. In zsh, left Option+B / Option+F jump words; right Option+E then E types `é`.
7. Resize the window, then `stty size` prints the new rows and columns.
8. `exit` closes the app; relaunch and ⌘Q also quits.

- [ ] **Step 7: Commit**

```bash
git add moray-app/src
git commit -m "Add Moray app: one window running the user's login shell"
```

### Task 10: The throughput benchmark

**Files:**
- Create: `moray-app/src/main/java/dev/moray/app/Bench.java`
- Modify: `moray-app/build.gradle.kts` (register the `bench` task)

**Interfaces:**
- Consumes: `TerminalSession.start`, `exitFuture()` (Tasks 4–5); `TerminalView`, `TerminalOptions.defaults()` (Task 8).
- Produces: `./gradlew :moray-app:bench`, which prints one line: `Moray view: <MB> MB in <s> s = <rate> MB/s`.

The benchmark repeats the engine spike's measurement through Moray's own view: it generates (once) ~100 MB of text where every fourth line has bold 16-color and truecolor escapes, runs `cat` on it in a session shown in a window, and times from process start until the process exits. `cat` cannot exit until the emulator has consumed its output, so this measures emulator + rendering throughput.

- [ ] **Step 1: Implement `Bench`**

`moray-app/src/main/java/dev/moray/app/Bench.java`:
```java
package dev.moray.app;

import dev.moray.terminal.TerminalOptions;
import dev.moray.terminal.TerminalSession;
import dev.moray.terminal.TerminalView;

import javax.swing.JFrame;
import javax.swing.SwingUtilities;
import java.io.IOException;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Random;

/** Pipes ~100 MB of ANSI-colored text through a Moray terminal window and prints the throughput. */
public final class Bench {
    private static final long TARGET_BYTES = 100L * 1024 * 1024;
    private static final String[] WORDS = ("the quick brown fox jumps over lazy dog lorem ipsum dolor sit amet "
        + "consectetur adipiscing elit sed do eiusmod tempor").split(" ");
    private static final int[] COLORS = {31, 32, 33, 34, 35, 36, 91, 92, 93, 94};

    private Bench() {
    }

    public static void main(String[] args) throws Exception {
        Path data = Path.of(args[0]).toAbsolutePath();
        if (!Files.exists(data) || Files.size(data) < TARGET_BYTES) {
            generate(data);
        }
        long bytes = Files.size(data);
        boolean windows = System.getProperty("os.name").startsWith("Windows");
        List<String> command = windows
            ? List.of("cmd.exe", "/c", "type", data.toString())
            : List.of("/bin/cat", data.toString());
        TerminalOptions options = TerminalOptions.defaults();

        long start = System.nanoTime();
        TerminalSession session = TerminalSession.start(command, System.getenv(), data.getParent(), 120, 36, options.scrollback());
        SwingUtilities.invokeAndWait(() -> {
            JFrame frame = new JFrame("Moray bench");
            frame.add(new TerminalView(session, options));
            frame.pack();
            frame.setVisible(true);
        });
        session.exitFuture().get();
        double seconds = (System.nanoTime() - start) / 1e9;

        System.out.printf("Moray view: %.1f MB in %.2f s = %.1f MB/s%n", bytes / 1e6, seconds, bytes / 1e6 / seconds);
        System.exit(0);
    }

    static void generate(Path file) throws IOException {
        Files.createDirectories(file.getParent());
        Random random = new Random(42);
        StringBuilder line = new StringBuilder();
        long written = 0;
        try (Writer out = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) {
            for (int n = 1; written < TARGET_BYTES; n++) {
                line.setLength(0);
                StringBuilder words = new StringBuilder();
                int count = 4 + random.nextInt(15);
                for (int i = 0; i < count; i++) {
                    if (i > 0) {
                        words.append(' ');
                    }
                    words.append(WORDS[random.nextInt(WORDS.length)]);
                }
                if (n % 4 == 0) {
                    line.append("\033[1;").append(COLORS[random.nextInt(COLORS.length)]).append('m')
                        .append(String.format("%08d", n)).append("\033[0m ").append(words)
                        .append(" \033[38;2;").append(random.nextInt(256)).append(';').append(random.nextInt(256))
                        .append(';').append(random.nextInt(256)).append("m█\033[0m");
                } else {
                    line.append(String.format("%08d", n)).append(' ').append(words);
                }
                line.append('\n');
                out.write(line.toString());
                written += line.length();
            }
        }
    }
}
```

- [ ] **Step 2: Register the Gradle task**

Append to `moray-app/build.gradle.kts`:
```kotlin
tasks.register<JavaExec>("bench") {
    group = "verification"
    description = "Pipes ~100 MB of ANSI-colored text through a Moray terminal window and prints MB/s."
    classpath = sourceSets["main"].runtimeClasspath
    mainClass = "dev.moray.app.Bench"
    jvmArgs("--enable-native-access=ALL-UNNAMED")
    args(layout.buildDirectory.file("bench/ansi-100mb.txt").get().asFile.absolutePath)
}
```

- [ ] **Step 3: Run the benchmark**

Close anything heavy (games, VMs) first. Run: `./gradlew :moray-app:bench`
Expected: a window opens and scrolls, then one line such as `Moray view: 105.4 MB in 2.61 s = 40.4 MB/s`.

**Gate (spec §9):** the rate must be **≥ 35 MB/s** (the `jediterm-ui` baseline from the spike); the target is ≥ 45 MB/s. Record the number in the commit message. If it is below 35 MB/s, stop and report it instead of optimizing blindly. The first levers are (a) caching each run's `GlyphVector` between frames, and (b) repainting only rows that changed.

- [ ] **Step 4: Run the full check**

Run: `./gradlew check`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 5: Commit**

```bash
git add moray-app
git commit -m "Add throughput benchmark (measured: <rate> MB/s on <machine>)"
```

---

## Spec coverage for this plan

| Spec section | Covered here | Deferred to |
|---|---|---|
| §2 decisions (JBR 25, Gradle, jediterm-core 3.76, pty4j 0.13.10, own view) | Tasks 1, 4, 5, 8 | — |
| §3 two modules, one-way dependency, JediTerm confined | Task 1 build files; all tasks | — |
| §4.1 session, `PtyConnector`, reader thread, `TerminalDisplay`, snapshot under lock | Tasks 4, 5 | exit message in pane → plan 2 |
| §4.2 runs, ligatures, per-code-point fallback, wide chars, cursor shapes, DECSCUSR | Tasks 2, 3, 6, 8 | dirty-row repaint (if the benchmark needs it), `line_height`, dimming → plans 3–4; DECSCUSR 0 → plan 2 |
| §4.3 keys, Option-as-Meta | Task 7 | mouse reporting, bracketed paste, wheel → plan 2 |
| §4.4–4.6 selection, scrollback, search, shell integration | — | plan 2 |
| §5–6 window chrome, keybindings, look | minimal window in Task 9 | plan 3 |
| §7 configuration | defaults in `TerminalOptions` | plan 4 |
| §8 tests, benchmark, CI on three OSes | every task; Task 10; Task 1 CI | — |
