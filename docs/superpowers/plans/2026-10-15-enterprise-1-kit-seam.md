# Enterprise presentation 1: kit seam Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** jasper-ui gains the kit seam: a `dev.jasper.ui.kit.Kit` value (`Kit.DEFAULT`) that every `KitContext`, every
FlatLaf install, the native chrome painters and the app's former static metric finals read, with no painted value,
UIManager value or frozen table changed.

**Architecture:** `DesignMetrics` and the new `TypeScale` become instance values, `FaceSet.PLATFORM` names today's faces
and `Typography.of(scale, faces, user)` replaces the `Typography` constructors; `Kit(id, metrics, type, faces, uiKeys)`
bundles them in the new JDK-only package `dev.jasper.ui.kit`. `FlatLafInstaller.install(theme, kit)` derives FlatLaf's
colour keys from the theme at install time through the theme-local `UiKeySet` (so `GeneratedTheme` loses `uiKeys`),
takes its geometry from the kit and publishes the kit beside the theme as the look-and-feel default
`Jasper.installedKit`. App chrome reaches the kit through that installed appearance (`FlatLafInstaller.installedKit()`,
`ChromePainter.installed()`, `NativeStyles.installed()`), as it already reaches the installed theme. `StaticDefaultTest`,
a `java.lang.classfile` scan of the jasper-ui and jasper-app jars, keeps `DesignMetrics.DEFAULT` inside the catalog's
owners and `AnimationSettings`, and every static initializer free of metric reads.

**Tech Stack:** Java 25 on JetBrains Runtime 25 (`java.lang.classfile`), Swing and FlatLaf 3.7, Gradle 9.7 (Kotlin DSL
for `gradle/ui-architecture.gradle.kts`), JUnit 6.1.3 and AssertJ 3.27.7, Python 3 for the mechanical conversion scripts
(always `python3 -I`).

**Spec:** [`docs/superpowers/specs/2026-10-15-jasper-enterprise-presentation-design.md`](../specs/2026-10-15-jasper-enterprise-presentation-design.md)
(sections 2, 3, 5.4-5.6, 6.1-6.6, 16.1.7 and row 1 of 19.1).

**Workspace:** worktree `/Users/RQ7RQVF/projects/moray/.claude/worktrees/enterprise-presentation`, branch
`claude/enterprise-presentation` (spec approved at `0af7b83e2`). Run every command from the worktree root. Every line
number below is from `0af7b83e2` unless a step says it is from an earlier task's result, and every edit also quotes the
exact text it replaces. When a step edits several places in one file, make the edits from the bottom of the file up so
the cited line numbers still hold.

## Global Constraints

- Java 25 on the JetBrains Runtime 25 through the Gradle toolchain; run `./gradlew`, never a system `gradle`.
- Never launch the GUI (`:jasper-app:run`, `:jasper-app:bench`) and never train the AOT cache (AGENTS.md).
- Work only in the worktree above on `claude/enterprise-presentation`; one commit per task; every commit message ends with the two lines `🤖 Generated with Claude Code at The Home Depot` and `Co-Authored-By: Claude <noreply@anthropic.com>` separated by a blank line.
- Zero pixel change: every painted value, every existing UIManager key and value, every FlatLaf extra and every frozen table (`compact-roles.tsv`, `ui-color-keys.tsv`) stays identical, and no existing assertion's expected value changes.
- The one new UIManager entry is the look-and-feel default `Jasper.installedKit`, published with `Jasper.installedTheme` by every `SnapshotLaf.getDefaults()`.
- `Kit.DEFAULT` is `new Kit("default", DesignMetrics.DEFAULT, TypeScale.DEFAULT, FaceSet.PLATFORM, UiKeySet.DEFAULT)`; `Kit.DEFAULT.metrics()` is the same instance as `DesignMetrics.DEFAULT`.
- `FlatLafInstaller.installedKit()` returns `Kit.DEFAULT` when no Jasper look and feel is installed, never throws, and may be called off the EDT.
- The kit is process-wide and fixed before any window; plan 1 only ever installs `Kit.DEFAULT` in production (`JasperApplication` passes it to `ThemeController.startup`).
- jasper-ui depends on no other Jasper module; `dev.jasper.ui.theme`, `dev.jasper.ui.model` and the new `dev.jasper.ui.kit` are Swing-free and JDK-only; `kit` depends on `metrics` and `theme` only, `metrics` on `theme` only, `swing` gains `kit`; the package graph stays acyclic (`verifyUiArchitecture`).
- Outside `dev.jasper.ui.metrics` and `dev.jasper.ui.kit`, production code reads `DesignMetrics.DEFAULT` only in `AnimationSettings`' static initializer (its three tab durations, spec 6.6), never reads `TypeScale.DEFAULT` or `FaceSet.PLATFORM`, and no static initializer reads a metric or builds a painter (`StaticDefaultTest`).
- `ThemeManager` keeps `TitlePane.centerTitle` unconditional.
- `compact-roles.tsv` and `ui-color-keys.tsv` are not edited.
- Javadoc doclint fails the build: jasper-ui runs `-Xdoclint:all,-missing`, jasper-app `-Xdoclint:all`; every `{@link}` must resolve and no comment may contain a raw `<`, `>`, `&` or `->`.
- No colour literal in production code (`ColourLiteralScanTest`).
- Every new candidate source under jasper-app, the SDK, the testkit or plugins gets a `docs/design/theme-engine/migration.csv` row (`ThemeMigrationCoverageTest`), and a renamed source's row moves with it in the same commit.
- MVP (no compatibility shims): `new DesignMetrics()`, the `Typography` constructors, the `KitContext` constructors, `UiColorKeys.flatLaf(SemanticPalette, Color)`, `GeneratedTheme.uiKeys`, `FlatLafInstaller.install(GeneratedTheme)`, `ThemeManager.install(GeneratedTheme)`, `ThemeController.startup(boolean, ThemeSelection, Consumer)`, `IconRegistry.size(IconSize)` and the metric-less tile overloads, `NativeChromePainting` and `NativeControlStyles` are removed outright.
- Source hygiene (AGENTS.md): no raw control, private-use or unpaired surrogate characters in any source file.

## Review Focus

1. Something reads the installed kit before any Jasper look and feel exists or off the EDT (headless tests, `--no-window` paths, `AppIcons.named` and `PresentationIcon` from a plugin thread): it gets `Kit.DEFAULT` and never an exception. Task 6: `FlatLafInstallerKitTest.withoutAJasperLookAndFeelTheInstalledKitIsTheDefault`, `theInstalledKitCanBeReadOffTheEventThread`, and the Metal assertion in `JasperApplicationThemesTest`.
2. An appearance transaction fails after a look and feel with another kit was installed: the previous kit and its geometry come back with the previous theme. Task 6: `FlatLafInstallerKitTest.aFailedInstallRestoresThePreviousKitAndItsGeometry`.
3. A same-instance look-and-feel reinstall (a font change, `updateComponentTreeUI`, rollback): the same kit and the same install-time colour keys are republished, alpha included. Task 3: `ThemeManagerTest.keysFlatLafSkipsAreRestoredWithoutReplacingItsOwn`; Task 6: `FlatLafInstallerKitTest.theGeometryAndThePopupShadowFollowTheInstalledKit`.
4. User font settings through a kit: no font, size only, a named family with the override flag, and the platform family named without the flag (the calibration fixtures' mode) give exactly the fonts they gave before, and a user size wins over a kit's body. Task 7: `KitContextTest.typographyFollowsTheKitAndTheUserFontModes`.
5. The installed kit changes (plan 5's fallback reinstalls `Kit.DEFAULT` after a failed look): `ChromePainter.installed()` and `NativeStyles.installed()` follow at once, one instance per kit. Task 9: `NativeStylesTest.installedFollowsTheInstalledKitWithOneInstancePerKit`.

## Planned deviations

Record each of these in `docs/STATUS.md` (Task 11) and in this plan's status banner when it is executed.

1. **The installed-kit carrier.** Spec 6.6 says app chrome reads "the window's kit". Plan 1 reaches it through the
   installed appearance, exactly like the installed theme (`ThemeZone.theme` reads `FlatLafInstaller.installedTheme()`;
   "there is no second cache of installed appearance"): `FlatLafInstaller.install(theme, kit)` publishes the kit as the
   look-and-feel default `Jasper.installedKit`, `FlatLafInstaller.installedKit()` returns it (`Kit.DEFAULT` before any
   install), and `ChromePainter.installed()` / `NativeStyles.installed()` return one instance for the installed kit,
   cached by kit identity. The app's former static metric finals become live reads of the installed kit's metrics.
   `AppearanceBindings`, `HostedKit` and `PresentationIcons` build contexts with
   `KitContext.of(FlatLafInstaller.installedKit(), …)`. The kit is process-wide and fixed before any window, so this is
   the window's kit; plan 5 replaces the carrier with explicit `Presentation` plumbing. Tests construct
   `new ChromePainter(kit)` and `new NativeStyles(kit)`. Limit, and a plan-5 entry condition: an off-EDT reader during
   a rollback (`AppearanceTransaction`'s snapshot restore clears the look-and-feel defaults before it re-puts them) sees
   `Kit.DEFAULT`, and the icons that keep a rail size (`AppIcons`' cache, each `PresentationIcon`) keep whichever kit
   was installed when they were made; plan 5's `Presentation` plumbing must replace this carrier before a non-default
   kit is installed.
2. **Kit's plan-1 shape.** `Kit(String id, DesignMetrics metrics, TypeScale type, FaceSet faces, UiKeySet uiKeys)`;
   spec 6.1's `ControlVariants controls` and `ColorSlots colors` components and `Kit.CLASSIC` arrive in plan 4.
3. **Catalog and type values.** `DesignMetrics` gets `with(Role, int)` now; `with(MetricTokens)` and `CLASSIC` arrive in
   plan 4. `TypeScale` has `DEFAULT` and `withBody(float)` now (the seam tests use it); `CLASSIC` and `classic-type.tsv`
   arrive in plan 4. `UiKeySet` has `DEFAULT` only; `CLASSIC` and `ui-color-keys-classic.tsv` arrive in plan 4.
4. **Faces.** `FaceSet` carries only the data faces (regular and medium) and implies today's platform policy for the UI
   roles; it has no public constructor. The UI faces of spec 5.6 (regular, medium, semibold) and a way to build a set
   arrive in plan 5 with the Plex registration.
5. **The user's font.** `KitContext.typography()` is `Typography.of(kit.type(), kit.faces(), user)` where `user` is
   derived from the UIManager keys `ThemeController` already writes (`defaultFont`, `Label.font`,
   `Jasper.uiFontFamilyOverride`), not from configuration passed in; plan 5 decides whether that changes.
6. **Start-up.** `ThemeController.startup(dark, selection, kit, fallingBack)` captures the kit in its
   `Predicate<GeneratedTheme>` installer, so the Slate Cobalt fallback and every later `configure` install under the
   same kit. Spec 4.7's fallback chain (the same selection or Slate Cobalt under `Kit.DEFAULT`) is plan 5.
7. **Test-only behaviour.** A recoloured `GeneratedTheme` copy now installs FlatLaf keys from its own palette (it used to
   carry its base's); production installs only `JasperTheme` variants, whose install-time keys equal the keys they
   carried (Task 3 proves it for every built-in variant). `ThemeTestSupport.withUiKeys` is removed and its three users
   are re-expressed through roles or look-and-feel defaults.
8. **Additions to `StaticDefaultTest`.** Beyond spec 16.1.7 it also rejects `TypeScale.DEFAULT` and `FaceSet.PLATFORM`
   reads outside the catalog's owners, rejects painters built at class load, and pins the classes that read
   `Kit.DEFAULT` (`FlatLafInstaller`, `ThemeController`, `JasperApplication`), the inventory plan 5 shrinks.
9. **Small removals.** `TitleSearchField.ARC` (never read) is deleted. `PresentationIcon`, which the spec's list does not
   name, reads the installed kit's rail size because `IconRegistry.size` now takes the caller's metrics (spec 6.3).

---

### Task 1: Instance-valued DesignMetrics

`DesignMetrics` becomes an `int[]` indexed by role ordinal, built once from the declared values as `DEFAULT`, with no
public constructor and a copy-with method for derived catalogs. Every read already goes through `value(role)`, so no
caller changes. `compact-roles.tsv` keeps freezing `DEFAULT` unchanged.

**Files:**
- Modify: `jasper-ui/src/main/java/dev/jasper/ui/metrics/DesignMetrics.java:3-7` (imports, class comment), `:17` (`DEFAULT`), `:121-122` (the enum's field and constructor), `:124-125` (constructor and `value`), `:147` (the closing brace)
- Test: `jasper-ui/src/test/java/dev/jasper/ui/metrics/DesignMetricsTest.java` (three tests after line 78)

**Interfaces:**
- Consumes: nothing new.
- Produces: `public DesignMetrics with(DesignMetrics.Role role, int value)` (a copy with one role changed;
  `IllegalArgumentException("Negative <ROLE>: <value>")` for a negative value, `NullPointerException` for a null role);
  value equality (`equals`, `hashCode` over all roles); no public constructor; `DesignMetrics.DEFAULT` unchanged.

- [ ] **Step 1: Write the failing tests**

In `DesignMetricsTest.java`, insert after line 78 (the closing brace of `invalidFontSettingsAreRejected`):

```java

    /** A catalog is a value with no public constructor: every catalog derives from DEFAULT. */
    @Test void catalogsComeOnlyFromTheDefaultCatalog() {
        assertThat(DesignMetrics.class.getConstructors()).isEmpty();
    }

    /** {@code with} copies one role and leaves its source and every other role alone; equal catalogs are equal values. */
    @Test void withCopiesOneRole() {
        var tall = DesignMetrics.DEFAULT.with(DesignMetrics.Role.BUTTON_HEIGHT, 30);
        assertThat(tall.value(DesignMetrics.Role.BUTTON_HEIGHT)).isEqualTo(30);
        assertThat(DesignMetrics.DEFAULT.value(DesignMetrics.Role.BUTTON_HEIGHT)).isEqualTo(24);
        for (var role : DesignMetrics.Role.values())
            if (role != DesignMetrics.Role.BUTTON_HEIGHT)
                assertThat(tall.value(role)).as(role.name()).isEqualTo(DesignMetrics.DEFAULT.value(role));
        assertThat(tall).isNotEqualTo(DesignMetrics.DEFAULT);
        var same = DesignMetrics.DEFAULT.with(DesignMetrics.Role.BUTTON_HEIGHT, 24);
        assertThat(same).isEqualTo(DesignMetrics.DEFAULT).hasSameHashCodeAs(DesignMetrics.DEFAULT).isNotSameAs(DesignMetrics.DEFAULT);
        assertThat(DesignMetrics.DEFAULT.with(DesignMetrics.Role.TAB_SWITCH_MILLIS, 0).value(DesignMetrics.Role.TAB_SWITCH_MILLIS)).isZero();
        assertThatThrownBy(() -> DesignMetrics.DEFAULT.with(DesignMetrics.Role.BUTTON_HEIGHT, -1))
            .isInstanceOf(IllegalArgumentException.class).hasMessage("Negative BUTTON_HEIGHT: -1");
        assertThatThrownBy(() -> DesignMetrics.DEFAULT.with(null, 1)).isInstanceOf(NullPointerException.class);
    }

    /** Content heights read the catalog they are asked of: a derived floor or padding moves them. */
    @Test void contentHeightFollowsTheCatalog() {
        var frc = new FontRenderContext(null, true, true);
        var body = new java.awt.Font("SansSerif", java.awt.Font.PLAIN, 13);
        int line = (int) Math.ceil(body.getLineMetrics("Ag", frc).getHeight());
        int padding = DesignMetrics.DEFAULT.value(DesignMetrics.Role.FIELD_VERTICAL_PADDING);
        assertThat(DesignMetrics.DEFAULT.with(DesignMetrics.Role.FIELD_HEIGHT, 40).contentHeight(DesignMetrics.Role.FIELD_HEIGHT, body, frc))
            .isEqualTo(Math.max(40, line + padding));
        assertThat(DesignMetrics.DEFAULT.with(DesignMetrics.Role.FIELD_VERTICAL_PADDING, 20).contentHeight(DesignMetrics.Role.FIELD_HEIGHT, body, frc))
            .isEqualTo(Math.max(24, line + 20));
    }
```

- [ ] **Step 2: Run the tests to verify they fail**

Run: `./gradlew :jasper-ui:test --tests 'dev.jasper.ui.metrics.DesignMetricsTest'`
Expected: FAIL in `:jasper-ui:compileTestJava` with `error: cannot find symbol` / `symbol: method with(Role,int)`.

- [ ] **Step 3: Make the catalog an instance value**

In `DesignMetrics.java`, replace lines 3-7:

```java
import java.awt.Font;
import java.awt.font.FontRenderContext;
import java.util.Objects;

/** Fixed logical geometry. Display transforms belong to the host; font growth belongs to content bounds. */
```

with:

```java
import java.awt.Font;
import java.awt.font.FontRenderContext;
import java.util.Arrays;
import java.util.Objects;

/**
 * Logical geometry: one value per {@link Role}. {@link #DEFAULT} is the default catalog; other catalogs derive from it
 * with {@link #with(Role, int)}. Display transforms belong to the host; font growth belongs to content bounds.
 */
```

Replace line 17:

```java
    public static final DesignMetrics DEFAULT = new DesignMetrics();
```

with:

```java
    /** The default catalog: each role's declared value, frozen by compact-roles.tsv. */
    public static final DesignMetrics DEFAULT = new DesignMetrics(declared());
```

Replace lines 121-122 (the last two lines inside `enum Role`):

```java
        private final int value;
        Role(int value) { this.value = value; }
```

with:

```java
        /** The role's value in {@link DesignMetrics#DEFAULT}; every reading goes through a catalog. */
        private final int declared;
        Role(int value) { declared = value; }
```

Replace lines 124-125:

```java
    public DesignMetrics() {}
    public int value(Role role) { return Objects.requireNonNull(role).value; }
```

with:

```java
    private final int[] values;
    private DesignMetrics(int[] values) { this.values = values; }
    private static int[] declared() {
        var roles = Role.values();
        var values = new int[roles.length];
        for (var role : roles) values[role.ordinal()] = role.declared;
        return values;
    }
    /** {@code role}'s logical value in this catalog. */
    public int value(Role role) { return values[Objects.requireNonNull(role).ordinal()]; }
    /** A copy of this catalog with {@code role} set to {@code value}, which must not be negative. */
    public DesignMetrics with(Role role, int value) {
        Objects.requireNonNull(role);
        if (value < 0) throw new IllegalArgumentException("Negative " + role + ": " + value);
        int[] copy = values.clone();
        copy[role.ordinal()] = value;
        return new DesignMetrics(copy);
    }
```

Replace the last line of the file (line 147, the class's closing `}`) with:

```java
    @Override public boolean equals(Object other) { return other instanceof DesignMetrics metrics && Arrays.equals(values, metrics.values); }
    @Override public int hashCode() { return Arrays.hashCode(values); }
}
```

`contentPadding` and `contentHeight` are unchanged: `contentHeight` already reads through `value(...)`. The enum's 560
constants stay exactly as written, in order (`everyRoleHasItsCompactValue` checks the order against
`compact-roles.tsv`).

- [ ] **Step 4: Run the tests to verify they pass**

Run: `./gradlew :jasper-ui:test --tests 'dev.jasper.ui.metrics.DesignMetricsTest'`
Expected: PASS (8 tests).

- [ ] **Step 5: Run the module's checks**

Run: `./gradlew :jasper-ui:check verifyUiArchitecture :jasper-app:compileTestJava`
Expected: BUILD SUCCESSFUL. The repository has exactly one `new DesignMetrics()` (the old line 17), so nothing else
changes.

- [ ] **Step 6: Commit**

```bash
git add jasper-ui/src/main/java/dev/jasper/ui/metrics/DesignMetrics.java jasper-ui/src/test/java/dev/jasper/ui/metrics/DesignMetricsTest.java
git commit -F - <<'EOF'
Kit seam: DesignMetrics becomes an instance catalog

The catalog is an int array by role ordinal built once as DEFAULT, with
no public constructor, value equality and with(role, value) for derived
catalogs. Every read already went through value(role), and
compact-roles.tsv still freezes DEFAULT.

🤖 Generated with Claude Code at The Home Depot

Co-Authored-By: Claude <noreply@anthropic.com>
EOF
```

### Task 2: TypeScale, FaceSet and Typography.of

The role table moves out of `Typography.Role`'s fields into the value `TypeScale` (frozen by a new `default-type.tsv`),
`FaceSet.PLATFORM` names today's faces, and the three public constructors give way to
`Typography.of(TypeScale, FaceSet, Typography.UserFont)`. A test keeps today's resolution algorithm verbatim as an
oracle and checks that every role resolves to the same `Font` under every user-font mode.

**Files:**
- Create: `jasper-ui/src/main/java/dev/jasper/ui/metrics/TypeScale.java`, `jasper-ui/src/main/java/dev/jasper/ui/metrics/FaceSet.java`
- Modify: `jasper-ui/src/main/java/dev/jasper/ui/metrics/Typography.java` (whole file), `jasper-ui/src/main/java/dev/jasper/ui/metrics/package-info.java:2`, `jasper-ui/src/main/java/dev/jasper/ui/swing/SwingTypography.java` (whole file)
- Create: `jasper-ui/src/test/resources/dev/jasper/ui/metrics/default-type.tsv`
- Test: `jasper-ui/src/test/java/dev/jasper/ui/metrics/TypeScaleTest.java` (new), `jasper-ui/src/test/java/dev/jasper/ui/metrics/TypographyTest.java` (whole file)
- Modify tests: `jasper-ui/src/test/java/dev/jasper/ui/metrics/DesignMetricsTest.java:11,43,57,58,76,77`, `jasper-ui/src/test/java/dev/jasper/ui/testsupport/KitFixtures.java:16`, `jasper-ui/src/test/java/dev/jasper/ui/gallery/KitGallery.java:101`, `jasper-ui/src/test/java/dev/jasper/ui/swing/KitCalibrationTest.java:22,37,57`, `jasper-ui/src/test/java/dev/jasper/ui/swing/HeroCompositionTest.java:113`, `jasper-app/src/test/java/dev/jasper/app/appearance/UiTypographyTest.java:71`

**Interfaces:**
- Consumes: nothing from Task 1.
- Produces (all in `dev.jasper.ui.metrics`):
  - `public final class TypeScale`: `public static final TypeScale DEFAULT`; `float size(Typography.Role)`,
    `int weight(Typography.Role)`, `float base()`, `float body()`, `TypeScale withBody(float body)`
    (`IllegalArgumentException("Invalid body size: <body>")` unless finite and 6-96); value equality; no public
    constructor.
  - `public final class FaceSet`: `public static final FaceSet PLATFORM`; `String dataRegular()`, `String dataMedium()`
    (both `Font.MONOSPACED` in `PLATFORM`); no public constructor.
  - `public record Typography.UserFont(Optional<String> family, Optional<Float> size)` with `UserFont.NONE`.
  - `public static Typography Typography.of(TypeScale scale, FaceSet faces, Typography.UserFont user)`; no public
    constructor; `family()`, `bodySize()`, `font(Role)`, `font(Role, boolean)`, `resultFont(Role, boolean)` unchanged.
  - Package-private `SwingTypography.user()` returning the `UserFont` the UIManager keys describe.
  - Test support: `KitFixtures.platformFamily()`; `TypeScaleTest.defaultTable()` and `TypeScaleTest.Row(float size, int weight)`.

- [ ] **Step 1: Freeze today's type table**

Run (it writes 38 rows, `NAME<TAB>size<TAB>weight`, copied from `Typography.java:15-22` at `0af7b83e2`):

```bash
python3 -I - <<'PY'
import pathlib
rows = [("HERO_TITLE", "15", 600), ("DETAIL_TITLE", "16", 600), ("WINDOW_TITLE", "13", 600), ("PANEL_TITLE", "13", 600),
        ("PANEL_DETAIL_TITLE", "12.5", 600), ("PANEL_DETAIL_LABEL", "12", 400), ("PANEL_HEADER_TITLE", "12.5", 600),
        ("PANEL_HEADER_COUNT", "11.5", 400), ("PANEL_SEARCH", "12", 400), ("PANEL_ROW_SELECTED", "12", 500),
        ("PANEL_ROW_SECONDARY", "13.25", 400), ("PANEL_DETAIL_MONO", "11.65", 400), ("PANEL_EMPTY_DESCRIPTION", "12", 400),
        ("PANEL_ROW_CHIP", "10", 400), ("PICKER_COMPACT", "10", 400), ("PICKER_CAPTION", "9", 400), ("TITLE_CONTEXT", "12", 400),
        ("TOOLBAR_LABEL", "11.5", 400), ("STATUS_TEXT", "10", 400), ("STATUS_PATH", "12", 400), ("RESULTS_HEADING", "11", 400),
        ("PALETTE_HINT", "11", 400), ("BODY", "13", 400), ("BODY_STRONG", "13", 500), ("LABEL", "12.5", 400),
        ("DESCRIPTION", "12.5", 400), ("SETTINGS_BODY", "13", 400), ("SETTINGS_PREVIEW", "12", 400), ("SUPPORTING", "11.5", 400),
        ("CAPTION", "11.5", 400), ("SETTING_DESCRIPTION", "11", 400), ("SECTION_LABEL", "11", 600), ("COUNT", "11", 400),
        ("CHIP", "9.5", 400), ("MONO", "12", 400), ("MONO_SMALL", "11.5", 400), ("MONO_CAPTION", "11", 400),
        ("ORDERED_ACTION", "12", 400)]
assert len(rows) == 38
path = pathlib.Path("jasper-ui/src/test/resources/dev/jasper/ui/metrics/default-type.tsv")
path.write_text("".join(f"{name}\t{size}\t{weight}\n" for name, size, weight in rows), encoding="utf-8", newline="")
PY
```

- [ ] **Step 2: Write the failing tests**

Create `jasper-ui/src/test/java/dev/jasper/ui/metrics/TypeScaleTest.java`:

```java
package dev.jasper.ui.metrics;

import static org.assertj.core.api.Assertions.*;

import java.awt.Font;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** Enterprise presentation spec 5.6 and 6.2: a kit's type is a value, and the default scale is today's table. */
class TypeScaleTest {
    /** A role's size at the 13-point base and its weight. */
    record Row(float size, int weight) {}

    /** default-type.tsv: the table frozen when the scale became a value; a retune edits it in the same change. */
    static Map<String, Row> defaultTable() throws IOException {
        var table = new LinkedHashMap<String, Row>();
        try (var in = TypeScaleTest.class.getResourceAsStream("default-type.tsv")) {
            for (var line : new String(in.readAllBytes(), StandardCharsets.UTF_8).split("\n")) {
                var cells = line.split("\t");
                table.put(cells[0], new Row(Float.parseFloat(cells[1]), Integer.parseInt(cells[2])));
            }
        }
        return table;
    }

    @Test void theDefaultScaleIsTheFrozenThirteenPointTable() throws IOException {
        var table = defaultTable();
        assertThat(Arrays.stream(Typography.Role.values()).map(Enum::name).toList()).containsExactlyElementsOf(table.keySet());
        for (var role : Typography.Role.values()) {
            assertThat(TypeScale.DEFAULT.size(role)).as(role.name()).isEqualTo(table.get(role.name()).size());
            assertThat(TypeScale.DEFAULT.weight(role)).as(role.name()).isEqualTo(table.get(role.name()).weight());
        }
        assertThat(TypeScale.DEFAULT.base()).isEqualTo(13f);
        assertThat(TypeScale.DEFAULT.body()).isEqualTo(13f);
    }

    @Test void aBodyCopyKeepsTheTableAndChangesOnlyTheBody() {
        var large = TypeScale.DEFAULT.withBody(20);
        for (var role : Typography.Role.values()) {
            assertThat(large.size(role)).as(role.name()).isEqualTo(TypeScale.DEFAULT.size(role));
            assertThat(large.weight(role)).as(role.name()).isEqualTo(TypeScale.DEFAULT.weight(role));
        }
        assertThat(large.base()).isEqualTo(13f);
        assertThat(large.body()).isEqualTo(20f);
        assertThat(large).isNotEqualTo(TypeScale.DEFAULT);
        assertThat(TypeScale.DEFAULT.withBody(13)).isEqualTo(TypeScale.DEFAULT).hasSameHashCodeAs(TypeScale.DEFAULT)
            .isNotSameAs(TypeScale.DEFAULT);
        for (float body : new float[] {Float.NaN, Float.POSITIVE_INFINITY, 5.9f, 96.1f})
            assertThatThrownBy(() -> TypeScale.DEFAULT.withBody(body)).isInstanceOf(IllegalArgumentException.class)
                .hasMessageStartingWith("Invalid body size");
    }

    @Test void scalesAndFacesAreValuesWithoutPublicConstructors() {
        assertThat(TypeScale.class.getConstructors()).isEmpty();
        assertThat(FaceSet.class.getConstructors()).isEmpty();
        assertThat(FaceSet.PLATFORM.dataRegular()).isEqualTo(Font.MONOSPACED);
        assertThat(FaceSet.PLATFORM.dataMedium()).isEqualTo(Font.MONOSPACED);
    }
}
```

Replace the whole of `jasper-ui/src/test/java/dev/jasper/ui/metrics/TypographyTest.java` with (the five former tests
keep their assertions; only their construction changes, through the three helpers at the top):

```java
package dev.jasper.ui.metrics;

import java.awt.Font;
import java.awt.font.TextAttribute;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TypographyTest {
    /** Today's platform typography: the default scale and faces with no user setting. */
    static Typography platform() { return Typography.of(TypeScale.DEFAULT, FaceSet.PLATFORM, Typography.UserFont.NONE); }
    /** A size-only user setting, which keeps the platform family policy. */
    static Typography sized(float size) {
        return Typography.of(TypeScale.DEFAULT, FaceSet.PLATFORM, new Typography.UserFont(Optional.empty(), Optional.of(size)));
    }
    /** A named family and a size. */
    static Typography named(String family, float size) {
        return Typography.of(TypeScale.DEFAULT, FaceSet.PLATFORM, new Typography.UserFont(Optional.of(family), Optional.of(size)));
    }

    @Test void sectionAndTechnicalCaptionInkUseTheirOwnSourceCalibratedAdvances() {
        var typography=platform();
        org.junit.jupiter.api.Assumptions.assumeTrue(typography.family().equals("Helvetica Neue"));
        String[] sections={"Server","Access","Security & Startup"};
        int[][] extents={{71,75},{77,83},{205,209}};
        for(int i=0;i<sections.length;i++)assertThat(inkWidth(typography.font(Typography.Role.SECTION_LABEL),sections[i]))
            .as("source section %s at2x",sections[i]).isBetween(extents[i][0],extents[i][1]);
        assertThat(inkWidth(typography.font(Typography.Role.MONO_CAPTION),"http://127.0.0.1:8080")).isBetween(273,277);
        // Separate technical roles and established title policy do not follow caption calibration.
        assertThat(sized(26).font(Typography.Role.MONO).getSize2D()).isEqualTo(24);
        assertThat(sized(26).font(Typography.Role.PANEL_DETAIL_MONO).getSize2D()).isEqualTo(23.3f);
        assertThat(typography.font(Typography.Role.HERO_TITLE).getAttributes().get(java.awt.font.TextAttribute.TRACKING)).isEqualTo(.018f);
    }
    private static int inkWidth(java.awt.Font font,String text) {
        var image=new java.awt.image.BufferedImage(1000,100,java.awt.image.BufferedImage.TYPE_INT_ARGB);
        var g=image.createGraphics();
        try {g.scale(2,2);g.setRenderingHint(java.awt.RenderingHints.KEY_TEXT_ANTIALIASING,java.awt.RenderingHints.VALUE_TEXT_ANTIALIAS_ON);g.setRenderingHint(java.awt.RenderingHints.KEY_FRACTIONALMETRICS,java.awt.RenderingHints.VALUE_FRACTIONALMETRICS_ON);g.setFont(font);g.setColor(java.awt.Color.WHITE);g.drawString(text,4,30);}finally{g.dispose();}
        int min=1000,max=0;for(int y=0;y<100;y++)for(int x=0;x<1000;x++)if((image.getRGB(x,y)>>>24)>51){min=Math.min(min,x);max=Math.max(max,x);}
        return max-min+1;
    }

    @Test void nativeChromeRolesFollowTheReferenceWorkspaceSizesAndScaleWithTheUserSize() {
        // Desktop workspace references: title context and status path 12, toolbar labels 11.5, status text 10,
        // palette headings and hint 11; all regular weight on the platform text family.
        var roles = java.util.Map.of(Typography.Role.TITLE_CONTEXT, 12f, Typography.Role.TOOLBAR_LABEL, 11.5f, Typography.Role.STATUS_TEXT, 10f,
            Typography.Role.STATUS_PATH, 12f, Typography.Role.RESULTS_HEADING, 11f, Typography.Role.PALETTE_HINT, 11f);
        var typography = platform();
        roles.forEach((role, size) -> {
            assertThat(typography.font(role).getSize2D()).as(role.name()).isEqualTo(size);
            assertThat(typography.font(role).getAttributes().get(java.awt.font.TextAttribute.WEIGHT)).as(role.name())
                .isEqualTo(typography.font(Typography.Role.LABEL).getAttributes().get(java.awt.font.TextAttribute.WEIGHT));
            assertThat(sized(26).font(role).getSize2D()).as(role + " doubles with a 26pt body").isEqualTo(size * 2);
            assertThat(typography.font(role).getFamily()).isEqualTo(typography.font(Typography.Role.LABEL).getFamily());
        });
    }

    @Test void selectedResultsKeepEveryDeclaredRoleIncludingWeight() {
        for (var typography : List.of(platform(), named(Font.MONOSPACED, 32)))
            for (var role : Typography.Role.values()) {
                var base = typography.font(role); var selected = typography.resultFont(role, true);
                assertThat(selected.getFamily()).isEqualTo(base.getFamily());
                assertThat(selected.getSize2D()).isEqualTo(base.getSize2D());
                assertThat(selected).isEqualTo(base);
                assertThat(typography.resultFont(role, false)).isEqualTo(base);
            }
    }

    @Test void measuredMediumPolicyPreservesUserFamiliesAndProportionalSizes() {
        var user = named(java.awt.Font.MONOSPACED, 26);
        assertThat(user.font(Typography.Role.HERO_TITLE).getFamily()).isEqualTo(java.awt.Font.MONOSPACED);
        assertThat(user.font(Typography.Role.HERO_TITLE).getSize2D()).isEqualTo(30);
        assertThat(user.font(Typography.Role.HERO_TITLE).getAttributes().get(java.awt.font.TextAttribute.TRACKING)).isNull();
        assertThat(named("Helvetica Neue", 19).font(Typography.Role.BODY).getSize2D()).isEqualTo(19);
        var face = new java.awt.Font("HelveticaNeue-Medium", java.awt.Font.PLAIN, 1);
        if (face.getFamily(java.util.Locale.ROOT).equals("Helvetica Neue")) {
            assertThat(named("Helvetica Neue", 19).font(Typography.Role.BODY).getFamily()).isEqualTo("Helvetica Neue");
            if (platform().family().equals("Helvetica Neue"))
                assertThat(platform().font(Typography.Role.BODY).getFamily()).isEqualTo("Helvetica Neue");
            var calibrated = named("Helvetica Neue", 26).font(Typography.Role.HERO_TITLE);
            assertThat(calibrated.getFontName(java.util.Locale.ROOT)).isEqualTo(face.getFontName(java.util.Locale.ROOT));
            assertThat(calibrated.getSize2D()).isEqualTo(30);
            assertThat(calibrated.getAttributes().get(java.awt.font.TextAttribute.TRACKING)).isEqualTo(.018f);
        }
    }

    /**
     * The platform family is resolved once per process. Each installed-family lookup creates native font objects that
     * macOS never releases (about twenty CFonts per call); per-paint lookups exhausted the test heap after Task 5.
     */
    @Test void platformFamilyIsResolvedOnceNotPerTypography() {
        var first = platform();
        int lookups = Typography.platformLookups();
        for (int i = 0; i < 100; i++) assertThat(platform().family()).isEqualTo(first.family());
        assertThat(Typography.platformLookups()).isEqualTo(lookups).isLessThanOrEqualTo(1);
    }

    /** Every typography is a scale and faces with the user's settings; a user size wins over the scale's body. */
    @Test void typographyComesOnlyFromAScaleFacesAndTheUserFont() {
        assertThat(Typography.class.getConstructors()).isEmpty();
        assertThat(platform().bodySize()).as("no user size: the scale's body").isEqualTo(13f);
        var large = TypeScale.DEFAULT.withBody(20);
        assertThat(Typography.of(large, FaceSet.PLATFORM, Typography.UserFont.NONE).font(Typography.Role.BODY).getSize2D()).isEqualTo(20f);
        assertThat(Typography.of(large, FaceSet.PLATFORM, new Typography.UserFont(Optional.empty(), Optional.of(18f)))
            .font(Typography.Role.BODY).getSize2D()).isEqualTo(18f);
        for (float size : new float[] {Float.NaN, 5.9f, 96.1f})
            assertThatThrownBy(() -> sized(size)).isInstanceOf(IllegalArgumentException.class).hasMessage("Invalid font family or body size");
        assertThatThrownBy(() -> named(" ", 13)).isInstanceOf(IllegalArgumentException.class);
    }

    /**
     * The kit seam moved no font. For every role, selected or not, under no user font, a size-only setting and named
     * families (the platform's own among them, as the calibration fixtures name it), {@code Typography.of} over the
     * default scale and platform faces gives exactly the font the former constructors gave: {@code former} is
     * Typography.font before the seam, over the frozen default-type.tsv.
     */
    @Test void everyRoleResolvesAsTheFormerTypographyDid() throws Exception {
        var table = TypeScaleTest.defaultTable();
        String platform = platform().family();
        record Mode(Typography typography, String family, float body, boolean platformDefault) {}
        var modes = List.of(new Mode(platform(), platform, 13, true), new Mode(sized(20), platform, 20, true),
            new Mode(named("Serif", 20), "Serif", 20, false), new Mode(named(platform, 19), platform, 19, false),
            new Mode(named("Helvetica Neue", 26), "Helvetica Neue", 26, false),
            new Mode(named(Font.MONOSPACED, 32), Font.MONOSPACED, 32, false));
        for (var mode : modes)
            for (var role : Typography.Role.values())
                for (boolean selected : new boolean[] {false, true}) {
                    var row = table.get(role.name());
                    assertThat(mode.typography().font(role, selected)).as("%s %s selected=%s", mode.family(), role, selected)
                        .isEqualTo(former(role, selected, mode.family(), mode.body(), mode.platformDefault(), row.size(), row.weight()));
                }
    }

    private static final boolean APPLE_TEXT = !new Font(".AppleSystemUIFont", Font.PLAIN, 1).getFamily(Locale.ROOT).equals(Font.DIALOG);
    private static final Font HELVETICA_MEDIUM = new Font("HelveticaNeue-Medium", Font.PLAIN, 1);
    private static final boolean MEDIUM = HELVETICA_MEDIUM.getFamily(Locale.ROOT).equals("Helvetica Neue");

    /** Typography.font at 0af7b83e2 (Typography.java:44-60), with the role's frozen size and weight passed in. */
    private static Font former(Typography.Role role, boolean selected, String family, float bodySize, boolean platformDefault,
                               float size, int declaredWeight) {
        String resolved = role == Typography.Role.MONO || role == Typography.Role.MONO_SMALL || role == Typography.Role.MONO_CAPTION
            || role == Typography.Role.PANEL_DETAIL_MONO ? Font.MONOSPACED : family;
        int weight = selected && role == Typography.Role.LABEL ? 500 : declaredWeight;
        if (platformDefault && role != Typography.Role.BODY && weight < 600 && resolved.equals("Helvetica Neue") && APPLE_TEXT)
            resolved = ".AppleSystemUIFont";
        if (weight == 600 && resolved.equals("Helvetica Neue") && MEDIUM)
            return HELVETICA_MEDIUM.deriveFont(Map.of(TextAttribute.SIZE, size * bodySize / 13f,
                TextAttribute.TRACKING, platformDefault && role == Typography.Role.SECTION_LABEL ? .060f : .018f));
        return new Font(resolved, Font.PLAIN, 1).deriveFont(Map.of(
            TextAttribute.SIZE, size * bodySize / 13f,
            TextAttribute.WEIGHT, weight == 600 ? TextAttribute.WEIGHT_SEMIBOLD
                : weight == 500 ? TextAttribute.WEIGHT_MEDIUM : TextAttribute.WEIGHT_REGULAR));
    }
}
```

- [ ] **Step 3: Run the tests to verify they fail**

Run: `./gradlew :jasper-ui:test --tests 'dev.jasper.ui.metrics.*'`
Expected: FAIL in `:jasper-ui:compileTestJava` with `error: cannot find symbol` for `TypeScale`, `FaceSet` and
`Typography.UserFont`.

- [ ] **Step 4: Add TypeScale and FaceSet**

Create `jasper-ui/src/main/java/dev/jasper/ui/metrics/TypeScale.java`:

```java
package dev.jasper.ui.metrics;

import java.util.Arrays;
import java.util.Objects;

/**
 * A kit's type: a size and a weight for every {@link Typography.Role}, the body size those sizes are written for (the
 * base) and the body size the kit draws at when the user sets none. A role draws at its size times the body size over
 * the base.
 */
public final class TypeScale {
    /** Today's 13-point table at a 13-point body, frozen by default-type.tsv. */
    public static final TypeScale DEFAULT = new TypeScale(declaredSizes(), declaredWeights(), 13f, 13f);

    private final float[] sizes;
    private final int[] weights;
    private final float base;
    private final float body;

    private TypeScale(float[] sizes, int[] weights, float base, float body) {
        this.sizes = sizes; this.weights = weights; this.base = base; this.body = body;
    }

    /** {@code role}'s size at the base body size. */
    public float size(Typography.Role role) { return sizes[Objects.requireNonNull(role).ordinal()]; }
    /** {@code role}'s weight: 400, 500 or 600. */
    public int weight(Typography.Role role) { return weights[Objects.requireNonNull(role).ordinal()]; }
    /** The body size the role sizes are written for. */
    public float base() { return base; }
    /** The body size this scale draws at when the user sets none. */
    public float body() { return body; }

    /** This table at another standard body size, from 6 to 96 points. */
    public TypeScale withBody(float body) {
        if (!Float.isFinite(body) || body < 6 || body > 96) throw new IllegalArgumentException("Invalid body size: " + body);
        return new TypeScale(sizes, weights, base, body);
    }

    private static float[] declaredSizes() {
        var roles = Typography.Role.values();
        var sizes = new float[roles.length];
        for (var role : roles) sizes[role.ordinal()] = role.declaredSize;
        return sizes;
    }

    private static int[] declaredWeights() {
        var roles = Typography.Role.values();
        var weights = new int[roles.length];
        for (var role : roles) weights[role.ordinal()] = role.declaredWeight;
        return weights;
    }

    @Override public boolean equals(Object other) {
        return other instanceof TypeScale scale && Arrays.equals(sizes, scale.sizes) && Arrays.equals(weights, scale.weights)
            && Float.compare(base, scale.base) == 0 && Float.compare(body, scale.body) == 0;
    }
    @Override public int hashCode() { return Objects.hash(Arrays.hashCode(sizes), Arrays.hashCode(weights), base, body); }
}
```

Create `jasper-ui/src/main/java/dev/jasper/ui/metrics/FaceSet.java`:

```java
package dev.jasper.ui.metrics;

import java.awt.Font;

/**
 * The faces a kit draws text in, by registered face name. The UI roles keep the platform policy of {@link Typography}
 * (the platform family, resolved once per process, unless the user names one); the data roles draw in
 * {@link #dataRegular()}, or in {@link #dataMedium()} from weight 500.
 */
public final class FaceSet {
    /** Today's policy: the platform UI family and {@link Font#MONOSPACED} for the data roles. */
    public static final FaceSet PLATFORM = new FaceSet(Font.MONOSPACED, Font.MONOSPACED);

    private final String dataRegular;
    private final String dataMedium;

    private FaceSet(String dataRegular, String dataMedium) { this.dataRegular = dataRegular; this.dataMedium = dataMedium; }

    /** The data face at regular weight. */
    public String dataRegular() { return dataRegular; }
    /** The data face from weight 500. */
    public String dataMedium() { return dataMedium; }
}
```

- [ ] **Step 5: Make Typography a scale and faces with the user's font**

Replace the whole of `jasper-ui/src/main/java/dev/jasper/ui/metrics/Typography.java` with (the 38 role constants, the
Helvetica policy, the lazy holders and the size arithmetic, `size * body / base` left to right, are unchanged):

```java
package dev.jasper.ui.metrics;

import java.awt.Font;
import java.awt.GraphicsEnvironment;
import java.awt.font.TextAttribute;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * A type scale's roles drawn in a face set with the user's font settings, applied consistently to named text roles: the
 * platform family (resolved once per process) unless the user names one, at the scale's body size unless the user sets
 * one.
 */
public final class Typography {
    public enum Role {
        HERO_TITLE(15, 600), DETAIL_TITLE(16, 600), WINDOW_TITLE(13, 600), PANEL_TITLE(13, 600), PANEL_DETAIL_TITLE(12.5f, 600), PANEL_DETAIL_LABEL(12, 400), PANEL_HEADER_TITLE(12.5f, 600), PANEL_HEADER_COUNT(11.5f, 400), PANEL_SEARCH(12,400), PANEL_ROW_SELECTED(12,500), PANEL_ROW_SECONDARY(13.25f,400), PANEL_DETAIL_MONO(11.65f,400),
        PANEL_EMPTY_DESCRIPTION(12, 400), PANEL_ROW_CHIP(10, 400),
        PICKER_COMPACT(10, 400), PICKER_CAPTION(9, 400),
        TITLE_CONTEXT(12, 400), TOOLBAR_LABEL(11.5f, 400), STATUS_TEXT(10, 400), STATUS_PATH(12, 400), RESULTS_HEADING(11, 400), PALETTE_HINT(11, 400),
        BODY(13, 400), BODY_STRONG(13, 500), LABEL(12.5f, 400), DESCRIPTION(12.5f, 400),
        SETTINGS_BODY(13, 400), SETTINGS_PREVIEW(12, 400),
        SUPPORTING(11.5f, 400), CAPTION(11.5f, 400), SETTING_DESCRIPTION(11, 400), SECTION_LABEL(11, 600), COUNT(11, 400), CHIP(9.5f, 400), MONO(12, 400), MONO_SMALL(11.5f, 400), MONO_CAPTION(11, 400),
        ORDERED_ACTION(12, 400);
        /** The role's size and weight in {@link TypeScale#DEFAULT}; every reading goes through a scale. */
        final float declaredSize;
        final int declaredWeight;
        Role(float size, int weight) { declaredSize = size; declaredWeight = weight; }
    }

    /**
     * The user's font settings as the host applied them.
     *
     * @param family a named UI family, or empty for the faces' own
     * @param size a body size in points, or empty for the scale's
     */
    public record UserFont(Optional<String> family, Optional<Float> size) {
        /** Neither setting. */
        public static final UserFont NONE = new UserFont(Optional.empty(), Optional.empty());
        /** Both values are required; an empty one means the setting is unset. */
        public UserFont { Objects.requireNonNull(family, "family"); Objects.requireNonNull(size, "size"); }
    }

    private final TypeScale scale;
    private final FaceSet faces;
    private final String family;
    private final float bodySize;
    private final boolean platformDefault;

    /** {@code scale}'s roles in {@code faces}, with {@code user}'s family and size where set. */
    public static Typography of(TypeScale scale, FaceSet faces, UserFont user) {
        Objects.requireNonNull(scale, "scale"); Objects.requireNonNull(faces, "faces"); Objects.requireNonNull(user, "user");
        // A named family never runs the installed-family lookup (platformLookups counts it).
        return new Typography(scale, faces, user.family().orElseGet(() -> PlatformFamily.NAME), user.size().orElse(scale.body()),
            user.family().isEmpty());
    }

    private Typography(TypeScale scale, FaceSet faces, String family, float bodySize, boolean platformDefault) {
        Objects.requireNonNull(family);
        if (family.isBlank() || !Float.isFinite(bodySize) || bodySize < 6 || bodySize > 96)
            throw new IllegalArgumentException("Invalid font family or body size");
        this.scale = scale; this.faces = faces; this.family = family; this.bodySize = bodySize; this.platformDefault = platformDefault;
    }
    public String family() { return family; }
    public float bodySize() { return bodySize; }
    public Font font(Role role) { return font(role, false); }
    /** Selected labels use medium weight. Size settings enlarge every role proportionally. */
    public Font font(Role role, boolean selected) {
        Objects.requireNonNull(role);
        int weight = selected && role == Role.LABEL ? 500 : scale.weight(role);
        String resolved = role == Role.MONO || role == Role.MONO_SMALL || role == Role.MONO_CAPTION || role == Role.PANEL_DETAIL_MONO
            ? weight >= 500 ? faces.dataMedium() : faces.dataRegular() : family;
        // The native macOS text face best matches regular desktop roles. Explicit user families bypass this policy.
        if (platformDefault && role != Role.BODY && weight < 600 && resolved.equals("Helvetica Neue") && AppleText.AVAILABLE)
            resolved = ".AppleSystemUIFont";
        float size = scale.size(role) * bodySize / scale.base();
        // JBR maps Helvetica Neue's numeric semibold to regular. Use its installed medium face;
        // the small family-wide tracking adjustment is calibrated from original desktop title ink.
        if (weight == 600 && resolved.equals("Helvetica Neue") && HelveticaMedium.AVAILABLE)
            return HelveticaMedium.FONT.deriveFont(Map.of(TextAttribute.SIZE, size,
                TextAttribute.TRACKING, platformDefault && role == Role.SECTION_LABEL ? .060f : .018f));
        return new Font(resolved, Font.PLAIN, 1).deriveFont(Map.of(
            TextAttribute.SIZE, size,
            TextAttribute.WEIGHT, weight == 600 ? TextAttribute.WEIGHT_SEMIBOLD
                : weight == 500 ? TextAttribute.WEIGHT_MEDIUM : TextAttribute.WEIGHT_REGULAR));
    }
    /** Result selection changes receiving ink; every declared role keeps its family, size and weight. */
    public Font resultFont(Role role, boolean selected) {
        return font(role);
    }
    private static final class AppleText {
        static final boolean AVAILABLE = !new Font(".AppleSystemUIFont", Font.PLAIN, 1).getFamily(Locale.ROOT).equals(Font.DIALOG);
    }
    private static final class HelveticaMedium {
        static final Font FONT = new Font("HelveticaNeue-Medium", Font.PLAIN, 1);
        static final boolean AVAILABLE = FONT.getFamily(Locale.ROOT).equals("Helvetica Neue");
    }
    static int platformLookups() { return LOOKUPS.get(); }
    private static final java.util.concurrent.atomic.AtomicInteger LOOKUPS = new java.util.concurrent.atomic.AtomicInteger();
    /** Installed families are a process constant; each lookup creates native font objects macOS never releases. */
    private static final class PlatformFamily { static final String NAME = platformFamily(); }
    private static String platformFamily() {
        LOOKUPS.incrementAndGet();
        Set<String> installed = Set.of(GraphicsEnvironment.getLocalGraphicsEnvironment().getAvailableFontFamilyNames(Locale.ROOT));
        String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        List<String> families = os.contains("mac") ? List.of("Helvetica Neue", "Helvetica", "Noto Sans", "DejaVu Sans")
            : os.contains("win") ? List.of("Segoe UI", "Tahoma", "Noto Sans", "DejaVu Sans")
            : List.of("Noto Sans", "Liberation Sans", "DejaVu Sans");
        return families.stream().filter(installed::contains).findFirst().orElse(Font.SANS_SERIF);
    }
}
```

Under `TypeScale.DEFAULT` and `FaceSet.PLATFORM` this is bit-exact: `new Typography()` was the platform family at 13
with the platform policy, which is `of(DEFAULT, PLATFORM, UserFont.NONE)`; `new Typography(size)` is a size-only
`UserFont`; `new Typography(family, size)` a named one. The data roles are all weight 400 and `selected` changes only
`LABEL`, so they stay `Font.MONOSPACED`.

In `jasper-ui/src/main/java/dev/jasper/ui/metrics/package-info.java`, replace line 2:

```java
 * Fixed design geometry and centrally resolved typography.
```

with:

```java
 * Design geometry catalogs, type scales, face sets and centrally resolved typography.
```

- [ ] **Step 6: Read the user's font through Typography.of**

Replace the whole of `jasper-ui/src/main/java/dev/jasper/ui/swing/SwingTypography.java` with (`fontProperty`,
`overrides` and `effective` are unchanged; the three branches of the old `typography()` become `user()`):

```java
package dev.jasper.ui.swing;

import com.formdev.flatlaf.util.UIScale;
import dev.jasper.ui.metrics.FaceSet;
import dev.jasper.ui.metrics.TypeScale;
import dev.jasper.ui.metrics.Typography;
import java.awt.Font;
import java.util.Optional;
import javax.swing.UIManager;

/** Shared bridge from host font provenance to logical roles and effective Swing point sizes. */
final class SwingTypography {
    private SwingTypography() {}
    static boolean fontProperty(String name) {
        return "defaultFont".equals(name)||"Label.font".equals(name)||"Jasper.uiFontFamilyOverride".equals(name);
    }
    private static Font[] overrides() {
        Font[] result = {null, null};
        // Enumerate only developer defaults: the LAF's inherited fonts are already scaled.
        UIManager.getDefaults().forEach((key, value) -> {
            if ("defaultFont".equals(key) && value instanceof Font font) result[0] = font;
            if ("Label.font".equals(key) && value instanceof Font font) result[1] = font;
        });
        return result;
    }
    static Typography typography() {
        return Typography.of(TypeScale.DEFAULT, FaceSet.PLATFORM, user());
    }
    /**
     * The user's font settings as the host applied them: none without a developer {@code defaultFont}; otherwise its size
     * ({@code Label.font}'s when that is set too) and, unless {@code Jasper.uiFontFamilyOverride} is false, its family.
     */
    static Typography.UserFont user() {
        Font[] fonts = overrides();
        if (fonts[0] == null) return Typography.UserFont.NONE;
        Optional<Float> size = Optional.of((fonts[1] == null ? fonts[0] : fonts[1]).getSize2D());
        return Boolean.FALSE.equals(UIManager.get("Jasper.uiFontFamilyOverride"))
            ? new Typography.UserFont(Optional.empty(), size) : new Typography.UserFont(Optional.of(fonts[0].getFamily()), size);
    }
    static Font effective(Font role) {
        // A host override is an exact effective form-font size, including fractions. UIScale may
        // itself derive from that size; applying it again would turn configured 20pt into 30pt.
        // Unset catalog fonts receive user scaling once. Geometry and Java2D device scaling stay
        // independent, including when an explicit flatlaf.uiScale accompanies a font override.
        return overrides()[0] != null ? role : role.deriveFont(UIScale.scale(role.getSize2D()));
    }
}
```

`typography()` still reads `TypeScale.DEFAULT` and `FaceSet.PLATFORM` until Task 7 gives it the context's kit.

- [ ] **Step 7: Convert the remaining constructor calls**

Add to `jasper-ui/src/test/java/dev/jasper/ui/testsupport/KitFixtures.java`, after line 16
(`public static GeneratedTheme light() { ... }`):

```java

    /** The platform UI family the default kit draws in when the user names none (resolved once per process). */
    public static String platformFamily() {
        return dev.jasper.ui.metrics.Typography.of(dev.jasper.ui.metrics.TypeScale.DEFAULT, dev.jasper.ui.metrics.FaceSet.PLATFORM,
            dev.jasper.ui.metrics.Typography.UserFont.NONE).family();
    }
```

Then run this script; it makes exactly the listed replacements and stops if a count differs:

```bash
python3 -I - <<'PY'
import pathlib
EDITS = [
    ("jasper-ui/src/test/java/dev/jasper/ui/metrics/DesignMetricsTest.java", "new Typography(", "named(", 5),
    ("jasper-ui/src/test/java/dev/jasper/ui/metrics/DesignMetricsTest.java", "class DesignMetricsTest {\n",
     "class DesignMetricsTest {\n    private static Typography named(String family, float size) {\n"
     "        return Typography.of(TypeScale.DEFAULT, FaceSet.PLATFORM,\n"
     "            new Typography.UserFont(java.util.Optional.of(family), java.util.Optional.of(size)));\n    }\n\n", 1),
    ("jasper-ui/src/test/java/dev/jasper/ui/gallery/KitGallery.java",
     "new dev.jasper.ui.metrics.Typography().family()", "KitFixtures.platformFamily()", 1),
    ("jasper-ui/src/test/java/dev/jasper/ui/swing/KitCalibrationTest.java",
     "new dev.jasper.ui.metrics.Typography().family()", "dev.jasper.ui.testsupport.KitFixtures.platformFamily()", 3),
    ("jasper-ui/src/test/java/dev/jasper/ui/swing/HeroCompositionTest.java",
     "new dev.jasper.ui.metrics.Typography().family()", "KitFixtures.platformFamily()", 1),
    ("jasper-app/src/test/java/dev/jasper/app/appearance/UiTypographyTest.java", "new dev.jasper.ui.metrics.Typography()",
     "dev.jasper.ui.metrics.Typography.of(dev.jasper.ui.metrics.TypeScale.DEFAULT, dev.jasper.ui.metrics.FaceSet.PLATFORM, "
     "dev.jasper.ui.metrics.Typography.UserFont.NONE)", 1),
]
for name, old, new, count in EDITS:
    path = pathlib.Path(name)
    text = path.read_text(encoding="utf-8")
    if text.count(old) != count: raise SystemExit(f"{name}: expected {count} of {old!r}, found {text.count(old)}")
    path.write_text(text.replace(old, new), encoding="utf-8")
PY
git grep -n -e 'new Typography(' -e 'new dev.jasper.ui.metrics.Typography(' -- '*.java'
```

Expected: the final `git grep` prints nothing. (`DesignMetricsTest`'s five calls are at lines 43, 57, 58, 76 and 77;
`named("", 13)` and `named("SansSerif", Float.NaN)` still throw `IllegalArgumentException` from the private
constructor.)

- [ ] **Step 8: Run the tests to verify they pass**

Run: `./gradlew :jasper-ui:test --tests 'dev.jasper.ui.metrics.*'`
Expected: PASS (`TypeScaleTest` 3, `TypographyTest` 7, `DesignMetricsTest` 8; the Helvetica-only cases skip their
Helvetica assertions where the platform family is another).

- [ ] **Step 9: Run the affected suites**

Run: `./gradlew :jasper-ui:check verifyUiArchitecture`
Expected: BUILD SUCCESSFUL; `KitCalibrationTest`, `GalleryCoverageTest`, `HeroCompositionTest` and
`AppearanceTransactionTest` pass unchanged in their assertions.

Run: `./gradlew :jasper-app:test --tests 'dev.jasper.app.appearance.UiTypographyTest'`
Expected: PASS (5 tests).

- [ ] **Step 10: Commit**

```bash
git add jasper-ui/src/main/java/dev/jasper/ui/metrics jasper-ui/src/main/java/dev/jasper/ui/swing/SwingTypography.java \
  jasper-ui/src/test/resources/dev/jasper/ui/metrics/default-type.tsv jasper-ui/src/test/java/dev/jasper/ui/metrics \
  jasper-ui/src/test/java/dev/jasper/ui/testsupport/KitFixtures.java jasper-ui/src/test/java/dev/jasper/ui/gallery/KitGallery.java \
  jasper-ui/src/test/java/dev/jasper/ui/swing/KitCalibrationTest.java jasper-ui/src/test/java/dev/jasper/ui/swing/HeroCompositionTest.java \
  jasper-app/src/test/java/dev/jasper/app/appearance/UiTypographyTest.java
git commit -F - <<'EOF'
Kit seam: TypeScale, FaceSet and Typography.of

The role sizes and weights become the value TypeScale (DEFAULT is
today's table, frozen by default-type.tsv), FaceSet.PLATFORM names
today's faces, and Typography.of(scale, faces, user) replaces the three
constructors. A test keeps the former resolution as an oracle: every
role resolves to the same font under every user-font mode.

🤖 Generated with Claude Code at The Home Depot

Co-Authored-By: Claude <noreply@anthropic.com>
EOF
```

### Task 3: FlatLaf colour keys computed at install time

`FlatLafInstaller` stops reading the keys a theme carries and computes them from the theme it installs, through the
theme-local `UiKeySet` (spec 6.5), once per install: the same map feeds FlatLaf's JSON and every republish of the
explicit colours. A test proves that, for every built-in variant, the computed keys equal both `ui-color-keys.tsv` over
the variant's own roles and the keys the variant still carries. The tests that injected raw keys through `uiKeys`
(`ThemeTestSupport.withUiKeys`) are re-expressed through roles and look-and-feel defaults. `GeneratedTheme.uiKeys`
itself goes in Task 4.

**Files:**
- Create: `jasper-ui/src/main/java/dev/jasper/ui/theme/UiKeySet.java`
- Modify: `jasper-ui/src/main/java/dev/jasper/ui/theme/UiColorKeys.java:1-34` (imports, class comment, the two `flatLaf` overloads)
- Modify: `jasper-ui/src/main/java/dev/jasper/ui/swing/FlatLafInstaller.java` (whole file)
- Test: `jasper-ui/src/test/java/dev/jasper/ui/theme/UiColorKeysTest.java` (whole file)
- Modify tests: `jasper-app/src/test/java/dev/jasper/app/appearance/ThemeManagerTest.java:17-30,47-58`, `jasper-app/src/test/java/dev/jasper/app/appearance/ThemeTestSupport.java:13,81-86`, `jasper-app/src/test/java/dev/jasper/app/workspace/DockDropOverlayTest.java:226-239`

**Interfaces:**
- Consumes: nothing from Tasks 1-2.
- Produces: `public enum UiKeySet { DEFAULT }` in `dev.jasper.ui.theme`;
  `public static Map<String, Object> UiColorKeys.flatLaf(GeneratedTheme theme, UiKeySet set)` (roles from
  `theme.semantic()`, text selection from `theme.terminal().selection()`); `FlatLafInstaller.install(GeneratedTheme)`
  computes its keys with it. `UiColorKeys.flatLaf(SemanticPalette, Color)` remains until Task 4.
  `ThemeTestSupport.withUiKeys` is removed.

- [ ] **Step 1: Write the failing tests**

Replace the whole of `jasper-ui/src/test/java/dev/jasper/ui/theme/UiColorKeysTest.java` with (the three former tests
keep their assertions; the table read moves into helpers that the new tests share):

```java
package dev.jasper.ui.theme;

import static org.assertj.core.api.Assertions.assertThat;

import java.awt.Color;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** Review Focus 3: every FlatLaf key is one of the variant's colours, from the role its key names. */
class UiColorKeysTest {
    /** ui-color-keys.tsv: each key of UiKeySet.DEFAULT and the source it takes, in FlatLaf's order. */
    static LinkedHashMap<String, String> frozenSources() throws IOException {
        var table = new LinkedHashMap<String, String>();
        try (var in = UiColorKeysTest.class.getResourceAsStream("ui-color-keys.tsv")) {
            for (String line : new String(in.readAllBytes(), StandardCharsets.UTF_8).split("\n")) {
                if (line.isBlank() || line.startsWith("#")) continue;
                String[] cells = line.split("\t");
                table.put(cells[0], cells[1]);
            }
        }
        return table;
    }

    /** The keys ui-color-keys.tsv gives over {@code palette} with {@code selection} as the terminal selection. */
    static LinkedHashMap<String, Object> expected(SemanticPalette palette, Color selection) throws IOException {
        var sources = new LinkedHashMap<String, Object>();
        for (var role : SemanticColor.values()) sources.put(role.name(), ColorMath.hex(palette.color(role)));
        sources.put("terminal.selection", ColorMath.hex(selection));
        sources.put("error.tint", ColorMath.hex(UiColorKeys.errorTint(palette)));
        var expected = new LinkedHashMap<String, Object>();
        for (var entry : frozenSources().entrySet()) {
            assertThat(sources).as(entry.getKey()).containsKey(entry.getValue());
            expected.put(entry.getKey(), sources.get(entry.getValue()));
        }
        return expected;
    }

    /**
     * Each key takes the source frozen in {@code ui-color-keys.tsv} (captured when the keys matched the former template),
     * in order. Every role and both derived sources get a colour of their own, so a key moved to another role is caught
     * even where Slate Cobalt gives the two roles one colour.
     */
    @Test void eachKeyTakesItsFrozenSourceInOrder() throws IOException {
        var colors = new EnumMap<SemanticColor, Color>(SemanticColor.class);
        for (var role : SemanticColor.values()) {
            int i = role.ordinal() + 1;
            colors.put(role, new Color(i * 3, 255 - i * 2, (i * 53) % 256));
        }
        var palette = new SemanticPalette(colors);
        var selection = new Color(0xAA, 0x01, 0xFE);
        var sources = new HashSet<Object>();
        for (var role : SemanticColor.values()) sources.add(ColorMath.hex(palette.color(role)));
        sources.add(ColorMath.hex(selection));
        sources.add(ColorMath.hex(UiColorKeys.errorTint(palette)));
        assertThat(sources).as("every source has its own colour").hasSize(SemanticColor.values().length + 2);
        assertThat(UiColorKeys.flatLaf(palette, selection)).containsExactlyEntriesOf(expected(palette, selection));
    }

    /**
     * Enterprise presentation plan 1: computed at install time, every built-in variant's keys are the frozen keys over its
     * own roles and matching terminal selection, and the keys it carried before the seam.
     */
    @Test void everyBuiltInVariantInstallsTheKeysItCarried() throws IOException {
        int variants = 0;
        for (var theme : JasperTheme.builtIns())
            for (boolean dark : new boolean[] {true, false}) {
                if (!theme.has(dark)) continue;
                var generated = theme.generated(dark);
                var keys = UiColorKeys.flatLaf(generated, UiKeySet.DEFAULT);
                String name = theme.id() + (dark ? " dark" : " light");
                assertThat(keys).as(name).containsExactlyEntriesOf(expected(generated.semantic(), theme.matching(dark).colors().selection()));
                assertThat(keys).as(name + ", as carried").containsExactlyEntriesOf(generated.uiKeys());
                variants++;
            }
        assertThat(variants).as("Slate Cobalt's two variants and the four Catppuccin flavours").isEqualTo(6);
    }

    /** A theme's keys are its own roles': a copy with another palette installs that palette's colours. */
    @Test void aRecolouredCopyGetsTheKeysOfItsOwnRoles() {
        var base = JasperTheme.bundled().generated(true);
        var colors = new EnumMap<SemanticColor, Color>(SemanticColor.class);
        for (var role : SemanticColor.values()) colors.put(role, base.semantic().color(role));
        colors.put(SemanticColor.SURFACE_APP, new Color(0x12, 0x34, 0x56));
        var copy = dev.jasper.ui.testsupport.KitFixtures.withSemantic(base, new SemanticPalette(colors));
        assertThat(UiColorKeys.flatLaf(copy, UiKeySet.DEFAULT).get("SearchEverywhere.Header.background")).isEqualTo("#123456");
        assertThat(UiColorKeys.flatLaf(base, UiKeySet.DEFAULT).get("SearchEverywhere.Header.background"))
            .isEqualTo(ColorMath.hex(base.semantic().color(SemanticColor.SURFACE_APP)));
    }

    @Test void everyKeyIsARolesColour() {
        for (boolean dark : new boolean[]{true, false}) {
            var p = JasperTheme.bundled().palette(dark);
            var selection = JasperTheme.bundled().matching(dark).colors().selection();
            var allowed = new HashSet<Object>();
            for (var role : SemanticColor.values()) allowed.add(ColorMath.hex(p.color(role)));
            allowed.add(ColorMath.hex(selection)); allowed.add(ColorMath.hex(UiColorKeys.errorTint(p)));
            for (var entry : UiColorKeys.flatLaf(p, selection).entrySet())
                assertThat(entry.getValue()).as("%s in %s", entry.getKey(), dark ? "dark" : "light").isIn(allowed);
        }
    }

    @Test void textSelectionAndTheSearchTintKeepTheirSources() {
        for (boolean dark : new boolean[]{true, false}) {
            var p = JasperTheme.bundled().palette(dark);
            var selection = JasperTheme.bundled().matching(dark).colors().selection();
            Map<String, Object> keys = UiColorKeys.flatLaf(p, selection);
            assertThat(keys.get("*.selectionBackground")).isEqualTo(ColorMath.hex(selection));
            assertThat(keys.get("SearchField.errorBackground")).isEqualTo(ColorMath.hex(UiColorKeys.errorTint(p)));
            assertThat(keys.get("Panel.background")).isEqualTo(ColorMath.hex(p.color(SemanticColor.SURFACE_PANEL)));
        }
    }
}
```

`ColorMath.hex` writes lowercase digits, so `#123456` is the recoloured `surface.app`.

- [ ] **Step 2: Run the tests to verify they fail**

Run: `./gradlew :jasper-ui:test --tests 'dev.jasper.ui.theme.UiColorKeysTest'`
Expected: FAIL in `:jasper-ui:compileTestJava` with `error: cannot find symbol` / `symbol: class UiKeySet`.

- [ ] **Step 3: Add the key set and the theme-taking overload**

Create `jasper-ui/src/main/java/dev/jasper/ui/theme/UiKeySet.java`:

```java
package dev.jasper.ui.theme;

/**
 * The FlatLaf colour keys {@link UiColorKeys#flatLaf(GeneratedTheme, UiKeySet)} fills from a theme's roles. A kit names
 * its set, so the theme engine never sees the kit.
 */
public enum UiKeySet {
    /** The default look's keys, frozen by ui-color-keys.tsv. */
    DEFAULT
}
```

In `jasper-ui/src/main/java/dev/jasper/ui/theme/UiColorKeys.java`, replace lines 1-34 (from `package` through the
closing brace of `flatLaf(SemanticPalette, Color)`):

```java
package dev.jasper.ui.theme;

import static dev.jasper.ui.theme.SemanticColor.*;

import java.awt.Color;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * FlatLaf's colour defaults for a Slate Cobalt variant, each from a semantic role; text selection in text components
 * takes the variant's terminal selection and a search field's no-match background the {@linkplain #errorTint error
 * tint}. Order matters: a {@code *.suffix} key recolours the keys before it that end in the suffix.
 */
public final class UiColorKeys {
    private UiColorKeys() {}

    private sealed interface Source permits Role, TextSelection, ErrorTint {}
    private record Role(SemanticColor role) implements Source {}
    private record TextSelection() implements Source {}
    private record ErrorTint() implements Source {}
    private static final Source SELECTED_TEXT = new TextSelection(), ERROR = new ErrorTint();
    private static final Map<String, Source> KEYS = keys();

    /** The keys in FlatLaf's order, each as {@code #rrggbb} or {@code #rrggbbaa}. */
    public static Map<String, Object> flatLaf(SemanticPalette palette, Color textSelection) {
        var keys = new LinkedHashMap<String, Object>();
        KEYS.forEach((key, source) -> keys.put(key, ColorMath.hex(switch (source) {
            case Role role -> palette.color(role.role());
            case TextSelection ignored -> textSelection;
            case ErrorTint ignored -> errorTint(palette);
        })));
        return Collections.unmodifiableMap(keys);
    }
```

with:

```java
package dev.jasper.ui.theme;

import static dev.jasper.ui.theme.SemanticColor.*;

import java.awt.Color;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * FlatLaf's colour defaults for a theme variant in a {@link UiKeySet}, each from a semantic role; text selection in text
 * components takes the variant's terminal selection and a search field's no-match background the
 * {@linkplain #errorTint error tint}. Order matters: a {@code *.suffix} key recolours the keys before it that end in the
 * suffix.
 */
public final class UiColorKeys {
    private UiColorKeys() {}

    private sealed interface Source permits Role, TextSelection, ErrorTint {}
    private record Role(SemanticColor role) implements Source {}
    private record TextSelection() implements Source {}
    private record ErrorTint() implements Source {}
    private static final Source SELECTED_TEXT = new TextSelection(), ERROR = new ErrorTint();
    private static final Map<String, Source> KEYS = keys();

    /**
     * {@code set}'s keys for {@code theme} in FlatLaf's order, each as {@code #rrggbb} or {@code #rrggbbaa}: the roles from
     * the theme's palette, text selection from its terminal colours.
     */
    public static Map<String, Object> flatLaf(GeneratedTheme theme, UiKeySet set) {
        Map<String, Source> sources = switch (Objects.requireNonNull(set)) {
            case DEFAULT -> KEYS;
        };
        return values(sources, theme.semantic(), theme.terminal().selection());
    }

    /** The default keys in FlatLaf's order, each as {@code #rrggbb} or {@code #rrggbbaa}. */
    public static Map<String, Object> flatLaf(SemanticPalette palette, Color textSelection) {
        return values(KEYS, palette, textSelection);
    }

    private static Map<String, Object> values(Map<String, Source> sources, SemanticPalette palette, Color textSelection) {
        var keys = new LinkedHashMap<String, Object>();
        sources.forEach((key, source) -> keys.put(key, ColorMath.hex(switch (source) {
            case Role role -> palette.color(role.role());
            case TextSelection ignored -> textSelection;
            case ErrorTint ignored -> errorTint(palette);
        })));
        return Collections.unmodifiableMap(keys);
    }
```

`errorTint` and `keys()` (from line 36 on) are unchanged.

- [ ] **Step 4: Compute the keys once per install**

Replace the whole of `jasper-ui/src/main/java/dev/jasper/ui/swing/FlatLafInstaller.java` with:

```java
package dev.jasper.ui.swing;

import com.formdev.flatlaf.FlatLaf;
import com.formdev.flatlaf.IntelliJTheme;
import dev.jasper.ui.theme.*;
import dev.jasper.ui.metrics.DesignMetrics;
import java.awt.EventQueue;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import javax.swing.UIManager;
import javax.swing.plaf.ColorUIResource;

/** Real FlatLaf installation. Authored colors are retained; geometry is owned by the kit. EDT only. */
public final class FlatLafInstaller {
    private FlatLafInstaller() {}
    /** Installed immutable snapshot; stored with the LAF so appearance rollback restores it atomically. */
    public static GeneratedTheme installedTheme() {
        Object value = UIManager.getLookAndFeelDefaults().get("Jasper.installedTheme");
        if (value instanceof GeneratedTheme theme) return theme;
        throw new IllegalStateException("No Jasper theme installed");
    }
    private static final class SnapshotLaf extends IntelliJTheme.ThemeLaf {
        private final GeneratedTheme snapshot;
        private final Map<String, Object> ui;
        SnapshotLaf(IntelliJTheme source, GeneratedTheme snapshot, Map<String, Object> ui) {
            super(source); this.snapshot = snapshot; this.ui = ui;
        }
        @Override public javax.swing.UIDefaults getDefaults() {
            var defaults = super.getDefaults();
            publish(defaults, snapshot, ui);
            return defaults;
        }
    }
    /** Installs {@code theme}: FlatLaf's colour keys computed from its roles now, then the catalog's geometry. */
    public static void install(GeneratedTheme theme) {
        if (!EventQueue.isDispatchThread()) throw new IllegalStateException("Appearance requires EDT");
        // One map, computed from the theme's roles, feeds FlatLaf's input and every republish of the explicit colours.
        var ui = UiColorKeys.flatLaf(theme, UiKeySet.DEFAULT);
        String json = FlatLafThemeData.json(theme.name(), theme.dark(), theme.author(), ui);
        FlatLaf laf;
        try { laf = new SnapshotLaf(new IntelliJTheme(new ByteArrayInputStream(json.getBytes(StandardCharsets.UTF_8))), theme, ui); }
        catch (IOException failure) { throw new UncheckedIOException(failure); }
        var fixed = new LinkedHashMap<String, String>();
        var metrics = DesignMetrics.DEFAULT;
        String control = Integer.toString(metrics.value(DesignMetrics.Role.CONTROL_RADIUS) * 2);
        fixed.put("Component.arc", control);
        fixed.put("TextComponent.arc", control);
        fixed.put("Button.arc", control);
        fixed.put("Component.focusWidth", Integer.toString(metrics.value(DesignMetrics.Role.FOCUS_RING)));
        fixed.put("Button.minimumHeight", Integer.toString(metrics.value(DesignMetrics.Role.BUTTON_HEIGHT)));
        fixed.put("Component.minimumHeight", Integer.toString(metrics.value(DesignMetrics.Role.FIELD_HEIGHT)));
        // Buttons as on main: a 14 px label margin and a 72 px minimum width (a window footer's action).
        int label = metrics.value(DesignMetrics.Role.BUTTON_LABEL_MARGIN), side = metrics.value(DesignMetrics.Role.BUTTON_HORIZONTAL_PADDING);
        fixed.put("Button.margin", insets(label, side, label, side));
        fixed.put("Button.minimumWidth", Integer.toString(metrics.value(DesignMetrics.Role.WINDOW_ACTION_MIN_WIDTH)));
        // Selection is a plain rectangle, edge to edge.
        for (var key : java.util.List.of("List", "Tree", "Table", "ComboBox", "MenuItem")) {
            fixed.put(key + ".selectionArc", "0");
            fixed.put(key + ".selectionInsets", "0,0,0,0");
        }
        // Native menu rows match the kit's: a 22 px row from a 16 px line, 8 px sides, 4 px popup padding, radius 6.
        int row = metrics.value(DesignMetrics.Role.MENU_ITEM_VERTICAL_PADDING), inset = metrics.value(DesignMetrics.Role.MENU_ITEM_PADDING);
        for (var key : java.util.List.of("MenuItem", "Menu", "CheckBoxMenuItem", "RadioButtonMenuItem")) fixed.put(key + ".margin", insets(row, inset, row, inset));
        int padding = metrics.value(DesignMetrics.Role.MENU_PADDING), line = metrics.value(DesignMetrics.Role.HAIRLINE);
        fixed.put("PopupMenu.borderInsets", insets(padding, line, padding, line));
        String radius = Integer.toString(metrics.value(DesignMetrics.Role.CARD_RADIUS));
        fixed.put("Popup.borderCornerRadius", radius);
        fixed.put("PopupMenu.borderCornerRadius", radius);
        laf.setExtraDefaults(fixed);
        try { UIManager.setLookAndFeel(laf); }
        catch (javax.swing.UnsupportedLookAndFeelException failure) { throw new IllegalStateException("Cannot install generated appearance", failure); }
    }

    private static String insets(int top, int left, int bottom, int right) { return top + "," + left + "," + bottom + "," + right; }

    // A LAF reinstall (including rollback) obtains fresh defaults; publish the same immutable snapshot every time.
    private static void publish(javax.swing.UIDefaults defaults, GeneratedTheme theme, Map<String, Object> ui) {
        defaults.put("Jasper.installedTheme", theme);
        FlatLafThemeData.explicitColors(ui).forEach((key, color) -> defaults.put(key, new ColorUIResource(color)));
        for (var role : SemanticColor.values()) defaults.put("Jasper.semantic." + role.key(), new ColorUIResource(theme.semantic().color(role)));
        // Popovers and menus: FlatLaf's painted popup shadow (macOS keeps its native one), black at theme.md's strength.
        defaults.put("Popup.dropShadowColor", new ColorUIResource(java.awt.Color.BLACK));
        defaults.put("Popup.dropShadowOpacity", DesignMetrics.DEFAULT.value(theme.dark()
            ? DesignMetrics.Role.POPOVER_SHADOW_PERCENT_DARK : DesignMetrics.Role.POPOVER_SHADOW_PERCENT_LIGHT) / 100f);
        var palette = theme.semantic();
        // FlatLaf primitives also inherit the canonical roles; kit paints use the palette directly.
        Map<String, SemanticColor> roles = Map.ofEntries(
            Map.entry("Panel.background", SemanticColor.SURFACE_PANEL), Map.entry("Label.foreground", SemanticColor.TEXT_PRIMARY),
            Map.entry("TextField.background", SemanticColor.SURFACE_INPUT), Map.entry("TextField.foreground", SemanticColor.TEXT_PRIMARY),
            Map.entry("PasswordField.background", SemanticColor.SURFACE_INPUT), Map.entry("PasswordField.foreground", SemanticColor.TEXT_PRIMARY),
            Map.entry("Component.borderColor", SemanticColor.BORDER_INPUT), Map.entry("Component.focusColor", SemanticColor.BORDER_FOCUS),
            Map.entry("Button.background", SemanticColor.BUTTON_SECONDARY_BG), Map.entry("Button.foreground", SemanticColor.TEXT_STRONG_SECONDARY),
            Map.entry("List.selectionBackground", SemanticColor.SELECTION_BG), Map.entry("List.selectionForeground", SemanticColor.TEXT_ONSELECTION));
        roles.forEach((key, role) -> defaults.put(key, new ColorUIResource(palette.color(role))));
    }
}
```

The geometry still reads `DesignMetrics.DEFAULT` here; Task 6 gives it the kit.

- [ ] **Step 5: Re-express the tests that injected raw keys**

In `jasper-app/src/test/java/dev/jasper/app/appearance/ThemeManagerTest.java`, replace lines 17-30
(`keysFlatLafSkipsAreRestoredWithoutReplacingItsOwn`) with:

```java
    @Test void keysFlatLafSkipsAreRestoredWithoutReplacingItsOwn() throws Exception {
        LookAndFeel original = UIManager.getLookAndFeel();
        try {
            // The theme's own roles behind keys FlatLaf skips or derives itself, and an alpha-carrying role (border.subtle).
            var theme = ThemeTestSupport.recolored(ThemeTestSupport.generated(true), Map.of(
                dev.jasper.ui.theme.SemanticColor.SURFACE_APP, new Color(0x12, 0x34, 0x56),
                dev.jasper.ui.theme.SemanticColor.SURFACE_INPUT, new Color(0x01, 0x01, 0x01)));
            assertThat(ThemeManager.install(theme)).isTrue();
            var expected = Map.of("SearchEverywhere.Header.background", "#123456", "ComboBox.background", "#010101",
                "Separator.foreground", hex(theme.semantic().color(dev.jasper.ui.theme.SemanticColor.BORDER_SUBTLE)));
            assertThat(expected.get("Separator.foreground")).as("Slate Cobalt Dark's border.subtle carries alpha").hasSize(9);
            assertColours(expected);
            // A theme's own raw colours win consistently, including alpha and same-instance reinstall.
            UIManager.setLookAndFeel(UIManager.getLookAndFeel());
            assertColours(expected);
        } finally { UIManager.setLookAndFeel(original); }
    }
```

and replace lines 47-58 (`switchingThemesLeavesNothingOfThePreviousOneBehind`) with:

```java
    @Test void switchingThemesLeavesNothingOfThePreviousOneBehind() throws Exception {
        LookAndFeel original = UIManager.getLookAndFeel();
        try {
            ThemeManager.install(ThemeTestSupport.generated(false));
            // A key only the previous look and feel's defaults hold.
            UIManager.getLookAndFeelDefaults().put("Plugins.hoverBackground", new javax.swing.plaf.ColorUIResource(new Color(0x12, 0x34, 0x56)));
            assertThat(UIManager.getColor("Plugins.hoverBackground")).isNotNull();
            var dark = ThemeTestSupport.generated(true);
            ThemeManager.install(dark);
            assertThat(UIManager.getColor("Plugins.hoverBackground")).isNull();
            assertColours(Map.of("ToolWindow.background",
                (String) dev.jasper.ui.theme.UiColorKeys.flatLaf(dark, dev.jasper.ui.theme.UiKeySet.DEFAULT).get("ToolWindow.background"),
                "Jasper.semantic.surface.app", hex(dark.semantic().color(dev.jasper.ui.theme.SemanticColor.SURFACE_APP))));
        } finally { UIManager.setLookAndFeel(original); }
    }
```

`SearchEverywhere.Header.background` takes `surface.app`, `ComboBox.background` `surface.input` and
`Separator.foreground` `border.subtle` (`UiColorKeys.java:163`, `:74`, `:107`); `publish`'s own role map sets none of
the three, so the explicit-colour republish is what the first test exercises.

In `jasper-app/src/test/java/dev/jasper/app/workspace/DockDropOverlayTest.java`, replace lines 226-239 (from
`@Test void dragGuidesFollowTheCanonicalAccentRoute()` through the closing brace of `installDragTheme`):

```java
    @Test void dragGuidesFollowTheCanonicalAccentRoute() {
        installDragTheme(new Color(0x3574f0), Map.of("DragAndDrop.areaBackground", "#0a141e28", "DragAndDrop.borderColor", "#c86432"));
        assertThat(DockDropOverlay.fill()).isEqualTo(new Color(0x35, 0x74, 0xf0, 64));
        assertThat(DockDropOverlay.border()).isEqualTo(new Color(0x35, 0x74, 0xf0, 153));
        installDragTheme(new Color(0x2f80ed), Map.of());
        assertThat(DockDropOverlay.fill()).isEqualTo(new Color(0x2f, 0x80, 0xed, 64));
        assertThat(DockDropOverlay.border()).isEqualTo(new Color(0x2f, 0x80, 0xed, 153));
    }

    private void installDragTheme(Color accent, Map<String, Object> colors) {
        if (originalLookAndFeel == null) originalLookAndFeel = UIManager.getLookAndFeel();
        ThemeTestSupport.install(ThemeTestSupport.withUiKeys(ThemeTestSupport.recolored(ThemeTestSupport.generated(true),
            Map.of(dev.jasper.ui.theme.SemanticColor.ACCENT, accent)), colors));
    }
```

with:

```java
    @Test void dragGuidesFollowTheCanonicalAccentRoute() {
        installDragTheme(new Color(0x3574f0), Map.of("DragAndDrop.areaBackground", new Color(0x0a, 0x14, 0x1e, 0x28),
            "DragAndDrop.borderColor", new Color(0xc86432)));
        assertThat(DockDropOverlay.fill()).isEqualTo(new Color(0x35, 0x74, 0xf0, 64));
        assertThat(DockDropOverlay.border()).isEqualTo(new Color(0x35, 0x74, 0xf0, 153));
        installDragTheme(new Color(0x2f80ed), Map.of());
        assertThat(DockDropOverlay.fill()).isEqualTo(new Color(0x2f, 0x80, 0xed, 64));
        assertThat(DockDropOverlay.border()).isEqualTo(new Color(0x2f, 0x80, 0xed, 153));
    }

    private void installDragTheme(Color accent, Map<String, Color> authored) {
        if (originalLookAndFeel == null) originalLookAndFeel = UIManager.getLookAndFeel();
        ThemeTestSupport.install(ThemeTestSupport.recolored(ThemeTestSupport.generated(true),
            Map.of(dev.jasper.ui.theme.SemanticColor.ACCENT, accent)));
        // Authored vendor DragAndDrop.* values in the installed defaults; the guides ignore them.
        authored.forEach((key, color) -> UIManager.getLookAndFeelDefaults().put(key, new javax.swing.plaf.ColorUIResource(color)));
    }
```

In `jasper-app/src/test/java/dev/jasper/app/appearance/ThemeTestSupport.java`, delete lines 81-86 (`withUiKeys`, its
comment included):

```java
    /** {@code base} with these FlatLaf UI keys added or replaced (values {@code #rrggbb} or FlatLaf values). */
    public static GeneratedTheme withUiKeys(GeneratedTheme base, Map<String, Object> keys) {
        var merged = new LinkedHashMap<>(base.uiKeys()); merged.putAll(keys);
        return new GeneratedTheme(base.name(), base.dark(), base.author(), base.semantic(), base.terminal(), base.editor(),
            base.syntax(), merged, base.glass());
    }
```

and line 13 (`import java.util.LinkedHashMap;`, now unused).

- [ ] **Step 6: Run the tests to verify they pass**

Run: `./gradlew :jasper-ui:test --tests 'dev.jasper.ui.theme.UiColorKeysTest' --tests 'dev.jasper.ui.swing.SlateCobaltInstallTest'`
Expected: PASS (`UiColorKeysTest` 5 tests, `SlateCobaltInstallTest` 4 tests).

Run: `./gradlew :jasper-app:test --tests 'dev.jasper.app.appearance.ThemeManagerTest' --tests 'dev.jasper.app.workspace.DockDropOverlayTest'`
Expected: PASS.

- [ ] **Step 7: Run the affected suites**

Run: `./gradlew :jasper-ui:check verifyUiArchitecture`
Expected: BUILD SUCCESSFUL; `GalleryCoverageTest` (which installs `KitFixtures.custom()`) and `AppearanceTransactionTest`
pass unchanged.

Run: `./gradlew :jasper-app:test`
Expected: BUILD SUCCESSFUL. The other installs of recoloured copies (`ConfigurationStatusTest:119`,
`WorkspaceRolesTest:76`, `WindowCommandPaletteTest:496`) assert colours of the copy's own roles, which the computed keys
now match.

- [ ] **Step 8: Commit**

```bash
git add jasper-ui/src/main/java/dev/jasper/ui/theme/UiKeySet.java jasper-ui/src/main/java/dev/jasper/ui/theme/UiColorKeys.java \
  jasper-ui/src/main/java/dev/jasper/ui/swing/FlatLafInstaller.java jasper-ui/src/test/java/dev/jasper/ui/theme/UiColorKeysTest.java \
  jasper-app/src/test/java/dev/jasper/app/appearance/ThemeManagerTest.java jasper-app/src/test/java/dev/jasper/app/appearance/ThemeTestSupport.java \
  jasper-app/src/test/java/dev/jasper/app/workspace/DockDropOverlayTest.java
git commit -F - <<'EOF'
Kit seam: compute FlatLaf's colour keys when a theme is installed

FlatLafInstaller computes the keys from the theme it installs, through
the theme-local UiKeySet, once per install; the same map feeds FlatLaf
and every republish. For every built-in variant the result equals
ui-color-keys.tsv over its roles and the keys it carried. Tests that
injected raw keys now go through roles or look-and-feel defaults.

🤖 Generated with Claude Code at The Home Depot

Co-Authored-By: Claude <noreply@anthropic.com>
EOF
```

### Task 4: GeneratedTheme loses uiKeys

Nothing reads the carried keys any more, so the record component goes (spec 6.5), with the palette overload of
`UiColorKeys.flatLaf`; `JasperTheme.build` and `TitleBarTheme` stop computing them.

**Files:**
- Modify: `jasper-ui/src/main/java/dev/jasper/ui/theme/GeneratedTheme.java` (whole file), `jasper-ui/src/main/java/dev/jasper/ui/theme/JasperTheme.java:175-176`, `jasper-ui/src/main/java/dev/jasper/ui/theme/TitleBarTheme.java:52-53`, `jasper-ui/src/main/java/dev/jasper/ui/theme/UiColorKeys.java` (the palette overload from Task 3)
- Test: `jasper-ui/src/test/java/dev/jasper/ui/theme/SlateCobaltGeneratedTest.java:21-23`
- Modify tests: `jasper-ui/src/test/java/dev/jasper/ui/theme/UiColorKeysTest.java` (Task 3's file), `jasper-ui/src/test/java/dev/jasper/ui/swing/SlateCobaltInstallTest.java:6,21`, `jasper-ui/src/test/java/dev/jasper/ui/testsupport/KitFixtures.java:32-33,46-47` (lines as of Task 3: 38-39 and 52-53 after Task 2's insertion), `jasper-app/src/test/java/dev/jasper/app/appearance/ThemeTestSupport.java` (recolored, withSyntax, withGlass, withDark), `jasper-app/src/test/java/dev/jasper/app/plugins/HostedPalettesTest.java:23-24`, `jasper-app/src/test/java/dev/jasper/app/plugins/HostedKitTest.java:143-144`, `jasper-app/src/test/java/dev/jasper/app/plugins/WorkspaceAppearanceFixture.java:49-51`

**Interfaces:**
- Consumes: `UiColorKeys.flatLaf(GeneratedTheme, UiKeySet)`, `UiKeySet.DEFAULT` (Task 3).
- Produces: `public record GeneratedTheme(String name, boolean dark, String author, SemanticPalette semantic,
  TerminalColors terminal, EditorColors editor, Map<SyntaxRole, SyntaxStyle> syntax, GlassLevels glass)`;
  `UiColorKeys.flatLaf(GeneratedTheme, UiKeySet)` is the only `flatLaf`.

- [ ] **Step 1: Write the failing test**

In `jasper-ui/src/test/java/dev/jasper/ui/theme/SlateCobaltGeneratedTest.java`, replace lines 21-23:

```java
            assertThat(theme.uiKeys()).containsKeys("*.background", "Panel.background");
            assertThat(theme.uiKeys().keySet()).as("no Jasper.* aliases: chrome reads Jasper.semantic.*").noneMatch(key -> key.startsWith("Jasper."));
            assertThat(theme.uiKeys().get("Panel.background")).isEqualTo(ColorMath.hex(theme.semantic().color(SemanticColor.SURFACE_PANEL)));
```

with:

```java
            var keys = UiColorKeys.flatLaf(theme, UiKeySet.DEFAULT);
            assertThat(keys).containsKeys("*.background", "Panel.background");
            assertThat(keys.keySet()).as("no Jasper.* aliases: chrome reads Jasper.semantic.*").noneMatch(key -> key.startsWith("Jasper."));
            assertThat(keys.get("Panel.background")).isEqualTo(ColorMath.hex(theme.semantic().color(SemanticColor.SURFACE_PANEL)));
            assertThat(java.util.Arrays.stream(GeneratedTheme.class.getRecordComponents()).map(java.lang.reflect.RecordComponent::getName))
                .as("FlatLaf's keys are derived when a theme is installed").doesNotContain("uiKeys");
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `./gradlew :jasper-ui:test --tests 'dev.jasper.ui.theme.SlateCobaltGeneratedTest'`
Expected: FAIL: `FlatLaf's keys are derived when a theme is installed` ... `not to contain ["uiKeys"]`.

- [ ] **Step 3: Remove the component**

Replace the whole of `jasper-ui/src/main/java/dev/jasper/ui/theme/GeneratedTheme.java` with:

```java
package dev.jasper.ui.theme;

import java.util.Map;
import java.util.Objects;

/**
 * A theme variant with every value filled in: the semantic palette, the matching terminal palette, the editor scheme and
 * its syntax styles and the workspace glass. Immutable and Swing-free. FlatLaf's UI keys are derived from it when it is
 * installed ({@link UiColorKeys#flatLaf(GeneratedTheme, UiKeySet)}).
 */
public record GeneratedTheme(String name, boolean dark, String author, SemanticPalette semantic, TerminalColors terminal,
                             EditorColors editor, Map<SyntaxRole, SyntaxStyle> syntax, GlassLevels glass) {
    public GeneratedTheme {
        Objects.requireNonNull(name); Objects.requireNonNull(author); Objects.requireNonNull(semantic);
        Objects.requireNonNull(terminal); Objects.requireNonNull(editor); Objects.requireNonNull(glass);
        syntax = Map.copyOf(syntax);
    }
}
```

In `UiColorKeys.java`, delete the palette overload Task 3 kept:

```java
    /** The default keys in FlatLaf's order, each as {@code #rrggbb} or {@code #rrggbbaa}. */
    public static Map<String, Object> flatLaf(SemanticPalette palette, Color textSelection) {
        return values(KEYS, palette, textSelection);
    }

```

Then run this script; it makes exactly the listed replacements (every other `new GeneratedTheme(` drops its `uiKeys`
argument) and stops if a count differs:

```bash
python3 -I - <<'PY'
import pathlib
EDITS = [
    ("jasper-ui/src/main/java/dev/jasper/ui/theme/JasperTheme.java",
     "        var generated = new GeneratedTheme(name, dark, author, palette, scheme.colors(), colors, syntax,\n"
     "            UiColorKeys.flatLaf(palette, scheme.colors().selection()), GlassLevels.OPAQUE);\n",
     "        var generated = new GeneratedTheme(name, dark, author, palette, scheme.colors(), colors, syntax, GlassLevels.OPAQUE);\n", 1),
    ("jasper-ui/src/main/java/dev/jasper/ui/theme/TitleBarTheme.java",
     "            theme.syntax(), theme.uiKeys(), theme.glass());", "            theme.syntax(), theme.glass());", 1),
    ("jasper-ui/src/test/java/dev/jasper/ui/testsupport/KitFixtures.java",
     "base.syntax(),\n            base.uiKeys(), base.glass());", "base.syntax(),\n            base.glass());", 2),
    ("jasper-app/src/test/java/dev/jasper/app/appearance/ThemeTestSupport.java",
     "            base.syntax(), base.uiKeys(), base.glass());", "            base.syntax(), base.glass());", 2),
    ("jasper-app/src/test/java/dev/jasper/app/appearance/ThemeTestSupport.java",
     "            merged, base.uiKeys(), base.glass());", "            merged, base.glass());", 1),
    ("jasper-app/src/test/java/dev/jasper/app/appearance/ThemeTestSupport.java",
     "            base.syntax(), base.uiKeys(), glass);", "            base.syntax(), glass);", 1),
    ("jasper-app/src/test/java/dev/jasper/app/plugins/HostedPalettesTest.java",
     "            base.syntax(), base.uiKeys(), base.glass());", "            base.syntax(), base.glass());", 1),
    ("jasper-app/src/test/java/dev/jasper/app/plugins/HostedKitTest.java",
     "recoloured.syntax(),recoloured.uiKeys(),recoloured.glass());", "recoloured.syntax(),recoloured.glass());", 1),
    ("jasper-app/src/test/java/dev/jasper/app/plugins/WorkspaceAppearanceFixture.java",
     "            base.syntax(), base.uiKeys(), base.glass());", "            base.syntax(), base.glass());", 1),
    ("jasper-ui/src/test/java/dev/jasper/ui/swing/SlateCobaltInstallTest.java",
     "assertThat(theme.uiKeys()).doesNotContainKeys(", "assertThat(UiColorKeys.flatLaf(theme, UiKeySet.DEFAULT)).doesNotContainKeys(", 1),
    ("jasper-ui/src/test/java/dev/jasper/ui/swing/SlateCobaltInstallTest.java",
     "import dev.jasper.ui.theme.JasperTheme;\n",
     "import dev.jasper.ui.theme.JasperTheme;\nimport dev.jasper.ui.theme.UiColorKeys;\nimport dev.jasper.ui.theme.UiKeySet;\n", 1),
    ("jasper-ui/src/test/java/dev/jasper/ui/theme/UiColorKeysTest.java",
     "                assertThat(keys).as(name + \", as carried\").containsExactlyEntriesOf(generated.uiKeys());\n", "", 1),
    ("jasper-ui/src/test/java/dev/jasper/ui/theme/UiColorKeysTest.java",
     "own roles and matching terminal selection, and the keys it carried before the seam.",
     "own roles and matching terminal selection, as the keys it carried before the seam were.", 1),
    ("jasper-ui/src/test/java/dev/jasper/ui/theme/UiColorKeysTest.java",
     "assertThat(UiColorKeys.flatLaf(palette, selection)).containsExactlyEntriesOf(expected(palette, selection));",
     "assertThat(UiColorKeys.flatLaf(theme(palette, selection), UiKeySet.DEFAULT)).containsExactlyEntriesOf(expected(palette, selection));", 1),
    ("jasper-ui/src/test/java/dev/jasper/ui/theme/UiColorKeysTest.java",
     "UiColorKeys.flatLaf(p, selection)", "UiColorKeys.flatLaf(JasperTheme.bundled().generated(dark), UiKeySet.DEFAULT)", 2),
    ("jasper-ui/src/test/java/dev/jasper/ui/theme/UiColorKeysTest.java",
     "    @Test void everyKeyIsARolesColour() {",
     "    /** Slate Cobalt Dark with {@code palette} and {@code selection} as its terminal selection. */\n"
     "    private static GeneratedTheme theme(SemanticPalette palette, Color selection) {\n"
     "        var base = JasperTheme.bundled().generated(true);\n"
     "        var t = base.terminal();\n"
     "        return new GeneratedTheme(base.name(), base.dark(), base.author(), palette,\n"
     "            new TerminalColors(t.background(), t.foreground(), t.cursor(), selection, t.ansi()), base.editor(), base.syntax(), base.glass());\n"
     "    }\n\n"
     "    @Test void everyKeyIsARolesColour() {", 1),
]
for name, old, new, count in EDITS:
    path = pathlib.Path(name)
    text = path.read_text(encoding="utf-8")
    if text.count(old) != count: raise SystemExit(f"{name}: expected {count} of {old!r}, found {text.count(old)}")
    path.write_text(text.replace(old, new), encoding="utf-8")
PY
git grep -n -e 'uiKeys' -e 'flatLaf(p,' -e 'flatLaf(palette,' -- '*.java' ':!jasper-ui/src/test/java/dev/jasper/ui/theme/SlateCobaltGeneratedTest.java'
```

Expected: the final `git grep` prints nothing (it leaves out `SlateCobaltGeneratedTest`, whose Step 1 assertion names
`"uiKeys"` as the record component that must be gone). In `UiColorKeysTest`, `everyKeyIsARolesColour` and
`textSelectionAndTheSearchTintKeepTheirSources` now take the keys of the generated variant, whose terminal colours are
the matching scheme's (`SlateCobaltGeneratedTest:16`), so their `selection` still names the same colour.

- [ ] **Step 4: Run the tests to verify they pass**

Run: `./gradlew :jasper-ui:test --tests 'dev.jasper.ui.theme.*' --tests 'dev.jasper.ui.swing.SlateCobaltInstallTest'`
Expected: PASS.

- [ ] **Step 5: Run the affected suites**

Run: `./gradlew :jasper-ui:check verifyUiArchitecture :jasper-app:test`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 6: Commit**

```bash
git add jasper-ui/src/main/java/dev/jasper/ui/theme jasper-ui/src/test/java/dev/jasper/ui/theme \
  jasper-ui/src/test/java/dev/jasper/ui/swing/SlateCobaltInstallTest.java jasper-ui/src/test/java/dev/jasper/ui/testsupport/KitFixtures.java \
  jasper-app/src/test/java/dev/jasper/app/appearance/ThemeTestSupport.java jasper-app/src/test/java/dev/jasper/app/plugins/HostedPalettesTest.java \
  jasper-app/src/test/java/dev/jasper/app/plugins/HostedKitTest.java jasper-app/src/test/java/dev/jasper/app/plugins/WorkspaceAppearanceFixture.java
git commit -F - <<'EOF'
Kit seam: GeneratedTheme no longer carries FlatLaf keys

The keys are derived when a theme is installed, so the record component,
the palette overload of UiColorKeys.flatLaf and their computation in
JasperTheme.build and TitleBarTheme go.

🤖 Generated with Claude Code at The Home Depot

Co-Authored-By: Claude <noreply@anthropic.com>
EOF
```

### Task 5: The kit package

`dev.jasper.ui.kit.Kit` bundles the catalog, the type scale, the faces and the key set. The package is JDK-only and
depends on `metrics` and `theme` only; `verifyUiArchitecture` gains each package's allowed outgoing edges, so the
package-info lists are checked rather than prose.

**Files:**
- Create: `jasper-ui/src/main/java/dev/jasper/ui/kit/Kit.java`, `jasper-ui/src/main/java/dev/jasper/ui/kit/package-info.java`
- Modify: `jasper-ui/src/main/java/dev/jasper/ui/swing/package-info.java:3`, `gradle/ui-architecture.gradle.kts:24-46`
- Test: `jasper-ui/src/test/java/dev/jasper/ui/kit/KitTest.java` (new)
- Modify test support: `jasper-ui/src/test/java/dev/jasper/ui/testsupport/KitFixtures.java:1-8` (imports) and a new `kit(DesignMetrics)` after `platformFamily()`

**Interfaces:**
- Consumes: `DesignMetrics.with` and value equality (Task 1); `TypeScale`, `FaceSet` (Task 2); `UiKeySet` (Task 3).
- Produces: `public record Kit(String id, DesignMetrics metrics, TypeScale type, FaceSet faces, UiKeySet uiKeys)` with
  `public static final Kit DEFAULT`; every component required (`NullPointerException` naming it) and
  `IllegalArgumentException("Blank kit id")`; `KitFixtures.kit(DesignMetrics metrics)` returns
  `new Kit("test", metrics, Kit.DEFAULT.type(), Kit.DEFAULT.faces(), Kit.DEFAULT.uiKeys())`.

- [ ] **Step 1: Write the failing test**

Create `jasper-ui/src/test/java/dev/jasper/ui/kit/KitTest.java`:

```java
package dev.jasper.ui.kit;

import static org.assertj.core.api.Assertions.*;

import dev.jasper.ui.metrics.DesignMetrics;
import dev.jasper.ui.metrics.FaceSet;
import dev.jasper.ui.metrics.TypeScale;
import dev.jasper.ui.theme.UiKeySet;
import org.junit.jupiter.api.Test;

/** Enterprise presentation spec 6.1: a kit names a look's catalog, type, faces and FlatLaf keys. */
class KitTest {
    @Test void theDefaultKitIsTodaysCatalogTypeFacesAndKeys() {
        assertThat(Kit.DEFAULT.id()).isEqualTo("default");
        assertThat(Kit.DEFAULT.metrics()).isSameAs(DesignMetrics.DEFAULT);
        assertThat(Kit.DEFAULT.type()).isSameAs(TypeScale.DEFAULT);
        assertThat(Kit.DEFAULT.faces()).isSameAs(FaceSet.PLATFORM);
        assertThat(Kit.DEFAULT.uiKeys()).isEqualTo(UiKeySet.DEFAULT);
    }

    @Test void everyPartIsRequired() {
        assertThatNullPointerException().isThrownBy(() -> new Kit(null, DesignMetrics.DEFAULT, TypeScale.DEFAULT, FaceSet.PLATFORM, UiKeySet.DEFAULT)).withMessage("id");
        assertThatNullPointerException().isThrownBy(() -> new Kit("k", null, TypeScale.DEFAULT, FaceSet.PLATFORM, UiKeySet.DEFAULT)).withMessage("metrics");
        assertThatNullPointerException().isThrownBy(() -> new Kit("k", DesignMetrics.DEFAULT, null, FaceSet.PLATFORM, UiKeySet.DEFAULT)).withMessage("type");
        assertThatNullPointerException().isThrownBy(() -> new Kit("k", DesignMetrics.DEFAULT, TypeScale.DEFAULT, null, UiKeySet.DEFAULT)).withMessage("faces");
        assertThatNullPointerException().isThrownBy(() -> new Kit("k", DesignMetrics.DEFAULT, TypeScale.DEFAULT, FaceSet.PLATFORM, null)).withMessage("uiKeys");
        assertThatIllegalArgumentException().isThrownBy(() -> new Kit(" ", DesignMetrics.DEFAULT, TypeScale.DEFAULT, FaceSet.PLATFORM, UiKeySet.DEFAULT))
            .withMessage("Blank kit id");
    }

    @Test void kitsCompareByValue() {
        var same = new Kit("default", DesignMetrics.DEFAULT.with(DesignMetrics.Role.HAIRLINE, 1), TypeScale.DEFAULT.withBody(13),
            FaceSet.PLATFORM, UiKeySet.DEFAULT);
        assertThat(same).isEqualTo(Kit.DEFAULT).hasSameHashCodeAs(Kit.DEFAULT).isNotSameAs(Kit.DEFAULT);
        assertThat(new Kit("default", DesignMetrics.DEFAULT.with(DesignMetrics.Role.HAIRLINE, 2), TypeScale.DEFAULT, FaceSet.PLATFORM,
            UiKeySet.DEFAULT)).isNotEqualTo(Kit.DEFAULT);
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `./gradlew :jasper-ui:test --tests 'dev.jasper.ui.kit.KitTest'`
Expected: FAIL in `:jasper-ui:compileTestJava` with `error: cannot find symbol` / `symbol: class Kit`.

- [ ] **Step 3: Add the kit**

Create `jasper-ui/src/main/java/dev/jasper/ui/kit/Kit.java`:

```java
package dev.jasper.ui.kit;

import dev.jasper.ui.metrics.DesignMetrics;
import dev.jasper.ui.metrics.FaceSet;
import dev.jasper.ui.metrics.TypeScale;
import dev.jasper.ui.theme.UiKeySet;
import java.util.Objects;

/**
 * A look: the metric catalog, type scale, faces and FlatLaf key set that the Swing kit and the native chrome paint
 * with. Immutable and compared by value; the host fixes one per process before any window.
 *
 * @param id a short name for diagnostics
 * @param metrics the logical geometry
 * @param type the type scale
 * @param faces the faces text draws in
 * @param uiKeys the FlatLaf colour keys the theme's roles fill
 */
public record Kit(String id, DesignMetrics metrics, TypeScale type, FaceSet faces, UiKeySet uiKeys) {
    /** The default look: the default catalog, the 13-point type scale, the platform faces and the default FlatLaf keys. */
    public static final Kit DEFAULT = new Kit("default", DesignMetrics.DEFAULT, TypeScale.DEFAULT, FaceSet.PLATFORM, UiKeySet.DEFAULT);

    /** Every part is required, and the id must not be blank. */
    public Kit {
        Objects.requireNonNull(id, "id"); Objects.requireNonNull(metrics, "metrics"); Objects.requireNonNull(type, "type");
        Objects.requireNonNull(faces, "faces"); Objects.requireNonNull(uiKeys, "uiKeys");
        if (id.isBlank()) throw new IllegalArgumentException("Blank kit id");
    }
}
```

Create `jasper-ui/src/main/java/dev/jasper/ui/kit/package-info.java`:

```java
/**
 * The look: a {@link dev.jasper.ui.kit.Kit} names the metric catalog, type scale, faces and FlatLaf key set that the
 * Swing kit and the native chrome paint with; {@link dev.jasper.ui.kit.Kit#DEFAULT} is the default look.
 * JDK-only. Allowed outgoing Jasper dependencies: dev.jasper.ui.metrics and dev.jasper.ui.theme.
 * Kits are immutable values; the host fixes one per process before any window.
 */
package dev.jasper.ui.kit;
```

In `jasper-ui/src/main/java/dev/jasper/ui/swing/package-info.java`, replace line 3:

```java
 * Allowed outgoing Jasper dependencies: dev.jasper.ui.model, dev.jasper.ui.theme, dev.jasper.ui.metrics and dev.jasper.ui.icons.
```

with:

```java
 * Allowed outgoing Jasper dependencies: dev.jasper.ui.model, dev.jasper.ui.theme, dev.jasper.ui.metrics, dev.jasper.ui.icons and dev.jasper.ui.kit.
```

In `jasper-ui/src/test/java/dev/jasper/ui/testsupport/KitFixtures.java`, add `import dev.jasper.ui.kit.Kit;` after
line 5 (`import dev.jasper.ui.icons.IconRegistry;`), and after the closing brace of `platformFamily()` (Task 2) add:

```java

    /** The default kit with {@code metrics}: a kit that differs from the default in geometry alone. */
    public static Kit kit(DesignMetrics metrics) {
        return new Kit("test", metrics, Kit.DEFAULT.type(), Kit.DEFAULT.faces(), Kit.DEFAULT.uiKeys());
    }
```

- [ ] **Step 4: Check every UI package's outgoing edges**

In `gradle/ui-architecture.gradle.kts`, replace line 24:

```kotlin
        val pure = setOf("dev.jasper.ui.theme", "dev.jasper.ui.model")
```

with:

```kotlin
        val pure = setOf("dev.jasper.ui.theme", "dev.jasper.ui.model", "dev.jasper.ui.kit")
        // Each package's allowed outgoing UI packages: the ones its package-info's "Allowed outgoing Jasper
        // dependencies:" line names; a package-info without that line (theme, model) allows none.
        val sources = uiProject.layout.projectDirectory.dir("src/main/java/dev/jasper/ui").asFile
        val listed = Regex("Allowed outgoing Jasper dependenc(?:y|ies): (.*)")
        val named = Regex("dev\\.jasper\\.ui\\.[a-z]+")
        val allowed = sources.listFiles().orEmpty().filter { it.resolve("package-info.java").isFile }.associate { dir ->
            val names = listed.find(dir.resolve("package-info.java").readText())?.groupValues?.get(1).orEmpty()
            "dev.jasper.ui.${dir.name}" to named.findAll(names).map { it.value }.toSet()
        }
```

replace lines 36-37:

```kotlin
            val jdkOnly = from == "dev.jasper.ui.theme" || from.startsWith("dev.jasper.ui.theme.")
                || from == "dev.jasper.ui.model" || from.startsWith("dev.jasper.ui.model.")
```

with:

```kotlin
            val jdkOnly = pure.any { from == it || from.startsWith("$it.") }
```

and replace lines 42-45:

```kotlin
            if (toClass.startsWith("dev.jasper.ui.")) {
                val to = toClass.substringBeforeLast('.')
                if (from != to) graph.getOrPut(from) { linkedSetOf() }.add(to)
            }
```

with:

```kotlin
            if (toClass.startsWith("dev.jasper.ui.")) {
                val to = toClass.substringBeforeLast('.')
                if (from != to) {
                    val permitted = allowed[from] ?: error("UI package without a package-info: $from")
                    check(to in permitted) { "UI package edge outside its package-info: $line" }
                    graph.getOrPut(from) { linkedSetOf() }.add(to)
                }
            }
```

The edges at `0af7b83e2` are `icons -> {metrics, model, theme}` and `swing -> {icons, metrics, model, theme}`; `theme`,
`model` and `metrics` have none. The package-info lines give `icons {theme, metrics, model}`, `metrics {theme}`,
`swing {model, theme, metrics, icons, kit}` (Step 3's edit) and `kit {metrics, theme}`, and `theme` and `model` carry no
such line, so the lists admit today's graph and Kit's `kit -> {metrics, theme}`; an edge a package-info does not name
fails the gate.

- [ ] **Step 5: Run the tests and the gate**

Run: `./gradlew :jasper-ui:test --tests 'dev.jasper.ui.kit.KitTest' verifyUiArchitecture`
Expected: PASS (3 tests) and BUILD SUCCESSFUL.

- [ ] **Step 6: Run the module's checks**

Run: `./gradlew :jasper-ui:check verifyUiArchitecture`
Expected: BUILD SUCCESSFUL (javadoc resolves the `{@link}`s in `Kit` and its package-info).

- [ ] **Step 7: Commit**

```bash
git add jasper-ui/src/main/java/dev/jasper/ui/kit jasper-ui/src/main/java/dev/jasper/ui/swing/package-info.java \
  jasper-ui/src/test/java/dev/jasper/ui/kit/KitTest.java jasper-ui/src/test/java/dev/jasper/ui/testsupport/KitFixtures.java \
  gradle/ui-architecture.gradle.kts
git commit -F - <<'EOF'
Kit seam: the kit package and its default kit

Kit names a look's metric catalog, type scale, faces and FlatLaf key
set; Kit.DEFAULT is today's look. The package is JDK-only and depends on
metrics and theme only, and verifyUiArchitecture now checks every UI
package's outgoing edges against the list its package-info names.

🤖 Generated with Claude Code at The Home Depot

Co-Authored-By: Claude <noreply@anthropic.com>
EOF
```

### Task 6: Install under a kit

`FlatLafInstaller.install(theme, kit)` takes its colour keys from `kit.uiKeys()` and its geometry from `kit.metrics()`,
and publishes the kit with the theme; `installedKit()` reads it back. `ThemeManager.install(theme, kit)` takes the split
divider from the kit, and `ThemeController.startup(…, kit, …)` carries the kit to every install of the process.
`JasperApplication` starts under `Kit.DEFAULT`.

**Files:**
- Modify: `jasper-ui/src/main/java/dev/jasper/ui/swing/FlatLafInstaller.java` (whole file; Task 3's version)
- Modify: `jasper-app/src/main/java/dev/jasper/app/appearance/ThemeManager.java` (whole file), `jasper-app/src/main/java/dev/jasper/app/appearance/ThemeController.java:4,48-83`, `jasper-app/src/main/java/dev/jasper/app/application/JasperApplication.java:251-252`, `jasper-app/src/main/java/dev/jasper/app/appearance/package-info.java:3-4`
- Test: `jasper-ui/src/test/java/dev/jasper/ui/swing/FlatLafInstallerKitTest.java` (new), `jasper-ui/src/test/java/dev/jasper/ui/swing/SlateCobaltInstallTest.java` (one new test), `jasper-app/src/test/java/dev/jasper/app/appearance/ThemeManagerTest.java` (whole file), `jasper-app/src/test/java/dev/jasper/app/application/JasperApplicationThemesTest.java:33,36`
- Modify tests (the install sites): `jasper-app/src/test/java/dev/jasper/app/appearance/ThemeTestSupport.java:47,73`, `jasper-ui/src/test/java/dev/jasper/ui/swing/AppearanceTransactionTest.java:59,70`, `jasper-app/src/test/java/dev/jasper/app/workspace/WorkspaceRolesTest.java:65`, and the 27 calls of the script in Step 5 in `ShippedControlReadabilityTest`, `TitleBarZoneTest`, `HostedKitTest`, `DesktopTestSupport`, `WorkspaceRolesTest`, `GalleryCoverageTest`, `KitGallery`, `AppearanceTransactionTest`, `KitCalibrationTest`, `KitKeyBindingAuditTest`, `NoticeKitTest`, `PickerMenusTest`, `SearchChoiceRefreshTest` and `SlateCobaltInstallTest`

**Interfaces:**
- Consumes: `Kit`, `Kit.DEFAULT`, `KitFixtures.kit(DesignMetrics)` (Task 5); `UiColorKeys.flatLaf(GeneratedTheme, UiKeySet)` (Task 3); `DesignMetrics.with` (Task 1).
- Produces: `public static void FlatLafInstaller.install(GeneratedTheme theme, Kit kit)`; `public static Kit FlatLafInstaller.installedKit()`
  (the look-and-feel default `Jasper.installedKit`, `Kit.DEFAULT` when absent); package-private
  `static boolean ThemeManager.install(GeneratedTheme theme, Kit kit)`;
  `public static ThemeController ThemeController.startup(boolean dark, ThemeSelection selection, Kit kit, Consumer<ThemeController.InstallationFailure> fallingBack)`.
  The one-argument `install` methods and `startup(boolean, ThemeSelection, Consumer)` are removed.

- [ ] **Step 1: Write the failing tests**

Create `jasper-ui/src/test/java/dev/jasper/ui/swing/FlatLafInstallerKitTest.java`:

```java
package dev.jasper.ui.swing;

import static org.assertj.core.api.Assertions.*;

import dev.jasper.ui.kit.Kit;
import dev.jasper.ui.metrics.DesignMetrics;
import dev.jasper.ui.testsupport.Edt;
import dev.jasper.ui.testsupport.KitFixtures;
import dev.jasper.ui.theme.ThemeBinding;
import dev.jasper.ui.theme.ThemePair;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import javax.swing.LookAndFeel;
import javax.swing.UIManager;
import javax.swing.UnsupportedLookAndFeelException;
import org.assertj.core.data.Offset;
import org.junit.jupiter.api.Test;

/** The kit an appearance is installed under travels with its look and feel, like the theme, and gives FlatLaf's geometry. */
class FlatLafInstallerKitTest {
    /** The default kit with other geometry: radius 2 (arcs 4), a 2 px focus ring and a 40% dark popup shadow. */
    private static Kit other() {
        return KitFixtures.kit(Kit.DEFAULT.metrics().with(DesignMetrics.Role.CONTROL_RADIUS, 2).with(DesignMetrics.Role.FOCUS_RING, 2)
            .with(DesignMetrics.Role.POPOVER_SHADOW_PERCENT_DARK, 40));
    }

    /** Review Focus 1: before any Jasper look and feel the installed kit is the default; it never throws. */
    @Test void withoutAJasperLookAndFeelTheInstalledKitIsTheDefault() {
        Edt.run(() -> {
            var prior = UIManager.getLookAndFeel();
            try {
                set(new javax.swing.plaf.metal.MetalLookAndFeel());
                assertThat(FlatLafInstaller.installedKit()).isSameAs(Kit.DEFAULT);
                assertThatIllegalStateException().as("the theme, unlike the kit, has no default").isThrownBy(FlatLafInstaller::installedTheme);
            } finally { set(prior); }
        });
    }

    /** The seam: one altered metric moves FlatLaf's extras; a same-instance reinstall republishes the kit (Review Focus 3). */
    @Test void theGeometryAndThePopupShadowFollowTheInstalledKit() {
        Edt.run(() -> {
            var prior = UIManager.getLookAndFeel();
            try {
                var kit = other();
                FlatLafInstaller.install(KitFixtures.dark(), kit);
                assertThat(FlatLafInstaller.installedKit()).isSameAs(kit);
                for (var key : List.of("Button.arc", "Component.arc", "TextComponent.arc")) assertThat(UIManager.getInt(key)).as(key).isEqualTo(4);
                assertThat(UIManager.getInt("Component.focusWidth")).isEqualTo(2);
                assertThat(((Number) UIManager.get("Popup.dropShadowOpacity")).doubleValue()).isCloseTo(.4, Offset.offset(1e-6));
                set(UIManager.getLookAndFeel());
                assertThat(FlatLafInstaller.installedKit()).as("a same-instance reinstall republishes the kit").isSameAs(kit);
                assertThat(((Number) UIManager.get("Popup.dropShadowOpacity")).doubleValue()).isCloseTo(.4, Offset.offset(1e-6));
                FlatLafInstaller.install(KitFixtures.dark(), Kit.DEFAULT);
                assertThat(FlatLafInstaller.installedKit()).isSameAs(Kit.DEFAULT);
                assertThat(UIManager.getInt("Button.arc")).isEqualTo(8);
                assertThat(UIManager.getInt("Component.focusWidth")).isEqualTo(3);
                assertThat(((Number) UIManager.get("Popup.dropShadowOpacity")).doubleValue()).isCloseTo(.5, Offset.offset(1e-6));
            } finally { set(prior); }
        });
    }

    /** Review Focus 2: a rolled-back install restores the previous kit with the previous theme. */
    @Test void aFailedInstallRestoresThePreviousKitAndItsGeometry() {
        Edt.run(() -> {
            var prior = UIManager.getLookAndFeel();
            var kit = other();
            try (var binding = new ThemeBinding(new ThemePair(KitFixtures.dark(), KitFixtures.dark()), failure -> {})) {
                FlatLafInstaller.install(KitFixtures.dark(), kit);
                var transaction = new AppearanceTransaction(binding, theme -> {
                    FlatLafInstaller.install(theme, Kit.DEFAULT);
                    throw new IllegalStateException("injected");
                }, theme -> {});
                assertThatIllegalStateException().isThrownBy(() -> transaction.apply(new ThemePair(KitFixtures.light(), KitFixtures.light())))
                    .withMessage("injected");
                assertThat(FlatLafInstaller.installedKit()).isSameAs(kit);
                assertThat(FlatLafInstaller.installedTheme()).isSameAs(KitFixtures.dark());
                assertThat(UIManager.getInt("Component.arc")).isEqualTo(4);
            } finally { set(prior); }
        });
    }

    /** Review Focus 1: plugin threads read the installed kit (named icons, presentation icons) off the EDT. */
    @Test void theInstalledKitCanBeReadOffTheEventThread() throws Exception {
        var kit = other();
        var prior = new AtomicReference<LookAndFeel>();
        Edt.run(() -> { prior.set(UIManager.getLookAndFeel()); FlatLafInstaller.install(KitFixtures.dark(), kit); });
        try {
            var seen = new AtomicReference<Object>();
            var reader = new Thread(() -> {
                try { seen.set(FlatLafInstaller.installedKit()); } catch (RuntimeException failure) { seen.set(failure); }
            });
            reader.start();
            reader.join(10_000);
            assertThat(seen.get()).isSameAs(kit);
        } finally { Edt.run(() -> set(prior.get())); }
    }

    private static void set(LookAndFeel laf) {
        try { UIManager.setLookAndFeel(laf); } catch (UnsupportedLookAndFeelException failure) { throw new AssertionError(failure); }
    }
}
```

In `jasper-ui/src/test/java/dev/jasper/ui/swing/SlateCobaltInstallTest.java`, insert after the closing brace of
`flatLafGeometryComesFromTheCompactScale` (line 56 at `0af7b83e2`, line 58 after Task 4's two imports):

```java

    /** Enterprise presentation plan 1: the default kit's FlatLaf extras, frozen whole; the seam changed no key or value. */
    @Test void theDefaultKitsExtrasAreTheFormerOnes() {
        Edt.run(() -> {
            var prior = UIManager.getLookAndFeel();
            try {
                FlatLafInstaller.install(JasperTheme.bundled().generated(true), dev.jasper.ui.kit.Kit.DEFAULT);
                var expected = new java.util.LinkedHashMap<String, String>();
                for (var key : List.of("Component.arc", "TextComponent.arc", "Button.arc")) expected.put(key, "8");
                expected.put("Component.focusWidth", "3");
                expected.put("Button.minimumHeight", "24");
                expected.put("Component.minimumHeight", "24");
                expected.put("Button.margin", "2,14,2,14");
                expected.put("Button.minimumWidth", "72");
                for (var key : List.of("List", "Tree", "Table", "ComboBox", "MenuItem")) {
                    expected.put(key + ".selectionArc", "0");
                    expected.put(key + ".selectionInsets", "0,0,0,0");
                }
                for (var key : List.of("MenuItem", "Menu", "CheckBoxMenuItem", "RadioButtonMenuItem")) expected.put(key + ".margin", "3,8,3,8");
                expected.put("PopupMenu.borderInsets", "4,1,4,1");
                expected.put("Popup.borderCornerRadius", "6");
                expected.put("PopupMenu.borderCornerRadius", "6");
                assertThat(((com.formdev.flatlaf.FlatLaf) UIManager.getLookAndFeel()).getExtraDefaults()).isEqualTo(expected);
                assertThat(UIManager.getLookAndFeelDefaults().get("Jasper.installedKit")).isSameAs(dev.jasper.ui.kit.Kit.DEFAULT);
            } finally {
                try { UIManager.setLookAndFeel(prior); } catch (UnsupportedLookAndFeelException failure) { throw new AssertionError(failure); }
            }
        });
    }
```

Replace the whole of `jasper-app/src/test/java/dev/jasper/app/appearance/ThemeManagerTest.java` with (Task 3's two
re-expressed tests and the four others keep their assertions; every install names its kit; the last two tests are new):

```java
package dev.jasper.app.appearance;
import dev.jasper.ui.theme.GeneratedTheme;

import com.formdev.flatlaf.FlatLaf;
import dev.jasper.app.config.TerminalSchemeChoice;
import dev.jasper.app.testsupport.EdtTestExtension;
import dev.jasper.ui.kit.Kit;
import dev.jasper.ui.metrics.DesignMetrics;
import java.awt.Color;
import java.util.Map;
import javax.swing.LookAndFeel;
import javax.swing.UIManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import static org.assertj.core.api.Assertions.*;

@ExtendWith(EdtTestExtension.class)
class ThemeManagerTest {
    @Test void keysFlatLafSkipsAreRestoredWithoutReplacingItsOwn() throws Exception {
        LookAndFeel original = UIManager.getLookAndFeel();
        try {
            // The theme's own roles behind keys FlatLaf skips or derives itself, and an alpha-carrying role (border.subtle).
            var theme = ThemeTestSupport.recolored(ThemeTestSupport.generated(true), Map.of(
                dev.jasper.ui.theme.SemanticColor.SURFACE_APP, new Color(0x12, 0x34, 0x56),
                dev.jasper.ui.theme.SemanticColor.SURFACE_INPUT, new Color(0x01, 0x01, 0x01)));
            assertThat(ThemeManager.install(theme, Kit.DEFAULT)).isTrue();
            var expected = Map.of("SearchEverywhere.Header.background", "#123456", "ComboBox.background", "#010101",
                "Separator.foreground", hex(theme.semantic().color(dev.jasper.ui.theme.SemanticColor.BORDER_SUBTLE)));
            assertThat(expected.get("Separator.foreground")).as("Slate Cobalt Dark's border.subtle carries alpha").hasSize(9);
            assertColours(expected);
            // A theme's own raw colours win consistently, including alpha and same-instance reinstall.
            UIManager.setLookAndFeel(UIManager.getLookAndFeel());
            assertColours(expected);
        } finally { UIManager.setLookAndFeel(original); }
    }

    @Test void restoredAndDerivedKeysSurviveReinstallingTheSameLookAndFeel() throws Exception {
        LookAndFeel original = UIManager.getLookAndFeel();
        try {
            var light = ThemeTestSupport.generated(false);
            ThemeManager.install(light, Kit.DEFAULT);
            LookAndFeel lightLaf = UIManager.getLookAndFeel();
            ThemeManager.install(ThemeTestSupport.generated(true), Kit.DEFAULT);
            // ThemeController rolls back like this when a later install fails.
            UIManager.setLookAndFeel(lightLaf);
            assertColours(Map.of("Jasper.semantic.surface.app", hex(light.semantic().color(dev.jasper.ui.theme.SemanticColor.SURFACE_APP)),
                "Jasper.semantic.accent", hex(light.semantic().color(dev.jasper.ui.theme.SemanticColor.ACCENT))));
            assertThat(UIManager.getFont("Label.font").getSize2D()).isEqualTo(13f);
        } finally { UIManager.setLookAndFeel(original); }
    }

    @Test void switchingThemesLeavesNothingOfThePreviousOneBehind() throws Exception {
        LookAndFeel original = UIManager.getLookAndFeel();
        try {
            ThemeManager.install(ThemeTestSupport.generated(false), Kit.DEFAULT);
            // A key only the previous look and feel's defaults hold.
            UIManager.getLookAndFeelDefaults().put("Plugins.hoverBackground", new javax.swing.plaf.ColorUIResource(new Color(0x12, 0x34, 0x56)));
            assertThat(UIManager.getColor("Plugins.hoverBackground")).isNotNull();
            var dark = ThemeTestSupport.generated(true);
            ThemeManager.install(dark, Kit.DEFAULT);
            assertThat(UIManager.getColor("Plugins.hoverBackground")).isNull();
            assertColours(Map.of("ToolWindow.background",
                (String) dev.jasper.ui.theme.UiColorKeys.flatLaf(dark, dev.jasper.ui.theme.UiKeySet.DEFAULT).get("ToolWindow.background"),
                "Jasper.semantic.surface.app", hex(dark.semantic().color(dev.jasper.ui.theme.SemanticColor.SURFACE_APP))));
        } finally { UIManager.setLookAndFeel(original); }
    }

    @Test void everyBuiltInVariantInstallsAsTheLookAndFeel() throws Exception {
        LookAndFeel original = UIManager.getLookAndFeel();
        try {
            for (var builtIn : dev.jasper.ui.theme.JasperTheme.builtIns()) {
                for (boolean dark : new boolean[] {true, false}) {
                    if (!builtIn.has(dark)) continue;
                    var theme = builtIn.generated(dark);
                    assertThat(ThemeManager.install(theme, Kit.DEFAULT)).as("%s %s", builtIn.id(), dark ? "dark" : "light").isTrue();
                    assertThat(UIManager.getLookAndFeel().getName()).isEqualTo(builtIn.name());
                    assertThat(FlatLaf.isLafDark()).as(builtIn.id()).isEqualTo(dark);
                    assertThat(UIManager.getColor("Panel.background")).as(builtIn.id())
                        .isEqualTo(theme.semantic().color(dev.jasper.ui.theme.SemanticColor.SURFACE_PANEL));
                }
            }
        } finally { UIManager.setLookAndFeel(original); }
    }

    private static void assertColours(Map<String, String> expected) {
        expected.forEach((key, value) -> assertThat(hex(UIManager.getColor(key))).as(key).isEqualTo(value));
    }

    @Test void aGeneratedThemeInstallsItsRolesAsTheLookAndFeel() throws Exception {
        LookAndFeel original = UIManager.getLookAndFeel();
        try {
            GeneratedTheme theme = ThemeTestSupport.generated(true);
            var roles = theme.semantic();
            assertThat(ThemeManager.install(theme, Kit.DEFAULT)).isTrue();
            assertThat(UIManager.getLookAndFeel().getName()).isEqualTo("Slate Cobalt");
            assertThat(FlatLaf.isLafDark()).isTrue();
            assertThat(UIManager.getColor("Panel.background")).isEqualTo(roles.color(dev.jasper.ui.theme.SemanticColor.SURFACE_PANEL));
            assertThat(UIManager.getColor("List.background")).isEqualTo(roles.color(dev.jasper.ui.theme.SemanticColor.SURFACE_CARD));
            assertThat(UIManager.getColor("EditorTabs.underlineColor")).as("a key FlatLaf skips is put back")
                .isEqualTo(roles.color(dev.jasper.ui.theme.SemanticColor.TAB_INDICATOR));
            assertThat(dev.jasper.ui.swing.FlatLafInstaller.installedTheme().semantic().color(dev.jasper.ui.theme.SemanticColor.SURFACE_INPUT))
                .as("native search reads its canonical role directly").isEqualTo(roles.color(dev.jasper.ui.theme.SemanticColor.SURFACE_INPUT));
            assertThat(UIManager.getInt("TextComponent.arc")).isEqualTo(2 * dev.jasper.ui.metrics.DesignMetrics.DEFAULT.value(dev.jasper.ui.metrics.DesignMetrics.Role.CONTROL_RADIUS));
            assertThat(UIManager.getFont("Label.font").getSize2D()).as("canonical module typography replaces the legacy app override").isEqualTo(13f);
        } finally { UIManager.setLookAndFeel(original); }
    }

    /** The 2026-10-01 spike: FlatLaf re-derives its controls from the base variables the theme sets. */
    @Test void flatLafReDerivesUnsetControlsFromTheBaseVariables() throws Exception {
        LookAndFeel original = UIManager.getLookAndFeel();
        try {
            ThemeManager.install(ThemeTestSupport.generated(true), Kit.DEFAULT);
            Color first = UIManager.getColor("Button.disabledSelectedBackground");
            ThemeManager.install(ThemeTestSupport.generated(false), Kit.DEFAULT);
            assertThat(UIManager.getColor("Button.disabledSelectedBackground")).isNotEqualTo(first);
        } finally { UIManager.setLookAndFeel(original); }
    }

    /** Enterprise presentation plan 1: the app's split divider is the kit's, and the kit travels with the look and feel. */
    @Test void theSplitDividerComesFromTheKitAndTheKitTravelsWithTheLookAndFeel() throws Exception {
        LookAndFeel original = UIManager.getLookAndFeel();
        try {
            var kit = divider();
            assertThat(ThemeManager.install(ThemeTestSupport.generated(true), kit)).isTrue();
            assertThat(UIManager.getInt("SplitPane.dividerSize")).isEqualTo(5);
            assertThat(dev.jasper.ui.swing.FlatLafInstaller.installedKit()).isSameAs(kit);
            assertThat(UIManager.getBoolean("TitlePane.centerTitle")).as("still unconditional in plan 1").isTrue();
            ThemeManager.install(ThemeTestSupport.generated(true), Kit.DEFAULT);
            assertThat(UIManager.getInt("SplitPane.dividerSize")).isEqualTo(8);
            assertThat(dev.jasper.ui.swing.FlatLafInstaller.installedKit()).isSameAs(Kit.DEFAULT);
        } finally { UIManager.setLookAndFeel(original); }
    }

    /** Start-up installs under its kit, and every later install of the same controller keeps it. */
    @Test void startUpInstallsUnderItsKitAndConfigureKeepsIt() throws Exception {
        LookAndFeel original = UIManager.getLookAndFeel();
        var kit = divider();
        try (var controller = ThemeController.startup(true, ThemeSelection.slateCobalt(TerminalSchemeChoice.MATCH), kit,
                failure -> { throw new AssertionError(failure); })) {
            assertThat(dev.jasper.ui.swing.FlatLafInstaller.installedKit()).isSameAs(kit);
            assertThat(UIManager.getInt("SplitPane.dividerSize")).isEqualTo(5);
            controller.configure(false, TerminalSchemeChoice.MATCH);
            assertThat(dev.jasper.ui.swing.FlatLafInstaller.installedKit()).as("configure reinstalls under the start-up kit").isSameAs(kit);
            assertThat(UIManager.getInt("SplitPane.dividerSize")).isEqualTo(5);
        } finally {
            ThemeTestSupport.controller(true).close();
            UIManager.setLookAndFeel(original);
        }
    }

    /** The default kit with the split divider at 5. */
    private static Kit divider() {
        return new Kit("divider", Kit.DEFAULT.metrics().with(DesignMetrics.Role.NATIVE_SPLIT_DIVIDER, 5), Kit.DEFAULT.type(),
            Kit.DEFAULT.faces(), Kit.DEFAULT.uiKeys());
    }

    private static String hex(Color color) {
        if (color == null) return null;
        String rgb = String.format("#%06x", color.getRGB() & 0xffffff);
        return color.getAlpha() == 255 ? rgb : rgb + String.format("%02x", color.getAlpha());
    }
}
```

In `jasper-app/src/test/java/dev/jasper/app/application/JasperApplicationThemesTest.java`, after line 33
(`...assertThatIllegalStateException().isThrownBy(dev.jasper.ui.swing.FlatLafInstaller::installedTheme);`) insert:

```java
                assertThat(dev.jasper.ui.swing.FlatLafInstaller.installedKit()).as("no Jasper look and feel yet: the default kit")
                    .isSameAs(dev.jasper.ui.kit.Kit.DEFAULT);
```

and after line 36 (`assertThat(dev.jasper.ui.swing.FlatLafInstaller.installedTheme()).isSameAs(expected);`, line 38
once the first insertion is in) insert:

```java
                assertThat(UIManager.getLookAndFeelDefaults().get("Jasper.installedKit")).as("start-up publishes its kit with the theme")
                    .isSameAs(dev.jasper.ui.kit.Kit.DEFAULT);
```

- [ ] **Step 2: Run the tests to verify they fail**

Run: `./gradlew :jasper-ui:test --tests 'dev.jasper.ui.swing.FlatLafInstallerKitTest'`
Expected: FAIL in `:jasper-ui:compileTestJava` with `error: cannot find symbol` / `symbol: method installedKit()` and
`method install(GeneratedTheme,Kit)`.

- [ ] **Step 3: Install under a kit and publish it**

Replace the whole of `jasper-ui/src/main/java/dev/jasper/ui/swing/FlatLafInstaller.java` with:

```java
package dev.jasper.ui.swing;

import com.formdev.flatlaf.FlatLaf;
import com.formdev.flatlaf.IntelliJTheme;
import dev.jasper.ui.kit.Kit;
import dev.jasper.ui.theme.*;
import dev.jasper.ui.metrics.DesignMetrics;
import java.awt.EventQueue;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import javax.swing.UIManager;
import javax.swing.plaf.ColorUIResource;

/** Real FlatLaf installation. Authored colors are retained; geometry is owned by the kit. EDT only. */
public final class FlatLafInstaller {
    private FlatLafInstaller() {}
    /** Installed immutable snapshot; stored with the LAF so appearance rollback restores it atomically. */
    public static GeneratedTheme installedTheme() {
        Object value = UIManager.getLookAndFeelDefaults().get("Jasper.installedTheme");
        if (value instanceof GeneratedTheme theme) return theme;
        throw new IllegalStateException("No Jasper theme installed");
    }
    /**
     * The kit the installed appearance was installed under, stored with the LAF beside the theme so rollback restores
     * both; {@link Kit#DEFAULT} when no Jasper look and feel is installed. Safe on any thread.
     */
    public static Kit installedKit() {
        return UIManager.getLookAndFeelDefaults().get("Jasper.installedKit") instanceof Kit kit ? kit : Kit.DEFAULT;
    }
    private static final class SnapshotLaf extends IntelliJTheme.ThemeLaf {
        private final GeneratedTheme snapshot;
        private final Kit kit;
        private final Map<String, Object> ui;
        SnapshotLaf(IntelliJTheme source, GeneratedTheme snapshot, Kit kit, Map<String, Object> ui) {
            super(source); this.snapshot = snapshot; this.kit = kit; this.ui = ui;
        }
        @Override public javax.swing.UIDefaults getDefaults() {
            var defaults = super.getDefaults();
            publish(defaults, snapshot, kit, ui);
            return defaults;
        }
    }
    /** Installs {@code theme} under {@code kit}: FlatLaf's colour keys in the kit's key set, its geometry from the kit's metrics. */
    public static void install(GeneratedTheme theme, Kit kit) {
        if (!EventQueue.isDispatchThread()) throw new IllegalStateException("Appearance requires EDT");
        Objects.requireNonNull(theme); Objects.requireNonNull(kit);
        // One map, computed from the theme's roles, feeds FlatLaf's input and every republish of the explicit colours.
        var ui = UiColorKeys.flatLaf(theme, kit.uiKeys());
        String json = FlatLafThemeData.json(theme.name(), theme.dark(), theme.author(), ui);
        FlatLaf laf;
        try { laf = new SnapshotLaf(new IntelliJTheme(new ByteArrayInputStream(json.getBytes(StandardCharsets.UTF_8))), theme, kit, ui); }
        catch (IOException failure) { throw new UncheckedIOException(failure); }
        var fixed = new LinkedHashMap<String, String>();
        var metrics = kit.metrics();
        String control = Integer.toString(metrics.value(DesignMetrics.Role.CONTROL_RADIUS) * 2);
        fixed.put("Component.arc", control);
        fixed.put("TextComponent.arc", control);
        fixed.put("Button.arc", control);
        fixed.put("Component.focusWidth", Integer.toString(metrics.value(DesignMetrics.Role.FOCUS_RING)));
        fixed.put("Button.minimumHeight", Integer.toString(metrics.value(DesignMetrics.Role.BUTTON_HEIGHT)));
        fixed.put("Component.minimumHeight", Integer.toString(metrics.value(DesignMetrics.Role.FIELD_HEIGHT)));
        // Buttons as on main: a 14 px label margin and a 72 px minimum width (a window footer's action).
        int label = metrics.value(DesignMetrics.Role.BUTTON_LABEL_MARGIN), side = metrics.value(DesignMetrics.Role.BUTTON_HORIZONTAL_PADDING);
        fixed.put("Button.margin", insets(label, side, label, side));
        fixed.put("Button.minimumWidth", Integer.toString(metrics.value(DesignMetrics.Role.WINDOW_ACTION_MIN_WIDTH)));
        // Selection is a plain rectangle, edge to edge.
        for (var key : java.util.List.of("List", "Tree", "Table", "ComboBox", "MenuItem")) {
            fixed.put(key + ".selectionArc", "0");
            fixed.put(key + ".selectionInsets", "0,0,0,0");
        }
        // Native menu rows match the kit's: a 22 px row from a 16 px line, 8 px sides, 4 px popup padding, radius 6.
        int row = metrics.value(DesignMetrics.Role.MENU_ITEM_VERTICAL_PADDING), inset = metrics.value(DesignMetrics.Role.MENU_ITEM_PADDING);
        for (var key : java.util.List.of("MenuItem", "Menu", "CheckBoxMenuItem", "RadioButtonMenuItem")) fixed.put(key + ".margin", insets(row, inset, row, inset));
        int padding = metrics.value(DesignMetrics.Role.MENU_PADDING), line = metrics.value(DesignMetrics.Role.HAIRLINE);
        fixed.put("PopupMenu.borderInsets", insets(padding, line, padding, line));
        String radius = Integer.toString(metrics.value(DesignMetrics.Role.CARD_RADIUS));
        fixed.put("Popup.borderCornerRadius", radius);
        fixed.put("PopupMenu.borderCornerRadius", radius);
        laf.setExtraDefaults(fixed);
        try { UIManager.setLookAndFeel(laf); }
        catch (javax.swing.UnsupportedLookAndFeelException failure) { throw new IllegalStateException("Cannot install generated appearance", failure); }
    }

    private static String insets(int top, int left, int bottom, int right) { return top + "," + left + "," + bottom + "," + right; }

    // A LAF reinstall (including rollback) obtains fresh defaults; publish the same immutable snapshot and kit every time.
    private static void publish(javax.swing.UIDefaults defaults, GeneratedTheme theme, Kit kit, Map<String, Object> ui) {
        defaults.put("Jasper.installedTheme", theme);
        defaults.put("Jasper.installedKit", kit);
        FlatLafThemeData.explicitColors(ui).forEach((key, color) -> defaults.put(key, new ColorUIResource(color)));
        for (var role : SemanticColor.values()) defaults.put("Jasper.semantic." + role.key(), new ColorUIResource(theme.semantic().color(role)));
        // Popovers and menus: FlatLaf's painted popup shadow (macOS keeps its native one), black at theme.md's strength.
        defaults.put("Popup.dropShadowColor", new ColorUIResource(java.awt.Color.BLACK));
        defaults.put("Popup.dropShadowOpacity", kit.metrics().value(theme.dark()
            ? DesignMetrics.Role.POPOVER_SHADOW_PERCENT_DARK : DesignMetrics.Role.POPOVER_SHADOW_PERCENT_LIGHT) / 100f);
        var palette = theme.semantic();
        // FlatLaf primitives also inherit the canonical roles; kit paints use the palette directly.
        Map<String, SemanticColor> roles = Map.ofEntries(
            Map.entry("Panel.background", SemanticColor.SURFACE_PANEL), Map.entry("Label.foreground", SemanticColor.TEXT_PRIMARY),
            Map.entry("TextField.background", SemanticColor.SURFACE_INPUT), Map.entry("TextField.foreground", SemanticColor.TEXT_PRIMARY),
            Map.entry("PasswordField.background", SemanticColor.SURFACE_INPUT), Map.entry("PasswordField.foreground", SemanticColor.TEXT_PRIMARY),
            Map.entry("Component.borderColor", SemanticColor.BORDER_INPUT), Map.entry("Component.focusColor", SemanticColor.BORDER_FOCUS),
            Map.entry("Button.background", SemanticColor.BUTTON_SECONDARY_BG), Map.entry("Button.foreground", SemanticColor.TEXT_STRONG_SECONDARY),
            Map.entry("List.selectionBackground", SemanticColor.SELECTION_BG), Map.entry("List.selectionForeground", SemanticColor.TEXT_ONSELECTION));
        roles.forEach((key, role) -> defaults.put(key, new ColorUIResource(palette.color(role))));
    }
}
```

`AppearanceTransaction` needs no change: `Jasper.installedKit` is one of the raw look-and-feel defaults its snapshot
restores, exactly as it restores `Jasper.installedTheme`.

Replace the whole of `jasper-app/src/main/java/dev/jasper/app/appearance/ThemeManager.java` with:

```java
package dev.jasper.app.appearance;
import dev.jasper.ui.kit.Kit;
import dev.jasper.ui.theme.GeneratedTheme;
import javax.swing.SwingUtilities;

/** EDT installation policy; all generation and installation use the shared module. */
public final class ThemeManager {
    private ThemeManager() {}

    /** Installs the module snapshot under {@code kit} plus app-owned native window defaults; installation failures propagate. */
    static boolean install(GeneratedTheme theme, Kit kit) {
        requireEdt();
        dev.jasper.ui.swing.FlatLafInstaller.install(theme, kit);
        // Native delegate installation only; all palette/style generation belongs to jasper-ui.
        var laf = (com.formdev.flatlaf.FlatLaf) javax.swing.UIManager.getLookAndFeel();
        var extra = new java.util.LinkedHashMap<>(laf.getExtraDefaults());
        int divider = kit.metrics().value(dev.jasper.ui.metrics.DesignMetrics.Role.NATIVE_SPLIT_DIVIDER);
        extra.put("SplitPane.dividerSize", Integer.toString(divider));
        extra.put("SplitPaneDivider.border", "dev.jasper.app.workspace.SplitDividerBorder");
        extra.put("TitlePane.centerTitle", "true");
        laf.setExtraDefaults(extra);
        var defaults = javax.swing.UIManager.getLookAndFeelDefaults();
        defaults.put("SplitPane.dividerSize", divider);
        defaults.put("SplitPaneDivider.border", new javax.swing.UIDefaults.ProxyLazyValue("dev.jasper.app.workspace.SplitDividerBorder"));
        defaults.put("TitlePane.centerTitle", true);
        return true;
    }

    private static void requireEdt() {
        if (!SwingUtilities.isEventDispatchThread()) throw new IllegalStateException("Theme operations require the EDT");
    }
}
```

In `jasper-app/src/main/java/dev/jasper/app/appearance/ThemeController.java`, replace lines 48-83 (from the `/**` of
the first `startup` through the closing brace of
`ThemeController(boolean dark, ThemeSelection selection, Predicate<GeneratedTheme> installer)`), then add
`import dev.jasper.ui.kit.Kit;` after line 4 (`import dev.jasper.app.config.UiFontConfig;`). Lines 48-83:

```java
    /**
     * Start-up selection: {@code selection} in {@code dark}, or Slate Cobalt Dark when that cannot be installed. A failed
     * attempt is rolled back completely, and the fallback is installed before this returns, so nothing can read the
     * installed snapshot (ThemeZone, named icons, native chrome) before one exists. The fallback's own failure propagates.
     */
    public static ThemeController startup(boolean dark, ThemeSelection selection, java.util.function.Consumer<InstallationFailure> fallingBack) {
        return startup(dark, selection, ThemeManager::install, fallingBack);
    }
    /** As {@link #startup(boolean, ThemeSelection, java.util.function.Consumer)} for Slate Cobalt with {@code scheme}. */
    public static ThemeController startup(boolean dark, TerminalSchemeChoice scheme, java.util.function.Consumer<InstallationFailure> fallingBack) {
        return startup(dark, ThemeSelection.slateCobalt(scheme), fallingBack);
    }
    static ThemeController startup(boolean dark, TerminalSchemeChoice scheme, Predicate<GeneratedTheme> installer,
                                   java.util.function.Consumer<InstallationFailure> fallingBack) {
        return startup(dark, ThemeSelection.slateCobalt(scheme), installer, fallingBack);
    }
    static ThemeController startup(boolean dark, ThemeSelection selection, Predicate<GeneratedTheme> installer,
                                   java.util.function.Consumer<InstallationFailure> fallingBack) {
        try { return new ThemeController(new Shown(dark, selection), installer); }
        catch (InstallationFailure failure) {
            fallingBack.accept(failure);
            var cobalt = JasperTheme.bundled();
            return new ThemeController(new Shown(true, new ThemeSelection(selection.library(), cobalt, cobalt, selection.scheme())), installer);
        }
    }

    /** Slate Cobalt in {@code dark} with {@code scheme}. */
    public ThemeController(boolean dark, TerminalSchemeChoice scheme) { this(dark, scheme, ThemeManager::install); }
    ThemeController(boolean dark, TerminalSchemeChoice scheme, Predicate<GeneratedTheme> installer) {
        this(new Shown(dark, ThemeSelection.slateCobalt(Objects.requireNonNull(scheme))), installer);
    }
    /** {@code selection} in {@code dark}. */
    public ThemeController(boolean dark, ThemeSelection selection) { this(new Shown(dark, Objects.requireNonNull(selection)), ThemeManager::install); }
    ThemeController(boolean dark, ThemeSelection selection, Predicate<GeneratedTheme> installer) {
        this(new Shown(dark, Objects.requireNonNull(selection)), installer);
    }
```

with:

```java
    /**
     * Start-up selection under {@code kit}: {@code selection} in {@code dark}, or Slate Cobalt Dark when that cannot be
     * installed. Every install of the controller (the fallback's and every later configuration's) uses {@code kit}. A
     * failed attempt is rolled back completely, and the fallback is installed before this returns, so nothing can read
     * the installed snapshot (ThemeZone, named icons, native chrome) before one exists. The fallback's own failure
     * propagates.
     */
    public static ThemeController startup(boolean dark, ThemeSelection selection, Kit kit,
                                          java.util.function.Consumer<InstallationFailure> fallingBack) {
        Objects.requireNonNull(kit);
        return startup(dark, selection, theme -> ThemeManager.install(theme, kit), fallingBack);
    }
    /**
     * As {@link #startup(boolean, ThemeSelection, Kit, java.util.function.Consumer)} for Slate Cobalt with {@code scheme},
     * under the default kit.
     */
    public static ThemeController startup(boolean dark, TerminalSchemeChoice scheme, java.util.function.Consumer<InstallationFailure> fallingBack) {
        return startup(dark, ThemeSelection.slateCobalt(scheme), Kit.DEFAULT, fallingBack);
    }
    static ThemeController startup(boolean dark, TerminalSchemeChoice scheme, Predicate<GeneratedTheme> installer,
                                   java.util.function.Consumer<InstallationFailure> fallingBack) {
        return startup(dark, ThemeSelection.slateCobalt(scheme), installer, fallingBack);
    }
    static ThemeController startup(boolean dark, ThemeSelection selection, Predicate<GeneratedTheme> installer,
                                   java.util.function.Consumer<InstallationFailure> fallingBack) {
        try { return new ThemeController(new Shown(dark, selection), installer); }
        catch (InstallationFailure failure) {
            fallingBack.accept(failure);
            var cobalt = JasperTheme.bundled();
            return new ThemeController(new Shown(true, new ThemeSelection(selection.library(), cobalt, cobalt, selection.scheme())), installer);
        }
    }

    /** Slate Cobalt in {@code dark} with {@code scheme}, under the default kit. */
    public ThemeController(boolean dark, TerminalSchemeChoice scheme) { this(dark, scheme, ThemeController::installDefault); }
    ThemeController(boolean dark, TerminalSchemeChoice scheme, Predicate<GeneratedTheme> installer) {
        this(new Shown(dark, ThemeSelection.slateCobalt(Objects.requireNonNull(scheme))), installer);
    }
    /** {@code selection} in {@code dark}, under the default kit. */
    public ThemeController(boolean dark, ThemeSelection selection) {
        this(new Shown(dark, Objects.requireNonNull(selection)), ThemeController::installDefault);
    }
    ThemeController(boolean dark, ThemeSelection selection, Predicate<GeneratedTheme> installer) {
        this(new Shown(dark, Objects.requireNonNull(selection)), installer);
    }
    /** The installer of the controllers made without a kit. */
    private static boolean installDefault(GeneratedTheme theme) { return ThemeManager.install(theme, Kit.DEFAULT); }
```

In `jasper-app/src/main/java/dev/jasper/app/application/JasperApplication.java`, replace lines 251-252:

```java
        themes = ThemeController.startup(dark, selection,
            failure -> LOG.log(System.Logger.Level.WARNING, "Starting in Slate Cobalt Dark: " + failure.getMessage(), failure));
```

with:

```java
        themes = ThemeController.startup(dark, selection, dev.jasper.ui.kit.Kit.DEFAULT,
            failure -> LOG.log(System.Logger.Level.WARNING, "Starting in Slate Cobalt Dark: " + failure.getMessage(), failure));
```

In `jasper-app/src/main/java/dev/jasper/app/appearance/package-info.java`, replace lines 3-4:

```java
 * <p>Allowed outgoing Jasper dependencies: dev.jasper.app.config, dev.jasper.app.lifecycle, dev.jasper.terminal.config, dev.jasper.ui.theme,
 * dev.jasper.ui.swing, dev.jasper.ui.metrics, dev.jasper.ui.icons.
```

with:

```java
 * <p>Allowed outgoing Jasper dependencies: dev.jasper.app.config, dev.jasper.app.lifecycle, dev.jasper.terminal.config, dev.jasper.ui.theme,
 * dev.jasper.ui.swing, dev.jasper.ui.metrics, dev.jasper.ui.icons, dev.jasper.ui.kit.
```

- [ ] **Step 4: Name the kit at the test helpers' installs**

In `jasper-app/src/test/java/dev/jasper/app/appearance/ThemeTestSupport.java`, replace line 47 (46 after Task 3 removed
an import):

```java
    public static boolean install(GeneratedTheme theme) { return ThemeManager.install(theme); }
```

with:

```java
    public static boolean install(GeneratedTheme theme) { return ThemeManager.install(theme, dev.jasper.ui.kit.Kit.DEFAULT); }
```

and line 73 (72 after Task 3):

```java
    public static boolean install(boolean dark) { return ThemeManager.install(generated(dark)); }
```

with:

```java
    public static boolean install(boolean dark) { return ThemeManager.install(generated(dark), dev.jasper.ui.kit.Kit.DEFAULT); }
```

- [ ] **Step 5: Name the kit at every other install call**

Run this script first; it adds `, dev.jasper.ui.kit.Kit.DEFAULT` to every one-argument `FlatLafInstaller.install(…)`
call in the listed files (it leaves two-argument calls, such as Step 1's new test, alone) and stops unless it converted
exactly 27:

```bash
python3 -I - <<'PY'
import pathlib
FILES = """jasper-app/src/test/java/dev/jasper/app/appearance/ShippedControlReadabilityTest.java
jasper-app/src/test/java/dev/jasper/app/platform/TitleBarZoneTest.java
jasper-app/src/test/java/dev/jasper/app/plugins/HostedKitTest.java
jasper-app/src/test/java/dev/jasper/app/workspace/DesktopTestSupport.java
jasper-app/src/test/java/dev/jasper/app/workspace/WorkspaceRolesTest.java
jasper-ui/src/test/java/dev/jasper/ui/gallery/GalleryCoverageTest.java
jasper-ui/src/test/java/dev/jasper/ui/gallery/KitGallery.java
jasper-ui/src/test/java/dev/jasper/ui/swing/AppearanceTransactionTest.java
jasper-ui/src/test/java/dev/jasper/ui/swing/KitCalibrationTest.java
jasper-ui/src/test/java/dev/jasper/ui/swing/KitKeyBindingAuditTest.java
jasper-ui/src/test/java/dev/jasper/ui/swing/NoticeKitTest.java
jasper-ui/src/test/java/dev/jasper/ui/swing/PickerMenusTest.java
jasper-ui/src/test/java/dev/jasper/ui/swing/SearchChoiceRefreshTest.java
jasper-ui/src/test/java/dev/jasper/ui/swing/SlateCobaltInstallTest.java""".split()
CALL = "FlatLafInstaller.install("
total = 0
for name in FILES:
    path = pathlib.Path(name)
    text = path.read_text(encoding="utf-8")
    out, at = [], 0
    while (start := text.find(CALL, at)) >= 0:
        depth, i, two = 1, start + len(CALL), False
        while depth:
            c = text[i]
            if c == "(": depth += 1
            elif c == ")": depth -= 1
            elif c == "," and depth == 1: two = True
            i += 1
        out.append(text[at:i - 1] + ("" if two else ", dev.jasper.ui.kit.Kit.DEFAULT") + ")")
        total += not two
        at = i
    out.append(text[at:])
    path.write_text("".join(out), encoding="utf-8")
if total != 27: raise SystemExit(f"expected 27 one-argument installs, converted {total}")
PY
```

Then convert the four method references by hand (after the script, so it cannot touch them):

`jasper-ui/src/test/java/dev/jasper/ui/swing/AppearanceTransactionTest.java` line 59:

```java
                }, FlatLafInstaller::install);
```

becomes:

```java
                }, restored -> FlatLafInstaller.install(restored, dev.jasper.ui.kit.Kit.DEFAULT));
```

and line 70:

```java
                new AppearanceTransaction(binding, FlatLafInstaller::install, FlatLafInstaller::install)
```

becomes:

```java
                new AppearanceTransaction(binding, next -> FlatLafInstaller.install(next, dev.jasper.ui.kit.Kit.DEFAULT),
                    previous -> FlatLafInstaller.install(previous, dev.jasper.ui.kit.Kit.DEFAULT))
```

`jasper-app/src/test/java/dev/jasper/app/workspace/WorkspaceRolesTest.java` line 65:

```java
            new dev.jasper.ui.swing.AppearanceTransaction(binding, dev.jasper.ui.swing.FlatLafInstaller::install, previous -> {})
```

becomes:

```java
            new dev.jasper.ui.swing.AppearanceTransaction(binding, next -> dev.jasper.ui.swing.FlatLafInstaller.install(next, dev.jasper.ui.kit.Kit.DEFAULT), previous -> {})
```

Check that no one-argument install is left:

```bash
git grep -nE 'FlatLafInstaller::install([^e]|$)|ThemeManager::install' -- '*.java'
python3 -I - <<'PY'
import pathlib, re
for root in ("jasper-ui/src", "jasper-app/src"):
    for path in pathlib.Path(root).rglob("*.java"):
        text = path.read_text(encoding="utf-8")
        for match in re.finditer(r"(?:FlatLafInstaller|ThemeManager)\.install\(", text):
            depth, i, two = 1, match.end(), False
            while depth:
                c = text[i]
                depth += {"(": 1, ")": -1}.get(c, 0); two |= c == "," and depth == 1; i += 1
            if not two: print(f"{path}:{text.count(chr(10), 0, match.start()) + 1}: one-argument install")
PY
```

Expected: both print nothing.

- [ ] **Step 6: Run the tests to verify they pass**

Run: `./gradlew :jasper-ui:test --tests 'dev.jasper.ui.swing.FlatLafInstallerKitTest' --tests 'dev.jasper.ui.swing.SlateCobaltInstallTest' --tests 'dev.jasper.ui.swing.AppearanceTransactionTest'`
Expected: PASS (`FlatLafInstallerKitTest` 4, `SlateCobaltInstallTest` 5).

Run: `./gradlew :jasper-app:test --tests 'dev.jasper.app.appearance.ThemeManagerTest' --tests 'dev.jasper.app.appearance.SlateCobaltControllerTest' --tests 'dev.jasper.app.application.JasperApplicationThemesTest' --tests 'dev.jasper.app.workspace.WorkspaceRolesTest'`
Expected: PASS (`ThemeManagerTest` 8).

- [ ] **Step 7: Run the affected suites**

Run: `./gradlew :jasper-ui:check verifyUiArchitecture :jasper-app:test :jasper-app:javadoc`
Expected: BUILD SUCCESSFUL (`PresentationOwnershipTest` still sees `SplitPane.dividerSize` 8 and
`TitlePane.centerTitle` true; `KitCalibrationTest` and `GalleryCoverageTest` pass unchanged in their assertions).

- [ ] **Step 8: Commit**

```bash
git add jasper-ui/src/main/java/dev/jasper/ui/swing/FlatLafInstaller.java jasper-app/src/main/java/dev/jasper/app/appearance \
  jasper-app/src/main/java/dev/jasper/app/application/JasperApplication.java jasper-ui/src/test/java jasper-app/src/test/java
git commit -F - <<'EOF'
Kit seam: install every appearance under a kit

FlatLafInstaller.install(theme, kit) takes FlatLaf's colour keys from
the kit's key set and its geometry from the kit's metrics, and publishes
the kit as Jasper.installedKit beside the theme, so a reinstall or a
rollback restores both; installedKit() reads it, the default kit before
any install. ThemeManager takes the split divider from the kit, and
ThemeController.startup carries the kit to every install. Start-up uses
Kit.DEFAULT.

🤖 Generated with Claude Code at The Home Depot

Co-Authored-By: Claude <noreply@anthropic.com>
EOF
```

### Task 7: Every context is made with a kit

`KitContext.of(kit, binding, route, icons, animations, shortcuts)` (spec 6.3's one factory) replaces both public
constructors; `metrics()` returns `kit.metrics()` and `typography()` is `Typography.of(kit.type(), kit.faces(), user)`.
The three app construction sites read the installed kit (a test installs another kit and checks each); the tests'
contexts name theirs.

**Files:**
- Modify: `jasper-ui/src/main/java/dev/jasper/ui/swing/KitContext.java` (whole file), `jasper-ui/src/main/java/dev/jasper/ui/swing/SwingTypography.java:3-6,23-25` (Task 2's version), `jasper-ui/src/main/java/dev/jasper/ui/swing/NativeControlStyles.java:40`
- Modify: `jasper-app/src/main/java/dev/jasper/app/appearance/AppearanceBindings.java` (whole file), `jasper-app/src/main/java/dev/jasper/app/plugins/HostedKit.java:6,57`, `jasper-app/src/main/java/dev/jasper/app/platform/PresentationIcons.java:24`
- Modify: `docs/ui-architecture.md:46-47` and `jasper-ui/src/test/java/dev/jasper/ui/gallery/UiExamplesTest.java:6,32-33` (the compiled example, together)
- Test: `jasper-ui/src/test/java/dev/jasper/ui/swing/KitContextTest.java` (new); `jasper-app/src/test/java/dev/jasper/app/plugins/HostedKitTest.java` (one new test before line 56, and line 40)
- Modify test support: `jasper-ui/src/test/java/dev/jasper/ui/testsupport/KitFixtures.java` (`context(GeneratedTheme)` and `context()`)
- Modify tests (Step 5's script, 26 constructions in 16 files): `ShippedControlReadabilityTest`, `Task9cChooserEvidenceTest`, `NotificationCardTest`, `HostedKitTest`, `NativeShellsChromeTest`, `SheetOwnershipTest` (4), `WorkspaceRolesTest`, `GalleryCoverageTest` (2), `KitGallery`, `EditorKitTest`, `KitCalibrationTest` (5), `KitKeyBindingAuditTest`, `MenusTest`, `ModalOverlayTest`, `RemoteKitTest` (3), `SwitchesTest`

**Interfaces:**
- Consumes: `Kit` (Task 5); `FlatLafInstaller.installedKit()` (Task 6); `SwingTypography.user()` (Task 2);
  `KitFixtures.kit(DesignMetrics)` (Task 5).
- Produces: `public static KitContext KitContext.of(Kit kit, ThemeBinding binding, ThemeRoute route, IconRegistry icons, BooleanSupplier animationsEnabled, Function<String, String> shortcuts)`;
  `public Kit KitContext.kit()`; no public constructor; package-private `static Typography SwingTypography.typography(Kit kit)`
  (replaces `typography()`); `KitFixtures.context(GeneratedTheme theme, Kit kit)`.

- [ ] **Step 1: Write the failing tests**

Create `jasper-ui/src/test/java/dev/jasper/ui/swing/KitContextTest.java`:

```java
package dev.jasper.ui.swing;

import static org.assertj.core.api.Assertions.*;

import com.formdev.flatlaf.util.UIScale;
import dev.jasper.ui.kit.Kit;
import dev.jasper.ui.metrics.DesignMetrics;
import dev.jasper.ui.metrics.FaceSet;
import dev.jasper.ui.metrics.TypeScale;
import dev.jasper.ui.metrics.Typography;
import dev.jasper.ui.model.ControlState;
import dev.jasper.ui.model.Nodes;
import dev.jasper.ui.model.ViewSpec;
import dev.jasper.ui.testsupport.Edt;
import dev.jasper.ui.testsupport.KitFixtures;
import dev.jasper.ui.theme.UiKeySet;
import java.awt.Font;
import java.util.List;
import java.util.Optional;
import javax.swing.UIManager;
import org.junit.jupiter.api.Test;

/** Enterprise presentation spec 6.3: every context is made with a kit, and what it measures or draws follows that kit. */
class KitContextTest {
    @Test void everyContextIsMadeWithAKitThatAViewsCopyKeeps() {
        Edt.run(() -> {
            assertThat(KitContext.class.getConstructors()).isEmpty();
            var kit = KitFixtures.kit(Kit.DEFAULT.metrics().with(DesignMetrics.Role.FIELD_HEIGHT, 40));
            var context = KitFixtures.context(KitFixtures.dark(), kit);
            try (var view = new KitView(context, new ViewSpec(field()), event -> {})) {
                assertThat(context.kit()).isSameAs(kit);
                assertThat(context.metrics()).isSameAs(kit.metrics());
                assertThat(view.context()).as("the view's private copy").isNotSameAs(context);
                assertThat(view.context().kit()).isSameAs(kit);
            } finally { release(context); }
        });
    }

    /** The seam: one altered metric moves the context's metric, a measured height and a mounted control. */
    @Test void aKitWithOneAlteredMetricMovesWhatReadsIt() {
        Edt.run(() -> {
            var standard = KitFixtures.context();
            var tall = KitFixtures.context(KitFixtures.dark(), KitFixtures.kit(Kit.DEFAULT.metrics().with(DesignMetrics.Role.FIELD_HEIGHT, 40)));
            try (var a = new KitView(standard, new ViewSpec(field()), event -> {});
                 var b = new KitView(tall, new ViewSpec(field()), event -> {})) {
                assertThat(ThemePainting.metric(standard, DesignMetrics.Role.FIELD_HEIGHT)).isEqualTo(UIScale.scale(24));
                assertThat(ThemePainting.metric(tall, DesignMetrics.Role.FIELD_HEIGHT)).isEqualTo(UIScale.scale(40));
                assertThat(ThemePainting.height(tall, DesignMetrics.Role.FIELD_HEIGHT, Typography.Role.BODY)).isGreaterThanOrEqualTo(UIScale.scale(40));
                assertThat(b.getPreferredSize().height).as("a mounted field grows with its kit").isGreaterThan(a.getPreferredSize().height);
            } finally { release(standard); release(tall); }
        });
    }

    /** Review Focus 4: the user's font settings reach a kit's typography exactly as they reached the former global one. */
    @Test void typographyFollowsTheKitAndTheUserFontModes() {
        Edt.run(() -> {
            // Only the developer values, as AppearanceTransactionTest does: UIManager.get would fall through to the look and
            // feel's fonts and pin them as developer defaults for the rest of the test JVM.
            var keys = List.of("defaultFont", "Label.font", "Jasper.uiFontFamilyOverride");
            var saved = new java.util.HashMap<Object, Object>();
            UIManager.getDefaults().forEach((key, value) -> { if (keys.contains(key)) saved.put(key, value); });
            var context = KitFixtures.context();
            var large = KitFixtures.context(KitFixtures.dark(), new Kit("large-body", DesignMetrics.DEFAULT, TypeScale.DEFAULT.withBody(20),
                FaceSet.PLATFORM, UiKeySet.DEFAULT));
            try {
                UIManager.put("defaultFont", null); UIManager.put("Label.font", null); UIManager.put("Jasper.uiFontFamilyOverride", null);
                same(context, Typography.UserFont.NONE);
                assertThat(large.typography().font(Typography.Role.BODY).getSize2D()).as("no user size: the kit's body").isEqualTo(20f);
                assertThat(large.typography().font(Typography.Role.LABEL).getSize2D()).isEqualTo(12.5f * 20f / 13f);
                // Size only: ThemeController writes the flag false for the "system" family.
                UIManager.put("defaultFont", new Font("Dialog", Font.PLAIN, 18)); UIManager.put("Jasper.uiFontFamilyOverride", false);
                same(context, new Typography.UserFont(Optional.empty(), Optional.of(18f)));
                assertThat(large.typography().font(Typography.Role.BODY).getSize2D()).as("a user size wins over the kit's body").isEqualTo(18f);
                // A named family; Label.font's size wins over defaultFont's.
                var serif = new Font("Serif", Font.PLAIN, 20);
                UIManager.put("defaultFont", serif); UIManager.put("Label.font", serif.deriveFont(19.5f));
                UIManager.put("Jasper.uiFontFamilyOverride", true);
                same(context, new Typography.UserFont(Optional.of(serif.getFamily()), Optional.of(19.5f)));
                // The calibration fixtures' mode: the platform family named without the flag is an explicit family.
                var platform = new Font(KitFixtures.platformFamily(), Font.PLAIN, 13);
                UIManager.put("Label.font", null); UIManager.put("Jasper.uiFontFamilyOverride", null); UIManager.put("defaultFont", platform);
                same(context, new Typography.UserFont(Optional.of(platform.getFamily()), Optional.of(13f)));
            } finally {
                for (var key : keys) UIManager.put(key, saved.get(key)); // null removes a key that was absent
                release(context); release(large);
            }
        });
    }

    private static void same(KitContext context, Typography.UserFont user) {
        var expected = Typography.of(TypeScale.DEFAULT, FaceSet.PLATFORM, user);
        assertThat(context.typography().family()).isEqualTo(expected.family());
        for (var role : Typography.Role.values()) assertThat(context.typography().font(role)).as(role.name()).isEqualTo(expected.font(role));
    }

    private static Nodes.Field field() {
        return new Nodes.Field("name", ControlState.READY, Nodes.FieldKind.TEXT, "", "Name", "Name", "");
    }

    private static void release(KitContext context) { context.binding().close(); context.icons().close(); }
}
```

In `jasper-app/src/test/java/dev/jasper/app/plugins/HostedKitTest.java`, insert before line 56
(`@Test void failedPresentationReconciliationRetainsBothGenerationsUntilRecovery() throws Exception {…`):

```java
 /** Enterprise presentation plan 1: the app's three context sites take the kit installed when they make a context. */
 @Test void appContextsAreMadeOverTheInstalledKit() throws Exception {SwingUtilities.invokeAndWait(() -> {
  var base=dev.jasper.ui.kit.Kit.DEFAULT;
  var tall=new dev.jasper.ui.kit.Kit("tall-buttons",base.metrics().with(dev.jasper.ui.metrics.DesignMetrics.Role.BUTTON_HEIGHT,40),base.type(),base.faces(),base.uiKeys());
  var pair=ThemeTestSupport.pair(true);
  var spec=new ViewSpec(new Nodes.Button("go",ControlState.READY,"Go","",Nodes.ButtonKind.SECONDARY,"go",""));
  var original=UIManager.getLookAndFeel();
  var kit=new HostedKit("owner",() -> true,() -> pair,null,new Containment(java.awt.EventQueue::isDispatchThread));
  try(var bindings=new dev.jasper.app.appearance.AppearanceBindings(pair,e -> {throw new AssertionError(e);},() -> false);var receiver=new dev.jasper.app.platform.PresentationIcons()) {
   dev.jasper.ui.swing.FlatLafInstaller.install(pair.chrome(),base);
   var source=bindings.context(dev.jasper.ui.theme.ThemeRoute.WORKSPACE);
   var view=kit.create(spec,e -> {});
   dev.jasper.ui.swing.FlatLafInstaller.install(pair.chrome(),tall);
   assertThat(source.kit()).as("read when the context was made").isSameAs(base);
   assertThat(bindings.context(dev.jasper.ui.theme.ThemeRoute.CHROME).kit()).isSameAs(tall);
   assertThat(receiver.context(source).kit()).as("the installed kit, not the source's").isSameAs(tall);
   var tallView=kit.create(spec,e -> {});
   assertThat(tallView.component().getPreferredSize().height).as("a hosted view measures with the kit installed when it was made")
    .isGreaterThan(view.component().getPreferredSize().height);
  } finally {
   kit.close();
   try{UIManager.setLookAndFeel(original);}catch(UnsupportedLookAndFeelException failure){throw new AssertionError(failure);}
  }
 });}
```

(`BUTTON_HEIGHT` alone changes, so the rail icon size that `AppIcons` and `PresentationIcon` keep is the default kit's
throughout.) Then, at the end of line 40
(`assertThat(context.metrics()).isSameAs(source.metrics());assertThat(context.animationsEnabled()).isSameAs(source.animationsEnabled());`)
append:

```java
assertThat(context.kit()).isSameAs(dev.jasper.ui.swing.FlatLafInstaller.installedKit());
```

- [ ] **Step 2: Run the tests to verify they fail**

Run: `./gradlew :jasper-ui:test --tests 'dev.jasper.ui.swing.KitContextTest'`
Expected: FAIL in `:jasper-ui:compileTestJava` with `error: cannot find symbol` for `method kit()` and
`method context(GeneratedTheme,Kit)`.

Run: `./gradlew :jasper-app:compileTestJava`
Expected: FAIL in `HostedKitTest` with `error: cannot find symbol` for `method kit()`.

- [ ] **Step 3: Make contexts with a kit**

Replace the whole of `jasper-ui/src/main/java/dev/jasper/ui/swing/KitContext.java` with:

```java
package dev.jasper.ui.swing;

import dev.jasper.ui.icons.IconRegistry;
import dev.jasper.ui.kit.Kit;
import dev.jasper.ui.metrics.*;
import dev.jasper.ui.theme.*;
import java.util.Objects;
import java.util.function.BooleanSupplier;
import java.util.function.Function;

/**
 * Shared rendering values. Host owns binding/registry; each view owns its motion registrations. The kit, which every
 * metric, type and face read follows, is fixed for the context's life.
 */
public final class KitContext {
    private final Kit kit;
    private final ThemeBinding binding;
    /** Changed only through the one view that owns this copy (KitView.route), on the EDT. */
    private volatile ThemeRoute route;
    private final IconRegistry icons;
    private final BooleanSupplier animationsEnabled;
    private final Function<String, String> shortcuts;

    private KitContext(Kit kit, ThemeBinding binding, ThemeRoute route, IconRegistry icons, BooleanSupplier animationsEnabled,
                       Function<String, String> shortcuts) {
        this.kit = Objects.requireNonNull(kit);
        this.binding = Objects.requireNonNull(binding); this.route = Objects.requireNonNull(route);
        this.icons = Objects.requireNonNull(icons);
        this.animationsEnabled = Objects.requireNonNull(animationsEnabled); this.shortcuts = Objects.requireNonNull(shortcuts);
    }
    /** A view's private copy, so re-routing one view never re-routes another sharing the caller's context. */
    KitContext(KitContext source) {
        this(source.kit, source.binding, source.route, source.icons, source.animationsEnabled, source.shortcuts);
    }
    /**
     * A context over {@code kit}; {@code shortcuts} maps an action id to its current binding as menus show it ("" when
     * unbound).
     */
    public static KitContext of(Kit kit, ThemeBinding binding, ThemeRoute route, IconRegistry icons, BooleanSupplier animationsEnabled,
                                Function<String, String> shortcuts) {
        return new KitContext(kit, binding, route, icons, animationsEnabled, shortcuts);
    }
    /** The kit this context renders with. */
    public Kit kit() { return kit; }
    /** The action-id-to-binding text menus use. */
    public Function<String, String> shortcuts() { return shortcuts; }
    /** The current binding of {@code actionId} as a menu shows it; "" for none or for an empty id. */
    public String shortcut(String actionId) { return actionId.isEmpty() ? "" : Objects.requireNonNullElse(shortcuts.apply(actionId), ""); }
    void route(ThemeRoute value) { route = Objects.requireNonNull(value); }
    public ThemeBinding binding() { return binding; }
    public ThemeRoute route() { return route; }
    /** The kit's metrics. */
    public DesignMetrics metrics() { return kit.metrics(); }
    public IconRegistry icons() { return icons; }
    public BooleanSupplier animationsEnabled() { return animationsEnabled; }
    public GeneratedTheme theme() { return route.select(binding.current()); }
    /**
     * The kit's type scale and faces with the user's font settings, read live so they survive theme installs. FlatLaf owns
     * scaling; role fonts remain unscaled logical values.
     */
    public Typography typography() {
        return SwingTypography.typography(kit);
    }
    public MotionPolicy motionPolicy() { return new MotionPolicy(animationsEnabled); }
}
```

In `jasper-ui/src/main/java/dev/jasper/ui/swing/SwingTypography.java` (Task 2's version), replace the imports

```java
import dev.jasper.ui.metrics.FaceSet;
import dev.jasper.ui.metrics.TypeScale;
import dev.jasper.ui.metrics.Typography;
```

with:

```java
import dev.jasper.ui.kit.Kit;
import dev.jasper.ui.metrics.Typography;
```

and replace

```java
    static Typography typography() {
        return Typography.of(TypeScale.DEFAULT, FaceSet.PLATFORM, user());
    }
```

with:

```java
    static Typography typography(Kit kit) {
        return Typography.of(kit.type(), kit.faces(), user());
    }
```

In `jasper-ui/src/main/java/dev/jasper/ui/swing/NativeControlStyles.java`, replace line 40:

```java
        return SwingTypography.effective(SwingTypography.typography().font(role));
```

with (Task 9 replaces the class; until then its context-free fonts are the installed kit's):

```java
        return SwingTypography.effective(SwingTypography.typography(FlatLafInstaller.installedKit()).font(role));
```

- [ ] **Step 4: Build the app's contexts over the installed kit**

Replace the whole of `jasper-app/src/main/java/dev/jasper/app/appearance/AppearanceBindings.java` with:

```java
package dev.jasper.app.appearance;

import dev.jasper.ui.theme.*;
import dev.jasper.ui.swing.FlatLafInstaller;
import dev.jasper.ui.swing.KitContext;
import dev.jasper.ui.icons.IconRegistry;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

/** Application-owned appearance and icons shared by app-native kit views. EDT confined. */
public final class AppearanceBindings implements AutoCloseable {
    private final ThemeBinding binding;
    private final IconRegistry icons = new IconRegistry();
    private final BooleanSupplier animations;
    private java.util.function.Function<String, String> shortcuts = action -> "";
    public AppearanceBindings(ThemePair initial, Consumer<Throwable> failures, BooleanSupplier animations) {
        binding = new ThemeBinding(initial, failures);
        this.animations = java.util.Objects.requireNonNull(animations);
    }
    public ThemeBinding binding() { return binding; }
    /** The keymap text app-native menus show beside an item's action; contexts made earlier follow it. */
    public void shortcuts(java.util.function.Function<String, String> labels) { shortcuts = java.util.Objects.requireNonNull(labels); }
    /** A context for {@code route} over the installed kit, read now: contexts are made after the start-up install. */
    public KitContext context(ThemeRoute route) {
        return KitContext.of(FlatLafInstaller.installedKit(), binding, route, icons, animations, action -> shortcuts.apply(action));
    }
    @Override public void close() { binding.close(); icons.close(); }
}
```

In `jasper-app/src/main/java/dev/jasper/app/plugins/HostedKit.java`, replace line 57 and then delete line 6
(`import dev.jasper.ui.metrics.DesignMetrics;`). Line 57:

```java
    @Override public HostedKitView create(ViewSpec spec,Consumer<UiEvent> events){initialize();validateIcons(spec);var view=new HostedKitView(this,new KitContext(binding,ThemeRoute.CHROME,DesignMetrics.DEFAULT,icons,animations,shortcuts),spec,events);views.add(view);return view;}
```

with (`FlatLafInstaller` resolves through the existing `import dev.jasper.ui.swing.*;`):

```java
    @Override public HostedKitView create(ViewSpec spec,Consumer<UiEvent> events){initialize();validateIcons(spec);var view=new HostedKitView(this,KitContext.of(FlatLafInstaller.installedKit(),binding,ThemeRoute.CHROME,icons,animations,shortcuts),spec,events);views.add(view);return view;}
```

In `jasper-app/src/main/java/dev/jasper/app/platform/PresentationIcons.java`, replace line 24:

```java
        return new KitContext(source.binding(), source.route(), source.metrics(), registry, source.animationsEnabled(), source.shortcuts());
```

with:

```java
        return KitContext.of(dev.jasper.ui.swing.FlatLafInstaller.installedKit(), source.binding(), source.route(), registry,
            source.animationsEnabled(), source.shortcuts());
```

- [ ] **Step 5: Name the kit at every test context**

In `jasper-ui/src/test/java/dev/jasper/ui/testsupport/KitFixtures.java`, replace the two context factories:

```java
    /** A context over {@code theme} for both routes; caller owns and closes the binding and icon registry; EDT. */
    public static KitContext context(GeneratedTheme theme) {
        return new KitContext(new ThemeBinding(new ThemePair(theme, theme), failure -> { throw new AssertionError(failure); }),
            ThemeRoute.WORKSPACE, DesignMetrics.DEFAULT, new IconRegistry(), () -> false);
    }

    /** Caller owns and closes the binding and icon registry; creation requires EDT. */
    public static KitContext context() {
        return new KitContext(
                new ThemeBinding(
                        new ThemePair(dark(), dark()),
                        failure -> {
                            throw new AssertionError(failure);
                        }),
                ThemeRoute.WORKSPACE,
                DesignMetrics.DEFAULT,
                new IconRegistry(),
                () -> false);
    }
```

with:

```java
    /** A context over {@code theme} and {@code kit} for both routes; caller owns and closes the binding and icon registry; EDT. */
    public static KitContext context(GeneratedTheme theme, Kit kit) {
        return KitContext.of(kit, new ThemeBinding(new ThemePair(theme, theme), failure -> { throw new AssertionError(failure); }),
            ThemeRoute.WORKSPACE, new IconRegistry(), () -> false, action -> "");
    }

    /** A context over {@code theme} for both routes; caller owns and closes the binding and icon registry; EDT. */
    public static KitContext context(GeneratedTheme theme) { return context(theme, Kit.DEFAULT); }

    /** Caller owns and closes the binding and icon registry; creation requires EDT. */
    public static KitContext context() { return context(dark()); }
```

(`dark()` is the stored `JasperTheme.bundled().generated(true)` record, so `context()` builds the same values as before.)

In `jasper-ui/src/test/java/dev/jasper/ui/gallery/UiExamplesTest.java`, replace line 6
(`import dev.jasper.ui.metrics.DesignMetrics;`) with `import dev.jasper.ui.kit.Kit;` and lines 32-33:

```java
                var context = new KitContext(binding, ThemeRoute.WORKSPACE,
                        DesignMetrics.DEFAULT, icons, () -> false);
```

with:

```java
                var context = KitContext.of(Kit.DEFAULT, binding, ThemeRoute.WORKSPACE,
                        icons, () -> false, action -> "");
```

and in `docs/ui-architecture.md` replace lines 46-47 the same way (`UiDocumentationTest` compares the two after
`stripIndent`):

```java
    var context = new KitContext(binding, ThemeRoute.WORKSPACE,
            DesignMetrics.DEFAULT, icons, () -> false);
```

with:

```java
    var context = KitContext.of(Kit.DEFAULT, binding, ThemeRoute.WORKSPACE,
            icons, () -> false, action -> "");
```

Then run this script over the remaining test constructions. For `new KitContext(binding, route, METRICS, icons,
animations[, shortcuts])` it writes `KitContext.of(KIT, binding, route, icons, animations, shortcuts)`, where `KIT` is
`dev.jasper.ui.kit.Kit.DEFAULT` for `DesignMetrics.DEFAULT` and `x.kit()` for a copied `x.metrics()`, and a missing
`shortcuts` becomes `action -> ""` (what the five-argument constructor supplied, `KitContext.java:19`). It stops on
anything else and unless it converted exactly 26:

```bash
python3 -I - <<'PY'
import re, pathlib
FILES = """jasper-app/src/test/java/dev/jasper/app/appearance/ShippedControlReadabilityTest.java
jasper-app/src/test/java/dev/jasper/app/files/Task9cChooserEvidenceTest.java
jasper-app/src/test/java/dev/jasper/app/notificationui/NotificationCardTest.java
jasper-app/src/test/java/dev/jasper/app/plugins/HostedKitTest.java
jasper-app/src/test/java/dev/jasper/app/windows/NativeShellsChromeTest.java
jasper-app/src/test/java/dev/jasper/app/windows/SheetOwnershipTest.java
jasper-app/src/test/java/dev/jasper/app/workspace/WorkspaceRolesTest.java
jasper-ui/src/test/java/dev/jasper/ui/gallery/GalleryCoverageTest.java
jasper-ui/src/test/java/dev/jasper/ui/gallery/KitGallery.java
jasper-ui/src/test/java/dev/jasper/ui/swing/EditorKitTest.java
jasper-ui/src/test/java/dev/jasper/ui/swing/KitCalibrationTest.java
jasper-ui/src/test/java/dev/jasper/ui/swing/KitKeyBindingAuditTest.java
jasper-ui/src/test/java/dev/jasper/ui/swing/MenusTest.java
jasper-ui/src/test/java/dev/jasper/ui/swing/ModalOverlayTest.java
jasper-ui/src/test/java/dev/jasper/ui/swing/RemoteKitTest.java
jasper-ui/src/test/java/dev/jasper/ui/swing/SwitchesTest.java""".split()
START = re.compile(r'new\s+((?:dev\.jasper\.ui\.swing\.)?)KitContext\s*\(')
DEFAULT = re.compile(r'^\s*(?:dev\.jasper\.ui\.metrics\.)?DesignMetrics\.DEFAULT\s*$')
COPY = re.compile(r'^\s*([\w.]+)\.metrics\(\)\s*$')
def arguments(text, open_at):
    depth, args, start, i = 0, [], open_at + 1, open_at
    while True:
        c = text[i]
        if c == '"':
            i += 1
            while text[i] != '"': i += 2 if text[i] == '\\' else 1
        elif c in '([{': depth += 1
        elif c in ')]}':
            depth -= 1
            if depth == 0: args.append(text[start:i]); return args, i
        elif c == ',' and depth == 1: args.append(text[start:i]); start = i + 1
        i += 1
total = 0
for name in FILES:
    path = pathlib.Path(name)
    text = path.read_text(encoding="utf-8")
    out, at = [], 0
    for match in START.finditer(text):
        args, close = arguments(text, match.end() - 1)
        if len(args) not in (5, 6): raise SystemExit(f"{name}: unexpected arity {len(args)}")
        if DEFAULT.match(args[2]): kit = "dev.jasper.ui.kit.Kit.DEFAULT"
        elif COPY.match(args[2]): kit = COPY.match(args[2]).group(1) + ".kit()"
        else: raise SystemExit(f"{name}: unexpected metrics argument {args[2]!r}")
        rest = [args[0], args[1], args[3], args[4]] + ([args[5]] if len(args) == 6 else [' action -> ""'])
        rest = [arg if arg[:1].isspace() else " " + arg for arg in rest]
        out.append(text[at:match.start()])
        out.append(match.group(1) + "KitContext.of(" + kit + "," + ",".join(rest) + ")")
        at = close + 1
        total += 1
    out.append(text[at:])
    path.write_text("".join(out), encoding="utf-8")
if total != 26: raise SystemExit(f"expected 26 constructions, converted {total}")
PY
git grep -nE 'new (dev\.jasper\.ui\.swing\.)?KitContext[[:space:]]*\(' -- '*.java' '*.md' ':!docs/superpowers'
```

Expected: the final `git grep` prints only `jasper-ui/src/main/java/dev/jasper/ui/swing/KitView.java:45` (the
package-private copy `new KitContext(Objects.requireNonNull(context))`). For example `KitCalibrationTest.java:99`
becomes `KitContext.of(base.kit(), base.binding(), base.route(), dev.jasper.ui.gallery.KitGallery.icons(), () -> false, action -> "")`,
`MenusTest.java:275` keeps its `id -> bindings.getOrDefault(id, "")`, and `NativeShellsChromeTest.java:38-39` joins into
one line.

- [ ] **Step 6: Run the tests to verify they pass**

Run: `./gradlew :jasper-ui:test --tests 'dev.jasper.ui.swing.KitContextTest' --tests 'dev.jasper.ui.gallery.UiDocumentationTest' --tests 'dev.jasper.ui.gallery.UiExamplesTest' --tests 'dev.jasper.ui.swing.NativeControlStylesTest'`
Expected: PASS (`KitContextTest` 3).

Run: `./gradlew :jasper-app:test --tests 'dev.jasper.app.plugins.HostedKitTest' --tests 'dev.jasper.app.appearance.UiTypographyTest'`
Expected: PASS.

- [ ] **Step 7: Run the affected suites**

Run: `./gradlew :jasper-ui:check verifyUiArchitecture :jasper-app:test`
Expected: BUILD SUCCESSFUL; `KitCalibrationTest`, `GalleryCoverageTest`, `RemoteKitTest`, `EditorKitTest` and the
app's kit tests pass with unchanged assertions.

- [ ] **Step 8: Commit**

```bash
git add jasper-ui/src/main/java/dev/jasper/ui/swing/KitContext.java jasper-ui/src/main/java/dev/jasper/ui/swing/SwingTypography.java \
  jasper-ui/src/main/java/dev/jasper/ui/swing/NativeControlStyles.java jasper-app/src/main/java/dev/jasper/app/appearance/AppearanceBindings.java \
  jasper-app/src/main/java/dev/jasper/app/plugins/HostedKit.java jasper-app/src/main/java/dev/jasper/app/platform/PresentationIcons.java \
  docs/ui-architecture.md jasper-ui/src/test/java jasper-app/src/test/java
git commit -F - <<'EOF'
Kit seam: every KitContext is made with a kit

KitContext.of(kit, ...) replaces both constructors; metrics() is the
kit's and typography() is Typography.of over the kit's scale and faces
with the user's font. The app's three construction sites read the
installed kit; every test context names its kit.

🤖 Generated with Claude Code at The Home Depot

Co-Authored-By: Claude <noreply@anthropic.com>
EOF
```

### Task 8: Icon sizes and tiles take the caller's metrics

`dev.jasper.ui.icons` cannot see `swing`, so `IconRegistry.size` and the tile and identity-header overloads take the
`DesignMetrics` the caller's context passes (spec 6.3); the registry keeps no catalog of its own.
`PresentationIcon`, an immutable app value with no context, reads the installed kit's rail size once.

**Files:**
- Modify: `jasper-ui/src/main/java/dev/jasper/ui/icons/IconRegistry.java:168-195,211,221-229`
- Modify (Step 5's script, 19 calls): `jasper-ui/src/main/java/dev/jasper/ui/swing/{Buttons,Choices,ItemLists,Labels,Menus,RichOptions,SearchChoices,SecretFields,Stacks,Tables,TextFields,Trees}.java`
- Modify: `jasper-app/src/main/java/dev/jasper/app/platform/PresentationIcon.java:17,25,29-30`
- Test: `jasper-ui/src/test/java/dev/jasper/ui/icons/IconRegistryTest.java` (one new test before line 179)
- Modify tests (Step 5's script, 6 context calls and 16 registry calls): `TextFieldsTest`, `InteractiveRowsTest`, `KitCalibrationTest`, `SettingsArtworkTest`, `BuiltInArtworkTest`, `IconRegistryTest`, `IdentityArtworkTest`; by hand `jasper-app/src/test/java/dev/jasper/app/plugins/HostedKitTest.java:21`

**Interfaces:**
- Consumes: `DesignMetrics.with` (Task 1); `KitContext.metrics()` (Task 7); `FlatLafInstaller.installedKit()` (Task 6).
- Produces: `public int IconRegistry.size(DesignMetrics metrics, Nodes.IconSize size)`;
  `tile(DesignMetrics metrics, String name, SemanticPalette palette, Nodes.Hue hue, int logicalSize, boolean selected)`,
  `tile(DesignMetrics, String, SemanticPalette, Nodes.Hue, int width, int height, boolean)`,
  `tile(DesignMetrics, String, SemanticPalette, Nodes.Hue, int width, int height, int glyphSize, boolean)`,
  `tile(DesignMetrics, String, GeneratedTheme, Nodes.Hue, int width, int height, int glyphSize, SemanticColor selectedInk)`,
  `identityHeader(DesignMetrics, String, GeneratedTheme, Nodes.Hue, int width, int height, int glyphSize)`. The metric-less
  forms are removed.

- [ ] **Step 1: Write the failing test**

In `jasper-ui/src/test/java/dev/jasper/ui/icons/IconRegistryTest.java`, insert before line 179
(`private static BufferedImage paint(Icon icon) {`):

```java
    /** Enterprise presentation plan 1: a tile's opacity and an icon's size are the passed catalog's, not a fixed one. */
    @Test void sizesAndTileOpacityFollowTheCatalogTheCallerPasses() {
        var colors = new EnumMap<SemanticColor, Color>(SemanticColor.class);
        for (var role : SemanticColor.values()) colors.put(role, KitFixtures.dark().semantic().color(role));
        colors.put(SemanticColor.HUE_BLUE_SURFACE, new Color(10, 20, 30, 128));
        var catalog = dev.jasper.ui.metrics.DesignMetrics.DEFAULT.with(dev.jasper.ui.metrics.DesignMetrics.Role.TILE_PERCENT_DARK, 50)
            .with(dev.jasper.ui.metrics.DesignMetrics.Role.ICON_CONTROL, 18);
        try (var icons = new IconRegistry()) {
            assertThat(new Color(paint(icons.tile(catalog, "add", new SemanticPalette(colors), Nodes.Hue.BLUE, 24, false)).getRGB(4, 4), true).getAlpha())
                .isEqualTo(64); // 128 * 50%, once
            assertThat(icons.size(catalog, Nodes.IconSize.CONTROL)).isEqualTo(18);
            assertThat(icons.size(dev.jasper.ui.metrics.DesignMetrics.DEFAULT, Nodes.IconSize.CONTROL)).isEqualTo(14);
        }
    }

```

(The registry is named `icons`, not `registry`, so Step 5's script leaves its calls alone.)

- [ ] **Step 2: Run the test to verify it fails**

Run: `./gradlew :jasper-ui:test --tests 'dev.jasper.ui.icons.IconRegistryTest'`
Expected: FAIL in `:jasper-ui:compileTestJava` with `error: method size in class IconRegistry cannot be applied to given types`.

- [ ] **Step 3: Pass the catalog through the registry**

In `jasper-ui/src/main/java/dev/jasper/ui/icons/IconRegistry.java`, replace lines 168-195 (line 196, the
`Invalid tile glyph size` check, stays as it is):

```java
    /** Category tile. Selected treatment is shared across list/menu renderers, applied to base alpha exactly once. */
    public Icon tile(String name, SemanticPalette palette, Nodes.Hue hue, int logicalSize, boolean selected) {
        return tile(name, palette, hue, logicalSize, logicalSize, selected);
    }
    /** Rectangular category tile; glyph remains optically centered and unstretched. */
    public Icon tile(String name, SemanticPalette palette, Nodes.Hue hue, int width, int height, boolean selected) {
        return tile(name, palette, hue, width, height, 0, selected);
    }
    /** Category tile with explicit glyph extent; zero retains the standard optical sizing policy. A selected tile is on the accent fill. */
    public Icon tile(String name, SemanticPalette palette, Nodes.Hue hue, int width, int height, int glyphSize, boolean selected) {
        return tile(name,palette,null,hue,width,height,glyphSize,selected?SemanticColor.TEXT_ONACCENT:null);
    }
    /**
     * Identity tiles select the actual receiving-theme sibling; legacy settings/group geometry is preserved. A selected
     * tile is drawn in {@code selectedInk}, the ink of the fill it lies on; null draws it unselected.
     */
    public Icon tile(String name, GeneratedTheme theme, Nodes.Hue hue, int width, int height, int glyphSize, SemanticColor selectedInk) {
        return tile(name,theme.semantic(),theme,hue,width,height,glyphSize,selectedInk);
    }
    /** Receiving identity header: normal authored ink with the shared inward semantic contour. */
    public Icon identityHeader(String name, GeneratedTheme theme, Nodes.Hue hue, int width, int height, int glyphSize) {
        return tile(name,theme.semantic(),theme,hue,width,height,glyphSize,null,theme.semantic().color(SemanticColor.BORDER_IDENTITY));
    }
    private Icon tile(String name, SemanticPalette palette, GeneratedTheme theme, Nodes.Hue hue, int width, int height, int glyphSize, SemanticColor selectedInk) {
        return tile(name,palette,theme,hue,width,height,glyphSize,selectedInk,null);
    }
    private Icon tile(String name, SemanticPalette palette, GeneratedTheme theme, Nodes.Hue hue, int width, int height, int glyphSize, SemanticColor selectedInk, Color outline) {
        boolean selected = selectedInk != null;
```

with:

```java
    /**
     * Category tile in {@code metrics}' tile opacity. Selected treatment is shared across list/menu renderers, applied to
     * base alpha exactly once.
     */
    public Icon tile(DesignMetrics metrics, String name, SemanticPalette palette, Nodes.Hue hue, int logicalSize, boolean selected) {
        return tile(metrics, name, palette, hue, logicalSize, logicalSize, selected);
    }
    /** Rectangular category tile; glyph remains optically centered and unstretched. */
    public Icon tile(DesignMetrics metrics, String name, SemanticPalette palette, Nodes.Hue hue, int width, int height, boolean selected) {
        return tile(metrics, name, palette, hue, width, height, 0, selected);
    }
    /** Category tile with explicit glyph extent; zero retains the standard optical sizing policy. A selected tile is on the accent fill. */
    public Icon tile(DesignMetrics metrics, String name, SemanticPalette palette, Nodes.Hue hue, int width, int height, int glyphSize, boolean selected) {
        return tile(metrics,name,palette,null,hue,width,height,glyphSize,selected?SemanticColor.TEXT_ONACCENT:null);
    }
    /**
     * Identity tiles select the actual receiving-theme sibling; legacy settings/group geometry is preserved. A selected
     * tile is drawn in {@code selectedInk}, the ink of the fill it lies on; null draws it unselected.
     */
    public Icon tile(DesignMetrics metrics, String name, GeneratedTheme theme, Nodes.Hue hue, int width, int height, int glyphSize, SemanticColor selectedInk) {
        return tile(metrics,name,theme.semantic(),theme,hue,width,height,glyphSize,selectedInk);
    }
    /** Receiving identity header: normal authored ink with the shared inward semantic contour. */
    public Icon identityHeader(DesignMetrics metrics, String name, GeneratedTheme theme, Nodes.Hue hue, int width, int height, int glyphSize) {
        return tile(metrics,name,theme.semantic(),theme,hue,width,height,glyphSize,null,theme.semantic().color(SemanticColor.BORDER_IDENTITY));
    }
    private Icon tile(DesignMetrics metrics, String name, SemanticPalette palette, GeneratedTheme theme, Nodes.Hue hue, int width, int height, int glyphSize, SemanticColor selectedInk) {
        return tile(metrics,name,palette,theme,hue,width,height,glyphSize,selectedInk,null);
    }
    private Icon tile(DesignMetrics metrics, String name, SemanticPalette palette, GeneratedTheme theme, Nodes.Hue hue, int width, int height, int glyphSize, SemanticColor selectedInk, Color outline) {
        Objects.requireNonNull(metrics, "metrics");
        boolean selected = selectedInk != null;
```

In line 211, replace `ColorMath.opacity(fill, DesignMetrics.DEFAULT.value(` with `ColorMath.opacity(fill, metrics.value(` (the
rest of the line is unchanged).

Replace lines 221-222 (the `switch` arms and closing lines 223-229 stay):

```java
    public int size(Nodes.IconSize size) {
        return DesignMetrics.DEFAULT.value(switch (size) {
```

with:

```java
    /** The logical extent of {@code size} in {@code metrics}. */
    public int size(DesignMetrics metrics, Nodes.IconSize size) {
        return metrics.value(switch (size) {
```

`IconRegistry.java` already imports `DesignMetrics` (line 5) and `Objects` (line 10); the icons package-info already
allows `dev.jasper.ui.metrics`.

- [ ] **Step 4: Read the installed kit's rail size in PresentationIcon**

In `jasper-app/src/main/java/dev/jasper/app/platform/PresentationIcon.java`, replace line 17:

```java
    private final AppIcons.NativePreparation nativeArtwork;
```

with:

```java
    /** The installed kit's rail glyph size in logical pixels, read once: the kit is fixed before any window. */
    private final int rail;
    private final AppIcons.NativePreparation nativeArtwork;
```

replace line 25:

```java
        nativeArtwork=pathData==null?AppIcons.prepare(name,artwork.size(Nodes.IconSize.RAIL)):null;
```

with:

```java
        rail = artwork.size(dev.jasper.ui.swing.FlatLafInstaller.installedKit().metrics(), Nodes.IconSize.RAIL);
        nativeArtwork=pathData==null?AppIcons.prepare(name,rail):null;
```

and in lines 29-30 replace both `UIScale.scale(artwork.size(Nodes.IconSize.RAIL))` with `UIScale.scale(rail)`.

In `jasper-app/src/test/java/dev/jasper/app/plugins/HostedKitTest.java` line 21, replace
`registry.size(dev.jasper.ui.model.Nodes.IconSize.RAIL)` with
`registry.size(dev.jasper.ui.kit.Kit.DEFAULT.metrics(), dev.jasper.ui.model.Nodes.IconSize.RAIL)`.

- [ ] **Step 5: Pass the caller's metrics at every other call**

Run this script. A context-held registry (`context().icons()` or `context.icons()`) gets the same context's metrics; the
icons tests' standalone `registry` gets the default catalog. It stops unless it converted exactly 25 and 16 calls:

```bash
python3 -I - <<'PY'
import re, pathlib
CONTEXT = re.compile(r'\b(context(?:\(\))?)(\s*\.icons\(\)\s*\.(?:size|tile|identityHeader)\()')
REGISTRY = re.compile(r'\bregistry\.tile\(')
CONTEXT_FILES = """jasper-ui/src/main/java/dev/jasper/ui/swing/Buttons.java
jasper-ui/src/main/java/dev/jasper/ui/swing/Choices.java
jasper-ui/src/main/java/dev/jasper/ui/swing/ItemLists.java
jasper-ui/src/main/java/dev/jasper/ui/swing/Labels.java
jasper-ui/src/main/java/dev/jasper/ui/swing/Menus.java
jasper-ui/src/main/java/dev/jasper/ui/swing/RichOptions.java
jasper-ui/src/main/java/dev/jasper/ui/swing/SearchChoices.java
jasper-ui/src/main/java/dev/jasper/ui/swing/SecretFields.java
jasper-ui/src/main/java/dev/jasper/ui/swing/Stacks.java
jasper-ui/src/main/java/dev/jasper/ui/swing/Tables.java
jasper-ui/src/main/java/dev/jasper/ui/swing/TextFields.java
jasper-ui/src/main/java/dev/jasper/ui/swing/Trees.java
jasper-ui/src/test/java/dev/jasper/ui/swing/TextFieldsTest.java
jasper-ui/src/test/java/dev/jasper/ui/swing/InteractiveRowsTest.java
jasper-ui/src/test/java/dev/jasper/ui/swing/KitCalibrationTest.java
jasper-app/src/test/java/dev/jasper/app/settingsui/SettingsArtworkTest.java""".split()
REGISTRY_FILES = """jasper-ui/src/test/java/dev/jasper/ui/icons/BuiltInArtworkTest.java
jasper-ui/src/test/java/dev/jasper/ui/icons/IconRegistryTest.java
jasper-ui/src/test/java/dev/jasper/ui/icons/IdentityArtworkTest.java""".split()
contexts = registries = 0
for name in CONTEXT_FILES:
    path = pathlib.Path(name)
    text, count = CONTEXT.subn(lambda m: m.group(1) + m.group(2) + m.group(1) + ".metrics(), ", path.read_text(encoding="utf-8"))
    path.write_text(text, encoding="utf-8"); contexts += count
for name in REGISTRY_FILES:
    path = pathlib.Path(name)
    text, count = REGISTRY.subn("registry.tile(dev.jasper.ui.metrics.DesignMetrics.DEFAULT, ", path.read_text(encoding="utf-8"))
    path.write_text(text, encoding="utf-8"); registries += count
if (contexts, registries) != (25, 16): raise SystemExit(f"expected 25 context and 16 registry calls, converted {contexts} and {registries}")
PY
```

The 25 context calls are the 19 main ones (`Buttons.java:81`, `Choices.java:123,126,242`, `ItemLists.java:315`,
`Labels.java:167,177,178`, `Menus.java:223-225,491-492`, `RichOptions.java:26`, `SearchChoices.java:340`,
`SecretFields.java:337,421,456`, `Stacks.java:135`, `Tables.java:167`, `TextFields.java:288`, `Trees.java:336`) and
`TextFieldsTest.java:21,24`, `InteractiveRowsTest.java:649`, `KitCalibrationTest.java:213` and
`SettingsArtworkTest.java:28` (two). The 16 registry calls are `BuiltInArtworkTest.java:45`,
`IconRegistryTest.java:36,51,62,117,127,161,173` and `IdentityArtworkTest.java:24,26,27,31,56,57,61` (two at 61).

Check that every call passes a catalog first:

```bash
python3 -I - <<'PY'
import re, pathlib
CALL = re.compile(r'(?:\.icons\(\)|\bregistry|\bartwork)\s*\.(size|tile|identityHeader)\(')
for root in ("jasper-ui/src", "jasper-app/src"):
    for path in sorted(pathlib.Path(root).rglob("*.java")):
        text = path.read_text(encoding="utf-8")
        for match in CALL.finditer(text):
            rest = text[match.end():match.end() + 80]
            if match.group(1) == "size" and re.match(r'\s*\)', rest): continue  # List.size()
            if not re.match(r'\s*(?:context(?:\(\))?\.metrics\(\)|dev\.jasper\.ui\.metrics\.DesignMetrics\.DEFAULT|dev\.jasper\.ui\.kit\.Kit\.DEFAULT\.metrics\(\)|dev\.jasper\.ui\.swing\.FlatLafInstaller\.installedKit\(\)\.metrics\(\))', rest):
                print(f"{path}:{text.count(chr(10), 0, match.start()) + 1}: {rest.splitlines()[0]}")
PY
```

Expected: prints nothing.

- [ ] **Step 6: Run the tests to verify they pass**

Run: `./gradlew :jasper-ui:test --tests 'dev.jasper.ui.icons.*' --tests 'dev.jasper.ui.swing.TextFieldsTest' --tests 'dev.jasper.ui.swing.InteractiveRowsTest'`
Expected: PASS (`IconRegistryTest` gains one test; the existing `128 * 17%` = 22 case still passes).

- [ ] **Step 7: Run the affected suites**

Run: `./gradlew :jasper-ui:check verifyUiArchitecture :jasper-app:test`
Expected: BUILD SUCCESSFUL (`HostedKitTest`, `SettingsArtworkTest`, `KitCalibrationTest` and `GalleryCoverageTest`
unchanged in their assertions).

- [ ] **Step 8: Commit**

```bash
git add jasper-ui/src/main/java/dev/jasper/ui/icons/IconRegistry.java jasper-ui/src/main/java/dev/jasper/ui/swing \
  jasper-app/src/main/java/dev/jasper/app/platform/PresentationIcon.java jasper-ui/src/test/java jasper-app/src/test/java
git commit -F - <<'EOF'
Kit seam: icon sizes and tiles take the caller's metrics

IconRegistry.size and the tile and identity-header overloads take the
DesignMetrics of the context that asks, since the icons package cannot
see the Swing kit; PresentationIcon reads the installed kit's rail size.

🤖 Generated with Claude Code at The Home Depot

Co-Authored-By: Claude <noreply@anthropic.com>
EOF
```

### Task 9: ChromePainter and NativeStyles

`NativeChromePainting` and `NativeControlStyles` become the single classes `ChromePainter(Kit)` and `NativeStyles(Kit)`
(spec 6.6; no interfaces). Every former static is an instance method with the same name, parameters and result; the only
statics left are `installed()`, which returns the installed kit's instance (cached by kit identity), and its cache. App
chrome calls `ChromePainter.installed()` / `NativeStyles.installed()`; jasper-ui's own measuring uses a package-private
`ThemePainting.measureText`; the tests construct their painters.

**Files:**
- Rename: `jasper-ui/src/main/java/dev/jasper/ui/swing/NativeChromePainting.java` to `ChromePainter.java` and `NativeControlStyles.java` to `NativeStyles.java` (both rewritten whole)
- Rename: `jasper-ui/src/test/java/dev/jasper/ui/swing/NativeChromePaintingTest.java` to `ChromePainterTest.java` and `NativeControlStylesTest.java` to `NativeStylesTest.java`
- Modify: `jasper-ui/src/main/java/dev/jasper/ui/swing/ThemePainting.java:17` (two members after it)
- Modify (Step 6's script, 153 references on 140 lines in 36 files): `jasper-ui/src/main/java/dev/jasper/ui/swing/{Composition,RichOptions,Tables}.java` and, under `jasper-app/src/main/java/dev/jasper/app/`, `about/AboutView.java`, `about/LicensesView.java`, `files/FileChooserPanel.java`, `notificationui/{ErrorsView,HistoryPanel,NotificationCard,NotificationStyle,TextBlock}.java`, `platform/{MacTitleBar,ThemeZone}.java`, `pluginmanager/{PluginDetails,PluginManagerPanel}.java`, `workspace/{BadgedIcon,CollapsingMenuBar,DockDropOverlay,FindBar,MenuBarItems,PathBreadcrumbs,SplitDividerBorder,StatusProgressView,TerminalPane,TitleSearchField,ToolStripe,ToolWindowHeader,ToolWindowMenu,WindowChrome,WindowCommandPalette,WindowContent,WindowContributions,WindowHeader,WindowStatusBar,WindowTabs,WindowToolbar}.java`
- Modify: `docs/design/theme-engine/migration.csv:766-767`
- Test: `ChromePainterTest` and `NativeStylesTest` (new tests); Step 2's script over `ChromeRolesTest`, `TablesTest`, `ShippedControlReadabilityTest`, `UiTypographyTest`, `CommandPaletteTest`, `WorkspaceRolesTest`, `WorkspaceMetricsTest`

**Interfaces:**
- Consumes: `Kit`, `KitFixtures.kit` (Task 5); `FlatLafInstaller.installedKit()`, `install(theme, kit)` (Task 6);
  `SwingTypography.typography(Kit)` (Task 7).
- Produces: `public final class ChromePainter` with `public ChromePainter(Kit kit)`, `public static ChromePainter installed()`,
  `public Kit kit()` and the instance methods `prepareText`, `measureText`, `action`, `search`, `hoverOverlay`,
  `actionWidth`, `rule`, `bottomHairline`, `titleBarEdges`, `tabSurface`, `tabStrip`, `statusDot`, `badge`,
  `connectedDot`, `splitDivider`, `dropGuide`, `dropGuides`, `popupShadow`, `clip` (former signatures);
  `public final class NativeStyles` with `public NativeStyles(Kit kit)`, `public static NativeStyles installed()`,
  `public Kit kit()` and the instance methods `splitPaneUI`, `font(Typography.Role)`, `font(KitContext, Typography.Role)`,
  `observeTypography`, `metric`, `unframed`, `padding`, `button` (two), `framedButton`, `label`, `textArea`, `menu`,
  `menuTree`, `borderlessField`, `refresh` (former signatures); package-private
  `static int ThemePainting.measureText(Font font, String text)`.

- [ ] **Step 1: Rename the two test classes**

```bash
git mv jasper-ui/src/test/java/dev/jasper/ui/swing/NativeChromePaintingTest.java jasper-ui/src/test/java/dev/jasper/ui/swing/ChromePainterTest.java
git mv jasper-ui/src/test/java/dev/jasper/ui/swing/NativeControlStylesTest.java jasper-ui/src/test/java/dev/jasper/ui/swing/NativeStylesTest.java
```

- [ ] **Step 2: Point the tests at instances**

Run this script; every replacement must find its text. The ported tests keep their expected values, so they prove that
`ChromePainter(Kit.DEFAULT)` paints and styles exactly as the static classes did:

```bash
python3 -I - <<'PY'
import pathlib
UI = pathlib.Path("jasper-ui/src/test/java/dev/jasper/ui/swing")
APP = pathlib.Path("jasper-app/src/test/java/dev/jasper/app")
PAINTER = "    private static final ChromePainter PAINTER = new ChromePainter(Kit.DEFAULT);\n"
STYLES = "    private static final NativeStyles STYLES = new NativeStyles(Kit.DEFAULT);\n"
def edit(path, *pairs, fields=None, cls=None):
    text = path.read_text(encoding="utf-8")
    for old, new in pairs:
        if old not in text: raise SystemExit(f"{path}: missing {old!r}")
        text = text.replace(old, new)
    if fields:
        anchor = f"class {cls} {{\n"
        if anchor not in text: raise SystemExit(f"{path}: missing {anchor!r}")
        text = text.replace(anchor, anchor + fields, 1)
        text = text.replace("package dev.jasper.ui.swing;\n", "package dev.jasper.ui.swing;\n\nimport dev.jasper.ui.kit.Kit;", 1)
    path.write_text(text, encoding="utf-8")
edit(UI / "ChromePainterTest.java", ("class NativeChromePaintingTest {", "class ChromePainterTest {"),
     ("NativeChromePainting.", "PAINTER."), ("NativeControlStyles.", "STYLES."), fields=PAINTER + STYLES, cls="ChromePainterTest")
edit(UI / "NativeStylesTest.java", ("class NativeControlStylesTest {", "class NativeStylesTest {"),
     ("NativeControlStyles.", "STYLES."), ("NativeChromePainting.", "PAINTER."), fields=STYLES + PAINTER, cls="NativeStylesTest")
edit(UI / "ChromeRolesTest.java", ("NativeChromePainting.", "PAINTER."), fields=PAINTER, cls="ChromeRolesTest")
edit(UI / "TablesTest.java", ("NativeChromePainting.measureText(", "ThemePainting.measureText("))
edit(APP / "appearance/ShippedControlReadabilityTest.java",
     ("NativeChromePainting.search(", "new ChromePainter(dev.jasper.ui.kit.Kit.DEFAULT).search("))
for name in ("appearance/UiTypographyTest.java", "palette/CommandPaletteTest.java", "workspace/WorkspaceRolesTest.java"):
    edit(APP / name, ("dev.jasper.ui.swing.NativeControlStyles.", "new dev.jasper.ui.swing.NativeStyles(dev.jasper.ui.kit.Kit.DEFAULT)."))
edit(APP / "workspace/WorkspaceMetricsTest.java",
     ('swing.resolve("NativeChromePainting.java"), swing.resolve("NativeControlStyles.java")',
      'swing.resolve("ChromePainter.java"), swing.resolve("NativeStyles.java")'),
     ('Files.readString(swing.resolve("NativeChromePainting.java"))', 'Files.readString(swing.resolve("ChromePainter.java"))'),
     ('"NativeChromePainting.statusDot"', '"ChromePainter.installed().statusDot"'),
     ('"NativeChromePainting.tabStrip"', '"ChromePainter.installed().tabStrip"'))
PY
git grep -n -e NativeChromePainting -e NativeControlStyles -- '*/src/test/*.java'
```

Expected: the `git grep` prints nothing. (`ChromePainterTest`'s two `DesignMetrics.DEFAULT.value(` expectations at its
old lines 94-95 and `NativeStylesTest`'s at 63 keep reading the frozen catalog; Task 10's `StaticDefaultTest` scans
only the production jars.)

- [ ] **Step 3: Write the failing seam tests**

In `ChromePainterTest.java`, insert before the file's last line (the class's closing `}`):

```java

    /** The seam: every metric a painter reads is its own kit's; the default kit's painter is unchanged. */
    @Test void aPainterReadsOnlyItsOwnKit() {
        var theme = KitFixtures.marked();
        var painter = new ChromePainter(KitFixtures.kit(Kit.DEFAULT.metrics().with(DesignMetrics.Role.HAIRLINE, 3)
            .with(DesignMetrics.Role.DOCK_DROP_FILL_ALPHA, 10).with(DesignMetrics.Role.DOCK_DROP_BORDER_ALPHA, 200)
            .with(DesignMetrics.Role.NATIVE_HOVER_OVERLAY_PERCENT, 50)));
        assertThat(painter.dropGuide(theme, false).getAlpha()).isEqualTo(10);
        assertThat(painter.dropGuide(theme, true).getAlpha()).isEqualTo(200);
        assertThat(painter.hoverOverlay(theme)).isEqualTo(ColorMath.opacity(theme.semantic().color(SemanticColor.HOVER_OVERLAY), .5));
        var row = new JPanel();
        row.setSize(40, 20);
        var image = new BufferedImage(40, 20, BufferedImage.TYPE_INT_ARGB);
        var g = image.createGraphics();
        try { painter.bottomHairline(row, g, theme); } finally { g.dispose(); }
        int line = com.formdev.flatlaf.util.UIScale.scale(3);
        assertThat(image.getRGB(20, 20 - line)).isEqualTo(theme.semantic().color(SemanticColor.BORDER_SUBTLE).getRGB());
        assertThat(image.getRGB(20, 20 - line - 1) >>> 24).as("nothing above the kit's hairline").isZero();
        assertThat(PAINTER.dropGuide(theme, false).getAlpha()).as("the default kit's painter is unchanged")
            .isEqualTo(Kit.DEFAULT.metrics().value(DesignMetrics.Role.DOCK_DROP_FILL_ALPHA));
    }
```

In `NativeStylesTest.java`, insert before the file's last line:

```java

    /** The seam: styles read only their own kit's metrics, scaled once, and the ghost arc unscaled for FlatLaf. */
    @Test void stylesReadOnlyTheirOwnKit() {
        Edt.run(() -> {
            var theme = KitFixtures.dark();
            var styles = new NativeStyles(KitFixtures.kit(Kit.DEFAULT.metrics().with(DesignMetrics.Role.HAIRLINE, 3)
                .with(DesignMetrics.Role.RAIL_BUTTON_ARC, 13)));
            assertThat(styles.metric(DesignMetrics.Role.HAIRLINE)).isEqualTo(com.formdev.flatlaf.util.UIScale.scale(3));
            assertThat(STYLES.metric(DesignMetrics.Role.HAIRLINE)).isEqualTo(com.formdev.flatlaf.util.UIScale.scale(1));
            var rail = new JButton();
            styles.button(rail, () -> theme, DesignMetrics.Role.RAIL_BUTTON_ARC);
            assertThat(((Map<?, ?>) rail.getClientProperty("FlatLaf.style")).get("arc")).as("logical; FlatLaf scales style values").isEqualTo(13);
            assertThat(styles.padding(DesignMetrics.Role.HAIRLINE, DesignMetrics.Role.HAIRLINE).getBorderInsets(rail).top)
                .isEqualTo(com.formdev.flatlaf.util.UIScale.scale(3));
            assertThat(styles.font(dev.jasper.ui.metrics.Typography.Role.BODY)).as("the same type and faces")
                .isEqualTo(STYLES.font(dev.jasper.ui.metrics.Typography.Role.BODY));
        });
    }

    /** Review Focus 5: the installed styles and painter follow the installed kit at once, one instance per kit. */
    @Test void installedFollowsTheInstalledKitWithOneInstancePerKit() {
        Edt.run(() -> {
            var prior = UIManager.getLookAndFeel();
            var theme = KitFixtures.dark();
            var derived = KitFixtures.kit(Kit.DEFAULT.metrics().with(DesignMetrics.Role.HAIRLINE, 3));
            try {
                FlatLafInstaller.install(theme, Kit.DEFAULT);
                assertThat(NativeStyles.installed().kit()).isSameAs(Kit.DEFAULT);
                assertThat(NativeStyles.installed()).isSameAs(NativeStyles.installed());
                assertThat(ChromePainter.installed()).isSameAs(ChromePainter.installed());
                FlatLafInstaller.install(theme, derived);
                assertThat(NativeStyles.installed().kit()).isSameAs(derived);
                assertThat(ChromePainter.installed().kit()).isSameAs(derived);
                assertThat(NativeStyles.installed().metric(DesignMetrics.Role.HAIRLINE)).isEqualTo(com.formdev.flatlaf.util.UIScale.scale(3));
                FlatLafInstaller.install(theme, Kit.DEFAULT);
                assertThat(ChromePainter.installed().kit()).as("back to the default kit at once").isSameAs(Kit.DEFAULT);
                assertThat(NativeStyles.installed().kit()).isSameAs(Kit.DEFAULT);
            } finally {
                try { UIManager.setLookAndFeel(prior); } catch (UnsupportedLookAndFeelException failure) { throw new AssertionError(failure); }
            }
        });
    }
```

- [ ] **Step 4: Run the tests to verify they fail**

Run: `./gradlew :jasper-ui:test --tests 'dev.jasper.ui.swing.ChromePainterTest' --tests 'dev.jasper.ui.swing.NativeStylesTest'`
Expected: FAIL in `:jasper-ui:compileTestJava` with `error: cannot find symbol` / `symbol: class ChromePainter` and
`class NativeStyles`.

- [ ] **Step 5: Make the two classes kit instances**

```bash
git mv jasper-ui/src/main/java/dev/jasper/ui/swing/NativeChromePainting.java jasper-ui/src/main/java/dev/jasper/ui/swing/ChromePainter.java
git mv jasper-ui/src/main/java/dev/jasper/ui/swing/NativeControlStyles.java jasper-ui/src/main/java/dev/jasper/ui/swing/NativeStyles.java
```

Replace the whole of `jasper-ui/src/main/java/dev/jasper/ui/swing/ChromePainter.java` with (the bodies are
`NativeChromePainting`'s, with `NativeControlStyles.metric(` and `NativeControlStyles.font(` read through this kit's
styles and each `DesignMetrics.DEFAULT.value(` read from this kit's metrics):

```java
package dev.jasper.ui.swing;

import dev.jasper.ui.kit.Kit;
import dev.jasper.ui.metrics.*;
import dev.jasper.ui.theme.*;
import java.awt.*;
import java.util.Objects;
import javax.swing.*;
import com.formdev.flatlaf.util.UIScale;

/**
 * Kit-owned painters for temporary native chrome control adapters, over one kit's metrics and type; native input and
 * layout remain host-owned. Application chrome paints with {@link #installed()}; a test builds one for the kit it checks.
 */
public final class ChromePainter {
    /** The painter of the installed kit last asked for; replaced when the installed kit changes identity. */
    private static volatile ChromePainter current;
    private final Kit kit;
    private final NativeStyles styles;

    /** A painter over {@code kit}'s metrics and type. */
    public ChromePainter(Kit kit) {
        this.kit = Objects.requireNonNull(kit, "kit");
        this.styles = new NativeStyles(kit);
    }

    /** The painter of {@link FlatLafInstaller#installedKit()}: one instance until the installed kit changes. */
    public static ChromePainter installed() {
        Kit kit = FlatLafInstaller.installedKit();
        ChromePainter painter = current;
        if (painter == null || painter.kit != kit) current = painter = new ChromePainter(kit);
        return painter;
    }

    /** The kit whose metrics and type this painter reads. */
    public Kit kit() { return kit; }

    // metric() is scaled for the display; value() is the logical role (an alpha, a percentage or a span the caller scales).
    private int metric(DesignMetrics.Role role) { return styles.metric(role); }
    private int value(DesignMetrics.Role role) { return kit.metrics().value(role); }
    private Font font(Typography.Role role) { return styles.font(role); }

    /** Applies the engine's text coverage to a caller-owned graphics clone. */
    public void prepareText(Graphics2D graphics) { ThemePainting.antialias(graphics); }
    /** Literal advance measured with the same fractional context used by engine labels and native text. */
    public int measureText(Font font, String text) { return ThemePainting.measureText(font, text); }

    public void action(AbstractButton button, Graphics graphics, GeneratedTheme theme, boolean labels, Icon arrow) {
        var g = (Graphics2D) graphics.create();
        try {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            var model = button.getModel();
            var palette = theme.semantic();
            if (button.isEnabled() && (model.isRollover() || model.isPressed() || model.isSelected())) {
                g.setColor(model.isPressed() || model.isSelected() ? palette.color(SemanticColor.SURFACE_CONTROL_SELECTED)
                    : hoverOverlay(theme));
                int arc = metric(DesignMetrics.Role.NATIVE_TOOLBAR_HOVER_ARC);
                g.fillRoundRect(0, 0, button.getWidth(), button.getHeight(), arc, arc);
            }
            if (!button.isEnabled()) g.setComposite(AlphaComposite.SrcOver.derive(
                value(DesignMetrics.Role.NATIVE_TOOLBAR_DISABLED_PERCENT) / 100f));
            Icon icon = button.getIcon();
            int pad = metric(DesignMetrics.Role.NATIVE_ACTION_PADDING);
            int arrowSpace = metric(DesignMetrics.Role.NATIVE_MENU_SPACE);
            int x = labels ? pad : arrow != null ? Math.max(pad, (button.getWidth() - arrowSpace - icon.getIconWidth()) / 2)
                : (button.getWidth() - icon.getIconWidth()) / 2;
            icon.paintIcon(button, g, x, (button.getHeight() - icon.getIconHeight()) / 2);
            x += icon.getIconWidth();
            if (labels) {
                x += pad; g.setFont(font(Typography.Role.TOOLBAR_LABEL)); g.setColor(palette.color(SemanticColor.TEXT_PRIMARY));
                var metrics = g.getFontMetrics();
                g.drawString(button.getText(), x, (button.getHeight() - metrics.getHeight()) / 2 + metrics.getAscent());
                x += metrics.stringWidth(button.getText());
            }
            if (arrow != null) { x += metric(DesignMetrics.Role.NATIVE_MENU_ARROW_GAP); arrow.paintIcon(button, g, x, (button.getHeight() - arrow.getIconHeight()) / 2); }
        } finally { g.dispose(); }
    }

    public void search(JComponent component, Graphics graphics, GeneratedTheme theme, boolean hover, boolean collapsed,
                       String context, String hint, Icon search, Icon contextIcon) {
        var g = (Graphics2D) graphics.create();
        try {
            var palette = theme.semantic();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            int arc = metric(DesignMetrics.Role.TITLE_SEARCH_ARC), width = component.getWidth(), height = component.getHeight();
            g.setColor(palette.color(SemanticColor.SURFACE_FIELD_RAISED));
            g.fillRoundRect(0, 0, width - 1, height - 1, arc, arc);
            g.setColor(palette.color(hover ? SemanticColor.BORDER_FIELD_RAISED_HOVER : SemanticColor.BORDER_FIELD_RAISED));
            g.drawRoundRect(0, 0, width - 1, height - 1, arc, arc);
            if (collapsed) { search.paintIcon(component, g, (width - search.getIconWidth()) / 2, (height - search.getIconHeight()) / 2); return; }
            int pad = metric(DesignMetrics.Role.TITLE_SEARCH_PADDING), gap = metric(DesignMetrics.Role.TITLE_SEARCH_GAP), x = pad;
            search.paintIcon(component, g, x, (height - search.getIconHeight()) / 2);
            x += search.getIconWidth() + gap;
            g.setFont(font(Typography.Role.TITLE_CONTEXT));
            var metrics = g.getFontMetrics();
            int hintWidth = hint.isEmpty() ? 0 : metrics.stringWidth(hint);
            int end = width - pad - (hintWidth == 0 ? 0 : hintWidth + gap);
            if (contextIcon != null && x + contextIcon.getIconWidth() <= end) {
                contextIcon.paintIcon(component, g, x, (height - contextIcon.getIconHeight()) / 2);
                x += contextIcon.getIconWidth() + gap;
            }
            int baseline = (height - metrics.getHeight()) / 2 + metrics.getAscent();
            g.setColor(palette.color(SemanticColor.TEXT_FIELD_RAISED)); g.drawString(clip(context, metrics, end - x), x, baseline);
            if (hintWidth > 0 && width - pad - hintWidth > x) {
                g.setColor(SurfaceContrast.on(palette, SemanticColor.TEXT_PLACEHOLDER,
                    SemanticColor.SURFACE_FIELD_RAISED, SurfaceContrast.INDICATOR)); g.drawString(hint, width - pad - hintWidth, baseline);
            }
        } finally { g.dispose(); }
    }
    /** The native chrome's resting hover tint: the hover overlay at its named share. */
    public Color hoverOverlay(GeneratedTheme theme) {
        return ColorMath.opacity(theme.semantic().color(SemanticColor.HOVER_OVERLAY),
            value(DesignMetrics.Role.NATIVE_HOVER_OVERLAY_PERCENT) / 100.0);
    }

    /**
     * A toolbar action's preferred width: square at {@code side} for an icon-only action; otherwise padding, icon,
     * padding, the label in the toolbar role and trailing padding; a menu action adds the arrow's space.
     */
    public int actionWidth(AbstractButton button, int side, boolean labels, boolean menu) {
        int pad = value(DesignMetrics.Role.NATIVE_ACTION_PADDING);
        // The leading padding, the toolbar icon and the gap scale as one span, as the label's trailing padding does.
        int width = labels ? UIScale.scale(2 * pad + value(DesignMetrics.Role.TOOLBAR_ACTION_ICON)) + UIScale.scale(pad)
            + button.getFontMetrics(font(Typography.Role.TOOLBAR_LABEL)).stringWidth(button.getText()) : side;
        return menu ? width + metric(DesignMetrics.Role.NATIVE_MENU_SPACE) : width;
    }

    /** A toolbar's vertical rule between action groups: its whole bounds in the subtle border role. */
    public void rule(JComponent component, Graphics graphics, GeneratedTheme theme) {
        graphics.setColor(theme.semantic().color(SemanticColor.BORDER_SUBTLE));
        graphics.fillRect(0, 0, component.getWidth(), component.getHeight());
    }

    /** The hairline closing a native row (the find bar) along its lower edge. */
    public void bottomHairline(JComponent component, Graphics graphics, GeneratedTheme theme) {
        int line = metric(DesignMetrics.Role.HAIRLINE);
        graphics.setColor(theme.semantic().color(SemanticColor.BORDER_SUBTLE));
        graphics.fillRect(0, component.getHeight() - line, component.getWidth(), line);
    }

    /**
     * The native title row's edges: a header row (the workspace title bar) gets the hover overlay's highlight along its
     * top; a window's title row gets a subtle hairline along its bottom; a dialog's title band, which continues into
     * the dialog surface, gets neither.
     */
    public void titleBarEdges(JComponent bar, Graphics graphics, GeneratedTheme theme, boolean header, boolean rule) {
        int line = metric(DesignMetrics.Role.HAIRLINE);
        if (header) { graphics.setColor(hoverOverlay(theme)); graphics.fillRect(0, 0, bar.getWidth(), line); }
        if (!header && rule) {
            graphics.setColor(theme.semantic().color(SemanticColor.BORDER_SUBTLE));
            graphics.fillRect(0, bar.getHeight() - line, bar.getWidth(), line);
        }
    }

    /**
     * An equal-width workspace tab's surface: the selected tab is the terminal's surface and the others the tab strip's,
     * kept clearly apart from it ({@link SurfaceContrast#TAB_LIGHTNESS}) so the selected tab shows whatever the theme gives
     * the two, both at {@code base}'s alpha (the window's opacity while it is translucent).
     */
    public Color tabSurface(GeneratedTheme workspace, Color base, boolean selected) {
        var palette = workspace.semantic();
        Color terminal = palette.color(SemanticColor.SURFACE_TERMINAL);
        Color role = selected ? terminal : SurfaceContrast.apart(palette.color(SemanticColor.SURFACE_TABSTRIP), terminal, SurfaceContrast.TAB_LIGHTNESS);
        return new Color(role.getRed(), role.getGreen(), role.getBlue(), base.getAlpha());
    }

    /**
     * The equal-width tab strip: the strip's surface behind everything, each slot's surface (only {@code selected} the
     * terminal's), the hover overlay over a hovered inactive slot, the selected slot's indicator along its top
     * ({@code tab.indicator}, or {@code tab.indicator.inactive} while the window is not {@code active}), the
     * window-border dividers between slots and the rule along the bottom. Slot bounds are the host's retained motion
     * geometry; {@code selected} and {@code hovered} are -1 when no slot is.
     */
    public void tabStrip(JComponent strip, Graphics graphics, GeneratedTheme workspace, Color base,
                         java.util.List<Rectangle> slots, int selected, int hovered, boolean active) {
        var g = (Graphics2D) graphics.create();
        try {
            g.setComposite(AlphaComposite.Src);
            g.setColor(tabSurface(workspace, base, false)); g.fillRect(0, 0, strip.getWidth(), strip.getHeight());
            for (int i = 0; i < slots.size(); i++) { g.setColor(tabSurface(workspace, base, i == selected)); g.fill(slots.get(i)); }
            g.setComposite(AlphaComposite.SrcOver);
            if (hovered >= 0 && hovered < slots.size() && hovered != selected) { g.setColor(hoverOverlay(workspace)); g.fill(slots.get(hovered)); }
            if (selected >= 0 && selected < slots.size()) {
                Rectangle tab = slots.get(selected);
                g.setColor(workspace.semantic().color(active ? SemanticColor.TAB_INDICATOR : SemanticColor.TAB_INDICATOR_INACTIVE));
                g.fillRect(tab.x, tab.y, tab.width, metric(DesignMetrics.Role.TAB_INDICATOR));
            }
            g.setColor(workspace.semantic().color(SemanticColor.BORDER_WINDOW));
            int line = metric(DesignMetrics.Role.TAB_DIVIDER);
            for (int i = 0; i < slots.size() - 1; i++) {
                Rectangle bounds = slots.get(i);
                if (bounds.width > 0) g.fillRect(bounds.x + bounds.width - line, 0, line, strip.getHeight());
            }
            g.fillRect(0, strip.getHeight() - line, strip.getWidth(), line);
        } finally { g.dispose(); }
    }

    /** The status bar's running dot at its leading gap: success while a shell runs, the idle status otherwise. */
    public void statusDot(JComponent bar, Graphics graphics, GeneratedTheme theme, boolean running) {
        var g = (Graphics2D) graphics.create();
        try {
            var palette = theme.semantic();
            g.setColor(running ? SurfaceContrast.on(palette, SemanticColor.STATUS_SUCCESS, SemanticColor.SURFACE_STATUS, SurfaceContrast.TEXT)
                : palette.color(SemanticColor.STATUS_IDLE));
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            int size = metric(DesignMetrics.Role.STATUS_RUNNING_DOT);
            g.fillOval(metric(DesignMetrics.Role.STATUS_GAP), (bar.getHeight() - size) / 2, size, size);
        } finally { g.dispose(); }
    }

    /**
     * An unread badge at an icon's top-right corner on the rail: a rail-coloured ring, then the accent (or danger) dot
     * held to the indicator floor on the rail surface.
     */
    public void badge(Graphics graphics, int x, int y, int iconWidth, GeneratedTheme theme, boolean error) {
        var g = (Graphics2D) graphics.create();
        try {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            int dot = metric(DesignMetrics.Role.RAIL_BADGE_DOT), ring = metric(DesignMetrics.Role.HAIRLINE);
            int left = x + iconWidth - dot;
            var palette = theme.semantic();
            Color rail = palette.color(SemanticColor.SURFACE_APP);
            if (rail != null) { g.setColor(rail); g.fillOval(left - ring, y - ring, dot + 2 * ring, dot + 2 * ring); }
            g.setColor(SurfaceContrast.on(palette, error ? SemanticColor.STATUS_DANGER : SemanticColor.ACCENT, SemanticColor.SURFACE_APP,
                SurfaceContrast.INDICATOR));
            g.fillOval(left, y, dot, dot);
        } finally { g.dispose(); }
    }

    /** A tool window header's connected dot: the success status held to the indicator floor on the panel surface. */
    public void connectedDot(Graphics graphics, int x, int y, int size, GeneratedTheme theme) {
        var g = (Graphics2D) graphics.create();
        try {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setColor(SurfaceContrast.on(theme.semantic(), SemanticColor.STATUS_SUCCESS, SemanticColor.SURFACE_PANEL, SurfaceContrast.INDICATOR));
            g.fillOval(x, y, size, size);
        } finally { g.dispose(); }
    }

    /** The thin rule centred in a native split divider's wider drag area, in the divider token. */
    public void splitDivider(Graphics graphics, int x, int y, int width, int height, boolean vertical, GeneratedTheme theme) {
        int thickness = metric(DesignMetrics.Role.SPLIT_STROKE);
        Graphics g = graphics.create();
        try {
            g.setColor(theme.semantic().color(SemanticColor.BORDER_WINDOW));
            if (vertical) g.fillRect(x + (width - thickness) / 2, y, thickness, height);
            else g.fillRect(x, y + (height - thickness) / 2, width, thickness);
        } finally { g.dispose(); }
    }

    /** A dock drop guide colour: the accent at the named fill or border alpha. */
    public Color dropGuide(GeneratedTheme theme, boolean border) {
        Color accent = theme.semantic().color(SemanticColor.ACCENT);
        return new Color(accent.getRed(), accent.getGreen(), accent.getBlue(),
            value(border ? DesignMetrics.Role.DOCK_DROP_BORDER_ALPHA : DesignMetrics.Role.DOCK_DROP_FILL_ALPHA));
    }

    /**
     * Dock drop guides: a square highlight over a whole zone and a rounded placeholder where the window lands. Each fill
     * sits inside its border so the two translucent colours never stack; the placeholder border is stroked half a pixel
     * in, so its inner edge and the fill share one corner diameter.
     */
    public void dropGuides(Graphics graphics, Rectangle highlight, Rectangle placeholder, Color fill, Color border) {
        var g = (Graphics2D) graphics.create();
        try {
            if (highlight != null) {
                g.setColor(fill);
                g.fillRect(highlight.x + 1, highlight.y + 1, highlight.width - 2, highlight.height - 2);
                g.setColor(border);
                g.drawRect(highlight.x, highlight.y, highlight.width - 1, highlight.height - 1);
            }
            if (placeholder != null) {
                g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                double arc = metric(DesignMetrics.Role.DOCK_DROP_ARC);
                double inner = Math.max(0, arc - 1);
                g.setColor(fill);
                g.fill(new java.awt.geom.RoundRectangle2D.Double(placeholder.x + 1, placeholder.y + 1, placeholder.width - 2, placeholder.height - 2, inner, inner));
                g.setColor(border);
                g.draw(new java.awt.geom.RoundRectangle2D.Double(placeholder.x + 0.5, placeholder.y + 0.5, placeholder.width - 1, placeholder.height - 1, arc, arc));
            }
        } finally { g.dispose(); }
    }

    /**
     * The command palette's soft shadow around its card, theme.md's popover shadow: black, 12 px down, 32 px blur, 50% in
     * Dark and 18% in Light (as {@code dark} says).
     */
    public void popupShadow(Graphics graphics, Rectangle card, GeneratedTheme theme, boolean dark) {
        ThemePainting.softShadow((Graphics2D) graphics, new java.awt.geom.Rectangle2D.Double(card.x, card.y, card.width, card.height),
            metric(DesignMetrics.Role.PALETTE_CORNER_ARC) / 2.0, metric(DesignMetrics.Role.POPOVER_SHADOW_OFFSET),
            metric(DesignMetrics.Role.POPOVER_SHADOW_BLUR),
            value(dark ? DesignMetrics.Role.POPOVER_SHADOW_PERCENT_DARK : DesignMetrics.Role.POPOVER_SHADOW_PERCENT_LIGHT) / 100.0);
    }

    public String clip(String text, FontMetrics metrics, int width) {
        if (width <= 0) return "";
        if (metrics.stringWidth(text) <= width) return text;
        for (int end = text.offsetByCodePoints(text.length(), -1); end > 0; end = text.offsetByCodePoints(end, -1)) {
            String candidate = text.substring(0, end) + "…";
            if (metrics.stringWidth(candidate) <= width) return candidate;
        }
        return "";
    }
}
```

(The `…` in `clip` is U+2026, already in the old file and outside the AGENTS hygiene ranges.)

Replace the whole of `jasper-ui/src/main/java/dev/jasper/ui/swing/NativeStyles.java` with (the bodies are
`NativeControlStyles`', made instance methods; `metric`, the ghost arc and the context-free `font` read this kit):

```java
package dev.jasper.ui.swing;

import com.formdev.flatlaf.util.UIScale;
import dev.jasper.ui.kit.Kit;
import dev.jasper.ui.metrics.*;
import dev.jasper.ui.theme.*;
import java.awt.*;
import java.util.Map;
import java.util.Objects;
import java.util.function.Supplier;
import javax.swing.*;

/**
 * Kit-owned styling for identity-sensitive native Swing adapters, over one kit's metrics and type. Does not rebuild
 * documents, models, input maps or callbacks. App composition migrates these adapters to descriptor views separately.
 * Application chrome styles with {@link #installed()}; a test builds one for the kit it checks.
 */
public final class NativeStyles {
    /** The styles of the installed kit last asked for; replaced when the installed kit changes identity. */
    private static volatile NativeStyles current;
    private final Kit kit;

    /** Styles over {@code kit}'s metrics and type. */
    public NativeStyles(Kit kit) { this.kit = Objects.requireNonNull(kit, "kit"); }

    /** The styles of {@link FlatLafInstaller#installedKit()}: one instance until the installed kit changes. */
    public static NativeStyles installed() {
        Kit kit = FlatLafInstaller.installedKit();
        NativeStyles styles = current;
        if (styles == null || styles.kit != kit) current = styles = new NativeStyles(kit);
        return styles;
    }

    /** The kit whose metrics and type these styles read. */
    public Kit kit() { return kit; }
    /** Native split interaction with an engine-owned separator; no feature paints or geometry. */
    public javax.swing.plaf.basic.BasicSplitPaneUI splitPaneUI(Supplier<GeneratedTheme> theme) {
        return new javax.swing.plaf.basic.BasicSplitPaneUI() {
            @Override public void paint(Graphics graphics,JComponent component) {
                super.paint(graphics,component);
                var divider=getDivider();
                if(divider==null)return;
                graphics.setColor(theme.get().semantic().color(SemanticColor.BORDER_SUBTLE));
                int line=NativeStyles.this.metric(DesignMetrics.Role.HAIRLINE);
                // Paint in the lightweight owner. BasicSplitPaneDivider is an AWT Container and
                // is omitted by headless print traversal even though it has valid allocated bounds.
                if(splitPane.getOrientation()==JSplitPane.HORIZONTAL_SPLIT)graphics.fillRect(divider.getX(),divider.getY(),line,divider.getHeight());
                else graphics.fillRect(divider.getX(),divider.getY(),divider.getWidth(),line);
            }
            @Override public javax.swing.plaf.basic.BasicSplitPaneDivider createDefaultDivider() {
                return new javax.swing.plaf.basic.BasicSplitPaneDivider(this) {
                    @Override public void paint(Graphics graphics) { }
                };
            }
        };
    }
    /** Exact host point-size overrides, or this kit's catalog roles with user scaling applied once. */
    public Font font(Typography.Role role) {
        return SwingTypography.effective(SwingTypography.typography(kit).font(role));
    }
    /** Context role font, in the context's kit, with the same exactly-once host/user scaling policy as descriptor controls. */
    public Font font(KitContext context,Typography.Role role) { return ThemePainting.font(context,role); }
    /** Observes the engine's supported font defaults and user scale for one native lifetime owner.
     * Registration has no callback. Delivery is on the EDT; closing is idempotent and suppresses queued delivery. */
    public AutoCloseable observeTypography(Runnable refresh) {
        java.util.Objects.requireNonNull(refresh);
        var closed=new java.util.concurrent.atomic.AtomicBoolean();
        Runnable deliver=()->{if(!closed.get())refresh.run();};
        Runnable dispatch=()->{if(EventQueue.isDispatchThread())deliver.run();else EventQueue.invokeLater(deliver);};
        java.beans.PropertyChangeListener fonts=event->{if(SwingTypography.fontProperty(event.getPropertyName()))dispatch.run();};
        java.beans.PropertyChangeListener scale=event->{if("userScaleFactor".equals(event.getPropertyName()))dispatch.run();};
        var defaults=UIManager.getDefaults();
        defaults.addPropertyChangeListener(fonts);
        try {UIScale.addPropertyChangeListener(scale);}
        catch(RuntimeException|Error failure){closed.set(true);defaults.removePropertyChangeListener(fonts);throw failure;}
        return ()->{if(closed.compareAndSet(false,true)){defaults.removePropertyChangeListener(fonts);UIScale.removePropertyChangeListener(scale);}};
    }
    /** Scaled spacing from this kit's catalog. */
    public int metric(DesignMetrics.Role role) { return UIScale.scale(kit.metrics().value(role)); }
    /** Borderless native command; host retains focusability and Action ownership. */
    public void unframed(AbstractButton button) {
        button.setBorder(BorderFactory.createEmptyBorder()); button.setContentAreaFilled(false); button.setOpaque(false);
    }
    /** Role padding for native adapters; callers supply semantic sides, not literal geometry. */
    public javax.swing.border.Border padding(DesignMetrics.Role vertical, DesignMetrics.Role horizontal) {
        return BorderFactory.createEmptyBorder(metric(vertical), metric(horizontal), metric(vertical), metric(horizontal));
    }
    /**
     * Compact ghost toolbar action: module role colors only. The host's existing arc and toolbar margins are kept;
     * action/model and focus policy remain host-owned.
     */
    public void button(AbstractButton button, Supplier<GeneratedTheme> theme) { ghost(button, theme, null); }
    /** Ghost toolbar action with a named, preserved native arc (for example the tool-window rail's 10). */
    public void button(AbstractButton button, Supplier<GeneratedTheme> theme, DesignMetrics.Role arc) {
        ghost(button, theme, java.util.Objects.requireNonNull(arc));
    }
    private void ghost(AbstractButton button, Supplier<GeneratedTheme> theme, DesignMetrics.Role arc) {
        bind(button, () -> {
            var palette = theme.get().semantic();
            button.setFont(font(Typography.Role.LABEL));
            button.setForeground(palette.color(SemanticColor.TEXT_SECONDARY));
            button.setOpaque(false);
            button.putClientProperty("JButton.buttonType", "toolBarButton");
            var style = new java.util.LinkedHashMap<String, Object>();
            if (arc != null) style.put("arc", kit.metrics().value(arc));
            style.put("toolbar.hoverBackground", ColorMath.opacity(palette.color(SemanticColor.HOVER_OVERLAY), .08));
            style.put("toolbar.pressedBackground", palette.color(SemanticColor.SURFACE_CONTROL_HOVER));
            style.put("toolbar.selectedBackground", palette.color(SemanticColor.SURFACE_CONTROL_SELECTED));
            button.putClientProperty("FlatLaf.style", Map.copyOf(style));
        });
    }
    /**
     * Standard framed command (the kit's secondary kind): module role colors; the host keeps the look and feel's
     * button arc, margin, font and focus behavior. Native framed commands stay framed until Plan 3 Task 10.
     */
    public void framedButton(AbstractButton button, Supplier<GeneratedTheme> theme) {
        bind(button, () -> {
            var palette = theme.get().semantic();
            button.putClientProperty("JButton.buttonType", null);
            button.setContentAreaFilled(true); button.setBorderPainted(true);
            button.setForeground(palette.color(SemanticColor.TEXT_STRONG_SECONDARY));
            button.putClientProperty("FlatLaf.style", Map.of(
                "background", palette.color(SemanticColor.BUTTON_SECONDARY_BG),
                "hoverBackground", palette.color(SemanticColor.BUTTON_SECONDARY_BG_HOVER),
                "borderColor", palette.color(SemanticColor.BUTTON_SECONDARY_BORDER),
                "focusedBorderColor", palette.color(SemanticColor.BORDER_FOCUS)));
        });
    }
    /** Native informational label; text and accessibility remain host-owned. */
    public void label(JLabel label, Typography.Role role, SemanticColor color, Supplier<GeneratedTheme> theme) {
        bind(label, () -> { label.setFont(font(role)); label.setForeground(theme.get().semantic().color(color)); });
    }
    /** Read-only native diagnostics text retaining its document and selection. */
    public void textArea(JTextArea text, Supplier<GeneratedTheme> theme) {
        bind(text, () -> {
            var palette = theme.get().semantic();
            text.setFont(font(Typography.Role.BODY));
            text.setBackground(palette.color(SemanticColor.SURFACE_PANEL));
            text.setForeground(palette.color(SemanticColor.TEXT_PRIMARY));
            text.setCaretColor(palette.color(SemanticColor.TEXT_PRIMARY));
            text.setSelectionColor(palette.color(SemanticColor.SELECTION_BG));
            text.setSelectedTextColor(palette.color(SemanticColor.TEXT_ONSELECTION));
        });
    }
    /** Native menu presentation (colors and typography); keyboard routing, actions and look-and-feel margins stay with the host. */
    public void menu(JMenuItem item, Supplier<GeneratedTheme> theme) {
        bind(item, () -> {
            var palette = theme.get().semantic();
            // The kit menu's type, so native and kit menus read the same.
            item.setFont(font(Typography.Role.BODY));
            item.setForeground(palette.color(SemanticColor.TEXT_PRIMARY));
            item.setBackground(palette.color(SemanticColor.SURFACE_MENU));
            item.putClientProperty("FlatLaf.style", Map.of("selectionBackground", palette.color(SemanticColor.ACCENT),
                "selectionForeground", palette.color(SemanticColor.TEXT_ONACCENT)));
        });
    }
    /** Styles an existing popup hierarchy without replacing items, models or listeners. */
    public void menuTree(JPopupMenu popup, Supplier<GeneratedTheme> theme) {
        bind(popup, () -> popup.setBackground(theme.get().semantic().color(SemanticColor.SURFACE_MENU)));
        for (Component child : popup.getComponents()) {
            if (child instanceof JMenuItem item) menu(item, theme);
            if (child instanceof JMenu menu) menuTree(menu.getPopupMenu(), theme);
        }
    }
    /**
     * Borderless in-row field (the find bar's query): module colors and typography with the existing zero border
     * and focus widths, so the row keeps its native height and has no focus ring until Plan 3 Task 10.
     */
    public void borderlessField(JTextField field, Supplier<GeneratedTheme> theme) {
        bind(field, () -> {
            fieldColors(field, theme.get());
            field.putClientProperty("FlatLaf.style", "borderWidth: 0; focusWidth: 0; innerFocusWidth: 0");
        });
    }
    private void fieldColors(JTextField field, GeneratedTheme theme) {
        var palette = theme.semantic();
        field.setFont(font(Typography.Role.LABEL));
        field.setBackground("error".equals(field.getClientProperty("JComponent.outline"))
            ? UiColorKeys.errorTint(palette) : palette.color(SemanticColor.SURFACE_INPUT));
        field.setForeground(palette.color(SemanticColor.TEXT_PRIMARY));
        field.setCaretColor(palette.color(SemanticColor.TEXT_PRIMARY));
        field.setSelectionColor(palette.color(SemanticColor.SELECTION_BG));
        field.setSelectedTextColor(palette.color(SemanticColor.TEXT_ONSELECTION));
    }
    /** Reapply native adapter roles after either a LAF refresh or a workspace-only appearance update. */
    public void refresh(JComponent component) {
        if (component.getClientProperty("Jasper.kit.nativeStyle") instanceof Runnable refresh) refresh.run();
    }
    private void bind(JComponent component, Runnable refresh) {
        if (component.getClientProperty("Jasper.kit.nativeStyle") == null)
            component.addPropertyChangeListener("UI", event -> refresh(component));
        component.putClientProperty("Jasper.kit.nativeStyle", refresh);
        refresh.run();
    }
}
```

In `jasper-ui/src/main/java/dev/jasper/ui/swing/ThemePainting.java`, after line 17
(`static Font font(KitContext context, Typography.Role role) { ... }`) insert (the file already imports
`java.awt.font.FontRenderContext` and `java.awt.*`):

```java
    /** The fractional antialiased context engine labels and native text measure in. */
    private static final FontRenderContext TEXT_CONTEXT = new FontRenderContext(null, true, true);
    /** Literal advance measured with the same fractional context used by engine labels and native text. */
    static int measureText(Font font, String text) { return (int) Math.ceil(font.getStringBounds(text, TEXT_CONTEXT).getWidth()); }
```

This is `NativeChromePainting.java:12,16`'s arithmetic, unchanged.

- [ ] **Step 6: Convert every caller**

Run this script over the 36 main files that name either class. jasper-ui's three measurers call
`ThemePainting.measureText`; everywhere else `NativeChromePainting.x(` becomes `ChromePainter.installed().x(` and
`NativeControlStyles.x(` becomes `NativeStyles.installed().x(` (fully qualified forms likewise), and the imports follow.
Method names and arguments never change. It stops unless it made exactly 153 replacements:

```bash
python3 -I - <<'PY'
import re, subprocess, pathlib
UI = {f"jasper-ui/src/main/java/dev/jasper/ui/swing/{n}.java" for n in ("Composition", "RichOptions", "Tables")}
RULES = [
    (r'^import dev\.jasper\.ui\.swing\.NativeChromePainting;', 'import dev.jasper.ui.swing.ChromePainter;'),
    (r'^import dev\.jasper\.ui\.swing\.NativeControlStyles;', 'import dev.jasper.ui.swing.NativeStyles;'),
    (r'dev\.jasper\.ui\.swing\.NativeChromePainting\.(?=[a-z]\w*\s*\()', 'dev.jasper.ui.swing.ChromePainter.installed().'),
    (r'(?<![\w.])NativeChromePainting\.(?=[a-z]\w*\s*\()', 'ChromePainter.installed().'),
    (r'dev\.jasper\.ui\.swing\.NativeControlStyles\.(?=[a-z]\w*\s*\()', 'dev.jasper.ui.swing.NativeStyles.installed().'),
    (r'(?<![\w.])NativeControlStyles\.(?=[a-z]\w*\s*\()', 'NativeStyles.installed().'),
]
files = subprocess.run(["git", "grep", "-l", "-e", "NativeChromePainting", "-e", "NativeControlStyles", "--", "*/src/main/*.java"],
                       capture_output=True, text=True, check=True).stdout.split()
total = 0
for name in files:
    path = pathlib.Path(name)
    lines = path.read_text(encoding="utf-8").split("\n")
    for index, line in enumerate(lines):
        if name in UI:
            line, count = re.subn(r'(?<![\w.])NativeChromePainting\.measureText\(', 'ThemePainting.measureText(', line); total += count
        else:
            for pattern, replacement in RULES:
                line, count = re.subn(pattern, replacement, line); total += count
        lines[index] = line
    path.write_text("\n".join(lines), encoding="utf-8")
if len(files) != 36 or total != 153: raise SystemExit(f"expected 36 files and 153 replacements, got {len(files)} and {total}")
PY
git grep -n -e NativeChromePainting -e NativeControlStyles -- '*.java' '*.kts'
git grep -n 'DesignMetrics.DEFAULT' -- jasper-ui/src/main/java/dev/jasper/ui/swing
```

Expected: both `git grep`s print nothing. (`git mv` in Step 5 already moved the two class files, so the script's
`git grep -l` lists only callers. A trial of this script on `0af7b83e2` changed exactly the 140 lines that name either
class.)

- [ ] **Step 7: Move the two ledger rows with their files**

In `docs/design/theme-engine/migration.csv`, replace line 766:

```
jasper-ui/src/main/java/dev/jasper/ui/swing/NativeChromePainting.java,ui.swing,NativeChromePainting,standard-ui,5,10,none-direct,New in Plan 2 Task 5; module-owned native styling entrypoint or migration behavioral evidence. Native layout adapters have Plan 3 Task 10 composition ownership.
```

with:

```
jasper-ui/src/main/java/dev/jasper/ui/swing/ChromePainter.java,ui.swing,ChromePainter,standard-ui,5,10,none-direct,New in Plan 2 Task 5; module-owned native styling entrypoint or migration behavioral evidence. Native layout adapters have Plan 3 Task 10 composition ownership. Renamed from NativeChromePainting in Enterprise presentation Plan 1 (one instance per kit).
```

and line 767:

```
jasper-ui/src/main/java/dev/jasper/ui/swing/NativeControlStyles.java,ui.swing,NativeControlStyles,standard-ui,5,10,none-direct,"New module-owned native adapter styles: ghost toolbar button (optional named arc), framed (kit secondary) button, borderless field, label, textArea, menu, menuTree; role colors and typography only, preserving the host's existing arc, margins, focus widths and button kind; local UI refresh. Native consumers compose in Plan 3 Task 10."
```

with:

```
jasper-ui/src/main/java/dev/jasper/ui/swing/NativeStyles.java,ui.swing,NativeStyles,standard-ui,5,10,none-direct,"New module-owned native adapter styles: ghost toolbar button (optional named arc), framed (kit secondary) button, borderless field, label, textArea, menu, menuTree; role colors and typography only, preserving the host's existing arc, margins, focus widths and button kind; local UI refresh. Native consumers compose in Plan 3 Task 10. Renamed from NativeControlStyles in Enterprise presentation Plan 1 (one instance per kit)."
```

(`ThemeMigrationCoverageTest` requires every row's path to exist; rows 252, 257 and 276 name `NativeControlStyles` in
history text only, which no test reads.)

- [ ] **Step 8: Run the tests to verify they pass**

Run: `./gradlew :jasper-ui:test --tests 'dev.jasper.ui.swing.ChromePainterTest' --tests 'dev.jasper.ui.swing.NativeStylesTest' --tests 'dev.jasper.ui.swing.ChromeRolesTest' --tests 'dev.jasper.ui.swing.TablesTest'`
Expected: PASS (`ChromePainterTest` gains one test, `NativeStylesTest` two).

Run: `./gradlew :jasper-app:test --tests 'dev.jasper.app.workspace.WorkspaceMetricsTest' --tests 'dev.jasper.app.appearance.ShippedControlReadabilityTest' --tests 'dev.jasper.app.appearance.UiTypographyTest' --tests 'dev.jasper.app.palette.CommandPaletteTest' --tests 'dev.jasper.app.workspace.WorkspaceRolesTest' --tests 'dev.jasper.app.appearance.ThemeMigrationCoverageTest'`
Expected: PASS.

- [ ] **Step 9: Run the affected suites**

Run: `./gradlew :jasper-ui:check verifyUiArchitecture :jasper-app:test :jasper-app:javadoc`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 10: Commit**

```bash
git add -A jasper-ui/src/main/java/dev/jasper/ui/swing jasper-ui/src/test/java jasper-app/src/main/java jasper-app/src/test/java \
  docs/design/theme-engine/migration.csv
git commit -F - <<'EOF'
Kit seam: ChromePainter and NativeStyles paint for one kit

NativeChromePainting and NativeControlStyles become the instance classes
ChromePainter(kit) and NativeStyles(kit). App chrome paints and styles
with installed(), one instance for the installed kit; jasper-ui measures
with ThemePainting.measureText; tests build a painter for the kit they
check. The default kit's painter keeps every ported expectation.

🤖 Generated with Claude Code at The Home Depot

Co-Authored-By: Claude <noreply@anthropic.com>
EOF
```

### Task 10: StaticDefaultTest; the app's static metric finals read the installed kit

A bytecode scan keeps the seam closed (spec 16.1.7). It comes first: after Tasks 1-9 it fails on exactly the reads this
task converts, and nothing else. The static finals that fixed `DesignMetrics.DEFAULT` values at class load (spec 6.6:
`ToolWindowArea.java:53,55`, `ToolWindowHeader.java:31`, `DockDrag.java:54`, `WindowHeader.java:24`, and
`TitleSearchField.java:32-35` through its helper) become live reads of the installed kit, and so do the method reads
(`MacTitleBar.java:217`, `TitleSearchField.java:36`, `WindowToolbar.java:48`, `ToolWindowHeader.java:78`,
`AppIcons.java:43,50`). A read never runs at class load, so it never touches UIManager before the start-up install.
`AnimationSettings.java:16-18` stays: its durations feed `ConfigSnapshot`, which is built before any window.

**Files:**
- Create: `jasper-app/src/test/java/dev/jasper/app/architecture/StaticDefaultTest.java`
- Modify: `jasper-app/build.gradle.kts:93-96`, `docs/design/theme-engine/migration.csv` (one row at the end)
- Modify (all under `jasper-app/src/main/java/dev/jasper/app/`): `platform/AppIcons.java:41-51`, `platform/MacTitleBar.java:216-218`, `workspace/DockDrag.java:53-54,176,309`, `workspace/TitleSearchField.java:32-36,81-83`, `workspace/WindowHeader.java:24,74,79,82`, `workspace/ToolWindowArea.java:53-55,75,97,230,520-521`, `workspace/ToolWindowHeader.java:30-31,78,154,156`, `workspace/WindowToolbar.java:48`
- Test: `jasper-app/src/test/java/dev/jasper/app/workspace/WorkspaceMetricsTest.java` (one new test before the last line)
- Modify tests: `jasper-app/src/test/java/dev/jasper/app/workspace/DockDragTest.java:181`, `ToolWindowAreaTest.java:72`, `ToolWindowHeaderTest.java:130`

**Interfaces:**
- Consumes: `FlatLafInstaller.installedKit()`, `install(theme, kit)` (Task 6); `NativeStyles.installed()` (Task 9); the
  jars of `:jasper-app` and `:jasper-ui`.
- Produces: `StaticDefaultTest` (a `java.lang.classfile` scan); the test system property `jasper.uiJar` in
  `:jasper-app:test`. Package-private in `dev.jasper.app.workspace`: `static int DockDrag.threshold()`,
  `static int TitleSearchField.preferredWidth()`, `static int TitleSearchField.minimumWidth()`,
  `static int ToolWindowArea.surfaceArc()`, `static int ToolWindowHeader.logicalHeight()` (all logical pixels of the
  installed kit); `DockDrag.THRESHOLD`, `TitleSearchField.PREFERRED_WIDTH`/`MINIMUM_WIDTH`/`MAXIMUM_WIDTH`/`ARC`,
  `ToolWindowArea.GUTTER`/`SURFACE_ARC`, `ToolWindowHeader.HEIGHT` and `WindowHeader.GAP` are removed.

- [ ] **Step 1: Write the bytecode scan**

Create `jasper-app/src/test/java/dev/jasper/app/architecture/StaticDefaultTest.java`:

```java
package dev.jasper.app.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import dev.jasper.ui.kit.Kit;
import dev.jasper.ui.metrics.DesignMetrics;
import dev.jasper.ui.metrics.TypeScale;
import dev.jasper.ui.swing.FlatLafInstaller;
import dev.jasper.ui.swing.NativeStyles;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.lang.classfile.ClassFile;
import java.lang.classfile.ClassModel;
import java.lang.classfile.MethodModel;
import java.lang.classfile.Opcode;
import java.lang.classfile.instruction.FieldInstruction;
import java.lang.classfile.instruction.InvokeInstruction;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.IntSupplier;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

/**
 * Enterprise presentation spec 6.6 and 16.1.7: geometry and type follow a kit. Outside the catalog's own packages
 * (dev.jasper.ui.metrics and dev.jasper.ui.kit) only AnimationSettings reads DesignMetrics.DEFAULT (ConfigSnapshot is
 * built before any window, so the tab durations come from the default catalog), nothing reads TypeScale.DEFAULT or
 * FaceSet.PLATFORM, only the classes that choose the start-up kit or fall back to it read Kit.DEFAULT, and no static
 * initializer reads a metric or builds a painter, itself or through the methods it calls.
 */
class StaticDefaultTest {
    static final String METRICS = "dev/jasper/ui/metrics/DesignMetrics", ROLE = "Ldev/jasper/ui/metrics/DesignMetrics$Role;";
    static final String ANIMATION = "dev/jasper/app/config/AnimationSettings", DEFAULT_KIT = "dev/jasper/ui/kit/Kit.DEFAULT";
    static final String PROBE = "dev/jasper/app/architecture/StaticDefaultTest$";
    /** How many calls deep a static initializer's calls are followed through the scanned classes. */
    static final int DEPTH = 4;
    /** Default catalogs that only their owners may read. */
    static final Set<String> CATALOGS = Set.of(METRICS + ".DEFAULT", "dev/jasper/ui/metrics/TypeScale.DEFAULT",
        "dev/jasper/ui/metrics/FaceSet.PLATFORM");
    /** Calls that read a metric; a static initializer makes none. */
    static final Set<String> METRIC_READS = Set.of(METRICS + ".value", METRICS + ".contentHeight", METRICS + ".contentPadding",
        METRICS + ".with", "dev/jasper/ui/kit/Kit.metrics", "dev/jasper/ui/kit/Kit.type", "dev/jasper/ui/swing/KitContext.metrics",
        "dev/jasper/ui/swing/KitContext.kit", "dev/jasper/ui/swing/FlatLafInstaller.installedKit");
    /** Painters a static initializer must not build or call: their kit would be fixed at class load. */
    static final Set<String> PAINTERS = Set.of("dev/jasper/ui/swing/NativeStyles", "dev/jasper/ui/swing/ChromePainter");
    /** The classes that read Kit.DEFAULT: the installed-kit fallback, the controllers made without a kit, start-up. */
    static final Set<String> DEFAULT_KIT_READERS = Set.of("dev/jasper/ui/swing/FlatLafInstaller",
        "dev/jasper/app/appearance/ThemeController", "dev/jasper/app/application/JasperApplication");

    static boolean owner(String type) { return type.startsWith("dev/jasper/ui/metrics/") || type.startsWith("dev/jasper/ui/kit/"); }

    /** The scanned classes, every method body among them by "type.name descriptor", and each class's superclass. */
    record Classes(List<ClassModel> models, Map<String, MethodModel> bodies, Map<String, String> supers) {
        static Classes of(List<byte[]> files) {
            var models = new ArrayList<ClassModel>();
            var bodies = new HashMap<String, MethodModel>();
            var supers = new HashMap<String, String>();
            for (var bytes : files) {
                var model = ClassFile.of().parse(bytes);
                String type = model.thisClass().asInternalName();
                models.add(model);
                model.superclass().ifPresent(parent -> supers.put(type, parent.asInternalName()));
                for (var method : model.methods())
                    bodies.put(type + "." + method.methodName().stringValue() + method.methodType().stringValue(), method);
            }
            return new Classes(models, bodies, supers);
        }

        /** The body {@code call} runs when it lies in these classes outside the catalog's owners; null otherwise. */
        MethodModel body(InvokeInstruction call) {
            String member = call.name().stringValue() + call.type().stringValue();
            boolean constructor = call.name().stringValue().equals("<init>");
            for (String type = call.owner().asInternalName(); type != null && !owner(type); type = constructor ? null : supers.get(type)) {
                var body = bodies.get(type + "." + member);
                if (body != null) return body;
            }
            return null;
        }
    }

    /** Every read the rules name in {@code classes}, as "type.method: what" (an initializer's call as "type.<clinit>: calls owner.member"). */
    static List<String> reads(Classes classes) {
        var found = new ArrayList<String>();
        for (var model : classes.models()) {
            String type = model.thisClass().asInternalName();
            if (owner(type)) continue;
            for (var method : model.methods()) {
                String name = method.methodName().stringValue();
                boolean initializer = name.equals("<clinit>");
                method.code().ifPresent(code -> {
                    for (var element : code) {
                        if (element instanceof FieldInstruction field && field.opcode() == Opcode.GETSTATIC) {
                            String read = field.owner().asInternalName() + "." + field.name().stringValue();
                            if (CATALOGS.contains(read) || read.equals(DEFAULT_KIT)) found.add(type + "." + name + ": " + read);
                        }
                        if (initializer && element instanceof InvokeInstruction call) {
                            String callee = call.owner().asInternalName() + "." + call.name().stringValue();
                            String descriptor = call.type().stringValue(), parameters = descriptor.substring(1, descriptor.indexOf(')'));
                            if (metricRead(call) || (!call.name().stringValue().equals("<init>") && parameters.contains(ROLE))) {
                                found.add(type + ".<clinit>: calls " + callee);
                            } else {
                                String reached = reaches(classes, call, 1, new HashSet<>());
                                if (reached != null) found.add(type + ".<clinit>: calls " + callee + ", which reaches " + reached);
                            }
                        }
                    }
                });
            }
        }
        return found;
    }

    /** A call that reads a metric or uses a painter. */
    static boolean metricRead(InvokeInstruction call) {
        String owner = call.owner().asInternalName();
        return METRIC_READS.contains(owner + "." + call.name().stringValue()) || PAINTERS.contains(owner);
    }

    /**
     * The first default-catalog read, metric read or painter use that {@code call} leads to, following calls through the
     * scanned classes up to {@link #DEPTH} deep, as "owner.member"; null for none. Calls into the catalog's owners, the JDK
     * and libraries are not followed, nor lambda bodies, which an initializer only creates.
     */
    static String reaches(Classes classes, InvokeInstruction call, int depth, Set<String> visited) {
        var body = classes.body(call);
        if (body == null || depth > DEPTH
                || !visited.add(call.owner().asInternalName() + "." + call.name().stringValue() + call.type().stringValue())) return null;
        var code = body.code().orElse(null);
        if (code == null) return null;
        for (var element : code) {
            if (element instanceof FieldInstruction field && field.opcode() == Opcode.GETSTATIC) {
                String read = field.owner().asInternalName() + "." + field.name().stringValue();
                if (CATALOGS.contains(read)) return read;
            }
            if (element instanceof InvokeInstruction next) {
                if (metricRead(next)) return next.owner().asInternalName() + "." + next.name().stringValue();
                String reached = reaches(classes, next, depth + 1, visited);
                if (reached != null) return reached;
            }
        }
        return null;
    }

    /** The class files in {@code jar}, which must hold at least {@code minimum}. */
    static List<byte[]> classFiles(Path jar, int minimum) throws IOException {
        var files = new ArrayList<byte[]>();
        try (var system = FileSystems.newFileSystem(jar); var walk = Files.walk(system.getPath("/"))) {
            for (var path : walk.filter(entry -> entry.toString().endsWith(".class")).toList()) files.add(Files.readAllBytes(path));
        }
        assertThat(files.size()).as("classes read from %s", jar).isGreaterThanOrEqualTo(minimum);
        return files;
    }

    @Test void onlyTheCatalogsOwnersAndAnimationSettingsReadTheDefaultCatalogs() throws IOException {
        // One set of classes, so an app initializer's call into jasper-ui is followed too.
        var files = new ArrayList<byte[]>(classFiles(Path.of(System.getProperty("jasper.appJar")), 400));
        files.addAll(classFiles(Path.of(System.getProperty("jasper.uiJar")), 100));
        var found = reads(Classes.of(files));
        assertThat(found.stream().filter(read -> read.equals(ANIMATION + ".<clinit>: " + METRICS + ".DEFAULT")))
            .as("the one allowed read, so the scan is live").hasSize(3);
        assertThat(found.stream().filter(read -> !read.startsWith(ANIMATION + ".<clinit>: ") && !read.endsWith(": " + DEFAULT_KIT)))
            .as("default-catalog reads and static metric finals outside the catalog").isEmpty();
        // A typed local: assertThat(stream.collect(toCollection(TreeSet::new))) is ambiguous among AssertJ's overloads.
        Set<String> readers = found.stream().filter(read -> read.endsWith(": " + DEFAULT_KIT))
            .map(read -> read.substring(0, read.indexOf('.'))).collect(Collectors.toCollection(TreeSet::new));
        assertThat(readers).as("the classes that read Kit.DEFAULT").containsExactlyInAnyOrderElementsOf(DEFAULT_KIT_READERS);
    }

    // Probes compiled with this test: what the rules report and what they leave alone. They are never initialized.
    static final class DefaultInAMethod { int gap() { return DesignMetrics.DEFAULT.value(DesignMetrics.Role.HAIRLINE); } }
    static final class TypeDefaultInAMethod { Object scale() { return TypeScale.DEFAULT; } }
    static final class FinalFromTheInstalledKit {
        static final int GAP = FlatLafInstaller.installedKit().metrics().value(DesignMetrics.Role.HAIRLINE);
    }
    static final class FinalThroughAHelper {
        static final int GAP = metric(DesignMetrics.Role.HAIRLINE);
        private static int metric(DesignMetrics.Role role) { return role.ordinal(); }
    }
    static final class FinalThroughAPlainHelper {
        static final int GAP = gap();
        static int gap() { return FlatLafInstaller.installedKit().metrics().value(DesignMetrics.Role.HAIRLINE); }
    }
    static final class FinalThroughAConstructor {
        static final FinalThroughAConstructor INSTANCE = new FinalThroughAConstructor();
        final int gap = NativeStyles.installed().metric(DesignMetrics.Role.HAIRLINE);
    }
    static final class StylesAtClassLoad { static final NativeStyles STYLES = new NativeStyles(Kit.DEFAULT); }
    static final class LiveRead { static int gap() { return FlatLafInstaller.installedKit().metrics().value(DesignMetrics.Role.HAIRLINE); } }
    static final class LazySupplier {
        static final IntSupplier GAP = () -> FlatLafInstaller.installedKit().metrics().value(DesignMetrics.Role.HAIRLINE);
    }

    @Test void theRulesReportDefaultReadsStaticMetricFinalsAndStaticPainters() {
        assertThat(probe(DefaultInAMethod.class)).containsExactly(PROBE + "DefaultInAMethod.gap: " + METRICS + ".DEFAULT");
        assertThat(probe(TypeDefaultInAMethod.class)).containsExactly(PROBE + "TypeDefaultInAMethod.scale: dev/jasper/ui/metrics/TypeScale.DEFAULT");
        assertThat(probe(FinalFromTheInstalledKit.class))
            .contains(PROBE + "FinalFromTheInstalledKit.<clinit>: calls dev/jasper/ui/swing/FlatLafInstaller.installedKit");
        assertThat(probe(FinalThroughAHelper.class))
            .containsExactly(PROBE + "FinalThroughAHelper.<clinit>: calls " + PROBE + "FinalThroughAHelper.metric");
        assertThat(probe(FinalThroughAPlainHelper.class)).as("a helper without a role parameter is followed")
            .containsExactly(PROBE + "FinalThroughAPlainHelper.<clinit>: calls " + PROBE
                + "FinalThroughAPlainHelper.gap, which reaches dev/jasper/ui/swing/FlatLafInstaller.installedKit");
        assertThat(probe(FinalThroughAConstructor.class)).as("so is a constructor")
            .containsExactly(PROBE + "FinalThroughAConstructor.<clinit>: calls " + PROBE
                + "FinalThroughAConstructor.<init>, which reaches dev/jasper/ui/swing/NativeStyles.installed");
        assertThat(probe(StylesAtClassLoad.class)).containsExactly(PROBE + "StylesAtClassLoad.<clinit>: " + DEFAULT_KIT,
            PROBE + "StylesAtClassLoad.<clinit>: calls dev/jasper/ui/swing/NativeStyles.<init>");
        assertThat(probe(LiveRead.class)).as("a read when used is the point").isEmpty();
        assertThat(probe(LazySupplier.class)).as("a lambda body is not the initializer").isEmpty();
    }

    static List<String> probe(Class<?> type) {
        String file = type.getName().substring(type.getName().lastIndexOf('.') + 1) + ".class";
        try (InputStream in = type.getResourceAsStream(file)) { return reads(Classes.of(List.of(in.readAllBytes()))); }
        catch (IOException failure) { throw new UncheckedIOException(failure); }
    }
}
```

In `jasper-app/build.gradle.kts`, replace line 93:

```kotlin
    dependsOn(tasks.jar, ":jasper-buddy:jar", ":jasper-terminal:jar")
```

with:

```kotlin
    dependsOn(tasks.jar, ":jasper-buddy:jar", ":jasper-terminal:jar", ":jasper-ui:jar")
```

and after line 96 (`systemProperty("jasper.terminalJar", …)`) add:

```kotlin
    systemProperty("jasper.uiJar", project(":jasper-ui").layout.buildDirectory.file("libs/jasper-ui.jar").get().asFile.absolutePath)
```

Append to `docs/design/theme-engine/migration.csv` (the file imports `dev.jasper.ui`, so `ThemeMigrationCoverageTest`
counts it as a candidate):

```
jasper-app/src/test/java/dev/jasper/app/architecture/StaticDefaultTest.java,app.architecture,StaticDefaultTest,nonvisual-helper,none,none,jasper-app/src/test/java/dev/jasper/app/architecture/StaticDefaultTest.java,New in Enterprise presentation Plan 1; geometry follows a kit: no default-catalog read or static metric final outside the catalog and AnimationSettings
```

- [ ] **Step 2: Run the scan to verify it fails**

Run: `./gradlew :jasper-app:test --tests 'dev.jasper.app.architecture.StaticDefaultTest' --tests 'dev.jasper.app.appearance.ThemeMigrationCoverageTest'`
Expected: FAIL. `theRulesReportDefaultReadsStaticMetricFinalsAndStaticPainters` and the coverage tests pass;
`onlyTheCatalogsOwnersAndAnimationSettingsReadTheDefaultCatalogs` fails on `[default-catalog reads and static metric
finals outside the catalog]` with exactly the 21 reads Step 5 converts (the message lists them in jar order):
`dev/jasper/app/workspace/DockDrag.<clinit>: dev/jasper/ui/metrics/DesignMetrics.DEFAULT` and
`dev/jasper/app/workspace/DockDrag.<clinit>: calls dev/jasper/ui/metrics/DesignMetrics.value`, the same pair for
`WindowHeader.<clinit>` and `ToolWindowHeader.<clinit>` and twice for `ToolWindowArea.<clinit>`,
`dev/jasper/app/workspace/TitleSearchField.<clinit>: calls dev/jasper/app/workspace/TitleSearchField.metric` four times,
and a `: dev/jasper/ui/metrics/DesignMetrics.DEFAULT` read in `dev/jasper/app/platform/AppIcons.lambda$named$0`,
`AppIcons.lambda$semantic$0`, `MacTitleBar.metric`, `TitleSearchField.metric`, `ToolWindowHeader.<init>` (twice) and
`WindowToolbar.buttonSide`. Anything else it lists is a read an earlier task missed: convert it in this task and record
the deviation in the plan's status banner.

- [ ] **Step 3: Write the failing seam test**

In `jasper-app/src/test/java/dev/jasper/app/workspace/WorkspaceMetricsTest.java`, insert before the last line (the
class's closing `}`):

```java

    /** Enterprise presentation plan 1: geometry that static finals fixed at class load follows the installed kit. */
    @Test void formerStaticMetricsFollowTheInstalledKit() throws Exception {
        javax.swing.SwingUtilities.invokeAndWait(() -> {
            var base = dev.jasper.ui.kit.Kit.DEFAULT;
            var kit = new dev.jasper.ui.kit.Kit("statics", base.metrics().with(DesignMetrics.Role.NATIVE_TOOL_HEADER_HEIGHT, 40)
                .with(DesignMetrics.Role.DOCK_DRAG_THRESHOLD, 9).with(DesignMetrics.Role.WORKSPACE_SURFACE_ARC, 0)
                .with(DesignMetrics.Role.TITLE_SEARCH_WIDTH, 300).with(DesignMetrics.Role.TOOLBAR_BUTTON_TRIM, 10),
                base.type(), base.faces(), base.uiKeys());
            var original = javax.swing.UIManager.getLookAndFeel();
            try {
                dev.jasper.ui.swing.FlatLafInstaller.install(dev.jasper.app.appearance.ThemeTestSupport.generated(true), kit);
                assertThat(ToolWindowHeader.logicalHeight()).isEqualTo(40);
                assertThat(new ToolWindowHeader("Sessions", javax.swing.JPopupMenu::new, () -> { }).getPreferredSize().height)
                    .isEqualTo(com.formdev.flatlaf.util.UIScale.scale(40));
                assertThat(DockDrag.threshold()).isEqualTo(9);
                assertThat(ToolWindowArea.surfaceArc()).isZero();
                assertThat(TitleSearchField.preferredWidth()).isEqualTo(300);
                assertThat(WindowToolbar.buttonSide(34)).isEqualTo(com.formdev.flatlaf.util.UIScale.scale(24));
                dev.jasper.ui.swing.FlatLafInstaller.install(dev.jasper.app.appearance.ThemeTestSupport.generated(true), base);
                assertThat(ToolWindowHeader.logicalHeight()).isEqualTo(32);
                assertThat(DockDrag.threshold()).isEqualTo(5);
                assertThat(ToolWindowArea.surfaceArc()).isEqualTo(16);
                assertThat(TitleSearchField.preferredWidth()).isEqualTo(400);
                assertThat(WindowToolbar.buttonSide(34)).isEqualTo(com.formdev.flatlaf.util.UIScale.scale(28));
            } finally {
                try { javax.swing.UIManager.setLookAndFeel(original); }
                catch (javax.swing.UnsupportedLookAndFeelException failure) { throw new AssertionError(failure); }
            }
        });
    }
```

(Defaults at `0af7b83e2`: `NATIVE_TOOL_HEADER_HEIGHT` 32, `DOCK_DRAG_THRESHOLD` 5, `WORKSPACE_SURFACE_ARC` 16,
`TITLE_SEARCH_WIDTH` 400, `TOOLBAR_BUTTON_TRIM` 6.)

- [ ] **Step 4: Run the test to verify it fails**

Run: `./gradlew :jasper-app:test --tests 'dev.jasper.app.workspace.WorkspaceMetricsTest'`
Expected: FAIL in `:jasper-app:compileTestJava` with `error: cannot find symbol` for `logicalHeight()`, `threshold()`,
`surfaceArc()` and `preferredWidth()`.

- [ ] **Step 5: Replace each static final with a read of the installed kit**

`platform/AppIcons.java`, replace lines 41-51:

```java
    public static Icon named(String name) {
        return CACHE.computeIfAbsent("named:" + name, ignored -> new SemanticIcon(NamedIcons.kitName(name),
            DesignMetrics.DEFAULT.value(DesignMetrics.Role.ICON_RAIL), false));
    }

    /** A module glyph for an app-native affordance outside the SDK named-icon vocabulary. */
    public static Icon semantic(String name) {
        if (!NAMED.names().contains(name)) throw new IllegalArgumentException("Unknown module icon: " + name);
        return CACHE.computeIfAbsent("semantic:" + name, ignored -> new SemanticIcon(name,
            DesignMetrics.DEFAULT.value(DesignMetrics.Role.ICON_RAIL), false));
    }
```

with:

```java
    public static Icon named(String name) {
        return CACHE.computeIfAbsent("named:" + name, ignored -> new SemanticIcon(NamedIcons.kitName(name), railSize(), false));
    }

    /** A module glyph for an app-native affordance outside the SDK named-icon vocabulary. */
    public static Icon semantic(String name) {
        if (!NAMED.names().contains(name)) throw new IllegalArgumentException("Unknown module icon: " + name);
        return CACHE.computeIfAbsent("semantic:" + name, ignored -> new SemanticIcon(name, railSize(), false));
    }

    /** The installed kit's rail glyph size; a cached icon keeps it, since the kit is fixed before any window. */
    private static int railSize() {
        return dev.jasper.ui.swing.FlatLafInstaller.installedKit().metrics().value(DesignMetrics.Role.ICON_RAIL);
    }
```

`platform/MacTitleBar.java`, replace lines 216-218:

```java
    private static int metric(dev.jasper.ui.metrics.DesignMetrics.Role role) {
        return UIScale.scale(dev.jasper.ui.metrics.DesignMetrics.DEFAULT.value(role));
    }
```

with (the same scaled value, from the installed kit):

```java
    private static int metric(dev.jasper.ui.metrics.DesignMetrics.Role role) {
        return dev.jasper.ui.swing.NativeStyles.installed().metric(role);
    }
```

`workspace/DockDrag.java`, replace lines 53-54:

```java
    /** How far, in logical pixels, a press must move before it becomes a drag. */
    static final int THRESHOLD = DesignMetrics.DEFAULT.value(DesignMetrics.Role.DOCK_DRAG_THRESHOLD);
```

with:

```java
    /** How far, in logical pixels, a press must move before it becomes a drag: the installed kit's threshold. */
    static int threshold() {
        return dev.jasper.ui.swing.FlatLafInstaller.installedKit().metrics().value(DesignMetrics.Role.DOCK_DRAG_THRESHOLD);
    }
```

and in line 176 and line 309 replace `UIScale.scale(THRESHOLD)` with `UIScale.scale(threshold())`.

`workspace/TitleSearchField.java`, replace lines 32-36:

```java
    static final int PREFERRED_WIDTH = metric(DesignMetrics.Role.TITLE_SEARCH_WIDTH),
        MINIMUM_WIDTH = metric(DesignMetrics.Role.TITLE_SEARCH_MIN_WIDTH),
        MAXIMUM_WIDTH = metric(DesignMetrics.Role.TITLE_SEARCH_MAX_WIDTH),
        ARC = metric(DesignMetrics.Role.TITLE_SEARCH_ARC);
    private static int metric(DesignMetrics.Role role) { return DesignMetrics.DEFAULT.value(role); }
```

with (`ARC` was never read; the painter reads `TITLE_SEARCH_ARC` itself):

```java
    /** {@code role}'s logical value in the installed kit. */
    private static int metric(DesignMetrics.Role role) { return dev.jasper.ui.swing.FlatLafInstaller.installedKit().metrics().value(role); }
    /** The field's preferred width, in logical pixels. */
    static int preferredWidth() { return metric(DesignMetrics.Role.TITLE_SEARCH_WIDTH); }
    /** The width below which the field collapses to its magnifier, in logical pixels. */
    static int minimumWidth() { return metric(DesignMetrics.Role.TITLE_SEARCH_MIN_WIDTH); }
```

and lines 81-83:

```java
    @Override public Dimension getPreferredSize() { return new Dimension(UIScale.scale(PREFERRED_WIDTH), UIScale.scale(metric(DesignMetrics.Role.TITLE_SEARCH_HEIGHT))); }
    @Override public Dimension getMinimumSize() { return new Dimension(UIScale.scale(MINIMUM_WIDTH), UIScale.scale(metric(DesignMetrics.Role.TITLE_SEARCH_HEIGHT))); }
    @Override public Dimension getMaximumSize() { return new Dimension(UIScale.scale(MAXIMUM_WIDTH), UIScale.scale(metric(DesignMetrics.Role.TITLE_SEARCH_HEIGHT))); }
```

with:

```java
    @Override public Dimension getPreferredSize() { return new Dimension(UIScale.scale(preferredWidth()), UIScale.scale(metric(DesignMetrics.Role.TITLE_SEARCH_HEIGHT))); }
    @Override public Dimension getMinimumSize() { return new Dimension(UIScale.scale(minimumWidth()), UIScale.scale(metric(DesignMetrics.Role.TITLE_SEARCH_HEIGHT))); }
    @Override public Dimension getMaximumSize() { return new Dimension(UIScale.scale(metric(DesignMetrics.Role.TITLE_SEARCH_MAX_WIDTH)), UIScale.scale(metric(DesignMetrics.Role.TITLE_SEARCH_HEIGHT))); }
```

`workspace/WindowHeader.java`, replace line 24:

```java
    private static final int GAP = DesignMetrics.DEFAULT.value(DesignMetrics.Role.NATIVE_HEADER_GAP);
```

with:

```java
    /** The installed kit's gap between the field and each side, in logical pixels. */
    private static int headerGap() { return dev.jasper.ui.swing.FlatLafInstaller.installedKit().metrics().value(DesignMetrics.Role.NATIVE_HEADER_GAP); }
```

in lines 74 and 79 replace `UIScale.scale(GAP)` with `UIScale.scale(headerGap())`, and in line 82 replace
`UIScale.scale(TitleSearchField.PREFERRED_WIDTH), minimum = UIScale.scale(TitleSearchField.MINIMUM_WIDTH)` with
`UIScale.scale(TitleSearchField.preferredWidth()), minimum = UIScale.scale(TitleSearchField.minimumWidth())`.

`workspace/ToolWindowArea.java`, replace lines 53-55:

```java
    private static final int GUTTER = DesignMetrics.DEFAULT.value(DesignMetrics.Role.WORKSPACE_GUTTER);
    /** Shared with the folder tabs so their upper corners meet the workspace frame exactly. */
    static final int SURFACE_ARC = DesignMetrics.DEFAULT.value(DesignMetrics.Role.WORKSPACE_SURFACE_ARC);
```

with:

```java
    /** The installed kit's exterior gutter, in logical pixels. */
    private static int gutter() { return dev.jasper.ui.swing.FlatLafInstaller.installedKit().metrics().value(DesignMetrics.Role.WORKSPACE_GUTTER); }
    /** The installed kit's workspace frame arc, in logical pixels. */
    static int surfaceArc() { return dev.jasper.ui.swing.FlatLafInstaller.installedKit().metrics().value(DesignMetrics.Role.WORKSPACE_SURFACE_ARC); }
```

replace line 75 (in `Host(boolean toolWindow)`):

```java
            gutters(GUTTER, GUTTER, GUTTER, GUTTER);
```

with:

```java
            int gutter = gutter();
            gutters(gutter, gutter, gutter, gutter);
```

in line 97 replace `UIScale.scale(SURFACE_ARC)` with `UIScale.scale(surfaceArc())`, in line 230 replace
`UIScale.scale(SURFACE_ARC / 2)` with `UIScale.scale(surfaceArc() / 2)`, and replace lines 520-521 (in
`refreshGutters()`):

```java
        int top = headerVisible ? 0 : GUTTER, bottom = statusVisible ? 0 : GUTTER;
        int left = leftStripeVisible ? 0 : GUTTER, right = rightStripeVisible ? 0 : GUTTER;
```

with:

```java
        int gutter = gutter();
        int top = headerVisible ? 0 : gutter, bottom = statusVisible ? 0 : gutter;
        int left = leftStripeVisible ? 0 : gutter, right = rightStripeVisible ? 0 : gutter;
```

`workspace/ToolWindowHeader.java`, replace lines 30-31:

```java
    /** Height in logical pixels. */
    static final int HEIGHT = DesignMetrics.DEFAULT.value(DesignMetrics.Role.NATIVE_TOOL_HEADER_HEIGHT);
```

with:

```java
    /** The installed kit's header height, in logical pixels. */
    static int logicalHeight() {
        return dev.jasper.ui.swing.FlatLafInstaller.installedKit().metrics().value(DesignMetrics.Role.NATIVE_TOOL_HEADER_HEIGHT);
    }
```

replace line 78:

```java
        setBorder(BorderFactory.createCompoundBorder(new FlatEmptyBorder(0, DesignMetrics.DEFAULT.value(DesignMetrics.Role.TOOL_HEADER_LEADING_PADDING), 0, DesignMetrics.DEFAULT.value(DesignMetrics.Role.TOOL_HEADER_TRAILING_PADDING)), null));
```

with:

```java
        var metrics = dev.jasper.ui.swing.FlatLafInstaller.installedKit().metrics();
        setBorder(BorderFactory.createCompoundBorder(new FlatEmptyBorder(0, metrics.value(DesignMetrics.Role.TOOL_HEADER_LEADING_PADDING), 0, metrics.value(DesignMetrics.Role.TOOL_HEADER_TRAILING_PADDING)), null));
```

and in lines 154 and 156 replace `UIScale.scale(HEIGHT)` with `UIScale.scale(logicalHeight())`.
(`WorkspaceMetricsTest.nativeAdaptersDoNotBorrowGenericKitRoles` still finds `TOOL_HEADER_LEADING_PADDING`,
`TOOL_HEADER_TRAILING_PADDING`, `TOOL_HEADER_DETAIL_GAP` and `TOOL_HEADER_ACTIVE_DOT` in this file.)

`workspace/WindowToolbar.java`, replace line 48:

```java
    static int buttonSide(int height) { return UIScale.scale(height - DesignMetrics.DEFAULT.value(DesignMetrics.Role.TOOLBAR_BUTTON_TRIM)); }
```

with:

```java
    static int buttonSide(int height) {
        return UIScale.scale(height - dev.jasper.ui.swing.FlatLafInstaller.installedKit().metrics().value(DesignMetrics.Role.TOOLBAR_BUTTON_TRIM));
    }
```

- [ ] **Step 6: Point the tests at the new reads**

`DockDragTest.java:181`: replace `UIScale.scale(DockDrag.THRESHOLD)` with `UIScale.scale(DockDrag.threshold())`.
`ToolWindowAreaTest.java:72` and `ToolWindowHeaderTest.java:130`: replace `ToolWindowHeader.HEIGHT` with
`ToolWindowHeader.logicalHeight()`.

Then check that no production read of the default catalog is left outside the catalog and `AnimationSettings`:

```bash
git grep -nF 'DesignMetrics.DEFAULT' -- '*/src/main/*.java' 'plugins/*/src/main/*.java'
git grep -nwE 'THRESHOLD|SURFACE_ARC|GUTTER|PREFERRED_WIDTH|MINIMUM_WIDTH|MAXIMUM_WIDTH|GAP' -- \
  jasper-app/src/main/java/dev/jasper/app/workspace/DockDrag.java jasper-app/src/main/java/dev/jasper/app/workspace/ToolWindowArea.java \
  jasper-app/src/main/java/dev/jasper/app/workspace/TitleSearchField.java jasper-app/src/main/java/dev/jasper/app/workspace/WindowHeader.java \
  jasper-app/src/test/java/dev/jasper/app/workspace/DockDragTest.java
git grep -nF 'ToolWindowHeader.HEIGHT' -- '*.java'
```

Expected: the first prints only the three lines of `AnimationSettings.java:16-18` and the `Kit.java` line that builds
`Kit.DEFAULT`; the second and third print nothing.

- [ ] **Step 7: Run the tests to verify they pass**

Run: `./gradlew :jasper-app:test --tests 'dev.jasper.app.architecture.StaticDefaultTest' --tests 'dev.jasper.app.appearance.ThemeMigrationCoverageTest' --tests 'dev.jasper.app.workspace.WorkspaceMetricsTest' --tests 'dev.jasper.app.workspace.DockDragTest' --tests 'dev.jasper.app.workspace.ToolWindowAreaTest' --tests 'dev.jasper.app.workspace.ToolWindowHeaderTest' --tests 'dev.jasper.app.workspace.TitleSearchFieldTest' --tests 'dev.jasper.app.workspace.WindowHeaderTest' --tests 'dev.jasper.app.workspace.WindowToolbarTest' --tests 'dev.jasper.app.workspace.MacTitleBarTest' --tests 'dev.jasper.app.platform.*'`
Expected: PASS (`StaticDefaultTest` 2, `WorkspaceMetricsTest` 4): the scan reports nothing outside `AnimationSettings`
and the pinned `Kit.DEFAULT` readers.

- [ ] **Step 8: Run the affected suites**

Run: `./gradlew :jasper-app:test :jasper-app:javadoc verifyApplicationArchitecture`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 9: Commit**

```bash
git add jasper-app/src/main/java/dev/jasper/app/platform jasper-app/src/main/java/dev/jasper/app/workspace \
  jasper-app/src/test/java/dev/jasper/app/workspace jasper-app/src/test/java/dev/jasper/app/architecture/StaticDefaultTest.java \
  jasper-app/build.gradle.kts docs/design/theme-engine/migration.csv
git commit -F - <<'EOF'
Kit seam: StaticDefaultTest; the app's static metric finals read the installed kit

A bytecode scan of the jasper-ui and jasper-app jars keeps the default
catalogs inside their owners and AnimationSettings, keeps every static
initializer free of metric reads, following the methods it calls, and
pins the classes that read Kit.DEFAULT. The header heights and gaps,
the dock drag threshold, the workspace gutter and arc, the title search
widths, the toolbar button trim and the rail icon size were fixed from
the default catalog at class load; they are now read from the installed
kit when they are used.

🤖 Generated with Claude Code at The Home Depot

Co-Authored-By: Claude <noreply@anthropic.com>
EOF
```

### Task 11: Documentation and status

The current-state documents say what the kit seam does; `docs/STATUS.md` records the plan; the whole build runs.

**Files:**
- Modify docs: `docs/ui-architecture.md:11-16,28-29,35,105-106,223`, `docs/ui-maintenance.md:21,37`, `jasper-ui/README.md:8-9,30,33-34,38`, `docs/app-architecture.md:227,233-235,264-265`, `docs/app-maintenance.md:366-368`, `docs/STATUS.md:2`, this plan's header

**Interfaces:**
- Consumes: everything above.
- Produces: documentation only.

- [ ] **Step 1: Say what the seam does in the current-state documents**

Run this script; each replacement must find its text exactly once:

```bash
python3 -I - <<'PY'
import pathlib
EDITS = [
    ("docs/ui-architecture.md",
     "| `metrics` | `theme` | Fixed logical geometry and central typography/motion roles |\n",
     "| `metrics` | `theme` | Logical geometry catalogs (`DesignMetrics`), type scales and faces, central typography/motion roles |\n"
     "| `kit` | `metrics`, `theme` | The look: `Kit` names a metric catalog, type scale, faces and FlatLaf key set; `Kit.DEFAULT` is the default look |\n"),
    ("docs/ui-architecture.md",
     "| `swing` | `model`, `theme`, `metrics`, `icons` | EDT views",
     "| `swing` | `model`, `theme`, `metrics`, `icons`, `kit` | EDT views"),
    ("docs/ui-architecture.md",
     "`verifyUiArchitecture` checks the acyclic package graph, independence from other Jasper modules,\nJDK-only theme/model boundaries.",
     "`verifyUiArchitecture` checks each package's edges against the list its package-info names (this table), the acyclic\npackage graph, independence from other Jasper modules and JDK-only theme/model/kit boundaries."),
    ("docs/ui-architecture.md",
     "Geometry, spacing, artwork and typography roles\ncome from `DesignMetrics`, `Typography` and `IconRegistry`.",
     "Geometry, spacing, artwork and typography roles\ncome from the context's kit (`KitContext.kit()`: its `DesignMetrics`, `TypeScale` and `FaceSet`), `Typography` and\n`IconRegistry`."),
    ("docs/ui-architecture.md",
     "Each `KitContext` selects a route.",
     "Each `KitContext`, made with `KitContext.of(kit, …)`, carries its kit and selects a route."),
    ("docs/ui-architecture.md",
     "palette, the matching terminal scheme, the editor scheme and syntax, FlatLaf's UI keys from the roles (`UiColorKeys`)\nand the glass.",
     "palette, the matching terminal scheme, the editor scheme and syntax and the glass; `FlatLafInstaller.install(theme, kit)`\nderives FlatLaf's UI keys from the roles when it installs one (`UiColorKeys.flatLaf(theme, kit.uiKeys())`)."),
    ("docs/ui-architecture.md",
     "`NativeChromePainting` owns the native chrome's painting",
     "`ChromePainter` (one per kit; app chrome paints with `ChromePainter.installed()`) owns the native chrome's painting"),
    ("docs/ui-maintenance.md",
     "Install `FlatLafInstaller` before constructing fixtures.",
     "Install `FlatLafInstaller.install(theme, kit)` before constructing fixtures."),
    ("docs/ui-maintenance.md",
     "Keep fixed geometry in named metrics.",
     "Keep fixed geometry in named metrics read through a kit (`KitContext.metrics()`, `ChromePainter`, `NativeStyles`);\n"
     "`StaticDefaultTest` rejects other `DesignMetrics.DEFAULT` reads and static metric finals."),
    ("jasper-ui/README.md",
     "each variant's semantic palette, terminal and editor schemes, syntax styles, glass levels and FlatLaf UI keys\n"
     "(`UiColorKeys`, from the roles) as an immutable `GeneratedTheme`;",
     "each variant's semantic palette, terminal and editor schemes, syntax styles and glass levels as an immutable\n"
     "`GeneratedTheme` (`UiColorKeys` derives FlatLaf's UI keys from its roles when it is installed);"),
    ("jasper-ui/README.md",
     "Swing dependencies in pure packages and package cycles.",
     "Swing dependencies in pure packages, edges a package's package-info does not list and package cycles."),
    ("jasper-ui/README.md",
     "values, fixed logical\n`DesignMetrics`, centrally resolved `Typography`,",
     "values, a `Kit` (`Kit.DEFAULT`) of\nlogical `DesignMetrics`, a `TypeScale` and a `FaceSet`, centrally resolved `Typography`,"),
    ("jasper-ui/README.md",
     "`KitContext` selects each view's theme route and supplies host-owned values.",
     "`KitContext.of(kit, …)` carries a kit, selects each view's theme route and supplies host-owned values."),
    ("docs/app-architecture.md",
     "their painting and text sizes come from `NativeChromePainting` and named kit roles.",
     "their painting and text sizes come from `ChromePainter.installed()` and named kit roles."),
    ("docs/app-architecture.md",
     "`ThemeManager.install` installs a variant through jasper-ui's `FlatLafInstaller` (FlatLaf's IntelliJ-format input from\n"
     "the theme's UI keys, which `UiColorKeys` derives from the semantic roles, then the `Jasper.semantic.*` role keys and\n"
     "the popup shadow) and adds the app's native defaults: the split divider border by class name and the centred title.",
     "`ThemeManager.install(theme, kit)` installs a variant under the start-up kit through jasper-ui's `FlatLafInstaller`\n"
     "(FlatLaf's IntelliJ-format input from the UI keys `UiColorKeys` derives from the theme's semantic roles, then the\n"
     "`Jasper.semantic.*` role keys and the popup shadow, with the kit's geometry) and adds the app's native defaults: the\n"
     "split divider border by class name and the centred title. The kit is published with the theme; app chrome reads it\n"
     "through `FlatLafInstaller.installedKit()`, `ChromePainter.installed()` and `NativeStyles.installed()`."),
    ("docs/app-architecture.md",
     "the named module\nNATIVE_SPLIT_DIVIDER metric",
     "the kit's\nNATIVE_SPLIT_DIVIDER metric"),
    ("docs/app-maintenance.md",
     "and one `GeneratedTheme` per variant whose FlatLaf UI keys\n`UiColorKeys.flatLaf` derives from the roles (",
     "and one `GeneratedTheme` per variant; when one is installed,\n`UiColorKeys.flatLaf` derives its FlatLaf UI keys from the roles ("),
]
for name, old, new in EDITS:
    path = pathlib.Path(name)
    text = path.read_text(encoding="utf-8")
    if text.count(old) != 1: raise SystemExit(f"{name}: expected one {old!r}, found {text.count(old)}")
    path.write_text(text.replace(old, new), encoding="utf-8")
PY
```

The historical records that name the old classes (`docs/STATUS.md`'s earlier entries,
`docs/design/theme-engine/temporary-paths.md`, `calibration.md:282`, `acceptance/workspace.md:83`, plans and specs) stay
as they are.

- [ ] **Step 2: Run the whole build**

Run: `./gradlew check --continue`
Expected: BUILD SUCCESSFUL. Then print the totals:

```bash
python3 -I - <<'PY'
import pathlib, re
totals = [0, 0, 0, 0]
for path in pathlib.Path(".").glob("**/build/test-results/test/*.xml"):
    suite = re.search(r"<testsuite\b[^>]*>", path.read_text(encoding="utf-8")).group(0)
    for i, key in enumerate(("tests", "failures", "errors", "skipped")):
        totals[i] += int(re.search(rf'\b{key}="(\d+)"', suite).group(1))
print(f"{totals[0]:,} tests, {totals[1]} failures, {totals[2]} errors, {totals[3]} skipped")
PY
```

and run the AGENTS.md source-hygiene script over every Java source; it must print nothing.

- [ ] **Step 3: Record the plan in STATUS and in this plan**

In `docs/STATUS.md`, insert after line 2 (the blank line under `# Jasper — Status and Handoff`) the entry below, with
the line Step 2's totals script printed in place of `TOTALS` and the first and last commit of Tasks 1-11 in place of
`FIRST..LAST`, followed by a blank line:

```markdown
**Enterprise presentation, plan 1: the kit seam — 2026-10-15** (`claude/enterprise-presentation`, worktree
`.claude/worktrees/enterprise-presentation`, `FIRST..LAST`; [plan](superpowers/plans/2026-10-15-enterprise-1-kit-seam.md),
[spec](superpowers/specs/2026-10-15-jasper-enterprise-presentation-design.md)). jasper-ui gains `dev.jasper.ui.kit`: a
`Kit` names a metric catalog, a type scale, faces and a FlatLaf key set, and `Kit.DEFAULT` is today's look.
`DesignMetrics` and the new `TypeScale` are values (`with`, `withBody`), `FaceSet.PLATFORM` names today's faces,
`Typography.of(scale, faces, user)` replaces its constructors, every `KitContext` is made with `KitContext.of(kit, …)`,
`IconRegistry.size` and tiles take the caller's metrics, and `ChromePainter(kit)` and `NativeStyles(kit)` replace the
static native painters. `FlatLafInstaller.install(theme, kit)` derives FlatLaf's colour keys from the theme at install
time (`GeneratedTheme` no longer carries them) and publishes the kit beside the theme as `Jasper.installedKit`;
`ThemeManager.install(theme, kit)` and `ThemeController.startup(…, kit, …)` carry it, and start-up uses `Kit.DEFAULT`.
Zero pixel change, proven by tests: the frozen catalog (`compact-roles.tsv`) and the new `default-type.tsv`, an oracle of
the former `Typography` over every role and user-font mode, every built-in variant's install-time keys against
`ui-color-keys.tsv` and the keys it carried, the default kit's FlatLaf extras frozen whole, and the ported painter tests;
seam tests show one altered metric moving a context, a FlatLaf extra and a painter. `StaticDefaultTest` (a
`java.lang.classfile` scan of the jasper-ui and jasper-app jars) allows `DesignMetrics.DEFAULT` only in the catalog's
packages and `AnimationSettings`, and no static metric final, following each static initializer's calls. Planned
deviations (plan, "Planned deviations"): (1) the app reaches the kit through the installed appearance
(`FlatLafInstaller.installedKit()`, `ChromePainter.installed()`, `NativeStyles.installed()`) until plan 5's
`Presentation` plumbing, which must replace it before a non-default kit is installed (a rollback can show off-EDT
readers `Kit.DEFAULT`, and cached icons keep their kit); (2) `Kit` has no control variants or colour slots yet; (3)
`DesignMetrics.with(Role, int)` and `TypeScale.withBody` only, with the `CLASSIC` values and tables in plan 4; (4)
`FaceSet` carries only the data faces, the UI faces in plan 5; (5) the user's font comes from the UIManager keys
`ThemeController` writes; (6) the start-up kit is captured in the controller's installer; (7) a recoloured test theme
installs FlatLaf keys from its own palette, and `ThemeTestSupport.withUiKeys` is gone; (8) `StaticDefaultTest` also
rejects `TypeScale.DEFAULT` and `FaceSet.PLATFORM` reads and painters built at class load, and pins the `Kit.DEFAULT`
readers; (9) `TitleSearchField.ARC` is deleted and `PresentationIcon` reads the installed kit's rail size.
`./gradlew check --continue`: **TOTALS**. GUI checks for the user: none new (nothing
visible changed). Not merged, not pushed.
```

Then insert, after this plan's first line (`# Enterprise presentation 1: kit seam Implementation Plan`) and a blank line:

```markdown
> **Status (2026-10-15): executed on `claude/enterprise-presentation` (`FIRST..LAST`).** Deviations from this plan's
> text: none beyond its "Planned deviations".
```

replacing `FIRST..LAST` the same way, and naming any step whose text had to change instead of "none" (and the same in
the STATUS entry).

- [ ] **Step 4: Commit**

```bash
git add docs/ui-architecture.md docs/ui-maintenance.md jasper-ui/README.md \
  docs/app-architecture.md docs/app-maintenance.md docs/STATUS.md docs/superpowers/plans/2026-10-15-enterprise-1-kit-seam.md
git commit -F - <<'EOF'
Kit seam: documentation and status

The current-state documents describe the kit seam, and STATUS records
plan 1 with its planned deviations.

🤖 Generated with Claude Code at The Home Depot

Co-Authored-By: Claude <noreply@anthropic.com>
EOF
```

