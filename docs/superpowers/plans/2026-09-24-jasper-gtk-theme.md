# GTK Application Style Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

Status: written 2026-09-24, awaiting user review. Branch `claude/gtk-theme`.

**Goal:** Add `ui.theme.style = "gtk"`, which installs the JDK's GTK look and feel, uses native Swing chrome, derives the terminal colours from the GTK theme and draws icons from the desktop's freedesktop icon theme, falling back to modern where GTK is unavailable.

**Architecture:** `ThemeController` gains a requested/effective style split and a GTK installer (`GtkDefaults`) that aliases sampled GTK colours into Jasper's `Jasper.*` keys; `GtkPalette` turns sampled text colours into the terminal palette. The "retro" presentation predicate splits into `nativeChrome()` (retro and GTK: structure) and `retro()` (Metal-only paint). A new platform-level Icon Theme Specification resolver (`FreedesktopIcons`, `IconThemeName`, `FreedesktopNames`) backs GTK-mode icons in `AppIcons`, with bundled artwork as the per-icon fallback.

**Tech Stack:** Java 25 on JBR 25, Swing, `com.sun.java.swing.plaf.gtk.GTKLookAndFeel` (by class name only), FlatLaf 3.7 / `flatlaf-extras` `FlatSVGIcon` (jsvg) for SVG rasterisation, JUnit 5 + AssertJ.

**Spec:** [`docs/superpowers/specs/2026-09-24-jasper-gtk-theme-design.md`](../specs/2026-09-24-jasper-gtk-theme-design.md)

## Global Constraints

- Java 25 on JBR; run Gradle through `./gradlew` only. `./gradlew check` must pass (includes `verifyApplicationArchitecture`, doclint, source hygiene).
- Never reference `com.sun.java.swing.plaf.gtk` types at compile time; install by class-name string `"com.sun.java.swing.plaf.gtk.GTKLookAndFeel"` via `UIManager.setLookAndFeel(String)`.
- No JNI/Panama and no new dependency; SVGs use `FlatSVGIcon` from the existing `flatlaf-extras`.
- No SDK API change (JavaDoc wording only). `OldGnomeIcon` keeps its name.
- `dev.jasper.app.platform` depends on no other Jasper package; `dev.jasper.app.appearance` depends only on `dev.jasper.app.config`, `dev.jasper.app.lifecycle`, `dev.jasper.terminal.config`.
- No interface without two real implementations: seams are `Function`/`Supplier`/`Predicate`, not new interfaces.
- Retro and modern behaviour must not change; existing tests stay green.
- Do not launch the GUI. Linux visual checks are the user's.
- Source hygiene: no raw control/private-use characters; use Java escapes.
- One commit per task; messages end with:
  ```
  🤖 Generated with Claude Code at The Home Depot

  Co-Authored-By: Claude <noreply@anthropic.com>
  ```
- Style change to/from `"gtk"` needs a full restart; `ui.theme.variant` is ignored in GTK mode.
- GTK falls back to modern on macOS, Windows, and Linux without native GTK, with a status-bar warning; it never fails startup.

**Deviation from spec (recorded here and in `docs/STATUS.md` at the end):** the terminal `Palette` record has one `selection` colour and no selection foreground, so `GtkPalette` maps the sampled text selection background only.

## Review Focus

1. A GTK theme whose delegates leave sampled colours `null` → every `Jasper.*` key GTK sets is still non-null (pinned in Task 3 `GtkDefaultsTest.nullSamplesStillDefineEveryKey`).
2. Modern icon themes ship mostly `-symbolic` SVGs → they must be recoloured to the chrome foreground so they stay visible on dark themes (pinned in Task 7 `DesktopIconsTest.symbolicSkinIconsTakeTheDarkChromeForeground`).
3. An icon theme name from settings that is blank, quoted, or path-like (`../x`) → ignored, never used as a path (pinned in Task 6 `IconThemeNameTest`).
4. Linux CI with a real display may install GTK for real → tests that iterate `ThemeStyle.values()` or use the real installer must accept either outcome (pinned in Task 3 `realInstallerEitherInstallsGtkOrFallsBack` and Task 5's `RetroChromeTest` edit).
5. Changing `ui.font` while GTK is running reinstalls the LAF → palette and GTK keys survive (pinned in Task 3 `fontReconfigurationKeepsTheGtkPalette`).

---

## File Structure

Create (main, `jasper-app/src/main/java/dev/jasper/app/`):
- `appearance/GtkDefaults.java` — install GTK LAF by name, sample colours, alias `Jasper.*` keys.
- `appearance/GtkPalette.java` — terminal palette from sampled colours; luminance rule.
- `platform/FreedesktopIcons.java` — Icon Theme Specification lookup and rasterisation.
- `platform/IconThemeName.java` — active icon theme name discovery.
- `platform/FreedesktopNames.java` — Jasper name → freedesktop candidates.
- `platform/DesktopIcons.java` — GTK-mode icon selection with bundled fallback.
- `platform/DesktopSkinIcon.java` — captured GTK semantic icon with 24 px toolbar variant.

Create (test, `jasper-app/src/test/java/dev/jasper/app/`):
- `appearance/GtkTestThemes.java` — test-only bridge: GTK-style controller on any host.
- `appearance/GtkPaletteTest.java`, `appearance/GtkDefaultsTest.java`, `appearance/GtkThemeControllerTest.java`
- `workspace/GtkChromeTest.java`
- `platform/FreedesktopIconsTest.java`, `platform/IconThemeNameTest.java`, `platform/FreedesktopNamesTest.java`, `platform/DesktopIconsTest.java`

Modify: `config/ThemeStyle.java`, `config/ConfigLoader.java`, `config/ConfigTemplate.java`, `config.example.toml`, `appearance/BuiltinTheme.java`, `appearance/ResolvedTheme.java`, `appearance/ThemeController.java`, `appearance/MetalDefaults.java`, `application/ConfigurationController.java`, `application/JasperApplication.java`, `platform/SwingAppearance.java`, `platform/AppIcons.java`, `platform/OldGnomeCatalog.java`, `windows/NativeShells.java`, `workspace/WindowContent.java`, `workspace/WindowChrome.java`, `workspace/WindowCommands.java`, `workspace/WindowCommandPalette.java`, `workspace/WindowRail.java`, `workspace/WindowStatusBar.java`, `workspace/TerminalDeck.java`, `palette/CommandPalette.java`, tests `config/ConfigLoaderTest.java`, `workspace/ConfigurationControllerTest.java`, `workspace/RetroChromeTest.java`, `workspace/CommandPalettePreview.java`, SDK `jasper-sdk/src/main/java/dev/jasper/sdk/ui/{Appearance,OldGnomeIcon}.java`, docs.

Paths below abbreviate `jasper-app/src/main/java/dev/jasper/app/` as `main/` and `jasper-app/src/test/java/dev/jasper/app/` as `test/`.

---

### Task 1: `"gtk"` configuration value

**Files:**
- Modify: `main/config/ThemeStyle.java`, `main/config/ConfigLoader.java:224-225`, `main/config/ConfigTemplate.java:126-131`, `config.example.toml:102-107`, `docs/configuration.md:85,124-125`
- Test: `test/config/ConfigLoaderTest.java`

**Interfaces:**
- Produces: `ThemeStyle.GTK` (enum order `MODERN, RETRO, GTK`).

- [ ] **Step 1: Write the failing test** — append to `ConfigLoaderTest` beside `retroStyleDoesNotRewriteSavedVariant`:

```java
@Test void gtkStyleParsesAndKeepsTheSavedVariant() {
    var result = parse("[ui.theme]\nstyle='gtk'\nvariant='light'\n");
    assertThat(result.rejected()).isFalse();
    assertThat(result.diagnostics()).isEmpty();
    assertThat(result.snapshot().style()).isEqualTo(ThemeStyle.GTK);
    assertThat(result.snapshot().variant()).isEqualTo(Appearance.LIGHT);
    assertThat(parse("[ui.theme]\nstyle='GTK'\n").diagnostics()).singleElement()
        .satisfies(d -> assertThat(d.message()).contains("gtk, modern, retro"));
}
```

- [ ] **Step 2: Run it to verify it fails**

Run: `./gradlew :jasper-app:test --tests 'dev.jasper.app.config.ConfigLoaderTest.gtkStyleParsesAndKeepsTheSavedVariant'`
Expected: compilation failure, `cannot find symbol ... GTK`.

- [ ] **Step 3: Implement**

`ThemeStyle.java`:
```java
package dev.jasper.app.config;

/** Application presentation family. Changing it requires a full restart. */
public enum ThemeStyle { MODERN, RETRO, GTK }
```
(Keep the existing JavaDoc line if it differs; only add `GTK`.)

`ConfigLoader.java`, the `ui.theme.style` case:
```java
            case "ui.theme.style" -> style = choice(path, value, Map.of(
    "modern", ThemeStyle.MODERN, "retro", ThemeStyle.RETRO, "gtk", ThemeStyle.GTK), style);
```

`ConfigTemplate.java` `[ui.theme]` block — replace the five comment lines above `# style = "modern"` and the variant comment:
```
            [ui.theme]
            # Application style: "modern", "retro" (stock light Java Metal) or "gtk" (Linux GTK desktop theme).
            # Set style to "retro" for Metal controls, classic icons and a high-contrast terminal.
            # Set style to "gtk" to follow the desktop's GTK theme, icon theme and text colors; other systems use modern.
            # Restart required: fully quit and relaunch, including with background residency enabled.
            # Reload reports the pending style change; existing windows keep their current style.
            # style = "modern"
            # Variant applies only to modern; retro and gtk ignore it.
            # Theme variant: "dark" or "light"; switches chrome and terminal colors live across windows.
            # variant = "dark"
```
Apply the same wording (uncommented `style = "modern"` line as it is today) to `config.example.toml` lines 102-107.

`docs/configuration.md`: line 85 comment becomes `# "modern", "retro" or "gtk"; fully quit and relaunch after changing style.`; table rows:
```
| `ui.theme.style` | `"modern"` | `"modern"`, `"retro"`, `"gtk"` | Full process restart |
| `ui.theme.variant` | `"dark"` | `"dark"`, `"light"` | Live in modern mode; retained but ignored in retro and gtk |
```

- [ ] **Step 4: Run tests**

Run: `./gradlew :jasper-app:test --tests 'dev.jasper.app.config.*'`
Expected: PASS (template tests parse the template cleanly; if a test pins the old comment text, update that assertion to the new wording).

`test/workspace/CommandPalettePreview.java` does not switch on `ThemeStyle`; nothing else switches on it (verified by `grep -rn "case RETRO"`).

- [ ] **Step 5: Commit**

```bash
git add jasper-app/src/main/java/dev/jasper/app/config config.example.toml docs/configuration.md jasper-app/src/test/java/dev/jasper/app/config
git commit -m "feat(config): accept ui.theme.style = \"gtk\"

🤖 Generated with Claude Code at The Home Depot

Co-Authored-By: Claude <noreply@anthropic.com>"
```

---

### Task 2: GTK terminal palette and theme brightness

**Files:**
- Create: `main/appearance/GtkPalette.java`
- Modify: `main/appearance/BuiltinTheme.java`, `main/appearance/ResolvedTheme.java`, `main/application/JasperApplication.java:148-149,311,322`, `main/workspace/WindowContent.java:196,198`, `main/windows/NativeShells.java:120,151`, `main/workspace/WindowCommandPalette.java:191`, `test/workspace/CommandPalettePreview.java:290-294`
- Test: `test/appearance/GtkPaletteTest.java`

**Interfaces:**
- Produces:
  - `GtkPalette.BACKGROUND/FOREGROUND/CARET/SELECTION` — `String` UIManager keys `"Jasper.gtkTextBackground"`, `"Jasper.gtkTextForeground"`, `"Jasper.gtkTextCaret"`, `"Jasper.gtkTextSelectionBackground"`.
  - `static Palette GtkPalette.from(Function<String, Color> colors)`
  - `static boolean GtkPalette.dark(Color)` — WCAG relative luminance `< 0.5`.
  - `BuiltinTheme.GTK`
  - `Appearance ResolvedTheme.appearance()` and `boolean ResolvedTheme.dark()`.

- [ ] **Step 1: Write the failing test** — `test/appearance/GtkPaletteTest.java`:

```java
package dev.jasper.app.appearance;

import dev.jasper.app.config.Appearance;
import dev.jasper.terminal.config.Palette;
import java.awt.Color;
import java.util.Map;
import javax.swing.plaf.ColorUIResource;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class GtkPaletteTest {
    @Test void lightTextViewKeepsItsColorsWithLightAnsi() {
        var palette = GtkPalette.from(Map.<String, Color>of(
            GtkPalette.BACKGROUND, new ColorUIResource(0xffffff), GtkPalette.FOREGROUND, new Color(0x2e3436),
            GtkPalette.CARET, new Color(0x000000), GtkPalette.SELECTION, new Color(0x3584e4))::get);
        assertThat(palette.background()).isEqualTo(Color.WHITE).isExactlyInstanceOf(Color.class);
        assertThat(palette.foreground()).isEqualTo(new Color(0x2e3436));
        assertThat(palette.cursor()).isEqualTo(Color.BLACK);
        assertThat(palette.selection()).isEqualTo(new Color(0x3584e4));
        assertThat(palette.ansi()).isEqualTo(Palette.jasperLight().ansi());
    }

    @Test void darkTextViewUsesDarkAnsiAndCursorDefaultsToForeground() {
        var palette = GtkPalette.from(Map.<String, Color>of(
            GtkPalette.BACKGROUND, new Color(0x2d2d2d), GtkPalette.FOREGROUND, new Color(0xeeeeec))::get);
        assertThat(palette.ansi()).isEqualTo(Palette.jasperDark().ansi());
        assertThat(palette.cursor()).isEqualTo(new Color(0xeeeeec));
        assertThat(palette.selection()).isEqualTo(Palette.jasperDark().selection());
    }

    @Test void missingColorsUseTheJasperLightPaletteWithAForegroundCursor() {
        var palette = GtkPalette.from(key -> null);
        var light = Palette.jasperLight();
        assertThat(palette.background()).isEqualTo(light.background());
        assertThat(palette.foreground()).isEqualTo(light.foreground());
        assertThat(palette.cursor()).isEqualTo(light.foreground());
        assertThat(palette.selection()).isEqualTo(light.selection());
        assertThat(palette.ansi()).isEqualTo(light.ansi());
    }

    @Test void luminanceThresholdIsOneHalf() {
        assertThat(GtkPalette.dark(Color.BLACK)).isTrue();
        assertThat(GtkPalette.dark(new Color(0x808080))).isTrue();   // L = 0.216
        assertThat(GtkPalette.dark(new Color(0xbcbcbc))).isFalse();  // L = 0.57
        assertThat(GtkPalette.dark(Color.WHITE)).isFalse();
    }

    @Test void resolvedGtkThemeReportsBrightnessFromItsTerminal() {
        var dark = new ResolvedTheme(BuiltinTheme.GTK, GtkPalette.from(Map.<String, Color>of(GtkPalette.BACKGROUND, new Color(0x1e1e1e))::get));
        assertThat(dark.appearance()).isEqualTo(Appearance.DARK);
        assertThat(dark.dark()).isTrue();
        assertThat(new ResolvedTheme(BuiltinTheme.GTK, Palette.jasperLight()).appearance()).isEqualTo(Appearance.LIGHT);
        assertThat(new ResolvedTheme(BuiltinTheme.RETRO, BuiltinTheme.RETRO.palette()).appearance()).isEqualTo(Appearance.LIGHT);
        assertThat(new ResolvedTheme(BuiltinTheme.DARK, Palette.jasperDark()).dark()).isTrue();
    }
}
```

- [ ] **Step 2: Run to verify it fails**

Run: `./gradlew :jasper-app:test --tests 'dev.jasper.app.appearance.GtkPaletteTest'`
Expected: compilation failure (`GtkPalette` missing).

- [ ] **Step 3: Implement**

`main/appearance/GtkPalette.java`:
```java
package dev.jasper.app.appearance;

import dev.jasper.terminal.config.Palette;
import java.awt.Color;
import java.util.function.Function;

/** Terminal colours from the installed GTK theme's text view; the ANSI set follows its brightness. */
final class GtkPalette {
    static final String BACKGROUND = "Jasper.gtkTextBackground";
    static final String FOREGROUND = "Jasper.gtkTextForeground";
    static final String CARET = "Jasper.gtkTextCaret";
    static final String SELECTION = "Jasper.gtkTextSelectionBackground";
    private GtkPalette() {}

    static Palette from(Function<String, Color> colors) {
        Color background = colors.apply(BACKGROUND);
        Palette base = background != null && dark(background) ? Palette.jasperDark() : Palette.jasperLight();
        Color foreground = orElse(colors.apply(FOREGROUND), base.foreground());
        return new Palette(plain(foreground), plain(orElse(background, base.background())),
            plain(orElse(colors.apply(CARET), foreground)), plain(orElse(colors.apply(SELECTION), base.selection())), base.ansi());
    }

    /** WCAG relative luminance below one half. */
    static boolean dark(Color color) {
        return 0.2126 * channel(color.getRed()) + 0.7152 * channel(color.getGreen()) + 0.0722 * channel(color.getBlue()) < 0.5;
    }

    private static double channel(int value) {
        double v = value / 255.0;
        return v <= 0.04045 ? v / 12.92 : Math.pow((v + 0.055) / 1.055, 2.4);
    }
    private static Color orElse(Color color, Color fallback) { return color != null ? color : fallback; }
    // The terminal compares colours by value; drop UIResource subclasses and alpha.
    private static Color plain(Color color) { return new Color(color.getRed(), color.getGreen(), color.getBlue()); }
}
```

`BuiltinTheme.java`: add the constant after `RETRO`:
```java
    RETRO("retro", "Retro", RetroPalette.create()),
    /** Its palette is a placeholder; the controller derives the real one from the installed GTK theme. */
    GTK("gtk", "GTK", Palette.jasperLight());
```

`ResolvedTheme.java`:
```java
package dev.jasper.app.appearance;

import dev.jasper.app.config.Appearance;
import dev.jasper.terminal.config.Palette;

import java.util.Objects;

/** Immutable chrome and terminal-palette resolution published on EDT; owns no Swing components or native resources. */
public record ResolvedTheme(BuiltinTheme chrome, Palette palette) {
    public ResolvedTheme { Objects.requireNonNull(chrome); Objects.requireNonNull(palette); }

    /** The built-in theme's own brightness; GTK's follows its terminal background. */
    public Appearance appearance() {
        if (chrome != BuiltinTheme.GTK) return chrome.appearance();
        return GtkPalette.dark(palette.background()) ? Appearance.DARK : Appearance.LIGHT;
    }

    public boolean dark() { return appearance() == Appearance.DARK; }
}
```

Call sites (replace exactly):
- `JasperApplication.java` — every `themes.current().chrome() == BuiltinTheme.DARK` → `themes.current().dark()`; every `theme.chrome() == BuiltinTheme.DARK` → `theme.dark()` (lines 148, 149, 311, 322). Remove the `BuiltinTheme` import if it becomes unused.
- `WindowContent.java:196,198` — `theme.chrome().appearance() == Appearance.LIGHT` → `theme.appearance() == Appearance.LIGHT`; `content.theme().chrome().appearance()` → `content.theme().appearance()`.
- `NativeShells.java:120,151` — `resolved.chrome().appearance()` → `resolved.appearance()`.
- `WindowCommandPalette.java:191` — `owner.theme().chrome().appearance()` → `owner.theme().appearance()`.
- `CommandPalettePreview.java` `themeSlug` switch — add `case GTK -> "gtk";`.

- [ ] **Step 4: Run tests**

Run: `./gradlew :jasper-app:test --tests 'dev.jasper.app.appearance.*' --tests 'dev.jasper.app.workspace.*' --tests 'dev.jasper.app.application.*'`
Expected: PASS.

- [ ] **Step 5: Commit** — `git add -A jasper-app && git commit -m "feat(appearance): derive a GTK terminal palette and theme brightness"` (with the trailer from Global Constraints).

---

### Task 3: GTK installer and requested/effective style

**Files:**
- Create: `main/appearance/GtkDefaults.java`, `test/appearance/GtkTestThemes.java`, `test/appearance/GtkDefaultsTest.java`, `test/appearance/GtkThemeControllerTest.java`
- Modify: `main/appearance/ThemeController.java`

**Interfaces:**
- Consumes: `GtkPalette` keys and `from`, `BuiltinTheme.GTK` (Task 2); `ThemeStyle.GTK` (Task 1).
- Produces:
  - `GtkDefaults.LOOK_AND_FEEL = "com.sun.java.swing.plaf.gtk.GTKLookAndFeel"`
  - sample keys `GtkDefaults.PANEL_BACKGROUND="panel.background"`, `LABEL_FOREGROUND="label.foreground"`, `LIST_SELECTION_BACKGROUND="list.selectionBackground"`, `LIST_SELECTION_FOREGROUND="list.selectionForeground"`, `TEXT_BACKGROUND="text.background"`, `TEXT_FOREGROUND="text.foreground"`, `TEXT_CARET="text.caret"`, `TEXT_SELECTION_BACKGROUND="text.selectionBackground"`
  - `static boolean GtkDefaults.install()` (throws `IllegalStateException` when unavailable)
  - `static void GtkDefaults.decorate(UIDefaults, Function<String, Color>)` — puts `Jasper.nativeChrome=true`, `Jasper.gtk=true`, aliases, status colours, `GtkPalette` keys.
  - `ThemeStyle ThemeController.style()` (effective), `ThemeStyle ThemeController.requestedStyle()`, `Optional<String> ThemeController.fallbackReason()`
  - Test bridge `GtkTestThemes.LIGHT`, `GtkTestThemes.DARK` (`Map<String, Color>`), `GtkTestThemes.themes(Map<String, Color>)`, `GtkTestThemes.unavailable(Appearance)`.

- [ ] **Step 1: Write the test bridge** — `test/appearance/GtkTestThemes.java`:

```java
package dev.jasper.app.appearance;

import dev.jasper.app.config.Appearance;
import dev.jasper.app.config.ThemeStyle;
import java.awt.Color;
import java.util.Map;
import javax.swing.UIManager;
import javax.swing.UnsupportedLookAndFeelException;
import javax.swing.plaf.metal.MetalLookAndFeel;

/** Package-local test access, excluded from production artifacts: a GTK-style controller on any host. */
public final class GtkTestThemes {
    private GtkTestThemes() {}

    /** Adwaita-like samples. */
    public static final Map<String, Color> LIGHT = Map.of(
        GtkDefaults.PANEL_BACKGROUND, new Color(0xf6f5f4), GtkDefaults.LABEL_FOREGROUND, new Color(0x2e3436),
        GtkDefaults.LIST_SELECTION_BACKGROUND, new Color(0x3584e4), GtkDefaults.LIST_SELECTION_FOREGROUND, Color.WHITE,
        GtkDefaults.TEXT_BACKGROUND, Color.WHITE, GtkDefaults.TEXT_FOREGROUND, Color.BLACK,
        GtkDefaults.TEXT_CARET, Color.BLACK, GtkDefaults.TEXT_SELECTION_BACKGROUND, new Color(0x3584e4));

    /** Adwaita-dark-like samples. */
    public static final Map<String, Color> DARK = Map.of(
        GtkDefaults.PANEL_BACKGROUND, new Color(0x353535), GtkDefaults.LABEL_FOREGROUND, new Color(0xeeeeec),
        GtkDefaults.LIST_SELECTION_BACKGROUND, new Color(0x15539e), GtkDefaults.LIST_SELECTION_FOREGROUND, Color.WHITE,
        GtkDefaults.TEXT_BACKGROUND, new Color(0x2d2d2d), GtkDefaults.TEXT_FOREGROUND, Color.WHITE,
        GtkDefaults.TEXT_CARET, Color.WHITE, GtkDefaults.TEXT_SELECTION_BACKGROUND, new Color(0x15539e));

    /** Metal delegates decorated exactly as GTK would be, so native-chrome and icon paths run headless. */
    public static ThemeController themes(Map<String, Color> colors) {
        return new ThemeController(ThemeStyle.GTK, Appearance.DARK, theme -> {
            if (theme != BuiltinTheme.GTK) return ThemeController.install(theme);
            try { UIManager.setLookAndFeel(new MetalLookAndFeel()); }
            catch (UnsupportedLookAndFeelException failure) { throw new IllegalStateException(failure); }
            GtkDefaults.decorate(UIManager.getLookAndFeelDefaults(), colors::get);
            return true;
        });
    }

    /** A GTK request on a host where installing GTK fails. */
    public static ThemeController unavailable(Appearance saved) {
        return new ThemeController(ThemeStyle.GTK, saved, theme -> {
            if (theme == BuiltinTheme.GTK) throw new IllegalStateException("GTK is not available on this desktop");
            return ThemeController.install(theme);
        });
    }
}
```

- [ ] **Step 2: Write the failing tests**

`test/appearance/GtkDefaultsTest.java`:
```java
package dev.jasper.app.appearance;

import dev.jasper.app.testsupport.EdtTestExtension;
import java.awt.Color;
import java.util.List;
import javax.swing.UIDefaults;
import javax.swing.UIManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import static org.assertj.core.api.Assertions.*;

@ExtendWith(EdtTestExtension.class)
class GtkDefaultsTest {
    static final List<String> KEYS = List.of("Jasper.titleBackground", "Jasper.paletteBackground", "Jasper.tabSelectedBackground",
        "Jasper.titleForeground", "Jasper.chromeForeground", "Jasper.tabSelectedForeground", "Jasper.paletteForeground",
        "Jasper.titleInactiveForeground", "Jasper.mutedForeground", "Jasper.paletteMutedForeground",
        "Jasper.titleSeparator", "Jasper.splitDivider", "Component.borderColor", "Jasper.paletteBorder",
        "Jasper.paletteAccent", "Component.focusedBorderColor", "Jasper.paletteSelectionBackground",
        "Jasper.paletteSelectionForeground", "Jasper.runningForeground", "Actions.Red", "Actions.Yellow", "Actions.Green",
        "Jasper.configSuccessForeground", "Jasper.configWarningForeground", "Jasper.configErrorForeground");

    @Test void lightSamplesBecomeJasperAliasesAndLightStatusColors() {
        var defaults = new UIDefaults();
        GtkDefaults.decorate(defaults, GtkTestThemes.LIGHT::get);
        assertThat(defaults.getBoolean("Jasper.nativeChrome")).isTrue();
        assertThat(defaults.getBoolean("Jasper.gtk")).isTrue();
        assertThat(defaults.getBoolean("Jasper.retro")).isFalse();
        assertThat(defaults.getColor("Jasper.titleBackground")).isEqualTo(new Color(0xf6f5f4));
        assertThat(defaults.getColor("Jasper.chromeForeground")).isEqualTo(new Color(0x2e3436));
        assertThat(defaults.getColor("Jasper.paletteSelectionBackground")).isEqualTo(new Color(0x3584e4));
        assertThat(defaults.getColor("Jasper.paletteSelectionForeground")).isEqualTo(Color.WHITE);
        Color muted = defaults.getColor("Jasper.mutedForeground");
        assertThat(muted.getRed()).isStrictlyBetween(0x2e, 0xf6);
        assertThat(defaults.getColor("Jasper.configErrorForeground")).isEqualTo(new Color(0xb42332));
        assertThat(defaults.getColor(GtkPalette.BACKGROUND)).isEqualTo(Color.WHITE);
        assertThat(defaults.getColor(GtkPalette.CARET)).isEqualTo(Color.BLACK);
    }

    @Test void darkSamplesSelectDarkStatusColors() {
        var defaults = new UIDefaults();
        GtkDefaults.decorate(defaults, GtkTestThemes.DARK::get);
        assertThat(defaults.getColor("Jasper.configErrorForeground")).isEqualTo(new Color(0xff858d));
        assertThat(defaults.getColor("Actions.Green")).isEqualTo(new Color(0x499c54));
    }

    @Test void nullSamplesStillDefineEveryKey() {
        var defaults = new UIDefaults();
        GtkDefaults.decorate(defaults, key -> null);
        for (String key : KEYS) assertThat(defaults.getColor(key)).as(key).isNotNull();
        assertThat(defaults.get(GtkPalette.BACKGROUND)).isNull();
    }

    @Test void installEitherSucceedsOrExplainsWhyGtkIsUnavailable() {
        var before = UIManager.getLookAndFeel();
        try {
            assertThat(GtkDefaults.install()).isTrue();
            assertThat(UIManager.getLookAndFeel().getClass().getName()).isEqualTo(GtkDefaults.LOOK_AND_FEEL);
            assertThat(UIManager.getBoolean("Jasper.gtk")).isTrue();
        } catch (IllegalStateException unavailable) {
            assertThat(unavailable).hasMessageContaining("GTK");
            assertThat(UIManager.getLookAndFeel()).isSameAs(before);
        } finally { new ThemeController(); }
    }

    @Test void macOsAndWindowsNeverInstallGtk() {
        String os = System.getProperty("os.name");
        org.junit.jupiter.api.Assumptions.assumeTrue(os.startsWith("Mac") || os.startsWith("Windows"));
        assertThatIllegalStateException().isThrownBy(GtkDefaults::install).withMessageContaining("GTK");
    }
}
```

`test/appearance/GtkThemeControllerTest.java`:
```java
package dev.jasper.app.appearance;

import dev.jasper.app.config.Appearance;
import dev.jasper.app.config.ThemeStyle;
import dev.jasper.app.config.UiFontConfig;
import dev.jasper.app.testsupport.EdtTestExtension;
import dev.jasper.terminal.config.Palette;
import java.awt.Color;
import javax.swing.UIManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import static org.assertj.core.api.Assertions.*;

@ExtendWith(EdtTestExtension.class)
class GtkThemeControllerTest {
    @AfterEach void restore() throws Exception { javax.swing.SwingUtilities.invokeAndWait(() -> new ThemeController()); }

    @Test void gtkUsesDecoratedChromeAndItsDerivedTerminal() {
        var themes = GtkTestThemes.themes(GtkTestThemes.LIGHT);
        assertThat(themes.style()).isEqualTo(ThemeStyle.GTK);
        assertThat(themes.requestedStyle()).isEqualTo(ThemeStyle.GTK);
        assertThat(themes.fallbackReason()).isEmpty();
        assertThat(themes.current().chrome()).isEqualTo(BuiltinTheme.GTK);
        assertThat(themes.current().palette().background()).isEqualTo(Color.WHITE);
        assertThat(themes.current().palette().ansi()).isEqualTo(Palette.jasperLight().ansi());
        assertThat(themes.choice()).isEqualTo(Appearance.LIGHT);
        assertThat(UIManager.getBoolean("Jasper.gtk")).isTrue();
        var before = themes.current();
        themes.selectAppearance(Appearance.DARK);
        themes.configure(Appearance.DARK);
        assertThat(themes.current()).isEqualTo(before);
    }

    @Test void darkGtkThemeIsDarkEverywhere() {
        var themes = GtkTestThemes.themes(GtkTestThemes.DARK);
        assertThat(themes.choice()).isEqualTo(Appearance.DARK);
        assertThat(themes.current().dark()).isTrue();
        assertThat(themes.current().palette().ansi()).isEqualTo(Palette.jasperDark().ansi());
    }

    @Test void unavailableGtkFallsBackToLiveModernWithAReason() {
        var themes = GtkTestThemes.unavailable(Appearance.LIGHT);
        assertThat(themes.style()).isEqualTo(ThemeStyle.MODERN);
        assertThat(themes.requestedStyle()).isEqualTo(ThemeStyle.GTK);
        assertThat(themes.fallbackReason()).hasValueSatisfying(reason ->
            assertThat(reason).contains("GTK is not available on this desktop").contains("modern"));
        assertThat(UIManager.getLookAndFeel()).isInstanceOf(com.formdev.flatlaf.FlatLightLaf.class);
        themes.selectAppearance(Appearance.DARK);
        assertThat(themes.current().chrome()).isEqualTo(BuiltinTheme.DARK);
    }

    @Test void realInstallerEitherInstallsGtkOrFallsBack() {
        var themes = new ThemeController(ThemeStyle.GTK, Appearance.DARK);
        assertThat(themes.requestedStyle()).isEqualTo(ThemeStyle.GTK);
        if (themes.style() == ThemeStyle.GTK) {
            assertThat(UIManager.getLookAndFeel().getClass().getName()).isEqualTo(GtkDefaults.LOOK_AND_FEEL);
            assertThat(themes.fallbackReason()).isEmpty();
        } else {
            assertThat(themes.style()).isEqualTo(ThemeStyle.MODERN);
            assertThat(themes.fallbackReason()).isPresent();
        }
    }

    @Test void fontReconfigurationKeepsTheGtkPalette() {
        var themes = GtkTestThemes.themes(GtkTestThemes.DARK);
        var palette = themes.current().palette();
        themes.configure(Appearance.DARK, new UiFontConfig("Serif", 18));
        assertThat(themes.current().palette()).isEqualTo(palette);
        assertThat(UIManager.getBoolean("Jasper.gtk")).isTrue();
        assertThat(UIManager.getFont("Label.font").getFamily()).isEqualTo("Serif");
    }

    @Test void otherStylesNeverReportAFallback() {
        assertThat(new ThemeController(ThemeStyle.RETRO, Appearance.DARK).fallbackReason()).isEmpty();
        assertThat(new ThemeController(ThemeStyle.MODERN, Appearance.DARK).requestedStyle()).isEqualTo(ThemeStyle.MODERN);
    }
}
```

- [ ] **Step 3: Run to verify they fail**

Run: `./gradlew :jasper-app:test --tests 'dev.jasper.app.appearance.Gtk*'`
Expected: compilation failure (`GtkDefaults`, `requestedStyle`, `fallbackReason` missing).

- [ ] **Step 4: Implement `GtkDefaults`** — `main/appearance/GtkDefaults.java`:

```java
package dev.jasper.app.appearance;

import java.awt.Color;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JTextArea;
import javax.swing.UIDefaults;
import javax.swing.UIManager;
import javax.swing.UnsupportedLookAndFeelException;

/** The JDK's GTK look and feel plus aliases for Jasper-owned paint, sampled from the installed desktop theme. */
final class GtkDefaults {
    static final String LOOK_AND_FEEL = "com.sun.java.swing.plaf.gtk.GTKLookAndFeel";
    static final String PANEL_BACKGROUND = "panel.background";
    static final String LABEL_FOREGROUND = "label.foreground";
    static final String LIST_SELECTION_BACKGROUND = "list.selectionBackground";
    static final String LIST_SELECTION_FOREGROUND = "list.selectionForeground";
    static final String TEXT_BACKGROUND = "text.background";
    static final String TEXT_FOREGROUND = "text.foreground";
    static final String TEXT_CARET = "text.caret";
    static final String TEXT_SELECTION_BACKGROUND = "text.selectionBackground";
    // GTK's synth delegates publish few colour defaults; these system colours stand behind the samples.
    private static final Map<String, String> SYSTEM = Map.of(PANEL_BACKGROUND, "control", LABEL_FOREGROUND, "controlText",
        LIST_SELECTION_BACKGROUND, "textHighlight", LIST_SELECTION_FOREGROUND, "textHighlightText",
        TEXT_BACKGROUND, "text", TEXT_FOREGROUND, "textText", TEXT_CARET, "textText", TEXT_SELECTION_BACKGROUND, "textHighlight");
    private GtkDefaults() {}

    /** Installs GTK or throws; the class is Linux-only and module-internal, so it is named, never referenced. */
    static boolean install() {
        try { UIManager.setLookAndFeel(LOOK_AND_FEEL); }
        catch (ClassNotFoundException failure) { throw new IllegalStateException("this Java runtime has no GTK look and feel", failure); }
        catch (UnsupportedLookAndFeelException failure) { throw new IllegalStateException("GTK is not available on this desktop", failure); }
        catch (ReflectiveOperationException | LinkageError | InternalError failure) {
            throw new IllegalStateException("the GTK look and feel could not be loaded", failure);
        }
        decorate(UIManager.getLookAndFeelDefaults(), sample());
        return true;
    }

    /** Colours the installed delegates gave throwaway components, with the LAF's system colours behind them. */
    static Function<String, Color> sample() {
        var panel = new JPanel(); var label = new JLabel(); var list = new JList<String>(); var text = new JTextArea();
        Map<String, Color> sampled = new HashMap<>();
        sampled.put(PANEL_BACKGROUND, panel.getBackground());
        sampled.put(LABEL_FOREGROUND, label.getForeground());
        sampled.put(LIST_SELECTION_BACKGROUND, list.getSelectionBackground());
        sampled.put(LIST_SELECTION_FOREGROUND, list.getSelectionForeground());
        sampled.put(TEXT_BACKGROUND, text.getBackground());
        sampled.put(TEXT_FOREGROUND, text.getForeground());
        sampled.put(TEXT_CARET, text.getCaretColor());
        sampled.put(TEXT_SELECTION_BACKGROUND, text.getSelectionColor());
        return key -> {
            Color color = sampled.get(key);
            return color != null ? color : UIManager.getColor(SYSTEM.get(key));
        };
    }

    static void decorate(UIDefaults defaults, Function<String, Color> sample) {
        Color panel = orElse(sample.apply(PANEL_BACKGROUND), new Color(0xf6f5f4));
        boolean dark = GtkPalette.dark(panel);
        Color foreground = orElse(sample.apply(LABEL_FOREGROUND), dark ? Color.WHITE : Color.BLACK);
        Color muted = blend(foreground, panel, 0.55);
        Color border = blend(foreground, panel, 0.2);
        Color selection = orElse(sample.apply(LIST_SELECTION_BACKGROUND), new Color(0x3584e4));
        Color selectionForeground = orElse(sample.apply(LIST_SELECTION_FOREGROUND), Color.WHITE);
        defaults.put("Jasper.nativeChrome", true);
        defaults.put("Jasper.gtk", true);
        put(defaults, panel, "Jasper.titleBackground", "Jasper.paletteBackground", "Jasper.tabSelectedBackground");
        put(defaults, foreground, "Jasper.titleForeground", "Jasper.chromeForeground", "Jasper.tabSelectedForeground",
            "Jasper.paletteForeground");
        put(defaults, muted, "Jasper.titleInactiveForeground", "Jasper.mutedForeground", "Jasper.paletteMutedForeground");
        put(defaults, border, "Jasper.titleSeparator", "Jasper.splitDivider", "Component.borderColor", "Jasper.paletteBorder");
        put(defaults, selection, "Jasper.paletteAccent", "Component.focusedBorderColor", "Jasper.paletteSelectionBackground");
        put(defaults, selectionForeground, "Jasper.paletteSelectionForeground");
        // Jasper Light / Jasper Dark status colours, so status text reads on either theme.
        put(defaults, new Color(dark ? 0xa8c58d : 0x50a14f), "Jasper.runningForeground");
        put(defaults, new Color(dark ? 0xa8c58d : 0x28752a), "Jasper.configSuccessForeground");
        put(defaults, new Color(dark ? 0xe5c07b : 0x805900), "Jasper.configWarningForeground");
        put(defaults, new Color(dark ? 0xff858d : 0xb42332), "Jasper.configErrorForeground");
        put(defaults, new Color(dark ? 0xc75450 : 0xdb5860), "Actions.Red");
        put(defaults, new Color(dark ? 0xf0a732 : 0xeda200), "Actions.Yellow");
        put(defaults, new Color(dark ? 0x499c54 : 0x59a869), "Actions.Green");
        putIfPresent(defaults, GtkPalette.BACKGROUND, sample.apply(TEXT_BACKGROUND));
        putIfPresent(defaults, GtkPalette.FOREGROUND, sample.apply(TEXT_FOREGROUND));
        putIfPresent(defaults, GtkPalette.CARET, sample.apply(TEXT_CARET));
        putIfPresent(defaults, GtkPalette.SELECTION, sample.apply(TEXT_SELECTION_BACKGROUND));
    }

    private static void put(UIDefaults defaults, Color color, String... keys) {
        // Plain colours: a UIResource would be replaced by the delegate on the next updateUI.
        Color plain = new Color(color.getRed(), color.getGreen(), color.getBlue());
        for (String key : keys) defaults.put(key, plain);
    }
    private static void putIfPresent(UIDefaults defaults, String key, Color color) { if (color != null) put(defaults, color, key); }
    private static Color orElse(Color color, Color fallback) { return color != null ? color : fallback; }
    private static Color blend(Color a, Color b, double weight) {
        return new Color((int) Math.round(a.getRed() * weight + b.getRed() * (1 - weight)),
            (int) Math.round(a.getGreen() * weight + b.getGreen() * (1 - weight)),
            (int) Math.round(a.getBlue() * weight + b.getBlue() * (1 - weight)));
    }
}
```

- [ ] **Step 5: Implement the controller changes** in `main/appearance/ThemeController.java`:

Fields — replace `private final ThemeStyle style;` with:
```java
    private static final System.Logger LOG = System.getLogger(ThemeController.class.getName());
    private final ThemeStyle requestedStyle;
    private ThemeStyle style;
    private String fallbackReason;
    private Palette gtkPalette = BuiltinTheme.GTK.palette();
```
and add `import dev.jasper.terminal.config.Palette;` and `import java.util.Optional;`.

Constructor — replace `this.style = Objects.requireNonNull(style);` with:
```java
        this.requestedStyle = Objects.requireNonNull(style);
        this.style = style;
```
and replace the constructor's `installOrThrow(resolve(state).chrome());` with:
```java
        try { installChrome(resolve(state).chrome()); }
        catch (InstallationFailure failure) {
            if (style != ThemeStyle.GTK) throw failure;
            this.style = ThemeStyle.MODERN;
            fallbackReason = "GTK appearance is unavailable (" + reason(failure) + "); using modern.";
            LOG.log(System.Logger.Level.WARNING, fallbackReason, failure);
            installChrome(resolve(state).chrome());
        }
```

Accessors and resolution — replace `style()`, `resolve`, `choice` with:
```java
    /** The installed style: the requested one, or modern after a GTK fallback. */
    public ThemeStyle style() { requireEdt(); return style; }
    /** The configured style, which a restart would try again. */
    public ThemeStyle requestedStyle() { requireEdt(); return requestedStyle; }
    /** Why the requested style could not be installed, when it could not. */
    public Optional<String> fallbackReason() { requireEdt(); return Optional.ofNullable(fallbackReason); }
    private ResolvedTheme resolve(ThemeState candidate) {
        return switch (style) {
            case RETRO -> new ResolvedTheme(BuiltinTheme.RETRO, BuiltinTheme.RETRO.palette());
            case GTK -> new ResolvedTheme(BuiltinTheme.GTK, gtkPalette);
            case MODERN -> candidate.resolve();
        };
    }
    public ResolvedTheme current() { requireEdt(); return resolve(state); }
    public Appearance choice() {
        requireEdt(); return style == ThemeStyle.MODERN ? state.choice() : resolve(state).appearance();
    }
```

In `apply`, replace `installOrThrow(next.chrome());` with `installChrome(next.chrome());`.

Add beside `installOrThrow`:
```java
    private void installChrome(BuiltinTheme theme) {
        installOrThrow(theme);
        if (theme == BuiltinTheme.GTK) gtkPalette = GtkPalette.from(UIManager::getColor);
    }

    private static String reason(Throwable failure) {
        Throwable cause = failure;
        while (cause.getCause() != null) cause = cause.getCause();
        String message = cause.getMessage();
        return message == null || message.isBlank() ? cause.getClass().getSimpleName() : message;
    }
```

In `install(BuiltinTheme theme)` add as the second line: `if (theme == BuiltinTheme.GTK) return GtkDefaults.install();`.

`installFontDefaults` keeps its `if (style == ThemeStyle.RETRO) return;` — GTK uses the UIManager form-font keys, which `GTKStyle` consults per region before its theme font.

- [ ] **Step 6: Run tests**

Run: `./gradlew :jasper-app:test --tests 'dev.jasper.app.appearance.*'`
Expected: PASS. On macOS `macOsAndWindowsNeverInstallGtk` runs and passes.

- [ ] **Step 7: Commit** — `git add -A jasper-app && git commit -m "feat(appearance): install the GTK look and feel with a modern fallback"` (trailer).

---

### Task 4: Report the fallback in the config status

**Files:**
- Modify: `main/application/ConfigurationController.java:28-36`
- Test: `test/workspace/ConfigurationControllerTest.java`

**Interfaces:**
- Consumes: `ThemeController.requestedStyle()`, `fallbackReason()` (Task 3); `GtkTestThemes.unavailable` (Task 3).

- [ ] **Step 1: Write the failing test** — append to `ConfigurationControllerTest`:

```java
@Test void unavailableGtkReportsItsFallbackWithoutAPendingRestart() throws Exception {
    Files.writeString(directory.resolve("config.toml"), "ui.theme.style='gtk'\n");
    service = new ConfigService(directory.resolve("config.toml"), false);
    edt(() -> {
        themes = dev.jasper.app.appearance.GtkTestThemes.unavailable(dev.jasper.app.config.Appearance.DARK);
        controller = new ConfigurationTestSupport(themes, service);
        assertThat(themes.style()).isEqualTo(dev.jasper.app.config.ThemeStyle.MODERN);
        assertThat(controller.shown().diagnostics()).filteredOn(d -> d.key().equals("ui.theme.style"))
            .singleElement().satisfies(d -> assertThat(d.message()).contains("GTK is not available").doesNotContain("Restart"));
    });
    reload("ui.theme.style='modern'\n");
    edt(() -> assertThat(controller.shown().diagnostics()).filteredOn(d -> d.key().equals("ui.theme.style"))
        .extracting(d -> d.message()).anyMatch(message -> message.contains("Restart Jasper")));
}
```

- [ ] **Step 2: Run to verify it fails**

Run: `./gradlew :jasper-app:test --tests 'dev.jasper.app.workspace.ConfigurationControllerTest.unavailableGtkReportsItsFallbackWithoutAPendingRestart'`
Expected: FAIL — the diagnostic reads "Restart Jasper to apply ui.theme.style." (snapshot GTK ≠ effective MODERN).

- [ ] **Step 3: Implement** — in `ConfigurationController.shown()` replace the `if (state.snapshot().style() != themes.style())` block with:

```java
        if (state.snapshot().style() != themes.requestedStyle())
            merged.add(new dev.jasper.app.config.ConfigDiagnostic(
                dev.jasper.app.config.ConfigDiagnostic.Severity.WARNING,
                state.file(), 0, 0, "ui.theme.style", "Restart Jasper to apply ui.theme.style."));
        themes.fallbackReason().ifPresent(reason -> merged.add(new dev.jasper.app.config.ConfigDiagnostic(
            dev.jasper.app.config.ConfigDiagnostic.Severity.WARNING, state.file(), 0, 0, "ui.theme.style", reason)));
```

- [ ] **Step 4: Run tests**

Run: `./gradlew :jasper-app:test --tests 'dev.jasper.app.workspace.ConfigurationControllerTest'`
Expected: PASS (existing retro restart tests unchanged: for retro and modern, requested == effective).

- [ ] **Step 5: Commit** — `git commit -am "feat(config): report an unavailable GTK style in the status bar"` (trailer).

---

### Task 5: Native chrome for GTK

**Files:**
- Modify: `main/platform/SwingAppearance.java`, `main/appearance/MetalDefaults.java:25`, `main/workspace/WindowContent.java`, `main/workspace/WindowChrome.java`, `main/workspace/WindowCommands.java:108,110`, `main/workspace/WindowCommandPalette.java:185`, `main/workspace/WindowRail.java:51`, `main/workspace/WindowStatusBar.java:116,121,126,149`, `main/workspace/TerminalDeck.java:12,16`, `main/palette/CommandPalette.java:321-403`, `test/workspace/RetroChromeTest.java:49-58`
- Create: `test/workspace/GtkChromeTest.java`

**Interfaces:**
- Consumes: `GtkTestThemes.themes`, `GtkTestThemes.LIGHT` (Task 3).
- Produces: `SwingAppearance.nativeChrome()`, `SwingAppearance.gtk()` (public static boolean); `WindowContent.nativeChrome()`, `WindowContent.gtk()` (package-private boolean). `WindowContent.retro()` now means effective `RETRO` only.

- [ ] **Step 1: Write the failing test** — `test/workspace/GtkChromeTest.java`:

```java
package dev.jasper.app.workspace;

import dev.jasper.app.appearance.GtkTestThemes;
import dev.jasper.app.appearance.ThemeController;
import dev.jasper.app.commands.ActionId;
import java.awt.Color;
import java.util.ArrayList;
import java.util.List;
import javax.swing.JButton;
import javax.swing.JMenu;
import javax.swing.JMenuItem;
import javax.swing.JTabbedPane;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import static dev.jasper.app.workspace.DesktopTestSupport.*;
import static org.assertj.core.api.Assertions.*;

class GtkChromeTest {
    @AfterEach void cleanup() throws Exception { closeOwners(); edt(() -> new ThemeController()); }

    @Test void gtkBuildsNativeChromeWithoutMetalStyling() throws Exception {
        edt(() -> {
            var owner = content(launcher(new java.util.ArrayDeque<>()), GtkTestThemes.themes(GtkTestThemes.LIGHT));
            assertThat(owner.nativeChrome()).isTrue();
            assertThat(owner.gtk()).isTrue();
            assertThat(owner.retro()).isFalse();
            assertThat(owner.windowTabs()).isNull();
            assertThat(owner.toolbar()).isInstanceOf(RetroToolbar.class);
            assertThat(owner.tabStrip().getTabLayoutPolicy()).isEqualTo(JTabbedPane.SCROLL_TAB_LAYOUT);
            assertThat(owner.menuBar().getBorder()).isNotInstanceOf(javax.swing.border.MatteBorder.class);
            var actions = java.util.Arrays.stream(owner.toolbar().getComponents()).filter(JButton.class::isInstance)
                .map(c -> ((JButton) c).getAction()).toList();
            assertThat(actions).contains(owner.action(ActionId.OPEN_SETTINGS), owner.action(ActionId.QUIT));
            assertThat(owner.windowCommands().view("view.appearance.dark").isEnabled()).isFalse();
            assertThat(owner.windowCommands().view("view.tab_height").isEnabled()).isFalse();
            assertThat(menuTexts(owner.menuBar())).contains("GTK follows the desktop theme; change style in Settings and restart.");
            assertThat(owner.theme().palette().background()).isEqualTo(Color.WHITE);
            assertThat(owner.status().getBackground()).isEqualTo(javax.swing.UIManager.getColor("Panel.background"));
        });
    }

    private static List<String> menuTexts(javax.swing.JMenuBar bar) {
        var texts = new ArrayList<String>();
        for (int i = 0; i < bar.getMenuCount(); i++) collect(bar.getMenu(i), texts);
        return texts;
    }
    private static void collect(JMenu menu, List<String> texts) {
        for (var child : menu.getMenuComponents()) {
            if (child instanceof JMenu sub) collect(sub, texts);
            else if (child instanceof JMenuItem item) texts.add(item.getText());
        }
    }
}
```

- [ ] **Step 2: Run to verify it fails**

Run: `./gradlew :jasper-app:test --tests 'dev.jasper.app.workspace.GtkChromeTest'`
Expected: compilation failure (`nativeChrome()` / `gtk()` missing on `WindowContent`).

- [ ] **Step 3: Implement predicates**

`SwingAppearance.java`:
```java
package dev.jasper.app.platform;

import javax.swing.UIManager;

/** Presentation predicates supplied by the installed app LAF; contains no configuration state. */
public final class SwingAppearance {
    private SwingAppearance() {}
    /** Metal-specific paint and typography. */
    public static boolean retro() { return UIManager.getBoolean("Jasper.retro"); }
    /** Retro and GTK: standard Swing tabs, toolbar and title instead of Jasper's custom-painted chrome. */
    public static boolean nativeChrome() { return UIManager.getBoolean("Jasper.nativeChrome"); }
    /** Chrome and icons follow the desktop's GTK and icon themes. */
    public static boolean gtk() { return UIManager.getBoolean("Jasper.gtk"); }
}
```

`MetalDefaults.install()` — after `defaults.put("Jasper.retro", true);` add `defaults.put("Jasper.nativeChrome", true);`.

`WindowContent.java` — replace `boolean retro() { ... }` with:
```java
    boolean retro() { return themes.style() == dev.jasper.app.config.ThemeStyle.RETRO; }
    boolean gtk() { return themes.style() == dev.jasper.app.config.ThemeStyle.GTK; }
    /** Retro and GTK build standard Swing tabs, toolbar and title; modern builds Jasper's own. */
    boolean nativeChrome() { return themes.style() != dev.jasper.app.config.ThemeStyle.MODERN; }
```
and in the same file change `retro()` → `nativeChrome()` at the `windowTabs =` / `retroTabs =` lines and the `tabs.setBackground(retro() ? ...` line.

- [ ] **Step 4: Move structural call sites** (rule: structure → native chrome, Metal paint → retro):

`WindowChrome.java`:
- `toolbar = owner.retro() ? new RetroToolbar() : new ReferenceToolbar();` → `owner.nativeChrome()`; same for `if (!owner.retro()) toolbar.setBorder(...)`, the trailing `addButton(ActionId.OPEN_SETTINGS...)` block, `addToolbarSeparator`, both `if (owner.retro()) { RetroToolbar.styleButton(button); } else {...}` blocks, `if (!owner.retro()) button.setFont(...)`, `toolbar.setBackground(owner.retro() ? ...)`, `if (owner.retro()) RetroToolbar.styleButton(button); else ...` in `refreshTheme`, and `status.setBackground(owner.retro() ? ...)`.
- `ReferenceButton.getPreferredSize` / `paintComponent`: `dev.jasper.app.platform.SwingAppearance.retro()` → `dev.jasper.app.platform.SwingAppearance.nativeChrome()`.
- Keep `if (owner.retro()) { menuBar.setBackground(...); menuBar.setBorder(MatteBorder...) }` as retro-only (Metal).
- Appearance note:
```java
        if (owner.nativeChrome()) {
            var note = new JMenuItem(owner.gtk() ? "GTK follows the desktop theme; change style in Settings and restart."
                : "Retro uses light Metal; change style in Settings and restart.");
            note.setEnabled(false); appearance.addSeparator(); appearance.add(note);
        }
```

`WindowCommands.java:108,110` — `!owner.retro()` → `!owner.nativeChrome()`.
`WindowCommandPalette.java:185` — `if (owner.retro()) return;` → `if (owner.nativeChrome()) return;`.
`WindowRail.java:51`, `WindowStatusBar.java:116,121,126,149`, `TerminalDeck.java:12,16` — `SwingAppearance.retro()` → `SwingAppearance.nativeChrome()`.

`CommandPalette.java` — replace the two helpers at line 321-322 with:
```java
    private static boolean nativeChrome() { return dev.jasper.app.platform.SwingAppearance.nativeChrome(); }
    private static int radius(int modern) { return nativeChrome() ? 0 : modern; }
    /** GTK's synth delegates publish few defaults; a fresh control carries the installed look instead. */
    private static javax.swing.border.Border fieldBorder() {
        var border = UIManager.getBorder("TextField.border");
        return border != null ? border : new JTextField().getBorder();
    }
    private static java.awt.Font font(String key, java.util.function.Supplier<? extends JComponent> sample) {
        var font = UIManager.getFont(key);
        return font != null ? font : sample.get().getFont();
    }
```
then rename every `retro()` call in the file to `nativeChrome()`, and inside those branches replace `UIManager.getBorder("TextField.border")` → `fieldBorder()`, `UIManager.getFont("TextField.font")` → `font("TextField.font", JTextField::new)`, `UIManager.getFont("Label.font")` → `font("Label.font", JLabel::new)`. Add `javax.swing.JComponent`/`JTextField`/`JLabel` imports if missing.

`MacTitleBar.java` stays on `retro()` (macOS only; GTK is never effective there).

`RetroChromeTest.retroTrailingControlsUseSettingsAndQuitActions` — replace `if (style != ThemeStyle.RETRO) {` with `if (!owner.nativeChrome()) {` so a real GTK install on a Linux display is accepted.

- [ ] **Step 5: Run tests**

Run: `./gradlew :jasper-app:test --tests 'dev.jasper.app.workspace.*' --tests 'dev.jasper.app.palette.*' --tests 'dev.jasper.app.appearance.*' --tests 'dev.jasper.app.windows.*'`
Expected: PASS, including every existing retro test.

- [ ] **Step 6: Commit** — `git add -A jasper-app && git commit -m "feat(workspace): give GTK the native chrome retro uses"` (trailer).

---

### Task 6: Freedesktop icon theme lookup

**Files:**
- Create: `main/platform/IconThemeName.java`, `main/platform/FreedesktopNames.java`, `main/platform/FreedesktopIcons.java`
- Test: `test/platform/IconThemeNameTest.java`, `test/platform/FreedesktopNamesTest.java`, `test/platform/FreedesktopIconsTest.java`

**Interfaces:**
- Produces:
  - `static String IconThemeName.find()`; `static String IconThemeName.find(Function<String, Object> desktop, Path configHome, Supplier<String> gsettings)`; `static boolean IconThemeName.valid(String)`; `static String IconThemeName.command(List<String>, Duration)`.
  - `static List<String> FreedesktopNames.of(String jasperName)` (throws `IllegalArgumentException` for unknown names); `static Set<String> FreedesktopNames.NAMES`.
  - `FreedesktopIcons(String theme, List<Path> bases, List<Path> pixmaps)`; `static FreedesktopIcons fromEnvironment(String theme, Map<String, String> env, Path home)`; `List<Path> bases()`; `List<String> chain()`; `Optional<Path> find(List<String> names, int size, int scale)`; `Optional<Image> image(List<String> names, int size, Color symbolic)`.

- [ ] **Step 1: Write the failing tests**

`test/platform/IconThemeNameTest.java`:
```java
package dev.jasper.app.platform;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;
import static org.assertj.core.api.Assertions.*;

class IconThemeNameTest {
    @TempDir Path config;

    @Test void xsettingsWinsOverFilesAndGsettings() throws Exception {
        ini("gtk-3.0", "gtk-icon-theme-name=Breeze");
        assertThat(IconThemeName.find(key -> key.equals("gnome.Net/IconThemeName") ? " Papirus " : null, config, () -> "'Yaru'"))
            .isEqualTo("Papirus");
    }

    @Test void settingsFilesComeNextGtk3BeforeGtk4() throws Exception {
        ini("gtk-4.0", "[Settings]\ngtk-icon-theme-name = \"Four\"");
        assertThat(IconThemeName.find(key -> null, config, () -> null)).isEqualTo("Four");
        ini("gtk-3.0", "[Settings]\n# comment\ngtk-icon-theme-name = Three");
        assertThat(IconThemeName.find(key -> "", config, () -> null)).isEqualTo("Three");
    }

    @Test void gsettingsOutputIsUnquoted() {
        assertThat(IconThemeName.find(key -> null, config, () -> "'Adwaita'\n")).isEqualTo("Adwaita");
    }

    @Test void unusableNamesAreIgnored() throws Exception {
        ini("gtk-3.0", "gtk-icon-theme-name=../../etc");
        assertThat(IconThemeName.find(key -> ".hidden", config, () -> "'a/b'")).isNull();
        assertThat(IconThemeName.find(key -> 7, config, () -> "  ")).isNull();
        assertThat(IconThemeName.valid("Adwaita")).isTrue();
        for (String bad : new String[]{null, "", " ", ".x", "a/b", "a\\b", "a\0b"}) assertThat(IconThemeName.valid(bad)).as(bad).isFalse();
    }

    @Test @DisabledOnOs(OS.WINDOWS) void commandsReturnOutputAndGiveUpOnTimeoutOrAbsence() {
        assertThat(IconThemeName.command(List.of("sh", "-c", "echo \"'Papirus'\""), Duration.ofSeconds(5))).isEqualTo("'Papirus'\n");
        assertThat(IconThemeName.command(List.of("sh", "-c", "sleep 5"), Duration.ofMillis(200))).isNull();
        assertThat(IconThemeName.command(List.of("sh", "-c", "exit 3"), Duration.ofSeconds(5))).isNull();
        assertThat(IconThemeName.command(List.of("jasper-no-such-binary"), Duration.ofSeconds(1))).isNull();
    }

    private void ini(String version, String text) throws Exception {
        Files.createDirectories(config.resolve(version));
        Files.writeString(config.resolve(version).resolve("settings.ini"), text);
    }
}
```

`test/platform/FreedesktopNamesTest.java`:
```java
package dev.jasper.app.platform;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class FreedesktopNamesTest {
    @Test void everyBundledNameHasFreedesktopCandidates() {
        assertThat(FreedesktopNames.NAMES).containsAll(GnomeIcons.NAMES).containsAll(OldGnomeCatalog.NAMES);
        for (String name : FreedesktopNames.NAMES) assertThat(FreedesktopNames.of(name)).as(name).isNotEmpty();
        assertThat(FreedesktopNames.of("SAVE")).containsExactly("document-save");
        assertThat(FreedesktopNames.of("square-plus")).startsWith("tab-new");
        assertThatIllegalArgumentException().isThrownBy(() -> FreedesktopNames.of("nope"));
    }
}
```

`test/platform/FreedesktopIconsTest.java`:
```java
package dev.jasper.app.platform;

import java.awt.Color;
import java.awt.Image;
import java.awt.image.BufferedImage;
import java.awt.image.MultiResolutionImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.assertj.core.api.Assertions.*;

class FreedesktopIconsTest {
    @TempDir Path root;
    Path base() { return root.resolve("icons"); }

    @Test void chainFollowsInheritsDepthFirstIgnoringCyclesThenHicolor() throws Exception {
        theme("Child", "Parent,Other", "16x16/actions", "[16x16/actions]\nSize=16\nType=Fixed");
        theme("Parent", "Child", "16x16/actions", "[16x16/actions]\nSize=16\nType=Fixed");
        theme("Other", "", "16x16/actions", "[16x16/actions]\nSize=16\nType=Fixed");
        theme("hicolor", "", "16x16/apps", "[16x16/apps]\nSize=16\nType=Threshold");
        assertThat(icons("Child").chain()).containsExactly("Child", "Parent", "Other", "hicolor");
        assertThat(icons(null).chain()).containsExactly("hicolor");
        assertThat(icons("../Child").chain()).containsExactly("hicolor");
    }

    @Test void findsInThemeThenAncestorsThenHicolorThenPixmaps() throws Exception {
        theme("Child", "Parent", "16x16/actions", "[16x16/actions]\nSize=16\nType=Fixed");
        theme("Parent", "", "16x16/actions", "[16x16/actions]\nSize=16\nType=Fixed");
        theme("hicolor", "", "16x16/apps", "[16x16/apps]\nSize=16\nType=Threshold");
        png("Child/16x16/actions/edit-copy.png", 16, Color.RED);
        png("Parent/16x16/actions/edit-paste.png", 16, Color.GREEN);
        png("Parent/16x16/actions/edit-copy.png", 16, Color.BLUE);
        png("hicolor/16x16/apps/jasper.png", 16, Color.BLACK);
        Path pixmaps = Files.createDirectories(root.resolve("pixmaps"));
        write(pixmaps.resolve("folder.png"), 16, Color.WHITE);
        var icons = new FreedesktopIcons("Child", List.of(base()), List.of(pixmaps));
        assertThat(icons.find(List.of("edit-copy"), 16, 1)).hasValue(base().resolve("Child/16x16/actions/edit-copy.png"));
        assertThat(icons.find(List.of("edit-paste"), 16, 1)).hasValue(base().resolve("Parent/16x16/actions/edit-paste.png"));
        assertThat(icons.find(List.of("jasper"), 16, 1)).hasValue(base().resolve("hicolor/16x16/apps/jasper.png"));
        assertThat(icons.find(List.of("folder"), 16, 1)).hasValue(pixmaps.resolve("folder.png"));
        assertThat(icons.find(List.of("missing"), 16, 1)).isEmpty();
        // Candidates are tried per theme: the child's second name beats the parent's first.
        assertThat(icons.find(List.of("edit-paste", "edit-copy"), 16, 1)).hasValue(base().resolve("Child/16x16/actions/edit-copy.png"));
    }

    @Test void directoryTypesAndClosestSizeFollowTheSpecification() throws Exception {
        theme("T", "", "16x16/a,32x32/a,24x24/t,scalable/a,16x16@2/a",
            "[16x16/a]\nSize=16\nType=Fixed\n[32x32/a]\nSize=32\nType=Fixed\n[24x24/t]\nSize=24\nType=Threshold\nThreshold=2\n"
                + "[scalable/a]\nSize=48\nType=Scalable\nMinSize=64\nMaxSize=512\n[16x16@2/a]\nSize=16\nScale=2\nType=Fixed");
        png("T/16x16/a/fixed.png", 16, Color.RED); png("T/32x32/a/fixed.png", 32, Color.RED);
        png("T/24x24/t/thresh.png", 24, Color.RED);
        png("T/scalable/a/vector.png", 64, Color.RED);
        png("T/16x16@2/a/hidpi.png", 32, Color.RED); png("T/16x16/a/hidpi.png", 16, Color.RED);
        var icons = icons("T");
        assertThat(icons.find(List.of("fixed"), 22, 1)).hasValue(base().resolve("T/16x16/a/fixed.png"));
        assertThat(icons.find(List.of("fixed"), 30, 1)).hasValue(base().resolve("T/32x32/a/fixed.png"));
        assertThat(icons.find(List.of("thresh"), 22, 1)).hasValue(base().resolve("T/24x24/t/thresh.png"));
        assertThat(icons.find(List.of("vector"), 100, 1)).hasValue(base().resolve("T/scalable/a/vector.png"));
        assertThat(icons.find(List.of("hidpi"), 16, 2)).hasValue(base().resolve("T/16x16@2/a/hidpi.png"));
        assertThat(icons.find(List.of("hidpi"), 16, 1)).hasValue(base().resolve("T/16x16/a/hidpi.png"));
    }

    @Test void malformedDirectoriesAndMissingIndexesAreSkipped() throws Exception {
        theme("T", "NoIndex", "bad/a,16x16/a", "[bad/a]\nSize=abc\n[16x16/a]\nSize=16\nType=Fixed");
        Files.createDirectories(base().resolve("NoIndex/16x16/a"));
        png("NoIndex/16x16/a/only.png", 16, Color.RED);
        png("T/bad/a/x.png", 16, Color.RED); png("T/16x16/a/x.png", 16, Color.RED);
        var icons = icons("T");
        assertThat(icons.chain()).containsExactly("T", "NoIndex", "hicolor");
        assertThat(icons.find(List.of("x"), 16, 1)).hasValue(base().resolve("T/16x16/a/x.png"));
        assertThat(icons.find(List.of("only"), 16, 1)).isEmpty();
    }

    @Test void imagesCarryA2xVariantAndSymbolicSvgsTakeTheGivenColor() throws Exception {
        theme("T", "", "16x16/a,32x32/a,scalable/a",
            "[16x16/a]\nSize=16\nType=Fixed\n[32x32/a]\nSize=32\nType=Fixed\n[scalable/a]\nSize=16\nType=Scalable\nMinSize=8\nMaxSize=512");
        png("T/16x16/a/pic.png", 16, Color.RED); png("T/32x32/a/pic.png", 32, Color.BLUE);
        Files.writeString(base().resolve("T/scalable/a/sym-symbolic.svg"),
            "<svg xmlns=\"http://www.w3.org/2000/svg\" width=\"16\" height=\"16\"><rect width=\"16\" height=\"16\" fill=\"#000000\"/></svg>");
        var icons = icons("T");
        Image pic = icons.image(List.of("pic"), 16, Color.BLACK).orElseThrow();
        assertThat(pic).isInstanceOf(MultiResolutionImage.class);
        var variant = (BufferedImage) ((MultiResolutionImage) pic).getResolutionVariant(32, 32);
        assertThat(variant.getWidth()).isEqualTo(32);
        assertThat(variant.getRGB(16, 16)).isEqualTo(Color.BLUE.getRGB());
        var symbolic = (BufferedImage) ((MultiResolutionImage) icons.image(List.of("sym-symbolic"), 16, new Color(0x123456)).orElseThrow())
            .getResolutionVariant(16, 16);
        assertThat(symbolic.getWidth()).isEqualTo(16);
        assertThat(symbolic.getRGB(8, 8) & 0xffffff).isEqualTo(0x123456);
    }

    @Test void unreadableFilesAreMissesEveryTime() throws Exception {
        theme("T", "", "16x16/a", "[16x16/a]\nSize=16\nType=Fixed");
        Files.writeString(base().resolve("T/16x16/a/broken.png"), "not a png");
        var icons = icons("T");
        assertThat(icons.image(List.of("broken"), 16, Color.BLACK)).isEmpty();
        assertThat(icons.image(List.of("broken"), 16, Color.BLACK)).isEmpty();
    }

    @Test void environmentSelectsStandardBaseDirectories() {
        Path home = Path.of("/home/u");
        assertThat(FreedesktopIcons.fromEnvironment(null, Map.of(), home).bases()).containsExactly(
            Path.of("/home/u/.local/share/icons"), Path.of("/home/u/.icons"),
            Path.of("/usr/local/share/icons"), Path.of("/usr/share/icons"));
        assertThat(FreedesktopIcons.fromEnvironment(null, Map.of("XDG_DATA_HOME", "/d", "XDG_DATA_DIRS", "/a::/b"), home).bases())
            .containsExactly(Path.of("/d/icons"), Path.of("/home/u/.icons"), Path.of("/a/icons"), Path.of("/b/icons"));
    }

    private FreedesktopIcons icons(String theme) { return new FreedesktopIcons(theme, List.of(base()), List.of()); }
    private void theme(String name, String inherits, String directories, String sections) throws Exception {
        Path dir = Files.createDirectories(base().resolve(name));
        for (String d : directories.split(",")) Files.createDirectories(dir.resolve(d));
        Files.writeString(dir.resolve("index.theme"), "[Icon Theme]\nName=" + name + "\nInherits=" + inherits
            + "\nDirectories=" + directories + "\n\n" + sections + "\n");
    }
    private void png(String relative, int size, Color color) throws Exception { write(base().resolve(relative), size, color); }
    private static void write(Path file, int size, Color color) throws Exception {
        Files.createDirectories(file.getParent());
        var image = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        var g = image.createGraphics();
        try { g.setColor(color); g.fillRect(0, 0, size, size); } finally { g.dispose(); }
        ImageIO.write(image, "png", file.toFile());
    }
}
```
(`16x16@2/a` is listed in `Directories=`; the parser also reads `ScaledDirectories=`.)

- [ ] **Step 2: Run to verify they fail**

Run: `./gradlew :jasper-app:test --tests 'dev.jasper.app.platform.IconThemeNameTest' --tests 'dev.jasper.app.platform.Freedesktop*'`
Expected: compilation failure.

- [ ] **Step 3: Implement `IconThemeName`** — `main/platform/IconThemeName.java`:

```java
package dev.jasper.app.platform;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.function.Supplier;

/** The desktop's icon theme name: XSETTINGS, then GTK settings files, then gsettings. */
final class IconThemeName {
    private IconThemeName() {}

    /** The live desktop's choice, or null when none is configured. */
    static String find() {
        Map<String, String> env = System.getenv();
        String configHome = env.get("XDG_CONFIG_HOME");
        Path config = configHome == null || configHome.isBlank()
            ? Path.of(System.getProperty("user.home"), ".config") : Path.of(configHome);
        return find(IconThemeName::desktopProperty, config, () -> command(
            List.of("gsettings", "get", "org.gnome.desktop.interface", "icon-theme"), Duration.ofSeconds(2)));
    }

    static String find(Function<String, Object> desktop, Path configHome, Supplier<String> gsettings) {
        if (desktop.apply("gnome.Net/IconThemeName") instanceof String name && valid(name.strip())) return name.strip();
        for (String version : List.of("gtk-3.0", "gtk-4.0")) {
            String name = settingsIni(configHome.resolve(version).resolve("settings.ini"));
            if (valid(name)) return name;
        }
        String name = unquote(gsettings.get());
        return valid(name) ? name : null;
    }

    /** A plain directory name: not blank, no separators, no leading dot, no NUL. */
    static boolean valid(String name) {
        return name != null && !name.isBlank() && !name.startsWith(".")
            && name.indexOf('/') < 0 && name.indexOf('\\') < 0 && name.indexOf('\0') < 0;
    }

    /** Standard output of a short command, or null when it is absent, fails or outlives the timeout. */
    static String command(List<String> command, Duration timeout) {
        Process process;
        try { process = new ProcessBuilder(command).redirectError(ProcessBuilder.Redirect.DISCARD).start(); }
        catch (IOException absent) { return null; }
        try {
            if (!process.waitFor(timeout.toMillis(), TimeUnit.MILLISECONDS)) { process.destroyForcibly(); return null; }
            if (process.exitValue() != 0) return null;
            return new String(process.getInputStream().readNBytes(4096), StandardCharsets.UTF_8);
        } catch (IOException failure) {
            return null;
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt(); process.destroyForcibly(); return null;
        }
    }

    private static String settingsIni(Path file) {
        if (!Files.isRegularFile(file)) return null;
        try {
            for (String line : Files.readAllLines(file, StandardCharsets.UTF_8)) {
                int equals = line.indexOf('=');
                if (equals > 0 && line.substring(0, equals).strip().equals("gtk-icon-theme-name"))
                    return unquote(line.substring(equals + 1));
            }
        } catch (IOException | RuntimeException unreadable) { return null; }
        return null;
    }

    private static String unquote(String value) {
        if (value == null) return null;
        String text = value.strip();
        if (text.length() >= 2 && (text.charAt(0) == '\'' || text.charAt(0) == '"') && text.charAt(text.length() - 1) == text.charAt(0))
            text = text.substring(1, text.length() - 1);
        return text.strip();
    }

    private static Object desktopProperty(String key) {
        try { return java.awt.Toolkit.getDefaultToolkit().getDesktopProperty(key); }
        catch (RuntimeException | java.awt.AWTError unavailable) { return null; }
    }
}
```

- [ ] **Step 4: Implement `FreedesktopNames`** — `main/platform/FreedesktopNames.java`:

```java
package dev.jasper.app.platform;

import java.util.List;
import java.util.Map;
import java.util.Set;

/** Freedesktop Icon Naming candidates for Jasper's application and semantic icon names; first found wins. */
final class FreedesktopNames {
    private static final Map<String, List<String>> CANDIDATES = Map.ofEntries(
        Map.entry("square-plus", List.of("tab-new", "list-add")),
        Map.entry("app-window", List.of("window-new")),
        Map.entry("columns-2", List.of("view-split-left-right", "view-dual", "view-column")),
        Map.entry("maximize", List.of("view-fullscreen", "window-maximize")),
        Map.entry("search", List.of("edit-find", "system-search")),
        Map.entry("settings", List.of("preferences-system", "emblem-system")),
        Map.entry("refresh", List.of("view-refresh")),
        Map.entry("command", List.of("system-run", "utilities-terminal")),
        Map.entry("history", List.of("document-open-recent")),
        Map.entry("bookmark", List.of("bookmark-new", "user-bookmarks")),
        Map.entry("close", List.of("window-close")),
        Map.entry("exit", List.of("application-exit", "system-log-out")),
        Map.entry("LOCK", List.of("changes-prevent", "system-lock-screen")),
        Map.entry("UNLOCK", List.of("changes-allow")),
        Map.entry("KEY", List.of("dialog-password", "channel-secure")),
        Map.entry("FOLDER", List.of("folder")),
        Map.entry("SAVE", List.of("document-save")),
        Map.entry("SEARCH", List.of("edit-find", "system-search")),
        Map.entry("HISTORY", List.of("document-open-recent")),
        Map.entry("BOOKMARK", List.of("bookmark-new", "user-bookmarks")),
        Map.entry("ADD", List.of("list-add")),
        Map.entry("REMOVE", List.of("list-remove")),
        Map.entry("DELETE", List.of("edit-delete")),
        Map.entry("COPY", List.of("edit-copy")),
        Map.entry("PASTE", List.of("edit-paste")),
        Map.entry("REFRESH", List.of("view-refresh")),
        Map.entry("SETTINGS", List.of("preferences-system", "emblem-system")),
        Map.entry("EXECUTE", List.of("system-run", "media-playback-start")),
        Map.entry("CONNECT", List.of("network-connect", "network-transmit-receive")),
        Map.entry("DISCONNECT", List.of("network-disconnect", "network-offline")),
        Map.entry("NETWORK", List.of("network-workgroup", "network-server", "network-wired")),
        Map.entry("INFO", List.of("dialog-information")),
        Map.entry("HELP", List.of("help-browser", "help-contents")),
        Map.entry("CLOSE", List.of("window-close")));
    static final Set<String> NAMES = CANDIDATES.keySet();
    private FreedesktopNames() {}

    static List<String> of(String name) {
        var candidates = name == null ? null : CANDIDATES.get(name);
        if (candidates == null) throw new IllegalArgumentException("No freedesktop mapping for icon: " + name);
        return candidates;
    }
}
```

- [ ] **Step 5: Implement `FreedesktopIcons`** — `main/platform/FreedesktopIcons.java`:

```java
package dev.jasper.app.platform;

import com.formdev.flatlaf.extras.FlatSVGIcon;
import java.awt.Color;
import java.awt.Image;
import java.awt.RenderingHints;
import java.awt.image.BaseMultiResolutionImage;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import javax.imageio.ImageIO;

/**
 * Freedesktop Icon Theme Specification lookup over local directories: the theme, its Inherits chain,
 * then hicolor, then unthemed pixmaps. Reads files only; no native code or network.
 */
final class FreedesktopIcons {
    private static final System.Logger LOG = System.getLogger(FreedesktopIcons.class.getName());
    private static final List<String> EXTENSIONS = List.of("png", "svg");
    private final String theme;
    private final List<Path> bases;
    private final List<Path> pixmaps;
    private final Map<String, Optional<Theme>> themes = new ConcurrentHashMap<>();
    private final Map<String, Optional<Path>> files = new ConcurrentHashMap<>();
    private final Map<String, Optional<Image>> images = new ConcurrentHashMap<>();
    private final Set<Path> broken = ConcurrentHashMap.newKeySet();

    FreedesktopIcons(String theme, List<Path> bases, List<Path> pixmaps) {
        this.theme = IconThemeName.valid(theme) ? theme : null;
        this.bases = List.copyOf(bases);
        this.pixmaps = List.copyOf(pixmaps);
    }

    /** The specification's base directories, from the XDG environment. */
    static FreedesktopIcons fromEnvironment(String theme, Map<String, String> env, Path home) {
        var bases = new ArrayList<Path>();
        String dataHome = env.get("XDG_DATA_HOME");
        bases.add((dataHome == null || dataHome.isBlank() ? home.resolve(".local/share") : Path.of(dataHome)).resolve("icons"));
        bases.add(home.resolve(".icons"));
        String dataDirs = env.get("XDG_DATA_DIRS");
        for (String dir : (dataDirs == null || dataDirs.isBlank() ? "/usr/local/share:/usr/share" : dataDirs).split(":"))
            if (!dir.isBlank()) bases.add(Path.of(dir).resolve("icons"));
        return new FreedesktopIcons(theme, bases, List.of(Path.of("/usr/share/pixmaps")));
    }

    List<Path> bases() { return bases; }

    /** Theme names in search order: the theme, its ancestors depth-first, then hicolor. */
    List<String> chain() {
        var order = new LinkedHashSet<String>();
        if (theme != null) visit(theme, order);
        order.add("hicolor");
        return List.copyOf(order);
    }

    private void visit(String name, Set<String> order) {
        if (!order.add(name)) return;
        theme(name).ifPresent(found -> found.parents().forEach(parent -> visit(parent, order)));
    }

    /** The best file for the first candidate a theme in the chain provides, else an unthemed pixmap. */
    Optional<Path> find(List<String> names, int size, int scale) {
        return files.computeIfAbsent(String.join(",", names) + "@" + size + "x" + scale, key -> {
            for (String name : chain()) {
                var found = theme(name);
                if (found.isEmpty()) continue;
                for (String icon : names) {
                    var file = found.get().find(icon, size, scale);
                    if (file.isPresent()) return file;
                }
            }
            for (String icon : names) for (Path directory : pixmaps) for (String extension : EXTENSIONS) {
                Path file = directory.resolve(icon + "." + extension);
                if (Files.isRegularFile(file)) return Optional.of(file);
            }
            return Optional.empty();
        });
    }

    /** A size-by-size image with a 2x variant when available; symbolic icons take the given colour. */
    Optional<Image> image(List<String> names, int size, Color symbolic) {
        return images.computeIfAbsent(String.join(",", names) + "@" + size + "#" + Integer.toHexString(symbolic.getRGB()), key -> {
            BufferedImage base = find(names, size, 1).map(file -> render(file, size, symbolic)).orElse(null);
            if (base == null) return Optional.empty();
            BufferedImage large = find(names, size, 2).map(file -> render(file, size * 2, symbolic)).orElse(null);
            return Optional.<Image>of(new BaseMultiResolutionImage(large == null ? new Image[]{base} : new Image[]{base, large}));
        });
    }

    private Optional<Theme> theme(String name) { return themes.computeIfAbsent(name, key -> Theme.load(key, bases)); }

    private BufferedImage render(Path file, int pixels, Color symbolic) {
        if (broken.contains(file)) return null;
        try {
            BufferedImage source = file.getFileName().toString().endsWith(".svg") ? svg(file, pixels, symbolic) : ImageIO.read(file.toFile());
            if (source == null || source.getWidth() <= 0 || source.getHeight() <= 0) throw new IOException("unreadable image");
            return source.getWidth() == pixels && source.getHeight() == pixels ? source : scaled(source, pixels);
        } catch (IOException | RuntimeException failure) {
            if (broken.add(file)) LOG.log(System.Logger.Level.WARNING, "Desktop icon " + file + " could not be read; using bundled artwork", failure);
            return null;
        }
    }

    private static BufferedImage svg(Path file, int pixels, Color symbolic) throws IOException {
        var icon = new FlatSVGIcon(file.toUri().toURL()).derive(pixels, pixels);
        if (!icon.hasFound()) throw new IOException("unreadable SVG");
        if (file.getFileName().toString().contains("-symbolic"))
            icon.setColorFilter(new FlatSVGIcon.ColorFilter(color ->
                new Color(symbolic.getRed(), symbolic.getGreen(), symbolic.getBlue(), color.getAlpha())));
        var image = new BufferedImage(icon.getIconWidth(), icon.getIconHeight(), BufferedImage.TYPE_INT_ARGB);
        var graphics = image.createGraphics();
        try { icon.paintIcon(new javax.swing.JLabel(), graphics, 0, 0); } finally { graphics.dispose(); }
        return image;
    }

    private static BufferedImage scaled(BufferedImage source, int pixels) {
        var image = new BufferedImage(pixels, pixels, BufferedImage.TYPE_INT_ARGB);
        var graphics = image.createGraphics();
        try {
            graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
            graphics.drawImage(source, 0, 0, pixels, pixels, null);
        } finally { graphics.dispose(); }
        return image;
    }

    record Directory(String path, int size, int scale, String type, int min, int max, int threshold) {
        boolean matches(int want, int wantScale) {
            if (scale != wantScale) return false;
            return switch (type) {
                case "Fixed" -> size == want;
                case "Scalable" -> min <= want && want <= max;
                default -> size - threshold <= want && want <= size + threshold;
            };
        }
        int distance(int want, int wantScale) {
            int low = switch (type) { case "Fixed" -> size; case "Scalable" -> min; default -> size - threshold; };
            int high = switch (type) { case "Fixed" -> size; case "Scalable" -> max; default -> size + threshold; };
            int target = want * wantScale;
            low *= scale; high *= scale;
            return target < low ? low - target : target > high ? target - high : 0;
        }
    }

    record Theme(List<Path> roots, List<String> parents, List<Directory> directories) {
        static Optional<Theme> load(String name, List<Path> bases) {
            if (!IconThemeName.valid(name)) return Optional.empty();
            var roots = bases.stream().map(base -> base.resolve(name)).filter(Files::isDirectory).toList();
            var index = roots.stream().map(root -> root.resolve("index.theme")).filter(Files::isRegularFile).findFirst();
            if (index.isEmpty()) return Optional.empty();
            try {
                var sections = sections(Files.readString(index.get(), StandardCharsets.UTF_8));
                var header = sections.getOrDefault("Icon Theme", Map.of());
                var parents = list(header.get("Inherits")).stream().filter(IconThemeName::valid).toList();
                var names = new LinkedHashSet<>(list(header.get("Directories")));
                names.addAll(list(header.get("ScaledDirectories")));
                var directories = new ArrayList<Directory>();
                for (String path : names) {
                    var values = sections.get(path);
                    Integer size = values == null || path.contains("..") ? null : integer(values.get("Size"));
                    if (size == null) continue;
                    directories.add(new Directory(path, size, or(integer(values.get("Scale")), 1),
                        values.getOrDefault("Type", "Threshold"), or(integer(values.get("MinSize")), size),
                        or(integer(values.get("MaxSize")), size), or(integer(values.get("Threshold")), 2)));
                }
                return Optional.of(new Theme(roots, parents, List.copyOf(directories)));
            } catch (IOException | RuntimeException failure) {
                LOG.log(System.Logger.Level.WARNING, "Icon theme index " + index.get() + " could not be read; skipping " + name, failure);
                return Optional.empty();
            }
        }

        Optional<Path> find(String icon, int size, int scale) {
            for (Directory directory : directories) if (directory.matches(size, scale)) {
                Path file = file(directory, icon);
                if (file != null) return Optional.of(file);
            }
            Path best = null;
            int closest = Integer.MAX_VALUE;
            for (Directory directory : directories) {
                int distance = directory.distance(size, scale);
                if (distance >= closest) continue;
                Path file = file(directory, icon);
                if (file != null) { best = file; closest = distance; }
            }
            return Optional.ofNullable(best);
        }

        private Path file(Directory directory, String icon) {
            for (Path root : roots) for (String extension : EXTENSIONS) {
                Path file = root.resolve(directory.path()).resolve(icon + "." + extension);
                if (Files.isRegularFile(file)) return file;
            }
            return null;
        }

        private static Map<String, Map<String, String>> sections(String text) {
            var sections = new LinkedHashMap<String, Map<String, String>>();
            Map<String, String> current = null;
            for (String raw : text.split("\\R")) {
                String line = raw.strip();
                if (line.isEmpty() || line.startsWith("#")) continue;
                if (line.startsWith("[") && line.endsWith("]")) {
                    current = sections.computeIfAbsent(line.substring(1, line.length() - 1), key -> new HashMap<>());
                    continue;
                }
                int equals = line.indexOf('=');
                if (current != null && equals > 0) current.putIfAbsent(line.substring(0, equals).strip(), line.substring(equals + 1).strip());
            }
            return sections;
        }
        private static List<String> list(String value) {
            return value == null ? List.of() : Arrays.stream(value.split(",")).map(String::strip).filter(text -> !text.isEmpty()).toList();
        }
        private static Integer integer(String value) {
            try { return value == null ? null : Integer.valueOf(value.strip()); } catch (NumberFormatException malformed) { return null; }
        }
        private static int or(Integer value, int fallback) { return value != null ? value : fallback; }
    }
}
```

- [ ] **Step 6: Run tests**

Run: `./gradlew :jasper-app:test --tests 'dev.jasper.app.platform.*'`
Expected: PASS. If `FlatSVGIcon.hasFound()` is not available in 3.7, replace the check with `icon.getIconWidth() <= 0` and note it in the plan status banner.

- [ ] **Step 7: Commit** — `git add -A jasper-app && git commit -m "feat(platform): resolve icons from the freedesktop icon theme"` (trailer).

---

### Task 7: GTK icons in `AppIcons`

**Files:**
- Create: `main/platform/DesktopIcons.java`, `main/platform/DesktopSkinIcon.java`, `test/platform/DesktopIconsTest.java`
- Modify: `main/platform/AppIcons.java`, `main/platform/OldGnomeCatalog.java:45`, `test/workspace/GtkChromeTest.java`

**Interfaces:**
- Consumes: `FreedesktopIcons`, `IconThemeName.find()`, `FreedesktopNames.of` (Task 6); `SwingAppearance.gtk()` (Task 5); `GtkTestThemes` (Task 3).
- Produces: `DesktopIcons.SOURCE_KEY = "Jasper.desktopIcons"`, `DesktopIcons.COMPACT = 16`, `DesktopIcons.TOOLBAR = 24`, `static Icon DesktopIcons.app(String, int)`, `static Icon DesktopIcons.skin(String)`, `static Optional<ImageIcon> DesktopIcons.themed(List<String>, int)`; `DesktopSkinIcon.toolbar()`.

- [ ] **Step 1: Write the failing test** — `test/platform/DesktopIconsTest.java`:

```java
package dev.jasper.app.platform;

import dev.jasper.app.appearance.GtkTestThemes;
import dev.jasper.app.appearance.ThemeController;
import dev.jasper.app.testsupport.EdtTestExtension;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import javax.imageio.ImageIO;
import javax.swing.Icon;
import javax.swing.JLabel;
import javax.swing.UIManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import static org.assertj.core.api.Assertions.*;

@ExtendWith(EdtTestExtension.class)
class DesktopIconsTest {
    private static final String SVG = "dev/jasper/app/icons/search.svg";
    @TempDir Path root;

    @AfterEach void restore() throws Exception {
        javax.swing.SwingUtilities.invokeAndWait(() -> { UIManager.put(DesktopIcons.SOURCE_KEY, null); new ThemeController(); });
    }

    @Test void appIconsComeFromTheThemeAtChromeAndToolbarSizes() throws Exception {
        GtkTestThemes.themes(GtkTestThemes.LIGHT);
        install();
        Icon compact = AppIcons.icon("square-plus");
        assertThat(compact.getIconWidth()).isEqualTo(16);
        assertThat(pixel(compact, 8)).isEqualTo(Color.RED.getRGB());
        Icon toolbar = AppIcons.toolbarIcon("square-plus");
        assertThat(toolbar.getIconWidth()).isEqualTo(24);
        assertThat(pixel(toolbar, 12)).isEqualTo(Color.GREEN.getRGB());
    }

    @Test void namesTheThemeLacksUseBundledArtwork() throws Exception {
        GtkTestThemes.themes(GtkTestThemes.LIGHT);
        install();
        assertThat(pixels(AppIcons.icon("search"), 16)).isEqualTo(pixels(GnomeIcons.icon("search", 16), 16));
        var lock = AppIcons.skin(getClass().getClassLoader(), SVG, "LOCK");
        assertThat(pixels(lock, 16)).isEqualTo(pixels(OldGnomeCatalog.icon("LOCK", 16), 16));
        assertThat(AppIcons.forToolbar(lock).getIconWidth()).isEqualTo(24);
        assertThat(AppIcons.toolbarIcon("search").getIconWidth()).isEqualTo(24);
    }

    @Test void symbolicSkinIconsTakeTheDarkChromeForeground() throws Exception {
        GtkTestThemes.themes(GtkTestThemes.DARK);
        install();
        var copy = AppIcons.skin(getClass().getClassLoader(), SVG, "COPY");
        assertThat(copy.getIconWidth()).isEqualTo(16);
        assertThat(pixel(copy, 8) & 0xffffff).isEqualTo(0xeeeeec);
        assertThat(AppIcons.forToolbar(copy).getIconWidth()).isEqualTo(24);
        assertThat(pixel(AppIcons.named("COPY"), 8) & 0xffffff).isEqualTo(0xeeeeec);
    }

    @Test void validationIsUnchangedInGtk() {
        GtkTestThemes.themes(GtkTestThemes.LIGHT);
        assertThatIllegalArgumentException().isThrownBy(() -> AppIcons.skin(getClass().getClassLoader(), SVG, "invalid"));
        assertThatIllegalArgumentException().isThrownBy(() -> AppIcons.skin(getClass().getClassLoader(), "missing.svg", "LOCK"));
        assertThatIllegalArgumentException().isThrownBy(() -> AppIcons.icon("nope"));
        assertThatIllegalArgumentException().isThrownBy(() -> AppIcons.toolbarIcon("nope"));
    }

    private void install() throws Exception {
        Path theme = Files.createDirectories(root.resolve("Test"));
        Files.writeString(theme.resolve("index.theme"), "[Icon Theme]\nName=Test\nDirectories=16x16/actions,24x24/actions,scalable/actions\n\n"
            + "[16x16/actions]\nSize=16\nType=Fixed\n[24x24/actions]\nSize=24\nType=Fixed\n"
            + "[scalable/actions]\nSize=16\nType=Scalable\nMinSize=8\nMaxSize=512\n");
        png(theme.resolve("16x16/actions/tab-new.png"), 16, Color.RED);
        png(theme.resolve("24x24/actions/tab-new.png"), 24, Color.GREEN);
        Files.createDirectories(theme.resolve("scalable/actions"));
        Files.writeString(theme.resolve("scalable/actions/edit-copy-symbolic.svg"),
            "<svg xmlns=\"http://www.w3.org/2000/svg\" width=\"16\" height=\"16\"><rect width=\"16\" height=\"16\" fill=\"#000000\"/></svg>");
        UIManager.put(DesktopIcons.SOURCE_KEY, new FreedesktopIcons("Test", List.of(root), List.of()));
    }
    private static void png(Path file, int size, Color color) throws Exception {
        Files.createDirectories(file.getParent());
        var image = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        var g = image.createGraphics();
        try { g.setColor(color); g.fillRect(0, 0, size, size); } finally { g.dispose(); }
        ImageIO.write(image, "png", file.toFile());
    }
    private static int pixel(Icon icon, int at) { return pixels(icon, icon.getIconWidth())[at * icon.getIconWidth() + at]; }
    private static int[] pixels(Icon icon, int size) {
        var image = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        var g = image.createGraphics();
        try { icon.paintIcon(new JLabel(), g, 0, 0); } finally { g.dispose(); }
        return image.getRGB(0, 0, size, size, null, 0, size);
    }
}
```
The theme ships only `edit-copy-symbolic.svg`, as current Adwaita does: `COPY` maps to `edit-copy`, and `DesktopIcons.themed` (Step 3) also tries each candidate's `-symbolic` form, so the recoloured symbolic file is found.

- [ ] **Step 2: Run to verify it fails**

Run: `./gradlew :jasper-app:test --tests 'dev.jasper.app.platform.DesktopIconsTest'`
Expected: compilation failure (`DesktopIcons` missing).

- [ ] **Step 3: Implement**

`DesktopIcons.themed` tries each candidate and then its `-symbolic` form, so themes that ship only symbolic artwork (current Adwaita) still resolve:

`main/platform/DesktopIcons.java`:
```java
package dev.jasper.app.platform;

import java.awt.Color;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import javax.swing.Icon;
import javax.swing.ImageIcon;
import javax.swing.UIManager;

/** GTK-style artwork from the desktop icon theme; bundled artwork stands in for any name the theme lacks. */
final class DesktopIcons {
    static final String SOURCE_KEY = "Jasper.desktopIcons";
    static final int COMPACT = 16;
    static final int TOOLBAR = 24;
    private DesktopIcons() {}

    static Icon app(String name, int size) {
        if (!GnomeIcons.NAMES.contains(name)) throw new IllegalArgumentException("Unknown application icon: " + name);
        return themed(FreedesktopNames.of(name), size).<Icon>map(icon -> icon).orElseGet(() -> GnomeIcons.icon(name, size));
    }

    static Icon skin(String name) { return new DesktopSkinIcon(name); }

    /** Each candidate, then its symbolic form, recoloured to the chrome foreground. */
    static Optional<ImageIcon> themed(List<String> names, int size) {
        var candidates = new ArrayList<>(names);
        for (String name : names) candidates.add(name + "-symbolic");
        Color symbolic = UIManager.getColor("Jasper.chromeForeground");
        return source().image(candidates, size, symbolic == null ? Color.BLACK : symbolic).map(ImageIcon::new);
    }

    /** One resolver per installed look and feel, created on first use from the live desktop. */
    static FreedesktopIcons source() {
        if (UIManager.get(SOURCE_KEY) instanceof FreedesktopIcons icons) return icons;
        var created = FreedesktopIcons.fromEnvironment(IconThemeName.find(), System.getenv(), Path.of(System.getProperty("user.home")));
        UIManager.getLookAndFeelDefaults().put(SOURCE_KEY, created);
        return created;
    }
}
```

`main/platform/DesktopSkinIcon.java`:
```java
package dev.jasper.app.platform;

import javax.swing.ImageIcon;

/** Captured GTK artwork for a semantic name; ImageIcon lets the LAF derive its disabled form. */
final class DesktopSkinIcon extends ImageIcon {
    private final String name;
    DesktopSkinIcon(String name) {
        super(DesktopIcons.themed(FreedesktopNames.of(name), DesktopIcons.COMPACT).map(ImageIcon::getImage)
            .orElseGet(() -> OldGnomeCatalog.icon(name, DesktopIcons.COMPACT).getImage()));
        this.name = name;
    }
    ImageIcon toolbar() {
        return DesktopIcons.themed(FreedesktopNames.of(name), DesktopIcons.TOOLBAR)
            .orElseGet(() -> OldGnomeCatalog.icon(name, DesktopIcons.TOOLBAR));
    }
}
```

`OldGnomeCatalog.icon` — allow 24: `if (size != 16 && size != 24 && size != 28) throw new IllegalArgumentException("Unsupported icon size: " + size);`

`AppIcons.java`:
```java
    public static javax.swing.Icon icon(String name) {
        if (!GnomeIcons.NAMES.contains(name))
            throw new IllegalArgumentException("Unknown application icon: " + name);
        if (SwingAppearance.gtk()) return DesktopIcons.app(name, DesktopIcons.COMPACT);
        if (SwingAppearance.retro()) return GnomeIcons.icon(name);
        // (rest unchanged)
    }

    /** Application toolbar artwork: desktop icons in GTK, large classic icons in retro, regular modern icons otherwise. */
    public static javax.swing.Icon toolbarIcon(String name) {
        if (SwingAppearance.gtk()) return DesktopIcons.app(name, DesktopIcons.TOOLBAR);
        return SwingAppearance.retro() ? GnomeIcons.icon(name, 28) : icon(name);
    }
```
In `named(name)` after `String resource = NamedIcons.resource(name);` add `if (SwingAppearance.gtk()) return DesktopIcons.skin(name);`.
In `skin(...)` replace the final return with:
```java
        if (SwingAppearance.gtk()) return DesktopIcons.skin(retroName);
        return SwingAppearance.retro() ? new SkinIcon(retroName) : themed(loader, modernSvgResourcePath);
```
`forToolbar`:
```java
    /** Sizes only managed retro and GTK icons; shared compact icons and external artwork remain untouched. */
    public static javax.swing.Icon forToolbar(javax.swing.Icon icon) {
        if (icon instanceof DesktopSkinIcon desktop) return desktop.toolbar();
        return icon instanceof SkinIcon managed ? managed.toolbar() : icon;
    }
```
Update the class JavaDoc: `/** Bundled Tabler, GNOME 2 and Tango icons, or the desktop icon theme in GTK; no network access. */`

`GtkChromeTest.gtkBuildsNativeChromeWithoutMetalStyling` — add inside the `edt` block, after the toolbar assertion:
```java
            for (var child : owner.toolbar().getComponents()) if (child instanceof JButton button)
                assertThat(button.getIcon().getIconWidth()).isEqualTo(24);
```

- [ ] **Step 4: Run tests**

Run: `./gradlew :jasper-app:test --tests 'dev.jasper.app.platform.*' --tests 'dev.jasper.app.workspace.GtkChromeTest' --tests 'dev.jasper.app.plugins.*'`
Expected: PASS. Existing `SkinIconsTest`/`OldGnomeCatalogTest` stay green (if `OldGnomeCatalogTest` asserts that 24 is rejected, change that assertion to a different unsupported size such as 20).

- [ ] **Step 5: Commit** — `git add -A jasper-app && git commit -m "feat(platform): draw GTK icons from the desktop icon theme"` (trailer).

---

### Task 8: Documentation, SDK wording and full verification

**Files:**
- Modify: `jasper-sdk/src/main/java/dev/jasper/sdk/ui/Appearance.java`, `jasper-sdk/src/main/java/dev/jasper/sdk/ui/OldGnomeIcon.java`, `docs/configuration.md`, `docs/app-architecture.md`, `docs/app-maintenance.md`, `docs/STATUS.md`, this plan's status banner

- [ ] **Step 1: SDK JavaDoc (no API change)**

`Appearance.icon(IconName)` paragraph: `A host-owned icon for a semantic name. Jasper selects the artwork for the running skin, including the desktop icon theme when the host runs its GTK style; plugins need no resource path or skin check. Compact icons are 16 pixels; retro host toolbars use an independent 28-pixel variant and GTK host toolbars a 24-pixel one.`

`Appearance.icon(String, OldGnomeIcon)` first sentence: `A 16 by 16 icon using the plugin's monochrome SVG in modern mode, bundled OldGNOME2 artwork in retro mode, and the matching desktop icon-theme artwork (else the OldGNOME2 artwork) in GTK mode.`

`OldGnomeIcon` class comment: `Semantic artwork for the classic skins. The host supplies bundled OldGNOME2 images in retro, and desktop icon-theme images with those as fallback in GTK.`

- [ ] **Step 2: Docs**

`docs/configuration.md` — add after the retro section a `### GTK appearance` section:
```markdown
### GTK appearance

On Linux, set `style = "gtk"` in your existing `[ui.theme]` table and fully quit and relaunch Jasper.
Jasper then uses Java's GTK look and feel, so controls, menus, fonts and colours follow your desktop's
GTK theme, and toolbar, menu and plugin icons come from your icon theme (for example Adwaita or Papirus),
with Jasper's bundled artwork for any icon the theme lacks. Tabs, toolbar and title bar use the same
standard layout as retro. The terminal takes its background, text, cursor and selection colours from the
theme's text views; the 16 ANSI colours follow Jasper Dark or Jasper Light by the background's brightness.
`variant` is ignored. Desktop theme changes apply on the next launch.

Where GTK is not available (macOS, Windows, or a Linux session whose Java runtime cannot load native GTK,
which can include Wayland-native sessions), Jasper starts in the modern style and the status bar explains
why. `ui.font` settings apply on top of the GTK theme font.
```

`docs/app-architecture.md` appearance paragraph (near line 179): add `GTK installs the JDK's GTKLookAndFeel by class name; GtkDefaults samples its colours into the same Jasper.* aliases and GtkPalette derives the terminal palette. When GTK cannot be installed the controller installs modern and keeps the requested style for the restart check. SwingAppearance.nativeChrome() selects the standard-Swing tabs, toolbar and title for retro and GTK; retro() is Metal paint only. In GTK, AppIcons resolves freedesktop names through FreedesktopIcons (Icon Theme Specification) before bundled artwork.`

`docs/app-maintenance.md` icon recipe: add `A new application or semantic icon also needs freedesktop candidates in FreedesktopNames; FreedesktopNamesTest fails until it has them.`

`docs/STATUS.md` — add a dated entry at the top of the current-work section:
```markdown
**GTK style (2026-09-24):** On `claude/gtk-theme`, `ui.theme.style = "gtk"` installs Java's GTK look and feel with retro's native chrome, a terminal palette derived from the GTK text view, and icons from the freedesktop icon theme ([spec](superpowers/specs/2026-09-24-jasper-gtk-theme-design.md), [plan](superpowers/plans/2026-09-24-jasper-gtk-theme.md)). Unavailable GTK falls back to modern with a status warning. Deviation: the terminal palette has no selection foreground, so only the selection background is derived. `./gradlew check`: <fill in the counts from the XML>. Not done: Linux GUI verification (Adwaita, Adwaita-dark, a third-party icon theme, explicit `ui.font`, Wayland fallback), merge, push.
```
(Replace `<fill in ...>` with the real numbers from `jasper-app/build/test-results/test/*.xml` after Step 3 — this is the one value only known after running.)

- [ ] **Step 3: Full verification**

Run: `./gradlew check`
Expected: BUILD SUCCESSFUL. Count results:
```bash
python3 - <<'PY'
import glob, xml.etree.ElementTree as ET
t=f=e=s=0
for p in glob.glob("*/build/test-results/test/*.xml")+glob.glob("plugins/*/build/test-results/test/*.xml"):
    r=ET.parse(p).getroot(); t+=int(r.get("tests")); f+=int(r.get("failures")); e+=int(r.get("errors")); s+=int(r.get("skipped"))
print(f"tests={t} failures={f} errors={e} skipped={s}")
PY
```
Run the source hygiene script from `AGENTS.md`; expected: no output.

- [ ] **Step 4: Update the plan status banner** to `Status: implemented on claude/gtk-theme; ./gradlew check green (<counts>); Linux GUI verification pending (user).` and record any deviations taken during execution.

- [ ] **Step 5: Commit** — `git add -A && git commit -m "docs: document the GTK style"` (trailer).

---

## User verification (after execution; not an agent step)

On a Linux GTK desktop: Adwaita and Adwaita-dark (controls, menus, tabs, toolbar, dialogs, status bar, command palette, terminal colours); a third-party icon theme such as Papirus; an explicit `ui.font`; a Wayland session (GTK or clean modern fallback with warning); retro and modern unchanged.
