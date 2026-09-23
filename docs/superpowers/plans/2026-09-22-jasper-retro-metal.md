# Jasper Retro Metal Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [x]`) syntax for tracking.

**Status:** User authorized native execution; implementation tasks 1–6 and screenshot refinements are complete on `codex/retro-metal`, based on `ac02f94`. Task 7 headless verification passed (1,576 tests, three skips, no failures/errors; architecture guards, distribution, source hygiene and image inspection passed). Independent review is underway; native acceptance remains user-run. User supplied GPL2+ licensing for OldGNOME2; bundled notices record that provenance without inventing authors. Original checkout changes remain untouched. No GUI launch, merge or push.

**Execution adjustments:** Preserve existing whole-snapshot rejection for invalid config types; unknown strings still default per-field. Test cleanup and parameterized cases explicitly marshal LAF changes to EDT because the existing extension intercepts ordinary test methods only. Invalidate the stock toolbar layout cache after compacting labels to prevent clipping. User screenshot feedback supersedes stock chrome borders: icon-only 20-pixel tab close targets, flat compact toolbar buttons with 24-pixel artwork, and unboxed status actions. Ordinary forms retain Metal. Palette scope integration tests cover submission/cancellation in both styles. Vault renders use standalone Metal within the SDK-only plugin test module; modern form rendering remains in the host tests. Visual QA also corrected the palette accent alias and terminal placeholder foreground, with RED/GREEN regressions. These changes preserve module boundaries.

**Goal:** Add a restart-required `ui.theme.style = "retro"` option that gives Jasper stock light Metal controls, GNOME 2 application icons, and a high-contrast terminal while preserving modern mode and existing SDK/plugin behavior.

**Architecture:** Capture an app-only `ThemeStyle` before UI construction. Extend existing theme resolution and select presentation paths in the workspace, platform and auxiliary-window owners; retain the existing session, action, tab, contribution and palette models. Existing plugins see `Variant.LIGHT` and ordinary Swing controls inherit Metal without new SDK signatures.

**Tech Stack:** Java 25 on JetBrains Runtime 25, Swing Metal/Ocean, existing FlatLaf for modern mode and SVG decoding, existing tomlj, JUnit/AssertJ, Gradle wrapper. No new dependency.

**Spec:** [Retro Metal design](../specs/2026-09-22-jasper-retro-metal-design.md). Read it alongside `docs/STATUS.md`, `docs/app-architecture.md`, `docs/app-maintenance.md`, `docs/sdk-architecture.md`, and `docs/plugin-authoring.md`.

## Global Constraints

- Java 25 on JetBrains Runtime 25; use `./gradlew`, never system Gradle.
- No new runtime dependency, emulator backend, public terminal API, or SDK signature.
- Swing and global look-and-feel mutation run on the EDT; preserve terminal/session threading.
- Preserve package DAGs; SDK imports in app production remain confined to `dev.jasper.app.plugins`.
- Do not launch the GUI or benchmark; native acceptance belongs to the user.
- Work on a `codex/` branch; never commit on `main`, merge, or push without authorization.
- End commit messages with `Co-Authored-By: Codex <noreply@openai.com>`.
- Preserve unrelated working-tree changes and existing plugin/session lifecycle behavior.
- Record execution deviations in the plan status banner and `docs/STATUS.md`.
- Metal means unmodified `MetalLookAndFeel` plus `OceanTheme` in this proposal. Do not add Steel as a second setting.
- No live style mutation, replacement sessions, automatic restart, or new plugin lifecycle.
- Scope includes application-owned artwork. Existing plugin SVG/custom-icon contracts remain intact; do not promise replacement of arbitrary plugin drawing.
- One commit per implementation task, after its targeted checks. Full `check`, architecture guards and `installDist` gate the final deliverable. Commit only named task files; never use `git add .`.
- New Java code below needs its listed imports or fully qualified types; add ordinary imports in the owner file. Existing private helpers/fixtures explicitly named below remain in their owner packages.

## Review Focus

1. **A style reload while sessions, hidden panels or new-window requests exist:** all use the original process style; only a derived notice changes. Task 2 pins both reload directions and session identity.
2. **Metal followed by modern in the same test JVM, or an installation failure:** no persistent Metal aliases, branded Metal buttons, stale delegate or false success event. Task 2 pins defaults and rollback.
3. **Long/literal-HTML titles, many tabs and close during drag:** title safety, reachable tabs, same selection/session objects, no stale close target. Task 4 pins identity and gestures.
4. **Contributed UI with missing icons, disabled actions, hidden panels and a narrow toolbar:** readable Metal controls, original actions/ownership, accessible fallback, no clipping at the declared minimum. Tasks 5 and 6 own these tests.
5. **Installed artifacts on another machine/device scale:** no home-directory references or symlink inheritance, usable images at 1x/2x, notices packaged. Tasks 3 and 7 verify resources and distribution contents.

## File structure and ownership

```text
jasper-app/src/main/java/dev/jasper/app/
  config/ThemeStyle.java                         new config enum
  config/{ConfigSnapshot,ConfigLoader,ConfigTemplate}.java
  appearance/{BuiltinTheme,ThemeController}.java
  appearance/{MetalDefaults,RetroPalette}.java  new LAF aliases and terminal values
  platform/SwingAppearance.java                  new JDK-only presentation predicate
  platform/{AppIcons,GnomeIcons}.java            modify resolver; new PNG loader
  application/{JasperApplication,ConfigurationController}.java
  workspace/{TerminalDeck,WindowContent,WindowChrome,WindowCommands,WindowRail,WindowStatusBar}.java
  workspace/RetroTabs.java                       new headers/gestures over retained deck
  workspace/RetroToolbar.java                    new ordinary toolbar with compact labels
  workspace/WindowCommandPalette.java
  windows/NativeShells.java
  palette/CommandPalette.java
jasper-app/src/main/resources/dev/jasper/app/icons/gnome2/
  {16,24}/*.png, assets.tsv, LICENSE.txt, NOTICE.md
jasper-sdk/src/main/java/dev/jasper/sdk/Variant.java     documentation only
jasper-app/src/test/java/dev/jasper/app/
  config/{ConfigLoaderTest,ConfigSnapshotBuilderTest,ConfigTemplateTest}.java
  appearance/RetroThemeTest.java
  application/ConfigurationTestSupport.java
  workspace/{ConfigurationControllerTest,RetroTabsTest,RetroChromeTest}.java
  platform/{GnomeIconsTest,AppIconsTest,AppIconsThemedTest}.java
  windows/NativeShellsChromeTest.java
  plugins/HostedUiTest.java
  workspace/{WindowChromeContributionsTest,WindowContributionsTest}.java
  palette/CommandPaletteTest.java
docs/{configuration,plugin-authoring,app-architecture,STATUS}.md
config.example.toml, jasper-app/README.md
```

No app package dependency edge changes are necessary: appearance continues to depend on config/lifecycle/terminal values; the new platform helpers depend only on JDK classes. Workspace and palette already depend on platform. No production test bridge is added.

---

### Task 0: Establish the execution baseline

**Files:** No production changes.

**Interfaces:** Consumes the reviewed spec/plan; produces a verified implementation branch.

- [x] **Step 1: Read the current branch and worktree state.**

```bash
git status --short --branch
git log -3 --oneline
git rev-parse --git-dir --git-common-dir
```

Use the isolated planning worktree if it is still attached and clean. Create `codex/retro-metal` there for execution, or use the native worktree tool if executing elsewhere. Do not overwrite the unrelated `WindowStatusBar.java` / `VaultPlugin.java` edits found in the original checkout during planning. If the base has advanced, re-read touched owner files before applying the snippets; record actual deviations.

- [x] **Step 2: Run the headless baseline.**

```bash
./gradlew check
```

Expected: exit 0; record XML totals. The known concurrent-list race in `AttachedSessionTest.shellIntegrationWorksButNeverReportsALocalDirectory` is documented in STATUS; identify and report any failure rather than hiding it with blanket retries. No baseline GUI or benchmark.

### Task 1: Add the saved style value without changing the running UI

**Files:** Create `config/ThemeStyle.java`; modify `config/{ConfigSnapshot,ConfigLoader,ConfigTemplate}.java`, `config.example.toml`; tests `config/{ConfigLoaderTest,ConfigSnapshotBuilderTest,ConfigTemplateTest}.java` (paths relative to the app Java roots above).

**Interfaces:** Produces `ThemeStyle.MODERN/RETRO`, `ConfigSnapshot.style()`, `ConfigSnapshot.Builder.style(ThemeStyle)`. All existing ConfigSnapshot constructor signatures continue to mean modern; `toBuilder()` retains the new value.

- [x] **Step 1: Add these parser and builder regression tests.** Place the first two in `ConfigLoaderTest` (which already has `FILE` and `parse`), and the third in `ConfigSnapshotBuilderTest`.

```java
@Test void retroStyleDoesNotRewriteSavedVariant() {
    var result = parse("[ui.theme]\nstyle='retro'\nvariant='dark'\n");
    assertThat(result.rejected()).isFalse();
    assertThat(result.diagnostics()).isEmpty();
    assertThat(result.snapshot().style()).isEqualTo(ThemeStyle.RETRO);
    assertThat(result.snapshot().variant()).isEqualTo(Appearance.DARK);
    assertThat(parse("").snapshot().style()).isEqualTo(ThemeStyle.MODERN);
}

@Test void invalidStyleUsesTheExistingPerFieldDiagnosticPolicy() {
    for (String value : java.util.List.of("'private-value'", "7", "true")) {
        var result = parse("[ui.theme]\nstyle=" + value + "\nvariant='light'\n");
        assertThat(result.rejected()).isFalse();
        assertThat(result.snapshot().style()).isEqualTo(ThemeStyle.MODERN);
        assertThat(result.snapshot().variant()).isEqualTo(Appearance.LIGHT);
        assertThat(result.diagnostics()).singleElement().satisfies(problem -> {
            assertThat(problem.key()).isEqualTo("ui.theme.style");
            assertThat(problem.line()).isEqualTo(2);
            assertThat(problem.column()).isEqualTo(1);
            assertThat(problem.message()).doesNotContain("private-value");
        });
    }
}

@Test void copyingASnapshotRetainsStyleAndUnrelatedValues() {
    var saved = ConfigSnapshot.builder().style(ThemeStyle.RETRO).columns(91).build();
    assertThat(saved.toBuilder().lines(33).build())
        .isEqualTo(ConfigSnapshot.builder().style(ThemeStyle.RETRO).columns(91).lines(33).build());
    assertThat(ConfigSnapshot.defaults().style()).isEqualTo(ThemeStyle.MODERN);
}
```

- [x] **Step 2: Run RED.**

```bash
./gradlew :jasper-app:test --tests '*ConfigLoaderTest' --tests '*ConfigSnapshotBuilderTest'
```

Expected: compilation failure for the new enum/accessors.

- [x] **Step 3: Add the value and wire every construction path.**

```java
package dev.jasper.app.config;

/** Startup-selected application presentation; changing the saved value requires restart. */
public enum ThemeStyle { MODERN, RETRO }
```

Append `ThemeStyle style` to the canonical `ConfigSnapshot` record components and require it nonnull. Preserve the former canonical constructor as this delegating overload; the older overloads already delegate through it:

```java
public ConfigSnapshot(int tabHeight, ToolbarMode toolbar, boolean statusBar,
                      FontConfig font, Appearance variant, Map<String, String> keybindings,
                      int columns, int lines, TerminalConfig terminal, boolean buddyEnabled,
                      int maxResults, int longCommandSeconds, boolean backgroundEnabled,
                      Map<String, Map<String, Object>> plugins) {
    this(tabHeight, toolbar, statusBar, font, variant, keybindings, columns, lines,
        terminal, buddyEnabled, maxResults, longCommandSeconds, backgroundEnabled,
        plugins, ThemeStyle.MODERN);
}
```

The builder additions and final constructor call are:

```java
private ThemeStyle style;
// In Builder(ConfigSnapshot source):
style = source.style();
public Builder style(ThemeStyle value) { style = value; return this; }
public ConfigSnapshot build() {
    return new ConfigSnapshot(tabHeight, toolbar, statusBar, font, variant, keybindings,
        columns, lines, terminal, buddyEnabled, maxResults, longCommandSeconds,
        backgroundEnabled, plugins, style);
}
```

In `ConfigLoader`, add `style` beside `variant` in the allowed `ui.theme` keys, initialize the field to MODERN, append it to the snapshot constructor, and add:

```java
case "ui.theme.style" -> style = choice(path, value, Map.of(
    "modern", ThemeStyle.MODERN, "retro", ThemeStyle.RETRO), style);
```

In both generated and example `[ui.theme]` sections insert exactly:

```toml
# Application style: "modern" or "retro" (stock light Java Metal).
# Changing style requires fully restarting Jasper; existing sessions keep their current style.
# style = "modern"
# Variant applies only to modern; retro always uses light controls and a dark terminal.
```

- [x] **Step 4: Run GREEN and commit.**

```bash
./gradlew :jasper-app:test --tests '*ConfigLoaderTest' --tests '*ConfigSnapshotBuilderTest' --tests '*ConfigTemplateTest'
git add jasper-app/src/main/java/dev/jasper/app/config jasper-app/src/test/java/dev/jasper/app/config config.example.toml
git commit -m "feat: add startup appearance style configuration" -m "Co-Authored-By: Codex <noreply@openai.com>"
```

### Task 2: Capture style, install stock Metal, and report pending restart

**Files:** Create `appearance/{MetalDefaults,RetroPalette}.java`, `platform/SwingAppearance.java`, `appearance/RetroThemeTest.java`; modify `appearance/{BuiltinTheme,ThemeController}.java`, `application/{JasperApplication,ConfigurationController}.java`; extend test-only `application/ConfigurationTestSupport.java` and the startup helper in `workspace/ConfigurationControllerTest.java`. Update modern-only enum-loop expectations in `appearance/BrandedButtonsTest.java`, `palette/CommandPaletteTest.java`, and `workspace/{ThemeControllerTest,ConfigurationStatusTest,MacTitleBarTest,SplitDividerTest,MockUiPreview,TitleBarPreview,CommandPalettePreview}.java`; the preview switch must compile with the new enum member.

**Interfaces:** Produces `ThemeController(ThemeStyle, Appearance)`, `ThemeController.style()`, `BuiltinTheme.RETRO` with `.appearance()==LIGHT`, `SwingAppearance.retro()`. Existing constructors and installer seam retain their modern behavior. Saved style remains in ConfigSnapshot; the controller's final style is the effective value.

- [x] **Step 1: Create `RetroThemeTest` with the EDT extension and these tests.**

```java
package dev.jasper.app.appearance;

import dev.jasper.app.config.Appearance;
import dev.jasper.app.config.ThemeStyle;
import dev.jasper.app.testsupport.EdtTestExtension;
import javax.swing.*;
import javax.swing.plaf.metal.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import static org.assertj.core.api.Assertions.*;

@ExtendWith(EdtTestExtension.class)
class RetroThemeTest {
    @AfterEach void restore() { new ThemeController(); }

    @Test void retroIsStockMetalWithLightChromeAndAnIndependentBlackTerminal() {
        var themes = new ThemeController(ThemeStyle.RETRO, Appearance.DARK);
        assertThat(UIManager.getLookAndFeel()).isExactlyInstanceOf(MetalLookAndFeel.class);
        assertThat(MetalLookAndFeel.getCurrentTheme()).isExactlyInstanceOf(OceanTheme.class);
        assertThat(new JButton().getUI()).isInstanceOf(MetalButtonUI.class);
        assertThat(new JTabbedPane().getUI()).isInstanceOf(MetalTabbedPaneUI.class);
        assertThat(themes.choice()).isEqualTo(Appearance.LIGHT);
        assertThat(themes.current().palette().background()).isEqualTo(java.awt.Color.BLACK);
        var before = themes.current();
        themes.selectAppearance(Appearance.DARK);
        themes.configure(Appearance.DARK);
        assertThat(themes.current()).isEqualTo(before);
        assertThat(themes.style()).isEqualTo(ThemeStyle.RETRO);
    }

    @Test void metalAliasesDoNotLeakIntoTheNextModernInstallation() {
        new ThemeController(ThemeStyle.RETRO, Appearance.DARK);
        assertThat(UIManager.getBoolean("Jasper.retro")).isTrue();
        new ThemeController(ThemeStyle.MODERN, Appearance.LIGHT);
        assertThat(UIManager.getBoolean("Jasper.retro")).isFalse();
        assertThat(UIManager.getLookAndFeel()).isInstanceOf(com.formdev.flatlaf.FlatLightLaf.class);
        assertThat(new JButton().getUI()).isInstanceOf(BrandedButtonUI.class);
    }
}
```

In `ConfigurationControllerTest.start`, initialize themes with the initial snapshot's style/variant. Add `public ConfigService.State shown() { return controller.shown(); }` only to the existing test bridge. Reserve the following two integration tests for Task 4, when workspace presentation exists; do not commit knowingly failing integration tests in this task:

```java
@Test void changingDesiredStyleKeepsExistingAndNewOwnersUntilRestart() throws Exception {
    start("ui.theme.style='retro'\n");
    edt(() -> owner());
    var first = owners.getFirst();
    var retained = first.currentPane();
    reload("ui.theme.style='modern'\nfont.size=21\n");
    edt(() -> {
        owner();
        assertThat(themes.style()).isEqualTo(dev.jasper.app.config.ThemeStyle.RETRO);
        assertThat(first.currentPane()).isSameAs(retained);
        assertThat(owners).allSatisfy(w -> assertThat(w.theme().chrome()).isEqualTo(BuiltinTheme.RETRO));
        assertThat(controller.shown().diagnostics()).filteredOn(d -> d.key().equals("ui.theme.style"))
            .singleElement().satisfies(d -> assertThat(d.message()).contains("Restart Jasper"));
        assertThat(controller.snapshot().fontSize()).isEqualTo(21);
    });
    reload("ui.theme.style='retro'\n");
    edt(() -> assertThat(controller.shown().diagnostics()).noneMatch(d -> d.key().equals("ui.theme.style")));
}

@Test void modernVariantRemainsLiveWhileRetroIsPending() throws Exception {
    start("ui.theme.style='modern'\nui.theme.variant='dark'\n");
    reload("ui.theme.style='retro'\nui.theme.variant='light'\n");
    edt(() -> {
        assertThat(themes.style()).isEqualTo(dev.jasper.app.config.ThemeStyle.MODERN);
        assertThat(themes.current().chrome()).isEqualTo(BuiltinTheme.LIGHT);
        assertThat(controller.shown().diagnostics()).anyMatch(d -> d.key().equals("ui.theme.style"));
    });
}
```

Both integration tests are added/run in Task 4; the resolution/defaults tests are this task's immediate gate. Existing `savedFieldsApplyAcrossOwnersAndPendingHiddenViewsWithoutReplacingSessions` continues to prove session preservation; add the focused retro session regression in Task 6 without changing its existing modern-specific assertions.

- [x] **Step 2: Run RED for the independent theme tests.**

```bash
./gradlew :jasper-app:test --tests '*RetroThemeTest'
```

- [x] **Step 3: Add the JDK-only style predicate and app palette.**

```java
package dev.jasper.app.platform;

import javax.swing.UIManager;

/** Presentation predicate supplied by the installed app LAF; contains no configuration state. */
public final class SwingAppearance {
    private SwingAppearance() {}
    public static boolean retro() { return UIManager.getBoolean("Jasper.retro"); }
}
```

```java
package dev.jasper.app.appearance;

import dev.jasper.terminal.config.Palette;
import java.awt.Color;
import java.util.Arrays;

/** App-only retro defaults; does not alter terminal emulation or explicit application colors. */
final class RetroPalette {
    private RetroPalette() {}
    static Palette create() {
        int[] ansi = {0x000000, 0xff5555, 0x55ff55, 0xffff55, 0x6688ff, 0xff55ff, 0x55ffff, 0xdddddd,
            0x888888, 0xff8888, 0x88ff88, 0xffff88, 0x99bbff, 0xff88ff, 0x88ffff, 0xffffff};
        return new Palette(Color.WHITE, Color.BLACK, Color.WHITE, new Color(0x264f78),
            Arrays.stream(ansi).mapToObj(Color::new).toList());
    }
}
```

`MetalDefaults` installs aliases into the LAF-specific table. The complete class is:

```java
package dev.jasper.app.appearance;

import java.awt.Color;
import javax.swing.UIDefaults;
import javax.swing.UIManager;
import javax.swing.UnsupportedLookAndFeelException;
import javax.swing.plaf.metal.MetalLookAndFeel;
import javax.swing.plaf.metal.OceanTheme;

/** Stock Metal delegates with aliases for Jasper-owned paint, never branded component defaults. */
final class MetalDefaults {
    private MetalDefaults() {}
    static boolean install() {
        MetalLookAndFeel.setCurrentTheme(new OceanTheme());
        try { UIManager.setLookAndFeel(new MetalLookAndFeel()); }
        catch (UnsupportedLookAndFeelException failure) { throw new IllegalStateException(failure); }
        UIDefaults defaults = UIManager.getLookAndFeelDefaults();
        defaults.put("Jasper.retro", true);
        alias(defaults, "Panel.background", "Jasper.titleBackground", "Jasper.paletteBackground");
        alias(defaults, "Label.foreground", "Jasper.titleForeground", "Jasper.chromeForeground",
            "Jasper.tabSelectedForeground", "Jasper.paletteForeground");
        alias(defaults, "Label.disabledForeground", "Jasper.titleInactiveForeground",
            "Jasper.mutedForeground", "Jasper.paletteMutedForeground");
        alias(defaults, "controlShadow", "Jasper.titleSeparator", "Jasper.splitDivider",
            "Component.borderColor", "Jasper.paletteBorder");
        alias(defaults, "TabbedPane.selected", "Jasper.tabSelectedBackground");
        alias(defaults, "textHighlight", "Jasper.paletteAccent", "Component.focusedBorderColor");
        alias(defaults, "List.selectionBackground", "Jasper.paletteSelectionBackground");
        alias(defaults, "List.selectionForeground", "Jasper.paletteSelectionForeground");
        defaults.put("Jasper.runningForeground", new Color(0x166534));
        defaults.put("Actions.Red", new Color(0xb91c1c));
        defaults.put("Actions.Yellow", new Color(0x854d0e));
        defaults.put("Actions.Green", new Color(0x166534));
        defaults.put("Jasper.configSuccessForeground", new Color(0x166534));
        defaults.put("Jasper.configWarningForeground", new Color(0x854d0e));
        defaults.put("Jasper.configErrorForeground", new Color(0xb91c1c));
        return true;
    }
    private static void alias(UIDefaults defaults, String source, String... targets) {
        Object value = defaults.get(source);
        if (value == null) throw new IllegalStateException("Missing Metal default: " + source);
        for (String target : targets) defaults.put(target, value);
    }
}
```

The three configuration aliases above are the exact keys used by `WindowStatusBar.configColor` at the inspected baseline. Assert each is nonnull in `RetroThemeTest`. Do not import FlatLaf property files to satisfy a missing alias.

- [x] **Step 4: Resolve the final process style without multiplying state models.**

In `BuiltinTheme`, append `RETRO("retro", "Retro", RetroPalette.create())`; change `appearance()` to `return this == DARK ? Appearance.DARK : Appearance.LIGHT;`. Keep `of(Appearance)` modern-only. In `ThemeController`, remove static eager defaults registration and register the FlatLaf package once only when installing a modern theme. Add a final `ThemeStyle style` and these constructor/accessor/resolution changes:

```java
public ThemeController() { this(ThemeStyle.MODERN, Appearance.DARK); }
public ThemeController(ThemeStyle style, Appearance saved) {
    this(style, saved, ThemeController::install);
}
public ThemeController(Predicate<BuiltinTheme> installer) {
    this(ThemeStyle.MODERN, Appearance.DARK, installer);
}
ThemeController(ThemeStyle style, Appearance saved, Predicate<BuiltinTheme> installer) {
    requireEdt();
    this.style = Objects.requireNonNull(style);
    this.installer = Objects.requireNonNull(installer);
    this.state = ThemeState.defaults().configure(Objects.requireNonNull(saved));
    installOrThrow(resolve(state).chrome());
}
public ThemeStyle style() { requireEdt(); return style; }
private ResolvedTheme resolve(ThemeState candidate) {
    return style == ThemeStyle.RETRO
        ? new ResolvedTheme(BuiltinTheme.RETRO, BuiltinTheme.RETRO.palette()) : candidate.resolve();
}
public ResolvedTheme current() { requireEdt(); return resolve(state); }
public Appearance choice() {
    requireEdt(); return style == ThemeStyle.RETRO ? Appearance.LIGHT : state.choice();
}
public void selectAppearance(Appearance choice) {
    requireEdt(); Objects.requireNonNull(choice);
    if (style == ThemeStyle.MODERN) apply(state.choose(choice));
}
private void apply(ThemeState candidate) {
    if (style == ThemeStyle.RETRO) { state = candidate; return; }
    ResolvedTheme previous = resolve(state), next = resolve(candidate);
    boolean chromeChanged = previous.chrome() != next.chrome();
    boolean choiceChanged = state.choice() != candidate.choice();
    if (chromeChanged) installOrThrow(next.chrome());
    state = candidate;
    if (!previous.equals(next) || choiceChanged)
        for (var listener : List.copyOf(listeners)) listener.accept(next, chromeChanged);
}
```

The install switch is:

```java
private static boolean modernDefaultsRegistered;
static boolean install(BuiltinTheme theme) {
    requireEdt();
    if (theme == BuiltinTheme.RETRO) return MetalDefaults.install();
    if (!modernDefaultsRegistered) {
        FlatLaf.registerCustomDefaultsSource("dev.jasper.app.themes");
        modernDefaultsRegistered = true;
    }
    return theme == BuiltinTheme.LIGHT ? FlatLightLaf.setup() : FlatDarkLaf.setup();
}
```

In `installOrThrow`, capture `MetalLookAndFeel.getCurrentTheme()` alongside the existing LAF; on failure restore it before restoring the LAF:

```java
// Next to LookAndFeel previous = UIManager.getLookAndFeel():
var previousMetalTheme = javax.swing.plaf.metal.MetalLookAndFeel.getCurrentTheme();
// First statement inside catch (RuntimeException failure):
javax.swing.plaf.metal.MetalLookAndFeel.setCurrentTheme(previousMetalTheme);
```

Add this installer-seam regression to `RetroThemeTest`; retain the existing modern failure/subscriber tests:

```java
@Test void failedRetroInstallationRestoresBothGlobalDefaultsOwners() {
    new ThemeController();
    var before = UIManager.getLookAndFeel();
    var beforeMetal = MetalLookAndFeel.getCurrentTheme();
    assertThatThrownBy(() -> new ThemeController(ThemeStyle.RETRO, Appearance.DARK, theme -> {
        MetalDefaults.install();
        return false;
    })).isInstanceOf(ThemeController.InstallationFailure.class);
    assertThat(UIManager.getLookAndFeel()).isSameAs(before);
    assertThat(MetalLookAndFeel.getCurrentTheme()).isSameAs(beforeMetal);
    assertThat(UIManager.getBoolean("Jasper.retro")).isFalse();
}
```

In `JasperApplication`, remove the `new ThemeController()` field initializer. At the first line of the full constructor, before Buddy construction, set:

```java
ConfigSnapshot startup = service == null ? ConfigSnapshot.defaults() : service.initialState().snapshot();
themes = new ThemeController(startup.style(), startup.variant());
```

- [x] **Step 5: Derive the restart warning from saved versus captured style.** Replace `ConfigurationController.shown()` with:

```java
ConfigService.State shown() {
    requireEdt();
    var merged = new java.util.ArrayList<>(state.diagnostics());
    merged.addAll(reported);
    if (state.snapshot().style() != themes.style())
        merged.add(new dev.jasper.app.config.ConfigDiagnostic(
            dev.jasper.app.config.ConfigDiagnostic.Severity.WARNING,
            state.file(), 0, 0, "ui.theme.style", "Restart Jasper to apply ui.theme.style."));
    return new ConfigService.State(state.snapshot(), merged, state.file(), state.present());
}
```

No `accept()` change to style, no new restart command. The initial constructor still applies saved variant through `configure`; in retro it only retains saved state. App plugin/Buddy wiring already uses `chrome()==DARK`, which correctly reports false in retro; retain it. The title-bar/palette checks using `chrome()==LIGHT` are updated in Tasks 4/6.

In the modern-only tests/previews listed above, replace `BuiltinTheme.values()` with `java.util.List.of(BuiltinTheme.DARK, BuiltinTheme.LIGHT)`; these tests assert modern-specific geometry/colors, while new tests cover retro separately. In `CommandPalettePreview`'s exhaustive theme-name switch add `case RETRO -> "retro";` so all test sources still compile. This is a test-source enum migration, not permission to run the preview's GUI.

- [x] **Step 6: Run GREEN and commit.**

```bash
./gradlew :jasper-app:test --tests '*RetroThemeTest' --tests '*ThemeStateTest' --tests '*ThemeControllerTest' --tests '*BrandedButtonsTest'
git add jasper-app/src/main/java/dev/jasper/app/appearance jasper-app/src/main/java/dev/jasper/app/platform/SwingAppearance.java jasper-app/src/main/java/dev/jasper/app/application/JasperApplication.java jasper-app/src/main/java/dev/jasper/app/application/ConfigurationController.java jasper-app/src/test/java/dev/jasper/app/appearance jasper-app/src/test/java/dev/jasper/app/application/ConfigurationTestSupport.java jasper-app/src/test/java/dev/jasper/app/workspace/ConfigurationControllerTest.java jasper-app/src/test/java/dev/jasper/app/palette/CommandPaletteTest.java jasper-app/src/test/java/dev/jasper/app/workspace/ThemeControllerTest.java jasper-app/src/test/java/dev/jasper/app/workspace/ConfigurationStatusTest.java jasper-app/src/test/java/dev/jasper/app/workspace/MacTitleBarTest.java jasper-app/src/test/java/dev/jasper/app/workspace/SplitDividerTest.java jasper-app/src/test/java/dev/jasper/app/workspace/MockUiPreview.java jasper-app/src/test/java/dev/jasper/app/workspace/TitleBarPreview.java jasper-app/src/test/java/dev/jasper/app/workspace/CommandPalettePreview.java
git commit -m "feat: resolve retro Metal appearance for the process lifetime" -m "Co-Authored-By: Codex <noreply@openai.com>"
```

### Task 3: Bundle a self-contained GNOME 2 icon subset

**Files:** Create `platform/GnomeIcons.java`, `platform/GnomeIconsTest.java`, bundled `icons/gnome2` resources; modify `platform/AppIcons.java` and its tests.

**Interfaces:** `AppIcons.icon(String)` returns `javax.swing.Icon`; it accepts its existing ten semantic names plus `close`. `AppIcons.themed(ClassLoader,String)` retains its exact contract. No plugin-path remapping.

- [x] **Step 1: Resolve the packaging prerequisite.** Obtain source/author/license evidence for the exact OldGNOME2 files, record the original source URL and license text in resource `NOTICE.md` and `LICENSE.txt`. Use the original distribution's notices; do not copy a license from a different GNOME theme. If unavailable, report this concrete blocker and do not redistribute the icons. Continue independent code work if useful, but do not mark this task or the feature complete.

The checked source mapping is:

| App name | Path under each source size directory |
|---|---|
| square-plus | actions/tab-new.png |
| app-window | actions/window-new.png |
| columns-2 | stock/table/stock_table-split.png |
| maximize | actions/view-fullscreen.png |
| search | actions/system-search.png |
| settings | actions/gtk-preferences.png |
| refresh | actions/view-refresh.png |
| command | actions/gtk-execute.png |
| history | stock/navigation/stock_undo-history.png |
| bookmark | places/user-bookmarks.png |
| close | stock/generic/stock_close.png |

- [x] **Step 2: Add `GnomeIconsTest` on EDT, then run RED.**

```java
@Test void everyBundledIconHasRealImagesAndPaintsAtBothScales() {
    for (String name : java.util.List.of("square-plus", "app-window", "columns-2", "maximize",
            "search", "settings", "refresh", "command", "history", "bookmark", "close")) {
        var icon = GnomeIcons.icon(name);
        assertThat(icon.getIconWidth()).isEqualTo(16);
        assertThat(icon.getIconHeight()).isEqualTo(16);
        for (int scale : new int[]{1, 2}) {
            var image = new java.awt.image.BufferedImage(16 * scale, 16 * scale,
                java.awt.image.BufferedImage.TYPE_INT_ARGB);
            var graphics = image.createGraphics();
            try { graphics.scale(scale, scale); icon.paintIcon(new javax.swing.JLabel(), graphics, 0, 0); }
            finally { graphics.dispose(); }
            boolean painted = false;
            for (int y = 0; y < image.getHeight(); y++)
                for (int x = 0; x < image.getWidth(); x++) painted |= (image.getRGB(x, y) >>> 24) != 0;
            assertThat(painted).as(name + " at " + scale).isTrue();
        }
    }
}
```

```bash
./gradlew :jasper-app:test --tests '*GnomeIconsTest'
```

- [x] **Step 3: Copy verified assets as bytes, with a hash manifest.** Run after Step 1; use a quoted heredoc. This code creates regular files even if the source names are symlinks.

```python
from pathlib import Path
import hashlib
import shutil
source = Path.home() / "Downloads/OldGNOME2"
target = Path("jasper-app/src/main/resources/dev/jasper/app/icons/gnome2")
mapping = {
    "square-plus": "actions/tab-new.png", "app-window": "actions/window-new.png",
    "columns-2": "stock/table/stock_table-split.png", "maximize": "actions/view-fullscreen.png",
    "search": "actions/system-search.png", "settings": "actions/gtk-preferences.png",
    "refresh": "actions/view-refresh.png", "command": "actions/gtk-execute.png",
    "history": "stock/navigation/stock_undo-history.png", "bookmark": "places/user-bookmarks.png",
    "close": "stock/generic/stock_close.png",
}
rows = ["resource\tsource\tsha256"]
for size in (16, 24):
    for name, relative in mapping.items():
        original = source / f"{size}x{size}" / relative
        destination = target / str(size) / f"{name}.png"
        assert original.is_file(), original
        data = original.read_bytes()
        assert data[:8] == b"\x89PNG\r\n\x1a\n", original
        destination.parent.mkdir(parents=True, exist_ok=True)
        destination.write_bytes(data)
        rows.append(f"{size}/{name}.png\t{size}x{size}/{relative}\t{hashlib.sha256(data).hexdigest()}")
(target / "assets.tsv").write_text("\n".join(rows) + "\n", encoding="utf-8")
```

- [x] **Step 4: Implement the PNG loader and mode dispatch.**

```java
package dev.jasper.app.platform;

import java.awt.Component;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import javax.imageio.ImageIO;
import javax.swing.Icon;

/** Self-contained, full-color raster artwork. Source and license travel with the resources. */
final class GnomeIcons {
    static final Set<String> NAMES = Set.of("square-plus", "app-window", "columns-2", "maximize",
        "search", "settings", "refresh", "command", "history", "bookmark", "close");
    private static final Map<String, Icon> CACHE = new ConcurrentHashMap<>();
    private GnomeIcons() {}
    static Icon icon(String name) {
        if (!NAMES.contains(name)) throw new IllegalArgumentException("Unknown application icon: " + name);
        return CACHE.computeIfAbsent(name, key -> new Raster(read(key, 16), read(key, 24)));
    }
    private static BufferedImage read(String name, int size) {
        String path = "/dev/jasper/app/icons/gnome2/" + size + "/" + name + ".png";
        try (var input = GnomeIcons.class.getResourceAsStream(path)) {
            if (input == null) throw new IllegalStateException("Missing bundled icon: " + path);
            var image = ImageIO.read(input);
            if (image == null || image.getWidth() != size || image.getHeight() != size)
                throw new IllegalStateException("Invalid bundled icon: " + path);
            return image;
        } catch (IOException failure) { throw new UncheckedIOException(failure); }
    }
    private record Raster(BufferedImage small, BufferedImage large) implements Icon {
        @Override public int getIconWidth() { return 16; }
        @Override public int getIconHeight() { return 16; }
        @Override public void paintIcon(Component component, Graphics graphics, int x, int y) {
            var g = (Graphics2D) graphics.create();
            try {
                var transform = g.getTransform();
                double scale = Math.max(Math.hypot(transform.getScaleX(), transform.getShearY()),
                    Math.hypot(transform.getShearX(), transform.getScaleY()));
                var image = scale <= 1 ? small : large;
                g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
                g.drawImage(image, x, y, 16, 16, component);
            } finally { g.dispose(); }
        }
    }
}
```

In `AppIcons.icon`, use `GnomeIcons.NAMES` for validation, return `GnomeIcons.icon(name)` when `SwingAppearance.retro()`, otherwise use the existing SVG path/filter. Map modern `close` to the existing `dev/jasper/app/icons/title/x.svg`. Preserve all ten existing modern paths. Change only the return type to Swing `Icon`; update tests that call concrete SVG methods to explicitly inspect the modern implementation. Unknown names still throw.

Extend the resource test to parse every `assets.tsv` row, assert `MessageDigest` SHA-256 equals the manifest and `ImageIO` dimensions match the directory, and require nonempty LICENSE/NOTICE resources. The byte/hash assertion is:

```java
assertThat(java.util.HexFormat.of().formatHex(
    java.security.MessageDigest.getInstance("SHA-256").digest(bytes))).isEqualTo(expectedHash);
```

Keep `AppIcons.themed` using `FlatSVGIcon`; the existing foreground filter now reads the Metal-supplied `Jasper.chromeForeground` alias. Extend `AppIconsThemedTest` to install retro before its existing real-SVG painting check.

- [x] **Step 5: Run GREEN and commit approved assets with code.**

```bash
./gradlew :jasper-app:test --tests '*GnomeIconsTest' --tests '*AppIconsTest' --tests '*AppIconsThemedTest'
git add jasper-app/src/main/java/dev/jasper/app/platform/AppIcons.java jasper-app/src/main/java/dev/jasper/app/platform/GnomeIcons.java jasper-app/src/main/resources/dev/jasper/app/icons/gnome2 jasper-app/src/test/java/dev/jasper/app/platform
git commit -m "feat: bundle GNOME 2 artwork for retro application icons" -m "Co-Authored-By: Codex <noreply@openai.com>"
```

### Task 4: Stock Metal tabs over the existing workspace model

**Files:** Create `workspace/RetroTabs.java` and `workspace/RetroTabsTest.java`; modify `workspace/{TerminalDeck,WindowContent}.java`. Add the two deferred restart/owner tests from Task 2 to `workspace/ConfigurationControllerTest.java` now.

**Interfaces:** Produces package-private `RetroTabs(WindowContent)`, `refresh()`, `close()`. `WindowContent.retro()` reads the captured style. `WindowTabs` exists only in modern; all references to it are guarded. The existing JTabbedPane, TerminalTab and TerminalPane instances remain authoritative.

- [x] **Step 1: Add an EDT workspace test with these methods.** Use `DesktopTestSupport.content(launcher(pending), themes)` so launches remain queued; do not drain the queue. `closeOwners()` is the existing cleanup fixture. Restore a modern ThemeController after each test on EDT.

```java
@Test void metalTabsKeepTheirModelAndHeaderIdentityAcrossUpdates() throws Exception {
    edt(() -> {
        var owner = content(launcher(new java.util.ArrayDeque<>()),
            new ThemeController(ThemeStyle.RETRO, Appearance.DARK));
        var deck = owner.tabStrip();
        assertThat(deck.getUI()).isInstanceOf(javax.swing.plaf.metal.MetalTabbedPaneUI.class);
        assertThat(owner.windowTabs()).isNull();
        var first = owner.currentTab();
        var pane = owner.currentPane();
        var header = deck.getTabComponentAt(0);
        assertThat(header).isNotNull();
        first.rename("<html>literal & long title"); owner.update();
        owner.newTab(HOME);
        var second = owner.currentTab();
        owner.reorderTab(0, 1);
        assertThat(deck.getTabComponentAt(1)).isSameAs(header);
        owner.selectTab(first);
        assertThat(owner.currentPane()).isSameAs(pane);
        assertThat(deck.getTitleAt(1)).isEqualTo("<html>literal & long title");
        var label = (javax.swing.JLabel) ((javax.swing.JPanel) header).getComponent(0);
        assertThat(label.getClientProperty("html.disable")).isEqualTo(true);
        assertThat(label.getToolTipText()).isEqualTo(first.title());
        var close = (javax.swing.JButton) ((javax.swing.JPanel) deck.getTabComponentAt(0)).getComponent(2);
        close.doClick();
        assertThat(deck.getTabCount()).isEqualTo(1);
        assertThat(owner.currentTab()).isSameAs(first);
        assertThat(deck.indexOfComponent(second)).isEqualTo(-1);
    });
}

@Test void retroTabsUseMouseGesturesAndScrollForOverflow() throws Exception {
    edt(() -> {
        var owner = content(launcher(new java.util.ArrayDeque<>()),
            new ThemeController(ThemeStyle.RETRO, Appearance.LIGHT));
        var first = owner.currentTab();
        owner.newTab(HOME);
        var deck = owner.tabStrip();
        deck.setSize(650, 300); deck.doLayout();
        var header = (javax.swing.JComponent) deck.getTabComponentAt(0);
        var target = deck.getBoundsAt(1);
        var end = javax.swing.SwingUtilities.convertPoint(deck, target.x + target.width / 2,
            target.y + target.height / 2, header);
        header.dispatchEvent(new java.awt.event.MouseEvent(header, java.awt.event.MouseEvent.MOUSE_PRESSED,
            1L, 0, 4, 4, 1, false, java.awt.event.MouseEvent.BUTTON1));
        header.dispatchEvent(new java.awt.event.MouseEvent(header, java.awt.event.MouseEvent.MOUSE_RELEASED,
            2L, 0, end.x, end.y, 1, false, java.awt.event.MouseEvent.BUTTON1));
        assertThat(deck.getComponentAt(1)).isSameAs(first);
        assertThat(owner.currentTab()).isSameAs(first);
        header.dispatchEvent(new java.awt.event.MouseEvent(header, java.awt.event.MouseEvent.MOUSE_PRESSED,
            3L, 0, 4, 4, 1, false, java.awt.event.MouseEvent.BUTTON2));
        assertThat(deck.indexOfComponent(first)).isEqualTo(-1);
        for (int i = 0; i < 20; i++) owner.newTab(HOME);
        assertThat(deck.getTabLayoutPolicy()).isEqualTo(javax.swing.JTabbedPane.SCROLL_TAB_LAYOUT);
    });
}
```

Add a close-during-drag case by pressing the header, closing its tab through `owner.closeTab(first)`, then releasing over a surviving tab; assert the survivor's identity/count are unchanged and no exception. Header callbacks must use the current index, not an index captured at construction.

- [x] **Step 2: Run RED.**

```bash
./gradlew :jasper-app:test --tests '*RetroTabsTest'
```

- [x] **Step 3: Let the deck choose the installed delegate.** Add the `SwingAppearance` import and replace `TerminalDeck` with:

```java
package dev.jasper.app.workspace;

import com.formdev.flatlaf.ui.FlatTabbedPaneUI;
import dev.jasper.app.platform.SwingAppearance;
import java.awt.Insets;
import javax.swing.JTabbedPane;

/** One retained selection/content model, with stock Metal tabs or the modern external strip. */
final class TerminalDeck extends JTabbedPane {
    TerminalDeck() {
        super(TOP, WRAP_TAB_LAYOUT);
        setTabLayoutPolicy(SwingAppearance.retro() ? SCROLL_TAB_LAYOUT : WRAP_TAB_LAYOUT);
        putClientProperty("html.disable", true);
    }
    @Override public void updateUI() {
        if (SwingAppearance.retro()) { super.updateUI(); return; }
        setUI(new FlatTabbedPaneUI() {
            @Override protected boolean hideTabArea() { return true; }
            @Override protected Insets getTabAreaInsets(int placement) { return new Insets(0, 0, 0, 0); }
        });
        putClientProperty("JTabbedPane.hasFullBorder", false);
        putClientProperty("JTabbedPane.tabAreaInsets", new Insets(0, 0, 0, 0));
    }
}
```

- [x] **Step 4: Add headers and gestures without a duplicate tab list.**

```java
package dev.jasper.app.workspace;

import dev.jasper.app.platform.AppIcons;
import java.awt.FlowLayout;
import java.awt.Point;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.IdentityHashMap;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;

/** Metal tab headers; the owner retains every tab, selection and session lifetime. */
final class RetroTabs implements AutoCloseable {
    private final WindowContent owner;
    private final IdentityHashMap<TerminalTab, Header> headers = new IdentityHashMap<>();
    private boolean closed;
    RetroTabs(WindowContent owner) { this.owner = owner; }
    void refresh() {
        if (closed) return;
        var deck = owner.tabStrip();
        headers.keySet().removeIf(tab -> deck.indexOfComponent(tab) < 0);
        for (int i = 0; i < deck.getTabCount(); i++) {
            var tab = (TerminalTab) deck.getComponentAt(i);
            var header = headers.computeIfAbsent(tab, Header::new);
            header.title.setText(tab.title());
            header.title.setToolTipText(tab.title());
            header.title.getAccessibleContext().setAccessibleName(tab.title());
            header.hint.setText(owner.tabShortcut(i));
            header.close.setToolTipText("Close " + tab.title());
            header.close.getAccessibleContext().setAccessibleName("Close " + tab.title());
            if (deck.getTabComponentAt(i) != header) deck.setTabComponentAt(i, header);
        }
    }
    @Override public void close() { closed = true; headers.clear(); }
    private final class Header extends JPanel {
        private final JLabel title = new JLabel(), hint = new JLabel();
        private final JButton close = new JButton(AppIcons.icon("close"));
        private Point origin;
        Header(TerminalTab tab) {
            super(new FlowLayout(FlowLayout.LEADING, 4, 0));
            setOpaque(false);
            title.putClientProperty("html.disable", true);
            hint.putClientProperty("html.disable", true);
            close.setFocusable(false);
            close.addActionListener(event -> { if (!closed) owner.closeTab(tab); });
            add(title); add(hint); add(close);
            var gestures = new MouseAdapter() {
                @Override public void mousePressed(MouseEvent event) {
                    origin = null;
                    if (closed || owner.tabStrip().indexOfComponent(tab) < 0) return;
                    if (SwingUtilities.isMiddleMouseButton(event)) { owner.closeTab(tab); return; }
                    if (SwingUtilities.isLeftMouseButton(event)) {
                        owner.selectTab(tab);
                        origin = SwingUtilities.convertPoint(event.getComponent(), event.getPoint(), owner.tabStrip());
                    }
                }
                @Override public void mouseReleased(MouseEvent event) {
                    Point start = origin; origin = null;
                    if (closed || start == null || !SwingUtilities.isLeftMouseButton(event)) return;
                    var deck = owner.tabStrip();
                    Point end = SwingUtilities.convertPoint(event.getComponent(), event.getPoint(), deck);
                    if (start.distance(end) <= 5) return;
                    owner.reorderTab(deck.indexOfComponent(tab), deck.indexAtLocation(end.x, end.y));
                }
            };
            addMouseListener(gestures); title.addMouseListener(gestures); hint.addMouseListener(gestures);
            close.addMouseListener(new MouseAdapter() {
                @Override public void mousePressed(MouseEvent event) {
                    if (!closed && SwingUtilities.isMiddleMouseButton(event)) owner.closeTab(tab);
                }
            });
        }
    }
}
```

- [x] **Step 5: Join the presentation to WindowContent.** Add the final `RetroTabs retroTabs` field and these helpers:

```java
boolean retro() { return themes.style() == dev.jasper.app.config.ThemeStyle.RETRO; }
private void refreshTabs() {
    if (retroTabs != null) retroTabs.refresh(); else windowTabs.refresh();
}
```

At tab-strip construction, use:

```java
windowTabs = retro() ? null : new WindowTabs(this, animationClock);
retroTabs = retro() ? new RetroTabs(this) : null;
var north = new JPanel(new BorderLayout());
if (windowTabs != null) north.add(windowTabs, BorderLayout.NORTH);
north.add(chrome.toolbar(), BorderLayout.CENTER);
```

Replace every direct `windowTabs.refresh()` call outside `refreshTabs()` with `refreshTabs()`. Guard `revalidate/repaint`, `setActive`, and `close` with `windowTabs != null`; close `retroTabs` when nonnull. Keep `windowTabs()` as a package-private accessor returning null for retro; production callers use it only after checking presentation. `setTabHeight` still remembers the value, but no longer drives a retro tab layout.

Pass `supported && !content.retro()` into `MacTitleBar.install` in `WindowContent.installTitleBar`; its existing unsupported path already sets the root content pane before returning null. This avoids constructing custom decorations and leaves title/close/focus callbacks intact. Change the `applyTheme` deck background to:

```java
tabs.setBackground(retro() ? UIManager.getColor("TabbedPane.background") : theme.palette().background());
```

Only the actual terminal tabs/panes keep the terminal palette. Task 5 handles the remaining status/toolbar painting.

- [x] **Step 6: Run GREEN, including the deferred restart tests, and commit.**

```bash
./gradlew :jasper-app:test --tests '*RetroTabsTest' --tests '*WindowTabsTest' --tests '*ConfigurationControllerTest'
git add jasper-app/src/main/java/dev/jasper/app/workspace/TerminalDeck.java jasper-app/src/main/java/dev/jasper/app/workspace/RetroTabs.java jasper-app/src/main/java/dev/jasper/app/workspace/WindowContent.java jasper-app/src/test/java/dev/jasper/app/workspace/RetroTabsTest.java jasper-app/src/test/java/dev/jasper/app/workspace/ConfigurationControllerTest.java
git commit -m "feat: render retro workspace tabs with stock Metal" -m "Co-Authored-By: Codex <noreply@openai.com>"
```

### Task 5: Ordinary Metal toolbar, rail, status and appearance commands

**Files:** Modify `workspace/{WindowChrome,WindowRail,WindowStatusBar,WindowCommands}.java`; create `workspace/RetroToolbar.java` and `workspace/RetroChromeTest.java`; extend `workspace/WindowChromeContributionsTest.java`.

**Interfaces:** `RetroToolbar` is package-private `JToolBar` with ordinary LAF painting and compact layout. Existing `ReferenceButton` remains the shared action/dropdown wiring but uses its JButton delegate in retro. Commands and contribution structures are unchanged.

- [x] **Step 1: Add concrete delegate/behavior tests.** In `RetroChromeTest`, use the same EDT/queued-launch/cleanup fixtures as Task 4.

```java
@Test void retroButtonsKeepMetalDelegatesAndToolbarModes() throws Exception {
    edt(() -> {
        var owner = content(launcher(new java.util.ArrayDeque<>()),
            new ThemeController(ThemeStyle.RETRO, Appearance.DARK));
        var toolbar = owner.toolbar();
        for (var child : toolbar.getComponents()) if (child instanceof javax.swing.JButton button) {
            assertThat(button.getUI()).isInstanceOf(javax.swing.plaf.metal.MetalButtonUI.class);
            assertThat(button.getBorder()).isNotNull();
            assertThat(button.isContentAreaFilled()).isTrue();
            assertThat(button.getFont()).isEqualTo(javax.swing.UIManager.getFont("Button.font"));
        }
        owner.setToolbarMode(dev.jasper.app.config.ToolbarMode.ICONS);
        assertThat(((javax.swing.JButton) toolbar.getComponent(0)).getText()).isNull();
        owner.setToolbarMode(dev.jasper.app.config.ToolbarMode.HIDDEN);
        assertThat(toolbar.isVisible()).isFalse();
        owner.setToolbarMode(dev.jasper.app.config.ToolbarMode.ICONS_AND_LABELS);
        assertThat(toolbar.isVisible()).isTrue();
        ((javax.swing.JButton) toolbar.getComponent(0)).doClick();
        assertThat(owner.tabStrip().getTabCount()).isEqualTo(2);
        assertThat(owner.status().getBackground()).isEqualTo(javax.swing.UIManager.getColor("Panel.background"));
        assertThat(owner.windowCommands().view("view.appearance.dark").isEnabled()).isFalse();
        assertThat(owner.windowCommands().view("view.appearance.light").isEnabled()).isFalse();
        assertThat(owner.windowCommands().view("view.tab_height").isEnabled()).isFalse();
    });
}
```

In the existing contribution tests, run toolbar button/dropdown/disabled-action cases with a retro controller as well as modern. Keep the model registrations and handler assertions; assert enabled state and actual invocation count, not only delegate classes. Add a minimum-width render asserting every visible button has positive width at least its icon+insets and remains within toolbar bounds. Use the toolbar's reported minimum width; at widths below minimum, menu actions remain available as in modern.

- [x] **Step 2: Run RED.**

```bash
./gradlew :jasper-app:test --tests '*RetroChromeTest'
```

- [x] **Step 3: Add the plain toolbar with compact labels.**

```java
package dev.jasper.app.workspace;

import java.awt.Component;
import java.awt.Dimension;
import javax.swing.JButton;
import javax.swing.JToolBar;

/** Normal Metal toolbar painting/layout, with app label preferences and icon-only compaction. */
final class RetroToolbar extends JToolBar {
    private boolean layingOut;
    private boolean labels = true;
    void labels(boolean value) { labels = value; revalidate(); }
    @Override public void doLayout() {
        if (layingOut) { super.doLayout(); return; }
        layingOut = true;
        try {
            texts(labels);
            if (labels && super.getPreferredSize().width > getWidth()) texts(false);
            super.doLayout();
        } finally { layingOut = false; }
    }
    private void texts(boolean visible) {
        for (Component child : getComponents()) if (child instanceof JButton button)
            button.setText(visible ? (String) button.getClientProperty("label") : null);
    }
    @Override public Dimension getMinimumSize() {
        var edge = getInsets();
        int width = edge.left + edge.right, height = 0;
        for (Component child : getComponents()) {
            if (!child.isVisible()) continue;
            Dimension size = child.getMinimumSize();
            if (child instanceof JButton button) {
                var insets = button.getInsets();
                width += insets.left + insets.right + (button.getIcon() == null ? 0 : button.getIcon().getIconWidth());
            } else width += size.width;
            height = Math.max(height, size.height);
        }
        return new Dimension(width, height + edge.top + edge.bottom);
    }
}
```

Assign `WindowChrome.toolbar` in its constructor to `owner.retro() ? new RetroToolbar() : new ReferenceToolbar()`. Remove the field initializer. Do not clear the toolbar border in retro. Replace each `toolbar.add(new ToolbarSeparator())` with a helper:

```java
private void addToolbarSeparator() {
    if (owner.retro()) toolbar.addSeparator(); else toolbar.add(new ToolbarSeparator());
}
```

Keep `ReferenceButton` action construction and dropdown handlers. At the start of its `getPreferredSize()` and `paintComponent()` insert:

```java
// getPreferredSize:
if (dev.jasper.app.platform.SwingAppearance.retro()) return super.getPreferredSize();
// paintComponent:
if (dev.jasper.app.platform.SwingAppearance.retro()) { super.paintComponent(graphics); return; }
```

In both `addButton` and `contributedButton`, wrap empty-border/content-area/icon-gap/forced-font mutations in `if (!owner.retro())`. Keep icon, action, `label` property, tooltip, accessible name, and terminal-focus policy in both. In `refreshTheme`, only set the custom chrome font for modern ReferenceButtons; retro JButton fonts come from their installed delegates. Change status background assignment to `owner.retro() ? UIManager.getColor("Panel.background") : owner.theme().palette().background()`.

In `setToolbarMode`, after applying the current text and visibility behavior, add:

```java
if (toolbar instanceof RetroToolbar retro) retro.labels(mode == ToolbarMode.ICONS_AND_LABELS);
```

- [x] **Step 4: Preserve stock fonts/borders in rail and status.** In `WindowRail.style`, apply the `JButton.buttonType` property and zero margin only when `!SwingAppearance.retro()`; keep icons/actions/tooltips/selection. In `WindowStatusBar`, apply empty button border/content-area/opacity overrides only in modern. Select fonts through:

```java
private static java.awt.Font statusFont() {
    var font = UIManager.getFont("Label.font");
    return dev.jasper.app.platform.SwingAppearance.retro() ? font : font.deriveFont(UIScale.scale(10f));
}
```

Use `statusFont()` for metadata labels; for actionable buttons in retro retain `Button.font` instead. Make `Segment.getPreferredSize()` use the max of its children's preferred heights and the existing modern 30-pixel floor so default Metal fonts/borders cannot clip. Keep the existing pending-configuration button and accessibility description.

In `WindowCommands.update`, set availability in the existing loop and for tab height:

```java
for (var appearance : Appearance.values()) {
    var action = view("view.appearance." + appearance.name().toLowerCase(java.util.Locale.ROOT));
    action.putValue(Action.SELECTED_KEY, owner.appearance() == appearance);
    action.setEnabled(!owner.retro());
}
view("view.tab_height").setEnabled(!owner.retro());
```

The exact owner method is `WindowCommands.refresh()`: replace its final appearance-selection loop with the code above. In `WindowChrome`'s Appearance menu, append a disabled `JMenuItem("Retro uses light Metal; change style in Settings and restart.")` only in retro. Do not add a misleading live style selector.

- [x] **Step 5: Run GREEN and commit.**

```bash
./gradlew :jasper-app:test --tests '*RetroChromeTest' --tests '*WindowChromeTest' --tests '*WindowChromeHintTest' --tests '*WindowChromeContributionsTest' --tests '*ConfigurationStatusTest'
git add jasper-app/src/main/java/dev/jasper/app/workspace/WindowChrome.java jasper-app/src/main/java/dev/jasper/app/workspace/RetroToolbar.java jasper-app/src/main/java/dev/jasper/app/workspace/WindowRail.java jasper-app/src/main/java/dev/jasper/app/workspace/WindowStatusBar.java jasper-app/src/main/java/dev/jasper/app/workspace/WindowCommands.java jasper-app/src/test/java/dev/jasper/app/workspace/RetroChromeTest.java jasper-app/src/test/java/dev/jasper/app/workspace/WindowChromeContributionsTest.java
git commit -m "feat: use Metal controls throughout retro workspace chrome" -m "Co-Authored-By: Codex <noreply@openai.com>"
```

### Task 6: Palette and plugin surfaces follow Metal without SDK changes

**Files:** Modify `windows/NativeShells.java`, `palette/CommandPalette.java`, `workspace/WindowCommandPalette.java`, documentation in `jasper-sdk/src/main/java/dev/jasper/sdk/Variant.java`; extend `windows/NativeShellsChromeTest.java`, `plugins/HostedUiTest.java`, `application/JasperApplicationPluginsTest.java`, `palette/CommandPaletteTest.java`, `workspace/{WindowPanelsTest,WindowStatusBarContributionsTest,ConfigurationControllerTest}.java`.

**Interfaces:** Existing `Appearance.icon`, `Variant`, events, factories, surface handles, palette controllers and plugin contribution values remain binary-compatible. NativeShells' headless title-bar seam becomes style-aware; no native peer is created in its tests.

- [x] **Step 1: Add the auxiliary-window policy regression.** `NativeShellsChromeTest` already executes on EDT and constructs `AuxiliarySurface` without native windows. Add:

```java
@Test void retroKeepsNormalWindowDecorationsForFramesAndDialogs() {
    new ThemeController(dev.jasper.app.config.ThemeStyle.RETRO, dev.jasper.app.config.Appearance.DARK);
    try {
        for (var kind : AuxiliarySurface.Kind.values()) {
            var surface = new AuxiliarySurface("dev.test.retro", "Retro editor", kind, true,
                new java.awt.Dimension(400, 300), null, null,
                ignored -> { throw new AssertionError("No native shell in tests"); });
            var content = new javax.swing.JPanel(); surface.setContent(content);
            var root = new javax.swing.JRootPane();
            assertThat(NativeShells.installTitleBar(root, surface, true)).isNull();
            assertThat(root.getContentPane()).isSameAs(surface.holder());
            assertThat(content.getParent()).isSameAs(surface.holder());
            assertThat(root.getClientProperty("apple.awt.fullWindowContent")).isNull();
            assertThat(root.getClientProperty("apple.awt.transparentTitleBar")).isNull();
            assertThat(root.getClientProperty("apple.awt.windowTitleVisible")).isNull();
        }
    } finally { new ThemeController(); }
}
```

Add the matching root-content assertion to `RetroTabsTest` by calling `WindowContent.installTitleBar(new JRootPane(), owner, true, titles::add)` with a local `List<String> titles`; assert null bar, content identity and updated native title after `rename`/`update`.

- [x] **Step 2: Add an actual plugin-start test, not just a synthetic variant comparison.** In `JasperApplicationPluginsTest`, build this SDK-only fixture with the existing `PluginJars` helper:

```java
private static final String RETRO_FIXTURE = """
    package fix.retro;
    import dev.jasper.sdk.plugin.Plugin;
    import dev.jasper.sdk.plugin.PluginContext;
    import java.nio.file.Files;
    import javax.swing.JButton;
    import javax.swing.UIManager;
    public final class Main implements Plugin {
        public void start(PluginContext context) throws Exception {
            Files.writeString(context.dataDirectory().resolve("appearance"),
                context.appearance().variant().name() + "\\n" +
                UIManager.getLookAndFeel().getClass().getName() + "\\n" +
                new JButton().getUI().getClass().getName());
        }
    }
    """;

@Test void pluginsStartWithLightMetalBeforeAnyWindowExists() throws Exception {
    var dirs = new AppDirs(home, home.resolve("config.toml"), home.resolve("logs"));
    java.nio.file.Files.writeString(dirs.configFile(), "ui.theme.style='retro'\nui.theme.variant='dark'\n");
    var dev = home.resolve("retro-plugin");
    PluginJars.build(dev, "retro.jar", PluginJars.descriptor("dev.example.retro", "1.0.0", "fix.retro.Main"),
        Map.of("fix.retro.Main", RETRO_FIXTURE), List.of());
    var service = new dev.jasper.app.config.ConfigService(dirs.configFile(), false);
    var terminated = new CountDownLatch(1);
    JasperApplication[] app = new JasperApplication[1];
    try {
        edt(() -> {
            app[0] = new JasperApplication(service, launcher(new ArrayDeque<>()), new CommandHistory(),
                null, terminated::countDown);
            app[0].startPlugins(null, dev, false, dirs);
        });
        assertThat(dirs.plugins().resolve("dev.example.retro/data/appearance")).hasContent(
            "LIGHT\njavax.swing.plaf.metal.MetalLookAndFeel\njavax.swing.plaf.metal.MetalButtonUI");
    } finally {
        if (app[0] != null) { edt(app[0]::quit); assertThat(terminated.await(5, TimeUnit.SECONDS)).isTrue(); }
        service.close();
        edt(() -> new dev.jasper.app.appearance.ThemeController());
    }
}
```

In `HostedUiTest` add an EDT test using its existing `ui`, `variant` and `open` fixture fields:

```java
@Test void metalKeepsPluginSvgAndSubscriptionContracts() {
    new dev.jasper.app.appearance.ThemeController(dev.jasper.app.config.ThemeStyle.RETRO,
        dev.jasper.app.config.Appearance.DARK);
    try {
        variant = Variant.LIGHT;
        assertThat(ui.appearance().variant()).isEqualTo(Variant.LIGHT);
        var icon = ui.appearance().icon("dev/jasper/app/icons/search.svg");
        var image = new java.awt.image.BufferedImage(32, 32, java.awt.image.BufferedImage.TYPE_INT_ARGB);
        var graphics = image.createGraphics();
        try { graphics.scale(2, 2); icon.paintIcon(new javax.swing.JLabel(), graphics, 0, 0); }
        finally { graphics.dispose(); }
        assertThat(icon.getIconWidth()).isEqualTo(16);
        assertThatIllegalArgumentException().isThrownBy(() -> ui.appearance().icon("absent.svg"));
        var events = new java.util.ArrayList<Variant>();
        var subscription = ui.appearance().onChanged(events::add);
        subscription.close();
        assertThat(themeHandlers).isEmpty();
    } finally { ui.closeAll(); new dev.jasper.app.appearance.ThemeController(); }
}
```

Run the existing plugin-window close-guard, native-picker ownership, and hosted-panel disposal tests unchanged as lifecycle regression coverage. Add this to `WindowPanelsTest`; its `toggle`, `edt`, and cleanup helpers already exist:

```java
@Test void retroHiddenPanelKeepsItsComponentAndHandlers() throws Exception {
    edt(() -> {
        var themes = new dev.jasper.app.appearance.ThemeController(
            dev.jasper.app.config.ThemeStyle.RETRO, dev.jasper.app.config.Appearance.DARK);
        var owner = DesktopTestSupport.content(DesktopTestSupport.launcher(new ArrayDeque<>()), themes);
        var model = new Contributions(); owner.connectContributions(model, UiState.inMemory());
        var clicks = new java.util.concurrent.atomic.AtomicInteger();
        var button = new javax.swing.JButton("Plugin action");
        button.addActionListener(event -> clicks.incrementAndGet());
        var instances = new java.util.concurrent.atomic.AtomicInteger();
        model.addPanel("dev.x.retro", "Retro panel", new ImageIcon(), PanelRegion.LEFT, site -> {
            instances.incrementAndGet(); return button;
        });
        toggle(model, owner, "dev.x.retro");
        toggle(model, owner, "dev.x.retro");
        owner.applyTheme(owner.theme(), true);
        toggle(model, owner, "dev.x.retro");
        assertThat(owner.regions().content(PanelRegion.LEFT)).isSameAs(button);
        assertThat(instances.get()).isEqualTo(1);
        assertThat(button.getUI()).isInstanceOf(javax.swing.plaf.metal.MetalButtonUI.class);
        button.doClick(); assertThat(clicks.get()).isEqualTo(1);
    });
}
```

Add the focused launched-session test to `ConfigurationControllerTest`; it uses the existing controlled `/bin/sh` fixture, not a native window or login shell:

```java
@Test @DisabledOnOs(OS.WINDOWS)
void retroStyleReloadKeepsALiveSessionAndAppliesFontChanges() throws Exception {
    start("ui.theme.style='retro'\n");
    edt(() -> owner()); launchAll();
    var pane = owners.getFirst().currentPane();
    var session = pane.session();
    reload("ui.theme.style='modern'\nfont.size=22\n");
    edt(() -> {
        assertThat(pane.session()).isSameAs(session);
        assertThat(pane.view().fontSize()).isEqualTo(22);
        assertThat(pane.view().palette().background()).isEqualTo(java.awt.Color.BLACK);
        assertThat(themes.current().chrome()).isEqualTo(BuiltinTheme.RETRO);
    });
}
```

Restore modern LAF on EDT after retro test cleanup (after owners close), so global Swing state does not leak between test classes. Do not create a new production fake-surface abstraction.

- [x] **Step 3: Add a palette test before changing its paint.** In `CommandPaletteTest`:

```java
@Test void retroQueryRetainsMetalBorderAndNativeEditing() throws Exception {
    SwingUtilities.invokeAndWait(() -> {
        new ThemeController(dev.jasper.app.config.ThemeStyle.RETRO, dev.jasper.app.config.Appearance.DARK);
        try {
            var changes = new ArrayList<String>();
            var palette = new CommandPalette(false, changes::add, (row, verb) -> {}, () -> {}, () -> {});
            assertThat(palette.queryField().getBorder()).isEqualTo(UIManager.getBorder("TextField.border"));
            assertThat(palette.queryField().getFont()).isEqualTo(UIManager.getFont("TextField.font"));
            assertThat(palette.queryField().getActionMap().get(DefaultEditorKit.deletePrevCharAction)).isNotNull();
            palette.queryField().setText("split");
            assertThat(changes).containsExactly("split");
            palette.setSize(palette.getPreferredSize()); palette.doLayout();
            var image = new BufferedImage(palette.getWidth(), palette.getHeight(), BufferedImage.TYPE_INT_ARGB);
            var graphics = image.createGraphics();
            try { palette.paint(graphics); } finally { graphics.dispose(); }
            assertThat(image.getRGB(2, 2) >>> 24).isEqualTo(255);
        } finally { new ThemeController(); }
    });
}
```

Add a retro case to existing step-form/result execution tests using the same real row/step values: assert successful submission and cancellation callbacks, default Metal text-field border, and literal result labels. This verifies behavior, beyond a screenshot of an empty card.

- [x] **Step 4: Run RED for the policy and palette regressions.**

```bash
./gradlew :jasper-app:test --tests '*NativeShellsChromeTest' --tests '*CommandPaletteTest'
```

- [x] **Step 5: Bypass custom auxiliary chrome and modern palette presentation.** In `NativeShells.installTitleBar`, pass `supported && !SwingAppearance.retro()` to the existing `MacTitleBar.install` call. Its fallback sets `surface.holder()` as root content. Keep surface title propagation, menu bars, file picker, ownership and disposal unchanged.

In `CommandPalette`, add:

```java
private static boolean retro() { return dev.jasper.app.platform.SwingAppearance.retro(); }
private static int radius(int modern) { return retro() ? 0 : modern; }
```

Replace the card call's literal radius `12` with `radius(12)`. Wrap the chip/selection/badge arc arguments with `radius(...)`, including values currently scaled by UIScale. Make the Escape hint border's rounded flag `!retro()`. Keep selection colors from Metal aliases. At the end of `refreshTheme()` restore stock controls:

```java
if (retro()) {
    query.setBorder(UIManager.getBorder("TextField.border"));
    query.setFont(UIManager.getFont("TextField.font"));
    chip.setFont(UIManager.getFont("Label.font"));
    footer.setFont(UIManager.getFont("Label.font"));
}
```

In `applyStepColors`, replace the custom field-border assignment with:

```java
if (retro()) {
    field.setBorder(UIManager.getBorder("TextField.border"));
    field.setFont(UIManager.getFont("TextField.font"));
} else {
    field.setBorder(BorderFactory.createCompoundBorder(
        BorderFactory.createLineBorder(borderColor, UIScale.scale(1), true),
        BorderFactory.createEmptyBorder(0, UIScale.scale(6), 0, UIScale.scale(6))));
}
```

At the start of `WindowCommandPalette.Overlay.paintComponent`, return immediately when `owner.retro()`; this component is the transparent shadow layer, and its children still paint normally. Keep all controller listeners, scope routing, focus restoration and layout bounds.

Update `Variant` enum Javadoc only:

```java
/** Dark application chrome; terminal colors may be configured independently. */
DARK,
/** Light application chrome; terminal colors may be configured independently. */
LIGHT
```

Do not bump SDK version/descriptor ranges for documentation. Audit all `BuiltinTheme.LIGHT` equality checks: actual brightness tests use `.appearance()==Appearance.LIGHT`; explicit modern selection and the modern FlatLaf install switch retain their intended meaning.

- [x] **Step 6: Run GREEN and commit.**

```bash
./gradlew :jasper-app:test --tests '*NativeShellsChromeTest' --tests '*HostedUiTest' --tests '*JasperApplicationPluginsTest' --tests '*CommandPaletteTest' --tests '*WindowPanelsTest' --tests '*WindowStatusBarContributionsTest' --tests '*ConfigurationControllerTest'
git add jasper-app/src/main/java/dev/jasper/app/windows/NativeShells.java jasper-app/src/main/java/dev/jasper/app/palette/CommandPalette.java jasper-app/src/main/java/dev/jasper/app/workspace/WindowCommandPalette.java jasper-sdk/src/main/java/dev/jasper/sdk/Variant.java jasper-app/src/test/java/dev/jasper/app/windows/NativeShellsChromeTest.java jasper-app/src/test/java/dev/jasper/app/plugins/HostedUiTest.java jasper-app/src/test/java/dev/jasper/app/application/JasperApplicationPluginsTest.java jasper-app/src/test/java/dev/jasper/app/palette/CommandPaletteTest.java jasper-app/src/test/java/dev/jasper/app/workspace/WindowPanelsTest.java jasper-app/src/test/java/dev/jasper/app/workspace/WindowStatusBarContributionsTest.java jasper-app/src/test/java/dev/jasper/app/workspace/ConfigurationControllerTest.java
git commit -m "feat: preserve plugin and palette behavior under Metal" -m "Co-Authored-By: Codex <noreply@openai.com>"
```

### Task 7: Render, verify packaged resources, document and review

**Files:** Extend `workspace/RetroChromeTest.java` with headless renders; update `docs/{configuration,plugin-authoring,app-architecture,STATUS}.md`, `jasper-app/README.md`, this plan's status banner. Resource notices must already be complete.

**Interfaces:** Produces inspected preview PNGs, passing headless/architecture checks, a distributable app with assets/notices, and an explicit native-acceptance handoff.

- [x] **Step 1: Add a deterministic lightweight render fixture.** In `RetroChromeTest`, loop over the three constructors below on EDT, create queued-launch owners through the existing fixture, give the first tab a long literal title, create a second tab, size the owner to 960x640, recursively lay it out, and save 1x/2x PNGs under the app's `build/retro-preview/`. Include a sibling JPanel form with JLabel, JTextField, JPasswordField, JComboBox and default/disabled JButtons in each render. Keep all windows unconstructed.

```java
var choices = java.util.List.of(
    java.util.Map.entry(ThemeStyle.MODERN, Appearance.DARK),
    java.util.Map.entry(ThemeStyle.MODERN, Appearance.LIGHT),
    java.util.Map.entry(ThemeStyle.RETRO, Appearance.LIGHT));
```

Close each owner before installing the next style. Use this complete helper to render each component, with `name` formed from style/variant/component/scale:

```java
private static void layoutTree(java.awt.Container container) {
    container.doLayout();
    for (var child : container.getComponents())
        if (child instanceof java.awt.Container nested) layoutTree(nested);
}
private static void saveRender(javax.swing.JComponent component, String name, int scale) {
    component.setSize(960, 640);
    layoutTree(component);
    var image = new java.awt.image.BufferedImage(960 * scale, 640 * scale,
        java.awt.image.BufferedImage.TYPE_INT_ARGB);
    var graphics = image.createGraphics();
    try { graphics.scale(scale, scale); component.printAll(graphics); }
    finally { graphics.dispose(); }
    try {
        var folder = java.nio.file.Path.of("build/retro-preview");
        java.nio.file.Files.createDirectories(folder);
        javax.imageio.ImageIO.write(image, "png", folder.resolve(name + ".png").toFile());
    } catch (java.io.IOException failure) { throw new java.io.UncheckedIOException(failure); }
}
```

Keep screenshot generation separate from assertions about exact anti-aliased pixels. Inspect images using `view_image`, checking Metal borders/fonts, readable status/selection, unclipped toolbar/tab controls, icon edges and modern appearance parity. Save palette and Vault form renders using their own package tests/helpers as well; do not import plugin internals into app production or introduce a plugin dependency on app test fixtures.

- [x] **Step 2: Add the exact user-facing configuration documentation.**

```markdown
### Retro Metal appearance

Set `style = "retro"` under `[ui.theme]`, then fully restart Jasper. Retro uses Java's stock light Metal/Ocean controls, GNOME 2 application icons, and a black high-contrast terminal. `style = "modern"` restores the current appearance. Existing configuration files default to modern.

Style changes take effect only after restarting the process. Reload keeps current sessions and windows as they are and shows a restart notice; changing the setting back clears that notice. When background residency is enabled, closing every window does not restart Jasper.

The saved `variant` remains available for modern mode. Retro always uses light controls, so Light/Dark and custom tab-height commands are unavailable there. Font, terminal cursor, toolbar visibility, keybinding and other live settings continue to work normally.

Plugins need no changes to use ordinary Metal controls. Their custom icons and custom-painted content remain plugin-owned. Native OS title bars and file dialogs remain native; the Jasper logo and Buddy artwork are unchanged.
```

Add this to `docs/configuration.md`; link it from app README and STATUS. Update plugin-authoring's form styling section to describe modern branded buttons versus stock Metal automatically provided by the host, and to define `Variant` as chrome brightness. Record the style lifetime and semantic-color aliases in app architecture. Do not describe new SDK APIs or live style switching.

- [x] **Step 3: Run the final integration gate.**

```bash
./gradlew verifyTerminalArchitecture verifyApplicationArchitecture verifySdkArchitecture verifyPluginArchitecture check :jasper-app:installDist
```

Expected: exit 0. Inspect all `*/build/test-results/test/TEST-*.xml`, report exact failures/errors/skips and counts, and use the known-flake policy from Task 0. No `:jasper-app:run` or benchmark.

- [x] **Step 4: Verify the distribution is self-contained.** Run this from repository root:

```python
from pathlib import Path
import hashlib
import zipfile
library = Path("jasper-app/build/install/jasper-app/lib")
prefix = "dev/jasper/app/icons/gnome2/"
candidates = []
for jar in library.glob("*.jar"):
    with zipfile.ZipFile(jar) as archive:
        if prefix + "assets.tsv" in archive.namelist():
            candidates.append(jar)
assert len(candidates) == 1, candidates
with zipfile.ZipFile(candidates[0]) as archive:
    for notice in ("LICENSE.txt", "NOTICE.md"):
        assert archive.read(prefix + notice).strip(), notice
    rows = archive.read(prefix + "assets.tsv").decode("utf-8").splitlines()[1:]
    assert len(rows) == 22, len(rows)
    for row in rows:
        resource, original, expected = row.split("\t")
        data = archive.read(prefix + resource)
        assert hashlib.sha256(data).hexdigest() == expected, resource
        assert data[:8] == b"\x89PNG\r\n\x1a\n", resource
print("Verified 22 bundled GNOME PNGs and their notices")
```

If the distribution directory name differs in the checked Gradle configuration, resolve it from the `installDist` task output and record that path; do not skip the jar-content assertions. Verify source hygiene using AGENTS.md's Python check and run `git diff --check`.

- [x] **Step 5: Record verification, commit documentation, and request the selected final review.**

```bash
git diff --check
git add docs/configuration.md docs/plugin-authoring.md docs/app-architecture.md docs/STATUS.md jasper-app/README.md docs/superpowers/plans/2026-09-22-jasper-retro-metal.md jasper-app/src/test/java/dev/jasper/app/workspace/RetroChromeTest.java
git commit -m "docs: document and verify retro Metal appearance" -m "Co-Authored-By: Codex <noreply@openai.com>"
```

Use the execution method chosen by the user after plan review. For native execution, one independent reviewer inspects the whole branch against both documents and the five Review Focus conditions. For subagent-driven execution, retain per-task implementation/review gates plus a whole-branch review. Fix actionable findings, rerun the affected checks, and record them. Do not merge or push as part of this plan without authorization.

## User-run native acceptance

1. Save retro config and fully restart. Confirm light Metal controls, GNOME application icons, black terminal and normal OS title bar. Confirm plugin startup, menu bar and keyboard focus.
2. Create/rename/select/reorder/close many tabs, including middle-click and long titles. Split and zoom panes; use Find, copy/paste, configured shortcuts and command-palette forms. Confirm running commands/session identities survive ordinary config reloads.
3. Exercise icon-only/label/hidden toolbar modes, minimum window size, rail movement and hidden panels. Open Plugins manager and Vault editor/manager dialogs. Check default/disabled buttons and native file-picker ownership/cancellation.
4. Change style to modern while retro runs. Confirm one restart notice, unchanged existing/new window style and continued live font/keybinding settings. Revert it and confirm the notice clears.
5. Request modern again and fully restart, including quitting any resident process. Confirm modern light/dark switching, custom tabs, branding and plugin behavior are unchanged. Return to retro in a fresh process and repeat on a Retina/2x display.

Native checks stay pending until the user reports results. Successful headless renders do not certify OS decoration/modality behavior.

## Plan self-review ledger

- Configuration/default/backward-constructor coverage: Task 1.
- Captured style, startup order, variant semantics, rollback and restart notice: Task 2; owner/session integration: Tasks 4 and 6.
- Asset names, local copy, hashes, size/paint checks and license prerequisite: Task 3; installed jar gate: Task 7.
- Same tab/session models, title safety, gestures and overflow: Task 4.
- Built-in/contributed toolbar, rail/status defaults and disabled appearance commands: Task 5.
- Native shell policy, palette controls/shadows, SDK-only startup and SVG compatibility: Task 6.
- Headless images, architecture/full checks, distribution, docs and native acceptance: Task 7.
- The original plan was written before implementation; execution status and evidence are recorded above and in STATUS.md.
