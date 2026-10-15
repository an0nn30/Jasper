# Enterprise presentation 2: derived roles Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** The theme engine gains the colour side of the Enterprise look: host-private derived roles computed from every
variant's palette (or authored by it through eight override keys), paint-time guards in `SurfaceContrast`, the
converter's override keys, and the contrast and gallery matrices as JUnit inside `./gradlew check`, with no painted
pixel changed.

**Architecture:** `dev.jasper.ui.theme.DerivedColor` (23 roles, 8 of them overridable) is a separate enum from
`SemanticColor`; `DerivedPalette.of(GeneratedTheme)` computes them in declaration order from spec 7.1's recipes, each
reading only semantic roles and earlier derived roles, or takes the variant's override, and is kept by theme identity.
`JasperTheme` reads the override keys through its own table (`DERIVED_OVERRIDES`) into
`GeneratedTheme.derivedOverrides()`, which `TitleBarTheme` carries. `SurfaceContrast.Guard` is the paint-time repair a
face declares for each pair it paints (`PaintPair`, over the sealed `ColorRole` of both enums), applied by
`SurfaceContrast.painted`. jasper-ui's gallery and kit fixtures move to a `testFixtures` source set that jasper-app's
tests share; `KitMatrixTest` renders the gallery under `Kit.DEFAULT` over the theme set, and `PaintedContrastTest`
(the generalised `ShippedControlReadabilityTest`) samples the default look's declared pairs over the theme set and
eight degenerate palettes. Nothing in the default look paints a derived role, and it declares only the pairs it
repairs today.

**Tech Stack:** Java 25 on JetBrains Runtime 25, Swing and FlatLaf 3.7, Gradle 9.7 (Kotlin DSL, `java-test-fixtures`),
JUnit 6.1.3 and AssertJ 3.27.7, Python 3.11+ standard library for the converter (`tools/themes/jasper_theme.py`) and
the mechanical edit scripts (always `python3 -I`).

**Spec:** [`docs/superpowers/specs/2026-10-15-jasper-enterprise-presentation-design.md`](../specs/2026-10-15-jasper-enterprise-presentation-design.md)
(sections 7.1-7.3, 7.7, 16.1.1-16.1.3 and row 2 of 19.1). Plan 1 ([kit seam](2026-10-15-enterprise-1-kit-seam.md)) is
merged on `main` at `2f6770b5b`; its `docs/STATUS.md` entry holds its deviations and the plan-5 entry conditions, none
of which this plan touches.

**Workspace:** worktree `/Users/RQ7RQVF/projects/moray/.claude/worktrees/enterprise-derived-roles`, branch
`claude/enterprise-derived-roles`, from `main` at `2f6770b5b`. Run every command from the worktree root, never in
`~/projects/moray` itself. Every line number below is from `2f6770b5b` unless a step says otherwise, and every edit
quotes the exact text it replaces: the edit scripts stop with a message if their text is not found exactly once.

## Global Constraints

- Java 25 on the JetBrains Runtime 25 through the Gradle toolchain; run `./gradlew`, never a system `gradle`.
- Never launch the GUI (`:jasper-app:run`, `:jasper-app:bench`) and never train the AOT cache (AGENTS.md). `:jasper-ui:gallery` is headless (`java.awt.headless=true`) and may be run.
- Work only in the worktree above on `claude/enterprise-derived-roles`; one commit per task; every commit message ends with the two lines `🤖 Generated with Claude Code at The Home Depot` and `Co-Authored-By: Claude <noreply@anthropic.com>` separated by a blank line. Do not push or merge.
- Zero pixel change (spec 19.1 row 2): no default-look painter reads a derived role, no painted colour changes, every existing UIManager key and frozen table (`compact-roles.tsv`, `default-type.tsv`, `ui-color-keys.tsv`) stays identical, and no existing assertion's expected value changes except the converter goldens and built-in Catppuccin files that spec 7.7 regenerates (Task 5). The default look declares only the pairs it repairs today (spec 7.3).
- `DerivedColor` is host-private: `SemanticPalette`'s completeness rule, `SemanticColor`'s constants and the SDK's `SemanticColor` mirror are unchanged (spec 7.1). The derived roles are spec 7.1's 23, in its order; the 8 overridable ones are its starred roles.
- Every derived colour is opaque; an override key takes `#RRGGBB` only (spec 7.3's guards assume opaque colours).
- `DerivedPalette.of` is a pure function of the palette and the variant's overrides; guards never store a result (spec 7.3).
- Guard floors (spec 7.3): text 4.5:1, indicator 3:1, structural boundary 1.3:1, control boundary 1.5:1, apart = the ΔL* its recipe names. A contrast floor is at most 4.58 and a lightness distance at most 50 L*, so every guard terminates.
- jasper-ui depends on no other Jasper module; `dev.jasper.ui.theme` stays JDK-only and Swing-free (`verifyUiArchitecture`).
- Javadoc doclint fails the build: jasper-ui runs `-Xdoclint:all,-missing`, jasper-app `-Xdoclint:all`; every `{@link}` must resolve and no comment may contain a raw `<`, `>`, `&` or `->`.
- No colour literal in production code or resources outside the built-in theme files (`ColourLiteralScanTest`).
- Every new or renamed candidate source under jasper-app, the SDK, the testkit or plugins gets its `docs/design/theme-engine/migration.csv` row in the same commit (`ThemeMigrationCoverageTest`).
- The converter's output is byte-stable and its goldens are compared byte for byte; the four built-in Catppuccin themes are the converter's output (`test_the_built_in_catppuccin_themes_are_the_converters_output`).
- MVP (no compatibility shims): `GeneratedTheme` gains its component outright and every construction site passes it; no secondary constructor.
- Source hygiene (AGENTS.md): no raw control, private-use or unpaired surrogate characters in any source file.

## Review Focus

1. A theme author lists an override key with alpha (`"surface.tableHeader": "#F0F1F380"`), a non-string, or a derived role that has no override key (`"border.divider"`): the file fails to load with one message naming the entry, as every other malformed entry does. Task 3: `JasperThemeTest.anOverrideKeyTakesAnOpaqueColourAndOnlyTheEightAreKeys`.
2. A theme gives a recipe's input alpha (a hand-written `"border.window": "#10203080"` or a translucent `text.primary`): the derived roles are computed from the colour as it shows on the surface the recipe mixes it with, and stay opaque. Task 4: `DerivedPaletteTest.aTranslucentInputIsLaidOverTheSurfaceItsRecipeMixesItWith`.
3. An override defeats a recipe's distance (Catppuccin's active and inactive headers are one colour; an authored table header almost the card's colour): the derived roles keep the author's colours, the recipes after it read them (the zebra row stays closer to the card than the header), and the guards, not the values, repair contrast where they are painted. Task 4: `DerivedPaletteTest.anOverrideReplacesItsRecipeAndTheRecipesAfterItReadIt`; Task 5: `BuiltInThemesTest.catppuccinAuthorsFiveDerivedRolesAndSlateCobaltNone`.
4. The converter meets an IntelliJ theme whose override key comes only from IntelliJ's own parent theme (New UI's `Table.stripeColor`) or from a `*.` wildcard: no override key is written, because that colour belongs to another palette; a parent theme in the same plugin does count. Task 5: `test_a_wildcard_or_an_intellij_parent_fills_no_override_and_a_parent_in_the_plugin_does`.
5. A variant with a title bar of its own: header chrome derives from `TitleBarTheme.of(theme)`, whose palette is the title bar's and which still carries the variant's overrides. Task 3: `TitleBarThemeTest.theHeaderThemeCarriesTheDerivedRolesTheVariantAuthors`; Task 4: `DerivedPaletteTest.underATitleBarOfItsOwnTheHeaderDerivesFromTheHeaderPalette`.

## Planned deviations

Record each of these in `docs/STATUS.md` (Task 8) and in this plan's status banner when it is executed.

1. **Declared pairs before faces.** Spec 7.3 has each face declare its `(slot, against, guard)` pairs and 16.1.1's
   `PaintSpecTest` check those tables; faces arrive in plan 4. Plan 2 adds the declaration types (`PaintPair`,
   `SurfaceContrast.Guard`, `ColorRole`) and `SurfaceContrast.painted`, and the default look's declarations are
   `PaintedContrastTest.DEFAULT_PAIRS`, beside the painter each is sampled from: the title search hint, the inactive tab
   surface, the running status dot, the rail badge and error badge, the connected dot (`ChromePainter`), the selected
   segment label (`Choices`) and the selected preview caption (`PreviewChoices`). The repairs public API does not reach
   keep their package tests (`PanelHeaders`' dot, which is the connected dot's pair: `PanelHostHeaderTest`; the status
   bar's configuration text: `ConfigurationStatusTest`; notification counts: `NotificationCardTest`);
   `IconRegistry.surfaceTint` repairs
   artwork on a surface outside the palette, which is not a role pair. Plan 4 moves the declarations onto the default
   faces and adds `PaintSpecTest`.
2. **The theme set.** Spec 16.1.2 names the converter fixtures "ocean, ocean-dawn, paper-light"; `paper-light` is a
   terminal-only Alacritty fixture with no palette, so `KitFixtures.themes()` takes the converter fixtures that have UI
   variants: ocean, ocean-dawn and stray. Enterprise Light and Dark join it in plan 5.
3. **The default kit's matrix.** Of 16.1.3's assertions, plan 2's `KitMatrixTest` renders every gallery fixture under
   `Kit.DEFAULT` over the theme set and checks that under `custom()` no pixel has a colour only another theme has (the
   literal check). The marked-pixel mapping is stated for classic pixels and arrives with the classic faces, as do the
   complement kit, the covering array and the classic frozen tables (plan 4); `GalleryCoverageTest` is
   kit-independent and stays as it is; `derived-roles.tsv` is frozen by `DerivedPaletteTest`.
4. **Recipe details spec 7.1 leaves open.** A translucent input is laid over the surface its recipe mixes it with
   first; `border.window′` and `border.control′` are guarded on `surface.card` toward `text.primary`; "closer to
   `surface.card` than `surface.tableHeader`" falls back to halfway between the table header and the card when the
   zebra recipe is not closer; `accent.edge` moves darker when the accent is darker than the card (or as light, on a
   light card) and lighter otherwise, ending at black or white. "Cached by identity" keeps the last four themes asked
   for (chrome, workspace and the title bar alternate while painting). "Computed after `TitleBarTheme`" means header
   chrome passes `TitleBarTheme.of(theme)`, which carries the overrides.
5. **Override keys.** The Java loader accepts the override keys anywhere in a variant, as it accepts the roles in any
   order; the converter writes them after the roles and the title bar's keys, and its check enforces that order.
6. **The converter's source keys.** "When the IntelliJ theme has the key" is read as: the theme, or a parent theme in
   its plugin, sets the exact key. IntelliJ's vendored themes and `*.` wildcards do not count, and a translucent
   value is laid over `surface.app` (header keys) or `surface.card` (the rest). To exercise this, the Ocean fixture
   (both copies) gains `ToolWindow.Header.*` and `Table.hoverBackground` and the Dawn fixture `TableHeader.background`
   and `Banner.*`. Regenerating the goldens also rewrites the four built-in Catppuccin themes, which gain five override
   keys each because the vendored plugin sets those keys; nothing in the default look paints them.
7. **Gallery entry points.** `KitGallery.main`'s body splits into `installed(theme, kit, action)` and
   `capture(fixture, context, focus)`, so `KitMatrixTest` renders exactly what the opt-in gallery writes;
   `KitGallery.GalleryFocus` becomes public.

---

### Task 1: jasper-ui test fixtures, the theme set and the degenerate palettes

The gallery and the kit fixtures move from jasper-ui's test source set into a `testFixtures` source set (spec 16.1.3),
so jasper-app's tests can use them (Task 7) and they still never reach the production jar. `KitFixtures` gains the
theme set every later matrix runs over and the eight degenerate palettes of spec 16.1.2. Before anything changes, the
gallery's output at the base is recorded; Task 8 compares the branch against it.

**Files:**
- Modify: `jasper-ui/build.gradle.kts:3-9` (plugins, dependencies)
- Move: `jasper-ui/src/test/java/dev/jasper/ui/gallery/{KitGallery,FormFixtures,PanelFixtures,StateFixtures}.java` to `jasper-ui/src/testFixtures/java/dev/jasper/ui/gallery/`, and `jasper-ui/src/test/java/dev/jasper/ui/testsupport/{KitFixtures,Edt}.java` to `jasper-ui/src/testFixtures/java/dev/jasper/ui/testsupport/`
- Modify: `jasper-ui/src/testFixtures/java/dev/jasper/ui/testsupport/KitFixtures.java:42` (after the move; new methods before `withSemantic`)
- Test: `jasper-ui/src/test/java/dev/jasper/ui/testsupport/KitFixturesTest.java` (two tests)

**Interfaces:**
- Consumes: nothing new.
- Produces: the `testFixtures` source set of `:jasper-ui` (consumed as `testFixtures(project(":jasper-ui"))`), with
  `dev.jasper.ui.testsupport.KitFixtures`, `dev.jasper.ui.testsupport.Edt` and the `dev.jasper.ui.gallery` builders
  (`KitGallery`, `FormFixtures`, `PanelFixtures`, `StateFixtures`); `public static List<GeneratedTheme>
  KitFixtures.themes()` (Slate Cobalt Dark, Slate Cobalt Light, Catppuccin Latte, Frappé, Macchiato, Mocha, then the
  converter goldens ocean, ocean-dawn and stray, each loaded from `tools/themes/testdata/expected` under the
  `jasper.repoRoot` system property); `public static List<GeneratedTheme> KitFixtures.degenerate()` (eight themes named
  `"Degenerate: one grey"`, `"Degenerate: all black"`, `"Degenerate: all white"`, `"Degenerate: accent on the
  surfaces"`, `"Degenerate: maximum chroma"`, `"Degenerate: text on the surfaces"`, `"Degenerate: borders on the
  surfaces"`, `"Degenerate: light under the dark flag"`, in that order).

- [ ] **Step 1: Record the base gallery**

Before any edit, render the headless gallery for both Slate Cobalt variants and keep its hashes for Task 8:

```bash
mkdir -p build/plan2
./gradlew :jasper-ui:gallery -Pjasper.galleryTheme=dark
./gradlew :jasper-ui:gallery -Pjasper.galleryTheme=light
(cd jasper-ui/build/gallery/dark-1x && shasum -a 256 * | sort) > build/plan2/gallery-dark-base.sha
(cd jasper-ui/build/gallery/light-1x && shasum -a 256 * | sort) > build/plan2/gallery-light-base.sha
wc -l build/plan2/gallery-dark-base.sha build/plan2/gallery-light-base.sha
```

Expected: each `gallery` run prints `Rendered 66 fixtures with Slate Cobalt at 1.0x to …`, and each hash file has 142
lines (66 PNGs, 66 JSONs, 9 contact sheets and the manifest).

- [ ] **Step 2: Write the failing tests**

Replace the whole of `jasper-ui/src/test/java/dev/jasper/ui/testsupport/KitFixturesTest.java` with:

```java
package dev.jasper.ui.testsupport;

import static org.assertj.core.api.Assertions.assertThat;

import dev.jasper.ui.theme.GeneratedTheme;
import dev.jasper.ui.theme.JasperTheme;
import dev.jasper.ui.theme.SemanticColor;
import dev.jasper.ui.theme.SemanticPalette;
import java.util.Set;
import org.junit.jupiter.api.Test;

class KitFixturesTest {
    @Test void theFixturesAreSlateCobaltAndCustomDiffersInEveryOpaqueRole() {
        assertThat(KitFixtures.dark()).isSameAs(JasperTheme.bundled().generated(true));
        assertThat(KitFixtures.light()).isSameAs(JasperTheme.bundled().generated(false));
        var custom = KitFixtures.custom();
        assertThat(custom.dark()).isTrue();
        for (var role : SemanticColor.values()) {
            var base = KitFixtures.dark().semantic().color(role);
            assertThat(custom.semantic().color(role).getAlpha()).as(role.key()).isEqualTo(base.getAlpha());
            if (base.getAlpha() == 255) assertThat(custom.semantic().color(role)).as(role.key()).isNotEqualTo(base);
        }
    }

    /** Spec 16.1.2's theme set: the shipped variants, then the converter goldens that have a UI variant. */
    @Test void theThemeSetIsTheShippedVariantsThenTheConvertedOnes() {
        assertThat(KitFixtures.themes()).extracting(theme -> theme.name() + (theme.dark() ? " Dark" : " Light")).containsExactly(
            "Slate Cobalt Dark", "Slate Cobalt Light", "Catppuccin Latte Light", "Catppuccin Frappé Dark",
            "Catppuccin Macchiato Dark", "Catppuccin Mocha Dark", "Ocean Dark", "Ocean Dawn Light", "Stray Dark");
        assertThat(KitFixtures.themes().getFirst()).isSameAs(KitFixtures.dark());
    }

    /** Spec 16.1.2's eight degenerate palettes, each a Slate Cobalt variant with its terminal, editor and alpha kept. */
    @Test void theDegeneratePalettesAreTheEightNamedOnes() {
        var palettes = KitFixtures.degenerate();
        assertThat(palettes).extracting(GeneratedTheme::name).containsExactly("Degenerate: one grey", "Degenerate: all black",
            "Degenerate: all white", "Degenerate: accent on the surfaces", "Degenerate: maximum chroma",
            "Degenerate: text on the surfaces", "Degenerate: borders on the surfaces", "Degenerate: light under the dark flag");
        for (var role : SemanticColor.values()) {
            assertThat(rgb(palettes.get(0).semantic(), role)).as("one grey " + role.key()).isEqualTo(0x808080);
            assertThat(rgb(palettes.get(1).semantic(), role)).as("all black " + role.key()).isZero();
            assertThat(rgb(palettes.get(2).semantic(), role)).as("all white " + role.key()).isEqualTo(0xFFFFFF);
            assertThat(Set.of(0xFF0000, 0x00FF00, 0x0000FF, 0xFFFF00, 0xFF00FF, 0x00FFFF)).as("maximum chroma " + role.key())
                .contains(rgb(palettes.get(4).semantic(), role));
            assertThat(palettes.get(0).semantic().color(role).getAlpha()).as("alpha kept " + role.key())
                .isEqualTo(KitFixtures.dark().semantic().color(role).getAlpha());
        }
        assertThat(KitFixtures.degenerate().get(4).semantic()).as("seeded").isEqualTo(palettes.get(4).semantic());
        var accent = palettes.get(3).semantic();
        int card = rgb(KitFixtures.light().semantic(), SemanticColor.SURFACE_CARD);
        for (var role : new SemanticColor[] {SemanticColor.ACCENT, SemanticColor.SELECTION_BG, SemanticColor.SURFACE_APP, SemanticColor.SURFACE_INPUT})
            assertThat(rgb(accent, role)).as("accent on the surfaces " + role.key()).isEqualTo(card);
        assertThat(accent.color(SemanticColor.TEXT_PRIMARY)).isEqualTo(KitFixtures.light().semantic().color(SemanticColor.TEXT_PRIMARY));
        var text = palettes.get(5).semantic();
        for (var role : new SemanticColor[] {SemanticColor.TEXT_PRIMARY, SemanticColor.TEXT_ONACCENT, SemanticColor.STATUS_WARNING, SemanticColor.SURFACE_PANEL})
            assertThat(rgb(text, role)).as("text on the surfaces " + role.key()).isEqualTo(card);
        var borders = palettes.get(6).semantic();
        for (var role : new SemanticColor[] {SemanticColor.BORDER_WINDOW, SemanticColor.BORDER_CONTROL, SemanticColor.BUTTON_SECONDARY_BORDER, SemanticColor.SURFACE_APP})
            assertThat(rgb(borders, role)).as("borders on the surfaces " + role.key()).isEqualTo(card);
        assertThat(borders.color(SemanticColor.ACCENT)).isEqualTo(KitFixtures.light().semantic().color(SemanticColor.ACCENT));
        var flagged = palettes.get(7);
        assertThat(flagged.dark()).isTrue();
        assertThat(flagged.semantic()).isEqualTo(KitFixtures.light().semantic());
        for (int i = 0; i < palettes.size(); i++)
            assertThat(palettes.get(i).terminal()).as(palettes.get(i).name())
                .isEqualTo((i < 3 || i == 4 ? KitFixtures.dark() : KitFixtures.light()).terminal());
    }

    private static int rgb(SemanticPalette palette, SemanticColor role) { return palette.color(role).getRGB() & 0xFFFFFF; }
}
```

- [ ] **Step 3: Run the tests to verify they fail**

Run: `./gradlew :jasper-ui:test --tests 'dev.jasper.ui.testsupport.KitFixturesTest'`
Expected: FAIL in `:jasper-ui:compileTestJava` with `error: cannot find symbol` / `symbol: method themes()` and
`symbol: method degenerate()`.

- [ ] **Step 4: Move the gallery and the kit fixtures into a testFixtures source set**

```bash
mkdir -p jasper-ui/src/testFixtures/java/dev/jasper/ui/gallery jasper-ui/src/testFixtures/java/dev/jasper/ui/testsupport
for name in KitGallery FormFixtures PanelFixtures StateFixtures; do
  git mv jasper-ui/src/test/java/dev/jasper/ui/gallery/$name.java jasper-ui/src/testFixtures/java/dev/jasper/ui/gallery/$name.java
done
for name in KitFixtures Edt; do
  git mv jasper-ui/src/test/java/dev/jasper/ui/testsupport/$name.java jasper-ui/src/testFixtures/java/dev/jasper/ui/testsupport/$name.java
done
python3 -I - <<'PY'
import pathlib
path = pathlib.Path("jasper-ui/build.gradle.kts")
text = path.read_text(encoding="utf-8")
old = """plugins { `java-library` }

dependencies {
    implementation("com.formdev:flatlaf:3.7")
    implementation("com.formdev:flatlaf-extras:3.7")
    implementation("com.github.weisj:jsvg:2.0.0")
}
"""
new = """plugins { `java-library`; `java-test-fixtures` }

dependencies {
    implementation("com.formdev:flatlaf:3.7")
    implementation("com.formdev:flatlaf-extras:3.7")
    implementation("com.github.weisj:jsvg:2.0.0")
    // The gallery and the kit fixtures, shared with jasper-app's tests (spec 16.1.3); never in the production jar.
    testFixturesImplementation("com.formdev:flatlaf:3.7")
}
"""
if text.count(old) != 1: raise SystemExit(f"jasper-ui/build.gradle.kts: expected one {old!r}")
path.write_text(text.replace(old, new), encoding="utf-8")
PY
```

The gallery's `JavaExec` keeps `sourceSets.test.get().runtimeClasspath`, which includes the test fixtures. The tests
that stay in `src/test` (`GalleryCoverageTest`, `UiDocumentationTest`, `UiExamplesTest`, `KitFixturesTest`) reach the
gallery's package-private members from the same package, which the test classpath shares.

- [ ] **Step 5: Add the theme set and the degenerate palettes**

```bash
python3 -I - <<'PY'
import pathlib
path = pathlib.Path("jasper-ui/src/testFixtures/java/dev/jasper/ui/testsupport/KitFixtures.java")
text = path.read_text(encoding="utf-8")
old = "    /** {@code base} with its semantic palette replaced. */"
new = '''    /**
     * The theme set every look is checked over (spec 16.1.2): Slate Cobalt Dark and Light, the four Catppuccin flavours and
     * the converter goldens' UI variants (ocean, ocean-dawn, stray), in that order.
     */
    public static java.util.List<GeneratedTheme> themes() {
        var themes = new java.util.ArrayList<GeneratedTheme>();
        for (var theme : JasperTheme.builtIns())
            for (boolean dark : new boolean[] {true, false}) if (theme.has(dark)) themes.add(theme.generated(dark));
        var expected = java.nio.file.Path.of(System.getProperty("jasper.repoRoot", "..")).toAbsolutePath().normalize()
            .resolve("tools/themes/testdata/expected");
        for (String id : java.util.List.of("ocean", "ocean-dawn", "stray")) {
            try {
                var theme = JasperTheme.parse(java.nio.file.Files.readString(expected.resolve(id + ".json")), id);
                for (boolean dark : new boolean[] {true, false}) if (theme.has(dark)) themes.add(theme.generated(dark));
            } catch (java.io.IOException failure) { throw new java.io.UncheckedIOException(failure); }
        }
        return java.util.List.copyOf(themes);
    }

    /**
     * Eight palettes no theme should have (spec 16.1.2), each Slate Cobalt's with its terminal, editor and syntax kept, and
     * each role's alpha kept: every role one grey; all black; all white; the accent equal to the surfaces; maximum chroma,
     * seeded; each text equal to its surface; each border equal to its surface; Light's colours under the dark flag. A
     * name says which.
     */
    public static java.util.List<GeneratedTheme> degenerate() {
        var grey = new java.awt.Color(0x808080);
        var card = light().semantic().color(SemanticColor.SURFACE_CARD);
        var chroma = java.util.List.of(new java.awt.Color(0xFF0000), new java.awt.Color(0x00FF00), new java.awt.Color(0x0000FF),
            new java.awt.Color(0xFFFF00), new java.awt.Color(0xFF00FF), new java.awt.Color(0x00FFFF));
        var seeded = new java.util.Random(0x5EEDL);
        var maximum = new java.util.EnumMap<SemanticColor, java.awt.Color>(SemanticColor.class);
        for (var role : SemanticColor.values()) maximum.put(role, chroma.get(seeded.nextInt(chroma.size())));
        return java.util.List.of(
            degenerate("one grey", dark(), role -> grey),
            degenerate("all black", dark(), role -> java.awt.Color.BLACK),
            degenerate("all white", dark(), role -> java.awt.Color.WHITE),
            degenerate("accent on the surfaces", light(), role -> role.key().startsWith("surface.") || role == SemanticColor.ACCENT
                || role == SemanticColor.ACCENT_HOVER || role == SemanticColor.SELECTION_BG || role == SemanticColor.BORDER_FOCUS
                || role == SemanticColor.TAB_INDICATOR ? card : null),
            degenerate("maximum chroma", dark(), maximum::get),
            degenerate("text on the surfaces", light(), role -> role.key().startsWith("surface.") || role.key().startsWith("text.")
                || role.key().startsWith("status.") ? card : null),
            degenerate("borders on the surfaces", light(), role -> role.key().startsWith("surface.") || role.key().startsWith("border.")
                || role == SemanticColor.BUTTON_SECONDARY_BG || role == SemanticColor.BUTTON_SECONDARY_BORDER ? card : null),
            withName(withDark(light(), true), "Degenerate: light under the dark flag"));
    }

    /** {@code base} named for a degenerate palette, each role {@code colour}'s value with its own alpha, or kept when null. */
    private static GeneratedTheme degenerate(String name, GeneratedTheme base,
                                             java.util.function.Function<SemanticColor, java.awt.Color> colour) {
        var values = new java.util.EnumMap<SemanticColor, java.awt.Color>(SemanticColor.class);
        for (var role : SemanticColor.values()) {
            var own = base.semantic().color(role);
            var replaced = colour.apply(role);
            values.put(role, replaced == null ? own
                : new java.awt.Color(replaced.getRed(), replaced.getGreen(), replaced.getBlue(), own.getAlpha()));
        }
        return withName(withSemantic(base, new SemanticPalette(values)), "Degenerate: " + name);
    }

    /** {@code base} named {@code name}. */
    private static GeneratedTheme withName(GeneratedTheme base, String name) {
        return new GeneratedTheme(name, base.dark(), base.author(), base.semantic(), base.terminal(), base.editor(), base.syntax(),
            base.glass());
    }

    /** {@code base} with its semantic palette replaced. */'''
if text.count(old) != 1: raise SystemExit(f"KitFixtures.java: expected one {old!r}")
path.write_text(text.replace(old, new), encoding="utf-8")
PY
```

- [ ] **Step 6: Run the tests to verify they pass**

Run: `./gradlew :jasper-ui:test`
Expected: BUILD SUCCESSFUL; `KitFixturesTest` passes its three tests and every other jasper-ui test still passes,
among them `GalleryCoverageTest.shippedJarExcludesGalleryAndTestSupport` (the production jar has no `/gallery/` or
`/testsupport/` entry) and `KitKeyBindingAuditTest`, `PanelCompositionTest` and `KitCalibrationTest`, which use the
gallery from `dev.jasper.ui.swing`.

Then: `./gradlew :jasper-ui:gallery -Pjasper.galleryTheme=dark` — Expected: `Rendered 66 fixtures …`, and
`(cd jasper-ui/build/gallery/dark-1x && shasum -a 256 * | sort) | diff - build/plan2/gallery-dark-base.sha` prints
nothing.

- [ ] **Step 7: Commit**

```bash
git add jasper-ui/build.gradle.kts jasper-ui/src/testFixtures jasper-ui/src/test/java/dev/jasper/ui/gallery \
  jasper-ui/src/test/java/dev/jasper/ui/testsupport
git commit -F - <<'EOF'
Derived roles: jasper-ui test fixtures, the theme set and degenerate palettes

The gallery and the kit fixtures move to a testFixtures source set so
jasper-app's tests can share them. KitFixtures gains the theme set the
matrices run over and spec 16.1.2's eight degenerate palettes.

🤖 Generated with Claude Code at The Home Depot

Co-Authored-By: Claude <noreply@anthropic.com>
EOF
```

### Task 2: Paint-time guards in SurfaceContrast

`SurfaceContrast` gains spec 7.3's two boundary floors and `Guard`, the repair a face declares for a pair it paints: a
contrast floor or a lightness distance, applied only where the pair is painted. A guard takes only a floor every
opaque colour can reach, so it always terminates and always holds afterwards. Nothing calls it from a painter yet.

**Files:**
- Modify: `jasper-ui/src/main/java/dev/jasper/ui/theme/SurfaceContrast.java:9-11` (class comment), `:17-19` (constants and the record)
- Test: `jasper-ui/src/test/java/dev/jasper/ui/theme/SurfaceContrastTest.java` (imports, four tests)

**Interfaces:**
- Consumes: `KitFixtures.degenerate()` (Task 1).
- Produces: `public static final double SurfaceContrast.STRUCTURAL = 1.3`, `CONTROL = 1.5`, `REACHABLE = 4.58`,
  `REACHABLE_LIGHTNESS = 50`; `public record SurfaceContrast.Guard(Guard.Kind kind, double floor)` with
  `enum Kind { CONTRAST, APART }`, constants `Guard.TEXT` (4.5), `Guard.INDICATOR` (3), `Guard.STRUCTURAL` (1.3),
  `Guard.CONTROL` (1.5), `static Guard apart(double lightness)`, `Color apply(Color color, Color against, Color
  toward)` (the colour itself when it already holds; a contrast guard moves it as `readable` does, toward `toward`
  then black or white; an apart guard moves it as `apart` does) and `boolean holds(Color color, Color against)`. The
  compact constructor throws `IllegalArgumentException("<KIND> floor out of range: <floor>")` for a contrast floor
  outside 1-4.58 or a distance outside 0-50 (NaN included) and `NullPointerException` for a null kind.

- [ ] **Step 1: Write the failing tests**

In `jasper-ui/src/test/java/dev/jasper/ui/theme/SurfaceContrastTest.java`, replace lines 5-8:

```java
import java.util.EnumMap;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
```

with:

```java
import java.util.EnumMap;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;
```

and insert before the class's closing brace (line 60):

```java

    @Test void theGuardsAreSpecSevenThreesFloors() {
        assertThat(SurfaceContrast.Guard.TEXT).isEqualTo(new SurfaceContrast.Guard(SurfaceContrast.Guard.Kind.CONTRAST, 4.5));
        assertThat(SurfaceContrast.Guard.INDICATOR).isEqualTo(new SurfaceContrast.Guard(SurfaceContrast.Guard.Kind.CONTRAST, 3));
        assertThat(SurfaceContrast.Guard.STRUCTURAL).isEqualTo(new SurfaceContrast.Guard(SurfaceContrast.Guard.Kind.CONTRAST, 1.3));
        assertThat(SurfaceContrast.Guard.CONTROL).isEqualTo(new SurfaceContrast.Guard(SurfaceContrast.Guard.Kind.CONTRAST, 1.5));
        assertThat(SurfaceContrast.Guard.apart(9)).isEqualTo(new SurfaceContrast.Guard(SurfaceContrast.Guard.Kind.APART, 9));
    }

    @Test void aGuardTakesOnlyAFloorEveryOpaqueColourReaches() {
        for (double floor : new double[] {.99, 4.59, Double.NaN})
            assertThatThrownBy(() -> new SurfaceContrast.Guard(SurfaceContrast.Guard.Kind.CONTRAST, floor))
                .isInstanceOf(IllegalArgumentException.class).hasMessageStartingWith("CONTRAST floor out of range");
        for (double distance : new double[] {-.01, 50.01, Double.NaN})
            assertThatThrownBy(() -> SurfaceContrast.Guard.apart(distance))
                .isInstanceOf(IllegalArgumentException.class).hasMessageStartingWith("APART floor out of range");
        assertThatThrownBy(() -> new SurfaceContrast.Guard(null, 3)).isInstanceOf(NullPointerException.class);
    }

    /** Spec 7.3: every guard terminates and holds, for every pair of roles of every degenerate palette, at the extreme floors too. */
    @Test void everyGuardHoldsForEveryPairOfEveryDegeneratePalette() {
        var guards = List.of(SurfaceContrast.Guard.TEXT, SurfaceContrast.Guard.INDICATOR, SurfaceContrast.Guard.STRUCTURAL,
            SurfaceContrast.Guard.CONTROL, new SurfaceContrast.Guard(SurfaceContrast.Guard.Kind.CONTRAST, SurfaceContrast.REACHABLE),
            SurfaceContrast.Guard.apart(SurfaceContrast.TAB_LIGHTNESS), SurfaceContrast.Guard.apart(SurfaceContrast.REACHABLE_LIGHTNESS));
        for (var theme : KitFixtures.degenerate()) {
            var palette = theme.semantic();
            Color ink = palette.color(SemanticColor.TEXT_PRIMARY);
            for (var slot : SemanticColor.values()) for (var against : SemanticColor.values()) for (var guard : guards) {
                Color painted = guard.apply(palette.color(slot), palette.color(against), ink);
                assertThat(guard.holds(painted, palette.color(against))).as("%s: %s on %s, %s", theme.name(), slot, against, guard).isTrue();
            }
        }
    }

    @Test void aGuardThatHoldsLeavesTheColourAsItIs() {
        var translucent = new Color(0x10, 0x20, 0x30, 0x80);
        assertThat(SurfaceContrast.Guard.TEXT.apply(translucent, Color.WHITE, Color.BLACK)).isSameAs(translucent);
        assertThat(SurfaceContrast.Guard.apart(3).apply(Color.BLACK, Color.WHITE, Color.BLACK)).isSameAs(Color.BLACK);
    }
```

- [ ] **Step 2: Run the tests to verify they fail**

Run: `./gradlew :jasper-ui:test --tests 'dev.jasper.ui.theme.SurfaceContrastTest'`
Expected: FAIL in `:jasper-ui:compileTestJava` with `error: cannot find symbol` / `symbol: class Guard`.

- [ ] **Step 3: Add the floors and the guard**

```bash
python3 -I - <<'PY'
import pathlib
path = pathlib.Path("jasper-ui/src/main/java/dev/jasper/ui/theme/SurfaceContrast.java")
text = path.read_text(encoding="utf-8")
EDITS = [
(""" * {@link #on} applies a floor only where a role is painted; the resolved role itself is never changed, so shared
 * roles stay as resolved on every other surface. Pure: no Swing state.
 */""",
""" * {@link #on} applies a floor only where a role is painted; the resolved role itself is never changed, so shared
 * roles stay as resolved on every other surface. A {@link Guard} is the repair a face applies where it paints a pair.
 * Pure: no Swing state.
 */"""),
("""    /** How far apart in CIE L* an inactive tab's surface stays from the active tab's. */
    public static final double TAB_LIGHTNESS = 9;
    private SurfaceContrast() {}
""",
"""    /** How far apart in CIE L* an inactive tab's surface stays from the active tab's. */
    public static final double TAB_LIGHTNESS = 9;
    /** Structural boundaries (window and panel rules, dividers) on the surface they divide. */
    public static final double STRUCTURAL = 1.3;
    /** Control boundaries (field, button and box edges) on the surface the control stands on. */
    public static final double CONTROL = 1.5;
    /** The highest contrast every opaque colour reaches against black or white, so the highest floor a guard takes. */
    public static final double REACHABLE = 4.58;
    /** The largest CIE L* distance {@link #apart} always reaches. */
    public static final double REACHABLE_LIGHTNESS = 50;
    private SurfaceContrast() {}

    /**
     * A repair applied where a pair is painted: a contrast floor ({@link #TEXT}, {@link #INDICATOR}, {@link #STRUCTURAL},
     * {@link #CONTROL}) or a CIE L* distance ({@link #apart(double)}). Every guard terminates on any opaque colours.
     *
     * @param kind whether {@code floor} is a contrast ratio or a lightness distance
     * @param floor the ratio (1 to {@link SurfaceContrast#REACHABLE}) or the distance (0 to
     *     {@link SurfaceContrast#REACHABLE_LIGHTNESS})
     */
    public record Guard(Kind kind, double floor) {
        /** What a guard's floor measures. */
        public enum Kind {
            /** The WCAG contrast ratio of the painted colour and the colour it stands on. */
            CONTRAST,
            /** The CIE L* distance between the painted surface and the one it stands apart from. */
            APART
        }

        /** Text and status text. */
        public static final Guard TEXT = new Guard(Kind.CONTRAST, SurfaceContrast.TEXT);
        /** Non-text indicators: a checked box, a radio dot, a status dot, a swatch. */
        public static final Guard INDICATOR = new Guard(Kind.CONTRAST, SurfaceContrast.INDICATOR);
        /** Structural boundaries. */
        public static final Guard STRUCTURAL = new Guard(Kind.CONTRAST, SurfaceContrast.STRUCTURAL);
        /** Control boundaries. */
        public static final Guard CONTROL = new Guard(Kind.CONTRAST, SurfaceContrast.CONTROL);

        /** A floor that every opaque colour can reach. */
        public Guard {
            Objects.requireNonNull(kind, "kind");
            double limit = kind == Kind.CONTRAST ? REACHABLE : REACHABLE_LIGHTNESS;
            if (!(floor >= (kind == Kind.CONTRAST ? 1 : 0) && floor <= limit))
                throw new IllegalArgumentException(kind + " floor out of range: " + floor);
        }

        /** Surfaces kept {@code lightness} CIE L* apart, as an {@code apart} recipe names. */
        public static Guard apart(double lightness) { return new Guard(Kind.APART, lightness); }

        /**
         * {@code color} as painted against {@code against}: unchanged when it holds; otherwise a contrast guard moves it
         * toward {@code toward}, then black or white, and an apart guard moves it as {@link SurfaceContrast#apart} does.
         */
        public Color apply(Color color, Color against, Color toward) {
            if (holds(color, against)) return color;
            return switch (kind) {
                case CONTRAST -> readable(color, against, floor, toward);
                case APART -> SurfaceContrast.apart(color, against, floor);
            };
        }

        /** Whether {@code color} meets this guard against {@code against}. */
        public boolean holds(Color color, Color against) {
            return switch (kind) {
                case CONTRAST -> ColorMath.contrast(color, against) >= floor;
                case APART -> Math.abs(ColorMath.lightness(color) - ColorMath.lightness(against)) >= floor;
            };
        }
    }
"""),
]
for old, new in EDITS:
    if text.count(old) != 1: raise SystemExit(f"SurfaceContrast.java: expected one {old!r}")
    text = text.replace(old, new)
path.write_text(text, encoding="utf-8")
PY
```

`Guard.apply` returns `SurfaceContrast.on`'s colour for the same floor (both keep a colour that holds and otherwise call
`readable`), so a face that later paints through a guard paints what the default painters paint today; Task 4 proves
it over every palette.

- [ ] **Step 4: Run the tests to verify they pass**

Run: `./gradlew :jasper-ui:test --tests 'dev.jasper.ui.theme.SurfaceContrastTest'`
Expected: PASS (10 tests).

- [ ] **Step 5: Commit**

```bash
git add jasper-ui/src/main/java/dev/jasper/ui/theme/SurfaceContrast.java jasper-ui/src/test/java/dev/jasper/ui/theme/SurfaceContrastTest.java
git commit -F - <<'EOF'
Derived roles: paint-time guards in SurfaceContrast

SurfaceContrast gains the structural and control boundary floors and
Guard, the repair a face declares for a pair it paints. A guard takes only
a floor every opaque colour reaches, so it always terminates and holds.

🤖 Generated with Claude Code at The Home Depot

Co-Authored-By: Claude <noreply@anthropic.com>
EOF
```

### Task 3: Override keys: DerivedColor and the parse table

`DerivedColor` names spec 7.1's 23 derived roles, eight of them overridable (the starred ones). A theme variant may
list an overridable role's colour; `JasperTheme` reads those keys through its own table (`DERIVED_OVERRIDES`),
consulted before `SemanticColor.of`, into `GeneratedTheme.derivedOverrides()`, and `TitleBarTheme` carries them to
the header's theme (spec 7.2). Every existing file still loads, an unknown key still fails, and the semantic palette
is untouched. Task 4 computes the roles.

**Files:**
- Create: `jasper-ui/src/main/java/dev/jasper/ui/theme/DerivedColor.java`
- Modify: `jasper-ui/src/main/java/dev/jasper/ui/theme/GeneratedTheme.java` (whole file)
- Modify: `jasper-ui/src/main/java/dev/jasper/ui/theme/JasperTheme.java:29-31` (class comment), `:49-51` (after `OPTIONAL`), `:53-54` (after `Variant`), `:169-203` (`build` and `palette`)
- Modify: `jasper-ui/src/main/java/dev/jasper/ui/theme/TitleBarTheme.java:6-11` (class comment), `:52-53` (the copy)
- Modify (each construction of `GeneratedTheme` passes the overrides): `jasper-ui/src/testFixtures/java/dev/jasper/ui/testsupport/KitFixtures.java` (three, after Task 1), `jasper-ui/src/test/java/dev/jasper/ui/theme/UiColorKeysTest.java:98-99`, `jasper-app/src/test/java/dev/jasper/app/appearance/ThemeTestSupport.java:77-95` (four), `jasper-app/src/test/java/dev/jasper/app/plugins/WorkspaceAppearanceFixture.java:49-51`, `jasper-app/src/test/java/dev/jasper/app/plugins/HostedPalettesTest.java:23-24`, `jasper-app/src/test/java/dev/jasper/app/plugins/HostedKitTest.java:167-168`
- Test: `jasper-ui/src/test/java/dev/jasper/ui/theme/DerivedColorTest.java` (new), `jasper-ui/src/test/java/dev/jasper/ui/theme/GeneratedThemeTest.java` (new), `jasper-ui/src/test/java/dev/jasper/ui/theme/JasperThemeTest.java` (a helper and two tests), `jasper-ui/src/test/java/dev/jasper/ui/theme/TitleBarThemeTest.java` (one test)

**Interfaces:**
- Consumes: `KitFixtures.light()` (existing).
- Produces: `public enum DerivedColor` with the constants `SURFACE_HEADER`, `SURFACE_HEADER_ACTIVE`, `SURFACE_STRIPE`,
  `SURFACE_TABLE_HEADER`, `SURFACE_ZEBRA`, `SURFACE_ROW_HOVER`, `WARNING_GROUND`, `WARNING_BORDER` (overridable),
  `DANGER_GROUND`, `DANGER_BORDER`, `INFO_GROUND`, `INFO_BORDER`, `SURFACE_FOOTER`, `BORDER_DIVIDER`, `BORDER_ROW`,
  `BORDER_HOVER`, `ACCENT_EDGE`, `BUTTON_END`, `TAG_BORDER_BLUE`, `TAG_BORDER_GREEN`, `TAG_BORDER_AMBER`,
  `TAG_BORDER_PURPLE`, `TAG_BORDER_NEUTRAL`, in that order, with `String key()` (spec 7.1's names, such as
  `"surface.tableHeader"`) and `boolean overridable()`; `GeneratedTheme`'s ninth component `Map<DerivedColor, Color>
  derivedOverrides()` (copied; the canonical constructor throws `IllegalArgumentException("Not an override key:
  <key>")` for a role that is not overridable and `IllegalArgumentException("Translucent override: <key>")` for a
  colour with alpha); `static final Map<String, DerivedColor> JasperTheme.DERIVED_OVERRIDES` (package-private, the
  eight keys); parse errors `ui.<variant>.<key>: Expected a colour` and `ui.<variant>.<key>: Expected #RRGGBB, got
  "<text>"`.

- [ ] **Step 1: Write the failing tests**

Create `jasper-ui/src/test/java/dev/jasper/ui/theme/DerivedColorTest.java` with:

```java
package dev.jasper.ui.theme;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import org.junit.jupiter.api.Test;

/** Spec 7.1: the derived roles, in the order they are computed, and the eight a variant may author. */
class DerivedColorTest {
    @Test void theRolesAreSpecSevenOnesAndTheEightStarredOnesAreOverridable() {
        assertThat(Arrays.stream(DerivedColor.values()).map(DerivedColor::key)).containsExactly("surface.header",
            "surface.header.active", "surface.stripe", "surface.tableHeader", "surface.zebra", "surface.rowHover", "warning.ground",
            "warning.border", "danger.ground", "danger.border", "info.ground", "info.border", "surface.footer", "border.divider",
            "border.row", "border.hover", "accent.edge", "button.end", "tag.border.blue", "tag.border.green", "tag.border.amber",
            "tag.border.purple", "tag.border.neutral");
        assertThat(Arrays.stream(DerivedColor.values()).filter(DerivedColor::overridable).map(DerivedColor::key)).containsExactly(
            "surface.header", "surface.header.active", "surface.stripe", "surface.tableHeader", "surface.zebra", "surface.rowHover",
            "warning.ground", "warning.border");
        for (var role : DerivedColor.values())
            assertThat(SemanticColor.of(role.key())).as("%s is not a semantic role", role.key()).isEmpty();
    }
}
```

Create `jasper-ui/src/test/java/dev/jasper/ui/theme/GeneratedThemeTest.java` with:

```java
package dev.jasper.ui.theme;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.jasper.ui.testsupport.KitFixtures;
import java.awt.Color;
import java.util.EnumMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** A generated variant carries the derived roles it authors (spec 7.2): only the overridable ones, opaque, copied. */
class GeneratedThemeTest {
    private static GeneratedTheme with(Map<DerivedColor, Color> overrides) {
        var base = KitFixtures.light();
        return new GeneratedTheme(base.name(), base.dark(), base.author(), base.semantic(), base.terminal(), base.editor(),
            base.syntax(), base.glass(), overrides);
    }

    @Test void aGeneratedThemeTakesOnlyOpaqueOverridesOfTheOverridableRoles() {
        assertThatThrownBy(() -> with(Map.of(DerivedColor.BORDER_DIVIDER, Color.RED)))
            .isInstanceOf(IllegalArgumentException.class).hasMessage("Not an override key: border.divider");
        assertThatThrownBy(() -> with(Map.of(DerivedColor.SURFACE_HEADER, new Color(1, 2, 3, 4))))
            .isInstanceOf(IllegalArgumentException.class).hasMessage("Translucent override: surface.header");
        assertThatThrownBy(() -> with(null)).isInstanceOf(NullPointerException.class);
    }

    @Test void theOverridesAreCopiedAndImmutable() {
        var overrides = new EnumMap<DerivedColor, Color>(DerivedColor.class);
        overrides.put(DerivedColor.SURFACE_HEADER, Color.RED);
        var theme = with(overrides);
        overrides.clear();
        assertThat(theme.derivedOverrides()).containsOnlyKeys(DerivedColor.SURFACE_HEADER);
        assertThatThrownBy(() -> theme.derivedOverrides().put(DerivedColor.SURFACE_ZEBRA, Color.BLUE))
            .isInstanceOf(UnsupportedOperationException.class);
        assertThat(KitFixtures.light().derivedOverrides()).as("Slate Cobalt authors none").isEmpty();
    }
}
```

In `jasper-ui/src/test/java/dev/jasper/ui/theme/JasperThemeTest.java`, insert before the class's closing brace (the
last line):

```java

    /** Slate Cobalt with its Light variant listing two override keys after its roles. */
    static String withOverrides() throws Exception {
        return bundledJson().replace("\"hue.neutral\": \"#5A6270\"",
            "\"hue.neutral\": \"#5A6270\", \"surface.tableHeader\": \"#F0F1F3\", \"warning.ground\": \"#FBF1DA\"");
    }

    @Test void aVariantMayListOverrideKeysWhichAreDerivedRolesNotSemanticOnes() throws Exception {
        var theme = JasperTheme.parse(withOverrides(), "t");
        assertThat(theme.generated(false).derivedOverrides()).containsOnly(
            entry(DerivedColor.SURFACE_TABLE_HEADER, new Color(0xF0F1F3)), entry(DerivedColor.WARNING_GROUND, new Color(0xFBF1DA)));
        assertThat(theme.generated(true).derivedOverrides()).as("the other variant lists none").isEmpty();
        assertThat(theme.palette(false)).as("the semantic palette is untouched").isEqualTo(JasperTheme.bundled().palette(false));
        assertThat(JasperTheme.DERIVED_OVERRIDES).hasSize(8).allSatisfy((key, role) -> {
            assertThat(role.key()).isEqualTo(key);
            assertThat(role.overridable()).isTrue();
        });
    }

    @Test void anOverrideKeyTakesAnOpaqueColourAndOnlyTheEightAreKeys() throws Exception {
        String json = withOverrides();
        assertThat(failure(json.replace("\"#F0F1F3\"", "\"#F0F1F380\""))).hasMessageContaining("ui.light.surface.tableHeader: Expected #RRGGBB, got \"#F0F1F380\"");
        assertThat(failure(json.replace("\"#F0F1F3\"", "3"))).hasMessageContaining("ui.light.surface.tableHeader: Expected a colour");
        assertThat(failure(json.replace("\"surface.tableHeader\"", "\"border.divider\""))).as("a derived role without an override key")
            .hasMessageContaining("ui.light: Unknown colour \"border.divider\"");
        assertThat(failure(json.replace("\"surface.tableHeader\"", "\"surface.tableHeaders\""))).hasMessageContaining("ui.light: Unknown colour \"surface.tableHeaders\"");
    }
```

In `jasper-ui/src/test/java/dev/jasper/ui/theme/TitleBarThemeTest.java`, insert before the class's closing brace (line
46):

```java

    @Test void theHeaderThemeCarriesTheDerivedRolesTheVariantAuthors() throws Exception {
        var light = JasperTheme.parse(JasperThemeTest.withOverrides().replace("\"accent\": \"#2563C9\"",
            "\"surface.titlebar\": \"#27282E\", \"text.titlebar\": \"#DFE1E5\", \"accent\": \"#2563C9\""), "header").generated(false);
        var header = TitleBarTheme.of(light);
        assertThat(header).isNotSameAs(light);
        assertThat(header.derivedOverrides()).isEqualTo(light.derivedOverrides()).isNotEmpty();
    }
```

- [ ] **Step 2: Run the tests to verify they fail**

Run: `./gradlew :jasper-ui:test --tests 'dev.jasper.ui.theme.*'`
Expected: FAIL in `:jasper-ui:compileTestJava` with `error: cannot find symbol` / `symbol: class DerivedColor` and
`symbol: method derivedOverrides()`.

- [ ] **Step 3: Add the derived roles' names**

Create `jasper-ui/src/main/java/dev/jasper/ui/theme/DerivedColor.java` with:

```java
package dev.jasper.ui.theme;

/**
 * Colours the classic shapes paint that a theme does not list, each computed from the variant's palette. Host-private:
 * {@link SemanticPalette} does not hold them and the SDK does not see them. A variant may list an
 * {@linkplain #overridable() overridable} role's colour itself ({@link GeneratedTheme#derivedOverrides()}). Declared in
 * the order they are computed: a role is computed from semantic roles and the derived roles declared before it.
 */
public enum DerivedColor {
    SURFACE_HEADER("surface.header", true),
    SURFACE_HEADER_ACTIVE("surface.header.active", true),
    SURFACE_STRIPE("surface.stripe", true),
    SURFACE_TABLE_HEADER("surface.tableHeader", true),
    SURFACE_ZEBRA("surface.zebra", true),
    SURFACE_ROW_HOVER("surface.rowHover", true),
    WARNING_GROUND("warning.ground", true),
    WARNING_BORDER("warning.border", true),
    DANGER_GROUND("danger.ground", false),
    DANGER_BORDER("danger.border", false),
    INFO_GROUND("info.ground", false),
    INFO_BORDER("info.border", false),
    SURFACE_FOOTER("surface.footer", false),
    BORDER_DIVIDER("border.divider", false),
    BORDER_ROW("border.row", false),
    BORDER_HOVER("border.hover", false),
    ACCENT_EDGE("accent.edge", false),
    BUTTON_END("button.end", false),
    TAG_BORDER_BLUE("tag.border.blue", false),
    TAG_BORDER_GREEN("tag.border.green", false),
    TAG_BORDER_AMBER("tag.border.amber", false),
    TAG_BORDER_PURPLE("tag.border.purple", false),
    TAG_BORDER_NEUTRAL("tag.border.neutral", false);

    private final String key;
    private final boolean overridable;

    DerivedColor(String key, boolean overridable) {
        this.key = key;
        this.overridable = overridable;
    }

    /** The case-sensitive key theme files and frozen tables name the role by. */
    public String key() { return key; }

    /** Whether a theme variant may list this role (an override key) instead of taking its computed colour. */
    public boolean overridable() { return overridable; }
}
```

- [ ] **Step 4: Carry the overrides on the generated variant**

Replace the whole of `jasper-ui/src/main/java/dev/jasper/ui/theme/GeneratedTheme.java` with:

```java
package dev.jasper.ui.theme;

import java.awt.Color;
import java.util.Map;
import java.util.Objects;

/**
 * A theme variant with every value filled in: the semantic palette, the matching terminal palette, the editor scheme and
 * its syntax styles, the workspace glass and the derived roles the variant authors itself. Immutable and Swing-free.
 * FlatLaf's UI keys are derived from it when it is installed ({@link UiColorKeys#flatLaf(GeneratedTheme, UiKeySet)}).
 *
 * @param derivedOverrides the {@linkplain DerivedColor#overridable() overridable} derived roles the variant lists, opaque
 */
public record GeneratedTheme(String name, boolean dark, String author, SemanticPalette semantic, TerminalColors terminal,
                             EditorColors editor, Map<SyntaxRole, SyntaxStyle> syntax, GlassLevels glass,
                             Map<DerivedColor, Color> derivedOverrides) {
    public GeneratedTheme {
        Objects.requireNonNull(name); Objects.requireNonNull(author); Objects.requireNonNull(semantic);
        Objects.requireNonNull(terminal); Objects.requireNonNull(editor); Objects.requireNonNull(glass);
        syntax = Map.copyOf(syntax);
        derivedOverrides = Map.copyOf(derivedOverrides);
        derivedOverrides.forEach((role, color) -> {
            if (!role.overridable()) throw new IllegalArgumentException("Not an override key: " + role.key());
            if (color.getAlpha() != 255) throw new IllegalArgumentException("Translucent override: " + role.key());
        });
    }
}
```

- [ ] **Step 5: Read the override keys and carry them to the header**

```bash
python3 -I - <<'PY'
import pathlib
def edit(name, edits):
    path = pathlib.Path(name)
    text = path.read_text(encoding="utf-8")
    for old, new, count in edits:
        if text.count(old) != count: raise SystemExit(f"{name}: expected {count} of {old!r}, found {text.count(old)}")
        text = text.replace(old, new)
    path.write_text(text, encoding="utf-8")

edit("jasper-ui/src/main/java/dev/jasper/ui/theme/JasperTheme.java", [
(""" * {@code text.primary}. A variant's matching terminal scheme is""",
 """ * {@code text.primary}. A variant may also list the {@linkplain DerivedColor#overridable() overridable} derived roles,
 * opaque ({@link GeneratedTheme#derivedOverrides()}). A variant's matching terminal scheme is""", 1),
("""    private static final Map<SemanticColor, SemanticColor> OPTIONAL = Map.of(SemanticColor.SURFACE_TITLEBAR, SemanticColor.SURFACE_APP,
        SemanticColor.TEXT_TITLEBAR, SemanticColor.TEXT_PRIMARY);
""",
 """    private static final Map<SemanticColor, SemanticColor> OPTIONAL = Map.of(SemanticColor.SURFACE_TITLEBAR, SemanticColor.SURFACE_APP,
        SemanticColor.TEXT_TITLEBAR, SemanticColor.TEXT_PRIMARY);
    /** The override keys a variant may list, read before the roles: the overridable derived roles, by key. */
    static final Map<String, DerivedColor> DERIVED_OVERRIDES = java.util.Arrays.stream(DerivedColor.values())
        .filter(DerivedColor::overridable).collect(java.util.stream.Collectors.toUnmodifiableMap(DerivedColor::key, role -> role));
""", 1),
("""    private record Variant(SemanticPalette palette, EditorColors editor, Map<SyntaxRole, SyntaxStyle> syntax, GeneratedTheme generated) { }
""",
 """    private record Variant(SemanticPalette palette, EditorColors editor, Map<SyntaxRole, SyntaxStyle> syntax, GeneratedTheme generated) { }
    /** A variant's roles and the derived roles it authors, as read. */
    private record Colours(SemanticPalette palette, Map<DerivedColor, Color> overrides) { }
""", 1),
("""    private Variant build(boolean dark) {
        var palette = palette(dark ? DARK : LIGHT);
""",
 """    private Variant build(boolean dark) {
        var read = colours(dark ? DARK : LIGHT);
        var palette = read.palette();
""", 1),
("""        var generated = new GeneratedTheme(name, dark, author, palette, scheme.colors(), colors, syntax, GlassLevels.OPAQUE);
""",
 """        var generated = new GeneratedTheme(name, dark, author, palette, scheme.colors(), colors, syntax, GlassLevels.OPAQUE,
            read.overrides());
""", 1),
("""    private SemanticPalette palette(String variant) {
        @SuppressWarnings("unchecked") var values = (Map<String, Object>) ui.get(variant);
        String where = "ui." + variant;
        var colors = new EnumMap<SemanticColor, Color>(SemanticColor.class);
        values.forEach((key, value) -> {
""",
 """    private Colours colours(String variant) {
        @SuppressWarnings("unchecked") var values = (Map<String, Object>) ui.get(variant);
        String where = "ui." + variant;
        var colors = new EnumMap<SemanticColor, Color>(SemanticColor.class);
        var overrides = new EnumMap<DerivedColor, Color>(DerivedColor.class);
        values.forEach((key, value) -> {
            var derived = DERIVED_OVERRIDES.get(key);
            if (derived != null) {
                if (!(value instanceof String text)) throw error(where + "." + key + ": Expected a colour");
                if (!RGB.matcher(text).matches()) throw error(where + "." + key + ": Expected #RRGGBB, got \\"" + text + "\\"");
                overrides.put(derived, color(text, origin, where + "." + key));
                return;
            }
""", 1),
("""        colors.put(SemanticColor.HUE_NEUTRAL_SURFACE, colors.get(SemanticColor.HUE_NEUTRAL));
        return new SemanticPalette(colors);
    }
""",
 """        colors.put(SemanticColor.HUE_NEUTRAL_SURFACE, colors.get(SemanticColor.HUE_NEUTRAL));
        return new Colours(new SemanticPalette(colors), overrides);
    }
""", 1),
])

edit("jasper-ui/src/main/java/dev/jasper/ui/theme/TitleBarTheme.java", [
(""" * the search field's fill and borders, and the theme is dark when the title bar is. Everything else is the theme's.
 */""",
 """ * the search field's fill and borders, and the theme is dark when the title bar is. Everything else is the theme's,
 * the derived roles it authors included.
 */""", 1),
("""            theme.syntax(), theme.glass());""", """            theme.syntax(), theme.glass(), theme.derivedOverrides());""", 1),
])

edit("jasper-ui/src/testFixtures/java/dev/jasper/ui/testsupport/KitFixtures.java", [
("base.glass());", "base.glass(), base.derivedOverrides());", 3)])
edit("jasper-ui/src/test/java/dev/jasper/ui/theme/UiColorKeysTest.java", [
("base.syntax(), base.glass());", "base.syntax(), base.glass(), base.derivedOverrides());", 1)])
edit("jasper-app/src/test/java/dev/jasper/app/appearance/ThemeTestSupport.java", [
("base.glass());", "base.glass(), base.derivedOverrides());", 3),
("base.syntax(), glass);", "base.syntax(), glass, base.derivedOverrides());", 1)])
edit("jasper-app/src/test/java/dev/jasper/app/plugins/WorkspaceAppearanceFixture.java", [
("base.syntax(), base.glass());", "base.syntax(), base.glass(), base.derivedOverrides());", 1)])
edit("jasper-app/src/test/java/dev/jasper/app/plugins/HostedPalettesTest.java", [
("base.syntax(), base.glass());", "base.syntax(), base.glass(), base.derivedOverrides());", 1)])
edit("jasper-app/src/test/java/dev/jasper/app/plugins/HostedKitTest.java", [
("recoloured.syntax(),recoloured.glass());", "recoloured.syntax(),recoloured.glass(),recoloured.derivedOverrides());", 1)])
PY
grep -rn --include='*.java' -E 'new (dev\.jasper\.ui\.theme\.)?GeneratedTheme\(' jasper-ui/src jasper-app/src
```

Expected: the grep lists fourteen constructions (two in production, twelve in tests with `GeneratedThemeTest`'s), each
now passing the overrides; the test copies keep their base's overrides (`withSemantic`, `withDark` and `withName` in `KitFixtures`; `recolored`,
`withSyntax`, `withGlass` and `withDark` in `ThemeTestSupport`). The key is read before `SemanticColor.of`, so a
semantic key is never an override and a derived role without an override key is still "Unknown colour".

- [ ] **Step 6: Run the tests to verify they pass**

Run: `./gradlew :jasper-ui:test :jasper-app:test --tests 'dev.jasper.app.plugins.HostedPalettesTest' --tests 'dev.jasper.app.plugins.HostedKitTest' --tests 'dev.jasper.app.plugins.*Workspace*' --tests 'dev.jasper.app.appearance.*'`
Expected: BUILD SUCCESSFUL: every jasper-ui test (with `DerivedColorTest`, `GeneratedThemeTest`, the two
`JasperThemeTest` and the `TitleBarThemeTest` additions) and the named jasper-app tests pass.

- [ ] **Step 7: Commit**

```bash
git add jasper-ui/src/main/java/dev/jasper/ui/theme jasper-ui/src/test/java/dev/jasper/ui/theme \
  jasper-ui/src/testFixtures/java/dev/jasper/ui/testsupport/KitFixtures.java \
  jasper-app/src/test/java/dev/jasper/app/appearance/ThemeTestSupport.java \
  jasper-app/src/test/java/dev/jasper/app/plugins/WorkspaceAppearanceFixture.java \
  jasper-app/src/test/java/dev/jasper/app/plugins/HostedPalettesTest.java jasper-app/src/test/java/dev/jasper/app/plugins/HostedKitTest.java
git commit -F - <<'EOF'
Derived roles: override keys, DerivedColor and the parse table

DerivedColor names spec 7.1's 23 derived roles, eight of them overridable.
JasperTheme reads those keys through its own table into
GeneratedTheme.derivedOverrides, opaque only, and TitleBarTheme carries
them to the header's theme. Every existing file loads as before.

🤖 Generated with Claude Code at The Home Depot

Co-Authored-By: Claude <noreply@anthropic.com>
EOF
```

### Task 4: DerivedPalette: the recipes, ColorRole, PaintPair and painted

`DerivedPalette.of(theme)` computes every derived role in declaration order: the variant's override when it lists one,
else spec 7.1's recipe over the palette and the derived roles before it. `ColorRole` lets a pair name either kind of
role; `PaintPair` is a face's declaration and `SurfaceContrast.painted` paints it through its guard. The recipes are
calibrated: Slate Cobalt Light gives exactly the three values spec 7.1 records. `DerivedColorTest` checks the static
closure (spec 16.1.1): every recipe reads spec 7.1's inputs, only semantic roles and earlier derived roles, over every
palette. Nothing in the default look reads a derived role.

**Files:**
- Create: `jasper-ui/src/main/java/dev/jasper/ui/theme/ColorRole.java`, `jasper-ui/src/main/java/dev/jasper/ui/theme/PaintPair.java`, `jasper-ui/src/main/java/dev/jasper/ui/theme/DerivedPalette.java`
- Modify: `jasper-ui/src/main/java/dev/jasper/ui/theme/DerivedColor.java` (Task 3: `implements ColorRole`, the class comment, `@Override`)
- Modify: `jasper-ui/src/main/java/dev/jasper/ui/theme/SemanticColor.java:9` (`implements ColorRole`), `:83-84` (`@Override`)
- Modify: `jasper-ui/src/main/java/dev/jasper/ui/theme/SurfaceContrast.java` (Task 2's class comment; `painted` after `Guard`)
- Modify: `jasper-ui/src/main/java/dev/jasper/ui/theme/package-info.java:2-3`
- Modify: `docs/themes.md:125-129` (the override keys, after the `ui` bullet)
- Test: `jasper-ui/src/test/java/dev/jasper/ui/theme/DerivedColorTest.java` (whole file), `jasper-ui/src/test/java/dev/jasper/ui/theme/DerivedPaletteTest.java` (new)

**Interfaces:**
- Consumes: `DerivedColor`, `GeneratedTheme.derivedOverrides()` (Task 3); `SurfaceContrast.Guard`,
  `SurfaceContrast.STRUCTURAL`, `CONTROL` (Task 2); `KitFixtures.themes()`, `degenerate()`, `withDark`, `withSemantic`,
  `marked()` (Task 1); `TitleBarThemeTest.darkHeader()` (existing, package-private).
- Produces: `public sealed interface ColorRole permits SemanticColor, DerivedColor` with `String key()` and `default
  Color color(GeneratedTheme theme)`; `public record PaintPair(ColorRole slot, ColorRole against, SurfaceContrast.Guard
  guard)`; `public final class DerivedPalette` with `static synchronized DerivedPalette of(GeneratedTheme theme)` (kept
  by identity for the last four themes), `Color color(DerivedColor role)`, value `equals`/`hashCode`, and
  package-private `static DerivedPalette derive(SemanticPalette palette, Map<DerivedColor, Color> overrides)` and
  `static Set<ColorRole> inputs(DerivedColor role, SemanticPalette palette)`; `public static Color
  SurfaceContrast.painted(GeneratedTheme theme, PaintPair pair)`.

- [ ] **Step 1: Write the failing tests**

Replace the whole of `jasper-ui/src/test/java/dev/jasper/ui/theme/DerivedColorTest.java` with:

```java
package dev.jasper.ui.theme;

import static dev.jasper.ui.theme.DerivedColor.*;
import static org.assertj.core.api.Assertions.assertThat;

import dev.jasper.ui.testsupport.KitFixtures;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * Spec 7.1's roles and spec 16.1.1's static closure: every recipe reads only semantic roles, which every theme that loads
 * has, and derived roles computed before it, so the order is acyclic; and the roles each reads are spec 7.1's.
 */
class DerivedColorTest {
    /** Spec 7.1: the roles each recipe reads. */
    static final Map<DerivedColor, Set<ColorRole>> INPUTS = inputs();

    private static Map<DerivedColor, Set<ColorRole>> inputs() {
        var inputs = new EnumMap<DerivedColor, Set<ColorRole>>(DerivedColor.class);
        Set<ColorRole> onApp = Set.of(SemanticColor.TEXT_PRIMARY, SemanticColor.SURFACE_APP);
        Set<ColorRole> onCard = Set.of(SemanticColor.TEXT_PRIMARY, SemanticColor.SURFACE_CARD);
        inputs.put(SURFACE_HEADER, onApp);
        inputs.put(SURFACE_HEADER_ACTIVE, Set.of(SemanticColor.ACCENT, SemanticColor.SURFACE_APP, SURFACE_HEADER));
        inputs.put(SURFACE_STRIPE, onApp);
        inputs.put(SURFACE_TABLE_HEADER, onCard);
        inputs.put(SURFACE_ZEBRA, Set.of(SemanticColor.TEXT_PRIMARY, SemanticColor.SURFACE_CARD, SURFACE_TABLE_HEADER));
        inputs.put(SURFACE_ROW_HOVER, Set.of(SemanticColor.ACCENT, SemanticColor.SURFACE_CARD));
        inputs.put(WARNING_GROUND, Set.of(SemanticColor.STATUS_WARNING, SemanticColor.SURFACE_CARD));
        inputs.put(WARNING_BORDER, Set.of(SemanticColor.STATUS_WARNING, SemanticColor.SURFACE_CARD));
        inputs.put(DANGER_GROUND, Set.of(SemanticColor.STATUS_DANGER, SemanticColor.SURFACE_CARD));
        inputs.put(DANGER_BORDER, Set.of(SemanticColor.STATUS_DANGER, SemanticColor.SURFACE_CARD));
        inputs.put(INFO_GROUND, Set.of(SemanticColor.ACCENT, SemanticColor.SURFACE_CARD));
        inputs.put(INFO_BORDER, Set.of(SemanticColor.ACCENT, SemanticColor.SURFACE_CARD));
        inputs.put(SURFACE_FOOTER, Set.of(SemanticColor.TEXT_PRIMARY, SemanticColor.SURFACE_DIALOG));
        inputs.put(BORDER_DIVIDER, Set.of(SemanticColor.BORDER_WINDOW, SemanticColor.SURFACE_CARD, SemanticColor.TEXT_PRIMARY));
        inputs.put(BORDER_ROW, Set.of(SemanticColor.BORDER_WINDOW, SemanticColor.SURFACE_CARD, SemanticColor.TEXT_PRIMARY));
        inputs.put(BORDER_HOVER, Set.of(SemanticColor.BORDER_CONTROL, SemanticColor.SURFACE_CARD, SemanticColor.TEXT_PRIMARY));
        inputs.put(ACCENT_EDGE, Set.of(SemanticColor.ACCENT, SemanticColor.SURFACE_CARD));
        inputs.put(BUTTON_END, Set.of(SemanticColor.TEXT_PRIMARY, SemanticColor.BUTTON_SECONDARY_BG));
        inputs.put(TAG_BORDER_BLUE, Set.of(SemanticColor.HUE_BLUE, SemanticColor.SURFACE_CARD));
        inputs.put(TAG_BORDER_GREEN, Set.of(SemanticColor.HUE_GREEN, SemanticColor.SURFACE_CARD));
        inputs.put(TAG_BORDER_AMBER, Set.of(SemanticColor.HUE_AMBER, SemanticColor.SURFACE_CARD));
        inputs.put(TAG_BORDER_PURPLE, Set.of(SemanticColor.HUE_PURPLE, SemanticColor.SURFACE_CARD));
        inputs.put(TAG_BORDER_NEUTRAL, Set.of(SemanticColor.HUE_NEUTRAL, SemanticColor.SURFACE_CARD));
        return inputs;
    }

    @Test void theRolesAreSpecSevenOnesAndTheEightStarredOnesAreOverridable() {
        assertThat(Arrays.stream(DerivedColor.values()).map(DerivedColor::key)).containsExactly("surface.header",
            "surface.header.active", "surface.stripe", "surface.tableHeader", "surface.zebra", "surface.rowHover", "warning.ground",
            "warning.border", "danger.ground", "danger.border", "info.ground", "info.border", "surface.footer", "border.divider",
            "border.row", "border.hover", "accent.edge", "button.end", "tag.border.blue", "tag.border.green", "tag.border.amber",
            "tag.border.purple", "tag.border.neutral");
        assertThat(Arrays.stream(DerivedColor.values()).filter(DerivedColor::overridable).map(DerivedColor::key)).containsExactly(
            "surface.header", "surface.header.active", "surface.stripe", "surface.tableHeader", "surface.zebra", "surface.rowHover",
            "warning.ground", "warning.border");
        for (var role : DerivedColor.values())
            assertThat(SemanticColor.of(role.key())).as("%s is not a semantic role", role.key()).isEmpty();
    }

    @Test void everyRecipeReadsItsSpecInputsOnlySemanticRolesAndDerivedRolesBeforeItOverEveryPalette() {
        var palettes = new ArrayList<SemanticPalette>();
        for (var theme : KitFixtures.themes()) palettes.add(theme.semantic());
        for (var theme : KitFixtures.degenerate()) palettes.add(theme.semantic());
        palettes.add(KitFixtures.marked().semantic());
        assertThat(INPUTS).hasSize(DerivedColor.values().length);
        for (var palette : palettes) for (var role : DerivedColor.values()) {
            var inputs = DerivedPalette.inputs(role, palette);
            assertThat(inputs).as(role.name()).containsExactlyInAnyOrderElementsOf(INPUTS.get(role));
            for (var input : inputs)
                if (input instanceof DerivedColor derived) assertThat(derived.ordinal()).as(role + " reads " + derived).isLessThan(role.ordinal());
        }
    }
}
```

Create `jasper-ui/src/test/java/dev/jasper/ui/theme/DerivedPaletteTest.java` with:

```java
package dev.jasper.ui.theme;

import static dev.jasper.ui.theme.DerivedColor.*;
import static org.assertj.core.api.Assertions.assertThat;

import dev.jasper.ui.testsupport.KitFixtures;
import java.awt.Color;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** Spec 7.1: the derived roles' recipes, their overrides, their cache and how a pair is painted through its guard. */
class DerivedPaletteTest {
    static List<GeneratedTheme> palettes() {
        var palettes = new ArrayList<>(KitFixtures.themes());
        palettes.addAll(KitFixtures.degenerate());
        return palettes;
    }

    private static double apart(Color a, Color b) { return Math.abs(ColorMath.lightness(a) - ColorMath.lightness(b)); }

    @Test void slateCobaltLightMeetsTheDesignCalibration() {
        var light = DerivedPalette.of(JasperTheme.bundled().generated(false));
        assertThat(light.color(SURFACE_HEADER)).as("header strip, against #E1E3E6").isEqualTo(new Color(0xDFE2E6));
        assertThat(light.color(SURFACE_TABLE_HEADER)).as("table header, against #F0F1F3").isEqualTo(new Color(0xF1F2F2));
        assertThat(light.color(SURFACE_ROW_HOVER)).as("row hover, against #EEF2FA").isEqualTo(new Color(0xEEF3FB));
    }

    /** Every role is opaque, and the recipes keep the distances spec 7.1 names, over every palette. */
    @Test void everyRoleIsOpaqueAndEveryRecipeKeepsItsDistancesOverEveryPalette() {
        for (var theme : palettes()) {
            var palette = theme.semantic();
            var derived = DerivedPalette.derive(palette, Map.of());
            String where = theme.name() + (theme.dark() ? " Dark" : " Light");
            for (var role : DerivedColor.values())
                assertThat(DerivedPalette.of(theme).color(role).getAlpha()).as(where + " " + role).isEqualTo(255);
            Color app = palette.color(SemanticColor.SURFACE_APP), card = palette.color(SemanticColor.SURFACE_CARD);
            assertThat(apart(derived.color(SURFACE_HEADER), app)).as(where + " header").isGreaterThanOrEqualTo(3);
            assertThat(apart(derived.color(SURFACE_HEADER_ACTIVE), derived.color(SURFACE_HEADER))).as(where + " active header")
                .isGreaterThanOrEqualTo(2);
            assertThat(apart(derived.color(SURFACE_STRIPE), app)).as(where + " stripe").isGreaterThanOrEqualTo(1.5);
            assertThat(apart(derived.color(SURFACE_TABLE_HEADER), card)).as(where + " table header").isGreaterThanOrEqualTo(3);
            assertThat(apart(derived.color(SURFACE_ZEBRA), card)).as(where + " zebra").isGreaterThanOrEqualTo(1.5)
                .isLessThan(apart(derived.color(SURFACE_TABLE_HEADER), card));
            assertThat(apart(derived.color(SURFACE_FOOTER), palette.color(SemanticColor.SURFACE_DIALOG))).as(where + " footer")
                .isGreaterThanOrEqualTo(2);
            Color edge = derived.color(ACCENT_EDGE), accent = palette.color(SemanticColor.ACCENT);
            if (!edge.equals(Color.BLACK) && !edge.equals(Color.WHITE))
                assertThat(apart(edge, accent)).as(where + " accent edge").isGreaterThanOrEqualTo(7);
        }
    }

    @Test void derivedPalettesAreKeptByThemeIdentity() {
        var light = JasperTheme.bundled().generated(false);
        assertThat(DerivedPalette.of(light)).isSameAs(DerivedPalette.of(light));
        var copy = KitFixtures.withDark(KitFixtures.withDark(light, true), false);
        assertThat(copy).isNotSameAs(light);
        assertThat(DerivedPalette.of(copy)).as("an equal theme object derives equal roles").isEqualTo(DerivedPalette.of(light));
    }

    @Test void underATitleBarOfItsOwnTheHeaderDerivesFromTheHeaderPalette() throws Exception {
        var light = TitleBarThemeTest.darkHeader().generated(false);
        var header = DerivedPalette.of(TitleBarTheme.of(light));
        Color bar = new Color(0x27282E), ink = new Color(0xDFE1E5);
        assertThat(header.color(SURFACE_HEADER)).isEqualTo(SurfaceContrast.apart(ColorMath.mix(ink, bar, .05), bar, 3));
        assertThat(header).as("the header palette's text is the title bar's").isNotEqualTo(DerivedPalette.of(light));
        var plain = JasperTheme.bundled().generated(false);
        assertThat(DerivedPalette.of(TitleBarTheme.of(plain))).isSameAs(DerivedPalette.of(plain));
    }

    @Test void aTranslucentInputIsLaidOverTheSurfaceItsRecipeMixesItWith() {
        var base = KitFixtures.light();
        Color card = base.semantic().color(SemanticColor.SURFACE_CARD), app = base.semantic().color(SemanticColor.SURFACE_APP);
        Color border = new Color(0x10, 0x20, 0x30, 0x80), ink = new Color(0x20, 0x10, 0x00, 0x99);
        var translucent = recoloured(base, Map.of(SemanticColor.BORDER_WINDOW, border, SemanticColor.TEXT_PRIMARY, ink));
        var onCard = recoloured(base, Map.of(SemanticColor.BORDER_WINDOW, ColorMath.mix(border, card, 0x80 / 255.0),
            SemanticColor.TEXT_PRIMARY, ColorMath.mix(ink, card, 0x99 / 255.0)));
        var onApp = recoloured(base, Map.of(SemanticColor.TEXT_PRIMARY, ColorMath.mix(ink, app, 0x99 / 255.0)));
        assertThat(DerivedPalette.of(translucent).color(BORDER_DIVIDER)).isEqualTo(DerivedPalette.of(onCard).color(BORDER_DIVIDER));
        assertThat(DerivedPalette.of(translucent).color(SURFACE_TABLE_HEADER)).isEqualTo(DerivedPalette.of(onCard).color(SURFACE_TABLE_HEADER));
        assertThat(DerivedPalette.of(translucent).color(SURFACE_HEADER)).isEqualTo(DerivedPalette.of(onApp).color(SURFACE_HEADER));
        for (var role : DerivedColor.values()) assertThat(DerivedPalette.of(translucent).color(role).getAlpha()).isEqualTo(255);
    }

    @Test void anOverrideReplacesItsRecipeAndTheRecipesAfterItReadIt() {
        var base = KitFixtures.light();
        Color header = new Color(0xE1E3E6), table = new Color(0xFAFAFA);
        var theme = new GeneratedTheme(base.name(), base.dark(), base.author(), base.semantic(), base.terminal(), base.editor(),
            base.syntax(), base.glass(), Map.of(SURFACE_HEADER, header, SURFACE_TABLE_HEADER, table));
        var derived = DerivedPalette.of(theme);
        Color card = base.semantic().color(SemanticColor.SURFACE_CARD);
        assertThat(derived.color(SURFACE_HEADER)).isEqualTo(header);
        assertThat(derived.color(SURFACE_HEADER_ACTIVE)).isEqualTo(SurfaceContrast.apart(
            ColorMath.mix(base.semantic().color(SemanticColor.ACCENT), base.semantic().color(SemanticColor.SURFACE_APP), .10), header, 2));
        assertThat(derived.color(SURFACE_TABLE_HEADER)).isEqualTo(table);
        assertThat(derived.color(SURFACE_ZEBRA)).as("halfway, so it stays closer to the card than the authored header")
            .isEqualTo(ColorMath.mix(table, card, .5));
        assertThat(derived.color(BORDER_DIVIDER)).as("a role after them that reads neither").isEqualTo(DerivedPalette.of(base).color(BORDER_DIVIDER));
        var authored = new GeneratedTheme(base.name(), base.dark(), base.author(), base.semantic(), base.terminal(), base.editor(),
            base.syntax(), base.glass(), Map.of(SURFACE_HEADER, header, SURFACE_HEADER_ACTIVE, header));
        assertThat(DerivedPalette.of(authored).color(SURFACE_HEADER_ACTIVE)).as("the author's colour, not repaired").isEqualTo(header);
    }

    @Test void paintedIsThePairsGuardOverEitherKindOfRole() {
        for (var theme : palettes()) {
            var palette = theme.semantic();
            assertThat(SurfaceContrast.painted(theme, new PaintPair(SemanticColor.TEXT_PLACEHOLDER, SemanticColor.SURFACE_FIELD_RAISED,
                SurfaceContrast.Guard.INDICATOR))).isEqualTo(SurfaceContrast.on(palette, SemanticColor.TEXT_PLACEHOLDER,
                SemanticColor.SURFACE_FIELD_RAISED, SurfaceContrast.INDICATOR));
            assertThat(SurfaceContrast.painted(theme, new PaintPair(SemanticColor.SURFACE_TABSTRIP, SemanticColor.SURFACE_TERMINAL,
                SurfaceContrast.Guard.apart(SurfaceContrast.TAB_LIGHTNESS)))).isEqualTo(SurfaceContrast.apart(
                palette.color(SemanticColor.SURFACE_TABSTRIP), palette.color(SemanticColor.SURFACE_TERMINAL), SurfaceContrast.TAB_LIGHTNESS));
            var warning = new PaintPair(SemanticColor.STATUS_WARNING, WARNING_GROUND, SurfaceContrast.Guard.TEXT);
            assertThat(ColorMath.contrast(SurfaceContrast.painted(theme, warning), DerivedPalette.of(theme).color(WARNING_GROUND)))
                .isGreaterThanOrEqualTo(4.5);
            assertThat(WARNING_GROUND.color(theme)).isEqualTo(DerivedPalette.of(theme).color(WARNING_GROUND));
            assertThat(SemanticColor.ACCENT.color(theme)).isEqualTo(palette.color(SemanticColor.ACCENT));
        }
    }

    private static GeneratedTheme recoloured(GeneratedTheme base, Map<SemanticColor, Color> roles) {
        var values = new EnumMap<SemanticColor, Color>(SemanticColor.class);
        for (var role : SemanticColor.values()) values.put(role, roles.getOrDefault(role, base.semantic().color(role)));
        return KitFixtures.withSemantic(base, new SemanticPalette(values));
    }
}
```

- [ ] **Step 2: Run the tests to verify they fail**

Run: `./gradlew :jasper-ui:test --tests 'dev.jasper.ui.theme.Derived*'`
Expected: FAIL in `:jasper-ui:compileTestJava` with `error: cannot find symbol` / `symbol: class ColorRole`,
`class DerivedPalette` and `class PaintPair`.

- [ ] **Step 3: Add ColorRole and PaintPair**

Create `jasper-ui/src/main/java/dev/jasper/ui/theme/ColorRole.java` with:

```java
package dev.jasper.ui.theme;

import java.awt.Color;

/**
 * A colour a face paints with: a {@link SemanticColor} from the variant's palette or a {@link DerivedColor} computed
 * from it. Pairs a face declares ({@link PaintPair}) name roles of either kind.
 */
public sealed interface ColorRole permits SemanticColor, DerivedColor {
    /** The case-sensitive key theme files and frozen tables name the role by. */
    String key();

    /** The role's colour in {@code theme}: its palette's, or its {@linkplain DerivedPalette derived palette}'s. */
    default Color color(GeneratedTheme theme) {
        return switch (this) {
            case SemanticColor role -> theme.semantic().color(role);
            case DerivedColor role -> DerivedPalette.of(theme).color(role);
        };
    }
}
```

Create `jasper-ui/src/main/java/dev/jasper/ui/theme/PaintPair.java` with:

```java
package dev.jasper.ui.theme;

import java.util.Objects;

/**
 * A pair a face paints, as it declares it: {@code slot} painted on or beside {@code against}, held there by
 * {@code guard} at paint time ({@link SurfaceContrast#painted}). Neither role is changed.
 *
 * @param slot the role painted
 * @param against the role it must stand apart from
 * @param guard the repair applied where it is painted
 */
public record PaintPair(ColorRole slot, ColorRole against, SurfaceContrast.Guard guard) {
    /** Every part is required. */
    public PaintPair {
        Objects.requireNonNull(slot, "slot"); Objects.requireNonNull(against, "against"); Objects.requireNonNull(guard, "guard");
    }
}
```

- [ ] **Step 4: Add the recipes**

Create `jasper-ui/src/main/java/dev/jasper/ui/theme/DerivedPalette.java` with:

```java
package dev.jasper.ui.theme;

import static dev.jasper.ui.theme.SemanticColor.*;

import java.awt.Color;
import java.util.EnumMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;

/**
 * A variant's {@link DerivedColor}s: each the variant's override when it lists one, else its recipe over the palette
 * and the derived roles before it. A pure function of the palette and the overrides; every colour is opaque, and a
 * translucent input is first laid over the surface its recipe mixes it with. Chrome under a title bar of its own derives
 * from {@link TitleBarTheme#of}'s theme, which carries the overrides.
 */
public final class DerivedPalette {
    /** A tint's share of a ground (Notices, pills), and of a border drawn round it. */
    private static final double GROUND = .14, EDGE = .40;
    /** Derived palettes kept, by theme identity: chrome, workspace and the title bar alternate while painting. */
    private static final int KEPT = 4;
    private static final GeneratedTheme[] THEMES = new GeneratedTheme[KEPT];
    private static final DerivedPalette[] PALETTES = new DerivedPalette[KEPT];
    private static int next;

    private final Map<DerivedColor, Color> values;

    private DerivedPalette(Map<DerivedColor, Color> values) { this.values = Map.copyOf(values); }

    /** {@code theme}'s derived roles, computed once per theme object while it is among the last few asked for. */
    public static synchronized DerivedPalette of(GeneratedTheme theme) {
        Objects.requireNonNull(theme, "theme");
        for (int i = 0; i < KEPT; i++) if (THEMES[i] == theme) return PALETTES[i];
        var palette = derive(theme.semantic(), theme.derivedOverrides());
        THEMES[next] = theme;
        PALETTES[next] = palette;
        next = (next + 1) % KEPT;
        return palette;
    }

    /** The colour of {@code role}; opaque. */
    public Color color(DerivedColor role) { return values.get(Objects.requireNonNull(role, "role")); }

    @Override public boolean equals(Object other) { return other instanceof DerivedPalette p && values.equals(p.values); }
    @Override public int hashCode() { return values.hashCode(); }
    @Override public String toString() { return "DerivedPalette" + values; }

    /** Every derived role of {@code palette}, in declaration order: an override when {@code overrides} has one. */
    static DerivedPalette derive(SemanticPalette palette, Map<DerivedColor, Color> overrides) {
        var values = new EnumMap<DerivedColor, Color>(DerivedColor.class);
        for (var role : DerivedColor.values()) {
            Color override = overrides.get(role);
            values.put(role, override != null ? override : recipe(role, input -> read(input, palette, values, role)));
        }
        return new DerivedPalette(values);
    }

    /** The roles {@code role}'s recipe reads over {@code palette}, in the order it reads them. */
    static Set<ColorRole> inputs(DerivedColor role, SemanticPalette palette) {
        var earlier = derive(palette, Map.of());
        var reads = new LinkedHashSet<ColorRole>();
        var values = new EnumMap<DerivedColor, Color>(DerivedColor.class);
        for (var before : DerivedColor.values()) if (before.ordinal() < role.ordinal()) values.put(before, earlier.color(before));
        recipe(role, input -> { reads.add(input); return read(input, palette, values, role); });
        return reads;
    }

    private static Color read(ColorRole input, SemanticPalette palette, Map<DerivedColor, Color> values, DerivedColor reader) {
        return switch (input) {
            case SemanticColor role -> palette.color(role);
            case DerivedColor role -> {
                Color value = values.get(role);
                if (value == null) throw new IllegalStateException(reader + " reads " + role + ", which is computed after it");
                yield value;
            }
        };
    }

    /** {@code role}'s recipe (spec 7.1), reading its inputs through {@code in}. */
    private static Color recipe(DerivedColor role, Function<ColorRole, Color> in) {
        return switch (role) {
            case SURFACE_HEADER -> SurfaceContrast.apart(mix(in, TEXT_PRIMARY, SURFACE_APP, .05), in.apply(SURFACE_APP), 3);
            case SURFACE_HEADER_ACTIVE -> SurfaceContrast.apart(mix(in, ACCENT, SURFACE_APP, .10),
                in.apply(DerivedColor.SURFACE_HEADER), 2);
            case SURFACE_STRIPE -> SurfaceContrast.apart(mix(in, TEXT_PRIMARY, SURFACE_APP, .035), in.apply(SURFACE_APP), 1.5);
            case SURFACE_TABLE_HEADER -> SurfaceContrast.apart(mix(in, TEXT_PRIMARY, SURFACE_CARD, .06), in.apply(SURFACE_CARD), 3);
            case SURFACE_ZEBRA -> closer(SurfaceContrast.apart(mix(in, TEXT_PRIMARY, SURFACE_CARD, .025), in.apply(SURFACE_CARD), 1.5),
                in.apply(DerivedColor.SURFACE_TABLE_HEADER), in.apply(SURFACE_CARD));
            case SURFACE_ROW_HOVER -> mix(in, ACCENT, SURFACE_CARD, .08);
            case WARNING_GROUND -> mix(in, STATUS_WARNING, SURFACE_CARD, GROUND);
            case WARNING_BORDER -> mix(in, STATUS_WARNING, SURFACE_CARD, EDGE);
            case DANGER_GROUND -> mix(in, STATUS_DANGER, SURFACE_CARD, GROUND);
            case DANGER_BORDER -> mix(in, STATUS_DANGER, SURFACE_CARD, EDGE);
            case INFO_GROUND -> mix(in, ACCENT, SURFACE_CARD, GROUND);
            case INFO_BORDER -> mix(in, ACCENT, SURFACE_CARD, EDGE);
            case SURFACE_FOOTER -> SurfaceContrast.apart(mix(in, TEXT_PRIMARY, SURFACE_DIALOG, .03), in.apply(SURFACE_DIALOG), 2);
            case BORDER_DIVIDER -> ColorMath.mix(boundary(in, BORDER_WINDOW, SurfaceContrast.Guard.STRUCTURAL), in.apply(SURFACE_CARD), .73);
            case BORDER_ROW -> ColorMath.mix(boundary(in, BORDER_WINDOW, SurfaceContrast.Guard.STRUCTURAL), in.apply(SURFACE_CARD), .46);
            case BORDER_HOVER -> {
                Color edge = boundary(in, BORDER_CONTROL, SurfaceContrast.Guard.CONTROL);
                yield ColorMath.mix(flat(in.apply(TEXT_PRIMARY), edge), edge, .24);
            }
            case ACCENT_EDGE -> away(flat(in.apply(ACCENT), in.apply(SURFACE_CARD)), in.apply(SURFACE_CARD), 7);
            case BUTTON_END -> mix(in, TEXT_PRIMARY, BUTTON_SECONDARY_BG, .04);
            case TAG_BORDER_BLUE -> mix(in, HUE_BLUE, SURFACE_CARD, EDGE);
            case TAG_BORDER_GREEN -> mix(in, HUE_GREEN, SURFACE_CARD, EDGE);
            case TAG_BORDER_AMBER -> mix(in, HUE_AMBER, SURFACE_CARD, EDGE);
            case TAG_BORDER_PURPLE -> mix(in, HUE_PURPLE, SURFACE_CARD, EDGE);
            case TAG_BORDER_NEUTRAL -> mix(in, HUE_NEUTRAL, SURFACE_CARD, EDGE);
        };
    }

    /** {@code share} of {@code top} over {@code bottom}. */
    private static Color mix(Function<ColorRole, Color> in, SemanticColor top, SemanticColor bottom, double share) {
        Color under = in.apply(bottom);
        return ColorMath.mix(flat(in.apply(top), under), under, share);
    }

    /** {@code role} as painted on {@code surface.card}, held there by {@code guard} toward {@code text.primary}. */
    private static Color boundary(Function<ColorRole, Color> in, SemanticColor role, SurfaceContrast.Guard guard) {
        Color card = in.apply(SURFACE_CARD), ink = in.apply(TEXT_PRIMARY);
        return guard.apply(flat(in.apply(role), card), card, ink);
    }

    /** {@code color} laid over {@code under} when it carries alpha; opaque. */
    private static Color flat(Color color, Color under) {
        return color.getAlpha() == 255 ? color : ColorMath.mix(color, under, color.getAlpha() / 255.0);
    }

    /** {@code zebra}, or halfway from {@code header} to {@code card} when it would not be closer to {@code card}. */
    private static Color closer(Color zebra, Color header, Color card) {
        double surface = ColorMath.lightness(card);
        return Math.abs(ColorMath.lightness(zebra) - surface) < Math.abs(ColorMath.lightness(header) - surface)
            ? zebra : ColorMath.mix(header, card, .5);
    }

    /**
     * {@code color} moved {@code distance} CIE L* further from {@code from}, in 1% steps toward black (when it is darker
     * than {@code from}, or as light and {@code from} is light) or white, ending there when that is not far enough.
     */
    private static Color away(Color color, Color from, double distance) {
        double own = ColorMath.lightness(color), other = ColorMath.lightness(from);
        Color toward = own < other || (own == other && other >= 50) ? Color.BLACK : Color.WHITE;
        for (int step = 1; step <= 100; step++) {
            Color candidate = ColorMath.mix(toward, color, step / 100.0);
            if (Math.abs(ColorMath.lightness(candidate) - own) >= distance) return candidate;
        }
        return toward;
    }
}
```

A recipe reads every input unconditionally (`boundary` reads `text.primary` even when the edge already holds), so
`inputs` records the same roles over every palette. Recipes are switch arms rather than enum fields so `DerivedColor`
stays a list of names, as `SemanticColor` is.

- [ ] **Step 5: Make both role enums ColorRoles, paint a pair, and say so**

```bash
python3 -I - <<'PY'
import pathlib
def edit(name, edits):
    path = pathlib.Path(name)
    text = path.read_text(encoding="utf-8")
    for old, new in edits:
        if text.count(old) != 1: raise SystemExit(f"{name}: expected one {old!r}, found {text.count(old)}")
        text = text.replace(old, new)
    path.write_text(text, encoding="utf-8")

edit("jasper-ui/src/main/java/dev/jasper/ui/theme/DerivedColor.java", [
(""" * Colours the classic shapes paint that a theme does not list, each computed from the variant's palette. Host-private:""",
 """ * Colours the classic shapes paint that a theme does not list, each computed from the variant's palette by
 * {@link DerivedPalette}. Host-private:"""),
("public enum DerivedColor {", "public enum DerivedColor implements ColorRole {"),
("""    /** The case-sensitive key theme files and frozen tables name the role by. */
    public String key() { return key; }""", """    @Override public String key() { return key; }"""),
])
edit("jasper-ui/src/main/java/dev/jasper/ui/theme/SemanticColor.java", [
("public enum SemanticColor {", "public enum SemanticColor implements ColorRole {"),
("    public String key() { return key; }", "    @Override public String key() { return key; }"),
])
edit("jasper-ui/src/main/java/dev/jasper/ui/theme/SurfaceContrast.java", [
(""" * roles stay as resolved on every other surface. A {@link Guard} is the repair a face applies where it paints a pair.
 * Pure: no Swing state.""",
 """ * roles stay as resolved on every other surface. A face declares each pair it paints ({@link PaintPair}) with its
 * {@link Guard} and paints it with {@link #painted}. Pure: no Swing state."""),
("""    /** {@code role} as painted on {@code surface}: unchanged when it meets {@code minimum}, else made readable there. */""",
 """    /**
     * {@code pair}'s slot as painted in {@code theme}: its guard applied against the pair's other role, toward
     * {@code text.primary} first. The roles themselves are never changed.
     */
    public static Color painted(GeneratedTheme theme, PaintPair pair) {
        return pair.guard().apply(pair.slot().color(theme), pair.against().color(theme),
            theme.semantic().color(SemanticColor.TEXT_PRIMARY));
    }

    /** {@code role} as painted on {@code surface}: unchanged when it meets {@code minimum}, else made readable there. */"""),
])
edit("jasper-ui/src/main/java/dev/jasper/ui/theme/package-info.java", [
(" * terminal and editor schemes, syntax styles, FlatLaf's UI keys from the roles, glass levels and the generated variants.",
 " * terminal and editor schemes, syntax styles, FlatLaf's UI keys from the roles, glass levels, the generated variants, their\n"
 " * derived roles and the paint-time contrast guards."),
])
edit("docs/themes.md", [
("""  a light theme; left out, they are `surface.app` and `text.primary`.
""", """  a light theme; left out, they are `surface.app` and `text.primary`.
- A variant may also list eight derived roles, each as `#RRGGBB`: `surface.header`, `surface.header.active`,
  `surface.stripe`, `surface.tableHeader`, `surface.zebra`, `surface.rowHover`, `warning.ground` and
  `warning.border`. Jasper otherwise computes them from the variant's roles; a listed one is used as given.
"""),
])
PY
```

`SemanticColor`'s constants, keys and order are unchanged; implementing the interface adds no member to the enum.

- [ ] **Step 6: Run the tests to verify they pass**

Run: `./gradlew :jasper-ui:test --tests 'dev.jasper.ui.theme.*' :jasper-ui:javadoc verifyUiArchitecture`
Expected: BUILD SUCCESSFUL: `DerivedColorTest` (2) and `DerivedPaletteTest` (7) pass, every other theme test still
passes, the Javadoc links resolve and the theme package stays JDK-only.

- [ ] **Step 7: Commit**

```bash
git add jasper-ui/src/main/java/dev/jasper/ui/theme jasper-ui/src/test/java/dev/jasper/ui/theme docs/themes.md
git commit -F - <<'EOF'
Derived roles: DerivedPalette, ColorRole, PaintPair and painted

DerivedPalette computes spec 7.1's derived roles in order from the palette
or takes the variant's overrides, kept by theme identity; Slate Cobalt
Light meets the spec's calibration. ColorRole lets a pair name either kind
of role, and SurfaceContrast.painted paints a declared pair through its
guard. Nothing in the default look reads a derived role yet.

🤖 Generated with Claude Code at The Home Depot

Co-Authored-By: Claude <noreply@anthropic.com>
EOF
```

### Task 5: The converter's override keys, the regenerated goldens and derived-roles.tsv

`tools/themes/jasper_theme.py` learns the eight override keys (spec 7.7): its check accepts them after the roles and
the title bar's keys, in order and opaque, and it fills seven of them when the IntelliJ theme, or a parent theme in its
plugin, sets the matching IntelliJ key (`surface.stripe` has none). The Ocean and Dawn fixtures gain such keys, the
goldens are regenerated, and so are the four built-in Catppuccin themes, which are the converter's output and whose
vendored plugin sets five of the keys. Then the shipped themes' derived roles are frozen as `derived-roles.tsv`
(spec 7.1), and spec 7.3's known repairs are measured on the theme set with the guards that repair them. No
default-look painter reads a derived role, so nothing painted changes.

**Files:**
- Modify: `tools/themes/jasper_theme.py:55` (after `OPTIONAL_ROLES`), `:184-188` (`validate`), `:527` (`Effective`), `:580-589` (`resolve`), `:956` (before `convert_intellij_theme`), `:992` and `:1003` (in it)
- Modify: `tools/themes/test_jasper_theme.py:505` (three tests before `test_chosen_roles_take_a_colour_of_the_themes_own_palette_or_a_hex_colour`)
- Modify fixtures: `tools/themes/testdata/intellij/plugin/themes/Ocean.theme.json:26`, `tools/themes/testdata/intellij/loose/Ocean.theme.json:26`, `tools/themes/testdata/intellij/plugin/themes/Dawn.theme.json:8`
- Regenerate: `tools/themes/testdata/expected/ocean.json`, `tools/themes/testdata/expected/ocean-dawn.json`, `jasper-ui/src/main/resources/dev/jasper/ui/theme/catppuccin-{latte,frappe,macchiato,mocha}.json`
- Modify: `jasper-ui/src/test/java/dev/jasper/ui/theme/ConvertedThemesTest.java` (one test), `jasper-ui/src/test/java/dev/jasper/ui/theme/BuiltInThemesTest.java` (one test)
- Create: `jasper-ui/src/test/java/dev/jasper/ui/theme/DerivedRolesTableTest.java`, `jasper-ui/src/test/resources/dev/jasper/ui/theme/derived-roles.tsv`, `jasper-ui/src/test/java/dev/jasper/ui/theme/KnownRepairsTest.java`
- Modify: `docs/themes.md:92-98` (the IntelliJ bullet)

**Interfaces:**
- Consumes: `DerivedPalette.of`, `DerivedColor` (Tasks 3-4), the `JasperTheme` override keys (Task 3).
- Produces: `OVERRIDE_KEYS` and `OVERRIDES` in `jasper_theme.py`, `overrides(effective, roles) -> (dict, list[str])`
  and `Effective.authored` (the flattened `ui` of the theme and its parents in its plugin);
  `jasper-ui/src/test/resources/dev/jasper/ui/theme/derived-roles.tsv` (`<theme id>\t<variant>\t<role key>\t#rrggbb`,
  138 rows after one comment line, built-in themes in `JasperTheme.builtIns()` order, Dark before Light, roles in
  `DerivedColor` order).

- [ ] **Step 1: Write the failing tests**

```bash
python3 -I - <<'PY'
import pathlib
path = pathlib.Path("tools/themes/test_jasper_theme.py")
text = path.read_text(encoding="utf-8")
old = "    def test_chosen_roles_take_a_colour_of_the_themes_own_palette_or_a_hex_colour(self):\n"
new = """    def test_keys_the_theme_sets_itself_become_override_keys_after_the_roles(self):
        \"\"\"Dawn sets three of the IntelliJ keys; ExperimentalLight, its IntelliJ parent, sets others that do not count.\"\"\"
        with tempfile.TemporaryDirectory() as scratch:
            themes = tool.plugin_themes(tool.Files.archive(plugin_jar(pathlib.Path(scratch) / "ocean.jar")))
            dawn = tool.convert_intellij_theme(next(t for t in themes if t.id == "ocean-dawn"), themes, None)
        light = dawn.doc["ui"]["light"]
        self.assertEqual(list(light), tool.ROLES + ["surface.tableHeader", "warning.ground", "warning.border"])
        self.assertEqual(light["surface.tableHeader"], "#F2EBE1")
        self.assertEqual(light["warning.ground"],
                         tool.hex_of(tool.over(tool.parse_hex("#E0A0301A"), tool.parse_hex(light["surface.card"]))), "laid over the card")
        self.assertIn("surface.tableHeader: #F2EBE1 from TableHeader.background", dawn.sources)

    def test_a_wildcard_or_an_intellij_parent_fills_no_override_and_a_parent_in_the_plugin_does(self):
        night = {"name": "Night", "dark": True, "parentTheme": "ExperimentalDark",
                 "ui": {"*": {"stripeColor": "#123456", "hoverBackground": "#654321"}}}
        base = {"name": "Base Theme", "dark": True,
                "ui": {"ToolWindow.background": "#102030", "TableHeader.background": "#203040", "Table.stripeColor": "#151515"}}
        child = {"name": "Child Theme", "parentTheme": "Base Theme", "ui": {"Table.stripeColor": "#181818"}}
        with tempfile.TemporaryDirectory() as scratch:
            themes = tool.plugin_themes(tool.Files.archive(theme_jar(pathlib.Path(scratch) / "t.jar",
                                                                     {"night": night, "base": base, "child": child})))
            converted = {theme.id: tool.convert_intellij_theme(theme, themes, None).doc["ui"]["dark"] for theme in themes}
        self.assertFalse(set(converted["night"]) & set(tool.OVERRIDE_KEYS), "wildcards and IntelliJ's own keys do not count")
        self.assertEqual(converted["child"]["surface.tableHeader"], "#203040", "from its parent in the plugin")
        self.assertEqual(converted["child"]["surface.zebra"], "#181818", "its own key first")

    def test_the_check_takes_override_keys_after_the_roles_in_order_and_opaque(self):
        themes, loose = tool.themes_in(DATA / "intellij" / "loose" / "Ocean.theme.json")
        doc = tool.convert_intellij_theme(themes[0], themes, None, loose_dir=loose).doc
        dark = doc["ui"]["dark"]
        self.assertEqual(list(dark)[len(tool.ROLES):], ["surface.header", "surface.header.active", "surface.rowHover"])
        tool.validate(doc, "ocean.json")
        def check(roles, message):
            doc["ui"]["dark"] = roles
            with self.assertRaisesRegex(tool.ConversionError, message):
                tool.validate(doc, "ocean.json")
        keys = list(dark)
        check({key: dark[key] for key in keys[:-3] + [keys[-2], keys[-3], keys[-1]]}, "internal: ui.dark roles")
        check({key: dark[key] for key in [keys[-1]] + keys[:-1]}, "internal: ui.dark roles")
        check(dict(dark, **{"surface.header": "#16242F80"}), "internal: ui.dark.surface.header")
        titled = {key: dark[key] for key in tool.ROLES} | {"surface.titlebar": "#000000", "text.titlebar": "#FFFFFF"} \\
            | {key: dark[key] for key in keys[len(tool.ROLES):]}
        doc["ui"]["dark"] = titled
        tool.validate(doc, "ocean.json")
        check({key: dark[key] for key in tool.ROLES} | {"surface.header": "#16242F", "surface.titlebar": "#000000"},
              "internal: ui.dark roles")

    def test_chosen_roles_take_a_colour_of_the_themes_own_palette_or_a_hex_colour(self):
"""
if text.count(old) != 1: raise SystemExit(f"test_jasper_theme.py: expected one {old!r}")
path.write_text(text.replace(old, new), encoding="utf-8")

def insert(name, before, addition):
    path = pathlib.Path(name)
    text = path.read_text(encoding="utf-8").rstrip()
    if not text.endswith("}") or text.count(before) != 1: raise SystemExit(f"{name}: unexpected shape")
    path.write_text(text[:-1].rstrip() + "\n" + addition + "}\n", encoding="utf-8")

insert("jasper-ui/src/test/java/dev/jasper/ui/theme/ConvertedThemesTest.java", "class ConvertedThemesTest", """
    @Test void theOverrideKeysTheConverterFillsLoadAsDerivedRoles() throws Exception {
        var ocean = JasperTheme.parse(Files.readString(EXPECTED.resolve("ocean.json")), "ocean").generated(true);
        assertThat(ocean.derivedOverrides()).containsOnly(entry(DerivedColor.SURFACE_HEADER, new Color(0x16242F)),
            entry(DerivedColor.SURFACE_HEADER_ACTIVE, new Color(0x1D3445)), entry(DerivedColor.SURFACE_ROW_HOVER, new Color(0x1D3C51)));
        var dawn = JasperTheme.parse(Files.readString(EXPECTED.resolve("ocean-dawn.json")), "ocean-dawn").generated(false);
        assertThat(dawn.derivedOverrides()).containsOnly(entry(DerivedColor.SURFACE_TABLE_HEADER, new Color(0xF2EBE1)),
            entry(DerivedColor.WARNING_GROUND, new Color(0xF8EEDE)), entry(DerivedColor.WARNING_BORDER, new Color(0xD9A441)));
        var stray = JasperTheme.parse(Files.readString(EXPECTED.resolve("stray.json")), "stray").generated(true);
        assertThat(stray.derivedOverrides()).as("Stray sets none of the keys").isEmpty();
    }
""")
insert("jasper-ui/src/test/java/dev/jasper/ui/theme/BuiltInThemesTest.java", "class BuiltInThemesTest", """
    /** The vendored Catppuccin plugin sets five of the IntelliJ keys the converter reads; Slate Cobalt authors none. */
    @Test void catppuccinAuthorsFiveDerivedRolesAndSlateCobaltNone() {
        for (boolean dark : new boolean[] {true, false}) assertThat(JasperTheme.bundled().generated(dark).derivedOverrides()).isEmpty();
        for (var theme : JasperTheme.builtIns().subList(1, 5)) {
            var variant = theme.generated(!theme.id().equals("catppuccin-latte"));
            assertThat(variant.derivedOverrides()).as(theme.id()).containsOnlyKeys(DerivedColor.SURFACE_HEADER,
                DerivedColor.SURFACE_HEADER_ACTIVE, DerivedColor.SURFACE_ROW_HOVER, DerivedColor.WARNING_GROUND, DerivedColor.WARNING_BORDER);
            assertThat(DerivedPalette.of(variant).color(DerivedColor.SURFACE_HEADER_ACTIVE)).as("%s: as authored, one with the header", theme.id())
                .isEqualTo(DerivedPalette.of(variant).color(DerivedColor.SURFACE_HEADER));
        }
    }
""")
PY
```

Create `jasper-ui/src/test/java/dev/jasper/ui/theme/DerivedRolesTableTest.java` with:

```java
package dev.jasper.ui.theme;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

/** Spec 7.1: every built-in variant's derived roles, frozen in derived-roles.tsv; a recipe or built-in theme change edits it in the same commit. */
class DerivedRolesTableTest {
    @Test void theBuiltInVariantsDerivedRolesAreFrozen() throws IOException {
        var expected = new StringBuilder();
        try (var in = DerivedRolesTableTest.class.getResourceAsStream("derived-roles.tsv")) {
            for (String line : new String(in.readAllBytes(), StandardCharsets.UTF_8).split("\n"))
                if (!line.isBlank() && !line.startsWith("#")) expected.append(line).append('\n');
        }
        var actual = new StringBuilder();
        for (var theme : JasperTheme.builtIns()) for (boolean dark : new boolean[] {true, false}) if (theme.has(dark)) {
            var derived = DerivedPalette.of(theme.generated(dark));
            for (var role : DerivedColor.values())
                actual.append(theme.id()).append('\t').append(dark ? "dark" : "light").append('\t').append(role.key()).append('\t')
                    .append(ColorMath.hex(derived.color(role))).append('\n');
        }
        assertThat(actual.toString()).isEqualTo(expected.toString());
    }
}
```

Create `jasper-ui/src/test/java/dev/jasper/ui/theme/KnownRepairsTest.java` with:

```java
package dev.jasper.ui.theme;

import static dev.jasper.ui.theme.SemanticColor.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import dev.jasper.ui.testsupport.KitFixtures;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Spec 7.3's known repairs, measured on the theme set: each pair's contrast as the theme gives it, and its guard's repair
 * where a classic face will paint it (plan 4 declares these pairs on its faces).
 */
class KnownRepairsTest {
    private static GeneratedTheme theme(String name) {
        return KitFixtures.themes().stream().filter(theme -> (theme.name() + (theme.dark() ? " Dark" : " Light")).equals(name))
            .findFirst().orElseThrow(() -> new AssertionError("No theme " + name));
    }

    private static void repairs(String name, PaintPair pair, double measured) {
        var theme = theme(name);
        var against = pair.against().color(theme);
        assertThat(ColorMath.contrast(pair.slot().color(theme), against)).as("%s: %s on %s", name, pair.slot().key(), pair.against().key())
            .isCloseTo(measured, within(.005));
        assertThat(pair.guard().holds(SurfaceContrast.painted(theme, pair), against)).as("%s: %s repaired", name, pair.slot().key()).isTrue();
    }

    @Test void theKnownRepairsMeasureAsSpecSevenThreeSaysAndTheirGuardsRepairThem() {
        for (String flavour : List.of("Catppuccin Latte Light", "Catppuccin Frappé Dark", "Catppuccin Macchiato Dark", "Catppuccin Mocha Dark"))
            repairs(flavour, new PaintPair(BORDER_CONTROL, SURFACE_INPUT, SurfaceContrast.Guard.CONTROL), 1.00);
        repairs("Catppuccin Latte Light", new PaintPair(BORDER_WINDOW, SURFACE_APP, SurfaceContrast.Guard.STRUCTURAL), 1.17);
        repairs("Ocean Dawn Light", new PaintPair(BORDER_WINDOW, SURFACE_APP, SurfaceContrast.Guard.STRUCTURAL), 1.11);
        repairs("Catppuccin Latte Light", new PaintPair(TEXT_ONACCENT, ACCENT, SurfaceContrast.Guard.TEXT), 4.09);
        repairs("Ocean Dark", new PaintPair(TEXT_ONACCENT, ACCENT, SurfaceContrast.Guard.TEXT), 3.09);
        var warning = new PaintPair(STATUS_WARNING, DerivedColor.WARNING_GROUND, SurfaceContrast.Guard.TEXT);
        // Latte's warning ground is now the one its plugin authors; spec 7.3 measured the recipe's (2.06:1).
        repairs("Catppuccin Latte Light", warning, 2.09);
        repairs("Ocean Dawn Light", warning, 1.60);
        repairs("Ocean Dark", warning, 3.52);
        repairs("Slate Cobalt Light", warning, 4.15);
        var dark = theme("Slate Cobalt Dark");
        assertThat(ColorMath.contrast(ACCENT.color(dark), SURFACE_CARD.color(dark))).as("the checked box's fill").isCloseTo(2.70, within(.005));
        assertThat(ColorMath.contrast(DerivedColor.ACCENT_EDGE.color(dark), SURFACE_CARD.color(dark))).as("its accent.edge ring")
            .isGreaterThanOrEqualTo(SurfaceContrast.INDICATOR);
    }
}
```

Write the table with real tabs (the cells below are space-separated only for the plan):

```bash
python3 -I - <<'PY'
import pathlib
ROWS = """
slate-cobalt dark surface.header #2d3036
slate-cobalt dark surface.header.active #232b3c
slate-cobalt dark surface.stripe #2a2d33
slate-cobalt dark surface.tableHeader #3a3e45
slate-cobalt dark surface.zebra #33373f
slate-cobalt dark surface.rowHover #2e3747
slate-cobalt dark warning.ground #49473f
slate-cobalt dark warning.border #7c6d48
slate-cobalt dark danger.ground #493d43
slate-cobalt dark danger.border #7c5153
slate-cobalt dark info.ground #2e3b51
slate-cobalt dark info.border #2e4a7a
slate-cobalt dark surface.footer #3a3f48
slate-cobalt dark border.divider #3e444e
slate-cobalt dark border.row #383d46
slate-cobalt dark border.hover #7b818c
slate-cobalt dark accent.edge #4e85e0
slate-cobalt dark button.end #434852
slate-cobalt dark tag.border.blue #546785
slate-cobalt dark tag.border.green #48765b
slate-cobalt dark tag.border.amber #7c6d48
slate-cobalt dark tag.border.purple #6c6089
slate-cobalt dark tag.border.neutral #6f737b
slate-cobalt light surface.header #dfe2e6
slate-cobalt light surface.header.active #d3dcea
slate-cobalt light surface.stripe #e2e5e9
slate-cobalt light surface.tableHeader #f1f2f2
slate-cobalt light surface.zebra #f9f9fa
slate-cobalt light surface.rowHover #eef3fb
slate-cobalt light warning.ground #f1e9db
slate-cobalt light warning.border #d8c099
slate-cobalt light danger.ground #f7e1e2
slate-cobalt light danger.border #e7aaad
slate-cobalt light info.ground #e0e9f7
slate-cobalt light info.border #a8c1e9
slate-cobalt light surface.footer #eef1f3
slate-cobalt light border.divider #d5dae0
slate-cobalt light border.row #e5e8ec
slate-cobalt light border.hover #9ba1aa
slate-cobalt light accent.edge #1f52a7
slate-cobalt light button.end #f6f6f6
slate-cobalt light tag.border.blue #a8c1e9
slate-cobalt light tag.border.green #a5cab5
slate-cobalt light tag.border.amber #d8c099
slate-cobalt light tag.border.purple #cab2d8
slate-cobalt light tag.border.neutral #bdc0c6
catppuccin-latte light surface.header #e6e9ef
catppuccin-latte light surface.header.active #e6e9ef
catppuccin-latte light surface.stripe #d7dbe4
catppuccin-latte light surface.tableHeader #e5e7ed
catppuccin-latte light surface.zebra #e9ebf0
catppuccin-latte light surface.rowHover #d8dae1
catppuccin-latte light warning.ground #f1e3dd
catppuccin-latte light warning.border #f1e3dd
catppuccin-latte light danger.ground #ebd1db
catppuccin-latte light danger.border #e397aa
catppuccin-latte light info.ground #e1d7f4
catppuccin-latte light info.border #c6a7f3
catppuccin-latte light surface.footer #dfe2e9
catppuccin-latte light border.divider #d5d9e1
catppuccin-latte light border.row #dfe2e9
catppuccin-latte light border.hover #a3a7b7
catppuccin-latte light accent.edge #7230c9
catppuccin-latte light button.end #c7cbd5
catppuccin-latte light tag.border.blue #a1b7e4
catppuccin-latte light tag.border.green #abc7ac
catppuccin-latte light tag.border.amber #d7c2a8
catppuccin-latte light tag.border.purple #dabbd9
catppuccin-latte light tag.border.neutral #b4b7c3
catppuccin-frappe dark surface.header #292c3c
catppuccin-frappe dark surface.header.active #292c3c
catppuccin-frappe dark surface.stripe #292c3b
catppuccin-frappe dark surface.tableHeader #323647
catppuccin-frappe dark surface.zebra #2d3041
catppuccin-frappe dark surface.rowHover #44495d
catppuccin-frappe dark warning.ground #3d3842
catppuccin-frappe dark warning.border #3d3842
catppuccin-frappe dark danger.ground #443846
catppuccin-frappe dark danger.border #754e59
catppuccin-frappe dark info.ground #403c54
catppuccin-frappe dark info.border #695a80
catppuccin-frappe dark surface.footer #35394b
catppuccin-frappe dark border.divider #3b3e51
catppuccin-frappe dark border.row #343849
catppuccin-frappe dark border.hover #666c85
catppuccin-frappe dark accent.edge #d7b6ec
catppuccin-frappe dark button.end #464b5f
catppuccin-frappe dark tag.border.blue #576384
catppuccin-frappe dark tag.border.green #5f6e68
catppuccin-frappe dark tag.border.amber #716b6a
catppuccin-frappe dark tag.border.purple #756781
catppuccin-frappe dark tag.border.neutral #61667e
catppuccin-macchiato dark surface.header #1e2030
catppuccin-macchiato dark surface.header.active #1e2030
catppuccin-macchiato dark surface.stripe #1e202d
catppuccin-macchiato dark surface.tableHeader #282b3c
catppuccin-macchiato dark surface.zebra #222435
catppuccin-macchiato dark surface.rowHover #3a3e53
catppuccin-macchiato dark warning.ground #342e38
catppuccin-macchiato dark warning.border #342e38
catppuccin-macchiato dark danger.ground #3b2e3e
catppuccin-macchiato dark danger.border #714959
catppuccin-macchiato dark info.ground #36324c
catppuccin-macchiato dark info.border #61537f
catppuccin-macchiato dark surface.footer #292c40
catppuccin-macchiato dark border.divider #303347
catppuccin-macchiato dark border.row #292c3e
catppuccin-macchiato dark border.hover #5f657d
catppuccin-macchiato dark accent.edge #d5b9f8
catppuccin-macchiato dark button.end #3c4056
catppuccin-macchiato dark tag.border.blue #515d7e
catppuccin-macchiato dark tag.border.green #596a64
catppuccin-macchiato dark tag.border.amber #6d6867
catppuccin-macchiato dark tag.border.purple #6f627a
catppuccin-macchiato dark tag.border.neutral #5c6076
catppuccin-mocha dark surface.header #181825
catppuccin-mocha dark surface.header.active #181825
catppuccin-mocha dark surface.stripe #181823
catppuccin-mocha dark surface.tableHeader #232331
catppuccin-mocha dark surface.zebra #1d1d2a
catppuccin-mocha dark surface.rowHover #353749
catppuccin-mocha dark warning.ground #2f282f
catppuccin-mocha dark warning.border #2f282f
catppuccin-mocha dark danger.ground #372837
catppuccin-mocha dark danger.border #704659
catppuccin-mocha dark info.ground #312c42
catppuccin-mocha dark info.border #605179
catppuccin-mocha dark surface.footer #232434
catppuccin-mocha dark border.divider #2a2b3c
catppuccin-mocha dark border.row #242433
catppuccin-mocha dark border.hover #5d5f75
catppuccin-mocha dark accent.edge #dabff9
catppuccin-mocha dark button.end #37394b
catppuccin-mocha dark tag.border.blue #4d5a79
catppuccin-mocha dark tag.border.green #566861
catppuccin-mocha dark tag.border.amber #6d6765
catppuccin-mocha dark tag.border.purple #6c5e74
catppuccin-mocha dark tag.border.neutral #595c6f
"""
lines = ["# Every built-in variant's derived roles (DerivedPalette), frozen: theme id, variant, role, colour."]
lines += ["\t".join(row.split()) for row in ROWS.strip().splitlines()]
if len(lines) != 139: raise SystemExit(f"derived-roles.tsv: expected 138 rows, got {len(lines) - 1}")
pathlib.Path("jasper-ui/src/test/resources/dev/jasper/ui/theme/derived-roles.tsv").write_text("\n".join(lines) + "\n", encoding="utf-8")
PY
```

Its Slate Cobalt Light rows meet spec 7.1's calibration: `surface.header` `#dfe2e6`, `surface.tableHeader` `#f1f2f2`,
`surface.rowHover` `#eef3fb`. The Catppuccin rows hold the overrides Step 5 writes: each flavour's headers are one
colour and its warning ground and border are one colour, as the plugin sets them; the guards repair them where a
classic face paints them (plans 4 and 7).

- [ ] **Step 2: Run the tests to verify they fail**

Run: `python3 -B -m unittest discover -s tools/themes -p 'test_*.py'`
Expected: FAILED (errors=3 or more): `AttributeError: module 'jasper_theme' has no attribute 'OVERRIDE_KEYS'` and the
new tests' assertions.

Run: `./gradlew :jasper-ui:test --tests 'dev.jasper.ui.theme.ConvertedThemesTest' --tests 'dev.jasper.ui.theme.BuiltInThemesTest' --tests 'dev.jasper.ui.theme.DerivedRolesTableTest' --tests 'dev.jasper.ui.theme.KnownRepairsTest'`
Expected: FAIL: the converted and built-in themes carry no overrides yet, the table's Catppuccin rows differ, and
Latte's warning pair measures 2.06 (its recipe) instead of 2.09.

- [ ] **Step 3: Teach the converter the override keys**

```bash
python3 -I - <<'PY'
import pathlib
path = pathlib.Path("tools/themes/jasper_theme.py")
text = path.read_text(encoding="utf-8")
EDITS = [
("""OPTIONAL_ROLES = ["surface.titlebar", "text.titlebar"]
""", """OPTIONAL_ROLES = ["surface.titlebar", "text.titlebar"]
# Keys a variant may add after those: derived roles the theme gives itself instead of their recipes, always opaque.
OVERRIDE_KEYS = ["surface.header", "surface.header.active", "surface.stripe", "surface.tableHeader", "surface.zebra",
                 "surface.rowHover", "warning.ground", "warning.border"]
"""),
("""        if list(roles) not in (ROLES, ROLES + OPTIONAL_ROLES):
            fail(f"ui.{variant} roles")
        for role, value in roles.items():
            if not HEX.fullmatch(value):
                fail(f"ui.{variant}.{role} = {value!r}")
""", """        keys = list(roles)
        extra = keys[len(ROLES):]
        if keys[:len(ROLES)] != ROLES or extra != [key for key in OPTIONAL_ROLES + OVERRIDE_KEYS if key in extra]:
            fail(f"ui.{variant} roles")
        for role, value in roles.items():
            if not HEX.fullmatch(value) or (role in OVERRIDE_KEYS and len(value) != 7):
                fail(f"ui.{variant}.{role} = {value!r}")
"""),
("""    scheme: ElementTree.Element | None
    scheme_from: str
    chain: list[str]
""", """    scheme: ElementTree.Element | None
    scheme_from: str
    chain: list[str]
    authored: list[dict] = field(default_factory=list)  # the flattened ui of the theme and its parents in its plugin
"""),
("""    levels = []
    for item in chain:
""", """    levels, authored = [], []
    for item in chain:
"""),
("""        levels.append(("theme" if item is theme else str(item.data.get("name", item.id)), flat))
    scheme, scheme_from = find_scheme(chain, theme, warnings, scheme_file, loose_dir, dark)
    return Effective(dark, colors, levels, scheme, scheme_from, [str(t.data.get("name", t.id)) for t in chain])
""", """        levels.append(("theme" if item is theme else str(item.data.get("name", item.id)), flat))
        if item.files is not None:
            authored.append(flat)
    scheme, scheme_from = find_scheme(chain, theme, warnings, scheme_file, loose_dir, dark)
    return Effective(dark, colors, levels, scheme, scheme_from, [str(t.data.get("name", t.id)) for t in chain], authored)
"""),
("""def convert_intellij_theme(""", """# The override keys an IntelliJ theme fills (docs/themes.md): each from one IntelliJ key, a translucent colour laid over
# the role it stands on. surface.stripe has no IntelliJ key.
OVERRIDES = [
    ("surface.header", "ToolWindow.Header.inactiveBackground", "surface.app"),
    ("surface.header.active", "ToolWindow.Header.background", "surface.app"),
    ("surface.tableHeader", "TableHeader.background", "surface.card"),
    ("surface.zebra", "Table.stripeColor", "surface.card"),
    ("surface.rowHover", "Table.hoverBackground", "surface.card"),
    ("warning.ground", "Banner.warningBackground", "surface.card"),
    ("warning.border", "Banner.warningBorderColor", "surface.card"),
]


def overrides(effective: Effective, roles: dict) -> tuple[dict, list[str]]:
    \"\"\"The override keys whose IntelliJ key the theme, or a parent in its plugin, sets itself, and one provenance line
    each. IntelliJ's own themes and "*." wildcards do not count: without the key, the role follows its recipe.\"\"\"
    lookup = Lookup(effective)
    values, provenance = {}, []
    for key, intellij, under in OVERRIDES:
        for ui in effective.authored:
            colour = lookup.colour(ui[intellij]) if intellij in ui else None
            if colour is not None:
                values[key] = hex_of(colour if colour[3] == 255 else over(colour, parse_hex(roles[under])))
                provenance.append(f"{key}: {values[key]} from {intellij}")
                break
    return values, provenance


def convert_intellij_theme("""),
("""    doc["ui"] = {variant: roles | title_bar(Lookup(effective), roles, effective.dark)}
""", """    authored, provenance_overrides = overrides(effective, roles)
    doc["ui"] = {variant: roles | title_bar(Lookup(effective), roles, effective.dark) | authored}
"""),
("""    return Result(doc, summary, warnings, provenance)
""", """    return Result(doc, summary, warnings, provenance + provenance_overrides)
"""),
]
for old, new in EDITS:
    if text.count(old) != 1: raise SystemExit(f"jasper_theme.py: expected one {old!r}, found {text.count(old)}")
    text = text.replace(old, new)
path.write_text(text, encoding="utf-8")
PY
```

`Effective.levels` keeps every level for the roles, wildcards included; `authored` holds only the levels whose theme
comes from the converted plugin (`ThemeFile.files` is set), so a key that IntelliJ's vendored parent sets, for another
palette, is never copied.

- [ ] **Step 4: Give the fixtures the keys**

```bash
python3 -I - <<'PY'
import pathlib
def edit(name, old, new):
    path = pathlib.Path(name)
    text = path.read_text(encoding="utf-8")
    if text.count(old) != 1: raise SystemExit(f"{name}: expected one {old!r}")
    path.write_text(text.replace(old, new), encoding="utf-8")
for name in ["tools/themes/testdata/intellij/plugin/themes/Ocean.theme.json", "tools/themes/testdata/intellij/loose/Ocean.theme.json"]:
    edit(name, """    "ActionButton.hoverBackground": "#FFFFFF1A",
""", """    "ActionButton.hoverBackground": "#FFFFFF1A",
    "ToolWindow.Header": { "background": "#1D3445", "inactiveBackground": "sea" },
    "Table.hoverBackground": "#3A9AD933",
""")
edit("tools/themes/testdata/intellij/plugin/themes/Dawn.theme.json", """    "Button.default.startBackground": "#C2410C"
""", """    "Button.default.startBackground": "#C2410C",
    "TableHeader.background": "#F2EBE1",
    "Banner": { "warningBackground": "#E0A0301A", "warningBorderColor": "#D9A441" }
""")
PY
```

The two Ocean files stay identical (`test_the_loose_and_packaged_theme_agree`). Ocean's header uses its named colour
`sea` and its row hover is translucent; Dawn's warning ground is translucent and its parent, IntelliJ's
`ExperimentalLight`, sets `Table.stripeColor` and `ToolWindow.Header.inactiveBackground`, which must not be copied.

- [ ] **Step 5: Regenerate the goldens and the built-in Catppuccin themes, and review the diff**

```bash
JASPER_THEME_UPDATE_GOLDEN=1 python3 -B -m unittest discover -s tools/themes -p 'test_*.py'
git diff --stat -- tools/themes/testdata/expected jasper-ui/src/main/resources/dev/jasper/ui/theme
git diff -- tools/themes/testdata/expected jasper-ui/src/main/resources/dev/jasper/ui/theme | grep '^[-+] '
```

Expected: six files change, and the last command prints exactly (each file's last role gains a comma and its override
keys follow it):

```
-      "hue.neutral": "#B6BEE2"
+      "hue.neutral": "#B6BEE2",
+      "surface.header": "#292C3C",
+      "surface.header.active": "#292C3C",
+      "surface.rowHover": "#44495D",
+      "warning.ground": "#3D3842",
+      "warning.border": "#3D3842"
-      "hue.neutral": "#5C5F77"
+      "hue.neutral": "#5C5F77",
+      "surface.header": "#E6E9EF",
+      "surface.header.active": "#E6E9EF",
+      "surface.rowHover": "#D8DAE1",
+      "warning.ground": "#F1E3DD",
+      "warning.border": "#F1E3DD"
-      "hue.neutral": "#B8C0E0"
+      "hue.neutral": "#B8C0E0",
+      "surface.header": "#1E2030",
+      "surface.header.active": "#1E2030",
+      "surface.rowHover": "#3A3E53",
+      "warning.ground": "#342E38",
+      "warning.border": "#342E38"
-      "hue.neutral": "#BAC2DE"
+      "hue.neutral": "#BAC2DE",
+      "surface.header": "#181825",
+      "surface.header.active": "#181825",
+      "surface.rowHover": "#353749",
+      "warning.ground": "#2F282F",
+      "warning.border": "#2F282F"
-      "hue.neutral": "#40424A"
+      "hue.neutral": "#40424A",
+      "surface.tableHeader": "#F2EBE1",
+      "warning.ground": "#F8EEDE",
+      "warning.border": "#D9A441"
-      "hue.neutral": "#B4C5D1"
+      "hue.neutral": "#B4C5D1",
+      "surface.header": "#16242F",
+      "surface.header.active": "#1D3445",
+      "surface.rowHover": "#1D3C51"
```

(frappe, latte, macchiato, mocha, ocean-dawn, ocean). Any other change is a stop: report it rather than committing.

- [ ] **Step 6: Run the tests to verify they pass**

Run: `python3 -B -m unittest discover -s tools/themes -p 'test_*.py'`
Expected: `Ran 69 tests` … `OK`.

Run: `./gradlew :jasper-ui:test checkThemeTool`
Expected: BUILD SUCCESSFUL; `ConvertedThemesTest`, `BuiltInThemesTest`, `DerivedRolesTableTest` and `KnownRepairsTest`
pass with the rest,
and `ColourLiteralScanTest` (jasper-app) is unaffected because the built-in theme files are its exemption.

- [ ] **Step 7: Document the converter's keys**

```bash
python3 -I - <<'PY'
import pathlib
path = pathlib.Path("docs/themes.md")
text = path.read_text(encoding="utf-8")
old = """  theme with the same name apart from the word "Islands"), the theme's window chrome comes from that variant. Every
"""
new = """  theme with the same name apart from the word "Islands"), the theme's window chrome comes from that variant. A
  theme that sets `ToolWindow.Header.inactiveBackground`, `ToolWindow.Header.background`, `TableHeader.background`,
  `Table.stripeColor`, `Table.hoverBackground`, `Banner.warningBackground` or `Banner.warningBorderColor` itself, or
  through a parent theme in its plugin, also gets the matching derived role (`surface.header`,
  `surface.header.active`, `surface.tableHeader`, `surface.zebra`, `surface.rowHover`, `warning.ground`,
  `warning.border`). Every
"""
if text.count(old) != 1: raise SystemExit(f"docs/themes.md: expected one {old!r}")
path.write_text(text.replace(old, new), encoding="utf-8")
PY
```

- [ ] **Step 8: Commit**

```bash
git add tools/themes jasper-ui/src/main/resources/dev/jasper/ui/theme jasper-ui/src/test/java/dev/jasper/ui/theme \
  jasper-ui/src/test/resources/dev/jasper/ui/theme/derived-roles.tsv docs/themes.md
git commit -F - <<'EOF'
Derived roles: the converter's override keys, goldens and derived-roles.tsv

The converter writes the override keys after the roles when the theme or
a parent in its plugin sets the IntelliJ key, never from IntelliJ's own
themes or wildcards. The Ocean and Dawn fixtures exercise them; the goldens
and the four built-in Catppuccin themes are regenerated. The shipped
themes' derived roles are frozen in derived-roles.tsv, and KnownRepairsTest
measures spec 7.3's known repairs on the theme set.

🤖 Generated with Claude Code at The Home Depot

Co-Authored-By: Claude <noreply@anthropic.com>
EOF
```

### Task 6: The default kit's gallery matrix in check

`KitGallery.main`'s body splits into `installed(theme, kit, action)` (the gallery's look and feel, font, caps-lock and
focus set-up and restoration) and `capture(fixture, context, focus)` (one fixture as the gallery writes it), so the
opt-in gallery and the new `KitMatrixTest` render the same images. `KitMatrixTest` renders every gallery fixture under
`Kit.DEFAULT` over the theme set inside `./gradlew check`, and checks that under `custom()` no pixel has a colour only
another theme has, which is how a hard-coded colour shows (spec 16.1.3, the default kit's part).

**Files:**
- Modify: `jasper-ui/src/testFixtures/java/dev/jasper/ui/gallery/KitGallery.java:92-166` (`main`), `:167` (`GalleryFocus`)
- Test: `jasper-ui/src/test/java/dev/jasper/ui/gallery/KitMatrixTest.java` (new)

**Interfaces:**
- Consumes: `KitFixtures.themes()`, `custom()`, `dark()` (Task 1).
- Produces: `public record KitGallery.Capture(Fixture fixture, BufferedImage image, String metadata)`;
  `public static void KitGallery.installed(GeneratedTheme theme, Kit kit, BiConsumer<KitContext, GalleryFocus> action)`;
  `public static Capture KitGallery.capture(Fixture fixture, KitContext context, GalleryFocus focus)`;
  `public static final class KitGallery.GalleryFocus`. Plans 4 and 5 add the classic and Enterprise kits to the matrix.

- [ ] **Step 1: Write the failing test**

Create `jasper-ui/src/test/java/dev/jasper/ui/gallery/KitMatrixTest.java` with:

```java
package dev.jasper.ui.gallery;

import static org.assertj.core.api.Assertions.assertThat;

import dev.jasper.ui.kit.Kit;
import dev.jasper.ui.testsupport.Edt;
import dev.jasper.ui.testsupport.KitFixtures;
import dev.jasper.ui.theme.GeneratedTheme;
import dev.jasper.ui.theme.SemanticColor;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** Spec 16.1.3, the default kit's part: the gallery renders under Kit.DEFAULT over every theme and paints no literal. */
class KitMatrixTest {
    static List<KitGallery.Capture> captures(GeneratedTheme theme, Kit kit) {
        var captures = new ArrayList<KitGallery.Capture>();
        Edt.run(() -> KitGallery.installed(theme, kit, (context, focus) -> {
            for (var fixture : KitGallery.fixtures()) captures.add(KitGallery.capture(fixture, context, focus));
        }));
        return captures;
    }

    @Test void everyFixtureRendersUnderTheDefaultKitOverTheThemeSet() {
        for (var theme : KitFixtures.themes()) {
            var captures = captures(theme, Kit.DEFAULT);
            assertThat(captures).hasSameSizeAs(KitGallery.fixtures());
            for (var capture : captures)
                assertThat(colours(capture.image())).as("%s %s: %s", theme.name(), theme.dark() ? "Dark" : "Light", capture.fixture().name())
                    .hasSizeGreaterThan(1);
        }
    }

    @Test void underCustomNoFixturePaintsAColourOnlyAnotherThemeHas() {
        var foreign = foreign(KitFixtures.custom());
        var found = new ArrayList<String>();
        for (var capture : captures(KitFixtures.custom(), Kit.DEFAULT)) {
            var painted = colours(capture.image());
            painted.retainAll(foreign);
            for (int rgb : painted) found.add(capture.fixture().name() + " #" + Integer.toHexString(rgb & 0xFFFFFF));
        }
        assertThat(found).as("pixels in another theme's colours under custom()").isEmpty();
    }

    @Test void aLiteralOfAnotherThemesColourIsFound() {
        var literal = KitFixtures.dark().semantic().color(SemanticColor.ACCENT);
        var image = new BufferedImage(4, 4, BufferedImage.TYPE_INT_ARGB);
        image.setRGB(2, 2, literal.getRGB());
        var painted = colours(image);
        painted.retainAll(foreign(KitFixtures.custom()));
        assertThat(painted).containsExactly(literal.getRGB());
    }

    /** The theme set's opaque role colours that {@code custom} does not have, black and white aside: a literal paints one. */
    static Set<Integer> foreign(GeneratedTheme custom) {
        var foreign = new HashSet<Integer>();
        for (var theme : KitFixtures.themes()) for (var role : SemanticColor.values()) {
            var colour = theme.semantic().color(role);
            if (colour.getAlpha() == 255) foreign.add(colour.getRGB());
        }
        for (var role : SemanticColor.values()) foreign.remove(custom.semantic().color(role).getRGB() | 0xFF000000);
        foreign.remove(0xFF000000);
        foreign.remove(0xFFFFFFFF);
        return foreign;
    }

    /** The opaque colours in {@code image}. */
    static Set<Integer> colours(BufferedImage image) {
        var colours = new HashSet<Integer>();
        for (int y = 0; y < image.getHeight(); y++) for (int x = 0; x < image.getWidth(); x++) {
            int argb = image.getRGB(x, y);
            if ((argb >>> 24) == 0xFF) colours.add(argb);
        }
        return colours;
    }
}
```

Black and white are left out because shadows and inset lines are painted in them (`docs/themes.md`, "The token
rule").

- [ ] **Step 2: Run the test to verify it fails**

Run: `./gradlew :jasper-ui:test --tests 'dev.jasper.ui.gallery.KitMatrixTest'`
Expected: FAIL in `:jasper-ui:compileTestJava` with `error: cannot find symbol` / `symbol: class Capture` and
`symbol: method installed(…)`.

- [ ] **Step 3: Split the gallery's entry point**

```bash
python3 -I - <<'PY'
import pathlib
path = pathlib.Path("jasper-ui/src/testFixtures/java/dev/jasper/ui/gallery/KitGallery.java")
text = path.read_text(encoding="utf-8")
start_mark = "    public static void main(String[] args) throws Exception {\n"
end_mark = "    static final class GalleryFocus extends DefaultKeyboardFocusManager {\n"
if text.count(start_mark) != 1 or text.count(end_mark) != 1: raise SystemExit("KitGallery.java: main or GalleryFocus not found once")
start, end = text.index(start_mark), text.index(end_mark)
old = text[start:end]
for expected in ("FlatLafInstaller.install(theme, dev.jasper.ui.kit.Kit.DEFAULT);", "registerCustom(view, fixture.spec().root(), context);",
                 "writeSheets(directory, contact, scale);", "UIManager.setLookAndFeel(priorLaf);"):
    if old.count(expected) != 1: raise SystemExit(f"KitGallery.java: main does not hold {expected!r} once")
new = '''    public static void main(String[] args) throws Exception {
        Path output = Path.of(args.length == 0 ? "jasper-ui/build/gallery" : args[0]);
        String name = System.getProperty("jasper.galleryTheme", "dark");
        GeneratedTheme theme = theme(name);
        Edt.run(() -> installed(theme, dev.jasper.ui.kit.Kit.DEFAULT, (context, focus) -> {
            try {
                float scale = UIScale.getUserScaleFactor();
                Path directory = output.resolve(name + "-" + (int)scale + "x");
                Files.createDirectories(directory);
                var contact = new ArrayList<Rendered>();
                for (Fixture fixture : fixtures()) {
                    var capture = capture(fixture, context, focus);
                    ImageIO.write(capture.image(), "png", directory.resolve(fixture.name() + ".png").toFile());
                    Files.writeString(directory.resolve(fixture.name() + ".json"), capture.metadata());
                    contact.add(new Rendered(fixture.name(), capture.image()));
                }
                writeSheets(directory, contact, scale);
                Files.writeString(directory.resolve("manifest.json"), "{\\"theme\\":" + quote(name) + ",\\"scale\\":" + scale + ",\\"deviceScale\\":1,\\"lookAndFeel\\":" + quote(UIManager.getLookAndFeel().getClass().getName()) + ",\\"fixtureCount\\":" + contact.size() + ",\\"nodeKinds\\":26}");
                System.out.println("Rendered " + contact.size() + " fixtures with " + UIManager.getLookAndFeel().getName() + " at " + scale + "x to " + directory);
            } catch (IOException failure) { throw new java.io.UncheckedIOException(failure); }
        }));
    }

    /** One fixture as the gallery renders it: the image it writes and the metadata beside it. */
    public record Capture(Fixture fixture, BufferedImage image, String metadata) {}

    /**
     * Runs {@code action} with {@code theme} and {@code kit} installed as the gallery renders them: real FlatLaf, the
     * platform family at 13 points, no caps-lock query and the gallery's focus manager, all restored afterwards. EDT.
     */
    public static void installed(GeneratedTheme theme, dev.jasper.ui.kit.Kit kit, java.util.function.BiConsumer<KitContext, GalleryFocus> action) {
        var priorLaf = UIManager.getLookAndFeel();
        Object priorFont = UIManager.get("defaultFont");
        Object priorCaps = UIManager.get("PasswordField.showCapsLock");
        String family = KitFixtures.platformFamily();
        var priorFocus = KeyboardFocusManager.getCurrentKeyboardFocusManager();
        var focus = new GalleryFocus();
        try {
            // Install before creating any gallery widget, including transition controllers.
            FlatLafInstaller.install(theme, kit);
            UIManager.put("PasswordField.showCapsLock", false); // native keyboard-lock queries are unavailable headlessly
            UIManager.put("defaultFont", new Font(family, Font.PLAIN, 13));
            if (!(UIManager.getLookAndFeel() instanceof FlatLaf)) throw new AssertionError("Gallery must use real FlatLaf");
            KeyboardFocusManager.setCurrentKeyboardFocusManager(focus);
            var binding = new ThemeBinding(new ThemePair(theme, theme), failure -> { throw new AssertionError(failure); });
            try (var icons = icons(); var ownedBinding = contextBinding(binding)) {
                action.accept(KitContext.of(kit, ownedBinding, ThemeRoute.WORKSPACE, icons, () -> false, ignored -> ""), focus);
            }
        } finally {
            KeyboardFocusManager.setCurrentKeyboardFocusManager(priorFocus);
            UIManager.put("defaultFont", priorFont);
            UIManager.put("PasswordField.showCapsLock", priorCaps);
            try { UIManager.setLookAndFeel(priorLaf); } catch (UnsupportedLookAndFeelException failure) { throw new AssertionError(failure); }
        }
    }

    /** {@code fixture} rendered as {@link #main} writes it, in {@code context} with {@code focus} from {@link #installed}. EDT. */
    public static Capture capture(Fixture fixture, KitContext context, GalleryFocus focus) {
        String family = KitFixtures.platformFamily();
        float scale = UIScale.getUserScaleFactor();
        UIManager.put("defaultFont", new Font(family, Font.PLAIN, fixture.name().contains("font24") ? 24 : 13));
        try (var view = new KitView(context, fixture.spec(), e -> {})) {
            registerCustom(view, fixture.spec().root(), context);
            int width = UIScale.scale(fixture.width()), height = UIScale.scale(fixture.height());
            view.setAvailableHeight(fixture.height() + 1000); // reference content has no artificial screen cap
            KitFixtures.render(view, width, height, 1);
            if (fixture.name().contains("short-font24")) {
                descendants(view).stream().filter(JScrollPane.class::isInstance).map(JScrollPane.class::cast).findFirst().ifPresent(scroll -> scroll.getVerticalScrollBar().setValue(scroll.getVerticalScrollBar().getMaximum()));
            }
            applyState(view, fixture, focus);
            JComponent paintedRoot = view;
            BufferedImage rendered = onSurface(KitFixtures.render(view, width, height, 1), context);
            if (fixture.name().equals("dropdown-open")) {
                var combo=(JComboBox<?>)find(view,"dropdown.input");
                var popup=(javax.swing.plaf.basic.BasicComboPopup)combo.getUI().getAccessibleChild(combo,0);
                popup.getList().setSelectedIndex(0);paintedRoot=popup;
                rendered=paint(popup,UIScale.scale(240),UIScale.scale(100));
            }
            if (fixture.name().equals("vault-add-menu")) {
                var anchor = find(view, "add-menu");
                var popup = anchor.getComponentPopupMenu();
                popup.setInvoker(anchor);
                paintedRoot = popup;
                rendered = paint(popup, popup.getPreferredSize().width, popup.getPreferredSize().height);
            }
            return new Capture(fixture, rendered, metadata(fixture, view, context, scale, rendered, paintedRoot));
        } finally {
            if (focus.target != null) {
                var lost = new FocusEvent(focus.target, FocusEvent.FOCUS_LOST);
                for (var listener : focus.target.getFocusListeners()) listener.focusLost(lost);
                focus.target = null;
            }
        }
    }

'''
text = text[:start] + new + text[end:]
focus_old = "    static final class GalleryFocus extends DefaultKeyboardFocusManager {\n"
focus_new = "    /** The gallery's focus manager: the focus owner is whatever {@link #applyState} focused. */\n    public static final class GalleryFocus extends DefaultKeyboardFocusManager {\n"
text = text.replace(focus_old, focus_new, 1)
path.write_text(text, encoding="utf-8")
PY
```

The metadata is still taken before the fixture's focus is released (the `return` expression runs before `finally`),
and the manifest still names the installed FlatLaf class, because `main` writes it inside `installed`.

- [ ] **Step 4: Run the tests and the gallery to verify they pass and nothing it writes changed**

Run: `./gradlew :jasper-ui:test --tests 'dev.jasper.ui.gallery.*'`
Expected: PASS: `KitMatrixTest` (3) with `GalleryCoverageTest`, `UiDocumentationTest` and `UiExamplesTest`.

Then:

```bash
./gradlew :jasper-ui:gallery -Pjasper.galleryTheme=dark
(cd jasper-ui/build/gallery/dark-1x && shasum -a 256 * | sort) | diff - build/plan2/gallery-dark-base.sha && echo dark-unchanged
./gradlew :jasper-ui:gallery -Pjasper.galleryTheme=light
(cd jasper-ui/build/gallery/light-1x && shasum -a 256 * | sort) | diff - build/plan2/gallery-light-base.sha && echo light-unchanged
```

Expected: `dark-unchanged` and `light-unchanged`: all 142 files of each are byte-identical to Task 1's base.

- [ ] **Step 5: Commit**

```bash
git add jasper-ui/src/testFixtures/java/dev/jasper/ui/gallery/KitGallery.java jasper-ui/src/test/java/dev/jasper/ui/gallery/KitMatrixTest.java
git commit -F - <<'EOF'
Derived roles: the default kit's gallery matrix in check

KitGallery's entry point splits into installed and capture, so the opt-in
gallery and KitMatrixTest render the same images. KitMatrixTest renders
every fixture under Kit.DEFAULT over the theme set and finds no colour
only another theme has under custom(). The gallery's output is unchanged.

🤖 Generated with Claude Code at The Home Depot

Co-Authored-By: Claude <noreply@anthropic.com>
EOF
```

### Task 7: PaintedContrastTest: the default look's declared pairs over every palette

`ShippedControlReadabilityTest` becomes `PaintedContrastTest` (spec 16.1.2). It declares each pair the default look
repairs today as a `PaintPair` with its guard, beside the painter that paints it, and checks over the theme set and the
eight degenerate palettes that the painted colour is exactly `SurfaceContrast.painted`'s and meets its guard; the
off switch, which no guard repairs, is painted as authored over the same palettes. jasper-app's tests gain jasper-ui's
test fixtures for the palettes.

**Files:**
- Modify: `jasper-app/build.gradle.kts:7` (after `implementation(project(":jasper-ui"))`)
- Move: `jasper-app/src/test/java/dev/jasper/app/appearance/ShippedControlReadabilityTest.java` to `jasper-app/src/test/java/dev/jasper/app/appearance/PaintedContrastTest.java` (whole file replaced)
- Modify: `docs/design/theme-engine/migration.csv:1145` (the row moves with the file)

**Interfaces:**
- Consumes: `PaintPair`, `SurfaceContrast.painted`, `ColorRole.color` (Task 4), `SurfaceContrast.Guard` (Task 2),
  `KitFixtures.themes()`, `degenerate()`, `context(theme)`, `render` (Task 1), `ChromePainter`'s `search`, `tabStrip`,
  `statusDot`, `badge` and `connectedDot`, `KitTestSupport.require`, `ThemeTestSupport.generated` (existing).
- Produces: `PaintedContrastTest.DEFAULT_PAIRS`, the default look's declarations; plan 4 moves them onto the default
  faces, and plans 4, 6 and 7 add the classic faces' and chrome strategies' pairs here.

- [ ] **Step 1: Write the failing test**

```bash
git mv jasper-app/src/test/java/dev/jasper/app/appearance/ShippedControlReadabilityTest.java \
  jasper-app/src/test/java/dev/jasper/app/appearance/PaintedContrastTest.java
```

Replace the whole of `jasper-app/src/test/java/dev/jasper/app/appearance/PaintedContrastTest.java` with:

```java
package dev.jasper.app.appearance;

import static dev.jasper.ui.theme.SemanticColor.*;
import static org.assertj.core.api.Assertions.*;

import dev.jasper.app.testsupport.EdtTestExtension;
import dev.jasper.app.testsupport.KitTestSupport;
import dev.jasper.ui.icons.IconRegistry;
import dev.jasper.ui.kit.Kit;
import dev.jasper.ui.metrics.DesignMetrics;
import dev.jasper.ui.model.*;
import dev.jasper.ui.swing.*;
import dev.jasper.ui.testsupport.KitFixtures;
import dev.jasper.ui.theme.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import javax.swing.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

/**
 * Spec 16.1.2 for the default look: each pair it repairs today is declared with its guard and painted as the guard makes
 * it, over the theme set and the eight degenerate palettes; the off switch, which no guard repairs, is painted as
 * authored. The default look declares no other pair (spec 7.3), so it stays pixel-identical.
 */
@ExtendWith(EdtTestExtension.class)
class PaintedContrastTest {
    private static final ChromePainter PAINTER = new ChromePainter(Kit.DEFAULT);
    private static final DesignMetrics METRICS = Kit.DEFAULT.metrics();

    /** {@code role} as the painters lay it out: the default catalog's value at the UI scale. */
    private static int scaled(DesignMetrics.Role role) { return com.formdev.flatlaf.util.UIScale.scale(METRICS.value(role)); }

    /** Whether the painter painted {@code expected} where its pair is painted, in {@code theme}. */
    @FunctionalInterface private interface Sampler { boolean paints(GeneratedTheme theme, Color expected); }

    /** A pair the default look repairs today and the painter that paints it. */
    private record Declared(String name, PaintPair pair, Sampler painter) { }

    private static final List<Declared> DEFAULT_PAIRS = List.of(
        new Declared("title search hint", new PaintPair(TEXT_PLACEHOLDER, SURFACE_FIELD_RAISED, SurfaceContrast.Guard.INDICATOR),
            PaintedContrastTest::searchHint),
        new Declared("inactive tab", new PaintPair(SURFACE_TABSTRIP, SURFACE_TERMINAL, SurfaceContrast.Guard.apart(SurfaceContrast.TAB_LIGHTNESS)),
            PaintedContrastTest::inactiveTab),
        new Declared("running status dot", new PaintPair(STATUS_SUCCESS, SURFACE_STATUS, SurfaceContrast.Guard.TEXT),
            PaintedContrastTest::runningDot),
        new Declared("rail badge", new PaintPair(ACCENT, SURFACE_APP, SurfaceContrast.Guard.INDICATOR),
            (theme, expected) -> badge(theme, expected, false)),
        new Declared("rail error badge", new PaintPair(STATUS_DANGER, SURFACE_APP, SurfaceContrast.Guard.INDICATOR),
            (theme, expected) -> badge(theme, expected, true)),
        new Declared("connected dot", new PaintPair(STATUS_SUCCESS, SURFACE_PANEL, SurfaceContrast.Guard.INDICATOR),
            PaintedContrastTest::connectedDot),
        new Declared("selected segment label", new PaintPair(TEXT_PRIMARY, SEGMENT_SELECTED_BG, SurfaceContrast.Guard.TEXT),
            PaintedContrastTest::selectedSegment),
        new Declared("selected preview caption", new PaintPair(TEXT_PRIMARY, SURFACE_CARD, SurfaceContrast.Guard.TEXT),
            PaintedContrastTest::selectedPreview));

    /** The theme set, then the degenerate palettes. */
    private static List<GeneratedTheme> palettes() {
        var palettes = new ArrayList<>(KitFixtures.themes());
        palettes.addAll(KitFixtures.degenerate());
        return palettes;
    }

    private static String name(GeneratedTheme theme) { return theme.name() + (theme.dark() ? " Dark" : " Light"); }

    @Test void eachDeclaredPairIsPaintedAsItsGuardMakesItOverEveryPalette() {
        for (var theme : palettes()) for (var declared : DEFAULT_PAIRS) {
            String where = name(theme) + ", " + declared.name();
            Color expected = SurfaceContrast.painted(theme, declared.pair());
            assertThat(declared.pair().guard().holds(expected, declared.pair().against().color(theme))).as(where + " meets its guard").isTrue();
            assertThat(declared.painter().paints(theme, expected)).as(where + " is painted " + ColorMath.hex(expected)).isTrue();
        }
    }

    @Test void theOffSwitchWhichNoGuardRepairsIsPaintedAsAuthoredOverEveryPalette() throws Exception {
        var initial = ThemeTestSupport.generated(true);
        var original = UIManager.getLookAndFeel();
        try (var binding = new ThemeBinding(new ThemePair(initial, initial), failure -> { throw new AssertionError(failure); });
             var icons = new IconRegistry()) {
            var kit = KitContext.of(Kit.DEFAULT, binding, ThemeRoute.CHROME, icons, () -> false, action -> "");
            for (boolean compact : List.of(false, true)) {
                var events = new ArrayList<UiEvent>();
                try (var view = new KitView(kit, new ViewSpec(new Nodes.Toggle("toggle", ControlState.READY, "Enabled", false, "toggle", compact)), events::add)) {
                    var button = KitTestSupport.require(view, "toggle", JButton.class);
                    for (var theme : palettes()) {
                        FlatLafInstaller.install(theme, Kit.DEFAULT); binding.publish(new ThemePair(theme, theme)); SwingUtilities.updateComponentTreeUI(view);
                        button.setSize(button.getPreferredSize()); var image = paint(button);
                        // Centers of the left knob and right track avoid antialiasing and the shadow.
                        Color knob = new Color(image.getRGB(button.getWidth() / 4, button.getHeight() / 2), true);
                        Color track = new Color(image.getRGB(button.getWidth() * 3 / 4, button.getHeight() / 2), true);
                        String where = name(theme) + " compact=" + compact;
                        assertThat(knob).as(where).isEqualTo(theme.semantic().color(TOGGLE_KNOB));
                        assertThat(track).as(where).isEqualTo(theme.semantic().color(TOGGLE_TRACK_OFF));
                        // Slate Cobalt Dark separates knob and track by more than 3:1; Light's white knob on its #C3CAD3 track is
                        // 1.65:1, and the knob's shadow carries the separation.
                        if (theme == ThemeTestSupport.generated(true)) assertThat(ColorMath.contrast(knob, track)).isGreaterThanOrEqualTo(3);
                        assertThat(button.isEnabled()).isTrue(); assertThat(button.isSelected()).isFalse();
                    }
                    assertThat(events).isEmpty(); button.doClick(0); assertThat(events).containsExactly(new UiEvent.Toggled("toggle", true));
                }
            }
        } finally { UIManager.setLookAndFeel(original); }
    }

    private static boolean searchHint(GeneratedTheme theme, Color expected) {
        var blank = new Icon() {
            public int getIconWidth() { return 16; } public int getIconHeight() { return 16; }
            public void paintIcon(Component c, Graphics g, int x, int y) { }
        };
        for (boolean hover : List.of(false, true)) {
            var component = new JPanel(); component.setSize(300, 32);
            var image = new BufferedImage(300, 32, BufferedImage.TYPE_INT_ARGB);
            var graphics = image.createGraphics();
            try { PAINTER.search(component, graphics, theme, hover, false, "", "MMMM", blank, null); } finally { graphics.dispose(); }
            if (!hasColor(image, expected)) return false;
        }
        return true;
    }

    private static boolean inactiveTab(GeneratedTheme theme, Color expected) {
        var strip = new JPanel(); strip.setSize(300, 28);
        var image = new BufferedImage(300, 28, BufferedImage.TYPE_INT_ARGB);
        var graphics = image.createGraphics();
        try { PAINTER.tabStrip(strip, graphics, theme, Color.BLACK, List.of(new Rectangle(0, 0, 100, 28), new Rectangle(100, 0, 100, 28)), 1, -1, true); }
        finally { graphics.dispose(); }
        return image.getRGB(50, 10) == expected.getRGB();
    }

    private static boolean runningDot(GeneratedTheme theme, Color expected) {
        var bar = new JPanel(); bar.setSize(120, 24);
        var image = new BufferedImage(120, 24, BufferedImage.TYPE_INT_ARGB);
        var graphics = image.createGraphics();
        try { PAINTER.statusDot(bar, graphics, theme, true); } finally { graphics.dispose(); }
        int size = scaled(DesignMetrics.Role.STATUS_RUNNING_DOT);
        return image.getRGB(scaled(DesignMetrics.Role.STATUS_GAP) + size / 2, (24 - size) / 2 + size / 2) == expected.getRGB();
    }

    private static boolean badge(GeneratedTheme theme, Color expected, boolean error) {
        var image = new BufferedImage(40, 40, BufferedImage.TYPE_INT_ARGB);
        var graphics = image.createGraphics();
        try { PAINTER.badge(graphics, 10, 10, 16, theme, error); } finally { graphics.dispose(); }
        int dot = scaled(DesignMetrics.Role.RAIL_BADGE_DOT);
        return image.getRGB(10 + 16 - dot + dot / 2, 10 + dot / 2) == expected.getRGB();
    }

    private static boolean connectedDot(GeneratedTheme theme, Color expected) {
        var image = new BufferedImage(20, 20, BufferedImage.TYPE_INT_ARGB);
        var graphics = image.createGraphics();
        try { PAINTER.connectedDot(graphics, 4, 4, 8, theme); } finally { graphics.dispose(); }
        return image.getRGB(8, 8) == expected.getRGB();
    }

    private static boolean selectedSegment(GeneratedTheme theme, Color expected) {
        var context = KitFixtures.context(theme);
        var choice = new Nodes.Choice("choice", ControlState.READY, Nodes.ChoiceKind.SEGMENTED,
            List.of(new Option("a", "Alpha", Optional.empty()), new Option("b", "Beta", Optional.empty())), "b", "Protocol", "choose");
        try (var view = new KitView(context, new ViewSpec(choice), event -> { })) {
            KitFixtures.render(view, 400, 80, 1);
            return KitTestSupport.require(view, "choice.option.b", JButton.class).getForeground().equals(expected);
        } finally { context.icons().close(); context.binding().close(); }
    }

    /** The preview tiles' selected caption: its most opaque ink below the tile, painted over nothing. */
    private static boolean selectedPreview(GeneratedTheme theme, Color expected) {
        var context = KitFixtures.context(theme);
        var choice = new Nodes.Choice("theme", ControlState.READY, Nodes.ChoiceKind.PREVIEWS,
            List.of(new Option("a", "Alpha", Optional.empty()), new Option("b", "Beta", Optional.empty())), "a", "Theme", "choose");
        try (var view = new KitView(context, new ViewSpec(choice), event -> { })) {
            KitFixtures.render(view, 400, 200, 1);
            var image = paint(KitTestSupport.require(view, "theme.option.a", JButton.class));
            int strongest = 0, ink = 0;
            for (int y = Math.min(scaled(DesignMetrics.Role.THEME_PREVIEW_HEIGHT), image.getHeight()); y < image.getHeight(); y++)
                for (int x = 0; x < image.getWidth(); x++) {
                    int argb = image.getRGB(x, y);
                    if (argb >>> 24 > strongest) { strongest = argb >>> 24; ink = argb & 0xFFFFFF; }
                }
            return strongest > 0 && ink == (expected.getRGB() & 0xFFFFFF);
        } finally { context.icons().close(); context.binding().close(); }
    }

    private static BufferedImage paint(JComponent component) {
        var image = new BufferedImage(component.getWidth(), component.getHeight(), BufferedImage.TYPE_INT_ARGB);
        var graphics = image.createGraphics(); try { component.paint(graphics); } finally { graphics.dispose(); } return image;
    }

    private static boolean hasColor(BufferedImage image, Color color) {
        for (int y = 1; y < image.getHeight() - 1; y++) for (int x = image.getWidth() / 2; x < image.getWidth() - 2; x++)
            if (image.getRGB(x, y) == color.getRGB()) return true;
        return false;
    }
}
```

The samplers read what was painted, not a colour the code computes: the dots' centres and the tab strip's inactive
slot are fills, the hint is painted without text antialiasing on its field, the segment label's colour is the
button's foreground (as `ChoicesTest` reads it), and the preview caption is painted over nothing, so its most opaque
pixel carries the text colour (as `NotificationCardTest` reads the count).

- [ ] **Step 2: Run the test to verify it fails**

Run: `./gradlew :jasper-app:test --tests 'dev.jasper.app.appearance.PaintedContrastTest'`
Expected: FAIL in `:jasper-app:compileTestJava` with `error: package dev.jasper.ui.testsupport does not exist`.

- [ ] **Step 3: Give jasper-app's tests the fixtures and move the ledger row**

```bash
python3 -I - <<'PY'
import pathlib
def edit(name, old, new):
    path = pathlib.Path(name)
    text = path.read_text(encoding="utf-8")
    if text.count(old) != 1: raise SystemExit(f"{name}: expected one {old!r}")
    path.write_text(text.replace(old, new), encoding="utf-8")
edit("jasper-app/build.gradle.kts", """    implementation(project(":jasper-ui"))
""", """    implementation(project(":jasper-ui"))
    testImplementation(testFixtures(project(":jasper-ui")))
""")
edit("docs/design/theme-engine/migration.csv",
"jasper-app/src/test/java/dev/jasper/app/appearance/ShippedControlReadabilityTest.java,app.appearance,ShippedControlReadabilityTest,nonvisual-helper,12,10,jasper-app/src/test/java/dev/jasper/app/appearance/ShippedControlReadabilityTest.java,Test/fixture ownership only; real kit switch and native title-search paint regressions for shipped theme readability. No production raw-control path.\n",
"jasper-app/src/test/java/dev/jasper/app/appearance/PaintedContrastTest.java,app.appearance,PaintedContrastTest,nonvisual-helper,12,10,jasper-app/src/test/java/dev/jasper/app/appearance/PaintedContrastTest.java,Test/fixture ownership only; the default look's declared painted pairs and the authored off switch over the theme set and the degenerate palettes. No production raw-control path.\n")
PY
```

- [ ] **Step 4: Run the tests to verify they pass**

Run: `./gradlew :jasper-app:test --tests 'dev.jasper.app.appearance.PaintedContrastTest' --tests 'dev.jasper.app.appearance.ThemeMigrationCoverageTest'`
Expected: PASS: `PaintedContrastTest` (2) and `ThemeMigrationCoverageTest` (5).

To see that the declarations are checked, change the hint's guard in `DEFAULT_PAIRS` to `SurfaceContrast.Guard.TEXT`
and run the test again: it fails with `[Slate Cobalt Dark, title search hint is painted #a3aab6]`. Restore the
`INDICATOR` guard before committing.

- [ ] **Step 5: Commit**

```bash
git add jasper-app/build.gradle.kts jasper-app/src/test/java/dev/jasper/app/appearance docs/design/theme-engine/migration.csv
git commit -F - <<'EOF'
Derived roles: PaintedContrastTest over every palette

ShippedControlReadabilityTest becomes PaintedContrastTest: the default
look declares each pair it repairs today with its guard, and each is
painted exactly as SurfaceContrast.painted makes it over the theme set and
the degenerate palettes; the unguarded off switch is painted as authored.

🤖 Generated with Claude Code at The Home Depot

Co-Authored-By: Claude <noreply@anthropic.com>
EOF
```

### Task 8: Documentation and status

The current-state documents say what derived roles, guards and the shared fixtures are; `docs/STATUS.md` records the
plan; the whole build runs, and the gallery is compared with Task 1's base once more.

**Files:**
- Modify docs: `docs/ui-architecture.md:9,107`, `docs/ui-maintenance.md:19,38`, `jasper-ui/README.md:9`, `docs/STATUS.md:2`, this plan's header

**Interfaces:**
- Consumes: everything above.
- Produces: documentation only.

- [ ] **Step 1: Say what the theme engine now holds**

Run this script; each replacement must find its text exactly once:

```bash
python3 -I - <<'PY'
import pathlib
EDITS = [
    ("docs/ui-architecture.md",
     "| `theme` | none | Jasper theme files (Slate Cobalt is bundled), semantic palettes, terminal and editor schemes, syntax, glass and FlatLaf keys; EDT appearance binding |",
     "| `theme` | none | Jasper theme files (Slate Cobalt is bundled), semantic palettes and derived roles, terminal and editor schemes, syntax, glass, FlatLaf keys and paint-time contrast guards; EDT appearance binding |"),
    ("docs/ui-architecture.md",
     "apply one kit metric to a base role, and a role that carries alpha (the scrim, `border.subtle`) is painted as given.\n",
     "apply one kit metric to a base role, and a role that carries alpha (the scrim, `border.subtle`) is painted as given.\n"
     "A variant may also list eight of its derived roles (`GeneratedTheme.derivedOverrides()`); `DerivedPalette.of(theme)`\n"
     "gives all 23 `DerivedColor`s, each the override or its recipe over semantic roles and the derived roles before it,\n"
     "kept by theme identity (header chrome passes `TitleBarTheme.of(theme)`). `PaintPair` declares a pair a painter paints,\n"
     "naming either kind of `ColorRole`, with its `SurfaceContrast.Guard` (text 4.5:1, indicator 3:1, structural boundary\n"
     "1.3:1, control boundary 1.5:1, or a CIE L* distance); `SurfaceContrast.painted` applies the guard where the pair is\n"
     "painted and never changes a role.\n"),
    ("docs/ui-maintenance.md",
     "transitions. Test and gallery classes stay out of the production jar.\n",
     "transitions. Test and gallery classes stay out of the production jar.\n"
     "The gallery and the kit fixtures (`KitFixtures`, `Edt` and the `dev.jasper.ui.gallery` builders) are jasper-ui's\n"
     "`testFixtures` source set, which jasper-app's tests use through `testFixtures(project(\":jasper-ui\"))`. Inside\n"
     "`check`, `KitMatrixTest` renders every fixture under `Kit.DEFAULT` over `KitFixtures.themes()`, and jasper-app's\n"
     "`PaintedContrastTest` samples every declared pair over the theme set and `KitFixtures.degenerate()`.\n"),
    ("docs/ui-maintenance.md",
     "When changing appearance, test contrasting chrome/workspace colors, replay/rollback and disposal.\n",
     "When changing appearance, test contrasting chrome/workspace colors, replay/rollback and disposal.\n"
     "A derived role's recipe lives in `DerivedPalette`: changing one, or a built-in theme, updates `DerivedColorTest.INPUTS`\n"
     "and `derived-roles.tsv` in the same commit. A painter that repairs a pair declares it in `PaintedContrastTest`.\n"),
    ("jasper-ui/README.md",
     "`GeneratedTheme` (`UiColorKeys` derives FlatLaf's UI keys from its roles when it is installed); the app reads installed theme files with the same\n",
     "`GeneratedTheme` (`UiColorKeys` derives FlatLaf's UI keys from its roles when it is installed); `DerivedPalette` computes its\n"
     "derived roles and `SurfaceContrast` holds the paint-time contrast guards; the app reads installed theme files with the same\n"),
]
for name, old, new in EDITS:
    path = pathlib.Path(name)
    text = path.read_text(encoding="utf-8")
    if text.count(old) != 1: raise SystemExit(f"{name}: expected one {old!r}, found {text.count(old)}")
    path.write_text(text.replace(old, new), encoding="utf-8")
PY
```

`docs/themes.md` already has the override keys (Task 4) and the converter's keys (Task 5). The historical records that
name `ShippedControlReadabilityTest` (`docs/design/theme-engine/temporary-paths.md`, earlier `docs/STATUS.md` entries,
plans and specs) stay as they are.

- [ ] **Step 2: Run the whole build and compare the gallery**

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

Expected: 0 failures and 0 errors, with 27 tests more than the base's 10,473 (jasper-ui's new and extended theme,
fixture and gallery tests; `PaintedContrastTest`'s two replace `ShippedControlReadabilityTest`'s two). Run the
AGENTS.md source-hygiene script over every Java source; it must print nothing. Then compare the gallery once more:

```bash
./gradlew :jasper-ui:gallery -Pjasper.galleryTheme=dark
(cd jasper-ui/build/gallery/dark-1x && shasum -a 256 * | sort) | diff - build/plan2/gallery-dark-base.sha && echo dark-unchanged
./gradlew :jasper-ui:gallery -Pjasper.galleryTheme=light
(cd jasper-ui/build/gallery/light-1x && shasum -a 256 * | sort) | diff - build/plan2/gallery-light-base.sha && echo light-unchanged
```

Expected: `dark-unchanged` and `light-unchanged`.

- [ ] **Step 3: Record the plan in STATUS and in this plan**

In `docs/STATUS.md`, insert after line 2 (the blank line under `# Jasper — Status and Handoff`) the entry below, with
the line Step 2's totals script printed in place of `TOTALS` and the first and last commit of Tasks 1-8 in place of
`FIRST..LAST`, followed by a blank line:

```markdown
**Enterprise presentation, plan 2: derived roles — 2026-10-15** (`claude/enterprise-derived-roles`, worktree
`.claude/worktrees/enterprise-derived-roles`, `FIRST..LAST`;
[plan](superpowers/plans/2026-10-15-enterprise-2-derived-roles.md),
[spec](superpowers/specs/2026-10-15-jasper-enterprise-presentation-design.md)). The theme engine gains the colour side
of the Enterprise look. `DerivedColor` names spec 7.1's 23 host-private derived roles; `DerivedPalette.of(theme)`
computes them in order from the palette (each recipe reads only semantic roles and earlier derived roles, checked
over every palette by `DerivedColorTest`), or takes the variant's override, and keeps the last four themes by identity.
A variant may list the eight overridable roles as `#RRGGBB` (`JasperTheme.DERIVED_OVERRIDES`, read before
`SemanticColor.of`), carried as `GeneratedTheme.derivedOverrides()` and by `TitleBarTheme`. `SurfaceContrast` gains the
structural (1.3) and control (1.5) boundary floors and `Guard`, the paint-time repair a face declares for a
`PaintPair` (over the sealed `ColorRole` of both enums), applied by `SurfaceContrast.painted`. The converter writes the
override keys from IntelliJ keys the theme or a parent in its plugin sets; the Ocean and Dawn fixtures exercise them,
the goldens and the four built-in Catppuccin themes are regenerated (each flavour now authors five derived roles), and
`derived-roles.tsv` freezes the built-in variants' derived roles (Slate Cobalt Light meets spec 7.1's calibration:
#DFE2E6, #F1F2F2, #EEF3FB), and `KnownRepairsTest` measures spec 7.3's known repairs on the theme set (Latte's
warning pair is 2.09:1 with its authored ground, against the spec's 2.06:1 from the recipe). jasper-ui's gallery and kit fixtures are a `testFixtures` source set shared with
jasper-app; `KitFixtures.themes()` and `degenerate()` (eight palettes) feed `KitMatrixTest` (every gallery fixture under
`Kit.DEFAULT` over the theme set, and no literal under `custom()`) and `PaintedContrastTest` (formerly
`ShippedControlReadabilityTest`: the default look's eight declared pairs painted exactly as their guards make them over
every palette, and the unguarded off switch as authored). Zero pixel change: no default painter reads a derived role,
and the headless gallery's 142 files for Slate Cobalt Dark and Light are byte-identical to the base. Planned
deviations (plan, "Planned deviations"): (1) the default look's pair declarations live in `PaintedContrastTest` until
plan 4's faces, with `PaintSpecTest`; repairs public API does not reach keep their package tests; (2) the theme set
takes ocean, ocean-dawn and stray (`paper-light` has no palette), Enterprise joins in plan 5; (3) `KitMatrixTest` is the
default kit's part of 16.1.3; (4) recipe details: translucent inputs laid over the recipe's surface, the primed borders
guarded on `surface.card`, the zebra's halfway fallback, `accent.edge`'s direction, a four-theme identity cache; (5) the
Java loader accepts override keys in any order, the converter writes and checks them after the roles; (6) the converter
counts only keys the theme or its plugin's parents set, and lays translucent values over `surface.app` or
`surface.card`; (7) `KitGallery.main` splits into `installed` and `capture`. For plan 7's review: Slate Cobalt Dark's
active header (#232B3C) comes out darker than its inactive header (#2D3036), as `apart` moves toward black, and each
Catppuccin flavour authors one colour for both headers. `./gradlew check --continue`: **TOTALS**. GUI checks for the
user: none new (nothing visible changed). Not merged, not pushed.
```

Then insert, after this plan's first line (`# Enterprise presentation 2: derived roles Implementation Plan`) and a
blank line:

```markdown
> **Status (2026-10-15): executed on `claude/enterprise-derived-roles` (`FIRST..LAST`).** Deviations from this plan's
> text: none beyond its "Planned deviations".
```

replacing `FIRST..LAST` the same way, and naming any step whose text had to change instead of "none" (and the same in
the STATUS entry).

- [ ] **Step 4: Commit**

```bash
git add docs/ui-architecture.md docs/ui-maintenance.md jasper-ui/README.md docs/STATUS.md \
  docs/superpowers/plans/2026-10-15-enterprise-2-derived-roles.md
git commit -F - <<'EOF'
Derived roles: documentation and status

The current-state documents describe derived roles, the paint-time guards
and the shared jasper-ui test fixtures, and STATUS records plan 2 with its
planned deviations.

🤖 Generated with Claude Code at The Home Depot

Co-Authored-By: Claude <noreply@anthropic.com>
EOF
```
